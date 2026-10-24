import jnative.ui.*;
import jnative.graphics.*;

/**
 * Live preview window: the form built with the very same builder as the design canvas (but with
 * enabled / visible honoured), refreshed on every edit.
 *
 * @author 张晓达
 */
public class DsPreview {
    Window window;
    DsDoc doc;
    Component content;
    int forcedW = 0;
    int forcedH = 0;
    Runnable onClosed;

    boolean isOpen() {
        return window != null;
    }

    void open(DsDoc d, int w, int h) {
        doc = d;
        forcedW = w;
        forcedH = h;
        if (window != null) {
            refresh();
            window.activate();
            return;
        }
        window = new Window(title(), width(), height());
        window.onClose(() -> {
            window = null;
            content = null;
            if (onClosed != null) {
                onClosed.run();
            }
        });
        refresh();
        window.show();
    }

    String title() {
        return "预览 - " + doc.root.get("title");
    }

    int width() {
        return forcedW > 0 ? forcedW : doc.root.getInt("width");
    }

    int height() {
        return forcedH > 0 ? forcedH : doc.root.getInt("height");
    }

    void setDocument(DsDoc d) {
        doc = d;
        refresh();
    }

    void setDevice(int w, int h) {
        forcedW = w;
        forcedH = h;
        if (window != null) {
            window.setSize(width(), height());
            refresh();
        }
    }

    void refresh() {
        if (window == null || doc == null) {
            return;
        }
        window.setTitle(title());
        DsBuild b = new DsBuild(false);
        Component root = b.build(doc);
        if (root == null) {
            return;
        }
        Component old = content;
        content = root;
        window.setContent(root);
        if (old != null) {
            old.parent = null;
            old.dispose();
        }
    }

    void close() {
        if (window != null) {
            Window w = window;
            window = null;
            w.close();
        }
    }
}
