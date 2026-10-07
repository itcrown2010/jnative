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

/** Round 1.1 — break / continue native e2e + negative compile. */
class LoopControlEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void breakWhileTest() throws Exception {
        assertEquals("3\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        int i = 0;
                        int n = 0;
                        while (i < 10) {
                            if (i == 3) {
                                break;
                            }
                            n = n + 1;
                            i = i + 1;
                        }
                        System.out.println(n);
                    }
                }
                """, "BreakWhile", 0));
    }

    @Test
    void breakForTest() throws Exception {
        assertEquals("5\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        int n = 0;
                        for (int i = 0; i < 10; i = i + 1) {
                            if (i == 5) {
                                break;
                            }
                            n = n + 1;
                        }
                        System.out.println(n);
                    }
                }
                """, "BreakFor", 0));
    }

    @Test
    void breakNestedLoopTest() throws Exception {
        // Inner break only; 2 increments × 3 outer = 6
        assertEquals("6\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        int outer = 0;
                        int i = 0;
                        while (i < 3) {
                            for (int j = 0; j < 10; j = j + 1) {
                                if (j == 2) {
                                    break;
                                }
                                outer = outer + 1;
                            }
                            i = i + 1;
                        }
                        System.out.println(outer);
                    }
                }
                """, "BreakNest", 0));
    }

    @Test
    void breakInvalidTest() {
        CompileResult result = compileFail("""
                public class Main {
                    public static void main(String[] args) {
                        break;
                    }
                }
                """);
        assertFalse(result.success());
        String d = result.diagnostics().toString();
        assertTrue(d.contains("JN1001") || d.contains("break"), d);
        assertFalse(d.contains("NullPointerException"), d);
        assertFalse(d.contains("AssertionError"), d);
    }

    @Test
    void breakFinallyTest() throws Exception {
        assertEquals("10\n1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        int x = 0;
                        int i = 0;
                        while (i < 5) {
                            try {
                                break;
                            } finally {
                                x = 10;
                            }
                        }
                        System.out.println(x);
                        System.out.println(1);
                    }
                }
                """, "BreakFin", 0));
    }

    @Test
    void continueWhileTest() throws Exception {
        // 1+2+4+5 (skip 3) = 12
        assertEquals("12\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        int i = 0;
                        int sum = 0;
                        while (i < 5) {
                            i = i + 1;
                            if (i == 3) {
                                continue;
                            }
                            sum = sum + i;
                        }
                        System.out.println(sum);
                    }
                }
                """, "ContWhile", 0));
    }

    @Test
    void continueForTest() throws Exception {
        // continue must hit update: sum of 0..9 except 5 = 40
        assertEquals("40\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        int sum = 0;
                        for (int i = 0; i < 10; i = i + 1) {
                            if (i == 5) {
                                continue;
                            }
                            sum = sum + i;
                        }
                        System.out.println(sum);
                    }
                }
                """, "ContFor", 0));
    }

    @Test
    void continueNestedLoopTest() throws Exception {
        assertEquals("9\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        int n = 0;
                        int i = 0;
                        while (i < 3) {
                            for (int j = 0; j < 4; j = j + 1) {
                                if (j == 1) {
                                    continue;
                                }
                                n = n + 1;
                            }
                            i = i + 1;
                        }
                        System.out.println(n);
                    }
                }
                """, "ContNest", 0));
    }

    @Test
    void continueInvalidTest() {
        CompileResult result = compileFail("""
                public class Main {
                    public static void main(String[] args) {
                        continue;
                    }
                }
                """);
        assertFalse(result.success());
        String d = result.diagnostics().toString();
        assertTrue(d.contains("JN1002") || d.contains("continue"), d);
        assertFalse(d.contains("NullPointerException"), d);
    }

    @Test
    void continueFinallyTest() throws Exception {
        // finally always; sum = 0+2 (skip i==1)
        assertEquals("3\n2\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        int hits = 0;
                        int sum = 0;
                        for (int i = 0; i < 3; i = i + 1) {
                            try {
                                if (i == 1) {
                                    continue;
                                }
                                sum = sum + i;
                            } finally {
                                hits = hits + 1;
                            }
                        }
                        System.out.println(hits);
                        System.out.println(sum);
                    }
                }
                """, "ContFin", 0));
    }

    @Test
    void loopControlNativeE2E_O0() throws Exception {
        assertEquals("JNATIVE_LOOP_CONTROL_PASS\n", run(loopControlProgram(), "LoopCtl0", 0));
    }

    @Test
    void loopControlNativeE2E_O2() throws Exception {
        assertEquals("JNATIVE_LOOP_CONTROL_PASS\n", run(loopControlProgram(), "LoopCtl2", 2));
    }

    @Test
    void loopControlNativeE2E_O3() throws Exception {
        assertEquals("JNATIVE_LOOP_CONTROL_PASS\n", run(loopControlProgram(), "LoopCtl3", 3));
    }

    private static String loopControlProgram() {
        return """
                public class Main {
                    public static void main(String[] args) {
                        int sum = 0;
                        for (int i = 0; i < 20; i = i + 1) {
                            if (i == 15) {
                                break;
                            }
                            if (i == 5) {
                                continue;
                            }
                            sum = sum + i;
                        }
                        int w = 0;
                        int k = 0;
                        while (k < 10) {
                            k = k + 1;
                            if (k == 7) {
                                break;
                            }
                            w = w + 1;
                        }
                        // 0..14 except 5 = 100; while breaks at k==7 after 6 iterations
                        if (sum == 100) {
                            if (w == 6) {
                                System.out.println("JNATIVE_LOOP_CONTROL_PASS");
                            }
                        }
                    }
                }
                """;
    }

    private CompileResult compileFail(String source) {
        try {
            Path src = tempDir.resolve("Fail" + System.nanoTime() + ".java");
            Files.writeString(src, source);
            CompilerOptions options = new CompilerOptions();
            options.setSourceFile(src);
            return new JNativeCompiler().compile(options);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String run(String source, String name, int opt) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setOptLevel(opt);
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "timeout");
        String output = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
