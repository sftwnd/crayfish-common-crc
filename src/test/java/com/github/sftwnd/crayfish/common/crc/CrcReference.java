/*
 * Copyright (c) 2017-20xx Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

/**
 * Bit-at-a-time CRC calculation from the model parameters (width, poly, init, refin, refot, xorot).
 * It does not use the lookup tables of CrcModel and is used to calculate the expected values in tests.
 */
final class CrcReference {

    private CrcReference() {
    }

    /**
     * Calculate CRC of the buffer fragment bit by bit
     * @param description CRC parameters
     * @param buff buffer
     * @param offset start offset on buffer
     * @param len count of bytes to calculate
     * @return CRC value
     */
    static long crc(CrcDescription description, byte[] buff, int offset, int len) {
        int width = description.getWidth();
        long top = 1L << (width - 1);
        long mask = top | (top - 1);
        long poly = description.getPoly() & mask;
        long register = description.getInit() & mask;
        for (int i = offset; i < offset + len; i++) {
            int value = buff[i] & 0xff;
            if (description.isRefin()) {
                value = Integer.reverse(value) >>> 24;
            }
            for (int bit = 7; bit >= 0; bit--) {
                boolean feedback = (((value >>> bit) & 1) != 0) != ((register & top) != 0);
                register = (register << 1) & mask;
                if (feedback) {
                    register ^= poly;
                }
            }
        }
        if (description.isRefot()) {
            register = Long.reverse(register) >>> (Long.SIZE - width);
        }
        return (register ^ description.getXorot()) & mask;
    }

    /**
     * Calculate CRC of the whole buffer bit by bit
     * @param description CRC parameters
     * @param buff buffer
     * @return CRC value
     */
    static long crc(CrcDescription description, byte[] buff) {
        return crc(description, buff, 0, buff.length);
    }

}
