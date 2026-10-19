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
 * Integer/Long/Boolean/Double/Character library classes and Java-compatible floating-point text:
 * native output must be byte-identical to the javac/java reference.
 *
 * @author 张晓达
 */
class StdlibNumbersEndToEndTest {

    @Test
    void numberAndCharacterApisMatchJava(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String src = JavaReference.resource("/e2e/stdlib/Std1.java.txt");
        String expected = normalize(JavaReference.run(dir.resolve("ref"), src));
        assertEquals(expected, normalize(JavaReference.runNative(dir.resolve("o0"), src, 0)));
        assertEquals(expected, normalize(JavaReference.runNative(dir.resolve("o2"), src, 2)));
    }

    @Test
    void doubleAndFloatToStringMatchJava(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String src = JavaReference.resource("/e2e/stdlib/Dbl1.java.txt");
        String expected = normalize(JavaReference.run(dir.resolve("ref"), src));
        assertEquals(expected, normalize(JavaReference.runNative(dir.resolve("o0"), src, 0)));
        assertEquals(expected, normalize(JavaReference.runNative(dir.resolve("o2"), src, 2)));
    }
}
