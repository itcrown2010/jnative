/**
 * @author 张晓达
 */
public class App {
    public static void main(String[] args) {
        System.out.println("sleep scheduled");
        EventLoop loop = new EventLoop();
        int delayMs = 500;
        JnFuture slept = Async.sleepOnLoop(loop, delayMs);
        SleepDoneCb done = new SleepDoneCb();
        done.loop = loop;
        done.delayMs = delayMs;
        slept.onComplete(done);
        loop.run();
        System.out.println("loop stopped");
    }
}

class SleepDoneCb implements JnFutureCallback {
    EventLoop loop;
    int delayMs;

    SleepDoneCb() {
        loop = null;
        delayMs = 0;
    }

    public void invoke(JnFuture f) {
        System.out.println("Async.sleep completed after " + String.valueOf(delayMs) + "ms");
        System.out.println("wakeups=" + String.valueOf(loop.getPollWakeups()));
        loop.stop();
    }
}
