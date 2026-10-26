import jnative.ui.*;
import jnative.graphics.*;
import jnative.editor.*;

/**
 * Floating information next to the caret, fed by the language service: the parameter list of the method being called
 * (the current parameter in bold, return type at the end) and quick documentation (⌘J) of the symbol under the caret.
 * Parameter info opens on "(" and ",", follows the caret while typing, and closes with Esc, ")" or when the caret
 * leaves the call.
 *
 * @author 张晓达
 */
public class IdeHint extends Canvas {
    IdeApp app;
    CodeEditor editor;
    boolean isOpen = false;
    /** 0 = parameter info, 1 = quick documentation. */
    int mode = 0;
    String sig = "";
    int hlA = -1;
    int hlB = -1;
    int sigCount = 1;
    int sigIndex = 0;
    ArrayList<String> doc = new ArrayList<String>();
    int anchor = 0;
    int reqId = 0;
    int seq = 0;
    boolean paramsActive = false;
    boolean quiet = false;
    boolean hovering = false;
    int hoverA = 0;
    int hoverB = 0;

    IdeHint(IdeApp a) {
        app = a;
        setHiDpi(true);
        onPaint(g -> paintHint(g));
        IdeApp.vis(this, false);
    }

    // ---------------------------------------------------------------- parameter info

    /** Asks the language service for the signature at the caret (call after "(" / "," or when the caret moved). */
    void triggerParams(CodeEditor ed, String path) {
        if (!app.ensureLsp()) {
            return;
        }
        editor = ed;
        paramsActive = true;
        seq = seq + 1;
        int my = seq;
        if (reqId > 0) {
            app.lsp.cancel(reqId);
        }
        app.lsp.sync(path, ed.getText());
        TextPosition p = ed.document.offsetToPosition(ed.caret.offset);
        anchor = ed.caret.offset;
        reqId = app.lsp.requestAt("textDocument/signatureHelp", path, p.line, p.column, "", r -> gotSignature(my, r));
    }

    void gotSignature(int my, JsonValue r) {
        if (my != seq) {
            return;
        }
        reqId = 0;
        if (r == null || !r.isObject()) {
            hide();
            return;
        }
        JsonObject o = r.asObject();
        JsonValue sv = o.get("signatures");
        if (sv == null || !sv.isArray() || sv.asArray().size() == 0) {
            hide();
            return;
        }
        JsonArray sigs = sv.asArray();
        sigCount = sigs.size();
        sigIndex = o.getInt("activeSignature", 0);
        if (sigIndex < 0 || sigIndex >= sigCount) {
            sigIndex = 0;
        }
        JsonObject s = sigs.get(sigIndex).asObject();
        sig = s.getString("label", "");
        int active = o.getInt("activeParameter", 0);
        hlA = -1;
        hlB = -1;
        JsonValue pv = s.get("parameters");
        if (pv != null && pv.isArray() && active >= 0 && active < pv.asArray().size()) {
            JsonValue pl = pv.asArray().get(active).asObject().get("label");
            if (pl != null && pl.isString()) {
                String lab = pl.asString();
                int open = sig.indexOf("(");
                int at = sig.indexOf(lab, open < 0 ? 0 : open);
                if (at >= 0) {
                    hlA = at;
                    hlB = at + lab.length();
                }
            }
        }
        mode = 0;
        show();
    }

    // ---------------------------------------------------------------- quick documentation

    void quickDoc(CodeEditor ed, String path) {
        if (!app.ensureLsp()) {
            app.setStatus("语言服务未启动（找不到 java 或编译器）");
            return;
        }
        editor = ed;
        paramsActive = false;
        seq = seq + 1;
        int my = seq;
        app.lsp.sync(path, ed.getText());
        TextPosition p = ed.document.offsetToPosition(ed.caret.offset);
        int col = p.column;
        String text = ed.getText();
        int off = ed.caret.offset;
        if ((off >= text.length() || !IdeSuggest.identChar(text.charAt(off))) && col > 0) {
            col = col - 1;
        }
        anchor = ed.caret.offset;
        hovering = false;
        app.setStatus("正在查询…");
        app.lsp.requestAt("textDocument/hover", path, p.line, col, "", r -> gotDoc(my, r));
    }

    /** Mouse-hover documentation for the identifier covering {@code off} (silent when there is nothing to show). */
    void hoverDoc(CodeEditor ed, String path, int off, int wordA, int wordB) {
        if (!app.ensureLsp()) {
            return;
        }
        editor = ed;
        paramsActive = false;
        seq = seq + 1;
        int my = seq;
        quiet = true;
        hoverA = wordA;
        hoverB = wordB;
        app.lsp.sync(path, ed.getText());
        TextPosition p = ed.document.offsetToPosition(off);
        anchor = wordA;
        app.lsp.requestAt("textDocument/hover", path, p.line, p.column, "", r -> gotDoc(my, r));
    }

    /** A debugger value tooltip ("name = value") anchored at the hovered word. */
    void hoverValue(CodeEditor ed, int wordA, int wordB, String text) {
        if (ed != app.hoverEd || wordA != app.hoverWA) {
            return;
        }
        editor = ed;
        paramsActive = false;
        seq = seq + 1;
        quiet = false;
        hoverA = wordA;
        hoverB = wordB;
        anchor = wordA;
        doc = new ArrayList<String>();
        doc.add(text.length() > 400 ? text.substring(0, 400) + "…" : text);
        sig = doc.get(0);
        hovering = true;
        mode = 1;
        show();
    }

    void gotDoc(int my, JsonValue r) {
        if (my != seq) {
            return;
        }
        if (quiet && (r == null || !r.isObject())) {
            quiet = false;
            hide();
            return;
        }
        if (r == null || !r.isObject()) {
            app.setStatus("光标处没有可显示的信息");
            hide();
            return;
        }
        JsonValue c = r.asObject().get("contents");
        String value = "";
        if (c != null && c.isString()) {
            value = c.asString();
        } else if (c != null && c.isObject()) {
            value = c.asObject().getString("value", "");
        } else if (c != null && c.isArray() && c.asArray().size() > 0) {
            JsonValue f = c.asArray().get(0);
            if (f.isString()) {
                value = f.asString();
            } else if (f.isObject()) {
                value = f.asObject().getString("value", "");
            }
        }
        doc = new ArrayList<String>();
        ArrayList<String> lines = DsGen.splitLines(value);
        int i = 0;
        while (i < lines.size()) {
            String ln = lines.get(i);
            if (!ln.startsWith("```") && ln.trim().length() > 0) {
                doc.add(ln);
            }
            i = i + 1;
        }
        if (doc.size() == 0) {
            if (!quiet) {
                app.setStatus("光标处没有可显示的信息");
            }
            quiet = false;
            hide();
            return;
        }
        if (!quiet) {
            app.setStatus("");
        }
        hovering = quiet;
        quiet = false;
        sig = doc.get(0);
        mode = 1;
        show();
    }

    // ---------------------------------------------------------------- show / hide / layout

    void show() {
        if (editor == null) {
            return;
        }
        isOpen = true;
        app.host.remove(this);
        app.host.add(this);
        place();
        IdeApp.vis(this, true);
        repaint();
    }

    void hide() {
        paramsActive = false;
        hovering = false;
        quiet = false;
        seq = seq + 1;
        if (!isOpen && visible == 0) {
            return;
        }
        isOpen = false;
        IdeApp.vis(this, false);
    }

    int estWidth(String s, double k) {
        return (int) (s.length() * k * IdeSettings.fontSize) + 24;
    }

    void place() {
        int lh = editor.lineHeightPx();
        int rowH = 22;
        int h;
        int w;
        if (mode == 0) {
            h = rowH + 8;
            w = estWidth(sig, 0.62);
        } else {
            h = doc.size() * rowH + 10;
            w = 0;
            int i = 0;
            while (i < doc.size()) {
                w = Math.max(w, estWidth(doc.get(i), i == 0 ? 0.62 : 0.95));
                i = i + 1;
            }
        }
        int hostW = app.host.width;
        int hostH = app.host.height;
        w = Math.min(w, Math.min(hostW - 16, 720));
        w = Math.max(w, 120);
        int ex = editor.offsetPixelX(anchor);
        int ey = editor.offsetPixelY(anchor) + app.findBarHeight();
        int x = ex - 8;
        if (x + w > hostW - 8) {
            x = hostW - w - 8;
        }
        if (x < 4) {
            x = 4;
        }
        int y;
        if (mode == 0) {
            y = ey - h - 4;
            if (y < 0) {
                y = ey + lh + 4;
            }
        } else {
            y = ey + lh + 4;
            if (y + h > hostH - 4) {
                y = ey - h - 4;
                if (y < 0) {
                    y = 0;
                }
            }
        }
        setBounds(x, y, w, h);
    }

    // ---------------------------------------------------------------- paint

    void paintHint(Graphics g) {
        g.setAntialias(true);
        Ide.fill(g, Ide.HINT_BG, 0, 0, width, height);
        Ide.rstroke(g, Ide.BORDER, 0.5, 0.5, width - 1, height - 1, 6);
        Font mono = Ide.mono();
        Font boldMono = new Font("native:mono", 12, FontStyle.BOLD);
        g.setClip(0, 0, width, height);
        if (mode == 0) {
            int x = 10;
            int rowH = 22;
            if (hlA >= 0) {
                String pre = sig.substring(0, hlA);
                String mid = sig.substring(hlA, hlB);
                String post = sig.substring(hlB);
                Ide.textRow(g, pre, x, 4, rowH, mono, Ide.SUBTLE);
                x = x + Ide.textWidth(g, pre, mono);
                Ide.textRow(g, mid, x, 4, rowH, boldMono, Ide.TEXT);
                x = x + Ide.textWidth(g, mid, boldMono);
                Ide.textRow(g, post, x, 4, rowH, mono, Ide.SUBTLE);
            } else {
                Ide.textRow(g, sig, x, 4, rowH, mono, Ide.SUBTLE);
            }
            if (sigCount > 1) {
                String more = "1/" + sigCount;
                int mw = Ide.textWidth(g, more, Ide.small());
                Ide.textRow(g, more, width - mw - 8, 4, rowH, Ide.small(), Ide.FAINT);
            }
        } else {
            int i = 0;
            while (i < doc.size()) {
                String ln = Ide.ellipsize(g, doc.get(i), i == 0 ? mono : Ide.ui(), width - 20);
                Ide.textRow(g, ln, 10, 5 + i * 22, 22, i == 0 ? boldMono : Ide.ui(), Ide.TEXT);
                i = i + 1;
            }
        }
        g.clearClip();
    }
}
