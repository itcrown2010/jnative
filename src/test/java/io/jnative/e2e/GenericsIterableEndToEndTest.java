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

/** Generics type-safety + Iterable enhanced-for.
 * @author 张晓达
 */
class GenericsIterableEndToEndTest {

    @Test
    void boxWrongSetArgumentRejected() {
        DiagnosticReporter r = analyze("""
                class Box<T> {
                    T value;
                    void set(T value) { this.value = value; }
                }
                public class T {
                    public static void f() {
                        Box<String> b = new Box<String>();
                        b.set(1);
                    }
                }
                """);
        assertTrue(r.hasErrors(), messages(r));
    }

    @Test
    void boxInvariantRejected() {
        DiagnosticReporter r = analyze("""
                class Parent {}
                class Child extends Parent {}
                class Box<T> {
                    T value;
                    Box(T value) { this.value = value; }
                }
                public class T {
                    public static void f() {
                        Box<Child> c = new Box<Child>(new Child());
                        Box<Parent> p = c;
                    }
                }
                """);
        assertTrue(r.hasErrors(), messages(r));
        assertTrue(messages(r).toLowerCase().contains("incompatible"), messages(r));
    }

    @Test
    void concreteIntoTypeVariableRejected() {
        DiagnosticReporter r = analyze("""
                class Box<T> {
                    T value;
                    void bad(String s) { this.value = s; }
                }
                public class T {
                    public static void f() {
                        Box<String> b = new Box<String>();
                    }
                }
                """);
        assertTrue(r.hasErrors(), messages(r));
    }

    @Test
    void boundRejected() {
        DiagnosticReporter r = analyze("""
                class Base {}
                class Other {}
                class Holder<T extends Base> {
                    T value;
                    Holder(T value) { this.value = value; }
                }
                public class T {
                    public static void f() {
                        Holder<Other> h = new Holder<Other>(new Other());
                    }
                }
                """);
        assertTrue(r.hasErrors(), messages(r));
        assertTrue(messages(r).toLowerCase().contains("bound"), messages(r));
    }

    @Test
    void boundMemberCallOk() {
        DiagnosticReporter r = analyze("""
                class Base {
                    public int id() { return 7; }
                }
                class Holder<T extends Base> {
                    T value;
                    Holder(T value) { this.value = value; }
                    public int getId() { return value.id(); }
                }
                public class T {
                    public static int f() {
                        Holder<Base> h = new Holder<Base>(new Base());
                        return h.getId();
                    }
                }
                """);
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void rawTypeStillRejected() {
        DiagnosticReporter r = analyze("""
                class Box<T> { T value; }
                public class T {
                    public static void f() {
                        Box b = null;
                    }
                }
                """);
        assertTrue(r.hasErrors(), messages(r));
        assertTrue(messages(r).toLowerCase().contains("raw"), messages(r));
    }

    @Test
    void nonIterableForeachRejected() {
        DiagnosticReporter r = analyze("""
                public class T {
                    public static int f(Object o) {
                        int s = 0;
                        for (Object x : o) {
                            s = s + 1;
                        }
                        return s;
                    }
                }
                """);
        assertTrue(r.hasErrors(), messages(r));
        assertTrue(messages(r).toLowerCase().contains("iterable")
                        || messages(r).toLowerCase().contains("array"),
                messages(r));
    }

    @Test
    void multiFileGenericBox(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path lib = dir.resolve("lib");
        Files.createDirectories(lib);
        Files.writeString(lib.resolve("Box.jn"), """
                public class Box<T> {
                    T value;
                    public Box(T value) { this.value = value; }
                    public void set(T value) { this.value = value; }
                    public T get() { return value; }
                }
                """);
        String main = """
                public class Main {
                    public static void main(String[] args) {
                        Box<String> b = new Box<String>("ok");
                        b.set("yes");
                        System.out.println(b.get());
                    }
                }
                """;
        Path mainFile = dir.resolve("Main.jn");
        Files.writeString(mainFile, main);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(mainFile);
        options.setProgramSources(ProgramSources.of(
                SourceUnit.ofPath(lib.resolve("Box.jn"), Files.readString(lib.resolve("Box.jn"))),
                SourceUnit.ofPath(mainFile, main)));
        options.setOutputFile(dir.resolve("out"));
        options.setOptLevel(0);
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().toString());
        String out = runExe(cr.executable());
        assertEquals("yes\n", out);
    }

    @Test
    void nativeIterableForeachAndGenerics(@TempDir Path dir) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String src = """
                class Base {
                    public int n;
                    public Base(int n) { this.n = n; }
                    public int id() { return this.n; }
                }

                class Holder<T extends Base> {
                    T value;
                    public Holder(T value) { this.value = value; }
                    public int getId() { return value.id(); }
                }

                public class Main {
                    public static int side;

                    public static ArrayList<String> listOnce() {
                        side = side + 1;
                        ArrayList<String> a = new ArrayList<String>();
                        a.add("a");
                        a.add("b");
                        a.add("c");
                        return a;
                    }

                    public static void main(String[] args) {
                        Box<String> box = new Box<String>("x");
                        box.set("hi");
                        System.out.println(box.get());

                        Holder<Base> h = new Holder<Base>(new Base(9));
                        System.out.println(h.getId());

                        static <T> T identity(T v) { return v; }
                        // generic method at top-level not nested — use Main.identity below
                    }
                }

                class Box<T> {
                    T value;
                    Box(T value) { this.value = value; }
                    void set(T value) { this.value = value; }
                    T get() { return value; }
                }
                """;
        // Fix: can't nest generic method in main like that. Rewrite cleanly.
        src = """
                class Base {
                    public int n;
                    public Base(int n) { this.n = n; }
                    public int id() { return this.n; }
                }

                class Holder<T extends Base> {
                    T value;
                    public Holder(T value) { this.value = value; }
                    public int getId() { return value.id(); }
                }

                class Box<T> {
                    T value;
                    Box(T value) { this.value = value; }
                    void set(T value) { this.value = value; }
                    T get() { return value; }
                }

                public class Main {
                    public static int side;

                    static <T> T identity(T v) {
                        return v;
                    }

                    public static ArrayList<String> listOnce() {
                        side = side + 1;
                        ArrayList<String> a = new ArrayList<String>();
                        a.add("a");
                        a.add("b");
                        a.add("c");
                        return a;
                    }

                    public static void main(String[] args) {
                        Box<String> box = new Box<String>("x");
                        box.set("hi");
                        System.out.println(box.get());

                        Holder<Base> h = new Holder<Base>(new Base(9));
                        System.out.println(h.getId());

                        System.out.println(identity("id"));

                        side = 0;
                        int sum = 0;
                        for (String s : listOnce()) {
                            if (s.equals("b")) {
                                continue;
                            }
                            if (s.equals("c")) {
                                break;
                            }
                            sum = sum + s.length();
                        }
                        System.out.println(side);
                        System.out.println(sum);

                        ArrayList<String> empty = new ArrayList<String>();
                        int emptyCount = 0;
                        for (String s : empty) {
                            emptyCount = emptyCount + 1;
                        }
                        System.out.println(emptyCount);

                        ArrayList<Base> items = new ArrayList<Base>();
                        items.add(new Base(3));
                        items.add(new Base(4));
                        int total = 0;
                        for (Base item : items) {
                            total = total + item.n;
                        }
                        System.gc();
                        System.out.println(total);
                        System.out.println(items.get(0).n);
                    }
                }
                """;
        String o0 = compileAndRun(dir.resolve("o0"), src, 0);
        String o1 = compileAndRun(dir.resolve("o1"), src, 1);
        assertEquals(o0, o1);
        String[] lines = o0.trim().split("\\R");
        assertEquals("hi", lines[0]);
        assertEquals("9", lines[1]);
        assertEquals("id", lines[2]);
        assertEquals("1", lines[3]); // listOnce side effect once
        assertEquals("1", lines[4]); // "a".length, skip b, break c
        assertEquals("0", lines[5]); // empty
        assertEquals("7", lines[6]); // 3+4
        assertEquals("3", lines[7]); // GC keep-alive
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
        return runExe(cr.executable());
    }

    private static String runExe(Path exe) throws Exception {
        Process p = new ProcessBuilder(exe.toAbsolutePath().toString())
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
