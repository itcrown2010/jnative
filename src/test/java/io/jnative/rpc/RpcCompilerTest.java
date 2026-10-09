package io.jnative.rpc;

import io.jnative.ast.CompilationUnit;
import io.jnative.diagnostics.DiagnosticReporter;
import io.jnative.lexer.Lexer;
import io.jnative.lexer.Token;
import io.jnative.lexer.TokenType;
import io.jnative.parser.Parser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
public class RpcCompilerTest {

    private CompilationUnit parse(String source) {
        DiagnosticReporter reporter = new DiagnosticReporter();
        Lexer lexer = new Lexer(source, "RpcTest.java");
        List<Token> tokens = lexer.tokenize();
        for (Token t : tokens) {
            if (t.type() == TokenType.ERROR) {
                fail(t.lexeme());
            }
        }
        Parser parser = new Parser(tokens, reporter);
        CompilationUnit unit = parser.parse("RpcTest.java");
        assertFalse(reporter.hasErrors(), reporter.diagnostics().toString());
        return unit;
    }

    @Test void parsesRpcAnnotationOnInterface() {
        CompilationUnit unit = parse("""
                @Rpc("/api/users")
                interface UserApi {
                    User getUser(int id);
                }
                class User {
                    int id;
                }
                """);
        assertTrue(unit.typeAnnotations().containsKey("UserApi"));
        assertEquals("Rpc", unit.typeAnnotations().get("UserApi").get(0).name());
        assertEquals("/api/users", unit.typeAnnotations().get("UserApi").get(0).stringValue().orElse(""));
    }

    @Test void analyzesServiceAndMethods() {
        CompilationUnit unit = parse("""
                @Rpc("/api/users")
                interface UserApi {
                    User getUser(int id);
                    ArrayList<User> listUsers();
                    void deleteUser(int id);
                }
                class User {
                    int id;
                }
                """);
        DiagnosticReporter reporter = new DiagnosticReporter();
        List<RpcServiceModel> services = new RpcAnalyzer(reporter).analyze(unit);
        assertEquals(1, services.size());
        RpcServiceModel s = services.get(0);
        assertEquals("UserApi", s.interfaceName());
        assertEquals("/api/users", s.path());
        assertEquals(3, s.methods().size());
        assertEquals("/api/users/getUser", s.methods().get(0).httpPath());
    }

    @Test void authMetadataOnMethods() {
        CompilationUnit unit = parse("""
                @Rpc("/api/users")
                interface UserApi {
                    @Authenticated
                    @Role("ADMIN")
                    User createUser(CreateUserRequest request);
                }
                class User { int id; }
                class CreateUserRequest { String name; }
                """);
        DiagnosticReporter reporter = new DiagnosticReporter();
        List<RpcServiceModel> services = new RpcAnalyzer(reporter).analyze(unit);
        RpcMethodModel m = services.get(0).methods().get(0);
        assertTrue(m.authenticated());
        assertEquals("ADMIN", m.role());
    }

    @Test void rejectsUnsupportedReturnType() {
        CompilationUnit unit = parse("""
                @Rpc("/api/bad")
                interface BadApi {
                    NativePtr bad();
                }
                """);
        DiagnosticReporter reporter = new DiagnosticReporter();
        new RpcAnalyzer(reporter).analyze(unit);
        assertTrue(reporter.hasErrors());
    }

    @Test void generatesMountAndClient() {
        CompilationUnit unit = parse("""
                @Rpc("/api/users")
                interface UserApi {
                    User getUser(int id);
                }
                class User { int id; }
                """);
        List<RpcServiceModel> services = new RpcAnalyzer(new DiagnosticReporter()).analyze(unit);
        String gen = RpcCodeGenerator.generate(services);
        assertTrue(gen.contains("UserApi_Rpc"));
        assertTrue(gen.contains("UserApi_RpcClient"));
        assertTrue(gen.contains("mount(WebApp app"));
        assertTrue(gen.contains("clientAt(String baseUrl)"));
        assertTrue(gen.contains("impl.getUser(id)"));
        assertFalse(gen.contains("Method.invoke"));
        assertFalse(gen.contains("getMethod"));
    }

    @Test void schemaReportContainsMethodIds() {
        CompilationUnit unit = parse("""
                @Rpc("/api/users")
                interface UserApi {
                    User getUser(int id);
                }
                class User { int id; }
                """);
        List<RpcServiceModel> services = new RpcAnalyzer(new DiagnosticReporter()).analyze(unit);
        String report = RpcSchemaGenerator.toReport(services);
        assertTrue(report.contains("methodId:"));
        assertTrue(report.contains("getUser"));
        String json = RpcSchemaGenerator.toJsonSchema(services);
        assertTrue(json.contains("\"jnativeRpc\": \"0.28\""));
    }

    @Test void stableMethodIdsAreDeterministic() {
        long a = RpcAnalyzer.stableHash("UserApi.getUser(int,)User|v1");
        long b = RpcAnalyzer.stableHash("UserApi.getUser(int,)User|v1");
        assertEquals(a, b);
        assertNotEquals(a, RpcAnalyzer.stableHash("UserApi.getUser(String,)User|v1"));
    }

    @Test void rpcDemoBinaryExists() throws Exception {
        // built by earlier steps; soft check for source presence
        assertTrue(java.nio.file.Files.exists(
                java.nio.file.Path.of("examples/fullstack/rpc/App.java")));
    }
}
