/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.mobile;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
public class MobileProjectGeneratorTest {

    @TempDir
    Path parent;

    @Test
    void generateWritesTomlIdentifiersAndDoesNotOverwrite() throws Exception {
        Path dir = MobileProjectGenerator.generate(parent, new MobileProjectGenerator.Options(
                "HelloMobile",
                "Hello Mobile",
                "io.jnative.hello.mobile",
                "io.jnative.hello.mobile",
                "0.2.0",
                "7"
        ));
        assertTrue(Files.isRegularFile(dir.resolve("jnative.toml")));
        assertTrue(Files.isRegularFile(dir.resolve("src/App.java")));
        assertTrue(Files.isRegularFile(dir.resolve("web/bridge.js")));
        assertTrue(Files.isRegularFile(dir.resolve("platforms/android/app/build.gradle.kts")));
        assertTrue(Files.isRegularFile(dir.resolve("platforms/ios/App.xcodeproj/project.pbxproj")));

        String toml = Files.readString(dir.resolve("jnative.toml"), StandardCharsets.UTF_8);
        assertTrue(toml.contains("android_application_id = \"io.jnative.hello.mobile\""));
        assertTrue(toml.contains("build_number = \"7\""));

        String gradle = Files.readString(
                dir.resolve("platforms/android/app/build.gradle.kts"), StandardCharsets.UTF_8);
        assertTrue(gradle.contains("applicationId = \"io.jnative.hello.mobile\""));
        assertTrue(gradle.contains("versionCode = 7"));
        assertTrue(gradle.contains("versionName = \"0.2.0\""));

        String pbx = Files.readString(
                dir.resolve("platforms/ios/App.xcodeproj/project.pbxproj"), StandardCharsets.UTF_8);
        assertTrue(pbx.contains("PRODUCT_BUNDLE_IDENTIFIER = io.jnative.hello.mobile;"));
        assertTrue(pbx.contains("MARKETING_VERSION = 0.2.0;"));

        // Do not overwrite
        Files.writeString(dir.resolve("src/App.java"), "// user edit\n", StandardCharsets.UTF_8);
        MobileProjectGenerator.generate(parent, new MobileProjectGenerator.Options(
                "HelloMobile", "Hello Mobile", "io.jnative.hello.mobile",
                "io.jnative.hello.mobile", "0.2.0", "7"));
        assertEquals("// user edit\n", Files.readString(dir.resolve("src/App.java")));

        MobileConfig cfg = MobileConfig.load(dir);
        assertEquals("io.jnative.hello.mobile", cfg.androidApplicationId());
        assertEquals("7", cfg.buildNumber());
    }
}
