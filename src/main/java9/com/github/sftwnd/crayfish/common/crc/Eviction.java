/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import java.lang.ref.Cleaner;

/**
 * How the Interner removes the entries of the collected values on Java 9+: the version of the multi-release jar
 * in META-INF/versions/9. The thread of a cleaner removes the entry of a value after the value has been collected,
 * the pool does not poll a queue
 */
final class Eviction {

    // the thread of a cleaner is a daemon thread, see Cleaner.create(ThreadFactory)
    private static final Cleaner CLEANER = Cleaner.create(Eviction::newThread);

    private Eviction() {
    }

    static boolean isAsynchronous() {
        return true;
    }

    static void register(final Object referent, final Runnable action) {
        CLEANER.register(referent, action);
    }

    private static Thread newThread(final Runnable runnable) {
        Thread thread = new Thread(runnable, "crayfish-crc-eviction");
        thread.setDaemon(true);
        // the thread lives as long as the library is used: it must not keep the class loader of the first caller
        thread.setContextClassLoader(null);
        return thread;
    }

}
