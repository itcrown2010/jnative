package io.jnative.compiler;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class CompilerOptions {
    private Path sourceFile;
    private Path outputFile;
    private boolean emitAst;
    private boolean emitIr;
    private boolean emitLlvm;
    private boolean emitLayout;
    private boolean emitEffects;
    private boolean emitAsync;
    private boolean emitGenerics;
    private boolean explainGenerics;
    private int optLevel = 2;
    private boolean emitIrBeforeOpt;
    private boolean emitIrAfterOpt;
    private boolean optReport;
    private boolean emitEscapeAnalysis;
    private boolean explainOpt;
    private boolean verifyIr;
    private boolean keepTemps;
    private boolean help;
    private boolean version;
    private boolean headless;
    private String uiBackend = "auto";
    private String appType = "console";
    private String target = "native";
    private boolean emitWat;
    private boolean emitWasm;
    private boolean emitJsGlue;
    private boolean runAfterBuild;
    private final List<Path> libraryPaths = new ArrayList<>();
    private final List<String> linkLibraries = new ArrayList<>();
    private final List<Path> extraNativeSources = new ArrayList<>();
    private final List<String> extraLinkFlags = new ArrayList<>();

    public String target() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target == null ? "native" : target;
    }

    public boolean emitWat() {
        return emitWat;
    }

    public void setEmitWat(boolean emitWat) {
        this.emitWat = emitWat;
    }

    public boolean emitWasm() {
        return emitWasm;
    }

    public void setEmitWasm(boolean emitWasm) {
        this.emitWasm = emitWasm;
    }

    public boolean emitJsGlue() {
        return emitJsGlue;
    }

    public void setEmitJsGlue(boolean emitJsGlue) {
        this.emitJsGlue = emitJsGlue;
    }

    public boolean runAfterBuild() {
        return runAfterBuild;
    }

    public void setRunAfterBuild(boolean runAfterBuild) {
        this.runAfterBuild = runAfterBuild;
    }

    public boolean isWasmBrowserTarget() {
        try {
            return io.jnative.backend.CompileTarget.parse(target).isBrowser();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public Path sourceFile() {
        return sourceFile;
    }

    public void setSourceFile(Path sourceFile) {
        this.sourceFile = sourceFile;
    }

    public Path outputFile() {
        return outputFile;
    }

    public void setOutputFile(Path outputFile) {
        this.outputFile = outputFile;
    }

    public boolean emitAst() {
        return emitAst;
    }

    public void setEmitAst(boolean emitAst) {
        this.emitAst = emitAst;
    }

    public boolean emitIr() {
        return emitIr;
    }

    public void setEmitIr(boolean emitIr) {
        this.emitIr = emitIr;
    }

    public boolean emitLlvm() {
        return emitLlvm;
    }

    public void setEmitLlvm(boolean emitLlvm) {
        this.emitLlvm = emitLlvm;
    }

    public boolean emitLayout() {
        return emitLayout;
    }

    public void setEmitLayout(boolean emitLayout) {
        this.emitLayout = emitLayout;
    }

    public boolean emitEffects() {
        return emitEffects;
    }

    public void setEmitEffects(boolean emitEffects) {
        this.emitEffects = emitEffects;
    }

    public boolean emitAsync() {
        return emitAsync;
    }

    public void setEmitAsync(boolean emitAsync) {
        this.emitAsync = emitAsync;
    }

    public boolean emitGenerics() {
        return emitGenerics;
    }

    public void setEmitGenerics(boolean emitGenerics) {
        this.emitGenerics = emitGenerics;
    }

    public boolean explainGenerics() {
        return explainGenerics;
    }

    public void setExplainGenerics(boolean explainGenerics) {
        this.explainGenerics = explainGenerics;
    }

    public boolean keepTemps() {
        return keepTemps;
    }

    public void setKeepTemps(boolean keepTemps) {
        this.keepTemps = keepTemps;
    }

    public boolean help() {
        return help;
    }

    public void setHelp(boolean help) {
        this.help = help;
    }

    public boolean version() {
        return version;
    }

    public void setVersion(boolean version) {
        this.version = version;
    }

    public List<Path> libraryPaths() {
        return libraryPaths;
    }

    public void addLibraryPath(Path path) {
        libraryPaths.add(path);
    }

    public List<String> linkLibraries() {
        return linkLibraries;
    }

    public void addLinkLibrary(String lib) {
        linkLibraries.add(lib);
    }

    public List<Path> extraNativeSources() {
        return extraNativeSources;
    }

    public void addExtraNativeSource(Path path) {
        extraNativeSources.add(path);
    }

    public List<String> extraLinkFlags() {
        return extraLinkFlags;
    }

    public void addExtraLinkFlag(String flag) {
        if (flag != null && !flag.isBlank()) {
            extraLinkFlags.add(flag);
        }
    }

    public boolean headless() {
        return headless;
    }

    public void setHeadless(boolean headless) {
        this.headless = headless;
    }

    public String uiBackend() {
        return uiBackend;
    }

    public void setUiBackend(String uiBackend) {
        this.uiBackend = uiBackend == null ? "auto" : uiBackend;
    }

    public String appType() {
        return appType;
    }

    public void setAppType(String appType) {
        this.appType = appType == null ? "console" : appType;
    }

    public int optLevel() {
        return optLevel;
    }

    public void setOptLevel(int optLevel) {
        this.optLevel = Math.max(0, Math.min(3, optLevel));
    }

    public boolean emitIrBeforeOpt() {
        return emitIrBeforeOpt;
    }

    public void setEmitIrBeforeOpt(boolean emitIrBeforeOpt) {
        this.emitIrBeforeOpt = emitIrBeforeOpt;
    }

    public boolean emitIrAfterOpt() {
        return emitIrAfterOpt;
    }

    public void setEmitIrAfterOpt(boolean emitIrAfterOpt) {
        this.emitIrAfterOpt = emitIrAfterOpt;
    }

    public boolean optReport() {
        return optReport;
    }

    public void setOptReport(boolean optReport) {
        this.optReport = optReport;
    }

    public boolean emitEscapeAnalysis() {
        return emitEscapeAnalysis;
    }

    public void setEmitEscapeAnalysis(boolean emitEscapeAnalysis) {
        this.emitEscapeAnalysis = emitEscapeAnalysis;
    }

    public boolean explainOpt() {
        return explainOpt;
    }

    public void setExplainOpt(boolean explainOpt) {
        this.explainOpt = explainOpt;
    }

    public boolean verifyIr() {
        return verifyIr;
    }

    public void setVerifyIr(boolean verifyIr) {
        this.verifyIr = verifyIr;
    }

    public boolean emitOnly() {
        return emitAst || emitIr || emitLlvm || emitLayout || emitEffects || emitAsync || emitGenerics
                || explainGenerics || emitIrBeforeOpt || emitIrAfterOpt || optReport
                || emitEscapeAnalysis;
    }
}
