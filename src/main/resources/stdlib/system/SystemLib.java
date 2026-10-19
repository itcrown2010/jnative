/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative System shim. The parser desugars System.exit / currentTimeMillis / nanoTime /
// arraycopy / lineSeparator / out.print / err.print(ln) into static calls on JnSystem.

class JnSystem {
    @Native(symbol = "jnative_exit")
    static native void exit0(int status);

    @Native(symbol = "jnative_wall_millis")
    static native long currentTimeMillis();

    @Native(symbol = "jnative_mono_nanos")
    static native long nanoTime();

    @Native(symbol = "jnative_stdout_write")
    static native void outWrite(String s, int newline);

    @Native(symbol = "jnative_stderr_write")
    static native void errWrite(String s, int newline);

    static void exit(int status) {
        exit0(status);
    }

    static void print(String s) {
        outWrite(s, 0);
    }

    static void errPrint(String s) {
        errWrite(s, 0);
    }

    static void errPrintln(String s) {
        errWrite(s, 1);
    }

    @Native(symbol = "jnative_is_windows")
    static native int isWindows();

    static String lineSeparator() {
        if (isWindows() != 0) {
            return "\r\n";
        }
        return "\n";
    }

    static void checkCopy(int srcLen, int srcPos, int destLen, int destPos, int length) {
        if (srcPos < 0 || destPos < 0 || length < 0
                || srcPos > srcLen - length || destPos > destLen - length) {
            throw new IndexOutOfBoundsException("arraycopy: last source index "
                    + (srcPos + length) + " out of bounds for length " + srcLen);
        }
    }
    static void arraycopy(byte[] src, int srcPos, byte[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException("arraycopy");
        }
        checkCopy(src.length, srcPos, dest.length, destPos, length);
        if (src == dest && srcPos < destPos) {
            int i = length - 1;
            while (i >= 0) {
                dest[destPos + i] = src[srcPos + i];
                i = i - 1;
            }
        } else {
            int i = 0;
            while (i < length) {
                dest[destPos + i] = src[srcPos + i];
                i = i + 1;
            }
        }
    }

    static void arraycopy(short[] src, int srcPos, short[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException("arraycopy");
        }
        checkCopy(src.length, srcPos, dest.length, destPos, length);
        if (src == dest && srcPos < destPos) {
            int i = length - 1;
            while (i >= 0) {
                dest[destPos + i] = src[srcPos + i];
                i = i - 1;
            }
        } else {
            int i = 0;
            while (i < length) {
                dest[destPos + i] = src[srcPos + i];
                i = i + 1;
            }
        }
    }

    static void arraycopy(char[] src, int srcPos, char[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException("arraycopy");
        }
        checkCopy(src.length, srcPos, dest.length, destPos, length);
        if (src == dest && srcPos < destPos) {
            int i = length - 1;
            while (i >= 0) {
                dest[destPos + i] = src[srcPos + i];
                i = i - 1;
            }
        } else {
            int i = 0;
            while (i < length) {
                dest[destPos + i] = src[srcPos + i];
                i = i + 1;
            }
        }
    }

    static void arraycopy(int[] src, int srcPos, int[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException("arraycopy");
        }
        checkCopy(src.length, srcPos, dest.length, destPos, length);
        if (src == dest && srcPos < destPos) {
            int i = length - 1;
            while (i >= 0) {
                dest[destPos + i] = src[srcPos + i];
                i = i - 1;
            }
        } else {
            int i = 0;
            while (i < length) {
                dest[destPos + i] = src[srcPos + i];
                i = i + 1;
            }
        }
    }

    static void arraycopy(long[] src, int srcPos, long[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException("arraycopy");
        }
        checkCopy(src.length, srcPos, dest.length, destPos, length);
        if (src == dest && srcPos < destPos) {
            int i = length - 1;
            while (i >= 0) {
                dest[destPos + i] = src[srcPos + i];
                i = i - 1;
            }
        } else {
            int i = 0;
            while (i < length) {
                dest[destPos + i] = src[srcPos + i];
                i = i + 1;
            }
        }
    }

    static void arraycopy(float[] src, int srcPos, float[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException("arraycopy");
        }
        checkCopy(src.length, srcPos, dest.length, destPos, length);
        if (src == dest && srcPos < destPos) {
            int i = length - 1;
            while (i >= 0) {
                dest[destPos + i] = src[srcPos + i];
                i = i - 1;
            }
        } else {
            int i = 0;
            while (i < length) {
                dest[destPos + i] = src[srcPos + i];
                i = i + 1;
            }
        }
    }

    static void arraycopy(double[] src, int srcPos, double[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException("arraycopy");
        }
        checkCopy(src.length, srcPos, dest.length, destPos, length);
        if (src == dest && srcPos < destPos) {
            int i = length - 1;
            while (i >= 0) {
                dest[destPos + i] = src[srcPos + i];
                i = i - 1;
            }
        } else {
            int i = 0;
            while (i < length) {
                dest[destPos + i] = src[srcPos + i];
                i = i + 1;
            }
        }
    }

    static void arraycopy(boolean[] src, int srcPos, boolean[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException("arraycopy");
        }
        checkCopy(src.length, srcPos, dest.length, destPos, length);
        if (src == dest && srcPos < destPos) {
            int i = length - 1;
            while (i >= 0) {
                dest[destPos + i] = src[srcPos + i];
                i = i - 1;
            }
        } else {
            int i = 0;
            while (i < length) {
                dest[destPos + i] = src[srcPos + i];
                i = i + 1;
            }
        }
    }

    static void arraycopy(Object[] src, int srcPos, Object[] dest, int destPos, int length) {
        if (src == null || dest == null) {
            throw new NullPointerException("arraycopy");
        }
        checkCopy(src.length, srcPos, dest.length, destPos, length);
        if (src == dest && srcPos < destPos) {
            int i = length - 1;
            while (i >= 0) {
                dest[destPos + i] = src[srcPos + i];
                i = i - 1;
            }
        } else {
            int i = 0;
            while (i < length) {
                dest[destPos + i] = src[srcPos + i];
                i = i + 1;
            }
        }
    }

}
