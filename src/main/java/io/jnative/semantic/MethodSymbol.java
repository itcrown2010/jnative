package io.jnative.semantic;

import io.jnative.diagnostics.SourceLocation;
import io.jnative.ffi.NativeFunction;

import java.util.List;
import java.util.Objects;

public final class MethodSymbol {
    private final ClassSymbol owner;
    private final String name;
    private final JType returnType;
    private final List<ParameterSymbol> parameters;
    private final boolean isStatic;
    private final boolean isMain;
    private final boolean isVirtual;
    private final boolean isNative;
    private final String mangledName;
    private final SourceLocation location;
    private Integer vtableSlot;
    private MethodSymbol overrides;
    private NativeFunction nativeFunction;
    private boolean ffiIntrinsic;
    private String samInvokeRuntime;
    /** Declared contracts from @NoGC/@NoThrow/@NoBlock/@Deterministic. */
    private FunctionEffects declaredEffects = FunctionEffects.none();
    private boolean contractNoGc;
    private boolean contractNoThrow;
    private boolean contractNoBlock;
    /** Inferred from body + callees (filled by EffectAnalyzer). */
    private FunctionEffects inferredEffects = FunctionEffects.unknown();
    /** Effective = inferred (after propagation). */
    private FunctionEffects effectiveEffects = FunctionEffects.unknown();
    /** Trusted native effects from @Native(effects=...). */
    private String nativeEffectsContract;

    public MethodSymbol(
            ClassSymbol owner,
            String name,
            JType returnType,
            List<ParameterSymbol> parameters,
            boolean isStatic,
            boolean isMain,
            String mangledName,
            SourceLocation location
    ) {
        this(owner, name, returnType, parameters, isStatic, isMain, false, mangledName, location);
    }

    public MethodSymbol(
            ClassSymbol owner,
            String name,
            JType returnType,
            List<ParameterSymbol> parameters,
            boolean isStatic,
            boolean isMain,
            boolean isNative,
            String mangledName,
            SourceLocation location
    ) {
        this.owner = owner;
        this.name = name;
        this.returnType = returnType;
        this.parameters = List.copyOf(parameters);
        this.isStatic = isStatic;
        this.isMain = isMain;
        this.isNative = isNative;
        this.isVirtual = !isStatic && !isMain && !isNative;
        this.mangledName = mangledName;
        this.location = location;
    }

    public ClassSymbol owner() {
        return owner;
    }

    public String name() {
        return name;
    }

    public JType returnType() {
        return returnType;
    }

    public List<ParameterSymbol> parameters() {
        return parameters;
    }

    public List<JType> parameterTypes() {
        return parameters.stream().map(ParameterSymbol::type).toList();
    }

    public boolean isStatic() {
        return isStatic;
    }

    public boolean isMain() {
        return isMain;
    }

    public boolean isVirtual() {
        return isVirtual;
    }

    public boolean isNative() {
        return isNative;
    }

    public String mangledName() {
        return mangledName;
    }

    public SourceLocation location() {
        return location;
    }

    public Integer vtableSlot() {
        return vtableSlot;
    }

    public void setVtableSlot(Integer vtableSlot) {
        this.vtableSlot = vtableSlot;
    }

    public MethodSymbol overrides() {
        return overrides;
    }

    public void setOverrides(MethodSymbol overrides) {
        this.overrides = overrides;
    }

    public NativeFunction nativeFunction() {
        return nativeFunction;
    }

    public void setNativeFunction(NativeFunction nativeFunction) {
        this.nativeFunction = nativeFunction;
    }

    public boolean isFfiIntrinsic() {
        return ffiIntrinsic;
    }

    public void setFfiIntrinsic(boolean ffiIntrinsic) {
        this.ffiIntrinsic = ffiIntrinsic;
    }

    public String samInvokeRuntime() {
        return samInvokeRuntime;
    }

    public void setSamInvokeRuntime(String samInvokeRuntime) {
        this.samInvokeRuntime = samInvokeRuntime;
    }

    public FunctionEffects declaredEffects() {
        return declaredEffects;
    }

    public void setDeclaredEffects(FunctionEffects declaredEffects) {
        this.declaredEffects = declaredEffects != null ? declaredEffects : FunctionEffects.none();
    }

    public boolean contractNoGc() {
        return contractNoGc;
    }

    public void setContractNoGc(boolean contractNoGc) {
        this.contractNoGc = contractNoGc;
    }

    public boolean contractNoThrow() {
        return contractNoThrow;
    }

    public void setContractNoThrow(boolean contractNoThrow) {
        this.contractNoThrow = contractNoThrow;
    }

    public boolean contractNoBlock() {
        return contractNoBlock;
    }

    public void setContractNoBlock(boolean contractNoBlock) {
        this.contractNoBlock = contractNoBlock;
    }

    public FunctionEffects inferredEffects() {
        return inferredEffects;
    }

    public void setInferredEffects(FunctionEffects inferredEffects) {
        this.inferredEffects = inferredEffects != null ? inferredEffects : FunctionEffects.unknown();
    }

    public FunctionEffects effectiveEffects() {
        return effectiveEffects;
    }

    public void setEffectiveEffects(FunctionEffects effectiveEffects) {
        this.effectiveEffects = effectiveEffects != null ? effectiveEffects : FunctionEffects.unknown();
    }

    public String nativeEffectsContract() {
        return nativeEffectsContract;
    }

    public void setNativeEffectsContract(String nativeEffectsContract) {
        this.nativeEffectsContract = nativeEffectsContract;
    }

    public boolean sameSignatureAs(MethodSymbol other) {
        if (!Objects.equals(returnType, other.returnType)) {
            return false;
        }
        List<JType> a = parameterTypes();
        List<JType> b = other.parameterTypes();
        if (a.size() != b.size()) {
            return false;
        }
        for (int i = 0; i < a.size(); i++) {
            if (!signatureTypeEquals(a.get(i), b.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean signatureTypeEquals(JType a, JType b) {
        if (a == b) {
            return true;
        }
        if (a instanceof ClassType ca && b instanceof ClassType cb) {
            return ca.name().equals(cb.name());
        }
        if (a instanceof ArrayType aa && b instanceof ArrayType ab) {
            return signatureTypeEquals(aa.elementType(), ab.elementType());
        }
        if (a instanceof NativePtrType && b instanceof NativePtrType) {
            return true;
        }
        return a.equals(b);
    }
}
