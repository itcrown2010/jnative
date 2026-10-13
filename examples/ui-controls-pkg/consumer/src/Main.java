/**
 * Consumer of local/ui-controls — form demo + --self-test.
 * Author: 张晓达
 */
public class Main {
    public static void main(String[] args) {
        System.out.println(ControlsPkgVersion.id());
        if (args != null) {
            int i = 0;
            while (i < args.length) {
                if (args[i].equals("--self-test")) {
                    ControlsSelfTest.run();
                    return;
                }
                i = i + 1;
            }
        }
        Application.setAppName("UI Controls Demo");
        Application.setQuitOnLastWindowClose(true);
        Application app = new Application();
        Theme.setAppearanceMode(Theme.MODE_SYSTEM);
        DemoForm.open();
        app.run();
    }
}

class DemoForm {
    static Window window;
    static NumericField price;
    static NumericField qty;
    static SearchableSelect<String> category;
    static Label status;

    static void open() {
        window = new Window("组合控件演示", 420, 480);
        window.setMinSize(360, 400);
        price = new NumericField("单价");
        price.setRange(Decimal.fromString("0.01"), Decimal.fromString("999999.99"), "单价须在 0.01-999999.99");
        price.setMaxFractionDigits(2);
        price.setAllowNegative(false);
        price.setRequired(true);
        price.setRaw("9.90");

        qty = new NumericField("数量");
        qty.setRange(Decimal.fromString("1"), Decimal.fromString("99999"), "数量须在 1-99999");
        qty.setMaxFractionDigits(0);
        qty.setAllowNegative(false);
        qty.setRequired(true);
        qty.setRaw("2");

        category = new SearchableSelect<String>("分类");
        ArrayList<SelectOption<String>> opts = new ArrayList<SelectOption<String>>();
        opts.add(new SelectOption<String>("general", "常规", "general"));
        opts.add(new SelectOption<String>("food", "食品", "food"));
        opts.add(new SelectOption<String>("food2", "食品", "food-dup"));
        opts.add(new SelectOption<String>("elec", "电子", "elec"));
        category.setOptions(opts);
        category.setSelectedId("food");

        status = new Label("就绪");
        Theme.applyStyle(status, Theme.STYLE_SECONDARY);
        Button ok = new Button("校验");
        ok.onClick(() -> {
            DemoForm.onValidate();
        });

        VBox root = new VBox();
        root.setSpacing(10);
        root.setPadding(16);
        root.add(new Label("依赖 local/ui-controls@0.1.0"));
        root.add(price.root());
        root.add(qty.root());
        root.add(category.root());
        root.add(ok);
        root.add(status);
        window.setContent(root);

        ArrayList<Component> tabs = new ArrayList<Component>();
        tabs.add(price.focusTarget());
        tabs.add(qty.focusTarget());
        tabs.add(category.focusTarget());
        tabs.add(ok);
        FocusTraversal.apply(window, tabs);
        window.show();
    }

    static void onValidate() {
        int p = price.revalidate(0);
        int q = qty.revalidate(0);
        if (p == 0 || q == 0) {
            status.setText("校验失败");
            return;
        }
        String cid = category.getSelectedId();
        if (cid == null) {
            cid = "";
        }
        Decimal pd = price.getParsed();
        Decimal qd = qty.getParsed();
        String ps = "";
        String qs = "";
        if (pd != null) {
            ps = pd.toPlainString();
        }
        if (qd != null) {
            qs = qd.toPlainString();
        }
        status.setText("OK price=" + ps + " qty=" + qs + " cat=" + cid);
    }
}

class ControlsSelfTest {
    static NumericField nf;
    static SearchableSelect<String> ss;
    static SelectOption<String> sel;
    static ArrayList<SelectOption<String>> opts;

    static void run() {
        Application.setHeadless(1);
        Application app = new Application();

        nf = new NumericField("num");
        nf.setRange(Decimal.fromString("1"), Decimal.fromString("10"), "range 1-10");
        nf.setMaxFractionDigits(2);
        nf.setAllowNegative(false);
        nf.setRequired(true);
        nf.setRaw("abc");
        if (nf.revalidate(0) != 0 || nf.hasValue() != 0) {
            throw new RuntimeException("invalid text must not parse");
        }
        if (nf.getParsed() != null) {
            throw new RuntimeException("parsed must stay null");
        }
        String bad = nf.getRaw();
        if (!bad.equals("abc")) {
            throw new RuntimeException("raw text must be preserved");
        }
        nf.setRaw("1.234");
        if (nf.revalidate(0) != 0) {
            throw new RuntimeException("max fraction digits must reject");
        }
        nf.setRaw("-3");
        if (nf.revalidate(0) != 0) {
            throw new RuntimeException("negative must reject when disallowed");
        }
        nf.setRaw("");
        if (nf.revalidate(0) != 0) {
            throw new RuntimeException("empty required must fail");
        }
        nf.setRaw("3.14");
        if (nf.revalidate(0) == 0 || nf.hasValue() == 0) {
            throw new RuntimeException("valid decimal failed");
        }

        ss = new SearchableSelect<String>("cat");
        opts = new ArrayList<SelectOption<String>>();
        opts.add(new SelectOption<String>("a", "same", "va"));
        opts.add(new SelectOption<String>("b", "same", "vb"));
        opts.add(new SelectOption<String>("c", "other", "vc"));
        ss.setOptions(opts);
        ss.setSelectedId("b");
        if (!"b".equals(ss.getSelectedId())) {
            throw new RuntimeException("stable id selection failed");
        }
        sel = ss.getSelectedOption();
        if (sel == null) {
            throw new RuntimeException("selected option null");
        }
        if (!"vb".equals(sel.value)) {
            throw new RuntimeException("same-label must resolve by id");
        }
        ss.query().set("none");
        int genBefore = ss.generation();
        ss.dispose();
        if (ss.isAlive() != 0) {
            throw new RuntimeException("dispose must clear alive");
        }
        if (ss.generation() == genBefore) {
            throw new RuntimeException("dispose must bump gen");
        }
        nf.dispose();

        System.out.println("UI_CONTROLS_SELF_TEST_OK");
        Application.quit();
        app.run();
    }
}
