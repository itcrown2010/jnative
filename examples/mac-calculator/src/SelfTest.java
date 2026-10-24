import jnative.ui.*;

/**
 * 不用鼠标的自测：用 UiTest.click 点击真实的按钮（经过设计器生成的事件绑定），检查显示结果。
 * 运行：./.jnative/build/MacCalculator --selftest
 */
public class SelfTest {
    static int failed = 0;

    static void check(String name, String expected, MainForm f) {
        String got = f.lblDisplay.getText();
        if (!got.equals(expected)) {
            System.out.println("FAIL " + name + ": expected " + expected + " got " + got);
            failed = failed + 1;
        } else {
            System.out.println("ok   " + name + " = " + got);
        }
    }

    static Button digitButton(MainForm f, int d) {
        if (d == 0) {
            return f.btn0;
        } else if (d == 1) {
            return f.btn1;
        } else if (d == 2) {
            return f.btn2;
        } else if (d == 3) {
            return f.btn3;
        } else if (d == 4) {
            return f.btn4;
        } else if (d == 5) {
            return f.btn5;
        } else if (d == 6) {
            return f.btn6;
        } else if (d == 7) {
            return f.btn7;
        } else if (d == 8) {
            return f.btn8;
        }
        return f.btn9;
    }

    /** 按键序列：通过 UiTest.click 点击真实的按钮，因此事件绑定也一并被测试。 */
    static void keys(MainForm f, String seq) {
        int i = 0;
        while (i < seq.length()) {
            char c = seq.charAt(i);
            if (c >= '0' && c <= '9') {
                UiTest.click(digitButton(f, c - '0'));
            } else if (c == '.') {
                UiTest.click(f.btnDecimal);
            } else if (c == '+') {
                UiTest.click(f.btnAdd);
            } else if (c == '-') {
                UiTest.click(f.btnSubtract);
            } else if (c == '*') {
                UiTest.click(f.btnMultiply);
            } else if (c == '/') {
                UiTest.click(f.btnDivide);
            } else if (c == '=') {
                UiTest.click(f.btnEquals);
            } else if (c == 'C') {
                UiTest.click(f.btnClear);
            } else if (c == 'S') {
                UiTest.click(f.btnSign);
            } else if (c == '%') {
                UiTest.click(f.btnPercent);
            } else if (c == '<') {
                f.backspace();
            }
            i = i + 1;
        }
    }

    static int run() {
        MainForm f = new MainForm();
        check("初始", "0", f);
        keys(f, "12+30=");
        check("12+30", "42", f);
        keys(f, "C0.1+0.2=");
        check("0.1+0.2 (十进制精确)", "0.3", f);
        keys(f, "C7*8-6=");
        check("7*8-6 (从左到右)", "50", f);
        keys(f, "C1/3=");
        check("1/3 (最多 10 位小数)", "0.3333333333", f);
        keys(f, "C5/0=");
        check("除以零", "无法除以零", f);
        keys(f, "C9S");
        check("±", "-9", f);
        keys(f, "C50%");
        check("50%", "0.5", f);
        keys(f, "C123456789=");
        check("千位分隔", "123,456,789", f);
        keys(f, "C1234<<");
        check("退格", "12", f);
        keys(f, "C2+3==");
        check("连续等号 2+3==", "8", f);
        keys(f, "C2*3+4=");
        check("连续运算 2*3+4", "10", f);
        keys(f, "C9999999999*9999999999=");
        check("超过 15 位提示太大", "数值太大", f);
        keys(f, "C.5+.5=");
        check(".5+.5", "1", f);
        keys(f, "C100-250=");
        check("负结果", "-150", f);
        return failed;
    }

    static int snapStep = 0;
    static UiTimer snapTimer;

    /** 显示窗口，输入 `seq` 后把窗口截图到 path 并退出（用于无人值守检查界面）。 */
    static void snapshot(String seq, String path) {
        MainForm f = new MainForm();
        f.show();
        snapTimer = new UiTimer(800, () -> {
            snapStep = snapStep + 1;
            if (snapStep == 1) {
                keys(f, seq);
            } else if (snapStep == 3) {
                System.out.println("win " + f.window.width + "x" + f.window.height + " root " + f.root.width + " head " + f.head.width + " pad " + f.pad.width + " btn " + f.btn7.width);
                f.window.snapshot(path);
                System.out.println("snapshot " + path);
                System.exit(0);
            }
        });
    }
}
