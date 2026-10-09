package io.jnative.lsp;

import io.jnative.ast.BlockStatement;
import io.jnative.ast.ClassDeclaration;
import io.jnative.ast.CompilationUnit;
import io.jnative.ast.EnhancedForStatement;
import io.jnative.ast.ForStatement;
import io.jnative.ast.IfStatement;
import io.jnative.ast.MethodDeclaration;
import io.jnative.ast.Parameter;
import io.jnative.ast.Statement;
import io.jnative.ast.TryStatement;
import io.jnative.ast.VariableDeclaration;
import io.jnative.ast.WhileStatement;
import io.jnative.compiler.Compilation;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.SemanticModel;
import io.jnative.compiler.SourceUnit;
import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.ClassType;
import io.jnative.semantic.FieldSymbol;
import io.jnative.semantic.InterfaceSymbol;
import io.jnative.semantic.JType;
import io.jnative.semantic.MethodSymbol;
import io.jnative.semantic.ParameterSymbol;
import io.jnative.semantic.VariableSymbol;
import io.jnative.semantic.Visibility;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Semantic completion / signature help over {@link JNativeCompiler#analyze}.
 * Patches incomplete editor buffers so local syntax errors do not kill all hints.
 * @author 张晓达
 */
public final class CompletionEngine {
    private final JNativeCompiler compiler;

    public CompletionEngine(JNativeCompiler compiler) {
        this.compiler = compiler;
    }

    public record Item(
            String label,
            String detail,
            String insertText,
            int kind, // LSP CompletionItemKind
            String filterText
    ) {
    }

    public record Signature(
            String label,
            List<String> parameters,
            int activeParameter
    ) {
    }

    public List<Item> complete(String path, String text, int line0, int col0) {
        int offset = offsetOf(text, line0, col0);
        offset = Math.max(0, Math.min(offset, text.length()));
        Context ctx = classify(text, offset);
        String patched = patchForAnalyze(text, offset, ctx);
        Compilation comp = safeAnalyze(path, patched);
        SemanticModel model = comp == null ? null : comp.semanticModel();
        String prefix = ctx.prefix();
        List<Item> items = new ArrayList<>();
        if (ctx.kind() == ContextKind.MEMBER || ctx.kind() == ContextKind.STATIC_MEMBER) {
            collectMembers(model, path, line0 + 1, col0 + 1, ctx, items);
        } else {
            collectScope(model, path, line0 + 1, col0 + 1, items);
            collectTypes(model, items);
        }
        if (prefix != null && !prefix.isEmpty()) {
            String p = prefix.toLowerCase(Locale.ROOT);
            items.removeIf(it -> {
                String f = it.filterText() != null ? it.filterText() : it.label();
                return f == null || !f.toLowerCase(Locale.ROOT).startsWith(p);
            });
        }
        // Deduplicate by label+detail
        Map<String, Item> uniq = new LinkedHashMap<>();
        for (Item it : items) {
            uniq.putIfAbsent(it.label() + "|" + it.detail(), it);
        }
        return new ArrayList<>(uniq.values());
    }

    public Signature signatureHelp(String path, String text, int line0, int col0) {
        int offset = offsetOf(text, line0, col0);
        offset = Math.max(0, Math.min(offset, text.length()));
        CallSite site = findCallSite(text, offset);
        if (site == null) {
            return null;
        }
        // Close unfinished '(' / '[' at the call site so trailing '}' stay valid.
        Compilation comp = safeAnalyze(path, patchBalanced(text, offset));
        SemanticModel model = comp == null ? null : comp.semanticModel();
        List<MethodSymbol> overloads = model == null
                ? List.of()
                : resolveOverloads(model, path, site);
        // Fallback: scan AST method declarations by name
        if (overloads.isEmpty() && model != null && model.typed() != null) {
            List<ClassDeclaration> classes = model.documentClasses();
            if (classes.isEmpty() && model.typed().unit() != null) {
                classes = model.typed().unit().classes();
            }
            for (ClassDeclaration cls : classes) {
                for (MethodDeclaration md : cls.methods()) {
                    if (!site.name().equals(md.name())) {
                        continue;
                    }
                    List<String> params = new ArrayList<>();
                    StringBuilder label = new StringBuilder(md.name()).append('(');
                    for (int i = 0; i < md.parameters().size(); i++) {
                        Parameter p = md.parameters().get(i);
                        String piece = (p.type() == null ? "?" : p.type().toString()) + " " + p.name();
                        params.add(piece);
                        if (i > 0) {
                            label.append(", ");
                        }
                        label.append(piece);
                    }
                    label.append(')');
                    int active = params.isEmpty()
                            ? 0
                            : Math.min(site.argIndex(), params.size() - 1);
                    return new Signature(label.toString(), params, active);
                }
            }
            // Symbol-table fallback (user types may parse even when unit().classes is empty)
            for (ClassSymbol cls : model.classes().values()) {
                for (MethodSymbol m : cls.declaredMethods()) {
                    if (site.name().equals(m.name())) {
                        overloads = List.of(m);
                        break;
                    }
                }
                if (!overloads.isEmpty()) {
                    break;
                }
            }
        }
        if (overloads.isEmpty()) {
            return null;
        }
        MethodSymbol m = overloads.get(0);
        List<String> params = new ArrayList<>();
        StringBuilder label = new StringBuilder(m.name()).append('(');
        for (int i = 0; i < m.parameters().size(); i++) {
            ParameterSymbol p = m.parameters().get(i);
            String piece = typeName(p.type()) + " " + p.name();
            params.add(piece);
            if (i > 0) {
                label.append(", ");
            }
            label.append(piece);
        }
        label.append(')');
        if (m.returnType() != null) {
            label.append(": ").append(typeName(m.returnType()));
        }
        int active = Math.min(Math.max(0, site.argIndex()), Math.max(0, params.size() - 1));
        if (params.isEmpty()) {
            active = 0;
        }
        return new Signature(label.toString(), params, active);
    }

    private Compilation safeAnalyze(String path, String text) {
        try {
            String p = path == null || path.isBlank() ? "Memory.java" : path;
            return compiler.analyze(SourceUnit.ofPath(Path.of(p), text), 1L);
        } catch (Throwable t) {
            System.err.println("jnative lsp: analyze for completion failed: " + t.getMessage());
            return null;
        }
    }

    private void collectScope(SemanticModel model, String path, int line1, int col1, List<Item> out) {
        Set<String> seen = new LinkedHashSet<>();
        // Locals / params from AST (declared even if never referenced yet)
        CompilationUnit unit = model != null && model.typed() != null ? model.typed().unit() : null;
        if (unit != null) {
            collectLocalsFromAst(unit, line1, seen, out);
        }
        if (model != null && model.typed() != null) {
            for (Object sym : model.typed().resolvedSymbols().values()) {
                if (sym instanceof VariableSymbol vs) {
                    if (vs.location() != null && vs.location().line() > line1) {
                        continue;
                    }
                    if (!seen.add(vs.name())) {
                        continue;
                    }
                    String kind = vs.kind() == VariableSymbol.Kind.PARAMETER ? "param" : "local";
                    out.add(new Item(
                            vs.name(),
                            kind + ": " + typeName(vs.type()),
                            vs.name(),
                            6,
                            vs.name()));
                }
            }
            model.symbolAt(path, line1, col1).ifPresent(sym -> {
                ClassSymbol cls = enclosingClass(sym);
                if (cls == null) {
                    return;
                }
                for (FieldSymbol f : allFields(cls)) {
                    if (!accessible(f.visibility(), cls, cls) || f.isStatic()) {
                        continue;
                    }
                    if (!seen.add(f.name())) {
                        continue;
                    }
                    out.add(new Item(f.name(), "field: " + typeName(f.type()), f.name(), 5, f.name()));
                }
                for (MethodSymbol m : allMethods(cls)) {
                    if (!accessible(m.visibility(), ownerClass(m), cls) || m.isStatic()) {
                        continue;
                    }
                    String key = m.name() + sigKey(m);
                    if (!seen.add(key)) {
                        continue;
                    }
                    out.add(methodItem(m));
                }
            });
            // Static methods of current top-level classes (e.g. helpers in Main)
            for (ClassSymbol cls : model.classes().values()) {
                if (cls.isBuiltin()) {
                    continue;
                }
                for (MethodSymbol m : cls.declaredMethods()) {
                    if (!m.isStatic()) {
                        continue;
                    }
                    String key = m.name() + sigKey(m);
                    if (!seen.add(key)) {
                        continue;
                    }
                    out.add(methodItem(m));
                }
            }
        }
    }

    private void collectLocalsFromAst(
            CompilationUnit unit, int line1, Set<String> seen, List<Item> out
    ) {
        for (ClassDeclaration cls : unit.classes()) {
            for (MethodDeclaration m : cls.methods()) {
                if (m.location() != null && m.location().line() > line1) {
                    continue;
                }
                for (Parameter p : m.parameters()) {
                    if (p.location() != null && p.location().line() > line1) {
                        continue;
                    }
                    if (!seen.add(p.name())) {
                        continue;
                    }
                    String ty = p.type() == null ? "?" : p.type().toString();
                    out.add(new Item(p.name(), "param: " + ty, p.name(), 6, p.name()));
                }
                if (m.body() != null) {
                    walkStatements(m.body().statements(), line1, seen, out);
                }
            }
        }
    }

    private void walkStatements(
            List<Statement> stmts, int line1, Set<String> seen, List<Item> out
    ) {
        if (stmts == null) {
            return;
        }
        for (Statement s : stmts) {
            switch (s) {
                case VariableDeclaration v -> {
                    if (v.location() != null && v.location().line() > line1) {
                        break;
                    }
                    if (seen.add(v.name())) {
                        String ty = v.type() == null ? "?" : v.type().toString();
                        out.add(new Item(v.name(), "local: " + ty, v.name(), 6, v.name()));
                    }
                }
                case BlockStatement b -> walkStatements(b.statements(), line1, seen, out);
                case IfStatement i -> {
                    if (i.thenBranch() instanceof BlockStatement tb) {
                        walkStatements(tb.statements(), line1, seen, out);
                    }
                    if (i.elseBranch() instanceof BlockStatement eb) {
                        walkStatements(eb.statements(), line1, seen, out);
                    }
                }
                case WhileStatement w -> {
                    if (w.body() instanceof BlockStatement b) {
                        walkStatements(b.statements(), line1, seen, out);
                    }
                }
                case ForStatement f -> {
                    if (f.initializer() instanceof VariableDeclaration vd && seen.add(vd.name())) {
                        String ty = vd.type() == null ? "?" : vd.type().toString();
                        out.add(new Item(vd.name(), "local: " + ty, vd.name(), 6, vd.name()));
                    }
                    if (f.body() instanceof BlockStatement b) {
                        walkStatements(b.statements(), line1, seen, out);
                    }
                }
                case EnhancedForStatement ef -> {
                    if (seen.add(ef.variableName())) {
                        out.add(new Item(ef.variableName(), "local", ef.variableName(), 6, ef.variableName()));
                    }
                    if (ef.body() instanceof BlockStatement b) {
                        walkStatements(b.statements(), line1, seen, out);
                    }
                }
                case TryStatement t -> {
                    if (t.tryBlock() != null) {
                        walkStatements(t.tryBlock().statements(), line1, seen, out);
                    }
                }
                default -> {
                }
            }
        }
    }

    private void collectTypes(SemanticModel model, List<Item> out) {
        if (model == null) {
            return;
        }
        for (ClassSymbol c : model.classes().values()) {
            if (c.name().startsWith("__")) {
                continue;
            }
            out.add(new Item(c.name(), c.isBuiltin() ? "class (builtin)" : "class", c.name(), 7, c.name()));
        }
        for (InterfaceSymbol i : model.typed().interfaces().values()) {
            out.add(new Item(i.name(), "interface", i.name(), 8, i.name()));
        }
    }

    private void collectMembers(
            SemanticModel model,
            String path,
            int line1,
            int col1,
            Context ctx,
            List<Item> out
    ) {
        if (model == null) {
            return;
        }
        ClassSymbol from = enclosingClass(model.symbolAt(path, line1, col1).orElse(null));
        boolean wantStatic = ctx.kind() == ContextKind.STATIC_MEMBER;
        ClassSymbol typeCls = null;
        if (wantStatic && ctx.receiverName() != null) {
            typeCls = model.classes().get(ctx.receiverName());
        } else if (ctx.receiverName() != null) {
            // Find variable/field type by name among resolved symbols
            for (Object sym : model.typed().resolvedSymbols().values()) {
                if (sym instanceof VariableSymbol vs && vs.name().equals(ctx.receiverName())) {
                    typeCls = asClass(vs.type(), model);
                    break;
                }
                if (sym instanceof FieldSymbol fs && fs.name().equals(ctx.receiverName())) {
                    typeCls = asClass(fs.type(), model);
                    break;
                }
            }
            if (typeCls == null) {
                // Fallback: class name used as value? treat as static
                typeCls = model.classes().get(ctx.receiverName());
                if (typeCls != null) {
                    wantStatic = true;
                }
            }
        }
        if (typeCls == null) {
            // Try typeAt near the receiver
            model.typeAt(path, line1, Math.max(1, col1 - Math.max(1, ctx.prefix().length()) - 1))
                    .ifPresent(t -> {
                    });
            JType t = model.typeAt(path, line1, Math.max(1, col1 - 1)).orElse(null);
            typeCls = asClass(t, model);
        }
        if (typeCls == null) {
            return;
        }
        ClassSymbol accessFrom = from != null ? from : typeCls;
        for (FieldSymbol f : allFields(typeCls)) {
            if (f.isStatic() != wantStatic) {
                continue;
            }
            if (!accessible(f.visibility(), f.owner(), accessFrom)) {
                continue;
            }
            out.add(new Item(f.name(),
                    (f.isStatic() ? "static " : "") + "field: " + typeName(f.type()),
                    f.name(), 5, f.name()));
        }
        for (MethodSymbol m : allMethods(typeCls)) {
            if (m.isStatic() != wantStatic) {
                continue;
            }
            if (!accessible(m.visibility(), ownerClass(m), accessFrom)) {
                continue;
            }
            out.add(methodItem(m));
        }
    }

    private static Item methodItem(MethodSymbol m) {
        StringBuilder sig = new StringBuilder(m.name()).append('(');
        List<ParameterSymbol> ps = m.parameters();
        for (int i = 0; i < ps.size(); i++) {
            if (i > 0) {
                sig.append(", ");
            }
            sig.append(typeName(ps.get(i).type())).append(' ').append(ps.get(i).name());
        }
        sig.append("): ").append(typeName(m.returnType()));
        String insert;
        if (ps.isEmpty()) {
            insert = m.name() + "()";
        } else {
            // Snippet with placeholders — VS Code insertTextFormat=Snippet when `$` present
            StringBuilder sn = new StringBuilder(m.name()).append('(');
            for (int i = 0; i < ps.size(); i++) {
                if (i > 0) {
                    sn.append(", ");
                }
                sn.append("${").append(i + 1).append(":").append(ps.get(i).name()).append('}');
            }
            sn.append(')');
            insert = sn.toString();
        }
        return new Item(m.name(), (m.isStatic() ? "static " : "") + sig,
                insert, 2 /* Method */, m.name());
    }

    private List<MethodSymbol> resolveOverloads(SemanticModel model, String path, CallSite site) {
        List<MethodSymbol> out = new ArrayList<>();
        if (site.receiver() != null) {
            ClassSymbol cls = null;
            for (Object sym : model.typed().resolvedSymbols().values()) {
                if (sym instanceof VariableSymbol vs && vs.name().equals(site.receiver())) {
                    cls = asClass(vs.type(), model);
                    break;
                }
            }
            if (cls == null) {
                cls = model.classes().get(site.receiver());
            }
            if (cls != null) {
                out.addAll(cls.findMethods(site.name()));
            }
        } else {
            model.symbolAt(path, site.line1(), site.col1()).ifPresent(sym -> {
                ClassSymbol cls = enclosingClass(sym);
                if (cls != null) {
                    out.addAll(cls.findMethods(site.name()));
                }
            });
            if (out.isEmpty()) {
                for (ClassSymbol c : model.classes().values()) {
                    for (MethodSymbol m : c.findMethods(site.name())) {
                        if (m.isStatic() || "main".equals(m.name())) {
                            out.add(m);
                        }
                    }
                }
            }
        }
        return out;
    }

    private enum ContextKind { SCOPE, MEMBER, STATIC_MEMBER }

    private record Context(ContextKind kind, String prefix, String receiverName) {
    }

    private record CallSite(String name, String receiver, int nameEnd, int argIndex, int line1, int col1) {
    }

    private static Context classify(String text, int offset) {
        int i = offset - 1;
        while (i >= 0 && Character.isWhitespace(text.charAt(i))) {
            i--;
        }
        int end = i + 1;
        while (i >= 0 && isIdentPart(text.charAt(i))) {
            i--;
        }
        String prefix = text.substring(i + 1, Math.max(i + 1, end));
        int j = i;
        while (j >= 0 && Character.isWhitespace(text.charAt(j))) {
            j--;
        }
        if (j >= 0 && text.charAt(j) == '.') {
            int k = j - 1;
            while (k >= 0 && Character.isWhitespace(text.charAt(k))) {
                k--;
            }
            int recvEnd = k + 1;
            while (k >= 0 && isIdentPart(text.charAt(k))) {
                k--;
            }
            String recv = text.substring(k + 1, recvEnd);
            boolean maybeType = !recv.isEmpty() && Character.isUpperCase(recv.charAt(0));
            return new Context(maybeType ? ContextKind.STATIC_MEMBER : ContextKind.MEMBER, prefix, recv);
        }
        return new Context(ContextKind.SCOPE, prefix, null);
    }

    private static String patchForAnalyze(String text, int offset, Context ctx) {
        StringBuilder sb = new StringBuilder(text);
        if (ctx.kind() == ContextKind.MEMBER || ctx.kind() == ContextKind.STATIC_MEMBER) {
            // Ensure `recv.` / `recv.pre` becomes a parseable member access
            if (offset <= sb.length()) {
                if (ctx.prefix().isEmpty()) {
                    sb.insert(offset, "_jn");
                    offset += 3;
                }
                // Terminate the expression so the rest of the method can parse
                sb.insert(offset + ctx.prefix().length(), ";");
            }
        } else if (ctx.kind() == ContextKind.SCOPE && !ctx.prefix().isEmpty()) {
            // `an` → `an;` so prior locals remain in a recoverable statement list
            if (offset <= sb.length()) {
                sb.insert(offset, ";");
            }
        }
        return patchBalanced(sb.toString(), Math.min(offset, sb.length()));
    }

    /** Close unfinished brackets; missing {@code )} / {@code ]} go at {@code insertAt}. */
    static String patchBalanced(String text) {
        return patchBalanced(text, text.length());
    }

    /**
     * Insert missing {@code )} / {@code ]} at {@code insertAt} (cursor), then append missing {@code }}.
     * Appending parens at EOF breaks {@code add(1,\n}} into {@code add(1,\n}})}.
     */
    static String patchBalanced(String text, int insertAt) {
        int paren = 0;
        int brace = 0;
        int bracket = 0;
        boolean inStr = false;
        boolean inChar = false;
        boolean inLine = false;
        boolean inBlock = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char n = i + 1 < text.length() ? text.charAt(i + 1) : 0;
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && n == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (inStr) {
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (c == '"') {
                    inStr = false;
                }
                continue;
            }
            if (inChar) {
                if (c == '\\') {
                    i++;
                    continue;
                }
                if (c == '\'') {
                    inChar = false;
                }
                continue;
            }
            if (c == '/' && n == '/') {
                inLine = true;
                i++;
                continue;
            }
            if (c == '/' && n == '*') {
                inBlock = true;
                i++;
                continue;
            }
            if (c == '"') {
                inStr = true;
                continue;
            }
            if (c == '\'') {
                inChar = true;
                continue;
            }
            switch (c) {
                case '(' -> paren++;
                case ')' -> paren--;
                case '{' -> brace++;
                case '}' -> brace--;
                case '[' -> bracket++;
                case ']' -> bracket--;
                default -> {
                }
            }
        }
        StringBuilder sb = new StringBuilder(text);
        // If ends with '.' leave a dummy member for parse recovery
        String trimmedEnd = sb.toString().replaceAll("\\s+$", "");
        if (trimmedEnd.endsWith(".")) {
            sb = new StringBuilder(trimmedEnd).append("_jn");
            insertAt = Math.min(insertAt, sb.length());
        }
        int at = Math.max(0, Math.min(insertAt, sb.length()));
        StringBuilder mid = new StringBuilder();
        while (paren-- > 0) {
            mid.append(')');
        }
        while (bracket-- > 0) {
            mid.append(']');
        }
        if (!mid.isEmpty()) {
            // `add(1,` → insert dummy arg so `add(1,0)` parses; bare `add(` → `add()`
            int j = at - 1;
            while (j >= 0 && Character.isWhitespace(sb.charAt(j))) {
                j--;
            }
            if (j >= 0 && sb.charAt(j) == ',') {
                sb.insert(at, "0");
                at += 1;
            }
            sb.insert(at, mid);
            at += mid.length();
            // Expression statement needs ';' before following '}'
            int k = at;
            while (k < sb.length() && Character.isWhitespace(sb.charAt(k))) {
                k++;
            }
            if (k >= sb.length() || sb.charAt(k) == '}') {
                sb.insert(at, ';');
            }
        }
        while (brace-- > 0) {
            sb.append('}');
        }
        return sb.toString();
    }

    private static CallSite findCallSite(String text, int offset) {
        int depth = 0;
        int argIndex = 0;
        for (int i = offset - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (c == ')') {
                depth++;
            } else if (c == '(') {
                if (depth == 0) {
                    // walk back for name
                    int j = i - 1;
                    while (j >= 0 && Character.isWhitespace(text.charAt(j))) {
                        j--;
                    }
                    int nameEnd = j + 1;
                    while (j >= 0 && isIdentPart(text.charAt(j))) {
                        j--;
                    }
                    String name = text.substring(j + 1, nameEnd);
                    if (name.isEmpty()) {
                        return null;
                    }
                    String recv = null;
                    int k = j;
                    while (k >= 0 && Character.isWhitespace(text.charAt(k))) {
                        k--;
                    }
                    if (k >= 0 && text.charAt(k) == '.') {
                        int r = k - 1;
                        while (r >= 0 && Character.isWhitespace(text.charAt(r))) {
                            r--;
                        }
                        int recvEnd = r + 1;
                        while (r >= 0 && isIdentPart(text.charAt(r))) {
                            r--;
                        }
                        recv = text.substring(r + 1, recvEnd);
                    }
                    int[] lc = lineColOf(text, i);
                    // count commas at depth 0 between ( and offset
                    int ai = 0;
                    int d = 0;
                    for (int p = i + 1; p < offset; p++) {
                        char ch = text.charAt(p);
                        if (ch == '(') {
                            d++;
                        } else if (ch == ')') {
                            d--;
                        } else if (ch == ',' && d == 0) {
                            ai++;
                        }
                    }
                    return new CallSite(name, recv, nameEnd, ai, lc[0], lc[1]);
                }
                depth--;
            } else if (c == ',' && depth == 0) {
                argIndex++;
            }
        }
        return null;
    }

    private static ClassSymbol asClass(JType t, SemanticModel model) {
        if (t == null) {
            return null;
        }
        if (t instanceof ClassType ct) {
            return ct.classSymbol();
        }
        return model.classes().get(t.toString());
    }

    private static ClassSymbol enclosingClass(Object sym) {
        if (sym instanceof ClassSymbol c) {
            return c;
        }
        if (sym instanceof MethodSymbol m) {
            return ownerClass(m);
        }
        if (sym instanceof FieldSymbol f) {
            return f.owner();
        }
        if (sym instanceof VariableSymbol) {
            return null;
        }
        return null;
    }

    private static ClassSymbol ownerClass(MethodSymbol m) {
        if (m.owner() instanceof ClassSymbol c) {
            return c;
        }
        return null;
    }

    private static List<FieldSymbol> allFields(ClassSymbol cls) {
        List<FieldSymbol> out = new ArrayList<>();
        ClassSymbol c = cls;
        while (c != null) {
            out.addAll(c.declaredFields());
            c = c.superClass().orElse(null);
        }
        return out;
    }

    private static List<MethodSymbol> allMethods(ClassSymbol cls) {
        Set<String> seen = new LinkedHashSet<>();
        List<MethodSymbol> out = new ArrayList<>();
        for (MethodSymbol m : cls.declaredMethods()) {
            String k = m.name() + sigKey(m);
            if (seen.add(k)) {
                out.add(m);
            }
        }
        ClassSymbol parent = cls.superClass().orElse(null);
        if (parent != null) {
            for (MethodSymbol m : allMethods(parent)) {
                String k = m.name() + sigKey(m);
                if (seen.add(k)) {
                    out.add(m);
                }
            }
        }
        return out;
    }

    private static String sigKey(MethodSymbol m) {
        StringBuilder sb = new StringBuilder("(");
        for (ParameterSymbol p : m.parameters()) {
            sb.append(typeName(p.type())).append(',');
        }
        sb.append(')');
        return sb.toString();
    }

    private static boolean accessible(Visibility v, ClassSymbol declaring, ClassSymbol from) {
        if (v == null) {
            return true;
        }
        if (declaring == null || from == null) {
            return v == Visibility.PUBLIC;
        }
        boolean subtype = from.isSubtypeOf(declaring);
        return v.accessibleFrom(
                packageOf(declaring.name()),
                declaring.name(),
                packageOf(from.name()),
                from.name(),
                subtype);
    }

    private static String packageOf(String fqn) {
        if (fqn == null) {
            return "";
        }
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? "" : fqn.substring(0, dot);
    }

    private static String typeName(JType t) {
        return t == null ? "?" : t.toString();
    }

    private static boolean isIdentPart(char c) {
        return Character.isJavaIdentifierPart(c);
    }

    static int offsetOf(String text, int line0, int col0) {
        int line = 0;
        int i = 0;
        while (i < text.length() && line < line0) {
            if (text.charAt(i) == '\n') {
                line++;
            }
            i++;
        }
        return Math.min(text.length(), i + Math.max(0, col0));
    }

    static int[] lineColOf(String text, int offset) {
        int line = 1;
        int col = 1;
        for (int i = 0; i < offset && i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                line++;
                col = 1;
            } else {
                col++;
            }
        }
        return new int[]{line, col};
    }
}
