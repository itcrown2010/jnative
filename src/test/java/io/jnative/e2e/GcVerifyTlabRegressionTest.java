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
 * Regression: jn_gc2_verify_heap must fill TLAB waste before scanning eden
 * (otherwise false "corrupt young header size"). Fixed in Runtime Stability 0.8.
 * @author 张晓达
 */
class GcVerifyTlabRegressionTest {

    @TempDir
    Path tempDir;

    @Test
    void verifyAfterTlabBumpWithoutGc() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent());
        String src = """
                class Box { int v; Box(int v) { this.v = v; } }
                public class Main {
                    @Native(symbol = "jn_gc_user_verify_heap")
                    static native int verifyHeap();
                    public static void main(String[] args) {
                        Box keep = new Box(1);
                        int i = 0;
                        while (i < 3000) {
                            Box t = new Box(i);
                            i = i + 1;
                        }
                        /* No System.gc() — verify must tolerate live TLAB gaps. */
                        if (verifyHeap() != 1) {
                            System.out.println("VERIFY_FAIL");
                            return;
                        }
                        if (keep.v != 1) {
                            System.out.println("KEEP_FAIL");
                            return;
                        }
                        System.out.println("GC_VERIFY_TLAB_PASS");
                    }
                }
                """;
        Path javaFile = tempDir.resolve("GcVerifyTlab.java");
        Files.writeString(javaFile, src, StandardCharsets.UTF_8);
        Path outBin = tempDir.resolve("GcVerifyTlab");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(outBin);
        options.setOptLevel(0);
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().toString());
        ProcessBuilder pb = new ProcessBuilder(outBin.toAbsolutePath().toString());
        Map<String, String> env = new HashMap<>(pb.environment());
        env.put("JNATIVE_GC", "generational");
        env.put("JNATIVE_GC_VERIFY", "1");
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), out);
        assertTrue(out.contains("GC_VERIFY_TLAB_PASS"), out);
    }
}
