/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.e2e;

import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class AsyncEndToEndTest {

    @TempDir
    Path temp;

    @Test
    void jnFutureStatesAndThenChain() throws Exception {
        String src = """
                class Main {
                    public static void main(String[] args) {
                        JnFuture a = new JnFuture();
                        if (a.getState() != JnFuture.PENDING) {
                            System.out.println("bad pending");
                        }
                        a.tryComplete("ok");
                        if (a.getState() != JnFuture.SUCCESS) {
                            System.out.println("bad success");
                        }
                        JnFuture c = new JnFuture();
                        c.state = JnFuture.FAILED;
                        if (c.getState() != JnFuture.FAILED) {
                            System.out.println("bad fail");
                        }
                        JnFuture d = new JnFuture();
                        d.tryCancel();
                        if (d.getState() != JnFuture.CANCELLED) {
                            System.out.println("bad cancel");
                        }
                        System.out.println("future-states-ok");
                    }
                }
                """;
        assertTrue(run(src).contains("future-states-ok"));
    }

    @Test
    void sleepConcurrencyWallTime() throws Exception {
        String src = """
                class Main {
                    public static void main(String[] args) {
                        int total = 200;
                        int delayMs = 50;
                        EventLoop loop = new EventLoop();
                        ConcurrencyState state = new ConcurrencyState();
                        state.loop = loop;
                        state.total = total;
                        state.startMs = AsyncClockNative.monoMillis();
                        state.done = 0;
                        int i = 0;
                        while (i < total) {
                            Async.sleepOnLoop(loop, delayMs).then(state);
                            i = i + 1;
                        }
                        EventLoopRunner runner = new EventLoopRunner();
                        runner.loop = loop;
                        runner.start();
                        runner.join();
                        long elapsed = AsyncClockNative.monoMillis() - state.startMs;
                        if (elapsed < 5000) {
                            System.out.println("concurrency-fast");
                        } else {
                            System.out.println("concurrency-slow");
                        }
                    }
                }
                class ConcurrencyState implements Function<Object, Object> {
                    EventLoop loop;
                    int total;
                    long startMs;
                    int done;
                    public Object apply(Object v) {
                        done = done + 1;
                        if (done >= total) {
                            loop.stop();
                        }
                        return v;
                    }
                }
                """;
        assertTrue(run(src).contains("concurrency-fast"));
    }

    private String run(String source) throws Exception {
        Path exe = compile(source);
        Process p = new ProcessBuilder(exe.toString())
                .redirectErrorStream(true)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(120, TimeUnit.SECONDS), "timeout");
        assertEquals(0, p.exitValue(), out);
        return out;
    }

    private Path compile(String source) throws Exception {
        Path src = temp.resolve("Main.java");
        Files.writeString(src, source);
        Path out = temp.resolve("main");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(src);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), result.diagnostics().toString());
        return out;
    }
}
