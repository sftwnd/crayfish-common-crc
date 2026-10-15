/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

/**
 * Bit-at-a-time CRC calculation of the Rocksoft model from the parameters (width, poly, init, refin, refout, xorout).
 * It does not use the code of the library and is used to calculate the expected values in tests.
 */
final class CrcReference {

    private CrcReference() {
    }

    /**
     * Calculate CRC of the buffer fragment bit by bit
     * @param parameters CRC parameters
     * @param buff buffer
     * @param offset start offset on buffer
     * @param len count of bytes to calculate
     * @return CRC value
     */
    static long crc(CrcParameters parameters, byte[] buff, int offset, int len) {
        int width = parameters.width();
        long top = 1L << (width - 1);
        long mask = top | (top - 1);
        long register = parameters.init();
        for (int i = offset; i < offset + len; i++) {
            int value = buff[i] & 0xff;
            if (parameters.refin()) {
                value = Integer.reverse(value) >>> 24;
            }
            for (int bit = 7; bit >= 0; bit--) {
                boolean feedback = (((value >>> bit) & 1) != 0) != ((register & top) != 0);
                register = (register << 1) & mask;
                if (feedback) {
                    register ^= parameters.poly();
                }
            }
        }
        if (parameters.refout()) {
            register = Long.reverse(register) >>> (Long.SIZE - width);
        }
        return (register ^ parameters.xorout()) & mask;
    }

    /**
     * Calculate CRC of the whole buffer bit by bit
     * @param parameters CRC parameters
     * @param buff buffer
     * @return CRC value
     */
    static long crc(CrcParameters parameters, byte[] buff) {
        return crc(parameters, buff, 0, buff.length);
    }

    /**
     * Calculate the CRC of two parts from their CRC values with the operators of zero bits as matrices over GF(2):
     * crc_general_combine of crcany (Mark Adler), O(width^2 * log(length2)) and independent of the combine
     * of the library
     * @param parameters CRC parameters
     * @param value1 CRC value of the first part
     * @param value2 CRC value of the second part
     * @param length2 length of the second part, more than 0
     * @return CRC value of both parts
     */
    static long combine(CrcParameters parameters, long value1, long value2, long length2) {
        int width = parameters.width();
        boolean refout = parameters.refout();
        long poly = refout ? reflect(parameters.poly(), width) : parameters.poly();
        long init = (refout ? reflect(parameters.init(), width) : parameters.init()) ^ parameters.xorout();
        long[] even = new long[width];
        long[] odd = new long[width];
        // the operator of one zero bit
        if (refout) {
            odd[0] = poly;
            for (int n = 1; n < width; n++) {
                odd[n] = 1L << (n - 1);
            }
        } else {
            for (int n = 0; n < width - 1; n++) {
                odd[n] = 1L << (n + 1);
            }
            odd[width - 1] = poly;
        }
        // the operators of two and four zero bits
        square(even, odd);
        square(odd, even);
        // the first square gives the operator of one zero byte, every next square of the next power of two
        long crc = value1 ^ init;
        long remaining = length2;
        do {
            square(even, odd);
            if ((remaining & 1) != 0) {
                crc = times(even, crc);
            }
            remaining >>>= 1;
            if (remaining == 0) {
                break;
            }
            square(odd, even);
            if ((remaining & 1) != 0) {
                crc = times(odd, crc);
            }
            remaining >>>= 1;
        } while (remaining != 0);
        return crc ^ value2;
    }

    private static long times(long[] matrix, long vector) {
        long sum = 0;
        for (int i = 0; vector != 0; vector >>>= 1, i++) {
            if ((vector & 1) != 0) {
                sum ^= matrix[i];
            }
        }
        return sum;
    }

    private static void square(long[] square, long[] matrix) {
        for (int n = 0; n < matrix.length; n++) {
            square[n] = times(matrix, matrix[n]);
        }
    }

    private static long reflect(long value, int width) {
        return Long.reverse(value) >>> (Long.SIZE - width);
    }

}
