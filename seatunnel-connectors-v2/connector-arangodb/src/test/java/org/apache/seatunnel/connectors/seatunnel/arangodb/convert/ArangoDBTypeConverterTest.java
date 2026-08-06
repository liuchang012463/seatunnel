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

package org.apache.seatunnel.connectors.seatunnel.arangodb.convert;

import org.apache.seatunnel.api.table.type.ArrayType;
import org.apache.seatunnel.api.table.type.BasicType;
import org.apache.seatunnel.api.table.type.DecimalType;
import org.apache.seatunnel.api.table.type.LocalTimeType;
import org.apache.seatunnel.api.table.type.MapType;
import org.apache.seatunnel.api.table.type.PrimitiveByteArrayType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorException;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArangoDBTypeConverterTest {

    @Test
    void convertsNullPrimitiveDecimalAndBytes() {
        assertNull(ArangoDBTypeConverter.toSourceValue(BasicType.STRING_TYPE, null));
        assertEquals(42, ArangoDBTypeConverter.toSourceValue(BasicType.INT_TYPE, 42L));
        assertEquals(42L, ArangoDBTypeConverter.toSourceValue(BasicType.LONG_TYPE, "42"));
        assertEquals(true, ArangoDBTypeConverter.toSourceValue(BasicType.BOOLEAN_TYPE, "true"));
        assertEquals(
                new BigDecimal("12.30"),
                ArangoDBTypeConverter.toSourceValue(new DecimalType(10, 2), "12.30"));

        byte[] bytes = {1, 2, 3};
        String encoded = Base64.getEncoder().encodeToString(bytes);
        assertArrayEquals(
                bytes,
                (byte[])
                        ArangoDBTypeConverter.toSourceValue(
                                PrimitiveByteArrayType.INSTANCE, encoded));
        assertEquals(
                encoded, ArangoDBTypeConverter.toSinkValue(PrimitiveByteArrayType.INSTANCE, bytes));
    }

    @Test
    void convertsDatesAndTimestampsAsIsoValues() {
        LocalDate date = LocalDate.of(2026, 8, 7);
        LocalTime time = LocalTime.of(12, 13, 14);
        LocalDateTime timestamp = LocalDateTime.of(date, time);

        assertEquals(
                date,
                ArangoDBTypeConverter.toSourceValue(
                        LocalTimeType.LOCAL_DATE_TYPE, date.toString()));
        assertEquals(
                time,
                ArangoDBTypeConverter.toSourceValue(
                        LocalTimeType.LOCAL_TIME_TYPE, time.toString()));
        assertEquals(
                timestamp,
                ArangoDBTypeConverter.toSourceValue(
                        LocalTimeType.LOCAL_DATE_TIME_TYPE, timestamp.toString()));
        assertEquals(
                date.toString(),
                ArangoDBTypeConverter.toSinkValue(LocalTimeType.LOCAL_DATE_TYPE, date));
        assertEquals(
                timestamp.toString(),
                ArangoDBTypeConverter.toSinkValue(LocalTimeType.LOCAL_DATE_TIME_TYPE, timestamp));
    }

    @Test
    void convertsNestedArrayMapAndRowRecursively() {
        MapType<String, Integer> scoresType =
                new MapType<>(BasicType.STRING_TYPE, BasicType.INT_TYPE);
        SeaTunnelRowType nestedType =
                new SeaTunnelRowType(
                        new String[] {"tags", "scores"},
                        new SeaTunnelDataType<?>[] {ArrayType.STRING_ARRAY_TYPE, scoresType});

        Map<String, Object> sourceValue = new LinkedHashMap<>();
        sourceValue.put("tags", Arrays.asList("a", "b"));
        sourceValue.put("scores", Collections.singletonMap("quality", 7L));

        SeaTunnelRow nested =
                (SeaTunnelRow) ArangoDBTypeConverter.toSourceValue(nestedType, sourceValue);
        assertArrayEquals(new String[] {"a", "b"}, (String[]) nested.getField(0));
        assertEquals(7, ((Map<?, ?>) nested.getField(1)).get("quality"));

        Map<?, ?> sinkValue = (Map<?, ?>) ArangoDBTypeConverter.toSinkValue(nestedType, nested);
        assertEquals(Arrays.asList("a", "b"), sinkValue.get("tags"));
        assertEquals(7, ((Map<?, ?>) sinkValue.get("scores")).get("quality"));
        assertTrue(sinkValue instanceof LinkedHashMap);
    }

    @Test
    void rejectsNonStringMapKeySchema() {
        MapType<Integer, String> invalidType =
                new MapType<>(BasicType.INT_TYPE, BasicType.STRING_TYPE);
        Map<Integer, String> value = Collections.singletonMap(1, "one");

        assertThrows(
                ArangoDBConnectorException.class,
                () -> ArangoDBTypeConverter.toSinkValue(invalidType, value));
    }
}
