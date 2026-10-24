import jnative.ui.*;

/**
 * The completion list window: a prefix box on top of a keyboard-navigable list. Typing filters, Up/Down
 * moves, Enter / double click inserts, Esc closes.
 *
 * @author 张晓达
 */
public class IdeCompletePopup {
    static IdeCompletePopup last;
    Window win;
    TextField box;
    IdeTree list;
    ArrayList<IdeItem> all;
    CodeEditor editor;
    int caret;
    int prefixLen;

    static void show(Window owner, ArrayList<IdeItem> items, String prefix, CodeEditor ed) {
        IdeCompletePopup p = new IdeCompletePopup();
        p.all = items;
        p.editor = ed;
        p.caret = ed.caret.offset;
        p.prefixLen = prefix.length();
        p.build(owner, prefix);
        last = p;
    }

    void build(Window owner, String prefix) {
        win = new Window("代码补全", 460, 340);
        win.setDialogStyle(owner);
        Panel p = new Panel();
        box = new TextField(prefix);
        p.add(box);
        box.setBounds(12, 12, 436, 28);
        list = new IdeTree();
        list.flat = true;
        list.rowH = 22;
        list.emptyText = "没有匹配项";
        list.onActivate = it -> accept(it);
        p.add(list);
        list.setBounds(12, 48, 436, 240);
        Button ok = new Button("插入");
        Button cancel = new Button("取消");
        p.add(ok);
        p.add(cancel);
        ok.setBounds(270, 298, 80, 30);
        cancel.setBounds(360, 298, 80, 30);
        ok.onClick(() -> acceptSelected());
        cancel.onClick(() -> win.close());
        box.onChange(e -> refilter());
        box.onKeyDown(e -> boxKey(e));
        win.setDefaultButton(ok);
        win.setCancelButton(cancel);
        win.setContent(p);
        win.centerOnOwner();
        refilter();
        win.show();
        box.requestFocus();
    }

    void refilter() {
        String q = box.getText();
        IdeItem root = new IdeItem("", "", "");
        int n = 0;
        int i = 0;
        while (i < all.size() && n < 300) {
            IdeItem it = all.get(i);
            if (IdeComplete.matches(it.key, q) || IdeComplete.matches(it.text, q)) {
                IdeItem c = new IdeItem(it.text, it.icon, it.key);
                c.hint = it.hint;
                c.tag = it.tag;
                root.add(c);
                n = n + 1;
            }
            i = i + 1;
        }
        list.setRoot(root);
        if (root.kids.size() > 0) {
            list.select(root.kids.get(0), true);
        }
    }

    void boxKey(KeyEvent e) {
        int idx = list.selected == null ? -1 : list.rows.indexOf(list.selected);
        if (e.keyCode == KeyCode.DOWN && idx + 1 < list.rows.size()) {
            list.select(list.rows.get(idx + 1), true);
        } else if (e.keyCode == KeyCode.UP && idx > 0) {
            list.select(list.rows.get(idx - 1), true);
        }
    }

    void acceptSelected() {
        if (list.selected != null) {
            accept(list.selected);
        }
    }

    /** Replaces the identifier prefix before the caret with the chosen text. */
    void accept(IdeItem it) {
        win.close();
        int start = caret - prefixLen;
        editor.selection.anchor = start;
        editor.selection.active = caret;
        editor.caret.offset = caret;
        editor.replaceSelection(it.key);
        editor.requestFocus();
    }
}
