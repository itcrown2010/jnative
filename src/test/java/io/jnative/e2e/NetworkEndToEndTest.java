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

class NetworkEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void clientTalksToNativeEchoServer() throws Exception {
        int port = freePort();
        Path serverBin = compileC("tcp_echo_server.c", "echo_server");
        Process server = startReady(serverBin, String.valueOf(port), "echo");
        try {
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            TcpSocket socket = TcpSocket.connect("127.0.0.1", %d);
                            socket.writeString("hello");
                            String result = socket.readString(5);
                            socket.close();
                            System.out.println(result);
                        }
                    }
                    """.formatted(port), "hello\n");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void finalAcceptanceNativeServerHello() throws Exception {
        int port = freePort();
        Path serverBin = compileC("tcp_echo_server.c", "hello_server");
        Process server = startReady(serverBin, String.valueOf(port), "hello");
        try {
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            TcpSocket socket = TcpSocket.connect("127.0.0.1", %d);
                            socket.writeString("Hello from JNative");
                            String response = socket.readAllString();
                            socket.close();
                            System.out.println(response);
                        }
                    }
                    """.formatted(port), "Hello Native Client\n");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void jnativeServerNativeClient() throws Exception {
        int port = freePort();
        Path clientBin = compileC("tcp_ping_client.c", "ping_client");
        Path serverBin = compileJn("""
                public class Server {
                    public static void main(String[] args) {
                        TcpServer server = TcpServer.listen("127.0.0.1", %d);
                        TcpSocket client = server.accept();
                        String request = client.readString(1024);
                        client.writeString("pong");
                        client.close();
                        server.close();
                    }
                }
                """.formatted(port), "JnServer");

        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            Thread.sleep(300);
            Process client = new ProcessBuilder(
                    clientBin.toAbsolutePath().toString(), "127.0.0.1", String.valueOf(port))
                    .redirectErrorStream(true)
                    .start();
            String out = new String(client.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(client.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, client.exitValue(), out);
            assertEquals("pong\n", out);
            assertTrue(server.waitFor(30, TimeUnit.SECONDS));
            assertEquals(0, server.exitValue());
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void binaryNulAndFf() throws Exception {
        int port = freePort();
        Path serverBin = compileC("tcp_echo_server.c", "bin_echo");
        Process server = startReady(serverBin, String.valueOf(port), "echo");
        try {
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            TcpSocket socket = TcpSocket.connect("127.0.0.1", %d);
                            byte[] data = new byte[3];
                            data[0] = 0;
                            data[1] = -1;
                            data[2] = 7;
                            socket.write(data);
                            byte[] got = socket.readBytes(3);
                            socket.close();
                            int ok = 1;
                            if (got.length != 3) { ok = 0; }
                            if (got[0] != 0) { ok = 0; }
                            if (got[1] != -1) { ok = 0; }
                            if (got[2] != 7) { ok = 0; }
                            System.out.println(ok);
                        }
                    }
                    """.formatted(port), "1\n");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void oneMegabyteTransfer() throws Exception {
        int port = freePort();
        Path serverBin = compileJn("""
                public class Server {
                    public static void main(String[] args) {
                        int n = 1048576;
                        TcpServer server = TcpServer.listen("127.0.0.1", %d);
                        TcpSocket client = server.accept();
                        byte[] data = new byte[n];
                        int got = 0;
                        while (got < n) {
                            int want = n - got;
                            if (want > 65536) {
                                want = 65536;
                            }
                            byte[] chunk = client.readBytes(want);
                            int i = 0;
                            while (i < chunk.length) {
                                data[got] = chunk[i];
                                got = got + 1;
                                i = i + 1;
                            }
                        }
                        client.write(data);
                        client.close();
                        server.close();
                    }
                }
                """.formatted(port), "MbServer");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            Thread.sleep(300);
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            int n = 1048576;
                            byte[] data = new byte[n];
                            int i = 0;
                            while (i < n) {
                                if ((i %% 2) == 0) {
                                    data[i] = 0;
                                } else {
                                    data[i] = -1;
                                }
                                i = i + 1;
                            }
                            TcpSocket socket = TcpSocket.connect("127.0.0.1", %d);
                            socket.write(data);
                            byte[] got = new byte[n];
                            int g = 0;
                            while (g < n) {
                                int want = n - g;
                                if (want > 65536) {
                                    want = 65536;
                                }
                                byte[] chunk = socket.readBytes(want);
                                int j = 0;
                                while (j < chunk.length) {
                                    got[g] = chunk[j];
                                    g = g + 1;
                                    j = j + 1;
                                }
                            }
                            socket.close();
                            int ok = 1;
                            if (g != n) { ok = 0; }
                            i = 0;
                            while (i < n) {
                                if (got[i] != data[i]) { ok = 0; }
                                i = i + 1;
                            }
                            System.out.println(ok);
                        }
                    }
                    """.formatted(port), "1\n");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void connectRefused() throws Exception {
        int port = freePort();
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        try {
                            TcpSocket socket = TcpSocket.connect("127.0.0.1", %d);
                            socket.close();
                            System.out.println("ok");
                        } catch (ConnectException e) {
                            System.out.println("refused");
                        }
                    }
                }
                """.formatted(port), "refused\n");
    }

    @Test
    void unknownHost() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        try {
                            TcpSocket socket = TcpSocket.connect(
                                "this-domain-should-not-exist.invalid", 80);
                            socket.close();
                            System.out.println("ok");
                        } catch (UnknownHostException e) {
                            System.out.println("unknown");
                        }
                    }
                }
                """, "unknown\n");
    }

    @Test
    void useAfterCloseThrows() throws Exception {
        int port = freePort();
        Path serverBin = compileC("tcp_echo_server.c", "uac_server");
        Process server = startReady(serverBin, String.valueOf(port), "echo");
        try {
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            TcpSocket socket = TcpSocket.connect("127.0.0.1", %d);
                            socket.close();
                            socket.close();
                            try {
                                socket.writeString("hello");
                                System.out.println("ok");
                            } catch (SocketException e) {
                                System.out.println("closed");
                            }
                        }
                    }
                    """.formatted(port), "closed\n");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void readTimeout() throws Exception {
        int port = freePort();
        Path serverBin = compileJn("""
                public class Server {
                    public static void main(String[] args) {
                        TcpServer server = TcpServer.listen("127.0.0.1", %d);
                        TcpSocket client = server.accept();
                        client.setReadTimeout(30000);
                        byte[] hold = client.readBytes(1);
                        client.close();
                        server.close();
                    }
                }
                """.formatted(port), "ToServer");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            Thread.sleep(300);
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            TcpSocket socket = TcpSocket.connect("127.0.0.1", %d);
                            socket.setReadTimeout(200);
                            try {
                                String s = socket.readString(16);
                                System.out.println(s);
                            } catch (SocketTimeoutException e) {
                                System.out.println("timeout");
                            }
                            socket.writeString("x");
                            socket.close();
                        }
                    }
                    """.formatted(port), "timeout\n");
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void gcStressNetwork() throws Exception {
        int port = freePort();
        Path serverBin = compileJn("""
                public class Server {
                    public static void main(String[] args) {
                        TcpServer server = TcpServer.listen("127.0.0.1", %d);
                        int i = 0;
                        while (i < 50) {
                            TcpSocket client = server.accept();
                            byte[] data = client.readBytes(64);
                            client.write(data);
                            client.close();
                            i = i + 1;
                        }
                        server.close();
                    }
                }
                """.formatted(port), "GcNetServer");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            Thread.sleep(300);
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            int i = 0;
                            while (i < 50) {
                                TcpSocket socket = TcpSocket.connect("127.0.0.1", %d);
                                socket.writeString("ping");
                                String r = socket.readString(4);
                                socket.close();
                                i = i + 1;
                            }
                            System.out.println(1);
                        }
                    }
                    """.formatted(port), "1\n", Map.of("JNATIVE_GC_STRESS", "1"));
        } finally {
            server.destroyForcibly();
        }
    }

    @Test
    void resourceStressConnectClose() throws Exception {
        int port = freePort();
        Path serverBin = compileJn("""
                public class Server {
                    public static void main(String[] args) {
                        TcpServer server = TcpServer.listen("127.0.0.1", %d);
                        int i = 0;
                        while (i < 10000) {
                            TcpSocket client = server.accept();
                            byte[] data = client.readBytes(8);
                            client.writeString("ok");
                            client.close();
                            i = i + 1;
                        }
                        server.close();
                    }
                }
                """.formatted(port), "StressServer");
        Process server = new ProcessBuilder(serverBin.toAbsolutePath().toString())
                .redirectErrorStream(true)
                .start();
        try {
            Thread.sleep(300);
            assertStdout("""
                    public class Main {
                        public static void main(String[] args) {
                            int i = 0;
                            while (i < 10000) {
                                TcpSocket socket = TcpSocket.connect("127.0.0.1", %d);
                                socket.writeString("x");
                                String r = socket.readString(2);
                                socket.close();
                                i = i + 1;
                            }
                            System.out.println(1);
                        }
                    }
                    """.formatted(port), "1\n");
        } finally {
            server.destroyForcibly();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
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
        Path out = compileJn(source, "NetProg");
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
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
