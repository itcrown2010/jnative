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

/** JNative 0.14 interface / abstract / functional-interface e2e. */
class InterfaceEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void basicInterfaceDispatch() throws Exception {
        assertEquals("Hello from JNative\n", run("""
                interface Greeter {
                    void greet();
                }
                class EnglishGreeter implements Greeter {
                    void greet() {
                        System.out.println("Hello from JNative");
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Greeter g = new EnglishGreeter();
                        g.greet();
                    }
                }
                """, "IfaceBasic", Map.of(), 0));
    }

    @Test
    void polymorphicInterfaceArray() throws Exception {
        assertEquals("woof\nmeow\n", run("""
                interface Animal {
                    void speak();
                }
                class Dog implements Animal {
                    void speak() { System.out.println("woof"); }
                }
                class Cat implements Animal {
                    void speak() { System.out.println("meow"); }
                }
                public class Main {
                    public static void main(String[] args) {
                        Animal[] animals = new Animal[2];
                        animals[0] = new Dog();
                        animals[1] = new Cat();
                        int i = 0;
                        while (i < animals.length) {
                            animals[i].speak();
                            i = i + 1;
                        }
                    }
                }
                """, "IfacePoly", Map.of(), 0));
    }

    @Test
    void functionalInterfaceLambda() throws Exception {
        assertEquals("42\n", run("""
                interface Doubler {
                    int apply(int x);
                }
                public class Main {
                    public static void main(String[] args) {
                        Doubler f = x -> x * 2;
                        System.out.println(f.apply(21));
                    }
                }
                """, "IfaceLambda", Map.of(), 0));
    }

    @Test
    void capturingLambdaWithGcStress() throws Exception {
        assertEquals("42\n", run("""
                interface Scale {
                    int apply(int x);
                }
                public class Main {
                    public static void main(String[] args) {
                        int base = 2;
                        Scale operation = x -> x * base;
                        System.out.println(operation.apply(21));
                    }
                }
                """, "IfaceCap", Map.of(
                "JNATIVE_GC", "generational",
                "JNATIVE_GC_STRESS", "all",
                "JNATIVE_GC_VERIFY", "1"
        ), 0));
    }

    @Test
    void finalAcceptanceOperations() throws Exception {
        assertEquals("42\n42\n", run("""
                interface Operation {
                    int apply(int a, int b);
                }
                class Add implements Operation {
                    public int apply(int a, int b) {
                        return a + b;
                    }
                }
                class Multiply implements Operation {
                    public int apply(int a, int b) {
                        return a * b;
                    }
                }
                public class Main {
                    static int execute(Operation op, int a, int b) {
                        return op.apply(a, b);
                    }
                    public static void main(String[] args) {
                        Operation add = new Add();
                        Operation multiply = new Multiply();
                        System.out.println(execute(add, 20, 22));
                        System.out.println(execute(multiply, 6, 7));
                    }
                }
                """, "IfaceFinal", Map.of(), 0));
    }

    @Test
    void abstractClass() throws Exception {
        assertEquals("woof\nzzz\n", run("""
                abstract class Animal {
                    abstract void speak();
                    void sleep() {
                        System.out.println("zzz");
                    }
                }
                class Dog extends Animal {
                    void speak() {
                        System.out.println("woof");
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Animal a = new Dog();
                        a.speak();
                        a.sleep();
                    }
                }
                """, "AbstractDog", Map.of(), 0));
    }

    @Test
    void instanceofAndCastInterface() throws Exception {
        assertEquals("1\n", run("""
                interface Named {
                    void run();
                }
                class Worker implements Named {
                    void run() {
                        System.out.println(1);
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Object o = new Worker();
                        if (o instanceof Named) {
                            Named r = (Named) o;
                            r.run();
                        }
                    }
                }
                """, "IfaceRtti", Map.of(), 0));
    }

    @Test
    void interfaceInheritance() throws Exception {
        assertEquals("a\nb\n", run("""
                interface A {
                    void a();
                }
                interface B extends A {
                    void b();
                }
                class C implements B {
                    void a() { System.out.println("a"); }
                    void b() { System.out.println("b"); }
                }
                public class Main {
                    public static void main(String[] args) {
                        B b = new C();
                        A a = b;
                        a.a();
                        b.b();
                    }
                }
                """, "IfaceExt", Map.of(), 0));
    }

    @Test
    void inheritedClassMethodSatisfiesInterface() throws Exception {
        assertEquals("run\n", run("""
                interface Job {
                    void run();
                }
                class Base {
                    public void run() {
                        System.out.println("run");
                    }
                }
                class Child extends Base implements Job {
                }
                public class Main {
                    public static void main(String[] args) {
                        Job r = new Child();
                        r.run();
                    }
                }
                """, "IfaceInherit", Map.of(), 0));
    }

    @Test
    void missingImplementationFails() {
        CompileResult result = compileFail("""
                interface Animal {
                    void speak();
                }
                class Dog implements Animal {
                }
                public class Main {
                    public static void main(String[] args) {}
                }
                """);
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.message().contains("does not implement") || d.message().contains("Animal.speak")));
    }

    @Test
    void abstractInstantiationFails() {
        CompileResult result = compileFail("""
                abstract class Animal {
                    abstract void speak();
                }
                public class Main {
                    public static void main(String[] args) {
                        Animal a = new Animal();
                    }
                }
                """);
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.message().contains("abstract") || d.message().contains("instantiate")));
    }

    @Test
    void nonFunctionalLambdaFails() {
        CompileResult result = compileFail("""
                interface Bad {
                    void a();
                    void b();
                }
                public class Main {
                    public static void main(String[] args) {
                        Bad x = () -> {};
                    }
                }
                """);
        assertFalse(result.success());
        assertTrue(result.diagnostics().stream().anyMatch(d ->
                d.message().toLowerCase().contains("functional")
                        || d.message().toLowerCase().contains("lambda")
                        || d.message().toLowerCase().contains("sam")));
    }

    @Test
    void irContainsInterfaceCall() {
        Path src = tempDir.resolve("IrCheck.java");
        try {
            Files.writeString(src, """
                    interface Greeter { void greet(); }
                    class G implements Greeter {
                        void greet() { System.out.println(1); }
                    }
                    public class Main {
                        public static void main(String[] args) {
                            Greeter g = new G();
                            g.greet();
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
        assertNotNull(result.ir());
        String ir = IrPrinter.print(result.ir());
        assertTrue(ir.contains("interfacecall"), ir);
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
