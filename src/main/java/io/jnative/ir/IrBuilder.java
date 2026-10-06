package io.jnative.ir;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper for emitting IR into the current function/block.
 */
public final class IrBuilder {
    private final IrModule module;
    private IrFunction function;
    private IrBasicBlock entryBlock;
    private IrBasicBlock block;
    private int tempId;
    private int blockId;
    private int entryAllocaCount;
    private final Map<String, IrValue.Local> locals = new HashMap<>();
    private final Deque<IrBasicBlock> exceptionDispatchStack = new ArrayDeque<>();

    public IrBuilder(IrModule module) {
        this.module = module;
    }

    public IrModule module() {
        return module;
    }

    public void beginFunction(IrFunction function) {
        this.function = function;
        this.tempId = 0;
        this.blockId = 0;
        this.entryAllocaCount = 0;
        this.locals.clear();
        this.exceptionDispatchStack.clear();
        this.entryBlock = function.addBlock("entry");
        this.block = entryBlock;
    }

    public IrFunction function() {
        return function;
    }

    public IrBasicBlock block() {
        return block;
    }

    public boolean currentBlockTerminated() {
        return block.isTerminated();
    }

    public void setBlock(IrBasicBlock block) {
        this.block = block;
    }

    public IrBasicBlock newBlock(String hint) {
        return function.addBlock(hint + "." + (blockId++));
    }

    public IrValue.Temp temp(IrType type) {
        return new IrValue.Temp(tempId++, type);
    }

    public IrValue.Local alloca(String identifier, IrType type) {
        IrValue.Local local = new IrValue.Local(identifier, type);
        locals.put(identifier, local);
        // Always place allocas in the entry block (required for shadow-stack roots).
        entryBlock.instructions().add(entryAllocaCount++, new IrInstruction.Alloca(local));
        return local;
    }

    public IrValue.Local local(String identifier) {
        return locals.get(identifier);
    }

    public void emit(IrInstruction instruction) {
        block.add(instruction);
    }

    public IrValue load(IrValue.Local local) {
        IrValue.Temp t = temp(local.type());
        emit(new IrInstruction.Load(t, local));
        return t;
    }

    public void store(IrValue value, IrValue.Local local) {
        emit(new IrInstruction.Store(value, local));
    }

    public IrValue binary(IrInstruction.BinOp op, IrValue left, IrValue right) {
        IrValue.Temp t = temp(left.type());
        emit(new IrInstruction.Binary(t, op, left, right));
        if ((op == IrInstruction.BinOp.DIV || op == IrInstruction.BinOp.MOD)
                && left.type() != IrType.DOUBLE) {
            maybeCheckPending();
        }
        return t;
    }

    public IrValue compare(IrInstruction.CmpOp op, IrValue left, IrValue right) {
        IrValue.Temp t = temp(IrType.I1);
        emit(new IrInstruction.Compare(t, op, left, right));
        return t;
    }

    public IrValue not(IrValue operand) {
        IrValue.Temp t = temp(IrType.I1);
        emit(new IrInstruction.UnaryNot(t, operand));
        return t;
    }

    public IrValue cast(IrValue value, IrType target) {
        if (value.type() == target) {
            return value;
        }
        IrValue.Temp t = temp(target);
        emit(new IrInstruction.Cast(t, value, target));
        return t;
    }

    public IrValue call(String name, IrType returnType, java.util.List<IrValue> args) {
        return call(name, returnType, args, true);
    }

    public IrValue call(String name, IrType returnType, java.util.List<IrValue> args, boolean checkException) {
        if (returnType == IrType.VOID) {
            emit(new IrInstruction.Call(null, name, returnType, args));
            if (checkException) {
                maybeCheckPending();
            }
            return null;
        }
        IrValue.Temp t = temp(returnType);
        emit(new IrInstruction.Call(t, name, returnType, args));
        if (checkException) {
            maybeCheckPending();
        }
        if (returnType.isManagedPointer()) {
            return protectRef(t);
        }
        return t;
    }

    public void println(IrValue value) {
        emit(new IrInstruction.Println(value));
    }

    public void branch(IrBasicBlock target) {
        emit(new IrInstruction.Branch(target.name()));
    }

    public void condBranch(IrValue cond, IrBasicBlock trueBlock, IrBasicBlock falseBlock) {
        emit(new IrInstruction.CondBranch(cond, trueBlock.name(), falseBlock.name()));
    }

    public void ret(IrValue value) {
        emit(new IrInstruction.Return(value));
    }

    public void retVoid() {
        emit(new IrInstruction.Return());
    }

    public IrValue.GlobalString stringConstant(String content) {
        return module.addString(content);
    }

    public IrValue newObject(String className, String llvmTypeName, long sizeBytes, String metadataGlobal) {
        IrValue.Temp t = temp(IrType.PTR);
        emit(new IrInstruction.NewObject(t, className, llvmTypeName, sizeBytes, metadataGlobal));
        return protectRef(t);
    }

    public void constructorCall(String mangledName, java.util.List<IrValue> args) {
        emit(new IrInstruction.ConstructorCall(mangledName, args));
        maybeCheckPending();
    }

    public IrValue loadField(IrValue receiver, String className, String llvmTypeName, int fieldIndex, IrType fieldType) {
        return loadField(receiver, className, llvmTypeName, fieldIndex, fieldType, false);
    }

    public IrValue loadField(
            IrValue receiver, String className, String llvmTypeName, int fieldIndex, IrType fieldType,
            boolean isVolatile
    ) {
        emit(new IrInstruction.NullCheck(receiver));
        maybeCheckPending();
        IrValue.Temp t = temp(fieldType);
        emit(new IrInstruction.LoadField(
                t, receiver, className, llvmTypeName, fieldIndex, fieldType, isVolatile));
        if (fieldType.isManagedPointer()) {
            return protectRef(t);
        }
        return t;
    }

    public void storeField(IrValue receiver, String className, String llvmTypeName, int fieldIndex, IrValue value) {
        storeField(receiver, className, llvmTypeName, fieldIndex, value, false);
    }

    public void storeField(
            IrValue receiver, String className, String llvmTypeName, int fieldIndex, IrValue value,
            boolean isVolatile
    ) {
        emit(new IrInstruction.NullCheck(receiver));
        maybeCheckPending();
        emit(new IrInstruction.StoreField(
                receiver, className, llvmTypeName, fieldIndex, value, isVolatile));
    }

    public IrValue loadStatic(String globalName, IrType fieldType, boolean isVolatile) {
        IrValue.Temp t = temp(fieldType);
        emit(new IrInstruction.LoadStatic(t, globalName, fieldType, isVolatile));
        if (fieldType.isManagedPointer()) {
            return protectRef(t);
        }
        return t;
    }

    public void storeStatic(String globalName, IrValue value, boolean isVolatile) {
        emit(new IrInstruction.StoreStatic(globalName, value, isVolatile));
    }

    public void safepointPoll() {
        emit(new IrInstruction.SafepointPoll());
    }

    public void nullCheck(IrValue pointer) {
        emit(new IrInstruction.NullCheck(pointer));
        maybeCheckPending();
    }

    public IrValue virtualCall(IrValue receiver, int slot, IrType returnType, java.util.List<IrValue> args, String debugName) {
        if (returnType == IrType.VOID) {
            emit(new IrInstruction.VirtualCall(null, receiver, slot, returnType, args, debugName));
            maybeCheckPending();
            return null;
        }
        IrValue.Temp t = temp(returnType);
        emit(new IrInstruction.VirtualCall(t, receiver, slot, returnType, args, debugName));
        maybeCheckPending();
        if (returnType.isManagedPointer()) {
            return protectRef(t);
        }
        return t;
    }

    public IrValue newArray(
            IrType elementIrType,
            IrValue length,
            long elementSize,
            String metadataGlobal,
            boolean referenceElements
    ) {
        IrValue.Temp t = temp(IrType.PTR);
        emit(new IrInstruction.NewArray(t, elementIrType, length, elementSize, metadataGlobal, referenceElements));
        maybeCheckPending();
        return protectRef(t);
    }

    public IrValue loadElement(IrValue array, IrValue index, IrType elementType) {
        IrValue.Temp t = temp(elementType);
        emit(new IrInstruction.LoadElement(t, array, index, elementType));
        maybeCheckPending();
        if (elementType.isManagedPointer()) {
            return protectRef(t);
        }
        return t;
    }

    public void storeElement(IrValue array, IrValue index, IrValue value) {
        emit(new IrInstruction.StoreElement(array, index, value));
        maybeCheckPending();
    }

    public IrValue arrayLength(IrValue array) {
        IrValue.Temp t = temp(IrType.I32);
        emit(new IrInstruction.ArrayLength(t, array));
        return t;
    }

    public IrValue instanceOf(IrValue object, String targetMetaGlobal) {
        IrValue.Temp t = temp(IrType.I1);
        emit(new IrInstruction.InstanceOf(t, object, targetMetaGlobal));
        return t;
    }

    public IrValue checkedCast(IrValue object, String targetMetaGlobal) {
        IrValue.Temp t = temp(IrType.PTR);
        emit(new IrInstruction.CheckedCast(t, object, targetMetaGlobal));
        maybeCheckPending();
        return protectRef(t);
    }

    public IrValue stringLiteral(IrValue.GlobalString cString, int byteLength) {
        IrValue.Temp t = temp(IrType.PTR);
        emit(new IrInstruction.StringLiteral(t, cString, byteLength));
        return protectRef(t);
    }

    /**
     * Store a reference into a dedicated alloca so the shadow-stack / PTR-alloca
     * root scan keeps it live across GC safepoints.
     */
    public IrValue protectRef(IrValue ref) {
        if (ref.type().isManagedPointer()) {
            IrValue.Local slot = alloca("gctmp." + tempId, IrType.PTR);
            store(ref, slot);
            return load(slot);
        }
        return ref;
    }

    public IrValue nativeCall(
            String symbol,
            IrType returnType,
            java.util.List<IrValue> args,
            java.util.List<IrType> argTypes,
            String library
    ) {
        // Native calls are not JNative exception sources and not GC safepoints (0.7).
        if (returnType == IrType.VOID) {
            emit(new IrInstruction.NativeCall(null, symbol, returnType, args, argTypes, library, false));
            return null;
        }
        IrValue.Temp t = temp(returnType);
        emit(new IrInstruction.NativeCall(t, symbol, returnType, args, argTypes, library, false));
        return t;
    }

    public void gcCollect() {
        emit(new IrInstruction.GcCollect());
        maybeCheckPending();
    }

    public void pushExceptionHandler(IrBasicBlock dispatchBlock) {
        exceptionDispatchStack.push(dispatchBlock);
    }

    public void popExceptionHandler() {
        if (!exceptionDispatchStack.isEmpty()) {
            exceptionDispatchStack.pop();
        }
    }

    public void throwException(IrValue exception) {
        emit(new IrInstruction.Throw(exception));
        maybeCheckPending();
    }

    public void clearException() {
        emit(new IrInstruction.ClearException());
    }

    public void checkPendingTo(IrBasicBlock dispatchBlock) {
        IrBasicBlock cont = newBlock("ex.cont");
        IrValue pending = call("jnative_exception_pending", IrType.I1, java.util.List.of(), false);
        condBranch(pending, dispatchBlock, cont);
        setBlock(cont);
    }

    /**
     * After a potentially throwing op: branch to the innermost catch/finally dispatch,
     * or early-return from this function so the caller can observe the pending exception.
     */
    private void maybeCheckPending() {
        maybeCheckPendingPublic();
    }

    /** Visible for try-after blocks that must still propagate pending exceptions. */
    public void maybeCheckPendingPublic() {
        if (!exceptionDispatchStack.isEmpty()) {
            checkPendingTo(exceptionDispatchStack.peek());
            return;
        }
        // No local handler — propagate by returning immediately.
        IrBasicBlock unwind = newBlock("ex.unwind");
        IrBasicBlock cont = newBlock("ex.cont");
        IrValue pending = call("jnative_exception_pending", IrType.I1, java.util.List.of(), false);
        condBranch(pending, unwind, cont);
        setBlock(unwind);
        emitUnwindReturn();
        setBlock(cont);
    }

    private void emitUnwindReturn() {
        IrType ret = function.returnType();
        if (ret == IrType.VOID) {
            retVoid();
        } else {
            ret(defaultValue(ret));
        }
    }

    private IrValue defaultValue(IrType type) {
        return switch (type) {
            case I1 -> new IrValue.Const(IrType.I1, false);
            case I8 -> new IrValue.Const(IrType.I8, 0);
            case I16 -> new IrValue.Const(IrType.I16, 0);
            case I32 -> new IrValue.Const(IrType.I32, 0);
            case I64 -> new IrValue.Const(IrType.I64, 0L);
            case FLOAT -> new IrValue.Const(IrType.FLOAT, 0.0f);
            case DOUBLE -> new IrValue.Const(IrType.DOUBLE, 0.0);
            case PTR -> new IrValue.Const(IrType.PTR, "null");
            case NATIVE_PTR -> new IrValue.Const(IrType.NATIVE_PTR, "null");
            case VOID -> null;
        };
    }

    public IrValue.Local stackAlloc(
            String identifier,
            String structName,
            String llvmTypeName,
            int alignment,
            long sizeBytes
    ) {
        IrValue.Local local = new IrValue.Local(identifier, IrType.NATIVE_PTR);
        locals.put(identifier, local);
        entryBlock.instructions().add(entryAllocaCount++,
                new IrInstruction.StackAlloc(local, structName, llvmTypeName, alignment, sizeBytes));
        return local;
    }

    public IrValue addressOf(IrValue.Local stackLocal) {
        IrValue.Temp t = temp(IrType.NATIVE_PTR);
        emit(new IrInstruction.AddressOf(t, stackLocal));
        return t;
    }

    public IrValue nativeFieldAddress(
            IrValue base,
            String structName,
            String llvmTypeName,
            String fieldName,
            long byteOffset
    ) {
        IrValue.Temp t = temp(IrType.NATIVE_PTR);
        emit(new IrInstruction.NativeFieldAddress(
                t, base, structName, llvmTypeName, fieldName, byteOffset));
        return t;
    }

    public IrValue nativeLoad(IrValue address, IrType valueType) {
        IrValue.Temp t = temp(valueType);
        emit(new IrInstruction.NativeLoad(t, address, valueType));
        return t;
    }

    public void nativeStore(IrValue address, IrValue value) {
        emit(new IrInstruction.NativeStore(address, value));
    }

    public IrValue nativePtrAdd(IrValue base, IrValue elements, long elementSize) {
        IrValue.Temp t = temp(IrType.NATIVE_PTR);
        emit(new IrInstruction.NativePtrAdd(t, base, elements, elementSize));
        return t;
    }

    public void setReturnCompletion(IrValue value) {
        if (value.type() == IrType.PTR) {
            call("jnative_completion_set_return_ptr", IrType.VOID, java.util.List.of(value), false);
        } else if (value.type() == IrType.I64) {
            call("jnative_completion_set_return_i64", IrType.VOID, java.util.List.of(value), false);
        } else if (value.type() == IrType.I32) {
            IrValue widened = cast(value, IrType.I64);
            call("jnative_completion_set_return_i64", IrType.VOID, java.util.List.of(widened), false);
        } else if (value.type() == IrType.DOUBLE) {
            IrValue bits = cast(value, IrType.I64);
            call("jnative_completion_set_return_i64", IrType.VOID, java.util.List.of(bits), false);
        } else {
            call("jnative_completion_set_return_i64", IrType.VOID,
                    java.util.List.of(cast(value, IrType.I64)), false);
        }
    }

    public IrValue takeReturnCompletion(IrType type) {
        if (type == IrType.PTR) {
            return call("jnative_completion_take_return_ptr", IrType.PTR, java.util.List.of(), false);
        }
        IrValue raw = call("jnative_completion_take_return_i64", IrType.I64, java.util.List.of(), false);
        if (type == IrType.I64) {
            return raw;
        }
        if (type == IrType.I32) {
            return cast(raw, IrType.I32);
        }
        if (type == IrType.DOUBLE) {
            return cast(raw, IrType.DOUBLE);
        }
        return cast(raw, type);
    }

    public IrValue hasReturnCompletion() {
        return call("jnative_completion_has_return", IrType.I1, java.util.List.of(), false);
    }

    public void clearReturnCompletion() {
        call("jnative_completion_clear", IrType.VOID, java.util.List.of(), false);
    }

    public IrValue makeCallable(
            String samClass,
            String metadataGlobal,
            long sizeBytes,
            String codeSymbol,
            IrValue envOrNull,
            boolean hasEnv
    ) {
        IrValue.Temp t = temp(IrType.PTR);
        emit(new IrInstruction.MakeCallable(
                t, samClass, metadataGlobal, sizeBytes, codeSymbol, envOrNull, hasEnv));
        return protectRef(t);
    }

    public IrValue invokeCallable(IrValue callable, IrType returnType, java.util.List<IrValue> args) {
        if (returnType == IrType.VOID) {
            emit(new IrInstruction.InvokeCallable(null, callable, returnType, args));
            maybeCheckPending();
            return null;
        }
        IrValue.Temp t = temp(returnType);
        emit(new IrInstruction.InvokeCallable(t, callable, returnType, args));
        maybeCheckPending();
        if (returnType.isManagedPointer()) {
            return protectRef(t);
        }
        return t;
    }

    public IrValue createNativeCallback(
            String trampolineSymbol,
            IrValue callable,
            String invokeRuntime,
            int callbackId,
            String metadataGlobal,
            long handleSizeBytes
    ) {
        IrValue.Temp t = temp(IrType.PTR);
        emit(new IrInstruction.CreateNativeCallback(
                t, trampolineSymbol, callable, invokeRuntime, callbackId, metadataGlobal, handleSizeBytes));
        return protectRef(t);
    }

    public void releaseNativeCallback(
            IrValue handle,
            String llvmTypeName,
            int slotFieldIndex,
            int handleFieldIndex,
            int retainedFieldIndex
    ) {
        emit(new IrInstruction.ReleaseNativeCallback(
                handle, llvmTypeName, slotFieldIndex, handleFieldIndex, retainedFieldIndex));
    }
}
