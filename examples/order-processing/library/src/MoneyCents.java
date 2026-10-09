package com.acme.money;

/**
 * Fixed-point money in integer cents. Not floating point.
 * Generic helper used by the order-processing app via package manager.
 */
public class MoneyCents {
    public static int add(int a, int b) {
        long s = (long) a + (long) b;
        if (s > 2147483647L || s < -2147483648L) {
            throw new RuntimeException("money overflow");
        }
        return (int) s;
    }

    public static int mulQty(int unitCents, int qty) {
        if (qty < 0) {
            throw new RuntimeException("negative qty");
        }
        long p = (long) unitCents * (long) qty;
        if (p > 2147483647L || p < -2147483648L) {
            throw new RuntimeException("money overflow");
        }
        return (int) p;
    }

    /** Prefer int overload when both exist (boxing interaction smoke). */
    public static int prefer(int x) {
        return 1;
    }

    public static int prefer(Integer x) {
        return 2;
    }

    public static <T> T identity(T v) {
        return v;
    }
}
