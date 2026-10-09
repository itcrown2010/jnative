package io.jnative.incremental;

/** Classification of a source edit for minimal invalidation.
 * @author 张晓达
 */
public enum ChangeClassification {
    IMPLEMENTATION_ONLY,
    PUBLIC_API_CHANGE,
    ABI_CHANGE,
    RPC_CONTRACT_CHANGE,
    JSON_SHAPE_CHANGE,
    RESOURCE_ONLY,
    BUILD_CONFIG_CHANGE,
    SOURCE_CHANGED,
    UNCHANGED
}
