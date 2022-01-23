package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static com.github.sftwnd.crayfish.common.crc.CrcModel.getModels;
import static org.junit.jupiter.api.Assertions.*;

class CrcDescriptionTest {

    @Test
    void testGetCRC() {
        byte[] buff = "123456789".getBytes();
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
                    assertEquals(crcVal, Optional.ofNullable(crc).map(CRC::getCrc).orElse(null), "Wrong crc in the CRC, created by getCRC(crc, len)");
                    assertEquals(lenVal, Optional.ofNullable(crc).map(CRC::getLength).orElse(null), "Wrong length in the CRC, created by getCRC(crc, len)");
                }
        );
    }

    @Test
    void testToString() {
        getModels().map(CrcModel::getCrcDescription).forEach(description -> assertDoesNotThrow(description::toString, "CrcDescription::toString hasn't got to throws exception"));
    }

}