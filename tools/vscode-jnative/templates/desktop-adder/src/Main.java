// JNative 桌面加法器 — 业务逻辑在原生程序中执行。
// Author: 张晓达

/**
 * Shared compute for desktop / web / mobile templates.
 * getLastError: 0=OK, 1=empty, 2=invalid
 */
public class Main {
    public static int lastError;
    static Label resultLabel;
    static Label statusLabel;
    static TextField leftField;
    static TextField rightField;

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
        Theme.setAppearanceMode(Theme.MODE_LIGHT);
        Application app = new Application();
        Window w = new Window("{{APP_NAME}}", 420, 360);

        Label title = new Label("桌面加法器");
        title.setDisplayStyle(20, 0, -14935529);
        title.setPrefSize(380, 28);

        Label hint = new Label("业务计算在 JNative 原生代码中执行");
        hint.setDisplayStyle(12, 0, -10725812);
        hint.setPrefSize(380, 22);

        leftField = new TextField("12");
        leftField.setPrefSize(360, 28);
        rightField = new TextField("30");
        rightField.setPrefSize(360, 28);

        Button sumBtn = new Button("相加");
        sumBtn.subscribeClick(() -> {
            onSum();
        });

        resultLabel = new Label("结果：—");
        resultLabel.setDisplayStyle(18, 0, -14935529);
        resultLabel.setPrefSize(380, 28);

        statusLabel = new Label("就绪");
        statusLabel.setDisplayStyle(12, 0, -10725812);
        statusLabel.setPrefSize(380, 22);

        VBox root = new VBox();
        root.spacing = 10;
        root.padding = 16;
        root.add(title);
        root.add(hint);
        root.add(new Label("第一个整数"));
        root.add(leftField);
        root.add(new Label("第二个整数"));
        root.add(rightField);
        root.add(sumBtn);
        root.add(resultLabel);
        root.add(statusLabel);
        w.setContent(root);
        w.show();
        app.run();
    }

    static void onSum() {
        String left = leftField.getText();
        String right = rightField.getText();
        int sum = compute(left, right);
        int err = getLastError();
        if (err == 1) {
            statusLabel.setText("错误：输入不能为空");
            return;
        }
        if (err == 2) {
            statusLabel.setText("错误：请输入合法整数");
            return;
        }
        resultLabel.setText("结果：" + String.valueOf(sum));
        statusLabel.setText("计算完成（JNative 原生）");
    }
}
