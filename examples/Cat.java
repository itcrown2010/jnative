/**
 * @author 张晓达
 */
public class Cat {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("usage: jcat <file>");
            return;
        }
        String text = File.readString(args[0]);
        System.out.println(text);
    }
}
