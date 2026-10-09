package io.jnative.ir;

import io.jnative.diagnostics.SourceLocation;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Side-channel debug metadata for IR → LLVM DI lowering.
 * Does not alter instruction record shapes.
 * @author 张晓达
 */
public final class IrDebugInfo {
    public record LocalVar(
            IrFunction function,
            IrValue.Local slot,
            String sourceName,
            SourceLocation location,
            boolean isParameter,
            int argIndex,
            /** Optional Java type name (e.g. {@code Person}, {@code String}, {@code int[]}). */
            String javaTypeName
    ) {
        public LocalVar(
                IrFunction function,
                IrValue.Local slot,
                String sourceName,
                SourceLocation location,
                boolean isParameter,
                int argIndex
        ) {
            this(function, slot, sourceName, location, isParameter, argIndex, null);
        }
    }

    private boolean enabled;
    private String compileUnitFile = "";
    private String compileUnitDirectory = "";
    private final IdentityHashMap<IrInstruction, SourceLocation> instructionLocations = new IdentityHashMap<>();
    private final Map<IrFunction, SourceLocation> functionLocations = new IdentityHashMap<>();
    private final Map<IrFunction, String> functionDisplayNames = new IdentityHashMap<>();
    private final List<LocalVar> locals = new ArrayList<>();
    /** Alloca instruction → local var record for dbg.declare. */
    private final IdentityHashMap<IrInstruction, LocalVar> allocaLocals = new IdentityHashMap<>();

    public boolean enabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String compileUnitFile() {
        return compileUnitFile;
    }

    public String compileUnitDirectory() {
        return compileUnitDirectory;
    }

    public void setCompileUnit(String directory, String file) {
        this.compileUnitDirectory = directory == null ? "" : directory;
        this.compileUnitFile = file == null ? "" : file;
    }

    public void putInstruction(IrInstruction inst, SourceLocation loc) {
        if (!enabled || inst == null || loc == null || loc == SourceLocation.UNKNOWN) {
            return;
        }
        if (loc.line() <= 0) {
            return;
        }
        instructionLocations.put(inst, loc);
    }

    public SourceLocation locationOf(IrInstruction inst) {
        return instructionLocations.get(inst);
    }

    public void setFunction(IrFunction fn, String displayName, SourceLocation loc) {
        if (!enabled || fn == null) {
            return;
        }
        if (displayName != null) {
            functionDisplayNames.put(fn, displayName);
        }
        if (loc != null && loc != SourceLocation.UNKNOWN && loc.line() > 0) {
            functionLocations.put(fn, loc);
        }
    }

    public SourceLocation functionLocation(IrFunction fn) {
        return functionLocations.get(fn);
    }

    public String functionDisplayName(IrFunction fn) {
        return functionDisplayNames.getOrDefault(fn, fn == null ? "" : fn.name());
    }

    public boolean hasFunctionDebug(IrFunction fn) {
        return functionLocations.containsKey(fn);
    }

    public void recordLocal(
            IrFunction function,
            IrInstruction allocaInst,
            IrValue.Local slot,
            String sourceName,
            SourceLocation location,
            boolean isParameter,
            int argIndex
    ) {
        recordLocal(function, allocaInst, slot, sourceName, location, isParameter, argIndex, null);
    }

    public void recordLocal(
            IrFunction function,
            IrInstruction allocaInst,
            IrValue.Local slot,
            String sourceName,
            SourceLocation location,
            boolean isParameter,
            int argIndex,
            String javaTypeName
    ) {
        if (!enabled || function == null || slot == null || sourceName == null) {
            return;
        }
        LocalVar lv = new LocalVar(
                function, slot, sourceName, location, isParameter, argIndex, javaTypeName);
        locals.add(lv);
        if (allocaInst != null) {
            allocaLocals.put(allocaInst, lv);
        }
    }

    public List<LocalVar> locals() {
        return List.copyOf(locals);
    }

    public LocalVar localForAlloca(IrInstruction alloca) {
        return allocaLocals.get(alloca);
    }

    public Map<IrFunction, List<LocalVar>> localsByFunction() {
        Map<IrFunction, List<LocalVar>> map = new LinkedHashMap<>();
        for (LocalVar lv : locals) {
            map.computeIfAbsent(lv.function(), k -> new ArrayList<>()).add(lv);
        }
        return map;
    }
}
