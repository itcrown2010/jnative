package org.jnative.archive.internal;

import java.util.zip.ZipException;

/**
 * Native bindings to jn_zip. Internal package — prefer java.util.zip / ArchiveOps.
 * @author 张晓达
 */
public class ZipNative {
    @Native(symbol = "jn_zip_writer_create", library = "jn_zip")
    public static native NativePtr writerCreate();

    @Native(symbol = "jn_zip_writer_open", library = "jn_zip")
    public static native int writerOpen(NativePtr w, String path, int append);

    @Native(symbol = "jn_zip_writer_set_password", library = "jn_zip")
    public static native int writerSetPassword(NativePtr w, String password);

    @Native(symbol = "jn_zip_writer_set_aes_strength", library = "jn_zip")
    public static native int writerSetAesStrength(NativePtr w, int strength);

    @Native(symbol = "jn_zip_writer_put_next_entry", library = "jn_zip")
    public static native int writerPutNextEntry(NativePtr w, String name, int method, int level, int isDir);

    @Native(symbol = "jn_zip_writer_request_cancel", library = "jn_zip")
    public static native void writerRequestCancel(NativePtr w);

    @Native(symbol = "jn_zip_writer_stat_bytes_in", library = "jn_zip")
    public static native long writerStatBytesIn(NativePtr w);

    @Native(symbol = "jn_zip_writer_stat_entries_done", library = "jn_zip")
    public static native long writerStatEntriesDone(NativePtr w);

    @Native(symbol = "jn_zip_writer_stat_cancel_ack", library = "jn_zip")
    public static native int writerStatCancelAck(NativePtr w);

    @Native(symbol = "jn_zip_writer_stat_current_name", library = "jn_zip")
    public static native int writerStatCurrentName(NativePtr w, NativePtr buf, int cap);

    @Native(symbol = "jn_zip_writer_write", library = "jn_zip")
    public static native int writerWrite(NativePtr w, NativePtr buf, int len);

    @Native(symbol = "jn_zip_writer_close_entry", library = "jn_zip")
    public static native int writerCloseEntry(NativePtr w);

    @Native(symbol = "jn_zip_writer_add_file", library = "jn_zip")
    public static native int writerAddFile(NativePtr w, String srcPath, String entryName, int method, int level);

    @Native(symbol = "jn_zip_writer_finish", library = "jn_zip")
    public static native int writerFinish(NativePtr w);

    @Native(symbol = "jn_zip_writer_close", library = "jn_zip")
    public static native int writerClose(NativePtr w);

    @Native(symbol = "jn_zip_writer_destroy", library = "jn_zip")
    public static native void writerDestroy(NativePtr w);

    @Native(symbol = "jn_zip_reader_create", library = "jn_zip")
    public static native NativePtr readerCreate();

    @Native(symbol = "jn_zip_reader_open", library = "jn_zip")
    public static native int readerOpen(NativePtr r, String path);

    @Native(symbol = "jn_zip_reader_set_password", library = "jn_zip")
    public static native int readerSetPassword(NativePtr r, String password);

    @Native(symbol = "jn_zip_reader_goto_first", library = "jn_zip")
    public static native int readerGotoFirst(NativePtr r);

    @Native(symbol = "jn_zip_reader_goto_next", library = "jn_zip")
    public static native int readerGotoNext(NativePtr r);

    @Native(symbol = "jn_zip_reader_locate", library = "jn_zip")
    public static native int readerLocate(NativePtr r, String name, int ignoreCase);

    @Native(symbol = "jn_zip_reader_entry_name", library = "jn_zip")
    public static native int readerEntryName(NativePtr r, NativePtr buf, int cap);

    @Native(symbol = "jn_zip_reader_entry_uncompressed_size", library = "jn_zip")
    public static native long readerEntryUncompressedSize(NativePtr r);

    @Native(symbol = "jn_zip_reader_entry_compressed_size", library = "jn_zip")
    public static native long readerEntryCompressedSize(NativePtr r);

    @Native(symbol = "jn_zip_reader_entry_crc32", library = "jn_zip")
    public static native int readerEntryCrc32(NativePtr r);

    @Native(symbol = "jn_zip_reader_entry_method", library = "jn_zip")
    public static native int readerEntryMethod(NativePtr r);

    @Native(symbol = "jn_zip_reader_entry_is_dir", library = "jn_zip")
    public static native int readerEntryIsDir(NativePtr r);

    @Native(symbol = "jn_zip_reader_entry_encrypted", library = "jn_zip")
    public static native int readerEntryEncrypted(NativePtr r);

    @Native(symbol = "jn_zip_reader_entry_aes_strength", library = "jn_zip")
    public static native int readerEntryAesStrength(NativePtr r);

    @Native(symbol = "jn_zip_reader_entry_is_symlink", library = "jn_zip")
    public static native int readerEntryIsSymlink(NativePtr r);

    @Native(symbol = "jn_zip_reader_entry_needs_password", library = "jn_zip")
    public static native int readerEntryNeedsPassword(NativePtr r);

    @Native(symbol = "jn_zip_reader_open_entry", library = "jn_zip")
    public static native int readerOpenEntry(NativePtr r);

    @Native(symbol = "jn_zip_reader_request_cancel", library = "jn_zip")
    public static native void readerRequestCancel(NativePtr r);

    @Native(symbol = "jn_zip_reader_stat_bytes_out", library = "jn_zip")
    public static native long readerStatBytesOut(NativePtr r);

    @Native(symbol = "jn_zip_reader_stat_entries_done", library = "jn_zip")
    public static native long readerStatEntriesDone(NativePtr r);

    @Native(symbol = "jn_zip_reader_stat_cancel_ack", library = "jn_zip")
    public static native int readerStatCancelAck(NativePtr r);

    @Native(symbol = "jn_zip_reader_stat_current_name", library = "jn_zip")
    public static native int readerStatCurrentName(NativePtr r, NativePtr buf, int cap);

    @Native(symbol = "jn_zip_reader_read", library = "jn_zip")
    public static native int readerRead(NativePtr r, NativePtr buf, int len);

    @Native(symbol = "jn_zip_reader_close_entry", library = "jn_zip")
    public static native int readerCloseEntry(NativePtr r);

    @Native(symbol = "jn_zip_reader_extract_entry", library = "jn_zip")
    public static native int readerExtractEntry(NativePtr r, String destDir);

    @Native(symbol = "jn_zip_reader_extract_entry_ex", library = "jn_zip")
    public static native int readerExtractEntryEx(NativePtr r, String destDir, int overwrite,
            long maxEntry, long maxTotal, long maxEntries);

    @Native(symbol = "jn_zip_reader_test_all", library = "jn_zip")
    public static native int readerTestAll(NativePtr r);

    @Native(symbol = "jn_zip_reader_close", library = "jn_zip")
    public static native int readerClose(NativePtr r);

    @Native(symbol = "jn_zip_reader_destroy", library = "jn_zip")
    public static native void readerDestroy(NativePtr r);

    @Native(symbol = "jn_zip_crc32", library = "jn_zip")
    public static native int crc32(int crc, NativePtr buf, int len);

    @Native(symbol = "jn_zip_strerror", library = "jn_zip")
    public static native NativePtr strerror(int code);

    @Native(symbol = "jn_zip_engine_version", library = "jn_zip")
    public static native NativePtr engineVersion();

    @Native(symbol = "jn_zip_crypto_info", library = "jn_zip")
    public static native NativePtr cryptoInfo();

    @Native(symbol = "jn_zip_now_ms", library = "jn_zip")
    public static native long nowMs();

    @Native(symbol = "jn_zip_password_read_fd", library = "jn_zip")
    public static native int passwordReadFd(int fd, NativePtr buf, int cap);

    @Native(symbol = "jn_zip_password_read_tty", library = "jn_zip")
    public static native int passwordReadTty(String prompt, NativePtr buf, int cap);

    @Native(symbol = "jn_zip_password_wipe", library = "jn_zip")
    public static native void passwordWipe(NativePtr buf, int cap);

    @Native(symbol = "jn_zip_make_unique_temp_path", library = "jn_zip")
    public static native NativePtr makeUniqueTempPath(String finalPath);

    @Native(symbol = "jn_zip_free", library = "jn_zip")
    public static native void freePtr(NativePtr p);

    public static String errMsg(int code) {
        NativePtr p = strerror(code);
        if (p == NativePtr.NULL) {
            return "zip error " + String.valueOf(code);
        }
        return NativeMemory.fromCString(p);
    }

    public static void check(int code, String op) {
        if (code < 0) {
            throw new ZipException(op + ": " + errMsg(code));
        }
    }

    public static String engineVersionString() {
        NativePtr p = engineVersion();
        if (p == NativePtr.NULL) {
            return "";
        }
        return NativeMemory.fromCString(p);
    }

    public static String cryptoInfoString() {
        NativePtr p = cryptoInfo();
        if (p == NativePtr.NULL) {
            return "";
        }
        return NativeMemory.fromCString(p);
    }
}

/**
 * Streaming ZIP writer.
 * Ownership: this stream owns the archive handle. Entry data is written with write();
 * closeEntry() finishes one entry; finish() closes any open entry; close() is idempotent
 * and writes the central directory.
 */
