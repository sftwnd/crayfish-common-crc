/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.github.sftwnd.crayfish.common.crc.CrcModel.getModels;
import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.*;

class CrcDescriptionTest {

    // every test starts with the predefined models only
    @AfterEach
    void cleanUp() {
        CrcModel.removeRegisteredModels();
    }

    @Test
    void testGetCRC() {
        byte[] buff = "123456789".getBytes(US_ASCII);
        getModels().forEach(
                model -> {
                    CrcDescription crcDescription = model.getCrcDescription();
                    CRC crc = crcDescription.getCRC();
                    crc.update(buff);
                    assertEquals(model.getCheck(), crc.getCrc(), "Wrong description check value for crcDescription");
                }
        );
    }

    @Test
    void testGetCRCCrcVal() {
        getModels().filter(m -> m.getCheck()!= null).forEach(
                m -> {
                    long crcVal = 0x17;
                    int  lenVal = 47;
                    CRC crc = m.getCrcDescription().getCRC(crcVal, lenVal);
                    // 0x17 does not fit into the 3- and 4-bit models: the bits above the width are ignored
                    assertEquals(crcVal & CrcModel.widmask(m.getWidth()), Optional.ofNullable(crc).map(CRC::getCrc).orElse(null), "Wrong crc in the CRC, created by getCRC(crc, len)");
                    assertEquals(lenVal, Optional.ofNullable(crc).map(CRC::getLength).orElse(null), "Wrong length in the CRC, created by getCRC(crc, len)");
                }
        );
    }

    @Test
    void testWidthOutOfRange() {
        for (int width : new int[]{0, -1, 65, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> new CrcDescription(width, 0x07, 0x0, false, false, 0x0),
                    "A CrcDescription with the width " + width + " has to be rejected");
        }
        // the limits 1 and 64 are valid widths
        byte[] buff = "123456789".getBytes(US_ASCII);
        for (CrcDescription description : new CrcDescription[]{
                new CrcDescription(1, 0x1, 0x0, false, false, 0x0),
                new CrcDescription(64, 0x42F0E1EBA9EA3693L, 0x0, false, false, 0x0)}) {
            assertEquals(CrcReference.crc(description, buff), description.getCRC().update(buff).getCrc(),
                    "Wrong crc for the width " + description.getWidth());
        }
    }

    @Test
    void testToString() {
        getModels().map(CrcModel::getCrcDescription).forEach(description -> assertDoesNotThrow(description::toString, "CrcDescription::toString hasn't got to throws exception"));
    }

    @Test
    void testToStringFormat() {
        assertEquals("CRC-32/P4C11DB7_IFFFFFFFF_RI_RO_XFFFFFFFF", new CrcDescription(32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL).toString(),
                "Wrong toString() of the CRC-32/ISO-HDLC parameters");
        assertEquals("CRC-16/P1021_I0", new CrcDescription(16, 0x1021, 0x0, false, false, 0x0).toString(),
                "Wrong toString() of the CRC-16/XMODEM parameters");
    }

    @Test
    void testEqualsAndHashCodeDoNotChangeOnFirstUse() {
        CrcDescription unused = new CrcDescription(13, 0x1CF5, 0x1, true, true, 0x2);
        CrcModel model = CrcModel.construct("CRC-13/TEST-EQUALS-HASHCODE", new CrcDescription(13, 0x1CF5, 0x1, true, true, 0x2));
        model.getCRC().update("123456789".getBytes(US_ASCII));
        CrcDescription used = model.getCrcDescription();
        assertNotNull(used.table_byte, "The lookup table of the used CrcDescription has to be built");
        assertEquals(unused, used, "Used CrcDescription has to be equal to an unused one with the same parameters");
        assertEquals(unused.hashCode(), used.hashCode(), "CrcDescription.hashCode() must not change after first use");
    }

    @Test
    void testGetCRCDoesNotRegisterNewParameters() {
        byte[] buff = "123456789".getBytes(US_ASCII);
        long modelCount = getModels().count();
        // a search over candidate polynomials: every parameter set is used once
        for (int i = 1; i <= 1000; i++) {
            CrcDescription description = new CrcDescription(16, 2 * i + 1, 0x0, false, false, 0x0);
            CRC crc = description.getCRC().update(buff);
            assertEquals(CrcReference.crc(description, buff), crc.getCrc(), "Wrong crc of " + description);
            assertEquals(CrcReference.crc(description, buff), description.getCRC(CrcReference.crc(description, buff, 0, 4), 4).update(buff, 4, 5).getCrc(),
                    "Wrong crc of " + description + " continued from a known crc value");
        }
        // 5 of the parameter sets are those of predefined models, for example CRC-16/XMODEM: no set is registered
        assertEquals(modelCount, getModels().count(), "CrcDescription.getCRC() must not register models");
        assertNull(CrcModel.lookUp(new CrcDescription(16, 0x3, 0x0, false, false, 0x0).toString()), "lookUp() must not find a model for parameters used by getCRC()");
        assertTrue(CrcModel.unregisteredModelCount() <= CrcModel.UNREGISTERED_MODELS, "The cache of CrcDescription.getCRC() has to be bounded");
    }

    @Test
    void testGetCRCWithUnregisteredParameters() {
        byte[] buff = "123456789".getBytes(US_ASCII);
        CrcDescription description = new CrcDescription(16, 0x1234, 0x0, false, false, 0x0);
        CrcModel model = description.getCRC().getModel();
        assertSame(model, description.getCRC().getModel(), "Repeated calls have to use the cached model");
        assertSame(model, new CrcDescription(description).getCRC(0x1L, 1).getModel(), "An equal description has to use the cached model");
        assertNotSame(description, model.getCrcDescription(), "The model must not keep the CrcDescription of the caller");
        assertEquals(description, model.getCrcDescription(), "The model has to keep the parameters of the CrcDescription");
        assertNull(description.table_byte, "The lookup table must not be built in the CrcDescription of the caller");
        assertEquals("CRC-16/P1234_I0", model.getName(), "The model has to be named after the parameters");
        assertEquals(CrcReference.crc(description, buff), model.getCheck(), "Wrong check value of the model");
        // a CRC of the unregistered model and a CRC of a model registered later with the same parameters
        CRC first = description.getCRC().update(buff, 0, 4);
        CrcModel registered = CrcModel.construct(description);
        assertNotSame(model, registered, "construct() has to register a model");
        assertSame(registered, description.getCRC().getModel(), "After construct(), getCRC() has to use the registered model");
        assertEquals(CrcReference.crc(description, buff), first.combine(registered.getCRC(buff, 4, 5)).getCrc(),
                "CRCs of the unregistered and the registered model with the same parameters have to be combined");
    }

    @Test
    void testUnregisteredModel() {
        byte[] buff = "123456789".getBytes(US_ASCII);
        long modelCount = getModels().count();
        CrcDescription description = new CrcDescription(16, 0x1235, 0x0, true, true, 0x0);
        CrcModel model = CrcModel.unregistered(description);
        CrcModel named = CrcModel.unregistered("CRC-16/TEST-UNREGISTERED", description);
        assertEquals("CRC-16/P1235_I0_RI_RO", model.getName(), "An unregistered model without a name has to be named after the parameters");
        assertEquals("CRC-16/TEST-UNREGISTERED", named.getName(), "Wrong name of the unregistered model");
        assertNotSame(model, CrcModel.unregistered(description), "Every call has to create a new model");
        assertNotSame(description, model.getCrcDescription(), "The model must not keep the CrcDescription of the caller");
        assertEquals(modelCount, getModels().count(), "unregistered() must not register models");
        assertNull(CrcModel.lookUp("CRC-16/TEST-UNREGISTERED"), "lookUp() must not find an unregistered model");
        assertEquals(CrcReference.crc(description, buff), named.getCRC(buff).getCrc(), "Wrong crc of the unregistered model");
        assertEquals(named.getCheck(), named.getCRC(buff).getCrc(), "Wrong check value of the unregistered model");
        // a predefined model is copied, not returned
        CrcModel crc32 = CrcModel.unregistered(CrcModel.CRC32);
        assertNotSame(CrcModel.CRC32, crc32, "unregistered() has to create a new model also for predefined parameters");
        assertEquals(CrcModel.CRC32.getCheck(), crc32.getCRC(buff).getCrc(), "Wrong crc of the unregistered CRC-32 model");
        assertSame(CrcModel.CRC32_ISO_HDLC, CrcModel.construct(crc32), "construct() with the parameters of an unregistered model has to return the registered model");
        assertThrows(NullPointerException.class, () -> CrcModel.unregistered(null), "unregistered(null) has to throw NullPointerException");
    }

    @Test
    void testGetCRCDoesNotRegisterModelForKnownParameters() {
        // README "Simple case": a new CrcDescription with the parameters of CRC-3/GSM for every calculation
        long expected = CrcModel.CRC3_GSM.getCRC().update("StringA".getBytes()).getCrc();
        long modelCount = getModels().count();
        for (int i = 0; i < 100; i++) {
            CrcDescription crcDescription = new CrcDescription(3, 0x3, 0x0, false, false, 0x7);
            assertEquals(expected, crcDescription.getCRC().update("StringA".getBytes()).getCrc(), "Wrong CRC value for the parameters of CRC-3/GSM");
            assertEquals(modelCount, getModels().count(), "CrcDescription.getCRC() must not register models for the parameters of a registered model");
        }
    }

}