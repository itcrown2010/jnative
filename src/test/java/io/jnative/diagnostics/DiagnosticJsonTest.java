/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.diagnostics;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class DiagnosticJsonTest {

    @Test
    void remapsUserLinesAfterStdlibPrefix() {
        String user = "class Main {\n  void x() {\n    int y = ;\n  }\n}\n";
        String prepended = "class Std {\n}\n\n" + user;
        int offset = DiagnosticJson.userLineOffset(prepended, user);
        assertTrue(offset > 0);
        List<Diagnostic> remapped = DiagnosticJson.remapToUserSource(
                List.of(Diagnostic.error(new SourceLocation("Main.java", offset + 3, 13), "expected expression")),
                "/proj/Main.java",
                offset);
        assertEquals(1, remapped.size());
        assertEquals("/proj/Main.java", remapped.get(0).location().file());
        assertEquals(3, remapped.get(0).location().line());
    }

    @Test
    void stdlibRegionMarked() {
        List<Diagnostic> remapped = DiagnosticJson.remapToUserSource(
                List.of(Diagnostic.error(new SourceLocation("Main.java", 1, 1), "boom")),
                "/proj/Main.java",
                10);
        assertEquals("<stdlib>", remapped.get(0).location().file());
        assertTrue(remapped.get(0).message().startsWith("[stdlib]"));
    }

    @Test
    void jsonReportShape() {
        String json = DiagnosticJson.buildReportJson(true, "/tmp/a", List.of());
        assertTrue(json.contains("\"success\":true"));
        assertTrue(json.contains("\"artifact\":\"/tmp/a\""));
        assertTrue(json.contains("\"diagnostics\":[]"));
    }
}
