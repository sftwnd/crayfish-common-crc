/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.net.URLClassLoader;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * The class loader of the library is collected when the application that uses it is unloaded, as when an application
 * server redeploys a web application with the library in WEB-INF/lib, an OSGi framework updates the bundle or a host
 * reloads a plugin: the jar and {@link LoaderApplication} are loaded in a class loader without a parent, which
 * calculates a CRC and is dropped. maven-failsafe-plugin runs the integration tests with the jar of the project,
 * so that Java 9+ loads the classes of META-INF/versions/9 of the multi-release jar
 */
class ClassLoaderIT {

    // the daemon thread of the cleaner of 2.0.0, which kept the class loader of the library on Java 9+
    private static final String EVICTION_THREAD = "crayfish-crc-eviction";

    // the location of the classes of the type: the jar of the library, the directory of the classes of the tests
    private static URL location(Class<?> type) {
        return type.getProtectionDomain().getCodeSource().getLocation();
    }

    // the library and the application in a class loader of their own without a parent: the application calculates
    // a CRC, keeping its model in the static field or not, and the loader is closed; the probe of the class loader
    private static WeakReference<ClassLoader> deploy(boolean keep) throws ReflectiveOperationException, IOException {
        try (URLClassLoader loader = new URLClassLoader(new URL[]{location(CrcModel.class), location(LoaderApplication.class)}, null)) {
            assertNotSame(CrcModel.class, loader.loadClass(CrcModel.class.getName()), "The class loader has to load the library of its own");
            Class<?> application = loader.loadClass(LoaderApplication.class.getName());
            long crc = (Long) application.getMethod("crc", boolean.class).invoke(null, keep);
            assertEquals(StandardCrc.CRC_32_ISCSI.check(), crc, "Wrong CRC of the application");
            return new WeakReference<>(loader);
        }
    }

    private static boolean evictionThreadAlive() {
        return Thread.getAllStackTraces().keySet().stream().anyMatch(thread -> EVICTION_THREAD.equals(thread.getName()));
    }

    // the application deployed and its class loader dropped: the loader has to be collected, and no thread of the
    // library may run, looked for before the collection; a failure reports both checks
    private static void assertCollected(boolean keep, String message) throws ReflectiveOperationException, IOException {
        WeakReference<ClassLoader> probe = deploy(keep);
        boolean thread = evictionThreadAlive();
        assertAll(() -> Gc.awaitCleared(probe, message),
                () -> assertFalse(thread, "The library has not got to start the thread " + EVICTION_THREAD));
    }

    @Test
    void testLoaderIsCollectedWhenNoModelIsKept() throws ReflectiveOperationException, IOException {
        assertCollected(false, "The class loader of the library has to be collected when the application keeps no model");
    }

    @Test
    void testLoaderIsCollectedWhenAModelIsKept() throws ReflectiveOperationException, IOException {
        assertCollected(true, "The class loader of the library has to be collected when the application keeps a model in a static field");
    }

}
