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

package org.apache.seatunnel.connectors.seatunnel.jdbc.internal.connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;

/**
 * Tests which driver {@link SimpleJdbcConnectionProvider} picks when several drivers share one
 * class name, which is what happens when a PostgreSQL-compatible database ships its own repackaged
 * {@code org.postgresql.Driver}.
 */
class SimpleJdbcConnectionProviderDriverTest {

    private static final String DRIVER_NAME = RepackagedPostgresDriver.class.getName();

    @AfterEach
    void deregisterAmbientDrivers() throws SQLException {
        for (Driver driver : Collections.list(DriverManager.getDrivers())) {
            if (driver.getClass() == RepackagedPostgresDriver.class) {
                DriverManager.deregisterDriver(driver);
            }
        }
    }

    @Test
    void taskClassLoaderWinsOverDriverRegisteredWithSameName(@TempDir Path dir) throws Exception {
        // a driver of the server classpath, already registered and visible to the connector
        Driver ambientDriver = new RepackagedPostgresDriver();
        // the same driver class name, declared by the job on its own classpath
        ClassLoader taskClassLoader = isolatedDriverLoader(dir.resolve("job"));

        Driver loaded = SimpleJdbcConnectionProvider.loadDriver(DRIVER_NAME, taskClassLoader);

        Assertions.assertSame(taskClassLoader, loaded.getClass().getClassLoader());
        Assertions.assertNotSame(ambientDriver.getClass(), loaded.getClass());
    }

    @Test
    void fallsBackToConnectorClassLoaderWhenTaskClassLoaderCannotSeeTheDriver(@TempDir Path dir)
            throws Exception {
        ClassLoader emptyTaskClassLoader =
                new URLClassLoader(new URL[0], ClassLoader.getPlatformClassLoader());

        Driver loaded =
                SimpleJdbcConnectionProvider.loadDriver(DRIVER_NAME, emptyTaskClassLoader);

        // the classloader of the connector is the next candidate, which the test classloader stands
        // in for here
        Assertions.assertSame(RepackagedPostgresDriver.class, loaded.getClass());
    }

    @Test
    void fallsBackToConnectorClassLoaderWithoutTaskClassLoader() throws Exception {
        Driver loaded = SimpleJdbcConnectionProvider.loadDriver(DRIVER_NAME, null);

        Assertions.assertSame(RepackagedPostgresDriver.class, loaded.getClass());
    }

    /**
     * Copies the driver class into its own directory and loads it in isolation, the way a driver
     * jar is attached to a job classpath: the class is not visible to the parent classloader, so it
     * is a different class than the one of the same name on the server classpath.
     */
    private static ClassLoader isolatedDriverLoader(Path classesDir) throws IOException {
        Path classFile = classesDir.resolve(DRIVER_NAME.replace('.', '/') + ".class");
        Files.createDirectories(classFile.getParent());

        ClassLoader testClassLoader = RepackagedPostgresDriver.class.getClassLoader();
        String classResource = DRIVER_NAME.replace('.', '/') + ".class";
        try (InputStream classBytes = testClassLoader.getResourceAsStream(classResource)) {
            Assertions.assertNotNull(classBytes, "the driver class must be on the test classpath");
            Files.copy(classBytes, classFile);
        }

        return new URLClassLoader(
                new URL[] {classesDir.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
    }
}
