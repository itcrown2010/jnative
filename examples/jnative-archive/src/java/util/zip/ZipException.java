package java.util.zip;

/** Thrown when a ZIP format or engine error occurs. @author 张晓达 */
public class ZipException extends IOException {
    public ZipException(String msg) {
        super(msg);
    }
}
