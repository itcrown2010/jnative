package io.jnative.ir;

import io.jnative.diagnostics.DiagnosticJson;
import io.jnative.diagnostics.SourceLocation;

/**
 * Maps locations from a stdlib-prepended parse buffer back onto original user source files.
 * Locations that fall inside the prepended prefix become {@link SourceLocation#UNKNOWN}
 * so they are not attributed to user code in debug info.
 * @author 张晓达
 */
public final class SourceLocationMapper {
    private final String userFile;
    private final int lineOffset;
    private final SourceLineMap lineMap;

    public SourceLocationMapper(String userFile, int lineOffset) {
        this(userFile, lineOffset, null);
    }

    public SourceLocationMapper(String userFile, int lineOffset, SourceLineMap lineMap) {
        this.userFile = userFile == null ? "" : userFile;
        this.lineOffset = Math.max(0, lineOffset);
        this.lineMap = lineMap;
    }

    public static SourceLocationMapper identity() {
        return new SourceLocationMapper("", 0, null);
    }

    public static SourceLocationMapper forPrepended(String userFileAbs, String prepended, String userSource) {
        int offset = DiagnosticJson.userLineOffset(prepended, userSource);
        return new SourceLocationMapper(userFileAbs, offset, null);
    }

    public static SourceLocationMapper forPrepended(
            String userFileAbs,
            String prepended,
            String userSource,
            SourceLineMap lineMap
    ) {
        int offset = DiagnosticJson.userLineOffset(prepended, userSource);
        return new SourceLocationMapper(userFileAbs, offset, lineMap);
    }

    public String userFile() {
        if (lineMap != null) {
            return lineMap.primaryFile();
        }
        return userFile;
    }

    public int lineOffset() {
        return lineOffset;
    }

    public SourceLineMap lineMap() {
        return lineMap;
    }

    public SourceLocation map(SourceLocation loc) {
        if (loc == null || loc == SourceLocation.UNKNOWN) {
            return SourceLocation.UNKNOWN;
        }
        if (lineOffset > 0 && loc.line() > 0 && loc.line() <= lineOffset) {
            return SourceLocation.UNKNOWN;
        }
        int userLine = lineOffset > 0 && loc.line() > 0 ? loc.line() - lineOffset : loc.line();
        if (lineMap != null) {
            return lineMap.mapUserLine(userLine, loc.column());
        }
        String file = !userFile.isEmpty() ? userFile : loc.file();
        return new SourceLocation(file, userLine, loc.column());
    }
}
