/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import java.nio.ByteBuffer;
import java.util.zip.CRC32C;

/**
 * The classes of the JDK that calculate a CRC on Java 9+: the version of the multi-release jar in META-INF/versions/9,
 * which knows java.util.zip.CRC32C of Java 9+ besides java.util.zip.CRC32
 */
final class JdkCrcs {

    // faster than the lookup tables of the library, the data continued from a value too: CRC32 from 64 bytes on (the
    // intrinsic aligns the data to 16 bytes a byte at a time), CRC32C from 8 bytes on (JMH on JDK 11 and 25, x86 with
    // CLMUL and SSE 4.2)
    private static final JdkCrc<?> CRC_32 = new JdkCrc.Crc32(64);
    private static final JdkCrc<?> CRC_32C = new Crc32c(8);

    private JdkCrcs() {
    }

    // CRC32 or CRC32C of the polynomial, null for every other polynomial; java:S1452 does not apply, see the version
    // of Java 8
    @SuppressWarnings("java:S1452")
    static JdkCrc<?> of(final long poly) {
        if (poly == JdkCrc.CRC32_POLY) {
            return CRC_32;
        }
        return poly == JdkCrc.CRC32C_POLY ? CRC_32C : null;
    }

    // java.util.zip.CRC32C, a class of Java 9+
    static final class Crc32c extends JdkCrc<CRC32C> {

        Crc32c(final int minLength) {
            super(minLength);
        }

        @Override
        CRC32C checksum() {
            return new CRC32C();
        }

        @Override
        void update(final CRC32C checksum, final ByteBuffer data) {
            checksum.update(data);
        }

    }

}
