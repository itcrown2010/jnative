// JNative Web 加法器 — 业务逻辑在 WASM 中执行；HTML 仅提供 DOM 宿主。
// Author: 张晓达

/**
 * Shared compute for desktop / web / mobile templates.
 * getLastError: 0=OK, 1=empty, 2=invalid
 */
public class App {
    public static int lastError;

    public static int add(int a, int b) {
        return a + b;
    }

    public static int getLastError() {
        return lastError;
    }

    public static int parseIntStrict(String s) {
        if (s == null) {
            lastError = 1;
            return 0;
        }
        int n = s.length();
        if (n == 0) {
            lastError = 1;
            return 0;
        }
        int i = 0;
        int sign = 1;
        int c0 = s.charAt(0);
        if (c0 == 45) {
            if (n == 1) {
                lastError = 2;
                return 0;
            }
            sign = 0 - 1;
            i = 1;
        } else if (c0 == 43) {
            if (n == 1) {
                lastError = 2;
                return 0;
            }
            i = 1;
        }
        int acc = 0;
        int digits = 0;
        while (i < n) {
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

    public static int compute(String left, String right) {
        int a = parseIntStrict(left);
        if (lastError != 0) {
            return 0;
        }
        int b = parseIntStrict(right);
        if (lastError != 0) {
            return 0;
        }
        lastError = 0;
        return add(a, b);
    }

    public static void main(String[] args) {
        Document doc = Browser.document();
        Element btn = doc.getElementById("sum");
        if (btn != null) {
            btn.addEventListener("click", e -> {
                onSum();
            });
        }
        Element status = doc.getElementById("status");
        if (status != null) {
            status.setText("就绪 — 在 WASM 中计算");
        }
    }

    static void onSum() {
        Document doc = Browser.document();
        Element leftEl = doc.getElementById("left");
        Element rightEl = doc.getElementById("right");
        Element resultEl = doc.getElementById("result");
        Element status = doc.getElementById("status");
        String left = "";
        String right = "";
        if (leftEl != null) {
            left = leftEl.asInput().value();
        }
        if (rightEl != null) {
            right = rightEl.asInput().value();
        }
        int sum = compute(left, right);
        int err = getLastError();
        if (err == 1) {
            if (status != null) {
                status.setText("错误：输入不能为空");
            }
            return;
        }
        if (err == 2) {
            if (status != null) {
                status.setText("错误：请输入合法整数");
            }
            return;
        }
        if (resultEl != null) {
            resultEl.setText(String.valueOf(sum));
        }
        if (status != null) {
            status.setText("计算完成（JNative WASM）");
        }
    }
}
