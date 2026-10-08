package io.jnative.mobile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/** Records correspondence between source, web, and platform artifacts for a build. */
public final class MobileBuildRecord {

    private final Map<String, String> fields = new LinkedHashMap<>();

    public MobileBuildRecord put(String key, String value) {
        fields.put(key, value);
        return this;
    }

    public void write(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        int i = 0;
        for (Map.Entry<String, String> e : fields.entrySet()) {
            if (i++ > 0) {
                sb.append(",\n");
            }
            sb.append("  \"").append(escape(e.getKey())).append("\": \"")
                    .append(escape(e.getValue())).append('"');
        }
        sb.append("\n}\n");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
    }

    public static String sha256(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(Files.readAllBytes(file));
            return HexFormat.of().formatHex(md.digest());
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
