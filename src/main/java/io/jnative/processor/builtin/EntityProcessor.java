/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.processor.builtin;

import io.jnative.meta.AnnotationModel;
import io.jnative.meta.ClassModel;
import io.jnative.meta.FieldModel;
import io.jnative.meta.TypeId;
import io.jnative.processor.AnnotationProcessor;
import io.jnative.processor.ProcessingContext;
import io.jnative.processor.ProcessorKind;
import io.jnative.processor.SourceFileBuilder;

import java.util.Set;

/** Compile-time row mapper / SQL metadata (not Hibernate).
 * @author 张晓达
 */
public class EntityProcessor implements AnnotationProcessor {
    @Override
    public String name() {
        return "EntityProcessor";
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
        return Set.of(TypeId.of("Entity"), TypeId.of("Repository"));
    }

    @Override
    public void process(ProcessingContext context) {
        for (ClassModel c : context.roundEnv().getElementsAnnotatedWith("Entity")) {
            AnnotationModel ent = c.annotation("Entity");
            String table = ent != null && ent.stringValue().isPresent()
                    ? ent.stringValue().get()
                    : c.simpleName().toLowerCase() + "s";
            String mapper = c.simpleName() + "RowMapper";
            SourceFileBuilder b = context.codeGenerator().newSourceFile("db/" + mapper + ".java");
            b.origin(c.sourceFile(), c.simpleName()).fingerprint(table);
            b.line("class " + mapper + " implements RowMapper {");
            b.line("    static String TABLE = \"" + table + "\";");
            b.line("    public Object map(Row row) {");
            b.line("        " + c.simpleName() + " e = new " + c.simpleName() + "();");
            int col = 0;
            for (FieldModel f : c.fields()) {
                if (f.isStatic()) {
                    continue;
                }
                String t = f.type().simpleName();
                if (t.equals("int")) {
                    b.line("        e." + f.name() + " = row.getInt(" + col + ");");
                } else if (t.equals("long")) {
                    b.line("        e." + f.name() + " = row.getLong(" + col + ");");
                } else if (t.equals("String")) {
                    b.line("        e." + f.name() + " = row.getString(" + col + ");");
                }
                col++;
            }
            b.line("        return e;");
            b.line("    }");
            b.line("}");
            b.finish();
            context.codeGenerator().createMetadata(
                    "schema/" + c.simpleName() + ".sql.meta.json",
                    "{\"table\":\"" + table + "\",\"entity\":\"" + c.simpleName() + "\"}",
                    c.sourceFile(),
                    c.simpleName(),
                    table);
        }
        for (ClassModel c : context.roundEnv().getElementsAnnotatedWith("Repository")) {
            // Foundation stub: generated empty repository helper
            String helper = c.simpleName() + "Generated";
            SourceFileBuilder b = context.codeGenerator().newSourceFile("db/" + helper + ".java");
            b.origin(c.sourceFile(), c.simpleName()).fingerprint(c.simpleName());
            b.line("class " + helper + " {");
            b.line("    static String name() { return \"" + c.simpleName() + "\"; }");
            b.line("}");
            b.finish();
        }
    }
}
