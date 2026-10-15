/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        StringBuilder hex = new StringBuilder("0x");
        String digits = Long.toHexString(value);
        for (int i = digits.length(); i < (width + 3) / 4; i++) {
            hex.append('0');
        }
        return hex.append(digits).toString();
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
    static @Nullable CrcParameters parse(@NonNull final String name) {
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
