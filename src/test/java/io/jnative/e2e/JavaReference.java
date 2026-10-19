/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.e2e;

import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.ProgramSources;
import io.jnative.compiler.SourceUnit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential-testing helpers: run a program with javac/java (reference) and as a JNative executable
 * and compare stdout. The program's public class must be named {@code Main}.
 *
 * @author 张晓达
 */
final class JavaReference {
    private JavaReference() {
    }

    /** Loads {@code /e2e/stdlib/<name>.java.txt} from the test resources. */
    static String resource(String path) throws IOException {
        try (InputStream in = JavaReference.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing test resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static String normalize(String s) {
        return s.replace("\r\n", "\n");
    }

    /** Output of the program compiled by javac and run on the JVM. */
    static String run(Path dir, String src) throws Exception {
        return run(dir, src, "");
    }

    /**
     * Same, with {@code javaPrelude} (e.g. {@code import java.util.ArrayList;}) prepended for javac only:
     * JNative supplies its library types without imports.
     */
    static String run(Path dir, String src, String javaPrelude) throws Exception {
        Files.createDirectories(dir);
        Path javaFile = dir.resolve("Main.java");
        Files.writeString(javaFile, javaPrelude + src, StandardCharsets.UTF_8);
        Process compile = new ProcessBuilder("javac", "-encoding", "UTF-8", javaFile.getFileName().toString())
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        String compileOut = new String(compile.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(compile.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, compile.exitValue(), compileOut);
        Process run = new ProcessBuilder("java", "-Dstdout.encoding=UTF-8", "-cp", ".", "Main")
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        // Drain stdout first: a full pipe (4 KB on Windows) would otherwise block the child forever.
        String out = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(run.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, run.exitValue(), out);
        return out;
    }

    /** Output of the same program compiled by JNative at the given optimisation level. */
    static String runNative(Path dir, String src, int opt) throws Exception {
        Files.createDirectories(dir);
        Path file = dir.resolve("Main-" + opt + ".jn");
        Files.writeString(file, src, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(file);
        options.setProgramSources(ProgramSources.of(SourceUnit.ofPath(file, src)));
        options.setOutputFile(dir.resolve("main-" + opt));
        options.setOptLevel(opt);
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().stream().map(Object::toString)
                .reduce("", (x, y) -> x + "\n" + y));
        Process p = new ProcessBuilder(cr.executable().toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), stdout);
        return stdout;
    }
}
