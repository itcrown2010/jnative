/**
 * 商品编辑器 — 演示可观察状态、双向绑定、表单校验与 JSON 持久化。
 * 金额使用 Decimal；不拼接 SQL。
 *
 * @author 张晓达
 */
public class ProductEditor {
    public static void main(String[] args) {
        int appearance = Theme.MODE_SYSTEM;
        String path = "build/product-editor/product.json";
        if (args != null) {
            int i = 0;
            while (i < args.length) {
                if (args[i].equals("--appearance") && i + 1 < args.length) {
                    if (args[i + 1].equals("dark")) {
                        appearance = Theme.MODE_DARK;
                    } else if (args[i + 1].equals("light")) {
                        appearance = Theme.MODE_LIGHT;
                    }
                    i = i + 1;
                }
                if (args[i].equals("--data") && i + 1 < args.length) {
                    path = args[i + 1];
                    i = i + 1;
                }
                if (args[i].equals("--self-test")) {
                    PeSelfTest.run();
                    return;
                }
                i = i + 1;
            }
        }
        Application.setAppName("商品编辑器");
        Application.setQuitOnLastWindowClose(true);
        Application app = new Application();
        Theme.setAppearanceMode(appearance);
        Application.onQuit(() -> {
            PeActions.teardown();
        });
        PeUi.open(path);
        app.run();
    }
}

class ProductRecord {
    String id;
    String name;
    String categoryId;
    String priceText;
    String qtyText;
    int enabled;

    ProductRecord() {
        id = "p1";
        name = "";
        categoryId = "general";
        priceText = "";
        qtyText = "1";
        enabled = 1;
    }
}

class ProductStore {
    static String path;

    static ProductRecord load(String filePath) {
        path = filePath;
        ProductRecord r = new ProductRecord();
        try {
            if (!File.exists(filePath)) {
                return r;
            }
            String text = File.readString(filePath);
            if (text == null || text.length() == 0) {
                return r;
            }
            JsonValue root = Json.parse(text);
            if (!(root instanceof JsonObject)) {
                return r;
            }
            JsonObject o = (JsonObject) root;
            r.id = jsonStr(o, "id", r.id);
            r.name = jsonStr(o, "name", "");
            r.categoryId = jsonStr(o, "categoryId", "general");
            r.priceText = jsonStr(o, "price", "");
            r.qtyText = jsonStr(o, "qty", "1");
            r.enabled = jsonInt(o, "enabled", 1);
            return r;
        } catch (Exception ex) {
            return r;
        }
    }

    static int save(ProductRecord r) {
        if (path == null) {
            return 0;
        }
        try {
            String json = "{"
                    + "\"id\":" + jsonQuote(r.id)
                    + ",\"name\":" + jsonQuote(r.name)
                    + ",\"categoryId\":" + jsonQuote(r.categoryId)
                    + ",\"price\":" + jsonQuote(r.priceText)
                    + ",\"qty\":" + jsonQuote(r.qtyText)
                    + ",\"enabled\":" + String.valueOf(r.enabled)
                    + "}";
            return FileDialog.writeTextSafe(path, json);
        } catch (Exception ex) {
            return 0;
        }
    }

    static String jsonStr(JsonObject o, String key, String def) {
        JsonValue v = o.get(key);
        if (v instanceof JsonString) {
            return ((JsonString) v).value;
        }
        return def;
    }

    static int jsonInt(JsonObject o, String key, int def) {
        JsonValue v = o.get(key);
        if (v instanceof JsonNumber) {
            JsonNumber num = (JsonNumber) v;
            if (num.literal != null && num.literal.length() > 0) {
                return JsonParseInt.fromNumberString(num.literal, def);
            }
            return def;
        }
        return def;
    }

    static String jsonQuote(String s) {
        if (s == null) {
            s = "";
        }
        String out = "\"";
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c == 34 || c == 92) {
                out = out + "\\";
            }
            out = out + s.substring(i, i + 1);
            i = i + 1;
        }
        return out + "\"";
    }
}

class PeUi {
    static Window window;
    static BindingScope scope;
    static ProductRecord loaded;
    static ProductRecord draft;
    static StringFieldModel nameField;
    static DecimalFieldModel priceField;
    static DecimalFieldModel qtyField;
    static ObservableValue<String> categoryId;
    static ObservableInt enabled;
    static ObservableInt dirty;
    static ObservableInt canSave;
    static FormModel form;
    static Label status;
    static Label formErr;
    static Label serverErr;
    static Button saveBtn;
    static CheckBox enableBox;
    static TextField nameTf;
    static NumericField priceCtl;
    static NumericField qtyCtl;
    static SearchableSelect<String> categorySelect;
    static Button cancelBtn;
    static ArrayList<String> categoryIds;
    static ArrayList<String> categoryLabels;
    static Window aboutWin;
    static int alive;
    /** Generation token: incremented on close so late invokeLater cannot mutate UI. */
    static int gen;

    static void open(String path) {
        alive = 1;
        gen = gen + 1;
        loaded = ProductStore.load(path);
        draft = copyOf(loaded);
        scope = new BindingScope();
        window = new Window("商品编辑器", 520, 640);
        window.setMinSize(420, 520);
        window.onClose(() -> {
            PeActions.onClose();
        });
        build();
        PeActions.installMenus();
        ShortcutManager.installOn(window);
        window.show();
        /* Re-sync after native peers realize (combo / check title sometimes blank pre-show). */
        PeActions.resyncControls();
        PeActions.onFieldChanged();
    }

    static ProductRecord copyOf(ProductRecord s) {
        ProductRecord d = new ProductRecord();
        d.id = s.id;
        d.name = s.name;
        d.categoryId = s.categoryId;
        d.priceText = s.priceText;
        d.qtyText = s.qtyText;
        d.enabled = s.enabled;
        return d;
    }

    static void build() {
        categoryIds = new ArrayList<String>();
        categoryLabels = new ArrayList<String>();
        categoryIds.add("general");
        categoryLabels.add("常规");
        categoryIds.add("food");
        categoryLabels.add("食品");
        categoryIds.add("elec");
        categoryLabels.add("电子");

        nameField = new StringFieldModel();
        nameField.raw.set(draft.name);
        nameField.addRule(new RequiredRule("名称必填"));
        nameField.addRule(new LengthRule(1, 64, "名称长度 1–64"));

        priceCtl = new NumericField("单价");
        priceCtl.setRange(Decimal.fromString("0.01"), Decimal.fromString("999999.99"), "单价须在 0.01-999999.99");
        priceCtl.setMaxFractionDigits(2);
        priceCtl.setAllowNegative(false);
        priceCtl.setRequired(true);
        priceCtl.setRaw(draft.priceText);
        priceField = priceCtl.model();
        priceField.addRule(new RequiredRule("单价必填"));

        qtyCtl = new NumericField("数量");
        qtyCtl.setRange(Decimal.fromString("1"), Decimal.fromString("99999"), "数量须在 1-99999");
        qtyCtl.setMaxFractionDigits(0);
        qtyCtl.setAllowNegative(false);
        qtyCtl.setRequired(true);
        qtyCtl.setRaw(draft.qtyText);
        qtyField = qtyCtl.model();
        qtyField.addRule(new RequiredRule("数量必填"));

        categorySelect = new SearchableSelect<String>("分类");
        ArrayList<SelectOption<String>> catOpts = new ArrayList<SelectOption<String>>();
        int ci = 0;
        while (ci < categoryIds.size()) {
            catOpts.add(new SelectOption<String>(
                    categoryIds.get(ci), categoryLabels.get(ci), categoryIds.get(ci)));
            ci = ci + 1;
        }
        /* Same display label twice to verify stable id (food vs food2). */
        catOpts.add(new SelectOption<String>("food2", "食品", "food2"));
        categorySelect.setOptions(catOpts);
        categorySelect.setSelectedId(draft.categoryId);
        categoryId = categorySelect.selectedId();

        enabled = new ObservableInt(draft.enabled);
        dirty = new ObservableInt(0);
        canSave = new ObservableInt(0);

        form = new FormModel();
        form.addString(nameField);
        form.addDecimal(priceField);
        form.addDecimal(qtyField);
        form.addCross(new PeLineAmountRule());

        VBox root = new VBox();
        root.setSpacing(8);
        root.setPadding(16);

        Label title = new Label("编辑商品");
        Theme.applyStyle(title, Theme.STYLE_TITLE);

        nameTf = new TextField(draft.name);
        nameTf.setAccessibleName("商品名称");
        Label nameErr = new Label("");
        Theme.applyStyle(nameErr, Theme.STYLE_ERROR);
        nameField.bind(nameTf, nameErr, scope);

        CheckBox en = new CheckBox("启用销售");
        en.setAccessibleName("启用销售");
        Bindings.bindCheck(en, enabled, scope);
        PeUi.enableBox = en;

        formErr = new Label("");
        Theme.applyStyle(formErr, Theme.STYLE_ERROR);
        formErr.setAccessibleName("表单错误");
        Bindings.bindLabel(formErr, form.formError, scope);
        serverErr = new Label("");
        Theme.applyStyle(serverErr, Theme.STYLE_ERROR);
        serverErr.setAccessibleName("服务器错误");
        Bindings.bindLabel(serverErr, form.serverError, scope);

        status = new Label("已加载");
        Theme.applyStyle(status, Theme.STYLE_SECONDARY);
        status.setAccessibleName("状态");

        HBox actions = new HBox();
        actions.setSpacing(8);
        saveBtn = new Button("保存");
        saveBtn.setAccessibleName("保存");
        cancelBtn = new Button("取消");
        cancelBtn.setAccessibleName("取消");
        Bindings.bindEnabled(saveBtn, canSave, scope);
        saveBtn.onClick(() -> {
            PeActions.onSave();
        });
        cancelBtn.onClick(() -> {
            PeActions.onCancel();
        });
        actions.add(saveBtn);
        actions.add(cancelBtn);
        window.setDefaultButton(saveBtn);
        window.setCancelButton(cancelBtn);

        ArrayList<Component> tabOrder = new ArrayList<Component>();
        tabOrder.add(nameTf);
        tabOrder.add(categorySelect.focusTarget());
        tabOrder.add(priceCtl.focusTarget());
        tabOrder.add(qtyCtl.focusTarget());
        tabOrder.add(en);
        tabOrder.add(saveBtn);
        tabOrder.add(cancelBtn);
        FocusTraversal.apply(window, tabOrder);

        root.add(title);
        root.add(new Label("名称"));
        root.add(nameTf);
        root.add(nameErr);
        root.add(categorySelect.root());
        root.add(priceCtl.root());
        root.add(qtyCtl.root());
        root.add(en);
        root.add(formErr);
        root.add(serverErr);
        root.add(actions);
        root.add(status);

        /* Dirty + canSave derived from field observables; all held by BindingScope. */
        scope.addObs(nameField.raw.subscribe((ev) -> { PeActions.onFieldChanged(); }));
        scope.addObs(priceField.raw.subscribe((ev) -> { PeActions.onFieldChanged(); }));
        scope.addObs(qtyField.raw.subscribe((ev) -> { PeActions.onFieldChanged(); }));
        scope.addObs(categoryId.subscribe((ev) -> { PeActions.onFieldChanged(); }));
        scope.addObs(enabled.subscribe((ev) -> { PeActions.onFieldChanged(); }));

        window.setContent(root);
        PeActions.onFieldChanged();
    }
}

class PeLineAmountRule implements CrossFieldRule {
    String validate() {
        if (PeUi.priceField.hasValue() != 0 && PeUi.qtyField.hasValue() != 0) {
            Decimal line = PeUi.priceField.getParsed().mul(PeUi.qtyField.getParsed());
            if (line.compareTo(Decimal.fromString("1000000")) > 0) {
                return "行金额过大（单价×数量）";
            }
        }
        return null;
    }
}

class PeActions {
    static void installMenus() {
        Command openCmd = new Command("file.open", "打开…");
        openCmd.setShortcut(KeyCombination.of(79).withPrimary());
        openCmd.setExecute(() -> {
            PeActions.onOpen();
        });
        Command saveCmd = new Command("file.save", "保存");
        saveCmd.setShortcut(KeyCombination.of(83).withPrimary());
        saveCmd.setExecute(() -> {
            PeActions.onSave();
        });
        Command saveAsCmd = new Command("file.saveAs", "另存为…");
        saveAsCmd.setExecute(() -> {
            PeActions.onSaveAs();
        });
        Command quitCmd = new Command("app.quit", "退出");
        quitCmd.setExecute(() -> {
            PeActions.confirmQuitThenExit();
        });
        Command closeCmd = new Command("win.close", "关闭窗口");
        closeCmd.setShortcut(KeyCombination.of(87).withPrimary());
        closeCmd.setExecute(() -> {
            if (PeUi.window != null) {
                PeUi.window.close();
            }
        });
        Command aboutCmd = new Command("help.about", "关于商品编辑器");
        aboutCmd.setExecute(() -> {
            PeActions.onAboutWindow();
        });
        MenuBar bar = new MenuBar();
        Menu file = new Menu("文件");
        MenuItem miOpen = new MenuItem("打开…");
        openCmd.bindMenuItem(miOpen);
        file.add(miOpen);
        MenuItem miSave = new MenuItem("保存");
        saveCmd.bindMenuItem(miSave);
        file.add(miSave);
        MenuItem miSaveAs = new MenuItem("另存为…");
        saveAsCmd.bindMenuItem(miSaveAs);
        file.add(miSaveAs);
        file.addSeparator();
        MenuItem miQuit = new MenuItem("退出商品编辑器");
        quitCmd.bindMenuItem(miQuit);
        file.add(miQuit);
        bar.add(file);
        bar.add(AppMenus.editMenu());
        bar.add(AppMenus.windowMenu(closeCmd));
        bar.add(AppMenus.helpMenu(aboutCmd));
        PeUi.window.setMenuBar(bar);
    }

    static void onOpen() {
        String p = FileDialog.open(PeUi.window, "json");
        if (p == null) {
            return;
        }
        ProductStore.path = p;
        PeUi.loaded = ProductStore.load(p);
        PeUi.draft = PeUi.copyOf(PeUi.loaded);
        PeUi.nameField.raw.set(PeUi.draft.name);
        PeUi.priceField.raw.set(PeUi.draft.priceText);
        PeUi.qtyField.raw.set(PeUi.draft.qtyText);
        PeUi.categoryId.set(PeUi.draft.categoryId);
        PeUi.enabled.set(PeUi.draft.enabled);
        PeActions.resyncControls();
        PeActions.refreshDirty();
        PeUi.status.setText("已打开");
    }

    static void onSaveAs() {
        String p = FileDialog.save(PeUi.window, "json");
        if (p == null) {
            return;
        }
        ProductStore.path = p;
        PeActions.onSave();
    }

    static void onAboutWindow() {
        if (PeUi.aboutWin != null && PeUi.aboutWin.disposed == 0) {
            PeUi.aboutWin.activate();
            return;
        }
        Window aw = new Window("关于", 360, 200);
        aw.setOwnerWindow(PeUi.window);
        aw.setMinSize(280, 160);
        VBox box = new VBox();
        box.setSpacing(8);
        box.setPadding(16);
        box.add(new Label("商品编辑器"));
        box.add(new Label("演示命令、菜单、绑定与安全保存。"));
        Button close = new Button("关闭");
        close.onClick(() -> {
            aw.close();
        });
        box.add(close);
        aw.setContent(box);
        aw.onClose(() -> {
            PeUi.aboutWin = null;
        });
        PeUi.aboutWin = aw;
        aw.show();
    }

    static void resyncControls() {
        if (PeUi.categorySelect != null && PeUi.categoryId != null) {
            PeUi.categorySelect.setSelectedId(PeUi.categoryId.get());
        }
        if (PeUi.enableBox != null && PeUi.enabled != null) {
            int v = PeUi.enabled.get();
            /* Toggle peer so Cocoa matches; restore obs (setSelected fires change). */
            PeUi.enableBox.setSelected(false);
            if (v != 0) {
                PeUi.enableBox.setSelected(true);
            }
            PeUi.enabled.set(v);
        }
    }

    static void onFieldChanged() {
        if (PeUi.alive == 0 || PeUi.scope == null || PeUi.scope.isDisposed() != 0) {
            return;
        }
        PeUi.priceField.revalidate(1);
        PeUi.qtyField.revalidate(1);
        PeUi.nameField.revalidate(1);
        PeActions.refreshDirty();
        int ok = 1;
        if (PeUi.nameField.state.get() == FieldState.INVALID) {
            ok = 0;
        }
        if (PeUi.priceField.state.get() == FieldState.INVALID) {
            ok = 0;
        }
        if (PeUi.qtyField.state.get() == FieldState.INVALID) {
            ok = 0;
        }
        if (PeUi.dirty.get() == 0) {
            ok = 0;
        }
        PeUi.canSave.set(ok);
        PeUi.form.refreshCanSubmitSoft();
    }

    static void refreshDirty() {
        int d = 0;
        if (!PeUi.nameField.raw.get().equals(PeUi.loaded.name)) {
            d = 1;
        }
        if (!PeUi.priceField.raw.get().equals(PeUi.loaded.priceText)) {
            d = 1;
        }
        if (!PeUi.qtyField.raw.get().equals(PeUi.loaded.qtyText)) {
            d = 1;
        }
        if (!PeUi.categoryId.get().equals(PeUi.loaded.categoryId)) {
            d = 1;
        }
        if (PeUi.enabled.get() != PeUi.loaded.enabled) {
            d = 1;
        }
        PeUi.dirty.set(d);
        if (d != 0) {
            PeUi.status.setText("有未保存更改");
        } else {
            PeUi.status.setText("已与磁盘一致");
        }
    }

    static void focusFirstInvalid() {
        if (PeUi.nameField.state.get() == FieldState.INVALID && PeUi.nameTf != null) {
            FocusManager.requestFocus(PeUi.nameTf);
            return;
        }
        if (PeUi.priceField.state.get() == FieldState.INVALID && PeUi.priceCtl != null) {
            FocusManager.requestFocus(PeUi.priceCtl.focusTarget());
            return;
        }
        if (PeUi.qtyField.state.get() == FieldState.INVALID && PeUi.qtyCtl != null) {
            FocusManager.requestFocus(PeUi.qtyCtl.focusTarget());
        }
    }

    static void onSave() {
        if (PeUi.form.validateSubmit() == 0) {
            PeUi.status.setText("校验未通过");
            PeActions.focusFirstInvalid();
            return;
        }
        PeUi.draft.name = PeUi.nameField.raw.get();
        PeUi.draft.categoryId = PeUi.categoryId.get();
        PeUi.draft.priceText = PeUi.priceField.raw.get();
        PeUi.draft.qtyText = PeUi.qtyField.raw.get();
        PeUi.draft.enabled = PeUi.enabled.get();
        /* Simulate async persist; late result must not touch disposed UI. */
        int token = PeUi.gen;
        String failEnv = Env.get("PE_FORCE_SAVE_FAIL");
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(120);
            } catch (Exception e) {
            }
            int ok = 1;
            if (failEnv != null && failEnv.equals("1")) {
                ok = 0;
            } else {
                ok = ProductStore.save(PeUi.draft);
            }
            int finalOk = ok;
            Application.invokeLater(() -> {
                if (PeUi.alive == 0 || token != PeUi.gen || PeUi.window == null
                        || PeUi.window.disposed != 0) {
                    return;
                }
                if (finalOk == 0) {
                    PeUi.form.setServerError("保存失败：请检查磁盘权限后重试");
                    PeUi.status.setText("保存失败（输入已保留）");
                    return;
                }
                PeUi.form.setServerError("");
                PeUi.loaded = PeUi.copyOf(PeUi.draft);
                PeActions.refreshDirty();
                PeUi.status.setText("保存成功");
                Dialogs.info(PeUi.window, "商品已保存。");
            });
        });
        t.start();
    }

    static void onCancel() {
        if (PeUi.dirty.get() != 0) {
            boolean ok = Dialogs.confirm(PeUi.window, "放弃未保存更改？");
            if (!ok) {
                return;
            }
        }
        PeUi.nameField.raw.set(PeUi.loaded.name);
        PeUi.priceField.raw.set(PeUi.loaded.priceText);
        PeUi.qtyField.raw.set(PeUi.loaded.qtyText);
        PeUi.categoryId.set(PeUi.loaded.categoryId);
        PeUi.enabled.set(PeUi.loaded.enabled);
        PeUi.form.formError.set("");
        PeUi.form.setServerError("");
        PeActions.refreshDirty();
        PeUi.status.setText("已还原");
    }

    /** Quit menu: confirm dirty first, then requestQuit (veto ignored while quitting). */
    static void confirmQuitThenExit() {
        if (PeUi.dirty != null && PeUi.dirty.get() != 0) {
            int choice = Dialogs.askSave(PeUi.window, "退出前保存商品更改？");
            if (choice == 0) {
                return;
            }
            if (choice == 1) {
                if (PeUi.form.validateSubmit() == 0) {
                    PeUi.status.setText("校验未通过，无法退出保存");
                    PeActions.focusFirstInvalid();
                    return;
                }
                PeUi.draft.name = PeUi.nameField.raw.get();
                PeUi.draft.categoryId = PeUi.categoryId.get();
                PeUi.draft.priceText = PeUi.priceField.raw.get();
                PeUi.draft.qtyText = PeUi.qtyField.raw.get();
                PeUi.draft.enabled = PeUi.enabled.get();
                if (ProductStore.save(PeUi.draft) == 0) {
                    PeUi.form.setServerError("保存失败，未退出");
                    return;
                }
                PeUi.loaded = PeUi.copyOf(PeUi.draft);
                PeUi.dirty.set(0);
            }
        }
        Application.requestQuit();
    }

    static void onClose() {
        if (Application.quitting != 0) {
            PeActions.teardown();
            return;
        }
        if (PeUi.dirty.get() != 0) {
            int choice = Dialogs.askSave(PeUi.window, "商品有未保存更改。");
            if (choice == 0) {
                PeUi.window.vetoClose();
                return;
            }
            if (choice == 1) {
                if (PeUi.form.validateSubmit() == 0) {
                    PeUi.status.setText("校验未通过，无法保存");
                    PeUi.window.vetoClose();
                    return;
                }
                PeUi.draft.name = PeUi.nameField.raw.get();
                PeUi.draft.categoryId = PeUi.categoryId.get();
                PeUi.draft.priceText = PeUi.priceField.raw.get();
                PeUi.draft.qtyText = PeUi.qtyField.raw.get();
                PeUi.draft.enabled = PeUi.enabled.get();
                if (ProductStore.save(PeUi.draft) == 0) {
                    PeUi.form.setServerError("保存失败");
                    PeUi.window.vetoClose();
                    return;
                }
                PeUi.loaded = PeUi.copyOf(PeUi.draft);
            }
        }
        PeActions.teardown();
    }

    static void teardown() {
        PeUi.alive = 0;
        PeUi.gen = PeUi.gen + 1;
        if (PeUi.priceCtl != null) {
            PeUi.priceCtl.dispose();
            PeUi.priceCtl = null;
        }
        if (PeUi.qtyCtl != null) {
            PeUi.qtyCtl.dispose();
            PeUi.qtyCtl = null;
        }
        if (PeUi.categorySelect != null) {
            PeUi.categorySelect.dispose();
            PeUi.categorySelect = null;
        }
        if (PeUi.scope != null) {
            PeUi.scope.dispose();
            PeUi.scope = null;
        }
        PeUi.window = null;
        PeUi.aboutWin = null;
    }
}

class PeSelfTest {
    static int hits;

    static void run() {
        Application.setHeadless(1);
        Application app = new Application();
        ObservableValue<String> s = ObservableValue.ofString("a");
        hits = 0;
        ObsSubscription sub = s.subscribe((ev) -> {
            PeSelfTest.hits = PeSelfTest.hits + 1;
        });
        s.set("a");
        if (hits != 0) {
            throw new RuntimeException("equals-skip failed");
        }
        s.set("b");
        if (hits != 1) {
            throw new RuntimeException("notify failed");
        }
        sub.cancel();
        s.set("c");
        if (hits != 1) {
            throw new RuntimeException("cancel failed");
        }
        hits = 0;
        ObservableInt n = new ObservableInt(1);
        n.subscribe((ev) -> {
            PeSelfTest.hits = PeSelfTest.hits + 1;
        });
        n.beginUpdate();
        n.set(2);
        n.set(3);
        if (hits != 0) {
            throw new RuntimeException("batch must defer notify");
        }
        n.endUpdate();
        if (hits != 1 || n.get() != 3) {
            throw new RuntimeException("batch must notify once with final value");
        }

        hits = 0;
        ObservableInt nest = new ObservableInt(0);
        nest.subscribe((ev) -> {
            PeSelfTest.hits = PeSelfTest.hits + 1;
            if (ev.newValue < 4) {
                nest.set(ev.newValue + 1);
            }
        });
        nest.set(1);
        if (hits != 4 || nest.get() != 4) {
            throw new RuntimeException("nested update failed");
        }
        int threw = 0;
        try {
            ObservableInt loop = new ObservableInt(0);
            loop.subscribe((ev) -> {
                if (ev.newValue < 40) {
                    loop.set(ev.newValue + 1);
                }
            });
            loop.set(1);
        } catch (UiException ex) {
            threw = 1;
        }
        if (threw == 0) {
            throw new RuntimeException("MAX_NESTING must throw");
        }

        BindingScope sc = new BindingScope();
        ObservableValue<String> held = ObservableValue.ofString("k");
        hits = 0;
        sc.addObs(held.subscribe((ev) -> {
            PeSelfTest.hits = PeSelfTest.hits + 1;
        }));
        held.set("m");
        if (hits != 1) {
            throw new RuntimeException("scope subscribe failed");
        }
        sc.dispose();
        held.set("n");
        if (hits != 1) {
            throw new RuntimeException("scope dispose must cancel");
        }

        DecimalFieldModel df = new DecimalFieldModel();
        df.raw.set("12.5");
        if (df.revalidate(0) == 0 || df.hasValue() == 0) {
            throw new RuntimeException("decimal parse failed");
        }
        df.raw.set("not-a-number");
        if (df.revalidate(0) != 0 || df.hasValue() != 0) {
            throw new RuntimeException("decimal must not coerce to 0");
        }
        if (df.getParsed() != null) {
            throw new RuntimeException("parsed must stay null on failure");
        }
        df.raw.set("3.14");
        if (df.revalidate(0) == 0 || df.hasValue() == 0) {
            throw new RuntimeException("decimal recover failed");
        }

        /* Native-id UI integration (headless inject — not screen coordinates). */
        String dataPath = "build/product-editor/self-test-product.json";
        FileDialog.writeTextSafe(dataPath,
                "{\"id\":\"t1\",\"name\":\"测试茶\",\"categoryId\":\"food\",\"price\":\"9.90\",\"qty\":\"2\",\"enabled\":1}");
        PeUi.open(dataPath);
        if (PeUi.window == null || PeUi.nameTf == null) {
            throw new RuntimeException("ui open failed");
        }
        int scale = Dpi.scalePercentForId(PeUi.window.nativeId);
        if (scale < 50) {
            throw new RuntimeException("dpi scale invalid");
        }
        System.out.println("PE_DPI_SCALE_PERCENT=" + String.valueOf(scale));
        System.out.println("PE_REDUCED_MOTION=" + String.valueOf(Theme.prefersReducedMotion()));
        System.out.println("PE_INCREASED_CONTRAST=" + String.valueOf(Theme.prefersIncreasedContrast()));
        UiTest.focus(PeUi.nameTf);
        if (PeUi.nameTf.hasFocus() == false && FocusManager.focused != PeUi.nameTf) {
            /* Headless focus may only update FocusManager via fireFocus. */
            FocusManager.requestFocus(PeUi.nameTf);
        }
        PeUi.nameField.raw.set("");
        if (PeUi.form.validateSubmit() != 0) {
            throw new RuntimeException("empty name must fail submit");
        }
        PeActions.focusFirstInvalid();
        if (FocusManager.focused != PeUi.nameTf) {
            throw new RuntimeException("focus should move to invalid name field");
        }
        PeUi.nameField.raw.set("测试茶");
        PeUi.priceField.raw.set("9.90");
        PeUi.qtyField.raw.set("2");
        if (PeUi.enableBox != null) {
            UiTest.toggle(PeUi.enableBox);
            UiTest.toggle(PeUi.enableBox);
        }
        UiTest.keyPress(PeUi.window, KeyCode.ESCAPE);
        UiTest.flush();
        UiTest.setDialogResult(2); /* Don't Save if askSave appears */
        UiTest.closeWindow(PeUi.window);
        System.out.println("PE_UI_INTEGRATION_OK");
        System.out.println("PE_SELF_TEST_OK");
        Application.quit();
        app.run();
    }
}
