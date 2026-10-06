import jnative.ui.*;
import jnative.graphics.*;

public class CodeViewer {
    public static void main(String[] args) {
        Application app = new Application();
        Window window = new Window("JNative Code Viewer", 900, 600);
        Canvas canvas = new Canvas();
        String[] lines = new String[6];
        lines[0] = "public class Hello {";
        lines[1] = "  // greeting";
        lines[2] = "  String msg = \"hi\";";
        lines[3] = "  int n = 42;";
        lines[4] = "  void run() {";
        lines[5] = "  }";
        int[] scroll = new int[1];
        scroll[0] = 0;
        int[] caretLine = new int[1];
        caretLine[0] = 2;
        int[] selFrom = new int[1];
        int[] selTo = new int[1];
        selFrom[0] = 2;
        selTo[0] = 3;

        canvas.onPaint(g -> {
            g.setColor(new Color(30, 30, 30));
            g.fillRect(0, 0, 900, 600);
            Font mono = Font.monospace(16);
            g.setFont(mono);
            FontMetrics fm = g.getFontMetrics(mono);
            int lh = fm.getLineHeight();
            if (lh < 1) {
                lh = 18;
            }
            g.setColor(new Color(50, 50, 80));
            g.fillRect(0, 40 + (selFrom[0] - scroll[0]) * lh, 900, (selTo[0] - selFrom[0] + 1) * lh);
            int i = 0;
            while (i < 6) {
                int y = 40 + (i - scroll[0]) * lh;
                g.setColor(new Color(120, 120, 120));
                g.drawText("" + (i + 1), 8, y);
                String line = lines[i];
                Color c = new Color(220, 220, 220);
                if (i == 1) {
                    c = new Color(120, 180, 120);
                } else if (i == 0 || i == 4) {
                    c = new Color(120, 160, 255);
                } else if (i == 2) {
                    c = new Color(220, 180, 120);
                } else if (i == 3) {
                    c = new Color(200, 140, 220);
                }
                g.setColor(c);
                g.drawText(line, 48, y);
                i = i + 1;
            }
            g.setColor(Color.WHITE);
            int cy = 40 + (caretLine[0] - scroll[0]) * lh;
            g.fillRect(48, cy, 2, lh);
        });

        window.setContent(canvas);
        window.show();
        canvas.repaint();
        app.run();
    }
}
