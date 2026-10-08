package io.jnative.backend.llvm;

import io.jnative.diagnostics.SourceLocation;
import io.jnative.ir.IrDebugInfo;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrType;
import io.jnative.layout.ClassLayout;
import io.jnative.layout.FieldLayout;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Builds LLVM debug-info metadata (DWARF via DI*) for source-level debugging.
 * Emits {@code DICompositeType} for user classes (flattened inheritance fields)
 * and typed pointer locals when {@link IrDebugInfo.LocalVar#javaTypeName()} is set.
 */
final class LlvmDiBuilder {
    private final IrDebugInfo info;
    private final IrModule module;
    private final StringBuilder meta = new StringBuilder();
    private int nextId = 0;
    private int cuId = -1;
    private int primaryFileId = -1;
    private int emptyId = -1;
    private final Map<IrType, Integer> basicTypes = new HashMap<>();
    private final Map<String, Integer> fileIds = new HashMap<>();
    private final Map<String, Integer> compositeIds = new HashMap<>();
    private final Map<String, Integer> pointerIds = new HashMap<>();
    private final IdentityHashMap<IrFunction, Integer> subprograms = new IdentityHashMap<>();
    private final IdentityHashMap<IrInstruction, Integer> locations = new IdentityHashMap<>();
    private final IdentityHashMap<IrDebugInfo.LocalVar, Integer> localVars = new IdentityHashMap<>();
    private final Map<String, Integer> subroutineTypes = new HashMap<>();
    private IrFunction currentFunction;

    LlvmDiBuilder(IrDebugInfo info) {
        this(info, null);
    }

    LlvmDiBuilder(IrDebugInfo info, IrModule module) {
        this.info = info;
        this.module = module;
    }

    boolean enabled() {
        return info != null && info.enabled();
    }

    void beginModule() {
        if (!enabled()) {
            return;
        }
        emptyId = md("!{}");
        String dir = info.compileUnitDirectory();
        String file = info.compileUnitFile();
        if (file == null || file.isBlank()) {
            file = "unknown.java";
        }
        if (dir == null) {
            dir = "";
        }
        try {
            if (!dir.isBlank()) {
                dir = Path.of(dir).toAbsolutePath().normalize().toString();
            }
        } catch (Exception ignored) {
        }
        String primaryKey = dir.isBlank() ? file : Path.of(dir, file).toString();
        primaryFileId = diFile(primaryKey);
        cuId = md("distinct !DICompileUnit(language: DW_LANG_C_plus_plus, file: !" + primaryFileId
                + ", producer: \"JNative\", isOptimized: false, runtimeVersion: 0, "
                + "emissionKind: FullDebug, enums: !" + emptyId + ")");
        // Builtin runtime layouts
        ensureStringComposite();
        ensureArrayComposite("int[]", basicType(IrType.I32), 4);
        if (module != null) {
            for (ClassLayout layout : module.classLayouts().values()) {
                ensureClassComposite(layout);
            }
        }
    }

    String declares() {
        if (!enabled()) {
            return "";
        }
        return "declare void @llvm.dbg.declare(metadata, metadata, metadata)\n\n";
    }

    void beginFunction(IrFunction f) {
        currentFunction = f;
        if (!enabled() || !info.hasFunctionDebug(f)) {
            return;
        }
        ensureSubprogram(f);
    }

    String functionDbgAttachment(IrFunction f) {
        if (!enabled() || !info.hasFunctionDebug(f)) {
            return "";
        }
        Integer id = subprograms.get(f);
        return id == null ? "" : " !dbg !" + id;
    }

    String dbgDeclare(IrInstruction.Alloca alloca, String llvmLocalName) {
        if (!enabled() || currentFunction == null || !info.hasFunctionDebug(currentFunction)) {
            return null;
        }
        IrDebugInfo.LocalVar lv = info.localForAlloca(alloca);
        if (lv == null) {
            return null;
        }
        int varId = localVarId(lv);
        SourceLocation loc = lv.location();
        if (loc == null || loc.line() <= 0) {
            loc = info.functionLocation(currentFunction);
        }
        int locId = locationId(loc, currentFunction);
        return "  call void @llvm.dbg.declare(metadata ptr " + llvmLocalName
                + ", metadata !" + varId + ", metadata !DIExpression()), !dbg !" + locId;
    }

    String footer() {
        if (!enabled()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("\n!llvm.dbg.cu = !{!").append(cuId).append("}\n");
        int dwarf = md("!{i32 7, !\"Dwarf Version\", i32 4}");
        int dbgVer = md("!{i32 2, !\"Debug Info Version\", i32 3}");
        int wchar = md("!{i32 1, !\"wchar_size\", i32 4}");
        sb.append("!llvm.module.flags = !{!").append(dwarf).append(", !")
                .append(dbgVer).append(", !").append(wchar).append("}\n");
        sb.append("!llvm.ident = !{!").append(md("!{!\"JNative\"}")).append("}\n\n");
        sb.append(meta);
        return sb.toString();
    }

    private int ensureSubprogram(IrFunction f) {
        Integer existing = subprograms.get(f);
        if (existing != null) {
            return existing;
        }
        SourceLocation loc = info.functionLocation(f);
        int line = loc == null ? 1 : Math.max(1, loc.line());
        int fileId = fileIdFor(loc);
        String display = info.functionDisplayName(f);
        int typeId = subroutineType(f);
        int sp = md("distinct !DISubprogram(name: \"" + escape(simpleName(display))
                + "\", linkageName: \"" + escape(f.name())
                + "\", scope: !" + fileId + ", file: !" + fileId
                + ", line: " + line + ", type: !" + typeId
                + ", scopeLine: " + line
                + ", flags: DIFlagPrototyped, spFlags: DISPFlagDefinition, unit: !" + cuId
                + ", retainedNodes: !" + emptyId + ")");
        subprograms.put(f, sp);
        return sp;
    }

    private int subroutineType(IrFunction f) {
        String key = f.returnType().name() + "#" + f.params().size();
        for (var p : f.params()) {
            key += "," + p.type().name();
        }
        Integer cached = subroutineTypes.get(key);
        if (cached != null) {
            return cached;
        }
        StringBuilder types = new StringBuilder();
        types.append("!").append(basicType(f.returnType()));
        for (var p : f.params()) {
            types.append(", !").append(basicType(p.type()));
        }
        int typesId = md("!{" + types + "}");
        int id = md("!DISubroutineType(types: !" + typesId + ")");
        subroutineTypes.put(key, id);
        return id;
    }

    private int localVarId(IrDebugInfo.LocalVar lv) {
        Integer existing = localVars.get(lv);
        if (existing != null) {
            return existing;
        }
        int sp = ensureSubprogram(lv.function());
        SourceLocation loc = lv.location();
        int line = loc == null || loc.line() <= 0 ? 1 : loc.line();
        int fileId = fileIdFor(loc != null ? loc : info.functionLocation(lv.function()));
        int typeId = typeIdForLocal(lv);
        int id;
        if (lv.isParameter()) {
            id = md("!DILocalVariable(name: \"" + escape(lv.sourceName())
                    + "\", arg: " + Math.max(1, lv.argIndex() + 1)
                    + ", scope: !" + sp + ", file: !" + fileId
                    + ", line: " + line + ", type: !" + typeId + ")");
        } else {
            id = md("!DILocalVariable(name: \"" + escape(lv.sourceName())
                    + "\", scope: !" + sp + ", file: !" + fileId
                    + ", line: " + line + ", type: !" + typeId + ")");
        }
        localVars.put(lv, id);
        return id;
    }

    private int typeIdForLocal(IrDebugInfo.LocalVar lv) {
        String jtn = lv.javaTypeName();
        if (jtn != null && !jtn.isBlank()) {
            if (jtn.endsWith("[]")) {
                String elemName = jtn.substring(0, jtn.length() - 2);
                int elemTypeId;
                int elemBytes;
                switch (elemName) {
                    case "boolean", "byte" -> {
                        elemTypeId = basicType(IrType.I8);
                        elemBytes = 1;
                    }
                    case "short", "char" -> {
                        elemTypeId = basicType(IrType.I16);
                        elemBytes = 2;
                    }
                    case "int" -> {
                        elemTypeId = basicType(IrType.I32);
                        elemBytes = 4;
                    }
                    case "long" -> {
                        elemTypeId = basicType(IrType.I64);
                        elemBytes = 8;
                    }
                    case "float" -> {
                        elemTypeId = basicType(IrType.FLOAT);
                        elemBytes = 4;
                    }
                    case "double" -> {
                        elemTypeId = basicType(IrType.DOUBLE);
                        elemBytes = 8;
                    }
                    default -> {
                        elemTypeId = basicType(IrType.PTR);
                        elemBytes = 8;
                    }
                }
                int arr = ensureArrayComposite(jtn, elemTypeId, elemBytes);
                return pointerTo(jtn, arr);
            }
            if ("String".equals(jtn)) {
                return pointerTo("String", ensureStringComposite());
            }
            if (module != null && module.classLayouts().containsKey(jtn)) {
                int comp = ensureClassComposite(module.classLayouts().get(jtn));
                return pointerTo(jtn, comp);
            }
            // Unknown class — still tag as named opaque composite pointer
            int opaque = ensureOpaqueComposite(jtn);
            return pointerTo(jtn, opaque);
        }
        return basicType(lv.slot().type());
    }

    private int ensureClassComposite(ClassLayout layout) {
        String name = layout.className();
        Integer existing = compositeIds.get(name);
        if (existing != null) {
            return existing;
        }
        // Placeholder id reserved before members (self-ref safe)
        int id = nextId++;
        compositeIds.put(name, id);
        StringBuilder members = new StringBuilder();
        // ClassMetadata* header
        int metaPtr = pointerTo("__ClassMeta", ensureOpaqueComposite("__ClassMeta"));
        int headerMember = md("!DIDerivedType(tag: DW_TAG_member, name: \"__meta\", scope: !" + id
                + ", file: !" + primaryFileId + ", line: 1, baseType: !" + metaPtr
                + ", size: 64, offset: 0)");
        members.append("!").append(headerMember);
        for (FieldLayout f : layout.fields()) {
            int bt = fieldBaseType(f);
            int member = md("!DIDerivedType(tag: DW_TAG_member, name: \"" + escape(f.name())
                    + "\", scope: !" + id + ", file: !" + primaryFileId
                    + ", line: 1, baseType: !" + bt
                    + ", size: " + (f.size() * 8)
                    + ", offset: " + (f.offset() * 8) + ")");
            members.append(", !").append(member);
        }
        int elements = md("!{" + members + "}");
        long sizeBits = Math.max(64, layout.instanceSize() * 8);
        meta.append('!').append(id).append(" = !DICompositeType(tag: DW_TAG_structure_type, name: \"")
                .append(escape(name)).append("\", file: !").append(primaryFileId)
                .append(", line: 1, size: ").append(sizeBits)
                .append(", elements: !").append(elements).append(")\n");
        return id;
    }

    private int fieldBaseType(FieldLayout f) {
        if (f.isReference()) {
            return basicType(IrType.PTR);
        }
        return switch (f.llvmType()) {
            case "i1" -> basicType(IrType.I1);
            case "i8" -> basicType(IrType.I8);
            case "i16" -> basicType(IrType.I16);
            case "i32" -> basicType(IrType.I32);
            case "i64" -> basicType(IrType.I64);
            case "float" -> basicType(IrType.FLOAT);
            case "double" -> basicType(IrType.DOUBLE);
            default -> basicType(IrType.PTR);
        };
    }

    private int ensureStringComposite() {
        Integer existing = compositeIds.get("String");
        if (existing != null) {
            return existing;
        }
        int id = nextId++;
        compositeIds.put("String", id);
        int metaPtr = pointerTo("__ClassMeta", ensureOpaqueComposite("__ClassMeta"));
        int m0 = md("!DIDerivedType(tag: DW_TAG_member, name: \"__meta\", scope: !" + id
                + ", file: !" + primaryFileId + ", baseType: !" + metaPtr
                + ", size: 64, offset: 0)");
        int m1 = md("!DIDerivedType(tag: DW_TAG_member, name: \"length\", scope: !" + id
                + ", file: !" + primaryFileId + ", baseType: !" + basicType(IrType.I32)
                + ", size: 32, offset: 64)");
        // Flexible data[1] placeholder — LLDB formatters read real UTF-8
        int m2 = md("!DIDerivedType(tag: DW_TAG_member, name: \"data\", scope: !" + id
                + ", file: !" + primaryFileId + ", baseType: !" + basicType(IrType.I8)
                + ", size: 8, offset: 128)");
        int elements = md("!{!" + m0 + ", !" + m1 + ", !" + m2 + "}");
        meta.append('!').append(id).append(" = !DICompositeType(tag: DW_TAG_structure_type, name: \"String\", file: !")
                .append(primaryFileId).append(", size: 136, elements: !").append(elements).append(")\n");
        return id;
    }

    private int ensureArrayComposite(String name, int elemTypeId, int elemSizeBytes) {
        Integer existing = compositeIds.get(name);
        if (existing != null) {
            return existing;
        }
        int id = nextId++;
        compositeIds.put(name, id);
        int metaPtr = pointerTo("__ClassMeta", ensureOpaqueComposite("__ClassMeta"));
        int m0 = md("!DIDerivedType(tag: DW_TAG_member, name: \"__meta\", scope: !" + id
                + ", file: !" + primaryFileId + ", baseType: !" + metaPtr
                + ", size: 64, offset: 0)");
        int m1 = md("!DIDerivedType(tag: DW_TAG_member, name: \"length\", scope: !" + id
                + ", file: !" + primaryFileId + ", baseType: !" + basicType(IrType.I32)
                + ", size: 32, offset: 64)");
        int m2 = md("!DIDerivedType(tag: DW_TAG_member, name: \"elements\", scope: !" + id
                + ", file: !" + primaryFileId + ", baseType: !" + elemTypeId
                + ", size: " + (elemSizeBytes * 8) + ", offset: 128)");
        int elements = md("!{!" + m0 + ", !" + m1 + ", !" + m2 + "}");
        meta.append('!').append(id).append(" = !DICompositeType(tag: DW_TAG_structure_type, name: \"")
                .append(escape(name)).append("\", file: !").append(primaryFileId)
                .append(", size: ").append(128 + elemSizeBytes * 8)
                .append(", elements: !").append(elements).append(")\n");
        return id;
    }

    private int ensureOpaqueComposite(String name) {
        Integer existing = compositeIds.get(name);
        if (existing != null) {
            return existing;
        }
        int id = md("!DICompositeType(tag: DW_TAG_structure_type, name: \"" + escape(name)
                + "\", file: !" + primaryFileId + ", size: 64, elements: !" + emptyId + ")");
        compositeIds.put(name, id);
        return id;
    }

    private int pointerTo(String key, int baseTypeId) {
        String pk = "ptr:" + key;
        Integer existing = pointerIds.get(pk);
        if (existing != null) {
            return existing;
        }
        int id = md("!DIDerivedType(tag: DW_TAG_pointer_type, baseType: !" + baseTypeId + ", size: 64)");
        pointerIds.put(pk, id);
        return id;
    }

    private int locationId(SourceLocation loc, IrFunction f) {
        int line = Math.max(1, loc == null ? 1 : loc.line());
        int col = Math.max(0, loc == null ? 0 : loc.column());
        int sp = ensureSubprogram(f);
        return md("!DILocation(line: " + line + ", column: " + col + ", scope: !" + sp + ")");
    }

    private int locationIdCached(IrInstruction inst, SourceLocation loc, IrFunction f) {
        Integer existing = locations.get(inst);
        if (existing != null) {
            return existing;
        }
        int id = locationId(loc, f);
        locations.put(inst, id);
        return id;
    }

    String locationSuffixFor(IrInstruction inst) {
        if (!enabled() || inst == null || currentFunction == null
                || !info.hasFunctionDebug(currentFunction)) {
            return "";
        }
        SourceLocation loc = info.locationOf(inst);
        if (loc == null || loc == SourceLocation.UNKNOWN || loc.line() <= 0) {
            loc = info.functionLocation(currentFunction);
        }
        if (loc == null || loc.line() <= 0) {
            return "";
        }
        int id = locationIdCached(inst, loc, currentFunction);
        return ", !dbg !" + id;
    }

    private int fileIdFor(SourceLocation loc) {
        if (loc == null || loc == SourceLocation.UNKNOWN
                || loc.file() == null || loc.file().isBlank()
                || "<unknown>".equals(loc.file())) {
            return primaryFileId;
        }
        return diFile(loc.file());
    }

    private int diFile(String pathOrName) {
        String key = pathOrName == null ? "unknown.java" : pathOrName;
        try {
            Path p = Path.of(key);
            if (p.isAbsolute() || key.contains("/") || key.contains("\\")) {
                key = p.toAbsolutePath().normalize().toString();
            }
        } catch (Exception ignored) {
        }
        Integer existing = fileIds.get(key);
        if (existing != null) {
            return existing;
        }
        String filename;
        String directory;
        try {
            Path p = Path.of(key);
            Path parent = p.getParent();
            filename = p.getFileName() == null ? key : p.getFileName().toString();
            directory = parent == null ? "" : parent.toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            filename = key;
            directory = "";
        }
        int id = md("!DIFile(filename: \"" + escape(filename) + "\", directory: \""
                + escape(directory) + "\")");
        fileIds.put(key, id);
        return id;
    }

    private int basicType(IrType type) {
        Integer cached = basicTypes.get(type);
        if (cached != null) {
            return cached;
        }
        String encoding;
        int size;
        String name;
        switch (type) {
            case I1 -> {
                name = "boolean";
                size = 8;
                encoding = "DW_ATE_boolean";
            }
            case I8 -> {
                name = "byte";
                size = 8;
                encoding = "DW_ATE_signed";
            }
            case I16 -> {
                name = "short";
                size = 16;
                encoding = "DW_ATE_signed";
            }
            case I32 -> {
                name = "int";
                size = 32;
                encoding = "DW_ATE_signed";
            }
            case I64 -> {
                name = "long";
                size = 64;
                encoding = "DW_ATE_signed";
            }
            case FLOAT -> {
                name = "float";
                size = 32;
                encoding = "DW_ATE_float";
            }
            case DOUBLE -> {
                name = "double";
                size = 64;
                encoding = "DW_ATE_float";
            }
            case PTR, NATIVE_PTR -> {
                name = "ptr";
                size = 64;
                encoding = "DW_ATE_unsigned";
            }
            default -> {
                name = "void";
                size = 0;
                encoding = "DW_ATE_unsigned";
            }
        }
        int id = md("!DIBasicType(name: \"" + name + "\", size: " + size
                + ", encoding: " + encoding + ")");
        basicTypes.put(type, id);
        return id;
    }

    private int md(String body) {
        int id = nextId++;
        meta.append('!').append(id).append(" = ").append(body).append('\n');
        return id;
    }

    private static String simpleName(String display) {
        if (display == null) {
            return "fn";
        }
        int dot = display.lastIndexOf('.');
        return dot >= 0 ? display.substring(dot + 1) : display;
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
