// jnative.browser — wasm32-browser exception / finally / TWR stress
/**
 * @author 张晓达
 */
class Boom extends RuntimeException {
    Boom(String message) {
        super(message);
    }
}

class SoftBoom extends Boom {
    SoftBoom(String message) {
        super(message);
    }
}

interface ThrowingOp {
    int run();
}

class BoomOp implements ThrowingOp {
    public int run() {
        throw new Boom("iface");
    }
}

class Box implements AutoCloseable {
    static int closeLog;
    int id;
    int failClose;

    Box(int id) {
        this.id = id;
        this.failClose = 0;
    }

    Box(int id, int failClose) {
        this.id = id;
        this.failClose = failClose;
    }

    public void close() {
        closeLog = closeLog * 10 + id;
        if (failClose != 0) {
            throw new Boom("close-" + id);
        }
    }
}

class Hold {
    int v;
    Hold(int v) {
        this.v = v;
    }
}

public class ExcStress {
    static int side;
    static Hold retained;

    public static void main(String[] args) {
        boolean keep = false;
        if (keep) {
            catchExact();
            catchSuper();
            propagate();
            rethrowCatch();
            throwNullIsNpe();
            finallyPaths(0);
            finallyPaths(1);
            finallyPaths(2);
            nestedFinally();
            twrOrder();
            twrInitFail();
            twrSuppress();
            cleanupGc();
            ifaceThrow();
            closureThrow();
            pendingAfterLeave();
        }
    }

    /** Exact type catch. */
    public static int catchExact() {
        try {
            throw new SoftBoom("x");
        } catch (SoftBoom e) {
            return 1;
        }
    }

    /** Parent-type catch. */
    public static int catchSuper() {
        try {
            throw new SoftBoom("x");
        } catch (RuntimeException e) {
            return 2;
        }
    }

    static void deep() {
        throw new Boom("deep");
    }

    static void mid() {
        deep();
    }

    /** Cross-function propagation. */
    public static int propagate() {
        try {
            mid();
            return 0;
        } catch (Boom e) {
            return 3;
        }
    }

    /** catch then rethrow unmatched. */
    public static int rethrowCatch() {
        try {
            try {
                throw new Boom("r");
            } catch (Boom e) {
                throw e;
            }
        } catch (Boom e2) {
            return 4;
        }
    }

    /** throw null → NPE (language rule). */
    public static int throwNullIsNpe() {
        try {
            throw null;
        } catch (NullPointerException e) {
            return 5;
        } catch (RuntimeException e) {
            return -5;
        }
    }

    /**
     * mode 0: normal; 1: return through finally; 2: throw through finally.
     * side accumulates finally marker (9).
     */
    public static int finallyPaths(int mode) {
        side = 0;
        try {
            if (mode == 1) {
                return finish(10);
            }
            if (mode == 2) {
                throw new Boom("f");
            }
            side = 1;
        } catch (Boom e) {
            side = side + 2;
        } finally {
            side = side + 9;
        }
        return side;
    }

    static int finish(int v) {
        return v;
    }

    /** Nested finally order: inner then outer markers. */
    public static int nestedFinally() {
        side = 0;
        try {
            try {
                throw new Boom("n");
            } finally {
                side = side * 10 + 1;
            }
        } catch (Boom e) {
            side = side * 10 + 2;
        } finally {
            side = side * 10 + 3;
        }
        return side;
    }

    /** TWR reverse close order. */
    public static int twrOrder() {
        Box.closeLog = 0;
        try (Box a = new Box(1); Box b = new Box(2)) {
            return 0;
        } finally {
            return Box.closeLog; // 21
        }
    }

    /** Second resource init fails → first still closed. */
    public static int twrInitFail() {
        Box.closeLog = 0;
        try {
            try (Box a = new Box(1); Box b = boomBox()) {
                return -1;
            }
        } catch (Boom e) {
            return Box.closeLog; // 1
        }
    }

    static Box boomBox() {
        throw new Boom("init");
    }

    /** Close failure suppressed onto body exception; body still catchable. */
    public static int twrSuppress() {
        try {
            try (Box b = new Box(7, 1)) {
                throw new Boom("body");
            }
        } catch (Boom e) {
            Throwable[] s = e.getSuppressed();
            if (s != null && s.length >= 1) {
                return 70 + s.length;
            }
            return 70;
        }
    }

    /** Pending exception survives alloc + GC in finally/cleanup. */
    public static int cleanupGc() {
        retained = null;
        try {
            try {
                throw new Boom("gc");
            } finally {
                Hold h = new Hold(42);
                for (int i = 0; i < 200; i++) {
                    Hold tmp = new Hold(i);
                    if (tmp.v < 0) {
                        retained = tmp;
                    }
                }
                retained = h;
                System.gc();
            }
        } catch (Boom e) {
            if (retained != null && retained.v == 42) {
                return 8;
            }
            return -8;
        }
        return 0;
    }

    /** Interface call throws → catch at caller. */
    public static int ifaceThrow() {
        ThrowingOp op = new BoomOp();
        try {
            return op.run();
        } catch (Boom e) {
            return 9;
        }
    }

    /** Closure / SAM throw (forEach lambda). */
    public static int closureThrow() {
        ArrayList<Hold> bag = new ArrayList<Hold>();
        bag.add(new Hold(1));
        try {
            bag.forEach(it -> {
                throw new Boom("lam");
            });
            return 0;
        } catch (Boom e) {
            return 10;
        }
    }

    /** Uncaught leaves pending for host (caller clears). Returns 1 if pending set. */
    public static int leavePending() {
        throw new Boom("uncaught-probe");
    }

    public static int pendingAfterLeave() {
        try {
            leavePending();
        } catch (Boom e) {
            return 11;
        }
        return 0;
    }
}
