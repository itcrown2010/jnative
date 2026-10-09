package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.cli.ProjectCli;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Capability-integration: multipackage order-processing example.
 * Compares JNative -O0 vs default opt; Java-compatible subset separately.
 * @author 张晓达
 */
class OrderProcessingEndToEndTest {

    private static final String EXPECT_OK = """
            1
            3500
            200
            1
            1
            7
            34
            1
            10
            42
            """;

    private static final String EXPECT_BAD = """
            0
            1
            42
            """;

    @TempDir
    Path tmp;

    @Test
    void orderProjectO0MatchesOpt() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path root = Path.of("examples/order-processing/app").toAbsolutePath().normalize();
        Assumptions.assumeTrue(Files.isDirectory(root), "order-processing app missing");
        Path registry = Path.of("examples/order-processing/registry").toAbsolutePath().normalize();

        ProjectCli.BuildOutcome o0 = ProjectCli.buildQuiet(
                root, false, 0, false, false, List.of(registry), false);
        assertTrue(o0.success(), () -> String.join("\n", o0.diagnosticTexts()));
        Path binO0 = Files.copy(o0.primaryArtifact(), tmp.resolve("order-O0"));

        ProjectCli.BuildOutcome opt = ProjectCli.buildQuiet(
                root, false, 1, false, false, List.of(registry), false);
        assertTrue(opt.success(), () -> String.join("\n", opt.diagnosticTexts()));
        Path binOpt = opt.primaryArtifact();

        assertEquals(EXPECT_OK, run(binO0, root, List.of()));
        assertEquals(EXPECT_OK, run(binOpt, root, List.of()));
        assertEquals(EXPECT_BAD, run(binO0, root, List.of("bad")));
        assertEquals(EXPECT_BAD, run(binOpt, root, List.of("bad")));

        String stressA = run(binOpt, root, List.of("stress50"));
        String stressB = run(binOpt, root, List.of("stress50"));
        assertEquals(stressA, stressB, "fixed-seed stress must be deterministic");
        String[] lines = stressA.trim().split("\\R");
        assertEquals(2, lines.length);
        int size = Integer.parseInt(lines[1].trim());
        assertTrue(size < 50, "stress size after removals should be < N, got " + size);
    }

    @Test
    void javaCompatibleInteractionSubset() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        // Portable subset: enum/switch, boxing overload prefer, generic identity,
        // Unicode length/indexOf, finally marker — no File / jnative-only APIs.
        // Top-level enum (nested enum types are not in the supported surface for this subset).
        String javaSrc = """
                enum OrderStatus { DRAFT, APPROVED, POSTED, CANCELLED }
                public class Main {
                    public static void main(String[] args) {
                        OrderStatus s = OrderStatus.DRAFT;
                        s = transition(s, OrderStatus.APPROVED);
                        s = transition(s, OrderStatus.POSTED);
                        System.out.println(code(s));
                        System.out.println(prefer(3));
                        Integer boxed = identity(Integer.valueOf(7));
                        System.out.println(boxed.intValue());
                        String note = "订单#1001 🍎";
                        System.out.println(note.length());
                        System.out.println(note.indexOf("🍎") >= 0 ? 1 : 0);
                        int flag = 0;
                        try {
                            System.out.println(1);
                        } finally {
                            flag = Integer.valueOf(42).intValue();
                            System.out.println(flag);
                        }
                    }
                    static int prefer(int x) { return 1; }
                    static int prefer(Integer x) { return 2; }
                    static <T> T identity(T v) { return v; }
                    static int code(OrderStatus s) {
                        switch (s) {
                            case DRAFT: return 0;
                            case APPROVED: return 100;
                            case POSTED: return 200;
                            case CANCELLED: return 900;
                            default: return -1;
                        }
                    }
                    static OrderStatus transition(OrderStatus from, OrderStatus to) {
                        switch (from) {
                            case DRAFT:
                                if (to == OrderStatus.APPROVED) return to;
                                break;
                            case APPROVED:
                                if (to == OrderStatus.POSTED) return to;
                                break;
                            default:
                                break;
                        }
                        throw new RuntimeException("bad");
                    }
                }
                """;
        String expected = """
                200
                1
                7
                10
                1
                1
                42
                """;
        Path jdir = tmp.resolve("java");
        Files.createDirectories(jdir);
        Files.writeString(jdir.resolve("Main.java"), javaSrc, StandardCharsets.UTF_8);
        Process jc = new ProcessBuilder("javac", "Main.java")
                .directory(jdir.toFile()).redirectErrorStream(true).start();
        assertTrue(jc.waitFor(60, TimeUnit.SECONDS));
        assertEquals(0, jc.exitValue(), new String(jc.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
        Process jr = new ProcessBuilder("java", "-cp", ".", "Main")
                .directory(jdir.toFile()).redirectErrorStream(true).start();
        assertTrue(jr.waitFor(60, TimeUnit.SECONDS));
        String javaOut = new String(jr.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, jr.exitValue(), javaOut);
        assertEquals(expected, javaOut, "Java reference");

        String jnSrc = javaSrc; // identical for this subset
        assertEquals(expected, compileRun(tmp.resolve("jn0"), jnSrc, 0), "JNative O0");
        assertEquals(expected, compileRun(tmp.resolve("jn1"), jnSrc, 1), "JNative O1");
    }

    @Test
    void fileInputStreamUtf8RoundTrip() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        // Minimal repro for StreamNative.getU8 signed-division corruption (P0).
        String src = """
                public class Main {
                    public static void main(String[] args) {
                        String path = "emoji.txt";
                        String s = "订单#1001 🍎";
                        File.writeString(path, s);
                        byte[] expected = s.getBytes();
                        byte[] buf = new byte[64];
                        int n = 0;
                        try (FileInputStream in = new FileInputStream(path)) {
                            int r = in.read(buf, 0, buf.length);
                            if (r > 0) { n = r; }
                        }
                        int mismatch = 0;
                        int i = 0;
                        while (i < n && i < expected.length) {
                            int a = (int) expected[i] & 255;
                            int b = (int) buf[i] & 255;
                            if (a != b) { mismatch = mismatch + 1; }
                            i = i + 1;
                        }
                        byte[] exact = new byte[n];
                        i = 0;
                        while (i < n) { exact[i] = buf[i]; i = i + 1; }
                        String back = String.fromBytes(exact);
                        System.out.println(mismatch);
                        System.out.println(back.indexOf("🍎") >= 0 ? 1 : 0);
                        System.out.println(n == expected.length ? 1 : 0);
                    }
                }
                """;
        assertEquals("0\n1\n1\n", compileRun(tmp.resolve("utf8fis"), src, 1));
        assertEquals("0\n1\n1\n", compileRun(tmp.resolve("utf8fis0"), src, 0));
    }

    private static String run(Path bin, Path cwd, List<String> args) throws Exception {
        ProcessBuilder pb = new ProcessBuilder();
        pb.command().add(bin.toAbsolutePath().toString());
        pb.command().addAll(args);
        pb.directory(cwd.toFile());
        pb.redirectErrorStream(true);
        Process p = pb.start();
        boolean ok = p.waitFor(60, TimeUnit.SECONDS);
        if (!ok) {
            p.destroyForcibly();
            fail("timeout running " + bin + " " + args);
        }
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), out);
        return out;
    }

    private static String compileRun(Path out, String src, int opt) throws Exception {
        Path file = out.getParent().resolve(out.getFileName() + ".java");
        Files.writeString(file, src, StandardCharsets.UTF_8);
        io.jnative.compiler.CompilerOptions options = new io.jnative.compiler.CompilerOptions();
        options.setSourceFile(file);
        options.setProgramSources(io.jnative.compiler.ProgramSources.of(
                io.jnative.compiler.SourceUnit.ofPath(file, src)));
        options.setOutputFile(out);
        options.setOptLevel(opt);
        var cr = new io.jnative.compiler.JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().stream().map(Object::toString)
                .reduce("", (a, b) -> a + "\n" + b));
        Process p = new ProcessBuilder(cr.executable().toAbsolutePath().toString())
                .directory(out.getParent().toFile())
                .redirectErrorStream(true)
                .start();
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), stdout);
        return stdout;
    }
}
