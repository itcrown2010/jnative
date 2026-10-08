import jnative.webui.*;
import jnative.browser.*;

/**
 * WASM PlayerStore + BottomPlayer shell.
 * Thin audio/DOM bridge lives in public/audio-bridge.js (charter §24).
 * Route changes must not tear down the player root (#bottom-player stays outside #wasm-root).
 */
public class MusicApp {
    public static void main(String[] args) {
        WebUI.mount("#wasm-root", new MusicShell());
        Browser.console().log("JNative Music WASM PlayerStore ready");
    }
}

class PlayerStore {
    static StringState title;
    static StringState subtitle;
    static IntState playing;
    static IntState repeatMode;
    static IntState queueLen;

    static void init() {
        title = WebUI.stateString("Nothing playing");
        subtitle = WebUI.stateString("JNative Music");
        playing = WebUI.stateInt(0);
        repeatMode = WebUI.stateInt(0);
        queueLen = WebUI.stateInt(0);
    }

    static void setTrack(String t, String sub) {
        title.set(t);
        subtitle.set(sub);
    }

    static void togglePlay() {
        if (playing.get() != 0) {
            playing.set(0);
            bridgeCmd("pause");
        } else {
            playing.set(1);
            bridgeCmd("play");
        }
    }

    static void seek(int seconds) {
        bridgeCmd("seek|" + String.valueOf(seconds));
    }

    static void cycleRepeat() {
        int n = (repeatMode.get() + 1) % 4;
        repeatMode.set(n);
    }

    static void bridgeCmd(String cmd) {
        int el = BrowserNative.documentGetElementById("jn-bridge");
        if (el != 0) {
            BrowserNative.elementSetAttribute(el, "data-cmd", cmd);
        }
    }
}

class MusicShell extends WebUiComponent {
    MusicShell() {
        PlayerStore.init();
    }

    public View render() {
        return Html.div2(
            Html.h1Dynamic(new StoreTitle()),
            Html.buttonLabel("Play/Pause (WASM PlayerStore)", Html.onClickVoid(new PlayClick()))
        );
    }
}

class StoreTitle implements StringProducer {
    public String produce() {
        return "PlayerStore · " + PlayerStore.title.get()
                + " · queue=" + String.valueOf(PlayerStore.queueLen.get())
                + " · repeat=" + String.valueOf(PlayerStore.repeatMode.get());
    }
}

class PlayClick implements VoidCallback {
    public void run() {
        PlayerStore.togglePlay();
    }
}
