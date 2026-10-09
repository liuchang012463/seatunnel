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

package org.apache.seatunnel.engine.core.parse;

import org.apache.seatunnel.shade.com.typesafe.config.Config;

import org.apache.seatunnel.api.common.PluginIdentifier;
import org.apache.seatunnel.api.configuration.ReadonlyConfig;
import org.apache.seatunnel.api.options.EnvCommonOptions;
import org.apache.seatunnel.common.config.Common;
import org.apache.seatunnel.common.constants.CollectionConstants;
import org.apache.seatunnel.common.constants.PluginType;
import org.apache.seatunnel.engine.common.exception.SeaTunnelEngineException;
import org.apache.seatunnel.engine.common.loader.SeaTunnelChildFirstClassLoader;
import org.apache.seatunnel.plugin.discovery.seatunnel.SeaTunnelSinkPluginDiscovery;
import org.apache.seatunnel.plugin.discovery.seatunnel.SeaTunnelSourcePluginDiscovery;
import org.apache.seatunnel.plugin.discovery.seatunnel.SeaTunnelTransformPluginDiscovery;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class JobPluginClasspathHelper {

    /**
     * Driver jars declared by a JDBC plugin config, see {@code JdbcCommonOptions.DRIVER_LOCATION}.
     */
    public static final String DRIVER_LOCATION_KEY = "driver_location";

    private static final String JAR_SEPARATOR = ";";

    private JobPluginClasspathHelper() {}

    /**
     * Third-party JAR URLs declared in {@code env} ({@link EnvCommonOptions#JARS}), same as {@link
     * MultipleTableJobConfigParser#fillJobConfigAndCommonJars()} appends to common plugin jars.
     *
     * @param envOptions the env section of the job config as a {@link ReadonlyConfig}
     * @return list of resolved third-party JAR URLs (may be empty)
     */
    public static List<URL> thirdPartyJarsFromEnv(ReadonlyConfig envOptions) {
        String jarsOption = envOptions.getOptional(EnvCommonOptions.JARS).orElse("");
        return new ArrayList<>(
                Common.getThirdPartyJars(jarsOption).stream()
                        .map(Path::toUri)
                        .map(
                                uri -> {
                                    try {
                                        return uri.toURL();
                                    } catch (MalformedURLException e) {
                                        throw new SeaTunnelEngineException(
                                                "the uri of jar illegal:" + uri, e);
                                    }
                                })
                        .collect(Collectors.toList()));
    }

    /**
     * Driver JAR URLs declared by {@code driver_location} in the given plugin configs. Attaching
     * them to the job classloader is what lets a job pin its own JDBC driver instead of relying on
     * the driver that happens to be visible to the server.
     *
     * @param configs raw plugin {@link Config} blocks
     * @return resolved driver JAR URLs, in declaration order, without duplicates (may be empty)
     */
    public static List<URL> driverJarsFromPluginConfigs(List<? extends Config> configs) {
        if (configs == null || configs.isEmpty()) {
            return new ArrayList<>();
        }
        Set<URL> urls = new LinkedHashSet<>();
        for (Config config : configs) {
            if (config == null || !config.hasPath(DRIVER_LOCATION_KEY)) {
                continue;
            }
            String driverLocation = config.getString(DRIVER_LOCATION_KEY);
            if (driverLocation == null) {
                continue;
            }
            for (String location : driverLocation.split(JAR_SEPARATOR)) {
                String trimmed = location.trim();
                if (!trimmed.isEmpty()) {
                    urls.add(toJarUrl(trimmed));
                }
            }
        }
        return new ArrayList<>(urls);
    }

    private static URL toJarUrl(String location) {
        try {
            // A plain absolute path is accepted next to a file URI, matching the way the driver
            // location is written in the job config.
            URI uri = location.contains("://") ? URI.create(location) : Paths.get(location).toUri();
            return uri.toURL();
        } catch (MalformedURLException | IllegalArgumentException e) {
            throw new SeaTunnelEngineException("the uri of driver jar illegal:" + location, e);
        }
    }

    /**
     * Connector JAR URLs for the given plugin configs, plus {@code commonPluginJars}, matching the
     * discovery rules used when building the pipeline:
     *
     * <ul>
     *   <li>SOURCE – {@link SeaTunnelSourcePluginDiscovery#getPluginJarAndDependencyPaths}
     *   <li>TRANSFORM – {@link SeaTunnelTransformPluginDiscovery#getPluginJarPaths}
     *   <li>SINK – {@link SeaTunnelSinkPluginDiscovery#getPluginJarAndDependencyPaths}
     * </ul>
     *
     * @param configs list of raw plugin {@link Config} blocks
     * @param type the plugin type (SOURCE, TRANSFORM, or SINK)
     * @param commonPluginJars third-party jars already resolved from env; appended to the result
     * @return mutable list of connector JAR URLs (connector jars, then declared driver jars, then
     *     common jars)
     */
    public static List<URL> connectorJarList(
            List<? extends Config> configs, PluginType type, List<URL> commonPluginJars) {
        List<PluginIdentifier> factoryIds =
                configs.stream()
                        .map(ConfigParserUtil::getFactoryId)
                        .map(
                                factory ->
                                        PluginIdentifier.of(
                                                CollectionConstants.SEATUNNEL_PLUGIN,
                                                type.getType(),
                                                factory))
                        .collect(Collectors.toList());
        List<URL> jarPaths = new ArrayList<>();
        switch (type) {
            case SOURCE:
                jarPaths.addAll(
                        new SeaTunnelSourcePluginDiscovery()
                                .getPluginJarAndDependencyPaths(factoryIds));
                break;
            case TRANSFORM:
                jarPaths.addAll(
                        new SeaTunnelTransformPluginDiscovery().getPluginJarPaths(factoryIds));
                break;
            case SINK:
                jarPaths.addAll(
                        new SeaTunnelSinkPluginDiscovery()
                                .getPluginJarAndDependencyPaths(factoryIds));
                break;
            default:
                throw new IllegalArgumentException("Unsupported plugin type: " + type);
        }
        // Driver jars declared by the plugins come before env jars: the more specific declaration
        // wins when the child-first classloader resolves the driver class name.
        jarPaths.addAll(driverJarsFromPluginConfigs(configs));
        jarPaths.addAll(commonPluginJars);
        return jarPaths;
    }

    /**
     * Merged URL list for source + transform connectors (distinct), as in {@link
     * MultipleTableJobConfigParser#parse(org.apache.seatunnel.engine.core.classloader.ClassLoaderService)}.
     *
     * @param sourceConnectorJars JAR URLs resolved for source plugins
     * @param transformConnectorJars JAR URLs resolved for transform plugins
     * @return deduplicated union of both lists (source-first order)
     */
    public static List<URL> mergedSourceAndTransformJarUrls(
            List<URL> sourceConnectorJars, List<URL> transformConnectorJars) {
        return Stream.of(sourceConnectorJars, transformConnectorJars)
                .flatMap(Collection::stream)
                .distinct()
                .collect(Collectors.toList());
    }

    /**
     * Class loader for local execution when no {@link
     * org.apache.seatunnel.engine.core.classloader.ClassLoaderService} is involved: child-first
     * over {@code connectorJars} with {@code parentClassLoader} as parent.
     *
     * @param parentClassLoader the parent classloader (typically the current thread context CL)
     * @param connectorJars JAR URLs to expose in the child-first layer
     * @return a new {@link SeaTunnelChildFirstClassLoader}
     */
    public static ClassLoader createLocalPluginClassLoader(
            ClassLoader parentClassLoader, List<URL> connectorJars) {
        return new SeaTunnelChildFirstClassLoader(connectorJars, parentClassLoader);
    }
}
