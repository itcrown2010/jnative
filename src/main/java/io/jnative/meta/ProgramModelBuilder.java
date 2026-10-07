package io.jnative.meta;

import io.jnative.ast.ClassDeclaration;
import io.jnative.ast.CompilationUnit;
import io.jnative.ast.FieldDeclaration;
import io.jnative.ast.MarkerAnnotation;
import io.jnative.ast.MethodDeclaration;
import io.jnative.ast.Parameter;
import io.jnative.ast.TypeName;
import io.jnative.diagnostics.SourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Builds a stable {@link ProgramModel} from a parsed {@link CompilationUnit}. */
public final class ProgramModelBuilder {

    private ProgramModelBuilder() {
    }

    public static ProgramModel build(CompilationUnit unit, String targetTriple) {
        List<ClassModel> types = new ArrayList<>();
        if (unit == null) {
            return new ProgramModel(types, "", targetTriple);
        }
        Map<String, List<MarkerAnnotation>> typeAnns = unit.typeAnnotations();
        Map<String, List<MarkerAnnotation>> methodAnns = unit.methodAnnotations();
        Map<String, List<MarkerAnnotation>> fieldAnns =
                unit.fieldAnnotations() == null ? Map.of() : unit.fieldAnnotations();

        for (ClassDeclaration cd : unit.classes()) {
            List<AnnotationModel> anns = toAnnotations(typeAnns.getOrDefault(cd.name(), List.of()));
            List<FieldModel> fields = new ArrayList<>();
            for (FieldDeclaration fd : cd.fields()) {
                String key = cd.name() + "." + fd.name();
                fields.add(new FieldModel(
                        fd.name(),
                        typeIdOf(fd.type(), fd.isArray()),
                        fd.isStatic(),
                        false,
                        toAnnotations(fieldAnns.getOrDefault(key, List.of())),
                        fd.location()));
            }
            List<MethodModel> methods = new ArrayList<>();
            for (MethodDeclaration md : cd.methods()) {
                if (md.isConstructor()) {
                    continue;
                }
                String key = cd.name() + "." + md.name();
                List<ParameterModel> params = new ArrayList<>();
                for (Parameter p : md.parameters()) {
                    params.add(new ParameterModel(
                            p.name(),
                            typeIdOf(p.type(), p.isArray()),
                            List.of()));
                }
                methods.add(new MethodModel(
                        md.name(),
                        typeIdOf(md.returnType(), md.returnsArray()),
                        params,
                        toAnnotations(methodAnns.getOrDefault(key, List.of())),
                        md.isAbstract(),
                        md.isStatic(),
                        md.location()));
            }
            TypeId superClass = cd.superClassName() == null || cd.superClassName().isBlank()
                    ? null
                    : TypeId.of(cd.superClassName());
            List<TypeId> ifaces = new ArrayList<>();
            for (TypeName tn : cd.implementedInterfaces()) {
                ifaces.add(TypeId.of(tn.name()));
            }
            ClassModel.Kind kind = cd.isInterface() ? ClassModel.Kind.INTERFACE : ClassModel.Kind.CLASS;
            types.add(new ClassModel(
                    TypeId.of(cd.name()),
                    kind,
                    anns,
                    fields,
                    methods,
                    superClass,
                    ifaces,
                    unit.fileName(),
                    cd.location()));
        }
        return new ProgramModel(types, unit.fileName(), targetTriple);
    }

    private static TypeId typeIdOf(TypeName type, boolean array) {
        if (type == null) {
            return TypeId.of("void");
        }
        String n = type.name();
        if (array) {
            n = n + "[]";
        }
        return TypeId.of(n);
    }

    private static List<AnnotationModel> toAnnotations(List<MarkerAnnotation> markers) {
        List<AnnotationModel> out = new ArrayList<>();
        for (MarkerAnnotation m : markers) {
            Map<String, String> strings = new HashMap<>();
            Map<String, Integer> ints = new HashMap<>();
            m.stringValue().ifPresent(v -> strings.put("value", v));
            m.intValue().ifPresent(v -> ints.put("value", v));
            out.add(new AnnotationModel(
                    TypeId.of(m.name()),
                    strings,
                    ints,
                    RetentionPolicy.COMPILE_TIME,
                    m.location() == null ? SourceLocation.UNKNOWN : m.location()));
        }
        return out;
    }
}
