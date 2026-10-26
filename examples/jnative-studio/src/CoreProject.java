/**
 * Project model — jnative.project JSON.
 * @author Zhang Xiaoda
 */
class CoreProject {
    String name;
    String root;
    ArrayList<String> sourceRoots;
    String outputDirectory;
    String mainClass;

    CoreProject() {
        name = "Untitled";
        root = ".";
        sourceRoots = new ArrayList<String>();
        sourceRoots.add("src");
        outputDirectory = "build";
        mainClass = "Main";
    }

    static CoreProject open(String projectFileOrDir) {
        CoreProject p = new CoreProject();
        String root = projectFileOrDir;
        String file = projectFileOrDir + "/jnative.project";
        if (Files.isDirectory(FsPath.of(projectFileOrDir))) {
            root = projectFileOrDir;
            file = root + "/jnative.project";
        } else {
            FsPath fp = FsPath.of(projectFileOrDir);
            root = fp.getParent().toString();
            file = projectFileOrDir;
        }
        p.root = root;
        if (!File.exists(file)) {
            return p;
        }
        String text = File.readString(file);
        JsonValue v = Json.parse(text);
        if (v.isObject()) {
            JsonObject o = v.asObject();
            p.name = o.getString("name", p.name);
            p.outputDirectory = o.getString("output", p.outputDirectory);
            p.mainClass = o.getString("main", p.mainClass);
            JsonValue src = o.get("source");
            if (src != null && src.isArray()) {
                p.sourceRoots = new ArrayList<String>();
                JsonArray a = src.asArray();
                int i = 0;
                while (i < a.size()) {
                    if (a.get(i).isString()) {
                        p.sourceRoots.add(a.get(i).asString());
                    }
                    i = i + 1;
                }
            }
        }
        return p;
    }

    String binaryPath() {
        /* output field may be "build/Main" (full relative) or a directory name. */
        int i = 0;
        int hasSlash = 0;
        while (i < outputDirectory.length()) {
            if (outputDirectory.charAt(i) == 47) {
                hasSlash = 1;
            }
            i = i + 1;
        }
        if (hasSlash == 1) {
            return root + "/" + outputDirectory;
        }
        return root + "/" + outputDirectory + "/" + mainClass;
    }
}


