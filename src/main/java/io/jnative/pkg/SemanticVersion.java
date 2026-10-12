/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.pkg;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semantic version: major.minor.patch[-pre][+build]
 * @author 张晓达
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {
    private static final Pattern RE = Pattern.compile(
            "^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?(?:\\+([0-9A-Za-z.-]+))?$");

    private final int major;
    private final int minor;
    private final int patch;
    private final String pre;
    private final String build;

    public SemanticVersion(int major, int minor, int patch) {
        this(major, minor, patch, "", "");
    }

    public SemanticVersion(int major, int minor, int patch, String pre, String build) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("negative version component");
        }
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.pre = pre == null ? "" : pre;
        this.build = build == null ? "" : build;
    }

    public static SemanticVersion parse(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("empty version");
        }
        String t = text.trim();
        if (t.startsWith("v") || t.startsWith("V")) {
            t = t.substring(1);
        }
        // allow 1.2 → 1.2.0
        if (t.matches("\\d+\\.\\d+")) {
            t = t + ".0";
        } else if (t.matches("\\d+")) {
            t = t + ".0.0";
        }
        Matcher m = RE.matcher(t);
        if (!m.matches()) {
            throw new IllegalArgumentException("invalid semver: " + text);
        }
        return new SemanticVersion(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)),
                m.group(4) == null ? "" : m.group(4),
                m.group(5) == null ? "" : m.group(5));
    }

    public int major() {
        return major;
    }

    public int minor() {
        return minor;
    }

    public int patch() {
        return patch;
    }

    public String preRelease() {
        return pre;
    }

    public String buildMetadata() {
        return build;
    }

    public boolean isPreRelease() {
        return !pre.isEmpty();
    }

    @Override
    public int compareTo(SemanticVersion o) {
        int c = Integer.compare(major, o.major);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(minor, o.minor);
        if (c != 0) {
            return c;
        }
        c = Integer.compare(patch, o.patch);
        if (c != 0) {
            return c;
        }
        // no pre > has pre
        if (pre.isEmpty() && !o.pre.isEmpty()) {
            return 1;
        }
        if (!pre.isEmpty() && o.pre.isEmpty()) {
            return -1;
        }
        return comparePre(pre, o.pre);
    }

    private static int comparePre(String a, String b) {
        if (Objects.equals(a, b)) {
            return 0;
        }
        String[] aa = a.split("\\.");
        String[] bb = b.split("\\.");
        int n = Math.max(aa.length, bb.length);
        for (int i = 0; i < n; i++) {
            if (i >= aa.length) {
                return -1;
            }
            if (i >= bb.length) {
                return 1;
            }
            boolean an = aa[i].chars().allMatch(Character::isDigit);
            boolean bn = bb[i].chars().allMatch(Character::isDigit);
            if (an && bn) {
                int c = Integer.compare(Integer.parseInt(aa[i]), Integer.parseInt(bb[i]));
                if (c != 0) {
                    return c;
                }
            } else if (an) {
                return -1;
            } else if (bn) {
                return 1;
            } else {
                int c = aa[i].compareTo(bb[i]);
                if (c != 0) {
                    return c;
                }
            }
        }
        return 0;
    }

    public SemanticVersion nextMajor() {
        return new SemanticVersion(major + 1, 0, 0);
    }

    public SemanticVersion nextMinor() {
        return new SemanticVersion(major, minor + 1, 0);
    }

    public SemanticVersion nextPatch() {
        return new SemanticVersion(major, minor, patch + 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SemanticVersion that)) {
            return false;
        }
        return major == that.major && minor == that.minor && patch == that.patch
                && pre.equals(that.pre);
        // build metadata ignored for equality per semver
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, pre);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(major).append('.').append(minor).append('.').append(patch);
        if (!pre.isEmpty()) {
            sb.append('-').append(pre);
        }
        if (!build.isEmpty()) {
            sb.append('+').append(build);
        }
        return sb.toString();
    }

    public static List<SemanticVersion> sortDescending(List<SemanticVersion> versions) {
        List<SemanticVersion> copy = new ArrayList<>(versions);
        copy.sort((a, b) -> b.compareTo(a));
        return copy;
    }

    public String coreString() {
        return major + "." + minor + "." + patch;
    }

    public boolean sameMajor(SemanticVersion o) {
        return major == o.major;
    }
}
