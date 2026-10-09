package io.jnative.ir;

/**
 * Textual dump of JNative IR for --emit-ir.
 * @author 张晓达
 */
public final class IrPrinter {

    private IrPrinter() {
    }

    public static String print(IrModule module) {
        StringBuilder sb = new StringBuilder();
        sb.append("; Module: ").append(module.name()).append('\n');
        for (var layout : module.classLayouts().values()) {
            sb.append("class ").append(layout.className())
                    .append(" size=").append(layout.instanceSize())
                    .append(" llvm=%").append(layout.llvmTypeName()).append('\n');
        }
        for (var meta : module.classMetadatas().values()) {
            sb.append("meta ").append(meta.globalName())
                    .append(" size=").append(meta.instanceSize())
                    .append(" flags=").append(meta.flags()).append('\n');
        }
        for (var meta : module.arrayMetadatas().values()) {
            sb.append("arraymeta ").append(meta.globalName())
                    .append(" refElems=").append(meta.hasReferenceElements()).append('\n');
        }
        for (IrValue.GlobalString s : module.strings()) {
            sb.append("string ").append(s.name()).append(" = \"").append(escape(s.content())).append("\"\n");
        }
        for (IrFunction f : module.functions()) {
            printFunction(f, sb);
            sb.append('\n');
        }
        return sb.toString();
    }

    private static void printFunction(IrFunction f, StringBuilder sb) {
        sb.append("func ").append(f.name()).append('(');
        for (int i = 0; i < f.params().size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            IrValue.Param p = f.params().get(i);
            sb.append(p.type()).append(' ').append(p.identifier());
        }
        sb.append(") -> ").append(f.returnType()).append('\n');
        for (IrBasicBlock block : f.blocks()) {
            sb.append(block.name()).append(":\n");
            for (IrInstruction inst : block.instructions()) {
                sb.append("  ").append(format(inst)).append('\n');
            }
        }
    }

    private static String format(IrInstruction inst) {
        return switch (inst) {
            case IrInstruction.Alloca a -> a.dest().name() + " = alloca " + a.dest().type();
            case IrInstruction.Load l -> l.dest().name() + " = load " + l.source().name();
            case IrInstruction.Store s -> "store " + s.value().name() + ", " + s.dest().name();
            case IrInstruction.Binary b ->
                    b.dest().name() + " = " + b.op().name().toLowerCase() + " " + b.left().name() + ", " + b.right().name();
            case IrInstruction.Compare c ->
                    c.dest().name() + " = cmp " + c.op().name().toLowerCase() + " " + c.left().name() + ", " + c.right().name();
            case IrInstruction.UnaryNot u -> u.dest().name() + " = not " + u.operand().name();
            case IrInstruction.Cast c ->
                    c.dest().name() + " = cast " + c.source().name() + " to " + c.target();
            case IrInstruction.Call c -> {
                StringBuilder sb = new StringBuilder();
                if (c.dest() != null) {
                    sb.append(c.dest().name()).append(" = ");
                }
                sb.append("call ").append(c.returnType()).append(' ').append(c.function()).append('(');
                for (int i = 0; i < c.args().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(c.args().get(i).name());
                }
                sb.append(')');
                yield sb.toString();
            }
            case IrInstruction.Println p -> "println " + p.value().name();
            case IrInstruction.Branch b -> "br " + b.target();
            case IrInstruction.CondBranch c ->
                    "br " + c.condition().name() + ", " + c.trueTarget() + ", " + c.falseTarget();
            case IrInstruction.Return r -> r.value() == null ? "ret" : "ret " + r.value().name();
            case IrInstruction.JumpLabel j -> j.name() + ":";
            case IrInstruction.NewObject n ->
                    n.dest().name() + " = new " + n.className()
                            + " size=" + n.sizeBytes() + " metadata=@" + n.metadataGlobal()
                            + (n.stackAllocated() ? " stack" : "");
            case IrInstruction.ConstructorCall c -> {
                StringBuilder sb = new StringBuilder("ctorcall ").append(c.mangledName()).append('(');
                for (int i = 0; i < c.args().size(); i++) {
                    if (i > 0) {
                        sb.append(", ");
                    }
                    sb.append(c.args().get(i).name());
                }
                sb.append(')');
                yield sb.toString();
            }
            case IrInstruction.LoadField l ->
                    l.dest().name() + " = loadfield " + l.className() + "." + l.fieldIndex()
                            + ", " + l.receiver().name();
            case IrInstruction.StoreField s ->
                    "storefield " + s.className() + "." + s.fieldIndex()
                            + ", " + s.receiver().name() + ", " + s.value().name();
            case IrInstruction.NullCheck n -> "nullcheck " + n.pointer().name();
            case IrInstruction.VirtualCall v -> {
                StringBuilder sb = new StringBuilder();
                if (v.dest() != null) {
                    sb.append(v.dest().name()).append(" = ");
                }
                sb.append("virtualcall slot=").append(v.slot())
                        .append(" ").append(v.debugName())
                        .append(" receiver=").append(v.receiver().name());
                yield sb.toString();
            }
            case IrInstruction.InterfaceCall ic -> {
                StringBuilder sb = new StringBuilder();
                if (ic.dest() != null) {
                    sb.append(ic.dest().name()).append(" = ");
                }
                sb.append("interfacecall iface=").append(ic.interfaceId())
                        .append(" slot=").append(ic.slot())
                        .append(" ").append(ic.debugName())
                        .append(" receiver=").append(ic.receiver().name());
                yield sb.toString();
            }
            case IrInstruction.NewArray n ->
                    n.dest().name() + " = newarray " + n.elementIrType()
                            + " len=" + n.length().name()
                            + " metadata=@" + n.metadataGlobal();
            case IrInstruction.LoadElement l ->
                    l.dest().name() + " = loadelem " + l.array().name() + "[" + l.index().name() + "]"
                            + (l.unchecked() ? " unchecked" : "");
            case IrInstruction.StoreElement s ->
                    "storeelem " + s.array().name() + "[" + s.index().name() + "] = " + s.value().name();
            case IrInstruction.ArrayLength a ->
                    a.dest().name() + " = arraylength " + a.array().name();
            case IrInstruction.InstanceOf i ->
                    i.dest().name() + " = instanceof " + i.object().name()
                            + ", @" + i.targetMetaGlobal();
            case IrInstruction.InstanceOfInterface i ->
                    i.dest().name() + " = instanceof_iface " + i.object().name()
                            + ", id=" + i.interfaceId();
            case IrInstruction.CheckedCast c ->
                    c.dest().name() + " = checkedcast " + c.object().name()
                            + ", @" + c.targetMetaGlobal();
            case IrInstruction.CheckedCastInterface c ->
                    c.dest().name() + " = checkedcast_iface " + c.object().name()
                            + ", id=" + c.interfaceId();
            case IrInstruction.StringLiteral s ->
                    s.dest().name() + " = stringlit " + s.cString().name()
                            + " len=" + s.byteLength();
            case IrInstruction.GcCollect ignored -> "gc.collect";
            case IrInstruction.Throw t -> "throw " + t.exception().name();
            case IrInstruction.ClearException ignored -> "exception.clear";
            case IrInstruction.NativeCall n -> {
                StringBuilder s = new StringBuilder("nativecall ").append(n.symbol()).append('(');
                for (int i = 0; i < n.args().size(); i++) {
                    if (i > 0) {
                        s.append(", ");
                    }
                    s.append(n.args().get(i).name());
                }
                s.append(')');
                if (n.dest() != null) {
                    yield n.dest().name() + " = " + s;
                }
                yield s.toString();
            }
            case IrInstruction.StackAlloc a ->
                    a.dest().name() + " = stackalloc %" + a.llvmTypeName()
                            + " align=" + a.alignment() + " size=" + a.sizeBytes();
            case IrInstruction.AddressOf a ->
                    a.dest().name() + " = addressof " + a.stackLocal().name();
            case IrInstruction.NativeFieldAddress f ->
                    f.dest().name() + " = nativefield " + f.structName() + "." + f.fieldName()
                            + " +" + f.byteOffset() + ", " + f.base().name();
            case IrInstruction.NativeLoad l ->
                    l.dest().name() + " = nativeload " + l.valueType() + ", " + l.address().name();
            case IrInstruction.NativeStore s ->
                    "nativestore " + s.value().name() + ", " + s.address().name();
            case IrInstruction.NativePtrAdd a ->
                    a.dest().name() + " = nativeptradd " + a.base().name()
                            + ", " + a.elements().name() + " *" + a.elementSize();
            case IrInstruction.LoadStatic l ->
                    l.dest().name() + " = loadstatic @" + l.globalName()
                            + (l.isVolatile() ? " volatile" : "");
            case IrInstruction.StoreStatic s ->
                    "storestatic @" + s.globalName() + ", " + s.value().name()
                            + (s.isVolatile() ? " volatile" : "");
            case IrInstruction.SafepointPoll ignored -> "safepoint.poll";
            case IrInstruction.AtomicLoad a ->
                    a.dest().name() + " = atomicload " + a.order() + " " + a.address().name();
            case IrInstruction.AtomicStore a ->
                    "atomicstore " + a.order() + " " + a.value().name() + ", " + a.address().name();
            case IrInstruction.AtomicRmw a ->
                    a.dest().name() + " = atomicrmw " + a.op() + " " + a.order()
                            + " " + a.address().name() + ", " + a.value().name();
            case IrInstruction.CompareExchange c ->
                    c.destSuccess().name() + " = cmpxchg " + c.address().name()
                            + ", " + c.expected().name() + ", " + c.update().name();
            case IrInstruction.MakeCallable m ->
                    m.dest().name() + " = make_callable " + m.samClass()
                            + " code=@" + m.codeSymbol() + " hasEnv=" + m.hasEnv();
            case IrInstruction.InvokeCallable i -> {
                StringBuilder sb = new StringBuilder();
                if (i.dest() != null) {
                    sb.append(i.dest().name()).append(" = ");
                }
                sb.append("invoke_callable ").append(i.callable().name())
                        .append(" -> ").append(i.returnType());
                yield sb.toString();
            }
            case IrInstruction.CreateNativeCallback c ->
                    c.destHandle().name() + " = native_callback "
                            + c.callable().name() + " @" + c.trampolineSymbol()
                            + " id=" + c.callbackId();
            case IrInstruction.ReleaseNativeCallback r ->
                    "release_native_callback " + r.handle().name();
            case IrInstruction.AsyncBegin b ->
                    b.frame().name() + " = async_begin " + b.methodId() + " -> " + b.resultType();
            case IrInstruction.Await a ->
                    (a.dest() != null ? a.dest().name() + " = " : "")
                            + "await " + a.future().name() + " state=" + a.resumeState()
                            + " resume=" + a.resumeLabel();
            case IrInstruction.AsyncSuspend s ->
                    "async_suspend frame=" + s.frame().name() + " state=" + s.resumeState()
                            + " future=" + s.future().name();
            case IrInstruction.AsyncResume r -> "async_resume state=" + r.resumeState();
            case IrInstruction.AsyncComplete c ->
                    "async_complete " + c.value().name() + " -> " + c.resultType();
            case IrInstruction.AsyncFail f -> "async_fail " + f.exception().name();
        };
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
