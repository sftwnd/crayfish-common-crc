/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.nio.ByteBuffer;

/**
 * The calculation of a {@link CrcDescription}: the CRC values are those of the Rocksoft model, the register is kept
 * in the form that suits the table-driven calculation. The register of a reflected CRC (refin) is reflected,
 * in the low bits; the register of the other CRCs is aligned to bit 63, so that the calculation does not depend
 * on the width. The lookup tables are built on the first calculation with data
 */
final class CrcEngine {

    // less than 24 bytes a byte at a time: the 8-byte loop does not pay off its setup (JMH on JDK 21, 1.1.0)
    private static final int MIN_SLICED_LENGTH = 24;
    // the part of a buffer without an accessible array copied at a time
    private static final int BUFFER_PART = 4096;

    private final int width;
    private final long mask;
    private final boolean refin;
    private final boolean refout;
    // a register aligned to bit 63 of a width up to 8 bits: the whole register is in the top byte
    private final boolean narrow;
    private final long xorout;
    // the shift of a register aligned to bit 63
    private final int shift;
    // the polynomial of the register: reflected in the low bits, or aligned to bit 63
    private final long poly;
    private final long initRegister;
    // the CRC value of the empty data
    private final long empty;
    // combine works with CRC values: the init XOR-ed into the first value (reflected if refout), and the polynomial
    // in the normal bit order, bit i the coefficient of x^i
    private final long combinePoly;
    private final long combineInit;

    // built on first use without a lock: threads that build them at the same time build identical tables,
    // and the volatile field publishes them; CrcTables is immutable (its only field is final and filled before it
    // is published), so java:S3077 (volatile is not enough for a mutable object) does not apply
    @SuppressWarnings("java:S3077")
    private volatile CrcTables tables;

    // the operators of the zero bytes of combine, built on the first combine like the lookup tables; Zeros is
    // immutable, so java:S3077 does not apply
    @SuppressWarnings("java:S3077")
    private volatile Zeros zeros;

    CrcEngine(@NonNull final CrcParameters parameters) {
        this.width = parameters.width();
        this.mask = CrcParameters.mask(width);
        this.refin = parameters.refin();
        this.refout = parameters.refout();
        this.narrow = !refin && width <= Byte.SIZE;
        this.xorout = parameters.xorout();
        this.shift = Long.SIZE - width;
        this.poly = refin ? reflect(parameters.poly()) : parameters.poly() << shift;
        this.initRegister = refin ? reflect(parameters.init()) : parameters.init() << shift;
        this.empty = value(initRegister);
        this.combinePoly = parameters.poly();
        this.combineInit = (refout ? reflect(parameters.init()) : parameters.init()) ^ xorout;
    }

    long mask() {
        return this.mask;
    }

    long empty() {
        return this.empty;
    }

    // whether the lookup tables are built, for the tests
    boolean hasTables() {
        return this.tables != null;
    }

    CrcTables tables() {
        CrcTables built = this.tables;
        if (built == null) {
            built = CrcTables.build(refin, poly);
            this.tables = built;
        }
        return built;
    }

    // the register of a CRC value: the final XOR and the final reflection are undone
    long register(final long value) {
        long register = (value ^ xorout) & mask;
        if (refin != refout) {
            register = reflect(register);
        }
        return refin ? register : register << shift;
    }

    // the CRC value of a register
    long value(final long register) {
        long value = refin ? register : register >>> shift;
        if (refin != refout) {
            value = reflect(value);
        }
        return value ^ xorout;
    }

    // reflects the bits of the width; the bits above the width are shifted out
    private long reflect(final long value) {
        return Long.reverse(value) >>> shift;
    }

    // the CRC value of data a bit at a time, without the lookup tables: the check value of the parameters
    long bitwise(@NonNull final byte[] data) {
        long register = initRegister;
        for (byte b : data) {
            register = CrcTables.divide(refin, poly, refin ? register ^ (b & 0xff) : register ^ ((long) (b & 0xff) << 56));
        }
        return value(register);
    }

    /* the CRC value after the bytes of the data: the offset and the length are checked by the caller, length > 0.
       The data is processed 8 bytes at a time, then the rest a byte at a time. The loops are separate methods,
       so that this method stays small enough for the JIT compiler to inline it */
    long update(final long value, @NonNull final byte[] data, final int offset, final int length) {
        long[][] slices = tables().slices;
        int blocksEnd = length < MIN_SLICED_LENGTH ? offset : offset + (length & ~7);
        int end = offset + length;
        long register = register(value);
        // less than 24 bytes do not load the tables of the blocks
        if (refin) {
            if (offset < blocksEnd) {
                register = reflectedBlocks(slices, register, data, offset, blocksEnd);
            }
            register = reflectedBytes(slices[0], register, data, blocksEnd, end);
        } else {
            if (offset < blocksEnd) {
                register = alignedBlocks(slices, register, data, offset, blocksEnd);
            }
            register = narrow ? narrowBytes(slices[0], register, data, blocksEnd, end)
                              : alignedBytes(slices[0], register, data, blocksEnd, end);
        }
        return value(register);
    }

    /* the CRC value after the remaining bytes of the buffer, length > 0 checked by the caller; the position of
       the buffer is advanced to its limit. A buffer with an accessible array is calculated in the array; a direct
       or read-only buffer is copied in parts: 4096 bytes keep the copy small and are long enough for the 8-byte loop
       to pay off, as java.util.zip.Checksum.update(ByteBuffer) of Java 9+ does */
    long update(final long value, @NonNull final ByteBuffer data) {
        int position = data.position();
        int length = data.limit() - position;
        if (data.hasArray()) {
            long updated = update(value, data.array(), data.arrayOffset() + position, length);
            data.position(position + length);
            return updated;
        }
        byte[] part = new byte[Math.min(length, BUFFER_PART)];
        long updated = value;
        for (int rest = length; rest > 0; rest -= part.length) {
            int size = Math.min(rest, part.length);
            data.get(part, 0, size);
            updated = update(updated, part, 0, size);
        }
        return updated;
    }

    // the CRC value after one byte, without an array: a checksum updated a byte at a time
    @SuppressWarnings("java:S3034")
    long update(final long value, final byte b) {
        long[] t0 = tables().slices[0];
        long register = register(value);
        register = refin ? (register >>> 8) ^ t0[(int) ((register ^ b) & 0xff)]
                         : (register << 8) ^ t0[(int) (((register >>> 56) ^ b) & 0xff)];
        return value(register);
    }

    // The loops below suppress java:S3034: the byte is sign-extended in "crc ^ data[offset]", and the "& 0xff" of
    // the result removes the extension; a "& 0xff" of the byte itself would cost an operation per byte.

    // a byte at a time, reflected register in the low bits
    @SuppressWarnings("java:S3034")
    private static long reflectedBytes(final long[] t0, final long register, final byte[] data, final int offset, final int end) {
        long crc = register;
        for (int i = offset; i < end; i++) {
            crc = (crc >>> 8) ^ t0[(int) ((crc ^ data[i]) & 0xff)];
        }
        return crc;
    }

    // a byte at a time, register aligned to bit 63
    @SuppressWarnings("java:S3034")
    private static long alignedBytes(final long[] t0, final long register, final byte[] data, final int offset, final int end) {
        long crc = register;
        for (int i = offset; i < end; i++) {
            crc = (crc << 8) ^ t0[(int) (((crc >>> 56) ^ data[i]) & 0xff)];
        }
        return crc;
    }

    // a byte at a time, register aligned to bit 63 of a width up to 8 bits: "crc << 8" is 0, so the register is
    // the table entry alone, without the shift and the XOR on every byte
    @SuppressWarnings("java:S3034")
    private static long narrowBytes(final long[] t0, final long register, final byte[] data, final int offset, final int end) {
        long crc = register;
        for (int i = offset; i < end; i++) {
            crc = t0[(int) (((crc >>> 56) ^ data[i]) & 0xff)];
        }
        return crc;
    }

    // 8 bytes at a time, reflected register in the low bits; a loop over the index of the data, so that the JIT
    // compiler removes the bounds checks
    @SuppressWarnings("java:S3034")
    private static long reflectedBlocks(final long[][] slices, final long register, final byte[] data, final int offset, final int blocksEnd) {
        long[] t0 = slices[0];
        long[] t1 = slices[1];
        long[] t2 = slices[2];
        long[] t3 = slices[3];
        long[] t4 = slices[4];
        long[] t5 = slices[5];
        long[] t6 = slices[6];
        long[] t7 = slices[7];
        long crc = register;
        for (int i = offset; i < blocksEnd; i += 8) {
            crc = t7[(int) ((crc ^ data[i]) & 0xff)]
                ^ t6[(int) (((crc >>> 8) ^ data[i + 1]) & 0xff)]
                ^ t5[(int) (((crc >>> 16) ^ data[i + 2]) & 0xff)]
                ^ t4[(int) (((crc >>> 24) ^ data[i + 3]) & 0xff)]
                ^ t3[(int) (((crc >>> 32) ^ data[i + 4]) & 0xff)]
                ^ t2[(int) (((crc >>> 40) ^ data[i + 5]) & 0xff)]
                ^ t1[(int) (((crc >>> 48) ^ data[i + 6]) & 0xff)]
                ^ t0[(int) (((crc >>> 56) ^ data[i + 7]) & 0xff)];
        }
        return crc;
    }

    // 8 bytes at a time, register aligned to bit 63
    @SuppressWarnings("java:S3034")
    private static long alignedBlocks(final long[][] slices, final long register, final byte[] data, final int offset, final int blocksEnd) {
        long[] t0 = slices[0];
        long[] t1 = slices[1];
        long[] t2 = slices[2];
        long[] t3 = slices[3];
        long[] t4 = slices[4];
        long[] t5 = slices[5];
        long[] t6 = slices[6];
        long[] t7 = slices[7];
        long crc = register;
        for (int i = offset; i < blocksEnd; i += 8) {
            crc = t7[(int) (((crc >>> 56) ^ data[i]) & 0xff)]
                ^ t6[(int) (((crc >>> 48) ^ data[i + 1]) & 0xff)]
                ^ t5[(int) (((crc >>> 40) ^ data[i + 2]) & 0xff)]
                ^ t4[(int) (((crc >>> 32) ^ data[i + 3]) & 0xff)]
                ^ t3[(int) (((crc >>> 24) ^ data[i + 4]) & 0xff)]
                ^ t2[(int) (((crc >>> 16) ^ data[i + 5]) & 0xff)]
                ^ t1[(int) (((crc >>> 8) ^ data[i + 6]) & 0xff)]
                ^ t0[(int) ((crc ^ data[i + 7]) & 0xff)];
        }
        return crc;
    }

    // whether the operators of the zero bytes of combine are built, for the tests
    boolean hasZeros() {
        return this.zeros != null;
    }

    /* the CRC value of the data of value1 followed by length2 > 0 bytes with the CRC value value2, in the domain of
       the CRC values: (value1 ^ combineInit) * x^(8 * length2) mod P ^ value2, as crc_combine of zlib 1.2.12+ and of
       crcany (Mark Adler). x^(8 * length2) is the product of the operators of 2^k zero bytes of the bits of length2,
       so the cost is a multiplication of width steps per bit set in length2. Appending zero bytes does not depend on
       refin; a CRC value of refout is reflected, so it is reflected to the normal bit order and back */
    long combine(final long value1, final long value2, final long length2) {
        long[] operators = zeros().operators;
        long crc = value1 ^ combineInit;
        if (refout) {
            crc = reflect(crc);
        }
        int k = 0;
        for (long rest = length2; rest != 0; rest >>>= 1) {
            if ((rest & 1) != 0) {
                crc = multiply(operators[k], crc);
            }
            k++;
        }
        if (refout) {
            crc = reflect(crc);
        }
        return crc ^ value2;
    }

    private Zeros zeros() {
        Zeros built = this.zeros;
        if (built == null) {
            // a length is a positive long: bits 0..62
            long[] operators = new long[Long.SIZE - 1];
            long operator = 1;
            for (int bit = 0; bit < Byte.SIZE; bit++) {
                operator = timesX(operator);
            }
            operators[0] = operator;
            for (int k = 1; k < operators.length; k++) {
                operators[k] = multiply(operators[k - 1], operators[k - 1]);
            }
            built = new Zeros(operators);
            this.zeros = built;
        }
        return built;
    }

    // a(x) * b(x) mod P in the normal bit order: the multiples x^i * b(x) of the bits i of a(x) XOR-ed
    private long multiply(final long a, final long b) {
        long product = 0;
        long multiple = b;
        for (long rest = a; rest != 0; rest >>>= 1) {
            product ^= multiple & -(rest & 1);
            multiple = timesX(multiple);
        }
        return product;
    }

    // b(x) * x mod P in the normal bit order: the term x^width is replaced by the polynomial
    private long timesX(final long b) {
        return ((b << 1) & mask) ^ (combinePoly & -((b >>> (width - 1)) & 1));
    }

    // the operators of 2^k zero bytes, x^(8 * 2^k) mod P for k = 0..62: never changed after the constructor,
    // the final field publishes them completely
    private static final class Zeros {

        private final long[] operators;

        private Zeros(@NonNull final long[] operators) {
            this.operators = operators;
        }

    }

}
