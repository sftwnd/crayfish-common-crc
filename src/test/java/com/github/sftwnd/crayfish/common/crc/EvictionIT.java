/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.net.URL;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The eviction of the multi-release jar: maven-failsafe-plugin runs the integration tests with the jar of the project,
 * so that Java 9+ loads the version of Eviction in META-INF/versions/9
 */
class EvictionIT {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    private static boolean java9() {
        return !System.getProperty("java.specification.version").startsWith("1.");
    }

    @Test
    void testTheJarIsTested() {
        URL location = Eviction.class.getProtectionDomain().getCodeSource().getLocation();
        assertTrue(location.getPath().endsWith(".jar"), "The integration tests have to run with the jar: " + location);
    }

    @Test
    void testVersionOfEviction() {
        assertEquals(java9(), Eviction.isAsynchronous(), "A cleaner has to remove the entries of the pool on Java 9+ only");
    }

    // a value of the key that nobody refers to: the probe of the value
    private static WeakReference<Object> intern(Interner<String, Object> interner) {
        return new WeakReference<>(interner.intern("a", key -> new Object()));
    }

    @Test
    void testCleanerRemovesTheEntry() throws InterruptedException {
        assumeTrue(java9(), "the cleaner of Java 9+");
        Interner<String, Object> interner = new Interner<>(Retention.WEAK);
        WeakReference<Object> probe = intern(interner);
        Gc.awaitCleared(probe, "The unused value has to be collected");
        // nothing uses the pool: only the cleaner removes the entry
        Gc.await(() -> !interner.containsKey("a"), "The cleaner has to remove the entry of the collected value");
    }

    // a model of the parameters used for a calculation: the probe of its description
    private static WeakReference<CrcDescription> calculate(CrcParameters parameters) {
        CrcModel model = CrcModel.of(parameters);
        assertEquals(CrcReference.crc(parameters, CHECK), model.crc(CHECK).value(), "Wrong CRC");
        return new WeakReference<>(model.description());
    }

    @Test
    void testCollectedDescriptionIsRemoved() throws InterruptedException {
        Interner<CrcParameters, CrcDescription> descriptions = CrcDescription.descriptions();
        assumeTrue(descriptions.retention() == Retention.WEAK, "the pool refers to the descriptions softly");
        CrcParameters parameters = CrcParameters.of(40, 0x4820009L, 0x12345L, true, true, 0x0L);
        WeakReference<CrcDescription> probe = calculate(parameters);
        Gc.awaitCleared(probe, "The unused description has to be collected");
        if (java9()) {
            Gc.await(() -> !descriptions.containsKey(parameters), "The cleaner has to remove the entry of the collected description");
        } else {
            Gc.await(() -> descriptions.size() >= 0 && !descriptions.containsKey(parameters), "The pool has to remove the entry of the collected description");
        }
    }

}
