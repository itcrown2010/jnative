/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.ProgramSources;
import io.jnative.compiler.SourceUnit;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Collection / UI subscription / Future callback e2e (O0 + default opt).
 * @author 张晓达
 */
class FunctionalCallbacksEndToEndTest {

    @TempDir
    Path tmp;

    @Test
    void collectionFilterMapForEachO0AndOpt() throws Exception {
        String src = """
                class OrderRow {
                    String id;
                    int totalCents;
                    OrderRow(String id, int totalCents) {
                        this.id = id;
                        this.totalCents = totalCents;
                    }
                }
                class Counter { int n; }
                public class Main {
                    public static void main(String[] args) {
                        ArrayList<OrderRow> orders = new ArrayList<OrderRow>();
                        orders.add(new OrderRow("1", 1000));
                        orders.add(new OrderRow("2", 100));
                        ArrayList<OrderRow> big = orders.filter(o -> o.totalCents >= 500);
                        System.out.println(big.size());
                        ArrayList<Object> ids = orders.mapValues(o -> o.id);
                        System.out.println(((String) ids.get(0)).equals("1") ? 1 : 0);
                        Counter c = new Counter();
                        orders.forEach(o -> { c.n = c.n + o.totalCents; });
                        System.gc();
                        System.out.println(c.n);
                        try {
                            orders.forEach(null);
                            System.out.println(0);
                        } catch (NullPointerException e) {
                            System.out.println(1);
                        }
                    }
                }
                """;
        assertO0AndOpt(src, "1\n1\n1100\n1\n");
    }

    @Test
    void uiSubscribeCancelDispose() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String src = """
                import jnative.ui.*;
                class St { int n; }
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 200, 120);
                        Button button = new Button("x");
                        St st = new St();
                        UiSubscription sub = button.subscribeAction(ev -> {
                            st.n = st.n + 1;
                        });
                        window.setContent(button);
                        window.show();
                        UiTest.click(button);
                        System.out.println(st.n);
                        sub.cancel();
                        sub.cancel();
                        UiTest.click(button);
                        System.out.println(st.n);
                        UiSubscription sub2 = button.subscribeAction(ev -> {
                            st.n = st.n + 5;
                        });
                        button.dispose();
                        System.out.println(st.n);
                        System.out.println(sub2.isCancelled() ? 1 : 0);
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """;
        // dispose cancels outstanding subscriptions (releases closure refs).
        // Expected: 1 (click), 1 (after cancel click), 1 (dispose no extra click), 1 (cancelled)
        assertNative(src, "1\n1\n1\n1\n", Map.of("JNATIVE_HEADLESS", "1"), 1);
        assertNative(src, "1\n1\n1\n1\n", Map.of("JNATIVE_HEADLESS", "1"), 0);
    }

    @Test
    void futureSubscribeCancelAndLoop() throws Exception {
        String src = """
                class Box { int n; }
                class Work implements BlockingSupplier {
                    public Object get() { return Integer.valueOf(21); }
                }
                public class Main {
                    public static void main(String[] args) {
                        EventLoop loop = new EventLoop();
                        Box box = new Box();
                        JnFuture f = Async.submit(loop, new Work());
                        f.onSuccess(v -> {
                            box.n = ((Integer) v).intValue() * 2;
                        });
                        JnFuture pend = JnFuture.createPending(loop);
                        FutureSubscription sub = pend.onSuccess(v -> { box.n = 999; });
                        sub.cancel();
                        pend.tryComplete(Integer.valueOf(1));
                        f.onSuccess(v -> { loop.stop(); });
                        EventLoopRunner runner = new EventLoopRunner();
                        runner.loop = loop;
                        runner.start();
                        runner.join();
                        System.out.println(box.n);
                        System.out.println(sub.isCancelled() ? 1 : 0);
                        Async.shutdownSharedPool();
                    }
                }
                """;
        assertO0AndOpt(src, "42\n1\n");
    }

    @Test
    void uiSubscribeDuringDispatchAndException() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        String src = """
                import jnative.ui.*;
                class St { int n; int mid; }
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("T", 200, 120);
                        Button button = new Button("x");
                        St st = new St();
                        UiSubscription a = button.subscribeAction(ev -> {
                            st.n = st.n + 1;
                            button.subscribeAction(ev2 -> { st.mid = st.mid + 1; });
                        });
                        UiSubscription b = button.subscribeAction(ev -> {
                            st.n = st.n + 10;
                        });
                        window.setContent(button);
                        window.show();
                        UiTest.click(button);
                        // snapshot: a and b run; mid-subscribe does not run for this click
                        System.out.println(st.n);
                        System.out.println(st.mid);
                        UiTest.click(button);
                        System.out.println(st.mid >= 1 ? 1 : 0);
                        a.cancel();
                        b.cancel();
                        try {
                            button.subscribeAction(null);
                            System.out.println(0);
                        } catch (NullPointerException e) {
                            System.out.println(1);
                        }
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """;
        assertNative(src, "11\n0\n1\n1\n", Map.of("JNATIVE_HEADLESS", "1"), 0);
    }

    @Test
    void collectionMethodRefAndNestedGc() throws Exception {
        String src = """
                class Holder {
                    static String tag(OrderRow o) { return o.id; }
                    boolean big(OrderRow o) { return o.totalCents >= 100; }
                }
                class OrderRow {
                    String id;
                    int totalCents;
                    OrderRow(String id, int totalCents) {
                        this.id = id;
                        this.totalCents = totalCents;
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        ArrayList<OrderRow> orders = new ArrayList<OrderRow>();
                        orders.add(new OrderRow("甲", 200));
                        orders.add(new OrderRow("乙", 50));
                        Holder h = new Holder();
                        ArrayList<OrderRow> big = orders.filter(h::big);
                        System.out.println(big.size());
                        ArrayList<Object> ids = big.mapValues(Holder::tag);
                        System.gc();
                        System.gc();
                        String s = (String) ids.get(0);
                        System.out.println(s.length());
                        Consumer<OrderRow> outer = o -> {
                            Consumer<OrderRow> inner = x -> System.out.println(x.totalCents);
                            inner.accept(o);
                        };
                        big.forEach(outer);
                    }
                }
                """;
        assertO0AndOpt(src, "1\n1\n200\n");
    }

    private void assertO0AndOpt(String src, String expected) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        assertEquals(expected, compileRun(tmp.resolve("o0"), src, 0, Map.of()), "O0");
        assertEquals(expected, compileRun(tmp.resolve("o1"), src, 1, Map.of()), "O1");
    }

    private void assertNative(String src, String expected, Map<String, String> env, int opt)
            throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        assertEquals(expected, compileRun(tmp.resolve("ui" + opt), src, opt, env));
    }

    private String compileRun(Path out, String src, int opt, Map<String, String> env)
            throws Exception {
        Path file = out.getParent().resolve(out.getFileName() + ".java");
        Files.writeString(file, src, StandardCharsets.UTF_8);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(file);
        options.setProgramSources(ProgramSources.of(SourceUnit.ofPath(file, src)));
        options.setOutputFile(out);
        options.setOptLevel(opt);
        if (env.containsKey("JNATIVE_HEADLESS")) {
            options.setUiBackend("headless");
        }
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().stream().map(Object::toString)
                .reduce("", (a, b) -> a + "\n" + b));
        ProcessBuilder pb = new ProcessBuilder(cr.executable().toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        pb.environment().putAll(env);
        Process p = pb.start();
        boolean finished = p.waitFor(90, TimeUnit.SECONDS);
        if (!finished) {
            p.destroyForcibly();
            fail("timeout");
        }
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, p.exitValue(), stdout);
        return stdout;
    }
}
