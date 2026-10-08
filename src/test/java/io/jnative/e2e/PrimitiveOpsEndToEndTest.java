package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.ProgramSources;
import io.jnative.compiler.SourceUnit;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.compiler.MultiUnitFrontend;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Primitive cast, ternary, bitwise, and shift — native e2e vs javac/java reference.
 */
class PrimitiveOpsEndToEndTest {

    @TempDir
    Path tmp;

    @Test
    void groupingVsCastParse() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f(int x) {
                        return (x) + 1;
                    }
                    public static int g(long x) {
                        return (int) x;
                    }
                }
                """);
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void booleanCastRejected() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static void f() {
                        int x = (int) true;
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(messages(r).toLowerCase().contains("boolean"), messages(r));
    }

    @Test
    void floatCastRejected() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static void f(double d) {
                        int x = (int) d;
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(messages(r).toLowerCase().contains("float"), messages(r));
    }

    @Test
    void ternaryRequiresBoolean() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f() {
                        return 1 ? 2 : 3;
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(messages(r).toLowerCase().contains("boolean"), messages(r));
    }

    @Test
    void ternaryIncompatibleBranches() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static Object f(boolean b) {
                        return b ? 1 : "x";
                    }
                }
                """);
        assertTrue(r.hasErrors());
    }

    @Test
    void errorLocationOnUserFile() {
        DiagnosticReporter r = analyze("""
                package app;
                public class Bad {
                    public static void main(String[] args) {
                        int x = 1 ? 2 : 3;
                    }
                }
                """, "/proj/Bad.jn");
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream().anyMatch(d ->
                d.location() != null
                        && d.location().file() != null
                        && d.location().file().contains("Bad.jn")
                        && d.location().line() >= 4), messages(r));
    }

    @Test
    void nativeMatchesJavaReference(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String src = """
                public class Main {
                    static int sideA = 0;
                    static int sideB = 0;

                    static int bumpA() {
                        sideA = sideA + 1;
                        return sideA;
                    }

                    static int bumpB() {
                        sideB = sideB + 1;
                        return sideB;
                    }

                    public static void main(String[] args) {
                        long value = 258L;
                        int number = (int) value;
                        byte low = (byte) number;
                        System.out.println(number);
                        System.out.println((int) low);

                        int x = -1;
                        System.out.println(x << 1);
                        System.out.println(x >> 1);
                        System.out.println(x >>> 1);
                        System.out.println(5 << 33);
                        System.out.println(5L << 65);
                        System.out.println(-8 >> 2);
                        System.out.println(-8 >>> 2);

                        System.out.println(~0);
                        System.out.println(6 & 3);
                        System.out.println(6 | 3);
                        System.out.println(6 ^ 3);

                        int a = 1;
                        int b = 2;
                        System.out.println(true ? a : b);
                        System.out.println(false ? bumpA() : bumpB());
                        System.out.println(sideA);
                        System.out.println(sideB);

                        System.out.println((int) ((byte) 400));
                        System.out.println((int) 4294967297L);

                        char c = (char) 65535;
                        System.out.println((int) c);

                        boolean p = true;
                        boolean q = false;
                        System.out.println(p & q);
                        System.out.println(p | q);
                        System.out.println(p ^ q);

                        // boolean & evaluates both sides
                        sideA = 0;
                        sideB = 0;
                        boolean ignore = (bumpA() > 0) & (bumpB() > 0);
                        System.out.println(sideA);
                        System.out.println(sideB);
                        System.out.println(ignore ? 1 : 0);

                        int packed = encode(7, 3, 1);
                        System.out.println(fieldA(packed));
                        System.out.println(fieldB(packed));
                        System.out.println(flag(packed) ? 1 : 0);
                    }

                    static int encode(int a, int b, int f) {
                        return ((a & 15) << 4) | ((b & 7) << 1) | (f & 1);
                    }

                    static int fieldA(int v) {
                        return (v >> 4) & 15;
                    }

                    static int fieldB(int v) {
                        return (v >> 1) & 7;
                    }

                    static boolean flag(int v) {
                        return (v & 1) != 0;
                    }
                }
                """;
        String expected = javaReference(dir, src);
        String o0 = compileAndRun(dir.resolve("o0"), src, 0);
        String o1 = compileAndRun(dir.resolve("o1"), src, 1);
        assertEquals(expected, o0);
        assertEquals(expected, o1);
    }

    private static String javaReference(Path dir, String src) throws Exception {
        Path javaFile = dir.resolve("Main.java");
        Files.writeString(javaFile, src, StandardCharsets.UTF_8);
        Process compile = new ProcessBuilder("javac", javaFile.getFileName().toString())
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
        return analyze(src, "T.jn");
    }

    private static DiagnosticReporter analyze(String src, String path) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        MultiUnitFrontend.analyzeProgram(ProgramSources.of(new SourceUnit(path, src)), reporter);
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
