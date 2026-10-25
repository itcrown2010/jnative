/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.cli;

import io.jnative.compiler.Compilation;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.SourceUnit;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.diagnostics.DiagnosticJson;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Frontend-only analysis for IDE tooling: {@code jnative analyze}.
 * Never links or runs programs.
 * @author 张晓达
 */
public final class AnalyzeCli {
    private AnalyzeCli() {
    }

    /** Canonical path (resolves symlinks such as macOS /tmp) so overlays match the files found on disk. */
    private static Path realPath(Path p) {
        try {
            return p.toRealPath();
        } catch (java.io.IOException e) {
            return p.toAbsolutePath().normalize();
        }
    }

    public static int dispatch(String[] args) throws Exception {
        Path file = null;
        Path projectRoot = null;
        java.util.Map<String, String> overlays = new java.util.LinkedHashMap<>();
        boolean stdin = false;
        String pathName = "stdin.java";
        long version = 0;
        boolean json = true;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--stdin" -> stdin = true;
                case "--project" -> {
                    if (i + 1 < args.length) {
                        projectRoot = realPath(Path.of(args[++i]));
                    }
                }
                case "--overlay" -> {
                    // --overlay /abs/Source.java=/tmp/unsaved-text : unsaved editor buffer for that source
                    if (i + 1 < args.length) {
                        String spec = args[++i];
                        int eq = spec.lastIndexOf('=');
                        if (eq > 0) {
                            try {
                                overlays.put(realPath(Path.of(spec.substring(0, eq))).toString(),
                                        Files.readString(Path.of(spec.substring(eq + 1)), StandardCharsets.UTF_8));
                            } catch (java.io.IOException e) {
                                System.err.println("error: cannot read overlay " + spec);
                                return 1;
                            }
                        }
                    }
                }
                case "--path" -> {
                    if (i + 1 < args.length) {
                        pathName = args[++i];
                    }
                }
                case "--version" -> {
                    if (i + 1 < args.length) {
                        version = Long.parseLong(args[++i]);
                    }
                }
                case "--format" -> {
                    if (i + 1 < args.length) {
                        json = "json".equalsIgnoreCase(args[++i]);
                    }
                }
                case "--format=json" -> json = true;
                case "--format=text" -> json = false;
                default -> {
                    if (!a.startsWith("-") && file == null) {
                        file = Path.of(a);
                    }
                }
            }
        }

        if (projectRoot != null) {
            // Whole-project frontend check (all module sources + unsaved overlays); never builds native code.
            java.util.List<io.jnative.compiler.SourceUnit> units =
                    io.jnative.lsp.ProjectSourceCollector.collect(projectRoot, overlays);
            if (units.isEmpty()) {
                System.err.println("error: no sources under " + projectRoot);
                return 1;
            }
            Compilation pc = new JNativeCompiler().analyzeUnits(units, version);
            if (json) {
                System.out.println(DiagnosticJson.buildReportJson(pc.success(), null, pc.diagnostics()));
            } else {
                for (Diagnostic d : pc.diagnostics()) {
                    System.out.println(d);
                }
            }
            return pc.success() ? 0 : 1;
        }

        String text;
        String unitPath;
        if (stdin) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            text = sb.toString();
            unitPath = pathName;
        } else if (file != null) {
            Path abs = file.toAbsolutePath().normalize();
            if (!Files.isRegularFile(abs)) {
                System.err.println("error: file not found: " + abs);
                return 1;
            }
            text = Files.readString(abs, StandardCharsets.UTF_8);
            unitPath = abs.toString();
        } else {
            System.err.println("usage: jnative analyze <file.jn|.java> | --stdin [--path name] | --project DIR [--overlay path=file]... [--format json|text]");
            return 1;
        }

        Compilation c = new JNativeCompiler().analyze(SourceUnit.ofPath(Path.of(unitPath), text), version);
        if (json) {
            System.out.println(DiagnosticJson.buildReportJson(
                    c.success(), null, c.diagnostics()));
        } else {
            for (Diagnostic d : c.diagnostics()) {
                System.err.println(d);
            }
        }
        return c.success() ? 0 : 1;
    }
}
