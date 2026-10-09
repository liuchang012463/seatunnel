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

package org.apache.seatunnel.engine.server.rest.servlet;

import org.apache.seatunnel.engine.server.SeaTunnelServer;
import org.apache.seatunnel.engine.server.rest.service.DriverJarService;

import com.hazelcast.spi.impl.NodeEngineImpl;
import lombok.extern.slf4j.Slf4j;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

/**
 * Uploads a JDBC driver jar to the cluster: the jar is stored on the master and pushed to every
 * member, and the response carries the path the job has to declare in {@code driver_location}.
 *
 * <p>Request: {@code POST /driver-jar/upload?fileName=x.jar} with the jar as the raw body.
 */
@Slf4j
public class UploadDriverJarServlet extends BaseServlet {

    private static final long MAX_DRIVER_JAR_BYTES = 64L * 1024 * 1024;

    public UploadDriverJarServlet(NodeEngineImpl nodeEngine) {
        super(nodeEngine);
    }

    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        SeaTunnelServer seaTunnelServer = getSeaTunnelServer(false);
        if (seaTunnelServer == null) {
            resp.sendError(
                    HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                    "The SeaTunnel server is not available yet");
            return;
        }

        byte[] data;
        try {
            data = readDriverJar(req);
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            return;
        }

        try {
            String path =
                    new DriverJarService(nodeEngine, seaTunnelServer)
                            .storeDriverJar(req.getParameter("fileName"), data);
            writeJson(resp, Collections.singletonMap("path", path));
        } catch (IllegalArgumentException e) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.warn("Store driver jar failed: " + req.getParameter("fileName"), e);
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }

    private byte[] readDriverJar(HttpServletRequest req) throws IOException {
        long contentLength = req.getContentLengthLong();
        if (contentLength > MAX_DRIVER_JAR_BYTES) {
            throw new IllegalArgumentException(
                    "The driver jar is larger than " + MAX_DRIVER_JAR_BYTES + " bytes");
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        long total = 0;
        try (InputStream in = req.getInputStream()) {
            int read;
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > MAX_DRIVER_JAR_BYTES) {
                    throw new IllegalArgumentException(
                            "The driver jar is larger than " + MAX_DRIVER_JAR_BYTES + " bytes");
                }
                buffer.write(chunk, 0, read);
            }
        }
        return buffer.toByteArray();
    }
}
