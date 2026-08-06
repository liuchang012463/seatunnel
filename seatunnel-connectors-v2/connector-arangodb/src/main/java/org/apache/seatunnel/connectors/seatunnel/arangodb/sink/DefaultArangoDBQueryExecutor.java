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

package org.apache.seatunnel.connectors.seatunnel.arangodb.sink;

import org.apache.seatunnel.connectors.seatunnel.arangodb.client.ArangoDBClientFactory;
import org.apache.seatunnel.connectors.seatunnel.arangodb.config.ArangoDBConfig;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorException;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.model.AqlQueryOptions;

import java.io.IOException;
import java.util.Map;

final class DefaultArangoDBQueryExecutor implements ArangoDBQueryExecutor {

    private final ArangoDB client;
    private final ArangoDatabase database;

    DefaultArangoDBQueryExecutor(ArangoDBConfig config) {
        this.client = ArangoDBClientFactory.create(config);
        this.database = client.db(config.getDatabase());
    }

    @Override
    public void execute(String query, Map<String, Object> bindVariables) {
        try (ArangoCursor<Object> cursor =
                database.query(query, Object.class, bindVariables, new AqlQueryOptions())) {
            while (cursor.hasNext()) {
                cursor.next();
            }
        } catch (IOException | RuntimeException e) {
            throw new ArangoDBConnectorException(
                    ArangoDBConnectorErrorCode.QUERY_FAILED,
                    "Failed to execute sink AQL query against database '" + database.name() + "'",
                    e);
        }
    }

    @Override
    public void close() {
        client.shutdown();
    }
}
