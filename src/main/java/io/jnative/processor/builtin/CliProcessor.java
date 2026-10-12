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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** AGGREGATING: {@code @Command} classes → dispatcher + help (no runtime annotation scan).
 * @author 张晓达
 */
public class CliProcessor implements AnnotationProcessor {
    @Override
    public String name() {
        return "CliProcessor";
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
        return Set.of(TypeId.of("Command"));
    }

    @Override
    public void process(ProcessingContext context) {
        List<ClassModel> commands = context.roundEnv().getElementsAnnotatedWith("Command");
        if (commands.isEmpty()) {
            return;
        }
        for (ClassModel c : commands) {
            emitCommandParser(context, c);
        }
        SourceFileBuilder b = context.codeGenerator().newSourceFile("cli/GeneratedCliDispatch.java");
        b.origin("<aggregate>", "CliDispatch").fingerprint("cli-" + commands.size());
        b.line("class GeneratedCliDispatch {");
        b.line("    static int dispatch(String[] args) {");
        b.line("        if (args == null || args.length == 0) {");
        b.line("            printHelp();");
        b.line("            return 1;");
        b.line("        }");
        b.line("        String cmd = args[0];");
        for (ClassModel c : commands) {
            AnnotationModel ann = c.annotation("Command");
            String cmdName = ann != null && ann.stringValue().isPresent()
                    ? ann.stringValue().get()
                    : c.simpleName().toLowerCase();
            b.line("        if (cmd.equals(\"" + cmdName + "\")) {");
            b.line("            return " + c.simpleName() + "CliParser.run(args);");
            b.line("        }");
        }
        b.line("        printHelp();");
        b.line("        return 1;");
        b.line("    }");
        b.line("    static void printHelp() {");
        b.line("        System.out.println(\"Commands:\");");
        for (ClassModel c : commands) {
            AnnotationModel ann = c.annotation("Command");
            String cmdName = ann != null && ann.stringValue().isPresent()
                    ? ann.stringValue().get()
                    : c.simpleName().toLowerCase();
            b.line("        System.out.println(\"  " + cmdName + "\");");
        }
        b.line("    }");
        b.line("}");
        b.finish();
    }

    private void emitCommandParser(ProcessingContext context, ClassModel c) {
        String parser = c.simpleName() + "CliParser";
        SourceFileBuilder b = context.codeGenerator().newSourceFile("cli/" + parser + ".java");
        b.origin(c.sourceFile(), c.simpleName()).fingerprint(c.simpleName());
        List<FieldModel> options = new ArrayList<>();
        for (FieldModel f : c.fields()) {
            if (f.hasAnnotation("Option")) {
                options.add(f);
            }
        }
        b.line("class " + parser + " {");
        b.line("    static int run(String[] args) {");
        b.line("        " + c.simpleName() + " cmd = new " + c.simpleName() + "();");
        b.line("        int i = 1;");
        b.line("        while (i < args.length) {");
        b.line("            String a = args[i];");
        b.line("            int matched = 0;");
        for (FieldModel f : options) {
            AnnotationModel opt = f.annotation("Option");
            String flag = opt != null && opt.stringValue().isPresent()
                    ? opt.stringValue().get()
                    : "--" + f.name();
            b.line("            if (matched == 0 && a.equals(\"" + flag + "\")) {");
            b.line("                matched = 1;");
            b.line("                i = i + 1;");
            b.line("                if (i >= args.length) {");
            b.line("                    System.out.println(\"missing value for " + flag + "\");");
            b.line("                    return 2;");
            b.line("                }");
            if (f.type().simpleName().equals("String")) {
                b.line("                cmd." + f.name() + " = args[i];");
            } else {
                b.line("                cmd." + f.name() + " = args[i].length();");
            }
            b.line("            }");
        }
        b.line("            if (matched == 0 && a.equals(\"--help\")) {");
        b.line("                System.out.println(\"usage: " + c.simpleName() + " [options]\");");
        b.line("                return 0;");
        b.line("            }");
        b.line("            i = i + 1;");
        b.line("        }");
        b.line("        return 0;");
        b.line("    }");
        b.line("}");
        b.finish();
    }
}
