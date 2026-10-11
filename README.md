[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=sftwnd_crayfish_common_crc&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=sftwnd_crayfish_common_crc) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=sftwnd_crayfish_common_crc&metric=coverage)](https://sonarcloud.io/summary/new_code?id=sftwnd_crayfish_common_crc) [![Build](https://github.com/sftwnd/crayfish-common-crc/actions/workflows/build.yml/badge.svg)](https://github.com/sftwnd/crayfish-common-crc/actions/workflows/build.yml) [![License](https://img.shields.io/github/license/sftwnd/crayfish-common-crc)](https://github.com/sftwnd/crayfish-common-crc/blob/master/LICENSE)
# Crayfish Common CRC
Generic Java CRC implementation (CRC-16, CRC-32, CRC-64, etc.) with 101 predefined CRC algorithms (173 names including aliases), such as CRC-32/ISO-HDLC, CRC-64/ECMA-182 and CRC-8/SMBUS.
The parameters and check values of the predefined models are those of the [RevEng CRC catalogue](https://reveng.sourceforge.io/crc-catalogue/all.htm).
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
    // a calculation is continued from a saved state of any length, also of 2 GiB and more
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
Crc Model is an object for usage of CrcDescription with name and check code.
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
_P.S.> The checkValue will be calculated if it is not defined._

### Use predefined CrcModel
```java
    CrcModel crcModel = CrcModel.CRC64_GO_ISO;
    long crc = crcModel.getCRC().update("String".getBytes()).getCrc();
```
### Use well-known CrcModel by the name
```java
    CrcModel crcModel = CrcModel.lookUp("CRC-16/ARC");
    if (crcModel != null) {
        long crc=crcModel.getCRC().update("String".getBytes()).getCrc();
    }
```