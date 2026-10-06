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
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class FileIoEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void readStringAscii() throws Exception {
        Path hello = tempDir.resolve("hello.txt");
        Files.writeString(hello, "Hello JNative", StandardCharsets.UTF_8);
        assertStdout(cwd(tempDir), """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(File.readString("hello.txt"));
                    }
                }
                """, "Hello JNative\n");
    }

    @Test
    void readStringUnicode() throws Exception {
        Files.writeString(tempDir.resolve("unicode.txt"), "你好，JNative", StandardCharsets.UTF_8);
        assertStdout(cwd(tempDir), """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(File.readString("unicode.txt"));
                    }
                }
                """, "你好，JNative\n");
    }

    @Test
    void writeStringRoundtrip() throws Exception {
        assertStdout(cwd(tempDir), """
                public class Main {
                    public static void main(String[] args) {
                        File.writeString("output.txt", "Native Java");
                        System.out.println(File.readString("output.txt"));
                    }
                }
                """, "Native Java\n");
        assertEquals("Native Java", Files.readString(tempDir.resolve("output.txt"), StandardCharsets.UTF_8));
    }

    @Test
    void binaryRoundtrip() throws Exception {
        assertStdout(cwd(tempDir), """
                public class Main {
                    public static void main(String[] args) {
                        byte[] data = new byte[5];
                        data[0] = 0;
                        data[1] = 1;
                        data[2] = -1;
                        data[3] = 0;
                        data[4] = 127;
                        File.writeBytes("test.bin", data);
                        byte[] got = File.readBytes("test.bin");
                        if (got.length != 5) {
                            System.out.println(0);
                            return;
                        }
                        int ok = 1;
                        if (got[0] != 0) { ok = 0; }
                        if (got[1] != 1) { ok = 0; }
                        if (got[2] != -1) { ok = 0; }
                        if (got[3] != 0) { ok = 0; }
                        if (got[4] != 127) { ok = 0; }
                        System.out.println(ok);
                    }
                }
                """, "1\n");
        byte[] disk = Files.readAllBytes(tempDir.resolve("test.bin"));
        assertArrayEquals(new byte[]{0, 1, (byte) 255, 0, 127}, disk);
    }

    @Test
    void emptyFile() throws Exception {
        Files.write(tempDir.resolve("empty.txt"), new byte[0]);
        assertStdout(cwd(tempDir), """
                public class Main {
                    public static void main(String[] args) {
                        String s = File.readString("empty.txt");
                        byte[] b = File.readBytes("empty.txt");
                        System.out.println(s.length());
                        System.out.println(b.length);
                        System.out.println(File.size("empty.txt"));
                    }
                }
                """, "0\n0\n0\n");
    }

    @Test
    void missingFileException() throws Exception {
        assertStdout(cwd(tempDir), """
                public class Main {
                    public static void main(String[] args) {
                        try {
                            File.readString("does-not-exist.txt");
                            System.out.println(0);
                        } catch (FileNotFoundException e) {
                            System.out.println(1);
                        }
                    }
                }
                """, "1\n");
    }

    @Test
    void existsSizeDelete() throws Exception {
        assertStdout(cwd(tempDir), """
                public class Main {
                    public static void main(String[] args) {
                        File.writeString("temp.txt", "abcd");
                        if (File.exists("temp.txt")) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        System.out.println(File.size("temp.txt"));
                        boolean deleted = File.delete("temp.txt");
                        if (deleted) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        if (File.exists("temp.txt")) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "1\n4\n1\n0\n");
    }

    @Test
    void largeFile1mb() throws Exception {
        assertStdout(cwd(tempDir), """
                public class Main {
                    public static void main(String[] args) {
                        int n = 1048576;
                        byte[] data = new byte[n];
                        int i = 0;
                        while (i < n) {
                            data[i] = i % 127;
                            i = i + 1;
                        }
                        File.writeBytes("big.bin", data);
                        byte[] got = File.readBytes("big.bin");
                        if (got.length != n) {
                            System.out.println(0);
                            return;
                        }
                        if (File.size("big.bin") != 1048576L) {
                            System.out.println(0);
                            return;
                        }
                        int j = 0;
                        int ok = 1;
                        while (j < n) {
                            if (got[j] != j % 127) {
                                ok = 0;
                            }
                            j = j + 1;
                        }
                        System.out.println(ok);
                    }
                }
                """, "1\n");
    }

    @Test
    void gcStressRead() throws Exception {
        Files.writeString(tempDir.resolve("hello.txt"), "Hello", StandardCharsets.UTF_8);
        assertStdout(cwd(tempDir), """
                public class Main {
                    public static void main(String[] args) {
                        int i = 0;
                        while (i < 2000) {
                            String text = File.readString("hello.txt");
                            i = i + 1;
                        }
                        System.out.println(File.readString("hello.txt"));
                    }
                }
                """, "Hello\n", Map.of("JNATIVE_GC_STRESS", "1"));
    }

    @Test
    void exceptionStress() throws Exception {
        assertStdout(cwd(tempDir), """
                public class Main {
                    public static void main(String[] args) {
                        int i = 0;
                        int hits = 0;
                        while (i < 500) {
                            try {
                                File.readString("missing-stress.txt");
                            } catch (FileNotFoundException e) {
                                hits = hits + 1;
                            }
                            i = i + 1;
                        }
                        System.out.println(hits);
                    }
                }
                """, "500\n", Map.of("JNATIVE_GC_STRESS", "1"));
    }

    @Test
    void resourceLeakStress() throws Exception {
        Files.writeString(tempDir.resolve("x.txt"), "x", StandardCharsets.UTF_8);
        assertStdout(cwd(tempDir), """
                public class Main {
                    public static void main(String[] args) {
                        int i = 0;
                        while (i < 10000) {
                            String t = File.readString("x.txt");
                            i = i + 1;
                        }
                        System.out.println(1);
                    }
                }
                """, "1\n");
    }

    @Test
    void argvAndCat() throws Exception {
        Path hello = tempDir.resolve("hello.txt");
        Files.writeString(hello, "from argv", StandardCharsets.UTF_8);
        Path out = compile("""
                public class Cat {
                    public static void main(String[] args) {
                        System.out.println(File.readString(args[0]));
                    }
                }
                """);
        ProcessBuilder pb = new ProcessBuilder(
                out.toAbsolutePath().toString(), hello.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), stdout);
        assertEquals("from argv\n", stdout);
    }

    @Test
    void finalAcceptanceUnicodeFile() throws Exception {
        Files.writeString(tempDir.resolve("hello.txt"),
                "Hello from a real file.\n你好，JNative。", StandardCharsets.UTF_8);
        assertStdout(cwd(tempDir), """
                public class Main {
                    public static void main(String[] args) {
                        String text = File.readString("hello.txt");
                        System.out.println(text);
                    }
                }
                """, "Hello from a real file.\n你好，JNative。\n");
    }

    private static Path cwd(Path dir) {
        return dir;
    }

    private void assertStdout(Path workDir, String source, String expected) throws Exception {
        assertStdout(workDir, source, expected, Map.of());
    }

    private void assertStdout(Path workDir, String source, String expected, Map<String, String> env)
            throws Exception {
        Path out = compile(source);
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.directory(workDir.toFile());
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "timeout");
        assertEquals(0, process.exitValue(), stdout);
        assertEquals(expected, stdout);
    }

    private Path compile(String source) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve("FileIo.java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("FileIoBin");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        return out;
    }
}
