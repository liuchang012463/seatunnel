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

package org.apache.seatunnel.connectors.seatunnel.arangodb.config;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class ArangoDBConfig implements Serializable {

    private static final Pattern BIND_VARIABLE_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final List<ArangoDBHost> hosts;
    private final String username;
    private final String password;
    private final String database;
    private final String query;
    private final Map<String, String> queryParams;
    private final boolean useSsl;
    private final boolean verifyHost;
    private final int connectTimeoutMs;
    private final int batchSize;
    private final int maxBatchSize;
    private final String batchDataVariable;

    private ArangoDBConfig(
            List<ArangoDBHost> hosts,
            String username,
            String password,
            String database,
            String query,
            Map<String, String> queryParams,
            boolean useSsl,
            boolean verifyHost,
            int connectTimeoutMs,
            int batchSize,
            int maxBatchSize,
            String batchDataVariable) {
        this.hosts = Collections.unmodifiableList(new ArrayList<>(hosts));
        this.username = username;
        this.password = password;
        this.database = database;
        this.query = query;
        this.queryParams = Collections.unmodifiableMap(new LinkedHashMap<>(queryParams));
        this.useSsl = useSsl;
        this.verifyHost = verifyHost;
        this.connectTimeoutMs = connectTimeoutMs;
        this.batchSize = batchSize;
        this.maxBatchSize = maxBatchSize;
        this.batchDataVariable = batchDataVariable;
    }

    public static ArangoDBConfig fromSource(ReadonlyConfig options) {
        ArangoDBConfig config = from(options);
        requirePositive(config.batchSize, ArangoDBOptions.BATCH_SIZE.key());
        return config;
    }

    public static ArangoDBConfig fromSink(ReadonlyConfig options) {
        ArangoDBConfig config = from(options);
        requirePositive(config.maxBatchSize, ArangoDBOptions.MAX_BATCH_SIZE.key());
        requireNonBlank(config.batchDataVariable, ArangoDBOptions.BATCH_DATA_VARIABLE.key());
        if (!BIND_VARIABLE_PATTERN.matcher(config.batchDataVariable).matches()) {
            throw invalid(
                    ArangoDBOptions.BATCH_DATA_VARIABLE.key()
                            + " must be a valid AQL bind variable name");
        }
        if (config.queryParams.containsKey(config.batchDataVariable)) {
            throw invalid(
                    ArangoDBOptions.QUERY_PARAMS.key()
                            + " must not contain the sink batch variable '"
                            + config.batchDataVariable
                            + "'");
        }
        return config;
    }

    private static ArangoDBConfig from(ReadonlyConfig options) {
        if (options == null) {
            throw invalid("configuration must not be null");
        }

        List<String> configuredHosts = options.get(ArangoDBOptions.HOSTS);
        if (configuredHosts == null || configuredHosts.isEmpty()) {
            throw invalid(ArangoDBOptions.HOSTS.key() + " must contain at least one host");
        }
        List<ArangoDBHost> hosts = new ArrayList<>(configuredHosts.size());
        for (String host : configuredHosts) {
            hosts.add(ArangoDBHost.parse(host));
        }

        String database = options.get(ArangoDBOptions.DATABASE);
        String query = options.get(ArangoDBOptions.QUERY);
        requireNonBlank(database, ArangoDBOptions.DATABASE.key());
        requireNonBlank(query, ArangoDBOptions.QUERY.key());

        Optional<String> configuredUsername = options.getOptional(ArangoDBOptions.USERNAME);
        if (configuredUsername.isPresent()) {
            requireNonBlank(configuredUsername.get(), ArangoDBOptions.USERNAME.key());
        }

        int connectTimeoutMs = options.get(ArangoDBOptions.CONNECT_TIMEOUT_MS);
        requirePositive(connectTimeoutMs, ArangoDBOptions.CONNECT_TIMEOUT_MS.key());

        Map<String, String> queryParams = options.get(ArangoDBOptions.QUERY_PARAMS);
        if (queryParams == null) {
            queryParams = Collections.emptyMap();
        }
        for (String key : queryParams.keySet()) {
            requireNonBlank(key, ArangoDBOptions.QUERY_PARAMS.key() + " key");
        }

        return new ArangoDBConfig(
                hosts,
                configuredUsername.orElse(null),
                options.getOptional(ArangoDBOptions.PASSWORD).orElse(null),
                database,
                query,
                queryParams,
                options.get(ArangoDBOptions.USE_SSL),
                options.get(ArangoDBOptions.VERIFY_HOST),
                connectTimeoutMs,
                options.get(ArangoDBOptions.BATCH_SIZE),
                options.get(ArangoDBOptions.MAX_BATCH_SIZE),
                options.get(ArangoDBOptions.BATCH_DATA_VARIABLE));
    }

    private static void requirePositive(int value, String key) {
        if (value <= 0) {
            throw invalid(key + " must be greater than 0");
        }
    }

    private static void requireNonBlank(String value, String key) {
        if (value == null || value.trim().isEmpty()) {
            throw invalid(key + " must not be blank");
        }
    }

    private static ArangoDBConnectorException invalid(String message) {
        return new ArangoDBConnectorException(
                ArangoDBConnectorErrorCode.CONFIG_VALIDATION_FAILED, message);
    }

    public List<ArangoDBHost> getHosts() {
        return hosts;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getDatabase() {
        return database;
    }

    public String getQuery() {
        return query;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public boolean isUseSsl() {
        return useSsl;
    }

    public boolean isVerifyHost() {
        return verifyHost;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public int getMaxBatchSize() {
        return maxBatchSize;
    }

    public String getBatchDataVariable() {
        return batchDataVariable;
    }
}
