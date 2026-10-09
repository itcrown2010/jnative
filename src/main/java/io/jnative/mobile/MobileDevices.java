package io.jnative.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Lists Android devices/emulators and iOS simulators. Never guesses a target.
 * @author 张晓达
 */
public final class MobileDevices {

    public record Device(String id, String name, String platform, String state) {
    }

    private MobileDevices() {
    }

    public static List<Device> list(String target) throws Exception {
        return switch (target.toLowerCase(Locale.ROOT)) {
            case "android" -> listAndroid();
            case "ios" -> listIos();
            default -> throw new IllegalArgumentException("devices target must be android or ios");
        };
    }

    public static List<Device> listAndroid() throws Exception {
        PathAdb adb = resolveAdb();
        if (adb.path() == null) {
            throw new IllegalStateException("adb not found — install Android platform-tools");
        }
        MobileProcess.Result r = MobileProcess.run(
                List.of(adb.path(), "devices", "-l"), null, null, 30);
        if (r.timedOut()) {
            throw new IllegalStateException("adb devices timed out");
        }
        List<Device> out = new ArrayList<>();
        for (String line : r.output().split("\n")) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("List of devices")) {
                continue;
            }
            String[] parts = line.split("\\s+");
            if (parts.length < 2) {
                continue;
            }
            String id = parts[0];
            String state = parts[1];
            String name = id;
            Matcher m = Pattern.compile("model:(\\S+)").matcher(line);
            if (m.find()) {
                name = m.group(1).replace('_', ' ');
            }
            out.add(new Device(id, name, "android", state));
        }
        return out;
    }

    public static List<Device> listIos() {
        String out = MobileToolchain.runCapture(
                new String[]{"xcrun", "simctl", "list", "devices", "available"}, 60);
        List<Device> devices = new ArrayList<>();
        if (out == null) {
            return devices;
        }
        Pattern p = Pattern.compile("^\\s*(.+)\\s\\(([A-F0-9-]{36})\\)\\s\\(([^)]+)\\)\\s*$");
        for (String line : out.split("\n")) {
            if (!line.contains("iPhone") && !line.contains("iPad")) {
                continue;
            }
            Matcher m = p.matcher(line);
            if (m.find()) {
                devices.add(new Device(m.group(2), m.group(1).trim(), "ios", m.group(3)));
            }
        }
        return devices;
    }

    public static Device requireOne(String target, String deviceId) throws Exception {
        List<Device> all = list(target);
        if (deviceId != null && !deviceId.isBlank()) {
            return all.stream()
                    .filter(d -> d.id().equals(deviceId) || d.name().equals(deviceId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "device not found: " + deviceId + "\n" + format(all)));
        }
        List<Device> ready = all.stream()
                .filter(d -> "device".equals(d.state()) || "Booted".equals(d.state())
                        || "Shutdown".equals(d.state()))
                .toList();
        if ("android".equals(target.toLowerCase(Locale.ROOT))) {
            ready = all.stream().filter(d -> "device".equals(d.state())).toList();
        }
        if (ready.isEmpty()) {
            throw new IllegalStateException("no usable " + target + " devices.\n" + format(all)
                    + "\nStart an emulator/simulator or connect a device, then pass --device <id>.");
        }
        if (ready.size() > 1) {
            throw new IllegalStateException(
                    "multiple " + target + " devices — pass --device <id> explicitly:\n" + format(ready));
        }
        return ready.get(0);
    }

    public static String format(List<Device> devices) {
        if (devices.isEmpty()) {
            return "  (none)";
        }
        StringBuilder sb = new StringBuilder();
        for (Device d : devices) {
            sb.append("  ").append(d.id()).append("  ").append(d.name())
                    .append("  [").append(d.state()).append("]\n");
        }
        return sb.toString();
    }

    private record PathAdb(String path) {
    }

    private static PathAdb resolveAdb() {
        var sdk = MobileToolchain.androidSdk();
        if (sdk != null) {
            var adb = sdk.resolve("platform-tools/adb");
            if (java.nio.file.Files.isExecutable(adb)) {
                return new PathAdb(adb.toString());
            }
        }
        return new PathAdb(MobileToolchain.which("adb"));
    }
}
