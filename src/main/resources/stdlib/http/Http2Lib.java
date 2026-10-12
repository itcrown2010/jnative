/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative HTTP/2 (0.31) — framing + HPACK + multiplexing foundation.
// NOT a compiler intrinsic. Shares application routes via ALPN / protocol adapter.

class Http2Error {
    static int NO_ERROR = 0;
    static int PROTOCOL_ERROR = 1;
    static int INTERNAL_ERROR = 2;
    static int FLOW_CONTROL_ERROR = 3;
    static int STREAM_CLOSED = 5;
    static int FRAME_SIZE_ERROR = 6;
    static int REFUSED_STREAM = 7;
    static int CANCEL = 8;
    static int COMPRESSION_ERROR = 9;
    static int CONNECT_ERROR = 10;
    static int ENHANCE_YOUR_CALM = 11;
    static int INADEQUATE_SECURITY = 12;
}

class Http2FrameType {
    static int DATA = 0;
    static int HEADERS = 1;
    static int PRIORITY = 2;
    static int RST_STREAM = 3;
    static int SETTINGS = 4;
    static int PUSH_PROMISE = 5;
    static int PING = 6;
    static int GOAWAY = 7;
    static int WINDOW_UPDATE = 8;
    static int CONTINUATION = 9;
}

class Http2Frame {
    int length;
    int type;
    int flags;
    int streamId;
    byte[] payload;

    Http2Frame() {
        payload = new byte[0];
    }
}

class Http2Bits {
    static int u8(byte b) {
        return BitOps.and(b, 255);
    }

    static byte b8(int v) {
        return CryptoBytes.toByte(BitOps.and(v, 255));
    }

    static void putU24(byte[] out, int off, int v) {
        out[off] = Http2Bits.b8(BitOps.ushr(v, 16));
        out[off + 1] = Http2Bits.b8(BitOps.ushr(v, 8));
        out[off + 2] = Http2Bits.b8(v);
    }

    static void putU32(byte[] out, int off, int v) {
        out[off] = Http2Bits.b8(BitOps.ushr(v, 24));
        out[off + 1] = Http2Bits.b8(BitOps.ushr(v, 16));
        out[off + 2] = Http2Bits.b8(BitOps.ushr(v, 8));
        out[off + 3] = Http2Bits.b8(v);
    }

    static void putStreamId(byte[] out, int off, int streamId) {
        out[off] = Http2Bits.b8(BitOps.and(BitOps.ushr(streamId, 24), 127));
        out[off + 1] = Http2Bits.b8(BitOps.ushr(streamId, 16));
        out[off + 2] = Http2Bits.b8(BitOps.ushr(streamId, 8));
        out[off + 3] = Http2Bits.b8(streamId);
    }

    static int readU24(byte[] data, int off) {
        return BitOps.or(
                BitOps.or(BitOps.shl(Http2Bits.u8(data[off]), 16), BitOps.shl(Http2Bits.u8(data[off + 1]), 8)),
                Http2Bits.u8(data[off + 2]));
    }

    static int readStreamId(byte[] data, int off) {
        int b0 = BitOps.and(Http2Bits.u8(data[off]), 127);
        return BitOps.or(
                BitOps.or(BitOps.or(BitOps.shl(b0, 24), BitOps.shl(Http2Bits.u8(data[off + 1]), 16)),
                        BitOps.shl(Http2Bits.u8(data[off + 2]), 8)),
                Http2Bits.u8(data[off + 3]));
    }

    static int readU32(byte[] data, int off) {
        return BitOps.or(
                BitOps.or(BitOps.or(BitOps.shl(Http2Bits.u8(data[off]), 24), BitOps.shl(Http2Bits.u8(data[off + 1]), 16)),
                        BitOps.shl(Http2Bits.u8(data[off + 2]), 8)),
                Http2Bits.u8(data[off + 3]));
    }

    static int flagSet(int flags, int bit) {
        return BitOps.and(flags, bit);
    }
}

class Http2Codec {
    static byte[] preface() {
        return Bytes.fromString("PRI * HTTP/2.0\r\n\r\nSM\r\n\r\n");
    }

    static byte[] encodeFrame(int type, int flags, int streamId, byte[] payload) {
        int len = payload.length;
        byte[] out = new byte[9 + len];
        Http2Bits.putU24(out, 0, len);
        out[3] = Http2Bits.b8(type);
        out[4] = Http2Bits.b8(flags);
        Http2Bits.putStreamId(out, 5, streamId);
        int i = 0;
        while (i < len) {
            out[9 + i] = payload[i];
            i = i + 1;
        }
        return out;
    }

    static Http2Frame decodeFrame(byte[] data, int offset) {
        if (data.length - offset < 9) {
            return null;
        }
        Http2Frame f = new Http2Frame();
        f.length = Http2Bits.readU24(data, offset);
        f.type = Http2Bits.u8(data[offset + 3]);
        f.flags = Http2Bits.u8(data[offset + 4]);
        f.streamId = Http2Bits.readStreamId(data, offset + 5);
        if (data.length - offset < 9 + f.length) {
            return null;
        }
        f.payload = new byte[f.length];
        int i = 0;
        while (i < f.length) {
            f.payload[i] = data[offset + 9 + i];
            i = i + 1;
        }
        return f;
    }

    static byte[] settingsFrame() {
        byte[] payload = new byte[12];
        /* 0x3 MAX_CONCURRENT_STREAMS = 100 */
        payload[0] = 0;
        payload[1] = 3;
        Http2Bits.putU32(payload, 2, 100);
        /* 0x4 INITIAL_WINDOW_SIZE = 65535 */
        payload[6] = 0;
        payload[7] = 4;
        Http2Bits.putU32(payload, 8, 65535);
        return Http2Codec.encodeFrame(Http2FrameType.SETTINGS, 0, 0, payload);
    }

    static byte[] settingsAck() {
        return Http2Codec.encodeFrame(Http2FrameType.SETTINGS, 1, 0, new byte[0]);
    }

    static byte[] windowUpdate(int streamId, int incr) {
        byte[] p = new byte[4];
        Http2Bits.putStreamId(p, 0, incr);
        return Http2Codec.encodeFrame(Http2FrameType.WINDOW_UPDATE, 0, streamId, p);
    }

    static byte[] goaway(int lastStream, int errorCode) {
        byte[] p = new byte[8];
        Http2Bits.putStreamId(p, 0, lastStream);
        Http2Bits.putU32(p, 4, errorCode);
        return Http2Codec.encodeFrame(Http2FrameType.GOAWAY, 0, 0, p);
    }

    static byte[] rstStream(int streamId, int errorCode) {
        byte[] p = new byte[4];
        Http2Bits.putU32(p, 0, errorCode);
        return Http2Codec.encodeFrame(Http2FrameType.RST_STREAM, 0, streamId, p);
    }
}

/** Minimal HPACK encoder for static-table indexed literals (request/response basics).
 * @author 张晓达
 */
class HpackEncoder {
    static byte[] encodeRequest(String method, String path, String host, String scheme) {
        ByteBuf b = new ByteBuf();
        if (method.equals("GET")) {
            b.appendByte(130);
        } else {
            if (method.equals("POST")) {
                b.appendByte(131);
            } else {
                HpackEncoder.literal(b, ":method", method);
            }
        }
        if (path.equals("/")) {
            b.appendByte(132);
        } else {
            HpackEncoder.literal(b, ":path", path);
        }
        if (scheme.equals("https")) {
            b.appendByte(135);
        } else {
            b.appendByte(134);
        }
        HpackEncoder.literal(b, ":authority", host);
        return b.toArray();
    }

    static void literal(ByteBuf b, String name, String value) {
        b.appendByte(0);
        HpackEncoder.encodeString(b, name);
        HpackEncoder.encodeString(b, value);
    }

    static void encodeString(ByteBuf b, String s) {
        byte[] raw = Bytes.fromString(s);
        if (raw.length < 127) {
            b.appendByte(raw.length);
        } else {
            b.appendByte(127);
            int left = raw.length - 127;
            while (left >= 128) {
                b.appendByte(BitOps.or(BitOps.and(left, 127), 128));
                left = left / 128;
            }
            b.appendByte(left);
        }
        b.appendBytes(raw);
    }
}

class Http2Client {
    static HttpResponse request(TlsConnection tls, HttpRequest request, HttpUrl url) {
        tls.write(Http2Codec.preface());
        tls.write(Http2Codec.settingsFrame());
        byte[] headers = HpackEncoder.encodeRequest(request.method, url.path, url.host, url.scheme);
        int flags = 4;
        if (request.body == null || request.body.length == 0) {
            flags = flags + 1;
        }
        tls.write(Http2Codec.encodeFrame(Http2FrameType.HEADERS, flags, 1, headers));
        if (request.body != null && request.body.length > 0) {
            tls.write(Http2Codec.encodeFrame(Http2FrameType.DATA, 1, 1, request.body));
        }
        ByteBuf buf = new ByteBuf();
        int gotHeaders = 0;
        int status = 0;
        ByteBuf body = new ByteBuf();
        int done = 0;
        int guard = 0;
        while (done == 0 && guard < 10000) {
            byte[] chunk = new byte[8192];
            int n = tls.read(chunk);
            if (n <= 0) {
                done = 1;
            } else {
                byte[] got = new byte[n];
                int i = 0;
                while (i < n) {
                    got[i] = chunk[i];
                    i = i + 1;
                }
                buf.appendBytes(got);
                int off = 0;
                byte[] all = buf.toArray();
                int frameMore = 1;
                while (frameMore != 0 && off + 9 <= all.length) {
                    Http2Frame f = Http2Codec.decodeFrame(all, off);
                    if (f == null) {
                        frameMore = 0;
                    } else {
                        off = off + 9 + f.length;
                        if (f.type == Http2FrameType.SETTINGS) {
                            if (Http2Bits.flagSet(f.flags, 1) == 0) {
                                tls.write(Http2Codec.settingsAck());
                            }
                        } else {
                            if (f.type == Http2FrameType.HEADERS && f.streamId == 1) {
                                status = HpackDecoder.peekStatus(f.payload);
                                gotHeaders = 1;
                                if (Http2Bits.flagSet(f.flags, 1) != 0) {
                                    done = 1;
                                }
                            } else {
                                if (f.type == Http2FrameType.DATA && f.streamId == 1) {
                                    body.appendBytes(f.payload);
                                    if (Http2Bits.flagSet(f.flags, 1) != 0) {
                                        done = 1;
                                    }
                                } else {
                                    if (f.type == Http2FrameType.GOAWAY) {
                                        done = 1;
                                    } else {
                                        if (f.type == Http2FrameType.RST_STREAM && f.streamId == 1) {
                                            throw new HttpProtocolException("HTTP/2 RST_STREAM");
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (off > 0) {
                    buf = new ByteBuf();
                    if (off < all.length) {
                        buf.appendBytes(Bytes.slice(all, off, all.length));
                    }
                }
            }
            guard = guard + 1;
        }
        HttpResponse response = new HttpResponse();
        response.statusCode = status;
        if (status == 0) {
            response.statusCode = 200;
        }
        response.reasonPhrase = "";
        response.headers = new HttpHeaders();
        response.body = body.toArray();
        return response;
    }
}

class HpackDecoder {
    static int peekStatus(byte[] payload) {
        int i = 0;
        while (i < payload.length) {
            int b = Http2Bits.u8(payload[i]);
            if (b >= 128) {
                int idx = BitOps.and(b, 127);
                if (idx == 8) {
                    return 200;
                }
                if (idx == 9) {
                    return 204;
                }
                if (idx == 13) {
                    return 404;
                }
                if (idx == 14) {
                    return 500;
                }
                i = i + 1;
            } else {
                i = payload.length;
            }
        }
        return 200;
    }
}

class Http2Connection {
    TlsConnection tls;
    int nextStreamId;
    int goaway;
    int lastStream;
    int connWindow;
    int maxConcurrent;

    Http2Connection(TlsConnection tls) {
        this.tls = tls;
        nextStreamId = 1;
        goaway = 0;
        lastStream = 0;
        connWindow = 65535;
        maxConcurrent = 100;
    }

    void sendGoAway() {
        tls.write(Http2Codec.goaway(lastStream, Http2Error.NO_ERROR));
        goaway = 1;
    }

    int allocateStream() {
        if (goaway != 0) {
            throw new HttpProtocolException("HTTP/2 GOAWAY: refusing new streams");
        }
        int id = nextStreamId;
        nextStreamId = nextStreamId + 2;
        lastStream = id;
        return id;
    }
}

class HpackResponse {
    static byte[] encodeStatus(int status) {
        ByteBuf b = new ByteBuf();
        if (status == 200) {
            b.appendByte(136);
        } else {
            if (status == 204) {
                b.appendByte(137);
            } else {
                if (status == 404) {
                    b.appendByte(141);
                } else {
                    if (status == 500) {
                        b.appendByte(142);
                    } else {
                        HpackEncoder.literal(b, ":status", String.valueOf(status));
                    }
                }
            }
        }
        return b.toArray();
    }
}
