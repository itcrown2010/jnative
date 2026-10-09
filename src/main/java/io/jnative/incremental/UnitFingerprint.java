package io.jnative.incremental;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fingerprints for a compilation unit.
 * Public API vs implementation vs ABI vs RPC/JSON shapes drive invalidation.
 * @author 张晓达
 */
public final class UnitFingerprint {
    private final String path;
    private final String contentHash;
    private final String publicApiHash;
    private final String implementationHash;
    private final String abiHash;
    private final String rpcHash;
    private final String jsonHash;

    public UnitFingerprint(
            String path,
            String contentHash,
            String publicApiHash,
            String implementationHash,
            String abiHash,
            String rpcHash,
            String jsonHash
    ) {
        this.path = path;
        this.contentHash = contentHash;
        this.publicApiHash = publicApiHash;
        this.implementationHash = implementationHash;
        this.abiHash = abiHash;
        this.rpcHash = rpcHash;
        this.jsonHash = jsonHash;
    }

    public String path() {
        return path;
    }

    public String contentHash() {
        return contentHash;
    }

    public String publicApiHash() {
        return publicApiHash;
    }

    public String implementationHash() {
        return implementationHash;
    }

    public String abiHash() {
        return abiHash;
    }

    public String rpcHash() {
        return rpcHash;
    }

    public String jsonHash() {
        return jsonHash;
    }

    /**
     * Heuristic fingerprint from source text without full semantic analysis.
     * Extracts rough public API surface vs body for classification.
     */
    public static UnitFingerprint fromSource(String relativePath, String source) {
        String content = ContentHasher.sha256(source == null ? "" : source);
        String apiSurface = extractApiSurface(source);
        String implSurface = extractImplSurface(source);
        String rpcSurface = extractRpcSurface(source);
        String jsonSurface = extractJsonShape(source);
        String abiSurface = apiSurface; // layout-sensitive APIs approximated by public surface
        return new UnitFingerprint(
                relativePath,
                content,
                ContentHasher.sha256(apiSurface),
                ContentHasher.sha256(implSurface),
                ContentHasher.sha256(abiSurface),
                ContentHasher.sha256(rpcSurface),
                ContentHasher.sha256(jsonSurface)
        );
    }

    public static ChangeClassification classify(UnitFingerprint before, UnitFingerprint after) {
        if (before == null) {
            return ChangeClassification.SOURCE_CHANGED;
        }
        if (before.contentHash().equals(after.contentHash())) {
            return ChangeClassification.UNCHANGED;
        }
        if (!before.rpcHash().equals(after.rpcHash())) {
            return ChangeClassification.RPC_CONTRACT_CHANGE;
        }
        if (!before.jsonHash().equals(after.jsonHash())) {
            return ChangeClassification.JSON_SHAPE_CHANGE;
        }
        if (!before.publicApiHash().equals(after.publicApiHash())) {
            return ChangeClassification.PUBLIC_API_CHANGE;
        }
        if (!before.abiHash().equals(after.abiHash())) {
            return ChangeClassification.ABI_CHANGE;
        }
        if (!before.implementationHash().equals(after.implementationHash())) {
            return ChangeClassification.IMPLEMENTATION_ONLY;
        }
        return ChangeClassification.SOURCE_CHANGED;
    }

    private static String extractApiSurface(String source) {
        if (source == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String line : source.split("\n")) {
            String t = line.trim();
            if (t.startsWith("public ") || t.startsWith("protected ")
                    || t.startsWith("interface ") || t.startsWith("class ")
                    || t.startsWith("@Rpc") || t.startsWith("@Authenticated")
                    || t.startsWith("@Role")
                    || isInterfaceMethod(t)) {
                // Keep signature; for one-line interface bodies keep method list
                if (t.startsWith("interface ") || t.startsWith("class ")) {
                    sb.append(t).append('\n');
                } else {
                    int brace = t.indexOf('{');
                    if (brace > 0 && !t.startsWith("@")) {
                        t = t.substring(0, brace).trim();
                    }
                    sb.append(t).append('\n');
                }
            }
        }
        // Explicit method signatures anywhere (interfaces / classes)
        Matcher methods = Pattern.compile(
                "(?:public|protected)?\\s*[\\w<>,\\[\\]\\s]+\\s+(\\w+)\\s*\\(([^)]*)\\)\\s*(?:;|\\{)"
        ).matcher(source);
        while (methods.find()) {
            String name = methods.group(1);
            if ("if".equals(name) || "while".equals(name) || "for".equals(name)
                    || "switch".equals(name) || "catch".equals(name)) {
                continue;
            }
            sb.append(name).append('(').append(methods.group(2).trim()).append(")\n");
        }
        return sb.toString();
    }

    /** Interface / abstract method signatures without visibility keyword. */
    private static boolean isInterfaceMethod(String t) {
        if (!t.endsWith(";") || !t.contains("(") || t.contains("=")) {
            return false;
        }
        if (t.startsWith("//") || t.startsWith("*") || t.startsWith("import ")) {
            return false;
        }
        return t.matches("[\\w<>,\\[\\]\\s]+\\s+\\w+\\s*\\([^;]*\\)\\s*;");
    }

    private static String extractImplSurface(String source) {
        return source == null ? "" : source;
    }

    private static String extractRpcSurface(String source) {
        if (source == null || !source.contains("@Rpc")) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        // Annotation lines
        for (String line : source.split("\n")) {
            String t = line.trim();
            if (t.startsWith("@Rpc") || t.startsWith("@Authenticated") || t.startsWith("@Role")) {
                sb.append(t).append('\n');
            }
            if (t.startsWith("interface ")) {
                sb.append(t).append('\n');
            }
        }
        Matcher methods = Pattern.compile(
                "([A-Za-z_][\\w<>,\\[\\]]*)\\s+([A-Za-z_]\\w*)\\s*\\(([^)]*)\\)\\s*;"
        ).matcher(source);
        while (methods.find()) {
            sb.append(methods.group(1)).append(' ')
                    .append(methods.group(2)).append('(')
                    .append(methods.group(3).trim()).append(");\n");
        }
        return sb.toString();
    }

    private static String extractJsonShape(String source) {
        if (source == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        // Multiline field declarations
        for (String line : source.split("\n")) {
            String t = line.trim();
            if (isFieldDecl(t)) {
                sb.append(t).append('\n');
            }
        }
        // Also scan inline class bodies: class User { String id; String name; }
        Matcher fields = Pattern.compile(
                "(?:public|protected|private)?\\s*(?:final\\s+)?"
                        + "(String|int|long|boolean|double|[A-Z]\\w*(?:<[^>]+>)?)"
                        + "\\s+(\\w+)\\s*;").matcher(source);
        while (fields.find()) {
            sb.append(fields.group(1)).append(' ').append(fields.group(2)).append(";\n");
        }
        return sb.toString();
    }

    private static boolean isFieldDecl(String t) {
        return (t.startsWith("String ") || t.startsWith("int ") || t.startsWith("long ")
                || t.startsWith("boolean ") || t.startsWith("double ")
                || t.contains(" ArrayList<") || t.contains(" List<"))
                && t.endsWith(";")
                && !t.contains("(");
    }
}
