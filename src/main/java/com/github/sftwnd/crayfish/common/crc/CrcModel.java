/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Registered predefined CrcModel for CrcDescription
 */
@SuppressWarnings({"java:S100", "java:S107"})
@EqualsAndHashCode(callSuper = true)
public class CrcModel extends CrcDescription {

    public static final String CHECK_BUFF = "123456789";

    @Getter public  final String name;
    @Getter public  final Long check;
    @Getter private final CrcDescription crcDescription;

    // predefined model: it is added to the predefined models in declaration order
    private CrcModel(@Nullable final String name, final int width, final long poly, final long init, final boolean refin, final boolean refot, final long xorot, @Nullable final Long check) {
        this(name, new CrcDescription(width, poly, init, refin, refot, xorot), check);
        predefine(this);
    }

    private CrcModel(@Nullable final String name, @NonNull final CrcDescription model, @Nullable final Long check) {
        super(_config(Objects.requireNonNull(model, "CRC_model_t::new - model is null")));
        // a generated name shows the original parameters, not the processed ones of this object
        this.name = name == null ? new CrcDescription(model).toString() : name;
        this.crcDescription = model;
        this.check = Optional.ofNullable(check).orElseGet(() -> checkValue(model));
    }

    // the check value of the parameters: the crc of the ASCII bytes of "123456789"; -1 stops the recursion,
    // the model is only used for this calculation
    private static long checkValue(@NonNull final CrcDescription model) {
        return new CrcModel(null, model, -1L).getCRC().update(CHECK_BUFF.getBytes(StandardCharsets.US_ASCII)).getCrc();
    }

    @Override
    public long getPoly() {
        return crcDescription.getPoly();
    }

    @Override
    public long getInit() {
        return crcDescription.getInit();
    }

    @Override
    public boolean isRefot() {
        return crcDescription.isRefot();
    }

    /**
     * Create CRC for this model
     * @return created CRC
     */
    @Override
    public @NonNull CRC getCRC() {
        return new CRC(this);
    }

    /**
     * Create CRC and update by the value of buff
     * @param buff buffer
     * @param offset buffer offset
     * @param len used buffer size
     * @return created CRC
     */
    public @NonNull CRC getCRC(byte[] buff, int offset, int len) {
        return getCRC().update(buff, offset, len);
    }

    /**
     * Create CRC and update by the value of buff
     * @param buff buffer
     * @param len used buffer size
     * @return created CRC
     */
    public @NonNull CRC getCRC(byte[] buff, int len) {
        return getCRC(buff, 0, len);
    }

    /**
     * Create CRC and update by the value of buff
     * @param buff buffer
     * @return created CRC
     */
    public @NonNull CRC getCRC(byte[] buff) {
        return getCRC(buff, 0, buff == null ? 0 : buff.length);
    }

    /**
     * Create CRC that continues the calculation from a known crc value
     * @param crc initial crc value: crc of the bytes processed so far. Bits above the CRC width are ignored,
     *            so a CRC-32 kept in an int can be passed as is. If len is 0, crc is ignored:
     *            a CRC without processed data has the crc of the empty message
     * @param len initial processed length: number of bytes processed so far
     * @return created CRC
     * @since 1.1.0
     */
    @Override
    public @NonNull CRC getCRC(long crc, long len) {
        return new CRC(this, crc, len);
    }

    /**
     * Create CRC that continues the calculation from a known crc value: the same as {@link #getCRC(long, long)},
     * kept for the binary compatibility with the versions up to 1.0.2
     * @param crc initial crc value: crc of the bytes processed so far
     * @param len initial processed length: number of bytes processed so far
     * @return created CRC
     */
    @Override
    public @NonNull CRC getCRC(long crc, int len) {
        return getCRC(crc, (long) len);
    }

    @Override
    public String toString() {
        return name;
    }

    protected void _init() {
        lookupTable();
    }

    // the lookup table, built on first use without a lock: the model takes the table of its description if it is built
    // already, otherwise it builds the table and stores it there. Threads that build it at the same time build identical
    // tables, and the volatile fields publish them completely
    private long[] lookupTable() {
        long[] table = this.table_byte;
        if (table == null) {
            table = this.crcDescription.table_byte;
            if (table == null) {
                table = _createBytewiseTable();
                this.crcDescription.table_byte = table;
            }
            this.table_byte = table;
        }
        return table;
    }

    /**
     * Process the model data for the calculation: the fields of the model get the processed values,
     * the getters return the original parameters
     */
    @SuppressWarnings("java:S3358")
    private static CrcDescription _config(@NonNull final CrcDescription model) {
        return new CrcDescription(
                       model,
                       model.refin ? CrcModel.reflect(model.poly, model.width) : model.poly,
                       (model.refot ? CrcModel.reflect(model.init, model.width) : model.init) ^ model.xorot,
                       model.refot ^ model.refin
                   );
    }

    private long[] _createBytewiseTable() {
        long[] table = new long[256];
        long crc;
        for (int k = 0; k < table.length; k++) {
            crc = createBitwiseValue(k);
            if (refot) {
                crc = reflect(crc);
            }
            if (width < 8 && !refin) {
                crc <<= 8 - width;
            }
            table[k] = crc;
        }
        return table;
    }

    @SuppressWarnings({"java:S3776", "java:S125"})
    private long createBitwiseValue(int k) {
        /* if requested, return the initial CRC: if (k < 0) return init; */
        long poly = this.poly;
        /* pre-process the CRC */
        long crc = xorot;
        if (refot) {
            crc = reflect(crc);
        }
        /* process the input data a bit at a time */
        if (refin) {
            crc &= widmask(width);
            crc ^= k;
            for (int i = 0; i < 8; i++) {
                crc = ((crc & 1) != 0) ? (crc >>> 1) ^ poly : crc >>> 1;
            }
        } else if (width <= 8) {
            int shift = 8 - width;           /* 0..7 */
            poly <<= shift;
            crc <<= shift;
            crc ^= k;
            for (int i = 0; i < 8; i++) {
                crc = (crc & 0x80) != 0 ? (crc << 1) ^ poly : crc << 1;
            }
            crc >>= shift;
            crc &= widmask(width);
        } else {
            long mask = 1L << (width - 1);
            int shift = width - 8;           /* 1..WORDBITS-8 */
            crc ^= ((long) k) << shift;
            for (int i = 0; i < 8; i++) {
                crc = ((crc & mask) != 0) ? (crc << 1) ^ poly : crc << 1;
            }
            crc &= widmask(width);
        }
        /* post-process and return the CRC */
        if (refot) {
            crc = reflect(crc);
        }
        return crc ^ xorot;
    }

    protected long crcBytewise(long crc, byte[] buf, int offset, int len) {
        /* if requested, return the initial CRC */
        if (buf == null)
            return init;

        /* pre-process the CRC */
        if (refot) {
            crc = reflect(crc);
        }

        /* process the input data a byte at a time */
        long[] table = lookupTable();
        if (refin) {
            crc &= widmask(width);
            while (len-- > 0) {
                crc = (crc >>> 8) ^ table[(int) ((crc ^ buf[offset++]) & 0xff)];
            }
        } else if (width <= 8) {
            int shift = 8 - width;           /* 0..7 */
            crc <<= shift;
            while (len-- > 0) {
                crc = table[(int) ((crc ^ buf[offset++]) & 0xff)];
            }
            crc >>>= shift;
        } else {
            int shift = width - 8;           /* 1..WORDBITS-8 */
            while (len-- > 0) {
                crc = (crc << 8) ^
                      table[(int) (((crc >>> shift) ^ (buf[offset++] & 0xff)) & 0xff)];
            }
            crc &= widmask(width);
        }
        /* post-process and return the CRC */
        if (refot) {
            crc = reflect(crc);
        }
        return crc;
    }

    // registered models: a stream of the list works on a snapshot, so registration does not break running streams
    private static final List<CrcModel> models = new CopyOnWriteArrayList<>();

    // predefined models in declaration order, every primary model before its aliases: they add themselves
    // while the class is initialized, so the list has to be declared before them; it is not changed afterwards
    private static final List<CrcModel> predefinedModels = new ArrayList<>();

    // indexes of all registered models, the predefined ones included: the first model for every name, and the models
    // for every parameter set in the order of registration; they are changed while holding the lock on models
    private static final Map<String, CrcModel> modelsByName = new ConcurrentHashMap<>();
    private static final Map<CrcDescription, List<CrcModel>> modelsByParameters = new ConcurrentHashMap<>();

    // adds a predefined model while the class is initialized
    private static void predefine(@NonNull final CrcModel model) {
        predefinedModels.add(model);
        index(model);
    }

    private static void index(@NonNull final CrcModel model) {
        modelsByName.putIfAbsent(model.getName(), model);
        modelsByParameters.merge(model.getCrcDescription(), Collections.singletonList(model), (registered, added) -> {
            List<CrcModel> all = new ArrayList<>(registered);
            all.addAll(added);
            return Collections.unmodifiableList(all);
        });
    }

    /**
     * Stream of all registered models.
     * The stream contains the models registered at the time of the call
     * @return stream of models
     */
    public static @NonNull Stream<CrcModel> getModels() {
        return getModels(null);
    }

    // removes the models registered at run time, the predefined models stay: the tests start from the same registry
    static void removeRegisteredModels() {
        synchronized (models) {
            models.clear();
            modelsByName.clear();
            modelsByParameters.clear();
            predefinedModels.forEach(CrcModel::index);
        }
        synchronized (unregisteredModels) {
            unregisteredModels.clear();
        }
    }

    // models for parameters without a registered model, used by CrcDescription.getCRC(): a bounded cache of the
    // recently used ones, so that repeated calculations do not build the lookup table again, while the registry
    // does not grow; the map is used while holding the lock on it
    static final int UNREGISTERED_MODELS = 64;
    private static final Map<CrcDescription, CrcModel> unregisteredModels = new RecentlyUsedModels();

    private static final class RecentlyUsedModels extends LinkedHashMap<CrcDescription, CrcModel> {
        private static final long serialVersionUID = 1L;

        RecentlyUsedModels() {
            super(16, 0.75f, true);
        }

        @Override
        protected boolean removeEldestEntry(final Map.Entry<CrcDescription, CrcModel> eldest) {
            return size() > UNREGISTERED_MODELS;
        }
    }

    // the model used by CrcDescription.getCRC(): the registered model for the parameters (see construct()),
    // otherwise a model that is not registered
    static @NonNull CrcModel modelFor(@NonNull final CrcDescription crcDescription) {
        Optional<CrcModel> registered = findModel(null, crcDescription);
        if (registered.isPresent()) {
            return registered.get();
        }
        CrcDescription parameters = parameters(crcDescription);
        synchronized (unregisteredModels) {
            CrcModel model = unregisteredModels.get(parameters);
            if (model == null) {
                // a copy, also as the key: the description of the caller is neither kept nor changed
                model = new CrcModel(null, new CrcDescription(parameters), null);
                unregisteredModels.put(model.getCrcDescription(), model);
            }
            return model;
        }
    }

    // the number of models in the cache of CrcDescription.getCRC(), for the tests
    static int unregisteredModelCount() {
        synchronized (unregisteredModels) {
            return unregisteredModels.size();
        }
    }

    /**
     * Create a model for the parameters without registering it: {@link #getModels()}, {@link #lookUp(String)}
     * and {@link #construct(CrcDescription)} do not know it, and it is garbage collected like any other object.
     * Every call creates a new model and calculates its check value
     * @param name Name of model, null for a name made of the parameters
     * @param crcDescription CrcDescription
     * @return new model
     * @since 1.1.0
     */
    public static @NonNull CrcModel unregistered(@Nullable final String name, @NonNull final CrcDescription crcDescription) {
        Objects.requireNonNull(crcDescription, "CrcModel::unregistered - crcDescription is null");
        // a copy: the description of the caller is neither kept nor changed
        return new CrcModel(name, new CrcDescription(crcDescription), null);
    }

    /**
     * Create a model for the parameters without registering it, see {@link #unregistered(String, CrcDescription)}
     * @param crcDescription CrcDescription
     * @return new model with a name made of the parameters
     * @since 1.1.0
     */
    public static @NonNull CrcModel unregistered(@NonNull final CrcDescription crcDescription) {
        return unregistered(null, crcDescription);
    }

    /**
     * Stream of all registered models.
     * The stream contains the models registered at the time of the call: models registered later,
     * even while the stream is processed, are not included
     * @param filter filter predicate for models
     * @return stream of models
     */
    public static @NonNull Stream<CrcModel> getModels(@Nullable final Predicate<? super CrcModel> filter) {
        return Stream.concat(predefinedModels.stream(), models.stream())
                     .filter(filter == null ? m -> true : filter);
    }

    /**
     * Look up Crc Model by the name.
     * The misspelled names "CCRC-5/G-704" and "CRC32_C " of the versions 1.0.0 and 1.0.1 are accepted as well:
     * they return {@link #CRC5_G_704} and {@link #CRC32_C}
     * @param name Name of model
     * @return CrcModel for the name or null
     */
    public static @Nullable
    CrcModel lookUp(@Nullable final String name) {
        return name == null ? null : Optional.ofNullable(modelsByName.get(name)).orElseGet(() -> legacyModel(name));
    }

    // predefined models with a misspelled name in the versions 1.0.0 and 1.0.1
    private static @Nullable CrcModel legacyModel(@NonNull final String name) {
        switch (name) {
            case "CCRC-5/G-704": return CRC5_G_704;
            case "CRC32_C ":     return CRC32_C;
            default:             return null;
        }
    }

    /**
     * Creation and registration of CrcModel.
     * A registered model with the parameters of crcDescription is returned: the model with the given name,
     * otherwise crcDescription itself if it is a registered model, otherwise the model registered first for these
     * parameters (for predefined parameters the primary model, not an alias). A new model is registered
     * if no model with these parameters is registered, or if a name is given and none of them has it.
     * A name belongs to one parameter set: it cannot be registered for other parameters.
     * A given check value is verified: it has to be the crc of the ASCII bytes of {@link #CHECK_BUFF};
     * bits above the CRC width are ignored
     * @param name Name of model
     * @param crcDescription CrcDescription
     * @param value Check value, null to calculate it
     * @return registered model
     * @throws IllegalArgumentException if the name is used by a model with other parameters,
     *         or if the check value is not the crc of "123456789" for these parameters (both since 1.1.0)
     */
    public static @NonNull CrcModel construct(@Nullable final String name, @NonNull final CrcDescription crcDescription, @Nullable final Long value) {
        Objects.requireNonNull(crcDescription, "CrcModel::construct - crcDescription is null");
        // a registered model is returned without the lock
        Optional<CrcModel> registered = findModel(name, crcDescription)
                .filter(model -> name == null || model.getName().equals(name));
        if (registered.isPresent()) {
            return verifyCheck(registered.get(), value);
        }
        // lookup, checks and registration as one step: concurrent calls register the model only once,
        // and a name is never registered for two parameter sets
        synchronized (models) {
            Optional<CrcModel> model = findModel(name, crcDescription);
            if (model.isPresent() && (name == null || model.get().getName().equals(name))) {
                return verifyCheck(model.get(), value);
            }
            verifyName(name, crcDescription);
            if (model.isPresent()) {
                // a new name for registered parameters: the check value of the registered model
                return registerModel(name, model.get().getCrcDescription(), verifyCheck(model.get(), value).getCheck());
            }
            // a copy: the description of the caller is neither kept nor changed
            CrcDescription description = new CrcDescription(crcDescription);
            return registerModel(name, description, value == null ? null : verifyCheck(description, value));
        }
    }

    // a name belongs to the parameters of the model registered under it; the misspelled names of 1.0.x belong
    // to the parameters of their predefined models
    private static void verifyName(@Nullable final String name, @NonNull final CrcDescription crcDescription) {
        CrcModel owner = lookUp(name);
        if (owner != null && !owner.getCrcDescription().equals(parameters(crcDescription))) {
            throw new IllegalArgumentException("CrcModel::construct - the name " + name + " is used by a model with other parameters: "
                    + owner.getCrcDescription() + ", not " + new CrcDescription(crcDescription));
        }
    }

    private static CrcModel verifyCheck(@NonNull final CrcModel model, @Nullable final Long value) {
        if (value != null && (value & widmask(model.getWidth())) != model.getCheck()) {
            throw wrongCheck(value, model.getCheck(), model.getCrcDescription());
        }
        return model;
    }

    // the check value is calculated with a copy, so that no lookup table is built for the description of the new model
    private static long verifyCheck(@NonNull final CrcDescription description, final long value) {
        long check = checkValue(new CrcDescription(description));
        if ((value & widmask(description.getWidth())) != check) {
            throw wrongCheck(value, check, description);
        }
        return check;
    }

    private static IllegalArgumentException wrongCheck(final long value, final long check, @NonNull final CrcDescription description) {
        return new IllegalArgumentException("CrcModel::construct - wrong check value 0x" + Long.toHexString(value).toUpperCase()
                + " for " + description + ": the crc of \"" + CHECK_BUFF + "\" is 0x" + Long.toHexString(check).toUpperCase());
    }

    // registered model with the same parameters: the model with the requested name, otherwise the model passed in
    // if it is registered, otherwise the first registered one, which later registrations do not change:
    // the primary predefined model, or the model registered first at run time
    private static Optional<CrcModel> findModel(@Nullable final String name, @NonNull final CrcDescription crcDescription) {
        List<CrcModel> registered = modelsByParameters.getOrDefault(parameters(crcDescription), Collections.emptyList());
        Optional<CrcModel> model = registered.stream().filter(m -> m.getName().equals(name)).findFirst();
        if (!model.isPresent()) {
            model = registered.stream().filter(m -> m == crcDescription).findFirst();
        }
        return model.isPresent() ? model : registered.stream().findFirst();
    }

    // the parameters of a description as key of the index: a plain CrcDescription, as the models keep it
    private static CrcDescription parameters(@NonNull final CrcDescription description) {
        if (description instanceof CrcModel) {
            return ((CrcModel) description).getCrcDescription();
        }
        return description.getClass() == CrcDescription.class ? description : new CrcDescription(description);
    }

    // has to be called while holding the lock on models
    private static CrcModel registerModel(@Nullable String name, @NonNull CrcDescription crcDescription, @Nullable Long checkValue) {
        // a misspelled name of 1.0.x with the parameters of its model denotes the predefined model
        CrcModel legacy = name == null ? null : legacyModel(name);
        if (legacy != null && legacy.getCrcDescription().equals(crcDescription)) {
            return legacy;
        }
        CrcModel crcModel = new CrcModel(name, crcDescription, checkValue);
        models.add(crcModel);
        index(crcModel);
        return crcModel;
    }

    /**
     * Creation and registration of CrcModel, see {@link #construct(String, CrcDescription, Long)}
     * @param crcDescription CrcDescription
     * @param value Check value, null to calculate it
     * @return registered model
     * @throws IllegalArgumentException if the check value is not the crc of "123456789" for these parameters
     */
    public static @NonNull CrcModel construct(@NonNull final CrcDescription crcDescription, @Nullable final Long value) {
        return construct(null, crcDescription, value);
    }

    /**
     * Creation and registration of CrcModel
     * @param crcDescription CrcDescription
     * @return registered model
     */
    public static @NonNull CrcModel construct(@NonNull final CrcDescription crcDescription) {
        return construct(crcDescription, null);
    }

    /**
     * Creation and registration of CrcModel, see {@link #construct(String, CrcDescription, Long)}
     * @param name Name of model
     * @param crcDescription CrcDescription
     * @return registered model
     * @throws IllegalArgumentException if the name is used by a model with other parameters
     */
    public static @NonNull CrcModel construct(@Nullable final String name, @NonNull final CrcDescription crcDescription) {
        return construct(name, crcDescription, null);
    }

    // predefined alias: it is added to the predefined models in declaration order, after its primary model
    private static @NonNull CrcModel alias(@NonNull final String name, @NonNull CrcModel model) {
        CrcModel alias = new CrcModel(name, model.getCrcDescription(), model.getCheck());
        predefine(alias);
        return alias;
    }

    protected final long reflect(long value) {
        return reflect(value, getWidth());
    }

    protected static long reflect(long value, int bits) {
        long mask = widmask(bits);
        long rmask = ~mask;
        long prefix = value & rmask;
        long suffix = Long.reverse(value << (Long.SIZE - bits));
        return prefix | suffix;
    }

    protected static long widmask(int width) {
        return (((1L << (width-1)) - 1L) << 1) | 1L;
    }

    private static long gf2_matrix_times(long[] mat, long vec) {
        long sum = 0L;
        for (int i=0; vec != 0; vec >>>= 1, i++) {
            if ((vec & 1) != 0) {
                sum ^= mat[i];
            }
        }
        return sum;
    }

    private static void gf2_matrix_square(long[] square, long[] mat, int width) {
        for (int n = 0; n < width; n++) {
            square[n] = gf2_matrix_times(mat, mat[n]);
        }
    }

    // kept for the binary compatibility with the versions up to 1.0.2
    protected static long crc_general_combine(long crc1, long crc2, int len2, int width, long init, long poly, long xorot, boolean reflect) {
        return crc_general_combine(crc1, crc2, (long) len2, width, init, poly, xorot, reflect);
    }

    protected static long crc_general_combine(long crc1, long crc2, long len2, int width, long init, long poly, long xorot, boolean reflect) {
        int n;
        long col;
        long[] even = new long[width];          /* even-power-of-two zeros operator */
        long[] odd = new long[width];           /* odd-power-of-two zeros operator */

        if (reflect) {
            init = reflect(init, width);
            poly = reflect(poly, width);
        }
        /* degenerate case (also disallow negative lengths if type changed) */
        if (len2 <= 0) {
            return crc1;
        }

        /* exclusive-or the result with len2 zeros applied to the CRC of an empty
           sequence */
        crc1 ^= init ^ xorot;

        /* construct the operator for one zero bit and put in odd[] */
        if (reflect) {
            odd[0] = poly;                /* polynomial */
            col = 1;
            for (n = 1; n < width; n++) {
                odd[n] = col;
                col <<= 1;
            }
        } else {
            col = 2;
            for (n = 0; n < width - 1; n++) {
                odd[n] = col;
                col <<= 1;
            }
            odd[n] = poly;                /* polynomial */
        }
        /* put operator for two zero bits in even[] */
        gf2_matrix_square(even, odd, width);

        /* put operator for four zero bits in odd[] */
        gf2_matrix_square(odd, even, width);

       /* apply len2 zeros to crc1 (first square will put the operator for eight
          zero bits == one zero byte, in even[]) */
        do {
            /* apply zeros operator for this bit of len2 */
            gf2_matrix_square(even, odd, width);
            if ((len2 & 1) != 0) {
                crc1 = gf2_matrix_times(even, crc1);
            }
            len2 >>>= 1;

            /* if no more bits set, then done */
            if (len2 == 0)
                break;

            /* another iteration of the loop with odd[] and even[] swapped */
            gf2_matrix_square(odd, even, width);
            if ((len2 & 1) != 0) {
                crc1 = gf2_matrix_times(odd, crc1);
            }
            len2 >>>= 1;

            /* if no more bits set, then done */
        } while (len2 != 0);

        /* return combined crc */
        crc1 ^= crc2;
        return crc1;
    }

    // CRC-3
    public static final CrcModel CRC3_GSM                = new CrcModel("CRC-3/GSM", 3, 0x3, 0x0, false, false, 0x7, 0x4L);
    public static final CrcModel CRC3_ROHC               = new CrcModel("CRC-3/ROHC", 3, 0x3, 0x7, true, true, 0x0, 0x6L);

    // CRC-4
    public static final CrcModel CRC4_INTERLAKEN         = new CrcModel("CRC-4/INTERLAKEN", 4, 0x3, 0xf, false, false, 0xF, 0xBL);
    public static final CrcModel CRC4_G_704              = new CrcModel("CRC-4/G-704", 4, 0x3, 0x0, true, true, 0x0, 0x7L);
    public static final CrcModel CRC4_ITU                = alias("CRC-4/ITU", CRC4_G_704);

    // CRC-5
    public static final CrcModel CRC5_EPC_C1G2           = new CrcModel("CRC-5/EPC-C1G2", 5, 0x9, 0x9, false, false, 0x0, 0x0L);
    public static final CrcModel CRC5_EPC                = alias("CRC-5/EPC", CRC5_EPC_C1G2);
    public static final CrcModel CRC5_G_704              = new CrcModel("CRC-5/G-704", 5, 0x15, 0x0, true, true, 0x0, 0x7L);
    public static final CrcModel CRC5_ITU                = alias("CRC-5/ITU", CRC5_G_704);
    public static final CrcModel CRC5_USB                = new CrcModel("CRC-5/USB", 5, 0x5, 0x1F, true, true, 0x1F, 0x19L);

    // CRC-6
    public static final CrcModel CRC6_CDMA2000_A         = new CrcModel("CRC-6/CDMA2000-A", 6, 0x27, 0x3F, false, false, 0x0, 0xDL);
    public static final CrcModel CRC6_CDMA2000_B         = new CrcModel("CRC-6/CDMA2000-B", 6, 0x7, 0x3F, false, false, 0x0, 0x3BL);
    public static final CrcModel CRC6_DARC               = new CrcModel("CRC-6/DARC", 6, 0x19, 0x0, true, true, 0x0, 0x26L);
    public static final CrcModel CRC6_GSM                = new CrcModel("CRC-6/GSM", 6, 0x2F, 0x0, false, false, 0x3F, 0x13L);
    public static final CrcModel CRC6_G_704              = new CrcModel("CRC-6/G-704", 6, 0x3, 0x0, true, true, 0x0, 0x6L);
    public static final CrcModel CRC6_ITU                = alias("CRC-6/ITU", CRC6_G_704);

    // CRC-7
    public static final CrcModel CRC7_MMC                = new CrcModel("CRC-7/MMC", 7, 0x9, 0x0, false, false, 0x0, 0x75L);
    public static final CrcModel CRC7                    = alias("CRC-7", CRC7_MMC);
    public static final CrcModel CRC7_ROHC               = new CrcModel("CRC-7/ROHC", 7, 0x4F, 0x7F, true, true, 0x0, 0x53L);
    public static final CrcModel CRC7_UMTS               = new CrcModel("CRC-7/UMTS", 7, 0x45, 0x0, false, false, 0x0, 0x61L);

    // CRC-8
    public static final CrcModel CRC8_SMBUS              = new CrcModel("CRC-8/SMBUS", 8, 0x7, 0x0, false, false, 0x0, 0xF4L);
    public static final CrcModel CRC8                    = alias("CRC-8", CRC8_SMBUS);
    public static final CrcModel CRC8_AUTOSAR            = new CrcModel("CRC-8/AUTOSAR", 8, 0x2F, 0xFF, false, false, 0xFF, 0xDFL);
    public static final CrcModel CRC8_BLUETOOTH          = new CrcModel("CRC-8/BLUETOOTH", 8, 0xA7, 0x0, true, true, 0x0, 0x26L);
    public static final CrcModel CRC8_CDMA2000           = new CrcModel("CRC-8/CDMA2000", 8, 0x9B, 0xFF, false, false, 0x0, 0xDAL);
    /** @deprecated since 1.0.2, use {@link #CRC8_CDMA2000} */
    @Deprecated
    public static final CrcModel CRC8_CDMA_2000          = CRC8_CDMA2000;
    public static final CrcModel CRC8_DARC               = new CrcModel("CRC-8/DARC", 8, 0x39, 0x0, true, true, 0x0, 0x15L);
    public static final CrcModel CRC8_DVB_S2             = new CrcModel("CRC-8/DVB-S2", 8, 0xD5, 0x0, false, false, 0x0, 0xBCL);
    public static final CrcModel CRC8_TECH_3250          = new CrcModel("CRC-8/TECH-3250", 8, 0x1D, 0xFF, true, true, 0x0, 0x97L);
    public static final CrcModel CRC8_AES                = alias("CRC-8/AES", CRC8_TECH_3250);
    public static final CrcModel CRC8_EBU                = alias("CRC-8/EBU", CRC8_TECH_3250);
    public static final CrcModel CRC8_GSM_A              = new CrcModel("CRC-8/GSM-A", 8, 0x1D, 0x0, false, false, 0x0, 0x37L);
    public static final CrcModel CRC8_GSM_B              = new CrcModel("CRC-8/GSM-B", 8, 0x49, 0x0, false, false, 0xFF, 0x94L);
    public static final CrcModel CRC8_I_CODE             = new CrcModel("CRC-8/I-CODE", 8, 0x1D, 0xFD, false, false, 0x0, 0x7EL);
    /** @deprecated since 1.0.2, use {@link #CRC8_I_CODE} */
    @Deprecated
    public static final CrcModel CRC8_ICODE              = CRC8_I_CODE;
    public static final CrcModel CRC8_I_432_1            = new CrcModel("CRC-8/I-432-1", 8, 0x7, 0x0, false, false, 0x55, 0xA1L);
    public static final CrcModel CRC8_ITU                = alias("CRC-8/ITU", CRC8_I_432_1);
    public static final CrcModel CRC8_LTE                = new CrcModel("CRC-8/LTE", 8, 0x9B, 0x0, false, false, 0x0, 0xEAL);
    public static final CrcModel CRC8_MAXIM_DOW          = new CrcModel("CRC-8/MAXIM-DOW", 8, 0x31, 0x0, true, true, 0x0, 0xA1L);
    public static final CrcModel CRC8_MAXIM              = alias("CRC-8/MAXIM", CRC8_MAXIM_DOW);
    public static final CrcModel DOW_CRC                 = alias("DOW-CRC", CRC8_MAXIM_DOW);
    public static final CrcModel CRC8_OPENSAFETY         = new CrcModel("CRC-8/OPENSAFETY", 8, 0x2F, 0x0, false, false, 0x0, 0x3EL);
    public static final CrcModel CRC8_ROHC               = new CrcModel("CRC-8/ROHC", 8, 0x7, 0xFF, true, true, 0x0, 0xD0L);
    public static final CrcModel CRC8_SAE_J1850          = new CrcModel("CRC-8/SAE-J1850", 8, 0x1D, 0xFF, false, false, 0xFF, 0x4BL);
    public static final CrcModel CRC8_WCDMA              = new CrcModel("CRC-8/WCDMA", 8, 0x9B, 0x0, true, true, 0x0, 0x25L);

    // CRC-10
    public static final CrcModel CRC10_ATM               = new CrcModel("CRC-10/ATM", 10, 0x233, 0x0, false, false, 0x0, 0x199L);
    public static final CrcModel CRC10_I_610             = alias("CRC-10/I-610", CRC10_ATM);
    public static final CrcModel CRC10                   = alias("CRC-10", CRC10_ATM);
    public static final CrcModel CRC10_CDMA2000          = new CrcModel("CRC-10/CDMA2000", 10, 0x3D9, 0x3FF, false, false, 0x0, 0x233L);
    public static final CrcModel CRC10_GSM               = new CrcModel("CRC-10/GSM", 10, 0x175, 0x0, false, false, 0x3FF, 0x12AL);

    // CRC-11
    public static final CrcModel CRC11_FLEXRAY           = new CrcModel("CRC-11/FLEXRAY", 11, 0x385, 0x1A, false, false, 0x0, 0x5A3L);
    public static final CrcModel CRC11                   = alias("CRC-11", CRC11_FLEXRAY);
    public static final CrcModel CRC11_UMTS              = new CrcModel("CRC-11/UMTS", 11, 0x307, 0x0, false, false, 0x0, 0x061L);

    // CRC-12
    public static final CrcModel CRC12_UMTS              = new CrcModel("CRC-12/UMTS", 12, 0x80F, 0x0, false, true, 0x0, 0xDAFL);
    public static final CrcModel CRC12_3GPP              = alias("CRC-12/3GPP", CRC12_UMTS);
    public static final CrcModel CRC12_CDMA2000          = new CrcModel("CRC-12/CDMA2000", 12, 0xF13, 0xFFF, false, false, 0x0, 0xD4DL);
    public static final CrcModel CRC12_DECT              = new CrcModel("CRC-12/DECT", 12, 0x80F, 0x0, false, false, 0x0, 0xF5BL);
    public static final CrcModel X_CRC_12                = alias("X-CRC-12", CRC12_DECT);
    public static final CrcModel CRC12_GSM               = new CrcModel("CRC-12/GSM", 12, 0xD31, 0x0, false, false, 0xFFF, 0xB34L);

    // CRC-13
    public static final CrcModel CRC13_BBC               = new CrcModel("CRC-13/BBC", 13, 0x1CF5, 0x0, false, false, 0x0, 0x4FAL);

    // CRC-14
    public static final CrcModel CRC14_DARC              = new CrcModel("CRC-14/DARC", 14, 0x805, 0x0, true, true, 0x0, 0x82DL);
    public static final CrcModel CRC14_GSM               = new CrcModel("CRC-14/GSM", 14, 0x202D, 0x0, false, false, 0x3fff, 0x30AEL);

    // CRC-15
    public static final CrcModel CRC15_CAN               = new CrcModel("CRC-15/CAN", 15, 0x4599, 0x0, false, false, 0x0, 0x59EL);
    public static final CrcModel CRC15                   = alias("CRC-15", CRC15_CAN);
    public static final CrcModel CRC15_MPT1327           = new CrcModel("CRC-15/MPT1327", 15, 0x6815, 0x0, false, false, 0x1, 0x2566L);

    // CRC-16
    public static final CrcModel CRC16_ARC               = new CrcModel("CRC-16/ARC", 16, 0x8005, 0x0, true, true, 0x0, 0xBB3DL);
    public static final CrcModel ARC                     = alias("ARC", CRC16_ARC);
    public static final CrcModel CRC16                   = alias("CRC-16", CRC16_ARC);
    public static final CrcModel CRC16_LHA               = alias("CRC-16/LHA", CRC16_ARC);
    public static final CrcModel CRC_IBM                 = alias("CRC-IBM", CRC16_ARC);
    public static final CrcModel CRC16_SPI_FUJITSU       = new CrcModel("CRC-16/SPI-FUJITSU", 16, 0x1021, 0x1D0F, false, false, 0x0, 0xE5CCL);
    public static final CrcModel CRC16_AUG_CCITT         = alias("CRC-16/AUG-CCITT", CRC16_SPI_FUJITSU);
    public static final CrcModel CRC16_UMTS              = new CrcModel("CRC-16/UMTS", 16, 0x8005, 0x0, false, false, 0x0, 0xFEE8L);
    public static final CrcModel CRC16_BUYPASS           = alias("CRC-16/BUYPASS", CRC16_UMTS);
    public static final CrcModel CRC16_VERIFONE          = alias("CRC-16/VERIFONE", CRC16_UMTS);
    public static final CrcModel CRC16_IBM_3740          = new CrcModel("CRC-16/IBM-3740", 16, 0x1021, 0xFFFF, false, false, 0x0, 0x29B1L);
    public static final CrcModel CRC16_AUTOSAR           = alias("CRC-16/AUTOSAR", CRC16_IBM_3740);
    public static final CrcModel CRC16_CCITT_FALSE       = alias("CRC-16/CCITT-FALSE", CRC16_IBM_3740);
    public static final CrcModel CRC16_CDMA2000          = new CrcModel("CRC-16/CDMA2000", 16, 0xC867, 0xFFFF, false, false, 0x0, 0x4C06L);
    public static final CrcModel CRC16_CMS               = new CrcModel("CRC-16/CMS", 16, 0x8005, 0xFFFF, false, false, 0x0, 0xAEE7L);
    public static final CrcModel CRC16_DDS_110           = new CrcModel("CRC-16/DDS-110", 16, 0x8005, 0x800D, false, false, 0x0, 0x9ECFL);
    public static final CrcModel CRC16_DECT_R            = new CrcModel("CRC-16/DECT-R", 16, 0x589, 0x0, false, false, 0x1, 0x7EL);
    public static final CrcModel R_CRC_16                = alias("R-CRC-16", CRC16_DECT_R);
    public static final CrcModel CRC16_DECT_X            = new CrcModel("CRC-16/DECT-X", 16, 0x589, 0x0, false, false, 0x0, 0x7FL);
    public static final CrcModel X_CRC_16                = alias("X-CRC-16", CRC16_DECT_X);
    public static final CrcModel CRC16_DNP               = new CrcModel("CRC-16/DNP", 16, 0x3D65, 0x0, true, true, 0xFFFF, 0xEA82L);
    public static final CrcModel CRC16_EN_13757          = new CrcModel("CRC-16/EN-13757", 16, 0x3D65, 0x0, false, false, 0xFFFF, 0xC2B7L);
    /** @deprecated since 1.0.2, use {@link #CRC16_EN_13757} */
    @Deprecated
    public static final CrcModel CRC16_EN13757           = CRC16_EN_13757;
    public static final CrcModel CRC16_GENIBUS           = new CrcModel("CRC-16/GENIBUS", 16, 0x1021, 0xFFFF, false, false, 0xFFFF, 0xD64EL);
    public static final CrcModel CRC16_DARC              = alias("CRC-16/DARC", CRC16_GENIBUS);
    public static final CrcModel CRC16_EPC               = alias("CRC-16/EPC", CRC16_GENIBUS);
    public static final CrcModel CRC16_EPC_C1G2          = alias("CRC-16/EPC-C1G2", CRC16_GENIBUS);
    public static final CrcModel CRC16_I_CODE            = alias("CRC-16/I-CODE", CRC16_GENIBUS);
    public static final CrcModel CRC16_GSM               = new CrcModel("CRC-16/GSM", 16, 0x1021, 0x0, false, false, 0xFFFF, 0xCE3CL);
    public static final CrcModel CRC16_KERMIT            = new CrcModel("CRC-16/KERMIT", 16, 0x1021, 0x0, true, true, 0x0, 0x2189L);
    public static final CrcModel CRC16_CCITT             = alias("CRC-16/CCITT", CRC16_KERMIT);
    public static final CrcModel CRC16_CCITT_TRUE        = alias("CRC-16/CCITT-TRUE", CRC16_KERMIT);
    public static final CrcModel CRC16_V_41_LSB          = alias("CRC-16/V-41-LSB", CRC16_KERMIT);
    public static final CrcModel CRC_CCITT               = alias("CRC-CCITT", CRC16_KERMIT);
    public static final CrcModel KERMIT                  = alias("KERMIT", CRC16_KERMIT);
    public static final CrcModel CRC16_LJ1200            = new CrcModel("CRC-16/LJ1200", 16, 0x6F63, 0x0, false, false, 0x0, 0xBDF4L);
    public static final CrcModel CRC16_MAXIM_DOW         = new CrcModel("CRC-16/MAXIM-DOW", 16, 0x8005, 0x0, true, true, 0xFFFF, 0x44C2L);
    public static final CrcModel CRC16_MAXIM             = alias("CRC-16/MAXIM", CRC16_MAXIM_DOW);
    public static final CrcModel CRC16_MCRF4XX           = new CrcModel("CRC-16/MCRF4XX", 16, 0x1021, 0xFFFF, true, true, 0x0, 0x6F91L);
    public static final CrcModel CRC16_MODBUS            = new CrcModel("CRC-16/MODBUS", 16, 0x8005, 0xFFFF, true, true, 0x0, 0x4B37L);
    public static final CrcModel MODBUS                  = alias("MODBUS", CRC16_MODBUS);
    public static final CrcModel CRC16_OPENSAFETY_A      = new CrcModel("CRC-16/OPENSAFETY-A", 16, 0x5935, 0x0, false, false, 0x0, 0x5D38L);
    public static final CrcModel CRC16_OPENSAFETY_B      = new CrcModel("CRC-16/OPENSAFETY-B", 16, 0x755B, 0x0, false, false, 0x0, 0x20FEL);
    public static final CrcModel CRC16_PROFIBUS          = new CrcModel("CRC-16/PROFIBUS", 16, 0x1DCF, 0xFFFF, false, false, 0xFFFF, 0xA819L);
    public static final CrcModel CRC16_IEC_61158_2       = alias("CRC-16/IEC-61158-2", CRC16_PROFIBUS);
    public static final CrcModel CRC16_RIELLO            = new CrcModel("CRC-16/RIELLO", 16, 0x1021, 0xB2AA, true, true, 0x0, 0x63D0L);
    public static final CrcModel CRC16_T10_DIF           = new CrcModel("CRC-16/T10-DIF", 16, 0x8BB7, 0x0, false, false, 0x0, 0xD0DBL);
    public static final CrcModel CRC16_TELEDISK          = new CrcModel("CRC-16/TELEDISK", 16, 0xA097, 0x0, false, false, 0x0, 0xFB3L);
    public static final CrcModel CRC16_TMS37157          = new CrcModel("CRC-16/TMS37157", 16, 0x1021, 0x89EC, true, true, 0x0, 0x26B1L);
    public static final CrcModel CRC16_USB               = new CrcModel("CRC-16/USB", 16, 0x8005, 0xFFFF, true, true, 0xFFFF, 0xB4C8L);
    public static final CrcModel CRC16_ISO_IEC_14443_3_A = new CrcModel("CRC-16/ISO-IEC-14443-3-A", 16, 0x1021, 0xc6c6, true, true, 0x0, 0xBF05L);
    public static final CrcModel CRC_A                   = alias("CRC-A", CRC16_ISO_IEC_14443_3_A);
    public static final CrcModel CRC16_XMODEM            = new CrcModel("CRC-16/XMODEM", 16, 0x1021, 0x0, false, false, 0x0, 0x31C3L);
    public static final CrcModel CRC16_ACORN             = alias("CRC-16/ACORN", CRC16_XMODEM);
    public static final CrcModel CRC16_LTE               = alias("CRC-16/LTE", CRC16_XMODEM);
    public static final CrcModel CRC16_V_41_MSB          = alias("CRC-16/V-41-MSB", CRC16_XMODEM);
    public static final CrcModel XMODEM                  = alias("XMODEM", CRC16_XMODEM);
    public static final CrcModel ZMODEM                  = alias("ZMODEM", CRC16_XMODEM);
    public static final CrcModel CRC16_IBM_SDLC          = new CrcModel("CRC-16/IBM-SDLC", 16, 0x1021, 0xFFFF, true, true, 0xFFFF, 0x906EL);
    public static final CrcModel CRC16_ISO_HDLC          = alias("CRC-16/ISO-HDLC", CRC16_IBM_SDLC);
    public static final CrcModel CRC16_ISO_IEC_14443_3_B = alias("CRC-16/ISO-IEC-14443-3-B", CRC16_IBM_SDLC);
    public static final CrcModel CRC16_X_25              = alias("CRC-16/X-25", CRC16_IBM_SDLC);
    public static final CrcModel CRC_B                   = alias("CRC-B", CRC16_IBM_SDLC);
    /** @deprecated since 1.0.2, use {@link #CRC_B} */
    @Deprecated
    public static final CrcModel CRCB                    = CRC_B;
    public static final CrcModel X_25                    = alias("X-25", CRC16_IBM_SDLC);

    // CRC-17
    public static final CrcModel CRC17_CAN_FD            = new CrcModel("CRC-17/CAN-FD", 17, 0x1685B, 0x0, false, false, 0x0, 0x04F03L);

    // CRC-21
    public static final CrcModel CRC21_CAN_FD            = new CrcModel("CRC-21/CAN-FD", 21, 0x102899, 0x0, false, false, 0x0, 0x0ED841L);

    // CRC-24
    public static final CrcModel CRC24_OPENPGP           = new CrcModel("CRC-24/OPENPGP", 24, 0x864CFB, 0xB704CE, false, false, 0x0, 0x21CF02L);
    public static final CrcModel CRC24                   = alias("CRC-24", CRC24_OPENPGP);
    public static final CrcModel CRC24_BLE               = new CrcModel("CRC-24/BLE", 24, 0x65B, 0x555555, true, true, 0x0, 0xC25A56L);
    public static final CrcModel CRC24_FLEXRAY_A         = new CrcModel("CRC-24/FLEXRAY-A", 24, 0x5D6DCB, 0xFEDCBA, false, false, 0x0, 0x7979BDL);
    public static final CrcModel CRC24_FLEXRAY_B         = new CrcModel("CRC-24/FLEXRAY-B", 24, 0x5D6DCB, 0xABCDEF, false, false, 0x0, 0x1F23B8L);
    public static final CrcModel CRC24_INTERLAKEN        = new CrcModel("CRC-24/INTERLAKEN", 24, 0x328B63, 0xFFFFFF, false, false, 0xFFFFFF, 0xB4F3E6L);
    public static final CrcModel CRC24_LTE_A             = new CrcModel("CRC-24/LTE-A", 24, 0x864CFB, 0x0, false, false, 0x0, 0xCDE703L);
    public static final CrcModel CRC24_LTE_B             = new CrcModel("CRC-24/LTE-B", 24, 0x800063, 0x0, false, false, 0x0, 0x23EF52L);

    // CRC-30
    public static final CrcModel CRC30_CDMA              = new CrcModel("CRC-30/CDMA", 30, 0x2030B9C7, 0x3FFFFFFF, false, false , 0x3FFFFFFFL, 0x04C34ABFL);

    // CRC-31
    public static final CrcModel CRC31_PHILIPS           = new CrcModel("CRC-31/PHILIPS", 31, 0x4C11DB7, 0x7FFFFFFF, false, false , 0x7FFFFFFFL, 0xCE9E46CL);

    // CRC-32
    public static final CrcModel CRC32_ISO_HDLC          = new CrcModel("CRC-32/ISO-HDLC", 32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL, 0xCBF43926L);
    public static final CrcModel CRC32                   = alias("CRC-32", CRC32_ISO_HDLC);
    public static final CrcModel CRC32_ADCCP             = alias("CRC-32/ADCCP", CRC32_ISO_HDLC);
    public static final CrcModel CRC32_V_42              = alias("CRC-32/V-42", CRC32_ISO_HDLC);
    /** @deprecated since 1.0.2, use {@link #CRC32_V_42} */
    @Deprecated
    public static final CrcModel CRC32_V42               = CRC32_V_42;
    public static final CrcModel CRC32_XZ                = alias("CRC-32/XZ", CRC32_ISO_HDLC);
    public static final CrcModel PKZIP                   = alias("PKZIP", CRC32_ISO_HDLC);
    public static final CrcModel CRC32_AUTOSAR           = new CrcModel("CRC-32/AUTOSAR", 32, 0xF4ACFB13L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL, 0x1697D06AL);
    public static final CrcModel CRC32_BZIP2             = new CrcModel("CRC-32/BZIP2", 32, 0x04C11DB7L, 0xFFFFFFFFL, false, false, 0xFFFFFFFFL, 0xFC891918L);
    public static final CrcModel CRC32_AAL5              = alias("CRC-32/AAL5", CRC32_BZIP2);
    public static final CrcModel CRC32_DECT_B            = alias("CRC-32/DECT-B", CRC32_BZIP2);
    public static final CrcModel B_CRC_32                = alias("B-CRC-32", CRC32_BZIP2);
    public static final CrcModel CRC32_ISCSI             = new CrcModel("CRC-32/ISCSI", 32, 0x1EDC6F41L, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL, 0xE3069283L);
    public static final CrcModel CRC32_BASE91_C          = alias("CRC-32/BASE91-C", CRC32_ISCSI);
    public static final CrcModel CRC32_CASTAGNOLI        = alias("CRC-32/CASTAGNOLI", CRC32_ISCSI);
    public static final CrcModel CRC32_INTERLAKEN        = alias("CRC-32/INTERLAKEN", CRC32_ISCSI);
    public static final CrcModel CRC32_C                 = alias("CRC-32C", CRC32_ISCSI);
    public static final CrcModel CRC32_BASE91_D          = new CrcModel("CRC-32/BASE91-D", 32, 0xA833982BL, 0xFFFFFFFFL, true, true, 0xFFFFFFFFL, 0x87315576L);
    public static final CrcModel CRC32_D                 = alias("CRC-32D", CRC32_BASE91_D);
    public static final CrcModel CRC32_JAMCRC            = new CrcModel("CRC-32/JAMCRC", 32, 0x04C11DB7L, 0xFFFFFFFFL, true, true, 0x00000000L, 0x340BC6D9L);
    public static final CrcModel JAMCRC                  = alias("JAMCRC", CRC32_JAMCRC);
    public static final CrcModel CRC32_MPEG_2            = new CrcModel("CRC-32/MPEG-2", 32, 0x04C11DB7L, 0xFFFFFFFFL, false, false, 0x00000000L, 0x0376E6E7L);
    public static final CrcModel CRC32_CKSUM             = new CrcModel("CRC-32/CKSUM", 32, 0x04C11DB7L, 0x00000000L, false, false, 0xFFFFFFFFL, 0x765E7680L);
    public static final CrcModel CKSUM                   = alias("CKSUM", CRC32_CKSUM);
    public static final CrcModel CRC32_POSIX             = alias("CRC-32/POSIX", CRC32_CKSUM);
    public static final CrcModel CRC32_AIXM              = new CrcModel("CRC-32/AIXM", 32, 0x814141ABL, 0x00000000L, false, false, 0x00000000L, 0x3010BF7FL);
    public static final CrcModel CRC32_Q                 = alias("CRC-32Q", CRC32_AIXM);
    public static final CrcModel CRC32_XFER              = new CrcModel("CRC-32/XFER", 32, 0x000000AFL, 0x00000000L, false, false, 0x00000000L, 0xBD0BE338L);
    public static final CrcModel XFER                    = alias("XFER", CRC32_XFER);

    // CRC-40

    public static final CrcModel CRC40_GSM               = new CrcModel("CRC-40/GSM", 40, 0x4820009, 0x0, false, false, 0xFFFFFFFFFFL,  0xD4164FC646L);

    // CRC-64

    public static final CrcModel CRC64_GO_ISO            = new CrcModel("CRC-64/GO-ISO",64, 0x000000000000001BL, 0xFFFFFFFFFFFFFFFFL, true, true, 0xFFFFFFFFFFFFFFFFL, 0xB90956C775A41001L);
    public static final CrcModel CRC64_ECMA_182          = new CrcModel("CRC-64/ECMA-182",64, 0x42F0E1EBA9EA3693L, 0x00000000L, false, false, 0x00000000L, 0x6C40DF5F0B497347L);
    public static final CrcModel CRC64                   = alias("CRC-64", CRC64_ECMA_182);
    public static final CrcModel CRC64_WE                = new CrcModel("CRC-64/WE", 64, 0x42F0E1EBA9EA3693L, 0xFFFFFFFFFFFFFFFFL, false, false, 0xFFFFFFFFFFFFFFFFL,0x62EC59E3F1A4F00AL);
    public static final CrcModel CRC64_XZ                = new CrcModel("CRC-64/XZ", 64, 0x42F0E1EBA9EA3693L, 0xFFFFFFFFFFFFFFFFL, true, true, 0xFFFFFFFFFFFFFFFFL,0x995DC9BBDF1939FAL);
    public static final CrcModel CRC64_GO_ECMA           = alias("CRC-64/GO-ECMA", CRC64_XZ);

}