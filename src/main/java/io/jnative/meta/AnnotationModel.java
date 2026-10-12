/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.meta;

import io.jnative.diagnostics.SourceLocation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author 张晓达
 */
public final class AnnotationModel {
    private final TypeId type;
    private final Map<String, String> stringAttributes;
    private final Map<String, Integer> intAttributes;
    private final RetentionPolicy retention;
    private final SourceLocation location;

    public AnnotationModel(
            TypeId type,
            Map<String, String> stringAttributes,
            Map<String, Integer> intAttributes,
            RetentionPolicy retention,
            SourceLocation location
    ) {
        this.type = type;
        this.stringAttributes = stringAttributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(stringAttributes));
        this.intAttributes = intAttributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(intAttributes));
        this.retention = retention == null ? RetentionPolicy.COMPILE_TIME : retention;
        this.location = location == null ? SourceLocation.UNKNOWN : location;
    }

    public static AnnotationModel marker(String name, SourceLocation loc) {
        return new AnnotationModel(TypeId.of(name), Map.of(), Map.of(), RetentionPolicy.COMPILE_TIME, loc);
    }

    public TypeId type() {
        return type;
    }

    public String name() {
        return type.simpleName();
    }

    public RetentionPolicy retention() {
        return retention;
    }

    public SourceLocation location() {
        return location;
    }

    public Optional<String> stringValue() {
        if (stringAttributes.containsKey("value")) {
            return Optional.of(stringAttributes.get("value"));
        }
        if (stringAttributes.size() == 1) {
            return Optional.of(stringAttributes.values().iterator().next());
        }
        return Optional.empty();
    }

    public Optional<Integer> intValue() {
        if (intAttributes.containsKey("value")) {
            return Optional.of(intAttributes.get("value"));
        }
        if (intAttributes.size() == 1) {
            return Optional.of(intAttributes.values().iterator().next());
        }
        return Optional.empty();
    }

    public Map<String, String> stringAttributes() {
        return stringAttributes;
    }

    public Map<String, Integer> intAttributes() {
        return intAttributes;
    }
}
