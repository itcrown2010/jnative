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

    @Test
    void wasmRejectsThreadPoolAndConcurrencyPrimitives() {
        String[][] cases = {
                {"ExecutorService ex = Executors.newFixedThreadPool(2);", "ExecutorService"},
                {"ExecutorService ex = new ExecutorService(2);", "ExecutorService"},
                {"ThreadPoolExecutor p = new ThreadPoolExecutor(2);", "ThreadPoolExecutor"},
                {"CountDownLatch l = new CountDownLatch(1);", "primitives"},
                {"Semaphore s = new Semaphore(1);", "primitives"},
                {"ReentrantLock l = new ReentrantLock();", "primitives"},
                {"BlockingQueue<Integer> q = new BlockingQueue<Integer>(4);", "primitives"},
                {"Mutex m = new Mutex();", "Mutex"},
        };
        for (String[] c : cases) {
            String src = "public class W { public static void main(String[] a) { " + c[0] + " } }\n";
            List<Diagnostic> diags = TargetCapabilityChecker.checkSourceText("W.jn", src, "wasm32-browser");
            assertTrue(diags.stream().anyMatch(d -> d.code().equals(DiagnosticCodes.TARGET_API)
                            && d.message().contains(c[1])),
                    () -> c[0] + " should be rejected on wasm32-browser: " + diags);
            assertTrue(TargetCapabilityChecker.checkSourceText("W.jn", src, "native").isEmpty(),
                    () -> c[0] + " must stay allowed on native");
        }
    }

    @Test
    void asyncSemaphoreNotMistakenForThreadSemaphore() {
        List<Diagnostic> diags = TargetCapabilityChecker.checkSourceText(
                "W.jn",
                "public class W { void m(EventLoop l) { AsyncSemaphore s = new AsyncSemaphore(l, 1); } }\n",
                "native");
        assertTrue(diags.isEmpty(), diags::toString);
    }
}
