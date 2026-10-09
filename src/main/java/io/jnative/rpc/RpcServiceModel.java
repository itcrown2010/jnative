package io.jnative.rpc;

import java.util.ArrayList;
import java.util.List;

/** Compile-time model of one @Rpc service interface.
 * @author 张晓达
 */
public final class RpcServiceModel {
    private final String interfaceName;
    private final String path;
    private final int version;
    private final long serviceId;
    private final List<RpcMethodModel> methods = new ArrayList<>();

    public RpcServiceModel(String interfaceName, String path, int version, long serviceId) {
        this.interfaceName = interfaceName;
        this.path = path;
        this.version = version;
        this.serviceId = serviceId;
    }

    public String interfaceName() {
        return interfaceName;
    }

    public String path() {
        return path;
    }

    public int version() {
        return version;
    }

    public long serviceId() {
        return serviceId;
    }

    public List<RpcMethodModel> methods() {
        return methods;
    }

    public String generatedHelperName() {
        return interfaceName + "_Rpc";
    }

    public String generatedDispatcherPrefix() {
        return interfaceName + "_RpcDispatch_";
    }

    public String generatedClientName() {
        return interfaceName + "_RpcClient";
    }
}
