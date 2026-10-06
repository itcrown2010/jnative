package io.jnative.incremental;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Result of one incremental or full build generation. */
public final class BuildResult {
    private final boolean success;
    private final long generation;
    private final long wallTimeMs;
    private final List<String> changed = new ArrayList<>();
    private final List<String> parsed = new ArrayList<>();
    private final List<String> semantic = new ArrayList<>();
    private final List<String> irGenerated = new ArrayList<>();
    private final List<String> reused = new ArrayList<>();
    private final List<String> linkedTargets = new ArrayList<>();
    private final List<String> restartedTargets = new ArrayList<>();
    private final List<String> reloadedTargets = new ArrayList<>();
    private final List<String> explain = new ArrayList<>();
    private final List<String> diagnostics = new ArrayList<>();
    private int cacheHits;
    private int cacheMisses;
    private boolean compilerExecuted = true;
    private boolean preservedLastGood;

    public BuildResult(boolean success, long generation, long wallTimeMs) {
        this.success = success;
        this.generation = generation;
        this.wallTimeMs = wallTimeMs;
    }

    public boolean success() {
        return success;
    }

    public long generation() {
        return generation;
    }

    public long wallTimeMs() {
        return wallTimeMs;
    }

    public List<String> changed() {
        return changed;
    }

    public List<String> parsed() {
        return parsed;
    }

    public List<String> semantic() {
        return semantic;
    }

    public List<String> irGenerated() {
        return irGenerated;
    }

    public List<String> reused() {
        return reused;
    }

    public List<String> linkedTargets() {
        return linkedTargets;
    }

    public List<String> restartedTargets() {
        return restartedTargets;
    }

    public List<String> reloadedTargets() {
        return reloadedTargets;
    }

    public List<String> explain() {
        return explain;
    }

    public List<String> diagnostics() {
        return diagnostics;
    }

    public int cacheHits() {
        return cacheHits;
    }

    public void setCacheHits(int cacheHits) {
        this.cacheHits = cacheHits;
    }

    public int cacheMisses() {
        return cacheMisses;
    }

    public void setCacheMisses(int cacheMisses) {
        this.cacheMisses = cacheMisses;
    }

    public boolean compilerExecuted() {
        return compilerExecuted;
    }

    public void setCompilerExecuted(boolean compilerExecuted) {
        this.compilerExecuted = compilerExecuted;
    }

    public boolean preservedLastGood() {
        return preservedLastGood;
    }

    public void setPreservedLastGood(boolean preservedLastGood) {
        this.preservedLastGood = preservedLastGood;
    }

    public String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Build ").append(success ? "completed" : "FAILED")
                .append(" in ").append(wallTimeMs).append(" ms\n\n");
        sb.append("Changed:        ").append(changed.size()).append('\n');
        sb.append("Parsed:         ").append(parsed.size()).append('\n');
        sb.append("Semantic:       ").append(semantic.size()).append('\n');
        sb.append("IR generated:   ").append(irGenerated.size()).append('\n');
        sb.append("Reused units:   ").append(reused.size()).append('\n');
        sb.append("Cache hits:     ").append(cacheHits).append('\n');
        sb.append("Cache misses:   ").append(cacheMisses).append('\n');
        sb.append("Linked:         ").append(String.join(", ", linkedTargets)).append('\n');
        sb.append("Reload:         ").append(String.join(", ", reloadedTargets)).append('\n');
        sb.append("Server restart: ").append(restartedTargets.isEmpty() ? "NO" : String.join(", ", restartedTargets)).append('\n');
        if (!compilerExecuted) {
            sb.append("Compiler:       NO (resource-only)\n");
        }
        if (preservedLastGood) {
            sb.append("Last-known-good: preserved\n");
        }
        return sb.toString();
    }

    public Map<String, Object> metrics() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", success);
        m.put("generation", generation);
        m.put("wallTimeMs", wallTimeMs);
        m.put("changed", changed.size());
        m.put("parsed", parsed.size());
        m.put("semantic", semantic.size());
        m.put("irGenerated", irGenerated.size());
        m.put("reused", reused.size());
        m.put("cacheHits", cacheHits);
        m.put("cacheMisses", cacheMisses);
        m.put("compilerExecuted", compilerExecuted);
        return m;
    }
}
