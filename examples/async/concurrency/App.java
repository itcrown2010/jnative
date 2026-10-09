/**
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        int total = 1000;
        int delayMs = 100;
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
        System.out.println("concurrency total=" + String.valueOf(total));
        System.out.println("delayMs=" + String.valueOf(delayMs));
        System.out.println("wallMs=" + elapsed);
        System.out.println("threads=2");
        System.out.println("serialMs=" + String.valueOf(total * delayMs));
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
