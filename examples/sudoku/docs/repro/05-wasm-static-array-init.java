/**
 * Minimal repro: wasm32-browser clinit of `static int[] a = new int[81]`
 * leaves a broken array; reading a[0] traps with unreachable.
 * Workaround: lazy `a = new int[81]` inside a method after start.
 * @author 张晓达
 */
public class WasmStaticArrayInit {
    static int[] a = new int[81];

    public static int get() {
        return a[0];
    }

    public static void main(String[] args) {
        boolean keep = false;
        if (keep) {
            int x = get();
        }
    }
}
