package io.jnative.incremental;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Persistent symbol index shared by compiler session and (future) JNative Studio.
 * @author 张晓达
 */
public final class SymbolIndex {
    public enum Kind { CLASS, INTERFACE, METHOD, FIELD, OTHER }

    public record Symbol(
            String qualifiedName,
            Kind kind,
            String owner,
            String source,
            String signature,
            String visibility
    ) {
    }

    public record Reference(String symbol, String source, int line) {
    }

    private final Map<String, Symbol> symbols = new LinkedHashMap<>();
    private final Map<String, List<Reference>> references = new LinkedHashMap<>();
    private final Map<String, List<String>> bySource = new LinkedHashMap<>();

    public void clear() {
        symbols.clear();
        references.clear();
        bySource.clear();
    }

    public void removeSource(String source) {
        List<String> names = bySource.remove(source);
        if (names != null) {
            for (String n : names) {
                symbols.remove(n);
                references.remove(n);
            }
        }
        // also drop refs pointing into this file
        for (List<Reference> refs : references.values()) {
            refs.removeIf(r -> source.equals(r.source()));
        }
    }

    public void indexSource(String relativePath, String text) {
        removeSource(relativePath);
        if (text == null) {
            return;
        }
        List<String> owned = new ArrayList<>();
        Matcher classM = Pattern.compile(
                "(public\\s+|protected\\s+|private\\s+)?(class|interface)\\s+(\\w+)").matcher(text);
        while (classM.find()) {
            String vis = classM.group(1) == null ? "package" : classM.group(1).trim();
            Kind kind = "interface".equals(classM.group(2)) ? Kind.INTERFACE : Kind.CLASS;
            String name = classM.group(3);
            String qn = name;
            Symbol s = new Symbol(qn, kind, "", relativePath, kind.name().toLowerCase() + " " + name, vis);
            symbols.put(qn, s);
            owned.add(qn);
        }
        Matcher methodM = Pattern.compile(
                "(public|protected|private)\\s+[\\w<>,\\[\\]\\s]+\\s+(\\w+)\\s*\\(([^)]*)\\)").matcher(text);
        String owner = owned.isEmpty() ? "" : owned.get(0);
        while (methodM.find()) {
            String name = methodM.group(2);
            if ("if".equals(name) || "while".equals(name) || "for".equals(name) || "switch".equals(name)) {
                continue;
            }
            String sig = methodM.group(2) + "(" + methodM.group(3).trim() + ")";
            String qn = owner.isEmpty() ? name : owner + "." + name;
            symbols.put(qn, new Symbol(qn, Kind.METHOD, owner, relativePath, sig, methodM.group(1)));
            owned.add(qn);
        }
        bySource.put(relativePath, owned);
    }

    public Symbol get(String qualifiedName) {
        return symbols.get(qualifiedName);
    }

    public Map<String, Symbol> all() {
        return symbols;
    }

    public int size() {
        return symbols.size();
    }
}
