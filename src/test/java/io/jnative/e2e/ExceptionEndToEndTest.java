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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class ExceptionEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void basicCatch() throws Exception {
        assertStdout("""
                class MyException extends RuntimeException {
                    MyException(String message) { super(message); }
                }
                public class Main {
                    public static void main(String[] args) {
                        try {
                            throw new MyException("boom");
                        } catch (MyException e) {
                            System.out.println(1);
                        }
                    }
                }
                """, "1\n");
    }

    @Test
    void supertypeCatch() throws Exception {
        assertStdout("""
                class MyException extends RuntimeException {
                    MyException(String message) { super(message); }
                }
                public class Main {
                    public static void main(String[] args) {
                        try {
                            throw new MyException("boom");
                        } catch (RuntimeException e) {
                            System.out.println(2);
                        }
                    }
                }
                """, "2\n");
    }

    @Test
    void propagation() throws Exception {
        assertStdout("""
                class MyException extends RuntimeException {
                    MyException(String message) { super(message); }
                }
                public class Main {
                    static void c() { throw new MyException("boom"); }
                    static void b() { c(); }
                    static void a() {
                        try { b(); } catch (MyException e) { System.out.println(3); }
                    }
                    public static void main(String[] args) { a(); }
                }
                """, "3\n");
    }

    @Test
    void finallyCatch() throws Exception {
        assertStdout("""
                class MyException extends RuntimeException {
                    MyException(String message) { super(message); }
                }
                public class Main {
                    public static void main(String[] args) {
                        try {
                            throw new MyException("boom");
                        } catch (MyException e) {
                            System.out.println(1);
                        } finally {
                            System.out.println(2);
                        }
                    }
                }
                """, "1\n2\n");
    }

    @Test
    void arrayIndexOutOfBoundsCaught() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        int[] a = new int[2];
                        try {
                            System.out.println(a[10]);
                        } catch (ArrayIndexOutOfBoundsException e) {
                            System.out.println(9);
                        }
                    }
                }
                """, "9\n");
    }

    @Test
    void nullPointerCaught() throws Exception {
        assertStdout("""
                class Person { int age; }
                public class Main {
                    public static void main(String[] args) {
                        Person p = null;
                        try {
                            System.out.println(p.age);
                        } catch (NullPointerException e) {
                            System.out.println(8);
                        }
                    }
                }
                """, "8\n");
    }

    @Test
    void constructorThrow() throws Exception {
        assertStdout("""
                class MyException extends RuntimeException {
                    MyException(String message) { super(message); }
                }
                class Person {
                    Person() { throw new MyException("ctor"); }
                }
                public class Main {
                    public static void main(String[] args) {
                        try {
                            Person p = new Person();
                        } catch (MyException e) {
                            System.out.println(7);
                        }
                    }
                }
                """, "7\n");
    }

    @Test
    void virtualCallThrow() throws Exception {
        assertStdout("""
                class MyException extends RuntimeException {
                    MyException(String message) { super(message); }
                }
                class Animal { void run() { } }
                class Dog extends Animal {
                    void run() { throw new MyException("dog"); }
                }
                public class Main {
                    public static void main(String[] args) {
                        Animal a = new Dog();
                        try { a.run(); } catch (MyException e) { System.out.println(6); }
                    }
                }
                """, "6\n");
    }

    @Test
    void gcSafeExceptionMessage() throws Exception {
        assertStdout("""
                class Person { int x; }
                class MyException extends RuntimeException {
                    MyException(String message) { super(message); }
                }
                public class Main {
                    public static void main(String[] args) {
                        try {
                            throw new MyException("alive");
                        } catch (MyException e) {
                            for (int i = 0; i < 10000; i = i + 1) {
                                Person p = new Person();
                            }
                            System.gc();
                            System.out.println(e.getMessage());
                        }
                    }
                }
                """, "alive\n", Map.of("JNATIVE_GC_STRESS", "1"));
    }

    @Test
    void uncaughtException() throws Exception {
        assertUncaught("""
                class MyException extends RuntimeException {
                    MyException(String message) { super(message); }
                }
                public class Main {
                    public static void main(String[] args) {
                        throw new MyException("fatal");
                    }
                }
                """, "MyException", "fatal");
    }

    @Test
    void uncaughtPrintsStackSymbols() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        NativeRun r = run("""
                class MyException extends RuntimeException {
                    MyException(String message) { super(message); }
                }
                public class Main {
                    static void deep() { throw new MyException("stack"); }
                    public static void main(String[] args) {
                        deep();
                    }
                }
                """, "UncaughtStack", Map.of());
        assertTrue(r.compileSuccess, r.diagnostics);
        assertNotEquals(0, r.exitCode);
        String combined = r.stdout + r.stderr;
        assertTrue(combined.contains("MyException"), combined);
        assertTrue(combined.contains("stack"), combined);
        assertTrue(
                combined.contains("Main") || combined.contains("_JN_"),
                "expected demangled or mangled frame, got:\n" + combined);
        assertTrue(combined.contains("    at "), combined);
    }

    @Test
    void causeChainGetCause() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        RuntimeException inner = new RuntimeException("inner-cause");
                        RuntimeException outer = new RuntimeException("outer", inner);
                        Throwable c = outer.getCause();
                        System.out.println(c.getMessage());
                    }
                }
                """, "inner-cause\n");
    }

    @Test
    void coreAcceptance() throws Exception {
        assertStdout("""
                class MyException extends RuntimeException {
                    MyException(String message) { super(message); }
                }
                public class Main {
                    static void c() { throw new MyException("JNative"); }
                    static void b() { c(); }
                    static void a() {
                        try { b(); } catch (MyException e) {
                            System.out.println(e.getMessage());
                        } finally {
                            System.out.println("finally");
                        }
                    }
                    public static void main(String[] args) { a(); }
                }
                """, "JNative\nfinally\n");
    }

    @Test
    void returnThroughFinally() throws Exception {
        assertStdout("""
                public class Main {
                    static int foo() {
                        try {
                            return 1;
                        } finally {
                            System.out.println(2);
                        }
                    }
                    public static void main(String[] args) {
                        System.out.println(foo());
                    }
                }
                """, "2\n1\n");
    }

    @Test
    void finallyOverrideReturn() throws Exception {
        assertStdout("""
                public class Main {
                    static int foo() {
                        try {
                            return 1;
                        } finally {
                            return 2;
                        }
                    }
                    public static void main(String[] args) {
                        System.out.println(foo());
                    }
                }
                """, "2\n");
    }

    @Test
    void nestedFinallyOrder() throws Exception {
        assertStdout("""
                public class Main {
                    static int foo() {
                        try {
                            try {
                                return 1;
                            } finally {
                                System.out.println(2);
                            }
                        } finally {
                            System.out.println(3);
                        }
                    }
                    public static void main(String[] args) {
                        System.out.println(foo());
                    }
                }
                """, "2\n3\n1\n");
    }

    @Test
    void finallyPreservesException() throws Exception {
        assertStdout("""
                class Boom extends RuntimeException {
                    Boom(String message) { super(message); }
                }
                public class Main {
                    public static void main(String[] args) {
                        try {
                            try {
                                throw new Boom("x");
                            } finally {
                                System.out.println(1);
                            }
                        } catch (Boom e) {
                            System.out.println(e.getMessage());
                        }
                    }
                }
                """, "1\nx\n");
    }

    @Test
    void finallyThrowReplacesPending() throws Exception {
        assertStdout("""
                class A extends RuntimeException {
                    A(String message) { super(message); }
                }
                class B extends RuntimeException {
                    B(String message) { super(message); }
                }
                public class Main {
                    public static void main(String[] args) {
                        try {
                            try {
                                throw new A("a");
                            } finally {
                                throw new B("b");
                            }
                        } catch (RuntimeException e) {
                            System.out.println(e.getMessage());
                        }
                    }
                }
                """, "b\n");
    }

    @Test
    void throwNullBecomesNpe() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        try {
                            throw null;
                        } catch (NullPointerException e) {
                            System.out.println(1);
                        }
                    }
                }
                """, "1\n");
    }

    @Test
    void catchOrderSubtypeFirst() throws Exception {
        assertStdout("""
                class Boom extends RuntimeException {
                    Boom(String message) { super(message); }
                }
                public class Main {
                    public static void main(String[] args) {
                        try {
                            throw new Boom("x");
                        } catch (Boom e) {
                            System.out.println(1);
                        } catch (RuntimeException e) {
                            System.out.println(2);
                        }
                    }
                }
                """, "1\n");
    }

    @Test
    void rethrowFromCatch() throws Exception {
        assertStdout("""
                class Boom extends RuntimeException {
                    Boom(String message) { super(message); }
                }
                public class Main {
                    public static void main(String[] args) {
                        try {
                            try {
                                throw new Boom("x");
                            } catch (Boom e) {
                                throw e;
                            }
                        } catch (Boom e) {
                            System.out.println(e.getMessage());
                        }
                    }
                }
                """, "x\n");
    }

    @Test
    void breakThroughFinally() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        int i = 0;
                        while (i < 3) {
                            try {
                                i = i + 1;
                                if (i == 2) { break; }
                            } finally {
                                System.out.println(i);
                            }
                        }
                        System.out.println(9);
                    }
                }
                """, "1\n2\n9\n");
    }

    @Test
    void returnExprEvaluatedBeforeFinally() throws Exception {
        assertStdout("""
                public class Main {
                    static int side;
                    static int val() {
                        side = 1;
                        return side;
                    }
                    static int foo() {
                        try {
                            return val();
                        } finally {
                            side = 2;
                            System.out.println(side);
                        }
                    }
                    public static void main(String[] args) {
                        System.out.println(foo());
                    }
                }
                """, "2\n1\n");
    }

    @Test
    void unreachableCatchDiagnosed() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve("Unreach.java");
        Files.writeString(javaFile, """
                class Boom extends RuntimeException {
                    Boom(String message) { super(message); }
                }
                public class Main {
                    public static void main(String[] args) {
                        try {
                            throw new Boom("x");
                        } catch (RuntimeException e) {
                            System.out.println(1);
                        } catch (Boom e) {
                            System.out.println(2);
                        }
                    }
                }
                """, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("Unreach");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertFalse(result.success());
        assertTrue(result.diagnostics().toString().contains("unreachable catch"),
                result.diagnostics().toString());
    }

    @Test
    void optLevelsAgreeOnFinally() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String src = """
                public class Main {
                    static int foo() {
                        try { return 1; } finally { System.out.println(2); }
                    }
                    public static void main(String[] args) {
                        System.out.println(foo());
                    }
                }
                """;
        NativeRun o0 = runOpt(src, "FinO0", 0);
        NativeRun oDef = runOpt(src, "FinODef", -1);
        assertTrue(o0.compileSuccess, o0.diagnostics);
        assertTrue(oDef.compileSuccess, oDef.diagnostics);
        assertEquals("2\n1\n", o0.stdout, o0.stderr);
        assertEquals(o0.stdout, oDef.stdout);
    }

    private NativeRun runOpt(String source, String name, int optLevel) throws Exception {
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        if (optLevel >= 0) {
            options.setOptLevel(optLevel);
        }
        CompileResult result = new JNativeCompiler().compile(options);
        if (!result.success()) {
            return new NativeRun(false, result.diagnostics().toString(), "", "", 1);
        }
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new NativeRun(false, "timeout", stdout, stderr, 124);
        }
        return new NativeRun(true, "", stdout, stderr, process.exitValue());
    }

    private void assertStdout(String source, String expected) throws Exception {
        assertStdout(source, expected, Map.of());
    }

    private void assertStdout(String source, String expected, Map<String, String> env) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        NativeRun r = run(source, "Ex", env);
        assertTrue(r.compileSuccess, r.diagnostics);
        assertEquals(expected, r.stdout, r.stderr);
        assertEquals(0, r.exitCode);
    }

    private void assertUncaught(String source, String className, String message) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        NativeRun r = run(source, "Uncaught", Map.of());
        assertTrue(r.compileSuccess, r.diagnostics);
        assertNotEquals(0, r.exitCode);
        String combined = r.stdout + r.stderr;
        assertTrue(combined.contains(className), combined);
        assertTrue(combined.contains(message), combined);
    }

    private NativeRun run(String source, String name, Map<String, String> env) throws Exception {
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        if (!result.success()) {
            return new NativeRun(false, result.diagnostics().toString(), "", "", 1);
        }
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.environment().putAll(env);
        pb.redirectErrorStream(false);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new NativeRun(false, "native process timed out", stdout, stderr, 124);
        }
        return new NativeRun(true, "", stdout, stderr, process.exitValue());
    }

    private record NativeRun(boolean compileSuccess, String diagnostics, String stdout, String stderr, int exitCode) {
    }
}
