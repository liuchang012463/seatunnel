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

package org.apache.seatunnel.connectors.seatunnel.jdbc.catalog.duckdb;

import org.apache.seatunnel.common.utils.JdbcUrlUtil;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

class DuckDBCatalogSessionInitSqlTest {

    @Test
    void appliesTheInlineSessionInitSqlToTheCatalogConnection() throws Exception {
        String initSql =
                "CREATE TABLE sales(id INTEGER, name VARCHAR);"
                        + " INSERT INTO sales VALUES (1, 'alpha');";
        JdbcUrlUtil.UrlInfo urlInfo = DuckDBURLParser.parse("jdbc:duckdb:");
        DuckDBCatalog catalog = new DuckDBCatalog("duckdb", urlInfo, "main", initSql);

        try {
            Connection connection = catalog.getConnection("jdbc:duckdb:");
            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery("SELECT count(*) FROM sales")) {
                Assertions.assertTrue(resultSet.next());
                Assertions.assertEquals(1, resultSet.getInt(1));
            }
        } finally {
            catalog.close();
        }
    }
}
