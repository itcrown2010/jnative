package io.jnative.processor;

import io.jnative.ast.CompilationUnit;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.diagnostics.SourceLocation;
import io.jnative.meta.ProgramModel;
import io.jnative.meta.ProgramModelBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs annotation processors with rounds, crash isolation, timeout, and generated-dir writes.
 */
public final class ProcessorScheduler {
    public static final int MAX_ROUNDS = 8;
    public static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final List<AnnotationProcessor> processors;
    private final Path generatedRoot;
    private final boolean deterministic;
    private final long timeoutMs;
    private final Map<String, String> options;
    private final List<String> explainLog = new ArrayList<>();

    public ProcessorScheduler(
            List<AnnotationProcessor> processors,
            Path generatedRoot,
            boolean deterministic,
            long timeoutMs,
            Map<String, String> options
    ) {
        this.processors = processors == null ? List.of() : List.copyOf(processors);
        this.generatedRoot = generatedRoot;
        this.deterministic = deterministic;
        this.timeoutMs = timeoutMs <= 0 ? DEFAULT_TIMEOUT_MS : timeoutMs;
        this.options = options == null ? Map.of() : Map.copyOf(options);
    }

    public List<String> explainLog() {
        return List.copyOf(explainLog);
    }

    public Result run(CompilationUnit unit, String targetTriple, DiagnosticReporter reporter) {
        ProgramModel model = ProgramModelBuilder.build(unit, targetTriple);
        Map<String, String> previousFingerprints = loadPreviousFingerprints();
        List<GeneratedArtifact> all = new ArrayList<>();
        Set<String> writtenKeys = new LinkedHashSet<>();
        int round = 1;
        boolean generatedNew = true;
        while (generatedNew && round <= MAX_ROUNDS) {
            generatedNew = false;
            RoundEnvironment env = new RoundEnvironment(model, round, round == MAX_ROUNDS);
            for (AnnotationProcessor proc : processors) {
                if (!shouldRun(proc, model)) {
                    explainLog.add("SKIP " + proc.name() + ": no matching annotations (round " + round + ")");
                    continue;
                }
                CodeGenerator gen = new CodeGenerator(proc.name(), proc.version());
                ProcessingContext ctx = new ProcessingContext(
                        model,
                        env,
                        gen,
                        reporter,
                        targetTriple,
                        deterministic,
                        proc.permissions(),
                        options,
                        proc.name(),
                        previousFingerprints,
                        generatedRoot);
                explainLog.add("RUN " + proc.name() + " (" + proc.kind() + ") round " + round);
                try {
                    runIsolated(proc, ctx);
                } catch (ProcessorTimeoutException e) {
                    reporter.error(SourceLocation.UNKNOWN,
                            "processor " + proc.name() + " timed out after " + timeoutMs + "ms");
                    continue;
                } catch (ProcessorCrashException e) {
                    reporter.error(SourceLocation.UNKNOWN,
                            "processor " + proc.name() + " crashed: " + e.getMessage());
                    continue;
                } catch (ProcessorPermissionException e) {
                    reporter.error(SourceLocation.UNKNOWN, e.getMessage());
                    continue;
                } catch (RuntimeException e) {
                    reporter.error(SourceLocation.UNKNOWN,
                            "processor " + proc.name() + " crashed: " + e.getClass().getSimpleName()
                                    + ": " + e.getMessage());
                    continue;
                }
                for (GeneratedArtifact art : gen.artifacts()) {
                    String key = art.kind() + ":" + art.relativePath();
                    if (!writtenKeys.add(key)) {
                        explainLog.add("  REUSE " + art.relativePath());
                        continue;
                    }
                    all.add(art);
                    String prevFp = previousFingerprints.get(art.relativePath().replace('\\', '/'));
                    if (prevFp != null && prevFp.equals(art.inputFingerprint())) {
                        explainLog.add("  REUSE " + art.relativePath()
                                + ": " + art.originSymbol() + " unchanged");
                    } else {
                        generatedNew = true;
                        explainLog.add("  GENERATE " + art.relativePath()
                                + " origin=" + art.originSymbol());
                    }
                }
            }
            round++;
        }
        if (round > MAX_ROUNDS + 1 || (generatedNew && round > MAX_ROUNDS)) {
            reporter.error(SourceLocation.UNKNOWN,
                    "JN-META-1099 processor round limit exceeded (possible generation cycle)");
        }
        try {
            writeArtifacts(all);
            gcOrphans(all);
        } catch (IOException e) {
            reporter.error(SourceLocation.UNKNOWN, "failed to write generated outputs: " + e.getMessage());
        }
        return new Result(all, explainLog);
    }

    private Map<String, String> loadPreviousFingerprints() {
        Map<String, String> map = new LinkedHashMap<>();
        if (generatedRoot == null) {
            return map;
        }
        Path index = generatedRoot.resolve("generated-index.tsv");
        if (!Files.isRegularFile(index)) {
            return map;
        }
        try {
            for (String line : Files.readAllLines(index, StandardCharsets.UTF_8)) {
                if (line.startsWith("path\t") || line.isBlank()) {
                    continue;
                }
                String[] cols = line.split("\t");
                if (cols.length >= 4) {
                    map.put(cols[0].replace('\\', '/'), cols[3]);
                }
            }
        } catch (IOException ignored) {
        }
        return map;
    }

    private boolean shouldRun(AnnotationProcessor proc, ProgramModel model) {
        for (var tid : proc.supportedAnnotations()) {
            if (!model.findTypesAnnotatedWith(tid.simpleName()).isEmpty()) {
                return true;
            }
            if (!model.findMethodsAnnotatedWith(tid.simpleName()).isEmpty()) {
                return true;
            }
            // field-level: scan
            for (var c : model.types()) {
                for (var f : c.fields()) {
                    if (f.hasAnnotation(tid.simpleName())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void runIsolated(AnnotationProcessor proc, ProcessingContext ctx) {
        ExecutorService es = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "jnative-processor-" + proc.name());
            t.setDaemon(true);
            return t;
        });
        try {
            Future<Void> fut = es.submit((Callable<Void>) () -> {
                proc.process(ctx);
                return null;
            });
            fut.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new ProcessorTimeoutException(proc.name());
        } catch (ProcessorPermissionException e) {
            throw e;
        } catch (Exception e) {
            Throwable c = e.getCause() == null ? e : e.getCause();
            if (c instanceof ProcessorPermissionException ppe) {
                throw ppe;
            }
            throw new ProcessorCrashException(proc.name(), c);
        } finally {
            es.shutdownNow();
        }
    }

    private void writeArtifacts(List<GeneratedArtifact> artifacts) throws IOException {
        if (generatedRoot == null) {
            return;
        }
        Files.createDirectories(generatedRoot);
        Path index = generatedRoot.resolve("generated-index.tsv");
        StringBuilder idx = new StringBuilder();
        idx.append("path\tprocessor\torigin\tfingerprint\thash\n");
        for (GeneratedArtifact art : artifacts) {
            Path out = generatedRoot.resolve(art.relativePath()).normalize();
            if (!out.startsWith(generatedRoot)) {
                throw new IOException("processor write outside generated directory: " + art.relativePath());
            }
            Files.createDirectories(out.getParent());
            String body = art.kind() == GeneratedArtifact.Kind.SOURCE
                    ? art.headerComment() + art.content()
                    : art.content();
            Files.writeString(out, body, StandardCharsets.UTF_8);
            idx.append(art.relativePath()).append('\t')
                    .append(art.processorName()).append('\t')
                    .append(art.originSource()).append(':').append(art.originSymbol()).append('\t')
                    .append(art.inputFingerprint()).append('\t')
                    .append(art.outputHash()).append('\n');
        }
        Files.writeString(index, idx.toString(), StandardCharsets.UTF_8);
    }

    private void gcOrphans(List<GeneratedArtifact> artifacts) throws IOException {
        if (generatedRoot == null || !Files.isDirectory(generatedRoot)) {
            return;
        }
        Set<String> keep = new LinkedHashSet<>();
        keep.add("generated-index.tsv");
        for (GeneratedArtifact a : artifacts) {
            keep.add(a.relativePath());
        }
        try (var walk = Files.walk(generatedRoot)) {
            walk.filter(Files::isRegularFile)
                    .forEach(p -> {
                        String rel = generatedRoot.relativize(p).toString().replace('\\', '/');
                        if (!keep.contains(rel)) {
                            try {
                                Files.deleteIfExists(p);
                                explainLog.add("GC orphan generated: " + rel);
                            } catch (IOException ignored) {
                            }
                        }
                    });
        }
    }

    /** Merge generated sources into a compilation unit text for single-file compile. */
    public static String mergeGeneratedSources(String sourceText, List<GeneratedArtifact> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return sourceText;
        }
        StringBuilder sb = new StringBuilder(sourceText);
        if (!sourceText.contains("BEGIN GENERATED META")) {
            sb.append("\n\n// ---- BEGIN GENERATED META (jnative 0.32) ----\n");
            for (GeneratedArtifact a : artifacts) {
                if (a.kind() == GeneratedArtifact.Kind.SOURCE) {
                    sb.append(a.headerComment());
                    sb.append(a.content());
                    if (!a.content().endsWith("\n")) {
                        sb.append('\n');
                    }
                    sb.append('\n');
                }
            }
            sb.append("// ---- END GENERATED META ----\n");
        }
        return sb.toString();
    }

    public record Result(List<GeneratedArtifact> artifacts, List<String> explain) {
    }
}
