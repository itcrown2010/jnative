package io.jnative.pkg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal TOML-ish loader for jnative.toml (sufficient for package manager).
 * Also accepts a JSON sibling for tests.
 * @author 张晓达
 */
public final class ManifestFile {
    private ManifestFile() {
    }

    public static PackageManifest load(Path file) throws IOException {
        String text = Files.readString(file, StandardCharsets.UTF_8);
        if (file.getFileName().toString().endsWith(".json")) {
            return fromSimpleJson(text);
        }
        return fromToml(text);
    }

    public static PackageManifest discover(Path root) throws IOException {
        Path toml = root.resolve("jnative.toml");
        if (Files.isRegularFile(toml)) {
            return load(toml);
        }
        Path json = root.resolve("jnative.package.json");
        if (Files.isRegularFile(json)) {
            return load(json);
        }
        // Synthetic from directory name
        PackageManifest m = new PackageManifest();
        m.setId(PackageId.parse("jnative/" + root.getFileName()));
        m.setVersion(SemanticVersion.parse("0.1.0"));
        return m;
    }

    public static void saveToml(Path file, PackageManifest m) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("[package]\n");
        sb.append("name = \"").append(m.id().name()).append("\"\n");
        sb.append("namespace = \"").append(m.id().namespace()).append("\"\n");
        sb.append("version = \"").append(m.version()).append("\"\n");
        if (!m.description().isEmpty()) {
            sb.append("description = \"").append(escape(m.description())).append("\"\n");
        }
        if (!m.license().isEmpty()) {
            sb.append("license = \"").append(m.license()).append("\"\n");
        }
        if (!m.repository().isEmpty()) {
            sb.append("repository = \"").append(m.repository()).append("\"\n");
        }
        if (!m.minCompiler().isEmpty()) {
            sb.append("min_compiler = \"").append(m.minCompiler()).append("\"\n");
        }
        if (!m.packageType().isEmpty()) {
            sb.append("type = \"").append(m.packageType()).append("\"\n");
        }
        if (!m.requires().isEmpty()) {
            sb.append("requires = [");
            boolean first = true;
            for (TargetCapability c : m.requires()) {
                if (!first) {
                    sb.append(", ");
                }
                sb.append('"').append(c.name()).append('"');
                first = false;
            }
            sb.append("]\n");
        }
        sb.append('\n');
        writeDeps(sb, "dependencies", m.dependencies());
        writeDeps(sb, "dev-dependencies", m.devDependencies());
        for (Map.Entry<String, List<Dependency>> e : m.targetDependencies().entrySet()) {
            writeDeps(sb, "target." + e.getKey() + ".dependencies", e.getValue());
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void writeDeps(StringBuilder sb, String section, List<Dependency> deps) {
        if (deps.isEmpty()) {
            return;
        }
        sb.append('[').append(section).append("]\n");
        for (Dependency d : deps) {
            if (d.kind() == Dependency.Kind.PATH) {
                sb.append(d.id().name()).append(" = { path = \"").append(d.path()).append("\" }\n");
            } else if (!d.features().isEmpty() || d.optional()) {
                sb.append(d.id().name()).append(" = { version = \"").append(d.constraint())
                        .append("\"");
                if (d.optional()) {
                    sb.append(", optional = true");
                }
                if (!d.features().isEmpty()) {
                    sb.append(", features = [");
                    boolean first = true;
                    for (String f : d.features()) {
                        if (!first) {
                            sb.append(", ");
                        }
                        sb.append('"').append(f).append('"');
                        first = false;
                    }
                    sb.append(']');
                }
                sb.append(" }\n");
            } else {
                String coord = d.id().namespace().equals("jnative")
                        ? d.id().name()
                        : d.id().coordinate();
                sb.append(coord.replace('/', '_')).append(" = \"").append(d.constraint()).append("\"\n");
                // Prefer name key for official packages
                int start = sb.lastIndexOf(coord.replace('/', '_'));
                // rewrite last line more cleanly
                sb.setLength(sb.length());
            }
        }
        // Rewrite cleanly
        int cut = sb.lastIndexOf("[" + section + "]");
        sb.setLength(cut);
        sb.append('[').append(section).append("]\n");
        for (Dependency d : deps) {
            String key = d.id().namespace().equals("jnative") ? d.id().name() : d.id().coordinate();
            if (d.kind() == Dependency.Kind.PATH) {
                sb.append('"').append(key).append("\" = { path = \"").append(d.path()).append("\" }\n");
            } else {
                sb.append('"').append(key).append("\" = \"").append(d.constraint()).append("\"\n");
            }
        }
        sb.append('\n');
    }

    static PackageManifest fromToml(String text) {
        PackageManifest m = new PackageManifest();
        Map<String, String> pkg = section(text, "package");
        if (pkg.containsKey("name")) {
            String ns = unquote(pkg.getOrDefault("namespace", "jnative"));
            m.setId(new PackageId(ns, unquote(pkg.get("name"))));
        }
        if (pkg.containsKey("version")) {
            m.setVersion(SemanticVersion.parse(unquote(pkg.get("version"))));
        }
        if (pkg.containsKey("description")) {
            m.setDescription(unquote(pkg.get("description")));
        }
        if (pkg.containsKey("license")) {
            m.setLicense(unquote(pkg.get("license")));
        }
        if (pkg.containsKey("repository")) {
            m.setRepository(unquote(pkg.get("repository")));
        }
        if (pkg.containsKey("min_compiler")) {
            m.setMinCompiler(unquote(pkg.get("min_compiler")));
        }
        if (pkg.containsKey("type")) {
            m.setPackageType(unquote(pkg.get("type")));
        }
        if (pkg.containsKey("processor")) {
            m.setProcessor(Boolean.parseBoolean(unquote(pkg.get("processor"))));
        }
        if (pkg.containsKey("requires")) {
            m.requires().addAll(TargetCapability.parseAll(parseStringArray(pkg.get("requires"))));
        }
        Map<String, String> processorSec = section(text, "processor");
        if (!processorSec.isEmpty()) {
            m.setProcessor(true);
            if (processorSec.containsKey("entry")) {
                m.setProcessorEntry(unquote(processorSec.get("entry")));
            }
            if (processorSec.containsKey("min_api") || processorSec.containsKey("compilerApi")) {
                String v = processorSec.getOrDefault("min_api",
                        processorSec.getOrDefault("compilerApi", "1"));
                try {
                    m.setMinProcessorApi(Integer.parseInt(unquote(v)));
                } catch (NumberFormatException ignored) {
                    m.setMinProcessorApi(1);
                }
            }
            if (processorSec.containsKey("max_api")) {
                try {
                    m.setMaxProcessorApi(Integer.parseInt(unquote(processorSec.get("max_api"))));
                } catch (NumberFormatException ignored) {
                    m.setMaxProcessorApi(0);
                }
            }
        }
        Map<String, String> nativeSec = section(text, "native");
        if (nativeSec.containsKey("library")) {
            String lib = unquote(nativeSec.get("library"));
            List<String> platforms = nativeSec.containsKey("platforms")
                    ? parseStringArray(nativeSec.get("platforms"))
                    : List.of("native");
            for (String platform : platforms) {
                m.nativeLibraries().add(new PackageManifest.NativeLibrarySpec(
                        lib, unquote(platform), lib, ""));
            }
        }
        m.dependencies().addAll(parseDepSection(section(text, "dependencies"), false, ""));
        m.devDependencies().addAll(parseDepSection(section(text, "dev-dependencies"), true, ""));
        for (String scope : List.of("native", "browser", "desktop")) {
            Map<String, String> td = section(text, "target." + scope + ".dependencies");
            if (!td.isEmpty()) {
                List<Dependency> list = parseDepSection(td, false, scope);
                m.targetDependencies().put(scope, list);
            }
        }
        return m;
    }

    private static List<Dependency> parseDepSection(Map<String, String> map, boolean dev, String scope) {
        List<Dependency> out = new ArrayList<>();
        for (Map.Entry<String, String> e : map.entrySet()) {
            String key = unquote(e.getKey());
            PackageId id = PackageId.parse(key.contains("/") ? key : "jnative/" + key);
            String val = e.getValue().trim();
            Dependency dep;
            if (val.startsWith("{")) {
                String path = extractField(val, "path");
                String version = extractField(val, "version");
                boolean optional = val.contains("optional") && val.contains("true");
                if (path != null) {
                    dep = Dependency.path(id, path);
                } else {
                    dep = Dependency.registry(id, version == null ? "*" : version);
                }
                if (optional) {
                    dep = dep.asOptional();
                }
            } else {
                dep = Dependency.registry(id, unquote(val));
            }
            if (dev) {
                dep = dep.asDev();
            }
            if (!scope.isEmpty()) {
                dep = dep.withTargetScope(scope);
            }
            out.add(dep);
        }
        return out;
    }

    private static String extractField(String obj, String field) {
        Matcher m = Pattern.compile(field + "\\s*=\\s*\"([^\"]+)\"").matcher(obj);
        return m.find() ? m.group(1) : null;
    }

    private static Map<String, String> section(String text, String name) {
        Map<String, String> map = new LinkedHashMap<>();
        Pattern header = Pattern.compile("^\\[" + Pattern.quote(name) + "]\\s*$", Pattern.MULTILINE);
        Matcher hm = header.matcher(text);
        if (!hm.find()) {
            return map;
        }
        int start = hm.end();
        Matcher next = Pattern.compile("^\\[", Pattern.MULTILINE).matcher(text);
        int end = text.length();
        if (next.find(start)) {
            end = next.start();
        }
        String body = text.substring(start, end);
        for (String line : body.split("\n")) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) {
                continue;
            }
            int eq = t.indexOf('=');
            if (eq < 0) {
                continue;
            }
            map.put(unquote(t.substring(0, eq).trim()), t.substring(eq + 1).trim());
        }
        return map;
    }

    private static List<String> parseStringArray(String raw) {
        List<String> out = new ArrayList<>();
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(raw);
        while (m.find()) {
            out.add(m.group(1));
        }
        return out;
    }

    private static String unquote(String s) {
        if (s == null) {
            return "";
        }
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static PackageManifest fromSimpleJson(String text) {
        PackageManifest m = new PackageManifest();
        Matcher name = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
        if (name.find()) {
            m.setId(PackageId.parse(name.group(1).contains("/") ? name.group(1) : "jnative/" + name.group(1)));
        }
        Matcher ver = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"").matcher(text);
        if (ver.find()) {
            m.setVersion(SemanticVersion.parse(ver.group(1)));
        }
        return m;
    }
}
