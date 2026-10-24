/**
 * A design document (.jnui): the Form node tree plus selection, dirty flag and undo/redo.
 * Persisted as readable, diff-friendly JSON.
 *
 * @author 张晓达
 */
public class DsDoc {
    DsNode root;
    String path = "";
    String className = "MainForm";
    boolean dirty = false;
    int nextUid = 1;
    ArrayList<String> undoStack = new ArrayList<String>();
    ArrayList<String> redoStack = new ArrayList<String>();
    String selectedName = "";
    int version = 0; // bumped on every change; views poll it
    String coalesce = ""; // property being typed into; consecutive edits share one undo step

    DsDoc(String cls) {
        className = cls;
        root = newNode("Form");
        root.name = cls;
        root.set("title", cls);
    }

    DsNode newNode(String type) {
        DsNode n = new DsNode(type);
        n.uid = nextUid;
        nextUid = nextUid + 1;
        return n;
    }

    /** Default blank form: a root VBox ready to receive controls. */
    static DsDoc blank(String cls) {
        DsDoc d = new DsDoc(cls);
        DsNode content = d.newNode("VBox");
        content.name = "root";
        d.root.add(content);
        d.dirty = false;
        return d;
    }

    DsNode content() {
        if (root.children.size() == 0) {
            return null;
        }
        return root.children.get(0);
    }

    DsNode selected() {
        if (selectedName.length() == 0) {
            return null;
        }
        return findByName(selectedName);
    }

    void select(DsNode n) {
        if (n == null) {
            selectedName = "";
        } else {
            selectedName = n.name;
        }
    }

    DsNode findByName(String name) {
        return findIn(root, name);
    }

    static DsNode findIn(DsNode n, String name) {
        if (n.name.equals(name)) {
            return n;
        }
        int i = 0;
        while (i < n.children.size()) {
            DsNode r = findIn(n.children.get(i), name);
            if (r != null) {
                return r;
            }
            i = i + 1;
        }
        return null;
    }

    static void collect(DsNode n, ArrayList<DsNode> out) {
        out.add(n);
        int i = 0;
        while (i < n.children.size()) {
            collect(n.children.get(i), out);
            i = i + 1;
        }
    }

    ArrayList<DsNode> allNodes() {
        ArrayList<DsNode> out = new ArrayList<DsNode>();
        collect(root, out);
        return out;
    }

    boolean nameTaken(String name) {
        return findByName(name) != null;
    }

    String uniqueName(String type) {
        String base = DsUtil.lowerFirst(type);
        int i = 1;
        while (nameTaken(base + i)) {
            i = i + 1;
        }
        return base + i;
    }

    /** Create a node of the given type with a unique name and catalog defaults. */
    DsNode create(String type) {
        DsNode n = newNode(type);
        n.name = uniqueName(type);
        return n;
    }

    // ---- change tracking ----

    /** Call before any mutation. */
    void beginChange() {
        coalesce = "";
        undoStack.add(toJson());
        if (undoStack.size() > 100) {
            undoStack.remove(0);
        }
        redoStack.clear();
    }

    void changed() {
        dirty = true;
        version = version + 1;
    }

    void clearHistory() {
        undoStack.clear();
        redoStack.clear();
        coalesce = "";
        dirty = false;
    }

    boolean canUndo() {
        return undoStack.size() > 0;
    }

    boolean canRedo() {
        return redoStack.size() > 0;
    }

    boolean undo() {
        if (undoStack.size() == 0) {
            return false;
        }
        redoStack.add(toJson());
        String s = undoStack.remove(undoStack.size() - 1);
        restore(s);
        return true;
    }

    boolean redo() {
        if (redoStack.size() == 0) {
            return false;
        }
        undoStack.add(toJson());
        String s = redoStack.remove(redoStack.size() - 1);
        restore(s);
        return true;
    }

    void restore(String json) {
        DsNode r = parseNode(json);
        if (r != null) {
            root = r;
            reassignUids(root);
            if (selectedName.length() > 0 && findByName(selectedName) == null) {
                selectedName = "";
            }
        }
        changed();
    }

    void reassignUids(DsNode n) {
        n.uid = nextUid;
        nextUid = nextUid + 1;
        int i = 0;
        while (i < n.children.size()) {
            reassignUids(n.children.get(i));
            i = i + 1;
        }
    }

    // ---- structural edits (each performs beginChange itself) ----

    DsNode addChild(DsNode parent, String type, int index) {
        beginChange();
        DsNode n = create(type);
        parent.insert(index, n);
        applyDefaultsFor(n, parent);
        select(n);
        changed();
        return n;
    }

    /** Sensible starting sizes/positions/names for freshly dropped controls. */
    void applyDefaultsFor(DsNode n, DsNode parent) {
        DsType pt = DsMeta.find(parent.type);
        if (pt.container == DsType.TABS) {
            n.set("tabTitle", "Tab " + parent.children.size());
        }
        if (pt.container == DsType.GRID) {
            int col = 0;
            int row = 0;
            int guard = 0;
            while (cellUsed(parent, n, col, row) && guard < 200) {
                col = col + 1;
                if (col >= 3) {
                    col = 0;
                    row = row + 1;
                }
                guard = guard + 1;
            }
            n.set("col", "" + col);
            n.set("row", "" + row);
        }
    }

    boolean cellUsed(DsNode grid, DsNode self, int col, int row) {
        int i = 0;
        while (i < grid.children.size()) {
            DsNode c = grid.children.get(i);
            if (c != self && c.getInt("col") == col && c.getInt("row") == row) {
                return true;
            }
            i = i + 1;
        }
        return false;
    }

    boolean canAccept(DsNode parent, DsNode child) {
        DsType pt = DsMeta.find(parent.type);
        if (pt == null || !pt.isContainer()) {
            return false;
        }
        if (child != null && child.isAncestorOf(parent)) {
            return false;
        }
        if (pt.container == DsType.SINGLE && parent.children.size() >= 1) {
            return child != null && child.parent == parent;
        }
        if (pt.container == DsType.SPLIT && parent.children.size() >= 2) {
            return child != null && child.parent == parent;
        }
        return true;
    }

    void deleteNode(DsNode n) {
        if (n == null || n.parent == null || n == root) {
            return;
        }
        if (n.parent == root) {
            return; // the form's content root cannot be deleted
        }
        beginChange();
        DsNode p = n.parent;
        p.remove(n);
        if (selectedName.length() > 0 && findByName(selectedName) == null) {
            select(p);
        }
        changed();
    }

    void moveNode(DsNode n, DsNode newParent, int index) {
        if (n == null || n.parent == null || n.parent == root) {
            return;
        }
        if (!canAccept(newParent, n)) {
            return;
        }
        beginChange();
        DsNode old = n.parent;
        int oldIdx = old.children.indexOf(n);
        old.remove(n);
        if (old == newParent && oldIdx < index) {
            index = index - 1;
        }
        newParent.insert(index, n);
        changed();
    }

    DsNode duplicate(DsNode n) {
        if (n == null || n.parent == null || n.parent == root) {
            return null;
        }
        beginChange();
        DsNode c = n.deepCopy();
        renameTree(c);
        n.parent.insert(n.indexInParent() + 1, c);
        DsType pt = DsMeta.find(n.parent.type);
        if (pt.container == DsType.ABSOLUTE) {
            c.set("x", "" + (n.getInt("x") + 16));
            c.set("y", "" + (n.getInt("y") + 16));
        }
        select(c);
        changed();
        return c;
    }

    void renameTree(DsNode n) {
        n.name = uniqueName(n.type);
        n.evNames.clear();
        n.evHandlers.clear();
        int i = 0;
        while (i < n.children.size()) {
            renameTree(n.children.get(i));
            i = i + 1;
        }
    }

    void setProp(DsNode n, String key, String value) {
        if (n.get(key).equals(value)) {
            return;
        }
        beginChange();
        n.set(key, value);
        changed();
    }

    /** Property edit from a text box: keystrokes on the same property form one undo step. */
    void setPropLive(DsNode n, String key, String value) {
        String ck = n.name + "." + key;
        if (!ck.equals(coalesce)) {
            if (n.get(key).equals(value)) {
                return;
            }
            beginChange();
            coalesce = ck;
        }
        n.set(key, value);
        changed();
    }

    void endCoalesce() {
        coalesce = "";
    }

    /** Set several properties as one undo step. */
    void setProps(DsNode n, String k1, String v1, String k2, String v2) {
        beginChange();
        n.set(k1, v1);
        n.set(k2, v2);
        changed();
    }

    boolean rename(DsNode n, String newName) {
        if (!DsUtil.isIdent(newName)) {
            return false;
        }
        DsNode other = findByName(newName);
        if (other != null && other != n) {
            return false;
        }
        if (n.name.equals(newName)) {
            return true;
        }
        beginChange();
        String old = n.name;
        n.name = newName;
        if (selectedName.equals(old)) {
            selectedName = newName;
        }
        changed();
        return true;
    }

    void bindEvent(DsNode n, String event, String handler) {
        beginChange();
        n.setHandler(event, handler);
        changed();
    }

    // ---- validation ----

    ArrayList<String> validate() {
        ArrayList<String> out = new ArrayList<String>();
        ArrayList<DsNode> all = allNodes();
        int i = 0;
        while (i < all.size()) {
            DsNode n = all.get(i);
            if (n != root) {
                if (!DsUtil.isIdent(n.name)) {
                    out.add("组件名称无效: '" + n.name + "'");
                }
                int j = i + 1;
                while (j < all.size()) {
                    if (all.get(j).name.equals(n.name)) {
                        out.add("组件名称重复: " + n.name);
                    }
                    j = j + 1;
                }
            }
            int e = 0;
            while (e < n.evNames.size()) {
                String h = n.evHandlers.get(e);
                if (!DsUtil.isIdent(h)) {
                    out.add(n.name + "." + n.evNames.get(e) + " 的处理方法名无效: '" + h + "'");
                }
                e = e + 1;
            }
            i = i + 1;
        }
        return out;
    }

    // ---- JSON ----

    static void indent(StringBuilder sb, int level) {
        int i = 0;
        while (i < level) {
            sb.append("  ");
            i = i + 1;
        }
    }

    static void writeNode(StringBuilder sb, DsNode n, int level) {
        indent(sb, level);
        sb.append("{\n");
        indent(sb, level + 1);
        sb.append("\"type\": " + DsUtil.jsonString(n.type));
        sb.append(",\n");
        indent(sb, level + 1);
        sb.append("\"name\": " + DsUtil.jsonString(n.name));
        if (n.keys.size() > 0) {
            sb.append(",\n");
            indent(sb, level + 1);
            sb.append("\"props\": {\n");
            int i = 0;
            while (i < n.keys.size()) {
                indent(sb, level + 2);
                sb.append(DsUtil.jsonString(n.keys.get(i)) + ": " + DsUtil.jsonString(n.vals.get(i)));
                if (i + 1 < n.keys.size()) {
                    sb.append(",");
                }
                sb.append("\n");
                i = i + 1;
            }
            indent(sb, level + 1);
            sb.append("}");
        }
        if (n.evNames.size() > 0) {
            sb.append(",\n");
            indent(sb, level + 1);
            sb.append("\"events\": {\n");
            int i = 0;
            while (i < n.evNames.size()) {
                indent(sb, level + 2);
                sb.append(DsUtil.jsonString(n.evNames.get(i)) + ": " + DsUtil.jsonString(n.evHandlers.get(i)));
                if (i + 1 < n.evNames.size()) {
                    sb.append(",");
                }
                sb.append("\n");
                i = i + 1;
            }
            indent(sb, level + 1);
            sb.append("}");
        }
        if (n.children.size() > 0) {
            sb.append(",\n");
            indent(sb, level + 1);
            sb.append("\"children\": [\n");
            int i = 0;
            while (i < n.children.size()) {
                writeNode(sb, n.children.get(i), level + 2);
                if (i + 1 < n.children.size()) {
                    sb.append(",");
                }
                sb.append("\n");
                i = i + 1;
            }
            indent(sb, level + 1);
            sb.append("]");
        }
        sb.append("\n");
        indent(sb, level);
        sb.append("}");
    }

    String toJson() {
        StringBuilder sb = new StringBuilder();
        writeNode(sb, root, 0);
        return sb.toString();
    }

    String toFileText() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"jnui\": 1,\n  \"form\": ");
        sb.append(DsUtil.jsonString(className));
        sb.append(",\n  \"root\":\n");
        writeNode(sb, root, 2);
        sb.append("\n}\n");
        return sb.toString();
    }

    static DsNode readNode(JsonObject o) {
        DsNode n = new DsNode(o.getString("type", "VBox"));
        n.name = o.getString("name", "");
        JsonValue pv = o.get("props");
        if (pv.isObject()) {
            JsonObject po = pv.asObject();
            int i = 0;
            while (i < po.keys.size()) {
                JsonValue v = po.values.get(i);
                if (v.isString()) {
                    n.keys.add(po.keys.get(i));
                    n.vals.add(v.asString());
                }
                i = i + 1;
            }
        }
        JsonValue ev = o.get("events");
        if (ev.isObject()) {
            JsonObject eo = ev.asObject();
            int i = 0;
            while (i < eo.keys.size()) {
                JsonValue v = eo.values.get(i);
                if (v.isString()) {
                    n.evNames.add(eo.keys.get(i));
                    n.evHandlers.add(v.asString());
                }
                i = i + 1;
            }
        }
        JsonValue cv = o.get("children");
        if (cv.isArray()) {
            JsonArray ca = cv.asArray();
            int i = 0;
            while (i < ca.size()) {
                JsonValue c = ca.get(i);
                if (c.isObject()) {
                    n.add(readNode(c.asObject()));
                }
                i = i + 1;
            }
        }
        return n;
    }

    static DsNode parseNode(String text) {
        JsonValue v = Json.parse(text);
        if (!v.isObject()) {
            return null;
        }
        return readNode(v.asObject());
    }

    /** Parse a .jnui file; null when the text is not a valid document. */
    static DsDoc fromFileText(String text, String fallbackClass) {
        JsonValue v = Json.parse(text);
        if (!v.isObject()) {
            return null;
        }
        JsonObject o = v.asObject();
        JsonValue rv = o.get("root");
        if (!rv.isObject()) {
            return null;
        }
        DsDoc d = new DsDoc(o.getString("form", fallbackClass));
        d.root = readNode(rv.asObject());
        d.root.type = "Form";
        d.reassignUids(d.root);
        d.dirty = false;
        return d;
    }
}
