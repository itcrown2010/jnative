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

/** Core Round 1 — Math stdlib native e2e (no primitive casts). */
class MathEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void absMinMaxIntLong() throws Exception {
        assertEquals("5\n2\n9\n7\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(Math.abs(0 - 5));
                        System.out.println(Math.min(2, 9));
                        System.out.println(Math.max(2, 9));
                        System.out.println(Math.abs(0L - 7L));
                    }
                }
                """, "MathAbs"));
    }

    @Test
    void sqrtSinRound() throws Exception {
        assertEquals("1\n1\n4\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        double z = Math.sqrt(9.0);
                        if (z > 2.9) {
                            if (z < 3.1) {
                                System.out.println(1);
                            } else {
                                System.out.println(0);
                            }
                        } else {
                            System.out.println(0);
                        }
                        double s = Math.sin(0.0);
                        if (s > 0.0 - 0.0001) {
                            if (s < 0.0001) {
                                System.out.println(1);
                            } else {
                                System.out.println(0);
                            }
                        } else {
                            System.out.println(0);
                        }
                        System.out.println(Math.round(3.6));
                    }
                }
                """, "MathSqrt"));
    }

    @Test
    void nanInfinityFinite() throws Exception {
        assertEquals("1\n1\n1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        double nan = 0.0 / 0.0;
                        double inf = 1.0 / 0.0;
                        if (Math.isNaN(nan)) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        if (Math.isInfinite(inf)) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        if (Math.isFinite(1.5)) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "MathNan"));
    }

    @Test
    void hypotPowFloorCeil() throws Exception {
        assertEquals("1\n1\n1\n1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        double h = Math.hypot(3.0, 4.0);
                        if (h > 4.9) {
                            if (h < 5.1) {
                                System.out.println(1);
                            } else {
                                System.out.println(0);
                            }
                        } else {
                            System.out.println(0);
                        }
                        double p = Math.pow(2.0, 3.0);
                        if (p > 7.9) {
                            if (p < 8.1) {
                                System.out.println(1);
                            } else {
                                System.out.println(0);
                            }
                        } else {
                            System.out.println(0);
                        }
                        double f = Math.floor(3.7);
                        if (f > 2.9) {
                            if (f < 3.1) {
                                System.out.println(1);
                            } else {
                                System.out.println(0);
                            }
                        } else {
                            System.out.println(0);
                        }
                        double c = Math.ceil(3.2);
                        if (c > 3.9) {
                            if (c < 4.1) {
                                System.out.println(1);
                            } else {
                                System.out.println(0);
                            }
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "MathHyp"));
    }

    @Test
    void constantsPiE() throws Exception {
        assertEquals("1\n1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        if (Math.PI > 3.14) {
                            if (Math.PI < 3.15) {
                                System.out.println(1);
                            } else {
                                System.out.println(0);
                            }
                        } else {
                            System.out.println(0);
                        }
                        if (Math.E > 2.71) {
                            if (Math.E < 2.72) {
                                System.out.println(1);
                            } else {
                                System.out.println(0);
                            }
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "MathConst"));
    }

    @Test
    void overloadIntVsLongVsDouble() throws Exception {
        assertEquals("3\n3\n1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(Math.abs(0 - 3));
                        System.out.println(Math.abs(0L - 3L));
                        double d = Math.abs(0.0 - 1.5);
                        if (d > 1.4) {
                            if (d < 1.6) {
                                System.out.println(1);
                            } else {
                                System.out.println(0);
                            }
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "MathOv"));
    }

    private String run(String source, String name) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setOptLevel(0);
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
