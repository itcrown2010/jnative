// JNative TLS stdlib (0.31) — transport-layer SecureTransport over OpenSSL backend.
// NOT a compiler intrinsic. TLS_NATIVE capability. Browser WASM must NOT include this.
// TLS compiler intrinsics: NONE

/**
 * @author 张晓达
 */
class TlsException extends IOException {
    TlsException(String message) {
        super(message);
    }
}

class TlsHandshakeException extends TlsException {
    TlsHandshakeException(String message) {
        super(message);
    }
}

class TlsCertificateException extends TlsException {
    TlsCertificateException(String message) {
        super(message);
    }
}

class TlsWant {
    static int OK = 0;
    static int WANT_READ = 1;
    static int WANT_WRITE = 2;
    static int FAIL = -1;
}

class TlsNative {
    @Native(library = "jnative_tls", symbol = "jnative_tls_client_ctx_new")
    static native long clientCtxNew(int insecure, String caFile, String alpnCsv, NativePtr errbuf, int errLen);

    @Native(library = "jnative_tls", symbol = "jnative_tls_server_ctx_new")
    static native long serverCtxNew(String certFile, String keyFile, String alpnCsv, NativePtr errbuf, int errLen);

    @Native(library = "jnative_tls", symbol = "jnative_tls_ctx_reload_server")
    static native int ctxReloadServer(long ctx, String certFile, String keyFile, NativePtr errbuf, int errLen);

    @Native(library = "jnative_tls", symbol = "jnative_tls_ctx_free")
    static native void ctxFree(long ctx);

    @Native(library = "jnative_tls", symbol = "jnative_tls_wrap_client")
    static native long wrapClient(long ctx, long fd, String serverName, NativePtr errbuf, int errLen);

    @Native(library = "jnative_tls", symbol = "jnative_tls_wrap_server")
    static native long wrapServer(long ctx, long fd, NativePtr errbuf, int errLen);

    @Native(library = "jnative_tls", symbol = "jnative_tls_handshake")
    static native int handshake(long tls, NativePtr errbuf, int errLen);

    @Native(library = "jnative_tls", symbol = "jnative_tls_read")
    static native int read(long tls, NativePtr buf, int len, NativePtr errbuf, int errLen);

    @Native(library = "jnative_tls", symbol = "jnative_tls_write")
    static native int write(long tls, NativePtr buf, int len, NativePtr errbuf, int errLen);

    @Native(library = "jnative_tls", symbol = "jnative_tls_close")
    static native void close(long tls);

    @Native(library = "jnative_tls", symbol = "jnative_tls_alpn")
    static native int alpn(long tls, NativePtr out, int outLen);

    @Native(library = "jnative_tls", symbol = "jnative_tls_version")
    static native int version(long tls, NativePtr out, int outLen);

    @Native(library = "jnative_tls", symbol = "jnative_tls_inspect_cert")
    static native int inspectCert(String certFile, NativePtr out, int outLen, NativePtr errbuf, int errLen);

    static NativePtr allocErr(int len) {
        /* +4 so putU8/getU8 int RMW never reads past allocation */
        return NativeMemory.allocate(0L + len + 4);
    }

    static String readCString(NativePtr p, int maxLen) {
        return NativeMemory.fromCString(p);
    }

    static void zero(NativePtr p, int len) {
        long off = 0L;
        int left = len;
        while (left >= 4) {
            NativeMemory.putInt(p, off, 0);
            off = off + 4L;
            left = left - 4;
        }
        if (left > 0) {
            NativeMemory.putInt(p, off, 0);
        }
    }

    static void putU8(NativePtr p, int index, int value) {
        long base = 0L + ((index / 4) * 4);
        int shift = (index % 4) * 8;
        int word = NativeMemory.getInt(p, base);
        int mask = BitOps.shl(255, shift);
        int cleared = BitOps.and(word, BitOps.not(mask));
        int inserted = BitOps.or(cleared, BitOps.shl(BitOps.and(value, 255), shift));
        NativeMemory.putInt(p, base, inserted);
    }

    static int getU8(NativePtr p, int index) {
        long base = 0L + ((index / 4) * 4);
        int shift = (index % 4) * 8;
        int word = NativeMemory.getInt(p, base);
        return BitOps.and(BitOps.ushr(word, shift), 255);
    }

    static void copyToNative(byte[] src, NativePtr dst, int len) {
        int i = 0;
        while (i < len) {
            TlsNative.putU8(dst, i, src[i]);
            i = i + 1;
        }
    }

    static void copyFromNative(NativePtr src, byte[] dst, int len) {
        int i = 0;
        while (i < len) {
            dst[i] = CryptoBytes.toByte(TlsNative.getU8(src, i));
            i = i + 1;
        }
    }
}

class TlsConfig {
    String certificate;
    String privateKey;
    String caFile;
    int insecureSkipVerify;
    String alpn;
    String serverName;

    TlsConfig() {
        certificate = "";
        privateKey = "";
        caFile = "";
        insecureSkipVerify = 0;
        alpn = "h2,http/1.1";
        serverName = "";
    }

    static TlsConfig builder() {
        return new TlsConfig();
    }

    TlsConfig certificate(String path) {
        certificate = path;
        return this;
    }

    TlsConfig privateKey(String path) {
        privateKey = path;
        return this;
    }

    TlsConfig caFile(String path) {
        caFile = path;
        return this;
    }

    TlsConfig insecureSkipVerify(int v) {
        insecureSkipVerify = v;
        if (v != 0) {
            System.out.println("warning: TlsConfig.insecureSkipVerify(true) — certificate verification DISABLED");
        }
        return this;
    }

    TlsConfig alpn(String protocols) {
        alpn = protocols;
        return this;
    }

    TlsConfig serverName(String name) {
        serverName = name;
        return this;
    }

    TlsConfig build() {
        return this;
    }
}

class TlsContext {
    long handle;
    int isServer;
    TlsConfig config;

    static TlsContext client(TlsConfig config) {
        TlsContext c = new TlsContext();
        c.config = config;
        c.isServer = 0;
        NativePtr err = TlsNative.allocErr(512);
        TlsNative.zero(err, 512);
        int insecure = config.insecureSkipVerify;
        c.handle = TlsNative.clientCtxNew(insecure, config.caFile, config.alpn, err, 512);
        if (c.handle == 0) {
            String msg = TlsNative.readCString(err, 512);
            NativeMemory.free(err);
            throw new TlsException(msg);
        }
        NativeMemory.free(err);
        return c;
    }

    static TlsContext server(TlsConfig config) {
        TlsContext c = new TlsContext();
        c.config = config;
        c.isServer = 1;
        NativePtr err = TlsNative.allocErr(512);
        TlsNative.zero(err, 512);
        c.handle = TlsNative.serverCtxNew(config.certificate, config.privateKey, config.alpn, err, 512);
        if (c.handle == 0) {
            String msg = TlsNative.readCString(err, 512);
            NativeMemory.free(err);
            throw new TlsException(msg);
        }
        NativeMemory.free(err);
        return c;
    }

    void reloadCertificates() {
        if (isServer == 0) {
            throw new TlsException("reload only supported for server context");
        }
        NativePtr err = TlsNative.allocErr(512);
        TlsNative.zero(err, 512);
        int rc = TlsNative.ctxReloadServer(handle, config.certificate, config.privateKey, err, 512);
        if (rc != 0) {
            String msg = TlsNative.readCString(err, 512);
            NativeMemory.free(err);
            throw new TlsException(msg);
        }
        NativeMemory.free(err);
    }

    void close() {
        if (handle != 0) {
            TlsNative.ctxFree(handle);
            handle = 0;
        }
    }
}

class TlsConnection implements AutoCloseable {
    long handle;
    TcpSocket tcp;
    String negotiatedAlpn;
    String tlsVersion;
    int closed;
    TlsContext ctx;

    static TlsConnection connect(String host, int port, TlsConfig config) {
        TcpSocket tcp = TcpSocket.connect(host, port);
        TlsContext ctx = TlsContext.client(config);
        String sni = config.serverName;
        if (sni == null || sni.length() == 0) {
            sni = host;
        }
        long fd = tcp.nativeFd();
        NativePtr err = TlsNative.allocErr(512);
        TlsNative.zero(err, 512);
        long tls = TlsNative.wrapClient(ctx.handle, fd, sni, err, 512);
        if (tls == 0) {
            String msg = TlsNative.readCString(err, 512);
            NativeMemory.free(err);
            tcp.close();
            ctx.close();
            throw new TlsHandshakeException(msg);
        }
        NativeMemory.free(err);
        TlsConnection conn = new TlsConnection();
        conn.handle = tls;
        conn.tcp = tcp;
        conn.closed = 0;
        conn.ctx = ctx;
        ResourceTracker.trackOpen("tls");
        conn.driveHandshake();
        conn.refreshMeta();
        return conn;
    }

    static TlsConnection accept(TcpSocket tcp, TlsContext serverCtx) {
        long fd = tcp.nativeFd();
        NativePtr err = TlsNative.allocErr(512);
        TlsNative.zero(err, 512);
        long tls = TlsNative.wrapServer(serverCtx.handle, fd, err, 512);
        if (tls == 0) {
            String msg = TlsNative.readCString(err, 512);
            NativeMemory.free(err);
            throw new TlsHandshakeException(msg);
        }
        NativeMemory.free(err);
        TlsConnection conn = new TlsConnection();
        conn.handle = tls;
        conn.tcp = tcp;
        conn.ctx = serverCtx;
        conn.closed = 0;
        ResourceTracker.trackOpen("tls");
        conn.driveHandshake();
        conn.refreshMeta();
        return conn;
    }

    void driveHandshake() {
        NativePtr err = TlsNative.allocErr(512);
        int spins = 0;
        int done = 0;
        while (done == 0 && spins < 100000) {
            TlsNative.zero(err, 512);
            int rc = TlsNative.handshake(handle, err, 512);
            if (rc == TlsWant.OK) {
                done = 1;
            } else {
                if (rc == TlsWant.FAIL) {
                    String msg = TlsNative.readCString(err, 512);
                    NativeMemory.free(err);
                    close();
                    if (msg.indexOf("certificate") >= 0 || msg.indexOf("hostname") >= 0 || msg.indexOf("CA") >= 0) {
                        throw new TlsCertificateException(msg);
                    }
                    throw new TlsHandshakeException(msg);
                }
                spins = spins + 1;
            }
        }
        NativeMemory.free(err);
        if (done == 0) {
            close();
            throw new TlsHandshakeException("handshake timeout");
        }
    }

    void refreshMeta() {
        NativePtr buf = NativeMemory.allocate(64L);
        TlsNative.zero(buf, 64);
        TlsNative.alpn(handle, buf, 64);
        negotiatedAlpn = TlsNative.readCString(buf, 64);
        TlsNative.zero(buf, 64);
        TlsNative.version(handle, buf, 64);
        tlsVersion = TlsNative.readCString(buf, 64);
        NativeMemory.free(buf);
    }

    int read(byte[] buffer) {
        NativePtr err = TlsNative.allocErr(256);
        NativePtr nb = NativeMemory.allocate(0L + buffer.length + 4);
        TlsNative.zero(err, 256);
        int rc = TlsNative.read(handle, nb, buffer.length, err, 256);
        if (rc == -1) {
            String msg = TlsNative.readCString(err, 256);
            NativeMemory.free(err);
            NativeMemory.free(nb);
            throw new TlsException(msg);
        }
        if (rc == -2 || rc == -3) {
            NativeMemory.free(err);
            NativeMemory.free(nb);
            return 0;
        }
        if (rc > 0) {
            TlsNative.copyFromNative(nb, buffer, rc);
        }
        NativeMemory.free(err);
        NativeMemory.free(nb);
        return rc;
    }

    void write(byte[] data) {
        NativePtr err = TlsNative.allocErr(256);
        int offset = 0;
        while (offset < data.length) {
            int left = data.length - offset;
            NativePtr nb = NativeMemory.allocate(0L + left + 4);
            int i = 0;
            while (i < left) {
                TlsNative.putU8(nb, i, data[offset + i]);
                i = i + 1;
            }
            TlsNative.zero(err, 256);
            int rc = TlsNative.write(handle, nb, left, err, 256);
            NativeMemory.free(nb);
            if (rc == -1) {
                String msg = TlsNative.readCString(err, 256);
                NativeMemory.free(err);
                throw new TlsException(msg);
            }
            if (rc > 0) {
                offset = offset + rc;
            }
        }
        NativeMemory.free(err);
    }

    void writeString(String s) {
        write(Bytes.fromString(s));
    }

    String alpn() {
        return negotiatedAlpn;
    }

    String version() {
        return tlsVersion;
    }

    void close() {
        if (closed != 0) {
            return;
        }
        closed = 1;
        if (handle != 0) {
            TlsNative.close(handle);
            handle = 0;
            ResourceTracker.trackClose("tls");
        }
        if (tcp != null) {
            tcp.close();
        }
    }
}
