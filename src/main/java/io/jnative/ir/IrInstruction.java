package io.jnative.ir;

import java.util.List;

public sealed interface IrInstruction
        permits IrInstruction.Alloca, IrInstruction.Load, IrInstruction.Store,
        IrInstruction.Binary, IrInstruction.Compare, IrInstruction.UnaryNot,
        IrInstruction.Cast, IrInstruction.Call, IrInstruction.Println,
        IrInstruction.Branch, IrInstruction.CondBranch, IrInstruction.Return,
        IrInstruction.JumpLabel, IrInstruction.NewObject, IrInstruction.LoadField,
        IrInstruction.StoreField, IrInstruction.NullCheck, IrInstruction.VirtualCall,
        IrInstruction.ConstructorCall, IrInstruction.NewArray, IrInstruction.LoadElement,
        IrInstruction.StoreElement, IrInstruction.ArrayLength, IrInstruction.InstanceOf,
        IrInstruction.CheckedCast, IrInstruction.StringLiteral,
        IrInstruction.GcCollect, IrInstruction.Throw, IrInstruction.ClearException,
        IrInstruction.NativeCall, IrInstruction.StackAlloc, IrInstruction.AddressOf,
        IrInstruction.NativeFieldAddress,         IrInstruction.NativeLoad, IrInstruction.NativeStore,
        IrInstruction.NativePtrAdd,
        IrInstruction.LoadStatic, IrInstruction.StoreStatic,
        IrInstruction.SafepointPoll,
        IrInstruction.AtomicLoad, IrInstruction.AtomicStore,
        IrInstruction.AtomicRmw, IrInstruction.CompareExchange {

    record Alloca(IrValue.Local dest) implements IrInstruction {
    }

    record Load(IrValue.Temp dest, IrValue.Local source) implements IrInstruction {
    }

    record Store(IrValue value, IrValue.Local dest) implements IrInstruction {
    }

    enum BinOp { ADD, SUB, MUL, DIV, MOD, AND, OR }

    record Binary(IrValue.Temp dest, BinOp op, IrValue left, IrValue right) implements IrInstruction {
    }

    enum CmpOp { EQ, NE, LT, LE, GT, GE }

    record Compare(IrValue.Temp dest, CmpOp op, IrValue left, IrValue right) implements IrInstruction {
    }

    record UnaryNot(IrValue.Temp dest, IrValue operand) implements IrInstruction {
    }

    record Cast(IrValue.Temp dest, IrValue source, IrType target) implements IrInstruction {
    }

    record Call(IrValue.Temp dest, String function, IrType returnType, List<IrValue> args) implements IrInstruction {
    }

    record Println(IrValue value) implements IrInstruction {
    }

    record Branch(String target) implements IrInstruction {
    }

    record CondBranch(IrValue condition, String trueTarget, String falseTarget) implements IrInstruction {
    }

    record Return(IrValue value) implements IrInstruction {
        public Return() {
            this(null);
        }
    }

    record JumpLabel(String name) implements IrInstruction {
    }

    /** Allocate object, zero-init, install ClassMetadata* in header. */
    record NewObject(
            IrValue.Temp dest,
            String className,
            String llvmTypeName,
            long sizeBytes,
            String metadataGlobal
    ) implements IrInstruction {
    }

    record ConstructorCall(String mangledName, List<IrValue> args) implements IrInstruction {
    }

    record LoadField(
            IrValue.Temp dest,
            IrValue receiver,
            String className,
            String llvmTypeName,
            int fieldIndex,
            IrType fieldType,
            boolean isVolatile
    ) implements IrInstruction {
        public LoadField(
                IrValue.Temp dest,
                IrValue receiver,
                String className,
                String llvmTypeName,
                int fieldIndex,
                IrType fieldType
        ) {
            this(dest, receiver, className, llvmTypeName, fieldIndex, fieldType, false);
        }
    }

    record StoreField(
            IrValue receiver,
            String className,
            String llvmTypeName,
            int fieldIndex,
            IrValue value,
            boolean isVolatile
    ) implements IrInstruction {
        public StoreField(
                IrValue receiver,
                String className,
                String llvmTypeName,
                int fieldIndex,
                IrValue value
        ) {
            this(receiver, className, llvmTypeName, fieldIndex, value, false);
        }
    }

    record LoadStatic(
            IrValue.Temp dest,
            String globalName,
            IrType fieldType,
            boolean isVolatile
    ) implements IrInstruction {
    }

    record StoreStatic(
            String globalName,
            IrValue value,
            boolean isVolatile
    ) implements IrInstruction {
    }

    record SafepointPoll() implements IrInstruction {
    }

    enum MemoryOrder { RELAXED, ACQUIRE, RELEASE, ACQ_REL, SEQ_CST }

    record AtomicLoad(IrValue.Temp dest, IrValue address, IrType valueType, MemoryOrder order)
            implements IrInstruction {
    }

    record AtomicStore(IrValue address, IrValue value, MemoryOrder order) implements IrInstruction {
    }

    enum AtomicRmwOp { ADD, SUB, XCHG }

    record AtomicRmw(
            IrValue.Temp dest,
            AtomicRmwOp op,
            IrValue address,
            IrValue value,
            MemoryOrder order
    ) implements IrInstruction {
    }

    record CompareExchange(
            IrValue.Temp destSuccess,
            IrValue address,
            IrValue expected,
            IrValue update,
            MemoryOrder success,
            MemoryOrder failure
    ) implements IrInstruction {
    }

    record NullCheck(IrValue pointer) implements IrInstruction {
    }

    record VirtualCall(
            IrValue.Temp dest,
            IrValue receiver,
            int slot,
            IrType returnType,
            List<IrValue> args,
            String debugName
    ) implements IrInstruction {
    }

    record NewArray(
            IrValue.Temp dest,
            IrType elementIrType,
            IrValue length,
            long elementSize,
            String metadataGlobal,
            boolean referenceElements
    ) implements IrInstruction {
    }

    record LoadElement(IrValue.Temp dest, IrValue array, IrValue index, IrType elementType) implements IrInstruction {
    }

    record StoreElement(IrValue array, IrValue index, IrValue value) implements IrInstruction {
    }

    record ArrayLength(IrValue.Temp dest, IrValue array) implements IrInstruction {
    }

    record InstanceOf(IrValue.Temp dest, IrValue object, String targetMetaGlobal) implements IrInstruction {
    }

    record CheckedCast(IrValue.Temp dest, IrValue object, String targetMetaGlobal) implements IrInstruction {
    }

    /** Materialize a String object from a UTF-8 C string constant. */
    record StringLiteral(IrValue.Temp dest, IrValue.GlobalString cString, int byteLength) implements IrInstruction {
    }

    /** Explicit GC collection (System.gc intrinsic). */
    record GcCollect() implements IrInstruction {
    }

    /** Throw a Throwable reference (sets runtime pending exception). */
    record Throw(IrValue exception) implements IrInstruction {
    }

    /** Clear pending exception after a catch handler runs. */
    record ClearException() implements IrInstruction {
    }

    /**
     * Explicit FFI call. Backend emits {@code declare} + {@code call} to the native symbol.
     * Arguments are already converted to ABI values (e.g. CString temps).
     */
    record NativeCall(
            IrValue.Temp dest,
            String symbol,
            IrType returnType,
            List<IrValue> args,
            List<IrType> argTypes,
            String library,
            boolean stringBridgeTemps
    ) implements IrInstruction {
    }

    /**
     * Stack-allocate a {@code @NativeStruct} (LLVM {@code alloca}). Not a GC allocation.
     * {@code dest} is a local holding the stack address ({@link IrType#NATIVE_PTR}).
     */
    record StackAlloc(
            IrValue.Local dest,
            String structName,
            String llvmTypeName,
            int alignment,
            long sizeBytes
    ) implements IrInstruction {
    }

    /** Take address of a stack-allocated native struct local. */
    record AddressOf(IrValue.Temp dest, IrValue.Local stackLocal) implements IrInstruction {
    }

    /** Typed GEP into a native struct (offset from {@link io.jnative.ffi.NativeStructLayout}). */
    record NativeFieldAddress(
            IrValue.Temp dest,
            IrValue base,
            String structName,
            String llvmTypeName,
            String fieldName,
            long byteOffset
    ) implements IrInstruction {
    }

    record NativeLoad(IrValue.Temp dest, IrValue address, IrType valueType) implements IrInstruction {
    }

    record NativeStore(IrValue address, IrValue value) implements IrInstruction {
    }

    /** Element-scaled pointer arithmetic: {@code address + elements * elementSize}. */
    record NativePtrAdd(
            IrValue.Temp dest,
            IrValue base,
            IrValue elements,
            long elementSize
    ) implements IrInstruction {
    }
}
