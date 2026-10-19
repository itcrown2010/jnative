/**
 * JNative Windows Explorer - persisted user settings (theme, accent colour, view options).
 * Stored as a tiny key=value text file under the per-user config dir
 * (%APPDATA%\JNativeExplorer\settings.ini on Windows).
 *
 * @author 张晓达
 */
class FmSettings {
    static String filePath() {
        return Files.configDir("JNativeExplorer").resolve("settings.ini").text;
    }

    /** Parses a non-negative decimal integer; returns def for anything else. */
    static int parseInt(String s, int def) {
        String t = s.trim();
        if (t.length() == 0 || t.length() > 6) {
            return def;
        }
        int v = 0;
        int i = 0;
        while (i < t.length()) {
            int c = t.charAt(i);
            if (c < 48 || c > 57) {
                return def;
            }
            v = v * 10 + (c - 48);
            i = i + 1;
        }
        return v;
    }

    /** Reads the settings file into FmApp / Ft; unknown or invalid values are ignored. */
    static void load() {
        String p = filePath();
        if (!Files.exists(FsPath.of(p))) {
            return;
        }
        String text = File.readString(p);
        if (text == null) {
            return;
        }
        String[] lines = text.split("\n");
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].trim();
            i = i + 1;
            int eq = line.indexOf("=");
            if (eq <= 0) {
                continue;
            }
            String key = line.substring(0, eq).trim();
            String val = line.substring(eq + 1, line.length());
            if (key.equals("theme")) {
                int v = parseInt(val, 0);
                if (v >= 0 && v <= 2) {
                    FmApp.themeMode = v;
                }
            } else if (key.equals("accent")) {
                int v = parseInt(val, 0);
                if (v >= 0 && v < Ft.ACCENTS) {
                    Ft.accentIdx = v;
                }
            } else if (key.equals("showExt")) {
                FmApp.showExt = 1;
                if (parseInt(val, 1) == 0) {
                    FmApp.showExt = 0;
                }
            } else if (key.equals("showHidden")) {
                FmApp.showHiddenOpt = 0;
                if (parseInt(val, 0) != 0) {
                    FmApp.showHiddenOpt = 1;
                }
            } else if (key.equals("view")) {
                FmApp.startView = 0;
                if (parseInt(val, 0) == 1) {
                    FmApp.startView = 1;
                }
            }
        }
    }

    static void save() {
        String text = "theme=" + FmApp.themeMode + "\n"
                + "accent=" + Ft.accentIdx + "\n"
                + "showExt=" + FmApp.showExt + "\n"
                + "showHidden=" + FmApp.showHiddenOpt + "\n"
                + "view=" + FmApp.list.mode + "\n";
        Files.writeString(FsPath.of(filePath()), text);
    }
}
