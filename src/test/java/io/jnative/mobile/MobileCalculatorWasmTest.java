package io.jnative.mobile;

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
 * Rebuilds the mobile calculator from source and executes the WASM module in Node.
 * Does not read stale dist artifacts as the sole oracle.
 */
public class MobileCalculatorWasmTest {

    @TempDir
    Path tempDir;

    @Test
    void rebuildFromSourceCompute12Plus30() throws Exception {
        Path jar = Path.of("target/jnative.jar");
        Assumptions.assumeTrue(Files.isRegularFile(jar), "target/jnative.jar required");
        Assumptions.assumeTrue(isExecutable("wat2wasm"), "wat2wasm required");
        Assumptions.assumeTrue(isExecutable("node"), "node required");

        Path src = Path.of("examples/mobile/calculator/src/CalculatorApp.java");
        assertTrue(Files.isRegularFile(src));

        Path out = tempDir.resolve("dist");
        Files.createDirectories(out);
        run(new String[]{
                "java", "-jar", jar.toString(),
                "build", "--target", "wasm32-browser", "--keep-temps",
                src.toAbsolutePath().toString(),
                "-o", out.toAbsolutePath().toString()
        }, 120);

        Path wasm = out.resolve("app.wasm");
        assertTrue(Files.size(wasm) > 8);
        byte[] magic = Files.readAllBytes(wasm);
        assertEquals(0, magic[0]);
        assertEquals('a', (char) (magic[1] & 0xff));
        assertEquals('s', (char) (magic[2] & 0xff));
        assertEquals('m', (char) (magic[3] & 0xff));

        String script = """
                import { readFileSync } from 'fs';
                const wasmBytes = readFileSync(%s);
                const imports = { jnative: {
                  console_log() {}, document_get_element_by_id(){return 0}, element_set_text(){},
                  document_create_element(){return 0}, element_append_child(){}, create_text(){return 0},
                  query_selector(){return 0}, apply_dom_patches(){}, schedule_microtask(){},
                  delegate_event(){}, init_delegation(){}, register_callback(id){return id}, set_count_label(){},
                }};
                const { instance } = await WebAssembly.instantiate(wasmBytes, imports);
                const e = instance.exports;
                const mem = e.memory;
                function w(s){ const enc=new TextEncoder().encode(s); const p=e.jn_bump(enc.length+4)>>>0; new DataView(mem.buffer).setUint32(p,enc.length,true); new Uint8Array(mem.buffer,p+4,enc.length).set(enc); return p; }
                function find(sub){ for (const k of Object.keys(e)) if (k.includes(sub)) return e[k]; throw new Error('missing '+sub); }
                const compute = find('CalculatorApp_compute_CString_CString');
                const getErr = find('CalculatorApp_getLastError');
                const r = compute(w('12'), w('30'));
                if (r !== 42 || (getErr()|0) !== 0) throw new Error('got '+r+' err='+getErr());
                compute(w(''), w('1'));
                if ((getErr()|0) !== 1) throw new Error('empty err '+getErr());
                compute(w('nope'), w('1'));
                if ((getErr()|0) !== 2) throw new Error('invalid err '+getErr());
                console.log('OK');
                """.formatted(jsonString(wasm.toAbsolutePath().toString()));

        Path js = tempDir.resolve("check.mjs");
        Files.writeString(js, script, StandardCharsets.UTF_8);
        String outText = run(new String[]{"node", js.toAbsolutePath().toString()}, 60);
        assertTrue(outText.contains("OK"));
    }

    @Test
    void changingAddChangesWasmResult() throws Exception {
        Path jar = Path.of("target/jnative.jar");
        Assumptions.assumeTrue(Files.isRegularFile(jar), "target/jnative.jar required");
        Assumptions.assumeTrue(isExecutable("wat2wasm"), "wat2wasm required");
        Assumptions.assumeTrue(isExecutable("node"), "node required");

        Path original = Path.of("examples/mobile/calculator/src/CalculatorApp.java");
        String text = Files.readString(original, StandardCharsets.UTF_8);
        String mutated = text.replace("return a + b;", "return a + b + 1;");
        assertNotEquals(text, mutated, "mutation must change source");

        Path src = tempDir.resolve("CalculatorApp.java");
        Files.writeString(src, mutated, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("mut-dist");
        Files.createDirectories(out);
        run(new String[]{
                "java", "-jar", jar.toString(),
                "build", "--target", "wasm32-browser",
                src.toAbsolutePath().toString(),
                "-o", out.toAbsolutePath().toString()
        }, 120);

        String script = """
                import { readFileSync } from 'fs';
                const wasmBytes = readFileSync(%s);
                const imports = { jnative: {
                  console_log() {}, document_get_element_by_id(){return 0}, element_set_text(){},
                  document_create_element(){return 0}, element_append_child(){}, create_text(){return 0},
                  query_selector(){return 0}, apply_dom_patches(){}, schedule_microtask(){},
                  delegate_event(){}, init_delegation(){}, register_callback(id){return id}, set_count_label(){},
                }};
                const { instance } = await WebAssembly.instantiate(wasmBytes, imports);
                const e = instance.exports;
                const mem = e.memory;
                function w(s){ const enc=new TextEncoder().encode(s); const p=e.jn_bump(enc.length+4)>>>0; new DataView(mem.buffer).setUint32(p,enc.length,true); new Uint8Array(mem.buffer,p+4,enc.length).set(enc); return p; }
                function find(sub){ for (const k of Object.keys(e)) if (k.includes(sub)) return e[k]; throw new Error('missing '+sub); }
                const r = find('CalculatorApp_compute_CString_CString')(w('12'), w('30'));
                if (r !== 43) throw new Error('expected mutated 43, got '+r);
                console.log('OK');
                """.formatted(jsonString(out.resolve("app.wasm").toAbsolutePath().toString()));
        Path js = tempDir.resolve("mut.mjs");
        Files.writeString(js, script, StandardCharsets.UTF_8);
        assertTrue(run(new String[]{"node", js.toAbsolutePath().toString()}, 60).contains("OK"));
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static boolean isExecutable(String cmd) {
        try {
            Process p = new ProcessBuilder("which", cmd).start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String run(String[] cmd, long timeoutSec) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);
        Process p = pb.start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            fail("timeout: " + String.join(" ", cmd) + "\n" + sb);
        }
        if (p.exitValue() != 0) {
            fail("exit " + p.exitValue() + ": " + String.join(" ", cmd) + "\n" + sb);
        }
        return sb.toString();
    }
}
