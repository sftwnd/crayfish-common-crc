[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=sftwnd_crayfish_common_crc&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=sftwnd_crayfish_common_crc) [![Coverage](https://sonarcloud.io/api/project_badges/measure?project=sftwnd_crayfish_common_crc&metric=coverage)](https://sonarcloud.io/summary/new_code?id=sftwnd_crayfish_common_crc) [![TravisCI-build](https://app.travis-ci.com/sftwnd/crayfish-common-crc.svg?branch=master)](https://app.travis-ci.com/github/sftwnd/crayfish-common-crc/logscans) [![License](https://img.shields.io/github/license/sftwnd/crayfish-common-crc)](https://github.com/sftwnd/crayfish-common-crc/blob/master/LICENSE)
# Crayfish Common CRC
Generic Java language CRC implementation (includes CRC16, CRC32, CRC64, etc). With more than 170 predefined CRC like: CRC-32/ISO-HDLC, CRC-64/ECMA-182 &amp; CRC-8/SMBUS
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
### Combine crc
```java
    CrcModel crcModel = CrcModel.lookUp("CRC-16/ARC");
    String str1 = "StringABC";
    String str2 = "StringBCD";
    CRC crc = crcModel.getCRC(str1.getBytes());
    crc.update(str2.getBytes());
    long crcValue = crc.getCrc();
```

### Construct & combine value check examples
```java
    CrcModel crcModel = CrcModel.lookUp("CRC-16/ARC");
    String str1 = "StringABC";
    String str2 = "StringBCD";

    long crc1a = crcModel.getCRC().update(str1.getBytes()).update(str2.getBytes()).getCrc();
    long crc1b = crcModel.getCRC(str1.getBytes()).update(str2.getBytes()).getCrc();
    long crc2 = new CRC(crcModel, crcModel.getCRC(str1.getBytes()).getCrc(), str1.length()).update(str2.getBytes()).getCrc();
    long crc3 = crcModel.getCRC((str1+str2).getBytes()).getCrc();
```
In the all cases values has to be the same

## CrcModel
Crc Model is an object for usage of CrcDescription with name and check code.
### Register CrcModel
```java
    CrcDescription crcDescription = new CrcDescription(3, 0x3, 0x0, false, false, 0x7);
    CrcModel crcModel = CrcModel.construct(crcDescription);
    # >>> or:
    CrcModel crcModel = CrcModel.construct("MyModel", crcDescription);
    # >>> or:
    long checkCode = 0x4L;
    CrcModel crcModel = CrcModel.construct("MyModel", crcDescription, checkCode);
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