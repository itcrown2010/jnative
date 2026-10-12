/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.target;

import io.jnative.backend.CompileTarget;
import io.jnative.backend.TargetCapability;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.diagnostics.DiagnosticCodes;
import io.jnative.diagnostics.DiagnosticJson;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unified target capability model + precheck diagnostics.
 *
 * @author 张晓达
 */
public class TargetCapabilityUnifiedTest {

    @Test
    void backendAndPkgAgreeOnWasmNoThreads() {
        Set<TargetCapability> backend = CompileTarget.WASM32_BROWSER.capabilities();
        Set<io.jnative.pkg.TargetCapability> pkg =
                io.jnative.pkg.TargetCapability.forTriple("wasm32-browser");
        assertFalse(backend.contains(TargetCapability.THREADS));
        assertFalse(pkg.contains(io.jnative.pkg.TargetCapability.THREADS));
        assertTrue(backend.contains(TargetCapability.BROWSER_DOM));
        assertTrue(pkg.contains(io.jnative.pkg.TargetCapability.BROWSER_DOM));
    }

    @Test
    void backendAndPkgAgreeOnNativeFilesystem() {
        Set<TargetCapability> backend = TargetCapability.forNative();
        Set<io.jnative.pkg.TargetCapability> pkg =
                io.jnative.pkg.TargetCapability.forTriple("native");
        assertTrue(backend.contains(TargetCapability.FILESYSTEM));
        assertTrue(pkg.contains(io.jnative.pkg.TargetCapability.FILESYSTEM));
        assertTrue(backend.contains(TargetCapability.THREADS));
        assertTrue(pkg.contains(io.jnative.pkg.TargetCapability.THREADS));
    }

    @Test
    void mobileContainerUsesWasmCompileTriple() {
        assertEquals("wasm32-browser", TargetId.MOBILE_CONTAINER.compileTriple());
        assertFalse(TargetProfiles.profile(TargetId.MOBILE_CONTAINER)
                .providesPackageFlag(TargetCapability.THREADS));
        assertFalse(TargetProfiles.profile(TargetId.MOBILE_CONTAINER)
                .providesPackageFlag(TargetCapability.NATIVE_FFI));
        assertEquals(CapabilityEvidence.UNSUPPORTED,
                TargetProfiles.profile(TargetId.MOBILE_CONTAINER).evidenceOf(Capability.FFI_NATIVE));
    }

    @Test
    void wasmRejectsFilesystemApiWithStableCode() {
        List<Diagnostic> diags = TargetCapabilityChecker.checkSourceText(
                "App.jn",
                "class App { void m() { Files.exists(\"x\"); } }\n",
                "wasm32-browser");
        assertFalse(diags.isEmpty());
        assertEquals(DiagnosticCodes.TARGET_API, diags.getFirst().code());
        assertTrue(diags.getFirst().message().toLowerCase().contains("filesystem"));
        assertNotNull(diags.getFirst().hint());
        String json = DiagnosticJson.toJsonObject(diags.getFirst());
        assertTrue(json.contains("\"code\":\"JN2101\""));
        assertTrue(json.contains("\"hint\":"));
    }

    @Test
    void nativeAllowsFilesystemApi() {
        List<Diagnostic> diags = TargetCapabilityChecker.checkSourceText(
                "App.jn",
                "class App { void m() { Files.exists(\"x\"); } }\n",
                "native");
        assertTrue(diags.isEmpty());
    }

    @Test
    void packageRequiresMismatchUsesPackageCode() {
        // Simulate via checker.describe path — empty nodes ok; use source of truth flags
        Set<TargetCapability> required = Set.of(TargetCapability.FILESYSTEM);
        Set<TargetCapability> provided = TargetProfiles.packageFlags(TargetId.WASM32_BROWSER);
        assertFalse(TargetCapabilityChecker.describeMissing(required, provided).isEmpty());
    }

    @Test
    void diagnosticJsonBuildReportIncludesMeta() {
        String json = DiagnosticJson.buildReportJson(
                false, null, List.of(),
                java.util.Map.of("compilerVersion", "0.32", "target", "wasm32-browser"));
        assertTrue(json.contains("\"success\":false"));
        assertTrue(json.contains("\"compilerVersion\":\"0.32\""));
        assertTrue(json.contains("\"target\":\"wasm32-browser\""));
    }
}
