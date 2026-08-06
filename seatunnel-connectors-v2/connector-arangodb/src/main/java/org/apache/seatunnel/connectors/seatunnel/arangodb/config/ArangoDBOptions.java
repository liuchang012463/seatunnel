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

import org.apache.seatunnel.api.configuration.Option;
import org.apache.seatunnel.api.configuration.Options;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class ArangoDBOptions {

    public static final String PLUGIN_NAME = "ArangoDB";

    public static final Option<List<String>> HOSTS =
            Options.key("hosts")
                    .listType()
                    .noDefaultValue()
                    .withDescription(
                            "ArangoDB hosts in host, host:port, [IPv6], or [IPv6]:port form.");

    public static final Option<String> USERNAME =
            Options.key("username")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("ArangoDB username.");

    public static final Option<String> PASSWORD =
            Options.key("password")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("ArangoDB password.");

    public static final Option<String> DATABASE =
            Options.key("database")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("ArangoDB database name.");

    public static final Option<String> QUERY =
            Options.key("query")
                    .stringType()
                    .noDefaultValue()
                    .withDescription("AQL query to execute.");

    public static final Option<Map<String, String>> QUERY_PARAMS =
            Options.key("query_params")
                    .mapType()
                    .defaultValue(Collections.emptyMap())
                    .withDescription("Static AQL bind variables.");

    public static final Option<Boolean> USE_SSL =
            Options.key("use_ssl")
                    .booleanType()
                    .defaultValue(false)
                    .withDescription("Whether to use TLS for ArangoDB connections.");

    public static final Option<Boolean> VERIFY_HOST =
            Options.key("verify_host")
                    .booleanType()
                    .defaultValue(true)
                    .withDescription("Whether to verify the TLS server hostname.");

    public static final Option<Integer> CONNECT_TIMEOUT_MS =
            Options.key("connect_timeout_ms")
                    .intType()
                    .defaultValue(30000)
                    .withDescription("Connection and request timeout in milliseconds.");

    public static final Option<Integer> BATCH_SIZE =
            Options.key("batch_size")
                    .intType()
                    .defaultValue(1000)
                    .withDescription("Number of source records requested per cursor batch.");

    public static final Option<Integer> MAX_BATCH_SIZE =
            Options.key("max_batch_size")
                    .intType()
                    .defaultValue(500)
                    .withDescription("Maximum number of sink records per AQL execution.");

    public static final Option<String> BATCH_DATA_VARIABLE =
            Options.key("batch_data_variable")
                    .stringType()
                    .defaultValue("batch")
                    .withDescription("AQL bind variable that receives the buffered row list.");

    private ArangoDBOptions() {}
}
