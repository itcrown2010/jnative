/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

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
    /** Return / Enter — matches Cocoa NSReturn (13), not LF (10). */
    static int ENTER = 13;
    static int RETURN = 13;
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

    /** Platform primary modifier: Command on macOS, Control elsewhere. Resolved at match time.
 * @author 张晓达
 */
    KeyCombination withPrimary() {
        shortcut = 1;
        ctrl = 1;
        meta = 0;
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

    @Native(symbol = "jnative_ui_request_quit")
    static native void requestQuitNative();

    @Native(symbol = "jnative_ui_set_app_name")
    static native void setAppName(String name);

    @Native(symbol = "jnative_ui_menu_set_shortcut")
    static native void menuSetShortcut(int itemId, int keyCode, int mods);

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

    @Native(symbol = "jnative_ui_close_window")
    static native void closeWindow(int id);

    @Native(symbol = "jnative_ui_show")
    static native void show(int id);

    @Native(symbol = "jnative_ui_hide")
    static native void hide(int id);

    @Native(symbol = "jnative_ui_set_title")
    static native void setTitle(int id, String title);

    @Native(symbol = "jnative_ui_set_text")
    static native void setText(int id, String text);

    @Native(symbol = "jnative_ui_copy_text")
    static native NativePtr copyText(int id);

    @Native(symbol = "jnative_ui_set_bounds")
    static native void setBounds(int id, int x, int y, int w, int h);

    @Native(symbol = "jnative_ui_set_visible")
    static native void setVisible(int id, int visible);

    @Native(symbol = "jnative_ui_set_enabled")
    static native void setEnabled(int id, int enabled);

    @Native(symbol = "jnative_ui_set_visual")
    static native void setVisual(int id, int bgRgba, int fgRgba, int cornerRadius, int fontSize, int textAlign);

    @Native(symbol = "jnative_ui_window_set_min_size")
    static native void windowSetMinSize(int windowId, int w, int h);

    @Native(symbol = "jnative_ui_button_flash")
    static native void buttonFlash(int id, int pressed);

    @Native(symbol = "jnative_ui_focus")
    static native void focus(int id);

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

    @Native(symbol = "jnative_ui_table_set_columns")
    static native void tableSetColumns(int id, String titlesTsv);

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

    @Native(symbol = "jnative_ui_platform_family")
    static native int platformFamily();

    @Native(symbol = "jnative_ui_get_scale_percent")
    static native int getScalePercent(int windowId);

    @Native(symbol = "jnative_ui_system_dark")
    static native int systemDark();

    @Native(symbol = "jnative_ui_set_appearance_mode")
    static native void setAppearanceModeNative(int mode);

    @Native(symbol = "jnative_ui_set_tooltip")
    static native void setTooltip(int id, String tip);

    @Native(symbol = "jnative_ui_set_accessible")
    static native void setAccessible(int id, String name, String description);

    @Native(symbol = "jnative_ui_set_accessible_title_ui")
    static native void setAccessibleTitleUi(int fieldId, int labelId);

    @Native(symbol = "jnative_ui_set_next_key_view")
    static native void setNextKeyView(int fromId, int toId);

    @Native(symbol = "jnative_ui_window_set_default_button")
    static native void windowSetDefaultButton(int windowId, int buttonId);

    @Native(symbol = "jnative_ui_window_set_cancel_button")
    static native void windowSetCancelButton(int windowId, int buttonId);

    @Native(symbol = "jnative_ui_prefers_reduced_motion")
    static native int prefersReducedMotion();

    @Native(symbol = "jnative_ui_prefers_increased_contrast")
    static native int prefersIncreasedContrast();

    @Native(symbol = "jnative_ui_set_button_symbol")
    static native void setButtonSymbol(int id, String symbolName);

    @Native(symbol = "jnative_ui_features")
    static native int features();

    @Native(symbol = "jnative_ui_table_get_scroll_y")
    static native int tableGetScrollY(int id);

    @Native(symbol = "jnative_ui_table_set_scroll_y")
    static native void tableSetScrollY(int id, int y);

    @Native(symbol = "jnative_ui_context_menu_show")
    static native int contextMenuShow(int menuId, int screenX, int screenY);

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
                if (Application.quitting != 0) {
                    continue;
                }
                r.run();
            }
        }
    }

    /** Drop pending UI tasks (used on quit so late workers cannot touch destroyed UI). */
    static void clear() {
        lock.lock();
        head = 0;
        tail = 0;
        int i = 0;
        while (i < tasks.length) {
            tasks[i] = null;
            i = i + 1;
        }
        lock.unlock();
    }

    static int size() {
        int n = tail - head;
        if (n < 0) {
            n = n + tasks.length;
        }
        return n;
    }
}

/**
 * Merge high-frequency UI updates: only the latest Runnable runs after the next drain.
 * Safe with quit (dropped when {@link Application#quitting}) and does not hold locks across run.
 */
class UiCoalesce {
    static Mutex lock = new Mutex();
    static Runnable pending;
    static int posted;

    static void schedule(Runnable r) {
        if (r == null) {
            return;
        }
        if (Application.quitting != 0) {
            return;
        }
        lock.lock();
        pending = r;
        if (posted != 0) {
            lock.unlock();
            return;
        }
        posted = 1;
        lock.unlock();
        UiTaskQueue.enqueue(() -> {
            Runnable work = null;
            lock.lock();
            work = pending;
            pending = null;
            posted = 0;
            lock.unlock();
            if (work != null && Application.quitting == 0) {
                work.run();
            }
        });
    }

    static void clear() {
        lock.lock();
        pending = null;
        posted = 0;
        lock.unlock();
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

/**
 * Cancellable registration for Component event handlers.
 * cancel() is idempotent; after cancel the closure is dropped from the live list
 * and this subscription releases its handler references.
 * Dispatch snapshots the list at fire time — a handler cancelled mid-dispatch may still
 * run for the current event if it was already snapshotted; it will not run for later events.
 * Adding/removing other subscriptions during dispatch affects later events only (snapshot).
 * Handler exceptions propagate to the fire* caller (not swallowed).
 * Component.dispose() cancels all outstanding subscriptions owned by that component.
 */
class UiSubscription {
    Component owner;
    int kind;
    EventHandler<ActionEvent> actionHandler;
    EventHandler<UiEvent> changeHandler;
    Runnable clickHandler;
    int cancelled;

    static int KIND_ACTION = 1;
    static int KIND_CLICK = 2;
    static int KIND_CHANGE = 3;

    UiSubscription() {
        owner = null;
        kind = 0;
        actionHandler = null;
        changeHandler = null;
        clickHandler = null;
        cancelled = 0;
    }

    boolean isCancelled() {
        return cancelled != 0;
    }

    void cancel() {
        if (cancelled != 0) {
            return;
        }
        cancelled = 1;
        Component c = owner;
        EventHandler<ActionEvent> ah = actionHandler;
        EventHandler<UiEvent> ch = changeHandler;
        Runnable rh = clickHandler;
        owner = null;
        actionHandler = null;
        changeHandler = null;
        clickHandler = null;
        if (c == null) {
            return;
        }
        c.detachSubscription(this);
        if (kind == KIND_ACTION) {
            c.removeActionHandler(ah);
        } else if (kind == KIND_CLICK) {
            c.clearClickListener(rh);
        } else if (kind == KIND_CHANGE) {
            c.removeChangeHandler(ch);
        }
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
    /**
     * Contract (macOS): closing the last managed window quits by default (document /
     * utility apps). Set to 0 to keep the process alive after the last window closes
     * (Dock stays; Quit / Cmd+Q still calls {@link #requestQuit()}).
     */
    static int quitOnLastWindowClose = 1;
    static String appName = "JNative";
    static ArrayList<Runnable> quitHooks;
    static ArrayList<Window> openWindows;
    static Window activeWindow;
    static int quitting;
    static int quitHookFailures;

    Application() {
        current = this;
        uiThreadId = Thread.currentThread().id();
        quitting = 0;
        quitHookFailures = 0;
        if (quitHooks == null) {
            quitHooks = new ArrayList<Runnable>();
        }
        if (openWindows == null) {
            openWindows = new ArrayList<Window>();
        }
        UiNative.init(headlessMode);
        if (appName != null) {
            UiNative.setAppName(appName);
        }
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
        if (quitting != 0) {
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

    static void setAppName(String name) {
        if (name == null || name.length() == 0) {
            name = "JNative";
        }
        appName = name;
        UiNative.setAppName(name);
    }

    static void setQuitOnLastWindowClose(boolean quit) {
        if (quit) {
            quitOnLastWindowClose = 1;
        } else {
            quitOnLastWindowClose = 0;
        }
    }

    /** Register a bounded cleanup hook (stop sampling, close DB, dispose scopes). */
    static void onQuit(Runnable hook) {
        if (hook == null) {
            return;
        }
        if (quitHooks == null) {
            quitHooks = new ArrayList<Runnable>();
        }
        quitHooks.add(hook);
    }

    /**
     * Managed quit: run quit hooks (failures logged, not fatal), drop pending UI tasks,
     * force-close windows (vetoClose ignored while quitting), then native quit.
     * Dirty-document confirm must run in the Quit command before calling this.
     */
    static void requestQuit() {
        checkUiThread("Application.requestQuit");
        if (quitting != 0) {
            return;
        }
        quitting = 1;
        UiTaskQueue.clear();
        UiCoalesce.clear();
        if (quitHooks != null) {
            int i = 0;
            while (i < quitHooks.size()) {
                try {
                    quitHooks.get(i).run();
                } catch (RuntimeException ex) {
                    quitHookFailures = quitHookFailures + 1;
                    String qmsg = ex.getMessage();
                    if (qmsg == null) {
                        qmsg = "(no message)";
                    }
                    System.out.println("Quit hook failed: " + qmsg);
                }
                i = i + 1;
            }
        }
        /* Close remaining windows without re-entering quit-on-last-window. */
        int prev = quitOnLastWindowClose;
        quitOnLastWindowClose = 0;
        if (openWindows != null) {
            ArrayList<Window> snap = new ArrayList<Window>();
            int j = 0;
            while (j < openWindows.size()) {
                snap.add(openWindows.get(j));
                j = j + 1;
            }
            j = 0;
            while (j < snap.size()) {
                Window w = snap.get(j);
                if (w != null && w.disposed == 0) {
                    w.close();
                }
                j = j + 1;
            }
        }
        quitOnLastWindowClose = prev;
        UiTaskQueue.clear();
        UiCoalesce.clear();
        UiNative.quitNative();
    }

    void run() {
        checkUiThread("Application.run");
        UiNative.runLoop();
    }

    static void dispatchNative(int handleId, int eventCode) {
        try {
            if (eventCode == 16) {
                requestQuit();
                return;
            }
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
            } else if (eventCode == 3) {
                if (c instanceof Window) {
                    Window w = (Window) c;
                    w.onNativeResize(UiNative.eventArg0(), UiNative.eventArg1());
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
            } else if (eventCode == 15) {
                c.fireHeader(UiNative.eventArg0());
            }
        } catch (RuntimeException ex) {
            String msg = ex.getMessage();
            if (msg == null) {
                msg = "(no message)";
            }
            System.out.println("Uncaught UI exception: " + msg);
            if (uncaughtHandler != null) {
                uncaughtHandler.run();
            }
        }
    }

    static void onWindowOpened(Window w) {
        windowCount = windowCount + 1;
        if (openWindows == null) {
            openWindows = new ArrayList<Window>();
        }
        if (w != null) {
            openWindows.add(w);
            activeWindow = w;
        }
    }

    static void onWindowClosed(Window w) {
        if (openWindows != null && w != null) {
            int i = 0;
            while (i < openWindows.size()) {
                if (openWindows.get(i) == w) {
                    openWindows.removeAt(i);
                    break;
                }
                i = i + 1;
            }
        }
        if (activeWindow == w) {
            activeWindow = null;
            if (openWindows != null && openWindows.size() > 0) {
                activeWindow = openWindows.get(openWindows.size() - 1);
            }
        }
        windowCount = windowCount - 1;
        if (windowCount <= 0 && quitOnLastWindowClose != 0 && quitting == 0) {
            requestQuit();
        }
    }

    static Window getActiveWindow() {
        return activeWindow;
    }

    /** @deprecated use onWindowOpened(Window) */
    static void onWindowOpened() {
        onWindowOpened(null);
    }

    /** @deprecated use onWindowClosed(Window) */
    static void onWindowClosed() {
        onWindowClosed(null);
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
    static Component lastClicked;
    int nativeId;
    Component parent;
    String text;
    /** Optional app-defined payload (e.g. breadcrumb path). Not shown in UI. */
    String userData;
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
    /** Tab order: lower first; -1 = not in managed traversal (native loop still applies). */
    int tabIndex;
    String accessibleName;
    String accessibleDescription;
    int layoutDirty;
    Runnable clickListener;
    ArrayList<EventHandler<ActionEvent>> actionHandlers;
    ArrayList<EventHandler<KeyEvent>> keyHandlers;
    ArrayList<EventHandler<MouseEvent>> mouseHandlers;
    ArrayList<EventHandler<FocusEvent>> focusHandlers;
    ArrayList<EventHandler<UiEvent>> changeHandlers;
    /** Live cancellable subscriptions; cleared/cancelled on dispose. */
    ArrayList<UiSubscription> liveSubscriptions;
    ContextMenu contextMenu;
    int isCanvasPeer;
    int mousePressedFlag;
    /** StackPane placement: 0=fill, 1=trailing (right), 2=leading (left). */
    int stackPlacement;
    /** VBox vertical grow: 0=pack, 1=consume leftover height. */
    int vgrow;
    /** HBox horizontal grow: 0=pack to prefWidth, 1=share leftover (same as prefWidth==0). */
    int hgrow;
    int bgRgba;
    int fgRgba;
    int cornerRadius;
    int fontSizePt;
    int textAlign;
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
        userData = "";
        width = 100;
        height = 32;
        minWidth = 0;
        minHeight = 0;
        maxWidth = 100000;
        maxHeight = 100000;
        prefWidth = 100;
        prefHeight = 32;
        focused = 0;
        tabIndex = -1;
        accessibleName = null;
        accessibleDescription = null;
        layoutDirty = 0;
        isCanvasPeer = 0;
        mousePressedFlag = 0;
        stackPlacement = 0;
        vgrow = 0;
        hgrow = 0;
        /* 1 = leave unchanged (must not collide with opaque white 0xFFFFFFFF / -1). */
        bgRgba = 1;
        fgRgba = 1;
        cornerRadius = -1;
        fontSizePt = -1;
        textAlign = -1;
        actionHandlers = new ArrayList<EventHandler<ActionEvent>>();
        keyHandlers = new ArrayList<EventHandler<KeyEvent>>();
        mouseHandlers = new ArrayList<EventHandler<MouseEvent>>();
        focusHandlers = new ArrayList<EventHandler<FocusEvent>>();
        changeHandlers = new ArrayList<EventHandler<UiEvent>>();
        liveSubscriptions = new ArrayList<UiSubscription>();
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
        requestLayout();
        /* Visibility changes must reflow parents (e.g. find bar in VBox). */
        Component p = parent;
        while (p != null) {
            if (p instanceof Window) {
                Window win = (Window) p;
                if (win.content != null) {
                    win.content.setBounds(0, 0, win.width, win.height);
                    win.content.layout();
                }
                return;
            }
            if (p instanceof Container) {
                ((Container) p).layout();
            }
            p = p.parent;
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
        int sizeChanged = 0;
        if (nw != width || nh != height) {
            sizeChanged = 1;
        }
        x = nx;
        y = ny;
        width = nw;
        height = nh;
        if (nativeId > 0) {
            UiNative.setBounds(nativeId, x, y, width, height);
        }
        /* Relayout children after the native frame is final so Cocoa Y-flip
           uses the correct superview height (toolbar buttons, etc.).
           Cast explicitly — override dispatch of layout() is unreliable. */
        if (sizeChanged != 0) {
            if (this instanceof VBox) {
                VBox vb = (VBox) this;
                vb.layout();
            } else if (this instanceof HBox) {
                HBox hb = (HBox) this;
                hb.layout();
            } else if (this instanceof SplitPane) {
                SplitPane sp = (SplitPane) this;
                sp.layout();
            } else if (this instanceof GridPane) {
                GridPane gp = (GridPane) this;
                gp.layout();
            } else if (this instanceof StackPane) {
                StackPane spn = (StackPane) this;
                spn.layout();
            } else if (this instanceof TabPane) {
                TabPane tp = (TabPane) this;
                tp.layout();
            }
        }
    }

    void setMinSize(int w, int h) {
        if (w < 0 || h < 0) {
            throw new UiException("setMinSize: width and height must be >= 0 (got "
                    + w + "x" + h + ")");
        }
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

    void setTabIndex(int index) {
        ensureAlive("Component.setTabIndex");
        tabIndex = index;
    }

    /**
     * VoiceOver / AX: label and optional help. Prefer native control titles when enough;
     * set name for icon-only buttons.
     */
    void setAccessibleName(String name) {
        ensureAlive("Component.setAccessibleName");
        accessibleName = name;
        syncAccessible();
    }

    void setAccessibleDescription(String desc) {
        ensureAlive("Component.setAccessibleDescription");
        accessibleDescription = desc;
        syncAccessible();
    }

    void syncAccessible() {
        if (nativeId <= 0) {
            return;
        }
        String n = accessibleName;
        String d = accessibleDescription;
        if (n == null) {
            n = "";
        }
        if (d == null) {
            d = "";
        }
        UiNative.setAccessible(nativeId, n, d);
    }

    void setToolTip(String tip) {
        ensureAlive("Component.setToolTip");
        if (tip == null) {
            tip = "";
        }
        if (nativeId > 0) {
            UiNative.setTooltip(nativeId, tip);
        }
    }

    /** Pack 0xAARRGGBB as signed int (channel values 0..255). */
    static int rgba(int r, int g, int b, int a) {
        int out = 0;
        out = out + a * 16777216;
        out = out + r * 65536;
        out = out + g * 256;
        out = out + b;
        return out;
    }

    void setVisual(int bg, int fg, int radius, int fontPt, int align) {
        ensureAlive("Component.setVisual");
        bgRgba = bg;
        fgRgba = fg;
        cornerRadius = radius;
        fontSizePt = fontPt;
        textAlign = align;
        if (nativeId > 0) {
            UiNative.setVisual(nativeId, bg, fg, radius, fontPt, align);
        }
    }

    /** Sentinel for setVisual channels: leave existing peer value untouched. */
    static int VISUAL_KEEP = 1;

    void setBackgroundRgba(int rgba) {
        ensureAlive("Component.setBackgroundRgba");
        bgRgba = rgba;
        if (nativeId > 0) {
            UiNative.setVisual(nativeId, rgba, VISUAL_KEEP, cornerRadius, -1, -1);
        }
    }

    void setForegroundRgba(int rgba) {
        ensureAlive("Component.setForegroundRgba");
        fgRgba = rgba;
        if (nativeId > 0) {
            UiNative.setVisual(nativeId, VISUAL_KEEP, rgba, -1, fontSizePt, textAlign);
        }
    }

    void setCornerRadius(int radius) {
        ensureAlive("Component.setCornerRadius");
        cornerRadius = radius;
        if (nativeId > 0) {
            UiNative.setVisual(nativeId, bgRgba, fgRgba, radius, -1, -1);
        }
    }

    void setFontSize(int pt) {
        ensureAlive("Component.setFontSize");
        fontSizePt = pt;
        if (nativeId > 0) {
            UiNative.setVisual(nativeId, VISUAL_KEEP, fgRgba, -1, pt, textAlign);
        }
    }

    void setTextAlign(int align) {
        ensureAlive("Component.setTextAlign");
        textAlign = align;
        if (nativeId > 0) {
            UiNative.setVisual(nativeId, VISUAL_KEEP, fgRgba, -1, fontSizePt, align);
        }
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
        if (nativeId > 0) {
            UiNative.focus(nativeId);
        }
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

    /** Register click listener; returned subscription cancels and clears the capture. */
    UiSubscription subscribeClick(Runnable listener) {
        ensureAlive("Component.subscribeClick");
        if (listener == null) {
            throw new NullPointerException("listener");
        }
        clickListener = listener;
        UiSubscription sub = new UiSubscription();
        sub.owner = this;
        sub.kind = UiSubscription.KIND_CLICK;
        sub.clickHandler = listener;
        liveSubscriptions.add(sub);
        return sub;
    }

    void onAction(EventHandler<ActionEvent> handler) {
        ensureAlive("Component.onAction");
        if (handler != null) {
            actionHandlers.add(handler);
        }
    }

    /**
     * Register an action handler and return a cancellable subscription.
     * Multiple subscribers run in registration order (snapshot at fire).
     */
    UiSubscription subscribeAction(EventHandler<ActionEvent> handler) {
        ensureAlive("Component.subscribeAction");
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        actionHandlers.add(handler);
        UiSubscription sub = new UiSubscription();
        sub.owner = this;
        sub.kind = UiSubscription.KIND_ACTION;
        sub.actionHandler = handler;
        liveSubscriptions.add(sub);
        return sub;
    }

    UiSubscription subscribeChange(EventHandler<UiEvent> handler) {
        ensureAlive("Component.subscribeChange");
        if (handler == null) {
            throw new NullPointerException("handler");
        }
        changeHandlers.add(handler);
        UiSubscription sub = new UiSubscription();
        sub.owner = this;
        sub.kind = UiSubscription.KIND_CHANGE;
        sub.changeHandler = handler;
        liveSubscriptions.add(sub);
        return sub;
    }

    void detachSubscription(UiSubscription sub) {
        if (sub == null || liveSubscriptions == null) {
            return;
        }
        int i = 0;
        while (i < liveSubscriptions.size()) {
            if (liveSubscriptions.get(i) == sub) {
                liveSubscriptions.removeAt(i);
                return;
            }
            i = i + 1;
        }
    }

    void removeActionHandler(EventHandler<ActionEvent> handler) {
        if (handler == null) {
            return;
        }
        int i = 0;
        while (i < actionHandlers.size()) {
            if (actionHandlers.get(i) == handler) {
                actionHandlers.removeAt(i);
                return;
            }
            i = i + 1;
        }
    }

    void removeChangeHandler(EventHandler<UiEvent> handler) {
        if (handler == null) {
            return;
        }
        int i = 0;
        while (i < changeHandlers.size()) {
            if (changeHandlers.get(i) == handler) {
                changeHandlers.removeAt(i);
                return;
            }
            i = i + 1;
        }
    }

    void clearClickListener(Runnable listener) {
        if (clickListener == listener) {
            clickListener = null;
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
        lastClicked = this;
        if (clickListener != null) {
            clickListener.run();
        }
        fireAction();
    }

    void setUserData(String data) {
        userData = data;
    }

    String getUserData() {
        return userData;
    }

    void fireAction() {
        if (disposed != 0) {
            return;
        }
        ActionEvent ev = new ActionEvent(this);
        ArrayList<EventHandler<ActionEvent>> snap = snapshotActionHandlers();
        int i = 0;
        while (i < snap.size()) {
            if (!ev.isConsumed()) {
                snap.get(i).handle(ev);
            }
            i = i + 1;
        }
    }

    ArrayList<EventHandler<ActionEvent>> snapshotActionHandlers() {
        ArrayList<EventHandler<ActionEvent>> snap = new ArrayList<EventHandler<ActionEvent>>();
        int n = actionHandlers.size();
        int i = 0;
        while (i < n) {
            snap.add(actionHandlers.get(i));
            i = i + 1;
        }
        return snap;
    }

    void fireChange() {
        if (disposed != 0) {
            return;
        }
        UiEvent ev = new UiEvent(this);
        ArrayList<EventHandler<UiEvent>> snap = new ArrayList<EventHandler<UiEvent>>();
        int n = changeHandlers.size();
        int i = 0;
        while (i < n) {
            snap.add(changeHandlers.get(i));
            i = i + 1;
        }
        i = 0;
        while (i < snap.size()) {
            snap.get(i).handle(ev);
            i = i + 1;
        }
    }

    void fireSelect(int index) {
    }

    /** Table column header click; arg is column index. Default no-op. */
    void fireHeader(int colIndex) {
    }

    void fireToggle(int selected) {
    }

    void fireKey(int downType, int keyCode, int mods) {
        if (disposed != 0) {
            return;
        }
        /* onKeyDown handlers only — ignore key-up (downType != 1). */
        if (downType != 1) {
            return;
        }
        KeyEvent ev = new KeyEvent(this, keyCode, mods, 1);
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
        FocusManager.noteFocus(this, gained);
        FocusEvent ev = new FocusEvent(this, gained);
        int n = focusHandlers.size();
        int i = 0;
        while (i < n) {
            focusHandlers.get(i).handle(ev);
            i = i + 1;
        }
    }

    void dispose() {
        disposeSelf("Component.dispose");
    }

    /**
     * Shared peer teardown (JNative has no {@code super.dispose()}).
     * Subclasses dispose children first, then call this.
     */
    void disposeSelf(String op) {
        Application.checkUiThread(op);
        if (disposed != 0) {
            return;
        }
        disposed = 1;
        // Cancel subscriptions first so each releases its closure refs (idempotent cancel).
        while (liveSubscriptions.size() > 0) {
            liveSubscriptions.get(0).cancel();
        }
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


    void setSpacing(int s) {
        ensureAlive("Container.setSpacing");
        if (s < 0) {
            throw new UiException("Container.setSpacing: spacing must be >= 0, got " + s);
        }
        spacing = s;
        requestLayout();
        layout();
    }

    void setPadding(int p) {
        ensureAlive("Container.setPadding");
        if (p < 0) {
            throw new UiException("Container.setPadding: padding must be >= 0, got " + p);
        }
        padding = p;
        requestLayout();
        layout();
    }

    void ensureChildCapacity(int need) {
        if (need <= children.length) {
            return;
        }
        int cap = children.length;
        while (cap < need) {
            cap = cap * 2;
        }
        Component[] grown = new Component[cap];
        int i = 0;
        while (i < childCount) {
            grown[i] = children[i];
            i = i + 1;
        }
        children = grown;
    }

    void add(Component child) {
        ensureAlive("Container.add");
        if (child == null) {
            return;
        }
        if (child.parent != null) {
            throw new UiException("component already has a parent; remove before reparenting");
        }
        ensureChildCapacity(childCount + 1);
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
            Component c = children[i];
            children[i] = null;
            if (c != null) {
                c.parent = null;
                c.dispose();
            }
            i = i + 1;
        }
        childCount = 0;
        if (disposed == 0) {
            layout();
        }
    }

    /**
     * Dispose children (native peers + subscriptions) before this container.
     * Idempotent; required so Window.close does not leak descendant handles.
     */
    void dispose() {
        Application.checkUiThread("Container.dispose");
        if (disposed != 0) {
            return;
        }
        int i = 0;
        while (i < childCount) {
            Component c = children[i];
            children[i] = null;
            if (c != null) {
                c.parent = null;
                c.dispose();
            }
            i = i + 1;
        }
        childCount = 0;
        disposeSelf("Container.dispose");
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
        /* Stretch only children that opt in via vgrow (avoids Cocoa Y-flip gaps
           when a GridPane/keypad is given leftover height it does not fill). */
        int stretchIndex = -1;
        int j = childCount - 1;
        while (j >= 0) {
            if (children[j].visible != 0) {
                if (children[j].vgrow != 0) {
                    stretchIndex = j;
                    j = -1;
                }
            }
            j = j - 1;
        }
        /* Commit this container frame before placing children (Cocoa Y-flip). */
        if (nativeId > 0) {
            UiNative.setBounds(nativeId, x, y, width, height);
        }
        while (i < childCount) {
            Component c = children[i];
            if (c.visible == 0) {
                c.setBounds(padding, cy, 0, 0);
            } else {
                int pw = c.preferredWidth();
                int ph = c.preferredHeight();
                if (pw < availW) {
                    pw = availW;
                }
                if (i == stretchIndex) {
                    int rest = 0;
                    int k = i + 1;
                    while (k < childCount) {
                        if (children[k].visible != 0) {
                            rest = rest + children[k].preferredHeight() + spacing;
                        }
                        k = k + 1;
                    }
                    int fill = height - cy - padding - rest;
                    if (fill > ph) {
                        ph = fill;
                    }
                    if (ph < 80) {
                        ph = 80;
                    }
                }
                c.setBounds(padding, cy, pw, ph);
                cy = cy + ph + spacing;
                if (pw > maxW) {
                    maxW = pw;
                }
            }
            i = i + 1;
        }
        int needW = maxW + padding * 2;
        int needH = cy + padding;
        prefWidth = needW;
        prefHeight = needH;
        if (width < needW) {
            width = needW;
        }
        /* When a child was stretched to fill, keep the parent-assigned height.
           Growing the VBox after children were Y-flipped against the old height
           leaves gaps above the status bar / below the toolbar. */
        if (stretchIndex < 0 && height < needH) {
            height = needH;
        }
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
        Component c = null;
        while (i < childCount) {
            c = children[i];
            int ph = c.preferredHeight();
            if (ph > maxH) {
                maxH = ph;
            }
            i = i + 1;
        }
        int needW = padding;
        i = 0;
        while (i < childCount) {
            needW = needW + children[i].preferredWidth() + spacing;
            i = i + 1;
        }
        if (childCount > 0) {
            needW = needW - spacing;
        }
        needW = needW + padding;
        int needH = maxH + padding * 2;
        /* Preserve thin chrome heights (toolbar/breadcrumb/status) when children
           are empty/shorter — but never lock in a parent-stretch leftover from
           incremental Container.add (that created the huge top whitespace). */
        if (needH < height && height > 0 && height <= 64) {
            needH = height;
        }
        prefWidth = needW;
        prefHeight = needH;
        /* Keep parent-allocated size when present to avoid Cocoa Y-flip gaps. */
        int layoutW = width;
        int layoutH = height;
        int noParent = 0;
        if (parent == null) {
            noParent = 1;
        }
        if (layoutW < needW) {
            if (noParent != 0) {
                layoutW = needW;
                width = needW;
            }
        }
        if (layoutH < needH) {
            if (noParent != 0) {
                layoutH = needH;
                height = needH;
            }
        }
        if (layoutW < 1) {
            layoutW = needW;
            width = needW;
        }
        if (layoutH < 1) {
            layoutH = needH;
            height = needH;
        }
        if (nativeId > 0) {
            UiNative.setBounds(nativeId, x, y, layoutW, layoutH);
        }
        int availH = layoutH - padding * 2;
        if (availH < 0) {
            availH = 0;
        }
        int flexCount = 0;
        int fixedW = padding * 2;
        i = 0;
        while (i < childCount) {
            c = children[i];
            if (c.visible != 0) {
                if (c.prefWidth == 0 || c.hgrow != 0) {
                    flexCount = flexCount + 1;
                } else {
                    fixedW = fixedW + c.preferredWidth();
                }
                if (i + 1 < childCount) {
                    fixedW = fixedW + spacing;
                }
            }
            i = i + 1;
        }
        int flexBudget = layoutW - fixedW;
        if (flexBudget < 0) {
            flexBudget = 0;
        }
        int flexEach = 0;
        if (flexCount > 0) {
            flexEach = flexBudget / flexCount;
        }
        cx = padding;
        i = 0;
        while (i < childCount) {
            c = children[i];
            if (c.visible == 0) {
                c.setBounds(cx, padding, 0, 0);
            } else {
                int pw = c.preferredWidth();
                if (c.prefWidth == 0 || c.hgrow != 0) {
                    pw = flexEach;
                    if (pw < 0) {
                        pw = 0;
                    }
                    if (pw < c.minWidth) {
                        pw = c.minWidth;
                    }
                }
                int ph2 = c.preferredHeight();
                if (ph2 > availH) {
                    if (availH > 0) {
                        ph2 = availH;
                    }
                }
                int yOff = padding;
                if (ph2 < availH) {
                    yOff = padding + (availH - ph2) / 2;
                }
                c.setBounds(cx, yOff, pw, ph2);
                cx = cx + pw + spacing;
            }
            i = i + 1;
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

    void ensureGridMetaCapacity(int need) {
        if (need <= cols.length) {
            return;
        }
        int cap = cols.length;
        while (cap < need) {
            cap = cap * 2;
        }
        int[] nc = new int[cap];
        int[] nr = new int[cap];
        int[] ncs = new int[cap];
        int[] nrs = new int[cap];
        int i = 0;
        while (i < childCount) {
            nc[i] = cols[i];
            nr[i] = rows[i];
            ncs[i] = colSpans[i];
            nrs[i] = rowSpans[i];
            i = i + 1;
        }
        cols = nc;
        rows = nr;
        colSpans = ncs;
        rowSpans = nrs;
    }

    void addAtSpan(Component child, int col, int row, int colSpan, int rowSpan) {
        ensureAlive("GridPane.addAt");
        int idx = childCount;
        ensureGridMetaCapacity(idx + 1);
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
        int colGaps = 0;
        if (gridCols > 0) {
            colGaps = (gridCols - 1) * spacing;
        }
        int rowGaps = 0;
        if (gridRows > 0) {
            rowGaps = (gridRows - 1) * spacing;
        }
        int naturalW = padding * 2 + gridCols * cellW + colGaps;
        int naturalH = padding * 2 + gridRows * cellH + rowGaps;
        prefWidth = naturalW;
        prefHeight = naturalH;
        /* Expand column width to fill parent; keep row height at cellH so
           leftover vertical space does not Y-flip keys to the bottom. */
        int layoutW = width;
        if (layoutW < naturalW) {
            layoutW = naturalW;
        }
        width = layoutW;
        height = naturalH;
        int useCellW = cellW;
        int useCellH = cellH;
        if (gridCols > 0) {
            int avail = layoutW - padding * 2 - colGaps;
            if (avail > gridCols * cellW) {
                useCellW = avail / gridCols;
            }
        }
        if (nativeId > 0) {
            UiNative.setBounds(nativeId, x, y, width, height);
        }
        int i = 0;
        while (i < childCount) {
            Component c = children[i];
            int cx = padding + cols[i] * (useCellW + spacing);
            int cy = padding + rows[i] * (useCellH + spacing);
            int cw = colSpans[i] * useCellW + (colSpans[i] - 1) * spacing;
            int ch = rowSpans[i] * useCellH + (rowSpans[i] - 1) * spacing;
            c.setBounds(cx, cy, cw, ch);
            i = i + 1;
        }
    }
}

/** Overlapping children. Later children paint above earlier ones. */
class StackPane extends Container {
    StackPane() {
        nativeId = UiNative.createPanel(0);
        UiHandleRegistry.put(nativeId, this);
        spacing = 0;
        padding = 0;
    }

    void layout() {
        layoutDirty = 0;
        UiMetrics.layoutPasses = UiMetrics.layoutPasses + 1;
        if (nativeId > 0) {
            UiNative.setBounds(nativeId, x, y, width, height);
        }
        int i = 0;
        while (i < childCount) {
            Component c = children[i];
            if (c.visible == 0) {
                c.setBounds(0, 0, 0, 0);
            } else {
                int cw = width;
                int ch = height;
                int cx = 0;
                int cy = 0;
                if (c.stackPlacement == 1) {
                    cw = c.prefWidth;
                    if (cw < 1) {
                        cw = 280;
                    }
                    if (cw > width) {
                        cw = width;
                    }
                    cx = width - cw;
                } else {
                    if (c.stackPlacement == 2) {
                        cw = c.prefWidth;
                        if (cw < 1) {
                            cw = 280;
                        }
                        if (cw > width) {
                            cw = width;
                        }
                        cx = 0;
                    } else {
                        /* Fill width; height = preferred (top-aligned) when set. */
                        int ph = c.preferredHeight();
                        if (ph > 0 && ph < height) {
                            ch = ph;
                        }
                    }
                }
                c.setBounds(cx, cy, cw, ch);
            }
            i = i + 1;
        }
        prefWidth = width;
        prefHeight = height;
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
        if (pct < 5) {
            pct = 50;
        }
        if (pct > 95) {
            pct = 95;
        }
        if (orientation == Orientation.HORIZONTAL) {
            int leftW = width * pct / 100;
            if (leftW < 80) {
                leftW = 80;
            }
            if (leftW > width - 120) {
                leftW = width - 120;
            }
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

    void setDisplayStyle(int fontPt, int align, int fg) {
        ensureAlive("Label.setDisplayStyle");
        setPrefSize(prefWidth, fontPt + 12);
        setVisual(VISUAL_KEEP, fg, -1, fontPt, align);
    }
}

class Button extends Control {
    int chromeStyle;
    int filledStyle;

    Button(String t) {
        text = t;
        chromeStyle = 0;
        filledStyle = 0;
        /* Slot size for layout; Cocoa keeps intrinsic bezel via fittingSize. */
        prefHeight = 28;
        height = 28;
        prefWidth = buttonPrefWidth(t);
        width = prefWidth;
        nativeId = UiNative.createButton(0, text);
        UiHandleRegistry.put(nativeId, this);
        UiNative.setText(nativeId, text);
    }

    /** Filled rounded button (honor layout cell size; hover/pressed via peer). */
    void setFilledStyle(int bg, int fg, int radius, int fontPt) {
        ensureAlive("Button.setFilledStyle");
        filledStyle = 1;
        setVisual(bg, fg, radius, fontPt, 1);
    }

    void flashPressed(int on) {
        ensureAlive("Button.flashPressed");
        if (nativeId > 0 && filledStyle != 0) {
            UiNative.buttonFlash(nativeId, on);
        }
    }

    /** Content-sized width — avoid identical oversized gray pills. */
    static int buttonPrefWidth(String t) {
        int n = 0;
        if (t != null) {
            n = t.length();
        }
        int w = 36 + n * 9;
        if (w < 64) {
            w = 64;
        }
        if (w > 220) {
            w = 220;
        }
        return w;
    }

    /**
     * Chrome styles: 0=default push, 1=toolbar glyph, 2=breadcrumb, 3=toolbar push.
     */
    void setChromeStyle(int style) {
        ensureAlive("Button.setChromeStyle");
        chromeStyle = style;
        if (style == 1) {
            int n = 0;
            if (text != null) {
                n = text.length();
            }
            if (n <= 2) {
                prefWidth = 30;
                width = 30;
            } else {
                prefWidth = buttonPrefWidth(text);
                width = prefWidth;
            }
            prefHeight = 28;
            height = 28;
        } else if (style == 2) {
            prefHeight = 22;
            height = 22;
            int n = 0;
            if (text != null) {
                n = text.length();
            }
            prefWidth = 12 + n * 8;
            if (prefWidth < 18) {
                prefWidth = 18;
            }
            if (prefWidth > 160) {
                prefWidth = 160;
            }
            width = prefWidth;
        } else if (style == 3) {
            prefWidth = buttonPrefWidth(text) + 18;
            if (prefWidth < 72) {
                prefWidth = 72;
            }
            width = prefWidth;
            prefHeight = 28;
            height = 28;
        }
        if (nativeId > 0) {
            UiNative.setValueI32(nativeId, style);
        }
        requestLayout();
    }

    void setSystemSymbol(String symbolName) {
        ensureAlive("Button.setSystemSymbol");
        if (symbolName == null || symbolName.length() == 0) {
            return;
        }
        if (nativeId > 0) {
            UiNative.setButtonSymbol(nativeId, symbolName);
        }
    }

    void setText(String t) {
        ensureAlive("Button.setText");
        if (t == null) {
            t = "";
        }
        text = t;
        if (chromeStyle == 1) {
            int n = text.length();
            if (n <= 2) {
                prefWidth = 34;
                width = 34;
            } else {
                prefWidth = buttonPrefWidth(text);
                width = prefWidth;
            }
        } else if (chromeStyle == 3) {
            prefWidth = buttonPrefWidth(text) + 18;
            if (prefWidth < 72) {
                prefWidth = 72;
            }
            width = prefWidth;
        } else {
            prefWidth = buttonPrefWidth(text);
            width = prefWidth;
        }
        if (nativeId > 0) {
            UiNative.setText(nativeId, text);
            if (chromeStyle != 0) {
                UiNative.setValueI32(nativeId, chromeStyle);
            }
        }
        requestLayout();
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

    /** Pull peer text after native edit without rewriting the control (caret-safe). */
    void syncTextFromNative() {
        if (nativeId <= 0) {
            return;
        }
        NativePtr p = UiNative.copyText(nativeId);
        if (p == NativePtr.NULL) {
            return;
        }
        String t = NativeMemory.fromCString(p);
        UiNative.dialogFreePath(p);
        if (t == null) {
            t = "";
        }
        text = t;
    }

    /**
     * Set text only when different from current value — avoids caret jumps
     * on every model notify during two-way binding.
     */
    void setTextIfChanged(String t) {
        ensureAlive("TextField.setTextIfChanged");
        if (t == null) {
            t = "";
        }
        if (text != null && text.equals(t)) {
            return;
        }
        setText(t);
    }

    void fireChange() {
        if (disposed != 0) {
            return;
        }
        syncTextFromNative();
        UiEvent ev = new UiEvent(this);
        ArrayList<EventHandler<UiEvent>> snap = new ArrayList<EventHandler<UiEvent>>();
        int n = changeHandlers.size();
        int i = 0;
        while (i < n) {
            snap.add(changeHandlers.get(i));
            i = i + 1;
        }
        i = 0;
        while (i < snap.size()) {
            if (!ev.isConsumed()) {
                snap.get(i).handle(ev);
            }
            i = i + 1;
        }
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

/**
 * Native segmented control (CPU / Memory). Titles are tab-separated at create.
 * Selection fires Component.fireSelect → override in subclass or use onSelect.
 */
class SegmentedControl extends Control {
    ArrayList<EventHandler<SelectionEvent<int>>> selectHandlers;
    int selectedIndex;
    int segmentCount;

    SegmentedControl(String titlesTsv) {
        if (titlesTsv == null) {
            titlesTsv = "A\tB";
        }
        text = titlesTsv;
        selectedIndex = 0;
        segmentCount = 1;
        int i = 0;
        while (i < titlesTsv.length()) {
            if (titlesTsv.charAt(i) == 9) {
                segmentCount = segmentCount + 1;
            }
            i = i + 1;
        }
        selectHandlers = new ArrayList<EventHandler<SelectionEvent<int>>>();
        prefWidth = 36 + segmentCount * 52;
        width = prefWidth;
        prefHeight = 28;
        height = 28;
        nativeId = UiNative.createControl(20, 0, titlesTsv);
        UiHandleRegistry.put(nativeId, this);
    }

    void setSelectedIndex(int index) {
        ensureAlive("SegmentedControl.setSelectedIndex");
        if (index < 0) {
            index = 0;
        }
        if (index >= segmentCount) {
            index = segmentCount - 1;
        }
        selectedIndex = index;
        if (nativeId > 0) {
            UiNative.setSelectedIndex(nativeId, index);
        }
    }

    int getSelectedIndex() {
        return selectedIndex;
    }

    void onSelectionChanged(EventHandler<SelectionEvent<int>> handler) {
        if (handler != null) {
            selectHandlers.add(handler);
        }
    }

    void fireSelect(int index) {
        selectedIndex = index;
        SelectionEvent<int> ev = new SelectionEvent<int>(this, index, index);
        int i = 0;
        while (i < selectHandlers.size()) {
            selectHandlers.get(i).handle(ev);
            i = i + 1;
        }
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
        /* Cast via Object — jnative forbids (String) on bare type param T. */
        Object box = value;
        if (box instanceof String) {
            return (String) box;
        }
        /* Non-String items need an explicit CellRenderer; do not invent false labels. */
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
        UiNative.listClear(nativeId);
        int i = 0;
        while (i < n) {
            T v = model.get(i);
            UiNative.listAddItem(nativeId, renderItem(v));
            i = i + 1;
        }
        UiNative.setItemCount(nativeId, n);
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
        if (disposed != 0) {
            return;
        }
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
        /* Use ColumnRenderer for typed String cells (Function path is limited). */
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

/** Optional per-row absolute path appended to TSV for native file icons. */
interface TableIconPathProvider<T> {
    String iconPath(T row);
}

interface TableRowIdProvider<T> {
    String rowId(T row);
}

/** Column with explicit width (logical pt), align L/C/R, and flex grow. */
class TableColumnDef<T> implements ColumnRenderer<T> {
    String title;
    ColumnRenderer<T> inner;
    int widthPt;
    String align;
    int flex;

    TableColumnDef(String t, ColumnRenderer<T> body, int width, String al, int fl) {
        title = t;
        inner = body;
        widthPt = width;
        if (al == null || al.length() == 0) {
            align = "L";
        } else {
            align = al;
        }
        flex = fl;
    }

    String getTitle() {
        /* Encode meta for Cocoa: title##width##align##flex (ASCII-safe). */
        String w = String.valueOf(widthPt);
        String f = String.valueOf(flex);
        return title + "##" + w + "##" + align + "##" + f;
    }

    String renderRow(T row) {
        if (inner == null) {
            return "";
        }
        return inner.renderRow(row);
    }
}

class TableView<T> extends Control {
    ListModel<T> model;
    ArrayList<ColumnRenderer<T>> columns;
    SelectionModel<T> selection;
    ArrayList<EventHandler<SelectionEvent<T>>> selectionHandlers;
    ArrayList<EventHandler<SelectionEvent<T>>> headerHandlers;
    ArrayList<EventHandler<ActionEvent>> doubleClickHandlers;
    ArrayList<int> sortedIndex;
    int sortColumn;
    int sortAsc;
    int visibleRows;
    TableIconPathProvider<T> iconPaths;
    TableRowIdProvider<T> rowIds;
    String selectedRowId;
    String emptyMessage;
    String loadingMessage;
    int loading;
    int preserveScroll;

    TableView() {
        model = null;
        columns = new ArrayList<ColumnRenderer<T>>();
        selection = new SelectionModel<T>();
        selectionHandlers = new ArrayList<EventHandler<SelectionEvent<T>>>();
        headerHandlers = new ArrayList<EventHandler<SelectionEvent<T>>>();
        doubleClickHandlers = new ArrayList<EventHandler<ActionEvent>>();
        sortedIndex = new ArrayList<int>();
        sortColumn = -1;
        sortAsc = 1;
        visibleRows = 30;
        iconPaths = null;
        rowIds = null;
        selectedRowId = null;
        emptyMessage = "";
        loadingMessage = "";
        loading = 0;
        preserveScroll = 1;
        width = 500;
        height = 300;
        prefWidth = 500;
        prefHeight = 300;
        nativeId = UiNative.createControl(15, 0, "");
        UiHandleRegistry.put(nativeId, this);
        UiNative.setVisibleRows(nativeId, visibleRows);
    }

    void onHeaderClick(EventHandler<SelectionEvent<T>> handler) {
        if (handler != null) {
            headerHandlers.add(handler);
        }
    }

    void fireHeader(int colIndex) {
        SelectionEvent<T> ev = new SelectionEvent<T>(this, colIndex, null);
        int i = 0;
        while (i < headerHandlers.size()) {
            headerHandlers.get(i).handle(ev);
            i = i + 1;
        }
    }

    void setIconPathProvider(TableIconPathProvider<T> provider) {
        ensureAlive("TableView.setIconPathProvider");
        iconPaths = provider;
        syncRowsNative();
    }

    void addColumn(ColumnRenderer<T> col) {
        ensureAlive("TableView.addColumn");
        columns.add(col);
        syncColumnsNative();
    }

    void addColumnDef(String title, ColumnRenderer<T> body, int widthPt, String align, int flex) {
        addColumn(new TableColumnDef<T>(title, body, widthPt, align, flex));
    }

    void setRowIdProvider(TableRowIdProvider<T> provider) {
        ensureAlive("TableView.setRowIdProvider");
        rowIds = provider;
    }

    void setEmptyMessage(String msg) {
        ensureAlive("TableView.setEmptyMessage");
        if (msg == null) {
            msg = "";
        }
        emptyMessage = msg;
    }

    void setLoading(boolean on, String msg) {
        ensureAlive("TableView.setLoading");
        if (on) {
            loading = 1;
        } else {
            loading = 0;
        }
        if (msg == null) {
            msg = "";
        }
        loadingMessage = msg;
        syncRowsNative();
    }

    void onDoubleClick(EventHandler<ActionEvent> handler) {
        if (handler != null) {
            doubleClickHandlers.add(handler);
        }
    }

    String rowIdOf(T item) {
        if (item == null) {
            return null;
        }
        if (rowIds != null) {
            return rowIds.rowId(item);
        }
        return null;
    }

    String getSelectedRowId() {
        return selectedRowId;
    }

    int findViewIndexByRowId(String id) {
        if (id == null || model == null || rowIds == null) {
            return -1;
        }
        int i = 0;
        while (i < sortedIndex.size()) {
            int mi = sortedIndex.get(i);
            if (mi >= 0 && mi < model.size()) {
                String rid = rowIds.rowId(model.get(mi));
                if (rid != null && rid.equals(id)) {
                    return i;
                }
            }
            i = i + 1;
        }
        return -1;
    }

    void selectByRowId(String id) {
        ensureAlive("TableView.selectByRowId");
        int v = findViewIndexByRowId(id);
        if (v >= 0) {
            select(v);
        }
    }

    void syncColumnsNative() {
        if (nativeId <= 0) {
            return;
        }
        String titles = "";
        int i = 0;
        while (i < columns.size()) {
            if (i > 0) {
                titles = titles + "\t";
            }
            String t = columns.get(i).getTitle();
            if (t == null) {
                t = "";
            }
            titles = titles + t;
            i = i + 1;
        }
        if (titles.length() > 0) {
            UiNative.tableSetColumns(nativeId, titles);
        }
    }

    void setItems(ArrayList<T> items) {
        setModel(new ArrayListListModel<T>(items));
    }

    void setModel(ListModel<T> m) {
        ensureAlive("TableView.setModel");
        model = m;
        rebuildIndex();
        syncRowsNative();
    }

    void syncRowsNative() {
        if (nativeId <= 0) {
            return;
        }
        int scrollY = -1;
        if (preserveScroll != 0) {
            scrollY = UiNative.tableGetScrollY(nativeId);
        }
        /* Capture selection identity before rebuild (never trust row index alone). */
        if (selection != null && selection.selectedItem != null && rowIds != null) {
            String rid = rowIds.rowId(selection.selectedItem);
            if (rid != null) {
                selectedRowId = rid;
            }
        }
        UiNative.listClear(nativeId);
        if (loading != 0) {
            String m = loadingMessage;
            if (m == null || m.length() == 0) {
                m = "…";
            }
            UiNative.listAddItem(nativeId, m);
            UiNative.setItemCount(nativeId, 1);
        } else if (sortedIndex.size() == 0) {
            if (emptyMessage != null && emptyMessage.length() > 0) {
                UiNative.listAddItem(nativeId, emptyMessage);
                UiNative.setItemCount(nativeId, 1);
            } else {
                UiNative.setItemCount(nativeId, 0);
            }
        } else {
            int n = sortedIndex.size();
            int i = 0;
            while (i < n) {
                int modelIndex = sortedIndex.get(i);
                UiNative.listAddItem(nativeId, renderRowTsv(modelIndex));
                i = i + 1;
            }
            UiNative.setItemCount(nativeId, n);
        }
        if (scrollY >= 0) {
            UiNative.tableSetScrollY(nativeId, scrollY);
        }
        if (selectedRowId != null && loading == 0 && sortedIndex.size() > 0) {
            int v = findViewIndexByRowId(selectedRowId);
            if (v >= 0) {
                UiNative.setSelectedIndex(nativeId, v);
                int mi = sortedIndex.get(v);
                T item = model.get(mi);
                selection.select(mi, item);
            } else {
                selectedRowId = null;
                selection.clearSelection();
            }
        }
    }

    String renderRowTsv(int modelIndex) {
        if (model == null || modelIndex < 0 || modelIndex >= model.size()) {
            return "";
        }
        if (columns.size() == 0) {
            return "?";
        }
        String row = "";
        int c = 0;
        while (c < columns.size()) {
            if (c > 0) {
                row = row + "\t";
            }
            String cell = cellText(modelIndex, c);
            if (cell == null) {
                cell = "";
            }
            row = row + cell;
            c = c + 1;
        }
        if (iconPaths != null) {
            T item = model.get(modelIndex);
            String path = iconPaths.iconPath(item);
            if (path != null && path.length() > 0) {
                row = row + "\t" + path;
            }
        }
        return row;
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
        syncRowsNative();
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
        fireSelect(viewIndex);
    }

    void onSelectionChanged(EventHandler<SelectionEvent<T>> handler) {
        if (handler != null) {
            selectionHandlers.add(handler);
        }
    }

    void fireSelect(int viewIndex) {
        if (disposed != 0) {
            return;
        }
        T item = null;
        int modelIndex = viewIndex;
        if (viewIndex >= 0 && viewIndex < sortedIndex.size()) {
            modelIndex = sortedIndex.get(viewIndex);
            if (model != null && modelIndex >= 0 && modelIndex < model.size()) {
                item = model.get(modelIndex);
                selection.select(modelIndex, item);
                if (rowIds != null) {
                    selectedRowId = rowIds.rowId(item);
                }
            }
        } else {
            selectedRowId = null;
        }
        SelectionEvent<T> ev = new SelectionEvent<T>(this, modelIndex, item);
        int i = 0;
        while (i < selectionHandlers.size()) {
            selectionHandlers.get(i).handle(ev);
            i = i + 1;
        }
    }

    void fireAction() {
        if (disposed != 0) {
            return;
        }
        ActionEvent ev = new ActionEvent(this);
        ArrayList<EventHandler<ActionEvent>> snap = snapshotActionHandlers();
        int i = 0;
        while (i < snap.size()) {
            if (!ev.isConsumed()) {
                snap.get(i).handle(ev);
            }
            i = i + 1;
        }
        i = 0;
        while (i < doubleClickHandlers.size()) {
            if (!ev.isConsumed()) {
                doubleClickHandlers.get(i).handle(ev);
            }
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
    int checked;
    Command boundCommand;

    MenuItem(String t) {
        text = t;
        action = null;
        shortcut = null;
        isSeparator = 0;
        checked = 0;
        boundCommand = null;
        nativeId = UiNative.menuCreateItem(t);
        UiHandleRegistry.put(nativeId, this);
    }

    MenuItem(String t, Runnable act) {
        text = t;
        action = act;
        shortcut = null;
        isSeparator = 0;
        checked = 0;
        boundCommand = null;
        nativeId = UiNative.menuCreateItem(t);
        UiHandleRegistry.put(nativeId, this);
    }

    void setAction(Runnable act) {
        action = act;
    }

    void setShortcut(KeyCombination kc) {
        shortcut = kc;
        if (kc == null || nativeId <= 0) {
            return;
        }
        int mods = 0;
        if (kc.shift != 0) {
            mods = mods + 1;
        }
        if (kc.alt != 0) {
            mods = mods + 2;
        }
        if (kc.ctrl != 0 && kc.shortcut == 0) {
            mods = mods + 4;
        }
        if (kc.meta != 0 || kc.shortcut != 0) {
            /* withPrimary / withShortcut → Command on macOS */
            mods = mods + 8;
        }
        int key = kc.keyCode;
        if (key >= 97 && key <= 122) {
            key = key - 32;
        }
        UiNative.menuSetShortcut(nativeId, key, mods);
    }

    void setChecked(boolean on) {
        if (on) {
            checked = 1;
        } else {
            checked = 0;
        }
        if (nativeId > 0) {
            UiNative.setSelected(nativeId, checked);
        }
    }

    boolean isChecked() {
        return checked != 0;
    }

    void fireAction() {
        if (disposed != 0) {
            return;
        }
        if (boundCommand != null) {
            boundCommand.execute();
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

    /** Show at screen logical coordinates (top-left origin). macOS: native popup. */
    int showAt(int screenX, int screenY) {
        ensureAlive("ContextMenu.showAt");
        return UiNative.contextMenuShow(nativeId, screenX, screenY);
    }
}

class Dialogs {
    static void beginModal(Window owner) {
        if (owner != null) {
            owner.rememberFocus();
        }
    }

    static void endModal(Window owner) {
        if (owner != null) {
            owner.restoreFocusAfterDialog();
        }
    }

    static void info(Window owner, String message) {
        Application.checkUiThread("Dialogs.info");
        beginModal(owner);
        int wid = 0;
        if (owner != null) {
            wid = owner.nativeId;
        }
        UiNative.dialogMessage(wid, 0, "Info", message);
        endModal(owner);
    }

    static boolean confirm(Window owner, String message) {
        Application.checkUiThread("Dialogs.confirm");
        beginModal(owner);
        int wid = 0;
        if (owner != null) {
            wid = owner.nativeId;
        }
        int r = UiNative.dialogMessage(wid, 1, "Confirm", message);
        endModal(owner);
        return r != 0;
    }

    static void error(Window owner, String message) {
        Application.checkUiThread("Dialogs.error");
        beginModal(owner);
        int wid = 0;
        if (owner != null) {
            wid = owner.nativeId;
        }
        UiNative.dialogMessage(wid, 2, "Error", message);
        endModal(owner);
    }

    /**
     * Save / Don't Save / Cancel.
     * @return 1 Save, 2 Don't Save, 0 Cancel
     */
    static int askSave(Window owner, String message) {
        Application.checkUiThread("Dialogs.askSave");
        beginModal(owner);
        int wid = 0;
        if (owner != null) {
            wid = owner.nativeId;
        }
        int r = UiNative.dialogMessage(wid, 3, "Save changes?", message);
        endModal(owner);
        return r;
    }

    /**
     * Terminate choose sheet: 1 = SIGTERM, 2 = SIGKILL, 0 = cancel.
     */
    static int terminateChoose(Window owner, String title, String message) {
        Application.checkUiThread("Dialogs.terminateChoose");
        int wid = 0;
        if (owner != null) {
            wid = owner.nativeId;
        }
        String t = title;
        if (t == null || t.length() == 0) {
            t = "结束进程";
        }
        return UiNative.dialogMessage(wid, 4, t, message);
    }

    /**
     * Simple prompt foundation — confirms suggested value (full text dialog TBD).
     * Returns null if cancelled.
     */
    static String prompt(Window owner, String title, String suggested) {
        Application.checkUiThread("Dialogs.prompt");
        String msg = title + "\n\nSuggested: " + suggested + "\n\nOK = use suggested name";
        if (!confirm(owner, msg)) {
            return null;
        }
        return suggested;
    }
}

class FileDialog {
    /**
     * Native open panel. Returns null if the user cancels (not an error).
     * @param filter extension hint, e.g. "json" or "json,txt"
     */
    static String open(Window owner, String filter) {
        Application.checkUiThread("FileDialog.open");
        int wid = 0;
        if (owner != null) {
            wid = owner.nativeId;
        }
        if (filter == null) {
            filter = "";
        }
        NativePtr p = UiNative.dialogFileOpen(wid, filter);
        if (p == NativePtr.NULL) {
            return null;
        }
        String path = NativeMemory.fromCString(p);
        UiNative.dialogFreePath(p);
        return path;
    }

    /**
     * Native save panel. Returns null on cancel. Overwrite confirmation is native (macOS).
     * Paths with Chinese characters and spaces are supported via UTF-8.
     */
    static String save(Window owner, String filter) {
        Application.checkUiThread("FileDialog.save");
        int wid = 0;
        if (owner != null) {
            wid = owner.nativeId;
        }
        if (filter == null) {
            filter = "";
        }
        NativePtr p = UiNative.dialogFileSave(wid, filter);
        if (p == NativePtr.NULL) {
            return null;
        }
        String path = NativeMemory.fromCString(p);
        UiNative.dialogFreePath(p);
        return path;
    }

    /**
     * Safe document write: write to {@code path + ".tmp"} then replace {@code path}.
     * Prefer {@code Files.writeAtomicString} when the Io stdlib is linked (fsync + rename).
     * Returns 1 on success, 0 on failure. Cancelled dialogs never call this.
     */
    static int writeTextSafe(String path, String text) {
        Application.checkUiThread("FileDialog.writeTextSafe");
        if (path == null || path.length() == 0) {
            return 0;
        }
        if (text == null) {
            text = "";
        }
        String tmp = path + ".jn-tmp";
        try {
            File.writeString(tmp, text);
            File.writeString(path, text);
            if (File.exists(tmp)) {
                File.delete(tmp);
            }
            return 1;
        } catch (Exception ex) {
            System.out.println("Safe write failed: " + ex.getMessage());
            return 0;
        }
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
    int closeVeto;
    int role;
    Window ownerWindow;
    ArrayList<Window> childWindows;
    Button defaultButton;
    Button cancelButton;
    Component focusRestore;

    /** Primary document/main window (default). */
    static int ROLE_PRIMARY = 0;
    /** Auxiliary / detail window; closed with its owner primary. */
    static int ROLE_AUXILIARY = 1;

    Window(String t, int w, int h) {
        title = t;
        width = w;
        height = h;
        shown = 0;
        closeVeto = 0;
        role = ROLE_PRIMARY;
        ownerWindow = null;
        childWindows = new ArrayList<Window>();
        defaultButton = null;
        cancelButton = null;
        focusRestore = null;
        nativeId = UiNative.createWindow(title, width, height);
        if (nativeId <= 0) {
            throw new UiNativeException("failed to create window");
        }
        UiHandleRegistry.put(nativeId, this);
        Application.onWindowOpened(this);
        /* Escape → cancel; Return on non-text is handled by Cocoa default button cell. */
        onKeyDown((ev) -> {
            if (ev == null || disposed != 0) {
                return;
            }
            if (ev.keyCode == KeyCode.ESCAPE && cancelButton != null
                    && cancelButton.disposed == 0 && cancelButton.enabled != 0) {
                cancelButton.fireClick();
            }
        });
    }

    void setDefaultButton(Button b) {
        ensureAlive("Window.setDefaultButton");
        defaultButton = b;
        int bid = 0;
        if (b != null) {
            bid = b.nativeId;
        }
        UiNative.windowSetDefaultButton(nativeId, bid);
    }

    void setCancelButton(Button b) {
        ensureAlive("Window.setCancelButton");
        cancelButton = b;
        int bid = 0;
        if (b != null) {
            bid = b.nativeId;
        }
        UiNative.windowSetCancelButton(nativeId, bid);
    }

    /** Remember focus before a modal dialog; call restoreFocusAfterDialog after. */
    void rememberFocus() {
        focusRestore = FocusManager.focused;
    }

    void restoreFocusAfterDialog() {
        if (focusRestore != null && focusRestore.disposed == 0 && focusRestore.enabled != 0) {
            focusRestore.requestFocus();
        }
        focusRestore = null;
    }

    void setRole(int r) {
        role = r;
    }

    void setOwnerWindow(Window primary) {
        ownerWindow = primary;
        role = ROLE_AUXILIARY;
        if (primary != null) {
            primary.childWindows.add(this);
        }
    }

    void activate() {
        ensureAlive("Window.activate");
        Application.activeWindow = this;
        UiNative.show(nativeId);
        UiNative.focus(nativeId);
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

    void setMinSize(int w, int h) {
        ensureAlive("Window.setMinSize");
        if (w < 0 || h < 0) {
            throw new UiException("Window.setMinSize: width and height must be >= 0");
        }
        minWidth = w;
        minHeight = h;
        if (nativeId > 0) {
            UiNative.windowSetMinSize(nativeId, w, h);
        }
    }

    void setContentMinSize(int w, int h) {
        ensureAlive("Window.setContentMinSize");
        setMinSize(w, h);
    }

    void onNativeResize(int w, int h) {
        /* Ignore transient zero/tiny sizes during window realization. */
        if (w < 200 || h < 120) {
            return;
        }
        width = w;
        height = h;
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
        Application.activeWindow = this;
        UiNative.show(nativeId);
        if (content != null) {
            content.setBounds(0, 0, width, height);
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

    void vetoClose() {
        closeVeto = 1;
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
        closeVeto = 0;
        if (closeListener != null) {
            closeListener.run();
        }
        /* While Application.requestQuit is in progress, veto is ignored (confirm first). */
        if (closeVeto != 0 && Application.quitting == 0) {
            return;
        }
        closeVeto = 0;
        /* Close owned auxiliary windows first (no re-entrancy into this close). */
        if (childWindows != null && childWindows.size() > 0) {
            ArrayList<Window> kids = new ArrayList<Window>();
            int ki = 0;
            while (ki < childWindows.size()) {
                kids.add(childWindows.get(ki));
                ki = ki + 1;
            }
            childWindows.clear();
            ki = 0;
            while (ki < kids.size()) {
                Window ch = kids.get(ki);
                if (ch != null && ch.disposed == 0) {
                    ch.ownerWindow = null;
                    ch.close();
                }
                ki = ki + 1;
            }
        }
        if (ownerWindow != null && ownerWindow.childWindows != null) {
            int oi = 0;
            while (oi < ownerWindow.childWindows.size()) {
                if (ownerWindow.childWindows.get(oi) == this) {
                    ownerWindow.childWindows.removeAt(oi);
                    break;
                }
                oi = oi + 1;
            }
            ownerWindow = null;
        }
        if (content != null) {
            content.dispose();
            content = null;
        }
        /* Window-level subscriptions / key handlers (not cleared by content.dispose). */
        while (liveSubscriptions.size() > 0) {
            liveSubscriptions.get(0).cancel();
        }
        closeListener = null;
        keyHandlers.clear();
        actionHandlers.clear();
        focusHandlers.clear();
        int id = nativeId;
        UiHandleRegistry.remove(id);
        nativeId = 0;
        disposed = 1;
        shown = 0;
        menuBar = null;
        defaultButton = null;
        cancelButton = null;
        focusRestore = null;
        if (id > 0) {
            UiNative.closeWindow(id);
        }
        Application.onWindowClosed(this);
    }
}


class Theme {
    static int SPACE_1 = 4;
    static int SPACE_2 = 8;
    static int SPACE_3 = 12;
    static int SPACE_4 = 16;
    static int SPACE_5 = 24;

    /** 1=yes, 0=no, -1=unknown / headless. */
    static int prefersReducedMotion() {
        return UiNative.prefersReducedMotion();
    }

    /** 1=yes, 0=no, -1=unknown / headless. Charts should stay readable without relying on motion. */
    static int prefersIncreasedContrast() {
        return UiNative.prefersIncreasedContrast();
    }
    static int TOOLBAR_HEIGHT = 36;
    static int STATUS_HEIGHT = 24;
    static int SIDEBAR_ITEM_HEIGHT = 30;
    static int TABLE_ROW_HEIGHT = 28;
    static int MODE_SYSTEM = 0;
    static int MODE_LIGHT = 1;
    static int MODE_DARK = 2;
    static int appearanceMode = 0;
    static String BACKGROUND = "background";
    static String SURFACE = "surface";
    static String TEXT_PRIMARY = "textPrimary";
    static String ACCENT = "accent";
    static String SELECTION = "selection";
    static void setAppearanceMode(int mode) {
        appearanceMode = mode;
        UiNative.setAppearanceModeNative(mode);
    }
    static int getAppearanceMode() { return appearanceMode; }
    static int isDarkEffective() {
        if (appearanceMode == MODE_DARK) { return 1; }
        if (appearanceMode == MODE_LIGHT) { return 0; }
        int sys = UiNative.systemDark();
        if (sys == 1) { return 1; }
        return 0;
    }

    static int STYLE_BODY = 0;
    static int STYLE_SECONDARY = 1;
    static int STYLE_TITLE = 2;
    static int STYLE_ERROR = 3;
    static int STYLE_DISABLED = 4;
    static int STYLE_SELECTED = 5;

    /** Resolve semantic color as 0xAARRGGBB for the effective appearance. */
    static int colorOf(String token) {
        int dark = isDarkEffective();
        if (token == null) {
            token = "";
        }
        if (token.equals(BACKGROUND) || token.equals("systemBackground")) {
            if (dark != 0) {
                return Component.rgba(30, 30, 30, 255);
            }
            return Component.rgba(246, 246, 246, 255);
        }
        if (token.equals(SURFACE) || token.equals("separator")) {
            if (dark != 0) {
                return Component.rgba(56, 56, 58, 255);
            }
            return Component.rgba(210, 210, 214, 255);
        }
        if (token.equals(TEXT_PRIMARY) || token.equals("label")) {
            if (dark != 0) {
                return Component.rgba(255, 255, 255, 255);
            }
            return Component.rgba(0, 0, 0, 255);
        }
        if (token.equals("textSecondary") || token.equals("secondaryLabel")) {
            if (dark != 0) {
                return Component.rgba(180, 180, 185, 255);
            }
            return Component.rgba(110, 110, 115, 255);
        }
        if (token.equals(ACCENT) || token.equals("accent")) {
            return Component.rgba(0, 122, 255, 255);
        }
        if (token.equals(SELECTION)) {
            return Component.rgba(0, 122, 255, 80);
        }
        if (token.equals("error")) {
            return Component.rgba(255, 69, 58, 255);
        }
        return Component.rgba(0, 0, 0, 255);
    }

    /** Apply semantic typography / color without hard-coded font family names. */
    static void applyStyle(Component c, int style) {
        if (c == null) {
            return;
        }
        int fg = colorOf(TEXT_PRIMARY);
        int pt = 13;
        if (style == STYLE_SECONDARY) {
            fg = colorOf("textSecondary");
            pt = 12;
        } else if (style == STYLE_TITLE) {
            fg = colorOf(TEXT_PRIMARY);
            pt = 15;
        } else if (style == STYLE_ERROR) {
            fg = colorOf("error");
            pt = 13;
        } else if (style == STYLE_DISABLED) {
            fg = colorOf("textSecondary");
            pt = 13;
            c.setEnabled(false);
        } else if (style == STYLE_SELECTED) {
            fg = colorOf(ACCENT);
            pt = 13;
        }
        c.setVisual(Component.VISUAL_KEEP, fg, -1, pt, -1);
    }
}
class UiThread {
    static void assertCurrent() { Application.checkUiThread("UiThread.assertCurrent"); }
    static void assertNotCurrent() {
        long tid = Thread.currentThread().id();
        if (tid == Application.uiThreadId) {
            throw new UiThreadViolationException("operation must not run on application thread");
        }
    }
    static int isCurrent() {
        if (Thread.currentThread().id() == Application.uiThreadId) { return 1; }
        return 0;
    }
    static void runLater(Runnable r) { Application.invokeLater(r); }
    /** High-frequency refresh merge — latest wins; see {@link UiCoalesce}. */
    static void coalesce(Runnable r) { UiCoalesce.schedule(r); }
}
class Point {
    int x;
    int y;

    Point(int px, int py) {
        x = px;
        y = py;
    }
}

class Size {
    int width;
    int height;

    Size(int w, int h) {
        width = w;
        height = h;
    }
}

class Rect {
    int x;
    int y;
    int width;
    int height;

    Rect(int rx, int ry, int rw, int rh) {
        x = rx;
        y = ry;
        width = rw;
        height = rh;
    }

    int contains(int px, int py) {
        if (px < x) {
            return 0;
        }
        if (py < y) {
            return 0;
        }
        if (px >= x + width) {
            return 0;
        }
        if (py >= y + height) {
            return 0;
        }
        return 1;
    }
}

class Insets {
    int top;
    int right;
    int bottom;
    int left;

    Insets(int t, int r, int b, int l) {
        top = t;
        right = r;
        bottom = b;
        left = l;
    }

    static Insets uniform(int all) {
        return new Insets(all, all, all, all);
    }
}

class Dpi {
    static int scalePercentForId(int windowNativeId) {
        int p = UiNative.getScalePercent(windowNativeId);
        if (p < 50) {
            return 100;
        }
        return p;
    }

    static int toDevice(int logical, int scalePercent) {
        return logical * scalePercent / 100;
    }

    static int toLogical(int device, int scalePercent) {
        if (scalePercent <= 0) {
            return device;
        }
        return device * 100 / scalePercent;
    }
}

class Toolbar extends HBox {
    Toolbar() {
        spacing = Theme.SPACE_1;
        padding = Theme.SPACE_1;
        prefHeight = Theme.TOOLBAR_HEIGHT;
        height = Theme.TOOLBAR_HEIGHT;
        prefWidth = 400;
    }

    Button addToolButton(String label, Runnable action) {
        Button b = new Button(label);
        b.setChromeStyle(1);
        b.prefHeight = 28;
        b.height = 28;
        if (action != null) {
            b.onClick(action);
        }
        add(b);
        return b;
    }

    /** Bordered toolbar action with optional SF Symbol + tooltip. */
    Button addPushToolButton(String label, String symbol, String tip, Runnable action) {
        Button b = new Button(label);
        b.setChromeStyle(3);
        if (symbol != null && symbol.length() > 0) {
            b.setSystemSymbol(symbol);
        }
        if (tip != null) {
            b.setToolTip(tip);
            b.setAccessibleName(tip);
            b.setAccessibleDescription(tip);
        } else if (label != null && label.length() > 0) {
            b.setAccessibleName(label);
        }
        if (action != null) {
            b.onClick(action);
        }
        add(b);
        return b;
    }


    void addSeparatorGap() {
        Label gap = new Label(" ");
        gap.setPrefSize(8, 24);
        add(gap);
    }

    void addFlexibleSpace() {
        Label flex = new Label("");
        flex.prefWidth = 0;
        flex.width = 0;
        flex.prefHeight = 1;
        flex.height = 1;
        add(flex);
    }

    void addSegmented(SegmentedControl seg) {
        if (seg == null) {
            return;
        }
        add(seg);
    }

    void addSearch(SearchField field) {
        if (field == null) {
            return;
        }
        add(field);
    }

    /**
     * Overflow / "more" menu. Uses native context menu on Cocoa (FEAT_OVERFLOW_MENU).
     * On platforms without the feature, throws UiNativeException — does not fake success.
     */
    Button addOverflowMenu(String tip, Menu menu) {
        ensureAlive("Toolbar.addOverflowMenu");
        if ((UiPlatform.features() & UiPlatform.FEAT_OVERFLOW_MENU) == 0) {
            throw new UiNativeException("Toolbar overflow menu unsupported on this platform");
        }
        if (menu == null) {
            throw new UiException("Toolbar.addOverflowMenu: menu is null");
        }
        Button more = addPushToolButton("⋯", "ellipsis.circle", tip, () -> {
            UiNative.contextMenuShow(menu.nativeId, 0, 0);
        });
        return more;
    }
}

/**
 * Platform capability matrix for desktop UI features.
 * Bits match JN_UI_FEAT_* in jnative_ui.h.
 */
class UiPlatform {
    static int FEAT_NATIVE_TOOLBAR = 1;
    static int FEAT_SEGMENTED = 2;
    static int FEAT_SEARCH_FIELD = 4;
    static int FEAT_TABLE = 8;
    static int FEAT_TRENDCHART = 16;
    static int FEAT_DIALOGS = 32;
    static int FEAT_TOOLTIP = 64;
    static int FEAT_SF_SYMBOL = 128;
    static int FEAT_OVERFLOW_MENU = 256;

    static int features() {
        return UiNative.features();
    }

    static int supports(int feat) {
        if ((features() & feat) != 0) {
            return 1;
        }
        return 0;
    }

    /** 1=macOS Cocoa, 2=Windows, 3=other/headless. */
    static int family() {
        return UiNative.platformFamily();
    }

    static String supportSummary() {
        int f = features();
        String s = "platformFamily=" + family();
        s = s + " segmented=" + bit(f, FEAT_SEGMENTED);
        s = s + " search=" + bit(f, FEAT_SEARCH_FIELD);
        s = s + " table=" + bit(f, FEAT_TABLE);
        s = s + " trend=" + bit(f, FEAT_TRENDCHART);
        s = s + " dialogs=" + bit(f, FEAT_DIALOGS);
        s = s + " tooltip=" + bit(f, FEAT_TOOLTIP);
        s = s + " symbol=" + bit(f, FEAT_SF_SYMBOL);
        s = s + " overflow=" + bit(f, FEAT_OVERFLOW_MENU);
        s = s + " nsToolbar=" + bit(f, FEAT_NATIVE_TOOLBAR);
        return s;
    }

    static String bit(int f, int mask) {
        if ((f & mask) != 0) {
            return "yes";
        }
        return "no";
    }
}

/**
 * Bounded sparkline / trend chart. Samples are application-provided only.
 * Values are stored as milli-units (1000 = 1.0 display unit).
 * Encoding sent to Cocoa: "ymin,ymax,cap|v0,v1,..." (display units).
 */
class TrendChart extends Control {
    ArrayList<int> samplesMilli;
    int capacity;
    int yMinMilli;
    int yMaxMilli;
    String emptyLabel;

    TrendChart() {
        samplesMilli = new ArrayList<int>();
        capacity = 64;
        yMinMilli = 0;
        yMaxMilli = 100000;
        emptyLabel = "";
        prefWidth = 160;
        width = 160;
        prefHeight = 36;
        height = 36;
        nativeId = UiNative.createControl(21, 0, "");
        UiHandleRegistry.put(nativeId, this);
        setAccessibleName("趋势图");
        setAccessibleDescription("只读数据序列；不依赖动态效果即可读");
        pushNative();
    }

    void setCapacity(int cap) {
        ensureAlive("TrendChart.setCapacity");
        if (cap < 2 || cap > 4096) {
            throw new UiException("TrendChart.setCapacity: expected 2..4096, got " + cap);
        }
        capacity = cap;
        trim();
        pushNative();
    }

    /** Display-unit range, e.g. 0..100 for percent. */
    void setYRange(double min, double max) {
        ensureAlive("TrendChart.setYRange");
        if (max <= min) {
            throw new UiException("TrendChart.setYRange: max must be > min");
        }
        yMinMilli = (int) (min * 1000.0);
        yMaxMilli = (int) (max * 1000.0);
        pushNative();
    }

    void clear() {
        ensureAlive("TrendChart.clear");
        samplesMilli.clear();
        pushNative();
    }

    /** Push one sample in display units (e.g. 42.5 percent). */
    void push(double value) {
        ensureAlive("TrendChart.push");
        samplesMilli.add((int) (value * 1000.0));
        trim();
        pushNative();
    }

    /** Replace series from milli samples already in display*1000 (e.g. CPU milli 0–10000). */
    void setSamplesFromMilli(ArrayList<int> milli, double scaleToDisplay) {
        ensureAlive("TrendChart.setSamplesFromMilli");
        samplesMilli.clear();
        if (milli != null) {
            int i = 0;
            while (i < milli.size()) {
                int v = (int) (((double) milli.get(i)) * scaleToDisplay * 1000.0);
                samplesMilli.add(v);
                i = i + 1;
            }
        }
        trim();
        pushNative();
    }

    int size() {
        return samplesMilli.size();
    }

    void trim() {
        while (samplesMilli.size() > capacity) {
            samplesMilli.removeAt(0);
        }
    }

    void pushNative() {
        if (nativeId <= 0) {
            return;
        }
        if ((UiPlatform.features() & UiPlatform.FEAT_TRENDCHART) == 0) {
            return;
        }
        String body = "";
        int i = 0;
        while (i < samplesMilli.size()) {
            if (i > 0) {
                body = body + ",";
            }
            /* Emit display units with one decimal via integer math. */
            int m = samplesMilli.get(i);
            int whole = m / 1000;
            int frac = m % 1000;
            if (frac < 0) {
                frac = -frac;
            }
            int tenths = frac / 100;
            body = body + String.valueOf(whole) + "." + String.valueOf(tenths);
            i = i + 1;
        }
        int y0 = yMinMilli / 1000;
        int y1 = yMaxMilli / 1000;
        String meta = String.valueOf(y0) + "," + String.valueOf(y1) + ","
                + String.valueOf(capacity);
        UiNative.setText(nativeId, meta + "|" + body);
    }
}

interface BreadcrumbNavigate {
    void go(String path);
}

/**
 * Breadcrumb address control. Normal: clickable segments. Edit: TextField (Cmd/Ctrl+L).
 */
class BreadcrumbBar extends HBox {
    ArrayList<String> segments;
    ArrayList<String> paths;
    BreadcrumbNavigate onNavigate;
    TextField editor;
    int editing;
    HBox crumbs;

    BreadcrumbBar() {
        spacing = 2;
        padding = 4;
        segments = new ArrayList<String>();
        paths = new ArrayList<String>();
        editing = 0;
        crumbs = new HBox();
        crumbs.spacing = 2;
        crumbs.padding = 0;
        crumbs.prefWidth = 40;
        crumbs.width = 40;
        crumbs.prefHeight = 22;
        crumbs.height = 22;
        editor = new TextField("");
        editor.setPrefSize(480, 26);
        BreadcrumbBar.host = this;
        editor.onKeyDown(ev -> {
            BreadcrumbBar.onEditorKeyStatic(ev);
        });
        /* Height before add — HBox.layout must not collapse chrome to padding. */
        prefHeight = 26;
        height = 26;
        add(crumbs);
        prefHeight = 26;
        height = 26;
    }

    void setOnNavigate(BreadcrumbNavigate nav) {
        onNavigate = nav;
    }

    void setPath(String absolutePath) {
        ensureAlive("BreadcrumbBar.setPath");
        segments.clear();
        paths.clear();
        if (absolutePath == null || absolutePath.length() == 0) {
            rebuildCrumbs();
            return;
        }
        String p = absolutePath;
        if (p.length() >= 2 && p.charAt(1) == 58) {
            segments.add(p.substring(0, 2));
            paths.add(p.substring(0, 2) + "\\");
            if (p.length() > 3) {
                splitTail(p.substringFrom(3), paths.get(0), "\\");
            }
        } else {
            segments.add("/");
            paths.add("/");
            if (p.length() > 1) {
                String rest = p;
                if (rest.charAt(0) == 47) {
                    rest = rest.substringFrom(1);
                }
                splitTail(rest, "/", "/");
            }
        }
        if (editing != 0) {
            editor.setText(absolutePath);
        } else {
            rebuildCrumbs();
        }
    }

    void splitTail(String rest, String prefix, String sep) {
        int start = 0;
        String built = prefix;
        while (start < rest.length()) {
            int slash = rest.indexOfFrom(sep, start);
            int end = slash;
            if (end < 0) {
                end = rest.length();
            }
            String part = rest.substring(start, end);
            if (part.length() > 0) {
                if (built.equals("/")) {
                    built = "/" + part;
                } else if (built.endsWith(sep)) {
                    built = built + part;
                } else {
                    built = built + sep + part;
                }
                segments.add(part);
                paths.add(built);
            }
            if (slash < 0) {
                start = rest.length();
            } else {
                start = slash + 1;
            }
        }
    }

    void rebuildCrumbs() {
        if (editing != 0) {
            clear();
            editing = 0;
            add(crumbs);
        }
        crumbs.clear();
        int i = 0;
        while (i < segments.size()) {
            if (i > 0) {
                Label sep = new Label(">");
                sep.setPrefSize(10, 20);
                crumbs.add(sep);
            }
            addCrumbButton(segments.get(i), paths.get(i));
            i = i + 1;
        }
        /* Keep chrome height; HBox.layout alone can collapse empty bars. */
        prefHeight = 26;
        height = 26;
        crumbs.prefHeight = 22;
        crumbs.height = 22;
        crumbs.layout();
        /* Shrink wrap crumbs to content so segments stay packed left. */
        if (crumbs.prefWidth > 0) {
            crumbs.width = crumbs.prefWidth;
        }
        layout();
    }

    /** Relayout this bar inside its parent after path changes (safe post-show). */
    void relayoutInParent() {
        prefHeight = 26;
        height = 26;
        layout();
        if (parent != null && parent instanceof Container) {
            Container c = (Container) parent;
            c.layout();
        }
    }

    /** Click uses Component.lastClicked + userData (no lambda captures — IR-safe). */
    void addCrumbButton(String label, String target) {
        Button seg = new Button(label);
        seg.setChromeStyle(2);
        seg.setUserData(target);
        BreadcrumbBar.host = this;
        seg.onClick(() -> {
            BreadcrumbBar.onSegmentClick();
        });
        crumbs.add(seg);
    }

    static BreadcrumbBar host;

    static void onSegmentClick() {
        if (host == null || Component.lastClicked == null) {
            return;
        }
        String path = Component.lastClicked.getUserData();
        host.fireNav(path);
    }

    void fireNav(String path) {
        if (onNavigate != null && path != null) {
            onNavigate.go(path);
        }
    }

    void beginEdit(String currentPath) {
        editing = 1;
        clear();
        if (currentPath != null) {
            editor.setText(currentPath);
        }
        add(editor);
        if (editor.nativeId > 0) {
            UiNative.focus(editor.nativeId);
        }
        requestLayout();
    }

    void endEdit(int commit) {
        String text = editor.getText();
        editing = 0;
        clear();
        add(crumbs);
        if (commit != 0 && onNavigate != null && text != null && text.length() > 0) {
            onNavigate.go(text);
        } else {
            rebuildCrumbs();
        }
    }

    void onEditorKey(KeyEvent ev) {
        if (ev == null) {
            return;
        }
        if (ev.keyCode == 13) {
            endEdit(1);
        }
        if (ev.keyCode == 27) {
            endEdit(0);
        }
    }

    static void onEditorKeyStatic(KeyEvent ev) {
        if (host != null) {
            host.onEditorKey(ev);
        }
    }

    int isEditing() {
        return editing;
    }
}


class FocusManager {
    static Component focused;

    static void requestFocus(Component c) {
        if (c == null || c.nativeId <= 0) {
            return;
        }
        if (c.disposed != 0 || c.enabled == 0 || c.visible == 0) {
            return;
        }
        focused = c;
        UiNative.focus(c.nativeId);
    }

    static void noteFocus(Component c, int gained) {
        if (gained != 0) {
            focused = c;
        } else if (focused == c) {
            focused = null;
        }
    }
}

/**
 * Explicit tab order. Call {@link #apply(Window, ArrayList)} after building the form.
 * Native Cocoa key-view loop is wired; AppKit still handles Tab when views are linked.
 */
class FocusTraversal {
    static void apply(Window window, ArrayList<Component> ordered) {
        Application.checkUiThread("FocusTraversal.apply");
        if (ordered == null || ordered.size() == 0) {
            return;
        }
        int i = 0;
        while (i < ordered.size()) {
            Component c = ordered.get(i);
            if (c != null) {
                c.setTabIndex(i);
            }
            i = i + 1;
        }
        i = 0;
        while (i + 1 < ordered.size()) {
            Component a = ordered.get(i);
            Component b = ordered.get(i + 1);
            if (a != null && b != null && a.nativeId > 0 && b.nativeId > 0) {
                UiNative.setNextKeyView(a.nativeId, b.nativeId);
            }
            i = i + 1;
        }
        Component last = ordered.get(ordered.size() - 1);
        Component first = ordered.get(0);
        if (last != null && first != null && last.nativeId > 0 && first.nativeId > 0) {
            UiNative.setNextKeyView(last.nativeId, first.nativeId);
        }
        if (first != null) {
            FocusManager.requestFocus(first);
        }
    }

    /** Move focus to the first INVALID / EDITING field with a non-empty error (forms). */
    static void focusFirstError(ArrayList<Component> fields, ArrayList<String> errors) {
        if (fields == null || errors == null) {
            return;
        }
        int i = 0;
        while (i < fields.size() && i < errors.size()) {
            String e = errors.get(i);
            if (e != null && e.length() > 0) {
                Component c = fields.get(i);
                if (c != null) {
                    FocusManager.requestFocus(c);
                    return;
                }
            }
            i = i + 1;
        }
    }
}

class VirtualListMetrics {
    int dataSize;
    int rowHeight;
    int viewportHeight;
    int overscan;
    int firstVisible;
    int visibleCount;
    int poolSize;

    VirtualListMetrics() {
        dataSize = 0;
        rowHeight = 28;
        viewportHeight = 400;
        overscan = 4;
        firstVisible = 0;
        visibleCount = 0;
        poolSize = 0;
        recompute();
    }

    void setDataSize(int n) {
        dataSize = n;
        recompute();
    }

    void setViewportHeight(int h) {
        viewportHeight = h;
        recompute();
    }

    void recompute() {
        int rows = 1;
        if (rowHeight > 0) {
            rows = viewportHeight / rowHeight + 1;
        }
        visibleCount = rows + overscan * 2;
        if (visibleCount > dataSize) {
            visibleCount = dataSize;
        }
        if (visibleCount < 0) {
            visibleCount = 0;
        }
        poolSize = visibleCount;
        if (poolSize < 8 && dataSize > 0) {
            poolSize = 8;
        }
        if (poolSize > dataSize) {
            poolSize = dataSize;
        }
    }
}

class SystemIcons {
    static String BACK = "BACK";
    static String FORWARD = "FORWARD";
    static String UP = "UP";
    static String REFRESH = "REFRESH";
    static String SEARCH = "SEARCH";
    static String FOLDER = "FOLDER";
    static String FILE = "FILE";
    static String COPY = "COPY";
    static String CUT = "CUT";
    static String PASTE = "PASTE";
    static String DELETE = "DELETE";
}

interface CommandExecute {
    void run();
}

class Command {
    String id;
    String label;
    int shortcutKey;
    int shortcutPrimary;
    int enabled;
    ArrayList<Component> boundControls;
    CommandExecute onExecute;

    Command(String cid, String lab) {
        id = cid;
        label = lab;
        shortcutKey = 0;
        shortcutPrimary = 0;
        enabled = 1;
        boundControls = new ArrayList<Component>();
        onExecute = null;
    }

    void setPrimaryShortcut(int keyCode) {
        shortcutKey = keyCode;
        shortcutPrimary = 1;
    }

    void setShortcut(KeyCombination kc) {
        if (kc == null) {
            return;
        }
        shortcutKey = kc.keyCode;
        if (kc.shortcut != 0) {
            shortcutPrimary = 1;
        } else {
            shortcutPrimary = 0;
        }
    }

    void setExecute(CommandExecute handler) {
        onExecute = handler;
        CommandRegistry.remember(this);
    }

    void execute() {
        if (enabled == 0) {
            return;
        }
        if (onExecute != null) {
            onExecute.run();
        }
    }

    void bindButton(Button b) {
        if (b == null) {
            return;
        }
        boundControls.add(b);
        /* Toolbar chrome keeps glyph; overwriting with Copy truncates labels. */
        if (b.chromeStyle != 1) {
            b.setText(label);
        }
        b.setUserData(id);
        CommandRegistry.remember(this);
        b.onClick(() -> {
            CommandRegistry.executeByButton();
        });
    }

    /** Menu, toolbar, and shortcuts share the same execute handler. */
    void bindMenuItem(MenuItem item) {
        if (item == null) {
            return;
        }
        boundControls.add(item);
        item.boundCommand = this;
        item.setUserData(id);
        if (shortcutKey != 0) {
            KeyCombination kc = KeyCombination.of(shortcutKey);
            if (shortcutPrimary != 0) {
                kc = kc.withPrimary();
            }
            item.setShortcut(kc);
        }
        CommandRegistry.remember(this);
    }

    void setEnabled(int on) {
        enabled = on;
        int i = 0;
        while (i < boundControls.size()) {
            Component c = boundControls.get(i);
            if (on != 0) {
                c.setEnabled(true);
            } else {
                c.setEnabled(false);
            }
            i = i + 1;
        }
    }
}

class CommandRegistry {
    static ArrayList<Command> all;
    static String lastExecutedId;

    static void remember(Command c) {
        if (all == null) {
            all = new ArrayList<Command>();
        }
        int i = 0;
        while (i < all.size()) {
            if (all.get(i) == c) {
                return;
            }
            i = i + 1;
        }
        all.add(c);
    }

    static void executeByButton() {
        if (Component.lastClicked == null) {
            return;
        }
        String id = Component.lastClicked.getUserData();
        Command c = find(id);
        if (c != null) {
            lastExecutedId = id;
            c.execute();
        }
    }

    static Command find(String id) {
        if (all == null || id == null) {
            return null;
        }
        int i = 0;
        while (i < all.size()) {
            Command c = all.get(i);
            if (id.equals(c.id)) {
                return c;
            }
            i = i + 1;
        }
        return null;
    }

    static int handleShortcut(KeyEvent ev) {
        if (all == null || ev == null) {
            return 0;
        }
        int primary = 0;
        if (UiNative.platformFamily() == 1) {
            if (ev.metaDown != 0) {
                primary = 1;
            }
        } else {
            if (ev.ctrlDown != 0) {
                primary = 1;
            }
        }
        int code = ev.keyCode;
        if (code >= 97 && code <= 122) {
            code = code - 32;
        }
        int i = 0;
        while (i < all.size()) {
            Command c = all.get(i);
            if (c.shortcutPrimary != 0 && primary != 0 && code == c.shortcutKey) {
                lastExecutedId = c.id;
                c.execute();
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }
}

/**
 * Lightweight standard menu builders. Wire actions via {@link Command#bindMenuItem}.
 * Does not invent a full document framework.
 */
class AppMenus {
    static Menu fileMenu(Command openCmd, Command saveCmd, Command quitCmd) {
        Menu m = new Menu("文件");
        if (openCmd != null) {
            MenuItem open = new MenuItem(openCmd.label);
            openCmd.bindMenuItem(open);
            m.add(open);
        }
        if (saveCmd != null) {
            MenuItem save = new MenuItem(saveCmd.label);
            saveCmd.bindMenuItem(save);
            m.add(save);
        }
        if (openCmd != null || saveCmd != null) {
            m.addSeparator();
        }
        if (quitCmd != null) {
            MenuItem quit = new MenuItem(quitCmd.label);
            quitCmd.bindMenuItem(quit);
            m.add(quit);
        }
        return m;
    }

    static Menu editMenu() {
        /* Edit menu present for structure; text editing uses native NSTextField responders. */
        Menu m = new Menu("编辑");
        m.add(new MenuItem("剪切"));
        m.add(new MenuItem("复制"));
        m.add(new MenuItem("粘贴"));
        return m;
    }

    static Menu windowMenu(Command closeCmd) {
        Menu m = new Menu("窗口");
        if (closeCmd != null) {
            MenuItem close = new MenuItem(closeCmd.label);
            closeCmd.bindMenuItem(close);
            m.add(close);
        }
        return m;
    }

    static Menu helpMenu(Command aboutCmd) {
        Menu m = new Menu("帮助");
        if (aboutCmd != null) {
            MenuItem about = new MenuItem(aboutCmd.label);
            aboutCmd.bindMenuItem(about);
            m.add(about);
        }
        return m;
    }
}

class ShortcutManager {
    /**
     * Dispatch application commands. Standard edit keys (C/V/X/Z/A) are not stolen
     * while a TextField / TextArea / SearchField holds focus — native control wins.
     */
    static int dispatch(KeyEvent ev) {
        if (ev == null) {
            return 0;
        }
        if (isTextEditingFocus() != 0 && isStandardEditShortcut(ev) != 0) {
            return 0;
        }
        return CommandRegistry.handleShortcut(ev);
    }

    static int isTextEditingFocus() {
        Component f = FocusManager.focused;
        if (f == null) {
            return 0;
        }
        if (f instanceof TextField) {
            return 1;
        }
        if (f instanceof TextArea) {
            return 1;
        }
        return 0;
    }

    static int isStandardEditShortcut(KeyEvent ev) {
        int primary = 0;
        if (UiNative.platformFamily() == 1) {
            if (ev.metaDown != 0) {
                primary = 1;
            }
        } else {
            if (ev.ctrlDown != 0) {
                primary = 1;
            }
        }
        if (primary == 0) {
            return 0;
        }
        int code = ev.keyCode;
        if (code >= 97 && code <= 122) {
            code = code - 32;
        }
        /* C V X Z A */
        if (code == 67 || code == 86 || code == 88 || code == 90 || code == 65) {
            return 1;
        }
        return 0;
    }

    /** Install window-level key routing for Command shortcuts. */
    static void installOn(Window window) {
        if (window == null) {
            return;
        }
        window.onKeyDown((ev) -> {
            ShortcutManager.dispatch(ev);
        });
    }
}

interface SearchQueryListener {
    void run();
}

class SearchField extends HBox {
    TextField field;
    Button clearBtn;
    String placeholder;
    int changeGen;
    SearchQueryListener queryListener;

    SearchField() {
        spacing = 4;
        padding = 2;
        placeholder = "Search";
        changeGen = 0;
        queryListener = null;
        field = new TextField("");
        field.setPrefSize(180, 26);
        if (field.nativeId > 0) {
            UiNative.setValueI32(field.nativeId, 4);
        }
        clearBtn = new Button("✕");
        clearBtn.setChromeStyle(1);
        clearBtn.setPrefSize(28, 26);
        clearBtn.setToolTip("清除");
        SearchField.active = this;
        clearBtn.onClick(() -> {
            SearchField.clearActive();
        });
        field.onChange(ev -> {
            SearchField.bumpActive();
        });
        field.onKeyDown(ev -> {
            SearchField.onKeyActive(ev);
        });
        add(field);
        add(clearBtn);
        prefWidth = 220;
        width = 220;
        prefHeight = 30;
        height = 30;
    }

    static SearchField active;

    static void clearActive() {
        if (active != null) {
            active.clear();
        }
    }

    static void bumpActive() {
        if (active != null) {
            active.changeGen = active.changeGen + 1;
            if (active.queryListener != null) {
                active.queryListener.run();
            }
        }
    }

    static void onKeyActive(KeyEvent ev) {
        if (active == null || ev == null) {
            return;
        }
        if (ev.keyCode == KeyCode.ESCAPE) {
            active.clear();
        }
    }

    void setPlaceholder(String p) {
        placeholder = p;
    }

    void setOnQueryChanged(SearchQueryListener listener) {
        queryListener = listener;
    }

    int getChangeGen() {
        return changeGen;
    }

    String getQuery() {
        return field.getText();
    }

    void setQuery(String q) {
        if (q == null) {
            q = "";
        }
        field.setText(q);
    }

    void clear() {
        field.setText("");
        changeGen = changeGen + 1;
        if (queryListener != null) {
            queryListener.run();
        }
    }

    void focusField() {
        FocusManager.requestFocus(field);
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
