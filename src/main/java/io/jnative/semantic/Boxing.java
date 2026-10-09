package io.jnative.semantic;

/**
 * Boxing / unboxing conversion helpers (Integer, Long, Boolean, Float, Double).
 * Conversion kind order for overload resolution: exact → widening → boxing.
 * @author 张晓达
 */
public final class Boxing {
    private Boxing() {
    }

    public enum Kind {
        NONE,
        BOX,
        UNBOX,
        /** primitive widen then box, e.g. int → Long */
        WIDEN_BOX,
        /** unbox then primitive widen, e.g. Integer → long */
        UNBOX_WIDEN
    }

    public static boolean isWrapperName(String name) {
        return "Integer".equals(name)
                || "Long".equals(name)
                || "Boolean".equals(name)
                || "Float".equals(name)
                || "Double".equals(name);
    }

    public static boolean isWrapper(JType type) {
        return type instanceof ClassType ct && isWrapperName(ct.name());
    }

    public static PrimitiveType primitiveOfWrapper(JType wrapper) {
        if (!(wrapper instanceof ClassType ct)) {
            return null;
        }
        return switch (ct.name()) {
            case "Integer" -> PrimitiveType.INT;
            case "Long" -> PrimitiveType.LONG;
            case "Boolean" -> PrimitiveType.BOOLEAN;
            case "Float" -> PrimitiveType.FLOAT;
            case "Double" -> PrimitiveType.DOUBLE;
            default -> null;
        };
    }

    public static String wrapperNameOf(PrimitiveType p) {
        return switch (p) {
            case INT -> "Integer";
            case LONG -> "Long";
            case BOOLEAN -> "Boolean";
            case FLOAT -> "Float";
            case DOUBLE -> "Double";
            default -> null;
        };
    }

    public static String unboxMethod(String wrapperName) {
        return switch (wrapperName) {
            case "Integer" -> "intValue";
            case "Long" -> "longValue";
            case "Boolean" -> "booleanValue";
            case "Float" -> "floatValue";
            case "Double" -> "doubleValue";
            default -> null;
        };
    }

    /**
     * Assignability including boxing/unboxing (not subtype alone).
     * Does not replace {@link JType#isAssignableFrom} for reference subtyping.
     */
    public static boolean convertible(JType expected, JType actual) {
        return conversionKind(expected, actual) != null;
    }

    /**
     * @return kind, or {@code null} if not convertible (including when already assignable
     *         without boxing — callers should check {@code isAssignableFrom} first).
     */
    public static Kind boxingKind(JType expected, JType actual) {
        if (expected == null || actual == null
                || expected instanceof ErrorType || actual instanceof ErrorType) {
            return null;
        }
        if (expected.isAssignableFrom(actual)) {
            return Kind.NONE;
        }
        // box: int → Integer (exact wrapper) or int → Long (widen+box)
        if (actual instanceof PrimitiveType ap && expected instanceof ClassType) {
            String want = ((ClassType) expected).name();
            String exact = wrapperNameOf(ap);
            if (exact != null && exact.equals(want)) {
                return Kind.BOX;
            }
            PrimitiveType targetPrim = primitiveOfWrapper(expected);
            if (targetPrim != null && targetPrim.isAssignableFrom(ap) && !ap.equals(targetPrim)) {
                // e.g. int → Long: widen to long then box
                return Kind.WIDEN_BOX;
            }
            return null;
        }
        // unbox: Integer → int / long
        if (isWrapper(actual) && expected instanceof PrimitiveType ep) {
            PrimitiveType boxed = primitiveOfWrapper(actual);
            if (boxed == null) {
                return null;
            }
            if (ep == boxed) {
                return Kind.UNBOX;
            }
            if (ep.isAssignableFrom(boxed)) {
                return Kind.UNBOX_WIDEN;
            }
            return null;
        }
        return null;
    }

    /** Compatible either by normal assignability or boxing conversion. */
    public static boolean assignableOrBoxing(JType expected, JType actual) {
        if (expected.isAssignableFrom(actual)) {
            return true;
        }
        Kind k = boxingKind(expected, actual);
        return k != null && k != Kind.NONE;
    }

    private static Kind conversionKind(JType expected, JType actual) {
        if (expected.isAssignableFrom(actual)) {
            return Kind.NONE;
        }
        return boxingKind(expected, actual);
    }
}
