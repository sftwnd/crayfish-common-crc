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
import java.util.Optional;
import java.util.function.UnaryOperator;

/**
 * The description of a CRC parameter set: its names, its check value and the internal structures of the calculation.
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
 * is used first. On Java 9+ a cleaner thread removes the entry of a collected description from the pool, before Java 9
 * the pool removes such entries when it is used.
 * <p>
 * The system property {@value #JDK_PROPERTY} {@code = true} makes the classes of the JDK calculate the CRCs they can,
 * with the CRC instructions of the CPU: {@code java.util.zip.CRC32} the parameters of CRC-32/ISO-HDLC and
 * CRC-32/JAMCRC, {@code java.util.zip.CRC32C} on Java 9+ those of CRC-32/ISCSI (CRC-32C); generally a CRC 32 bits wide
 * with refin and the polynomial of the class, whatever init, refout and xorout. Java 8 has no {@code CRC32C}, so
 * CRC-32C is calculated by the lookup tables of the library there. Only the speed changes, not the CRC values.
 * The property is not set by default and is read once, when the first model is created.
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
     * System property that makes the classes of the JDK calculate the CRCs they can: {@code true}; not set by default,
     * when the lookup tables of the library calculate every CRC
     * @since 2.1.0
     */
    public static final String JDK_PROPERTY = "com.github.sftwnd.crayfish.common.crc.jdk";

    private static final byte[] CHECK_DATA = CrcParameters.CHECK_INPUT.getBytes(StandardCharsets.US_ASCII);

    // whether the classes of the JDK calculate the CRCs they can: read once, before the first description is created
    private static final boolean JDK = jdk(System::getProperty);

    // the descriptions in use: one per parameter set
    private static final Interner<CrcParameters, CrcDescription> DESCRIPTIONS = new Interner<>(Retention.configured());

    private final CrcParameters parameters;
    private final @Nullable StandardCrc standard;
    private final List<String> names;
    private final List<String> aliases;
    private final long check;
    private final CrcEngine engine;
    // the models of the names, in the order of the names: a model is unique for its description and name
    private final CrcModel[] models;

    private CrcDescription(@NonNull final CrcParameters parameters) {
        this.parameters = parameters;
        this.standard = StandardCrc.find(parameters).orElse(null);
        // JdkCrcEngine is loaded only with the property
        this.engine = JDK ? JdkCrcEngine.of(parameters) : new CrcEngine(parameters);
        if (this.standard != null) {
            this.names = this.standard.names();
            this.aliases = this.standard.aliases();
            this.check = this.standard.check();
        } else {
            this.names = Collections.singletonList(parameters.parametricName());
            this.aliases = Collections.emptyList();
            this.check = checkValue(this.engine);
        }
        this.models = new CrcModel[this.names.size()];
        for (int index = 0; index < this.models.length; index++) {
            this.models[index] = new CrcModel(this, index);
        }
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
     * Get the algorithm of the catalogue with these parameters
     * @return the algorithm of the catalogue, empty for parameters outside the catalogue
     */
    public @NonNull Optional<StandardCrc> standard() {
        return Optional.ofNullable(this.standard);
    }

    /**
     * Get the model with the name of the description
     * @return model with the name of the description
     */
    // there is a model per name and at least one name, of the catalogue or made of the parameters, so models[0]
    // always exists: javabugs:S6466 is a false positive
    @SuppressWarnings("javabugs:S6466")
    public @NonNull CrcModel model() {
        return this.models[0];
    }

    /**
     * Get the model with one of the names of the description
     * @param name name or alias of the description
     * @return model with the name
     * @throws IllegalArgumentException if the description has no such name
     */
    public @NonNull CrcModel model(@NonNull final String name) {
        int index = this.names.indexOf(name);
        if (index < 0) {
            throw new IllegalArgumentException("CrcDescription::model - " + name() + " has no name " + name);
        }
        return this.models[index];
    }

    // the model of the name with the index in the names
    @NonNull CrcModel model(final int index) {
        return this.models[index];
    }

    // the model of a name that denotes these parameters: the model of the name if the description has it,
    // otherwise the model with the name of the description (a name made of the parameters of the catalogue)
    @NonNull CrcModel modelOf(@NonNull final String name) {
        int index = this.names.indexOf(name);
        return this.models[Math.max(index, 0)];
    }

    @NonNull CrcEngine engine() {
        return this.engine;
    }

    /**
     * The description in the format of the catalogue: the parameters, the check value and the name, for example
     * {@code width=16  poly=0x8005  init=0x0000  refin=true  refout=true  xorout=0x0000  check=0xbb3d  name="CRC-16/ARC"}
     * @return description as string
     */
    @Override
    public @NonNull String toString() {
        return this.parameters + "  check=" + CrcParameters.hex(this.check, this.parameters.width()) + "  name=\"" + name() + '"';
    }

}
