/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ir;

import io.jnative.diagnostics.SourceLocation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps line numbers in a concatenated (or single) user source buffer onto original files.
 * Format (v1 text sidecar):
 * <pre>
 * # JNative source line map v1
 * # bufferStart bufferEnd absPath fileStartLine
 * 1 20 /abs/MathUtil.java 1
 * 21 40 /abs/Main.java 1
 * </pre>
 * Ranges are inclusive, 1-based, relative to the user buffer (before stdlib prepend).
 * @author 张晓达
 */
public final class SourceLineMap {
    public record Segment(int bufferStart, int bufferEnd, String absPath, int fileStartLine) {
    }

    private final List<Segment> segments;

    public SourceLineMap(List<Segment> segments) {
        this.segments = List.copyOf(segments);
    }

    public static SourceLineMap singleFile(String absPath, int lineCount) {
        int end = Math.max(1, lineCount);
        return new SourceLineMap(List.of(new Segment(1, end, absPath, 1)));
    }

    public static SourceLineMap read(Path path) throws IOException {
        List<Segment> segs = new ArrayList<>();
        for (String raw : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split("\\s+", 4);
            if (parts.length < 4) {
                throw new IOException("bad line map entry: " + raw);
            }
            segs.add(new Segment(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    parts[2],
                    Integer.parseInt(parts[3])
            ));
        }
        if (segs.isEmpty()) {
            throw new IOException("empty line map: " + path);
        }
        return new SourceLineMap(segs);
    }

    public void write(Path path) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# JNative source line map v1\n");
        sb.append("# bufferStart bufferEnd absPath fileStartLine\n");
        for (Segment s : segments) {
            sb.append(s.bufferStart()).append(' ')
                    .append(s.bufferEnd()).append(' ')
                    .append(s.absPath()).append(' ')
                    .append(s.fileStartLine()).append('\n');
        }
        Files.createDirectories(path.getParent());
        Files.writeString(path, sb.toString(), StandardCharsets.UTF_8);
    }

    public List<Segment> segments() {
        return segments;
    }

    /**
     * Map a 1-based line in the user buffer (no stdlib) to an original source location.
     */
    public SourceLocation mapUserLine(int userLine, int column) {
        if (userLine <= 0) {
            return SourceLocation.UNKNOWN;
        }
        for (Segment s : segments) {
            if (userLine >= s.bufferStart() && userLine <= s.bufferEnd()) {
                int fileLine = s.fileStartLine() + (userLine - s.bufferStart());
                return new SourceLocation(s.absPath(), fileLine, column);
            }
        }
        // Do not invent a location in the last file — that produced ErpServer.java:20000+
        // when stdlib offsets were not subtracted. Callers treat UNKNOWN as "no DI loc".
        return SourceLocation.UNKNOWN;
    }

    /** Primary (last) file — typically the entry / main source. */
    public String primaryFile() {
        return segments.get(segments.size() - 1).absPath();
    }
}
