package io.jnative.semantic;

import io.jnative.ast.CompilationUnit;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeDataModelSemanticTest {

    @Test
    void escapeReturnRejected() {
        DiagnosticReporter reporter = analyze("""
                @NativeStruct
                class Point { double x; double y; }
                public class Main {
                    static NativePtr<Point> bad() {
                        @Stack
                        Point p = new Point();
                        return Native.addressOf(p);
                    }
                    public static void main(String[] args) {}
                }
                """);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.message().toLowerCase().contains("escape")
                        || d.message().toLowerCase().contains("stack")));
    }

    @Test
    void recursiveInlineStructRejected() {
        DiagnosticReporter reporter = analyze("""
                @NativeStruct
                class Node { Node next; }
                public class Main {
                    public static void main(String[] args) {}
                }
                """);
        assertTrue(reporter.hasErrors());
    }

    @Test
    void managedReferenceInNativeStructRejected() {
        DiagnosticReporter reporter = analyze("""
                @NativeStruct
                class Bad { String name; }
                public class Main {
                    public static void main(String[] args) {}
                }
                """);
        assertTrue(reporter.hasErrors());
    }

    @Test
    void stackOnManagedClassRejected() {
        DiagnosticReporter reporter = analyze("""
                class Person { int x; }
                public class Main {
                    public static void main(String[] args) {
                        @Stack
                        Person p = new Person();
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
    }

    @Test
    void bareNativeStructNewRejected() {
        DiagnosticReporter reporter = analyze("""
                @NativeStruct
                class Point { double x; double y; }
                public class Main {
                    public static void main(String[] args) {
                        Point p = new Point();
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
    }

    @Test
    void incompatibleTypedPointersRejected() {
        DiagnosticReporter reporter = analyze("""
                @NativeStruct
                class Point { double x; double y; }
                @NativeStruct
                class Other { int v; }
                public class Main {
                    public static void main(String[] args) {
                        NativePtr<Point> a = NativePtr.NULL;
                        NativePtr<Other> b = a;
                    }
                }
                """);
        assertTrue(reporter.hasErrors());
    }

    @Test
    void voidPointerWideningAndCastOk() {
        DiagnosticReporter reporter = analyze("""
                @NativeStruct
                class Point { double x; double y; }
                public class Main {
                    public static void main(String[] args) {
                        @Stack
                        Point p = new Point();
                        NativePtr<Point> a = Native.addressOf(p);
                        NativePtr<Void> v = a;
                        NativePtr<Point> b = (NativePtr<Point>) v;
                    }
                }
                """);
        assertFalse(reporter.hasErrors(), reporter.diagnostics()::toString);
    }

    @Test
    void stackAddressOfToNativeCallOk() {
        DiagnosticReporter reporter = analyze("""
                @NativeStruct
                class Point { double x; double y; }
                class Lib {
                    @Native(symbol = "point_sum")
                    static native double pointSum(NativePtr<Point> p);
                }
                public class Main {
                    public static void main(String[] args) {
                        @Stack
                        Point p = new Point();
                        Lib.pointSum(Native.addressOf(p));
                    }
                }
                """);
        assertFalse(reporter.hasErrors(), reporter.diagnostics()::toString);
    }

    private static DiagnosticReporter analyze(String source) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        Lexer lexer = new Lexer(source, "T.java");
        CompilationUnit unit = new Parser(lexer.tokenize(), reporter).parse("T.java");
        if (!reporter.hasErrors()) {
            new SemanticAnalyzer(reporter).analyze(unit);
        }
        return reporter;
    }
}
