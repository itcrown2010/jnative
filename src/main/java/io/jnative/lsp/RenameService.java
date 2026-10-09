package io.jnative.lsp;

import io.jnative.ast.BlockStatement;
import io.jnative.ast.ClassDeclaration;
import io.jnative.ast.MethodDeclaration;
import io.jnative.ast.Parameter;
import io.jnative.ast.Statement;
import io.jnative.ast.VariableDeclaration;
import io.jnative.compiler.Compilation;
import io.jnative.compiler.SemanticModel;
import io.jnative.compiler.TextRange;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.ConstructorSymbol;
import io.jnative.semantic.FieldSymbol;
import io.jnative.semantic.InterfaceSymbol;
import io.jnative.semantic.MethodSymbol;
import io.jnative.semantic.ParameterSymbol;
import io.jnative.semantic.VariableSymbol;
import io.jnative.semantic.Visibility;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Safe semantic rename via {@link SemanticModel#referencesOf} — never string-replace.
 * @author 张晓达
 */
public final class RenameService {
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final IdeServices ide;

    public RenameService(IdeServices ide) {
        this.ide = ide;
    }

    public record Prepare(boolean ok, String placeholder, int startLine0, int startCol0,
                          int endLine0, int endCol0, String error) {
        public static Prepare fail(String error) {
            return new Prepare(false, null, 0, 0, 0, 0, error);
        }

        public static Prepare ok(String name, int sl, int sc, int el, int ec) {
            return new Prepare(true, name, sl, sc, el, ec, null);
        }
    }

    public record Edit(String file, int startLine0, int startCol0, int endLine0, int endCol0, String newText) {
    }

    public record RenameResult(boolean ok, List<Edit> edits, String error) {
        public static RenameResult fail(String error) {
            return new RenameResult(false, List.of(), error);
        }

        public static RenameResult ok(List<Edit> edits) {
            return new RenameResult(true, edits, null);
        }
    }

    public Prepare prepare(
            String path, String text, int line0, int col0, int version, Map<String, String> overlays
    ) {
        // requireFresh waits on the per-project lock — never rename from a foreign stale snapshot
        Compilation comp = ide.analyzeCached(path, text, version, overlays, true);
        SemanticModel model = comp == null ? null : comp.semanticModel();
        if (model == null) {
            return Prepare.fail("无法分析当前文档，拒绝重命名");
        }
        Optional<Object> symOpt = model.symbolAt(path, line0 + 1, col0 + 1);
        if (symOpt.isEmpty()) {
            return Prepare.fail("此处没有可重命名的符号");
        }
        Object sym = symOpt.get();
        String reason = refuseReason(sym, path, overlays);
        if (reason != null) {
            return Prepare.fail(reason);
        }
        String name = symbolName(sym);
        if (name == null || name.isBlank()) {
            return Prepare.fail("无法确定符号名称");
        }
        // Prefer declaration range; fall back to identifier under cursor in text
        Optional<TextRange> decl = model.declarationOf(sym);
        int[] span = identifierSpanAt(text, line0, col0, name);
        if (decl.isPresent() && samePath(decl.get().file(), path)) {
            TextRange r = decl.get();
            // If cursor is on a use, still return the use span under cursor for prepareRename UX
            if (span != null) {
                return Prepare.ok(name, span[0], span[1], span[2], span[3]);
            }
            int endCol = r.startColumn() + name.length();
            return Prepare.ok(name,
                    Math.max(0, r.startLine() - 1), Math.max(0, r.startColumn() - 1),
                    Math.max(0, r.startLine() - 1), Math.max(0, endCol - 1));
        }
        if (span == null) {
            return Prepare.fail("无法定位标识符范围");
        }
        return Prepare.ok(name, span[0], span[1], span[2], span[3]);
    }

    public RenameResult rename(
            String path, String text, int line0, int col0, String newName,
            int version, Map<String, String> overlays
    ) {
        if (newName == null || !IDENT.matcher(newName).matches()) {
            return RenameResult.fail("非法标识符: " + newName);
        }
        // Always fresh analyze for mutating edits (serialized per project scope)
        Compilation comp = ide.analyzeCached(path, text, version, overlays, true);
        SemanticModel model = comp == null ? null : comp.semanticModel();
        if (model == null) {
            return RenameResult.fail("无法分析当前文档，拒绝重命名");
        }
        Optional<Object> symOpt = model.symbolAt(path, line0 + 1, col0 + 1);
        if (symOpt.isEmpty()) {
            return RenameResult.fail("此处没有可重命名的符号");
        }
        Object sym = symOpt.get();
        String reason = refuseReason(sym, path, overlays);
        if (reason != null) {
            return RenameResult.fail(reason);
        }
        String oldName = symbolName(sym);
        if (oldName == null) {
            return RenameResult.fail("无法确定符号名称");
        }
        if (oldName.equals(newName)) {
            return RenameResult.ok(List.of());
        }
        String conflict = conflictReason(model, sym, newName);
        if (conflict != null) {
            return RenameResult.fail(conflict);
        }
        List<TextRange> refs = model.referencesOf(sym);
        if (refs.isEmpty()) {
            return RenameResult.fail("未找到语义引用，拒绝重命名");
        }
        Map<String, String> fileTexts = new LinkedHashMap<>();
        if (overlays != null) {
            fileTexts.putAll(overlays);
        }
        fileTexts.put(Path.of(path).toAbsolutePath().normalize().toString(), text);

        List<Edit> edits = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (TextRange r : refs) {
            if (r.file() == null || r.startLine() <= 0 || r.startColumn() <= 0) {
                continue;
            }
            if (isReadonlyPath(r.file(), overlays)) {
                return RenameResult.fail("引用落在第三方/只读源码，拒绝重命名: " + r.file());
            }
            String fileKey = Path.of(r.file()).toAbsolutePath().normalize().toString();
            String fileText = fileTexts.get(fileKey);
            if (fileText == null) {
                try {
                    fileText = Files.readString(Path.of(fileKey));
                    fileTexts.put(fileKey, fileText);
                } catch (Exception e) {
                    return RenameResult.fail("无法读取: " + r.file());
                }
            }
            int[] span = resolveNameSpan(fileText, r, oldName);
            if (span == null) {
                return RenameResult.fail("无法在 "
                        + r.file() + ":" + r.startLine() + " 对齐标识符 '" + oldName + "'，拒绝重命名");
            }
            String key = fileKey + ":" + span[0] + ":" + span[1] + ":" + span[2] + ":" + span[3];
            if (!seen.add(key)) {
                continue;
            }
            // Verify exact text equals oldName (never touch strings/comments — span is from semantic loc)
            String slice = slice(fileText, span[0], span[1], span[2], span[3]);
            if (!oldName.equals(slice)) {
                return RenameResult.fail("源码与符号名不一致（可能已过期），请重试: 期望 '"
                        + oldName + "' 实际 '" + slice + "'");
            }
            edits.add(new Edit(fileKey, span[0], span[1], span[2], span[3], newName));
        }
        if (edits.isEmpty()) {
            return RenameResult.fail("没有可应用的编辑");
        }
        // Apply order: bottom-to-top within file so offsets stay valid when client applies sequentially
        edits.sort((a, b) -> {
            int c = a.file().compareTo(b.file());
            if (c != 0) {
                return c;
            }
            if (a.startLine0() != b.startLine0()) {
                return Integer.compare(b.startLine0(), a.startLine0());
            }
            return Integer.compare(b.startCol0(), a.startCol0());
        });
        return RenameResult.ok(edits);
    }

    private static String refuseReason(Object sym, String activePath, Map<String, String> overlays) {
        if (sym instanceof ConstructorSymbol) {
            return "构造器重命名不受支持（与类型名绑定）";
        }
        if (sym instanceof MethodSymbol m) {
            if (m.overrides() != null) {
                return "该方法覆盖父类/接口成员，重命名不可靠，已拒绝";
            }
            if (m.isVirtual() && m.visibility() != Visibility.PRIVATE) {
                // May be overridden elsewhere — refuse unless static/private
                return "非 private 实例方法可能参与继承/覆盖，重命名已拒绝（请仅重命名 private/static 方法，或使用更完整的重构）";
            }
            Optional<TextRange> decl = Optional.ofNullable(m.location()).map(TextRange::of);
            if (decl.isPresent() && isReadonlyPath(decl.get().file(), overlays)) {
                return "方法定义位于第三方/只读源码，拒绝重命名";
            }
            return null;
        }
        if (sym instanceof FieldSymbol f) {
            if (f.location() != null && isReadonlyPath(f.location().file(), overlays)) {
                return "字段定义位于第三方/只读源码，拒绝重命名";
            }
            return null;
        }
        if (sym instanceof VariableSymbol) {
            return null;
        }
        if (sym instanceof ParameterSymbol) {
            return null;
        }
        if (sym instanceof ClassSymbol c) {
            if (c.isBuiltin()) {
                return "不能重命名内建类型";
            }
            if (c.location() != null && isReadonlyPath(c.location().file(), overlays)) {
                return "类型定义位于第三方/只读源码，拒绝重命名";
            }
            // Type rename in-place; file rename not required by JNative (public class name need not match file)
            return null;
        }
        if (sym instanceof InterfaceSymbol) {
            return "接口重命名暂不支持";
        }
        return "不支持重命名该符号类别";
    }

    private static String conflictReason(SemanticModel model, Object sym, String newName) {
        if (sym instanceof VariableSymbol vs) {
            for (Object other : model.typed().resolvedSymbols().values()) {
                if (other instanceof VariableSymbol o && o != vs && newName.equals(o.name())) {
                    if (sameEnclosingRough(vs.location(), o.location())) {
                        return "作用域内已存在 '" + newName + "'";
                    }
                }
            }
            // Locals may be unreferenced and absent from resolvedSymbols — scan AST
            if (model.typed().unit() != null && vs.location() != null) {
                for (ClassDeclaration cls : model.typed().unit().classes()) {
                    for (MethodDeclaration md : cls.methods()) {
                        if (!methodContains(md, vs.location())) {
                            continue;
                        }
                        for (Parameter p : md.parameters()) {
                            if (newName.equals(p.name()) && !newName.equals(vs.name())) {
                                return "作用域内已存在参数 '" + newName + "'";
                            }
                        }
                        if (md.body() != null && blockDeclares(md.body(), newName, vs.name())) {
                            return "作用域内已存在 '" + newName + "'";
                        }
                    }
                }
            }
            return null;
        }
        if (sym instanceof FieldSymbol f) {
            ClassSymbol owner = f.owner();
            if (owner != null) {
                for (FieldSymbol of : owner.declaredFields()) {
                    if (of != f && newName.equals(of.name())) {
                        return "类型中已存在字段 '" + newName + "'";
                    }
                }
            }
            return null;
        }
        if (sym instanceof MethodSymbol m) {
            ClassSymbol owner = m.owner() instanceof ClassSymbol c ? c : null;
            if (owner != null) {
                for (MethodSymbol om : owner.declaredMethods()) {
                    if (om != m && newName.equals(om.name())
                            && om.parameters().size() == m.parameters().size()) {
                        boolean same = true;
                        for (int i = 0; i < m.parameters().size(); i++) {
                            var a = m.parameters().get(i).type();
                            var b = om.parameters().get(i).type();
                            if (a == null || b == null || !a.toString().equals(b.toString())) {
                                same = false;
                                break;
                            }
                        }
                        if (same) {
                            return "已存在相同签名的方法 '" + newName + "'";
                        }
                    }
                }
            }
            return null;
        }
        if (sym instanceof ClassSymbol c) {
            for (ClassSymbol other : model.classes().values()) {
                if (other != c && !other.isBuiltin()) {
                    String sn = other.name();
                    int dot = sn.lastIndexOf('.');
                    String simple = dot < 0 ? sn : sn.substring(dot + 1);
                    if (newName.equals(simple) || newName.equals(sn)) {
                        return "已存在类型 '" + newName + "'";
                    }
                }
            }
        }
        return null;
    }

    private static boolean sameEnclosingRough(SourceLocation a, SourceLocation b) {
        if (a == null || b == null) {
            return false;
        }
        if (a.file() == null || !a.file().equals(b.file())) {
            return false;
        }
        // Same file — treat as potential conflict (conservative for locals)
        return Math.abs(a.line() - b.line()) < 400;
    }

    private static boolean methodContains(MethodDeclaration md, SourceLocation loc) {
        if (md == null || loc == null || md.location() == null) {
            return false;
        }
        if (md.location().file() != null && loc.file() != null
                && !md.location().file().equals(loc.file())
                && !md.location().file().endsWith(loc.file())
                && !loc.file().endsWith(md.location().file())) {
            return false;
        }
        return loc.line() >= md.location().line();
    }

    private static boolean blockDeclares(Statement stmt, String newName, String oldName) {
        if (stmt == null) {
            return false;
        }
        if (stmt instanceof VariableDeclaration v) {
            return newName.equals(v.name()) && !oldName.equals(v.name());
        }
        if (stmt instanceof BlockStatement b) {
            for (Statement s : b.statements()) {
                if (blockDeclares(s, newName, oldName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isReadonlyPath(String file, Map<String, String> overlays) {
        if (file == null || file.isBlank() || "<stdlib>".equals(file) || "<unknown>".equals(file)) {
            return true;
        }
        String norm = file.replace('\\', '/');
        if (norm.contains("/.jnative/pkg-cache/") || norm.contains("/stdlib/")
                || norm.contains("/target/generated")) {
            return true;
        }
        if (overlays != null) {
            for (String k : overlays.keySet()) {
                if (samePath(k, file)) {
                    return false; // open / unsaved buffer
                }
            }
        }
        Path p = Path.of(file);
        try {
            if (!Files.exists(p)) {
                // In-memory analyze path (e.g. Main.java) — editable unless cache-like
                return false;
            }
            return !Files.isRegularFile(p) || !Files.isWritable(p);
        } catch (Exception e) {
            return true;
        }
    }

    private static String symbolName(Object sym) {
        if (sym instanceof VariableSymbol v) {
            return v.name();
        }
        if (sym instanceof ParameterSymbol p) {
            return p.name();
        }
        if (sym instanceof FieldSymbol f) {
            return f.name();
        }
        if (sym instanceof MethodSymbol m) {
            return m.name();
        }
        if (sym instanceof ClassSymbol c) {
            String n = c.name();
            int dot = n.lastIndexOf('.');
            return dot < 0 ? n : n.substring(dot + 1);
        }
        return null;
    }

    /** line0/col0 span for identifier at cursor matching expected name. */
    private static int[] identifierSpanAt(String text, int line0, int col0, String name) {
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        if (line0 < 0 || line0 >= lines.length) {
            return null;
        }
        String line = lines[line0];
        int c = Math.min(Math.max(0, col0), line.length());
        int start = c;
        while (start > 0 && isIdentPart(line.charAt(start - 1))) {
            start--;
        }
        int end = c;
        while (end < line.length() && isIdentPart(line.charAt(end))) {
            end++;
        }
        if (start >= end) {
            return null;
        }
        String id = line.substring(start, end);
        if (name != null && !name.equals(id)) {
            // cursor may be on keyword near symbol — search name on line
            int idx = line.indexOf(name);
            if (idx >= 0 && (idx == 0 || !isIdentPart(line.charAt(idx - 1)))
                    && (idx + name.length() >= line.length()
                    || !isIdentPart(line.charAt(idx + name.length())))) {
                return new int[]{line0, idx, line0, idx + name.length()};
            }
            return null;
        }
        return new int[]{line0, start, line0, end};
    }

    private static int[] resolveNameSpan(String fileText, TextRange r, String name) {
        String[] lines = fileText.replace("\r\n", "\n").split("\n", -1);
        int line0 = r.startLine() - 1;
        if (line0 < 0 || line0 >= lines.length) {
            return null;
        }
        String line = lines[line0];
        int col0 = Math.max(0, r.startColumn() - 1);
        // Exact at column
        if (col0 + name.length() <= line.length()
                && name.equals(line.substring(col0, col0 + name.length()))) {
            return new int[]{line0, col0, line0, col0 + name.length()};
        }
        // Scan line for whole-word name nearest to column
        int best = -1;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i <= line.length() - name.length(); i++) {
            if (!line.regionMatches(i, name, 0, name.length())) {
                continue;
            }
            if (i > 0 && isIdentPart(line.charAt(i - 1))) {
                continue;
            }
            int end = i + name.length();
            if (end < line.length() && isIdentPart(line.charAt(end))) {
                continue;
            }
            int dist = Math.abs(i - col0);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        if (best < 0) {
            return null;
        }
        return new int[]{line0, best, line0, best + name.length()};
    }

    private static String slice(String text, int sl, int sc, int el, int ec) {
        String[] lines = text.replace("\r\n", "\n").split("\n", -1);
        if (sl != el || sl < 0 || sl >= lines.length) {
            return "";
        }
        String line = lines[sl];
        if (sc < 0 || ec > line.length() || sc > ec) {
            return "";
        }
        return line.substring(sc, ec);
    }

    private static boolean isIdentPart(char c) {
        return Character.isJavaIdentifierPart(c);
    }

    private static boolean samePath(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        try {
            return Path.of(a).toAbsolutePath().normalize().equals(Path.of(b).toAbsolutePath().normalize())
                    || a.endsWith(b) || b.endsWith(a);
        } catch (Exception e) {
            return a.equals(b);
        }
    }
}
