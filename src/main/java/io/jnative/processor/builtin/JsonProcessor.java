/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.processor.builtin;

import io.jnative.meta.ClassModel;
import io.jnative.meta.FieldModel;
import io.jnative.meta.TypeId;
import io.jnative.processor.AnnotationProcessor;
import io.jnative.processor.GeneratedArtifact;
import io.jnative.processor.ProcessingContext;
import io.jnative.processor.ProcessorKind;
import io.jnative.processor.SourceFileBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;

/**
 * ISOLATING: {@code @Json}/{@code @GenerateJson} → {@code XxxJsonCodec} implementing {@code JsonCodec}.
 * ZERO runtime reflection.
 * @author 张晓达
 */
public class JsonProcessor implements AnnotationProcessor {
    @Override
    public String name() {
        return "JsonProcessor";
    }

    @Override
    public String version() {
        return "0.32.0";
    }

    @Override
    public ProcessorKind kind() {
        return ProcessorKind.ISOLATING;
    }

    @Override
    public Set<TypeId> supportedAnnotations() {
        return Set.of(TypeId.of("Json"), TypeId.of("GenerateJson"));
    }

    @Override
    public void process(ProcessingContext context) {
        for (ClassModel c : context.roundEnv().getElementsAnnotatedWith("Json")) {
            emit(context, c);
        }
        for (ClassModel c : context.roundEnv().getElementsAnnotatedWith("GenerateJson")) {
            emit(context, c);
        }
    }

    private void emit(ProcessingContext context, ClassModel c) {
        if (c.isInterface()) {
            context.error(c.location(), "@Json cannot be applied to interface " + c.simpleName());
            return;
        }
        String codec = c.simpleName() + "JsonCodec";
        String fp = fingerprint(c);
        String codecPath = "json/" + codec + ".java";
        String schemaPath = "schema/" + c.simpleName() + ".json.schema.json";
        String descPath = "json/" + c.simpleName() + "TypeDescriptor.java";
        String aliasPath = "json/" + c.simpleName() + "Codec.java";
        if (context.isUnchanged(codecPath, fp)
                && context.isUnchanged(schemaPath, fp)
                && context.isUnchanged(descPath, fp)
                && (aliasPath.equals(codecPath) || context.isUnchanged(aliasPath, fp))
                && context.reuseGenerated(GeneratedArtifact.Kind.SOURCE, codecPath,
                c.sourceFile(), c.simpleName(), fp)
                && context.reuseGenerated(GeneratedArtifact.Kind.METADATA, schemaPath,
                c.sourceFile(), c.simpleName(), fp)
                && context.reuseGenerated(GeneratedArtifact.Kind.SOURCE, descPath,
                c.sourceFile(), c.simpleName(), fp)
                && (aliasPath.equals(codecPath)
                || context.reuseGenerated(GeneratedArtifact.Kind.SOURCE, aliasPath,
                c.sourceFile(), c.simpleName(), fp))) {
            return;
        }
        SourceFileBuilder b = context.codeGenerator().newSourceFile(codecPath);
        b.origin(c.sourceFile(), c.simpleName()).fingerprint(fp);
        // Prefer explicit implements when generics parse cleanly; keep methods JsonCodec-shaped.
        b.line("class " + codec + " implements JsonCodec<" + c.simpleName() + "> {");
        b.line("    static " + codec + " INSTANCE = new " + codec + "();");
        b.line("");
        b.line("    JsonValue encode(" + c.simpleName() + " value) {");
        b.line("        JsonObject o = new JsonObject();");
        for (FieldModel f : c.fields()) {
            if (f.isStatic()) {
                if (f.hasAnnotation("JsonField")) {
                    context.error(f.location(),
                            "JN-META-1021 @JsonField cannot be applied to static field.");
                }
                continue;
            }
            String jsonName = jsonName(f);
            String t = f.type().simpleName();
            if (t.equals("int") || t.equals("long") || t.equals("short") || t.equals("byte")) {
                b.line("        o.putNumber(\"" + escape(jsonName) + "\", value." + f.name() + ");");
            } else if (t.equals("boolean")) {
                b.line("        if (value." + f.name() + ") {");
                b.line("            o.put(\"" + escape(jsonName) + "\", new JsonBoolean(1));");
                b.line("        } else {");
                b.line("            o.put(\"" + escape(jsonName) + "\", new JsonBoolean(0));");
                b.line("        }");
            } else if (t.equals("String")) {
                b.line("        o.putString(\"" + escape(jsonName) + "\", value." + f.name() + ");");
            } else {
                b.line("        o.putString(\"" + escape(jsonName) + "\", \"\");");
            }
        }
        b.line("        return o;");
        b.line("    }");
        b.line("");
        b.line("    " + c.simpleName() + " decode(JsonValue value) {");
        b.line("        " + c.simpleName() + " out = new " + c.simpleName() + "();");
        b.line("        JsonObject o = value.asObject();");
        for (FieldModel f : c.fields()) {
            if (f.isStatic()) {
                continue;
            }
            String jsonName = jsonName(f);
            String t = f.type().simpleName();
            if (t.equals("int") || t.equals("short") || t.equals("byte")) {
                b.line("        out." + f.name() + " = o.getInt(\"" + escape(jsonName) + "\", 0);");
            } else if (t.equals("long")) {
                b.line("        out." + f.name() + " = o.getLong(\"" + escape(jsonName) + "\", 0);");
            } else if (t.equals("boolean")) {
                b.line("        if (o.has(\"" + escape(jsonName) + "\")) {");
                b.line("            out." + f.name() + " = o.get(\"" + escape(jsonName)
                        + "\").asBoolean();");
                b.line("        }");
            } else if (t.equals("String")) {
                b.line("        out." + f.name() + " = o.getString(\"" + escape(jsonName) + "\", \"\");");
            }
        }
        b.line("        return out;");
        b.line("    }");
        b.line("}");
        b.finish();

        // RpcCodeGenerator expects XxxCodec; emit thin alias (no reflection).
        String alias = c.simpleName() + "Codec";
        if (!alias.equals(codec)) {
            context.codeGenerator().createSourceFile(
                    "json/" + alias + ".java",
                    "class " + alias + " implements JsonCodec<" + c.simpleName() + "> {\n"
                            + "    JsonValue encode(" + c.simpleName() + " value) {\n"
                            + "        return " + codec + ".INSTANCE.encode(value);\n"
                            + "    }\n"
                            + "    " + c.simpleName() + " decode(JsonValue value) {\n"
                            + "        return " + codec + ".INSTANCE.decode(value);\n"
                            + "    }\n"
                            + "}\n",
                    c.sourceFile(),
                    c.simpleName(),
                    fp);
        }

        context.codeGenerator().createMetadata(
                "schema/" + c.simpleName() + ".json.schema.json",
                schemaJson(c),
                c.sourceFile(),
                c.simpleName(),
                fp);
        context.codeGenerator().createSourceFile(
                "json/" + c.simpleName() + "TypeDescriptor.java",
                "class " + c.simpleName() + "TypeDescriptor {\n"
                        + "    static " + c.simpleName() + "TypeDescriptor INSTANCE = new "
                        + c.simpleName() + "TypeDescriptor();\n"
                        + "    String typeName() { return \"" + c.simpleName() + "\"; }\n"
                        + "}\n",
                c.sourceFile(),
                c.simpleName(),
                fp);
    }

    private static String jsonName(FieldModel f) {
        if (f.hasAnnotation("JsonField") && f.annotation("JsonField").stringValue().isPresent()) {
            return f.annotation("JsonField").stringValue().get();
        }
        return f.name();
    }

    private static String schemaJson(ClassModel c) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"type\":\"object\",\"title\":\"").append(c.simpleName()).append("\",\"properties\":{");
        boolean first = true;
        for (FieldModel f : c.fields()) {
            if (f.isStatic()) {
                continue;
            }
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(escape(jsonName(f))).append("\":{\"type\":\"")
                    .append(schemaType(f.type().simpleName())).append("\"}");
        }
        sb.append("}}");
        return sb.toString();
    }

    private static String schemaType(String t) {
        if (t.equals("int") || t.equals("long") || t.equals("short") || t.equals("byte")) {
            return "integer";
        }
        if (t.equals("boolean")) {
            return "boolean";
        }
        return "string";
    }

    private static String fingerprint(ClassModel c) {
        StringBuilder sb = new StringBuilder(c.simpleName());
        for (FieldModel f : c.fields()) {
            sb.append('|').append(f.name()).append(':').append(f.type().simpleName());
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(sb.toString().getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 16);
        } catch (Exception e) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
