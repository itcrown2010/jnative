package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.ProgramSources;
import io.jnative.compiler.SourceUnit;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import io.jnative.semantic.SemanticAnalyzer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Closure reliability: escape, capture env, GC, nested, this, method refs, O0/opt, Java compare.
 */
class ClosureEndToEndTest {

    @TempDir
    Path tmp;

    private static final String SHARED = """
            interface IntUnary { int apply(int value); }
            interface StringSupplier { String get(); }
            interface BoxToInt { int apply(Box b); }
            class Box {
                int v;
                Box(int v) { this.v = v; }
                int get() { return v; }
                void set(int x) { v = x; }
            }
            class Holder {
                int field;
                Holder(int f) { this.field = f; }
                IntUnary addField() { return x -> x + this.field; }
            }
            class Functions {
                static IntUnary addBy(int base) {
                    return value -> value + base;
                }
                static IntUnary nest(int a) {
                    int b = 10;
                    return x -> {
                        IntUnary inner = y -> y + a + b;
                        return inner.apply(x);
                    };
                }
                static StringSupplier captureStr(String s) {
                    return () -> s;
                }
                static int doubleIt(int x) { return x * 2; }
            }
            """;

    @Test
    void escapeCaptureAfterReturnAndGc() throws Exception {
        String src = SHARED + """
                public class Main {
                    public static void main(String[] args) {
                        IntUnary f = Functions.addBy(100);
                        System.gc();
                        System.out.println(f.apply(23));
                    }
                }
                """;
        assertO0AndOpt(src, "123\n");
        assertJavaAgrees("""
                interface IntUnary { int apply(int value); }
                class Functions {
                    static IntUnary addBy(int base) { return value -> value + base; }
                }
                public class Main {
                    public static void main(String[] args) {
                        IntUnary f = Functions.addBy(100);
                        System.out.println(f.apply(23));
                    }
                }
                """, "123\n");
    }

    @Test
    void twoClosuresIndependent() throws Exception {
        String src = SHARED + """
                public class Main {
                    public static void main(String[] args) {
                        IntUnary a = Functions.addBy(1);
                        IntUnary b = Functions.addBy(2);
                        System.out.println(a.apply(0));
                        System.out.println(b.apply(0));
                    }
                }
                """;
        assertO0AndOpt(src, "1\n2\n");
    }

    @Test
    void nestedLambdaDifferentFrames() throws Exception {
        String src = SHARED + """
                public class Main {
                    public static void main(String[] args) {
                        IntUnary g = Functions.nest(5);
                        System.gc();
                        System.out.println(g.apply(1));
                    }
                }
                """;
        assertO0AndOpt(src, "16\n");
    }

    @Test
    void captureChineseStringAndObjectFieldMutation() throws Exception {
        String src = SHARED + """
                public class Main {
                    public static void main(String[] args) {
                        StringSupplier ss = Functions.captureStr("订单🍎");
                        Box box = new Box(7);
                        IntUnary h = x -> x + box.v;
                        box.set(9);
                        System.gc();
                        System.out.println(ss.get().length());
                        System.out.println(ss.get().indexOf("🍎") >= 0 ? 1 : 0);
                        System.out.println(h.apply(1));
                    }
                }
                """;
        // "订单🍎" = 2 + 2 = 4 UTF-16 units
        assertO0AndOpt(src, "4\n1\n10\n");
    }

    @Test
    void captureThisInstanceField() throws Exception {
        String src = SHARED + """
                public class Main {
                    public static void main(String[] args) {
                        Holder h = new Holder(3);
                        IntUnary t = h.addField();
                        System.gc();
                        System.out.println(t.apply(4));
                    }
                }
                """;
        assertO0AndOpt(src, "7\n");
    }

    @Test
    void rejectsNonEffectivelyFinal() {
        DiagnosticReporter r = analyze("""
                interface IntUnary { int apply(int value); }
                public class Main {
                    public static void main(String[] args) {
                        int base = 1;
                        IntUnary f = x -> x + base;
                        base = 2;
                        System.out.println(f.apply(0));
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream()
                .anyMatch(d -> d.message().contains("effectively final")));
    }

    @Test
    void closuresInListSurviveGc() throws Exception {
        String src = SHARED + """
                public class Main {
                    public static void main(String[] args) {
                        ArrayList<IntUnary> list = new ArrayList<IntUnary>();
                        int i = 0;
                        while (i < 8) {
                            list.add(Functions.addBy(i));
                            i = i + 1;
                        }
                        System.gc();
                        int sum = 0;
                        i = 0;
                        while (i < list.size()) {
                            IntUnary f = list.get(i);
                            sum = sum + f.apply(0);
                            i = i + 1;
                        }
                        System.out.println(sum);
                    }
                }
                """;
        assertO0AndOpt(src, "28\n");
    }

    @Test
    void lambdaExceptionAndFinally() throws Exception {
        String src = SHARED + """
                public class Main {
                    public static void main(String[] args) {
                        IntUnary f = x -> {
                            if (x < 0) {
                                throw new RuntimeException("neg");
                            }
                            return x + 1;
                        };
                        int flag = 0;
                        try {
                            f.apply(-1);
                            System.out.println(0);
                        } catch (RuntimeException e) {
                            System.out.println(1);
                        } finally {
                            flag = 42;
                            System.out.println(flag);
                        }
                        System.out.println(f.apply(41));
                    }
                }
                """;
        assertO0AndOpt(src, "1\n42\n42\n");
    }

    @Test
    void boundMethodRefReceiverEvaluatedOnce() throws Exception {
        String src = """
                interface IntUnary { int apply(int value); }
                class Counter {
                    int n;
                    Counter(int n) { this.n = n; }
                    int add(int x) { return x + n; }
                }
                class Factory {
                    static int evals;
                    static Counter once() {
                        evals = evals + 1;
                        return new Counter(10);
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Factory.evals = 0;
                        IntUnary f = Factory.once()::add;
                        System.out.println(Factory.evals);
                        System.out.println(f.apply(5));
                        System.out.println(f.apply(6));
                        System.out.println(Factory.evals);
                    }
                }
                """;
        assertO0AndOpt(src, "1\n15\n16\n1\n");
    }

    @Test
    void unboundInstanceMethodRef() throws Exception {
        String src = SHARED + """
                public class Main {
                    public static void main(String[] args) {
                        BoxToInt g = Box::get;
                        Box b = new Box(41);
                        System.out.println(g.apply(b));
                    }
                }
                """;
        assertO0AndOpt(src, "41\n");
    }

    @Test
    void constructorRefRejectedClearly() {
        DiagnosticReporter r = analyze("""
                interface BoxSupplier { Box get(); }
                class Box { Box() {} }
                public class Main {
                    public static void main(String[] args) {
                        BoxSupplier s = Box::new;
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(r.diagnostics().stream()
                .anyMatch(d -> d.message().contains("constructor method references")
                        || d.message().contains("Type::new")
                        || d.message().contains("::new")),
                () -> r.diagnostics().toString());
    }

    @Test
    void wasmRejectsCallableClearly() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path file = tmp.resolve("WasmLam.java");
        Files.writeString(file, """
                public class WasmLam {
                    public static void main(String[] args) {
                        IntUnaryFunction f = x -> x + 1;
                        System.out.println(f.apply(1));
                    }
                }
                """, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(file);
        options.setOutputFile(tmp.resolve("wasm-out"));
        options.setTarget("wasm32-browser");
        CompileResult cr = new JNativeCompiler().compile(options);
        assertFalse(cr.success());
        String msg = cr.diagnostics().stream().map(Object::toString).reduce("", (a, b) -> a + b);
        assertTrue(msg.contains("MakeCallable") || msg.contains("callable") || msg.contains("lambda"),
                () -> msg);
    }

    private void assertO0AndOpt(String src, String expected) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        assertEquals(expected, compileRun(tmp.resolve("c0"), src, 0), "O0");
        assertEquals(expected, compileRun(tmp.resolve("c1"), src, 1), "O1");
    }

    private void assertJavaAgrees(String javaSrc, String expected) throws Exception {
        Path dir = tmp.resolve("java-" + System.nanoTime());
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("Main.java"), javaSrc, StandardCharsets.UTF_8);
        Process jc = new ProcessBuilder("javac", "Main.java")
                .directory(dir.toFile()).redirectErrorStream(true).start();
        assertTrue(jc.waitFor(60, TimeUnit.SECONDS));
        assertEquals(0, jc.exitValue(), new String(jc.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        Process jr = new ProcessBuilder("java", "-cp", ".", "Main")
                .directory(dir.toFile()).redirectErrorStream(true).start();
        assertTrue(jr.waitFor(60, TimeUnit.SECONDS));
        String out = new String(jr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, jr.exitValue(), out);
        assertEquals(expected, out, "Java reference");
    }

    private static String compileRun(Path out, String src, int opt) throws Exception {
        Path file = out.getParent().resolve(out.getFileName() + ".java");
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
        var tokens = new Lexer(src, "t.java").tokenize();
        var unit = new Parser(tokens, reporter).parse("t.java");
        if (!reporter.hasErrors()) {
            new SemanticAnalyzer(reporter).analyze(unit);
        }
        return reporter;
    }
}
