// JNative IO Streams / ByteBuffer (Core Completion Round 4).
// Contract: InputStream.read → -1 at EOF (never 0 for single-byte read unless byte value 0).
// Partial native I/O: File streams loop until requested length, EOF, or error.

class BufferException extends RuntimeException {
    BufferException(String message) {
        super(message);
    }
}

class BufferOverflowException extends BufferException {
    BufferOverflowException(String message) {
        super(message);
    }
}

class BufferUnderflowException extends BufferException {
    BufferUnderflowException(String message) {
        super(message);
    }
}

/**
 * Heap ByteBuffer. position/limit/capacity invariants enforced;
 * invalid ops throw BufferException (no native corruption).
 */
class ByteBuffer {
    byte[] hb;
    int capacity;
    int position;
    int limit;
    int offset;
    int readOnly;

    ByteBuffer(byte[] buf, int off, int cap, int lim, int pos, int ro) {
        hb = buf;
        offset = off;
        capacity = cap;
        limit = lim;
        position = pos;
        readOnly = ro;
    }

    static ByteBuffer allocate(int capacity) {
        if (capacity < 0) {
            throw new BufferException("negative capacity");
        }
        byte[] b = new byte[capacity];
        return new ByteBuffer(b, 0, capacity, capacity, 0, 0);
    }

    static ByteBuffer wrap(byte[] array) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        return new ByteBuffer(array, 0, array.length, array.length, 0, 0);
    }

    static ByteBuffer wrap(byte[] array, int offset, int length) {
        if (array == null) {
            throw new NullPointerException("array");
        }
        if (offset < 0 || length < 0 || offset + length > array.length) {
            throw new IndexOutOfBoundsException("wrap bounds");
        }
        return new ByteBuffer(array, offset, length, length, 0, 0);
    }

    int capacity() {
        return capacity;
    }

    int position() {
        return position;
    }

    ByteBuffer position(int newPosition) {
        if (newPosition < 0 || newPosition > limit) {
            throw new BufferException("position > limit or negative");
        }
        position = newPosition;
        return this;
    }

    int limit() {
        return limit;
    }

    ByteBuffer limit(int newLimit) {
        if (newLimit < 0 || newLimit > capacity) {
            throw new BufferException("limit > capacity or negative");
        }
        limit = newLimit;
        if (position > limit) {
            position = limit;
        }
        return this;
    }

    int remaining() {
        return limit - position;
    }

    boolean hasRemaining() {
        return position < limit;
    }

    ByteBuffer clear() {
        position = 0;
        limit = capacity;
        return this;
    }

    ByteBuffer flip() {
        limit = position;
        position = 0;
        return this;
    }

    ByteBuffer rewind() {
        position = 0;
        return this;
    }

    /** Returns signed byte value. */
    int get() {
        if (position >= limit) {
            throw new BufferUnderflowException("get beyond limit");
        }
        int v = hb[offset + position];
        position = position + 1;
        return v;
    }

    ByteBuffer get(byte[] dst) {
        return get(dst, 0, dst.length);
    }

    ByteBuffer get(byte[] dst, int off, int len) {
        if (dst == null) {
            throw new NullPointerException("dst");
        }
        if (off < 0 || len < 0 || off + len > dst.length) {
            throw new IndexOutOfBoundsException("get bounds");
        }
        if (len > remaining()) {
            throw new BufferUnderflowException("get beyond limit");
        }
        int i = 0;
        while (i < len) {
            dst[off + i] = hb[offset + position];
            position = position + 1;
            i = i + 1;
        }
        return this;
    }

    int getAt(int index) {
        if (index < 0 || index >= limit) {
            throw new IndexOutOfBoundsException("getAt");
        }
        return hb[offset + index];
    }

    ByteBuffer put(int b) {
        if (readOnly != 0) {
            throw new BufferException("read-only");
        }
        if (position >= limit) {
            throw new BufferOverflowException("put beyond limit");
        }
        int v = b % 256;
        if (v > 127) {
            hb[offset + position] = v - 256;
        } else {
            hb[offset + position] = v;
        }
        position = position + 1;
        return this;
    }

    ByteBuffer put(byte[] src) {
        return put(src, 0, src.length);
    }

    ByteBuffer put(byte[] src, int off, int len) {
        if (readOnly != 0) {
            throw new BufferException("read-only");
        }
        if (src == null) {
            throw new NullPointerException("src");
        }
        if (off < 0 || len < 0 || off + len > src.length) {
            throw new IndexOutOfBoundsException("put bounds");
        }
        if (len > remaining()) {
            throw new BufferOverflowException("put beyond limit");
        }
        int i = 0;
        while (i < len) {
            hb[offset + position] = src[off + i];
            position = position + 1;
            i = i + 1;
        }
        return this;
    }

    ByteBuffer putAt(int index, int b) {
        if (readOnly != 0) {
            throw new BufferException("read-only");
        }
        if (index < 0 || index >= limit) {
            throw new IndexOutOfBoundsException("putAt");
        }
        int v = b % 256;
        if (v > 127) {
            hb[offset + index] = v - 256;
        } else {
            hb[offset + index] = v;
        }
        return this;
    }

    ByteBuffer slice() {
        int rem = remaining();
        return new ByteBuffer(hb, offset + position, rem, rem, 0, readOnly);
    }

    ByteBuffer duplicate() {
        return new ByteBuffer(hb, offset, capacity, limit, position, readOnly);
    }

    byte[] array() {
        return hb;
    }

    int arrayOffset() {
        return offset;
    }
}

/** Bounded pool of heap ByteBuffers (no unbounded native cache). */
class ByteBufferPool {
    ArrayList<ByteBuffer> free;
    int maxPooled;
    int allocated;
    int defaultCapacity;

    ByteBufferPool(int defaultCapacity, int maxPooled) {
        this.defaultCapacity = defaultCapacity;
        this.maxPooled = maxPooled;
        free = new ArrayList<ByteBuffer>();
        allocated = 0;
    }

    ByteBuffer acquire() {
        if (free.size() > 0) {
            ByteBuffer b = free.removeAt(free.size() - 1);
            b.clear();
            return b;
        }
        allocated = allocated + 1;
        return ByteBuffer.allocate(defaultCapacity);
    }

    void release(ByteBuffer buf) {
        if (buf == null) {
            return;
        }
        if (free.size() >= maxPooled) {
            return;
        }
        if (buf.capacity() != defaultCapacity) {
            return;
        }
        buf.clear();
        free.add(buf);
    }

    int pooledCount() {
        return free.size();
    }

    int allocatedCount() {
        return allocated;
    }
}

class StreamNative {
    @Native(symbol = "jnative_fs_stream_open")
    static native long open(String path, int mode);

    @Native(symbol = "jnative_fs_stream_read")
    static native int read(long fd, NativePtr buf, int len);

    @Native(symbol = "jnative_fs_stream_write")
    static native int write(long fd, NativePtr buf, int len);

    @Native(symbol = "jnative_fs_stream_close")
    static native int close(long fd);

    @Native(symbol = "jnative_fs_stream_fsync")
    static native int fsync(long fd);

    @Native(symbol = "jnative_fs_stream_seek")
    static native int seek(long fd, long offset);

    static int MODE_READ = 0;
    static int MODE_WRITE_TRUNC = 1;
    static int MODE_APPEND = 2;

    static void copyToNative(byte[] src, int srcOff, NativePtr dst, int len) {
        int i = 0;
        while (i < len) {
            int v = src[srcOff + i];
            if (v < 0) {
                v = v + 256;
            }
            StreamNative.putU8(dst, i, v);
            i = i + 1;
        }
    }

    static void copyFromNative(NativePtr src, byte[] dst, int dstOff, int len) {
        int i = 0;
        while (i < len) {
            int v = StreamNative.getU8(src, i);
            if (v > 127) {
                dst[dstOff + i] = v - 256;
            } else {
                dst[dstOff + i] = v;
            }
            i = i + 1;
        }
    }

    static void putU8(NativePtr p, int index, int value) {
        long base = 0L + ((index / 4) * 4);
        int word = NativeMemory.getInt(p, base);
        int lane = value % 256;
        if (lane < 0) {
            lane = lane + 256;
        }
        int b0 = word % 256;
        if (b0 < 0) {
            b0 = b0 + 256;
        }
        int w2 = word / 256;
        int b1 = w2 % 256;
        if (b1 < 0) {
            b1 = b1 + 256;
        }
        int w3 = w2 / 256;
        int b2 = w3 % 256;
        if (b2 < 0) {
            b2 = b2 + 256;
        }
        int b3 = w3 / 256;
        if (b3 < 0) {
            b3 = b3 + 256;
        }
        int laneIdx = index % 4;
        if (laneIdx == 0) {
            b0 = lane;
        } else if (laneIdx == 1) {
            b1 = lane;
        } else if (laneIdx == 2) {
            b2 = lane;
        } else {
            b3 = lane;
        }
        NativeMemory.putInt(p, base, b0 + (b1 * 256) + (b2 * 65536) + (b3 * 16777216));
    }

    static int getU8(NativePtr p, int index) {
        long base = 0L + ((index / 4) * 4);
        int word = NativeMemory.getInt(p, base);
        int laneIdx = index % 4;
        int v = word;
        int i = 0;
        while (i < laneIdx) {
            v = v / 256;
            i = i + 1;
        }
        v = v % 256;
        if (v < 0) {
            v = v + 256;
        }
        return v;
    }
}

/** Abstract byte source. EOF for read() / read(byte[]) is -1. */
class InputStream implements AutoCloseable {
    int closed;

    InputStream() {
        closed = 0;
    }

    /** Single byte 0..255, or -1 at EOF. */
    int read() {
        byte[] one = new byte[1];
        int n = read(one, 0, 1);
        if (n <= 0) {
            return -1;
        }
        int v = one[0];
        if (v < 0) {
            return v + 256;
        }
        return v;
    }

    int read(byte[] b) {
        if (b == null) {
            throw new NullPointerException("b");
        }
        return read(b, 0, b.length);
    }

    int read(byte[] b, int off, int len) {
        throw new IOException("InputStream.read not implemented");
    }

    long skip(long n) {
        if (n <= 0L) {
            return 0L;
        }
        long left = n;
        byte[] buf = new byte[4096];
        while (left > 0L) {
            int want = 4096;
            if (left < 4096L) {
                /* long→int without cast: left fits in 12 bits here */
                want = 0;
                long t = left;
                while (t > 0L) {
                    want = want + 1;
                    t = t - 1L;
                }
            }
            int got = read(buf, 0, want);
            if (got < 0) {
                break;
            }
            left = left - got;
        }
        return n - left;
    }

    int available() {
        return 0;
    }

    void close() {
        closed = 1;
    }

    void ensureOpen() {
        if (closed != 0) {
            throw new IOException("stream closed");
        }
    }
}

class OutputStream implements AutoCloseable {
    int closed;

    OutputStream() {
        closed = 0;
    }

    void write(int b) {
        byte[] one = new byte[1];
        if (b > 127) {
            one[0] = b - 256;
        } else {
            one[0] = b;
        }
        write(one, 0, 1);
    }

    void write(byte[] b) {
        if (b == null) {
            throw new NullPointerException("b");
        }
        write(b, 0, b.length);
    }

    void write(byte[] b, int off, int len) {
        throw new IOException("OutputStream.write not implemented");
    }

    void flush() {
    }

    void close() {
        closed = 1;
    }

    void ensureOpen() {
        if (closed != 0) {
            throw new IOException("stream closed");
        }
    }
}

class ByteArrayInputStream extends InputStream {
    byte[] buf;
    int pos;
    int count;

    ByteArrayInputStream(byte[] buf) {
        this.buf = buf;
        pos = 0;
        if (buf == null) {
            count = 0;
        } else {
            count = buf.length;
        }
    }

    ByteArrayInputStream(byte[] buf, int offset, int length) {
        this.buf = buf;
        pos = offset;
        count = offset + length;
        if (buf == null || offset < 0 || length < 0 || count > buf.length) {
            throw new IndexOutOfBoundsException("ByteArrayInputStream");
        }
    }

    int read(byte[] b, int off, int len) {
        ensureOpen();
        if (b == null) {
            throw new NullPointerException("b");
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("read bounds");
        }
        if (pos >= count) {
            return -1;
        }
        int avail = count - pos;
        int n = len;
        if (n > avail) {
            n = avail;
        }
        if (n <= 0) {
            return 0;
        }
        int i = 0;
        while (i < n) {
            b[off + i] = buf[pos];
            pos = pos + 1;
            i = i + 1;
        }
        return n;
    }

    int available() {
        ensureOpen();
        return count - pos;
    }
}

class ByteArrayOutputStream extends OutputStream {
    byte[] buf;
    int count;

    ByteArrayOutputStream() {
        buf = new byte[32];
        count = 0;
    }

    ByteArrayOutputStream(int size) {
        if (size < 1) {
            size = 1;
        }
        buf = new byte[size];
        count = 0;
    }

    void ensure(int need) {
        if (need <= buf.length) {
            return;
        }
        int ncap = buf.length * 2;
        while (ncap < need) {
            ncap = ncap * 2;
        }
        byte[] nb = new byte[ncap];
        Bytes.copy(buf, 0, nb, 0, count);
        buf = nb;
    }

    void write(byte[] b, int off, int len) {
        ensureOpen();
        if (b == null) {
            throw new NullPointerException("b");
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("write bounds");
        }
        ensure(count + len);
        int i = 0;
        while (i < len) {
            buf[count] = b[off + i];
            count = count + 1;
            i = i + 1;
        }
    }

    byte[] toByteArray() {
        byte[] out = new byte[count];
        Bytes.copy(buf, 0, out, 0, count);
        return out;
    }

    String toStringUtf8() {
        return Bytes.toString(toByteArray());
    }

    int size() {
        return count;
    }
}

class FileInputStream extends InputStream {
    long fd;
    String path;

    FileInputStream(String path) {
        this.path = path;
        fd = StreamNative.open(path, StreamNative.MODE_READ);
        if (fd < 0L) {
            throw new IOException("open failed: " + path);
        }
        ResourceTracker.trackOpen("file");
    }

    FileInputStream(FsPath path) {
        this(path.toString());
    }

    int read(byte[] b, int off, int len) {
        ensureOpen();
        if (b == null) {
            throw new NullPointerException("b");
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("read bounds");
        }
        if (len == 0) {
            return 0;
        }
        NativePtr nb = NativeMemory.allocate(0L + len + 4);
        int n = StreamNative.read(fd, nb, len);
        if (n < 0) {
            NativeMemory.free(nb);
            throw new IOException("read failed: " + path);
        }
        if (n == 0) {
            NativeMemory.free(nb);
            return -1;
        }
        StreamNative.copyFromNative(nb, b, off, n);
        NativeMemory.free(nb);
        return n;
    }

    void seek(long offset) {
        ensureOpen();
        if (offset < 0L) {
            throw new IOException("seek negative: " + path);
        }
        if (StreamNative.seek(fd, offset) != 0) {
            throw new IOException("seek failed: " + path);
        }
    }

    void close() {
        if (closed == 0) {
            if (fd >= 0L) {
                StreamNative.close(fd);
                fd = -1L;
                ResourceTracker.trackClose("file");
            }
            closed = 1;
        }
    }
}

class FileOutputStream extends OutputStream {
    long fd;
    String path;

    FileOutputStream(String path) {
        this(path, 0);
    }

    FileOutputStream(String path, int append) {
        this.path = path;
        int mode = StreamNative.MODE_WRITE_TRUNC;
        if (append != 0) {
            mode = StreamNative.MODE_APPEND;
        }
        fd = StreamNative.open(path, mode);
        if (fd < 0L) {
            throw new IOException("open failed: " + path);
        }
        ResourceTracker.trackOpen("file");
    }

    FileOutputStream(FsPath path) {
        this(path.toString(), 0);
    }

    void write(byte[] b, int off, int len) {
        ensureOpen();
        if (b == null) {
            throw new NullPointerException("b");
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("write bounds");
        }
        int done = 0;
        while (done < len) {
            int chunk = len - done;
            NativePtr nb = NativeMemory.allocate(0L + chunk + 4);
            StreamNative.copyToNative(b, off + done, nb, chunk);
            int n = StreamNative.write(fd, nb, chunk);
            NativeMemory.free(nb);
            if (n < 0) {
                throw new IOException("write failed: " + path);
            }
            if (n == 0) {
                throw new IOException("write failed: short write zero: " + path);
            }
            done = done + n;
        }
    }

    void flush() {
        ensureOpen();
        if (StreamNative.fsync(fd) != 0) {
            throw new IOException("fsync failed: " + path);
        }
    }

    void close() {
        if (closed == 0) {
            if (fd >= 0L) {
                StreamNative.close(fd);
                fd = -1L;
                ResourceTracker.trackClose("file");
            }
            closed = 1;
        }
    }
}

class BufferedInputStream extends InputStream {
    InputStream in;
    byte[] buf;
    int pos;
    int count;

    BufferedInputStream(InputStream in) {
        this(in, 8192);
    }

    BufferedInputStream(InputStream in, int size) {
        this.in = in;
        if (size < 1) {
            size = 1;
        }
        buf = new byte[size];
        pos = 0;
        count = 0;
    }

    int fill() {
        int n = in.read(buf, 0, buf.length);
        if (n < 0) {
            count = 0;
            pos = 0;
            return -1;
        }
        pos = 0;
        count = n;
        return n;
    }

    int read(byte[] b, int off, int len) {
        ensureOpen();
        if (b == null) {
            throw new NullPointerException("b");
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("read bounds");
        }
        if (len == 0) {
            return 0;
        }
        int total = 0;
        while (total < len) {
            if (pos >= count) {
                if (fill() < 0) {
                    if (total == 0) {
                        return -1;
                    }
                    return total;
                }
            }
            int avail = count - pos;
            int want = len - total;
            if (want > avail) {
                want = avail;
            }
            int i = 0;
            while (i < want) {
                b[off + total + i] = buf[pos];
                pos = pos + 1;
                i = i + 1;
            }
            total = total + want;
        }
        return total;
    }

    int available() {
        ensureOpen();
        return (count - pos) + in.available();
    }

    void close() {
        if (closed == 0) {
            in.close();
            closed = 1;
        }
    }
}

class BufferedOutputStream extends OutputStream {
    OutputStream out;
    byte[] buf;
    int count;

    BufferedOutputStream(OutputStream out) {
        this(out, 8192);
    }

    BufferedOutputStream(OutputStream out, int size) {
        this.out = out;
        if (size < 1) {
            size = 1;
        }
        buf = new byte[size];
        count = 0;
    }

    void write(byte[] b, int off, int len) {
        ensureOpen();
        if (b == null) {
            throw new NullPointerException("b");
        }
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("write bounds");
        }
        if (len >= buf.length) {
            flush();
            out.write(b, off, len);
            return;
        }
        if (len > buf.length - count) {
            flush();
        }
        int i = 0;
        while (i < len) {
            buf[count] = b[off + i];
            count = count + 1;
            i = i + 1;
        }
    }

    void flush() {
        ensureOpen();
        if (count > 0) {
            out.write(buf, 0, count);
            count = 0;
        }
        out.flush();
    }

    void close() {
        if (closed == 0) {
            flush();
            out.close();
            closed = 1;
        }
    }
}

/** UTF-8 character reader over an InputStream. */
class InputStreamReader extends Reader {
    InputStream in;

    InputStreamReader(InputStream in) {
        this.in = in;
    }

    int read(char[] cbuf, int off, int len) {
        ensureOpen();
        if (cbuf == null) {
            throw new NullPointerException("cbuf");
        }
        if (off < 0 || len < 0 || off + len > cbuf.length) {
            throw new IndexOutOfBoundsException("read bounds");
        }
        if (len == 0) {
            return 0;
        }
        /* Decode UTF-8 one code unit at a time (BMP / surrogate pairs as char). */
        int written = 0;
        while (written < len) {
            int b0 = in.read();
            if (b0 < 0) {
                if (written == 0) {
                    return -1;
                }
                return written;
            }
            int cp = 0;
            if (b0 < 128) {
                cp = b0;
            } else if (b0 < 224) {
                int b1 = in.read();
                if (b1 < 0) {
                    throw new IOException("truncated UTF-8");
                }
                cp = ((b0 % 32) * 64) + (b1 % 64);
            } else if (b0 < 240) {
                int b1 = in.read();
                int b2 = in.read();
                if (b1 < 0 || b2 < 0) {
                    throw new IOException("truncated UTF-8");
                }
                cp = ((b0 % 16) * 4096) + ((b1 % 64) * 64) + (b2 % 64);
            } else {
                int b1 = in.read();
                int b2 = in.read();
                int b3 = in.read();
                if (b1 < 0 || b2 < 0 || b3 < 0) {
                    throw new IOException("truncated UTF-8");
                }
                cp = ((b0 % 8) * 262144) + ((b1 % 64) * 4096) + ((b2 % 64) * 64) + (b3 % 64);
                if (cp > 65535) {
                    throw new IOException("UTF-8 codepoint beyond BMP not supported in Reader");
                }
            }
            cbuf[off + written] = cp;
            written = written + 1;
        }
        return written;
    }

    void close() {
        if (closed == 0) {
            in.close();
            closed = 1;
        }
    }
}

class OutputStreamWriter extends Writer {
    OutputStream out;

    OutputStreamWriter(OutputStream out) {
        this.out = out;
    }

    void write(String s) {
        ensureOpen();
        if (s == null) {
            throw new NullPointerException("s");
        }
        int n = s.length();
        int i = 0;
        while (i < n) {
            int cp = s.charAt(i);
            if (cp < 0) {
                cp = cp + 65536;
            }
            if (cp < 128) {
                out.write(cp);
            } else if (cp < 2048) {
                out.write(192 + (cp / 64));
                out.write(128 + (cp % 64));
            } else {
                out.write(224 + (cp / 4096));
                out.write(128 + ((cp / 64) % 64));
                out.write(128 + (cp % 64));
            }
            i = i + 1;
        }
    }

    void write(char[] cbuf, int off, int len) {
        ensureOpen();
        if (cbuf == null) {
            throw new NullPointerException("cbuf");
        }
        if (off < 0 || len < 0 || off + len > cbuf.length) {
            throw new IndexOutOfBoundsException("write bounds");
        }
        int i = 0;
        while (i < len) {
            int cp = cbuf[off + i];
            if (cp < 0) {
                cp = cp + 65536;
            }
            if (cp < 128) {
                out.write(cp);
            } else if (cp < 2048) {
                out.write(192 + (cp / 64));
                out.write(128 + (cp % 64));
            } else {
                out.write(224 + (cp / 4096));
                out.write(128 + ((cp / 64) % 64));
                out.write(128 + (cp % 64));
            }
            i = i + 1;
        }
    }

    void flush() {
        ensureOpen();
        out.flush();
    }

    void close() {
        if (closed == 0) {
            flush();
            out.close();
            closed = 1;
        }
    }
}

class Reader implements AutoCloseable {
    int closed;

    Reader() {
        closed = 0;
    }

    int read() {
        char[] one = new char[1];
        int n = read(one, 0, 1);
        if (n <= 0) {
            return -1;
        }
        return one[0];
    }

    int read(char[] cbuf) {
        return read(cbuf, 0, cbuf.length);
    }

    int read(char[] cbuf, int off, int len) {
        throw new IOException("Reader.read not implemented");
    }

    void close() {
        closed = 1;
    }

    void ensureOpen() {
        if (closed != 0) {
            throw new IOException("reader closed");
        }
    }
}

class Writer implements AutoCloseable {
    int closed;

    Writer() {
        closed = 0;
    }

    void write(int c) {
        char[] one = new char[1];
        one[0] = c;
        write(one, 0, 1);
    }

    void write(char[] cbuf) {
        write(cbuf, 0, cbuf.length);
    }

    void write(char[] cbuf, int off, int len) {
        throw new IOException("Writer.write not implemented");
    }

    void write(String s) {
        if (s == null) {
            throw new NullPointerException("s");
        }
        int n = s.length();
        char[] cbuf = new char[n];
        int i = 0;
        while (i < n) {
            cbuf[i] = s.charAt(i);
            i = i + 1;
        }
        write(cbuf, 0, n);
    }

    void flush() {
    }

    void close() {
        closed = 1;
    }

    void ensureOpen() {
        if (closed != 0) {
            throw new IOException("writer closed");
        }
    }
}

class BufferedReader extends Reader {
    Reader in;
    char[] buf;
    int pos;
    int count;

    BufferedReader(Reader in) {
        this(in, 8192);
    }

    BufferedReader(Reader in, int size) {
        this.in = in;
        if (size < 1) {
            size = 1;
        }
        buf = new char[size];
        pos = 0;
        count = 0;
    }

    int fill() {
        int n = in.read(buf, 0, buf.length);
        if (n < 0) {
            count = 0;
            pos = 0;
            return -1;
        }
        pos = 0;
        count = n;
        return n;
    }

    int read(char[] cbuf, int off, int len) {
        ensureOpen();
        if (len == 0) {
            return 0;
        }
        if (pos >= count) {
            if (fill() < 0) {
                return -1;
            }
        }
        int avail = count - pos;
        int n = len;
        if (n > avail) {
            n = avail;
        }
        int i = 0;
        while (i < n) {
            cbuf[off + i] = buf[pos];
            pos = pos + 1;
            i = i + 1;
        }
        return n;
    }

    String readLine() {
        ensureOpen();
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= count) {
                if (fill() < 0) {
                    if (sb.length() == 0) {
                        return null;
                    }
                    return sb.toString();
                }
            }
            int c = buf[pos];
            pos = pos + 1;
            if (c == 10) {
                return sb.toString();
            }
            if (c == 13) {
                if (pos >= count) {
                    fill();
                }
                if (pos < count && buf[pos] == 10) {
                    pos = pos + 1;
                }
                return sb.toString();
            }
            sb.append(String.valueOf(c));
        }
        return null;
    }

    void close() {
        if (closed == 0) {
            in.close();
            closed = 1;
        }
    }
}

class BufferedWriter extends Writer {
    Writer out;
    char[] buf;
    int count;

    BufferedWriter(Writer out) {
        this(out, 8192);
    }

    BufferedWriter(Writer out, int size) {
        this.out = out;
        if (size < 1) {
            size = 1;
        }
        buf = new char[size];
        count = 0;
    }

    void write(char[] cbuf, int off, int len) {
        ensureOpen();
        int i = 0;
        while (i < len) {
            if (count >= buf.length) {
                flush();
            }
            buf[count] = cbuf[off + i];
            count = count + 1;
            i = i + 1;
        }
    }

    void newLine() {
        write("\n");
    }

    void flush() {
        ensureOpen();
        if (count > 0) {
            out.write(buf, 0, count);
            count = 0;
        }
        out.flush();
    }

    void close() {
        if (closed == 0) {
            flush();
            out.close();
            closed = 1;
        }
    }
}
