package io.jnative.rpc;

import java.util.List;

/** Emits human-readable RPC schema and JSON schema artifact. */
public final class RpcSchemaGenerator {

    private RpcSchemaGenerator() {
    }

    public static String toReport(List<RpcServiceModel> services) {
        StringBuilder sb = new StringBuilder();
        sb.append("; JNative 0.28 RPC emit report\n");
        sb.append("Services: ").append(services.size()).append("\n");
        int methods = 0;
        for (RpcServiceModel s : services) {
            methods += s.methods().size();
        }
        sb.append("Methods: ").append(methods).append("\n\n");
        for (RpcServiceModel s : services) {
            sb.append(s.interfaceName()).append(":\n");
            sb.append("  path: ").append(s.path()).append("\n");
            sb.append("  version: ").append(s.version()).append("\n");
            sb.append("  serviceId: ").append(s.serviceId()).append("\n");
            for (RpcMethodModel m : s.methods()) {
                sb.append("  ").append(m.name()).append("\n");
                sb.append("    transport: ").append(m.stream() ? "SSE" : "HTTP").append("\n");
                sb.append("    http: ").append(m.httpMethod()).append(" ").append(m.httpPath()).append("\n");
                sb.append("    methodId: ").append(m.methodId()).append("\n");
                sb.append("    request: ");
                if (m.parameters().isEmpty()) {
                    sb.append("void");
                } else {
                    for (int i = 0; i < m.parameters().size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        RpcParameterModel p = m.parameters().get(i);
                        sb.append(p.name()).append(": ").append(p.typeName());
                    }
                }
                sb.append("\n");
                sb.append("    response: ").append(m.logicalReturnType()).append("\n");
                if (m.authenticated()) {
                    sb.append("    auth: AUTHENTICATED\n");
                }
                if (m.role() != null && !m.role().isEmpty()) {
                    sb.append("    role: ").append(m.role()).append("\n");
                }
                if (m.idempotent()) {
                    sb.append("    idempotent: true\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    public static String toJsonSchema(List<RpcServiceModel> services) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"jnativeRpc\": \"0.28\",\n  \"services\": [\n");
        for (int si = 0; si < services.size(); si++) {
            RpcServiceModel s = services.get(si);
            if (si > 0) {
                sb.append(",\n");
            }
            sb.append("    {\n");
            sb.append("      \"name\": \"").append(s.interfaceName()).append("\",\n");
            sb.append("      \"path\": \"").append(s.path()).append("\",\n");
            sb.append("      \"version\": ").append(s.version()).append(",\n");
            sb.append("      \"serviceId\": ").append(s.serviceId()).append(",\n");
            sb.append("      \"methods\": [\n");
            for (int mi = 0; mi < s.methods().size(); mi++) {
                RpcMethodModel m = s.methods().get(mi);
                if (mi > 0) {
                    sb.append(",\n");
                }
                sb.append("        {\n");
                sb.append("          \"name\": \"").append(m.name()).append("\",\n");
                sb.append("          \"methodId\": ").append(m.methodId()).append(",\n");
                sb.append("          \"httpPath\": \"").append(m.httpPath()).append("\",\n");
                sb.append("          \"httpMethod\": \"").append(m.httpMethod()).append("\",\n");
                sb.append("          \"returnType\": \"").append(m.logicalReturnType()).append("\",\n");
                sb.append("          \"authenticated\": ").append(m.authenticated()).append(",\n");
                sb.append("          \"parameters\": [");
                for (int pi = 0; pi < m.parameters().size(); pi++) {
                    if (pi > 0) {
                        sb.append(", ");
                    }
                    RpcParameterModel p = m.parameters().get(pi);
                    sb.append("{\"name\":\"").append(p.name())
                            .append("\",\"type\":\"").append(p.typeName()).append("\"}");
                }
                sb.append("]\n");
                sb.append("        }");
            }
            sb.append("\n      ]\n");
            sb.append("    }");
        }
        sb.append("\n  ]\n}\n");
        return sb.toString();
    }

    public static String diff(String oldJson, String newJson) {
        // Lightweight text-level break detector for MVP
        StringBuilder out = new StringBuilder();
        out.append("RPC schema diff\n");
        if (oldJson == null || oldJson.isBlank()) {
            out.append("INFO: no previous schema\n");
            return out.toString();
        }
        if (oldJson.equals(newJson)) {
            out.append("OK: schemas identical\n");
            return out.toString();
        }
        out.append("CHANGED: schema content differs (review methodIds / parameter types)\n");
        if (oldJson.contains("\"name\"") && newJson.contains("\"name\"")) {
            out.append("HINT: use methodId stability + version for compatibility decisions\n");
        }
        return out.toString();
    }
}
