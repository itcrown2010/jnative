package org.jnative.archive;

import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.jnative.archive.internal.ZipNative;

/**
 * JNative ZIP extensions (AES password, cancel, integrity test).
 * Not part of java.util.zip — keep password off the JDK-compat surface.
 * Method names are distinct (no overloads) — JNative IR currently mishandles
 * same-name static overloads across parameter types.
 *
 * @author 张晓达
 */
public class ZipExtensions {
    private ZipExtensions() {
    }

    /**
     * Optional AES password for subsequent entries. null clears encryption.
     * Empty string is rejected — never silently create an unencrypted archive.
     */
    public static void setOutputPassword(ZipOutputStream zos, String password) {
        if (password != null && password.length() == 0) {
            throw new ZipException("empty password rejected");
        }
        ZipNative.check(ZipNative.writerSetPassword(zos.nativeHandle(), password), "setPassword");
    }

    /** WinZip AES strength: 1=128, 2=192, 3=256 (default). */
    public static void setAesStrength(ZipOutputStream zos, int strength) {
        ZipNative.check(ZipNative.writerSetAesStrength(zos.nativeHandle(), strength), "setAesStrength");
    }

    public static void setFilePassword(ZipFile zf, String password) {
        if (password != null && password.length() == 0) {
            throw new ZipException("empty password rejected");
        }
        ZipNative.check(ZipNative.readerSetPassword(zf.nativeHandle(), password), "setPassword");
    }

    public static void setInputPassword(ZipInputStream zis, String password) {
        if (password != null && password.length() == 0) {
            throw new ZipException("empty password rejected");
        }
        ZipNative.check(ZipNative.readerSetPassword(zis.nativeHandle(), password), "setPassword");
    }

    public static void test(ZipFile zf) {
        ZipNative.check(ZipNative.readerTestAll(zf.nativeHandle()), "test");
    }

    public static void requestFileCancel(ZipFile zf) {
        ZipNative.readerRequestCancel(zf.nativeHandle());
    }

    public static void requestOutputCancel(ZipOutputStream zos) {
        ZipNative.writerRequestCancel(zos.nativeHandle());
    }
}
