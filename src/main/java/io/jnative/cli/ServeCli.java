/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.cli;

import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.diagnostics.Diagnostic;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * `jnative serve` — compile a project entry and run the native binary (0.31).
 * @author 张晓达
 */
public final class ServeCli {

    private ServeCli() {
    }

    public static int dispatch(String[] args) throws Exception {
        if (args.length < 2) {
            printUsage();
            return 1;
        }

        Path source = null;
        Integer port = null;
        Path tlsCert = null;
        Path tlsKey = null;
        List<String> programArgs = new ArrayList<>();

        for (int i = 1; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--port" -> {
                    if (i + 1 >= args.length) {
                        System.err.println("error: missing value after --port");
                        return 1;
                    }
                    port = Integer.parseInt(args[++i]);
                }
                case "--tls" -> {
                    if (i + 2 >= args.length) {
                        System.err.println("error: --tls requires <cert.crt> <key.key>");
                        return 1;
                    }
                    tlsCert = Path.of(args[++i]).toAbsolutePath().normalize();
                    tlsKey = Path.of(args[++i]).toAbsolutePath().normalize();
                }
                case "-h", "--help" -> {
                    printUsage();
                    return 0;
                }
                default -> {
                    if (arg.startsWith("-")) {
                        System.err.println("error: unknown option: " + arg);
                        printUsage();
                        return 1;
                    }
                    if (source == null && io.jnative.source.SourceFiles.isSourceFileName(arg)) {
                        source = Path.of(arg).toAbsolutePath().normalize();
                    } else {
                        programArgs.add(arg);
                    }
                }
            }
        }

        if (source == null) {
            System.err.println("error: missing entry source file (e.g. App.jn or App.java)");
            printUsage();
            return 1;
        }

        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(source);
        options.setOptLevel(1);

        System.out.println("[serve] compiling " + source + " ...");
        CompileResult result = new JNativeCompiler().compile(options);
        for (Diagnostic d : result.diagnostics()) {
            System.err.println(d);
        }
        if (!result.success() || result.executable() == null) {
            System.err.println("[serve] compile failed");
            return 1;
        }

        Path binary = result.executable();
        List<String> cmd = new ArrayList<>();
        cmd.add(binary.toString());
        cmd.addAll(programArgs);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.inheritIO();
        if (port != null) {
            pb.environment().put("JNATIVE_SERVE_PORT", Integer.toString(port));
            pb.environment().put("PORT", Integer.toString(port));
        }
        if (tlsCert != null && tlsKey != null) {
            pb.environment().put("JNATIVE_TLS_CERT", tlsCert.toString());
            pb.environment().put("JNATIVE_TLS_KEY", tlsKey.toString());
        }

        System.out.println("[serve] running " + binary);
        if (port != null) {
            System.out.println("[serve] port=" + port + " (JNATIVE_SERVE_PORT / PORT)");
        }
        if (tlsCert != null) {
            System.out.println("[serve] tls cert=" + tlsCert + " key=" + tlsKey);
        }
        Process process = pb.start();
        return process.waitFor();
    }

    private static void printUsage() {
        System.out.println("""
                jnative serve — compile and run a native server entry (0.31)

                Usage:
                  jnative serve <Entry.java> [--port N] [--tls <cert.crt> <key.key>] [-- <args...>]

                Compiles the entry with JNativeCompiler, then execs the native binary.
                Environment passed to the process:
                  JNATIVE_SERVE_PORT / PORT     when --port is set
                  JNATIVE_TLS_CERT / JNATIVE_TLS_KEY   when --tls is set

                Generate dev certs: jnative cert dev [--out DIR]
                """);
    }
}
