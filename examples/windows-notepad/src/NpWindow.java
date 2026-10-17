/**
 * One Notepad window: menu bar, edit control, status bar and the document state
 * (path, encoding, line ending, modified flag). Mirrors the in-box Windows Notepad.
 *
 * @author 张晓达
 */
class NpWindow {
    static String FILTER = "Text Documents (*.txt)|*.txt|All Files (*.*)|*.*";
    static String OPEN_ENC_LABELS = "Auto-Detect\tANSI\tUTF-16 LE\tUTF-16 BE\tUTF-8";

    // ---- user settings shared by all windows (Notepad keeps these per user) ----
    static String fontFamily = "Consolas";
    static int fontSize10 = 110;
    static int fontWeight = 400;
    static int fontItalic = 0;
    static int wrap = 0;
    static int statusBarOn = 1;

    // ---- last Find / Replace parameters ----
    static String findText = "";
    static String replaceText = "";
    static int findMatchCase = 0;
    static int findWrapAround = 1;
    static int findDown = 1;

    Window win;
    Panel root;
    TextArea edit;
    Panel statusPanel;
    Label stPos;
    Label stZoom;
    Label stEol;
    Label stEnc;
    Panel[] stDiv;
    Label timerHost;

    // document state
    String path;
    int enc;
    int eol;
    int dirty;
    int suppress;
    int zoom;
    String shownTitle;

    // dialogs
    NpFindDialog findDlg;
    NpFindDialog replaceDlg;
    NpGotoDialog gotoDlg;

    // menu items whose state follows the editor
    MenuItem miUndo;
    MenuItem miCut;
    MenuItem miCopy;
    MenuItem miDelete;
    MenuItem miPaste;
    MenuItem miBing;
    MenuItem miFind;
    MenuItem miFindNext;
    MenuItem miFindPrev;
    MenuItem miGoto;
    MenuItem miWrap;
    MenuItem miStatus;
    MenuItem miZoomIn;
    MenuItem miZoomOut;
    MenuItem miZoomReset;

    // cached editor state (poll tick)
    int cSelS;
    int cSelE;
    int cLen;
    int cUndo;
    int cPaste;
    String cPos;
    String cZoom;
    String cEol;
    String cEnc;

    NpWindow() {
        path = null;
        enc = NpCodec.ENC_UTF8;
        eol = NpCodec.EOL_CRLF;
        dirty = 0;
        suppress = 0;
        zoom = 100;
        shownTitle = "";
        cSelS = -1;
        cSelE = -1;
        cLen = -1;
        cUndo = -1;
        cPaste = -1;
        cPos = "";
        cZoom = "";
        cEol = "";
        cEnc = "";

        win = new Window("Untitled - Notepad", 1000, 640);
        win.setContentMinSize(260, 160);
        root = new Panel();
        root.setBackgroundRgba(NpUi.rgb(255, 255, 255));
        win.setContent(root);

        buildEdit();
        buildStatusBar();
        buildMenus();

        win.onKeyDown((ev) -> {
            onWindowKey(ev);
        });
        win.setResizeListener(() -> {
            layoutAll();
        });
        win.onClose(() -> {
            if (!confirmDiscard()) {
                win.vetoClose();
            }
        });

        timerHost = new Label(" ");
        root.add(timerHost);
        timerHost.setBounds(0, 0, 1, 1);
        timerHost.timerHook = () -> {
            tick();
        };

        applyFont();
        edit.setWordWrap(wrap != 0);
        applyMenuStates();
        layoutAll();
        updateTitle();
    }

    // ===================================================================== UI build

    void buildEdit() {
        edit = new TextArea();
        root.add(edit);
        edit.setFlat();
        edit.onChange((ev) -> {
            onTextChanged();
        });
    }

    void buildStatusBar() {
        int bg = NpUi.rgb(240, 240, 240);
        statusPanel = new Panel();
        statusPanel.setBackgroundRgba(bg);
        root.add(statusPanel);
        stPos = statusLabel("Ln 1, Col 1");
        stZoom = statusLabel("100%");
        stEol = statusLabel("Windows (CRLF)");
        stEnc = statusLabel("UTF-8");
        stDiv = new Panel[4];
        int i = 0;
        while (i < 4) {
            stDiv[i] = new Panel();
            stDiv[i].setBackgroundRgba(NpUi.rgb(160, 160, 160));
            statusPanel.add(stDiv[i]);
            i = i + 1;
        }
    }

    Label statusLabel(String t) {
        Label l = new Label(t);
        statusPanel.add(l);
        l.setVisual(NpUi.rgb(240, 240, 240), NpUi.rgb(0, 0, 0), -1, 9, 0);
        return l;
    }

    MenuItem item(Menu m, String text, int key, int mods, Runnable action) {
        MenuItem mi = new MenuItem(NpUi.mn(text));
        mi.setAction(action);
        if (key > 0) {
            KeyCombination kc = KeyCombination.of(key);
            if ((mods & 1) != 0) {
                kc = kc.withCtrl();
            }
            if ((mods & 2) != 0) {
                kc = kc.withShift();
            }
            mi.setShortcut(kc);
        }
        m.add(mi);
        return mi;
    }

    void buildMenus() {
        MenuBar bar = new MenuBar();

        Menu mFile = new Menu(NpUi.mn("&File"));
        item(mFile, "&New", 78, 1, () -> {
            doNew();
        });
        item(mFile, "New &Window", 78, 3, () -> {
            doNewWindow();
        });
        item(mFile, "&Open...", 79, 1, () -> {
            doOpen();
        });
        item(mFile, "&Save", 83, 1, () -> {
            doSave();
        });
        item(mFile, "Save &As...", 83, 3, () -> {
            doSaveAs();
        });
        mFile.addSeparator();
        item(mFile, "Page Set&up...", 0, 0, () -> {
            doPageSetup();
        });
        item(mFile, "&Print...", 80, 1, () -> {
            doPrint();
        });
        mFile.addSeparator();
        item(mFile, "E&xit", 0, 0, () -> {
            win.close();
        });
        bar.add(mFile);

        Menu mEdit = new Menu(NpUi.mn("&Edit"));
        miUndo = item(mEdit, "&Undo", 90, 1, () -> {
            edit.undo();
        });
        mEdit.addSeparator();
        miCut = item(mEdit, "Cu&t", 88, 1, () -> {
            edit.cut();
        });
        miCopy = item(mEdit, "&Copy", 67, 1, () -> {
            edit.copy();
        });
        miPaste = item(mEdit, "&Paste", 86, 1, () -> {
            edit.paste();
        });
        miDelete = new MenuItem(NpUi.mn("De&lete"));
        miDelete.shortcutLabelOnly = 1;
        miDelete.setShortcut(KeyCombination.of(46));
        miDelete.setAction(() -> {
            edit.deleteSelection();
        });
        mEdit.add(miDelete);
        mEdit.addSeparator();
        miBing = item(mEdit, "Search with &Bing...", 69, 1, () -> {
            searchWithBing();
        });
        miFind = item(mEdit, "&Find...", 70, 1, () -> {
            openFind();
        });
        miFindNext = item(mEdit, "Find &Next", 114, 0, () -> {
            findNextCommand(1);
        });
        miFindPrev = item(mEdit, "Find Previou&s", 114, 2, () -> {
            findNextCommand(0);
        });
        item(mEdit, "&Replace...", 72, 1, () -> {
            openReplace();
        });
        miGoto = item(mEdit, "&Go To...", 71, 1, () -> {
            openGoto();
        });
        mEdit.addSeparator();
        item(mEdit, "Select &All", 65, 1, () -> {
            edit.selectAll();
        });
        item(mEdit, "Time/&Date", 116, 0, () -> {
            edit.replaceSelection(UiSys.dateTimeString());
        });
        bar.add(mEdit);

        Menu mFormat = new Menu(NpUi.mn("F&ormat"));
        miWrap = item(mFormat, "&Word Wrap", 0, 0, () -> {
            toggleWrap();
        });
        item(mFormat, "&Font...", 0, 0, () -> {
            chooseFont();
        });
        bar.add(mFormat);

        Menu mView = new Menu(NpUi.mn("&View"));
        Menu mZoom = new Menu(NpUi.mn("&Zoom"));
        miZoomIn = item(mZoom, "Zoom &In", 61, 1, () -> {
            setZoom(zoom + 10);
        });
        miZoomOut = item(mZoom, "Zoom &Out", 45, 1, () -> {
            setZoom(zoom - 10);
        });
        miZoomReset = item(mZoom, "&Restore Default Zoom", 48, 1, () -> {
            setZoom(100);
        });
        mView.addMenu(mZoom);
        miStatus = item(mView, "&Status Bar", 0, 0, () -> {
            toggleStatusBar();
        });
        bar.add(mView);

        Menu mHelp = new Menu(NpUi.mn("&Help"));
        item(mHelp, "&View Help", 0, 0, () -> {
            Desktop.open("https://go.microsoft.com/fwlink/?LinkId=834783");
        });
        item(mHelp, "Send &Feedback", 0, 0, () -> {
            Desktop.open("https://aka.ms/winfeedback");
        });
        mHelp.addSeparator();
        item(mHelp, "&About Notepad", 0, 0, () -> {
            showAbout();
        });
        bar.add(mHelp);

        win.setMenuBar(bar);
    }

    // ===================================================================== layout

    void layoutAll() {
        int w = win.width;
        int h = win.height;
        int sh = 0;
        boolean showStatus = statusBarOn != 0 && wrap == 0;
        if (showStatus) {
            sh = 22;
        }
        NpUi.place(edit, 0, 0, w, h - sh);
        if (showStatus) {
            if (statusPanel.visible == 0) {
                statusPanel.setVisible(true);
            }
            NpUi.place(statusPanel, 0, h - sh, w, sh);
            int encW = 130;
            int eolW = 150;
            int zoomW = 60;
            int encX = w - encW;
            int eolX = encX - eolW;
            int zoomX = eolX - zoomW;
            NpUi.place(stPos, 8, 3, zoomX - 12, sh - 5);
            NpUi.place(stZoom, zoomX + 8, 3, zoomW - 10, sh - 5);
            NpUi.place(stEol, eolX + 8, 3, eolW - 10, sh - 5);
            NpUi.place(stEnc, encX + 8, 3, encW - 10, sh - 5);
            NpUi.place(stDiv[0], 0, 0, w, 1);
            NpUi.place(stDiv[1], zoomX, 3, 1, sh - 6);
            NpUi.place(stDiv[2], eolX, 3, 1, sh - 6);
            NpUi.place(stDiv[3], encX, 3, 1, sh - 6);
        } else if (statusPanel.visible != 0) {
            statusPanel.setVisible(false);
        }
    }

    // ===================================================================== state sync

    void updateTitle() {
        String name = "Untitled";
        if (path != null) {
            name = NpUi.baseName(path);
        }
        String t = name + " - Notepad";
        if (dirty != 0) {
            t = "*" + t;
        }
        if (!t.equals(shownTitle)) {
            shownTitle = t;
            win.setTitle(t);
        }
    }

    void onTextChanged() {
        if (suppress != 0) {
            return;
        }
        if (dirty == 0) {
            dirty = 1;
            updateTitle();
        }
    }

    void setEnabledIf(MenuItem mi, boolean on) {
        if ((mi.enabled != 0) != on) {
            mi.setEnabled(on);
        }
    }

    /** Enable/disable the commands that depend on the selection, clipboard and text. */
    void applyMenuStates() {
        int s = edit.getSelectionStart();
        int e = edit.getSelectionEnd();
        int len = edit.textLength();
        int undo = 0;
        if (edit.canUndo()) {
            undo = 1;
        }
        int paste = 0;
        if (UiSys.clipboardHasText()) {
            paste = 1;
        }
        boolean hasSel = s != e;
        boolean hasText = len > 0;
        setEnabledIf(miUndo, undo != 0);
        setEnabledIf(miCut, hasSel);
        setEnabledIf(miCopy, hasSel);
        setEnabledIf(miDelete, hasSel);
        setEnabledIf(miBing, hasSel);
        setEnabledIf(miPaste, paste != 0);
        setEnabledIf(miFind, hasText);
        setEnabledIf(miFindNext, hasText);
        setEnabledIf(miFindPrev, hasText);
        setEnabledIf(miGoto, wrap == 0);
        setEnabledIf(miStatus, wrap == 0);
        setEnabledIf(miZoomIn, zoom < 500);
        setEnabledIf(miZoomOut, zoom > 10);
        setEnabledIf(miZoomReset, zoom != 100);
        miWrap.setChecked(wrap != 0);
        miStatus.setChecked(statusBarOn != 0);
    }

    /** ~20 Hz poll: caret position, zoom/eol/encoding labels and menu enable states. */
    void tick() {
        if (win.disposed != 0) {
            return;
        }
        int s = edit.getSelectionStart();
        int e = edit.getSelectionEnd();
        int len = edit.textLength();
        int undo = 0;
        if (edit.canUndo()) {
            undo = 1;
        }
        int paste = 0;
        if (UiSys.clipboardHasText()) {
            paste = 1;
        }
        boolean menuChanged = s != cSelS || e != cSelE || len != cLen || undo != cUndo
                || paste != cPaste;
        cSelS = s;
        cSelE = e;
        cLen = len;
        cUndo = undo;
        cPaste = paste;
        if (menuChanged) {
            applyMenuStates();
        }
        if (statusPanel.visible != 0) {
            int line = edit.lineFromChar(e);
            int ls = edit.lineIndex(line);
            if (ls < 0) {
                ls = 0;
            }
            String pos = "Ln " + (line + 1) + ", Col " + (e - ls + 1);
            if (!pos.equals(cPos)) {
                cPos = pos;
                stPos.setText(pos);
            }
            String z = zoom + "%";
            if (!z.equals(cZoom)) {
                cZoom = z;
                stZoom.setText(z);
            }
            String en = NpCodec.eolName(eol);
            if (!en.equals(cEol)) {
                cEol = en;
                stEol.setText(en);
            }
            String ec = NpCodec.encName(enc);
            if (!ec.equals(cEnc)) {
                cEnc = ec;
                stEnc.setText(ec);
            }
        }
    }

    // ===================================================================== document

    /** Replace the whole document text (CRLF-normalized) without marking it modified. */
    void setDocumentText(String crlfText) {
        suppress = 1;
        edit.setTextReset(crlfText);
        suppress = 0;
        edit.setSelection(0, 0);
        edit.scrollCaret();
        dirty = 0;
        cLen = -1;
        updateTitle();
    }

    boolean confirmDiscard() {
        if (dirty == 0) {
            return true;
        }
        String name = "Untitled";
        if (path != null) {
            name = path;
        }
        int r = Dialogs.show(win, 3, "Notepad", "Do you want to save changes to " + name + "?");
        if (r == 1) {
            return doSave();
        }
        if (r == 2) {
            return true;
        }
        return false;
    }

    void doNew() {
        if (!confirmDiscard()) {
            return;
        }
        path = null;
        enc = NpCodec.ENC_UTF8;
        eol = NpCodec.EOL_CRLF;
        setDocumentText("");
    }

    void doNewWindow() {
        NpWindow w = new NpWindow();
        w.show();
    }

    void doOpen() {
        if (!confirmDiscard()) {
            return;
        }
        String p = FileDialogEx.show(win, 0, FILTER, "", OPEN_ENC_LABELS, 0);
        if (p == null) {
            return;
        }
        int sel = FileDialogEx.lastEncoding;
        int forced = -1;
        if (sel == 1) {
            forced = NpCodec.ENC_ANSI;
        } else if (sel == 2) {
            forced = NpCodec.ENC_UTF16LE;
        } else if (sel == 3) {
            forced = NpCodec.ENC_UTF16BE;
        } else if (sel == 4) {
            forced = NpCodec.ENC_UTF8;
        }
        loadFile(p, forced);
    }

    /** forcedEnc &lt; 0 = auto-detect. */
    boolean loadFile(String p, int forcedEnc) {
        byte[] data = null;
        try {
            data = File.readBytes(p);
        } catch (Exception ex) {
            data = null;
        }
        if (data == null) {
            Dialogs.show(win, 7, "Notepad", "Cannot open the " + p + " file.");
            return false;
        }
        int e = forcedEnc;
        if (e < 0) {
            e = NpCodec.detectEncoding(data);
        } else if (e == NpCodec.ENC_UTF8 && data.length >= 3 && (data[0] & 255) == 239
                && (data[1] & 255) == 187 && (data[2] & 255) == 191) {
            e = NpCodec.ENC_UTF8BOM;
        }
        String txt = NpCodec.decode(data, e);
        eol = NpCodec.detectEol(txt);
        enc = e;
        path = p;
        setDocumentText(NpCodec.normalizeEol(txt, NpCodec.EOL_CRLF));
        return true;
    }

    /** Command-line open: offers to create a missing file like Notepad does. */
    void openFromArgument(String p) {
        if (File.exists(p)) {
            loadFile(p, -1);
            return;
        }
        int r = Dialogs.show(win, 5, "Notepad", "Cannot find the " + p
                + " file.\n\nDo you want to create a new file?");
        if (r == 1) {
            try {
                File.writeBytes(p, new byte[0]);
            } catch (Exception ex) {
                Dialogs.show(win, 7, "Notepad", "Cannot create the " + p + " file.\n\n"
                        + "Make sure the path and file name are correct.");
                return;
            }
            path = p;
            enc = NpCodec.ENC_UTF8;
            eol = NpCodec.EOL_CRLF;
            setDocumentText("");
        }
    }

    boolean doSave() {
        if (path == null) {
            return doSaveAs();
        }
        return writeFile(path, enc);
    }

    boolean doSaveAs() {
        String def = "*.txt";
        if (!NpUi.isWindows()) {
            def = "Untitled.txt";
        }
        if (path != null) {
            def = NpUi.baseName(path);
        }
        String p = FileDialogEx.show(win, 1, FILTER, def, NpCodec.encLabels(), enc);
        if (p == null) {
            return false;
        }
        int e = FileDialogEx.lastEncoding;
        if (e < 0 || e > 4) {
            e = enc;
        }
        return writeFile(p, e);
    }

    boolean writeFile(String p, int e) {
        String text = NpCodec.normalizeEol(edit.getText(), eol);
        if (e == NpCodec.ENC_ANSI && NpCodec.ansiLossy(text)) {
            int r = Dialogs.show(win, 5, "Notepad",
                    "This file contains characters in Unicode format which will be lost if "
                    + "you save this file as an ANSI encoded text file. To keep the Unicode "
                    + "information, click No below and then select one of the Unicode options "
                    + "in the Encoding drop down.\n\nContinue?");
            if (r != 1) {
                return false;
            }
        }
        byte[] data = NpCodec.encode(text, e);
        try {
            File.writeBytes(p, data);
        } catch (Exception ex) {
            Dialogs.show(win, 7, "Notepad", "Cannot create the " + p + " file.\n\n"
                    + "Make sure the path and file name are correct.");
            return false;
        }
        path = p;
        enc = e;
        dirty = 0;
        updateTitle();
        return true;
    }

    void doPageSetup() {
        if (!NpUi.isWindows()) {
            Dialogs.show(win, 6, "Notepad", "Page Setup is only available on Windows.");
            return;
        }
        UiSys.pageSetup(win);
    }

    void doPrint() {
        if (!NpUi.isWindows()) {
            Dialogs.show(win, 6, "Notepad", "Printing is only available on Windows.");
            return;
        }
        String name = "Untitled";
        if (path != null) {
            name = NpUi.baseName(path);
        }
        UiSys.printText(win, name, edit.getText(), fontFamily, fontSize10, fontWeight, fontItalic);
    }

    // ===================================================================== view / format

    void toggleWrap() {
        if (wrap != 0) {
            wrap = 0;
        } else {
            wrap = 1;
        }
        edit.setWordWrap(wrap != 0);
        applyFont();
        applyMenuStates();
        layoutAll();
        cLen = -1;
    }

    void toggleStatusBar() {
        if (wrap != 0) {
            return;
        }
        if (statusBarOn != 0) {
            statusBarOn = 0;
        } else {
            statusBarOn = 1;
        }
        applyMenuStates();
        layoutAll();
    }

    void applyFont() {
        int sz = fontSize10 * zoom / 100;
        if (sz < 10) {
            sz = 10;
        }
        edit.setEditorFont(fontFamily, sz, fontWeight, fontItalic);
    }

    void setZoom(int z) {
        if (z < 10) {
            z = 10;
        }
        if (z > 500) {
            z = 500;
        }
        if (z == zoom) {
            return;
        }
        zoom = z;
        applyFont();
        applyMenuStates();
        cZoom = "";
    }

    void chooseFont() {
        String r = FontDialog.choose(win, fontFamily, fontSize10, fontWeight, fontItalic);
        if (r == null) {
            return;
        }
        String[] parts = r.split("|");
        if (parts.length < 4) {
            return;
        }
        int size = NpUi.parseInt(parts[1]);
        int weight = NpUi.parseInt(parts[2]);
        int italic = NpUi.parseInt(parts[3]);
        if (size <= 0 || weight < 0 || italic < 0) {
            return;
        }
        fontFamily = parts[0];
        fontSize10 = size;
        fontWeight = weight;
        fontItalic = italic;
        applyFont();
    }

    void showAbout() {
        Dialogs.show(win, 6, "About Notepad", "Notepad\n"
                + "JNative edition 1.0 (native build, no Java runtime)\n\n"
                + "A JNative re-implementation of the Windows Notepad.");
    }

    // ===================================================================== keys

    void onWindowKey(KeyEvent ev) {
        if (ev == null) {
            return;
        }
        // Ctrl+wheel arrives as a synthesized key (rawMods bit 16).
        if ((ev.rawMods & 16) != 0) {
            if (ev.keyCode == 61) {
                setZoom(zoom + 10);
            } else if (ev.keyCode == 45) {
                setZoom(zoom - 10);
            }
            ev.consume();
        }
    }

    // ===================================================================== search

    String selectedText() {
        int s = edit.getSelectionStart();
        int e = edit.getSelectionEnd();
        if (e <= s) {
            return "";
        }
        String t = edit.getText();
        if (e > t.length()) {
            e = t.length();
        }
        return t.substring(s, e);
    }

    void searchWithBing() {
        String sel = selectedText();
        if (sel.length() == 0) {
            return;
        }
        Desktop.open("https://www.bing.com/search?q=" + NpUi.urlEncode(sel));
    }

    void openFind() {
        if (findDlg == null) {
            findDlg = new NpFindDialog(this, 0);
        }
        if (replaceDlg != null) {
            replaceDlg.hideDialog();
        }
        findDlg.open(selectedText());
    }

    void openReplace() {
        if (replaceDlg == null) {
            replaceDlg = new NpFindDialog(this, 1);
        }
        if (findDlg != null) {
            findDlg.hideDialog();
        }
        replaceDlg.open(selectedText());
    }

    void openGoto() {
        if (wrap != 0) {
            return;
        }
        if (gotoDlg == null) {
            gotoDlg = new NpGotoDialog(this);
        }
        gotoDlg.open();
    }

    /** F3 / Shift+F3. Opens the Find dialog when there is nothing to search for yet. */
    void findNextCommand(int down) {
        if (findText.length() == 0) {
            openFind();
            return;
        }
        findNext(down, win);
    }

    String foldCase(String s) {
        if (findMatchCase != 0) {
            return s;
        }
        return s.toLowerCase();
    }

    /** Search from the selection; selects the match. Reports "Cannot find" on failure. */
    boolean findNext(int down, Window msgOwner) {
        String needle = findText;
        if (needle.length() == 0) {
            return false;
        }
        String hay = foldCase(edit.getText());
        String nd = foldCase(needle);
        int s = edit.getSelectionStart();
        int e = edit.getSelectionEnd();
        int idx = -1;
        if (down != 0) {
            idx = hay.indexOfFrom(nd, e);
            if (idx < 0 && findWrapAround != 0) {
                idx = hay.indexOf(nd);
            }
        } else {
            int from = s - nd.length();
            if (from >= 0) {
                idx = hay.lastIndexOfFrom(nd, from);
            }
            if (idx < 0 && findWrapAround != 0) {
                idx = hay.lastIndexOf(nd);
            }
        }
        if (idx < 0) {
            Dialogs.show(msgOwner, 6, "Notepad", "Cannot find \"" + needle + "\"");
            return false;
        }
        edit.setSelection(idx, idx + nd.length());
        edit.scrollCaret();
        return true;
    }

    void replaceOne(Window msgOwner) {
        String needle = findText;
        if (needle.length() == 0) {
            return;
        }
        String sel = selectedText();
        if (sel.length() > 0 && foldCase(sel).equals(foldCase(needle))) {
            edit.replaceSelection(replaceText);
        }
        findNext(1, msgOwner);
    }

    void replaceAll(Window msgOwner) {
        String needle = findText;
        if (needle.length() == 0) {
            return;
        }
        String text = edit.getText();
        String hay = foldCase(text);
        String nd = foldCase(needle);
        StringBuilder sb = new StringBuilder();
        int pos = 0;
        int count = 0;
        while (true) {
            int idx = hay.indexOfFrom(nd, pos);
            if (idx < 0) {
                break;
            }
            sb.append(text.substring(pos, idx));
            sb.append(replaceText);
            pos = idx + nd.length();
            count = count + 1;
        }
        if (count == 0) {
            Dialogs.show(msgOwner, 6, "Notepad", "Cannot find \"" + needle + "\"");
            return;
        }
        sb.append(text.substringFrom(pos));
        edit.setSelection(0, -1);
        edit.replaceSelection(sb.toString());
        edit.setSelection(0, 0);
        edit.scrollCaret();
    }

    /** Go To Line: 1-based. Returns false (after a message) when beyond the last line. */
    boolean gotoLine(int line, Window msgOwner) {
        int total = edit.lineCount();
        if (line < 1 || line > total) {
            Dialogs.show(msgOwner, 7, "Notepad - Goto Line",
                    "The line number is beyond the total number of lines");
            return false;
        }
        int idx = edit.lineIndex(line - 1);
        if (idx < 0) {
            idx = 0;
        }
        edit.setSelection(idx, idx);
        edit.scrollCaret();
        edit.requestFocus();
        return true;
    }

    int currentLine() {
        return edit.lineFromChar(edit.getSelectionEnd()) + 1;
    }

    // ===================================================================== lifecycle

    void show() {
        win.show();
        UiNative.timerStart(50, timerHost.nativeId);
        edit.requestFocus();
        tick();
    }
}
