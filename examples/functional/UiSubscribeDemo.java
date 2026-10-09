/**
 * UI event subscription lifecycle (headless).
 * Build: JNATIVE_HEADLESS=1 java -jar target/jnative.jar build examples/functional/UiSubscribeDemo.java -o build/UiSubscribeDemo
 * Run:   JNATIVE_HEADLESS=1 ./build/UiSubscribeDemo
 * @author 张晓达
 */
import jnative.ui.*;

class ClickState {
    int n;
    String last;
}

public class UiSubscribeDemo {
    public static void main(String[] args) {
        Application.setHeadless(1);
        Application app = new Application();
        Window window = new Window("Subscribe", 320, 200);
        VBox root = new VBox();
        Button button = new Button("Go");
        ClickState state = new ClickState();
        state.n = 0;
        state.last = "";

        UiSubscription sub = button.subscribeAction(ev -> {
            state.n = state.n + 1;
            state.last = "点击" + state.n;
            System.out.println(state.last);
        });

        root.add(button);
        window.setContent(root);
        window.show();

        UiTest.click(button);
        UiTest.click(button);
        System.out.println(state.n);

        sub.cancel();
        sub.cancel(); // idempotent
        UiTest.click(button);
        System.out.println(state.n);

        // Re-subscribe then dispose clears handlers
        UiSubscription sub2 = button.subscribeAction(ev -> {
            state.n = state.n + 10;
        });
        button.dispose();
        sub2.cancel();
        System.out.println(state.n);

        UiTest.closeWindow(window);
        app.run();
    }
}
