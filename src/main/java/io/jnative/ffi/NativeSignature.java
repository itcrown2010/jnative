package io.jnative.ffi;

import java.util.List;

public record NativeSignature(AbiType returnType, List<AbiType> parameters) {
    public NativeSignature {
        parameters = List.copyOf(parameters);
    }
}
