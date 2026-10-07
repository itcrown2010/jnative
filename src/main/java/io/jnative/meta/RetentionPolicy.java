package io.jnative.meta;

/**
 * Annotation retention. Default for framework annotations is {@link #COMPILE_TIME}.
 * {@link #RUNTIME_METADATA} is lightweight selected schema only — not JVM reflection.
 */
public enum RetentionPolicy {
    SOURCE,
    COMPILE_TIME,
    RUNTIME_METADATA
}
