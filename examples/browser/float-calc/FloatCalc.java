/**
 * wasm32-browser float/double smoke: all arithmetic runs in WASM.
 * UI only parses input text and displays results.
 * f/F suffixes are supported; casts remain for cross-width demos.
 */
public class FloatCalc {
    public static double lastLeft;
    public static double lastRight;

    public static double add(double a, double b) {
        return a + b;
    }

    public static double sub(double a, double b) {
        return a - b;
    }

    public static double mul(double a, double b) {
        return a * b;
    }

    public static double div(double a, double b) {
        return a / b;
    }

    public static float addf(float a, float b) {
        return a + b;
    }

    public static float mulf(float a, float b) {
        return a * b;
    }

    /** Cross-function + op dispatch. */
    public static double compute(double a, double b, int op) {
        lastLeft = a;
        lastRight = b;
        if (op == 0) {
            return add(a, b);
        }
        if (op == 1) {
            return sub(a, b);
        }
        if (op == 2) {
            return mul(a, b);
        }
        if (op == 3) {
            return div(a, b);
        }
        return 0.0 / 0.0;
    }

    public static double negate(double a) {
        return -a;
    }

    public static int cmpEq(double a, double b) {
        if (a == b) {
            return 1;
        }
        return 0;
    }

    public static int cmpLt(double a, double b) {
        if (a < b) {
            return 1;
        }
        return 0;
    }

    public static int toInt(double a) {
        return (int) a;
    }

    public static long toLong(double a) {
        return (long) a;
    }

    public static double fromInt(int a) {
        return (double) a;
    }

    public static float promoteDemote(float a) {
        double d = (double) a;
        return (float) d;
    }

    public static double arrayRoundTrip(double a, double b) {
        double[] xs = new double[2];
        xs[0] = a;
        xs[1] = b;
        return xs[0] + xs[1];
    }

    public static float floatPrecision() {
        float big = (float) 16777216.0;
        float one = (float) 1.0;
        return big + one;
    }

    public static double selectPos(double a, double b) {
        if (a >= 0.0) {
            return a;
        }
        return b;
    }

    public static void main(String[] args) {
        boolean keep = false;
        if (keep) {
            lastLeft = compute(1.0, 2.0, 0);
            lastRight = (double) addf((float) 1.0, (float) 2.0);
            lastLeft = arrayRoundTrip(1.0, 2.0);
            lastLeft = (double) floatPrecision();
            lastLeft = selectPos(1.0, 2.0);
            lastLeft = negate(1.0);
            lastLeft = fromInt(1);
            lastLeft = (double) promoteDemote((float) 1.0);
            lastLeft = (double) toInt(1.0);
            lastLeft = (double) toLong(1.0);
            lastLeft = (double) cmpEq(1.0, 1.0);
            lastLeft = (double) cmpLt(1.0, 2.0);
            lastLeft = sub(1.0, 1.0);
            lastLeft = mul(1.0, 1.0);
            lastLeft = div(1.0, 1.0);
            lastLeft = (double) mulf((float) 1.0, (float) 1.0);
            lastLeft = add(1.0, 1.0);
        }
    }
}
