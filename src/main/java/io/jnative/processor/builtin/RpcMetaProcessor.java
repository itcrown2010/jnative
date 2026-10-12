/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.processor.builtin;

import io.jnative.meta.ClassModel;
import io.jnative.meta.TypeId;
import io.jnative.processor.AnnotationProcessor;
import io.jnative.processor.ProcessingContext;
import io.jnative.processor.ProcessorKind;
import io.jnative.rpc.RpcAnalyzer;
import io.jnative.rpc.RpcCodeGenerator;
import io.jnative.rpc.RpcSchemaGenerator;
import io.jnative.rpc.RpcServiceModel;

import java.util.List;
import java.util.Set;

/**
 * Bridges 0.28 {@link RpcAnalyzer}/{@link RpcCodeGenerator} onto the unified Meta API.
 * Still emits ordinary JNative source — no reflection.
 * @author 张晓达
 */
public class RpcMetaProcessor implements AnnotationProcessor {
    @Override
    public String name() {
        return "RpcProcessor";
    }

    @Override
    public String version() {
        return "0.32.0";
    }

    @Override
    public ProcessorKind kind() {
        return ProcessorKind.AGGREGATING;
    }

    @Override
    public Set<TypeId> supportedAnnotations() {
        return Set.of(TypeId.of("Rpc"));
    }

    @Override
    public void process(ProcessingContext context) {
        List<ClassModel> rpcTypes = context.roundEnv().getElementsAnnotatedWith("Rpc");
        if (rpcTypes.isEmpty()) {
            return;
        }
        // Schema artifact for explain / Studio; actual source merge still handled by JNativeCompiler
        // via RpcAnalyzer for ABI stability. Meta layer records origin tracking.
        for (ClassModel c : rpcTypes) {
            context.codeGenerator().createMetadata(
                    "schema/" + c.simpleName() + ".rpc.schema.json",
                    "{\"service\":\"" + c.simpleName() + "\",\"processor\":\"RpcProcessor\"}",
                    c.sourceFile(),
                    c.simpleName(),
                    c.simpleName());
            context.info(c.location(),
                    "RpcProcessor registered " + c.simpleName()
                            + " (dispatcher/client via RpcCodeGenerator)");
        }
        // Also emit a thin registration helper for Studio / explain
        StringBuilder sb = new StringBuilder();
        sb.append("class GeneratedRpcRegistry {\n");
        sb.append("    static int serviceCount() { return ").append(rpcTypes.size()).append("; }\n");
        sb.append("}\n");
        context.codeGenerator().createSourceFile(
                "rpc/GeneratedRpcRegistry.java",
                sb.toString(),
                "<aggregate>",
                "RpcRegistry",
                "rpc-" + rpcTypes.size());
    }

    /** Helper used by tests / CLI explain — runs classic generator. */
    public static String generateSource(List<RpcServiceModel> services) {
        return RpcCodeGenerator.generate(services);
    }

    public static String generateSchema(List<RpcServiceModel> services) {
        return RpcSchemaGenerator.toJsonSchema(services);
    }
}
