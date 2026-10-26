/**
 * jn-project — inspect a JNative project (Level 1 tool).
 * Uses Json + File; drives no compiler internals.
 * @author Zhang Xiaoda
 */
class JnProjectInfo {
    String name;
    String root;
    String output;
    String mainClass;
    ArrayList<String> sources;

    JnProjectInfo() {
        name = "Untitled";
        root = ".";
        output = "build";
        mainClass = "Main";
        sources = new ArrayList<String>();
        sources.add("src");
    }
}

public class JnProjectMain {
    static JnProjectInfo load(String dir) {
        JnProjectInfo p = new JnProjectInfo();
        p.root = dir;
        String file = dir + "/jnative.project";
        if (!File.exists(file)) {
            return p;
        }
        JsonValue v = Json.parse(File.readString(file));
        if (v.isObject()) {
            JsonObject o = v.asObject();
            p.name = o.getString("name", p.name);
            p.output = o.getString("output", p.output);
            p.mainClass = o.getString("main", p.mainClass);
            JsonValue src = o.get("source");
            if (src != null && src.isArray()) {
                p.sources = new ArrayList<String>();
                JsonArray a = src.asArray();
                int i = 0;
                while (i < a.size()) {
                    if (a.get(i).isString()) {
                        p.sources.add(a.get(i).asString());
                    }
                    i = i + 1;
                }
            }
        }
        return p;
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("usage: jn-project <project-dir>");
            return;
        }
        String dir = args[0];
        JnProjectInfo p = load(dir);
        System.out.println("name=" + p.name);
        System.out.println("root=" + p.root);
        System.out.println("output=" + p.output);
        System.out.println("main=" + p.mainClass);
        int i = 0;
        while (i < p.sources.size()) {
            System.out.println("source=" + p.sources.get(i));
            i = i + 1;
        }
    }
}
