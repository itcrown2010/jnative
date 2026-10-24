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

    static void tick() {
        dump();
        step = step + 1;
        IdeDoc d = ide.activeDoc();
        String mode = Env.get("IDE_DEMO");
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
