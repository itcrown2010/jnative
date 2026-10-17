/**
 * Notepad tests: codec / helpers (pure) and, with argument "ui", a scripted scenario
 * that drives a real NpWindow (document load, find/replace, go to, zoom, save).
 *
 * @author 张晓达
 */
public class NpTests {
    static int pass = 0;
    static int fail = 0;

    static void check(String name, String got, String want) {
        if (got.equals(want)) {
            pass = pass + 1;
        } else {
            fail = fail + 1;
            System.out.println("FAIL " + name + ": got [" + got + "] want [" + want + "]");
        }
    }

    static void checkInt(String name, int got, int want) {
        check(name, "" + got, "" + want);
    }

    static byte[] bytes(int a, int b, int c, int d, int e, int f) {
        byte[] r = new byte[6];
        r[0] = (byte) a;
        r[1] = (byte) b;
        r[2] = (byte) c;
        r[3] = (byte) d;
        r[4] = (byte) e;
        r[5] = (byte) f;
        return r;
    }

    static void codecTests() {
        // encoding detection
        byte[] le = bytes(255, 254, 72, 0, 105, 0);
        checkInt("detect LE", NpCodec.detectEncoding(le), NpCodec.ENC_UTF16LE);
        check("decode LE", NpCodec.decode(le, NpCodec.ENC_UTF16LE), "Hi");
        byte[] be = bytes(254, 255, 0, 72, 0, 105);
        checkInt("detect BE", NpCodec.detectEncoding(be), NpCodec.ENC_UTF16BE);
        check("decode BE", NpCodec.decode(be, NpCodec.ENC_UTF16BE), "Hi");
        byte[] u8b = bytes(239, 187, 191, 72, 105, 33);
        checkInt("detect BOM", NpCodec.detectEncoding(u8b), NpCodec.ENC_UTF8BOM);
        check("decode BOM", NpCodec.decode(u8b, NpCodec.ENC_UTF8BOM), "Hi!");
        byte[] ascii = bytes(72, 101, 108, 108, 111, 33);
        checkInt("detect ascii=UTF-8", NpCodec.detectEncoding(ascii), NpCodec.ENC_UTF8);
        byte[] bad = bytes(72, 233, 108, 108, 111, 33);
        checkInt("detect invalid utf8 = ANSI", NpCodec.detectEncoding(bad), NpCodec.ENC_ANSI);
        byte[] nolebom = bytes(72, 0, 105, 0, 33, 0);
        checkInt("detect LE w/o BOM", NpCodec.detectEncoding(nolebom), NpCodec.ENC_UTF16LE);

        // UTF-8 round trip with CJK + astral
        String uni = "中文abc😀";
        byte[] enc8 = NpCodec.encode(uni, NpCodec.ENC_UTF8);
        checkInt("utf8 len", enc8.length, 3 + 3 + 3 + 4);
        check("utf8 round trip", NpCodec.decode(enc8, NpCodec.ENC_UTF8), uni);
        byte[] enc8b = NpCodec.encode(uni, NpCodec.ENC_UTF8BOM);
        checkInt("utf8bom len", enc8b.length, 3 + 13);
        check("utf8bom round trip", NpCodec.decode(enc8b, NpCodec.ENC_UTF8BOM), uni);
        byte[] e16 = NpCodec.encode(uni, NpCodec.ENC_UTF16LE);
        checkInt("utf16le len", e16.length, 2 + 2 * 7);
        check("utf16le round trip", NpCodec.decode(e16, NpCodec.ENC_UTF16LE), uni);
        byte[] e16b = NpCodec.encode(uni, NpCodec.ENC_UTF16BE);
        check("utf16be round trip", NpCodec.decode(e16b, NpCodec.ENC_UTF16BE), uni);

        // ANSI
        String latin = "caf" + NpUi.singleChar(65) + "x";
        byte[] ansi = NpCodec.encode(latin, NpCodec.ENC_ANSI);
        checkInt("ansi len", ansi.length, 5);
        check("ansi round trip", NpCodec.decode(ansi, NpCodec.ENC_ANSI), latin);
        checkInt("ansi lossy ascii", NpCodec.ansiLossy("hello") ? 1 : 0, 0);

        // line endings
        checkInt("eol crlf", NpCodec.detectEol("a\r\nb"), NpCodec.EOL_CRLF);
        checkInt("eol lf", NpCodec.detectEol("a\nb"), NpCodec.EOL_LF);
        checkInt("eol cr", NpCodec.detectEol("a\rb"), NpCodec.EOL_CR);
        checkInt("eol none", NpCodec.detectEol("ab"), NpCodec.EOL_CRLF);
        check("norm lf->crlf", NpCodec.normalizeEol("a\nb\n", NpCodec.EOL_CRLF), "a\r\nb\r\n");
        check("norm mixed->lf", NpCodec.normalizeEol("a\r\nb\rc\nd", NpCodec.EOL_LF), "a\nb\nc\nd");
        check("norm crlf->cr", NpCodec.normalizeEol("a\r\nb", NpCodec.EOL_CR), "a\rb");
        check("norm none", NpCodec.normalizeEol("abc", NpCodec.EOL_CRLF), "abc");

        // helpers
        check("basename win", NpUi.baseName("C:\\dir\\file.txt"), "file.txt");
        check("basename posix", NpUi.baseName("/a/b/c.log"), "c.log");
        check("basename plain", NpUi.baseName("x.txt"), "x.txt");
        checkInt("parse 42", NpUi.parseInt(" 42 "), 42);
        checkInt("parse bad", NpUi.parseInt("4x"), -1);
        checkInt("parse empty", NpUi.parseInt(""), -1);
        check("urlenc", NpUi.urlEncode("a b&c"), "a+b%26c");
        check("urlenc utf8", NpUi.urlEncode("中"), "%E4%B8%AD");
    }

    static void uiScenario() {
        Application.setAppName("NotepadTest");
        Application app = new Application();
        NpWindow w = new NpWindow();
        w.show();

        String p = "/tmp/np/sample_ui.txt";
        File.writeBytes(p, bytes(72, 105, 13, 10, 98, 121));
        w.loadFile(p, -1);
        check("ui title", w.shownTitle, "sample_ui.txt - Notepad");
        check("ui text", w.edit.getText(), "Hi\r\nby");
        checkInt("ui enc", w.enc, NpCodec.ENC_UTF8);
        checkInt("ui eol", w.eol, NpCodec.EOL_CRLF);
        checkInt("ui not dirty after load", w.dirty, 0);
        checkInt("ui lines", w.edit.lineCount(), 2);

        // typing marks the document modified
        w.edit.setSelection(0, 0);
        w.edit.replaceSelection("X");
        checkInt("ui dirty after edit", w.dirty, 1);
        w.updateTitle();
        check("ui dirty title", w.shownTitle, "*sample_ui.txt - Notepad");
        check("ui edited", w.edit.getText(), "XHi\r\nby");
        checkInt("ui can undo", w.edit.canUndo() ? 1 : 0, 1);

        // find / go to
        NpWindow.findText = "hi";
        NpWindow.findMatchCase = 0;
        NpWindow.findWrapAround = 1;
        w.edit.setSelection(0, 0);
        checkInt("ui find ci", w.findNext(1, w.win) ? 1 : 0, 1);
        checkInt("ui find sel start", w.edit.getSelectionStart(), 1);
        checkInt("ui find sel end", w.edit.getSelectionEnd(), 3);
        NpWindow.findMatchCase = 1;
        w.edit.setSelection(0, 0);
        NpWindow.findText = "Hi";
        checkInt("ui find cs", w.findNext(1, w.win) ? 1 : 0, 1);
        checkInt("ui goto 2", w.gotoLine(2, w.win) ? 1 : 0, 1);
        checkInt("ui goto caret", w.edit.getSelectionStart(), 5);
        checkInt("ui current line", w.currentLine(), 2);

        // replace all
        NpWindow.findMatchCase = 0;
        NpWindow.findText = "i";
        NpWindow.replaceText = "II";
        w.replaceAll(w.win);
        check("ui replace all", w.edit.getText(), "XHII\r\nby");

        // zoom
        w.setZoom(150);
        checkInt("ui zoom", w.zoom, 150);
        w.setZoom(9999);
        checkInt("ui zoom max", w.zoom, 500);
        w.setZoom(100);

        // save as LF / UTF-16
        w.eol = NpCodec.EOL_LF;
        checkInt("ui save utf8", w.writeFile("/tmp/np/out_lf.txt", NpCodec.ENC_UTF8) ? 1 : 0, 1);
        byte[] back = File.readBytes("/tmp/np/out_lf.txt");
        check("ui saved lf", NpCodec.decode(back, NpCodec.ENC_UTF8), "XHII\nby");
        checkInt("ui clean after save", w.dirty, 0);
        checkInt("ui save utf16", w.writeFile("/tmp/np/out_16.txt", NpCodec.ENC_UTF16LE) ? 1 : 0, 1);
        byte[] b16 = File.readBytes("/tmp/np/out_16.txt");
        checkInt("ui utf16 detected", NpCodec.detectEncoding(b16), NpCodec.ENC_UTF16LE);
        w.gotoLine(2, w.win);
        w.tick();
        check("ui status pos", w.cPos, "Ln 2, Col 1");
    }

    public static void main(String[] args) {
        codecTests();
        if (args != null && args.length > 0 && args[0].equals("ui")) {
            uiScenario();
        }
        System.out.println("passed=" + pass + " failed=" + fail);
    }
}
