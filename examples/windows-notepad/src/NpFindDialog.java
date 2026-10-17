/**
 * Find (mode 0) and Replace (mode 1) dialogs — modeless, owned by the Notepad window,
 * laid out like the classic Notepad dialogs.
 *
 * @author 张晓达
 */
class NpFindDialog {
    NpWindow owner;
    int mode;
    Window win;
    Panel root;
    TextField findField;
    TextField replField;
    CheckBox cbCase;
    CheckBox cbWrap;
    RadioButton rbUp;
    RadioButton rbDown;
    Button btnFind;
    Button btnReplace;
    Button btnReplaceAll;
    Button btnCancel;

    NpFindDialog(NpWindow o, int m) {
        owner = o;
        mode = m;
        int bg = NpUi.rgb(240, 240, 240);
        int h = 142;
        String title = "Find";
        if (mode == 1) {
            h = 174;
            title = "Replace";
        }
        win = new Window(title, 400, h);
        win.setOwnerWindow(owner.win);
        root = new Panel();
        root.setBackgroundRgba(bg);
        win.setBackgroundRgba(bg);
        win.setContent(root);

        NpUi.label(root, "Find what:", 12, 18, 80, 20);
        findField = new TextField("");
        root.add(findField);
        if (mode == 1) {
            findField.setBounds(96, 14, 182, 23);
            NpUi.label(root, "Replace with:", 12, 48, 84, 20);
            replField = new TextField("");
            root.add(replField);
            replField.setBounds(96, 44, 182, 23);
        } else {
            findField.setBounds(80, 14, 198, 23);
        }

        btnFind = NpUi.button(root, "Find Next", 290, 12, 98, 24);
        if (mode == 1) {
            btnReplace = NpUi.button(root, "Replace", 290, 42, 98, 24);
            btnReplaceAll = NpUi.button(root, "Replace All", 290, 72, 98, 24);
            btnCancel = NpUi.button(root, "Cancel", 290, 102, 98, 24);
        } else {
            btnCancel = NpUi.button(root, "Cancel", 290, 42, 98, 24);
        }

        int cy = 84;
        if (mode == 1) {
            cy = 106;
        }
        cbCase = new CheckBox("Match case");
        root.add(cbCase);
        cbCase.setBounds(12, cy, 140, 20);
        cbWrap = new CheckBox("Wrap around");
        root.add(cbWrap);
        cbWrap.setBounds(12, cy + 24, 140, 20);

        if (mode == 0) {
            int frameColor = NpUi.rgb(170, 170, 170);
            NpUi.frame(root, 150, 76, 130, 50, frameColor);
            Label dl = NpUi.label(root, "Direction", 158, 68, 58, 16);
            dl.setBackgroundRgba(bg);
            ToggleGroup g = new ToggleGroup();
            rbUp = new RadioButton("Up");
            rbDown = new RadioButton("Down");
            g.add(rbUp);
            g.add(rbDown);
            root.add(rbUp);
            root.add(rbDown);
            rbUp.setBounds(160, 90, 50, 20);
            rbDown.setBounds(216, 90, 56, 20);
        }

        btnFind.onClick(() -> {
            onFindNext();
        });
        if (mode == 1) {
            btnReplace.onClick(() -> {
                onReplace();
            });
            btnReplaceAll.onClick(() -> {
                onReplaceAll();
            });
        }
        btnCancel.onClick(() -> {
            hideDialog();
        });
        win.setCancelButton(btnCancel);
        win.onKeyDown((ev) -> {
            if (ev != null && ev.keyCode == KeyCode.ENTER) {
                onFindNext();
            }
        });
        win.onClose(() -> {
            win.vetoClose();
            hideDialog();
        });
        win.setDialogStyle(owner.win);
    }

    void open(String preset) {
        if (preset.length() > 0 && preset.indexOf("\r") < 0 && preset.indexOf("\n") < 0) {
            NpWindow.findText = preset;
        }
        findField.setText(NpWindow.findText);
        if (mode == 1) {
            replField.setText(NpWindow.replaceText);
        }
        cbCase.setSelected(NpWindow.findMatchCase != 0);
        cbWrap.setSelected(NpWindow.findWrapAround != 0);
        if (mode == 0) {
            if (NpWindow.findDown != 0) {
                rbDown.setSelected(true);
            } else {
                rbUp.setSelected(true);
            }
        }
        win.centerOnOwner();
        win.show();
        UiNative.windowActivate(win.nativeId);
        findField.requestFocus();
        findField.selectAll();
    }

    void hideDialog() {
        if (win.disposed == 0 && win.isVisible()) {
            win.hide();
        }
        if (owner.win.disposed == 0) {
            owner.edit.requestFocus();
        }
    }

    /** Pull the dialog fields into the shared search parameters. */
    void collect() {
        findField.syncTextFromNative();
        NpWindow.findText = findField.getText();
        if (mode == 1) {
            replField.syncTextFromNative();
            NpWindow.replaceText = replField.getText();
        }
        if (cbCase.isSelected()) {
            NpWindow.findMatchCase = 1;
        } else {
            NpWindow.findMatchCase = 0;
        }
        if (cbWrap.isSelected()) {
            NpWindow.findWrapAround = 1;
        } else {
            NpWindow.findWrapAround = 0;
        }
        if (mode == 0) {
            if (rbUp.isSelected()) {
                NpWindow.findDown = 0;
            } else {
                NpWindow.findDown = 1;
            }
        } else {
            NpWindow.findDown = 1;
        }
    }

    void onFindNext() {
        collect();
        if (NpWindow.findText.length() == 0) {
            return;
        }
        owner.findNext(NpWindow.findDown, win);
    }

    void onReplace() {
        collect();
        owner.replaceOne(win);
    }

    void onReplaceAll() {
        collect();
        owner.replaceAll(win);
    }
}
