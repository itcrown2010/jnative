package io.jnative.processor;

import io.jnative.meta.TypeId;

import java.util.Set;

/**
 * Compile-time annotation processor. Must not mutate AST/SymbolTable/LLVM.
 * Outputs: generated JNative source, resources, metadata.
 */
public interface AnnotationProcessor {

    String name();

    String version();

    default int minProcessorApi() {
        return 1;
    }

    default int maxProcessorApi() {
        return 0; // 0 = no upper bound
    }

    default ProcessorKind kind() {
        return ProcessorKind.ISOLATING;
    }

    default Set<ProcessorPermission> permissions() {
        return Set.of(
                ProcessorPermission.READ_DECLARED_FILES,
                ProcessorPermission.WRITE_GENERATED);
    }

    Set<TypeId> supportedAnnotations();

    void process(ProcessingContext context);
}
