/**
 * Fixed-point four-function calculator core (SCALE = 1e6).
 * Immediate evaluation (no operator precedence): 2 + 3 × 4 = 20.
 * All arithmetic lives here — Swift/JS must not reimplement it.
 *
 * Error codes (getError):
 *   0 = OK
 *   1 = division by zero
 *   2 = overflow / out of range
 */
public class CalcEngine {
    // Constants as methods — wasm32-browser static initializers are zero.
    private static long scale() {
        return 1000000L;
    }

    private static long maxAbs() {
        return 9999999999000000L;
    }

    /** Accumulator after an operator (scaled). */
    public static long acc;
    /** Current entry or last result shown (scaled). */
    public static long entry;
    /** Pending binary operator. */
    public static int op;
    /** 1 while the user is typing a number. */
    public static int entering;
    /** 1 if a decimal point was typed for the current entry. */
    public static int hasDot;
    /** Fractional digits typed so far (0..6). */
    public static int fracDigits;
    /** 1 after '=' produced a result (next digit starts a new calc). */
    public static int justEvaluated;
    /** 1 when waiting for the right-hand operand after an operator. */
    public static int awaitingRhs;
    public static int error;

    public static void reset() {
        acc = 0L;
        entry = 0L;
        op = 0;
        entering = 0;
        hasDot = 0;
        fracDigits = 0;
        justEvaluated = 0;
        awaitingRhs = 0;
        error = 0;
    }

    public static int getError() {
        return error;
    }

    public static int getEntering() {
        return entering;
    }

    public static int getHasDot() {
        return hasDot;
    }

    public static int getFracDigits() {
        return fracDigits;
    }

    public static int getOp() {
        return op;
    }

    /** Scaled display value as i64 (BigInt across the JS/WASM boundary). */
    public static long getValue() {
        return entry;
    }

    /** Promote digit 0..9 to long without casts. */
    private static long digitLong(int d) {
        long x = 0L;
        int i = 0;
        while (i < d) {
            x = x + 1L;
            i = i + 1;
        }
        return x;
    }

    public static void pressClear() {
        reset();
    }

    public static void pressDigit(int d) {
        if (d < 0 || d > 9) {
            return;
        }
        if (error != 0) {
            reset();
        }
        if (justEvaluated != 0) {
            reset();
        }
        if (awaitingRhs != 0) {
            entry = 0L;
            hasDot = 0;
            fracDigits = 0;
            awaitingRhs = 0;
        }
        if (entering == 0) {
            entry = digitLong(d) * scale();
            entering = 1;
            hasDot = 0;
            fracDigits = 0;
            return;
        }
        if (hasDot != 0) {
            if (fracDigits >= 6) {
                return;
            }
            long place = scale();
            int i = 0;
            while (i <= fracDigits) {
                place = place / 10L;
                i = i + 1;
            }
            long add = digitLong(d) * place;
            if (entry >= 0L) {
                if (canAdd(entry, add) == false) {
                    setError(2);
                    return;
                }
                entry = entry + add;
            } else {
                long neg = 0L - add;
                if (canAdd(entry, neg) == false) {
                    setError(2);
                    return;
                }
                entry = entry + neg;
            }
            fracDigits = fracDigits + 1;
            return;
        }
        // Integer part: entry = entry * 10 + d * scale()
        if (canMul(entry, 10L) == false) {
            setError(2);
            return;
        }
        long next = entry * 10L;
        long add = digitLong(d) * scale();
        if (canAdd(next, add) == false) {
            setError(2);
            return;
        }
        next = next + add;
        if (abs(next) > maxAbs()) {
            setError(2);
            return;
        }
        entry = next;
    }

    public static void pressDot() {
        if (error != 0) {
            reset();
        }
        if (justEvaluated != 0) {
            reset();
        }
        if (awaitingRhs != 0) {
            entry = 0L;
            hasDot = 0;
            fracDigits = 0;
            awaitingRhs = 0;
        }
        if (entering != 0 && hasDot != 0) {
            return;
        }
        if (entering == 0) {
            entry = 0L;
            entering = 1;
        }
        hasDot = 1;
        fracDigits = 0;
    }

    public static void pressOp(int nextOp) {
        if (nextOp < 1 || nextOp > 4) {
            return;
        }
        if (error != 0) {
            return;
        }
        if (justEvaluated != 0) {
            justEvaluated = 0;
            acc = entry;
            op = nextOp;
            entering = 0;
            hasDot = 0;
            fracDigits = 0;
            awaitingRhs = 1;
            return;
        }
        if (awaitingRhs != 0) {
            op = nextOp;
            return;
        }
        if (op != 0 && entering != 0) {
            if (applyOp() == false) {
                return;
            }
            acc = entry;
            op = nextOp;
            entering = 0;
            hasDot = 0;
            fracDigits = 0;
            awaitingRhs = 1;
            return;
        }
        acc = entry;
        op = nextOp;
        entering = 0;
        hasDot = 0;
        fracDigits = 0;
        awaitingRhs = 1;
    }

    public static void pressEquals() {
        if (error != 0) {
            return;
        }
        if (justEvaluated != 0) {
            return;
        }
        if (op == 0) {
            justEvaluated = 1;
            entering = 0;
            hasDot = 0;
            fracDigits = 0;
            awaitingRhs = 0;
            return;
        }
        if (awaitingRhs != 0) {
            // No second operand — keep current result.
            justEvaluated = 1;
            entering = 0;
            hasDot = 0;
            fracDigits = 0;
            awaitingRhs = 0;
            op = 0;
            return;
        }
        if (applyOp() == false) {
            return;
        }
        op = 0;
        justEvaluated = 1;
        entering = 0;
        hasDot = 0;
        fracDigits = 0;
        awaitingRhs = 0;
        acc = entry;
    }

    public static void pressBackspace() {
        if (error != 0) {
            return;
        }
        if (entering == 0) {
            return;
        }
        if (hasDot != 0 && fracDigits > 0) {
            long place = scale();
            int i = 0;
            while (i < fracDigits) {
                place = place / 10L;
                i = i + 1;
            }
            long digit = abs(entry) / place % 10L;
            long sub = digit * place;
            if (entry >= 0L) {
                entry = entry - sub;
            } else {
                entry = entry + sub;
            }
            fracDigits = fracDigits - 1;
            return;
        }
        if (hasDot != 0 && fracDigits == 0) {
            hasDot = 0;
            return;
        }
        // Remove last integer digit (entry is a multiple of scale() when hasDot==0).
        long intPart = entry / scale();
        intPart = intPart / 10L;
        entry = intPart * scale();
        if (entry == 0L && hasDot == 0) {
            entering = 0;
        }
    }

    public static void pressNegate() {
        if (error != 0) {
            return;
        }
        if (entry == 0L) {
            return;
        }
        if (canNegate(entry) == false) {
            setError(2);
            return;
        }
        entry = 0L - entry;
        if (justEvaluated != 0) {
            acc = entry;
        }
    }

    private static boolean applyOp() {
        long a = acc;
        long b = entry;
        long r = 0L;
        if (op == 1) {
            if (canAdd(a, b) == false) {
                setError(2);
                return false;
            }
            r = a + b;
        } else if (op == 2) {
            long nb = 0L - b;
            if (canNegate(b) == false || !canAdd(a, nb)) {
                setError(2);
                return false;
            }
            r = a - b;
        } else if (op == 3) {
            r = mulFixed(a, b);
            if (error != 0) {
                return false;
            }
        } else if (op == 4) {
            r = divFixed(a, b);
            if (error != 0) {
                return false;
            }
        } else {
            r = b;
        }
        if (abs(r) > maxAbs()) {
            setError(2);
            return false;
        }
        entry = r;
        return true;
    }

    /** Round-half-away-from-zero: (a * b) / scale(). */
    private static long mulFixed(long a, long b) {
        if (a == 0L || b == 0L) {
            return 0L;
        }
        if (canMul(a, b) == false) {
            setError(2);
            return 0L;
        }
        long prod = a * b;
        long half = scale() / 2L;
        long q;
        if (prod >= 0L) {
            if (canAdd(prod, half) == false) {
                setError(2);
                return 0L;
            }
            q = (prod + half) / scale();
        } else {
            long nh = 0L - half;
            if (canAdd(prod, nh) == false) {
                setError(2);
                return 0L;
            }
            q = (prod - half) / scale();
        }
        return q;
    }

    /** Round-half-away-from-zero: (a * scale()) / b. */
    private static long divFixed(long a, long b) {
        if (b == 0L) {
            setError(1);
            return 0L;
        }
        if (canMul(a, scale()) == false) {
            setError(2);
            return 0L;
        }
        long num = a * scale();
        long q = num / b;
        long r = num % b;
        if (r < 0L) {
            r = 0L - r;
        }
        long ab = b;
        if (ab < 0L) {
            ab = 0L - ab;
        }
        if (r * 2L >= ab) {
            if (q >= 0L) {
                if (canAdd(q, 1L) == false) {
                    setError(2);
                    return 0L;
                }
                q = q + 1L;
            } else {
                if (canAdd(q, 0L - 1L) == false) {
                    setError(2);
                    return 0L;
                }
                q = q - 1L;
            }
        }
        return q;
    }

    private static void setError(int code) {
        error = code;
        entering = 0;
        hasDot = 0;
        fracDigits = 0;
        awaitingRhs = 0;
        justEvaluated = 0;
        op = 0;
    }

    private static long abs(long v) {
        if (v < 0L) {
            return 0L - v;
        }
        return v;
    }

    private static boolean canNegate(long v) {
        // Calculator values stay within maxAbs(); negation is always safe there.
        return abs(v) <= maxAbs();
    }

    private static boolean canAdd(long a, long b) {
        long sum = a + b;
        // Detect wrap via sign analysis for values near maxAbs().
        if (b > 0L && sum < a) {
            return false;
        }
        if (b < 0L && sum > a) {
            return false;
        }
        if (abs(sum) > maxAbs()) {
            return false;
        }
        return true;
    }

    private static boolean canMul(long a, long b) {
        if (a == 0L || b == 0L) {
            return true;
        }
        long aa = abs(a);
        long bb = abs(b);
        // Bound product before multiply; maxAbs()*scale() fits in signed long.
        long limit = 9223372036854775807L;
        if (aa > limit / bb) {
            return false;
        }
        return true;
    }

    public static void main(String[] args) {
        // Keep exports alive in the IR module without running at startup.
        boolean keep = false;
        if (keep) {
            reset();
            pressDigit(1);
            pressDot();
            pressOp(1);
            pressEquals();
            pressClear();
            pressBackspace();
            pressNegate();
            long v = getValue();
            int e = getError();
            e = getEntering() + getHasDot() + getFracDigits() + getOp() + e;
            v = v + digitLong(e % 10);
            acc = v;
        }
    }
}
