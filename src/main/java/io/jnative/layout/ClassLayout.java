package io.jnative.layout;

import java.util.List;
import java.util.Optional;

/**
 * Layout of one class instance. Header size is reserved for future metadata/vtable
 * even when 0.2 emits headerSize == 0.
 * @author 张晓达
 */
public final class ClassLayout {
    private final String className;
    private final long headerSize;
    private final int headerAlignment;
    private final List<FieldLayout> fields;
    private final long instanceSize;
    private final int alignment;
    private final String llvmTypeName;

    public ClassLayout(
            String className,
            long headerSize,
            int headerAlignment,
            List<FieldLayout> fields,
            long instanceSize,
            int alignment,
            String llvmTypeName
    ) {
        this.className = className;
        this.headerSize = headerSize;
        this.headerAlignment = headerAlignment;
        this.fields = List.copyOf(fields);
        this.instanceSize = instanceSize;
        this.alignment = alignment;
        this.llvmTypeName = llvmTypeName;
    }

    public String className() {
        return className;
    }

    /** Bytes reserved before instance fields (0 in 0.2; future: class metadata pointer). */
    public long headerSize() {
        return headerSize;
    }

    public int headerAlignment() {
        return headerAlignment;
    }

    public List<FieldLayout> fields() {
        return fields;
    }

    public long instanceSize() {
        return instanceSize;
    }

    public int alignment() {
        return alignment;
    }

    public String llvmTypeName() {
        return llvmTypeName;
    }

    public Optional<FieldLayout> field(String name) {
        return fields.stream().filter(f -> f.name().equals(name)).findFirst();
    }

    public Optional<FieldLayout> fieldByIndex(int index) {
        return fields.stream().filter(f -> f.index() == index).findFirst();
    }

    /** GEP index of first instance field after optional header elements. */
    public int fieldGepBaseIndex() {
        return headerSize > 0 ? 1 : 0;
    }
}
