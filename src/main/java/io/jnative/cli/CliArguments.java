package io.jnative.cli;

import io.jnative.compiler.CompilerOptions;

import java.nio.file.Path;

public final class CliArguments {

    private CliArguments() {
    }

    public static CompilerOptions parse(String[] args) {
        CompilerOptions options = new CompilerOptions();
        if (args == null || args.length == 0) {
            options.setHelp(true);
            return options;
        }

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "-h", "--help" -> options.setHelp(true);
                case "-v", "--version" -> options.setVersion(true);
                case "-o" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("missing output path after -o");
                    }
                    options.setOutputFile(Path.of(args[++i]));
                }
                case "--emit-ast" -> options.setEmitAst(true);
                case "--emit-ir" -> options.setEmitIr(true);
                case "--emit-llvm" -> options.setEmitLlvm(true);
                case "--keep-temps" -> options.setKeepTemps(true);
                default -> {
                    if (arg.startsWith("-")) {
                        throw new IllegalArgumentException("unknown option: " + arg);
                    }
                    if (options.sourceFile() != null) {
                        throw new IllegalArgumentException("multiple source files are not supported");
                    }
                    options.setSourceFile(Path.of(arg));
                }
            }
        }
        return options;
    }

    public static String helpText() {
        return """
                JNative 0.3 — experimental Java native compiler

                Usage:
                  jnative <source.java> [-o <output>]
                  jnative <source.java> --emit-ast
                  jnative <source.java> --emit-ir
                  jnative <source.java> --emit-llvm
                  jnative --version
                  jnative --help

                Options:
                  -o <path>       Output executable path
                  --emit-ast      Print AST and stop
                  --emit-ir       Print JNative IR and stop
                  --emit-llvm     Print LLVM IR and stop
                  --keep-temps    Keep temporary .ll files
                  -h, --help      Show this help
                  -v, --version   Show version
                """;
    }
}
