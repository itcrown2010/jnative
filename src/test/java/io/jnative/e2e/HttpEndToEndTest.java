package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class HttpEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void getHello() throws Exception {
        int port = freePort();
        Path fixture = compileC("http_fixture.c", "http_fx");
        Process server = startReady(fixture, String.valueOf(port), "hello");
        try {
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            HttpResponse response = HttpClient.get("http://127.0.0.1:%d/hello");
                            System.out.println(response.statusCode());
                            System.out.println(response.bodyString());
                        }
                    }
                    """.formatted(port), "200\nHello JNative\n");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void chunkedBody() throws Exception {
        int port = freePort();
        Path fixture = compileC("http_fixture.c", "http_chunk");
        Process server = startReady(fixture, String.valueOf(port), "chunked");
        try {
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            HttpResponse response = HttpClient.get("http://127.0.0.1:%d/");
                            System.out.println(response.bodyString());
                        }
                    }
                    """.formatted(port), "Hello JNative\n");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void binaryBody() throws Exception {
        int port = freePort();
        Path fixture = compileC("http_fixture.c", "http_bin");
        Process server = startReady(fixture, String.valueOf(port), "binary");
        try {
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            HttpResponse response = HttpClient.get("http://127.0.0.1:%d/");
                            byte[] b = response.bodyBytes();
                            int ok = 1;
                            if (b.length != 5) { ok = 0; }
                            if (b[0] != 0) { ok = 0; }
                            if (b[1] != -1) { ok = 0; }
                            if (b[2] != 1) { ok = 0; }
                            if (b[3] != 0) { ok = 0; }
                            if (b[4] != 127) { ok = 0; }
                            System.out.println(ok);
                        }
                    }
                    """.formatted(port), "1\n");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void postEcho() throws Exception {
        int port = freePort();
        Path fixture = compileC("http_fixture.c", "http_post");
        Process server = startReady(fixture, String.valueOf(port), "post");
        try {
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            HttpRequest request = HttpRequest.create("POST", "http://127.0.0.1:%d/api");
                            request.header("Content-Type", "application/json");
                            request.bodyString("{\\"name\\":\\"JNative\\"}");
                            HttpResponse response = HttpClient.send(request);
                            System.out.println(response.bodyString());
                        }
                    }
                    """.formatted(port), "{\"name\":\"JNative\"}\n");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void redirect() throws Exception {
        int port = freePort();
        Path fixture = compileC("http_fixture.c", "http_redir");
        Process server = startReady(fixture, String.valueOf(port), "redirect");
        try {
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            HttpResponse response = HttpClient.get("http://127.0.0.1:%d/start");
                            System.out.println(response.statusCode());
                            System.out.println(response.bodyString());
                        }
                    }
                    """.formatted(port), "200\nHello JNative\n");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void oneMegabyteBody() throws Exception {
        int port = freePort();
        Path fixture = compileC("http_fixture.c", "http_large");
        Process server = startReady(fixture, String.valueOf(port), "large");
        try {
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            HttpResponse response = HttpClient.get("http://127.0.0.1:%d/");
                            byte[] b = response.bodyBytes();
                            int ok = 1;
                            if (b.length != 1048576) { ok = 0; }
                            if (b[0] != 65) { ok = 0; }
                            if (b[1048575] != 65) { ok = 0; }
                            System.out.println(ok);
                        }
                    }
                    """.formatted(port), "1\n");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void jnativeServerCurl() throws Exception {
        Assumptions.assumeTrue(commandExists("curl"), "curl not available");
        int port = freePort();
        Path serverBin = compileJn("""
                public class Server {
                    public static void main(String[] args) {
                        HttpServer server = HttpServer.listen("127.0.0.1", %d);
                        HttpConnection connection = server.accept();
                        HttpServerRequest request = connection.readRequest();
                        connection.send(HttpResponse.textStatus(200, "Hello JNative"));
                        connection.close();
                        server.close();
                    }
                }
                """.formatted(port), "HttpSrv");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            String out = null;
            int code = -1;
            for (int attempt = 0; attempt < 20; attempt++) {
                Thread.sleep(100);
                Process curl = new ProcessBuilder(
                        "curl", "-s", "--connect-timeout", "1",
                        "http://127.0.0.1:" + port + "/hello")
                        .redirectErrorStream(true)
                        .start();
                out = new String(curl.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertTrue(curl.waitFor(30, TimeUnit.SECONDS));
                code = curl.exitValue();
                if (code == 0) {
                    break;
                }
            }
            assertEquals(0, code, out);
            assertEquals("Hello JNative", out);
            assertTrue(server.waitFor(30, TimeUnit.SECONDS));
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void fileHttpIntegration() throws Exception {
        int port = freePort();
        Path fixture = compileC("http_fixture.c", "http_file");
        Process server = startReady(fixture, String.valueOf(port), "file");
        try {
            Path out = tempDir.resolve("download.bin");
            assertStdout(cwd(tempDir), """
                    public class Main {
                        public static void main(String[] args) {
                            HttpResponse response = HttpClient.get("http://127.0.0.1:%d/file.bin");
                            File.writeBytes("download.bin", response.bodyBytes());
                            System.out.println(response.statusCode());
                        }
                    }
                    """.formatted(port), "200\n");
            byte[] got = Files.readAllBytes(out);
            assertArrayEquals(new byte[]{0, (byte) 0xFF, 1, 0, 0x7F}, got);
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void gcStressHttp() throws Exception {
        int port = freePort();
        Path fixture = compileC("http_fixture.c", "http_gc");
        // fixture handles one connection — use JNative server loop instead
        Path serverBin = compileJn("""
                public class Server {
                    public static void main(String[] args) {
                        HttpServer server = HttpServer.listen("127.0.0.1", %d);
                        int i = 0;
                        while (i < 30) {
                            HttpConnection c = server.accept();
                            HttpServerRequest req = c.readRequest();
                            c.send(HttpResponse.text("ok"));
                            c.close();
                            i = i + 1;
                        }
                        server.close();
                    }
                }
                """.formatted(port), "HttpGcSrv");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            Thread.sleep(300);
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            int i = 0;
                            while (i < 30) {
                                HttpResponse r = HttpClient.get("http://127.0.0.1:%d/");
                                String s = r.bodyString();
                                i = i + 1;
                            }
                            System.out.println(1);
                        }
                    }
                    """.formatted(port), "1\n", Map.of(
                            "JNATIVE_GC", "marksweep",
                            "JNATIVE_GC_STRESS", "1"));
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void urlAndHeadersUnit() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        HttpUrl u = HttpUrl.parse("http://example.com:8080/api/users?id=1");
                        System.out.println(u.scheme);
                        System.out.println(u.host);
                        System.out.println(u.port);
                        System.out.println(u.path);
                        System.out.println(u.query);
                        HttpHeaders h = new HttpHeaders();
                        h.add("Content-Type", "text/plain");
                        h.add("CONTENT-TYPE", "application/json");
                        System.out.println(h.get("content-type"));
                        System.out.println(h.count);
                    }
                }
                """, """
                http
                example.com
                8080
                /api/users
                id=1
                text/plain
                2
                """);
    }

    @Test
    void unsupportedHttps() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        try {
                            HttpClient.get("https://example.com/");
                            System.out.println("ok");
                        } catch (UnsupportedSchemeException e) {
                            System.out.println("https");
                        }
                    }
                }
                """, "https\n");
    }

    @Test
    void malformedResponse() throws Exception {
        int port = freePort();
        // Reuse TCP echo that returns non-HTTP
        Path echo = compileCFixture("tcp_echo_server.c", "badhttp");
        Process server = startReady(echo, String.valueOf(port), "hello");
        try {
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            try {
                                HttpResponse r = HttpClient.get("http://127.0.0.1:%d/");
                                System.out.println(r.statusCode());
                            } catch (HttpProtocolException e) {
                                System.out.println("bad");
                            }
                        }
                    }
                    """.formatted(port), "bad\n");
        } finally {
            server.destroyForcibly();
        }
    }

    private Path compileCFixture(String name, String out) throws Exception {
        return compileC(name, out);
    }

    private static Path cwd(Path dir) {
        return dir;
    }

    private static int freePort() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static boolean commandExists(String cmd) {
        try {
            Process p = new ProcessBuilder("which", cmd).start();
            return p.waitFor(5, TimeUnit.SECONDS) && p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private Path compileC(String resourceName, String outName) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path src = tempDir.resolve(outName + ".c");
        try (var in = getClass().getClassLoader()
                .getResourceAsStream("native_fixtures/" + resourceName)) {
            assertNotNull(in, resourceName);
            Files.copy(in, src);
        }
        Path out = tempDir.resolve(outName);
        Process p = new ProcessBuilder(
                "clang", src.toAbsolutePath().toString(),
                "-o", out.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), log);
        return out;
    }

    private Process startReady(Path bin, String... args) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(bin.toAbsolutePath().toString());
        for (String a : args) {
            cmd.add(a);
        }
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
        String line = br.readLine();
        assertEquals("READY", line, "server did not become ready");
        return p;
    }

    private void assertStdout(String source, String expected) throws Exception {
        assertStdout(source, expected, Map.of());
    }

    private void assertStdout(String source, String expected, Map<String, String> env) throws Exception {
        assertStdout(tempDir, source, expected, env);
    }

    private void assertStdout(Path workDir, String source, String expected) throws Exception {
        assertStdout(workDir, source, expected, Map.of());
    }

    private void assertStdout(Path workDir, String source, String expected, Map<String, String> env)
            throws Exception {
        Path out = compileJn(source, "HttpProg");
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.directory(workDir.toFile());
        pb.environment().putAll(env);
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "timeout");
        assertEquals(0, process.exitValue(), stdout);
        assertEquals(expected, stdout);
    }

    private Path compileJn(String source, String name) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name + "Bin");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        return out;
    }
}
