import jnative.ui.*;
import jnative.graphics.*;

public class ChartDemo {
    public static void main(String[] args) {
        Application app = new Application();
        Window window = new Window("JNative Chart", 800, 500);
        Canvas canvas = new Canvas();
        canvas.onPaint(g -> {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 800, 500);
            g.setColor(Color.GRAY);
            int gy = 0;
            while (gy <= 400) {
                g.drawLine(60, 40 + gy, 760, 40 + gy);
                gy = gy + 40;
            }
            g.setColor(Color.BLACK);
            g.setStroke(new Stroke(2.0));
            g.drawLine(60, 40, 60, 440);
            g.drawLine(60, 440, 760, 440);
            g.setFont(new Font("Arial", 14));
            g.drawText("Sales", 20, 20);
            int[] values = new int[8];
            values[0] = 40;
            values[1] = 90;
            values[2] = 70;
            values[3] = 120;
            values[4] = 160;
            values[5] = 110;
            values[6] = 180;
            values[7] = 150;
            g.setColor(Color.BLUE);
            g.setStroke(new Stroke(2.5));
            int i = 1;
            while (i < 8) {
                double x0 = 60 + (i - 1) * 90;
                double y0 = 440 - values[i - 1];
                double x1 = 60 + i * 90;
                double y1 = 440 - values[i];
                g.drawLine(x0, y0, x1, y1);
                g.fillEllipse(x1 - 4, y1 - 4, 8, 8);
                i = i + 1;
            }
            g.fillEllipse(60 - 4, 440 - values[0] - 4, 8, 8);
            g.setColor(Color.BLACK);
            g.drawText("JNative Chart Demo", 300, 470);
        });
        window.setContent(canvas);
        window.show();
        canvas.repaint();
        app.run();
    }
}
