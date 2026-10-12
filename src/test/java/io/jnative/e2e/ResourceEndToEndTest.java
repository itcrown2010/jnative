/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

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

class ResourceEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void basicTwRCloseCalled() throws Exception {
        assertStdout("""
                class Box implements AutoCloseable {
                    int id;
                    Box(int id) { this.id = id; }
                    void close() {
                        System.out.println(this.id);
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        try (Box b = new Box(1)) {
                            System.out.println(0);
                        }
                    }
                }
                """, "0\n1\n");
    }

    @Test
    void exceptionInBodyStillCloses() throws Exception {
        assertStdout("""
                class Boom extends RuntimeException {
                    Boom(String message) { super(message); }
                }
                class Box implements AutoCloseable {
                    void close() {
                        System.out.println(2);
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        try (Box b = new Box()) {
                            throw new Boom("x");
                        } catch (Boom e) {
                            System.out.println(1);
                        }
                    }
                }
                // Close runs before catch (JLS §14.20.3).
                """, "2\n1\n");
    }

    @Test
    void multipleResourcesReverseOrder() throws Exception {
        assertStdout("""
                class Box implements AutoCloseable {
                    int id;
                    Box(int id) { this.id = id; }
                    void close() {
                        System.out.println(this.id);
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        try (Box a = new Box(1); Box b = new Box(2)) {
                            System.out.println(0);
                        }
                    }
                }
                """, "0\n2\n1\n");
    }

    /** Allocation + Exception + GC + TWR cleanup.
 * @author 张晓达
 */
    @Test
    void fourWayIntersection() throws Exception {
        assertStdout("""
                class Boom extends RuntimeException {
                    Boom(String message) { super(message); }
                }
                class Box implements AutoCloseable {
                    int closed;
                    Box() {
                        this.closed = 0;
                        ResourceTracker.trackOpen("Box");
                    }
                    void close() {
                        if (this.closed == 0) {
                            this.closed = 1;
                            ResourceTracker.trackClose("Box");
                            System.out.println(2);
                        }
                    }
                }
                class Hold {
                    int v;
                    Hold(int v) { this.v = v; }
                }
                public class Main {
                    public static void main(String[] args) {
                        try (Box b = new Box()) {
                            Hold x = new Hold(7);
                            System.gc();
                            if (x.v != 7) {
                                System.out.println(9);
                            }
                            throw new Boom("x");
                        } catch (Boom e) {
                            System.out.println(1);
                        }
                        System.out.println(ResourceTracker.openCount());
                    }
                }
                // close before catch
                """, "2\n1\n0\n");
    }

    @Test
    void initFailureClosesPriorResources() throws Exception {
        assertStdout("""
                class Boom extends RuntimeException {
                    Boom(String message) { super(message); }
                }
                class Box implements AutoCloseable {
                    int id;
                    Box(int id) {
                        this.id = id;
                        if (id == 2) { throw new Boom("open2"); }
                    }
                    void close() { System.out.println(this.id); }
                }
                public class Main {
                    public static void main(String[] args) {
                        try (Box a = new Box(1); Box b = new Box(2)) {
                            System.out.println(9);
                        } catch (Boom e) {
                            System.out.println(0);
                        }
                    }
                }
                """, "1\n0\n");
    }

    @Test
    void nullResourceSkipped() throws Exception {
        assertStdout("""
                class Box implements AutoCloseable {
                    void close() { System.out.println(1); }
                }
                public class Main {
                    static Box nil() { return null; }
                    public static void main(String[] args) {
                        try (Box a = nil(); Box b = new Box()) {
                            System.out.println(0);
                        }
                    }
                }
                """, "0\n1\n");
    }

    @Test
    void suppressedOnCloseFailure() throws Exception {
        assertStdout("""
                class Boom extends RuntimeException {
                    Boom(String message) { super(message); }
                }
                class CloseFail implements AutoCloseable {
                    void close() { throw new Boom("close"); }
                }
                public class Main {
                    public static void main(String[] args) {
                        try (CloseFail c = new CloseFail()) {
                            throw new Boom("body");
                        } catch (Boom e) {
                            System.out.println(e.getMessage());
                            Throwable[] s = e.getSuppressed();
                            System.out.println(s.length);
                            System.out.println(s[0].getMessage());
                        }
                    }
                }
                """, "body\n1\nclose\n");
    }

    @Test
    void closeFailureBecomesPrimary() throws Exception {
        assertStdout("""
                class Boom extends RuntimeException {
                    Boom(String message) { super(message); }
                }
                class CloseFail implements AutoCloseable {
                    int id;
                    CloseFail(int id) { this.id = id; }
                    void close() { throw new Boom("c" + this.id); }
                }
                public class Main {
                    public static void main(String[] args) {
                        try (CloseFail a = new CloseFail(1); CloseFail b = new CloseFail(2)) {
                            System.out.println(0);
                        } catch (Boom e) {
                            System.out.println(e.getMessage());
                            Throwable[] s = e.getSuppressed();
                            System.out.println(s.length);
                            System.out.println(s[0].getMessage());
                        }
                    }
                }
                """, "0\nc2\n1\nc1\n");
    }

    @Test
    void twrReturnStillCloses() throws Exception {
        assertStdout("""
                class Box implements AutoCloseable {
                    void close() { System.out.println(2); }
                }
                public class Main {
                    static int go() {
                        try (Box b = new Box()) {
                            return 1;
                        }
                    }
                    public static void main(String[] args) {
                        System.out.println(go());
                    }
                }
                """, "2\n1\n");
    }

    @Test
    void userFinallyAfterCloseAndCatch() throws Exception {
        assertStdout("""
                class Boom extends RuntimeException {
                    Boom(String message) { super(message); }
                }
                class Box implements AutoCloseable {
                    void close() { System.out.println(1); }
                }
                public class Main {
                    public static void main(String[] args) {
                        try (Box b = new Box()) {
                            throw new Boom("x");
                        } catch (Boom e) {
                            System.out.println(2);
                        } finally {
                            System.out.println(3);
                        }
                    }
                }
                """, "1\n2\n3\n");
    }

    private void assertStdout(String source, String expected) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        NativeRun r = run(source, "Res");
        assertTrue(r.compileSuccess, r.diagnostics);
        assertEquals(expected, r.stdout, r.stderr);
        assertEquals(0, r.exitCode);
    }

    private NativeRun run(String source, String name) throws Exception {
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        if (!result.success()) {
            return new NativeRun(false, result.diagnostics().toString(), "", "", 1);
        }
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.redirectErrorStream(false);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(15, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new NativeRun(false, "native process timed out", stdout, stderr, 124);
        }
        return new NativeRun(true, "", stdout, stderr, process.exitValue());
    }

    private record NativeRun(boolean compileSuccess, String diagnostics, String stdout, String stderr, int exitCode) {
    }
}
