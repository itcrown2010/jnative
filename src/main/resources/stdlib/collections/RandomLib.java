/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative java.util.Random: the same 48-bit linear congruential generator as the JDK, so a seeded
// Random produces the identical sequence (nextInt/nextInt(bound)/nextLong/nextDouble/nextBoolean).

class RandomNative {
    @Native(symbol = "jnative_mono_nanos")
    static native long nanos();
}

class Random {
    static long seedUniquifier = 8682522807148012L;

    long seed;

    Random() {
        seedUniquifier = seedUniquifier * 1181783497276652981L;
        setSeed(seedUniquifier ^ RandomNative.nanos());
    }

    Random(long seed) {
        setSeed(seed);
    }

    void setSeed(long s) {
        this.seed = (s ^ 25214903917L) & 281474976710655L;
    }

    int next(int bits) {
        seed = (seed * 25214903917L + 11L) & 281474976710655L;
        return (int) (seed >>> (48 - bits));
    }

    int nextInt() {
        return next(32);
    }

    int nextInt(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        int r = next(31);
        int m = bound - 1;
        if ((bound & m) == 0) {
            return (int) ((bound * (long) r) >> 31);
        }
        int u = r;
        r = u % bound;
        while (u - r + m < 0) {
            u = next(31);
            r = u % bound;
        }
        return r;
    }

    int nextInt(int origin, int bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException("bound must be greater than origin");
        }
        int n = bound - origin;
        if (n > 0) {
            return nextInt(n) + origin;
        }
        int r = nextInt();
        while (r < origin || r >= bound) {
            r = nextInt();
        }
        return r;
    }

    long nextLong() {
        long hi = (long) next(32);
        long lo = (long) next(32);
        return (hi << 32) + lo;
    }

    boolean nextBoolean() {
        return next(1) != 0;
    }

    float nextFloat() {
        return next(24) / ((float) (1 << 24));
    }

    double nextDouble() {
        long hi = (long) next(26);
        long lo = (long) next(27);
        return ((hi << 27) + lo) * 1.1102230246251565E-16;
    }
}
