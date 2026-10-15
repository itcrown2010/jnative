package org.jnative.archive;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.jnative.archive.internal.ZipNative;

/**
 * JNative API regression probe — exercises ZipLib wrappers (not native-only).
 * Build: merged with ZipLib + ArchiveTask via scripts/build-api-probe.sh
 *
 * @author 张晓达
 */
class ProbeFs {
    @Native(symbol = "rename")
    static native int rename(String from, String to);

    @Native(symbol = "exit")
    static native void exit(int code);

    @Native(symbol = "mkdir")
    static native int mkdir(String path, int mode);
}

public class ArchiveApiProbe {
    public static void main(String[] args) {
        String base = "examples/jnative-archive/build/api-probe";
        ProbeFs.mkdir(base, 493);
        ProbeFs.exit(run(base));
    }

    static int run(String base) {
        writeAscii(base + "/a.txt", "AAA");
        writeAscii(base + "/b.txt", "BBB");
        if (probeFinish(base) != 0) {
            return 1;
        }
        if (probeSingleStream(base) != 0) {
            return 1;
        }
        if (probeCrcCloseFail(base) != 0) {
            return 1;
        }
        System.out.println("API_PROBE_PASS");
        return 0;
    }

    static void writeAscii(String path, String s) {
        byte[] b = new byte[s.length()];
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            b[i] = c;
            i = i + 1;
        }
        File.writeBytes(path, b);
    }

    static int probeFinish(String base) {
        String zip = base + "/finish-api.zip";
        NativePtr tp = ZipNative.makeUniqueTempPath(zip);
        if (tp == NativePtr.NULL) {
            System.out.println("FAIL finish temp");
            return 1;
        }
        String tmp = NativeMemory.fromCString(tp);
        ZipNative.freePtr(tp);
        ZipOutputStream zos = new ZipOutputStream(tmp);
        ZipEntry e = new ZipEntry("hello.txt");
        zos.putNextEntry(e);
        writeToStream(zos, "hello");
        zos.closeEntry();
        zos.finish();
        ZipFile zf = new ZipFile(tmp);
        ZipExtensions.test(zf);
        zf.close();
        zos.close();
        if (ProbeFs.rename(tmp, zip) != 0) {
            System.out.println("FAIL rename");
            return 1;
        }
        System.out.println("finish-api OK");
        return 0;
    }

    static void writeToStream(ZipOutputStream zos, String s) {
        byte[] b = new byte[s.length()];
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            b[i] = c;
            i = i + 1;
        }
        zos.write(b, 0, b.length);
    }

    static int probeSingleStream(String base) {
        String zip = base + "/two.zip";
        ZipOutputStream zos = new ZipOutputStream(zip);
        ZipEntry ea = new ZipEntry("a.txt");
        zos.putNextEntry(ea);
        writeToStream(zos, "AAA");
        zos.closeEntry();
        ZipEntry eb = new ZipEntry("b.txt");
        zos.putNextEntry(eb);
        writeToStream(zos, "BBB");
        zos.closeEntry();
        zos.finish();
        zos.close();

        ZipFile zf = new ZipFile(zip);
        ZipEntry a = zf.getEntry("a.txt");
        ZipEntry b = zf.getEntry("b.txt");
        InputStream sa = zf.getInputStream(a);
        int rejected = 0;
        try {
            zf.getInputStream(b);
            rejected = 0;
        } catch (Exception ex) {
            rejected = 1;
        }
        if (rejected == 0) {
            System.out.println("FAIL expected second stream rejection");
            drainClose(sa);
            zf.close();
            return 1;
        }
        /* first stream still valid after rejected second open — drain fully before close */
        int c0 = sa.read();
        int c1 = sa.read();
        int c2 = sa.read();
        int eof = sa.read();
        sa.close();
        zf.close();
        if (c0 != 65 || c1 != 65 || c2 != 65 || eof != -1) {
            System.out.println("FAIL first stream bytes");
            return 1;
        }
        System.out.println("single-stream-limit OK");
        return 0;
    }

    /** Partial read then close must surface CRC/integrity failure via ZipFileEntryStream.close(). */
    static int probeCrcCloseFail(String base) {
        String zip = base + "/crc.zip";
        ZipOutputStream zos = new ZipOutputStream(zip);
        ZipEntry e = new ZipEntry("full.txt");
        zos.putNextEntry(e);
        writeToStream(zos, "ABCDEFGH");
        zos.closeEntry();
        zos.finish();
        zos.close();

        ZipFile zf = new ZipFile(zip);
        ZipEntry ent = zf.getEntry("full.txt");
        InputStream in = zf.getInputStream(ent);
        int one = in.read();
        if (one != 65) {
            System.out.println("FAIL crc probe first byte");
            zf.close();
            return 1;
        }
        int failed = 0;
        try {
            in.close();
            failed = 0;
        } catch (Exception ex) {
            failed = 1;
            String m = ex.getMessage();
            if (m == null || m.indexOf("CRC") < 0) {
                System.out.println("FAIL close expected CRC message got: " + m);
                zf.close();
                return 1;
            }
        }
        zf.close();
        if (failed == 0) {
            System.out.println("FAIL expected CRC on partial close");
            return 1;
        }
        System.out.println("crc-close-fail OK");
        return 0;
    }

    static void drainClose(InputStream in) {
        int n = 0;
        while (n >= 0) {
            n = in.read();
        }
        in.close();
    }
}
