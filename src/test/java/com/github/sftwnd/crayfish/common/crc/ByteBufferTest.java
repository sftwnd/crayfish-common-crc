/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.function.IntFunction;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByteBufferTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    // around the start of the 8-byte loop (24 bytes) and of the parts of a buffer without an array (4096 bytes)
    private static final int[] SIZES = {0, 1, 23, 24, 4095, 4096, 4097, 10000};

    // the bytes before the position and after the limit, which have not got to be calculated
    private static final int BEFORE = 5;
    private static final int AFTER = 3;

    private static final byte[] DATA = new byte[10000];

    static {
        new Random(96).nextBytes(DATA);
    }

    private static List<CrcModel> models() {
        List<CrcModel> models = new ArrayList<>();
        for (StandardCrc standard : StandardCrc.values()) {
            models.add(standard.model());
        }
        // parameter sets outside the catalogue
        models.add(CrcModel.of(3, 0x5L, 0x7L, false, false, 0x0L));
        models.add(CrcModel.of(13, 0x1CF5L, 0x0L, true, false, 0x1FFFL));
        models.add(CrcModel.of(21, 0x102899L, 0x1FFFFFL, true, false, 0x1L));
        models.add(CrcModel.of(64, 0x42F0E1EBA9EA3693L, 0x1L, false, true, 0x0L));
        return models;
    }

    // the kinds of buffers: the data between the position and the limit, other bytes before and after them
    private enum Kind {

        HEAP(ByteBuffer::allocate),
        // a slice of a wrapped array: the array offset is not 0
        SLICE(capacity -> {
            ByteBuffer wrapped = ByteBuffer.wrap(new byte[capacity + 7]);
            wrapped.position(7);
            return wrapped.slice();
        }),
        READ_ONLY(ByteBuffer::allocate),
        DIRECT(ByteBuffer::allocateDirect);

        private final IntFunction<ByteBuffer> allocate;

        Kind(IntFunction<ByteBuffer> allocate) {
            this.allocate = allocate;
        }

        ByteBuffer buffer(int size) {
            ByteBuffer buffer = allocate.apply(BEFORE + size + AFTER);
            byte[] other = new byte[BEFORE + size + AFTER];
            Arrays.fill(other, (byte) 0x5A);
            buffer.put(other);
            buffer.position(BEFORE);
            buffer.put(DATA, 0, size);
            buffer.limit(BEFORE + size);
            buffer.position(BEFORE);
            return this == READ_ONLY ? buffer.asReadOnlyBuffer() : buffer;
        }

    }

    private static void assertRead(ByteBuffer buffer, int size, String message) {
        assertEquals(BEFORE + size, buffer.limit(), message + ": the limit has not got to change");
        assertEquals(buffer.limit(), buffer.position(), message + ": the position has to be advanced to the limit");
    }

    @Test
    void testKindsOfBuffers() {
        ByteBuffer slice = Kind.SLICE.buffer(1);
        assertTrue(slice.hasArray() && slice.arrayOffset() == 7, "The slice has to have an array offset");
        assertFalse(Kind.READ_ONLY.buffer(1).hasArray(), "A read-only buffer has no accessible array");
        assertTrue(Kind.DIRECT.buffer(1).isDirect(), "The direct buffer has to be direct");
    }

    @Test
    void testValueAndCrc() {
        for (CrcModel model : models()) {
            CRC first = model.crc(CHECK);
            for (int size : SIZES) {
                byte[] data = Arrays.copyOf(DATA, size);
                long expected = model.value(data);
                for (Kind kind : Kind.values()) {
                    String message = model + ", " + size + " bytes of a " + kind + " buffer";
                    ByteBuffer buffer = kind.buffer(size);
                    assertEquals(expected, model.value(buffer), message + ": wrong value");
                    assertRead(buffer, size, message);
                    buffer = kind.buffer(size);
                    assertEquals(model.crc(data), model.crc(buffer), message + ": wrong CRC");
                    assertRead(buffer, size, message);
                    buffer = kind.buffer(size);
                    assertEquals(first.update(data), first.update(buffer), message + ": wrong CRC after other data");
                    assertRead(buffer, size, message);
                }
            }
        }
    }

    @Test
    void testChecksum() {
        for (CrcModel model : models()) {
            for (int size : SIZES) {
                byte[] data = Arrays.copyOf(DATA, size);
                for (Kind kind : Kind.values()) {
                    String message = model + ", " + size + " bytes of a " + kind + " buffer";
                    CrcChecksum expected = model.checksum();
                    expected.update(CHECK);
                    expected.update(data);
                    CrcChecksum checksum = model.checksum();
                    checksum.update(CHECK);
                    ByteBuffer buffer = kind.buffer(size);
                    checksum.update(buffer);
                    assertRead(buffer, size, message);
                    assertEquals(expected.getValue(), checksum.getValue(), message + ": wrong value of the checksum");
                    assertEquals(expected.length(), checksum.length(), message + ": wrong length of the checksum");
                }
            }
        }
    }

    @Test
    void testEmptyBuffer() {
        CrcModel model = CrcModel.of("CRC-32C");
        CRC crc = model.crc(CHECK);
        for (Kind kind : Kind.values()) {
            assertSame(crc, crc.update(kind.buffer(0)), "An empty " + kind + " buffer has to give the same CRC");
            assertSame(model.crc(), model.crc(kind.buffer(0)), "An empty " + kind + " buffer has to give the CRC of the empty data");
            assertEquals(model.crc().value(), model.value(kind.buffer(0)), "An empty " + kind + " buffer has to give the value of the empty data");
            CrcChecksum checksum = model.checksum();
            checksum.update(CHECK);
            checksum.update(kind.buffer(0));
            assertEquals(crc, checksum.crc(), "An empty " + kind + " buffer has not got to change the checksum");
        }
    }

    @Test
    void testNull() {
        CrcModel model = CrcModel.of("CRC-16/ARC");
        ByteBuffer none = null;
        CRC empty = model.crc();
        NullPointerException crcUpdate = assertThrows(NullPointerException.class, () -> empty.update(none),
                "CRC.update(null) has to throw NullPointerException");
        assertEquals("CRC::update - data is null", crcUpdate.getMessage(), "Wrong message");
        NullPointerException modelCrc = assertThrows(NullPointerException.class, () -> model.crc(none),
                "CrcModel.crc(null) has to throw NullPointerException");
        assertEquals("CrcModel::crc - data is null", modelCrc.getMessage(), "Wrong message");
        NullPointerException modelValue = assertThrows(NullPointerException.class, () -> model.value(none),
                "CrcModel.value(null) has to throw NullPointerException");
        assertEquals("CrcModel::value - data is null", modelValue.getMessage(), "Wrong message");
        CrcChecksum checksum = model.checksum();
        NullPointerException checksumUpdate = assertThrows(NullPointerException.class, () -> checksum.update(none),
                "CrcChecksum.update(null) has to throw NullPointerException");
        assertEquals("CrcChecksum::update - data is null", checksumUpdate.getMessage(), "Wrong message");
    }

    @Test
    void testLengthOverflow() throws ReflectiveOperationException {
        CrcModel model = CrcModel.of("CRC-32");
        CRC crc = model.crc(0x0L, Long.MAX_VALUE);
        ByteBuffer buffer = Kind.DIRECT.buffer(4);
        assertThrows(ArithmeticException.class, () -> crc.update(buffer), "The length of update(ByteBuffer) has to exceed Long.MAX_VALUE");
        assertEquals(BEFORE, buffer.position(), "The buffer has not got to be read when the length overflows");
        CrcChecksum checksum = model.checksum();
        checksum.update(CHECK);
        // a length of Long.MAX_VALUE processed bytes cannot be reached by a test: the field is set
        Field length = CrcChecksum.class.getDeclaredField("length");
        length.setAccessible(true);
        length.setLong(checksum, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> checksum.update(buffer), "The length of the checksum has to exceed Long.MAX_VALUE");
        assertEquals(BEFORE, buffer.position(), "The buffer has not got to be read when the length of the checksum overflows");
        assertEquals(model.check(), checksum.getValue(), "The rejected update has not got to change the value");
    }

}
