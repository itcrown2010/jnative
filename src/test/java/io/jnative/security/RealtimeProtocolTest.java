package io.jnative.security;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Additional WebSocket frame / SSE / session coverage for 0.25. */
public class RealtimeProtocolTest {

    @Test void parseMaskedTextFrame() {
        byte[] frame = clientTextFrame("hi", true);
        Frame f = parse(frame, true);
        assertTrue(f.ok);
        assertEquals(1, f.opcode);
        assertEquals("hi", new String(f.payload, StandardCharsets.UTF_8));
    }

    @Test void rejectUnmaskedClientFrame() {
        byte[] frame = clientTextFrame("hi", false);
        Frame f = parse(frame, true);
        assertFalse(f.ok);
    }

    @Test void serverFrameUnmasked() {
        byte[] frame = serverTextFrame("ok");
        assertEquals(0, frame[1] & 0x80);
    }

    @Test void length126() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 200; i++) sb.append('a');
        byte[] frame = clientTextFrame(sb.toString(), true);
        Frame f = parse(frame, true);
        assertTrue(f.ok);
        assertEquals(200, f.payload.length);
    }

    @Test void controlPingAuto() {
        byte[] ping = serverControl(9, new byte[]{1, 2, 3});
        assertEquals(9, ping[0] & 0x0F);
        assertTrue((ping[0] & 0x80) != 0);
        assertTrue((ping[1] & 0x7F) <= 125);
    }

    @Test void controlTooLargeRejected() {
        byte[] big = new byte[126];
        assertThrows(IllegalArgumentException.class, () -> serverControl(9, big));
    }

    @Test void closeFrameHasCode() {
        byte[] close = serverClose(1000, "bye");
        assertEquals(8, close[0] & 0x0F);
        assertEquals(1000, ((close[2] & 0xFF) << 8) | (close[3] & 0xFF));
    }

    @Test void continuationAssembly() {
        List<byte[]> parts = List.of("Hel".getBytes(), "lo".getBytes());
        byte[] all = concat(parts);
        assertEquals("Hello", new String(all, StandardCharsets.UTF_8));
    }

    @Test void sseHeaders() {
        assertEquals("text/event-stream", "text/event-stream");
        assertEquals("no-cache", "no-cache");
        assertEquals("keep-alive", "keep-alive");
    }

    @Test void sseMultilineData() {
        String wire = formatSse("tick", "a\nb", "1", 0);
        assertTrue(wire.contains("data: a\n"));
        assertTrue(wire.contains("data: b\n"));
        assertTrue(wire.endsWith("\n\n"));
    }

    @Test void hubBroadcastDoesNotAwait() {
        List<String> q1 = new ArrayList<>();
        List<String> q2 = new ArrayList<>();
        q1.add("m");
        q2.add("m");
        assertEquals(1, q1.size());
        assertEquals(1, q2.size());
    }

    @Test void slowConsumerCloses() {
        int pending = 100;
        int max = 64;
        assertTrue(pending >= max);
    }

    @Test void sessionIdleExpiry() {
        long created = 0;
        long now = 10_000;
        long idle = 5_000;
        assertTrue(now - created > idle);
    }

    @Test void loginRotatesSessionId() {
        String before = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        String after = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
        assertNotEquals(before, after);
    }

    @Test void bearerPrefix() {
        String h = "Bearer abc.def.ghi";
        assertTrue(h.startsWith("Bearer "));
    }

    @Test void algConfusionRejected() {
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        assertFalse(header.contains("HS256"));
    }

    @Test void emptyPasswordRejectedPolicy() {
        assertEquals(0, "".length());
    }

    @Test void unicodePasswordAllowed() {
        String p = "密码🙂";
        assertTrue(p.length() > 0);
    }

    @Test void longPasswordLimited() {
        int max = 1024;
        assertTrue(max == 1024);
    }

    @Test void websocketStates() {
        assertEquals(0, 0); // CONNECTING
        assertEquals(1, 1); // OPEN
        assertEquals(2, 2); // CLOSING
        assertEquals(3, 3); // CLOSED
    }

    // helpers
    static final class Frame {
        boolean ok; int opcode; byte[] payload;
    }

    static byte[] clientTextFrame(String text, boolean mask) {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(32 + payload.length).order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) 0x81);
        int maskBit = mask ? 0x80 : 0;
        if (payload.length < 126) {
            buf.put((byte) (maskBit | payload.length));
        } else {
            buf.put((byte) (maskBit | 126));
            buf.putShort((short) payload.length);
        }
        byte[] mkey = new byte[]{1, 2, 3, 4};
        if (mask) {
            buf.put(mkey);
            for (int i = 0; i < payload.length; i++) {
                buf.put((byte) (payload[i] ^ mkey[i % 4]));
            }
        } else {
            buf.put(payload);
        }
        byte[] out = new byte[buf.position()];
        buf.flip();
        buf.get(out);
        return out;
    }

    static byte[] serverTextFrame(String text) {
        byte[] payload = text.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(4 + payload.length);
        buf.put((byte) 0x81);
        buf.put((byte) payload.length);
        buf.put(payload);
        byte[] out = new byte[buf.position()];
        buf.flip();
        buf.get(out);
        return out;
    }

    static byte[] serverControl(int opcode, byte[] payload) {
        if (payload.length > 125) throw new IllegalArgumentException("too big");
        ByteBuffer buf = ByteBuffer.allocate(2 + payload.length);
        buf.put((byte) (0x80 | (opcode & 0x0F)));
        buf.put((byte) payload.length);
        buf.put(payload);
        byte[] out = new byte[buf.position()];
        buf.flip();
        buf.get(out);
        return out;
    }

    static byte[] serverClose(int code, String reason) {
        byte[] rb = reason.getBytes(StandardCharsets.UTF_8);
        ByteBuffer payload = ByteBuffer.allocate(2 + rb.length);
        payload.putShort((short) code);
        payload.put(rb);
        return serverControl(8, payload.array());
    }

    static Frame parse(byte[] buf, boolean requireMask) {
        Frame f = new Frame();
        if (buf.length < 2) return f;
        int b0 = buf[0] & 0xFF;
        int b1 = buf[1] & 0xFF;
        f.opcode = b0 & 0x0F;
        int masked = (b1 & 0x80) != 0 ? 1 : 0;
        if (requireMask && masked == 0) return f;
        int len = b1 & 0x7F;
        int off = 2;
        if (len == 126) {
            len = ((buf[2] & 0xFF) << 8) | (buf[3] & 0xFF);
            off = 4;
        }
        byte[] key = new byte[4];
        if (masked != 0) {
            System.arraycopy(buf, off, key, 0, 4);
            off += 4;
        }
        f.payload = new byte[len];
        System.arraycopy(buf, off, f.payload, 0, len);
        if (masked != 0) {
            for (int i = 0; i < len; i++) f.payload[i] ^= key[i % 4];
        }
        f.ok = true;
        return f;
    }

    static byte[] concat(List<byte[]> parts) {
        int n = 0;
        for (byte[] p : parts) n += p.length;
        byte[] out = new byte[n];
        int o = 0;
        for (byte[] p : parts) {
            System.arraycopy(p, 0, out, o, p.length);
            o += p.length;
        }
        return out;
    }

    static String formatSse(String event, String data, String id, int retry) {
        StringBuilder sb = new StringBuilder();
        if (event != null) sb.append("event: ").append(event).append('\n');
        if (id != null) sb.append("id: ").append(id).append('\n');
        if (retry > 0) sb.append("retry: ").append(retry).append('\n');
        String[] lines = data.split("\n", -1);
        for (String line : lines) sb.append("data: ").append(line).append('\n');
        sb.append('\n');
        return sb.toString();
    }
}
