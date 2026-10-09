package io.jnative.e2e;

import io.jnative.backend.Toolchain;
import io.jnative.compiler.CompileResult;
import io.jnative.compiler.CompilerOptions;
import io.jnative.compiler.JNativeCompiler;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author 张晓达
 */
class DnsUdpEndToEndTest {

    @TempDir
    Path tempDir;

    @Test
    void dnsLocalhostMultipleOrAtLeastOne() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        ArrayList<IPAddress> addrs = DnsResolver.resolve("localhost");
                        int a = 0;
                        if (addrs.size() >= 1) {
                            a = 1;
                        }
                        int b = 0;
                        if (addrs.get(0).toString().length() > 0) {
                            b = 1;
                        }
                        System.out.println(a);
                        System.out.println(b);
                    }
                }
                """, "1\n1\n");
    }

    @Test
    void dnsNxDomainFails() throws Exception {
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        int ok = 0;
                        try {
                            DnsResolver.resolve("no-such-host.invalid.");
                        } catch (IOException e) {
                            ok = 1;
                        }
                        System.out.println(ok);
                    }
                }
                """, "1\n");
    }

    @Test
    void udpEchoLoopback() throws Exception {
        int sp = freeUdpPort();
        int cp = freeUdpPort();
        assertStdout("""
                public class Main {
                    public static void main(String[] args) {
                        UdpSocket s = UdpSocket.bind("127.0.0.1", %d);
                        UdpSocket c = UdpSocket.bind("127.0.0.1", %d);
                        byte[] msg = Bytes.fromString("ping");
                        c.sendTo(msg, "127.0.0.1", %d);
                        String[] host = new String[1];
                        int[] port = new int[1];
                        byte[] got = s.receiveFrom(64, host, port);
                        System.out.println(Bytes.toString(got));
                        s.close();
                        c.close();
                    }
                }
                """.formatted(sp, cp, sp), "ping\n");
    }

    private static int freeUdpPort() throws Exception {
        try (java.net.DatagramSocket s = new java.net.DatagramSocket(0)) {
            return s.getLocalPort();
        }
    }

    private void assertStdout(String source, String expected) throws Exception {
        Assumptions.assumeTrue(Toolchain.detect().isPresent(), "clang not available");
        Path javaFile = tempDir.resolve("DnsUdpT.java");
        Files.writeString(javaFile, source, StandardCharsets.UTF_8);
        Path out = tempDir.resolve("DnsUdpBin");
        CompilerOptions options = new CompilerOptions();
        options.setSourceFile(javaFile);
        options.setOutputFile(out);
        CompileResult result = new JNativeCompiler().compile(options);
        assertTrue(result.success(), () -> result.diagnostics().toString());
        ProcessBuilder pb = new ProcessBuilder(out.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(process.waitFor(120, TimeUnit.SECONDS), "timeout");
        assertEquals(0, process.exitValue(), stdout);
        assertEquals(expected, stdout);
    }
}
