/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrcModelTest {

    private static final byte[] CHECK = CrcParameters.CHECK_INPUT.getBytes(US_ASCII);

    @Test
    void testOfEveryName() {
        for (StandardCrc standard : StandardCrc.values()) {
            List<CrcModel> models = new ArrayList<>();
            for (String name : standard.names()) {
                CrcModel model = CrcModel.of(name);
                assertEquals(name, model.name(), "The model has to have the name it was chosen by");
                assertEquals(name, model.toString(), "toString() has to be the name of the model");
                assertSame(model, CrcModel.of(name), "A model is unique for its description and name: " + name);
                assertSame(model, CrcModel.find(name).orElse(null), "find() has to find the model of " + name);
                assertSame(model, model.description().model(name), "The description has to give the model of " + name);
                assertEquals(Optional.of(standard), model.description().standard(), "Wrong algorithm of the model " + name);
                assertEquals(standard.parameters(), model.parameters(), "Wrong parameters of the model " + name);
                assertEquals(standard.check(), model.check(), "Wrong check value of the model " + name);
                models.add(model);
            }
            CrcModel primary = models.get(0);
            assertTrue(models.stream().allMatch(model -> model.description() == primary.description()),
                    standard + ": the models of the name and of the aliases have to share the description");
            assertSame(primary, standard.model(), standard + ": the model of the algorithm has to have its name");
            assertSame(primary, CrcModel.of(standard.parameters()), standard + ": the model of the parameters has to have the name of the algorithm");
            assertSame(primary, primary.description().model(), standard + ": the model of the description has to have its name");
        }
    }

    @Test
    void testOfParametersOfTheCatalogue() {
        CrcModel model = CrcModel.of(32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL);
        assertEquals("CRC-32/ISO-HDLC", model.name(), "The model of the parameters of the catalogue has to have the name of the algorithm");
        assertSame(CrcModel.of("PKZIP").description(), model.description(), "The parameters and the alias have to share the description");
        assertEquals(Arrays.asList("CRC-32/ISO-HDLC", "CRC-32", "CRC-32/ADCCP", "CRC-32/V-42", "CRC-32/XZ", "PKZIP"),
                model.description().names(), "The description of the parameters has to have the names of the algorithm");
    }

    @Test
    void testOfOtherParameters() {
        CrcModel model = CrcModel.of(16, 0x1234L, 0xFFFFL, false, true, 0xFFL);
        String name = "CRC-16/P1234_IFFFF_RO_XFF";
        assertEquals(name, model.name(), "Parameters outside the catalogue have to have the name made of the parameters");
        assertEquals(Collections.singletonList(name), model.description().names(), "Parameters outside the catalogue have to have one name");
        assertEquals(Collections.emptyList(), model.description().aliases(), "Parameters outside the catalogue have got no aliases");
        assertFalse(model.description().standard().isPresent(), "Parameters outside the catalogue have got no algorithm of the catalogue");
        assertEquals(CrcReference.crc(model.parameters(), CHECK), model.check(), "The check value has to be calculated");
        assertSame(model, CrcModel.of(name), "The name made of the parameters has to give the model");
        assertSame(model, CrcModel.of(model.parameters()), "Equal parameters have to give the model");
    }

    @Test
    void testOfTheNameOfEveryModel() {
        for (int width = 1; width <= 64; width++) {
            long mask = CrcParameters.mask(width);
            for (int kind = 0; kind < 4; kind++) {
                CrcModel model = CrcModel.of(width, 0x42F0E1EBA9EA3693L & mask, 0x0123456789ABCDEFL & mask, (kind & 1) != 0, (kind & 2) != 0,
                        kind < 2 ? 0x0L : 0xFEDCBA9876543210L & mask);
                assertTrue(model.name().startsWith("CRC-" + width + "/"), "The name has to start with CRC-{width}/: " + model);
                assertSame(model, CrcModel.of(model.name()), "The name of a model has to give the model: " + model);
            }
        }
    }

    @Test
    void testParametricNameOfTheCatalogue() {
        // a name made of the parameters, kept before the parameters became an algorithm of the catalogue
        CrcModel model = CrcModel.of("CRC-32/P4C11DB7_IFFFFFFFF_RI_RO_XFFFFFFFF");
        assertSame(CrcModel.of("CRC-32/ISO-HDLC"), model, "The name made of the parameters of the catalogue has to give the model of the algorithm");
    }

    @Test
    void testUnknownNames() {
        for (String name : new String[]{"CRC-32/UNKNOWN", "crc-32", "CRC-32 ", "", "CRC-16/P1234_I0_X0", "CRC-82/DARC"}) {
            assertFalse(CrcModel.find(name).isPresent(), "\"" + name + "\" has not got to be found");
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> CrcModel.of(name),
                    "\"" + name + "\" has to be rejected");
            assertEquals("CrcModel::of - unknown CRC name: " + name, exception.getMessage(), "Wrong message");
        }
        assertThrows(NullPointerException.class, () -> CrcModel.of((String) null), "of(null name) has to throw NullPointerException");
        assertThrows(NullPointerException.class, () -> CrcModel.find(null), "find(null) has to throw NullPointerException");
        assertThrows(NullPointerException.class, () -> CrcModel.of((CrcParameters) null), "of(null parameters) has to throw NullPointerException");
    }

    @Test
    void testBuilder() {
        assertSame(CrcModel.of("CRC-32/ISO-HDLC"),
                CrcModel.builder(32).poly(0x04C11DB7L).init(0xFFFFFFFFL).reflected(true).xorout(0xFFFFFFFFL).build(), "Wrong model of the builder");
        // init and xorout are 0 if they are not set
        assertSame(CrcModel.of("CRC-16/XMODEM"), CrcModel.builder(16).poly(0x1021L).reflected(false).build(), "Wrong model with the default init and xorout");
        // refin and refout set one by one
        assertSame(CrcModel.of("CRC-12/UMTS"), CrcModel.builder(12).poly(0x80FL).refin(false).refout(true).build(), "Wrong model of refin and refout");
        // a builder builds the model of its current values
        CrcModel.Builder builder = CrcModel.builder(16).poly(0x8005L).reflected(true);
        assertSame(CrcModel.of("CRC-16/ARC"), builder.build(), "Wrong model of the builder");
        assertSame(CrcModel.of("CRC-16/MODBUS"), builder.init(0xFFFFL).build(), "Wrong model of the builder after init()");
        assertSame(CrcModel.of("CRC-16/USB"), builder.xorout(0xFFFFL).build(), "Wrong model of the builder after xorout()");
        assertEquals("CRC-16/P1234_I0_RI_RO", CrcModel.builder(16).poly(0x1234L).reflected(true).build().name(), "Wrong name of other parameters");
    }

    private static void assertMissing(String missing, CrcModel.Builder builder) {
        IllegalStateException exception = assertThrows(IllegalStateException.class, builder::build, "build() has to report: " + missing);
        assertEquals("CrcModel.Builder::build - not set: " + missing, exception.getMessage(), "Wrong message");
    }

    @Test
    void testBuilderRequiredElements() {
        assertMissing("poly, refin, refout", CrcModel.builder(8));
        assertMissing("poly", CrcModel.builder(8).reflected(true));
        assertMissing("refin, refout", CrcModel.builder(8).poly(0x07L));
        assertMissing("refout", CrcModel.builder(8).poly(0x07L).refin(true));
        assertMissing("refin", CrcModel.builder(8).poly(0x07L).refout(true));
        // init, xorout and check are optional
        assertMissing("poly, refout", CrcModel.builder(8).refin(false).init(0xFFL).xorout(0xFFL).check(0x4BL));
    }

    @Test
    void testBuilderWidth() {
        for (int width : new int[]{0, 65}) {
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> CrcModel.builder(width),
                    "The width " + width + " has to be rejected");
            assertEquals("CrcModel::builder - width has to be in 1..64: " + width, exception.getMessage(), "Wrong message");
        }
        CrcModel.Builder builder = CrcModel.builder(16);
        assertEquals("CrcModel.Builder::poly - poly 0x18005 does not fit into the width 16 (poly without the x^16 term)",
                assertThrows(IllegalArgumentException.class, () -> builder.poly(0x18005L), "A poly above the width has to be rejected").getMessage(),
                "Wrong message");
        assertEquals("CrcModel.Builder::init - init 0x10000 does not fit into the width 16",
                assertThrows(IllegalArgumentException.class, () -> builder.init(0x10000L), "An init above the width has to be rejected").getMessage(),
                "Wrong message");
        assertEquals("CrcModel.Builder::xorout - xorout 0xFFFFFFFFFFFFFFFF does not fit into the width 16 (an int literal is sign-extended: "
                        + "use a long literal such as 0xFFFFL)",
                assertThrows(IllegalArgumentException.class, () -> builder.xorout(-1L), "A xorout above the width has to be rejected").getMessage(),
                "Wrong message");
        assertEquals("CrcModel.Builder::check - check 0x10000 does not fit into the width 16",
                assertThrows(IllegalArgumentException.class, () -> builder.check(0x10000L), "A check above the width has to be rejected").getMessage(),
                "Wrong message");
        // the rejected values are not set
        assertMissing("poly, refin, refout", builder);
        assertSame(CrcModel.of("CRC-64/WE"), CrcModel.builder(64).poly(0x42F0E1EBA9EA3693L).init(-1L).reflected(false).xorout(-1L)
                .check(0x62EC59E3F1A4F00AL).build(), "A width of 64 bits takes every value");
    }

    @Test
    void testBuilderCheck() {
        for (StandardCrc standard : StandardCrc.values()) {
            CrcParameters parameters = standard.parameters();
            assertSame(standard.model(), CrcModel.builder(parameters.width()).poly(parameters.poly()).init(parameters.init()).refin(parameters.refin())
                    .refout(parameters.refout()).xorout(parameters.xorout()).check(standard.check()).build(), standard + ": the check value has to be accepted");
        }
        CrcModel.Builder crc32 = CrcModel.builder(32).poly(0x04C11DB7L).init(0xFFFFFFFFL).reflected(true).xorout(0xFFFFFFFFL).check(0xCBF43927L);
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, crc32::build, "A wrong check value has to be rejected");
        assertEquals("CrcModel.Builder::build - wrong check value 0xcbf43927 for width=32  poly=0x04c11db7  init=0xffffffff  refin=true  refout=true"
                + "  xorout=0xffffffff: the CRC of \"123456789\" is 0xcbf43926", exception.getMessage(), "Wrong message");
        // parameters outside the catalogue: the check value is calculated
        CrcParameters parameters = CrcParameters.of(21, 0x102899L, 0x1FFFFFL, true, false, 0x1L);
        long check = CrcReference.crc(parameters, CHECK);
        CrcModel.Builder builder = CrcModel.builder(21).poly(0x102899L).init(0x1FFFFFL).refin(true).refout(false).xorout(0x1L);
        assertEquals(parameters, builder.check(check).build().parameters(), "The check value of other parameters has to be accepted");
        CrcModel.Builder wrongCheck = builder.check(check ^ 0x1L);
        assertThrows(IllegalArgumentException.class, wrongCheck::build, "A wrong check value of other parameters has to be rejected");
    }

    @Test
    void testCrc() {
        CrcModel model = CrcModel.of("CRC-16/ARC");
        CrcParameters parameters = model.parameters();
        assertSame(model.crc(), model.crc(), "The CRC of the empty data has to be kept by the model");
        assertSame(model, model.crc().model(), "Wrong model of the CRC");
        assertEquals(0, model.crc().length(), "Wrong length of the empty data");
        assertEquals(CrcReference.crc(parameters, new byte[0]), model.crc().value(), "Wrong CRC of the empty data");
        assertEquals(model.check(), model.crc(CHECK).value(), "Wrong CRC of the data");
        assertEquals(CHECK.length, model.crc(CHECK).length(), "Wrong length of the data");
        assertEquals(CrcReference.crc(parameters, CHECK, 2, 5), model.crc(CHECK, 2, 5).value(), "Wrong CRC of a part of the data");
        assertEquals(5, model.crc(CHECK, 2, 5).length(), "Wrong length of a part of the data");
    }

    @Test
    void testCrcOfDataProcessedBefore() {
        CrcModel model = CrcModel.of("CRC-16/ARC");
        CRC part = model.crc(CHECK, 0, 4);
        CRC resumed = model.crc(part.value(), part.length());
        assertEquals(part, resumed, "A saved CRC has to be restored");
        assertEquals(model.check(), resumed.update(CHECK, 4, 5).value(), "A restored CRC has to continue the calculation");
        assertEquals(part, model.crc(part.value() | 0xFFFF_0000L, part.length()), "The bits above the width have to be ignored");
        assertSame(model.crc(), model.crc(0x1234L, 0), "The value of the empty data has to be ignored");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> model.crc(0x0L, -1L), "A negative length has to be rejected");
        assertEquals("CrcModel::crc - length is negative: -1", exception.getMessage(), "Wrong message");
    }

}
