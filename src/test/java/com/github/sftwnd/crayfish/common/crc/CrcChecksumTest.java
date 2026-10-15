/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;
import java.util.zip.Checksum;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CrcChecksumTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    // the parts of the data: single bytes, short parts a byte at a time and parts of the 8-byte loop
    private static final int[] PARTS = {1, 0, 7, 1, 24, 1, 1, 33, 100, 3, 257};

    private static byte[] data() {
        int length = 0;
        for (int part : PARTS) {
            length += part;
        }
        byte[] data = new byte[length];
        new Random(93).nextBytes(data);
        return data;
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

    @Test
    void testCheckedOutputStream() throws IOException {
        byte[] data = data();
        for (CrcModel model : models()) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (CheckedOutputStream out = new CheckedOutputStream(bytes, model.checksum())) {
                int offset = 0;
                for (int part : PARTS) {
                    // a part of one byte as a byte, the others as parts of the buffer
                    if (part == 1) {
                        out.write(data[offset]);
                    } else {
                        out.write(data, offset, part);
                    }
                    offset += part;
                }
                assertArrayEquals(data, bytes.toByteArray(), "The stream has to write the data");
                assertEquals(model.value(data), out.getChecksum().getValue(), model + ": wrong value of the data written in parts");
                assertEquals(CrcReference.crc(model.parameters(), data), out.getChecksum().getValue(), model + ": the value has to be the CRC of the reference");
            }
        }
    }

    @Test
    void testCheckedInputStream() throws IOException {
        byte[] data = data();
        for (CrcModel model : models()) {
            try (CheckedInputStream in = new CheckedInputStream(new ByteArrayInputStream(data), model.checksum())) {
                assertEquals(data[0] & 0xff, in.read(), "The stream has to read the first byte");
                byte[] buffer = new byte[64];
                int total = 1;
                for (int read = in.read(buffer, 0, buffer.length); read >= 0; read = in.read(buffer, 0, buffer.length)) {
                    total += read;
                }
                assertEquals(data.length, total, "The stream has to read the data");
                assertEquals(model.value(data), in.getChecksum().getValue(), model + ": wrong value of the data read");
            }
        }
    }

    @Test
    void testSingleBytes() {
        byte[] data = data();
        for (CrcModel model : models()) {
            CrcChecksum checksum = model.checksum();
            for (byte b : data) {
                checksum.update(b);
            }
            assertEquals(model.value(data), checksum.getValue(), model + ": wrong value of the data a byte at a time");
            assertEquals(data.length, checksum.length(), model + ": wrong length of the data a byte at a time");
            checksum.reset();
            for (int i = 0; i < CHECK.length; i++) {
                // only the low 8 bits of the int are the byte
                checksum.update(CHECK[i] | 0x5A5A5A00);
            }
            assertEquals(model.check(), checksum.getValue(), model + ": update(int) has to take the low 8 bits only");
        }
    }

    @Test
    void testUpdateAndCrc() {
        byte[] data = data();
        for (CrcModel model : models()) {
            CrcChecksum checksum = model.checksum();
            assertSame(model, checksum.model(), "Wrong model of the checksum");
            assertEquals(model.crc().value(), checksum.getValue(), model + ": a new checksum has to have the value of the empty data");
            assertEquals(0, checksum.length(), "A new checksum has to have the length 0");
            assertSame(model.crc(), checksum.crc(), "The CRC of a new checksum has to be the CRC of the empty data");
            checksum.update(data);
            checksum.update(CHECK, 2, 5);
            checksum.update(new byte[0]);
            checksum.update(CHECK, 9, 0);
            CRC expected = model.crc(data).update(CHECK, 2, 5);
            assertEquals(expected, checksum.crc(), model + ": wrong CRC of the checksum");
            assertEquals(expected.value(), checksum.getValue(), model + ": wrong value");
            assertEquals(expected.length(), checksum.length(), model + ": wrong length");
            assertEquals(expected.toString(), checksum.toString(), model + ": wrong format");
            CRC saved = checksum.crc();
            checksum.update(CHECK);
            assertEquals(expected, saved, model + ": the CRC of the checksum has not got to change with the checksum");
            assertEquals(saved.update(CHECK), checksum.crc(), model + ": wrong CRC after a further update");
            checksum.reset();
            assertEquals(model.crc().value(), checksum.getValue(), model + ": reset() has to give the value of the empty data");
            assertEquals(0, checksum.length(), model + ": reset() has to give the length 0");
            checksum.update(CHECK);
            assertEquals(model.check(), checksum.getValue(), model + ": wrong value after reset()");
        }
    }

    @Test
    void testChecksumOfTheModel() {
        CrcModel model = CrcModel.of("PKZIP");
        CrcChecksum checksum = model.checksum();
        assertNotSame(checksum, model.checksum(), "Every checksum of a model has to be new");
        checksum.update(CHECK);
        assertEquals("PKZIP[crc:0xcbf43926,len:9]", checksum.toString(), "The checksum has to have the name the model was chosen by");
        assertEquals(model.crc().value(), model.checksum().getValue(), "The update of a checksum has not got to change the others");
    }

    @Test
    void testCrc32OfTheJdk() {
        byte[] data = data();
        CRC32 jdk = new CRC32();
        CrcChecksum checksum = CrcModel.of("CRC-32/ISO-HDLC").checksum();
        for (Checksum each : new Checksum[]{jdk, checksum}) {
            each.update(data, 0, data.length);
            each.update(0x1FF);
            each.update(data, 17, 100);
        }
        assertEquals(jdk.getValue(), checksum.getValue(), "The checksum of CRC-32/ISO-HDLC has to give the value of java.util.zip.CRC32");
    }

    @Test
    void testByteBuffer() throws ReflectiveOperationException {
        // Checksum.update(ByteBuffer) is a default method of Java 9+
        Method update;
        try {
            update = Checksum.class.getMethod("update", ByteBuffer.class);
        } catch (NoSuchMethodException e) {
            update = null;
        }
        assumeTrue(update != null, "Checksum.update(ByteBuffer) is a method of Java 9+");
        byte[] data = data();
        CrcModel model = CrcModel.of("CRC-32C");
        for (ByteBuffer buffer : new ByteBuffer[]{ByteBuffer.wrap(data), ByteBuffer.allocateDirect(data.length).put(data)}) {
            buffer.rewind();
            CrcChecksum checksum = model.checksum();
            update.invoke(checksum, buffer);
            assertEquals(model.value(data), checksum.getValue(), "Wrong value of a " + (buffer.isDirect() ? "direct" : "heap") + " buffer");
            assertEquals(data.length, checksum.length(), "Wrong length of a buffer");
            assertFalse(buffer.hasRemaining(), "The buffer has to be read");
        }
    }

    @Test
    void testBounds() {
        CrcModel model = CrcModel.of("CRC-16/ARC");
        CrcChecksum checksum = model.checksum();
        byte[] data = new byte[16];
        checksum.update(data, 16, 0);
        assertEquals(0, checksum.length(), "The empty part at the end of the data has to be accepted");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> checksum.update(data, 5, 12), "The bytes beyond the data have to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> checksum.update(data, -1, 8), "A negative offset has to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> checksum.update(data, 0, -1), "A negative length has to be rejected");
        ArrayIndexOutOfBoundsException exception = assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> checksum.update(data, Integer.MAX_VALUE, 2), "An overflowing end has to be rejected");
        assertEquals("CrcChecksum::update - offset 2147483647 and length 2 out of the buffer length 16", exception.getMessage(), "Wrong message");
        NullPointerException nullData = assertThrows(NullPointerException.class, () -> checksum.update(null), "update(null) has to throw NullPointerException");
        assertEquals("CrcChecksum::update - data is null", nullData.getMessage(), "Wrong message");
        assertThrows(NullPointerException.class, () -> checksum.update(null, 0, 0), "update(null, 0, 0) has to throw NullPointerException");
        assertEquals(model.crc(), checksum.crc(), "The rejected updates have not got to change the checksum");
    }

    @Test
    void testLengthOverflow() throws ReflectiveOperationException {
        CrcChecksum checksum = CrcModel.of("CRC-32").checksum();
        checksum.update(CHECK);
        // a length of Long.MAX_VALUE processed bytes cannot be reached by a test: the field is set
        Field length = CrcChecksum.class.getDeclaredField("length");
        length.setAccessible(true);
        length.setLong(checksum, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> checksum.update(1), "The length of update(int) has to exceed Long.MAX_VALUE");
        assertThrows(ArithmeticException.class, () -> checksum.update(CHECK), "The length of update(byte[]) has to exceed Long.MAX_VALUE");
        assertEquals(CrcModel.of("CRC-32").check(), checksum.getValue(), "The rejected updates have not got to change the value");
        assertEquals(Long.MAX_VALUE, checksum.length(), "The rejected updates have not got to change the length");
    }

}
