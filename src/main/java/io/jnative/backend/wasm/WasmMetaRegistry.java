package io.jnative.backend.wasm;

import io.jnative.ir.IrFunction;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrType;
import io.jnative.semantic.ClassMetadata;
import io.jnative.vtable.ITable;
import io.jnative.vtable.ITableEntry;
import io.jnative.vtable.VTable;
import io.jnative.vtable.VTableEntry;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compile-time typeId / vtable / itable maps for wasm32-browser.
 * Objects store {@code i32 typeId} at offset 0 (not a ClassMetadata*).
 * @author 张晓达
 */
public final class WasmMetaRegistry {

    private final Map<String, Integer> metaToTypeId = new LinkedHashMap<>();
    private final Map<Integer, String> typeIdToMeta = new HashMap<>();
    private final Map<Integer, Integer> superOf = new HashMap<>();
    /** typeId → (slot → mangled impl) */
    private final Map<Integer, Map<Integer, String>> vtables = new HashMap<>();
    /** typeId → (slot → slot-owner method simple name) */
    private final Map<Integer, Map<Integer, String>> vtableSlotNames = new HashMap<>();
    /** typeId → (interfaceId → (slot → mangled impl)) */
    private final Map<Integer, Map<Integer, Map<Integer, String>>> itables = new HashMap<>();
    /** interfaceId → set of typeIds that implement it */
    private final Map<Integer, Set<Integer>> interfaceImplementors = new HashMap<>();

    /** funcref table: code symbol → table index */
    private final Map<String, Integer> funcrefIndex = new LinkedHashMap<>();
    private final List<String> funcrefSymbols = new ArrayList<>();
    /** typeId → reference field byte offsets from payload start */
    private final Map<Integer, List<Integer>> refFieldOffsets = new HashMap<>();
    /** typeId → GC flags (ARRAY / REF_ELEMS) */
    private final Map<Integer, Integer> typeFlags = new HashMap<>();

    public WasmMetaRegistry(IrModule module) {
        int next = 1; // 0 = null / unknown
        for (ClassMetadata meta : module.classMetadatas().values()) {
            if (!metaToTypeId.containsKey(meta.globalName())) {
                metaToTypeId.put(meta.globalName(), next);
                typeIdToMeta.put(next, meta.globalName());
                next++;
            }
        }
        for (ClassMetadata meta : module.arrayMetadatas().values()) {
            if (!metaToTypeId.containsKey(meta.globalName())) {
                metaToTypeId.put(meta.globalName(), next);
                typeIdToMeta.put(next, meta.globalName());
                next++;
            }
        }
        for (ClassMetadata meta : module.classMetadatas().values()) {
            int id = metaToTypeId.get(meta.globalName());
            ClassMetadata sup = meta.superMetadata();
            if (sup != null && metaToTypeId.containsKey(sup.globalName())) {
                superOf.put(id, metaToTypeId.get(sup.globalName()));
            } else {
                superOf.put(id, 0);
            }
            VTable vt = meta.vtable();
            if (vt != null) {
                Map<Integer, String> slots = new HashMap<>();
                Map<Integer, String> names = new HashMap<>();
                for (VTableEntry e : vt.entries()) {
                    slots.put(e.slot(), e.mangledImplementation());
                    if (e.slotOwner() != null) {
                        names.put(e.slot(), e.slotOwner().name());
                    }
                }
                vtables.put(id, slots);
                vtableSlotNames.put(id, names);
            }
            Map<Integer, Map<Integer, String>> byIface = new HashMap<>();
            for (ITable it : meta.itables()) {
                int ifaceId = it.interfaceSymbol().interfaceId();
                Map<Integer, String> slots = new HashMap<>();
                for (ITableEntry e : it.entries()) {
                    slots.put(e.slot(), e.mangledImplementation());
                }
                byIface.put(ifaceId, slots);
                interfaceImplementors.computeIfAbsent(ifaceId, k -> new LinkedHashSet<>()).add(id);
            }
            if (!byIface.isEmpty()) {
                itables.put(id, byIface);
            }
            List<Integer> refs = new ArrayList<>();
            for (Long off : meta.referenceFieldByteOffsets()) {
                refs.add(off.intValue());
            }
            refFieldOffsets.put(id, refs);
            typeFlags.put(id, meta.flags());
        }
        for (ClassMetadata meta : module.arrayMetadatas().values()) {
            int id = metaToTypeId.get(meta.globalName());
            superOf.putIfAbsent(id, 0);
            refFieldOffsets.putIfAbsent(id, List.of());
            typeFlags.put(id, meta.flags());
        }
    }

    public List<Integer> refFieldOffsets(int typeId) {
        return refFieldOffsets.getOrDefault(typeId, List.of());
    }

    public int typeFlags(int typeId) {
        return typeFlags.getOrDefault(typeId, 0);
    }

    public boolean isArrayType(int typeId) {
        return (typeFlags(typeId) & ClassMetadata.FLAG_ARRAY) != 0;
    }

    public boolean hasRefElements(int typeId) {
        return (typeFlags(typeId) & ClassMetadata.FLAG_REF_ELEMENTS) != 0;
    }

    public int maxTypeId() {
        int max = 0;
        for (Integer id : metaToTypeId.values()) {
            max = Math.max(max, id);
        }
        return max;
    }

    public int typeId(String metadataGlobal) {
        if (metadataGlobal == null) {
            return 0;
        }
        Integer id = metaToTypeId.get(metadataGlobal);
        if (id == null) {
            throw new IllegalStateException("wasm: unknown metadata global " + metadataGlobal);
        }
        return id;
    }

    public Integer typeIdOrNull(String metadataGlobal) {
        return metadataGlobal == null ? null : metaToTypeId.get(metadataGlobal);
    }

    public int superTypeId(int typeId) {
        return superOf.getOrDefault(typeId, 0);
    }

    public Map<String, Integer> allMetaTypeIds() {
        return metaToTypeId;
    }

    public Map<Integer, Integer> superMap() {
        return superOf;
    }

    public Map<Integer, String> vtableSlots(int typeId) {
        return vtables.getOrDefault(typeId, Map.of());
    }

    public String resolveVirtual(int typeId, int slot) {
        int tid = typeId;
        while (tid > 0) {
            Map<Integer, String> slots = vtables.get(tid);
            if (slots != null && slots.containsKey(slot)) {
                return slots.get(slot);
            }
            int sup = superOf.getOrDefault(tid, 0);
            if (sup == tid) {
                break;
            }
            tid = sup;
        }
        return null;
    }

    /**
     * Resolve virtual impl only when the slot's owning method name matches
     * (prevents cross-hierarchy collisions on the same numeric slot).
     */
    public String resolveVirtualNamed(int typeId, int slot, String methodName) {
        if (methodName != null) {
            // Walk superclass chain: inherited slots keep the owner's method name.
            int tid = typeId;
            boolean matched = false;
            while (tid > 0) {
                Map<Integer, String> names = vtableSlotNames.get(tid);
                if (names != null && methodName.equals(names.get(slot))) {
                    matched = true;
                    break;
                }
                int sup = superOf.getOrDefault(tid, 0);
                if (sup == tid) {
                    break;
                }
                tid = sup;
            }
            if (!matched) {
                return null;
            }
        }
        return resolveVirtual(typeId, slot);
    }

    public static String methodNameFromDebug(String debugName) {
        if (debugName == null) {
            return null;
        }
        int dot = debugName.indexOf('.');
        if (dot < 0 || dot + 1 >= debugName.length()) {
            return null;
        }
        return debugName.substring(dot + 1);
    }

    /** All implementations of a vtable slot across known types (for collect + dispatch). */
    public Set<String> virtualImplementations(int slot) {
        Set<String> out = new LinkedHashSet<>();
        for (Map<Integer, String> slots : vtables.values()) {
            String impl = slots.get(slot);
            if (impl != null) {
                out.add(impl);
            }
        }
        return out;
    }

    public String resolveInterface(int typeId, int interfaceId, int slot) {
        Map<Integer, Map<Integer, String>> byIface = itables.get(typeId);
        if (byIface == null) {
            return null;
        }
        Map<Integer, String> slots = byIface.get(interfaceId);
        if (slots == null) {
            return null;
        }
        return slots.get(slot);
    }

    public Set<String> interfaceImplementations(int interfaceId, int slot) {
        Set<String> out = new LinkedHashSet<>();
        Set<Integer> types = interfaceImplementors.get(interfaceId);
        if (types == null) {
            return out;
        }
        for (int typeId : types) {
            String impl = resolveInterface(typeId, interfaceId, slot);
            if (impl != null) {
                out.add(impl);
            }
        }
        return out;
    }

    public boolean typeImplements(int typeId, int interfaceId) {
        Map<Integer, Map<Integer, String>> byIface = itables.get(typeId);
        return byIface != null && byIface.containsKey(interfaceId);
    }

    public Set<Integer> implementorsOf(int interfaceId) {
        return interfaceImplementors.getOrDefault(interfaceId, Set.of());
    }

    public Set<Integer> allInterfaceIds() {
        return interfaceImplementors.keySet();
    }

    public int internFuncref(String codeSymbol) {
        Integer existing = funcrefIndex.get(codeSymbol);
        if (existing != null) {
            return existing;
        }
        int idx = funcrefSymbols.size();
        funcrefSymbols.add(codeSymbol);
        funcrefIndex.put(codeSymbol, idx);
        return idx;
    }

    public List<String> funcrefSymbols() {
        return funcrefSymbols;
    }

    public int funcrefIndexOf(String codeSymbol) {
        Integer idx = funcrefIndex.get(codeSymbol);
        if (idx == null) {
            throw new IllegalStateException("wasm: missing funcref for " + codeSymbol);
        }
        return idx;
    }

    /** WAT type string for call_indirect of a lambda/code function. */
    public static String watIndirectType(IrFunction fn) {
        StringBuilder sb = new StringBuilder("(type (func");
        for (var p : fn.params()) {
            sb.append(" (param ").append(watType(p.type())).append(")");
        }
        if (fn.returnType() != IrType.VOID) {
            sb.append(" (result ").append(watType(fn.returnType())).append(")");
        }
        sb.append("))");
        return sb.toString();
    }

    private static String watType(IrType type) {
        return switch (type) {
            case I1, I8, I16, I32, PTR, NATIVE_PTR -> "i32";
            case I64 -> "i64";
            case FLOAT -> "f32";
            case DOUBLE -> "f64";
            case VOID -> "";
        };
    }
}
