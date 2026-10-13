/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.source;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Recognized JNative/Java source suffixes. Suffix does not change type semantics.
 * @author 张晓达
 */
public final class SourceFiles {
    public static final String EXT_JAVA = ".java";
    public static final String EXT_JN = ".jn";

    private SourceFiles() {
    }

    public static boolean isSourcePath(Path path) {
        if (path == null || path.getFileName() == null) {
            return false;
        }
        /*
         * Skip local tooling trees, but allow package extractions under
         * {@code .jnative/pkg-cache/extracted/} (third-party .jnpkg sources).
         */
        String norm = path.toString().replace('\\', '/');
        if (norm.contains("/.jnative-file-build/")
                || norm.contains("/node_modules/")
                || norm.contains("/.jnative/build/")
                || norm.contains("/.jnative/generated/")
                || norm.contains("/.jnative/cache/")) {
            return false;
        }
        for (Path part : path) {
            String seg = part.toString();
            if (seg.equals(".jnative-file-build") || seg.equals("node_modules")) {
                return false;
            }
        }
        return isSourceFileName(path.getFileName().toString());
    }

    public static boolean isSourceFileName(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        return lower.endsWith(EXT_JAVA) || lower.endsWith(EXT_JN);
    }

    /** Prefer {@code .jn} then {@code .java} for each basename. */
    public static List<String> entryFileNames(String mainClass) {
        List<String> out = new ArrayList<>();
        if (mainClass != null && !mainClass.isBlank()) {
            out.add(mainClass + EXT_JN);
            out.add(mainClass + EXT_JAVA);
        }
        out.add("App" + EXT_JN);
        out.add("App" + EXT_JAVA);
        out.add("Main" + EXT_JN);
        out.add("Main" + EXT_JAVA);
        out.add("Server" + EXT_JN);
        out.add("Server" + EXT_JAVA);
        return out;
    }

    public static String stripExtension(String fileName) {
        if (fileName == null) {
            return "";
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(EXT_JAVA)) {
            return fileName.substring(0, fileName.length() - EXT_JAVA.length());
        }
        if (lower.endsWith(EXT_JN)) {
            return fileName.substring(0, fileName.length() - EXT_JN.length());
        }
        return fileName;
    }
}
