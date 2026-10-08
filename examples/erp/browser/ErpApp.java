import jnative.webui.*;
import jnative.browser.*;

/**
 * Browser WASM entry for ERP.
 *
 * UI Foundation 1.0 NOTE:
 * The product shell users open in the browser is {@code examples/erp/public/}
 * (AppShell + PageRegistry + MockDataProvider).
 *
 * JNative wasm32-browser backend currently lowers only a specialized WebUI
 * Counter WAT or a hello-dom stub ({@code BrowserWatEmitter}) — it does NOT
 * execute this ErpApp view tree as a full SPA. Status: WASM UI = BLOCKED.
 *
 * This class remains a minimal WebUI mount probe for toolchain experiments.
 */
public class ErpApp {
    public static void main(String[] args) {
        ErpUiStore.init();
        WebUI.mount("#wasm-root", new ErpWasmProbe());
        Browser.console().log("JNative ERP WASM probe mounted (full UI is public/ SPA; WASM SPA BLOCKED)");
    }
}

class ErpUiStore {
    static StringState title;
    static StringState note;

    static void init() {
        title = WebUI.stateString("JNative One");
        note = WebUI.stateString("WASM SPA BLOCKED — open / for UI Foundation shell (DEMO DATA)");
    }
}

class ErpWasmProbe extends WebUiComponent {
    public View render() {
        return Html.div2(
                Html.h1Dynamic(new ErpTitleProd()),
                Html.h1Dynamic(new ErpNoteProd()));
    }
}

class ErpTitleProd implements StringProducer {
    public String produce() {
        return ErpUiStore.title.get();
    }
}

class ErpNoteProd implements StringProducer {
    public String produce() {
        return ErpUiStore.note.get();
    }
}
