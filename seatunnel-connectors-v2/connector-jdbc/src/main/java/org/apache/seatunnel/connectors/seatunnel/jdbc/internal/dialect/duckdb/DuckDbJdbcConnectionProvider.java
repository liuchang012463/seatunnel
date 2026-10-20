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

import org.apache.seatunnel.connectors.seatunnel.jdbc.config.JdbcConnectionConfig;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.jdbc.exception.JdbcConnectorException;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.connection.SimpleJdbcConnectionProvider;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

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
@Slf4j
public class DuckDbJdbcConnectionProvider extends SimpleJdbcConnectionProvider {

    private static final long serialVersionUID = 1L;

    /** Property carrying the full initialization SQL in the JDBC source configuration. */
    public static final String SESSION_INIT_SQL_PROPERTY = "duckdb.session_init_sql";

    private static final String DUCKDB_URL_PREFIX = "jdbc:duckdb:";
    private static final String SESSION_INIT_SQL_FILE_OPTION = "session_init_sql_file";

    /** DuckDB JDBC refuses a session init file larger than this. */
    private static final int MAX_SESSION_INIT_SQL_BYTES = 1024 * 1024;

    private static final Set<PosixFilePermission> FILE_PERMISSIONS =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

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
                initSqlFile = writeSessionInitSql(initSql);
                url = withSessionInitSqlFile(url, initSqlFile);
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
            deleteQuietly(initSqlFile);
        }
    }

    private static Path writeSessionInitSql(String sql) throws SQLException {
        if (sql.trim().isEmpty()) {
            throw new SQLException("DuckDB session init SQL must not be blank");
        }

        byte[] bytes = sql.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_SESSION_INIT_SQL_BYTES) {
            throw new SQLException(
                    "DuckDB session init SQL exceeds " + MAX_SESSION_INIT_SQL_BYTES + " bytes");
        }

        Path file = null;
        try {
            file = Files.createTempFile("duckdb-session-init-", ".sql");
            setOwnerOnlyPermissions(file);
            Files.write(file, bytes);
            return file;
        } catch (IOException e) {
            deleteQuietly(file);
            throw new SQLException("Cannot write the DuckDB session init SQL", e);
        }
    }

    /**
     * Puts {@code session_init_sql_file} immediately after the database path, because DuckDB JDBC
     * requires it to be the first connection-string parameter.
     */
    static String withSessionInitSqlFile(String url, Path initSqlFile) {
        if (url == null || !url.startsWith(DUCKDB_URL_PREFIX)) {
            return url;
        }

        String initSqlPath = initSqlFile.toAbsolutePath().toString();
        if (initSqlPath.contains(";")
                || initSqlPath.contains("\n")
                || initSqlPath.contains("\r")) {
            throw new IllegalArgumentException(
                    "DuckDB session init SQL path must not contain semicolons or line breaks");
        }

        String rest = url.substring(DUCKDB_URL_PREFIX.length());
        int queryIndex = rest.indexOf('?');
        String pathAndParameters = queryIndex >= 0 ? rest.substring(0, queryIndex) : rest;
        String query = queryIndex >= 0 ? rest.substring(queryIndex) : "";

        int parameterIndex = pathAndParameters.indexOf(';');
        String databasePath =
                parameterIndex >= 0
                        ? pathAndParameters.substring(0, parameterIndex)
                        : pathAndParameters;

        List<String> parameters = new ArrayList<>();
        if (parameterIndex >= 0) {
            for (String parameter :
                    pathAndParameters.substring(parameterIndex + 1).split(";", -1)) {
                if (!parameter.isEmpty()
                        && !parameter.startsWith(SESSION_INIT_SQL_FILE_OPTION + "=")) {
                    parameters.add(parameter);
                }
            }
        }

        StringBuilder builder =
                new StringBuilder(DUCKDB_URL_PREFIX)
                        .append(databasePath)
                        .append(';')
                        .append(SESSION_INIT_SQL_FILE_OPTION)
                        .append('=')
                        .append(initSqlPath);
        for (String parameter : parameters) {
            builder.append(';').append(parameter);
        }
        return builder.append(query).toString();
    }

    private static void setOwnerOnlyPermissions(Path file) {
        try {
            Files.setPosixFilePermissions(file, FILE_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX development hosts keep the default permissions.
        } catch (IOException e) {
            log.warn("Cannot restrict the DuckDB session init SQL permissions: {}", file, e);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Cannot delete the DuckDB session init SQL: {}", file, e);
        }
    }
}
