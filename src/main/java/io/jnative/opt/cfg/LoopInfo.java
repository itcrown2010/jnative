package io.jnative.opt.cfg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Simple backedge detection for loop headers. */
public final class LoopInfo {
    private final ControlFlowGraph cfg;
    private final List<Backedge> backedges = new ArrayList<>();

    public record Backedge(String header, String tail) {
    }

    public LoopInfo(ControlFlowGraph cfg) {
        this.cfg = cfg;
        detect();
    }

    public List<Backedge> backedges() {
        return backedges;
    }

    public boolean isLoopHeader(String block) {
        return backedges.stream().anyMatch(b -> b.header().equals(block));
    }

    private void detect() {
        if (cfg.function().blocks().isEmpty()) {
            return;
        }
        String entry = cfg.function().blocks().getFirst().name();
        for (var block : cfg.function().blocks()) {
            String name = block.name();
            for (String succ : cfg.successors(name)) {
                if (dominatesOrEqual(succ, name, entry)) {
                    backedges.add(new Backedge(succ, name));
                }
            }
        }
    }

    private boolean dominatesOrEqual(String dominator, String block, String entry) {
        if (dominator.equals(block)) {
            return true;
        }
        Set<String> visited = new HashSet<>();
        return reaches(dominator, block, visited);
    }

    private boolean reaches(String from, String target, Set<String> visited) {
        if (!visited.add(from)) {
            return false;
        }
        if (from.equals(target)) {
            return true;
        }
        for (String succ : cfg.successors(from)) {
            if (reaches(succ, target, visited)) {
                return true;
            }
        }
        return false;
    }
}
