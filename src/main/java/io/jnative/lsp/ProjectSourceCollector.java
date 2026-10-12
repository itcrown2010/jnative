/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.lsp;

import io.jnative.build.JNativeProject;
import io.jnative.build.Module;
import io.jnative.build.ProjectFile;
import io.jnative.compiler.SourceUnit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Collects project + cached package sources for IDE analyze (no native build).
 * @author 张晓达
 */
public final class ProjectSourceCollector {
    private ProjectSourceCollector() {
    }

    public static Path findProjectRoot(Path start) {
        Path cur = start == null ? null : start.toAbsolutePath().normalize();
        if (cur != null && Files.isRegularFile(cur)) {
            cur = cur.getParent();
        }
        while (cur != null) {
            if (Files.isRegularFile(cur.resolve("jnative.project"))
                    || (Files.isRegularFile(cur.resolve("jnative.toml"))
                    && Files.isDirectory(cur.resolve("src")))) {
                return cur;
            }
            cur = cur.getParent();
        }
        return null;
    }

    /**
     * Load source units for IDE. {@code overlays} maps absolute path → unsaved text.
     */
    public static List<SourceUnit> collect(Path projectRoot, Map<String, String> overlays) {
        Map<String, SourceUnit> byPath = new LinkedHashMap<>();
        if (projectRoot != null && Files.isDirectory(projectRoot)) {
            try {
                JNativeProject project = ProjectFile.discover(projectRoot);
                for (Module mod : project.modules().values()) {
                    for (String root : mod.sourceRoots()) {
                        Path dir = projectRoot.resolve(root).normalize();
                        addTree(dir, byPath);
                    }
                }
            } catch (IOException e) {
                addTree(projectRoot.resolve("src"), byPath);
            }
            // Package cache extracted by the package manager (third-party source libs)
            Path cache = projectRoot.resolve(".jnative").resolve("pkg-cache").resolve("extracted");
            addTree(cache, byPath);
        }
        if (overlays != null) {
            for (Map.Entry<String, String> e : overlays.entrySet()) {
                if (e.getKey() == null || e.getValue() == null) {
                    continue;
                }
                Path p = Path.of(e.getKey()).toAbsolutePath().normalize();
                byPath.put(p.toString(), SourceUnit.ofPath(p, e.getValue()));
            }
        }
        return new ArrayList<>(byPath.values());
    }

    private static void addTree(Path dir, Map<String, SourceUnit> byPath) {
        if (dir == null || !Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
                        return n.endsWith(".jn") || n.endsWith(".java");
                    })
                    .forEach(p -> {
                        try {
                            Path abs = p.toAbsolutePath().normalize();
                            String key = abs.toString();
                            if (byPath.containsKey(key)) {
                                return;
                            }
                            String text = Files.readString(abs, StandardCharsets.UTF_8);
                            byPath.put(key, SourceUnit.ofPath(abs, text));
                        } catch (IOException ignored) {
                            // skip unreadable file; do not fail whole project
                        }
                    });
        } catch (IOException ignored) {
            // skip bad trees
        }
    }
}
