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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class EndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void hello() throws Exception {
        assertNative("""
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("Hello JNative");
                    }
                }
                """, "Hello", "Hello JNative\n");
    }

    @Test
    void arithmetic() throws Exception {
        assertNative("""
                public class Arithmetic {
                    public static void main(String[] args) {
                        int a = 10;
                        int b = 20;
                        int c = a + b * 2;
                        System.out.println(c);
                    }
                }
                """, "Arithmetic", "50\n");
    }

    @Test
    void ifElse() throws Exception {
        assertNative("""
                public class IfElse {
                    public static void main(String[] args) {
                        int x = 20;
                        if (x > 10) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "IfElse", "1\n");
    }

    @Test
    void loop() throws Exception {
        assertNative("""
                public class Loop {
                    public static void main(String[] args) {
                        int i = 0;
                        while (i < 5) {
                            System.out.println(i);
                            i = i + 1;
                        }
                    }
                }
                """, "Loop", "0\n1\n2\n3\n4\n");
    }

    @Test
    void methods() throws Exception {
        assertNative("""
                public class Methods {
                    static int add(int a, int b) {
                        return a + b;
                    }
                    public static void main(String[] args) {
                        int result = add(10, 20);
                        System.out.println(result);
                    }
                }
                """, "Methods", "30\n");
    }

    @Test
    void basicObject() throws Exception {
        assertNative("""
                class Person {
                    int age;
                }
                public class Main {
                    public static void main(String[] args) {
                        Person p = new Person();
                        p.age = 45;
                        System.out.println(p.age);
                    }
                }
                """, "BasicObject", "45\n");
    }

    @Test
    void instanceMethod() throws Exception {
        assertNative("""
                class Person {
                    int age;
                    void birthday() {
                        age = age + 1;
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Person p = new Person();
                        p.age = 45;
                        p.birthday();
                        System.out.println(p.age);
                    }
                }
                """, "InstanceMethod", "46\n");
    }

    @Test
    void thisKeyword() throws Exception {
        assertNative("""
                class Counter {
                    int value;
                    void increment() {
                        this.value = this.value + 1;
                    }
                    int get() {
                        return this.value;
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Counter c = new Counter();
                        c.increment();
                        c.increment();
                        c.increment();
                        System.out.println(c.get());
                    }
                }
                """, "ThisKeyword", "3\n");
    }

    @Test
    void referenceIdentity() throws Exception {
        assertNative("""
                class Box {
                    int value;
                }
                public class Main {
                    public static void main(String[] args) {
                        Box a = new Box();
                        Box b = a;
                        b.value = 100;
                        System.out.println(a.value);
                        if (a == b) {
                            System.out.println(1);
                        }
                    }
                }
                """, "RefIdentity", "100\n1\n");
    }

    @Test
    void zeroInitialization() throws Exception {
        assertNative("""
                class Data {
                    int number;
                    boolean active;
                }
                public class Main {
                    public static void main(String[] args) {
                        Data d = new Data();
                        System.out.println(d.number);
                        System.out.println(d.active);
                    }
                }
                """, "ZeroInit", "0\nfalse\n");
    }

    @Test
    void nullCompare() throws Exception {
        assertNative("""
                class Node {
                    int value;
                }
                public class Main {
                    public static void main(String[] args) {
                        Node n = null;
                        if (n == null) {
                            System.out.println(1);
                        }
                    }
                }
                """, "NullCompare", "1\n");
    }

    @Test
    void sharedReference() throws Exception {
        assertNative("""
                class Person {
                    int age;
                }
                public class Main {
                    public static void main(String[] args) {
                        Person a = new Person();
                        Person b = a;
                        a.age = 10;
                        b.age = 99;
                        System.out.println(a.age);
                    }
                }
                """, "SharedRef", "99\n");
    }

    @Test
    void dynamicDispatch() throws Exception {
        assertNative("""
                class Animal {
                    void speak() {
                        System.out.println(1);
                    }
                }
                class Dog extends Animal {
                    void speak() {
                        System.out.println(2);
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Animal a = new Dog();
                        a.speak();
                    }
                }
                """, "DynamicDispatch", "2\n");
    }

    @Test
    void polymorphism() throws Exception {
        assertNative("""
                class Animal {
                    void speak() {
                        System.out.println(1);
                    }
                }
                class Dog extends Animal {
                    void speak() {
                        System.out.println(2);
                    }
                }
                class Cat extends Animal {
                    void speak() {
                        System.out.println(3);
                    }
                }
                public class Main {
                    static void makeSpeak(Animal animal) {
                        animal.speak();
                    }
                    public static void main(String[] args) {
                        Dog dog = new Dog();
                        Cat cat = new Cat();
                        makeSpeak(dog);
                        makeSpeak(cat);
                    }
                }
                """, "Polymorphism", "2\n3\n");
    }

    @Test
    void inheritedFields() throws Exception {
        assertNative("""
                class Animal {
                    int age;
                }
                class Dog extends Animal {
                    int weight;
                }
                public class Main {
                    public static void main(String[] args) {
                        Dog d = new Dog();
                        d.age = 5;
                        d.weight = 20;
                        System.out.println(d.age);
                        System.out.println(d.weight);
                    }
                }
                """, "InheritedFields", "5\n20\n");
    }

    @Test
    void inheritedMethod() throws Exception {
        assertNative("""
                class Animal {
                    void eat() {
                        System.out.println(10);
                    }
                }
                class Dog extends Animal {
                }
                public class Main {
                    public static void main(String[] args) {
                        Dog d = new Dog();
                        d.eat();
                    }
                }
                """, "InheritedMethod", "10\n");
    }

    @Test
    void multiLevelInheritance() throws Exception {
        assertNative("""
                class Animal {
                    void speak() {
                        System.out.println(1);
                    }
                }
                class Mammal extends Animal {
                }
                class Dog extends Mammal {
                    void speak() {
                        System.out.println(3);
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Animal a = new Dog();
                        a.speak();
                    }
                }
                """, "MultiLevel", "3\n");
    }

    @Test
    void parentReferenceMutation() throws Exception {
        assertNative("""
                class Animal {
                    int age;
                }
                class Dog extends Animal {
                }
                public class Main {
                    public static void main(String[] args) {
                        Dog d = new Dog();
                        Animal a = d;
                        a.age = 10;
                        System.out.println(d.age);
                    }
                }
                """, "ParentRefMutation", "10\n");
    }

    // --- JNative 0.4 Acceptance ---

    @Test
    void constructorWithArgs() throws Exception {
        assertNative("""
                class Person {
                    int age;
                    Person(int age) {
                        this.age = age;
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Person p = new Person(45);
                        System.out.println(p.age);
                    }
                }
                """, "CtorArgs", "45\n");
    }

    @Test
    void constructorInheritance() throws Exception {
        assertNative("""
                class Animal {
                    int age;
                    Animal(int age) {
                        this.age = age;
                    }
                }
                class Dog extends Animal {
                    int weight;
                    Dog(int age, int weight) {
                        super(age);
                        this.weight = weight;
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Dog d = new Dog(5, 20);
                        System.out.println(d.age);
                        System.out.println(d.weight);
                    }
                }
                """, "CtorInherit", "5\n20\n");
    }

    @Test
    void thisDelegation() throws Exception {
        assertNative("""
                class Person {
                    int age;
                    Person() {
                        this(10);
                    }
                    Person(int age) {
                        this.age = age;
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Person p = new Person();
                        System.out.println(p.age);
                    }
                }
                """, "ThisDelegation", "10\n");
    }

    @Test
    void primitiveArray() throws Exception {
        assertNative("""
                public class Main {
                    public static void main(String[] args) {
                        int[] a = new int[5];
                        for (int i = 0; i < a.length; i = i + 1) {
                            a[i] = i * 10;
                        }
                        System.out.println(a[3]);
                    }
                }
                """, "PrimArray", "30\n");
    }

    @Test
    void referenceArray() throws Exception {
        assertNative("""
                class Person {
                    int age;
                    Person(int age) {
                        this.age = age;
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Person[] people = new Person[2];
                        people[0] = new Person(10);
                        people[1] = new Person(20);
                        System.out.println(people[1].age);
                    }
                }
                """, "RefArray", "20\n");
    }

    @Test
    void stringConcatAndLength() throws Exception {
        assertNative("""
                public class Main {
                    public static void main(String[] args) {
                        String a = "Hello ";
                        String b = "JNative";
                        String c = a + b;
                        System.out.println(c);
                        System.out.println(c.length());
                    }
                }
                """, "StringConcat", "Hello JNative\n13\n");
    }

    @Test
    void stringCharAt() throws Exception {
        assertNative("""
                public class Main {
                    public static void main(String[] args) {
                        String s = "ABC";
                        System.out.println(s.charAt(1));
                    }
                }
                """, "StringCharAt", "B\n");
    }

    @Test
    void instanceOfCheck() throws Exception {
        assertNative("""
                class Animal {
                }
                class Dog extends Animal {
                }
                public class Main {
                    public static void main(String[] args) {
                        Animal a = new Dog();
                        if (a instanceof Dog) {
                            System.out.println(1);
                        }
                        if (a instanceof Animal) {
                            System.out.println(2);
                        }
                    }
                }
                """, "InstanceOf", "1\n2\n");
    }

    @Test
    void instanceOfNull() throws Exception {
        assertNative("""
                class Animal {
                }
                public class Main {
                    public static void main(String[] args) {
                        Animal a = null;
                        if (a instanceof Animal) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "InstanceOfNull", "0\n");
    }

    @Test
    void checkedCast() throws Exception {
        assertNative("""
                class Animal {
                }
                class Dog extends Animal {
                    int value;
                }
                public class Main {
                    public static void main(String[] args) {
                        Animal a = new Dog();
                        Dog d = (Dog) a;
                        d.value = 99;
                        System.out.println(d.value);
                    }
                }
                """, "CheckedCast", "99\n");
    }

    @Test
    void castNull() throws Exception {
        assertNative("""
                class Animal {
                }
                class Dog extends Animal {
                }
                public class Main {
                    public static void main(String[] args) {
                        Animal a = null;
                        Dog d = (Dog) a;
                        if (d == null) {
                            System.out.println(1);
                        }
                    }
                }
                """, "CastNull", "1\n");
    }

    @Test
    void examplesDirectory() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path examples = Path.of("examples").toAbsolutePath();
        Assumptions.assumeTrue(Files.isDirectory(examples), "examples/ not found");

        runExample(examples.resolve("Hello.java"), "Hello JNative\n");
        runExample(examples.resolve("Arithmetic.java"), "50\n");
        runExample(examples.resolve("IfElse.java"), "1\n");
        runExample(examples.resolve("Loop.java"), "0\n1\n2\n3\n4\n");
        runExample(examples.resolve("Methods.java"), "30\n");
    }

    private void runExample(Path source, String expected) throws Exception {
        String name = source.getFileName().toString().replace(".java", "");
        Path out = tempDir.resolve(name + "-ex");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(source);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertEquals(expected, execute(out));
    }

    private void assertNative(String source, String name, String expectedStdout) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");

        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);

        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);

        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertNotNull(result.executable());
        assertTrue(Files.exists(result.executable()));

        String stdout = execute(result.executable());
        assertEquals(expectedStdout, stdout);
    }

    private static String execute(Path executable) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(executable.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        boolean finished = process.waitFor(30, TimeUnit.SECONDS);
        assertTrue(finished, "process timed out");
        String output = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
