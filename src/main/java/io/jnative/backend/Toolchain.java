package io.jnative.backend;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Locates clang/LLVM tools on the host system.
 */
public final class Toolchain {
    private final Platform platform;
    private final Path clang;

    private Toolchain(Platform platform, Path clang) {
        this.platform = platform;
        this.clang = clang;
    }

    public Platform platform() {
        return platform;
    }

    public Path clang() {
        return clang;
    }

    public static Optional<Toolchain> detect() {
        Platform platform = Platform.detect();
        Optional<Path> clang = findOnPath("clang")
                .or(() -> findOnPath("clang-18"))
                .or(() -> findOnPath("clang-17"))
                .or(() -> findOnPath("clang-16"));
        return clang.map(path -> new Toolchain(platform, path));
    }

    private static Optional<Path> findOnPath(String name) {
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null || pathEnv.isBlank()) {
            return Optional.empty();
        }
        String sep = Platform.detect().pathSeparator();
        for (String dir : pathEnv.split(sep)) {
            if (dir.isBlank()) {
                continue;
            }
            Path candidate = Path.of(dir, name);
            if (Files.isExecutable(candidate)) {
                return Optional.of(candidate);
            }
            Path exe = Path.of(dir, name + ".exe");
            if (Files.isExecutable(exe)) {
                return Optional.of(exe);
            }
        }
        // macOS: /usr/bin/clang may not be "executable" via Files when Xcode CLT is present via shim
        try {
            ProcessBuilder pb = new ProcessBuilder(name, "--version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader ignored = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                // drain
                while (ignored.readLine() != null) {
                    // no-op
                }
            }
            if (p.waitFor() == 0) {
                return Optional.of(Path.of(name));
            }
        } catch (Exception ignored) {
            // not found
        }
        return Optional.empty();
    }

    public List<String> linkCommand(Path llFile, Path output) {
        return linkCommand(llFile, output, 2);
    }

    public List<String> linkCommand(Path llFile, Path output, int optLevel) {
        List<String> cmd = new ArrayList<>();
        cmd.add(clang.toString());
        cmd.add("-O" + Math.max(0, Math.min(3, optLevel)));
        cmd.add(llFile.toAbsolutePath().toString());
        cmd.add("-o");
        cmd.add(output.toAbsolutePath().toString());
        cmd.add("-Wno-override-module");
        cmd.add("-pthread");
        /* Export symbols for backtrace/dladdr demangling of _JN_* frames. */
        cmd.add("-rdynamic");
        return cmd;
    }
}
