package io.jnative.backend;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;

/**
 * Invokes clang to turn LLVM IR into a native executable.
 */
public final class NativeLinker {
    private final Toolchain toolchain;

    public NativeLinker(Toolchain toolchain) {
        this.toolchain = toolchain;
    }

    public record LinkResult(boolean success, int exitCode, String output) {
    }

    public LinkResult link(Path llFile, Path output) {
        List<String> command = toolchain.linkCommand(llFile, output);
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
            return new LinkResult(code == 0, code, out.toString());
        } catch (Exception e) {
            return new LinkResult(false, -1, e.getMessage());
        }
    }
}
