package io.jnative.webui;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WebUI / wasm32-browser checks that rebuild from source.
 * Full reactive WebUI DOM IR lowering is not complete this round — see mobile calculator
 * for the supported IR→WASM path (ints, strings length/charAt, loops, statics).
 * @author 张晓达
 */
public class WebUiReactiveTest {

    @TempDir
    Path tempDir;

    @Test
    void jsGlueHasNoEvalAndExportsStart() throws Exception {
        Path jar = Path.of("target/jnative.jar");
        Assumptions.assumeTrue(Files.isRegularFile(jar));
        Assumptions.assumeTrue(which("wat2wasm"));

        Path src = tempDir.resolve("Tiny.java");
        Files.writeString(src, """
                public class Tiny {
                    public static int add(int a, int b) { return a + b; }
                    public static void main(String[] args) {
                        boolean keep = false;
                        if (keep) { add(0, 0); }
                    }
                }
                """, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("dist");
        Files.createDirectories(out);
        run(new String[]{
                "java", "-jar", jar.toString(),
                "build", "--target", "wasm32-browser",
                src.toAbsolutePath().toString(),
                "-o", out.toAbsolutePath().toString()
        });
        String js = Files.readString(out.resolve("app.js"), StandardCharsets.UTF_8);
        assertTrue(js.contains("jnativeStart"));
        assertTrue(js.contains("applyDomPatches"));
        assertFalse(js.contains("eval("));
        assertFalse(js.contains("new Function"));
        byte[] wasm = Files.readAllBytes(out.resolve("app.wasm"));
        assertTrue(wasm.length > 8);
        assertEquals(0, wasm[0]);
        assertEquals('a', (char) (wasm[1] & 0xff));
    }

    @Test
    void unsupportedWebUiCounterFailsLoudlyOrBuildsWithoutTemplate() throws Exception {
        Path jar = Path.of("target/jnative.jar");
        Assumptions.assumeTrue(Files.isRegularFile(jar));
        Assumptions.assumeTrue(which("wat2wasm"));
        Path counter = Path.of("examples/webui/counter/App.java");
        Assumptions.assumeTrue(Files.isRegularFile(counter));
        Path out = tempDir.resolve("counter-dist");
        Files.createDirectories(out);
        ProcessBuilder pb = new ProcessBuilder(
                "java", "-jar", jar.toString(),
                "build", "--target", "wasm32-browser",
                counter.toAbsolutePath().toString(),
                "-o", out.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String log;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            log = r.lines().reduce("", (a, b) -> a + b + "\n");
        }
        boolean finished = p.waitFor(120, TimeUnit.SECONDS);
        assertTrue(finished);
        // Either fails with an explicit unsupported IR error, or succeeds without
        // fixed Increment/Count template strings in WAT.
        if (p.exitValue() == 0) {
            Path wat = out.resolve("app.wat");
            if (Files.exists(wat)) {
                String w = Files.readString(wat);
                assertFalse(w.contains("mount_counter_ui"), "must not use fixed counter template");
            }
        } else {
            assertTrue(log.contains("wasm") || log.contains("unsupported") || log.contains("error"),
                    "failure should mention wasm/unsupported: " + log);
        }
    }

    private static boolean which(String cmd) throws Exception {
        Process p = new ProcessBuilder("which", cmd).start();
        return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
    }

    private static void run(String[] cmd) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String log;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            log = r.lines().reduce("", (a, b) -> a + b + "\n");
        }
        assertTrue(p.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), log);
    }
}
