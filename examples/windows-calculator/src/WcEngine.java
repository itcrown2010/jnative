/**
 * JNative Windows Calculator engine — Standard / Scientific / Programmer.
 *
 * Mirrors the in-box Windows Calculator model: an expression line, an
 * entry/value display, immediate execution in Standard, operator precedence +
 * parentheses in Scientific/Programmer, repeat "=", memory stack, history,
 * and the Windows error strings. No UI types; fully headless-testable.
 *
 * @author 张晓达
 */
class WcEngine {
    static final int MODE_STD = 0;
    static final int MODE_SCI = 1;
    static final int MODE_PROG = 2;

    static final int ST_TYPING = 0;
    static final int ST_VALUE = 1;
    static final int ST_AFTER_OP = 2;
    static final int ST_RESULT = 3;
    static final int ST_ERROR = 4;

    static final int MEM_MAX = 100;
    static final int HIST_MAX = 200;

    int mode;
    int angle;
    int fe;
    int base;
    int bits;
    int state;
    int expMode;

    String entry;
    WcNum cur;
    String curText;
    String errorText;
    String expr;
    String finalExpr;

    WcNum[] vals;
    int valN;
    String[] ops;
    int opN;
    int[] parenStart;
    int parenN;

    String lastOp;
    WcNum lastRhs;

    WcNum[] mem;
    int memN;

    String[] histExpr;
    String[] histText;
    WcNum[] histVal;
    int histN;

    WcEngine() {
        mode = MODE_STD;
        angle = WcMath.DEG;
        fe = 0;
        base = 10;
        bits = 64;
        vals = new WcNum[128];
        ops = new String[128];
        parenStart = new int[64];
        mem = new WcNum[MEM_MAX];
        histExpr = new String[HIST_MAX];
        histText = new String[HIST_MAX];
        histVal = new WcNum[HIST_MAX];
        memN = 0;
        histN = 0;
        resetAll();
    }

    /* ---------------------------------------------------------------- */
    /* State                                                             */
    /* ---------------------------------------------------------------- */

    void resetAll() {
        entry = "0";
        expMode = 0;
        cur = WcNum.ZERO;
        curText = null;
        errorText = "";
        expr = "";
        finalExpr = "";
        valN = 0;
        opN = 0;
        parenN = 0;
        lastOp = "";
        lastRhs = WcNum.ZERO;
        state = ST_TYPING;
    }

    void clearExpression() {
        expr = "";
        finalExpr = "";
        valN = 0;
        opN = 0;
        parenN = 0;
    }

    int usesPrecedence() {
        if (mode == MODE_STD) {
            return 0;
        }
        return 1;
    }

    int maxDigits() {
        if (mode == MODE_SCI) {
            return 32;
        }
        return 16;
    }

    int displaySig() {
        if (mode == MODE_SCI) {
            return 32;
        }
        return 16;
    }

    void setMode(int m) {
        if (m == mode) {
            return;
        }
        WcNum keep = cur;
        int wasError = 0;
        if (state == ST_ERROR) {
            wasError = 1;
        }
        mode = m;
        clearExpression();
        lastOp = "";
        curText = null;
        expMode = 0;
        errorText = "";
        if (wasError != 0) {
            keep = WcNum.ZERO;
        }
        if (m == MODE_PROG) {
            WcNum t = keep.truncate();
            if (t.fitsLong() == 0) {
                t = WcNum.ZERO;
            }
            cur = WcNum.fromLong(WcProg.wrap(t.toLong(), bits));
        } else {
            cur = keep;
        }
        state = ST_VALUE;
        entry = "0";
    }

    /* ---------------------------------------------------------------- */
    /* Display                                                           */
    /* ---------------------------------------------------------------- */

    String fmt(WcNum v) {
        if (mode == MODE_PROG) {
            return WcFmt.progText(v.toLong(), base, bits, 1);
        }
        return WcFmt.format(v, displaySig(), 1, fe);
    }

    String displayText() {
        if (state == ST_ERROR) {
            return errorText;
        }
        if (state == ST_TYPING) {
            if (mode == MODE_PROG) {
                return WcFmt.progText(cur.toLong(), base, bits, 1);
            }
            return WcFmt.groupEntry(entry);
        }
        return fmt(cur);
    }

    String expressionText() {
        if (state == ST_ERROR) {
            return expr;
        }
        if (state == ST_RESULT) {
            return finalExpr;
        }
        if (curText != null && state == ST_VALUE) {
            return expr + curText;
        }
        return expr;
    }

    String radixText(int b) {
        if (state == ST_ERROR) {
            return "0";
        }
        return WcFmt.progText(cur.toLong(), b, bits, 1);
    }

    int hasMemory() {
        if (memN > 0) {
            return 1;
        }
        return 0;
    }

    int inError() {
        if (state == ST_ERROR) {
            return 1;
        }
        return 0;
    }

    String angleName() {
        if (angle == WcMath.RAD) {
            return "RAD";
        }
        if (angle == WcMath.GRAD) {
            return "GRAD";
        }
        return "DEG";
    }

    int openParens() {
        return parenN;
    }

    /* ---------------------------------------------------------------- */
    /* Errors                                                            */
    /* ---------------------------------------------------------------- */

    void setError(int kind) {
        errorText = WcErr.message(kind);
        state = ST_ERROR;
        entry = "0";
        expMode = 0;
    }

    /* ---------------------------------------------------------------- */
    /* Entry helpers                                                     */
    /* ---------------------------------------------------------------- */

    void syncCurFromEntry() {
        if (mode == MODE_PROG) {
            cur = WcNum.fromLong(WcProg.parseEntry(entry, base, bits));
            return;
        }
        WcNum v = WcNum.parse(entry);
        if (v == null) {
            v = WcNum.ZERO;
        }
        cur = v;
    }

    void beginEntry() {
        if (state == ST_ERROR) {
            resetAll();
        } else {
            if (state == ST_RESULT) {
                clearExpression();
                curText = null;
            }
        }
        if (state != ST_TYPING) {
            entry = "";
            expMode = 0;
            curText = null;
            state = ST_TYPING;
        }
    }

    int digitCount(String e) {
        int n = 0;
        int i = 0;
        int stop = e.length();
        int ep = e.indexOf("e");
        if (ep >= 0) {
            stop = ep;
        }
        while (i < stop) {
            int c = e.charAt(i);
            if (c >= 48 && c <= 57) {
                n = n + 1;
            }
            i = i + 1;
        }
        return n;
    }

    int digitValue(String key) {
        int c = key.charAt(0);
        if (c >= 48 && c <= 57) {
            return c - 48;
        }
        if (c >= 65 && c <= 70) {
            return c - 55;
        }
        return -1;
    }

    void inputDigit(String key) {
        int dv = digitValue(key);
        if (dv < 0) {
            return;
        }
        if (mode == MODE_PROG) {
            if (dv >= base) {
                return;
            }
        } else {
            if (dv > 9) {
                return;
            }
        }
        beginEntry();
        if (mode == MODE_PROG) {
            String cand = entry + key;
            String stripped = cand;
            while (stripped.length() > 1 && stripped.charAt(0) == 48) {
                stripped = stripped.substringFrom(1);
            }
            if (WcProg.entryFits(stripped, base, bits) == 0) {
                return;
            }
            entry = stripped;
            syncCurFromEntry();
            return;
        }
        if (expMode != 0) {
            int ep = entry.indexOf("e");
            String expPart = entry.substringFrom(ep + 2);
            if (expPart.length() >= 4) {
                return;
            }
            if (expPart.equals("0")) {
                expPart = "";
            }
            entry = entry.substring(0, ep + 2) + expPart + key;
            syncCurFromEntry();
            return;
        }
        if (digitCount(entry) >= maxDigits()) {
            return;
        }
        if (entry.equals("0")) {
            entry = key;
        } else {
            if (entry.equals("-0")) {
                entry = "-" + key;
            } else {
                entry = entry + key;
            }
        }
        syncCurFromEntry();
    }

    void inputPoint() {
        if (mode == MODE_PROG) {
            return;
        }
        if (state == ST_ERROR) {
            resetAll();
        }
        if (state != ST_TYPING) {
            beginEntry();
            entry = "0.";
            syncCurFromEntry();
            return;
        }
        if (expMode != 0 || entry.indexOf(".") >= 0) {
            return;
        }
        entry = entry + ".";
        syncCurFromEntry();
    }

    void inputExp() {
        if (mode != MODE_SCI) {
            return;
        }
        if (state == ST_ERROR) {
            return;
        }
        if (state != ST_TYPING) {
            String t = WcFmt.format(cur, 16, 0, 0);
            if (t.indexOf("e") >= 0) {
                return;
            }
            beginEntry();
            entry = t;
        }
        if (expMode != 0) {
            return;
        }
        if (entry.indexOf(".") < 0) {
            entry = entry + ".";
        }
        entry = entry + "e+0";
        expMode = 1;
        syncCurFromEntry();
    }

    void backspace() {
        if (state != ST_TYPING) {
            return;
        }
        if (mode == MODE_PROG) {
            if (entry.length() <= 1) {
                entry = "0";
            } else {
                entry = entry.substring(0, entry.length() - 1);
            }
            syncCurFromEntry();
            return;
        }
        if (expMode != 0) {
            int ep = entry.indexOf("e");
            String expPart = entry.substringFrom(ep + 2);
            if (expPart.length() > 1) {
                entry = entry.substring(0, entry.length() - 1);
            } else {
                if (expPart.equals("0")) {
                    entry = entry.substring(0, ep);
                    expMode = 0;
                } else {
                    entry = entry.substring(0, ep + 2) + "0";
                }
            }
            syncCurFromEntry();
            return;
        }
        String e = entry;
        if (e.length() <= 1) {
            e = "0";
        } else {
            e = e.substring(0, e.length() - 1);
            if (e.equals("-")) {
                e = "0";
            }
        }
        entry = e;
        syncCurFromEntry();
    }

    /* ---------------------------------------------------------------- */
    /* Operators                                                         */
    /* ---------------------------------------------------------------- */

    static String opSymbol(String op) {
        if (op.equals("+")) {
            return "+";
        }
        if (op.equals("-")) {
            return "−";
        }
        if (op.equals("*")) {
            return "×";
        }
        if (op.equals("/")) {
            return "÷";
        }
        if (op.equals("pow")) {
            return "^";
        }
        if (op.equals("yroot")) {
            return "yroot";
        }
        if (op.equals("mod")) {
            return "mod";
        }
        if (op.equals("logy")) {
            return "logy";
        }
        if (op.equals("and")) {
            return "AND";
        }
        if (op.equals("or")) {
            return "OR";
        }
        if (op.equals("xor")) {
            return "XOR";
        }
        if (op.equals("nand")) {
            return "NAND";
        }
        if (op.equals("nor")) {
            return "NOR";
        }
        if (op.equals("lsh")) {
            return "<<";
        }
        if (op.equals("rsh")) {
            return ">>";
        }
        if (op.equals("rshl")) {
            return ">>>";
        }
        if (op.equals("rol")) {
            return "RoL";
        }
        if (op.equals("ror")) {
            return "RoR";
        }
        return op;
    }

    int precedence(String op) {
        if (usesPrecedence() == 0) {
            return 1;
        }
        if (op.equals("or") || op.equals("xor")) {
            return 1;
        }
        if (op.equals("and") || op.equals("nand") || op.equals("nor")) {
            return 2;
        }
        if (op.equals("+") || op.equals("-")) {
            return 3;
        }
        if (op.equals("pow") || op.equals("yroot") || op.equals("logy")) {
            return 5;
        }
        return 4;
    }

    int isBinaryKey(String key) {
        if (key.equals("+") || key.equals("-") || key.equals("*") || key.equals("/")) {
            return 1;
        }
        if (mode == MODE_SCI) {
            if (key.equals("pow") || key.equals("yroot") || key.equals("mod") || key.equals("logy")) {
                return 1;
            }
        }
        if (mode == MODE_PROG) {
            if (key.equals("mod") || key.equals("and") || key.equals("or") || key.equals("xor")
                    || key.equals("nand") || key.equals("nor") || key.equals("lsh")
                    || key.equals("rsh") || key.equals("rshl") || key.equals("rol")
                    || key.equals("ror")) {
                return 1;
            }
        }
        return 0;
    }

    WcNum applyBinary(String op, WcNum a, WcNum b) {
        if (mode == MODE_PROG) {
            return WcNum.fromLong(WcProg.binary(op, a.toLong(), b.toLong(), bits));
        }
        if (op.equals("+")) {
            return a.add(b);
        }
        if (op.equals("-")) {
            return a.sub(b);
        }
        if (op.equals("*")) {
            return a.mul(b);
        }
        if (op.equals("/")) {
            return a.div(b);
        }
        if (op.equals("pow")) {
            return WcMath.pow(a, b);
        }
        if (op.equals("yroot")) {
            return WcMath.yroot(a, b);
        }
        if (op.equals("mod")) {
            return a.rem(b);
        }
        if (op.equals("logy")) {
            return WcMath.logBase(a, b);
        }
        throw WcErr.make(WcErr.INVALID);
    }

    void pushVal(WcNum v) {
        if (valN >= vals.length) {
            throw WcErr.make(WcErr.OVERFLOW);
        }
        vals[valN] = v;
        valN = valN + 1;
    }

    void pushOp(String o) {
        if (opN >= ops.length) {
            throw WcErr.make(WcErr.OVERFLOW);
        }
        ops[opN] = o;
        opN = opN + 1;
    }

    /** Apply the top operator to the top two values. */
    void reduceTop() {
        String op = ops[opN - 1];
        opN = opN - 1;
        WcNum b = vals[valN - 1];
        WcNum a = vals[valN - 2];
        valN = valN - 2;
        WcNum r = applyBinary(op, a, b);
        lastOp = op;
        lastRhs = b;
        pushVal(r);
    }

    String operandText() {
        if (curText != null) {
            return curText;
        }
        return fmt(cur);
    }

    void binaryOp(String key) {
        if (state == ST_ERROR) {
            return;
        }
        String sym = opSymbol(key);
        int afterBinary = 0;
        if (state == ST_AFTER_OP && opN > 0 && !ops[opN - 1].equals("(")) {
            afterBinary = 1;
        }
        if (afterBinary != 0) {
            /* replace the pending operator */
            String old = ops[opN - 1];
            String oldSym = opSymbol(old);
            String tail = oldSym + " ";
            if (expr.endsWith(tail)) {
                expr = expr.substring(0, expr.length() - tail.length()) + sym + " ";
            }
            ops[opN - 1] = key;
            return;
        }
        try {
            if (state == ST_RESULT) {
                clearExpression();
                lastOp = "";
            }
            String t = operandText();
            expr = expr + t + " " + sym + " ";
            curText = null;
            pushVal(cur);
            while (opN > 0 && !ops[opN - 1].equals("(")
                    && precedence(ops[opN - 1]) >= precedence(key)) {
                reduceTop();
            }
            pushOp(key);
            cur = vals[valN - 1];
            state = ST_AFTER_OP;
            entry = "0";
            expMode = 0;
        } catch (WcException ex) {
            setError(ex.kind);
        }
    }

    /** "(" starts an operand: after an operator, a result, or on a fresh expression. */
    int canOpenParen() {
        if (state == ST_AFTER_OP || state == ST_RESULT) {
            return 1;
        }
        if (state == ST_TYPING && entry.equals("0") && valN == 0 && opN == 0 && expr.length() == 0) {
            return 1;
        }
        return 0;
    }

    void openParen() {
        if (state == ST_ERROR) {
            return;
        }
        if (usesPrecedence() == 0 || mode == MODE_STD) {
            return;
        }
        if (canOpenParen() == 0) {
            return;
        }
        if (state == ST_RESULT) {
            clearExpression();
            lastOp = "";
        }
        if (parenN >= parenStart.length) {
            return;
        }
        try {
            parenStart[parenN] = expr.length();
            parenN = parenN + 1;
            expr = expr + "(";
            pushOp("(");
            cur = WcNum.ZERO;
            curText = null;
            entry = "0";
            expMode = 0;
            state = ST_AFTER_OP;
        } catch (WcException ex) {
            setError(ex.kind);
        }
    }

    void closeParen() {
        if (state == ST_ERROR || parenN == 0) {
            return;
        }
        try {
            String t = operandText();
            pushVal(cur);
            while (opN > 0 && !ops[opN - 1].equals("(")) {
                reduceTop();
            }
            if (opN > 0) {
                opN = opN - 1;
            }
            WcNum r = vals[valN - 1];
            valN = valN - 1;
            parenN = parenN - 1;
            int start = parenStart[parenN];
            String inner = expr.substringFrom(start);
            expr = expr.substring(0, start);
            curText = inner + t + ")";
            cur = r;
            state = ST_VALUE;
            entry = "0";
            expMode = 0;
        } catch (WcException ex) {
            setError(ex.kind);
        }
    }

    void equalsKey() {
        if (state == ST_ERROR) {
            return;
        }
        try {
            if (state == ST_RESULT) {
                if (lastOp.length() > 0) {
                    WcNum before = cur;
                    String lhsText = fmt(before);
                    WcNum r = applyBinary(lastOp, before, lastRhs);
                    String e = lhsText + " " + opSymbol(lastOp) + " " + fmt(lastRhs) + " =";
                    cur = r;
                    curText = null;
                    finalExpr = e;
                    addHistory(e, r);
                }
                return;
            }
            if (opN == 0 && valN == 0) {
                String e = operandText() + " =";
                finalExpr = e;
                curText = null;
                entry = "0";
                expMode = 0;
                state = ST_RESULT;
                addHistory(e, cur);
                lastOp = "";
                return;
            }
            String t = operandText();
            String e = expr + t;
            int p = 0;
            while (p < parenN) {
                e = e + ")";
                p = p + 1;
            }
            e = e + " =";
            pushVal(cur);
            while (opN > 0) {
                if (ops[opN - 1].equals("(")) {
                    opN = opN - 1;
                } else {
                    reduceTop();
                }
            }
            WcNum r = vals[valN - 1];
            valN = 0;
            opN = 0;
            parenN = 0;
            cur = r;
            curText = null;
            expr = "";
            finalExpr = e;
            entry = "0";
            expMode = 0;
            state = ST_RESULT;
            addHistory(e, r);
        } catch (WcException ex) {
            setError(ex.kind);
        }
    }

    /* ---------------------------------------------------------------- */
    /* Unary                                                             */
    /* ---------------------------------------------------------------- */

    String unaryLabel(String key) {
        if (key.equals("sqr")) {
            return "sqr";
        }
        if (key.equals("cube")) {
            return "cube";
        }
        if (key.equals("sqrt")) {
            return "√";
        }
        if (key.equals("cbrt")) {
            return "∛";
        }
        if (key.equals("recip")) {
            return "1/";
        }
        if (key.equals("neg")) {
            return "negate";
        }
        if (key.equals("pow10")) {
            return "10^";
        }
        if (key.equals("pow2")) {
            return "2^";
        }
        if (key.equals("epow")) {
            return "e^";
        }
        if (key.equals("not")) {
            return "NOT";
        }
        if (key.startsWith("a") && key.length() > 3 && !key.equals("abs")) {
            return key.substringFrom(1) + "⁻¹";
        }
        return key;
    }

    WcNum computeUnary(String key, WcNum x) {
        if (mode == MODE_PROG) {
            if (key.equals("not")) {
                return WcNum.fromLong(WcProg.wrap(~x.toLong(), bits));
            }
            if (key.equals("neg")) {
                return WcNum.fromLong(WcProg.wrap(0L - x.toLong(), bits));
            }
            throw WcErr.make(WcErr.INVALID);
        }
        if (key.equals("neg")) {
            return x.negate();
        }
        if (key.equals("sqr")) {
            return x.mul(x);
        }
        if (key.equals("cube")) {
            return x.mul(x).mul(x);
        }
        if (key.equals("sqrt")) {
            return WcMath.sqrt(x);
        }
        if (key.equals("cbrt")) {
            return WcMath.cbrt(x);
        }
        if (key.equals("recip")) {
            return WcNum.ONE.div(x);
        }
        if (key.equals("abs")) {
            return x.abs();
        }
        if (key.equals("fact")) {
            return WcMath.factorial(x);
        }
        if (key.equals("ln")) {
            return WcMath.ln(x);
        }
        if (key.equals("log")) {
            return WcMath.log10(x);
        }
        if (key.equals("pow10")) {
            return WcMath.pow10(x);
        }
        if (key.equals("pow2")) {
            return WcMath.pow2(x);
        }
        if (key.equals("epow")) {
            return WcMath.expE(x);
        }
        if (key.equals("floor")) {
            return x.floorNum();
        }
        if (key.equals("ceil")) {
            return x.ceilNum();
        }
        if (key.equals("dms")) {
            return WcMath.toDms(x);
        }
        if (key.equals("deg")) {
            return WcMath.fromDms(x);
        }
        if (key.equals("sin")) {
            return WcMath.sin(x, angle);
        }
        if (key.equals("cos")) {
            return WcMath.cos(x, angle);
        }
        if (key.equals("tan")) {
            return WcMath.tan(x, angle);
        }
        if (key.equals("sec")) {
            return WcMath.sec(x, angle);
        }
        if (key.equals("csc")) {
            return WcMath.csc(x, angle);
        }
        if (key.equals("cot")) {
            return WcMath.cot(x, angle);
        }
        if (key.equals("asin")) {
            return WcMath.asin(x, angle);
        }
        if (key.equals("acos")) {
            return WcMath.acos(x, angle);
        }
        if (key.equals("atan")) {
            return WcMath.atan(x, angle);
        }
        if (key.equals("asec")) {
            return WcMath.asec(x, angle);
        }
        if (key.equals("acsc")) {
            return WcMath.acsc(x, angle);
        }
        if (key.equals("acot")) {
            return WcMath.acot(x, angle);
        }
        if (key.equals("sinh")) {
            return WcMath.sinh(x);
        }
        if (key.equals("cosh")) {
            return WcMath.cosh(x);
        }
        if (key.equals("tanh")) {
            return WcMath.tanh(x);
        }
        if (key.equals("sech")) {
            return WcMath.sech(x);
        }
        if (key.equals("csch")) {
            return WcMath.csch(x);
        }
        if (key.equals("coth")) {
            return WcMath.coth(x);
        }
        if (key.equals("asinh")) {
            return WcMath.asinh(x);
        }
        if (key.equals("acosh")) {
            return WcMath.acosh(x);
        }
        if (key.equals("atanh")) {
            return WcMath.atanh(x);
        }
        if (key.equals("asech")) {
            return WcMath.asech(x);
        }
        if (key.equals("acsch")) {
            return WcMath.acsch(x);
        }
        if (key.equals("acoth")) {
            return WcMath.acoth(x);
        }
        throw WcErr.make(WcErr.INVALID);
    }

    void unary(String key) {
        if (state == ST_ERROR) {
            return;
        }
        if (key.equals("neg") && state == ST_TYPING && mode != MODE_PROG) {
            negateEntry();
            return;
        }
        try {
            if (state == ST_RESULT) {
                clearExpression();
                curText = null;
            }
            String t = operandText();
            WcNum r = computeUnary(key, cur);
            curText = unaryLabel(key) + "(" + t + ")";
            cur = r;
            state = ST_VALUE;
            entry = "0";
            expMode = 0;
        } catch (WcException ex) {
            setError(ex.kind);
        }
    }

    void negateEntry() {
        if (expMode != 0) {
            int ep = entry.indexOf("e");
            String sgn = entry.substring(ep + 1, ep + 2);
            String flip = "-";
            if (sgn.equals("-")) {
                flip = "+";
            }
            entry = entry.substring(0, ep + 1) + flip + entry.substringFrom(ep + 2);
            syncCurFromEntry();
            return;
        }
        if (cur.sign == 0 && entry.indexOf(".") < 0) {
            return;
        }
        if (entry.length() > 0 && entry.charAt(0) == 45) {
            entry = entry.substringFrom(1);
        } else {
            entry = "-" + entry;
        }
        syncCurFromEntry();
    }

    void percent() {
        if (state == ST_ERROR || mode != MODE_STD) {
            return;
        }
        try {
            if (state == ST_RESULT) {
                clearExpression();
            }
            WcNum v;
            if (opN > 0) {
                WcNum lhs = vals[valN - 1];
                String op = ops[opN - 1];
                if (op.equals("+") || op.equals("-")) {
                    v = lhs.mul(cur).div(WcNum.fromLong(100L));
                } else {
                    v = cur.div(WcNum.fromLong(100L));
                }
            } else {
                v = WcNum.ZERO;
            }
            cur = v;
            curText = fmt(v);
            state = ST_VALUE;
            entry = "0";
            expMode = 0;
        } catch (WcException ex) {
            setError(ex.kind);
        }
    }

    void setConstant(WcNum v) {
        if (state == ST_ERROR) {
            resetAll();
        }
        if (state == ST_RESULT) {
            clearExpression();
        }
        cur = v;
        curText = null;
        state = ST_VALUE;
        entry = "0";
        expMode = 0;
    }

    /* ---------------------------------------------------------------- */
    /* Memory                                                            */
    /* ---------------------------------------------------------------- */

    void afterMemoryOp() {
        if (state == ST_TYPING) {
            state = ST_VALUE;
        }
        entry = "0";
        expMode = 0;
    }

    void memStore() {
        if (state == ST_ERROR) {
            return;
        }
        int i = memN;
        if (i >= MEM_MAX) {
            i = MEM_MAX - 1;
        } else {
            memN = memN + 1;
        }
        while (i > 0) {
            mem[i] = mem[i - 1];
            i = i - 1;
        }
        mem[0] = cur;
        afterMemoryOp();
    }

    void memRecall() {
        if (state == ST_ERROR || memN == 0) {
            return;
        }
        recallValue(mem[0]);
    }

    void recallValue(WcNum v) {
        if (state == ST_RESULT) {
            clearExpression();
        }
        cur = v;
        curText = null;
        if (state != ST_AFTER_OP) {
            state = ST_VALUE;
        } else {
            state = ST_VALUE;
        }
        entry = "0";
        expMode = 0;
    }

    void memAdd() {
        if (state == ST_ERROR) {
            return;
        }
        if (memN == 0) {
            memStore();
            return;
        }
        mem[0] = mem[0].add(cur);
        afterMemoryOp();
    }

    void memSub() {
        if (state == ST_ERROR) {
            return;
        }
        if (memN == 0) {
            WcNum keep = cur;
            cur = cur.negate();
            memStore();
            cur = keep;
            return;
        }
        mem[0] = mem[0].sub(cur);
        afterMemoryOp();
    }

    void memClear() {
        memN = 0;
    }

    void memAddAt(int i) {
        if (i >= 0 && i < memN && state != ST_ERROR) {
            mem[i] = mem[i].add(cur);
        }
    }

    void memSubAt(int i) {
        if (i >= 0 && i < memN && state != ST_ERROR) {
            mem[i] = mem[i].sub(cur);
        }
    }

    void memClearAt(int i) {
        if (i < 0 || i >= memN) {
            return;
        }
        int j = i;
        while (j + 1 < memN) {
            mem[j] = mem[j + 1];
            j = j + 1;
        }
        memN = memN - 1;
        mem[memN] = null;
    }

    void memRecallAt(int i) {
        if (i >= 0 && i < memN && state != ST_ERROR) {
            recallValue(mem[i]);
        }
    }

    /* ---------------------------------------------------------------- */
    /* History                                                           */
    /* ---------------------------------------------------------------- */

    void addHistory(String e, WcNum v) {
        if (histN >= HIST_MAX) {
            int j = 0;
            while (j + 1 < histN) {
                histExpr[j] = histExpr[j + 1];
                histText[j] = histText[j + 1];
                histVal[j] = histVal[j + 1];
                j = j + 1;
            }
            histN = histN - 1;
        }
        histExpr[histN] = e;
        histText[histN] = fmt(v);
        histVal[histN] = v;
        histN = histN + 1;
    }

    void clearHistory() {
        histN = 0;
    }

    void clearHistoryAt(int i) {
        if (i < 0 || i >= histN) {
            return;
        }
        int j = i;
        while (j + 1 < histN) {
            histExpr[j] = histExpr[j + 1];
            histText[j] = histText[j + 1];
            histVal[j] = histVal[j + 1];
            j = j + 1;
        }
        histN = histN - 1;
    }

    void recallHistory(int i) {
        if (i < 0 || i >= histN) {
            return;
        }
        clearExpression();
        lastOp = "";
        cur = histVal[i];
        curText = null;
        state = ST_VALUE;
        entry = "0";
        expMode = 0;
    }

    /* ---------------------------------------------------------------- */
    /* Settings                                                          */
    /* ---------------------------------------------------------------- */

    void cycleAngle() {
        angle = angle + 1;
        if (angle > WcMath.GRAD) {
            angle = WcMath.DEG;
        }
    }

    void toggleFE() {
        if (fe == 0) {
            fe = 1;
        } else {
            fe = 0;
        }
    }

    void setBase(int b) {
        if (mode != MODE_PROG) {
            return;
        }
        if (state == ST_ERROR) {
            return;
        }
        base = b;
        if (state == ST_TYPING) {
            state = ST_VALUE;
        }
        entry = "0";
    }

    void setWordSize(int w) {
        bits = w;
        if (mode == MODE_PROG && state != ST_ERROR) {
            cur = WcNum.fromLong(WcProg.wrap(cur.toLong(), bits));
            if (state == ST_TYPING) {
                state = ST_VALUE;
            }
        }
    }

    void toggleBit(int i) {
        if (mode != MODE_PROG || state == ST_ERROR || i < 0 || i >= bits) {
            return;
        }
        long v = cur.toLong();
        v = v ^ (1L << i);
        cur = WcNum.fromLong(WcProg.wrap(v, bits));
        curText = null;
        if (state == ST_TYPING || state == ST_RESULT) {
            if (state == ST_RESULT) {
                clearExpression();
            }
            state = ST_VALUE;
        }
        entry = "0";
    }

    int bitAt(int i) {
        if (mode != MODE_PROG || i < 0 || i >= bits) {
            return 0;
        }
        long v = cur.toLong();
        if (((v >> i) & 1L) != 0L) {
            return 1;
        }
        return 0;
    }

    /* ---------------------------------------------------------------- */
    /* Key dispatch                                                      */
    /* ---------------------------------------------------------------- */

    int isDigitKey(String key) {
        if (key.length() != 1) {
            return 0;
        }
        int c = key.charAt(0);
        if (c >= 48 && c <= 57) {
            return 1;
        }
        if (c >= 65 && c <= 70) {
            return 1;
        }
        return 0;
    }

    /** Whether a key is usable right now (drives button enabled state). */
    int keyEnabled(String key) {
        if (isDigitKey(key) != 0) {
            int dv = digitValue(key);
            if (mode == MODE_PROG) {
                if (dv < base) {
                    return 1;
                }
                return 0;
            }
            if (dv <= 9) {
                return 1;
            }
            return 0;
        }
        if (key.equals("CLEAR") || key.equals("CE") || key.equals("BS")) {
            return 1;
        }
        if (key.equals(".")) {
            if (mode == MODE_PROG) {
                return 0;
            }
            return 1;
        }
        if (key.equals("MC") || key.equals("MR")) {
            if (state == ST_ERROR || memN == 0) {
                return 0;
            }
            return 1;
        }
        if (state == ST_ERROR) {
            return 0;
        }
        if (key.equals("(") && canOpenParen() == 0) {
            return 0;
        }
        if (key.equals(")") && parenN == 0) {
            return 0;
        }
        return 1;
    }

    void press(String key) {
        if (key == null || key.length() == 0) {
            return;
        }
        if (isDigitKey(key) != 0) {
            inputDigit(key);
            return;
        }
        if (key.equals(".")) {
            inputPoint();
            return;
        }
        if (key.equals("=")) {
            equalsKey();
            return;
        }
        if (key.equals("CLEAR")) {
            resetAll();
            return;
        }
        if (key.equals("CE")) {
            clearEntry();
            return;
        }
        if (key.equals("BS")) {
            backspace();
            return;
        }
        if (key.equals("%")) {
            if (mode == MODE_PROG) {
                binaryOp("mod");
            } else {
                percent();
            }
            return;
        }
        if (key.equals("(")) {
            openParen();
            return;
        }
        if (key.equals(")")) {
            closeParen();
            return;
        }
        if (key.equals("exp")) {
            inputExp();
            return;
        }
        if (key.equals("pi")) {
            setConstant(WcMath.PI);
            return;
        }
        if (key.equals("e")) {
            setConstant(WcMath.E);
            return;
        }
        if (key.equals("rand")) {
            setConstant(WcMath.random());
            return;
        }
        if (key.equals("MC")) {
            memClear();
            return;
        }
        if (key.equals("MR")) {
            memRecall();
            return;
        }
        if (key.equals("M+")) {
            memAdd();
            return;
        }
        if (key.equals("M-")) {
            memSub();
            return;
        }
        if (key.equals("MS")) {
            memStore();
            return;
        }
        if (isBinaryKey(key) != 0) {
            binaryOp(key);
            return;
        }
        unary(key);
    }

    void clearEntry() {
        if (state == ST_ERROR || state == ST_RESULT) {
            resetAll();
            return;
        }
        entry = "0";
        expMode = 0;
        cur = WcNum.ZERO;
        curText = null;
        state = ST_TYPING;
    }
}

/** Programmer-mode integer helpers (signed two's complement per word size). */
class WcProg {
    static long mask(int bits) {
        if (bits >= 64) {
            return -1L;
        }
        return (1L << bits) - 1L;
    }

    static long wrap(long v, int bits) {
        if (bits >= 64) {
            return v;
        }
        long m = mask(bits);
        long u = v & m;
        long sb = 1L << (bits - 1);
        if ((u & sb) != 0L) {
            u = u | (~m);
        }
        return u;
    }

    static int shiftOf(int base) {
        if (base == 16) {
            return 4;
        }
        if (base == 8) {
            return 3;
        }
        return 1;
    }

    static int bitLen(int d) {
        int n = 0;
        int v = d;
        while (v > 0) {
            n = n + 1;
            v = v / 2;
        }
        return n;
    }

    /** Whether the digit string (no leading zeros) fits the word size. */
    static int entryFits(String s, int base, int bits) {
        if (s.length() == 0 || s.equals("0")) {
            return 1;
        }
        if (base == 10) {
            long limit = 9223372036854775807L;
            if (bits < 64) {
                limit = (1L << (bits - 1)) - 1L;
            }
            String ls = "" + limit;
            if (s.length() > ls.length()) {
                return 0;
            }
            if (s.length() < ls.length()) {
                return 1;
            }
            if (DecimalDigits.compare(s, ls) <= 0) {
                return 1;
            }
            return 0;
        }
        int d0 = s.charAt(0);
        int dv = 0;
        if (d0 >= 48 && d0 <= 57) {
            dv = d0 - 48;
        } else {
            dv = d0 - 55;
        }
        int sig = (s.length() - 1) * shiftOf(base) + bitLen(dv);
        if (sig <= bits) {
            return 1;
        }
        return 0;
    }

    static long parseEntry(String s, int base, int bits) {
        long r = 0L;
        int i = 0;
        long b = (long) base;
        while (i < s.length()) {
            int c = s.charAt(i);
            int dv = 0;
            if (c >= 48 && c <= 57) {
                dv = c - 48;
            } else {
                if (c >= 65 && c <= 70) {
                    dv = c - 55;
                }
            }
            r = r * b + (long) dv;
            i = i + 1;
        }
        return wrap(r, bits);
    }

    static long binary(String op, long a, long b, int bits) {
        if (op.equals("+")) {
            return wrap(a + b, bits);
        }
        if (op.equals("-")) {
            return wrap(a - b, bits);
        }
        if (op.equals("*")) {
            return wrap(a * b, bits);
        }
        if (op.equals("/") || op.equals("mod")) {
            if (b == 0L) {
                throw WcErr.make(WcErr.DIV_ZERO);
            }
            if (op.equals("/")) {
                return wrap(a / b, bits);
            }
            return wrap(a % b, bits);
        }
        if (op.equals("and")) {
            return wrap(a & b, bits);
        }
        if (op.equals("or")) {
            return wrap(a | b, bits);
        }
        if (op.equals("xor")) {
            return wrap(a ^ b, bits);
        }
        if (op.equals("nand")) {
            return wrap(~(a & b), bits);
        }
        if (op.equals("nor")) {
            return wrap(~(a | b), bits);
        }
        if (op.equals("lsh")) {
            if (b < 0L || b >= (long) bits) {
                return 0L;
            }
            return wrap(a << (int) b, bits);
        }
        if (op.equals("rsh")) {
            if (b < 0L || b >= (long) bits) {
                if (a < 0L) {
                    return -1L;
                }
                return 0L;
            }
            return wrap(a >> (int) b, bits);
        }
        if (op.equals("rshl")) {
            if (b < 0L || b >= (long) bits) {
                return 0L;
            }
            long u = a & mask(bits);
            return wrap(u >>> (int) b, bits);
        }
        if (op.equals("rol") || op.equals("ror")) {
            int k = (int) (b % (long) bits);
            if (k < 0) {
                k = k + bits;
            }
            if (k == 0) {
                return a;
            }
            long u = a & mask(bits);
            if (op.equals("ror")) {
                k = bits - k;
            }
            long r = (u << k) | (u >>> (bits - k));
            return wrap(r & mask(bits), bits);
        }
        throw WcErr.make(WcErr.INVALID);
    }
}
