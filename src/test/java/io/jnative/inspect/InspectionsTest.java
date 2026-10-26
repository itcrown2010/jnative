/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.inspect;

import io.jnative.compiler.Compilation;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.SourceUnit;
import io.jnative.diagnostics.Diagnostic;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InspectionsTest {
    private static List<Diagnostic> warnings(String src) {
        Compilation c = new JNativeCompiler().analyze(new SourceUnit("/tmp/InspectDemo.java", src), 1);
        assertTrue(c.success(), c.diagnostics().toString());
        return c.diagnostics().stream().filter(d -> d.severity() == Diagnostic.Severity.WARNING).toList();
    }

    private static String codes(List<Diagnostic> ds) {
        return ds.stream().map(d -> d.code() + "@" + d.location().line()).sorted().collect(Collectors.joining(","));
    }

    @Test
    void findsEachProblemOnce() {
        String src = """
                import jnative.io.File;

                public class InspectDemo {
                    private int unusedField;
                    private int usedField;
                    private void helper() {
                    }

                    public static void main(String[] args) {
                        int a = 1;
                        int c = 3;
                        c = 4;
                        String s = "x";
                        if (s == "y") {
                            System.out.println("eq");
                        }
                        if (true) {
                            System.out.println("always");
                        }
                        try {
                            System.out.println(s);
                        } catch (Exception e) {
                        }
                        int d = 5;
                        d = d;
                        InspectDemo m = new InspectDemo();
                        System.out.println(m.usedField);
                        return;
                        System.out.println("dead");
                    }
                }
                """;
        assertEquals("JNW001@10,JNW002@11,JNW003@1,JNW004@4,JNW004@6,JNW005@29,JNW006@20,JNW007@14,JNW008@17,JNW010@25",
                codes(warnings(src)));
    }

    @Test
    void cleanCodeHasNoWarnings() {
        String src = """
                public class InspectDemo {
                    private int count;
                    private int bump(int by) {
                        count = count + by;
                        return count;
                    }

                    public static void main(String[] args) {
                        ArrayList<String> names = new ArrayList<String>();
                        names.add("x");
                        int total = 0;
                        for (int i = 0; i < names.size(); i++) {
                            total = total + i;
                        }
                        InspectDemo d = new InspectDemo();
                        int shown = total;
                        Runnable r = () -> System.out.println(d.bump(shown));
                        r.run();
                        try {
                            System.out.println(names.get(0));
                        } catch (Exception e) {
                            // nothing to do: the list is never empty here
                        }
                        String t = "b";
                        if (t.equals("b") || t == null) {
                            System.out.println(t);
                        }
                    }
                }
                """;
        assertEquals("", codes(warnings(src)));
    }

    @Test
    void noinspectCommentSuppresses() {
        String src = """
                public class InspectDemo {
                    public static void main(String[] args) {
                        int keep = 1; // noinspect
                        // noinspect: JNW001
                        int other = 2;
                        int shown = 3;
                    }
                }
                """;
        assertEquals("JNW001@6", codes(warnings(src)));
    }
}
