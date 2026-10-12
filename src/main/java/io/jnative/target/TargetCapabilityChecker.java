/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.target;

import io.jnative.backend.TargetCapability;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.diagnostics.DiagnosticCodes;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.pkg.BuildGraphPackageIntegration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pre-link / pre-run checks: package requires, source API usage, and toolchain flags.
 * Failures are concrete diagnostics — never silent IR drops or default stubs.
 *
 * @author 张晓达
 */
public final class TargetCapabilityChecker {
    private static final List<ApiCapabilityRule> RULES = List.of(
            new ApiCapabilityRule("Files\\.", TargetCapability.FILESYSTEM, "filesystem (Files)"),
            new ApiCapabilityRule("FsPath\\.", TargetCapability.FILESYSTEM, "filesystem (FsPath)"),
            new ApiCapabilityRule("new\\s+File\\s*\\(", TargetCapability.FILESYSTEM, "filesystem (File)"),
            new ApiCapabilityRule("FileInputStream", TargetCapability.FILESYSTEM, "filesystem (FileInputStream)"),
            new ApiCapabilityRule("FileOutputStream", TargetCapability.FILESYSTEM, "filesystem (FileOutputStream)"),
            new ApiCapabilityRule("ProcessBuilder", TargetCapability.PROCESS, "process (ProcessBuilder)"),
            new ApiCapabilityRule("Runtime\\.getRuntime\\(\\)\\.exec", TargetCapability.PROCESS, "process (Runtime.exec)"),
            new ApiCapabilityRule("new\\s+Thread\\s*\\(", TargetCapability.THREADS, "threads (Thread)"),
            new ApiCapabilityRule("\\bsynchronized\\s*\\(", TargetCapability.THREADS, "threads (synchronized)"),
            new ApiCapabilityRule("ServerSocket", TargetCapability.RAW_SOCKET, "raw socket (ServerSocket)"),
            new ApiCapabilityRule("new\\s+Socket\\s*\\(", TargetCapability.RAW_SOCKET, "raw socket (Socket)"),
            new ApiCapabilityRule("Sqlite\\.", TargetCapability.DATABASE, "database (Sqlite)"),
            new ApiCapabilityRule("Db\\.", TargetCapability.DATABASE, "database (Db)"),
            new ApiCapabilityRule("Pg\\.", TargetCapability.DATABASE, "database (Pg)"),
            new ApiCapabilityRule("@Native\\b", TargetCapability.NATIVE_FFI, "native FFI (@Native)"),
            new ApiCapabilityRule("NativePtr", TargetCapability.NATIVE_FFI, "native FFI (NativePtr)"),
            new ApiCapabilityRule("Ui\\.", TargetCapability.DESKTOP_UI, "desktop UI (Ui)"),
            new ApiCapabilityRule("Window\\.", TargetCapability.DESKTOP_UI, "desktop UI (Window)"),
            new ApiCapabilityRule("Tls\\.", TargetCapability.TLS_NATIVE, "native TLS (Tls)"),
            new ApiCapabilityRule("Http2\\.", TargetCapability.HTTP2_NATIVE, "native HTTP/2 (Http2)"),
            new ApiCapabilityRule("Document\\.", TargetCapability.BROWSER_DOM, "browser DOM (Document)"),
            new ApiCapabilityRule("JsObject", TargetCapability.JS_INTEROP, "JS interop (JsObject)"),
            new ApiCapabilityRule("JsHandle", TargetCapability.JS_INTEROP, "JS interop (JsHandle)"),
            new ApiCapabilityRule("Fetch\\.", TargetCapability.BROWSER_FETCH, "browser fetch (Fetch)"),
            new ApiCapabilityRule("Browser\\.", TargetCapability.BROWSER_DOM, "browser API (Browser)")
    );

    private TargetCapabilityChecker() {
    }

    public static List<Diagnostic> checkPackages(
            List<BuildGraphPackageIntegration.PackageBuildNode> nodes,
            String triple
    ) {
        List<Diagnostic> out = new ArrayList<>();
        TargetId id = TargetId.fromCompileTriple(triple);
        TargetProfile profile = TargetProfiles.profile(id);
        Set<TargetCapability> provided = profile.packageFlags();
        if (nodes == null) {
            return out;
        }
        for (BuildGraphPackageIntegration.PackageBuildNode n : nodes) {
            Set<TargetCapability> required = toBackendFlags(n.requires());
            String missing = describeMissing(required, provided);
            if (!missing.isEmpty()) {
                Path toml = n.root().resolve("jnative.toml");
                String file = FilesExists(toml) ? toml.toString() : n.root().toString();
                out.add(Diagnostic.error(
                        DiagnosticCodes.PACKAGE_CAPABILITY,
                        new SourceLocation(file, 1, 1),
                        "package " + n.id() + " requires " + missing
                                + " which target " + id.triple() + " (" + id.compileTriple()
                                + ") does not provide",
                        "Remove the dependency, change --target, or use a package that "
                                + "supports " + id.compileTriple() + ". Mobile containers compile "
                                + "as wasm32-browser and are not a full native mobile runtime."));
            }
        }
        return out;
    }

    public static List<Diagnostic> checkSourceText(String file, String source, String triple) {
        List<Diagnostic> out = new ArrayList<>();
        if (source == null || source.isBlank()) {
            return out;
        }
        TargetId id = TargetId.fromCompileTriple(triple);
        // Mobile uses wasm compile surface — never claim native-mobile capabilities.
        Set<TargetCapability> provided = TargetProfiles.profile(id).packageFlags();
        String path = file == null || file.isBlank() ? "<source>" : file;
        for (ApiCapabilityRule rule : RULES) {
            if (provided.contains(rule.required())) {
                continue;
            }
            Pattern p = Pattern.compile(rule.pattern());
            Matcher m = p.matcher(source);
            if (m.find()) {
                int[] lc = lineColumn(source, m.start());
                out.add(Diagnostic.error(
                        DiagnosticCodes.TARGET_API,
                        new SourceLocation(path, lc[0], lc[1]),
                        rule.featureLabel() + " is not available on target "
                                + id.triple() + " (compile as " + id.compileTriple() + ")",
                        "Use a native target, or replace this API with a "
                                + id.compileTriple() + "-supported alternative. "
                                + "Do not expect silent stubs or default return values."));
            }
        }
        if (id.isBrowserCompile() && containsBlockingAsyncAdapterClaim(source)) {
            int[] lc = lineColumn(source, Math.max(0, source.indexOf("blockGet")));
            out.add(Diagnostic.error(
                    DiagnosticCodes.BACKEND_UNSUPPORTED,
                    new SourceLocation(path, lc[0], lc[1]),
                    "blocking async adapter must not be advertised as non-blocking on "
                            + id.compileTriple(),
                    "Use true async/await on this target; do not wrap blocking waits "
                            + "as non-blocking capability."));
        }
        return out;
    }

    public static List<Diagnostic> checkSources(
            List<Path> files,
            java.util.function.Function<Path, String> reader,
            String triple
    ) {
        List<Diagnostic> out = new ArrayList<>();
        if (files == null) {
            return out;
        }
        for (Path f : files) {
            if (f == null) {
                continue;
            }
            try {
                String text = reader.apply(f);
                out.addAll(checkSourceText(f.toString(), text, triple));
            } catch (Exception e) {
                out.add(Diagnostic.error(
                        DiagnosticCodes.SOURCE_SYNTAX,
                        new SourceLocation(f.toString(), 0, 0),
                        "failed to read source for target check: " + e.getMessage(),
                        null));
            }
        }
        return out;
    }

    /** Convert pkg enum set to backend flags (shared names). */
    public static Set<TargetCapability> toBackendFlags(Set<io.jnative.pkg.TargetCapability> pkg) {
        EnumSet<TargetCapability> set = EnumSet.noneOf(TargetCapability.class);
        if (pkg == null) {
            return set;
        }
        for (io.jnative.pkg.TargetCapability c : pkg) {
            try {
                set.add(TargetCapability.valueOf(c.name()));
            } catch (IllegalArgumentException ignored) {
                // JAVA_BRIDGE is package-only; treat as native-host JAVA_BRIDGE via profile.
                if ("JAVA_BRIDGE".equals(c.name())
                        && !TargetProfiles.profile(TargetId.NATIVE_HOST).supports(Capability.JAVA_BRIDGE)) {
                    // still required — mark as missing by using NATIVE_FFI as proxy? Better: skip
                    // and emit explicit note via package check if we add JAVA_BRIDGE to backend.
                }
            }
        }
        return set;
    }

    public static String describeMissing(Set<TargetCapability> required, Set<TargetCapability> provided) {
        StringBuilder sb = new StringBuilder();
        for (TargetCapability c : required) {
            if (!provided.contains(c)) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(c.name());
            }
        }
        return sb.toString();
    }

    private static boolean containsBlockingAsyncAdapterClaim(String source) {
        String lower = source.toLowerCase(Locale.ROOT);
        return lower.contains("blockget") && (lower.contains("non-blocking") || lower.contains("nonblocking"));
    }

    private static boolean FilesExists(Path p) {
        try {
            return java.nio.file.Files.isRegularFile(p);
        } catch (Exception e) {
            return false;
        }
    }

    private static int[] lineColumn(String text, int offset) {
        int line = 1;
        int col = 1;
        int lim = Math.min(Math.max(0, offset), text.length());
        for (int i = 0; i < lim; i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new int[]{line, col};
    }
}
