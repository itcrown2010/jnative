package io.jnative.cli;

import io.jnative.compiler.CompilerOptions;

import java.nio.file.Path;

/**
 * @author 张晓达
 */
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
                case "-O0" -> options.setOptLevelExplicitly(0);
                case "-O1", "-Odev" -> options.setOptLevelExplicitly(1);
                case "-O2" -> options.setOptLevelExplicitly(2);
                case "-O3" -> options.setOptLevelExplicitly(3);
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
                case "--verify-llvm" -> options.setVerifyLlvmIr(true);
                case "--no-verify-llvm" -> options.setVerifyLlvmIr(false);
                case "--debug" -> {
                    options.setBuildProfile(io.jnative.compiler.BuildProfile.DEBUG);
                    options.setDebugSymbols(true);
                    options.setEmitSourceDebugInfo(true);
                    // Only default to -O0 when the user did not pass an explicit -O*.
                    if (!options.optLevelExplicit()) {
                        options.setOptLevel(0);
                    }
                }
                case "--release" -> {
                    options.setBuildProfile(io.jnative.compiler.BuildProfile.RELEASE);
                    if (options.optLevel() < 2) {
                        options.setOptLevel(2);
                    }
                }
                case "--sanitize" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("missing value after --sanitize");
                    }
                    options.setSanitizeMode(parseSanitize(args[++i]));
                }
                case "--no-debug-symbols" -> options.setDebugSymbols(false);
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
                case "--target" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("missing value after --target");
                    }
                    options.setTarget(args[++i]);
                }
                case "--emit-wat" -> options.setEmitWat(true);
                case "--emit-wasm" -> options.setEmitWasm(true);
                case "--emit-js-glue" -> options.setEmitJsGlue(true);
                case "--emit-rpc" -> options.setEmitRpc(true);
                case "--emit-rpc-schema" -> options.setEmitRpcSchema(true);
                case "--deterministic" -> options.setDeterministic(true);
                case "--explain-meta" -> options.setExplainMeta(true);
                case "--generated-dir" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("missing path after --generated-dir");
                    }
                    options.setGeneratedDir(Path.of(args[++i]));
                }
                case "build" -> { /* jnative build --target ... */ }
                case "run" -> options.setRunAfterBuild(true);
                case "--gc-stress" -> options.setGcStress("1");
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
                case "--format" -> {
                    // Consumed by CliDispatcher for JSON reports; ignore value here.
                    if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        i++;
                    }
                }
                case "--format=json", "--format=text" -> { /* CliDispatcher */ }
                default -> {
                    if (arg.startsWith("--ui-backend=")) {
                        options.setUiBackend(arg.substring("--ui-backend=".length()));
                        continue;
                    }
                    if (arg.startsWith("--app-type=")) {
                        options.setAppType(arg.substring("--app-type=".length()));
                        continue;
                    }
                    if (arg.startsWith("--target=")) {
                        options.setTarget(arg.substring("--target=".length()));
                        continue;
                    }
                    if (arg.startsWith("--gc-stress=")) {
                        String v = arg.substring("--gc-stress=".length());
                        options.setGcStress(v == null || v.isBlank() ? "1" : v);
                        continue;
                    }
                    if (arg.startsWith("--sanitize=")) {
                        options.setSanitizeMode(parseSanitize(arg.substring("--sanitize=".length())));
                        continue;
                    }
                    if (arg.startsWith("-O") && arg.length() == 3 && Character.isDigit(arg.charAt(2))) {
                        options.setOptLevelExplicitly(arg.charAt(2) - '0');
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

    private static io.jnative.compiler.SanitizeMode parseSanitize(String raw) {
        if (raw == null || raw.isBlank() || "none".equalsIgnoreCase(raw)) {
            return io.jnative.compiler.SanitizeMode.NONE;
        }
        String v = raw.trim().toLowerCase();
        return switch (v) {
            case "address", "asan" -> io.jnative.compiler.SanitizeMode.ASAN;
            case "undefined", "ubsan" -> io.jnative.compiler.SanitizeMode.UBSAN;
            case "address,undefined", "undefined,address", "asan+ubsan", "asan_ubsan" ->
                    io.jnative.compiler.SanitizeMode.ASAN_UBSAN;
            case "thread", "tsan" -> io.jnative.compiler.SanitizeMode.TSAN;
            default -> throw new IllegalArgumentException(
                    "unknown --sanitize mode: " + raw
                            + " (expected address|undefined|address,undefined|thread|none)");
        };
    }

    public static String helpText() {
        return io.jnative.cli.CliDispatcher.helpText();
    }
}
