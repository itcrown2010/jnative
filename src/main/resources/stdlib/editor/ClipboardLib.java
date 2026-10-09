// JNative Clipboard (text) — lightweight include without full Editor/Graphics.

/**
 * @author 张晓达
 */
class ClipboardNative {
    @Native(symbol = "jnative_clipboard_set_text")
    static native int setText(String text);

    @Native(symbol = "jnative_clipboard_get_text")
    static native NativePtr getTextPtr();

    @Native(symbol = "jnative_clipboard_free")
    static native void freeText(NativePtr p);
}

class Clipboard {
    static void setText(String text) {
        if (text == null) {
            text = "";
        }
        ClipboardNative.setText(text);
    }

    static String getText() {
        NativePtr p = ClipboardNative.getTextPtr();
        if (p == NativePtr.NULL) {
            return "";
        }
        String s = NativeMemory.fromCString(p);
        ClipboardNative.freeText(p);
        if (s == null) {
            return "";
        }
        return s;
    }
}
