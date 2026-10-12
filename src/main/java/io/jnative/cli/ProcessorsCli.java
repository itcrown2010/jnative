/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.cli;

import io.jnative.meta.MetaApiVersion;
import io.jnative.processor.AnnotationProcessor;
import io.jnative.processor.ProcessorPermission;
import io.jnative.processor.ProcessorRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

/** CLI: jnative processors | processors explain | generated
 * @author 张晓达
 */
public final class ProcessorsCli {

    private ProcessorsCli() {
    }

    public static int dispatch(String[] args) throws Exception {
        if (args.length == 1 || "list".equals(args[1])) {
            return list();
        }
        if ("explain".equals(args[1])) {
            if (args.length < 3) {
                System.err.println("usage: jnative processors explain <TypeName>");
                return 1;
            }
            return explain(args[2]);
        }
        System.err.println("usage: jnative processors [list|explain <Type>]");
        return 1;
    }

    public static int generated(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: jnative generated <SymbolOrPath>");
            return 1;
        }
        String q = args[1];
        Path root = Path.of(".jnative/generated").toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) {
            System.err.println("no generated directory: " + root);
            return 1;
        }
        Path index = root.resolve("generated-index.tsv");
        if (Files.isRegularFile(index)) {
            for (String line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
                if (line.contains(q)) {
                    System.out.println(line);
                }
            }
        }
        try (var walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().contains(q)
                            || p.toString().contains(q))
                    .forEach(p -> {
                        try {
                            String rel = root.relativize(p).toString();
                            System.out.println("file: " + rel);
                            String text = Files.readString(p, StandardCharsets.UTF_8);
                            text.lines().limit(8).forEach(System.out::println);
                            System.out.println("---");
                        } catch (Exception e) {
                            System.err.println(e.getMessage());
                        }
                    });
        }
        return 0;
    }

    private static int list() {
        System.out.println("JNative Compiler Meta API " + MetaApiVersion.CURRENT);
        System.out.println("Metaprogramming LLVM intrinsics: NONE");
        System.out.println("Annotation Processing LLVM intrinsics: NONE");
        System.out.println("Compile-Time DI LLVM intrinsics: NONE");
        System.out.println();
        for (AnnotationProcessor p : ProcessorRegistry.builtin()) {
            String perms = p.permissions().stream()
                    .map(ProcessorPermission::name)
                    .collect(Collectors.joining(","));
            System.out.println(p.name()
                    + "\tversion=" + p.version()
                    + "\tkind=" + p.kind()
                    + "\tapi=" + p.minProcessorApi()
                    + (p.maxProcessorApi() > 0 ? ".." + p.maxProcessorApi() : "+")
                    + "\tpermissions=" + perms
                    + "\tannotations=" + p.supportedAnnotations());
        }
        return 0;
    }

    private static int explain(String typeName) throws Exception {
        Path root = Path.of(".jnative/generated").toAbsolutePath().normalize();
        Path index = root.resolve("generated-index.tsv");
        System.out.println(typeName);
        System.out.println();
        if (!Files.isRegularFile(index)) {
            System.out.println("(no generated-index.tsv — build a unit that triggers processors)");
            // Still show which processors *would* care
            for (AnnotationProcessor p : ProcessorRegistry.builtin()) {
                System.out.println(p.name() + " may generate artifacts for @"
                        + p.supportedAnnotations());
            }
            return 0;
        }
        List<String> lines = Files.readAllLines(index, StandardCharsets.UTF_8);
        boolean any = false;
        for (String line : lines) {
            if (line.contains(typeName)) {
                any = true;
                String[] cols = line.split("\t");
                if (cols.length >= 3) {
                    System.out.println(cols[1]);
                    System.out.println("  generated " + cols[0]);
                    System.out.println("  origin " + cols[2]);
                    if (cols.length > 3) {
                        System.out.println("  fingerprint " + cols[3]);
                    }
                    System.out.println();
                } else {
                    System.out.println(line);
                }
            }
        }
        if (!any) {
            System.out.println("(no generated artifacts mentioning " + typeName + ")");
        }
        return 0;
    }
}
