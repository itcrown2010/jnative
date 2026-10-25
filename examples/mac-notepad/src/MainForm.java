import jnative.ui.*;
import jnative.graphics.*;
import jnative.io.*;

/**
 * macOS 记事本的业务逻辑。界面（编辑区 / 查找替换栏 / 状态栏）由 MainForm.jnui 在 JNative Studio 设计器里设计，
 * 生成到 MainFormBase；这里重写设计器绑定的事件，并用代码补上菜单栏、文件读写、查找替换和设置。
 *
 * 编辑区是原生 NSTextView（输入法、拼写、撤销栈都由系统提供）；它的选区下标是 UTF-16，而 JNative 字符串按
 * 码点计数，所以凡是在文本和选区之间换算的地方都经过 toNative / fromNative。
 */
public class MainForm extends MainFormBase {
    static String APP = "记事本";
    
    /** 当前文件路径，空串表示“未命名”。 */
    String path = "";
    boolean dirty = false;
    /** 打开文件时检测到的换行风格，保存时原样写回。 */
    boolean crlf = false;
    boolean bom = false;
    /** 程序正在替换整篇文本时忽略编辑器的变更事件。 */
    boolean loading = false;

    boolean wrap = true;
    boolean mono = false;
    int fontPt = 15;

    MenuItem miWrap;
    MenuItem miMono;
    UiTimer ticker;
    int lastSelA = -1;
    int lastSelB = -1;
    int lastLen = -1;

    MainForm() {
        buildUi();
        loadSettings();
        window.setMinSize(520, 320);
        applyFont();
        txtEditor.setWordWrap(wrap);
        buildMenus();
        window.onClose(() -> {
            if (!confirmDiscard()) {
                window.vetoClose();
            }
        });
        Application.setQuitGuard(() -> confirmDiscard());
        txtFind.onAction(e -> stepFind(1, false));
        txtReplace.onAction(e -> onReplace());
        window.onKeyDown(e -> onWindowKey(e));
        ticker = new UiTimer(120, () -> tick());
        updateTitle();
        refreshStatus(true);
        txtEditor.requestFocus();
    }

    // ------------------------------------------------------------ 设计器绑定的事件

    @Override
    void onEditorChange(UiEvent e) {
        if (loading) {
            return;
        }
        if (!dirty) {
            dirty = true;
            updateTitle();
        }
        refreshStatus(true);
    }

    @Override
    void onFindChange(UiEvent e) {
        stepFind(1, true);
    }

    @Override
    void onCaseToggle() {
        stepFind(1, true);
    }

    @Override
    void onFindNext() {
        stepFind(1, false);
    }

    @Override
    void onFindPrev() {
        stepFind(-1, false);
    }

    @Override
    void onReplace() {
        String q = txtFind.getText();
        if (q.length() == 0) {
            return;
        }
        String text = txtEditor.getText();
        int a = fromNative(text, txtEditor.getSelectionStart());
        int b = fromNative(text, txtEditor.getSelectionEnd());
        if (b > a && sameText(text.substring(a, b), q)) {
            txtEditor.replaceSelection(txtReplace.getText());
        }
        stepFind(1, false);
    }

    @Override
    void onReplaceAll() {
        String q = txtFind.getText();
        if (q.length() == 0) {
            return;
        }
        String text = txtEditor.getText();
        String hay = haystack(text);
        String needle = caseSensitive() ? q : q.toLowerCase();
        if (needle.length() == 0 || hay.indexOf(needle) < 0) {
            lblMatches.setText("无结果");
            return;
        }
        String rep = txtReplace.getText();
        StringBuilder out = new StringBuilder();
        int pos = 0;
        int count = 0;
        while (true) {
            int at = hay.indexOf(needle, pos);
            if (at < 0) {
                break;
            }
            out.append(text.substring(pos, at));
            out.append(rep);
            pos = at + needle.length();
            count = count + 1;
        }
        out.append(text.substring(pos));
        txtEditor.setSelection(0, -1);
        txtEditor.replaceSelection(out.toString());
        txtEditor.setSelection(0, 0);
        lblMatches.setText("已替换 " + count + " 处");
        refreshStatus(true);
    }

    @Override
    void onFindClose() {
        closeFindBar();
    }

    // ------------------------------------------------------------ 菜单

    MenuItem item(Menu m, String text, int key, int mods, Runnable r) {
        MenuItem mi = new MenuItem(text, r);
        if (key > 0) {
            KeyCombination kc = KeyCombination.of(key).withPrimary();
            if (mods == 1) {
                kc = kc.withShift();
            } else if (mods == 2) {
                kc = kc.withAlt();
            }
            mi.setShortcut(kc);
        }
        m.add(mi);
        return mi;
    }

    void buildMenus() {
        MenuBar bar = new MenuBar();
        Menu file = new Menu("文件");
        item(file, "新建", 78, 0, () -> doNew());
        item(file, "打开…", 79, 0, () -> doOpen());
        file.addSeparator();
        item(file, "存储", 83, 0, () -> doSave());
        item(file, "存储为…", 83, 1, () -> doSaveAs());
        file.addSeparator();
        item(file, "在 Finder 中显示", 0, 0, () -> revealInFinder());
        bar.add(file);

        Menu edit = new Menu("编辑");
        item(edit, "撤销", 90, 0, () -> txtEditor.undo());
        item(edit, "重做", 90, 1, () -> txtEditor.redo());
        edit.addSeparator();
        item(edit, "剪切", 88, 0, () -> txtEditor.cut());
        item(edit, "拷贝", 67, 0, () -> txtEditor.copy());
        item(edit, "粘贴", 86, 0, () -> txtEditor.paste());
        item(edit, "删除", 0, 0, () -> txtEditor.deleteSelection());
        item(edit, "全选", 65, 0, () -> txtEditor.selectAll());
        edit.addSeparator();
        item(edit, "查找…", 70, 0, () -> openFindBar(false));
        item(edit, "查找下一个", 71, 0, () -> stepFind(1, false));
        item(edit, "查找上一个", 71, 1, () -> stepFind(-1, false));
        item(edit, "替换…", 70, 2, () -> openFindBar(true));
        item(edit, "转到行…", 76, 0, () -> gotoLineDialog());
        edit.addSeparator();
        item(edit, "转换为大写", 0, 0, () -> transformSelection(true));
        item(edit, "转换为小写", 0, 0, () -> transformSelection(false));
        item(edit, "排序所选行", 0, 0, () -> sortSelectedLines());
        bar.add(edit);

        Menu fmt = new Menu("格式");
        miWrap = item(fmt, "自动换行", 0, 0, () -> toggleWrap());
        miWrap.setChecked(wrap);
        miMono = item(fmt, "使用等宽字体", 0, 0, () -> toggleMono());
        miMono.setChecked(mono);
        fmt.addSeparator();
        item(fmt, "放大", 187, 0, () -> zoom(1));
        item(fmt, "缩小", 189, 0, () -> zoom(-1));
        item(fmt, "实际大小", 48, 0, () -> zoom(0));
        bar.add(fmt);

        Menu help = new Menu("帮助");
        help.add(new MenuItem("关于记事本", () -> showAbout()));
        bar.add(help);
        window.setMenuBar(bar);
    }

    // ------------------------------------------------------------ 文件

    /** 有未保存的修改时询问；返回 false 表示用户取消。 */
    boolean confirmDiscard() {
        if (!dirty) {
            return true;
        }
        int r = Dialogs.askSave(window, "是否保存对“" + displayName() + "”的更改？");
        if (r == 0) {
            return false;
        }
        if (r == 1) {
            return doSave();
        }
        return true;
    }

    String displayName() {
        if (path.length() == 0) {
            return "未命名";
        }
        return baseName(path);
    }

    static String baseName(String p) {
        int i = p.lastIndexOf("/");
        if (i < 0) {
            return p;
        }
        return p.substring(i + 1);
    }

    String titleText = "";

    void updateTitle() {
        titleText = displayName() + (dirty ? " — 已编辑" : "");
        window.setTitle(titleText);
    }

    /** 用 text 替换整篇文档（清空撤销栈、复位修改标记）。 */
    void loadText(String text, String newPath) {
        loading = true;
        txtEditor.setTextReset(text);
        txtEditor.setSelection(0, 0);
        loading = false;
        path = newPath;
        dirty = false;
        updateTitle();
        refreshStatus(true);
        txtEditor.requestFocus();
    }

    void doNew() {
        if (!confirmDiscard()) {
            return;
        }
        crlf = false;
        bom = false;
        loadText("", "");
    }

    void doOpen() {
        if (!confirmDiscard()) {
            return;
        }
        String p = FileDialogEx.show(window, 0, "", "", "", 0);
        if (p == null || p.length() == 0) {
            return;
        }
        openPath(p);
    }

    /** 读取 UTF-8 文本文件：去掉 BOM、记住 CRLF 并统一成 LF 显示。 */
    void openPath(String p) {
        if (!Files.exists(FsPath.of(p))) {
            Dialogs.error(window, "文件不存在：" + p);
            return;
        }
        String text = Files.readString(FsPath.of(p));
        if (text == null) {
            Dialogs.error(window, "无法读取文件：" + p);
            return;
        }
        if (text.indexOf("" + (char) 0) >= 0) {
            if (!Dialogs.confirm(window, "“" + baseName(p) + "”看起来不是文本文件，仍要打开吗？")) {
                return;
            }
        }
        bom = text.length() > 0 && text.charAt(0) == 65279;
        if (bom) {
            text = text.substring(1);
        }
        crlf = text.indexOf("\r\n") >= 0;
        text = normalizeNewlines(text);
        loadText(text, p);
        setFormatLabel();
    }

    static String normalizeNewlines(String s) {
        if (s.indexOf("\r") < 0) {
            return s;
        }
        return s.replace("\r\n", "\n").replace("\r", "\n");
    }

    boolean doSave() {
        if (path.length() == 0) {
            return doSaveAs();
        }
        return writeFile(path);
    }

    boolean doSaveAs() {
        String name = path.length() == 0 ? "未命名.txt" : baseName(path);
        String p = FileDialogEx.show(window, 1, "文本文档 (*.txt)|*.txt|所有文件 (*.*)|*.*", name, "", 0);
        if (p == null || p.length() == 0) {
            return false;
        }
        return writeFile(p);
    }

    boolean writeFile(String p) {
        String out = txtEditor.getText();
        if (crlf) {
            out = out.replace("\n", "\r\n");
        }
        if (bom) {
            out = "" + (char) 65279 + out;
        }
        Files.writeString(FsPath.of(p), out);
        if (!Files.exists(FsPath.of(p))) {
            Dialogs.error(window, "无法保存文件：" + p);
            return false;
        }
        path = p;
        dirty = false;
        updateTitle();
        setStatusNote("已存储 " + baseName(p));
        return true;
    }

    void revealInFinder() {
        if (path.length() == 0) {
            setStatusNote("文档还没有存储到磁盘");
            return;
        }
        Desktop.revealFile(path);
    }

    // ------------------------------------------------------------ 编辑辅助

    static int toNative(String s, int cp) {
        int extra = 0;
        int i = 0;
        while (i < cp && i < s.length()) {
            int c = s.charAt(i);
            if (c >= 65536) {
                extra = extra + 1;
            }
            i = i + 1;
        }
        return cp + extra;
    }

    static int fromNative(String s, int u) {
        int units = 0;
        int i = 0;
        while (units < u && i < s.length()) {
            int c = s.charAt(i);
            units = units + (c >= 65536 ? 2 : 1);
            i = i + 1;
        }
        return i;
    }

    void selectCp(String text, int a, int b) {
        txtEditor.setSelection(toNative(text, a), toNative(text, b));
        txtEditor.scrollCaret();
    }

    void transformSelection(boolean upper) {
        String text = txtEditor.getText();
        int a = fromNative(text, txtEditor.getSelectionStart());
        int b = fromNative(text, txtEditor.getSelectionEnd());
        if (b <= a) {
            return;
        }
        String sel = text.substring(a, b);
        txtEditor.replaceSelection(upper ? sel.toUpperCase() : sel.toLowerCase());
    }

    /** 把选区扩展到整行后按字典序排序（没有选区时对整篇文档排序）。 */
    void sortSelectedLines() {
        String text = txtEditor.getText();
        int a = fromNative(text, txtEditor.getSelectionStart());
        int b = fromNative(text, txtEditor.getSelectionEnd());
        if (b <= a) {
            a = 0;
            b = text.length();
        }
        int start = a;
        while (start > 0 && text.charAt(start - 1) != '\n') {
            start = start - 1;
        }
        int end = b;
        while (end < text.length() && text.charAt(end) != '\n') {
            end = end + 1;
        }
        String block = text.substring(start, end);
        ArrayList<String> lines = new ArrayList<String>();
        int pos = 0;
        while (pos <= block.length()) {
            int eol = block.indexOf("\n", pos);
            if (eol < 0) {
                eol = block.length();
            }
            lines.add(block.substring(pos, eol));
            pos = eol + 1;
        }
        int i = 1;
        while (i < lines.size()) {
            String key = lines.get(i);
            int j = i - 1;
            while (j >= 0 && lines.get(j).compareTo(key) > 0) {
                lines.set(j + 1, lines.get(j));
                j = j - 1;
            }
            lines.set(j + 1, key);
            i = i + 1;
        }
        StringBuilder sb = new StringBuilder();
        i = 0;
        while (i < lines.size()) {
            if (i > 0) {
                sb.append("\n");
            }
            sb.append(lines.get(i));
            i = i + 1;
        }
        txtEditor.setSelection(toNative(text, start), toNative(text, end));
        txtEditor.replaceSelection(sb.toString());
    }

    void gotoLineDialog() {
        int total = txtEditor.lineCount();
        Window w = new Window("转到行", 340, 156);
        w.setDialogStyle(window);
        Panel p = new Panel();
        Label l = new Label("行号（1 - " + total + "）：");
        p.add(l);
        l.setBounds(20, 18, 300, 22);
        TextField tf = new TextField("");
        p.add(tf);
        tf.setBounds(20, 46, 300, 28);
        Button ok = new Button("转到");
        Button cancel = new Button("取消");
        p.add(ok);
        p.add(cancel);
        ok.setBounds(150, 100, 80, 32);
        cancel.setBounds(240, 100, 80, 32);
        ok.onClick(() -> {
            int n = parseInt(tf.getText().trim(), -1);
            w.close();
            if (n >= 1) {
                gotoLine(Math.min(n, total));
            }
        });
        cancel.onClick(() -> w.close());
        w.setDefaultButton(ok);
        w.setCancelButton(cancel);
        w.setContent(p);
        w.centerOnOwner();
        w.show();
        tf.requestFocus();
    }

    void gotoLine(int line) {
        int at = txtEditor.lineIndex(line - 1);
        if (at < 0) {
            return;
        }
        txtEditor.setSelection(at, at);
        txtEditor.scrollCaret();
        txtEditor.requestFocus();
    }

    static int parseInt(String s, int fallback) {
        if (s.length() == 0 || s.length() > 9) {
            return fallback;
        }
        int v = 0;
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                return fallback;
            }
            v = v * 10 + (c - 48);
            i = i + 1;
        }
        return v;
    }

    // ------------------------------------------------------------ 查找 / 替换

    boolean caseSensitive() {
        return chkCase.isSelected();
    }

    /** 大小写不敏感时统一转小写；若转换改变了长度（极少数字符）就退回原文，保证下标不错位。 */
    String haystack(String text) {
        if (caseSensitive()) {
            return text;
        }
        String low = text.toLowerCase();
        return low.length() == text.length() ? low : text;
    }

    boolean sameText(String a, String b) {
        if (caseSensitive()) {
            return a.equals(b);
        }
        return a.toLowerCase().equals(b.toLowerCase());
    }

    void openFindBar(boolean withReplace) {
        findBar.setVisible(true);
        txtReplace.setVisible(withReplace);
        btnReplace.setVisible(withReplace);
        btnReplaceAll.setVisible(withReplace);
        String text = txtEditor.getText();
        int a = fromNative(text, txtEditor.getSelectionStart());
        int b = fromNative(text, txtEditor.getSelectionEnd());
        if (b > a && b - a < 120 && text.substring(a, b).indexOf("\n") < 0) {
            txtFind.setText(text.substring(a, b));
        }
        window.content.layout();
        txtFind.requestFocus();
        stepFind(1, true);
    }

    void closeFindBar() {
        findBar.setVisible(false);
        window.content.layout();
        txtEditor.requestFocus();
    }

    void onWindowKey(KeyEvent e) {
        if (e.keyCode == KeyCode.ESCAPE && findBar.visible != 0) {
            closeFindBar();
        }
    }

    /**
     * 查找并选中下一个 / 上一个匹配，到头后绕回。fromStart 为 true（正在输入查找词）时从当前选区的起点开始，
     * 这样输入过程中匹配不会跳走。
     */
    void stepFind(int dir, boolean fromStart) {
        String q = txtFind.getText();
        if (q.length() == 0) {
            lblMatches.setText("");
            return;
        }
        String text = txtEditor.getText();
        String hay = haystack(text);
        String needle = caseSensitive() ? q : q.toLowerCase();
        int selA = fromNative(text, txtEditor.getSelectionStart());
        int selB = fromNative(text, txtEditor.getSelectionEnd());
        int at = -1;
        if (dir > 0) {
            at = hay.indexOf(needle, fromStart ? selA : selB);
            if (at < 0) {
                at = hay.indexOf(needle, 0);
            }
        } else {
            at = hay.substring(0, selA).lastIndexOf(needle);
            if (at < 0) {
                at = hay.lastIndexOf(needle);
            }
        }
        if (at < 0) {
            lblMatches.setText("无结果");
            return;
        }
        selectCp(text, at, at + needle.length());
        int total = 0;
        int index = 0;
        int pos = 0;
        while (total < 99999) {
            int m = hay.indexOf(needle, pos);
            if (m < 0) {
                break;
            }
            total = total + 1;
            if (m <= at) {
                index = total;
            }
            pos = m + Math.max(1, needle.length());
        }
        lblMatches.setText(index + " / " + total);
    }

    // ------------------------------------------------------------ 视图与设置

    void applyFont() {
        String family = mono ? "Menlo" : "PingFang SC";
        txtEditor.setEditorFont(family, fontPt * 8, 400, 0);
    }

    void toggleWrap() {
        wrap = !wrap;
        txtEditor.setWordWrap(wrap);
        miWrap.setChecked(wrap);
        saveSettings();
    }

    void toggleMono() {
        mono = !mono;
        applyFont();
        miMono.setChecked(mono);
        saveSettings();
    }

    void zoom(int delta) {
        if (delta == 0) {
            fontPt = 15;
        } else {
            fontPt = Math.max(9, Math.min(60, fontPt + delta));
        }
        applyFont();
        saveSettings();
        setStatusNote("字号 " + fontPt);
    }

    FsPath settingsFile() {
        FsPath dir = Files.configDir("jnative-notepad");
        Files.createDirectories(dir);
        return FsPath.of(dir.toString() + "/settings.txt");
    }

    void loadSettings() {
        FsPath f = settingsFile();
        if (!Files.exists(f)) {
            return;
        }
        String text = Files.readString(f);
        int pos = 0;
        while (pos < text.length()) {
            int eol = text.indexOf("\n", pos);
            if (eol < 0) {
                eol = text.length();
            }
            String ln = text.substring(pos, eol);
            pos = eol + 1;
            int eq = ln.indexOf("=");
            if (eq <= 0) {
                continue;
            }
            String k = ln.substring(0, eq);
            String v = ln.substring(eq + 1);
            if (k.equals("fontPt")) {
                fontPt = Math.max(9, Math.min(60, parseInt(v, 15)));
            } else if (k.equals("wrap")) {
                wrap = v.equals("1");
            } else if (k.equals("mono")) {
                mono = v.equals("1");
            }
        }
    }

    void saveSettings() {
        Files.writeString(settingsFile(),
                "fontPt=" + fontPt + "\nwrap=" + (wrap ? "1" : "0") + "\nmono=" + (mono ? "1" : "0") + "\n");
    }

    void showAbout() {
        Dialogs.info(window, "记事本 1.0\n\n用 JNative Studio 设计界面、JNative 编译为原生 macOS 程序。\n"
                + "编辑区使用系统文本视图：支持中文输入法、拼写检查、撤销 / 重做。");
    }

    // ------------------------------------------------------------ 状态栏

    /** 临时提示（如“已存储”）显示在选区位置，约 3 秒后恢复。 */
    int noteTicks = 0;

    void setStatusNote(String s) {
        lblSelection.setText(s);
        noteTicks = 25;
    }

    void setFormatLabel() {
        lblFormat.setText("UTF-8" + (bom ? " (BOM)" : "") + " · " + (crlf ? "CRLF" : "LF"));
    }

    /** 轮询光标位置：原生文本视图没有“选区变化”事件。 */
    void tick() {
        if (noteTicks > 0) {
            noteTicks = noteTicks - 1;
            if (noteTicks == 0) {
                lastSelA = -2;
            }
        }
        refreshStatus(false);
    }

    void refreshStatus(boolean force) {
        int a = txtEditor.getSelectionStart();
        int b = txtEditor.getSelectionEnd();
        int len = txtEditor.textLength();
        if (!force && a == lastSelA && b == lastSelB && len == lastLen) {
            return;
        }
        boolean lenChanged = len != lastLen;
        lastSelA = a;
        lastSelB = b;
        lastLen = len;
        int line = txtEditor.lineFromChar(a);
        int col = a - txtEditor.lineIndex(line);
        lblPosition.setText("行 " + (line + 1) + "，列 " + (col + 1));
        if (noteTicks == 0) {
            if (b > a) {
                lblSelection.setText("已选择 " + (b - a) + " 字符");
            } else {
                lblSelection.setText("");
            }
        }
        if (lenChanged || force) {
            lblStats.setText(len + " 字符 · " + txtEditor.lineCount() + " 行");
        }
        setFormatLabel();
    }
}
