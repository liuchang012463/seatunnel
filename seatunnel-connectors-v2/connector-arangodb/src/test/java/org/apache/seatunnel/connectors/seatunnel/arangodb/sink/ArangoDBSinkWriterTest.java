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

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.arangodb.config.ArangoDBConfig;
import org.apache.seatunnel.connectors.seatunnel.arangodb.config.ArangoDBOptions;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArangoDBSinkWriterTest {

    @Test
    void flushesAtBatchThresholdAndMergesStaticBindVariables() throws IOException {
        CapturingExecutor executor = new CapturingExecutor();
        ArangoDBSinkWriter writer = new ArangoDBSinkWriter(config(2), rowType(), executor);

        writer.write(new SeaTunnelRow(new Object[] {"alice", LocalDate.of(2026, 8, 7)}));
        assertEquals(0, executor.executions);
        writer.write(new SeaTunnelRow(new Object[] {"bob", LocalDate.of(2026, 8, 8)}));

        assertEquals(1, executor.executions);
        assertEquals("FOR doc IN @rows INSERT doc INTO people", executor.query);
        assertEquals("acme", executor.bindVariables.get("tenant"));
        List<?> rows = (List<?>) executor.bindVariables.get("rows");
        assertEquals(2, rows.size());
        assertEquals("alice", ((Map<?, ?>) rows.get(0)).get("name"));
        assertEquals("2026-08-07", ((Map<?, ?>) rows.get(0)).get("birthday"));

        writer.close();
        assertTrue(executor.closed);
        assertEquals(1, executor.executions);
    }

    @Test
    void prepareCommitFlushesRemainingRows() throws IOException {
        CapturingExecutor executor = new CapturingExecutor();
        ArangoDBSinkWriter writer = new ArangoDBSinkWriter(config(10), rowType(), executor);

        writer.write(new SeaTunnelRow(new Object[] {"alice", LocalDate.of(2026, 8, 7)}));
        writer.prepareCommit();

        assertEquals(1, executor.executions);
        assertEquals(1, ((List<?>) executor.bindVariables.get("rows")).size());
        writer.close();
        assertEquals(1, executor.executions);
    }

    private static ArangoDBConfig config(int maxBatchSize) {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put(ArangoDBOptions.HOSTS.key(), Collections.singletonList("localhost"));
        options.put(ArangoDBOptions.DATABASE.key(), "seatunnel");
        options.put(ArangoDBOptions.COLLECTION.key(), "people");
        options.put(ArangoDBOptions.QUERY.key(), "FOR doc IN @rows INSERT doc INTO people");
        options.put(ArangoDBOptions.QUERY_PARAMS.key(), Collections.singletonMap("tenant", "acme"));
        options.put(ArangoDBOptions.BATCH_DATA_VARIABLE.key(), "rows");
        options.put(ArangoDBOptions.MAX_BATCH_SIZE.key(), maxBatchSize);
        return ArangoDBConfig.fromSink(ReadonlyConfig.fromMap(options));
    }

    private static SeaTunnelRowType rowType() {
        return new SeaTunnelRowType(
                new String[] {"name", "birthday"},
                new SeaTunnelDataType<?>[] {BasicType.STRING_TYPE, LocalTimeType.LOCAL_DATE_TYPE});
    }

    private static final class CapturingExecutor implements ArangoDBQueryExecutor {
        private int executions;
        private String query;
        private Map<String, Object> bindVariables;
        private boolean closed;

        @Override
        public void execute(String query, Map<String, Object> bindVariables) {
            executions++;
            this.query = query;
            this.bindVariables = new LinkedHashMap<>(bindVariables);
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}