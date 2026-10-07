package io.jnative.incremental;

import io.jnative.build.BuildTargetSpec;
import io.jnative.build.JNativeProject;
import io.jnative.build.Module;
import io.jnative.build.ProjectFile;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import io.jnative.Main;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Long-lived incremental compilation session (0.29).
 * Holds project graph, fingerprints, symbol/source deps, and local cache.
 */
public final class CompilerSession {
    private final Path projectRoot;
    private JNativeProject project;
    private final LocalBuildCache cache;
    private final ModuleGraph moduleGraph = new ModuleGraph();
    private final SourceDependencyGraph sourceGraph = new SourceDependencyGraph();
    private final SymbolIndex symbolIndex = new SymbolIndex();
    private final Map<String, UnitFingerprint> fingerprints = new LinkedHashMap<>();
    private final Map<String, String> lastGoodArtifacts = new LinkedHashMap<>();
    private final Map<String, String> unitTexts = new LinkedHashMap<>();
    private final AtomicLong generation = new AtomicLong(0);
    private final JNativeCompiler compiler = new JNativeCompiler();
    private volatile long cancelledGeneration = -1;
    private int optLevel = 1; // -Odev default
    private boolean explain;
    private boolean releaseMode;
    private int jobs;
    private boolean initialBuildDone;

    public CompilerSession(Path projectRoot) throws IOException {
        this.projectRoot = projectRoot.toAbsolutePath().normalize();
        this.project = ProjectFile.discover(this.projectRoot);
        Path cacheRoot = this.projectRoot.resolve(project.cacheDir());
        this.cache = new LocalBuildCache(cacheRoot);
        this.cache.ensureLayout();
        Files.createDirectories(this.projectRoot.resolve(project.buildDir()));
        Files.createDirectories(this.projectRoot.resolve(project.generatedDir()));
        Files.createDirectories(this.projectRoot.resolve(".jnative/graph"));
        this.jobs = Math.max(1, Runtime.getRuntime().availableProcessors() - 1);
        rebuildModuleGraph();
        loadPersistedState();
        indexAllSources();
    }

    public Path projectRoot() {
        return projectRoot;
    }

    public JNativeProject project() {
        return project;
    }

    public LocalBuildCache cache() {
        return cache;
    }

    public ModuleGraph moduleGraph() {
        return moduleGraph;
    }

    public SourceDependencyGraph sourceGraph() {
        return sourceGraph;
    }

    public SymbolIndex symbolIndex() {
        return symbolIndex;
    }

    public boolean explain() {
        return explain;
    }

    public void setOptLevel(int optLevel) {
        this.optLevel = Math.max(0, Math.min(3, optLevel));
    }

    public void setExplain(boolean explain) {
        this.explain = explain;
    }

    /** When true, remap {@code build/X} outputs to {@code build/release/X}. */
    public void setReleaseMode(boolean releaseMode) {
        this.releaseMode = releaseMode;
    }

    public boolean releaseMode() {
        return releaseMode;
    }

    /** Resolved output path for a target (honors release layout). */
    public String targetOutput(BuildTargetSpec target) {
        String out = target.output();
        if (!releaseMode) {
            return out;
        }
        if (out.startsWith("build/") && !out.startsWith("build/release/")) {
            return "build/release/" + out.substring("build/".length());
        }
        if (out.contains(".jnative/build") || out.startsWith(".jnative/")) {
            return "build/release/" + target.name();
        }
        Path p = Path.of(out);
        Path parent = p.getParent();
        if (parent == null) {
            return "build/release/" + out;
        }
        return parent.resolve("release").resolve(p.getFileName()).toString().replace('\\', '/');
    }

    /** First existing native artifact from this session / project layout. */
    public Path primaryArtifact() {
        for (BuildTargetSpec t : project.targets()) {
            if (t.isBrowser()) {
                continue;
            }
            Path out = projectRoot.resolve(targetOutput(t)).toAbsolutePath().normalize();
            if (Files.exists(out)) {
                return out;
            }
            String known = lastGoodArtifacts.get(t.name());
            if (known != null) {
                Path k = Path.of(known).toAbsolutePath().normalize();
                // Only accept last-good when it matches the expected layout for this mode.
                if (Files.exists(k) && k.equals(out)) {
                    return k;
                }
            }
        }
        return null;
    }

    public void setJobs(int jobs) {
        this.jobs = jobs;
    }

    public long currentGeneration() {
        return generation.get();
    }

    public void cancelStale(long gen) {
        cancelledGeneration = gen;
    }

    public BuildResult build() throws Exception {
        return build(List.of(), false);
    }

    public BuildResult build(List<String> changedRelativePaths, boolean resourceOnly) throws Exception {
        long gen = generation.incrementAndGet();
        long t0 = System.currentTimeMillis();
        if (resourceOnly) {
            BuildResult r = new BuildResult(true, gen, System.currentTimeMillis() - t0);
            r.setCompilerExecuted(false);
            for (String p : changedRelativePaths) {
                r.changed().add(p);
                r.reloadedTargets().add("browser");
                r.explain().add("Reused compiler; resource-only change: " + p);
            }
            return r;
        }

        rebuildModuleGraph();
        List<String> cycle = moduleGraph.findCycle();
        if (!cycle.isEmpty()) {
            BuildResult fail = new BuildResult(false, gen, System.currentTimeMillis() - t0);
            fail.diagnostics().add("module cycle: " + String.join(" -> ", cycle));
            fail.setPreservedLastGood(true);
            return fail;
        }

        Set<String> known = new LinkedHashSet<>(listAllSources());
        sourceGraph.clear();
        Map<String, String> texts = new LinkedHashMap<>();
        for (String rel : known) {
            Path abs = projectRoot.resolve(rel);
            String text = Files.isRegularFile(abs)
                    ? Files.readString(abs, StandardCharsets.UTF_8) : "";
            texts.put(rel, text);
            sourceGraph.indexUnit(rel, text, known);
            symbolIndex.indexSource(rel, text);
        }

        List<String> seeds = new ArrayList<>(changedRelativePaths.isEmpty()
                ? detectChanges(texts)
                : changedRelativePaths);
        Set<String> invalidate = new LinkedHashSet<>();
        for (String rel : seeds) {
            UnitFingerprint neu = UnitFingerprint.fromSource(rel, texts.getOrDefault(rel, ""));
            UnitFingerprint old = fingerprints.get(rel);
            ChangeClassification c = UnitFingerprint.classify(old, neu);
            invalidate.addAll(sourceGraph.invalidateClosure(rel, c));
            fingerprints.put(rel, neu);
            unitTexts.put(rel, texts.getOrDefault(rel, ""));
        }
        // Ensure fingerprints exist for unchanged units
        for (String rel : known) {
            if (!fingerprints.containsKey(rel)) {
                UnitFingerprint fp = UnitFingerprint.fromSource(rel, texts.get(rel));
                fingerprints.put(rel, fp);
                unitTexts.put(rel, texts.get(rel));
                invalidate.add(rel);
            }
        }
        boolean needInitialArtifacts = false;
        for (BuildTargetSpec t : project.targets()) {
            Path out = projectRoot.resolve(targetOutput(t));
            if (!Files.exists(out)) {
                needInitialArtifacts = true;
                break;
            }
        }
        initialBuildDone = true;

        final BuildResult result = new BuildResult(true, gen, 0);
        for (String rel : known) {
            if (invalidate.contains(rel)) {
                result.changed().add(rel);
                result.parsed().add(rel);
                result.semantic().add(rel);
                result.irGenerated().add(rel);
                result.explain().add(explainLine(rel, fingerprints.get(rel), true));
            } else {
                result.reused().add(rel);
                result.explain().add("Reused " + rel + "\nReason:\nSOURCE_AND_DEPENDENCIES_UNCHANGED");
            }
        }

        if (cancelledGeneration == gen) {
            BuildResult cancelled = new BuildResult(false, gen, System.currentTimeMillis() - t0);
            cancelled.diagnostics().add("build cancelled (stale generation)");
            cancelled.setPreservedLastGood(true);
            return cancelled;
        }

        // Determine affected targets
        Set<String> affectedTargets = resolveAffectedTargets(invalidate);
        if (invalidate.isEmpty() && !needInitialArtifacts) {
            result.setCacheHits(known.size());
            result.setCompilerExecuted(false);
            long dt = System.currentTimeMillis() - t0;
            BuildResult done = copyWithTime(result, true, gen, dt);
            try {
                persistState();
            } catch (IOException ignored) {
            }
            return done;
        }

        // Compile affected entrypoints (main sources of affected targets)
        List<BuildScheduler.BuildTask> tasks = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        for (BuildTargetSpec target : project.targets()) {
            boolean include = needInitialArtifacts
                    || affectedTargets.contains(target.name())
                    || invalidate.size() == known.size();
            if (!include) {
                continue;
            }
            final BuildTargetSpec t = target;
            tasks.add(new BuildScheduler.BuildTask(t.name(),
                    BuildScheduler.ResourceHint.LINK,
                    () -> {
                        compileTarget(t, gen, result, errors);
                        return null;
                    }));
        }
        if (tasks.isEmpty() && !invalidate.isEmpty()) {
            for (BuildTargetSpec target : project.targets()) {
                if (targetTouches(target, invalidate)) {
                    final BuildTargetSpec t = target;
                    tasks.add(new BuildScheduler.BuildTask(t.name(),
                            BuildScheduler.ResourceHint.LINK,
                            () -> {
                                compileTarget(t, gen, result, errors);
                                return null;
                            }));
                }
            }
        }

        new BuildScheduler(jobs).runAll(tasks);

        boolean ok = errors.isEmpty();
        if (!ok) {
            result.setPreservedLastGood(true);
            result.diagnostics().addAll(errors);
        }
        try {
            Map<String, Object> st = cache.stats();
            result.setCacheHits(((Number) st.getOrDefault("hits", 0)).intValue());
            result.setCacheMisses(((Number) st.getOrDefault("misses", 0)).intValue());
        } catch (IOException ignored) {
        }
        BuildResult finalResult = copyWithTime(result, ok, gen, System.currentTimeMillis() - t0);
        if (ok) {
            try {
                persistState();
            } catch (IOException ignored) {
            }
        }
        return finalResult;
    }

    private void compileTarget(
            BuildTargetSpec target,
            long gen,
            BuildResult result,
            List<String> errors
    ) throws Exception {
        if (cancelledGeneration == gen) {
            return;
        }
        Path main = findMainSource(target);
        if (main == null || !Files.isRegularFile(main)) {
            errors.add("main source not found for target " + target.name());
            return;
        }
        String rel = projectRoot.relativize(main).toString().replace('\\', '/');
        String sourceText = Files.readString(main, StandardCharsets.UTF_8);
        UnitFingerprint fp = fingerprints.getOrDefault(rel, UnitFingerprint.fromSource(rel, sourceText));
        String targetFp = targetContentFingerprint(target);
        String outRel = targetOutput(target);
        String key = cache.cacheKey(
                Main.VERSION,
                target.triple(),
                optLevel + ":" + (releaseMode ? "release" : "dev") + ":" + outRel,
                fp.contentHash(),
                targetFp,
                "rpc");
        Path out = projectRoot.resolve(outRel);
        Files.createDirectories(out.getParent() == null ? projectRoot.resolve(project.buildDir()) : out.getParent());
        Path published = out;
        Path staging = out.resolveSibling(out.getFileName() + ".tmp." + gen);

        if (cache.contains(key) && Files.exists(published)) {
            lastGoodArtifacts.put(target.name(), published.toAbsolutePath().toString());
            result.reused().add("artifact:" + target.name());
            result.explain().add("Reused artifact " + target.name() + "\nReason:\nCACHE_HIT");
            return;
        }

        Path compileSource = materializeModuleCompileUnit(target, main);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(compileSource);
        options.setOutputFile(staging);
        options.setOptLevel(optLevel);
        options.setTarget(target.isBrowser() ? "wasm32-browser" : "native");
        CompileResult cr = compiler.compile(options);
        if (!cr.success()) {
            String msg = cr.diagnostics().stream()
                    .map(Object::toString)
                    .collect(Collectors.joining("\n"));
            errors.add(target.name() + ": " + msg);
            Files.deleteIfExists(staging);
            // preserve last good
            return;
        }
        // Atomic publish
        if (Files.isDirectory(staging)) {
            // wasm dist directory
            Path finalDir = published;
            if (Files.exists(finalDir) && !Files.isSameFile(staging, finalDir)) {
                // replace contents carefully
            }
            Files.createDirectories(finalDir.getParent());
            if (!Files.exists(finalDir)) {
                Files.move(staging, finalDir, StandardCopyOption.REPLACE_EXISTING);
            } else {
                // copy over
                try (Stream<Path> walk = Files.walk(staging)) {
                    walk.forEach(src -> {
                        try {
                            Path dest = finalDir.resolve(staging.relativize(src).toString());
                            if (Files.isDirectory(src)) {
                                Files.createDirectories(dest);
                            } else {
                                Files.createDirectories(dest.getParent());
                                Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                            }
                        } catch (IOException ignored) {
                        }
                    });
                }
            }
        } else if (Files.isRegularFile(staging)) {
            Files.move(staging, published, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        }
        lastGoodArtifacts.put(target.name(), published.toString());
        cache.putString(key, published.toString());
        result.linkedTargets().add(target.name());
        if (target.isBrowser()) {
            result.reloadedTargets().add(target.name());
        } else if ("server".equals(project.module(target.module()) != null
                ? project.module(target.module()).kind() : "")) {
            result.restartedTargets().add(target.name());
        } else if ("server".equals(target.name()) || target.name().contains("server")) {
            result.restartedTargets().add(target.name());
        }
        result.explain().add("Compiled " + target.name() + " from " + rel + "\nReason:\nSOURCE_CHANGED");
    }

    private String dependencyFingerprint(String rel) {
        StringBuilder sb = new StringBuilder();
        for (SourceDependencyGraph.Edge e : sourceGraph.outgoing(rel)) {
            UnitFingerprint fp = fingerprints.get(e.to());
            sb.append(e.kind()).append(':').append(e.to()).append(':')
                    .append(fp == null ? "" : fp.publicApiHash()).append(';');
        }
        return ContentHasher.sha256(sb.toString());
    }

    /** Content hash of all sources in the target module and its dependsOn modules. */
    private String targetContentFingerprint(BuildTargetSpec target) throws IOException {
        StringBuilder sb = new StringBuilder();
        Module m = project.module(target.module());
        List<Module> mods = new ArrayList<>();
        if (m != null) {
            mods.add(m);
            for (String d : m.dependsOn()) {
                Module dep = project.module(d);
                if (dep != null) {
                    mods.add(dep);
                }
            }
        }
        for (Module mod : mods) {
            for (String root : mod.sourceRoots()) {
                Path base = projectRoot.resolve(root);
                if (!Files.isDirectory(base)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(base)) {
                    walk.filter(p -> p.toString().endsWith(".java"))
                            .filter(Files::isRegularFile)
                            .sorted()
                            .forEach(p -> {
                                String rel = projectRoot.relativize(p).toString().replace('\\', '/');
                                UnitFingerprint fp = fingerprints.get(rel);
                                sb.append(rel).append(':')
                                        .append(fp == null ? "" : fp.contentHash())
                                        .append(';');
                            });
                }
            }
        }
        return ContentHasher.sha256(sb.toString());
    }

    private List<String> detectChanges(Map<String, String> texts) {
        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, String> e : texts.entrySet()) {
            UnitFingerprint neu = UnitFingerprint.fromSource(e.getKey(), e.getValue());
            UnitFingerprint old = fingerprints.get(e.getKey());
            if (old == null || !old.contentHash().equals(neu.contentHash())) {
                changed.add(e.getKey());
            }
        }
        // deletions
        for (String old : new ArrayList<>(fingerprints.keySet())) {
            if (!texts.containsKey(old)) {
                changed.add(old);
                fingerprints.remove(old);
            }
        }
        return changed;
    }

    private Set<String> resolveAffectedTargets(Set<String> invalidate) {
        Set<String> targets = new LinkedHashSet<>();
        for (BuildTargetSpec t : project.targets()) {
            if (targetTouches(t, invalidate)) {
                targets.add(t.name());
            }
        }
        return targets;
    }

    private boolean targetTouches(BuildTargetSpec t, Set<String> invalidate) {
        Module m = project.module(t.module());
        if (m == null) {
            return true;
        }
        for (String rel : invalidate) {
            for (String root : m.sourceRoots()) {
                if (rel.startsWith(root.replace('\\', '/') ) || rel.contains("/" + t.module() + "/")
                        || rel.startsWith(t.module() + "/")) {
                    return true;
                }
            }
            // shared dependency
            for (String dep : m.dependsOn()) {
                if (rel.startsWith(dep + "/") || rel.contains("/" + dep + "/")) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Multi-module projects share DTOs via dependsOn. The compiler still takes a single
     * source file, so we materialize a temporary compilation unit that concatenates
     * dependency + same-module sources (main last).
     */
    private Path materializeModuleCompileUnit(BuildTargetSpec target, Path main) throws IOException {
        Module m = project.module(target.module());
        if (m == null) {
            return main;
        }
        List<Path> roots = new ArrayList<>();
        for (String d : m.dependsOn()) {
            Module dep = project.module(d);
            if (dep != null) {
                for (String root : dep.sourceRoots()) {
                    roots.add(projectRoot.resolve(root));
                }
            }
        }
        for (String root : m.sourceRoots()) {
            roots.add(projectRoot.resolve(root));
        }
        List<Path> files = new ArrayList<>();
        LinkedHashSet<Path> seen = new LinkedHashSet<>();
        for (Path root : roots) {
            if (!Files.isDirectory(root)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(root, 4)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".java"))
                        .sorted()
                        .forEach(p -> {
                            Path abs = p.toAbsolutePath().normalize();
                            if (seen.add(abs)) {
                                files.add(abs);
                            }
                        });
            }
        }
        if (files.size() <= 1) {
            return main;
        }
        Path mainAbs = main.toAbsolutePath().normalize();
        StringBuilder sb = new StringBuilder();
        for (Path p : files) {
            if (p.equals(mainAbs)) {
                continue;
            }
            String text = Files.readString(p, StandardCharsets.UTF_8);
            if (target.isBrowser() && text.contains("@Rpc")) {
                continue;
            }
            sb.append(text);
            if (!text.endsWith("\n")) {
                sb.append('\n');
            }
            sb.append('\n');
        }
        sb.append(Files.readString(mainAbs, StandardCharsets.UTF_8));
        Path dir = projectRoot.resolve(project.buildDir()).resolve("units");
        Files.createDirectories(dir);
        Path unit = dir.resolve(target.name() + "-unit.java");
        Files.writeString(unit, sb.toString(), StandardCharsets.UTF_8);
        return unit;
    }

    private Path findMainSource(BuildTargetSpec target) throws IOException {
        Module m = project.module(target.module());
        if (m == null) {
            return null;
        }
        List<String> candidates = List.of(
                target.mainClass() + ".java",
                "App.java",
                "Main.java",
                "Server.java"
        );
        for (String root : m.sourceRoots()) {
            Path base = projectRoot.resolve(root);
            for (String c : candidates) {
                Path p = base.resolve(c);
                if (Files.isRegularFile(p)) {
                    return p;
                }
            }
            // recursive search for App.java
            if (Files.isDirectory(base)) {
                try (Stream<Path> walk = Files.walk(base, 3)) {
                    Path found = walk.filter(p -> p.getFileName().toString().equals(target.mainClass() + ".java")
                                    || p.getFileName().toString().equals("App.java"))
                            .filter(Files::isRegularFile)
                            .findFirst()
                            .orElse(null);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private void rebuildModuleGraph() {
        // reload project file if present
        try {
            Path pf = projectRoot.resolve("jnative.project");
            if (Files.isRegularFile(pf)) {
                project = ProjectFile.load(pf);
            }
        } catch (IOException ignored) {
        }
        moduleGraph.clear();
        for (Module m : project.modules().values()) {
            moduleGraph.addModule(m.name());
            for (String d : m.dependsOn()) {
                moduleGraph.addEdge(m.name(), d);
            }
        }
    }

    private void indexAllSources() throws IOException {
        List<String> all = listAllSources();
        Set<String> known = new LinkedHashSet<>(all);
        for (String rel : all) {
            Path abs = projectRoot.resolve(rel);
            String text = Files.readString(abs, StandardCharsets.UTF_8);
            unitTexts.put(rel, text);
            // Keep persisted fingerprint for change detection across CLI invocations
            if (!fingerprints.containsKey(rel)) {
                fingerprints.put(rel, UnitFingerprint.fromSource(rel, text));
            }
            sourceGraph.indexUnit(rel, text, known);
            symbolIndex.indexSource(rel, text);
        }
    }

    private Path graphDir() {
        return projectRoot.resolve(".jnative/graph");
    }

    private void loadPersistedState() {
        Path fpFile = graphDir().resolve("fingerprints.tsv");
        Path artFile = graphDir().resolve("artifacts.tsv");
        try {
            if (Files.isRegularFile(fpFile)) {
                for (String line : Files.readAllLines(fpFile, StandardCharsets.UTF_8)) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    String[] p = line.split("\t", -1);
                    if (p.length >= 7) {
                        fingerprints.put(p[0], new UnitFingerprint(p[0], p[1], p[2], p[3], p[4], p[5], p[6]));
                    }
                }
            }
            if (Files.isRegularFile(artFile)) {
                for (String line : Files.readAllLines(artFile, StandardCharsets.UTF_8)) {
                    if (line.isBlank() || line.startsWith("#")) {
                        continue;
                    }
                    String[] p = line.split("\t", 2);
                    if (p.length == 2) {
                        lastGoodArtifacts.put(p[0], p[1]);
                    }
                }
            }
            if (!fingerprints.isEmpty()) {
                initialBuildDone = true;
            }
        } catch (IOException ignored) {
        }
    }

    private void persistState() throws IOException {
        Files.createDirectories(graphDir());
        StringBuilder fp = new StringBuilder("# path\tcontent\tapi\timpl\tabi\trpc\tjson\n");
        for (Map.Entry<String, UnitFingerprint> e : fingerprints.entrySet()) {
            UnitFingerprint f = e.getValue();
            fp.append(e.getKey()).append('\t')
                    .append(f.contentHash()).append('\t')
                    .append(f.publicApiHash()).append('\t')
                    .append(f.implementationHash()).append('\t')
                    .append(f.abiHash()).append('\t')
                    .append(f.rpcHash()).append('\t')
                    .append(f.jsonHash()).append('\n');
        }
        atomicWrite(graphDir().resolve("fingerprints.tsv"), fp.toString());
        StringBuilder art = new StringBuilder("# target\tpath\n");
        for (Map.Entry<String, String> e : lastGoodArtifacts.entrySet()) {
            art.append(e.getKey()).append('\t').append(e.getValue()).append('\n');
        }
        atomicWrite(graphDir().resolve("artifacts.tsv"), art.toString());
    }

    private static void atomicWrite(Path dest, String text) throws IOException {
        Path tmp = dest.resolveSibling(dest.getFileName() + ".tmp");
        Files.writeString(tmp, text, StandardCharsets.UTF_8);
        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private List<String> listAllSources() throws IOException {
        List<String> out = new ArrayList<>();
        for (Module m : project.modules().values()) {
            for (String root : m.sourceRoots()) {
                Path base = projectRoot.resolve(root);
                if (!Files.isDirectory(base)) {
                    continue;
                }
                try (Stream<Path> walk = Files.walk(base)) {
                    walk.filter(p -> p.toString().endsWith(".java"))
                            .filter(Files::isRegularFile)
                            .forEach(p -> out.add(projectRoot.relativize(p).toString().replace('\\', '/')));
                }
            }
        }
        return out;
    }

    private static String explainLine(String rel, UnitFingerprint fp, boolean changed) {
        if (!changed) {
            return "Reused " + rel + "\nReason:\nSOURCE_AND_DEPENDENCIES_UNCHANGED";
        }
        return "Recompiled " + rel + "\nReason:\nSOURCE_CHANGED";
    }

    private static BuildResult copyWithTime(BuildResult r, boolean ok, long gen, long ms) {
        BuildResult n = new BuildResult(ok, gen, ms);
        n.changed().addAll(r.changed());
        n.parsed().addAll(r.parsed());
        n.semantic().addAll(r.semantic());
        n.irGenerated().addAll(r.irGenerated());
        n.reused().addAll(r.reused());
        n.linkedTargets().addAll(r.linkedTargets());
        n.restartedTargets().addAll(r.restartedTargets());
        n.reloadedTargets().addAll(r.reloadedTargets());
        n.explain().addAll(r.explain());
        n.diagnostics().addAll(r.diagnostics());
        n.setCacheHits(r.cacheHits());
        n.setCacheMisses(r.cacheMisses());
        n.setCompilerExecuted(r.compilerExecuted());
        n.setPreservedLastGood(r.preservedLastGood());
        return n;
    }

    public Map<String, UnitFingerprint> fingerprints() {
        return fingerprints;
    }
}
