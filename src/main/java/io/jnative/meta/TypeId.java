/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.meta;

import java.util.Objects;

/** Stable type identity for processor queries (not Class.forName).
 * @author 张晓达
 */
public final class TypeId {
    private final String binaryName;

    public TypeId(String binaryName) {
        this.binaryName = binaryName == null ? "" : binaryName;
    }

    public static TypeId of(String name) {
        return new TypeId(name);
    }

    public String binaryName() {
        return binaryName;
    }

    public String simpleName() {
        int i = binaryName.lastIndexOf('.');
        return i < 0 ? binaryName : binaryName.substring(i + 1);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof TypeId t && binaryName.equals(t.binaryName);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(binaryName);
    }

    @Override
    public String toString() {
        return binaryName;
    }
}
