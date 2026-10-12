/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.CheckReturnValue;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static com.github.sftwnd.crayfish.common.crc.CrcModel.crc_general_combine;

/**
 * CRC Implementation
 *
 * @since 1.0.0
 * @author Andrey D. Shindarev
 */
public final class CRC {

    private final CrcModel model;
    @SuppressWarnings("java:S1700")
    long crc;
    // number of processed bytes
    private long length;

    /**
     * Construct CRC as copy of CRC
     * @param crc CRC value as base to create current object
     */
    CRC(@NonNull final CRC crc) {
        Objects.requireNonNull(crc, "CRC::new - crc is null");
        this.model = crc.model;
        this.crc = crc.crc;
        this.length = crc.length;
    }

    /**
     * Construct CRC for defined model with defined crc value and length.
     * The lookup table of the model is built if the model has not been used yet
     * @param model CRC model
     * @param crc crc value; bits above the CRC width are ignored
     * @param length length of calculated buffer
     */
    CRC(@NonNull final CrcModel model, long crc, long length) {
        if (length < 0) {
            throw new IllegalArgumentException("CRC::new - length less than 0");
        }
        this.model = Objects.requireNonNull(model, "CRC::new - model is null");
        this.model._init();
        // bits above the CRC width are ignored, for example the sign extension of a CRC-32 kept in an int
        this.crc = length == 0 ? this.model.init : crc & CrcModel.widmask(this.model.getWidth());
        this.length = length;
    }

    /**
     * Construct CRC for defined model
     * @param model CrcModel to use as base for current object
     */
    CRC(@NonNull final CrcModel model) {
        this(model, model.init, 0);
    }

    /**
     * Get the model of the CRC
     * @return CRC model
     */
    public @NonNull CrcModel getModel() {
        return this.model;
    }

    /**
     * Copy CRC
     * @return copy of current CRC
     */
    public @NonNull CRC copy() {
        return new CRC(this);
    }

    /**
     * Get current crc value: the crc of the bytes processed so far.
     * For a CRC without processed data, this is the crc of the empty message, not the init parameter of the model.
     * @return current crc value
     */
    public long getCrc() {
        return this.length == 0 ? model.init : this.crc;
    }

    /**
     * Get the number of processed bytes.
     * The number is a long since 1.1.0: up to 1.0.2 it was an int, and getLength() failed for 2 GiB and more
     * @return number of processed bytes
     */
    public long getLength() {
        return this.length;
    }

    /**
     * Update CRC by calculating value of buffer
     * @param buff buffer
     * @param offset start offset on buffer
     * @param len count of bytes to calculate
     * @return CRC with updated result (same as this)
     */
    public @NonNull CRC update(@Nullable byte[] buff, int offset, int len) {
        if (buff != null && len > 0) {
            if (this.length == 0) {
                this.crc = model.init;
            }
            this.crc = model.crcBytewise(this.crc, buff, offset, len);
            this.length += len;
        }
        return this;
    }

    /**
     * Update CRC by calculating buffer from start of it
     * @param buff buffer to calculate
     * @param len count of bytes to calculate
     * @return CRC with updated result (same as this)
     */
    public @NonNull CRC update(@Nullable byte[] buff, int len) {
        return update(buff, 0, len);
    }

    /**
     * Update CRC by calculating buffer from start of it to the end
     * @param buff buffer to calculate, null does not change the CRC
     * @return CRC with updated result (same as this)
     */
    public @NonNull CRC update(@Nullable byte[] buff) {
        return update(buff, 0, buff == null ? 0 : buff.length);
    }

    /**
     * Combine current crc value with crc value of the next buffer.
     * Unlike {@link #combine(CRC)}, this method does not change the current CRC: it only returns the combined value
     * @param crc2 crc value of the next buffer; bits above the CRC width are ignored,
     *             so a CRC-32 kept in an int can be passed as is
     * @param len2 length of the next buffer
     * @return combined crc value
     * @since 1.1.0
     */
    @CheckReturnValue
    public long combine(long crc2, long len2) {
        return combinedValue(crc2 & CrcModel.widmask(model.getWidth()), len2);
    }

    /**
     * Combine current crc value with crc value of the next buffer: the same as {@link #combine(long, long)},
     * kept for the binary compatibility with the versions up to 1.0.2
     * @param crc2 crc value of the next buffer; bits above the CRC width are ignored
     * @param len2 length of the next buffer
     * @return combined crc value
     */
    @CheckReturnValue
    public long combine(long crc2, int len2) {
        return combine(crc2, (long) len2);
    }

    private long combinedValue(long crc2, long len2) {
        return crc_general_combine(
                  getCrc(), crc2, len2,
                  model.getWidth(), model.getInit(), model.getPoly(), model.getXorot(), model.isRefot()
               );
    }

    /**
     * Combine current CRC with CRC on next buffer: the current CRC is updated and covers both buffers.
     * Both CRCs have to use the same CRC parameters, for example a model and its alias
     * @param crc CRC of the next buffer
     * @return current CRC, combined with CRC of parameter
     * @throws IllegalArgumentException if the CRC of the next buffer uses other CRC parameters
     */
    public @NonNull CRC combine(@Nullable CRC crc) {
        if (crc != null && !model.getCrcDescription().equals(crc.model.getCrcDescription())) {
            throw new IllegalArgumentException("CRC::combine - incompatible models: " + model + " and " + crc.model);
        }
        Optional.ofNullable(crc)
                .filter(c -> c.length > 0)
                .ifPresent(c -> {
                         this.crc = combinedValue(c.getCrc(), c.length);
                         this.length += c.length;
                 });
        return this;
    }

    /**
     * CRCs are equal if they have the same model, crc value and length
     * @param o object to compare with
     * @return true if o is a CRC with the same model, crc value and length
     */
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof CRC)) {
            return false;
        }
        // the fields are compared, not getCrc(): the crc field of a CRC without data holds the crc of the empty message
        // as well (see the constructor)
        final CRC other = (CRC) o;
        return this.crc == other.crc
            && this.length == other.length
            && this.model.equals(other.model);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.model, this.crc, this.length);
    }

    /**
     * String representation of CRC: the model, the crc value as unsigned hexadecimal number
     * with the number of digits of the CRC width, and the length
     * @return String value
     */
    @Override
    public String toString() {
        int digits = (model.getWidth() + 3) / 4;
        return this.getModel().toString()+"[crc:0x"+String.format(Locale.ROOT, "%0" + digits + "x", getCrc())+",len:"+this.length+"]";
    }
    
}
