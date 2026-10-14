/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import java.lang.ref.Reference;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Waiting for the garbage collector in tests
 */
final class Gc {

    private static final long TIMEOUT = TimeUnit.SECONDS.toNanos(30);

    private Gc() {
    }

    /**
     * Wait until the garbage collector has cleared the reference
     * @param reference reference to an object that is not referenced otherwise
     * @param message message of the failure
     * @throws InterruptedException if the thread is interrupted
     */
    static void awaitCleared(Reference<?> reference, String message) throws InterruptedException {
        await(() -> reference.get() == null, message);
    }

    /**
     * Wait until the condition holds, collecting garbage in between
     * @param condition condition
     * @param message message of the failure
     * @throws InterruptedException if the thread is interrupted
     */
    static void await(BooleanSupplier condition, String message) throws InterruptedException {
        long start = System.nanoTime();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() - start > TIMEOUT) {
                fail(message);
            }
            System.gc();
            Thread.sleep(10);
        }
    }

}
