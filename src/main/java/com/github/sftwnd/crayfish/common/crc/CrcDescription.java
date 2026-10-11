/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Objects;

/**
 * CRC algorithm parameters holder
 */
@RequiredArgsConstructor
@EqualsAndHashCode
public class CrcDescription {

    @Getter protected final int     width;
    @Getter protected final long    poly;
    @Getter protected final long    init;
    @Getter protected final boolean refin;
    @Getter protected final boolean refot;
    @Getter protected final long    xorot;

    /**
     * Create description with the parameters of another description or model
     * @param model description or model to copy the parameters from
     */
    @SuppressWarnings("CopyConstructorMissesField")
    public CrcDescription(@NonNull final CrcDescription model) {
        this(Objects.requireNonNull(model, "CRC_model_d::new - model is null").getWidth(),
             model.getPoly(), model.getInit(), model.isRefin(), model.isRefot(), model.getXorot());
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
     * The CRC uses a model registered for the parameters: if no model is registered for them yet,
     * a new model is registered (see {@link CrcModel#construct(CrcDescription)}).
     * @return created CRC
     */
    public CRC getCRC() {
        return CrcModel.construct(this).getCRC();
    }

    /**
     * Create CRC for these parameters, continuing the calculation from a known crc value.
     * The model is looked up or registered as in {@link #getCRC()}.
     * @param crc initial crc value: crc of the bytes processed so far
     * @param len initial processed length: number of bytes processed so far
     * @return created CRC
     */
    public CRC getCRC(long crc, int len) {
        return CrcModel.construct(this).getCRC(crc, len);
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
