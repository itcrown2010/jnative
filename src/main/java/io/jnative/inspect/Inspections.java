/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.inspect;

import io.jnative.ast.*;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.semantic.Visibility;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static code inspections over parsed user sources (IDE tooling: {@code analyze}, LSP, Studio).
 * Findings are warnings with stable codes; precision is preferred over recall. A finding on a line (or the line
 * above it) carrying {@code // noinspect} or {@code // noinspect: JNW001} is suppressed.
 *
 * <pre>
 * JNW001 local variable is never used           JNW006 empty catch block
 * JNW002 value assigned but never read          JNW007 strings compared with == / !=
 * JNW003 unused import                          JNW008 condition is always true / false
 * JNW004 unused private field / method          JNW009 local variable hides a field
 * JNW005 unreachable code                       JNW010 self assignment
 * (JNW009 is implemented but disabled by default.)
 * </pre>
 *
 * @author 张晓达
 */
public final class Inspections {
    public static final String UNUSED_LOCAL = "JNW001";
    public static final String NEVER_READ = "JNW002";
    public static final String UNUSED_IMPORT = "JNW003";
    public static final String UNUSED_PRIVATE = "JNW004";
    public static final String UNREACHABLE = "JNW005";
    public static final String EMPTY_CATCH = "JNW006";
    public static final String STRING_COMPARE = "JNW007";
    public static final String CONSTANT_CONDITION = "JNW008";
    public static final String HIDES_FIELD = "JNW009";
    public static final String SELF_ASSIGN = "JNW010";

    /** Off by default: hiding a field with a local is common and usually intentional. */
    static final boolean HIDES_FIELD_ENABLED = false;

    private Inspections() {
    }

    /**
     * Runs all inspections.
     *
     * @param units     parsed compilation units (may include stdlib units, which are skipped)
     * @param texts     source text by unit path
     * @param userPaths paths of the units to inspect
     */
    public static void run(List<CompilationUnit> units, Map<String, String> texts, Set<String> userPaths,
                           DiagnosticReporter out) {
        for (CompilationUnit cu : units) {
            if (cu == null || !userPaths.contains(cu.fileName())) {
                continue;
            }
            String text = texts.get(cu.fileName());
            if (text == null) {
                continue;
            }
            try {
                new FileInspector(cu, text, out).run();
            } catch (RuntimeException e) {
                // an inspection must never break analysis
                System.err.println("jnative inspect: " + cu.fileName() + ": " + e);
            }
        }
    }

    // ------------------------------------------------------------------------------------------ per file

    private static final class Local {
        final String name;
        final TypeName type;
        final boolean isArray;
        final SourceLocation loc;
        final int kind; // 0 local, 1 foreach variable, 2 other (parameter, lambda parameter, catch, binding)
        int reads;
        int writes;

        Local(String name, TypeName type, boolean isArray, SourceLocation loc, int kind) {
            this.name = name;
            this.type = type;
            this.isArray = isArray;
            this.loc = loc;
            this.kind = kind;
        }

        boolean isString() {
            return !isArray && type != null && type.kind() == TypeName.Kind.CLASS && "String".equals(type.name());
        }
    }

    private static final class FileInspector {
        final CompilationUnit cu;
        final String text;
        final String[] lines;
        final DiagnosticReporter out;
        final Deque<List<Local>> scopes = new ArrayDeque<>();
        final Set<String> seen = new HashSet<>();
        ClassDeclaration cls;
        Set<String> fieldNames = new HashSet<>();
        Map<String, FieldDeclaration> fieldsByName = new HashMap<>();
        List<String> idents;

        FileInspector(CompilationUnit cu, String text, DiagnosticReporter out) {
            this.cu = cu;
            this.text = text;
            this.out = out;
            this.lines = text.split("\n", -1);
        }

        void run() {
            idents = identifierTokens(text);
            Set<String> importsDone = new HashSet<>();
            for (ClassDeclaration c : cu.classes()) {
                checkImports(c, importsDone);
                if (c.isAnonymous()) {
                    inspectClass(c, false);
                } else {
                    inspectClass(c, true);
                }
            }
        }

        // ----- classes and members

        void inspectClass(ClassDeclaration c, boolean members) {
            cls = c;
            fieldNames = new HashSet<>();
            fieldsByName = new HashMap<>();
            for (FieldDeclaration f : c.fields()) {
                fieldNames.add(f.name());
                fieldsByName.put(f.name(), f);
            }
            if (members) {
                checkPrivateMembers(c);
            }
            for (FieldDeclaration f : c.fields()) {
                if (f.initializer() != null) {
                    scopes.clear();
                    scopes.push(new ArrayList<>());
                    expr(f.initializer());
                    popScope();
                }
            }
            for (MethodDeclaration m : c.methods()) {
                if (m.body() == null) {
                    continue;
                }
                scopes.clear();
                scopes.push(new ArrayList<>());
                for (Parameter p : m.parameters()) {
                    declare(new Local(p.name(), p.type(), p.isArray(), p.location(), 2));
                }
                block(m.body().statements());
                popScope();
            }
        }

        void checkImports(ClassDeclaration c, Set<String> done) {
            for (ImportDeclaration imp : c.imports()) {
                if (imp.location() == null || !done.add(imp.qualifiedType() + "@" + imp.location().line())) {
                    continue;
                }
                String simple = imp.simpleName();
                if (simple.isEmpty() || simple.equals("*")) {
                    continue;
                }
                int uses = 0;
                for (int i = 0; i < idents.size(); i++) {
                    if (idents.get(i).equals(simple)) {
                        uses++;
                    }
                }
                // the import line itself contributes exactly one token with this name
                if (uses <= 1) {
                    report(refine(imp.location(), simple), UNUSED_IMPORT, "Unused import '" + imp.qualifiedType() + "'",
                            "Remove the import");
                }
            }
        }

        void checkPrivateMembers(ClassDeclaration c) {
            Map<String, Integer> count = new HashMap<>();
            for (String id : idents) {
                count.merge(id, 1, Integer::sum);
            }
            Map<String, Integer> declaredMethods = new HashMap<>();
            for (MethodDeclaration m : c.methods()) {
                declaredMethods.merge(m.name(), 1, Integer::sum);
            }
            for (FieldDeclaration f : c.fields()) {
                if (f.visibility() == Visibility.PRIVATE && count.getOrDefault(f.name(), 0) <= 1) {
                    report(refine(f.location(), f.name()), UNUSED_PRIVATE, "Private field '" + f.name() + "' is never used",
                            "Remove the field");
                }
            }
            for (MethodDeclaration m : c.methods()) {
                if (m.visibility() == Visibility.PRIVATE && !m.isConstructor() && !m.isNative()
                        && count.getOrDefault(m.name(), 0) <= declaredMethods.get(m.name())) {
                    report(refine(m.location(), m.name()), UNUSED_PRIVATE, "Private method '" + m.name() + "' is never used",
                            "Remove the method");
                }
            }
        }

        // ----- scopes

        void declare(Local l) {
            List<Local> top = scopes.peek();
            if (top != null) {
                top.add(l);
            }
        }

        Local lookup(String name) {
            for (List<Local> s : scopes) {
                for (int i = s.size() - 1; i >= 0; i--) {
                    if (s.get(i).name.equals(name)) {
                        return s.get(i);
                    }
                }
            }
            return null;
        }

        void popScope() {
            List<Local> s = scopes.pop();
            for (Local l : s) {
                if (l.kind == 2 || l.name.startsWith("$") || l.name.equals("_")) {
                    continue;
                }
                if (l.reads == 0 && l.writes == 0) {
                    report(refine(l.loc, l.name), UNUSED_LOCAL,
                            (l.kind == 1 ? "Loop variable '" : "Local variable '") + l.name + "' is never used",
                            "Remove the variable");
                } else if (l.reads == 0 && l.kind == 0) {
                    report(refine(l.loc, l.name), NEVER_READ,
                            "The value assigned to '" + l.name + "' is never read", "Remove the variable or use its value");
                }
            }
        }

        // ----- statements

        void block(List<Statement> stmts) {
            scopes.push(new ArrayList<>());
            boolean dead = false;
            boolean reported = false;
            for (Statement s : stmts) {
                if (s instanceof EmptyStatement) {
                    continue;
                }
                if (dead && !reported) {
                    report(s.location(), UNREACHABLE, "Unreachable code", "Remove the unreachable statements");
                    reported = true;
                }
                stmt(s);
                if (s instanceof ReturnStatement || s instanceof ThrowStatement || s instanceof BreakStatement
                        || s instanceof ContinueStatement) {
                    dead = true;
                }
            }
            popScope();
        }

        void stmt(Statement s) {
            if (s == null) {
                return;
            }
            switch (s) {
                case BlockStatement b -> block(b.statements());
                case VariableDeclaration v -> {
                    if (v.initializer() != null) {
                        expr(v.initializer());
                    }
                    if (HIDES_FIELD_ENABLED && fieldNames.contains(v.name()) && lookup(v.name()) == null && cls != null
                            && !cls.isAnonymous()) {
                        report(refine(v.location(), v.name()), HIDES_FIELD,
                                "Local variable '" + v.name() + "' hides a field of the same name",
                                "Rename the variable or use this." + v.name());
                    }
                    Local l = new Local(v.name(), v.type(), v.isArray(), v.location(), 0);
                    if (v.initializer() != null) {
                        l.writes = 0; // the initializer alone does not count as a use
                    }
                    declare(l);
                }
                case ExpressionStatement e -> expr(e.expression());
                case IfStatement i -> {
                    constantCondition(i.condition());
                    expr(i.condition());
                    scoped(i.thenBranch());
                    scoped(i.elseBranch());
                }
                case WhileStatement w -> {
                    if (w.condition() instanceof LiteralExpression lit && lit.kind() == LiteralExpression.Kind.BOOLEAN
                            && Boolean.FALSE.equals(lit.value())) {
                        constantCondition(w.condition());
                    }
                    expr(w.condition());
                    scoped(w.body());
                }
                case DoWhileStatement d -> {
                    scoped(d.body());
                    expr(d.condition());
                }
                case ForStatement f -> {
                    scopes.push(new ArrayList<>());
                    stmt(f.initializer());
                    if (f.condition() != null) {
                        expr(f.condition());
                    }
                    for (Expression u : f.allUpdates()) {
                        expr(u);
                    }
                    scoped(f.body());
                    popScope();
                }
                case EnhancedForStatement f -> {
                    expr(f.array());
                    scopes.push(new ArrayList<>());
                    declare(new Local(f.variableName(), f.elementType(), f.elementIsArray(), f.location(), 1));
                    scoped(f.body());
                    popScope();
                }
                case SwitchStatement sw -> {
                    expr(sw.selector());
                    sections(sw.sections());
                }
                case LabeledStatement l -> stmt(l.body());
                case ReturnStatement r -> {
                    if (r.value() != null) {
                        expr(r.value());
                    }
                }
                case ThrowStatement t -> expr(t.expression());
                case YieldStatement y -> expr(y.value());
                case ConstructorDelegationStatement c -> {
                    for (Expression a : c.arguments()) {
                        expr(a);
                    }
                }
                case TryStatement t -> {
                    scopes.push(new ArrayList<>());
                    for (VariableDeclaration r : t.resources()) {
                        stmt(r);
                    }
                    block(t.tryBlock().statements());
                    popScope();
                    for (CatchClause c : t.catches()) {
                        checkEmptyCatch(c);
                        scopes.push(new ArrayList<>());
                        declare(new Local(c.variableName(), c.exceptionType(), false, c.location(), 2));
                        block(c.body().statements());
                        popScope();
                    }
                    if (t.finallyBlock() != null) {
                        block(t.finallyBlock().statements());
                    }
                }
                case BreakStatement b -> {
                }
                case ContinueStatement c -> {
                }
                case EmptyStatement e -> {
                }
            }
        }

        void scoped(Statement s) {
            if (s == null) {
                return;
            }
            if (s instanceof BlockStatement) {
                stmt(s);
            } else {
                scopes.push(new ArrayList<>());
                stmt(s);
                popScope();
            }
        }

        void sections(List<SwitchSection> sections) {
            for (SwitchSection sec : sections) {
                for (SwitchLabel l : sec.labels()) {
                    if (l instanceof CaseLabel cl && cl.constant() != null) {
                        expr(cl.constant());
                    }
                }
                scopes.push(new ArrayList<>());
                boolean dead = false;
                boolean reported = false;
                for (Statement st : sec.statements()) {
                    if (st instanceof EmptyStatement) {
                        continue;
                    }
                    if (dead && !reported) {
                        report(st.location(), UNREACHABLE, "Unreachable code", "Remove the unreachable statements");
                        reported = true;
                    }
                    stmt(st);
                    if (st instanceof ReturnStatement || st instanceof ThrowStatement || st instanceof BreakStatement
                            || st instanceof ContinueStatement) {
                        dead = true;
                    }
                }
                popScope();
            }
        }

        void checkEmptyCatch(CatchClause c) {
            if (!c.body().statements().isEmpty() || c.body().location() == null) {
                return;
            }
            int open = offsetOf(c.body().location());
            if (open < 0 || open >= text.length()) {
                return;
            }
            int brace = text.indexOf('{', open);
            if (brace < 0) {
                return;
            }
            int depth = 0;
            int end = -1;
            for (int i = brace; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '{') {
                    depth++;
                } else if (ch == '}') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
            if (end < 0) {
                return;
            }
            String inner = text.substring(brace + 1, end);
            if (inner.contains("//") || inner.contains("/*")) {
                return; // an explaining comment counts as intentional
            }
            report(c.location(), EMPTY_CATCH, "Empty catch block silently swallows the exception",
                    "Handle the exception or add a comment explaining why it is ignored");
        }

        void constantCondition(Expression cond) {
            if (cond instanceof LiteralExpression lit && lit.kind() == LiteralExpression.Kind.BOOLEAN) {
                report(lit.location(), CONSTANT_CONDITION,
                        "Condition is always " + lit.value(), "Simplify the condition");
            }
        }

        // ----- expressions

        void expr(Expression e) {
            if (e == null) {
                return;
            }
            switch (e) {
                case VariableExpression v -> {
                    Local l = lookup(v.name());
                    if (l != null) {
                        l.reads++;
                    }
                }
                case AssignmentExpression a -> {
                    selfAssign(a);
                    if (a.target() instanceof VariableExpression tv) {
                        Local l = lookup(tv.name());
                        if (l != null) {
                            l.writes++;
                        }
                    } else {
                        expr(a.target());
                    }
                    expr(a.value());
                }
                case BinaryExpression b -> {
                    stringCompare(b);
                    expr(b.left());
                    expr(b.right());
                }
                case UnaryExpression u -> expr(u.operand());
                case MethodCallExpression m -> {
                    expr(m.receiver());
                    for (Expression a : m.arguments()) {
                        expr(a);
                    }
                }
                case PrintlnExpression p -> expr(p.argument());
                case GroupingExpression g -> expr(g.expression());
                case NewObjectExpression n -> {
                    for (Expression a : n.arguments()) {
                        expr(a);
                    }
                }
                case FieldAccessExpression f -> expr(f.receiver());
                case NewArrayExpression n -> {
                    expr(n.length());
                    for (Expression a : n.innerLengths()) {
                        expr(a);
                    }
                }
                case ArrayInitExpression n -> {
                    for (Expression a : n.elements()) {
                        expr(a);
                    }
                }
                case ArrayAccessExpression a -> {
                    expr(a.array());
                    expr(a.index());
                }
                case InstanceOfExpression i -> {
                    expr(i.expression());
                    if (i.binding() != null) {
                        declare(new Local(i.binding(), i.type(), false, i.location(), 2));
                    }
                }
                case CastExpression c -> expr(c.expression());
                case ConditionalExpression c -> {
                    expr(c.condition());
                    expr(c.thenExpr());
                    expr(c.elseExpr());
                }
                case LambdaExpression l -> {
                    scopes.push(new ArrayList<>());
                    for (LambdaParameter p : l.parameters()) {
                        declare(new Local(p.name(), p.type(), p.isArray(), p.location(), 2));
                    }
                    if (l.expressionBody() != null) {
                        expr(l.expressionBody());
                    }
                    if (l.blockBody() != null) {
                        block(l.blockBody().statements());
                    }
                    popScope();
                }
                case MethodReferenceExpression r -> expr(r.receiver());
                case AwaitExpression a -> expr(a.operand());
                case SwitchExpression s -> {
                    expr(s.selector());
                    sections(s.sections());
                }
                case LiteralExpression l -> {
                }
                case NullLiteralExpression n -> {
                }
                case ThisExpression t -> {
                }
                case SuperExpression s -> {
                }
                case GcCollectExpression g -> {
                }
                case NativeNullExpression n -> {
                }
            }
        }

        boolean isStringExpr(Expression e) {
            if (e instanceof LiteralExpression lit) {
                return lit.kind() == LiteralExpression.Kind.STRING;
            }
            if (e instanceof VariableExpression v) {
                Local l = lookup(v.name());
                if (l != null) {
                    return l.isString();
                }
                FieldDeclaration f = fieldsByName.get(v.name());
                return f != null && !f.isArray() && f.type() != null && f.type().kind() == TypeName.Kind.CLASS
                        && "String".equals(f.type().name());
            }
            if (e instanceof GroupingExpression g) {
                return isStringExpr(g.expression());
            }
            return false;
        }

        void stringCompare(BinaryExpression b) {
            if (b.operator() != BinaryOp.EQ && b.operator() != BinaryOp.NE) {
                return;
            }
            if (b.left() instanceof NullLiteralExpression || b.right() instanceof NullLiteralExpression) {
                return;
            }
            if (isStringExpr(b.left()) && isStringExpr(b.right())) {
                report(b.location(), STRING_COMPARE,
                        "Strings are compared with '" + (b.operator() == BinaryOp.EQ ? "==" : "!=") + "'",
                        "Use equals() to compare string contents");
            }
        }

        void selfAssign(AssignmentExpression a) {
            if (a.postfix()) {
                return;
            }
            if (a.target() instanceof VariableExpression t && a.value() instanceof VariableExpression v
                    && t.name().equals(v.name())) {
                report(a.location(), SELF_ASSIGN, "'" + t.name() + "' is assigned to itself", "Remove the assignment");
            } else if (a.target() instanceof FieldAccessExpression t && a.value() instanceof FieldAccessExpression v
                    && t.receiver() instanceof ThisExpression && v.receiver() instanceof ThisExpression
                    && t.fieldName().equals(v.fieldName())) {
                report(a.location(), SELF_ASSIGN, "'this." + t.fieldName() + "' is assigned to itself", "Remove the assignment");
            }
        }

        // ----- reporting helpers

        int offsetOf(SourceLocation loc) {
            if (loc == null || loc.line() < 1 || loc.line() > lines.length) {
                return -1;
            }
            int off = 0;
            for (int i = 0; i < loc.line() - 1; i++) {
                off += lines[i].length() + 1;
            }
            return off + Math.max(0, loc.column() - 1);
        }

        /** Moves the location to the first occurrence of {@code name} on that line at or after the column. */
        SourceLocation refine(SourceLocation loc, String name) {
            if (loc == null || loc.line() < 1 || loc.line() > lines.length || name == null) {
                return loc;
            }
            String line = lines[loc.line() - 1];
            int from = Math.max(0, loc.column() - 1);
            int i = line.indexOf(name, from);
            while (i >= 0) {
                boolean left = i == 0 || !Character.isJavaIdentifierPart(line.charAt(i - 1));
                boolean right = i + name.length() >= line.length() || !Character.isJavaIdentifierPart(line.charAt(i + name.length()));
                if (left && right) {
                    return new SourceLocation(loc.file(), loc.line(), i + 1);
                }
                i = line.indexOf(name, i + 1);
            }
            return loc;
        }

        boolean suppressed(SourceLocation loc, String code) {
            if (loc == null || loc.line() < 1 || loc.line() > lines.length) {
                return false;
            }
            for (int ln = loc.line() - 1; ln >= Math.max(0, loc.line() - 2); ln--) {
                String l = lines[ln];
                int i = l.indexOf("noinspect");
                if (i >= 0 && l.lastIndexOf("//", i) >= 0) {
                    String rest = l.substring(i + "noinspect".length());
                    if (!rest.startsWith(":") || rest.contains(code)) {
                        return true;
                    }
                }
            }
            return false;
        }

        void report(SourceLocation loc, String code, String message, String hint) {
            if (loc == null || loc.line() < 1 || suppressed(loc, code)) {
                return;
            }
            if (!seen.add(code + "@" + loc.line() + ":" + loc.column())) {
                return;
            }
            out.report(new Diagnostic(Diagnostic.Severity.WARNING, loc, message, code, hint, List.of()));
        }
    }

    // ------------------------------------------------------------------------------------------ text helpers

    /** Identifier tokens of the source, skipping comments, string and char literals. */
    static List<String> identifierTokens(String s) {
        List<String> out = new ArrayList<>();
        int n = s.length();
        int i = 0;
        while (i < n) {
            char c = s.charAt(i);
            if (c == '/' && i + 1 < n && s.charAt(i + 1) == '/') {
                while (i < n && s.charAt(i) != '\n') {
                    i++;
                }
            } else if (c == '/' && i + 1 < n && s.charAt(i + 1) == '*') {
                i += 2;
                while (i + 1 < n && !(s.charAt(i) == '*' && s.charAt(i + 1) == '/')) {
                    i++;
                }
                i += 2;
            } else if (c == '"') {
                if (s.startsWith("\"\"\"", i)) {
                    i += 3;
                    while (i < n && !s.startsWith("\"\"\"", i)) {
                        i += s.charAt(i) == '\\' ? 2 : 1;
                    }
                    i += 3;
                } else {
                    i++;
                    while (i < n && s.charAt(i) != '"' && s.charAt(i) != '\n') {
                        i += s.charAt(i) == '\\' ? 2 : 1;
                    }
                    i++;
                }
            } else if (c == '\'') {
                i++;
                while (i < n && s.charAt(i) != '\'' && s.charAt(i) != '\n') {
                    i += s.charAt(i) == '\\' ? 2 : 1;
                }
                i++;
            } else if (Character.isJavaIdentifierStart(c)) {
                int st = i;
                while (i < n && Character.isJavaIdentifierPart(s.charAt(i))) {
                    i++;
                }
                out.add(s.substring(st, i));
            } else {
                i++;
            }
        }
        return out;
    }
}
