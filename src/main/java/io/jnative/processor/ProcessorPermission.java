package io.jnative.processor;

/** Declared processor permissions. Defaults are least privilege.
 * @author 张晓达
 */
public enum ProcessorPermission {
    READ_PROJECT,
    READ_DECLARED_FILES,
    WRITE_GENERATED,
    NETWORK,
    PROCESS,
    ENVIRONMENT,
    NATIVE
}
