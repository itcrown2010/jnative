/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.SourceUnit;
import io.jnative.diagnostics.Diagnostic;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** JNative 0.20 Text Engine / CodeEditor — headless e2e.
 * @author 张晓达
 */
class EditorEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void pieceTableInsertDeleteNoFullCopy() throws Exception {
        assertEquals("ok\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        TextDocument doc = new TextDocument("abcd");
                        int pieces0 = doc.getPieceCount();
                        doc.insert(2, "XY");
                        doc.delete(0, 1);
                        int ok = 0;
                        if (doc.getLength() == 5 && doc.charAt(0) == 98 && doc.getPieceCount() >= pieces0) {
                            ok = 1;
                        }
                        if (ok == 1) {
                            System.out.println("ok");
                        } else {
                            System.out.println("fail");
                        }
                    }
                }
                """, "EdPiece", Map.of(), 0));
    }

    @Test
    void offsetLineMapping() throws Exception {
        assertEquals("1\n5\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        TextDocument doc = new TextDocument("aa");
                        doc.insert(2, "\\n");
                        doc.insert(3, "bbb");
                        TextPosition p = doc.offsetToPosition(4);
                        System.out.println(p.getLine());
                        System.out.println(doc.positionToOffset(1, 2));
                    }
                }
                """, "EdMap", Map.of(), 0));
    }

    @Test
    void crlfNormalized() throws Exception {
        assertEquals("2\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        TextDocument doc = new TextDocument("a\\r\\nb");
                        System.out.println(doc.getLineCount());
                    }
                }
                """, "EdCrlf", Map.of(), 0));
    }

    @Test
    void undoRedo() throws Exception {
        assertEquals("ok\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        TextDocument doc = new TextDocument("hi");
                        UndoManager u = new UndoManager(doc);
                        u.recordReplace(2, "", "!");
                        doc.insert(2, "!");
                        u.undo();
                        int ok = 0;
                        if (doc.getLength() == 2) {
                            ok = 1;
                        }
                        u.redo();
                        if (doc.getLength() == 3 && ok == 1) {
                            System.out.println("ok");
                        } else {
                            System.out.println("fail");
                        }
                    }
                }
                """, "EdUndo", Map.of(), 0));
    }

    @Test
    void replaceAllOneTransaction() throws Exception {
        assertEquals("2\nok\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        TextDocument doc = new TextDocument("aa aa");
                        UndoManager u = new UndoManager(doc);
                        int n = TextSearch.replaceAll(doc, u, "aa", "b", 1);
                        System.out.println(n);
                        u.undo();
                        if (doc.getText().equals("aa aa")) {
                            System.out.println("ok");
                        } else {
                            System.out.println("fail");
                        }
                    }
                }
                """, "EdRepl", Map.of(), 0));
    }

    @Test
    void tokenizerKeywordsAndComment() throws Exception {
        assertEquals("1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        TextDocument doc = new TextDocument("public class A {");
                        doc.insert(doc.getLength(), " ");
                        doc.insert(doc.getLength(), "/");
                        doc.insert(doc.getLength(), "/");
                        doc.insert(doc.getLength(), " c");
                        ArrayList<StyledSpan> spans = JNativeSyntaxTokenizer.highlightDocument(doc);
                        int foundKw = 0;
                        int foundCmt = 0;
                        int i = 0;
                        while (i < spans.size()) {
                            StyledSpan s = spans.get(i);
                            if (s.tokenType == TokenType.KEYWORD) {
                                foundKw = 1;
                            }
                            if (s.tokenType == TokenType.COMMENT) {
                                foundCmt = 1;
                            }
                            i = i + 1;
                        }
                        if (foundKw == 1 && foundCmt == 1) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "EdTok", Map.of(), 0));
    }

    @Test
    void multilineCommentState() throws Exception {
        assertEquals("1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        TextDocument doc = new TextDocument("a");
                        doc.insert(1, "\\n");
                        doc.setText("/*\\nx\\n*/ y");
                        ArrayList<StyledSpan> spans = JNativeSyntaxTokenizer.highlightDocument(doc);
                        int comment = 0;
                        int i = 0;
                        while (i < spans.size()) {
                            if (spans.get(i).tokenType == TokenType.COMMENT) {
                                comment = 1;
                            }
                            i = i + 1;
                        }
                        System.out.println(comment);
                    }
                }
                """, "EdBlk", Map.of(), 0));
    }

    @Test
    void clipboardRoundTrip() throws Exception {
        assertEquals("hello\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        Clipboard.setText("hello");
                        System.out.println(Clipboard.getText());
                    }
                }
                """, "EdClip", Map.of("JNATIVE_HEADLESS", "1"), 0));
    }

    @Test
    void documentVersionAndSnapshot() throws Exception {
        assertEquals("1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        TextDocument doc = new TextDocument("x");
                        int v0 = doc.getVersion();
                        TextSnapshot snap = doc.snapshot();
                        doc.insert(1, "y");
                        int ok = 0;
                        if (snap.getVersion() == v0 && doc.getVersion() == v0 + 1) {
                            if (snap.getText().equals("x")) {
                                ok = 1;
                            }
                        }
                        System.out.println(ok);
                    }
                }
                """, "EdSnap", Map.of(), 0));
    }

    @Test
    void unicodeDocument() throws Exception {
        assertEquals("ok\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        TextDocument doc = new TextDocument("Hello");
                        doc.insert(doc.getLength(), " 你好");
                        doc.insert(doc.getLength(), " こんにちは");
                        int n = doc.getLength();
                        if (n > 10) {
                            System.out.println("ok");
                        } else {
                            System.out.println("fail");
                        }
                    }
                }
                """, "EdUni", Map.of(), 0));
    }

    @Test
    void codeEditorHeadlessType() throws Exception {
        assertEquals("1\n", run("""
                class Holder {
                    static CodeEditor editor;
                }
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("Ed", 640, 400);
                        Holder.editor = new CodeEditor();
                        Holder.editor.setText("int x = 1;");
                        window.setContent(Holder.editor);
                        window.show();
                        UiTest.keyPress(Holder.editor, 65);
                        UiTest.paint(Holder.editor);
                        int ok = 0;
                        if (Holder.editor.getDocument().getLength() >= 10) {
                            ok = 1;
                        }
                        System.out.println(ok);
                        Holder.editor.disposeEditor();
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "EdUi", Map.of("JNATIVE_HEADLESS", "1"), 0));
    }

    @Test
    void sourceUnitCompilerApi() {
        SourceUnit unit = new SourceUnit("Mem.java", """
                public class Main {
                    public static void main(String[] args) {
                        System.out.println(1);
                    }
                }
                """);
        CompilerOptions options = new CompilerOptions();
        options.setEmitIr(true);
        options.setOptLevel(0);
        options.setHeadless(true);
        CompileResult result = new JNativeCompiler().compile(unit, options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
    }

    @Test
    void compilerDiagnosticsMap() {
        SourceUnit unit = new SourceUnit("Bad.java", """
                public class Main {
                    public static void main(String[] args) {
                        int x =
                    }
                }
                """);
        CompilerOptions options = new CompilerOptions();
        options.setEmitIr(true);
        options.setOptLevel(0);
        CompileResult result = new JNativeCompiler().compile(unit, options);
        assertFalse(result.success());
        assertFalse(result.diagnostics().isEmpty());
        Diagnostic d = result.diagnostics().get(0);
        assertEquals(Diagnostic.Severity.ERROR, d.severity());
        assertTrue(d.location().line() >= 1);
    }

    @Test
    void largeLineCountVirtualizationSmoke() throws Exception {
        assertEquals("1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        TextDocument doc = new TextDocument("");
                        int i = 0;
                        while (i < 800) {
                            doc.insert(doc.getLength(), "line\\n");
                            i = i + 1;
                        }
                        int ok = 0;
                        if (doc.getLineCount() >= 800 && doc.getPieceCount() >= 1) {
                            ok = 1;
                        }
                        System.out.println(ok);
                    }
                }
                """, "EdBig", Map.of(), 0));
    }

    @Test
    void wordBoundaryAndCompoundFind() throws Exception {
        assertEquals("ok\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        TextDocument doc = new TextDocument("foo_bar baz");
                        int a = WordBoundary.next(doc, 0);
                        int b = WordBoundary.prev(doc, 8);
                        TextRange r = TextSearch.find(doc, "baz", 0, 1, 1);
                        int ok = 0;
                        if (a == 7 && b <= 7 && r != null && r.start == 8) {
                            ok = 1;
                        }
                        if (ok == 1) {
                            System.out.println("ok");
                        } else {
                            System.out.println("fail");
                        }
                    }
                }
                """, "EdWord", Map.of(), 0));
    }

    @Test
    void dirtyMarkSavedAndChunks() throws Exception {
        assertEquals("ok\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        TextDocument doc = new TextDocument("ab");
                        doc.insert(2, "c");
                        int dirty1 = 0;
                        if (doc.isDirty()) {
                            dirty1 = 1;
                        }
                        doc.markSaved();
                        int dirty2 = 0;
                        if (doc.isDirty()) {
                            dirty2 = 1;
                        }
                        ChunkBox box = new ChunkBox();
                        doc.iterateChunks(chunk -> {
                            box.n = box.n + 1;
                            box.len = box.len + chunk.length();
                        });
                        if (dirty1 == 1 && dirty2 == 0 && box.len == 3 && box.n >= 1) {
                            System.out.println("ok");
                        } else {
                            System.out.println("fail");
                        }
                    }
                }
                class ChunkBox {
                    int n;
                    int len;
                }
                """, "EdDirty", Map.of(), 0));
    }

    @Test
    void diagnosticFromCompilerMapping() throws Exception {
        assertEquals("1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        TextDocument doc = new TextDocument("int x =\\n");
                        EditorDiagnostic d = EditorDiagnostic.fromCompiler(doc, 1, 1, EditorDiagnostic.ERROR, "bad");
                        if (d.start == 0 && d.message.equals("bad")) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "EdDiag", Map.of(), 0));
    }

    @Test
    void themeLightDark() throws Exception {
        assertEquals("ok\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        EditorTheme light = EditorTheme.light();
                        EditorTheme dark = EditorTheme.dark();
                        Color bg = light.background;
                        Color kw = dark.keyword;
                        int lr = bg.getR();
                        int dr = kw.getR();
                        if (lr >= 0 && dr >= 0) {
                            System.out.println("ok");
                        } else {
                            System.out.println("fail");
                        }
                    }
                }
                """, "EdTheme", Map.of(), 0));
    }

    @Test
    void editorMetricsBench() throws Exception {
        Path big = tempDir.resolve("bench.txt");
        StringBuilder sb = new StringBuilder(80_000);
        for (int i = 0; i < 10000; i++) {
            sb.append("public class L").append(i).append(" { int x; }\n");
        }
        Files.writeString(big, sb.toString(), StandardCharsets.UTF_8);
        String src = """
                public class Main {
                    public static void main(String[] args) {
                        int t0 = EditorClock.nowMs();
                        TextDocument doc = TextDocument.load("bench.txt");
                        int tOpen = EditorClock.nowMs() - t0;
                        int lines = doc.getLineCount();
                        int pieces0 = doc.getPieceCount();
                        t0 = EditorClock.nowMs();
                        int i = 0;
                        while (i < 200) {
                            doc.insert(doc.getLength(), "x");
                            i = i + 1;
                        }
                        int tIns = EditorClock.nowMs() - t0;
                        UndoManager u = new UndoManager(doc);
                        t0 = EditorClock.nowMs();
                        u.recordReplace(doc.getLength(), "", "Z");
                        doc.insert(doc.getLength(), "Z");
                        u.undo();
                        u.redo();
                        int tUndo = EditorClock.nowMs() - t0;
                        t0 = EditorClock.nowMs();
                        TextRange r = TextSearch.find(doc, "public", 0, 1, 0);
                        int tFind = EditorClock.nowMs() - t0;
                        t0 = EditorClock.nowMs();
                        ArrayList<int> states = new ArrayList<int>();
                        states.add(0);
                        ArrayList<StyledSpan> spans = JNativeSyntaxTokenizer.highlightLineRange(doc, 0, 199, states);
                        int tTok = EditorClock.nowMs() - t0;
                        doc.insert(0, " ");
                        t0 = EditorClock.nowMs();
                        ArrayList<StyledSpan> spans2 = JNativeSyntaxTokenizer.highlightLineRange(doc, 0, 2, states);
                        int tInc = EditorClock.nowMs() - t0;
                        System.out.println("OPEN_MS=" + tOpen);
                        System.out.println("LINES=" + lines);
                        System.out.println("PIECES=" + doc.getPieceCount());
                        System.out.println("INSERT200_MS=" + tIns);
                        System.out.println("UNDO_MS=" + tUndo);
                        System.out.println("FIND_MS=" + tFind);
                        System.out.println("TOKENIZE_MS=" + tTok);
                        System.out.println("INCR_MS=" + tInc);
                        System.out.println("SPANS=" + spans.size());
                        int found = 0;
                        if (r != null) {
                            found = 1;
                        }
                        System.out.println("FOUND=" + found);
                        System.out.println("PIECES0=" + pieces0);
                        System.out.println("SPANS2=" + spans2.size());
                        System.out.println("BYTES=" + doc.getLength());
                    }
                }
                """;
        String out = run(src, "EdBench", Map.of(), 0);
        assertTrue(out.contains("OPEN_MS="), out);
        assertTrue(out.contains("LINES=10000") || out.contains("LINES=10001"), out);
        assertTrue(out.contains("FOUND=1"), out);
        Files.writeString(tempDir.resolve("editor-metrics.txt"), out, StandardCharsets.UTF_8);
        System.out.println("--- Editor metrics ---\n" + out);
    }

    @Test
    void headlessSelectionAndGoTo() throws Exception {
        assertEquals("ok\n", run("""
                class Holder {
                    static CodeEditor editor;
                }
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        Window window = new Window("Ed2", 640, 400);
                        Holder.editor = new CodeEditor();
                        Holder.editor.setText("aaa\\nbbb\\nccc");
                        window.setContent(Holder.editor);
                        window.show();
                        Holder.editor.goTo(1, 1);
                        Holder.editor.executeCommand(EditorCommand.SELECT_ALL);
                        TextRange r = Holder.editor.selection.toRange();
                        int ok = 0;
                        if (r.length() >= 9) {
                            ok = 1;
                        }
                        if (ok == 1) {
                            System.out.println("ok");
                        } else {
                            System.out.println("fail");
                        }
                        Holder.editor.disposeEditor();
                        UiTest.closeWindow(window);
                        app.run();
                    }
                }
                """, "EdGo", Map.of("JNATIVE_HEADLESS", "1"), 0));
    }

    @Test
    void veryLongLineSafe() throws Exception {
        assertEquals("ok\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String chunk = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
                        TextDocument doc = new TextDocument("");
                        int i = 0;
                        while (i < 200) {
                            doc.insert(doc.getLength(), chunk);
                            i = i + 1;
                        }
                        doc.insert(doc.getLength(), "END");
                        if (doc.getLineCount() == 1 && doc.getLength() > 10000) {
                            System.out.println("ok");
                        } else {
                            System.out.println("fail");
                        }
                    }
                }
                """, "EdLong", Map.of(), 0));
    }

    @Test
    void compileResultMapsToEditorDiagnostics() {
        SourceUnit unit = new SourceUnit("Bad2.java", """
                public class Main {
                    public static void main(String[] args) {
                        int x =
                    }
                }
                """);
        CompilerOptions options = new CompilerOptions();
        options.setEmitIr(true);
        options.setOptLevel(0);
        CompileResult result = new JNativeCompiler().compile(unit, options);
        assertFalse(result.success());
        assertFalse(result.diagnostics().isEmpty());
        Diagnostic d = result.diagnostics().get(0);
        assertTrue(d.location().line() >= 1);
        assertNotNull(d.message());
    }

    private String run(String source, String name, Map<String, String> env, int opt) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setOptLevel(opt);
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        options.setHeadless(true);
        options.setUiBackend("headless");
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.directory(tempDir.toFile());
        if (env != null) {
            pb.environment().putAll(env);
        }
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(90, TimeUnit.SECONDS), "timeout");
        assertEquals(0, p.exitValue(), () -> stdout + stderr);
        return stdout;
    }
}
