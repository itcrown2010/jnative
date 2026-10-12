/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.rpc;

import java.util.List;

/**
 * Generates ordinary JNative source for RPC dispatchers and clients.
 * No reflection — direct method invocation in generated handlers.
 *
 * <p>JNative has no {@code Class} literals, so the DX is:
 * <pre>
 *   UserApi_Rpc.mount(app, new UserApiImpl());
 *   UserApi api = UserApi_Rpc.client();
 *   UserApi remote = UserApi_Rpc.client("https://api.example.com");
 * </pre>
 * @author 张晓达
 */
public final class RpcCodeGenerator {

    private RpcCodeGenerator() {
    }

    public static String generate(List<RpcServiceModel> services) {
        StringBuilder sb = new StringBuilder();
        sb.append("// ---- BEGIN GENERATED RPC (jnative 0.28) ----\n");
        sb.append("// RPC LLVM intrinsics: NONE\n\n");
        for (RpcServiceModel service : services) {
            emitHelper(sb, service);
            emitDispatchers(sb, service);
            emitClient(sb, service);
        }
        sb.append("// ---- END GENERATED RPC ----\n");
        return sb.toString();
    }

    private static void emitHelper(StringBuilder sb, RpcServiceModel service) {
        String helper = service.generatedHelperName();
        String client = service.generatedClientName();
        String iface = service.interfaceName();

        sb.append("class ").append(helper).append(" {\n");
        sb.append("    static long SERVICE_ID = ").append(service.serviceId()).append("L;\n");
        sb.append("    static int VERSION = ").append(service.version()).append(";\n");
        sb.append("    static String PATH = \"").append(service.path()).append("\";\n\n");

        sb.append("    static void mount(WebApp app, ").append(iface).append(" impl) {\n");
        for (RpcMethodModel m : service.methods()) {
            String handler = service.generatedDispatcherPrefix() + m.name();
            sb.append("        app.post(\"").append(escape(m.httpPath())).append("\", new ")
                    .append(handler).append("(impl));\n");
        }
        sb.append("    }\n\n");

        sb.append("    static ").append(iface).append(" client() {\n");
        sb.append("        return new ").append(client).append("(\"\");\n");
        sb.append("    }\n\n");

        sb.append("    static ").append(iface).append(" clientAt(String baseUrl) {\n");
        sb.append("        return new ").append(client).append("(baseUrl);\n");
        sb.append("    }\n");
        sb.append("}\n\n");
    }

    private static void emitDispatchers(StringBuilder sb, RpcServiceModel service) {
        String iface = service.interfaceName();
        for (RpcMethodModel m : service.methods()) {
            String handler = service.generatedDispatcherPrefix() + m.name();
            sb.append("class ").append(handler).append(" implements WebHandler {\n");
            sb.append("    ").append(iface).append(" impl;\n\n");
            sb.append("    ").append(handler).append("(").append(iface).append(" impl) {\n");
            sb.append("        this.impl = impl;\n");
            sb.append("    }\n\n");
            sb.append("    public void handle(WebContext ctx) {\n");
            sb.append("        String requestId = RpcRuntime.nextRequestId();\n");
            sb.append("        try {\n");
            emitDecodeArgs(sb, m);
            emitAuthCheck(sb, m);
            emitInvoke(sb, m);
            emitEncodeSuccess(sb, m);
            sb.append("        } catch (RpcException e) {\n");
            sb.append("            ctx.status(e.httpStatus());\n");
            sb.append("            ctx.jsonValue(RpcEnvelope.error(e.code(), e.getMessage(), requestId));\n");
            sb.append("        } catch (RuntimeException e) {\n");
            sb.append("            ctx.status(500);\n");
            sb.append("            ctx.jsonValue(RpcEnvelope.error(\"INTERNAL_ERROR\", \"internal error\", requestId));\n");
            sb.append("        }\n");
            sb.append("    }\n");
            sb.append("}\n\n");
        }
    }

    private static void emitDecodeArgs(StringBuilder sb, RpcMethodModel m) {
        sb.append("            JsonValue body = Json.parse(ctx.bodyString());\n");
        sb.append("            JsonObject args = RpcEnvelope.argsObject(body);\n");
        for (RpcParameterModel p : m.parameters()) {
            if (p.isPrimitive() && p.typeName().equals("int")) {
                sb.append("            int ").append(p.name())
                        .append(" = RpcEnvelope.getInt(args, \"").append(p.name()).append("\");\n");
            } else if (p.isString()) {
                sb.append("            String ").append(p.name())
                        .append(" = RpcEnvelope.getString(args, \"").append(p.name()).append("\");\n");
            } else if (p.isPrimitive() && p.typeName().equals("boolean")) {
                sb.append("            boolean ").append(p.name())
                        .append(" = RpcEnvelope.getBool(args, \"").append(p.name()).append("\");\n");
            } else if (p.list()) {
                sb.append("            ArrayList<").append(p.listElementType()).append("> ")
                        .append(p.name()).append(" = new ArrayList<").append(p.listElementType()).append(">();\n");
                sb.append("            {\n");
                sb.append("                JsonValue __lv = args.get(\"").append(p.name()).append("\");\n");
                sb.append("                if (__lv != null && __lv.isArray()) {\n");
                sb.append("                    JsonArray __la = __lv.asArray();\n");
                sb.append("                    int __i = 0;\n");
                sb.append("                    ").append(p.listElementType()).append("Codec __c = new ")
                        .append(p.listElementType()).append("Codec();\n");
                sb.append("                    while (__i < __la.size()) {\n");
                sb.append("                        ").append(p.name()).append(".add(__c.decode(__la.get(__i)));\n");
                sb.append("                        __i = __i + 1;\n");
                sb.append("                    }\n");
                sb.append("                }\n");
                sb.append("            }\n");
            } else {
                sb.append("            ").append(p.typeName()).append(" ").append(p.name())
                        .append(" = new ").append(p.typeName()).append("Codec().decode(RpcEnvelope.requireArg(args, \"")
                        .append(p.name()).append("\"));\n");
            }
        }
    }

    private static void emitAuthCheck(StringBuilder sb, RpcMethodModel m) {
        if (m.authenticated()) {
            sb.append("            if (!RpcRuntime.isAuthenticated(ctx)) {\n");
            sb.append("                throw new RpcUnauthorizedException(\"authentication required\");\n");
            sb.append("            }\n");
        }
        if (m.role() != null && !m.role().isEmpty()) {
            sb.append("            if (!RpcRuntime.hasRole(ctx, \"").append(escape(m.role())).append("\")) {\n");
            sb.append("                throw new RpcForbiddenException(\"role required: ")
                    .append(escape(m.role())).append("\");\n");
            sb.append("            }\n");
        }
    }

    private static void emitInvoke(StringBuilder sb, RpcMethodModel m) {
        if (m.returnsVoid()) {
            sb.append("            impl.").append(m.name()).append("(");
            appendArgs(sb, m);
            sb.append(");\n");
        } else if (m.returnsList()) {
            sb.append("            ArrayList<").append(m.listElementType()).append("> __result = impl.")
                    .append(m.name()).append("(");
            appendArgs(sb, m);
            sb.append(");\n");
        } else {
            sb.append("            ").append(m.returnTypeName()).append(" __result = impl.")
                    .append(m.name()).append("(");
            appendArgs(sb, m);
            sb.append(");\n");
        }
    }

    private static void appendArgs(StringBuilder sb, RpcMethodModel m) {
        for (int i = 0; i < m.parameters().size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(m.parameters().get(i).name());
        }
    }

    private static void emitEncodeSuccess(StringBuilder sb, RpcMethodModel m) {
        if (m.returnsVoid()) {
            sb.append("            ctx.jsonValue(RpcEnvelope.okNull());\n");
        } else if (m.returnsList()) {
            sb.append("            {\n");
            sb.append("                JsonArray __arr = new JsonArray();\n");
            sb.append("                ").append(m.listElementType()).append("Codec __c = new ")
                    .append(m.listElementType()).append("Codec();\n");
            sb.append("                int __i = 0;\n");
            sb.append("                while (__i < __result.size()) {\n");
            sb.append("                    __arr.add(__c.encode(__result.get(__i)));\n");
            sb.append("                    __i = __i + 1;\n");
            sb.append("                }\n");
            sb.append("                ctx.jsonValue(RpcEnvelope.okValue(__arr));\n");
            sb.append("            }\n");
        } else if (m.returnTypeName().equals("int")) {
            sb.append("            ctx.jsonValue(RpcEnvelope.okInt(__result));\n");
        } else if (m.returnTypeName().equals("String")) {
            sb.append("            ctx.jsonValue(RpcEnvelope.okString(__result));\n");
        } else if (m.returnTypeName().equals("boolean")) {
            sb.append("            ctx.jsonValue(RpcEnvelope.okBool(__result));\n");
        } else {
            sb.append("            ctx.jsonValue(RpcEnvelope.okValue(new ")
                    .append(m.returnTypeName()).append("Codec().encode(__result)));\n");
        }
    }

    private static void emitClient(StringBuilder sb, RpcServiceModel service) {
        String client = service.generatedClientName();
        String iface = service.interfaceName();
        sb.append("class ").append(client).append(" implements ").append(iface).append(" {\n");
        sb.append("    String baseUrl;\n\n");
        sb.append("    ").append(client).append("(String baseUrl) {\n");
        sb.append("        if (baseUrl == null) {\n");
        sb.append("            baseUrl = \"\";\n");
        sb.append("        }\n");
        sb.append("        this.baseUrl = baseUrl;\n");
        sb.append("    }\n\n");

        for (RpcMethodModel m : service.methods()) {
            emitClientMethod(sb, m);
        }
        sb.append("}\n\n");
    }

    private static void emitClientMethod(StringBuilder sb, RpcMethodModel m) {
        sb.append("    public ");
        if (m.returnsVoid()) {
            sb.append("void ");
        } else if (m.returnsList()) {
            sb.append("ArrayList<").append(m.listElementType()).append("> ");
        } else {
            sb.append(m.returnTypeName()).append(" ");
        }
        sb.append(m.name()).append("(");
        for (int i = 0; i < m.parameters().size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            RpcParameterModel p = m.parameters().get(i);
            if (p.list()) {
                sb.append("ArrayList<").append(p.listElementType()).append("> ").append(p.name());
            } else {
                sb.append(p.typeName()).append(" ").append(p.name());
            }
        }
        sb.append(") {\n");
        sb.append("        JsonObject args = new JsonObject();\n");
        for (RpcParameterModel p : m.parameters()) {
            if (p.isPrimitive() && p.typeName().equals("int")) {
                sb.append("        args.putNumber(\"").append(p.name()).append("\", ")
                        .append(p.name()).append(");\n");
            } else if (p.isString()) {
                sb.append("        args.putString(\"").append(p.name()).append("\", ")
                        .append(p.name()).append(");\n");
            } else if (p.isPrimitive() && p.typeName().equals("boolean")) {
                sb.append("        if (").append(p.name()).append(") {\n");
                sb.append("            args.put(\"").append(p.name()).append("\", new JsonBoolean(1));\n");
                sb.append("        } else {\n");
                sb.append("            args.put(\"").append(p.name()).append("\", new JsonBoolean(0));\n");
                sb.append("        }\n");
            } else if (p.list()) {
                sb.append("        {\n");
                sb.append("            JsonArray __arr = new JsonArray();\n");
                sb.append("            ").append(p.listElementType()).append("Codec __c = new ")
                        .append(p.listElementType()).append("Codec();\n");
                sb.append("            int __i = 0;\n");
                sb.append("            while (__i < ").append(p.name()).append(".size()) {\n");
                sb.append("                __arr.add(__c.encode(").append(p.name()).append(".get(__i)));\n");
                sb.append("                __i = __i + 1;\n");
                sb.append("            }\n");
                sb.append("            args.put(\"").append(p.name()).append("\", __arr);\n");
                sb.append("        }\n");
            } else {
                sb.append("        args.put(\"").append(p.name())
                        .append("\", new ").append(p.typeName()).append("Codec().encode(")
                        .append(p.name()).append("));\n");
            }
        }
        sb.append("        JsonObject req = new JsonObject();\n");
        sb.append("        req.put(\"args\", args);\n");
        sb.append("        String url = baseUrl + \"").append(escape(m.httpPath())).append("\";\n");
        sb.append("        HttpResponse resp = RpcHttp.postJson(url, req);\n");
        sb.append("        JsonValue parsed = Json.parse(resp.bodyString());\n");
        sb.append("        RpcEnvelope.throwIfError(parsed);\n");
        if (m.returnsVoid()) {
            sb.append("    }\n\n");
            return;
        }
        if (m.returnsList()) {
            sb.append("        ArrayList<").append(m.listElementType()).append("> __out = new ArrayList<")
                    .append(m.listElementType()).append(">();\n");
            sb.append("        JsonValue __rv = RpcEnvelope.resultValue(parsed);\n");
            sb.append("        if (__rv != null && __rv.isArray()) {\n");
            sb.append("            JsonArray __ra = __rv.asArray();\n");
            sb.append("            ").append(m.listElementType()).append("Codec __c = new ")
                    .append(m.listElementType()).append("Codec();\n");
            sb.append("            int __i = 0;\n");
            sb.append("            while (__i < __ra.size()) {\n");
            sb.append("                __out.add(__c.decode(__ra.get(__i)));\n");
            sb.append("                __i = __i + 1;\n");
            sb.append("            }\n");
            sb.append("        }\n");
            sb.append("        return __out;\n");
        } else if (m.returnTypeName().equals("int")) {
            sb.append("        return RpcEnvelope.resultInt(parsed);\n");
        } else if (m.returnTypeName().equals("String")) {
            sb.append("        return RpcEnvelope.resultString(parsed);\n");
        } else if (m.returnTypeName().equals("boolean")) {
            sb.append("        return RpcEnvelope.resultBool(parsed);\n");
        } else {
            sb.append("        return new ").append(m.returnTypeName())
                    .append("Codec().decode(RpcEnvelope.resultValue(parsed));\n");
        }
        sb.append("    }\n\n");
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
