/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

/**
 * The description of a CRC parameter set: its names, its check value, its residue and the internal structures of
 * the calculation.
 * There is one description per parameter set: every model and every CRC of the parameters, by any name, refers to it,
 * and the lookup tables of the calculation are built once, on the first calculation with data.
 * <p>
 * The parameters of an algorithm of the catalogue ({@link StandardCrc}) have its name and its aliases. Other parameters
 * have a name made of the parameters, {@code CRC-{width}/P{poly}_I{init}[_RI][_RO][_X{xorout}]} with hexadecimal values
 * (for example {@code CRC-16/P1234_I0_RI_RO}), and no aliases.
 * <p>
 * The descriptions are kept in a pool that refers to them weakly: when no model and no CRC of the parameters is used,
 * the garbage collector collects the description with its lookup tables, and the next model of the parameters gets
 * a new description. The system property {@value #RETENTION_PROPERTY} {@code = soft} makes the pool refer to them
 * softly instead: an unused description is kept until the memory runs short. The property is read once, when the pool
 * is used first. The pool removes the entries of the collected descriptions when it is used, on every Java version:
 * the library starts no thread, which would keep its class loader from being collected.
 * <p>
 * The classes of the JDK calculate some CRCs with the CRC instructions of the CPU: {@code java.util.zip.CRC32}
 * the parameters of CRC-32/ISO-HDLC and CRC-32/JAMCRC, {@code java.util.zip.CRC32C} on Java 9+ those of CRC-32/ISCSI
 * (CRC-32C); generally a CRC 32 bits wide with refin and the polynomial of the class, whatever init, refout and xorout.
 * Java 8 has no {@code CRC32C}, so CRC-32C is calculated by the lookup tables of the library there. Only the speed
 * changes, not the CRC values. A model of such parameters is calculated by the class of the JDK or by the lookup tables,
 * as it was chosen ({@link CrcModel#of(String, boolean)}, {@link CrcModel#withJdk(boolean)}); a model chosen without
 * a choice gets the default of {@link #isJdk()}, which the system property {@value #JDK_PROPERTY} {@code = true} sets
 * initially and {@link #setJdk(boolean)} changes.
 * <p>
 * A description is unique for its parameters, so it is compared by identity. Descriptions are immutable and thread-safe.
 *
 * @since 2.0.0
 * @author Andrey D. Shindarev
 */
public final class CrcDescription {

    /**
     * System property of the references of the pool to the descriptions: {@code weak} (the default) or {@code soft}
     */
    public static final String RETENTION_PROPERTY = "com.github.sftwnd.crayfish.common.crc.retention";

    /**
     * System property of the initial default of {@link #isJdk()}: {@code true} makes the classes of the JDK calculate
     * the CRCs they can; not set by default, when the lookup tables of the library calculate every CRC. It is read once,
     * when the first model is created; {@link #setJdk(boolean)} changes the default later
     * @since 2.1.0
     */
    public static final String JDK_PROPERTY = "com.github.sftwnd.crayfish.common.crc.jdk";

    private static final byte[] CHECK_DATA = CrcParameters.CHECK_INPUT.getBytes(StandardCharsets.US_ASCII);

    // the way of the models chosen without a choice: the classes of the JDK for the CRCs they calculate, or the lookup
    // tables of the library; the property gives the initial value, setJdk changes it
    private static volatile boolean defaultJdk = jdk(System::getProperty);

    // the descriptions in use: one per parameter set
    private static final Interner<CrcParameters, CrcDescription> DESCRIPTIONS = new Interner<>(Retention.configured());

    private final CrcParameters parameters;
    private final @Nullable StandardCrc standard;
    private final List<String> names;
    private final List<String> aliases;
    private final long check;
    private final long residue;
    // the calculation by the lookup tables; the calculation by a class of the JDK gives it the short data
    private final CrcEngine engine;
    // the models of the names calculated by the lookup tables, in the order of the names: a model is unique for its
    // description, name and way
    private final CrcModel[] models;
    // the models of the names calculated by the class of the JDK, created on the first request, so that the class of
    // the calculation with the JDK is not loaded before; the models of the lookup tables for the parameters that no
    // class of the JDK calculates
    private final AtomicReference<CrcModel[]> jdkModels = new AtomicReference<>();

    private CrcDescription(@NonNull final CrcParameters parameters) {
        this.parameters = parameters;
        this.standard = StandardCrc.find(parameters).orElse(null);
        this.engine = new CrcEngine(parameters);
        if (this.standard != null) {
            this.names = this.standard.names();
            this.aliases = this.standard.aliases();
            this.check = this.standard.check();
            this.residue = this.standard.residue();
        } else {
            this.names = Collections.singletonList(parameters.parametricName());
            this.aliases = Collections.emptyList();
            this.check = checkValue(this.engine);
            this.residue = parameters.residueValue();
        }
        this.models = new CrcModel[this.names.size()];
        for (int index = 0; index < this.models.length; index++) {
            this.models[index] = new CrcModel(this, index, this.engine);
        }
    }

    /**
     * Whether the models chosen without a choice of the way are calculated by the classes of the JDK, for the CRCs
     * they calculate: {@link CrcModel#of(String)}, {@link CrcModel#find(String)}, {@link CrcModel#of(CrcParameters)},
     * a builder without {@link CrcModel.Builder#jdk(boolean)}, {@link StandardCrc#model()}, {@link #model()} and
     * {@link #model(String)}.
     * Initially the value of the system property {@value #JDK_PROPERTY}, then the value of {@link #setJdk(boolean)}
     * @return true if the classes of the JDK calculate the models chosen without a choice, false if the lookup tables
     *         of the library do
     * @since 2.1.0
     */
    public static boolean isJdk() {
        return defaultJdk;
    }

    /**
     * Set the way of the models chosen without a choice, see {@link #isJdk()}. The models chosen before keep their way,
     * and so do their CRCs and checksums; the system property is not changed
     * @param jdk true for the classes of the JDK, for the CRCs they calculate; false for the lookup tables of the library
     * @since 2.1.0
     */
    public static void setJdk(final boolean jdk) {
        defaultJdk = jdk;
    }

    /* whether the property read by the function asks for the classes of the JDK: "true" in any case. A security manager
       that does not allow to read the property gives the default, the lookup tables of the library; the function is
       a parameter for the tests of such a manager, which newer JDKs do not allow to install */
    static boolean jdk(@NonNull final UnaryOperator<String> properties) {
        String value;
        try {
            value = properties.apply(JDK_PROPERTY);
        } catch (SecurityException e) {
            value = null;
        }
        return value != null && Boolean.parseBoolean(value.trim());
    }

    // the description of the parameters: the one in use, or a new one
    static @NonNull CrcDescription of(@NonNull final CrcParameters parameters) {
        return DESCRIPTIONS.intern(parameters, CrcDescription::new);
    }

    // the pool of the descriptions, for the tests
    static @NonNull Interner<CrcParameters, CrcDescription> descriptions() {
        return DESCRIPTIONS;
    }

    // the check value of the parameters a bit at a time: the lookup tables are not built
    static long checkValue(@NonNull final CrcEngine engine) {
        return engine.bitwise(CHECK_DATA);
    }

    /**
     * Get the parameters
     * @return parameters of the description
     */
    public @NonNull CrcParameters parameters() {
        return this.parameters;
    }

    /**
     * Get the name: the name of the algorithm of the catalogue, or the name made of the parameters
     * @return name of the description, it starts with {@code CRC-{width}/}
     */
    public @NonNull String name() {
        return this.names.get(0);
    }

    /**
     * Get the aliases: the aliases of the algorithm of the catalogue in its order
     * @return unmodifiable list of the aliases, empty for parameters outside the catalogue
     */
    public @NonNull List<String> aliases() {
        return this.aliases;
    }

    /**
     * Get all names: the name, followed by the aliases
     * @return unmodifiable list of the names, the name first
     */
    public @NonNull List<String> names() {
        return this.names;
    }

    /**
     * Get the check value: the CRC of the ASCII bytes of {@link CrcParameters#CHECK_INPUT}
     * @return check value
     */
    public long check() {
        return this.check;
    }

    /**
     * Get the residue of the catalogue: the register after an error-free codeword (the data followed by the width bits
     * of its CRC), reflected if refout, without the final XOR; calculated as the catalogue does, the register of
     * the width initialised with xorout, reflected if refout, after as many zero bits as the width, reflected if refin.
     * For a width that is a multiple of 8 and refin = refout, the CRC value of a valid codeword of
     * {@link CrcModel#isValid(byte[])} is {@code residue ^ xorout}, whatever the data: {@code 0x2144df1c} for
     * CRC-32/ISO-HDLC, for example, the value of a {@link CrcChecksum} that has read such a codeword as a stream. For
     * refin = refout and every width, so is the CRC value of bits followed by their CRC in the order of the model, see
     * {@link CrcModel#valueOfBits(byte[], long)}
     * @return residue
     * @since 2.1.0
     */
    public long residue() {
        return this.residue;
    }

    /**
     * Get the algorithm of the catalogue with these parameters
     * @return the algorithm of the catalogue, empty for parameters outside the catalogue
     */
    public @NonNull Optional<StandardCrc> standard() {
        return Optional.ofNullable(this.standard);
    }

    /**
     * Get the model with the name of the description, of the way of {@link #isJdk()}
     * @return model with the name of the description
     */
    // there is a model per name and at least one name, of the catalogue or made of the parameters, so the model 0
    // always exists: javabugs:S6466 is a false positive
    @SuppressWarnings("javabugs:S6466")
    public @NonNull CrcModel model() {
        return model(0, defaultJdk);
    }

    /**
     * Get the model with one of the names of the description, of the way of {@link #isJdk()}, whatever the case of
     * the ASCII letters of the name (since 2.1.0; before, the case mattered)
     * @param name name or alias of the description
     * @return model with the name as the description writes it
     * @throws IllegalArgumentException if the description has no such name
     */
    public @NonNull CrcModel model(@NonNull final String name) {
        return model(name, defaultJdk);
    }

    /**
     * Get the model with the name of the description, calculated the way given
     * @param jdk true for the class of the JDK, if a class of the JDK calculates the CRC; false for the lookup tables
     *            of the library
     * @return model with the name of the description and the way, see {@link CrcModel#withJdk(boolean)}
     * @since 2.1.0
     */
    // the model 0 always exists, see model(): javabugs:S6466 is a false positive
    @SuppressWarnings("javabugs:S6466")
    public @NonNull CrcModel model(final boolean jdk) {
        return model(0, jdk);
    }

    /**
     * Get the model with one of the names of the description, calculated the way given, whatever the case of the ASCII
     * letters of the name
     * @param name name or alias of the description
     * @param jdk true for the class of the JDK, if a class of the JDK calculates the CRC; false for the lookup tables
     *            of the library
     * @return model with the name as the description writes it and the way, see {@link CrcModel#withJdk(boolean)}
     * @throws IllegalArgumentException if the description has no such name
     * @since 2.1.0
     */
    public @NonNull CrcModel model(@NonNull final String name, final boolean jdk) {
        int index = this.names.indexOf(Objects.requireNonNull(name, "CrcDescription::model - name is null"));
        if (index < 0) {
            // the names of the catalogue and the parametric names are written in upper case
            index = this.names.indexOf(CrcParameters.upperCase(name));
        }
        if (index < 0) {
            throw new IllegalArgumentException("CrcDescription::model - " + name() + " has no name " + name);
        }
        return model(index, jdk);
    }

    // the model of the name with the index in the names: calculated by the class of the JDK if jdk is true and a class
    // of the JDK calculates the parameters, otherwise by the lookup tables. The index is the one of a name of the
    // description (0, the name of the description, or the index of a name found), and both arrays have a model per
    // name: javabugs:S6466 is a false positive
    @SuppressWarnings("javabugs:S6466")
    @NonNull CrcModel model(final int index, final boolean jdk) {
        return jdk ? jdkModels()[index] : this.models[index];
    }

    // the model of a name that denotes these parameters: the model of the name if the description has it,
    // otherwise the model with the name of the description (a name made of the parameters of the catalogue)
    @NonNull CrcModel modelOf(@NonNull final String name, final boolean jdk) {
        int index = this.names.indexOf(name);
        return model(Math.max(index, 0), jdk);
    }

    // the models of the class of the JDK: the models created first stay, also if threads create them at the same time
    private @NonNull CrcModel[] jdkModels() {
        CrcModel[] built = this.jdkModels.get();
        if (built == null) {
            this.jdkModels.compareAndSet(null, newJdkModels());
            built = this.jdkModels.get();
        }
        return built;
    }

    // new models of the class of the JDK that calculates the parameters, a reflected CRC 32 bits wide of its polynomial,
    // whatever init, refout and xorout; the models of the lookup tables if no class of the JDK calculates them
    private @NonNull CrcModel[] newJdkModels() {
        JdkCrc<?> jdk = this.parameters.width() == Integer.SIZE && this.parameters.refin() ? JdkCrcs.of(this.parameters.poly()) : null;
        if (jdk == null) {
            return this.models;
        }
        // the engine is created by the factory of its class: a new JdkCrcEngine here would make the verifier load
        // the class with this one, and the JIT compiler would no longer take CrcEngine for the only engine
        CrcEngine jdkEngine = JdkCrcEngine.of(this.parameters, jdk, this.engine);
        CrcModel[] result = new CrcModel[this.models.length];
        for (int index = 0; index < result.length; index++) {
            result[index] = new CrcModel(this, index, jdkEngine);
        }
        return result;
    }

    // the calculation by the lookup tables
    @NonNull CrcEngine engine() {
        return this.engine;
    }

    /**
     * The description as the parameter line of the catalogue: the parameters, the check value, the residue (since
     * 2.1.0) and the name, for example
     * {@code width=16  poly=0x8005  init=0x0000  refin=true  refout=true  xorout=0x0000  check=0xbb3d  residue=0x0000  name="CRC-16/ARC"}
     * @return description as string
     */
    @Override
    public @NonNull String toString() {
        int width = this.parameters.width();
        return this.parameters + "  check=" + CrcParameters.hex(this.check, width) + "  residue=" + CrcParameters.hex(this.residue, width)
                + "  name=\"" + name() + '"';
    }

}
