/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.pkg;

import java.util.Locale;
import java.util.Objects;

/** Stable package identity: namespace/name (+ optional source).
 * @author 张晓达
 */
public final class PackageId implements Comparable<PackageId> {
    private final String namespace;
    private final String name;
    private final String source; // registry url / path / git — may be empty until locked

    public PackageId(String namespace, String name) {
        this(namespace, name, "");
    }

    public PackageId(String namespace, String name, String source) {
        this.namespace = namespace == null || namespace.isBlank() ? "jnative" : namespace.trim();
        this.name = Objects.requireNonNull(name, "name").trim();
        if (this.name.isEmpty()) {
            throw new IllegalArgumentException("package name required");
        }
        this.source = source == null ? "" : source.trim();
    }

    public static PackageId parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty package id");
        }
        String t = text.trim();
        int slash = t.indexOf('/');
        if (slash < 0) {
            return new PackageId("jnative", t);
        }
        return new PackageId(t.substring(0, slash), t.substring(slash + 1));
    }

    public String namespace() {
        return namespace;
    }

    public String name() {
        return name;
    }

    public String source() {
        return source;
    }

    public PackageId withSource(String src) {
        return new PackageId(namespace, name, src);
    }

    public String coordinate() {
        return namespace + "/" + name;
    }

    @Override
    public int compareTo(PackageId o) {
        int c = namespace.compareToIgnoreCase(o.namespace);
        if (c != 0) {
            return c;
        }
        return name.compareToIgnoreCase(o.name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PackageId that)) {
            return false;
        }
        return namespace.equalsIgnoreCase(that.namespace) && name.equalsIgnoreCase(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(namespace.toLowerCase(Locale.ROOT), name.toLowerCase(Locale.ROOT));
    }

    @Override
    public String toString() {
        return coordinate();
    }
}
