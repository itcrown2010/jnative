package io.jnative.backend;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Extracts bundled native C/ObjC runtime sources for linking.
 */
public final class NativeRuntimeSources {
    private NativeRuntimeSources() {
    }

    public static List<Path> materializeSocketSources(Path dir, Platform platform) throws IOException {
        return materializeAll(dir, platform, false, false, "auto");
    }

    public static List<Path> materializeAll(Path dir, Platform platform) throws IOException {
        return materializeAll(dir, platform, false, false, "auto");
    }

    /**
     * @param includeUi whether to link UI runtime
     * @param headless force headless UI (no Cocoa/Win32/GTK objs)
     * @param uiBackend auto|headless|cocoa|win32|gtk|none
     */
    public static List<Path> materializeAll(
            Path dir,
            Platform platform,
            boolean includeUi,
            boolean headless,
            String uiBackend
    ) throws IOException {
        return materializeAll(dir, platform, includeUi, false, headless, uiBackend);
    }

    /**
     * @param includeGraphics whether to link software/graphics runtime (PNG/zlib raster)
     */
    public static List<Path> materializeAll(
            Path dir,
            Platform platform,
            boolean includeUi,
            boolean includeGraphics,
            boolean headless,
            String uiBackend
    ) throws IOException {
        return materializeAll(dir, platform, includeUi, includeGraphics, headless, uiBackend, false);
    }

    /**
     * @param includePoll whether to link async IoPoller runtime (jnative_poll_*)
     */
    public static List<Path> materializeAll(
            Path dir,
            Platform platform,
            boolean includeUi,
            boolean includeGraphics,
            boolean headless,
            String uiBackend,
            boolean includePoll
    ) throws IOException {
        return materializeAll(dir, platform, includeUi, includeGraphics, headless, uiBackend, includePoll, false);
    }

    /**
     * @param includeTls whether to link OpenSSL-backed jnative_tls runtime (0.31)
     */
    public static List<Path> materializeAll(
            Path dir,
            Platform platform,
            boolean includeUi,
            boolean includeGraphics,
            boolean headless,
            String uiBackend,
            boolean includePoll,
            boolean includeTls
    ) throws IOException {
        Files.createDirectories(dir);
        List<Path> sources = new ArrayList<>();
        copyResource("native/posix/jnative_socket.h", dir.resolve("jnative_socket.h"));
        if (platform == Platform.WINDOWS_X86_64) {
            sources.add(copyResource("native/windows/jnative_socket.c", dir.resolve("jnative_socket.c")));
        } else {
            sources.add(copyResource("native/posix/jnative_socket.c", dir.resolve("jnative_socket.c")));
            copyResource("native/posix/jnative_mt.h", dir.resolve("jnative_mt.h"));
            sources.add(copyResource("native/posix/jnative_mt.c", dir.resolve("jnative_mt.c")));
            copyResource("native/posix/jnative_gc2.h", dir.resolve("jnative_gc2.h"));
            sources.add(copyResource("native/posix/jnative_gc2.c", dir.resolve("jnative_gc2.c")));
            sources.add(copyResource(
                    "native/posix/jnative_crypto.c", dir.resolve("jnative_crypto.c")));
            copyResource("native/posix/jnative_fs.h", dir.resolve("jnative_fs.h"));
            sources.add(copyResource("native/posix/jnative_fs.c", dir.resolve("jnative_fs.c")));
            copyResource("native/posix/jnative_proc.h", dir.resolve("jnative_proc.h"));
            sources.add(copyResource("native/posix/jnative_proc.c", dir.resolve("jnative_proc.c")));
        }

        if (includeTls && platform != Platform.WINDOWS_X86_64) {
            copyResource("native/posix/jnative_tls.h", dir.resolve("jnative_tls.h"));
            sources.add(copyResource("native/posix/jnative_tls.c", dir.resolve("jnative_tls.c")));
        }

        if (includePoll) {
            copyResource("native/posix/jnative_poll.h", dir.resolve("jnative_poll.h"));
            if (platform == Platform.WINDOWS_X86_64) {
                sources.add(copyResource(
                        "native/windows/jnative_poll_iocp.c", dir.resolve("jnative_poll_iocp.c")));
            } else if (platform == Platform.LINUX_X86_64) {
                sources.add(copyResource(
                        "native/posix/jnative_poll_epoll.c", dir.resolve("jnative_poll_epoll.c")));
            } else {
                sources.add(copyResource(
                        "native/posix/jnative_poll_kqueue.c", dir.resolve("jnative_poll_kqueue.c")));
            }
        }

        if (includeGraphics || includeUi) {
            copyResource("native/posix/jnative_graphics.h", dir.resolve("jnative_graphics.h"));
            sources.add(copyResource(
                    "native/posix/jnative_graphics.c", dir.resolve("jnative_graphics.c")));
        }

        String backend = uiBackend == null ? "auto" : uiBackend.toLowerCase();
        boolean wantUi = includeUi && !"none".equals(backend);
        if (wantUi) {
            copyResource("native/posix/jnative_ui.h", dir.resolve("jnative_ui.h"));
            if (platform == Platform.WINDOWS_X86_64) {
                sources.add(copyResource(
                        "native/windows/jnative_ui_win32.c", dir.resolve("jnative_ui_win32.c")));
                // Win32 file is self-contained API; no separate jnative_ui.c.
            } else {
                sources.add(copyResource("native/posix/jnative_ui.c", dir.resolve("jnative_ui.c")));
                boolean useCocoa = !headless
                        && !"headless".equals(backend)
                        && !"gtk".equals(backend)
                        && ("cocoa".equals(backend)
                        || ("auto".equals(backend)
                        && (platform == Platform.MACOS_ARM64 || platform == Platform.MACOS_X86_64)));
                if (useCocoa) {
                    sources.add(copyResource(
                            "native/posix/jnative_ui_cocoa.m", dir.resolve("jnative_ui_cocoa.m")));
                }
            }
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
