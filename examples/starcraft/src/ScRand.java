/**
 * Deterministic pseudo random numbers (64-bit LCG). The simulation never uses anything else,
 * so a replay reproduces a match exactly.
 *
 * @author 张晓达
 */
public class ScRand {
    long state;

    ScRand(long seed) {
        state = seed * 2862933555777941757L + 3037000493L;
        next(1000);
        next(1000);
    }

    int next(int bound) {
        state = state * 6364136223846793005L + 1442695040888963407L;
        long r = (state >> 33) & 2147483647L;
        return (int) (r % bound);
    }

    double nextDouble() {
        return next(1000000) / 1000000.0;
    }
}
