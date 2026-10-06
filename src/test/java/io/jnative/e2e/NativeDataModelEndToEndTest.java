package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.ffi.NativeStructLayout;
import io.jnative.ffi.TargetAbi;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import io.jnative.semantic.NativeStructSymbol;
import io.jnative.semantic.SemanticAnalyzer;
import io.jnative.semantic.TypedCompilationUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NativeDataModelEndToEndTest {

    @TempDir
    Path tempDir;

    private static Path libDir;
    private static boolean fixtureReady;

    @BeforeAll
    static void buildCFixture() throws Exception {
        Optional<Toolchain> toolchain = Toolchain.detect();
        Assumptions.assumeTrue(toolchain.isPresent(), "clang not available");
        Path resources = Path.of("src/test/resources/native_fixtures/point_sum.c").toAbsolutePath();
        Assumptions.assumeTrue(Files.isRegularFile(resources), "C fixture missing");

        libDir = Files.createTempDirectory("jnative-test-lib");
        Path obj = libDir.resolve("point_sum.o");
        String os = System.getProperty("os.name", "").toLowerCase();
        Path lib = os.contains("mac") || os.contains("darwin")
                ? libDir.resolve("libjnative_test.dylib")
                : libDir.resolve("libjnative_test.so");

        ProcessBuilder compile = new ProcessBuilder(
                toolchain.get().clang().toString(),
                "-c", resources.toString(),
                "-o", obj.toString());
        compile.redirectErrorStream(true);
        Process cp = compile.start();
        String cout = new String(cp.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        Assumptions.assumeTrue(cp.waitFor(30, TimeUnit.SECONDS) && cp.exitValue() == 0, cout);

        ProcessBuilder link = new ProcessBuilder(
                toolchain.get().clang().toString(),
                "-shared", obj.toString(),
                "-o", lib.toString());
        link.redirectErrorStream(true);
        Process lp = link.start();
        String lout = new String(lp.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        fixtureReady = lp.waitFor(30, TimeUnit.SECONDS) && lp.exitValue() == 0;
        Assumptions.assumeTrue(fixtureReady, lout);
    }

    @Test
    void pointSumCAbiE2E() throws Exception {
        Assumptions.assumeTrue(fixtureReady);
        assertStdout("""
                @NativeStruct
                class Point {
                    double x;
                    double y;
                }

                class CLib {
                    @Native(symbol = "point_sum", library = "jnative_test")
                    static native double pointSum(NativePtr<Point> p);
                }

                public class Main {
                    public static void main(String[] args) {
                        @Stack
                        Point p = new Point();
                        p.x = 10.5;
                        p.y = 20.5;
                        double result = CLib.pointSum(Native.addressOf(p));
                        System.out.println(result);
                    }
                }
                """, "31\n"); // printf %g
    }

    @Test
    void layoutMatchesCFixture() throws Exception {
        Assumptions.assumeTrue(fixtureReady);
        DiagnosticReporter reporter = new DiagnosticReporter();
        String source = """
                @NativeStruct
                class Point { double x; double y; }
                @NativeStruct
                class Mixed { byte a; long b; short c; }
                @NativeStruct
                class Line { Point a; Point b; }
                public class Main { public static void main(String[] args) {} }
                """;
        var unit = new Parser(new Lexer(source, "L.java").tokenize(), reporter).parse("L.java");
        TypedCompilationUnit typed = new SemanticAnalyzer(reporter).analyze(unit);
        assertFalse(reporter.hasErrors(), reporter.diagnostics()::toString);

        NativeStructLayout point = typed.requireNativeStruct("Point").layout();
        NativeStructLayout mixed = typed.requireNativeStruct("Mixed").layout();
        NativeStructLayout line = typed.requireNativeStruct("Line").layout();

        // Cross-check via small C probe linked against the same fixture types
        // by reading sizeof/offset helpers from the shared library through JNative.
        assertStdout("""
                class Probe {
                    @Native(library = "jnative_test", symbol = "jn_sizeof_Point")
                    static native long sizeofPoint();
                    @Native(library = "jnative_test", symbol = "jn_offsetof_Point_x")
                    static native long offX();
                    @Native(library = "jnative_test", symbol = "jn_offsetof_Point_y")
                    static native long offY();
                    @Native(library = "jnative_test", symbol = "jn_sizeof_Mixed")
                    static native long sizeofMixed();
                    @Native(library = "jnative_test", symbol = "jn_offsetof_Mixed_a")
                    static native long offMa();
                    @Native(library = "jnative_test", symbol = "jn_offsetof_Mixed_b")
                    static native long offMb();
                    @Native(library = "jnative_test", symbol = "jn_offsetof_Mixed_c")
                    static native long offMc();
                    @Native(library = "jnative_test", symbol = "jn_sizeof_Line")
                    static native long sizeofLine();
                    @Native(library = "jnative_test", symbol = "jn_offsetof_Line_b")
                    static native long offLb();
                }
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(Probe.sizeofPoint());
                        System.out.println(Probe.offX());
                        System.out.println(Probe.offY());
                        System.out.println(Probe.sizeofMixed());
                        System.out.println(Probe.offMa());
                        System.out.println(Probe.offMb());
                        System.out.println(Probe.offMc());
                        System.out.println(Probe.sizeofLine());
                        System.out.println(Probe.offLb());
                    }
                }
                """,
                point.size() + "\n"
                        + point.findField("x").orElseThrow().offset() + "\n"
                        + point.findField("y").orElseThrow().offset() + "\n"
                        + mixed.size() + "\n"
                        + mixed.findField("a").orElseThrow().offset() + "\n"
                        + mixed.findField("b").orElseThrow().offset() + "\n"
                        + mixed.findField("c").orElseThrow().offset() + "\n"
                        + line.size() + "\n"
                        + line.findField("b").orElseThrow().offset() + "\n");
    }

    @Test
    void emitIrShowsStackAllocAndNativeField() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent());
        Path javaFile = tempDir.resolve("Emit.java");
        Files.writeString(javaFile, """
                @NativeStruct
                class Point { double x; double y; }
                class Lib {
                    @Native(symbol = "point_sum")
                    static native double process(NativePtr<Point> p);
                }
                public class Main {
                    public static void main(String[] args) {
                        @Stack
                        Point p = new Point();
                        p.x = 10.0;
                        Lib.process(Native.addressOf(p));
                    }
                }
                """, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setEmitIr(true);
        // Capture IR via llvm path as well
        options.setEmitIr(false);
        options.setEmitLlvm(true);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        String llvm = result.llvmIr();
        assertTrue(llvm.contains("%struct.Point = type"), llvm);
        assertTrue(llvm.contains("alloca %struct.Point"), llvm);
        assertTrue(llvm.contains("getelementptr"), llvm);
        assertTrue(llvm.contains("call double @point_sum(") || llvm.contains("call double @process("), llvm);
    }

    @Test
    void pointerNullAllowed() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent());
        assertStdout("""
                @NativeStruct
                class Point { double x; double y; }
                public class Main {
                    public static void main(String[] args) {
                        NativePtr<Point> p = NativePtr.NULL;
                        if (p == NativePtr.NULL) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "1\n", false);
    }

    @Test
    void nativeAllocateStructAndFree() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent());
        assertStdout("""
                @NativeStruct
                class Point { double x; double y; }
                public class Main {
                    public static void main(String[] args) {
                        NativePtr<Point> p = Native.allocate<Point>();
                        p.x = 1.5;
                        p.y = 2.5;
                        System.out.println(p.x);
                        NativeMemory.free(p);
                    }
                }
                """, "1.5\n", false); // %g keeps trailing .5
    }

    private void assertStdout(String source, String expected) throws Exception {
        assertStdout(source, expected, true);
    }

    private void assertStdout(String source, String expected, boolean needFixture) throws Exception {
        if (needFixture) {
            Assumptions.assumeTrue(fixtureReady);
        }
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve("Ndm.java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("Ndm");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        if (needFixture) {
            options.addLibraryPath(libDir);
        }
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());

        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        if (needFixture) {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("mac") || os.contains("darwin")) {
                pb.environment().put("DYLD_LIBRARY_PATH", libDir.toString());
            } else {
                String prev = pb.environment().getOrDefault("LD_LIBRARY_PATH", "");
                pb.environment().put("LD_LIBRARY_PATH",
                        prev.isBlank() ? libDir.toString() : libDir + ":" + prev);
            }
        }
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "timeout");
        assertEquals(0, process.exitValue(), stdout);
        assertEquals(expected, stdout);
    }
}
