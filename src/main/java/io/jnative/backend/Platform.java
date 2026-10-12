/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend;

/**
 * Host platform abstraction. Avoid hardcoding OS-specific paths in the compiler core.
 * @author 张晓达
 */
public enum Platform {
    WINDOWS_X86_64,
    LINUX_X86_64,
    MACOS_ARM64,
    MACOS_X86_64,
    UNKNOWN;

    public static Platform detect() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean arm = arch.contains("aarch64") || arch.contains("arm64");
        boolean x86 = arch.contains("amd64") || arch.contains("x86_64");

        if (os.contains("win")) {
            return WINDOWS_X86_64;
        }
        if (os.contains("mac") || os.contains("darwin")) {
            if (arm) {
                return MACOS_ARM64;
            }
            if (x86) {
                return MACOS_X86_64;
            }
        }
        if (os.contains("nux") || os.contains("linux")) {
            return LINUX_X86_64;
        }
        return UNKNOWN;
    }

    public String executableName(String base) {
        if (this == WINDOWS_X86_64) {
            return base.endsWith(".exe") ? base : base + ".exe";
        }
        return base;
    }

    public String pathSeparator() {
        return this == WINDOWS_X86_64 ? ";" : ":";
    }
}
