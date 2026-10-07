package io.jnative.meta;

import io.jnative.diagnostics.SourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MethodModel {
    private final String name;
    private final TypeId returnType;
    private final List<ParameterModel> parameters;
    private final List<AnnotationModel> annotations;
    private final boolean isAbstract;
    private final boolean isStatic;
    private final SourceLocation location;

    public MethodModel(
            String name,
            TypeId returnType,
            List<ParameterModel> parameters,
            List<AnnotationModel> annotations,
            boolean isAbstract,
            boolean isStatic,
            SourceLocation location
    ) {
        this.name = name;
        this.returnType = returnType;
        this.parameters = parameters == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(parameters));
        this.annotations = annotations == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(annotations));
        this.isAbstract = isAbstract;
        this.isStatic = isStatic;
        this.location = location == null ? SourceLocation.UNKNOWN : location;
    }

    public String name() {
        return name;
    }

    public TypeId returnType() {
        return returnType;
    }

    public List<ParameterModel> parameters() {
        return parameters;
    }

    public List<AnnotationModel> annotations() {
        return annotations;
    }

    public boolean isAbstract() {
        return isAbstract;
    }

    public boolean isStatic() {
        return isStatic;
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
