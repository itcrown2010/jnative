// JNative Crypto (0.25) — CSPRNG, SHA-1/256, HMAC, Base64, ConstantTime, PBKDF2, JWT HS256.
// SHA-1 is ONLY for WebSocket handshake. Passwords use PBKDF2-HMAC-SHA256 (Argon2id planned).

class CryptoException extends RuntimeException {
    CryptoException(String message) {
        super(message);
    }
}

class CryptoInt {
    int value;

    CryptoInt(int value) {
        this.value = value;
    }
}

class CryptoParse {
    static String digitString(int digit) {
        if (digit == 0) {
            return "0";
        }
        if (digit == 1) {
            return "1";
        }
        if (digit == 2) {
            return "2";
        }
        if (digit == 3) {
            return "3";
        }
        if (digit == 4) {
            return "4";
        }
        if (digit == 5) {
            return "5";
        }
        if (digit == 6) {
            return "6";
        }
        if (digit == 7) {
            return "7";
        }
        if (digit == 8) {
            return "8";
        }
        return "9";
    }

    static int intFromString(String s) {
        if (s == null || s.length() == 0) {
            return 0;
        }
        int n = 0;
        int neg = 0;
        int i = 0;
        if (s.charAt(0) == 45) {
            neg = 1;
            i = 1;
        }
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                return 0;
            }
            n = n * 10 + (c - 48);
            i = i + 1;
        }
        if (neg != 0) {
            n = -n;
        }
        return n;
    }

    static long longFromString(String s) {
        if (s == null || s.length() == 0) {
            return 0L;
        }
        long n = 0L;
        int neg = 0;
        int i = 0;
        if (s.charAt(0) == 45) {
            neg = 1;
            i = 1;
        }
        while (i < s.length()) {
            int c = s.charAt(i);
            if (c < 48 || c > 57) {
                return 0L;
            }
            n = n * 10L + (c - 48);
            i = i + 1;
        }
        if (neg != 0) {
            n = -n;
        }
        return n;
    }

    static String longToString(long v) {
        if (v == 0L) {
            return "0";
        }
        String out = "";
        int neg = 0;
        if (v < 0L) {
            neg = 1;
            v = -v;
        }
        while (v > 0L) {
            long rem = v - (v / 10L) * 10L;
            int digit = 0;
            int i = 0;
            while (i < rem) {
                digit = digit + 1;
                i = i + 1;
            }
            out = digitString(digit) + out;
            v = v / 10L;
        }
        if (neg != 0) {
            out = "-" + out;
        }
        return out;
    }
}

class BitOps {
    @Native(library = "jnative_crypto", symbol = "jnative_bit_and")
    static native int and(int a, int b);

    @Native(library = "jnative_crypto", symbol = "jnative_bit_or")
    static native int or(int a, int b);

    @Native(library = "jnative_crypto", symbol = "jnative_bit_xor")
    static native int xor(int a, int b);

    @Native(library = "jnative_crypto", symbol = "jnative_bit_not")
    static native int not(int a);

    @Native(library = "jnative_crypto", symbol = "jnative_bit_shl")
    static native int shl(int a, int n);

    @Native(library = "jnative_crypto", symbol = "jnative_bit_ushr")
    static native int ushr(int a, int n);

    @Native(library = "jnative_crypto", symbol = "jnative_bit_and64")
    static native long and64(long a, long b);

    @Native(library = "jnative_crypto", symbol = "jnative_bit_or64")
    static native long or64(long a, long b);

    @Native(library = "jnative_crypto", symbol = "jnative_bit_xor64")
    static native long xor64(long a, long b);

    @Native(library = "jnative_crypto", symbol = "jnative_bit_not64")
    static native long not64(long a);

    @Native(library = "jnative_crypto", symbol = "jnative_bit_shl64")
    static native long shl64(long a, int n);

    @Native(library = "jnative_crypto", symbol = "jnative_bit_ushr64")
    static native long ushr64(long a, int n);

    @Native(library = "jnative_crypto", symbol = "jnative_bit_shr64")
    static native long shr64(long a, int n);
}

class SecureRandomNative {
    @Native(library = "jnative_crypto", symbol = "jnative_secure_random")
    static native int fill(NativePtr out, int len);
}

class SecureRandom {
    static void nextBytes(byte[] out) {
        if (out == null || out.length == 0) {
            return;
        }
        long nbytes = 0L;
        int n = out.length;
        int z = 0;
        while (z < n) {
            nbytes = nbytes + 1L;
            z = z + 1;
        }
        NativePtr p = NativeMemory.allocate(nbytes);
        int rc = SecureRandomNative.fill(p, n);
        if (rc != 0) {
            NativeMemory.free(p);
            throw new CryptoException("secure random failed: " + String.valueOf(rc));
        }
        long off = 0L;
        int i = 0;
        while (i + 3 < n) {
            int w = NativeMemory.getInt(p, off);
            out[i] = CryptoBytes.toByte(BitOps.and(w, 255));
            out[i + 1] = CryptoBytes.toByte(BitOps.and(BitOps.ushr(w, 8), 255));
            out[i + 2] = CryptoBytes.toByte(BitOps.and(BitOps.ushr(w, 16), 255));
            out[i + 3] = CryptoBytes.toByte(BitOps.and(BitOps.ushr(w, 24), 255));
            i = i + 4;
            off = off + 4L;
        }
        if (i < n) {
            int w2 = NativeMemory.getInt(p, off);
            while (i < n) {
                int shift = (i % 4) * 8;
                out[i] = CryptoBytes.toByte(BitOps.and(BitOps.ushr(w2, shift), 255));
                i = i + 1;
            }
        }
        NativeMemory.free(p);
    }

    static byte[] bytes(int count) {
        if (count < 0) {
            throw new CryptoException("negative length");
        }
        byte[] out = new byte[count];
        nextBytes(out);
        return out;
    }

    static String tokenHex(int nbytes) {
        return CryptoHex.encode(bytes(nbytes));
    }

    static String sessionId() {
        return tokenHex(16);
    }
}

class CryptoHex {
    static String encode(byte[] data) {
        if (data == null) {
            return "";
        }
        String out = "";
        int i = 0;
        while (i < data.length) {
            int v = CryptoBytes.u8(data[i]);
            out = out + nibble(BitOps.ushr(v, 4)) + nibble(BitOps.and(v, 15));
            i = i + 1;
        }
        return out;
    }

    static String nibble(int v) {
        if (v == 0) { return "0"; }
        if (v == 1) { return "1"; }
        if (v == 2) { return "2"; }
        if (v == 3) { return "3"; }
        if (v == 4) { return "4"; }
        if (v == 5) { return "5"; }
        if (v == 6) { return "6"; }
        if (v == 7) { return "7"; }
        if (v == 8) { return "8"; }
        if (v == 9) { return "9"; }
        if (v == 10) { return "a"; }
        if (v == 11) { return "b"; }
        if (v == 12) { return "c"; }
        if (v == 13) { return "d"; }
        if (v == 14) { return "e"; }
        return "f";
    }

    static int fromNibble(String c) {
        if (c.equals("0")) { return 0; }
        if (c.equals("1")) { return 1; }
        if (c.equals("2")) { return 2; }
        if (c.equals("3")) { return 3; }
        if (c.equals("4")) { return 4; }
        if (c.equals("5")) { return 5; }
        if (c.equals("6")) { return 6; }
        if (c.equals("7")) { return 7; }
        if (c.equals("8")) { return 8; }
        if (c.equals("9")) { return 9; }
        if (c.equals("a") || c.equals("A")) { return 10; }
        if (c.equals("b") || c.equals("B")) { return 11; }
        if (c.equals("c") || c.equals("C")) { return 12; }
        if (c.equals("d") || c.equals("D")) { return 13; }
        if (c.equals("e") || c.equals("E")) { return 14; }
        if (c.equals("f") || c.equals("F")) { return 15; }
        throw new CryptoException("invalid hex");
    }

    static byte[] decode(String s) {
        if (s == null || s.length() == 0) {
            return new byte[0];
        }
        if ((s.length() % 2) != 0) {
            throw new CryptoException("odd hex length");
        }
        byte[] out = new byte[s.length() / 2];
        int i = 0;
        while (i < out.length) {
            int hi = fromNibble(s.substring(i * 2, i * 2 + 1));
            int lo = fromNibble(s.substring(i * 2 + 1, i * 2 + 2));
            out[i] = CryptoBytes.toByte(hi * 16 + lo);
            i = i + 1;
        }
        return out;
    }
}

class CryptoBytes {
    static int u8(byte b) {
        int v = b;
        if (v < 0) {
            return v + 256;
        }
        return v;
    }

    static byte toByte(int v) {
        int x = v % 256;
        if (x < 0) {
            x = x + 256;
        }
        if (x > 127) {
            return x - 256;
        }
        return x;
    }

    static byte[] concat(byte[] a, byte[] b) {
        if (a == null) {
            a = new byte[0];
        }
        if (b == null) {
            b = new byte[0];
        }
        byte[] out = new byte[a.length + b.length];
        int i = 0;
        while (i < a.length) {
            out[i] = a[i];
            i = i + 1;
        }
        int j = 0;
        while (j < b.length) {
            out[a.length + j] = b[j];
            j = j + 1;
        }
        return out;
    }

    static byte[] copyOf(byte[] src, int n) {
        byte[] out = new byte[n];
        int i = 0;
        while (i < n && i < src.length) {
            out[i] = src[i];
            i = i + 1;
        }
        return out;
    }

    static void putInt32BE(byte[] buf, int off, int v) {
        buf[off] = toByte(BitOps.and(BitOps.ushr(v, 24), 255));
        buf[off + 1] = toByte(BitOps.and(BitOps.ushr(v, 16), 255));
        buf[off + 2] = toByte(BitOps.and(BitOps.ushr(v, 8), 255));
        buf[off + 3] = toByte(BitOps.and(v, 255));
    }
}

class Base64Codec {
    static String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    static String urlAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    static String encode(byte[] data) {
        return encodeWith(data, alphabet, 1);
    }

    static String encodeUrl(byte[] data) {
        return encodeWith(data, urlAlphabet, 0);
    }

    static String encodeWith(byte[] data, String alpha, int pad) {
        if (data == null || data.length == 0) {
            return "";
        }
        String out = "";
        int i = 0;
        while (i + 2 < data.length) {
            int b0 = CryptoBytes.u8(data[i]);
            int b1 = CryptoBytes.u8(data[i + 1]);
            int b2 = CryptoBytes.u8(data[i + 2]);
            int n = b0 * 65536 + b1 * 256 + b2;
            out = out + ch(alpha, (n / 262144) % 64);
            out = out + ch(alpha, (n / 4096) % 64);
            out = out + ch(alpha, (n / 64) % 64);
            out = out + ch(alpha, n % 64);
            i = i + 3;
        }
        int rem = data.length - i;
        if (rem == 1) {
            int b0 = CryptoBytes.u8(data[i]);
            int n = b0 * 65536;
            out = out + ch(alpha, (n / 262144) % 64);
            out = out + ch(alpha, (n / 4096) % 64);
            if (pad != 0) {
                out = out + "==";
            }
        } else if (rem == 2) {
            int b0 = CryptoBytes.u8(data[i]);
            int b1 = CryptoBytes.u8(data[i + 1]);
            int n = b0 * 65536 + b1 * 256;
            out = out + ch(alpha, (n / 262144) % 64);
            out = out + ch(alpha, (n / 4096) % 64);
            out = out + ch(alpha, (n / 64) % 64);
            if (pad != 0) {
                out = out + "=";
            }
        }
        return out;
    }

    static byte[] decode(String s) {
        if (s == null || s.length() == 0) {
            return new byte[0];
        }
        ArrayList<Object> vals = new ArrayList<Object>();
        int i = 0;
        int v0 = 0;
        int v1 = 0;
        int v2 = 0;
        int stage = 0;
        while (i < s.length()) {
            String c = s.substring(i, i + 1);
            i = i + 1;
            if (c.equals("\n") || c.equals("\r") || c.equals(" ")) {
            } else if (c.equals("=")) {
                if (stage == 2) {
                    int n = v0 * 262144 + v1 * 4096;
                    vals.add(new CryptoInt(n / 65536));
                    stage = 0;
                } else if (stage == 3) {
                    int n = v0 * 262144 + v1 * 4096 + v2 * 64;
                    vals.add(new CryptoInt(n / 65536));
                    vals.add(new CryptoInt((n / 256) % 256));
                    stage = 0;
                }
            } else {
                int idx = indexOf(c);
                if (stage == 0) {
                    v0 = idx;
                    stage = 1;
                } else if (stage == 1) {
                    v1 = idx;
                    stage = 2;
                } else if (stage == 2) {
                    v2 = idx;
                    stage = 3;
                } else {
                    int v3 = idx;
                    int n = v0 * 262144 + v1 * 4096 + v2 * 64 + v3;
                    vals.add(new CryptoInt(n / 65536));
                    vals.add(new CryptoInt((n / 256) % 256));
                    vals.add(new CryptoInt(n % 256));
                    stage = 0;
                }
            }
        }
        byte[] out = new byte[vals.size()];
        i = 0;
        while (i < vals.size()) {
            CryptoInt bi = (CryptoInt)vals.get(i);
            out[i] = CryptoBytes.toByte(bi.value);
            i = i + 1;
        }
        return out;
    }

    static String ch(String alpha, int idx) {
        int i = idx % 64;
        if (i < 0) {
            i = i + 64;
        }
        return alpha.substring(i, i + 1);
    }

    static int indexOf(String c) {
        int i = 0;
        while (i < alphabet.length()) {
            if (alphabet.substring(i, i + 1).equals(c)) {
                return i;
            }
            i = i + 1;
        }
        i = 0;
        while (i < urlAlphabet.length()) {
            if (urlAlphabet.substring(i, i + 1).equals(c)) {
                return i;
            }
            i = i + 1;
        }
        return 0;
    }
}

class ConstantTime {
    static int equalBytes(byte[] a, byte[] b) {
        if (a == null || b == null) {
            return 0;
        }
        int diff = BitOps.xor(a.length, b.length);
        int n = a.length;
        if (b.length < n) {
            n = b.length;
        }
        int i = 0;
        while (i < n) {
            diff = BitOps.or(diff, BitOps.xor(CryptoBytes.u8(a[i]), CryptoBytes.u8(b[i])));
            i = i + 1;
        }
        if (diff == 0) {
            return 1;
        }
        return 0;
    }

    static int equalsString(String a, String b) {
        if (a == null || b == null) {
            return 0;
        }
        return equalBytes(Bytes.fromString(a), Bytes.fromString(b));
    }
}

class Sha1 {
    static byte[] hash(byte[] message) {
        if (message == null) {
            message = new byte[0];
        }
        int[] h = new int[5];
        h[0] = 1732584193;
        h[1] = -271733879;
        h[2] = -1732584194;
        h[3] = 271733878;
        h[4] = -1009589776;
        int bitLenHigh = 0;
        int bitLenLow = message.length * 8;
        int paddedLen = message.length + 1;
        while ((paddedLen % 64) != 56) {
            paddedLen = paddedLen + 1;
        }
        paddedLen = paddedLen + 8;
        byte[] padded = new byte[paddedLen];
        int i = 0;
        while (i < message.length) {
            padded[i] = message[i];
            i = i + 1;
        }
        padded[message.length] = -128;
        CryptoBytes.putInt32BE(padded, paddedLen - 8, bitLenHigh);
        CryptoBytes.putInt32BE(padded, paddedLen - 4, bitLenLow);
        int off = 0;
        while (off < paddedLen) {
            int[] w = new int[80];
            int t = 0;
            while (t < 16) {
                int b0 = CryptoBytes.u8(padded[off + t * 4]);
                int b1 = CryptoBytes.u8(padded[off + t * 4 + 1]);
                int b2 = CryptoBytes.u8(padded[off + t * 4 + 2]);
                int b3 = CryptoBytes.u8(padded[off + t * 4 + 3]);
                w[t] = BitOps.or(BitOps.or(BitOps.or(
                        BitOps.shl(b0, 24),
                        BitOps.shl(b1, 16)),
                        BitOps.shl(b2, 8)),
                        b3);
                t = t + 1;
            }
            t = 16;
            while (t < 80) {
                int x = BitOps.xor(BitOps.xor(BitOps.xor(w[t - 3], w[t - 8]), w[t - 14]), w[t - 16]);
                w[t] = rotl(x, 1);
                t = t + 1;
            }
            int a = h[0];
            int b = h[1];
            int c = h[2];
            int d = h[3];
            int e = h[4];
            t = 0;
            while (t < 80) {
                int f = 0;
                int k = 0;
                if (t < 20) {
                    f = BitOps.or(BitOps.and(b, c), BitOps.and(BitOps.not(b), d));
                    k = 1518500249;
                } else if (t < 40) {
                    f = BitOps.xor(b, BitOps.xor(c, d));
                    k = 1859775393;
                } else if (t < 60) {
                    f = BitOps.or(BitOps.or(BitOps.and(b, c), BitOps.and(b, d)), BitOps.and(c, d));
                    k = -1894007588;
                } else {
                    f = BitOps.xor(b, BitOps.xor(c, d));
                    k = -899497514;
                }
                int temp = rotl(a, 5) + f + e + k + w[t];
                e = d;
                d = c;
                c = rotl(b, 30);
                b = a;
                a = temp;
                t = t + 1;
            }
            h[0] = h[0] + a;
            h[1] = h[1] + b;
            h[2] = h[2] + c;
            h[3] = h[3] + d;
            h[4] = h[4] + e;
            off = off + 64;
        }
        byte[] out = new byte[20];
        CryptoBytes.putInt32BE(out, 0, h[0]);
        CryptoBytes.putInt32BE(out, 4, h[1]);
        CryptoBytes.putInt32BE(out, 8, h[2]);
        CryptoBytes.putInt32BE(out, 12, h[3]);
        CryptoBytes.putInt32BE(out, 16, h[4]);
        return out;
    }

    static int rotl(int v, int n) {
        return BitOps.or(BitOps.shl(v, n), BitOps.ushr(v, 32 - n));
    }
}

class Sha256 {
    static int[] K;

    static void ensureK() {
        if (K != null) {
            return;
        }
        K = new int[64];
        K[0] = 1116352408; K[1] = 1899447441; K[2] = -1245643825; K[3] = -373957723;
        K[4] = 961987163; K[5] = 1508970993; K[6] = -1841331548; K[7] = -1424204075;
        K[8] = -670586216; K[9] = 310598401; K[10] = 607225278; K[11] = 1426881987;
        K[12] = 1925078388; K[13] = -2132889090; K[14] = -1680079193; K[15] = -1046744716;
        K[16] = -459576895; K[17] = -272742522; K[18] = 264347078; K[19] = 604807628;
        K[20] = 770255983; K[21] = 1249150122; K[22] = 1555081692; K[23] = 1996064986;
        K[24] = -1740746414; K[25] = -1473132947; K[26] = -1341970488; K[27] = -1084653625;
        K[28] = -958395405; K[29] = -710438585; K[30] = 113926993; K[31] = 338241895;
        K[32] = 666307205; K[33] = 773529912; K[34] = 1294757372; K[35] = 1396182291;
        K[36] = 1695183700; K[37] = 1986661051; K[38] = -2117940946; K[39] = -1838011259;
        K[40] = -1564481375; K[41] = -1476022903; K[42] = -1035236496; K[43] = -459150915;
        K[44] = -272839938; K[45] = 472884868; K[46] = 553557557; K[47] = 644041734;
        K[48] = 701463874; K[49] = 833775961; K[50] = 945616953; K[51] = 1000298946;
        K[52] = 1102873575; K[53] = 1341975988; K[54] = 1467030066; K[55] = 1605042784;
        K[56] = 1810606405; K[57] = 1894112395; K[58] = -2057834219; K[59] = -1805190801;
        K[60] = -1658657183; K[61] = -1520251240; K[62] = -1263164761; K[63] = -1005681410;
    }

    static byte[] hash(byte[] message) {
        ensureK();
        if (message == null) {
            message = new byte[0];
        }
        int[] h = new int[8];
        h[0] = 1779033703; h[1] = -1150833019; h[2] = 1013904242; h[3] = -1521486534;
        h[4] = 1359893119; h[5] = -1694144372; h[6] = 528734635; h[7] = 1541459225;
        int bitLenLow = message.length * 8;
        int paddedLen = message.length + 1;
        while ((paddedLen % 64) != 56) {
            paddedLen = paddedLen + 1;
        }
        paddedLen = paddedLen + 8;
        byte[] padded = new byte[paddedLen];
        int i = 0;
        while (i < message.length) {
            padded[i] = message[i];
            i = i + 1;
        }
        padded[message.length] = -128;
        CryptoBytes.putInt32BE(padded, paddedLen - 4, bitLenLow);
        int off = 0;
        while (off < paddedLen) {
            int[] w = new int[64];
            int t = 0;
            while (t < 16) {
                int b0 = CryptoBytes.u8(padded[off + t * 4]);
                int b1 = CryptoBytes.u8(padded[off + t * 4 + 1]);
                int b2 = CryptoBytes.u8(padded[off + t * 4 + 2]);
                int b3 = CryptoBytes.u8(padded[off + t * 4 + 3]);
                w[t] = BitOps.or(BitOps.or(BitOps.or(
                        BitOps.shl(b0, 24),
                        BitOps.shl(b1, 16)),
                        BitOps.shl(b2, 8)),
                        b3);
                t = t + 1;
            }
            t = 16;
            while (t < 64) {
                int s0 = BitOps.xor(BitOps.xor(rotr(w[t - 15], 7), rotr(w[t - 15], 18)), BitOps.ushr(w[t - 15], 3));
                int s1 = BitOps.xor(BitOps.xor(rotr(w[t - 2], 17), rotr(w[t - 2], 19)), BitOps.ushr(w[t - 2], 10));
                w[t] = w[t - 16] + s0 + w[t - 7] + s1;
                t = t + 1;
            }
            int a = h[0]; int b = h[1]; int c = h[2]; int d = h[3];
            int e = h[4]; int f = h[5]; int g = h[6]; int hh = h[7];
            t = 0;
            while (t < 64) {
                int S1 = BitOps.xor(BitOps.xor(rotr(e, 6), rotr(e, 11)), rotr(e, 25));
                int ch = BitOps.xor(BitOps.and(e, f), BitOps.and(BitOps.not(e), g));
                int temp1 = hh + S1 + ch + K[t] + w[t];
                int S0 = BitOps.xor(BitOps.xor(rotr(a, 2), rotr(a, 13)), rotr(a, 22));
                int maj = BitOps.xor(BitOps.xor(BitOps.and(a, b), BitOps.and(a, c)), BitOps.and(b, c));
                int temp2 = S0 + maj;
                hh = g; g = f; f = e; e = d + temp1;
                d = c; c = b; b = a; a = temp1 + temp2;
                t = t + 1;
            }
            h[0] = h[0] + a; h[1] = h[1] + b; h[2] = h[2] + c; h[3] = h[3] + d;
            h[4] = h[4] + e; h[5] = h[5] + f; h[6] = h[6] + g; h[7] = h[7] + hh;
            off = off + 64;
        }
        byte[] out = new byte[32];
        i = 0;
        while (i < 8) {
            CryptoBytes.putInt32BE(out, i * 4, h[i]);
            i = i + 1;
        }
        return out;
    }

    static int rotr(int v, int n) {
        return BitOps.or(BitOps.ushr(v, n), BitOps.shl(v, 32 - n));
    }
}

class Hmac {
    static byte[] sha256(byte[] key, byte[] message) {
        if (key == null) {
            key = new byte[0];
        }
        if (message == null) {
            message = new byte[0];
        }
        byte[] k = key;
        if (k.length > 64) {
            k = Sha256.hash(k);
        }
        byte[] kopad = new byte[64];
        byte[] kipad = new byte[64];
        int i = 0;
        while (i < 64) {
            int kb = 0;
            if (i < k.length) {
                kb = CryptoBytes.u8(k[i]);
            }
            kipad[i] = CryptoBytes.toByte(BitOps.xor(kb, 54));
            kopad[i] = CryptoBytes.toByte(BitOps.xor(kb, 92));
            i = i + 1;
        }
        byte[] inner = Sha256.hash(CryptoBytes.concat(kipad, message));
        return Sha256.hash(CryptoBytes.concat(kopad, inner));
    }
}

class Password {
    /**
     * Versioned PBKDF2-HMAC-SHA256 default. Format: pbkdf2_sha256$N$saltHex$dkHex.
     * Raised from legacy 500 for Music Auth / security baseline; verify still accepts older N.
     * Prefer Argon2id (RH-3) when available via C ABI.
     */
    static int DEFAULT_ITERATIONS = 10000;
    static int LEGACY_ITERATIONS = 500;
    static int MAX_PASSWORD_CHARS = 1024;

    static JnFuture hash(EventLoop loop, String password) {
        PasswordHashSupplier sup = new PasswordHashSupplier();
        sup.password = password;
        return Async.blockingOnLoop(loop, sup);
    }

    static JnFuture verify(EventLoop loop, String password, String encoded) {
        PasswordVerifySupplier sup = new PasswordVerifySupplier();
        sup.password = password;
        sup.encoded = encoded;
        return Async.blockingOnLoop(loop, sup);
    }

    static String hashBlocking(String password) {
        if (password == null) {
            password = "";
        }
        if (password.length() > MAX_PASSWORD_CHARS) {
            throw new CryptoException("password too long");
        }
        byte[] salt = SecureRandom.bytes(16);
        byte[] dk = pbkdf2(Bytes.fromString(password), salt, DEFAULT_ITERATIONS, 32);
        return "pbkdf2_sha256$" + String.valueOf(DEFAULT_ITERATIONS) + "$"
                + CryptoHex.encode(salt) + "$" + CryptoHex.encode(dk);
    }

    static int verifyBlocking(String password, String encoded) {
        if (password == null || encoded == null) {
            return 0;
        }
        if (password.length() > MAX_PASSWORD_CHARS) {
            return 0;
        }
        if (encoded.indexOf("pbkdf2_sha256$") != 0) {
            return 0;
        }
        String rest = encoded.substringFrom(14);
        int p1 = rest.indexOf("$");
        if (p1 < 0) {
            return 0;
        }
        int iterations = CryptoParse.intFromString(rest.substring(0, p1));
        String rest2 = rest.substringFrom(p1 + 1);
        int p2 = rest2.indexOf("$");
        if (p2 < 0) {
            return 0;
        }
        byte[] salt = CryptoHex.decode(rest2.substring(0, p2));
        byte[] expected = CryptoHex.decode(rest2.substringFrom(p2 + 1));
        byte[] actual = pbkdf2(Bytes.fromString(password), salt, iterations, expected.length);
        return ConstantTime.equalBytes(actual, expected);
    }

    /** Non-zero when encoded hash should be upgraded to current DEFAULT_ITERATIONS. */
    static int needsRehash(String encoded) {
        if (encoded == null || encoded.indexOf("pbkdf2_sha256$") != 0) {
            return 1;
        }
        String rest = encoded.substringFrom(14);
        int p1 = rest.indexOf("$");
        if (p1 < 0) {
            return 1;
        }
        int iterations = CryptoParse.intFromString(rest.substring(0, p1));
        if (iterations < DEFAULT_ITERATIONS) {
            return 1;
        }
        return 0;
    }

    static byte[] pbkdf2(byte[] password, byte[] salt, int iterations, int dkLen) {
        byte[] out = new byte[dkLen];
        int block = 1;
        int pos = 0;
        while (pos < dkLen) {
            byte[] blockBytes = new byte[4];
            CryptoBytes.putInt32BE(blockBytes, 0, block);
            byte[] u = Hmac.sha256(password, CryptoBytes.concat(salt, blockBytes));
            byte[] t = CryptoBytes.copyOf(u, u.length);
            int i = 1;
            while (i < iterations) {
                u = Hmac.sha256(password, u);
                int j = 0;
                while (j < t.length) {
                    t[j] = CryptoBytes.toByte(BitOps.xor(CryptoBytes.u8(t[j]), CryptoBytes.u8(u[j])));
                    j = j + 1;
                }
                i = i + 1;
            }
            int copy = dkLen - pos;
            if (copy > t.length) {
                copy = t.length;
            }
            int k = 0;
            while (k < copy) {
                out[pos + k] = t[k];
                k = k + 1;
            }
            pos = pos + copy;
            block = block + 1;
        }
        return out;
    }
}

class PasswordHashSupplier implements BlockingSupplier {
    String password;

    public Object get() {
        return Password.hashBlocking(password);
    }
}

class PasswordVerifySupplier implements BlockingSupplier {
    String password;
    String encoded;

    public Object get() {
        return new CryptoInt(Password.verifyBlocking(password, encoded));
    }
}

class JwtClaims {
    String iss;
    String sub;
    String aud;
    long exp;
    long nbf;
    long iat;
    String jti;
    HashMap<String, String> extra;

    JwtClaims() {
        iss = "";
        sub = "";
        aud = "";
        exp = 0L;
        nbf = 0L;
        iat = 0L;
        jti = "";
        extra = new HashMap<String, String>();
    }
}

class Jwt {
    static int clockSkewSeconds = 60;

    static String signHs256(JwtClaims claims, byte[] key) {
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = claimsToJson(claims);
        String h64 = Base64Codec.encodeUrl(Bytes.fromString(header));
        String p64 = Base64Codec.encodeUrl(Bytes.fromString(payload));
        String signing = h64 + "." + p64;
        byte[] sig = Hmac.sha256(key, Bytes.fromString(signing));
        return signing + "." + Base64Codec.encodeUrl(sig);
    }

    static JwtClaims verifyHs256(String token, byte[] key) {
        if (token == null) {
            throw new CryptoException("null jwt");
        }
        int d1 = token.indexOf(".");
        if (d1 < 0) {
            throw new CryptoException("malformed jwt");
        }
        String rest = token.substringFrom(d1 + 1);
        int d2 = rest.indexOf(".");
        if (d2 < 0) {
            throw new CryptoException("malformed jwt");
        }
        String h64 = token.substring(0, d1);
        String p64 = rest.substring(0, d2);
        String s64 = rest.substringFrom(d2 + 1);
        String headerJson = Bytes.toString(Base64Codec.decode(h64));
        if (headerJson.indexOf("\"none\"") >= 0 || headerJson.indexOf("'none'") >= 0) {
            throw new CryptoException("alg=none rejected");
        }
        if (headerJson.indexOf("HS256") < 0) {
            throw new CryptoException("unexpected jwt algorithm");
        }
        String signing = h64 + "." + p64;
        byte[] expected = Hmac.sha256(key, Bytes.fromString(signing));
        byte[] actual = Base64Codec.decode(s64);
        if (ConstantTime.equalBytes(expected, actual) == 0) {
            throw new CryptoException("bad jwt signature");
        }
        String payloadJson = Bytes.toString(Base64Codec.decode(p64));
        JwtClaims claims = jsonToClaims(payloadJson);
        long now = AsyncClockNative.monoMillis() / 1000L;
        if (claims.exp > 0L) {
            if (now > claims.exp + clockSkewSeconds) {
                throw new CryptoException("jwt expired");
            }
        }
        if (claims.nbf > 0L) {
            if (now + clockSkewSeconds < claims.nbf) {
                throw new CryptoException("jwt not yet valid");
            }
        }
        return claims;
    }

    static String claimsToJson(JwtClaims c) {
        String out = "{";
        out = out + "\"sub\":\"" + c.sub + "\"";
        if (c.iss.length() > 0) {
            out = out + ",\"iss\":\"" + c.iss + "\"";
        }
        if (c.aud.length() > 0) {
            out = out + ",\"aud\":\"" + c.aud + "\"";
        }
        if (c.exp > 0L) {
            out = out + ",\"exp\":" + CryptoParse.longToString(c.exp);
        }
        if (c.nbf > 0L) {
            out = out + ",\"nbf\":" + CryptoParse.longToString(c.nbf);
        }
        if (c.iat > 0L) {
            out = out + ",\"iat\":" + CryptoParse.longToString(c.iat);
        }
        if (c.jti.length() > 0) {
            out = out + ",\"jti\":\"" + c.jti + "\"";
        }
        out = out + "}";
        return out;
    }

    static JwtClaims jsonToClaims(String json) {
        JwtClaims c = new JwtClaims();
        c.sub = jsonStringField(json, "sub");
        c.iss = jsonStringField(json, "iss");
        c.aud = jsonStringField(json, "aud");
        c.jti = jsonStringField(json, "jti");
        c.exp = jsonLongField(json, "exp");
        c.nbf = jsonLongField(json, "nbf");
        c.iat = jsonLongField(json, "iat");
        return c;
    }

    static String jsonStringField(String json, String key) {
        String needle = "\"" + key + "\":\"";
        int i = json.indexOf(needle);
        if (i < 0) {
            return "";
        }
        int start = i + needle.length();
        int end = start;
        int found = 0;
        while (found == 0 && end < json.length()) {
            if (json.substring(end, end + 1).equals("\"")) {
                found = 1;
            } else {
                end = end + 1;
            }
        }
        if (found == 0) {
            return "";
        }
        return json.substring(start, end);
    }

    static long jsonLongField(String json, String key) {
        String needle = "\"" + key + "\":";
        int i = json.indexOf(needle);
        if (i < 0) {
            return 0L;
        }
        int start = i + needle.length();
        int end = start;
        int alive = 1;
        while (alive != 0 && end < json.length()) {
            String ch = json.substring(end, end + 1);
            if (ch.equals(",") || ch.equals("}")) {
                alive = 0;
            } else {
                end = end + 1;
            }
        }
        return CryptoParse.longFromString(json.substring(start, end));
    }
}

class WebSocketAccept {
    static String GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    static String compute(String clientKey) {
        byte[] concat = Bytes.fromString(clientKey + GUID);
        byte[] digest = Sha1.hash(concat);
        return Base64Codec.encode(digest);
    }
}
