package io.jnative.lsp;

import io.jnative.compiler.JNativeCompiler;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class RenameServiceTest {

    private final RenameService renamer = new RenameService(new IdeServices(new JNativeCompiler()));

    @Test
    void renamesLocalVariableUsesOnly() {
        String src = """
                public class Main {
                    public static void main(String[] args) {
                        int answer = 42;
                        int x = answer;
                        String s = "answer";
                        // answer in comment
                    }
                }
                """;
        int idx = src.indexOf("= answer") + 2;
        int[] lc = CompletionEngine.lineColOf(src, idx);
        Map<String, String> ov = Map.of("Main.java", src);
        var prep = renamer.prepare("Main.java", src, lc[0] - 1, lc[1] - 1, 1, ov);
        assertTrue(prep.ok(), prep.error());
        var result = renamer.rename("Main.java", src, lc[0] - 1, lc[1] - 1, "result", 1, ov);
        assertTrue(result.ok(), result.error());
        assertEquals(2, result.edits().size(), result.edits().toString());
        // Apply edits bottom-up already sorted
        String out = apply(src, result);
        assertTrue(out.contains("int result = 42"));
        assertTrue(out.contains("int x = result"));
        assertTrue(out.contains("\"answer\""));
        assertTrue(out.contains("// answer in comment"));
    }

    @Test
    void rejectsConflictAndBadName() {
        String src = """
                public class Main {
                    public static void main(String[] args) {
                        int a = 1;
                        int b = 2;
                        int x = a;
                    }
                }
                """;
        int idx = src.indexOf("= a;") + 2;
        int[] lc = CompletionEngine.lineColOf(src, idx);
        var conflict = renamer.rename("Main.java", src, lc[0] - 1, lc[1] - 1, "b", 1, Map.of());
        assertFalse(conflict.ok());
        var bad = renamer.rename("Main.java", src, lc[0] - 1, lc[1] - 1, "1x", 1, Map.of());
        assertFalse(bad.ok());
    }

    @Test
    void renamesStaticMethod() {
        String src = """
                public class Main {
                    static int add(int a, int b) { return a + b; }
                    public static void main(String[] args) {
                        int y = add(1, 2);
                    }
                }
                """;
        int idx = src.indexOf("= add(") + 2;
        int[] lc = CompletionEngine.lineColOf(src, idx);
        var result = renamer.rename("Main.java", src, lc[0] - 1, lc[1] - 1, "sum", 1,
                Map.of("Main.java", src));
        assertTrue(result.ok(), result.error());
        String out = apply(src, result);
        assertTrue(out.contains("static int sum("));
        assertTrue(out.contains("= sum(1, 2)"));
    }

    private static String apply(String src, RenameService.RenameResult result) {
        String text = src.replace("\r\n", "\n");
        String[] lines = text.split("\n", -1);
        for (RenameService.Edit e : result.edits()) {
            String line = lines[e.startLine0()];
            lines[e.startLine0()] = line.substring(0, e.startCol0())
                    + e.newText()
                    + line.substring(e.endCol0());
        }
        return String.join("\n", lines);
    }
}
