/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative concurrency library: executor service, task futures, latches, semaphores,
// locks, barriers and blocking queues. Built on the real-OS-thread primitives
// (Thread / Mutex / Condition / Atomic*) and ThreadPoolExecutor from the collections stdlib.
//
// Not available on wasm32-browser / mobile-wasm targets (no THREADS capability).
//
// Rules of thumb:
//   * Pool workers are non-daemon: call shutdown() (or close()) before main returns, otherwise the
//     process waits for the workers (same as the JVM). Executors.newDaemonThreadPool() workers
//     do not keep the process alive.
//   * Every timed wait takes milliseconds and re-checks its predicate in a loop.

class ExecutionException extends RuntimeException {
    Exception failure;

    ExecutionException(String message, Exception failure) {
        super(message);
        this.failure = failure;
    }

    Throwable getCause() {
        return failure;
    }
}

class CancellationException extends RuntimeException {
    CancellationException(String message) {
        super(message);
    }
}

/** A queued task that can be cancelled (used when a pool is stopped via shutdownNow()). */
interface Cancellable extends PoolTask {
    boolean cancel();
}

/** Adapts a Runnable to the pool's internal task type. */
class RunnableTask implements PoolTask {
    Runnable target;

    RunnableTask(Runnable target) {
        this.target = target;
    }

    public void run() {
        target.run();
    }
}

/** Result handle of ExecutorService.submit(...). */
class TaskFuture<T> implements Cancellable {
    // 0 pending, 1 running, 2 done, 3 failed, 4 cancelled
    int state;
    Supplier<T> supplier;
    Runnable runnable;
    T value;
    Exception error;
    Mutex mu;
    Condition cv;

    TaskFuture(Supplier<T> supplier, Runnable runnable) {
        this.supplier = supplier;
        this.runnable = runnable;
        this.state = 0;
        this.mu = new Mutex();
        this.cv = new Condition();
    }

    public void run() {
        mu.lock();
        if (state != 0) {
            mu.unlock();
            return;
        }
        state = 1;
        mu.unlock();
        T result = null;
        Exception failure = null;
        try {
            if (supplier != null) {
                result = supplier.get();
            } else {
                runnable.run();
            }
        } catch (Exception ex) {
            failure = ex;
        }
        mu.lock();
        if (failure != null) {
            error = failure;
            state = 3;
        } else {
            value = result;
            state = 2;
        }
        cv.signalAll();
        mu.unlock();
    }

    /** Cancels the task if it has not started yet; returns true if it is (now) cancelled. */
    public boolean cancel() {
        mu.lock();
        if (state == 0) {
            state = 4;
            cv.signalAll();
        }
        boolean r = state == 4;
        mu.unlock();
        return r;
    }

    boolean isDone() {
        mu.lock();
        boolean r = state >= 2;
        mu.unlock();
        return r;
    }

    boolean isCancelled() {
        mu.lock();
        boolean r = state == 4;
        mu.unlock();
        return r;
    }

    /** True if the task finished by throwing. */
    boolean isFailed() {
        mu.lock();
        boolean r = state == 3;
        mu.unlock();
        return r;
    }

    /** Blocks until the task finished (successfully, failed or cancelled) without throwing. */
    void awaitDone() {
        mu.lock();
        while (state < 2) {
            cv.await(mu);
        }
        mu.unlock();
    }

    /** Blocks until done. Throws ExecutionException if the task failed, CancellationException if cancelled. */
    T get() {
        mu.lock();
        while (state < 2) {
            cv.await(mu);
        }
        T r = finish();
        return r;
    }

    /** Like get() but throws TimeoutException after timeoutMs. */
    T get(long timeoutMs) {
        long deadline = PoolClockNative.monoMillis() + timeoutMs;
        mu.lock();
        while (state < 2) {
            long left = deadline - PoolClockNative.monoMillis();
            if (left <= 0) {
                mu.unlock();
                throw new TimeoutException("task did not finish within " + timeoutMs + " ms");
            }
            cv.awaitMillis(mu, left);
        }
        T r = finish();
        return r;
    }

    /** Called with mu held and state >= 2; releases mu. */
    private T finish() {
        int st = state;
        T v = value;
        Exception err = error;
        mu.unlock();
        if (st == 3) {
            String m = err.getMessage();
            if (m == null) {
                m = "";
            }
            throw new ExecutionException("task failed: " + m, err);
        }
        if (st == 4) {
            throw new CancellationException("task was cancelled");
        }
        return v;
    }
}

class ConcCpuNative {
    @Native(symbol = "jnative_cpu_count")
    static native int cpuCount();
}

/** Fixed-size pool with Future-style results. */
class ExecutorService {
    ThreadPoolExecutor pool;

    ExecutorService(int workers) {
        pool = new ThreadPoolExecutor(workers, 0, 0, 0);
    }

    /**
     * @param queueCapacity max queued tasks (0 = unbounded)
     * @param rejectPolicy  ThreadPoolExecutor.REJECT_THROW / _CALLER_RUNS / _BLOCK / _DISCARD
     */
    ExecutorService(int workers, int queueCapacity, int rejectPolicy) {
        pool = new ThreadPoolExecutor(workers, 0, queueCapacity, rejectPolicy);
    }

    ExecutorService(int workers, int queueCapacity, int rejectPolicy, boolean daemon) {
        int d = 0;
        if (daemon) {
            d = 1;
        }
        pool = new ThreadPoolExecutor(workers, d, queueCapacity, rejectPolicy);
    }

    /** Fire-and-forget. A task that throws is counted and reported on stderr; the worker survives. */
    void execute(Runnable task) {
        pool.execute(new RunnableTask(task));
    }

    <T> TaskFuture<T> submit(Supplier<T> task) {
        TaskFuture<T> f = new TaskFuture<T>(task, null);
        pool.execute(f);
        return f;
    }

    /** Submit a Runnable; the future's get() returns null once it completed. */
    TaskFuture<Object> submitRun(Runnable task) {
        TaskFuture<Object> f = new TaskFuture<Object>(null, task);
        pool.execute(f);
        return f;
    }

    /** Submits all tasks and waits for every one of them. Individual failures surface from get(). */
    <T> ArrayList<TaskFuture<T>> invokeAll(ArrayList<Supplier<T>> tasks) {
        ArrayList<TaskFuture<T>> out = new ArrayList<TaskFuture<T>>();
        int i = 0;
        while (i < tasks.size()) {
            out.add(submit(tasks.get(i)));
            i = i + 1;
        }
        i = 0;
        while (i < out.size()) {
            out.get(i).awaitDone();
            i = i + 1;
        }
        return out;
    }

    /** Stop accepting tasks; queued tasks still run. */
    void shutdown() {
        pool.shutdown();
    }

    /** Stop accepting tasks, cancel queued futures, return how many queued tasks were dropped. */
    int shutdownNow() {
        ArrayList<PoolTask> dropped = pool.shutdownNow();
        int i = 0;
        while (i < dropped.size()) {
            PoolTask t = dropped.get(i);
            if (t instanceof Cancellable) {
                Cancellable c = (Cancellable) t;
                c.cancel();
            }
            i = i + 1;
        }
        return dropped.size();
    }

    boolean isShutdown() {
        return pool.isShutdown();
    }

    boolean isTerminated() {
        return pool.isTerminated();
    }

    /** After shutdown(): wait until all workers exited. False on timeout. */
    boolean awaitTermination(long timeoutMs) {
        return pool.awaitTermination(timeoutMs);
    }

    /** Wait until no task is queued or running; the pool stays usable. False on timeout. */
    boolean awaitIdle(long timeoutMs) {
        return pool.awaitIdle(timeoutMs);
    }

    /** shutdown() + wait for all queued tasks to finish (try-with-resources style cleanup). */
    void close() {
        pool.shutdown();
        pool.join();
    }

    int getPoolSize() {
        return pool.workerCount;
    }

    int getActiveCount() {
        return pool.activeWorkers();
    }

    int getQueueSize() {
        return pool.queuedCount();
    }

    long getCompletedTaskCount() {
        return pool.completedTasks();
    }

    long getFailedTaskCount() {
        return pool.failedTasks();
    }

    long getRejectedTaskCount() {
        return pool.rejectedTasks();
    }

    Exception getLastFailure() {
        return pool.lastFailure();
    }

    /** Silence the stderr report for tasks that throw out of execute(); failures are still counted. */
    void setQuiet(boolean quiet) {
        pool.setQuiet(quiet);
    }
}

class Executors {
    static int availableProcessors() {
        int n = ConcCpuNative.cpuCount();
        if (n < 1) {
            return 1;
        }
        return n;
    }

    static ExecutorService newFixedThreadPool(int workers) {
        return new ExecutorService(workers);
    }

    static ExecutorService newSingleThreadExecutor() {
        return new ExecutorService(1);
    }

    /** One worker per logical CPU. */
    static ExecutorService newCpuThreadPool() {
        return new ExecutorService(availableProcessors());
    }

    /** Workers do not keep the process alive; tasks still queued at exit are dropped. */
    static ExecutorService newDaemonThreadPool(int workers) {
        return new ExecutorService(workers, 0, 0, true);
    }

    /** Bounded queue; submit blocks while the queue is full (back-pressure). */
    static ExecutorService newBoundedThreadPool(int workers, int queueCapacity) {
        return new ExecutorService(workers, queueCapacity, ThreadPoolExecutor.REJECT_BLOCK);
    }
}

/** One-shot gate: waitFor() blocks until countDown() was called count times. */
class CountDownLatch {
    Mutex mu;
    Condition zero;
    int count;

    CountDownLatch(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count < 0");
        }
        this.count = count;
        this.mu = new Mutex();
        this.zero = new Condition();
    }

    void countDown() {
        mu.lock();
        if (count > 0) {
            count = count - 1;
            if (count == 0) {
                zero.signalAll();
            }
        }
        mu.unlock();
    }

    int getCount() {
        mu.lock();
        int c = count;
        mu.unlock();
        return c;
    }

    void waitFor() {
        mu.lock();
        while (count > 0) {
            zero.await(mu);
        }
        mu.unlock();
    }

    /** True if the count reached zero within timeoutMs. */
    boolean waitFor(long timeoutMs) {
        long deadline = PoolClockNative.monoMillis() + timeoutMs;
        mu.lock();
        while (count > 0) {
            long left = deadline - PoolClockNative.monoMillis();
            if (left <= 0) {
                mu.unlock();
                return false;
            }
            zero.awaitMillis(mu, left);
        }
        mu.unlock();
        return true;
    }
}

/** Counting semaphore. */
class Semaphore {
    Mutex mu;
    Condition avail;
    int permits;

    Semaphore(int permits) {
        this.permits = permits;
        this.mu = new Mutex();
        this.avail = new Condition();
    }

    void acquire() {
        acquire(1);
    }

    void acquire(int n) {
        mu.lock();
        while (permits < n) {
            avail.await(mu);
        }
        permits = permits - n;
        mu.unlock();
    }

    boolean tryAcquire() {
        return tryAcquire(1);
    }

    boolean tryAcquire(int n) {
        mu.lock();
        boolean ok = permits >= n;
        if (ok) {
            permits = permits - n;
        }
        mu.unlock();
        return ok;
    }

    /** Waits up to timeoutMs for one permit. */
    boolean tryAcquireFor(long timeoutMs) {
        long deadline = PoolClockNative.monoMillis() + timeoutMs;
        mu.lock();
        while (permits < 1) {
            long left = deadline - PoolClockNative.monoMillis();
            if (left <= 0) {
                mu.unlock();
                return false;
            }
            avail.awaitMillis(mu, left);
        }
        permits = permits - 1;
        mu.unlock();
        return true;
    }

    void release() {
        release(1);
    }

    void release(int n) {
        mu.lock();
        permits = permits + n;
        avail.signalAll();
        mu.unlock();
    }

    int availablePermits() {
        mu.lock();
        int p = permits;
        mu.unlock();
        return p;
    }
}

/** Re-entrant mutual exclusion (Mutex itself is not re-entrant). */
class ReentrantLock {
    Mutex mu;
    Condition free;
    long owner;
    int holds;

    ReentrantLock() {
        this.mu = new Mutex();
        this.free = new Condition();
        this.owner = -1;
        this.holds = 0;
    }

    void lock() {
        long me = Thread.currentThread().id();
        mu.lock();
        if (owner == me) {
            holds = holds + 1;
            mu.unlock();
            return;
        }
        while (owner != -1) {
            free.await(mu);
        }
        owner = me;
        holds = 1;
        mu.unlock();
    }

    boolean tryLock() {
        long me = Thread.currentThread().id();
        mu.lock();
        boolean ok = false;
        if (owner == me) {
            holds = holds + 1;
            ok = true;
        } else if (owner == -1) {
            owner = me;
            holds = 1;
            ok = true;
        }
        mu.unlock();
        return ok;
    }

    boolean tryLockFor(long timeoutMs) {
        long me = Thread.currentThread().id();
        long deadline = PoolClockNative.monoMillis() + timeoutMs;
        mu.lock();
        if (owner == me) {
            holds = holds + 1;
            mu.unlock();
            return true;
        }
        while (owner != -1) {
            long left = deadline - PoolClockNative.monoMillis();
            if (left <= 0) {
                mu.unlock();
                return false;
            }
            free.awaitMillis(mu, left);
        }
        owner = me;
        holds = 1;
        mu.unlock();
        return true;
    }

    void unlock() {
        long me = Thread.currentThread().id();
        mu.lock();
        if (owner != me) {
            mu.unlock();
            throw new IllegalStateException("lock is not held by the current thread");
        }
        holds = holds - 1;
        if (holds == 0) {
            owner = -1;
            free.signal();
        }
        mu.unlock();
    }

    boolean isHeldByCurrentThread() {
        long me = Thread.currentThread().id();
        mu.lock();
        boolean r = owner == me;
        mu.unlock();
        return r;
    }

    int getHoldCount() {
        long me = Thread.currentThread().id();
        mu.lock();
        int h = 0;
        if (owner == me) {
            h = holds;
        }
        mu.unlock();
        return h;
    }

    boolean isLocked() {
        mu.lock();
        boolean r = owner != -1;
        mu.unlock();
        return r;
    }
}

/** Reusable rendezvous point for a fixed number of parties. */
class CyclicBarrier {
    Mutex mu;
    Condition cv;
    int parties;
    int arrived;
    int generation;
    Runnable action;

    CyclicBarrier(int parties) {
        setup(parties, null);
    }

    /** action runs once per cycle, on the last-arriving thread, before the others are released. */
    CyclicBarrier(int parties, Runnable action) {
        setup(parties, action);
    }

    private void setup(int parties, Runnable action) {
        if (parties < 1) {
            throw new IllegalArgumentException("parties < 1");
        }
        this.parties = parties;
        this.action = action;
        this.mu = new Mutex();
        this.cv = new Condition();
        this.arrived = 0;
        this.generation = 0;
    }

    /** Returns the arrival index: parties-1 for the first thread to arrive, 0 for the last. */
    int arriveAndWait() {
        mu.lock();
        int gen = generation;
        arrived = arrived + 1;
        int index = parties - arrived;
        if (arrived == parties) {
            if (action != null) {
                action.run();
            }
            arrived = 0;
            generation = generation + 1;
            cv.signalAll();
            mu.unlock();
            return 0;
        }
        while (gen == generation) {
            cv.await(mu);
        }
        mu.unlock();
        return index;
    }

    int getParties() {
        return parties;
    }

    int getNumberWaiting() {
        mu.lock();
        int n = arrived;
        mu.unlock();
        return n;
    }
}

/**
 * Thread-safe FIFO queue, optionally bounded. close() lets consumers drain what is left
 * and then see null from take()/poll(), which makes producer/consumer shutdown simple.
 */
class BlockingQueue<T> {
    Mutex mu;
    Condition notEmpty;
    Condition notFull;
    ArrayDeque<T> items;
    int capacity;
    int closed;

    /** Unbounded. */
    BlockingQueue() {
        setup(0);
    }

    BlockingQueue(int capacity) {
        setup(capacity);
    }

    private void setup(int capacity) {
        this.capacity = capacity;
        this.mu = new Mutex();
        this.notEmpty = new Condition();
        this.notFull = new Condition();
        this.items = new ArrayDeque<T>();
        this.closed = 0;
    }

    /** Blocks while full. Throws IllegalStateException if the queue is closed. */
    void put(T item) {
        mu.lock();
        while (capacity > 0 && items.size() >= capacity && closed == 0) {
            notFull.await(mu);
        }
        if (closed != 0) {
            mu.unlock();
            throw new IllegalStateException("queue is closed");
        }
        items.addLast(item);
        notEmpty.signal();
        mu.unlock();
    }

    /** Non-blocking put; false if full or closed. */
    boolean offer(T item) {
        mu.lock();
        if (closed != 0 || (capacity > 0 && items.size() >= capacity)) {
            mu.unlock();
            return false;
        }
        items.addLast(item);
        notEmpty.signal();
        mu.unlock();
        return true;
    }

    /** Waits up to timeoutMs for space; false on timeout or if closed. */
    boolean offerFor(T item, long timeoutMs) {
        long deadline = PoolClockNative.monoMillis() + timeoutMs;
        mu.lock();
        while (capacity > 0 && items.size() >= capacity && closed == 0) {
            long left = deadline - PoolClockNative.monoMillis();
            if (left <= 0) {
                mu.unlock();
                return false;
            }
            notFull.awaitMillis(mu, left);
        }
        if (closed != 0) {
            mu.unlock();
            return false;
        }
        items.addLast(item);
        notEmpty.signal();
        mu.unlock();
        return true;
    }

    /** Blocks until an item is available. Returns null once the queue is closed and drained. */
    T take() {
        mu.lock();
        while (items.size() == 0 && closed == 0) {
            notEmpty.await(mu);
        }
        if (items.size() == 0) {
            mu.unlock();
            return null;
        }
        T v = items.removeFirst();
        notFull.signal();
        mu.unlock();
        return v;
    }

    /** Non-blocking; null if empty. */
    T poll() {
        mu.lock();
        if (items.size() == 0) {
            mu.unlock();
            return null;
        }
        T v = items.removeFirst();
        notFull.signal();
        mu.unlock();
        return v;
    }

    /** Waits up to timeoutMs; null on timeout or when closed and drained. */
    T pollFor(long timeoutMs) {
        long deadline = PoolClockNative.monoMillis() + timeoutMs;
        mu.lock();
        while (items.size() == 0 && closed == 0) {
            long left = deadline - PoolClockNative.monoMillis();
            if (left <= 0) {
                mu.unlock();
                return null;
            }
            notEmpty.awaitMillis(mu, left);
        }
        if (items.size() == 0) {
            mu.unlock();
            return null;
        }
        T v = items.removeFirst();
        notFull.signal();
        mu.unlock();
        return v;
    }

    int size() {
        mu.lock();
        int n = items.size();
        mu.unlock();
        return n;
    }

    boolean isEmpty() {
        return size() == 0;
    }

    /** Reject further puts and wake every blocked producer/consumer. */
    void close() {
        mu.lock();
        closed = 1;
        notEmpty.signalAll();
        notFull.signalAll();
        mu.unlock();
    }

    boolean isClosed() {
        mu.lock();
        boolean r = closed != 0;
        mu.unlock();
        return r;
    }
}
