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
    private boolean keepTemps;
    private boolean help;
    private boolean version;
    private final List<Path> libraryPaths = new ArrayList<>();
    private final List<String> linkLibraries = new ArrayList<>();

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

    public boolean emitOnly() {
        return emitAst || emitIr || emitLlvm || emitLayout;
    }
}
