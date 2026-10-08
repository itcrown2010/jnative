package io.jnative.mobile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class MobileConfigTest {

    @TempDir
    Path dir;

    @Test
    void parsesAndValidatesMobileSection() throws Exception {
        Files.createDirectories(dir.resolve("src"));
        Files.writeString(dir.resolve("src/App.java"),
                "public class App { public static void main(String[] a){} }\n", StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("jnative.toml"), """
                [package]
                name = "demo"
                version = "1.2.3"

                [mobile]
                app_name = "Demo"
                android_application_id = "io.example.demo"
                ios_bundle_id = "io.example.demo"
                version = "1.2.3"
                build_number = "9"
                entry = "src/App.java"
                targets = ["web", "android"]
                capabilities = ["platform.getName", "storage.get"]
                """, StandardCharsets.UTF_8);

        MobileConfig cfg = MobileConfig.load(dir);
        assertEquals("Demo", cfg.appName());
        assertEquals("io.example.demo", cfg.androidApplicationId());
        assertEquals("9", cfg.buildNumber());
        assertEquals(2, cfg.targets().size());
    }

    @Test
    void rejectsBadAndroidIdAndDuplicateTargets() {
        String toml = """
                [mobile]
                app_name = "X"
                android_application_id = "not-a-valid-id"
                ios_bundle_id = "io.ok.app"
                entry = "src/App.java"
                targets = ["web", "web"]
                """;
        assertThrows(MobileConfigException.class, () -> MobileConfig.parse(toml, dir));
    }

    @Test
    void rejectsMissingEntryFile() throws Exception {
        Files.writeString(dir.resolve("jnative.toml"), """
                [mobile]
                app_name = "X"
                android_application_id = "io.ok.app"
                ios_bundle_id = "io.ok.app"
                entry = "src/Missing.java"
                targets = ["web"]
                """, StandardCharsets.UTF_8);
        assertThrows(MobileConfigException.class, () -> MobileConfig.load(dir));
    }
}
