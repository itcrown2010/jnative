package io.jnative.semantic;

import java.util.List;

/**
 * First-class function type: {@code (T1, T2, ...) -> R}.
 * SAM builtin classes map to a FunctionType for target typing.
 */
public final class FunctionType implements JType {
    public enum Domain { MANAGED, NATIVE }

    private final List<JType> parameterTypes;
    private final JType returnType;
    private final Domain domain;
    /** Optional effect contract required of values of this function type (0.11). */
    private final FunctionEffects effectContract;

    public FunctionType(List<JType> parameterTypes, JType returnType, Domain domain) {
        this(parameterTypes, returnType, domain, null);
    }

    public FunctionType(
            List<JType> parameterTypes,
            JType returnType,
            Domain domain,
            FunctionEffects effectContract
    ) {
        this.parameterTypes = List.copyOf(parameterTypes);
        this.returnType = returnType;
        this.domain = domain;
        this.effectContract = effectContract;
    }

    public List<JType> parameterTypes() {
        return parameterTypes;
    }

    public JType returnType() {
        return returnType;
    }

    public Domain domain() {
        return domain;
    }

    /** Null means no additional effect constraint on the function type itself. */
    public FunctionEffects effectContract() {
        return effectContract;
    }

    @Override
    public String displayName() {
        StringBuilder sb = new StringBuilder("(");
        for (int i = 0; i < parameterTypes.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(parameterTypes.get(i).displayName());
        }
        sb.append(") -> ").append(returnType.displayName());
        return sb.toString();
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isReference() {
        return true;
    }

    @Override
    public boolean isNumeric() {
        return false;
    }

    @Override
    public boolean isInteger() {
        return false;
    }

    @Override
    public boolean isAssignableFrom(JType other) {
        if (other instanceof ErrorType || other instanceof NullType) {
            return true;
        }
        if (other instanceof FunctionType ft) {
            if (parameterTypes.size() != ft.parameterTypes.size()) {
                return false;
            }
            if (!returnType.isAssignableFrom(ft.returnType)
                    && !(returnType instanceof VoidType && ft.returnType instanceof VoidType)) {
                // allow exact void match
                if (!(returnType instanceof VoidType) || !(ft.returnType instanceof VoidType)) {
                    if (!returnType.isAssignableFrom(ft.returnType)) {
                        return false;
                    }
                }
            }
            for (int i = 0; i < parameterTypes.size(); i++) {
                if (!ft.parameterTypes.get(i).isAssignableFrom(parameterTypes.get(i))
                        && !parameterTypes.get(i).isAssignableFrom(ft.parameterTypes.get(i))) {
                    // contravariant-ish: accept equal types
                    if (!parameterTypes.get(i).equals(ft.parameterTypes.get(i))) {
                        return parameterTypes.get(i).isAssignableFrom(ft.parameterTypes.get(i))
                                || ft.parameterTypes.get(i).isAssignableFrom(parameterTypes.get(i));
                    }
                }
            }
            return domain == ft.domain;
        }
        return false;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FunctionType ft)) {
            return false;
        }
        return parameterTypes.equals(ft.parameterTypes)
                && returnType.equals(ft.returnType)
                && domain == ft.domain;
    }

    @Override
    public int hashCode() {
        return parameterTypes.hashCode() * 31 + returnType.hashCode();
    }
}
