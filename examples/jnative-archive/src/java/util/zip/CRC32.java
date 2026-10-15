package java.util.zip;

import org.jnative.archive.internal.ZipNative;

/** CRC-32 of data. Subset of java.util.zip.CRC32. @author 张晓达 */
public class CRC32 {
    public int value;

    public CRC32() {
        value = 0;
    }

    public void reset() {
        value = 0;
    }

    public void update(byte[] b, int off, int len) {
        if (b == null || len <= 0) {
            return;
        }
        NativePtr p = NativeMemory.allocate(0L + len + 4);
        StreamNative.copyToNative(b, off, p, len);
        value = ZipNative.crc32(value, p, len);
        NativeMemory.free(p);
    }

    public void update(byte[] b) {
        if (b == null) {
            return;
        }
        update(b, 0, b.length);
    }

    public long getValue() {
        return crc32ToLong(value);
    }

    public static long crc32ToLong(int crc) {
        long v = 0L + crc;
        if (v < 0L) {
            v = v + 4294967296L;
        }
        return v;
    }
}
