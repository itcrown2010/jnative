package io.jnative.opt;

import io.jnative.ir.IrBasicBlock;
import io.jnative.ir.IrFunction;
import io.jnative.ir.IrInstruction;
import io.jnative.ir.IrModule;
import io.jnative.ir.IrType;
import io.jnative.ir.IrValue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/** Shared IR helpers for optimization passes. */
public final class IrUtil {

    public record ValueUse(IrValue value, String operand) {
    }

    private IrUtil() {
    }

    public static int maxTempId(IrModule module) {
        int max = 0;
        for (IrFunction f : module.functions()) {
            max = Math.max(max, maxTempId(f));
        }
        return max;
    }

    public static int maxTempId(IrFunction function) {
        int max = 0;
        for (IrBasicBlock b : function.blocks()) {
            for (IrInstruction inst : b.instructions()) {
                IrValue.Temp def = definingTemp(inst);
                if (def != null) {
                    max = Math.max(max, def.id());
                }
                for (ValueUse u : usedValues(inst)) {
                    if (u.value() instanceof IrValue.Temp t) {
                        max = Math.max(max, t.id());
                    }
                }
            }
        }
        return max;
    }

    public static IrValue.Temp definingTemp(IrInstruction inst) {
        return switch (inst) {
            case IrInstruction.Alloca ignored -> null;
            case IrInstruction.Load l -> l.dest();
            case IrInstruction.Binary b -> b.dest();
            case IrInstruction.Compare c -> c.dest();
            case IrInstruction.UnaryNot u -> u.dest();
            case IrInstruction.Cast c -> c.dest();
            case IrInstruction.Call c -> c.dest();
            case IrInstruction.NewObject n -> n.dest();
            case IrInstruction.LoadField l -> l.dest();
            case IrInstruction.VirtualCall v -> v.dest();
            case IrInstruction.NewArray n -> n.dest();
            case IrInstruction.LoadElement l -> l.dest();
            case IrInstruction.ArrayLength a -> a.dest();
            case IrInstruction.InstanceOf i -> i.dest();
            case IrInstruction.CheckedCast c -> c.dest();
            case IrInstruction.StringLiteral s -> s.dest();
            case IrInstruction.NativeCall n -> n.dest();
            case IrInstruction.AddressOf a -> a.dest();
            case IrInstruction.NativeFieldAddress f -> f.dest();
            case IrInstruction.NativeLoad l -> l.dest();
            case IrInstruction.NativePtrAdd a -> a.dest();
            case IrInstruction.LoadStatic l -> l.dest();
            case IrInstruction.AtomicLoad a -> a.dest();
            case IrInstruction.AtomicRmw a -> a.dest();
            case IrInstruction.CompareExchange c -> c.destSuccess();
            case IrInstruction.MakeCallable m -> m.dest();
            case IrInstruction.InvokeCallable i -> i.dest();
            case IrInstruction.CreateNativeCallback c -> c.destHandle();
            default -> null;
        };
    }

    public static List<ValueUse> usedValues(IrInstruction inst) {
        List<ValueUse> out = new ArrayList<>();
        switch (inst) {
            case IrInstruction.Load l -> out.add(new ValueUse(l.source(), "source"));
            case IrInstruction.Store s -> out.add(new ValueUse(s.value(), "value"));
            case IrInstruction.Binary b -> {
                out.add(new ValueUse(b.left(), "left"));
                out.add(new ValueUse(b.right(), "right"));
            }
            case IrInstruction.Compare c -> {
                out.add(new ValueUse(c.left(), "left"));
                out.add(new ValueUse(c.right(), "right"));
            }
            case IrInstruction.UnaryNot u -> out.add(new ValueUse(u.operand(), "operand"));
            case IrInstruction.Cast c -> out.add(new ValueUse(c.source(), "source"));
            case IrInstruction.Call c -> c.args().forEach(a -> out.add(new ValueUse(a, "arg")));
            case IrInstruction.Println p -> out.add(new ValueUse(p.value(), "value"));
            case IrInstruction.CondBranch c -> out.add(new ValueUse(c.condition(), "condition"));
            case IrInstruction.Return r -> {
                if (r.value() != null) {
                    out.add(new ValueUse(r.value(), "value"));
                }
            }
            case IrInstruction.ConstructorCall c -> c.args().forEach(a -> out.add(new ValueUse(a, "arg")));
            case IrInstruction.LoadField l -> out.add(new ValueUse(l.receiver(), "receiver"));
            case IrInstruction.StoreField s -> {
                out.add(new ValueUse(s.receiver(), "receiver"));
                out.add(new ValueUse(s.value(), "value"));
            }
            case IrInstruction.NullCheck n -> out.add(new ValueUse(n.pointer(), "pointer"));
            case IrInstruction.VirtualCall v -> {
                out.add(new ValueUse(v.receiver(), "receiver"));
                v.args().forEach(a -> out.add(new ValueUse(a, "arg")));
            }
            case IrInstruction.NewArray n -> out.add(new ValueUse(n.length(), "length"));
            case IrInstruction.LoadElement l -> {
                out.add(new ValueUse(l.array(), "array"));
                out.add(new ValueUse(l.index(), "index"));
            }
            case IrInstruction.StoreElement s -> {
                out.add(new ValueUse(s.array(), "array"));
                out.add(new ValueUse(s.index(), "index"));
                out.add(new ValueUse(s.value(), "value"));
            }
            case IrInstruction.ArrayLength a -> out.add(new ValueUse(a.array(), "array"));
            case IrInstruction.InstanceOf i -> out.add(new ValueUse(i.object(), "object"));
            case IrInstruction.CheckedCast c -> out.add(new ValueUse(c.object(), "object"));
            case IrInstruction.Throw t -> out.add(new ValueUse(t.exception(), "exception"));
            case IrInstruction.NativeCall n -> n.args().forEach(a -> out.add(new ValueUse(a, "arg")));
            case IrInstruction.AddressOf a -> out.add(new ValueUse(a.stackLocal(), "stackLocal"));
            case IrInstruction.NativeFieldAddress f -> out.add(new ValueUse(f.base(), "base"));
            case IrInstruction.NativeLoad l -> out.add(new ValueUse(l.address(), "address"));
            case IrInstruction.NativeStore s -> {
                out.add(new ValueUse(s.address(), "address"));
                out.add(new ValueUse(s.value(), "value"));
            }
            case IrInstruction.NativePtrAdd a -> {
                out.add(new ValueUse(a.base(), "base"));
                out.add(new ValueUse(a.elements(), "elements"));
            }
            case IrInstruction.StoreStatic s -> out.add(new ValueUse(s.value(), "value"));
            case IrInstruction.AtomicLoad a -> out.add(new ValueUse(a.address(), "address"));
            case IrInstruction.AtomicStore a -> {
                out.add(new ValueUse(a.address(), "address"));
                out.add(new ValueUse(a.value(), "value"));
            }
            case IrInstruction.AtomicRmw a -> {
                out.add(new ValueUse(a.address(), "address"));
                out.add(new ValueUse(a.value(), "value"));
            }
            case IrInstruction.CompareExchange c -> {
                out.add(new ValueUse(c.address(), "address"));
                out.add(new ValueUse(c.expected(), "expected"));
                out.add(new ValueUse(c.update(), "update"));
            }
            case IrInstruction.MakeCallable m -> {
                if (m.hasEnv()) {
                    out.add(new ValueUse(m.envOrNull(), "env"));
                }
            }
            case IrInstruction.InvokeCallable i -> {
                out.add(new ValueUse(i.callable(), "callable"));
                i.args().forEach(a -> out.add(new ValueUse(a, "arg")));
            }
            case IrInstruction.CreateNativeCallback c -> out.add(new ValueUse(c.callable(), "callable"));
            case IrInstruction.ReleaseNativeCallback r -> out.add(new ValueUse(r.handle(), "handle"));
            default -> { }
        }
        return out;
    }

    public static IrInstruction mapValues(IrInstruction inst, Function<IrValue, IrValue> map) {
        return switch (inst) {
            case IrInstruction.Alloca a -> new IrInstruction.Alloca(mapLocal(a.dest(), map));
            case IrInstruction.Load l ->
                    new IrInstruction.Load(mapTemp(l.dest(), map), mapLocal(l.source(), map));
            case IrInstruction.Store s ->
                    new IrInstruction.Store(map.apply(s.value()), mapLocal(s.dest(), map));
            case IrInstruction.Binary b ->
                    new IrInstruction.Binary(mapTemp(b.dest(), map), b.op(), map.apply(b.left()), map.apply(b.right()));
            case IrInstruction.Compare c ->
                    new IrInstruction.Compare(mapTemp(c.dest(), map), c.op(), map.apply(c.left()), map.apply(c.right()));
            case IrInstruction.UnaryNot u ->
                    new IrInstruction.UnaryNot(mapTemp(u.dest(), map), map.apply(u.operand()));
            case IrInstruction.Cast c ->
                    new IrInstruction.Cast(mapTemp(c.dest(), map), map.apply(c.source()), c.target());
            case IrInstruction.Call c -> {
                List<IrValue> args = c.args().stream().map(map).toList();
                IrValue.Temp dest = c.dest() == null ? null : mapTemp(c.dest(), map);
                yield new IrInstruction.Call(dest, c.function(), c.returnType(), args);
            }
            case IrInstruction.Println p -> new IrInstruction.Println(map.apply(p.value()));
            case IrInstruction.CondBranch cb -> new IrInstruction.CondBranch(
                    map.apply(cb.condition()), cb.trueTarget(), cb.falseTarget());
            case IrInstruction.Return r -> new IrInstruction.Return(r.value() == null ? null : map.apply(r.value()));
            case IrInstruction.NewObject n -> new IrInstruction.NewObject(
                    mapTemp(n.dest(), map), n.className(), n.llvmTypeName(), n.sizeBytes(), n.metadataGlobal(),
                    n.stackAllocated());
            case IrInstruction.ConstructorCall cc -> new IrInstruction.ConstructorCall(
                    cc.mangledName(), cc.args().stream().map(map).toList());
            case IrInstruction.LoadField lf -> new IrInstruction.LoadField(
                    mapTemp(lf.dest(), map), map.apply(lf.receiver()),
                    lf.className(), lf.llvmTypeName(), lf.fieldIndex(), lf.fieldType(), lf.isVolatile());
            case IrInstruction.StoreField sf -> new IrInstruction.StoreField(
                    map.apply(sf.receiver()), sf.className(), sf.llvmTypeName(),
                    sf.fieldIndex(), map.apply(sf.value()), sf.isVolatile());
            case IrInstruction.NullCheck nc -> new IrInstruction.NullCheck(map.apply(nc.pointer()));
            case IrInstruction.VirtualCall vc -> new IrInstruction.VirtualCall(
                    vc.dest() == null ? null : mapTemp(vc.dest(), map),
                    map.apply(vc.receiver()), vc.slot(), vc.returnType(),
                    vc.args().stream().map(map).toList(), vc.debugName());
            case IrInstruction.NewArray na -> new IrInstruction.NewArray(
                    mapTemp(na.dest(), map), na.elementIrType(), map.apply(na.length()), na.elementSize(),
                    na.metadataGlobal(), na.referenceElements());
            case IrInstruction.LoadElement le -> new IrInstruction.LoadElement(
                    mapTemp(le.dest(), map), map.apply(le.array()), map.apply(le.index()),
                    le.elementType(), le.unchecked());
            case IrInstruction.StoreElement se -> new IrInstruction.StoreElement(
                    map.apply(se.array()), map.apply(se.index()), map.apply(se.value()));
            case IrInstruction.ArrayLength al ->
                    new IrInstruction.ArrayLength(mapTemp(al.dest(), map), map.apply(al.array()));
            case IrInstruction.InstanceOf io -> new IrInstruction.InstanceOf(
                    mapTemp(io.dest(), map), map.apply(io.object()), io.targetMetaGlobal());
            case IrInstruction.CheckedCast cc -> new IrInstruction.CheckedCast(
                    mapTemp(cc.dest(), map), map.apply(cc.object()), cc.targetMetaGlobal());
            case IrInstruction.StringLiteral sl -> new IrInstruction.StringLiteral(
                    mapTemp(sl.dest(), map), sl.cString(), sl.byteLength());
            case IrInstruction.Throw t -> new IrInstruction.Throw(map.apply(t.exception()));
            case IrInstruction.NativeCall nc -> new IrInstruction.NativeCall(
                    nc.dest() == null ? null : mapTemp(nc.dest(), map),
                    nc.symbol(), nc.returnType(), nc.args().stream().map(map).toList(),
                    nc.argTypes(), nc.library(), nc.stringBridgeTemps());
            case IrInstruction.MakeCallable mc -> new IrInstruction.MakeCallable(
                    mapTemp(mc.dest(), map), mc.samClass(), mc.metadataGlobal(), mc.sizeBytes(),
                    mc.codeSymbol(), map.apply(mc.envOrNull()), mc.hasEnv());
            case IrInstruction.InvokeCallable ic -> new IrInstruction.InvokeCallable(
                    ic.dest() == null ? null : mapTemp(ic.dest(), map),
                    map.apply(ic.callable()), ic.returnType(), ic.args().stream().map(map).toList());
            case IrInstruction.CreateNativeCallback cnc -> new IrInstruction.CreateNativeCallback(
                    mapTemp(cnc.destHandle(), map), cnc.trampolineSymbol(), map.apply(cnc.callable()),
                    cnc.invokeRuntime(), cnc.callbackId(), cnc.metadataGlobal(), cnc.handleSizeBytes());
            case IrInstruction.ReleaseNativeCallback rnc -> new IrInstruction.ReleaseNativeCallback(
                    map.apply(rnc.handle()), rnc.llvmTypeName(), rnc.slotFieldIndex(),
                    rnc.handleFieldIndex(), rnc.retainedFieldIndex());
            default -> inst;
        };
    }

    private static IrValue.Temp mapTemp(IrValue.Temp dest, Function<IrValue, IrValue> map) {
        IrValue mapped = map.apply(dest);
        if (mapped instanceof IrValue.Temp t) {
            return t;
        }
        return dest;
    }

    public static boolean hasSideEffects(IrInstruction inst, OptimizationContext ctx) {
        if (inst == null) {
            return false;
        }
        if (inst instanceof IrInstruction.Alloca
                || inst instanceof IrInstruction.StackAlloc
                || inst instanceof IrInstruction.Store
                || inst instanceof IrInstruction.StoreField
                || inst instanceof IrInstruction.StoreElement
                || inst instanceof IrInstruction.StoreStatic
                || inst instanceof IrInstruction.NativeStore
                || inst instanceof IrInstruction.AtomicStore
                || inst instanceof IrInstruction.AtomicRmw
                || inst instanceof IrInstruction.CompareExchange
                || inst instanceof IrInstruction.Println
                || inst instanceof IrInstruction.Throw
                || inst instanceof IrInstruction.GcCollect
                || inst instanceof IrInstruction.SafepointPoll
                || inst instanceof IrInstruction.ClearException
                || inst instanceof IrInstruction.MakeCallable
                || inst instanceof IrInstruction.CreateNativeCallback
                || inst instanceof IrInstruction.ReleaseNativeCallback
                || inst instanceof IrInstruction.NullCheck
                || inst instanceof IrInstruction.ConstructorCall
                || inst instanceof IrInstruction.NewObject
                || inst instanceof IrInstruction.NewArray
                || inst instanceof IrInstruction.InvokeCallable) {
            return true;
        }
        if (inst instanceof IrInstruction.Call c) {
            return ctx == null || !ctx.isPureCall(c.function());
        }
        if (inst instanceof IrInstruction.VirtualCall) {
            return true;
        }
        if (inst instanceof IrInstruction.NativeCall n) {
            return ctx == null || !ctx.isPureNative(n.symbol());
        }
        return false;
    }

    private static IrValue.Local mapLocal(IrValue value, Function<IrValue, IrValue> map) {
        IrValue mapped = map.apply(value);
        if (mapped instanceof IrValue.Local local) {
            return local;
        }
        return (IrValue.Local) value;
    }

    public static IrValue.Const foldBinary(IrInstruction.BinOp op, IrValue.Const left, IrValue.Const right) {
        if (!left.type().isIntegerLike() || !right.type().isIntegerLike()) {
            return null;
        }
        long l = ((Number) left.value()).longValue();
        long r = ((Number) right.value()).longValue();
        Long result = switch (op) {
            case ADD -> l + r;
            case SUB -> l - r;
            case MUL -> l * r;
            case DIV -> r == 0 ? null : l / r;
            case MOD -> r == 0 ? null : l % r;
            case AND -> l & r;
            case OR -> l | r;
        };
        if (result == null) {
            return null;
        }
        IrType type = left.type();
        Object boxed = switch (type) {
            case I1 -> result != 0;
            case I8 -> (byte) (long) result;
            case I16 -> (short) (long) result;
            case I32 -> (int) (long) result;
            case I64 -> result;
            default -> null;
        };
        if (boxed == null) {
            return null;
        }
        return new IrValue.Const(type, boxed);
    }

    public static IrValue.Const foldCompare(IrInstruction.CmpOp op, IrValue.Const left, IrValue.Const right) {
        if (!left.type().isIntegerLike() || !right.type().isIntegerLike()) {
            return null;
        }
        long l = ((Number) left.value()).longValue();
        long r = ((Number) right.value()).longValue();
        boolean result = switch (op) {
            case EQ -> l == r;
            case NE -> l != r;
            case LT -> l < r;
            case LE -> l <= r;
            case GT -> l > r;
            case GE -> l >= r;
        };
        return new IrValue.Const(IrType.I1, result);
    }

    public static Set<String> blockNames(IrFunction function) {
        Set<String> names = new HashSet<>();
        for (IrBasicBlock b : function.blocks()) {
            names.add(b.name());
        }
        return names;
    }

    public static String traceToNewObjectClass(IrFunction function, IrValue.Temp temp) {
        for (IrBasicBlock block : function.blocks()) {
            for (IrInstruction inst : block.instructions()) {
                if (inst instanceof IrInstruction.NewObject n) {
                    Set<IrValue.Temp> aliases = aliasTempsOfNewObject(function, n.dest());
                    if (aliases.contains(temp)) {
                        return n.className();
                    }
                }
            }
        }
        return null;
    }

    public static Set<IrValue.Temp> aliasTempsOfNewObject(IrFunction function, IrValue.Temp root) {
        Set<IrValue.Temp> aliases = new HashSet<>();
        Set<IrValue.Local> aliasLocals = new HashSet<>();
        aliases.add(root);
        boolean changed = true;
        while (changed) {
            changed = false;
            for (IrBasicBlock block : function.blocks()) {
                for (IrInstruction inst : block.instructions()) {
                    if (inst instanceof IrInstruction.Store s && s.dest() instanceof IrValue.Local local) {
                        if (s.value() instanceof IrValue.Temp t && aliases.contains(t) && aliasLocals.add(local)) {
                            changed = true;
                        }
                    }
                    if (inst instanceof IrInstruction.Load l && l.source() instanceof IrValue.Local local) {
                        if (aliasLocals.contains(local) && aliases.add(l.dest())) {
                            changed = true;
                        }
                    }
                }
            }
        }
        return aliases;
    }

    public static int instructionCount(IrFunction function) {
        int count = 0;
        for (IrBasicBlock b : function.blocks()) {
            count += b.instructions().size();
        }
        return count;
    }
}
