import jnative.ui.*;

/**
 * @author 张晓达
 */
public class HelloWindow {
    public static void main(String[] args) {
        Application app = new Application();
        Window window = new Window("Hello JNative", 800, 600);
        Button button = new Button("Click Me");
        button.onClick(() -> {
            System.out.println("Clicked!");
        });
        window.setContent(button);
        window.show();
        app.run();
    }
}
