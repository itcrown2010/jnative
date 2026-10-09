package io.jnative.compiler;

import io.jnative.backend.Toolchain;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.diagnostics.DiagnosticReporter;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * True multi-CU: independent parse, order-independent resolution, original file diagnostics.
 * @author 张晓达
 */
class MultiUnitCompileTest {

    @TempDir
    Path tmp;

    @Test
    void fileOrderDoesNotChangeSemanticDiagnostics() {
        String a = """
                package p.a;
                public class A {
                    public static int v() { return B.x(); }
                }
                """;
        String b = """
                package p.a;
                public class B {
                    public static int x() { return 1; }
                }
                """;
        DiagnosticReporter r1 = analyzeUnits(List.of(
                new SourceUnit("A.jn", a),
                new SourceUnit("B.jn", b)));
        DiagnosticReporter r2 = analyzeUnits(List.of(
                new SourceUnit("B.jn", b),
                new SourceUnit("A.jn", a)));
        assertFalse(r1.hasErrors(), messages(r1));
        assertFalse(r2.hasErrors(), messages(r2));
    }

    @Test
    void sameSimpleNameDifferentPackagesOk() {
        DiagnosticReporter r = analyzeUnits(List.of(
                new SourceUnit("app/Node.jn", """
                        package com.app;
                        public class Node {
                            public int n;
                            public Node() { n = 1; }
                        }
                        """),
                new SourceUnit("util/Node.jn", """
                        package com.util;
                        public class Node {
                            public int n;
                            public Node() { n = 2; }
                        }
                        """),
                new SourceUnit("Main.jn", """
                        package com.app;

                        import com.util.Node;

                        public class Main {
                            public static void main(String[] args) {
                                Node u = new Node();
                                com.app.Node a = new com.app.Node();
                                System.out.println(u.n + a.n);
                            }
                        }
                        """)));
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void fqnStaticCallWorks() {
        DiagnosticReporter r = analyzeUnits(List.of(
                new SourceUnit("lib/T.jn", """
                        package com.lib;
                        public class T {
                            public static int f() { return 9; }
                        }
                        """),
                new SourceUnit("Main.jn", """
                        package app;
                        public class Main {
                            public static void main(String[] args) {
                                System.out.println(com.lib.T.f());
                            }
                        }
                        """)));
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void duplicateFqnRejected() {
        DiagnosticReporter r = analyzeUnits(List.of(
                new SourceUnit("a.jn", "package p; public class Dup {}"),
                new SourceUnit("b.jn", "package p; public class Dup {}")));
        assertTrue(r.hasErrors());
        assertTrue(messages(r).toLowerCase().contains("duplicate"), messages(r));
    }

    @Test
    void errorLocationIsUserFileNotStdlib() throws Exception {
        // ArrayList pulls CollectionsLib as an independent CU (not text-prepended).
        ProgramSources program = MultiUnitFrontend.withStdlib(ProgramSources.of(
                new SourceUnit("/proj/Bad.jn", """
                        package app;
                        public class Bad {
                            public static void main(String[] args) {
                                ArrayList list = new ArrayList();
                                int x = true;
                            }
                        }
                        """)));
        DiagnosticReporter r = new DiagnosticReporter();
        MultiUnitFrontend.analyzeProgram(program, r);
        assertTrue(r.hasErrors());
        boolean onUser = r.diagnostics().stream().anyMatch(d ->
                d.location() != null
                        && d.location().file() != null
                        && d.location().file().contains("Bad.jn")
                        && !d.location().file().contains("<stdlib>")
                        && d.location().line() >= 5);
        assertTrue(onUser, messages(r));
        assertTrue(program.units().stream().anyMatch(u -> u.path().startsWith("<stdlib>/")),
                "expected independent stdlib units");
    }

    @Test
    void missingTypeDiagnosedAcrossFiles() {
        // Import makes the simple name parseable; semantic must reject the missing FQN.
        DiagnosticReporter r = analyzeUnits(List.of(
                new SourceUnit("Main.jn", """
                        package app;
                        import com.nowhere.Ghost;
                        public class Main {
                            public static void main(String[] args) {
                                Ghost g = null;
                            }
                        }
                        """)));
        assertTrue(r.hasErrors());
        String msg = messages(r).toLowerCase();
        assertTrue(msg.contains("ghost") || msg.contains("unknown") || msg.contains("not found")
                || msg.contains("cannot resolve") || msg.contains("import"), msg);
    }

    @Test
    void privateAccessRejectedAcrossFiles() {
        DiagnosticReporter r = analyzeUnits(List.of(
                new SourceUnit("A.jn", """
                        package p;
                        public class A {
                            private static int secret() { return 1; }
                        }
                        """),
                new SourceUnit("B.jn", """
                        package p;
                        public class B {
                            public static int leak() { return A.secret(); }
                        }
                        """)));
        assertTrue(r.hasErrors());
        String msg = messages(r).toLowerCase();
        assertTrue(msg.contains("private") || msg.contains("not accessible") || msg.contains("access"), msg);
    }

    @Test
    void inheritanceCycleDiagnosedAcrossFiles() {
        DiagnosticReporter r = analyzeUnits(List.of(
                new SourceUnit("A.jn", "package p; public class A extends B {}"),
                new SourceUnit("B.jn", "package p; public class B extends A {}")));
        assertTrue(r.hasErrors());
        assertTrue(messages(r).toLowerCase().contains("cyclic"), messages(r));
    }

    @Test
    void nativeMultiCuEndToEnd(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path a = dir.resolve("A.jn");
        Path b = dir.resolve("B.jn");
        Path main = dir.resolve("Main.jn");
        Files.writeString(a, """
                package demo;
                public class A {
                    public static int one() { return 1; }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(b, """
                package demo;
                public class B {
                    public static int two() { return 2; }
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(main, """
                package demo;
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(A.one() + B.two());
                        System.out.println(demo.A.one());
                    }
                }
                """, StandardCharsets.UTF_8);

        List<SourceUnit> forward = List.of(
                SourceUnit.ofPath(a, Files.readString(a)),
                SourceUnit.ofPath(b, Files.readString(b)),
                SourceUnit.ofPath(main, Files.readString(main)));
        List<SourceUnit> reverse = new ArrayList<>(forward);
        Collections.reverse(reverse);

        assertEquals("3\n1\n", compileAndRun(dir.resolve("out1"), forward));
        assertEquals("3\n1\n", compileAndRun(dir.resolve("out2"), reverse));
    }

    @Test
    void o0AndDefaultOptAgree(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path main = dir.resolve("Main.jn");
        Files.writeString(main, """
                public class Main {
                    public static void main(String[] args) {
                        int x = 7;
                        int y = 3;
                        System.out.println(x / y);
                        System.out.println(x % y);
                        System.out.println(x + y * 2);
                    }
                }
                """, StandardCharsets.UTF_8);
        SourceUnit u = SourceUnit.ofPath(main, Files.readString(main));
        String o0 = compileAndRunOpt(dir.resolve("o0"), List.of(u), 0);
        String o1 = compileAndRunOpt(dir.resolve("o1"), List.of(u), 1);
        assertEquals(o0, o1);
        assertEquals("2\n1\n13\n", o0);
    }

    private static String compileAndRun(Path out, List<SourceUnit> units) throws Exception {
        return compileAndRunOpt(out, units, 1);
    }

    private static String compileAndRunOpt(Path out, List<SourceUnit> units, int opt) throws Exception {
        CompilerOptions optn = new CompilerOptions();
        optn.setSourceFile(Path.of(units.getLast().path()));
        optn.setProgramSources(ProgramSources.of(units));
        optn.setOutputFile(out);
        optn.setOptLevel(opt);
        CompileResult cr = new JNativeCompiler().compile(optn);
        assertTrue(cr.success(), () -> cr.diagnostics().stream().map(Object::toString)
                .reduce("", (a, b) -> a + "\n" + b));
        Path bin = cr.executable();
        Process p = new ProcessBuilder(bin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        assertTrue(p.waitFor(30, TimeUnit.SECONDS));
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), stdout);
        return stdout;
    }

    private static DiagnosticReporter analyzeUnits(List<SourceUnit> units) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        MultiUnitFrontend.analyzeProgram(ProgramSources.of(units), reporter);
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
