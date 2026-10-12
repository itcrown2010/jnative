/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.wasm;

import io.jnative.backend.CompileTarget;
import io.jnative.backend.TargetCapability;
import io.jnative.ffi.Wasm32BrowserAbi;
import io.jnative.layout.LayoutConfig;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
public class WasmBrowserTargetTest {

    @Test void targetTripleParse() {
        assertTrue(CompileTarget.parse("wasm32-browser").isBrowser());
        assertFalse(CompileTarget.parse("native").isBrowser());
        assertEquals("wasm32-browser", CompileTarget.WASM32_BROWSER.triple());
    }

    @Test void wasmAbiPointerIs32Bit() {
        assertEquals(4, Wasm32BrowserAbi.INSTANCE.pointerSize());
        assertEquals(4, Wasm32BrowserAbi.INSTANCE.pointerAlign());
    }

    @Test void layoutConfigWasm32() {
        LayoutConfig.configureWasm32();
        assertEquals(4, LayoutConfig.pointerSize());
        LayoutConfig.configureNative64();
        assertEquals(8, LayoutConfig.pointerSize());
    }

    @Test void browserCapabilities() {
        assertTrue(CompileTarget.WASM32_BROWSER.capabilities().contains(TargetCapability.BROWSER_DOM));
        assertTrue(CompileTarget.WASM32_BROWSER.capabilities().contains(TargetCapability.JS_INTEROP));
        assertFalse(CompileTarget.WASM32_BROWSER.capabilities().contains(TargetCapability.RAW_SOCKET));
        assertFalse(CompileTarget.WASM32_BROWSER.capabilities().contains(TargetCapability.THREADS));
    }

    @Test void helloDomWasmExistsAndValidMagic() throws Exception {
        Path wasm = Path.of("examples/browser/hello-dom/dist/app.wasm");
        assertTrue(Files.exists(wasm), "build hello-dom first");
        byte[] bytes = Files.readAllBytes(wasm);
        assertTrue(bytes.length > 8);
        assertEquals(0x00, bytes[0] & 0xff);
        assertEquals('a', bytes[1] & 0xff);
        assertEquals('s', bytes[2] & 0xff);
        assertEquals('m', bytes[3] & 0xff);
    }

    @Test void jsGlueHasNoEval() throws Exception {
        Path js = Path.of("examples/browser/hello-dom/dist/app.js");
        assertTrue(Files.exists(js));
        String text = Files.readString(js);
        assertFalse(text.contains("eval("));
        assertFalse(text.contains("new Function"));
        assertTrue(text.contains("textContent"));
        assertTrue(text.contains("jnative_start"));
    }

    @Test void setTextUsesTextContentNotInnerHtml() throws Exception {
        String js = Files.readString(Path.of("examples/browser/hello-dom/dist/app.js"));
        assertTrue(js.contains("textContent"));
        assertFalse(js.contains("innerHTML"));
    }
}
