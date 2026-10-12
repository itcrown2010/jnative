/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.Compilation;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.SemanticModel;
import io.jnative.compiler.SourceUnit;
import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.MethodSymbol;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** JNative 0.21 Studio / JSON / Path / Process e2e.
 * @author 张晓达
 */
class StudioEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void jsonRoundTrip() throws Exception {
        assertEquals("{\"n\":\"Hi\"}\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        JsonObject o = new JsonObject();
                        o.putString("n", "Hi");
                        String s = JsonWriter.write(o);
                        JsonValue v = JsonParser.parse(s);
                        System.out.println(JsonWriter.write(v));
                    }
                }
                """, "StJson", Map.of(), 0));
    }

    @Test
    void pathResolveAndList() throws Exception {
        Files.createDirectories(tempDir.resolve("sub"));
        Files.writeString(tempDir.resolve("sub/a.txt"), "x", StandardCharsets.UTF_8);
        assertEquals("ok\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        FsPath p = FsPath.of("sub");
                        ArrayList<String> names = Files.list(p);
                        int ok = 0;
                        if (Files.isDirectory(p) && names.size() >= 1) {
                            ok = 1;
                        }
                        if (ok == 1) {
                            System.out.println("ok");
                        } else {
                            System.out.println("fail");
                        }
                    }
                }
                """, "StPath", Map.of(), 0));
    }

    @Test
    void processEcho() throws Exception {
        assertEquals("hi\n0\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        ProcessBuilder pb = new ProcessBuilder("/bin/echo");
                        pb.argument("hi");
                        Process p = pb.start();
                        String out = p.readStdout(100);
                        int end = out.length();
                        if (end > 0 && out.charAt(end - 1) == 10) {
                            end = end - 1;
                        }
                        System.out.println(out.substring(0, end));
                        System.out.println(p.waitFor());
                    }
                }
                """, "StProc", Map.of(), 0));
    }

    @Test
    void projectLoadSave() throws Exception {
        assertEquals("HelloStudio\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        Project p = new Project();
                        p.name = "HelloStudio";
                        p.rootDirectory = "proj";
                        FsNative.createDirectories("proj");
                        p.save();
                        Project q = Project.load("proj");
                        System.out.println(q.name);
                    }
                }
                """, "StProj", Map.of(), 0));
    }

    @Test
    void studioHeadlessStartup() throws Exception {
        assertEquals("1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        Application.setHeadless(1);
                        Application app = new Application();
                        StudioApplication studio = new StudioApplication();
                        studio.start();
                        int ok = 0;
                        if (studio.window != null) {
                            ok = 1;
                        }
                        System.out.println(ok);
                        UiTest.closeWindow(studio.window);
                        app.run();
                    }
                }
                """, "StUi", Map.of("JNATIVE_HEADLESS", "1"), 0));
    }

    @Test
    void compileStudioBinary() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path src = Path.of("examples/studio/JNativeStudio.java").toAbsolutePath();
        Path out = tempDir.resolve("JNativeStudio");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(src);
        options.setOutputFile(out);
        options.setHeadless(true);
        options.setUiBackend("headless");
        options.setOptLevel(0);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        assertTrue(Files.isRegularFile(out));
    }

    @Test
    void sourceUnitStillWorks() {
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
    void consoleBufferBound() throws Exception {
        assertEquals("ok\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        ConsoleBuffer b = new ConsoleBuffer(8);
                        b.append("abcdefghij");
                        if (b.getText().length() <= 8) {
                            System.out.println("ok");
                        } else {
                            System.out.println("fail");
                        }
                    }
                }
                """, "StCon", Map.of(), 0));
    }

    @Test
    void frontendAnalyzeSemanticModel() {
        SourceUnit unit = new SourceUnit("Person.java", """
                public class Person {
                    int age;
                    public void setAge(int a) { age = a; }
                    public static void main(String[] args) {
                        Person p = new Person();
                        p.setAge(1);
                    }
                }
                """);
        Compilation c = new JNativeCompiler().analyze(unit, 7L);
        assertTrue(c.success(), () -> c.diagnostics().toString());
        assertEquals(7L, c.version());
        assertNotNull(c.syntaxTree());
        assertNotNull(c.semanticModel());
        SemanticModel m = c.semanticModel();
        assertTrue(m.classes().containsKey("Person"));
        ClassSymbol person = m.classes().get("Person");
        assertFalse(m.declarationOf(person).isEmpty());
        assertFalse(m.referencesOf(person).isEmpty());
        boolean hasSetAge = false;
        for (MethodSymbol ms : person.declaredMethods()) {
            if ("setAge".equals(ms.name())) {
                hasSetAge = true;
            }
        }
        assertTrue(hasSetAge);
        assertFalse(m.membersOf(person.asType()).isEmpty());
    }

    @Test
    void malformedSourceDoesNotCrashAnalyze() {
        SourceUnit unit = new SourceUnit("Bad.java", """
                public class Bad {
                    public static void main(String[] args) {
                        person.
                    }
                }
                """);
        Compilation c = new JNativeCompiler().analyze(unit, 1L);
        assertNotNull(c);
        assertNotNull(c.diagnostics());
        // May fail semantic/parse, but must return a Compilation object.
        assertEquals(1L, c.version());
    }

    @Test
    void workspaceIndexFind() throws Exception {
        assertEquals("1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        WorkspaceIndex idx = new WorkspaceIndex();
                        idx.add(new StudioSymbol("Main", "class", "Main.java", 1, 1));
                        idx.add(new StudioSymbol("Person", "class", "Person.java", 1, 1));
                        ArrayList<StudioSymbol> hits = idx.findByName("Per");
                        System.out.println(hits.size());
                    }
                }
                """, "StIdx", Map.of(), 0));
    }

    @Test
    void completionItemsKind() throws Exception {
        assertEquals("3\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        CompletionItem c = new CompletionItem("setAge", "setAge()", CompletionItem.METHOD, "void");
                        System.out.println(c.kind);
                    }
                }
                """, "StCmp", Map.of(), 0));
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
