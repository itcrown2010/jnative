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

public class ConfigProcessor implements AnnotationProcessor {
    @Override
    public String name() {
        return "ConfigProcessor";
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
        return Set.of(TypeId.of("Config"));
    }

    @Override
    public void process(ProcessingContext context) {
        for (ClassModel c : context.roundEnv().getElementsAnnotatedWith("Config")) {
            AnnotationModel cfg = c.annotation("Config");
            String prefix = cfg != null && cfg.stringValue().isPresent()
                    ? cfg.stringValue().get()
                    : c.simpleName().toLowerCase();
            String parser = c.simpleName() + "ConfigParser";
            SourceFileBuilder b = context.codeGenerator().newSourceFile("config/" + parser + ".java");
            b.origin(c.sourceFile(), c.simpleName()).fingerprint(prefix);
            b.line("class " + parser + " {");
            b.line("    static " + c.simpleName() + " fromEnv() {");
            b.line("        " + c.simpleName() + " cfg = new " + c.simpleName() + "();");
            for (FieldModel f : c.fields()) {
                if (f.isStatic()) {
                    continue;
                }
                String env = (prefix + "_" + f.name()).toUpperCase().replace('-', '_');
                String t = f.type().simpleName();
                if (t.equals("int") || t.equals("long")) {
                    String def = "0";
                    if (f.hasAnnotation("Default") && f.annotation("Default").stringValue().isPresent()) {
                        def = f.annotation("Default").stringValue().get();
                    } else if (f.hasAnnotation("Default") && f.annotation("Default").intValue().isPresent()) {
                        def = String.valueOf(f.annotation("Default").intValue().get());
                    }
                    b.line("        cfg." + f.name() + " = Env.getInt(\"" + env + "\", " + def + ");");
                } else {
                    String def = "\"\"";
                    if (f.hasAnnotation("Default") && f.annotation("Default").stringValue().isPresent()) {
                        def = "\"" + f.annotation("Default").stringValue().get() + "\"";
                    }
                    b.line("        cfg." + f.name() + " = Env.getOr(\"" + env + "\", " + def + ");");
                }
            }
            b.line("        return cfg;");
            b.line("    }");
            b.line("}");
            b.finish();
            context.codeGenerator().createMetadata(
                    "schema/" + c.simpleName() + ".config.schema.json",
                    "{\"prefix\":\"" + prefix + "\",\"type\":\"" + c.simpleName() + "\"}",
                    c.sourceFile(),
                    c.simpleName(),
                    prefix);
        }
    }
}
