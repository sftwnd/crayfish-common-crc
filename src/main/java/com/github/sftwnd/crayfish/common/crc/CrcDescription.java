/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

/**
 * CRC algorithm parameters holder
 */
@EqualsAndHashCode
public class CrcDescription {

    @Getter protected final int     width;
    @Getter protected final long    poly;
    @Getter protected final long    init;
    @Getter protected final boolean refin;
    @Getter protected final boolean refot;
    @Getter protected final long    xorot;

    /**
     * Create description with the CRC parameters
     * @param width CRC width in bits: 1..64
     * @param poly polynomial, without the x^width term: for example 0x04C11DB7 for CRC-32, not 0x104C11DB7
     * @param init initial value of the CRC register
     * @param refin true if the input bytes are reflected
     * @param refot true if the CRC is reflected before the final XOR
     * @param xorot value XOR-ed to the CRC at the end
     * @throws IllegalArgumentException if the width is not in 1..64, or if poly, init or xorot do not fit into
     *         width bits (since 1.1.0)
     */
    public CrcDescription(final int width, final long poly, final long init, final boolean refin, final boolean refot, final long xorot) {
        if (width < 1 || width > 64) {
            throw new IllegalArgumentException("CRC_model_d::new - width has to be in 1..64: " + width);
        }
        long mask = CrcModel.widmask(width);
        checkFits("poly", poly, width, mask);
        checkFits("init", init, width, mask);
        checkFits("xorot", xorot, width, mask);
        this.width = width;
        this.poly = poly;
        this.init = init;
        this.refin = refin;
        this.refot = refot;
        this.xorot = xorot;
    }

    /**
     * Create description with the parameters of another description or model
     * @param model description or model to copy the parameters from
     */
    @SuppressWarnings("CopyConstructorMissesField")
    public CrcDescription(@NonNull final CrcDescription model) {
        this(Objects.requireNonNull(model, "CRC_model_d::new - model is null").getWidth(),
             model.getPoly(), model.getInit(), model.isRefin(), model.isRefot(), model.getXorot());
    }

    // a parameter with bits above the width gives wrong CRC values or fails later inside CrcModel
    private static void checkFits(final String parameter, final long value, final int width, final long mask) {
        if ((value & ~mask) != 0) {
            String hint;
            if ((value | mask) == -1L) {
                // all bits above the width are set: typically an int literal such as 0xFFFFFFFF, which is -1
                hint = " (an int literal is sign-extended: use a long literal such as 0x"
                     + Long.toHexString(value & mask).toUpperCase() + "L)";
            } else {
                hint = "poly".equals(parameter) ? " (poly without the x^" + width + " term)" : "";
            }
            throw new IllegalArgumentException("CRC_model_d::new - " + parameter + " 0x" + Long.toHexString(value).toUpperCase()
                    + " does not fit into the width " + width + hint);
        }
    }

    @Override
    public String toString() {
        return "CRC-" + getWidth()+"/P"+Long.toHexString(poly).toUpperCase()
             + "_I" +(init == 0 ? "0" : Long.toHexString(init).toUpperCase())
             + (refin ? "_RI" : "")
             + (refot ? "_RO" : "")
             + (xorot == 0 ? "" : "_X"+Long.toHexString(xorot).toUpperCase());
    }

    /**
     * Create CRC for these parameters.
     * The CRC uses the model registered for the parameters, as {@link CrcModel#construct(CrcDescription)} returns it:
     * for predefined parameters the primary predefined model. Since 1.1.0 no model is registered for parameters
     * without a registered model: the CRC uses a model that {@link CrcModel#getModels()} and
     * {@link CrcModel#lookUp(String)} do not know. To register the parameters, use
     * {@link CrcModel#construct(CrcDescription)}
     * @return created CRC
     */
    public CRC getCRC() {
        return CrcModel.modelFor(this).getCRC();
    }

    /**
     * Create CRC for these parameters, continuing the calculation from a known crc value.
     * The model is looked up as in {@link #getCRC()}.
     * @param crc initial crc value: crc of the bytes processed so far. Bits above the CRC width are ignored,
     *            so a CRC-32 kept in an int can be passed as is. If len is 0, crc is ignored:
     *            a CRC without processed data has the crc of the empty message
     * @param len initial processed length: number of bytes processed so far
     * @return created CRC
     * @since 1.1.0
     */
    public CRC getCRC(long crc, long len) {
        return CrcModel.modelFor(this).getCRC(crc, len);
    }

    /**
     * Create CRC for these parameters, continuing the calculation from a known crc value: the same as
     * {@link #getCRC(long, long)}, kept for the binary compatibility with the versions up to 1.0.2
     * @param crc initial crc value: crc of the bytes processed so far
     * @param len initial processed length: number of bytes processed so far
     * @return created CRC
     */
    public CRC getCRC(long crc, int len) {
        return getCRC(crc, (long) len);
    }

    @SuppressWarnings({
            // java:S116 Field names should comply with a naming convention
            // Names of original C algorithm methods has been saved
            "java:S116",
            // java:S3077 Non-primitive fields should not be "volatile"
            // table items are not mutable and use of table is controlled in CrcModel carefully
            "java:S3077"
    })
    // The lazily built lookup table is a cache, not a CRC parameter, so it is excluded from equals and hashCode
    @EqualsAndHashCode.Exclude
    protected volatile long[] table_byte = null;

}
