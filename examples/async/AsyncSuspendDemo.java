/**
 * Proves the event loop keeps running while an async method is suspended.
 * A pending Future is completed from a timer on the loop; another task runs meanwhile.
 *
 * Build: java -jar target/jnative.jar build examples/async/AsyncSuspendDemo.java -o build/async/AsyncSuspendDemo
 */
class Box {
    int n;

    Box(int n) {
        this.n = n;
    }
}

class TickTask implements LoopTask {
    EventLoop loop;
    Future<Box> pending;
    int[] otherRan;

    public void run() {
        otherRan[0] = otherRan[0] + 1;
        Future.tryComplete(pending, new Box(10));
    }
}

class StopTask implements LoopTask {
    EventLoop loop;

    public void run() {
        loop.stop();
    }
}

public class AsyncSuspendDemo {
    static int before;
    static int after;
    static int other;

    async Box compute(Future<Box> delayed) {
        before = before + 1;
        Box a = await delayed;
        after = after + 1;
        return new Box(a.n + 5);
    }

    public static void main(String[] args) {
        EventLoop loop = new EventLoop();
        Future<Box> delayed = Future.pending();
        AsyncSuspendDemo demo = new AsyncSuspendDemo();

        // Start async method — suspends on incomplete Future (does not block the loop).
        Future<Box> result = demo.compute(delayed);

        int[] otherRan = new int[1];
        otherRan[0] = 0;
        TickTask tick = new TickTask();
        tick.loop = loop;
        tick.pending = delayed;
        tick.otherRan = otherRan;
        // Schedule completion + prove other work runs on the loop.
        loop.scheduleTimer(5, tick);

        StopTask stop = new StopTask();
        stop.loop = loop;
        // After result completes, stop (polled from a short timer chain).
        loop.scheduleTimer(30, stop);

        EventLoopRunner runner = new EventLoopRunner();
        runner.loop = loop;
        runner.start();
        runner.join();

        other = otherRan[0];
        System.out.println(before);
        System.out.println(after);
        System.out.println(other);
        System.out.println(Future.isComplete(result) ? 1 : 0);
        Box v = (Box) Future.getCompleted(result);
        System.out.println(v.n);
    }
}
