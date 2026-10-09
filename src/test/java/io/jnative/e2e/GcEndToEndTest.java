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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Native GC acceptance tests. Many run under JNATIVE_GC_STRESS=1.
 * @author 张晓达
 */
class GcEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void deadObjectsAreCollected() throws Exception {
        NativeResult r = run("""
                class Person {
                    int value;
                    Person(int value) { this.value = value; }
                }
                public class Main {
                    public static void main(String[] args) {
                        for (int i = 0; i < 100000; i = i + 1) {
                            Person p = new Person(i);
                        }
                        System.out.println(1);
                    }
                }
                """, "GcDead", Map.of(
                "JNATIVE_GC_THRESHOLD", "4096",
                "JNATIVE_GC_STATS", "1"
        ), 0); // -O0: heap pressure test must not scalar-replace allocations away
        assertEquals("1\n", stripStats(r.stdout));
        assertTrue(r.collections > 0, "expected collections > 0, got " + r.collections);
        assertTrue(r.freed > 0, "expected freed > 0, got " + r.freed);
    }

    @Test
    void liveRootSurvives() throws Exception {
        NativeResult r = run("""
                class Person {
                    int value;
                }
                public class Main {
                    public static void main(String[] args) {
                        Person p = new Person();
                        p.value = 123;
                        for (int i = 0; i < 100000; i = i + 1) {
                            Person temp = new Person();
                        }
                        System.gc();
                        System.out.println(p.value);
                    }
                }
                """, "GcLiveRoot", Map.of("JNATIVE_GC_STRESS", "1"));
        assertEquals("123\n", r.stdout);
    }

    @Test
    void objectGraph() throws Exception {
        NativeResult r = run("""
                class Node {
                    int value;
                    Node next;
                }
                public class Main {
                    public static void main(String[] args) {
                        Node a = new Node();
                        Node b = new Node();
                        Node c = new Node();
                        a.value = 1;
                        b.value = 2;
                        c.value = 3;
                        a.next = b;
                        b.next = c;
                        System.gc();
                        System.out.println(a.next.next.value);
                    }
                }
                """, "GcGraph", Map.of("JNATIVE_GC_STRESS", "1"));
        assertEquals("3\n", r.stdout);
    }

    @Test
    void cyclicGraphSurvives() throws Exception {
        NativeResult r = run("""
                class Node {
                    Node next;
                }
                public class Main {
                    public static void main(String[] args) {
                        Node a = new Node();
                        Node b = new Node();
                        a.next = b;
                        b.next = a;
                        System.gc();
                        System.out.println(1);
                    }
                }
                """, "GcCycleLive", Map.of("JNATIVE_GC_STRESS", "1"));
        assertEquals("1\n", r.stdout);
    }

    @Test
    void unreachableCycleCollected() throws Exception {
        NativeResult r = run("""
                class Node {
                    Node next;
                }
                public class Main {
                    static void createCycle() {
                        Node a = new Node();
                        Node b = new Node();
                        a.next = b;
                        b.next = a;
                    }
                    public static void main(String[] args) {
                        createCycle();
                        System.gc();
                        System.out.println(1);
                    }
                }
                """, "GcCycleDead", Map.of(
                "JNATIVE_GC_STATS", "1"
        ), 0); // -O0: assert heap free of unreachable cycle
        assertEquals("1\n", stripStats(r.stdout));
        assertTrue(r.collections > 0);
        assertTrue(r.freed >= 2, "cycle should be freed, freed=" + r.freed);
    }

    @Test
    void referenceArray() throws Exception {
        NativeResult r = run("""
                class Person {
                    int value;
                }
                public class Main {
                    public static void main(String[] args) {
                        Person[] people = new Person[3];
                        people[0] = new Person();
                        people[1] = new Person();
                        people[2] = new Person();
                        people[1].value = 88;
                        System.gc();
                        System.out.println(people[1].value);
                    }
                }
                """, "GcRefArray", Map.of("JNATIVE_GC_STRESS", "1"));
        assertEquals("88\n", r.stdout);
    }

    @Test
    void inheritanceReferences() throws Exception {
        NativeResult r = run("""
                class Box {
                    int tag;
                }
                class Animal {
                    Box ref;
                }
                class Dog extends Animal {
                    Box another;
                }
                public class Main {
                    public static void main(String[] args) {
                        Dog d = new Dog();
                        d.ref = new Box();
                        d.another = new Box();
                        System.gc();
                        if (d.ref != null) {
                            System.out.println(1);
                        }
                        if (d.another != null) {
                            System.out.println(2);
                        }
                    }
                }
                """, "GcInherit", Map.of("JNATIVE_GC_STRESS", "1"));
        assertEquals("1\n2\n", r.stdout);
    }

    @Test
    void stringSurvives() throws Exception {
        NativeResult r = run("""
                public class Main {
                    public static void main(String[] args) {
                        String important = "Hello " + "JNative";
                        for (int i = 0; i < 100000; i = i + 1) {
                            String temp = "A" + "B";
                        }
                        System.gc();
                        System.out.println(important);
                    }
                }
                """, "GcString", Map.of("JNATIVE_GC_STRESS", "1"));
        assertEquals("Hello JNative\n", r.stdout);
    }

    @Test
    void parameterRoot() throws Exception {
        NativeResult r = run("""
                class Person {
                    int value;
                }
                public class Main {
                    static void stress(Person p) {
                        for (int i = 0; i < 100000; i = i + 1) {
                            Person temp = new Person();
                        }
                        System.gc();
                        System.out.println(p.value);
                    }
                    public static void main(String[] args) {
                        Person p = new Person();
                        p.value = 77;
                        stress(p);
                    }
                }
                """, "GcParam", Map.of("JNATIVE_GC_STRESS", "1"));
        assertEquals("77\n", r.stdout);
    }

    @Test
    void thisRoot() throws Exception {
        NativeResult r = run("""
                class Holder {
                    int value;
                    void stress() {
                        for (int i = 0; i < 100000; i = i + 1) {
                            Holder temp = new Holder();
                        }
                        System.gc();
                        System.out.println(this.value);
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Holder h = new Holder();
                        h.value = 55;
                        h.stress();
                    }
                }
                """, "GcThis", Map.of("JNATIVE_GC_STRESS", "1"));
        assertEquals("55\n", r.stdout);
    }

    @Test
    void stressMillionTemps() throws Exception {
        NativeResult r = run("""
                class Person {
                    int value;
                    Person(int value) { this.value = value; }
                }
                public class Main {
                    public static void main(String[] args) {
                        for (int i = 0; i < 1000000; i = i + 1) {
                            Person p = new Person(i);
                        }
                        System.out.println(1);
                    }
                }
                """, "GcMillion", Map.of(
                "JNATIVE_GC_STRESS", "1",
                "JNATIVE_GC_STATS", "1"
        ), 0); // -O0: stress heap allocator / collector
        assertEquals("1\n", stripStats(r.stdout));
        assertTrue(r.collections > 0);
        assertTrue(r.freed > 0);
        assertTrue(r.allocated > r.live, "allocated=" + r.allocated + " live=" + r.live);
    }

    private record NativeResult(String stdout, long collections, long allocated, long freed, long live, long peak) {
    }

    private NativeResult run(String source, String name, Map<String, String> env) throws Exception {
        return run(source, name, env, 2);
    }

    private NativeResult run(String source, String name, Map<String, String> env, int optLevel) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setOptLevel(optLevel);
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());

        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        // Legacy GC e2e suite pins mark-sweep unless a test selects GC2 explicitly.
        if (!env.containsKey("JNATIVE_GC")) {
            pb.environment().put("JNATIVE_GC", "marksweep");
        }
        pb.environment().putAll(env);
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "timeout");
        String output = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);

        long collections = matchLong(output, "collections: (\\d+)");
        long allocated = matchLong(output, "allocated: (\\d+)");
        long freed = matchLong(output, "freed: (\\d+)");
        long live = matchLong(output, "live: (\\d+)");
        long peak = matchLong(output, "peak: (\\d+)");
        return new NativeResult(output, collections, allocated, freed, live, peak);
    }

    private static String stripStats(String stdout) {
        StringBuilder sb = new StringBuilder();
        for (String line : stdout.split("\n", -1)) {
            if (line.startsWith("JNative GC:")
                    || line.startsWith("collections:")
                    || line.startsWith("allocated:")
                    || line.startsWith("freed:")
                    || line.startsWith("live:")
                    || line.startsWith("peak:")
                    || line.startsWith("minor:")
                    || line.startsWith("major:")
                    || line.startsWith("tlab_")
                    || line.startsWith("bytes_")) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(line);
        }
        String s = sb.toString().replaceAll("\n+$", "");
        return s.isEmpty() ? "" : s + "\n";
    }

    private static long matchLong(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        return -1;
    }
}
