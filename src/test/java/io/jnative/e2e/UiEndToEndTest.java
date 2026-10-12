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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** JNative 0.17 UI foundation — headless e2e.
 * @author 张晓达
 */
class UiEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void counterClickHeadless() throws Exception {
        assertEquals("Count: 1\nCount: 2\n", run("""
                import jnative.ui.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("JNative Counter", 400, 240);
                        VBox root = new VBox();
                        Label label = new Label("Count: 0");
                        Button button = new Button("Increment");
                        int[] count = new int[1];
                        count[0] = 0;
                        button.onClick(() -> {
                            count[0] = count[0] + 1;
                            label.setText("Count: " + count[0]);
                            System.out.println(label.getText());
                        });
                        root.add(label);
                        root.add(button);
                        window.setContent(root);
                        window.show();
                        UiTest.click(button);
                        UiTest.click(button);
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiCounter", Map.of("JNATIVE_HEADLESS", "1"), 0));
    }

    @Test
    void invokeLaterFromWorker() throws Exception {
        assertEquals("good\n", run("""
                import jnative.ui.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 200, 100);
                        Label label = new Label("bad");
                        window.setContent(label);
                        window.show();
                        Thread worker = new Thread(() -> {
                            Application.invokeLater(() -> {
                                label.setText("good");
                                System.out.println(label.getText());
                                Application.quit();
                            });
                        });
                        worker.start();
                        app.run();
                        worker.join();
                    }
                }
                """, "UiInvokeLater", Map.of("JNATIVE_HEADLESS", "1"), 0));
    }

    @Test
    void uiThreadViolation() throws Exception {
        // Capture via static holder to avoid try/catch+capture IR edge case.
        String out = run("""
                import jnative.ui.*;
                class Holder {
                    static Label label;
                    static int ok;
                }
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 200, 100);
                        Holder.label = new Label("x");
                        Holder.ok = 0;
                        window.setContent(Holder.label);
                        window.show();
                        Thread worker = new Thread(() -> {
                            try {
                                Holder.label.setText("bad");
                            } catch (UiThreadViolationException e) {
                                Holder.ok = 1;
                            }
                            Application.invokeLater(() -> {
                                System.out.println(Holder.ok);
                                Application.quit();
                            });
                        });
                        worker.start();
                        app.run();
                        worker.join();
                    }
                }
                """, "UiThreadViol", Map.of("JNATIVE_HEADLESS", "1"), 0);
        assertEquals("1\n", out);
    }

    @Test
    void multipleWindows() throws Exception {
        assertEquals("2\n1\n", run("""
                import jnative.ui.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window a = new Window("A", 200, 100);
                        Window b = new Window("B", 200, 100);
                        a.show();
                        b.show();
                        System.out.println(Application.liveWindows());
                        UiTest.closeWindow(b);
                        System.out.println(Application.liveWindows());
                        UiTest.closeWindow(a);
                        app.run();
                    }
                }
                """, "UiMultiWin", Map.of("JNATIVE_HEADLESS", "1"), 0));
    }

    @Test
    void unicodeTitleAndLabel() throws Exception {
        assertEquals("你好，JNative\n", run("""
                import jnative.ui.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("你好，JNative", 300, 200);
                        Label label = new Label("你好，JNative");
                        window.setContent(label);
                        window.show();
                        System.out.println(label.getText());
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "UiUnicode", Map.of("JNATIVE_HEADLESS", "1"), 0));
    }

    @Test
    void disposeIsIdempotent() throws Exception {
        assertEquals("ok\n", run("""
                import jnative.ui.*;
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Button b = new Button("x");
                        b.dispose();
                        b.dispose();
                        System.out.println("ok");
                        Application.quit();
                        app.run();
                    }
                }
                """, "UiDispose", Map.of("JNATIVE_HEADLESS", "1"), 0));
    }

    @Test
    void compileFailsWithoutUiStillOk() {
        // smoke: non-UI program unaffected
        Path src = tempDir.resolve("Plain.java");
        try {
            Files.writeString(src, """
                    public class Main {
                        public static void main(String[] args) {
                            System.out.println(1);
                        }
                    }
                    """);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(src);
        options.setEmitIr(true);
        options.setOptLevel(0);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
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
