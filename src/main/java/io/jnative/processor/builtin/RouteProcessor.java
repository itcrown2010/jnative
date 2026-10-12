/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.processor.builtin;

import io.jnative.meta.AnnotationModel;
import io.jnative.meta.ClassModel;
import io.jnative.meta.MethodModel;
import io.jnative.meta.TypeId;
import io.jnative.processor.AnnotationProcessor;
import io.jnative.processor.ProcessingContext;
import io.jnative.processor.ProcessorKind;
import io.jnative.processor.SourceFileBuilder;

import java.util.List;
import java.util.Set;

/**
 * AGGREGATING: {@code @Controller} → route table with direct method calls (no Method.invoke).
 * Imperative {@code app.get(...)} remains supported.
 * @author 张晓达
 */
public class RouteProcessor implements AnnotationProcessor {
    @Override
    public String name() {
        return "RouteProcessor";
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
        return Set.of(TypeId.of("Controller"), TypeId.of("Get"), TypeId.of("Post"));
    }

    @Override
    public void process(ProcessingContext context) {
        List<ClassModel> controllers = context.roundEnv().getElementsAnnotatedWith("Controller");
        if (controllers.isEmpty()) {
            return;
        }
        SourceFileBuilder b = context.codeGenerator().newSourceFile("web/GeneratedRouteTable.java");
        b.origin("<aggregate>", "RouteTable").fingerprint("routes-" + controllers.size());
        b.line("class GeneratedRouteTable {");
        b.line("    static void mount(WebApp app) {");
        for (ClassModel c : controllers) {
            AnnotationModel ctrl = c.annotation("Controller");
            String base = ctrl != null && ctrl.stringValue().isPresent()
                    ? ctrl.stringValue().get()
                    : "";
            b.line("        " + c.simpleName() + " ctrl_" + c.simpleName()
                    + " = new " + c.simpleName() + "();");
            for (MethodModel m : c.methods()) {
                String http = null;
                String path = "/";
                if (m.hasAnnotation("Get")) {
                    http = "get";
                    AnnotationModel a = m.annotation("Get");
                    if (a != null && a.stringValue().isPresent()) {
                        path = a.stringValue().get();
                    }
                } else if (m.hasAnnotation("Post")) {
                    http = "post";
                    AnnotationModel a = m.annotation("Post");
                    if (a != null && a.stringValue().isPresent()) {
                        path = a.stringValue().get();
                    }
                }
                if (http == null) {
                    continue;
                }
                String full = join(base, path);
                b.line("        app." + http + "(\"" + full + "\", new "
                        + c.simpleName() + "_" + m.name() + "Handler(ctrl_" + c.simpleName() + "));");
            }
        }
        b.line("    }");
        b.line("}");
        b.finish();

        for (ClassModel c : controllers) {
            for (MethodModel m : c.methods()) {
                if (!m.hasAnnotation("Get") && !m.hasAnnotation("Post")) {
                    continue;
                }
                String handler = c.simpleName() + "_" + m.name() + "Handler";
                SourceFileBuilder h = context.codeGenerator().newSourceFile("web/" + handler + ".java");
                h.origin(c.sourceFile(), c.simpleName() + "." + m.name()).fingerprint(handler);
                h.line("class " + handler + " implements WebHandler {");
                h.line("    " + c.simpleName() + " ctrl;");
                h.line("    " + handler + "(" + c.simpleName() + " ctrl) { this.ctrl = ctrl; }");
                h.line("    public void handle(WebContext ctx) {");
                if (m.parameters().isEmpty()) {
                    h.line("        ctrl." + m.name() + "();");
                } else {
                    h.line("        ctrl." + m.name() + "(ctx);");
                }
                h.line("    }");
                h.line("}");
                h.finish();
            }
        }
    }

    private static String join(String base, String path) {
        if (base == null || base.isEmpty()) {
            return path;
        }
        if (path == null || path.isEmpty() || path.equals("/")) {
            return base;
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            return base.substring(0, base.length() - 1) + path;
        }
        if (!base.endsWith("/") && !path.startsWith("/")) {
            return base + "/" + path;
        }
        return base + path;
    }
}
