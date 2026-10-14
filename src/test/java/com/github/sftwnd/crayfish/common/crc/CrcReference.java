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

}
