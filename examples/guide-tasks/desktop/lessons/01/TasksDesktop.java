/**
 * Lesson 01 endpoint — window + label only.
 * @author 张晓达
 */
public class TasksDesktop {
    public static void main(String[] args) {
        Theme.setAppearanceMode(Theme.MODE_LIGHT);
        Application app = new Application();
        Window w = new Window("JNative 任务管理器", 480, 320);
        Label title = new Label("小型任务管理器");
        title.setDisplayStyle(20, 0, -14935529);
        VBox root = new VBox();
        root.spacing = 12;
        root.padding = 16;
        root.add(title);
        w.setContent(root);
        w.show();
        app.run();
    }
}
