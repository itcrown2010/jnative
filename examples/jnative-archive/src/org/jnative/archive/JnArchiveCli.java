package org.jnative.archive;

import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import org.jnative.archive.internal.ZipNative;

/**
 * JNative Archive CLI — thin front-end over ArchiveOps.
 * Password: --password-fd N or interactive TTY only (never argv).
 * @author 张晓达
 */

public class ArchiveFs {
    @Native(symbol = "rename")
    public static native int rename(String from, String to);

    @Native(symbol = "exit")
    public static native void exit(int code);

    @Native(symbol = "atoi")
    public static native int atoi(String s);
}

/**
 * Interactive conflict handler for --overwrite ask (TTY). Batch mode must not use this.
 */
public class CliConflictHandler extends ArchiveConflictHandler {
    public ArchiveConflictDecision onConflict(ArchiveConflictRequest req) {
        System.out.println("CONFLICT\t" + req.entryName + "\t" + req.destPath);
        System.out.println("Choose: [o]verwrite  [s]kip  [r]ename  [f]ail  [O/S/R/F]=apply to rest");
        /* Read decision from stdin (fd 0) so scripts can pipe; not a password channel. */
        NativePtr buf = NativeMemory.allocate(64L);
        int rc = ZipNative.passwordReadFd(0, buf, 64);
        String line = "";
        if (rc >= 0) {
            line = NativeMemory.fromCString(buf);
        }
        ZipNative.passwordWipe(buf, 64);
        NativeMemory.free(buf);
        if (line == null || line.length() == 0) {
            return new ArchiveConflictDecision(ArchiveOverwrite.FAIL, 0);
        }
        int c = line.charAt(0);
        int all = 0;
        if (c >= 65 && c <= 90) {
            all = 1;
            c = c + 32;
        }
        if (c == 111) {
            return new ArchiveConflictDecision(ArchiveOverwrite.REPLACE, all);
        }
        if (c == 115) {
            return new ArchiveConflictDecision(ArchiveOverwrite.SKIP, all);
        }
        if (c == 114) {
            return new ArchiveConflictDecision(ArchiveOverwrite.RENAME, all);
        }
        return new ArchiveConflictDecision(ArchiveOverwrite.FAIL, all);
    }
}

public class JnArchiveCli {
    public static int optPasswordFd;
    public static int optEncrypt;
    public static int optAskPassword;
    public static int optOverwrite;
    public static int optAesStrength;
    public static int optProgress;
    public static int optBatch;
    public static int optProfile;
    public static long optMaxEntry;
    public static long optMaxTotal;
    public static long optMaxEntries;
    public static long optDebugCancelAfter;

    public static String safeOptLabel(String a) {
        if (a == null) {
            return "(null)";
        }
        if (a.indexOf("password") >= 0 || a.indexOf("Password") >= 0
                || a.indexOf("passwd") >= 0 || a.indexOf("secret") >= 0) {
            return "(redacted-option)";
        }
        if (a.length() > 2 && a.charAt(0) == 45 && a.charAt(1) == 45 && a.indexOf("=") >= 0) {
            int eq = a.indexOf("=");
            return a.substring(0, eq) + "=(redacted)";
        }
        return a;
    }

    public static void main(String[] args) {
        optPasswordFd = -1;
        optEncrypt = 0;
        optAskPassword = 0;
        optOverwrite = ArchiveOverwrite.FAIL;
        optAesStrength = 3;
        optProgress = 0;
        optBatch = 0;
        optProfile = ArchiveCompressProfile.STANDARD;
        optMaxEntry = 0L;
        optMaxTotal = 0L;
        optMaxEntries = 0L;
        optDebugCancelAfter = 0L;
        if (args == null || args.length == 0) {
            usage();
            ArchiveFs.exit(2);
            return;
        }
        int i = 0;
        while (i < args.length) {
            String a = args[i];
            if (a.equals("--password-fd")) {
                i = i + 1;
                if (i >= args.length) {
                    die("missing fd after --password-fd", 2);
                }
                optPasswordFd = ArchiveFs.atoi(args[i]);
            } else if (a.equals("--encrypt")) {
                optEncrypt = 1;
                optAskPassword = 1;
            } else if (a.equals("--ask-password")) {
                optAskPassword = 1;
            } else if (a.equals("--overwrite")) {
                i = i + 1;
                if (i >= args.length) {
                    die("missing overwrite mode", 2);
                }
                optOverwrite = parseOverwrite(args[i]);
            } else if (a.equals("--aes")) {
                i = i + 1;
                if (i >= args.length) {
                    die("missing aes strength", 2);
                }
                optAesStrength = ArchiveFs.atoi(args[i]);
            } else if (a.equals("--profile") || a.equals("--level")) {
                i = i + 1;
                if (i >= args.length) {
                    die("missing profile (fast|standard|max)", 2);
                }
                optProfile = parseProfile(args[i]);
            } else if (a.equals("--progress")) {
                optProgress = 1;
            } else if (a.equals("--batch")) {
                optBatch = 1;
            } else if (a.equals("--max-entry-bytes")) {
                i = i + 1;
                if (i >= args.length) {
                    die("missing value after --max-entry-bytes", 2);
                }
                optMaxEntry = 0L + ArchiveFs.atoi(args[i]);
            } else if (a.equals("--max-total-bytes")) {
                i = i + 1;
                if (i >= args.length) {
                    die("missing value after --max-total-bytes", 2);
                }
                optMaxTotal = 0L + ArchiveFs.atoi(args[i]);
            } else if (a.equals("--max-entries")) {
                i = i + 1;
                if (i >= args.length) {
                    die("missing value after --max-entries", 2);
                }
                optMaxEntries = 0L + ArchiveFs.atoi(args[i]);
            } else if (a.equals("--debug-cancel-after-bytes")) {
                i = i + 1;
                if (i >= args.length) {
                    die("missing value after --debug-cancel-after-bytes", 2);
                }
                optDebugCancelAfter = 0L + ArchiveFs.atoi(args[i]);
            } else if (a.equals("--help") || a.equals("-h")) {
                usage();
                return;
            } else if (a.length() > 0 && a.charAt(0) == 45) {
                die("unknown or unsupported option " + safeOptLabel(a)
                        + " (use --password-fd or TTY; never pass secrets on argv)", 2);
            } else {
                break;
            }
            i = i + 1;
        }
        if (i >= args.length) {
            usage();
            ArchiveFs.exit(2);
            return;
        }
        String cmd = args[i];
        String[] rest = shift(args, i + 1);
        try {
            if (cmd.equals("version") || cmd.equals("--version")) {
                System.out.println(ZipNative.engineVersionString());
                System.out.println(ZipNative.cryptoInfoString());
                System.out.println("profiles: fast|standard|max (ZIP deflate); formats: ZIP, AES-ZIP");
            } else if (cmd.equals("create")) {
                cmdCreate(rest);
            } else if (cmd.equals("list")) {
                cmdList(rest);
            } else if (cmd.equals("test")) {
                cmdTest(rest);
            } else if (cmd.equals("extract")) {
                cmdExtract(rest);
            } else if (cmd.equals("help")) {
                usage();
            } else {
                usage();
                ArchiveFs.exit(2);
            }
        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg == null) {
                msg = "error";
            }
            System.out.println("ERROR: " + ArchiveOps.sanitizeError(msg));
            ArchiveFs.exit(1);
        }
    }

    public static void die(String msg, int code) {
        System.out.println("ERROR: " + msg);
        ArchiveFs.exit(code);
    }

    public static int parseOverwrite(String s) {
        if (s.equals("fail")) {
            return ArchiveOverwrite.FAIL;
        }
        if (s.equals("skip")) {
            return ArchiveOverwrite.SKIP;
        }
        if (s.equals("replace") || s.equals("overwrite")) {
            return ArchiveOverwrite.REPLACE;
        }
        if (s.equals("rename")) {
            return ArchiveOverwrite.RENAME;
        }
        if (s.equals("ask")) {
            return ArchiveOverwrite.ASK;
        }
        die("overwrite must be fail|skip|replace|rename|ask", 2);
        return ArchiveOverwrite.FAIL;
    }

    public static int parseProfile(String s) {
        if (s.equals("fast") || s.equals("1")) {
            return ArchiveCompressProfile.FAST;
        }
        if (s.equals("standard") || s.equals("normal") || s.equals("6")) {
            return ArchiveCompressProfile.STANDARD;
        }
        if (s.equals("max") || s.equals("best") || s.equals("9")) {
            return ArchiveCompressProfile.MAX;
        }
        die("profile must be fast|standard|max", 2);
        return ArchiveCompressProfile.STANDARD;
    }

    public static String[] shift(String[] args, int from) {
        if (from >= args.length) {
            return new String[0];
        }
        String[] out = new String[args.length - from];
        int j = 0;
        while (j < out.length) {
            out[j] = args[from + j];
            j = j + 1;
        }
        return out;
    }

    public static void usage() {
        System.out.println("JNative Archive");
        System.out.println("  [--password-fd N] [--encrypt|--ask-password] [--aes 128|192|256]");
        System.out.println("  [--profile fast|standard|max] [--overwrite fail|skip|replace|rename|ask]");
        System.out.println("  [--batch] [--progress]");
        System.out.println("  [--max-entry-bytes N] [--max-total-bytes N] [--max-entries N]");
        System.out.println("  create <out.zip> <file-or-dir...>");
        System.out.println("  list <archive.zip>");
        System.out.println("  test <archive.zip>");
        System.out.println("  extract <archive.zip> <destDir> [entry...]");
        System.out.println("  version");
        System.out.println("Formats: ZIP, WinZip AES-ZIP. RAR create: not available.");
        System.out.println("Password: TTY (create confirms twice) or --password-fd (never argv).");
        System.out.println("Symlinks: rejected by default (not followed, not silently skipped).");
    }

    public static String resolvePassword(int forEncrypt) {
        if (optPasswordFd >= 0) {
            return ArchivePassword.readFromFd(optPasswordFd);
        }
        if (optAskPassword != 0 || forEncrypt != 0) {
            if (forEncrypt != 0) {
                return ArchivePassword.readInteractiveConfirmed();
            }
            return ArchivePassword.readInteractive("Password: ");
        }
        return null;
    }

    public static void cmdCreate(String[] args) {
        if (args.length < 2) {
            throw new ZipException("create requires out.zip and at least one file or directory");
        }
        ArchiveCreateOptions opt = new ArchiveCreateOptions();
        opt.profile = optProfile;
        opt.encrypt = optEncrypt;
        opt.aesStrength = mapAes(optAesStrength);
        opt.reportProgress = optProgress;
        opt.debugCancelAfterBytes = optDebugCancelAfter;
        if (optEncrypt != 0 || optPasswordFd >= 0) {
            opt.encrypt = 1;
            if (optPasswordFd >= 0) {
                opt.password = resolvePassword(1);
                opt.confirmPasswordInteractive = 0;
            } else {
                opt.confirmPasswordInteractive = 1;
            }
        }
        String out = args[0];
        String[] sources = shift(args, 1);
        ArchiveTask task = new ArchiveTask();
        ArchiveResult r = ArchiveOps.create(out, sources, opt, task);
        if (r.ok() == 0) {
            System.out.println("ERROR: " + r.errorMessage);
            if (r.relatedFile != null && r.relatedFile.length() > 0) {
                System.out.println("FILE: " + r.relatedFile);
            }
            if (r.state == ArchiveTaskState.CANCELLED) {
                ArchiveFs.exit(1);
            }
            ArchiveFs.exit(1);
            return;
        }
        System.out.println("OK created " + r.outputPath);
        System.out.println("NOTES: " + r.notes);
    }

    public static void cmdList(String[] args) {
        if (args.length < 1) {
            throw new ZipException("list requires archive.zip");
        }
        String password = null;
        if (optPasswordFd >= 0 || optAskPassword != 0) {
            password = resolvePassword(0);
        }
        ArrayList<ZipEntry> es = ArchiveOps.listEntries(args[0], password);
        int i = 0;
        while (i < es.size()) {
            ZipEntry e = es.get(i);
            String flag = "FILE";
            if (e.isDirectory()) {
                flag = "DIR";
            } else if (e.isEncrypted()) {
                flag = "ENC";
            }
            String aes = "";
            if (e.aesStrength == 1) {
                aes = "AES-128";
            } else if (e.aesStrength == 2) {
                aes = "AES-192";
            } else if (e.aesStrength == 3) {
                aes = "AES-256";
            }
            System.out.println(flag + "\t" + ("" + e.getSize()) + "\t"
                    + String.valueOf(e.getMethod()) + "\t" + aes + "\t" + e.getName());
            i = i + 1;
        }
    }

    public static void cmdTest(String[] args) {
        if (args.length < 1) {
            throw new ZipException("test requires archive.zip");
        }
        String password = null;
        if (optPasswordFd >= 0 || optAskPassword != 0) {
            password = resolvePassword(0);
        } else {
            /* Probe encryption without password → NEED_PASSWORD */
            ArrayList<ZipEntry> es = ArchiveOps.listEntries(args[0], null);
            int i = 0;
            while (i < es.size()) {
                if (es.get(i).isEncrypted()) {
                    throw new ZipException(ZipNative.errMsg(-12)
                            + " (use --ask-password or --password-fd)");
                }
                i = i + 1;
            }
        }
        ArchiveTask task = new ArchiveTask();
        ArchiveResult r = ArchiveOps.test(args[0], password, task);
        if (r.ok() == 0) {
            System.out.println("ERROR: " + r.errorMessage);
            ArchiveFs.exit(1);
            return;
        }
        System.out.println("OK test passed");
    }

    public static void cmdExtract(String[] args) {
        if (args.length < 2) {
            throw new ZipException("extract requires archive.zip and destDir");
        }
        if (optOverwrite == ArchiveOverwrite.ASK && optBatch != 0) {
            throw new ZipException("overwrite ask is incompatible with --batch");
        }
        ArchiveExtractOptions opt = new ArchiveExtractOptions();
        opt.conflictPolicy = optOverwrite;
        opt.batch = optBatch;
        opt.reportProgress = optProgress;
        opt.limits.maxEntryUncompressed = optMaxEntry;
        opt.limits.maxTotalUncompressed = optMaxTotal;
        opt.limits.maxEntries = optMaxEntries;
        if (optPasswordFd >= 0 || optAskPassword != 0) {
            opt.password = resolvePassword(0);
        } else {
            ArrayList<ZipEntry> es = ArchiveOps.listEntries(args[0], null);
            int i = 0;
            while (i < es.size()) {
                if (es.get(i).isEncrypted()) {
                    throw new ZipException(ZipNative.errMsg(-12)
                            + " (use --ask-password or --password-fd)");
                }
                i = i + 1;
            }
        }
        if (optOverwrite == ArchiveOverwrite.ASK) {
            opt.conflictHandler = new CliConflictHandler();
        }
        if (args.length > 2) {
            opt.selectedEntries = new ArrayList<String>();
            int j = 2;
            while (j < args.length) {
                opt.selectedEntries.add(args[j]);
                j = j + 1;
            }
        }
        ArchiveTask task = new ArchiveTask();
        ArchiveResult r = ArchiveOps.extract(args[0], args[1], opt, task);
        if (r.ok() == 0) {
            System.out.println("ERROR: " + r.errorMessage);
            if (r.relatedFile != null && r.relatedFile.length() > 0) {
                System.out.println("FILE: " + r.relatedFile);
            }
            ArchiveFs.exit(1);
            return;
        }
        System.out.println("OK extracted to " + args[1]);
    }

    public static int mapAes(int bits) {
        if (bits == 128) {
            return 1;
        }
        if (bits == 192) {
            return 2;
        }
        if (bits == 256 || bits == 3) {
            return 3;
        }
        if (bits == 1 || bits == 2) {
            return bits;
        }
        return 3;
    }
}
