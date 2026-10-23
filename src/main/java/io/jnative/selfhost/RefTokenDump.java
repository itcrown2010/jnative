/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.selfhost;

import io.jnative.lexer.Lexer;
import io.jnative.lexer.Token;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Dump reference lexer tokens in the canonical self-host compare format.
 * Format per line: TYPE\tline\tcolumn\tlexeme (escapes: \\ \\n \\t \\r)
 * @author 张晓达
 */
public final class RefTokenDump {
    private RefTokenDump() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: RefTokenDump <source-file>");
            System.exit(2);
        }
        Path path = Path.of(args[0]);
        String src = Files.readString(path, StandardCharsets.UTF_8);
        Lexer lexer = new Lexer(src, path.getFileName().toString());
        List<Token> tokens = lexer.tokenize();
        for (Token t : tokens) {
            System.out.println(t.type().name() + "\t" + t.line() + "\t" + t.column() + "\t" + escape(t.lexeme()));
        }
    }

    static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\t' -> sb.append("\\t");
                case '\r' -> sb.append("\\r");
                case '\0' -> sb.append("\\0");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
