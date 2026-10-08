/**
 * Native logic smoke for Music (compiled with jnative when tooling wires example tests).
 * Primary regression for Range lives in HttpRangeStreamingEndToEndTest.
 */
public class MusicLogicTest {
    public static void main(String[] args) {
        int ok = 1;
        if (RepeatMode.SEQUENCE != 0) {
            ok = 0;
        }
        if (RepeatMode.SHUFFLE != 3) {
            ok = 0;
        }
        System.out.println(ok);
    }
}
