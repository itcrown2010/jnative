package io.jnative.backend;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts bundled native C runtime sources (socket + multithreading) for linking.
 */
public final class NativeRuntimeSources {
    private NativeRuntimeSources() {
    }

    /**
     * Materialize platform C sources into {@code dir}. Returns paths to compile/link.
     */
    public static List<Path> materializeSocketSources(Path dir, Platform platform) throws IOException {
        return materializeAll(dir, platform);
    }

    public static List<Path> materializeAll(Path dir, Platform platform) throws IOException {
        Files.createDirectories(dir);
        List<Path> sources = new ArrayList<>();
        copyResource("native/posix/jnative_socket.h", dir.resolve("jnative_socket.h"));
        if (platform == Platform.WINDOWS_X86_64) {
            sources.add(copyResource("native/windows/jnative_socket.c", dir.resolve("jnative_socket.c")));
            // Windows threading backend not yet shipped; POSIX path used on macOS/Linux.
        } else {
            sources.add(copyResource("native/posix/jnative_socket.c", dir.resolve("jnative_socket.c")));
            copyResource("native/posix/jnative_mt.h", dir.resolve("jnative_mt.h"));
            sources.add(copyResource("native/posix/jnative_mt.c", dir.resolve("jnative_mt.c")));
        }
        return sources;
    }

    private static Path copyResource(String resource, Path target) throws IOException {
        try (InputStream in = NativeRuntimeSources.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("missing runtime resource: " + resource);
            }
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return target;
    }
}
