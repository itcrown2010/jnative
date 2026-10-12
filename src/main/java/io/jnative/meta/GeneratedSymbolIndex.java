/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.meta;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Studio / IDE foundation: map generated symbols ↔ origin symbols via generated-index.tsv.
 * @author 张晓达
 */
public final class GeneratedSymbolIndex {
    public record Entry(
            String relativePath,
            String processor,
            String originSource,
            String originSymbol,
            String fingerprint,
            String outputHash
    ) {
    }

    private final List<Entry> entries;

    public GeneratedSymbolIndex(List<Entry> entries) {
        this.entries = entries == null ? List.of() : List.copyOf(entries);
    }

    public static GeneratedSymbolIndex load(Path generatedRoot) throws IOException {
        Path index = generatedRoot.resolve("generated-index.tsv");
        if (!Files.isRegularFile(index)) {
            return new GeneratedSymbolIndex(List.of());
        }
        List<Entry> out = new ArrayList<>();
        for (String line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
            if (line.startsWith("path\t") || line.isBlank()) {
                continue;
            }
            String[] c = line.split("\t");
            if (c.length < 4) {
                continue;
            }
            String origin = c[2];
            String originSource = origin;
            String originSymbol = "";
            int colon = origin.lastIndexOf(':');
            if (colon >= 0) {
                originSource = origin.substring(0, colon);
                originSymbol = origin.substring(colon + 1);
            }
            out.add(new Entry(
                    c[0],
                    c[1],
                    originSource,
                    originSymbol,
                    c[3],
                    c.length > 4 ? c[4] : ""));
        }
        return new GeneratedSymbolIndex(out);
    }

    public List<Entry> entries() {
        return entries;
    }

    /** Go to generated: origin type → generated paths. */
    public List<Entry> generatedFrom(String originSymbol) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.originSymbol().equals(originSymbol)
                    || e.originSymbol().endsWith("." + originSymbol)
                    || e.relativePath().contains(originSymbol)) {
                out.add(e);
            }
        }
        return out;
    }

    /** Go to origin: generated path/symbol → origin. */
    public List<Entry> originOf(String generatedHint) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) {
            if (e.relativePath().contains(generatedHint)
                    || e.relativePath().endsWith(generatedHint + ".java")) {
                out.add(e);
            }
        }
        return out;
    }
}
