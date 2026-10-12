/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.incremental;

import io.jnative.Main;
import io.jnative.diagnostics.DiagnosticJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-target build result record written only after a successful publish.
 * Distinguishes this generation from any previously preserved last-known-good artifact.
 *
 * @author 张晓达
 */
public final class BuildArtifactManifest {
    public static final String FILE_NAME = "build-result.json";

    private BuildArtifactManifest() {
    }

    public static Path manifestPath(Path projectRoot, String targetName) {
        return projectRoot.resolve(".jnative/build").resolve(targetName).resolve(FILE_NAME);
    }

    public static void writeSuccess(
            Path projectRoot,
            String targetName,
            String triple,
            String artifactAbsolute,
            String inputFingerprint,
            int optLevel,
            boolean releaseMode,
            String lockFingerprint
    ) throws IOException {
        Path man = manifestPath(projectRoot, sanitize(targetName));
        Files.createDirectories(man.getParent());
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("status", "success");
        meta.put("compilerVersion", Main.VERSION);
        meta.put("target", targetName == null ? "" : targetName);
        meta.put("triple", triple == null ? "native" : triple);
        meta.put("optLevel", Integer.toString(optLevel));
        meta.put("release", Boolean.toString(releaseMode));
        meta.put("inputFingerprint", inputFingerprint == null ? "" : inputFingerprint);
        meta.put("lockFingerprint", lockFingerprint == null ? "" : lockFingerprint);
        meta.put("artifact", artifactAbsolute == null ? "" : artifactAbsolute);
        meta.put("generationTimeMs", Long.toString(System.currentTimeMillis()));
        String json = DiagnosticJson.buildReportJson(true, artifactAbsolute, java.util.List.of(), meta);
        Path staging = man.resolveSibling(FILE_NAME + ".tmp");
        Files.writeString(staging, json, StandardCharsets.UTF_8);
        Files.move(staging, man, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static void writeFailure(
            Path projectRoot,
            String targetName,
            String triple,
            String reason
    ) throws IOException {
        Path man = manifestPath(projectRoot, sanitize(targetName));
        Files.createDirectories(man.getParent());
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("status", "failed");
        meta.put("compilerVersion", Main.VERSION);
        meta.put("target", targetName == null ? "" : targetName);
        meta.put("triple", triple == null ? "native" : triple);
        meta.put("reason", reason == null ? "build failed" : reason);
        meta.put("artifact", null);
        meta.put("generationTimeMs", Long.toString(System.currentTimeMillis()));
        // success=false and artifact=null — runners must not treat last-good as this build
        String json = DiagnosticJson.buildReportJson(false, null, java.util.List.of(), meta);
        Path staging = man.resolveSibling(FILE_NAME + ".tmp");
        Files.writeString(staging, json, StandardCharsets.UTF_8);
        try {
            Files.move(staging, man, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(staging, man, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * True only when the last recorded result for this target was a success
     * and still points at an existing artifact. Failed builds overwrite the
     * manifest with {@code success:false} so runners refuse stale binaries.
     */
    public static boolean isFreshSuccess(Path projectRoot, String targetName, Path expectedArtifact)
            throws IOException {
        Path man = manifestPath(projectRoot, sanitize(targetName));
        if (!Files.isRegularFile(man)) {
            return false;
        }
        String text = Files.readString(man, StandardCharsets.UTF_8);
        if (!text.contains("\"success\":true") || text.contains("\"status\":\"failed\"")) {
            return false;
        }
        if (expectedArtifact != null) {
            if (!Files.exists(expectedArtifact)) {
                return false;
            }
            String abs = expectedArtifact.toAbsolutePath().normalize().toString().replace('\\', '/');
            String norm = text.replace('\\', '/');
            return norm.contains(abs);
        }
        return true;
    }

    private static String sanitize(String targetName) {
        if (targetName == null || targetName.isBlank()) {
            return "default";
        }
        return targetName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
