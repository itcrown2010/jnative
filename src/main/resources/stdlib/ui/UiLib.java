// JNative UI stdlib (0.18) — Advanced controls. Library/runtime, NOT compiler intrinsics.
// Public names match jnative.ui.* (imports are accepted and flattened).
// Depends on jnative.collections (ArrayList) when advanced controls are used.

class UiException extends RuntimeException {
    UiException(String message) {
        super(message);
    }
}

class UiNativeException extends UiException {
    UiNativeException(String message) {
        super(message);
    }
}

class UiThreadViolationException extends UiException {
    UiThreadViolationException(String message) {
        super(message);
    }
}

class DisposedComponentException extends UiException {
    DisposedComponentException(String message) {
        super(message);
    }
}

class Orientation {
    static int HORIZONTAL = 0;
    static int VERTICAL = 1;
}

class KeyCode {
    static int ENTER = 10;
    static int ESCAPE = 27;
    static int SPACE = 32;
    static int LEFT = 37;
    static int UP = 38;
    static int RIGHT = 39;
    static int DOWN = 40;
    static int BACKSPACE = 8;
    static int DELETE = 46;
    static int HOME = 36;
    static int END = 35;
    static int PAGE_UP = 33;
    static int PAGE_DOWN = 34;
    static int TAB = 9;
    static int A = 65;
    static int Z = 90;
    static int DIGIT0 = 48;
    static int DIGIT9 = 57;
    static int F1 = 112;
    static int F12 = 123;
}

class KeyCombination {
    int keyCode;
    int ctrl;
    int shift;
    int alt;
    int meta;
    int shortcut;

    KeyCombination(int key) {
        keyCode = key;
        ctrl = 0;
        shift = 0;
        alt = 0;
        meta = 0;
        shortcut = 0;
    }

    static KeyCombination of(int key) {
        return new KeyCombination(key);
    }

    KeyCombination withCtrl() {
        ctrl = 1;
        shortcut = 1;
        return this;
    }

    KeyCombination withShift() {
        shift = 1;
        return this;
    }

    KeyCombination withAlt() {
        alt = 1;
        return this;
    }

    KeyCombination withMeta() {
        meta = 1;
        return this;
    }

    KeyCombination withShortcut() {
        shortcut = 1;
        ctrl = 1;
        return this;
    }
}

class UiNative {
    @Native(symbol = "jnative_ui_init")
    static native void init(int headless);

    @Native(symbol = "jnative_ui_set_dispatcher")
    static native void setDispatcher(NativePtr fn);

    @Native(symbol = "jnative_ui_run_loop")
    static native void runLoop();

    @Native(symbol = "jnative_ui_quit")
    static native void quitNative();

    @Native(symbol = "jnative_ui_post_task")
    static native void postTask();

    @Native(symbol = "jnative_ui_create_window")
    static native int createWindow(String title, int width, int height);

    @Native(symbol = "jnative_ui_create_button")
    static native int createButton(int parentId, String text);

    @Native(symbol = "jnative_ui_create_label")
    static native int createLabel(int parentId, String text);

    @Native(symbol = "jnative_ui_create_textfield")
    static native int createTextField(int parentId, String text);

    @Native(symbol = "jnative_ui_create_panel")
    static native int createPanel(int parentId);

    @Native(symbol = "jnative_ui_create_control")
    static native int createControl(int kind, int parentId, String text);

    @Native(symbol = "jnative_ui_destroy")
    static native void destroy(int id);

    @Native(symbol = "jnative_ui_show")
    static native void show(int id);

    @Native(symbol = "jnative_ui_hide")
    static native void hide(int id);

    @Native(symbol = "jnative_ui_set_title")
    static native void setTitle(int id, String title);

    @Native(symbol = "jnative_ui_set_text")
    static native void setText(int id, String text);

    @Native(symbol = "jnative_ui_set_bounds")
    static native void setBounds(int id, int x, int y, int w, int h);

    @Native(symbol = "jnative_ui_set_visible")
    static native void setVisible(int id, int visible);

    @Native(symbol = "jnative_ui_set_enabled")
    static native void setEnabled(int id, int enabled);

    @Native(symbol = "jnative_ui_set_content")
    static native void setContent(int windowId, int childId);

    @Native(symbol = "jnative_ui_reparent")
    static native void reparent(int childId, int parentId);

    @Native(symbol = "jnative_ui_set_selected")
    static native void setSelected(int id, int selected);

    @Native(symbol = "jnative_ui_get_selected")
    static native int getSelected(int id);

    @Native(symbol = "jnative_ui_set_value_i32")
    static native void setValueI32(int id, int value);

    @Native(symbol = "jnative_ui_get_value_i32")
    static native int getValueI32(int id);

    @Native(symbol = "jnative_ui_set_progress")
    static native void setProgress(int id, double progress);

    @Native(symbol = "jnative_ui_get_progress")
    static native double getProgress(int id);

    @Native(symbol = "jnative_ui_set_item_count")
    static native void setItemCount(int id, int count);

    @Native(symbol = "jnative_ui_get_item_count")
    static native int getItemCount(int id);

    @Native(symbol = "jnative_ui_set_selected_index")
    static native void setSelectedIndex(int id, int index);

    @Native(symbol = "jnative_ui_get_selected_index")
    static native int getSelectedIndex(int id);

    @Native(symbol = "jnative_ui_set_visible_rows")
    static native void setVisibleRows(int id, int rows);

    @Native(symbol = "jnative_ui_get_visible_rows")
    static native int getVisibleRows(int id);

    @Native(symbol = "jnative_ui_combo_clear")
    static native void comboClear(int id);

    @Native(symbol = "jnative_ui_combo_add_item")
    static native void comboAddItem(int id, String text);

    @Native(symbol = "jnative_ui_list_clear")
    static native void listClear(int id);

    @Native(symbol = "jnative_ui_list_add_item")
    static native void listAddItem(int id, String text);

    @Native(symbol = "jnative_ui_tree_clear")
    static native void treeClear(int id);

    @Native(symbol = "jnative_ui_tree_add_item")
    static native void treeAddItem(int id, int parentIndex, String text);

    @Native(symbol = "jnative_ui_tree_set_expanded")
    static native void treeSetExpanded(int id, int index, int expanded);

    @Native(symbol = "jnative_ui_tree_is_expanded")
    static native int treeIsExpanded(int id, int index);

    @Native(symbol = "jnative_ui_tab_add")
    static native void tabAdd(int id, String title, int contentId);

    @Native(symbol = "jnative_ui_tab_set_selected")
    static native void tabSetSelected(int id, int index);

    @Native(symbol = "jnative_ui_tab_get_selected")
    static native int tabGetSelected(int id);

    @Native(symbol = "jnative_ui_split_set_divider")
    static native void splitSetDivider(int id, double pos);

    @Native(symbol = "jnative_ui_split_get_divider")
    static native double splitGetDivider(int id);

    @Native(symbol = "jnative_ui_menu_create_bar")
    static native int menuCreateBar();

    @Native(symbol = "jnative_ui_menu_create")
    static native int menuCreate(String title);

    @Native(symbol = "jnative_ui_menu_create_item")
    static native int menuCreateItem(String text);

    @Native(symbol = "jnative_ui_menu_create_separator")
    static native int menuCreateSeparator();

    @Native(symbol = "jnative_ui_menu_add")
    static native void menuAdd(int parentMenuId, int childId);

    @Native(symbol = "jnative_ui_window_set_menubar")
    static native void windowSetMenubar(int windowId, int menuBarId);

    @Native(symbol = "jnative_ui_dialog_message")
    static native int dialogMessage(int windowId, int type, String title, String message);

    @Native(symbol = "jnative_ui_dialog_file_open")
    static native NativePtr dialogFileOpen(int windowId, String filter);

    @Native(symbol = "jnative_ui_dialog_file_save")
    static native NativePtr dialogFileSave(int windowId, String filter);

    @Native(symbol = "jnative_ui_dialog_dir_open")
    static native NativePtr dialogDirOpen(int windowId);

    @Native(symbol = "jnative_ui_dialog_free_path")
    static native void dialogFreePath(NativePtr path);

    @Native(symbol = "jnative_ui_live_windows")
    static native int liveWindows();

    @Native(symbol = "jnative_ui_live_controls")
    static native int liveControls();

    @Native(symbol = "jnative_ui_live_handles")
    static native int liveHandles();

    @Native(symbol = "jnative_ui_queued_tasks")
    static native int queuedTasks();

    @Native(symbol = "jnative_ui_events_dispatched")
    static native int eventsDispatched();

    @Native(symbol = "jnative_ui_event_arg0")
    static native int eventArg0();

    @Native(symbol = "jnative_ui_event_arg1")
    static native int eventArg1();

    @Native(symbol = "jnative_ui_event_arg2")
    static native int eventArg2();

    @Native(symbol = "jnative_ui_event_arg3")
    static native int eventArg3();

    @Native(symbol = "jnative_ui_headless_click")
    static native void headlessClick(int id);

    @Native(symbol = "jnative_ui_headless_close")
    static native void headlessClose(int id);

    @Native(symbol = "jnative_ui_headless_toggle")
    static native void headlessToggle(int id);

    @Native(symbol = "jnative_ui_headless_select")
    static native void headlessSelect(int id, int index);

    @Native(symbol = "jnative_ui_headless_change")
    static native void headlessChange(int id);

    @Native(symbol = "jnative_ui_headless_action")
    static native void headlessAction(int id);

    @Native(symbol = "jnative_ui_headless_key")
    static native void headlessKey(int id, int keyCode, int mods, int down);

    @Native(symbol = "jnative_ui_headless_mouse")
    static native void headlessMouse(int id, int type, int x, int y, int button);

    @Native(symbol = "jnative_ui_headless_focus")
    static native void headlessFocus(int id, int gained);

    @Native(symbol = "jnative_ui_headless_set_dialog_result")
    static native void headlessSetDialogResult(int result);

    @Native(symbol = "jnative_ui_headless_set_file_result")
    static native void headlessSetFileResult(String path);

    @Native(symbol = "jnative_ui_headless_paint")
    static native void headlessPaint(int id);

    @Native(symbol = "jnative_ui_headless_timer_fire")
    static native void headlessTimerFire(int id);

    @Native(symbol = "jnative_ui_canvas_invalidate")
    static native void canvasInvalidate(int id);

    @Native(symbol = "jnative_ui_canvas_present")
    static native void canvasPresent(int id, int surfaceId);

    @Native(symbol = "jnative_ui_timer_start")
    static native int timerStart(int intervalMs, int handleId);

    @Native(symbol = "jnative_ui_timer_stop")
    static native void timerStop(int timerId);
}

class UiHandleRegistry {
    static Component[] table = new Component[4096];

    static void put(int id, Component c) {
        if (id > 0 && id < table.length) {
            table[id] = c;
        }
    }

    static Component get(int id) {
        if (id <= 0 || id >= table.length) {
            return null;
        }
        return table[id];
    }

    static void remove(int id) {
        if (id > 0 && id < table.length) {
            table[id] = null;
        }
    }
}

class UiTaskQueue {
    static Runnable[] tasks = new Runnable[256];
    static int head = 0;
    static int tail = 0;
    static Mutex lock = new Mutex();

    static void enqueue(Runnable r) {
        lock.lock();
        int next = (tail + 1) % tasks.length;
        if (next == head) {
            lock.unlock();
            throw new UiException("UI task queue full");
        }
        tasks[tail] = r;
        tail = next;
        lock.unlock();
        UiNative.postTask();
    }

    static void drain() {
        while (true) {
            Runnable r = null;
            lock.lock();
            if (head == tail) {
                lock.unlock();
                return;
            }
            r = tasks[head];
            tasks[head] = null;
            head = (head + 1) % tasks.length;
            lock.unlock();
            if (r != null) {
                r.run();
            }
        }
    }

    static int size() {
        int n = tail - head;
        if (n < 0) {
            n = n + tasks.length;
        }
        return n;
    }
}

class UiMetrics {
    static int layoutPasses = 0;
    static int managedRowComponents = 0;
}

interface EventHandler<E> {
    void handle(E event);
}

interface UiHook {
    void run();
}

class UiEvent {
    Component source;
    int consumed;

    UiEvent(Component src) {
        source = src;
        consumed = 0;
    }

    Component getSource() {
        return source;
    }

    void consume() {
        consumed = 1;
    }

    boolean isConsumed() {
        return consumed != 0;
    }
}

class ActionEvent extends UiEvent {
    ActionEvent(Component src) {
        super(src);
    }
}

class ValueChangeEvent<T> extends UiEvent {
    T oldValue;
    T newValue;

    ValueChangeEvent(Component src, T oldV, T newV) {
        super(src);
        oldValue = oldV;
        newValue = newV;
    }

    T getOldValue() {
        return oldValue;
    }

    T getNewValue() {
        return newValue;
    }
}

class SelectionEvent<T> extends UiEvent {
    int selectedIndex;
    T selectedItem;

    SelectionEvent(Component src, int index, T item) {
        super(src);
        selectedIndex = index;
        selectedItem = item;
    }

    int getSelectedIndex() {
        return selectedIndex;
    }

    T getSelectedItem() {
        return selectedItem;
    }
}

class MouseEvent extends UiEvent {
    int eventType;
    int x;
    int y;
    int button;
    int clickCount;
    int shiftDown;
    int ctrlDown;
    int altDown;

    MouseEvent(Component src, int type, int mx, int my, int btn) {
        super(src);
        eventType = type;
        x = mx;
        y = my;
        button = btn;
        clickCount = 1;
        shiftDown = 0;
        ctrlDown = 0;
        altDown = 0;
    }

    int getX() {
        return x;
    }

    int getY() {
        return y;
    }

    int getButton() {
        return button;
    }

    int getEventType() {
        return eventType;
    }
}

class KeyEvent extends UiEvent {
    int keyCode;
    int character;
    int shiftDown;
    int ctrlDown;
    int altDown;
    int metaDown;
    int down;

    KeyEvent(Component src, int code, int mods, int isDown) {
        super(src);
        keyCode = code;
        character = code;
        shiftDown = (mods % 2);
        ctrlDown = ((mods / 2) % 2);
        altDown = ((mods / 4) % 2);
        metaDown = ((mods / 8) % 2);
        down = isDown;
    }
}

class FocusEvent extends UiEvent {
    int gained;

    FocusEvent(Component src, int g) {
        super(src);
        gained = g;
    }

    boolean isFocusGained() {
        return gained != 0;
    }
}

class WindowEvent extends UiEvent {
    WindowEvent(Component src) {
        super(src);
    }
}

interface CellRenderer<T> {
    String render(T value);
}

interface ListModel<T> {
    int size();
    T get(int index);
}

class ArrayListListModel<T> implements ListModel<T> {
    ArrayList<T> list;

    ArrayListListModel(ArrayList<T> items) {
        list = items;
    }

    int size() {
        if (list == null) {
            return 0;
        }
        return list.size();
    }

    T get(int index) {
        return list.get(index);
    }
}

interface TreeModel<T> {
    T root();
    int childCount(T node);
    T child(T node, int index);
}

class SelectionModel<T> {
    int selectedIndex;
    T selectedItem;
    int multi;
    ArrayList<int> selectedIndices;

    SelectionModel() {
        selectedIndex = -1;
        selectedItem = null;
        multi = 0;
        selectedIndices = new ArrayList<int>();
    }

    void clearSelection() {
        selectedIndex = -1;
        selectedItem = null;
        selectedIndices.clear();
    }

    void select(int index, T item) {
        selectedIndex = index;
        selectedItem = item;
        selectedIndices.clear();
        if (index >= 0) {
            selectedIndices.add(index);
        }
    }

    int getSelectedIndex() {
        return selectedIndex;
    }

    T getSelectedItem() {
        return selectedItem;
    }

    void setMultiple(boolean enabled) {
        if (enabled) {
            multi = 1;
        } else {
            multi = 0;
        }
    }

    boolean isMultiple() {
        return multi != 0;
    }
}

class Application {
    static Application current;
    static long uiThreadId;
    static NativeCallbackHandle dispatcherHandle;
    static int windowCount = 0;
    static int headlessMode = 0;
    static Runnable uncaughtHandler;

    Application() {
        current = this;
        uiThreadId = Thread.currentThread().id();
        UiNative.init(headlessMode);
        dispatcherHandle = Native.callback(makeDispatch());
        UiNative.setDispatcher(dispatcherHandle.pointer());
    }

    static IntBinaryFunction makeDispatch() {
        return (handleId, eventCode) -> {
            dispatchNative(handleId, eventCode);
            return 0;
        };
    }

    static Application get() {
        return current;
    }

    static void setHeadless(int headless) {
        headlessMode = headless;
    }

    static void setUncaughtExceptionHandler(Runnable handler) {
        uncaughtHandler = handler;
    }

    static void checkUiThread(String operation) {
        long tid = Thread.currentThread().id();
        if (tid != uiThreadId) {
            throw new UiThreadViolationException(
                    "UI operation must run on application thread\noperation:\n" + operation);
        }
    }

    static void invokeLater(Runnable r) {
        if (r == null) {
            return;
        }
        if (Thread.currentThread().id() == uiThreadId) {
            r.run();
            return;
        }
        UiTaskQueue.enqueue(r);
    }

    static void quit() {
        UiNative.quitNative();
    }

    void run() {
        checkUiThread("Application.run");
        UiNative.runLoop();
    }

    static void dispatchNative(int handleId, int eventCode) {
        try {
            if (eventCode == 5) {
                UiTaskQueue.drain();
                return;
            }
            Component c = UiHandleRegistry.get(handleId);
            if (c == null) {
                return;
            }
            if (eventCode == 1) {
                c.fireClick();
            } else if (eventCode == 2) {
                if (c instanceof Window) {
                    Window w = (Window) c;
                    w.fireClose();
                }
            } else if (eventCode == 4) {
                c.fireChange();
            } else if (eventCode == 6) {
                c.fireSelect(UiNative.eventArg0());
            } else if (eventCode == 7) {
                c.fireToggle(UiNative.eventArg0());
            } else if (eventCode == 8) {
                c.fireAction();
            } else if (eventCode == 9) {
                c.fireKey(UiNative.eventArg0(), UiNative.eventArg1(), UiNative.eventArg2());
            } else if (eventCode == 10) {
                c.fireMouse(UiNative.eventArg0(), UiNative.eventArg1(), UiNative.eventArg2(), UiNative.eventArg3());
            } else if (eventCode == 11) {
                c.fireFocus(UiNative.eventArg0());
            } else if (eventCode == 12) {
                c.fireAction();
            } else if (eventCode == 13) {
                c.firePaint();
            } else if (eventCode == 14) {
                c.fireTimer();
            }
        } catch (RuntimeException ex) {
            System.out.println("Uncaught UI exception: " + ex.getMessage());
            if (uncaughtHandler != null) {
                uncaughtHandler.run();
            }
        }
    }

    static void onWindowOpened() {
        windowCount = windowCount + 1;
    }

    static void onWindowClosed() {
        windowCount = windowCount - 1;
        if (windowCount <= 0) {
            quit();
        }
    }

    static int liveWindows() {
        return UiNative.liveWindows();
    }

    static int liveControls() {
        return UiNative.liveControls();
    }

    static int liveNativeHandles() {
        return UiNative.liveHandles();
    }

    static int eventQueueSize() {
        return UiTaskQueue.size();
    }
}

class Component {
    int nativeId;
    Component parent;
    String text;
    int visible;
    int enabled;
    int disposed;
    int x;
    int y;
    int width;
    int height;
    int minWidth;
    int minHeight;
    int maxWidth;
    int maxHeight;
    int prefWidth;
    int prefHeight;
    int focused;
    int layoutDirty;
    Runnable clickListener;
    ArrayList<EventHandler<ActionEvent>> actionHandlers;
    ArrayList<EventHandler<KeyEvent>> keyHandlers;
    ArrayList<EventHandler<MouseEvent>> mouseHandlers;
    ArrayList<EventHandler<FocusEvent>> focusHandlers;
    ArrayList<EventHandler<UiEvent>> changeHandlers;
    ContextMenu contextMenu;
    int isCanvasPeer;
    int mousePressedFlag;
    EventHandler<MouseEvent> mouseDownHandler;
    EventHandler<MouseEvent> mouseUpHandler;
    EventHandler<MouseEvent> mouseMoveHandler;
    EventHandler<MouseEvent> mouseDragHandler;
    UiHook paintHook;
    UiHook timerHook;

    Component() {
        visible = 1;
        enabled = 1;
        disposed = 0;
        text = "";
        width = 100;
        height = 32;
        minWidth = 0;
        minHeight = 0;
        maxWidth = 100000;
        maxHeight = 100000;
        prefWidth = 100;
        prefHeight = 32;
        focused = 0;
        layoutDirty = 0;
        isCanvasPeer = 0;
        mousePressedFlag = 0;
        actionHandlers = new ArrayList<EventHandler<ActionEvent>>();
        keyHandlers = new ArrayList<EventHandler<KeyEvent>>();
        mouseHandlers = new ArrayList<EventHandler<MouseEvent>>();
        focusHandlers = new ArrayList<EventHandler<FocusEvent>>();
        changeHandlers = new ArrayList<EventHandler<UiEvent>>();
    }

    void ensureAlive(String op) {
        Application.checkUiThread(op);
        if (disposed != 0) {
            throw new DisposedComponentException("component disposed: " + op);
        }
    }

    void setText(String value) {
        ensureAlive("Component.setText");
        if (value == null) {
            value = "";
        }
        text = value;
        if (nativeId > 0) {
            UiNative.setText(nativeId, text);
        }
        requestLayout();
    }

    String getText() {
        ensureAlive("Component.getText");
        return text;
    }

    void setVisible(boolean v) {
        ensureAlive("Component.setVisible");
        if (v) {
            visible = 1;
        } else {
            visible = 0;
        }
        if (nativeId > 0) {
            UiNative.setVisible(nativeId, visible);
        }
    }

    void setEnabled(boolean e) {
        ensureAlive("Component.setEnabled");
        if (e) {
            enabled = 1;
        } else {
            enabled = 0;
        }
        if (nativeId > 0) {
            UiNative.setEnabled(nativeId, enabled);
        }
    }

    void setBounds(int nx, int ny, int nw, int nh) {
        ensureAlive("Component.setBounds");
        x = nx;
        y = ny;
        width = nw;
        height = nh;
        if (nativeId > 0) {
            UiNative.setBounds(nativeId, x, y, width, height);
        }
    }

    void setMinSize(int w, int h) {
        minWidth = w;
        minHeight = h;
    }

    void setMaxSize(int w, int h) {
        maxWidth = w;
        maxHeight = h;
    }

    void setPrefSize(int w, int h) {
        prefWidth = w;
        prefHeight = h;
        width = w;
        height = h;
    }

    void requestLayout() {
        layoutDirty = 1;
        if (parent != null) {
            parent.requestLayout();
        }
    }

    void requestFocus() {
        ensureAlive("Component.requestFocus");
        focused = 1;
        fireFocus(1);
    }

    boolean hasFocus() {
        return focused != 0;
    }

    void setContextMenu(ContextMenu menu) {
        contextMenu = menu;
    }

    void onClick(Runnable listener) {
        ensureAlive("Component.onClick");
        clickListener = listener;
    }

    void onAction(EventHandler<ActionEvent> handler) {
        ensureAlive("Component.onAction");
        if (handler != null) {
            actionHandlers.add(handler);
        }
    }

    void onKeyDown(EventHandler<KeyEvent> handler) {
        ensureAlive("Component.onKeyDown");
        if (handler != null) {
            keyHandlers.add(handler);
        }
    }

    void onMouse(EventHandler<MouseEvent> handler) {
        ensureAlive("Component.onMouse");
        if (handler != null) {
            mouseHandlers.add(handler);
        }
    }

    void onMouseDown(EventHandler<MouseEvent> handler) {
        ensureAlive("Component.onMouseDown");
        mouseDownHandler = handler;
    }

    void onMouseUp(EventHandler<MouseEvent> handler) {
        ensureAlive("Component.onMouseUp");
        mouseUpHandler = handler;
    }

    void onMouseMove(EventHandler<MouseEvent> handler) {
        ensureAlive("Component.onMouseMove");
        mouseMoveHandler = handler;
    }

    void onMouseDrag(EventHandler<MouseEvent> handler) {
        ensureAlive("Component.onMouseDrag");
        mouseDragHandler = handler;
    }

    void onFocus(EventHandler<FocusEvent> handler) {
        ensureAlive("Component.onFocus");
        if (handler != null) {
            focusHandlers.add(handler);
        }
    }

    void onChange(EventHandler<UiEvent> handler) {
        ensureAlive("Component.onChange");
        if (handler != null) {
            changeHandlers.add(handler);
        }
    }

    void fireClick() {
        if (disposed != 0) {
            return;
        }
        if (clickListener != null) {
            clickListener.run();
        }
        fireAction();
    }

    void fireAction() {
        if (disposed != 0) {
            return;
        }
        ActionEvent ev = new ActionEvent(this);
        int n = actionHandlers.size();
        int i = 0;
        while (i < n) {
            if (!ev.isConsumed()) {
                actionHandlers.get(i).handle(ev);
            }
            i = i + 1;
        }
    }

    void fireChange() {
        if (disposed != 0) {
            return;
        }
        UiEvent ev = new UiEvent(this);
        int n = changeHandlers.size();
        int i = 0;
        while (i < n) {
            changeHandlers.get(i).handle(ev);
            i = i + 1;
        }
    }

    void fireSelect(int index) {
    }

    void fireToggle(int selected) {
    }

    void fireKey(int downType, int keyCode, int mods) {
        if (disposed != 0) {
            return;
        }
        int isDown = 0;
        if (downType == 1) {
            isDown = 1;
        }
        KeyEvent ev = new KeyEvent(this, keyCode, mods, isDown);
        int n = keyHandlers.size();
        int i = 0;
        while (i < n) {
            if (!ev.isConsumed()) {
                keyHandlers.get(i).handle(ev);
            }
            i = i + 1;
        }
    }

    void fireMouse(int type, int mx, int my, int button) {
        if (disposed != 0) {
            return;
        }
        MouseEvent ev = new MouseEvent(this, type, mx, my, button);
        if (type == 1) {
            mousePressedFlag = 1;
            if (mouseDownHandler != null) {
                mouseDownHandler.handle(ev);
            }
        } else if (type == 2) {
            mousePressedFlag = 0;
            if (mouseUpHandler != null) {
                mouseUpHandler.handle(ev);
            }
        } else if (type == 5) {
            if (mouseMoveHandler != null) {
                mouseMoveHandler.handle(ev);
            }
            if (mousePressedFlag != 0 && mouseDragHandler != null) {
                mouseDragHandler.handle(ev);
            }
        }
        int n = mouseHandlers.size();
        int i = 0;
        while (i < n) {
            if (!ev.isConsumed()) {
                mouseHandlers.get(i).handle(ev);
            }
            i = i + 1;
        }
    }

    void fireFocus(int gainedFlag) {
        if (disposed != 0) {
            return;
        }
        if (gainedFlag != 0) {
            focused = 1;
        } else {
            focused = 0;
        }
        int gained = 0;
        if (gainedFlag == 1) {
            gained = 1;
        }
        FocusEvent ev = new FocusEvent(this, gained);
        int n = focusHandlers.size();
        int i = 0;
        while (i < n) {
            focusHandlers.get(i).handle(ev);
            i = i + 1;
        }
    }

    void dispose() {
        Application.checkUiThread("Component.dispose");
        if (disposed != 0) {
            return;
        }
        disposed = 1;
        clickListener = null;
        actionHandlers.clear();
        keyHandlers.clear();
        mouseHandlers.clear();
        focusHandlers.clear();
        changeHandlers.clear();
        if (nativeId > 0) {
            UiHandleRegistry.remove(nativeId);
            UiNative.destroy(nativeId);
            nativeId = 0;
        }
    }

    int preferredWidth() {
        return prefWidth;
    }

    int preferredHeight() {
        return prefHeight;
    }

    void layout() {
        layoutDirty = 0;
        UiMetrics.layoutPasses = UiMetrics.layoutPasses + 1;
    }

    void repaint() {
    }

    void firePaint() {
        if (paintHook != null) {
            paintHook.run();
        }
    }

    void fireTimer() {
        if (timerHook != null) {
            timerHook.run();
        }
    }
}

class Control extends Component {
    Control() {
    }
}

class Container extends Component {
    Component[] children;
    int childCount;
    int spacing;
    int padding;

    Container() {
        children = new Component[64];
        childCount = 0;
        spacing = 8;
        padding = 12;
        width = 400;
        height = 300;
        prefWidth = 400;
        prefHeight = 300;
    }

    void add(Component child) {
        ensureAlive("Container.add");
        if (child == null) {
            return;
        }
        if (child.parent != null) {
            throw new UiException("component already has a parent; remove before reparenting");
        }
        if (childCount >= children.length) {
            throw new UiException("container child capacity exceeded");
        }
        children[childCount] = child;
        childCount = childCount + 1;
        child.parent = this;
        if (nativeId > 0 && child.nativeId > 0) {
            UiNative.reparent(child.nativeId, nativeId);
        }
        requestLayout();
        layout();
    }

    void remove(Component child) {
        ensureAlive("Container.remove");
        int i = 0;
        while (i < childCount) {
            if (children[i] == child) {
                int j = i;
                while (j + 1 < childCount) {
                    children[j] = children[j + 1];
                    j = j + 1;
                }
                childCount = childCount - 1;
                children[childCount] = null;
                child.parent = null;
                requestLayout();
                layout();
                return;
            }
            i = i + 1;
        }
    }

    void clear() {
        ensureAlive("Container.clear");
        int i = 0;
        while (i < childCount) {
            children[i].parent = null;
            children[i] = null;
            i = i + 1;
        }
        childCount = 0;
        layout();
    }
}

class Panel extends Container {
    Panel() {
        nativeId = UiNative.createPanel(0);
        UiHandleRegistry.put(nativeId, this);
    }
}

class VBox extends Container {
    VBox() {
        nativeId = UiNative.createPanel(0);
        UiHandleRegistry.put(nativeId, this);
    }

    void layout() {
        layoutDirty = 0;
        UiMetrics.layoutPasses = UiMetrics.layoutPasses + 1;
        int cy = padding;
        int maxW = 0;
        int i = 0;
        int availW = width - padding * 2;
        if (availW < 0) {
            availW = 0;
        }
        while (i < childCount) {
            Component c = children[i];
            int pw = c.preferredWidth();
            int ph = c.preferredHeight();
            if (pw < availW) {
                pw = availW;
            }
            /* Stretch last child to fill remaining height (window content). */
            if (i == childCount - 1 && height > cy + ph + padding) {
                ph = height - cy - padding;
            }
            c.setBounds(padding, cy, pw, ph);
            cy = cy + ph + spacing;
            if (pw > maxW) {
                maxW = pw;
            }
            i = i + 1;
        }
        int needW = maxW + padding * 2;
        int needH = cy + padding;
        if (width < needW) {
            width = needW;
        }
        if (height < needH) {
            height = needH;
        }
        prefWidth = needW;
        prefHeight = needH;
        if (nativeId > 0) {
            UiNative.setBounds(nativeId, x, y, width, height);
        }
    }
}

class HBox extends Container {
    HBox() {
        nativeId = UiNative.createPanel(0);
        UiHandleRegistry.put(nativeId, this);
    }

    void layout() {
        layoutDirty = 0;
        UiMetrics.layoutPasses = UiMetrics.layoutPasses + 1;
        int cx = padding;
        int maxH = 0;
        int i = 0;
        while (i < childCount) {
            Component c = children[i];
            int pw = c.preferredWidth();
            int ph = c.preferredHeight();
            c.setBounds(cx, padding, pw, ph);
            cx = cx + pw + spacing;
            if (ph > maxH) {
                maxH = ph;
            }
            i = i + 1;
        }
        width = cx + padding;
        height = maxH + padding * 2;
        prefWidth = width;
        prefHeight = height;
        if (nativeId > 0) {
            UiNative.setBounds(nativeId, x, y, width, height);
        }
    }
}

class GridPane extends Container {
    int[] rows;
    int[] cols;
    int[] rowSpans;
    int[] colSpans;
    int gridRows;
    int gridCols;
    int cellW;
    int cellH;

    GridPane() {
        nativeId = UiNative.createPanel(0);
        UiHandleRegistry.put(nativeId, this);
        rows = new int[64];
        cols = new int[64];
        rowSpans = new int[64];
        colSpans = new int[64];
        gridRows = 0;
        gridCols = 0;
        cellW = 120;
        cellH = 32;
    }

    void addAt(Component child, int col, int row) {
        addAtSpan(child, col, row, 1, 1);
    }

    void addAtSpan(Component child, int col, int row, int colSpan, int rowSpan) {
        ensureAlive("GridPane.addAt");
        int idx = childCount;
        add(child);
        cols[idx] = col;
        rows[idx] = row;
        colSpans[idx] = colSpan;
        rowSpans[idx] = rowSpan;
        if (row + rowSpan > gridRows) {
            gridRows = row + rowSpan;
        }
        if (col + colSpan > gridCols) {
            gridCols = col + colSpan;
        }
        layout();
    }

    void layout() {
        layoutDirty = 0;
        UiMetrics.layoutPasses = UiMetrics.layoutPasses + 1;
        int i = 0;
        while (i < childCount) {
            Component c = children[i];
            int cx = padding + cols[i] * (cellW + spacing);
            int cy = padding + rows[i] * (cellH + spacing);
            int cw = colSpans[i] * cellW + (colSpans[i] - 1) * spacing;
            int ch = rowSpans[i] * cellH + (rowSpans[i] - 1) * spacing;
            c.setBounds(cx, cy, cw, ch);
            i = i + 1;
        }
        int colGaps = 0;
        if (gridCols > 0) {
            colGaps = (gridCols - 1) * spacing;
        }
        int rowGaps = 0;
        if (gridRows > 0) {
            rowGaps = (gridRows - 1) * spacing;
        }
        width = padding * 2 + gridCols * cellW + colGaps;
        height = padding * 2 + gridRows * cellH + rowGaps;
        prefWidth = width;
        prefHeight = height;
        if (nativeId > 0) {
            UiNative.setBounds(nativeId, x, y, width, height);
        }
    }
}

class ScrollView extends Container {
    Component content;

    ScrollView() {
        nativeId = UiNative.createControl(11, 0, "");
        UiHandleRegistry.put(nativeId, this);
        width = 300;
        height = 200;
        prefWidth = 300;
        prefHeight = 200;
    }

    ScrollView(Component child) {
        nativeId = UiNative.createControl(11, 0, "");
        UiHandleRegistry.put(nativeId, this);
        width = 300;
        height = 200;
        prefWidth = 300;
        prefHeight = 200;
        setContent(child);
    }

    void setContent(Component child) {
        ensureAlive("ScrollView.setContent");
        clear();
        content = child;
        if (child != null) {
            add(child);
        }
    }

    void layout() {
        layoutDirty = 0;
        UiMetrics.layoutPasses = UiMetrics.layoutPasses + 1;
        if (content != null) {
            content.setBounds(0, 0, content.preferredWidth(), content.preferredHeight());
            content.layout();
        }
        if (nativeId > 0) {
            UiNative.setBounds(nativeId, x, y, width, height);
        }
    }
}

class Tab {
    String title;
    Component content;

    Tab(String t, Component c) {
        title = t;
        content = c;
    }
}

class TabPane extends Container {
    ArrayList<Tab> tabs;
    int selectedIndex;

    TabPane() {
        nativeId = UiNative.createControl(12, 0, "");
        UiHandleRegistry.put(nativeId, this);
        tabs = new ArrayList<Tab>();
        selectedIndex = 0;
        width = 400;
        height = 300;
        prefWidth = 400;
        prefHeight = 300;
    }

    void addTab(String title, Component content) {
        ensureAlive("TabPane.addTab");
        Tab tab = new Tab(title, content);
        tabs.add(tab);
        if (content != null) {
            add(content);
        }
        int contentId = 0;
        if (content != null) {
            contentId = content.nativeId;
        }
        UiNative.tabAdd(nativeId, title, contentId);
        setSelectedIndex(selectedIndex);
    }

    void setSelectedIndex(int index) {
        ensureAlive("TabPane.setSelectedIndex");
        if (index < 0 || index >= tabs.size()) {
            return;
        }
        selectedIndex = index;
        UiNative.tabSetSelected(nativeId, index);
        int i = 0;
        while (i < tabs.size()) {
            Tab t = tabs.get(i);
            if (t.content != null) {
                t.content.setVisible(i == index);
            }
            i = i + 1;
        }
        fireSelect(index);
        layout();
    }

    int getSelectedIndex() {
        return selectedIndex;
    }

    void fireSelect(int index) {
        selectedIndex = index;
        fireChange();
    }

    void layout() {
        layoutDirty = 0;
        UiMetrics.layoutPasses = UiMetrics.layoutPasses + 1;
        if (selectedIndex >= 0 && selectedIndex < tabs.size()) {
            Tab t = tabs.get(selectedIndex);
            if (t.content != null) {
                t.content.setBounds(padding, padding + 28, width - padding * 2, height - padding * 2 - 28);
                t.content.layout();
            }
        }
        if (nativeId > 0) {
            UiNative.setBounds(nativeId, x, y, width, height);
        }
    }
}

class SplitPane extends Container {
    int orientation;
    Component first;
    Component second;
    double divider;

    SplitPane(int orient) {
        nativeId = UiNative.createControl(13, 0, "");
        UiHandleRegistry.put(nativeId, this);
        orientation = orient;
        divider = 0.5;
        width = 600;
        height = 400;
        prefWidth = 600;
        prefHeight = 400;
    }

    void setFirst(Component c) {
        ensureAlive("SplitPane.setFirst");
        first = c;
        if (c != null && c.parent == null) {
            add(c);
        }
        layout();
    }

    void setSecond(Component c) {
        ensureAlive("SplitPane.setSecond");
        second = c;
        if (c != null && c.parent == null) {
            add(c);
        }
        layout();
    }

    void setDividerPosition(double pos) {
        ensureAlive("SplitPane.setDividerPosition");
        divider = pos;
        UiNative.splitSetDivider(nativeId, pos);
        layout();
    }

    double getDividerPosition() {
        return divider;
    }

    void layout() {
        layoutDirty = 0;
        UiMetrics.layoutPasses = UiMetrics.layoutPasses + 1;
        int pct = UiNative.getValueI32(nativeId);
        if (pct <= 0) {
            pct = 50;
        }
        if (orientation == Orientation.HORIZONTAL) {
            int leftW = width * pct / 100;
            if (first != null) {
                first.setBounds(0, 0, leftW - 2, height);
                first.layout();
            }
            if (second != null) {
                second.setBounds(leftW + 2, 0, width - leftW - 2, height);
                second.layout();
            }
        } else {
            int topH = height * pct / 100;
            if (first != null) {
                first.setBounds(0, 0, width, topH - 2);
                first.layout();
            }
            if (second != null) {
                second.setBounds(0, topH + 2, width, height - topH - 2);
                second.layout();
            }
        }
        if (nativeId > 0) {
            UiNative.setBounds(nativeId, x, y, width, height);
        }
    }
}

class Label extends Control {
    Label(String t) {
        text = t;
        width = 200;
        height = 24;
        prefWidth = 200;
        prefHeight = 24;
        nativeId = UiNative.createLabel(0, text);
        UiHandleRegistry.put(nativeId, this);
        UiNative.setText(nativeId, text);
    }
}

class Button extends Control {
    Button(String t) {
        text = t;
        width = 120;
        height = 32;
        prefWidth = 120;
        prefHeight = 32;
        nativeId = UiNative.createButton(0, text);
        UiHandleRegistry.put(nativeId, this);
        UiNative.setText(nativeId, text);
    }
}

class TextField extends Control {
    TextField() {
        text = "";
        width = 200;
        height = 28;
        prefWidth = 200;
        prefHeight = 28;
        nativeId = UiNative.createTextField(0, text);
        UiHandleRegistry.put(nativeId, this);
    }

    TextField(String t) {
        text = t;
        width = 200;
        height = 28;
        prefWidth = 200;
        prefHeight = 28;
        nativeId = UiNative.createTextField(0, text);
        UiHandleRegistry.put(nativeId, this);
        UiNative.setText(nativeId, text);
    }
}

class TextArea extends Control {
    int editable;

    TextArea() {
        text = "";
        editable = 1;
        width = 300;
        height = 120;
        prefWidth = 300;
        prefHeight = 120;
        nativeId = UiNative.createControl(9, 0, text);
        UiHandleRegistry.put(nativeId, this);
    }

    void setEditable(boolean e) {
        ensureAlive("TextArea.setEditable");
        if (e) {
            editable = 1;
            setEnabled(true);
        } else {
            editable = 0;
            setEnabled(false);
        }
    }
}

class ProgressBar extends Control {
    double progress;
    int indeterminate;

    ProgressBar() {
        progress = 0.0;
        indeterminate = 0;
        width = 200;
        height = 20;
        prefWidth = 200;
        prefHeight = 20;
        nativeId = UiNative.createControl(10, 0, "");
        UiHandleRegistry.put(nativeId, this);
    }

    void setProgress(double value) {
        ensureAlive("ProgressBar.setProgress");
        progress = value;
        UiNative.setProgress(nativeId, value);
    }

    double getProgress() {
        return progress;
    }

    void setIndeterminate(boolean value) {
        ensureAlive("ProgressBar.setIndeterminate");
        if (value) {
            indeterminate = 1;
        } else {
            indeterminate = 0;
        }
    }
}

class CheckBox extends Control {
    int selected;
    ArrayList<EventHandler<ValueChangeEvent<int>>> changeValueHandlers;

    CheckBox(String t) {
        text = t;
        selected = 0;
        width = 160;
        height = 28;
        prefWidth = 160;
        prefHeight = 28;
        changeValueHandlers = new ArrayList<EventHandler<ValueChangeEvent<int>>>();
        nativeId = UiNative.createControl(6, 0, text);
        UiHandleRegistry.put(nativeId, this);
        UiNative.setText(nativeId, text);
    }

    void setSelected(boolean value) {
        ensureAlive("CheckBox.setSelected");
        int prev = selected;
        if (value) {
            selected = 1;
        } else {
            selected = 0;
        }
        UiNative.setSelected(nativeId, selected);
        if (prev != selected) {
            fireToggle(selected);
        }
    }

    boolean isSelected() {
        return selected != 0;
    }

    void onChangeValue(EventHandler<ValueChangeEvent<int>> handler) {
        if (handler != null) {
            changeValueHandlers.add(handler);
        }
    }

    void fireToggle(int sel) {
        selected = sel;
        ValueChangeEvent<int> ev = new ValueChangeEvent<int>(this, 0, sel);
        int i = 0;
        while (i < changeValueHandlers.size()) {
            changeValueHandlers.get(i).handle(ev);
            i = i + 1;
        }
        fireChange();
    }
}

class RadioButton extends Control {
    int selected;
    ToggleGroup group;
    ArrayList<EventHandler<ValueChangeEvent<int>>> changeValueHandlers;

    RadioButton(String t) {
        text = t;
        selected = 0;
        width = 160;
        height = 28;
        prefWidth = 160;
        prefHeight = 28;
        changeValueHandlers = new ArrayList<EventHandler<ValueChangeEvent<int>>>();
        nativeId = UiNative.createControl(7, 0, text);
        UiHandleRegistry.put(nativeId, this);
        UiNative.setText(nativeId, text);
    }

    void setSelected(boolean value) {
        ensureAlive("RadioButton.setSelected");
        if (value) {
            selected = 1;
            UiNative.setSelected(nativeId, 1);
            if (group != null) {
                group.select(this);
            }
            fireToggle(1);
        } else {
            selected = 0;
            UiNative.setSelected(nativeId, 0);
            fireToggle(0);
        }
    }

    boolean isSelected() {
        return selected != 0;
    }

    void fireToggle(int sel) {
        selected = sel;
        ValueChangeEvent<int> ev = new ValueChangeEvent<int>(this, 0, sel);
        int i = 0;
        while (i < changeValueHandlers.size()) {
            changeValueHandlers.get(i).handle(ev);
            i = i + 1;
        }
        fireChange();
    }
}

class ToggleGroup {
    ArrayList<RadioButton> buttons;

    ToggleGroup() {
        buttons = new ArrayList<RadioButton>();
    }

    void add(RadioButton button) {
        if (button == null) {
            return;
        }
        buttons.add(button);
        button.group = this;
    }

    void select(RadioButton chosen) {
        int i = 0;
        while (i < buttons.size()) {
            RadioButton b = buttons.get(i);
            if (b != chosen && b.selected != 0) {
                b.selected = 0;
                UiNative.setSelected(b.nativeId, 0);
            }
            i = i + 1;
        }
    }
}

class ComboBox<T> extends Control {
    ArrayList<T> items;
    CellRenderer<T> renderer;
    int selectedIndex;
    ArrayList<EventHandler<SelectionEvent<T>>> selectionHandlers;

    ComboBox() {
        items = new ArrayList<T>();
        selectedIndex = -1;
        selectionHandlers = new ArrayList<EventHandler<SelectionEvent<T>>>();
        width = 180;
        height = 28;
        prefWidth = 180;
        prefHeight = 28;
        nativeId = UiNative.createControl(8, 0, "");
        UiHandleRegistry.put(nativeId, this);
        renderer = null;
    }

    void setRenderer(CellRenderer<T> r) {
        renderer = r;
    }

    String renderItem(T value) {
        if (renderer != null) {
            return renderer.render(value);
        }
        if (value == null) {
            return "";
        }
        return "?";
    }

    void addItem(T value) {
        ensureAlive("ComboBox.addItem");
        items.add(value);
        UiNative.comboAddItem(nativeId, renderItem(value));
    }

    void clearItems() {
        ensureAlive("ComboBox.clearItems");
        items.clear();
        UiNative.comboClear(nativeId);
        selectedIndex = -1;
    }

    void setSelectedIndex(int index) {
        ensureAlive("ComboBox.setSelectedIndex");
        if (index < -1 || index >= items.size()) {
            return;
        }
        selectedIndex = index;
        UiNative.setSelectedIndex(nativeId, index);
        fireSelect(index);
    }

    int getSelectedIndex() {
        return selectedIndex;
    }

    T getSelectedItem() {
        if (selectedIndex < 0 || selectedIndex >= items.size()) {
            return null;
        }
        return items.get(selectedIndex);
    }

    void onSelectionChanged(EventHandler<SelectionEvent<T>> handler) {
        if (handler != null) {
            selectionHandlers.add(handler);
        }
    }

    void fireSelect(int index) {
        selectedIndex = index;
        T item = null;
        if (index >= 0 && index < items.size()) {
            item = items.get(index);
        }
        SelectionEvent<T> ev = new SelectionEvent<T>(this, index, item);
        int i = 0;
        while (i < selectionHandlers.size()) {
            selectionHandlers.get(i).handle(ev);
            i = i + 1;
        }
    }
}

class ListView<T> extends Control {
    ListModel<T> model;
    CellRenderer<T> renderer;
    SelectionModel<T> selection;
    ArrayList<EventHandler<SelectionEvent<T>>> selectionHandlers;
    int visibleRows;

    ListView() {
        model = null;
        renderer = null;
        selection = new SelectionModel<T>();
        selectionHandlers = new ArrayList<EventHandler<SelectionEvent<T>>>();
        visibleRows = 20;
        width = 240;
        height = 200;
        prefWidth = 240;
        prefHeight = 200;
        nativeId = UiNative.createControl(14, 0, "");
        UiHandleRegistry.put(nativeId, this);
        UiNative.setVisibleRows(nativeId, visibleRows);
        UiMetrics.managedRowComponents = 0;
    }

    void setItems(ArrayList<T> items) {
        setModel(new ArrayListListModel<T>(items));
    }

    void setModel(ListModel<T> m) {
        ensureAlive("ListView.setModel");
        model = m;
        int n = 0;
        if (m != null) {
            n = m.size();
        }
        UiNative.setItemCount(nativeId, n);
        UiNative.listClear(nativeId);
        int show = n;
        if (show > visibleRows) {
            show = visibleRows;
        }
        int i = 0;
        while (i < show) {
            T v = model.get(i);
            UiNative.listAddItem(nativeId, renderItem(v));
            i = i + 1;
        }
    }

    void setRenderer(CellRenderer<T> r) {
        renderer = r;
    }

    String renderItem(T value) {
        if (renderer != null) {
            return renderer.render(value);
        }
        if (value == null) {
            return "";
        }
        return "?";
    }

    SelectionModel<T> getSelectionModel() {
        return selection;
    }

    void select(int index) {
        ensureAlive("ListView.select");
        if (model == null || index < 0 || index >= model.size()) {
            return;
        }
        T item = model.get(index);
        selection.select(index, item);
        UiNative.setSelectedIndex(nativeId, index);
        fireSelect(index);
    }

    void onSelectionChanged(EventHandler<SelectionEvent<T>> handler) {
        if (handler != null) {
            selectionHandlers.add(handler);
        }
    }

    void fireSelect(int index) {
        T item = null;
        if (model != null && index >= 0 && index < model.size()) {
            item = model.get(index);
            selection.select(index, item);
        }
        SelectionEvent<T> ev = new SelectionEvent<T>(this, index, item);
        int i = 0;
        while (i < selectionHandlers.size()) {
            selectionHandlers.get(i).handle(ev);
            i = i + 1;
        }
    }

    int modelSize() {
        if (model == null) {
            return 0;
        }
        return model.size();
    }

    int visibleNativeRows() {
        return UiNative.getVisibleRows(nativeId);
    }

    int managedRowComponentCount() {
        return 0;
    }
}

interface ColumnRenderer<T> {
    String getTitle();
    String renderRow(T row);
}

class TableColumn<T, R> implements ColumnRenderer<T> {
    String title;
    Function<T, R> valueProvider;
    CellRenderer<R> renderer;

    TableColumn(String t, Function<T, R> provider) {
        title = t;
        valueProvider = provider;
        renderer = null;
    }

    void setRenderer(CellRenderer<R> r) {
        renderer = r;
    }

    String render(T row) {
        R value = valueProvider.apply(row);
        if (renderer != null) {
            return renderer.render(value);
        }
        if (value == null) {
            return "";
        }
        return "?";
    }

    String renderRow(T row) {
        return render(row);
    }

    String getTitle() {
        return title;
    }
}

class UiStrings {
    static int compare(String a, String b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        int na = a.length();
        int nb = b.length();
        int i = 0;
        int n = na;
        if (nb < n) {
            n = nb;
        }
        while (i < n) {
            int ca = a.charAt(i);
            int cb = b.charAt(i);
            if (ca != cb) {
                return ca - cb;
            }
            i = i + 1;
        }
        return na - nb;
    }
}

class TableView<T> extends Control {
    ListModel<T> model;
    ArrayList<ColumnRenderer<T>> columns;
    SelectionModel<T> selection;
    ArrayList<EventHandler<SelectionEvent<T>>> selectionHandlers;
    ArrayList<int> sortedIndex;
    int sortColumn;
    int sortAsc;
    int visibleRows;

    TableView() {
        model = null;
        columns = new ArrayList<ColumnRenderer<T>>();
        selection = new SelectionModel<T>();
        selectionHandlers = new ArrayList<EventHandler<SelectionEvent<T>>>();
        sortedIndex = new ArrayList<int>();
        sortColumn = -1;
        sortAsc = 1;
        visibleRows = 30;
        width = 500;
        height = 300;
        prefWidth = 500;
        prefHeight = 300;
        nativeId = UiNative.createControl(15, 0, "");
        UiHandleRegistry.put(nativeId, this);
        UiNative.setVisibleRows(nativeId, visibleRows);
    }

    void addColumn(ColumnRenderer<T> col) {
        ensureAlive("TableView.addColumn");
        columns.add(col);
    }

    void setItems(ArrayList<T> items) {
        setModel(new ArrayListListModel<T>(items));
    }

    void setModel(ListModel<T> m) {
        ensureAlive("TableView.setModel");
        model = m;
        rebuildIndex();
        int n = 0;
        if (m != null) {
            n = m.size();
        }
        UiNative.setItemCount(nativeId, n);
    }

    void rebuildIndex() {
        sortedIndex.clear();
        if (model == null) {
            return;
        }
        int i = 0;
        while (i < model.size()) {
            sortedIndex.add(i);
            i = i + 1;
        }
    }

    void sortByColumn(int colIndex) {
        ensureAlive("TableView.sortByColumn");
        if (colIndex < 0 || colIndex >= columns.size() || model == null) {
            return;
        }
        if (sortColumn == colIndex) {
            if (sortAsc != 0) {
                sortAsc = 0;
            } else {
                sortAsc = 1;
            }
        } else {
            sortColumn = colIndex;
            sortAsc = 1;
        }
        // simple insertion sort on sortedIndex by rendered string
        int n = sortedIndex.size();
        int i = 1;
        while (i < n) {
            int key = sortedIndex.get(i);
            String keyS = cellText(key, colIndex);
            int j = i - 1;
            int moving = 1;
            while (j >= 0 && moving != 0) {
                String js = cellText(sortedIndex.get(j), colIndex);
                int cmp = UiStrings.compare(keyS, js);
                int outOfOrder = 0;
                if (sortAsc != 0 && cmp < 0) {
                    outOfOrder = 1;
                }
                if (sortAsc == 0 && cmp > 0) {
                    outOfOrder = 1;
                }
                if (outOfOrder == 0) {
                    moving = 0;
                } else {
                    sortedIndex.set(j + 1, sortedIndex.get(j));
                    j = j - 1;
                }
            }
            sortedIndex.set(j + 1, key);
            i = i + 1;
        }
    }

    String cellText(int modelIndex, int colIndex) {
        T row = model.get(modelIndex);
        ColumnRenderer<T> col = columns.get(colIndex);
        return col.renderRow(row);
    }

    void select(int viewIndex) {
        ensureAlive("TableView.select");
        if (viewIndex < 0 || viewIndex >= sortedIndex.size()) {
            return;
        }
        int modelIndex = sortedIndex.get(viewIndex);
        T item = model.get(modelIndex);
        selection.select(modelIndex, item);
        UiNative.setSelectedIndex(nativeId, viewIndex);
        fireSelect(modelIndex);
    }

    void onSelectionChanged(EventHandler<SelectionEvent<T>> handler) {
        if (handler != null) {
            selectionHandlers.add(handler);
        }
    }

    void fireSelect(int index) {
        T item = null;
        if (model != null && index >= 0 && index < model.size()) {
            item = model.get(index);
            selection.select(index, item);
        }
        SelectionEvent<T> ev = new SelectionEvent<T>(this, index, item);
        int i = 0;
        while (i < selectionHandlers.size()) {
            selectionHandlers.get(i).handle(ev);
            i = i + 1;
        }
    }

    int modelSize() {
        if (model == null) {
            return 0;
        }
        return model.size();
    }

    int visibleNativeRows() {
        return UiNative.getVisibleRows(nativeId);
    }

    int managedRowComponentCount() {
        return 0;
    }

    SelectionModel<T> getSelectionModel() {
        return selection;
    }
}

class TreeNode<T> {
    T value;
    ArrayList<TreeNode<T>> children;
    int expanded;

    TreeNode(T v) {
        value = v;
        children = new ArrayList<TreeNode<T>>();
        expanded = 0;
    }

    void addChild(TreeNode<T> child) {
        children.add(child);
    }
}

class TreeView<T> extends Control {
    TreeModel<T> model;
    T root;
    ArrayList<T> flat;
    ArrayList<T> expandedNodes;
    CellRenderer<T> renderer;
    SelectionModel<T> selection;
    ArrayList<EventHandler<SelectionEvent<T>>> selectionHandlers;

    TreeView() {
        model = null;
        root = null;
        flat = new ArrayList<T>();
        expandedNodes = new ArrayList<T>();
        renderer = null;
        selection = new SelectionModel<T>();
        selectionHandlers = new ArrayList<EventHandler<SelectionEvent<T>>>();
        width = 240;
        height = 300;
        prefWidth = 240;
        prefHeight = 300;
        nativeId = UiNative.createControl(16, 0, "");
        UiHandleRegistry.put(nativeId, this);
    }

    void setModel(TreeModel<T> m) {
        ensureAlive("TreeView.setModel");
        model = m;
        if (m != null) {
            root = m.root();
        }
        rebuildFlat();
    }

    void setRoot(T r) {
        root = r;
        rebuildFlat();
    }

    void setRenderer(CellRenderer<T> r) {
        renderer = r;
    }

    String renderItem(T value) {
        if (renderer != null) {
            return renderer.render(value);
        }
        if (value == null) {
            return "";
        }
        return "?";
    }

    int isNodeExpanded(T node) {
        int i = 0;
        while (i < expandedNodes.size()) {
            if (expandedNodes.get(i) == node) {
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }

    void setNodeExpanded(T node, int expanded) {
        int i = 0;
        while (i < expandedNodes.size()) {
            if (expandedNodes.get(i) == node) {
                if (expanded == 0) {
                    expandedNodes.removeAt(i);
                }
                return;
            }
            i = i + 1;
        }
        if (expanded != 0) {
            expandedNodes.add(node);
        }
    }

    void rebuildFlat() {
        flat.clear();
        UiNative.treeClear(nativeId);
        if (model == null || root == null) {
            return;
        }
        visit(root, -1);
        UiNative.setItemCount(nativeId, flat.size());
    }

    void visit(T node, int parentIndex) {
        int idx = flat.size();
        flat.add(node);
        UiNative.treeAddItem(nativeId, parentIndex, renderItem(node));
        if (isNodeExpanded(node) != 0) {
            UiNative.treeSetExpanded(nativeId, idx, 1);
            int n = model.childCount(node);
            int i = 0;
            while (i < n) {
                visit(model.child(node, i), idx);
                i = i + 1;
            }
        }
    }

    void expand(int index) {
        ensureAlive("TreeView.expand");
        if (index < 0 || index >= flat.size()) {
            return;
        }
        setNodeExpanded(flat.get(index), 1);
        rebuildFlat();
    }

    void collapse(int index) {
        ensureAlive("TreeView.collapse");
        if (index < 0 || index >= flat.size()) {
            return;
        }
        setNodeExpanded(flat.get(index), 0);
        rebuildFlat();
    }

    boolean isExpanded(int index) {
        if (index < 0 || index >= flat.size()) {
            return false;
        }
        return isNodeExpanded(flat.get(index)) != 0;
    }

    void select(int index) {
        ensureAlive("TreeView.select");
        if (index < 0 || index >= flat.size()) {
            return;
        }
        T item = flat.get(index);
        selection.select(index, item);
        UiNative.setSelectedIndex(nativeId, index);
        fireSelect(index);
    }

    void onSelectionChanged(EventHandler<SelectionEvent<T>> handler) {
        if (handler != null) {
            selectionHandlers.add(handler);
        }
    }

    void fireSelect(int index) {
        T item = null;
        if (index >= 0 && index < flat.size()) {
            item = flat.get(index);
            selection.select(index, item);
        }
        SelectionEvent<T> ev = new SelectionEvent<T>(this, index, item);
        int i = 0;
        while (i < selectionHandlers.size()) {
            selectionHandlers.get(i).handle(ev);
            i = i + 1;
        }
    }

    SelectionModel<T> getSelectionModel() {
        return selection;
    }

    int flatSize() {
        return flat.size();
    }
}

class MenuItem extends Control {
    Runnable action;
    KeyCombination shortcut;
    int isSeparator;

    MenuItem(String t) {
        text = t;
        action = null;
        shortcut = null;
        isSeparator = 0;
        nativeId = UiNative.menuCreateItem(t);
        UiHandleRegistry.put(nativeId, this);
    }

    MenuItem(String t, Runnable act) {
        text = t;
        action = act;
        shortcut = null;
        isSeparator = 0;
        nativeId = UiNative.menuCreateItem(t);
        UiHandleRegistry.put(nativeId, this);
    }

    void setAction(Runnable act) {
        action = act;
    }

    void setShortcut(KeyCombination kc) {
        shortcut = kc;
    }

    void fireAction() {
        if (disposed != 0) {
            return;
        }
        if (action != null) {
            action.run();
        }
        ActionEvent ev = new ActionEvent(this);
        int n = actionHandlers.size();
        int i = 0;
        while (i < n) {
            if (!ev.isConsumed()) {
                actionHandlers.get(i).handle(ev);
            }
            i = i + 1;
        }
    }
}

class SeparatorMenuItem extends MenuItem {
    SeparatorMenuItem() {
        super("-");
        isSeparator = 1;
    }
}

class Menu extends Control {
    ArrayList<Control> items;

    Menu(String t) {
        text = t;
        items = new ArrayList<Control>();
        nativeId = UiNative.menuCreate(t);
        UiHandleRegistry.put(nativeId, this);
    }

    void add(MenuItem item) {
        ensureAlive("Menu.add");
        items.add(item);
        UiNative.menuAdd(nativeId, item.nativeId);
    }

    void addMenu(Menu submenu) {
        ensureAlive("Menu.addMenu");
        items.add(submenu);
        UiNative.menuAdd(nativeId, submenu.nativeId);
    }

    void addSeparator() {
        add(new SeparatorMenuItem());
    }
}

class MenuBar extends Control {
    ArrayList<Menu> menus;

    MenuBar() {
        menus = new ArrayList<Menu>();
        nativeId = UiNative.menuCreateBar();
        UiHandleRegistry.put(nativeId, this);
    }

    void add(Menu menu) {
        ensureAlive("MenuBar.add");
        menus.add(menu);
        UiNative.menuAdd(nativeId, menu.nativeId);
    }
}

class ContextMenu extends Menu {
    ContextMenu() {
        super("context");
    }
}

class Dialogs {
    static void info(Window owner, String message) {
        Application.checkUiThread("Dialogs.info");
        int wid = 0;
        if (owner != null) {
            wid = owner.nativeId;
        }
        UiNative.dialogMessage(wid, 0, "Info", message);
    }

    static boolean confirm(Window owner, String message) {
        Application.checkUiThread("Dialogs.confirm");
        int wid = 0;
        if (owner != null) {
            wid = owner.nativeId;
        }
        int r = UiNative.dialogMessage(wid, 1, "Confirm", message);
        return r != 0;
    }

    static void error(Window owner, String message) {
        Application.checkUiThread("Dialogs.error");
        int wid = 0;
        if (owner != null) {
            wid = owner.nativeId;
        }
        UiNative.dialogMessage(wid, 2, "Error", message);
    }
}

class FileDialog {
    static String open(Window owner, String filter) {
        Application.checkUiThread("FileDialog.open");
        int wid = 0;
        if (owner != null) {
            wid = owner.nativeId;
        }
        NativePtr p = UiNative.dialogFileOpen(wid, filter);
        if (p == NativePtr.NULL) {
            return null;
        }
        String path = NativeMemory.fromCString(p);
        UiNative.dialogFreePath(p);
        return path;
    }

    static String save(Window owner, String filter) {
        Application.checkUiThread("FileDialog.save");
        int wid = 0;
        if (owner != null) {
            wid = owner.nativeId;
        }
        NativePtr p = UiNative.dialogFileSave(wid, filter);
        if (p == NativePtr.NULL) {
            return null;
        }
        String path = NativeMemory.fromCString(p);
        UiNative.dialogFreePath(p);
        return path;
    }
}

class DirectoryDialog {
    static String open(Window owner) {
        Application.checkUiThread("DirectoryDialog.open");
        int wid = 0;
        if (owner != null) {
            wid = owner.nativeId;
        }
        NativePtr p = UiNative.dialogDirOpen(wid);
        if (p == NativePtr.NULL) {
            return null;
        }
        String path = NativeMemory.fromCString(p);
        UiNative.dialogFreePath(p);
        return path;
    }
}

/** Lightweight popup window for completion / hover (0.21). */
class PopupWindow {
    Window window;
    Component content;
    int shown;

    PopupWindow() {
        window = new Window("Popup", 320, 240);
        shown = 0;
    }

    void setContent(Component c) {
        content = c;
        window.setContent(c);
    }

    void showAt(int x, int y, int w, int h) {
        if (w < 80) {
            w = 80;
        }
        if (h < 40) {
            h = 40;
        }
        window.setBounds(x, y, w, h);
        window.show();
        shown = 1;
    }

    void hide() {
        window.hide();
        shown = 0;
    }

    boolean isShowing() {
        return shown != 0;
    }
}

class Window extends Component {
    Component content;
    Runnable closeListener;
    String title;
    int shown;
    MenuBar menuBar;

    Window(String t, int w, int h) {
        title = t;
        width = w;
        height = h;
        shown = 0;
        nativeId = UiNative.createWindow(title, width, height);
        if (nativeId <= 0) {
            throw new UiNativeException("failed to create window");
        }
        UiHandleRegistry.put(nativeId, this);
        Application.onWindowOpened();
    }

    void setTitle(String t) {
        ensureAlive("Window.setTitle");
        title = t;
        UiNative.setTitle(nativeId, title);
    }

    void setSize(int w, int h) {
        ensureAlive("Window.setSize");
        width = w;
        height = h;
        UiNative.setBounds(nativeId, x, y, width, height);
        if (content != null) {
            content.setBounds(0, 0, width, height);
            content.layout();
        }
    }

    void setContent(Component component) {
        ensureAlive("Window.setContent");
        content = component;
        if (component != null) {
            component.parent = this;
            UiNative.setContent(nativeId, component.nativeId);
            component.setBounds(0, 0, width, height);
            component.layout();
        }
    }

    void setMenuBar(MenuBar bar) {
        ensureAlive("Window.setMenuBar");
        menuBar = bar;
        if (bar != null) {
            UiNative.windowSetMenubar(nativeId, bar.nativeId);
        }
    }

    void show() {
        ensureAlive("Window.show");
        shown = 1;
        UiNative.show(nativeId);
        if (content != null) {
            content.layout();
        }
    }

    void hide() {
        ensureAlive("Window.hide");
        shown = 0;
        UiNative.hide(nativeId);
    }

    void close() {
        ensureAlive("Window.close");
        fireClose();
    }

    boolean isVisible() {
        return shown != 0;
    }

    void onClose(Runnable listener) {
        ensureAlive("Window.onClose");
        closeListener = listener;
    }

    void fireClose() {
        if (disposed != 0) {
            return;
        }
        if (closeListener != null) {
            closeListener.run();
        }
        if (content != null) {
            content.dispose();
            content = null;
        }
        dispose();
        Application.onWindowClosed();
    }
}

class UiTest {
    static void click(Button b) {
        Application.checkUiThread("UiTest.click");
        UiNative.headlessClick(b.nativeId);
    }

    static void closeWindow(Window w) {
        Application.checkUiThread("UiTest.closeWindow");
        UiNative.headlessClose(w.nativeId);
    }

    static void toggle(CheckBox box) {
        Application.checkUiThread("UiTest.toggle");
        UiNative.headlessToggle(box.nativeId);
    }

    static void toggleRadio(RadioButton box) {
        Application.checkUiThread("UiTest.toggleRadio");
        UiNative.headlessToggle(box.nativeId);
    }

    static void select(Component c, int index) {
        Application.checkUiThread("UiTest.select");
        UiNative.headlessSelect(c.nativeId, index);
    }

    static void action(MenuItem item) {
        Application.checkUiThread("UiTest.action");
        UiNative.headlessAction(item.nativeId);
    }

    static void keyPress(Component c, int keyCode) {
        Application.checkUiThread("UiTest.keyPress");
        UiNative.headlessKey(c.nativeId, keyCode, 0, 1);
    }

    static void mouseClick(Component c, int mx, int my) {
        Application.checkUiThread("UiTest.mouseClick");
        UiNative.headlessMouse(c.nativeId, 3, mx, my, 1);
    }

    static void mouseDown(Component c, int mx, int my) {
        Application.checkUiThread("UiTest.mouseDown");
        UiNative.headlessMouse(c.nativeId, 1, mx, my, 1);
    }

    static void mouseMove(Component c, int mx, int my) {
        Application.checkUiThread("UiTest.mouseMove");
        UiNative.headlessMouse(c.nativeId, 5, mx, my, 1);
    }

    static void mouseUp(Component c, int mx, int my) {
        Application.checkUiThread("UiTest.mouseUp");
        UiNative.headlessMouse(c.nativeId, 2, mx, my, 1);
    }

    static void paint(Component c) {
        Application.checkUiThread("UiTest.paint");
        UiNative.headlessPaint(c.nativeId);
    }

    static void timerFire(Component c) {
        Application.checkUiThread("UiTest.timerFire");
        UiNative.headlessTimerFire(c.nativeId);
    }

    static void focus(Component c) {
        Application.checkUiThread("UiTest.focus");
        UiNative.headlessFocus(c.nativeId, 1);
    }

    static void setDialogResult(int result) {
        UiNative.headlessSetDialogResult(result);
    }

    static void setFileResult(String path) {
        UiNative.headlessSetFileResult(path);
    }

    static void flush() {
        UiTaskQueue.drain();
    }
}

class UiTestHarness {
    Application app;
    Window window;

    UiTestHarness(String title, int w, int h) {
        Application.setHeadless(1);
        app = new Application();
        window = new Window(title, w, h);
    }

    Window getWindow() {
        return window;
    }

    void show() {
        window.show();
    }

    void shutdown() {
        UiTest.closeWindow(window);
        app.run();
    }
}
