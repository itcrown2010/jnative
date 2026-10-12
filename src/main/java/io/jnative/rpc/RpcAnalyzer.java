/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.rpc;

import io.jnative.ast.ClassDeclaration;
import io.jnative.ast.CompilationUnit;
import io.jnative.ast.MarkerAnnotation;
import io.jnative.ast.MethodDeclaration;
import io.jnative.ast.Parameter;
import io.jnative.ast.TypeName;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.diagnostics.SourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.CRC32;

/**
 * Compile-time RPC contract analyzer — no runtime reflection.
 * @author 张晓达
 */
public final class RpcAnalyzer {

    private final DiagnosticReporter reporter;

    public RpcAnalyzer(DiagnosticReporter reporter) {
        this.reporter = reporter;
    }

    public List<RpcServiceModel> analyze(CompilationUnit unit) {
        List<RpcServiceModel> services = new ArrayList<>();
        Set<String> paths = new HashSet<>();
        Set<Long> methodIds = new HashSet<>();
        Map<String, List<MarkerAnnotation>> typeAnns = unit.typeAnnotations();
        Map<String, List<MarkerAnnotation>> methodAnns = unit.methodAnnotations();

        for (ClassDeclaration cls : unit.classes()) {
            if (!cls.isInterface()) {
                continue;
            }
            List<MarkerAnnotation> anns = typeAnns.getOrDefault(cls.name(), List.of());
            MarkerAnnotation rpc = find(anns, "Rpc");
            if (rpc == null) {
                continue;
            }
            String path = rpc.stringValue().orElse("/api/" + toKebab(cls.name()));
            int version = rpc.intValue().orElse(1);
            long serviceId = stableHash(cls.name() + "|" + path + "|v" + version);
            RpcServiceModel service = new RpcServiceModel(cls.name(), path, version, serviceId);

            if (!paths.add(path)) {
                reporter.error(cls.location(), "duplicate RPC path '" + path + "'");
            }

            for (MethodDeclaration method : cls.methods()) {
                if (method.isConstructor() || method.isStatic()) {
                    continue;
                }
                RpcMethodModel mm = analyzeMethod(service, method, methodAnns, methodIds);
                if (mm != null) {
                    service.methods().add(mm);
                }
            }
            services.add(service);
        }
        return services;
    }

    private RpcMethodModel analyzeMethod(
            RpcServiceModel service,
            MethodDeclaration method,
            Map<String, List<MarkerAnnotation>> methodAnns,
            Set<Long> methodIds
    ) {
        TypeName ret = method.returnType();
        String returnName = typeSimpleName(ret);
        boolean returnsFuture = "Future".equals(returnName) && ret.hasTypeArguments();
        boolean returnsList = "List".equals(returnName) || "ArrayList".equals(returnName);
        boolean returnsVoid = ret.isVoid()
                || (returnsFuture && ret.typeArguments().get(0).isVoid())
                || "Void".equals(returnName);
        String logicalReturn = returnName;
        String listElement = null;
        if (returnsFuture && ret.hasTypeArguments()) {
            TypeName inner = ret.typeArguments().get(0);
            logicalReturn = typeSimpleName(inner);
            if ("List".equals(logicalReturn) || "ArrayList".equals(logicalReturn)) {
                returnsList = true;
                if (inner.hasTypeArguments()) {
                    listElement = typeSimpleName(inner.typeArguments().get(0));
                }
            } else if ("Void".equals(logicalReturn)) {
                returnsVoid = true;
            }
        } else if (returnsList && ret.hasTypeArguments()) {
            listElement = typeSimpleName(ret.typeArguments().get(0));
            logicalReturn = listElement;
        }

        if (isUnsupportedType(logicalReturn) && !returnsVoid && !returnsList) {
            reporter.error(method.location(),
                    "RPC return type " + logicalReturn + " is not serializable");
            return null;
        }
        if (returnsList && listElement != null && isUnsupportedType(listElement)) {
            reporter.error(method.location(),
                    "RPC return type List<" + listElement + "> is not serializable");
            return null;
        }

        String httpPath = service.path() + "/" + method.name();
        String sig = service.interfaceName() + "." + method.name() + "(";
        for (Parameter p : method.parameters()) {
            sig = sig + typeSimpleName(p.type()) + ",";
        }
        sig = sig + ")" + logicalReturn + "|v" + service.version();
        long methodId = stableHash(sig);
        if (!methodIds.add(methodId)) {
            reporter.error(method.location(), "duplicate RPC method id for " + method.name());
        }

        RpcMethodModel mm = new RpcMethodModel(
                method.name(),
                methodId,
                httpPath,
                returnsVoid ? "void" : (returnsList ? listElement : logicalReturn),
                returnsVoid,
                returnsFuture,
                returnsList,
                listElement
        );

        List<MarkerAnnotation> mans = methodAnns.getOrDefault(
                service.interfaceName() + "." + method.name(), List.of());
        for (MarkerAnnotation a : mans) {
            switch (a.name()) {
                case "Authenticated" -> mm.setAuthenticated(true);
                case "Role" -> mm.setRole(a.stringValue().orElse(""));
                case "Permission" -> mm.setPermission(a.stringValue().orElse(""));
                case "RpcTimeout" -> mm.setTimeoutMs(a.intValue().orElse(0));
                case "Idempotent" -> mm.setIdempotent(true);
                case "RpcStream" -> mm.setStream(true);
                case "RpcGet" -> {
                    mm.setHttpMethod("GET");
                    a.stringValue().ifPresent(p -> {
                        // keep default POST path unless explicit
                    });
                }
                default -> {
                }
            }
        }

        for (Parameter p : method.parameters()) {
            String tn = typeSimpleName(p.type());
            boolean list = "List".equals(tn) || "ArrayList".equals(tn);
            String elem = null;
            if (list && p.type().hasTypeArguments()) {
                elem = typeSimpleName(p.type().typeArguments().get(0));
            }
            if (isUnsupportedType(tn) && !list) {
                reporter.error(p.location(),
                        "RPC parameter type " + tn + " is not serializable");
                continue;
            }
            // Skip RpcContext — injected, not wire
            if ("RpcContext".equals(tn)) {
                continue;
            }
            RpcParameterModel pm = new RpcParameterModel(p.name(), tn, list, elem);
            mm.parameters().add(pm);
        }
        return mm;
    }

    private static boolean isUnsupportedType(String name) {
        if (name == null) {
            return true;
        }
        return name.equals("NativePtr")
                || name.equals("Thread")
                || name.equals("Element")
                || name.equals("Document")
                || name.equals("JsHandle")
                || name.equals("JsObject")
                || name.equals("WebUiComponent")
                || name.equals("Component")
                || name.equals("TcpSocket")
                || name.equals("File");
    }

    private static MarkerAnnotation find(List<MarkerAnnotation> anns, String name) {
        for (MarkerAnnotation a : anns) {
            if (a.name().equals(name)) {
                return a;
            }
        }
        return null;
    }

    private static String typeSimpleName(TypeName t) {
        if (t == null) {
            return "void";
        }
        if (t.isVoid()) {
            return "void";
        }
        return t.name();
    }

    private static String toKebab(String name) {
        if (name.endsWith("Api")) {
            name = name.substring(0, name.length() - 3);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isUpperCase(c) && i > 0) {
                sb.append('-');
            }
            sb.append(Character.toLowerCase(c));
        }
        return sb.toString() + "s";
    }

    static long stableHash(String s) {
        CRC32 crc = new CRC32();
        crc.update(s.getBytes(StandardCharsets.UTF_8));
        return crc.getValue();
    }
}
