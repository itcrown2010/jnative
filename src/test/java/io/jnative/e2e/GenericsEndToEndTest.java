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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** JNative 0.15 generics e2e: erasure, specialization, diamond, inference. */
class GenericsEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void boxStringErased() throws Exception {
        assertEquals("JNative\n", run("""
                class Box<T> {
                    T value;
                    Box(T value) { this.value = value; }
                    T get() { return value; }
                }
                public class Main {
                    public static void main(String[] args) {
                        Box<String> box = new Box<String>("JNative");
                        System.out.println(box.get());
                    }
                }
                """, "BoxString", Map.of(), 0));
    }

    @Test
    void boxIntSpecialized() throws Exception {
        assertEquals("42\n", run("""
                class Box<T> {
                    T value;
                    Box(T value) { this.value = value; }
                    T get() { return value; }
                }
                public class Main {
                    public static void main(String[] args) {
                        Box<int> value = new Box<int>(42);
                        System.out.println(value.get());
                    }
                }
                """, "BoxInt", Map.of(), 0));
    }

    @Test
    void diamondInference() throws Exception {
        assertEquals("JNative\n Native\n", run("""
                class Box<T> {
                    private T value;
                    Box(T value) { this.value = value; }
                    T get() { return value; }
                }
                public class Main {
                    public static void main(String[] args) {
                        Box<String> a = new Box<>("JNative");
                        Box<String> b = new Box<>(" Native");
                        System.out.println(a.get());
                        System.out.println(b.get());
                    }
                }
                """, "BoxDiamond", Map.of(), 0));
    }

    @Test
    void genericMethodInference() throws Exception {
        assertEquals("hello\n", run("""
                public class Main {
                    static <T> T identity(T value) {
                        return value;
                    }
                    public static void main(String[] args) {
                        String s = identity("hello");
                        System.out.println(s);
                    }
                }
                """, "GenMethod", Map.of(), 0));
    }

    @Test
    void genericInterfaceDispatch() throws Exception {
        assertEquals("3\n", run("""
                interface Mapper<T, R> {
                    R map(T value);
                }
                class LengthMapper implements Mapper<String, int> {
                    int map(String value) {
                        return value.length();
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Mapper<String, int> m = new LengthMapper();
                        System.out.println(m.map("abc"));
                    }
                }
                """, "GenIface", Map.of(), 0));
    }

    @Test
    void nestedGenericAndPair() throws Exception {
        assertEquals("ok\n", run("""
                class Box<T> {
                    T value;
                    Box(T value) { this.value = value; }
                    T get() { return value; }
                }
                class Pair<A, B> {
                    A first;
                    B second;
                    Pair(A first, B second) {
                        this.first = first;
                        this.second = second;
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Pair<String, Box<String>> p =
                            new Pair<String, Box<String>>("ok", new Box<String>("x"));
                        System.out.println(p.first);
                    }
                }
                """, "NestedPair", Map.of(), 0));
    }

    @Test
    void specializedLayoutInLlvm() {
        Path src = tempDir.resolve("SpecCheck.java");
        try {
            Files.writeString(src, """
                    class Box<T> {
                        T value;
                        Box(T value) { this.value = value; }
                        T get() { return value; }
                    }
                    public class Main {
                        public static void main(String[] args) {
                            Box<int> b = new Box<int>(7);
                            System.out.println(b.get());
                        }
                    }
                    """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(src);
        options.setEmitLlvm(true);
        options.setOptLevel(0);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertNotNull(result.llvmIr());
        assertTrue(result.llvmIr().contains("Box$P_i32") || result.llvmIr().contains("Box_P_i32"),
                result.llvmIr());
        assertTrue(result.llvmIr().contains("i32"), result.llvmIr());
    }

    @Test
    void rawTypeRejected() {
        CompileResult result = compileFail("""
                class Box<T> { T value; }
                public class Main {
                    public static void main(String[] args) {
                        Box box = null;
                    }
                }
                """);
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.message().toLowerCase().contains("raw")));
    }

    @Test
    void invariantAssignmentRejected() {
        CompileResult result = compileFail("""
                class Box<T> {
                    T value;
                    Box(T value) { this.value = value; }
                }
                public class Main {
                    public static void main(String[] args) {
                        Box<String> a = new Box<String>("x");
                        Box<int> b = a;
                    }
                }
                """);
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.message().toLowerCase().contains("incompatible")));
    }

    @Test
    void wrongArityRejected() {
        CompileResult result = compileFail("""
                class Box<T> { T value; }
                public class Main {
                    public static void main(String[] args) {
                        Box<String, int> b = null;
                    }
                }
                """);
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.message().toLowerCase().contains("wrong number")));
    }

    @Test
    void emitGenericsReport() {
        Path src = tempDir.resolve("EmitG.java");
        try {
            Files.writeString(src, """
                    class Box<T> {
                        T value;
                        Box(T value) { this.value = value; }
                        T get() { return value; }
                    }
                    public class Main {
                        public static void main(String[] args) {
                            Box<String> a = new Box<String>("x");
                            Box<int> b = new Box<int>(1);
                            System.out.println(a.get());
                            System.out.println(b.get());
                        }
                    }
                    """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(src);
        options.setEmitGenerics(true);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
    }

    @Test
    void irHasNoTypeVariable() {
        Path src = tempDir.resolve("IrErase.java");
        try {
            Files.writeString(src, """
                    class Box<T> {
                        T value;
                        Box(T value) { this.value = value; }
                        T get() { return value; }
                    }
                    public class Main {
                        public static void main(String[] args) {
                            Box<String> box = new Box<>("x");
                            System.out.println(box.get());
                        }
                    }
                    """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(src);
        options.setEmitIr(true);
        options.setOptLevel(0);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        String ir = IrPrinter.print(result.ir());
        assertFalse(ir.contains("TypeVariable"), ir);
    }

    private CompileResult compileFail(String source) {
        try {
            Path src = tempDir.resolve("Fail" + System.nanoTime() + ".java");
            Files.writeString(src, source);
            CompilerOptions options = new CompilerOptions();
            options.setSourceFile(src);
            return new JNativeCompiler().compile(options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String run(String source, String name, Map<String, String> env, int opt) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setOptLevel(opt);
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());

        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        pb.environment().putAll(new HashMap<>(env));
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "timeout");
        String output = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
