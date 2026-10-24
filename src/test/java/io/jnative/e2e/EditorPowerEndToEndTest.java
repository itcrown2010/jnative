/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behaviour of the rewritten code editor driven through synthetic keys, text-input (IME) events, the wheel and the
 * find / replace API: auto-pairing, smart indent, comment toggling, line commands, word navigation, undo
 * coalescing, bracket matching, scrolling. The scenario lives in {@code editor/EditorPowerCheck.java.txt}.
 *
 * @author 张晓达
 */
class EditorPowerEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void editingCommandsFindReplaceImeAndScrolling() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String source;
        try (var in = getClass().getResourceAsStream("/editor/EditorPowerCheck.java.txt")) {
            assertNotNull(in);
            source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        Path javaFile = tempDir.resolve("Main.java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("power");
        CompilerOptions options = new CompilerOptions();
        options.setOptLevel(0);
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        options.setHeadless(true);
        options.setUiBackend("headless");
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.directory(tempDir.toFile());
        pb.environment().put("JNATIVE_HEADLESS", "1");
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(90, TimeUnit.SECONDS), "timeout");
        assertEquals(0, p.exitValue(), () -> stdout + stderr);
        assertFalse(stdout.contains("FAIL"), stdout);
        assertTrue(stdout.contains("fails=0"), stdout);
        assertTrue(stdout.contains("ok   wheel notch scrolls 3 lines"), stdout);
    }
}
