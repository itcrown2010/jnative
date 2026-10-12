/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.ffi;

import io.jnative.diagnostics.SourceLocation;
import io.jnative.semantic.NativePtrType;
import io.jnative.semantic.NativeStructFieldSymbol;
import io.jnative.semantic.NativeStructSymbol;
import io.jnative.semantic.PrimitiveType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class NativeStructLayoutTest {

    @Test
    void pointLayoutMatchesAbi() {
        NativeStructSymbol point = new NativeStructSymbol("Point", SourceLocation.UNKNOWN);
        point.addField(new NativeStructFieldSymbol(
                point, "x", PrimitiveType.DOUBLE, 0, SourceLocation.UNKNOWN));
        point.addField(new NativeStructFieldSymbol(
                point, "y", PrimitiveType.DOUBLE, 1, SourceLocation.UNKNOWN));
        NativeStructLayout layout = NativeStructLayoutBuilder.buildWithNested(
                point, X86_64SystemVAbi.INSTANCE);
        point.setLayout(layout);

        assertEquals(16, layout.size());
        assertEquals(8, layout.alignment());
        assertEquals(0, layout.findField("x").orElseThrow().offset());
        assertEquals(8, layout.findField("y").orElseThrow().offset());
    }

    @Test
    void mixedAlignmentInsertsPadding() {
        NativeStructSymbol mixed = new NativeStructSymbol("Mixed", SourceLocation.UNKNOWN);
        mixed.addField(new NativeStructFieldSymbol(
                mixed, "a", PrimitiveType.BYTE, 0, SourceLocation.UNKNOWN));
        mixed.addField(new NativeStructFieldSymbol(
                mixed, "b", PrimitiveType.LONG, 1, SourceLocation.UNKNOWN));
        mixed.addField(new NativeStructFieldSymbol(
                mixed, "c", PrimitiveType.SHORT, 2, SourceLocation.UNKNOWN));
        NativeStructLayout layout = NativeStructLayoutBuilder.buildWithNested(
                mixed, X86_64SystemVAbi.INSTANCE);

        assertEquals(0, layout.findField("a").orElseThrow().offset());
        assertEquals(8, layout.findField("b").orElseThrow().offset());
        assertEquals(16, layout.findField("c").orElseThrow().offset());
        assertEquals(24, layout.size());
        assertEquals(8, layout.alignment());
        assertTrue(layout.fields().stream().anyMatch(NativeStructField::isPadding));
        String dump = layout.describe();
        assertTrue(dump.contains("padding"));
        assertTrue(dump.contains("a @0"));
        assertTrue(dump.contains("b @8"));
    }

    @Test
    void nestedStructInlineNotPointer() {
        NativeStructSymbol point = new NativeStructSymbol("Point", SourceLocation.UNKNOWN);
        point.addField(new NativeStructFieldSymbol(
                point, "x", PrimitiveType.DOUBLE, 0, SourceLocation.UNKNOWN));
        point.addField(new NativeStructFieldSymbol(
                point, "y", PrimitiveType.DOUBLE, 1, SourceLocation.UNKNOWN));
        point.setLayout(NativeStructLayoutBuilder.buildWithNested(point, X86_64SystemVAbi.INSTANCE));

        NativeStructSymbol line = new NativeStructSymbol("Line", SourceLocation.UNKNOWN);
        line.addField(new NativeStructFieldSymbol(
                line, "a", point.asType(), 0, SourceLocation.UNKNOWN));
        line.addField(new NativeStructFieldSymbol(
                line, "b", point.asType(), 1, SourceLocation.UNKNOWN));
        NativeStructLayout layout = NativeStructLayoutBuilder.buildWithNested(
                line, X86_64SystemVAbi.INSTANCE);

        assertEquals(32, layout.size());
        assertEquals(0, layout.findField("a").orElseThrow().offset());
        assertEquals(16, layout.findField("b").orElseThrow().offset());
        assertEquals(16, layout.findField("a").orElseThrow().size());
    }

    @Test
    void nativePtrFieldUsesPointerSize() {
        NativeStructSymbol node = new NativeStructSymbol("Node", SourceLocation.UNKNOWN);
        node.addField(new NativeStructFieldSymbol(
                node, "next", NativePtrType.of(node.asType()), 0, SourceLocation.UNKNOWN));
        NativeStructLayout layout = NativeStructLayoutBuilder.buildWithNested(
                node, X86_64SystemVAbi.INSTANCE);
        assertEquals(8, layout.size());
        assertEquals(8, layout.alignment());
    }
}
