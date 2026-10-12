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
 * @author 张晓达
 */
class SwitchEnumEndToEndTest {

    @Test
    void duplicateCaseRejected() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f(int x) {
                        switch (x) {
                            case 1: return 1;
                            case 1: return 2;
                        }
                        return 0;
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(messages(r).toLowerCase().contains("duplicate"), messages(r));
    }

    @Test
    void arrowSwitchRejected() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f(int x) {
                        switch (x) {
                            case 1 -> { return 1; }
                            default -> { return 0; }
                        }
                    }
                }
                """);
        assertTrue(r.hasErrors());
        String msg = messages(r).toLowerCase();
        assertTrue(msg.contains("arrow") || msg.contains("expected ':'"), msg);
    }

    @Test
    void continueInSwitchInsideLoopOk() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f() {
                        int s = 0;
                        for (int i = 0; i < 3; i = i + 1) {
                            switch (i) {
                                case 1:
                                    continue;
                                default:
                                    s = s + i;
                            }
                        }
                        return s;
                    }
                }
                """);
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void continueOutsideLoopRejected() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static void f(int x) {
                        switch (x) {
                            case 0:
                                continue;
                            default:
                                break;
                        }
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(messages(r).contains("JN1002") || messages(r).toLowerCase().contains("continue"),
                messages(r));
    }

    @Test
    void enumNewRejected() {
        DiagnosticReporter r = analyze("""
                enum Color { RED, GREEN }
                public class T {
                    public static void f() {
                        Color c = new Color();
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(messages(r).toLowerCase().contains("enum"), messages(r));
    }

    @Test
    void nativeIntegerSwitchAndEnum(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String src = """
                enum OrderStatus {
                    DRAFT,
                    APPROVED,
                    POSTED,
                    CANCELLED
                }

                public class Main {
                    static int side = 0;

                    static int bump() {
                        side = side + 1;
                        return side;
                    }

                    static int classify(int status) {
                        int result = 0;
                        switch (status) {
                            case 0:
                                result = 10;
                                break;
                            case 1:
                            case 2:
                                result = 20;
                                break;
                            default:
                                result = -1;
                        }
                        return result;
                    }

                    static int score(OrderStatus status) {
                        int result = 0;
                        switch (status) {
                            case DRAFT:
                                result = 1;
                                break;
                            case POSTED:
                                result = 2;
                                break;
                            default:
                                result = 0;
                        }
                        return result;
                    }

                    public static void main(String[] args) {
                        System.out.println(classify(0));
                        System.out.println(classify(1));
                        System.out.println(classify(2));
                        System.out.println(classify(9));

                        // selector evaluated once
                        side = 0;
                        int v = 0;
                        switch (bump()) {
                            case 1:
                                v = side;
                                break;
                            default:
                                v = -1;
                        }
                        System.out.println(v);
                        System.out.println(side);

                        // fallthrough without break
                        int f = 0;
                        switch (1) {
                            case 1:
                                f = f + 1;
                            case 2:
                                f = f + 10;
                                break;
                            default:
                                f = -1;
                        }
                        System.out.println(f);

                        // nested loop + switch continue
                        int s = 0;
                        for (int i = 0; i < 4; i = i + 1) {
                            switch (i) {
                                case 1:
                                    continue;
                                default:
                                    s = s + i;
                            }
                        }
                        System.out.println(s);

                        OrderStatus st = OrderStatus.DRAFT;
                        System.out.println(score(st));
                        System.out.println(score(OrderStatus.POSTED));
                        System.out.println(score(OrderStatus.APPROVED));
                        System.out.println(st == OrderStatus.DRAFT ? 1 : 0);
                        System.out.println(st.name());
                        System.out.println(st.ordinal());
                        System.out.println(OrderStatus.CANCELLED.ordinal());

                        OrderStatus[] a = OrderStatus.values();
                        OrderStatus[] b = OrderStatus.values();
                        a[0] = OrderStatus.CANCELLED;
                        System.out.println(b[0] == OrderStatus.DRAFT ? 1 : 0);
                        System.out.println(OrderStatus.valueOf("POSTED") == OrderStatus.POSTED ? 1 : 0);

                        // GC keep-alive for enum constants
                        System.gc();
                        System.out.println(OrderStatus.APPROVED.name());
                    }
                }
                """;
        String o0 = compileAndRun(dir.resolve("o0"), src, 0);
        String o1 = compileAndRun(dir.resolve("o1"), src, 1);
        assertEquals(o0, o1);
        String[] lines = o0.trim().split("\\R");
        assertEquals("10", lines[0]);
        assertEquals("20", lines[1]);
        assertEquals("20", lines[2]);
        assertEquals("-1", lines[3]);
        assertEquals("1", lines[4]); // selector side effect once
        assertEquals("1", lines[5]);
        assertEquals("11", lines[6]); // fallthrough 1+10
        assertEquals("5", lines[7]); // 0+2+3 (skipped 1)
        assertEquals("1", lines[8]);
        assertEquals("2", lines[9]);
        assertEquals("0", lines[10]);
        assertEquals("1", lines[11]);
        assertEquals("DRAFT", lines[12]);
        assertEquals("0", lines[13]);
        assertEquals("3", lines[14]);
        assertEquals("1", lines[15]); // values() isolation
        assertEquals("1", lines[16]);
        assertEquals("APPROVED", lines[17]);
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
                .reduce("", (a, b) -> a + "\n" + b));
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
