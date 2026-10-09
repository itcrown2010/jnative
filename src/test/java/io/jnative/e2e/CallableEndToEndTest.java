package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.ir.IrPrinter;
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
class CallableEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void nonCapturingLambda() throws Exception {
        assertNative("""
                public class Main {
                    public static void main(String[] args) {
                        IntUnaryFunction f = x -> x + 1;
                        System.out.println(f.apply(41));
                    }
                }
                """, "42\n");
    }

    @Test
    void capturingClosureReturn() throws Exception {
        assertNative("""
                public class Main {
                    static IntUnaryFunction create(int base) {
                        return x -> x + base;
                    }
                    public static void main(String[] args) {
                        IntUnaryFunction f = create(100);
                        System.gc();
                        System.out.println(f.apply(23));
                    }
                }
                """, "123\n");
    }

    @Test
    void closureGcKeepsManagedRef() throws Exception {
        assertNative("""
                class Person {
                    int value;
                }
                public class Main {
                    static IntUnaryFunction create() {
                        Person p = new Person();
                        p.value = 77;
                        return x -> x + p.value;
                    }
                    public static void main(String[] args) {
                        IntUnaryFunction f = create();
                        System.gc();
                        System.out.println(f.apply(10));
                    }
                }
                """, "87\n");
    }

    @Test
    void staticMethodRef() throws Exception {
        assertNative("""
                public class Main {
                    static int inc(int x) { return x + 1; }
                    public static void main(String[] args) {
                        IntUnaryFunction f = Main::inc;
                        System.out.println(f.apply(41));
                    }
                }
                """, "42\n");
    }

    @Test
    void boundMethodRef() throws Exception {
        assertNative("""
                class Counter {
                    int base = 10;
                    int add(int x) { return x + base; }
                }
                public class Main {
                    public static void main(String[] args) {
                        Counter c = new Counter();
                        IntUnaryFunction f = c::add;
                        System.out.println(f.apply(5));
                    }
                }
                """, "15\n");
    }

    @Test
    void virtualMethodRef() throws Exception {
        assertNative("""
                class Animal {
                    int speak(int x) { return x; }
                }
                class Dog extends Animal {
                    int speak(int x) { return x + 1; }
                }
                public class Main {
                    public static void main(String[] args) {
                        Animal a = new Dog();
                        IntUnaryFunction f = a::speak;
                        System.out.println(f.apply(41));
                    }
                }
                """, "42\n");
    }

    @Test
    void functionArgumentAndReturn() throws Exception {
        assertNative("""
                public class Main {
                    static int applyTwice(IntUnaryFunction f, int x) {
                        return f.apply(f.apply(x));
                    }
                    static IntUnaryFunction makeAdder(int n) {
                        return x -> x + n;
                    }
                    public static void main(String[] args) {
                        IntUnaryFunction f = makeAdder(1);
                        System.out.println(applyTwice(f, 40));
                    }
                }
                """, "42\n");
    }

    @Test
    void lambdaExceptionManaged() throws Exception {
        assertNative("""
                public class Main {
                    public static void main(String[] args) {
                        IntUnaryFunction f = x -> {
                            if (x < 0) {
                                throw new RuntimeException("neg");
                            }
                            return x + 1;
                        };
                        try {
                            f.apply(-1);
                            System.out.println(0);
                        } catch (RuntimeException e) {
                            System.out.println(1);
                        }
                        System.out.println(f.apply(41));
                    }
                }
                """, "1\n42\n");
    }

    @Test
    void threadRunnableLambda() throws Exception {
        assertNative("""
                public class Main {
                    public static void main(String[] args) {
                        Thread t = new Thread(() -> System.out.println(42));
                        t.start();
                        t.join();
                    }
                }
                """, "42\n");
    }

    @Test
    void threadCaptureWithAtomicInt() throws Exception {
        assertNative("""
                public class Main {
                    public static void main(String[] args) {
                        AtomicInt counter = new AtomicInt(0);
                        Thread t = new Thread(() -> {
                            int i = 0;
                            while (i < 1000) {
                                counter.incrementAndGet();
                                i = i + 1;
                            }
                        });
                        t.start();
                        t.join();
                        System.out.println(counter.get());
                    }
                }
                """, "1000\n");
    }

    @Test
    void finalManagedAcceptance() throws Exception {
        assertNative("""
                public class Main {
                    static IntUnaryFunction create(int base) {
                        return x -> x + base;
                    }
                    public static void main(String[] args) {
                        IntUnaryFunction f = create(100);
                        Thread t = new Thread(() -> System.out.println(f.apply(23)));
                        t.start();
                        t.join();
                    }
                }
                """, "123\n");
    }

    @Test
    void emitIrStructural() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve("EmitIr.java");
        Files.writeString(javaFile, """
                public class Main {
                    public static void main(String[] args) {
                        IntUnaryFunction f = x -> x + 1;
                        System.out.println(f.apply(41));
                    }
                }
                """, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setEmitIr(true);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertNotNull(result.ir());
        String text = IrPrinter.print(result.ir());
        assertTrue(text.contains("make_callable"), text);
        assertTrue(text.contains("invoke_callable"), text);
    }

    @Test
    void emitLlvmIndirectCall() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve("EmitLlvm.java");
        Files.writeString(javaFile, """
                public class Main {
                    public static void main(String[] args) {
                        IntUnaryFunction f = x -> x + 1;
                        System.out.println(f.apply(41));
                    }
                }
                """, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setEmitLlvm(true);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertNotNull(result.llvmIr());
        assertTrue(result.llvmIr().contains("call i32 @jnative_invoke_i32_i32(")
                        || result.llvmIr().contains("call i32 %"),
                result.llvmIr());
    }

    private void assertNative(String source, String expectedStdout) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");

        Path javaFile = tempDir.resolve("Callable.java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("Callable");

        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);

        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());

        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "timeout");
        assertEquals(0, process.exitValue(), stdout);
        assertEquals(expectedStdout, stdout);
    }
}
