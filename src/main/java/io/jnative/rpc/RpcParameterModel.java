package io.jnative.rpc;

public final class RpcParameterModel {
    private final String name;
    private final String typeName;
    private final boolean list;
    private final String listElementType;
    private boolean sensitive;
    private boolean valid;

    public RpcParameterModel(String name, String typeName, boolean list, String listElementType) {
        this.name = name;
        this.typeName = typeName;
        this.list = list;
        this.listElementType = listElementType;
    }

    public String name() {
        return name;
    }

    public String typeName() {
        return typeName;
    }

    public boolean list() {
        return list;
    }

    public String listElementType() {
        return listElementType;
    }

    public boolean sensitive() {
        return sensitive;
    }

    public void setSensitive(boolean sensitive) {
        this.sensitive = sensitive;
    }

    public boolean valid() {
        return valid;
    }

    public void setValid(boolean valid) {
        this.valid = valid;
    }

    public boolean isPrimitive() {
        return typeName.equals("int")
                || typeName.equals("long")
                || typeName.equals("boolean")
                || typeName.equals("double")
                || typeName.equals("float")
                || typeName.equals("byte")
                || typeName.equals("short")
                || typeName.equals("char");
    }

    public boolean isString() {
        return typeName.equals("String");
    }
}
