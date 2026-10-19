/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.MultiUnitFrontend;
import io.jnative.compiler.ProgramSources;
import io.jnative.compiler.SourceUnit;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.diagnostics.DiagnosticReporter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Character literals and hex / binary / digit-separator numeric literals: native output must equal the
 * javac/java reference, and malformed literals must be diagnosed (never crash the compiler).
 *
 * @author 张晓达
 */
class LiteralEndToEndTest {

    private static final String PROGRAM = """
            public class Main {
                static int classify(char c) {
                    switch (c) {
                        case 'a': return 1;
                        case 'z': return 26;
                        case '\\n': return 10;
                        case '0': return 100;
                        default: return -1;
                    }
                }

                public static void main(String[] args) {
                    char a = 'a';
                    char nl = '\\n';
                    char q = '\\'';
                    char bs = '\\\\';
                    char u = '\\u0041';
                    char o = '\\101';
                    System.out.println('z' - 'a');
                    System.out.println(a + 1);
                    System.out.println((char) (a + 1));
                    System.out.println(u);
                    System.out.println(o);
                    System.out.println(classify('a') + classify('z') + classify('\\n') + classify('0') + classify('x'));

                    String s = "x" + a + q + bs;
                    System.out.println(s);
                    System.out.println(s.length());
                    System.out.println(String.valueOf('Q'));
                    System.out.println(String.valueOf(65));
                    System.out.println("n=" + nl + "|");

                    int n = 0;
                    String num = "4096";
                    int i = 0;
                    while (i < num.length()) {
                        n = n * 10 + (num.charAt(i) - '0');
                        i = i + 1;
                    }
                    System.out.println(n);
                    String z = "" + '\\u4e2d' + '\\u00e9';
                    System.out.println(z.length());

                    StringBuilder sb = new StringBuilder();
                    sb.append('h').append("i").append(7).append('!');
                    System.out.println(sb.toString());
                    System.out.println(sb.charAt(1));
                    sb.setCharAt(0, 'H');
                    sb.deleteCharAt(sb.length() - 1);
                    System.out.println(sb.toString());

                    System.out.println(0xFF);
                    System.out.println(0xff);
                    System.out.println(0x7FFFFFFF);
                    System.out.println(0xFFFFFFFF);
                    System.out.println(0x80000000);
                    System.out.println(0b1010);
                    System.out.println(0B11111111);
                    System.out.println(1_000_000);
                    System.out.println(0xCAFE_BABE);
                    System.out.println(0xFFFFFFFFL);
                    System.out.println(0x7FFFFFFFFFFFFFFFL);
                    System.out.println(0xFFFFFFFFFFFFFFFFL);
                    System.out.println(123_456_789_012L);
                    System.out.println(-2147483648);
                    System.out.println(-9223372036854775808L);
                    System.out.println((int) (3.141_592 * 1000));
                    System.out.println(0xF0 & 0x3C);
                    System.out.println(0xF0 | 0x0F);
                    System.out.println(0xF0 ^ 0xFF);
                    int packed = (0xAB << 16) | 0xCD;
                    System.out.println(packed);
                    System.out.println(packed >>> 16);
                }
            }
            """;

    @Test
    void nativeMatchesJavaReference(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String expected = normalize(javaReference(dir, PROGRAM));
        assertEquals(expected, normalize(compileAndRun(dir.resolve("o0"), PROGRAM, 0)));
        assertEquals(expected, normalize(compileAndRun(dir.resolve("o1"), PROGRAM, 1)));
    }

    @Test
    void validLiteralsAnalyzeClean() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static void f() {
                        char c = 'x';
                        int h = 0x1F;
                        int b = 0b101;
                        long l = 0xFFL;
                        double d = 1_0.2_5e1_0;
                        int m = -2147483648;
                    }
                }
                """);
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void malformedLiteralsAreDiagnosed() {
        String[][] cases = {
                {"char c = '';", "empty character literal"},
                {"char c = 'ab';", "unclosed character literal"},
                {"char c = '\\q';", "illegal escape character"},
                {"char c = '\\u12';", "illegal unicode escape"},
                {"int x = 1_;", "illegal underscore"},
                {"int x = 0x_1;", "illegal underscore"},
                {"int x = 0x;", "hexadecimal literal has no digits"},
                {"int x = 0b102;", "illegal digit in binary literal"},
                {"int x = 2147483648;", "integer number too large"},
                {"int x = 99999999999;", "integer number too large"},
                {"int x = 0x1FFFFFFFF;", "int literal out of range"},
                {"long x = 9223372036854775808L;", "long number too large"},
                {"double x = 0x1.8p3;", "hexadecimal floating-point literals are not supported"},
        };
        for (String[] c : cases) {
            DiagnosticReporter r = analyze("public class T { public static void f() { " + c[0] + " } }");
            assertTrue(r.hasErrors(), "expected an error for: " + c[0]);
            assertTrue(messages(r).contains(c[1]), c[0] + " -> " + messages(r));
        }
    }

    private static String normalize(String s) {
        return s.replace("\r\n", "\n");
    }

    private static String javaReference(Path dir, String src) throws Exception {
        Path javaFile = dir.resolve("Main.java");
        Files.writeString(javaFile, src, StandardCharsets.UTF_8);
        Process compile = new ProcessBuilder("javac", "-encoding", "UTF-8", javaFile.getFileName().toString())
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        assertTrue(compile.waitFor(60, TimeUnit.SECONDS));
        assertEquals(0, compile.exitValue(), new String(compile.getInputStream().readAllBytes()));
        Process run = new ProcessBuilder("java", "-cp", ".", "Main")
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        assertTrue(run.waitFor(60, TimeUnit.SECONDS));
        String out = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, run.exitValue(), out);
        return out;
    }

    private static String compileAndRun(Path out, String src, int opt) throws Exception {
        Path file = out.getParent().resolve("Main-" + opt + ".jn");
        Files.writeString(file, src, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(file);
        options.setProgramSources(ProgramSources.of(SourceUnit.ofPath(file, src)));
        options.setOutputFile(out);
        options.setOptLevel(opt);
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().stream().map(Object::toString)
                .reduce("", (x, y) -> x + "\n" + y));
        Process p = new ProcessBuilder(cr.executable().toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), stdout);
        return stdout;
    }

    private static DiagnosticReporter analyze(String src) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        MultiUnitFrontend.analyzeProgram(ProgramSources.of(new SourceUnit("T.jn", src)), reporter);
        return reporter;
    }

    private static String messages(DiagnosticReporter r) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : r.diagnostics()) {
            sb.append(d).append('\n');
        }
        return sb.toString();
    }
}
