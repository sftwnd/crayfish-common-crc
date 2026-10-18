/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CRC value as hexadecimal digits: the digits of {@link String#format}, independent of the library, for every
 * width, the check values of the catalogue and the examples of the README
 */
class CrcHexTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    // long enough for the 8-byte loops of every width
    private static final byte[] DATA = new byte[48];

    static {
        new Random(154).nextBytes(DATA);
    }

    // the value in (width + 3) / 4 lower-case digits with the leading zeros, as String.format gives them; %x gives the
    // digits of a negative long without the sign, so of a CRC-64 with the highest bit set as well
    private static String expected(long value, int width) {
        return String.format(Locale.ROOT, "%0" + (width + 3) / 4 + "x", value);
    }

    @Test
    void testEveryWidth() {
        for (int width = 1; width <= 64; width++) {
            long mask = CrcParameters.mask(width);
            for (int kind = 0; kind < 4; kind++) {
                CrcModel model = CrcModel.of(width, 0x42F0E1EBA9EA3693L & mask, 0x0123456789ABCDEFL & mask, (kind & 1) != 0, (kind & 2) != 0,
                        0xFEDCBA9876543210L & mask);
                // all bits of the width set (the bits of the value above the width are ignored), the lowest and the
                // highest bit of the width, and the CRCs of the empty data, of the data and of the check data
                List<CRC> crcs = Arrays.asList(model.crc(-1L, 1), model.crc(1L, 1), model.crc(1L << (width - 1), 1), model.crc(),
                        model.crc(DATA), model.crc(CHECK));
                for (CRC crc : crcs) {
                    String hex = crc.hex();
                    assertEquals(expected(crc.value(), width), hex, crc + ": wrong digits");
                    assertEquals((width + 3) / 4, hex.length(), crc + ": the digits have to be those of the width");
                    assertEquals(crc.value(), Long.parseUnsignedLong(hex, 16), crc + ": the digits have to give the value back");
                    assertTrue(crc.toString().contains("[crc:0x" + hex + ','), crc + ": toString has to show the digits");
                }
            }
        }
    }

    @Test
    void testCheckValuesOfTheCatalogue() {
        for (StandardCrc standard : StandardCrc.values()) {
            int width = standard.parameters().width();
            for (String name : standard.names()) {
                assertEquals(expected(standard.check(), width), CrcModel.of(name).crc(CHECK).hex(), name + ": the digits have to be those of the check value");
            }
        }
    }

    @Test
    void testExamples() {
        // the examples of the README and of the javadoc, the leading zeros of CRC-15/CAN and of CRC-17/CAN-FD,
        // a CRC-64 with the highest bit set, the CRC of the empty data
        assertEquals("cbf43926", CrcModel.of("CRC-32").crc(CHECK).hex(), "Wrong digits of CRC-32");
        assertEquals("daf", CrcModel.of("CRC-12/UMTS").crc(CHECK).hex(), "Wrong digits of CRC-12/UMTS");
        assertEquals("4", CrcModel.of("CRC-3/GSM").crc(CHECK).hex(), "Wrong digits of CRC-3/GSM");
        assertEquals("059e", CrcModel.of("CRC-15/CAN").crc(CHECK).hex(), "Wrong digits of CRC-15/CAN");
        assertEquals("04f03", CrcModel.of("CRC-17/CAN-FD").crc(CHECK).hex(), "Wrong digits of CRC-17/CAN-FD");
        assertEquals("995dc9bbdf1939fa", CrcModel.of("CRC-64/XZ").crc(CHECK).hex(), "Wrong digits of CRC-64/XZ");
        assertEquals("0000", CrcModel.of("CRC-16/XMODEM").crc().hex(), "Wrong digits of the CRC-16/XMODEM of the empty data");
        assertEquals("00000000", CrcModel.of("CRC-32").crc().hex(), "Wrong digits of the CRC-32 of the empty data");
    }

}
