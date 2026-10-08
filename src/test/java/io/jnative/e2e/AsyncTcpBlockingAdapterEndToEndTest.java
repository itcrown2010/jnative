package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RH-1: prove AsyncTcp* is a blocking pool adapter that still functions,
 * without claiming event-driven fd I/O.
 */
class AsyncTcpBlockingAdapterEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void asyncTcpConnectReadWriteViaPoolAdapter() throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        int port = freePort();
        Path ready = tempDir.resolve("ready");

        Path serverBin = compile("""
                public class Server {
                    public static void main(String[] args) {
                        TcpServer server = TcpServer.listen("127.0.0.1", %d);
                        File.writeString("%s", "1");
                        TcpSocket s = server.accept();
                        String got = s.readString(4);
                        System.out.println(got);
                        s.close();
                        server.close();
                    }
                }
                """.formatted(port, ready.toAbsolutePath()), "AdpServer");

        ProcessBuilder spb = new ProcessBuilder(serverBin.toAbsolutePath().toString());
        spb.redirectErrorStream(true);
        Process server = spb.start();
        waitFile(ready, 10);

        Path clientBin = compile("""
                public class Client {
                    public static void main(String[] args) {
                        EventLoop loop = new EventLoop();
                        AsyncTcpSocket sock = (AsyncTcpSocket)FutureBlocker.blockingGet(
                                AsyncTcpSocket.connectAsync(loop, "127.0.0.1", %d));
                        byte[] msg = Bytes.fromString("ping");
                        FutureBlocker.blockingGet(sock.writeAsync(msg));
                        sock.close();
                        System.out.println("JNATIVE_ASYNCTCP_BLOCKING_ADAPTER_PASS");
                    }
                }
                """.formatted(port), "AdpClient");

        ProcessBuilder cpb = new ProcessBuilder(clientBin.toAbsolutePath().toString());
        cpb.redirectErrorStream(true);
        Process client = cpb.start();
        String clientOut = new String(client.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(client.waitFor(20, TimeUnit.SECONDS), "client timeout: " + clientOut);
        String serverOut = new String(server.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(server.waitFor(20, TimeUnit.SECONDS), "server timeout: " + serverOut);
        assertEquals(0, client.exitValue(), clientOut);
        assertEquals(0, server.exitValue(), serverOut);
        assertTrue(serverOut.contains("ping"), serverOut);
        assertTrue(clientOut.contains("JNATIVE_ASYNCTCP_BLOCKING_ADAPTER_PASS"), clientOut);
    }

    private Path compile(String source, String name) throws Exception {
        Path javaFile = tempDir.resolve(name + ".java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve(name);
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        options.setOptLevel(1);
        CompileResult cr = new JNativeCompiler().compile(options);
        assertTrue(cr.success(), () -> cr.diagnostics().toString());
        return out;
    }

    private static int freePort() throws Exception {
        try (ServerSocket ss = new ServerSocket(0)) {
            return ss.getLocalPort();
        }
    }

    private static void waitFile(Path p, int seconds) throws Exception {
        long deadline = System.currentTimeMillis() + seconds * 1000L;
        while (System.currentTimeMillis() < deadline) {
            if (Files.exists(p)) {
                return;
            }
            Thread.sleep(20);
        }
        fail("missing " + p);
    }
}
