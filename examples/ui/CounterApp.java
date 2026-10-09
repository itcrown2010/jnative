import jnative.ui.*;

/**
 * @author 张晓达
 */
public class CounterApp {

    public static void main(String[] args) {

        Application app =
            new Application();

        Window window =
            new Window(
                "JNative Counter",
                400,
                240
            );

        VBox root =
            new VBox();

        Label label =
            new Label(
                "Count: 0"
            );

        Button button =
            new Button(
                "Increment"
            );

        int[] count =
            new int[1];
        count[0] = 0;

        button.onClick(() -> {

            count[0] = count[0] + 1;

            label.setText(
                "Count: " +
                count[0]
            );
        });

        root.add(label);
        root.add(button);

        window.setContent(root);

        window.show();

        app.run();
    }
}
