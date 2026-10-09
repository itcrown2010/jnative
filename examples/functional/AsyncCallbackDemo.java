/**
 * Async task + Future callbacks on a dedicated EventLoop.
 * Worker pool runs blocking work; completion callbacks run on the loop thread.
 * Build: java -jar target/jnative.jar build examples/functional/AsyncCallbackDemo.java -o build/AsyncCallbackDemo
 */
class Box {
    int n;
    int fails;
    String msg;
}

class WorkSupplier implements BlockingSupplier {
    public Object get() {
        return Integer.valueOf(42);
    }
}

class FailSupplier implements BlockingSupplier {
    public Object get() {
        throw new RuntimeException("boom");
    }
}

public class AsyncCallbackDemo {
    public static void main(String[] args) {
        EventLoop loop = new EventLoop();
        Box box = new Box();
        box.n = 0;
        box.fails = 0;
        box.msg = "";

        JnFuture ok = Async.submit(loop, new WorkSupplier());
        FutureSubscription subOk = ok.onSuccess(v -> {
            box.n = ((Integer) v).intValue();
            box.msg = "完成中文";
        });

        JnFuture bad = Async.submit(loop, new FailSupplier());
        FutureSubscription subBad = bad.onFailure(err -> {
            box.fails = box.fails + 1;
        });

        // Cancel before complete — must not apply 999
        JnFuture pending = JnFuture.createPending(loop);
        FutureSubscription cancelled = pending.onSuccess(v -> {
            box.n = 999;
        });
        cancelled.cancel();
        pending.tryComplete(Integer.valueOf(7));

        // Stop once both submit futures have reported (success + failure paths).
        ok.onSuccess(v -> {
            bad.onFailure(err -> {
                System.out.println(box.n);
                System.out.println(box.msg.length());
                System.out.println(box.fails);
                loop.stop();
            });
        });

        EventLoopRunner runner = new EventLoopRunner();
        runner.loop = loop;
        runner.start();
        runner.join();

        System.gc();
        System.out.println(subOk.isCancelled() ? 0 : 1);
        System.out.println(subBad.isCancelled() ? 0 : 1);
        System.out.println(cancelled.isCancelled() ? 1 : 0);

        Async.shutdownSharedPool();
    }
}
