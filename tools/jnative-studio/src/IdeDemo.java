import jnative.ui.*;
import jnative.io.*;

/**
 * Scripted walkthrough used to verify the IDE end to end without a human at the mouse.
 *
 * @author 张晓达
 */
public class IdeDemo {
    static IdeApp ide;
    static int step = 0;
    static UiTimer timer;

    static void start(IdeApp a) {
        ide = a;
        timer = new UiTimer(3500, () -> tick());
    }

    static void dump() {
        StringBuilder sb = new StringBuilder();
        sb.append("step " + step + " status=" + ide.statusText + " tabs=" + ide.tabs.docs.size() + " active=" + ide.tabs.active + "\n");
        int i = 0;
        while (i < ide.output.root.kids.size()) {
            sb.append("  | " + ide.output.root.kids.get(i).text + "\n");
            i = i + 1;
        }
        Files.writeString(FsPath.of("/tmp/ide-demo.log"), sb.toString());
        ide.window.snapshot("/tmp/ide-snap-" + step + ".png");
    }

    static String logText = "";

    static void tryComplete(String typed, String label) {
        IdeDoc d = ide.activeDoc();
        CodeEditor e = d.editor;
        e.goTo(11, 0);
        e.insertText(typed);
        ide.completeInline(true);
        String s = label + " [" + typed.trim() + "]: ";
        IdeSuggest p = ide.suggest;
        if (p.isOpen && p.shown.size() > 0) {
            s = s + p.shown.size() + " items:";
            int i = 0;
            while (i < p.shown.size() && i < 6) {
                s = s + " " + p.shown.get(i).text;
                i = i + 1;
            }
            s = s + " @" + p.x + "," + p.y + " " + p.width + "x" + p.height + " vis=" + p.visible + " host=" + ide.host.width + "x" + ide.host.height;
            ide.window.snapshot("/tmp/ide-complete-" + label + ".png");
        } else {
            s = s + "no popup";
        }
        log(s + "\n");
    }

    static void defStep() {
        String main = ide.project.dir + "/src/MainForm.java";
        if (step == 1) {
            ide.openFile(main);
        } else if (step == 2) {
            ide.activeDoc().editor.goTo(11, 14);
            ide.gotoDefinition();
            IdeDoc d = ide.activeDoc();
            TextPosition p = d.editor.document.offsetToPosition(d.editor.caret.offset);
            log("lblMessage -> " + d.path + " line " + (p.line + 1) + "\n");
        } else if (step == 3) {
            ide.openFile(main);
            int off = ide.activeDoc().editor.getText().indexOf("getText");
            TextPosition p = ide.activeDoc().editor.document.offsetToPosition(off + 2);
            ide.activeDoc().editor.goTo(p.line, p.column);
            ide.gotoDefinition();
            log("getText -> " + ide.statusText + "\n");
        } else if (step == 4) {
            int off = ide.activeDoc().editor.getText().indexOf("onBtnHelloClick");
            TextPosition p = ide.activeDoc().editor.document.offsetToPosition(off + 3);
            ide.activeDoc().editor.goTo(p.line, p.column);
            ide.gotoDefinition();
            IdeDoc d = ide.activeDoc();
            TextPosition q = d.editor.document.offsetToPosition(d.editor.caret.offset);
            log("onBtnHelloClick -> " + d.path + " line " + (q.line + 1) + " status=" + ide.statusText + "\n");
        }
    }

    static void tools2Step() {
        if (step == 1) {
            ide.openFile(ide.project.dir + "/src/MainForm.java");
        } else if (step == 2) {
            ide.runSearch("text");
            log("search: " + ide.search.root.kids.size() + " hits\n");
            ide.window.snapshot("/tmp/ide-tools2-search.png");
        } else if (step == 3) {
            ide.searchActivate(ide.search.root.kids.get(0));
            TextPosition p = ide.activeDoc().editor.document.offsetToPosition(ide.activeDoc().editor.caret.offset);
            log("search jump -> " + ide.activeDoc().title + " " + (p.line + 1) + ":" + p.column + "\n");
            ide.zoomEditors(3);
        } else if (step == 4) {
            ide.window.snapshot("/tmp/ide-tools2-zoom.png");
            ide.zoomEditors(0);
            IdeItem fi = ide.explorer.findByKey(ide.project.dir + "/src/Main.java");
            ide.renameTo(fi, "App.java");
            log("rename: " + Files.exists(FsPath.of(ide.project.dir + "/src/App.java")) + " " + Files.exists(FsPath.of(ide.project.dir + "/src/Main.java")) + "\n");
        } else if (step == 5) {
            ide.window.snapshot("/tmp/ide-tools2-rename.png");
            ArrayList<String> rp = new ArrayList<String>();
            ArrayList<String> rt = new ArrayList<String>();
            int total = ide.planRename("lblMessage", "lblResult", rp, rt);
            log("plan: " + total + " in " + rp.size() + " files\n");
            ide.applyRename(rp, rt);
            log("MainForm now has lblResult: " + (ide.tabs.docs.size() > 0) + "\n");
        } else if (step == 6) {
            ide.build(false);
        } else if (step == 14) {
            log("after rename build: " + ide.statusText + " diags=" + ide.diagCount + "\n");
        }
    }

    static void toolsStep() {
        if (step == 1) {
            ide.openFile(ide.project.dir + "/src/MainForm.java");
        } else if (step == 2) {
            ide.findBar.open(true);
            ide.findBar.find.setText("lbl");
            ide.findBar.repl.setText("label");
            ide.findBar.queryChanged();
        } else if (step == 3) {
            ide.window.snapshot("/tmp/ide-tools-find.png");
            log("find: " + ide.findBar.count.getText() + "\n");
            ide.findBar.replaceOne();
            log("after replaceOne: " + ide.activeDoc().editor.getText().indexOf("label.setText") + " " + ide.findBar.count.getText() + "\n");
            ide.findBar.close();
            ide.quickOpenFile();
            ide.quick.query = "form";
            ide.quick.refilter();
            ide.quick.repaint();
        } else if (step == 4) {
            ide.window.snapshot("/tmp/ide-tools-quick.png");
            log("quick: " + ide.quick.shown.size() + " items\n");
            ide.quick.close();
            ide.quickSymbol();
        } else if (step == 5) {
            ide.window.snapshot("/tmp/ide-tools-symbol.png");
            ide.quick.accept(1);
            TextPosition p = ide.activeDoc().editor.document.offsetToPosition(ide.activeDoc().editor.caret.offset);
            log("symbol -> line " + (p.line + 1) + "\n");
            ide.navBack();
            TextPosition q = ide.activeDoc().editor.document.offsetToPosition(ide.activeDoc().editor.caret.offset);
            log("back -> line " + (q.line + 1) + "\n");
        }
    }

    static void completeStep() {
        if (step == 1) {
            ide.openFile(ide.project.dir + "/src/MainForm.java");
        } else if (step == 2) {
            tryComplete("        lblMessage.", "label");
        } else if (step == 3) {
            ide.suggest.hide();
            tryComplete("        txtName.getText().", "chain");
        } else if (step == 4) {
            ide.suggest.hide();
            tryComplete("        lblM", "word");
        } else if (step == 5) {
            ide.suggest.accept(ide.suggest.sel);
            log("after accept: " + ide.activeDoc().editor.getText().substring(0, 0) + "ok\n");
            ide.activeDoc().editor.goTo(11, 0);
            ide.window.snapshot("/tmp/ide-complete-final.png");
        }
    }

    static void typeKeys(CodeEditor e, String s, boolean viaText) {
        int i = 0;
        while (i < s.length()) {
            if (viaText) {
                UiTest.typeText(e, s.substring(i, i + 1), false);
            } else {
                UiTest.keyPress(e, s.charAt(i));
            }
            i = i + 1;
        }
    }

    static void report(String what) {
        IdeSuggest p = ide.suggest;
        String s = what + ": ";
        if (p.isOpen) {
            s = s + p.shown.size() + " items:";
            int i = 0;
            while (i < p.shown.size() && i < 5) {
                s = s + " " + p.shown.get(i).text;
                i = i + 1;
            }
        } else {
            s = s + "no popup";
        }
        log(s + "\n");
    }

    /** Types through the real key / text-input paths and checks that the list pops up on its own. */
    static void autotypeStep() {
        IdeDoc d = ide.activeDoc();
        if (step == 1) {
            ide.openFile(ide.project.dir + "/src/MainForm.java");
            return;
        }
        CodeEditor e = d.editor;
        if (step == 2) {
            int at = e.getText().indexOf("buildUi();");
            e.moveCaret(at + 10, 0);
            UiTest.keyPress(e, 13);
            typeKeys(e, "lblM", false);
            report("key path 'lblM'");
            String[] pre = new String[1];
            ide.indexContext(d);
            ArrayList<IdeItem> cs = IdeComplete.candidates(e.getText(), e.caret.offset, pre);
            log("direct candidates=" + cs.size() + " pre=" + pre[0] + " word=" + e.wordBeforeCaret() + " inStr=" + e.inStringOrComment(e.caret.offset) + " typedListener=" + (e.typedListener != null) + "\n");
        } else if (step == 3) {
            ide.suggest.hide();
            typeKeys(e, "x", false);
            ide.suggest.hide();
            e.moveCaret(e.document.getLength(), 0);
            int at = e.getText().indexOf("lblM");
            e.moveCaret(at, 0);
            e.executeCommand(EditorCommand.SELECT_LINE);
            typeKeys(e, "txtEditor.", true);
            report("text path 'txtEditor.'");
            ide.window.snapshot("/tmp/ide-autotype-dot.png");
        } else if (step == 4) {
            ide.suggest.hide();
            typeKeys(e, "txtF", true);
            report("text path 'txtF'");
            ide.window.snapshot("/tmp/ide-autotype.png");
        }
    }

    static void log(String s) {
        logText = logText + s;
        Files.writeString(FsPath.of("/tmp/ide-edit.log"), logText);
    }

    /** Find / goto / undo checks that need no mouse. */
    static void editStep(IdeDoc d) {
        if (d == null || d.kind != 0) {
            return;
        }
        CodeEditor e = d.editor;
        if (step == 2) {
            ide.lastFind = "buildUi";
            ide.findAgain();
            TextPosition p = e.document.offsetToPosition(e.caret.offset);
            log("find: line " + (p.line + 1) + " col " + p.column + "\n");
        } else if (step == 3) {
            int before = e.document.getLength();
            e.insertText("// hello\n");
            int mid = e.document.getLength();
            ide.designerUndo();
            int after = e.document.getLength();
            ide.designerRedo();
            int redone = e.document.getLength();
            log("len before=" + before + " inserted=" + mid + " undone=" + after + " redone=" + redone + "\n");
        } else if (step == 4) {
            e.goTo(0, 0);
            e.executeCommand(EditorCommand.SELECT_ALL);
            log("selectAll sel=" + e.selection.toRange().length() + " of " + e.document.getLength() + "\n");
            ide.window.snapshot("/tmp/ide-edit.png");
        }
    }

    static void tick() {
        dump();
        step = step + 1;
        IdeDoc d = ide.activeDoc();
        String mode = Env.get("IDE_DEMO");
        if (mode != null && mode.equals("gen")) {
            if (step == 1) {
                ide.openFile(ide.project.dir + "/src/MainForm.jnui");
            } else if (step == 2) {
                ide.saveDesigner(ide.activeDoc());
                ide.build(false);
            } else if (step == 6) {
                ide.window.snapshot("/tmp/ide-gen.png");
            }
            return;
        }
        if (mode != null && mode.equals("snap")) {
            if (step == 1) {
                ide.openFile(ide.project.dir + "/src/MainForm.jnui");
            } else if (step == 3) {
                ide.window.snapshot("/tmp/ide-designer.png");
                ide.activeDoc().designer.togglePreview();
            } else if (step == 4) {
                ide.activeDoc().designer.preview.window.snapshot("/tmp/ide-preview.png");
                ide.activeDoc().designer.togglePreview();
            }
            return;
        }
        if (mode != null && mode.equals("tpl")) {
            if (step == 1) {
                IdeDialog.template = DsUtil.parseInt(Env.get("IDE_TPL"), 2);
                ide.createProject("/tmp/ide-projects", Env.get("IDE_TPL_NAME"));
            } else if (step == 2) {
                ide.build(true);
            } else if (step == 6) {
                ide.stopAll();
            }
            return;
        }
        if (mode != null && mode.equals("def")) {
            defStep();
            return;
        }
        if (mode != null && mode.equals("restore")) {
            if (step == 1) {
                ide.restoreSession();
            } else if (step == 2) {
                log("restored: project=" + (ide.project == null ? "none" : ide.project.name) + " tabs=" + ide.tabs.docs.size()
                        + " active=" + (ide.activeDoc() == null ? "-" : ide.activeDoc().title) + "\n");
                ide.window.snapshot("/tmp/ide-restore.png");
            }
            return;
        }
        if (mode != null && mode.equals("extern")) {
            if (step == 1) {
                ide.openFile(ide.project.dir + "/src/MainForm.java");
            } else if (step == 2) {
                Files.writeString(FsPath.of(ide.project.dir + "/src/MainForm.java"), "// changed outside\n" + ide.activeDoc().editor.getText());
            } else if (step == 4) {
                log("extern: first line=[" + ide.activeDoc().editor.document.getLine(0) + "] dirty=" + ide.activeDoc().isDirty() + " status=" + ide.statusText + "\n");
            }
            return;
        }
        if (mode != null && mode.equals("check")) {
            if (step == 1) {
                ide.openFile(ide.project.dir + "/src/MainForm.java");
            } else if (step == 3) {
                log("clean: diag=" + ide.diagCount + "\n");
                CodeEditor e = ide.activeDoc().editor;
                e.goTo(12, 0);
                e.insertText("        int q = \"s\"; undefinedCall();\n");
            } else if (step == 6) {
                log("with errors: diag=" + ide.diagCount + " problems=" + ide.problems.root.kids.size() + "\n");
                ide.window.snapshot("/tmp/ide-check.png");
                ide.activeDoc().editor.executeCommand(EditorCommand.UNDO);
            } else if (step == 9) {
                log("fixed: diag=" + ide.diagCount + "\n");
            }
            return;
        }
        if (mode != null && mode.equals("tools2")) {
            tools2Step();
            return;
        }
        if (mode != null && mode.equals("tools")) {
            toolsStep();
            return;
        }
        if (mode != null && mode.equals("complete")) {
            completeStep();
            return;
        }
        if (mode != null && mode.equals("autotype")) {
            autotypeStep();
            return;
        }
        if (mode != null && mode.equals("edit")) {
            if (step == 1) {
                ide.openFile(ide.project.dir + "/src/MainForm.java");
                return;
            }
            editStep(ide.activeDoc());
            return;
        }
        if (mode != null && mode.equals("run")) {
            if (step == 1) {
                ide.build(true);
            } else if (step == 5) {
                ide.stopAll();
            }
            return;
        }
        if (ide.project == null) {
            if (step == 1) {
                ide.newProject();
            } else if (step == 2 && IdeDialog.last != null) {
                IdeDialog.last.snapshot("/tmp/ide-dialog.png");
                IdeDialog.last.close();
                IdeDialog.settings(ide.window, null);
            } else if (step == 3 && IdeDialog.last != null) {
                IdeDialog.last.snapshot("/tmp/ide-settings.png");
                IdeDialog.last.close();
                ide.newForm(null);
            }
            return;
        }
        if (d != null && d.kind == 1 && d.ds.findByName("btnHello") == null) {
            ArrayList<DsNode> all = d.ds.allNodes();
            if (step == 1) {
                int i = 0;
                while (i < all.size()) {
                    if (all.get(i).type.equals("ListView")) {
                        d.designer.selectNode(all.get(i));
                    }
                    i = i + 1;
                }
            } else if (step == 2) {
                d.designer.setDeviceIndex(2);
                d.designer.deviceBox.setSelectedIndex(2);
            } else if (step == 3) {
                d.designer.setDeviceIndex(0);
                d.designer.deviceBox.setSelectedIndex(0);
                int i = 0;
                while (i < all.size()) {
                    if (all.get(i).type.equals("Canvas")) {
                        d.designer.selectNode(all.get(i));
                    }
                    i = i + 1;
                }
            } else if (step == 4) {
                d.designer.togglePreview();
            } else if (step == 5) {
                d.designer.preview.window.snapshot("/tmp/ide-preview.png");
                d.designer.togglePreview();
            }
            return;
        }
        if (step == 1) {
            if (d != null && d.kind == 1) {
                d.designer.selectNode(d.ds.findByName("btnHello"));
            }
        } else if (step == 2) {
            if (d != null && d.kind == 1) {
                d.designer.surface.externalDrop("TextArea", 220, 300);
            }
        } else if (step == 3) {
            if (d != null && d.kind == 1) {
                d.designer.togglePreview();
            }
        } else if (step == 4) {
            if (d != null && d.kind == 1 && d.designer.preview.window != null) {
                d.designer.preview.window.snapshot("/tmp/ide-preview.png");
                d.designer.togglePreview();
                d.designer.gotoEvent(d.ds.findByName("btnHello"), DsMeta.find("Button").event("click"));
            }
        } else if (step == 5) {
            if (d != null && d.kind == 0) {
                String t = d.editor.getText();
                d.editor.setText(DsGen.replaceAll(t, "lblMessage.setText(", "lblMessage.setTxt("));
                ide.build(false);
            }
        } else if (step == 7) {
            if (ide.problems.root.kids.size() > 0) {
                ide.problemActivate(ide.problems.root.kids.get(0));
            }
        } else if (step == 9) {
            ide.stopAll();
        }
    }
}
