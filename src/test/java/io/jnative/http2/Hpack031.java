package io.jnative.http2;

/** Self-contained HPACK helpers for 0.31 host-side tests (RFC 7540/7541). */
final class Hpack031 {
    private Hpack031() {
    }

    static final int[] HUFFMAN_CODES = {
        0x1ff8,
        0x7fffd8,
        0xfffffe2,
        0xfffffe3,
        0xfffffe4,
        0xfffffe5,
        0xfffffe6,
        0xfffffe7,
        0xfffffe8,
        0xffffea,
        0x3ffffffc,
        0xfffffe9,
        0xfffffea,
        0x3ffffffd,
        0xfffffeb,
        0xfffffec,
        0xfffffed,
        0xfffffee,
        0xfffffef,
        0xffffff0,
        0xffffff1,
        0xffffff2,
        0x3ffffffe,
        0xffffff3,
        0xffffff4,
        0xffffff5,
        0xffffff6,
        0xffffff7,
        0xffffff8,
        0xffffff9,
        0xffffffa,
        0xffffffb,
        0x14,
        0x3f8,
        0x3f9,
        0xffa,
        0x1ff9,
        0x15,
        0xf8,
        0x7fa,
        0x3fa,
        0x3fb,
        0xf9,
        0x7fb,
        0xfa,
        0x16,
        0x17,
        0x18,
        0x0,
        0x1,
        0x2,
        0x19,
        0x1a,
        0x1b,
        0x1c,
        0x1d,
        0x1e,
        0x1f,
        0x5c,
        0xfb,
        0x7ffc,
        0x20,
        0xffb,
        0x3fc,
        0x1ffa,
        0x21,
        0x5d,
        0x5e,
        0x5f,
        0x60,
        0x61,
        0x62,
        0x63,
        0x64,
        0x65,
        0x66,
        0x67,
        0x68,
        0x69,
        0x6a,
        0x6b,
        0x6c,
        0x6d,
        0x6e,
        0x6f,
        0x70,
        0x71,
        0x72,
        0xfc,
        0x73,
        0xfd,
        0x1ffb,
        0x7fff0,
        0x1ffc,
        0x3ffc,
        0x22,
        0x7ffd,
        0x3,
        0x23,
        0x4,
        0x24,
        0x5,
        0x25,
        0x26,
        0x27,
        0x6,
        0x74,
        0x75,
        0x28,
        0x29,
        0x2a,
        0x7,
        0x2b,
        0x76,
        0x2c,
        0x8,
        0x9,
        0x2d,
        0x77,
        0x78,
        0x79,
        0x7a,
        0x7b,
        0x7ffe,
        0x7fc,
        0x3ffd,
        0x1ffd,
        0xffffffc,
        0xfffe6,
        0x3fffd2,
        0xfffe7,
        0xfffe8,
        0x3fffd3,
        0x3fffd4,
        0x3fffd5,
        0x7fffd9,
        0x3fffd6,
        0x7fffda,
        0x7fffdb,
        0x7fffdc,
        0x7fffdd,
        0x7fffde,
        0xffffeb,
        0x7fffdf,
        0xffffec,
        0xffffed,
        0x3fffd7,
        0x7fffe0,
        0xffffee,
        0x7fffe1,
        0x7fffe2,
        0x7fffe3,
        0x7fffe4,
        0x1fffdc,
        0x3fffd8,
        0x7fffe5,
        0x3fffd9,
        0x7fffe6,
        0x7fffe7,
        0xffffef,
        0x3fffda,
        0x1fffdd,
        0xfffe9,
        0x3fffdb,
        0x3fffdc,
        0x7fffe8,
        0x7fffe9,
        0x1fffde,
        0x7fffea,
        0x3fffdd,
        0x3fffde,
        0xfffff0,
        0x1fffdf,
        0x3fffdf,
        0x7fffeb,
        0x7fffec,
        0x1fffe0,
        0x1fffe1,
        0x3fffe0,
        0x1fffe2,
        0x7fffed,
        0x3fffe1,
        0x7fffee,
        0x7fffef,
        0xfffea,
        0x3fffe2,
        0x3fffe3,
        0x3fffe4,
        0x7ffff0,
        0x3fffe5,
        0x3fffe6,
        0x7ffff1,
        0x3ffffe0,
        0x3ffffe1,
        0xfffeb,
        0x7fff1,
        0x3fffe7,
        0x7ffff2,
        0x3fffe8,
        0x1ffffec,
        0x3ffffe2,
        0x3ffffe3,
        0x3ffffe4,
        0x7ffffde,
        0x7ffffdf,
        0x3ffffe5,
        0xfffff1,
        0x1ffffed,
        0x7fff2,
        0x1fffe3,
        0x3ffffe6,
        0x7ffffe0,
        0x7ffffe1,
        0x3ffffe7,
        0x7ffffe2,
        0xfffff2,
        0x1fffe4,
        0x1fffe5,
        0x3ffffe8,
        0x3ffffe9,
        0xffffffd,
        0x7ffffe3,
        0x7ffffe4,
        0x7ffffe5,
        0xfffec,
        0xfffff3,
        0xfffed,
        0x1fffe6,
        0x3fffe9,
        0x1fffe7,
        0x1fffe8,
        0x7ffff3,
        0x3fffea,
        0x3fffeb,
        0x1ffffee,
        0x1ffffef,
        0xfffff4,
        0xfffff5,
        0x3ffffea,
        0x7ffff4,
        0x3ffffeb,
        0x7ffffe6,
        0x3ffffec,
        0x3ffffed,
        0x7ffffe7,
        0x7ffffe8,
        0x7ffffe9,
        0x7ffffea,
        0x7ffffeb,
        0xffffffe,
        0x7ffffec,
        0x7ffffed,
        0x7ffffee,
        0x7ffffef,
        0x7fffff0,
        0x3ffffee,
        0x3fffffff
    };

    static final byte[] HUFFMAN_LENGTHS = {
        (byte) 13,
        (byte) 23,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 24,
        (byte) 30,
        (byte) 28,
        (byte) 28,
        (byte) 30,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 30,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 28,
        (byte) 6,
        (byte) 10,
        (byte) 10,
        (byte) 12,
        (byte) 13,
        (byte) 6,
        (byte) 8,
        (byte) 11,
        (byte) 10,
        (byte) 10,
        (byte) 8,
        (byte) 11,
        (byte) 8,
        (byte) 6,
        (byte) 6,
        (byte) 6,
        (byte) 5,
        (byte) 5,
        (byte) 5,
        (byte) 6,
        (byte) 6,
        (byte) 6,
        (byte) 6,
        (byte) 6,
        (byte) 6,
        (byte) 6,
        (byte) 7,
        (byte) 8,
        (byte) 15,
        (byte) 6,
        (byte) 12,
        (byte) 10,
        (byte) 13,
        (byte) 6,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 8,
        (byte) 7,
        (byte) 8,
        (byte) 13,
        (byte) 19,
        (byte) 13,
        (byte) 14,
        (byte) 6,
        (byte) 15,
        (byte) 5,
        (byte) 6,
        (byte) 5,
        (byte) 6,
        (byte) 5,
        (byte) 6,
        (byte) 6,
        (byte) 6,
        (byte) 5,
        (byte) 7,
        (byte) 7,
        (byte) 6,
        (byte) 6,
        (byte) 6,
        (byte) 5,
        (byte) 6,
        (byte) 7,
        (byte) 6,
        (byte) 5,
        (byte) 5,
        (byte) 6,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 7,
        (byte) 15,
        (byte) 11,
        (byte) 14,
        (byte) 13,
        (byte) 28,
        (byte) 20,
        (byte) 22,
        (byte) 20,
        (byte) 20,
        (byte) 22,
        (byte) 22,
        (byte) 22,
        (byte) 23,
        (byte) 22,
        (byte) 23,
        (byte) 23,
        (byte) 23,
        (byte) 23,
        (byte) 23,
        (byte) 24,
        (byte) 23,
        (byte) 24,
        (byte) 24,
        (byte) 22,
        (byte) 23,
        (byte) 24,
        (byte) 23,
        (byte) 23,
        (byte) 23,
        (byte) 23,
        (byte) 21,
        (byte) 22,
        (byte) 23,
        (byte) 22,
        (byte) 23,
        (byte) 23,
        (byte) 24,
        (byte) 22,
        (byte) 21,
        (byte) 20,
        (byte) 22,
        (byte) 22,
        (byte) 23,
        (byte) 23,
        (byte) 21,
        (byte) 23,
        (byte) 22,
        (byte) 22,
        (byte) 24,
        (byte) 21,
        (byte) 22,
        (byte) 23,
        (byte) 23,
        (byte) 21,
        (byte) 21,
        (byte) 22,
        (byte) 21,
        (byte) 23,
        (byte) 22,
        (byte) 23,
        (byte) 23,
        (byte) 20,
        (byte) 22,
        (byte) 22,
        (byte) 22,
        (byte) 23,
        (byte) 22,
        (byte) 22,
        (byte) 23,
        (byte) 26,
        (byte) 26,
        (byte) 20,
        (byte) 19,
        (byte) 22,
        (byte) 23,
        (byte) 22,
        (byte) 25,
        (byte) 26,
        (byte) 26,
        (byte) 26,
        (byte) 27,
        (byte) 27,
        (byte) 26,
        (byte) 24,
        (byte) 25,
        (byte) 19,
        (byte) 21,
        (byte) 26,
        (byte) 27,
        (byte) 27,
        (byte) 26,
        (byte) 27,
        (byte) 24,
        (byte) 21,
        (byte) 21,
        (byte) 26,
        (byte) 26,
        (byte) 28,
        (byte) 27,
        (byte) 27,
        (byte) 27,
        (byte) 20,
        (byte) 24,
        (byte) 20,
        (byte) 21,
        (byte) 22,
        (byte) 21,
        (byte) 21,
        (byte) 23,
        (byte) 22,
        (byte) 22,
        (byte) 25,
        (byte) 25,
        (byte) 24,
        (byte) 24,
        (byte) 26,
        (byte) 23,
        (byte) 26,
        (byte) 27,
        (byte) 26,
        (byte) 26,
        (byte) 27,
        (byte) 27,
        (byte) 27,
        (byte) 27,
        (byte) 27,
        (byte) 28,
        (byte) 27,
        (byte) 27,
        (byte) 27,
        (byte) 27,
        (byte) 27,
        (byte) 26,
        (byte) 30
    };

    static final class HeaderField {
        final String name;
        final String value;

        HeaderField(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    static final class DecodeException extends RuntimeException {
        DecodeException(String message) {
            super(message);
        }
    }

    private static final String[] STATIC_TABLE = {
            "", "",
            ":authority", "",
            ":method", "GET",
            ":method", "POST",
            ":path", "/",
            ":path", "/index.html",
            ":scheme", "http",
            ":scheme", "https",
            ":status", "200",
            ":status", "204",
            ":status", "206",
            ":status", "304",
            ":status", "400",
            ":status", "404",
            ":status", "500"
    };

    static int entrySize(String name, String value) {
        return 32 + name.length() + value.length();
    }

    static byte[] encodeInteger(int value, int prefixBits, int prefixMax) {
        java.util.List<Byte> out = new java.util.ArrayList<>();
        if (value < prefixMax) {
            out.add((byte) value);
            return toBytes(out);
        }
        out.add((byte) prefixMax);
        int remaining = value - prefixMax;
        while (remaining >= 128) {
            out.add((byte) ((remaining & 127) | 128));
            remaining = remaining / 128;
        }
        out.add((byte) remaining);
        return toBytes(out);
    }

    static int decodeInteger(byte[] data, int offset, int prefixBits, int[] outValue) {
        if (offset >= data.length) {
            return -1;
        }
        int mask = (1 << prefixBits) - 1;
        int value = data[offset] & mask;
        if (value < mask) {
            outValue[0] = value;
            return offset + 1;
        }
        int m = 0;
        int pos = offset + 1;
        int b;
        do {
            if (pos >= data.length) {
                return -1;
            }
            b = data[pos++] & 255;
            value = value + ((b & 127) * (1 << m));
            m = m + 7;
        } while ((b & 128) != 0);
        outValue[0] = value;
        return pos;
    }

    static byte[] encodeIndexed(int index) {
        byte[] raw = encodeInteger(index, 7, 127);
        raw[0] = (byte) (raw[0] | 0x80);
        return raw;
    }

    static int decodeIndexedFirstByte(int b) {
        if ((b & 128) == 0) {
            throw new DecodeException("not indexed");
        }
        int[] val = new int[1];
        int next = decodeInteger(new byte[]{(byte) b}, 0, 7, val);
        if (next < 0) {
            throw new DecodeException("truncated index");
        }
        return val[0];
    }

    static String staticName(int index) {
        if (index <= 0 || index * 2 >= STATIC_TABLE.length) {
            throw new DecodeException("invalid static index: " + index);
        }
        return STATIC_TABLE[index * 2];
    }

    static String staticValue(int index) {
        if (index <= 0 || index * 2 + 1 >= STATIC_TABLE.length) {
            throw new DecodeException("invalid static index: " + index);
        }
        return STATIC_TABLE[index * 2 + 1];
    }

    static byte[] encodeLiteralIndexedName(int nameIndex, String value, boolean indexing) {
        java.util.List<Byte> out = new java.util.ArrayList<>();
        int first = indexing ? 0x40 : 0x00;
        byte[] idx = encodeInteger(nameIndex, 6, 63);
        idx[0] = (byte) (first | (idx[0] & 63));
        append(out, idx);
        append(out, encodeString(value, false));
        return toBytes(out);
    }

    static byte[] encodeLiteralNewName(String name, String value, boolean indexing) {
        java.util.List<Byte> out = new java.util.ArrayList<>();
        out.add((byte) (indexing ? 0x40 : 0x00));
        append(out, encodeString(name, false));
        append(out, encodeString(value, false));
        return toBytes(out);
    }

    static byte[] encodeString(String s, boolean huffman) {
        byte[] raw = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        java.util.List<Byte> out = new java.util.ArrayList<>();
        if (!huffman) {
            append(out, encodeInteger(raw.length, 7, 127));
            for (byte b : raw) {
                out.add(b);
            }
            return toBytes(out);
        }
        byte[] enc = huffmanEncode(raw);
        byte[] lenPrefix = encodeInteger(enc.length, 7, 127);
        lenPrefix[0] = (byte) (lenPrefix[0] | 0x80);
        append(out, lenPrefix);
        for (byte b : enc) {
            out.add(b);
        }
        return toBytes(out);
    }

    static String decodeString(byte[] data, int offset, int[] next) {
        if (offset >= data.length) {
            throw new DecodeException("truncated string");
        }
        boolean huffman = (data[offset] & 0x80) != 0;
        int[] lenArr = new int[1];
        int pos = decodeInteger(data, offset, 7, lenArr);
        if (pos < 0 || pos + lenArr[0] > data.length) {
            throw new DecodeException("truncated string payload");
        }
        byte[] payload = java.util.Arrays.copyOfRange(data, pos, pos + lenArr[0]);
        next[0] = pos + lenArr[0];
        if (!huffman) {
            return new String(payload, java.nio.charset.StandardCharsets.US_ASCII);
        }
        return huffmanDecode(payload);
    }

    static final class DynamicTable {
        private final java.util.List<HeaderField> entries = new java.util.ArrayList<>();
        private int maxSize = 4096;
        private int size;

        void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
            evict();
        }

        int maxSize() {
            return maxSize;
        }

        int size() {
            return size;
        }

        int entryCount() {
            return entries.size();
        }

        void add(String name, String value) {
            int es = entrySize(name, value);
            if (es > maxSize) {
                entries.clear();
                size = 0;
                return;
            }
            evictUntil(maxSize - es);
            entries.add(0, new HeaderField(name, value));
            size = size + es;
        }

        HeaderField get(int index) {
            if (index <= 0 || index > entries.size()) {
                throw new DecodeException("dynamic index out of range: " + index);
            }
            return entries.get(index - 1);
        }

        HeaderField oldest() {
            if (entries.isEmpty()) {
                return null;
            }
            return entries.get(entries.size() - 1);
        }

        private void evictUntil(int target) {
            while (size > target && !entries.isEmpty()) {
                HeaderField removed = entries.remove(entries.size() - 1);
                size = size - entrySize(removed.name, removed.value);
            }
        }

        private void evict() {
            evictUntil(maxSize);
        }
    }

    static final class Decoder {
        private final DynamicTable table = new DynamicTable();

        DynamicTable table() {
            return table;
        }

        HeaderField decodeField(byte[] data, int offset, int[] next) {
            if (offset >= data.length) {
                throw new DecodeException("empty block");
            }
            int b = data[offset] & 255;
            if ((b & 0x80) != 0) {
                int index = decodeIndexedFirstByte(b);
                next[0] = offset + 1;
                if (index == 0) {
                    throw new DecodeException("indexed name must be non-zero");
                }
                if (index <= 61) {
                    return new HeaderField(staticName(index), staticValue(index));
                }
                return table.get(index - 61);
            }
            if ((b & 0x40) != 0) {
                return decodeLiteral(data, offset, 6, true, next);
            }
            if ((b & 0x20) != 0) {
                int[] max = new int[1];
                int pos = decodeInteger(data, offset, 5, max);
                if (pos < 0) {
                    throw new DecodeException("truncated table size");
                }
                table.setMaxSize(max[0]);
                next[0] = pos;
                return null;
            }
            return decodeLiteral(data, offset, 4, false, next);
        }

        private HeaderField decodeLiteral(byte[] data, int offset, int prefixBits, boolean indexing, int[] next) {
            int[] idx = new int[1];
            int pos = decodeInteger(data, offset, prefixBits, idx);
            if (pos < 0) {
                throw new DecodeException("truncated literal");
            }
            String name;
            if (idx[0] != 0) {
                name = staticName(idx[0]);
            } else {
                int[] npos = new int[1];
                name = decodeString(data, pos, npos);
                pos = npos[0];
            }
            int[] vpos = new int[1];
            String value = decodeString(data, pos, vpos);
            pos = vpos[0];
            if (indexing) {
                table.add(name, value);
            }
            next[0] = pos;
            return new HeaderField(name, value);
        }
    }

    static byte[] huffmanEncode(byte[] input) {
        long current = 0;
        int n = 0;
        java.util.List<Byte> out = new java.util.ArrayList<>();
        for (byte raw : input) {
            int sym = raw & 255;
            int code = HUFFMAN_CODES[sym];
            int bits = HUFFMAN_LENGTHS[sym] & 255;
            current = (current << bits) | code;
            n = n + bits;
            while (n >= 8) {
                n = n - 8;
                out.add((byte) (current >> n));
            }
        }
        if (n > 0) {
            current = current << (8 - n);
            current = current | (0xFF >>> n);
            out.add((byte) current);
        }
        return toBytes(out);
    }

    static String huffmanDecode(byte[] input) {
        Node root = HUFFMAN_ROOT;
        Node node = root;
        StringBuilder sb = new StringBuilder();
        for (byte b : input) {
            for (int i = 7; i >= 0; i--) {
                int bit = (b >> i) & 1;
                node = bit == 0 ? node.left : node.right;
                if (node == null) {
                    throw new DecodeException("invalid huffman code");
                }
                if (node.symbol >= 0) {
                    if (node.symbol == 256) {
                        return sb.toString();
                    }
                    sb.append((char) node.symbol);
                    node = root;
                }
            }
        }
        return sb.toString();
    }

    private static final class Node {
        Node left;
        Node right;
        final int symbol;

        Node(Node left, Node right, int symbol) {
            this.left = left;
            this.right = right;
            this.symbol = symbol;
        }
    }

    private static final Node HUFFMAN_ROOT = buildHuffmanTree();

    private static Node buildHuffmanTree() {
        Node root = new Node(null, null, -1);
        for (int sym = 0; sym < 257; sym++) {
            int code = HUFFMAN_CODES[sym];
            int bits = HUFFMAN_LENGTHS[sym] & 255;
            Node cur = root;
            for (int i = bits - 1; i > 0; i--) {
                int bit = (code >> i) & 1;
                if (bit == 0) {
                    if (cur.left == null) {
                        cur.left = new Node(null, null, -1);
                    }
                    cur = cur.left;
                } else {
                    if (cur.right == null) {
                        cur.right = new Node(null, null, -1);
                    }
                    cur = cur.right;
                }
            }
            if ((code & 1) == 0) {
                cur.left = new Node(null, null, sym);
            } else {
                cur.right = new Node(null, null, sym);
            }
        }
        return root;
    }

    private static void append(java.util.List<Byte> out, byte[] bytes) {
        for (byte b : bytes) {
            out.add(b);
        }
    }

    private static byte[] toBytes(java.util.List<Byte> out) {
        byte[] arr = new byte[out.size()];
        for (int i = 0; i < out.size(); i++) {
            arr[i] = out.get(i);
        }
        return arr;
    }
}
