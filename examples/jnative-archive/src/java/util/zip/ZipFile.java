package java.util.zip;

import org.jnative.archive.internal.ZipNative;

/**
 * Central-directory ZIP reader. Subset of java.util.zip.ZipFile.
 * Limit: one open entry InputStream at a time (shared native cursor).
 * Password / test / bulk extract: org.jnative.archive.
 * @author 张晓达
 */
public class ZipFile {
    public NativePtr handle;
    public int closed;
    /** Single shared cursor — at most one open entry stream (JDK multi-stream not supported yet). */
    public ZipFileEntryStream activeEntryStream;

    public ZipFile(String path) {
        handle = ZipNative.readerCreate();
        if (handle == NativePtr.NULL) {
            throw new ZipException("reader create failed");
        }
        closed = 0;
        activeEntryStream = null;
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

    public ArrayList<ZipEntry> entries() {
        ArrayList<ZipEntry> list = new ArrayList<ZipEntry>();
        int err = ZipNative.readerGotoFirst(handle);
        if (err == -9) {
            return list;
        }
        ZipNative.check(err, "entries");
        while (err == 0) {
            NativePtr buf = NativeMemory.allocate(2048L);
            int n = ZipNative.readerEntryName(handle, buf, 2048);
            ZipNative.check(n, "name");
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
            list.add(e);
            err = ZipNative.readerGotoNext(handle);
            if (err == -9) {
                break;
            }
            ZipNative.check(err, "next");
        }
        return list;
    }

    public ZipEntry getEntry(String name) {
        int err = ZipNative.readerLocate(handle, name, 0);
        if (err == -9 || err < 0) {
            return null;
        }
        NativePtr buf = NativeMemory.allocate(2048L);
        int n = ZipNative.readerEntryName(handle, buf, 2048);
        ZipNative.check(n, "name");
        String en = NativeMemory.fromCString(buf);
        NativeMemory.free(buf);
        ZipEntry e = new ZipEntry(en);
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

    /**
     * Open entry stream. Partial reads are NOT authentication success —
     * call stream.close() (closeEntry) to finalize CRC/auth before trusting data.
     *
     * Compatibility limit: only one entry stream may be open at a time (shared reader
     * cursor). Opening a second stream is rejected without disturbing the first.
     */
    public InputStream getInputStream(ZipEntry zipEntry) {
        if (zipEntry == null || zipEntry.name == null) {
            throw new ZipException("null entry");
        }
        if (activeEntryStream != null && activeEntryStream.closed == 0) {
            throw new ZipException(
                    "another entry stream is open; close it first (single-cursor ZipFile limit)");
        }
        ZipNative.check(ZipNative.readerLocate(handle, zipEntry.name, 0), "locate");
        if (ZipNative.readerEntryNeedsPassword(handle) != 0) {
            throw new ZipException(ZipNative.errMsg(-12));
        }
        ZipNative.check(ZipNative.readerOpenEntry(handle), "openEntry");
        ZipFileEntryStream stream = new ZipFileEntryStream();
        stream.reader = handle;
        stream.fileOwner = this;
        activeEntryStream = stream;
        return stream;
    }





    public void close() {
        if (closed != 0) {
            return;
        }
        ZipNative.check(ZipNative.readerClose(handle), "close");
        ZipNative.readerDestroy(handle);
        handle = NativePtr.NULL;
        closed = 1;
    }
}
