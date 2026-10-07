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

/**
 * Round-2 GC native validation: stress + graphs/cycles/strings/collections + heap verify.
 */
class GcNativeValidationEndToEndTest {

    @TempDir
    Path tempDir;

    private static final String SOURCE = """
            class Node {
                int value;
                Node next;
                Node(int value) { this.value = value; }
            }
            public class Main {
                @Native(symbol = "jn_gc_user_verify_heap")
                static native int verifyHeap();

                public static void main(String[] args) {
                    Node a = new Node(1);
                    Node b = new Node(2);
                    Node c = new Node(3);
                    a.next = b;
                    b.next = c;
                    c.next = a;

                    ArrayList<String> names = new ArrayList<String>();
                    HashMap<String, int> scores = new HashMap<String, int>();
                    int i = 0;
                    while (i < 500) {
                        String s = "k" + String.valueOf(i);
                        names.add(s);
                        scores.put(s, i);
                        Node tmp = new Node(i);
                        i = i + 1;
                    }
                    System.gc();
                    int v = verifyHeap();
                    if (v != 1) {
                        System.out.println("VERIFY_FAIL");
                        return;
                    }
                    if (a.next.next.value != 3) {
                        System.out.println("GRAPH_FAIL");
                        return;
                    }
                    if (c.next.value != 1) {
                        System.out.println("CYCLE_FAIL");
                        return;
                    }
                    if (names.size() != 500) {
                        System.out.println("LIST_FAIL");
                        return;
                    }
                    if (scores.get("k1") != 1) {
                        System.out.println("MAP_FAIL");
                        return;
                    }
                    String live = names.get(0);
                    System.out.println(live);
                    System.out.println("JNATIVE_GC_VALIDATION_PASS");
                }
            }
            """;

    @Test
    void validationUnderStressO0() throws Exception {
        assertPass(0, Map.of(
                "JNATIVE_GC_STRESS", "1",
                "JNATIVE_GC", "generational"
        ));
    }

    @Test
    void validationUnderStressO2() throws Exception {
        assertPass(2, Map.of(
                "JNATIVE_GC_STRESS", "1",
                "JNATIVE_GC", "generational"
        ));
    }

    @Test
    void validationMinorSoftStress() throws Exception {
        /* "minor" collects on every alloc — keep workload modest; O2 only. */
        assertPass(2, Map.of(
                "JNATIVE_GC_STRESS", "1",
                "JNATIVE_GC", "generational",
                "JNATIVE_YOUNG_SIZE", "262144",
                "JNATIVE_GC_VERIFY", "1"
        ));
    }

    private void assertPass(int opt, Map<String, String> env) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String out = run(SOURCE, "GcVal" + opt, env, opt);
        assertTrue(out.contains("JNATIVE_GC_VALIDATION_PASS"), out);
        assertFalse(out.contains("VERIFY_FAIL"), out);
    }

    private String run(String source, String name, Map<String, String> env, int opt) throws Exception {
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
        Map<String, String> e = new HashMap<>(env);
        pb.environment().putAll(e);
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(180, TimeUnit.SECONDS), "timeout");
        String output = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
