/*
 * Copyright (c) 2017-20xx Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static com.github.sftwnd.crayfish.common.crc.CrcModel.CHECK_BUFF;
import static com.github.sftwnd.crayfish.common.crc.CrcModel.getModels;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CRCTest {

    @Test
    void testUpdate() {
        getModels(m -> m.getCheck() != null).forEach( m -> {
            CRC crc = m.getCRC();
            long crcValue = crc.update(CHECK_BUFF.getBytes()).getCrc();
            assertEquals(m.getCheck(), crc.getCrc(), "CRC value for byte sequence of 123456789 has to be equals check value");
            assertEquals(m.getCheck(), crcValue, "Crc update result for byte sequence of 123456789 has to be equals check value");
            assertEquals(m.getCheck(), crc.update(CHECK_BUFF.getBytes(), 1, -1).getCrc(), "Update with negative length has not got to change result");
            assertEquals(m.getCheck(), crc.update(null, 1, 1).getCrc(), "Update with null reference to buff has not got to change result");
            assertEquals(m.getCheck(), crc.update(CHECK_BUFF.getBytes(), 1, -1).getCrc(), "Update with negative length has not got to change result");
            assertEquals(m.getCheck(), m.getCRC().update(CHECK_BUFF.getBytes(), 4).update(CHECK_BUFF.getBytes(), 4, 5).getCrc(), "Update with negative length has not got to change result");
        });
    }

    @Test
    void combineUpdate() {
        getModels(m -> m.getCheck() != null).forEach( m -> {
            CRC crc = m.getCRC();
            crc.update(CHECK_BUFF.getBytes(), 0, 4).combine(m.getCRC().update(CHECK_BUFF.getBytes(), 4, 5));
            assertEquals(m.getCheck(), crc.getCrc(), "CRC combine value of crcs of byte sequence of 1234 & 56789 has to be equals check value");
            crc = m.getCRC().combine(m.getCRC().update(CHECK_BUFF.getBytes()));
            assertEquals(m.getCheck(), crc.getCrc(), "Clear CRC combine value of crc of byte sequence of 123456789 has to be equals check value");
            crc = m.getCRC().update(CHECK_BUFF.getBytes()).combine(m.getCRC());
            assertEquals(m.getCheck(), crc.getCrc(), "CRC of byte sequence of 123456789 combine with clear crc has to be equals check value");
        });
    }

    @Test
    void testCopy() {
        getModels().forEach(
                m -> {
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes());
                    CRC crc1 = crc.copy();
                    assertEquals(crc, crc1, "CRC and CRC.clone() have to be equals");
                }
        );
    }

    @Test
    void testGetCrc() {
        getModels(m -> m.getCheck() != null).forEach(
                m -> {
                    CRC crc = m.getCRC();
                    assertEquals(m.getInit(), crc.getCrc(), "Clear CRC value has to be equals model initial value");
                    assertEquals(m.getCheck(), crc.update(CHECK_BUFF.getBytes()).getCrc(), "CRC value of 123456789 has to be equals model check value");
                    assertEquals(m.getInit(), m.getCRC(null).getCrc(), "CRC value of empty buff has to be equals model initial value");
                }
        );
    }

    @Test
    void testConstructor() {
        getModels().forEach(
                m -> {
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes());
                    CRC crc1 = new CRC(crc);
                    assertEquals(crc, crc1, "CRC and new CRC(crc) have to be equals");
                }
        );
    }

    @Test
    void testHashCode() {
        getModels().forEach(
                m -> {
                    CRC crc = m.getCRC(CHECK_BUFF.getBytes());
                    CRC crc1 = crc.copy();
                    assertEquals(crc.hashCode(), crc1.hashCode(), "CRC.hashCode() on cloned CRCs have to be equals");
                }
        );
    }

    @Test
    void testFind() {
        assertNull(CrcModel.lookUp(null), "CrcModel.lookUp(null) has to be null");
        getModels().forEach( model -> assertSame(model, CrcModel.lookUp(model.getName()), "CrcModel.lookUp(\"" + model + "\") has to be same with model"));
    }

    @Test
    void testToString() {
        Set<String> strings = new HashSet<>();
        assertEquals(
            getModels().peek( m -> strings.add(m.getCRC(CHECK_BUFF.getBytes()).toString())).count()
           ,strings.stream().distinct().count()
           ,"All toString value for different CRCs has to be different");
    }

}