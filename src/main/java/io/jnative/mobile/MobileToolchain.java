package io.jnative.mobile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Reports availability of tools needed for mobile targets.
 * Never downloads SDKs, accepts licenses, or changes system/signing settings.
 * @author 张晓达
 */
public final class MobileToolchain {

    public record Check(String name, boolean ok, String detail) {
    }

    public record Report(List<Check> checks) {
        public boolean healthyFor(String target) {
            return switch (target.toLowerCase(Locale.ROOT)) {
                case "web" -> ok("JNative compiler") && ok("wat2wasm");
                case "android" -> ok("JNative compiler") && ok("wat2wasm")
                        && ok("Android SDK") && ok("Java (Android)");
                case "ios" -> ok("JNative compiler") && ok("wat2wasm") && ok("Xcode") && ok("iOS Simulator SDK");
                default -> false;
            };
        }

        private boolean ok(String name) {
            return checks.stream().anyMatch(c -> c.name().equals(name) && c.ok());
        }

        public List<String> missingFor(String target) {
            List<String> miss = new ArrayList<>();
            for (Check c : checks) {
                if (!c.ok() && neededFor(target, c.name())) {
                    miss.add(c.name() + (c.detail() == null || c.detail().isBlank() ? "" : " — " + c.detail()));
                }
            }
            return miss;
        }

        private static boolean neededFor(String target, String check) {
            return switch (target.toLowerCase(Locale.ROOT)) {
                case "web" -> check.startsWith("JNative") || check.equals("wat2wasm");
                case "android" -> !check.equals("Xcode") && !check.equals("iOS Simulator SDK")
                        && !check.equals("iOS Simulator runtime / device");
                case "ios" -> !check.startsWith("Android") && !check.equals("Gradle wrapper or gradle")
                        && !check.equals("Java (Android)") && !check.equals("adb");
                default -> true;
            };
        }
    }

    private MobileToolchain() {
    }

    public static Report probe(Path jnativeJarHint) {
        List<Check> checks = new ArrayList<>();
        checks.add(checkJnative(jnativeJarHint));
        checks.add(checkOnPath("wat2wasm", "wat2wasm --version"));
        checks.add(checkJava());
        checks.add(checkAndroidSdk());
        checks.add(checkGradle());
        checks.add(checkAdb());
        checks.add(checkXcode());
        checks.add(checkIosSimulatorSdk());
        checks.add(checkIosSimulatorRuntime());
        return new Report(checks);
    }

    private static Check checkJnative(Path jarHint) {
        Path jar = jarHint;
        if (jar == null || !Files.isRegularFile(jar)) {
            String home = System.getenv("JNATIVE_HOME");
            if (home != null) {
                Path p = Path.of(home, "target", "jnative.jar");
                if (Files.isRegularFile(p)) {
                    jar = p;
                }
            }
        }
        if (jar != null && Files.isRegularFile(jar)) {
            return new Check("JNative compiler", true, jar.toString());
        }
        // Running from the same process means compiler classes are available.
        try {
            Class.forName("io.jnative.compiler.JNativeCompiler");
            return new Check("JNative compiler", true, "in-process");
        } catch (ClassNotFoundException e) {
            return new Check("JNative compiler", false, "target/jnative.jar not found; run mvn -DskipTests package");
        }
    }

    private static Check checkJava() {
        String v = System.getProperty("java.version", "");
        return new Check("Java (Android)", !v.isBlank(), "java.version=" + v);
    }

    private static Check checkAndroidSdk() {
        Path sdk = androidSdk();
        if (sdk != null && Files.isDirectory(sdk)) {
            return new Check("Android SDK", true, sdk.toString());
        }
        return new Check("Android SDK", false, "set ANDROID_HOME or ANDROID_SDK_ROOT");
    }

    private static Check checkGradle() {
        String gradle = which("gradle");
        // Not a hard failure: mobile projects ship / generate gradlew when available.
        return new Check("Gradle wrapper or gradle", true,
                gradle != null ? gradle : "system gradle optional; project ./gradlew used when present");
    }

    private static Check checkAdb() {
        Path sdk = androidSdk();
        if (sdk != null) {
            Path adb = sdk.resolve("platform-tools/adb");
            if (Files.isExecutable(adb)) {
                return new Check("adb", true, adb.toString());
            }
        }
        String adb = which("adb");
        return new Check("adb", adb != null, adb != null ? adb : "Android platform-tools missing");
    }

    private static Check checkXcode() {
        String path = which("xcodebuild");
        if (path == null) {
            return new Check("Xcode", false, "xcodebuild not on PATH");
        }
        String ver = runCapture(new String[]{"xcodebuild", "-version"}, 15);
        return new Check("Xcode", ver != null && ver.contains("Xcode"),
                ver == null ? path : ver.lines().findFirst().orElse(path));
    }

    private static Check checkIosSimulatorSdk() {
        String out = runCapture(new String[]{"xcodebuild", "-showsdks"}, 20);
        boolean ok = out != null && out.toLowerCase(Locale.ROOT).contains("iphonesimulator");
        return new Check("iOS Simulator SDK", ok,
                ok ? "iphonesimulator SDK present" : "iphonesimulator SDK missing");
    }

    private static Check checkIosSimulatorRuntime() {
        String out = runCapture(new String[]{"xcrun", "simctl", "list", "devices", "available"}, 60);
        if (out == null) {
            return new Check("iOS Simulator runtime / device", false, "simctl failed");
        }
        boolean hasBootable = out.lines().anyMatch(l ->
                l.contains("iPhone") && l.contains("(") && !l.contains("unavailable"));
        return new Check("iOS Simulator runtime / device", hasBootable,
                hasBootable ? "simctl lists iPhone devices" : "no bootable iPhone simulator (install runtime in Xcode Components)");
    }

    private static Check checkOnPath(String name, String versionCmd) {
        String path = which(name);
        if (path == null) {
            return new Check(name, false, "not found on PATH");
        }
        String[] parts = versionCmd.split("\\s+");
        String ver = runCapture(parts, 10);
        return new Check(name, true, ver == null ? path : ver.lines().findFirst().orElse(path));
    }

    public static Path androidSdk() {
        for (String key : List.of("ANDROID_HOME", "ANDROID_SDK_ROOT")) {
            String v = System.getenv(key);
            if (v != null && !v.isBlank()) {
                Path p = Path.of(v);
                if (Files.isDirectory(p)) {
                    return p;
                }
            }
        }
        Path def = Path.of(System.getProperty("user.home"), "Library", "Android", "sdk");
        return Files.isDirectory(def) ? def : null;
    }

    public static String which(String cmd) {
        String path = System.getenv("PATH");
        if (path == null) {
            return null;
        }
        for (String dir : path.split(java.io.File.pathSeparator)) {
            Path p = Path.of(dir, cmd);
            if (Files.isExecutable(p)) {
                return p.toString();
            }
        }
        return null;
    }

    public static String runCapture(String[] cmd, long timeoutSec) {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            }
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            return p.exitValue() == 0 ? sb.toString() : sb.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
