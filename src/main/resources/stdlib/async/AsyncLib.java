/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative Async Runtime (0.23) — EventLoop + JnFuture + native IoPoller.
// JnFuture is the stdlib promise type (compiler builtin Future is for async/await ABI).

class AsyncClockNative {
    @Native(symbol = "jnative_web_mono_millis")
    static native long monoMillis();

    @Native(symbol = "jnative_wall_millis")
    static native long wallMillis();
}

class AsyncCpuNative {
    @Native(symbol = "jnative_cpu_count")
    static native int cpuCount();
}

class AsyncCpu {
    static int count() {
        int n = AsyncCpuNative.cpuCount();
        if (n < 1) {
            return 1;
        }
        return n;
    }
}

class PollNative {
    @Native(symbol = "jnative_poll_create")
    static native NativePtr create();

    @Native(symbol = "jnative_poll_destroy")
    static native void destroy(NativePtr p);

    @Native(symbol = "jnative_poll_add")
    static native int add(NativePtr p, int fd, int events);

    @Native(symbol = "jnative_poll_mod")
    static native int mod(NativePtr p, int fd, int events);

    @Native(symbol = "jnative_poll_del")
    static native int del(NativePtr p, int fd);

    @Native(symbol = "jnative_poll_wait")
    static native int waitPoll(NativePtr p, int timeoutMs);

    @Native(symbol = "jnative_poll_last_wakeup")
    static native int lastWakeup(NativePtr p);

    @Native(symbol = "jnative_poll_wakeup")
    static native void wakeup(NativePtr p);

    @Native(symbol = "jnative_poll_registered_count")
    static native int registeredCount(NativePtr p);

    @Native(symbol = "jnative_plat_set_nonblocking")
    static native int setNonblocking(int fd, int nb);
}

class TimeoutException extends RuntimeException {
    TimeoutException(String message) {
        super(message);
    }
}

interface LoopTask {
    void run();
}

interface JnFutureCallback {
    void invoke(JnFuture future);
}

/**
 * Cancellable wrapper around {@link JnFutureCallback}.
 * cancel() is idempotent; a cancelled callback is skipped (at most once otherwise).
 * Does not force-stop a callback already running on the event loop thread.
 * @author 张晓达
 */
class FutureSubscription {
    int cancelled;
    JnFutureCallback inner;

    FutureSubscription() {
        cancelled = 0;
        inner = null;
    }

    boolean isCancelled() {
        return cancelled != 0;
    }

    void cancel() {
        cancelled = 1;
        inner = null;
    }
}

class CancellableFutureCallback implements JnFutureCallback {
    FutureSubscription sub;
    JnFutureCallback inner;
    /** 0=pending, 1=claimed (at most one invoke of inner). */
    int claimed;

    CancellableFutureCallback() {
        sub = null;
        inner = null;
        claimed = 0;
    }

    public void invoke(JnFuture future) {
        if (sub == null || sub.cancelled != 0) {
            return;
        }
        if (claimed != 0) {
            return;
        }
        claimed = 1;
        if (sub.cancelled != 0) {
            return;
        }
        JnFutureCallback cb = inner;
        inner = null;
        if (cb == null) {
            return;
        }
        cb.invoke(future);
    }
}

interface BlockingSupplier {
    Object get();
}

interface FlatMapFunction {
    JnFuture apply(Object value);
}

interface RecoverFunction {
    Object apply(Exception error);
}

class JnCancellationToken {
    int cancelled;
    ArrayList<JnFutureCallback> callbacks;
    Mutex lock;

    JnCancellationToken() {
        cancelled = 0;
        callbacks = new ArrayList<JnFutureCallback>();
        lock = new Mutex();
    }

    boolean isCancelled() {
        return cancelled != 0;
    }

    void throwIfCancelled() {
        if (cancelled != 0) {
            throw new RuntimeException("cancelled");
        }
    }

    void registerCallback(JnFutureCallback cb) {
        if (cb == null) {
            return;
        }
        lock.lock();
        if (cancelled != 0) {
            lock.unlock();
            cb.invoke(null);
            return;
        }
        callbacks.add(cb);
        lock.unlock();
    }

    void fireCancel() {
        lock.lock();
        if (cancelled != 0) {
            lock.unlock();
            return;
        }
        cancelled = 1;
        ArrayList<JnFutureCallback> copy = new ArrayList<JnFutureCallback>();
        int i = 0;
        while (i < callbacks.size()) {
            copy.add(callbacks.get(i));
            i = i + 1;
        }
        lock.unlock();
        i = 0;
        while (i < copy.size()) {
            JnFutureCallback cb = copy.get(i);
            cb.invoke(null);
            i = i + 1;
        }
    }
}

class CancellationSource {
    JnCancellationToken token;

    CancellationSource() {
        token = new JnCancellationToken();
    }

    JnCancellationToken getToken() {
        return token;
    }

    void cancel() {
        token.fireCancel();
    }
}

class JnFuture {
    static int PENDING = 0;
    static int SUCCESS = 1;
    static int FAILED = 2;
    static int CANCELLED = 3;

    int state;
    Object value;
    Exception error;
    EventLoop loop;
    ArrayList<JnFutureCallback> callbacks;
    Mutex lock;

    JnFuture() {
        state = PENDING;
        value = null;
        error = null;
        loop = null;
        callbacks = new ArrayList<JnFutureCallback>();
        lock = new Mutex();
    }

    static JnFuture createPending(EventLoop loop) {
        JnFuture f = new JnFuture();
        f.loop = loop;
        return f;
    }

    static JnFuture completed(EventLoop loop, Object value) {
        JnFuture f = new JnFuture();
        f.loop = loop;
        f.state = SUCCESS;
        f.value = value;
        return f;
    }

    static JnFuture failed(EventLoop loop, Exception error) {
        JnFuture f = new JnFuture();
        f.loop = loop;
        f.state = FAILED;
        f.error = error;
        return f;
    }

    int getState() {
        return state;
    }

    boolean isDone() {
        return state != PENDING;
    }

    Object getValue() {
        return value;
    }

    Exception getError() {
        return error;
    }

    boolean tryComplete(Object v) {
        lock.lock();
        if (state != PENDING) {
            lock.unlock();
            return false;
        }
        state = SUCCESS;
        value = v;
        ArrayList<JnFutureCallback> pending = callbacks;
        callbacks = new ArrayList<JnFutureCallback>();
        lock.unlock();
        dispatchCallbacks(pending);
        return true;
    }

    boolean tryFail(Exception ex) {
        lock.lock();
        if (state != PENDING) {
            lock.unlock();
            return false;
        }
        state = FAILED;
        error = ex;
        ArrayList<JnFutureCallback> pending = callbacks;
        callbacks = new ArrayList<JnFutureCallback>();
        lock.unlock();
        dispatchCallbacks(pending);
        return true;
    }

    boolean tryCancel() {
        lock.lock();
        if (state != PENDING) {
            lock.unlock();
            return false;
        }
        state = CANCELLED;
        ArrayList<JnFutureCallback> pending = callbacks;
        callbacks = new ArrayList<JnFutureCallback>();
        lock.unlock();
        dispatchCallbacks(pending);
        return true;
    }

    void onComplete(JnFutureCallback cb) {
        if (cb == null) {
            throw new NullPointerException("callback");
        }
        lock.lock();
        if (state != PENDING) {
            lock.unlock();
            dispatchOneCallback(cb);
            return;
        }
        callbacks.add(cb);
        lock.unlock();
    }

    /**
     * Register a cancellable completion callback.
     * If already done, the callback is scheduled/invoked immediately (unless cancelled first).
     * Thread: when {@code loop} is running, callbacks run on that event-loop thread; otherwise
     * they run on the completing thread.
     */
    FutureSubscription subscribe(JnFutureCallback cb) {
        if (cb == null) {
            throw new NullPointerException("callback");
        }
        FutureSubscription sub = new FutureSubscription();
        CancellableFutureCallback wrap = new CancellableFutureCallback();
        wrap.sub = sub;
        wrap.inner = cb;
        sub.inner = wrap;
        onComplete(wrap);
        return sub;
    }

    /** Success path only; failures/cancel skip the consumer. */
    FutureSubscription onSuccess(Consumer<Object> consumer) {
        if (consumer == null) {
            throw new NullPointerException("consumer");
        }
        SuccessCallback cb = new SuccessCallback();
        cb.consumer = consumer;
        return subscribe(cb);
    }

    /** Failure path only (FAILED state); SUCCESS/CANCELLED skip. */
    FutureSubscription onFailure(Consumer<Object> consumer) {
        if (consumer == null) {
            throw new NullPointerException("consumer");
        }
        FailureCallback cb = new FailureCallback();
        cb.consumer = consumer;
        return subscribe(cb);
    }

    void dispatchOneCallback(JnFutureCallback cb) {
        // Queue onto the loop only while it is running. Completions before EventLoop.run()
        // (e.g. ErpDb.setup + FutureBlocker.blockingWait) must invoke inline — otherwise
        // callbacks sit forever in the ready queue and blockingWait deadlocks.
        // stop()-before-run still drains via EventLoop.run()'s processReady path.
        if (loop != null && loop.running != 0) {
            RunCallbackTask t = new RunCallbackTask();
            t.cb = cb;
            t.future = this;
            loop.execute(t);
        } else {
            cb.invoke(this);
        }
    }

    void dispatchCallbacks(ArrayList<JnFutureCallback> pending) {
        int i = 0;
        while (i < pending.size()) {
            JnFutureCallback cb = pending.get(i);
            dispatchOneCallback(cb);
            i = i + 1;
        }
    }

    JnFuture then(Function<Object, Object> fn) {
        JnFuture next = JnFuture.createPending(loop);
        ThenCallback cb = new ThenCallback();
        cb.source = this;
        cb.next = next;
        cb.fn = fn;
        onComplete(cb);
        return next;
    }

    JnFuture map(Function<Object, Object> fn) {
        return then(fn);
    }

    JnFuture flatMap(FlatMapFunction fn) {
        JnFuture next = JnFuture.createPending(loop);
        FlatMapCallback cb = new FlatMapCallback();
        cb.source = this;
        cb.next = next;
        cb.fn = fn;
        onComplete(cb);
        return next;
    }

    JnFuture recover(RecoverFunction fn) {
        JnFuture next = JnFuture.createPending(loop);
        RecoverCallback cb = new RecoverCallback();
        cb.source = this;
        cb.next = next;
        cb.fn = fn;
        onComplete(cb);
        return next;
    }

    JnFuture finallyDo(Consumer<Object> fn) {
        JnFuture next = JnFuture.createPending(loop);
        FinallyCallback cb = new FinallyCallback();
        cb.source = this;
        cb.next = next;
        cb.fn = fn;
        onComplete(cb);
        return next;
    }

    static JnFuture all(ArrayList<JnFuture> futures) {
        JnFuture out = new JnFuture();
        if (futures == null || futures.size() == 0) {
            out.tryComplete(null);
            return out;
        }
        JnFuture first = futures.get(0);
        out.loop = first.loop;
        AllWaiter waiter = new AllWaiter();
        waiter.target = out;
        waiter.total = futures.size();
        waiter.done = 0;
        waiter.failed = 0;
        int i = 0;
        while (i < futures.size()) {
            JnFuture f = futures.get(i);
            f.onComplete(waiter);
            i = i + 1;
        }
        return out;
    }

    static JnFuture any(ArrayList<JnFuture> futures) {
        JnFuture out = new JnFuture();
        if (futures == null || futures.size() == 0) {
            out.tryFail(new RuntimeException("JnFuture.any: empty"));
            return out;
        }
        JnFuture first = futures.get(0);
        out.loop = first.loop;
        AnyRace race = new AnyRace();
        race.target = out;
        race.won = 0;
        int i = 0;
        while (i < futures.size()) {
            JnFuture f = futures.get(i);
            f.onComplete(race);
            i = i + 1;
        }
        return out;
    }

    static JnFuture timeout(JnFuture source, int ms, EventLoop loop) {
        JnFuture out = JnFuture.createPending(loop);
        TimeoutPair pair = new TimeoutPair();
        pair.out = out;
        pair.source = source;
        pair.fired = 0;
        source.onComplete(new TimeoutSourceCallback(pair));
        TimeoutFireTask fire = new TimeoutFireTask();
        fire.pair = pair;
        fire.ms = ms;
        loop.scheduleTimer(ms, fire);
        return out;
    }
}

class SuccessCallback implements JnFutureCallback {
    Consumer<Object> consumer;

    SuccessCallback() {
        consumer = null;
    }

    public void invoke(JnFuture future) {
        if (future.getState() == JnFuture.SUCCESS) {
            consumer.accept(future.getValue());
        }
    }
}

class FailureCallback implements JnFutureCallback {
    Consumer<Object> consumer;

    FailureCallback() {
        consumer = null;
    }

    public void invoke(JnFuture future) {
        if (future.getState() == JnFuture.FAILED) {
            consumer.accept(future.getError());
        }
    }
}

class RunCallbackTask implements LoopTask {
    JnFutureCallback cb;
    JnFuture future;

    RunCallbackTask() {
        cb = null;
        future = null;
    }

    public void run() {
        if (cb != null) {
            cb.invoke(future);
        }
    }
}

class ThenCallback implements JnFutureCallback {
    JnFuture source;
    JnFuture next;
    Function<Object, Object> fn;

    ThenCallback() {
        source = null;
        next = null;
        fn = null;
    }

    public void invoke(JnFuture ignored) {
        if (source.state == JnFuture.SUCCESS) {
            try {
                Object v = fn.apply(source.value);
                next.tryComplete(v);
            } catch (RuntimeException ex) {
                next.tryFail(ex);
            }
        } else if (source.state == JnFuture.FAILED) {
            next.tryFail(source.error);
        } else if (source.state == JnFuture.CANCELLED) {
            next.tryCancel();
        }
    }
}

class FlatMapCallback implements JnFutureCallback {
    JnFuture source;
    JnFuture next;
    FlatMapFunction fn;

    FlatMapCallback() {
        source = null;
        next = null;
        fn = null;
    }

    public void invoke(JnFuture ignored) {
        if (source.state == JnFuture.SUCCESS) {
            try {
                JnFuture inner = fn.apply(source.value);
                ForwardCallback fwd = new ForwardCallback();
                fwd.next = next;
                inner.onComplete(fwd);
            } catch (RuntimeException ex) {
                next.tryFail(ex);
            }
        } else if (source.state == JnFuture.FAILED) {
            next.tryFail(source.error);
        } else if (source.state == JnFuture.CANCELLED) {
            next.tryCancel();
        }
    }
}

class ForwardCallback implements JnFutureCallback {
    JnFuture next;

    ForwardCallback() {
        next = null;
    }

    public void invoke(JnFuture inner) {
        if (inner.state == JnFuture.SUCCESS) {
            next.tryComplete(inner.value);
        } else if (inner.state == JnFuture.FAILED) {
            next.tryFail(inner.error);
        } else if (inner.state == JnFuture.CANCELLED) {
            next.tryCancel();
        }
    }
}

class RecoverCallback implements JnFutureCallback {
    JnFuture source;
    JnFuture next;
    RecoverFunction fn;

    RecoverCallback() {
        source = null;
        next = null;
        fn = null;
    }

    public void invoke(JnFuture ignored) {
        if (source.state == JnFuture.SUCCESS) {
            next.tryComplete(source.value);
        } else if (source.state == JnFuture.FAILED) {
            try {
                Object v = fn.apply(source.error);
                next.tryComplete(v);
            } catch (RuntimeException ex) {
                next.tryFail(ex);
            }
        } else if (source.state == JnFuture.CANCELLED) {
            next.tryCancel();
        }
    }
}

class FinallyCallback implements JnFutureCallback {
    JnFuture source;
    JnFuture next;
    Consumer<Object> fn;

    FinallyCallback() {
        source = null;
        next = null;
        fn = null;
    }

    public void invoke(JnFuture ignored) {
        try {
            fn.accept(source);
        } catch (RuntimeException ex) {
            if (source.state == JnFuture.SUCCESS) {
                next.tryFail(ex);
                return;
            }
        }
        if (source.state == JnFuture.SUCCESS) {
            next.tryComplete(source.value);
        } else if (source.state == JnFuture.FAILED) {
            next.tryFail(source.error);
        } else if (source.state == JnFuture.CANCELLED) {
            next.tryCancel();
        }
    }
}

class AllWaiter implements JnFutureCallback {
    JnFuture target;
    int total;
    int done;
    int failed;

    public void invoke(JnFuture f) {
        done = done + 1;
        if (f.state == JnFuture.FAILED && failed == 0) {
            failed = 1;
            target.tryFail(f.error);
            return;
        }
        if (f.state == JnFuture.CANCELLED && failed == 0) {
            failed = 1;
            target.tryCancel();
            return;
        }
        if (done >= total && failed == 0) {
            target.tryComplete(null);
        }
    }
}

class AnyRace implements JnFutureCallback {
    JnFuture target;
    int won;

    public void invoke(JnFuture f) {
        if (won != 0) {
            return;
        }
        if (f.state == JnFuture.SUCCESS) {
            won = 1;
            target.tryComplete(f.value);
        } else if (f.state == JnFuture.FAILED) {
            won = 1;
            target.tryFail(f.error);
        } else if (f.state == JnFuture.CANCELLED) {
            won = 1;
            target.tryCancel();
        }
    }
}

class TimeoutPair {
    JnFuture out;
    JnFuture source;
    int fired;
}

class TimeoutSourceCallback implements JnFutureCallback {
    TimeoutPair pair;

    TimeoutSourceCallback(TimeoutPair pair) {
        this.pair = pair;
    }

    public void invoke(JnFuture ignored) {
        if (pair.fired != 0) {
            return;
        }
        pair.fired = 1;
        if (pair.source.state == JnFuture.SUCCESS) {
            pair.out.tryComplete(pair.source.value);
        } else if (pair.source.state == JnFuture.FAILED) {
            pair.out.tryFail(pair.source.error);
        } else if (pair.source.state == JnFuture.CANCELLED) {
            pair.out.tryCancel();
        }
    }
}

class TimeoutFireTask implements LoopTask {
    TimeoutPair pair;
    int ms;

    TimeoutFireTask() {
        pair = null;
        ms = 0;
    }

    public void run() {
        if (pair.fired != 0) {
            return;
        }
        if (pair.source.isDone()) {
            return;
        }
        pair.fired = 1;
        pair.source.tryCancel();
        String msg = "timeout after " + String.valueOf(ms) + "ms";
        pair.out.tryFail(new TimeoutException(msg));
    }
}

class TimerEntry {
    long deadlineMs;
    int id;
    LoopTask task;
    int cancelled;

    TimerEntry() {
        cancelled = 0;
    }
}

class EventLoop {
    ArrayDeque<LoopTask> ready;
    Mutex readyLock;
    ArrayList<TimerEntry> timers;
    int nextTimerId;
    NativePtr poll;
    int pollInit;
    int running;
    /** Sticky: stop() before run() must not be overwritten by run(). */
    int stopRequested;
    int activeConnections;
    int pollWakeups;
    long loopThreadId;
    EventLoop() {
        ready = new ArrayDeque<LoopTask>();
        readyLock = new Mutex();
        timers = new ArrayList<TimerEntry>();
        nextTimerId = 1;
        poll = NativePtr.NULL;
        pollInit = 0;
        running = 0;
        stopRequested = 0;
        activeConnections = 0;
        pollWakeups = 0;
        loopThreadId = 0;
    }

    boolean isLoopThread() {
        if (loopThreadId == 0) {
            return false;
        }
        return Thread.currentThread().id() == loopThreadId;
    }

    void ensurePoll() {
        if (pollInit != 0) {
            return;
        }
        NativePtr created = PollNative.create();
        if (created == NativePtr.NULL) {
            return;
        }
        poll = created;
        pollInit = 1;
    }

    int readyQueueSize() {
        return ready.size();
    }

    int timerCount() {
        return timers.size();
    }

    int getActiveConnections() {
        return activeConnections;
    }

    int getPollWakeups() {
        return pollWakeups;
    }

    void execute(LoopTask task) {
        if (task == null) {
            return;
        }
        ensurePoll();
        readyLock.lock();
        ready.addLast(task);
        readyLock.unlock();
        if (pollInit != 0) {
            PollNative.wakeup(poll);
        }
    }

    void scheduleTimer(int delayMs, LoopTask task) {
        if (task == null) {
            return;
        }
        TimerEntry timerItem = new TimerEntry();
        timerItem.deadlineMs = AsyncClockNative.monoMillis() + delayMs;
        timerItem.id = nextTimerId;
        nextTimerId = nextTimerId + 1;
        timerItem.task = task;
        insertTimer(timerItem);
        ensurePoll();
        if (pollInit != 0) {
            PollNative.wakeup(poll);
        }
    }

    void insertTimer(TimerEntry timerItem) {
        int pos = 0;
        int done = 0;
        while (done == 0 && pos < timers.size()) {
            TimerEntry t = timers.get(pos);
            if (timerItem.deadlineMs < t.deadlineMs) {
                done = 1;
            } else {
                pos = pos + 1;
            }
        }
        timers.addAt(pos, timerItem);
    }

    void processReady() {
        int limit = ready.size() + 64;
        int n = 0;
        while (n < limit) {
            readyLock.lock();
            LoopTask task = null;
            if (ready.size() > 0) {
                task = ready.removeFirst();
            }
            readyLock.unlock();
            if (task == null) {
                n = limit;
            } else {
                task.run();
                n = n + 1;
            }
        }
    }

    void processTimers() {
        long now = AsyncClockNative.monoMillis();
        int i = 0;
        int done = 0;
        while (done == 0 && i < timers.size()) {
            TimerEntry t = timers.get(i);
            if (t.deadlineMs > now) {
                done = 1;
            } else {
                timers.removeAt(i);
                if (t.cancelled == 0) {
                    t.task.run();
                }
            }
        }
    }

    int nextTimerDelayMs() {
        if (timers.size() == 0) {
            return -1;
        }
        TimerEntry t = timers.get(0);
        long now = AsyncClockNative.monoMillis();
        long d = t.deadlineMs - now;
        if (d <= 0) {
            return 0;
        }
        if (d > 1000) {
            return 1000;
        }
        if (d > 0) {
            return 1;
        }
        return 0;
    }

    void pollOnce() {
        ensurePoll();
        if (pollInit == 0) {
            return;
        }
        int delay = nextTimerDelayMs();
        PollNative.waitPoll(poll, delay);
        if (PollNative.lastWakeup(poll) != 0) {
            pollWakeups = pollWakeups + 1;
        }
    }

    void tick() {
        processReady();
        processTimers();
        pollOnce();
    }

    void run() {
        loopThreadId = Thread.currentThread().id();
        if (stopRequested != 0) {
            // stop() won the race before run(); drain queued completions once.
            processReady();
            loopThreadId = 0;
            return;
        }
        running = 1;
        while (running != 0) {
            tick();
        }
        // After stop(): drain already-queued ready tasks once (timers not auto-fired).
        processReady();
        loopThreadId = 0;
    }

    /**
     * Request loop exit. Does not cancel in-flight callback bodies.
     * Queued ready tasks are drained once when {@link #run()} observes the stop.
     * Safe if called before {@link #run()} (sticky stopRequested).
     */
    void stop() {
        stopRequested = 1;
        running = 0;
        if (pollInit != 0) {
            PollNative.wakeup(poll);
        }
    }

    void registerFd(int fd, int events) {
        PollNative.add(poll, fd, events);
        activeConnections = PollNative.registeredCount(poll);
    }

    void unregisterFd(int fd) {
        PollNative.del(poll, fd);
        activeConnections = PollNative.registeredCount(poll);
    }
}

class EventLoopRunner extends Thread {
    EventLoop loop;

    public void run() {
        loop.run();
    }
}

class EventLoopGroup {
    EventLoop[] loops;
    int nextIdx;
    EventLoopRunner[] runners;

    EventLoopGroup() {
        nextIdx = 0;
    }

    static EventLoopGroup createDefault() {
        int n = AsyncCpu.count();
        EventLoopGroup g = new EventLoopGroup();
        g.loops = new EventLoop[n];
        g.runners = new EventLoopRunner[n];
        int i = 0;
        while (i < n) {
            g.loops[i] = new EventLoop();
            i = i + 1;
        }
        return g;
    }

    EventLoop next() {
        EventLoop l = loops[nextIdx];
        nextIdx = nextIdx + 1;
        if (nextIdx >= loops.length) {
            nextIdx = 0;
        }
        return l;
    }

    void runAllInBackground() {
        int i = 0;
        while (i < loops.length) {
            EventLoopRunner r = new EventLoopRunner();
            r.loop = loops[i];
            runners[i] = r;
            r.start();
            i = i + 1;
        }
    }

    void joinAll() {
        int i = 0;
        while (i < runners.length) {
            if (runners[i] != null) {
                runners[i].join();
            }
            i = i + 1;
        }
    }
}

class Async {
    static EventLoopGroup defaultGroup;
    static ThreadPoolExecutor sharedPool;
    static int poolInit;

    static void ensurePool() {
        if (poolInit != 0) {
            return;
        }
        sharedPool = ThreadPoolExecutor.createDefault();
        poolInit = 1;
    }

    static EventLoop defaultLoop() {
        if (defaultGroup == null) {
            defaultGroup = EventLoopGroup.createDefault();
        }
        return defaultGroup.next();
    }

    static JnFuture sleepMs(int ms) {
        return sleepOnLoop(defaultLoop(), ms);
    }

    static JnFuture sleepOnLoop(EventLoop loop, int ms) {
        JnFuture f = JnFuture.createPending(loop);
        SleepCompleteTask task = new SleepCompleteTask();
        task.target = f;
        loop.scheduleTimer(ms, task);
        return f;
    }

    static JnFuture blockingRun(BlockingSupplier supplier) {
        return blockingOnLoop(defaultLoop(), supplier);
    }

    static JnFuture blockingOnLoop(EventLoop loop, BlockingSupplier supplier) {
        JnFuture f = JnFuture.createPending(loop);
        ensurePool();
        BlockingRunTask task = new BlockingRunTask();
        task.loop = loop;
        task.future = f;
        task.supplier = supplier;
        sharedPool.execute(task);
        return f;
    }

    /**
     * Run {@code work} on the shared thread pool; complete {@code JnFuture} on {@code loop}.
     * This is a <b>blocking adapter</b> path (worker thread), not non-blocking I/O.
     * Cancel via {@link JnFuture#tryCancel} only affects observers — does not stop {@code work}.
     */
    static JnFuture submit(EventLoop loop, BlockingSupplier work) {
        return blockingOnLoop(loop, work);
    }

    /** Post {@code task} onto {@code loop} (same thread as other loop callbacks). */
    static void runOnLoop(EventLoop loop, LoopTask task) {
        if (loop == null) {
            throw new NullPointerException("loop");
        }
        if (task == null) {
            throw new NullPointerException("task");
        }
        loop.execute(task);
    }

    /**
     * Shut down the shared blocking pool and join workers.
     * Call before process exit so {@code jn_thread_join_remaining} does not hang
     * on non-daemon pool threads blocked in {@code Condition.await}.
     */
    static void shutdownSharedPool() {
        if (poolInit == 0 || sharedPool == null) {
            return;
        }
        sharedPool.shutdown();
        sharedPool.join();
    }
}

class SleepCompleteTask implements LoopTask {
    JnFuture target;

    SleepCompleteTask() {
        target = null;
    }

    public void run() {
        if (target != null) {
            target.tryComplete(null);
        }
    }
}

class BlockingRunTask implements PoolTask {
    EventLoop loop;
    JnFuture future;
    BlockingSupplier supplier;

    BlockingRunTask() {
        loop = null;
        future = null;
        supplier = null;
    }

    public void run() {
        try {
            Object v = supplier.get();
            completeBlockingFuture(v, null);
        } catch (RuntimeException ex) {
            completeBlockingFuture(null, ex);
        }
    }

    void completeBlockingFuture(Object value, Exception error) {
        // Only marshal onto the loop while it is running. Before EventLoop.run(),
        // complete inline so FutureBlocker.blockingWait (DB migrate/seed, tests) can
        // observe done without pumping the loop.
        if (loop != null && loop.running != 0) {
            BlockingCompleteTask done = new BlockingCompleteTask();
            done.future = future;
            done.value = value;
            done.error = error;
            loop.execute(done);
        } else {
            if (error != null) {
                future.tryFail(error);
            } else {
                future.tryComplete(value);
            }
        }
    }
}

class BlockingCompleteTask implements LoopTask {
    JnFuture future;
    Object value;
    Exception error;

    BlockingCompleteTask() {
        future = null;
        value = null;
        error = null;
    }

    public void run() {
        if (error != null) {
            future.tryFail(error);
        } else {
            future.tryComplete(value);
        }
    }
}

class AsyncMutex {
    int locked;
    ArrayList<JnFuture> waiters;
    EventLoop loop;

    AsyncMutex(EventLoop loop) {
        this.loop = loop;
        locked = 0;
        waiters = new ArrayList<JnFuture>();
    }

    JnFuture lock() {
        JnFuture f = JnFuture.createPending(loop);
        if (locked == 0) {
            locked = 1;
            f.tryComplete(null);
            return f;
        }
        waiters.add(f);
        return f;
    }

    void unlock() {
        if (waiters.size() > 0) {
            JnFuture next = waiters.removeAt(0);
            next.tryComplete(null);
        } else {
            locked = 0;
        }
    }
}

class AsyncSemaphore {
    int permits;
    ArrayList<JnFuture> waiters;
    EventLoop loop;

    AsyncSemaphore(EventLoop loop, int permits) {
        this.loop = loop;
        this.permits = permits;
        waiters = new ArrayList<JnFuture>();
    }

    JnFuture acquire() {
        JnFuture f = JnFuture.createPending(loop);
        if (permits > 0) {
            permits = permits - 1;
            f.tryComplete(null);
            return f;
        }
        waiters.add(f);
        return f;
    }

    void release() {
        if (waiters.size() > 0) {
            JnFuture next = waiters.removeAt(0);
            next.tryComplete(null);
        } else {
            permits = permits + 1;
        }
    }
}

class AsyncQueue {
    ArrayDeque<Object> storage;
    ArrayList<JnFuture> takers;
    EventLoop loop;

    AsyncQueue(EventLoop loop) {
        this.loop = loop;
        storage = new ArrayDeque<Object>();
        takers = new ArrayList<JnFuture>();
    }

    void put(Object value) {
        if (takers.size() > 0) {
            JnFuture f = takers.removeAt(0);
            f.tryComplete(value);
        } else {
            storage.addLast(value);
        }
    }

    JnFuture take() {
        JnFuture f = JnFuture.createPending(loop);
        if (storage.size() > 0) {
            Object v = storage.removeFirst();
            f.tryComplete(v);
            return f;
        }
        takers.add(f);
        return f;
    }
}

class AsyncTcpConnectSupplier implements BlockingSupplier {
    EventLoop loop;
    String host;
    int port;

    AsyncTcpConnectSupplier(EventLoop loop, String host, int port) {
        this.loop = loop;
        this.host = host;
        this.port = port;
    }

    public Object get() {
        TcpSocket sock = TcpSocket.connect(host, port);
        AsyncTcpSocket out = new AsyncTcpSocket();
        out.loop = loop;
        out.socket = sock;
        return out;
    }
}

// RH-1 truth: AsyncTcpSocket is a BLOCKING-ADAPTER over TcpSocket, NOT event-driven I/O.
// Every connect/read/write runs on Async.sharedPool via Async.blockingOnLoop.
// Waiting for socket data occupies a pool worker thread. EventLoop PollNative/kqueue
// is NOT used for these fds (EventLoop.registerFd has no production callers).
// Do not market as non-blocking async networking. Release tier: Experimental.
class AsyncTcpSocket {
    EventLoop loop;
    TcpSocket socket;

    // Blocking connect on the shared pool; completes as a JnFuture.
    static JnFuture connect(EventLoop loop, String host, int port) {
        return Async.blockingOnLoop(loop, new AsyncTcpConnectSupplier(loop, host, port));
    }

    // Alias for connect — still a blocking-adapter (not fd-async).
    static JnFuture connectAsync(EventLoop loop, String host, int port) {
        return AsyncTcpSocket.connect(loop, host, port);
    }

    // Blocking write on the shared pool.
    JnFuture writeAll(byte[] data) {
        AsyncTcpWriteSupplier sup = new AsyncTcpWriteSupplier();
        sup.socket = socket;
        sup.data = data;
        return Async.blockingOnLoop(loop, sup);
    }

    // Alias for writeAll — blocking-adapter.
    JnFuture writeAsync(byte[] data) {
        return writeAll(data);
    }

    // Blocking read-to-EOF on the shared pool.
    JnFuture readAll() {
        AsyncTcpReadAllSupplier sup = new AsyncTcpReadAllSupplier();
        sup.socket = socket;
        return Async.blockingOnLoop(loop, sup);
    }

    // Alias for readAll — blocking-adapter.
    JnFuture readAsync() {
        return readAll();
    }

    void close() {
        if (socket != null) {
            socket.close();
        }
    }
}

class AsyncTcpWriteSupplier implements BlockingSupplier {
    TcpSocket socket;
    byte[] data;

    public Object get() {
        socket.write(data);
        return null;
    }
}

class AsyncTcpReadAllSupplier implements BlockingSupplier {
    TcpSocket socket;

    public Object get() {
        return socket.readAllString();
    }
}

class AsyncTcpAcceptSupplier implements BlockingSupplier {
    EventLoop loop;
    TcpServer server;

    AsyncTcpAcceptSupplier(EventLoop loop, TcpServer server) {
        this.loop = loop;
        this.server = server;
    }

    public Object get() {
        TcpSocket sock = server.accept();
        AsyncTcpSocket out = new AsyncTcpSocket();
        out.loop = loop;
        out.socket = sock;
        return out;
    }
}

class AsyncTcpServer {
    EventLoop loop;
    TcpServer server;

    static JnFuture listen(EventLoop loop, String host, int port) {
        AsyncTcpListenSupplier sup = new AsyncTcpListenSupplier();
        sup.loop = loop;
        sup.host = host;
        sup.port = port;
        return Async.blockingOnLoop(loop, sup);
    }

    JnFuture accept() {
        return Async.blockingOnLoop(loop, new AsyncTcpAcceptSupplier(loop, server));
    }

    void close() {
        server.close();
    }
}

class FutureBlocker {
    static void blockingWait(JnFuture f) {
        if (f == null || f.isDone()) {
            return;
        }
        int alive = 1;
        while (alive != 0) {
            if (f.isDone()) {
                alive = 0;
            } else {
                /* Drain ready tasks if wait races a still-idle loop (queued completions). */
                if (f.loop != null && f.loop.running == 0) {
                    f.loop.processReady();
                }
                if (f.isDone()) {
                    alive = 0;
                } else {
                    Thread.sleep(1);
                }
            }
        }
    }

    static Object blockingGet(JnFuture f) {
        blockingWait(f);
        if (f.getState() == JnFuture.FAILED) {
            throw f.getError();
        }
        if (f.getState() == JnFuture.CANCELLED) {
            throw new RuntimeException("future cancelled");
        }
        return f.getValue();
    }
}

class AsyncTcpListenSupplier implements BlockingSupplier {
    EventLoop loop;
    String host;
    int port;

    public Object get() {
        AsyncTcpServer out = new AsyncTcpServer();
        out.loop = loop;
        out.server = TcpServer.listen(host, port);
        return out;
    }
}
