/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The algorithms of the catalogue that give a CRC value of data: {@link StandardCrc#matching(byte[], long)} and
 * {@link StandardCrc#matching(byte[], int, int, long)}
 */
class MatchingTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    private static final byte[] EMPTY = new byte[0];

    // the lengths of the samples: the byte loop alone, the 8-byte loop of a CRC up to 32 bits wide and of a wider one
    // (from 24 bytes), with and without a rest
    private static final int[] SAMPLE_LENGTHS = {5, 8, 24, 31, 100, 257};

    // the algorithms of the catalogue that the predicate takes, in the order of the catalogue
    private static Set<StandardCrc> algorithms(Predicate<StandardCrc> predicate) {
        return Arrays.stream(StandardCrc.values()).filter(predicate).collect(Collectors.toCollection(() -> EnumSet.noneOf(StandardCrc.class)));
    }

    // the algorithms whose CRC of the part of the data by the reference is the value
    private static Set<StandardCrc> expected(byte[] data, int offset, int length, long value) {
        return algorithms(standard -> CrcReference.crc(standard.parameters(), data, offset, length) == value);
    }

    private static byte[] random(int length, long seed) {
        byte[] data = new byte[length];
        new Random(seed).nextBytes(data);
        return data;
    }

    @Test
    void testCheckValues() {
        for (StandardCrc standard : StandardCrc.values()) {
            long check = standard.check();
            assertEquals(algorithms(other -> other.check() == check), StandardCrc.matching(CHECK, check),
                    standard + ": the check data has to give exactly the algorithms with its check value");
        }
        assertEquals(EnumSet.of(StandardCrc.CRC_16_MODBUS), StandardCrc.matching(CHECK, 0x4B37L), "Wrong algorithms of the check value of CRC-16/MODBUS");
    }

    @Test
    void testSamples() {
        Random random = new Random(126);
        for (StandardCrc standard : StandardCrc.values()) {
            Set<StandardCrc> found = EnumSet.allOf(StandardCrc.class);
            for (int length : SAMPLE_LENGTHS) {
                byte[] sample = new byte[length];
                random.nextBytes(sample);
                long value = CrcReference.crc(standard.parameters(), sample);
                Set<StandardCrc> matching = StandardCrc.matching(sample, value);
                assertTrue(matching.contains(standard), standard + ": a sample of " + length + " bytes has to match the algorithm");
                assertEquals(expected(sample, 0, length, value), matching, standard + ": wrong algorithms of a sample of " + length + " bytes");
                found.retainAll(matching);
            }
            assertEquals(EnumSet.of(standard), found, standard + ": the samples have to give the algorithm alone");
        }
    }

    @Test
    void testOneSampleIsAmbiguous() {
        // the CRC 0 of the empty data: every algorithm with init and xorout 0, among others
        Set<StandardCrc> found = StandardCrc.matching(EMPTY, 0x0L);
        assertTrue(found.containsAll(EnumSet.of(StandardCrc.CRC_8_SMBUS, StandardCrc.CRC_16_ARC, StandardCrc.CRC_16_XMODEM, StandardCrc.CRC_64_ECMA_182)),
                "The algorithms with init and xorout 0 have to match the CRC 0 of the empty data: " + found);
        // a sample of CRC-16/XMODEM narrows them down
        byte[] sample = random(64, 1260);
        found.retainAll(StandardCrc.matching(sample, CrcReference.crc(StandardCrc.CRC_16_XMODEM.parameters(), sample)));
        assertEquals(EnumSet.of(StandardCrc.CRC_16_XMODEM), found, "Another sample has to narrow the algorithms down");
    }

    @Test
    void testValueAboveTheWidth() {
        StandardCrc crc32 = StandardCrc.CRC_32_ISO_HDLC;
        // a CRC-32 kept in an int: the int 0xCBF43926 is negative and sign-extended to a long
        int kept = (int) crc32.check();
        assertFalse(StandardCrc.matching(CHECK, kept).contains(crc32), "The bits above the width of the sign extension have to matter");
        assertEquals(algorithms(other -> other.check() == crc32.check()), StandardCrc.matching(CHECK, kept & 0xFFFFFFFFL),
                "The CRC-32 of an int without the sign extension has to match");
        // the low bits of a 16-bit value do not match a CRC-8
        StandardCrc smbus = StandardCrc.CRC_8_SMBUS;
        assertFalse(StandardCrc.matching(CHECK, 0x100L | smbus.check()).contains(smbus), "The low 8 bits of a 16-bit value have not got to match a CRC-8");
        for (StandardCrc standard : StandardCrc.values()) {
            int width = standard.parameters().width();
            if (width < Long.SIZE) {
                long value = standard.check() | 1L << width;
                assertEquals(algorithms(other -> other.check() == value), StandardCrc.matching(CHECK, value),
                        standard + ": the check value with a bit above the width has to match the wider algorithms with the value only");
            }
        }
        // only the algorithms of 64 bits take a negative value
        assertEquals(algorithms(other -> other.check() == -1L), StandardCrc.matching(CHECK, -1L), "Wrong algorithms of a negative value");
        assertEquals(EnumSet.of(StandardCrc.CRC_64_XZ), StandardCrc.matching(CHECK, StandardCrc.CRC_64_XZ.check()),
                "A negative check value of 64 bits has to match");
    }

    @Test
    void testEmptyData() {
        byte[] data = random(16, 1261);
        for (StandardCrc standard : StandardCrc.values()) {
            long empty = CrcReference.crc(standard.parameters(), EMPTY);
            Set<StandardCrc> expected = expected(EMPTY, 0, 0, empty);
            assertEquals(expected, StandardCrc.matching(EMPTY, empty), standard + ": wrong algorithms of the CRC of the empty data");
            assertEquals(expected, StandardCrc.matching(data, 5, 0, empty), standard + ": an empty part has to be the empty data");
            assertEquals(expected, StandardCrc.matching(data, 16, 0, empty), standard + ": the empty part at the end of the data has to be accepted");
        }
    }

    @Test
    void testPartOfTheData() {
        byte[] data = random(300, 1262);
        // the check data inside other data
        byte[] framed = random(40, 1263);
        System.arraycopy(CHECK, 0, framed, 13, CHECK.length);
        for (StandardCrc standard : StandardCrc.values()) {
            long check = standard.check();
            assertEquals(StandardCrc.matching(CHECK, check), StandardCrc.matching(framed, 13, CHECK.length, check),
                    standard + ": the check data inside other data has to give the algorithms of the check data");
            long value = CrcReference.crc(standard.parameters(), data, 7, 250);
            assertEquals(expected(data, 7, 250, value), StandardCrc.matching(data, 7, 250, value), standard + ": wrong algorithms of a part of the data");
        }
        long arc = CrcReference.crc(StandardCrc.CRC_16_ARC.parameters(), data);
        assertEquals(StandardCrc.matching(data, 0, data.length, arc), StandardCrc.matching(data, arc), "The data has to be the part of all its bytes");
    }

    @Test
    void testNewModifiableSet() {
        Set<StandardCrc> found = StandardCrc.matching(EMPTY, 0x0L);
        List<StandardCrc> inOrder = Arrays.stream(StandardCrc.values()).filter(found::contains).collect(Collectors.toList());
        assertEquals(inOrder, new ArrayList<>(found), "The algorithms have to be in the order of the catalogue");
        assertInstanceOf(EnumSet.class, found, "The algorithms have to be an EnumSet");
        Set<StandardCrc> next = StandardCrc.matching(EMPTY, 0x0L);
        assertNotSame(found, next, "Every call has to give a new set");
        assertEquals(found, next, "The same data and value have to give the same algorithms");
        found.clear();
        assertEquals(next, StandardCrc.matching(EMPTY, 0x0L), "A change of a set has not got to change the next one");
        Set<StandardCrc> none = StandardCrc.matching(CHECK, 0x1L << 50);
        assertTrue(none.isEmpty(), "No algorithm gives the value: the set has to be empty");
        assertTrue(none.add(StandardCrc.CRC_3_GSM), "The empty set has to be modifiable");
        assertEquals(EnumSet.of(StandardCrc.CRC_3_GSM), none, "Wrong set after the change");
    }

    @Test
    void testErrors() {
        byte[] data = new byte[16];
        NullPointerException nullData = assertThrows(NullPointerException.class, () -> StandardCrc.matching(null, 0x0L),
                "matching(null, value) has to throw NullPointerException");
        assertEquals("StandardCrc::matching - data is null", nullData.getMessage(), "Wrong message");
        NullPointerException nullPart = assertThrows(NullPointerException.class, () -> StandardCrc.matching(null, 0, 0, 0x0L),
                "matching(null, offset, length, value) has to throw NullPointerException");
        assertEquals("StandardCrc::matching - data is null", nullPart.getMessage(), "Wrong message");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> StandardCrc.matching(data, -1, 8, 0x0L), "A negative offset has to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> StandardCrc.matching(data, 0, -1, 0x0L), "A negative length has to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> StandardCrc.matching(data, 5, 12, 0x0L), "The bytes beyond the data have to be rejected");
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> StandardCrc.matching(data, 17, 0, 0x0L), "An offset beyond the data has to be rejected");
        ArrayIndexOutOfBoundsException beyond = assertThrows(ArrayIndexOutOfBoundsException.class, () -> StandardCrc.matching(data, 0, 17, 0x0L),
                "More bytes than the data has to be rejected");
        assertEquals("StandardCrc::matching - offset 0 and length 17 out of the buffer length 16", beyond.getMessage(), "Wrong message");
        ArrayIndexOutOfBoundsException overflow = assertThrows(ArrayIndexOutOfBoundsException.class,
                () -> StandardCrc.matching(data, Integer.MAX_VALUE, 2, 0x0L), "An overflowing end has to be rejected");
        assertEquals("StandardCrc::matching - offset 2147483647 and length 2 out of the buffer length 16", overflow.getMessage(), "Wrong message");
    }

    // a model of the algorithm with a new description, without the lookup tables: the description of the earlier tests
    // is collected first, nothing uses it. The unit tests run in one JVM: a model, a CRC or a checksum of the algorithm
    // that another test keeps, in a static field for example, keeps the description until the JVM exits
    private static CrcModel newModel(StandardCrc standard) throws InterruptedException {
        Gc.awaitCleared(new WeakReference<>(CrcDescription.of(standard.parameters())), standard + ": the description of the earlier tests"
                + " has to be collected, no other test may keep a model, a CRC or a checksum of the algorithm, in a static field for example");
        return standard.model();
    }

    // data that a model of the JDK in use calculates without the lookup tables, matched: the probe of the lookup tables
    // of the description of the model, which matching builds
    private static WeakReference<CrcTables> matchWithModelOfTheJdk(StandardCrc standard) throws InterruptedException {
        CrcModel model = newModel(standard);
        assertTrue(model.isJdk(), standard + ": the model of the default has to be calculated by the class of the JDK");
        CrcEngine engine = model.description().engine();
        byte[] data = random(1000, 1264);
        long value = model.value(data);
        assertEquals(CrcReference.crc(standard.parameters(), data), value, standard + ": wrong CRC of the class of the JDK");
        assertFalse(engine.hasTables(), standard + ": the class of the JDK has not got to build the lookup tables");
        assertTrue(StandardCrc.matching(data, value).contains(standard), standard + " has to match the CRC of the data");
        assertTrue(engine.hasTables(), standard + ": matching has to calculate with the lookup tables of the description in the pool");
        return new WeakReference<>(engine.tables());
    }

    @Test
    void testLookupTablesOfThePool() throws InterruptedException {
        assumeTrue(CrcDescription.descriptions().retention() == Retention.WEAK, "the pool refers to the descriptions softly");
        boolean saved = CrcDescription.isJdk();
        CrcDescription.setJdk(true);
        try {
            // java.util.zip.CRC32 calculates the 1000 bytes of the data without the lookup tables; CRC-32/JAMCRC rather
            // than CRC-32/ISO-HDLC, which a test is much more likely to keep a model of
            WeakReference<CrcTables> tables = matchWithModelOfTheJdk(StandardCrc.CRC_32_JAMCRC);
            Gc.awaitCleared(tables, "The lookup tables built by matching have to be collected when no model of the algorithm is used");
        } finally {
            CrcDescription.setJdk(saved);
        }
    }

}
