/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */

/**
 * CRC calculation for every CRC of the Rocksoft model up to 64 bits, with the algorithms of the
 * <a href="https://reveng.sourceforge.io/crc-catalogue/all.htm">RevEng catalogue</a> predefined: the CRC of arrays,
 * buffers, streams and bits, combined and continued, and the check of codewords, data followed by their CRC.
 * <ul>
 * <li>{@link com.github.sftwnd.crayfish.common.crc.CrcParameters} - the parameters of a CRC algorithm: width, poly,
 * init, refin, refout and xorout, also parsed from a parameter line of the catalogue;</li>
 * <li>{@link com.github.sftwnd.crayfish.common.crc.StandardCrc} - the algorithms of the catalogue with their names,
 * aliases, parameters, check values and residues, and the algorithms that give the CRCs of samples of data;</li>
 * <li>{@link com.github.sftwnd.crayfish.common.crc.CrcDescription} - the one description of a parameter set: its names,
 * its check value, its residue and the lookup tables of the calculation, built on first use and collected with the
 * description when nobody uses it;</li>
 * <li>{@link com.github.sftwnd.crayfish.common.crc.CrcModel} - a description with the name it was chosen by,
 * by a name, by the parameters or with a builder, calculated by the lookup tables of the library or by a class of
 * the JDK;</li>
 * <li>{@link com.github.sftwnd.crayfish.common.crc.CRC} - the immutable CRC of data: an update or a combination
 * gives a new CRC, and its value is given as bytes in a byte order;</li>
 * <li>{@link com.github.sftwnd.crayfish.common.crc.CrcChecksum} - the mutable {@link java.util.zip.Checksum} of a model,
 * for {@link java.util.zip.CheckedOutputStream}, {@link java.util.zip.CheckedInputStream} and other code that works
 * with a {@code Checksum}.</li>
 * </ul>
 * <pre>{@code
 * CrcModel model = CrcModel.of("CRC-32C");
 * long single = model.value(data);   // the CRC value of one buffer
 * CRC crc = model.crc().update(part1).update(part2);
 * long value = crc.value();
 * boolean valid = model.isValid(codeword);   // data followed by its CRC
 * }</pre>
 */
package com.github.sftwnd.crayfish.common.crc;
