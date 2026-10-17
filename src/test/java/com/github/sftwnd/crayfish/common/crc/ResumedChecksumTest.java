/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The checksum that continues a CRC, {@link CRC#checksum()}: the models of the catalogue and parameter sets outside it,
 * calculated by the lookup tables and by the classes of the JDK. The unit tests run with the class directory, where
 * JdkCrcs is the version of Java 8: java.util.zip.CRC32 calculates the models of the JDK of its polynomial, CRC-32C stays
 * with the lookup tables. ResumedChecksumIT runs the assertions of this class for the models of the JDK with the jar,
 * where java.util.zip.CRC32C calculates CRC-32C on Java 9+
 */
class ResumedChecksumTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    // the lengths of the data of the CRC that a checksum continues: the empty data, data shorter and longer than
    // the 8-byte loop
    private static final int[] SPLITS = {0, 1, 9, 100, 1000};

    // the lengths of the data that follows the CRC: around the 8-byte loop of the lookup tables (8 bytes, 24 for a CRC
    // wider than 32 bits), the minimal lengths of the classes of the JDK (CRC32C 8 bytes, CRC32 64 bytes on Java 9+ and
    // 128 on Java 8) and the parts of a buffer without an accessible array (4096 bytes)
    private static final int[] LENGTHS = {1, 7, 8, 9, 24, 63, 64, 65, 127, 128, 129, 1000, 4095, 4096, 4097};

    // the parts of the data that follow the CRC one after another, as written to a stream, given in the ways in turn
    private static final int[] PARTS = {1, 7, 8, 9, 1, 24, 63, 64, 65, 3, 127, 128, 129, 1, 1000, 4097, 2};

    // the bytes before the position and after the limit of a buffer, which have not got to be calculated
    private static final int BEFORE = 5;
    private static final int AFTER = 3;

    private static final byte[] DATA = new byte[7000];

    static {
        new Random(123).nextBytes(DATA);
    }

    // the ways to give a part of the data to a checksum: a byte at a time, an array, a part of an array, buffers
    private enum Way {

        BYTES, ARRAY, PART, HEAP, DIRECT, READ_ONLY, READ_ONLY_DIRECT;

        // gives the length bytes of the data from the offset to the checksum
        void update(CrcChecksum checksum, int offset, int length) {
            switch (this) {
                case BYTES:
                    for (int i = offset; i < offset + length; i++) {
                        checksum.update(DATA[i]);
                    }
                    break;
                case ARRAY:
                    checksum.update(Arrays.copyOfRange(DATA, offset, offset + length));
                    break;
                case PART:
                    checksum.update(DATA, offset, length);
                    break;
                default:
                    boolean direct = this == DIRECT || this == READ_ONLY_DIRECT;
                    boolean readOnly = this == READ_ONLY || this == READ_ONLY_DIRECT;
                    ByteBuffer buffer = buffer(direct, readOnly, offset, length);
                    checksum.update(buffer);
                    assertEquals(buffer.limit(), buffer.position(), this + ": the position of the buffer has to be advanced to the limit");
            }
        }

    }

    // the length bytes of the data from the offset in a buffer, between the other bytes before and after them
    private static ByteBuffer buffer(boolean direct, boolean readOnly, int offset, int length) {
        int capacity = BEFORE + length + AFTER;
        ByteBuffer buffer = direct ? ByteBuffer.allocateDirect(capacity) : ByteBuffer.allocate(capacity);
        byte[] other = new byte[capacity];
        Arrays.fill(other, (byte) 0x5A);
        buffer.put(other);
        buffer.position(BEFORE);
        buffer.put(DATA, offset, length);
        buffer.limit(BEFORE + length);
        buffer.position(BEFORE);
        return readOnly ? buffer.asReadOnlyBuffer() : buffer;
    }

    // the models of the catalogue and parameter sets outside it, calculated by the lookup tables, and the models chosen
    // with the classes of the JDK by a name, an alias and other parameters of their polynomials
    private static List<CrcModel> models() {
        List<CrcModel> models = new ArrayList<>();
        for (StandardCrc standard : StandardCrc.values()) {
            models.add(CrcModel.of(standard.crcName(), false));
        }
        models.add(CrcModel.of(CrcParameters.of(3, 0x5L, 0x7L, false, false, 0x0L), false));
        models.add(CrcModel.of(CrcParameters.of(13, 0x1CF5L, 0x0L, true, false, 0x1FFFL), false));
        models.add(CrcModel.of(CrcParameters.of(21, 0x102899L, 0x1FFFFFL, true, false, 0x1L), false));
        models.add(CrcModel.of(CrcParameters.of(64, 0x42F0E1EBA9EA3693L, 0x1L, false, true, 0x0L), false));
        models.add(CrcModel.of("CRC-32/ISO-HDLC", true));
        models.add(CrcModel.of("PKZIP", true));
        models.add(CrcModel.of("CRC-32/JAMCRC", true));
        models.add(CrcModel.of("CRC-32C", true));
        models.add(CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32_POLY, 0x0L, true, false, 0x5A5A5A5AL), true));
        models.add(CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32C_POLY, 0x12345678L, true, true, 0x0L), true));
        return models;
    }

    // the checksums of the CRCs of the first bytes of the data, continued with the next bytes in every way: the CRC of
    // the whole data, calculated at once by the lookup tables
    static void assertUpdates(CrcModel model) {
        CrcModel tables = model.withJdk(false);
        for (int split : SPLITS) {
            CRC crc = model.crc(DATA, 0, split);
            for (int length : LENGTHS) {
                CRC expected = tables.crc(DATA, 0, split + length);
                for (Way way : Way.values()) {
                    CrcChecksum checksum = crc.checksum();
                    way.update(checksum, split, length);
                    assertEquals(expected, checksum.crc(), model + ": wrong CRC of " + split + " bytes continued with " + length + " bytes, " + way);
                }
            }
        }
    }

    // the checksum of the CRC of the first bytes of the data continued with the parts one after another, in the ways
    // in turn: the CRC of the data so far after every part, the value of the reference at the end
    static void assertPartsOneAfterAnother(CrcModel model) {
        CrcModel tables = model.withJdk(false);
        Way[] ways = Way.values();
        for (int split : SPLITS) {
            CrcChecksum checksum = model.crc(DATA, 0, split).checksum();
            int offset = split;
            for (int i = 0; i < PARTS.length; i++) {
                ways[i % ways.length].update(checksum, offset, PARTS[i]);
                offset += PARTS[i];
                assertEquals(tables.crc(DATA, 0, offset), checksum.crc(), model + ": wrong CRC of the first " + offset + " bytes, continued after " + split);
            }
            assertEquals(CrcReference.crc(model.parameters(), DATA, 0, offset), checksum.getValue(), model + ": the value has to be the CRC of the reference");
            assertEquals(offset, checksum.length(), model + ": wrong length of the data");
        }
    }

    // the checksum of a CRC has the model, the value and the length of the CRC, and gives an equal CRC of the model, with
    // the name of the model; every call gives a new checksum, and an update changes neither the CRC nor the others
    static void assertCrcOfTheChecksum(CrcModel model) {
        String name = model.name();
        CRC crc = model.crc(CHECK, 0, 4);
        CrcChecksum checksum = crc.checksum();
        assertSame(model, checksum.model(), name + ": the checksum has to have the model of the CRC");
        assertEquals(crc.value(), checksum.getValue(), name + ": the checksum has to have the value of the CRC");
        assertEquals(crc.length(), checksum.length(), name + ": the checksum has to have the length of the CRC");
        assertEquals(crc, checksum.crc(), name + ": the CRC of the checksum has to be equal to the CRC");
        assertSame(model, checksum.crc().model(), name + ": the CRC of the checksum has to have the model of the CRC");
        assertEquals(name, checksum.crc().name(), name + ": the CRC of the checksum has to have the name of the CRC");
        assertEquals(crc.toString(), checksum.toString(), name + ": the checksum has to have the format of the CRC");
        CrcChecksum other = crc.checksum();
        assertNotSame(checksum, other, name + ": every checksum of a CRC has to be new");
        checksum.update(CHECK, 4, 5);
        assertEquals(model.check(), checksum.getValue(), name + ": the checksum has to continue the CRC");
        assertEquals(CHECK.length, checksum.length(), name + ": the checksum has to continue the length of the CRC");
        assertEquals(name, checksum.crc().name(), name + ": the CRC of the continued checksum has to keep the name");
        assertEquals(crc, other.crc(), name + ": the update of a checksum has not got to change the others");
        assertEquals(model.crc(CHECK, 0, 4), crc, name + ": the CRC has not got to change with the checksum");
    }

    // reset() gives the checksum of the empty data, as for every checksum, not the CRC that the checksum continues
    static void assertReset(CrcModel model) {
        CrcChecksum checksum = model.crc(DATA, 0, 1000).checksum();
        checksum.update(DATA, 1000, 100);
        checksum.reset();
        assertEquals(model.crc().value(), checksum.getValue(), model + ": reset() has to give the value of the empty data");
        assertEquals(0, checksum.length(), model + ": reset() has to give the length 0");
        assertSame(model.crc(), checksum.crc(), model + ": reset() has to give the CRC of the empty data");
        checksum.update(CHECK);
        assertEquals(model.check(), checksum.getValue(), model + ": wrong value after reset()");
        CrcChecksum notUpdated = model.crc(DATA, 0, 1000).checksum();
        notUpdated.reset();
        assertSame(model.crc(), notUpdated.crc(), model + ": reset() of a checksum without an update has to give the CRC of the empty data");
    }

    // the checksum of the CRC of the empty data is the checksum of the empty data, as a new checksum of the model
    static void assertChecksumOfTheEmptyData(CrcModel model) {
        CrcChecksum checksum = model.crc().checksum();
        assertSame(model, checksum.model(), model + ": the checksum of the empty data has to have the model");
        assertEquals(model.checksum().getValue(), checksum.getValue(), model + ": the checksum of the empty data has to have the value of a new checksum");
        assertEquals(0, checksum.length(), model + ": the checksum of the empty data has to have the length 0");
        assertSame(model.crc(), checksum.crc(), model + ": the CRC of the checksum of the empty data has to be the CRC of the empty data");
        // a CRC resumed with the length 0 is the CRC of the empty data, whatever the value
        assertSame(model.crc(), model.crc(0x5AL, 0).checksum().crc(), model + ": the checksum of a CRC of 0 bytes has to be the checksum of the empty data");
        checksum.update(DATA, 0, 1000);
        assertEquals(model.withJdk(false).crc(DATA, 0, 1000), checksum.crc(), model + ": the checksum of the empty data has to calculate as a new checksum");
    }

    // a CheckedOutputStream with the checksum of the saved value and length of the data written before appends the rest
    // of the data to the file: the CRC of the whole file. A CheckedInputStream with such a checksum reads the rest of
    // the data after a restart: the CRC of the whole data
    static void assertCheckedStreams(CrcModel model, Path file) throws IOException {
        int split = 1000;
        CrcChecksum first = model.checksum();
        try (CheckedOutputStream out = new CheckedOutputStream(Files.newOutputStream(file), first)) {
            out.write(DATA, 0, split);
        }
        long savedValue = first.getValue();
        long savedLength = first.length();
        CrcChecksum checksum = model.crc(savedValue, savedLength).checksum();
        try (CheckedOutputStream out = new CheckedOutputStream(Files.newOutputStream(file, StandardOpenOption.APPEND), checksum)) {
            out.write(DATA[split]);
            out.write(DATA, split + 1, DATA.length - split - 1);
        }
        assertArrayEquals(DATA, Files.readAllBytes(file), model + ": the stream has to append the data to the file");
        CRC whole = checksum.crc();
        assertEquals(model.withJdk(false).crc(DATA), whole, model + ": wrong CRC of the whole file");
        assertSame(model, whole.model(), model + ": the CRC of the file has to have the model");
        CrcChecksum read = model.crc(savedValue, savedLength).checksum();
        try (CheckedInputStream in = new CheckedInputStream(new ByteArrayInputStream(DATA, split, DATA.length - split), read)) {
            assertEquals(DATA[split] & 0xff, in.read(), "The stream has to read the first byte");
            byte[] buffer = new byte[1000];
            long total = savedLength + 1;
            for (int count = in.read(buffer, 0, buffer.length); count >= 0; count = in.read(buffer, 0, buffer.length)) {
                total += count;
            }
            assertEquals(DATA.length, total, "The stream has to read the data");
        }
        assertEquals(whole, read.crc(), model + ": the stream has to continue the CRC of the data read before");
        assertEquals(CrcReference.crc(model.parameters(), DATA), read.getValue(), model + ": the value of the data read has to be the CRC of the reference");
    }

    // a checksum of a CRC continues the way of the model: a class of the JDK calculates the data from its minimal length
    // on and a direct buffer, so the lookup tables of the description are not built; the model has parameters of its own,
    // so that no other test has built them
    static void assertTheWayOfTheModel(CrcModel model) {
        CrcEngine lookup = model.description().engine();
        assertFalse(lookup.hasTables(), model + ": the lookup tables have not got to be built yet");
        CrcChecksum checksum = model.crc(0x12345678L, 1000).checksum();
        checksum.update(DATA, 0, 4096);
        checksum.update(buffer(true, false, 4096, 1000));
        assertEquals(!model.isJdk(), lookup.hasTables(), model + ": the checksum has to continue the CRC the way of the model");
        assertEquals(model.withJdk(false).crc(0x12345678L, 1000).update(DATA, 0, 5096), checksum.crc(), model + ": wrong CRC of the checksum");
    }

    @Test
    void testUpdates() {
        for (CrcModel model : models()) {
            assertUpdates(model);
        }
    }

    @Test
    void testPartsOneAfterAnother() {
        for (CrcModel model : models()) {
            assertPartsOneAfterAnother(model);
        }
    }

    @Test
    void testCrcOfTheChecksum() {
        // every name and alias of the catalogue, by the lookup tables and with the classes of the JDK
        for (StandardCrc standard : StandardCrc.values()) {
            for (String name : standard.names()) {
                assertCrcOfTheChecksum(CrcModel.of(name, false));
                assertCrcOfTheChecksum(CrcModel.of(name, true));
            }
        }
        for (CrcModel model : models()) {
            assertCrcOfTheChecksum(model);
        }
    }

    @Test
    void testResetAndTheEmptyData() {
        for (CrcModel model : models()) {
            assertReset(model);
            assertChecksumOfTheEmptyData(model);
        }
    }

    @Test
    void testCheckedStreams(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("data");
        for (CrcModel model : models()) {
            assertCheckedStreams(model, file);
        }
    }

    @Test
    void testTheWayOfTheModel() {
        // the parameters of CRC-32 with other init and xorout, one set calculated by java.util.zip.CRC32, one by the
        // lookup tables
        CrcModel jdk = CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32_POLY, 0x7A5E1D3CL, true, false, 0x3C1D5E7AL), true);
        CrcModel tables = CrcModel.of(CrcParameters.of(32, JdkCrc.CRC32_POLY, 0x1D3C7A5EL, true, false, 0x5E7A3C1DL), false);
        assertTrue(jdk.isJdk(), "java.util.zip.CRC32 has to calculate the model of the JDK");
        assertFalse(tables.isJdk(), "The lookup tables have to calculate the model of the lookup tables");
        assertTheWayOfTheModel(jdk);
        assertTheWayOfTheModel(tables);
    }

    @Test
    void testLengthsBeyondAnInt() {
        // the length of a CRC is a long: no large input is needed
        long[] lengths = {(1L << 31) - 1, 1L << 31, 1L << 32, Long.MAX_VALUE - CHECK.length};
        for (CrcModel model : models()) {
            for (long length : lengths) {
                CRC crc = model.crc(model.check(), length);
                CrcChecksum checksum = crc.checksum();
                assertEquals(length, checksum.length(), model + ": the checksum has to have the length of the CRC of " + length + " bytes");
                checksum.update(CHECK);
                assertEquals(crc.update(CHECK), checksum.crc(), model + ": wrong CRC continued after " + length + " bytes");
            }
            CRC full = model.crc(model.check(), Long.MAX_VALUE);
            CrcChecksum checksum = full.checksum();
            assertThrows(ArithmeticException.class, () -> checksum.update(1), model + ": the length of update(int) has to exceed Long.MAX_VALUE");
            assertThrows(ArithmeticException.class, () -> checksum.update(CHECK), model + ": the length of update(byte[]) has to exceed Long.MAX_VALUE");
            assertEquals(full, checksum.crc(), model + ": the rejected updates have not got to change the checksum");
        }
    }

}
