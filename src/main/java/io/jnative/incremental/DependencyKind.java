package io.jnative.incremental;

/** Dependency edge kinds in the symbol/source graph.
 * @author 张晓达
 */
public enum DependencyKind {
    TYPE_REFERENCE,
    METHOD_CALL,
    FIELD_REFERENCE,
    INHERITANCE,
    INTERFACE_IMPLEMENTATION,
    GENERIC_INSTANTIATION,
    ANNOTATION,
    JSON_CODEC,
    RPC_CONTRACT,
    UI_COMPONENT,
    RESOURCE,
    INLINE_DEPENDENCY,
    OPTIMIZATION_DEPENDENCY
}
