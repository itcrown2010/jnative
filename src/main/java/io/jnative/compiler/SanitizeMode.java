package io.jnative.compiler;

/**
 * Clang sanitizer modes. Only enabled when the host toolchain supports them.
 * ASan and UBSan may be combined; TSan must not be combined with ASan.
 */
public enum SanitizeMode {
    NONE,
    ASAN,
    UBSAN,
    ASAN_UBSAN,
    TSAN
}