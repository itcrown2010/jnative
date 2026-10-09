// jnative.browser — wasm32-browser GC stress (objects, cycles, ArrayList, nested alloc)
/**
 * @author 张晓达
 */
class Node {
    Node next;
    int value;

    Node(int v) {
        this.value = v;
        this.next = null;
    }
}

class IntBox {
    int value;
}

class GcStress {
    static Node keepAlive;
    static ArrayList<Node> bag;

    public static void main(String[] args) {
        bag = new ArrayList<Node>();
        keepAlive = null;
        // Keep exported entry points alive under whole-program DCE (same pattern as FloatCalc).
        boolean keep = false;
        if (keep) {
            churn(1);
            makeCycle();
            dropKeepAlive();
            bagChurn(1);
            nestedSurvive(1);
            closureEscape(1);
        }
    }

    /** Allocate many short-lived nodes; only last remains in keepAlive. */
    public static int churn(int n) {
        Node last = null;
        for (int i = 0; i < n; i++) {
            Node x = new Node(i);
            last = x;
        }
        keepAlive = last;
        return keepAlive != null ? keepAlive.value : -1;
    }

    /** Two-node cycle retained via keepAlive. */
    public static int makeCycle() {
        Node a = new Node(1);
        Node b = new Node(2);
        a.next = b;
        b.next = a;
        keepAlive = a;
        return a.value + b.value;
    }

    /** Drop cycle root so GC can reclaim. */
    public static void dropKeepAlive() {
        keepAlive = null;
    }

    /** Grow ArrayList then clear — cleared slots must not pin objects. */
    public static int bagChurn(int n) {
        bag = new ArrayList<Node>();
        for (int i = 0; i < n; i++) {
            bag.add(new Node(i));
        }
        int sz = bag.size();
        bag.clear();
        return sz;
    }

    /** Nested expression: first arg object must survive second arg's allocations. */
    public static int nestedSurvive(int n) {
        return holdAndChurn(new Node(42), n);
    }

    static int holdAndChurn(Node held, int n) {
        for (int i = 0; i < n; i++) {
            Node tmp = new Node(i);
            if (tmp.value < 0) {
                return -1;
            }
        }
        keepAlive = held;
        return held.value;
    }

    /** Closure capture retained until bag cleared. */
    public static int closureEscape(int n) {
        IntBox box = new IntBox();
        box.value = 0;
        bag = new ArrayList<Node>();
        for (int i = 0; i < n; i++) {
            bag.add(new Node(i));
        }
        bag.forEach(it -> {
            box.value = box.value + it.value;
        });
        bag.clear();
        return box.value;
    }
}
