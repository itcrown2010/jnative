package io.jnative.semantic;

import java.util.ArrayList;
import java.util.List;

/**
 * Bitset effect summary for callables (JNative 0.11).
 * Effects are inferred in the semantic/effect pass — LLVM must not invent them.
 * @author 张晓达
 */
public final class FunctionEffects {
    public static final int ALLOCATES = 1;
    public static final int SAFEPOINT = 1 << 1;
    public static final int THROWS = 1 << 2;
    public static final int BLOCKS = 1 << 3;
    public static final int NATIVE_CALL = 1 << 4;
    public static final int WRITES_GLOBAL = 1 << 5;
    public static final int UNKNOWN = 1 << 6;
    /** Cooperative async suspension (distinct from BLOCKS — JNative 0.23). */
    public static final int SUSPENDS = 1 << 7;

    public static final int ALL_DANGEROUS =
            ALLOCATES | SAFEPOINT | THROWS | BLOCKS | NATIVE_CALL | WRITES_GLOBAL | UNKNOWN | SUSPENDS;

    private int bits;

    public FunctionEffects() {
        this(0);
    }

    public FunctionEffects(int bits) {
        this.bits = bits;
    }

    public int bits() {
        return bits;
    }

    public boolean has(int flag) {
        return (bits & flag) != 0;
    }

    public FunctionEffects with(int flag) {
        bits |= flag;
        return this;
    }

    public FunctionEffects union(FunctionEffects other) {
        if (other != null) {
            bits |= other.bits;
        }
        return this;
    }

    public FunctionEffects copy() {
        return new FunctionEffects(bits);
    }

    public boolean mayAllocate() {
        return has(ALLOCATES) || has(UNKNOWN);
    }

    public boolean maySafepoint() {
        return has(SAFEPOINT) || has(ALLOCATES) || has(UNKNOWN);
    }

    public boolean mayThrow() {
        return has(THROWS) || has(UNKNOWN);
    }

    public boolean mayBlock() {
        return has(BLOCKS) || has(UNKNOWN);
    }

    public boolean maySuspend() {
        return has(SUSPENDS) || has(UNKNOWN);
    }

    public boolean nativeCall() {
        return has(NATIVE_CALL) || has(UNKNOWN);
    }

    public boolean isUnknown() {
        return has(UNKNOWN);
    }

    public boolean isPure() {
        return bits == 0;
    }

    public boolean isDeterministic() {
        return !mayAllocate() && !maySafepoint() && !mayThrow() && !mayBlock()
                && !maySuspend() && !isUnknown() && !has(WRITES_GLOBAL);
    }

    /** Effects forbidden by @NoGC. */
    public boolean violatesNoGc() {
        return mayAllocate() || maySafepoint() || isUnknown();
    }

    public boolean violatesNoThrow() {
        return mayThrow() || isUnknown();
    }

    public boolean violatesNoBlock() {
        return mayBlock() || isUnknown();
    }

    public List<String> flagNames() {
        List<String> names = new ArrayList<>();
        if (has(ALLOCATES)) {
            names.add("ALLOCATES");
        }
        if (has(SAFEPOINT)) {
            names.add("SAFEPOINT");
        }
        if (has(THROWS)) {
            names.add("THROWS");
        }
        if (has(BLOCKS)) {
            names.add("BLOCKS");
        }
        if (has(SUSPENDS)) {
            names.add("SUSPENDS");
        }
        if (has(NATIVE_CALL)) {
            names.add("NATIVE_CALL");
        }
        if (has(WRITES_GLOBAL)) {
            names.add("WRITES_GLOBAL");
        }
        if (has(UNKNOWN)) {
            names.add("UNKNOWN");
        }
        if (names.isEmpty()) {
            names.add("PURE");
        }
        return names;
    }

    @Override
    public String toString() {
        return String.join("|", flagNames());
    }

    public static FunctionEffects none() {
        return new FunctionEffects(0);
    }

    public static FunctionEffects pure() {
        return none();
    }

    public static FunctionEffects unknown() {
        return new FunctionEffects(UNKNOWN | ALLOCATES | SAFEPOINT | THROWS | BLOCKS | NATIVE_CALL);
    }

    public static FunctionEffects allocates() {
        return new FunctionEffects(ALLOCATES | SAFEPOINT);
    }

    public static FunctionEffects throwsEffect() {
        return new FunctionEffects(THROWS);
    }

    public static FunctionEffects blocks() {
        return new FunctionEffects(BLOCKS);
    }

    public static FunctionEffects suspends() {
        return new FunctionEffects(SUSPENDS | SAFEPOINT);
    }

    public static FunctionEffects nativeCall(boolean mayBlock, boolean mayCallback) {
        FunctionEffects e = new FunctionEffects(NATIVE_CALL);
        if (mayBlock) {
            e.with(BLOCKS);
        }
        if (mayCallback) {
            e.with(UNKNOWN); // callback into managed code is opaque
        }
        return e;
    }

    // Backward-compat setters used by older 0.10 call sites
    public void setMayAllocate(boolean v) {
        if (v) {
            with(ALLOCATES | SAFEPOINT);
        } else {
            bits &= ~(ALLOCATES);
        }
    }

    public void setMaySafepoint(boolean v) {
        if (v) {
            with(SAFEPOINT);
        } else {
            bits &= ~SAFEPOINT;
        }
    }

    public void setMayThrow(boolean v) {
        if (v) {
            with(THROWS);
        } else {
            bits &= ~THROWS;
        }
    }

    public void setMayBlock(boolean v) {
        if (v) {
            with(BLOCKS);
        } else {
            bits &= ~BLOCKS;
        }
    }

    public void setNativeCall(boolean v) {
        if (v) {
            with(NATIVE_CALL);
        } else {
            bits &= ~NATIVE_CALL;
        }
    }
}
