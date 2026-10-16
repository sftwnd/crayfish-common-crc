/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.nio.ByteBuffer;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * A class of the JDK that calculates a reflected CRC 32 bits wide of its polynomial, which the JVM calculates with
 * the CRC instructions of the CPU: it creates the checksums of the class and updates one with a direct buffer, which
 * a {@link Checksum} of Java 8 cannot. The register of a checksum is the reflected register of {@link CrcEngine}: a new
 * checksum has the register {@code 0xFFFFFFFF}, and its value is the complement of its register. The classes the JDK
 * has are given by {@link JdkCrcs}, with the length from which each one is faster than the lookup tables of the library
 * @param <C> type of the checksums
 */
abstract class JdkCrc<C extends Checksum> {

    /** The polynomial of {@code java.util.zip.CRC32}: CRC-32/ISO-HDLC and CRC-32/JAMCRC */
    static final long CRC32_POLY = 0x04C11DB7L;
    /** The polynomial of {@code java.util.zip.CRC32C} of Java 9+: CRC-32/ISCSI */
    static final long CRC32C_POLY = 0x1EDC6F41L;

    // the shortest data that the class calculates: shorter data is calculated by the lookup tables of the library,
    // which are as fast there or faster; at least 4, the bytes that bring a checksum to a register, see CrcEngine
    final int minLength;

    JdkCrc(final int minLength) {
        this.minLength = minLength;
    }

    // a new checksum of the class: the register 0xFFFFFFFF
    abstract @NonNull C checksum();

    // updates the checksum with the remaining bytes of a direct buffer at its address; the position of the buffer is
    // advanced to its limit
    abstract void update(@NonNull C checksum, @NonNull ByteBuffer data);

    /**
     * {@code java.util.zip.CRC32}, a class of every Java version
     */
    static final class Crc32 extends JdkCrc<CRC32> {

        Crc32(final int minLength) {
            super(minLength);
        }

        @Override
        @NonNull CRC32 checksum() {
            return new CRC32();
        }

        // CRC32.update(ByteBuffer) is a method of Java 8 too
        @Override
        void update(@NonNull final CRC32 checksum, @NonNull final ByteBuffer data) {
            checksum.update(data);
        }

    }

}
