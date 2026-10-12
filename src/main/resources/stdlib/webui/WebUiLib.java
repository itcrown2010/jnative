/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative WebUI (0.27) — reactive components, view tree, batched DOM patches.
// Namespace: jnative.webui — NOT compiler intrinsics.

import jnative.browser.*;

/**
 * @author 张晓达
 */
interface ReactiveSubscription {
    void notifyChange();
    void dispose();
}

interface ReactiveSource {
    void subscribe(ReactiveSubscription sub);
    void unsubscribe(ReactiveSubscription sub);
}

interface StringProducer {
    String produce();
}

interface VoidCallback {
    void run();
}

interface MouseCallback {
    void onClick(BrowserEvent event);
}

class ReactiveTracker {
    static ReactiveComputation current;

    static void track(ReactiveSource source) {
        if (current != null && source != null) {
            current.addDependency(source);
        }
    }
}

abstract class ReactiveComputation implements ReactiveSubscription {
    ArrayList<ReactiveSource> deps;
    boolean dirty;

    ReactiveComputation() {
        this.deps = new ArrayList<ReactiveSource>();
        this.dirty = true;
    }

    void addDependency(ReactiveSource source) {
        if (source == null) {
            return;
        }
        if (!deps.contains(source)) {
            deps.add(source);
            source.subscribe(this);
        }
    }

    void clearDependencies() {
        for (int i = 0; i < deps.size(); i = i + 1) {
            deps.get(i).unsubscribe(this);
        }
        deps.clear();
    }

    abstract void run();

    void notifyChange() {
        dirty = true;
        onInvalidate();
    }

    void onInvalidate() {
    }

    void dispose() {
        clearDependencies();
    }
}

class IntState implements ReactiveSource {
    int value;
    ArrayList<ReactiveSubscription> subs;

    IntState(int initial) {
        this.value = initial;
        this.subs = new ArrayList<ReactiveSubscription>();
    }

    int get() {
        ReactiveTracker.track(this);
        return value;
    }

    void set(int v) {
        if (v == value) {
            return;
        }
        value = v;
        notifySubs();
    }

    void subscribe(ReactiveSubscription sub) {
        if (sub != null && !subs.contains(sub)) {
            subs.add(sub);
        }
    }

    void unsubscribe(ReactiveSubscription sub) {
        removeSub(sub);
    }

    void removeSub(ReactiveSubscription sub) {
        for (int i = 0; i < subs.size(); i = i + 1) {
            if (subs.get(i) == sub) {
                subs.removeAt(i);
                return;
            }
        }
    }

    void notifySubs() {
        RenderScheduler.markDirty();
        for (int i = 0; i < subs.size(); i = i + 1) {
            subs.get(i).notifyChange();
        }
    }
}

class StringState implements ReactiveSource {
    String value;
    ArrayList<ReactiveSubscription> subs;

    StringState(String initial) {
        if (initial == null) {
            initial = "";
        }
        this.value = initial;
        this.subs = new ArrayList<ReactiveSubscription>();
    }

    String get() {
        ReactiveTracker.track(this);
        return value;
    }

    void set(String v) {
        if (v == null) {
            v = "";
        }
        if (v == value) {
            return;
        }
        value = v;
        notifySubs();
    }

    void subscribe(ReactiveSubscription sub) {
        if (sub != null && !subs.contains(sub)) {
            subs.add(sub);
        }
    }

    void unsubscribe(ReactiveSubscription sub) {
        for (int i = 0; i < subs.size(); i = i + 1) {
            if (subs.get(i) == sub) {
                subs.removeAt(i);
                return;
            }
        }
    }

    void notifySubs() {
        RenderScheduler.markDirty();
        for (int i = 0; i < subs.size(); i = i + 1) {
            subs.get(i).notifyChange();
        }
    }
}

class RenderScheduler {
    static boolean dirty;
    static boolean flushScheduled;
    static ArrayList<DomBinding> pendingBindings;
    static int flushCount;
    static int bindingUpdateCount;

    static void ensureInit() {
        if (pendingBindings == null) {
            pendingBindings = new ArrayList<DomBinding>();
        }
    }

    static void markDirty() {
        ensureInit();
        dirty = true;
        scheduleFlush();
    }

    static void scheduleFlush() {
        if (flushScheduled) {
            return;
        }
        flushScheduled = true;
        WebUiNative.scheduleMicrotask(new FlushTask());
    }

    static void enqueueBinding(DomBinding binding) {
        ensureInit();
        if (!pendingBindings.contains(binding)) {
            pendingBindings.add(binding);
        }
        scheduleFlush();
    }

    static void flush() {
        ensureInit();
        flushScheduled = false;
        flushCount = flushCount + 1;
        for (int i = 0; i < pendingBindings.size(); i = i + 1) {
            pendingBindings.get(i).apply();
            bindingUpdateCount = bindingUpdateCount + 1;
        }
        pendingBindings.clear();
        DomPatchBuffer.flush();
        dirty = false;
    }

    static void batch(VoidCallback fn) {
        boolean prev = flushScheduled;
        flushScheduled = true;
        fn.run();
        flushScheduled = prev;
        scheduleFlush();
    }
}

class FlushTask implements VoidCallback {
    public void run() {
        RenderScheduler.flush();
    }
}

class DomPatchOps {
    static int SET_TEXT = 1;
    static int SET_ATTR = 2;
    static int SET_PROP = 4;
    static int APPEND = 5;
}

class DomPatchBuffer {
    static int bufPtr;
    static int cursor;

    static void reset() {
        if (bufPtr == 0) {
            bufPtr = WebUiNative.allocPatchBuffer(4096);
        }
        cursor = 0;
    }

    static void writeInt(int v) {
        WebUiNative.patchWriteInt(bufPtr, cursor, v);
        cursor = cursor + 4;
    }

    static void writeString(String s) {
        cursor = WebUiNative.patchWriteString(bufPtr, cursor, s);
    }

    static void setText(int nodeId, String text) {
        writeInt(DomPatchOps.SET_TEXT);
        writeInt(nodeId);
        writeString(text);
    }

    static void setAttr(int nodeId, String name, String value) {
        writeInt(DomPatchOps.SET_ATTR);
        writeInt(nodeId);
        writeString(name);
        writeString(value);
    }

    static void appendChild(int parentId, int childId) {
        writeInt(DomPatchOps.APPEND);
        writeInt(parentId);
        writeInt(childId);
    }

    static void flush() {
        if (cursor > 0) {
            WebUiNative.applyDomPatches(bufPtr, cursor);
            cursor = 0;
        }
    }
}

class View {
    int kind;
    ArrayList<View> children;

    View() {
        this.children = new ArrayList<View>();
    }

    void addChild(View child) {
        if (child != null) {
            children.add(child);
        }
    }
}

class ElementView extends View {
    String tag;
    ArrayList<ViewProp> props;
    ArrayList<ViewEvent> events;

    ElementView(String tag) {
        this.kind = 1;
        this.tag = tag;
        this.props = new ArrayList<ViewProp>();
        this.events = new ArrayList<ViewEvent>();
    }
}

class TextView extends View {
    String text;
    StringProducer dynamic;

    TextView(String text) {
        this.kind = 2;
        this.text = text;
    }

    TextView(StringProducer dynamic, int marker) {
        this.kind = 2;
        this.dynamic = dynamic;
    }
}

class ComponentView extends View {
    WebUiComponent component;

    ComponentView(WebUiComponent component) {
        this.kind = 4;
        this.component = component;
    }
}

class ViewProp {
    String name;
    String value;
    StringProducer dynamic;
    boolean isProperty;
}

class ViewEvent {
    String type;
    MouseCallback handler;
}

class DomBinding extends ReactiveComputation {
    StringProducer producer;
    int domNodeId;

    DomBinding(StringProducer producer, int domNodeId) {
        this.producer = producer;
        this.domNodeId = domNodeId;
    }

    void run() {
        apply();
    }

    void apply() {
        clearDependencies();
        ReactiveComputation prev = ReactiveTracker.current;
        ReactiveTracker.current = this;
        String text = producer.produce();
        ReactiveTracker.current = prev;
        DomPatchBuffer.setText(domNodeId, text);
    }

    void onInvalidate() {
        RenderScheduler.enqueueBinding(this);
    }
}

abstract class WebUiComponent {
    ComponentInstance instance;

    abstract View render();

    protected void onMount() {
    }

    protected void onUnmount() {
    }
}

class ComponentInstance {
    WebUiComponent component;
    int rootDomId;
    ArrayList<DomBinding> bindings;
    ArrayList<ComponentInstance> children;

    ComponentInstance(WebUiComponent component) {
        this.component = component;
        this.bindings = new ArrayList<DomBinding>();
        this.children = new ArrayList<ComponentInstance>();
        component.instance = this;
    }

    void mount(int parentDomId) {
        View tree = component.render();
        BrowserDomRenderer renderer = new BrowserDomRenderer(this);
        rootDomId = renderer.mount(tree, parentDomId);
        component.onMount();
    }

    void unmount() {
        component.onUnmount();
        for (int i = 0; i < bindings.size(); i = i + 1) {
            bindings.get(i).dispose();
        }
        bindings.clear();
        for (int i = 0; i < children.size(); i = i + 1) {
            children.get(i).unmount();
        }
        children.clear();
    }
}

class BrowserDomRenderer {
    ComponentInstance owner;

    BrowserDomRenderer(ComponentInstance owner) {
        this.owner = owner;
    }

    int mount(View view, int parentDomId) {
        DomPatchBuffer.reset();
        int id = renderView(view, parentDomId);
        DomPatchBuffer.flush();
        return id;
    }

    int renderView(View view, int parentDomId) {
        if (view == null) {
            return 0;
        }
        if (view.kind == 1) {
            return renderElement((ElementView) view, parentDomId);
        }
        if (view.kind == 2) {
            return renderText((TextView) view, parentDomId);
        }
        if (view.kind == 4) {
            return renderComponent((ComponentView) view, parentDomId);
        }
        return 0;
    }

    int renderElement(ElementView el, int parentDomId) {
        int nodeId = WebUiNative.createElement(el.tag);
        for (int i = 0; i < el.events.size(); i = i + 1) {
            ViewEvent ev = el.events.get(i);
            int cbId = WebUiNative.registerCallback(ev.handler);
            WebUiNative.delegateEvent(nodeId, ev.type, cbId);
        }
        if (parentDomId != 0) {
            DomPatchBuffer.appendChild(parentDomId, nodeId);
        }
        for (int i = 0; i < el.children.size(); i = i + 1) {
            renderView(el.children.get(i), nodeId);
        }
        return nodeId;
    }

    int renderText(TextView tv, int parentDomId) {
        int nodeId;
        if (tv.dynamic != null) {
            nodeId = WebUiNative.createTextNode("");
            DomBinding b = new DomBinding(tv.dynamic, nodeId);
            owner.bindings.add(b);
            b.apply();
        } else {
            String safe = tv.text;
            if (safe == null) {
                safe = "";
            }
            nodeId = WebUiNative.createTextNode(safe);
        }
        if (parentDomId != 0) {
            DomPatchBuffer.appendChild(parentDomId, nodeId);
        }
        return nodeId;
    }

    int renderComponent(ComponentView cv, int parentDomId) {
        ComponentInstance child = new ComponentInstance(cv.component);
        owner.children.add(child);
        child.mount(parentDomId);
        return child.rootDomId;
    }
}

class Html {
    static ElementView div2(View a, View b) {
        ElementView el = new ElementView("div");
        el.addChild(a);
        el.addChild(b);
        return el;
    }

    static ElementView h1Text(String text) {
        ElementView el = new ElementView("h1");
        el.addChild(new TextView(text));
        return el;
    }

    static ElementView h1Dynamic(StringProducer dynamic) {
        ElementView el = new ElementView("h1");
        el.addChild(new TextView(dynamic, 1));
        return el;
    }

    static ElementView buttonLabel(String label, ViewEvent ev) {
        ElementView el = new ElementView("button");
        el.addChild(new TextView(label));
        el.events.add(ev);
        return el;
    }

    static ViewEvent onClickVoid(VoidCallback handler) {
        ViewEvent ev = new ViewEvent();
        ev.type = "click";
        ev.handler = new ClickVoidAdapter(handler);
        return ev;
    }
}

class ClickVoidAdapter implements MouseCallback {
    VoidCallback inner;

    ClickVoidAdapter(VoidCallback inner) {
        this.inner = inner;
    }

    public void onClick(BrowserEvent event) {
        inner.run();
    }
}

class WebUI {
    static ComponentInstance root;
    static int mountCount;

    static IntState stateInt(int initial) {
        return new IntState(initial);
    }

    static StringState stateString(String initial) {
        return new StringState(initial);
    }

    static void mount(String selector, WebUiComponent component) {
        mountCount = mountCount + 1;
        int host = WebUiNative.querySelector(selector);
        if (host == 0) {
            Browser.console().error("WebUI mount target not found");
            return;
        }
        WebUiNative.initEventDelegation(host);
        root = new ComponentInstance(component);
        root.mount(host);
        RenderScheduler.scheduleFlush();
    }

    static void batch(VoidCallback fn) {
        RenderScheduler.batch(fn);
    }
}

class WebUiNative {
    static int allocPatchBuffer(int size) {
        return 1;
    }

    static void patchWriteInt(int buf, int off, int v) {
    }

    static int patchWriteString(int buf, int off, String s) {
        return off + 4;
    }

    static void applyDomPatches(int buf, int len) {
    }

    static void scheduleMicrotask(VoidCallback task) {
        task.run();
    }

    static int querySelector(String selector) {
        if (selector.length() > 0 && selector.charAt(0) == 35) {
            return BrowserNative.documentGetElementById(selector.substring(1, selector.length()));
        }
        return 0;
    }

    static int createElement(String tag) {
        return BrowserNative.documentCreateElement(tag);
    }

    static int createTextNode(String text) {
        return BrowserNative.documentCreateElement("span");
    }

    static int registerCallback(MouseCallback handler) {
        return 1;
    }

    static void delegateEvent(int nodeId, String type, int callbackId) {
        BrowserNative.elementAddEventListener(nodeId, type, new WebUiEventBridge(callbackId));
    }

    static void initEventDelegation(int rootId) {
    }

    static void dispatchCallback(int callbackId, BrowserEvent event) {
    }
}

class WebUiEventBridge implements BrowserEventHandler {
    int callbackId;

    WebUiEventBridge(int callbackId) {
        this.callbackId = callbackId;
    }

    public void handle(BrowserEvent event) {
        WebUiNative.dispatchCallback(callbackId, event);
    }
}
