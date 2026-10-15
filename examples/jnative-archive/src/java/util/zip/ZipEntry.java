package java.util.zip;

/**
 * ZIP entry metadata. Subset of java.util.zip.ZipEntry.
 * isEncrypted/getAesStrength/isSymlink are JNative listing extensions.
 * @author 张晓达
 */
public class ZipEntry {
    public static int STORED = 0;
    public static int DEFLATED = 8;

    public String name;
    public long size;
    public long compressedSize;
    public long crc;
    public int method;
    public int directory;
    public int encrypted;
    public int aesStrength;
    public int symlink;
    public long time;

    public ZipEntry(String n) {
        name = n;
        size = -1;
        compressedSize = -1;
        crc = -1;
        method = DEFLATED;
        directory = 0;
        encrypted = 0;
        aesStrength = 0;
        symlink = 0;
        time = -1;
    }

    public String getName() {
        return name;
    }

    public long getSize() {
        return size;
    }

    public long getCompressedSize() {
        return compressedSize;
    }

    public long getCrc() {
        return crc;
    }

    public int getMethod() {
        return method;
    }

    public boolean isDirectory() {
        return directory != 0;
    }

    public boolean isEncrypted() {
        return encrypted != 0;
    }

    public boolean needsPassword() {
        return encrypted != 0;
    }

    public int getAesStrength() {
        return aesStrength;
    }

    public boolean isSymlink() {
        return symlink != 0;
    }

    public void setMethod(int m) {
        method = m;
    }

    public void setSize(long s) {
        size = s;
    }
}
