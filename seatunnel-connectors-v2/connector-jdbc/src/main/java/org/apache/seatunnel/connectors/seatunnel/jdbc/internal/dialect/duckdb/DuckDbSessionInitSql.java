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

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Materializes the inline DuckDB session init SQL as a local file and rewrites a DuckDB JDBC URL to
 * reference it.
 *
 * <p>DuckDB JDBC only accepts a local file through {@code session_init_sql_file}. The file is
 * removed as soon as the driver has read it, so no persistent copy is left on the engine node.
 */
@Slf4j
public final class DuckDbSessionInitSql {

    public static final String SESSION_INIT_SQL_FILE_OPTION = "session_init_sql_file";

    private static final String DUCKDB_URL_PREFIX = "jdbc:duckdb:";

    /** DuckDB JDBC refuses a session init file larger than this. */
    private static final int MAX_SESSION_INIT_SQL_BYTES = 1024 * 1024;

    private static final Set<PosixFilePermission> FILE_PERMISSIONS =
            EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private DuckDbSessionInitSql() {}

    public static Path writeSessionInitSql(String sql) throws SQLException {
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
    public static String withSessionInitSqlFile(String url, Path initSqlFile) {
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

    public static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Cannot delete the DuckDB session init SQL: {}", file, e);
        }
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
}
