/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.compiler;

import java.nio.file.Path;
import java.util.Objects;

/**
 * In-memory or on-disk source unit for IDE / compiler API (0.20).
 * Allows compiling unsaved editor buffers without forcing a disk path first.
 * @author 张晓达
 */
public final class SourceUnit {
    private final String path;
    private final String sourceText;

    public SourceUnit(String path, String sourceText) {
        this.path = path == null ? "<memory>" : path;
        this.sourceText = Objects.requireNonNullElse(sourceText, "");
    }

    public static SourceUnit ofPath(Path path, String sourceText) {
        return new SourceUnit(path == null ? "<memory>" : path.toString(), sourceText);
    }

    public String path() {
        return path;
    }

    public String sourceText() {
        return sourceText;
    }
}
