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

package org.apache.seatunnel.connectors.seatunnel.arangodb.config;

import org.apache.seatunnel.connectors.seatunnel.arangodb.exception.ArangoDBConnectorException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ArangoDBHostTest {

    @Test
    void parsesHostAndDefaultPort() {
        ArangoDBHost host = ArangoDBHost.parse("db.example.com");

        assertEquals("db.example.com", host.getHost());
        assertEquals(8529, host.getPort());
    }

    @Test
    void parsesExplicitPortAndIpv6() {
        assertEquals(8530, ArangoDBHost.parse("db.example.com:8530").getPort());

        ArangoDBHost ipv6 = ArangoDBHost.parse("[2001:db8::1]:8531");
        assertEquals("2001:db8::1", ipv6.getHost());
        assertEquals(8531, ipv6.getPort());

        ArangoDBHost ipv6WithoutPort = ArangoDBHost.parse("2001:db8::2");
        assertEquals("2001:db8::2", ipv6WithoutPort.getHost());
        assertEquals(8529, ipv6WithoutPort.getPort());

        ArangoDBHost schemed = ArangoDBHost.parse("http://db.example.com:8529");
        assertEquals("db.example.com", schemed.getHost());
        assertEquals(8529, schemed.getPort());
    }

    @Test
    void rejectsMalformedHosts() {
        assertThrows(ArangoDBConnectorException.class, () -> ArangoDBHost.parse("host:not-a-port"));
        assertThrows(ArangoDBConnectorException.class, () -> ArangoDBHost.parse("[::1"));
        assertThrows(ArangoDBConnectorException.class, () -> ArangoDBHost.parse("host:70000"));
        assertThrows(ArangoDBConnectorException.class, () -> ArangoDBHost.parse("ftp://host:8529"));
        assertThrows(ArangoDBConnectorException.class, () -> ArangoDBHost.parse("http://"));
    }
}