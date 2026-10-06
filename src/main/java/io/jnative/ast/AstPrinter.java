package io.jnative.ast;

import java.util.List;

/**
 * Pretty-prints AST for --emit-ast debugging.
 */
public final class AstPrinter {

    private AstPrinter() {
    }

    public static String print(CompilationUnit unit) {
        StringBuilder sb = new StringBuilder();
        sb.append("CompilationUnit(").append(unit.fileName()).append(")\n");
        for (ClassDeclaration cls : unit.classes()) {
            printClass(cls, sb, 1);
        }
        return sb.toString();
    }

    private static void printClass(ClassDeclaration cls, StringBuilder sb, int indent) {
        indent(sb, indent);
        if (cls.nativeStruct()) {
            sb.append("@NativeStruct ");
        }
        sb.append("Class ").append(cls.name());
        if (cls.superClassName() != null) {
            sb.append(" extends ").append(cls.superClassName());
        }
        sb.append('\n');
        for (FieldDeclaration field : cls.fields()) {
            indent(sb, indent + 1).append("Field ").append(field.type()).append(' ')
                    .append(field.name()).append('\n');
        }
        for (MethodDeclaration method : cls.methods()) {
            printMethod(method, sb, indent + 1);
        }
    }

    private static void printMethod(MethodDeclaration method, StringBuilder sb, int indent) {
        indent(sb, indent)
                .append(method.isPublic() ? "public " : "")
                .append(method.isStatic() ? "static " : "");
        if (method.isConstructor()) {
            sb.append("ctor ");
        } else {
            sb.append(method.returnType()).append(' ');
        }
        sb.append(method.name()).append('(');
        for (int i = 0; i < method.parameters().size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            Parameter p = method.parameters().get(i);
            sb.append(p.type()).append(p.isArray() ? "[]" : "").append(' ').append(p.name());
        }
        sb.append(")\n");
        printStmt(method.body(), sb, indent + 1);
    }

    private static void printStmt(Statement stmt, StringBuilder sb, int indent) {
        switch (stmt) {
            case BlockStatement b -> {
                indent(sb, indent).append("Block {\n");
                for (Statement s : b.statements()) {
                    printStmt(s, sb, indent + 1);
                }
                indent(sb, indent).append("}\n");
            }
            case VariableDeclaration v -> {
                indent(sb, indent);
                if (v.stackAllocated()) {
                    sb.append("@Stack ");
                }
                sb.append("Var ").append(v.type());
                if (v.isArray()) {
                    sb.append("[]");
                }
                sb.append(' ').append(v.name());
                if (v.initializer() != null) {
                    sb.append(" = ").append(printExpr(v.initializer()));
                }
                sb.append('\n');
            }
            case ExpressionStatement e ->
                    indent(sb, indent).append("Expr ").append(printExpr(e.expression())).append('\n');
            case IfStatement i -> {
                indent(sb, indent).append("If ").append(printExpr(i.condition())).append('\n');
                printStmt(i.thenBranch(), sb, indent + 1);
                if (i.elseBranch() != null) {
                    indent(sb, indent).append("Else\n");
                    printStmt(i.elseBranch(), sb, indent + 1);
                }
            }
            case WhileStatement w -> {
                indent(sb, indent).append("While ").append(printExpr(w.condition())).append('\n');
                printStmt(w.body(), sb, indent + 1);
            }
            case ForStatement f -> {
                indent(sb, indent).append("For\n");
                if (f.initializer() != null) {
                    printStmt(f.initializer(), sb, indent + 1);
                }
                if (f.condition() != null) {
                    indent(sb, indent + 1).append("cond ").append(printExpr(f.condition())).append('\n');
                }
                if (f.update() != null) {
                    indent(sb, indent + 1).append("update ").append(printExpr(f.update())).append('\n');
                }
                printStmt(f.body(), sb, indent + 1);
            }
            case ReturnStatement r -> {
                indent(sb, indent).append("Return");
                if (r.value() != null) {
                    sb.append(' ').append(printExpr(r.value()));
                }
                sb.append('\n');
            }
            case ConstructorDelegationStatement d -> {
                indent(sb, indent).append(d.isSuper() ? "super(" : "this(");
                appendArguments(d.arguments(), sb);
                sb.append(")\n");
            }
            case EmptyStatement ignored -> indent(sb, indent).append("Empty\n");
            case ThrowStatement t ->
                    indent(sb, indent).append("Throw ").append(printExpr(t.expression())).append('\n');
            case TryStatement t -> {
                indent(sb, indent).append("Try\n");
                printStmt(t.tryBlock(), sb, indent + 1);
                for (CatchClause c : t.catches()) {
                    indent(sb, indent + 1).append("Catch ").append(c.exceptionType())
                            .append(' ').append(c.variableName()).append('\n');
                    printStmt(c.body(), sb, indent + 2);
                }
                if (t.finallyBlock() != null) {
                    indent(sb, indent + 1).append("Finally\n");
                    printStmt(t.finallyBlock(), sb, indent + 2);
                }
            }
        }
    }

    private static String printExpr(Expression expr) {
        return switch (expr) {
            case BinaryExpression b ->
                    "(" + printExpr(b.left()) + " " + b.operator() + " " + printExpr(b.right()) + ")";
            case UnaryExpression u -> "(" + u.operator() + " " + printExpr(u.operand()) + ")";
            case AssignmentExpression a -> "(" + printExpr(a.target()) + " = " + printExpr(a.value()) + ")";
            case LiteralExpression l -> String.valueOf(l.value());
            case VariableExpression v -> v.name();
            case MethodCallExpression m -> {
                StringBuilder sb = new StringBuilder();
                if (m.receiver() != null) {
                    sb.append(printExpr(m.receiver())).append('.');
                }
                sb.append(m.name());
                if (m.typeArguments() != null && !m.typeArguments().isEmpty()) {
                    sb.append('<');
                    for (int i = 0; i < m.typeArguments().size(); i++) {
                        if (i > 0) {
                            sb.append(", ");
                        }
                        sb.append(m.typeArguments().get(i));
                    }
                    sb.append('>');
                }
                sb.append('(');
                appendArguments(m.arguments(), sb);
                sb.append(')');
                yield sb.toString();
            }
            case PrintlnExpression p -> "println(" + printExpr(p.argument()) + ")";
            case GroupingExpression g -> "(" + printExpr(g.expression()) + ")";
            case NewObjectExpression n -> {
                StringBuilder sb = new StringBuilder("new ").append(n.className()).append('(');
                appendArguments(n.arguments(), sb);
                sb.append(')');
                yield sb.toString();
            }
            case NewArrayExpression n -> "new " + n.elementType() + "[" + printExpr(n.length()) + "]";
            case ArrayAccessExpression a -> printExpr(a.array()) + "[" + printExpr(a.index()) + "]";
            case InstanceOfExpression i -> "(" + printExpr(i.expression()) + " instanceof " + i.type() + ")";
            case CastExpression c -> "((" + c.type() + ") " + printExpr(c.expression()) + ")";
            case GcCollectExpression ignored -> "System.gc()";
            case NativeNullExpression ignored -> "NativePtr.NULL";
            case FieldAccessExpression f -> printExpr(f.receiver()) + "." + f.fieldName();
            case NullLiteralExpression ignored -> "null";
            case ThisExpression ignored -> "this";
            case LambdaExpression l -> {
                StringBuilder sb = new StringBuilder("(");
                for (int i = 0; i < l.parameters().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    var p = l.parameters().get(i);
                    if (p.type() != null) {
                        sb.append(p.type()).append(' ');
                    }
                    sb.append(p.name());
                }
                sb.append(") -> ");
                if (l.isBlock()) {
                    sb.append("{ ... }");
                } else {
                    sb.append(printExpr(l.expressionBody()));
                }
                yield sb.toString();
            }
            case MethodReferenceExpression r -> {
                if (r.typeName() != null) {
                    yield r.typeName().name() + "::" + r.methodName();
                }
                yield printExpr(r.receiver()) + "::" + r.methodName();
            }
        };
    }

    private static void appendArguments(List<Expression> arguments, StringBuilder sb) {
        for (int i = 0; i < arguments.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(printExpr(arguments.get(i)));
        }
    }

    private static StringBuilder indent(StringBuilder sb, int n) {
        sb.append("  ".repeat(Math.max(0, n)));
        return sb;
    }
}
