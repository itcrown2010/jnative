package io.jnative.semantic;

/**
 * Resolved Native.callback call site: trampoline symbol + invoke helper for IR lowering.
 * @author 张晓达
 */
public record NativeCallbackSite(
        String trampolineSymbol,
        String invokeRuntime,
        int callbackId,
        FunctionType functionType
) {
}
