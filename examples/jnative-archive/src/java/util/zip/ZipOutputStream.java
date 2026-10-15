package java.util.zip;

import org.jnative.archive.internal.ZipNative;

/**
 * Streaming ZIP writer. Subset of java.util.zip.ZipOutputStream.
 * Constructor takes a filesystem path (JNative; JDK uses OutputStream).
 * AES password: org.jnative.archive.ZipExtensions — not on this type.
 * @author 张晓达
 */
public class ZipOutputStream extends OutputStream {
    public NativePtr handle;
    public int entryOpen;
    public int finished;
    public int level;

    public ZipOutputStream(String path) {
        handle = ZipNative.writerCreate();
        if (handle == NativePtr.NULL) {
            throw new ZipException("writer create failed");
        }
        entryOpen = 0;
        finished = 0;
        level = 6;
        int rc = ZipNative.writerOpen(handle, path, 0);
        if (rc < 0) {
            ZipNative.writerDestroy(handle);
            handle = NativePtr.NULL;
            throw new ZipException("open: " + ZipNative.errMsg(rc));
        }
    }

    /** Native handle for org.jnative.archive extensions (not a JDK API). */
    public NativePtr nativeHandle() {
        return handle;
    }

    public void setLevel(int lvl) {
        level = lvl;
    }



    public void putNextEntry(ZipEntry e) {
        if (e == null || e.name == null) {
            throw new ZipException("null entry");
        }
        if (entryOpen != 0) {
            closeEntry();
        }
        int isDir = 0;
        if (e.isDirectory()) {
            isDir = 1;
        }
        int method = e.method;
        ZipNative.check(ZipNative.writerPutNextEntry(handle, e.name, method, level, isDir), "putNextEntry");
        entryOpen = 1;
    }

    public void closeEntry() {
        if (entryOpen == 0) {
            return;
        }
        ZipNative.check(ZipNative.writerCloseEntry(handle), "closeEntry");
        entryOpen = 0;
    }

    public void write(int b) {
        byte[] one = new byte[1];
        one[0] = b;
        write(one, 0, 1);
    }

    public void write(byte[] b, int off, int len) {
        if (closed != 0 || finished != 0) {
            throw new IOException("stream closed");
        }
        if (entryOpen == 0) {
            throw new ZipException("no open entry");
        }
        if (b == null || len < 0 || off < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("write");
        }
        if (len == 0) {
            return;
        }
        NativePtr p = NativeMemory.allocate(0L + len + 4);
        StreamNative.copyToNative(b, off, p, len);
        int n = ZipNative.writerWrite(handle, p, len);
        NativeMemory.free(p);
        ZipNative.check(n, "write");
    }

    /**
     * Completes the archive (writes central directory). After success the file is a
     * valid ZIP readable by external tools. Further putNextEntry is rejected.
     * close() only releases the native handle and must not rewrite the archive.
     */
    public void finish() {
        if (finished != 0) {
            return;
        }
        if (entryOpen != 0) {
            closeEntry();
        }
        ZipNative.check(ZipNative.writerFinish(handle), "finish");
        finished = 1;
    }

    public void close() {
        if (closed != 0) {
            return;
        }
        ZipException pending = null;
        if (finished == 0 && handle != NativePtr.NULL) {
            try {
                finish();
            } catch (Exception ex) {
                String m = ex.getMessage();
                if (m == null) {
                    m = "finish failed";
                }
                pending = new ZipException(m);
            }
        }
        if (handle != NativePtr.NULL) {
            ZipNative.writerDestroy(handle);
            handle = NativePtr.NULL;
        }
        closed = 1;
        if (pending != null) {
            throw pending;
        }
    }
}

/**
 * Sequential ZIP reader (ZipInputStream semantics).
 * getNextEntry advances; read() reads current entry; closeEntry optional before next.
 * Parent stream close closes the archive (idempotent).
 */
