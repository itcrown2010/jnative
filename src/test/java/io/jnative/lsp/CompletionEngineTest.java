package io.jnative.lsp;

import io.jnative.compiler.JNativeCompiler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class CompletionEngineTest {

    private final CompletionEngine engine = new CompletionEngine(new JNativeCompiler());

    @Test
    void completesLocalVariableWithoutSave() {
        String src = """
                public class Main {
                    public static void main(String[] args) {
                        int answer = 42;
                        an
                    }
                }
                """;
        // cursor after "an"
        int line = 3;
        int col = 26; // approximate end of "an" on that line — compute from text
        int offset = src.indexOf("an\n") + 2;
        int[] lc = CompletionEngine.lineColOf(src, offset);
        List<CompletionEngine.Item> items = engine.complete("Main.java", src, lc[0] - 1, lc[1] - 1);
        assertTrue(items.stream().anyMatch(i -> "answer".equals(i.label())),
                "expected local 'answer' in " + items);
    }

    @Test
    void completesMembersAfterDotOnIncompleteBuffer() {
        String src = """
                class Box {
                    int value;
                    void bump() {}
                }
                public class Main {
                    public static void main(String[] args) {
                        Box b = new Box();
                        b.
                    }
                }
                """;
        int offset = src.indexOf("b.") + 2;
        int[] lc = CompletionEngine.lineColOf(src, offset);
        List<CompletionEngine.Item> items = engine.complete("Main.java", src, lc[0] - 1, lc[1] - 1);
        assertTrue(items.stream().anyMatch(i -> "value".equals(i.label())),
                "expected field value, got " + items);
        assertTrue(items.stream().anyMatch(i -> "bump".equals(i.label())),
                "expected method bump, got " + items);
    }

    @Test
    void patchBalancedClosesParen() {
        assertEquals("foo();", CompletionEngine.patchBalanced("foo("));
    }

    @Test
    void completesLocalsWithCrlfAndChinesePath() {
        // CRLF buffers + path with spaces/CJK must still analyze and complete ASCII locals.
        String src = "public class Main {\r\n"
                + "    public static void main(String[] args) {\r\n"
                + "        int answer = 1;\r\n"
                + "        an\r\n"
                + "    }\r\n"
                + "}\r\n";
        int offset = src.lastIndexOf("an\r\n") + 2;
        int[] lc = CompletionEngine.lineColOf(src, offset);
        List<CompletionEngine.Item> items = engine.complete(
                "/tmp/中文 目录/Main.java", src, lc[0] - 1, lc[1] - 1);
        assertTrue(items.stream().anyMatch(i -> "answer".equals(i.label())),
                "expected answer with CRLF/CJK path, got " + items);
    }

    @Test
    void signatureHelpActiveParameter() {
        String src = """
                public class Main {
                    static int add(int a, int b) { return a + b; }
                    public static void main(String[] args) {
                        add(1,
                    }
                }
                """;
        int offset = src.indexOf("add(1,") + "add(1,".length();
        int[] lc = CompletionEngine.lineColOf(src, offset);
        CompletionEngine.Signature sig = engine.signatureHelp("Main.java", src, lc[0] - 1, lc[1] - 1);
        assertNotNull(sig);
        assertEquals(1, sig.activeParameter());
        assertTrue(sig.label().contains("add"));
    }
}
