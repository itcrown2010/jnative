package java.util.zip;

import org.jnative.archive.internal.ZipNative;

/**
 * Sequential ZIP reader. Subset of java.util.zip.ZipInputStream.
 * Constructor takes a filesystem path (JNative; JDK uses InputStream).
 * Password: org.jnative.archive.ZipExtensions.
 * @author 张晓达
 */
public class ZipInputStream extends InputStream {
    public NativePtr handle;
    public ZipEntry current;
    public int entryOpen;
    public int exhausted;

    public ZipInputStream(String path) {
        handle = ZipNative.readerCreate();
        if (handle == NativePtr.NULL) {
            throw new ZipException("reader create failed");
        }
        current = null;
        entryOpen = 0;
        exhausted = 0;
        int rc = ZipNative.readerOpen(handle, path);
        if (rc < 0) {
            ZipNative.readerDestroy(handle);
            handle = NativePtr.NULL;
            throw new ZipException("open: " + ZipNative.errMsg(rc));
        }
    }


    /** Native handle for org.jnative.archive extensions (not a JDK API). */
    public NativePtr nativeHandle() {
        return handle;
    }

    public ZipEntry getNextEntry() {
        int err;
        if (entryOpen != 0) {
            closeEntry();
        }
        if (exhausted != 0) {
            return null;
        }
        if (current == null) {
            err = ZipNative.readerGotoFirst(handle);
        } else {
            err = ZipNative.readerGotoNext(handle);
        }
        if (err == -9) {
            /* JN_ZIP_ERR_EOF */
            exhausted = 1;
            current = null;
            return null;
        }
        ZipNative.check(err, "getNextEntry");
        current = readCurrentInfo();
        ZipNative.check(ZipNative.readerOpenEntry(handle), "openEntry");
        entryOpen = 1;
        return current;
    }

    public ZipEntry readCurrentInfo() {
        NativePtr buf = NativeMemory.allocate(2048L);
        int n = ZipNative.readerEntryName(handle, buf, 2048);
        ZipNative.check(n, "entryName");
        String name = NativeMemory.fromCString(buf);
        NativeMemory.free(buf);
        ZipEntry e = new ZipEntry(name);
        e.size = ZipNative.readerEntryUncompressedSize(handle);
        e.compressedSize = ZipNative.readerEntryCompressedSize(handle);
        e.crc = CRC32.crc32ToLong(ZipNative.readerEntryCrc32(handle));
        e.method = ZipNative.readerEntryMethod(handle);
        e.directory = ZipNative.readerEntryIsDir(handle);
        e.encrypted = ZipNative.readerEntryEncrypted(handle);
        e.aesStrength = ZipNative.readerEntryAesStrength(handle);
        e.symlink = ZipNative.readerEntryIsSymlink(handle);
        return e;
    }

    public void closeEntry() {
        if (entryOpen == 0) {
            return;
        }
        /* Final CRC/auth check — required before treating entry as verified. */
        ZipNative.check(ZipNative.readerCloseEntry(handle), "closeEntry");
        entryOpen = 0;
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
            throw new IOException("stream closed");
        }
        if (entryOpen == 0) {
            return -1;
        }
        if (b == null || len < 0 || off < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("read");
        }
        if (len == 0) {
            return 0;
        }
        NativePtr p = NativeMemory.allocate(0L + len + 4);
        int n = ZipNative.readerRead(handle, p, len);
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
        try {
            if (entryOpen != 0) {
                closeEntry();
            }
            ZipNative.check(ZipNative.readerClose(handle), "close");
        } finally {
            ZipNative.readerDestroy(handle);
            handle = NativePtr.NULL;
            closed = 1;
        }
    }
}

/**
 * Random-access style archive reader (central directory).
 * getInputStream(entry) opens that entry for sequential read via ZipFileEntryStream.
 */
