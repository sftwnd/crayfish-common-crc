/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrcBytesTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    private static final ByteOrder[] ORDERS = {ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN};

    // the bytes of the value as a buffer of the JDK puts a long in the byte order, independent of the library: the low
    // (width + 7) / 8 bytes of the long, the last ones of the buffer big-endian, the first ones little-endian
    private static byte[] expected(long value, int width, ByteOrder order) {
        int length = (width + 7) / 8;
        byte[] all = ByteBuffer.allocate(Long.BYTES).order(order).putLong(value).array();
        return order == ByteOrder.BIG_ENDIAN ? Arrays.copyOfRange(all, Long.BYTES - length, Long.BYTES) : Arrays.copyOf(all, length);
    }

    // the bytes of hexadecimal digits, two per byte: "0daf" gives 0d af
    private static byte[] hex(String digits) {
        byte[] bytes = new byte[digits.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = (byte) Integer.parseInt(digits.substring(2 * i, 2 * i + 2), 16);
        }
        return bytes;
    }

    @Test
    void testEveryWidth() {
        // long enough for the 8-byte loops of every width
        byte[] data = new byte[48];
        new Random(124).nextBytes(data);
        for (int width = 1; width <= 64; width++) {
            long mask = CrcParameters.mask(width);
            int length = (width + 7) / 8;
            // the bits of the most significant byte inside the width: 1..8
            int used = width - 8 * (length - 1);
            for (int kind = 0; kind < 4; kind++) {
                CrcModel model = CrcModel.of(width, 0x42F0E1EBA9EA3693L & mask, 0x0123456789ABCDEFL & mask, (kind & 1) != 0, (kind & 2) != 0,
                        0xFEDCBA9876543210L & mask);
                // all bits of the width set (the bits of the value above the width are ignored), the lowest and
                // the highest bit of the width, and the CRCs of the empty data, of the data and of the check data
                List<CRC> crcs = Arrays.asList(model.crc(-1L, 1), model.crc(1L, 1), model.crc(1L << (width - 1), 1),
                        model.crc(), model.crc(data), model.crc(CHECK));
                for (CRC crc : crcs) {
                    for (ByteOrder order : ORDERS) {
                        String message = crc + ", " + order;
                        byte[] bytes = crc.bytes(order);
                        assertEquals(length, bytes.length, message + ": wrong number of bytes");
                        assertArrayEquals(expected(crc.value(), width, order), bytes, message + ": wrong bytes");
                        // the most significant byte: the first one big-endian, the last one little-endian
                        int top = bytes[order == ByteOrder.BIG_ENDIAN ? 0 : length - 1] & 0xff;
                        assertEquals(0, top >>> used, message + ": the unused high bits of the most significant byte have to be 0");
                    }
                }
                assertArrayEquals(expected(mask, width, ByteOrder.BIG_ENDIAN), model.crc(-1L, 1).bytes(ByteOrder.BIG_ENDIAN),
                        model + ": all bits of the width have to be set in the bytes, and no other bits");
            }
        }
    }

    @Test
    void testCheckValuesOfTheCatalogue() {
        for (StandardCrc standard : StandardCrc.values()) {
            int width = standard.parameters().width();
            for (String name : standard.names()) {
                CRC crc = CrcModel.of(name).crc(CHECK);
                for (ByteOrder order : ORDERS) {
                    assertArrayEquals(expected(standard.check(), width, order), crc.bytes(order),
                            name + ", " + order + ": the bytes have to be those of the check value of the catalogue");
                }
            }
        }
    }

    @Test
    void testProtocols() {
        // the examples of the README: CRC-16/MODBUS is sent least significant byte first, CRC-16/XMODEM most
        // significant byte first; a CRC-12 takes 2 bytes, the 4 high bits of the most significant one are 0
        assertArrayEquals(hex("374b"), CrcModel.of("CRC-16/MODBUS").crc(CHECK).bytes(ByteOrder.LITTLE_ENDIAN), "Wrong bytes of CRC-16/MODBUS");
        assertArrayEquals(hex("31c3"), CrcModel.of("CRC-16/XMODEM").crc(CHECK).bytes(ByteOrder.BIG_ENDIAN), "Wrong bytes of CRC-16/XMODEM");
        CRC umts = CrcModel.of("CRC-12/UMTS").crc(CHECK);
        assertArrayEquals(hex("0daf"), umts.bytes(ByteOrder.BIG_ENDIAN), "Wrong big-endian bytes of CRC-12/UMTS");
        assertArrayEquals(hex("af0d"), umts.bytes(ByteOrder.LITTLE_ENDIAN), "Wrong little-endian bytes of CRC-12/UMTS");
        // the frame check sequence of Ethernet least significant byte first, the CRC of a PNG chunk most significant
        // byte first
        CRC crc32 = CrcModel.of("CRC-32").crc(CHECK);
        assertArrayEquals(hex("2639f4cb"), crc32.bytes(ByteOrder.LITTLE_ENDIAN), "Wrong little-endian bytes of CRC-32");
        assertArrayEquals(hex("cbf43926"), crc32.bytes(ByteOrder.BIG_ENDIAN), "Wrong big-endian bytes of CRC-32");
        // 1, 3, 5 and 8 bytes
        assertArrayEquals(hex("04"), CrcModel.of("CRC-3/GSM").crc(CHECK).bytes(ByteOrder.LITTLE_ENDIAN), "Wrong bytes of CRC-3/GSM");
        assertArrayEquals(hex("21cf02"), CrcModel.of("CRC-24/OPENPGP").crc(CHECK).bytes(ByteOrder.BIG_ENDIAN), "Wrong bytes of CRC-24/OPENPGP");
        assertArrayEquals(hex("46c64f16d4"), CrcModel.of("CRC-40/GSM").crc(CHECK).bytes(ByteOrder.LITTLE_ENDIAN), "Wrong bytes of CRC-40/GSM");
        assertArrayEquals(hex("995dc9bbdf1939fa"), CrcModel.of("CRC-64/XZ").crc(CHECK).bytes(ByteOrder.BIG_ENDIAN), "Wrong bytes of CRC-64/XZ");
    }

    @Test
    void testNewArray() {
        CRC crc = CrcModel.of("CRC-32C").crc(CHECK);
        for (ByteOrder order : ORDERS) {
            byte[] bytes = crc.bytes(order);
            assertNotSame(bytes, crc.bytes(order), order + ": every call has to give a new array");
            Arrays.fill(bytes, (byte) 0);
            assertArrayEquals(expected(crc.value(), 32, order), crc.bytes(order), order + ": a change of an array given before has not got to change the bytes");
        }
    }

    @Test
    void testNullOrder() {
        CRC crc = CrcModel.of("CRC-16/MODBUS").crc(CHECK);
        NullPointerException exception = assertThrows(NullPointerException.class, () -> crc.bytes(null), "bytes(null) has to throw NullPointerException");
        assertEquals("CRC::bytes - order is null", exception.getMessage(), "Wrong message");
    }

}
