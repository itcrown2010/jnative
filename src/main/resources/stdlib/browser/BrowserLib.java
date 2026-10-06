// JNative Browser (0.26) — DOM / console / storage via JsHandle.
// Bodies are stubs for semantic analysis; wasm32-browser backend lowers real calls to JS imports.
// DOM/fetch are NOT compiler intrinsics.

class JsException extends RuntimeException {
    String name;
    String jsMessage;

    JsException(String name, String message) {
        super(message);
        this.name = name;
        this.jsMessage = message;
    }
}

class BrowserException extends RuntimeException {
    BrowserException(String message) {
        super(message);
    }
}

class JsHandle {
    int id;

    JsHandle(int id) {
        this.id = id;
    }

    int id() {
        return id;
    }
}

class JsObject {
    JsHandle handle;

    JsObject(JsHandle handle) {
        this.handle = handle;
    }

    int handleId() {
        if (handle == null) {
            return 0;
        }
        return handle.id;
    }
}

class Console extends JsObject {
    Console(JsHandle handle) {
        super(handle);
    }

    void log(String message) {
        BrowserNative.consoleLog(message);
    }

    void warn(String message) {
        BrowserNative.consoleWarn(message);
    }

    void error(String message) {
        BrowserNative.consoleError(message);
    }
}

class Document extends JsObject {
    Document(JsHandle handle) {
        super(handle);
    }

    Element getElementById(String id) {
        int h = BrowserNative.documentGetElementById(id);
        if (h == 0) {
            return null;
        }
        return new Element(new JsHandle(h));
    }

    Element createElement(String tag) {
        int h = BrowserNative.documentCreateElement(tag);
        return new Element(new JsHandle(h));
    }

    Element body() {
        return new Element(new JsHandle(BrowserNative.documentBody()));
    }
}

class Element extends JsObject {
    Element(JsHandle handle) {
        super(handle);
    }

    void setText(String text) {
        BrowserNative.elementSetText(handleId(), text);
    }

    String getText() {
        return BrowserNative.elementGetText(handleId());
    }

    void setAttribute(String name, String value) {
        BrowserNative.elementSetAttribute(handleId(), name, value);
    }

    void appendChild(Element child) {
        if (child != null) {
            BrowserNative.elementAppendChild(handleId(), child.handleId());
        }
    }

    void addEventListener(String type, BrowserEventHandler handler) {
        BrowserNative.elementAddEventListener(handleId(), type, handler);
    }

    HtmlInputElement asInput() {
        return new HtmlInputElement(handle);
    }

    HtmlButtonElement asButton() {
        return new HtmlButtonElement(handle);
    }
}

class HtmlInputElement extends Element {
    HtmlInputElement(JsHandle handle) {
        super(handle);
    }

    String value() {
        return BrowserNative.inputValue(handleId());
    }

    void setValue(String value) {
        BrowserNative.inputSetValue(handleId(), value);
    }
}

class HtmlButtonElement extends Element {
    HtmlButtonElement(JsHandle handle) {
        super(handle);
    }
}

interface BrowserEventHandler {
    void handle(BrowserEvent event);
}

class BrowserEvent extends JsObject {
    BrowserEvent(JsHandle handle) {
        super(handle);
    }

    String type() {
        return BrowserNative.eventType(handleId());
    }
}

class Storage extends JsObject {
    Storage(JsHandle handle) {
        super(handle);
    }

    void set(String key, String value) {
        BrowserNative.storageSet(handleId(), key, value);
    }

    String get(String key) {
        return BrowserNative.storageGet(handleId(), key);
    }
}

class Window extends JsObject {
    Window(JsHandle handle) {
        super(handle);
    }

    Document document() {
        return Browser.document();
    }

    Storage localStorage() {
        return Browser.localStorage();
    }
}

class Browser {
    static Console console() {
        return new Console(new JsHandle(1));
    }

    static Document document() {
        return new Document(new JsHandle(2));
    }

    static Window window() {
        return new Window(new JsHandle(3));
    }

    static Storage localStorage() {
        return new Storage(new JsHandle(4));
    }
}

/** Host stubs — wasm backend replaces with JS imports. */
class BrowserNative {
    static void consoleLog(String message) {
    }

    static void consoleWarn(String message) {
    }

    static void consoleError(String message) {
    }

    static int documentGetElementById(String id) {
        return 10;
    }

    static int documentCreateElement(String tag) {
        return 11;
    }

    static int documentBody() {
        return 12;
    }

    static void elementSetText(int handle, String text) {
    }

    static String elementGetText(int handle) {
        return "";
    }

    static void elementSetAttribute(int handle, String name, String value) {
    }

    static void elementAppendChild(int parent, int child) {
    }

    static void elementAddEventListener(int handle, String type, BrowserEventHandler handler) {
    }

    static String inputValue(int handle) {
        return "";
    }

    static void inputSetValue(int handle, String value) {
    }

    static String eventType(int handle) {
        return "";
    }

    static void storageSet(int handle, String key, String value) {
    }

    static String storageGet(int handle, String key) {
        return "";
    }
}
