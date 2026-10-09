package io.jnative.build;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal {@code jnative.project} loader.
 * Supports legacy Studio JSON and extended multi-module form.
 * @author 张晓达
*/
public final class ProjectFile {
    private static final Pattern NAME = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern SOURCE = Pattern.compile("\"source\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern OUTPUT = Pattern.compile("\"output\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MAIN = Pattern.compile("\"main\"\\s*:\\s*\"([^\"]+)\"");

    private ProjectFile() {
    }

    public static JNativeProject load(Path projectFile) throws IOException {
        Path root = projectFile.toAbsolutePath().normalize().getParent();
        if (root == null) {
            root = Path.of(".");
        }
        String text = Files.readString(projectFile, StandardCharsets.UTF_8);
        String name = matchFirst(NAME, text, root.getFileName().toString());
        JNativeProject project = new JNativeProject(name, root.toString());

        String modulesObj = extractObject(text, "modules");
        if (modulesObj != null) {
            for (String[] entry : extractObjectEntries(modulesObj)) {
                String modName = entry[0];
                String body = entry[1];
                Module mod = new Module(modName);
                String kind = extractString(body, "kind", "lib");
                mod.setKind(kind);
                String src = extractString(body, "src", null);
                if (src != null) {
                    mod.sourceRoots().add(src);
                } else {
                    mod.sourceRoots().add("src/" + modName);
                }
                String depsArr = extractArray(body, "dependsOn");
                if (depsArr != null) {
                    for (String d : splitStrings(depsArr)) {
                        mod.dependsOn().add(d);
                    }
                }
                project.modules().put(modName, mod);
            }
        } else {
            // Legacy single-module Studio project
            Module main = new Module("main");
            main.setKind("server");
            Matcher srcArr = SOURCE.matcher(text);
            if (srcArr.find()) {
                for (String s : splitStrings(srcArr.group(1))) {
                    main.sourceRoots().add(s);
                }
            } else {
                main.sourceRoots().add("src");
            }
            project.modules().put("main", main);
            String out = matchFirst(OUTPUT, text, "build/app");
            String mainClass = matchFirst(MAIN, text, "Main");
            project.targets().add(new BuildTargetSpec("default", "native", "main", mainClass, out));
        }

        String targetsArr = extractArray(text, "targets");
        if (targetsArr != null) {
            for (String part : splitTopLevelObjects(targetsArr)) {
                String n = extractString(part, "name", "app");
                String triple = extractString(part, "triple", "native");
                String module = extractString(part, "module", "main");
                String mainClass = extractString(part, "main", "App");
                String output = extractString(part, "output", ".jnative/build/" + n);
                project.targets().add(new BuildTargetSpec(n, triple, module, mainClass, output));
            }
        } else if (project.targets().isEmpty() && !project.modules().isEmpty()) {
            for (Module m : project.modules().values()) {
                String triple = switch (m.kind()) {
                    case "browser" -> "wasm32-browser";
                    case "desktop" -> "desktop-native";
                    default -> "native";
                };
                project.targets().add(new BuildTargetSpec(
                        m.name(), triple, m.name(), "App",
                        ".jnative/build/" + m.name()));
            }
        }
        return project;
    }

    public static JNativeProject discover(Path root) throws IOException {
        Path file = root.resolve("jnative.project");
        if (Files.isRegularFile(file)) {
            return load(file);
        }
        JNativeProject project = new JNativeProject(root.getFileName().toString(), root.toString());
        addIfPresent(project, root, "shared", "lib");
        addIfPresent(project, root, "server", "server");
        addIfPresent(project, root, "browser", "browser");
        addIfPresent(project, root, "desktop", "desktop");
        if (project.modules().isEmpty()) {
            Module main = new Module("main");
            main.sourceRoots().add(".");
            main.setKind("server");
            project.modules().put("main", main);
            project.targets().add(new BuildTargetSpec("default", "native", "main", "App", ".jnative/build/app"));
        } else {
            for (Module m : project.modules().values()) {
                if (!"lib".equals(m.kind()) && !"shared".equals(m.name())) {
                    String triple = "browser".equals(m.kind()) ? "wasm32-browser" : "native";
                    project.targets().add(new BuildTargetSpec(
                            m.name(), triple, m.name(), "App", ".jnative/build/" + m.name()));
                }
            }
        }
        return project;
    }

    private static void addIfPresent(JNativeProject project, Path root, String name, String kind) {
        Path dir = root.resolve(name);
        Path src = root.resolve("src").resolve(name);
        if (Files.isDirectory(dir) || Files.isDirectory(src)) {
            Module m = new Module(name);
            m.setKind(kind);
            if (Files.isDirectory(src)) {
                m.sourceRoots().add("src/" + name);
            } else {
                m.sourceRoots().add(name);
            }
            if (!"shared".equals(name) && project.modules().containsKey("shared")) {
                m.dependsOn().add("shared");
            }
            project.modules().put(name, m);
        }
    }

    /** Extract balanced `{...}` value for a JSON key. */
    static String extractObject(String text, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\{");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            return null;
        }
        int start = m.end() - 1;
        return balanced(text, start, '{', '}');
    }

    static String extractArray(String text, String key) {
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[");
        Matcher m = p.matcher(text);
        if (!m.find()) {
            return null;
        }
        int start = m.end() - 1;
        return balanced(text, start, '[', ']');
    }

    private static String balanced(String text, int start, char open, char close) {
        int depth = 0;
        boolean inString = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' && (i == 0 || text.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (inString) {
                continue;
            }
            if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    return text.substring(start + 1, i);
                }
            }
        }
        return null;
    }

    /** Split object body into name → object-body pairs. */
    static List<String[]> extractObjectEntries(String body) {
        List<String[]> out = new ArrayList<>();
        Pattern p = Pattern.compile("\"([^\"]+)\"\\s*:\\s*\\{");
        Matcher m = p.matcher(body);
        while (m.find()) {
            String name = m.group(1);
            int brace = m.end() - 1;
            String inner = balanced(body, brace, '{', '}');
            if (inner != null) {
                out.add(new String[]{name, inner});
            }
        }
        return out;
    }

    static List<String> splitTopLevelObjects(String arrayBody) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        int start = -1;
        for (int i = 0; i < arrayBody.length(); i++) {
            char c = arrayBody.charAt(i);
            if (c == '"' && (i == 0 || arrayBody.charAt(i - 1) != '\\')) {
                inString = !inString;
            }
            if (inString) {
                continue;
            }
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(arrayBody.substring(start + 1, i));
                    start = -1;
                }
            }
        }
        return out;
    }

    private static String matchFirst(Pattern p, String text, String def) {
        Matcher m = p.matcher(text);
        return m.find() ? m.group(1) : def;
    }

    private static String extractString(String body, String key, String def) {
        Matcher m = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]+)\"").matcher(body);
        return m.find() ? m.group(1) : def;
    }

    private static List<String> splitStrings(String inner) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(inner);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }
}
