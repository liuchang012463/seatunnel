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

import org.apache.seatunnel.api.sink.SinkWriter;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.arangodb.config.ArangoDBConfig;
import org.apache.seatunnel.connectors.seatunnel.arangodb.convert.ArangoDBTypeConverter;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ArangoDBSinkWriter implements SinkWriter<SeaTunnelRow, Void, Void> {

    private final ArangoDBConfig config;
    private final SeaTunnelRowType rowType;
    private final ArangoDBQueryExecutor queryExecutor;
    private final List<Map<String, Object>> buffer;

    public ArangoDBSinkWriter(ArangoDBConfig config, SeaTunnelRowType rowType) {
        this(config, rowType, new DefaultArangoDBQueryExecutor(config));
    }

    ArangoDBSinkWriter(
            ArangoDBConfig config, SeaTunnelRowType rowType, ArangoDBQueryExecutor queryExecutor) {
        this.config = config;
        this.rowType = rowType;
        this.queryExecutor = queryExecutor;
        this.buffer = new ArrayList<>(config.getMaxBatchSize());
    }

    @Override
    public void write(SeaTunnelRow element) {
        buffer.add(ArangoDBTypeConverter.toSinkRow(rowType, element));
        if (buffer.size() >= config.getMaxBatchSize()) {
            flush();
        }
    }

    void flush() {
        if (buffer.isEmpty()) {
            return;
        }
        if (config.getQueryParams().containsKey(config.getBatchDataVariable())) {
            throw new ArangoDBConnectorException(
                    ArangoDBConnectorErrorCode.CONFIG_VALIDATION_FAILED,
                    "Static query parameters must not occupy sink batch variable '"
                            + config.getBatchDataVariable()
                            + "'");
        }

        Map<String, Object> bindVariables = new LinkedHashMap<>();
        bindVariables.putAll(config.getQueryParams());
        bindVariables.put(config.getBatchDataVariable(), new ArrayList<>(buffer));
        queryExecutor.execute(config.getQuery(), bindVariables);
        buffer.clear();
    }

    @Override
    public Optional<Void> prepareCommit() {
        flush();
        return Optional.empty();
    }

    @Override
    public void abortPrepare() {
        // No two-phase commit is available. Buffered rows remain available for a retry.
    }

    @Override
    public void close() throws IOException {
        RuntimeException flushFailure = null;
        try {
            flush();
        } catch (RuntimeException e) {
            flushFailure = e;
        }

        try {
            queryExecutor.close();
        } catch (IOException e) {
            if (flushFailure != null) {
                flushFailure.addSuppressed(e);
            } else {
                throw e;
            }
        }

        if (flushFailure != null) {
            throw flushFailure;
        }
    }
}
