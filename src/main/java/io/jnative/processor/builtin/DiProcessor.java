/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.processor.builtin;

import io.jnative.diagnostics.SourceLocation;
import io.jnative.meta.ClassModel;
import io.jnative.meta.FieldModel;
import io.jnative.meta.MethodModel;
import io.jnative.meta.ParameterModel;
import io.jnative.meta.TypeId;
import io.jnative.processor.AnnotationProcessor;
import io.jnative.processor.ProcessingContext;
import io.jnative.processor.ProcessorKind;
import io.jnative.processor.SourceFileBuilder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compile-time DI graph. No runtime bean scanning.
 * Detects cycles A→B→C→A at compile time.
 * @author 张晓达
 */
public class DiProcessor implements AnnotationProcessor {
    @Override
    public String name() {
        return "DiProcessor";
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
        return Set.of(TypeId.of("Service"), TypeId.of("Inject"));
    }

    @Override
    public void process(ProcessingContext context) {
        List<ClassModel> services = context.roundEnv().getElementsAnnotatedWith("Service");
        if (services.isEmpty()) {
            return;
        }
        Map<String, List<String>> deps = new HashMap<>();
        for (ClassModel c : services) {
            List<String> d = new ArrayList<>();
            for (FieldModel f : c.fields()) {
                if (f.hasAnnotation("Inject")) {
                    d.add(f.type().simpleName());
                }
            }
            for (MethodModel m : c.methods()) {
                if (m.name().equals(c.simpleName())) {
                    for (ParameterModel p : m.parameters()) {
                        d.add(p.type().simpleName());
                    }
                }
            }
            // Also: single constructor-like naming UserService(...) is MethodModel with isConstructor skipped
            deps.put(c.simpleName(), d);
        }
        // Cycle detection
        for (String s : deps.keySet()) {
            List<String> path = new ArrayList<>();
            if (hasCycle(s, deps, new HashSet<>(), path)) {
                context.error(SourceLocation.UNKNOWN,
                        "JN-META-DI-CYCLE dependency cycle: " + String.join(" → ", path));
                return;
            }
        }
        for (ClassModel c : services) {
            String factory = c.simpleName() + "Factory";
            SourceFileBuilder b = context.codeGenerator().newSourceFile("di/" + factory + ".java");
            b.origin(c.sourceFile(), c.simpleName()).fingerprint(c.simpleName());
            b.line("class " + factory + " {");
            b.line("    static " + c.simpleName() + " INSTANCE;");
            b.line("    static " + c.simpleName() + " get() {");
            b.line("        if (INSTANCE == null) {");
            b.line("            INSTANCE = create();");
            b.line("        }");
            b.line("        return INSTANCE;");
            b.line("    }");
            b.line("    static " + c.simpleName() + " create() {");
            b.line("        " + c.simpleName() + " s = new " + c.simpleName() + "();");
            for (FieldModel f : c.fields()) {
                if (f.hasAnnotation("Inject")) {
                    String dep = f.type().simpleName();
                    b.line("        s." + f.name() + " = " + dep + "Factory.get();");
                }
            }
            b.line("        return s;");
            b.line("    }");
            b.line("}");
            b.finish();
        }
        SourceFileBuilder graph = context.codeGenerator().newSourceFile("di/ApplicationGraph.java");
        graph.origin("<aggregate>", "ApplicationGraph").fingerprint("graph-" + services.size());
        graph.line("class ApplicationGraph {");
        graph.line("    static void warm() {");
        for (ClassModel c : services) {
            graph.line("        " + c.simpleName() + "Factory.get();");
        }
        graph.line("    }");
        graph.line("}");
        graph.finish();
    }

    private static boolean hasCycle(
            String node,
            Map<String, List<String>> deps,
            Set<String> stack,
            List<String> path
    ) {
        if (stack.contains(node)) {
            path.add(node);
            return true;
        }
        if (!deps.containsKey(node)) {
            return false;
        }
        stack.add(node);
        path.add(node);
        for (String d : deps.get(node)) {
            if (hasCycle(d, deps, stack, path)) {
                return true;
            }
        }
        stack.remove(node);
        path.remove(path.size() - 1);
        return false;
    }
}
