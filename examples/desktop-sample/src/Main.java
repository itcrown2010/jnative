/**
 * Desktop sample — lifecycle-safe UI: commands, binding, background task, quit.
 * Data under build/desktop-sample/ only. Author: 张晓达
 */
public class Main {
    public static void main(String[] args) {
        if (args != null) {
            int i = 0;
            while (i < args.length) {
                if (args[i].equals("--self-test")) {
                    DsSelfTest.run();
                    return;
                }
                i = i + 1;
            }
        }
        Application.setAppName("Desktop Sample");
        Application.setQuitOnLastWindowClose(true);
        Application app = new Application();
        Theme.setAppearanceMode(Theme.MODE_SYSTEM);
        Application.onQuit(() -> {
            DsApp.teardown();
        });
        DsApp.open();
        app.run();
    }
}

class DsApp {
    static Window window;
    static BindingScope scope;
    static ObservableValue<String> note;
    static ObservableInt dirty;
    static Label status;
    static TextField field;
    static int alive;
    static int gen;
    static String dataPath;

    static void open() {
        alive = 1;
        gen = gen + 1;
        dataPath = "build/desktop-sample/note.txt";
        scope = new BindingScope();
        note = ObservableValue.ofString(DsStore.load(dataPath));
        dirty = new ObservableInt(0);
        window = new Window("Desktop Sample", 480, 320);
        window.setMinSize(360, 240);
        window.onClose(() -> {
            DsApp.onClose();
        });

        VBox root = new VBox();
        root.setSpacing(8);
        root.setPadding(16);
        Label title = new Label("生命周期示例");
        Theme.applyStyle(title, Theme.STYLE_TITLE);
        field = new TextField(note.get());
        field.setAccessibleName("备注");
        Bindings.bindTextTwoWay(field, note, scope);
        scope.addObs(note.subscribe((ev) -> {
            DsApp.markDirty();
        }));
        status = new Label("就绪");
        Theme.applyStyle(status, Theme.STYLE_SECONDARY);
        status.setAccessibleName("状态");
        Button save = new Button("保存");
        save.setAccessibleName("保存");
        Button cancel = new Button("还原");
        cancel.setAccessibleName("还原");
        Button bg = new Button("后台任务");
        bg.setAccessibleName("后台任务");
        save.onClick(() -> {
            DsApp.save();
        });
        cancel.onClick(() -> {
            DsApp.revert();
        });
        bg.onClick(() -> {
            DsApp.runBackground();
        });
        window.setDefaultButton(save);
        window.setCancelButton(cancel);
        HBox row = new HBox();
        row.setSpacing(8);
        row.add(save);
        row.add(cancel);
        row.add(bg);
        root.add(title);
        root.add(new Label("备注（绑定 + 未保存确认）"));
        root.add(field);
        root.add(row);
        root.add(status);
        window.setContent(root);

        ArrayList<Component> tabs = new ArrayList<Component>();
        tabs.add(field);
        tabs.add(save);
        tabs.add(cancel);
        tabs.add(bg);
        FocusTraversal.apply(window, tabs);

        Command quit = new Command("quit", "退出");
        quit.setExecute(() -> {
            DsApp.confirmQuit();
        });
        MenuBar bar = new MenuBar();
        Menu file = new Menu("文件");
        MenuItem miSave = new MenuItem("保存");
        Command saveCmd = new Command("save", "保存");
        saveCmd.setShortcut(KeyCombination.of(83).withPrimary());
        saveCmd.setExecute(() -> {
            DsApp.save();
        });
        saveCmd.bindMenuItem(miSave);
        file.add(miSave);
        MenuItem miQuit = new MenuItem("退出");
        quit.bindMenuItem(miQuit);
        file.add(miQuit);
        bar.add(file);
        bar.add(AppMenus.editMenu());
        bar.add(AppMenus.helpMenu(null));
        window.setMenuBar(bar);
        ShortcutManager.installOn(window);
        window.show();
        status.setText("scale%=" + String.valueOf(Dpi.scalePercentForId(window.nativeId)));
    }

    static void markDirty() {
        if (alive == 0 || scope == null || scope.isDisposed() != 0) {
            return;
        }
        dirty.set(1);
        status.setText("有未保存更改");
    }

    static void save() {
        if (alive == 0) {
            return;
        }
        int ok = FileDialog.writeTextSafe(dataPath, note.get());
        if (ok != 0) {
            dirty.set(0);
            status.setText("已保存");
        } else {
            status.setText("保存失败");
            Dialogs.error(window, "无法写入 " + dataPath);
        }
    }

    static void revert() {
        note.set(DsStore.load(dataPath));
        dirty.set(0);
        status.setText("已还原");
    }

    static void runBackground() {
        int token = gen;
        status.setText("后台运行中…");
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(200);
            } catch (Exception e) {
            }
            /* Coalesce: burst of completions keep only the latest UI update. */
            UiThread.coalesce(() -> {
                if (alive == 0 || token != gen || window == null || window.disposed != 0) {
                    return;
                }
                status.setText("后台完成 gen=" + String.valueOf(token));
            });
        });
        t.start();
    }

    static void confirmQuit() {
        if (dirty.get() != 0) {
            int c = Dialogs.askSave(window, "退出前保存备注？");
            if (c == 0) {
                return;
            }
            if (c == 1) {
                save();
                if (dirty.get() != 0) {
                    return;
                }
            }
        }
        Application.requestQuit();
    }

    static void onClose() {
        if (Application.quitting != 0) {
            teardown();
            return;
        }
        if (dirty.get() != 0) {
            int c = Dialogs.askSave(window, "有未保存更改。");
            if (c == 0) {
                window.vetoClose();
                return;
            }
            if (c == 1) {
                save();
                if (dirty.get() != 0) {
                    window.vetoClose();
                    return;
                }
            }
        }
        teardown();
    }

    static void teardown() {
        alive = 0;
        gen = gen + 1;
        if (scope != null) {
            scope.dispose();
            scope = null;
        }
        window = null;
    }
}

class DsStore {
    static String load(String path) {
        try {
            if (!File.exists(path)) {
                return "hello desktop";
            }
            String s = File.readString(path);
            if (s == null) {
                return "";
            }
            return s;
        } catch (Exception e) {
            return "";
        }
    }
}

class DsSelfTest {
    static void run() {
        Application.setHeadless(1);
        Application app = new Application();
        DsApp.open();
        if (DsApp.window == null) {
            throw new RuntimeException("open failed");
        }
        UiTest.focus(DsApp.field);
        DsApp.note.set("self-test");
        DsApp.save();
        int before = Application.liveNativeHandles();
        UiTest.setDialogResult(2);
        UiTest.closeWindow(DsApp.window);
        int after = Application.liveNativeHandles();
        System.out.println("DS_HANDLES_BEFORE=" + String.valueOf(before));
        System.out.println("DS_HANDLES_AFTER=" + String.valueOf(after));
        if (after > before) {
            throw new RuntimeException("handle count grew after close");
        }
        System.out.println("DS_SELF_TEST_OK");
        Application.quit();
        app.run();
    }
}
