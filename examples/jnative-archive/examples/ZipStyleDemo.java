package org.jnative.archive.examples;

import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Minimal Java-style ZIP demo compiled by JNative to a native binary.
 *
 * @author 张晓达
 */
public class ZipStyleDemo {
    public static void main(String[] args) {
        String zipPath = "examples/jnative-archive/build/zip-style-demo.zip";
        String entryName = "hello.txt";
        String payload = "hello from java.util.zip";

        ZipOutputStream zos = new ZipOutputStream(zipPath);
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        writeAscii(zos, payload);
        zos.closeEntry();
        zos.finish();
        zos.close();
        System.out.println("CREATED " + zipPath);

        ZipFile zf = new ZipFile(zipPath);
        ZipEntry found = zf.getEntry(entryName);
        if (found == null) {
            System.out.println("FAIL missing entry");
            zf.close();
            return;
        }
        InputStream in = zf.getInputStream(found);
        byte[] buf = new byte[256];
        int n = in.read(buf, 0, buf.length);
        in.close();
        zf.close();
        if (n != payload.length()) {
            System.out.println("FAIL length " + n);
            return;
        }
        int i = 0;
        while (i < n) {
            int c = buf[i];
            if (c < 0) {
                c = c + 256;
            }
            if (c != payload.charAt(i)) {
                System.out.println("FAIL content at " + i);
                return;
            }
            i = i + 1;
        }
        System.out.println("READ_OK " + found.getName() + " size=" + found.getSize());
        System.out.println("ZIP_STYLE_DEMO_PASS");
    }

    static void writeAscii(ZipOutputStream zos, String s) {
        byte[] b = new byte[s.length()];
        int i = 0;
        while (i < s.length()) {
            int c = s.charAt(i);
            b[i] = c;
            i = i + 1;
        }
        zos.write(b, 0, b.length);
    }
}
