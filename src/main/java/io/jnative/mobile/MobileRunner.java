/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.mobile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Install + launch only after an explicit run command and a successful fresh build stamp.
 * @author 张晓达
 */
public final class MobileRunner {

    private final Path projectRoot;
    private final MobileConfig config;

    public MobileRunner(Path projectRoot, MobileConfig config) {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.config = config;
    }

    public void runAndroid(String deviceId) throws Exception {
        Path stamp = projectRoot.resolve(".jnative/mobile-staging/android.ok");
        if (!Files.isRegularFile(stamp)) {
            throw new IllegalStateException(
                    "no fresh Android build stamp — run `jnative mobile build --target android` first");
        }
        Path apk = Path.of(Files.readString(stamp, StandardCharsets.UTF_8).trim());
        if (!Files.isRegularFile(apk)) {
            throw new IllegalStateException("stamped APK missing (rebuild required): " + apk);
        }
        MobileDevices.Device device = MobileDevices.requireOne("android", deviceId);
        if (!"device".equals(device.state())) {
            throw new IllegalStateException("Android device not ready: " + device.id()
                    + " state=" + device.state());
        }
        String adb = resolveAdb();
        System.out.println("==> installing on " + device.id() + " (" + device.name() + ")");
        MobileProcess.Result install = MobileProcess.run(
                List.of(adb, "-s", device.id(), "install", "-r", apk.toString()),
                null, androidEnv(), 180);
        if (install.timedOut()) {
            throw new IllegalStateException("adb install timed out");
        }
        if (!install.ok()) {
            throw new IllegalStateException("adb install failed:\n" + install.output());
        }
        String activity = config.androidApplicationId() + "/.MainActivity";
        System.out.println("==> starting " + activity);
        MobileProcess.Result start = MobileProcess.run(
                List.of(adb, "-s", device.id(), "shell", "am", "start", "-n", activity),
                null, androidEnv(), 60);
        if (start.timedOut()) {
            throw new IllegalStateException("am start timed out");
        }
        if (!start.ok() && !start.output().toLowerCase(Locale.ROOT).contains("starting")) {
            throw new IllegalStateException("am start failed:\n" + start.output());
        }
        System.out.println("Launched on " + device.id());
    }

    public void runIos(String deviceId) throws Exception {
        Path stamp = projectRoot.resolve(".jnative/mobile-staging/ios.ok");
        if (!Files.isRegularFile(stamp)) {
            throw new IllegalStateException(
                    "no fresh iOS build stamp — run `jnative mobile build --target ios` first");
        }
        String stamped = Files.readString(stamp, StandardCharsets.UTF_8).trim();
        Path app = Path.of(stamped);
        if (!Files.isDirectory(app) || !stamped.endsWith(".app")) {
            throw new IllegalStateException(
                    "iOS app path missing from stamp — rebuild with a destination that produces .app: "
                            + stamped);
        }
        MobileDevices.Device device = MobileDevices.requireOne("ios", deviceId);
        System.out.println("==> booting " + device.name() + " (" + device.id() + ")");
        MobileProcess.run(List.of("xcrun", "simctl", "boot", device.id()), null, null, 60);
        MobileProcess.run(List.of("open", "-a", "Simulator"), null, null, 30);
        System.out.println("==> installing " + app);
        MobileProcess.Result install = MobileProcess.run(
                List.of("xcrun", "simctl", "install", device.id(), app.toString()),
                null, null, 120);
        if (install.timedOut()) {
            throw new IllegalStateException("simctl install timed out");
        }
        if (!install.ok()) {
            throw new IllegalStateException("simctl install failed:\n" + install.output());
        }
        System.out.println("==> launching " + config.iosBundleId());
        MobileProcess.Result launch = MobileProcess.run(
                List.of("xcrun", "simctl", "launch", device.id(), config.iosBundleId()),
                null, null, 60);
        if (launch.timedOut()) {
            throw new IllegalStateException("simctl launch timed out");
        }
        if (!launch.ok()) {
            throw new IllegalStateException("simctl launch failed:\n" + launch.output());
        }
        System.out.println("Launched on " + device.name());
    }

    public void logsAndroid(String deviceId, int lines) throws Exception {
        MobileDevices.Device device = MobileDevices.requireOne("android", deviceId);
        String adb = resolveAdb();
        MobileProcess.run(
                List.of(adb, "-s", device.id(), "logcat", "-d", "-t", String.valueOf(lines),
                        "*:S", "chromium:V", "Console:V", "JNative:V", "AndroidRuntime:E"),
                null, androidEnv(), 60);
    }

    public void logsIos(String deviceId, int lines) throws Exception {
        MobileDevices.Device device = MobileDevices.requireOne("ios", deviceId);
        // Predicate avoids dumping storage values; process name filter only.
        MobileProcess.run(
                List.of("xcrun", "simctl", "spawn", device.id(), "log", "show",
                        "--last", lines + "s", "--predicate",
                        "process == \"App\" OR process == \"Calculator\""
                                + " OR processImagePath CONTAINS \"jnative\""
                                + " OR processImagePath CONTAINS \"guidetasks\""),
                null, null, 60);
    }

    /** Stop an app previously launched via {@code mobile run} (does not kill unrelated processes). */
    public void stopAndroid(String deviceId) throws Exception {
        MobileDevices.Device device = MobileDevices.requireOne("android", deviceId);
        String adb = resolveAdb();
        MobileProcess.Result r = MobileProcess.run(
                List.of(adb, "-s", device.id(), "shell", "am", "force-stop",
                        config.androidApplicationId()),
                null, androidEnv(), 30);
        if (!r.ok()) {
            throw new IllegalStateException("adb force-stop failed:\n" + r.output());
        }
        System.out.println("Stopped " + config.androidApplicationId() + " on " + device.id());
    }

    public void stopIos(String deviceId) throws Exception {
        MobileDevices.Device device = MobileDevices.requireOne("ios", deviceId);
        MobileProcess.Result r = MobileProcess.run(
                List.of("xcrun", "simctl", "terminate", device.id(), config.iosBundleId()),
                null, null, 30);
        if (!r.ok() && !r.output().toLowerCase(Locale.ROOT).contains("not running")) {
            throw new IllegalStateException("simctl terminate failed:\n" + r.output());
        }
        System.out.println("Stopped " + config.iosBundleId() + " on " + device.name());
    }

    private static Map<String, String> androidEnv() {
        Map<String, String> env = new HashMap<>();
        Path sdk = MobileToolchain.androidSdk();
        if (sdk != null) {
            env.put("ANDROID_HOME", sdk.toString());
            env.put("ANDROID_SDK_ROOT", sdk.toString());
        }
        return env;
    }

    private static String resolveAdb() {
        Path sdk = MobileToolchain.androidSdk();
        if (sdk != null) {
            Path adb = sdk.resolve("platform-tools/adb");
            if (Files.isExecutable(adb)) {
                return adb.toString();
            }
        }
        String w = MobileToolchain.which("adb");
        if (w == null) {
            throw new IllegalStateException("adb not found");
        }
        return w;
    }
}
