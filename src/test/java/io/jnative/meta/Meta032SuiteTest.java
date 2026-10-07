package io.jnative.meta;

import io.jnative.Main;
import io.jnative.ast.CompilationUnit;
import io.jnative.build.BuildReport032;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Lexer;
import io.jnative.parser.Parser;
import io.jnative.pkg.ManifestFile;
import io.jnative.pkg.PackageManifest;
import io.jnative.processor.AnnotationProcessor;
import io.jnative.processor.GeneratedArtifact;
import io.jnative.processor.ProcessorKind;
import io.jnative.processor.ProcessorPermission;
import io.jnative.processor.ProcessorRegistry;
import io.jnative.processor.ProcessorScheduler;
import io.jnative.processor.ProcessingContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class Meta032SuiteTest {

    @TempDir
    Path temp;

    @Test
    void versionIs032() {
        assertEquals("0.32", Main.VERSION);
    }

    @Test
    void metaApiVersionAndIntrinsics() {
        assertEquals(1, MetaApiVersion.CURRENT);
        assertEquals("Metaprogramming LLVM intrinsics: NONE", BuildReport032.META_LLVM_INTRINSICS);
        assertEquals("Annotation Processing LLVM intrinsics: NONE", BuildReport032.ANNOTATION_LLVM_INTRINSICS);
        assertEquals("Compile-Time DI LLVM intrinsics: NONE", BuildReport032.DI_LLVM_INTRINSICS);
    }

    @Test
    void annotationParsingRetentionAndTargets() {
        CompilationUnit cu = parse("""
                @Json
                class User {
                    @JsonField("user_id")
                    int id;
                    String name;
                }
                """);
        assertTrue(cu.typeAnnotations().containsKey("User"));
        assertEquals("Json", cu.typeAnnotations().get("User").get(0).name());
        assertTrue(cu.fieldAnnotations().containsKey("User.id"));
        assertEquals("JsonField", cu.fieldAnnotations().get("User.id").get(0).name());
        assertEquals("user_id", cu.fieldAnnotations().get("User.id").get(0).stringValue().orElse(""));
    }

    @Test
    void programModelBuilderAndQueries() {
        CompilationUnit cu = parse("""
                @Json
                class User {
                    int id;
                    String name;
                }
                @Validated
                class CreateUserRequest {
                    @NotBlank
                    String name;
                    @Min(18)
                    int age;
                }
                """);
        ProgramModel model = ProgramModelBuilder.build(cu, "native");
        assertEquals(2, model.findTypesAnnotatedWith("Json").size()
                + model.findTypesAnnotatedWith("Validated").size() - 0);
        assertFalse(model.findTypesAnnotatedWith("Json").isEmpty());
        assertFalse(model.findTypesAnnotatedWith("Validated").isEmpty());
        ClassModel user = model.findType("User");
        assertNotNull(user);
        assertEquals(2, user.fields().size());
        assertTrue(user.fields().get(0).type().simpleName().equals("int")
                || user.fields().get(1).type().simpleName().equals("int"));
    }

    @Test
    void processorDiscoveryAndKinds() {
        List<AnnotationProcessor> procs = ProcessorRegistry.builtin();
        assertTrue(procs.stream().anyMatch(p -> p.name().equals("JsonProcessor")));
        assertTrue(procs.stream().anyMatch(p -> p.name().equals("RpcProcessor")));
        assertTrue(procs.stream().anyMatch(p -> p.name().equals("DiProcessor")));
        assertEquals(ProcessorKind.ISOLATING,
                procs.stream().filter(p -> p.name().equals("JsonProcessor")).findFirst().orElseThrow().kind());
        assertEquals(ProcessorKind.AGGREGATING,
                procs.stream().filter(p -> p.name().equals("CliProcessor")).findFirst().orElseThrow().kind());
    }

    @Test
    void jsonProcessorGeneratesSourceResourceMetadata(@TempDir Path gen) throws Exception {
        CompilationUnit cu = parse("""
                @Json
                class User {
                    int id;
                    String name;
                }
                """);
        DiagnosticReporter reporter = new DiagnosticReporter();
        ProcessorScheduler scheduler = new ProcessorScheduler(
                ProcessorRegistry.builtin(), gen, true, 10_000, Map.of());
        ProcessorScheduler.Result result = scheduler.run(cu, "native", reporter);
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        assertTrue(result.artifacts().stream()
                .anyMatch(a -> a.relativePath().contains("UserJsonCodec")));
        assertTrue(result.artifacts().stream()
                .anyMatch(a -> a.kind() == GeneratedArtifact.Kind.METADATA));
        assertTrue(Files.isRegularFile(gen.resolve("json/UserJsonCodec.java")));
        assertTrue(Files.isRegularFile(gen.resolve("generated-index.tsv")));
        String codec = Files.readString(gen.resolve("json/UserJsonCodec.java"));
        assertTrue(codec.contains("generated by: JsonProcessor"));
        assertTrue(codec.contains("origin:"));
        assertTrue(codec.contains("implements JsonCodec"));
    }

    @Test
    void isolatingDtoChangeDoesNotRequireRerunOfUnrelatedLogic() {
        // Fingerprints differ per type — Product change does not share User fingerprint.
        CompilationUnit user = parse("@Json class User { int id; }");
        CompilationUnit product = parse("@Json class Product { int sku; }");
        ProgramModel mu = ProgramModelBuilder.build(user, "native");
        ProgramModel mp = ProgramModelBuilder.build(product, "native");
        assertNotEquals(mu.findType("User").simpleName(), mp.findType("Product").simpleName());
        assertTrue(mu.findTypesAnnotatedWith("Json").get(0).fields().get(0).name().equals("id"));
        assertTrue(mp.findTypesAnnotatedWith("Json").get(0).fields().get(0).name().equals("sku"));
    }

    @Test
    void diCycleDetected() {
        CompilationUnit cu = parse("""
                @Service
                class A {
                    @Inject
                    B b;
                }
                @Service
                class B {
                    @Inject
                    A a;
                }
                """);
        DiagnosticReporter reporter = new DiagnosticReporter();
        ProcessorScheduler scheduler = new ProcessorScheduler(
                ProcessorRegistry.builtin(), temp.resolve("gen"), true, 10_000, Map.of());
        scheduler.run(cu, "native", reporter);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.message().contains("JN-META-DI-CYCLE")));
    }

    @Test
    void permissionDeniedForNetwork() {
        AnnotationProcessor evil = new AnnotationProcessor() {
            @Override
            public String name() {
                return "Evil";
            }

            @Override
            public String version() {
                return "0";
            }

            @Override
            public Set<TypeId> supportedAnnotations() {
                return Set.of(TypeId.of("Json"));
            }

            @Override
            public void process(ProcessingContext context) {
                context.checkPermission(ProcessorPermission.NETWORK);
            }
        };
        CompilationUnit cu = parse("@Json class U { int x; }");
        DiagnosticReporter reporter = new DiagnosticReporter();
        ProcessorScheduler scheduler = new ProcessorScheduler(
                List.of(evil), temp.resolve("gen2"), true, 5_000, Map.of());
        scheduler.run(cu, "native", reporter);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.message().contains("denied permission")));
    }

    @Test
    void processorCrashIsolated() {
        AnnotationProcessor boom = new AnnotationProcessor() {
            @Override
            public String name() {
                return "Boom";
            }

            @Override
            public String version() {
                return "0";
            }

            @Override
            public Set<TypeId> supportedAnnotations() {
                return Set.of(TypeId.of("Json"));
            }

            @Override
            public void process(ProcessingContext context) {
                throw new RuntimeException("kaboom");
            }
        };
        CompilationUnit cu = parse("@Json class U { int x; }");
        DiagnosticReporter reporter = new DiagnosticReporter();
        ProcessorScheduler scheduler = new ProcessorScheduler(
                List.of(boom), temp.resolve("gen3"), true, 5_000, Map.of());
        scheduler.run(cu, "native", reporter);
        assertTrue(reporter.hasErrors());
        assertTrue(reporter.diagnostics().stream()
                .anyMatch(d -> d.message().contains("crashed")));
    }

    @Test
    void determinismSameOutputHash() {
        CompilationUnit cu = parse("@Json class User { int id; String name; }");
        DiagnosticReporter r1 = new DiagnosticReporter();
        DiagnosticReporter r2 = new DiagnosticReporter();
        Path g1 = temp.resolve("d1");
        Path g2 = temp.resolve("d2");
        var a1 = new ProcessorScheduler(ProcessorRegistry.builtin(), g1, true, 10_000, Map.of())
                .run(cu, "native", r1).artifacts();
        var a2 = new ProcessorScheduler(ProcessorRegistry.builtin(), g2, true, 10_000, Map.of())
                .run(cu, "native", r2).artifacts();
        String h1 = a1.stream().filter(a -> a.relativePath().contains("UserJsonCodec"))
                .findFirst().orElseThrow().outputHash();
        String h2 = a2.stream().filter(a -> a.relativePath().contains("UserJsonCodec"))
                .findFirst().orElseThrow().outputHash();
        assertEquals(h1, h2);
    }

    @Test
    void processorPackageManifest() throws Exception {
        Path root = temp.resolve("json-processor");
        Files.createDirectories(root);
        Files.writeString(root.resolve("jnative.toml"), """
                [package]
                name = "json-processor"
                namespace = "jnative"
                version = "0.32.0"
                type = "processor"
                processor = true

                [processor]
                entry = "io.jnative.processor.builtin.JsonProcessor"
                min_api = 1
                max_api = 1
                """);
        PackageManifest m = ManifestFile.discover(root);
        assertTrue(m.processor());
        assertEquals("io.jnative.processor.builtin.JsonProcessor", m.processorEntry());
        assertEquals(1, m.minProcessorApi());
        MetaApiVersion.checkCompatible(m.minProcessorApi(), m.maxProcessorApi());
    }

    @Test
    void incompatibleApiRejected() {
        assertThrows(IncompatibleProcessorApiException.class,
                () -> MetaApiVersion.checkCompatible(99, 99));
    }

    @Test
    void validationConfigCliEntityRouteGenerate(@TempDir Path gen) {
        CompilationUnit cu = parse("""
                @Validated
                class CreateUserRequest {
                    @NotBlank
                    String name;
                    @Min(18)
                    int age;
                }
                @Config("database")
                class DatabaseConfig {
                    String host;
                    @Default("5432")
                    int port;
                }
                @Command("backup")
                class BackupCommand {
                    @Option("--output")
                    String output;
                }
                @Entity("users")
                class UserEnt {
                    @Id
                    long id;
                    String name;
                }
                @Controller("/users")
                class UserController {
                    @Get("/:id")
                    void get() {
                    }
                }
                """);
        DiagnosticReporter reporter = new DiagnosticReporter();
        ProcessorScheduler.Result result = new ProcessorScheduler(
                ProcessorRegistry.builtin(), gen, true, 10_000, Map.of())
                .run(cu, "native", reporter);
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        assertTrue(result.artifacts().stream().anyMatch(a -> a.relativePath().contains("Validator")));
        assertTrue(result.artifacts().stream().anyMatch(a -> a.relativePath().contains("ConfigParser")));
        assertTrue(result.artifacts().stream().anyMatch(a -> a.relativePath().contains("CliParser")));
        assertTrue(result.artifacts().stream().anyMatch(a -> a.relativePath().contains("RowMapper")));
        assertTrue(result.artifacts().stream().anyMatch(a -> a.relativePath().contains("GeneratedRouteTable")));
    }

    @Test
    void isolatingReuseSkipsUnrelatedDtoRegeneration(@TempDir Path gen) {
        CompilationUnit both = parse("""
                @Json class User { int id; }
                @Json class Product { int sku; }
                """);
        DiagnosticReporter r1 = new DiagnosticReporter();
        ProcessorScheduler.Result first = new ProcessorScheduler(
                ProcessorRegistry.builtin(), gen, true, 10_000, Map.of())
                .run(both, "native", r1);
        assertFalse(r1.hasErrors(), () -> r1.diagnostics().toString());
        assertTrue(first.explain().stream().anyMatch(l -> l.contains("GENERATE") && l.contains("User")));
        assertTrue(first.explain().stream().anyMatch(l -> l.contains("GENERATE") && l.contains("Product")));

        // Warm rebuild: same sources → REUSE both
        DiagnosticReporter r2 = new DiagnosticReporter();
        ProcessorScheduler.Result warm = new ProcessorScheduler(
                ProcessorRegistry.builtin(), gen, true, 10_000, Map.of())
                .run(both, "native", r2);
        assertFalse(r2.hasErrors());
        assertTrue(warm.explain().stream().anyMatch(l -> l.contains("REUSE") && l.contains("User")));
        assertTrue(warm.explain().stream().anyMatch(l -> l.contains("REUSE") && l.contains("Product")));

        // Edit only Product → User should REUSE, Product GENERATE
        CompilationUnit edited = parse("""
                @Json class User { int id; }
                @Json class Product { int sku; String title; }
                """);
        DiagnosticReporter r3 = new DiagnosticReporter();
        ProcessorScheduler.Result delta = new ProcessorScheduler(
                ProcessorRegistry.builtin(), gen, true, 10_000, Map.of())
                .run(edited, "native", r3);
        assertFalse(r3.hasErrors(), () -> r3.diagnostics().toString());
        assertTrue(delta.explain().stream().anyMatch(l -> l.contains("REUSE") && l.contains("User")));
        assertTrue(delta.explain().stream().anyMatch(l -> l.contains("GENERATE") && l.contains("Product")));
    }

    @Test
    void generatedSymbolIndexGoToOriginAndGenerated(@TempDir Path gen) throws Exception {
        CompilationUnit cu = parse("@Json class User { int id; }");
        new ProcessorScheduler(ProcessorRegistry.builtin(), gen, true, 10_000, Map.of())
                .run(cu, "native", new DiagnosticReporter());
        GeneratedSymbolIndex idx = GeneratedSymbolIndex.load(gen);
        assertFalse(idx.generatedFrom("User").isEmpty());
        assertFalse(idx.originOf("UserJsonCodec").isEmpty());
        assertEquals("User", idx.originOf("UserJsonCodec").get(0).originSymbol());
    }

    @Test
    void multiProcessorRoundGeneratesValidatorsAndCodecs(@TempDir Path gen) {
        CompilationUnit cu = parse("""
                @Json
                @Validated
                class CreateUserRequest {
                    @NotBlank
                    String name;
                    @Min(18)
                    int age;
                }
                """);
        DiagnosticReporter reporter = new DiagnosticReporter();
        ProcessorScheduler.Result result = new ProcessorScheduler(
                ProcessorRegistry.builtin(), gen, true, 10_000, Map.of())
                .run(cu, "native", reporter);
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        assertTrue(result.artifacts().stream().anyMatch(a -> a.relativePath().contains("JsonCodec")));
        assertTrue(result.artifacts().stream().anyMatch(a -> a.relativePath().contains("Validator")));
    }

    private static CompilationUnit parse(String src) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        Lexer lexer = new Lexer(src, "T.java");
        Parser parser = new Parser(lexer.tokenize(), reporter);
        CompilationUnit cu = parser.parse("T.java");
        assertFalse(reporter.hasErrors(), () -> reporter.diagnostics().toString());
        return cu;
    }
}
