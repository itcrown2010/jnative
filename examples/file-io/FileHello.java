/**
 * Round-trip a UTF-8 file via builtin File API.
 * @author 张晓达
 */
public class FileHello {
    public static void main(String[] args) {
        File.writeString("jn-file-hello.txt", "Hello file");
        System.out.println(File.readString("jn-file-hello.txt"));
        File.delete("jn-file-hello.txt");
    }
}
