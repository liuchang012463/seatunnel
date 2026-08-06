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

package org.apache.seatunnel.connectors.seatunnel.arangodb.client;

import org.apache.seatunnel.connectors.seatunnel.arangodb.config.ArangoDBConfig;
import org.apache.seatunnel.connectors.seatunnel.arangodb.config.ArangoDBHost;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorException;

import com.arangodb.ArangoDB;

public final class ArangoDBClientFactory {

    private ArangoDBClientFactory() {}

    public static ArangoDB create(ArangoDBConfig config) {
        try {
            ArangoDB.Builder builder =
                    new ArangoDB.Builder()
                            .timeout(config.getConnectTimeoutMs())
                            .useSsl(config.isUseSsl())
                            .verifyHost(config.isVerifyHost());
            for (ArangoDBHost host : config.getHosts()) {
                builder.host(host.getHost(), host.getPort());
            }
            if (config.getUsername() != null) {
                builder.user(config.getUsername());
            }
            if (config.getPassword() != null) {
                builder.password(config.getPassword());
            }
            return builder.build();
        } catch (RuntimeException e) {
            throw new ArangoDBConnectorException(
                    ArangoDBConnectorErrorCode.CONNECTION_FAILED,
                    "Unable to create the ArangoDB client",
                    e);
        }
    }
}
