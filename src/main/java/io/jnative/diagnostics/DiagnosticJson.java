package io.jnative.diagnostics;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Machine-readable diagnostic serialization for IDE / problem matchers.
 * Fields reflect only what {@link Diagnostic} actually carries.
 *
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
                        "[stdlib] " + d.message(),
                        d.code(),
                        d.hint(),
                        d.related()));
            } else {
                int userLine = userLineOffset > 0 ? line - userLineOffset : line;
                out.add(new Diagnostic(
                        d.severity(),
                        new SourceLocation(
                                path != null ? path : loc.file(),
                                userLine,
                                loc.column()),
                        d.message(),
                        d.code(),
                        d.hint(),
                        d.related()));
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
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"file\":").append(quote(loc.file()));
        sb.append(",\"line\":").append(loc.line());
        sb.append(",\"column\":").append(loc.column());
        sb.append(",\"severity\":").append(quote(severity));
        sb.append(",\"message\":").append(quote(d.message()));
        if (d.code() != null && !d.code().isBlank()) {
            sb.append(",\"code\":").append(quote(d.code()));
        }
        if (d.hint() != null && !d.hint().isBlank()) {
            sb.append(",\"hint\":").append(quote(d.hint()));
        }
        if (d.related() != null && !d.related().isEmpty()) {
            sb.append(",\"related\":[");
            boolean first = true;
            for (Diagnostic.RelatedLocation r : d.related()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                SourceLocation rl = r.location() == null ? SourceLocation.UNKNOWN : r.location();
                sb.append("{\"file\":").append(quote(rl.file()))
                        .append(",\"line\":").append(rl.line())
                        .append(",\"column\":").append(rl.column())
                        .append(",\"message\":").append(quote(r.message()))
                        .append('}');
            }
            sb.append(']');
        }
        sb.append('}');
        return sb.toString();
    }

    public static String buildReportJson(
            boolean success,
            String artifact,
            List<Diagnostic> diagnostics
    ) {
        return buildReportJson(success, artifact, diagnostics, Map.of());
    }

    /**
     * @param meta optional build metadata (target, compilerVersion, inputFingerprint, …)
     */
    public static String buildReportJson(
            boolean success,
            String artifact,
            List<Diagnostic> diagnostics,
            Map<String, String> meta
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"success\":").append(success);
        sb.append(",\"artifact\":").append(artifact == null ? "null" : quote(artifact));
        if (meta != null) {
            for (Map.Entry<String, String> e : meta.entrySet()) {
                if (e.getKey() == null || e.getKey().isBlank()) {
                    continue;
                }
                sb.append(',').append(quote(e.getKey())).append(':');
                if (e.getValue() == null) {
                    sb.append("null");
                } else {
                    sb.append(quote(e.getValue()));
                }
            }
        }
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
