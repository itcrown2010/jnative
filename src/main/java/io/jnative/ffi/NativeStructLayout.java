package io.jnative.ffi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ABI layout for a {@code @NativeStruct}. Sole source of native field offsets/sizes.
 * No GC header, ClassMetadata, or vtable.
 */
public final class NativeStructLayout {
    private final String name;
    private final long size;
    private final int alignment;
    private final List<NativeStructField> fields;
    private final String llvmTypeName;

    public NativeStructLayout(
            String name,
            long size,
            int alignment,
            List<NativeStructField> fields,
            String llvmTypeName
    ) {
        this.name = name;
        this.size = size;
        this.alignment = alignment;
        this.fields = List.copyOf(fields);
        this.llvmTypeName = llvmTypeName;
    }

    public String name() {
        return name;
    }

    public long size() {
        return size;
    }

    public int alignment() {
        return alignment;
    }

    public List<NativeStructField> fields() {
        return fields;
    }

    public String llvmTypeName() {
        return llvmTypeName;
    }

    public Optional<NativeStructField> findField(String fieldName) {
        return fields.stream()
                .filter(f -> !f.isPadding() && f.name().equals(fieldName))
                .findFirst();
    }

    /** Human-readable layout dump for {@code --emit-layout}. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("NativeStruct ").append(name).append('\n');
        sb.append("size=").append(size).append('\n');
        sb.append("align=").append(alignment).append('\n');
        sb.append('\n');
        for (NativeStructField f : fields) {
            if (f.isPadding()) {
                long end = f.offset() + f.size() - 1;
                sb.append("padding @").append(f.offset());
                if (f.size() > 1) {
                    sb.append("..").append(end);
                }
                sb.append('\n');
            } else {
                sb.append(f.name()).append(" @").append(f.offset())
                        .append(" size=").append(f.size()).append('\n');
            }
        }
        return sb.toString();
    }

    public List<NativeStructField> nonPaddingFields() {
        List<NativeStructField> out = new ArrayList<>();
        for (NativeStructField f : fields) {
            if (!f.isPadding()) {
                out.add(f);
            }
        }
        return out;
    }
}
