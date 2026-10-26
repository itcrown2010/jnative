/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** {@code jnative analyze --project}: whole-project frontend check with unsaved-buffer overlays (Studio live check). */
class AnalyzeProjectCliTest {

    @TempDir
    Path tempDir;

    private String run(int[] exit, String... args) throws Exception {
        PrintStream oldOut = System.out;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buf, true, "UTF-8"));
        try {
            exit[0] = AnalyzeCli.dispatch(args);
        } finally {
            System.setOut(oldOut);
        }
        return buf.toString("UTF-8");
    }

    @Test
    void projectCheckResolvesAcrossFilesAndHonoursOverlays() throws Exception {
        Path src = tempDir.resolve("src");
        Files.createDirectories(src);
        Files.writeString(tempDir.resolve("jnative.project"), """
                {"name":"p","modules":{"app":{"kind":"app","src":"src"}},
                 "targets":[{"name":"p","triple":"native","module":"app","main":"Main","output":".jnative/build/p"}]}
                """);
        Files.writeString(src.resolve("Base.java"), "public class Base {\n    int value() { return 1; }\n}\n");
        Files.writeString(src.resolve("Main.java"), """
                public class Main extends Base {
                    public static void main(String[] args) {
                        System.out.println(new Main().value());
                    }
                }
                """);
        int[] exit = new int[1];
        String ok = run(exit, "analyze", "--project", tempDir.toString(), "--format", "text");
        assertEquals(0, exit[0], ok);
        assertEquals("", ok.trim());

        Path overlay = tempDir.resolve("unsaved.txt");
        Files.writeString(overlay, """
                public class Main extends Base {
                    public static void main(String[] args) {
                        int x = "s";
                        missing();
                    }
                }
                """);
        String bad = run(exit, "analyze", "--project", tempDir.toString(),
                "--overlay", src.resolve("Main.java") + "=" + overlay, "--format", "text");
        assertEquals(1, exit[0], bad);
        assertTrue(bad.contains("Main.java:3:13: error: incompatible types"), bad);
        assertTrue(bad.contains("Main.java:4:9: error: undefined method 'missing'"), bad);
        assertFalse(bad.contains("duplicate class"), bad);
    }
}
