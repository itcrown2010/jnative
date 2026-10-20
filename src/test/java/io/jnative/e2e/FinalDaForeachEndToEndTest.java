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
class FinalDaForeachEndToEndTest {

    @Test
    void unassignedLocalRejected() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f(boolean c) {
                        int value;
                        if (c) {
                            value = 1;
                        }
                        return value;
                    }
                }
                """);
        assertTrue(r.hasErrors(), messages(r));
        assertTrue(messages(r).toLowerCase().contains("initialized")
                        || messages(r).toLowerCase().contains("assigned"),
                messages(r));
    }

    @Test
    void bothBranchesAssignedOk() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f(boolean c) {
                        int value;
                        if (c) {
                            value = 1;
                        } else {
                            value = 2;
                        }
                        return value;
                    }
                }
                """);
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void loopZeroTripDoesNotAssign() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f(boolean c) {
                        int value;
                        while (c) {
                            value = 1;
                            c = false;
                        }
                        return value;
                    }
                }
                """);
        assertTrue(r.hasErrors(), messages(r));
    }

    @Test
    void switchWithoutDefaultNotAssigned() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f(int x) {
                        int value;
                        switch (x) {
                            case 1:
                                value = 1;
                                break;
                        }
                        return value;
                    }
                }
                """);
        assertTrue(r.hasErrors(), messages(r));
    }

    @Test
    void switchAllPathsAssignedOk() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f(int x) {
                        int value;
                        switch (x) {
                            case 1:
                                value = 1;
                                break;
                            default:
                                value = 0;
                                break;
                        }
                        return value;
                    }
                }
                """);
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void finalLocalReassignRejected() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f() {
                        final int x = 1;
                        x = 2;
                        return x;
                    }
                }
                """);
        assertTrue(r.hasErrors(), messages(r));
        assertTrue(messages(r).toLowerCase().contains("final"), messages(r));
    }

    @Test
    void finalParamReassignRejected() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f(final int x) {
                        x = 2;
                        return x;
                    }
                }
                """);
        assertTrue(r.hasErrors(), messages(r));
    }

    @Test
    void blankFinalAssignedOnceOk() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f(boolean c) {
                        final int x;
                        if (c) {
                            x = 1;
                        } else {
                            x = 2;
                        }
                        return x;
                    }
                }
                """);
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void blankFinalInLoopRejected() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f(boolean c) {
                        final int x;
                        while (c) {
                            x = 1;
                            c = false;
                        }
                        return 0;
                    }
                }
                """);
        assertTrue(r.hasErrors(), messages(r));
    }

    @Test
    void finalClassCannotBeExtended() {
        DiagnosticReporter r = analyze("""
                public final class Base {}
                public class Child extends Base {}
                """);
        assertTrue(r.hasErrors(), messages(r));
        assertTrue(messages(r).toLowerCase().contains("final"), messages(r));
    }

    @Test
    void finalMethodCannotBeOverridden() {
        DiagnosticReporter r = analyze("""
                public class Base {
                    public final int f() { return 1; }
                }
                public class Child extends Base {
                    public int f() { return 2; }
                }
                """);
        assertTrue(r.hasErrors(), messages(r));
        assertTrue(messages(r).toLowerCase().contains("final"), messages(r));
    }

    @Test
    void finalFieldAccepted() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public final int x = 1;
                }
                """);
        // 'final' on fields is accepted for Java source compatibility (not enforced).
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void iterableForeachRejected() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f(Object o) {
                        int s = 0;
                        for (Object x : o) {
                            s = s + 1;
                        }
                        return s;
                    }
                }
                """);
        assertTrue(r.hasErrors(), messages(r));
        assertTrue(messages(r).toLowerCase().contains("iterable")
                        || messages(r).toLowerCase().contains("array"),
                messages(r));
    }

    @Test
    void nativeRunDaFinalForeach(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String src = """
                public class Box {
                    public int n;
                    public Box(int n) { this.n = n; }
                }

                public class Main {
                    public static int side;
                    public static int[] once() {
                        side = side + 1;
                        int[] a = new int[3];
                        a[0] = 10;
                        a[1] = 20;
                        a[2] = 30;
                        return a;
                    }

                    public static void main(String[] args) {
                        // DA + final locals
                        final int base = 1;
                        int total;
                        boolean flag = true;
                        if (flag) {
                            total = base;
                        } else {
                            total = 0;
                        }

                        // Array foreach: expression once, break/continue, mutate elem != array
                        side = 0;
                        int sum = 0;
                        for (int v : once()) {
                            if (v == 20) {
                                continue;
                            }
                            if (v == 30) {
                                break;
                            }
                            sum = sum + v;
                            v = 999;
                        }
                        System.out.println(side);
                        System.out.println(sum);

                        int[] nums = once();
                        nums[0] = 7;
                        int s2 = 0;
                        for (int v : nums) {
                            s2 = s2 + v;
                        }
                        System.out.println(s2);

                        // Object array + GC
                        Box[] boxes = new Box[2];
                        boxes[0] = new Box(5);
                        boxes[1] = new Box(6);
                        int bs = 0;
                        for (Box b : boxes) {
                            bs = bs + b.n;
                        }
                        System.gc();
                        System.out.println(bs);
                        System.out.println(boxes[0].n);

                        // Nested foreach + shadowing
                        int nested = 0;
                        int[] outer = new int[2];
                        outer[0] = 1;
                        outer[1] = 2;
                        for (int v : outer) {
                            int[] inner = new int[1];
                            inner[0] = v + 10;
                            for (int v2 : inner) {
                                nested = nested + v2;
                            }
                        }
                        System.out.println(nested);

                        // final ref: rebind rejected at compile time; mutate object OK
                        final Box box = new Box(3);
                        box.n = 4;
                        System.out.println(box.n);
                        System.out.println(total);
                    }
                }
                """;
        String o0 = compileAndRun(dir.resolve("o0"), src, 0);
        String o1 = compileAndRun(dir.resolve("o1"), src, 1);
        assertEquals(o0, o1);
        String[] lines = o0.trim().split("\\R");
        assertEquals("1", lines[0]); // once() side effect
        assertEquals("10", lines[1]); // 10 only (skip 20, break before 30)
        assertEquals("57", lines[2]); // 7+20+30
        assertEquals("11", lines[3]); // boxes 5+6
        assertEquals("5", lines[4]); // GC keep-alive
        assertEquals("23", lines[5]); // 11+12
        assertEquals("4", lines[6]); // mutated through final ref
        assertEquals("1", lines[7]); // total
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
