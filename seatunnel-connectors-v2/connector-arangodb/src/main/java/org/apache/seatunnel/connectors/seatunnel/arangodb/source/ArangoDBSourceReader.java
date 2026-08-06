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

package org.apache.seatunnel.connectors.seatunnel.arangodb.source;

import org.apache.seatunnel.api.source.Collector;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.arangodb.client.ArangoDBClientFactory;
import org.apache.seatunnel.connectors.seatunnel.arangodb.config.ArangoDBConfig;
import org.apache.seatunnel.connectors.seatunnel.arangodb.convert.ArangoDBTypeConverter;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorException;
import org.apache.seatunnel.connectors.seatunnel.common.source.AbstractSingleSplitReader;
import org.apache.seatunnel.connectors.seatunnel.common.source.SingleSplitReaderContext;

import com.arangodb.ArangoCursor;
import com.arangodb.ArangoDB;
import com.arangodb.ArangoDatabase;
import com.arangodb.model.AqlQueryOptions;

import java.io.IOException;
import java.util.Map;

public class ArangoDBSourceReader extends AbstractSingleSplitReader<SeaTunnelRow> {

    private final SingleSplitReaderContext context;
    private final ArangoDBConfig config;
    private final SeaTunnelRowType rowType;

    private transient ArangoDB client;
    private transient ArangoDatabase database;

    public ArangoDBSourceReader(
            SingleSplitReaderContext context, ArangoDBConfig config, SeaTunnelRowType rowType) {
        this.context = context;
        this.config = config;
        this.rowType = rowType;
    }

    @Override
    public void open() {
        client = ArangoDBClientFactory.create(config);
        database = client.db(config.getDatabase());
    }

    @Override
    public void internalPollNext(Collector<SeaTunnelRow> output) {
        ArangoCursor<Map> cursor = null;
        RuntimeException queryFailure = null;
        try {
            cursor =
                    database.query(
                            config.getQuery(),
                            Map.class,
                            config.getQueryParams(),
                            new AqlQueryOptions().batchSize(config.getBatchSize()));
            while (cursor.hasNext()) {
                Map<?, ?> record = cursor.next();
                Object[] fields = new Object[rowType.getTotalFields()];
                for (int i = 0; i < rowType.getTotalFields(); i++) {
                    fields[i] =
                            ArangoDBTypeConverter.toSourceValue(
                                    rowType.getFieldType(i), record.get(rowType.getFieldName(i)));
                }
                output.collect(new SeaTunnelRow(fields));
            }
        } catch (ArangoDBConnectorException e) {
            queryFailure = e;
            throw e;
        } catch (RuntimeException e) {
            queryFailure =
                    new ArangoDBConnectorException(
                            ArangoDBConnectorErrorCode.QUERY_FAILED,
                            "Failed to execute source AQL query against database '"
                                    + config.getDatabase()
                                    + "'",
                            e);
            throw queryFailure;
        } finally {
            closeCursor(cursor, queryFailure);
        }
        context.signalNoMoreElement();
    }

    private void closeCursor(ArangoCursor<?> cursor, RuntimeException queryFailure) {
        if (cursor == null) {
            return;
        }
        try {
            cursor.close();
        } catch (IOException e) {
            if (queryFailure != null) {
                queryFailure.addSuppressed(e);
                return;
            }
            throw new ArangoDBConnectorException(
                    ArangoDBConnectorErrorCode.QUERY_FAILED,
                    "Failed to close the ArangoDB source cursor",
                    e);
        }
    }

    @Override
    public void close() {
        if (client != null) {
            client.shutdown();
            client = null;
            database = null;
        }
    }
}
