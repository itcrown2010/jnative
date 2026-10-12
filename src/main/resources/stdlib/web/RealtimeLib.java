/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative Realtime (0.25) — WebSocket frames, connection pump, Hub, SSE.

/**
 * @author 张晓达
 */
interface WebSocketHandler {
    void onConnect(WebSocketConnection socket);

    void onMessage(WebSocketConnection socket, WebSocketMessage message);
}

class WebSocketHandlerAdapter implements WebSocketHandler {
    public void onConnect(WebSocketConnection socket) {
    }

    public void onMessage(WebSocketConnection socket, WebSocketMessage message) {
    }
}

class WebSocketOpcode {
    static int CONTINUATION = 0;
    static int TEXT = 1;
    static int BINARY = 2;
    static int CLOSE = 8;
    static int PING = 9;
    static int PONG = 10;
}

class WebSocketState {
    static int CONNECTING = 0;
    static int OPEN = 1;
    static int CLOSING = 2;
    static int CLOSED = 3;
}

class WebSocketMessage {
    int kind;
    String text;
    byte[] bytes;

    static int KIND_TEXT = 0;
    static int KIND_BINARY = 1;

    WebSocketMessage() {
        kind = KIND_TEXT;
        text = "";
        bytes = new byte[0];
    }

    static WebSocketMessage textMessage(String s) {
        WebSocketMessage m = new WebSocketMessage();
        m.kind = KIND_TEXT;
        if (s == null) {
            m.text = "";
        } else {
            m.text = s;
        }
        m.bytes = Bytes.fromString(m.text);
        return m;
    }

    static WebSocketMessage binaryMessage(byte[] data) {
        WebSocketMessage m = new WebSocketMessage();
        m.kind = KIND_BINARY;
        if (data == null) {
            m.bytes = new byte[0];
        } else {
            m.bytes = data;
        }
        m.text = "";
        return m;
    }

    int isText() {
        if (kind == KIND_TEXT) {
            return 1;
        }
        return 0;
    }

    int isBinary() {
        if (kind == KIND_BINARY) {
            return 1;
        }
        return 0;
    }
}

class WebSocketFrame {
    int fin;
    int opcode;
    byte[] payload;
    int bytesConsumed;
    int ok;
    int needMore;
    int error;
    String errorMessage;

    WebSocketFrame() {
        fin = 1;
        opcode = 0;
        payload = new byte[0];
        bytesConsumed = 0;
        ok = 0;
        needMore = 0;
        error = 0;
        errorMessage = "";
    }

    static WebSocketFrame needMoreResult() {
        WebSocketFrame f = new WebSocketFrame();
        f.needMore = 1;
        return f;
    }

    static WebSocketFrame errorResult(String msg) {
        WebSocketFrame f = new WebSocketFrame();
        f.error = 1;
        f.errorMessage = msg;
        return f;
    }
}

class WebSocketFrameCodec {
    static int DEFAULT_MAX_PAYLOAD = 1048576;

    static WebSocketFrame parseOne(byte[] buf, int offset, int maxPayload, int requireMasked) {
        if (buf == null) {
            return WebSocketFrame.needMoreResult();
        }
        if (maxPayload <= 0) {
            maxPayload = DEFAULT_MAX_PAYLOAD;
        }
        int avail = buf.length - offset;
        if (avail < 2) {
            return WebSocketFrame.needMoreResult();
        }
        int b0 = CryptoBytes.u8(buf[offset]);
        int b1 = CryptoBytes.u8(buf[offset + 1]);
        int fin = 0;
        if (BitOps.and(b0, 128) != 0) {
            fin = 1;
        }
        int rsv = BitOps.and(b0, 112);
        if (rsv != 0) {
            return WebSocketFrame.errorResult("RSV bits must be 0");
        }
        int opcode = BitOps.and(b0, 15);
        int masked = 0;
        if (BitOps.and(b1, 128) != 0) {
            masked = 1;
        }
        if (requireMasked != 0) {
            if (masked == 0) {
                return WebSocketFrame.errorResult("client frames must be masked");
            }
        }
        int len7 = BitOps.and(b1, 127);
        int header = 2;
        long payloadLen = len7;
        if (len7 == 126) {
            if (avail < 4) {
                return WebSocketFrame.needMoreResult();
            }
            int hi = CryptoBytes.u8(buf[offset + 2]);
            int lo = CryptoBytes.u8(buf[offset + 3]);
            payloadLen = hi * 256 + lo;
            header = 4;
        } else {
            if (len7 == 127) {
                if (avail < 10) {
                    return WebSocketFrame.needMoreResult();
                }
                int i = 0;
                long v = 0L;
                int oversized = 0;
                while (i < 8) {
                    int b = CryptoBytes.u8(buf[offset + 2 + i]);
                    if (i < 4) {
                        if (b != 0) {
                            oversized = 1;
                        }
                    } else {
                        v = v * 256L + b;
                    }
                    i = i + 1;
                }
                if (oversized != 0 || v > maxPayload) {
                    return WebSocketFrame.errorResult("payload too large");
                }
                payloadLen = v;
                header = 10;
            }
        }
        if (payloadLen > maxPayload) {
            return WebSocketFrame.errorResult("payload too large");
        }
        int isControl = 0;
        if (opcode == WebSocketOpcode.CLOSE || opcode == WebSocketOpcode.PING || opcode == WebSocketOpcode.PONG) {
            isControl = 1;
        }
        if (isControl != 0) {
            if (fin == 0) {
                return WebSocketFrame.errorResult("control frame must be FIN");
            }
            if (payloadLen > 125) {
                return WebSocketFrame.errorResult("control payload must be <= 125");
            }
        }
        int maskBytes = 0;
        if (masked != 0) {
            maskBytes = 4;
        }
        int payloadLenInt = 0;
        long pl = payloadLen;
        while (pl > 0L) {
            payloadLenInt = payloadLenInt + 1;
            pl = pl - 1L;
        }
        int total = header + maskBytes + payloadLenInt;
        if (avail < total) {
            return WebSocketFrame.needMoreResult();
        }
        byte[] payload = new byte[payloadLenInt];
        int payloadOff = offset + header + maskBytes;
        int j = 0;
        while (j < payloadLen) {
            payload[j] = buf[payloadOff + j];
            j = j + 1;
        }
        if (masked != 0) {
            int m0 = CryptoBytes.u8(buf[offset + header]);
            int m1 = CryptoBytes.u8(buf[offset + header + 1]);
            int m2 = CryptoBytes.u8(buf[offset + header + 2]);
            int m3 = CryptoBytes.u8(buf[offset + header + 3]);
            j = 0;
            while (j < payloadLen) {
                int mk = 0;
                int mod = j - (j / 4) * 4;
                if (mod == 0) {
                    mk = m0;
                } else {
                    if (mod == 1) {
                        mk = m1;
                    } else {
                        if (mod == 2) {
                            mk = m2;
                        } else {
                            mk = m3;
                        }
                    }
                }
                int pb = CryptoBytes.u8(payload[j]);
                payload[j] = CryptoBytes.toByte(BitOps.xor(pb, mk));
                j = j + 1;
            }
        }
        WebSocketFrame out = new WebSocketFrame();
        out.fin = fin;
        out.opcode = opcode;
        out.payload = payload;
        out.bytesConsumed = total;
        out.ok = 1;
        return out;
    }

    static byte[] encodeServer(int opcode, byte[] payload) {
        if (payload == null) {
            payload = new byte[0];
        }
        int isControl = 0;
        if (opcode == WebSocketOpcode.CLOSE || opcode == WebSocketOpcode.PING || opcode == WebSocketOpcode.PONG) {
            isControl = 1;
        }
        if (isControl != 0) {
            if (payload.length > 125) {
                throw new RuntimeException("control payload must be <= 125");
            }
        }
        int len = payload.length;
        int headerLen = 2;
        if (len >= 126) {
            if (len <= 65535) {
                headerLen = 4;
            } else {
                headerLen = 10;
            }
        }
        byte[] out = new byte[headerLen + len];
        int b0 = BitOps.or(128, BitOps.and(opcode, 15));
        out[0] = CryptoBytes.toByte(b0);
        if (len < 126) {
            out[1] = CryptoBytes.toByte(len);
        } else {
            if (len <= 65535) {
                out[1] = CryptoBytes.toByte(126);
                out[2] = CryptoBytes.toByte(len / 256);
                out[3] = CryptoBytes.toByte(len - (len / 256) * 256);
            } else {
                out[1] = CryptoBytes.toByte(127);
                out[2] = CryptoBytes.toByte(0);
                out[3] = CryptoBytes.toByte(0);
                out[4] = CryptoBytes.toByte(0);
                out[5] = CryptoBytes.toByte(0);
                int n = len;
                out[6] = CryptoBytes.toByte((n / 16777216) % 256);
                out[7] = CryptoBytes.toByte((n / 65536) % 256);
                out[8] = CryptoBytes.toByte((n / 256) % 256);
                out[9] = CryptoBytes.toByte(n % 256);
            }
        }
        int i = 0;
        while (i < len) {
            out[headerLen + i] = payload[i];
            i = i + 1;
        }
        return out;
    }

    static byte[] encodeClose(int code, String reason) {
        if (reason == null) {
            reason = "";
        }
        byte[] reasonBytes = Bytes.fromString(reason);
        int n = 2 + reasonBytes.length;
        if (n > 125) {
            n = 125;
            reasonBytes = CryptoBytes.copyOf(reasonBytes, 123);
        }
        byte[] payload = new byte[n];
        payload[0] = CryptoBytes.toByte((code / 256) % 256);
        payload[1] = CryptoBytes.toByte(code % 256);
        int i = 0;
        while (i < reasonBytes.length && (2 + i) < n) {
            payload[2 + i] = reasonBytes[i];
            i = i + 1;
        }
        return encodeServer(WebSocketOpcode.CLOSE, payload);
    }
}

class WsBytesChunk {
    byte[] data;

    WsBytesChunk() {
        data = new byte[0];
    }

    static WsBytesChunk of(byte[] data) {
        WsBytesChunk c = new WsBytesChunk();
        if (data == null) {
            c.data = new byte[0];
        } else {
            c.data = data;
        }
        return c;
    }
}

class WsConnReadSupplier implements BlockingSupplier {
    WebSocketConnection conn;
    int maxBytes;

    public Object get() {
        byte[] raw = conn.socket.readBytes(maxBytes);
        return WsBytesChunk.of(raw);
    }
}

class WsConnWriteSupplier implements BlockingSupplier {
    WebSocketConnection conn;
    byte[] data;

    public Object get() {
        conn.socket.write(data);
        return null;
    }
}

class WebSocketConnection {
    TcpSocket socket;
    EventLoop loop;
    int state;
    AsyncQueue inbound;
    AsyncQueue outbound;
    int maxMessageBytes;
    int maxPendingMessages;
    String principalName;
    Object userData;
    int pendingOutbound;
    int slow;
    byte[] readBuf;
    int fragActive;
    int fragOpcode;
    byte[] fragBuf;

    WebSocketConnection() {
        socket = null;
        loop = null;
        state = WebSocketState.CONNECTING;
        inbound = null;
        outbound = null;
        maxMessageBytes = 1048576;
        maxPendingMessages = 64;
        principalName = "";
        userData = null;
        pendingOutbound = 0;
        slow = 0;
        readBuf = new byte[0];
        fragActive = 0;
        fragOpcode = 0;
        fragBuf = new byte[0];
    }

    static WebSocketConnection open(TcpSocket socket, EventLoop loop, int maxMessageBytes, int maxPendingMessages) {
        WebSocketConnection c = new WebSocketConnection();
        c.socket = socket;
        c.loop = loop;
        c.inbound = new AsyncQueue(loop);
        c.outbound = new AsyncQueue(loop);
        if (maxMessageBytes > 0) {
            c.maxMessageBytes = maxMessageBytes;
        }
        if (maxPendingMessages > 0) {
            c.maxPendingMessages = maxPendingMessages;
        }
        c.state = WebSocketState.OPEN;
        return c;
    }

    int isOpen() {
        if (state == WebSocketState.OPEN) {
            return 1;
        }
        return 0;
    }

    JnFuture receive() {
        return inbound.take();
    }

    void enqueueOutbound(byte[] frame) {
        if (isOpen() == 0) {
            return;
        }
        if (pendingOutbound >= maxPendingMessages) {
            slow = 1;
            close(1008, "slow consumer");
            return;
        }
        outbound.put(WsBytesChunk.of(frame));
        pendingOutbound = pendingOutbound + 1;
    }

    void sendText(String text) {
        if (text == null) {
            text = "";
        }
        byte[] payload = Bytes.fromString(text);
        if (payload.length > maxMessageBytes) {
            close(1009, "message too big");
            return;
        }
        enqueueOutbound(WebSocketFrameCodec.encodeServer(WebSocketOpcode.TEXT, payload));
    }

    void sendBytes(byte[] data) {
        if (data == null) {
            data = new byte[0];
        }
        if (data.length > maxMessageBytes) {
            close(1009, "message too big");
            return;
        }
        enqueueOutbound(WebSocketFrameCodec.encodeServer(WebSocketOpcode.BINARY, data));
    }

    void close(int code, String reason) {
        if (state == WebSocketState.CLOSED || state == WebSocketState.CLOSING) {
            return;
        }
        state = WebSocketState.CLOSING;
        byte[] frame = WebSocketFrameCodec.encodeClose(code, reason);
        if (pendingOutbound < maxPendingMessages) {
            outbound.put(WsBytesChunk.of(frame));
            pendingOutbound = pendingOutbound + 1;
        }
        state = WebSocketState.CLOSED;
        if (socket != null) {
            socket.close();
        }
    }

    void appendRead(byte[] chunk) {
        if (chunk == null || chunk.length == 0) {
            return;
        }
        readBuf = CryptoBytes.concat(readBuf, chunk);
    }

    byte[] consumeRead(int n) {
        if (n <= 0) {
            return readBuf;
        }
        if (n >= readBuf.length) {
            readBuf = new byte[0];
            return readBuf;
        }
        byte[] rest = new byte[readBuf.length - n];
        int i = 0;
        while (i < rest.length) {
            rest[i] = readBuf[n + i];
            i = i + 1;
        }
        readBuf = rest;
        return readBuf;
    }

    void deliverMessage(int opcode, byte[] payload) {
        if (opcode == WebSocketOpcode.TEXT) {
            if (inbound.storage.size() >= maxPendingMessages) {
                close(1008, "inbound full");
                return;
            }
            inbound.put(WebSocketMessage.textMessage(Bytes.toString(payload)));
        } else {
            if (opcode == WebSocketOpcode.BINARY) {
                if (inbound.storage.size() >= maxPendingMessages) {
                    close(1008, "inbound full");
                    return;
                }
                inbound.put(WebSocketMessage.binaryMessage(payload));
            }
        }
    }

    void handleIncomingBytes(byte[] chunk) {
        if (isOpen() == 0 && state != WebSocketState.CLOSING) {
            return;
        }
        appendRead(chunk);
        int alive = 1;
        while (alive != 0) {
            WebSocketFrame frame = WebSocketFrameCodec.parseOne(readBuf, 0, maxMessageBytes, 1);
            if (frame.needMore != 0) {
                alive = 0;
            } else {
                if (frame.error != 0) {
                    close(1002, frame.errorMessage);
                    alive = 0;
                } else {
                    consumeRead(frame.bytesConsumed);
                    int opcode = frame.opcode;
                    if (opcode == WebSocketOpcode.PING) {
                        enqueueOutbound(WebSocketFrameCodec.encodeServer(WebSocketOpcode.PONG, frame.payload));
                    } else {
                        if (opcode == WebSocketOpcode.PONG) {
                        } else {
                            if (opcode == WebSocketOpcode.CLOSE) {
                                int code = 1000;
                                String reason = "";
                                if (frame.payload.length >= 2) {
                                    code = CryptoBytes.u8(frame.payload[0]) * 256 + CryptoBytes.u8(frame.payload[1]);
                                    if (frame.payload.length > 2) {
                                        byte[] rb = new byte[frame.payload.length - 2];
                                        int i = 0;
                                        while (i < rb.length) {
                                            rb[i] = frame.payload[2 + i];
                                            i = i + 1;
                                        }
                                        reason = Bytes.toString(rb);
                                    }
                                }
                                close(code, reason);
                                alive = 0;
                            } else {
                                if (opcode == WebSocketOpcode.TEXT || opcode == WebSocketOpcode.BINARY) {
                                    if (fragActive != 0) {
                                        close(1002, "unexpected data frame during fragment");
                                        alive = 0;
                                    } else {
                                        if (frame.fin != 0) {
                                            deliverMessage(opcode, frame.payload);
                                        } else {
                                            fragActive = 1;
                                            fragOpcode = opcode;
                                            fragBuf = frame.payload;
                                        }
                                    }
                                } else {
                                    if (opcode == WebSocketOpcode.CONTINUATION) {
                                        if (fragActive == 0) {
                                            close(1002, "unexpected continuation");
                                            alive = 0;
                                        } else {
                                            fragBuf = CryptoBytes.concat(fragBuf, frame.payload);
                                            if (fragBuf.length > maxMessageBytes) {
                                                close(1009, "message too big");
                                                alive = 0;
                                            } else {
                                                if (frame.fin != 0) {
                                                    deliverMessage(fragOpcode, fragBuf);
                                                    fragActive = 0;
                                                    fragOpcode = 0;
                                                    fragBuf = new byte[0];
                                                }
                                            }
                                        }
                                    } else {
                                        close(1002, "unsupported opcode");
                                        alive = 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    void pumpReadOnce() {
        if (socket == null || isOpen() == 0) {
            return;
        }
        /* Sync I/O on the connection thread — avoid EventLoop deadlock. */
        byte[] syncChunk = socket.readBytes(8192);
        if (syncChunk == null || syncChunk.length == 0) {
            close(1001, "eof");
        } else {
            handleIncomingBytes(syncChunk);
        }
    }

    void pumpWriteOnce() {
        if (socket == null) {
            return;
        }
        if (outbound.storage.size() == 0) {
            return;
        }
        Object frameObj = outbound.storage.removeFirst();
        WsBytesChunk held = (WsBytesChunk)frameObj;
        byte[] frame = held.data;
        if (pendingOutbound > 0) {
            pendingOutbound = pendingOutbound - 1;
        }
        socket.write(frame);
    }
}

class WebSocketHub {
    HashMap<String, ArrayList<WebSocketConnection>> rooms;

    WebSocketHub() {
        rooms = new HashMap<String, ArrayList<WebSocketConnection>>();
    }

    void join(String roomId, WebSocketConnection socket) {
        if (roomId == null || socket == null) {
            return;
        }
        ArrayList<WebSocketConnection> list = rooms.get(roomId);
        if (list == null) {
            list = new ArrayList<WebSocketConnection>();
            rooms.put(roomId, list);
        }
        int found = 0;
        int i = 0;
        while (i < list.size()) {
            if (list.get(i) == socket) {
                found = 1;
            }
            i = i + 1;
        }
        if (found == 0) {
            list.add(socket);
        }
    }

    void leave(String roomId, WebSocketConnection socket) {
        if (roomId == null || socket == null) {
            return;
        }
        ArrayList<WebSocketConnection> list = rooms.get(roomId);
        if (list == null) {
            return;
        }
        int i = 0;
        int removed = 0;
        while (i < list.size() && removed == 0) {
            if (list.get(i) == socket) {
                list.removeAt(i);
                removed = 1;
            } else {
                i = i + 1;
            }
        }
        if (list.size() == 0) {
            rooms.remove(roomId);
        }
    }

    void broadcastText(String roomId, String text) {
        if (roomId == null) {
            return;
        }
        ArrayList<WebSocketConnection> list = rooms.get(roomId);
        if (list == null) {
            return;
        }
        int i = 0;
        while (i < list.size()) {
            WebSocketConnection c = list.get(i);
            if (c != null && c.isOpen() != 0) {
                c.sendText(text);
            }
            i = i + 1;
        }
    }
}

class SseEvent {
    String event;
    String data;
    String id;
    int retry;

    SseEvent() {
        event = "";
        data = "";
        id = "";
        retry = -1;
    }

    static SseEvent ofData(String data) {
        SseEvent e = new SseEvent();
        e.data = data;
        return e;
    }
}

class SseStream {
    static String encode(SseEvent ev) {
        if (ev == null) {
            return "\n";
        }
        String out = "";
        if (ev.event != null && ev.event.length() > 0) {
            out = out + "event: " + ev.event + "\n";
        }
        if (ev.id != null && ev.id.length() > 0) {
            out = out + "id: " + ev.id + "\n";
        }
        if (ev.retry >= 0) {
            out = out + "retry: " + String.valueOf(ev.retry) + "\n";
        }
        String data = ev.data;
        if (data == null) {
            data = "";
        }
        int start = 0;
        int i = 0;
        int done = 0;
        while (done == 0) {
            if (i >= data.length()) {
                out = out + "data: " + data.substring(start, i) + "\n";
                done = 1;
            } else {
                int c = data.charAt(i);
                if (c == 10) {
                    out = out + "data: " + data.substring(start, i) + "\n";
                    start = i + 1;
                }
                i = i + 1;
            }
        }
        out = out + "\n";
        return out;
    }
}

class SseSupport {
    static String CONTENT_TYPE = "text/event-stream";
    static String CACHE_CONTROL = "no-cache";
    static String CONNECTION = "keep-alive";

    static void applyHeaders(WebResponse response) {
        if (response == null) {
            return;
        }
        response.setHeader("Content-Type", CONTENT_TYPE);
        response.setHeader("Cache-Control", CACHE_CONTROL);
        response.setHeader("Connection", CONNECTION);
    }

    static void applyHeadersOnContext(WebContext ctx) {
        if (ctx == null) {
            return;
        }
        applyHeaders(ctx.response);
    }

    static String format(SseEvent ev) {
        return SseStream.encode(ev);
    }

    static String formatData(String data) {
        return SseStream.encode(SseEvent.ofData(data));
    }

    static String headersWire() {
        return "Content-Type: " + CONTENT_TYPE + "\r\n"
                + "Cache-Control: " + CACHE_CONTROL + "\r\n"
                + "Connection: " + CONNECTION + "\r\n";
    }
}
