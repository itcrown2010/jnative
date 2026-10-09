package io.jnative.http2;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Host-side HTTP/2 framing + HPACK tests (no OpenSSL).
 * @author 张晓达
 */
class Http2Hpack031Test {

    @Test
    void frameRoundtripData() {
        assertFrameRoundtrip(new Http2Frame031.Frame(
                Http2Frame031.FrameType.DATA, 0x01, 1, "hello".getBytes()));
    }

    @Test
    void frameRoundtripHeaders() {
        assertFrameRoundtrip(new Http2Frame031.Frame(
                Http2Frame031.FrameType.HEADERS, 0x04, 3, new byte[]{(byte) 0x82, (byte) 0x84, (byte) 0x86}));
    }

    @Test
    void frameRoundtripPriority() {
        byte[] payload = new byte[]{
                0x00, 0x00, 0x00, 0x00, // stream dep
                10 // weight
        };
        assertFrameRoundtrip(new Http2Frame031.Frame(
                Http2Frame031.FrameType.PRIORITY, 0, 5, payload));
    }

    @Test
    void frameRoundtripRstStream() {
        byte[] payload = new byte[]{0, 0, 0, 8}; // CANCEL
        assertFrameRoundtrip(new Http2Frame031.Frame(
                Http2Frame031.FrameType.RST_STREAM, 0, 7, payload));
    }

    @Test
    void frameRoundtripSettings() {
        byte[] payload = new byte[]{0, 4, 0, 0, (byte) 255, (byte) 255}; // INITIAL_WINDOW_SIZE
        assertFrameRoundtrip(new Http2Frame031.Frame(
                Http2Frame031.FrameType.SETTINGS, 0, 0, payload));
    }

    @Test
    void frameRoundtripPushPromise() {
        byte[] payload = new byte[]{0, 0, 0, 4, (byte) 0x82}; // promised stream 4 + indexed GET
        assertFrameRoundtrip(new Http2Frame031.Frame(
                Http2Frame031.FrameType.PUSH_PROMISE, 0x04, 1, payload));
    }

    @Test
    void frameRoundtripPing() {
        byte[] payload = new byte[]{1, 2, 3, 4, 5, 6, 7, 8};
        assertFrameRoundtrip(new Http2Frame031.Frame(
                Http2Frame031.FrameType.PING, 0, 0, payload));
    }

    @Test
    void frameRoundtripGoaway() {
        byte[] payload = new byte[8];
        assertFrameRoundtrip(new Http2Frame031.Frame(
                Http2Frame031.FrameType.GOAWAY, 0, 0, payload));
    }

    @Test
    void frameRoundtripWindowUpdate() {
        byte[] payload = new byte[]{0, 0, 0, 1};
        assertFrameRoundtrip(new Http2Frame031.Frame(
                Http2Frame031.FrameType.WINDOW_UPDATE, 0, 1, payload));
    }

    @Test
    void frameRoundtripContinuation() {
        assertFrameRoundtrip(new Http2Frame031.Frame(
                Http2Frame031.FrameType.CONTINUATION, 0x04, 1, new byte[]{0x41, 0x0a, 'x'}));
    }

    @Test
    void malformedFrameTooShortHeaderReturnsNull() {
        assertNull(Http2Frame031.decode(new byte[]{0, 0}, 0));
        assertNull(Http2Frame031.decode(new byte[8], 0));
    }

    @Test
    void malformedFramePayloadTruncatedReturnsNull() {
        byte[] partial = new byte[]{0, 0, 5, 0, 0, 0, 0, 0, 1, 1, 2};
        assertNull(Http2Frame031.decode(partial, 0));
    }

    @Test
    void hpackIntegerSmallValues() {
        assertArrayEquals(new byte[]{2}, Hpack031.encodeInteger(2, 5, 31));
        int[] out = new int[1];
        assertEquals(1, Hpack031.decodeInteger(new byte[]{2}, 0, 5, out));
        assertEquals(2, out[0]);
    }

    @Test
    void hpackIntegerAbove127() {
        byte[] enc = Hpack031.encodeInteger(200, 7, 127);
        assertTrue(enc.length > 1);
        int[] out = new int[1];
        int next = Hpack031.decodeInteger(enc, 0, 7, out);
        assertTrue(next > 0);
        assertEquals(200, out[0]);
    }

    @Test
    void hpackIntegerLargeValue() {
        byte[] enc = Hpack031.encodeInteger(1337, 7, 127);
        int[] out = new int[1];
        Hpack031.decodeInteger(enc, 0, 7, out);
        assertEquals(1337, out[0]);
    }

    @Test
    void hpackStaticTableIndexedMethodGet() {
        byte[] enc = Hpack031.encodeIndexed(2);
        assertEquals(0x82, enc[0] & 255);
        Hpack031.Decoder dec = new Hpack031.Decoder();
        int[] next = new int[1];
        Hpack031.HeaderField field = dec.decodeField(enc, 0, next);
        assertEquals(":method", field.name);
        assertEquals("GET", field.value);
    }

    @Test
    void hpackStaticTableIndexedPathRoot() {
        byte[] enc = Hpack031.encodeIndexed(4);
        Hpack031.Decoder dec = new Hpack031.Decoder();
        int[] next = new int[1];
        Hpack031.HeaderField field = dec.decodeField(enc, 0, next);
        assertEquals(":path", field.name);
        assertEquals("/", field.value);
    }

    @Test
    void hpackStaticTableIndexedStatus200() {
        byte[] enc = Hpack031.encodeIndexed(8);
        Hpack031.Decoder dec = new Hpack031.Decoder();
        int[] next = new int[1];
        Hpack031.HeaderField field = dec.decodeField(enc, 0, next);
        assertEquals(":status", field.name);
        assertEquals("200", field.value);
    }

    @Test
    void hpackHuffmanRoundtripExampleHost() {
        String sample = "www.example.com";
        byte[] enc = Hpack031.huffmanEncode(sample.getBytes());
        assertEquals(sample, Hpack031.huffmanDecode(enc));
    }

    @Test
    void hpackHuffmanRoundtripCustomKeyValue() {
        assertHuffmanRoundtrip("custom-key");
        assertHuffmanRoundtrip("custom-value");
        assertHuffmanRoundtrip("accept-encoding");
    }

    @Test
    void hpackHuffmanEncodedStringLiteral() {
        byte[] lit = Hpack031.encodeString("www.example.com", true);
        int[] next = new int[1];
        assertEquals("www.example.com", Hpack031.decodeString(lit, 0, next));
    }

    @Test
    void dynamicTableEvictsOldestWhenSizeExceeded() {
        Hpack031.DynamicTable table = new Hpack031.DynamicTable();
        table.setMaxSize(100);
        table.add("alpha", "one-two-three");
        table.add("beta", "two-three-four");
        assertEquals("alpha", table.oldest().name);
        table.add("gamma", "three-four-five-six");
        assertTrue(table.size() <= 100);
        assertNotEquals("alpha", table.oldest().name);
    }

    @Test
    void dynamicTableSingleOversizedEntryClearsTable() {
        Hpack031.DynamicTable table = new Hpack031.DynamicTable();
        table.setMaxSize(64);
        table.add("x", "y");
        assertEquals(1, table.entryCount());
        table.add("very-long-name-that-exceeds", "very-long-value-that-exceeds-limit");
        assertEquals(0, table.entryCount());
        assertEquals(0, table.size());
    }

    @Test
    void invalidEncodingRejected() {
        assertThrows(Hpack031.DecodeException.class, () -> Hpack031.decodeIndexedFirstByte(0x00));
        assertEquals(-1, Hpack031.decodeInteger(new byte[]{127}, 0, 7, new int[1]));
        assertThrows(Hpack031.DecodeException.class, () -> {
            Hpack031.Decoder dec = new Hpack031.Decoder();
            dec.decodeField(new byte[]{(byte) 0x80}, 0, new int[1]);
        });
    }

    @Test
    void flowControlConnectionAndStreamWindowMath() {
        Http2Frame031.FlowControl fc = new Http2Frame031.FlowControl();
        fc.initStream(1, 1000);
        fc.consume(1, 400);
        assertEquals(600, fc.streamWindow(1));
        fc.windowUpdate(1, 200);
        assertEquals(800, fc.streamWindow(1));
        fc.consume(0, 100);
        assertEquals(65535 - 100, fc.connectionWindow);
        fc.windowUpdate(0, 50);
        assertEquals(65535 - 50, fc.connectionWindow);
    }

    @Test
    void flowControlExceededThrows() {
        Http2Frame031.FlowControl fc = new Http2Frame031.FlowControl();
        fc.initStream(3, 10);
        assertThrows(IllegalStateException.class, () -> fc.consume(3, 11));
    }

    @Test
    void streamStateIdleOpenHalfClosedClosed() {
        Http2Frame031.StreamStateMachine sm = new Http2Frame031.StreamStateMachine();
        assertEquals(Http2Frame031.StreamState.IDLE, sm.state(1));
        sm.onHeaders(1, false);
        assertEquals(Http2Frame031.StreamState.OPEN, sm.state(1));
        sm.onHeadersReceived(1, true);
        assertEquals(Http2Frame031.StreamState.HALF_CLOSED_REMOTE, sm.state(1));
        sm.onHeaders(1, true);
        assertEquals(Http2Frame031.StreamState.CLOSED, sm.state(1));
    }

    @Test
    void rstStreamIsolatesOneStream() {
        Http2Frame031.Session session = new Http2Frame031.Session();
        int a = session.allocateStream();
        int b = session.allocateStream();
        session.rstStream(a);
        assertTrue(session.streams().isClosed(a));
        assertFalse(session.streams().isClosed(b));
        assertEquals(Http2Frame031.StreamState.OPEN, session.streams().state(b));
    }

    @Test
    void goawayRefusesNewStreams() {
        Http2Frame031.Session session = new Http2Frame031.Session();
        session.allocateStream();
        session.sendGoAway();
        assertThrows(IllegalStateException.class, session::allocateStream);
    }

    private static void assertFrameRoundtrip(Http2Frame031.Frame original) {
        Http2Frame031.Frame decoded = Http2Frame031.roundtrip(original);
        assertNotNull(decoded);
        assertEquals(original.type, decoded.type);
        assertEquals(original.flags, decoded.flags);
        assertEquals(original.streamId, decoded.streamId);
        assertArrayEquals(original.payload, decoded.payload);
    }

    private static void assertHuffmanRoundtrip(String s) {
        byte[] enc = Hpack031.huffmanEncode(s.getBytes());
        assertEquals(s, Hpack031.huffmanDecode(enc));
    }
}
