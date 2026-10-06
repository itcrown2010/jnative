package io.jnative.semantic;

/**
 * Compile-time / runtime identity for an interface (not a managed object).
 */
public final class InterfaceMetadata {
    private final InterfaceSymbol interfaceSymbol;
    private final String globalName;
    private int interfaceId = -1;

    public InterfaceMetadata(InterfaceSymbol interfaceSymbol) {
        this.interfaceSymbol = interfaceSymbol;
        this.globalName = "iface." + interfaceSymbol.name();
    }

    public InterfaceSymbol interfaceSymbol() {
        return interfaceSymbol;
    }

    public String globalName() {
        return globalName;
    }

    public int interfaceId() {
        return interfaceId;
    }

    public void setInterfaceId(int interfaceId) {
        this.interfaceId = interfaceId;
    }

    public String name() {
        return interfaceSymbol.name();
    }
}
