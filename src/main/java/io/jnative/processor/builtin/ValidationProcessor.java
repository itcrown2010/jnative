package io.jnative.processor.builtin;

import io.jnative.meta.ClassModel;
import io.jnative.meta.FieldModel;
import io.jnative.meta.TypeId;
import io.jnative.processor.AnnotationProcessor;
import io.jnative.processor.ProcessingContext;
import io.jnative.processor.ProcessorKind;
import io.jnative.processor.SourceFileBuilder;

import java.util.Set;

/** Shared Native/WASM validation codegen from {@code @Validated} + field constraints.
 * @author 张晓达
 */
public class ValidationProcessor implements AnnotationProcessor {
    @Override
    public String name() {
        return "ValidationProcessor";
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
        return Set.of(TypeId.of("Validated"));
    }

    @Override
    public void process(ProcessingContext context) {
        for (ClassModel c : context.roundEnv().getElementsAnnotatedWith("Validated")) {
            String name = c.simpleName() + "Validator";
            SourceFileBuilder b = context.codeGenerator().newSourceFile("validation/" + name + ".java");
            b.origin(c.sourceFile(), c.simpleName()).fingerprint(c.simpleName());
            b.line("class " + name + " {");
            b.line("    static " + name + " INSTANCE = new " + name + "();");
            b.line("    String validate(" + c.simpleName() + " value) {");
            for (FieldModel f : c.fields()) {
                if (f.isStatic()) {
                    continue;
                }
                if (f.hasAnnotation("NotBlank")) {
                    b.line("        if (value." + f.name() + " == null || value." + f.name()
                            + ".length() == 0) {");
                    b.line("            return \"" + f.name() + " must not be blank\";");
                    b.line("        }");
                }
                if (f.hasAnnotation("Min") && f.annotation("Min").intValue().isPresent()) {
                    int min = f.annotation("Min").intValue().get();
                    b.line("        if (value." + f.name() + " < " + min + ") {");
                    b.line("            return \"" + f.name() + " must be >= " + min + "\";");
                    b.line("        }");
                }
                if (f.hasAnnotation("Max") && f.annotation("Max").intValue().isPresent()) {
                    int max = f.annotation("Max").intValue().get();
                    b.line("        if (value." + f.name() + " > " + max + ") {");
                    b.line("            return \"" + f.name() + " must be <= " + max + "\";");
                    b.line("        }");
                }
            }
            b.line("        return null;");
            b.line("    }");
            b.line("    int isValid(" + c.simpleName() + " value) {");
            b.line("        if (validate(value) == null) { return 1; }");
            b.line("        return 0;");
            b.line("    }");
            b.line("}");
            b.finish();
        }
    }
}
