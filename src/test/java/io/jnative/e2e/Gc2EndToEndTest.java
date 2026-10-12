/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/** Generational GC2 / TLAB acceptance tests.
 * @author 张晓达
 */
class Gc2EndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void youngObjectsSurviveAcrossMinorGc() throws Exception {
        String out = run("""
                class Node {
                    int value;
                    Node next;
                }
                public class Main {
                    public static void main(String[] args) {
                        Node a = new Node();
                        Node b = new Node();
                        Node c = new Node();
                        a.value = 1;
                        b.value = 2;
                        c.value = 3;
                        a.next = b;
                        b.next = c;
                        System.gc();
                        System.out.println(a.next.next.value);
                    }
                }
                """, "Gc2Graph", Map.of(
                "JNATIVE_GC", "generational",
                "JNATIVE_YOUNG_SIZE", "65536",
                "JNATIVE_GC_STRESS", "minor"
        ), 0);
        assertEquals("3\n", stripStats(out));
    }

    @Test
    void cycleSurvivesMinor() throws Exception {
        String out = run("""
                class Node {
                    Node next;
                }
                public class Main {
                    public static void main(String[] args) {
                        Node a = new Node();
                        Node b = new Node();
                        a.next = b;
                        b.next = a;
                        System.gc();
                        System.out.println(1);
                    }
                }
                """, "Gc2Cycle", Map.of(
                "JNATIVE_GC", "generational",
                "JNATIVE_GC_STRESS", "minor",
                "JNATIVE_GC_VERIFY", "1"
        ), 0);
        assertEquals("1\n", stripStats(out));
    }

    @Test
    void sharedIdentityPreserved() throws Exception {
        String out = run("""
                class Box {
                    int v;
                }
                class Holder {
                    Box a;
                    Box b;
                }
                public class Main {
                    public static void main(String[] args) {
                        Box c = new Box();
                        c.v = 9;
                        Holder h = new Holder();
                        h.a = c;
                        h.b = c;
                        System.gc();
                        if (h.a == h.b) {
                            System.out.println(h.a.v);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "Gc2Share", Map.of(
                "JNATIVE_GC", "generational",
                "JNATIVE_GC_STRESS", "minor"
        ), 0);
        assertEquals("9\n", stripStats(out));
    }

    @Test
    void deadYoungReclaimed() throws Exception {
        String out = run("""
                class Person {
                    int value;
                    Person(int value) { this.value = value; }
                }
                public class Main {
                    public static void main(String[] args) {
                        int i = 0;
                        while (i < 50000) {
                            Person p = new Person(i);
                            i = i + 1;
                        }
                        System.out.println(1);
                    }
                }
                """, "Gc2Dead", Map.of(
                "JNATIVE_GC", "generational",
                "JNATIVE_YOUNG_SIZE", "65536",
                "JNATIVE_TLAB_SIZE", "4096",
                "JNATIVE_GC_STATS", "1"
        ), 0);
        assertEquals("1\n", stripStats(out));
        assertTrue(matchLong(out, "collections: (\\d+)") > 0, out);
        assertTrue(matchLong(out, "freed: (\\d+)") > 0, out);
        assertTrue(matchLong(out, "tlab_allocs: (\\d+)") > 0, out);
    }

    @Test
    void legacyMarksweepStillWorks() throws Exception {
        String out = run("""
                class Person {
                    int value;
                }
                public class Main {
                    public static void main(String[] args) {
                        Person p = new Person();
                        p.value = 42;
                        System.gc();
                        System.out.println(p.value);
                    }
                }
                """, "GcLegacy", Map.of(
                "JNATIVE_GC", "marksweep",
                "JNATIVE_GC_STRESS", "1"
        ));
        assertEquals("42\n", stripStats(out));
    }

    @Test
    void differentialLegacyVsGc2() throws Exception {
        String source = """
                class Node {
                    int value;
                    Node next;
                }
                public class Main {
                    public static void main(String[] args) {
                        Node head = new Node();
                        head.value = 1;
                        Node cur = head;
                        int i = 0;
                        while (i < 20) {
                            Node n = new Node();
                            n.value = i;
                            cur.next = n;
                            cur = n;
                            i = i + 1;
                        }
                        System.gc();
                        System.out.println(head.next.next.value);
                    }
                }
                """;
        String a = stripStats(run(source, "DiffMs", Map.of("JNATIVE_GC", "marksweep"), 0));
        String b = stripStats(run(source, "DiffG2", Map.of(
                "JNATIVE_GC", "generational",
                "JNATIVE_YOUNG_SIZE", "65536",
                "JNATIVE_GC_VERIFY", "1"
        ), 0));
        assertEquals(a, b);
    }

    private String run(String source, String name, Map<String, String> env) throws Exception {
        return run(source, name, env, 2);
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
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());

        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Map<String, String> e = new HashMap<>(env);
        pb.environment().putAll(e);
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "timeout");
        String output = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        return output;
    }

    private static String stripStats(String stdout) {
        // Stats may appear on stderr merged after/before stdout depending on buffering.
        StringBuilder sb = new StringBuilder();
        for (String line : stdout.split("\n", -1)) {
            if (line.startsWith("JNative GC:")
                    || line.startsWith("collections:")
                    || line.startsWith("allocated:")
                    || line.startsWith("freed:")
                    || line.startsWith("live:")
                    || line.startsWith("peak:")
                    || line.startsWith("minor:")
                    || line.startsWith("major:")
                    || line.startsWith("tlab_")
                    || line.startsWith("bytes_")) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(line);
        }
        String s = sb.toString();
        if (!s.endsWith("\n") && stdout.contains("\n")) {
            /* keep trailing newline if program printed one */
        }
        // Normalize to single trailing newline for println programs.
        s = s.replaceAll("\n+$", "");
        return s.isEmpty() ? "" : s + "\n";
    }

    private static long matchLong(String text, String regex) {
        Matcher m = Pattern.compile(regex).matcher(text);
        if (m.find()) {
            return Long.parseLong(m.group(1));
        }
        return -1;
    }
}
