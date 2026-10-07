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

class ByteBufferEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void capacityPositionLimitFlipClear() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        ByteBuffer b = ByteBuffer.allocate(8);
                        System.out.println(b.capacity());
                        System.out.println(b.remaining());
                        b.put(1);
                        b.put(2);
                        System.out.println(b.position());
                        b.flip();
                        System.out.println(b.remaining());
                        System.out.println(b.get());
                        System.out.println(b.get());
                        b.clear();
                        System.out.println(b.remaining());
                    }
                }
                """, "8\n8\n2\n2\n1\n2\n8\n");
    }

    @Test
    void boundsThrowNoCorruption() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        ByteBuffer b = ByteBuffer.allocate(2);
                        b.put(9);
                        b.put(8);
                        int ok = 0;
                        try {
                            b.put(7);
                        } catch (BufferOverflowException e) {
                            ok = ok + 1;
                        }
                        b.flip();
                        b.get();
                        b.get();
                        try {
                            b.get();
                        } catch (BufferUnderflowException e) {
                            ok = ok + 1;
                        }
                        try {
                            b.position(3);
                        } catch (BufferException e) {
                            ok = ok + 1;
                        }
                        System.out.println(ok);
                    }
                }
                """, "3\n");
    }

    @Test
    void sliceAndDuplicate() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        ByteBuffer b = ByteBuffer.allocate(4);
                        b.put(10);
                        b.put(20);
                        b.put(30);
                        b.flip();
                        b.get();
                        ByteBuffer s = b.slice();
                        System.out.println(s.remaining());
                        System.out.println(s.get());
                        ByteBuffer d = b.duplicate();
                        System.out.println(d.position());
                    }
                }
                """, "2\n20\n1\n");
    }

    @Test
    void poolBounded() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        ByteBufferPool pool = new ByteBufferPool(16, 2);
                        ByteBuffer a = pool.acquire();
                        ByteBuffer b = pool.acquire();
                        pool.release(a);
                        pool.release(b);
                        ByteBuffer c = pool.acquire();
                        pool.release(c);
                        System.out.println(pool.pooledCount());
                        System.out.println(pool.allocatedCount());
                    }
                }
                """, "2\n2\n");
    }

    private void assertStdout(String source, String expected) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve("ByteBufferT.java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("ByteBufferBin");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "timeout");
        assertEquals(0, process.exitValue(), stdout);
        assertEquals(expected, stdout);
    }
}
