package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FilesystemRound4EndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void unicodeEmojiSpaceNamesAndAtomic() throws Exception {
        assertStdout(tempDir, """
                public class Main {
                    public static void main(String[] args) {
                        FsPath dir = FsPath.of("sub dir");
                        Files.createDirectories(dir);
                        FsPath p = dir.resolve("你好 🌟.txt");
                        Files.writeAtomicString(p, "ok-中文");
                        int ex = 0;
                        if (Files.exists(p)) {
                            ex = 1;
                        }
                        System.out.println(ex);
                        System.out.println(Files.readString(p));
                        int sz = 0;
                        if (Files.size(p) > 0L) {
                            sz = 1;
                        }
                        System.out.println(sz);
                    }
                }
                """, "1\nok-中文\n1\n");
    }

    @Test
    void walkSkipsSymlinkCycles() throws Exception {
        Path root = tempDir.resolve("walkroot");
        Files.createDirectories(root.resolve("a"));
        Files.createFile(root.resolve("a/f.txt"));
        try {
            Files.createSymbolicLink(root.resolve("a/loop"), root.resolve("a"));
        } catch (UnsupportedOperationException | java.io.IOException ex) {
            Assumptions.assumeTrue(false, "symlink unsupported: " + ex.getMessage());
        }
        assertStdout(tempDir, """
                public class Main {
                    public static void main(String[] args) {
                        ArrayList<FsPath> all = Files.walk(FsPath.of("walkroot"));
                        int a = 0;
                        if (all.size() >= 2) {
                            a = 1;
                        }
                        int b = 0;
                        if (all.size() < 100) {
                            b = 1;
                        }
                        System.out.println(a);
                        System.out.println(b);
                    }
                }
                """, "1\n1\n");
    }

    @Test
    void handleLeakSmoke1k() throws Exception {
        assertStdout(tempDir, """
                public class Main {
                    public static void main(String[] args) {
                        ResourceTracker.reset();
                        Files.writeString(FsPath.of("x.bin"), "x");
                        int i = 0;
                        while (i < 1000) {
                            try (FileInputStream in = new FileInputStream("x.bin")) {
                                in.read();
                            }
                            i = i + 1;
                        }
                        System.out.println(ResourceTracker.files());
                    }
                }
                """, "0\n");
    }

    @Test
    void watchServicePoll() throws Exception {
        Path dir = tempDir.resolve("wdir");
        Files.createDirectories(dir);
        assertStdout(tempDir, """
                public class Main {
                    public static void main(String[] args) {
                        WatchService w = WatchService.open(FsPath.of("wdir"));
                        int before = w.pollEx();
                        Files.writeString(FsPath.of("wdir").resolve("n.txt"), "1");
                        int seen = 0;
                        int i = 0;
                        while (i < 50) {
                            int ev = w.pollEx();
                            if (ev == WatchKind.CHANGED) {
                                seen = 1;
                            }
                            if (ev == WatchKind.OVERFLOW) {
                                seen = 1;
                            }
                            Thread.sleep(20);
                            i = i + 1;
                        }
                        w.close();
                        int beforeOk = 0;
                        if (before == WatchKind.NONE) {
                            beforeOk = 1;
                        }
                        if (before == WatchKind.CHANGED) {
                            beforeOk = 1;
                        }
                        System.out.println(beforeOk);
                        System.out.println(seen);
                    }
                }
                """, "1\n1\n");
    }

    private void assertStdout(Path workDir, String source, String expected) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve("FsR4.java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("FsR4Bin");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.directory(workDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "timeout");
        assertEquals(0, process.exitValue(), stdout);
        assertEquals(expected, stdout);
    }
}
