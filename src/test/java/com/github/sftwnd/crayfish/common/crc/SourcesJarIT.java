/*
 * Copyright (c) 2017-2026 Andrey D. Shindarev (ashindarev@gmail.com)
 * This program is made available under the terms of the BSD 3-Clause License.
 */
package com.github.sftwnd.crayfish.common.crc;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Enumeration;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The sources jar mirrors the multi-release jar: maven-failsafe-plugin runs the integration tests with the jar of the
 * project, and the sources jar is built next to it before them. Every source belongs to a top-level class of the jar at
 * the same place, the root or META-INF/versions/9, and every top-level class has its source there; the sources are
 * those of src/main/java and src/main/java9. A build on JDK 9+ has the module descriptor and the Java 9 version of
 * JdkCrcs in META-INF/versions/9 of both jars, a build on JDK 8 has no META-INF/versions/9
 */
class SourcesJarIT {

    private static final String VERSIONS_9 = "META-INF/versions/9/";

    // the source of the package, which has no class
    private static final String PACKAGE_INFO = "com/github/sftwnd/crayfish/common/crc/package-info";

    // the jar the integration tests run with, in the target directory of the project
    private static Path jar() throws URISyntaxException {
        return Paths.get(JdkCrcs.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    // the sources jar next to the jar: <artifactId>-<version>-sources.jar
    private static Path sourcesJar() throws URISyntaxException {
        Path jar = jar();
        String name = jar.getFileName().toString();
        return jar.resolveSibling(name.substring(0, name.length() - ".jar".length()) + "-sources.jar");
    }

    // the names of the entries of the jar with the extension, without the extension
    private static Set<String> names(Path jar, String extension) throws IOException {
        Set<String> names = new TreeSet<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                String name = entries.nextElement().getName();
                if (name.endsWith(extension)) {
                    names.add(name.substring(0, name.length() - extension.length()));
                }
            }
        }
        return names;
    }

    // all bytes of the stream, without InputStream.readAllBytes of Java 9
    private static byte[] read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    @Test
    void testTheJarsAreTested() throws URISyntaxException {
        assertTrue(jar().toString().endsWith(".jar"), "The integration tests have to run with the jar: " + jar());
        assertTrue(Files.isRegularFile(sourcesJar()), "The sources jar has to be built next to the jar: " + sourcesJar());
    }

    @Test
    void testTheSourcesOfTheClasses() throws IOException, URISyntaxException {
        // the top-level classes, not the nested ones (Outer$Inner), at their place, and the source of the package
        Set<String> expected = new TreeSet<>();
        for (String name : names(jar(), ".class")) {
            if (name.indexOf('$') < 0) {
                expected.add(name);
            }
        }
        expected.add(PACKAGE_INFO);
        assertEquals(expected, names(sourcesJar(), ".java"),
                "Every top-level class of the jar has to have its source at the same place in the sources jar, and the sources jar no other source");
    }

    @Test
    void testTheSourcesOfTheProject() throws IOException, URISyntaxException {
        // the project directory: the jar is in its target directory
        Path project = jar().getParent().getParent();
        int checked = 0;
        try (ZipFile zip = new ZipFile(sourcesJar().toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".java")) {
                    Path file = name.startsWith(VERSIONS_9)
                            ? project.resolve("src/main/java9").resolve(name.substring(VERSIONS_9.length()))
                            : project.resolve("src/main/java").resolve(name);
                    byte[] source;
                    try (InputStream in = zip.getInputStream(entry)) {
                        source = read(in);
                    }
                    assertArrayEquals(Files.readAllBytes(file), source, name + " has to be the source " + file + " of the project, byte for byte");
                    checked++;
                }
            }
        }
        assertEquals(names(sourcesJar(), ".java").size(), checked, "Every source of the sources jar has to be checked");
    }

    @Test
    void testTheSourcesOfJava9() throws IOException, URISyntaxException {
        // the integration tests run on the JDK of the build: a build on JDK 9+ compiles src/main/java9 into
        // META-INF/versions/9 of the jar, a build on JDK 8 does not
        boolean java9 = !System.getProperty("java.specification.version").startsWith("1.");
        Set<String> sources = names(sourcesJar(), ".java");
        assertEquals(java9, names(jar(), ".class").contains(VERSIONS_9 + "module-info"), "The module descriptor has to be in the jar of a build on JDK 9+ only");
        assertEquals(java9, sources.contains(VERSIONS_9 + "module-info"), "The source of the module descriptor has to be in the sources jar of a build on JDK 9+ only");
        assertEquals(java9, sources.contains(VERSIONS_9 + "com/github/sftwnd/crayfish/common/crc/JdkCrcs"),
                "The source of the Java 9 version of JdkCrcs has to be in the sources jar of a build on JDK 9+ only");
    }

}
