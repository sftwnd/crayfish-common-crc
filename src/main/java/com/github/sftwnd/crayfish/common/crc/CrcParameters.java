/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * The parameters of a CRC algorithm in the Rocksoft model of Ross Williams, as the
 * <a href="https://reveng.sourceforge.io/crc-catalogue/all.htm">RevEng catalogue</a> gives them: width, poly, init,
 * refin, refout and xorout. An immutable value: parameters are equal if all six values are equal.
 *
 * @since 2.0.0
 * @author Andrey D. Shindarev
 */
public final class CrcParameters {

    /** Data of the check value: the check value of a CRC algorithm is the CRC of the ASCII bytes of this string */
    public static final String CHECK_INPUT = "123456789";

    private final int width;
    private final long poly;
    private final long init;
    private final boolean refin;
    private final boolean refout;
    private final long xorout;
    // the parameters are the key of the pool of the descriptions: the hash code is calculated once
    private final int hash;

    private CrcParameters(final int width, final long poly, final long init, final boolean refin, final boolean refout, final long xorout) {
        this.width = width;
        this.poly = poly;
        this.init = init;
        this.refin = refin;
        this.refout = refout;
        this.xorout = xorout;
        int h = Integer.hashCode(width);
        h = 31 * h + Long.hashCode(poly);
        h = 31 * h + Long.hashCode(init);
        h = 31 * h + Boolean.hashCode(refin);
        h = 31 * h + Boolean.hashCode(refout);
        this.hash = 31 * h + Long.hashCode(xorout);
    }

    /**
     * Create the parameters of a CRC algorithm
     * @param width CRC width in bits: 1..64
     * @param poly polynomial, without the x^width term: for example 0x04C11DB7 for CRC-32, not 0x104C11DB7
     * @param init initial value of the CRC register
     * @param refin true if the input bytes are reflected
     * @param refout true if the CRC is reflected before the final XOR
     * @param xorout value XOR-ed to the CRC at the end
     * @return parameters of the CRC algorithm
     * @throws IllegalArgumentException if the width is not in 1..64, or if poly, init or xorout do not fit into width bits
     */
    public static @NonNull CrcParameters of(final int width, final long poly, final long init, final boolean refin, final boolean refout, final long xorout) {
        String source = "CrcParameters::of";
        checkWidth(source, width);
        checkFits(source, "poly", poly, width);
        checkFits(source, "init", init, width);
        checkFits(source, "xorout", xorout, width);
        return new CrcParameters(width, poly, init, refin, refout, xorout);
    }

    /**
     * Parse the parameters of a CRC algorithm from a parameter line of the catalogue, the format of {@link #toString()},
     * for example {@code width=16  poly=0x8005  init=0x0000  refin=true  refout=true  xorout=0x0000}. The fields
     * {@code name=value} are separated by whitespace, in any order, each at most once. The parameters are required:
     * {@code width} (decimal, 1..64), {@code poly}, {@code init} and {@code xorout} (hexadecimal with {@code 0x}, digits
     * of any case, fitting into the width), {@code refin} and {@code refout} ({@code true} or {@code false}). The other
     * fields of the parameter lines of the catalogue are optional: {@code check} (hexadecimal, verified: the CRC of
     * {@link #CHECK_INPUT}), {@code residue} (hexadecimal, verified: the residue of the parameters, see
     * {@link CrcDescription#residue()}) and {@code name} (a quoted string, which may contain whitespace; ignored, as the
     * parameters have no name), so the format of {@link CrcDescription#toString()} is parsed as well. The parameters of
     * an algorithm of the catalogue give the model with its name, see {@link CrcModel#of(CrcParameters)}
     * @param line parameter line
     * @return parameters of the line
     * @throws IllegalArgumentException if a field is unknown, given twice or missing, if a value is not valid or does
     *         not fit into the width, if the check value is not the CRC of {@link #CHECK_INPUT}, or if the residue is not
     *         the residue of the parameters
     * @since 2.1.0
     */
    public static @NonNull CrcParameters parse(@NonNull final String line) {
        Map<Field, String> fields = fields(Objects.requireNonNull(line, PARSE + " - line is null"));
        int width = parseWidth(fields.get(Field.WIDTH));
        CrcParameters parameters = new CrcParameters(width, parseHex(fields, Field.POLY, width), parseHex(fields, Field.INIT, width),
                parseFlag(fields, Field.REFIN), parseFlag(fields, Field.REFOUT), parseHex(fields, Field.XOROUT, width));
        if (fields.containsKey(Field.CHECK)) {
            verifyCheck(parameters, parseHex(fields, Field.CHECK, width));
        }
        if (fields.containsKey(Field.RESIDUE)) {
            verifyResidue(parameters, parseHex(fields, Field.RESIDUE, width));
        }
        // the name is ignored: the parameters have no name
        String name = fields.get(Field.NAME);
        if (name != null && name.charAt(0) != '"') {
            throw new IllegalArgumentException(PARSE + " - name is not a quoted string: " + name);
        }
        return parameters;
    }

    private static final String PARSE = "CrcParameters::parse";

    // a field of a parameter line from the start of the region on: the name, '=' and the value, quoted (whitespace
    // allowed inside the quotes) or without whitespace and quotes, followed by whitespace or the end of the line
    private static final Pattern FIELD = Pattern.compile("([^\\s=\"]++)=(\"[^\"]*+\"|[^\\s\"]++)(?:\\s++|$)");
    private static final Pattern DECIMAL = Pattern.compile("\\d++");
    private static final Pattern HEXADECIMAL = Pattern.compile("0x\\p{XDigit}++");

    // the fields of a parameter line: the parameters in the order of toString(), which are required, then the other
    // fields of the parameter lines of the catalogue
    private enum Field {
        WIDTH, POLY, INIT, REFIN, REFOUT, XOROUT, CHECK, RESIDUE, NAME;

        // the name of the field in a line
        final String key = name().toLowerCase(Locale.ROOT);
    }

    private static final Set<Field> REQUIRED = EnumSet.range(Field.WIDTH, Field.XOROUT);

    // the values of the fields of a parameter line: every field is known and given once, every parameter is given
    private static @NonNull Map<Field, String> fields(@NonNull final String line) {
        Map<Field, String> fields = new EnumMap<>(Field.class);
        String text = line.trim();
        Matcher matcher = FIELD.matcher(text);
        for (int position = 0; position < text.length(); position = matcher.end()) {
            if (!matcher.region(position, text.length()).lookingAt()) {
                throw new IllegalArgumentException(PARSE + " - not a field name=value: " + text.substring(position));
            }
            Field field = field(matcher.group(1));
            if (fields.put(field, matcher.group(2)) != null) {
                throw new IllegalArgumentException(PARSE + " - field given twice: " + field.key);
            }
        }
        String missing = REQUIRED.stream().filter(field -> !fields.containsKey(field)).map(field -> field.key)
                .collect(Collectors.joining(", "));
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(PARSE + " - missing: " + missing);
        }
        return fields;
    }

    // the field of a name in a line
    private static @NonNull Field field(@NonNull final String key) {
        for (Field field : Field.values()) {
            if (field.key.equals(key)) {
                return field;
            }
        }
        throw new IllegalArgumentException(PARSE + " - unknown field: " + key);
    }

    // the width of a line: a decimal number in 1..64
    private static int parseWidth(@NonNull final String text) {
        if (!DECIMAL.matcher(text).matches()) {
            throw new IllegalArgumentException(PARSE + " - width is not a decimal number: " + text);
        }
        // the digits are read until the width exceeds 64, so that a longer number does not overflow
        int width = 0;
        for (int i = 0; i < text.length() && width <= 64; i++) {
            width = width * 10 + text.charAt(i) - '0';
        }
        if (width < 1 || width > 64) {
            throw new IllegalArgumentException(PARSE + " - width has to be in 1..64: " + text);
        }
        return width;
    }

    // a hexadecimal value of a line: 0x followed by digits of any case, with any number of leading zeros, fitting
    // into the width
    private static long parseHex(@NonNull final Map<Field, String> fields, @NonNull final Field field, final int width) {
        String text = fields.get(field);
        if (!HEXADECIMAL.matcher(text).matches()) {
            throw new IllegalArgumentException(PARSE + " - " + field.key + " is not a hexadecimal number with 0x: " + text);
        }
        BigInteger value = new BigInteger(text.substring(2), 16);
        if (value.bitLength() > width) {
            String hint = field == Field.POLY ? " (poly without the x^" + width + " term)" : "";
            throw new IllegalArgumentException(PARSE + " - " + field.key + " " + text + " does not fit into the width " + width + hint);
        }
        return value.longValue();
    }

    // a flag of a line: true or false
    private static boolean parseFlag(@NonNull final Map<Field, String> fields, @NonNull final Field field) {
        String text = fields.get(field);
        boolean flag = "true".equals(text);
        if (!flag && !"false".equals(text)) {
            throw new IllegalArgumentException(PARSE + " - " + field.key + " is not true or false: " + text);
        }
        return flag;
    }

    // the check value of a line has to be the CRC of CHECK_INPUT, calculated a bit at a time as for a builder with
    // a check value; CrcDescription is not used, so that a line is parsed without the pool of the descriptions
    private static void verifyCheck(@NonNull final CrcParameters parameters, final long check) {
        long actual = new CrcEngine(parameters).bitwise(CHECK_INPUT.getBytes(StandardCharsets.US_ASCII));
        if (actual != check) {
            throw new IllegalArgumentException(PARSE + " - wrong check value " + hex(check, parameters.width) + " for " + parameters
                    + ": the CRC of \"" + CHECK_INPUT + "\" is " + hex(actual, parameters.width));
        }
    }

    // the residue of a line has to be the residue of the parameters, calculated as the catalogue does, without the
    // pool of the descriptions as well
    private static void verifyResidue(@NonNull final CrcParameters parameters, final long residue) {
        long actual = parameters.residueValue();
        if (actual != residue) {
            throw new IllegalArgumentException(PARSE + " - wrong residue " + hex(residue, parameters.width) + " for " + parameters
                    + ": the residue of the parameters is " + hex(actual, parameters.width));
        }
    }

    /**
     * Get the CRC width
     * @return CRC width in bits: 1..64
     */
    public int width() {
        return this.width;
    }

    /**
     * Get the polynomial
     * @return polynomial, without the x^width term
     */
    public long poly() {
        return this.poly;
    }

    /**
     * Get the initial value of the CRC register
     * @return initial value of the CRC register, not reflected
     */
    public long init() {
        return this.init;
    }

    /**
     * Whether the input bytes are reflected
     * @return true if the input bytes are reflected
     */
    public boolean refin() {
        return this.refin;
    }

    /**
     * Whether the CRC is reflected before the final XOR
     * @return true if the CRC is reflected before the final XOR
     */
    public boolean refout() {
        return this.refout;
    }

    /**
     * Get the value XOR-ed to the CRC at the end
     * @return value XOR-ed to the CRC at the end
     */
    public long xorout() {
        return this.xorout;
    }

    /**
     * Parameters are equal if all six values are equal
     * @param o object to compare with
     * @return true if o are parameters with the same values
     */
    // @CheckForNull, as SpotBugs takes the parameter of Object.equals: @Nullable would tighten it
    @Override
    public boolean equals(@CheckForNull final Object o) {
        if (o == this) {
            return true;
        }
        if (!(o instanceof CrcParameters)) {
            return false;
        }
        final CrcParameters other = (CrcParameters) o;
        return this.width == other.width
            && this.poly == other.poly
            && this.init == other.init
            && this.refin == other.refin
            && this.refout == other.refout
            && this.xorout == other.xorout;
    }

    @Override
    public int hashCode() {
        return this.hash;
    }

    /**
     * The parameters in the format of the parameter lines of the catalogue,
     * for example {@code width=16  poly=0x8005  init=0x0000  refin=true  refout=true  xorout=0x0000}
     * @return parameters as string
     */
    @Override
    public @NonNull String toString() {
        return "width=" + width
             + "  poly=" + hex(poly, width)
             + "  init=" + hex(init, width)
             + "  refin=" + refin
             + "  refout=" + refout
             + "  xorout=" + hex(xorout, width);
    }

    // a value of the width in the format of the catalogue: lower case, all digits of the width
    static @NonNull String hex(final long value, final int width) {
        return "0x" + digits(value, width);
    }

    // the hexadecimal digits of a value of the width: lower case, all digits of the width, with the leading zeros;
    // the digits of a value with bits above the width as well
    static @NonNull String digits(final long value, final int width) {
        StringBuilder hex = new StringBuilder();
        String digits = Long.toHexString(value);
        for (int i = digits.length(); i < (width + 3) / 4; i++) {
            hex.append('0');
        }
        return hex.append(digits).toString();
    }

    // the residue of the parameters as the catalogue calculates it, a bit at a time: the register of the width
    // initialised with xorout, reflected if refout, after as many zero bits as the width, reflected if refin. A method
    // of the parameters, so that the residue of a parameter line is verified without the pool of the descriptions
    long residueValue() {
        int shift = Long.SIZE - this.width;
        long mask = mask(this.width);
        long register = this.refout ? Long.reverse(this.xorout) >>> shift : this.xorout;
        for (int bit = 0; bit < this.width; bit++) {
            // a zero bit: the register is shifted, and the polynomial XOR-ed if the bit shifted out is set
            register = ((register << 1) & mask) ^ (this.poly & -(register >>> (this.width - 1)));
        }
        return this.refin ? Long.reverse(register) >>> shift : register;
    }

    // the name of the parameters outside the catalogue: the format of the names of the unregistered models of 1.x,
    // CRC-{width}/P{poly}_I{init}[_RI][_RO][_X{xorout}] with upper case digits and without leading zeros.
    // The names of the catalogue contain no '_', so the name differs from them
    @NonNull String parametricName() {
        StringBuilder name = new StringBuilder("CRC-").append(width)
                .append("/P").append(upperHex(poly))
                .append("_I").append(upperHex(init));
        if (refin) {
            name.append("_RI");
        }
        if (refout) {
            name.append("_RO");
        }
        if (xorout != 0) {
            name.append("_X").append(upperHex(xorout));
        }
        return name.toString();
    }

    // the hexadecimal digits a-f of Long.toHexString have no locale dependent upper case
    private static @NonNull String upperHex(final long value) {
        return Long.toHexString(value).toUpperCase(Locale.ROOT);
    }

    private static final Pattern PARAMETRIC_NAME =
            Pattern.compile("CRC-([1-9]\\d?)/P(\\p{XDigit}{1,16})_I(\\p{XDigit}{1,16})(_RI)?(_RO)?(?:_X(\\p{XDigit}{1,16}))?");

    // the parameters of a parametric name, null if the name is not a parametric name in its canonical form:
    // every parameter set has exactly one name
    static @Nullable CrcParameters parseName(@NonNull final String name) {
        Matcher matcher = PARAMETRIC_NAME.matcher(name);
        if (!matcher.matches()) {
            return null;
        }
        int width = Integer.parseInt(matcher.group(1));
        if (width > 64) {
            return null;
        }
        long poly = Long.parseUnsignedLong(matcher.group(2), 16);
        long init = Long.parseUnsignedLong(matcher.group(3), 16);
        long xorout = matcher.group(6) == null ? 0 : Long.parseUnsignedLong(matcher.group(6), 16);
        long mask = mask(width);
        if (((poly | init | xorout) & ~mask) != 0) {
            return null;
        }
        CrcParameters parameters = new CrcParameters(width, poly, init, matcher.group(4) != null, matcher.group(5) != null, xorout);
        return parameters.parametricName().equals(name) ? parameters : null;
    }

    // the bits of the width
    static long mask(final int width) {
        return -1L >>> (Long.SIZE - width);
    }

    static void checkWidth(@NonNull final String source, final int width) {
        if (width < 1 || width > 64) {
            throw new IllegalArgumentException(source + " - width has to be in 1..64: " + width);
        }
    }

    // a parameter with bits above the width gives wrong CRC values
    static long checkFits(@NonNull final String source, @NonNull final String parameter, final long value, final int width) {
        long mask = mask(width);
        if ((value & ~mask) != 0) {
            String hint;
            if ((value | mask) == -1L) {
                // all bits above the width are set: typically an int literal such as 0xFFFFFFFF, which is -1
                hint = " (an int literal is sign-extended: use a long literal such as 0x" + upperHex(value & mask) + "L)";
            } else {
                hint = "poly".equals(parameter) ? " (poly without the x^" + width + " term)" : "";
            }
            throw new IllegalArgumentException(source + " - " + parameter + " 0x" + upperHex(value)
                    + " does not fit into the width " + width + hint);
        }
        return value;
    }

}
