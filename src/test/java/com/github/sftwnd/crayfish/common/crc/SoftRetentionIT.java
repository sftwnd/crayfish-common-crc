/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The pool of the descriptions with soft references: maven-failsafe-plugin runs this test alone in a JVM started with
 * {@code -Dcom.github.sftwnd.crayfish.common.crc.retention=soft} and a small heap (the execution soft-retention)
 */
class SoftRetentionIT {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    // parameters outside the catalogue that no other test of this JVM uses
    private static final AtomicInteger RUNS = new AtomicInteger();

    private static CrcParameters newParameters() {
        return CrcParameters.of(48, 0x1D5BD6C9F5L, 0x200000L + RUNS.incrementAndGet(), false, true, 0x0L);
    }

    // a model of the parameters used for a calculation: the probes of its description and of its lookup tables
    private static List<WeakReference<Object>> calculate(CrcParameters parameters) {
        CrcModel model = CrcModel.of(parameters);
        assertEquals(CrcReference.crc(parameters, CHECK), model.crc(CHECK).value(), "Wrong CRC");
        return Arrays.asList(new WeakReference<>(model.description()), new WeakReference<>(model.description().engine().tables()));
    }

    // allocates until the memory runs out: the JVM clears all soft references before it throws OutOfMemoryError
    // java:S1181: the OutOfMemoryError is the purpose of the allocation, the heap is free again when the list is gone
    @SuppressWarnings("java:S1181")
    private static int runMemoryShort() {
        List<byte[]> blocks = new ArrayList<>();
        try {
            // more than any heap of the test can hold
            for (int i = 0; i < 1 << 20; i++) {
                blocks.add(new byte[1 << 20]);
            }
        } catch (OutOfMemoryError e) {
            int allocated = blocks.size();
            blocks.clear();
            return allocated;
        }
        return blocks.size();
    }

    @Test
    void testThePoolIsSoft() {
        assertSame(Retention.SOFT, CrcDescription.descriptions().retention(),
                "The property " + CrcDescription.RETENTION_PROPERTY + "=soft has to make the pool refer to the descriptions softly");
    }

    @Test
    void testUnusedDescriptionIsKept() {
        CrcParameters parameters = newParameters();
        List<WeakReference<Object>> probes = calculate(parameters);
        // soft references are cleared before the memory runs out, not by an explicit garbage collection
        for (int i = 0; i < 3; i++) {
            System.gc();
        }
        Object description = probes.get(0).get();
        assertNotNull(description, "The unused description has to be kept softly");
        assertNotNull(probes.get(1).get(), "The lookup tables of the unused description have to be kept with it");
        CrcModel model = CrcModel.of(parameters);
        assertSame(description, model.description(), "The kept description has to be given for its parameters again");
        assertSame(probes.get(1).get(), model.description().engine().tables(), "The kept lookup tables have not got to be built again");
        assertTrue(CrcDescription.descriptions().containsKey(parameters), "The entry of the kept description has to be kept");
    }

    @Test
    void testDescriptionIsCollectedWhenTheMemoryRunsShort() throws InterruptedException {
        Interner<CrcParameters, CrcDescription> descriptions = CrcDescription.descriptions();
        CrcParameters parameters = newParameters();
        List<WeakReference<Object>> probes = calculate(parameters);
        assertTrue(descriptions.containsKey(parameters), "The description has to be in the pool");
        assertTrue(runMemoryShort() > 0, "The memory has to be allocated before it runs out");
        Gc.awaitCleared(probes.get(0), "The unused description has to be collected when the memory runs short");
        Gc.awaitCleared(probes.get(1), "The lookup tables of the unused description have to be collected with it");
        Gc.await(() -> descriptions.size() >= 0 && !descriptions.containsKey(parameters), "The pool has to remove the entry of the collected description");
        assertEquals(CrcReference.crc(parameters, CHECK), CrcModel.of(parameters).crc(CHECK).value(), "A new description of the parameters has to calculate");
    }

}
