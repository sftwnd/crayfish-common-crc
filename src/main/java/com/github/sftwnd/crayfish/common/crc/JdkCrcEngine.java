/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.nio.ByteBuffer;
import java.util.zip.Checksum;

/**
 * The engine of a CRC that a class of the JDK calculates ({@link JdkCrc}), with the system property
 * {@link CrcDescription#JDK_PROPERTY}: a reflected CRC 32 bits wide of the polynomial of the class, whatever init,
 * refout and xorout. The class of the JDK calculates the data from its minimal length on and a direct buffer at its
 * address; the lookup tables of the engine calculate shorter data and single bytes, and {@code combine} stays with
 * the engine. The register of a checksum of the JDK is the reflected register of the engine.
 * <p>
 * An engine of its own, so that the calculation of the other CRCs, and of every CRC without the property, has no check
 * of the JDK: without the property this class is not loaded
 */
final class JdkCrcEngine extends CrcEngine {

    // the register of a new checksum of the JDK: the initial register of CRC-32 and CRC-32C
    private static final int JDK_REGISTER = -1;

    private final JdkCrc<?> jdk;

    private JdkCrcEngine(@NonNull final CrcParameters parameters, @NonNull final JdkCrc<?> jdk) {
        super(parameters);
        this.jdk = jdk;
    }

    // the engine of the parameters: the one of the class of the JDK that calculates them, otherwise the lookup tables
    // alone. A reflected CRC-32 has the register of the class of the JDK of its polynomial; init, refout and xorout are
    // the conversion of the engine between the register and the CRC value
    static @NonNull CrcEngine of(@NonNull final CrcParameters parameters) {
        JdkCrc<?> jdk = parameters.width() == Integer.SIZE && parameters.refin() ? JdkCrcs.of(parameters.poly()) : null;
        return jdk == null ? new CrcEngine(parameters) : new JdkCrcEngine(parameters, jdk);
    }

    @Override
    boolean isJdk() {
        return true;
    }

    // the CRC value after the bytes of the data, length > 0 checked by the caller: the class of the JDK from its minimal
    // length on, the lookup tables below
    @Override
    long update(final long value, @NonNull final byte[] data, final int offset, final int length) {
        return length >= jdk.minLength ? jdkUpdate(value, data, offset, length) : super.update(value, data, offset, length);
    }

    // the CRC value after the remaining bytes of the buffer, see the engine: the class of the JDK calculates a direct
    // buffer at its address, without the copy of the engine; a heap buffer goes the way of its array. A read-only heap
    // buffer is copied by the engine in parts, which the class of the JDK calculates: CRC32 of Java 8 would copy all
    // the bytes of such a buffer into one array
    @Override
    long update(final long value, @NonNull final ByteBuffer data) {
        return data.isDirect() && data.remaining() >= jdk.minLength ? jdkUpdate(jdk, value, data) : super.update(value, data);
    }

    /* A checksum of the JDK starts with the register 0xFFFFFFFF, and no other register can be given to it. But after
       the first 4 bytes the register of a reflected CRC-32 depends on the register XOR-ed with these bytes as a word
       only, the first byte in the low bits: each of the 4 steps of a byte XORs a byte of the data into the byte of the
       register it meets, as the 8-byte loop of the engine does with a word. So the first 4 bytes XOR-ed with
       the difference of the register and 0xFFFFFFFF bring the checksum of the JDK to the register that the bytes
       themselves give from the register of the value, and the rest of the data follows as it is. The data does not
       change: the 4 bytes are given to the checksum one at a time, update(int) takes the low 8 bits of the int.
       A value with the register 0xFFFFFFFF, the value of the empty data of CRC-32/ISO-HDLC, CRC-32/JAMCRC and CRC-32C,
       needs no difference. The minimal length of a class of the JDK is at least 4 */

    // the CRC value after the bytes of the data, calculated by the class of the JDK
    // java:S3034: the bytes are sign-extended, but update(int) takes the low 8 bits only
    @SuppressWarnings("java:S3034")
    private long jdkUpdate(final long value, @NonNull final byte[] data, final int offset, final int length) {
        Checksum checksum = jdk.checksum();
        int difference = (int) register(value) ^ JDK_REGISTER;
        if (difference == 0) {
            checksum.update(data, offset, length);
        } else {
            checksum.update(data[offset] ^ difference);
            checksum.update(data[offset + 1] ^ (difference >>> 8));
            checksum.update(data[offset + 2] ^ (difference >>> 16));
            checksum.update(data[offset + 3] ^ (difference >>> 24));
            checksum.update(data, offset + 4, length - 4);
        }
        return jdkValue(checksum);
    }

    // the CRC value after the remaining bytes of a direct buffer, calculated by the class of the JDK at the address of
    // the buffer; the position of the buffer is advanced to its limit. java:S3034 as in the other one
    @SuppressWarnings("java:S3034")
    private <C extends Checksum> long jdkUpdate(@NonNull final JdkCrc<C> jdkCrc, final long value, @NonNull final ByteBuffer data) {
        C checksum = jdkCrc.checksum();
        int difference = (int) register(value) ^ JDK_REGISTER;
        if (difference != 0) {
            checksum.update(data.get() ^ difference);
            checksum.update(data.get() ^ (difference >>> 8));
            checksum.update(data.get() ^ (difference >>> 16));
            checksum.update(data.get() ^ (difference >>> 24));
        }
        jdkCrc.update(checksum, data);
        return jdkValue(checksum);
    }

    // the CRC value of a checksum of the JDK: its value is the complement of its register
    private long jdkValue(@NonNull final Checksum checksum) {
        return value(~checksum.getValue() & 0xffffffffL);
    }

}
