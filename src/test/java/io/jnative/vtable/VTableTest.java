package io.jnative.vtable;

import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.MethodSymbol;
import io.jnative.semantic.SemanticAnalyzer;
import io.jnative.semantic.TypedCompilationUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VTableTest {

    private TypedCompilationUnit analyze(String src) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        var unit = new Parser(new Lexer(src, "t.java").tokenize(), reporter).parse("t.java");
        TypedCompilationUnit typed = new SemanticAnalyzer(reporter).analyze(unit);
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        return typed;
    }

    @Test
    void baseClassSlots() {
        TypedCompilationUnit typed = analyze("""
                class Animal {
                    void eat() {}
                    void speak() {}
                }
                public class Main {
                    public static void main(String[] args) {}
                }
                """);
        ClassSymbol animal = typed.classes().get("Animal");
        VTable vt = animal.vtable();
        assertEquals(2, vt.size());
        assertEquals("eat", vt.entry(0).orElseThrow().implementation().name());
        assertEquals("speak", vt.entry(1).orElseThrow().implementation().name());
        assertEquals(0, animal.findMethod("eat").orElseThrow().vtableSlot());
        assertEquals(1, animal.findMethod("speak").orElseThrow().vtableSlot());
    }

    @Test
    void overrideReusesSlot() {
        TypedCompilationUnit typed = analyze("""
                class Animal {
                    void eat() {}
                    void speak() {}
                }
                class Dog extends Animal {
                    void speak() {}
                    void run() {}
                }
                public class Main {
                    public static void main(String[] args) {}
                }
                """);
        ClassSymbol dog = typed.classes().get("Dog");
        VTable vt = dog.vtable();
        assertEquals(3, vt.size());
        assertEquals("_JN_Animal_eat", vt.entry(0).orElseThrow().mangledImplementation());
        assertEquals("_JN_Dog_speak", vt.entry(1).orElseThrow().mangledImplementation());
        assertEquals("_JN_Dog_run", vt.entry(2).orElseThrow().mangledImplementation());

        MethodSymbol animalSpeak = typed.classes().get("Animal").findMethod("speak").orElseThrow();
        MethodSymbol dogSpeak = dog.findDeclaredMethod("speak").orElseThrow();
        assertEquals(animalSpeak.vtableSlot(), dogSpeak.vtableSlot());
        assertEquals(1, dogSpeak.vtableSlot());
    }

    @Test
    void deeperSubclassKeepsStableSlots() {
        TypedCompilationUnit typed = analyze("""
                class Animal {
                    void speak() {}
                }
                class Mammal extends Animal {
                }
                class Dog extends Mammal {
                    void speak() {}
                    void run() {}
                }
                public class Main {
                    public static void main(String[] args) {}
                }
                """);
        ClassSymbol animal = typed.classes().get("Animal");
        ClassSymbol dog = typed.classes().get("Dog");
        assertEquals(0, animal.findMethod("speak").orElseThrow().vtableSlot());
        assertEquals(0, dog.findDeclaredMethod("speak").orElseThrow().vtableSlot());
        assertEquals(1, dog.findDeclaredMethod("run").orElseThrow().vtableSlot());
        assertEquals("_JN_Dog_speak", dog.vtable().entry(0).orElseThrow().mangledImplementation());
    }
}
