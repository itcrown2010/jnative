/**
 * @author 张晓达
 */
public class Copy {
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("usage: jcopy <src> <dst>");
            return;
        }
        byte[] data = File.readBytes(args[0]);
        File.writeBytes(args[1], data);
    }
}
