package io.jnative.meta;

import io.jnative.diagnostics.SourceLocation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Class / interface / enum view for processors — stable Extension API.
 * @author 张晓达
 */
public final class ClassModel {
    public enum Kind {
        CLASS,
        INTERFACE,
        ENUM
    }

    private final TypeId typeId;
    private final Kind kind;
    private final List<AnnotationModel> annotations;
    private final List<FieldModel> fields;
    private final List<MethodModel> methods;
    private final TypeId superClass;
    private final List<TypeId> interfaces;
    private final String sourceFile;
    private final SourceLocation location;

    public ClassModel(
            TypeId typeId,
            Kind kind,
            List<AnnotationModel> annotations,
            List<FieldModel> fields,
            List<MethodModel> methods,
            TypeId superClass,
            List<TypeId> interfaces,
            String sourceFile,
            SourceLocation location
    ) {
        this.typeId = typeId;
        this.kind = kind == null ? Kind.CLASS : kind;
        this.annotations = annotations == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(annotations));
        this.fields = fields == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(fields));
        this.methods = methods == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(methods));
        this.superClass = superClass;
        this.interfaces = interfaces == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(interfaces));
        this.sourceFile = sourceFile == null ? "" : sourceFile;
        this.location = location == null ? SourceLocation.UNKNOWN : location;
    }

    public TypeId typeId() {
        return typeId;
    }

    public String simpleName() {
        return typeId.simpleName();
    }

    public Kind kind() {
        return kind;
    }

    public boolean isInterface() {
        return kind == Kind.INTERFACE;
    }

    public List<AnnotationModel> annotations() {
        return annotations;
    }

    public List<FieldModel> fields() {
        return fields;
    }

    public List<MethodModel> methods() {
        return methods;
    }

    public TypeId superClass() {
        return superClass;
    }

    public List<TypeId> interfaces() {
        return interfaces;
    }

    public String sourceFile() {
        return sourceFile;
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
