// JNative Math stdlib (0.33)
// Floating Math uses IEEE 754 / libm semantics (NaN/Infinity, not domain exceptions).
// Integer abs(MIN_VALUE) follows two's-complement Java-compatible wrap.
// float overloads: deferred — language lacks float literals / primitive float casts (see Math report).

class Math {
    static double PI = 3.141592653589793;
    static double E = 2.718281828459045;
    static double TAU = 6.283185307179586;

    static int abs(int a) {
        if (a < 0) {
            return 0 - a;
        }
        return a;
    }

    static long abs(long a) {
        if (a < 0L) {
            return 0L - a;
        }
        return a;
    }

    static double abs(double a) {
        if (a < 0.0) {
            return 0.0 - a;
        }
        return a;
    }

    static int min(int a, int b) {
        if (a <= b) {
            return a;
        }
        return b;
    }

    static long min(long a, long b) {
        if (a <= b) {
            return a;
        }
        return b;
    }

    static double min(double a, double b) {
        if (isNaN(a)) {
            return b;
        }
        if (isNaN(b)) {
            return a;
        }
        if (a <= b) {
            return a;
        }
        return b;
    }

    static int max(int a, int b) {
        if (a >= b) {
            return a;
        }
        return b;
    }

    static long max(long a, long b) {
        if (a >= b) {
            return a;
        }
        return b;
    }

    static double max(double a, double b) {
        if (isNaN(a)) {
            return b;
        }
        if (isNaN(b)) {
            return a;
        }
        if (a >= b) {
            return a;
        }
        return b;
    }

    @Native(symbol = "sqrt")
    static native double sqrt(double x);

    @Native(symbol = "cbrt")
    static native double cbrt(double x);

    @Native(symbol = "pow")
    static native double pow(double a, double b);

    @Native(symbol = "sin")
    static native double sin(double x);

    @Native(symbol = "cos")
    static native double cos(double x);

    @Native(symbol = "tan")
    static native double tan(double x);

    @Native(symbol = "asin")
    static native double asin(double x);

    @Native(symbol = "acos")
    static native double acos(double x);

    @Native(symbol = "atan")
    static native double atan(double x);

    @Native(symbol = "atan2")
    static native double atan2(double y, double x);

    @Native(symbol = "exp")
    static native double exp(double x);

    @Native(symbol = "log")
    static native double log(double x);

    @Native(symbol = "log10")
    static native double log10(double x);

    @Native(symbol = "floor")
    static native double floor(double x);

    @Native(symbol = "ceil")
    static native double ceil(double x);

    @Native(symbol = "trunc")
    static native double trunc(double x);

    @Native(symbol = "hypot")
    static native double hypot(double x, double y);

    /**
     * Java-compatible Math.round(double) → long.
     * Uses libm llround because JNative has no primitive cast (double→long) yet.
     */
    @Native(symbol = "llround")
    static native long round(double a);

    static double toRadians(double deg) {
        return deg * 0.017453292519943295;
    }

    static double toDegrees(double rad) {
        return rad * 57.29577951308232;
    }

    /**
     * Pure IEEE predicates — avoid libm isnan/isinf macros (no stable link symbol on Darwin).
     * NaN is unordered: not &lt;, not &gt;, not == 0.
     */
    static boolean isNaN(double a) {
        if (a < 0.0) {
            return false;
        }
        if (a > 0.0) {
            return false;
        }
        if (a == 0.0) {
            return false;
        }
        return true;
    }

    static boolean isInfinite(double a) {
        if (isNaN(a)) {
            return false;
        }
        double inf = 1.0 / 0.0;
        if (a == inf) {
            return true;
        }
        if (a == (0.0 - inf)) {
            return true;
        }
        return false;
    }

    static boolean isFinite(double a) {
        if (isNaN(a)) {
            return false;
        }
        if (isInfinite(a)) {
            return false;
        }
        return true;
    }
}
