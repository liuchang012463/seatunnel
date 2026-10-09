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

package org.apache.seatunnel.engine.server.service.jar;

import org.apache.seatunnel.engine.core.job.ConnectorJarIdentifier;
import org.apache.seatunnel.engine.core.job.ConnectorJarType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Tests that a member which receives a jar writes it even when its storage directory does not exist
 * yet, which is the case for the driver jar directory of a freshly started node.
 */
class ServerConnectorPackageClientTest {

    private final ServerConnectorPackageClient client =
            new ServerConnectorPackageClient(null, null);

    @Test
    void storesJarIntoAMissingDirectory(@TempDir Path dir) throws Exception {
        Path storageFile = dir.resolve("driver-jars").resolve("abc-driver.jar");
        Assertions.assertFalse(Files.exists(storageFile.getParent()));

        client.storageConnectorJarFile(
                "jar-bytes".getBytes(StandardCharsets.UTF_8), identifierOf(storageFile));

        Assertions.assertEquals("jar-bytes", Files.readString(storageFile));
    }

    @Test
    void keepsAnAlreadyStoredJarUnchanged(@TempDir Path dir) throws Exception {
        Path storageFile = dir.resolve("driver.jar");
        Files.write(storageFile, "original".getBytes(StandardCharsets.UTF_8));

        client.storageConnectorJarFile(
                "newer".getBytes(StandardCharsets.UTF_8), identifierOf(storageFile));

        Assertions.assertEquals("original", Files.readString(storageFile));
    }

    private ConnectorJarIdentifier identifierOf(Path storageFile) {
        return ConnectorJarIdentifier.of(
                ConnectorJarType.COMMON_PLUGIN_JAR, "driver.jar", storageFile.toString());
    }
}
