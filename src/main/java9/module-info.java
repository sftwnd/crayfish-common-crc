/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */

/**
 * CRC calculation with predefined and custom CRC models.
 * The library is compiled for Java 8; this descriptor is read by Java 9+ only, from META-INF/versions/9 of the jar
 */
// the spotbugs annotations jar is an automatic module: it has no module descriptor, only an Automatic-Module-Name
@SuppressWarnings("requires-automatic")
module com.github.sftwnd.crayfish_common_crc {
    // CLASS-retention marker annotations, needed neither to compile against the module nor to run it
    requires static com.github.spotbugs.annotations;
    exports com.github.sftwnd.crayfish.common.crc;
}
