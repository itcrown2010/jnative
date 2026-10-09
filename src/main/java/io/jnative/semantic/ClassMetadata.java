package io.jnative.semantic;

import io.jnative.layout.ClassLayout;
import io.jnative.vtable.ITable;
import io.jnative.vtable.VTable;

import java.util.ArrayList;
import java.util.List;

/**
 * Runtime-facing class metadata. Objects store a pointer to this structure.
 * Layout (LLVM %jn.ClassMeta):
 *   0: vtable*
 *   1: super ClassMetadata*
 *   2: i64 instanceSize
 *   3: i32 flags (bit0=array, bit1=ref elements)
 *   4: ptr name
 *   5: i32 refFieldCount
 *   6: ptr refFieldOffsets (i64* byte offsets from object start)
 *   7: i32 interfaceCount
 *   8: ptr interfaceEntries ({i32 id, ptr itable}*)
 *
 * Object references point at ClassMetadata* (not the GC allocation header).
 * @author 张晓达
 */
public final class ClassMetadata {
    public static final int FLAG_ARRAY = 1;
    public static final int FLAG_REF_ELEMENTS = 2;

    private final ClassSymbol classSymbol;
    private ClassMetadata superMetadata;
    private ClassLayout layout;
    private VTable vtable;
    private final List<ITable> itables = new ArrayList<>();
    private final List<Integer> referenceFieldGepIndices = new ArrayList<>();
    private final List<Long> referenceFieldByteOffsets = new ArrayList<>();
    private boolean arrayType;
    private boolean referenceElements;
    private JType arrayElementType;
    private final String globalName;

    public ClassMetadata(ClassSymbol classSymbol) {
        this.classSymbol = classSymbol;
        this.globalName = "meta." + classSymbol.name();
    }

    /** Synthetic metadata for array types (e.g. int[], Person[]). */
    public ClassMetadata(String syntheticName, boolean referenceElements, JType elementType) {
        this.classSymbol = null;
        this.globalName = "meta." + syntheticName;
        this.arrayType = true;
        this.referenceElements = referenceElements;
        this.arrayElementType = elementType;
    }

    public ClassSymbol classSymbol() {
        return classSymbol;
    }

    public String globalName() {
        return globalName;
    }

    public String name() {
        return classSymbol != null ? classSymbol.name() : globalName.substring("meta.".length());
    }

    public ClassMetadata superMetadata() {
        return superMetadata;
    }

    public void setSuperMetadata(ClassMetadata superMetadata) {
        this.superMetadata = superMetadata;
    }

    public ClassLayout layout() {
        return layout;
    }

    public void setLayout(ClassLayout layout) {
        this.layout = layout;
        referenceFieldGepIndices.clear();
        referenceFieldByteOffsets.clear();
        if (layout != null) {
            for (var f : layout.fields()) {
                if (f.isReference()) {
                    referenceFieldGepIndices.add(f.index());
                    referenceFieldByteOffsets.add(f.offset());
                }
            }
        }
    }

    public List<Integer> referenceFieldGepIndices() {
        return List.copyOf(referenceFieldGepIndices);
    }

    /** Byte offsets from object start (ClassMetadata*) for GC scanning. Flattened incl. inherited. */
    public List<Long> referenceFieldByteOffsets() {
        return List.copyOf(referenceFieldByteOffsets);
    }

    public VTable vtable() {
        return vtable;
    }

    public void setVtable(VTable vtable) {
        this.vtable = vtable;
    }

    public List<ITable> itables() {
        return List.copyOf(itables);
    }

    public void setItables(List<ITable> tables) {
        itables.clear();
        if (tables != null) {
            itables.addAll(tables);
        }
    }

    public boolean isArrayType() {
        return arrayType;
    }

    public boolean hasReferenceElements() {
        return referenceElements;
    }

    public JType arrayElementType() {
        return arrayElementType;
    }

    public int flags() {
        int f = 0;
        if (arrayType) {
            f |= FLAG_ARRAY;
        }
        if (referenceElements) {
            f |= FLAG_REF_ELEMENTS;
        }
        return f;
    }

    public long instanceSize() {
        return layout != null ? layout.instanceSize() : 0;
    }
}
