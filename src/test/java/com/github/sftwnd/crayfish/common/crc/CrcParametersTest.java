/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrcParametersTest {

    @Test
    void testOf() {
        CrcParameters parameters = CrcParameters.of(12, 0x80FL, 0x123L, false, true, 0xFFFL);
        assertEquals(12, parameters.width(), "Wrong width");
        assertEquals(0x80FL, parameters.poly(), "Wrong poly");
        assertEquals(0x123L, parameters.init(), "Wrong init");
        assertFalse(parameters.refin(), "Wrong refin");
        assertTrue(parameters.refout(), "Wrong refout");
        assertEquals(0xFFFL, parameters.xorout(), "Wrong xorout");
        assertEquals(-1L, CrcParameters.of(64, -1L, -1L, true, true, -1L).poly(), "A width of 64 bits takes every value");
    }

    @Test
    void testWidthOutOfRange() {
        for (int width : new int[]{-1, 0, 65, Integer.MAX_VALUE}) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    () -> CrcParameters.of(width, 0x1L, 0x0L, false, false, 0x0L), "The width " + width + " has to be rejected");
            assertEquals("CrcParameters::of - width has to be in 1..64: " + width, exception.getMessage(), "Wrong message");
        }
    }

    @Test
    void testValuesAboveTheWidth() {
        IllegalArgumentException poly = assertThrows(IllegalArgumentException.class,
                () -> CrcParameters.of(16, 0x18005L, 0x0L, true, true, 0x0L), "A poly with the x^16 term has to be rejected");
        assertEquals("CrcParameters::of - poly 0x18005 does not fit into the width 16 (poly without the x^16 term)", poly.getMessage(), "Wrong message");
        // an int literal 0xFFFFFFFF is -1, sign-extended to all 64 bits
        IllegalArgumentException init = assertThrows(IllegalArgumentException.class,
                () -> CrcParameters.of(32, 0x04C11DB7L, 0xFFFFFFFF, true, true, 0xFFFFFFFFL), "A sign-extended init has to be rejected");
        assertEquals("CrcParameters::of - init 0xFFFFFFFFFFFFFFFF does not fit into the width 32 (an int literal is sign-extended: "
                + "use a long literal such as 0xFFFFFFFFL)", init.getMessage(), "Wrong message");
        IllegalArgumentException xorout = assertThrows(IllegalArgumentException.class,
                () -> CrcParameters.of(8, 0x07L, 0x0L, false, false, 0x100L), "A xorout above the width has to be rejected");
        assertEquals("CrcParameters::of - xorout 0x100 does not fit into the width 8", xorout.getMessage(), "Wrong message");
    }

    @Test
    // assertNotEquals(a, b) calls a.equals(b): the object under test has to be the first argument
    @SuppressWarnings("java:S3415")
    void testEquals() {
        CrcParameters parameters = CrcParameters.of(16, 0x8005L, 0xFFFFL, true, true, 0x0L);
        CrcParameters same = CrcParameters.of(16, 0x8005L, 0xFFFFL, true, true, 0x0L);
        assertEquals(parameters, parameters, "Parameters have to be equal to themselves");
        assertEquals(parameters, same, "Parameters with the same values have to be equal");
        assertEquals(parameters.hashCode(), same.hashCode(), "Equal parameters have to have the same hash code");
        assertNotEquals(parameters, CrcParameters.of(17, 0x8005L, 0xFFFFL, true, true, 0x0L), "Other width");
        assertNotEquals(parameters, CrcParameters.of(16, 0x8004L, 0xFFFFL, true, true, 0x0L), "Other poly");
        assertNotEquals(parameters, CrcParameters.of(16, 0x8005L, 0xFFFEL, true, true, 0x0L), "Other init");
        assertNotEquals(parameters, CrcParameters.of(16, 0x8005L, 0xFFFFL, false, true, 0x0L), "Other refin");
        assertNotEquals(parameters, CrcParameters.of(16, 0x8005L, 0xFFFFL, true, false, 0x0L), "Other refout");
        assertNotEquals(parameters, CrcParameters.of(16, 0x8005L, 0xFFFFL, true, true, 0x1L), "Other xorout");
        assertNotEquals(parameters, null, "Parameters are not equal to null");
        assertNotEquals(parameters, parameters.toString(), "Parameters are not equal to another type");
    }

    @Test
    void testToString() {
        assertEquals("width=16  poly=0x8005  init=0x0000  refin=true  refout=true  xorout=0x0000",
                CrcParameters.of(16, 0x8005L, 0x0L, true, true, 0x0L).toString(), "Wrong format of CRC-16/ARC");
        assertEquals("width=3  poly=0x3  init=0x0  refin=false  refout=false  xorout=0x7",
                CrcParameters.of(3, 0x3L, 0x0L, false, false, 0x7L).toString(), "Wrong format of CRC-3/GSM");
        assertEquals("width=32  poly=0x04c11db7  init=0xffffffff  refin=true  refout=true  xorout=0xffffffff",
                CrcParameters.of(32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL).toString(), "Wrong format of CRC-32/ISO-HDLC");
        assertEquals("width=64  poly=0x000000000000001b  init=0xffffffffffffffff  refin=true  refout=true  xorout=0xffffffffffffffff",
                CrcParameters.of(64, 0x1BL, -1L, true, true, -1L).toString(), "Wrong format of CRC-64/GO-ISO");
    }

    @Test
    void testParametricName() {
        assertEquals("CRC-16/P8005_I0_RI_RO", CrcParameters.of(16, 0x8005L, 0x0L, true, true, 0x0L).parametricName(), "Wrong name");
        assertEquals("CRC-32/P4C11DB7_IFFFFFFFF_RI_RO_XFFFFFFFF",
                CrcParameters.of(32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL).parametricName(), "Wrong name");
        assertEquals("CRC-12/P80F_I0_RO", CrcParameters.of(12, 0x80FL, 0x0L, false, true, 0x0L).parametricName(), "Wrong name");
        assertEquals("CRC-5/P15_I1F_RI_X3", CrcParameters.of(5, 0x15L, 0x1FL, true, false, 0x3L).parametricName(), "Wrong name");
        assertEquals("CRC-1/P1_I0_X1", CrcParameters.of(1, 0x1L, 0x0L, false, false, 0x1L).parametricName(), "Wrong name");
        assertEquals("CRC-64/PFFFFFFFFFFFFFFFF_IFFFFFFFFFFFFFFFF_XFFFFFFFFFFFFFFFF",
                CrcParameters.of(64, -1L, -1L, false, false, -1L).parametricName(), "Wrong name");
    }

    @Test
    void testParse() {
        Random random = new Random(64);
        for (int width = 1; width <= 64; width++) {
            long mask = CrcParameters.mask(width);
            for (int kind = 0; kind < 8; kind++) {
                // values with all bits, without bits and random ones, with and without xorout
                long xorout = kind < 4 ? random.nextLong() & mask : 0;
                CrcParameters parameters = CrcParameters.of(width, (kind == 0 ? -1L : random.nextLong()) & mask, kind == 1 ? 0 : random.nextLong() & mask,
                        (kind & 1) != 0, (kind & 2) != 0, xorout);
                String name = parameters.parametricName();
                assertTrue(name.startsWith("CRC-" + width + "/"), "The name has to start with CRC-{width}/: " + name);
                assertEquals(parameters, CrcParameters.parse(name), "The parameters of the name " + name + " have to be parsed");
            }
        }
    }

    @Test
    void testParseOtherNames() {
        String[] names = {
            "CRC-16/ARC", "CRC-32", "", "CRC-16/P8005_I0_RI_RO ", " CRC-16/P8005_I0_RI_RO",
            // lower case digits, leading zeros, a zero xorout, other order of the flags, an empty value
            "CRC-16/P8005_Iffff", "CRC-16/P08005_I0", "CRC-16/P8005_I00", "CRC-16/P8005_I0_X0", "CRC-16/P8005_I0_RO_RI", "CRC-16/P8005_I0_X",
            // widths out of 1..64, a width with a leading zero, values above the width, too many digits
            "CRC-0/P1_I0", "CRC-65/P1_I0", "CRC-99/P1_I0", "CRC-08/P7_I0", "CRC-16/P10000_I0", "CRC-16/P8005_I10000",
            "CRC-16/P8005_I0_X10000", "CRC-64/P10000000000000000_I0"
        };
        for (String name : names) {
            assertNull(CrcParameters.parse(name), "\"" + name + "\" is not a name made of parameters");
        }
    }

}
