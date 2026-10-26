/**
 * CLI Todo for the 30-minute tutorial.
 * ArrayList + File persistence + args.
 * @author Zhang Xiaoda
 */
public class TodoApp {
    static String storePath = "todo-store.txt";
    static String nl = "\n";

    static ArrayList<String> load() {
        ArrayList<String> items = new ArrayList<String>();
        if (!File.exists(storePath)) {
            return items;
        }
        String all = File.readString(storePath);
        int start = 0;
        int i = 0;
        int n = all.length();
        int nlCode = nl.charAt(0);
        while (i <= n) {
            boolean end = i == n;
            boolean isNl = false;
            if (!end) {
                isNl = all.charAt(i) == nlCode;
            }
            if (end || isNl) {
                if (i > start) {
                    items.add(all.substring(start, i));
                }
                start = i + 1;
            }
            i = i + 1;
        }
        return items;
    }

    static void save(ArrayList<String> items) {
        String out = "";
        int i = 0;
        while (i < items.size()) {
            out = out + items.get(i) + nl;
            i = i + 1;
        }
        File.writeString(storePath, out);
    }

    static void list() {
        ArrayList<String> items = load();
        if (items.size() == 0) {
            System.out.println("(empty)");
            return;
        }
        int i = 0;
        while (i < items.size()) {
            System.out.println(items.get(i));
            i = i + 1;
        }
    }

    static void add(String title) {
        ArrayList<String> items = load();
        items.add(title);
        save(items);
        System.out.println("added");
    }

    static void removeTitle(String title) {
        ArrayList<String> items = load();
        ArrayList<String> next = new ArrayList<String>();
        int i = 0;
        int removed = 0;
        while (i < items.size()) {
            String cur = items.get(i);
            if (removed == 0 && cur.equals(title)) {
                removed = 1;
            } else {
                next.add(cur);
            }
            i = i + 1;
        }
        save(next);
        if (removed == 1) {
            System.out.println("done");
        } else {
            System.out.println("missing");
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("usage: todo add|list|done ...");
            return;
        }
        String cmd = args[0];
        if (cmd.equals("list")) {
            list();
            return;
        }
        if (cmd.equals("add") && args.length >= 2) {
            add(args[1]);
            return;
        }
        if (cmd.equals("done") && args.length >= 2) {
            removeTitle(args[1]);
            return;
        }
        System.out.println("usage: todo add|list|done ...");
    }
}
