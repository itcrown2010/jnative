package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class NativeCallbackEndToEndTest {

    @TempDir
    Path tempDir;

    private static Path libDir;
    private static boolean fixtureReady;

    @BeforeAll
    static void buildCFixtures() throws Exception {
        OptionalToolchain toolchain = Toolchain.detect()
                .map(t -> new OptionalToolchain(t.clang().toString()))
                .orElse(null);
        Assumptions.assumeTrue(toolchain != null, "clang not available");

        libDir = Files.createTempDirectory("jnative-cbtest-lib");
        String os = System.getProperty("os.name", "").toLowerCase();
        Path lib = os.contains("mac") || os.contains("darwin")
                ? libDir.resolve("libjnative_cbtest.dylib")
                : libDir.resolve("libjnative_cbtest.so");

        String[] sources = {
                "callback_invoke.c",
                "callback_foreign_thread.c",
                "callback_multi_thread.c"
        };
        Path[] objs = new Path[sources.length];
        for (int i = 0; i < sources.length; i++) {
            Path src = Path.of("src/test/resources/native_fixtures/" + sources[i]).toAbsolutePath();
            Assumptions.assumeTrue(Files.isRegularFile(src), sources[i] + " missing");
            objs[i] = libDir.resolve(sources[i].replace(".c", ".o"));
            ProcessBuilder compile = new ProcessBuilder(
                    toolchain.clang, "-c", src.toString(), "-o", objs[i].toString());
            compile.redirectErrorStream(true);
            Process cp = compile.start();
            String cout = new String(cp.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            Assumptions.assumeTrue(cp.waitFor(30, TimeUnit.SECONDS) && cp.exitValue() == 0, cout);
        }

        ProcessBuilder link = new ProcessBuilder(toolchain.clang, "-shared");
        for (Path obj : objs) {
            link.command().add(obj.toString());
        }
        link.command().add("-o");
        link.command().add(lib.toString());
        link.command().add("-lpthread");
        link.redirectErrorStream(true);
        Process lp = link.start();
        String lout = new String(lp.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        fixtureReady = lp.waitFor(30, TimeUnit.SECONDS) && lp.exitValue() == 0;
        Assumptions.assumeTrue(fixtureReady, lout);
    }

    @Test
    void customCallback() throws Exception {
        assertStdout("""
                class CLib {
                    @Native(symbol = "invoke_callback", library = "jnative_cbtest")
                    static native int invokeCallback(NativePtr cb, int value);
                }
                public class Main {
                    public static void main(String[] args) {
                        NativeCallbackHandle cb = Native.callback(x -> x * 2);
                        int result = CLib.invokeCallback(cb.pointer(), 21);
                        cb.close();
                        System.out.println(result);
                    }
                }
                """, "42\n");
    }

    @Test
    void foreignThreadCallback() throws Exception {
        assertStdout("""
                class Person {
                    int value;
                }
                class CLib {
                    @Native(symbol = "invoke_callback_on_thread", library = "jnative_cbtest")
                    static native int invokeCallbackOnThread(NativePtr cb, int value);
                }
                public class Main {
                    public static void main(String[] args) {
                        NativeCallbackHandle cb = Native.callback(x -> {
                            Person p = new Person();
                            p.value = x;
                            System.gc();
                            return x * 2;
                        });
                        int result = CLib.invokeCallbackOnThread(cb.pointer(), 21);
                        cb.close();
                        System.out.println(result);
                    }
                }
                """, "42\n");
    }

    @Test
    void callbackExceptionBoundary() throws Exception {
        assertStdout("""
                class CLib {
                    @Native(symbol = "invoke_callback", library = "jnative_cbtest")
                    static native int invokeCallback(NativePtr cb, int value);
                }
                public class Main {
                    public static void main(String[] args) {
                        NativeCallbackHandle cb = Native.callback(x -> {
                            throw new RuntimeException("boom");
                        });
                        int result = CLib.invokeCallback(cb.pointer(), 1);
                        cb.close();
                        System.out.println(result);
                    }
                }
                """, "0\n");
    }

    @Test
    void qsortCallback() throws Exception {
        assertStdout("""
                @NativeStruct
                class Int5 {
                    int e0;
                    int e1;
                    int e2;
                    int e3;
                    int e4;
                }
                class LibC {
                    @Native(symbol = "qsort")
                    static native void qsort(NativePtr base, long n, long size, NativePtr compar);
                }
                public class Main {
                    public static void main(String[] args) {
                        @Stack
                        Int5 data = new Int5();
                        data.e0 = 5;
                        data.e1 = 1;
                        data.e2 = 4;
                        data.e3 = 2;
                        data.e4 = 3;
                        IntCompare cmp = (a, b) -> {
                            int x = Native.loadInt(a);
                            int y = Native.loadInt(b);
                            return x - y;
                        };
                        NativeCallbackHandle cb = Native.callback(cmp);
                        NativePtr base = Native.addressOf(data);
                        LibC.qsort(base, 5, 4, cb.pointer());
                        cb.close();
                        System.out.println(data.e0);
                        System.out.println(data.e1);
                        System.out.println(data.e2);
                        System.out.println(data.e3);
                        System.out.println(data.e4);
                    }
                }
                """, "1\n2\n3\n4\n5\n");
    }

    private void assertStdout(String source, String expected) throws Exception {
        Assumptions.assumeTrue(fixtureReady);
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve("Cb.java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("Cb");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        options.addLibraryPath(libDir);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());

        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("mac") || os.contains("darwin")) {
            pb.environment().put("DYLD_LIBRARY_PATH", libDir.toString());
        } else {
            String prev = pb.environment().getOrDefault("LD_LIBRARY_PATH", "");
            pb.environment().put("LD_LIBRARY_PATH",
                    prev.isBlank() ? libDir.toString() : libDir + ":" + prev);
        }
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(30, TimeUnit.SECONDS), "timeout");
        assertEquals(0, process.exitValue(), stdout);
        assertEquals(expected, stdout);
    }

    private record OptionalToolchain(String clang) {}
}
