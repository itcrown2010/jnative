package io.jnative.backend.llvm;

import io.jnative.ir.IrModule;
import io.jnative.layout.ClassLayout;
import io.jnative.layout.FieldLayout;
import io.jnative.semantic.ClassMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Sidecar JSON describing runtime layouts for LLDB formatters.
 * Written next to the executable when {@code --debug} is enabled.
 */
public final class DebugCatalogEmitter {
    private DebugCatalogEmitter() {
    }

    public static void write(Path executable, IrModule module) throws IOException {
        if (executable == null || module == null) {
            return;
        }
        Path out = Path.of(executable.toAbsolutePath().normalize() + ".jn-debug.json");
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"version\": 1,\n");
        sb.append("  \"pointerSize\": 8,\n");
        sb.append("  \"classMeta\": {\n");
        sb.append("    \"namePtrOffset\": 32,\n");
        sb.append("    \"flagsOffset\": 24,\n");
        sb.append("    \"instanceSizeOffset\": 16\n");
        sb.append("  },\n");
        sb.append("  \"string\": { \"lengthOffset\": 8, \"dataOffset\": 16, \"dataInline\": true },\n");
        sb.append("  \"array\": { \"lengthOffset\": 8, \"dataOffset\": 16, \"dataInline\": true },\n");
        sb.append("  \"maxStringBytes\": 256,\n");
        sb.append("  \"maxArrayElements\": 16,\n");
        sb.append("  \"objectHeaderSize\": 8,\n");
        sb.append("  \"classes\": {\n");
        boolean first = true;
        Map<String, ClassLayout> layouts = module.classLayouts();
        for (Map.Entry<String, ClassLayout> e : layouts.entrySet()) {
            ClassLayout layout = e.getValue();
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            sb.append("    \"").append(escape(layout.className())).append("\": {\n");
            sb.append("      \"instanceSize\": ").append(layout.instanceSize()).append(",\n");
            sb.append("      \"fields\": [\n");
            for (int i = 0; i < layout.fields().size(); i++) {
                FieldLayout f = layout.fields().get(i);
                if (i > 0) {
                    sb.append(",\n");
                }
                sb.append("        {\"name\":\"").append(escape(f.name()))
                        .append("\",\"offset\":").append(f.offset())
                        .append(",\"size\":").append(f.size())
                        .append(",\"ref\":").append(f.isReference())
                        .append(",\"llvmType\":\"").append(escape(f.llvmType()))
                        .append("\"}");
            }
            sb.append("\n      ]\n");
            sb.append("    }");
        }
        sb.append("\n  },\n");
        sb.append("  \"arrayMetas\": [\n");
        first = true;
        for (ClassMetadata meta : module.arrayMetadatas().values()) {
            if (!first) {
                sb.append(",\n");
            }
            first = false;
            int elemSize = elementSizeBytes(meta);
            sb.append("    {\"name\":\"").append(escape(meta.name()))
                    .append("\",\"refElements\":").append(meta.hasReferenceElements())
                    .append(",\"elemSize\":").append(elemSize)
                    .append("}");
        }
        sb.append("\n  ]\n");
        sb.append("}\n");
        Files.writeString(out, sb.toString(), StandardCharsets.UTF_8);
    }

    private static int elementSizeBytes(ClassMetadata meta) {
        if (meta.hasReferenceElements()) {
            return 8;
        }
        var et = meta.arrayElementType();
        if (et == null) {
            return 8;
        }
        return Math.max(1, io.jnative.layout.ObjectLayoutBuilder.sizeOf(et));
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
