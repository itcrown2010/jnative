/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.processor;

import io.jnative.meta.TypeId;

import java.util.Set;

/**
 * Compile-time annotation processor. Must not mutate AST/SymbolTable/LLVM.
 * Outputs: generated JNative source, resources, metadata.
 * @author 张晓达
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
