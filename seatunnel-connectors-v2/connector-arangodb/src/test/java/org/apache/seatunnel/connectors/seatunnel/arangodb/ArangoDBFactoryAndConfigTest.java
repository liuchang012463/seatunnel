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

package org.apache.seatunnel.connectors.seatunnel.arangodb;

import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.configuration.util.ConfigValidator;
import org.apache.seatunnel.api.configuration.util.OptionValidationException;
import org.apache.seatunnel.connectors.seatunnel.arangodb.config.ArangoDBConfig;
import org.apache.seatunnel.connectors.seatunnel.arangodb.config.ArangoDBOptions;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorException;
import org.apache.seatunnel.connectors.seatunnel.arangodb.sink.ArangoDBSinkFactory;
import org.apache.seatunnel.connectors.seatunnel.arangodb.source.ArangoDBSourceFactory;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArangoDBFactoryAndConfigTest {

    @Test
    void exposesFactoriesAndRequiredOptions() {
        ArangoDBSourceFactory sourceFactory = new ArangoDBSourceFactory();
        ArangoDBSinkFactory sinkFactory = new ArangoDBSinkFactory();

        assertEquals("ArangoDB", sourceFactory.factoryIdentifier());
        assertEquals("ArangoDB", sinkFactory.factoryIdentifier());
        assertNotNull(sourceFactory.optionRule());
        assertNotNull(sinkFactory.optionRule());

        Map<String, Object> onlyHosts = new LinkedHashMap<>();
        onlyHosts.put(ArangoDBOptions.HOSTS.key(), Collections.singletonList("localhost"));
        assertThrows(
                OptionValidationException.class,
                () ->
                        ConfigValidator.of(ReadonlyConfig.fromMap(onlyHosts))
                                .validate(sourceFactory.optionRule()));
        assertThrows(
                OptionValidationException.class,
                () ->
                        ConfigValidator.of(ReadonlyConfig.fromMap(onlyHosts))
                                .validate(sinkFactory.optionRule()));
    }

    @Test
    void appliesDefaultsAndParsesCommonOptions() {
        Map<String, Object> sourceOptions = baseOptions();
        sourceOptions.put(
                ArangoDBOptions.QUERY_PARAMS.key(), Collections.singletonMap("tenant", "acme"));

        ArangoDBConfig source = ArangoDBConfig.fromSource(ReadonlyConfig.fromMap(sourceOptions));

        Map<String, Object> sinkOptions = baseSinkOptions();
        sinkOptions.put(
                ArangoDBOptions.QUERY_PARAMS.key(), Collections.singletonMap("tenant", "acme"));
        ArangoDBConfig sink = ArangoDBConfig.fromSink(ReadonlyConfig.fromMap(sinkOptions));

        assertEquals(2, source.getHosts().size());
        assertEquals(1000, source.getBatchSize());
        assertEquals(500, sink.getMaxBatchSize());
        assertEquals("batch", sink.getBatchDataVariable());
        assertEquals("acme", sink.getQueryParams().get("tenant"));
        assertFalse(source.isUseSsl());
        assertTrue(source.isVerifyHost());
        assertEquals(30000, source.getConnectTimeoutMs());
    }

    @Test
    void rejectsInvalidSizesAndBatchVariableCollision() {
        Map<String, Object> options = baseOptions();
        options.put(ArangoDBOptions.BATCH_SIZE.key(), 0);
        Map<String, Object> invalidSourceBatchSize = options;
        assertThrows(
                ArangoDBConnectorException.class,
                () -> ArangoDBConfig.fromSource(ReadonlyConfig.fromMap(invalidSourceBatchSize)));

        options = baseSinkOptions();
        options.put(ArangoDBOptions.MAX_BATCH_SIZE.key(), -1);
        Map<String, Object> invalidBatchSize = options;
        assertThrows(
                ArangoDBConnectorException.class,
                () -> ArangoDBConfig.fromSink(ReadonlyConfig.fromMap(invalidBatchSize)));

        options = baseSinkOptions();
        options.put(ArangoDBOptions.BATCH_DATA_VARIABLE.key(), "payload");
        options.put(
                ArangoDBOptions.QUERY_PARAMS.key(),
                Collections.singletonMap("payload", "reserved"));
        Map<String, Object> collidingVariable = options;
        assertThrows(
                ArangoDBConnectorException.class,
                () -> ArangoDBConfig.fromSink(ReadonlyConfig.fromMap(collidingVariable)));
    }

    private static Map<String, Object> baseOptions() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put(ArangoDBOptions.HOSTS.key(), Arrays.asList("localhost", "[2001:db8::1]:8530"));
        options.put(ArangoDBOptions.DATABASE.key(), "seatunnel");
        options.put(ArangoDBOptions.COLLECTION.key(), "seatunnel_docs");
        options.put(ArangoDBOptions.QUERY.key(), "FOR d IN seatunnel_docs RETURN d");
        return options;
    }

    private static Map<String, Object> baseSinkOptions() {
        Map<String, Object> options = new LinkedHashMap<>();
        options.put(ArangoDBOptions.HOSTS.key(), Arrays.asList("localhost"));
        options.put(ArangoDBOptions.DATABASE.key(), "seatunnel");
        options.put(ArangoDBOptions.COLLECTION.key(), "seatunnel_docs");
        return options;
    }
}