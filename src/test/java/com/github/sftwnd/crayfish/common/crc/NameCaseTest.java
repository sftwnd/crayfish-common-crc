/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The names of the catalogue and the parametric names whatever the case of their ASCII letters: every name and alias
 * in lower case, in upper case and in mixed case gives the model of the name as the catalogue writes it, by every
 * lookup and both ways of calculation; letters of other alphabets match nothing
 */
class NameCaseTest {

    // the name with every second letter in lower case
    private static String mixedCase(final String name) {
        StringBuilder mixed = new StringBuilder(name.length());
        boolean lower = false;
        for (char c : name.toCharArray()) {
            if (Character.isLetter(c)) {
                mixed.append(lower ? Character.toLowerCase(c) : c);
                lower = !lower;
            } else {
                mixed.append(c);
            }
        }
        return mixed.toString();
    }

    // the name in lower case and in mixed case
    private static List<String> spellings(final String name) {
        return Arrays.asList(name.toLowerCase(Locale.ROOT), mixedCase(name));
    }

    @Test
    void testTheCatalogueWritesItsNamesInUpperCase() {
        Set<String> names = new HashSet<>();
        for (StandardCrc standard : StandardCrc.values()) {
            for (String name : standard.names()) {
                assertEquals(name, CrcParameters.upperCase(name), name + ": the catalogue has to write the name in upper case");
                assertTrue(names.add(name.toUpperCase(Locale.ROOT)), name + ": no two names of the catalogue may differ by the case only");
            }
        }
        assertEquals(186, names.size(), "Wrong number of the names and aliases of the catalogue");
    }

    @Test
    void testTheNamesOfTheCatalogueInEveryCase() {
        for (StandardCrc standard : StandardCrc.values()) {
            for (String name : standard.names()) {
                for (boolean jdk : new boolean[]{false, true}) {
                    CrcModel model = CrcModel.of(name, jdk);
                    for (String spelling : spellings(name)) {
                        String message = spelling + (jdk ? ", the way of the JDK" : ", the lookup tables");
                        assertSame(model, CrcModel.of(spelling, jdk), message + ": of has to give the model of the name");
                        assertSame(model, CrcModel.find(spelling, jdk).orElse(null), message + ": find has to give the model of the name");
                        assertSame(model, model.description().model(spelling, jdk), message + ": the description has to give the model of the name");
                    }
                }
                for (String spelling : spellings(name)) {
                    assertSame(CrcModel.of(name), CrcModel.of(spelling), spelling + ": of has to give the model of the name of the default way");
                    assertSame(CrcModel.of(name), CrcModel.find(spelling).orElse(null), spelling + ": find has to give the model of the default way");
                    assertSame(CrcModel.of(name), CrcModel.of(name).description().model(spelling), spelling + ": the description has to give the model");
                    assertSame(standard, StandardCrc.find(spelling).orElse(null), spelling + ": StandardCrc.find has to give the algorithm");
                    assertEquals(name, CrcModel.of(spelling).crc().name(), spelling + ": the CRC has to have the name as the catalogue writes it");
                }
            }
        }
    }

    @Test
    void testParametricNamesInEveryCase() {
        CrcModel model = CrcModel.of(CrcParameters.of(16, 0x8005L, 0x0L, true, true, 0x1234L));
        String name = model.name();
        assertEquals("CRC-16/P8005_I0_RI_RO_X1234", name, "Wrong parametric name");
        for (String spelling : spellings(name)) {
            assertSame(model, CrcModel.of(spelling), spelling + ": of has to give the model of the parametric name");
            assertSame(model, CrcModel.find(spelling, false).orElse(null), spelling + ": find has to give the model of the parametric name");
            assertSame(model, model.description().model(spelling), spelling + ": the description has to give the model of the parametric name");
        }
        // the parametric name of an algorithm of the catalogue gives its model with the name of the catalogue
        String arc = CrcModel.of("CRC-16/ARC").parameters().parametricName().toLowerCase(Locale.ROOT);
        assertSame(CrcModel.of("CRC-16/ARC"), CrcModel.of(arc), arc + ": the model of CRC-16/ARC has to be given");
    }

    @Test
    void testLettersOfOtherAlphabets() {
        // the dotless i and the long s are I and S in upper case, the Cyrillic Es looks like C: none of them matches
        for (String name : new String[]{"CRC-32/ıSO-HDLC", "CRC-32/ISO-HDLC".replace('S', 'ſ'), "СRC-32"}) {
            assertFalse(CrcModel.find(name).isPresent(), name + ": no model may be found");
            assertFalse(StandardCrc.find(name).isPresent(), name + ": no algorithm may be found");
            IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> CrcModel.of(name), name + " has to be unknown");
            assertEquals("CrcModel::of - unknown CRC name: " + name, unknown.getMessage(), "Wrong message");
        }
        CrcDescription description = CrcModel.of("CRC-32").description();
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> description.model("crc-32/ıso-hdlc"),
                "The dotless i has not got to match");
        assertEquals("CrcDescription::model - CRC-32/ISO-HDLC has no name crc-32/ıso-hdlc", unknown.getMessage(), "Wrong message");
        assertFalse(CrcModel.find("crc-33").isPresent(), "An unknown name in lower case has to stay unknown");
    }

    @Test
    void testUpperCase() {
        String upper = "CRC-16/ARC";
        assertSame(upper, CrcParameters.upperCase(upper), "A name without a lower-case letter has to be the string itself");
        assertEquals("CRC-16/ARC", CrcParameters.upperCase("crc-16/arc"), "Wrong upper case of a name in lower case");
        assertEquals("CRC-16/ARC", CrcParameters.upperCase("Crc-16/aRc"), "Wrong upper case of a name in mixed case");
        assertEquals("@AZ[`AZ{", CrcParameters.upperCase("@AZ[`az{"), "Only the letters a to z have to change, not the characters around them");
        assertEquals("ıſс", CrcParameters.upperCase("ıſс"), "Letters of other alphabets have to stay");
        assertEquals("", CrcParameters.upperCase(""), "The empty name has to stay empty");
    }

}
