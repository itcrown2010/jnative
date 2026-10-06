package io.jnative.backend;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
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
        List<String> command = new ArrayList<>(toolchain.linkCommand(llFile, output, optLevel));
        // Insert extra C sources before -o so clang compiles and links them.
        int oIndex = command.indexOf("-o");
        if (oIndex < 0) {
            oIndex = command.size();
        }
        List<String> extras = new ArrayList<>();
        for (Path src : extraSources) {
            if (src != null) {
                extras.add(src.toAbsolutePath().normalize().toString());
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
            String text = out.toString();
            if (code != 0) {
                text = rewriteLinkerErrors(text, libraries);
            }
            return new LinkResult(code == 0, code, text);
        } catch (Exception e) {
            return new LinkResult(false, -1, e.getMessage());
        }
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
