package io.jnative.parser;

import io.jnative.ast.*;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Lexer;
import io.jnative.lexer.Token;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class ParserTest {

    private CompilationUnit parse(String src) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        List<Token> tokens = new Lexer(src, "t.java").tokenize();
        CompilationUnit unit = new Parser(tokens, reporter).parse("t.java");
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        return unit;
    }

    @Test
    void parsesClassAndMain() {
        CompilationUnit unit = parse("""
                public class Hello {
                    public static void main(String[] args) {
                        System.out.println("hi");
                    }
                }
                """);
        assertEquals(1, unit.classes().size());
        assertEquals("Hello", unit.classes().getFirst().name());
        MethodDeclaration main = unit.classes().getFirst().methods().getFirst();
        assertEquals("main", main.name());
        assertTrue(main.isStatic());
    }

    @Test
    void parsesControlFlowAndExpressions() {
        CompilationUnit unit = parse("""
                public class C {
                    static int add(int a, int b) { return a + b; }
                    public static void main(String[] args) {
                        int x = 1;
                        if (x < 2) { x = x + 1; } else { x = 0; }
                        while (x < 5) { x = x + 1; }
                        for (int i = 0; i < 3; i = i + 1) { System.out.println(i); }
                        int y = add(1, 2);
                    }
                }
                """);
        assertEquals(2, unit.classes().getFirst().methods().size());
        BlockStatement body = unit.classes().getFirst().methods().get(1).body();
        assertTrue(body.statements().stream().anyMatch(s -> s instanceof IfStatement));
        assertTrue(body.statements().stream().anyMatch(s -> s instanceof WhileStatement));
        assertTrue(body.statements().stream().anyMatch(s -> s instanceof ForStatement));
    }

    @Test
    void respectsOperatorPrecedence() {
        CompilationUnit unit = parse("""
                public class C {
                    public static void main(String[] args) {
                        int c = 10 + 20 * 2;
                    }
                }
                """);
        VariableDeclaration decl = (VariableDeclaration) unit.classes().getFirst()
                .methods().getFirst().body().statements().getFirst();
        BinaryExpression expr = (BinaryExpression) decl.initializer();
        assertEquals(BinaryOp.ADD, expr.operator());
        assertInstanceOf(BinaryExpression.class, expr.right());
        assertEquals(BinaryOp.MUL, ((BinaryExpression) expr.right()).operator());
    }
}
