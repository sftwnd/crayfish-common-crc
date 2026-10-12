/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;

/**
 * CRC algorithm parameters holder
 */
public class CrcDescription {

    /** CRC width in bits: 1..64 */
    protected final int     width;
    /**
     * Polynomial, without the x^width term. In a {@link CrcModel} the polynomial processed for the calculation:
     * reflected if refin is true; {@link #getPoly()} returns the parameter
     */
    protected final long    poly;
    /**
     * Initial value of the CRC register. In a {@link CrcModel} the value processed for the calculation:
     * reflected if refout is true, and XOR-ed with xorout; {@link #getInit()} returns the parameter
     */
    protected final long    init;
    /** True if the input bytes are reflected (refin) */
    protected final boolean refin;
    /**
     * True if the CRC is reflected before the final XOR (refout). In a {@link CrcModel} the value processed for
     * the calculation: refout XOR refin; {@link #isRefot()} returns the parameter
     */
    protected final boolean refot;
    /** Value XOR-ed to the CRC at the end (xorout) */
    protected final long    xorot;

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

    // the parameters of a checked description, processed for the calculation by CrcModel: the reflected values
    // and the XOR of init with xorot keep the width, so they are not checked again
    CrcDescription(@NonNull final CrcDescription model, final long poly, final long init, final boolean refot) {
        this.width = model.width;
        this.poly = poly;
        this.init = init;
        this.refin = model.refin;
        this.refot = refot;
        this.xorot = model.xorot;
    }

    // a parameter with bits above the width gives wrong CRC values or fails later inside CrcModel
    private static void checkFits(@NonNull final String parameter, final long value, final int width, final long mask) {
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

    /**
     * Get the CRC width
     * @return CRC width in bits: 1..64
     */
    public int getWidth() {
        return this.width;
    }

    /**
     * Get the polynomial
     * @return polynomial, without the x^width term
     */
    public long getPoly() {
        return this.poly;
    }

    /**
     * Get the initial value of the CRC register
     * @return initial value of the CRC register
     */
    public long getInit() {
        return this.init;
    }

    /**
     * Whether the input bytes are reflected
     * @return true if the input bytes are reflected
     */
    public boolean isRefin() {
        return this.refin;
    }

    /**
     * Whether the CRC is reflected before the final XOR
     * @return true if the CRC is reflected before the final XOR
     */
    public boolean isRefot() {
        return this.refot;
    }

    /**
     * Get the value XOR-ed to the CRC at the end
     * @return value XOR-ed to the CRC at the end
     */
    public long getXorot() {
        return this.xorot;
    }

    /**
     * Descriptions are equal if they have the same CRC parameters.
     * The parameters are compared with the getters, so a {@link CrcModel} is compared by its original parameters,
     * and the lookup table, a cache, is not compared
     * @param o object to compare with
     * @return true if o is a description with the same parameters
     */
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof CrcDescription)) {
            return false;
        }
        final CrcDescription other = (CrcDescription) o;
        return other.canEqual(this)
            && this.getWidth() == other.getWidth()
            && this.getPoly() == other.getPoly()
            && this.getInit() == other.getInit()
            && this.isRefin() == other.isRefin()
            && this.isRefot() == other.isRefot()
            && this.getXorot() == other.getXorot();
    }

    /**
     * Whether the other object can be equal to this one: a subclass with additional state overrides it,
     * so that equals() stays symmetric
     * @param other object to compare with
     * @return true if other is a CrcDescription
     */
    protected boolean canEqual(final Object other) {
        return other instanceof CrcDescription;
    }

    // not Objects.hash(): it is called on every getCRC() (see CrcModel.modelFor()) and would box the parameters
    @Override
    public int hashCode() {
        int result = Integer.hashCode(this.getWidth());
        result = 31 * result + Long.hashCode(this.getPoly());
        result = 31 * result + Long.hashCode(this.getInit());
        result = 31 * result + Boolean.hashCode(this.isRefin());
        result = 31 * result + Boolean.hashCode(this.isRefot());
        result = 31 * result + Long.hashCode(this.getXorot());
        return result;
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
    public @NonNull CRC getCRC() {
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
    public @NonNull CRC getCRC(long crc, long len) {
        return CrcModel.modelFor(this).getCRC(crc, len);
    }

    /**
     * Create CRC for these parameters, continuing the calculation from a known crc value: the same as
     * {@link #getCRC(long, long)}, kept for the binary compatibility with the versions up to 1.0.2
     * @param crc initial crc value: crc of the bytes processed so far
     * @param len initial processed length: number of bytes processed so far
     * @return created CRC
     */
    public @NonNull CRC getCRC(long crc, int len) {
        return getCRC(crc, (long) len);
    }

    /**
     * Lookup table of the calculation, built by {@link CrcModel} on first use and kept in the model and in its
     * original description, so that the aliases of a model share it: since 1.1.0 the first of the 8 tables of
     * the calculation, 8 bytes per step (slicing-by-8). An internal cache, not a CRC parameter: it takes no part
     * in equals and hashCode, and it is not meant to be used outside this library
     */
    @SuppressWarnings({
            // java:S116 Field names should comply with a naming convention
            // Names of original C algorithm methods has been saved
            "java:S116",
            // java:S3077 Non-primitive fields should not be "volatile"
            // table items are not mutable and use of table is controlled in CrcModel carefully
            "java:S3077"
    })
    protected volatile long[] table_byte = null;

    // the 8 lookup tables of the calculation, table_byte is the first one: built and published like table_byte
    @SuppressWarnings("java:S3077")
    volatile long[][] tables = null;

}
