/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.rpc;

import java.util.ArrayList;
import java.util.List;

/**
 * @author 张晓达
 */
public final class RpcMethodModel {
    private final String name;
    private final long methodId;
    private final String httpPath;
    private final String returnTypeName;
    private final boolean returnsVoid;
    private final boolean returnsFuture;
    private final boolean returnsList;
    private final String listElementType;
    private final List<RpcParameterModel> parameters = new ArrayList<>();
    private boolean authenticated;
    private String role;
    private String permission;
    private int timeoutMs;
    private boolean idempotent;
    private boolean stream;
    private String httpMethod = "POST";

    public RpcMethodModel(
            String name,
            long methodId,
            String httpPath,
            String returnTypeName,
            boolean returnsVoid,
            boolean returnsFuture,
            boolean returnsList,
            String listElementType
    ) {
        this.name = name;
        this.methodId = methodId;
        this.httpPath = httpPath;
        this.returnTypeName = returnTypeName;
        this.returnsVoid = returnsVoid;
        this.returnsFuture = returnsFuture;
        this.returnsList = returnsList;
        this.listElementType = listElementType;
    }

    public String name() {
        return name;
    }

    public long methodId() {
        return methodId;
    }

    public String httpPath() {
        return httpPath;
    }

    public String returnTypeName() {
        return returnTypeName;
    }

    public boolean returnsVoid() {
        return returnsVoid;
    }

    public boolean returnsFuture() {
        return returnsFuture;
    }

    public boolean returnsList() {
        return returnsList;
    }

    public String listElementType() {
        return listElementType;
    }

    public List<RpcParameterModel> parameters() {
        return parameters;
    }

    public boolean authenticated() {
        return authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public String role() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String permission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public int timeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public boolean idempotent() {
        return idempotent;
    }

    public void setIdempotent(boolean idempotent) {
        this.idempotent = idempotent;
    }

    public boolean stream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public String httpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String logicalReturnType() {
        if (returnsList) {
            return "ArrayList<" + listElementType + ">";
        }
        if (returnsVoid) {
            return "void";
        }
        return returnTypeName;
    }

    public String codecTypeName() {
        if (returnsList) {
            return listElementType;
        }
        return returnTypeName;
    }
}
