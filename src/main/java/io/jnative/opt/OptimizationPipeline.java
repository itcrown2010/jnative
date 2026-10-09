package io.jnative.opt;

import io.jnative.compiler.CompilerOptions;
import io.jnative.ir.IrModule;
import io.jnative.semantic.TypedCompilationUnit;

/** Selects and runs optimization passes for a given -O level.
 * @author 张晓达
 */
public final class OptimizationPipeline {

    private OptimizationPipeline() {
    }

    public static OptimizationPipeline forLevel(int level) {
        return new OptimizationPipeline();
    }

    public OptimizationReport run(IrModule module, TypedCompilationUnit typed, CompilerOptions options) {
        int level = options.optLevel();
        OptimizationReport report = new OptimizationReport();
        report.setExplainEnabled(options.explainOpt());

        if (level <= 0) {
            return report;
        }

        OptimizationContext ctx = new OptimizationContext(
                module, typed, level, report, options.emitEscapeAnalysis());

        PassManager pm = new PassManager();
        pm.register(new EscapeAnalysisPass());

        if (level >= 1) {
            pm.register(new ConstantPropagationPass());
            pm.register(new ScalarReplacementPass());
            pm.register(new DevirtualizePass());
            pm.register(new ConstantPropagationPass());
            pm.register(new NullCheckEliminationPass());
            pm.register(new DeadCodeEliminationPass());
        }
        if (level >= 2) {
            pm.register(new InlinePass());
            pm.register(new EscapeAnalysisPass());
            pm.register(new ConstantPropagationPass());
            pm.register(new ScalarReplacementPass());
            pm.register(new DeadCodeEliminationPass());
            pm.register(new BoundsCheckEliminationPass());
            pm.register(new SafepointEliminationPass());
            pm.register(new StackAllocationPass());
            pm.register(new WholeProgramDcePass());
        }

        pm.run(ctx);

        if (options.verifyIr()) {
            IrVerifier.verify(module);
        }

        return report;
    }
}
