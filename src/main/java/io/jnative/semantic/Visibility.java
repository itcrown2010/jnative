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
            case PRIVATE -> declaringTypeFqn != null && declaringTypeFqn.equals(fromTypeFqn);
            case PACKAGE -> dp.equals(fp);
            case PROTECTED -> dp.equals(fp) || fromSubtypeOfDeclaring;
        };
    }
}
