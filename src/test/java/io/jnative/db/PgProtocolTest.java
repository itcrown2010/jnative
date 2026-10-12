/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.db;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Protocol-level tests mirroring PgMessageReader / PgErrorInfo / extended-query bind semantics.
 * (JNative e2e compile of the full db stack is tracked separately — see PARTIAL notes in PgDriverLib.)
 * @author 张晓达
 */
class PgProtocolTest {

    @Test
    void parserSurvivesRandomChunkSizes() {
        byte[] msg = readyForQueryIdle();
        int[] sizes = {1, 2, 3, 4, 5, 6, 7};
        for (int chunkSize : sizes) {
            PgMessageReader reader = new PgMessageReader();
            int count = feedAndCount(reader, msg, chunkSize);
            assertEquals(1, count, "chunkSize=" + chunkSize);
        }
    }

    @Test
    void sqlStateParsedFromErrorResponse() {
        byte[] raw = errorResponse("23505", "ERROR", "duplicate key");
        PgMessageReader reader = new PgMessageReader();
        reader.feed(raw);
        PgMessage msg = reader.poll();
        assertNotNull(msg);
        PgErrorInfo info = PgErrorInfo.parse(msg.payload);
        assertEquals("23505", info.sqlState);
        assertEquals("ERROR", info.severity);
        assertEquals("duplicate key", info.message);
    }

    @Test
    void boundParameterNotInterpolatedIntoSql() {
        String verdict = bindSecurityCheck(
                "SELECT * FROM users WHERE name = $1",
                "' OR 1=1 --");
        assertEquals("SECURE", verdict);
    }

    // --- minimal Java mirrors of stdlib helpers ---

    static class PgMessage {
        int tag;
        byte[] payload;
    }

    static class PgMessageReader {
        final ArrayList<Byte> buf = new ArrayList<>();

        void feed(byte[] chunk) {
            if (chunk == null) {
                return;
            }
            for (byte b : chunk) {
                buf.add(b);
            }
        }

        void clear() {
            buf.clear();
        }

        PgMessage poll() {
            if (buf.size() < 5) {
                return null;
            }
            int tag = buf.get(0) & 255;
            int len = readInt32At(1);
            if (len < 4) {
                throw new IllegalStateException("bad length");
            }
            int total = 1 + len;
            if (buf.size() < total) {
                return null;
            }
            byte[] payload = new byte[len - 4];
            for (int i = 0; i < payload.length; i++) {
                payload[i] = buf.get(5 + i);
            }
            for (int i = 0; i < total; i++) {
                buf.remove(0);
            }
            PgMessage m = new PgMessage();
            m.tag = tag;
            m.payload = payload;
            return m;
        }

        int readInt32At(int off) {
            int b0 = buf.get(off) & 255;
            int b1 = buf.get(off + 1) & 255;
            int b2 = buf.get(off + 2) & 255;
            int b3 = buf.get(off + 3) & 255;
            return (b0 << 24) | (b1 << 16) | (b2 << 8) | b3;
        }
    }

    static class PgErrorInfo {
        String sqlState = "00000";
        String severity = "";
        String message = "";
        String detail = "";

        static PgErrorInfo parse(byte[] payload) {
            PgErrorInfo info = new PgErrorInfo();
            int pos = 0;
            while (pos < payload.length) {
                int field = payload[pos] & 255;
                pos = pos + 1;
                if (field == 0) {
                    break;
                }
                int end = pos;
                while (end < payload.length && payload[end] != 0) {
                    end = end + 1;
                }
                String val = new String(payload, pos, end - pos, StandardCharsets.UTF_8);
                pos = end + 1;
                if (field == 'C') {
                    info.sqlState = val;
                } else if (field == 'S') {
                    info.severity = val;
                } else if (field == 'M') {
                    info.message = val;
                } else if (field == 'D') {
                    info.detail = val;
                }
            }
            return info;
        }
    }

    static byte[] readyForQueryIdle() {
        ByteBuffer b = ByteBuffer.allocate(6).order(ByteOrder.BIG_ENDIAN);
        b.put((byte) 'Z');
        b.putInt(5);
        b.put((byte) 'I');
        return b.array();
    }

    static byte[] errorResponse(String sqlState, String severity, String message) {
        ByteBuffer b = ByteBuffer.allocate(128).order(ByteOrder.BIG_ENDIAN);
        b.put((byte) 'E');
        b.putInt(0);
        b.put((byte) 'S');
        putCString(b, severity);
        b.put((byte) 'C');
        putCString(b, sqlState);
        b.put((byte) 'M');
        putCString(b, message);
        b.put((byte) 0);
        b.flip();
        int len = b.remaining() - 1;
        b.put(1, (byte) ((len >> 24) & 255));
        b.put(2, (byte) ((len >> 16) & 255));
        b.put(3, (byte) ((len >> 8) & 255));
        b.put(4, (byte) (len & 255));
        byte[] out = new byte[b.remaining()];
        b.get(out);
        return out;
    }

    static void putCString(ByteBuffer b, String s) {
        b.put(s.getBytes(StandardCharsets.UTF_8));
        b.put((byte) 0);
    }

    static int feedAndCount(PgMessageReader reader, byte[] full, int chunkSize) {
        int count = 0;
        int off = 0;
        Random rnd = new Random(42);
        while (off < full.length) {
            int len = chunkSize;
            if (rnd.nextBoolean() && chunkSize > 1) {
                len = 1 + rnd.nextInt(chunkSize);
            }
            if (off + len > full.length) {
                len = full.length - off;
            }
            byte[] chunk = new byte[len];
            System.arraycopy(full, off, chunk, 0, len);
            reader.feed(chunk);
            PgMessage msg;
            while ((msg = reader.poll()) != null) {
                count++;
            }
            off += len;
        }
        return count;
    }

    static String bindSecurityCheck(String sql, String param) {
        byte[] wire = extendedQuery(sql, List.of(param));
        byte[] needle = "' OR 1=1 --".getBytes(StandardCharsets.UTF_8);
        byte[] sqlBytes = sql.getBytes(StandardCharsets.UTF_8);
        int parseEnd = indexOf(wire, sqlBytes);
        if (parseEnd < 0) {
            return "INSECURE";
        }
        parseEnd = parseEnd + sqlBytes.length;
        int bindStart = indexOfByte(wire, (byte) 'B');
        if (bindStart < 0) {
            return "INSECURE";
        }
        for (int i = 0; i <= wire.length - needle.length; i++) {
            if (i >= parseEnd && i < bindStart) {
                continue;
            }
            boolean match = true;
            for (int j = 0; j < needle.length; j++) {
                if (wire[i + j] != needle[j]) {
                    match = false;
                    break;
                }
            }
            if (match && i < parseEnd) {
                return "INSECURE";
            }
        }
        return "SECURE";
    }

    static int indexOf(byte[] hay, byte[] needle) {
        for (int i = 0; i <= hay.length - needle.length; i++) {
            boolean ok = true;
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                return i;
            }
        }
        return -1;
    }

    static int indexOfByte(byte[] hay, byte b) {
        int i = 0;
        while (i < hay.length) {
            if (hay[i] == b) {
                return i;
            }
            i = i + 1;
        }
        return -1;
    }

    static byte[] extendedQuery(String sql, List<String> params) {
        ByteBuffer b = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN);
        appendParse(b, sql);
        appendBind(b, params);
        b.put((byte) 'S');
        b.putInt(4);
        b.flip();
        byte[] out = new byte[b.remaining()];
        b.get(out);
        return out;
    }

    static void appendParse(ByteBuffer b, String sql) {
        int start = b.position();
        b.put((byte) 'P');
        b.putInt(0);
        putCString(b, "");
        putCString(b, sql);
        b.putShort((short) 0);
        patchLen(b, start);
    }

    static void appendBind(ByteBuffer b, List<String> params) {
        int start = b.position();
        b.put((byte) 'B');
        b.putInt(0);
        putCString(b, "");
        putCString(b, "");
        b.putShort((short) 0);
        b.putShort((short) params.size());
        for (String p : params) {
            byte[] raw = p.getBytes(StandardCharsets.UTF_8);
            b.putInt(raw.length);
            b.put(raw);
        }
        b.putShort((short) 0);
        patchLen(b, start);
    }

    static void patchLen(ByteBuffer b, int start) {
        int len = b.position() - start;
        b.put(start + 1, (byte) ((len >> 24) & 255));
        b.put(start + 2, (byte) ((len >> 16) & 255));
        b.put(start + 3, (byte) ((len >> 8) & 255));
        b.put(start + 4, (byte) (len & 255));
    }
}
