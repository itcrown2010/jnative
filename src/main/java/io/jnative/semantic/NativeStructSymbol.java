/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;
import io.jnative.ffi.NativeStructLayout;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Semantic model for {@code @NativeStruct}. Distinct from {@link ClassSymbol}:
 * no Object header, ClassMetadata, vtable, GC header, inheritance, or virtual methods.
 * @author 张晓达
 */
public final class NativeStructSymbol {
    private final String name;
    private final SourceLocation location;
    private final Map<String, NativeStructFieldSymbol> fields = new LinkedHashMap<>();
    private final List<NativeStructFieldSymbol> declaredFields = new ArrayList<>();
    private NativeStructLayout layout;
    private final NativeStructType asType = new NativeStructType(this);

    public NativeStructSymbol(String name, SourceLocation location) {
        this.name = name;
        this.location = location;
    }

    public String name() {
        return name;
    }

    public SourceLocation location() {
        return location;
    }

    public NativeStructType asType() {
        return asType;
    }

    public void addField(NativeStructFieldSymbol field) {
        fields.put(field.name(), field);
        declaredFields.add(field);
    }

    public Optional<NativeStructFieldSymbol> findField(String fieldName) {
        return Optional.ofNullable(fields.get(fieldName));
    }

    public List<NativeStructFieldSymbol> declaredFields() {
        return List.copyOf(declaredFields);
    }

    public NativeStructLayout layout() {
        return layout;
    }

    public void setLayout(NativeStructLayout layout) {
        this.layout = layout;
    }
}
