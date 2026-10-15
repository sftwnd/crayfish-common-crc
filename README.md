# Crayfish Common CRC

[![Maven Central](https://img.shields.io/maven-central/v/com.github.sftwnd.crayfish.common/crayfish-common-crc)](https://central.sonatype.com/artifact/com.github.sftwnd.crayfish.common/crayfish-common-crc)
[![Java 8+](https://img.shields.io/badge/Java-8%2B-blue)](#java-modules)
[![JPMS](https://img.shields.io/badge/JPMS-named%20module-blue)](#java-modules)
[![javadoc](https://javadoc.io/badge2/com.github.sftwnd.crayfish.common/crayfish-common-crc/javadoc.svg)](https://javadoc.io/doc/com.github.sftwnd.crayfish.common/crayfish-common-crc)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=sftwnd_crayfish_common_crc&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=sftwnd_crayfish_common_crc)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=sftwnd_crayfish_common_crc&metric=coverage)](https://sonarcloud.io/summary/new_code?id=sftwnd_crayfish_common_crc)
[![Build](https://github.com/sftwnd/crayfish-common-crc/actions/workflows/build.yml/badge.svg)](https://github.com/sftwnd/crayfish-common-crc/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/sftwnd/crayfish-common-crc)](https://github.com/sftwnd/crayfish-common-crc/blob/master/LICENSE)

**Fast, zero-dependency CRC library for Java 8+: every CRC from CRC-3 to CRC-64 of the RevEng catalogue — and any custom one.**

112 predefined CRC algorithms (186 names with aliases): CRC-32, CRC-32C, CRC-16/MODBUS, CRC-16/CCITT-FALSE,
CRC-16/XMODEM, CRC-8, CRC-64/ECMA-182, CRC-64/NVME and many more. Parameters and check values are those of the
[RevEng CRC catalogue](https://reveng.sourceforge.io/crc-catalogue/all.htm).

This is the README of 2.0, which is in development (`2.0.0-SNAPSHOT`). Its API is not compatible with 1.x, see
[Migrating from 1.x](#migrating-from-1x). The latest release is 1.1.0, its README is that of the tag
[crayfish-common-crc-1.1.0](https://github.com/sftwnd/crayfish-common-crc/blob/crayfish-common-crc-1.1.0/README.md).

## Features

- **Fast** — table-driven, slicing-by-8 for every model, not only for CRC-32: 8 bytes per step, 2–4 times faster than
  one byte per step on buffers from 256 bytes
- **Immutable** — a CRC is a value: an update or a combination gives a new CRC, so a CRC can be shared between threads
  and kept as the state of a calculation (*2.0*)
- **Combine and resume** — join the CRCs of parts calculated independently (in parallel threads or on other nodes),
  like zlib `crc32_combine`; continue a calculation from a saved CRC value and length, also of 2 GiB and more
- **Streams** — a `java.util.zip.Checksum` of any model for `CheckedOutputStream`, `CheckedInputStream` and other code
  written for `CRC32` (*2.0*)
- **Names and aliases** — a model is chosen by the name or by any alias of the catalogue, and its CRCs tell the name
  it was chosen by; other parameters get a unique name of their own (*2.0*)
- **Memory on demand** — one description per parameter set; its lookup tables are built on the first calculation and
  collected with it when no model of the parameters is used any more (*2.0*)
- **Java 8+ and modular** — runs on Java 8; on Java 9+ it is the named JPMS module `com.github.sftwnd.crayfish_common_crc`
  of a multi-release jar; OSGi bundle headers included
- **Zero runtime dependencies** — a single small jar
- **Verified** — every predefined model is tested against its RevEng check value and a bit-by-bit reference implementation
- **Any CRC up to 64 bits** — width 1…64, any poly, init, refin, refout, xorout
- **BSD 3-Clause** license

## Installation

Maven:

```xml
<dependency>
    <groupId>com.github.sftwnd.crayfish.common</groupId>
    <artifactId>crayfish-common-crc</artifactId>
    <version>2.0.0</version>
</dependency>
```

Gradle:

```groovy
implementation 'com.github.sftwnd.crayfish.common:crayfish-common-crc:2.0.0'
```

## Quick start

```java
    byte[] data = "123456789".getBytes(StandardCharsets.US_ASCII);
    long modbus = CrcModel.of("CRC-16/MODBUS").value(data);   // 0x4B37
    long crc32c = CrcModel.of("CRC-32C").value(data);         // 0xE3069283
```

## Concepts

| Type | What it is |
|---|---|
| `CrcParameters` | the parameters of a CRC algorithm: width, poly, init, refin, refout, xorout |
| `StandardCrc` | the 112 algorithms of the catalogue: name, aliases, parameters, check value |
| `CrcDescription` | the one description of a parameter set: names, check value, lookup tables |
| `CrcModel` | a description with the name it was chosen by |
| `CRC` | the immutable CRC of data: model, value, length |
| `CrcChecksum` | the mutable `java.util.zip.Checksum` of a model, for streams |

## Models

### By name

```java
    CrcModel crc32 = CrcModel.of("CRC-32/ISO-HDLC");
    CrcModel pkzip = CrcModel.of("PKZIP");                     // an alias of CRC-32/ISO-HDLC
    Optional<CrcModel> model = CrcModel.find(configuredName);  // empty for an unknown name
    CrcModel modbus = StandardCrc.CRC_16_MODBUS.model();
```

`CrcModel.of()` throws `IllegalArgumentException` for an unknown name. The case of a name matters. The constants of
`StandardCrc` are the names of the algorithms with `_` for every character other than `A-Z` and `0-9`.

A model chosen by its name costs a lookup in the catalogue and in the pool of the descriptions (see [Memory](#memory)),
about 15 ns. For calculations in a loop, keep the model in a field rather than choosing it for every calculation:

```java
    private static final CrcModel CRC32C = CrcModel.of("CRC-32C");   // chosen once

    long checksum(byte[] data) {
        return CRC32C.value(data);
    }
```

### By parameters

```java
    CrcModel arc = CrcModel.of(16, 0x8005, 0x0, true, true, 0x0);      // CRC-16/ARC
    CrcModel other = CrcModel.of(16, 0x1234, 0x0, true, true, 0x0);    // CRC-16/P1234_I0_RI_RO
    CrcModel same = CrcModel.of(CrcParameters.of(16, 0x1234, 0x0, true, true, 0x0));
```

The parameters of an algorithm of the catalogue give the model with its name.

### With a builder

```java
    CrcModel crc32 = CrcModel.builder(32)   // the width
            .poly(0x04C11DB7L)              // required
            .reflected(true)                // required: refin and refout, or refin(...) and refout(...)
            .init(0xFFFFFFFFL)              // 0 if not set
            .xorout(0xFFFFFFFFL)            // 0 if not set
            .check(0xCBF43926L)             // optional: build() verifies it
            .build();                       // CRC-32/ISO-HDLC
```

Every setter checks that the value fits into the width. `build()` throws `IllegalStateException` if poly, refin or refout
is not set: they have no neutral value, a forgotten one would give another CRC. A wrong check value makes `build()`
throw `IllegalArgumentException`.

## CRC

### Calculate

```java
    CrcModel model = CrcModel.of("CRC-16/ARC");
    long value = model.value(data);                                  // the CRC value only, no CRC object
    long first = model.value(data, length);                          // the first length bytes
    long part = model.value(data, offset, length);                   // a part of the data
    CRC firstCrc = model.crc(data, length);                          // the CRC of the first length bytes
    CRC crc = model.crc().update(part1).update(part2, length2);      // part1 followed by the first length2 bytes of part2
    long processed = crc.length();
```

`model.value(...)` equals `model.crc(...).value()` without creating a `CRC`; a `CRC` is needed to continue a calculation,
to combine CRCs or to keep the length. `model.crc()` is the CRC of the empty data. A CRC is immutable, so the result of `update()` has to be used:

```java
    CRC crc = model.crc();
    crc.update(data);          // wrong: the new CRC is lost, crc is still the CRC of the empty data
    crc = crc.update(data);    // right
```

### Combine

```java
    CRC first = model.crc(part1);        // calculated independently,
    CRC second = model.crc(part2);       // for example in parallel threads
    CRC both = first.combine(second);    // the CRC of part1 followed by part2
    // only the value and the length of the second part are known:
    CRC same = first.combine(second.value(), second.length());
    // only the values are kept, like zlib crc32_combine(crc1, crc2, len2):
    long value = model.combine(first.value(), second.value(), second.length());
```

`model.combine(value1, value2, length2)` needs no length of the first part: its value already holds its data, and the value
of an empty first part, `model.crc().value()`, gives `value2`.

Both CRCs have to have the same parameters: a model and its alias are fine, others give `IllegalArgumentException`. The
cost does not depend on the size of the data: one multiplication modulo the polynomial per bit set in the length of the
second part, with 63 values per parameter set built on the first `combine`.

### Resume

```java
    long savedValue = crc.value();
    long savedLength = crc.length();
    // later, also in another process
    CRC resumed = model.crc(savedValue, savedLength).update(moreData);
```

The bits above the width are ignored, so a CRC-32 kept in an `int` can be passed as is.

### Streams

`model.checksum()` is a `java.util.zip.Checksum` of the model, so any CRC works with the JDK classes made for `CRC32`.
The CRC of the parts written to a stream one after another, also by code that knows nothing about the CRC:

```java
    CrcModel model = CrcModel.of("CRC-32C");
    try (CheckedOutputStream out = new CheckedOutputStream(Files.newOutputStream(path), model.checksum())) {
        out.write(header);                 // the parts in the order they are written:
        out.write(body, 0, bodyLength);    // arrays, parts of arrays
        out.write(0x0A);                   // and single bytes
        long value = out.getChecksum().getValue();   // the CRC value of all bytes written
    }
```

Reading is the same, the CRC of the bytes read:

```java
    try (CheckedInputStream in = new CheckedInputStream(Files.newInputStream(path), model.checksum())) {
        byte[] buffer = new byte[8192];
        while (in.read(buffer) >= 0) {
            // process the buffer
        }
        long value = in.getChecksum().getValue();
    }
```

A `java.util.zip.Checksum` has the value only. Keep the `CrcChecksum` itself (`getChecksum()` of the stream returns a
`Checksum`) to get the length of the bytes written as well, and the `CRC` of them, to combine it with the CRCs of other
parts or to resume the calculation later:

```java
    CrcChecksum checksum = model.checksum();
    try (CheckedOutputStream out = new CheckedOutputStream(Files.newOutputStream(path), checksum)) {
        out.write(header);
        out.write(body, 0, bodyLength);
    }
    CRC part = checksum.crc();             // the value and the length of the bytes written
    CRC whole = previous.combine(part);    // the CRC of the previous parts followed by this one
```

A `CrcChecksum` can be used directly as well; unlike a `CRC` it is mutable and not thread-safe, like `CRC32`:

```java
    CrcChecksum checksum = model.checksum();
    checksum.update(part1);
    checksum.update(part2, 0, length2);
    checksum.update(b);                    // the low 8 bits of an int
    long value = checksum.getValue();
    long length = checksum.length();       // the number of the bytes processed
    CRC crc = checksum.crc();              // the immutable CRC, to combine it or to resume later
    checksum.reset();                      // the checksum of the empty data again
```

On Java 9+ `checksum.update(ByteBuffer)` of the interface works too, with heap and direct buffers.

For CRC-32/ISO-HDLC itself, and for CRC-32C on Java 9+, the JDK classes `java.util.zip.CRC32` and `CRC32C` are faster:
the JVM calculates them with the CRC instructions of the CPU (about 4 times faster on 256 bytes). `CrcChecksum` is for
the other CRCs, and for the code that has to work with any model.

## Names

```java
    CrcDescription description = CrcModel.of("PKZIP").description();
    String name = description.name();             // CRC-32/ISO-HDLC
    List<String> aliases = description.aliases(); // [CRC-32, CRC-32/ADCCP, CRC-32/V-42, CRC-32/XZ, PKZIP]
    List<String> names = description.names();     // the name followed by the aliases
    String chosen = CrcModel.of("PKZIP").crc(data).name();   // PKZIP: the name the model was chosen by
```

Parameters outside the catalogue have a unique name made of the parameters and no aliases:
`CRC-{width}/P{poly}_I{init}[_RI][_RO][_X{xorout}]`, the values hexadecimal without leading zeros, `_RI` and `_RO` for
refin and refout, `_X` for a non-zero xorout (the format of the names of unregistered models of 1.x). `CrcModel.of()`
accepts such a name, so `CrcModel.of(model.name())` gives the model of any model. A name made of the parameters of an
algorithm of the catalogue gives the model with the name of the algorithm.

## Memory

- There is one `CrcDescription` per parameter set, shared by all models and CRCs of the parameters, by any name.
- The lookup tables (8 × 256 values, 16 KiB) are built on the first calculation with data, not when a model is chosen.
- The descriptions are kept in a pool that refers to them weakly: when no model and no CRC of the parameters is used,
  the garbage collector collects the description with its tables. Keep the model in a field to keep the tables.
- `-Dcom.github.sftwnd.crayfish.common.crc.retention=soft` makes the pool refer to them softly: an unused description
  is kept until the memory runs short. It helps if a model is chosen by its name for every calculation and not kept.
  The property is read once, when the pool is used first.
- On Java 9+ the daemon thread `crayfish-crc-eviction` of a `java.lang.ref.Cleaner` removes the entries of the collected
  descriptions from the pool; before Java 9, and with a jar without the `Multi-Release` attribute, the pool removes them
  when it is used.

## Supported CRC algorithms

112 algorithms of 21 widths; aliases in italics, every name and alias is accepted by `CrcModel.of()`.

| Width | # | Algorithms *(aliases)* |
|---:|---:|---|
| 3 | 2 | CRC-3/GSM, CRC-3/ROHC |
| 4 | 2 | CRC-4/G-704 *(CRC-4/ITU)*, CRC-4/INTERLAKEN |
| 5 | 3 | CRC-5/EPC-C1G2 *(CRC-5/EPC)*, CRC-5/G-704 *(CRC-5/ITU)*, CRC-5/USB |
| 6 | 5 | CRC-6/CDMA2000-A, CRC-6/CDMA2000-B, CRC-6/DARC, CRC-6/G-704 *(CRC-6/ITU)*, CRC-6/GSM |
| 7 | 3 | CRC-7/MMC *(CRC-7)*, CRC-7/ROHC, CRC-7/UMTS |
| 8 | 20 | CRC-8/AUTOSAR, CRC-8/BLUETOOTH, CRC-8/CDMA2000, CRC-8/DARC, CRC-8/DVB-S2, CRC-8/GSM-A, CRC-8/GSM-B, CRC-8/HITAG, CRC-8/I-432-1 *(CRC-8/ITU)*, CRC-8/I-CODE, CRC-8/LTE, CRC-8/MAXIM-DOW *(CRC-8/MAXIM, DOW-CRC)*, CRC-8/MIFARE-MAD, CRC-8/NRSC-5, CRC-8/OPENSAFETY, CRC-8/ROHC, CRC-8/SAE-J1850, CRC-8/SMBUS *(CRC-8)*, CRC-8/TECH-3250 *(CRC-8/AES, CRC-8/EBU)*, CRC-8/WCDMA |
| 10 | 3 | CRC-10/ATM *(CRC-10, CRC-10/I-610)*, CRC-10/CDMA2000, CRC-10/GSM |
| 11 | 2 | CRC-11/FLEXRAY *(CRC-11)*, CRC-11/UMTS |
| 12 | 4 | CRC-12/CDMA2000, CRC-12/DECT *(X-CRC-12)*, CRC-12/GSM, CRC-12/UMTS *(CRC-12/3GPP)* |
| 13 | 1 | CRC-13/BBC |
| 14 | 2 | CRC-14/DARC, CRC-14/GSM |
| 15 | 2 | CRC-15/CAN *(CRC-15)*, CRC-15/MPT1327 |
| 16 | 31 | CRC-16/ARC *(ARC, CRC-16, CRC-16/LHA, CRC-IBM)*, CRC-16/CDMA2000, CRC-16/CMS, CRC-16/DDS-110, CRC-16/DECT-R *(R-CRC-16)*, CRC-16/DECT-X *(X-CRC-16)*, CRC-16/DNP, CRC-16/EN-13757, CRC-16/GENIBUS *(CRC-16/DARC, CRC-16/EPC, CRC-16/EPC-C1G2, CRC-16/I-CODE)*, CRC-16/GSM, CRC-16/IBM-3740 *(CRC-16/AUTOSAR, CRC-16/CCITT-FALSE)*, CRC-16/IBM-SDLC *(CRC-16/ISO-HDLC, CRC-16/ISO-IEC-14443-3-B, CRC-16/X-25, CRC-B, X-25)*, CRC-16/ISO-IEC-14443-3-A *(CRC-A)*, CRC-16/KERMIT *(CRC-16/BLUETOOTH, CRC-16/CCITT, CRC-16/CCITT-TRUE, CRC-16/V-41-LSB, CRC-CCITT, KERMIT)*, CRC-16/LJ1200, CRC-16/M17, CRC-16/MAXIM-DOW *(CRC-16/MAXIM)*, CRC-16/MCRF4XX, CRC-16/MODBUS *(MODBUS)*, CRC-16/NRSC-5, CRC-16/OPENSAFETY-A, CRC-16/OPENSAFETY-B, CRC-16/PROFIBUS *(CRC-16/IEC-61158-2)*, CRC-16/RIELLO, CRC-16/SPI-FUJITSU *(CRC-16/AUG-CCITT)*, CRC-16/T10-DIF, CRC-16/TELEDISK, CRC-16/TMS37157, CRC-16/UMTS *(CRC-16/BUYPASS, CRC-16/VERIFONE)*, CRC-16/USB, CRC-16/XMODEM *(CRC-16/ACORN, CRC-16/LTE, CRC-16/V-41-MSB, XMODEM, ZMODEM)* |
| 17 | 1 | CRC-17/CAN-FD |
| 21 | 1 | CRC-21/CAN-FD |
| 24 | 8 | CRC-24/BLE, CRC-24/FLEXRAY-A, CRC-24/FLEXRAY-B, CRC-24/INTERLAKEN, CRC-24/LTE-A, CRC-24/LTE-B, CRC-24/OPENPGP *(CRC-24)*, CRC-24/OS-9 |
| 30 | 1 | CRC-30/CDMA |
| 31 | 1 | CRC-31/PHILIPS |
| 32 | 12 | CRC-32/AIXM *(CRC-32Q)*, CRC-32/AUTOSAR, CRC-32/BASE91-D *(CRC-32D)*, CRC-32/BZIP2 *(CRC-32/AAL5, CRC-32/DECT-B, B-CRC-32)*, CRC-32/CD-ROM-EDC, CRC-32/CKSUM *(CKSUM, CRC-32/POSIX)*, CRC-32/ISCSI *(CRC-32/BASE91-C, CRC-32/CASTAGNOLI, CRC-32/INTERLAKEN, CRC-32C, CRC-32/NVME)*, CRC-32/ISO-HDLC *(CRC-32, CRC-32/ADCCP, CRC-32/V-42, CRC-32/XZ, PKZIP)*, CRC-32/JAMCRC *(JAMCRC)*, CRC-32/MEF, CRC-32/MPEG-2, CRC-32/XFER *(XFER)* |
| 40 | 1 | CRC-40/GSM |
| 64 | 7 | CRC-64/ECMA-182 *(CRC-64)*, CRC-64/GO-ISO, CRC-64/MS, CRC-64/NVME, CRC-64/REDIS, CRC-64/WE, CRC-64/XZ *(CRC-64/GO-ECMA)* |

CRC-82/DARC of the catalogue is not predefined: the CRC value is a `long`, the width is limited to 64 bits.

## Migrating from 1.x

| 1.x | 2.0 |
|---|---|
| `CrcModel.CRC32_ISO_HDLC` | `StandardCrc.CRC_32_ISO_HDLC.model()` or `CrcModel.of("CRC-32/ISO-HDLC")` |
| `CrcModel.lookUp(name)`, null for an unknown name | `CrcModel.find(name)`, empty for an unknown name, or `CrcModel.of(name)` |
| `new CrcDescription(width, poly, init, refin, refot, xorot)` | `CrcParameters.of(width, poly, init, refin, refout, xorout)` |
| `crcDescription.getCRC()` | `CrcModel.of(parameters).crc()` |
| `CrcModel.construct(name, crcDescription, check)` | `CrcModel.builder(width)` … `.check(check).build()`; no names of the user |
| `CrcModel.unregistered(crcDescription)` | `CrcModel.of(parameters)`: the same name made of the parameters |
| `CrcModel.getModels()` | `StandardCrc.values()` |
| `CrcModel.CHECK_BUFF` | `CrcParameters.CHECK_INPUT` |
| `model.getCRC(data).getCrc()` | `model.value(data)` |
| `model.getCRC(data, length).getCrc()` | `model.value(data, length)` |
| `model.getCRC(crc, length)` | `model.crc(value, length)` |
| `crc.update(data)` changes the CRC | `crc = crc.update(data)`: a new CRC |
| `crc.combine(crc2)` changes the CRC | `crc = crc.combine(crc2)`: a new CRC |
| `crc.combine(crc2, length2)` returns a `long` | returns a new CRC; the value only: `model.combine(crc.value(), crc2, length2)` |
| `crc.getCrc()`, `crc.getLength()`, `crc.getModel()` | `crc.value()`, `crc.length()`, `crc.model()` |
| `crc.copy()` | not needed: a CRC is immutable |
| `update(null)` and a negative length are ignored | `NullPointerException`, `ArrayIndexOutOfBoundsException` |

## Java modules

The library runs on Java 8+. On Java 9+ it is the named module `com.github.sftwnd.crayfish_common_crc`, which exports
the package `com.github.sftwnd.crayfish.common.crc`:

```java
module my.application {
    requires com.github.sftwnd.crayfish_common_crc;
}
```

The module descriptor and the Java 9 version of the eviction of the pool are in `META-INF/versions/9` of the
multi-release jar, so Java 8 ignores them.

## Building

The project builds with the Maven Wrapper (Maven 3.9.16) on JDK 8 to 25:

```shell
./mvnw verify
```

The classes are compiled for Java 8 on every JDK; the module descriptor and the Java 9 classes are built on JDK 9+ only,
so a release has to be built on JDK 9+. The integration tests (`*IT`) run with the jar, so on JDK 9+ they test the
classes of `META-INF/versions/9`.
