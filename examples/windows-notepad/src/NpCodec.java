/**
 * Text encodings and line endings of the JNative Notepad (same set as Windows Notepad):
 * ANSI, UTF-16 LE, UTF-16 BE, UTF-8, UTF-8 with BOM.
 *
 * Java Strings in JNative are UTF-16 internally, so String offsets match the Win32 EDIT
 * control offsets used by TextArea.getSelectionStart().
 *
 * @author 张晓达
 */
class NpCodec {
    static int ENC_ANSI = 0;
    static int ENC_UTF16LE = 1;
    static int ENC_UTF16BE = 2;
    static int ENC_UTF8 = 3;
    static int ENC_UTF8BOM = 4;

    static int EOL_CRLF = 0;
    static int EOL_LF = 1;
    static int EOL_CR = 2;

    static String encName(int e) {
        if (e == ENC_ANSI) {
            return "ANSI";
        }
        if (e == ENC_UTF16LE) {
            return "UTF-16 LE";
        }
        if (e == ENC_UTF16BE) {
            return "UTF-16 BE";
        }
        if (e == ENC_UTF8BOM) {
            return "UTF-8 with BOM";
        }
        return "UTF-8";
    }

    /** Tab-separated labels for the Save As encoding combo (order = ENC_* ids). */
    static String encLabels() {
        return "ANSI\tUTF-16 LE\tUTF-16 BE\tUTF-8\tUTF-8 with BOM";
    }

    static String eolName(int e) {
        if (e == EOL_LF) {
            return "Unix (LF)";
        }
        if (e == EOL_CR) {
            return "Macintosh (CR)";
        }
        return "Windows (CRLF)";
    }

    static int b(byte[] d, int i) {
        return d[i] & 255;
    }

    // ---- detection ----------------------------------------------------------

    static int detectEncoding(byte[] d) {
        int n = d.length;
        if (n >= 2 && b(d, 0) == 255 && b(d, 1) == 254) {
            return ENC_UTF16LE;
        }
        if (n >= 2 && b(d, 0) == 254 && b(d, 1) == 255) {
            return ENC_UTF16BE;
        }
        if (n >= 3 && b(d, 0) == 239 && b(d, 1) == 187 && b(d, 2) == 191) {
            return ENC_UTF8BOM;
        }
        int guess = guessUtf16WithoutBom(d);
        if (guess != 0) {
            return guess;
        }
        if (isValidUtf8(d, 0)) {
            return ENC_UTF8;
        }
        return ENC_ANSI;
    }

    /** ASCII-heavy UTF-16 without BOM: NUL bytes alternate at one parity. */
    static int guessUtf16WithoutBom(byte[] d) {
        int n = d.length;
        if (n < 4 || n % 2 != 0) {
            return 0;
        }
        int limit = n;
        if (limit > 4096) {
            limit = 4096;
        }
        int evenZero = 0;
        int oddZero = 0;
        int i = 0;
        while (i + 1 < limit) {
            if (b(d, i) == 0) {
                evenZero = evenZero + 1;
            }
            if (b(d, i + 1) == 0) {
                oddZero = oddZero + 1;
            }
            i = i + 2;
        }
        int pairs = limit / 2;
        if (oddZero * 10 >= pairs * 8 && evenZero == 0) {
            return ENC_UTF16LE;
        }
        if (evenZero * 10 >= pairs * 8 && oddZero == 0) {
            return ENC_UTF16BE;
        }
        return 0;
    }

    static boolean isValidUtf8(byte[] d, int from) {
        int n = d.length;
        int i = from;
        while (i < n) {
            int c = b(d, i);
            if (c < 128) {
                i = i + 1;
            } else if (c >= 194 && c <= 223) {
                if (i + 1 >= n || (b(d, i + 1) & 192) != 128) {
                    return false;
                }
                i = i + 2;
            } else if (c >= 224 && c <= 239) {
                if (i + 2 >= n || (b(d, i + 1) & 192) != 128 || (b(d, i + 2) & 192) != 128) {
                    return false;
                }
                if (c == 224 && b(d, i + 1) < 160) {
                    return false;
                }
                if (c == 237 && b(d, i + 1) > 159) {
                    return false;
                }
                i = i + 3;
            } else if (c >= 240 && c <= 244) {
                if (i + 3 >= n || (b(d, i + 1) & 192) != 128 || (b(d, i + 2) & 192) != 128
                        || (b(d, i + 3) & 192) != 128) {
                    return false;
                }
                if (c == 240 && b(d, i + 1) < 144) {
                    return false;
                }
                if (c == 244 && b(d, i + 1) > 143) {
                    return false;
                }
                i = i + 4;
            } else {
                return false;
            }
        }
        return true;
    }

    // ---- UTF-8 byte building ------------------------------------------------

    static int utf8Len(int cp) {
        if (cp < 128) {
            return 1;
        }
        if (cp < 2048) {
            return 2;
        }
        if (cp < 65536) {
            return 3;
        }
        return 4;
    }

    static int putUtf8(byte[] out, int pos, int cp) {
        if (cp < 128) {
            out[pos] = (byte) cp;
            return pos + 1;
        }
        if (cp < 2048) {
            out[pos] = (byte) (192 | (cp >> 6));
            out[pos + 1] = (byte) (128 | (cp & 63));
            return pos + 2;
        }
        if (cp < 65536) {
            out[pos] = (byte) (224 | (cp >> 12));
            out[pos + 1] = (byte) (128 | ((cp >> 6) & 63));
            out[pos + 2] = (byte) (128 | (cp & 63));
            return pos + 3;
        }
        out[pos] = (byte) (240 | (cp >> 18));
        out[pos + 1] = (byte) (128 | ((cp >> 12) & 63));
        out[pos + 2] = (byte) (128 | ((cp >> 6) & 63));
        out[pos + 3] = (byte) (128 | (cp & 63));
        return pos + 4;
    }

    // ---- decode -------------------------------------------------------------

    /** Decode file bytes using enc (BOM skipped). NUL characters become spaces. */
    static String decode(byte[] d, int enc) {
        if (enc == ENC_UTF16LE) {
            return decodeUtf16(d, 1);
        }
        if (enc == ENC_UTF16BE) {
            return decodeUtf16(d, 0);
        }
        if (enc == ENC_ANSI) {
            return decodeAnsi(d);
        }
        int from = 0;
        if (enc == ENC_UTF8BOM && d.length >= 3 && b(d, 0) == 239 && b(d, 1) == 187
                && b(d, 2) == 191) {
            from = 3;
        }
        int n = d.length - from;
        byte[] t = new byte[n];
        int i = 0;
        while (i < n) {
            int c = b(d, from + i);
            if (c == 0) {
                c = 32;
            }
            t[i] = (byte) c;
            i = i + 1;
        }
        return String.fromBytes(t);
    }

    static int unit(byte[] d, int i, int little) {
        if (little != 0) {
            return b(d, i) | (b(d, i + 1) << 8);
        }
        return (b(d, i) << 8) | b(d, i + 1);
    }

    static String decodeUtf16(byte[] d, int little) {
        int start = 0;
        if (d.length >= 2) {
            int u0 = unit(d, 0, little);
            if (u0 == 65279) {
                start = 2;
            }
        }
        int units = (d.length - start) / 2;
        int total = 0;
        int i = 0;
        while (i < units) {
            int u = unit(d, start + i * 2, little);
            if (u >= 55296 && u <= 56319 && i + 1 < units) {
                int u2 = unit(d, start + (i + 1) * 2, little);
                if (u2 >= 56320 && u2 <= 57343) {
                    total = total + 4;
                    i = i + 2;
                    continue;
                }
            }
            if (u >= 55296 && u <= 57343) {
                total = total + 3;
            } else if (u == 0) {
                total = total + 1;
            } else {
                total = total + utf8Len(u);
            }
            i = i + 1;
        }
        byte[] out = new byte[total];
        int pos = 0;
        i = 0;
        while (i < units) {
            int u = unit(d, start + i * 2, little);
            if (u >= 55296 && u <= 56319 && i + 1 < units) {
                int u2 = unit(d, start + (i + 1) * 2, little);
                if (u2 >= 56320 && u2 <= 57343) {
                    int cp = 65536 + ((u - 55296) << 10) + (u2 - 56320);
                    pos = putUtf8(out, pos, cp);
                    i = i + 2;
                    continue;
                }
            }
            if (u >= 55296 && u <= 57343) {
                pos = putUtf8(out, pos, 65533);
            } else if (u == 0) {
                pos = putUtf8(out, pos, 32);
            } else {
                pos = putUtf8(out, pos, u);
            }
            i = i + 1;
        }
        return String.fromBytes(out);
    }

    /** Raw bytes -> "byte string" (one code point U+0001..U+00FF per byte) as a String. */
    static String bytesToByteString(byte[] d) {
        int total = 0;
        int i = 0;
        while (i < d.length) {
            int c = b(d, i);
            if (c >= 128) {
                total = total + 2;
            } else {
                total = total + 1;
            }
            i = i + 1;
        }
        byte[] out = new byte[total];
        int pos = 0;
        i = 0;
        while (i < d.length) {
            int c = b(d, i);
            if (c == 0) {
                c = 32;
            }
            pos = putUtf8(out, pos, c);
            i = i + 1;
        }
        return String.fromBytes(out);
    }

    /** Inverse of bytesToByteString. */
    static byte[] byteStringToBytes(String s) {
        int n = s.length();
        byte[] out = new byte[n];
        int i = 0;
        while (i < n) {
            int c = s.charAt(i);
            out[i] = (byte) c;
            i = i + 1;
        }
        return out;
    }

    static String decodeAnsi(byte[] d) {
        String bs = bytesToByteString(d);
        NativePtr p = UiNative.codepageDecode(bs, UiNative.systemAnsiCodepage());
        if (p == NativePtr.NULL) {
            return bs;
        }
        String r = NativeMemory.fromCString(p);
        UiNative.dialogFreePath(p);
        if (r == null) {
            return "";
        }
        return r;
    }

    // ---- encode -------------------------------------------------------------

    static byte[] encode(String s, int enc) {
        if (enc == ENC_UTF16LE) {
            return encodeUtf16(s, 1);
        }
        if (enc == ENC_UTF16BE) {
            return encodeUtf16(s, 0);
        }
        if (enc == ENC_ANSI) {
            return encodeAnsi(s);
        }
        byte[] body = s.getBytes();
        if (enc == ENC_UTF8BOM) {
            byte[] out = new byte[body.length + 3];
            out[0] = (byte) 239;
            out[1] = (byte) 187;
            out[2] = (byte) 191;
            int i = 0;
            while (i < body.length) {
                out[3 + i] = body[i];
                i = i + 1;
            }
            return out;
        }
        return body;
    }

    static byte[] encodeUtf16(String s, int little) {
        int n = s.length();
        byte[] out = new byte[2 + n * 2];
        if (little != 0) {
            out[0] = (byte) 255;
            out[1] = (byte) 254;
        } else {
            out[0] = (byte) 254;
            out[1] = (byte) 255;
        }
        int i = 0;
        while (i < n) {
            int c = s.charAt(i);
            int lo = c & 255;
            int hi = (c >> 8) & 255;
            if (little != 0) {
                out[2 + i * 2] = (byte) lo;
                out[3 + i * 2] = (byte) hi;
            } else {
                out[2 + i * 2] = (byte) hi;
                out[3 + i * 2] = (byte) lo;
            }
            i = i + 1;
        }
        return out;
    }

    static byte[] encodeAnsi(String s) {
        NativePtr p = UiNative.codepageEncode(s, UiNative.systemAnsiCodepage());
        if (p == NativePtr.NULL) {
            return s.getBytes();
        }
        String bs = NativeMemory.fromCString(p);
        UiNative.dialogFreePath(p);
        if (bs == null) {
            bs = "";
        }
        return byteStringToBytes(bs);
    }

    /** True when saving s as ANSI would lose characters. */
    static boolean ansiLossy(String s) {
        int n = s.length();
        boolean hi = false;
        int i = 0;
        while (i < n) {
            if (s.charAt(i) > 127) {
                hi = true;
                break;
            }
            i = i + 1;
        }
        if (!hi) {
            return false;
        }
        String back = decodeAnsi(encodeAnsi(s));
        return !back.equals(s);
    }

    // ---- line endings -------------------------------------------------------

    /** Line ending of the first break in the text (Notepad default: CRLF). */
    static int detectEol(String s) {
        int n = s.length();
        int i = 0;
        while (i < n) {
            int c = s.charAt(i);
            if (c == 13) {
                if (i + 1 < n && s.charAt(i + 1) == 10) {
                    return EOL_CRLF;
                }
                return EOL_CR;
            }
            if (c == 10) {
                return EOL_LF;
            }
            i = i + 1;
        }
        return EOL_CRLF;
    }

    /** Any mix of CRLF / LF / CR -> the given ending. */
    static String normalizeEol(String s, int eol) {
        int n = s.length();
        boolean need = false;
        int i = 0;
        while (i < n) {
            int c = s.charAt(i);
            if (c == 13 || c == 10) {
                need = true;
                break;
            }
            i = i + 1;
        }
        if (!need) {
            return s;
        }
        String sep = "\r\n";
        if (eol == EOL_LF) {
            sep = "\n";
        } else if (eol == EOL_CR) {
            sep = "\r";
        }
        StringBuilder sb = new StringBuilder();
        int start = 0;
        i = 0;
        while (i < n) {
            int c = s.charAt(i);
            if (c == 13 || c == 10) {
                sb.append(s.substring(start, i));
                sb.append(sep);
                if (c == 13 && i + 1 < n && s.charAt(i + 1) == 10) {
                    i = i + 1;
                }
                start = i + 1;
            }
            i = i + 1;
        }
        sb.append(s.substring(start, n));
        return sb.toString();
    }
}
