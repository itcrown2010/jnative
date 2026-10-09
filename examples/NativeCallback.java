// Requires libjnative_cbtest (see src/test/resources/native_fixtures/callback_invoke.c).
// Build: clang -shared callback_invoke.c -o libjnative_cbtest.dylib
// Run:   jnative NativeCallback.java -o NativeCallback -L. -ljnative_cbtest

/**
 * @author 张晓达
 */
class CLib {
    @Native(symbol = "invoke_callback", library = "jnative_cbtest")
    static native int invokeCallback(NativePtr cb, int value);
}

public class NativeCallback {
    public static void main(String[] args) {
        NativeCallbackHandle cb = Native.callback(x -> x * 2);
        int result = CLib.invokeCallback(cb.pointer(), 21);
        cb.close();
        System.out.println(result);
    }
}
