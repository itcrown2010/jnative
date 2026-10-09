import jnative.ui.*;

/**
 * JNative Calculator — native desktop calculator (Standard / Scientific / Programmer).
 *
 * <pre>
 * java -jar target/jnative.jar examples/calculator/JNativeCalculator.java \
 *   -o build/calculator/Calculator --app-type gui
 * ./build/calculator/Calculator
 * </pre>
 *
 * @author 张晓达
 */
public class JNativeCalculator {
    public static void main(String[] args) {
        Theme.setAppearanceMode(Theme.MODE_DARK);
        Application app = new Application();
        CalculatorWindow.show();
        app.run();
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

/* ======================== UI ======================== */

class CalcTheme {
    static int BG = -14935010;
    static int DIGIT = -12961220;
    static int FUNC = -10263706;
    static int OP = -24822;
    static int TEXT = -1;
    static int DRAWER = -13882322;
    static int RADIUS = 14;
    static int KEY_H = 56;
    static int KEY_GAP = 8;
    static int HIST_W = 280;
}

class CalculatorHolder {
    static CalculatorWindow win;
    static CalculatorEngine engine;
}

class CalculatorWindow {
    Window window;
    VBox calcBody;
    PopupWindow historyPopup;
    VBox historyBox;
    Label exprLabel;
    Label resultLabel;
    Label metaLabel;
    GridPane grid;
    Button modeStd;
    Button modeSci;
    Button modeProg;
    Button histToggle;
    CalculatorEngine engine;
    int gridKind;
    int historyOpen;
    HashMap<String, Button> keyButtons;
    Button lastFlash;

    static void show() {
        CalculatorWindow w = new CalculatorWindow();
        CalculatorHolder.win = w;
        CalculatorHolder.engine = w.engine;
        w.window.show();
    }

    CalculatorWindow() {
        engine = new CalculatorEngine();
        gridKind = -1;
        historyOpen = 0;
        keyButtons = new HashMap<String, Button>();
        lastFlash = null;

        window = new Window("JNative Calculator", 420, 620);
        window.setContentMinSize(360, 520);
        window.setBackgroundRgba(CalcTheme.BG);

        calcBody = new VBox();
        calcBody.spacing = 6;
        calcBody.padding = 10;
        calcBody.setBackgroundRgba(CalcTheme.BG);

        HBox topBar = new HBox();
        topBar.spacing = 6;
        topBar.padding = 0;
        topBar.setPrefSize(392, 32);
        topBar.setBackgroundRgba(CalcTheme.BG);
        modeStd = new Button("Standard");
        modeSci = new Button("Scientific");
        modeProg = new Button("Programmer");
        styleModeBtn(modeStd, 1);
        styleModeBtn(modeSci, 0);
        styleModeBtn(modeProg, 0);
        modeStd.onClick(() -> {
            CalculatorActions.setMode(CalculatorState.MODE_STANDARD);
        });
        modeSci.onClick(() -> {
            CalculatorActions.setMode(CalculatorState.MODE_SCIENTIFIC);
        });
        modeProg.onClick(() -> {
            CalculatorActions.setMode(CalculatorState.MODE_PROGRAMMER);
        });
        histToggle = new Button("History");
        histToggle.setFilledStyle(CalcTheme.FUNC, CalcTheme.TEXT, 10, 13);
        histToggle.setPrefSize(78, 28);
        histToggle.onClick(() -> {
            CalculatorActions.toggleHistory();
        });
        topBar.add(modeStd);
        topBar.add(modeSci);
        topBar.add(modeProg);
        topBar.add(histToggle);

        exprLabel = new Label(" ");
        exprLabel.setPrefSize(392, 22);
        exprLabel.setDisplayStyle(16, 2, CalcTheme.FUNC);

        resultLabel = new Label("0");
        resultLabel.setPrefSize(392, 56);
        resultLabel.setDisplayStyle(48, 2, CalcTheme.TEXT);

        metaLabel = new Label(" ");
        metaLabel.setPrefSize(392, 14);
        metaLabel.setDisplayStyle(11, 2, CalcTheme.FUNC);

        grid = newGrid();
        fillGridKeys(grid);

        calcBody.add(topBar);
        calcBody.add(exprLabel);
        calcBody.add(resultLabel);
        calcBody.add(metaLabel);
        calcBody.add(grid);
        window.setContent(calcBody);

        VBox histRoot = new VBox();
        histRoot.spacing = 8;
        histRoot.padding = 12;
        histRoot.setBackgroundRgba(CalcTheme.DRAWER);
        Label histTitle = new Label("History");
        histTitle.setPrefSize(250, 24);
        histTitle.setDisplayStyle(16, 0, CalcTheme.TEXT);
        historyBox = new VBox();
        historyBox.spacing = 6;
        historyBox.setPrefSize(250, 480);
        historyBox.vgrow = 1;
        historyBox.setBackgroundRgba(CalcTheme.DRAWER);
        Button clearHist = new Button("Clear");
        clearHist.setFilledStyle(CalcTheme.FUNC, CalcTheme.TEXT, CalcTheme.RADIUS, 14);
        clearHist.setPrefSize(250, 40);
        clearHist.onClick(() -> {
            CalculatorActions.clearHistory();
        });
        histRoot.add(histTitle);
        histRoot.add(historyBox);
        histRoot.add(clearHist);
        historyPopup = new PopupWindow();
        historyPopup.setContent(histRoot);

        window.onKeyDown(ev -> {
            CalculatorActions.onKey(ev);
        });
        applyModeWindowSize(CalculatorState.MODE_STANDARD);
        refresh();
    }

    void styleModeBtn(Button b, int selected) {
        if (selected != 0) {
            b.setFilledStyle(CalcTheme.OP, CalcTheme.TEXT, 10, 13);
        } else {
            b.setFilledStyle(CalcTheme.FUNC, CalcTheme.TEXT, 10, 13);
        }
        b.setPrefSize(96, 28);
    }

    void syncModeButtons() {
        int mode = engine.state.mode;
        int s0 = 0;
        int s1 = 0;
        int s2 = 0;
        if (mode == CalculatorState.MODE_STANDARD) {
            s0 = 1;
        }
        if (mode == CalculatorState.MODE_SCIENTIFIC) {
            s1 = 1;
        }
        if (mode == CalculatorState.MODE_PROGRAMMER) {
            s2 = 1;
        }
        styleModeBtn(modeStd, s0);
        styleModeBtn(modeSci, s1);
        styleModeBtn(modeProg, s2);
    }

    void applyModeWindowSize(int mode) {
        int w = 420;
        if (mode == CalculatorState.MODE_SCIENTIFIC) {
            w = 680;
        } else {
            if (mode == CalculatorState.MODE_PROGRAMMER) {
                w = 760;
            }
        }
        if (calcBody != null) {
            calcBody.setBounds(0, 0, w, 800);
            calcBody.layout();
            int h = calcBody.prefHeight;
            if (h < 520) {
                h = 520;
            }
            if (h > 900) {
                h = 900;
            }
            window.setContentMinSize(w - 40, 480);
            window.setSize(w, h);
            calcBody.setBounds(0, 0, w, h);
            calcBody.layout();
        } else {
            window.setSize(w, 620);
        }
    }

    GridPane newGrid() {
        GridPane g = new GridPane();
        g.spacing = CalcTheme.KEY_GAP;
        g.padding = 0;
        g.cellW = 88;
        g.cellH = CalcTheme.KEY_H;
        g.setBackgroundRgba(CalcTheme.BG);
        return g;
    }

    void fillGridKeys(GridPane g) {
        keyButtons = new HashMap<String, Button>();
        lastFlash = null;
        int mode = engine.state.mode;
        if (mode == CalculatorState.MODE_STANDARD) {
            addStdKeys(g);
        } else {
            if (mode == CalculatorState.MODE_SCIENTIFIC) {
                g.cellW = 72;
                addSciKeys(g);
            } else {
                g.cellW = 64;
                addProgKeys(g);
            }
        }
        gridKind = mode;
    }

    void rebuildGrid() {
        if (gridKind == engine.state.mode && grid != null) {
            applyModeWindowSize(engine.state.mode);
            return;
        }
        GridPane g = newGrid();
        fillGridKeys(g);
        if (grid != null && grid.parent != null) {
            calcBody.remove(grid);
        }
        grid = g;
        calcBody.add(grid);
        applyModeWindowSize(engine.state.mode);
    }

    void addStyledBtn(GridPane g, String label, String key, int col, int row, int kind) {
        addStyledBtnSpan(g, label, key, col, row, 1, 1, kind);
    }

    void addStyledBtnSpan(GridPane g, String label, String key, int col, int row, int colSpan, int rowSpan, int kind) {
        Button b = new Button(label);
        int bg = CalcTheme.DIGIT;
        int fontPt = 22;
        if (kind == 1) {
            bg = CalcTheme.FUNC;
            fontPt = 18;
        } else {
            if (kind == 2) {
                bg = CalcTheme.OP;
                fontPt = 26;
            } else {
                if (kind == 3) {
                    bg = CalcTheme.OP;
                    fontPt = 28;
                }
            }
        }
        b.setFilledStyle(bg, CalcTheme.TEXT, CalcTheme.RADIUS, fontPt);
        b.setPrefSize(g.cellW * colSpan + (colSpan - 1) * g.spacing, g.cellH);
        b.onClick(() -> {
            CalculatorActions.press(key);
        });
        g.addAtSpan(b, col, row, colSpan, rowSpan);
        keyButtons.put(key, b);
    }

    void addStdKeys(GridPane g) {
        /* kind: 0 digit, 1 func, 2 op, 3 equals */
        addStyledBtn(g, "AC", "AC", 0, 0, 1);
        addStyledBtn(g, "+/−", "+/-", 1, 0, 1);
        addStyledBtn(g, "%", "%", 2, 0, 1);
        addStyledBtn(g, "÷", "/", 3, 0, 2);
        addStyledBtn(g, "7", "7", 0, 1, 0);
        addStyledBtn(g, "8", "8", 1, 1, 0);
        addStyledBtn(g, "9", "9", 2, 1, 0);
        addStyledBtn(g, "×", "*", 3, 1, 2);
        addStyledBtn(g, "4", "4", 0, 2, 0);
        addStyledBtn(g, "5", "5", 1, 2, 0);
        addStyledBtn(g, "6", "6", 2, 2, 0);
        addStyledBtn(g, "−", "-", 3, 2, 2);
        addStyledBtn(g, "1", "1", 0, 3, 0);
        addStyledBtn(g, "2", "2", 1, 3, 0);
        addStyledBtn(g, "3", "3", 2, 3, 0);
        addStyledBtn(g, "+", "+", 3, 3, 2);
        addStyledBtnSpan(g, "0", "0", 0, 4, 2, 1, 0);
        addStyledBtn(g, ".", ".", 2, 4, 0);
        addStyledBtn(g, "=", "=", 3, 4, 3);
        addStyledBtn(g, "CE", "CE", 0, 5, 1);
        addStyledBtn(g, "⌫", "BS", 1, 5, 1);
        addStyledBtn(g, "1/x", "1/x", 2, 5, 1);
        addStyledBtn(g, "√x", "√x", 3, 5, 1);
    }

    void addSciKeys(GridPane g) {
        addStyledBtn(g, "AC", "AC", 0, 0, 1);
        addStyledBtn(g, "DEG", "DEG", 1, 0, 1);
        addStyledBtn(g, "RAD", "RAD", 2, 0, 1);
        addStyledBtn(g, "⌫", "BS", 3, 0, 1);
        addStyledBtn(g, "sin", "sin", 0, 1, 1);
        addStyledBtn(g, "cos", "cos", 1, 1, 1);
        addStyledBtn(g, "tan", "tan", 2, 1, 1);
        addStyledBtn(g, "^", "^", 3, 1, 2);
        addStyledBtn(g, "asin", "asin", 0, 2, 1);
        addStyledBtn(g, "acos", "acos", 1, 2, 1);
        addStyledBtn(g, "atan", "atan", 2, 2, 1);
        addStyledBtn(g, "!", "!", 3, 2, 1);
        addStyledBtn(g, "log", "log", 0, 3, 1);
        addStyledBtn(g, "ln", "ln", 1, 3, 1);
        addStyledBtn(g, "√", "sqrt", 2, 3, 1);
        addStyledBtn(g, "exp", "exp", 3, 3, 1);
        addStyledBtn(g, "π", "pi", 0, 4, 1);
        addStyledBtn(g, "e", "e", 1, 4, 1);
        addStyledBtn(g, "(", "(", 2, 4, 1);
        addStyledBtn(g, ")", ")", 3, 4, 1);
        addStyledBtn(g, "7", "7", 0, 5, 0);
        addStyledBtn(g, "8", "8", 1, 5, 0);
        addStyledBtn(g, "9", "9", 2, 5, 0);
        addStyledBtn(g, "÷", "/", 3, 5, 2);
        addStyledBtn(g, "4", "4", 0, 6, 0);
        addStyledBtn(g, "5", "5", 1, 6, 0);
        addStyledBtn(g, "6", "6", 2, 6, 0);
        addStyledBtn(g, "×", "*", 3, 6, 2);
        addStyledBtn(g, "1", "1", 0, 7, 0);
        addStyledBtn(g, "2", "2", 1, 7, 0);
        addStyledBtn(g, "3", "3", 2, 7, 0);
        addStyledBtn(g, "−", "-", 3, 7, 2);
        addStyledBtn(g, "0", "0", 0, 8, 0);
        addStyledBtn(g, ".", ".", 1, 8, 0);
        addStyledBtn(g, "+", "+", 2, 8, 2);
        addStyledBtn(g, "=", "=", 3, 8, 3);
    }

    void addProgKeys(GridPane g) {
        addStyledBtn(g, "AC", "AC", 0, 0, 1);
        addStyledBtn(g, "HEX", "HEX", 1, 0, 1);
        addStyledBtn(g, "DEC", "DEC", 2, 0, 1);
        addStyledBtn(g, "OCT", "OCT", 3, 0, 1);
        addStyledBtn(g, "BIN", "BIN", 4, 0, 1);
        addStyledBtn(g, "AND", "AND", 0, 1, 1);
        addStyledBtn(g, "OR", "OR", 1, 1, 1);
        addStyledBtn(g, "XOR", "XOR", 2, 1, 1);
        addStyledBtn(g, "NOT", "NOT", 3, 1, 1);
        addStyledBtn(g, "⌫", "BS", 4, 1, 1);
        addStyledBtn(g, "<<", "<<", 0, 2, 1);
        addStyledBtn(g, ">>", ">>", 1, 2, 1);
        addStyledBtn(g, ">>>", ">>>", 2, 2, 1);
        addStyledBtn(g, "64", "W64", 3, 2, 1);
        addStyledBtn(g, "32", "W32", 4, 2, 1);
        addStyledBtn(g, "16", "W16", 0, 3, 1);
        addStyledBtn(g, "8", "W8", 1, 3, 1);
        addStyledBtn(g, "±S", "SIGNED", 2, 3, 1);
        addStyledBtn(g, "U", "UNSIGNED", 3, 3, 1);
        addStyledBtn(g, "÷", "/", 4, 3, 2);
        addStyledBtn(g, "A", "A", 0, 4, 0);
        addStyledBtn(g, "B", "B", 1, 4, 0);
        addStyledBtn(g, "C", "C", 2, 4, 0);
        addStyledBtn(g, "D", "D", 3, 4, 0);
        addStyledBtn(g, "×", "*", 4, 4, 2);
        addStyledBtn(g, "E", "E", 0, 5, 0);
        addStyledBtn(g, "F", "F", 1, 5, 0);
        addStyledBtn(g, "7", "7", 2, 5, 0);
        addStyledBtn(g, "8", "8", 3, 5, 0);
        addStyledBtn(g, "−", "-", 4, 5, 2);
        addStyledBtn(g, "9", "9", 0, 6, 0);
        addStyledBtn(g, "4", "4", 1, 6, 0);
        addStyledBtn(g, "5", "5", 2, 6, 0);
        addStyledBtn(g, "6", "6", 3, 6, 0);
        addStyledBtn(g, "+", "+", 4, 6, 2);
        addStyledBtn(g, "1", "1", 0, 7, 0);
        addStyledBtn(g, "2", "2", 1, 7, 0);
        addStyledBtn(g, "3", "3", 2, 7, 0);
        addStyledBtn(g, "0", "0", 3, 7, 0);
        addStyledBtn(g, "=", "=", 4, 7, 3);
    }

    void flashKey(String key) {
        if (lastFlash != null) {
            lastFlash.flashPressed(0);
            lastFlash = null;
        }
        Button b = keyButtons.get(key);
        if (b == null) {
            return;
        }
        b.flashPressed(1);
        lastFlash = b;
    }

    void setHistoryOpen(int open) {
        historyOpen = open;
        if (open != 0) {
            refreshHistory();
            histToggle.setText("History ▸");
            int x = window.x + window.width - CalcTheme.HIST_W;
            int y = window.y;
            if (x < 0) {
                x = 0;
            }
            historyPopup.showAt(x, y, CalcTheme.HIST_W, window.height);
        } else {
            histToggle.setText("History");
            historyPopup.hide();
        }
    }

    void refresh() {
        CalculatorState s = engine.state;
        exprLabel.setText(s.expression);
        String disp = s.display;
        int fontPt = 56;
        if (disp != null && disp.length() > 12) {
            fontPt = 40;
        }
        if (disp != null && disp.length() > 18) {
            fontPt = 28;
        }
        if (disp != null && disp.length() > 28) {
            fontPt = 20;
        }
        resultLabel.setText(disp);
        resultLabel.setDisplayStyle(fontPt, 2, CalcTheme.TEXT);
        if (s.error.length() > 0) {
            metaLabel.setText(s.error);
        } else {
            String meta = "";
            if (s.mode == CalculatorState.MODE_SCIENTIFIC) {
                if (s.angleMode == CalculatorState.ANGLE_DEG) {
                    meta = "DEG";
                } else {
                    meta = "RAD";
                }
            } else {
                if (s.mode == CalculatorState.MODE_PROGRAMMER) {
                    meta = "w" + String.valueOf(s.bitWidth);
                    if (s.signed != 0) {
                        meta = meta + " signed";
                    } else {
                        meta = meta + " unsigned";
                    }
                }
            }
            metaLabel.setText(meta);
        }
        if (historyOpen != 0) {
            refreshHistory();
        }
    }

    void refreshHistory() {
        historyBox.clear();
        ArrayList<HistoryEntry> items = engine.history.get();
        int i = items.size() - 1;
        int shown = 0;
        while (i >= 0 && shown < 16) {
            HistoryEntry e = items.get(i);
            addHistoryButton(e.expr, e.result);
            shown = shown + 1;
            i = i - 1;
        }
    }

    void addHistoryButton(String expr, String result) {
        Button hb = new Button(expr + " = " + result);
        hb.setFilledStyle(CalcTheme.DIGIT, CalcTheme.TEXT, 10, 13);
        hb.setPrefSize(250, 36);
        hb.onClick(() -> {
            CalculatorActions.recallHistory(expr, result);
        });
        historyBox.add(hb);
    }
}

class CalculatorActions {
    static void setMode(int mode) {
        CalculatorWindow w = CalculatorHolder.win;
        w.engine.setMode(mode);
        w.rebuildGrid();
        w.syncModeButtons();
        w.refresh();
    }

    static void toggleHistory() {
        CalculatorWindow w = CalculatorHolder.win;
        if (w.historyOpen != 0) {
            w.setHistoryOpen(0);
        } else {
            w.setHistoryOpen(1);
        }
    }

    static void press(String key) {
        CalculatorWindow w = CalculatorHolder.win;
        w.flashKey(key);
        CalculatorEngine eng = w.engine;
        String beforeExpr = eng.state.expression;
        eng.input(key);
        if (key.equals("=") && eng.state.error.length() == 0) {
            String expr = eng.state.expression;
            if (expr.length() == 0) {
                expr = beforeExpr;
            }
            eng.recordHistory(expr, eng.state.display);
        }
        w.refresh();
    }

    static void clearHistory() {
        CalculatorWindow w = CalculatorHolder.win;
        w.engine.history.clear();
        w.refresh();
    }

    static void recallHistory(String expr, String result) {
        CalculatorWindow w = CalculatorHolder.win;
        CalculatorEngine eng = w.engine;
        eng.state.expression = expr;
        eng.state.display = result;
        eng.state.entry = result;
        eng.state.lastResult = result;
        eng.state.error = "";
        eng.state.waitingForOperand = 1;
        if (expr != null && expr.length() > 0) {
            ExpressionEvaluator ev = new ExpressionEvaluator(eng.state.angleMode);
            ExprEvalResult r = ev.evaluate(expr);
            if (r.ok != 0) {
                Decimal d = Decimal.fromDouble(r.value);
                String formatted = d.toDisplayString(12);
                eng.state.display = formatted;
                eng.state.entry = formatted;
                eng.state.lastResult = formatted;
            } else {
                eng.state.error = r.error;
            }
        }
        w.refresh();
    }

    static void onKey(KeyEvent ev) {
        int code = ev.keyCode;
        if ((ev.metaDown != 0 || ev.ctrlDown != 0) && code == 67) {
            Clipboard.setText(CalculatorHolder.win.engine.state.display);
            return;
        }
        if ((ev.metaDown != 0 || ev.ctrlDown != 0) && code == 86) {
            String t = Clipboard.getText();
            if (t != null && t.length() > 0) {
                CalculatorHolder.win.engine.state.entry = t;
                CalculatorHolder.win.engine.state.display = t;
                CalculatorHolder.win.refresh();
            }
            return;
        }
        if (code == KeyCode.ESCAPE) {
            press("AC");
            return;
        }
        if (code == KeyCode.BACKSPACE) {
            press("BS");
            return;
        }
        if (code == KeyCode.ENTER || code == 61) {
            press("=");
            return;
        }
        if (code >= KeyCode.DIGIT0 && code <= KeyCode.DIGIT9) {
            press(CalcStr.fromCode(code));
            return;
        }
        if (code >= 96 && code <= 105) {
            int d = code - 96;
            press(CalcStr.DIGITS.substring(d, d + 1));
            return;
        }
        if (code == 46 || code == 110) {
            press(".");
            return;
        }
        if (code == 43 || code == 107) {
            press("+");
            return;
        }
        if (code == 45 || code == 109) {
            press("-");
            return;
        }
        if (code == 42 || code == 106) {
            press("*");
            return;
        }
        if (code == 47 || code == 111) {
            press("/");
            return;
        }
        if (code == 94) {
            press("^");
            return;
        }
        if (code >= 65 && code <= 70) {
            if (CalculatorHolder.win.engine.state.mode == CalculatorState.MODE_PROGRAMMER) {
                press(CalcStr.fromCode(code));
            }
        }
    }
}
