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
                case "-O0" -> options.setOptLevel(0);
                case "-O1" -> options.setOptLevel(1);
                case "-O2" -> options.setOptLevel(2);
                case "-O3" -> options.setOptLevel(3);
                case "-o" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("missing output path after -o");
                    }
                    options.setOutputFile(Path.of(args[++i]));
                }
                case "--emit-ast" -> options.setEmitAst(true);
                case "--emit-ir" -> options.setEmitIr(true);
                case "--emit-llvm" -> options.setEmitLlvm(true);
                case "--emit-layout" -> options.setEmitLayout(true);
                case "--emit-effects" -> options.setEmitEffects(true);
                case "--emit-async" -> options.setEmitAsync(true);
                case "--emit-generics" -> options.setEmitGenerics(true);
                case "--explain-generics" -> options.setExplainGenerics(true);
                case "--emit-ir-before-opt" -> options.setEmitIrBeforeOpt(true);
                case "--emit-ir-after-opt" -> options.setEmitIrAfterOpt(true);
                case "--opt-report" -> options.setOptReport(true);
                case "--emit-escape-analysis" -> options.setEmitEscapeAnalysis(true);
                case "--explain-opt" -> options.setExplainOpt(true);
                case "--verify-ir" -> options.setVerifyIr(true);
                case "--keep-temps" -> options.setKeepTemps(true);
                case "--headless" -> options.setHeadless(true);
                case "--ui-backend" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("missing value after --ui-backend");
                    }
                    options.setUiBackend(args[++i]);
                }
                case "--app-type" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("missing value after --app-type");
                    }
                    options.setAppType(args[++i]);
                }
                case "--library-path", "-L" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("missing path after " + arg);
                    }
                    options.addLibraryPath(Path.of(args[++i]));
                }
                case "--link", "-l" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("missing library after " + arg);
                    }
                    options.addLinkLibrary(args[++i]);
                }
                default -> {
                    if (arg.startsWith("--ui-backend=")) {
                        options.setUiBackend(arg.substring("--ui-backend=".length()));
                        continue;
                    }
                    if (arg.startsWith("--app-type=")) {
                        options.setAppType(arg.substring("--app-type=".length()));
                        continue;
                    }
                    if (arg.startsWith("-O") && arg.length() == 3 && Character.isDigit(arg.charAt(2))) {
                        options.setOptLevel(arg.charAt(2) - '0');
                        continue;
                    }
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
                JNative 0.23 — experimental Java native compiler

                Usage:
                  jnative <source.java> [-o <output>] [-O0|-O1|-O2|-O3]
                  jnative <source.java> --emit-ast
                  jnative <source.java> --emit-ir
                  jnative <source.java> --emit-llvm
                  jnative <source.java> --emit-layout
                  jnative <source.java> --emit-effects
                  jnative --version
                  jnative --help

                Options:
                  -o <path>              Output executable path
                  -O0, -O1, -O2, -O3      Optimization level (default -O2)
                  --emit-ast             Print AST and stop
                  --emit-ir              Print JNative IR (after opt if -O>0) and stop
                  --emit-ir-before-opt   Print IR before optimization passes
                  --emit-ir-after-opt    Print IR after optimization passes
                  --emit-llvm            Print LLVM IR and stop
                  --emit-layout          Print NativeStruct layouts and stop
                  --emit-effects         Print inferred effect report and stop
                  --emit-async           Print async/await lowering report and stop
                  --emit-generics        Print generic lowering report and stop
                  --explain-generics     Same as --emit-generics (verbose alias)
                  --opt-report           Print optimization counters
                  --emit-escape-analysis Include escape info in opt report
                  --explain-opt          Verbose optimization notes
                  --verify-ir            Verify IR after optimization pipeline
                  --headless             Force headless UI backend (no real window)
                  --ui-backend <name>    UI backend: auto|headless|cocoa|win32|gtk
                  --app-type <name>      console (default) or gui
                  --library-path <dir>   Add native library search path (-L)
                  --link <name>          Link additional native library (-l)
                  --keep-temps           Keep temporary .ll files
                  -h, --help             Show this help
                  -v, --version          Show version
                """;
    }
}
