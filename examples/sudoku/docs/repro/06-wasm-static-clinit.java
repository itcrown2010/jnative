/**
 * Minimal repro: wasm32-browser never ran jnative_clinit, so
 * `public static final int C = 1` stayed 0 and comparisons like
 * `status == ST_PAUSED` were true whenever status was PLAYING (0).
 *
 * Expected after fix: jnative_start calls jnative_clinit; reading C yields 1.
 * @author 张晓达
 */
public class WasmStaticClinitRepro {
    public static final int C = 1;
    public static int mutableInit = 7;

    public static int getC() {
        return C;
    }

    public static int getMutable() {
        return mutableInit;
    }

    public static void main(String[] args) {
        System.out.println(getC());
        System.out.println(getMutable());
    }
}
