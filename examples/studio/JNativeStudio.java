import jnative.ui.*;
import jnative.studio.*;

/**
 * JNative Studio — self-hosted native IDE entry (0.21).
 *
 * <pre>
 * jnative examples/studio/JNativeStudio.java -o JNativeStudio
 * ./JNativeStudio
 * </pre>
 * @author 张晓达
 */
public class JNativeStudio {
    public static void main(String[] args) {
        Application app = new Application();
        StudioApplication studio = new StudioApplication();
        studio.start();
        app.run();
    }
}
