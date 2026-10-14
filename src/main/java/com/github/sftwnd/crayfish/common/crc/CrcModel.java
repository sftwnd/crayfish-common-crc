/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A CRC model: the {@link CrcDescription} of a parameter set with one of its names, the name the model was chosen by.
 * The CRCs of a model have its name, so a CRC of a model chosen by an alias tells the alias.
 * <p>
 * A model is chosen by a name ({@link #of(String)}), by the parameters ({@link #of(CrcParameters)}) or with a builder
 * ({@link #builder(int)}). There is one model per description and name: models are compared by identity.
 * A model keeps its description, and so the lookup tables of the calculation, from being collected.
 * Models are immutable and thread-safe.
 *
 * @since 2.0.0
 * @author Andrey D. Shindarev
 */
public final class CrcModel {

    private final CrcDescription description;
    // the index of the name in the names of the description
    private final int index;
    // the calculation of the description, kept by the model for a short way from a CRC
    private final CrcEngine engine;
    // the CRC of the empty data
    private final CRC empty;

    // created by the description, one model per name
    CrcModel(@NonNull final CrcDescription description, final int index) {
        this.description = description;
        this.index = index;
        this.engine = description.engine();
        this.empty = new CRC(this, this.engine.empty(), 0);
    }

    /**
     * Get the model by a name: the name or an alias of an algorithm of the catalogue (see {@link StandardCrc}),
     * or the name of a description made of the parameters (see {@link CrcDescription}). The case of the name matters
     * @param name name of the model
     * @return model with the name; for a name made of the parameters of an algorithm of the catalogue, the model with
     *         the name of the algorithm
     * @throws IllegalArgumentException if the name is unknown
     */
    public static @NonNull CrcModel of(@NonNull final String name) {
        return find(name).orElseThrow(() -> new IllegalArgumentException("CrcModel::of - unknown CRC name: " + name));
    }

    /**
     * Find the model by a name, see {@link #of(String)}
     * @param name name of the model
     * @return model with the name, empty if the name is unknown
     */
    public static @NonNull Optional<CrcModel> find(@NonNull final String name) {
        Objects.requireNonNull(name, "CrcModel::find - name is null");
        Optional<StandardCrc> standard = StandardCrc.find(name);
        if (standard.isPresent()) {
            return Optional.of(CrcDescription.of(standard.get().parameters()).model(name));
        }
        CrcParameters parameters = CrcParameters.parse(name);
        return parameters == null ? Optional.empty() : Optional.of(CrcDescription.of(parameters).modelOf(name));
    }

    /**
     * Get the model of the parameters, with the name of their description
     * @param parameters CRC parameters
     * @return model of the parameters: for the parameters of an algorithm of the catalogue, the model with its name
     */
    public static @NonNull CrcModel of(@NonNull final CrcParameters parameters) {
        return CrcDescription.of(Objects.requireNonNull(parameters, "CrcModel::of - parameters is null")).model();
    }

    /**
     * Get the model of the parameters, see {@link #of(CrcParameters)} and {@link CrcParameters#of}
     * @param width CRC width in bits: 1..64
     * @param poly polynomial, without the x^width term
     * @param init initial value of the CRC register
     * @param refin true if the input bytes are reflected
     * @param refout true if the CRC is reflected before the final XOR
     * @param xorout value XOR-ed to the CRC at the end
     * @return model of the parameters
     * @throws IllegalArgumentException if the width is not in 1..64, or if poly, init or xorout do not fit into width bits
     */
    public static @NonNull CrcModel of(final int width, final long poly, final long init, final boolean refin, final boolean refout, final long xorout) {
        return of(CrcParameters.of(width, poly, init, refin, refout, xorout));
    }

    /**
     * Create a builder of a model of the width
     * @param width CRC width in bits: 1..64
     * @return builder of a model
     * @throws IllegalArgumentException if the width is not in 1..64
     */
    public static @NonNull Builder builder(final int width) {
        return new Builder(width);
    }

    /**
     * Get the description of the model
     * @return description of the parameters of the model
     */
    public @NonNull CrcDescription description() {
        return this.description;
    }

    /**
     * Get the name of the model: the name of the description or the alias the model was chosen by
     * @return name of the model
     */
    public @NonNull String name() {
        return this.description.names().get(this.index);
    }

    /**
     * Get the parameters of the model
     * @return parameters of the model
     */
    public @NonNull CrcParameters parameters() {
        return this.description.parameters();
    }

    /**
     * Get the check value of the model, see {@link CrcDescription#check()}
     * @return check value
     */
    public long check() {
        return this.description.check();
    }

    /**
     * Get the CRC of the empty data: the start of a calculation
     * @return CRC of the empty data
     */
    public @NonNull CRC crc() {
        return this.empty;
    }

    /**
     * Calculate the CRC of the data
     * @param data data
     * @return CRC of the data
     */
    public @NonNull CRC crc(@NonNull final byte[] data) {
        return this.empty.update(data);
    }

    /**
     * Calculate the CRC of a part of the data
     * @param data data
     * @param offset offset of the part in the data
     * @param length length of the part
     * @return CRC of the part of the data
     * @throws ArrayIndexOutOfBoundsException if the part is not inside the data
     */
    public @NonNull CRC crc(@NonNull final byte[] data, final int offset, final int length) {
        return this.empty.update(data, offset, length);
    }

    /**
     * Get the CRC of data processed before, to continue the calculation: the CRC with the value and the length
     * @param value CRC value of the data processed so far. Bits above the CRC width are ignored, so that a CRC-32
     *              kept in an int can be passed as is. If length is 0, the value is ignored: the CRC of the empty data
     *              has its value
     * @param length length of the data processed so far
     * @return CRC with the value and the length
     * @throws IllegalArgumentException if the length is negative
     */
    public @NonNull CRC crc(final long value, final long length) {
        if (length < 0) {
            throw new IllegalArgumentException("CrcModel::crc - length is negative: " + length);
        }
        return length == 0 ? this.empty : new CRC(this, value & this.engine.mask(), length);
    }

    /**
     * Calculate the CRC value of the data, without a {@link CRC} object: {@code crc(data).value()}
     * @param data data
     * @return CRC value of the data
     */
    public long value(@NonNull final byte[] data) {
        Objects.requireNonNull(data, "CrcModel::value - data is null");
        return value(data, 0, data.length);
    }

    /**
     * Calculate the CRC value of the first bytes of the data, without a {@link CRC} object: {@code value(data, 0, length)}
     * @param data data
     * @param length number of the first bytes of the data
     * @return CRC value of the first bytes of the data
     * @throws ArrayIndexOutOfBoundsException if the length is negative or exceeds the length of the data
     */
    public long value(@NonNull final byte[] data, final int length) {
        return value(data, 0, length);
    }

    /**
     * Calculate the CRC value of a part of the data, without a {@link CRC} object: {@code crc(data, offset, length).value()}
     * @param data data
     * @param offset offset of the part in the data
     * @param length length of the part
     * @return CRC value of the part of the data
     * @throws ArrayIndexOutOfBoundsException if the part is not inside the data
     */
    public long value(@NonNull final byte[] data, final int offset, final int length) {
        Objects.requireNonNull(data, "CrcModel::value - data is null");
        if (offset < 0 || length < 0 || length > data.length - offset) {
            throw new ArrayIndexOutOfBoundsException("CrcModel::value - offset " + offset + " and length " + length
                    + " out of the buffer length " + data.length);
        }
        long empty = this.engine.empty();
        return length == 0 ? empty : this.engine.update(empty, data, offset, length);
    }

    @NonNull CrcEngine engine() {
        return this.engine;
    }

    /**
     * The name of the model
     * @return name of the model
     */
    @Override
    public String toString() {
        return name();
    }

    /**
     * Builder of a model. The width is given when the builder is created, so every value is checked to fit into
     * the width when it is set. The polynomial and the reflection of the input and of the output are required:
     * they have no neutral value, and a forgotten one would give another CRC without an error. The init and the xorout
     * are 0 if they are not set. A check value is verified by {@link #build()}
     *
     * @since 2.0.0
     */
    public static final class Builder {

        private final int width;
        private long poly;
        private boolean polySet;
        private long init;
        private boolean refin;
        private boolean refinSet;
        private boolean refout;
        private boolean refoutSet;
        private long xorout;
        private long check;
        private boolean checkSet;

        Builder(final int width) {
            CrcParameters.checkWidth("CrcModel::builder", width);
            this.width = width;
        }

        /**
         * Set the polynomial, required
         * @param poly polynomial, without the x^width term: for example 0x04C11DB7 for CRC-32, not 0x104C11DB7
         * @return this builder
         * @throws IllegalArgumentException if the polynomial does not fit into the width
         */
        public @NonNull Builder poly(final long poly) {
            this.poly = CrcParameters.checkFits("CrcModel.Builder::poly", "poly", poly, width);
            this.polySet = true;
            return this;
        }

        /**
         * Set the initial value of the CRC register, 0 if it is not set
         * @param init initial value of the CRC register, not reflected
         * @return this builder
         * @throws IllegalArgumentException if the value does not fit into the width
         */
        public @NonNull Builder init(final long init) {
            this.init = CrcParameters.checkFits("CrcModel.Builder::init", "init", init, width);
            return this;
        }

        /**
         * Set whether the input bytes are reflected, required
         * @param refin true if the input bytes are reflected
         * @return this builder
         */
        public @NonNull Builder refin(final boolean refin) {
            this.refin = refin;
            this.refinSet = true;
            return this;
        }

        /**
         * Set whether the CRC is reflected before the final XOR, required
         * @param refout true if the CRC is reflected before the final XOR
         * @return this builder
         */
        public @NonNull Builder refout(final boolean refout) {
            this.refout = refout;
            this.refoutSet = true;
            return this;
        }

        /**
         * Set refin and refout to the same value
         * @param reflected true if the input bytes and the CRC are reflected
         * @return this builder
         */
        public @NonNull Builder reflected(final boolean reflected) {
            return refin(reflected).refout(reflected);
        }

        /**
         * Set the value XOR-ed to the CRC at the end, 0 if it is not set
         * @param xorout value XOR-ed to the CRC at the end
         * @return this builder
         * @throws IllegalArgumentException if the value does not fit into the width
         */
        public @NonNull Builder xorout(final long xorout) {
            this.xorout = CrcParameters.checkFits("CrcModel.Builder::xorout", "xorout", xorout, width);
            return this;
        }

        /**
         * Set the check value that {@link #build()} verifies: the CRC of the ASCII bytes of
         * {@link CrcParameters#CHECK_INPUT}
         * @param check check value
         * @return this builder
         * @throws IllegalArgumentException if the value does not fit into the width
         */
        public @NonNull Builder check(final long check) {
            this.check = CrcParameters.checkFits("CrcModel.Builder::check", "check", check, width);
            this.checkSet = true;
            return this;
        }

        /**
         * Build the model of the parameters, see {@link CrcModel#of(CrcParameters)}
         * @return model of the parameters
         * @throws IllegalStateException if the polynomial, refin or refout is not set
         * @throws IllegalArgumentException if a check value is set and the parameters have another one
         */
        public @NonNull CrcModel build() {
            List<String> missing = new ArrayList<>(3);
            if (!polySet) {
                missing.add("poly");
            }
            if (!refinSet) {
                missing.add("refin");
            }
            if (!refoutSet) {
                missing.add("refout");
            }
            if (!missing.isEmpty()) {
                throw new IllegalStateException("CrcModel.Builder::build - not set: " + String.join(", ", missing));
            }
            CrcParameters parameters = CrcParameters.of(width, poly, init, refin, refout, xorout);
            if (checkSet) {
                // verified before the description is created: the lookup tables are not needed
                long actual = CrcDescription.checkValue(new CrcEngine(parameters));
                if (actual != check) {
                    throw new IllegalArgumentException("CrcModel.Builder::build - wrong check value " + CrcParameters.hex(check, width)
                            + " for " + parameters + ": the CRC of \"" + CrcParameters.CHECK_INPUT + "\" is " + CrcParameters.hex(actual, width));
                }
            }
            return CrcModel.of(parameters);
        }

    }

}
