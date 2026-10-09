package io.jnative.cli;

import io.jnative.mobile.MobileBuilder;
import io.jnative.mobile.MobileConfig;
import io.jnative.mobile.MobileConfigException;
import io.jnative.mobile.MobileDevices;
import io.jnative.mobile.MobileProjectGenerator;
import io.jnative.mobile.MobileRunner;
import io.jnative.mobile.MobileToolchain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Mobile project CLI:
 * <pre>
 * jnative mobile build --target web|android|ios [--project DIR]
 * jnative mobile devices --target android|ios
 * jnative mobile run --target android|ios --device ID [--project DIR]
 * jnative mobile logs --target android|ios [--device ID] [--project DIR]
 * </pre>
 * @author 张晓达
 */
public final class MobileCli {

    private MobileCli() {
    }

    public static int dispatch(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println(usage());
            return 1;
        }
        return switch (args[1]) {
            case "build" -> build(args);
            case "devices" -> devices(args);
            case "run" -> run(args);
            case "logs" -> logs(args);
            case "help", "-h", "--help" -> {
                System.out.println(usage());
                yield 0;
            }
            default -> {
                System.err.println("unknown mobile command: " + args[1]);
                System.err.println(usage());
                yield 1;
            }
        };
    }

    public static int doctorMobile(Path jarHint) {
        System.out.println("Mobile toolchain");
        System.out.println();
        MobileToolchain.Report report = MobileToolchain.probe(jarHint);
        boolean anyFail = false;
        for (MobileToolchain.Check c : report.checks()) {
            System.out.println((c.ok() ? "✓" : "✗") + " " + c.name());
            if (!c.ok()) {
                anyFail = true;
            }
            if (c.detail() != null && !c.detail().isBlank()) {
                System.out.println("  " + c.detail());
            }
        }
        System.out.println();
        for (String t : new String[]{"web", "android", "ios"}) {
            boolean ok = report.healthyFor(t);
            // iOS build can succeed with SDK even if runtime missing
            if ("ios".equals(t)) {
                boolean xcode = report.checks().stream().anyMatch(c -> c.name().equals("Xcode") && c.ok());
                boolean sdk = report.checks().stream().anyMatch(c -> c.name().equals("iOS Simulator SDK") && c.ok());
                ok = xcode && sdk;
            }
            System.out.println("target " + t + ": " + (ok ? "available" : "incomplete"));
            if (!ok) {
                for (String m : report.missingFor(t)) {
                    System.out.println("  - " + m);
                }
            }
        }
        System.out.println();
        System.out.println("Doctor never downloads SDKs, accepts licenses, or changes signing settings.");
        return anyFail ? 1 : 0;
    }

    private static int build(String[] args) throws Exception {
        Path project = Path.of(".").toAbsolutePath().normalize();
        String target = null;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--project" -> project = Path.of(args[++i]).toAbsolutePath().normalize();
                case "--target" -> target = args[++i];
                default -> {
                    if (args[i].startsWith("--target=")) {
                        target = args[i].substring("--target=".length());
                    }
                }
            }
        }
        if (target == null || target.isBlank()) {
            System.err.println("error: --target web|android|ios required");
            return 1;
        }
        target = target.toLowerCase(Locale.ROOT);
        try {
            MobileConfig config = MobileConfig.load(project);
            Path jar = Path.of("target/jnative.jar").toAbsolutePath().normalize();
            if (!Files.isRegularFile(jar)) {
                String env = System.getenv("JNATIVE_JAR");
                jar = env != null ? Path.of(env) : jar;
            }
            MobileBuilder builder = new MobileBuilder(project, config, jar);
            switch (target) {
                case "web" -> builder.buildWeb();
                case "android" -> builder.buildAndroid();
                case "ios" -> builder.buildIosSimulator();
                default -> {
                    System.err.println("error: unknown target " + target);
                    return 1;
                }
            }
            System.out.println("Build record: " + builder.buildRecordPath());
            return 0;
        } catch (MobileConfigException e) {
            System.err.println("error: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            return 1;
        }
    }

    private static int devices(String[] args) throws Exception {
        String target = null;
        for (int i = 2; i < args.length; i++) {
            if ("--target".equals(args[i]) && i + 1 < args.length) {
                target = args[++i];
            }
        }
        if (target == null) {
            System.err.println("error: --target android|ios required");
            return 1;
        }
        try {
            var list = MobileDevices.list(target);
            System.out.println(target + " devices:");
            System.out.print(MobileDevices.format(list));
            return 0;
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            return 1;
        }
    }

    private static int run(String[] args) throws Exception {
        Path project = Path.of(".").toAbsolutePath().normalize();
        String target = null;
        String device = null;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--project" -> project = Path.of(args[++i]).toAbsolutePath().normalize();
                case "--target" -> target = args[++i];
                case "--device" -> device = args[++i];
                default -> {
                }
            }
        }
        if (target == null) {
            System.err.println("error: --target android|ios required");
            return 1;
        }
        try {
            MobileConfig config = MobileConfig.load(project);
            MobileRunner runner = new MobileRunner(project, config);
            switch (target.toLowerCase(Locale.ROOT)) {
                case "android" -> runner.runAndroid(device);
                case "ios" -> runner.runIos(device);
                default -> {
                    System.err.println("error: run supports android|ios only");
                    return 1;
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            return 1;
        }
    }

    private static int logs(String[] args) throws Exception {
        Path project = Path.of(".").toAbsolutePath().normalize();
        String target = null;
        String device = null;
        int lines = 200;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--project" -> project = Path.of(args[++i]).toAbsolutePath().normalize();
                case "--target" -> target = args[++i];
                case "--device" -> device = args[++i];
                case "--lines" -> lines = Integer.parseInt(args[++i]);
                default -> {
                }
            }
        }
        if (target == null) {
            System.err.println("error: --target android|ios required");
            return 1;
        }
        try {
            MobileConfig config = MobileConfig.load(project);
            MobileRunner runner = new MobileRunner(project, config);
            switch (target.toLowerCase(Locale.ROOT)) {
                case "android" -> runner.logsAndroid(device, lines);
                case "ios" -> runner.logsIos(device, lines);
                default -> {
                    System.err.println("error: logs supports android|ios only");
                    return 1;
                }
            }
            return 0;
        } catch (Exception e) {
            System.err.println("error: " + e.getMessage());
            return 1;
        }
    }

    public static int newMobileProject(String[] args) throws Exception {
        // args: new <name> --template mobile [--dir DIR] [--app-name ...] [--android-id ...] [--ios-id ...]
        if (args.length < 2) {
            System.err.println("usage: jnative new <name> --template mobile [--dir DIR]");
            return 1;
        }
        String name = args[1].trim();
        Path parent = Path.of(".").toAbsolutePath().normalize();
        String appName = name;
        String androidId = null;
        String iosId = null;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--dir" -> parent = Path.of(args[++i]).toAbsolutePath().normalize();
                case "--template" -> i++; // already known
                case "--app-name" -> appName = args[++i];
                case "--android-id" -> androidId = args[++i];
                case "--ios-id" -> iosId = args[++i];
                default -> {
                }
            }
        }
        Path dest = parent.resolve(name);
        if (Files.exists(dest.resolve("jnative.toml")) || Files.exists(dest.resolve("src/App.java"))) {
            System.err.println("error: refusing to overwrite existing project files in " + dest);
            return 1;
        }
        try {
            Path dir = MobileProjectGenerator.generate(parent, new MobileProjectGenerator.Options(
                    name, appName, androidId, iosId, "0.1.0", "1"));
            System.out.println();
            System.out.println("Created mobile project `" + name + "` at " + dir);
            System.out.println("Next:");
            System.out.println("  cd " + dir);
            System.out.println("  jnative doctor --mobile");
            System.out.println("  jnative mobile build --target web");
            System.out.println();
            return 0;
        } catch (MobileConfigException e) {
            System.err.println("error: " + e.getMessage());
            return 1;
        }
    }

    private static String usage() {
        return """
                usage:
                  jnative mobile build --target web|android|ios [--project DIR]
                  jnative mobile devices --target android|ios
                  jnative mobile run --target android|ios --device ID [--project DIR]
                  jnative mobile logs --target android|ios [--device ID] [--lines N]
                """;
    }
}
