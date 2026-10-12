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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Core Round 1 — String / StringBuilder native e2e.
 * @author 张晓达
 */
class StringEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void isEmptyContains() throws Exception {
        assertEquals("1\n1\n0\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String e = "";
                        String s = "hello";
                        if (e.isEmpty()) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        if (s.contains("ell")) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        if (s.contains("xyz")) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "StrEmpty"));
    }

    @Test
    void lastIndexOfCompareTo() throws Exception {
        assertEquals("3\n1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String s = "ababa";
                        System.out.println(s.lastIndexOf("ba"));
                        String a = "abc";
                        String b = "abd";
                        if (a.compareTo(b) < 0) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "StrLast"));
    }

    @Test
    void toUpperReplace() throws Exception {
        assertEquals("HELLO\nx_y_z\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        System.out.println("Hello".toUpperCase());
                        System.out.println("x-y-z".replace("-", "_"));
                    }
                }
                """, "StrUp"));
    }

    @Test
    void unicodeLengthIsUtf16CodeUnits() throws Exception {
        /* length() = UTF-16 code units; utf8Length() = UTF-8 bytes. "中文😀" → 4 / 10. */
        assertEquals("2\n2\n4\n6\n4\n10\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String cn = "你好";
                        System.out.println(cn.length());
                        String emoji = "😀";
                        System.out.println(emoji.length());
                        String mix = "中文😀";
                        System.out.println(mix.length());
                        System.out.println(cn.utf8Length());
                        System.out.println(emoji.utf8Length());
                        System.out.println(mix.utf8Length());
                    }
                }
                """, "StrUtf16"));
    }

    @Test
    void charAtAndSubstringSurrogate() throws Exception {
        assertEquals("1\n2\n1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String e = "😀";
                        char hi = e.charAt(0);
                        char lo = e.charAt(1);
                        if (hi >= 55296 && hi <= 56319) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        if (lo >= 56320 && lo <= 57343) {
                            System.out.println(2);
                        } else {
                            System.out.println(0);
                        }
                        String alone = e.substring(0, 1);
                        System.out.println(alone.length());
                    }
                }
                """, "StrSurr"));
    }

    @Test
    void equalsHashCodeJavaCompatible() throws Exception {
        assertEquals("1\n1\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String a = "中文😀";
                        String b = "中" + "文" + "😀";
                        if (a.equals(b)) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        if (a.hashCode() == b.hashCode()) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                    }
                }
                """, "StrEq"));
    }

    @Test
    void embeddedNulRoundTrip() throws Exception {
        assertEquals("3\n0\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        byte[] b = new byte[3];
                        b[0] = 97;
                        b[1] = 0;
                        b[2] = 98;
                        String s = String.fromBytes(b);
                        System.out.println(s.length());
                        System.out.println((int) s.charAt(1));
                    }
                }
                """, "StrNul"));
    }

    @Test
    void getBytesUtf8RoundTrip() throws Exception {
        assertEquals("10\n中文😀\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String s = "中文😀";
                        byte[] b = s.getBytes();
                        System.out.println(b.length);
                        System.out.println(String.fromBytes(b));
                    }
                }
                """, "StrBytes"));
    }

    @Test
    void gcKeepsUnicode() throws Exception {
        assertEquals("中文😀\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String s = "中文😀";
                        int i = 0;
                        while (i < 2000) {
                            String t = "x" + i;
                            i = i + 1;
                        }
                        System.gc();
                        System.out.println(s);
                    }
                }
                """, "StrGc"));
    }

    @Test
    void stringBuilderAppendToString() throws Exception {
        assertEquals("hello42\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("hello");
                        sb.appendInt(42);
                        System.out.println(sb.toString());
                    }
                }
                """, "SbApp"));
    }

    @Test
    void stringBuilderReverseDelete() throws Exception {
        assertEquals("cba\nxz\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("abc");
                        sb.reverse();
                        System.out.println(sb.toString());
                        StringBuilder sb2 = new StringBuilder();
                        sb2.append("xyz");
                        sb2.delete(1, 2);
                        System.out.println(sb2.toString());
                    }
                }
                """, "SbRev"));
    }

    @Test
    void stringBuilderManyAppends() throws Exception {
        assertEquals("1000\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        StringBuilder sb = new StringBuilder();
                        int i = 0;
                        while (i < 1000) {
                            sb.append("x");
                            i = i + 1;
                        }
                        System.out.println(sb.length());
                    }
                }
                """, "SbMany"));
    }

    @Test
    void splitLiteral() throws Exception {
        assertEquals("a\nb\nc\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String[] p = "a,b,c".split(",");
                        System.out.println(p[0]);
                        System.out.println(p[1]);
                        System.out.println(p[2]);
                    }
                }
                """, "StrSplit"));
    }

    @Test
    void splitConsecutiveAndTrailing() throws Exception {
        assertEquals("3\n0\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String[] p = "a,,c".split(",");
                        System.out.println(p.length);
                        System.out.println(p[1].length());
                    }
                }
                """, "StrSplit2"));
    }

    @Test
    void splitUnicode() throws Exception {
        assertEquals("你\n好\n世界\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String[] p = "你|好|世界".split("|");
                        System.out.println(p[0]);
                        System.out.println(p[1]);
                        System.out.println(p[2]);
                    }
                }
                """, "StrSplitU"));
    }

    @Test
    void joinBasic() throws Exception {
        assertEquals("a,b,c\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String[] v = new String[3];
                        v[0] = "a";
                        v[1] = "b";
                        v[2] = "c";
                        System.out.println(String.join(",", v));
                    }
                }
                """, "StrJoin"));
    }

    @Test
    void joinEmptyAndUnicode() throws Exception {
        assertEquals("\n你-好\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String[] empty = new String[0];
                        System.out.println(String.join(",", empty));
                        String[] v = new String[2];
                        v[0] = "你";
                        v[1] = "好";
                        System.out.println(String.join("-", v));
                    }
                }
                """, "StrJoin2"));
    }

    @Test
    void stringSplitJoin_O2() throws Exception {
        assertEquals("a-b\n", runOpt("""
                public class Main {
                    public static void main(String[] args) {
                        String[] p = "a,b".split(",");
                        System.out.println(String.join("-", p));
                    }
                }
                """, "StrSJ2", 2));
    }

    @Test
    void startsEndsTrim() throws Exception {
        assertEquals("1\n1\nhello\n", run("""
                public class Main {
                    public static void main(String[] args) {
                        String s = "  hello  ";
                        if ("hello".startsWith("he")) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        if ("hello".endsWith("lo")) {
                            System.out.println(1);
                        } else {
                            System.out.println(0);
                        }
                        System.out.println(s.trim());
                    }
                }
                """, "StrTrim"));
    }

    private String run(String source, String name) throws Exception {
        return runOpt(source, name, 0);
    }

    private String runOpt(String source, String name, int opt) throws Exception {
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
        Process process = pb.start();
        byte[] bytes = process.getInputStream().readAllBytes();
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "timeout");
        String output = new String(bytes, StandardCharsets.UTF_8);
        assertEquals(0, process.exitValue(), output);
        return output;
    }
}
