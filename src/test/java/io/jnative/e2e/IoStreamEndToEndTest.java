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

/**
 * @author 张晓达
 */
class IoStreamEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void eofIsMinusOne() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        byte[] data = new byte[2];
                        data[0] = 65;
                        data[1] = 66;
                        ByteArrayInputStream in = new ByteArrayInputStream(data);
                        System.out.println(in.read());
                        System.out.println(in.read());
                        System.out.println(in.read());
                    }
                }
                """, "65\n66\n-1\n");
    }

    @Test
    void bufferedBoundaryAndTwR() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        ResourceTracker.reset();
                        byte[] data = new byte[5];
                        data[0] = 1;
                        data[1] = 2;
                        data[2] = 3;
                        data[3] = 4;
                        data[4] = 5;
                        try (ByteArrayInputStream raw = new ByteArrayInputStream(data);
                             BufferedInputStream in = new BufferedInputStream(raw, 2)) {
                            byte[] got = new byte[5];
                            int n = in.read(got, 0, 5);
                            System.out.println(n);
                            System.out.println(got[0] + got[4]);
                        }
                        System.out.println(ResourceTracker.openCount());
                    }
                }
                """, "5\n6\n0\n");
    }

    @Test
    void fileStreamRoundtripPartialFriendly() throws Exception {
        assertStdout(tempDir, """
                public class Main {
                    public static void main(String[] args) {
                        ResourceTracker.reset();
                        byte[] data = new byte[3];
                        data[0] = 0;
                        data[1] = 127;
                        data[2] = -1;
                        try (FileOutputStream out = new FileOutputStream("s.bin")) {
                            out.write(data);
                            out.flush();
                        }
                        try (FileInputStream in = new FileInputStream("s.bin")) {
                            byte[] got = new byte[8];
                            int n = in.read(got, 0, 8);
                            System.out.println(n);
                            int ok = 1;
                            if (got[0] != 0) { ok = 0; }
                            if (got[1] != 127) { ok = 0; }
                            if (got[2] != -1) { ok = 0; }
                            System.out.println(ok);
                            System.out.println(in.read());
                        }
                        System.out.println(ResourceTracker.files());
                    }
                }
                """, "3\n1\n-1\n0\n");
    }

    @Test
    void utf8ReaderWriter() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        OutputStreamWriter w = new OutputStreamWriter(bos);
                        w.write("Hi");
                        w.close();
                        byte[] utf8 = bos.toByteArray();
                        System.out.println(utf8.length);
                        ByteArrayInputStream bis = new ByteArrayInputStream(utf8);
                        InputStreamReader r = new InputStreamReader(bis);
                        char[] buf = new char[8];
                        int n = r.read(buf, 0, 8);
                        System.out.println(n);
                        int c0 = buf[0];
                        int c1 = buf[1];
                        System.out.println(c0);
                        System.out.println(c1);
                        /* CJK via Bytes UTF-8 roundtrip (File/String path) */
                        byte[] cn = Bytes.fromString("你好");
                        System.out.println(cn.length);
                        System.out.println(Bytes.toString(cn));
                    }
                }
                """, "2\n2\n72\n105\n6\n你好\n");
    }

    private void assertStdout(String source, String expected) throws Exception {
        assertStdout(tempDir, source, expected);
    }

    private void assertStdout(Path workDir, String source, String expected) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve("IoStreamT.java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("IoStreamBin");
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
