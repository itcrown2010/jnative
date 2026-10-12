/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.lsp;

import java.util.ArrayDeque;
import java.util.Locale;

/**
 * Bounded stderr logging for the language server (never dumps secrets / full env).
 * @author 张晓达
 */
public final class LspLog {
    public enum Level { ERROR, WARN, INFO, DEBUG }

    private static volatile Level minLevel = Level.INFO;
    private static final int CAP = 500;
    private static final ArrayDeque<String> ring = new ArrayDeque<>();

    private LspLog() {
    }

    public static void setMinLevel(Level level) {
        if (level != null) {
            minLevel = level;
        }
    }

    public static void setMinLevel(String name) {
        if (name == null || name.isBlank()) {
            return;
        }
        try {
            setMinLevel(Level.valueOf(name.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            // keep previous
        }
    }

    public static void error(String msg) {
        log(Level.ERROR, msg);
    }

    public static void warn(String msg) {
        log(Level.WARN, msg);
    }

    public static void info(String msg) {
        log(Level.INFO, msg);
    }

    public static void debug(String msg) {
        log(Level.DEBUG, msg);
    }

    private static void log(Level level, String msg) {
        if (level.ordinal() > minLevel.ordinal()) {
            return;
        }
        String line = "jnative lsp: [" + level.name().toLowerCase(Locale.ROOT) + "] " + redact(msg);
        synchronized (ring) {
            if (ring.size() >= CAP) {
                ring.removeFirst();
            }
            ring.addLast(line);
        }
        System.err.println(line);
    }

    static String redact(String line) {
        if (line == null) {
            return "";
        }
        return line
                .replaceAll("(?i)(Authorization:\\s*)\\S+", "$1***")
                .replaceAll("(?i)(api[_-]?key\\s*[=:]\\s*)\\S+", "$1***")
                .replaceAll("(?i)(password\\s*[=:]\\s*)\\S+", "$1***")
                .replaceAll("(?i)(token\\s*[=:]\\s*)\\S+", "$1***");
    }
}
