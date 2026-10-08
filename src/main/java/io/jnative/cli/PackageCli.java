package io.jnative.cli;

import io.jnative.pkg.BuildGraphPackageIntegration;
import io.jnative.pkg.FilesystemRegistry;
import io.jnative.pkg.PackageId;
import io.jnative.pkg.PackageManager;
import io.jnative.pkg.ResolvedDependencyGraph;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** CLI subcommands for the 0.30 package manager. */
public final class PackageCli {

    private PackageCli() {
    }

    public static int dispatch(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("usage: jnative <add|remove|update|deps|search|info|publish|audit|licenses|...>");
            return 1;
        }
        Path root = Path.of(".").toAbsolutePath().normalize();
        List<Path> registries = new ArrayList<>();
        boolean offline = false;
        for (int i = 1; i < args.length; i++) {
            if ("--project".equals(args[i]) && i + 1 < args.length) {
                root = Path.of(args[++i]).toAbsolutePath().normalize();
            } else if ("--registry".equals(args[i]) && i + 1 < args.length) {
                registries.add(Path.of(args[++i]).toAbsolutePath().normalize());
            } else if ("--offline".equals(args[i])) {
                offline = true;
            }
        }
        PackageManager pm = new PackageManager(root);
        if (offline) {
            pm.setOffline(true);
        }
        // Prepend CLI registries (first wins for publish + resolve precedence).
        for (int i = registries.size() - 1; i >= 0; i--) {
            Path reg = registries.get(i);
            Files.createDirectories(reg);
            pm.addRegistry(new FilesystemRegistry(reg, "cli"));
        }
        return switch (args[0]) {
            case "add" -> {
                if (args.length < 2) {
                    System.err.println("usage: jnative add <package> [constraint]");
                    yield 1;
                }
                String constraint = args.length >= 3 && !args[2].startsWith("-") ? args[2] : null;
                pm.add(args[1], constraint);
                System.out.println("added " + args[1] + (constraint == null ? "" : " " + constraint));
                yield 0;
            }
            case "remove" -> {
                if (args.length < 2) {
                    System.err.println("usage: jnative remove <package>");
                    yield 1;
                }
                pm.remove(args[1]);
                System.out.println("removed " + args[1]);
                yield 0;
            }
            case "update" -> {
                String pkg = args.length >= 2 && !args[1].startsWith("-") ? args[1] : null;
                pm.update(pkg);
                System.out.println(pkg == null ? "updated all dependencies" : "updated " + pkg);
                yield 0;
            }
            case "deps" -> runDeps(pm, args);
            case "search" -> {
                String q = args.length >= 2 ? args[1] : "";
                for (PackageId id : pm.search(q)) {
                    System.out.println(id.coordinate());
                }
                yield 0;
            }
            case "info" -> {
                if (args.length < 2) {
                    System.err.println("usage: jnative info <package>");
                    yield 1;
                }
                System.out.print(pm.info(args[1]));
                yield 0;
            }
            case "publish" -> {
                boolean dry = false;
                for (String a : args) {
                    if ("--dry-run".equals(a)) {
                        dry = true;
                    }
                }
                if (registries.isEmpty() && pm.registries().registries().isEmpty()) {
                    System.err.println("error: no registry configured; pass --registry <path> or create ./registry");
                    yield 1;
                }
                var r = pm.publish(dry);
                System.out.println((dry ? "dry-run " : "") + "publish " + r.id() + "@" + r.version()
                        + (r.hash().isEmpty() ? "" : " sha256:" + r.hash()));
                if (!pm.registries().url().isEmpty()) {
                    System.out.println("  registry: " + pm.registries().url());
                }
                for (String f : r.files()) {
                    System.out.println("  " + f);
                }
                yield 0;
            }
            case "audit" -> {
                for (String f : pm.audit()) {
                    System.out.println(f);
                }
                yield 0;
            }
            case "licenses" -> {
                System.out.print(pm.licenses());
                yield 0;
            }
            default -> {
                System.err.println("unknown package command: " + args[0]);
                yield 1;
            }
        };
    }

    private static int runDeps(PackageManager pm, String[] args) throws Exception {
        boolean graph = false;
        String why = null;
        String explain = null;
        for (int i = 1; i < args.length; i++) {
            if ("--graph".equals(args[i])) {
                graph = true;
            } else if ("--json".equals(args[i])) {
                System.out.print(pm.resolve("native", false, false).toJson());
                return 0;
            } else if ("--why".equals(args[i]) && i + 1 < args.length) {
                why = args[++i];
            } else if ("explain".equals(args[i]) && i + 1 < args.length) {
                explain = args[++i];
            }
        }
        // jnative deps explain postgres
        if (args.length >= 3 && "explain".equals(args[1])) {
            System.out.print(pm.explain(args[2]));
            return 0;
        }
        if (why != null) {
            System.out.print(pm.why(why));
            return 0;
        }
        if (explain != null) {
            System.out.print(pm.explain(explain));
            return 0;
        }
        ResolvedDependencyGraph g = pm.resolve("native", false, false);
        if (graph) {
            System.out.print(g.toDot());
        } else {
            System.out.print(g.tree());
        }
        // materialize for build-graph visibility
        var nodes = BuildGraphPackageIntegration.materialize(g, pm.cache());
        System.out.println("# package build nodes: " + nodes.size());
        return 0;
    }
}
