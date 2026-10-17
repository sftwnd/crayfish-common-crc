/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.CheckReturnValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The CRC algorithms of the <a href="https://reveng.sourceforge.io/crc-catalogue/all.htm">RevEng catalogue of
 * parametrised CRC algorithms</a> (last updated 11 December 2024) with a width up to 64 bits: 112 algorithms with
 * 186 names. CRC-82/DARC of the catalogue is not included: a CRC value is a {@code long}.
 * <p>
 * A constant holds the data of the catalogue only: the names, the parameters, the check value and the residue.
 * The lookup tables are built by the {@link CrcDescription} of the parameters when a CRC is calculated, and the garbage
 * collector removes them when no model and no CRC of the algorithm is used any more.
 * <p>
 * The name of a constant is the name of the algorithm with every character other than {@code A-Z} and {@code 0-9}
 * replaced by {@code _}: {@code CRC-32/ISO-HDLC} is {@link #CRC_32_ISO_HDLC}.
 *
 * @since 2.0.0
 * @author Andrey D. Shindarev
 */
public enum StandardCrc {

    // CRC-3
    /** CRC-3/GSM: width=3 poly=0x3 init=0x0 refin=false refout=false xorout=0x7 check=0x4 residue=0x2 */
    CRC_3_GSM("CRC-3/GSM", 3, 0x3L, 0x0L, false, false, 0x7L, 0x4L, 0x2L),
    /** CRC-3/ROHC: width=3 poly=0x3 init=0x7 refin=true refout=true xorout=0x0 check=0x6 residue=0x0 */
    CRC_3_ROHC("CRC-3/ROHC", 3, 0x3L, 0x7L, true, true, 0x0L, 0x6L, 0x0L),

    // CRC-4
    /** CRC-4/G-704, alias CRC-4/ITU: width=4 poly=0x3 init=0x0 refin=true refout=true xorout=0x0 check=0x7 residue=0x0 */
    CRC_4_G_704("CRC-4/G-704", 4, 0x3L, 0x0L, true, true, 0x0L, 0x7L, 0x0L,
            "CRC-4/ITU"),
    /** CRC-4/INTERLAKEN: width=4 poly=0x3 init=0xf refin=false refout=false xorout=0xf check=0xb residue=0x2 */
    CRC_4_INTERLAKEN("CRC-4/INTERLAKEN", 4, 0x3L, 0xFL, false, false, 0xFL, 0xBL, 0x2L),

    // CRC-5
    /** CRC-5/EPC-C1G2, alias CRC-5/EPC: width=5 poly=0x09 init=0x09 refin=false refout=false xorout=0x00 check=0x00 residue=0x00 */
    CRC_5_EPC_C1G2("CRC-5/EPC-C1G2", 5, 0x09L, 0x09L, false, false, 0x00L, 0x00L, 0x00L,
            "CRC-5/EPC"),
    /** CRC-5/G-704, alias CRC-5/ITU: width=5 poly=0x15 init=0x00 refin=true refout=true xorout=0x00 check=0x07 residue=0x00 */
    CRC_5_G_704("CRC-5/G-704", 5, 0x15L, 0x00L, true, true, 0x00L, 0x07L, 0x00L,
            "CRC-5/ITU"),
    /** CRC-5/USB: width=5 poly=0x05 init=0x1f refin=true refout=true xorout=0x1f check=0x19 residue=0x06 */
    CRC_5_USB("CRC-5/USB", 5, 0x05L, 0x1FL, true, true, 0x1FL, 0x19L, 0x06L),

    // CRC-6
    /** CRC-6/CDMA2000-A: width=6 poly=0x27 init=0x3f refin=false refout=false xorout=0x00 check=0x0d residue=0x00 */
    CRC_6_CDMA2000_A("CRC-6/CDMA2000-A", 6, 0x27L, 0x3FL, false, false, 0x00L, 0x0DL, 0x00L),
    /** CRC-6/CDMA2000-B: width=6 poly=0x07 init=0x3f refin=false refout=false xorout=0x00 check=0x3b residue=0x00 */
    CRC_6_CDMA2000_B("CRC-6/CDMA2000-B", 6, 0x07L, 0x3FL, false, false, 0x00L, 0x3BL, 0x00L),
    /** CRC-6/DARC: width=6 poly=0x19 init=0x00 refin=true refout=true xorout=0x00 check=0x26 residue=0x00 */
    CRC_6_DARC("CRC-6/DARC", 6, 0x19L, 0x00L, true, true, 0x00L, 0x26L, 0x00L),
    /** CRC-6/G-704, alias CRC-6/ITU: width=6 poly=0x03 init=0x00 refin=true refout=true xorout=0x00 check=0x06 residue=0x00 */
    CRC_6_G_704("CRC-6/G-704", 6, 0x03L, 0x00L, true, true, 0x00L, 0x06L, 0x00L,
            "CRC-6/ITU"),
    /** CRC-6/GSM: width=6 poly=0x2f init=0x00 refin=false refout=false xorout=0x3f check=0x13 residue=0x3a */
    CRC_6_GSM("CRC-6/GSM", 6, 0x2FL, 0x00L, false, false, 0x3FL, 0x13L, 0x3AL),

    // CRC-7
    /** CRC-7/MMC, alias CRC-7: width=7 poly=0x09 init=0x00 refin=false refout=false xorout=0x00 check=0x75 residue=0x00 */
    CRC_7_MMC("CRC-7/MMC", 7, 0x09L, 0x00L, false, false, 0x00L, 0x75L, 0x00L,
            "CRC-7"),
    /** CRC-7/ROHC: width=7 poly=0x4f init=0x7f refin=true refout=true xorout=0x00 check=0x53 residue=0x00 */
    CRC_7_ROHC("CRC-7/ROHC", 7, 0x4FL, 0x7FL, true, true, 0x00L, 0x53L, 0x00L),
    /** CRC-7/UMTS: width=7 poly=0x45 init=0x00 refin=false refout=false xorout=0x00 check=0x61 residue=0x00 */
    CRC_7_UMTS("CRC-7/UMTS", 7, 0x45L, 0x00L, false, false, 0x00L, 0x61L, 0x00L),

    // CRC-8
    /** CRC-8/AUTOSAR: width=8 poly=0x2f init=0xff refin=false refout=false xorout=0xff check=0xdf residue=0x42 */
    CRC_8_AUTOSAR("CRC-8/AUTOSAR", 8, 0x2FL, 0xFFL, false, false, 0xFFL, 0xDFL, 0x42L),
    /** CRC-8/BLUETOOTH: width=8 poly=0xa7 init=0x00 refin=true refout=true xorout=0x00 check=0x26 residue=0x00 */
    CRC_8_BLUETOOTH("CRC-8/BLUETOOTH", 8, 0xA7L, 0x00L, true, true, 0x00L, 0x26L, 0x00L),
    /** CRC-8/CDMA2000: width=8 poly=0x9b init=0xff refin=false refout=false xorout=0x00 check=0xda residue=0x00 */
    CRC_8_CDMA2000("CRC-8/CDMA2000", 8, 0x9BL, 0xFFL, false, false, 0x00L, 0xDAL, 0x00L),
    /** CRC-8/DARC: width=8 poly=0x39 init=0x00 refin=true refout=true xorout=0x00 check=0x15 residue=0x00 */
    CRC_8_DARC("CRC-8/DARC", 8, 0x39L, 0x00L, true, true, 0x00L, 0x15L, 0x00L),
    /** CRC-8/DVB-S2: width=8 poly=0xd5 init=0x00 refin=false refout=false xorout=0x00 check=0xbc residue=0x00 */
    CRC_8_DVB_S2("CRC-8/DVB-S2", 8, 0xD5L, 0x00L, false, false, 0x00L, 0xBCL, 0x00L),
    /** CRC-8/GSM-A: width=8 poly=0x1d init=0x00 refin=false refout=false xorout=0x00 check=0x37 residue=0x00 */
    CRC_8_GSM_A("CRC-8/GSM-A", 8, 0x1DL, 0x00L, false, false, 0x00L, 0x37L, 0x00L),
    /** CRC-8/GSM-B: width=8 poly=0x49 init=0x00 refin=false refout=false xorout=0xff check=0x94 residue=0x53 */
    CRC_8_GSM_B("CRC-8/GSM-B", 8, 0x49L, 0x00L, false, false, 0xFFL, 0x94L, 0x53L),
    /** CRC-8/HITAG: width=8 poly=0x1d init=0xff refin=false refout=false xorout=0x00 check=0xb4 residue=0x00 */
    CRC_8_HITAG("CRC-8/HITAG", 8, 0x1DL, 0xFFL, false, false, 0x00L, 0xB4L, 0x00L),
    /** CRC-8/I-432-1, alias CRC-8/ITU: width=8 poly=0x07 init=0x00 refin=false refout=false xorout=0x55 check=0xa1 residue=0xac */
    CRC_8_I_432_1("CRC-8/I-432-1", 8, 0x07L, 0x00L, false, false, 0x55L, 0xA1L, 0xACL,
            "CRC-8/ITU"),
    /** CRC-8/I-CODE: width=8 poly=0x1d init=0xfd refin=false refout=false xorout=0x00 check=0x7e residue=0x00 */
    CRC_8_I_CODE("CRC-8/I-CODE", 8, 0x1DL, 0xFDL, false, false, 0x00L, 0x7EL, 0x00L),
    /** CRC-8/LTE: width=8 poly=0x9b init=0x00 refin=false refout=false xorout=0x00 check=0xea residue=0x00 */
    CRC_8_LTE("CRC-8/LTE", 8, 0x9BL, 0x00L, false, false, 0x00L, 0xEAL, 0x00L),
    /** CRC-8/MAXIM-DOW, aliases CRC-8/MAXIM, DOW-CRC: width=8 poly=0x31 init=0x00 refin=true refout=true xorout=0x00 check=0xa1 residue=0x00 */
    CRC_8_MAXIM_DOW("CRC-8/MAXIM-DOW", 8, 0x31L, 0x00L, true, true, 0x00L, 0xA1L, 0x00L,
            "CRC-8/MAXIM", "DOW-CRC"),
    /** CRC-8/MIFARE-MAD: width=8 poly=0x1d init=0xc7 refin=false refout=false xorout=0x00 check=0x99 residue=0x00 */
    CRC_8_MIFARE_MAD("CRC-8/MIFARE-MAD", 8, 0x1DL, 0xC7L, false, false, 0x00L, 0x99L, 0x00L),
    /** CRC-8/NRSC-5: width=8 poly=0x31 init=0xff refin=false refout=false xorout=0x00 check=0xf7 residue=0x00 */
    CRC_8_NRSC_5("CRC-8/NRSC-5", 8, 0x31L, 0xFFL, false, false, 0x00L, 0xF7L, 0x00L),
    /** CRC-8/OPENSAFETY: width=8 poly=0x2f init=0x00 refin=false refout=false xorout=0x00 check=0x3e residue=0x00 */
    CRC_8_OPENSAFETY("CRC-8/OPENSAFETY", 8, 0x2FL, 0x00L, false, false, 0x00L, 0x3EL, 0x00L),
    /** CRC-8/ROHC: width=8 poly=0x07 init=0xff refin=true refout=true xorout=0x00 check=0xd0 residue=0x00 */
    CRC_8_ROHC("CRC-8/ROHC", 8, 0x07L, 0xFFL, true, true, 0x00L, 0xD0L, 0x00L),
    /** CRC-8/SAE-J1850: width=8 poly=0x1d init=0xff refin=false refout=false xorout=0xff check=0x4b residue=0xc4 */
    CRC_8_SAE_J1850("CRC-8/SAE-J1850", 8, 0x1DL, 0xFFL, false, false, 0xFFL, 0x4BL, 0xC4L),
    /** CRC-8/SMBUS, alias CRC-8: width=8 poly=0x07 init=0x00 refin=false refout=false xorout=0x00 check=0xf4 residue=0x00 */
    CRC_8_SMBUS("CRC-8/SMBUS", 8, 0x07L, 0x00L, false, false, 0x00L, 0xF4L, 0x00L,
            "CRC-8"),
    /** CRC-8/TECH-3250, aliases CRC-8/AES, CRC-8/EBU: width=8 poly=0x1d init=0xff refin=true refout=true xorout=0x00 check=0x97 residue=0x00 */
    CRC_8_TECH_3250("CRC-8/TECH-3250", 8, 0x1DL, 0xFFL, true, true, 0x00L, 0x97L, 0x00L,
            "CRC-8/AES", "CRC-8/EBU"),
    /** CRC-8/WCDMA: width=8 poly=0x9b init=0x00 refin=true refout=true xorout=0x00 check=0x25 residue=0x00 */
    CRC_8_WCDMA("CRC-8/WCDMA", 8, 0x9BL, 0x00L, true, true, 0x00L, 0x25L, 0x00L),

    // CRC-10
    /** CRC-10/ATM, aliases CRC-10, CRC-10/I-610: width=10 poly=0x233 init=0x000 refin=false refout=false xorout=0x000 check=0x199 residue=0x000 */
    CRC_10_ATM("CRC-10/ATM", 10, 0x233L, 0x000L, false, false, 0x000L, 0x199L, 0x000L,
            "CRC-10", "CRC-10/I-610"),
    /** CRC-10/CDMA2000: width=10 poly=0x3d9 init=0x3ff refin=false refout=false xorout=0x000 check=0x233 residue=0x000 */
    CRC_10_CDMA2000("CRC-10/CDMA2000", 10, 0x3D9L, 0x3FFL, false, false, 0x000L, 0x233L, 0x000L),
    /** CRC-10/GSM: width=10 poly=0x175 init=0x000 refin=false refout=false xorout=0x3ff check=0x12a residue=0x0c6 */
    CRC_10_GSM("CRC-10/GSM", 10, 0x175L, 0x000L, false, false, 0x3FFL, 0x12AL, 0x0C6L),

    // CRC-11
    /** CRC-11/FLEXRAY, alias CRC-11: width=11 poly=0x385 init=0x01a refin=false refout=false xorout=0x000 check=0x5a3 residue=0x000 */
    CRC_11_FLEXRAY("CRC-11/FLEXRAY", 11, 0x385L, 0x01AL, false, false, 0x000L, 0x5A3L, 0x000L,
            "CRC-11"),
    /** CRC-11/UMTS: width=11 poly=0x307 init=0x000 refin=false refout=false xorout=0x000 check=0x061 residue=0x000 */
    CRC_11_UMTS("CRC-11/UMTS", 11, 0x307L, 0x000L, false, false, 0x000L, 0x061L, 0x000L),

    // CRC-12
    /** CRC-12/CDMA2000: width=12 poly=0xf13 init=0xfff refin=false refout=false xorout=0x000 check=0xd4d residue=0x000 */
    CRC_12_CDMA2000("CRC-12/CDMA2000", 12, 0xF13L, 0xFFFL, false, false, 0x000L, 0xD4DL, 0x000L),
    /** CRC-12/DECT, alias X-CRC-12: width=12 poly=0x80f init=0x000 refin=false refout=false xorout=0x000 check=0xf5b residue=0x000 */
    CRC_12_DECT("CRC-12/DECT", 12, 0x80FL, 0x000L, false, false, 0x000L, 0xF5BL, 0x000L,
            "X-CRC-12"),
    /** CRC-12/GSM: width=12 poly=0xd31 init=0x000 refin=false refout=false xorout=0xfff check=0xb34 residue=0x178 */
    CRC_12_GSM("CRC-12/GSM", 12, 0xD31L, 0x000L, false, false, 0xFFFL, 0xB34L, 0x178L),
    /** CRC-12/UMTS, alias CRC-12/3GPP: width=12 poly=0x80f init=0x000 refin=false refout=true xorout=0x000 check=0xdaf residue=0x000 */
    CRC_12_UMTS("CRC-12/UMTS", 12, 0x80FL, 0x000L, false, true, 0x000L, 0xDAFL, 0x000L,
            "CRC-12/3GPP"),

    // CRC-13
    /** CRC-13/BBC: width=13 poly=0x1cf5 init=0x0000 refin=false refout=false xorout=0x0000 check=0x04fa residue=0x0000 */
    CRC_13_BBC("CRC-13/BBC", 13, 0x1CF5L, 0x0000L, false, false, 0x0000L, 0x04FAL, 0x0000L),

    // CRC-14
    /** CRC-14/DARC: width=14 poly=0x0805 init=0x0000 refin=true refout=true xorout=0x0000 check=0x082d residue=0x0000 */
    CRC_14_DARC("CRC-14/DARC", 14, 0x0805L, 0x0000L, true, true, 0x0000L, 0x082DL, 0x0000L),
    /** CRC-14/GSM: width=14 poly=0x202d init=0x0000 refin=false refout=false xorout=0x3fff check=0x30ae residue=0x031e */
    CRC_14_GSM("CRC-14/GSM", 14, 0x202DL, 0x0000L, false, false, 0x3FFFL, 0x30AEL, 0x031EL),

    // CRC-15
    /** CRC-15/CAN, alias CRC-15: width=15 poly=0x4599 init=0x0000 refin=false refout=false xorout=0x0000 check=0x059e residue=0x0000 */
    CRC_15_CAN("CRC-15/CAN", 15, 0x4599L, 0x0000L, false, false, 0x0000L, 0x059EL, 0x0000L,
            "CRC-15"),
    /** CRC-15/MPT1327: width=15 poly=0x6815 init=0x0000 refin=false refout=false xorout=0x0001 check=0x2566 residue=0x6815 */
    CRC_15_MPT1327("CRC-15/MPT1327", 15, 0x6815L, 0x0000L, false, false, 0x0001L, 0x2566L, 0x6815L),

    // CRC-16
    /** CRC-16/ARC, aliases ARC, CRC-16, CRC-16/LHA, CRC-IBM: width=16 poly=0x8005 init=0x0000 refin=true refout=true xorout=0x0000 check=0xbb3d residue=0x0000 */
    CRC_16_ARC("CRC-16/ARC", 16, 0x8005L, 0x0000L, true, true, 0x0000L, 0xBB3DL, 0x0000L,
            "ARC", "CRC-16", "CRC-16/LHA", "CRC-IBM"),
    /** CRC-16/CDMA2000: width=16 poly=0xc867 init=0xffff refin=false refout=false xorout=0x0000 check=0x4c06 residue=0x0000 */
    CRC_16_CDMA2000("CRC-16/CDMA2000", 16, 0xC867L, 0xFFFFL, false, false, 0x0000L, 0x4C06L, 0x0000L),
    /** CRC-16/CMS: width=16 poly=0x8005 init=0xffff refin=false refout=false xorout=0x0000 check=0xaee7 residue=0x0000 */
    CRC_16_CMS("CRC-16/CMS", 16, 0x8005L, 0xFFFFL, false, false, 0x0000L, 0xAEE7L, 0x0000L),
    /** CRC-16/DDS-110: width=16 poly=0x8005 init=0x800d refin=false refout=false xorout=0x0000 check=0x9ecf residue=0x0000 */
    CRC_16_DDS_110("CRC-16/DDS-110", 16, 0x8005L, 0x800DL, false, false, 0x0000L, 0x9ECFL, 0x0000L),
    /** CRC-16/DECT-R, alias R-CRC-16: width=16 poly=0x0589 init=0x0000 refin=false refout=false xorout=0x0001 check=0x007e residue=0x0589 */
    CRC_16_DECT_R("CRC-16/DECT-R", 16, 0x0589L, 0x0000L, false, false, 0x0001L, 0x007EL, 0x0589L,
            "R-CRC-16"),
    /** CRC-16/DECT-X, alias X-CRC-16: width=16 poly=0x0589 init=0x0000 refin=false refout=false xorout=0x0000 check=0x007f residue=0x0000 */
    CRC_16_DECT_X("CRC-16/DECT-X", 16, 0x0589L, 0x0000L, false, false, 0x0000L, 0x007FL, 0x0000L,
            "X-CRC-16"),
    /** CRC-16/DNP: width=16 poly=0x3d65 init=0x0000 refin=true refout=true xorout=0xffff check=0xea82 residue=0x66c5 */
    CRC_16_DNP("CRC-16/DNP", 16, 0x3D65L, 0x0000L, true, true, 0xFFFFL, 0xEA82L, 0x66C5L),
    /** CRC-16/EN-13757: width=16 poly=0x3d65 init=0x0000 refin=false refout=false xorout=0xffff check=0xc2b7 residue=0xa366 */
    CRC_16_EN_13757("CRC-16/EN-13757", 16, 0x3D65L, 0x0000L, false, false, 0xFFFFL, 0xC2B7L, 0xA366L),
    /** CRC-16/GENIBUS, aliases CRC-16/DARC, CRC-16/EPC, CRC-16/EPC-C1G2, CRC-16/I-CODE: width=16 poly=0x1021 init=0xffff refin=false refout=false xorout=0xffff check=0xd64e residue=0x1d0f */
    CRC_16_GENIBUS("CRC-16/GENIBUS", 16, 0x1021L, 0xFFFFL, false, false, 0xFFFFL, 0xD64EL, 0x1D0FL,
            "CRC-16/DARC", "CRC-16/EPC", "CRC-16/EPC-C1G2", "CRC-16/I-CODE"),
    /** CRC-16/GSM: width=16 poly=0x1021 init=0x0000 refin=false refout=false xorout=0xffff check=0xce3c residue=0x1d0f */
    CRC_16_GSM("CRC-16/GSM", 16, 0x1021L, 0x0000L, false, false, 0xFFFFL, 0xCE3CL, 0x1D0FL),
    /** CRC-16/IBM-3740, aliases CRC-16/AUTOSAR, CRC-16/CCITT-FALSE: width=16 poly=0x1021 init=0xffff refin=false refout=false xorout=0x0000 check=0x29b1 residue=0x0000 */
    CRC_16_IBM_3740("CRC-16/IBM-3740", 16, 0x1021L, 0xFFFFL, false, false, 0x0000L, 0x29B1L, 0x0000L,
            "CRC-16/AUTOSAR", "CRC-16/CCITT-FALSE"),
    /** CRC-16/IBM-SDLC, aliases CRC-16/ISO-HDLC, CRC-16/ISO-IEC-14443-3-B, CRC-16/X-25, CRC-B, X-25: width=16 poly=0x1021 init=0xffff refin=true refout=true xorout=0xffff check=0x906e residue=0xf0b8 */
    CRC_16_IBM_SDLC("CRC-16/IBM-SDLC", 16, 0x1021L, 0xFFFFL, true, true, 0xFFFFL, 0x906EL, 0xF0B8L,
            "CRC-16/ISO-HDLC", "CRC-16/ISO-IEC-14443-3-B", "CRC-16/X-25", "CRC-B", "X-25"),
    /** CRC-16/ISO-IEC-14443-3-A, alias CRC-A: width=16 poly=0x1021 init=0xc6c6 refin=true refout=true xorout=0x0000 check=0xbf05 residue=0x0000 */
    CRC_16_ISO_IEC_14443_3_A("CRC-16/ISO-IEC-14443-3-A", 16, 0x1021L, 0xC6C6L, true, true, 0x0000L, 0xBF05L, 0x0000L,
            "CRC-A"),
    /** CRC-16/KERMIT, aliases CRC-16/BLUETOOTH, CRC-16/CCITT, CRC-16/CCITT-TRUE, CRC-16/V-41-LSB, CRC-CCITT, KERMIT: width=16 poly=0x1021 init=0x0000 refin=true refout=true xorout=0x0000 check=0x2189 residue=0x0000 */
    CRC_16_KERMIT("CRC-16/KERMIT", 16, 0x1021L, 0x0000L, true, true, 0x0000L, 0x2189L, 0x0000L,
            "CRC-16/BLUETOOTH", "CRC-16/CCITT", "CRC-16/CCITT-TRUE", "CRC-16/V-41-LSB", "CRC-CCITT", "KERMIT"),
    /** CRC-16/LJ1200: width=16 poly=0x6f63 init=0x0000 refin=false refout=false xorout=0x0000 check=0xbdf4 residue=0x0000 */
    CRC_16_LJ1200("CRC-16/LJ1200", 16, 0x6F63L, 0x0000L, false, false, 0x0000L, 0xBDF4L, 0x0000L),
    /** CRC-16/M17: width=16 poly=0x5935 init=0xffff refin=false refout=false xorout=0x0000 check=0x772b residue=0x0000 */
    CRC_16_M17("CRC-16/M17", 16, 0x5935L, 0xFFFFL, false, false, 0x0000L, 0x772BL, 0x0000L),
    /** CRC-16/MAXIM-DOW, alias CRC-16/MAXIM: width=16 poly=0x8005 init=0x0000 refin=true refout=true xorout=0xffff check=0x44c2 residue=0xb001 */
    CRC_16_MAXIM_DOW("CRC-16/MAXIM-DOW", 16, 0x8005L, 0x0000L, true, true, 0xFFFFL, 0x44C2L, 0xB001L,
            "CRC-16/MAXIM"),
    /** CRC-16/MCRF4XX: width=16 poly=0x1021 init=0xffff refin=true refout=true xorout=0x0000 check=0x6f91 residue=0x0000 */
    CRC_16_MCRF4XX("CRC-16/MCRF4XX", 16, 0x1021L, 0xFFFFL, true, true, 0x0000L, 0x6F91L, 0x0000L),
    /** CRC-16/MODBUS, alias MODBUS: width=16 poly=0x8005 init=0xffff refin=true refout=true xorout=0x0000 check=0x4b37 residue=0x0000 */
    CRC_16_MODBUS("CRC-16/MODBUS", 16, 0x8005L, 0xFFFFL, true, true, 0x0000L, 0x4B37L, 0x0000L,
            "MODBUS"),
    /** CRC-16/NRSC-5: width=16 poly=0x080b init=0xffff refin=true refout=true xorout=0x0000 check=0xa066 residue=0x0000 */
    CRC_16_NRSC_5("CRC-16/NRSC-5", 16, 0x080BL, 0xFFFFL, true, true, 0x0000L, 0xA066L, 0x0000L),
    /** CRC-16/OPENSAFETY-A: width=16 poly=0x5935 init=0x0000 refin=false refout=false xorout=0x0000 check=0x5d38 residue=0x0000 */
    CRC_16_OPENSAFETY_A("CRC-16/OPENSAFETY-A", 16, 0x5935L, 0x0000L, false, false, 0x0000L, 0x5D38L, 0x0000L),
    /** CRC-16/OPENSAFETY-B: width=16 poly=0x755b init=0x0000 refin=false refout=false xorout=0x0000 check=0x20fe residue=0x0000 */
    CRC_16_OPENSAFETY_B("CRC-16/OPENSAFETY-B", 16, 0x755BL, 0x0000L, false, false, 0x0000L, 0x20FEL, 0x0000L),
    /** CRC-16/PROFIBUS, alias CRC-16/IEC-61158-2: width=16 poly=0x1dcf init=0xffff refin=false refout=false xorout=0xffff check=0xa819 residue=0xe394 */
    CRC_16_PROFIBUS("CRC-16/PROFIBUS", 16, 0x1DCFL, 0xFFFFL, false, false, 0xFFFFL, 0xA819L, 0xE394L,
            "CRC-16/IEC-61158-2"),
    /** CRC-16/RIELLO: width=16 poly=0x1021 init=0xb2aa refin=true refout=true xorout=0x0000 check=0x63d0 residue=0x0000 */
    CRC_16_RIELLO("CRC-16/RIELLO", 16, 0x1021L, 0xB2AAL, true, true, 0x0000L, 0x63D0L, 0x0000L),
    /** CRC-16/SPI-FUJITSU, alias CRC-16/AUG-CCITT: width=16 poly=0x1021 init=0x1d0f refin=false refout=false xorout=0x0000 check=0xe5cc residue=0x0000 */
    CRC_16_SPI_FUJITSU("CRC-16/SPI-FUJITSU", 16, 0x1021L, 0x1D0FL, false, false, 0x0000L, 0xE5CCL, 0x0000L,
            "CRC-16/AUG-CCITT"),
    /** CRC-16/T10-DIF: width=16 poly=0x8bb7 init=0x0000 refin=false refout=false xorout=0x0000 check=0xd0db residue=0x0000 */
    CRC_16_T10_DIF("CRC-16/T10-DIF", 16, 0x8BB7L, 0x0000L, false, false, 0x0000L, 0xD0DBL, 0x0000L),
    /** CRC-16/TELEDISK: width=16 poly=0xa097 init=0x0000 refin=false refout=false xorout=0x0000 check=0x0fb3 residue=0x0000 */
    CRC_16_TELEDISK("CRC-16/TELEDISK", 16, 0xA097L, 0x0000L, false, false, 0x0000L, 0x0FB3L, 0x0000L),
    /** CRC-16/TMS37157: width=16 poly=0x1021 init=0x89ec refin=true refout=true xorout=0x0000 check=0x26b1 residue=0x0000 */
    CRC_16_TMS37157("CRC-16/TMS37157", 16, 0x1021L, 0x89ECL, true, true, 0x0000L, 0x26B1L, 0x0000L),
    /** CRC-16/UMTS, aliases CRC-16/BUYPASS, CRC-16/VERIFONE: width=16 poly=0x8005 init=0x0000 refin=false refout=false xorout=0x0000 check=0xfee8 residue=0x0000 */
    CRC_16_UMTS("CRC-16/UMTS", 16, 0x8005L, 0x0000L, false, false, 0x0000L, 0xFEE8L, 0x0000L,
            "CRC-16/BUYPASS", "CRC-16/VERIFONE"),
    /** CRC-16/USB: width=16 poly=0x8005 init=0xffff refin=true refout=true xorout=0xffff check=0xb4c8 residue=0xb001 */
    CRC_16_USB("CRC-16/USB", 16, 0x8005L, 0xFFFFL, true, true, 0xFFFFL, 0xB4C8L, 0xB001L),
    /** CRC-16/XMODEM, aliases CRC-16/ACORN, CRC-16/LTE, CRC-16/V-41-MSB, XMODEM, ZMODEM: width=16 poly=0x1021 init=0x0000 refin=false refout=false xorout=0x0000 check=0x31c3 residue=0x0000 */
    CRC_16_XMODEM("CRC-16/XMODEM", 16, 0x1021L, 0x0000L, false, false, 0x0000L, 0x31C3L, 0x0000L,
            "CRC-16/ACORN", "CRC-16/LTE", "CRC-16/V-41-MSB", "XMODEM", "ZMODEM"),

    // CRC-17
    /** CRC-17/CAN-FD: width=17 poly=0x1685b init=0x00000 refin=false refout=false xorout=0x00000 check=0x04f03 residue=0x00000 */
    CRC_17_CAN_FD("CRC-17/CAN-FD", 17, 0x1685BL, 0x00000L, false, false, 0x00000L, 0x04F03L, 0x00000L),

    // CRC-21
    /** CRC-21/CAN-FD: width=21 poly=0x102899 init=0x000000 refin=false refout=false xorout=0x000000 check=0x0ed841 residue=0x000000 */
    CRC_21_CAN_FD("CRC-21/CAN-FD", 21, 0x102899L, 0x000000L, false, false, 0x000000L, 0x0ED841L, 0x000000L),

    // CRC-24
    /** CRC-24/BLE: width=24 poly=0x00065b init=0x555555 refin=true refout=true xorout=0x000000 check=0xc25a56 residue=0x000000 */
    CRC_24_BLE("CRC-24/BLE", 24, 0x00065BL, 0x555555L, true, true, 0x000000L, 0xC25A56L, 0x000000L),
    /** CRC-24/FLEXRAY-A: width=24 poly=0x5d6dcb init=0xfedcba refin=false refout=false xorout=0x000000 check=0x7979bd residue=0x000000 */
    CRC_24_FLEXRAY_A("CRC-24/FLEXRAY-A", 24, 0x5D6DCBL, 0xFEDCBAL, false, false, 0x000000L, 0x7979BDL, 0x000000L),
    /** CRC-24/FLEXRAY-B: width=24 poly=0x5d6dcb init=0xabcdef refin=false refout=false xorout=0x000000 check=0x1f23b8 residue=0x000000 */
    CRC_24_FLEXRAY_B("CRC-24/FLEXRAY-B", 24, 0x5D6DCBL, 0xABCDEFL, false, false, 0x000000L, 0x1F23B8L, 0x000000L),
    /** CRC-24/INTERLAKEN: width=24 poly=0x328b63 init=0xffffff refin=false refout=false xorout=0xffffff check=0xb4f3e6 residue=0x144e63 */
    CRC_24_INTERLAKEN("CRC-24/INTERLAKEN", 24, 0x328B63L, 0xFFFFFFL, false, false, 0xFFFFFFL, 0xB4F3E6L, 0x144E63L),
    /** CRC-24/LTE-A: width=24 poly=0x864cfb init=0x000000 refin=false refout=false xorout=0x000000 check=0xcde703 residue=0x000000 */
    CRC_24_LTE_A("CRC-24/LTE-A", 24, 0x864CFBL, 0x000000L, false, false, 0x000000L, 0xCDE703L, 0x000000L),
    /** CRC-24/LTE-B: width=24 poly=0x800063 init=0x000000 refin=false refout=false xorout=0x000000 check=0x23ef52 residue=0x000000 */
    CRC_24_LTE_B("CRC-24/LTE-B", 24, 0x800063L, 0x000000L, false, false, 0x000000L, 0x23EF52L, 0x000000L),
    /** CRC-24/OPENPGP, alias CRC-24: width=24 poly=0x864cfb init=0xb704ce refin=false refout=false xorout=0x000000 check=0x21cf02 residue=0x000000 */
    CRC_24_OPENPGP("CRC-24/OPENPGP", 24, 0x864CFBL, 0xB704CEL, false, false, 0x000000L, 0x21CF02L, 0x000000L,
            "CRC-24"),
    /** CRC-24/OS-9: width=24 poly=0x800063 init=0xffffff refin=false refout=false xorout=0xffffff check=0x200fa5 residue=0x800fe3 */
    CRC_24_OS_9("CRC-24/OS-9", 24, 0x800063L, 0xFFFFFFL, false, false, 0xFFFFFFL, 0x200FA5L, 0x800FE3L),

    // CRC-30
    /** CRC-30/CDMA: width=30 poly=0x2030b9c7 init=0x3fffffff refin=false refout=false xorout=0x3fffffff check=0x04c34abf residue=0x34efa55a */
    CRC_30_CDMA("CRC-30/CDMA", 30, 0x2030B9C7L, 0x3FFFFFFFL, false, false, 0x3FFFFFFFL, 0x04C34ABFL, 0x34EFA55AL),

    // CRC-31
    /** CRC-31/PHILIPS: width=31 poly=0x04c11db7 init=0x7fffffff refin=false refout=false xorout=0x7fffffff check=0x0ce9e46c residue=0x4eaf26f1 */
    CRC_31_PHILIPS("CRC-31/PHILIPS", 31, 0x04C11DB7L, 0x7FFFFFFFL, false, false, 0x7FFFFFFFL, 0x0CE9E46CL, 0x4EAF26F1L),

    // CRC-32
    /** CRC-32/AIXM, alias CRC-32Q: width=32 poly=0x814141ab init=0x00000000 refin=false refout=false xorout=0x00000000 check=0x3010bf7f residue=0x00000000 */
    CRC_32_AIXM("CRC-32/AIXM", 32, 0x814141ABL, 0x00000000L, false, false, 0x00000000L, 0x3010BF7FL, 0x00000000L,
            "CRC-32Q"),
    /** CRC-32/AUTOSAR: width=32 poly=0xf4acfb13 init=0xffffffff refin=true refout=true xorout=0xffffffff check=0x1697d06a residue=0x904cddbf */
    CRC_32_AUTOSAR("CRC-32/AUTOSAR", 32, 0xF4ACFB13L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL, 0x1697D06AL, 0x904CDDBFL),
    /** CRC-32/BASE91-D, alias CRC-32D: width=32 poly=0xa833982b init=0xffffffff refin=true refout=true xorout=0xffffffff check=0x87315576 residue=0x45270551 */
    CRC_32_BASE91_D("CRC-32/BASE91-D", 32, 0xA833982BL, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL, 0x87315576L, 0x45270551L,
            "CRC-32D"),
    /** CRC-32/BZIP2, aliases CRC-32/AAL5, CRC-32/DECT-B, B-CRC-32: width=32 poly=0x04c11db7 init=0xffffffff refin=false refout=false xorout=0xffffffff check=0xfc891918 residue=0xc704dd7b */
    CRC_32_BZIP2("CRC-32/BZIP2", 32, 0x04C11DB7L, 0xFFFFFFFFL, false, false, 0xFFFFFFFFL, 0xFC891918L, 0xC704DD7BL,
            "CRC-32/AAL5", "CRC-32/DECT-B", "B-CRC-32"),
    /** CRC-32/CD-ROM-EDC: width=32 poly=0x8001801b init=0x00000000 refin=true refout=true xorout=0x00000000 check=0x6ec2edc4 residue=0x00000000 */
    CRC_32_CD_ROM_EDC("CRC-32/CD-ROM-EDC", 32, 0x8001801BL, 0x00000000L, true, true, 0x00000000L, 0x6EC2EDC4L, 0x00000000L),
    /** CRC-32/CKSUM, aliases CKSUM, CRC-32/POSIX: width=32 poly=0x04c11db7 init=0x00000000 refin=false refout=false xorout=0xffffffff check=0x765e7680 residue=0xc704dd7b */
    CRC_32_CKSUM("CRC-32/CKSUM", 32, 0x04C11DB7L, 0x00000000L, false, false, 0xFFFFFFFFL, 0x765E7680L, 0xC704DD7BL,
            "CKSUM", "CRC-32/POSIX"),
    /** CRC-32/ISCSI, aliases CRC-32/BASE91-C, CRC-32/CASTAGNOLI, CRC-32/INTERLAKEN, CRC-32C, CRC-32/NVME: width=32 poly=0x1edc6f41 init=0xffffffff refin=true refout=true xorout=0xffffffff check=0xe3069283 residue=0xb798b438 */
    CRC_32_ISCSI("CRC-32/ISCSI", 32, 0x1EDC6F41L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL, 0xE3069283L, 0xB798B438L,
            "CRC-32/BASE91-C", "CRC-32/CASTAGNOLI", "CRC-32/INTERLAKEN", "CRC-32C", "CRC-32/NVME"),
    /** CRC-32/ISO-HDLC, aliases CRC-32, CRC-32/ADCCP, CRC-32/V-42, CRC-32/XZ, PKZIP: width=32 poly=0x04c11db7 init=0xffffffff refin=true refout=true xorout=0xffffffff check=0xcbf43926 residue=0xdebb20e3 */
    CRC_32_ISO_HDLC("CRC-32/ISO-HDLC", 32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL, 0xCBF43926L, 0xDEBB20E3L,
            "CRC-32", "CRC-32/ADCCP", "CRC-32/V-42", "CRC-32/XZ", "PKZIP"),
    /** CRC-32/JAMCRC, alias JAMCRC: width=32 poly=0x04c11db7 init=0xffffffff refin=true refout=true xorout=0x00000000 check=0x340bc6d9 residue=0x00000000 */
    CRC_32_JAMCRC("CRC-32/JAMCRC", 32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0x00000000L, 0x340BC6D9L, 0x00000000L,
            "JAMCRC"),
    /** CRC-32/MEF: width=32 poly=0x741b8cd7 init=0xffffffff refin=true refout=true xorout=0x00000000 check=0xd2c22f51 residue=0x00000000 */
    CRC_32_MEF("CRC-32/MEF", 32, 0x741B8CD7L, 0xFFFFFFFFL, true, true, 0x00000000L, 0xD2C22F51L, 0x00000000L),
    /** CRC-32/MPEG-2: width=32 poly=0x04c11db7 init=0xffffffff refin=false refout=false xorout=0x00000000 check=0x0376e6e7 residue=0x00000000 */
    CRC_32_MPEG_2("CRC-32/MPEG-2", 32, 0x04C11DB7L, 0xFFFFFFFFL, false, false, 0x00000000L, 0x0376E6E7L, 0x00000000L),
    /** CRC-32/XFER, alias XFER: width=32 poly=0x000000af init=0x00000000 refin=false refout=false xorout=0x00000000 check=0xbd0be338 residue=0x00000000 */
    CRC_32_XFER("CRC-32/XFER", 32, 0x000000AFL, 0x00000000L, false, false, 0x00000000L, 0xBD0BE338L, 0x00000000L,
            "XFER"),

    // CRC-40
    /** CRC-40/GSM: width=40 poly=0x0004820009 init=0x0000000000 refin=false refout=false xorout=0xffffffffff check=0xd4164fc646 residue=0xc4ff8071ff */
    CRC_40_GSM("CRC-40/GSM", 40, 0x0004820009L, 0x0000000000L, false, false, 0xFFFFFFFFFFL, 0xD4164FC646L, 0xC4FF8071FFL),

    // CRC-64
    /** CRC-64/ECMA-182, alias CRC-64: width=64 poly=0x42f0e1eba9ea3693 init=0x0000000000000000 refin=false refout=false xorout=0x0000000000000000 check=0x6c40df5f0b497347 residue=0x0000000000000000 */
    CRC_64_ECMA_182("CRC-64/ECMA-182", 64, 0x42F0E1EBA9EA3693L, 0x0000000000000000L, false, false, 0x0000000000000000L, 0x6C40DF5F0B497347L, 0x0000000000000000L,
            "CRC-64"),
    /** CRC-64/GO-ISO: width=64 poly=0x000000000000001b init=0xffffffffffffffff refin=true refout=true xorout=0xffffffffffffffff check=0xb90956c775a41001 residue=0x5300000000000000 */
    CRC_64_GO_ISO("CRC-64/GO-ISO", 64, 0x000000000000001BL, 0xFFFFFFFFFFFFFFFFL, true, true, 0xFFFFFFFFFFFFFFFFL, 0xB90956C775A41001L, 0x5300000000000000L),
    /** CRC-64/MS: width=64 poly=0x259c84cba6426349 init=0xffffffffffffffff refin=true refout=true xorout=0x0000000000000000 check=0x75d4b74f024eceea residue=0x0000000000000000 */
    CRC_64_MS("CRC-64/MS", 64, 0x259C84CBA6426349L, 0xFFFFFFFFFFFFFFFFL, true, true, 0x0000000000000000L, 0x75D4B74F024ECEEAL, 0x0000000000000000L),
    /** CRC-64/NVME: width=64 poly=0xad93d23594c93659 init=0xffffffffffffffff refin=true refout=true xorout=0xffffffffffffffff check=0xae8b14860a799888 residue=0xf310303b2b6f6e42 */
    CRC_64_NVME("CRC-64/NVME", 64, 0xAD93D23594C93659L, 0xFFFFFFFFFFFFFFFFL, true, true, 0xFFFFFFFFFFFFFFFFL, 0xAE8B14860A799888L, 0xF310303B2B6F6E42L),
    /** CRC-64/REDIS: width=64 poly=0xad93d23594c935a9 init=0x0000000000000000 refin=true refout=true xorout=0x0000000000000000 check=0xe9c6d914c4b8d9ca residue=0x0000000000000000 */
    CRC_64_REDIS("CRC-64/REDIS", 64, 0xAD93D23594C935A9L, 0x0000000000000000L, true, true, 0x0000000000000000L, 0xE9C6D914C4B8D9CAL, 0x0000000000000000L),
    /** CRC-64/WE: width=64 poly=0x42f0e1eba9ea3693 init=0xffffffffffffffff refin=false refout=false xorout=0xffffffffffffffff check=0x62ec59e3f1a4f00a residue=0xfcacbebd5931a992 */
    CRC_64_WE("CRC-64/WE", 64, 0x42F0E1EBA9EA3693L, 0xFFFFFFFFFFFFFFFFL, false, false, 0xFFFFFFFFFFFFFFFFL, 0x62EC59E3F1A4F00AL, 0xFCACBEBD5931A992L),
    /** CRC-64/XZ, alias CRC-64/GO-ECMA: width=64 poly=0x42f0e1eba9ea3693 init=0xffffffffffffffff refin=true refout=true xorout=0xffffffffffffffff check=0x995dc9bbdf1939fa residue=0x49958c9abd7d353f */
    CRC_64_XZ("CRC-64/XZ", 64, 0x42F0E1EBA9EA3693L, 0xFFFFFFFFFFFFFFFFL, true, true, 0xFFFFFFFFFFFFFFFFL, 0x995DC9BBDF1939FAL, 0x49958C9ABD7D353FL,
            "CRC-64/GO-ECMA");

    private final String crcName;
    private final List<String> aliases;
    private final List<String> names;
    private final CrcParameters parameters;
    private final long check;
    private final long residue;

    // the arguments are the fields of the catalogue line, in its order
    @SuppressWarnings("java:S107")
    StandardCrc(final String crcName, final int width, final long poly, final long init, final boolean refin, final boolean refout,
                final long xorout, final long check, final long residue, final String... aliases) {
        this.crcName = crcName;
        this.parameters = CrcParameters.of(width, poly, init, refin, refout, xorout);
        this.check = check;
        this.residue = residue;
        this.aliases = Collections.unmodifiableList(Arrays.asList(aliases));
        List<String> all = new ArrayList<>(aliases.length + 1);
        all.add(crcName);
        all.addAll(this.aliases);
        this.names = Collections.unmodifiableList(all);
    }

    // the algorithm and the index of every name and alias, and the algorithm of every parameter set: the names
    // and the parameter sets of the catalogue are unique
    private static final Map<String, Name> BY_NAME = new HashMap<>();
    private static final Map<CrcParameters, StandardCrc> BY_PARAMETERS = new HashMap<>();

    static {
        for (StandardCrc standard : values()) {
            BY_PARAMETERS.put(standard.parameters, standard);
            for (int index = 0; index < standard.names.size(); index++) {
                BY_NAME.put(standard.names.get(index), new Name(standard, index));
            }
        }
    }

    /**
     * Get the name of the algorithm in the catalogue
     * @return name of the algorithm, for example {@code CRC-32/ISO-HDLC}
     */
    public @NonNull String crcName() {
        return this.crcName;
    }

    /**
     * Get the aliases of the algorithm, in the order of the catalogue
     * @return unmodifiable list of the aliases, empty if the algorithm has none
     */
    public @NonNull List<String> aliases() {
        return this.aliases;
    }

    /**
     * Get all names of the algorithm: the name, followed by the aliases
     * @return unmodifiable list of the names, the name of the algorithm first
     */
    public @NonNull List<String> names() {
        return this.names;
    }

    /**
     * Get the parameters of the algorithm
     * @return parameters of the algorithm
     */
    public @NonNull CrcParameters parameters() {
        return this.parameters;
    }

    /**
     * Get the check value of the catalogue: the CRC of the ASCII bytes of {@link CrcParameters#CHECK_INPUT}
     * @return check value
     */
    public long check() {
        return this.check;
    }

    /**
     * Get the residue of the catalogue: the register after an error-free codeword, see {@link CrcDescription#residue()}
     * @return residue
     * @since 2.1.0
     */
    public long residue() {
        return this.residue;
    }

    /**
     * Get the model of the algorithm with its name, the same as {@code CrcModel.of(crcName())}
     * @return model of the algorithm
     */
    public @NonNull CrcModel model() {
        return CrcModel.of(this.parameters);
    }

    /**
     * Find the algorithm by its name or by one of its aliases; the case of the name matters
     * @param name name or alias of the algorithm
     * @return the algorithm, empty if the catalogue has no algorithm with this name
     */
    @CheckReturnValue
    public static @NonNull Optional<StandardCrc> find(@NonNull final String name) {
        Name found = name(Objects.requireNonNull(name, "StandardCrc::find - name is null"));
        return found == null ? Optional.empty() : Optional.of(found.standard);
    }

    // the algorithm of a name with the index of the name in its names, null if the catalogue has no such name:
    // the model of the name is found without a search in the names
    static @Nullable Name name(@NonNull final String name) {
        return BY_NAME.get(name);
    }

    /**
     * Find the algorithm by its parameters
     * @param parameters parameters of the algorithm
     * @return the algorithm, empty if the catalogue has no algorithm with these parameters
     */
    @CheckReturnValue
    public static @NonNull Optional<StandardCrc> find(@NonNull final CrcParameters parameters) {
        return Optional.ofNullable(BY_PARAMETERS.get(Objects.requireNonNull(parameters, "StandardCrc::find - parameters is null")));
    }

    // a name of the catalogue: the algorithm and the index of the name in its names
    static final class Name {

        final StandardCrc standard;
        final int index;

        private Name(@NonNull final StandardCrc standard, final int index) {
            this.standard = standard;
            this.index = index;
        }

    }

}
