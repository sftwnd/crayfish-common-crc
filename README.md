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

## Features

- **Fast** — table-driven, slicing-by-8 for every model, not only for CRC-32: 8 bytes per step, 2–4 times faster than
  one byte per step on buffers from 256 bytes (*1.1.0*)
- **Combine and resume** — join the CRCs of parts calculated independently (in parallel threads or on other nodes),
  like zlib `crc32_combine`; continue a calculation from a saved CRC value and length, also of 2 GiB and more (*1.1.0*)
- **Java 8+ and modular** — runs on Java 8; on Java 9+ it is the named JPMS module `com.github.sftwnd.crayfish_common_crc`
  of a multi-release jar (*1.1.0*, an automatic module of the same name up to 1.0.2); OSGi bundle headers included
- **Zero runtime dependencies** — a single small jar
- **Verified** — every predefined model is tested against its RevEng check value and a bit-by-bit reference implementation
- **Any CRC up to 64 bits** — width 1…64, any poly, init, refin, refout, xorout; look up a model by name or by any alias
- **BSD 3-Clause** license

## Installation

Maven:

```xml
<dependency>
    <groupId>com.github.sftwnd.crayfish.common</groupId>
    <artifactId>crayfish-common-crc</artifactId>
    <version>1.0.2</version>
</dependency>
```

Gradle:

```groovy
implementation 'com.github.sftwnd.crayfish.common:crayfish-common-crc:1.0.2'
```

## Quick start

```java
    byte[] data = "123456789".getBytes(StandardCharsets.US_ASCII);
    long modbus = CrcModel.CRC16_MODBUS.getCRC(data).getCrc();       // 0x4B37
    long crc32c = CrcModel.lookUp("CRC-32C").getCRC(data).getCrc();  // 0xE3069283
```

## Supported CRC algorithms

112 algorithms of 21 widths; aliases in italics, every name and alias is accepted by `CrcModel.lookUp()`.
The names marked ¹ come with 1.1.0: 1.0.2 has 101 algorithms with 173 names.

| Width | # | Algorithms *(aliases)* |
|---:|---:|---|
| 3 | 2 | CRC-3/GSM, CRC-3/ROHC |
| 4 | 2 | CRC-4/G-704 *(CRC-4/ITU)*, CRC-4/INTERLAKEN |
| 5 | 3 | CRC-5/EPC-C1G2 *(CRC-5/EPC)*, CRC-5/G-704 *(CRC-5/ITU)*, CRC-5/USB |
| 6 | 5 | CRC-6/CDMA2000-A, CRC-6/CDMA2000-B, CRC-6/DARC, CRC-6/G-704 *(CRC-6/ITU)*, CRC-6/GSM |
| 7 | 3 | CRC-7/MMC *(CRC-7)*, CRC-7/ROHC, CRC-7/UMTS |
| 8 | 20 | CRC-8/AUTOSAR, CRC-8/BLUETOOTH, CRC-8/CDMA2000, CRC-8/DARC, CRC-8/DVB-S2, CRC-8/GSM-A, CRC-8/GSM-B, CRC-8/HITAG¹, CRC-8/I-432-1 *(CRC-8/ITU)*, CRC-8/I-CODE, CRC-8/LTE, CRC-8/MAXIM-DOW *(CRC-8/MAXIM, DOW-CRC)*, CRC-8/MIFARE-MAD¹, CRC-8/NRSC-5¹, CRC-8/OPENSAFETY, CRC-8/ROHC, CRC-8/SAE-J1850, CRC-8/SMBUS *(CRC-8)*, CRC-8/TECH-3250 *(CRC-8/AES, CRC-8/EBU)*, CRC-8/WCDMA |
| 10 | 3 | CRC-10/ATM *(CRC-10, CRC-10/I-610)*, CRC-10/CDMA2000, CRC-10/GSM |
| 11 | 2 | CRC-11/FLEXRAY *(CRC-11)*, CRC-11/UMTS |
| 12 | 4 | CRC-12/CDMA2000, CRC-12/DECT *(X-CRC-12)*, CRC-12/GSM, CRC-12/UMTS *(CRC-12/3GPP)* |
| 13 | 1 | CRC-13/BBC |
| 14 | 2 | CRC-14/DARC, CRC-14/GSM |
| 15 | 2 | CRC-15/CAN *(CRC-15)*, CRC-15/MPT1327 |
| 16 | 31 | CRC-16/ARC *(ARC, CRC-16, CRC-16/LHA, CRC-IBM)*, CRC-16/CDMA2000, CRC-16/CMS, CRC-16/DDS-110, CRC-16/DECT-R *(R-CRC-16)*, CRC-16/DECT-X *(X-CRC-16)*, CRC-16/DNP, CRC-16/EN-13757, CRC-16/GENIBUS *(CRC-16/DARC, CRC-16/EPC, CRC-16/EPC-C1G2, CRC-16/I-CODE)*, CRC-16/GSM, CRC-16/IBM-3740 *(CRC-16/AUTOSAR, CRC-16/CCITT-FALSE)*, CRC-16/IBM-SDLC *(CRC-16/ISO-HDLC, CRC-16/ISO-IEC-14443-3-B, CRC-16/X-25, CRC-B, X-25)*, CRC-16/ISO-IEC-14443-3-A *(CRC-A)*, CRC-16/KERMIT *(CRC-16/BLUETOOTH¹, CRC-16/CCITT, CRC-16/CCITT-TRUE, CRC-16/V-41-LSB, CRC-CCITT, KERMIT)*, CRC-16/LJ1200, CRC-16/M17¹, CRC-16/MAXIM-DOW *(CRC-16/MAXIM)*, CRC-16/MCRF4XX, CRC-16/MODBUS *(MODBUS)*, CRC-16/NRSC-5¹, CRC-16/OPENSAFETY-A, CRC-16/OPENSAFETY-B, CRC-16/PROFIBUS *(CRC-16/IEC-61158-2)*, CRC-16/RIELLO, CRC-16/SPI-FUJITSU *(CRC-16/AUG-CCITT)*, CRC-16/T10-DIF, CRC-16/TELEDISK, CRC-16/TMS37157, CRC-16/UMTS *(CRC-16/BUYPASS, CRC-16/VERIFONE)*, CRC-16/USB, CRC-16/XMODEM *(CRC-16/ACORN, CRC-16/LTE, CRC-16/V-41-MSB, XMODEM, ZMODEM)* |
| 17 | 1 | CRC-17/CAN-FD |
| 21 | 1 | CRC-21/CAN-FD |
| 24 | 8 | CRC-24/BLE, CRC-24/FLEXRAY-A, CRC-24/FLEXRAY-B, CRC-24/INTERLAKEN, CRC-24/LTE-A, CRC-24/LTE-B, CRC-24/OPENPGP *(CRC-24)*, CRC-24/OS-9¹ |
| 30 | 1 | CRC-30/CDMA |
| 31 | 1 | CRC-31/PHILIPS |
| 32 | 12 | CRC-32/AIXM *(CRC-32Q)*, CRC-32/AUTOSAR, CRC-32/BASE91-D *(CRC-32D)*, CRC-32/BZIP2 *(B-CRC-32, CRC-32/AAL5, CRC-32/DECT-B)*, CRC-32/CD-ROM-EDC¹, CRC-32/CKSUM *(CKSUM, CRC-32/POSIX)*, CRC-32/ISCSI *(CRC-32/BASE91-C, CRC-32/CASTAGNOLI, CRC-32/INTERLAKEN, CRC-32/NVME¹, CRC-32C)*, CRC-32/ISO-HDLC *(CRC-32, CRC-32/ADCCP, CRC-32/V-42, CRC-32/XZ, PKZIP)*, CRC-32/JAMCRC *(JAMCRC)*, CRC-32/MEF¹, CRC-32/MPEG-2, CRC-32/XFER *(XFER)* |
| 40 | 1 | CRC-40/GSM |
| 64 | 7 | CRC-64/ECMA-182 *(CRC-64)*, CRC-64/GO-ISO, CRC-64/MS¹, CRC-64/NVME¹, CRC-64/REDIS¹, CRC-64/WE, CRC-64/XZ *(CRC-64/GO-ECMA)* |

CRC-82/DARC of the catalogue is not predefined: the CRC value is a `long`, the width is limited to 64 bits.

## Create CRC

### Full case with description

```java
    CrcDescription crcDescription = new CrcDescription(3, 0x3, 0x0, false, false, 0x7);
    CRC crc = crcDescription.getCRC();
    crc.update("StringA".getBytes());
    long crcValue = crc.getCrc();
```

### Simple case

```java
    CrcDescription crcDescription = new CrcDescription(3, 0x3, 0x0, false, false, 0x7);
    long crc = crcDescription.getCRC().update("StringA".getBytes()).getCrc();
```

`CrcDescription.getCRC()` uses the model registered for the parameters, here the predefined CRC-3/GSM. Parameters
without a registered model are not registered (*1.1.0*): the CRC uses a model that `CrcModel.getModels()` and
`CrcModel.lookUp()` do not know, so calculations with many different parameter sets do not fill the registry.

### Incremental update

```java
    CrcModel crcModel = CrcModel.lookUp("CRC-16/ARC");
    String str1 = "StringABC";
    String str2 = "StringBCD";
    CRC crc = crcModel.getCRC(str1.getBytes());
    crc.update(str2.getBytes());
    long crcValue = crc.getCrc();
```

### Combine crc

```java
    CrcModel crcModel = CrcModel.lookUp("CRC-16/ARC");
    byte[] part1 = "StringABC".getBytes(StandardCharsets.UTF_8);
    byte[] part2 = "StringBCD".getBytes(StandardCharsets.UTF_8);
    CRC crc = crcModel.getCRC(part1);         // CRC of part1
    CRC crc2 = crcModel.getCRC(part2);        // CRC of part2, calculated independently
    crc.combine(crc2);                        // crc now covers part1 followed by part2
    long crcValue = crc.getCrc();
    // only the CRC value and the length of the second part are known
    // (combine(long, long) returns the result and does not modify the CRC object):
    long crcValue2 = crcModel.getCRC(part1).combine(crc2.getCrc(), part2.length);
    // a calculation is continued from a saved state of any length, also of 2 GiB and more (1.1.0)
    long savedCrc = crc.getCrc();
    long savedLength = crc.getLength();
    CRC resumed = crcModel.getCRC(savedCrc, savedLength);
```

### Construct & combine value check examples

```java
    CrcModel crcModel = CrcModel.lookUp("CRC-16/ARC");
    String str1 = "StringABC";
    String str2 = "StringBCD";

    long crc1a = crcModel.getCRC().update(str1.getBytes()).update(str2.getBytes()).getCrc();
    long crc1b = crcModel.getCRC(str1.getBytes()).update(str2.getBytes()).getCrc();
    long crc2 = crcModel.getCRC(crcModel.getCRC(str1.getBytes()).getCrc(), str1.getBytes().length).update(str2.getBytes()).getCrc();
    long crc3 = crcModel.getCRC((str1+str2).getBytes()).getCrc();
    long crc4 = crcModel.getCRC(str1.getBytes()).combine(crcModel.getCRC(str2.getBytes())).getCrc();
```

In all cases the values are the same.

## CrcModel

A CRC model is a `CrcDescription` with a name and a check value: the CRC of the ASCII bytes of `"123456789"`.

### Register CrcModel

```java
    CrcDescription crcDescription = new CrcDescription(3, 0x3, 0x0, false, false, 0x7);
    // returns a model registered for these parameters (here: the predefined CRC-3/GSM)
    // or registers a new one
    CrcModel crcModel1 = CrcModel.construct(crcDescription);
    // or: register the parameters under a name
    CrcModel crcModel2 = CrcModel.construct("MyModel", crcDescription);
    // or: the same with a known check value
    long checkCode = 0x4L;
    CrcModel crcModel3 = CrcModel.construct("MyModel", crcDescription, checkCode);
```

_P.S.> The check value is calculated if it is not given. A given check value is verified: `construct()` throws
`IllegalArgumentException` if it is not the CRC of the ASCII bytes of `"123456789"`. A name belongs to one parameter set:
`construct()` throws `IllegalArgumentException` for a name that is used by a model with other parameters._

### Unregistered CrcModel

```java
    // a model that is not registered: getModels(), lookUp() and construct() do not know it (1.1.0)
    CrcModel crcModel = CrcModel.unregistered("MyModel", new CrcDescription(16, 0x1234, 0x0, false, false, 0x0));
    long crc = crcModel.getCRC().update("String".getBytes()).getCrc();
```

### Use predefined CrcModel

```java
    CrcModel crcModel = CrcModel.CRC64_GO_ISO;
    long crc = crcModel.getCRC().update("String".getBytes()).getCrc();
```

### Use well-known CrcModel by the name

```java
    CrcModel crcModel = CrcModel.lookUp("CRC-16/ARC");
    if (crcModel != null) {
        long crc = crcModel.getCRC().update("String".getBytes()).getCrc();
    }
```

## Java modules

The library runs on Java 8+. On Java 9+ it is the named module `com.github.sftwnd.crayfish_common_crc` (*1.1.0*;
up to 1.0.2 an automatic module of the same name), which exports the package `com.github.sftwnd.crayfish.common.crc`:

```java
module my.application {
    requires com.github.sftwnd.crayfish_common_crc;
}
```

The module descriptor is in `META-INF/versions/9` of the multi-release jar, so Java 8 ignores it.

## Building

The project builds with the Maven Wrapper (Maven 3.9.16) on JDK 8 to 25:

```shell
./mvnw verify
```

The classes are compiled for Java 8 on every JDK; the module descriptor is built on JDK 9+ only, so a release has to be
built on JDK 9+.
