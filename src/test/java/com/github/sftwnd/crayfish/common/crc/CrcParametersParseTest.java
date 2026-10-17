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
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrcParametersParseTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    // the name of a line of the catalogue, and an alias of the snapshot, which the parameter lines of the catalogue
    // have not got; a field starts at a word boundary: find() does not try every position inside a word (java:S8786)
    private static final Pattern NAME = Pattern.compile("\\bname=\"([^\"]*+)\"");
    private static final Pattern ALIAS = Pattern.compile("\\balias=\"[^\"]*+\"");

    // the fields of the parameter line of CRC-16/ARC
    private static final String[] ARC = {"width=16", "poly=0x8005", "init=0x0000", "refin=true", "refout=true", "xorout=0x0000"};

    // the lines of the snapshot of the catalogue, without the aliases
    private static List<String> catalogue() throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream stream = Objects.requireNonNull(CrcParametersParseTest.class.getResourceAsStream("reveng-catalogue.txt"));
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.startsWith("#")) {
                    lines.add(ALIAS.matcher(line).replaceAll(""));
                }
            }
        }
        return lines;
    }

    // the line of CRC-16/ARC with the value of a field: the field moves to the end of the line, or is added there
    private static String arc(String field, String value) {
        List<String> fields = new ArrayList<>();
        for (String arcField : ARC) {
            if (!arcField.startsWith(field + "=")) {
                fields.add(arcField);
            }
        }
        fields.add(field + "=" + value);
        return String.join("  ", fields);
    }

    private static void assertRejected(String message, String line) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> CrcParameters.parse(line),
                "The line has to be rejected: " + line);
        assertEquals("CrcParameters::parse - " + message, exception.getMessage(), "Wrong message of the line " + line);
    }

    @Test
    void testCatalogue() throws IOException {
        List<String> lines = catalogue();
        assertEquals(113, lines.size(), "The snapshot of the catalogue has 113 algorithms");
        Set<StandardCrc> found = EnumSet.noneOf(StandardCrc.class);
        for (String line : lines) {
            Matcher name = NAME.matcher(line);
            assertTrue(name.find(), "A line of the catalogue has to have a name: " + line);
            Optional<StandardCrc> standard = StandardCrc.find(name.group(1));
            if (standard.isPresent()) {
                StandardCrc crc = standard.get();
                assertEquals(crc.parameters(), CrcParameters.parse(line), "Wrong parameters of the line of " + crc.crcName());
                assertSame(crc.model(), CrcModel.of(CrcParameters.parse(line)), crc + ": the parameters have to give the model with its name");
                assertTrue(found.add(crc), crc + " has to be found once");
            } else {
                // CRC-82/DARC is not predefined: wider than 64 bits
                assertRejected("width has to be in 1..64: 82", line);
            }
        }
        assertEquals(EnumSet.allOf(StandardCrc.class), found, "Every predefined algorithm has to be parsed from its line");
    }

    @Test
    void testToStringOfTheCatalogue() {
        for (StandardCrc crc : StandardCrc.values()) {
            CrcParameters parameters = crc.parameters();
            assertEquals(parameters, CrcParameters.parse(parameters.toString()), crc + ": toString() of the parameters has to give them back");
            CrcDescription description = crc.model().description();
            assertEquals(parameters, CrcParameters.parse(description.toString()), crc + ": toString() of the description has to give its parameters");
        }
    }

    @Test
    void testToStringOfOtherParameters() {
        Random random = new Random(127);
        for (int width = 1; width <= 64; width++) {
            long mask = CrcParameters.mask(width);
            for (int kind = 0; kind < 4; kind++) {
                // every refin and refout; poly and xorout with all bits of the width and init without bits, then random values
                boolean first = kind == 0;
                CrcParameters parameters = CrcParameters.of(width, first ? mask : random.nextLong() & mask, first ? 0 : random.nextLong() & mask,
                        (kind & 1) != 0, (kind & 2) != 0, first ? mask : random.nextLong() & mask);
                assertEquals(parameters, CrcParameters.parse(parameters.toString()), "toString() of the parameters has to give them back: " + parameters);
                CrcDescription description = CrcModel.of(parameters).description();
                assertEquals(parameters, CrcParameters.parse(description.toString()), "toString() of the description has to give its parameters: " + description);
            }
        }
    }

    @Test
    void testFormat() {
        CrcParameters arc = StandardCrc.CRC_16_ARC.parameters();
        String[] lines = {
            // the fields in other orders
            "xorout=0x0000 refout=true refin=true init=0x0000 poly=0x8005 width=16",
            "name=\"CRC-16/ARC\" residue=0x0000 check=0xbb3d xorout=0x0000 refout=true refin=true init=0x0000 poly=0x8005 width=16",
            "refin=true width=16 check=0xbb3d refout=true init=0x0000 name=\"CRC-16/ARC\" poly=0x8005 xorout=0x0000",
            // other whitespace between, before and after the fields: tabs, line breaks, a form feed
            "\twidth=16\tpoly=0x8005\tinit=0x0000\trefin=true\trefout=true\txorout=0x0000\t",
            "  width=16 \r\n poly=0x8005\ninit=0x0000\frefin=true refout=true     xorout=0x0000\r\n",
            // digits of any case, other numbers of digits
            "width=16 poly=0x8005 init=0x0 refin=true refout=true xorout=0x00000000000000000000 check=0xBB3D",
            "width=016 poly=0x00008005 init=0x0000 refin=true refout=true xorout=0x0000 check=0xBb3d residue=0x0",
            // a name is ignored: whitespace and '=' inside the quotes, an empty name, the name of other parameters
            "width=16 poly=0x8005 init=0x0000 refin=true refout=true xorout=0x0000 name=\" CRC-16 / ARC = IBM \"",
            "width=16 poly=0x8005 init=0x0000 refin=true refout=true xorout=0x0000 name=\"\"",
            "width=16 poly=0x8005 init=0x0000 refin=true refout=true xorout=0x0000 name=\"CRC-16/MODBUS\""
        };
        for (String line : lines) {
            assertEquals(arc, CrcParameters.parse(line), "Wrong parameters of the line " + line);
        }
    }

    @Test
    void testCheck() {
        assertRejected("wrong check value 0xbb3e for width=16  poly=0x8005  init=0x0000  refin=true  refout=true  xorout=0x0000: "
                + "the CRC of \"123456789\" is 0xbb3d", arc("check", "0xBB3E"));
        // the check value of parameters outside the catalogue
        CrcParameters parameters = CrcParameters.of(21, 0x102899L, 0x1FFFFFL, true, false, 0x1L);
        long value = CrcReference.crc(parameters, CHECK);
        String check = CrcParameters.hex(value, 21);
        assertEquals(parameters, CrcParameters.parse(parameters + "  check=" + check), "The check value of other parameters has to be accepted");
        String wrong = CrcParameters.hex(value ^ 0x100000L, 21);
        assertRejected("wrong check value " + wrong + " for " + parameters + ": the CRC of \"123456789\" is " + check,
                parameters + "  check=" + wrong);
    }

    @Test
    void testNull() {
        NullPointerException exception = assertThrows(NullPointerException.class, () -> CrcParameters.parse(null),
                "parse(null) has to throw NullPointerException");
        assertEquals("CrcParameters::parse - line is null", exception.getMessage(), "Wrong message");
    }

    @Test
    void testFields() {
        String arc = String.join("  ", ARC);
        String[][] errors = {
            // not a field name=value: the rest of the line from there on
            {"not a field name=value: CRC-16/ARC", arc + "  CRC-16/ARC"},
            {"not a field name=value: width = 16  poly=0x8005", "width = 16  poly=0x8005"},
            {"not a field name=value: =16  poly=0x8005", "=16  poly=0x8005"},
            {"not a field name=value: xorout=", "width=16  poly=0x8005  init=0x0000  refin=true  refout=true  xorout="},
            {"not a field name=value: name=\"CRC-16/ARC  check=0xbb3d", arc + "  name=\"CRC-16/ARC  check=0xbb3d"},
            {"not a field name=value: name=\"CRC-16/ARC\"\"", arc + "  name=\"CRC-16/ARC\"\""},
            {"not a field name=value: name=\"CRC-16\"/ARC", arc + "  name=\"CRC-16\"/ARC"},
            {"not a field name=value: poly=0x80\"05\"  init=0x0000", "width=16  poly=0x80\"05\"  init=0x0000"},
            // fields that the parameter lines of the catalogue have not got, also of another case
            {"unknown field: alias", arc + "  alias=\"ARC\""},
            {"unknown field: Width", "Width=16  poly=0x8005"},
            {"unknown field: xorot", "width=16  poly=0x8005  init=0x0000  refin=true  refout=true  xorot=0x0000"},
            // a field given twice, also with the same value
            {"field given twice: width", arc + "  width=16"},
            {"field given twice: name", arc + "  name=\"ARC\"  name=\"CRC-16/ARC\""},
            // the parameters are required, whatever the optional fields
            {"missing: width, poly, init, refin, refout, xorout", ""},
            {"missing: width, poly, init, refin, refout, xorout", " \t\r\n "},
            {"missing: width, poly, init, refin, refout, xorout", "check=0xbb3d  residue=0x0000  name=\"CRC-16/ARC\""},
            {"missing: init, xorout", "width=16  poly=0x8005  refin=true  refout=true"},
            {"missing: xorout", "width=16  poly=0x8005  init=0x0000  refin=true  refout=true  check=0xbb3d"}
        };
        for (String[] error : errors) {
            assertRejected(error[0], error[1]);
        }
    }

    @Test
    void testValues() {
        String[][] errors = {
            // the width: decimal, 1..64
            {"width is not a decimal number: 0x10", arc("width", "0x10")},
            {"width is not a decimal number: +16", arc("width", "+16")},
            {"width is not a decimal number: 16.0", arc("width", "16.0")},
            {"width is not a decimal number: \"16\"", arc("width", "\"16\"")},
            {"width has to be in 1..64: 0", arc("width", "0")},
            {"width has to be in 1..64: 65", arc("width", "65")},
            {"width has to be in 1..64: 0100", arc("width", "0100")},
            {"width has to be in 1..64: 18446744073709551616", arc("width", "18446744073709551616")},
            // the hexadecimal values: 0x and digits, fitting into the width
            {"poly is not a hexadecimal number with 0x: 8005", arc("poly", "8005")},
            {"poly is not a hexadecimal number with 0x: 0X8005", arc("poly", "0X8005")},
            {"init is not a hexadecimal number with 0x: 0x", arc("init", "0x")},
            {"init is not a hexadecimal number with 0x: 0x-1", arc("init", "0x-1")},
            {"xorout is not a hexadecimal number with 0x: 0x00g0", arc("xorout", "0x00g0")},
            {"check is not a hexadecimal number with 0x: 0xbb3d,", arc("check", "0xbb3d,")},
            {"residue is not a hexadecimal number with 0x: \"0x0000\"", arc("residue", "\"0x0000\"")},
            {"poly 0x18005 does not fit into the width 16 (poly without the x^16 term)", arc("poly", "0x18005")},
            {"init 0x10000 does not fit into the width 16", arc("init", "0x10000")},
            {"xorout 0xffffffffffffffff does not fit into the width 16", arc("xorout", "0xffffffffffffffff")},
            {"check 0x1BB3D does not fit into the width 16", arc("check", "0x1BB3D")},
            {"residue 0x10000 does not fit into the width 16", arc("residue", "0x10000")},
            {"xorout 0x10000000000000000 does not fit into the width 64",
                "width=64  poly=0x42f0e1eba9ea3693  init=0x0000000000000000  refin=false  refout=false  xorout=0x10000000000000000"},
            {"poly 0x0308c0111011401440411 does not fit into the width 64 (poly without the x^64 term)",
                "width=64  poly=0x0308c0111011401440411  init=0x0  refin=true  refout=true  xorout=0x0"},
            // the flags: true or false
            {"refin is not true or false: TRUE", arc("refin", "TRUE")},
            {"refin is not true or false: 1", arc("refin", "1")},
            {"refout is not true or false: False", arc("refout", "False")},
            {"refout is not true or false: \"true\"", arc("refout", "\"true\"")},
            // the name: a quoted string
            {"name is not a quoted string: CRC-16/ARC", arc("name", "CRC-16/ARC")}
        };
        for (String[] error : errors) {
            assertRejected(error[0], error[1]);
        }
    }

}
