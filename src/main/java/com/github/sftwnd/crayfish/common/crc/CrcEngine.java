/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The calculation of a {@link CrcDescription}: the CRC values are those of the Rocksoft model, the register is kept
 * in the form that suits the table-driven calculation. The register of a reflected CRC (refin) is reflected,
 * in the low bits; the register of the other CRCs is aligned to bit 63, so that the calculation does not depend
 * on the width. The lookup tables are built on the first calculation with data
 */
final class CrcEngine {

    // less than 24 bytes a byte at a time: the 8-byte loop does not pay off its setup (JMH on JDK 21, 1.1.0)
    private static final int MIN_SLICED_LENGTH = 24;

    private final int width;
    private final long mask;
    private final boolean refin;
    private final boolean refout;
    private final long xorout;
    // the shift of a register aligned to bit 63
    private final int shift;
    // the polynomial of the register: reflected in the low bits, or aligned to bit 63
    private final long poly;
    private final long initRegister;
    // the CRC value of the empty data
    private final long empty;
    // the polynomial and the init of combine: combine works with CRC values, reflected if refout
    private final long combinePoly;
    private final long combineInit;

    // built on first use without a lock: threads that build them at the same time build identical tables,
    // and the volatile field publishes them; CrcTables is immutable (its only field is final and filled before it
    // is published), so java:S3077 (volatile is not enough for a mutable object) does not apply
    @SuppressWarnings("java:S3077")
    private volatile CrcTables tables;

    CrcEngine(@NonNull final CrcParameters parameters) {
        this.width = parameters.width();
        this.mask = CrcParameters.mask(width);
        this.refin = parameters.refin();
        this.refout = parameters.refout();
        this.xorout = parameters.xorout();
        this.shift = Long.SIZE - width;
        this.poly = refin ? reflect(parameters.poly()) : parameters.poly() << shift;
        this.initRegister = refin ? reflect(parameters.init()) : parameters.init() << shift;
        this.empty = value(initRegister);
        this.combinePoly = refout ? reflect(parameters.poly()) : parameters.poly();
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
            register = alignedBytes(slices[0], register, data, blocksEnd, end);
        }
        return value(register);
    }

    // The loops below suppress java:S3034: the byte is sign-extended in "crc ^ data[offset]", and the "& 0xff" of
    // the result removes the extension; a "& 0xff" of the byte itself would cost an operation per byte.
    // A register aligned to bit 63 of a width up to 8 bits is shifted out completely by "<< 8".

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

    /* the CRC value of the data of value1 followed by length2 > 0 bytes with the CRC value value2: crc_general_combine
       of crcany (Mark Adler), in the domain of the CRC values. Appending zero bytes does not depend on refin,
       so the operators of the zero bits are reflected if refout */
    long combine(final long value1, final long value2, final long length2) {
        long[] even = new long[width];
        long[] odd = new long[width];
        // the operator of one zero bit
        if (refout) {
            odd[0] = combinePoly;
            long row = 1;
            for (int n = 1; n < width; n++) {
                odd[n] = row;
                row <<= 1;
            }
        } else {
            long row = 2;
            for (int n = 0; n < width - 1; n++) {
                odd[n] = row;
                row <<= 1;
            }
            odd[width - 1] = combinePoly;
        }
        // the operators of two and four zero bits
        gf2MatrixSquare(even, odd);
        gf2MatrixSquare(odd, even);
        // the zeros of length2 applied to value1 XOR-ed with the CRC value of the empty data; the first square
        // gives the operator of one zero byte, every next square the operator of the next power of two of the bytes
        long crc = value1 ^ combineInit;
        long[] operator = even;
        long[] previous = odd;
        for (long remaining = length2; remaining != 0; remaining >>>= 1) {
            gf2MatrixSquare(operator, previous);
            if ((remaining & 1) != 0) {
                crc = gf2MatrixTimes(operator, crc);
            }
            long[] swap = previous;
            previous = operator;
            operator = swap;
        }
        return crc ^ value2;
    }

    private static long gf2MatrixTimes(final long[] matrix, final long vector) {
        long sum = 0;
        long bits = vector;
        for (int i = 0; bits != 0; bits >>>= 1, i++) {
            if ((bits & 1) != 0) {
                sum ^= matrix[i];
            }
        }
        return sum;
    }

    private void gf2MatrixSquare(final long[] square, final long[] matrix) {
        for (int n = 0; n < width; n++) {
            square[n] = gf2MatrixTimes(matrix, matrix[n]);
        }
    }

}
