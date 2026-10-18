/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.CheckReturnValue;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.util.Objects;

/**
 * The CRC of data: the model, the CRC value and the length of the data. A CRC is immutable: {@link #update(byte[])}
 * and {@link #combine(CRC)} return a new CRC, the CRC itself does not change, so it can be shared between threads
 * and kept as the state of a calculation.
 * <p>
 * A calculation starts with {@link CrcModel#crc()} (the CRC of the empty data) or with {@link CrcModel#crc(long, long)}
 * (the CRC of data processed before). The CRC has the name of its model: a CRC of a model chosen by an alias tells
 * the alias. CRCs are compared by the parameters, not by the names: CRCs with the same description, CRC value and
 * length are equal, whatever the names or the aliases their models were chosen by.
 *
 * @since 2.0.0
 * @author Andrey D. Shindarev
 */
public final class CRC {

    private static final String UPDATE_NULL_DATA = "CRC::update - data is null";
    private static final String BYTES_NULL_ORDER = "CRC::bytes - order is null";
    private static final String BYTES_NULL_DESTINATION = "CRC::bytes - destination is null";
    // the buffer that a stream or a channel is read into, a new one per call: the size of the buffers of
    // BufferedInputStream and of InputStream.transferTo of Java 9+
    private static final int STREAM_BUFFER = 8192;

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
     * Get the CRC value as bytes in the byte order given, as a protocol sends or stores the CRC after the data:
     * {@code (width + 7) / 8} bytes with the value in their low bits, the unused high bits of the most significant
     * byte 0. The check value {@code 0xdaf} of CRC-12/UMTS is {@code 0d af} big-endian, {@code af 0d} little-endian.
     * There is no default order: the order belongs to the protocol, for example CRC-16/MODBUS is sent least significant
     * byte first, CRC-16/XMODEM most significant byte first. Every call gives a new array;
     * {@link #bytes(byte[], int, ByteOrder)} and {@link #bytes(ByteBuffer, ByteOrder)} write the bytes into an array or
     * a buffer instead
     * @param order byte order: {@link ByteOrder#BIG_ENDIAN}, the most significant byte first, or
     *              {@link ByteOrder#LITTLE_ENDIAN}, the least significant byte first
     * @return CRC value in a new array of {@code (width + 7) / 8} bytes
     * @since 2.1.0
     */
    @CheckReturnValue
    public @NonNull byte[] bytes(@NonNull final ByteOrder order) {
        Objects.requireNonNull(order, BYTES_NULL_ORDER);
        byte[] bytes = new byte[byteCount()];
        encode(bytes, 0, order);
        return bytes;
    }

    /**
     * Write the CRC value as bytes in the byte order given into the array at the offset, without a new array: the bytes
     * of {@link #bytes(ByteOrder)}, for example after the data of a frame in its buffer. The other bytes of the array
     * do not change
     * @param destination array
     * @param offset offset of the bytes in the array
     * @param order byte order: {@link ByteOrder#BIG_ENDIAN}, the most significant byte first, or
     *              {@link ByteOrder#LITTLE_ENDIAN}, the least significant byte first
     * @return number of the bytes, {@code (width + 7) / 8}: the offset after them is the offset plus the number
     * @throws ArrayIndexOutOfBoundsException if the bytes are not inside the array; the array does not change then
     * @since 2.1.0
     */
    public int bytes(@NonNull final byte[] destination, final int offset, @NonNull final ByteOrder order) {
        Objects.requireNonNull(destination, BYTES_NULL_DESTINATION);
        Objects.requireNonNull(order, BYTES_NULL_ORDER);
        int count = byteCount();
        if (offset < 0 || count > destination.length - offset) {
            throw new ArrayIndexOutOfBoundsException("CRC::bytes - offset " + offset + " and length " + count
                    + " out of the buffer length " + destination.length);
        }
        encode(destination, offset, order);
        return count;
    }

    /**
     * Write the CRC value as bytes in the byte order given into the buffer at its position, without a new array: the
     * bytes of {@link #bytes(ByteOrder)}, heap and direct buffers. The position of the buffer is advanced by their
     * number, the other bytes of the buffer do not change. The byte order of the buffer is not used
     * @param destination buffer
     * @param order byte order: {@link ByteOrder#BIG_ENDIAN}, the most significant byte first, or
     *              {@link ByteOrder#LITTLE_ENDIAN}, the least significant byte first
     * @return number of the bytes, {@code (width + 7) / 8}
     * @throws ReadOnlyBufferException if the buffer is read-only; the buffer does not change then
     * @throws BufferOverflowException if fewer bytes remain in the buffer; the buffer does not change then
     * @since 2.1.0
     */
    public int bytes(@NonNull final ByteBuffer destination, @NonNull final ByteOrder order) {
        Objects.requireNonNull(destination, BYTES_NULL_DESTINATION);
        Objects.requireNonNull(order, BYTES_NULL_ORDER);
        if (destination.isReadOnly()) {
            throw new ReadOnlyBufferException();
        }
        int count = byteCount();
        if (destination.remaining() < count) {
            throw new BufferOverflowException();
        }
        int position = destination.position();
        int last = count - 1;
        boolean bigEndian = order == ByteOrder.BIG_ENDIAN;
        // the bytes of the value from the least significant one, as by encode, at their index in the buffer: the
        // order of the buffer is not used
        for (int i = 0; i <= last; i++) {
            destination.put(position + (bigEndian ? last - i : i), (byte) (this.value >>> (8 * i)));
        }
        destination.position(position + count);
        return count;
    }

    // the number of the bytes of the CRC value: the bits of the width in whole bytes
    private int byteCount() {
        return (this.model.parameters().width() + 7) / 8;
    }

    // the bytes of the CRC value in the array at the offset, inside the array. The bytes of the value from the least
    // significant one; the value has no bits above the width, so the unused high bits of the most significant byte are
    // 0 without a mask
    private void encode(@NonNull final byte[] destination, final int offset, @NonNull final ByteOrder order) {
        int last = byteCount() - 1;
        boolean bigEndian = order == ByteOrder.BIG_ENDIAN;
        for (int i = 0; i <= last; i++) {
            destination[offset + (bigEndian ? last - i : i)] = (byte) (this.value >>> (8 * i));
        }
    }

    /**
     * Calculate the CRC of the data of this CRC followed by the bytes of the buffer
     * @param data buffer
     * @return new CRC, or this CRC if the buffer is empty
     * @throws ArithmeticException if the length exceeds Long.MAX_VALUE
     */
    @CheckReturnValue
    public @NonNull CRC update(@NonNull final byte[] data) {
        Objects.requireNonNull(data, UPDATE_NULL_DATA);
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
        Objects.requireNonNull(data, UPDATE_NULL_DATA);
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
     * Calculate the CRC of the data of this CRC followed by the remaining bytes of the buffer, from its position
     * to its limit: heap, direct and read-only buffers. The position of the buffer is advanced to its limit, as by
     * {@code java.util.zip.Checksum.update(ByteBuffer)}
     * @param data buffer
     * @return new CRC, or this CRC if no bytes remain in the buffer
     * @throws ArithmeticException if the length of the data exceeds Long.MAX_VALUE; the buffer is not read then
     */
    @CheckReturnValue
    public @NonNull CRC update(@NonNull final ByteBuffer data) {
        Objects.requireNonNull(data, UPDATE_NULL_DATA);
        int remaining = data.remaining();
        if (remaining == 0) {
            return this;
        }
        long total = Math.addExact(this.length, remaining);
        return new CRC(this.model, this.model.engine().update(this.value, data), total);
    }

    /**
     * Calculate the CRC of the data of this CRC followed by the bytes of the stream, read to its end: until
     * {@code read} returns -1. The stream is read into a buffer of 8192 bytes and is not closed
     * @param in stream
     * @return new CRC, or this CRC if no bytes are read
     * @throws IOException if the stream throws it
     * @throws ArithmeticException if the length of the data exceeds Long.MAX_VALUE
     * @since 2.1.0
     */
    @CheckReturnValue
    public @NonNull CRC update(@NonNull final InputStream in) throws IOException {
        Objects.requireNonNull(in, "CRC::update - in is null");
        byte[] buffer = new byte[STREAM_BUFFER];
        CRC crc = this;
        // every part read is calculated as by update(byte[]), so a model of the JDK gives the parts to the class of
        // the JDK; a read of 0 bytes, which the contract of InputStream.read does not allow, gives the same CRC
        for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
            crc = crc.update(buffer, 0, read);
        }
        return crc;
    }

    /**
     * Calculate the CRC of the data of this CRC followed by the bytes of the channel, read to its end: until
     * {@code read} returns -1. The channel is read into a buffer of 8192 bytes and is not closed
     * @param channel channel
     * @return new CRC, or this CRC if no bytes are read
     * @throws IOException if the channel throws it
     * @throws IllegalBlockingModeException if the channel is a {@link SelectableChannel} in non-blocking mode, which
     *         can read 0 bytes for ever; the channel is not read then
     * @throws ArithmeticException if the length of the data exceeds Long.MAX_VALUE
     * @since 2.1.0
     */
    @CheckReturnValue
    public @NonNull CRC update(@NonNull final ReadableByteChannel channel) throws IOException {
        Objects.requireNonNull(channel, "CRC::update - channel is null");
        // rejected before a read, as by Channels.newInputStream
        if (channel instanceof SelectableChannel && !((SelectableChannel) channel).isBlocking()) {
            throw new IllegalBlockingModeException();
        }
        byte[] array = new byte[STREAM_BUFFER];
        ByteBuffer buffer = ByteBuffer.wrap(array);
        CRC crc = this;
        // the parts read are calculated in the array, as by update(InputStream); a read of 0 bytes, which a channel
        // can return, gives the same CRC
        for (int read = channel.read(buffer); read >= 0; read = channel.read(buffer)) {
            crc = crc.update(array, 0, read);
            buffer.clear();
        }
        return crc;
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
     * Get a new {@link java.util.zip.Checksum} that continues the calculation of this CRC, for
     * {@link java.util.zip.CheckedOutputStream}, {@link java.util.zip.CheckedInputStream} and other code that works
     * with a {@code Checksum}: the checksum of the model of this CRC with its CRC value and its length, the counterpart
     * of {@link CrcChecksum#crc()}. The CRC does not change. {@link CrcChecksum#reset()} gives the checksum of the empty
     * data, as for every checksum, not this CRC
     * @return new mutable checksum with the model, the CRC value and the length of this CRC
     * @since 2.1.0
     */
    @CheckReturnValue
    public @NonNull CrcChecksum checksum() {
        return new CrcChecksum(this.model, this.value, this.length);
    }

    /**
     * CRCs are equal if they have the same description, CRC value and length, whatever the names or the aliases their
     * models were chosen by: the CRCs of the same data by {@code CRC-32} and by {@code PKZIP} are equal (since 2.1.0;
     * before, the CRCs of the models of other names were not equal)
     * @param o object to compare with
     * @return true if o is a CRC with the same description, value and length
     */
    // @CheckForNull, as SpotBugs takes the parameter of Object.equals: @Nullable would tighten it
    @Override
    public boolean equals(@CheckForNull final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof CRC)) {
            return false;
        }
        final CRC other = (CRC) o;
        // one description per parameter set, kept by the CRC: the CRCs of the same parameters have the same description
        return this.model.description() == other.model.description()
            && this.value == other.value
            && this.length == other.length;
    }

    // of the description, not of the model: equal CRCs of the models of other names have the same hash code
    @Override
    public int hashCode() {
        int result = this.model.description().hashCode();
        result = 31 * result + Long.hashCode(this.value);
        return 31 * result + Long.hashCode(this.length);
    }

    /**
     * The name, the CRC value as unsigned hexadecimal number with all digits of the width and the length,
     * for example {@code CRC-32[crc:0xcbf43926,len:9]}
     * @return CRC as string
     */
    @Override
    public @NonNull String toString() {
        return name() + "[crc:" + CrcParameters.hex(this.value, this.model.parameters().width()) + ",len:" + this.length + ']';
    }

}
