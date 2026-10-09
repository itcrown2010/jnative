import jnative.ui.*;
import jnative.graphics.*;
import jnative.collections.*;

/**
 * @author 张晓达
 */
public class PaintApp {
    public static void main(String[] args) {
        Application app = new Application();
        Window window = new Window("JNative Paint", 1000, 700);
        Canvas canvas = new Canvas();
        ArrayList<Point> points = new ArrayList<Point>();

        canvas.onMouseDown(event -> {
            points.add(new Point(event.getX(), event.getY()));
            canvas.repaint();
        });

        canvas.onMouseDrag(event -> {
            points.add(new Point(event.getX(), event.getY()));
            canvas.repaint();
        });

        canvas.onPaint(g -> {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 1000, 700);
            g.setColor(Color.BLUE);
            g.setStroke(new Stroke(2.0));
            int i = 1;
            while (i < points.size()) {
                Point a = points.get(i - 1);
                Point b = points.get(i);
                g.drawLine(a.x, a.y, b.x, b.y);
                i = i + 1;
            }
        });

        window.setContent(canvas);
        window.show();
        canvas.repaint();
        app.run();
    }
}
