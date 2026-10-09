// JNative Net addressing / DNS / UDP (Core Completion Round 4).
// TCP remains compiler builtins (TcpSocket/TcpServer). This unit is library + C ABI.

/**
 * @author 张晓达
 */
class NetNative {
    @Native(symbol = "jnative_plat_dns_resolve")
    static native NativePtr dnsResolve(String host, NativePtr errbuf, int errLen);

    @Native(symbol = "jnative_plat_net_free")
    static native void netFree(NativePtr p);

    @Native(symbol = "jnative_plat_udp_bind")
    static native long udpBind(String host, int port, NativePtr errbuf, int errLen);

    @Native(symbol = "jnative_plat_udp_sendto")
    static native int udpSendTo(long handle, NativePtr buf, int n, String host, int port, NativePtr errbuf, int errLen);

    @Native(symbol = "jnative_plat_udp_recvfrom")
    static native int udpRecvFrom(long handle, NativePtr buf, int max, NativePtr outN,
                                  NativePtr hostOut, int hostOutLen, NativePtr outPort,
                                  NativePtr errbuf, int errLen);

    @Native(symbol = "jnative_plat_udp_close")
    static native int udpClose(long handle);

    static NativePtr allocErr(int len) {
        return NativeMemory.allocate(0L + len + 4);
    }

    static void zero(NativePtr p, int len) {
        long off = 0L;
        int left = len;
        while (left >= 4) {
            NativeMemory.putInt(p, off, 0);
            off = off + 4L;
            left = left - 4;
        }
    }

    static int getI32(NativePtr p) {
        return NativeMemory.getInt(p, 0L);
    }

    static void putI32(NativePtr p, int v) {
        NativeMemory.putInt(p, 0L, v);
    }

    /** Same contract as StreamNative.putU8/getU8 — logical shifts, not signed /256. */
    static void putU8(NativePtr p, int index, int value) {
        long base = 0L + ((index / 4) * 4);
        int shift = (index % 4) * 8;
        int word = NativeMemory.getInt(p, base);
        int mask = 255 << shift;
        int cleared = word & (~mask);
        int inserted = cleared | ((value & 255) << shift);
        NativeMemory.putInt(p, base, inserted);
    }

    static int getU8(NativePtr p, int index) {
        long base = 0L + ((index / 4) * 4);
        int shift = (index % 4) * 8;
        int word = NativeMemory.getInt(p, base);
        return (word >>> shift) & 255;
    }
}

class IPAddress {
    String text;
    int isV6;

    IPAddress(String text, int isV6) {
        this.text = text;
        this.isV6 = isV6;
    }

    static IPAddress parse(String s) {
        if (s == null || s.length() == 0) {
            throw new IllegalArgumentException("empty IP");
        }
        int v6 = 0;
        int i = 0;
        while (i < s.length()) {
            if (s.charAt(i) == 58) {
                v6 = 1;
            }
            i = i + 1;
        }
        return new IPAddress(s, v6);
    }

    static IPAddress loopbackV4() {
        return new IPAddress("127.0.0.1", 0);
    }

    static IPAddress loopbackV6() {
        return new IPAddress("::1", 1);
    }

    static IPAddress anyV4() {
        return new IPAddress("0.0.0.0", 0);
    }

    static IPAddress anyV6() {
        return new IPAddress("::", 1);
    }

    boolean isIPv6() {
        return isV6 != 0;
    }

    String toString() {
        return text;
    }
}

class SocketAddress {
    IPAddress address;
    int port;

    SocketAddress(IPAddress address, int port) {
        this.address = address;
        this.port = port;
    }

    static SocketAddress of(String host, int port) {
        return new SocketAddress(IPAddress.parse(host), port);
    }

    String host() {
        return address.toString();
    }

    int port() {
        return port;
    }
}

class DnsResolver {
    static ArrayList<IPAddress> resolve(String hostname) {
        if (hostname == null || hostname.length() == 0) {
            throw new IOException("dns: empty hostname");
        }
        NativePtr err = NetNative.allocErr(256);
        NetNative.zero(err, 256);
        NativePtr p = NetNative.dnsResolve(hostname, err, 256);
        if (p == NativePtr.NULL) {
            String msg = NativeMemory.fromCString(err);
            NativeMemory.free(err);
            throw new IOException(msg);
        }
        NativeMemory.free(err);
        String blob = NativeMemory.fromCString(p);
        NetNative.netFree(p);
        ArrayList<IPAddress> out = new ArrayList<IPAddress>();
        if (blob == null || blob.length() == 0) {
            return out;
        }
        int i = 0;
        int start = 0;
        while (i <= blob.length()) {
            if (i == blob.length() || blob.charAt(i) == 10) {
                if (i > start) {
                    out.add(IPAddress.parse(blob.substring(start, i)));
                }
                start = i + 1;
            }
            i = i + 1;
        }
        return out;
    }
}

class UdpSocket implements AutoCloseable {
    long handle;
    int closed;

    UdpSocket() {
        handle = -1L;
        closed = 0;
    }

    static UdpSocket bind(String host, int port) {
        UdpSocket s = new UdpSocket();
        NativePtr err = NetNative.allocErr(256);
        NetNative.zero(err, 256);
        long h = NetNative.udpBind(host, port, err, 256);
        if (h < 0L) {
            String msg = NativeMemory.fromCString(err);
            NativeMemory.free(err);
            throw new IOException(msg);
        }
        s.handle = h;
        NativeMemory.free(err);
        ResourceTracker.trackOpen("socket");
        return s;
    }

    static UdpSocket bind(int port) {
        return bind("127.0.0.1", port);
    }

    void sendTo(byte[] data, String host, int port) {
        if (closed != 0 || handle < 0L) {
            throw new IOException("udp closed");
        }
        if (data == null) {
            throw new NullPointerException("data");
        }
        NativePtr buf = NativeMemory.allocate(0L + data.length + 4);
        int i = 0;
        while (i < data.length) {
            NetNative.putU8(buf, i, data[i]);
            i = i + 1;
        }
        NativePtr err = NetNative.allocErr(256);
        NetNative.zero(err, 256);
        int rc = NetNative.udpSendTo(handle, buf, data.length, host, port, err, 256);
        NativeMemory.free(buf);
        if (rc != 0) {
            String msg = NativeMemory.fromCString(err);
            NativeMemory.free(err);
            throw new IOException(msg);
        }
        NativeMemory.free(err);
    }

    /** Returns datagram bytes; peer host/port written into peerHostOut[0] / peerPortOut[0]. */
    byte[] receiveFrom(int maxBytes, String[] peerHostOut, int[] peerPortOut) {
        if (closed != 0 || handle < 0L) {
            throw new IOException("udp closed");
        }
        if (maxBytes < 1) {
            maxBytes = 1;
        }
        NativePtr buf = NativeMemory.allocate(0L + maxBytes + 4);
        NativePtr outN = NativeMemory.allocate(16L);
        NativePtr hostOut = NativeMemory.allocate(128L);
        NativePtr outPort = NativeMemory.allocate(16L);
        NativePtr err = NetNative.allocErr(256);
        NetNative.zero(err, 256);
        NetNative.putI32(outN, 0);
        NetNative.putI32(outPort, 0);
        int rc = NetNative.udpRecvFrom(handle, buf, maxBytes, outN, hostOut, 120, outPort, err, 256);
        if (rc != 0) {
            String msg = NativeMemory.fromCString(err);
            NativeMemory.free(err);
            NativeMemory.free(buf);
            NativeMemory.free(outN);
            NativeMemory.free(hostOut);
            NativeMemory.free(outPort);
            throw new IOException(msg);
        }
        int n = NetNative.getI32(outN);
        byte[] data = new byte[n];
        int i = 0;
        while (i < n) {
            int v = NetNative.getU8(buf, i);
            if (v > 127) {
                data[i] = v - 256;
            } else {
                data[i] = v;
            }
            i = i + 1;
        }
        if (peerHostOut != null && peerHostOut.length > 0) {
            peerHostOut[0] = NativeMemory.fromCString(hostOut);
        }
        if (peerPortOut != null && peerPortOut.length > 0) {
            peerPortOut[0] = NetNative.getI32(outPort);
        }
        NativeMemory.free(err);
        NativeMemory.free(buf);
        NativeMemory.free(outN);
        NativeMemory.free(hostOut);
        NativeMemory.free(outPort);
        return data;
    }

    void close() {
        if (closed == 0) {
            if (handle >= 0L) {
                NetNative.udpClose(handle);
                handle = -1L;
                ResourceTracker.trackClose("socket");
            }
            closed = 1;
        }
    }
}
