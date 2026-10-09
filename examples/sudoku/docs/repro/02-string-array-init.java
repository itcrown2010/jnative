/** Minimal repro: new String[]{ "a", "b" } array initializer is unsupported.
 * @author 张晓达
 */
public class StringArrayInitRepro {
    static String[] A = new String[] { "abc", "def" };
    public static void main(String[] args) {
        System.out.println(A[0].length());
    }
}
