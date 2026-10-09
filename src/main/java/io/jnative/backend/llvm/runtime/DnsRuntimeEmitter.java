package io.jnative.backend.llvm.runtime;

/**
 * DNS runtime notes (0.8.2). Resolution uses {@code getaddrinfo} inside the PlatformSocket
 * layer ({@code jnative_plat_tcp_connect}/{@code listen}); IPv4 and IPv6 address lists are tried
 * in order. Public surface is hostname-capable {@code TcpSocket.connect}.
 * @author 张晓达
 */
public final class DnsRuntimeEmitter {
    private final StringBuilder sb;

    public DnsRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("; DnsRuntimeEmitter — getaddrinfo/freeaddrinfo in PlatformSocket (POSIX)\n");
    }
}
