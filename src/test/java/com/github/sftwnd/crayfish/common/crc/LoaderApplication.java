/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import static java.nio.charset.StandardCharsets.US_ASCII;

/**
 * An application that shares its class loader with the library, as a web application with the library in WEB-INF/lib:
 * {@link ClassLoaderIT} loads it with the jar in a class loader without a parent. It uses only the library and java.*,
 * and it is public, so that the test can call it in the other class loader
 */
public final class LoaderApplication {

    // the model kept in a static field, as the README recommends: the class loader of the application keeps it
    static CrcModel model;

    private LoaderApplication() {
    }

    /**
     * Calculate the CRC-32C of the check input
     * @param keep whether the application keeps the model in its static field
     * @return CRC value
     */
    public static long crc(boolean keep) {
        CrcModel crc32c = CrcModel.of("CRC-32C");
        if (keep) {
            model = crc32c;
        }
        return crc32c.crc(CrcParameters.CHECK_INPUT.getBytes(US_ASCII)).value();
    }

}
