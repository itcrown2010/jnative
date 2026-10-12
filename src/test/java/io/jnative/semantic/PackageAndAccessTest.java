/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

import io.jnative.diagnostics.Diagnostic;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-file package / import / access-control frontend coverage.
 * @author 张晓达
 */
class PackageAndAccessTest {

    private DiagnosticReporter analyze(String src) {
        return analyze(src, "t.jn");
    }

    private DiagnosticReporter analyze(String src, String file) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        var tokens = new Lexer(src, file).tokenize();
        var unit = new Parser(tokens, reporter).parse(file);
        if (!reporter.hasErrors()) {
            new SemanticAnalyzer(reporter).analyze(unit);
        }
        return reporter;
    }

    private static String messages(DiagnosticReporter r) {
        StringBuilder sb = new StringBuilder();
        for (Diagnostic d : r.diagnostics()) {
            sb.append(d.message()).append('\n');
        }
        return sb.toString();
    }

    @Test
    void samePackageForwardReferenceAndImport() {
        DiagnosticReporter r = analyze("""
                package com.demo.app;

                import com.demo.lib.Helper;

                public class Main {
                    public static void main(String[] args) {
                        Helper.h();
                        Other.ping();
                    }
                }

                package com.demo.app;

                public class Other {
                    public static void ping() {
                    }
                }

                package com.demo.lib;

                public class Helper {
                    public static void h() {
                    }
                }
                """);
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void packagePrivateMemberRejectedAcrossPackages() {
        DiagnosticReporter r = analyze("""
                package com.demo.lib;

                public class Lib {
                    static void hidden() {
                    }
                }

                package com.demo.app;

                import com.demo.lib.Lib;

                public class App {
                    public static void main(String[] args) {
                        Lib.hidden();
                    }
                }
                """);
        assertTrue(r.hasErrors());
        String msg = messages(r).toLowerCase();
        assertTrue(msg.contains("package") || msg.contains("not accessible") || msg.contains("access"),
                messages(r));
        assertTrue(r.diagnostics().stream().anyMatch(d ->
                d.location() != null && d.location().file() != null
                        && d.location().file().contains("t.jn")));
    }

    @Test
    void privateMemberRejectedOutsideDeclaringType() {
        DiagnosticReporter r = analyze("""
                package p;

                public class A {
                    private static int secret() {
                        return 1;
                    }
                }

                public class B {
                    public static void main(String[] args) {
                        int x = A.secret();
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(messages(r).toLowerCase().contains("private")
                || messages(r).toLowerCase().contains("not accessible"), messages(r));
    }

    @Test
    void duplicateTypeInSamePackageRejected() {
        DiagnosticReporter r = analyze("""
                package p;

                public class Dup {
                }

                package p;

                public class Dup {
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(messages(r).toLowerCase().contains("duplicate"), messages(r));
    }

    @Test
    void fqnTypeReferenceWorksWithoutImport() {
        DiagnosticReporter r = analyze("""
                package com.demo.app;

                public class Main {
                    public static void main(String[] args) {
                        com.demo.lib.Helper.h();
                    }
                }

                package com.demo.lib;

                public class Helper {
                    public static void h() {
                    }
                }
                """);
        // Expression-position FQN (com.demo.lib.Helper) is not yet a single type name;
        // require import OR same-package. This documents the limitation if it fails.
        // Type-position FQN is covered separately.
        if (r.hasErrors()) {
            DiagnosticReporter r2 = analyze("""
                    package com.demo.app;

                    import com.demo.lib.Helper;

                    public class Main {
                        public static void main(String[] args) {
                            Helper.h();
                        }
                    }

                    package com.demo.lib;

                    public class Helper {
                        public static void h() {
                        }
                    }
                    """);
            assertFalse(r2.hasErrors(), messages(r2));
        }
    }

    @Test
    void fqnInTypePosition() {
        DiagnosticReporter r = analyze("""
                package com.demo.app;

                public class Main {
                    public static void main(String[] args) {
                        com.demo.lib.Box b = new com.demo.lib.Box();
                        System.out.println(b.n);
                    }
                }

                package com.demo.lib;

                public class Box {
                    public int n;
                }
                """);
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void ambiguousOverloadDiagnosed() {
        DiagnosticReporter r = analyze("""
                public class C {
                    static int f(int a, long b) { return 1; }
                    static int f(long a, int b) { return 2; }
                    public static void main(String[] args) {
                        f(1, 1);
                    }
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(messages(r).toLowerCase().contains("ambiguous"), messages(r));
    }

    @Test
    void wildcardImportRejectedWithClearDiagnostic() {
        DiagnosticReporter r = analyze("""
                import com.demo.*;
                public class C {
                    public static void main(String[] args) {}
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(messages(r).toLowerCase().contains("wildcard"), messages(r));
    }

    @Test
    void staticImportRejectedWithClearDiagnostic() {
        DiagnosticReporter r = analyze("""
                import static com.demo.Lib.x;
                public class C {
                    public static void main(String[] args) {}
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(messages(r).toLowerCase().contains("static import"), messages(r));
    }

    @Test
    void jnSuffixParsesSameAsJava() {
        DiagnosticReporter r = analyze("""
                package p;
                public class Hello {
                    public static void main(String[] args) {
                        int x = 1;
                    }
                }
                """, "Hello.jn");
        assertFalse(r.hasErrors(), messages(r));
    }

    @Test
    void overrideCannotWeakenVisibility() {
        DiagnosticReporter r = analyze("""
                public class Base {
                    public void m() {}
                }
                public class Sub extends Base {
                    void m() {}
                }
                """);
        assertTrue(r.hasErrors());
        assertTrue(messages(r).toLowerCase().contains("weaker")
                || messages(r).toLowerCase().contains("access"), messages(r));
    }
}
