/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Objects;
import java.util.zip.Checksum;

/**
 * The {@link Checksum} of a {@link CrcModel}: the CRC of the bytes passed to it, for the code that works with
 * a {@code Checksum}, for example {@link java.util.zip.CheckedOutputStream} and {@link java.util.zip.CheckedInputStream}.
 * <pre>{@code
 * CheckedOutputStream out = new CheckedOutputStream(stream, model.checksum());
 * out.write(part1);
 * out.write(part2, 0, length2);
 * long value = out.getChecksum().getValue();   // the CRC value of all bytes written
 * }</pre>
 * Unlike a {@link CRC}, a checksum is mutable: an update changes it. It is not thread-safe, like
 * {@link java.util.zip.CRC32}. {@link #crc()} gives the immutable CRC of the processed data.
 *
 * @since 2.0.0
 * @author Andrey D. Shindarev
 */
public final class CrcChecksum implements Checksum {

    private final CrcModel model;
    private final CrcEngine engine;
    private long value;
    private long length;

    CrcChecksum(@NonNull final CrcModel model) {
        this.model = model;
        this.engine = model.engine();
        this.value = this.engine.empty();
    }

    /**
     * Update the checksum with a byte
     * @param b byte, the low 8 bits of the int
     * @throws ArithmeticException if the length of the data exceeds Long.MAX_VALUE
     */
    @Override
    public void update(final int b) {
        long updated = Math.addExact(this.length, 1);
        this.value = this.engine.update(this.value, (byte) b);
        this.length = updated;
    }

    /**
     * Update the checksum with the bytes of the buffer
     * @param data buffer
     * @throws ArithmeticException if the length of the data exceeds Long.MAX_VALUE
     */
    // a default method of Checksum on Java 9+ only: no @Override, the library is compiled for Java 8
    public void update(@NonNull final byte[] data) {
        Objects.requireNonNull(data, "CrcChecksum::update - data is null");
        update(data, 0, data.length);
    }

    /**
     * Update the checksum with a part of the buffer
     * @param data buffer
     * @param offset offset of the part in the buffer
     * @param length length of the part
     * @throws ArrayIndexOutOfBoundsException if the part is not inside the buffer
     * @throws ArithmeticException if the length of the data exceeds Long.MAX_VALUE
     */
    @Override
    public void update(@NonNull final byte[] data, final int offset, final int length) {
        Objects.requireNonNull(data, "CrcChecksum::update - data is null");
        if (offset < 0 || length < 0 || length > data.length - offset) {
            throw new ArrayIndexOutOfBoundsException("CrcChecksum::update - offset " + offset + " and length " + length
                    + " out of the buffer length " + data.length);
        }
        if (length > 0) {
            long updated = Math.addExact(this.length, length);
            this.value = this.engine.update(this.value, data, offset, length);
            this.length = updated;
        }
    }

    /**
     * Get the CRC value of the processed data
     * @return CRC value, the CRC of the empty data if no data is processed
     */
    @Override
    public long getValue() {
        return this.value;
    }

    /**
     * Reset the checksum to the checksum of the empty data
     */
    @Override
    public void reset() {
        this.value = this.engine.empty();
        this.length = 0;
    }

    /**
     * Get the model of the checksum
     * @return model of the checksum
     */
    public @NonNull CrcModel model() {
        return this.model;
    }

    /**
     * Get the length of the processed data
     * @return number of the processed bytes
     */
    public long length() {
        return this.length;
    }

    /**
     * Get the immutable CRC of the processed data, to combine it or to continue the calculation later;
     * the checksum does not change
     * @return CRC of the model with the value and the length of the processed data
     */
    public @NonNull CRC crc() {
        return this.model.crc(this.value, this.length);
    }

    /**
     * The name of the model, the CRC value as unsigned hexadecimal number with all digits of the width and the length,
     * as {@link CRC#toString()}, for example {@code CRC-32[crc:0xcbf43926,len:9]}
     * @return checksum as string
     */
    @Override
    public String toString() {
        return crc().toString();
    }

}
