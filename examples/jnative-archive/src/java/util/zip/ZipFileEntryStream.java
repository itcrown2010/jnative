package java.util.zip;

import org.jnative.archive.internal.ZipNative;

/**
 * Entry stream owned by ZipFile.
 * Fields are assigned by ZipFile after construction (subclass ctor field-store
 * has been unreliable for this type in multi-unit builds).
 *
 * @author 张晓达
 */
public class ZipFileEntryStream extends InputStream {
    public NativePtr reader;
    public ZipFile fileOwner;

    public ZipFileEntryStream() {
        reader = NativePtr.NULL;
        fileOwner = null;
    }

    public int read() {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        if (n <= 0) {
            return -1;
        }
        int v = one[0];
        if (v < 0) {
            v = v + 256;
        }
        return v;
    }

    public int read(byte[] b, int off, int len) {
        if (closed != 0) {
            throw new IOException("closed");
        }
        if (b == null) {
            throw new NullPointerException("b");
        }
        if (len < 0 || off < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("read");
        }
        if (len == 0) {
            return 0;
        }
        NativePtr p = NativeMemory.allocate(0L + len + 4);
        int n = ZipNative.readerRead(reader, p, len);
        if (n < 0) {
            NativeMemory.free(p);
            ZipNative.check(n, "read");
            return -1;
        }
        if (n == 0) {
            NativeMemory.free(p);
            return -1;
        }
        StreamNative.copyFromNative(p, b, off, n);
        NativeMemory.free(p);
        return n;
    }

    public void close() {
        if (closed != 0) {
            return;
        }
        int rc = ZipNative.readerCloseEntry(reader);
        closed = 1;
        if (fileOwner != null && fileOwner.activeEntryStream == this) {
            fileOwner.activeEntryStream = null;
        }
        ZipNative.check(rc, "closeEntry");
    }
}
