/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.target;

import io.jnative.diagnostics.Diagnostic;
import io.jnative.diagnostics.DiagnosticCodes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Document / browser-DOM capability checks must not false-positive on editor APIs.
 *
 * @author 张晓达
 */
public class TargetCapabilityDocumentTest {

    @Test
    void editorTextDocumentAllowedOnNative() {
        List<Diagnostic> diags = TargetCapabilityChecker.checkSourceText(
                "Ed.jn",
                """
                        public class Ed {
                            void open() {
                                TextDocument doc = TextDocument.load("a.txt");
                                doc.saveAs("b.txt");
                            }
                        }
                        """,
                "native");
        assertTrue(diags.isEmpty(), diags::toString);
    }

    @Test
    void editorGetDocumentNotFlaggedAsBrowserDomOnNative() {
        List<Diagnostic> diags = TargetCapabilityChecker.checkSourceText(
                "Ed.jn",
                "class Ed { void m(CodeEditor e) { e.getDocument().saveAs(\"x\"); } }\n",
                "native");
        assertTrue(diags.isEmpty(), diags::toString);
    }

    @Test
    void userDocumentTypeNotFlaggedOnNative() {
        List<Diagnostic> diags = TargetCapabilityChecker.checkSourceText(
                "App.jn",
                """
                        class Document { int id; Document(int id) { this.id = id; } }
                        public class App {
                            public static void main(String[] args) {
                                Document d = new Document(1);
                                System.out.println(d.id);
                            }
                        }
                        """,
                "native");
        assertTrue(diags.isEmpty(), () -> diags.toString());
    }

    @Test
    void commentAndStringDocumentNamesDoNotTrigger() {
        List<Diagnostic> diags = TargetCapabilityChecker.checkSourceText(
                "App.jn",
                """
                        public class App {
                            // Browser.document().getElementById("x")
                            public static void main(String[] args) {
                                String s = "Document.getElementById";
                                System.out.println(s.length());
                            }
                        }
                        """,
                "native");
        assertTrue(diags.isEmpty(), diags::toString);
    }

    @Test
    void realBrowserDocumentApiRejectedOnNative() {
        List<Diagnostic> diags = TargetCapabilityChecker.checkSourceText(
                "App.jn",
                """
                        public class App {
                            public static void main(String[] args) {
                                Document d = Browser.document();
                                d.getElementById("root");
                            }
                        }
                        """,
                "native");
        assertFalse(diags.isEmpty());
        assertTrue(diags.stream().anyMatch(d ->
                d.code().equals(DiagnosticCodes.TARGET_API)
                        && d.message().toLowerCase().contains("browser")));
    }

    @Test
    void maskPreservesNewlinesForLineNumbers() {
        String src = "a\n// Document.getElementById\nb\n";
        String masked = TargetCapabilityChecker.maskCommentsAndStrings(src);
        assertEquals(src.length(), masked.length());
        assertTrue(masked.contains("\n"));
        assertFalse(masked.contains("Document"));
    }

    @Test
    void wasmRejectsUserAsyncWithClearCode() {
        List<Diagnostic> diags = TargetCapabilityChecker.checkSourceText(
                "W.jn",
                """
                        public class W {
                            async int m() { return 1; }
                            public static void main(String[] args) {
                                Future<Integer> f = new W().m();
                            }
                        }
                        """,
                "wasm32-browser");
        assertFalse(diags.isEmpty());
        assertTrue(diags.stream().anyMatch(d ->
                d.code().equals(DiagnosticCodes.BACKEND_UNSUPPORTED)
                        && d.message().toLowerCase().contains("async")));
    }
}
