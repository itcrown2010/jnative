/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

/**
 * Java-like member/type visibility for JNative language packages.
 * Distinct from package-manager coordinates ({@code namespace/name}).
 * @author 张晓达
 */
public enum Visibility {
    PUBLIC,
    PROTECTED,
    PACKAGE,
    PRIVATE;

    public static Visibility fromModifiers(boolean isPublic, boolean isProtected, boolean isPrivate) {
        int n = (isPublic ? 1 : 0) + (isProtected ? 1 : 0) + (isPrivate ? 1 : 0);
        if (n > 1) {
            return null; // caller reports error
        }
        if (isPublic) {
            return PUBLIC;
        }
        if (isProtected) {
            return PROTECTED;
        }
        if (isPrivate) {
            return PRIVATE;
        }
        return PACKAGE;
    }

    /** Outermost enclosing type of {@code fqn} ({@code pkg.Outer.Inner} -> {@code pkg.Outer}). */
    private static String nestRoot(String pkg, String fqn) {
        String rest = fqn;
        String prefix = "";
        if (!pkg.isEmpty() && fqn.startsWith(pkg + ".")) {
            prefix = pkg + ".";
            rest = fqn.substring(prefix.length());
        }
        int dot = rest.indexOf('.');
        return prefix + (dot < 0 ? rest : rest.substring(0, dot));
    }

    /**
     * Whether {@code member} declared in {@code declaringTypePkg} / {@code declaringType}
     * is accessible from {@code fromPkg} / {@code fromType} (simple FQN names).
     */
    public boolean accessibleFrom(
            String declaringTypePkg,
            String declaringTypeFqn,
            String fromPkg,
            String fromTypeFqn,
            boolean fromSubtypeOfDeclaring
    ) {
        String dp = declaringTypePkg == null ? "" : declaringTypePkg;
        String fp = fromPkg == null ? "" : fromPkg;
        return switch (this) {
            case PUBLIC -> true;
            case PRIVATE -> declaringTypeFqn != null && fromTypeFqn != null
                    && (declaringTypeFqn.equals(fromTypeFqn)
                    || nestRoot(dp, declaringTypeFqn).equals(nestRoot(fp, fromTypeFqn)));
            case PACKAGE -> dp.equals(fp);
            case PROTECTED -> dp.equals(fp) || fromSubtypeOfDeclaring;
        };
    }
}
