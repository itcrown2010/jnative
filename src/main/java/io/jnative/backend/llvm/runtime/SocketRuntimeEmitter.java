package io.jnative.backend.llvm.runtime;

/**
 * Socket-facing runtime notes (0.8.2). Concrete helpers live in {@link NetworkRuntimeEmitter};
 * platform syscalls are in the POSIX/Winsock PlatformSocket C layer.
 */
public final class SocketRuntimeEmitter {
    private final StringBuilder sb;

    public SocketRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        // Reserved for future socket-only helpers (keepalive, nodelay, etc.).
        sb.append("; SocketRuntimeEmitter — TCP helpers emitted by NetworkRuntimeEmitter\n");
    }
}
