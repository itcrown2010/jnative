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
        ide.completeDialog();
        String s = label + " [" + typed.trim() + "]: ";
        IdeCompletePopup p = IdeCompletePopup.last;
        if (p != null && p.win != null && p.list.rows.size() > 0) {
            s = s + p.list.rows.size() + " items:";
            int i = 0;
            while (i < p.list.rows.size() && i < 6) {
                s = s + " " + p.list.rows.get(i).text;
                i = i + 1;
            }
            p.win.snapshot("/tmp/ide-complete-" + label + ".png");
        } else {
            s = s + "no popup";
        }
        log(s + "\n");
    }

    static void completeStep() {
        if (step == 1) {
            ide.openFile(ide.project.dir + "/src/MainForm.java");
        } else if (step == 2) {
            tryComplete("        lblMessage.", "label");
        } else if (step == 3) {
            if (IdeCompletePopup.last != null) {
                IdeCompletePopup.last.win.close();
                IdeCompletePopup.last = null;
            }
            tryComplete("        txtName.getText().", "chain");
        } else if (step == 4) {
            if (IdeCompletePopup.last != null) {
                IdeCompletePopup.last.win.close();
                IdeCompletePopup.last = null;
            }
            tryComplete("        lblM", "word");
        } else if (step == 5) {
            IdeCompletePopup.last.acceptSelected();
            log("after accept: " + ide.activeDoc().editor.getText().substring(0, 0) + "ok\n");
            ide.activeDoc().editor.goTo(11, 0);
            ide.window.snapshot("/tmp/ide-complete-final.png");
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
        if (mode != null && mode.equals("complete")) {
            completeStep();
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
