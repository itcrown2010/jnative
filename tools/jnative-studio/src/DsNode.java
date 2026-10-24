
/**
 * One component in a design document: type, unique field name, properties, event bindings and
 * children. Properties are plain strings so the model serialises to JSON without reflection.
 *
 * @author 张晓达
 */
public class DsNode {
    String type;
    String name = "";
    ArrayList<String> keys = new ArrayList<String>();
    ArrayList<String> vals = new ArrayList<String>();
    ArrayList<String> evNames = new ArrayList<String>();
    ArrayList<String> evHandlers = new ArrayList<String>();
    ArrayList<DsNode> children = new ArrayList<DsNode>();
    DsNode parent;
    int uid;

    DsNode(String t) {
        type = t;
    }

    int indexOfKey(String k) {
        int i = 0;
        while (i < keys.size()) {
            if (keys.get(i).equals(k)) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    boolean has(String k) {
        return indexOfKey(k) >= 0;
    }

    /** Raw stored value, or the catalog default when unset. */
    String get(String k) {
        int i = indexOfKey(k);
        if (i >= 0) {
            return vals.get(i);
        }
        DsProp p = DsMeta.prop(type, k);
        if (p != null) {
            return p.def;
        }
        return "";
    }

    int getInt(String k) {
        String v = get(k);
        if (v.length() == 0) {
            return 0;
        }
        return DsUtil.parseInt(v, 0);
    }

    boolean getBool(String k) {
        return get(k).equals("true");
    }

    double getDouble(String k) {
        String v = get(k);
        if (v.length() == 0) {
            return 0;
        }
        return DsUtil.parseDouble(v, 0);
    }

    void set(String k, String v) {
        int i = indexOfKey(k);
        DsProp p = DsMeta.prop(type, k);
        if (p != null && v.equals(p.def)) {
            // keep files small: defaults are not stored
            if (i >= 0) {
                keys.remove(i);
                vals.remove(i);
            }
            return;
        }
        if (i >= 0) {
            vals.set(i, v);
        } else {
            keys.add(k);
            vals.add(v);
        }
    }

    void unset(String k) {
        int i = indexOfKey(k);
        if (i >= 0) {
            keys.remove(i);
            vals.remove(i);
        }
    }

    String handler(String ev) {
        int i = 0;
        while (i < evNames.size()) {
            if (evNames.get(i).equals(ev)) {
                return evHandlers.get(i);
            }
            i = i + 1;
        }
        return "";
    }

    void setHandler(String ev, String h) {
        int i = 0;
        while (i < evNames.size()) {
            if (evNames.get(i).equals(ev)) {
                if (h.length() == 0) {
                    evNames.remove(i);
                    evHandlers.remove(i);
                } else {
                    evHandlers.set(i, h);
                }
                return;
            }
            i = i + 1;
        }
        if (h.length() > 0) {
            evNames.add(ev);
            evHandlers.add(h);
        }
    }

    void add(DsNode c) {
        c.parent = this;
        children.add(c);
    }

    void insert(int index, DsNode c) {
        c.parent = this;
        if (index < 0 || index > children.size()) {
            index = children.size();
        }
        children.add(index, c);
    }

    void remove(DsNode c) {
        int i = children.indexOf(c);
        if (i >= 0) {
            children.remove(i);
            c.parent = null;
        }
    }

    int indexInParent() {
        if (parent == null) {
            return -1;
        }
        return parent.children.indexOf(this);
    }

    DsNode deepCopy() {
        DsNode n = new DsNode(type);
        n.name = name;
        int i = 0;
        while (i < keys.size()) {
            n.keys.add(keys.get(i));
            n.vals.add(vals.get(i));
            i = i + 1;
        }
        i = 0;
        while (i < evNames.size()) {
            n.evNames.add(evNames.get(i));
            n.evHandlers.add(evHandlers.get(i));
            i = i + 1;
        }
        i = 0;
        while (i < children.size()) {
            n.add(children.get(i).deepCopy());
            i = i + 1;
        }
        return n;
    }

    boolean isAncestorOf(DsNode other) {
        DsNode p = other;
        while (p != null) {
            if (p == this) {
                return true;
            }
            p = p.parent;
        }
        return false;
    }
}
