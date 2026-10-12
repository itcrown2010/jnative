/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend;

import io.jnative.backend.llvm.LlvmIrGenerator;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.ir.IrGenerator;
import io.jnative.ir.IrModule;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import io.jnative.semantic.SemanticAnalyzer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class LlvmIrGeneratorTest {

    private String llvm(String src) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        var tokens = new Lexer(src, "t.java").tokenize();
        var unit = new Parser(tokens, reporter).parse("t.java");
        var typed = new SemanticAnalyzer(reporter).analyze(unit);
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        IrModule ir = new IrGenerator().generate(typed);
        return new LlvmIrGenerator().generate(ir);
    }

    @Test
    void emitsNativeMainAndPrintlnRuntime() {
        String ll = llvm("""
                public class C {
                    public static void main(String[] args) {
                        System.out.println(42);
                    }
                }
                """);
        assertTrue(ll.contains("define i32 @main(i32 %argc, ptr %argv)"), ll);
        assertTrue(ll.contains("jnative_argv_to_string_array"));
        assertTrue(ll.contains("define void @jn_main"));
        assertTrue(ll.contains("jnative_println_i32"));
    }

    @Test
    void emitsPutsForString() {
        String ll = llvm("""
                public class C {
                    public static void main(String[] args) {
                        System.out.println("Hello");
                    }
                }
                """);
        assertTrue(ll.contains("jnative_println_string"));
        assertTrue(ll.contains("Hello"));
    }

    @Test
    void emitsCallForUserMethod() {
        String ll = llvm("""
                public class C {
                    static int add(int a, int b) { return a + b; }
                    public static void main(String[] args) {
                        System.out.println(add(1, 2));
                    }
                }
                """);
        assertTrue(ll.contains("define i32 @_JN_C_add_i32_i32") || ll.contains("@_JN_C_add"));
        assertTrue(ll.contains("call i32 @_JN_C_add"));
    }

    @Test
    void emitsStructAndAllocForObject() {
        String ll = llvm("""
                class Person { int age; }
                public class Main {
                    public static void main(String[] args) {
                        Person p = new Person();
                        p.age = 1;
                        System.out.println(p.age);
                    }
                }
                """);
        assertTrue(ll.contains("%class.Person = type"));
        assertTrue(ll.contains("jnative_alloc_zeroed"));
        assertTrue(ll.contains("jnative_null_check"));
        assertTrue(ll.contains("getelementptr"));
        assertTrue(ll.contains("@vtable.Person") || ll.contains("@meta.Person"));
        assertTrue(ll.contains("@meta.Person"));
    }

    @Test
    void emitsIndirectCallForVirtualDispatch() {
        String ll = llvm("""
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
        assertTrue(ll.contains("@vtable.Dog"));
        assertTrue(ll.contains("@vtable.Animal"));
        assertTrue(ll.contains("ptr @_JN_Dog_speak"));
        // indirect call: "call ... %fn" pattern (not call void @_JN_Animal_speak)
        assertTrue(ll.matches("(?s).*call void %jn\\.scratch\\.\\d+\\(ptr .*"),
                () -> "expected indirect call, got:\n" + ll);
        assertFalse(ll.contains("call void @_JN_Animal_speak(ptr"));
    }
}
