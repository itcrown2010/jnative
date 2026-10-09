/** Minimal repro: char literals like 'J' are not tokenized. Use int 74 instead. */
public class CharLiteralRepro {
    public static void main(String[] args) {
        int c = 'J';
        System.out.println(c);
    }
}
