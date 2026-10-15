/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The lookup tables of slicing-by-8: table 0 is the register after a byte from a zero register, table k after the byte
 * and k zero bytes. A CRC up to 32 bits wide has the tables in one array of ints, table k at {@code k * 256}, for the
 * register of an int; a wider CRC has them in an array of longs per table. The unused form is empty. Built by
 * {@link CrcEngine} and never changed afterwards: the final fields publish them completely
 */
final class CrcTables {

    // 8 bytes per step
    static final int SLICE_COUNT = 8;
    // the entries of a table
    static final int TABLE_SIZE = 256;

    private static final int[] NO_WORDS = new int[0];
    private static final long[][] NO_SLICES = new long[0][];

    // the tables of a CRC up to 32 bits wide, table k at k * 256; empty for a wider CRC
    final int[] words;
    // the tables of a wider CRC, one array per table; empty for a CRC up to 32 bits wide
    final long[][] slices;

    private CrcTables(@NonNull final int[] words, @NonNull final long[][] slices) {
        this.words = words;
        this.slices = slices;
    }

    /* the tables of the register of the engine: reflected in the low bits (refin), or aligned to bit 63. The tables of
       a CRC up to 32 bits wide are those of the register of an int: the low 32 bits, or the top 32 bits shifted down
       to bit 31 */
    static @NonNull CrcTables build(final boolean reflected, final long poly, final int width) {
        return width <= Integer.SIZE ? buildWords(reflected, reflected ? (int) poly : (int) (poly >>> Integer.SIZE))
                                     : buildSlices(reflected, poly);
    }

    private static @NonNull CrcTables buildWords(final boolean reflected, final int poly) {
        int[] words = new int[SLICE_COUNT * TABLE_SIZE];
        for (int k = 0; k < TABLE_SIZE; k++) {
            words[k] = divide(reflected, poly, reflected ? k : k << 24);
        }
        for (int t = 1; t < SLICE_COUNT; t++) {
            int from = (t - 1) * TABLE_SIZE;
            int to = t * TABLE_SIZE;
            for (int k = 0; k < TABLE_SIZE; k++) {
                int previous = words[from + k];
                words[to + k] = reflected ? (previous >>> 8) ^ words[previous & 0xff]
                                          : (previous << 8) ^ words[previous >>> 24];
            }
        }
        return new CrcTables(words, NO_SLICES);
    }

    private static @NonNull CrcTables buildSlices(final boolean reflected, final long poly) {
        long[][] slices = new long[SLICE_COUNT][TABLE_SIZE];
        long[] table = slices[0];
        for (int k = 0; k < 256; k++) {
            table[k] = divide(reflected, poly, reflected ? k : (long) k << 56);
        }
        for (int t = 1; t < SLICE_COUNT; t++) {
            for (int k = 0; k < 256; k++) {
                long previous = slices[t - 1][k];
                slices[t][k] = reflected ? (previous >>> 8) ^ table[(int) (previous & 0xff)]
                                         : (previous << 8) ^ table[(int) (previous >>> 56)];
            }
        }
        return new CrcTables(NO_WORDS, slices);
    }

    // 8 steps of the polynomial division of an int register: the register after a byte XOR-ed into it, see the long one
    static int divide(final boolean reflected, final int poly, final int register) {
        int crc = register;
        for (int bit = 0; bit < 8; bit++) {
            if (reflected) {
                crc = (crc & 1) != 0 ? (crc >>> 1) ^ poly : crc >>> 1;
            } else {
                crc = crc < 0 ? (crc << 1) ^ poly : crc << 1;
            }
        }
        return crc;
    }

    // 8 steps of the polynomial division: the register after a byte XOR-ed into it. A reflected register is shifted
    // to the right, a register aligned to bit 63 to the left; the bits of the byte beyond a register narrower than
    // 8 bits are the next message bits, so the step works for every width
    static long divide(final boolean reflected, final long poly, final long register) {
        long crc = register;
        for (int bit = 0; bit < 8; bit++) {
            if (reflected) {
                crc = (crc & 1) != 0 ? (crc >>> 1) ^ poly : crc >>> 1;
            } else {
                crc = crc < 0 ? (crc << 1) ^ poly : crc << 1;
            }
        }
        return crc;
    }

}
