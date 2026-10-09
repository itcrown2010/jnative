package io.jnative.backend.wasm;

import io.jnative.backend.BackendRequest;
import io.jnative.backend.CompileArtifact;
import io.jnative.backend.TargetBackend;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrValue;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * wasm32-browser backend: JNative IR → WAT → .wasm + minimal JS glue.
 * Uses linear-memory managed heap (bump + mark foundation) and JsHandle imports.
 * @author 张晓达
 */
public final class WasmBrowserBackend implements TargetBackend {

    @Override
    public String triple() {
        return "wasm32-browser";
    }

    @Override
    public CompileArtifact compile(BackendRequest request) throws Exception {
        Path outDir = request.outputDir();
        Files.createDirectories(outDir);

        BrowserWatEmitter emitter = new BrowserWatEmitter();
        String wat = emitter.emit(request.ir());
        Path watFile = outDir.resolve("app.wat");
        Files.writeString(watFile, wat, StandardCharsets.UTF_8);

        Path wasmFile = outDir.resolve("app.wasm");
        runWat2Wasm(watFile, wasmFile);

        Path jsFile = outDir.resolve("app.js");
        Files.writeString(jsFile, JsGlueGenerator.generate(), StandardCharsets.UTF_8);

        Path htmlFile = outDir.resolve("index.html");
        if (!Files.exists(htmlFile)) {
            Files.writeString(htmlFile, defaultHtml(), StandardCharsets.UTF_8);
        }

        if (request.options().emitWat()) {
            // already wrote app.wat
        } else if (!request.options().keepTemps()) {
            Files.deleteIfExists(watFile);
        }

        return CompileArtifact.of(wasmFile, jsFile, htmlFile);
    }

    private static void runWat2Wasm(Path wat, Path wasm) throws Exception {
        Path wat2wasm = findWat2Wasm();
        ProcessBuilder pb = new ProcessBuilder(
                wat2wasm.toString(),
                wat.toAbsolutePath().toString(),
                "-o", wasm.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String output;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
            output = sb.toString();
        }
        if (!p.waitFor(60, TimeUnit.SECONDS) || p.exitValue() != 0) {
            throw new IllegalStateException("wat2wasm failed: " + output);
        }
    }

    private static Path findWat2Wasm() {
        String[] candidates = {
                // Prefer Cellar / non-PATH natives: npm's wabt ships a Node+wasm
                // wrapper as /opt/homebrew/bin/wat2wasm that OOMs on large WAT.
                "/opt/homebrew/opt/wabt/bin/wat2wasm",
                "/usr/local/opt/wabt/bin/wat2wasm",
                "/opt/homebrew/bin/wat2wasm",
                "/usr/local/bin/wat2wasm",
                "wat2wasm"
        };
        for (String c : candidates) {
            Path p = Path.of(c);
            if (!c.equals("wat2wasm") && !Files.isExecutable(p)) {
                continue;
            }
            if (!c.equals("wat2wasm") && isNodeWat2WasmShim(p)) {
                continue;
            }
            return p;
        }
        return Path.of("wat2wasm");
    }

    /** npm `wabt` package installs a Node script that crashes on large modules. */
    private static boolean isNodeWat2WasmShim(Path p) {
        try {
            byte[] bytes = Files.readAllBytes(p);
            int n = Math.min(240, bytes.length);
            String head = new String(bytes, 0, n, StandardCharsets.ISO_8859_1);
            return head.contains("node") || head.contains("/usr/bin/env") || head.contains("wabt/bin");
        } catch (Exception e) {
            return false;
        }
    }

    private static String defaultHtml() {
        return "<!doctype html>\n"
                + "<html lang=\"en\">\n"
                + "<head>\n"
                + "  <meta charset=\"utf-8\"/>\n"
                + "  <title>JNative WebUI</title>\n"
                + "</head>\n"
                + "<body>\n"
                + "  <div id=\"app\"></div>\n"
                + "  <script type=\"module\">\n"
                + "    import { jnativeStart } from './app.js';\n"
                + "    jnativeStart().catch((e) => console.error(e));\n"
                + "  </script>\n"
                + "</body>\n"
                + "</html>\n";
    }
}
