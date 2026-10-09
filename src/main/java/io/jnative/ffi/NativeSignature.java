package io.jnative.ffi;

import java.util.List;

/**
 * @author 张晓达
 */
public record NativeSignature(AbiType returnType, List<AbiType> parameters) {
    public NativeSignature {
        parameters = List.copyOf(parameters);
    }
}
