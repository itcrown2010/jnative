package io.jnative.semantic;

import io.jnative.ast.*;
import io.jnative.diagnostics.Diagnostic;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.diagnostics.SourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * JNative 0.11 — effect inference, propagation, and contract verification.
 */
public final class EffectAnalyzer {

    private static final FunctionEffects IO_EFFECTS = new FunctionEffects(
            FunctionEffects.BLOCKS | FunctionEffects.NATIVE_CALL | FunctionEffects.THROWS);
    private static final FunctionEffects INSTANCE_NULL_CHECK = FunctionEffects.throwsEffect();

    private final DiagnosticReporter reporter;
    private TypedCompilationUnit typed;
    private CompilationUnit unit;
    private final Map<MethodSymbol, MethodDeclaration> methodAst = new HashMap<>();
    private final Map<MethodSymbol, FunctionEffects> localEffects = new HashMap<>();
    /** Local reason string or callee {@link MethodSymbol} for effect-path diagnostics. */
    private final Map<MethodSymbol, List<Object>> contributions = new HashMap<>();

    public EffectAnalyzer(DiagnosticReporter reporter) {
        this.reporter = reporter;
    }

    public void analyze(TypedCompilationUnit typed, CompilationUnit unit) {
        this.typed = typed;
        this.unit = unit;
        buildMethodAstMap();
        seedBuiltins();
        inferLocalBodies();
        propagateFixedPoint();
        inferLambdaBindings();
        verifyContracts();
        verifyAsyncDiagnostics();
        verifyOverrideContracts();
    }

    public String emitEffectsReport(TypedCompilationUnit typed) {
        StringBuilder sb = new StringBuilder();
        for (ClassDeclaration cls : typed.unit().classes()) {
            if (cls.nativeStruct()) {
                continue;
            }
            ClassSymbol owner = typed.classes().get(cls.qualifiedName());
            if (owner == null) {
                owner = typed.classes().get(cls.name());
            }
            if (owner == null) {
                continue;
            }
            java.util.Map<String, Integer> overloadOrdinal = new java.util.HashMap<>();
            for (MethodDeclaration md : cls.methods()) {
                if (md.isConstructor()) {
                    continue;
                }
                MethodSymbol ms = resolveDeclaredOverload(owner, md, overloadOrdinal);
                if (ms == null || ms.isNative()) {
                    continue;
                }
                sb.append(owner.name()).append('.').append(ms.name()).append(": ");
                sb.append(String.join("|", ms.effectiveEffects().flagNames()));
                sb.append(" DETERMINISTIC ")
                        .append(ms.effectiveEffects().isDeterministic() ? "YES" : "NO")
                        .append('\n');
            }
        }
        return sb.toString();
    }

    private void buildMethodAstMap() {
        methodAst.clear();
        for (ClassDeclaration cls : unit.classes()) {
            if (cls.nativeStruct()) {
                continue;
            }
            ClassSymbol owner = typed.classes().get(cls.qualifiedName());
            if (owner == null) {
                owner = typed.classes().get(cls.name());
            }
            if (owner == null) {
                continue;
            }
            java.util.Map<String, Integer> overloadOrdinal = new java.util.HashMap<>();
            for (MethodDeclaration md : cls.methods()) {
                if (md.isConstructor()) {
                    continue;
                }
                MethodSymbol ms = resolveDeclaredOverload(owner, md, overloadOrdinal);
                if (ms != null) {
                    methodAst.put(ms, md);
                }
            }
        }
    }

    /** Pair AST method with MethodSymbol by declaration order within the same name. */
    private static MethodSymbol resolveDeclaredOverload(
            ClassSymbol owner,
            MethodDeclaration md,
            java.util.Map<String, Integer> overloadOrdinal
    ) {
        int idx = overloadOrdinal.merge(md.name(), 1, Integer::sum) - 1;
        java.util.List<MethodSymbol> overs = owner.findDeclaredMethods(md.name());
        if (idx < 0 || idx >= overs.size()) {
            return null;
        }
        return overs.get(idx);
    }

    private void seedBuiltins() {
        for (ClassSymbol cls : typed.classes().values()) {
            if (!cls.isBuiltin()) {
                continue;
            }
            for (MethodSymbol ms : cls.methods()) {
                if (ms.isNative() && ms.nativeEffectsContract() != null) {
                    continue;
                }
                FunctionEffects seeded = seedBuiltinMethod(cls, ms);
                if (seeded != null) {
                    ms.setInferredEffects(seeded.copy());
                    ms.setEffectiveEffects(seeded.copy());
                }
            }
        }
    }

    private FunctionEffects seedBuiltinMethod(ClassSymbol owner, MethodSymbol ms) {
        String cls = owner.name();
        String name = ms.name();

        if ("Thread".equals(cls)) {
            return switch (name) {
                case "sleep", "join", "start" -> new FunctionEffects(
                        FunctionEffects.BLOCKS | FunctionEffects.SAFEPOINT);
                default -> ms.isFfiIntrinsic() ? FunctionEffects.none() : null;
            };
        }
        if ("Mutex".equals(cls)) {
            return switch (name) {
                case "lock" -> FunctionEffects.blocks();
                case "tryLock", "unlock" -> FunctionEffects.none();
                default -> null;
            };
        }
        if ("Condition".equals(cls)) {
            return switch (name) {
                case "await" -> FunctionEffects.blocks();
                case "signal", "signalAll" -> FunctionEffects.none();
                default -> null;
            };
        }
        if ("Future".equals(cls)) {
            return switch (name) {
                case "awaitCooperative" -> FunctionEffects.suspends();
                case "completed" -> FunctionEffects.allocates();
                case "isComplete", "getCompleted" -> FunctionEffects.none();
                default -> null;
            };
        }
        if (cls.startsWith("Atomic")) {
            return FunctionEffects.none();
        }
        if ("File".equals(cls) || "TcpSocket".equals(cls) || "TcpServer".equals(cls)
                || cls.startsWith("Http")) {
            return IO_EFFECTS.copy();
        }
        if ("String".equals(cls)) {
            if (ms.isStatic()) {
                return switch (name) {
                    case "fromBytes", "valueOf" -> FunctionEffects.allocates();
                    default -> FunctionEffects.none();
                };
            }
            if (isAllocatingStringMethod(name)) {
                return FunctionEffects.allocates().union(INSTANCE_NULL_CHECK);
            }
            return INSTANCE_NULL_CHECK.copy();
        }
        if ("Bytes".equals(cls)) {
            return switch (name) {
                case "fromString", "toString", "concat", "slice" -> FunctionEffects.allocates();
                default -> FunctionEffects.none();
            };
        }
        if (ms.isFfiIntrinsic() && ms.samInvokeRuntime() != null) {
            return FunctionEffects.unknown();
        }
        if (ms.isFfiIntrinsic()) {
            return IO_EFFECTS.copy();
        }
        return null;
    }

    private static boolean isAllocatingStringMethod(String name) {
        return switch (name) {
            case "substring", "substringFrom", "trim", "toLowerCase", "getBytes" -> true;
            default -> false;
        };
    }

    private void inferLocalBodies() {
        localEffects.clear();
        contributions.clear();
        for (var entry : methodAst.entrySet()) {
            MethodSymbol ms = entry.getKey();
            if (ms.isNative()) {
                continue;
            }
            List<Object> contribs = new ArrayList<>();
            FunctionEffects body = inferMethodBody(entry.getValue(), ms, contribs);
            localEffects.put(ms, body);
            contributions.put(ms, contribs);
            ms.setInferredEffects(body.copy());
            ms.setEffectiveEffects(body.copy());
        }
    }

    private FunctionEffects inferMethodBody(MethodDeclaration md, MethodSymbol ms, List<Object> contribs) {
        if (md.body() == null) {
            return FunctionEffects.none();
        }
        if (ms.isAsync()) {
            contribs.add("async method");
        }
        return inferStatement(md.body(), ms, contribs);
    }

    private FunctionEffects inferStatement(Statement stmt, MethodSymbol ms, List<Object> contribs) {
        return switch (stmt) {
            case BlockStatement b -> {
                FunctionEffects e = FunctionEffects.none();
                for (Statement s : b.statements()) {
                    e.union(inferStatement(s, ms, contribs));
                }
                yield e;
            }
            case VariableDeclaration v -> v.initializer() != null
                    ? inferExpression(v.initializer(), ms, contribs)
                    : FunctionEffects.none();
            case ExpressionStatement e -> inferExpression(e.expression(), ms, contribs);
            case IfStatement i -> {
                FunctionEffects e = inferExpression(i.condition(), ms, contribs);
                e.union(inferStatement(i.thenBranch(), ms, contribs));
                if (i.elseBranch() != null) {
                    e.union(inferStatement(i.elseBranch(), ms, contribs));
                }
                yield e;
            }
            case WhileStatement w -> {
                FunctionEffects e = inferExpression(w.condition(), ms, contribs);
                e.union(inferStatement(w.body(), ms, contribs));
                yield e;
            }
            case DoWhileStatement d -> {
                FunctionEffects e = inferStatement(d.body(), ms, contribs);
                e.union(inferExpression(d.condition(), ms, contribs));
                yield e;
            }
            case BreakStatement ignored -> FunctionEffects.none();
            case ContinueStatement ignored -> FunctionEffects.none();
            case ForStatement f -> {
                FunctionEffects e = FunctionEffects.none();
                if (f.initializer() != null) {
                    e.union(inferStatement(f.initializer(), ms, contribs));
                }
                if (f.condition() != null) {
                    e.union(inferExpression(f.condition(), ms, contribs));
                }
                e.union(inferStatement(f.body(), ms, contribs));
                if (f.update() != null) {
                    e.union(inferExpression(f.update(), ms, contribs));
                }
                yield e;
            }
            case ReturnStatement r -> r.value() != null
                    ? inferExpression(r.value(), ms, contribs)
                    : FunctionEffects.none();
            case ThrowStatement t -> {
                contribs.add("throw");
                FunctionEffects e = FunctionEffects.throwsEffect();
                e.union(inferExpression(t.expression(), ms, contribs));
                yield e;
            }
            case TryStatement t -> {
                FunctionEffects e = FunctionEffects.none();
                for (VariableDeclaration r : t.resources()) {
                    if (r.initializer() != null) {
                        e.union(inferExpression(r.initializer(), ms, contribs));
                    }
                }
                e.union(inferStatement(t.tryBlock(), ms, contribs));
                for (CatchClause c : t.catches()) {
                    e.union(inferStatement(c.body(), ms, contribs));
                }
                if (t.finallyBlock() != null) {
                    e.union(inferStatement(t.finallyBlock(), ms, contribs));
                }
                yield e;
            }
            case ConstructorDelegationStatement ignored -> FunctionEffects.none();
            case EmptyStatement ignored -> FunctionEffects.none();
        };
    }

    private FunctionEffects inferExpression(Expression expr, MethodSymbol ms, List<Object> contribs) {
        FunctionEffects e = FunctionEffects.none();
        switch (expr) {
            case NewObjectExpression n -> {
                e.with(FunctionEffects.ALLOCATES | FunctionEffects.SAFEPOINT);
                contribs.add("new " + n.className());
                for (Expression arg : n.arguments()) {
                    e.union(inferExpression(arg, ms, contribs));
                }
            }
            case NewArrayExpression n -> {
                e.with(FunctionEffects.ALLOCATES | FunctionEffects.SAFEPOINT);
                contribs.add("new array");
                e.union(inferExpression(n.length(), ms, contribs));
            }
            case PrintlnExpression p -> {
                e.with(FunctionEffects.BLOCKS | FunctionEffects.NATIVE_CALL);
                contribs.add("println");
                e.union(inferExpression(p.argument(), ms, contribs));
            }
            case GcCollectExpression ignored -> {
                e.with(FunctionEffects.SAFEPOINT);
                contribs.add("System.gc()");
            }
            case AwaitExpression a -> {
                e.with(FunctionEffects.SUSPENDS | FunctionEffects.SAFEPOINT);
                contribs.add("await");
                e.union(inferExpression(a.operand(), ms, contribs));
            }
            case LambdaExpression l -> inferCallableCreation(l, e, contribs, ms);
            case MethodReferenceExpression r -> inferCallableCreation(r, e, contribs, ms);
            case MethodCallExpression m -> {
                e.union(inferCall(m, ms, contribs));
                if (m.receiver() != null) {
                    e.union(inferExpression(m.receiver(), ms, contribs));
                }
                for (Expression arg : m.arguments()) {
                    e.union(inferExpression(arg, ms, contribs));
                }
            }
            case AssignmentExpression a -> {
                e.union(inferAssignment(a, ms, contribs));
                e.union(inferExpression(a.value(), ms, contribs));
            }
            case BinaryExpression b -> {
                e.union(inferExpression(b.left(), ms, contribs));
                e.union(inferExpression(b.right(), ms, contribs));
                if (b.operator() == BinaryOp.DIV || b.operator() == BinaryOp.MOD) {
                    e.with(FunctionEffects.THROWS);
                    contribs.add(b.operator().name().toLowerCase());
                }
                if (b.operator() == BinaryOp.ADD && isStringType(b.left()) && isStringType(b.right())) {
                    e.with(FunctionEffects.ALLOCATES | FunctionEffects.SAFEPOINT);
                    contribs.add("string concat");
                }
            }
            case UnaryExpression u -> e.union(inferExpression(u.operand(), ms, contribs));
            case FieldAccessExpression f -> {
                e.union(instanceNullCheck(f.receiver(), contribs));
                e.union(inferExpression(f.receiver(), ms, contribs));
            }
            case ArrayAccessExpression a -> {
                e.with(FunctionEffects.THROWS);
                contribs.add("array access");
                e.union(inferExpression(a.array(), ms, contribs));
                e.union(inferExpression(a.index(), ms, contribs));
            }
            case CastExpression c -> {
                e.with(FunctionEffects.THROWS);
                contribs.add("cast");
                e.union(inferExpression(c.expression(), ms, contribs));
            }
            case InstanceOfExpression i -> {
                e.with(FunctionEffects.THROWS);
                contribs.add("instanceof");
                e.union(inferExpression(i.expression(), ms, contribs));
            }
            case VariableExpression v -> {
                if (isInstanceContext(v)) {
                    e.with(FunctionEffects.THROWS);
                }
            }
            default -> {
                if (expr instanceof GroupingExpression g) {
                    e.union(inferExpression(g.expression(), ms, contribs));
                } else if (expr instanceof ThisExpression) {
                    e.with(FunctionEffects.THROWS);
                }
            }
        }
        return e;
    }

    private void inferCallableCreation(
            Expression expr,
            FunctionEffects e,
            List<Object> contribs,
            MethodSymbol ms
    ) {
        CallableBinding binding = typed.callableBindings().get(expr);
        e.with(FunctionEffects.ALLOCATES | FunctionEffects.SAFEPOINT);
        contribs.add("callable");
        if (binding != null && binding.hasEnv()) {
            contribs.add("capturing lambda");
        }
    }

    private FunctionEffects inferAssignment(AssignmentExpression a, MethodSymbol ms, List<Object> contribs) {
        FunctionEffects e = FunctionEffects.none();
        if (a.target() instanceof FieldAccessExpression fa) {
            Object sym = typed.resolvedSymbols().get(fa);
            if (sym instanceof FieldSymbol fs && fs.isStatic()) {
                e.with(FunctionEffects.WRITES_GLOBAL);
                contribs.add("write " + fs.owner().name() + "." + fs.name());
            }
            e.union(instanceNullCheck(fa.receiver(), contribs));
            e.union(inferExpression(fa.receiver(), ms, contribs));
        }
        return e;
    }

    private FunctionEffects inferCall(MethodCallExpression m, MethodSymbol caller, List<Object> contribs) {
        Object resolved = typed.resolvedSymbols().get(m);
        if (resolved instanceof NativeCallbackSite) {
            contribs.add("Native.callback");
            return new FunctionEffects(FunctionEffects.NATIVE_CALL | FunctionEffects.UNKNOWN);
        }
        if (!(resolved instanceof MethodSymbol callee)) {
            return FunctionEffects.none();
        }
        if (m.receiver() != null && !callee.isStatic()) {
            // receiver effects handled by caller inferExpression
        }
        boolean virtual = m.receiver() != null && callee.isVirtual();
        FunctionEffects callEffects = resolveCallEffects(callee, virtual);
        contribs.add(callee);
        return callEffects.copy();
    }

    private FunctionEffects instanceNullCheck(Expression receiver, List<Object> contribs) {
        if (receiver == null || receiver instanceof ThisExpression) {
            return FunctionEffects.none();
        }
        if (receiver instanceof VariableExpression ve && "this".equals(ve.name())) {
            return FunctionEffects.none();
        }
        if (isTypeNameReceiver(receiver)) {
            return FunctionEffects.none();
        }
        return INSTANCE_NULL_CHECK.copy();
    }

    private boolean isTypeNameReceiver(Expression receiver) {
        if (receiver instanceof VariableExpression ve) {
            Object sym = typed.resolvedSymbols().get(ve);
            return sym instanceof ClassSymbol;
        }
        return false;
    }

    private boolean isInstanceContext(VariableExpression v) {
        if ("this".equals(v.name())) {
            return false;
        }
        Object sym = typed.resolvedSymbols().get(v);
        return sym instanceof VariableSymbol vs
                && vs.kind() == VariableSymbol.Kind.PARAMETER
                && !vs.type().isPrimitive();
    }

    private boolean isStringType(Expression expr) {
        JType t = typed.expressionTypes().get(expr);
        return t instanceof ClassType ct && "String".equals(ct.name());
    }

    private FunctionEffects resolveCallEffects(MethodSymbol callee, boolean virtualDispatch) {
        if (!virtualDispatch || !callee.isVirtual()) {
            return callee.effectiveEffects().copy();
        }
        FunctionEffects union = callee.effectiveEffects().copy();
        for (ClassSymbol cls : typed.classes().values()) {
            Optional<MethodSymbol> override = cls.findMethod(callee.name());
            if (override.isPresent() && overridesChainIncludes(override.get(), callee)) {
                union.union(override.get().effectiveEffects());
            }
        }
        return union;
    }

    private static boolean overridesChainIncludes(MethodSymbol candidate, MethodSymbol target) {
        MethodSymbol cur = candidate;
        while (cur != null) {
            if (cur == target) {
                return true;
            }
            cur = cur.overrides();
        }
        return false;
    }

    private void propagateFixedPoint() {
        List<MethodSymbol> worklist = new ArrayList<>(localEffects.keySet());
        int maxIter = worklist.size() * 2 + 10;
        int iter = 0;
        boolean changed = true;
        while (changed && iter++ < maxIter) {
            changed = false;
            for (MethodSymbol ms : worklist) {
                FunctionEffects body = localEffects.getOrDefault(ms, FunctionEffects.none()).copy();
                FunctionEffects prev = ms.effectiveEffects().copy();
                for (Object c : contributions.getOrDefault(ms, List.of())) {
                    if (c instanceof MethodSymbol callee) {
                        body.union(callee.effectiveEffects());
                    }
                }
                if (body.bits() != prev.bits()) {
                    ms.setEffectiveEffects(body);
                    ms.setInferredEffects(body.copy());
                    changed = true;
                }
            }
        }
    }

    private void inferLambdaBindings() {
        for (CallableBinding binding : typed.callableBindings().values()) {
            if (binding.targetMethod() != null) {
                binding.setEffects(binding.targetMethod().effectiveEffects().copy());
                continue;
            }
            List<Object> contribs = new ArrayList<>();
            FunctionEffects local = FunctionEffects.none();
            if (binding.bodyBlock() != null) {
                local = inferStatement(binding.bodyBlock(), null, contribs);
            } else if (binding.bodyExpression() != null) {
                local = inferExpression(binding.bodyExpression(), null, contribs);
            }
            for (Object c : contribs) {
                if (c instanceof MethodSymbol callee) {
                    local.union(callee.effectiveEffects());
                }
            }
            binding.setEffects(local);
        }
    }

    private void verifyAsyncDiagnostics() {
        for (ClassDeclaration cls : unit.classes()) {
            if (cls.nativeStruct()) {
                continue;
            }
            ClassSymbol owner = typed.classes().get(cls.qualifiedName());
            if (owner == null) {
                owner = typed.classes().get(cls.name());
            }
            if (owner == null) {
                continue;
            }
            java.util.Map<String, Integer> overloadOrdinal = new java.util.HashMap<>();
            for (MethodDeclaration md : cls.methods()) {
                if (md.isConstructor()) {
                    continue;
                }
                MethodSymbol ms = resolveDeclaredOverload(owner, md, overloadOrdinal);
                if (ms == null || !ms.isAsync()) {
                    continue;
                }
                SourceLocation loc = md.location();
                if (ms.contractNoGc() && containsAwait(md.body())) {
                    reporter.error(loc,
                            "@NoGC async method '" + ms.name()
                                    + "' cannot use await (await requires safepoint/GC cooperation)");
                }
                if (ms.contractNoBlock() && ms.effectiveEffects().mayBlock()) {
                    reporter.report(new Diagnostic(
                            Diagnostic.Severity.WARNING,
                            loc,
                            "async method '" + ms.name()
                                    + "' is @NoBlock but body may BLOCK; consider removing @NoBlock"));
                }
                if (ms.effectiveEffects().mayBlock() && ms.effectiveEffects().maySuspend()) {
                    reporter.report(new Diagnostic(
                            Diagnostic.Severity.WARNING,
                            loc,
                            "async method '" + ms.name()
                                    + "' both SUSPENDS and BLOCKS; blocking calls inside async may stall the event loop"));
                }
            }
        }
    }

    private static boolean containsAwait(Statement stmt) {
        if (stmt == null) {
            return false;
        }
        return switch (stmt) {
            case BlockStatement b -> {
                for (Statement s : b.statements()) {
                    if (containsAwait(s)) {
                        yield true;
                    }
                }
                yield false;
            }
            case ExpressionStatement e -> containsAwaitExpr(e.expression());
            case VariableDeclaration v ->
                    v.initializer() != null && containsAwaitExpr(v.initializer());
            case IfStatement i -> containsAwait(i.thenBranch())
                    || (i.elseBranch() != null && containsAwait(i.elseBranch()));
            case WhileStatement w -> containsAwait(w.body());
            case DoWhileStatement d -> containsAwait(d.body()) || containsAwaitExpr(d.condition());
            case ForStatement f -> (f.initializer() != null && containsAwait(f.initializer()))
                    || (f.condition() != null && containsAwaitExpr(f.condition()))
                    || containsAwait(f.body())
                    || (f.update() != null && containsAwaitExpr(f.update()));
            case BreakStatement ignored -> false;
            case ContinueStatement ignored -> false;
            case ReturnStatement r -> r.value() != null && containsAwaitExpr(r.value());
            case ThrowStatement t -> containsAwaitExpr(t.expression());
            case TryStatement t -> t.resources().stream()
                    .anyMatch(r -> r.initializer() != null && containsAwaitExpr(r.initializer()))
                    || containsAwait(t.tryBlock())
                    || t.catches().stream().anyMatch(c -> containsAwait(c.body()))
                    || (t.finallyBlock() != null && containsAwait(t.finallyBlock()));
            default -> false;
        };
    }

    private static boolean containsAwaitExpr(Expression expr) {
        if (expr == null) {
            return false;
        }
        return switch (expr) {
            case AwaitExpression ignored -> true;
            case BinaryExpression b ->
                    containsAwaitExpr(b.left()) || containsAwaitExpr(b.right());
            case UnaryExpression u -> containsAwaitExpr(u.operand());
            case AssignmentExpression a ->
                    containsAwaitExpr(a.target()) || containsAwaitExpr(a.value());
            case MethodCallExpression m -> {
                boolean r = m.receiver() != null && containsAwaitExpr(m.receiver());
                for (Expression arg : m.arguments()) {
                    r = r || containsAwaitExpr(arg);
                }
                yield r;
            }
            case PrintlnExpression p -> containsAwaitExpr(p.argument());
            case GroupingExpression g -> containsAwaitExpr(g.expression());
            case NewObjectExpression n -> {
                boolean r = false;
                for (Expression arg : n.arguments()) {
                    r = r || containsAwaitExpr(arg);
                }
                yield r;
            }
            case FieldAccessExpression f -> containsAwaitExpr(f.receiver());
            case NewArrayExpression n -> containsAwaitExpr(n.length());
            case ArrayAccessExpression a ->
                    containsAwaitExpr(a.array()) || containsAwaitExpr(a.index());
            case InstanceOfExpression i -> containsAwaitExpr(i.expression());
            case CastExpression c -> containsAwaitExpr(c.expression());
            case LambdaExpression l -> l.expressionBody() != null && containsAwaitExpr(l.expressionBody());
            default -> false;
        };
    }

    private void verifyContracts() {
        for (ClassDeclaration cls : unit.classes()) {
            if (cls.nativeStruct()) {
                continue;
            }
            ClassSymbol owner = typed.classes().get(cls.qualifiedName());
            if (owner == null) {
                owner = typed.classes().get(cls.name());
            }
            if (owner == null) {
                continue;
            }
            java.util.Map<String, Integer> overloadOrdinal = new java.util.HashMap<>();
            for (MethodDeclaration md : cls.methods()) {
                if (md.isConstructor()) {
                    continue;
                }
                MethodSymbol ms = resolveDeclaredOverload(owner, md, overloadOrdinal);
                if (ms == null) {
                    continue;
                }
                SourceLocation loc = md.effectContract() != null
                        ? md.effectContract().location()
                        : ms.location();
                if (ms.contractNoGc() && ms.effectiveEffects().violatesNoGc()) {
                    reporter.error(loc, formatContractError("@NoGC", ms, "may allocate",
                            FunctionEffects::violatesNoGc));
                }
                if (ms.contractNoThrow() && ms.effectiveEffects().violatesNoThrow()) {
                    reporter.error(loc, formatContractError("@NoThrow", ms, "may throw",
                            FunctionEffects::violatesNoThrow));
                }
                if (ms.contractNoBlock() && ms.effectiveEffects().violatesNoBlock()) {
                    reporter.error(loc, formatContractError("@NoBlock", ms, "may block",
                            FunctionEffects::violatesNoBlock));
                }
                if (md.effectContract() != null && md.effectContract().deterministic()
                        && !ms.effectiveEffects().isDeterministic()) {
                    reporter.error(loc, formatContractError("@Deterministic", ms,
                            "is not deterministic", e -> !e.isDeterministic()));
                }
            }
        }
    }

    private void verifyOverrideContracts() {
        for (ClassSymbol cls : typed.classes().values()) {
            if (cls.isBuiltin()) {
                continue;
            }
            for (MethodSymbol child : cls.methods()) {
                MethodSymbol parent = child.overrides();
                if (parent == null) {
                    continue;
                }
                verifyImplementationContracts(child, parent, "override");
            }
            for (io.jnative.semantic.InterfaceSymbol iface : cls.transitiveInterfaces()) {
                for (io.jnative.semantic.InterfaceMethodSlot slot : iface.slots()) {
                    io.jnative.vtable.ITableBuilder.resolveImplementation(cls, slot.method())
                            .ifPresent(impl -> verifyImplementationContracts(
                                    impl, slot.method(), "implements interface method"));
                }
            }
        }
    }

    private void verifyImplementationContracts(MethodSymbol impl, MethodSymbol required, String context) {
        if (required.contractNoGc() && impl.effectiveEffects().violatesNoGc()) {
            reporter.error(impl.location(),
                    context + " effect contract violation: '" + impl.name()
                            + "' may allocate but required method is @NoGC");
        }
        if (required.contractNoThrow() && impl.effectiveEffects().violatesNoThrow()) {
            reporter.error(impl.location(),
                    context + " effect contract violation: '" + impl.name()
                            + "' may throw but required method is @NoThrow");
        }
        if (required.contractNoBlock() && impl.effectiveEffects().violatesNoBlock()) {
            reporter.error(impl.location(),
                    context + " effect contract violation: '" + impl.name()
                            + "' may block but required method is @NoBlock");
        }
    }

    private String formatContractError(
            String annotation,
            MethodSymbol ms,
            String verb,
            Predicate<FunctionEffects> violates
    ) {
        return annotation + " function '" + ms.name() + "' " + verb + "\n\n" + buildEffectPath(ms, violates);
    }

    private String buildEffectPath(MethodSymbol root, Predicate<FunctionEffects> violates) {
        StringBuilder sb = new StringBuilder();
        sb.append("effect path:\n");
        sb.append("  ").append(formatMethod(root)).append('\n');
        appendPathFrom(root, violates, sb, new HashSet<>());
        return sb.toString();
    }

    private boolean appendPathFrom(
            MethodSymbol ms,
            Predicate<FunctionEffects> violates,
            StringBuilder sb,
            Set<MethodSymbol> seen
    ) {
        if (!seen.add(ms)) {
            return false;
        }
        for (Object c : contributions.getOrDefault(ms, List.of())) {
            if (c instanceof String local) {
                FunctionEffects localEff = localEffectFor(local);
                if (violates.test(localEff)) {
                    sb.append("  → ").append(local).append('\n');
                    return true;
                }
            }
        }
        for (Object c : contributions.getOrDefault(ms, List.of())) {
            if (c instanceof MethodSymbol callee && violates.test(callee.effectiveEffects())) {
                sb.append("  → ").append(formatMethod(callee)).append('\n');
                if (appendPathFrom(callee, violates, sb, seen)) {
                    return true;
                }
                return true;
            }
        }
        return false;
    }

    private static FunctionEffects localEffectFor(String local) {
        if (local.startsWith("new ") || local.equals("callable") || local.equals("capturing lambda")
                || local.equals("string concat")) {
            return FunctionEffects.allocates();
        }
        if (local.equals("throw") || local.equals("array access") || local.equals("cast")
                || local.equals("instanceof") || local.equals("div") || local.equals("mod")) {
            return FunctionEffects.throwsEffect();
        }
        if (local.equals("println") || local.equals("Native.callback")) {
            return new FunctionEffects(FunctionEffects.BLOCKS | FunctionEffects.NATIVE_CALL);
        }
        if (local.equals("System.gc()")) {
            return new FunctionEffects(FunctionEffects.SAFEPOINT);
        }
        if (local.startsWith("Thread.") || local.contains("sleep")) {
            return FunctionEffects.blocks();
        }
        return FunctionEffects.none();
    }

    private static String formatMethod(MethodSymbol ms) {
        return ms.owner().name() + "." + ms.name() + "()";
    }
}
