/**
 * Serializes request handlers. The runtime's connection pool semaphore and futures are loop-owned and not
 * thread-safe, so concurrent worker threads calling into the database can corrupt shared state. One handler
 * runs at a time; the event loop thread keeps completing DB futures without taking this lock.
 * @author 张晓达
 */
class ErpLocked implements AsyncWebHandler {
    static Mutex gate;
    AsyncWebHandler inner;

    ErpLocked(AsyncWebHandler inner) {
        this.inner = inner;
    }

    static AsyncWebHandler wrap(AsyncWebHandler h) {
        gate();
        return new ErpLocked(h);
    }

    static Mutex gate() {
        if (gate == null) {
            gate = new Mutex();
        }
        return gate;
    }

    public JnFuture handle(WebContext ctx) {
        Mutex m = gate();
        m.lock();
        JnFuture out = null;
        try {
            out = inner.handle(ctx);
        } catch (Exception ex) {
            m.unlock();
            throw ex;
        }
        m.unlock();
        return out;
    }
}
