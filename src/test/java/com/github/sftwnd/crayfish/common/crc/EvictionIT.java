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
 * The eviction of the pool with the multi-release jar: maven-failsafe-plugin runs the integration tests with the jar
 * of the project, so that Java 9+ loads the classes of META-INF/versions/9. On every Java version the pool removes
 * the entry of a collected value on the next access, and no thread removes it
 */
class EvictionIT {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    @Test
    void testTheJarIsTested() {
        URL location = Interner.class.getProtectionDomain().getCodeSource().getLocation();
        assertTrue(location.getPath().endsWith(".jar"), "The integration tests have to run with the jar: " + location);
    }

    // a value of the key that nobody refers to: the probe of the value
    private static WeakReference<Object> intern(Interner<String, Object> interner) {
        return new WeakReference<>(interner.intern("a", key -> new Object()));
    }

    @Test
    void testNextAccessRemovesTheEntry() throws InterruptedException {
        Interner<String, Object> interner = new Interner<>(Retention.WEAK);
        WeakReference<Object> probe = intern(interner);
        Gc.awaitCleared(probe, "The unused value has to be collected");
        // nothing uses the pool, and no thread removes the entries
        assertTrue(interner.containsKey("a"), "The entry of the collected value has to stay until the next access");
        Gc.await(() -> interner.size() == 0, "The next access has to remove the entry of the collected value");
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
        Gc.await(() -> descriptions.size() >= 0 && !descriptions.containsKey(parameters), "The next access to the pool has to remove the entry of the collected description");
    }

}
