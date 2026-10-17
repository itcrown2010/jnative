/**
 * "Go To Line" dialog (Edit > Go To..., Ctrl+G).
 *
 * @author 张晓达
 */
class NpGotoDialog {
    NpWindow owner;
    Window win;
    Panel root;
    TextField field;
    Button btnGo;
    Button btnCancel;

    NpGotoDialog(NpWindow o) {
        owner = o;
        int bg = NpUi.rgb(240, 240, 240);
        win = new Window("Go To Line", 290, 112);
        win.setOwnerWindow(owner.win);
        root = new Panel();
        root.setBackgroundRgba(bg);
        win.setBackgroundRgba(bg);
        win.setContent(root);

        NpUi.label(root, "Line number:", 14, 12, 120, 18);
        field = new TextField("1");
        root.add(field);
        field.setBounds(14, 34, 260, 23);
        btnGo = NpUi.button(root, "Go To", 98, 74, 86, 24);
        btnCancel = NpUi.button(root, "Cancel", 190, 74, 86, 24);

        btnGo.onClick(() -> {
            onGo();
        });
        btnCancel.onClick(() -> {
            hideDialog();
        });
        win.setCancelButton(btnCancel);
        win.onKeyDown((ev) -> {
            if (ev != null && ev.keyCode == KeyCode.ENTER) {
                onGo();
            }
        });
        win.onClose(() -> {
            win.vetoClose();
            hideDialog();
        });
        win.setDialogStyle(owner.win);
    }

    void open() {
        field.setText("" + owner.currentLine());
        win.centerOnOwner();
        win.show();
        UiNative.windowActivate(win.nativeId);
        field.requestFocus();
        field.selectAll();
    }

    void hideDialog() {
        if (win.disposed == 0 && win.isVisible()) {
            win.hide();
        }
        if (owner.win.disposed == 0) {
            owner.edit.requestFocus();
        }
    }

    void onGo() {
        field.syncTextFromNative();
        int n = NpUi.parseInt(field.getText());
        if (n < 1) {
            n = 0;
        }
        if (owner.gotoLine(n, win)) {
            hideDialog();
        } else {
            field.requestFocus();
            field.selectAll();
        }
    }
}
