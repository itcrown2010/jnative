package io.jnative.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Machine-readable diagnostic serialization for IDE / problem matchers.
 * Fields reflect only what {@link Diagnostic} actually carries.
 * @author 张晓达
 */
public final class DiagnosticJson {
    private DiagnosticJson() {
    }

    /**
     * Remap line numbers from a stdlib-prepended buffer back onto user source.
     * Diagnostics that fall inside the prepended prefix keep their file as
     * {@code <stdlib>} so IDEs do not mis-attribute them to user code.
     */
    public static List<Diagnostic> remapToUserSource(
            List<Diagnostic> diagnostics,
            String userPath,
            int userLineOffset
    ) {
        if (diagnostics == null || diagnostics.isEmpty()) {
            return diagnostics == null ? List.of() : diagnostics;
        }
        String path = userPath == null || userPath.isBlank() ? null : userPath;
        List<Diagnostic> out = new ArrayList<>(diagnostics.size());
        for (Diagnostic d : diagnostics) {
            SourceLocation loc = d.location();
            if (loc == null || loc == SourceLocation.UNKNOWN) {
                out.add(d);
                continue;
            }
            int line = loc.line();
            if (userLineOffset > 0 && line <= userLineOffset) {
                out.add(new Diagnostic(
                        d.severity(),
                        new SourceLocation("<stdlib>", line, loc.column()),
                        "[stdlib] " + d.message()));
            } else {
                int userLine = userLineOffset > 0 ? line - userLineOffset : line;
                out.add(new Diagnostic(
                        d.severity(),
                        new SourceLocation(
                                path != null ? path : loc.file(),
                                userLine,
                                loc.column()),
                        d.message()));
            }
        }
        return out;
    }

    /** Lines before the user source suffix in a prepended compilation buffer. */
    public static int userLineOffset(String prepended, String userSource) {
        if (prepended == null || userSource == null) {
            return 0;
        }
        if (prepended.equals(userSource)) {
            return 0;
        }
        // Prefer exact suffix match (common for small programs).
        if (prepended.endsWith(userSource)) {
            int cut = prepended.length() - userSource.length();
            if (cut <= 0) {
                return 0;
            }
            return countLines(prepended.substring(0, cut));
        }
        int idx = prepended.lastIndexOf(userSource);
        if (idx > 0) {
            return countLines(prepended.substring(0, idx));
        }
        // Database/Time stdlibs may be inserted before the primary public class, so the
        // user unit is no longer a contiguous suffix. Locate the leading probe of the
        // user buffer instead (enough for files that appear before the insert point).
        int probeLen = Math.min(userSource.length(), 4096);
        while (probeLen >= 64) {
            String probe = userSource.substring(0, probeLen);
            int at = prepended.indexOf(probe);
            if (at > 0) {
                return countLines(prepended.substring(0, at));
            }
            if (at == 0) {
                return 0;
            }
            probeLen /= 2;
        }
        return 0;
    }

    public static int countLines(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int lines = 1;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                lines++;
            }
        }
        // Trailing newline does not start another line of content for offset purposes
        // when the suffix is joined after "\n\n"; countLines of the prefix substring
        // (which typically ends with \n\n) is what we want as 1-based start offset.
        if (text.endsWith("\n")) {
            lines--;
        }
        return Math.max(0, lines);
    }

    public static String toJsonArray(List<Diagnostic> diagnostics) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (Diagnostic d : diagnostics) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append(toJsonObject(d));
        }
        sb.append(']');
        return sb.toString();
    }

    public static String toJsonObject(Diagnostic d) {
        SourceLocation loc = d.location() == null ? SourceLocation.UNKNOWN : d.location();
        String severity = switch (d.severity()) {
            case ERROR -> "error";
            case WARNING -> "warning";
            case NOTE -> "hint";
        };
        return "{"
                + "\"file\":" + quote(loc.file())
                + ",\"line\":" + loc.line()
                + ",\"column\":" + loc.column()
                + ",\"severity\":" + quote(severity)
                + ",\"message\":" + quote(d.message())
                + "}";
    }

    public static String buildReportJson(
            boolean success,
            String artifact,
            List<Diagnostic> diagnostics
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"success\":").append(success);
        sb.append(",\"artifact\":").append(artifact == null ? "null" : quote(artifact));
        sb.append(",\"diagnostics\":").append(toJsonArray(diagnostics == null ? List.of() : diagnostics));
        sb.append('}');
        return sb.toString();
    }

    public static String quote(String s) {
        if (s == null) {
            return "\"\"";
        }
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format(Locale.ROOT, "\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
