package io.jnative.opt;

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

class DifferentialOptTest {

    @TempDir
    Path tempDir;

    @Test
    void pointLoopSameResult() throws Exception {
        String source = """
                class Point {
                    int x;
                    int y;
                    Point(int x, int y) { this.x = x; this.y = y; }
                }
                public class Main {
                    static int calculate(int value) {
                        Point p = new Point(value, value + 1);
                        return p.x + p.y;
                    }
                    public static void main(String[] args) {
                        int sum = 0;
                        for (int i = 0; i < 100; i = i + 1) {
                            sum = sum + calculate(i);
                        }
                        System.out.println(sum);
                    }
                }
                """;
        String expected = runNative(source, 0);
        assertEquals(expected, runNative(source, 2));
    }

    @Test
    void exceptionsSame() throws Exception {
        String source = """
                public class Main {
                    public static void main(String[] args) {
                        String s = null;
                        try {
                            System.out.println(s.length());
                        } catch (NullPointerException e) {
                            System.out.println("npe");
                        }
                    }
                }
                """;
        assertEquals(runNative(source, 0), runNative(source, 2));
    }

    private String runNative(String source, int optLevel) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve("T" + optLevel + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("t" + optLevel);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        options.setOptLevel(optLevel);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        return execute(result.executable());
    }

    private static String execute(Path executable) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(executable.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(30, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), new String(bytes, StandardCharsets.UTF_8));
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
