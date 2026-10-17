/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CRC of a stream and of a channel read to their end by the models chosen with the classes of the JDK
 * ({@code CrcModel.of(name, true)}): maven-failsafe-plugin runs the integration tests with the jar of the project,
 * so that Java 9+ loads the version of JdkCrcs in META-INF/versions/9, which knows java.util.zip.CRC32C
 */
class StreamIT {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    // the parts that a stream reads in turn, fewer bytes than asked: around the minimal lengths of the classes of
    // the JDK (8 bytes for CRC32C, 64 bytes for CRC32, 128 on Java 8) and the buffer of 8192 bytes
    private static final int[] PARTS = {1, 7, 8, 9, 63, 64, 65, 127, 128, 129, 8192, 3, 5000};

    // no bytes, a few bytes, exactly one buffer of 8192 bytes, several buffers and a rest
    private static final int[] SIZES = {0, 9, 8192, 30000};

    private static final byte[] DATA = new byte[30000];

    static {
        new Random(128).nextBytes(DATA);
    }

    private static boolean java9() {
        return !System.getProperty("java.specification.version").startsWith("1.");
    }

    // the models of the algorithms of the catalogue chosen with the classes of the JDK: CRC32 and, on Java 9+, CRC32C
    // calculate the models of their parameters, the lookup tables the other models
    private static List<CrcModel> models() {
        List<CrcModel> models = new ArrayList<>();
        for (StandardCrc standard : StandardCrc.values()) {
            models.add(CrcModel.of(standard.crcName(), true));
        }
        return models;
    }

    // a stream of the data that reads the parts of PARTS in turn. A channel of Channels.newChannel reads the same
    // parts from it: no bytes are available after a part
    private static InputStream inParts(byte[] data) {
        List<InputStream> parts = new ArrayList<>();
        int offset = 0;
        int next = 0;
        while (offset < data.length) {
            int part = Math.min(PARTS[next++ % PARTS.length], data.length - offset);
            parts.add(new ByteArrayInputStream(data, offset, part));
            offset += part;
        }
        return new SequenceInputStream(Collections.enumeration(parts));
    }

    @Test
    void testTheJarIsTested() {
        URL location = JdkCrcs.class.getProtectionDomain().getCodeSource().getLocation();
        assertTrue(location.getPath().endsWith(".jar"), "The integration tests have to run with the jar: " + location);
    }

    @Test
    void testModelsOfTheJdk() {
        for (String name : new String[]{"CRC-32/ISO-HDLC", "CRC-32/JAMCRC"}) {
            assertTrue(CrcModel.of(name, true).isJdk(), name + ": java.util.zip.CRC32 has to calculate the model of the JDK on every Java version");
        }
        assertEquals(java9(), CrcModel.of("CRC-32/ISCSI", true).isJdk(),
                "CRC-32/ISCSI: java.util.zip.CRC32C has to calculate the model of the JDK on Java 9+, the lookup tables on Java 8");
    }

    @Test
    void testStreamsAndChannels() throws IOException {
        for (CrcModel model : models()) {
            // the CRCs of the lookup tables, equal to the CRCs of the JDK
            CrcModel tables = model.withJdk(false);
            CRC first = model.crc(CHECK);
            for (int size : SIZES) {
                byte[] data = Arrays.copyOf(DATA, size);
                String message = model + (model.isJdk() ? " of the JDK" : "") + ", " + size + " bytes";
                CRC expected = tables.crc(data);
                CRC expectedAfter = tables.crc(CHECK).update(data);
                try (InputStream in = inParts(data)) {
                    assertEquals(expected, model.crc(in), message + " of a stream: wrong CRC");
                }
                try (InputStream in = inParts(data)) {
                    assertEquals(expectedAfter, first.update(in), message + " of a stream: wrong CRC after other data");
                }
                try (ReadableByteChannel channel = Channels.newChannel(inParts(data))) {
                    assertEquals(expected, model.crc(channel), message + " of a channel: wrong CRC");
                }
                try (ReadableByteChannel channel = Channels.newChannel(inParts(data))) {
                    assertEquals(expectedAfter, first.update(channel), message + " of a channel: wrong CRC after other data");
                }
            }
        }
    }

}
