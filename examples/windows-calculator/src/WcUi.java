/**
 * Windows 11 Calculator UI on JNative UiLib: Standard / Scientific / Programmer,
 * memory + history (docked when wide, flyout when narrow), navigation flyout,
 * trig / function / bitwise / shift flyouts, programmer bit toggles, keyboard
 * shortcuts, resize relayout and light/dark theme that follows the system.
 *
 * All geometry is absolute (Panel children) and recomputed in relayout().
 *
 * @author 张晓达
 */
class WcUi {
    static final int POOL = 10;

    WcEngine eng;
    WcTheme th;
    Window window;
    Panel root;
    int W;
    int H;
    int mode;
    int second;
    int hyp;
    int shiftMode;
    int bitsView;
    int sideOpen;
    int sideTab;
    int flyKind;
    int navOpen;
    int lastDark;
    int hOff;
    int mOff;
    int resPt;

    int[] modeW;
    int[] modeH;

    /* header + display */
    Button navBtn;
    Label titleLbl;
    Button histBtn;
    Label exprLbl;
    Label resLbl;

    /* memory row */
    Button[] memBtn;
    String[] memKey;

    /* keypad */
    Button[] cells;
    String[] cLbl;
    String[] cKey;
    int[] cKind;
    int cCols;
    int cRows;
    String[] stdLbl;
    String[] stdKey;
    int[] stdKind;
    String[] sciLbl;
    String[] sciKey;
    int[] sciKind;
    String[] sciLbl2;
    String[] sciKey2;
    String[] proLbl;
    String[] proKey;
    int[] proKind;

    /* scientific toolbar */
    Button angleBtn;
    Button feBtn;
    Button trigBtn;
    Button funcBtn;

    /* programmer */
    Button[] radix;
    Button bitsBtn;
    Button wordBtn;
    Button bitwiseBtn;
    Button shiftBtn;
    Button[] bitBtn;

    /* flyout */
    Panel fly;
    Button[] flyBtn;
    String[] flyLbl;
    String[] flyKey;
    int[] flyKind2;
    int flyN;
    int flyCols;

    /* history / memory side panel */
    Panel side;
    Button tabHist;
    Button tabMem;
    Label sideEmpty;
    Button sideClear;
    Button[] hRow;
    Label[] hExpr;
    Button[] mRow;
    Button[] mMc;
    Button[] mPlus;
    Button[] mMinus;
    int sideRows;

    /* navigation flyout */
    Panel nav;
    Label navTitle;
    Button[] navItem;

    Label timerHost;

    WcUi() {
        eng = new WcEngine();
        th = new WcTheme();
        mode = WcEngine.MODE_STD;
        second = 0;
        hyp = 0;
        shiftMode = 0;
        bitsView = 0;
        sideOpen = 0;
        sideTab = 0;
        flyKind = 0;
        navOpen = 0;
        hOff = 0;
        mOff = 0;
        resPt = 0;
        modeW = new int[3];
        modeH = new int[3];
        modeW[0] = 336;
        modeH[0] = 540;
        modeW[1] = 540;
        modeH[1] = 640;
        modeW[2] = 520;
        modeH[2] = 680;
        W = modeW[0];
        H = modeH[0];
        lastDark = Theme.isDarkEffective();
        th.apply(lastDark);
        buildTables();

        window = new Window("Calculator", W, H);
        window.setContentMinSize(320, 480);
        window.setBackgroundRgba(th.bg);
        root = new Panel();
        root.setBackgroundRgba(th.bg);
        window.setContent(root);

        buildHeader();
        buildMemoryRow();
        buildSciToolbar();
        buildProgrammer();
        buildKeypad();
        buildSide();
        buildFlyout();
        buildNav();

        window.onKeyDown((ev) -> {
            onKey(ev);
        });
        window.setResizeListener(() -> {
            onResized();
        });
        timerHost = new Label(" ");
        timerHost.setBounds(0, 0, 1, 1);
        root.add(timerHost);
        timerHost.timerHook = () -> {
            pollTheme();
        };
    }

    void show() {
        window.show();
        UiNative.timerStart(1000, timerHost.nativeId);
        applyMode(WcEngine.MODE_STD, 0);
    }

    /* ------------------------------------------------------------------ */
    /* Tables                                                              */
    /* ------------------------------------------------------------------ */

    void buildTables() {
        stdLbl = new String[24];
        stdKey = new String[24];
        stdKind = new int[24];
        sciLbl = new String[35];
        sciKey = new String[35];
        sciKind = new int[35];
        sciLbl2 = new String[35];
        sciKey2 = new String[35];
        proLbl = new String[30];
        proKey = new String[30];
        proKind = new int[30];

        /* Standard: 4 x 6 */
        setStd(0, "%", "%", 1);
        setStd(1, "CE", "CE", 1);
        setStd(2, "C", "CLEAR", 1);
        setStd(3, "⌫", "BS", 1);
        setStd(4, "1/x", "recip", 1);
        setStd(5, "x²", "sqr", 1);
        setStd(6, "√x", "sqrt", 1);
        setStd(7, "÷", "/", 1);
        setStd(8, "7", "7", 0);
        setStd(9, "8", "8", 0);
        setStd(10, "9", "9", 0);
        setStd(11, "×", "*", 1);
        setStd(12, "4", "4", 0);
        setStd(13, "5", "5", 0);
        setStd(14, "6", "6", 0);
        setStd(15, "−", "-", 1);
        setStd(16, "1", "1", 0);
        setStd(17, "2", "2", 0);
        setStd(18, "3", "3", 0);
        setStd(19, "+", "+", 1);
        setStd(20, "±", "neg", 0);
        setStd(21, "0", "0", 0);
        setStd(22, ".", ".", 0);
        setStd(23, "=", "=", 2);

        /* Scientific: 5 x 7 (second labels in *2) */
        setSci(0, "2nd", "ui:2nd", 1, null, null);
        setSci(1, "π", "pi", 1, null, null);
        setSci(2, "e", "e", 1, null, null);
        setSci(3, "C", "CLEAR", 1, null, null);
        setSci(4, "⌫", "BS", 1, null, null);
        setSci(5, "x²", "sqr", 1, "x³", "cube");
        setSci(6, "1/x", "recip", 1, null, null);
        setSci(7, "|x|", "abs", 1, null, null);
        setSci(8, "exp", "exp", 1, null, null);
        setSci(9, "mod", "mod", 1, null, null);
        setSci(10, "√x", "sqrt", 1, "∛x", "cbrt");
        setSci(11, "(", "(", 1, null, null);
        setSci(12, ")", ")", 1, null, null);
        setSci(13, "n!", "fact", 1, null, null);
        setSci(14, "÷", "/", 1, null, null);
        setSci(15, "xʸ", "pow", 1, "ʸ√x", "yroot");
        setSci(16, "7", "7", 0, null, null);
        setSci(17, "8", "8", 0, null, null);
        setSci(18, "9", "9", 0, null, null);
        setSci(19, "×", "*", 1, null, null);
        setSci(20, "10ˣ", "pow10", 1, "2ˣ", "pow2");
        setSci(21, "4", "4", 0, null, null);
        setSci(22, "5", "5", 0, null, null);
        setSci(23, "6", "6", 0, null, null);
        setSci(24, "−", "-", 1, null, null);
        setSci(25, "log", "log", 1, "log_y x", "logy");
        setSci(26, "1", "1", 0, null, null);
        setSci(27, "2", "2", 0, null, null);
        setSci(28, "3", "3", 0, null, null);
        setSci(29, "+", "+", 1, null, null);
        setSci(30, "ln", "ln", 1, "eˣ", "epow");
        setSci(31, "±", "neg", 0, null, null);
        setSci(32, "0", "0", 0, null, null);
        setSci(33, ".", ".", 0, null, null);
        setSci(34, "=", "=", 2, null, null);

        /* Programmer: 5 x 6 */
        setPro(0, "A", "A", 1);
        setPro(1, "<<", "ui:lsh", 1);
        setPro(2, ">>", "ui:rsh", 1);
        setPro(3, "C", "CLEAR", 1);
        setPro(4, "⌫", "BS", 1);
        setPro(5, "B", "B", 1);
        setPro(6, "(", "(", 1);
        setPro(7, ")", ")", 1);
        setPro(8, "mod", "mod", 1);
        setPro(9, "÷", "/", 1);
        setPro(10, "C", "C", 1);
        setPro(11, "7", "7", 0);
        setPro(12, "8", "8", 0);
        setPro(13, "9", "9", 0);
        setPro(14, "×", "*", 1);
        setPro(15, "D", "D", 1);
        setPro(16, "4", "4", 0);
        setPro(17, "5", "5", 0);
        setPro(18, "6", "6", 0);
        setPro(19, "−", "-", 1);
        setPro(20, "E", "E", 1);
        setPro(21, "1", "1", 0);
        setPro(22, "2", "2", 0);
        setPro(23, "3", "3", 0);
        setPro(24, "+", "+", 1);
        setPro(25, "F", "F", 1);
        setPro(26, "±", "neg", 0);
        setPro(27, "0", "0", 0);
        setPro(28, ".", ".", 0);
        setPro(29, "=", "=", 2);

        cLbl = new String[36];
        cKey = new String[36];
        cKind = new int[36];
    }

    void setStd(int i, String l, String k, int kind) {
        stdLbl[i] = l;
        stdKey[i] = k;
        stdKind[i] = kind;
    }

    void setSci(int i, String l, String k, int kind, String l2, String k2) {
        sciLbl[i] = l;
        sciKey[i] = k;
        sciKind[i] = kind;
        sciLbl2[i] = l2;
        sciKey2[i] = k2;
    }

    void setPro(int i, String l, String k, int kind) {
        proLbl[i] = l;
        proKey[i] = k;
        proKind[i] = kind;
    }

    /* ------------------------------------------------------------------ */
    /* Construction                                                        */
    /* ------------------------------------------------------------------ */

    Button mk(Component parent, String text, int kind, int pt) {
        Button b = new Button(text);
        if (parent == root) {
            root.add(b);
        } else {
            ((Panel) parent).add(b);
        }
        style(b, kind, pt, 1);
        return b;
    }

    Label mkLabel(Component parent, String text, int pt, int align, int fg, int bg) {
        Label l = new Label(text);
        if (parent == root) {
            root.add(l);
        } else {
            ((Panel) parent).add(l);
        }
        l.setVisual(bg, fg, -1, pt, align);
        return l;
    }

    void style(Button b, int kind, int pt, int align) {
        int bg = th.keyNum;
        int fg = th.text;
        if (kind == 1) {
            bg = th.keyFunc;
        } else if (kind == 2) {
            bg = th.keyAccent;
            fg = th.textOnAccent;
        } else if (kind == 3) {
            bg = th.keyToggleOn;
            fg = th.textOnAccent;
        } else if (kind == 4) {
            bg = th.bg;
        } else if (kind == 5) {
            bg = th.panel;
        } else if (kind == 6) {
            bg = th.panel;
            fg = th.accent;
        } else if (kind == 7) {
            bg = th.bg;
            fg = th.accent;
        }
        if (b.bgRgba == bg && b.fgRgba == fg && b.fontSizePt == pt && b.textAlign == align) {
            return;
        }
        b.setFilledStyle(bg, fg, 4, pt);
        if (align != 1) {
            b.setTextAlign(align);
        }
    }

    void buildHeader() {
        navBtn = mk(root, "≡", 4, 16);
        navBtn.onClick(() -> {
            toggleNav();
        });
        titleLbl = mkLabel(root, "Standard", 14, 0, th.text, th.bg);
        histBtn = mk(root, "History", 4, 11);
        histBtn.onClick(() -> {
            toggleSide(0);
        });
        exprLbl = mkLabel(root, " ", 11, 2, th.textSec, th.bg);
        resLbl = mkLabel(root, "0", 30, 2, th.text, th.bg);
        resPt = 30;
    }

    void buildMemoryRow() {
        memBtn = new Button[6];
        memKey = new String[6];
        memKey[0] = "MC";
        memKey[1] = "MR";
        memKey[2] = "M+";
        memKey[3] = "M-";
        memKey[4] = "MS";
        memKey[5] = "ui:mem";
        String[] lbl = new String[6];
        lbl[0] = "MC";
        lbl[1] = "MR";
        lbl[2] = "M+";
        lbl[3] = "M−";
        lbl[4] = "MS";
        lbl[5] = "M˅";
        int i = 0;
        while (i < 6) {
            memBtn[i] = mk(root, lbl[i], 4, 11);
            bindKey(memBtn[i], memKey[i]);
            i = i + 1;
        }
    }

    void buildSciToolbar() {
        angleBtn = mk(root, "DEG", 4, 11);
        angleBtn.onClick(() -> {
            doKey("ui:angle");
        });
        feBtn = mk(root, "F-E", 4, 11);
        feBtn.onClick(() -> {
            doKey("ui:fe");
        });
        trigBtn = mk(root, "Trigonometry ˅", 4, 11);
        trigBtn.onClick(() -> {
            toggleFly(1);
        });
        funcBtn = mk(root, "Function ˅", 4, 11);
        funcBtn.onClick(() -> {
            toggleFly(2);
        });
    }

    void buildProgrammer() {
        radix = new Button[4];
        String[] names = new String[4];
        names[0] = "HEX";
        names[1] = "DEC";
        names[2] = "OCT";
        names[3] = "BIN";
        int i = 0;
        while (i < 4) {
            radix[i] = mk(root, names[i], 4, 11);
            bindRadix(radix[i], i);
            i = i + 1;
        }
        bitsBtn = mk(root, "⁝⁝⁝", 4, 13);
        bitsBtn.onClick(() -> {
            doKey("ui:bits");
        });
        wordBtn = mk(root, "QWORD", 4, 11);
        wordBtn.onClick(() -> {
            doKey("ui:word");
        });
        bitwiseBtn = mk(root, "Bitwise ˅", 4, 11);
        bitwiseBtn.onClick(() -> {
            toggleFly(3);
        });
        shiftBtn = mk(root, "Bit shift ˅", 4, 11);
        shiftBtn.onClick(() -> {
            toggleFly(4);
        });
        bitBtn = new Button[64];
        i = 0;
        while (i < 64) {
            bitBtn[i] = mk(root, "0", 4, 11);
            bindBit(bitBtn[i], i);
            i = i + 1;
        }
    }

    void buildKeypad() {
        cells = new Button[36];
        int i = 0;
        while (i < 36) {
            cells[i] = mk(root, " ", 0, 14);
            bindCell(cells[i], i);
            i = i + 1;
        }
    }

    void buildSide() {
        side = new Panel();
        side.setBackgroundRgba(th.panel);
        root.add(side);
        tabHist = mk(side, "History", 6, 12);
        tabHist.onClick(() -> {
            setSideTab(0);
        });
        tabMem = mk(side, "Memory", 5, 12);
        tabMem.onClick(() -> {
            setSideTab(1);
        });
        sideEmpty = mkLabel(side, "There's no history yet", 11, 0, th.textSec, th.panel);
        sideClear = mk(side, "Clear history", 6, 11);
        sideClear.onClick(() -> {
            clearSide();
        });
        hRow = new Button[POOL];
        hExpr = new Label[POOL];
        mRow = new Button[POOL];
        mMc = new Button[POOL];
        mPlus = new Button[POOL];
        mMinus = new Button[POOL];
        int i = 0;
        while (i < POOL) {
            hRow[i] = mk(side, " ", 5, 16);
            hRow[i].setTextAlign(2);
            bindHist(hRow[i], i);
            hExpr[i] = mkLabel(side, " ", 11, 2, th.textSec, th.panel);
            mRow[i] = mk(side, " ", 5, 16);
            mRow[i].setTextAlign(2);
            bindMemRow(mRow[i], i);
            mMc[i] = mk(side, "MC", 6, 10);
            bindMemOp(mMc[i], i, 0);
            mPlus[i] = mk(side, "M+", 6, 10);
            bindMemOp(mPlus[i], i, 1);
            mMinus[i] = mk(side, "M−", 6, 10);
            bindMemOp(mMinus[i], i, 2);
            i = i + 1;
        }
        side.setVisible(false);
    }

    void buildFlyout() {
        fly = new Panel();
        fly.setBackgroundRgba(th.panel);
        root.add(fly);
        flyBtn = new Button[8];
        flyLbl = new String[8];
        flyKey = new String[8];
        flyKind2 = new int[8];
        int i = 0;
        while (i < 8) {
            flyBtn[i] = mk(fly, " ", 5, 12);
            bindFly(flyBtn[i], i);
            i = i + 1;
        }
        fly.setVisible(false);
    }

    void buildNav() {
        nav = new Panel();
        nav.setBackgroundRgba(th.panel);
        root.add(nav);
        navTitle = mkLabel(nav, "Calculator", 13, 0, th.text, th.panel);
        navItem = new Button[3];
        String[] names = new String[3];
        names[0] = "Standard";
        names[1] = "Scientific";
        names[2] = "Programmer";
        int i = 0;
        while (i < 3) {
            navItem[i] = mk(nav, names[i], 5, 13);
            navItem[i].setTextAlign(0);
            bindNav(navItem[i], i);
            i = i + 1;
        }
        nav.setVisible(false);
    }

    /* Binders keep the captured value effectively final. */
    void bindKey(Button b, String key) {
        b.onClick(() -> {
            doKey(key);
        });
    }

    void bindCell(Button b, int i) {
        b.onClick(() -> {
            doKey(cKey[i]);
        });
    }

    void bindRadix(Button b, int i) {
        b.onClick(() -> {
            int base = 16;
            if (i == 1) {
                base = 10;
            } else if (i == 2) {
                base = 8;
            } else if (i == 3) {
                base = 2;
            }
            closeFlyouts();
            eng.setBase(base);
            refresh();
        });
    }

    void bindBit(Button b, int i) {
        b.onClick(() -> {
            closeFlyouts();
            eng.toggleBit(i);
            refresh();
        });
    }

    void bindHist(Button b, int i) {
        b.onClick(() -> {
            int idx = eng.histN - 1 - hOff - i;
            if (idx >= 0 && idx < eng.histN) {
                eng.recallHistory(idx);
                if (docked() == 0) {
                    sideOpen = 0;
                }
                relayout();
                refresh();
            }
        });
    }

    void bindMemRow(Button b, int i) {
        b.onClick(() -> {
            eng.memRecallAt(mOff + i);
            if (docked() == 0) {
                sideOpen = 0;
            }
            relayout();
            refresh();
        });
    }

    void bindMemOp(Button b, int i, int op) {
        b.onClick(() -> {
            int idx = mOff + i;
            if (op == 0) {
                eng.memClearAt(idx);
            } else if (op == 1) {
                eng.memAddAt(idx);
            } else {
                eng.memSubAt(idx);
            }
            refresh();
        });
    }

    void bindFly(Button b, int i) {
        b.onClick(() -> {
            flyClick(i);
        });
    }

    void bindNav(Button b, int i) {
        b.onClick(() -> {
            navOpen = 0;
            applyMode(i, 1);
        });
    }

    /* ------------------------------------------------------------------ */
    /* Actions                                                             */
    /* ------------------------------------------------------------------ */

    int docked() {
        if (W >= 720) {
            return 1;
        }
        return 0;
    }

    void closeFlyouts() {
        if (flyKind != 0) {
            flyKind = 0;
            fly.setVisible(false);
        }
        if (navOpen != 0) {
            navOpen = 0;
            nav.setVisible(false);
        }
    }

    void doKey(String key) {
        if (key == null || key.length() == 0) {
            return;
        }
        if (key.equals("ui:2nd")) {
            closeFlyouts();
            second = 1 - second;
            refresh();
            return;
        }
        if (key.equals("ui:hyp")) {
            hyp = 1 - hyp;
            refresh();
            fillFly();
            return;
        }
        if (key.equals("ui:angle")) {
            closeFlyouts();
            eng.cycleAngle();
            refresh();
            return;
        }
        if (key.equals("ui:fe")) {
            closeFlyouts();
            eng.toggleFE();
            refresh();
            return;
        }
        if (key.equals("ui:bits")) {
            closeFlyouts();
            bitsView = 1 - bitsView;
            relayout();
            refresh();
            return;
        }
        if (key.equals("ui:word")) {
            closeFlyouts();
            if (eng.bits == 64) {
                eng.setWordSize(32);
            } else if (eng.bits == 32) {
                eng.setWordSize(16);
            } else if (eng.bits == 16) {
                eng.setWordSize(8);
            } else {
                eng.setWordSize(64);
            }
            refresh();
            return;
        }
        if (key.equals("ui:mem")) {
            closeFlyouts();
            toggleSide(1);
            return;
        }
        if (key.equals("ui:lsh")) {
            closeFlyouts();
            eng.press("lsh");
            refresh();
            return;
        }
        if (key.equals("ui:rsh")) {
            closeFlyouts();
            if (shiftMode == 2) {
                eng.press("ror");
            } else if (shiftMode == 1) {
                eng.press("rshl");
            } else {
                eng.press("rsh");
            }
            refresh();
            return;
        }
        closeFlyouts();
        eng.press(key);
        refresh();
    }

    void applyMode(int m, int resize) {
        mode = m;
        eng.setMode(m);
        eng.setBase(eng.base);
        second = 0;
        hyp = 0;
        closeFlyouts();
        if (m == WcEngine.MODE_STD) {
            window.setContentMinSize(320, 480);
        } else if (m == WcEngine.MODE_SCI) {
            window.setContentMinSize(480, 560);
        } else {
            window.setContentMinSize(480, 620);
        }
        if (resize != 0) {
            int nw = modeW[m];
            int nh = modeH[m];
            window.setSize(nw, nh);
            W = nw;
            H = nh;
            forceLayout = 1;
        }
        loadCells();
        relayout();
        forceLayout = 0;
        refresh();
    }

    void loadCells() {
        int i = 0;
        int n = 0;
        if (mode == WcEngine.MODE_STD) {
            cCols = 4;
            cRows = 6;
            n = 24;
            while (i < n) {
                cLbl[i] = stdLbl[i];
                cKey[i] = stdKey[i];
                cKind[i] = stdKind[i];
                i = i + 1;
            }
        } else if (mode == WcEngine.MODE_SCI) {
            cCols = 5;
            cRows = 7;
            n = 35;
            while (i < n) {
                cLbl[i] = sciLbl[i];
                cKey[i] = sciKey[i];
                cKind[i] = sciKind[i];
                i = i + 1;
            }
        } else {
            cCols = 5;
            cRows = 6;
            n = 30;
            while (i < n) {
                cLbl[i] = proLbl[i];
                cKey[i] = proKey[i];
                cKind[i] = proKind[i];
                i = i + 1;
            }
        }
        while (i < 36) {
            cLbl[i] = "";
            cKey[i] = "";
            cKind[i] = 0;
            i = i + 1;
        }
    }

    void toggleNav() {
        if (navOpen != 0) {
            navOpen = 0;
            nav.setVisible(false);
            return;
        }
        closeFlyouts();
        navOpen = 1;
        relayoutNav();
        nav.setVisible(true);
        refresh();
    }

    void toggleSide(int tab) {
        if (docked() != 0) {
            sideTab = tab;
            sideOpen = 1;
            hOff = 0;
            mOff = 0;
            relayout();
            refresh();
            return;
        }
        if (sideOpen != 0 && sideTab == tab) {
            sideOpen = 0;
        } else {
            sideOpen = 1;
            sideTab = tab;
            hOff = 0;
            mOff = 0;
        }
        closeFlyouts();
        relayout();
        refresh();
    }

    void setSideTab(int tab) {
        sideTab = tab;
        hOff = 0;
        mOff = 0;
        refresh();
    }

    void clearSide() {
        if (sideTab == 0) {
            eng.clearHistory();
        } else {
            eng.memClear();
        }
        hOff = 0;
        mOff = 0;
        refresh();
    }

    void scrollSide(int dir) {
        if (sideOpen == 0 && docked() == 0) {
            return;
        }
        if (sideTab == 0) {
            int maxOff = eng.histN - sideRows;
            if (maxOff < 0) {
                maxOff = 0;
            }
            hOff = hOff + dir;
            if (hOff < 0) {
                hOff = 0;
            }
            if (hOff > maxOff) {
                hOff = maxOff;
            }
        } else {
            int maxOff = eng.memN - sideRows;
            if (maxOff < 0) {
                maxOff = 0;
            }
            mOff = mOff + dir;
            if (mOff < 0) {
                mOff = 0;
            }
            if (mOff > maxOff) {
                mOff = maxOff;
            }
        }
        refresh();
    }

    /* ---- flyouts ---- */

    void toggleFly(int kind) {
        if (flyKind == kind) {
            closeFlyouts();
            return;
        }
        closeFlyouts();
        flyKind = kind;
        fillFly();
        placeFly();
        fly.setVisible(true);
    }

    void fillFly() {
        if (flyKind == 0) {
            return;
        }
        int n = 0;
        if (flyKind == 1) {
            flyCols = 2;
            setFly(0, "2nd", "ui:2nd", second);
            setFly(1, "hyp", "ui:hyp", hyp);
            String[] base = new String[6];
            base[0] = "sin";
            base[1] = "cos";
            base[2] = "tan";
            base[3] = "sec";
            base[4] = "csc";
            base[5] = "cot";
            int i = 0;
            while (i < 6) {
                String lab = base[i];
                String key = base[i];
                if (second != 0) {
                    key = "a" + key;
                    lab = lab + "⁻¹";
                }
                if (hyp != 0) {
                    key = key + "h";
                    lab = lab + "h";
                }
                setFly(2 + i, lab, key, 0);
                i = i + 1;
            }
            n = 8;
        } else if (flyKind == 2) {
            flyCols = 2;
            setFly(0, "|x|", "abs", 0);
            setFly(1, "⌊x⌋", "floor", 0);
            setFly(2, "⌈x⌉", "ceil", 0);
            setFly(3, "rand", "rand", 0);
            setFly(4, "→dms", "dms", 0);
            setFly(5, "→deg", "deg", 0);
            n = 6;
        } else if (flyKind == 3) {
            flyCols = 2;
            setFly(0, "AND", "and", 0);
            setFly(1, "OR", "or", 0);
            setFly(2, "NOT", "not", 0);
            setFly(3, "NAND", "nand", 0);
            setFly(4, "NOR", "nor", 0);
            setFly(5, "XOR", "xor", 0);
            n = 6;
        } else {
            flyCols = 1;
            setFly(0, "Arithmetic shift", "ui:shift0", 0);
            setFly(1, "Logical shift", "ui:shift1", 0);
            setFly(2, "Rotate circular shift", "ui:shift2", 0);
            n = 3;
        }
        flyN = n;
        int i = 0;
        while (i < 8) {
            if (i < n) {
                int k = 5;
                if (flyKind == 4 && shiftMode == i) {
                    k = 6;
                }
                if (flyKey[i].equals("ui:2nd") || flyKey[i].equals("ui:hyp")) {
                    if (flyKind2[i] != 0) {
                        k = 3;
                    } else {
                        k = 1;
                    }
                }
                if (flyKind == 1 && i >= 2) {
                    k = 1;
                }
                if (flyKind == 2 || flyKind == 3) {
                    k = 1;
                }
                style(flyBtn[i], k, 12, 1);
                flyBtn[i].setText(flyLbl[i]);
                if (flyBtn[i].visible == 0) {
                    flyBtn[i].setVisible(true);
                }
            } else if (flyBtn[i].visible != 0) {
                flyBtn[i].setVisible(false);
            }
            i = i + 1;
        }
    }

    void setFly(int i, String l, String k, int on) {
        flyLbl[i] = l;
        flyKey[i] = k;
        flyKind2[i] = on;
    }

    void placeFly() {
        int cellW = 88;
        int cellH = 38;
        if (flyCols == 1) {
            cellW = 190;
        }
        int rows = (flyN + flyCols - 1) / flyCols;
        int fw = cellW * flyCols + 8 + (flyCols - 1) * 2;
        int fh = cellH * rows + 8 + (rows - 1) * 2;
        Button anchor = trigBtn;
        if (flyKind == 2) {
            anchor = funcBtn;
        } else if (flyKind == 3) {
            anchor = bitwiseBtn;
        } else if (flyKind == 4) {
            anchor = shiftBtn;
        }
        int fx = anchor.x;
        int fy = anchor.y + anchor.height + 2;
        int calcW = W;
        if (docked() != 0) {
            calcW = W - sidePanelW();
        }
        if (fx + fw > calcW - 4) {
            fx = calcW - 4 - fw;
        }
        if (fx < 4) {
            fx = 4;
        }
        place(fly, fx, fy, fw, fh);
        int i = 0;
        while (i < flyN) {
            int c = i % flyCols;
            int r = i / flyCols;
            place(flyBtn[i], 4 + c * (cellW + 2), 4 + r * (cellH + 2), cellW, cellH);
            i = i + 1;
        }
    }

    void flyClick(int i) {
        if (i < 0 || i >= flyN) {
            return;
        }
        String key = flyKey[i];
        if (key.equals("ui:2nd") || key.equals("ui:hyp")) {
            doKey(key);
            return;
        }
        if (key.equals("ui:shift0")) {
            shiftMode = 0;
            closeFlyouts();
            refresh();
            return;
        }
        if (key.equals("ui:shift1")) {
            shiftMode = 1;
            closeFlyouts();
            refresh();
            return;
        }
        if (key.equals("ui:shift2")) {
            shiftMode = 2;
            closeFlyouts();
            refresh();
            return;
        }
        doKey(key);
    }

    /* ------------------------------------------------------------------ */
    /* Keyboard                                                            */
    /* ------------------------------------------------------------------ */

    void onKey(KeyEvent ev) {
        if (ev == null || ev.down == 0) {
            return;
        }
        int code = ev.keyCode;
        if ((ev.rawMods / 16) % 2 != 0) {
            if (code == 38) {
                scrollSide(-1);
            } else {
                scrollSide(1);
            }
            return;
        }
        if (ev.ctrlDown != 0) {
            ctrlKey(code);
            return;
        }
        if (ev.altDown != 0) {
            if (code == 49) {
                applyMode(WcEngine.MODE_STD, 1);
            } else if (code == 50) {
                applyMode(WcEngine.MODE_SCI, 1);
            } else if (code == 51 || code == 52) {
                applyMode(WcEngine.MODE_PROG, 1);
            }
            return;
        }
        if (code >= 112 && code <= 123) {
            fKey(code - 111);
            return;
        }
        if (code == 27) {
            if (flyKind != 0 || navOpen != 0) {
                closeFlyouts();
                return;
            }
            if (sideOpen != 0 && docked() == 0) {
                sideOpen = 0;
                relayout();
                refresh();
                return;
            }
            doKey("CLEAR");
            return;
        }
        if (code == 13) {
            doKey("=");
            return;
        }
        if (code == 8) {
            doKey("BS");
            return;
        }
        if (code == 46) {
            doKey("CE");
            return;
        }
        if (code == 33) {
            scrollSide(-3);
            return;
        }
        if (code == 34) {
            scrollSide(3);
            return;
        }
        int ch = ev.typedChar;
        if (ch < 32 || ch > 126) {
            return;
        }
        String k = keyForChar(ch);
        if (k.length() > 0) {
            doKey(k);
        }
    }

    String keyForChar(int ch) {
        String table = " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";
        String c = table.substring(ch - 32, ch - 31);
        return mapChar(c);
    }

    String mapChar(String c) {
        String digits = "0123456789";
        if (digits.indexOf(c) >= 0) {
            return c;
        }
        if (mode == WcEngine.MODE_PROG) {
            String hex = "abcdefABCDEF";
            if (hex.indexOf(c) >= 0) {
                return c.toUpperCase();
            }
        }
        if (c.equals(".") || c.equals(",")) {
            return ".";
        }
        if (c.equals("+") || c.equals("-") || c.equals("*") || c.equals("/") || c.equals("=")) {
            return c;
        }
        if (c.equals("(") || c.equals(")")) {
            if (mode == WcEngine.MODE_STD) {
                return "";
            }
            return c;
        }
        if (c.equals("%")) {
            if (mode == WcEngine.MODE_STD) {
                return "%";
            }
            return "mod";
        }
        if (c.equals("@")) {
            return "sqrt";
        }
        if (c.equals("r") || c.equals("R")) {
            return "recip";
        }
        if (c.equals("q") || c.equals("Q")) {
            return "sqr";
        }
        if (mode == WcEngine.MODE_SCI) {
            return mapSci(c);
        }
        if (mode == WcEngine.MODE_PROG) {
            return mapProg(c);
        }
        return "";
    }

    String mapSci(String c) {
        if (c.equals("!")) {
            return "fact";
        }
        if (c.equals("#")) {
            return "cube";
        }
        if (c.equals("^") || c.equals("y") || c.equals("Y")) {
            return "pow";
        }
        if (c.equals("|")) {
            return "abs";
        }
        if (c.equals("p") || c.equals("P")) {
            return "pi";
        }
        if (c.equals("e") || c.equals("E")) {
            return "e";
        }
        if (c.equals("n") || c.equals("N")) {
            return "ln";
        }
        if (c.equals("l") || c.equals("L")) {
            return "log";
        }
        if (c.equals("s") || c.equals("S")) {
            return sciTrig("sin");
        }
        if (c.equals("o") || c.equals("O")) {
            return sciTrig("cos");
        }
        if (c.equals("t") || c.equals("T")) {
            return sciTrig("tan");
        }
        if (c.equals("m") || c.equals("M")) {
            return "dms";
        }
        if (c.equals("x") || c.equals("X")) {
            return "exp";
        }
        if (c.equals("v") || c.equals("V")) {
            return "ui:fe";
        }
        if (c.equals("d") || c.equals("D")) {
            return "deg";
        }
        return "";
    }

    String sciTrig(String base) {
        String key = base;
        if (second != 0) {
            key = "a" + key;
        }
        if (hyp != 0) {
            key = key + "h";
        }
        return key;
    }

    String mapProg(String c) {
        if (c.equals("&")) {
            return "and";
        }
        if (c.equals("|")) {
            return "or";
        }
        if (c.equals("^")) {
            return "xor";
        }
        if (c.equals("~")) {
            return "not";
        }
        if (c.equals("<")) {
            return "ui:lsh";
        }
        if (c.equals(">")) {
            return "ui:rsh";
        }
        return "";
    }

    void fKey(int n) {
        if (n == 9) {
            doKey("neg");
            return;
        }
        if (mode == WcEngine.MODE_SCI) {
            if (n == 3) {
                eng.angle = WcMath.DEG;
            } else if (n == 4) {
                eng.angle = WcMath.RAD;
            } else if (n == 5) {
                eng.angle = WcMath.GRAD;
            } else if (n == 12) {
                eng.toggleFE();
            }
            refresh();
            return;
        }
        if (mode == WcEngine.MODE_PROG) {
            int base = 0;
            if (n == 5) {
                base = 16;
            } else if (n == 6) {
                base = 10;
            } else if (n == 7) {
                base = 8;
            } else if (n == 8) {
                base = 2;
            }
            if (base != 0) {
                eng.setBase(base);
                refresh();
            }
        }
    }

    void ctrlKey(int code) {
        if (code == 67) {
            copyResult();
        } else if (code == 86) {
            pasteResult();
        } else if (code == 76) {
            doKey("MC");
        } else if (code == 82) {
            doKey("MR");
        } else if (code == 77) {
            doKey("MS");
        } else if (code == 80) {
            doKey("M+");
        } else if (code == 81) {
            doKey("M-");
        } else if (code == 72) {
            toggleSide(0);
        }
    }

    String plainDisplay() {
        String t = eng.displayText();
        String out = "";
        int i = 0;
        while (i < t.length()) {
            String c = t.substring(i, i + 1);
            if (!c.equals(",") && !c.equals(" ")) {
                out = out + c;
            }
            i = i + 1;
        }
        return out;
    }

    void copyResult() {
        Clipboard.setText(plainDisplay());
    }

    void pasteResult() {
        String t = Clipboard.getText();
        if (t == null) {
            return;
        }
        t = t.trim();
        if (t.length() == 0) {
            return;
        }
        String allowed = "0123456789.";
        if (mode == WcEngine.MODE_PROG) {
            if (eng.base == 16) {
                allowed = "0123456789abcdefABCDEF";
            } else if (eng.base == 8) {
                allowed = "01234567";
            } else if (eng.base == 2) {
                allowed = "01";
            } else {
                allowed = "0123456789";
            }
        }
        int neg = 0;
        int i = 0;
        String[] keys = new String[t.length() + 1];
        int kn = 0;
        while (i < t.length()) {
            String c = t.substring(i, i + 1);
            if (c.equals("-") && i == 0) {
                neg = 1;
            } else if (c.equals(",") || c.equals(" ")) {
                /* digit grouping */
            } else if (allowed.indexOf(c) >= 0) {
                if (mode == WcEngine.MODE_PROG) {
                    keys[kn] = c.toUpperCase();
                } else {
                    keys[kn] = c;
                }
                kn = kn + 1;
            } else {
                eng.setError(WcErr.INVALID);
                refresh();
                return;
            }
            i = i + 1;
        }
        closeFlyouts();
        eng.press("CE");
        i = 0;
        while (i < kn) {
            eng.press(keys[i]);
            i = i + 1;
        }
        if (neg != 0) {
            eng.press("neg");
        }
        refresh();
    }

    /* ------------------------------------------------------------------ */
    /* Theme                                                               */
    /* ------------------------------------------------------------------ */

    void pollTheme() {
        int d = Theme.isDarkEffective();
        if (d != lastDark) {
            lastDark = d;
            th.apply(d);
            retheme();
        }
    }

    void retheme() {
        window.setBackgroundRgba(th.bg);
        root.setBackgroundRgba(th.bg);
        side.setBackgroundRgba(th.panel);
        fly.setBackgroundRgba(th.panel);
        nav.setBackgroundRgba(th.panel);
        titleLbl.setVisual(th.bg, th.text, -1, 14, 0);
        exprLbl.setVisual(th.bg, th.textSec, -1, 11, 2);
        resLbl.setVisual(th.bg, th.text, -1, resPt, 2);
        sideEmpty.setVisual(th.panel, th.textSec, -1, 11, 0);
        navTitle.setVisual(th.panel, th.text, -1, 13, 0);
        int i = 0;
        while (i < POOL) {
            hExpr[i].setVisual(th.panel, th.textSec, -1, 11, 2);
            i = i + 1;
        }
        refresh();
    }

    /* ------------------------------------------------------------------ */
    /* Layout                                                              */
    /* ------------------------------------------------------------------ */

    void onResized() {
        int nw = window.width;
        int nh = window.height;
        if (nw == W && nh == H) {
            return;
        }
        W = nw;
        H = nh;
        modeW[mode] = nw;
        modeH[mode] = nh;
        forceLayout = 1;
        relayout();
        forceLayout = 0;
        refresh();
    }

    int sidePanelW() {
        return 300;
    }

    void place(Component c, int x, int y, int w, int h) {
        if (w < 1) {
            w = 1;
        }
        if (h < 1) {
            h = 1;
        }
        if (forceLayout == 0 && c.x == x && c.y == y && c.width == w && c.height == h) {
            return;
        }
        c.setBounds(x, y, w, h);
    }

    void vis(Component c, int on) {
        int cur = 0;
        if (c.visible != 0) {
            cur = 1;
        }
        if (cur != on) {
            c.setVisible(on != 0);
        }
    }

    void relayout() {
        root.setBounds(0, 0, W, H);
        int calcW = W;
        int dock = docked();
        if (dock != 0) {
            calcW = W - sidePanelW();
            sideOpen = 1;
        }
        int std = 0;
        int sci = 0;
        int pro = 0;
        if (mode == WcEngine.MODE_STD) {
            std = 1;
        } else if (mode == WcEngine.MODE_SCI) {
            sci = 1;
        } else {
            pro = 1;
        }

        /* header */
        place(navBtn, 4, 4, 40, 40);
        place(titleLbl, 52, 12, calcW - 52 - 90, 26);
        place(histBtn, calcW - 88, 6, 82, 36);
        vis(histBtn, dock != 0 ? 0 : 1);
        place(exprLbl, 8, 46, calcW - 16, 22);
        place(resLbl, 8, 68, calcW - 16, 64);

        int y = 136;
        if (pro != 0) {
            int i = 0;
            while (i < 4) {
                place(radix[i], 4, y + i * 24, calcW - 8, 24);
                i = i + 1;
            }
            y = y + 4 * 24 + 4;
        }
        int rowW = calcW - 8;
        int mw = (rowW - 5 * 2) / 6;
        int memCount = 6;
        if (dock != 0) {
            memCount = 5;
            mw = (rowW - 4 * 2) / 5;
        }
        int i = 0;
        while (i < 6) {
            if (i < memCount) {
                place(memBtn[i], 4 + i * (mw + 2), y, mw, 32);
            }
            vis(memBtn[i], i < memCount ? 1 : 0);
            i = i + 1;
        }
        y = y + 34;

        vis(angleBtn, sci);
        vis(feBtn, sci);
        vis(trigBtn, sci);
        vis(funcBtn, sci);
        if (sci != 0) {
            int aw = 60;
            int fw = 52;
            int rest = rowW - aw - fw - 4;
            int tw = rest / 2 - 1;
            place(angleBtn, 4, y, aw, 32);
            place(feBtn, 4 + aw + 2, y, fw, 32);
            place(trigBtn, 4 + aw + fw + 4, y, tw, 32);
            place(funcBtn, 4 + aw + fw + 6 + tw, y, rest - tw - 2, 32);
            y = y + 34;
        }
        vis(bitsBtn, pro);
        vis(wordBtn, pro);
        vis(bitwiseBtn, pro);
        vis(shiftBtn, pro);
        int r = 0;
        while (r < 4) {
            vis(radix[r], pro);
            r = r + 1;
        }
        if (pro != 0) {
            int bw = 52;
            int ww = 76;
            int rest = rowW - bw - ww - 4;
            int hw = rest / 2 - 1;
            place(bitsBtn, 4, y, bw, 32);
            place(wordBtn, 4 + bw + 2, y, ww, 32);
            place(bitwiseBtn, 4 + bw + ww + 4, y, hw, 32);
            place(shiftBtn, 4 + bw + ww + 6 + hw, y, rest - hw - 2, 32);
            y = y + 34;
        }
        /* bit toggle grid (programmer) */
        int showBits = 0;
        if (pro != 0 && bitsView != 0) {
            showBits = 1;
        }
        int b = 0;
        while (b < 64) {
            vis(bitBtn[b], showBits);
            b = b + 1;
        }
        if (showBits != 0) {
            int gapN = 6;
            int cellW = (rowW - 3 * gapN - 15 * 1) / 16;
            int cellH = 26;
            int row = 0;
            while (row < 4) {
                int col = 0;
                while (col < 16) {
                    int bit = 63 - (row * 16 + col);
                    int px = 4 + col * (cellW + 1) + (col / 4) * gapN;
                    place(bitBtn[bit], px, y + row * (cellH + 2), cellW, cellH);
                    col = col + 1;
                }
                row = row + 1;
            }
            y = y + 4 * (cellH + 2) + 2;
        }

        /* keypad */
        int total = cCols * cRows;
        int ch = (H - 4 - y - (cRows - 1) * 2) / cRows;
        if (ch < 24) {
            ch = 24;
        }
        int cw2 = (rowW - (cCols - 1) * 2) / cCols;
        int k = 0;
        while (k < 36) {
            if (k < total) {
                int cc = k % cCols;
                int rr = k / cCols;
                place(cells[k], 4 + cc * (cw2 + 2), y + rr * (ch + 2), cw2, ch);
                vis(cells[k], 1);
            } else {
                vis(cells[k], 0);
            }
            k = k + 1;
        }
        keyPt = ch >= 60 ? 17 : (ch >= 48 ? 15 : (ch >= 40 ? 13 : 11));

        relayoutSide(calcW, dock);
        if (navOpen != 0) {
            relayoutNav();
        }
        if (flyKind != 0) {
            placeFly();
        }
    }

    int keyPt;
    int forceLayout;

    void relayoutSide(int calcW, int dock) {
        int show = 0;
        if (sideOpen != 0 || dock != 0) {
            show = 1;
        }
        if (show == 0) {
            vis(side, 0);
            return;
        }
        int sx = 0;
        int sy = 0;
        int sw = W;
        int sh = H;
        if (dock != 0) {
            sx = calcW;
            sy = 0;
            sw = W - calcW;
            sh = H;
        } else {
            sy = 136;
            sh = H - 136;
        }
        place(side, sx, sy, sw, sh);
        vis(side, 1);
        int tabW = sw / 2 - 6;
        place(tabHist, 4, 4, tabW, 36);
        place(tabMem, 8 + tabW, 4, tabW, 36);
        int rowH = 66;
        int listTop = 46;
        int clearH = 36;
        int avail = sh - listTop - clearH - 8;
        sideRows = avail / rowH;
        if (sideRows < 1) {
            sideRows = 1;
        }
        if (sideRows > POOL) {
            sideRows = POOL;
        }
        place(sideEmpty, 12, listTop + 6, sw - 24, 22);
        place(sideClear, sw - 130, sh - clearH - 4, 124, clearH);
        int i = 0;
        while (i < POOL) {
            int ry = listTop + i * rowH;
            place(hRow[i], 4, ry, sw - 8, rowH - 2);
            place(hExpr[i], 12, ry + 6, sw - 28, 18);
            place(mRow[i], 4, ry, sw - 8, rowH - 24);
            int bw = (sw - 8) / 3;
            place(mMc[i], 4, ry + rowH - 26, bw - 2, 24);
            place(mPlus[i], 4 + bw, ry + rowH - 26, bw - 2, 24);
            place(mMinus[i], 4 + 2 * bw, ry + rowH - 26, bw - 2, 24);
            i = i + 1;
        }
    }

    void relayoutNav() {
        place(nav, 0, 0, 260, H);
        place(navTitle, 16, 14, 200, 24);
        int i = 0;
        while (i < 3) {
            place(navItem[i], 6, 56 + i * 46, 248, 42);
            i = i + 1;
        }
    }

    /* ------------------------------------------------------------------ */
    /* Refresh                                                             */
    /* ------------------------------------------------------------------ */

    int fitPt(String s, int availPx, int maxPt) {
        int n = s.length();
        if (n < 1) {
            n = 1;
        }
        /* average glyph ~0.58em for digits + separators */
        int em = (availPx * 100) / (58 * n);
        int pt = (em * 3) / 4;
        if (pt > maxPt) {
            pt = maxPt;
        }
        if (pt < 12) {
            pt = 12;
        }
        return pt;
    }

    String modeName() {
        if (mode == WcEngine.MODE_SCI) {
            return "Scientific";
        }
        if (mode == WcEngine.MODE_PROG) {
            return "Programmer";
        }
        return "Standard";
    }

    void setText(Button b, String t) {
        if (!b.text.equals(t)) {
            b.setText(t);
        }
    }

    void setLabel(Label l, String t) {
        if (!l.text.equals(t)) {
            l.setText(t);
        }
    }

    void enable(Component c, int on) {
        int cur = 0;
        if (c.enabled != 0) {
            cur = 1;
        }
        if (cur != on) {
            c.setEnabled(on != 0);
        }
    }

    void refresh() {
        int calcW = W;
        if (docked() != 0) {
            calcW = W - sidePanelW();
        }
        setLabel(titleLbl, modeName());
        String ex = eng.expressionText();
        if (ex == null || ex.length() == 0) {
            ex = " ";
        }
        setLabel(exprLbl, ex);
        String disp = eng.displayText();
        setLabel(resLbl, disp);
        int pt = fitPt(disp, calcW - 24, 36);
        if (pt != resPt) {
            resPt = pt;
            resLbl.setFontSize(pt);
        }
        window.setTitle("Calculator");

        /* memory row enablement */
        int hasMem = eng.hasMemory();
        enable(memBtn[0], hasMem);
        enable(memBtn[1], hasMem);
        enable(memBtn[5], 1);
        int err = eng.inError();
        enable(memBtn[2], 1 - err);
        enable(memBtn[3], 1 - err);
        enable(memBtn[4], 1 - err);
        if (docked() != 0) {
            enable(memBtn[5], 0);
        }

        /* keypad */
        int total = cCols * cRows;
        int k = 0;
        while (k < total) {
            String lbl = cLbl[k];
            String key = cKey[k];
            int kind = cKind[k];
            if (mode == WcEngine.MODE_SCI && second != 0 && sciLbl2[k] != null) {
                lbl = sciLbl2[k];
                key = sciKey2[k];
            }
            if (mode == WcEngine.MODE_SCI) {
                if (key.equals("ui:2nd") && second != 0) {
                    kind = 3;
                }
            }
            if (mode == WcEngine.MODE_PROG) {
                if (key.equals("ui:lsh") || key.equals("ui:rsh")) {
                    lbl = shiftLabel(key);
                }
            }
            int pt2 = keyPt;
            if (kind == 1 && isDigitLike(key) == 0) {
                pt2 = keyPt - 2;
            }
            if (lbl.length() > 1 && kind == 1) {
                pt2 = keyPt - 3;
            }
            Button cb = cells[k];
            setText(cb, lbl);
            style(cb, kind, pt2, 1);
            int en = 1;
            if (key.startsWith("ui:")) {
                en = 1;
            } else {
                en = eng.keyEnabled(key);
            }
            if (mode == WcEngine.MODE_PROG && key.equals(".")) {
                en = 0;
            }
            enable(cb, en);
            k = k + 1;
        }

        /* scientific toolbar */
        if (mode == WcEngine.MODE_SCI) {
            setText(angleBtn, eng.angleName());
            style(angleBtn, 4, 11, 1);
            style(feBtn, eng.fe != 0 ? 3 : 4, 11, 1);
            style(trigBtn, flyKind == 1 ? 7 : 4, 11, 1);
            style(funcBtn, flyKind == 2 ? 7 : 4, 11, 1);
        }
        if (mode == WcEngine.MODE_PROG) {
            refreshProgrammer();
        }
        refreshSide();
        if (flyKind != 0) {
            fillFly();
        }
        if (navOpen != 0) {
            int i = 0;
            while (i < 3) {
                style(navItem[i], mode == i ? 6 : 5, 13, 0);
                i = i + 1;
            }
        }
    }

    int isDigitLike(String key) {
        if (key.length() == 1) {
            return 1;
        }
        return 0;
    }

    String shiftLabel(String key) {
        if (key.equals("ui:lsh")) {
            if (shiftMode == 2) {
                return "RoL";
            }
            return "<<";
        }
        if (shiftMode == 2) {
            return "RoR";
        }
        if (shiftMode == 1) {
            return ">>>";
        }
        return ">>";
    }

    void refreshProgrammer() {
        int i = 0;
        int[] bases = new int[4];
        bases[0] = 16;
        bases[1] = 10;
        bases[2] = 8;
        bases[3] = 2;
        String[] names = new String[4];
        names[0] = "HEX";
        names[1] = "DEC";
        names[2] = "OCT";
        names[3] = "BIN";
        while (i < 4) {
            String v = eng.radixText(bases[i]);
            setText(radix[i], names[i] + "    " + v);
            style(radix[i], eng.base == bases[i] ? 7 : 4, 11, 0);
            i = i + 1;
        }
        String wn = "QWORD";
        if (eng.bits == 32) {
            wn = "DWORD";
        } else if (eng.bits == 16) {
            wn = "WORD";
        } else if (eng.bits == 8) {
            wn = "BYTE";
        }
        setText(wordBtn, wn);
        style(bitsBtn, bitsView != 0 ? 3 : 4, 13, 1);
        style(bitwiseBtn, flyKind == 3 ? 7 : 4, 11, 1);
        style(shiftBtn, flyKind == 4 ? 7 : 4, 11, 1);
        if (bitsView != 0) {
            int b = 0;
            while (b < 64) {
                int on = eng.bitAt(b);
                setText(bitBtn[b], on != 0 ? "1" : "0");
                style(bitBtn[b], on != 0 ? 3 : 1, 11, 1);
                enable(bitBtn[b], b < eng.bits && eng.inError() == 0 ? 1 : 0);
                b = b + 1;
            }
        }
    }

    void refreshSide() {
        if (sideOpen == 0 && docked() == 0) {
            return;
        }
        style(tabHist, sideTab == 0 ? 6 : 5, 12, 1);
        style(tabMem, sideTab == 1 ? 6 : 5, 12, 1);
        int i = 0;
        if (sideTab == 0) {
            int n = eng.histN;
            int shown = n - hOff;
            if (shown > sideRows) {
                shown = sideRows;
            }
            if (shown < 0) {
                shown = 0;
            }
            vis(sideEmpty, n == 0 ? 1 : 0);
            setLabel(sideEmpty, "There's no history yet");
            vis(sideClear, n > 0 ? 1 : 0);
            setText(sideClear, "Clear history");
            while (i < POOL) {
                int on = i < shown ? 1 : 0;
                if (on != 0) {
                    int idx = n - 1 - hOff - i;
                    setText(hRow[i], eng.histText[idx]);
                    setLabel(hExpr[i], eng.histExpr[idx]);
                    style(hRow[i], 5, 16, 2);
                }
                vis(hRow[i], on);
                vis(hExpr[i], on);
                vis(mRow[i], 0);
                vis(mMc[i], 0);
                vis(mPlus[i], 0);
                vis(mMinus[i], 0);
                i = i + 1;
            }
        } else {
            int n = eng.memN;
            int shown = n - mOff;
            if (shown > sideRows) {
                shown = sideRows;
            }
            if (shown < 0) {
                shown = 0;
            }
            vis(sideEmpty, n == 0 ? 1 : 0);
            setLabel(sideEmpty, "There's nothing saved in memory");
            vis(sideClear, n > 0 ? 1 : 0);
            setText(sideClear, "Clear memory");
            while (i < POOL) {
                int on = i < shown ? 1 : 0;
                if (on != 0) {
                    int idx = mOff + i;
                    setText(mRow[i], eng.fmt(eng.mem[idx]));
                    style(mRow[i], 5, 16, 2);
                    int ok = eng.inError() == 0 ? 1 : 0;
                    enable(mPlus[i], ok);
                    enable(mMinus[i], ok);
                }
                vis(mRow[i], on);
                vis(mMc[i], on);
                vis(mPlus[i], on);
                vis(mMinus[i], on);
                vis(hRow[i], 0);
                vis(hExpr[i], 0);
                i = i + 1;
            }
        }
    }
}
