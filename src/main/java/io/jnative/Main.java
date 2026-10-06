package io.jnative;

import io.jnative.cli.CliArguments;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.diagnostics.Diagnostic;

/**
 * JNative CLI entry point.
 */
public final class Main {
    public static final String VERSION = "0.12";

    private Main() {
    }

    public static void main(String[] args) {
        int code = run(args);
        System.exit(code);
    }

    public static int run(String[] args) {
        CompilerOptions options;
        try {
            options = CliArguments.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println("error: " + e.getMessage());
            System.err.println(CliArguments.helpText());
            return 1;
        }

        if (options.help()) {
            System.out.print(CliArguments.helpText());
            return 0;
        }
        if (options.version()) {
            System.out.println("JNative " + VERSION);
            return 0;
        }
        if (options.sourceFile() == null) {
            System.err.println("error: no input file");
            System.err.println(CliArguments.helpText());
            return 1;
        }

        CompileResult result = new JNativeCompiler().compile(options);
        for (Diagnostic d : result.diagnostics()) {
            System.err.println(d);
        }
        return result.success() ? 0 : 1;
    }
}
