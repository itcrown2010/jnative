package io.jnative.wasm;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rebuilds float-calc from source and runs the Node WASM verify script.
 */
public class WasmBrowserFloatTest {

    private static boolean toolsPresent() {
        return Files.isRegularFile(Path.of("target/jnative.jar"))
                && isOnPath("node")
                && isOnPath("wat2wasm");
    }

    private static boolean isOnPath(String cmd) {
        try {
            Process p = new ProcessBuilder("which", cmd).start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Test
    @EnabledIf("toolsPresent")
    void floatCalcVerifyFromSource() throws Exception {
        Path script = Path.of("examples/browser/float-calc/scripts/verify.sh");
        assertTrue(Files.isRegularFile(script), "missing " + script);
        ProcessBuilder pb = new ProcessBuilder("bash", script.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        pb.directory(Path.of(".").toAbsolutePath().toFile());
        Process p = pb.start();
        StringBuilder out = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                out.append(line).append('\n');
            }
        }
        boolean finished = p.waitFor(120, TimeUnit.SECONDS);
        assertTrue(finished, "verify timed out:\n" + out);
        assertEquals(0, p.exitValue(), "verify failed:\n" + out);
        assertTrue(out.toString().contains("PASS float/double"), out.toString());
    }
}
