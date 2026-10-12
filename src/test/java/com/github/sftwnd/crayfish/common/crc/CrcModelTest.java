/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.github.sftwnd.crayfish.common.crc.CrcModel.CHECK_BUFF;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.CKSUM;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.CRC16_XMODEM;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.CRC32_AIXM;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.CRC32_CKSUM;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.CRC32_POSIX;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.CRC32_Q;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.CRC4_G_704;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.CRC4_ITU;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.CRC64_GO_ISO;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.CRC64_XZ;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.construct;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.crc_general_combine;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.lookUp;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.getModels;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.reflect;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrcModelTest {

    // every test starts with the predefined models only
    @AfterEach
    void cleanUp() {
        CrcModel.removeRegisteredModels();
    }

    @Test
    void testPoly() {
        getModels().forEach(
                m -> assertEquals(m.isRefin() ? reflect(m.getPoly(), m.getWidth()) : m.getPoly(), m.poly, "Wrong poly field initialization in constructor")
        );
    }

    @Test
    void testInit() {
        getModels().forEach(
                m -> assertEquals((m.isRefot() ? CrcModel.reflect(m.getInit(), m.getWidth()) : m.getInit()) ^ m.getXorot(), m.init, "Wrong init field initialization in constructor")
        );
    }

    @Test
    void testRefot() {
        getModels().forEach(
                m -> assertEquals(m.isRefot() ^ m.isRefin(), m.refot, "Wrong refot field initialization in constructor")
        );
    }

    @Test
    void testGetModels() {
        assertTrue(getModels().count() >= 101, "Wrong getModels size");
    }

    @Test
    void testGetModelsByName() {
        getModels().forEach(
                m -> {
                    CrcModel model = getModels(mdl -> mdl.getName().equals(m.getName())).findFirst().orElse(null);
                    assertSame(m, model, "getModels(byName) result is wrong");
                }
        );
    }

    @Test
    void testCrcBytewise() {
        getModels().filter(m -> m.getCheck() != null).forEach(m -> {
            m._init();
            long crc = m.crcBytewise(m.init, CHECK_BUFF.getBytes(US_ASCII), 0, 9);
            assertEquals(m.getCheck().longValue(), crc, "Update crc by '123456789' buffer has to be equals check value");
            crc = m.crcBytewise(m.init, null, 0, 9);
            assertEquals(m.init, crc, "Update crc by null buffer has got to return init value");
            crc = m.crcBytewise(m.init, null, 0, -1);
            assertEquals(m.init, crc, "Update crc by buffer with -1 length has got to return init value");
        });
    }

    @Test
    void testCrcBytewiseAllByteValues() {
        getModels().forEach(m -> {
            m._init();
            for (int value = 0; value < 256; value++) {
                byte[] buff = {(byte) value};
                assertEquals(CrcReference.crc(m.getCrcDescription(), buff), m.crcBytewise(m.init, buff, 0, 1),
                        "Model " + m + ": crc of the byte 0x" + Integer.toHexString(value) + " has to be equal to the bitwise calculated value");
            }
        });
    }

    @Test
    void testCrcBytewiseNonReflectedInputReflectedOutput() {
        // width <= 8 with refin = false and refot = true: the result of the narrow table lookup is reflected
        CrcModel crc8 = construct("CRC-8/TEST-REFIN-FALSE-REFOUT-TRUE", new CrcDescription(8, 0x07, 0x00, false, true, 0x00));
        CrcModel crc5 = construct("CRC-5/TEST-REFIN-FALSE-REFOUT-TRUE", new CrcDescription(5, 0x15, 0x1E, false, true, 0x03));
        assertEquals(0x91L, crc8.getCRC(new byte[]{(byte) 0x80}).getCrc(), "Wrong crc of the byte 0x80 for model " + crc8);
        assertEquals(0x2L, crc5.getCRC(new byte[]{(byte) 0x80}).getCrc(), "Wrong crc of the byte 0x80 for model " + crc5);
        byte[] allValues = new byte[256];
        for (int value = 0; value < allValues.length; value++) {
            allValues[value] = (byte) value;
        }
        for (CrcModel m : new CrcModel[]{crc8, crc5}) {
            for (int value = 0; value < 256; value++) {
                byte[] buff = {(byte) value};
                assertEquals(CrcReference.crc(m.getCrcDescription(), buff), m.getCRC(buff).getCrc(),
                        "Model " + m + ": crc of the byte 0x" + Integer.toHexString(value) + " has to be equal to the bitwise calculated value");
            }
            long expected = CrcReference.crc(m.getCrcDescription(), allValues);
            assertEquals(expected, m.getCRC(allValues).getCrc(),
                    "Model " + m + ": crc of the bytes 0x00..0xFF has to be equal to the bitwise calculated value");
            assertEquals(expected, m.getCRC(allValues, 0, 100).update(allValues, 100, 156).getCrc(),
                    "Model " + m + ": crc of the bytes 0x00..0xFF updated in two parts has to be equal to the bitwise calculated value");
            assertEquals(expected, m.getCRC(allValues, 0, 100).combine(m.getCRC(allValues, 100, 156)).getCrc(),
                    "Model " + m + ": combined crc of two parts of the bytes 0x00..0xFF has to be equal to the bitwise calculated value");
        }
    }

    @Test
    void testGetModelsByDescription() {
        getModels().forEach(
                m -> {
                    CrcModel model = getModels(mdl -> mdl.getCrcDescription().equals(m.getCrcDescription())).findFirst().orElse(null);
                    assertNotNull(model, "getModels(byDescription: '"+m.getName()+"') result hasn't got to be null");
                    assertEquals(m.getCrcDescription(), model.getCrcDescription(), "getModels(byDescription: '"+m.getName()+"').crcDescription value is wrong for model: "+m.getName());
                }
        );
    }

    @Test
    void testCRCLengthArg() {
        Assertions.assertThrows(
                IllegalArgumentException.class
               ,() -> CRC64_XZ.getCRC(0, -1)
               ,"getCrc(length < 0) had to throw IllegalArgumentException"
        );
    }

    @Test
    void testGetCRC() {
        getModels().forEach(
                m -> {
                    CRC crc = m.getCRC();
                    assertSame(m, Optional.of(crc).map(CRC::getModel).orElse(null), "Wrong model in the CRC, created by getCRC");
                }
        );
    }

    @Test
    void testGetCRCCrcVal() {
        getModels().filter(m -> m.getCheck()!= null).forEach(
                m -> {
                    long crcVal = 0x17;
                    int  lenVal = 47;
                    CRC crc = m.getCRC(crcVal, lenVal);
                    assertSame(m, Optional.of(crc).map(CRC::getModel).orElse(null), "Wrong model in the CRC, created by getCRC(crc, len)");
                    // 0x17 does not fit into the 3- and 4-bit models: the bits above the width are ignored
                    assertEquals(crcVal & CrcModel.widmask(m.getWidth()), Optional.of(crc).map(CRC::getCrc).orElse(null), "Wrong crc in the CRC, created by getCRC(crc, len)");
                    assertEquals(lenVal, Optional.of(crc).map(CRC::getLength).orElse(null), "Wrong length in the CRC, created by getCRC(crc, len)");
                }
        );
    }

    @Test
    void testGetCRCBuff() {
        getModels().filter(m -> m.getCheck()!= null).forEach(
                m -> {
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes(US_ASCII));
                    assertSame(m, Optional.of(crc).map(CRC::getModel).orElse(null), "Wrong model in the CRC, created by getCRC('123456789')");
                    assertEquals(m.getCheck(), Optional.of(crc).map(CRC::getCrc).orElse(null), "Wrong crc in the CRC, created by getCRC('123456789')");
                }
        );
    }

    @Test
    void testGetCRCBuffLen() {
        getModels().forEach(
                m -> {
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes(US_ASCII), CHECK_BUFF.length());
                    assertSame(m, Optional.of(crc).map(CRC::getModel).orElse(null), "Wrong model in the CRC, created by getCRC('123456789', 9)");
                    assertEquals(m.getCheck(), Optional.of(crc).map(CRC::getCrc).orElse(null), "Wrong crc in the CRC, created by getCRC('123456789', 9)");
                }
        );
    }

    @Test
    void testGetCRCBuffOffsetLen() {
        getModels().filter(m -> m.getCheck() != null).forEach(
                m -> {
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes(US_ASCII), 0, CHECK_BUFF.length());
                    assertSame(m, Optional.of(crc).map(CRC::getModel).orElse(null), "Wrong model in the CRC, created by getCRC('123456789', 0, 9)");
                    assertEquals(m.getCheck(), Optional.of(crc).map(CRC::getCrc).orElse(null), "Wrong crc in the CRC, created by getCRC('123456789', 0, 9)");
                }
        );
    }

    @Test
    void testFind() {
        assertNull(lookUp("$$$"), "Result for unknown model has to be null");
        assertSame(CRC64_GO_ISO, lookUp(CRC64_GO_ISO.getName()), "Result for lookUp(known model) has to return known model");
    }

    @Test
    void testConstruct() {
        assertSame(CRC64_GO_ISO, construct(null, CRC64_GO_ISO, CRC64_GO_ISO.getCheck()), "Result for construct(null name, known model, check value) has to return known model");
        assertSame(CRC64_GO_ISO, construct(null, CRC64_GO_ISO, null), "Result for construct(null name, known model, null check value) has to return known model");
        assertSame(CRC64_GO_ISO, construct("CRC-64/GO-ISO", CRC64_GO_ISO, null), "Result for construct(name, known model, null check value) has to return known model");
        assertSame(CRC32_AIXM, construct("CRC-32/AIXM", CRC32_Q, null), "Result for construct(name, model with the same parameters, null check value) has to return model by name");
        assertSame(CRC64_GO_ISO, construct(CRC64_GO_ISO, CRC64_GO_ISO.getCheck()), "Result for construct(known model, check value) has to return known model");
        assertSame(CRC64_GO_ISO, construct(CRC64_GO_ISO), "Result for construct(known model) has to return known model");
        assertThrows(NullPointerException.class, () -> construct(null), "Result for construct(null) has to throws NPE");
        CrcDescription description = new CrcDescription(27, 0x800069, 0x0, true, true, 0x0);
        assertEquals(description, construct("###", description, null).getCrcDescription(), "Result for construct(name, new description, null check value) has to return a model with the description");
        assertSame(CRC4_G_704, CrcModel.construct(CRC4_G_704.getName(), CRC4_ITU.getCrcDescription(), CRC4_ITU.getCheck()), "Result of construct(known name, known description, check) has to be same with model(known name). Model: '"+CRC4_G_704.getName()+"'.");
        assertSame(CRC4_ITU, CrcModel.construct(CRC4_ITU.getName(), CRC4_G_704.getCrcDescription(), CRC4_G_704.getCheck()), "Result of construct(known name, known description, check) has to be same with model(known name). Model: '"+CRC4_ITU.getName()+"'.");
        assertTrue(
                Arrays.asList(CKSUM, CRC32_POSIX, CRC32_CKSUM).contains(
                        construct(null, CRC32_CKSUM.getCrcDescription(), null)
                )
        );
    }

    @Test
    void testConstructNameDescription() {
        CrcModel.getModels().forEach(m -> {
            CrcModel crcModel = CrcModel.construct(null, m.getCrcDescription());
            assertEquals(m.getCheck(), crcModel.getCheck(), "CheckValue for the same CrcDescription has to be equal" );
        });
    }

    @Test
    void testCombine() {
        getModels().filter(m -> m.getCheck() != null).forEach( m -> {
            for (int i=0; i<CHECK_BUFF.getBytes(US_ASCII).length; i++) {
                long crc = crc_general_combine(
                               m.getCRC(CHECK_BUFF.getBytes(US_ASCII), 0, i).crc,
                               m.getCRC(CHECK_BUFF.getBytes(US_ASCII), i, CHECK_BUFF.length() -i).crc,
                        CHECK_BUFF.length() -i,
                               m.getWidth(), m.getInit(), m.getPoly(), m.getXorot(), m.isRefot());
                assertEquals(m.getCheck().longValue(), crc, "Combine crc of byte sequence '123456789' splitted in position "+i+" has to be equals check value");
            }
            assertEquals(
                    m.getCheck().longValue(),
                    crc_general_combine(
                            m.getCRC(CHECK_BUFF.getBytes(US_ASCII)).crc, -1, -1,
                            m.getWidth(), m.getInit(), m.getPoly(), m.getXorot(), m.isRefot()),
                         "Combine crc to crc with negative length has to return nonchanged crc value");
        });
    }

    @Test
    void testConstructWithNewEqualDescription() {
        // make sure that the lookup table of the predefined model is built
        CRC16_XMODEM.getCRC();
        long modelCount = getModels().count();
        CrcModel model = construct(new CrcDescription(16, 0x1021, 0x0, false, false, 0x0));
        assertSame(CRC16_XMODEM.getCrcDescription(), model.getCrcDescription(), "construct() has to return CRC-16/XMODEM or one of its aliases");
        for (int i = 0; i < 10; i++) {
            assertSame(model, construct(new CrcDescription(16, 0x1021, 0x0, false, false, 0x0)), "construct() has to return the same model for equal descriptions");
        }
        assertEquals(modelCount, getModels().count(), "construct() must not register models for the parameters of a registered model");
    }

    @Test
    void testHashCodeDoesNotChangeOnFirstUse() {
        CrcModel model = construct("CRC-16/TEST-HASHCODE", new CrcDescription(16, 0x8BB7, 0x1234, true, false, 0x0));
        Set<CrcModel> models = new HashSet<>(Collections.singleton(model));
        int hashCode = model.hashCode();
        // the first use of the model builds its lookup table
        model.getCRC().update(CHECK_BUFF.getBytes(US_ASCII));
        assertEquals(hashCode, model.hashCode(), "CrcModel.hashCode() must not change after first use");
        assertTrue(models.contains(model), "CrcModel has to be found in a HashSet after first use");
    }

    @Test
    void testCatalogueNames() {
        assertEquals("CRC-5/G-704", CrcModel.CRC5_G_704.getName(), "Wrong name of CRC5_G_704");
        assertEquals("CRC-32C", CrcModel.CRC32_C.getName(), "Wrong name of CRC32_C");
        assertSame(CrcModel.CRC5_G_704, lookUp("CRC-5/G-704"), "lookUp(\"CRC-5/G-704\") has to return CRC5_G_704");
        assertSame(CrcModel.CRC32_C, lookUp("CRC-32C"), "lookUp(\"CRC-32C\") has to return CRC32_C");
    }

    @Test
    void testLookUpMisspelledNames() {
        // names of 1.0.0 and 1.0.1: still accepted by lookUp() and construct(), but no model is listed under them
        assertSame(CrcModel.CRC5_G_704, lookUp("CCRC-5/G-704"), "lookUp(\"CCRC-5/G-704\") has to return CRC5_G_704");
        assertSame(CrcModel.CRC32_C, lookUp("CRC32_C "), "lookUp(\"CRC32_C \") has to return CRC32_C");
        assertSame(CrcModel.CRC5_G_704, construct("CCRC-5/G-704", new CrcDescription(5, 0x15, 0x0, true, true, 0x0)),
                "construct() with the old name and the parameters of CRC5_G_704 has to return CRC5_G_704");
        assertSame(CrcModel.CRC32_C, construct("CRC32_C ", CrcModel.CRC32_ISCSI),
                "construct() with the old name and the parameters of CRC32_C has to return CRC32_C");
        assertEquals(0, getModels(m -> "CCRC-5/G-704".equals(m.getName()) || "CRC32_C ".equals(m.getName())).count(),
                "getModels() must not contain models with the misspelled names");
    }

    @Test
    @SuppressWarnings("deprecation")
    void testDeprecatedConstants() throws IllegalAccessException {
        CrcModel[][] replacements = {
                {CrcModel.CRC_B, CrcModel.CRCB},
                {CrcModel.CRC8_CDMA2000, CrcModel.CRC8_CDMA_2000},
                {CrcModel.CRC8_I_CODE, CrcModel.CRC8_ICODE},
                {CrcModel.CRC16_EN_13757, CrcModel.CRC16_EN13757},
                {CrcModel.CRC32_V_42, CrcModel.CRC32_V42}
        };
        for (CrcModel[] replacement : replacements) {
            assertSame(replacement[0], replacement[1], "The deprecated constant has to refer to the model " + replacement[0]);
            assertEquals(1, getModels(m -> m == replacement[0]).count(), "The model " + replacement[0] + " has to be listed by getModels() once");
        }
        // getModels() skips deprecated fields: each of them has to refer to a listed model
        for (Field field : CrcModel.class.getDeclaredFields()) {
            if (field.getType() == CrcModel.class && field.isAnnotationPresent(Deprecated.class)) {
                CrcModel model = (CrcModel) field.get(null);
                assertEquals(1, getModels(m -> m == model).count(), "The deprecated constant " + field.getName() + " has to refer to a model listed by getModels()");
            }
        }
    }

    @Test
    void testConstantNamesMatchModelNames() throws IllegalAccessException {
        for (Field field : CrcModel.class.getDeclaredFields()) {
            if (field.getType() != CrcModel.class || field.isAnnotationPresent(Deprecated.class)) {
                continue;
            }
            String name = ((CrcModel) field.get(null)).getName();
            String expected = name.replaceFirst("^CRC-(\\d+)([A-Z])$", "CRC$1_$2")  // CRC-32C -> CRC32_C
                                  .replaceFirst("^CRC-(\\d+)", "CRC$1")               // CRC-16/ -> CRC16/
                                  .replaceAll("[-/]", "_");
            assertEquals(expected, field.getName(), "The constant name has to be derived from the model name [" + name + "]");
        }
    }

    @Test
    void testJavadocOfConstants() throws IOException, ReflectiveOperationException {
        // the Javadoc line above every constant shows the model in the notation of the RevEng catalogue, an alias
        // links to its model: the line has to match the model, also after a change of the parameters
        List<String> lines = Files.readAllLines(Paths.get("src/main/java/com/github/sftwnd/crayfish/common/crc/CrcModel.java"), UTF_8);
        Pattern declaration = Pattern.compile("^ {4}public static final CrcModel (\\w+) *= (new CrcModel|alias)\\(");
        Map<CrcDescription, String> models = new IdentityHashMap<>();
        int documented = 0;
        for (int i = 1; i < lines.size(); i++) {
            Matcher matcher = declaration.matcher(lines.get(i));
            if (matcher.find()) {
                String field = matcher.group(1);
                CrcModel model = (CrcModel) CrcModel.class.getField(field).get(null);
                String javadoc;
                if (matcher.group(2).equals("alias")) {
                    javadoc = model.getName() + ", alias of {@link #" + models.get(model.getCrcDescription()) + "}";
                } else {
                    models.put(model.getCrcDescription(), field);
                    javadoc = model.getName();
                }
                int digits = (model.getWidth() + 3) / 4;
                javadoc += ": width=" + model.getWidth() + " poly=" + hex(model.getPoly(), digits) + " init=" + hex(model.getInit(), digits)
                         + " refin=" + model.isRefin() + " refout=" + model.isRefot() + " xorout=" + hex(model.getXorot(), digits)
                         + " check=" + hex(model.getCheck(), digits);
                assertEquals("    /** " + javadoc + " */", lines.get(i - 1), "Wrong Javadoc of the constant " + field);
                documented++;
            }
        }
        long constants = Arrays.stream(CrcModel.class.getDeclaredFields())
                .filter(field -> field.getType() == CrcModel.class && !field.isAnnotationPresent(Deprecated.class))
                .count();
        assertEquals(constants, documented, "Every constant that is not deprecated has to be documented");
    }

    private static String hex(final long value, final int digits) {
        return String.format("0x%0" + digits + "x", value);
    }

    @Test
    void testPredefinedModelsInDeclarationOrder() throws IllegalAccessException {
        List<CrcModel> declared = new ArrayList<>();
        for (Field field : CrcModel.class.getDeclaredFields()) {
            if (field.getType() == CrcModel.class && !field.isAnnotationPresent(Deprecated.class)) {
                declared.add((CrcModel) field.get(null));
            }
        }
        // every non-deprecated constant is listed once, and every listed model is a constant
        assertEquals(declared.size(), getModels().count(), "getModels() has to list every predefined model once");
        assertEquals(new HashSet<>(declared), getModels().collect(Collectors.toSet()), "getModels() has to list the predefined models");
        // a primary model is listed before its aliases
        CrcModel[] order = {CrcModel.CRC16_ARC, CrcModel.ARC, CrcModel.CRC32_ISO_HDLC, CrcModel.CRC32, CrcModel.PKZIP, CrcModel.CRC64_XZ, CrcModel.CRC64_GO_ECMA};
        List<CrcModel> models = getModels().collect(Collectors.toList());
        for (int i = 1; i < order.length; i++) {
            assertTrue(models.indexOf(order[i - 1]) < models.indexOf(order[i]), order[i - 1] + " has to be listed before " + order[i]);
        }
    }

    @Test
    void testConstructReturnsTheModelPassedIn() {
        getModels().forEach(m -> {
            assertSame(m, construct(m), "construct(model) has to return the model " + m);
            assertSame(m, construct(null, m, null), "construct(null, model, null) has to return the model " + m);
            assertSame(m, construct(m.getName(), m), "construct(name of the model, model) has to return the model " + m);
        });
        CrcModel registered = construct("CRC-16/TEST-CONSTRUCT-MODEL", CRC16_XMODEM);
        assertSame(registered, construct(registered), "construct(model) has to return a model registered at run time");
    }

    @Test
    void testConstructReturnsThePrimaryModel() {
        // an alias and its primary model, which is declared before the alias
        CrcModel[][] aliases = {
                {CrcModel.ARC, CrcModel.CRC16_ARC},
                {CrcModel.CRC8, CrcModel.CRC8_SMBUS},
                {CrcModel.CRC32, CrcModel.CRC32_ISO_HDLC},
                {CrcModel.PKZIP, CrcModel.CRC32_ISO_HDLC},
                {CRC32_POSIX, CRC32_CKSUM},
                {CrcModel.CRC16_X_25, CrcModel.CRC16_IBM_SDLC}
        };
        for (CrcModel[] alias : aliases) {
            assertSame(alias[1], construct(alias[0].getCrcDescription()), "construct(parameters of " + alias[0] + ") has to return " + alias[1]);
            assertSame(alias[1], construct(new CrcDescription(alias[0])), "construct(new CrcDescription(" + alias[0] + ")) has to return " + alias[1]);
            assertSame(alias[1], alias[0].getCrcDescription().getCRC().getModel(), "CrcDescription.getCRC() for the parameters of " + alias[0] + " has to use " + alias[1]);
        }
        byte[] data = "hello".getBytes(US_ASCII);
        assertEquals(CrcModel.CRC32_ISO_HDLC.getCRC(data), CrcModel.CRC32_ISO_HDLC.getCrcDescription().getCRC().update(data),
                "CRCs over the same data have to be equal, whether they are created by the model or by its parameters");
    }

    @Test
    void testConstructIsNotChangedByLaterRegistrations() {
        // a model registered later with a name that sorts first, for example by another library in the same JVM
        assertEquals("!my-crc", construct("!my-crc", CrcModel.CRC32).getName(), "The later model has to be registered");
        assertSame(CrcModel.CRC32_ISO_HDLC, construct(CrcModel.CRC32_ISO_HDLC), "construct(model) must not be changed by a later registration");
        assertSame(CrcModel.CRC32_ISO_HDLC, construct(new CrcDescription(CrcModel.CRC32)), "construct(parameters) must not be changed by a later registration");
        assertSame(CrcModel.CRC32_ISO_HDLC, CrcModel.CRC32.getCrcDescription().getCRC().getModel(), "CrcDescription.getCRC() must not be changed by a later registration");
        // parameters that are registered at run time: the model registered first
        CrcModel first = construct("CRC-24/TEST-FIRST", new CrcDescription(24, 0x123457, 0x0, false, false, 0x0));
        construct("!CRC-24/TEST-SECOND", first);
        assertSame(first, construct(new CrcDescription(first)), "construct(parameters) has to return the model registered first");
    }

    @Test
    void testGeneratedNames() {
        assertEquals("CRC-27/P800069_I1_RI_RO_X5", construct(new CrcDescription(27, 0x800069, 0x1, true, true, 0x5)).getName(),
                "The generated name has to show the original parameters");
        CrcModel model = construct(new CrcDescription(32, 0xEDB88320L, 0x0, true, false, 0xFFFFFFFFL));
        assertEquals("CRC-32/PEDB88320_I0_RI_XFFFFFFFF", model.getName(), "The generated name has to show the original parameters");
        assertEquals(model.getCrcDescription().toString(), model.getName(), "The generated name has to be the parameter string of the model");
        assertNotEquals(CrcModel.CRC32_ISO_HDLC.getCrcDescription().toString(), model.getName(), "The generated name must not be the parameter string of CRC-32");
    }

    @Test
    void testNameOfOtherParameters() {
        long modelCount = getModels().count();
        // CRC-16/XMODEM parameters under the name of the predefined 32-bit CRC-32
        CrcDescription xmodem = new CrcDescription(16, 0x1021, 0x0, false, false, 0x0);
        assertThrows(IllegalArgumentException.class, () -> construct("CRC-32", xmodem), "The name of a predefined model has to be rejected for other parameters");
        assertThrows(IllegalArgumentException.class, () -> construct("CRC-32", CRC16_XMODEM), "The name of a predefined model has to be rejected for another model");
        // a name registered at run time
        CrcModel model = construct("CRC-24/TEST-NAME", new CrcDescription(24, 0x123457, 0x0, false, false, 0x0));
        assertThrows(IllegalArgumentException.class, () -> construct("CRC-24/TEST-NAME", new CrcDescription(24, 0x123459, 0x0, false, false, 0x0)),
                "The name of a registered model has to be rejected for other parameters");
        assertSame(model, construct("CRC-24/TEST-NAME", new CrcDescription(model)), "The name with its own parameters has to return the registered model");
        // the misspelled names of 1.0.x belong to their predefined models
        assertThrows(IllegalArgumentException.class, () -> construct("CCRC-5/G-704", new CrcDescription(5, 0x15, 0x1, true, true, 0x0)),
                "A misspelled name of 1.0.x has to be rejected for other parameters");
        assertSame(CrcModel.CRC5_G_704, construct("CCRC-5/G-704", CrcModel.CRC5_ITU), "A misspelled name of 1.0.x with its parameters has to return the predefined model");
        // every name is found by lookUp() and stays with its model
        assertEquals(modelCount + 1, getModels().count(), "Only CRC-24/TEST-NAME has to be registered");
        assertSame(CrcModel.CRC32, lookUp("CRC-32"), "lookUp(\"CRC-32\") has to return the predefined model");
        getModels().forEach(m -> assertSame(m, lookUp(m.getName()), "lookUp(\"" + m + "\") has to return the model"));
    }

    @Test
    void testCheckValueIsVerified() {
        long modelCount = getModels().count();
        // new parameters with a wrong check value: the crc of "123456789" is 0x14AF
        CrcDescription crc13 = new CrcDescription(13, 0x1CF5, 0x1, false, false, 0x0);
        IllegalArgumentException wrong = assertThrows(IllegalArgumentException.class, () -> construct("X", crc13, 0x1234L),
                "A wrong check value has to be rejected for new parameters");
        assertTrue(wrong.getMessage().contains("0x1234") && wrong.getMessage().contains("0x14AF"), "Wrong message: " + wrong.getMessage());
        // the parameters of CRC-16/ARC with a check value that is not the one of CRC-16/ARC
        assertThrows(IllegalArgumentException.class, () -> construct("Y", new CrcDescription(16, 0x8005, 0x0, true, true, 0x0), 0x1234L),
                "A wrong check value has to be rejected for the parameters of a predefined model");
        assertThrows(IllegalArgumentException.class, () -> construct(CRC64_GO_ISO, 0L), "A wrong check value has to be rejected for a predefined model");
        assertEquals(modelCount, getModels().count(), "Rejected calls must not register models");
        // correct check values, also with the bits above the width set by the sign extension of an int
        CrcModel x = construct("X", crc13, 0x14AFL);
        assertEquals(0x14AFL, x.getCheck(), "Wrong check value of X");
        assertNull(x.getCrcDescription().table_byte, "The verification of the check value must not build the lookup table of the new model");
        assertEquals(x.getCheck(), x.getCRC(CHECK_BUFF.getBytes(US_ASCII)).getCrc(), "The check value of X has to be the crc of 123456789");
        CrcModel y = construct("Y", new CrcDescription(16, 0x8005, 0x0, true, true, 0x0), 0xBB3DL);
        assertSame(CrcModel.CRC16_ARC.getCrcDescription(), y.getCrcDescription(), "Y has to share the parameters of CRC-16/ARC");
        assertEquals(CrcModel.CRC16_ARC.getCheck(), y.getCheck(), "Wrong check value of Y");
        CrcModel z = construct("Z", new CrcDescription(32, 0x04C11DB7L, 0x0, false, false, 0x0), (long) (int) 0x89A1897FL);
        assertEquals(0x89A1897FL, z.getCheck(), "A sign-extended check value has to be reduced to the CRC width");
        assertSame(x, construct("X", crc13, 0x14AFL), "A registered model has to be returned for its check value");
        assertThrows(IllegalArgumentException.class, () -> construct("X", crc13, 0x14AEL), "A wrong check value has to be rejected for a registered model");
    }

    @Test
    void testEqualsAndHashCode() {
        CrcModel model = construct("CRC-16/TEST-EQUALS", new CrcDescription(16, 0x8BB7, 0x1, false, false, 0x0));
        CrcModel same = CrcModel.unregistered("CRC-16/TEST-EQUALS", model.getCrcDescription());
        assertEquals(model, model, "A model has to be equal to itself");
        assertEquals(model, same, "Models with the same parameters, name and check value have to be equal");
        assertEquals(model.hashCode(), same.hashCode(), "Equal models have to have the same hash code");
        assertNotEquals(model, null, "A model must not be equal to null");
        assertNotEquals(model, "CRC-16/TEST-EQUALS", "A model must not be equal to another type");
        assertNotEquals(model.getCrcDescription(), model, "A description must not be equal to a model");
        assertNotEquals(model, model.getCrcDescription(), "A model must not be equal to a description");
        assertNotEquals(model, CrcModel.unregistered("CRC-16/TEST-EQUALS-OTHER", model.getCrcDescription()), "Models with different names must not be equal");
        assertNotEquals(model, CrcModel.unregistered("CRC-16/TEST-EQUALS", new CrcDescription(16, 0x8BB7, 0x2, false, false, 0x0)),
                "Models with different parameters must not be equal");
        // an alias has the parameters and the check value of its model, but another name
        assertNotEquals(CrcModel.CRC32_ISO_HDLC, CrcModel.CRC32, "An alias must not be equal to its model");
        assertEquals(CrcModel.CRC32_ISO_HDLC.getCrcDescription(), CrcModel.CRC32.getCrcDescription(), "An alias has to have the parameters of its model");
        // a subclass of CrcDescription is never equal to a model, a subclass of CrcModel can't be created
        CrcDescription subclass = new CrcDescription(model) { };
        assertNotEquals(model, subclass, "A model must not be equal to a subclass of CrcDescription");
        assertNotEquals(subclass, model, "A subclass of CrcDescription must not be equal to a model");
        getModels().forEach(m -> {
            CrcModel copy = CrcModel.unregistered(m.getName(), m.getCrcDescription());
            assertEquals(m, copy, "Model " + m + " has to be equal to its unregistered copy");
            assertEquals(m.hashCode(), copy.hashCode(), "Model " + m + " and its unregistered copy have to have the same hash code");
        });
    }

    @Test
    void testCheckValueOverAscii() {
        // the check value of a model without a given one: the CRC of the ASCII bytes of "123456789", whatever the default charset
        CrcDescription crc16 = new CrcDescription(16, 0x7001, 0x0, false, false, 0x0);
        CrcDescription crc8 = new CrcDescription(8, 0x2B, 0x0, false, false, 0x0);
        assertEquals(0xD50L, construct(crc16).getCheck(), "Wrong check value of " + crc16);
        assertEquals(0x40L, construct(crc8).getCheck(), "Wrong check value of " + crc8);
        for (CrcDescription description : new CrcDescription[]{crc16, crc8}) {
            assertEquals(CrcReference.crc(description, CHECK_BUFF.getBytes(US_ASCII)), construct(description).getCheck(),
                    "The check value of " + description + " has to be the CRC of the ASCII bytes of 123456789");
        }
    }

}