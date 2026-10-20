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
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.connection.SimpleJdbcConnectionProvider;
import org.apache.seatunnel.connectors.seatunnel.jdbc.internal.dialect.mysql.MysqlDialect;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;

class DuckDbJdbcConnectionProviderTest {

    private static final String DRIVER = "org.duckdb.DuckDBDriver";

    @Test
    void runsTheInlineSessionInitSqlBeforeTheQuery() throws Exception {
        JdbcConnectionConfig config =
                config(
                        "jdbc:duckdb::memory:",
                        "CREATE TABLE t(a int); INSERT INTO t VALUES (7);");

        try (Connection connection =
                        new DuckDbJdbcConnectionProvider(config).getOrEstablishConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT a FROM t")) {
            Assertions.assertTrue(resultSet.next());
            Assertions.assertEquals(7, resultSet.getInt(1));
        }
    }

    @Test
    void putsSessionInitSqlFileFirstAndKeepsOtherParameters() {
        Path initSql = Paths.get("/tmp/duckdb-init.sql");

        Assertions.assertEquals(
                "jdbc:duckdb:;session_init_sql_file=/tmp/duckdb-init.sql",
                DuckDbJdbcConnectionProvider.withSessionInitSqlFile(
                        "jdbc:duckdb:", initSql));

        Assertions.assertEquals(
                "jdbc:duckdb:/tmp/db.duckdb;session_init_sql_file=/tmp/duckdb-init.sql;threads=2",
                DuckDbJdbcConnectionProvider.withSessionInitSqlFile(
                        "jdbc:duckdb:/tmp/db.duckdb;threads=2", initSql));
    }

    @Test
    void replacesAnExistingSessionInitSqlFile() {
        Assertions.assertEquals(
                "jdbc:duckdb:;session_init_sql_file=/tmp/new.sql;threads=2",
                DuckDbJdbcConnectionProvider.withSessionInitSqlFile(
                        "jdbc:duckdb:;session_init_sql_file=/tmp/old.sql;threads=2",
                        Paths.get("/tmp/new.sql")));
    }

    @Test
    void leavesNonDuckDbUrlsAlone() {
        String url = "jdbc:mysql://localhost:3306/test";
        Assertions.assertEquals(
                url,
                DuckDbJdbcConnectionProvider.withSessionInitSqlFile(
                        url, Paths.get("/tmp/duckdb-init.sql")));
    }

    @Test
    void rejectsBlankSessionInitSql() {
        JdbcConnectionConfig config = config("jdbc:duckdb::memory:", "   ");

        SQLException exception =
                Assertions.assertThrows(
                        SQLException.class,
                        () -> new DuckDbJdbcConnectionProvider(config).getOrEstablishConnection());

        Assertions.assertTrue(exception.getMessage().contains("must not be blank"));
    }

    @Test
    void rejectsOversizedSessionInitSql() {
        char[] chars = new char[1024 * 1024 + 1];
        Arrays.fill(chars, 'x');
        JdbcConnectionConfig config = config("jdbc:duckdb::memory:", new String(chars));

        SQLException exception =
                Assertions.assertThrows(
                        SQLException.class,
                        () -> new DuckDbJdbcConnectionProvider(config).getOrEstablishConnection());

        Assertions.assertTrue(exception.getMessage().contains("exceeds"));
    }

    @Test
    void duckDbDialectSelectsTheDuckDbProvider() {
        JdbcConnectionConfig config = config("jdbc:duckdb:", null);

        Assertions.assertTrue(
                new DuckDBDialect().getJdbcConnectionProvider(config)
                        instanceof DuckDbJdbcConnectionProvider);
    }

    @Test
    void otherDialectsKeepTheSimpleProvider() {
        JdbcConnectionConfig config = config("jdbc:mysql://localhost:3306/test", null);

        Assertions.assertTrue(
                new MysqlDialect().getJdbcConnectionProvider(config)
                        instanceof SimpleJdbcConnectionProvider);
        Assertions.assertFalse(
                new MysqlDialect().getJdbcConnectionProvider(config)
                        instanceof DuckDbJdbcConnectionProvider);
    }

    @Test
    void connectsWithoutAnInlineSessionInitSql() throws Exception {
        JdbcConnectionConfig config = config("jdbc:duckdb::memory:", null);

        try (Connection connection =
                        new DuckDbJdbcConnectionProvider(config).getOrEstablishConnection();
                Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("SELECT 42")) {
            Assertions.assertTrue(resultSet.next());
            Assertions.assertEquals(42, resultSet.getInt(1));
        }
    }

    private static JdbcConnectionConfig config(String url, String initSql) {
        return JdbcConnectionConfig.builder()
                .url(url)
                .driverName(DRIVER)
                .properties(
                        initSql == null
                                ? Collections.emptyMap()
                                : Collections.singletonMap(
                                        DuckDbJdbcConnectionProvider.SESSION_INIT_SQL_PROPERTY,
                                        initSql))
                .build();
    }
}
