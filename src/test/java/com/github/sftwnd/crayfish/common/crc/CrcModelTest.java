/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrcModelTest {

    // a model in a private field of another class: castModel() cannot read it
    private static final CrcModel PRIVATE_MODEL = CrcModel.CRC32;

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
            long crc = m.crcBytewise(m.init, CHECK_BUFF.getBytes(), 0, 9);
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
                    assertEquals(crcVal, Optional.of(crc).map(CRC::getCrc).orElse(null), "Wrong crc in the CRC, created by getCRC(crc, len)");
                    assertEquals(lenVal, Optional.of(crc).map(CRC::getLength).orElse(null), "Wrong length in the CRC, created by getCRC(crc, len)");
                }
        );
    }

    @Test
    void testGetCRCBuff() {
        getModels().filter(m -> m.getCheck()!= null).forEach(
                m -> {
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes());
                    assertSame(m, Optional.of(crc).map(CRC::getModel).orElse(null), "Wrong model in the CRC, created by getCRC('123456789')");
                    assertEquals(m.getCheck(), Optional.of(crc).map(CRC::getCrc).orElse(null), "Wrong crc in the CRC, created by getCRC('123456789')");
                }
        );
    }

    @Test
    void testGetCRCBuffLen() {
        getModels().forEach(
                m -> {
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes(), CHECK_BUFF.length());
                    assertSame(m, Optional.of(crc).map(CRC::getModel).orElse(null), "Wrong model in the CRC, created by getCRC('123456789', 9)");
                    assertEquals(m.getCheck(), Optional.of(crc).map(CRC::getCrc).orElse(null), "Wrong crc in the CRC, created by getCRC('123456789', 9)");
                }
        );
    }

    @Test
    void testGetCRCBuffOffsetLen() {
        getModels().filter(m -> m.getCheck() != null).forEach(
                m -> {
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes(), 0, CHECK_BUFF.length());
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
            for (int i=0; i<CHECK_BUFF.getBytes().length; i++) {
                long crc = crc_general_combine(
                               m.getCRC(CHECK_BUFF.getBytes(), 0, i).crc,
                               m.getCRC(CHECK_BUFF.getBytes(), i, CHECK_BUFF.length() -i).crc,
                        CHECK_BUFF.length() -i,
                               m.getWidth(), m.getInit(), m.getPoly(), m.getXorot(), m.isRefot());
                assertEquals(m.getCheck().longValue(), crc, "Combine crc of byte sequence '123456789' splitted in position "+i+" has to be equals check value");
            }
            assertEquals(
                    m.getCheck().longValue(),
                    crc_general_combine(
                            m.getCRC(CHECK_BUFF.getBytes()).crc, -1, -1,
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
        model.getCRC().update(CHECK_BUFF.getBytes());
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
    void testCastModel() throws NoSuchFieldException {
        assertSame(CrcModel.CRC32_ISO_HDLC, CrcModel.castModel(CrcModel.class.getDeclaredField("CRC32_ISO_HDLC")), "The field of a predefined model has to give the model");
        assertSame(CrcModel.PKZIP, CrcModel.castModel(CrcModel.class.getDeclaredField("PKZIP")), "The field of an alias has to give the alias");
        assertNull(CrcModel.castModel(null), "No field has to give no model");
        assertNull(CrcModel.castModel(CrcModel.class.getDeclaredField("CHECK_BUFF")), "A field of another type has to give no model");
        assertNull(CrcModel.castModel(CrcModel.class.getDeclaredField("CRCB")), "A deprecated field has to give no model: the field that replaces it gives the model");
        assertSame(CrcModel.CRC32, PRIVATE_MODEL, "The private field of the test has to contain a model");
        assertNull(CrcModel.castModel(CrcModelTest.class.getDeclaredField("PRIVATE_MODEL")), "A field that cannot be read has to give no model");
    }

}