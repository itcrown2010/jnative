/**
 * Minimal repro: Container children[] was fixed at 64, so a 9×9 GridPane
 * (81 cells) threw UiException "container child capacity exceeded".
 *
 * Expected after fix: Container grows via ensureChildCapacity.
 * @author 张晓达
 */
public class UiContainerCapacityRepro {
    public static void main(String[] args) {
        Application app = new Application();
        Window w = new Window("cap", 400, 400);
        GridPane g = new GridPane();
        int i = 0;
        while (i < 81) {
            g.addAt(new Button("" + i), i % 9, i / 9);
            i = i + 1;
        }
        w.setContent(g);
        w.show();
        System.out.println("ok-81");
        app.run();
    }
}
