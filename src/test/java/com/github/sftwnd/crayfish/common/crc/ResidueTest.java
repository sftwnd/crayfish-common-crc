/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The residue: the residues of the constants of StandardCrc against the snapshot of the catalogue and against the
 * calculation of the library, the residues of other parameters against both definitions of the catalogue, calculated
 * without the code of the library
 */
class ResidueTest {

    // the lengths of the data of the error-free codewords
    private static final int[] LENGTHS = {0, 1, 3, 8, 21};

    // the lines of the algorithms of the snapshot of the catalogue
    private static List<String> catalogue() throws IOException {
        List<String> lines = new ArrayList<>();
        try (InputStream stream = Objects.requireNonNull(ResidueTest.class.getResourceAsStream("reveng-catalogue.txt"));
             BufferedReader reader = new BufferedReader(new InputStreamReader(stream, UTF_8))) {
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (!line.startsWith("#")) {
                    lines.add(line);
                }
            }
        }
        return lines;
    }

    // the value of a field of a line of the catalogue: the text after "key=" up to the next space, without the quotes
    private static String field(String line, String key) {
        int start = line.indexOf(key + "=") + key.length() + 1;
        int end = line.indexOf(' ', start);
        return line.substring(start, end < 0 ? line.length() : end).replace("\"", "");
    }

    // parameter sets outside the catalogue: every width with every reflection, random polynomials, inits and xorouts
    private static List<CrcParameters> otherParameters() {
        Random random = new Random(125);
        List<CrcParameters> parameters = new ArrayList<>();
        for (int width = 1; width <= 64; width++) {
            long mask = -1L >>> (Long.SIZE - width);
            for (int kind = 0; kind < 4; kind++) {
                CrcParameters other = CrcParameters.of(width, random.nextLong() & mask, random.nextLong() & mask, (kind & 1) != 0, (kind & 2) != 0,
                        random.nextLong() & mask);
                if (!StandardCrc.find(other).isPresent()) {
                    parameters.add(other);
                }
            }
        }
        return parameters;
    }

    // the bits of the width in the reverse order, a bit at a time
    private static long reflect(long value, int width) {
        long reflected = 0;
        for (int bit = 0; bit < width; bit++) {
            reflected = (reflected << 1) | ((value >>> bit) & 1);
        }
        return reflected;
    }

    private static BigInteger unsigned(long value) {
        return new BigInteger(Long.toUnsignedString(value));
    }

    /* the residue by the equivalent definition of the catalogue as a division of polynomials over GF(2), without
       the code of the library: xorout, reflected if refout, times x^width modulo the polynomial with its term x^width,
       reflected if refin */
    private static long divided(CrcParameters parameters) {
        int width = parameters.width();
        BigInteger remainder = unsigned(parameters.refout() ? reflect(parameters.xorout(), width) : parameters.xorout()).shiftLeft(width);
        BigInteger divisor = unsigned(parameters.poly()).setBit(width);
        for (int bit = 2 * width - 1; bit >= width; bit--) {
            if (remainder.testBit(bit)) {
                remainder = remainder.xor(divisor.shiftLeft(bit - width));
            }
        }
        long residue = remainder.longValue();
        return parameters.refin() ? reflect(residue, width) : residue;
    }

    // the register of the width after a bit of a codeword: shifted, and the polynomial XOR-ed if the bit differs from
    // the bit shifted out
    private static long read(CrcParameters parameters, long register, long bit) {
        int width = parameters.width();
        long shifted = (register << 1) & (-1L >>> (Long.SIZE - width));
        return ((register >>> (width - 1)) & 1) != bit ? shifted ^ parameters.poly() : shifted;
    }

    /* the register after an error-free codeword by the definition of the catalogue, a bit at a time without the code
       of the library: the register initialised with init reads the bits of the data (every byte the least significant
       bit first if refin) and the width bits of its CRC (the least significant bit first if refout), and is reflected
       if refout. Both definitions of the catalogue agree for refin = refout; for refin != refout in general only for
       xorout 0, the residue 0 of CRC-12/UMTS */
    private static long afterCodeword(CrcParameters parameters, byte[] data) {
        int width = parameters.width();
        long register = parameters.init();
        for (byte b : data) {
            for (int i = 0; i < Byte.SIZE; i++) {
                register = read(parameters, register, (b >>> (parameters.refin() ? i : Byte.SIZE - 1 - i)) & 1);
            }
        }
        long crc = CrcReference.crc(parameters, data);
        for (int i = 0; i < width; i++) {
            register = read(parameters, register, (crc >>> (parameters.refout() ? i : width - 1 - i)) & 1);
        }
        return parameters.refout() ? reflect(register, width) : register;
    }

    @Test
    void testResiduesOfTheCatalogue() throws IOException {
        int algorithms = 0;
        int residues = 0;
        for (String line : catalogue()) {
            String name = field(line, "name");
            if (Integer.parseInt(field(line, "width")) > 64) {
                assertFalse(StandardCrc.find(name).isPresent(), name + " is wider than 64 bits and has not got to be predefined");
                continue;
            }
            long residue = Long.parseUnsignedLong(field(line, "residue").substring(2), 16);
            StandardCrc standard = StandardCrc.find(name).orElseThrow(() -> new AssertionError(name + " has to be predefined"));
            assertEquals(residue, standard.residue(), name + ": the constant has to keep the residue of the catalogue");
            assertEquals(residue, standard.parameters().residueValue(), name + ": the calculated residue has to be the residue of the catalogue");
            assertEquals(residue, divided(standard.parameters()), name + ": the residue of the catalogue has to be the residue of the definition");
            for (String alias : standard.names()) {
                assertEquals(residue, CrcModel.of(alias).residue(), alias + ": the model has to have the residue of the catalogue");
            }
            CrcDescription description = standard.model().description();
            assertEquals(residue, description.residue(), name + ": the description has to have the residue of the catalogue");
            int aliases = line.indexOf("  alias=");
            assertEquals(aliases < 0 ? line : line.substring(0, aliases), description.toString(),
                    name + ": the description has to be the parameter line of the catalogue without the aliases");
            algorithms++;
            if (residue != 0) {
                residues++;
            }
        }
        assertEquals(StandardCrc.values().length, algorithms, "Every predefined algorithm has to be an algorithm of the catalogue");
        assertEquals(36, residues, "36 algorithms of the catalogue have got a residue other than 0");
    }

    @Test
    void testResiduesOfOtherParameters() {
        for (CrcParameters parameters : otherParameters()) {
            CrcModel model = CrcModel.of(parameters);
            assertFalse(model.description().standard().isPresent(), model + " has to be outside the catalogue");
            assertEquals(divided(parameters), model.residue(), model + ": wrong residue");
            assertEquals(model.residue(), model.description().residue(), model + ": the model has to have the residue of its description");
            CrcParameters noXorout = CrcParameters.of(parameters.width(), parameters.poly(), parameters.init(), parameters.refin(), parameters.refout(), 0x0L);
            assertEquals(0L, CrcModel.of(noXorout).residue(), noXorout + ": the residue of xorout 0 has to be 0");
        }
    }

    @Test
    void testRegisterAfterAnErrorFreeCodeword() {
        List<CrcParameters> parameters = new ArrayList<>();
        for (StandardCrc standard : StandardCrc.values()) {
            parameters.add(standard.parameters());
        }
        for (CrcParameters other : otherParameters()) {
            if (other.refin() == other.refout()) {
                parameters.add(other);
            }
        }
        Random random = new Random(1250);
        for (CrcParameters crc : parameters) {
            CrcModel model = CrcModel.of(crc);
            for (int length : LENGTHS) {
                byte[] data = new byte[length];
                random.nextBytes(data);
                assertEquals(afterCodeword(crc, data), model.residue(), model + ": the residue has to be the register after an error-free codeword of "
                        + length + " bytes of data");
            }
        }
    }

}
