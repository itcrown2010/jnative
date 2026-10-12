/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.lsp;

import io.jnative.ast.ClassDeclaration;
import io.jnative.ast.FieldDeclaration;
import io.jnative.ast.ImportDeclaration;
import io.jnative.ast.MethodDeclaration;
import io.jnative.ast.Parameter;
import io.jnative.compiler.Compilation;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.compiler.SemanticModel;
import io.jnative.compiler.SourceUnit;
import io.jnative.compiler.TextRange;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.semantic.ClassSymbol;
import io.jnative.semantic.ClassType;
import io.jnative.semantic.ConstructorSymbol;
import io.jnative.semantic.FieldSymbol;
import io.jnative.semantic.InterfaceSymbol;
import io.jnative.semantic.JType;
import io.jnative.semantic.MethodSymbol;
import io.jnative.semantic.ParameterSymbol;
import io.jnative.semantic.VariableSymbol;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared IDE queries over {@link JNativeCompiler#analyzeUnits} — definition, references,
 * hover, symbols, limited code actions. Analyze-only (never links).
 * @author 张晓达
 */
public final class IdeServices {
    private static final Pattern UNDEFINED_TYPE = Pattern.compile(
            "(?i)(?:cannot find symbol|undefined type|unknown type|type\\s+'([^']+)'\\s+not found|"
                    + "cannot resolve type\\s+(\\w+)|undefined (?:class|type)\\s+(\\w+))");

    private static final int MAX_CACHE_SCOPES = 8;

    private final JNativeCompiler compiler;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    /** Scopes currently running a full project analyze (not ready for edit ops). */
    private final Set<String> indexing = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, Object> scopeLocks = new ConcurrentHashMap<>();

    public IdeServices(JNativeCompiler compiler) {
        this.compiler = compiler;
    }

    public boolean isIndexing(String pathHint) {
        Path root = ProjectSourceCollector.findProjectRoot(
                Path.of(pathHint == null ? "." : pathHint));
        String scope = root == null
                ? Path.of(pathHint == null ? "." : pathHint).toAbsolutePath().normalize().toString()
                : root.toString();
        return indexing.contains(scope);
    }

    public record Location(String file, int line1, int col1, String message) {
        public static Location of(TextRange r) {
            return new Location(r.file(), r.startLine(), r.startColumn(), null);
        }
    }

    public record Hover(String markdown) {
    }

    public record DocSymbol(
            String name,
            int kind,
            int startLine0,
            int startCol0,
            int endLine0,
            int endCol0,
            List<DocSymbol> children
    ) {
    }

    public record WorkspaceSymbol(String name, int kind, String container, String file, int line1, int col1) {
    }

    public record CodeAction(String title, String kind, List<TextEdit> edits) {
    }

    public record TextEdit(String file, int startLine0, int startCol0, int endLine0, int endCol0, String newText) {
    }

    private record CacheEntry(String key, SemanticModel model, List<Diagnostic> diagnostics, long analyzedAtMs) {
    }

    public SemanticModel modelFor(
            String activePath,
            String activeText,
            int docVersion,
            Map<String, String> openOverlays
    ) {
        Compilation c = analyzeCached(activePath, activeText, docVersion, openOverlays);
        return c == null ? null : c.semanticModel();
    }

    public Compilation analyzeCached(
            String activePath,
            String activeText,
            int docVersion,
            Map<String, String> openOverlays
    ) {
        return analyzeCached(activePath, activeText, docVersion, openOverlays, false);
    }

    /**
     * @param requireFresh when true, bypass cache (rename / code actions that mutate).
     */
    public Compilation analyzeCached(
            String activePath,
            String activeText,
            int docVersion,
            Map<String, String> openOverlays,
            boolean requireFresh
    ) {
        Path path = Path.of(activePath == null ? "Memory.java" : activePath).toAbsolutePath().normalize();
        Path root = ProjectSourceCollector.findProjectRoot(path);
        String cacheScope = root == null ? path.toString() : root.toString();
        Map<String, String> overlays = overlaysForScope(cacheScope, path, activeText, openOverlays);
        long diskFp = diskFingerprint(root, path);
        String key = cacheKey(cacheScope, overlays, docVersion, diskFp);
        if (!requireFresh) {
            CacheEntry hit = cache.get(cacheScope);
            if (hit != null && key.equals(hit.key()) && hit.model() != null) {
                LspLog.debug("cache hit scope=" + shortName(cacheScope));
                return wrap(hit);
            }
        }
        Object lock = scopeLocks.computeIfAbsent(cacheScope, k -> new Object());
        synchronized (lock) {
            if (!requireFresh) {
                CacheEntry hit2 = cache.get(cacheScope);
                if (hit2 != null && key.equals(hit2.key()) && hit2.model() != null) {
                    return wrap(hit2);
                }
            }
            indexing.add(cacheScope);
            long t0 = System.nanoTime();
            try {
                List<SourceUnit> units;
                if (root != null) {
                    units = ProjectSourceCollector.collect(root, overlays);
                } else {
                    units = List.of(SourceUnit.ofPath(path, activeText == null ? "" : activeText));
                }
                if (units.isEmpty()) {
                    units = List.of(SourceUnit.ofPath(path, activeText == null ? "" : activeText));
                }
                Compilation comp = compiler.analyzeUnits(units, docVersion);
                long ms = (System.nanoTime() - t0) / 1_000_000L;
                LspLog.info("analyzeUnits files=" + units.size()
                        + " ms=" + ms + " scope=" + shortName(cacheScope)
                        + (requireFresh ? " fresh" : ""));
                if (comp != null && comp.semanticModel() != null) {
                    putCache(cacheScope, new CacheEntry(key, comp.semanticModel(), comp.diagnostics(), ms));
                }
                return comp;
            } finally {
                indexing.remove(cacheScope);
            }
        }
    }

    /** Overlays belonging to the same project root (multi-root isolation). */
    static Map<String, String> overlaysForScope(
            String cacheScope,
            Path activePath,
            String activeText,
            Map<String, String> openOverlays
    ) {
        Map<String, String> overlays = new LinkedHashMap<>();
        if (openOverlays != null) {
            for (Map.Entry<String, String> e : openOverlays.entrySet()) {
                Path p = Path.of(e.getKey()).toAbsolutePath().normalize();
                Path root = ProjectSourceCollector.findProjectRoot(p);
                String scope = root == null ? p.toString() : root.toString();
                if (scope.equals(cacheScope)) {
                    overlays.put(p.toString(), e.getValue());
                }
            }
        }
        overlays.put(activePath.toString(), activeText == null ? "" : activeText);
        return overlays;
    }

    public void invalidate(String pathHint) {
        if (pathHint == null) {
            cache.clear();
            LspLog.info("cache cleared (all)");
            return;
        }
        Path root = ProjectSourceCollector.findProjectRoot(Path.of(pathHint));
        if (root != null) {
            cache.remove(root.toString());
            LspLog.info("cache invalidate scope=" + shortName(root.toString()));
        }
        cache.remove(Path.of(pathHint).toAbsolutePath().normalize().toString());
    }

    private void putCache(String scope, CacheEntry entry) {
        cache.put(scope, entry);
        if (cache.size() <= MAX_CACHE_SCOPES) {
            return;
        }
        // Drop an arbitrary non-matching scope (simple bound; scopes are few)
        for (String k : cache.keySet()) {
            if (!k.equals(scope)) {
                cache.remove(k);
                LspLog.debug("cache evict scope=" + shortName(k));
                if (cache.size() <= MAX_CACHE_SCOPES) {
                    break;
                }
            }
        }
    }

    /** Cheap fingerprint so external/disk edits invalidate the snapshot. */
    static long diskFingerprint(Path projectRoot, Path fallbackFile) {
        try {
            if (projectRoot != null && Files.isDirectory(projectRoot)) {
                long acc = 0;
                int n = 0;
                try (var walk = Files.walk(projectRoot, 6)) {
                    var it = walk.iterator();
                    while (it.hasNext()) {
                        Path p = it.next();
                        String name = p.getFileName() == null ? "" : p.getFileName().toString();
                        if (!Files.isRegularFile(p)) {
                            continue;
                        }
                        String lower = name.toLowerCase(Locale.ROOT);
                        boolean src = lower.endsWith(".jn") || lower.endsWith(".java");
                        boolean manifest = name.equals("jnative.project") || name.equals("jnative.toml")
                                || name.equals("jnative.lock");
                        if (!src && !manifest) {
                            continue;
                        }
                        String norm = p.toString().replace('\\', '/');
                        if (norm.contains("/.jnative/pkg-cache/") && src) {
                            continue;
                        }
                        acc = acc * 31 + Files.getLastModifiedTime(p).toMillis();
                        acc = acc * 31 + Files.size(p);
                        n++;
                        if (n > 400) {
                            break;
                        }
                    }
                }
                return acc * 31 + n;
            }
            if (fallbackFile != null && Files.isRegularFile(fallbackFile)) {
                return Files.getLastModifiedTime(fallbackFile).toMillis()
                        ^ Files.size(fallbackFile);
            }
        } catch (Exception e) {
            return 0L;
        }
        return 0L;
    }

    private static String shortName(String scope) {
        if (scope == null) {
            return "?";
        }
        Path p = Path.of(scope);
        return p.getFileName() == null ? scope : p.getFileName().toString();
    }

    private static Compilation wrap(CacheEntry hit) {
        // Minimal facade — callers only need model + diagnostics
        return new Compilation(
                hit.model().version(),
                null,
                hit.model().typed(),
                hit.model(),
                hit.diagnostics(),
                true,
                hit.analyzedAtMs(),
                0);
    }

    private static String cacheKey(
            String scope, Map<String, String> overlays, int docVersion, long diskFp
    ) {
        StringBuilder sb = new StringBuilder(scope).append('|').append(docVersion)
                .append("|disk=").append(diskFp).append('|');
        for (Map.Entry<String, String> e : overlays.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue() == null ? 0 : e.getValue().length())
                    .append('@').append(e.getValue() == null ? 0 : e.getValue().hashCode()).append(';');
        }
        return sb.toString();
    }

    public List<Location> definition(
            String path, String text, int line0, int col0, int version, Map<String, String> overlays
    ) {
        Compilation comp = analyzeCached(path, text, version, overlays);
        SemanticModel model = comp == null ? null : comp.semanticModel();
        if (model == null) {
            return List.of();
        }
        Optional<Object> sym = model.symbolAt(path, line0 + 1, col0 + 1);
        if (sym.isEmpty()) {
            return List.of();
        }
        Optional<TextRange> decl = model.declarationOf(sym.get());
        if (decl.isEmpty()) {
            // Builtin / no source — do not jump to a wrong file
            return List.of(new Location("", 0, 0,
                    "定义源码不可用（可能是内建类型或依赖未纳入当前项目）: " + describe(sym.get())));
        }
        TextRange r = decl.get();
        Path declPath = Path.of(r.file());
        boolean onDisk = Files.isRegularFile(declPath);
        boolean openBuffer = sameFile(r.file(), path)
                || (overlays != null && overlays.keySet().stream().anyMatch(p -> sameFile(p, r.file())));
        if (!onDisk && !openBuffer) {
            return List.of(new Location("", 0, 0,
                    "定义文件不存在或不可读: " + r.file()));
        }
        return List.of(Location.of(r));
    }

    public List<Location> references(
            String path, String text, int line0, int col0, int version, Map<String, String> overlays
    ) {
        Compilation comp = analyzeCached(path, text, version, overlays);
        SemanticModel model = comp == null ? null : comp.semanticModel();
        if (model == null) {
            return List.of();
        }
        Optional<Object> sym = model.symbolAt(path, line0 + 1, col0 + 1);
        if (sym.isEmpty()) {
            return List.of();
        }
        List<Location> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (TextRange r : model.referencesOf(sym.get())) {
            if (r.file() == null || r.file().isBlank() || r.startLine() <= 0) {
                continue;
            }
            String k = r.file() + ":" + r.startLine() + ":" + r.startColumn();
            if (seen.add(k)) {
                out.add(Location.of(r));
            }
        }
        return out;
    }

    public Hover hover(
            String path, String text, int line0, int col0, int version, Map<String, String> overlays
    ) {
        Compilation comp = analyzeCached(path, text, version, overlays);
        SemanticModel model = comp == null ? null : comp.semanticModel();
        if (model == null) {
            return null;
        }
        Optional<Object> symOpt = model.symbolAt(path, line0 + 1, col0 + 1);
        Optional<JType> typeOpt = model.typeAt(path, line0 + 1, col0 + 1);
        if (symOpt.isEmpty() && typeOpt.isEmpty()) {
            return null;
        }
        StringBuilder md = new StringBuilder();
        if (symOpt.isPresent()) {
            Object sym = symOpt.get();
            md.append("```jnative\n").append(signatureOf(sym)).append("\n```\n");
            String doc = extractSourceDoc(model.declarationOf(sym).orElse(null));
            if (doc != null && !doc.isBlank()) {
                md.append('\n').append(doc).append('\n');
            }
        } else {
            md.append("```jnative\n").append(typeOpt.get()).append("\n```\n");
        }
        return new Hover(md.toString().trim());
    }

    public List<DocSymbol> documentSymbols(String path, String text, int version, Map<String, String> overlays) {
        Compilation comp = analyzeCached(path, text, version, overlays);
        SemanticModel model = comp == null ? null : comp.semanticModel();
        if (model == null || model.typed() == null || model.typed().unit() == null) {
            return List.of();
        }
        Path want = Path.of(path).toAbsolutePath().normalize();
        List<DocSymbol> out = new ArrayList<>();
        for (ClassDeclaration cls : model.typed().unit().classes()) {
            if (cls.location() == null || !sameFile(cls.location().file(), want.toString())) {
                continue;
            }
            List<DocSymbol> children = new ArrayList<>();
            for (FieldDeclaration f : cls.fields()) {
                SourceLocation loc = f.location();
                if (loc == null) {
                    continue;
                }
                children.add(new DocSymbol(f.name(), 8 /* Field */,
                        Math.max(0, loc.line() - 1), Math.max(0, loc.column() - 1),
                        Math.max(0, loc.line() - 1), Math.max(0, loc.column()),
                        List.of()));
            }
            for (MethodDeclaration m : cls.methods()) {
                SourceLocation loc = m.location();
                if (loc == null) {
                    continue;
                }
                children.add(new DocSymbol(m.name() + paramLabel(m),
                        m.isConstructor() ? 9 /* Constructor */ : 6 /* Method */,
                        Math.max(0, loc.line() - 1), Math.max(0, loc.column() - 1),
                        Math.max(0, loc.line() - 1), Math.max(0, loc.column()),
                        List.of()));
            }
            SourceLocation loc = cls.location();
            out.add(new DocSymbol(cls.name(),
                    cls.isInterface() ? 11 : 5,
                    Math.max(0, loc.line() - 1), Math.max(0, loc.column() - 1),
                    Math.max(0, loc.line() - 1), Math.max(0, loc.column()),
                    children));
        }
        return out;
    }

    public List<WorkspaceSymbol> workspaceSymbols(String query, Map<String, String> overlays, String hintPath) {
        Path hint = hintPath == null ? null : Path.of(hintPath);
        Path root = ProjectSourceCollector.findProjectRoot(hint);
        if (root == null && overlays != null && !overlays.isEmpty()) {
            root = ProjectSourceCollector.findProjectRoot(Path.of(overlays.keySet().iterator().next()));
        }
        Map<String, String> ov = overlays == null ? Map.of() : overlays;
        String anyPath = hintPath;
        if (anyPath == null && !ov.isEmpty()) {
            anyPath = ov.keySet().iterator().next();
        }
        if (anyPath == null) {
            return List.of();
        }
        // Prefer open buffer text — do not analyze an empty overlay for the hint path.
        String anyText = ov.getOrDefault(anyPath, "");
        if (anyText.isEmpty()) {
            for (Map.Entry<String, String> e : ov.entrySet()) {
                if (e.getValue() != null && !e.getValue().isEmpty()) {
                    anyPath = e.getKey();
                    anyText = e.getValue();
                    break;
                }
            }
        }
        Compilation comp = analyzeCached(anyPath, anyText, 1, ov);
        SemanticModel model = comp == null ? null : comp.semanticModel();
        if (model == null) {
            return List.of();
        }
        String q = query == null ? "" : query.toLowerCase(Locale.ROOT);
        List<WorkspaceSymbol> out = new ArrayList<>();
        for (ClassSymbol cls : model.classes().values()) {
            if (cls.isBuiltin() || cls.location() == null || cls.location().line() <= 0) {
                continue;
            }
            if (!sourceReachable(cls.location().file(), ov)) {
                continue;
            }
            if (q.isEmpty() || cls.name().toLowerCase(Locale.ROOT).contains(q)
                    || simpleName(cls.name()).toLowerCase(Locale.ROOT).contains(q)) {
                out.add(new WorkspaceSymbol(simpleName(cls.name()), 5, packageOf(cls.name()),
                        cls.location().file(), cls.location().line(), cls.location().column()));
            }
            for (MethodSymbol m : cls.declaredMethods()) {
                if (m.location() == null || m.location().line() <= 0) {
                    continue;
                }
                if (q.isEmpty() || m.name().toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(new WorkspaceSymbol(m.name(), 6, simpleName(cls.name()),
                            m.location().file(), m.location().line(), m.location().column()));
                }
            }
            for (FieldSymbol f : cls.declaredFields()) {
                if (f.location() == null || f.location().line() <= 0) {
                    continue;
                }
                if (q.isEmpty() || f.name().toLowerCase(Locale.ROOT).contains(q)) {
                    out.add(new WorkspaceSymbol(f.name(), 8, simpleName(cls.name()),
                            f.location().file(), f.location().line(), f.location().column()));
                }
            }
            if (out.size() > 200) {
                break;
            }
        }
        return out;
    }

    public List<CodeAction> codeActions(
            String path, String text, int version, Map<String, String> overlays, List<Diagnostic> diags
    ) {
        List<CodeAction> out = new ArrayList<>();
        Compilation comp = analyzeCached(path, text, version, overlays);
        SemanticModel model = comp == null ? null : comp.semanticModel();
        List<Diagnostic> useDiags = diags == null || diags.isEmpty()
                ? (comp == null ? List.of() : comp.diagnostics())
                : diags;
        // Remove unused imports (AST-declared imports never referenced by resolved symbols / type names)
        if (model != null && model.typed() != null && model.typed().unit() != null) {
            for (ClassDeclaration cls : model.typed().unit().classes()) {
                if (!sameFile(cls.location() == null ? null : cls.location().file(), path)) {
                    continue;
                }
                Set<String> usedSimple = collectUsedTypeSimpleNames(model, path);
                for (ImportDeclaration imp : cls.imports()) {
                    String simple = imp.simpleName();
                    if (simple.isEmpty() || usedSimple.contains(simple)) {
                        continue;
                    }
                    SourceLocation loc = imp.location();
                    if (loc == null || loc.line() <= 0) {
                        continue;
                    }
                    int line0 = loc.line() - 1;
                    // Delete whole import line including trailing newline when present
                    int endLine0 = line0;
                    int endCol0 = 0;
                    String[] lines = text.replace("\r\n", "\n").split("\n", -1);
                    if (line0 < lines.length) {
                        endCol0 = 0;
                        endLine0 = line0 + 1; // remove through start of next line
                        if (endLine0 >= lines.length) {
                            endLine0 = line0;
                            endCol0 = lines[line0].length();
                        }
                    }
                    out.add(new CodeAction(
                            "移除未使用的 import: " + imp.qualifiedType(),
                            "quickfix",
                            List.of(new TextEdit(path, line0, 0, endLine0, endCol0, ""))));
                }
            }
        }
        // Add import for uniquely resolved missing types
        for (Diagnostic d : useDiags) {
            if (d.message() == null || d.location() == null) {
                continue;
            }
            if (!sameFile(d.location().file(), path)) {
                continue;
            }
            String missing = extractMissingType(d.message());
            if (missing == null) {
                continue;
            }
            List<ClassSymbol> candidates = findTypeCandidates(model, missing);
            if (candidates.isEmpty()) {
                continue;
            }
            if (candidates.size() == 1) {
                String fqn = candidates.get(0).name();
                TextEdit edit = insertImportEdit(path, text, fqn);
                if (edit != null) {
                    out.add(new CodeAction("添加 import " + fqn, "quickfix", List.of(edit)));
                }
            } else {
                for (ClassSymbol c : candidates) {
                    TextEdit edit = insertImportEdit(path, text, c.name());
                    if (edit != null) {
                        out.add(new CodeAction("添加 import " + c.name(), "quickfix", List.of(edit)));
                    }
                }
            }
        }
        return out;
    }

    private static Set<String> collectUsedTypeSimpleNames(SemanticModel model, String path) {
        Set<String> used = new LinkedHashSet<>();
        if (model.typed() == null) {
            return used;
        }
        model.typed().expressionTypes().forEach((expr, t) -> {
            if (t != null) {
                used.add(simpleName(t.toString()));
            }
        });
        model.typed().resolvedSymbols().values().forEach(sym -> {
            if (sym instanceof ClassSymbol c) {
                used.add(simpleName(c.name()));
            } else if (sym instanceof FieldSymbol f && f.type() != null) {
                used.add(simpleName(f.type().toString()));
            } else if (sym instanceof MethodSymbol m) {
                if (m.returnType() != null) {
                    used.add(simpleName(m.returnType().toString()));
                }
            } else if (sym instanceof VariableSymbol v && v.type() != null) {
                used.add(simpleName(v.type().toString()));
            }
        });
        for (ClassDeclaration cls : model.documentClasses()) {
            if (!sameFile(cls.location() == null ? null : cls.location().file(), path)) {
                continue;
            }
            if (cls.superClassName() != null) {
                used.add(simpleName(cls.superClassName()));
            }
            for (var iface : cls.implementedInterfaces()) {
                if (iface != null && iface.name() != null) {
                    used.add(simpleName(iface.name()));
                }
            }
        }
        return used;
    }

    private static List<ClassSymbol> findTypeCandidates(SemanticModel model, String simpleOrFqn) {
        List<ClassSymbol> out = new ArrayList<>();
        if (model == null || simpleOrFqn == null) {
            return out;
        }
        String want = simpleOrFqn;
        for (ClassSymbol c : model.classes().values()) {
            if (c.isBuiltin()) {
                continue;
            }
            if (c.name().equals(want) || simpleName(c.name()).equals(want)) {
                if (c.location() != null && c.location().line() > 0
                        && Files.isRegularFile(Path.of(c.location().file()))) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    private static String extractMissingType(String message) {
        Matcher m = UNDEFINED_TYPE.matcher(message);
        if (m.find()) {
            for (int g = 1; g <= m.groupCount(); g++) {
                if (m.group(g) != null && !m.group(g).isBlank()) {
                    return m.group(g);
                }
            }
        }
        // Fallback: "... type Foo ..."
        Matcher m2 = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\b").matcher(message);
        if (message.toLowerCase(Locale.ROOT).contains("cannot")
                || message.toLowerCase(Locale.ROOT).contains("undefined")
                || message.toLowerCase(Locale.ROOT).contains("not found")) {
            while (m2.find()) {
                String n = m2.group(1);
                if (!Set.of("String", "Object", "Error", "Type", "Class").contains(n)) {
                    return n;
                }
            }
        }
        return null;
    }

    private static TextEdit insertImportEdit(String path, String text, String fqn) {
        if (fqn == null || !fqn.contains(".")) {
            // default-package types need no import
            return null;
        }
        String normalized = text.replace("\r\n", "\n");
        if (normalized.contains("import " + fqn + ";")) {
            return null;
        }
        String[] lines = normalized.split("\n", -1);
        int insertLine = 0;
        int lastImport = -1;
        int packageLine = -1;
        for (int i = 0; i < lines.length; i++) {
            String t = lines[i].trim();
            if (t.startsWith("package ")) {
                packageLine = i;
            }
            if (t.startsWith("import ")) {
                lastImport = i;
            }
            if (t.startsWith("public ") || t.startsWith("class ") || t.startsWith("interface ")
                    || t.startsWith("final ") || t.startsWith("abstract ")) {
                break;
            }
        }
        if (lastImport >= 0) {
            insertLine = lastImport + 1;
        } else if (packageLine >= 0) {
            insertLine = packageLine + 1;
        } else {
            insertLine = 0;
        }
        String prefix = insertLine > 0 && lastImport < 0 && packageLine >= 0 ? "\n" : "";
        String snippet = prefix + "import " + fqn + ";\n";
        return new TextEdit(path, insertLine, 0, insertLine, 0, snippet);
    }

    private static String extractSourceDoc(TextRange decl) {
        if (decl == null || decl.file() == null || !Files.isRegularFile(Path.of(decl.file()))) {
            return null;
        }
        try {
            String src = Files.readString(Path.of(decl.file()));
            String[] lines = src.replace("\r\n", "\n").split("\n", -1);
            int lineIdx = decl.startLine() - 1;
            if (lineIdx <= 0 || lineIdx > lines.length) {
                return null;
            }
            int end = lineIdx - 1;
            while (end >= 0 && lines[end].trim().isEmpty()) {
                end--;
            }
            if (end < 0 || !lines[end].trim().endsWith("*/")) {
                return null;
            }
            int start = end;
            while (start >= 0 && !lines[start].contains("/**") && !lines[start].contains("/*")) {
                start--;
            }
            if (start < 0) {
                return null;
            }
            StringBuilder doc = new StringBuilder();
            for (int i = start; i <= end; i++) {
                String t = lines[i].trim();
                t = t.replaceFirst("^/\\*\\*?\\s?", "");
                t = t.replaceFirst("^\\*\\s?", "");
                t = t.replaceFirst("\\*/\\s*$", "");
                if (!t.isBlank()) {
                    doc.append(t).append('\n');
                }
            }
            return doc.toString().trim();
        } catch (Exception e) {
            return null;
        }
    }

    private static String signatureOf(Object sym) {
        if (sym instanceof ClassSymbol c) {
            return (c.isInterface() ? "interface " : "class ") + c.name();
        }
        if (sym instanceof InterfaceSymbol i) {
            return "interface " + i.name();
        }
        if (sym instanceof MethodSymbol m) {
            StringBuilder sb = new StringBuilder();
            if (m.isStatic()) {
                sb.append("static ");
            }
            sb.append(typeName(m.returnType())).append(' ').append(m.name()).append('(');
            for (int i = 0; i < m.parameters().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                ParameterSymbol p = m.parameters().get(i);
                sb.append(typeName(p.type())).append(' ').append(p.name());
            }
            sb.append(')');
            if (m.owner() != null) {
                sb.append(" — ").append(m.owner().name());
            }
            return sb.toString();
        }
        if (sym instanceof ConstructorSymbol ctor) {
            StringBuilder sb = new StringBuilder(ctor.owner() == null ? "?" : simpleName(ctor.owner().name()));
            sb.append('(');
            for (int i = 0; i < ctor.parameters().size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                ParameterSymbol p = ctor.parameters().get(i);
                sb.append(typeName(p.type())).append(' ').append(p.name());
            }
            sb.append(')');
            return sb.toString();
        }
        if (sym instanceof FieldSymbol f) {
            return (f.isStatic() ? "static " : "") + typeName(f.type()) + " " + f.name();
        }
        if (sym instanceof VariableSymbol v) {
            String kind = v.kind() == VariableSymbol.Kind.PARAMETER ? "param" : "local";
            return kind + " " + typeName(v.type()) + " " + v.name();
        }
        if (sym instanceof ParameterSymbol p) {
            return "param " + typeName(p.type()) + " " + p.name();
        }
        if (sym instanceof ClassType ct) {
            return ct.toString();
        }
        return String.valueOf(sym);
    }

    private static String describe(Object sym) {
        if (sym instanceof ClassSymbol c) {
            return c.name();
        }
        if (sym instanceof MethodSymbol m) {
            return m.name();
        }
        return String.valueOf(sym);
    }

    private static String typeName(JType t) {
        return t == null ? "?" : t.toString();
    }

    private static String simpleName(String fqn) {
        if (fqn == null) {
            return "";
        }
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? fqn : fqn.substring(dot + 1);
    }

    private static String packageOf(String fqn) {
        if (fqn == null) {
            return "";
        }
        int dot = fqn.lastIndexOf('.');
        return dot < 0 ? "" : fqn.substring(0, dot);
    }

    private static String paramLabel(MethodDeclaration m) {
        StringBuilder sb = new StringBuilder("(");
        List<Parameter> ps = m.parameters();
        for (int i = 0; i < ps.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(ps.get(i).type() == null ? "?" : ps.get(i).type().toString());
        }
        sb.append(')');
        return sb.toString();
    }

    private static boolean sameFile(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        Path pa = Path.of(a).toAbsolutePath().normalize();
        Path pb = Path.of(b).toAbsolutePath().normalize();
        return pa.equals(pb) || a.endsWith(b) || b.endsWith(a);
    }

    /** On-disk source or currently open (possibly unsaved) overlay. */
    private static boolean sourceReachable(String file, Map<String, String> overlays) {
        if (file == null || file.isBlank() || "<stdlib>".equals(file) || "<unknown>".equals(file)) {
            return false;
        }
        try {
            if (Files.isRegularFile(Path.of(file))) {
                return true;
            }
        } catch (Exception ignored) {
            // fall through
        }
        if (overlays == null) {
            return false;
        }
        for (String p : overlays.keySet()) {
            if (sameFile(p, file)) {
                return true;
            }
        }
        return false;
    }
}
