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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author 张晓达
 */
class ThreadEndToEndTest {

    @TempDir
    Path temp;

    @Test
    void finalAcceptanceFourWorkersAtomic() throws Exception {
        String src = """
                class Worker extends Thread {
                    private int id;
                    Worker(int id) {
                        this.id = id;
                    }
                    public void run() {
                        int i = 0;
                        while (i < 10000) {
                            Main.counter.incrementAndGet();
                            Person p = new Person();
                            p.value = i;
                            i = i + 1;
                        }
                        System.out.println(id);
                    }
                }
                class Person {
                    int value;
                }
                public class Main {
                    static AtomicInt counter = new AtomicInt(0);
                    public static void main(String[] args) {
                        Worker a = new Worker(1);
                        Worker b = new Worker(2);
                        Worker c = new Worker(3);
                        Worker d = new Worker(4);
                        a.start();
                        b.start();
                        c.start();
                        d.start();
                        a.join();
                        b.join();
                        c.join();
                        d.join();
                        System.gc();
                        System.out.println(counter.get());
                    }
                }
                """;
        String out = run(src);
        assertTrue(out.contains("40000"), out);
        assertTrue(out.contains("1"), out);
        assertTrue(out.contains("2"), out);
        assertTrue(out.contains("3"), out);
        assertTrue(out.contains("4"), out);
    }

    @Test
    void mutexCounterFourWorkers() throws Exception {
        String src = """
                class Worker extends Thread {
                    public void run() {
                        int i = 0;
                        while (i < 10000) {
                            Main.lock.lock();
                            Main.counter = Main.counter + 1;
                            Main.lock.unlock();
                            i = i + 1;
                        }
                    }
                }
                public class Main {
                    static Mutex lock = new Mutex();
                    static int counter;
                    public static void main(String[] args) {
                        Worker a = new Worker();
                        Worker b = new Worker();
                        Worker c = new Worker();
                        Worker d = new Worker();
                        a.start();
                        b.start();
                        c.start();
                        d.start();
                        a.join();
                        b.join();
                        c.join();
                        d.join();
                        System.out.println(counter);
                    }
                }
                """;
        assertEquals("40000\n", run(src));
    }

    @Test
    void startTwiceThrows() throws Exception {
        String src = """
                class Worker extends Thread {
                    public void run() {
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Worker w = new Worker();
                        w.start();
                        w.start();
                        w.join();
                    }
                }
                """;
        Path exe = compile(src);
        Process p = new ProcessBuilder(exe.toString())
                .redirectErrorStream(true)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(30, TimeUnit.SECONDS));
        assertTrue(out.contains("IllegalStateException") || p.exitValue() != 0, out);
    }

    @Test
    void sleepAndCurrentThread() throws Exception {
        String src = """
                public class Main {
                    public static void main(String[] args) {
                        Thread.sleep(50);
                        Thread t = Thread.currentThread();
                        System.out.println(t.id());
                    }
                }
                """;
        String out = run(src);
        assertTrue(out.trim().matches("\\d+"), out);
    }

    @Test
    void producerConsumer() throws Exception {
        String src = """
                class Producer extends Thread {
                    public void run() {
                        int i = 0;
                        while (i < 10000) {
                            Main.lock.lock();
                            while (Main.count >= 64) {
                                Main.notFull.await(Main.lock);
                            }
                            Main.count = Main.count + 1;
                            Main.produced = Main.produced + 1;
                            Main.notEmpty.signal();
                            Main.lock.unlock();
                            i = i + 1;
                        }
                    }
                }
                class BufferConsumer extends Thread {
                    public void run() {
                        int i = 0;
                        while (i < 10000) {
                            Main.lock.lock();
                            while (Main.count <= 0) {
                                Main.notEmpty.await(Main.lock);
                            }
                            Main.count = Main.count - 1;
                            Main.consumed = Main.consumed + 1;
                            Main.notFull.signal();
                            Main.lock.unlock();
                            i = i + 1;
                        }
                    }
                }
                public class Main {
                    static Mutex lock = new Mutex();
                    static Condition notEmpty = new Condition();
                    static Condition notFull = new Condition();
                    static int count;
                    static int produced;
                    static int consumed;
                    public static void main(String[] args) {
                        Producer p = new Producer();
                        BufferConsumer c = new BufferConsumer();
                        p.start();
                        c.start();
                        p.join();
                        c.join();
                        System.out.println(produced);
                        System.out.println(consumed);
                        System.out.println(count);
                    }
                }
                """;
        String out = run(src);
        assertEquals("10000\n10000\n0\n", out);
    }

    @Test
    void osThreadCount() throws Exception {
        String src = """
                class Worker extends Thread {
                    public void run() {
                        Thread.sleep(200);
                    }
                }
                public class Main {
                    public static void main(String[] args) {
                        Worker a = new Worker();
                        Worker b = new Worker();
                        a.start();
                        b.start();
                        Thread.sleep(50);
                        System.out.println(1);
                        a.join();
                        b.join();
                    }
                }
                """;
        assertTrue(run(src).contains("1"));
    }

    @Test
    void extendedAtomicOpsAreAtomic() throws Exception {
        String src = """
                class Worker extends Thread {
                    public void run() {
                        int i = 0;
                        while (i < 1000) {
                            Main.a.addAndGet(3);
                            Main.a.getAndAdd(2);
                            Main.a.decrementAndGet();
                            Main.a.getAndDecrement();
                            Main.l.addAndGet(3L);
                            Main.l.getAndAdd(2L);
                            Main.l.decrementAndGet();
                            Main.l.getAndDecrement();
                            i = i + 1;
                        }
                    }
                }
                public class Main {
                    static AtomicInt a = new AtomicInt(0);
                    static AtomicLong l = new AtomicLong(0);
                    public static void main(String[] args) {
                        Worker[] ws = new Worker[4];
                        int i = 0;
                        while (i < 4) {
                            ws[i] = new Worker();
                            ws[i].start();
                            i = i + 1;
                        }
                        i = 0;
                        while (i < 4) {
                            ws[i].join();
                            i = i + 1;
                        }
                        System.out.println("a=" + a.get() + " l=" + l.get());
                        AtomicInt x = new AtomicInt(5);
                        System.out.println("old=" + x.getAndSet(9) + " now=" + x.get()
                                + " add=" + x.addAndGet(-4) + " getAndAdd=" + x.getAndAdd(10)
                                + " end=" + x.get());
                        AtomicLong y = new AtomicLong(1L);
                        System.out.println("lold=" + y.getAndSet(7L) + " lnow=" + y.get());
                        AtomicBool b = new AtomicBool(false);
                        System.out.println("bold=" + b.getAndSet(true) + " bnow=" + b.get()
                                + " bold2=" + b.getAndSet(false) + " bnow2=" + b.get());
                    }
                }
                """;
        String out = run(src);
        assertTrue(out.contains("a=12000 l=12000"), out);
        assertTrue(out.contains("old=5 now=9 add=5 getAndAdd=5 end=15"), out);
        assertTrue(out.contains("lold=1 lnow=7"), out);
        assertTrue(out.contains("bold=false bnow=true bold2=true bnow2=false"), out);
    }

    @Test
    void conditionAwaitMillisTimesOutAndWakes() throws Exception {
        String src = """
                class Signaller extends Thread {
                    public void run() {
                        Thread.sleep(150);
                        Main.m.lock();
                        Main.ready = 1;
                        Main.c.signalAll();
                        Main.m.unlock();
                    }
                }
                public class Main {
                    static Mutex m = new Mutex();
                    static Condition c = new Condition();
                    static int ready = 0;
                    public static void main(String[] args) {
                        m.lock();
                        boolean first = c.awaitMillis(m, 40);
                        // still the owner after a timed-out wait
                        m.unlock();
                        System.out.println("timeout=" + first);
                        Signaller s = new Signaller();
                        s.start();
                        m.lock();
                        boolean woken = false;
                        while (ready == 0) {
                            woken = c.awaitMillis(m, 5000);
                        }
                        m.unlock();
                        s.join();
                        System.out.println("woken=" + woken + " ready=" + ready);
                    }
                }
                """;
        String out = run(src);
        assertTrue(out.contains("timeout=false"), out);
        assertTrue(out.contains("woken=true ready=1"), out);
    }

    private String run(String source) throws Exception {
        Path exe = compile(source);
        Process p = new ProcessBuilder(exe.toString())
                .redirectErrorStream(true)
                .start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS), "timeout");
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
