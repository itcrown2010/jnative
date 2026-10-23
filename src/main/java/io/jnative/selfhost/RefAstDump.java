/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.selfhost;

import io.jnative.ast.AstPrinter;
import io.jnative.ast.CompilationUnit;
import io.jnative.compiler.MultiUnitFrontend;
import io.jnative.compiler.SourceUnit;
import io.jnative.diagnostics.DiagnosticReporter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Dump reference AST via {@link AstPrinter} for self-host parser compare.
 * @author 张晓达
 */
public final class RefAstDump {
    private RefAstDump() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: RefAstDump <source-file>");
            System.exit(2);
        }
        Path path = Path.of(args[0]);
        String src = Files.readString(path, StandardCharsets.UTF_8);
        DiagnosticReporter reporter = new DiagnosticReporter();
        CompilationUnit cu = MultiUnitFrontend.parseUnit(new SourceUnit(path.toString(), src), reporter);
        System.out.print(AstPrinter.print(cu));
        if (reporter.hasErrors()) {
            System.err.println("REF_AST_ERRORS");
            reporter.diagnostics().forEach(d -> System.err.println(d));
            System.exit(1);
        }
    }
}
