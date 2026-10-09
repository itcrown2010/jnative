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

/**
 * Integer/Long/Boolean boxing + ArrayList/HashMap reference collections vs javac.
 */
class BoxingCollectionsEndToEndTest {

    @TempDir
    Path tmp;

    @Test
    void overloadPrefersPrimitiveOverBoxed() {
        DiagnosticReporter r = analyze("""
                public class T {
                    static int f(int x) { return 1; }
                    static int f(Integer x) { return 2; }
                    static int g() { return f(3); }
                }
                """);
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void arrayListIntStillRejectedAsPrimitiveGenericOk() {
        // ArrayList<int> remains the specialization path (not an error).
        DiagnosticReporter r = analyze("""
                public class T {
                    static void g() {
                        ArrayList<int> a = new ArrayList<int>();
                        a.add(1);
                    }
                }
                """);
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void nativeMatchesJavaReference() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String src = """
                public class Main {
                    static int flag(boolean b) {
                        if (b) { return 1; }
                        return 0;
                    }

                    static int prefer(int x) { return 1; }
                    static int prefer(Integer x) { return 2; }

                    public static void main(String[] args) {
                        Integer a = 40;
                        Integer b = Integer.valueOf(2);
                        System.out.println(a + b.intValue());
                        System.out.println(prefer(3));
                        System.out.println(flag(Integer.valueOf(100) == Integer.valueOf(100)));
                        System.out.println(flag(Integer.valueOf(200) == Integer.valueOf(200)));
                        System.out.println(flag(Integer.valueOf(200).equals(Integer.valueOf(200))));

                        Boolean t = true;
                        System.out.println(flag(t == Boolean.valueOf(true)));
                        System.out.println(flag(Boolean.TRUE == Boolean.valueOf(false)));

                        Long L = 5L;
                        System.out.println(L.longValue() + 1L);

                        ArrayList<Integer> list = new ArrayList<Integer>();
                        list.add(1);
                        list.add(2);
                        list.add(3);
                        list.set(1, 20);
                        System.out.println(list.size());
                        System.out.println(list.get(1).intValue());
                        System.out.println(flag(list.contains(Integer.valueOf(20))));
                        list.removeAt(0);
                        System.out.println(list.size());
                        int sum = 0;
                        for (Integer v : list) {
                            sum = sum + v;
                        }
                        System.out.println(sum);
                        list.clear();
                        System.out.println(flag(list.isEmpty()));

                        HashMap<String, Integer> map = new HashMap<String, Integer>();
                        map.put("甲", 10);
                        map.put("乙", 20);
                        map.put("甲", 11);
                        System.out.println(map.size());
                        System.out.println(map.get("甲").intValue());
                        System.out.println(flag(map.containsKey("乙")));
                        System.out.println(flag(map.containsKey("丙")));
                        int entrySum = 0;
                        HashMapEntryIterator<String, Integer> it = map.entryIterator();
                        while (it.hasNext()) {
                            HashMapEntry<String, Integer> e = it.next();
                            entrySum = entrySum + e.getValue();
                        }
                        System.out.println(entrySum);

                        // equal keys, distinct instances
                        HashMap<Integer, Integer> im = new HashMap<Integer, Integer>();
                        im.put(Integer.valueOf(1000), 7);
                        System.out.println(im.get(Integer.valueOf(1000)).intValue());

                        // negative hashCode key
                        HashMap<NegKey, Integer> nm = new HashMap<NegKey, Integer>();
                        NegKey k1 = new NegKey(1);
                        NegKey k2 = new NegKey(1);
                        nm.put(k1, 9);
                        System.out.println(nm.get(k2).intValue());

                        // null unbox
                        Integer n = null;
                        int threw = 0;
                        try {
                            int x = n;
                            threw = x;
                        } catch (NullPointerException e) {
                            threw = 1;
                        }
                        System.out.println(threw);
                    }
                }

                class NegKey {
                    int id;
                    NegKey(int id) { this.id = id; }
                    public int hashCode() { return 0 - 2147483647; }
                    public boolean equals(Object o) {
                        if (o == null) { return false; }
                        if (!(o instanceof NegKey)) { return false; }
                        NegKey other = (NegKey) o;
                        return this.id == other.id;
                    }
                }
                """;
        // Java reference cannot use HashMapEntryIterator — build a Java-comparable subset + jnative-only checks separately.
        String javaSrc = """
                import java.util.*;
                public class Main {
                    static int flag(boolean b) {
                        if (b) { return 1; }
                        return 0;
                    }
                    static int prefer(int x) { return 1; }
                    static int prefer(Integer x) { return 2; }
                    public static void main(String[] args) {
                        Integer a = 40;
                        Integer b = Integer.valueOf(2);
                        System.out.println(a + b.intValue());
                        System.out.println(prefer(3));
                        System.out.println(flag(Integer.valueOf(100) == Integer.valueOf(100)));
                        System.out.println(flag(Integer.valueOf(200) == Integer.valueOf(200)));
                        System.out.println(flag(Integer.valueOf(200).equals(Integer.valueOf(200))));
                        Boolean t = true;
                        System.out.println(flag(t == Boolean.valueOf(true)));
                        System.out.println(flag(Boolean.TRUE == Boolean.valueOf(false)));
                        Long L = 5L;
                        System.out.println(L.longValue() + 1L);
                        ArrayList<Integer> list = new ArrayList<Integer>();
                        list.add(1);
                        list.add(2);
                        list.add(3);
                        list.set(1, 20);
                        System.out.println(list.size());
                        System.out.println(list.get(1).intValue());
                        System.out.println(flag(list.contains(Integer.valueOf(20))));
                        list.remove(0);
                        System.out.println(list.size());
                        int sum = 0;
                        for (Integer v : list) {
                            sum = sum + v;
                        }
                        System.out.println(sum);
                        list.clear();
                        System.out.println(flag(list.isEmpty()));
                        HashMap<String, Integer> map = new HashMap<String, Integer>();
                        map.put("甲", 10);
                        map.put("乙", 20);
                        map.put("甲", 11);
                        System.out.println(map.size());
                        System.out.println(map.get("甲").intValue());
                        System.out.println(flag(map.containsKey("乙")));
                        System.out.println(flag(map.containsKey("丙")));
                        int entrySum = 0;
                        for (Map.Entry<String, Integer> e : map.entrySet()) {
                            entrySum = entrySum + e.getValue();
                        }
                        System.out.println(entrySum);
                        HashMap<Integer, Integer> im = new HashMap<Integer, Integer>();
                        im.put(Integer.valueOf(1000), 7);
                        System.out.println(im.get(Integer.valueOf(1000)).intValue());
                        HashMap<NegKey, Integer> nm = new HashMap<NegKey, Integer>();
                        NegKey k1 = new NegKey(1);
                        NegKey k2 = new NegKey(1);
                        nm.put(k1, 9);
                        System.out.println(nm.get(k2).intValue());
                        Integer n = null;
                        int threw = 0;
                        try {
                            int x = n;
                            threw = x;
                        } catch (NullPointerException e) {
                            threw = 1;
                        }
                        System.out.println(threw);
                    }
                }
                class NegKey {
                    int id;
                    NegKey(int id) { this.id = id; }
                    public int hashCode() { return 0 - 2147483647; }
                    public boolean equals(Object o) {
                        if (o == null) { return false; }
                        if (!(o instanceof NegKey)) { return false; }
                        NegKey other = (NegKey) o;
                        return this.id == other.id;
                    }
                }
                """;
        String expected = javaReference(tmp, javaSrc);
        String o0 = compileAndRun(tmp.resolve("o0"), src, 0);
        String o1 = compileAndRun(tmp.resolve("o1"), src, 1);
        assertEquals(expected, o0, "O0 vs Java");
        assertEquals(expected, o1, "O1 vs Java");
    }

    private static String javaReference(Path dir, String src) throws Exception {
        Path javaFile = dir.resolve("Main.java");
        Files.writeString(javaFile, src, StandardCharsets.UTF_8);
        Process compile = new ProcessBuilder("javac", javaFile.getFileName().toString())
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        assertTrue(compile.waitFor(60, TimeUnit.SECONDS));
        assertEquals(0, compile.exitValue(), new String(compile.getInputStream().readAllBytes()));
        Process run = new ProcessBuilder("java", "-cp", ".", "Main")
                .directory(dir.toFile())
                .redirectErrorStream(true)
                .start();
        assertTrue(run.waitFor(60, TimeUnit.SECONDS));
        String out = new String(run.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, run.exitValue(), out);
        return out;
    }

    private static String compileAndRun(Path out, String src, int opt) throws Exception {
        Path file = out.getParent().resolve("BoxMain-" + out.getFileName() + ".jn");
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
        try {
            MultiUnitFrontend.analyzeProgram(
                    MultiUnitFrontend.withStdlib(ProgramSources.of(new SourceUnit("T.jn", src))),
                    reporter);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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
