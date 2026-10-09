/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.seatunnel.engine.server.rest.service;

import org.apache.seatunnel.shade.org.apache.commons.lang3.StringUtils;

import org.apache.seatunnel.engine.common.config.SeaTunnelProperties;
import org.apache.seatunnel.engine.core.job.ConnectorJar;
import org.apache.seatunnel.engine.core.job.ConnectorJarIdentifier;
import org.apache.seatunnel.engine.core.job.ConnectorJarType;
import org.apache.seatunnel.engine.server.SeaTunnelServer;
import org.apache.seatunnel.engine.server.task.operation.CheckConnectorJarExistsOperation;
import org.apache.seatunnel.engine.server.task.operation.SendConnectorJarToMemberNodeOperation;
import org.apache.seatunnel.engine.server.utils.NodeEngineUtil;

import com.hazelcast.cluster.Address;
import com.hazelcast.spi.impl.NodeEngineImpl;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/**
 * Stores JDBC driver jars for jobs that pin their driver through {@code driver_location}.
 *
 * <p>The jar is stored on the master and pushed to every other cluster member, so the task finds
 * the jar on whichever node it runs. The path is identical on every member, which is what the
 * per-node jar check of the job classloader expects.
 */
@Slf4j
public class DriverJarService {

    private static final String DRIVER_JAR_DIR = "driver-jars";

    private final NodeEngineImpl nodeEngine;

    private final SeaTunnelServer seaTunnelServer;

    public DriverJarService(NodeEngineImpl nodeEngine, SeaTunnelServer seaTunnelServer) {
        this.nodeEngine = nodeEngine;
        this.seaTunnelServer = seaTunnelServer;
    }

    /**
     * Stores the given jar on this node and on every other member.
     *
     * @param fileName the jar file name, without any directory part
     * @param data the jar bytes
     * @return the path of the jar, identical on every member of the cluster
     */
    public String storeDriverJar(String fileName, byte[] data) {
        validate(fileName, data);

        Path storagePath = resolveStoragePath(fileName, data);
        writeIfAbsent(storagePath, data);

        ConnectorJar connectorJar =
                ConnectorJar.createConnectorJar(ConnectorJarType.COMMON_PLUGIN_JAR, data, fileName);
        ConnectorJarIdentifier identifier =
                ConnectorJarIdentifier.of(
                        ConnectorJarType.COMMON_PLUGIN_JAR,
                        fileName,
                        storagePath.toAbsolutePath().toString());

        Address thisAddress = nodeEngine.getThisAddress();
        nodeEngine
                .getClusterService()
                .getMembers()
                .forEach(
                        member -> {
                            Address address = member.getAddress();
                            if (address.equals(thisAddress)) {
                                return;
                            }
                            // A member that already holds the jar needs no transfer; a member that
                            // restarted without it is pushed again, so submitting a job heals the
                            // cluster without transferring the jar to every member every time.
                            if (holdsDriverJar(address, identifier)) {
                                log.debug(
                                        "Member {} already holds the driver jar {}",
                                        address,
                                        identifier.getStoragePath());
                                return;
                            }
                            NodeEngineUtil.sendOperationToMemberNode(
                                            nodeEngine,
                                            new SendConnectorJarToMemberNodeOperation(
                                                    connectorJar, identifier),
                                            address)
                                    .join();
                        });

        log.info("Stored driver jar {} on the cluster, path: {}", fileName, identifier.getStoragePath());
        return identifier.getStoragePath();
    }

    private boolean holdsDriverJar(Address address, ConnectorJarIdentifier identifier) {
        Object exists =
                NodeEngineUtil.sendOperationToMemberNode(
                                nodeEngine,
                                new CheckConnectorJarExistsOperation(identifier),
                                address)
                        .join();
        return Boolean.TRUE.equals(exists);
    }

    private void validate(String fileName, byte[] data) {
        if (data == null || data.length == 0) {
            throw new IllegalArgumentException("The driver jar is empty");
        }
        if (StringUtils.isBlank(fileName)) {
            throw new IllegalArgumentException("The driver jar file name is missing");
        }
        if (!fileName.toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("The driver file is not a jar: " + fileName);
        }
        if (fileName.contains("/") || fileName.contains("\\") || fileName.contains("..")) {
            throw new IllegalArgumentException("The driver jar file name must be a plain name: " + fileName);
        }
    }

    private Path resolveStoragePath(String fileName, byte[] data) {
        String storageDir =
                seaTunnelServer
                        .getSeaTunnelConfig()
                        .getEngineConfig()
                        .getConnectorJarStorageConfig()
                        .getStoragePath();
        if (StringUtils.isBlank(storageDir)) {
            storageDir =
                    System.getProperty(
                            SeaTunnelProperties.SEATUNNEL_HOME.getName(),
                            SeaTunnelProperties.SEATUNNEL_HOME.getDefaultValue());
        }
        // The digest keeps two drivers that share a file name apart, and makes re-uploading the
        // same jar a no-op.
        return Paths.get(storageDir, DRIVER_JAR_DIR, digest(data) + "-" + fileName);
    }

    private void writeIfAbsent(Path storagePath, byte[] data) {
        try {
            Files.createDirectories(storagePath.getParent());
            if (Files.exists(storagePath)) {
                return;
            }
            Path tempFile = Files.createTempFile(storagePath.getParent(), "driver-jar", ".tmp");
            Files.write(tempFile, data);
            Files.move(
                    tempFile,
                    storagePath,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot store the driver jar " + storagePath, e);
        }
    }

    private String digest(byte[] data) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                builder.append(String.format("%02x", hash[i]));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
