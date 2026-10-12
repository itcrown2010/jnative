/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Verifies generated LLVM IR (.ll) before native link.
 * Prefers {@code llvm-as}; falls back to {@code clang -c} IR parse.
 * @author 张晓达
 */
public final class LlvmIrFileVerifier {

    public record Result(boolean ok, String tool, String output, int exitCode) {
    }

    private LlvmIrFileVerifier() {
    }

    public static Result verify(Path llFile, Toolchain toolchain) {
        if (llFile == null || !Files.isRegularFile(llFile)) {
            return new Result(false, "none", "missing .ll file", -1);
        }
        Optional<Path> llvmAs = findLlvmAs();
        if (llvmAs.isPresent()) {
            return run(List.of(llvmAs.get().toString(), llFile.toAbsolutePath().toString(), "-o", "/dev/null"),
                    "llvm-as");
        }
        /* clang can parse LLVM IR bitcode/text when compiling .ll */
        List<String> cmd = new ArrayList<>();
        cmd.add(toolchain.clang().toString());
        cmd.add("-c");
        cmd.add(llFile.toAbsolutePath().toString());
        cmd.add("-o");
        cmd.add(llFile.toAbsolutePath().toString() + ".verify.o");
        cmd.add("-Wno-override-module");
        Result r = run(cmd, "clang-ir");
        try {
            Files.deleteIfExists(Path.of(llFile.toAbsolutePath() + ".verify.o"));
        } catch (Exception ignored) {
            // best-effort
        }
        return r;
    }

    private static Result run(List<String> cmd, String tool) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            boolean finished = p.waitFor(60, TimeUnit.SECONDS);
            if (!finished) {
                p.destroyForcibly();
                return new Result(false, tool, "timeout\n" + out, -1);
            }
            int code = p.exitValue();
            return new Result(code == 0, tool, out.toString(), code);
        } catch (Exception e) {
            return new Result(false, tool, e.getMessage(), -1);
        }
    }

    static Optional<Path> findLlvmAs() {
        String[] candidates = {
                "llvm-as",
                "/opt/homebrew/opt/llvm/bin/llvm-as",
                "/usr/local/opt/llvm/bin/llvm-as",
                "/usr/bin/llvm-as"
        };
        for (String c : candidates) {
            Path p = Path.of(c);
            if (Files.isExecutable(p) || "llvm-as".equals(c)) {
                try {
                    ProcessBuilder pb = new ProcessBuilder(c.equals("llvm-as") ? "llvm-as" : p.toString(), "--version");
                    pb.redirectErrorStream(true);
                    Process proc = pb.start();
                    try (BufferedReader ignored = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                        while (ignored.readLine() != null) {
                            // drain
                        }
                    }
                    if (proc.waitFor(5, TimeUnit.SECONDS) && proc.exitValue() == 0) {
                        return Optional.of(c.equals("llvm-as") ? Path.of("llvm-as") : p);
                    }
                } catch (Exception ignored) {
                    // try next
                }
            }
        }
        return Optional.empty();
    }
}