/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CrcBytesTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    private static final ByteOrder[] ORDERS = {ByteOrder.BIG_ENDIAN, ByteOrder.LITTLE_ENDIAN};

    // long enough for the 8-byte loops of every width
    private static final byte[] DATA = new byte[48];

    // the bytes of an array or a buffer before and after the bytes of a CRC, which have not got to change
    private static final int BEFORE = 5;
    private static final int AFTER = 3;
    private static final byte OTHER = 0x5A;

    static {
        new Random(124).nextBytes(DATA);
    }

    // a model of every width from 1 to 64 with every reflection
    private static List<CrcModel> models() {
        List<CrcModel> models = new ArrayList<>();
        for (int width = 1; width <= 64; width++) {
            long mask = CrcParameters.mask(width);
            for (int kind = 0; kind < 4; kind++) {
                models.add(CrcModel.of(width, 0x42F0E1EBA9EA3693L & mask, 0x0123456789ABCDEFL & mask, (kind & 1) != 0, (kind & 2) != 0,
                        0xFEDCBA9876543210L & mask));
            }
        }
        return models;
    }

    // all bits of the width set (the bits of the value above the width are ignored), the lowest and the highest bit of
    // the width, and the CRCs of the empty data, of the data and of the check data
    private static List<CRC> crcs(CrcModel model) {
        int width = model.parameters().width();
        return Arrays.asList(model.crc(-1L, 1), model.crc(1L, 1), model.crc(1L << (width - 1), 1), model.crc(), model.crc(DATA), model.crc(CHECK));
    }

    // an array of the length with the other bytes
    private static byte[] other(int length) {
        byte[] other = new byte[length];
        Arrays.fill(other, OTHER);
        return other;
    }

    // the other bytes with the bytes at the offset
    private static byte[] other(int length, byte[] bytes, int offset) {
        byte[] other = other(length);
        System.arraycopy(bytes, 0, other, offset, bytes.length);
        return other;
    }

    // the kinds of buffers the bytes are written into
    private enum Kind {

        HEAP(ByteBuffer::allocate),
        // a slice of a wrapped array: the array offset is not 0
        SLICE(capacity -> {
            ByteBuffer wrapped = ByteBuffer.wrap(new byte[capacity + 7]);
            wrapped.position(7);
            return wrapped.slice();
        }),
        DIRECT(ByteBuffer::allocateDirect);

        private final IntFunction<ByteBuffer> allocate;

        Kind(IntFunction<ByteBuffer> allocate) {
            this.allocate = allocate;
        }

        // a buffer of the other bytes at the position before, in the other byte order than that given
        ByteBuffer buffer(int capacity, ByteOrder order) {
            ByteBuffer buffer = allocate.apply(capacity);
            buffer.put(other(capacity));
            buffer.position(BEFORE);
            buffer.order(order == ByteOrder.BIG_ENDIAN ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
            return buffer;
        }

    }

    // all bytes of the buffer, from 0 to its capacity
    private static byte[] content(ByteBuffer buffer) {
        byte[] content = new byte[buffer.capacity()];
        for (int i = 0; i < content.length; i++) {
            content[i] = buffer.get(i);
        }
        return content;
    }

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
        for (CrcModel model : models()) {
            int width = model.parameters().width();
            int length = (width + 7) / 8;
            // the bits of the most significant byte inside the width: 1..8
            int used = width - 8 * (length - 1);
            for (CRC crc : crcs(model)) {
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
            assertArrayEquals(expected(CrcParameters.mask(width), width, ByteOrder.BIG_ENDIAN), model.crc(-1L, 1).bytes(ByteOrder.BIG_ENDIAN),
                    model + ": all bits of the width have to be set in the bytes, and no other bits");
        }
    }

    @Test
    void testIntoAnArray() {
        for (CrcModel model : models()) {
            for (CRC crc : crcs(model)) {
                for (ByteOrder order : ORDERS) {
                    String message = crc + ", " + order;
                    byte[] bytes = crc.bytes(order);
                    byte[] array = other(BEFORE + bytes.length + AFTER);
                    assertEquals(bytes.length, crc.bytes(array, BEFORE, order), message + ": the number of the bytes has to be returned");
                    assertArrayEquals(other(array.length, bytes, BEFORE), array, message + ": the bytes of bytes(order) have to be at the offset, and no other byte has to change");
                    byte[] alone = new byte[bytes.length];
                    assertEquals(bytes.length, crc.bytes(alone, 0, order), message + ": the number of the bytes has to be returned for an array of the bytes alone");
                    assertArrayEquals(bytes, alone, message + ": wrong bytes in an array of the bytes alone");
                }
            }
        }
    }

    @Test
    void testIntoABuffer() {
        for (CrcModel model : models()) {
            for (CRC crc : crcs(model)) {
                for (ByteOrder order : ORDERS) {
                    byte[] bytes = crc.bytes(order);
                    int capacity = BEFORE + bytes.length + AFTER;
                    for (Kind kind : Kind.values()) {
                        String message = crc + ", " + order + ", a " + kind + " buffer of the other byte order";
                        ByteBuffer buffer = kind.buffer(capacity, order);
                        assertEquals(bytes.length, crc.bytes(buffer, order), message + ": the number of the bytes has to be returned");
                        assertEquals(BEFORE + bytes.length, buffer.position(), message + ": the position has to be advanced by the number of the bytes");
                        assertEquals(capacity, buffer.limit(), message + ": the limit has not got to change");
                        assertArrayEquals(other(capacity, bytes, BEFORE), content(buffer),
                                message + ": the bytes of bytes(order) have to be at the position, and no other byte has to change");
                    }
                }
            }
        }
    }

    @Test
    void testArrayErrors() {
        CRC crc = CrcModel.of("CRC-32").crc(CHECK);
        byte[] array = other(8);
        // a negative offset, an end beyond the array, an offset beyond the array and an overflowing end
        for (int offset : new int[]{-1, 5, 9, Integer.MAX_VALUE}) {
            for (ByteOrder order : ORDERS) {
                ArrayIndexOutOfBoundsException exception = assertThrows(ArrayIndexOutOfBoundsException.class, () -> crc.bytes(array, offset, order),
                        "The offset " + offset + " has to be rejected, " + order);
                assertEquals("CRC::bytes - offset " + offset + " and length 4 out of the buffer length 8", exception.getMessage(), "Wrong message");
            }
        }
        assertArrayEquals(other(8), array, "A rejected offset has not got to change the array");
        assertEquals(4, crc.bytes(array, 4, ByteOrder.BIG_ENDIAN), "The bytes at the end of the array have to be accepted");
        assertArrayEquals(other(8, crc.bytes(ByteOrder.BIG_ENDIAN), 4), array, "Wrong bytes at the end of the array");
        NullPointerException nullDestination = assertThrows(NullPointerException.class, () -> crc.bytes((byte[]) null, 0, ByteOrder.BIG_ENDIAN),
                "bytes(null, 0, order) has to throw NullPointerException");
        assertEquals("CRC::bytes - destination is null", nullDestination.getMessage(), "Wrong message");
        NullPointerException nullOrder = assertThrows(NullPointerException.class, () -> crc.bytes(array, 0, null), "bytes(array, 0, null) has to throw NullPointerException");
        assertEquals("CRC::bytes - order is null", nullOrder.getMessage(), "Wrong message");
    }

    @Test
    void testBufferErrors() {
        CRC crc = CrcModel.of("CRC-32").crc(CHECK);
        // 3 bytes remaining for the 4 bytes of the CRC
        ByteBuffer small = Kind.HEAP.buffer(8, ByteOrder.BIG_ENDIAN);
        assertThrows(BufferOverflowException.class, () -> crc.bytes(small, ByteOrder.BIG_ENDIAN), "A buffer with fewer bytes remaining has to be rejected");
        assertEquals(BEFORE, small.position(), "A rejected buffer has not got to be read");
        assertArrayEquals(other(8), content(small), "A rejected buffer has not got to change");
        // exactly the bytes of the CRC remaining
        ByteBuffer exact = Kind.DIRECT.buffer(BEFORE + 4, ByteOrder.LITTLE_ENDIAN);
        assertEquals(4, crc.bytes(exact, ByteOrder.LITTLE_ENDIAN), "A buffer with the bytes of the CRC remaining has to be accepted");
        assertEquals(exact.limit(), exact.position(), "The position has to be advanced to the limit");
        ByteBuffer readOnly = Kind.HEAP.buffer(16, ByteOrder.BIG_ENDIAN).asReadOnlyBuffer();
        assertThrows(ReadOnlyBufferException.class, () -> crc.bytes(readOnly, ByteOrder.BIG_ENDIAN), "A read-only buffer has to be rejected");
        assertEquals(BEFORE, readOnly.position(), "A read-only buffer has not got to be read");
        assertArrayEquals(other(16), content(readOnly), "A read-only buffer has not got to change");
        NullPointerException nullDestination = assertThrows(NullPointerException.class, () -> crc.bytes((ByteBuffer) null, ByteOrder.BIG_ENDIAN),
                "bytes(null, order) has to throw NullPointerException");
        assertEquals("CRC::bytes - destination is null", nullDestination.getMessage(), "Wrong message");
        ByteBuffer buffer = Kind.HEAP.buffer(16, ByteOrder.BIG_ENDIAN);
        NullPointerException nullOrder = assertThrows(NullPointerException.class, () -> crc.bytes(buffer, null), "bytes(buffer, null) has to throw NullPointerException");
        assertEquals("CRC::bytes - order is null", nullOrder.getMessage(), "Wrong message");
        assertEquals(BEFORE, buffer.position(), "A null order has not got to change the buffer");
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
