import jnative.webui.*;

/**
 * Browser WASM entry (WebUI). Full calendar UX is also served as public/app.js
 * by the native server for end-to-end REST validation.
 * @author 张晓达
 */
public class SchedulerApp {
    public static void main(String[] args) {
        WebUI.mount("#app", new SchedulerShell());
    }
}

class SchedulerShell extends WebUiComponent {
    StringState title;

    SchedulerShell() {
        title = WebUI.stateString("JNative Scheduler");
    }

    public View render() {
        return Html.div2(
            Html.h1Dynamic(new ShellTitle(title)),
            Html.h1Text("WASM WebUI shell — open / for the full calendar UI.")
        );
    }
}

class ShellTitle implements StringProducer {
    StringState title;

    ShellTitle(StringState title) {
        this.title = title;
    }

    public String produce() {
        return title.get();
    }
}
