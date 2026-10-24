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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Platform pieces added for JNative Studio: non-blocking child-process reads, anti-aliased drawing,
 * transparent canvases / clipped panels, and the Studio tool itself (document model + code generator).
 *
 * @author 张晓达
 */
class StudioPlatformEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void nonBlockingProcessReadStreamsOutput() throws Exception {
        String out = run("""
                import jnative.io.*;
                public class Main {
                    public static void main(String[] args) {
                        ProcessBuilder pb = new ProcessBuilder("/bin/sh");
                        pb.argument("-c");
                        pb.argument("printf one; sleep 0.3; printf two; exit 3");
                        Process p = pb.start();
                        String all = "";
                        int polls = 0;
                        int sawEmpty = 0;
                        int code = -1000;
                        while (code == -1000 && polls < 400) {
                            String s = p.readStdoutNb(256, 20);
                            if (s != null && s.length() == 0) {
                                sawEmpty = sawEmpty + 1;
                            }
                            if (s != null) {
                                all = all + s;
                            }
                            code = p.pollExit();
                            polls = polls + 1;
                        }
                        String rest = p.readStdoutNb(256, 0);
                        if (rest != null) {
                            all = all + rest;
                        }
                        System.out.println(all);
                        System.out.println(code);
                        System.out.println(sawEmpty > 0 ? "polled" : "blocked");
                    }
                }
                """, "NbProc", Map.of());
        assertEquals("onetwo\n3\npolled\n", out);
    }

    @Test
    void antialiasedShapesBlendEdges() throws Exception {
        String out = run("""
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        Image image = Image.create(40, 40);
                        Graphics g = image.beginGraphics();
                        g.setColor(Color.WHITE);
                        g.fillRect(0, 0, 40, 40);
                        g.setAntialias(true);
                        g.setColor(Color.BLACK);
                        g.fillEllipse(4, 4, 31, 31);
                        g.end();
                        int centre = image.getPixel(20, 20);
                        int corner = image.getPixel(1, 1);
                        int edge = image.getPixel(8, 8);
                        System.out.println(GfxNative.unpackR(centre));
                        System.out.println(GfxNative.unpackR(corner));
                        int er = GfxNative.unpackR(edge);
                        System.out.println(er > 0 && er < 255 ? "blended" : "hard:" + er);
                        Font f = Font.system(12);
                        Image tmp = Image.create(4, 4);
                        Graphics m = tmp.beginGraphics();
                        Size sz = m.measureText("abc", f);
                        System.out.println(sz.width > 0 ? "measured" : "empty");
                        m.end();
                        image.dispose();
                    }
                }
                """, "AaShapes", Map.of());
        assertEquals("0\n255\nblended\nmeasured\n", out);
    }

    @Test
    void transparentCanvasAndClippedPanelHeadless() throws Exception {
        String out = run("""
                import jnative.ui.*;
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("Glass", 300, 200);
                        Panel host = new Panel();
                        host.setClipChildren(true, true);
                        Button b = new Button("under");
                        host.add(b);
                        b.setBounds(10, 10, 100, 30);
                        Canvas glass = new Canvas();
                        glass.setTransparent(true);
                        glass.setHiDpi(true);
                        host.add(glass);
                        glass.setBounds(0, 0, 300, 200);
                        window.setContent(host);
                        window.show();
                        System.out.println("ok " + host.childCount);
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "GlassPanel", Map.of());
        assertEquals("ok 2\n", out);
    }

    @Test
    void studioToolBuildsAndRoundTripsDocuments() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path src = Path.of("tools", "jnative-studio").toAbsolutePath();
        Assumptions.assumeTrue(Files.isDirectory(src.resolve("src")), "tools/jnative-studio not present");
        Path proj = tempDir.resolve("studio");
        copyTree(src.resolve("src"), proj.resolve("src"));
        Files.copy(src.resolve("jnative.project"), tempDir.resolve("studio").resolve("jnative.project"));

        ProcessBuilder build = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", System.getProperty("java.class.path"), "io.jnative.Main", "build", "--project", ".");
        build.directory(proj.toFile());
        build.redirectErrorStream(true);
        Process bp = build.start();
        String buildOut = new String(bp.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(bp.waitFor(300, TimeUnit.SECONDS), "studio build timeout");
        assertEquals(0, bp.exitValue(), buildOut);

        ProcessBuilder run = new ProcessBuilder(proj.resolve(".jnative/build/jnative-studio").toString(), "--gen");
        run.directory(proj.toFile());
        run.redirectErrorStream(true);
        Process rp = run.start();
        String runOut = new String(rp.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(rp.waitFor(60, TimeUnit.SECONDS), "studio run timeout");
        assertEquals(0, rp.exitValue(), runOut);
        assertTrue(runOut.contains("ROUNDTRIP OK"), runOut);
        assertTrue(runOut.contains("public class DemoFormBase"), runOut);
        assertTrue(runOut.contains("void buildUi()"), runOut);
    }

    private static void copyTree(Path from, Path to) throws IOException {
        try (Stream<Path> s = Files.walk(from)) {
            for (Path p : (Iterable<Path>) s::iterator) {
                Path dest = to.resolve(from.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest);
                }
            }
        }
    }

    private String run(String source, String name, Map<String, String> env) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setOptLevel(0);
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        options.setHeadless(true);
        options.setUiBackend("headless");
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Map<String, String> e = new HashMap<>(env);
        e.put("JNATIVE_HEADLESS", "1");
        pb.environment().putAll(e);
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "timeout");
        String output = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
