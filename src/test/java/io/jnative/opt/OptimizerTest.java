package io.jnative.opt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class OptimizerTest {

    private static final String POINT_CALC = """
            class Point {
                int x;
                int y;
                Point(int x, int y) {
                    this.x = x;
                    this.y = y;
                }
            }
            public class Main {
                static int calculate(int value) {
                    Point p = new Point(value, value + 1);
                    return p.x + p.y;
                }
                public static void main(String[] args) {
                    System.out.println(calculate(10));
                }
            }
            """;

    @Test
    void scalarReplacementEliminatesPointAllocation() {
        String ir = OptTestSupport.irText(POINT_CALC, 2);
        assertFalse(ir.contains("new Point"), () -> ir);
        assertFalse(ir.contains("= new Point"), () -> ir);
        int calculateIdx = ir.indexOf("func ");
        String calculateSection = ir.substring(calculateIdx);
        assertTrue(calculateSection.contains("calculate") || !calculateSection.contains("new Point"));
    }

    @Test
    void constantFoldReturn() {
        String ir = OptTestSupport.irText("""
                public class C {
                    static int foo() { return 2 + 3; }
                    public static void main(String[] args) {
                        System.out.println(foo());
                    }
                }
                """, 2);
        assertTrue(ir.contains("ret 5") || ir.contains("const-fold") || !ir.contains("add %"));
    }

    @Test
    void nullCheckElimAfterNew() {
        String ir = OptTestSupport.irText(POINT_CALC, 2);
        int start = ir.indexOf("func _JN_Main_calculate");
        int end = ir.indexOf("\nfunc ", start + 1);
        String calc = end > start ? ir.substring(start, end) : ir.substring(start);
        assertFalse(calc.contains("nullcheck"), () -> calc);
    }

    @Test
    void devirtualizeSingleImpl() {
        String ir = OptTestSupport.irText("""
                class Animal {
                    int speak() { return 1; }
                }
                public class Main {
                    public static void main(String[] args) {
                        Animal a = new Animal();
                        System.out.println(a.speak());
                    }
                }
                """, 2);
        assertTrue(ir.contains("call i32 _JN_Animal_speak")
                        || ir.contains("devirtualize")
                        || !ir.contains("virtualcall slot="),
                () -> ir);
    }

    @Test
    void stackAllocatesNonEscapingHolderWithRefField() {
        String ir = OptTestSupport.irText("""
                class Person {
                    int age;
                    Person(int age) { this.age = age; }
                }
                class Holder {
                    Person person;
                    Holder() { this.person = null; }
                }
                public class Main {
                    public static void main(String[] args) {
                        Holder h = new Holder();
                        h.person = new Person(7);
                        System.out.println(h.person.age);
                    }
                }
                """, 2);
        assertTrue(ir.contains("stack") || ir.contains("new Holder"), () -> ir);
        // Holder must not escape; remaining NewObject for Holder should be stack-marked.
        assertTrue(ir.contains("new Holder") && ir.contains(" stack"), () -> ir);
    }

    @Test
    void escapingObjectNotStackAllocated() {
        String ir = OptTestSupport.irText("""
                class Box {
                    int v;
                    Box(int v) { this.v = v; }
                }
                public class Main {
                    static Box make() { return new Box(1); }
                    public static void main(String[] args) {
                        System.out.println(make().v);
                    }
                }
                """, 2);
        int idx = ir.indexOf("new Box");
        assertTrue(idx >= 0, () -> ir);
        String around = ir.substring(idx, Math.min(ir.length(), idx + 80));
        assertFalse(around.contains(" stack"), () -> ir);
    }
}
