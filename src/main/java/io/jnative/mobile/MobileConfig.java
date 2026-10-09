package io.jnative.mobile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Mobile application section of {@code jnative.toml}.
 *
 * <pre>
 * [mobile]
 * app_name = "My App"
 * android_application_id = "io.example.myapp"
 * ios_bundle_id = "io.example.myapp"
 * version = "0.1.0"
 * build_number = "1"
 * entry = "src/App.java"
 * web_root = "web"
 * web_entry = "web/index.html"
 * platforms_root = "platforms"
 * targets = ["web", "android", "ios"]
 * capabilities = ["platform.getName", "storage.get", "storage.set", "storage.remove"]
 * </pre>
 * @author 张晓达
 */
public final class MobileConfig {

    public static final Set<String> KNOWN_TARGETS = Set.of("web", "android", "ios");
    public static final Set<String> KNOWN_CAPABILITIES = Set.of(
            "platform.getName",
            "storage.get",
            "storage.set",
            "storage.remove",
            "lifecycle"
    );

    private final String appName;
    private final String androidApplicationId;
    private final String iosBundleId;
    private final String version;
    private final String buildNumber;
    private final String entry;
    private final String webRoot;
    private final String webEntry;
    private final String platformsRoot;
    private final List<String> targets;
    private final List<String> capabilities;

    public MobileConfig(
            String appName,
            String androidApplicationId,
            String iosBundleId,
            String version,
            String buildNumber,
            String entry,
            String webRoot,
            String webEntry,
            String platformsRoot,
            List<String> targets,
            List<String> capabilities
    ) {
        this.appName = appName;
        this.androidApplicationId = androidApplicationId;
        this.iosBundleId = iosBundleId;
        this.version = version;
        this.buildNumber = buildNumber;
        this.entry = entry;
        this.webRoot = webRoot;
        this.webEntry = webEntry;
        this.platformsRoot = platformsRoot;
        this.targets = List.copyOf(targets);
        this.capabilities = List.copyOf(capabilities);
    }

    public String appName() {
        return appName;
    }

    public String androidApplicationId() {
        return androidApplicationId;
    }

    public String iosBundleId() {
        return iosBundleId;
    }

    public String version() {
        return version;
    }

    public String buildNumber() {
        return buildNumber;
    }

    public String entry() {
        return entry;
    }

    public String webRoot() {
        return webRoot;
    }

    public String webEntry() {
        return webEntry;
    }

    public String platformsRoot() {
        return platformsRoot;
    }

    public List<String> targets() {
        return targets;
    }

    public List<String> capabilities() {
        return capabilities;
    }

    public static MobileConfig load(Path projectRoot) throws IOException {
        Path toml = projectRoot.resolve("jnative.toml");
        if (!Files.isRegularFile(toml)) {
            throw new MobileConfigException("missing jnative.toml in " + projectRoot);
        }
        return parse(Files.readString(toml, StandardCharsets.UTF_8), projectRoot);
    }

    public static boolean isMobileProject(Path projectRoot) {
        Path toml = projectRoot.resolve("jnative.toml");
        if (!Files.isRegularFile(toml)) {
            return false;
        }
        try {
            String text = Files.readString(toml, StandardCharsets.UTF_8);
            return section(text, "mobile").containsKey("app_name")
                    || section(text, "mobile").containsKey("entry")
                    || section(text, "mobile").containsKey("targets");
        } catch (IOException e) {
            return false;
        }
    }

    public static MobileConfig parse(String text, Path projectRoot) {
        Map<String, String> m = section(text, "mobile");
        if (m.isEmpty()) {
            throw new MobileConfigException("jnative.toml missing [mobile] section");
        }
        Map<String, String> pkg = section(text, "package");
        String appName = unquote(m.getOrDefault("app_name",
                unquote(pkg.getOrDefault("name", projectRoot.getFileName().toString()))));
        String androidId = unquote(m.getOrDefault("android_application_id", ""));
        String iosId = unquote(m.getOrDefault("ios_bundle_id", ""));
        String version = unquote(m.getOrDefault("version",
                unquote(pkg.getOrDefault("version", "0.1.0"))));
        String buildNumber = unquote(m.getOrDefault("build_number", "1"));
        String entry = unquote(m.getOrDefault("entry", "src/App.java"));
        String webRoot = unquote(m.getOrDefault("web_root", "web"));
        String webEntry = unquote(m.getOrDefault("web_entry", webRoot + "/index.html"));
        String platformsRoot = unquote(m.getOrDefault("platforms_root", "platforms"));
        List<String> targets = m.containsKey("targets")
                ? parseStringArray(m.get("targets"))
                : List.of("web", "android", "ios");
        List<String> caps = m.containsKey("capabilities")
                ? parseStringArray(m.get("capabilities"))
                : List.of(
                "platform.getName",
                "storage.get",
                "storage.set",
                "storage.remove",
                "lifecycle");

        MobileConfig cfg = new MobileConfig(
                appName, androidId, iosId, version, buildNumber,
                entry, webRoot, webEntry, platformsRoot, targets, caps);
        cfg.validate(projectRoot);
        return cfg;
    }

    public void validate(Path projectRoot) {
        List<String> errors = new ArrayList<>();
        if (appName == null || appName.isBlank()) {
            errors.add("mobile.app_name must be non-empty");
        }
        if (!isAndroidApplicationId(androidApplicationId)) {
            errors.add("mobile.android_application_id invalid (expected reverse-DNS, e.g. io.example.app): "
                    + androidApplicationId);
        }
        if (!isIosBundleId(iosBundleId)) {
            errors.add("mobile.ios_bundle_id invalid (expected reverse-DNS): " + iosBundleId);
        }
        if (!version.matches("\\d+\\.\\d+\\.\\d+([.-][A-Za-z0-9.]+)?")) {
            errors.add("mobile.version invalid: " + version);
        }
        if (!buildNumber.matches("\\d+")) {
            errors.add("mobile.build_number must be an integer string: " + buildNumber);
        }
        if (entry.isBlank() || entry.contains("..")) {
            errors.add("mobile.entry invalid: " + entry);
        } else if (projectRoot != null && !Files.isRegularFile(projectRoot.resolve(entry))) {
            errors.add("mobile.entry does not exist: " + entry);
        }
        if (webEntry.isBlank() || webEntry.contains("..")) {
            errors.add("mobile.web_entry invalid: " + webEntry);
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String t : targets) {
            String key = t.toLowerCase(Locale.ROOT);
            if (!KNOWN_TARGETS.contains(key)) {
                errors.add("mobile.targets unknown target: " + t + " (allowed: web, android, ios)");
            }
            if (!seen.add(key)) {
                errors.add("mobile.targets duplicate: " + t);
            }
        }
        if (seen.isEmpty()) {
            errors.add("mobile.targets must list at least one target");
        }
        for (String c : capabilities) {
            if (!KNOWN_CAPABILITIES.contains(c) && !c.equals("bridge.hello")) {
                errors.add("mobile.capabilities unknown capability: " + c);
            }
        }
        if (!errors.isEmpty()) {
            throw new MobileConfigException(String.join("\n", errors));
        }
    }

    /** Validation used when generating a project (entry may not exist yet). */
    public void validateForGenerate() {
        List<String> errors = new ArrayList<>();
        if (appName == null || appName.isBlank()) {
            errors.add("app_name must be non-empty");
        }
        if (!isAndroidApplicationId(androidApplicationId)) {
            errors.add("android_application_id invalid: " + androidApplicationId);
        }
        if (!isIosBundleId(iosBundleId)) {
            errors.add("ios_bundle_id invalid: " + iosBundleId);
        }
        if (!buildNumber.matches("\\d+")) {
            errors.add("build_number must be digits");
        }
        Set<String> seen = new LinkedHashSet<>();
        for (String t : targets) {
            if (!KNOWN_TARGETS.contains(t.toLowerCase(Locale.ROOT))) {
                errors.add("unknown target: " + t);
            }
            if (!seen.add(t.toLowerCase(Locale.ROOT))) {
                errors.add("duplicate target: " + t);
            }
        }
        if (!errors.isEmpty()) {
            throw new MobileConfigException(String.join("\n", errors));
        }
    }

    public static boolean isAndroidApplicationId(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        // reverse-DNS, at least two segments, Java-package-like
        return id.matches("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+");
    }

    public static boolean isIosBundleId(String id) {
        return isAndroidApplicationId(id);
    }

    public String toTomlSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("[mobile]\n");
        sb.append("app_name = \"").append(escape(appName)).append("\"\n");
        sb.append("android_application_id = \"").append(escape(androidApplicationId)).append("\"\n");
        sb.append("ios_bundle_id = \"").append(escape(iosBundleId)).append("\"\n");
        sb.append("version = \"").append(escape(version)).append("\"\n");
        sb.append("build_number = \"").append(escape(buildNumber)).append("\"\n");
        sb.append("entry = \"").append(escape(entry)).append("\"\n");
        sb.append("web_root = \"").append(escape(webRoot)).append("\"\n");
        sb.append("web_entry = \"").append(escape(webEntry)).append("\"\n");
        sb.append("platforms_root = \"").append(escape(platformsRoot)).append("\"\n");
        sb.append("targets = [");
        for (int i = 0; i < targets.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"').append(targets.get(i)).append('"');
        }
        sb.append("]\n");
        sb.append("capabilities = [");
        for (int i = 0; i < capabilities.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"').append(capabilities.get(i)).append('"');
        }
        sb.append("]\n");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
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
        Matcher m = Pattern.compile("\"([^\"]+)\"").matcher(Objects.requireNonNullElse(raw, ""));
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
}
