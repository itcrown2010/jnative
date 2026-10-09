/**
 * Mobile shared calculator core.
 * All arithmetic and input validation live here; platform shells must not reimplement them.
 *
 * Error codes (getLastError):
 *   0 = OK
 *   1 = empty input
 *   2 = invalid integer text
 *   3 = division by zero
 *   4 = overflow
 *   5 = unknown operator
 *
 * Operators (computeOp op):
 *   0 = +
 *   1 = -
 *   2 = *
 *   3 = /
 *
 * Note: wasm32-browser only supports String.length / charAt — no substring.
 * @author 张晓达
 */
public class CalculatorApp {
    public static int lastError;

    public static int add(int a, int b) {
        return a + b;
    }

    public static int sub(int a, int b) {
        return a - b;
    }

    public static int mul(int a, int b) {
        return a * b;
    }

    public static int getLastError() {
        return lastError;
    }

    /**
     * Parse decimal integer in s[start, end).
     * Optional leading '+' / '-'. On failure sets lastError and returns 0.
     */
    public static int parseIntRange(String s, int start, int end) {
        if (s == null) {
            lastError = 1;
            return 0;
        }
        if (start < 0 || end > s.length() || start >= end) {
            lastError = 1;
            return 0;
        }
        int i = start;
        int sign = 1;
        int c0 = s.charAt(i);
        if (c0 == 45) {
            if (start + 1 >= end) {
                lastError = 2;
                return 0;
            }
            sign = 0 - 1;
            i = start + 1;
        } else if (c0 == 43) {
            if (start + 1 >= end) {
                lastError = 2;
                return 0;
            }
            i = start + 1;
        }
        int acc = 0;
        int digits = 0;
        while (i < end) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                lastError = 2;
                return 0;
            }
            int digit = c - 48;
            if (acc > 214748364 || (acc == 214748364 && digit > 7)) {
                lastError = 2;
                return 0;
            }
            acc = acc * 10 + digit;
            digits = digits + 1;
            i = i + 1;
        }
        if (digits == 0) {
            lastError = 2;
            return 0;
        }
        lastError = 0;
        if (sign < 0) {
            return 0 - acc;
        }
        return acc;
    }

    public static int parseIntStrict(String s) {
        if (s == null) {
            lastError = 1;
            return 0;
        }
        return parseIntRange(s, 0, s.length());
    }

    public static int applyOp(int a, int b, int op) {
        if (op == 0) {
            lastError = 0;
            return add(a, b);
        }
        if (op == 1) {
            lastError = 0;
            return sub(a, b);
        }
        if (op == 2) {
            int aa = a;
            int bb = b;
            if (aa < 0) {
                aa = 0 - aa;
            }
            if (bb < 0) {
                bb = 0 - bb;
            }
            if (aa > 0 && bb > 0 && bb > 2147483647 / aa) {
                lastError = 4;
                return 0;
            }
            lastError = 0;
            return mul(a, b);
        }
        if (op == 3) {
            if (b == 0) {
                lastError = 3;
                return 0;
            }
            lastError = 0;
            return a / b;
        }
        lastError = 5;
        return 0;
    }

    /** Legacy: always add. Prefer computeOp. */
    public static int compute(String left, String right) {
        return computeOp(left, right, 0);
    }

    /**
     * Apply operator to two integer strings.
     * op: 0=add, 1=sub, 2=mul, 3=div (trunc toward zero).
     */
    public static int computeOp(String left, String right, int op) {
        int a = parseIntStrict(left);
        if (lastError != 0) {
            return 0;
        }
        int b = parseIntStrict(right);
        if (lastError != 0) {
            return 0;
        }
        return applyOp(a, b, op);
    }

    /**
     * Evaluate a simple expression "123*456" / "12+30" (two integer operands, one op).
     * Also accepts Unicode multiply (215) and divide (247).
     */
    public static int evaluate(String expr) {
        if (expr == null || expr.length() == 0) {
            lastError = 1;
            return 0;
        }
        int n = expr.length();
        int opPos = 0 - 1;
        int op = 0 - 1;
        int i = 0;
        if (expr.charAt(0) == 43 || expr.charAt(0) == 45) {
            i = 1;
        }
        while (i < n) {
            int c = expr.charAt(i);
            if (c == 43 || c == 45 || c == 42 || c == 47 || c == 215 || c == 247) {
                if (opPos >= 0) {
                    lastError = 2;
                    return 0;
                }
                opPos = i;
                if (c == 43) {
                    op = 0;
                } else if (c == 45) {
                    op = 1;
                } else if (c == 42 || c == 215) {
                    op = 2;
                } else {
                    op = 3;
                }
            }
            i = i + 1;
        }
        if (opPos <= 0 || opPos >= n - 1) {
            lastError = 2;
            return 0;
        }
        int a = parseIntRange(expr, 0, opPos);
        if (lastError != 0) {
            return 0;
        }
        int b = parseIntRange(expr, opPos + 1, n);
        if (lastError != 0) {
            return 0;
        }
        return applyOp(a, b, op);
    }

    public static void main(String[] args) {
        boolean keep = false;
        if (keep) {
            lastError = compute("0", "0");
            lastError = computeOp("0", "0", 2);
            lastError = evaluate("1*1");
            lastError = getLastError();
            lastError = add(0, 0);
            lastError = sub(0, 0);
            lastError = mul(0, 0);
            lastError = parseIntStrict("0");
            lastError = parseIntRange("0", 0, 1);
            lastError = applyOp(0, 0, 0);
        }
    }
}
