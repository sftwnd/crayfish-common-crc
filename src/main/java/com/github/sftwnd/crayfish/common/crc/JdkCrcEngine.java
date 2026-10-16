/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.nio.ByteBuffer;
import java.util.zip.Checksum;

/**
 * The engine of the models of a CRC that a class of the JDK calculates ({@link JdkCrc}), see
 * {@link CrcModel#withJdk(boolean)}: a reflected CRC 32 bits wide of the polynomial of the class, whatever init, refout
 * and xorout. The class of the JDK calculates the data from its minimal length on and a direct buffer at its address;
 * the engine of the lookup tables of the description calculates shorter data, single bytes and {@code combine}, so that
 * the parameters calculated both ways have one set of lookup tables. The register of a checksum of the JDK is
 * the reflected register of the engine.
 * <p>
 * An engine of its own, so that the calculation of the models of the lookup tables has no check of the JDK: as long as
 * no model of a class of the JDK is chosen, this class is not loaded
 */
final class JdkCrcEngine extends CrcEngine {

    // the register of a new checksum of the JDK: the initial register of CRC-32 and CRC-32C
    private static final int JDK_REGISTER = -1;

    private final JdkCrc<?> jdk;
    // the engine of the lookup tables of the description
    private final CrcEngine lookup;

    private JdkCrcEngine(@NonNull final CrcParameters parameters, @NonNull final JdkCrc<?> jdk, @NonNull final CrcEngine lookup) {
        super(parameters);
        this.jdk = jdk;
        this.lookup = lookup;
    }

    // the engine of the parameters calculated by the class of the JDK, with the engine of the lookup tables of the
    // description for the short data. A reflected CRC-32 has the register of the class of the JDK of its polynomial;
    // init, refout and xorout are the conversion of the engine between the register and the CRC value. The type
    // CrcEngine of the result keeps this class out of the verification of the caller, see CrcDescription
    static @NonNull CrcEngine of(@NonNull final CrcParameters parameters, @NonNull final JdkCrc<?> jdk, @NonNull final CrcEngine lookup) {
        return new JdkCrcEngine(parameters, jdk, lookup);
    }

    @Override
    boolean isJdk() {
        return true;
    }

    // the CRC value after the bytes of the data, length > 0 checked by the caller: the class of the JDK from its minimal
    // length on, the lookup tables below
    @Override
    long update(final long value, @NonNull final byte[] data, final int offset, final int length) {
        return length >= jdk.minLength ? jdkUpdate(value, data, offset, length) : lookup.update(value, data, offset, length);
    }

    // a single byte: the lookup tables
    @Override
    long update(final long value, final byte b) {
        return lookup.update(value, b);
    }

    // the class of the JDK has no combine: the operators of the zero bytes of the description
    @Override
    long combine(final long value1, final long value2, final long length2) {
        return lookup.combine(value1, value2, length2);
    }

    // the CRC value after the remaining bytes of the buffer, see the engine: the class of the JDK calculates a direct
    // buffer at its address, without the copy of the engine; a heap buffer goes the way of its array. A read-only heap
    // buffer is copied by the engine in parts, which the class of the JDK calculates: CRC32 of Java 8 would copy all
    // the bytes of such a buffer into one array. The parts and the array go to update of this engine, so the lookup
    // tables of this engine are not built
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
