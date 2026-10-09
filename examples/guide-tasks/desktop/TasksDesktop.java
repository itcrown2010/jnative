/**
 * Desktop task manager UI (jnative.ui). Rules live in TaskCore only.
 * Types come from flattened stdlib (no jnative.ui import FQNs).
 *
 * Build: ./examples/guide-tasks/scripts/build_desktop.sh
 * @author 张晓达
 */
public class TasksDesktop {
    public static void main(String[] args) {
        Theme.setAppearanceMode(Theme.MODE_LIGHT);
        Application app = new Application();
        TasksWindow.show();
        app.run();
    }
}

class TasksUi {
    static Window window;
    static Label title;
    static Label status;
    static TextField input;
    static Button addBtn;
    static Button saveBtn;
    static Button loadBtn;
    static Button bgBtn;
    static VBox listBox;
    static int alive;
    static String savePath;
    static UiSubscription addSub;
    static int bgToken;

    static void ensure() {
        if (savePath == null) {
            savePath = "build/guide-tasks/tasks-save.txt";
        }
        alive = 1;
    }
}

class TasksWindow {
    static void show() {
        TasksUi.ensure();
        TasksUi.window = new Window("JNative 任务管理器", 520, 640);
        TasksUi.window.onClose(() -> {
            TasksUi.alive = 0;
            TasksUi.bgToken = TasksUi.bgToken + 1;
            if (TasksUi.addSub != null) {
                TasksUi.addSub.cancel();
                TasksUi.addSub = null;
            }
        });

        VBox root = new VBox();
        root.spacing = 10;
        root.padding = 16;

        TasksUi.title = new Label("小型任务管理器");
        TasksUi.title.setDisplayStyle(20, 0, -14935529);
        TasksUi.title.setPrefSize(480, 28);

        TasksUi.status = new Label("就绪");
        TasksUi.status.setDisplayStyle(12, 0, -10725812);
        TasksUi.status.setPrefSize(480, 22);

        HBox form = new HBox();
        form.spacing = 8;
        TasksUi.input = new TextField("");
        TasksUi.input.setPrefSize(300, 28);
        TasksUi.addBtn = new Button("添加");
        TasksUi.addSub = TasksUi.addBtn.subscribeClick(() -> {
            TasksActions.addFromInput();
        });
        form.add(TasksUi.input);
        form.add(TasksUi.addBtn);

        HBox tools = new HBox();
        tools.spacing = 8;
        TasksUi.saveBtn = new Button("保存");
        TasksUi.loadBtn = new Button("加载");
        TasksUi.bgBtn = new Button("后台计数");
        TasksUi.saveBtn.onClick(() -> { TasksActions.save(); });
        TasksUi.loadBtn.onClick(() -> { TasksActions.load(); });
        TasksUi.bgBtn.onClick(() -> { TasksActions.startBackground(); });
        tools.add(TasksUi.saveBtn);
        tools.add(TasksUi.loadBtn);
        tools.add(TasksUi.bgBtn);

        TasksUi.listBox = new VBox();
        TasksUi.listBox.spacing = 6;
        TasksUi.listBox.padding = 8;

        root.add(TasksUi.title);
        root.add(TasksUi.status);
        root.add(form);
        root.add(tools);
        root.add(TasksUi.listBox);

        TasksUi.window.setContent(root);
        TasksUi.window.show();
        TasksActions.refreshList();
        TasksActions.setStatus("就绪 — 输入任务后点添加", 0);
    }
}

class TasksActions {
    static void setStatus(String msg, int isError) {
        if (TasksUi.alive == 0 || TasksUi.status == null) {
            return;
        }
        TasksUi.status.setText(msg);
        if (isError != 0) {
            TasksUi.status.setDisplayStyle(12, 0, -65536);
        } else {
            TasksUi.status.setDisplayStyle(12, 0, -10725812);
        }
    }

    static void addFromInput() {
        if (TasksUi.alive == 0) {
            return;
        }
        String t = TasksUi.input.getText();
        if (t == null) {
            t = "";
        }
        int id = TaskCore.addTaskString(t);
        if (id < 0) {
            int err = TaskCore.getLastError();
            if (err == 2) {
                setStatus("标题不能为空", 1);
            } else if (err == 3) {
                setStatus("标题过长（最多 " + String.valueOf(TaskCore.TITLE_MAX) + "）", 1);
            } else if (err == 4) {
                setStatus("任务数量已满", 1);
            } else {
                setStatus("添加失败 code=" + String.valueOf(err), 1);
            }
            return;
        }
        TasksUi.input.setText("");
        refreshList();
        setStatus("已添加 #" + String.valueOf(id), 0);
    }

    static void refreshList() {
        if (TasksUi.alive == 0 || TasksUi.listBox == null) {
            return;
        }
        /* Rebuild rows — simple teaching pattern. */
        VBox box = new VBox();
        box.spacing = 6;
        box.padding = 8;
        int n = TaskCore.getCount();
        int i = 0;
        while (i < n) {
            box.add(makeRow(i));
            i = i + 1;
        }
        if (n == 0) {
            Label empty = new Label("（暂无任务）");
            empty.setDisplayStyle(13, 0, -10725812);
            box.add(empty);
        }
        TasksUi.listBox = box;
        VBox root = new VBox();
        root.spacing = 10;
        root.padding = 16;
        root.add(TasksUi.title);
        root.add(TasksUi.status);
        HBox form = new HBox();
        form.spacing = 8;
        form.add(TasksUi.input);
        form.add(TasksUi.addBtn);
        root.add(form);
        HBox tools = new HBox();
        tools.spacing = 8;
        tools.add(TasksUi.saveBtn);
        tools.add(TasksUi.loadBtn);
        tools.add(TasksUi.bgBtn);
        root.add(tools);
        root.add(TasksUi.listBox);
        TasksUi.window.setContent(root);
    }

    static HBox makeRow(int index) {
        HBox row = new HBox();
        row.spacing = 8;
        String mark = "[ ] ";
        if (TaskCore.getDone(index) != 0) {
            mark = "[x] ";
        }
        Label lab = new Label(mark + TaskCore.titleString(index));
        lab.setPrefSize(280, 26);
        Button toggle = new Button("完成");
        Button edit = new Button("改");
        Button del = new Button("删");
        int idx = index;
        toggle.onClick(() -> {
            if (TasksUi.alive == 0) {
                return;
            }
            TaskCore.toggleDone(idx);
            refreshList();
        });
        edit.onClick(() -> {
            if (TasksUi.alive == 0) {
                return;
            }
            String t = TasksUi.input.getText();
            if (TaskCore.setTitleString(idx, t) == 0) {
                setStatus("编辑失败 code=" + String.valueOf(TaskCore.getLastError()), 1);
                return;
            }
            TasksUi.input.setText("");
            refreshList();
            setStatus("已更新 #" + String.valueOf(idx), 0);
        });
        del.onClick(() -> {
            if (TasksUi.alive == 0) {
                return;
            }
            TaskCore.removeAt(idx);
            refreshList();
            setStatus("已删除", 0);
        });
        row.add(lab);
        row.add(toggle);
        row.add(edit);
        row.add(del);
        return row;
    }

    static void save() {
        if (TasksUi.alive == 0) {
            return;
        }
        try {
            int n = TaskCore.prepareSave();
            String s = "";
            int i = 0;
            while (i < n) {
                s = s + TaskCore.unitToString(TaskCore.getSaveChar(i));
                i = i + 1;
            }
            File.writeString(TasksUi.savePath, s);
            setStatus("已保存 → " + TasksUi.savePath, 0);
        } catch (Exception e) {
            setStatus("保存异常", 1);
        }
    }

    static void load() {
        if (TasksUi.alive == 0) {
            return;
        }
        try {
            if (File.exists(TasksUi.savePath) == false) {
                setStatus("没有存档文件", 1);
                return;
            }
            String data = File.readString(TasksUi.savePath);
            if (TaskCore.loadSave(data) == 0) {
                setStatus("加载失败 code=" + String.valueOf(TaskCore.getLastError()), 1);
                return;
            }
            refreshList();
            setStatus("已加载 " + String.valueOf(TaskCore.getCount()) + " 条", 0);
        } catch (Exception e) {
            setStatus("加载异常", 1);
        }
    }

    static void startBackground() {
        if (TasksUi.alive == 0) {
            return;
        }
        TasksUi.bgToken = TasksUi.bgToken + 1;
        int token = TasksUi.bgToken;
        setStatus("后台任务运行中…", 0);
        Thread t = new Thread(() -> {
            int i = 0;
            while (i < 5) {
                try {
                    Thread.sleep(200);
                } catch (Exception e) {
                }
                i = i + 1;
            }
            int finished = i;
            Application.invokeLater(() -> {
                if (TasksUi.alive == 0 || token != TasksUi.bgToken) {
                    return;
                }
                setStatus("后台任务完成（计数 " + String.valueOf(finished) + "）", 0);
            });
        });
        t.start();
    }
}
