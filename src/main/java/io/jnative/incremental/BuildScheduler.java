package io.jnative.incremental;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * DAG-aware parallel task runner. Avoids oversubscription by defaulting to
 * min(CPU, jobs) and marking LINK tasks for serial-ish execution when needed.
 * @author 张晓达
 */
public final class BuildScheduler {
    public enum ResourceHint { CPU, IO, MEMORY_HEAVY, LINK }

    public record BuildTask(String id, ResourceHint hint, Callable<Void> work) {
    }

    private final int jobs;
    private final AtomicInteger completed = new AtomicInteger();

    public BuildScheduler(int jobs) {
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        this.jobs = jobs <= 0 ? Math.max(1, cores - 1) : Math.min(jobs, cores);
    }

    public int jobs() {
        return jobs;
    }

    public void runAll(List<BuildTask> tasks) throws Exception {
        if (tasks.isEmpty()) {
            return;
        }
        List<BuildTask> links = new ArrayList<>();
        List<BuildTask> others = new ArrayList<>();
        for (BuildTask t : tasks) {
            if (t.hint() == ResourceHint.LINK) {
                links.add(t);
            } else {
                others.add(t);
            }
        }
        ExecutorService pool = Executors.newFixedThreadPool(jobs);
        try {
            List<Future<Void>> futures = new ArrayList<>();
            for (BuildTask t : others) {
                futures.add(pool.submit(() -> {
                    t.work().call();
                    completed.incrementAndGet();
                    return null;
                }));
            }
            for (Future<Void> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    throw unwrap(e);
                }
            }
            // Link phase sequential to reduce LLVM/linker oversubscription
            for (BuildTask t : links) {
                t.work().call();
                completed.incrementAndGet();
            }
        } finally {
            pool.shutdownNow();
        }
    }

    public int completedCount() {
        return completed.get();
    }

    private static Exception unwrap(ExecutionException e) {
        Throwable c = e.getCause();
        if (c instanceof Exception ex) {
            return ex;
        }
        return e;
    }
}
