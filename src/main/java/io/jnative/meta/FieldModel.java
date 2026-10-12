/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.meta;

import io.jnative.diagnostics.SourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author 张晓达
 */
public final class FieldModel {
    private final String name;
    private final TypeId type;
    private final boolean isStatic;
    private final boolean isPrivate;
    private final List<AnnotationModel> annotations;
    private final SourceLocation location;

    public FieldModel(
            String name,
            TypeId type,
            boolean isStatic,
            boolean isPrivate,
            List<AnnotationModel> annotations,
            SourceLocation location
    ) {
        this.name = name;
        this.type = type;
        this.isStatic = isStatic;
        this.isPrivate = isPrivate;
        this.annotations = annotations == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(annotations));
        this.location = location == null ? SourceLocation.UNKNOWN : location;
    }

    public String name() {
        return name;
    }

    public TypeId type() {
        return type;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public List<AnnotationModel> annotations() {
        return annotations;
    }

    public SourceLocation location() {
        return location;
    }

    public boolean hasAnnotation(String simpleName) {
        for (AnnotationModel a : annotations) {
            if (a.name().equals(simpleName)) {
                return true;
            }
        }
        return false;
    }

    public AnnotationModel annotation(String simpleName) {
        for (AnnotationModel a : annotations) {
            if (a.name().equals(simpleName)) {
                return a;
            }
        }
        return null;
    }
}
