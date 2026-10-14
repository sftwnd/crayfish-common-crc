/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The lookup tables of slicing-by-8: table 0 is the register after a byte from a zero register, table k after the byte
 * and k zero bytes. Built by {@link CrcEngine} and never changed afterwards: the final field publishes them completely
 */
final class CrcTables {

    // 8 bytes per step
    static final int SLICES = 8;

    final long[][] slices;

    private CrcTables(@NonNull final long[][] slices) {
        this.slices = slices;
    }

    // the tables of a reflected register in the low bits (refin), or of a register aligned to bit 63
    static @NonNull CrcTables build(final boolean reflected, final long poly) {
        long[][] slices = new long[SLICES][256];
        long[] table = slices[0];
        for (int k = 0; k < 256; k++) {
            table[k] = divide(reflected, poly, reflected ? k : (long) k << 56);
        }
        for (int t = 1; t < SLICES; t++) {
            for (int k = 0; k < 256; k++) {
                long previous = slices[t - 1][k];
                slices[t][k] = reflected ? (previous >>> 8) ^ table[(int) (previous & 0xff)]
                                         : (previous << 8) ^ table[(int) (previous >>> 56)];
            }
        }
        return new CrcTables(slices);
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
