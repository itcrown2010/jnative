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
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Invokes clang to turn LLVM IR into a native executable.
 * @author 张晓达
 */
public final class NativeLinker {
    private final Toolchain toolchain;

    public NativeLinker(Toolchain toolchain) {
        this.toolchain = toolchain;
    }

    public record LinkResult(boolean success, int exitCode, String output) {
    }

    public LinkResult link(Path llFile, Path output) {
        return link(llFile, output, List.of(), List.of());
    }

    public LinkResult link(Path llFile, Path output, Collection<String> libraries) {
        return link(llFile, output, libraries, List.of());
    }

    public LinkResult link(
            Path llFile,
            Path output,
            Collection<String> libraries,
            Collection<Path> libraryPaths
    ) {
        return link(llFile, output, libraries, libraryPaths, List.of());
    }

    public LinkResult link(
            Path llFile,
            Path output,
            Collection<String> libraries,
            Collection<Path> libraryPaths,
            Collection<Path> extraSources
    ) {
        return link(llFile, output, libraries, libraryPaths, extraSources, 2);
    }

    public LinkResult link(
            Path llFile,
            Path output,
            Collection<String> libraries,
            Collection<Path> libraryPaths,
            Collection<Path> extraSources,
            int optLevel
    ) {
        return link(llFile, output, libraries, libraryPaths, extraSources, optLevel, List.of());
    }

    public LinkResult link(
            Path llFile,
            Path output,
            Collection<String> libraries,
            Collection<Path> libraryPaths,
            Collection<Path> extraSources,
            int optLevel,
            Collection<String> extraFlags
    ) {
        return link(llFile, output, libraries, libraryPaths, extraSources, optLevel, extraFlags, true);
    }

    public LinkResult link(
            Path llFile,
            Path output,
            Collection<String> libraries,
            Collection<Path> libraryPaths,
            Collection<Path> extraSources,
            int optLevel,
            Collection<String> extraFlags,
            boolean debugSymbols
    ) {
        List<String> command = new ArrayList<>(toolchain.linkCommand(llFile, output, optLevel, debugSymbols));
        // Insert extra C/.o sources before -o so clang compiles and links them.
        int oIndex = command.indexOf("-o");
        if (oIndex < 0) {
            oIndex = command.size();
        }
        List<String> extras = new ArrayList<>();
        StringBuilder prepOut = new StringBuilder();
        for (Path src : extraSources) {
            if (src == null) {
                continue;
            }
            Path abs = src.toAbsolutePath().normalize();
            String name = abs.getFileName().toString().toLowerCase(Locale.ROOT);
            if (name.endsWith(".rc")) {
                Path obj = abs.resolveSibling(
                        abs.getFileName().toString().replaceAll("(?i)\\.rc$", ".o"));
                LinkResult rc = compileResource(abs, obj);
                if (!rc.success()) {
                    return rc;
                }
                if (!rc.output().isBlank()) {
                    prepOut.append(rc.output());
                }
                extras.add(obj.toString());
            } else {
                extras.add(abs.toString());
            }
        }
        command.addAll(oIndex, extras);
        for (Path libPath : libraryPaths) {
            if (libPath != null) {
                command.add("-L" + libPath.toAbsolutePath().normalize());
            }
        }
        for (String lib : libraries) {
            if (lib == null || lib.isBlank()) {
                continue;
            }
            command.add("-l" + lib);
        }
        if (extraFlags != null) {
            for (String flag : extraFlags) {
                if (flag != null && !flag.isBlank()) {
                    command.add(flag);
                }
            }
        }
        try {
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            int code = process.waitFor();
            String text = prepOut.toString() + out.toString();
            if (code != 0) {
                text = rewriteLinkerErrors(text, libraries);
            }
            return new LinkResult(code == 0, code, text);
        } catch (Exception e) {
            return new LinkResult(false, -1, e.getMessage());
        }
    }

    /**
     * Compile a Windows .rc (e.g. ComCtl6 manifest) to a COFF object with windres/llvm-windres.
     */
    private LinkResult compileResource(Path rcFile, Path objFile) {
        try {
            Files.createDirectories(objFile.getParent());
            Path windres = findWindres();
            if (windres == null) {
                return new LinkResult(false, -1,
                        "windres/llvm-windres not found; cannot embed Windows manifest: " + rcFile);
            }
            List<String> cmd = List.of(
                    windres.toString(),
                    "-i", rcFile.toString(),
                    "-o", objFile.toString(),
                    "-O", "coff");
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(rcFile.getParent().toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            int code = process.waitFor();
            if (code != 0 || !Files.isRegularFile(objFile)) {
                return new LinkResult(false, code,
                        "windres failed for " + rcFile + "\n" + out);
            }
            return new LinkResult(true, 0, out.toString());
        } catch (Exception e) {
            return new LinkResult(false, -1, e.getMessage());
        }
    }

    private Path findWindres() {
        Path clang = toolchain.clang();
        Path dir = clang.getParent();
        if (dir != null) {
            for (String name : List.of(
                    "windres", "windres.exe",
                    "llvm-windres", "llvm-windres.exe",
                    "x86_64-w64-mingw32-windres", "x86_64-w64-mingw32-windres.exe")) {
                Path candidate = dir.resolve(name);
                if (Files.isExecutable(candidate) || Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static String rewriteLinkerErrors(String output, Collection<String> libraries) {
        String lower = output.toLowerCase();
        StringBuilder msg = new StringBuilder();
        for (String lib : libraries) {
            if (lib == null || lib.isBlank()) {
                continue;
            }
            if (lower.contains("library not found for -l" + lib.toLowerCase())
                    || lower.contains("cannot find -l" + lib.toLowerCase())
                    || lower.contains("-l" + lib.toLowerCase())) {
                msg.append("native library not found: ").append(lib).append('\n');
            }
        }
        if (lower.contains("undefined symbols") || lower.contains("undefined reference")
                || lower.contains("unresolved")) {
            msg.append("unresolved native symbol (see linker detail below)\n");
        }
        if (!msg.isEmpty()) {
            return msg + output;
        }
        return output;
    }
}
