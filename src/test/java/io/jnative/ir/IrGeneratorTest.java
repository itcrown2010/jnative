package io.jnative.ir;

import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import io.jnative.semantic.SemanticAnalyzer;
import io.jnative.semantic.TypedCompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IrGeneratorTest {

    private IrModule generate(String src) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        var tokens = new Lexer(src, "t.java").tokenize();
        var unit = new Parser(tokens, reporter).parse("t.java");
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        TypedCompilationUnit typed = new SemanticAnalyzer(reporter).analyze(unit);
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        return new IrGenerator().generate(typed);
    }

    @Test
    void generatesMainAndArithmetic() {
        IrModule module = generate("""
                public class C {
                    public static void main(String[] args) {
                        int a = 10;
                        int b = 20;
                        System.out.println(a + b);
                    }
                }
                """);
        assertTrue(module.functions().stream().anyMatch(IrFunction::isMain));
        String text = IrPrinter.print(module);
        assertTrue(text.contains("func jn_main"));
    }

    @Test
    void generatesBranchesForWhile() {
        IrModule module = generate("""
                public class C {
                    public static void main(String[] args) {
                        int i = 0;
                        while (i < 3) {
                            i = i + 1;
                        }
                    }
                }
                """);
        String text = IrPrinter.print(module);
        assertTrue(text.contains("br "));
        assertTrue(text.contains("while"));
    }

    @Test
    void generatesObjectInstructions() {
        IrModule module = generate("""
                class Person {
                    int age;
                    void birthday() { age = age + 1; }
                }
                public class Main {
                    public static void main(String[] args) {
                        Person p = new Person();
                        p.age = 45;
                        p.birthday();
                        System.out.println(p.age);
                    }
                }
                """);
        String text = IrPrinter.print(module);
        assertTrue(text.contains("new Person"));
        assertTrue(text.contains("storefield"));
        assertTrue(text.contains("loadfield"));
        assertTrue(text.contains("virtualcall"));
    }

    @Test
    void generatesVirtualCallNotDirectOverride() {
        IrModule module = generate("""
                class Animal {
                    void speak() { System.out.println(1); }
                }
                class Dog extends Animal {
                    void speak() { System.out.println(2); }
                }
                public class Main {
                    public static void main(String[] args) {
                        Animal a = new Dog();
                        a.speak();
                    }
                }
                """);
        String text = IrPrinter.print(module);
        assertTrue(text.contains("virtualcall slot="));
        assertFalse(text.contains("call void _JN_Dog_speak"));
    }
}
