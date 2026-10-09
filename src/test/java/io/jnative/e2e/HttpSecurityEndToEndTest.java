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
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class HttpSecurityEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void crlfHeaderRejected() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        byte[] crlf = new byte[2];
                        crlf[0] = 13;
                        crlf[1] = 10;
                        String bad = "a" + Bytes.toString(crlf) + "Injected: 1";
                        int ok = 0;
                        try {
                            HttpRequest r = HttpRequest.create("GET", "http://127.0.0.1/");
                            r.header("X-Foo", bad);
                        } catch (HttpProtocolException e) {
                            ok = 1;
                        }
                        System.out.println(ok);
                    }
                }
                """, "1\n");
    }

    @Test
    void crossOriginRedirectStripsAuthorization() throws Exception {
        int portA = freePort();
        int portB = freePort();
        Path fixture = compileC("http_redirect_auth_fixture.c", "redir_auth");
        Process server = new ProcessBuilder(
                fixture.toAbsolutePath().toString(),
                String.valueOf(portA),
                String.valueOf(portB))
                .redirectErrorStream(true)
                .start();
        try {
            waitReady(server);
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            HttpRequest r = HttpRequest.create("GET", "http://127.0.0.1:%d/start");
                            r.header("Authorization", "Bearer SECRET");
                            r.header("X-Keep", "yes");
                            HttpResponse resp = HttpClient.send(r);
                            System.out.println(resp.statusCode());
                            System.out.println(resp.bodyString());
                        }
                    }
                    """.formatted(portA), "200\nno-auth keep=yes\n");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void staticPathTraversalRejected() throws Exception {
        int port = freePort();
        Path root = tempDir.resolve("www");
        Files.createDirectories(root);
        Files.writeString(root.resolve("ok.txt"), "safe", StandardCharsets.UTF_8);
        Path ready = tempDir.resolve("static-ready");
        Path serverBin = compileJn("""
                class OkHandler implements WebHandler {
                    public void handle(WebContext ctx) {
                        ctx.text("hi");
                    }
                }
                public class Server {
                    public static void main(String[] args) {
                        WebApp app = new WebApp();
                        app.get("/", new OkHandler());
                        app.staticFiles("/static", "%s");
                        File.writeString("%s", "1");
                        app.listen(%d);
                    }
                }
                """.formatted(esc(root.toString()), esc(ready.toString()), port), "StaticSrv");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            waitReadyFile(ready, server);
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            int ok = 0;
                            try {
                                HttpResponse r = HttpClient.get("http://127.0.0.1:%d/static/../../ok.txt");
                                if (r.statusCode() >= 400) {
                                    ok = 1;
                                }
                            } catch (HttpException e) {
                                ok = 1;
                            } catch (IOException e) {
                                ok = 1;
                            }
                            System.out.println(ok);
                        }
                    }
                    """.formatted(port), "1\n");
        } finally {
            server.destroyForcibly();
        }
    }

    private Path compileC(String name, String outName) throws Exception {
        Path src = Path.of("src/test/resources/native_fixtures").resolve(name).toAbsolutePath();
        if (!Files.exists(src)) {
            // write fixture if missing
            Files.createDirectories(src.getParent());
            Files.writeString(src, REDIR_FIXTURE, StandardCharsets.UTF_8);
        }
        Path out = tempDir.resolve(outName);
        Process p = new ProcessBuilder("clang", "-O2", src.toString(), "-o", out.toString())
                .redirectErrorStream(true)
                .start();
        String log = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(p.waitFor(60, TimeUnit.SECONDS));
        assertEquals(0, p.exitValue(), log);
        return out;
    }

    private static final String REDIR_FIXTURE = """
            #include <arpa/inet.h>
            #include <netinet/in.h>
            #include <stdio.h>
            #include <stdlib.h>
            #include <string.h>
            #include <sys/socket.h>
            #include <unistd.h>

            static int listen_port(int port) {
                int fd = socket(AF_INET, SOCK_STREAM, 0);
                int one = 1;
                setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &one, sizeof(one));
                struct sockaddr_in addr;
                memset(&addr, 0, sizeof(addr));
                addr.sin_family = AF_INET;
                addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);
                addr.sin_port = htons((uint16_t)port);
                bind(fd, (struct sockaddr *)&addr, sizeof(addr));
                listen(fd, 16);
                return fd;
            }

            static void serve_once(int lfd, const char *resp) {
                int c = accept(lfd, NULL, NULL);
                char buf[4096];
                recv(c, buf, sizeof(buf) - 1, 0);
                send(c, resp, strlen(resp), 0);
                close(c);
            }

            int main(int argc, char **argv) {
                int portA = atoi(argv[1]);
                int portB = atoi(argv[2]);
                int a = listen_port(portA);
                int b = listen_port(portB);
                printf("ready\\n");
                fflush(stdout);
                char loc[128];
                snprintf(loc, sizeof(loc),
                    "HTTP/1.1 302 Found\\r\\nLocation: http://127.0.0.1:%d/target\\r\\nConnection: close\\r\\nContent-Length: 0\\r\\n\\r\\n",
                    portB);
                serve_once(a, loc);
                int c = accept(b, NULL, NULL);
                char buf[4096];
                memset(buf, 0, sizeof(buf));
                recv(c, buf, sizeof(buf) - 1, 0);
                int hasAuth = strstr(buf, "Authorization:") != NULL;
                int hasKeep = strstr(buf, "X-Keep: yes") != NULL;
                char body[64];
                if (hasAuth) {
                    snprintf(body, sizeof(body), "has-auth");
                } else if (hasKeep) {
                    snprintf(body, sizeof(body), "no-auth keep=yes");
                } else {
                    snprintf(body, sizeof(body), "no-auth");
                }
                char resp[256];
                snprintf(resp, sizeof(resp),
                    "HTTP/1.1 200 OK\\r\\nContent-Length: %zu\\r\\nConnection: close\\r\\n\\r\\n%s",
                    strlen(body), body);
                send(c, resp, strlen(resp), 0);
                close(c);
                close(a);
                close(b);
                return 0;
            }
            """;

    private void waitReady(Process server) throws Exception {
        long deadline = System.currentTimeMillis() + 15000;
        BufferedReader br = new BufferedReader(new InputStreamReader(server.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder acc = new StringBuilder();
        while (System.currentTimeMillis() < deadline) {
            if (br.ready()) {
                String line = br.readLine();
                if (line != null) {
                    acc.append(line).append('\n');
                    if (line.contains("ready")) {
                        return;
                    }
                }
            }
            if (!server.isAlive()) {
                fail("server died: " + acc);
            }
            Thread.sleep(20);
        }
        fail("not ready: " + acc);
    }

    private void waitReadyFile(Path ready, Process server) throws Exception {
        long deadline = System.currentTimeMillis() + 20000;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(ready)) {
                return;
            }
            if (!server.isAlive()) {
                fail("server died");
            }
            Thread.sleep(20);
        }
        fail("ready flag missing");
    }

    private void assertStdout(String source, String expected) throws Exception {
        Path bin = compileJn(source, "HttpSec");
        ProcessBuilder pb = new ProcessBuilder(bin.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(120, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue(), stdout);
        assertEquals(expected, stdout);
    }

    private Path compileJn(String source, String name) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent());
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

    private static String esc(String path) {
        return path.replace("\\", "\\\\");
    }

    private static int freePort() throws Exception {
        try (ServerSocket s = new ServerSocket(0)) {
            return s.getLocalPort();
        }
    }
}
