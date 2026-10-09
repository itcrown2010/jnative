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

    public static int dispatch(String[] args) throws Exception {
        Path file = null;
        boolean stdin = false;
        String pathName = "stdin.java";
        long version = 0;
        boolean json = true;
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--stdin" -> stdin = true;
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
            System.err.println("usage: jnative analyze <file.jn|.java> | --stdin [--path name] [--format json|text]");
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
