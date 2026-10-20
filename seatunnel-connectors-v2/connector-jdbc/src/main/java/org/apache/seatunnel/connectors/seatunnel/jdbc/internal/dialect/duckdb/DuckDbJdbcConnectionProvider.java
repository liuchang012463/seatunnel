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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.duckdb;

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;
import org.apache.seatunnel.connectors.seatunnel.jdbc.config.JdbcConnectionConfig;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.connection.SimpleJdbcConnectionProvider;

import lombok.NonNull;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

/**
 * Writes the inline DuckDB session initialization SQL to a local temporary file.
 *
 * <p>DuckDB JDBC only accepts a local file through {@code session_init_sql_file}, so the SQL is
 * materialized per connection. The file is removed as soon as the driver has read it, which leaves
 * no persistent copy on the engine node.
 *
 * <p>This provider is selected by {@link DuckDBDialect} only, so other JDBC dialects keep using
 * {@link SimpleJdbcConnectionProvider} unchanged.
 */
public class DuckDbJdbcConnectionProvider extends SimpleJdbcConnectionProvider {

    private static final long serialVersionUID = 1L;

    /** Property carrying the full initialization SQL in the JDBC source configuration. */
    public static final String SESSION_INIT_SQL_PROPERTY = "duckdb.session_init_sql";

    public static final Option<String> SESSION_INIT_SQL_OPTION =
            Options.key(SESSION_INIT_SQL_PROPERTY).stringType().noDefaultValue();

    public DuckDbJdbcConnectionProvider(@NonNull JdbcConnectionConfig jdbcConfig) {
        super(jdbcConfig);
    }

    @Override
    public Connection getOrEstablishConnection() throws SQLException, ClassNotFoundException {
        if (isConnectionValid()) {
            return connection;
        }

        Driver driver = getLoadedDriver();
        Properties info = new Properties();
        if (jdbcConfig.getUsername().isPresent()) {
            info.setProperty("user", jdbcConfig.getUsername().get());
        }
        if (jdbcConfig.getPassword().isPresent()) {
            info.setProperty("password", jdbcConfig.getPassword().get());
        }
        Map<String, String> configuredProperties = jdbcConfig.getProperties();
        if (configuredProperties != null) {
            info.putAll(configuredProperties);
        }

        String initSql = info.getProperty(SESSION_INIT_SQL_PROPERTY);
        Path initSqlFile = null;
        String url = jdbcConfig.getUrl();
        try {
            if (initSql != null) {
                info.remove(SESSION_INIT_SQL_PROPERTY);
                initSqlFile = DuckDbSessionInitSql.writeSessionInitSql(initSql);
                url = DuckDbSessionInitSql.withSessionInitSqlFile(url, initSqlFile);
            }

            connection = driver.connect(url, info);
            if (connection == null) {
                throw new JdbcConnectorException(
                        JdbcConnectorErrorCode.NO_SUITABLE_DRIVER,
                        "No suitable driver found for the configured JDBC URL");
            }
            connection.setAutoCommit(jdbcConfig.isAutoCommit());
            return connection;
        } finally {
            DuckDbSessionInitSql.deleteQuietly(initSqlFile);
        }
    }

    /** Kept for the unit tests that verify the connection-string rewrite. */
    static String withSessionInitSqlFile(String url, Path initSqlFile) {
        return DuckDbSessionInitSql.withSessionInitSqlFile(url, initSqlFile);
    }
}
