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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Real-OS-thread concurrency library: ExecutorService / TaskFuture / latches / semaphores /
 * locks / barriers / blocking queues. Every program is run as a subprocess with a hard timeout.
 *
 * @author 张晓达
 */
class ConcurrentEndToEndTest {

    @TempDir
    Path temp;

    @Test
    void executorResultsFailuresAndCounters() throws Exception {
        List<String> out = run("""
                import jnative.concurrent.*;
                import jnative.collections.*;

                public class Main {
                    static AtomicInt hits = new AtomicInt(0);

                    static int boom() {
                        throw new RuntimeException("kaboom");
                    }

                    public static void main(String[] args) {
                        ExecutorService ex = Executors.newFixedThreadPool(4);

                        ArrayList<TaskFuture<Integer>> fs = new ArrayList<TaskFuture<Integer>>();
                        int i = 0;
                        while (i < 20) {
                            int n = i;
                            fs.add(ex.submit(() -> n * n));
                            i = i + 1;
                        }
                        int sum = 0;
                        i = 0;
                        while (i < fs.size()) {
                            sum = sum + fs.get(i).get();
                            i = i + 1;
                        }
                        System.out.println("sum=" + sum);

                        TaskFuture<Integer> bad = ex.submit(() -> boom());
                        try {
                            bad.get();
                            System.out.println("failure-not-thrown");
                        } catch (ExecutionException e) {
                            System.out.println("execution-exception " + e.getMessage()
                                    + " cause=" + e.getCause().getMessage());
                        }
                        System.out.println("bad.isFailed=" + bad.isFailed() + " isDone=" + bad.isDone());

                        // invokeAll waits for every task.
                        ArrayList<Supplier<Integer>> work = new ArrayList<Supplier<Integer>>();
                        i = 0;
                        while (i < 5) {
                            int k = i + 1;
                            work.add(() -> k * 10);
                            i = i + 1;
                        }
                        ArrayList<TaskFuture<Integer>> all = ex.invokeAll(work);
                        int total = 0;
                        i = 0;
                        while (i < all.size()) {
                            total = total + all.get(i).get();
                            i = i + 1;
                        }
                        System.out.println("invokeAll=" + total);

                        CountDownLatch latch = new CountDownLatch(8);
                        i = 0;
                        while (i < 8) {
                            ex.execute(() -> {
                                hits.incrementAndGet();
                                latch.countDown();
                            });
                            i = i + 1;
                        }
                        latch.waitFor();
                        System.out.println("hits=" + hits.get() + " latch=" + latch.getCount());
                        System.out.println("latch-timeout=" + new CountDownLatch(1).waitFor(40));

                        System.out.println("idle=" + ex.awaitIdle(5000));
                        ex.shutdown();
                        System.out.println("shutdown=" + ex.isShutdown());
                        System.out.println("terminated=" + ex.awaitTermination(5000)
                                + " " + ex.isTerminated());
                        System.out.println("pool=" + ex.getPoolSize() + " active=" + ex.getActiveCount()
                                + " queued=" + ex.getQueueSize());
                    }
                }
                """);
        assertTrue(out.contains("sum=2470"), out.toString());
        assertTrue(out.contains("execution-exception task failed: kaboom cause=kaboom"), out.toString());
        assertTrue(out.contains("bad.isFailed=true isDone=true"), out.toString());
        assertTrue(out.contains("invokeAll=150"), out.toString());
        assertTrue(out.contains("hits=8 latch=0"), out.toString());
        assertTrue(out.contains("latch-timeout=false"), out.toString());
        assertTrue(out.contains("idle=true"), out.toString());
        assertTrue(out.contains("shutdown=true"), out.toString());
        assertTrue(out.contains("terminated=true true"), out.toString());
        assertTrue(out.contains("pool=4 active=0 queued=0"), out.toString());
    }

    /** Regression: a task that throws must not kill its worker (tasks were silently lost). */
    @Test
    void workerSurvivesThrowingTasks() throws Exception {
        List<String> out = run("""
                public class Main {
                    static AtomicInt done = new AtomicInt(0);

                    static void work(int fail) {
                        if (fail != 0) {
                            throw new RuntimeException("task boom");
                        }
                        done.incrementAndGet();
                    }

                    public static void main(String[] args) {
                        ExecutorService ex = new ExecutorService(2);
                        ex.setQuiet(true);
                        int i = 0;
                        while (i < 6) {
                            ex.execute(() -> work(1));
                            i = i + 1;
                        }
                        i = 0;
                        while (i < 100) {
                            ex.execute(() -> work(0));
                            i = i + 1;
                        }
                        ex.shutdown();
                        System.out.println("terminated=" + ex.awaitTermination(10000));
                        System.out.println("done=" + done.get());
                        System.out.println("failed=" + ex.getFailedTaskCount()
                                + " completed=" + ex.getCompletedTaskCount());
                        System.out.println("last=" + ex.getLastFailure().getMessage());
                    }
                }
                """);
        assertTrue(out.contains("terminated=true"), out.toString());
        assertTrue(out.contains("done=100"), out.toString());
        assertTrue(out.contains("failed=6 completed=100"), out.toString());
        assertTrue(out.contains("last=task boom"), out.toString());
    }

    @Test
    void boundedQueueRejectionPoliciesAndShutdownNow() throws Exception {
        List<String> out = run("""
                public class Main {
                    static AtomicInt ran = new AtomicInt(0);

                    /** One busy worker + one queued task, so the next submit hits the full queue. */
                    static ExecutorService saturated(int policy, CountDownLatch gate) {
                        ExecutorService e = new ExecutorService(1, 1, policy);
                        CountDownLatch started = new CountDownLatch(1);
                        e.execute(() -> {
                            started.countDown();
                            gate.waitFor();
                        });
                        started.waitFor();
                        e.execute(() -> ran.incrementAndGet());
                        return e;
                    }

                    public static void main(String[] args) {
                        // REJECT_THROW
                        CountDownLatch g1 = new CountDownLatch(1);
                        ExecutorService a = saturated(ThreadPoolExecutor.REJECT_THROW, g1);
                        boolean threw = false;
                        try {
                            a.execute(() -> ran.incrementAndGet());
                        } catch (RejectedExecutionException e) {
                            threw = true;
                        }
                        System.out.println("throw=" + threw + " rejected=" + a.getRejectedTaskCount()
                                + " queued=" + a.getQueueSize());
                        g1.countDown();
                        a.shutdown();
                        a.awaitTermination(5000);

                        // REJECT_DISCARD
                        CountDownLatch g2 = new CountDownLatch(1);
                        ExecutorService b = saturated(ThreadPoolExecutor.REJECT_DISCARD, g2);
                        b.execute(() -> ran.incrementAndGet());
                        System.out.println("discard rejected=" + b.getRejectedTaskCount());
                        g2.countDown();
                        b.shutdown();
                        b.awaitTermination(5000);

                        // REJECT_CALLER_RUNS: runs synchronously on the submitting thread.
                        CountDownLatch g3 = new CountDownLatch(1);
                        ExecutorService c = saturated(ThreadPoolExecutor.REJECT_CALLER_RUNS, g3);
                        AtomicLong runner = new AtomicLong(-1);
                        c.execute(() -> runner.set(Thread.currentThread().id()));
                        System.out.println("callerRuns="
                                + (runner.get() == Thread.currentThread().id()));
                        g3.countDown();
                        c.shutdown();
                        c.awaitTermination(5000);

                        // REJECT_BLOCK: the producer waits until a slot frees up.
                        CountDownLatch g4 = new CountDownLatch(1);
                        ExecutorService d = saturated(ThreadPoolExecutor.REJECT_BLOCK, g4);
                        AtomicBool submitted = new AtomicBool(false);
                        Thread producer = new Thread(() -> {
                            d.execute(() -> ran.incrementAndGet());
                            submitted.set(true);
                        });
                        producer.start();
                        Thread.sleep(150);
                        System.out.println("blockedWhileFull=" + !submitted.get());
                        g4.countDown();
                        producer.join();
                        d.shutdown();
                        System.out.println("blockTerminated=" + d.awaitTermination(5000)
                                + " submitted=" + submitted.get());

                        // ran: a(1 queued) + b(1 queued) + c(1 queued; caller-run task only sets runner)
                        //      + d(1 queued + 1 from the blocked producer) = 5
                        System.out.println("ran=" + ran.get());

                        // shutdownNow cancels queued futures but lets the running task finish.
                        ExecutorService e = new ExecutorService(1);
                        CountDownLatch started = new CountDownLatch(1);
                        CountDownLatch gate = new CountDownLatch(1);
                        TaskFuture<Object> running = e.submitRun(() -> {
                            started.countDown();
                            gate.waitFor();
                        });
                        started.waitFor();
                        TaskFuture<Integer> q1 = e.submit(() -> 1);
                        TaskFuture<Integer> q2 = e.submit(() -> 2);
                        int dropped = e.shutdownNow();
                        System.out.println("dropped=" + dropped + " q1.cancelled=" + q1.isCancelled()
                                + " q2.cancelled=" + q2.isCancelled());
                        boolean cancelled = false;
                        try {
                            q1.get();
                        } catch (CancellationException ce) {
                            cancelled = true;
                        }
                        System.out.println("getCancelled=" + cancelled);
                        boolean rejected = false;
                        try {
                            e.submit(() -> 3);
                        } catch (RejectedExecutionException re) {
                            rejected = true;
                        }
                        System.out.println("afterShutdownRejected=" + rejected);
                        gate.countDown();
                        System.out.println("stopTerminated=" + e.awaitTermination(5000)
                                + " runningDone=" + running.isDone());
                    }
                }
                """);
        assertTrue(out.contains("throw=true rejected=1 queued=1"), out.toString());
        assertTrue(out.contains("discard rejected=1"), out.toString());
        assertTrue(out.contains("callerRuns=true"), out.toString());
        assertTrue(out.contains("blockedWhileFull=true"), out.toString());
        assertTrue(out.contains("blockTerminated=true submitted=true"), out.toString());
        assertTrue(out.contains("ran=5"), out.toString());
        assertTrue(out.contains("dropped=2 q1.cancelled=true q2.cancelled=true"), out.toString());
        assertTrue(out.contains("getCancelled=true"), out.toString());
        assertTrue(out.contains("afterShutdownRejected=true"), out.toString());
        assertTrue(out.contains("stopTerminated=true runningDone=true"), out.toString());
    }

    @Test
    void timedWaits() throws Exception {
        List<String> out = run("""
                public class Main {
                    static int slow() {
                        Thread.sleep(400);
                        return 7;
                    }

                    public static void main(String[] args) {
                        ExecutorService ex = new ExecutorService(2);
                        TaskFuture<Integer> f = ex.submit(() -> slow());
                        boolean timedOut = false;
                        try {
                            f.get(40);
                        } catch (TimeoutException t) {
                            timedOut = true;
                        }
                        System.out.println("timedOut=" + timedOut + " done=" + f.isDone());
                        System.out.println("value=" + f.get());

                        TaskFuture<Integer> g = ex.submit(() -> slow());
                        ex.shutdown();
                        System.out.println("early=" + ex.awaitTermination(40));
                        System.out.println("late=" + ex.awaitTermination(5000));
                        System.out.println("g=" + g.get());

                        Semaphore s = new Semaphore(0);
                        System.out.println("tryAcquireFor=" + s.tryAcquireFor(40));
                        s.release();
                        System.out.println("tryAcquireFor2=" + s.tryAcquireFor(40));

                        BlockingQueue<Integer> q = new BlockingQueue<Integer>(1);
                        System.out.println("pollFor=" + q.pollFor(40));
                        System.out.println("offer1=" + q.offer(1) + " offer2=" + q.offer(2)
                                + " offerFor=" + q.offerFor(3, 40));
                    }
                }
                """);
        assertTrue(out.contains("timedOut=true done=false"), out.toString());
        assertTrue(out.contains("value=7"), out.toString());
        assertTrue(out.contains("early=false"), out.toString());
        assertTrue(out.contains("late=true"), out.toString());
        assertTrue(out.contains("g=7"), out.toString());
        assertTrue(out.contains("tryAcquireFor=false"), out.toString());
        assertTrue(out.contains("tryAcquireFor2=true"), out.toString());
        assertTrue(out.contains("pollFor=null"), out.toString());
        assertTrue(out.contains("offer1=true offer2=false offerFor=false"), out.toString());
    }

    @Test
    void semaphoreLockAndBarrier() throws Exception {
        List<String> out = run("""
                public class Main {
                    static int shared = 0;
                    static AtomicInt active = new AtomicInt(0);
                    static AtomicInt maxActive = new AtomicInt(0);
                    static AtomicInt violations = new AtomicInt(0);
                    static AtomicInt cycles = new AtomicInt(0);

                    public static void main(String[] args) {
                        // Semaphore bounds concurrency.
                        Semaphore sem = new Semaphore(3);
                        ExecutorService ex = new ExecutorService(8);
                        CountDownLatch all = new CountDownLatch(8);
                        int i = 0;
                        while (i < 8) {
                            ex.execute(() -> {
                                sem.acquire();
                                int cur = active.incrementAndGet();
                                int m = maxActive.get();
                                while (cur > m) {
                                    if (maxActive.compareAndSet(m, cur)) {
                                        m = cur;
                                    } else {
                                        m = maxActive.get();
                                    }
                                }
                                Thread.sleep(100);
                                active.decrementAndGet();
                                sem.release();
                                all.countDown();
                            });
                            i = i + 1;
                        }
                        all.waitFor();
                        System.out.println("maxActive<=3:" + (maxActive.get() <= 3)
                                + " maxActive>=2:" + (maxActive.get() >= 2)
                                + " permits=" + sem.availablePermits());

                        // ReentrantLock protects a plain int, including nested acquisition.
                        ReentrantLock lock = new ReentrantLock();
                        CountDownLatch locked = new CountDownLatch(4);
                        i = 0;
                        while (i < 4) {
                            ex.execute(() -> {
                                int k = 0;
                                while (k < 2000) {
                                    lock.lock();
                                    lock.lock();
                                    shared = shared + 1;
                                    lock.unlock();
                                    lock.unlock();
                                    k = k + 1;
                                }
                                locked.countDown();
                            });
                            i = i + 1;
                        }
                        locked.waitFor();
                        System.out.println("shared=" + shared);

                        lock.lock();
                        lock.lock();
                        System.out.println("holds=" + lock.getHoldCount()
                                + " held=" + lock.isHeldByCurrentThread());
                        AtomicBool otherGotIt = new AtomicBool(true);
                        Thread t = new Thread(() -> otherGotIt.set(lock.tryLock()));
                        t.start();
                        t.join();
                        System.out.println("otherTryLock=" + otherGotIt.get());
                        lock.unlock();
                        lock.unlock();
                        boolean misuse = false;
                        try {
                            lock.unlock();
                        } catch (IllegalStateException e) {
                            misuse = true;
                        }
                        System.out.println("unlockNotHeld=" + misuse + " locked=" + lock.isLocked());

                        // CyclicBarrier: nobody enters phase p+1 before everyone finished phase p.
                        CyclicBarrier barrier = new CyclicBarrier(4, () -> cycles.incrementAndGet());
                        AtomicInt[] arrived = new AtomicInt[3];
                        i = 0;
                        while (i < 3) {
                            arrived[i] = new AtomicInt(0);
                            i = i + 1;
                        }
                        CountDownLatch finished = new CountDownLatch(4);
                        i = 0;
                        while (i < 4) {
                            ex.execute(() -> {
                                int p = 0;
                                while (p < 3) {
                                    arrived[p].incrementAndGet();
                                    barrier.arriveAndWait();
                                    if (arrived[p].get() != 4) {
                                        violations.incrementAndGet();
                                    }
                                    p = p + 1;
                                }
                                finished.countDown();
                            });
                            i = i + 1;
                        }
                        finished.waitFor();
                        System.out.println("violations=" + violations.get() + " cycles=" + cycles.get());
                        ex.shutdown();
                        System.out.println("terminated=" + ex.awaitTermination(5000));
                    }
                }
                """);
        assertTrue(out.contains("maxActive<=3:true maxActive>=2:true permits=3"), out.toString());
        assertTrue(out.contains("shared=8000"), out.toString());
        assertTrue(out.contains("holds=2 held=true"), out.toString());
        assertTrue(out.contains("otherTryLock=false"), out.toString());
        assertTrue(out.contains("unlockNotHeld=true locked=false"), out.toString());
        assertTrue(out.contains("violations=0 cycles=3"), out.toString());
        assertTrue(out.contains("terminated=true"), out.toString());
    }

    @Test
    void blockingQueueProducersConsumersAndGcStress() throws Exception {
        List<String> out = run("""
                public class Main {
                    static AtomicLong sum = new AtomicLong(0);
                    static AtomicInt count = new AtomicInt(0);
                    static AtomicInt tasks = new AtomicInt(0);

                    static int churn(int seed) {
                        int[] a = new int[16];
                        String s = "task-" + seed;
                        a[seed & 15] = s.length();
                        tasks.incrementAndGet();
                        return a[seed & 15];
                    }

                    public static void main(String[] args) {
                        BlockingQueue<Integer> q = new BlockingQueue<Integer>(8);
                        ExecutorService ex = new ExecutorService(5);
                        CountDownLatch producers = new CountDownLatch(2);
                        CountDownLatch consumers = new CountDownLatch(3);
                        int c = 0;
                        while (c < 3) {
                            ex.execute(() -> {
                                Integer v = q.take();
                                while (v != null) {
                                    sum.addAndGet(v);
                                    count.incrementAndGet();
                                    v = q.take();
                                }
                                consumers.countDown();
                            });
                            c = c + 1;
                        }
                        int p = 0;
                        while (p < 2) {
                            ex.execute(() -> {
                                int i = 1;
                                while (i <= 500) {
                                    q.put(i);
                                    i = i + 1;
                                }
                                producers.countDown();
                            });
                            p = p + 1;
                        }
                        producers.waitFor();
                        q.close();
                        consumers.waitFor();
                        System.out.println("count=" + count.get() + " sum=" + sum.get());
                        boolean putFailed = false;
                        try {
                            q.put(1);
                        } catch (IllegalStateException e) {
                            putFailed = true;
                        }
                        System.out.println("putAfterClose=" + putFailed + " take=" + q.take()
                                + " closed=" + q.isClosed());
                        ex.shutdown();
                        ex.awaitTermination(5000);

                        // Back-pressure + GC stress: many short tasks allocating on 4 workers.
                        ExecutorService pool = Executors.newBoundedThreadPool(4, 32);
                        TaskFuture<Integer> last = null;
                        int n = 0;
                        while (n < 20000) {
                            int seed = n;
                            last = pool.submit(() -> churn(seed));
                            if (n == 10000) {
                                System.gc();
                            }
                            n = n + 1;
                        }
                        last.get();
                        pool.shutdown();
                        System.out.println("stressTerminated=" + pool.awaitTermination(30000)
                                + " tasks=" + tasks.get() + " completed=" + pool.getCompletedTaskCount());
                    }
                }
                """);
        assertTrue(out.contains("count=1000 sum=250500"), out.toString());
        assertTrue(out.contains("putAfterClose=true take=null closed=true"), out.toString());
        assertTrue(out.contains("stressTerminated=true tasks=20000 completed=20000"), out.toString());
    }

    @Test
    void daemonPoolDoesNotBlockProcessExit() throws Exception {
        long start = System.nanoTime();
        List<String> out = run("""
                public class Main {
                    public static void main(String[] args) {
                        ExecutorService d = Executors.newDaemonThreadPool(2);
                        d.execute(() -> Thread.sleep(600000));
                        Thread.sleep(50);
                        System.out.println("main done cpus>=1:" + (Executors.availableProcessors() >= 1));
                    }
                }
                """, 20);
        assertTrue(out.contains("main done cpus>=1:true"), out.toString());
        assertTrue((System.nanoTime() - start) / 1_000_000_000L < 60, "exit should not wait for daemon workers");
    }

    private List<String> run(String source) throws Exception {
        return run(source, 120);
    }

    private List<String> run(String source, long timeoutSeconds) throws Exception {
        Path src = temp.resolve("Main.java");
        Files.writeString(src, source);
        Path exe = temp.resolve("main");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(src);
        options.setOutputFile(exe);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), result.diagnostics().toString());

        Path outFile = temp.resolve("out.txt");
        Process p = new ProcessBuilder(exe.toString())
                .redirectErrorStream(true)
                .redirectOutput(outFile.toFile())
                .start();
        try {
            if (!p.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                p.waitFor(5, TimeUnit.SECONDS);
                fail("timeout after " + timeoutSeconds + "s; partial output:\n"
                        + Files.readString(outFile, StandardCharsets.UTF_8));
            }
            String text = Files.readString(outFile, StandardCharsets.UTF_8);
            assertEquals(0, p.exitValue(), text);
            return text.lines().toList();
        } finally {
            if (p.isAlive()) {
                p.destroyForcibly();
            }
        }
    }
}
