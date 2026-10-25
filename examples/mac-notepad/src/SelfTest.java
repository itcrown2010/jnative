import jnative.ui.*;
import jnative.io.*;

/** 无鼠标自测：直接驱动 MainForm 的文件、查找替换和编辑逻辑，并可截图检查界面。 */
public class SelfTest {
    static int failed = 0;

    static void check(String name, String got, String want) {
        if (got.equals(want)) {
            System.out.println("ok   " + name);
        } else {
            failed = failed + 1;
            System.out.println("FAIL " + name + ": got [" + got + "] want [" + want + "]");
        }
    }

    /** 模拟用户输入：在光标处插入文本并触发设计器绑定的变更事件。 */
    static void type(MainForm f, String s) {
        f.txtEditor.replaceSelection(s);
        f.onEditorChange(null);
    }

    static void fresh(MainForm f, String text) {
        f.crlf = false;
        f.bom = false;
        f.loadText(text, "");
    }

    static MainForm form;
    static UiTimer undoTimer;
    static int undoTries = 0;

    /** 撤销需要真实的运行循环（NSUndoManager 按事件分组），所以放在定时器里异步检查，最后退出进程。 */
    static void start() {
        int failedSync = run();
        MainForm f = form;
        fresh(f, "");
        type(f, "abc");
        undoTries = 0;
        undoTimer = new UiTimer(300, () -> {
            f.txtEditor.undo();
            undoTries = undoTries + 1;
            // 系统繁忙时撤销登记可能晚一拍，最多重试 5 次
            if (f.txtEditor.getText().length() > 0 && undoTries < 5) {
                return;
            }
            undoTimer.stop();
            check("撤销", f.txtEditor.getText(), "");
            f.txtEditor.redo();
            check("重做", f.txtEditor.getText(), "abc");
            System.out.println(failed == 0 ? "ALL OK" : failed + " FAILED");
            System.exit(failed == 0 ? 0 : 1);
        });
    }

    static int run() {
        MainForm f = new MainForm();
        form = f;
        f.show();
        String tmp = "/tmp/macnotepad-selftest";
        Files.createDirectories(FsPath.of(tmp));

        check("初始标题", f.titleText, "未命名");
        check("初始统计", f.lblStats.getText(), "0 字符 · 1 行");

        type(f, "hello\nworld");
        check("编辑后标记已编辑", f.titleText, "未命名 — 已编辑");
        f.refreshStatus(true);
        check("统计", f.lblStats.getText(), "11 字符 · 2 行");
        check("光标位置", f.lblPosition.getText(), "行 2，列 6");

        String p1 = tmp + "/a.txt";
        check("存储成功", f.writeFile(p1) ? "yes" : "no", "yes");
        check("存储后清除标记", f.titleText, "a.txt");
        check("文件内容", Files.readString(FsPath.of(p1)), "hello\nworld");

        // CRLF + BOM 往返
        String p2 = tmp + "/b.txt";
        Files.writeString(FsPath.of(p2), "" + (char) 65279 + "x\r\ny\r\n");
        f.openPath(p2);
        check("打开 CRLF 文件统一为 LF", f.txtEditor.getText(), "x\ny\n");
        check("检测到 CRLF 与 BOM", f.crlf + "/" + f.bom, "true/true");
        check("格式标签", f.lblFormat.getText(), "UTF-8 (BOM) · CRLF");
        type(f, "z");
        f.doSave();
        check("保存时写回 CRLF 与 BOM", Files.readString(FsPath.of(p2)), "" + (char) 65279 + "zx\r\ny\r\n");

        // 查找
        fresh(f, "Foo bar foo FOO");
        f.txtFind.setText("foo");
        f.stepFind(1, true);
        check("首个匹配", f.txtEditor.getSelectionStart() + "-" + f.txtEditor.getSelectionEnd(), "0-3");
        check("匹配计数", f.lblMatches.getText(), "1 / 3");
        f.stepFind(1, false);
        check("下一个", f.txtEditor.getSelectionStart() + "-" + f.txtEditor.getSelectionEnd(), "8-11");
        f.stepFind(1, false);
        f.stepFind(1, false);
        check("绕回开头", f.lblMatches.getText(), "1 / 3");
        f.stepFind(-1, false);
        check("上一个绕回末尾", f.lblMatches.getText(), "3 / 3");
        f.chkCase.setSelected(true);
        f.stepFind(1, true);
        check("区分大小写", f.lblMatches.getText(), "1 / 1");
        f.stepFind(1, false);
        check("无结果提示", f.lblMatches.getText(), "1 / 1");
        f.txtFind.setText("nope");
        f.stepFind(1, true);
        check("无结果", f.lblMatches.getText(), "无结果");
        f.chkCase.setSelected(false);

        // 替换
        fresh(f, "foo bar foo");
        f.txtFind.setText("foo");
        f.txtReplace.setText("X");
        f.stepFind(1, true);
        f.onReplace();
        check("替换一个并跳到下一个", f.txtEditor.getText() + "|" + f.txtEditor.getSelectionStart(), "X bar foo|6");
        f.onReplaceAll();
        check("全部替换", f.txtEditor.getText(), "X bar X");
        check("替换计数", f.lblMatches.getText(), "已替换 1 处");

        // 非 BMP 字符：选区下标是 UTF-16
        fresh(f, "😀 foo");
        f.txtFind.setText("foo");
        f.stepFind(1, true);
        check("emoji 之后的匹配选区", f.txtEditor.getSelectionStart() + "-" + f.txtEditor.getSelectionEnd(), "3-6");
        f.txtReplace.setText("bar");
        f.onReplace();
        check("emoji 之后替换", f.txtEditor.getText(), "😀 bar");

        // 转到行 / 大小写 / 排序
        fresh(f, "a\nb\nc");
        f.gotoLine(3);
        check("转到第 3 行", "" + f.txtEditor.getSelectionStart(), "4");
        fresh(f, "hello world");
        f.txtEditor.setSelection(0, 5);
        f.transformSelection(true);
        check("转大写", f.txtEditor.getText(), "HELLO world");
        fresh(f, "b\nc\na\n");
        f.txtEditor.setSelection(0, -1);
        f.sortSelectedLines();
        check("排序所选行", f.txtEditor.getText(), "\na\nb\nc");

        // 视图设置
        f.zoom(0);
        f.zoom(2);
        check("放大字号", "" + f.fontPt, "17");
        f.zoom(0);
        check("实际大小", "" + f.fontPt, "15");
        boolean w0 = f.wrap;
        f.toggleWrap();
        check("切换自动换行", "" + (f.wrap != w0), "true");
        f.toggleWrap();

        // 未保存时的新建流程（已存储则直接清空）
        fresh(f, "keep");
        f.doNew();
        check("新建后为空", f.txtEditor.getText() + "|" + f.titleText, "|未命名");
        return failed;
    }

    static int snapStep = 0;
    static UiTimer snapTimer;

    /** 打开 file（可为空）、展开查找替换栏，把窗口截图到 png 后退出。 */
    static void snapshot(String file, String png) {
        MainForm f = new MainForm();
        f.show();
        snapTimer = new UiTimer(700, () -> {
            snapStep = snapStep + 1;
            if (snapStep == 1) {
                if (file.length() > 0) {
                    f.openPath(file);
                }
                f.openFindBar(true);
                f.txtFind.setText("the");
                f.txtReplace.setText("THE");
                f.stepFind(1, true);
            } else if (snapStep == 3) {
                f.window.snapshot(png.length() > 0 ? png : "/tmp/macnotepad.png");
                System.out.println("snapshot done");
                System.exit(0);
            }
        });
    }
}
