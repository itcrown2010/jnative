package io.jnative.ffi;

import java.util.List;
import java.util.Optional;

/**
 * Resolved native function model (not an annotation string dump).
 */
public final class NativeFunction {
    private final String javaName;
    private final String symbol;
    private final Optional<String> library;
    private final NativeSignature signature;
    private final CallingConvention callingConvention;

    public NativeFunction(
            String javaName,
            String symbol,
            String library,
            NativeSignature signature,
            CallingConvention callingConvention
    ) {
        this.javaName = javaName;
        this.symbol = symbol;
        this.library = library == null || library.isBlank() ? Optional.empty() : Optional.of(library);
        this.signature = signature;
        this.callingConvention = callingConvention;
    }

    public String javaName() {
        return javaName;
    }

    public String symbol() {
        return symbol;
    }

    public Optional<String> library() {
        return library;
    }

    public NativeSignature signature() {
        return signature;
    }

    public CallingConvention callingConvention() {
        return callingConvention;
    }

    public List<AbiType> parameterAbis() {
        return signature.parameters();
    }

    public AbiType returnAbi() {
        return signature.returnType();
    }
}
