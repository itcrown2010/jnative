import jnative.ui.*;
import jnative.graphics.*;
import jnative.editor.*;

/**
 * Find / replace bar docked above the code editor. Typing highlights every match in the editor and jumps to the
 * nearest one; Enter goes to the next match, the arrow buttons step both ways, Esc closes the bar.
 *
 * @author 张晓达
 */
public class IdeFindBar {
    IdeApp app;
    Canvas bg;
    TextField find;
    TextField repl;
    CheckBox cCase;
    CheckBox cWord;
    Button bPrev;
    Button bNext;
    Button bRepl;
    Button bAll;
    Button bClose;
    Label count;
    boolean shown = false;
    boolean replaceMode = false;
    boolean quiet = false;
    String lastQuery = "";

    IdeFindBar(IdeApp a) {
        app = a;
        Panel host = app.host;
        bg = new Canvas();
        bg.setHiDpi(true);
        bg.onPaint(g -> paintBg(g));
        host.add(bg);
        find = new TextField("");
        find.setCueBanner("查找");
        host.add(find);
        repl = new TextField("");
        repl.setCueBanner("替换为");
        host.add(repl);
        cCase = new CheckBox("区分大小写");
        host.add(cCase);
        cWord = new CheckBox("整词");
        host.add(cWord);
        bPrev = new Button("↑");
        host.add(bPrev);
        bNext = new Button("↓");
        host.add(bNext);
        bRepl = new Button("替换");
        host.add(bRepl);
        bAll = new Button("全部替换");
        host.add(bAll);
        bClose = new Button("✕");
        host.add(bClose);
        count = new Label("");
        host.add(count);
        count.setForegroundRgba(Component.rgba(107, 114, 128, 255));

        find.onChange(e -> queryChanged());
        find.onAction(e -> step(1));
        repl.onAction(e -> replaceOne());
        cCase.onChange(e -> queryChanged());
        cWord.onChange(e -> queryChanged());
        bPrev.onClick(() -> step(-1));
        bNext.onClick(() -> step(1));
        bRepl.onClick(() -> replaceOne());
        bAll.onClick(() -> replaceAll());
        bClose.onClick(() -> close());
        setShown(false);
    }

    int height() {
        if (!shown) {
            return 0;
        }
        return replaceMode ? 72 : 40;
    }

    void setShown(boolean v) {
        shown = v;
        IdeApp.vis(bg, v);
        IdeApp.vis(find, v);
        IdeApp.vis(cCase, v);
        IdeApp.vis(cWord, v);
        IdeApp.vis(bPrev, v);
        IdeApp.vis(bNext, v);
        IdeApp.vis(bClose, v);
        IdeApp.vis(count, v);
        IdeApp.vis(repl, v && replaceMode);
        IdeApp.vis(bRepl, v && replaceMode);
        IdeApp.vis(bAll, v && replaceMode);
    }

    void paintBg(Graphics g) {
        Ide.fill(g, Ide.PANEL_ALT, 0, 0, bg.width, bg.height);
        Ide.line(g, Ide.BORDER, 0, bg.height - 0.5, bg.width, bg.height - 0.5);
    }

    /** Lays the bar out across the width of the editor area. */
    void layoutBar(int w) {
        if (!shown) {
            return;
        }
        int h = height();
        bg.setBounds(0, 0, w, h);
        int fw = Math.max(140, Math.min(300, w - 440));
        find.setBounds(12, 7, fw, 26);
        int x = 12 + fw + 6;
        bPrev.setBounds(x, 7, 30, 26);
        bNext.setBounds(x + 32, 7, 30, 26);
        cCase.setBounds(x + 70, 7, 112, 26);
        cWord.setBounds(x + 184, 7, 66, 26);
        count.setBounds(x + 254, 11, 110, 20);
        bClose.setBounds(w - 40, 7, 30, 26);
        if (replaceMode) {
            repl.setBounds(12, 38, fw, 26);
            bRepl.setBounds(x, 38, 62, 26);
            bAll.setBounds(x + 66, 38, 84, 26);
        }
    }

    CodeEditor editor() {
        IdeDoc d = app.activeDoc();
        if (d == null || d.kind != 0) {
            return null;
        }
        return d.editor;
    }

    void open(boolean withReplace) {
        CodeEditor e = editor();
        if (e == null) {
            return;
        }
        replaceMode = withReplace;
        if (!selectionIsEmpty(e)) {
            TextRange r = e.selection.toRange();
            String s = e.document.substring(r.start, r.end);
            if (s.indexOf("\n") < 0 && s.length() < 200) {
                quiet = true;
                find.setText(s);
                quiet = false;
            }
        } else if (find.getText().length() == 0 && lastQuery.length() > 0) {
            quiet = true;
            find.setText(lastQuery);
            quiet = false;
        }
        setShown(true);
        app.layoutDocs();
        find.requestFocus();
        find.selectAll();
        apply(e, true);
    }

    boolean selectionIsEmpty(CodeEditor e) {
        return e.selection.isEmpty();
    }

    void close() {
        if (!shown) {
            return;
        }
        CodeEditor e = editor();
        setShown(false);
        app.layoutDocs();
        int i = 0;
        while (i < app.tabs.docs.size()) {
            IdeDoc d = app.tabs.docs.get(i);
            if (d.kind == 0) {
                d.editor.clearFind();
            }
            i = i + 1;
        }
        if (e != null) {
            e.requestFocus();
        }
    }

    /** Re-targets the highlight when another document becomes active. */
    void sync() {
        int i = 0;
        while (i < app.tabs.docs.size()) {
            IdeDoc d = app.tabs.docs.get(i);
            if (d.kind == 0) {
                d.editor.clearFind();
            }
            i = i + 1;
        }
        CodeEditor e = editor();
        if (shown && e != null) {
            apply(e, false);
        }
    }

    void queryChanged() {
        if (quiet) {
            return;
        }
        CodeEditor e = editor();
        if (e != null) {
            apply(e, true);
        }
    }

    void apply(CodeEditor e, boolean jump) {
        String q = find.getText();
        lastQuery = q;
        e.setFind(q, cCase.isSelected() ? 1 : 0, cWord.isSelected() ? 1 : 0);
        if (jump && q.length() > 0) {
            e.findStep(1, true);
        }
        updateCount(e);
        app.lastFind = q;
    }

    void updateCount(CodeEditor e) {
        if (find.getText().length() == 0) {
            count.setText("");
            return;
        }
        int[] st = e.findStats();
        if (st[1] == 0) {
            count.setText("无结果");
            count.setForegroundRgba(Component.rgba(209, 52, 56, 255));
        } else {
            count.setText((st[0] > 0 ? st[0] : 0) + " / " + st[1]);
            count.setForegroundRgba(Component.rgba(107, 114, 128, 255));
        }
    }

    void step(int dir) {
        CodeEditor e = editor();
        if (e == null) {
            return;
        }
        if (e.findQuery.length() == 0) {
            apply(e, false);
        }
        e.findStep(dir, false);
        updateCount(e);
    }

    void replaceOne() {
        CodeEditor e = editor();
        if (e == null || !replaceMode) {
            return;
        }
        if (e.findQuery.length() == 0) {
            apply(e, false);
        }
        e.replaceCurrent(repl.getText());
        updateCount(e);
    }

    void replaceAll() {
        CodeEditor e = editor();
        if (e == null || !replaceMode) {
            return;
        }
        if (e.findQuery.length() == 0) {
            apply(e, false);
        }
        int n = e.replaceAllMatches(repl.getText());
        updateCount(e);
        app.setStatus(n > 0 ? "已替换 " + n + " 处" : "没有可替换的内容");
    }
}
