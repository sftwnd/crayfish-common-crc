/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * The classes of the JDK that calculate a CRC, see {@link JdkCrc}: {@code java.util.zip.CRC32} before Java 9.
 * The multi-release jar has another version of this class for Java 9+ (src/main/java9), which knows
 * {@code java.util.zip.CRC32C} as well. The class directory of a build and a jar without the Multi-Release attribute
 * use this version on every Java version, so CRC-32C is calculated by the lookup tables of the library there.
 * Methods, not constants: javac would copy the value of a constant of this version into the callers
 */
final class JdkCrcs {

    // CRC32 of Java 8 is faster than the lookup tables of the library from 128 bytes on, as fast or slower below (JMH
    // on JDK 8, x86 with CLMUL: the intrinsic aligns the data to 16 bytes a byte at a time)
    private static final JdkCrc<?> CRC_32 = new JdkCrc.Crc32(128);

    private JdkCrcs() {
    }

    // the class of the JDK of a reflected CRC 32 bits wide of the polynomial: CRC32 of its polynomial; Java 8 has
    // no CRC32C, so null for every other polynomial. The class is chosen at run time, and the wildcard keeps the type
    // of its checksums with it, so that a direct buffer is given to its checksum without a cast: java:S1452
    // (a wildcard in the return type) does not apply
    @SuppressWarnings("java:S1452")
    static @Nullable JdkCrc<?> of(final long poly) {
        return poly == JdkCrc.CRC32_POLY ? CRC_32 : null;
    }

}
