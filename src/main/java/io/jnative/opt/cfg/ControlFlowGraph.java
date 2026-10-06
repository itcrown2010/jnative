package io.jnative.opt.cfg;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Control-flow graph with optional dominator tree (iterative). */
public final class ControlFlowGraph {
    private final IrFunction function;
    private final Map<String, IrBasicBlock> blocksByName = new LinkedHashMap<>();
    private final Map<String, List<String>> successors = new HashMap<>();
    private final Map<String, List<String>> predecessors = new HashMap<>();
    private final Map<String, Set<String>> dominators = new HashMap<>();

    public ControlFlowGraph(IrFunction function) {
        this.function = function;
        build();
    }

    public IrFunction function() {
        return function;
    }

    public IrBasicBlock block(String name) {
        return blocksByName.get(name);
    }

    public List<String> successors(String block) {
        return successors.getOrDefault(block, List.of());
    }

    public List<String> predecessors(String block) {
        return predecessors.getOrDefault(block, List.of());
    }

    public Set<String> dominators(String block) {
        return dominators.getOrDefault(block, Set.of());
    }

    public boolean dominates(String dominator, String block) {
        return dominators.getOrDefault(block, Set.of()).contains(dominator);
    }

    private void build() {
        for (IrBasicBlock b : function.blocks()) {
            blocksByName.put(b.name(), b);
            successors.put(b.name(), new ArrayList<>());
            predecessors.put(b.name(), new ArrayList<>());
        }
        if (function.blocks().isEmpty()) {
            return;
        }
        String entry = function.blocks().getFirst().name();
        for (IrBasicBlock b : function.blocks()) {
            for (IrInstruction inst : b.instructions()) {
                switch (inst) {
                    case IrInstruction.Branch br -> addEdge(b.name(), br.target());
                    case IrInstruction.CondBranch cb -> {
                        addEdge(b.name(), cb.trueTarget());
                        addEdge(b.name(), cb.falseTarget());
                    }
                    default -> { }
                }
            }
        }
        computeDominators(entry);
    }

    private void addEdge(String from, String to) {
        if (!blocksByName.containsKey(to)) {
            return;
        }
        List<String> succs = successors.get(from);
        if (!succs.contains(to)) {
            succs.add(to);
        }
        List<String> preds = predecessors.computeIfAbsent(to, k -> new ArrayList<>());
        if (!preds.contains(from)) {
            preds.add(from);
        }
    }

    private void computeDominators(String entry) {
        Set<String> all = blocksByName.keySet();
        for (String b : all) {
            dominators.put(b, new HashSet<>(all));
        }
        dominators.get(entry).clear();
        dominators.get(entry).add(entry);

        boolean changed = true;
        while (changed) {
            changed = false;
            for (String b : all) {
                if (b.equals(entry)) {
                    continue;
                }
                Set<String> newDom = new HashSet<>(all);
                List<String> preds = predecessors.getOrDefault(b, List.of());
                if (!preds.isEmpty()) {
                    newDom.clear();
                    newDom.add(b);
                    boolean first = true;
                    for (String p : preds) {
                        if (first) {
                            newDom.addAll(dominators.getOrDefault(p, Set.of()));
                            newDom.add(b);
                            first = false;
                        } else {
                            newDom.retainAll(dominators.getOrDefault(p, Set.of()));
                            newDom.add(b);
                        }
                    }
                } else {
                    newDom.add(b);
                }
                if (!newDom.equals(dominators.get(b))) {
                    dominators.put(b, newDom);
                    changed = true;
                }
            }
        }
    }
}
