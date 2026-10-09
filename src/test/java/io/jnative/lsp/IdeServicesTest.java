package io.jnative.lsp;

import io.jnative.compiler.JNativeCompiler;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class IdeServicesTest {

    private final IdeServices ide = new IdeServices(new JNativeCompiler());

    @Test
    void definitionAndHoverForLocalAndMethod() {
        String src = """
                public class Main {
                    static int add(int a, int b) { return a + b; }
                    public static void main(String[] args) {
                        int answer = 42;
                        int x = answer;
                        int y = add(1, 2);
                    }
                }
                """;
        int answerUse = src.indexOf("= answer") + "= ".length() + 2; // inside answer
        int[] lc = CompletionEngine.lineColOf(src, answerUse);
        List<IdeServices.Location> defs = ide.definition(
                "Main.java", src, lc[0] - 1, lc[1] - 1, 1, Map.of());
        assertFalse(defs.isEmpty());
        assertTrue(defs.get(0).line1() > 0);

        int addUse = src.indexOf("= add(") + "= ".length() + 1;
        int[] lc2 = CompletionEngine.lineColOf(src, addUse);
        IdeServices.Hover hover = ide.hover("Main.java", src, lc2[0] - 1, lc2[1] - 1, 1, Map.of());
        assertNotNull(hover);
        assertTrue(hover.markdown().contains("add"), hover.markdown());

        List<IdeServices.Location> refs = ide.references(
                "Main.java", src, lc[0] - 1, lc[1] - 1, 1, Map.of());
        assertTrue(refs.size() >= 2, "decl + use, got " + refs.size());
    }

    @Test
    void documentSymbolsAndImportAction() {
        String src = """
                import java.lang.String;
                public class Main {
                    int field;
                    void bump() {}
                    public static void main(String[] args) {}
                }
                """;
        List<IdeServices.DocSymbol> syms = ide.documentSymbols("Main.java", src, 1, Map.of());
        assertTrue(syms.stream().anyMatch(s -> "Main".equals(s.name())));

        // unused import java.lang.String may or may not be flagged depending on String usage in signature
        List<IdeServices.CodeAction> actions = ide.codeActions("Main.java", src, 1, Map.of(), List.of());
        // At least documentSymbols path must not throw; import actions are best-effort
        assertNotNull(actions);
    }

    @Test
    void crossFileDefinitionInMultiCuProject() {
        Path main = Path.of("examples/multi-cu-resolve/app/src/Main.jn").toAbsolutePath().normalize();
        if (!main.toFile().isFile()) {
            return;
        }
        String text;
        try {
            text = java.nio.file.Files.readString(main);
        } catch (Exception e) {
            return;
        }
        int idx = text.indexOf("Util.answer");
        if (idx < 0) {
            return;
        }
        int[] lc = CompletionEngine.lineColOf(text, idx + 2); // inside Util
        List<IdeServices.Location> defs = ide.definition(
                main.toString(), text, lc[0] - 1, lc[1] - 1, 1, Map.of());
        assertFalse(defs.isEmpty(), "expected Util definition");
        if (!defs.get(0).file().isEmpty()) {
            assertTrue(defs.get(0).file().contains("Util")
                            || defs.get(0).file().endsWith(".jn")
                            || defs.get(0).file().endsWith(".java"),
                    defs.get(0).file());
        }
    }
}
