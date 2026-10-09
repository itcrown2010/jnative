import jnative.ui.*;

/**
 * Headless tests for JNative Calculator engines (no UI).
 *
 * <pre>
 * java -jar target/jnative.jar examples/calculator/CalculatorTests.java \
 *   -o build/calculator/CalculatorTests
 * ./build/calculator/CalculatorTests
 * </pre>
 *
 * Engines are duplicated from JNativeCalculator.java so this unit compiles alone.
 *
 * @author 张晓达
 */
public class CalculatorTests {
    static int passed;
    static int failed;

    public static void main(String[] args) {
        Application.setHeadless(1);
        passed = 0;
        failed = 0;
        runDecimalTests();
        runArithmeticTests();
        runExpressionTests();
        runScientificTests();
        runProgrammerTests();
        runPercentTests();
        runStateTests();
        System.out.println("PASS " + String.valueOf(passed));
        System.out.println("FAIL " + String.valueOf(failed));
        if (failed != 0) {
            throw new RuntimeException("CalculatorTests failed: " + String.valueOf(failed));
        }
    }

    static void ok(String name, int cond) {
        if (cond != 0) {
            passed = passed + 1;
            System.out.println("ok   " + name);
        } else {
            failed = failed + 1;
            System.out.println("FAIL " + name);
        }
    }

    static void runDecimalTests() {
        Decimal a = Decimal.fromString("0.1");
        Decimal b = Decimal.fromString("0.2");
        Decimal s = a.add(b);
        int eq03 = 0;
        if (s.compareTo(Decimal.fromString("0.3")) == 0) { eq03 = 1; }
        ok("0.1+0.2==0.3", eq03);

        Decimal one = Decimal.fromLong(1L);
        Decimal three = Decimal.fromLong(3L);
        String third = one.div(three).toPlainString();
        int starts = 0;
        if (third.startsWith("0.333")) {
            starts = 1;
        }
        ok("1/3 starts with 0.333", starts);

        Decimal big = Decimal.fromString("999999999999");
        Decimal big1 = big.add(Decimal.fromLong(1L));
        int bigOk = 0;
        if (big1.toPlainString().equals("1000000000000")) { bigOk = 1; }
        ok("999999999999+1", bigOk);
    }

    static void runArithmeticTests() {
        CalculatorState st = new CalculatorState();
        DecimalEngine eng = new DecimalEngine(st);
        eng.input("1");
        eng.input("/");
        eng.input("0");
        eng.input("=");
        int div0 = 0;
        if (st.error.indexOf("zero") >= 0 || st.display.indexOf("zero") >= 0) {
            div0 = 1;
        }
        ok("div0", div0);

        st = new CalculatorState();
        eng = new DecimalEngine(st);
        eng.input("5");
        eng.input("+/-");
        int negOk = 0;
        if (st.display.startsWith("-")) { negOk = 1; }
        ok("negative", negOk);

        st = new CalculatorState();
        eng = new DecimalEngine(st);
        eng.input("2");
        eng.input("+");
        eng.input("3");
        eng.input("=");
        String first = st.display;
        eng.input("=");
        String second = st.display;
        /* continuous equals: 2+3=5, = → 8 */
        int cont = 0;
        if (first.equals("5") && second.equals("8")) {
            cont = 1;
        }
        ok("continuous equals", cont);
    }

    static void runExpressionTests() {
        ExpressionEvaluator ev = new ExpressionEvaluator(CalculatorState.ANGLE_DEG);
        ExprEvalResult r1 = ev.evaluate("2+3*4");
        int e1 = 0;
        if (r1.ok != 0 && Math.abs(r1.value - 14.0) < 1.0e-9) { e1 = 1; }
        ok("2+3*4=14", e1);
        ExprEvalResult r2 = ev.evaluate("(2+3)*4");
        int e2 = 0;
        if (r2.ok != 0 && Math.abs(r2.value - 20.0) < 1.0e-9) { e2 = 1; }
        ok("(2+3)*4=20", e2);
        ExprEvalResult r3 = ev.evaluate("2^3^2");
        int e3 = 0;
        if (r3.ok != 0 && Math.abs(r3.value - 512.0) < 1.0e-9) { e3 = 1; }
        ok("2^3^2=512", e3);
    }

    static void runScientificTests() {
        ExpressionEvaluator ev = new ExpressionEvaluator(CalculatorState.ANGLE_DEG);
        ExprEvalResult r = ev.evaluate("sin(0)");
        int s0 = 0;
        if (r.ok != 0 && Math.abs(r.value) < 1.0e-12) { s0 = 1; }
        ok("sin(0)=0 approx", s0);
    }

    static void runProgrammerTests() {
        ProgrammerEngine pe = new ProgrammerEngine(new CalculatorState());
        long v1 = pe.parseLiteral("0xFF", 16);
        int h = 0;
        if (v1 == 255L) { h = 1; }
        ok("0xFF=255", h);
        long v2 = pe.parseLiteral("0b1010", 2);
        int b = 0;
        if (v2 == 10L) { b = 1; }
        ok("0b1010=10", b);
    }

    static void runPercentTests() {
        CalculatorState st = new CalculatorState();
        DecimalEngine eng = new DecimalEngine(st);
        eng.input("5");
        eng.input("0");
        eng.input("%");
        int p = 0;
        if (st.display.equals("0.5")) { p = 1; }
        ok("50% = 0.5", p);
    }

    static void runStateTests() {
        CalculatorEngine ce = new CalculatorEngine();
        ce.setMode(CalculatorState.MODE_SCIENTIFIC);
        int m1 = 0;
        if (ce.state.mode == CalculatorState.MODE_SCIENTIFIC) { m1 = 1; }
        ok("mode scientific", m1);

        CalculatorState st = new CalculatorState();
        DecimalEngine eng = new DecimalEngine(st);
        eng.input("9");
        eng.input("+/-");
        eng.input("√x");
        int negSqrt = 0;
        if (st.error.length() > 0) { negSqrt = 1; }
        ok("negative sqrt errors", negSqrt);

        st = new CalculatorState();
        eng = new DecimalEngine(st);
        eng.input("1");
        eng.input("0");
        eng.input("+");
        eng.input("2");
        eng.input("0");
        eng.input("+");
        eng.input("3");
        eng.input("=");
        int chain = 0;
        if (st.display.equals("33")) { chain = 1; }
        ok("continuous ops 10+20+3", chain);

        HistoryManager hm = new HistoryManager();
        hm.add("1+1", "2");
        hm.clear();
        int cleared = 0;
        if (hm.size() == 0) { cleared = 1; }
        ok("history clear", cleared);
    }
}


/* ======================== State ======================== */

class CalculatorState {
    static int MODE_STANDARD = 0;
    static int MODE_SCIENTIFIC = 1;
    static int MODE_PROGRAMMER = 2;

    static int ANGLE_DEG = 0;
    static int ANGLE_RAD = 1;

    static int BASE_BIN = 2;
    static int BASE_OCT = 8;
    static int BASE_DEC = 10;
    static int BASE_HEX = 16;

    int mode;
    String display;
    String expression;
    String error;
    int angleMode;
    int bitWidth;
    int signed;
    int base;
    String pendingOp;
    String entry;
    String lastResult;
    int waitingForOperand;
    String lastBinaryOp;
    String lastBinaryRhs;

    CalculatorState() {
        mode = MODE_STANDARD;
        display = "0";
        expression = "";
        error = "";
        angleMode = ANGLE_DEG;
        bitWidth = 64;
        signed = 1;
        base = BASE_DEC;
        pendingOp = "";
        entry = "0";
        lastResult = "0";
        waitingForOperand = 0;
        lastBinaryOp = "";
        lastBinaryRhs = "";
    }

    void clearError() {
        error = "";
    }

    void setError(String msg) {
        error = msg;
        display = msg;
    }
}

class HistoryEntry {
    String expr;
    String result;
    long timeMs;

    HistoryEntry(String e, String r, long t) {
        expr = e;
        result = r;
        timeMs = t;
    }
}

class HistoryManager {
    ArrayList<HistoryEntry> items;

    HistoryManager() {
        items = new ArrayList<HistoryEntry>();
    }

    void add(String expr, String result) {
        long t = Instant.now().toEpochMilli();
        items.add(new HistoryEntry(expr, result, t));
    }

    void clear() {
        items = new ArrayList<HistoryEntry>();
    }

    ArrayList<HistoryEntry> get() {
        return items;
    }

    int size() {
        return items.size();
    }
}

/* ======================== Façade ======================== */

class CalculatorEngine {
    CalculatorState state;
    DecimalEngine decimal;
    ScientificEngine scientific;
    ProgrammerEngine programmer;
    HistoryManager history;

    CalculatorEngine() {
        state = new CalculatorState();
        decimal = new DecimalEngine(state);
        scientific = new ScientificEngine(state);
        programmer = new ProgrammerEngine(state);
        history = new HistoryManager();
    }

    void setMode(int mode) {
        state.mode = mode;
        state.clearError();
        if (mode == CalculatorState.MODE_PROGRAMMER) {
            programmer.syncDisplayFromEntry();
        } else {
            if (state.entry == null || state.entry.length() == 0) {
                state.entry = "0";
            }
            state.display = state.entry;
        }
    }

    void input(String key) {
        state.clearError();
        if (state.mode == CalculatorState.MODE_STANDARD) {
            decimal.input(key);
        } else {
            if (state.mode == CalculatorState.MODE_SCIENTIFIC) {
                scientific.input(key);
            } else {
                programmer.input(key);
            }
        }
    }

    void recordHistory(String expr, String result) {
        if (expr == null || result == null) {
            return;
        }
        if (expr.length() == 0) {
            return;
        }
        history.add(expr, result);
    }
}

/* ======================== Decimal / Standard ======================== */

/**
 * Standard calculator using Decimal.
 *
 * Percent:
 * - Unary: pressing % alone after an entry → value/100 (e.g. 50% = 0.5).
 * - Binary with pending + or -: a % b means a*(b/100) when % is applied as the
 *   second operand transform before the pending op (classic calculator % on +/−).
 * - Otherwise % is treated as unary percent on the current entry.
 */
class DecimalEngine {
    CalculatorState state;

    DecimalEngine(CalculatorState s) {
        state = s;
    }

    void input(String key) {
        if (key == null || key.length() == 0) {
            return;
        }
        if (key.equals("AC")) {
            allClear();
            return;
        }
        if (key.equals("CE")) {
            clearEntry();
            return;
        }
        if (key.equals("BS") || key.equals("⌫")) {
            backspace();
            return;
        }
        if (key.equals("+/-")) {
            negate();
            return;
        }
        if (key.equals("%")) {
            percent();
            return;
        }
        if (key.equals("1/x")) {
            reciprocal();
            return;
        }
        if (key.equals("x²") || key.equals("x^2")) {
            square();
            return;
        }
        if (key.equals("√x") || key.equals("sqrt")) {
            sqrt();
            return;
        }
        if (key.equals("=")) {
            equalsOp();
            return;
        }
        if (key.equals("+") || key.equals("-") || key.equals("*") || key.equals("/")) {
            binaryOp(key);
            return;
        }
        if (key.equals(".")) {
            digitDot();
            return;
        }
        if (isDigitKey(key) != 0) {
            digit(key);
            return;
        }
    }

    int isDigitKey(String key) {
        if (key.length() != 1) {
            return 0;
        }
        int c = key.charAt(0);
        if (c >= 48 && c <= 57) {
            return 1;
        }
        return 0;
    }

    void allClear() {
        state.entry = "0";
        state.display = "0";
        state.expression = "";
        state.pendingOp = "";
        state.lastResult = "0";
        state.waitingForOperand = 0;
        state.lastBinaryOp = "";
        state.lastBinaryRhs = "";
        state.clearError();
    }

    void clearEntry() {
        state.entry = "0";
        state.display = "0";
        state.waitingForOperand = 0;
        state.clearError();
    }

    void backspace() {
        if (state.waitingForOperand != 0) {
            return;
        }
        if (state.entry.length() <= 1) {
            state.entry = "0";
        } else {
            state.entry = state.entry.substring(0, state.entry.length() - 1);
            if (state.entry.equals("-")) {
                state.entry = "0";
            }
        }
        state.display = state.entry;
    }

    void digit(String d) {
        if (state.waitingForOperand != 0) {
            state.entry = d;
            state.waitingForOperand = 0;
        } else {
            if (state.entry.equals("0")) {
                state.entry = d;
            } else {
                if (state.entry.equals("-0")) {
                    state.entry = "-" + d;
                } else {
                    state.entry = state.entry + d;
                }
            }
        }
        state.display = state.entry;
    }

    void digitDot() {
        if (state.waitingForOperand != 0) {
            state.entry = "0.";
            state.waitingForOperand = 0;
            state.display = state.entry;
            return;
        }
        if (state.entry.indexOf(".") >= 0) {
            return;
        }
        state.entry = state.entry + ".";
        state.display = state.entry;
    }

    void negate() {
        try {
            Decimal v = Decimal.fromString(state.entry);
            v = v.negate();
            state.entry = formatDec(v);
            state.display = state.entry;
        } catch (DecimalException ex) {
            state.setError(ex.getMessage());
        }
    }

    void percent() {
        try {
            Decimal b = Decimal.fromString(state.entry);
            /* Unary percent = value/100 when no pending +/−, or when pressing % alone. */
            if (state.pendingOp.equals("+") || state.pendingOp.equals("-")) {
                if (state.lastResult != null && state.lastResult.length() > 0 && state.waitingForOperand == 0) {
                    /* Binary a%b with pending +/- → a*(b/100). */
                    Decimal a = Decimal.fromString(state.lastResult);
                    Decimal pct = a.mul(b.percent());
                    state.entry = formatDec(pct);
                    state.display = state.entry;
                    return;
                }
            }
            Decimal u = b.percent();
            state.entry = formatDec(u);
            state.display = state.entry;
        } catch (DecimalException ex) {
            state.setError(ex.getMessage());
        }
    }

    void reciprocal() {
        try {
            Decimal v = Decimal.fromString(state.entry);
            if (v.isZero() != 0) {
                state.setError("Division by zero");
                return;
            }
            state.entry = formatDec(v.reciprocal());
            state.display = state.entry;
            state.waitingForOperand = 1;
        } catch (DecimalException ex) {
            state.setError(ex.getMessage());
        }
    }

    void square() {
        try {
            Decimal v = Decimal.fromString(state.entry);
            state.entry = formatDec(v.square());
            state.display = state.entry;
            state.waitingForOperand = 1;
        } catch (DecimalException ex) {
            state.setError(ex.getMessage());
        }
    }

    void sqrt() {
        try {
            Decimal v = Decimal.fromString(state.entry);
            state.entry = formatDec(v.sqrt());
            state.display = state.entry;
            state.waitingForOperand = 1;
        } catch (DecimalException ex) {
            state.setError(ex.getMessage());
        }
    }

    void binaryOp(String op) {
        try {
            if (state.pendingOp.length() > 0 && state.waitingForOperand == 0) {
                computePending();
                if (state.error.length() > 0) {
                    return;
                }
            } else {
                state.lastResult = state.entry;
            }
            state.pendingOp = op;
            state.expression = state.lastResult + " " + op;
            state.waitingForOperand = 1;
            state.display = state.entry;
        } catch (DecimalException ex) {
            state.setError(ex.getMessage());
        }
    }

    void equalsOp() {
        try {
            if (state.pendingOp.length() == 0) {
                if (state.lastBinaryOp.length() > 0) {
                    /* Continuous equals: repeat last binary op with last RHS. */
                    state.pendingOp = state.lastBinaryOp;
                    state.lastResult = state.entry;
                    state.entry = state.lastBinaryRhs;
                    computePending();
                    if (state.error.length() == 0) {
                        state.expression = state.expression + " =";
                    }
                    return;
                }
                state.display = state.entry;
                return;
            }
            if (state.waitingForOperand != 0) {
                state.entry = state.lastResult;
            }
            String rhs = state.entry;
            String expr = state.lastResult + " " + state.pendingOp + " " + rhs;
            computePending();
            if (state.error.length() == 0) {
                /* computePending already stashed lastBinaryOp / lastBinaryRhs */
                state.expression = expr;
                state.waitingForOperand = 1;
            }
        } catch (DecimalException ex) {
            state.setError(ex.getMessage());
        }
    }

    void computePending() {
        Decimal a = Decimal.fromString(state.lastResult);
        Decimal b = Decimal.fromString(state.entry);
        String op = state.pendingOp;
        Decimal r = a;
        if (op.equals("+")) {
            r = a.add(b);
        } else {
            if (op.equals("-")) {
                r = a.sub(b);
            } else {
                if (op.equals("*")) {
                    r = a.mul(b);
                } else {
                    if (op.equals("/")) {
                        if (b.isZero() != 0) {
                            state.setError("Division by zero");
                            state.pendingOp = "";
                            return;
                        }
                        r = a.div(b);
                    }
                }
            }
        }
        state.lastBinaryOp = op;
        state.lastBinaryRhs = state.entry;
        state.lastResult = formatDec(r);
        state.entry = state.lastResult;
        state.display = state.entry;
        state.pendingOp = "";
    }

    String formatDec(Decimal v) {
        return v.toDisplayString(16);
    }
}

/* ======================== Expression (Scientific) ======================== */

class ExprTok {
    static int NUM = 1;
    static int OP = 2;
    static int LPAREN = 3;
    static int RPAREN = 4;
    static int IDENT = 5;
    static int FACT = 6;
    static int END = 7;

    int kind;
    String text;

    ExprTok(int k, String t) {
        kind = k;
        text = t;
    }
}

class ExpressionTokenizer {
    static ArrayList<ExprTok> tokenize(String src) {
        ArrayList<ExprTok> out = new ArrayList<ExprTok>();
        if (src == null) {
            out.add(new ExprTok(ExprTok.END, ""));
            return out;
        }
        int i = 0;
        int n = src.length();
        while (i < n) {
            int c = src.charAt(i);
            if (c == 32 || c == 9 || c == 10 || c == 13) {
                i = i + 1;
            } else {
                if (c == 40) {
                    out.add(new ExprTok(ExprTok.LPAREN, "("));
                    i = i + 1;
                } else {
                    if (c == 41) {
                        out.add(new ExprTok(ExprTok.RPAREN, ")"));
                        i = i + 1;
                    } else {
                        if (c == 33) {
                            out.add(new ExprTok(ExprTok.FACT, "!"));
                            i = i + 1;
                        } else {
                            if (c == 43 || c == 45 || c == 42 || c == 47 || c == 94) {
                                String op = src.substring(i, i + 1);
                                out.add(new ExprTok(ExprTok.OP, op));
                                i = i + 1;
                            } else {
                                if ((c >= 48 && c <= 57) || c == 46) {
                                    int start = i;
                                    while (i < n) {
                                        int d = src.charAt(i);
                                        if ((d >= 48 && d <= 57) || d == 46) {
                                            i = i + 1;
                                        } else {
                                            break;
                                        }
                                    }
                                    out.add(new ExprTok(ExprTok.NUM, src.substring(start, i)));
                                } else {
                                    if ((c >= 65 && c <= 90) || (c >= 97 && c <= 122)) {
                                        int start2 = i;
                                        while (i < n) {
                                            int d2 = src.charAt(i);
                                            if ((d2 >= 65 && d2 <= 90) || (d2 >= 97 && d2 <= 122)
                                                    || (d2 >= 48 && d2 <= 57) || d2 == 95) {
                                                i = i + 1;
                                            } else {
                                                break;
                                            }
                                        }
                                        out.add(new ExprTok(ExprTok.IDENT, src.substring(start2, i)));
                                    } else {
                                        out.add(new ExprTok(ExprTok.END, "bad:" + src.substring(i, i + 1)));
                                        return out;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        out.add(new ExprTok(ExprTok.END, ""));
        return out;
    }
}

class ExpressionParser {
    ArrayList<ExprTok> toks;
    int pos;
    String err;

    ExpressionParser() {
        pos = 0;
        err = "";
        toks = new ArrayList<ExprTok>();
    }

    /**
     * Factory: ArrayList fields assigned from constructor parameters are unreliable
     * in current JNative (parameter ref may not stick); set toks after construction.
     */
    static ExpressionParser of(ArrayList<ExprTok> t) {
        ExpressionParser p = new ExpressionParser();
        p.toks = t;
        p.pos = 0;
        p.err = "";
        return p;
    }

    ExprTok peek() {
        if (pos >= toks.size()) {
            return new ExprTok(ExprTok.END, "");
        }
        return toks.get(pos);
    }

    ExprTok next() {
        ExprTok t = peek();
        pos = pos + 1;
        return t;
    }

    /** Parse expression → AST as nested ExprNode tree. */
    ExprNode parse() {
        ExprNode n = parseExpr();
        if (err.length() > 0) {
            return null;
        }
        if (peek().kind != ExprTok.END) {
            err = "Unexpected token: " + peek().text;
            return null;
        }
        return n;
    }

    ExprNode parseExpr() {
        ExprNode left = parseTerm();
        if (left == null) {
            return null;
        }
        while (peek().kind == ExprTok.OP && (peek().text.equals("+") || peek().text.equals("-"))) {
            String op = next().text;
            ExprNode right = parseTerm();
            if (right == null) {
                return null;
            }
            left = ExprNode.binary(op, left, right);
        }
        return left;
    }

    ExprNode parseTerm() {
        ExprNode left = parsePower();
        if (left == null) {
            return null;
        }
        while (peek().kind == ExprTok.OP && (peek().text.equals("*") || peek().text.equals("/"))) {
            String op = next().text;
            ExprNode right = parsePower();
            if (right == null) {
                return null;
            }
            left = ExprNode.binary(op, left, right);
        }
        return left;
    }

    /** ^ is right-associative: 2^3^2 = 2^(3^2) = 512. */
    ExprNode parsePower() {
        ExprNode left = parseUnary();
        if (left == null) {
            return null;
        }
        if (peek().kind == ExprTok.OP && peek().text.equals("^")) {
            next();
            ExprNode right = parsePower();
            if (right == null) {
                return null;
            }
            return ExprNode.binary("^", left, right);
        }
        return left;
    }

    ExprNode parseUnary() {
        if (peek().kind == ExprTok.OP && peek().text.equals("+")) {
            next();
            return parseUnary();
        }
        if (peek().kind == ExprTok.OP && peek().text.equals("-")) {
            next();
            ExprNode u = parseUnary();
            if (u == null) {
                return null;
            }
            return ExprNode.unary("-", u);
        }
        return parsePostfix();
    }

    ExprNode parsePostfix() {
        ExprNode n = parsePrimary();
        if (n == null) {
            return null;
        }
        while (peek().kind == ExprTok.FACT) {
            next();
            n = ExprNode.unary("!", n);
        }
        return n;
    }

    ExprNode parsePrimary() {
        ExprTok t = peek();
        if (t.kind == ExprTok.NUM) {
            next();
            return ExprNode.number(t.text);
        }
        if (t.kind == ExprTok.IDENT) {
            next();
            String name = t.text;
            String lower = CalcStr.lower(name);
            if (lower.equals("pi") || lower.equals("e")) {
                return ExprNode.constant(lower);
            }
            if (peek().kind == ExprTok.LPAREN) {
                next();
                ExprNode arg = parseExpr();
                if (arg == null) {
                    return null;
                }
                if (peek().kind != ExprTok.RPAREN) {
                    err = "Expected ')'";
                    return null;
                }
                next();
                return ExprNode.func(lower, arg);
            }
            err = "Unknown identifier: " + name;
            return null;
        }
        if (t.kind == ExprTok.LPAREN) {
            next();
            ExprNode e = parseExpr();
            if (e == null) {
                return null;
            }
            if (peek().kind != ExprTok.RPAREN) {
                err = "Expected ')'";
                return null;
            }
            next();
            return e;
        }
        err = "Unexpected: " + t.text;
        return null;
    }
}

class ExprNode {
    static int NUM = 1;
    static int BIN = 2;
    static int UNARY = 3;
    static int FUNC = 4;
    static int CONST = 5;

    int kind;
    String text;
    ExprNode left;
    ExprNode right;

    static ExprNode number(String s) {
        ExprNode n = new ExprNode();
        n.kind = NUM;
        n.text = s;
        return n;
    }

    static ExprNode constant(String s) {
        ExprNode n = new ExprNode();
        n.kind = CONST;
        n.text = s;
        return n;
    }

    static ExprNode binary(String op, ExprNode a, ExprNode b) {
        ExprNode n = new ExprNode();
        n.kind = BIN;
        n.text = op;
        n.left = a;
        n.right = b;
        return n;
    }

    static ExprNode unary(String op, ExprNode a) {
        ExprNode n = new ExprNode();
        n.kind = UNARY;
        n.text = op;
        n.left = a;
        return n;
    }

    static ExprNode func(String name, ExprNode a) {
        ExprNode n = new ExprNode();
        n.kind = FUNC;
        n.text = name;
        n.left = a;
        return n;
    }
}

class ExprEvalResult {
    int ok;
    double value;
    String error;

    static ExprEvalResult ok(double v) {
        ExprEvalResult r = new ExprEvalResult();
        r.ok = 1;
        r.value = v;
        r.error = "";
        return r;
    }

    static ExprEvalResult fail(String msg) {
        ExprEvalResult r = new ExprEvalResult();
        r.ok = 0;
        r.value = 0.0;
        r.error = msg;
        return r;
    }
}

class ExpressionEvaluator {
    int angleMode;

    ExpressionEvaluator(int angle) {
        angleMode = angle;
    }

    /** Evaluate expression; on error returns diagnostic string via fail. */
    ExprEvalResult evaluate(String src) {
        ArrayList<ExprTok> toks = ExpressionTokenizer.tokenize(src);
        if (toks.size() > 0) {
            ExprTok last = toks.get(toks.size() - 1);
            if (last.kind == ExprTok.END && last.text.startsWith("bad:")) {
                return ExprEvalResult.fail("Invalid character in expression");
            }
        }
        ExpressionParser p = ExpressionParser.of(toks);
        ExprNode ast = p.parse();
        if (ast == null) {
            if (p.err.length() > 0) {
                return ExprEvalResult.fail(p.err);
            }
            return ExprEvalResult.fail("Parse error");
        }
        return evalNode(ast);
    }

    ExprEvalResult evalNode(ExprNode n) {
        if (n.kind == ExprNode.NUM) {
            try {
                Decimal d = Decimal.fromString(n.text);
                return ExprEvalResult.ok(d.toDouble());
            } catch (DecimalException ex) {
                return ExprEvalResult.fail(ex.getMessage());
            }
        }
        if (n.kind == ExprNode.CONST) {
            if (n.text.equals("pi")) {
                return ExprEvalResult.ok(Math.PI);
            }
            if (n.text.equals("e")) {
                return ExprEvalResult.ok(Math.E);
            }
            return ExprEvalResult.fail("Unknown constant");
        }
        if (n.kind == ExprNode.UNARY) {
            ExprEvalResult a = evalNode(n.left);
            if (a.ok == 0) {
                return a;
            }
            if (n.text.equals("-")) {
                return ExprEvalResult.ok(0.0 - a.value);
            }
            if (n.text.equals("!")) {
                return factorial(a.value);
            }
            return ExprEvalResult.fail("Unknown unary");
        }
        if (n.kind == ExprNode.BIN) {
            ExprEvalResult a = evalNode(n.left);
            if (a.ok == 0) {
                return a;
            }
            ExprEvalResult b = evalNode(n.right);
            if (b.ok == 0) {
                return b;
            }
            if (n.text.equals("+")) {
                return ExprEvalResult.ok(a.value + b.value);
            }
            if (n.text.equals("-")) {
                return ExprEvalResult.ok(a.value - b.value);
            }
            if (n.text.equals("*")) {
                return ExprEvalResult.ok(a.value * b.value);
            }
            if (n.text.equals("/")) {
                if (b.value == 0.0) {
                    return ExprEvalResult.fail("Division by zero");
                }
                return ExprEvalResult.ok(a.value / b.value);
            }
            if (n.text.equals("^")) {
                return ExprEvalResult.ok(Math.pow(a.value, b.value));
            }
            return ExprEvalResult.fail("Unknown operator");
        }
        if (n.kind == ExprNode.FUNC) {
            ExprEvalResult a = evalNode(n.left);
            if (a.ok == 0) {
                return a;
            }
            return evalFunc(n.text, a.value);
        }
        return ExprEvalResult.fail("Bad AST");
    }

    ExprEvalResult evalFunc(String name, double x) {
        double arg = x;
        if (name.equals("sin") || name.equals("cos") || name.equals("tan")) {
            if (angleMode == CalculatorState.ANGLE_DEG) {
                arg = Math.toRadians(x);
            }
        }
        if (name.equals("sin")) {
            return ExprEvalResult.ok(Math.sin(arg));
        }
        if (name.equals("cos")) {
            return ExprEvalResult.ok(Math.cos(arg));
        }
        if (name.equals("tan")) {
            return ExprEvalResult.ok(Math.tan(arg));
        }
        if (name.equals("asin")) {
            double r = Math.asin(x);
            if (angleMode == CalculatorState.ANGLE_DEG) {
                r = Math.toDegrees(r);
            }
            return ExprEvalResult.ok(r);
        }
        if (name.equals("acos")) {
            double r = Math.acos(x);
            if (angleMode == CalculatorState.ANGLE_DEG) {
                r = Math.toDegrees(r);
            }
            return ExprEvalResult.ok(r);
        }
        if (name.equals("atan")) {
            double r = Math.atan(x);
            if (angleMode == CalculatorState.ANGLE_DEG) {
                r = Math.toDegrees(r);
            }
            return ExprEvalResult.ok(r);
        }
        if (name.equals("log") || name.equals("log10")) {
            return ExprEvalResult.ok(Math.log10(x));
        }
        if (name.equals("ln") || name.equals("logn")) {
            return ExprEvalResult.ok(Math.log(x));
        }
        if (name.equals("sqrt")) {
            return ExprEvalResult.ok(Math.sqrt(x));
        }
        if (name.equals("exp")) {
            return ExprEvalResult.ok(Math.exp(x));
        }
        return ExprEvalResult.fail("Unknown function: " + name);
    }

    ExprEvalResult factorial(double x) {
        if (x < 0.0) {
            return ExprEvalResult.fail("Factorial of negative");
        }
        double r = Math.floor(x + 1.0e-12);
        if (Math.abs(x - r) > 1.0e-9) {
            return ExprEvalResult.fail("Factorial requires integer");
        }
        if (r > 170.0) {
            return ExprEvalResult.fail("Factorial overflow");
        }
        double acc = 1.0;
        double i = 2.0;
        while (i <= r + 1.0e-9) {
            acc = acc * i;
            i = i + 1.0;
        }
        return ExprEvalResult.ok(acc);
    }
}

class CalcStr {
    static String DIGITS = "0123456789";
    static String LOWER = "abcdefghijklmnopqrstuvwxyz";
    static String UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    static String fromCode(int c) {
        if (c >= 48 && c <= 57) {
            return DIGITS.substring(c - 48, c - 47);
        }
        if (c >= 97 && c <= 122) {
            return LOWER.substring(c - 97, c - 96);
        }
        if (c >= 65 && c <= 90) {
            return UPPER.substring(c - 65, c - 64);
        }
        if (c == 46) {
            return ".";
        }
        if (c == 43) {
            return "+";
        }
        if (c == 45) {
            return "-";
        }
        if (c == 42) {
            return "*";
        }
        if (c == 47) {
            return "/";
        }
        if (c == 94) {
            return "^";
        }
        return "";
    }

    static String lower(String s) {
        String out = "";
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c >= 65 && c <= 90) {
                out = out + LOWER.substring(c - 65, c - 64);
            } else {
                if (c >= 97 && c <= 122) {
                    out = out + LOWER.substring(c - 97, c - 96);
                } else {
                    if (c >= 48 && c <= 57) {
                        out = out + DIGITS.substring(c - 48, c - 47);
                    } else {
                        out = out + fromCode(c);
                    }
                }
            }
            i = i + 1;
        }
        return out;
    }
}

class ScientificEngine {
    CalculatorState state;
    String buffer;

    ScientificEngine(CalculatorState s) {
        state = s;
        buffer = "";
    }

    void input(String key) {
        if (key.equals("AC")) {
            buffer = "";
            state.entry = "0";
            state.display = "0";
            state.expression = "";
            state.clearError();
            return;
        }
        if (key.equals("CE")) {
            buffer = "";
            state.entry = "0";
            state.display = "0";
            return;
        }
        if (key.equals("BS") || key.equals("⌫")) {
            if (buffer.length() > 0) {
                buffer = buffer.substring(0, buffer.length() - 1);
            }
            sync();
            return;
        }
        if (key.equals("DEG") || key.equals("RAD")) {
            if (key.equals("DEG")) {
                state.angleMode = CalculatorState.ANGLE_DEG;
            } else {
                state.angleMode = CalculatorState.ANGLE_RAD;
            }
            return;
        }
        if (key.equals("=")) {
            evaluate();
            return;
        }
        if (key.equals("sin") || key.equals("cos") || key.equals("tan")
                || key.equals("asin") || key.equals("acos") || key.equals("atan")
                || key.equals("log") || key.equals("ln") || key.equals("sqrt") || key.equals("exp")) {
            buffer = buffer + key + "(";
            sync();
            return;
        }
        if (key.equals("pi")) {
            buffer = buffer + "pi";
            sync();
            return;
        }
        if (key.equals("e")) {
            buffer = buffer + "e";
            sync();
            return;
        }
        if (key.equals("x^y") || key.equals("^")) {
            buffer = buffer + "^";
            sync();
            return;
        }
        if (key.equals("!")) {
            buffer = buffer + "!";
            sync();
            return;
        }
        if (key.equals("(") || key.equals(")")) {
            buffer = buffer + key;
            sync();
            return;
        }
        /* digits / ops / dot */
        buffer = buffer + key;
        sync();
    }

    void sync() {
        state.expression = buffer;
        if (buffer.length() == 0) {
            state.display = "0";
            state.entry = "0";
        } else {
            state.display = buffer;
            state.entry = buffer;
        }
    }

    void evaluate() {
        if (buffer.length() == 0) {
            return;
        }
        ExpressionEvaluator ev = new ExpressionEvaluator(state.angleMode);
        ExprEvalResult r = ev.evaluate(buffer);
        if (r.ok == 0) {
            state.setError(r.error);
            return;
        }
        String res = Decimal.fromDouble(r.value).toDisplayString(12);
        state.expression = buffer;
        state.entry = res;
        state.display = res;
        state.lastResult = res;
        buffer = res;
    }
}

/* ======================== Programmer ======================== */

class ProgrammerEngine {
    CalculatorState state;
    long value;
    long pendingLeft;
    String pendingOp;
    int waiting;

    ProgrammerEngine(CalculatorState s) {
        state = s;
        value = 0L;
        pendingLeft = 0L;
        pendingOp = "";
        waiting = 0;
    }

    long mask() {
        if (state.bitWidth >= 64) {
            return 0L - 1L;
        }
        return BitOps.shl64(1L, state.bitWidth) - 1L;
    }

    long applyMask(long v) {
        long m = mask();
        long u = BitOps.and64(v, m);
        if (state.signed != 0 && state.bitWidth < 64) {
            long signBit = BitOps.shl64(1L, state.bitWidth - 1);
            if (BitOps.and64(u, signBit) != 0L) {
                /* sign-extend into long */
                long ext = BitOps.not64(m);
                u = BitOps.or64(u, ext);
            }
        }
        return u;
    }

    void syncDisplayFromEntry() {
        value = parseCurrent();
        state.display = formatValue(value);
        state.entry = state.display;
    }

    void input(String key) {
        if (key.equals("AC")) {
            value = 0L;
            pendingLeft = 0L;
            pendingOp = "";
            waiting = 0;
            state.entry = "0";
            state.display = "0";
            state.expression = "";
            state.clearError();
            return;
        }
        if (key.equals("CE")) {
            value = 0L;
            state.entry = "0";
            state.display = formatValue(0L);
            waiting = 0;
            return;
        }
        if (key.equals("BS") || key.equals("⌫")) {
            if (waiting != 0) {
                return;
            }
            String cur = stripPrefix(state.entry);
            if (cur.length() <= 1) {
                value = 0L;
            } else {
                String next = cur.substring(0, cur.length() - 1);
                value = parseLiteral(next, state.base);
            }
            refreshEntry();
            return;
        }
        if (key.equals("BIN")) {
            state.base = CalculatorState.BASE_BIN;
            refreshEntry();
            return;
        }
        if (key.equals("OCT")) {
            state.base = CalculatorState.BASE_OCT;
            refreshEntry();
            return;
        }
        if (key.equals("DEC")) {
            state.base = CalculatorState.BASE_DEC;
            refreshEntry();
            return;
        }
        if (key.equals("HEX")) {
            state.base = CalculatorState.BASE_HEX;
            refreshEntry();
            return;
        }
        if (key.equals("W8") || key.equals("W16") || key.equals("W32") || key.equals("W64")) {
            if (key.equals("W8")) {
                state.bitWidth = 8;
            } else {
                if (key.equals("W16")) {
                    state.bitWidth = 16;
                } else {
                    if (key.equals("W32")) {
                        state.bitWidth = 32;
                    } else {
                        state.bitWidth = 64;
                    }
                }
            }
            value = applyMask(value);
            refreshEntry();
            return;
        }
        if (key.equals("SIGNED") || key.equals("UNSIGNED")) {
            if (key.equals("SIGNED")) {
                state.signed = 1;
            } else {
                state.signed = 0;
            }
            value = applyMask(value);
            refreshEntry();
            return;
        }
        if (key.equals("AND") || key.equals("OR") || key.equals("XOR")
                || key.equals("<<") || key.equals(">>") || key.equals(">>>")) {
            binOp(key);
            return;
        }
        if (key.equals("NOT")) {
            value = applyMask(BitOps.not64(value));
            refreshEntry();
            waiting = 1;
            return;
        }
        if (key.equals("=")) {
            equalsOp();
            return;
        }
        if (key.equals("+") || key.equals("-") || key.equals("*") || key.equals("/")) {
            binOp(key);
            return;
        }
        if (isProgDigit(key) != 0) {
            digit(key);
            return;
        }
    }

    int isProgDigit(String key) {
        if (key.length() != 1) {
            return 0;
        }
        int c = key.charAt(0);
        if (c >= 48 && c <= 57) {
            int d = c - 48;
            if (d < state.base) {
                return 1;
            }
            return 0;
        }
        if (state.base == CalculatorState.BASE_HEX) {
            if ((c >= 65 && c <= 70) || (c >= 97 && c <= 102)) {
                return 1;
            }
        }
        return 0;
    }

    void digit(String d) {
        if (waiting != 0) {
            state.entry = withPrefix(d);
            waiting = 0;
        } else {
            String body = stripPrefix(state.entry);
            if (body.equals("0")) {
                body = d;
            } else {
                body = body + d;
            }
            state.entry = withPrefix(body);
        }
        value = parseCurrent();
        state.display = state.entry;
    }

    void binOp(String op) {
        if (pendingOp.length() > 0 && waiting == 0) {
            compute();
            if (state.error.length() > 0) {
                return;
            }
        } else {
            pendingLeft = value;
        }
        pendingOp = op;
        state.expression = formatValue(pendingLeft) + " " + op;
        waiting = 1;
    }

    void equalsOp() {
        if (pendingOp.length() == 0) {
            refreshEntry();
            return;
        }
        compute();
        state.expression = state.expression + " " + formatValue(value);
        waiting = 1;
    }

    void compute() {
        long a = pendingLeft;
        long b = value;
        long r = a;
        String op = pendingOp;
        if (op.equals("AND")) {
            r = BitOps.and64(a, b);
        } else {
            if (op.equals("OR")) {
                r = BitOps.or64(a, b);
            } else {
                if (op.equals("XOR")) {
                    r = BitOps.xor64(a, b);
                } else {
                    if (op.equals("<<")) {
                        int n = shiftCount(b);
                        r = BitOps.shl64(a, n);
                    } else {
                        if (op.equals(">>")) {
                            int n2 = shiftCount(b);
                            r = BitOps.shr64(a, n2);
                        } else {
                            if (op.equals(">>>")) {
                                int n3 = shiftCount(b);
                                r = BitOps.ushr64(a, n3);
                            } else {
                                if (op.equals("+")) {
                                    r = a + b;
                                } else {
                                    if (op.equals("-")) {
                                        r = a - b;
                                    } else {
                                        if (op.equals("*")) {
                                            r = a * b;
                                        } else {
                                            if (op.equals("/")) {
                                                if (b == 0L) {
                                                    state.setError("Division by zero");
                                                    pendingOp = "";
                                                    return;
                                                }
                                                r = a / b;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        value = applyMask(r);
        pendingOp = "";
        refreshEntry();
    }

    int shiftCount(long b) {
        long m = BitOps.and64(b, 63L);
        int n = 0;
        while (m > 0L && n < 63) {
            n = n + 1;
            m = m - 1L;
        }
        return n;
    }

    void refreshEntry() {
        state.entry = formatValue(value);
        state.display = state.entry;
    }

    long parseCurrent() {
        return applyMask(parseLiteral(stripPrefix(state.entry), state.base));
    }

    String stripPrefix(String s) {
        if (s == null) {
            return "0";
        }
        String t = s;
        if (t.startsWith("0x") || t.startsWith("0X")) {
            return t.substringFrom(2);
        }
        if (t.startsWith("0b") || t.startsWith("0B")) {
            return t.substringFrom(2);
        }
        if (t.startsWith("0o") || t.startsWith("0O")) {
            return t.substringFrom(2);
        }
        return t;
    }

    String withPrefix(String body) {
        if (state.base == CalculatorState.BASE_HEX) {
            return "0x" + body;
        }
        if (state.base == CalculatorState.BASE_BIN) {
            return "0b" + body;
        }
        if (state.base == CalculatorState.BASE_OCT) {
            return "0o" + body;
        }
        return body;
    }

    long parseLiteral(String body, int base) {
        if (body == null || body.length() == 0) {
            return 0L;
        }
        String s = body;
        int neg = 0;
        if (s.startsWith("-")) {
            neg = 1;
            s = s.substringFrom(1);
        }
        if (s.startsWith("0x") || s.startsWith("0X")) {
            base = 16;
            s = s.substringFrom(2);
        } else {
            if (s.startsWith("0b") || s.startsWith("0B")) {
                base = 2;
                s = s.substringFrom(2);
            } else {
                if (s.startsWith("0o") || s.startsWith("0O")) {
                    base = 8;
                    s = s.substringFrom(2);
                }
            }
        }
        long v = 0L;
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            int d = digitVal(c);
            if (d < 0 || d >= base) {
                return 0L;
            }
            v = v * CalcFmt.longFromInt(base) + CalcFmt.longFromInt(d);
            i = i + 1;
        }
        if (neg != 0) {
            v = 0L - v;
        }
        return v;
    }

    int digitVal(int c) {
        if (c >= 48 && c <= 57) {
            return c - 48;
        }
        if (c >= 65 && c <= 70) {
            return c - 55;
        }
        if (c >= 97 && c <= 102) {
            return c - 87;
        }
        return -1;
    }

    String formatValue(long v) {
        long u = applyMask(v);
        if (state.base == CalculatorState.BASE_HEX) {
            return "0x" + toBase(u, 16);
        }
        if (state.base == CalculatorState.BASE_BIN) {
            return "0b" + toBase(u, 2);
        }
        if (state.base == CalculatorState.BASE_OCT) {
            return "0o" + toBase(u, 8);
        }
        return toDec(u);
    }

    String toDec(long v) {
        return CalcFmt.longToString(v);
    }

    String toBase(long v, int base) {
        long m = mask();
        long u = BitOps.and64(v, m);
        if (u == 0L) {
            return "0";
        }
        String digits = "0123456789ABCDEF";
        String out = "";
        long b = CalcFmt.longFromInt(base);
        while (u != 0L) {
            long rem = u - (u / b) * b;
            int di = 0;
            long t = rem;
            while (t > 0L) {
                di = di + 1;
                t = t - 1L;
            }
            out = digits.substring(di, di + 1) + out;
            u = u / b;
        }
        return out;
    }
}

class CalcFmt {
    static String longToString(long v) {
        if (v == 0L) {
            return "0";
        }
        int neg = 0;
        long n = v;
        if (n < 0L) {
            neg = 1;
            n = 0L - n;
        }
        String out = "";
        while (n > 0L) {
            long rem = n - (n / 10L) * 10L;
            int digit = 0;
            long t = rem;
            while (t > 0L) {
                digit = digit + 1;
                t = t - 1L;
            }
            out = String.valueOf(digit) + out;
            n = n / 10L;
        }
        if (neg != 0) {
            return "-" + out;
        }
        return out;
    }

    static long longFromInt(int v) {
        long r = 0L;
        int n = v;
        if (n < 0) {
            n = 0 - n;
            while (n > 0) {
                r = r + 1L;
                n = n - 1;
            }
            return 0L - r;
        }
        while (n > 0) {
            r = r + 1L;
            n = n - 1;
        }
        return r;
    }
}

