/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.Locale;

/**
 * How the pool of the descriptions keeps a description that no model and no CRC refers to any more,
 * see {@link CrcDescription#RETENTION_PROPERTY}
 */
enum Retention {

    /** A weak reference: the description and its lookup tables are collected at the next garbage collection */
    WEAK,
    /** A soft reference: the description and its lookup tables are kept until the memory runs short */
    SOFT;

    // the retention of the system property: "soft" in any case, otherwise weak
    static @NonNull Retention configured() {
        String value;
        try {
            value = System.getProperty(CrcDescription.RETENTION_PROPERTY);
        } catch (SecurityException e) {
            // a security manager that does not allow to read the property: the default
            value = null;
        }
        return of(value);
    }

    static @NonNull Retention of(@Nullable final String value) {
        return value != null && "soft".equals(value.trim().toLowerCase(Locale.ROOT)) ? SOFT : WEAK;
    }

}
