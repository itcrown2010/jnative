package io.jnative.mobile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Builds web (WASM) then optionally Android/iOS debug artifacts.
 * On failure, does not leave prior successful platform packages as "current".
 */
public final class MobileBuilder {

    private final Path projectRoot;
    private final MobileConfig config;
    private final Path jnativeJar;

    public MobileBuilder(Path projectRoot, MobileConfig config, Path jnativeJar) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.config = config;
        this.jnativeJar = jnativeJar;
    }

    public Path distDir() {
        return projectRoot.resolve("dist");
    }

    public Path stagingDir() {
        return projectRoot.resolve(".jnative/mobile-staging");
    }

    public Path buildRecordPath() {
        return projectRoot.resolve(".jnative/mobile-build.json");
    }

    /** Compile JNative entry → fresh dist/; overlay web shell from web_root. */
    public void buildWeb() throws Exception {
        Path entry = projectRoot.resolve(config.entry());
        if (!Files.isRegularFile(entry)) {
            throw new IllegalStateException("entry not found: " + entry);
        }
        Path stage = stagingDir().resolve("web");
        deleteRecursively(stage);
        Files.createDirectories(stage);

        if (MobileToolchain.which("wat2wasm") == null) {
            throw new IllegalStateException("wat2wasm not found on PATH (install WABT)");
        }

        System.out.println("==> compiling " + config.entry() + " → wasm32-browser");
        buildWebViaJar(entry, stage);

        byte[] magic = Files.readAllBytes(stage.resolve("app.wasm"));
        if (magic.length < 4 || magic[0] != 0 || magic[1] != 'a' || magic[2] != 's' || magic[3] != 'm') {
            throw new IllegalStateException("app.wasm missing WASM magic — refusing to package");
        }

        Path webRoot = projectRoot.resolve(config.webRoot());
        copyIfExists(webRoot.resolve("index.html"), stage.resolve("index.html"));
        copyIfExists(webRoot.resolve("bridge.js"), stage.resolve("bridge.js"));
        copyIfExists(webRoot.resolve("app-shell.js"), stage.resolve("app-shell.js"));
        // Apply identifiers into a small build-info.js for the shell
        Files.writeString(stage.resolve("build-info.js"),
                "export const BUILD_INFO = "
                        + "{ appName: \"" + escapeJs(config.appName()) + "\", "
                        + "version: \"" + escapeJs(config.version()) + "\", "
                        + "buildNumber: \"" + escapeJs(config.buildNumber()) + "\", "
                        + "androidId: \"" + escapeJs(config.androidApplicationId()) + "\", "
                        + "iosBundleId: \"" + escapeJs(config.iosBundleId()) + "\" };\n",
                StandardCharsets.UTF_8);

        Path dist = distDir();
        deleteRecursively(dist);
        Files.createDirectories(dist);
        try (Stream<Path> stream = Files.list(stage)) {
            for (Path p : stream.toList()) {
                Files.copy(p, dist.resolve(p.getFileName().toString()), StandardCopyOption.REPLACE_EXISTING);
            }
        }

        MobileBuildRecord rec = new MobileBuildRecord()
                .put("timestamp", Instant.now().toString())
                .put("target", "web")
                .put("entry", config.entry())
                .put("entry_sha256", MobileBuildRecord.sha256(entry))
                .put("wasm", dist.resolve("app.wasm").toString())
                .put("wasm_sha256", MobileBuildRecord.sha256(dist.resolve("app.wasm")))
                .put("js", dist.resolve("app.js").toString())
                .put("js_sha256", MobileBuildRecord.sha256(dist.resolve("app.js")))
                .put("app_name", config.appName())
                .put("version", config.version())
                .put("build_number", config.buildNumber());
        rec.write(buildRecordPath());
        System.out.println("==> web dist ready: " + dist);
    }

    private void buildWebViaJar(Path entry, Path stage) throws Exception {
        Path jar = resolveJar();
        if (jar == null) {
            throw new IllegalStateException(
                    "wasm products missing and jnative.jar not found (set JNATIVE_JAR or build target/jnative.jar)");
        }
        List<String> cmd = List.of(
                ProcessHandle.current().info().command().orElse("java"),
                "-jar", jar.toString(),
                "build", "--target", "wasm32-browser", "--keep-temps",
                entry.toString(),
                "-o", stage.toString());
        MobileProcess.Result r = MobileProcess.run(cmd, projectRoot, Map.of(), 180);
        if (r.timedOut()) {
            throw new IllegalStateException("wasm compile timed out");
        }
        if (!r.ok()) {
            throw new IllegalStateException("wasm compile failed:\n" + r.output());
        }
    }

    public void syncPlatformAssets() throws Exception {
        Path dist = distDir();
        requireFreshDist(dist);
        Path androidWww = projectRoot.resolve(config.platformsRoot())
                .resolve("android/app/src/main/assets/www");
        Path iosWww = projectRoot.resolve(config.platformsRoot())
                .resolve("ios/App/www");
        // Also support Calculator/www naming from MVP
        Path iosWwwAlt = projectRoot.resolve(config.platformsRoot())
                .resolve("ios/Calculator/www");
        syncDir(dist, androidWww);
        if (Files.isDirectory(iosWww.getParent())) {
            syncDir(dist, iosWww);
        } else if (Files.isDirectory(iosWwwAlt.getParent())) {
            syncDir(dist, iosWwwAlt);
        } else {
            syncDir(dist, iosWww);
        }
        System.out.println("==> synced dist → platform www folders");
    }

    public Path buildAndroid() throws Exception {
        buildWeb();
        syncPlatformAssets();
        MobileToolchain.Report report = MobileToolchain.probe(resolveJar());
        if (!report.healthyFor("android")) {
            throw new IllegalStateException("Android toolchain incomplete:\n  - "
                    + String.join("\n  - ", report.missingFor("android")));
        }
        Path androidRoot = projectRoot.resolve(config.platformsRoot()).resolve("android");
        applyAndroidIdentifiers(androidRoot);

        Path stamp = stagingDir().resolve("android.ok");
        Files.deleteIfExists(stamp);

        Path gradlew = androidRoot.resolve("gradlew");
        List<String> cmd;
        if (Files.isExecutable(gradlew)) {
            cmd = List.of(gradlew.toAbsolutePath().toString(), ":app:assembleDebug");
        } else if (MobileToolchain.which("gradle") != null) {
            cmd = List.of(MobileToolchain.which("gradle"), ":app:assembleDebug");
        } else {
            throw new IllegalStateException("neither gradlew nor gradle available under " + androidRoot);
        }
        Map<String, String> env = new HashMap<>();
        Path sdk = MobileToolchain.androidSdk();
        if (sdk != null) {
            env.put("ANDROID_HOME", sdk.toString());
            env.put("ANDROID_SDK_ROOT", sdk.toString());
        }
        MobileProcess.Result r = MobileProcess.run(cmd, androidRoot, env, 600);
        if (r.timedOut()) {
            throw new IllegalStateException("Android build timed out — not installing any APK");
        }
        if (!r.ok()) {
            throw new IllegalStateException("Android build failed — not installing any APK\n" + r.output());
        }
        Path apk = androidRoot.resolve("app/build/outputs/apk/debug/app-debug.apk");
        if (!Files.isRegularFile(apk)) {
            throw new IllegalStateException("Android build produced no APK at " + apk);
        }
        Files.writeString(stamp, apk.toString(), StandardCharsets.UTF_8);
        new MobileBuildRecord()
                .put("timestamp", Instant.now().toString())
                .put("target", "android")
                .put("entry_sha256", MobileBuildRecord.sha256(projectRoot.resolve(config.entry())))
                .put("wasm_sha256", MobileBuildRecord.sha256(distDir().resolve("app.wasm")))
                .put("apk", apk.toString())
                .put("apk_sha256", MobileBuildRecord.sha256(apk))
                .put("android_application_id", config.androidApplicationId())
                .put("version", config.version())
                .put("build_number", config.buildNumber())
                .write(buildRecordPath());
        System.out.println("Android APK: " + apk);
        return apk;
    }

    public Path buildIosSimulator() throws Exception {
        buildWeb();
        syncPlatformAssets();
        MobileToolchain.Report report = MobileToolchain.probe(resolveJar());
        List<String> miss = report.missingFor("ios");
        // Allow build with SDK even if runtime missing — but report it.
        boolean hasXcode = report.checks().stream().anyMatch(c -> c.name().equals("Xcode") && c.ok());
        boolean hasSdk = report.checks().stream().anyMatch(c -> c.name().equals("iOS Simulator SDK") && c.ok());
        if (!hasXcode || !hasSdk) {
            throw new IllegalStateException("iOS toolchain incomplete:\n  - " + String.join("\n  - ", miss));
        }
        Path iosRoot = projectRoot.resolve(config.platformsRoot()).resolve("ios");
        applyIosIdentifiers(iosRoot);
        Path stamp = stagingDir().resolve("ios.ok");
        Files.deleteIfExists(stamp);

        Path xcodeproj = findXcodeproj(iosRoot);
        String scheme = detectScheme(iosRoot);
        List<String> destinations = List.of(
                "generic/platform=iOS Simulator",
                "platform=iOS Simulator,name=iPhone 15",
                "platform=iOS Simulator,name=iPhone 16"
        );
        IllegalStateException last = null;
        for (String dest : destinations) {
            List<String> cmd = List.of(
                    "xcodebuild",
                    "-project", xcodeproj.getFileName().toString(),
                    "-scheme", scheme,
                    "-destination", dest,
                    "-configuration", "Debug",
                    "CODE_SIGNING_ALLOWED=NO",
                    "build"
            );
            System.out.println("==> xcodebuild destination: " + dest);
            MobileProcess.Result r = MobileProcess.run(cmd, iosRoot, Map.of(), 600);
            if (r.timedOut()) {
                last = new IllegalStateException("iOS build timed out for " + dest);
                continue;
            }
            if (r.ok()) {
                Path app = findBuiltApp();
                Files.writeString(stamp, app != null ? app.toString() : "built", StandardCharsets.UTF_8);
                new MobileBuildRecord()
                        .put("timestamp", Instant.now().toString())
                        .put("target", "ios-simulator")
                        .put("entry_sha256", MobileBuildRecord.sha256(projectRoot.resolve(config.entry())))
                        .put("wasm_sha256", MobileBuildRecord.sha256(distDir().resolve("app.wasm")))
                        .put("app", app == null ? "" : app.toString())
                        .put("ios_bundle_id", config.iosBundleId())
                        .put("version", config.version())
                        .put("build_number", config.buildNumber())
                        .put("destination", dest)
                        .write(buildRecordPath());
                System.out.println("iOS build finished (" + dest + ")");
                if (app != null) {
                    System.out.println("App: " + app);
                }
                return app;
            }
            last = new IllegalStateException("iOS build failed for " + dest + "\n" + r.output());
        }
        throw last != null ? last : new IllegalStateException("iOS build failed");
    }

    private Path findBuiltApp() throws Exception {
        Path derived = Path.of(System.getProperty("user.home"),
                "Library/Developer/Xcode/DerivedData");
        if (!Files.isDirectory(derived)) {
            return null;
        }
        try (Stream<Path> stream = Files.walk(derived, 6)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".app"))
                    .filter(p -> p.toString().contains("Debug-iphonesimulator"))
                    .filter(p -> {
                        try {
                            return Files.isRegularFile(p.resolve("Info.plist"));
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .sorted((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified()))
                    .findFirst()
                    .orElse(null);
        }
    }

    private Path findXcodeproj(Path iosRoot) throws Exception {
        try (Stream<Path> stream = Files.list(iosRoot)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".xcodeproj"))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("no .xcodeproj under " + iosRoot));
        }
    }

    private String detectScheme(Path iosRoot) throws Exception {
        Path schemes = null;
        try (Stream<Path> stream = Files.walk(iosRoot, 4)) {
            schemes = stream
                    .filter(p -> p.toString().endsWith(".xcscheme"))
                    .findFirst()
                    .orElse(null);
        }
        if (schemes != null) {
            String name = schemes.getFileName().toString();
            return name.substring(0, name.length() - ".xcscheme".length());
        }
        return "App";
    }

    private void applyAndroidIdentifiers(Path androidRoot) throws Exception {
        Path gradle = androidRoot.resolve("app/build.gradle.kts");
        if (!Files.isRegularFile(gradle)) {
            gradle = androidRoot.resolve("app/build.gradle");
        }
        if (!Files.isRegularFile(gradle)) {
            return;
        }
        String text = Files.readString(gradle, StandardCharsets.UTF_8);
        String updated = text
                .replaceAll("applicationId\\s*=\\s*\"[^\"]+\"",
                        "applicationId = \"" + config.androidApplicationId() + "\"")
                .replaceAll("versionName\\s*=\\s*\"[^\"]+\"",
                        "versionName = \"" + config.version() + "\"")
                .replaceAll("versionCode\\s*=\\s*\\d+",
                        "versionCode = " + config.buildNumber());
        if (!updated.equals(text)) {
            Files.writeString(gradle, updated, StandardCharsets.UTF_8);
            System.out.println("==> applied Android id/version from jnative.toml");
        }
        Path strings = androidRoot.resolve("app/src/main/res/values/strings.xml");
        if (Files.isRegularFile(strings)) {
            String s = Files.readString(strings, StandardCharsets.UTF_8);
            String s2 = s.replaceAll(
                    "<string name=\"app_name\">[^<]*</string>",
                    "<string name=\"app_name\">" + xmlEscape(config.appName()) + "</string>");
            if (!s2.equals(s)) {
                Files.writeString(strings, s2, StandardCharsets.UTF_8);
            }
        } else {
            Files.createDirectories(androidRoot.resolve("app/src/main/res/values"));
            Files.writeString(androidRoot.resolve("app/src/main/res/values/strings.xml"),
                    "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n"
                            + "    <string name=\"app_name\">" + xmlEscape(config.appName()) + "</string>\n"
                            + "</resources>\n",
                    StandardCharsets.UTF_8);
        }
    }

    private void applyIosIdentifiers(Path iosRoot) throws Exception {
        try (Stream<Path> stream = Files.walk(iosRoot, 4)) {
            for (Path p : stream.filter(f -> f.getFileName().toString().equals("project.pbxproj")).toList()) {
                String text = Files.readString(p, StandardCharsets.UTF_8);
                String updated = text
                        .replaceAll("PRODUCT_BUNDLE_IDENTIFIER = [^;]+;",
                                "PRODUCT_BUNDLE_IDENTIFIER = " + config.iosBundleId() + ";")
                        .replaceAll("MARKETING_VERSION = [^;]+;",
                                "MARKETING_VERSION = " + config.version() + ";")
                        .replaceAll("CURRENT_PROJECT_VERSION = [^;]+;",
                                "CURRENT_PROJECT_VERSION = " + config.buildNumber() + ";");
                if (!updated.equals(text)) {
                    Files.writeString(p, updated, StandardCharsets.UTF_8);
                    System.out.println("==> applied iOS bundle id/version from jnative.toml");
                }
            }
        }
        try (Stream<Path> stream = Files.walk(iosRoot, 4)) {
            for (Path p : stream.filter(f -> f.getFileName().toString().equals("Info.plist")).toList()) {
                String text = Files.readString(p, StandardCharsets.UTF_8);
                if (text.contains("CFBundleDisplayName")) {
                    String updated = text.replaceAll(
                            "(<key>CFBundleDisplayName</key>\\s*<string>)[^<]*(</string>)",
                            "$1" + xmlEscape(config.appName()) + "$2");
                    if (!updated.equals(text)) {
                        Files.writeString(p, updated, StandardCharsets.UTF_8);
                    }
                }
            }
        }
    }

    private void requireFreshDist(Path dist) throws IOException {
        if (!Files.isRegularFile(dist.resolve("app.wasm"))
                || !Files.isRegularFile(dist.resolve("app.js"))
                || !Files.isRegularFile(dist.resolve("index.html"))) {
            throw new IllegalStateException("dist incomplete — build web first");
        }
    }

    private static void syncDir(Path dist, Path dest) throws IOException {
        deleteRecursively(dest);
        Files.createDirectories(dest);
        for (String name : List.of("app.wasm", "app.js", "index.html", "bridge.js", "app-shell.js", "build-info.js")) {
            Path src = dist.resolve(name);
            if (Files.isRegularFile(src)) {
                Files.copy(src, dest.resolve(name), StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private Path resolveJar() {
        if (jnativeJar != null && Files.isRegularFile(jnativeJar)) {
            return jnativeJar;
        }
        String env = System.getenv("JNATIVE_JAR");
        if (env != null && Files.isRegularFile(Path.of(env))) {
            return Path.of(env);
        }
        Path fromCwd = Path.of("target/jnative.jar").toAbsolutePath().normalize();
        if (Files.isRegularFile(fromCwd)) {
            return fromCwd;
        }
        return null;
    }

    private static void copyIfExists(Path src, Path dest) throws IOException {
        if (Files.isRegularFile(src)) {
            Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> paths = walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static String escapeJs(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String xmlEscape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
