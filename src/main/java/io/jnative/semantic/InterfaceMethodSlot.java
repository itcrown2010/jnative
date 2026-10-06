package io.jnative.semantic;

/**
 * Stable slot within an interface's ITable layout.
 */
public record InterfaceMethodSlot(
        int slot,
        InterfaceSymbol owner,
        MethodSymbol method
) {
    public String name() {
        return method.name();
    }

    public JType returnType() {
        return method.returnType();
    }

    public java.util.List<JType> parameterTypes() {
        return method.parameterTypes();
    }
}
