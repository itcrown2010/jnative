package io.jnative.mobile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Creates a mobile-app project from the built-in template.
 * Never overwrites existing files. Upgrades must not clobber user native code.
 * @author 张晓达
 */
public final class MobileProjectGenerator {

    public record Options(
            String projectName,
            String appName,
            String androidApplicationId,
            String iosBundleId,
            String version,
            String buildNumber
    ) {
    }

    private MobileProjectGenerator() {
    }

    public static Path generate(Path parentDir, Options opt) throws IOException {
        String name = opt.projectName();
        if (!name.matches("[A-Za-z_][A-Za-z0-9_-]*")) {
            throw new MobileConfigException("invalid project name: " + name);
        }
        String androidId = opt.androidApplicationId() != null && !opt.androidApplicationId().isBlank()
                ? opt.androidApplicationId()
                : ("io.jnative." + name.toLowerCase(Locale.ROOT).replace("-", "_"));
        String iosId = opt.iosBundleId() != null && !opt.iosBundleId().isBlank()
                ? opt.iosBundleId()
                : androidId;
        String appName = opt.appName() != null && !opt.appName().isBlank() ? opt.appName() : name;
        String version = opt.version() != null ? opt.version() : "0.1.0";
        String buildNumber = opt.buildNumber() != null ? opt.buildNumber() : "1";

        MobileConfig probe = new MobileConfig(
                appName, androidId, iosId, version, buildNumber,
                "src/App.java", "web", "web/index.html", "platforms",
                List.of("web", "android", "ios"),
                List.of("platform.getName", "storage.get", "storage.set", "storage.remove", "lifecycle"));
        probe.validateForGenerate();

        Path dir = parentDir.resolve(name).toAbsolutePath().normalize();
        Files.createDirectories(dir);

        Map<String, String> vars = new LinkedHashMap<>();
        vars.put("PROJECT_NAME", name);
        vars.put("APP_NAME", appName);
        vars.put("ANDROID_APPLICATION_ID", androidId);
        vars.put("IOS_BUNDLE_ID", iosId);
        vars.put("VERSION", version);
        vars.put("BUILD_NUMBER", buildNumber);
        vars.put("ANDROID_NAMESPACE_PATH", androidId.replace('.', '/'));

        writeNew(dir.resolve("jnative.toml"), render("jnative.toml.tmpl", vars));
        writeNew(dir.resolve("README.md"), render("README.md.tmpl", vars));
        writeNew(dir.resolve(".gitignore"), """
                /dist/
                /.jnative/
                /platforms/android/.gradle/
                /platforms/android/app/build/
                /platforms/android/local.properties
                /platforms/android/app/src/main/assets/www/*
                !/platforms/android/app/src/main/assets/www/.gitkeep
                /platforms/ios/App/www/*
                !/platforms/ios/App/www/.gitkeep
                *.xcuserstate
                xcuserdata/
                DerivedData/
                """);
        writeNew(dir.resolve("src/App.java"), render("src/App.java.tmpl", vars));
        writeNew(dir.resolve("assets/.gitkeep"), "");
        writeNew(dir.resolve("web/index.html"), render("web/index.html.tmpl", vars));
        writeNew(dir.resolve("web/bridge.js"), loadResource("web/bridge.js"));
        writeNew(dir.resolve("web/app-shell.js"), render("web/app-shell.js.tmpl", vars));

        // Android skeleton
        String nsPath = androidId.replace('.', '/');
        writeNew(dir.resolve("platforms/android/settings.gradle.kts"), """
                pluginManagement {
                    repositories { google(); mavenCentral(); gradlePluginPortal() }
                }
                dependencyResolutionManagement {
                    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                    repositories { google(); mavenCentral() }
                }
                rootProject.name = "JNativeMobile"
                include(":app")
                """);
        writeNew(dir.resolve("platforms/android/build.gradle.kts"), """
                plugins {
                    id("com.android.application") version "8.5.2" apply false
                    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
                }
                """);
        writeNew(dir.resolve("platforms/android/gradle.properties"), """
                org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
                android.useAndroidX=true
                kotlin.code.style=official
                """);
        writeNew(dir.resolve("platforms/android/app/build.gradle.kts"), render("android/app/build.gradle.kts.tmpl", vars));
        writeNew(dir.resolve("platforms/android/app/src/main/AndroidManifest.xml"),
                render("android/AndroidManifest.xml.tmpl", vars));
        writeNew(dir.resolve("platforms/android/app/src/main/res/values/strings.xml"),
                "<resources>\n    <string name=\"app_name\">" + xml(appName) + "</string>\n</resources>\n");
        writeNew(dir.resolve("platforms/android/app/src/main/res/values/themes.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <resources>
                    <style name="Theme.JNativeMobile" parent="Theme.MaterialComponents.DayNight.NoActionBar"/>
                </resources>
                """);
        writeNew(dir.resolve("platforms/android/app/src/main/res/layout/activity_main.xml"), """
                <?xml version="1.0" encoding="utf-8"?>
                <FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
                    android:id="@+id/root"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:fitsSystemWindows="true">
                    <WebView
                        android:id="@+id/webview"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent" />
                </FrameLayout>
                """);
        writeNew(dir.resolve("platforms/android/app/src/main/java/" + nsPath + "/MainActivity.kt"),
                render("android/MainActivity.kt.tmpl", vars));
        writeNew(dir.resolve("platforms/android/app/src/main/java/" + nsPath + "/BridgeHost.kt"),
                render("android/BridgeHost.kt.tmpl", vars));
        writeNew(dir.resolve("platforms/android/app/src/main/assets/www/.gitkeep"), "");

        // Copy gradle wrapper from calculator example if present, else note in README
        copyGradleWrapperIfAvailable(dir.resolve("platforms/android"));

        // iOS skeleton
        writeNew(dir.resolve("platforms/ios/App/AppDelegate.swift"), render("ios/AppDelegate.swift.tmpl", vars));
        writeNew(dir.resolve("platforms/ios/App/ViewController.swift"), render("ios/ViewController.swift.tmpl", vars));
        writeNew(dir.resolve("platforms/ios/App/BridgeHost.swift"), render("ios/BridgeHost.swift.tmpl", vars));
        writeNew(dir.resolve("platforms/ios/App/Info.plist"), render("ios/Info.plist.tmpl", vars));
        writeNew(dir.resolve("platforms/ios/App/www/.gitkeep"), "");
        writeNew(dir.resolve("platforms/ios/App.xcodeproj/project.pbxproj"), render("ios/project.pbxproj.tmpl", vars));
        writeNew(dir.resolve("platforms/ios/App.xcodeproj/xcshareddata/xcschemes/App.xcscheme"),
                render("ios/App.xcscheme.tmpl", vars));

        writeNew(dir.resolve("jnative.project"), """
                {
                  "name": "%s",
                  "source": ["src"],
                  "main": "App",
                  "output": "dist"
                }
                """.formatted(name));

        return dir;
    }

    /** Write only if missing — never overwrite user files. */
    private static void writeNew(Path path, String content) throws IOException {
        if (Files.exists(path)) {
            System.out.println("skip existing " + path);
            return;
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static String render(String resource, Map<String, String> vars) throws IOException {
        String text = loadResource(resource);
        for (Map.Entry<String, String> e : vars.entrySet()) {
            text = text.replace("{{" + e.getKey() + "}}", e.getValue());
        }
        return text;
    }

    private static String loadResource(String name) throws IOException {
        String path = "/templates/mobile-app/" + name;
        try (InputStream in = MobileProjectGenerator.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("missing template resource: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static void copyGradleWrapperIfAvailable(Path androidRoot) throws IOException {
        Path example = Path.of("examples/mobile/calculator/android").toAbsolutePath().normalize();
        if (!Files.isDirectory(example)) {
            // Try relative to jar location heuristics — optional
            return;
        }
        for (String rel : List.of("gradlew", "gradlew.bat", "gradle/wrapper/gradle-wrapper.jar",
                "gradle/wrapper/gradle-wrapper.properties")) {
            Path src = example.resolve(rel);
            Path dest = androidRoot.resolve(rel);
            if (Files.isRegularFile(src) && !Files.exists(dest)) {
                Files.createDirectories(dest.getParent());
                Files.copy(src, dest);
                if (rel.equals("gradlew")) {
                    dest.toFile().setExecutable(true);
                }
            }
        }
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;");
    }
}
