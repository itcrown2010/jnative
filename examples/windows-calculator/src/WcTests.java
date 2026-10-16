/**
 * Headless engine tests for the JNative Windows Calculator.
 *
 * java -jar target/jnative.jar examples/windows-calculator/src/Wc{Num,Fmt,Math,Engine}.java \
 *   examples/windows-calculator/src/WcTests.java -o build/windows-calculator/WcTests
 *
 * @author 张晓达
 */
public class WcTests {
    static int pass = 0;
    static int fail = 0;

    static void check(String name, String got, String want) {
        if (got.equals(want)) {
            pass = pass + 1;
        } else {
            fail = fail + 1;
            System.out.println("FAIL " + name + ": got [" + got + "] want [" + want + "]");
        }
    }

    static void checkInt(String name, int got, int want) {
        check(name, "" + got, "" + want);
    }

    static int isNumToken(String t) {
        int i = 0;
        while (i < t.length()) {
            int c = t.charAt(i);
            if (!((c >= 48 && c <= 57) || c == 46)) {
                return 0;
            }
            i = i + 1;
        }
        return 1;
    }

    /** Press keys: space-separated; all-digit tokens are typed digit by digit. */
    static void seq(WcEngine e, String s) {
        String[] parts = s.split(" ");
        int i = 0;
        while (i < parts.length) {
            String t = parts[i];
            if (t.length() > 0) {
                if (isNumToken(t) != 0) {
                    int j = 0;
                    while (j < t.length()) {
                        e.press(t.substring(j, j + 1));
                        j = j + 1;
                    }
                } else {
                    e.press(t);
                }
            }
            i = i + 1;
        }
    }

    static WcEngine std() {
        return new WcEngine();
    }

    static WcEngine sci() {
        WcEngine e = new WcEngine();
        e.setMode(WcEngine.MODE_SCI);
        e.resetAll();
        return e;
    }

    static WcEngine prog() {
        WcEngine e = new WcEngine();
        e.setMode(WcEngine.MODE_PROG);
        e.resetAll();
        return e;
    }

    static void tStandard() {
        WcEngine e = std();
        check("std init", e.displayText(), "0");
        seq(e, "1 2 + 3 =");
        check("std add", e.displayText(), "15");
        check("std add expr", e.expressionText(), "12 + 3 =");

        e = std();
        seq(e, "5 + 3 +");
        check("std chain disp", e.displayText(), "8");
        check("std chain expr", e.expressionText(), "5 + 3 + ");

        e = std();
        seq(e, "5 + 3 = =");
        check("std repeat =", e.displayText(), "11");
        check("std repeat expr", e.expressionText(), "8 + 3 =");

        e = std();
        seq(e, "9 sqrt");
        check("std sqrt", e.displayText(), "3");
        check("std sqrt expr", e.expressionText(), "√(9)");

        e = std();
        seq(e, "0.1 + 0.2 =");
        check("std 0.1+0.2", e.displayText(), "0.3");

        e = std();
        seq(e, "1 / 3 =");
        check("std 1/3", e.displayText(), "0.3333333333333333");
        seq(e, "* 3 =");
        check("std 1/3*3", e.displayText(), "1");

        e = std();
        seq(e, "1 / 0 =");
        check("std div0", e.displayText(), "Cannot divide by zero");
        checkInt("std err ops disabled", e.keyEnabled("+"), 0);
        checkInt("std err digit enabled", e.keyEnabled("5"), 1);
        seq(e, "5");
        check("std err cleared by digit", e.displayText(), "5");

        e = std();
        seq(e, "0 / 0 =");
        check("std 0/0", e.displayText(), "Result is undefined");

        e = std();
        seq(e, "4 neg sqrt");
        check("std sqrt neg", e.displayText(), "Invalid input");

        e = std();
        seq(e, "0 recip");
        check("std 1/0", e.displayText(), "Cannot divide by zero");

        e = std();
        seq(e, "200 + 10 % =");
        check("std percent add", e.displayText(), "220");
        e = std();
        seq(e, "200 * 10 % =");
        check("std percent mul", e.displayText(), "20");
        e = std();
        seq(e, "50 %");
        check("std percent alone", e.displayText(), "0");
        e = std();
        seq(e, "200 - 25 % =");
        check("std percent sub", e.displayText(), "150");

        e = std();
        seq(e, "5 neg");
        check("std neg typed", e.displayText(), "-5");
        seq(e, "6");
        check("std neg typed continue", e.displayText(), "-56");
        e = std();
        seq(e, "5 + 3 = neg");
        check("std neg result", e.displayText(), "-8");
        check("std neg result expr", e.expressionText(), "negate(8)");
        e = std();
        seq(e, "0 neg");
        check("std neg zero", e.displayText(), "0");

        e = std();
        seq(e, "1 2 3 BS");
        check("std bs", e.displayText(), "12");
        seq(e, "BS BS BS");
        check("std bs to zero", e.displayText(), "0");

        e = std();
        seq(e, "5 + 3 CE 4 =");
        check("std CE keeps expr", e.displayText(), "9");
        e = std();
        seq(e, "5 + 3 CLEAR");
        check("std C display", e.displayText(), "0");
        check("std C expr", e.expressionText(), "");

        e = std();
        seq(e, "9999999999999999 + 1 =");
        check("std sci overflow display", e.displayText(), "1e+16");

        e = std();
        seq(e, "1234567");
        check("std grouping", e.displayText(), "1,234,567");
        seq(e, "8 9 0 1 2 3 4 5 6 7 8");
        check("std 16 digit cap", e.displayText(), "1,234,567,890,123,456");

        e = std();
        seq(e, ".");
        check("std point only", e.displayText(), "0.");
        seq(e, "5");
        check("std point 5", e.displayText(), "0.5");
        seq(e, ". 0");
        check("std second point ignored", e.displayText(), "0.50");

        e = std();
        seq(e, "5 MS 3 M+ MR");
        check("mem add", e.displayText(), "8");
        checkInt("mem count", e.memN, 1);
        seq(e, "2 M- MR");
        check("mem sub", e.displayText(), "6");
        seq(e, "MC");
        checkInt("mem cleared", e.hasMemory(), 0);
        checkInt("MR disabled when empty", e.keyEnabled("MR"), 0);
        seq(e, "7 MS 9 MS");
        checkInt("mem stack", e.memN, 2);
        check("mem top", e.fmt(e.mem[0]), "9");
        check("mem second", e.fmt(e.mem[1]), "7");
        e.memAddAt(1);
        check("mem item add", e.fmt(e.mem[1]), "16");
        e.memClearAt(0);
        check("mem item clear", e.fmt(e.mem[0]), "16");

        e = std();
        seq(e, "4 recip");
        check("std recip", e.displayText(), "0.25");
        check("std recip expr", e.expressionText(), "1/(4)");
        e = std();
        seq(e, "3 sqr sqr");
        check("std sqr nest", e.displayText(), "81");
        check("std sqr nest expr", e.expressionText(), "sqr(sqr(3))");
        e = std();
        seq(e, "5 + 3 sqr");
        check("std unary in expr", e.expressionText(), "5 + sqr(3)");
        seq(e, "=");
        check("std unary result", e.displayText(), "14");

        e = std();
        seq(e, "2 * 3 + 4 =");
        check("std immediate exec", e.displayText(), "10");
        e = std();
        seq(e, "2 + 3 * 4 =");
        check("std immediate exec2", e.displayText(), "20");
        e = std();
        seq(e, "5 + * 3 =");
        check("std op replace", e.displayText(), "15");
        e = std();
        seq(e, "5 + =");
        check("std 5 + =", e.displayText(), "10");
        e = std();
        seq(e, "1 2 =");
        seq(e, "+ 1 =");
        check("std continue from result", e.displayText(), "13");
        e = std();
        seq(e, "3 = 4");
        check("std new entry after =", e.displayText(), "4");
        e = std();
        seq(e, "1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 * 1 0 =");
        check("std big mul", e.displayText(), "1.234567890123456e+16");

        e = std();
        seq(e, "5 + 3 =");
        checkInt("history count", e.histN, 1);
        check("history expr", e.histExpr[0], "5 + 3 =");
        check("history result", e.histText[0], "8");
        seq(e, "CLEAR 9 * 9 =");
        checkInt("history count 2", e.histN, 2);
        e.recallHistory(0);
        check("history recall", e.displayText(), "8");
        e.clearHistory();
        checkInt("history clear", e.histN, 0);

        e = std();
        seq(e, "0 . 0 0 0 0 0 0 1 =");
        check("std small", e.displayText(), "1e-7");
        e = std();
        seq(e, "1 / 1 0 0 0 0 0 0 0 =");
        check("std tiny sci", e.displayText(), "1e-7");
    }

    static void tScientific() {
        WcEngine e = sci();
        seq(e, "30 sin");
        check("sci sin30", e.displayText(), "0.5");
        check("sci sin expr", e.expressionText(), "sin(30)");
        e = sci();
        seq(e, "90 cos");
        check("sci cos90", e.displayText(), "0");
        e = sci();
        seq(e, "180 sin");
        check("sci sin180", e.displayText(), "0");
        e = sci();
        seq(e, "45 tan");
        check("sci tan45", e.displayText(), "1");
        e = sci();
        seq(e, "90 tan");
        check("sci tan90", e.displayText(), "Cannot divide by zero");
        e = sci();
        seq(e, "0.5 asin");
        check("sci asin", e.displayText(), "30");
        e = sci();
        seq(e, "2 asin");
        check("sci asin domain", e.displayText(), "Invalid input");
        e = sci();
        e.cycleAngle();
        seq(e, "pi cos");
        check("sci cos pi rad", e.displayText(), "-1");
        e = sci();
        e.cycleAngle();
        e.cycleAngle();
        seq(e, "100 sin");
        check("sci grad", e.displayText(), "1");
        e = sci();
        seq(e, "30 sinh");
        check("sci sinh", e.displayText(), "5,343,237,290,762.2310734952342785826");

        e = sci();
        seq(e, "( 2 + 3 ) * 4 =");
        check("sci paren", e.displayText(), "20");
        check("sci paren expr", e.expressionText(), "(2 + 3) × 4 =");
        e = sci();
        seq(e, "2 + 3 * 4 =");
        check("sci precedence", e.displayText(), "14");
        e = sci();
        seq(e, "2 pow 3 pow 2 =");
        check("sci pow left assoc", e.displayText(), "64");
        e = sci();
        seq(e, "2 pow 1 0 =");
        check("sci 2^10", e.displayText(), "1,024");
        e = sci();
        seq(e, "2 pow 0.5 =");
        check("sci 2^0.5", e.displayText(), "1.4142135623730950488016887242097");
        e = sci();
        seq(e, "2 pow 1 0 0 =");
        check("sci 2^100", e.displayText(), "1,267,650,600,228,229,401,496,703,205,376");
        e = sci();
        seq(e, "8 cbrt");
        check("sci cbrt", e.displayText(), "2");
        e = sci();
        seq(e, "2 7 yroot 3 =");
        check("sci yroot", e.displayText(), "3");
        e = sci();
        seq(e, "5 fact");
        check("sci fact", e.displayText(), "120");
        e = sci();
        seq(e, "2 0 fact");
        check("sci 20!", e.displayText(), "2,432,902,008,176,640,000");
        e = sci();
        seq(e, "0.5 fact");
        check("sci gamma", e.displayText().substring(0, 16), "0.88622692545275");
        e = sci();
        seq(e, "1 neg fact");
        check("sci fact neg", e.displayText(), "Invalid input");
        e = sci();
        seq(e, "1 0 0 log");
        check("sci log100", e.displayText(), "2");
        e = sci();
        seq(e, "e ln");
        check("sci ln e", e.displayText(), "1");
        e = sci();
        seq(e, "0 ln");
        check("sci ln0", e.displayText(), "Invalid input");
        e = sci();
        seq(e, "1 0 pow10");
        check("sci 10^10", e.displayText(), "10,000,000,000");
        e = sci();
        seq(e, "1 pow2");
        check("sci 2^1", e.displayText(), "2");
        e = sci();
        seq(e, "1 epow");
        check("sci e^1", e.displayText(), "2.7182818284590452353602874713527");
        e = sci();
        seq(e, "2 sqrt");
        check("sci sqrt2 32 digits", e.displayText(), "1.4142135623730950488016887242097");
        e = sci();
        seq(e, "pi");
        check("sci pi", e.displayText(), "3.1415926535897932384626433832795");
        e = sci();
        seq(e, "3 exp 2 =");
        check("sci exp entry", e.displayText(), "300");
        e = sci();
        seq(e, "3 exp");
        check("sci exp typing", e.displayText(), "3.e+0");
        seq(e, "5");
        check("sci exp typing digit", e.displayText(), "3.e+5");
        seq(e, "neg");
        check("sci exp neg", e.displayText(), "3.e-5");
        seq(e, "=");
        check("sci exp neg result", e.displayText(), "0.00003");
        e = sci();
        seq(e, "1 . 5 neg floor");
        check("sci floor", e.displayText(), "-2");
        e = sci();
        seq(e, "1 . 5 neg ceil");
        check("sci ceil", e.displayText(), "-1");
        e = sci();
        seq(e, "1 0 mod 3 =");
        check("sci mod", e.displayText(), "1");
        e = sci();
        seq(e, "1 0 neg mod 3 =");
        check("sci mod neg", e.displayText(), "-1");
        e = sci();
        seq(e, "5 abs");
        check("sci abs", e.displayText(), "5");
        e = sci();
        seq(e, "5 neg abs");
        check("sci abs neg", e.displayText(), "5");
        e = sci();
        seq(e, "5");
        e.toggleFE();
        seq(e, "=");
        check("sci FE", e.displayText(), "5e+0");
        e = sci();
        seq(e, "1 0 . 5 dms");
        check("sci dms", e.displayText(), "10.3");
        seq(e, "deg");
        check("sci deg", e.displayText(), "10.5");
        e = sci();
        seq(e, "3 pow 3 0 0 0 0 =");
        check("sci overflow", e.displayText(), "Overflow");
        e = sci();
        seq(e, "0 pow 0 =");
        check("sci 0^0", e.displayText(), "Result is undefined");
        e = sci();
        e = sci();
        seq(e, "1 0 0 0 0 pow10");
        check("sci 10^10000 overflow", e.displayText(), "Overflow");
        e = sci();
        seq(e, "( 1 + 2 ) sqr");
        check("sci paren unary expr", e.expressionText(), "sqr((1 + 2))");
        check("sci paren unary", e.displayText(), "9");
        e = sci();
        seq(e, "2 * ( 3 + 4 ) + 1 =");
        check("sci nested", e.displayText(), "15");
        e = sci();
        seq(e, "( ( 1 + 2 ) * ( 3 + 4 ) ) =");
        check("sci double paren", e.displayText(), "21");
        e = sci();
        seq(e, "2 logy 8 =");
        check("sci logy", e.displayText(), "0.33333333333333333333333333333333");
    }

    static void tProgrammer() {
        WcEngine e = prog();
        seq(e, "2 5 5");
        check("prog dec", e.displayText(), "255");
        check("prog hex row", e.radixText(16), "FF");
        check("prog bin row", e.radixText(2), "1111 1111");
        check("prog oct row", e.radixText(8), "377");

        e = prog();
        e.setBase(16);
        seq(e, "F F");
        check("prog hex entry", e.displayText(), "FF");
        check("prog hex entry dec", e.radixText(10), "255");
        checkInt("prog hex A enabled", e.keyEnabled("A"), 1);
        e.setBase(10);
        checkInt("prog dec A disabled", e.keyEnabled("A"), 0);
        e.setBase(2);
        checkInt("prog bin 2 disabled", e.keyEnabled("2"), 0);

        e = prog();
        seq(e, "5 and 3 =");
        check("prog and", e.displayText(), "1");
        e = prog();
        seq(e, "5 or 3 =");
        check("prog or", e.displayText(), "7");
        e = prog();
        seq(e, "5 xor 3 =");
        check("prog xor", e.displayText(), "6");
        e = prog();
        seq(e, "5 not");
        check("prog not", e.displayText(), "-6");
        e = prog();
        seq(e, "5 nand 3 =");
        check("prog nand", e.displayText(), "-2");
        e = prog();
        seq(e, "1 lsh 4 =");
        check("prog lsh", e.displayText(), "16");
        e = prog();
        seq(e, "1 6 rsh 2 =");
        check("prog rsh", e.displayText(), "4");
        e = prog();
        seq(e, "1 0 / 3 =");
        check("prog div", e.displayText(), "3");
        e = prog();
        seq(e, "1 0 % 3 =");
        check("prog mod", e.displayText(), "1");
        e = prog();
        seq(e, "1 / 0 =");
        check("prog div0", e.displayText(), "Cannot divide by zero");
        e = prog();
        seq(e, "1 neg");
        check("prog neg", e.displayText(), "-1");
        check("prog neg hex", e.radixText(16), "FFFF FFFF FFFF FFFF");

        e = prog();
        e.setWordSize(8);
        seq(e, "1 2 7 + 1 =");
        check("prog byte wrap", e.displayText(), "-128");
        check("prog byte hex", e.radixText(16), "80");
        e = prog();
        e.setWordSize(8);
        seq(e, "2 0 0");
        check("prog byte entry cap", e.displayText(), "20");
        e = prog();
        e.setWordSize(8);
        e.setBase(16);
        seq(e, "8 1 rol 1 =");
        check("prog rol", e.displayText(), "3");
        e = prog();
        e.setWordSize(8);
        e.setBase(16);
        seq(e, "3 ror 1 =");
        check("prog ror", e.displayText(), "81");
        e = prog();
        e.setWordSize(16);
        seq(e, "1 neg rshl 4 =");
        check("prog rshl", e.radixText(16), "FFF");
        e = prog();
        seq(e, "( 1 + 2 ) * 3 =");
        check("prog paren", e.displayText(), "9");
        e = prog();
        seq(e, "1 + 2 and 3 =");
        check("prog precedence", e.displayText(), "3");
        e = prog();
        e.toggleBit(0);
        e.toggleBit(3);
        check("prog bit toggle", e.displayText(), "9");
        checkInt("prog bit at", e.bitAt(3), 1);
        checkInt("prog bit at 2", e.bitAt(2), 0);
        e.setWordSize(8);
        e.toggleBit(7);
        check("prog bit sign", e.displayText(), "-119");
        e = prog();
        e.setWordSize(32);
        seq(e, "1 neg");
        e.setWordSize(64);
        check("prog widen", e.displayText(), "-1");
        e.setWordSize(8);
        seq(e, "CLEAR 3 0 0");
        check("prog byte typed 300", e.displayText(), "30");
        e = prog();
        seq(e, "1 0");
        e.setMode(WcEngine.MODE_STD);
        check("mode switch keeps value", e.displayText(), "10");
        e = std();
        seq(e, "3 . 7");
        e.setMode(WcEngine.MODE_PROG);
        check("mode to prog truncates", e.displayText(), "3");
    }

    public static void main(String[] args) {
        String which = "all";
        if (args.length > 0) {
            which = args[0];
        }
        if (which.equals("all") || which.equals("std")) {
            tStandard();
        }
        if (which.equals("all") || which.equals("sci")) {
            tScientific();
        }
        if (which.equals("all") || which.equals("prog")) {
            tProgrammer();
        }
        System.out.println("pass=" + pass + " fail=" + fail);
        if (fail != 0) {
            System.out.println("WC_TESTS_FAIL");
        } else {
            System.out.println("WC_TESTS_PASS");
        }
    }
}
