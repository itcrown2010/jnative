package io.jnative.meta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ParameterModel {
    private final String name;
    private final TypeId type;
    private final List<AnnotationModel> annotations;

    public ParameterModel(String name, TypeId type, List<AnnotationModel> annotations) {
        this.name = name;
        this.type = type;
        this.annotations = annotations == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(annotations));
    }

    public String name() {
        return name;
    }

    public TypeId type() {
        return type;
    }

    public List<AnnotationModel> annotations() {
        return annotations;
    }
}
