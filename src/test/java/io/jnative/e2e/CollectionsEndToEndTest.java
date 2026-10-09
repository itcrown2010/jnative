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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** JNative 0.16 Collections Framework e2e.
 * @author 张晓达
 */
class CollectionsEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void arrayListString() throws Exception {
        assertEquals("Bob\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        ArrayList<String> names = new ArrayList<String>();
                        names.add("Alice");
                        names.add("Bob");
                        System.out.println(names.get(1));
                    }
                }
                """, "AlStr", Map.of(), 0));
    }

    @Test
    void arrayListIntNoBoxing() throws Exception {
        assertEquals("42\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        ArrayList<int> numbers = new ArrayList<int>();
                        numbers.add(20);
                        numbers.add(22);
                        System.out.println(numbers.get(0) + numbers.get(1));
                    }
                }
                """, "AlInt", Map.of(), 0));
    }

    @Test
    void arrayListIntSpecializedLayout() {
        Path src = tempDir.resolve("AlLayout.java");
        try {
            Files.writeString(src, """
                    public class Main {
                        public static void main(String[] args) {
                            ArrayList<int> a = new ArrayList<int>();
                            a.add(1);
                            System.out.println(a.get(0));
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
        String llvm = result.llvmIr();
        assertTrue(llvm.contains("ArrayList$P_i32") || llvm.contains("ArrayList_P_i32"), llvm);
        assertTrue(llvm.contains("@meta.array.i32") || llvm.contains("array.i32"), llvm);
        // BoxingLib may define Integer.valueOf; specialized add must not call it.
        assertFalse(llvm.contains("call ptr @_JN_Integer_valueOf"),
                "ArrayList<int> must not call Integer.valueOf:\n" + llvm);
    }

    @Test
    void hashMapStringInt() throws Exception {
        assertEquals("42\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        HashMap<String, int> scores = new HashMap<String, int>();
                        scores.put("Alice", 20);
                        scores.put("Bob", 22);
                        System.out.println(scores.get("Alice") + scores.get("Bob"));
                    }
                }
                """, "HmStrInt", Map.of(), 0));
    }

    @Test
    void hashMapIntInt() throws Exception {
        assertEquals("42\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        HashMap<int, int> map = new HashMap<int, int>();
                        map.put(1, 20);
                        map.put(2, 22);
                        System.out.println(map.get(1) + map.get(2));
                    }
                }
                """, "HmIntInt", Map.of(), 0));
    }

    @Test
    void hashSetDedup() throws Exception {
        assertEquals("2\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        HashSet<String> set = new HashSet<String>();
                        set.add("A");
                        set.add("B");
                        set.add("A");
                        System.out.println(set.size());
                    }
                }
                """, "HsDedup", Map.of(), 0));
    }

    @Test
    void arrayDeque() throws Exception {
        assertEquals("1\n2\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        ArrayDeque<int> q = new ArrayDeque<int>();
                        q.addLast(1);
                        q.addLast(2);
                        System.out.println(q.removeFirst());
                        System.out.println(q.removeFirst());
                    }
                }
                """, "Deque", Map.of(), 0));
    }

    @Test
    void arrayDequeBothEnds() throws Exception {
        assertEquals("0\n3\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        ArrayDeque<int> q = new ArrayDeque<int>();
                        q.addLast(1);
                        q.addLast(2);
                        q.addFirst(0);
                        q.addLast(3);
                        System.out.println(q.removeFirst());
                        System.out.println(q.removeLast());
                    }
                }
                """, "Deque2", Map.of(), 0));
    }

    @Test
    void hashSetIterator() throws Exception {
        assertEquals("2\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        HashSet<String> set = new HashSet<String>();
                        set.add("A");
                        set.add("B");
                        set.add("A");
                        int n = 0;
                        Iterator<String> it = set.iterator();
                        while (it.hasNext()) {
                            String s = it.next();
                            n = n + 1;
                        }
                        System.out.println(n);
                    }
                }
                """, "HsIt", Map.of(), 0));
    }

    @Test
    void hashMapCollisionResize() throws Exception {
        assertEquals("100\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        HashMap<String, int> map = new HashMap<String, int>();
                        int i = 0;
                        while (i < 100) {
                            map.put("k" + String.valueOf(i), i);
                            i = i + 1;
                        }
                        System.out.println(map.size());
                    }
                }
                """, "HmResize", Map.of(), 0));
    }

    @Test
    void genericContainsAlgorithm() throws Exception {
        assertEquals("1\n", run("""
                public class Main {
                    static <T> boolean contains(Iterable<T> source, T value) {
                        Iterator<T> it = source.iterator();
                        while (it.hasNext()) {
                            T item = it.next();
                            if (item.equals(value)) {
                                return true;
                            }
                        }
                        return false;
                    }
                    public static void main(String[] args) {
                        ArrayList<String> list = new ArrayList<String>();
                        list.add("x");
                        list.add("y");
                        if (contains(list, "y")) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "GenContains", Map.of(), 0));
    }

    @Test
    void stableIdentityHashAcrossGc() throws Exception {
        assertEquals("1\n", run("""
                class Person {
                    int id;
                    Person(int id) { this.id = id; }
                }
                public class Main {
                    public static void main(String[] args) {
                        Person p = new Person(1);
                        int h1 = p.hashCode();
                        System.gc();
                        int h2 = p.hashCode();
                        if (h1 == h2) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "IdHash", Map.of(
                "JNATIVE_GC", "generational",
                "JNATIVE_GC_STRESS", "all"
        ), 0));
    }

    @Test
    void hashMapSurvivesMovingGc() throws Exception {
        assertEquals("42\n", run("""
                class Person {
                    int id;
                    Person(int id) { this.id = id; }
                }
                public class Main {
                    public static void main(String[] args) {
                        HashMap<Person, int> map = new HashMap<Person, int>();
                        Person key = new Person(7);
                        map.put(key, 42);
                        System.gc();
                        System.out.println(map.get(key));
                    }
                }
                """, "HmMove", Map.of(
                "JNATIVE_GC", "generational",
                "JNATIVE_GC_STRESS", "all",
                "JNATIVE_GC_VERIFY", "1"
        ), 0));
    }

    @Test
    void stringHashCodeEquals() throws Exception {
        assertEquals("1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String a = "hello";
                        String b = "hello";
                        if (a.equals(b) && a.hashCode() == b.hashCode()) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "StrHash", Map.of(), 0));
    }

    @Test
    void collectionsNativeE2E_O0() throws Exception {
        assertEquals("JNATIVE_COLLECTIONS_PASS\n", run(collectionsProgram(), "ColAll0", Map.of(), 0));
    }

    @Test
    void collectionsNativeE2E_O2() throws Exception {
        assertEquals("JNATIVE_COLLECTIONS_PASS\n", run(collectionsProgram(), "ColAll2", Map.of(), 2));
    }

    @Test
    void collectionsNativeE2E_O3() throws Exception {
        assertEquals("JNATIVE_COLLECTIONS_PASS\n", run(collectionsProgram(), "ColAll3", Map.of(), 3));
    }

    private static String collectionsProgram() {
        return """
                public class Main {
                    public static void main(String[] args) {
                        ArrayList<String> list = new ArrayList<String>();
                        list.add("a");
                        list.add("b");
                        HashMap<String, int> map = new HashMap<String, int>();
                        map.put("k", 7);
                        HashSet<String> set = new HashSet<String>();
                        set.add("x");
                        set.add("x");
                        ArrayDeque<String> dq = new ArrayDeque<String>();
                        dq.addLast("1");
                        dq.addFirst("0");
                        dq.offer("2");
                        int ok = 0;
                        if (list.size() == 2) {
                            if (map.get("k") == 7) {
                                if (set.size() == 1) {
                                    if (dq.removeFirst().equals("0")) {
                                        if (dq.removeFirst().equals("1")) {
                                            if (dq.removeLast().equals("2")) {
                                                ok = 1;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        if (ok == 1) {
                            System.out.println("JNATIVE_COLLECTIONS_PASS");
                        }
                    }
                }
                """;
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
