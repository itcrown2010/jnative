package org.jnative.archive;

import java.util.zip.ZipException;
import org.jnative.archive.internal.ZipNative;

/**
 * Shared archive task model for CLI and GUI.
 * @author 张晓达
 */

public class ArchiveErrorCode {
    public static int OK = 0;
    public static int PARAM = -2;
    public static int IO = -3;
    public static int FORMAT = -4;
    public static int CRC = -5;
    public static int PASSWORD = -6;
    public static int UNSUPPORTED = -7;
    public static int PATH = -8;
    public static int EOF = -9;
    public static int STATE = -10;
    public static int EXISTS = -11;
    public static int NEED_PASSWORD = -12;
    public static int CANCELLED = -13;
    public static int LIMIT = -14;
    public static int LINK = -15;
    public static int SPACE = -16;
    public static int SOURCE = -17;
    public static int TARGET = -18;

    public static String nameOf(int code) {
        if (code == OK) {
            return "OK";
        }
        if (code == NEED_PASSWORD) {
            return "NEED_PASSWORD";
        }
        if (code == PASSWORD) {
            return "PASSWORD_OR_INTEGRITY";
        }
        if (code == CANCELLED) {
            return "CANCELLED";
        }
        if (code == PATH) {
            return "PATH_SECURITY";
        }
        if (code == LINK) {
            return "LINK_REJECTED";
        }
        if (code == LIMIT) {
            return "LIMIT";
        }
        if (code == SPACE) {
            return "SPACE";
        }
        if (code == SOURCE) {
            return "SOURCE";
        }
        if (code == TARGET) {
            return "TARGET";
        }
        if (code == FORMAT) {
            return "FORMAT";
        }
        if (code == CRC) {
            return "CRC";
        }
        if (code == UNSUPPORTED) {
            return "UNSUPPORTED";
        }
        if (code == EXISTS) {
            return "EXISTS";
        }
        return "ERROR";
    }
}

/**
 * Extract publish policy. ASK is resolved in ArchiveOps (never passed to native);
 * batch mode must not wait forever on ASK.
 */
public class ArchiveOverwrite {
    public static int FAIL = 0;
    public static int SKIP = 1;
    public static int REPLACE = 2;
    public static int RENAME = 3;
    public static int ASK = 4;
}

public class ArchiveLimits {
    public long maxEntryUncompressed;
    public long maxTotalUncompressed;
    public long maxEntries;

    public ArchiveLimits() {
        maxEntryUncompressed = 0L;
        maxTotalUncompressed = 0L;
        maxEntries = 0L;
    }
}

/**
 * WAITING → PREPARING → RUNNING → COMPLETED | FAILED | CANCELLED.
 * requestCancel while RUNNING → CANCELLING; terminal CANCELLED only after cleanup.
 */
public class ArchiveTaskState {
    public static int WAITING = 0;
    public static int PREPARING = 1;
    public static int RUNNING = 2;
    public static int COMPLETED = 3;
    public static int FAILED = 4;
    public static int CANCELLED = 5;
    public static int CANCELLING = 6;

    public static String nameOf(int s) {
        if (s == WAITING) {
            return "WAITING";
        }
        if (s == PREPARING) {
            return "PREPARING";
        }
        if (s == RUNNING) {
            return "RUNNING";
        }
        if (s == CANCELLING) {
            return "CANCELLING";
        }
        if (s == COMPLETED) {
            return "COMPLETED";
        }
        if (s == FAILED) {
            return "FAILED";
        }
        if (s == CANCELLED) {
            return "CANCELLED";
        }
        return "UNKNOWN";
    }
}

/**
 * Progress snapshot. percent is -1 when total unknown.
 * Do not treat output growth as input progress; do not report 100% before COMPLETED.
 */
public class ArchiveProgress {
    public int state;
    public String phase;
    public String currentFile;
    public long bytesIn;
    public long bytesOut;
    public long entriesDone;
    public long entriesTotal;
    public long bytesTotalIn;
    public int percent;
    public long speedBytesPerSec;
    public int cancelRequested;
    public int cancelAcknowledged;
    public int errorCode;
    public String errorMessage;

    public ArchiveProgress() {
        state = ArchiveTaskState.WAITING;
        phase = "";
        currentFile = "";
        bytesIn = 0L;
        bytesOut = 0L;
        entriesDone = 0L;
        entriesTotal = -1L;
        bytesTotalIn = -1L;
        percent = -1;
        speedBytesPerSec = 0L;
        cancelRequested = 0;
        cancelAcknowledged = 0;
        errorCode = 0;
        errorMessage = "";
    }
}

/**
 * Task ownership: CLI/GUI worker owns the task; native writer/reader handles are
 * owned here until close + temp cleanup. CANCELLED only after markCancelledAfterCleanup().
 * Cross-thread progress/cancel: read counters via snapshot() → native locked stats;
 * do not treat Java field writes alone as the source of truth for bytes/cancel ack.
 */
public class ArchiveTask {
    public int state;
    public int cancelRequested;
    public int errorCode;
    public String errorMessage;
    public String phase;
    public String currentFile;
    public long bytesIn;
    public long bytesOut;
    public long entriesDone;
    public long entriesTotal;
    public long bytesTotalIn;
    public long startMillis;
    public long lastProgressMillis;
    public NativePtr writerHandle;
    public NativePtr readerHandle;

    public ArchiveTask() {
        state = ArchiveTaskState.WAITING;
        cancelRequested = 0;
        errorCode = 0;
        errorMessage = "";
        phase = "";
        currentFile = "";
        bytesIn = 0L;
        bytesOut = 0L;
        entriesDone = 0L;
        entriesTotal = -1L;
        bytesTotalIn = -1L;
        startMillis = 0L;
        lastProgressMillis = 0L;
        writerHandle = NativePtr.NULL;
        readerHandle = NativePtr.NULL;
    }

    public void begin(String ph) {
        state = ArchiveTaskState.PREPARING;
        phase = ph;
        startMillis = ZipNative.nowMs();
        lastProgressMillis = startMillis;
        errorCode = 0;
        errorMessage = "";
    }

    public void markRunning() {
        if (state == ArchiveTaskState.PREPARING || state == ArchiveTaskState.WAITING) {
            state = ArchiveTaskState.RUNNING;
        }
    }

    public void requestCancel() {
        cancelRequested = 1;
        if (state == ArchiveTaskState.RUNNING || state == ArchiveTaskState.PREPARING) {
            state = ArchiveTaskState.CANCELLING;
        }
        if (writerHandle != NativePtr.NULL) {
            ZipNative.writerRequestCancel(writerHandle);
        }
        if (readerHandle != NativePtr.NULL) {
            ZipNative.readerRequestCancel(readerHandle);
        }
    }

    /**
     * Record failure. CANCELLED is only set via markCancelledAfterCleanup() after
     * handles are closed and temps removed — cancelRequested alone is not terminal.
     */
    public void fail(int code, String msg) {
        if (state == ArchiveTaskState.CANCELLED || state == ArchiveTaskState.COMPLETED) {
            return;
        }
        if (code == ArchiveErrorCode.CANCELLED) {
            /* Prefer explicit cleanup completion path */
            errorCode = ArchiveErrorCode.CANCELLED;
            errorMessage = "cancelled";
            return;
        }
        state = ArchiveTaskState.FAILED;
        errorCode = code;
        if (msg == null) {
            errorMessage = ZipNative.errMsg(code);
        } else {
            errorMessage = msg;
        }
    }

    /** Call only after native stop + temp cleanup finished. */
    public void markCancelledAfterCleanup() {
        state = ArchiveTaskState.CANCELLED;
        errorCode = ArchiveErrorCode.CANCELLED;
        errorMessage = "cancelled";
        writerHandle = NativePtr.NULL;
        readerHandle = NativePtr.NULL;
    }

    public void complete() {
        if (state == ArchiveTaskState.CANCELLED) {
            return;
        }
        if (cancelRequested != 0) {
            /* Caller must cleanup then markCancelledAfterCleanup — do not lie about terminal cancel. */
            state = ArchiveTaskState.FAILED;
            errorCode = ArchiveErrorCode.CANCELLED;
            errorMessage = "cancel requested but cleanup incomplete";
            return;
        }
        state = ArchiveTaskState.COMPLETED;
        errorCode = 0;
        errorMessage = "";
    }

    /**
     * Rate-limited snapshot for UI/CLI. Returns null if called too soon (under 100ms)
     * unless force != 0 or terminal state.
     */
    public ArchiveProgress snapshot(int force) {
        long now = ZipNative.nowMs();
        int terminal = 0;
        if (state == ArchiveTaskState.COMPLETED || state == ArchiveTaskState.FAILED
                || state == ArchiveTaskState.CANCELLED) {
            terminal = 1;
        }
        if (force == 0 && terminal == 0 && now - lastProgressMillis < 100L) {
            return null;
        }
        lastProgressMillis = now;
        ArchiveProgress p = new ArchiveProgress();
        p.state = state;
        p.phase = phase;
        p.currentFile = currentFile;
        p.bytesIn = bytesIn;
        p.bytesOut = bytesOut;
        p.entriesDone = entriesDone;
        p.entriesTotal = entriesTotal;
        p.bytesTotalIn = bytesTotalIn;
        p.cancelRequested = cancelRequested;
        p.errorCode = errorCode;
        p.errorMessage = errorMessage;
        if (writerHandle != NativePtr.NULL) {
            p.bytesIn = ZipNative.writerStatBytesIn(writerHandle);
            p.entriesDone = ZipNative.writerStatEntriesDone(writerHandle);
            p.cancelAcknowledged = ZipNative.writerStatCancelAck(writerHandle);
            NativePtr buf = NativeMemory.allocate(512L);
            int n = ZipNative.writerStatCurrentName(writerHandle, buf, 512);
            if (n >= 0) {
                p.currentFile = NativeMemory.fromCString(buf);
            }
            NativeMemory.free(buf);
        }
        if (readerHandle != NativePtr.NULL) {
            p.bytesOut = ZipNative.readerStatBytesOut(readerHandle);
            p.entriesDone = ZipNative.readerStatEntriesDone(readerHandle);
            p.cancelAcknowledged = ZipNative.readerStatCancelAck(readerHandle);
            NativePtr buf = NativeMemory.allocate(512L);
            int n = ZipNative.readerStatCurrentName(readerHandle, buf, 512);
            if (n >= 0) {
                p.currentFile = NativeMemory.fromCString(buf);
            }
            NativeMemory.free(buf);
        }
        long elapsed = now - startMillis;
        if (elapsed < 1L) {
            elapsed = 1L;
        }
        long processed = p.bytesIn;
        if (processed < p.bytesOut) {
            processed = p.bytesOut;
        }
        p.speedBytesPerSec = (processed * 1000L) / elapsed;
        p.percent = -1;
        if (p.bytesTotalIn > 0L && p.bytesIn >= 0L) {
            long pct = (p.bytesIn * 100L) / p.bytesTotalIn;
            if (pct > 99L && state != ArchiveTaskState.COMPLETED) {
                pct = 99L;
            }
            if (state == ArchiveTaskState.COMPLETED) {
                pct = 100L;
            }
            p.percent = 0;
            while (pct > 0L) {
                p.percent = p.percent + 1;
                pct = pct - 1L;
            }
        }
        return p;
    }
}

/**
 * Password source: never from argv. Interactive TTY or FD.
 * Caller should wipe buffers after use via ZipNative.passwordWipe.
 */
public class ArchivePassword {
    public static String readFromFd(int fd) {
        NativePtr buf = NativeMemory.allocate(512L);
        int rc = ZipNative.passwordReadFd(fd, buf, 512);
        if (rc < 0) {
            NativeMemory.free(buf);
            throw new ZipException("password fd: " + ZipNative.errMsg(rc));
        }
        String s = NativeMemory.fromCString(buf);
        ZipNative.passwordWipe(buf, 512);
        NativeMemory.free(buf);
        return s;
    }

    public static String readInteractive(String prompt) {
        NativePtr buf = NativeMemory.allocate(512L);
        int rc = ZipNative.passwordReadTty(prompt, buf, 512);
        if (rc == ArchiveErrorCode.CANCELLED) {
            ZipNative.passwordWipe(buf, 512);
            NativeMemory.free(buf);
            throw new ZipException("password cancelled");
        }
        if (rc < 0) {
            ZipNative.passwordWipe(buf, 512);
            NativeMemory.free(buf);
            throw new ZipException("password input: " + ZipNative.errMsg(rc));
        }
        String s = NativeMemory.fromCString(buf);
        ZipNative.passwordWipe(buf, 512);
        NativeMemory.free(buf);
        if (s == null || s.length() == 0) {
            throw new ZipException("empty password rejected");
        }
        return s;
    }

    /** Create-time TTY: enter password twice; mismatch → clear error (no secret echo). */
    public static String readInteractiveConfirmed() {
        String a = readInteractive("Password: ");
        String b = readInteractive("Confirm password: ");
        if (a == null || b == null || !a.equals(b)) {
            throw new ZipException("password confirmation mismatch");
        }
        return a;
    }
}
