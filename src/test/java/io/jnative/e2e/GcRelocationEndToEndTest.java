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
 * RH-1: young-gen relocation under default GC2.
 * Address may change after minor GC; rooted reference must still see correct fields.
 * @author 张晓达
 */
class GcRelocationEndToEndTest {

    @TempDir
    Path tempDir;

    private static final String SOURCE = """
            class Node {
                int value;
                Node next;
                Node(int value) { this.value = value; }
            }
            public class Main {
                // FFI cannot take managed Object (RH-1): prove relocation safety via
                // forced minor GC + rooted field integrity + stable identity hash.
                @Native(symbol = "jn_gc2_minor")
                static native void minorGc();

                @Native(symbol = "jn_gc_user_verify_heap")
                static native int verifyHeap();

                public static void main(String[] args) {
                    Node live = new Node(42);
                    live.next = new Node(7);
                    int h0 = live.hashCode();
                    int i = 0;
                    while (i < 2000) {
                        Node junk = new Node(i);
                        junk.next = live;
                        i = i + 1;
                    }
                    minorGc();
                    minorGc();
                    if (live.value != 42) {
                        System.out.println("VALUE_FAIL");
                        return;
                    }
                    if (live.next.value != 7) {
                        System.out.println("GRAPH_FAIL");
                        return;
                    }
                    int h1 = live.hashCode();
                    if (h0 != h1) {
                        System.out.println("IDENTITY_FAIL");
                        return;
                    }
                    int v = verifyHeap();
                    if (v != 1) {
                        System.out.println("VERIFY_FAIL");
                        return;
                    }
                    System.out.println(h0);
                    System.out.println("JNATIVE_GC_RELOCATION_PASS");
                }
            }
            """;

    @Test
    void youngObjectSurvivesRelocationO0() throws Exception {
        String out = run(0);
        assertTrue(out.contains("JNATIVE_GC_RELOCATION_PASS"), out);
        assertFalse(out.contains("VALUE_FAIL"), out);
        assertFalse(out.contains("GRAPH_FAIL"), out);
        assertFalse(out.contains("IDENTITY_FAIL"), out);
        assertFalse(out.contains("VERIFY_FAIL"), out);
    }

    @Test
    void youngObjectSurvivesRelocationO2() throws Exception {
        String out = run(2);
        assertTrue(out.contains("JNATIVE_GC_RELOCATION_PASS"), out);
        assertFalse(out.contains("VALUE_FAIL"), out);
        assertFalse(out.contains("IDENTITY_FAIL"), out);
    }

    private String run(int opt) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve("GcReloc" + opt + ".java");
        Files.writeString(javaFile, SOURCE, StandardCharsets.UTF_8);
        Path outBin = tempDir.resolve("GcReloc" + opt);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(outBin);
        options.setOptLevel(opt);
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().toString());
        ProcessBuilder pb = new ProcessBuilder(outBin.toAbsolutePath().toString());
        Map<String, String> env = new HashMap<>(pb.environment());
        env.put("JNATIVE_GC", "generational");
        env.put("JNATIVE_YOUNG_SIZE", "262144");
        env.put("JNATIVE_GC_VERIFY", "1");
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), stdout);
        return stdout;
    }
}
