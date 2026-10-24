import jnative.ui.*;
import jnative.graphics.*;

/**
 * macOS 风格计算器的业务逻辑。界面由 MainForm.jnui 在 JNative Studio 设计器中设计，
 * 并生成到 MainFormBase；这里只重写按钮事件并实现计算。
 *
 * 数值使用标准库 Decimal（十进制定点运算），因此 0.1 + 0.2 恰好等于 0.3。
 */
public class MainForm extends MainFormBase {
    static int OP_NONE = 0;
    static int OP_ADD = 1;
    static int OP_SUB = 2;
    static int OP_MUL = 3;
    static int OP_DIV = 4;
    static int MAX_DIGITS = 12;

    /** 当前输入（或结果）的纯文本，如 "-12.5"。 */
    String entry = "0";
    Decimal acc = Decimal.ZERO;
    int pending = OP_NONE;
    /** true：下一次按数字键开始新的输入（刚按过运算符或等号）。 */
    boolean fresh = true;
    boolean error = false;
    /** 最近一次 “=” 的运算，用于连续按 “=” 重复计算。 */
    int lastOp = OP_NONE;
    Decimal lastOperand = Decimal.ZERO;

    MainForm() {
        buildUi();
        window.onKeyDown(e -> onKey(e));
        refresh();
    }

    // ------------------------------------------------------------ 设计器绑定的事件

    @Override
    void onDigit0() {
        digit(0);
    }

    @Override
    void onDigit1() {
        digit(1);
    }

    @Override
    void onDigit2() {
        digit(2);
    }

    @Override
    void onDigit3() {
        digit(3);
    }

    @Override
    void onDigit4() {
        digit(4);
    }

    @Override
    void onDigit5() {
        digit(5);
    }

    @Override
    void onDigit6() {
        digit(6);
    }

    @Override
    void onDigit7() {
        digit(7);
    
    }

    @Override
    void onDigit8() {
        digit(8);
    }

    @Override
    void onDigit9() {
        digit(9);
    }

    @Override
    void onDecimal() {
        decimalPoint();
    }

    @Override
    void onAdd() {
        operator(OP_ADD);
    }

    @Override
    void onSubtract() {
        operator(OP_SUB);
    }

    @Override
    void onMultiply() {
        operator(OP_MUL);
    }

    @Override
    void onDivide() {
        operator(OP_DIV);
    }

    @Override
    void onEquals() {
        equalsKey();
    }

    @Override
    void onClear() {
        clearAll();
    }

    @Override
    void onSign() {
        toggleSign();
    }

    @Override
    void onPercent() {
        percent();
    }

    // ------------------------------------------------------------ 键盘

    void onKey(KeyEvent e) {
        if (e.metaDown != 0 || e.ctrlDown != 0) {
            return;
        }
        int ch = e.typedChar;
        if (ch >= '0' && ch <= '9') {
            digit(ch - '0');
        } else if (ch == '.') {
            decimalPoint();
        } else if (ch == '+') {
            operator(OP_ADD);
        } else if (ch == '-') {
            operator(OP_SUB);
        } else if (ch == '*' || ch == 'x') {
            operator(OP_MUL);
        } else if (ch == '/') {
            operator(OP_DIV);
        } else if (ch == '=') {
            equalsKey();
        } else if (ch == '%') {
            percent();
        } else if (e.keyCode == KeyCode.ENTER) {
            equalsKey();
        } else if (e.keyCode == KeyCode.ESCAPE) {
            clearAll();
        } else if (e.keyCode == KeyCode.BACKSPACE) {
            backspace();
        }
    }

    // ------------------------------------------------------------ 计算逻辑

    void digit(int d) {
        if (error) {
            clearAll();
        }
        if (fresh) {
            entry = "" + d;
            fresh = false;
        } else if (entry.equals("0")) {
            entry = "" + d;
        } else if (entry.equals("-0")) {
            entry = "-" + d;
        } else if (digitCount(entry) < MAX_DIGITS) {
            entry = entry + d;
        }
        refresh();
    }

    void decimalPoint() {
        if (error) {
            clearAll();
        }
        if (fresh) {
            entry = "0.";
            fresh = false;
        } else if (entry.indexOf(".") < 0) {
            entry = entry + ".";
        }
        refresh();
    }

    void backspace() {
        if (error || fresh) {
            return;
        }
        if (entry.length() <= 1 || (entry.length() == 2 && entry.startsWith("-"))) {
            entry = "0";
        } else {
            entry = entry.substring(0, entry.length() - 1);
        }
        refresh();
    }

    void toggleSign() {
        if (error) {
            return;
        }
        if (entry.startsWith("-")) {
            entry = entry.substring(1);
        } else if (!entry.equals("0")) {
            entry = "-" + entry;
        }
        refresh();
    }

    void percent() {
        if (error) {
            return;
        }
        Decimal v = value().percent();
        entry = plain(v);
        fresh = true;
        refresh();
    }

    void operator(int op) {
        if (error) {
            return;
        }
        if (pending != OP_NONE && !fresh) {
            if (!compute()) {
                return;
            }
        } else {
            acc = value();
        }
        pending = op;
        fresh = true;
        lastOp = OP_NONE;
        refresh();
    }

    void equalsKey() {
        if (error) {
            return;
        }
        if (pending != OP_NONE) {
            Decimal operand = fresh ? acc : value();
            lastOp = pending;
            lastOperand = operand;
            if (compute()) {
                pending = OP_NONE;
                fresh = true;
            }
        } else if (lastOp != OP_NONE) {
            // 连续按 “=”：重复上一次的运算
            acc = value();
            pending = lastOp;
            Decimal keep = lastOperand;
            if (apply(keep)) {
                pending = OP_NONE;
                fresh = true;
            }
        }
        refresh();
    }

    /** 计算 acc (pending) 当前输入，结果写入 acc 与 entry。 */
    boolean compute() {
        return apply(value());
    }

    boolean apply(Decimal operand) {
        Decimal r = acc;
        if (pending == OP_ADD) {
            r = acc.add(operand);
        } else if (pending == OP_SUB) {
            r = acc.sub(operand);
        } else if (pending == OP_MUL) {
            r = acc.mul(operand);
        } else if (pending == OP_DIV) {
            if (operand.isZero() != 0) {
                fail("无法除以零");
                return false;
            }
            r = acc.divScale(operand, 12);
        }
        acc = r;
        entry = plain(r);
        return true;
    }

    void fail(String message) {
        error = true;
        entry = message;
        pending = OP_NONE;
        fresh = true;
        lastOp = OP_NONE;
    }

    void clearAll() {
        entry = "0";
        acc = Decimal.ZERO;
        pending = OP_NONE;
        lastOp = OP_NONE;
        fresh = true;
        error = false;
        refresh();
    }

    Decimal value() {
        String t = entry;
        if (t.endsWith(".")) {
            t = t + "0";
        }
        return Decimal.fromString(t);
    }

    /** 结果转成输入框文本：最多 10 位小数，去掉多余的 0。 */
    String plain(Decimal v) {
        String s = v.toDisplayString(10);
        if (s.indexOf(".") >= 0) {
            while (s.endsWith("0")) {
                s = s.substring(0, s.length() - 1);
            }
            if (s.endsWith(".")) {
                s = s.substring(0, s.length() - 1);
            }
        }
        if (s.equals("-0") || s.length() == 0) {
            s = "0";
        }
        if (digitCount(s) > 15) {
            fail("数值太大");
            return entry;
        }
        return s;
    }

    int digitCount(String s) {
        int n = 0;
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                n = n + 1;
            }
            i = i + 1;
        }
        return n;
    }

    // ------------------------------------------------------------ 显示

    String opSymbol(int op) {
        if (op == OP_ADD) {
            return "+";
        }
        if (op == OP_SUB) {
            return "−";
        }
        if (op == OP_MUL) {
            return "×";
        }
        if (op == OP_DIV) {
            return "÷";
        }
        return "";
    }

    /** 整数部分按千位加逗号：-1234567.5 → -1,234,567.5 。 */
    String grouped(String s) {
        if (error) {
            return s;
        }
        String sign = "";
        if (s.startsWith("-")) {
            sign = "-";
            s = s.substring(1);
        }
        String frac = "";
        int dot = s.indexOf(".");
        if (dot >= 0) {
            frac = s.substring(dot);
            s = s.substring(0, dot);
        }
        String out = "";
        int count = 0;
        int i = s.length() - 1;
        while (i >= 0) {
            out = s.charAt(i) + out;
            count = count + 1;
            if (count % 3 == 0 && i > 0) {
                out = "," + out;
            }
            i = i - 1;
        }
        return sign + out + frac;
    }

    void refresh() {
        String shown = grouped(entry);
        lblDisplay.setText(shown);
        int n = shown.length();
        int size = 52;
        if (n > 14) {
            size = 28;
        } else if (n > 11) {
            size = 34;
        } else if (n > 8) {
            size = 42;
        }
        lblDisplay.setFontSize(size);
        if (pending != OP_NONE) {
            lblHistory.setText(grouped(plain(acc)) + " " + opSymbol(pending));
        } else {
            lblHistory.setText("");
        }
        highlight(btnAdd, OP_ADD);
        highlight(btnSubtract, OP_SUB);
        highlight(btnMultiply, OP_MUL);
        highlight(btnDivide, OP_DIV);
    }

    /** 与 macOS 计算器一致：等待第二个操作数时，当前运算符键反白显示。 */
    void highlight(Button b, int op) {
        boolean active = pending == op && fresh;
        if (active) {
            b.setBackgroundRgba(Component.rgba(255, 255, 255, 255));
            b.setForegroundRgba(Component.rgba(255, 159, 10, 255));
        } else {
            b.setBackgroundRgba(Component.rgba(255, 159, 10, 255));
            b.setForegroundRgba(Component.rgba(255, 255, 255, 255));
        }
    }
}
