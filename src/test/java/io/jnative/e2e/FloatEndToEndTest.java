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
 * float/double semantics: literals, promotion, ops, casts, NaN/Inf/-0 — native vs javac.
 */
class FloatEndToEndTest {

    @TempDir
    Path tmp;

    @Test
    void hexFloatAndDigitSeparatorRejected() {
        DiagnosticReporter hex = analyze("""
                public class T {
                    static double x = 0x1.0p0;
                }
                """);
        assertTrue(hex.hasErrors());
        assertTrue(messages(hex).toLowerCase().contains("hex"), messages(hex));

        DiagnosticReporter sep = analyze("""
                public class T {
                    static double x = 1_000.0;
                }
                """);
        assertTrue(sep.hasErrors());
        assertTrue(messages(sep).toLowerCase().contains("separator")
                || messages(sep).toLowerCase().contains("digit"), messages(sep));
    }

    @Test
    void floatSuffixAndPromotionTypecheck() {
        DiagnosticReporter r = analyze("""
                public class T {
                    static float f = 1.5f;
                    static double d = 1.5d;
                    static double mix(int a, float b, long c) {
                        return a + b + c;
                    }
                    static float tern(boolean p, float a, float b) {
                        return p ? a : b;
                    }
                    static float overload(float x) { return x; }
                    static double overload(double x) { return x; }
                    static double pick() { return overload(1.0f); }
                }
                """);
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void nativeMatchesJavaReference() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String src = """
                public class Main {
                    static int flag(boolean b) {
                        if (b) {
                            return 1;
                        }
                        return 0;
                    }

                    static int bitsEq(double a, double b) {
                        // semantic equality for normals; NaN checked separately
                        if (a == b) {
                            return 1;
                        }
                        return 0;
                    }

                    public static void main(String[] args) {
                        // literals / suffixes
                        float f = 1.5f;
                        double d = 2.5d;
                        System.out.println((int) (f * 10.0f));
                        System.out.println((int) (d * 10.0));

                        // promotion int+float → float, +long → float then widen in expr
                        float p = 1 + 2.0f;
                        System.out.println((int) (p * 10.0f));
                        double q = 1L + 2.0;
                        System.out.println((int) (q * 10.0));

                        // ops
                        System.out.println((int) ((3.0 + 4.0) * 10.0));
                        System.out.println((int) ((10.0 - 3.0) * 10.0));
                        System.out.println((int) ((2.5 * 4.0) * 10.0));
                        System.out.println((int) ((9.0 / 2.0) * 10.0));
                        System.out.println((int) ((7.5 % 2.0) * 10.0));
                        System.out.println((int) ((0.0 - 7.5) % 2.0 * 10.0));

                        // float intermediate rounding (binary32)
                        float big = 16777216.0f;
                        System.out.println(flag(big + 1.0f == big));

                        // div by zero → Inf / NaN (not ArithmeticException)
                        double inf = 1.0 / 0.0;
                        double ninf = (0.0 - 1.0) / 0.0;
                        double nan = 0.0 / 0.0;
                        System.out.println(flag(inf > 0.0));
                        System.out.println(flag(ninf < 0.0));
                        System.out.println(flag(nan != nan));
                        System.out.println(flag(nan == nan));
                        System.out.println(flag(inf == inf));

                        // -0
                        double nz = 0.0 - 0.0;
                        double mz = 0.0 * (0.0 - 1.0);
                        System.out.println(flag(nz == 0.0));
                        System.out.println(flag(1.0 / mz < 0.0));

                        // comparisons with NaN: ordered preds false; != true
                        System.out.println(flag(nan < 1.0));
                        System.out.println(flag(nan > 1.0));
                        System.out.println(flag(nan <= 1.0));
                        System.out.println(flag(nan >= 1.0));
                        System.out.println(flag(nan != 1.0));

                        // casts toward zero / NaN→0 / saturate
                        System.out.println((int) 3.9);
                        System.out.println((int) (0.0 - 3.9));
                        System.out.println((int) nan);
                        System.out.println((int) inf);
                        System.out.println((int) ninf);
                        System.out.println((long) inf);
                        System.out.println((long) ninf);
                        System.out.println((long) nan);

                        // float ↔ double / int
                        double fd = (double) 1.25f;
                        float df = (float) 1.25;
                        System.out.println((int) (fd * 100.0));
                        System.out.println((int) (df * 100.0f));
                        System.out.println((int) ((float) 9));

                        // narrow after float→int
                        System.out.println((int) ((byte) 300.0));
                        System.out.println((int) ((char) 70000.0));

                        // ternary + overload selection
                        float t = true ? 1.5f : 2.5f;
                        System.out.println((int) (t * 10.0f));
                        System.out.println((int) (pick(1.0f) * 10.0));
                        System.out.println((int) (pick(1.0) * 10.0));

                        // unary -
                        System.out.println(flag(1.0 / (0.0 - 0.0) < 0.0));
                        System.out.println((int) ((0.0 - 2.5) * 10.0));

                        // subnormal smoke: very small positive
                        double tiny = 1.0e-320;
                        System.out.println(flag(tiny > 0.0));
                        System.out.println(flag(tiny / 10.0 >= 0.0));
                    }

                    static float pick(float x) {
                        return x + 0.5f;
                    }

                    static double pick(double x) {
                        return x + 1.5;
                    }
                }
                """;
        String expected = javaReference(tmp, src);
        String o0 = compileAndRun(tmp.resolve("o0"), src, 0);
        String o1 = compileAndRun(tmp.resolve("o1"), src, 1);
        assertEquals(expected, o0, "O0 vs Java");
        assertEquals(expected, o1, "O1 vs Java");
        assertEquals(o0, o1, "O0 vs O1");
    }

    @Test
    void constFoldMatchesRuntime() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        // Foldable literals vs runtime variables — same printed flags.
        String src = """
                public class Main {
                    static int flag(boolean b) {
                        if (b) { return 1; }
                        return 0;
                    }
                    public static void main(String[] args) {
                        double a = 1.5;
                        double b = 2.5;
                        System.out.println(flag(1.5 + 2.5 == a + b));
                        System.out.println(flag(1.5 * 2.5 == a * b));
                        System.out.println(flag((0.0 / 0.0) != (0.0 / 0.0)));
                        float fa = 1.0f;
                        float fb = 16777216.0f;
                        System.out.println(flag(1.0f + 16777216.0f == fa + fb));
                        System.out.println((int) (3.7 + 0.0));
                        System.out.println((int) (0.0 - 3.7));
                    }
                }
                """;
        String expected = javaReference(tmp, src);
        assertEquals(expected, compileAndRun(tmp.resolve("cf0"), src, 0));
        assertEquals(expected, compileAndRun(tmp.resolve("cf1"), src, 1));
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
        Path file = out.getParent().resolve("FloatMain-" + out.getFileName() + ".jn");
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
