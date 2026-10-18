/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StandardCrcTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    // a field starts at a word boundary: find() does not try every position inside a word (java:S8786)
    private static final Pattern FIELD = Pattern.compile("\\b(\\w++)=(\"[^\"]*+\"|\\S++)");

    // the lines of the snapshot of the catalogue: the values of the fields, several of them for "alias"
    private static List<Map<String, List<String>>> catalogue() throws IOException {
        List<Map<String, List<String>>> lines = new ArrayList<>();
        try (InputStream stream = Objects.requireNonNull(StandardCrcTest.class.getResourceAsStream("reveng-catalogue.txt"));
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.startsWith("#")) {
                    continue;
                }
                Map<String, List<String>> fields = new LinkedHashMap<>();
                Matcher matcher = FIELD.matcher(line);
                while (matcher.find()) {
                    fields.computeIfAbsent(matcher.group(1), key -> new ArrayList<>()).add(matcher.group(2).replace("\"", ""));
                }
                lines.add(fields);
            }
        }
        return lines;
    }

    private static long hex(Map<String, List<String>> line, String field) {
        return Long.parseUnsignedLong(line.get(field).get(0).substring(2), 16);
    }

    @Test
    void testCatalogue() throws IOException {
        List<Map<String, List<String>>> catalogue = catalogue();
        assertEquals(113, catalogue.size(), "The snapshot of the catalogue has 113 algorithms");
        Set<StandardCrc> found = EnumSet.noneOf(StandardCrc.class);
        for (Map<String, List<String>> line : catalogue) {
            String name = line.get("name").get(0);
            int width = Integer.parseInt(line.get("width").get(0));
            Optional<StandardCrc> standard = StandardCrc.find(name);
            if (width > 64) {
                assertFalse(standard.isPresent(), name + " is wider than 64 bits and has not got to be predefined");
                continue;
            }
            assertTrue(standard.isPresent(), name + " has to be predefined");
            StandardCrc crc = standard.get();
            assertEquals(name, crc.crcName(), "Wrong name of " + crc);
            assertEquals(CrcParameters.of(width, hex(line, "poly"), hex(line, "init"), Boolean.parseBoolean(line.get("refin").get(0)),
                            Boolean.parseBoolean(line.get("refout").get(0)), hex(line, "xorout")), crc.parameters(),
                    "Wrong parameters of " + name);
            assertEquals(hex(line, "check"), crc.check(), "Wrong check value of " + name);
            assertEquals(line.getOrDefault("alias", new ArrayList<>()), crc.aliases(), "Wrong aliases of " + name);
            assertTrue(found.add(crc), name + " has to be found once");
        }
        assertEquals(EnumSet.allOf(StandardCrc.class), found, "Every predefined algorithm has to be an algorithm of the catalogue");
    }

    @Test
    void testNames() {
        assertEquals(112, StandardCrc.values().length, "Wrong number of the predefined algorithms");
        List<String> names = Arrays.stream(StandardCrc.values()).flatMap(crc -> crc.names().stream()).collect(Collectors.toList());
        assertEquals(186, names.size(), "Wrong number of the names with the aliases");
        assertEquals(names.size(), new HashSet<>(names).size(), "The names and the aliases have to be unique");
        for (StandardCrc crc : StandardCrc.values()) {
            assertEquals(crc.crcName(), crc.names().get(0), crc + ": the name has to be the first of the names");
            assertEquals(crc.aliases(), crc.names().subList(1, crc.names().size()), crc + ": the aliases have to follow the name");
            assertEquals(crc.crcName().replaceAll("[^A-Z0-9]", "_"), crc.name(), "Wrong name of the constant of " + crc.crcName());
            assertTrue(crc.crcName().startsWith("CRC-" + crc.parameters().width() + "/"), crc + ": the name has to start with CRC-{width}/");
            assertFalse(crc.crcName().contains("_"), crc + ": a name of the catalogue has got no '_', unlike a name made of the parameters");
            List<String> crcNames = crc.names();
            assertThrows(UnsupportedOperationException.class, () -> crcNames.add("CRC-TEST"), crc + ": the names have to be unmodifiable");
            List<String> crcAliases = crc.aliases();
            assertThrows(UnsupportedOperationException.class, () -> crcAliases.add("CRC-TEST"), crc + ": the aliases have to be unmodifiable");
        }
    }

    @Test
    void testCheckValues() {
        for (StandardCrc crc : StandardCrc.values()) {
            assertEquals(CrcReference.crc(crc.parameters(), CHECK), crc.check(), crc + ": the check value has to be the CRC of 123456789");
            assertEquals(crc.check(), crc.model().crc(CHECK).value(), crc + ": the model has to calculate the check value");
        }
    }

    @Test
    void testFind() {
        for (StandardCrc crc : StandardCrc.values()) {
            for (String name : crc.names()) {
                assertEquals(Optional.of(crc), StandardCrc.find(name), "Wrong algorithm of the name " + name);
            }
            CrcParameters parameters = crc.parameters();
            assertEquals(Optional.of(crc), StandardCrc.find(CrcParameters.of(parameters.width(), parameters.poly(), parameters.init(),
                    parameters.refin(), parameters.refout(), parameters.xorout())), crc + " has to be found by equal parameters");
        }
        assertFalse(StandardCrc.find("CRC-32/UNKNOWN").isPresent(), "An unknown name has not got to be found");
        assertEquals(Optional.of(StandardCrc.CRC_32_ISO_HDLC), StandardCrc.find("crc-32"), "The case of the letters of a name has not got to matter");
        assertFalse(StandardCrc.find("CRC-32/P4C11DB7_IFFFFFFFF_RI_RO_XFFFFFFFF").isPresent(), "A name made of the parameters is not a name of the catalogue");
        assertFalse(StandardCrc.find(CrcParameters.of(16, 0x1234L, 0x0L, false, false, 0x0L)).isPresent(), "Other parameters have not got to be found");
        assertThrows(NullPointerException.class, () -> StandardCrc.find((String) null), "find(null name) has to throw NullPointerException");
        assertThrows(NullPointerException.class, () -> StandardCrc.find((CrcParameters) null), "find(null parameters) has to throw NullPointerException");
    }

    @Test
    void testModel() {
        for (StandardCrc crc : StandardCrc.values()) {
            CrcModel model = crc.model();
            assertEquals(crc.crcName(), model.name(), crc + ": the model has to have the name of the algorithm");
            assertEquals(Optional.of(crc), model.description().standard(), crc + ": the description has to know the algorithm");
            assertSame(model, CrcModel.of(crc.crcName()), crc + ": the model of the name has to be the same");
        }
    }

}
