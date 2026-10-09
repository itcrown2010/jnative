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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** JNative 0.19 Graphics — headless e2e.
 * @author 张晓达
 */
class GraphicsEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void headlessFillRectPixel() throws Exception {
        Path png = tempDir.resolve("blue.png");
        String out = run("""
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        Image image = Image.create(100, 80);
                        Graphics g = image.beginGraphics();
                        g.setColor(Color.WHITE);
                        g.fillRect(0, 0, 100, 80);
                        g.setColor(Color.BLUE);
                        g.fillRect(10, 10, 40, 30);
                        g.end();
                        image.save("%s");
                        int px = image.getPixel(20, 20);
                        System.out.println(GfxNative.unpackR(px));
                        System.out.println(GfxNative.unpackG(px));
                        System.out.println(GfxNative.unpackB(px));
                        System.out.println(GfxNative.unpackA(px));
                        image.dispose();
                    }
                }
                """.formatted(png.toAbsolutePath().toString().replace("\\", "\\\\")),
                "GfxFill", Map.of("JNATIVE_HEADLESS", "1"), 0);
        assertEquals("0\n0\n255\n255\n", out);
        assertTrue(Files.size(png) > 50);
        byte[] sig = Files.readAllBytes(png);
        assertEquals((byte) 137, sig[0]);
        assertEquals((byte) 'P', sig[1]);
    }

    @Test
    void ephemeralGraphicsInvalidAfterEnd() throws Exception {
        assertEquals("ok\n", run("""
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        Image image = Image.create(40, 40);
                        Graphics g = image.beginGraphics();
                        g.end();
                        int ok = 0;
                        try {
                            g.fillRect(0, 0, 1, 1);
                        } catch (InvalidGraphicsStateException e) {
                            ok = 1;
                        }
                        if (ok == 1) {
                            System.out.println("ok");
                        } else {
                            System.out.println("fail");
                        }
                        image.dispose();
                    }
                }
                """, "GfxEphemeral", Map.of(), 0));
    }

    @Test
    void pathClipSaveRestore() throws Exception {
        assertEquals("1\n", run("""
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        Image image = Image.create(80, 80);
                        Graphics g = image.beginGraphics();
                        g.setColor(Color.WHITE);
                        g.fillRect(0, 0, 80, 80);
                        g.save();
                        g.setClip(0, 0, 20, 20);
                        g.setColor(Color.RED);
                        g.fillRect(0, 0, 80, 80);
                        g.restore();
                        g.setColor(Color.GREEN);
                        Path p = new Path();
                        p.moveTo(40, 40);
                        p.lineTo(70, 40);
                        p.lineTo(55, 70);
                        p.close();
                        g.fillPath(p);
                        g.end();
                        int a = image.getPixel(5, 5);
                        int b = image.getPixel(50, 50);
                        int ok = 0;
                        if (GfxNative.unpackR(a) == 255 && GfxNative.unpackG(b) == 255) {
                            ok = 1;
                        }
                        System.out.println(ok);
                        p.dispose();
                        image.dispose();
                    }
                }
                """, "GfxPathClip", Map.of(), 0));
    }

    @Test
    void measureTextAndUnicode() throws Exception {
        assertEquals("1\n", run("""
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        Image image = Image.create(200, 80);
                        Graphics g = image.beginGraphics();
                        Font f = new Font("Arial", 18);
                        Size s = g.measureText("Hello 中文", f);
                        g.setColor(Color.BLACK);
                        g.drawText("Hello 中文 日本語", 4, 20);
                        g.end();
                        int ok = 0;
                        if (s.getWidth() > 0 && s.getHeight() > 0) {
                            ok = 1;
                        }
                        System.out.println(ok);
                        image.dispose();
                    }
                }
                """, "GfxText", Map.of(), 0));
    }

    @Test
    void pngRoundTripLoad() throws Exception {
        Path png = tempDir.resolve("rt.png");
        String path = png.toAbsolutePath().toString().replace("\\", "\\\\");
        assertEquals("1\n", run("""
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        Image a = Image.create(32, 32);
                        Graphics g = a.beginGraphics();
                        g.setColor(Color.RED);
                        g.fillRect(0, 0, 32, 32);
                        g.end();
                        a.save("%s");
                        a.dispose();
                        Image b = Image.load("%s");
                        int px = b.getPixel(8, 8);
                        int ok = 0;
                        if (GfxNative.unpackR(px) == 255 && b.getWidth() == 32) {
                            ok = 1;
                        }
                        System.out.println(ok);
                        b.dispose();
                    }
                }
                """.formatted(path, path), "GfxPngRt", Map.of(), 0));
    }

    @Test
    void disposedImageThrows() throws Exception {
        assertEquals("ok\n", run("""
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        Image image = Image.create(16, 16);
                        image.dispose();
                        int ok = 0;
                        try {
                            image.getWidth();
                        } catch (DisposedGraphicsResourceException e) {
                            ok = 1;
                        }
                        if (ok == 1) {
                            System.out.println("ok");
                        } else {
                            System.out.println("fail");
                        }
                    }
                }
                """, "GfxDisposed", Map.of(), 0));
    }

    @Test
    void canvasPaintAndMouseHeadless() throws Exception {
        assertEquals("2\n", run("""
                import jnative.ui.*;
                import jnative.graphics.*;
                import jnative.collections.*;
                class Holder {
                    static ArrayList<Point> points;
                    static Canvas canvas;
                }
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("Gfx", 200, 150);
                        Holder.canvas = new Canvas();
                        Holder.points = new ArrayList<Point>();
                        Holder.canvas.onMouseDrag(event -> {
                            Holder.points.add(new Point(event.getX(), event.getY()));
                            Holder.canvas.repaint();
                        });
                        Holder.canvas.onPaint(g -> {
                            g.setColor(Color.BLUE);
                            int i = 1;
                            while (i < Holder.points.size()) {
                                g.drawLine(
                                        Holder.points.get(i - 1).x,
                                        Holder.points.get(i - 1).y,
                                        Holder.points.get(i).x,
                                        Holder.points.get(i).y);
                                i = i + 1;
                            }
                        });
                        window.setContent(Holder.canvas);
                        window.show();
                        UiTest.mouseDown(Holder.canvas, 10, 10);
                        UiTest.mouseMove(Holder.canvas, 20, 20);
                        UiTest.mouseMove(Holder.canvas, 30, 25);
                        UiTest.mouseUp(Holder.canvas, 30, 25);
                        UiTest.paint(Holder.canvas);
                        System.out.println(Holder.points.size());
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "GfxCanvas", Map.of("JNATIVE_HEADLESS", "1"), 0));
    }

    @Test
    void uiTimerFires() throws Exception {
        assertEquals("1\n", run("""
                import jnative.ui.*;
                import jnative.graphics.*;
                class Holder {
                    static int ticks;
                    static UiTimer timer;
                }
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 100, 80);
                        window.show();
                        Holder.ticks = 0;
                        Holder.timer = new UiTimer(1000, () -> {
                            Holder.ticks = Holder.ticks + 1;
                        });
                        UiTest.timerFire(Holder.timer);
                        System.out.println(Holder.ticks);
                        Holder.timer.stop();
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "GfxTimer", Map.of("JNATIVE_HEADLESS", "1"), 0));
    }

    @Test
    void transformTranslate() throws Exception {
        assertEquals("1\n", run("""
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        Image image = Image.create(60, 60);
                        Graphics g = image.beginGraphics();
                        g.setColor(Color.WHITE);
                        g.fillRect(0, 0, 60, 60);
                        g.translate(20, 20);
                        g.setColor(Color.RED);
                        g.fillRect(0, 0, 10, 10);
                        g.end();
                        int px = image.getPixel(25, 25);
                        int ok = 0;
                        if (GfxNative.unpackR(px) == 255) {
                            ok = 1;
                        }
                        System.out.println(ok);
                        image.dispose();
                    }
                }
                """, "GfxXform", Map.of(), 0));
    }

    @Test
    void drawImageCopy() throws Exception {
        assertEquals("1\n", run("""
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        Image src = Image.create(10, 10);
                        Graphics sg = src.beginGraphics();
                        sg.setColor(Color.GREEN);
                        sg.fillRect(0, 0, 10, 10);
                        sg.end();
                        Image dst = Image.create(40, 40);
                        Graphics g = dst.beginGraphics();
                        g.setColor(Color.BLACK);
                        g.fillRect(0, 0, 40, 40);
                        g.drawImage(src, 5, 5);
                        g.end();
                        int px = dst.getPixel(7, 7);
                        int ok = 0;
                        if (GfxNative.unpackG(px) == 255) {
                            ok = 1;
                        }
                        System.out.println(ok);
                        src.dispose();
                        dst.dispose();
                    }
                }
                """, "GfxBlit", Map.of(), 0));
    }

    @Test
    void resourceCounters() throws Exception {
        assertEquals("0\n", run("""
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        Image a = Image.create(8, 8);
                        Path p = new Path();
                        a.dispose();
                        p.dispose();
                        System.out.println(GfxMetrics.liveImages());
                    }
                }
                """, "GfxRes", Map.of(), 0));
    }

    @Test
    void ellipseAndStroke() throws Exception {
        assertEquals("1\n", run("""
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        Image image = Image.create(100, 100);
                        Graphics g = image.beginGraphics();
                        g.setColor(Color.WHITE);
                        g.fillRect(0, 0, 100, 100);
                        g.setStroke(new Stroke(3.0));
                        g.setColor(Color.BLUE);
                        g.fillEllipse(20, 20, 40, 40);
                        g.end();
                        int px = image.getPixel(40, 40);
                        int ok = 0;
                        if (GfxNative.unpackB(px) == 255) {
                            ok = 1;
                        }
                        System.out.println(ok);
                        image.dispose();
                    }
                }
                """, "GfxEllipse", Map.of(), 0));
    }

    @Test
    void jpegPlaceholderLoads() throws Exception {
        // Write minimal JPEG SOI via File from a prior PNG-named path created in Java test harness.
        Path jpg = tempDir.resolve("tiny.jpg");
        Files.write(jpg, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9});
        String path = jpg.toAbsolutePath().toString().replace("\\", "\\\\");
        assertEquals("1\n", run("""
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        Image img = Image.load("%s");
                        int ok = 0;
                        if (img.getWidth() == 8) {
                            ok = 1;
                        }
                        System.out.println(ok);
                        img.dispose();
                    }
                }
                """.formatted(path), "GfxJpeg", Map.of(), 0));
    }

    @Test
    void drawLineThroughputSmoke() throws Exception {
        String out = run("""
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        Image image = Image.create(200, 200);
                        Graphics g = image.beginGraphics();
                        g.setColor(Color.BLACK);
                        int i = 0;
                        while (i < 10000) {
                            g.drawLine(0, 0, 199, 199);
                            i = i + 1;
                        }
                        g.end();
                        System.out.println(GfxMetrics.liveContexts());
                        image.dispose();
                    }
                }
                """, "GfxPerfLine", Map.of(), 0);
        assertEquals("0\n", out);
    }

    @Test
    void colorStandards() throws Exception {
        assertEquals("1\n", run("""
                import jnative.graphics.*;
                public class Main {
                    public static void main(String[] args) {
                        int ok = 0;
                        if (Color.BLACK.getR() == 0
                                && Color.WHITE.getR() == 255
                                && Color.RED.getR() == 255
                                && Color.GREEN.getG() == 255
                                && Color.BLUE.getB() == 255
                                && Color.TRANSPARENT.getA() == 0) {
                            ok = 1;
                        }
                        System.out.println(ok);
                    }
                }
                """, "GfxColors", Map.of(), 0));
    }

    private String run(String source, String name, Map<String, String> env, int opt) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setOptLevel(opt);
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        options.setHeadless(true);
        options.setUiBackend("headless");
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.directory(tempDir.toFile());
        Map<String, String> e = pb.environment();
        e.putAll(env != null ? env : Map.of());
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = p.waitFor(60, TimeUnit.SECONDS);
        assertTrue(finished, "timeout");
        assertEquals(0, p.exitValue(), () -> stdout + stderr);
        return stdout;
    }
}
