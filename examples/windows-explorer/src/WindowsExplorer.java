/**
 * JNative Windows Explorer - a Windows 11 style File Explorer.
 * The UI is drawn by Java onto a double-buffered canvas (GDI) through UiLib and compiled ahead of time
 * by JNative into a native EXE (no Swing / AWT / JRE). Shell integration (icons, recycle bin, clipboard,
 * context menu, properties) goes through the small native library jn_fm.
 *
 * @author 张晓达
 */
public class WindowsExplorer {
    public static void main(String[] args) {
        String start = "";
        if (args != null && args.length > 0) {
            start = args[0];
        }
        FmApp.start(start);
    }
}
