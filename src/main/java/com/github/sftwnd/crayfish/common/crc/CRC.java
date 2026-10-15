/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.CheckReturnValue;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;

/**
 * The CRC of data: the model, the CRC value and the length of the data. A CRC is immutable: {@link #update(byte[])}
 * and {@link #combine(CRC)} return a new CRC, the CRC itself does not change, so it can be shared between threads
 * and kept as the state of a calculation.
 * <p>
 * A calculation starts with {@link CrcModel#crc()} (the CRC of the empty data) or with {@link CrcModel#crc(long, long)}
 * (the CRC of data processed before). The CRC has the name of its model: a CRC of a model chosen by an alias tells
 * the alias.
 *
 * @since 2.0.0
 * @author Andrey D. Shindarev
 */
public final class CRC {

    private final CrcModel model;
    private final long value;
    private final long length;

    CRC(@NonNull final CrcModel model, final long value, final long length) {
        this.model = model;
        this.value = value;
        this.length = length;
    }

    /**
     * Get the model of the CRC
     * @return model of the CRC
     */
    public @NonNull CrcModel model() {
        return this.model;
    }

    /**
     * Get the name of the CRC: the name of its model, the name or the alias the model was chosen by
     * @return name of the CRC
     */
    public @NonNull String name() {
        return this.model.name();
    }

    /**
     * Get the CRC value of the data
     * @return CRC value, the CRC of the empty data if no data is processed
     */
    public long value() {
        return this.value;
    }

    /**
     * Get the length of the data
     * @return number of the processed bytes
     */
    public long length() {
        return this.length;
    }

    /**
     * Calculate the CRC of the data of this CRC followed by the bytes of the buffer
     * @param data buffer
     * @return new CRC, or this CRC if the buffer is empty
     * @throws ArithmeticException if the length exceeds Long.MAX_VALUE
     */
    @CheckReturnValue
    public @NonNull CRC update(@NonNull final byte[] data) {
        Objects.requireNonNull(data, "CRC::update - data is null");
        return update(data, 0, data.length);
    }

    /**
     * Calculate the CRC of the data of this CRC followed by the first bytes of the buffer: {@code update(data, 0, length)}
     * @param data buffer
     * @param length number of the first bytes of the buffer
     * @return new CRC, or this CRC if the length is 0
     * @throws ArrayIndexOutOfBoundsException if the length is negative or exceeds the length of the buffer
     * @throws ArithmeticException if the length of the data exceeds Long.MAX_VALUE
     */
    @CheckReturnValue
    public @NonNull CRC update(@NonNull final byte[] data, final int length) {
        return update(data, 0, length);
    }

    /**
     * Calculate the CRC of the data of this CRC followed by a part of the buffer
     * @param data buffer
     * @param offset offset of the part in the buffer
     * @param length length of the part
     * @return new CRC, or this CRC if the length is 0
     * @throws ArrayIndexOutOfBoundsException if the part is not inside the buffer
     * @throws ArithmeticException if the length of the data exceeds Long.MAX_VALUE
     */
    @CheckReturnValue
    public @NonNull CRC update(@NonNull final byte[] data, final int offset, final int length) {
        Objects.requireNonNull(data, "CRC::update - data is null");
        if (offset < 0 || length < 0 || length > data.length - offset) {
            throw new ArrayIndexOutOfBoundsException("CRC::update - offset " + offset + " and length " + length
                    + " out of the buffer length " + data.length);
        }
        if (length == 0) {
            return this;
        }
        return new CRC(this.model, this.model.engine().update(this.value, data, offset, length), Math.addExact(this.length, length));
    }

    /**
     * Calculate the CRC of the data of this CRC followed by the data of the next CRC, calculated independently,
     * for example in another thread. Both CRCs have to have the same description: the same model, or a model
     * and its alias
     * @param next CRC of the next data
     * @return new CRC with the model of this CRC, or this CRC if the next data is empty
     * @throws IllegalArgumentException if the next CRC has other parameters
     * @throws ArithmeticException if the length of the data exceeds Long.MAX_VALUE
     */
    @CheckReturnValue
    public @NonNull CRC combine(@NonNull final CRC next) {
        Objects.requireNonNull(next, "CRC::combine - next is null");
        if (next.model.description() != this.model.description()) {
            throw new IllegalArgumentException("CRC::combine - incompatible models: " + this.model.description()
                    + " and " + next.model.description());
        }
        return combine(next.value, next.length);
    }

    /**
     * Calculate the CRC of the data of this CRC followed by the next data, of which only the CRC value and the length
     * are known
     * @param value CRC value of the next data. Bits above the CRC width are ignored, so that a CRC-32 kept in an int
     *              can be passed as is
     * @param length length of the next data
     * @return new CRC with the model of this CRC, or this CRC if the length is 0
     * @throws IllegalArgumentException if the length is negative
     * @throws ArithmeticException if the length of the data exceeds Long.MAX_VALUE
     */
    @CheckReturnValue
    public @NonNull CRC combine(final long value, final long length) {
        if (length < 0) {
            throw new IllegalArgumentException("CRC::combine - length is negative: " + length);
        }
        if (length == 0) {
            return this;
        }
        CrcEngine engine = this.model.engine();
        long next = value & engine.mask();
        // the CRC of the empty data followed by the next data is the CRC of the next data
        long combined = this.length == 0 ? next : engine.combine(this.value, next, length);
        return new CRC(this.model, combined, Math.addExact(this.length, length));
    }

    /**
     * CRCs are equal if they have the same model, value and length
     * @param o object to compare with
     * @return true if o is a CRC with the same model, value and length
     */
    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof CRC)) {
            return false;
        }
        final CRC other = (CRC) o;
        return this.model == other.model
            && this.value == other.value
            && this.length == other.length;
    }

    @Override
    public int hashCode() {
        int result = this.model.hashCode();
        result = 31 * result + Long.hashCode(this.value);
        return 31 * result + Long.hashCode(this.length);
    }

    /**
     * The name, the CRC value as unsigned hexadecimal number with all digits of the width and the length,
     * for example {@code CRC-32[crc:0xcbf43926,len:9]}
     * @return CRC as string
     */
    @Override
    public String toString() {
        return name() + "[crc:" + CrcParameters.hex(this.value, this.model.parameters().width()) + ",len:" + this.length + ']';
    }

}
