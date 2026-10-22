/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend.wasm;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Shared Node host for wasm32-browser modules — import table comes from
 * {@link WasmHostImportContract} so tests do not drift from WAT emission.
 *
 * @author 张晓达
 */
public final class WasmNodeHarness {

    private WasmNodeHarness() {
    }

    /** Instantiate {@code app.wasm}, call {@code jnative_start}, return console_log text. */
    public static String runMain(Path wasm, long timeoutSec) throws Exception {
        Path dir = wasm.getParent();
        Path js = dir.resolve("wasm-harness-" + System.nanoTime() + ".mjs");
        String script = """
                import { readFileSync } from 'fs';
                const wasmBytes = readFileSync(%s);
                let mem;
                let exports = null;
                const logs = [];
                function readUtf8(ptr, len) {
                  ptr = ptr >>> 0; len = len >>> 0;
                  if (!len) return '';
                  return new TextDecoder().decode(new Uint8Array(mem.buffer, ptr, len));
                }
                const imports = { jnative: %s };
                imports.jnative.console_log = (ptr, len) => { logs.push(readUtf8(ptr, len)); };
                imports.jnative.report_uncaught = (ptr, len) => {
                  throw new Error('uncaught: ' + readUtf8(ptr, len));
                };
                // Match JsGlueGenerator: async / Future completion depends on flush.
                imports.jnative.schedule_microtask = () => {
                  queueMicrotask(() => {
                    if (exports && typeof exports.jnative_flush === 'function') {
                      exports.jnative_flush();
                    }
                  });
                };
                const { instance } = await WebAssembly.instantiate(wasmBytes, imports);
                exports = instance.exports;
                mem = instance.exports.memory;
                if (typeof instance.exports.jnative_start === 'function') {
                  instance.exports.jnative_start();
                } else if (typeof instance.exports.jn_main === 'function') {
                  instance.exports.jn_main(0);
                } else {
                  throw new Error('no jnative_start/jn_main');
                }
                // Drain microtasks scheduled during start (async completion).
                await new Promise((r) => setImmediate(r));
                await new Promise((r) => setImmediate(r));
                process.stdout.write('__JN_OUT_BEGIN__\\n');
                process.stdout.write(logs.join('\\n'));
                process.stdout.write('\\n__JN_OUT_END__\\n');
                """.formatted(
                jsonString(wasm.toAbsolutePath().toString()),
                WasmHostImportContract.nodeJnativeImportsObjectLiteral());
        Files.writeString(js, script, StandardCharsets.UTF_8);
        ProcessBuilder pb = new ProcessBuilder("node", js.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new AssertionError("node harness timed out:\n" + out);
        }
        if (p.exitValue() != 0) {
            throw new AssertionError("node exit " + p.exitValue() + ":\n" + out);
        }
        int begin = out.indexOf("__JN_OUT_BEGIN__");
        int end = out.indexOf("__JN_OUT_END__");
        if (begin < 0 || end < 0 || end < begin) {
            throw new AssertionError("missing harness markers:\n" + out);
        }
        int contentStart = out.indexOf('\n', begin) + 1;
        return out.substring(contentStart, end).replaceAll("\\n$", "");
    }

    /** Instantiate {@code app.wasm} and call the first export whose name contains {@code exportFragment} with one i32 argument. */
    public static int callInt(Path wasm, String exportFragment, int arg, long timeoutSec) throws Exception {
        Path js = wasm.getParent().resolve("wasm-call-" + System.nanoTime() + ".mjs");
        String script = """
                import { readFileSync } from 'fs';
                const imports = { jnative: %s };
                const { instance } = await WebAssembly.instantiate(readFileSync(%s), imports);
                const e = instance.exports;
                const key = Object.keys(e).find((k) => k.includes(%s));
                if (!key) throw new Error('no export containing ' + %s);
                process.stdout.write('__JN_RESULT__' + (e[key](%d) | 0) + '\\n');
                """.formatted(
                WasmHostImportContract.nodeJnativeImportsObjectLiteral(),
                jsonString(wasm.toAbsolutePath().toString()),
                jsonString(exportFragment), jsonString(exportFragment), arg);
        Files.writeString(js, script, StandardCharsets.UTF_8);
        ProcessBuilder pb = new ProcessBuilder("node", js.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            p.destroyForcibly();
            throw new AssertionError("node timed out:\n" + out);
        }
        if (p.exitValue() != 0) {
            throw new AssertionError("node exit " + p.exitValue() + ":\n" + out);
        }
        int i = out.indexOf("__JN_RESULT__");
        if (i < 0) {
            throw new AssertionError("no result marker:\n" + out);
        }
        return Integer.parseInt(out.substring(i + "__JN_RESULT__".length()).trim());
    }

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
