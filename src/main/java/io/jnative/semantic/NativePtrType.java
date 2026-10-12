/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

/**
 * Typed native pointer {@code NativePtr<T>}. Machine pointer — not a GC-managed object.
 * GC must never treat a NativePtr value as ClassMetadata*.
 * @author 张晓达
 */
public final class NativePtrType implements JType {
    /** Untyped / void* — {@code NativePtr} or {@code NativePtr<Void>}. */
    public static final NativePtrType VOID = new NativePtrType(VoidType.INSTANCE);
    /** Backward-compatible alias for untyped NativePtr. */
    public static final NativePtrType INSTANCE = VOID;

    private final JType pointee;

    private NativePtrType(JType pointee) {
        this.pointee = pointee;
    }

    public static NativePtrType of(JType pointee) {
        if (pointee instanceof VoidType) {
            return VOID;
        }
        return new NativePtrType(pointee);
    }

    public JType pointee() {
        return pointee;
    }

    public boolean isVoidPointer() {
        return pointee instanceof VoidType;
    }

    @Override
    public String displayName() {
        if (pointee instanceof VoidType) {
            return "NativePtr<Void>";
        }
        return "NativePtr<" + pointee.displayName() + ">";
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isReference() {
        // Not a managed Java reference.
        return false;
    }

    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public boolean isInteger() {
        return false;
    }

    @Override
    public boolean isAssignableFrom(JType other) {
        if (other instanceof ErrorType) {
            return true;
        }
        if (!(other instanceof NativePtrType np)) {
            // Java null is not a native null.
            return false;
        }
        // Same pointee
        if (pointeeCompatible(pointee, np.pointee)) {
            return true;
        }
        // NativePtr<T> → NativePtr<Void> (controlled widening)
        if (pointee instanceof VoidType) {
            return true;
        }
        // NativePtr.NULL / void* → NativePtr<T>
        if (np.isVoidPointer()) {
            return true;
        }
        return false;
    }

    private static boolean pointeeCompatible(JType a, JType b) {
        if (a instanceof VoidType && b instanceof VoidType) {
            return true;
        }
        if (a instanceof NativeStructType sa && b instanceof NativeStructType sb) {
            return sa.symbol() == sb.symbol();
        }
        return a.equals(b);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof NativePtrType np)) {
            return false;
        }
        return pointeeCompatible(pointee, np.pointee);
    }

    @Override
    public int hashCode() {
        return pointee.displayName().hashCode();
    }

    @Override
    public String toString() {
        return displayName();
    }
}
