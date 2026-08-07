package org.apache.seatunnel.connectors.seatunnel.arangodb.config;

import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorErrorCode;
import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorException;

import java.io.Serializable;
import java.util.Objects;

public final class ArangoDBHost implements Serializable {

    public static final int DEFAULT_PORT = 8529;

    private final String host;
    private final int port;

    private ArangoDBHost(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public static ArangoDBHost parse(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw invalidHost(value, "host must not be blank");
        }

        String input = value.trim();

        if (input.regionMatches(true, 0, "http://", 0, 7)) {
            input = input.substring(7);
        } else if (input.regionMatches(true, 0, "https://", 0, 8)) {
            input = input.substring(8);
        }

        int schemeSep = input.indexOf("://");
        if (schemeSep >= 0) {
            throw invalidHost(value, "URI schemes are not supported");
        }

        if (input.isEmpty()) {
            throw invalidHost(value, "host must not be blank");
        }

        if (input.charAt(0) == '[') {
            int closingBracket = input.indexOf(']');
            if (closingBracket < 0) {
                throw invalidHost(value, "missing closing bracket for IPv6 host");
            }
            String host = input.substring(1, closingBracket);
            if (host.isEmpty()) {
                throw invalidHost(value, "host must not be blank");
            }
            String remainder = input.substring(closingBracket + 1);
            if (remainder.isEmpty()) {
                return new ArangoDBHost(host, DEFAULT_PORT);
            }
            if (!remainder.startsWith(":") || remainder.length() == 1) {
                throw invalidHost(value, "expected [IPv6]:port");
            }
            return new ArangoDBHost(host, parsePort(value, remainder.substring(1)));
        }

        int firstColon = input.indexOf(':');
        int lastColon = input.lastIndexOf(':');
        if (firstColon < 0 || firstColon != lastColon) {
            return new ArangoDBHost(input, DEFAULT_PORT);
        }

        String host = input.substring(0, firstColon);
        String port = input.substring(firstColon + 1);
        if (host.isEmpty() || port.isEmpty()) {
            throw invalidHost(value, "expected host:port");
        }
        return new ArangoDBHost(host, parsePort(value, port));
    }

    private static int parsePort(String originalValue, String portValue) {
        try {
            int port = Integer.parseInt(portValue);
            if (port < 1 || port > 65535) {
                throw invalidHost(originalValue, "port must be between 1 and 65535");
            }
            return port;
        } catch (NumberFormatException e) {
            throw new ArangoDBConnectorException(
                    ArangoDBConnectorErrorCode.CONFIG_VALIDATION_FAILED,
                    "Invalid ArangoDB host '" + originalValue + "': port must be numeric",
                    e);
        }
    }

    private static ArangoDBConnectorException invalidHost(String value, String reason) {
        return new ArangoDBConnectorException(
                ArangoDBConnectorErrorCode.CONFIG_VALIDATION_FAILED,
                "Invalid ArangoDB host '" + value + "': " + reason);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof ArangoDBHost)) {
            return false;
        }
        ArangoDBHost that = (ArangoDBHost) obj;
        return port == that.port && Objects.equals(host, that.host);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, port);
    }

    @Override
    public String toString() {
        return host + ':' + port;
    }
}