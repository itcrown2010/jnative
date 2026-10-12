/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.processor;

/**
 * @author 张晓达
 */
public final class SourceFileBuilder {
    private final CodeGenerator generator;
    private final String relativePath;
    private final StringBuilder body = new StringBuilder();
    private String originSource = "";
    private String originSymbol = "";
    private String fingerprint = "";

    SourceFileBuilder(CodeGenerator generator, String relativePath) {
        this.generator = generator;
        this.relativePath = relativePath;
    }

    public SourceFileBuilder origin(String source, String symbol) {
        this.originSource = source;
        this.originSymbol = symbol;
        return this;
    }

    public SourceFileBuilder fingerprint(String fp) {
        this.fingerprint = fp;
        return this;
    }

    public SourceFileBuilder append(String text) {
        body.append(text);
        return this;
    }

    public SourceFileBuilder line(String text) {
        body.append(text).append('\n');
        return this;
    }

    public void finish() {
        generator.createSourceFile(relativePath, body.toString(), originSource, originSymbol, fingerprint);
    }
}
