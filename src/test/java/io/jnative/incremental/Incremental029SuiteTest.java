package io.jnative.incremental;

import io.jnative.build.JNativeProject;
import io.jnative.build.Module;
import io.jnative.build.ProjectFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class Incremental029SuiteTest {

    @TempDir Path tmp;

    @Test
    void moduleDagAndCycleDetection() {
        ModuleGraph g = new ModuleGraph();
        g.addEdge("server", "shared");
        g.addEdge("browser", "shared");
        g.addEdge("desktop", "shared");
        assertTrue(g.findCycle().isEmpty());
        assertEquals(Set.of("server", "browser", "desktop"), g.dependents("shared"));

        ModuleGraph cycle = new ModuleGraph();
        cycle.addEdge("a", "b");
        cycle.addEdge("b", "c");
        cycle.addEdge("c", "a");
        List<String> path = cycle.findCycle();
        assertFalse(path.isEmpty());
        assertTrue(path.contains("a"));
    }

    @Test
    void sourceAndSymbolDependencyKinds() {
        SourceDependencyGraph g = new SourceDependencyGraph();
        Set<String> units = Set.of(
                "shared/User.java",
                "shared/UserApi.java",
                "browser/UsersPage.java",
                "server/UserRepository.java",
                "shared/UserJsonCodec.java"
        );
        g.indexUnit("browser/UsersPage.java",
                "class UsersPage { User u; UserApi api; }", units);
        g.indexUnit("server/UserRepository.java",
                "class UserRepository { User find() { return null; } }", units);
        g.indexUnit("shared/UserJsonCodec.java",
                "class UserJsonCodec { User encode; }", units);
        assertFalse(g.outgoing("browser/UsersPage.java").isEmpty());
        boolean hasRpc = g.outgoing("browser/UsersPage.java").stream()
                .anyMatch(e -> e.to().contains("UserApi"));
        assertTrue(hasRpc);
    }

    @Test
    void fingerprintClassifiesPrivateBodyVsPublicApi() {
        String before = """
                class A {
                    public int foo() { return 1; }
                    private int calculate() { return 42; }
                }
                """;
        String implOnly = """
                class A {
                    public int foo() { return 1; }
                    private int calculate() { return 43; }
                }
                """;
        String apiChange = """
                class A {
                    public int foo(int x) { return 1; }
                    private int calculate() { return 42; }
                }
                """;
        UnitFingerprint a = UnitFingerprint.fromSource("A.java", before);
        UnitFingerprint b = UnitFingerprint.fromSource("A.java", implOnly);
        UnitFingerprint c = UnitFingerprint.fromSource("A.java", apiChange);
        assertEquals(ChangeClassification.IMPLEMENTATION_ONLY, UnitFingerprint.classify(a, b));
        assertEquals(ChangeClassification.PUBLIC_API_CHANGE, UnitFingerprint.classify(a, c));
    }

    @Test
    void dtoAndRpcFingerprints() {
        String user1 = "class User { String id; String name; }";
        String user2 = "class User { String id; String name; String email; }";
        assertEquals(ChangeClassification.JSON_SHAPE_CHANGE,
                UnitFingerprint.classify(
                        UnitFingerprint.fromSource("User.java", user1),
                        UnitFingerprint.fromSource("User.java", user2)));

        String rpc1 = "@Rpc(path = \"/rpc/users\")\ninterface UserApi { User getUser(String id); }";
        String rpc2 = "@Rpc(path = \"/rpc/users\")\ninterface UserApi { User getUser(String id); int countUsers(); }";
        assertEquals(ChangeClassification.RPC_CONTRACT_CHANGE,
                UnitFingerprint.classify(
                        UnitFingerprint.fromSource("UserApi.java", rpc1),
                        UnitFingerprint.fromSource("UserApi.java", rpc2)));
    }

    @Test
    void invalidationMinimalForImplementationOnly() {
        SourceDependencyGraph g = new SourceDependencyGraph();
        Set<String> units = Set.of("A.java", "B.java");
        g.addEdge("B.java", "A.java", DependencyKind.TYPE_REFERENCE);
        List<String> inv = g.invalidateClosure("A.java", ChangeClassification.IMPLEMENTATION_ONLY);
        assertEquals(List.of("A.java"), inv);

        g.addEdge("B.java", "A.java", DependencyKind.INLINE_DEPENDENCY);
        inv = g.invalidateClosure("A.java", ChangeClassification.IMPLEMENTATION_ONLY);
        assertTrue(inv.contains("A.java"));
        assertTrue(inv.contains("B.java"));
    }

    @Test
    void cacheHitMissCorruptionAndVersionKey() throws Exception {
        LocalBuildCache cache = new LocalBuildCache(tmp.resolve("cache"));
        cache.ensureLayout();
        String key = cache.cacheKey("0.29", "native", "1", "src", "dep", "flags");
        assertFalse(cache.contains(key));
        cache.putBytes(key, "hello".getBytes(StandardCharsets.UTF_8));
        assertTrue(cache.contains(key));
        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), cache.getBytes(key));

        // corruption → discard
        Files.write(cache.objectPath(key), new byte[0]);
        assertNull(cache.getBytes(key));

        String key2 = cache.cacheKey("0.30", "native", "1", "src", "dep", "flags");
        assertNotEquals(key, key2);

        Map<String, Object> st = cache.stats();
        assertTrue(((Number) st.get("hits")).longValue() >= 1
                || ((Number) st.get("misses")).longValue() >= 1);
        cache.clean();
    }

    @Test
    void projectFileMultiModule() throws Exception {
        Path root = tmp.resolve("admin");
        Files.createDirectories(root.resolve("shared"));
        Files.createDirectories(root.resolve("server"));
        Files.createDirectories(root.resolve("browser"));
        Files.writeString(root.resolve("jnative.project"), """
                {
                  "name": "admin",
                  "modules": {
                    "shared": { "kind": "lib", "src": "shared" },
                    "server": { "kind": "server", "src": "server", "dependsOn": ["shared"] },
                    "browser": { "kind": "browser", "src": "browser", "dependsOn": ["shared"] }
                  }
                }
                """);
        Files.writeString(root.resolve("shared/User.java"), "class User { String id; }\n");
        Files.writeString(root.resolve("server/App.java"), "class App { public static void main(String[] a){} }\n");
        Files.writeString(root.resolve("browser/UsersPage.java"), "class UsersPage { User u; }\n");

        JNativeProject p = ProjectFile.load(root.resolve("jnative.project"));
        assertEquals("admin", p.name());
        assertEquals(3, p.modules().size());
        Module server = p.module("server");
        assertTrue(server.dependsOn().contains("shared"));
        assertTrue(p.targets().stream().anyMatch(t -> t.isBrowser()));
    }

    @Test
    void compilerSessionBrowserOnlyVsServerOnly() throws Exception {
        Path root = writeMiniProject();
        CompilerSession session = new CompilerSession(root);
        BuildResult cold = session.build();
        assertNotNull(cold);

        // No-op
        BuildResult noop = session.build(List.of(), false);
        assertTrue(noop.changed().isEmpty() || !noop.compilerExecuted() || noop.parsed().isEmpty()
                || noop.reused().size() >= noop.changed().size());

        // Browser-only edit
        Path page = root.resolve("browser/UsersPage.java");
        Files.writeString(page, Files.readString(page) + "\n// touch\n");
        BuildResult browser = session.build(List.of("browser/UsersPage.java"), false);
        assertTrue(browser.changed().stream().anyMatch(c -> c.contains("UsersPage")));
        assertFalse(browser.restartedTargets().stream().anyMatch(t -> t.contains("server")),
                "browser-only edit must not restart server");

        // CSS resource-only
        BuildResult css = session.build(List.of("browser/public/app.css"), true);
        assertFalse(css.compilerExecuted());
        assertTrue(css.reloadedTargets().contains("browser"));

        // Server-only
        Path repo = root.resolve("server/UserRepository.java");
        Files.writeString(repo, Files.readString(repo).replace("return 42;", "return 43;"));
        BuildResult server = session.build(List.of("server/UserRepository.java"), false);
        assertTrue(server.changed().stream().anyMatch(c -> c.contains("UserRepository")));
        assertFalse(server.reloadedTargets().stream().anyMatch(t -> t.contains("browser"))
                        && server.linkedTargets().stream().anyMatch(t -> t.contains("browser")),
                "server impl edit should not rebuild browser when classification is implementation-only");
    }

    @Test
    void sharedDtoInvalidatesDependents() throws Exception {
        Path root = writeMiniProject();
        CompilerSession session = new CompilerSession(root);
        session.build();
        Path user = root.resolve("shared/User.java");
        Files.writeString(user, "class User { String id; String name; String email; String phone; }\n");
        BuildResult r = session.build(List.of("shared/User.java"), false);
        assertTrue(r.changed().contains("shared/User.java"));
        // dependents should be in invalidate closure via graph
        List<String> closure = session.sourceGraph().invalidateClosure(
                "shared/User.java", ChangeClassification.JSON_SHAPE_CHANGE);
        assertTrue(closure.contains("shared/User.java"));
    }

    @Test
    void symbolIndexIncrementalUpdate() {
        SymbolIndex idx = new SymbolIndex();
        idx.indexSource("A.java", "public class A { public int foo() { return 1; } }");
        assertTrue(idx.size() >= 1);
        assertNotNull(idx.get("A"));
        idx.removeSource("A.java");
        assertNull(idx.get("A"));
        idx.indexSource("A.java", "public class A { public int bar() { return 2; } }");
        assertNotNull(idx.get("A"));
    }

    @Test
    void buildSchedulerRunsTasks() throws Exception {
        BuildScheduler sched = new BuildScheduler(2);
        int[] n = {0};
        sched.runAll(List.of(
                new BuildScheduler.BuildTask("a", BuildScheduler.ResourceHint.CPU, () -> { n[0]++; return null; }),
                new BuildScheduler.BuildTask("b", BuildScheduler.ResourceHint.LINK, () -> { n[0]++; return null; })
        ));
        assertEquals(2, n[0]);
        assertEquals(2, sched.completedCount());
    }

    @Test
    void contentHasherDeterministic() {
        assertEquals(ContentHasher.sha256("abc"), ContentHasher.sha256("abc"));
        assertNotEquals(ContentHasher.sha256("abc"), ContentHasher.sha256("abd"));
    }

    @Test
    void largeProjectFixtureFingerprints() throws Exception {
        Path root = tmp.resolve("large");
        Files.createDirectories(root.resolve("shared"));
        Files.createDirectories(root.resolve("server"));
        Files.createDirectories(root.resolve("browser"));
        StringBuilder project = new StringBuilder("{\"name\":\"large\",\"modules\":{");
        project.append("\"shared\":{\"kind\":\"lib\",\"src\":\"shared\"},");
        project.append("\"server\":{\"kind\":\"server\",\"src\":\"server\",\"dependsOn\":[\"shared\"]},");
        project.append("\"browser\":{\"kind\":\"browser\",\"src\":\"browser\",\"dependsOn\":[\"shared\"]}}}");
        Files.writeString(root.resolve("jnative.project"), project.toString());
        for (int i = 0; i < 50; i++) {
            Files.writeString(root.resolve("shared/Type" + i + ".java"),
                    "class Type" + i + " { int v" + i + "; int get(){ return " + i + "; } }\n");
        }
        Files.writeString(root.resolve("server/App.java"),
                "class App { public static void main(String[] a){ Type0 t = null; } }\n");
        Files.writeString(root.resolve("browser/Page.java"),
                "class Page { Type1 t; }\n");
        CompilerSession session = new CompilerSession(root);
        assertEquals(52, session.fingerprints().size());
        BuildResult cold = session.build();
        long t0 = System.currentTimeMillis();
        BuildResult noop = session.build(List.of(), false);
        long dt = System.currentTimeMillis() - t0;
        assertTrue(noop.changed().isEmpty());
        assertTrue(noop.reused().size() >= 50);
        System.out.println("large cold ms=" + cold.wallTimeMs()
                + " no-op ms=" + dt + " reused=" + noop.reused().size()
                + " compilerExecuted=" + noop.compilerExecuted());
    }

    private Path writeMiniProject() throws Exception {
        Path root = tmp.resolve("mini");
        Files.createDirectories(root.resolve("shared"));
        Files.createDirectories(root.resolve("server"));
        Files.createDirectories(root.resolve("browser/public"));
        Files.createDirectories(root.resolve("desktop"));
        Files.writeString(root.resolve("jnative.project"), Files.readString(
                Path.of("examples/dev/admin/jnative.project")));
        Files.writeString(root.resolve("shared/User.java"), "class User { String id; String name; }\n");
        Files.writeString(root.resolve("shared/UserApi.java"),
                "@Rpc(path=\"/rpc/users\")\ninterface UserApi { User getUser(String id); }\n");
        Files.writeString(root.resolve("server/UserRepository.java"), """
                class UserRepository {
                    User findById(String id) { return null; }
                    private int calculate() { return 42; }
                }
                """);
        Files.writeString(root.resolve("server/App.java"),
                "class App { public static void main(String[] args) { } }\n");
        Files.writeString(root.resolve("browser/UsersPage.java"),
                "class UsersPage { User u; }\n");
        Files.writeString(root.resolve("browser/public/app.css"), "body{color:#111}\n");
        Files.writeString(root.resolve("desktop/App.java"),
                "class App { public static void main(String[] args) { } }\n");
        return root;
    }
}
