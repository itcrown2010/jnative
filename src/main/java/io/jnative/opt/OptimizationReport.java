package io.jnative.opt;

import java.util.LinkedHashMap;
import java.util.Map;

/** Counters and optional explanation for --opt-report.
 * @author 张晓达
 */
public final class OptimizationReport {
    private final Map<String, Integer> counters = new LinkedHashMap<>();
    private final StringBuilder explain = new StringBuilder();
    private boolean explainEnabled;

    public void increment(String key) {
        counters.merge(key, 1, Integer::sum);
    }

    public void add(String key, int delta) {
        if (delta != 0) {
            counters.merge(key, delta, Integer::sum);
        }
    }

    public void note(String line) {
        if (explainEnabled) {
            explain.append(line).append('\n');
        }
    }

    public void setExplainEnabled(boolean explainEnabled) {
        this.explainEnabled = explainEnabled;
    }

    public int get(String key) {
        return counters.getOrDefault(key, 0);
    }

    public String format() {
        StringBuilder sb = new StringBuilder();
        sb.append("; JNative optimization report\n");
        for (var e : counters.entrySet()) {
            sb.append(";   ").append(e.getKey()).append(": ").append(e.getValue()).append('\n');
        }
        if (!explain.isEmpty()) {
            sb.append("; --- explain ---\n");
            sb.append(explain);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return format();
    }
}
