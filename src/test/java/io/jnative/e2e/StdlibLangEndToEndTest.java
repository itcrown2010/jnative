/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static io.jnative.e2e.JavaReference.normalize;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code java.lang.String} / {@code System} parity: native output (stdout + stderr, merged) must
 * equal the javac/java reference.
 *
 * @author 张晓达
 */
class StdlibLangEndToEndTest {

    @Test
    void stringApiMatchesJava(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        compareWithJava(dir, "/e2e/stdlib/Str1.java.txt");
    }

    @Test
    void systemShimMatchesJava(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        compareWithJava(dir, "/e2e/stdlib/Sys1.java.txt");
    }

    @Test
    void collectionsMatchJava(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        compareWithJava(dir, "/e2e/stdlib/Col1.java.txt", "import java.util.*;\n");
    }

    @Test
    void compoundAssignmentMatchesJava(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        compareWithJava(dir, "/e2e/stdlib/Compound1.java.txt");
    }

    private static void compareWithJava(Path dir, String resource) throws Exception {
        compareWithJava(dir, resource, "");
    }

    private static void compareWithJava(Path dir, String resource, String javaPrelude) throws Exception {
        String src = JavaReference.resource(resource);
        String expected = normalize(JavaReference.run(dir.resolve("ref"), src, javaPrelude));
        assertEquals(expected, normalize(JavaReference.runNative(dir.resolve("o0"), src, 0)));
        assertEquals(expected, normalize(JavaReference.runNative(dir.resolve("o2"), src, 2)));
    }
}
