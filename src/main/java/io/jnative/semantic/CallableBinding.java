package io.jnative.semantic;

import io.jnative.ast.BlockStatement;
import io.jnative.ast.Expression;
import io.jnative.ast.LambdaParameter;

import java.util.List;

/**
 * Resolved callable: SAM object layout + code/env symbols for IR lowering.
 */
public final class CallableBinding {
    private final ClassSymbol samClass;
    private final String codeSymbolName;
    private final ClassSymbol envClass;
    private final List<ClosureCapture> captures;
    private FunctionEffects effects;
    private final boolean isNativeCallback;
    private final boolean isVirtualMethodRef;
    private final ClassType boundReceiverType;
    private final String samInvokeRuntime;
    private final Expression bodyExpression;
    private final BlockStatement bodyBlock;
    private final List<LambdaParameter> lambdaParameters;
    private final MethodSymbol targetMethod;
    private final Expression receiverExpression;

    public CallableBinding(
            ClassSymbol samClass,
            String codeSymbolName,
            ClassSymbol envClass,
            List<ClosureCapture> captures,
            FunctionEffects effects,
            boolean isNativeCallback,
            boolean isVirtualMethodRef,
            ClassType boundReceiverType,
            String samInvokeRuntime,
            Expression bodyExpression,
            BlockStatement bodyBlock,
            List<LambdaParameter> lambdaParameters,
            MethodSymbol targetMethod,
            Expression receiverExpression
    ) {
        this.samClass = samClass;
        this.codeSymbolName = codeSymbolName;
        this.envClass = envClass;
        this.captures = List.copyOf(captures);
        this.effects = effects != null ? effects : FunctionEffects.unknown();
        this.isNativeCallback = isNativeCallback;
        this.isVirtualMethodRef = isVirtualMethodRef;
        this.boundReceiverType = boundReceiverType;
        this.samInvokeRuntime = samInvokeRuntime;
        this.bodyExpression = bodyExpression;
        this.bodyBlock = bodyBlock;
        this.lambdaParameters = lambdaParameters != null ? List.copyOf(lambdaParameters) : List.of();
        this.targetMethod = targetMethod;
        this.receiverExpression = receiverExpression;
    }

    public boolean hasEnv() {
        return envClass != null;
    }

    public ClassSymbol samClass() {
        return samClass;
    }

    public String codeSymbolName() {
        return codeSymbolName;
    }

    public ClassSymbol envClass() {
        return envClass;
    }

    public List<ClosureCapture> captures() {
        return captures;
    }

    public FunctionEffects effects() {
        return effects;
    }

    public void setEffects(FunctionEffects effects) {
        this.effects = effects != null ? effects : FunctionEffects.unknown();
    }

    public boolean isNativeCallback() {
        return isNativeCallback;
    }

    public boolean isVirtualMethodRef() {
        return isVirtualMethodRef;
    }

    public ClassType boundReceiverType() {
        return boundReceiverType;
    }

    public String samInvokeRuntime() {
        return samInvokeRuntime;
    }

    public Expression bodyExpression() {
        return bodyExpression;
    }

    public BlockStatement bodyBlock() {
        return bodyBlock;
    }

    public List<LambdaParameter> lambdaParameters() {
        return lambdaParameters;
    }

    public MethodSymbol targetMethod() {
        return targetMethod;
    }

    public Expression receiverExpression() {
        return receiverExpression;
    }
}
