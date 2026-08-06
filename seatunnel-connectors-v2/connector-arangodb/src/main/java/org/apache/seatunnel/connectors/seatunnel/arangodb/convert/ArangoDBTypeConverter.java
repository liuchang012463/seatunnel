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
import org.apache.seatunnel.api.table.type.MapType;
import org.apache.seatunnel.api.table.type.SeaTunnelDataType;
import org.apache.seatunnel.api.table.type.SeaTunnelRow;
import org.apache.seatunnel.api.table.type.SeaTunnelRowType;
import org.apache.seatunnel.api.table.type.SqlType;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorException;

import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArangoDBTypeConverter {

    private ArangoDBTypeConverter() {}

    public static Object toSourceValue(SeaTunnelDataType<?> dataType, Object value) {
        if (dataType == null) {
            throw conversionError("SeaTunnel data type must not be null", null);
        }
        if (value == null || dataType.getSqlType() == SqlType.NULL) {
            return null;
        }

        try {
            switch (dataType.getSqlType()) {
                case BOOLEAN:
                    return toBoolean(value);
                case TINYINT:
                    return toNumber(value).byteValue();
                case SMALLINT:
                    return toNumber(value).shortValue();
                case INT:
                    return toNumber(value).intValue();
                case BIGINT:
                    return toNumber(value).longValue();
                case FLOAT:
                    return toNumber(value).floatValue();
                case DOUBLE:
                    return toNumber(value).doubleValue();
                case DECIMAL:
                    return value instanceof BigDecimal
                            ? value
                            : new BigDecimal(String.valueOf(value));
                case STRING:
                    return String.valueOf(value);
                case BYTES:
                    return toBytes(value);
                case DATE:
                    return toLocalDate(value);
                case TIME:
                    return toLocalTime(value);
                case TIMESTAMP:
                    return toLocalDateTime(value);
                case ARRAY:
                    return toSourceArray((ArrayType<?, ?>) dataType, value);
                case MAP:
                    return toSourceMap((MapType<?, ?>) dataType, value);
                case ROW:
                    return toSourceRow((SeaTunnelRowType) dataType, value);
                default:
                    throw unsupported(dataType);
            }
        } catch (ArangoDBConnectorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw conversionError(
                    "Cannot convert value '" + value + "' to SeaTunnel type " + dataType, e);
        }
    }

    public static Object toSinkValue(SeaTunnelDataType<?> dataType, Object value) {
        if (dataType == null) {
            throw conversionError("SeaTunnel data type must not be null", null);
        }
        if (value == null || dataType.getSqlType() == SqlType.NULL) {
            return null;
        }

        try {
            switch (dataType.getSqlType()) {
                case BOOLEAN:
                    return toBoolean(value);
                case TINYINT:
                    return toNumber(value).byteValue();
                case SMALLINT:
                    return toNumber(value).shortValue();
                case INT:
                    return toNumber(value).intValue();
                case BIGINT:
                    return toNumber(value).longValue();
                case FLOAT:
                    return toNumber(value).floatValue();
                case DOUBLE:
                    return toNumber(value).doubleValue();
                case DECIMAL:
                    return value instanceof BigDecimal
                            ? value
                            : new BigDecimal(String.valueOf(value));
                case STRING:
                    return String.valueOf(value);
                case BYTES:
                    return Base64.getEncoder().encodeToString(toBytes(value));
                case DATE:
                    return toLocalDate(value).toString();
                case TIME:
                    return toLocalTime(value).toString();
                case TIMESTAMP:
                    return toLocalDateTime(value).toString();
                case ARRAY:
                    return toSinkArray((ArrayType<?, ?>) dataType, value);
                case MAP:
                    return toSinkMap((MapType<?, ?>) dataType, value);
                case ROW:
                    return toSinkRow((SeaTunnelRowType) dataType, value);
                default:
                    throw unsupported(dataType);
            }
        } catch (ArangoDBConnectorException e) {
            throw e;
        } catch (RuntimeException e) {
            throw conversionError(
                    "Cannot convert SeaTunnel value '"
                            + value
                            + "' of type "
                            + dataType
                            + " to an ArangoDB value",
                    e);
        }
    }

    public static LinkedHashMap<String, Object> toSinkRow(
            SeaTunnelRowType rowType, SeaTunnelRow row) {
        if (rowType == null || row == null) {
            throw conversionError("Row type and row must not be null", null);
        }
        if (row.getArity() != rowType.getTotalFields()) {
            throw conversionError(
                    "Row arity "
                            + row.getArity()
                            + " does not match schema field count "
                            + rowType.getTotalFields(),
                    null);
        }
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < rowType.getTotalFields(); i++) {
            result.put(
                    rowType.getFieldName(i), toSinkValue(rowType.getFieldType(i), row.getField(i)));
        }
        return result;
    }

    private static Object toSourceArray(ArrayType<?, ?> arrayType, Object value) {
        SeaTunnelDataType<?> elementType = arrayType.getElementType();
        List<Object> values = toList(value);
        Object result = Array.newInstance(elementType.getTypeClass(), values.size());
        for (int i = 0; i < values.size(); i++) {
            Array.set(result, i, toSourceValue(elementType, values.get(i)));
        }
        return result;
    }

    private static List<Object> toSinkArray(ArrayType<?, ?> arrayType, Object value) {
        SeaTunnelDataType<?> elementType = arrayType.getElementType();
        List<Object> values = toList(value);
        List<Object> result = new ArrayList<>(values.size());
        for (Object element : values) {
            result.add(toSinkValue(elementType, element));
        }
        return result;
    }

    private static Map<String, Object> toSourceMap(MapType<?, ?> mapType, Object value) {
        validateStringMapKey(mapType);
        if (!(value instanceof Map)) {
            throw conversionError("Expected a map value but found " + value.getClass(), null);
        }
        SeaTunnelDataType<?> valueType = mapType.getValueType();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw conversionError("ArangoDB map keys must be strings", null);
            }
            result.put((String) entry.getKey(), toSourceValue(valueType, entry.getValue()));
        }
        return result;
    }

    private static Map<String, Object> toSinkMap(MapType<?, ?> mapType, Object value) {
        validateStringMapKey(mapType);
        if (!(value instanceof Map)) {
            throw conversionError("Expected a map value but found " + value.getClass(), null);
        }
        SeaTunnelDataType<?> valueType = mapType.getValueType();
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
            if (!(entry.getKey() instanceof String)) {
                throw conversionError("SeaTunnel map keys must be strings", null);
            }
            result.put((String) entry.getKey(), toSinkValue(valueType, entry.getValue()));
        }
        return result;
    }

    private static SeaTunnelRow toSourceRow(SeaTunnelRowType rowType, Object value) {
        Object[] fields = new Object[rowType.getTotalFields()];
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            for (int i = 0; i < rowType.getTotalFields(); i++) {
                fields[i] =
                        toSourceValue(rowType.getFieldType(i), map.get(rowType.getFieldName(i)));
            }
            return new SeaTunnelRow(fields);
        }

        Object[] sourceFields;
        if (value instanceof SeaTunnelRow) {
            sourceFields = ((SeaTunnelRow) value).getFields();
        } else if (value instanceof Object[]) {
            sourceFields = (Object[]) value;
        } else {
            throw conversionError(
                    "Expected a map or row value but found " + value.getClass(), null);
        }
        if (sourceFields.length != rowType.getTotalFields()) {
            throw conversionError("Nested row field count does not match its schema", null);
        }
        for (int i = 0; i < fields.length; i++) {
            fields[i] = toSourceValue(rowType.getFieldType(i), sourceFields[i]);
        }
        return new SeaTunnelRow(fields);
    }

    private static Map<String, Object> toSinkRow(SeaTunnelRowType rowType, Object value) {
        if (value instanceof SeaTunnelRow) {
            return toSinkRow(rowType, (SeaTunnelRow) value);
        }
        if (!(value instanceof Map)) {
            throw conversionError(
                    "Expected a map or row value but found " + value.getClass(), null);
        }
        Map<?, ?> map = (Map<?, ?>) value;
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < rowType.getTotalFields(); i++) {
            String fieldName = rowType.getFieldName(i);
            result.put(fieldName, toSinkValue(rowType.getFieldType(i), map.get(fieldName)));
        }
        return result;
    }

    private static void validateStringMapKey(MapType<?, ?> mapType) {
        if (mapType.getKeyType().getSqlType() != SqlType.STRING) {
            throw unsupported(mapType);
        }
    }

    private static List<Object> toList(Object value) {
        if (value instanceof Collection) {
            return new ArrayList<>((Collection<?>) value);
        }
        if (!value.getClass().isArray()) {
            throw conversionError("Expected an array value but found " + value.getClass(), null);
        }
        int length = Array.getLength(value);
        List<Object> result = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            result.add(Array.get(value, i));
        }
        return result;
    }

    private static Number toNumber(Object value) {
        if (value instanceof Number) {
            return (Number) value;
        }
        return new BigDecimal(String.valueOf(value));
    }

    private static Boolean toBoolean(Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        String stringValue = String.valueOf(value);
        if ("true".equalsIgnoreCase(stringValue)) {
            return true;
        }
        if ("false".equalsIgnoreCase(stringValue)) {
            return false;
        }
        throw conversionError("Expected a boolean value but found '" + value + "'", null);
    }

    private static byte[] toBytes(Object value) {
        if (value instanceof byte[]) {
            return ((byte[]) value).clone();
        }
        if (value instanceof Byte[]) {
            Byte[] boxed = (Byte[]) value;
            byte[] bytes = new byte[boxed.length];
            for (int i = 0; i < boxed.length; i++) {
                bytes[i] = boxed[i];
            }
            return bytes;
        }
        return Base64.getDecoder().decode(String.valueOf(value));
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof java.sql.Date) {
            return ((java.sql.Date) value).toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private static LocalTime toLocalTime(Object value) {
        if (value instanceof LocalTime) {
            return (LocalTime) value;
        }
        if (value instanceof java.sql.Time) {
            return ((java.sql.Time) value).toLocalTime();
        }
        String stringValue = String.valueOf(value);
        try {
            return LocalTime.parse(stringValue);
        } catch (DateTimeParseException e) {
            return OffsetTime.parse(stringValue).toLocalTime();
        }
    }

    private static LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime) {
            return (LocalDateTime) value;
        }
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toLocalDateTime();
        }
        if (value instanceof OffsetDateTime) {
            return ((OffsetDateTime) value).toLocalDateTime();
        }
        if (value instanceof Instant) {
            return LocalDateTime.ofInstant((Instant) value, ZoneOffset.UTC);
        }
        String stringValue = String.valueOf(value);
        try {
            return LocalDateTime.parse(stringValue);
        } catch (DateTimeParseException e) {
            return OffsetDateTime.parse(stringValue).toLocalDateTime();
        }
    }

    private static ArangoDBConnectorException unsupported(SeaTunnelDataType<?> dataType) {
        return new ArangoDBConnectorException(
                ArangoDBConnectorErrorCode.UNSUPPORTED_DATA_TYPE,
                "SeaTunnel data type " + dataType + " is not supported by ArangoDB connector");
    }

    private static ArangoDBConnectorException conversionError(String message, Throwable cause) {
        if (cause == null) {
            return new ArangoDBConnectorException(
                    ArangoDBConnectorErrorCode.DATA_CONVERSION_FAILED, message);
        }
        return new ArangoDBConnectorException(
                ArangoDBConnectorErrorCode.DATA_CONVERSION_FAILED, message, cause);
    }
}
