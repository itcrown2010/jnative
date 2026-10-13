/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

// JNative UI Binding (lightweight) — explicit, typed, no reflection / no MVVM framework.
// Depends on UiLib event/subscription model. Included with UI stdlib.

/**
 * Listener for ObservableValue. Must not re-enter set() unboundedly;
 * nested updates are allowed up to ObservableValue.MAX_NESTING then throw.
 */
class ObsChangeEvent<T> {
    T oldValue;
    T newValue;

    ObsChangeEvent(T o, T n) {
        oldValue = o;
        newValue = n;
    }
}

interface ObsListener<T> {
    void onChanged(ObsChangeEvent<T> ev);
}

/** Cancellable observer registration. cancel() is idempotent. */
class ObsSubscription {
    Runnable cancelAction;
    int cancelled;

    ObsSubscription(Runnable action) {
        cancelAction = action;
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
        Runnable a = cancelAction;
        cancelAction = null;
        if (a != null) {
            a.run();
        }
    }
}

/**
 * Explicit observable box. Equals-skip on set. Listeners run outside any lock
 * after the value is committed. Nested set during notify increments nesting;
 * beyond MAX_NESTING throws UiException (prevents feedback loops).
 *
 * Thread: set/subscribe/cancel must run on the UI thread (Application.checkUiThread).
 */
class ObservableValue<T> {
    static int MAX_NESTING = 8;
    T value;
    ArrayList<ObsListener<T>> listeners;
    int nesting;
    int batchDepth;
    int batchDirty;
    T batchOld;
    int suppressEquals;

    ObservableValue(T initial) {
        value = initial;
        listeners = new ArrayList<ObsListener<T>>();
        nesting = 0;
        batchDepth = 0;
        batchDirty = 0;
        batchOld = null;
        suppressEquals = 0;
    }

    static ObservableValue<String> ofString(String v) {
        if (v == null) {
            v = "";
        }
        return new ObservableValue<String>(v);
    }

    static ObservableInt ofInt(int v) {
        return new ObservableInt(v);
    }

    static ObservableInt ofBool(boolean v) {
        if (v) {
            return new ObservableInt(1);
        }
        return new ObservableInt(0);
    }

    T get() {
        return value;
    }

    void set(T next) {
        Application.checkUiThread("ObservableValue.set");
        if (suppressEquals == 0 && obsEquals(value, next) != 0) {
            return;
        }
        if (batchDepth > 0) {
            if (batchDirty == 0) {
                batchOld = value;
                batchDirty = 1;
            }
            value = next;
            return;
        }
        commit(next);
    }

    void setForce(T next) {
        Application.checkUiThread("ObservableValue.setForce");
        if (batchDepth > 0) {
            if (batchDirty == 0) {
                batchOld = value;
                batchDirty = 1;
            }
            value = next;
            return;
        }
        commit(next);
    }

    void beginUpdate() {
        Application.checkUiThread("ObservableValue.beginUpdate");
        batchDepth = batchDepth + 1;
    }

    void endUpdate() {
        Application.checkUiThread("ObservableValue.endUpdate");
        if (batchDepth <= 0) {
            throw new UiException("ObservableValue.endUpdate without beginUpdate");
        }
        batchDepth = batchDepth - 1;
        if (batchDepth == 0 && batchDirty != 0) {
            batchDirty = 0;
            T oldV = batchOld;
            batchOld = null;
            if (obsEquals(oldV, value) == 0) {
                notifyListeners(oldV, value);
            }
        }
    }

    ObsSubscription subscribe(ObsListener<T> listener) {
        Application.checkUiThread("ObservableValue.subscribe");
        if (listener == null) {
            throw new UiException("ObservableValue.subscribe: listener is null");
        }
        listeners.add(listener);
        ObsListener<T> held = listener;
        ObservableValue<T> self = this;
        return new ObsSubscription(() -> {
            self.detach(held);
        });
    }

    void detach(ObsListener<T> listener) {
        int i = 0;
        while (i < listeners.size()) {
            if (listeners.get(i) == listener) {
                listeners.removeAt(i);
                return;
            }
            i = i + 1;
        }
    }

    void commit(T next) {
        T oldV = value;
        value = next;
        notifyListeners(oldV, next);
    }

    void notifyListeners(T oldV, T next) {
        if (nesting >= MAX_NESTING) {
            throw new UiException("ObservableValue: nested update exceeded MAX_NESTING="
                    + MAX_NESTING + " (possible binding feedback loop)");
        }
        nesting = nesting + 1;
        try {
            ArrayList<ObsListener<T>> snap = new ArrayList<ObsListener<T>>();
            int n = listeners.size();
            int i = 0;
            while (i < n) {
                snap.add(listeners.get(i));
                i = i + 1;
            }
            i = 0;
            while (i < snap.size()) {
                try {
                    snap.get(i).onChanged(new ObsChangeEvent<T>(oldV, next));
                } catch (UiException ex) {
                    /* Nesting / contract errors must propagate to the set() caller. */
                    throw ex;
                } catch (RuntimeException ex) {
                    System.out.println("Uncaught observable listener: " + ex.getMessage());
                    if (Application.uncaughtHandler != null) {
                        Application.uncaughtHandler.run();
                    }
                }
                i = i + 1;
            }
        } finally {
            nesting = nesting - 1;
        }
    }

    static int obsEquals(Object a, Object b) {
        if (a == null && b == null) {
            return 1;
        }
        if (a == null || b == null) {
            return 0;
        }
        if (a == b) {
            return 1;
        }
        if (a instanceof String && b instanceof String) {
            if (((String) a).equals((String) b)) {
                return 1;
            }
            return 0;
        }
        /* Primitive boxes compare by == for int identity in jnative ArrayList<int>. */
        return 0;
    }
}

/**
 * Window/control-scoped bag of bindings and subscriptions.
 * dispose() cancels all; attach to Window.onClose or Component.dispose path.
 */

/**
 * Int observable with value equality (avoids generic box compare pitfalls).
 */
class ObservableInt {
    static int MAX_NESTING = 8;
    int value;
    ArrayList<ObsListener<int>> listeners;
    int nesting;
    int batchDepth;
    int batchDirty;
    int batchOld;

    ObservableInt(int initial) {
        value = initial;
        listeners = new ArrayList<ObsListener<int>>();
        nesting = 0;
        batchDepth = 0;
        batchDirty = 0;
        batchOld = 0;
    }

    int get() {
        return value;
    }

    void set(int next) {
        Application.checkUiThread("ObservableInt.set");
        if (value == next) {
            return;
        }
        if (batchDepth > 0) {
            if (batchDirty == 0) {
                batchOld = value;
                batchDirty = 1;
            }
            value = next;
            return;
        }
        int oldV = value;
        value = next;
        notifyListeners(oldV, next);
    }

    void beginUpdate() {
        Application.checkUiThread("ObservableInt.beginUpdate");
        batchDepth = batchDepth + 1;
    }

    void endUpdate() {
        Application.checkUiThread("ObservableInt.endUpdate");
        if (batchDepth <= 0) {
            throw new UiException("ObservableInt.endUpdate without beginUpdate");
        }
        batchDepth = batchDepth - 1;
        if (batchDepth == 0 && batchDirty != 0) {
            batchDirty = 0;
            int oldV = batchOld;
            if (oldV != value) {
                notifyListeners(oldV, value);
            }
        }
    }

    ObsSubscription subscribe(ObsListener<int> listener) {
        Application.checkUiThread("ObservableInt.subscribe");
        if (listener == null) {
            throw new UiException("ObservableInt.subscribe: listener is null");
        }
        listeners.add(listener);
        ObsListener<int> held = listener;
        ObservableInt self = this;
        return new ObsSubscription(() -> {
            self.detach(held);
        });
    }

    void detach(ObsListener<int> listener) {
        int i = 0;
        while (i < listeners.size()) {
            if (listeners.get(i) == listener) {
                listeners.removeAt(i);
                return;
            }
            i = i + 1;
        }
    }

    void notifyListeners(int oldV, int next) {
        if (nesting >= MAX_NESTING) {
            throw new UiException("ObservableInt: nested update exceeded MAX_NESTING");
        }
        nesting = nesting + 1;
        try {
            ArrayList<ObsListener<int>> snap = new ArrayList<ObsListener<int>>();
            int n = listeners.size();
            int i = 0;
            while (i < n) {
                snap.add(listeners.get(i));
                i = i + 1;
            }
            i = 0;
            while (i < snap.size()) {
                try {
                    snap.get(i).onChanged(new ObsChangeEvent<int>(oldV, next));
                } catch (UiException ex) {
                    throw ex;
                } catch (RuntimeException ex) {
                    System.out.println("Uncaught observable listener: " + ex.getMessage());
                    if (Application.uncaughtHandler != null) {
                        Application.uncaughtHandler.run();
                    }
                }
                i = i + 1;
            }
        } finally {
            nesting = nesting - 1;
        }
    }
}

class BindingScope {
    ArrayList<ObsSubscription> obs;
    ArrayList<UiSubscription> ui;
    ArrayList<BindingHandle> handles;
    int disposed;
    Component host;

    BindingScope() {
        obs = new ArrayList<ObsSubscription>();
        ui = new ArrayList<UiSubscription>();
        handles = new ArrayList<BindingHandle>();
        disposed = 0;
        host = null;
    }

    BindingScope(Component owner) {
        obs = new ArrayList<ObsSubscription>();
        ui = new ArrayList<UiSubscription>();
        handles = new ArrayList<BindingHandle>();
        disposed = 0;
        host = owner;
    }

    void addObs(ObsSubscription s) {
        if (s != null && disposed == 0) {
            obs.add(s);
        }
    }

    void addUi(UiSubscription s) {
        if (s != null && disposed == 0) {
            ui.add(s);
        }
    }

    void addHandle(BindingHandle h) {
        if (h != null && disposed == 0) {
            handles.add(h);
        }
    }

    int isDisposed() {
        return disposed;
    }

    void dispose() {
        Application.checkUiThread("BindingScope.dispose");
        if (disposed != 0) {
            return;
        }
        disposed = 1;
        int i = 0;
        while (i < handles.size()) {
            handles.get(i).dispose();
            i = i + 1;
        }
        handles.clear();
        i = 0;
        while (i < obs.size()) {
            obs.get(i).cancel();
            i = i + 1;
        }
        obs.clear();
        i = 0;
        while (i < ui.size()) {
            ui.get(i).cancel();
            i = i + 1;
        }
        ui.clear();
        host = null;
    }
}

class BindingHandle {
    int disposed;
    ObsSubscription obsSub;
    UiSubscription uiSub;
    Runnable extra;

    BindingHandle() {
        disposed = 0;
        obsSub = null;
        uiSub = null;
        extra = null;
    }

    void dispose() {
        if (disposed != 0) {
            return;
        }
        disposed = 1;
        if (obsSub != null) {
            obsSub.cancel();
            obsSub = null;
        }
        if (uiSub != null) {
            uiSub.cancel();
            uiSub = null;
        }
        if (extra != null) {
            extra.run();
            extra = null;
        }
    }
}

/**
 * Explicit control bindings. Two-way text uses setTextIfChanged to preserve caret.
 */
class Bindings {
    static BindingHandle bindLabel(Label label, ObservableValue<String> obs, BindingScope scope) {
        Application.checkUiThread("Bindings.bindLabel");
        BindingHandle h = new BindingHandle();
        if (label == null || obs == null) {
            throw new UiException("Bindings.bindLabel: null arg");
        }
        String cur = obs.get();
        if (cur == null) {
            cur = "";
        }
        label.setText(cur);
        h.obsSub = obs.subscribe((ev) -> {
            if (scope != null && scope.isDisposed() != 0) {
                return;
            }
            if (label.disposed != 0) {
                return;
            }
            String t = ev.newValue;
            if (t == null) {
                t = "";
            }
            label.setText(t);
        });
        if (scope != null) {
            scope.addObs(h.obsSub);
            scope.addHandle(h);
        }
        return h;
    }

    static BindingHandle bindTextOneWay(TextField field, ObservableValue<String> obs, BindingScope scope) {
        Application.checkUiThread("Bindings.bindTextOneWay");
        BindingHandle h = new BindingHandle();
        if (field == null || obs == null) {
            throw new UiException("Bindings.bindTextOneWay: null arg");
        }
        String cur = obs.get();
        if (cur == null) {
            cur = "";
        }
        field.setTextIfChanged(cur);
        h.obsSub = obs.subscribe((ev) -> {
            if (scope != null && scope.isDisposed() != 0) {
                return;
            }
            if (field.disposed != 0) {
                return;
            }
            String t = ev.newValue;
            if (t == null) {
                t = "";
            }
            field.setTextIfChanged(t);
        });
        if (scope != null) {
            scope.addObs(h.obsSub);
            scope.addHandle(h);
        }
        return h;
    }

    static BindingHandle bindTextTwoWay(TextField field, ObservableValue<String> obs, BindingScope scope) {
        Application.checkUiThread("Bindings.bindTextTwoWay");
        BindingHandle h = bindTextOneWay(field, obs, scope);
        h.uiSub = field.subscribeChange((ev) -> {
            if (scope != null && scope.isDisposed() != 0) {
                return;
            }
            if (field.disposed != 0) {
                return;
            }
            String t = field.getText();
            if (t == null) {
                t = "";
            }
            obs.set(t);
        });
        if (scope != null) {
            scope.addUi(h.uiSub);
        }
        return h;
    }

    static BindingHandle bindCheck(CheckBox box, ObservableInt obs, BindingScope scope) {
        Application.checkUiThread("Bindings.bindCheck");
        BindingHandle h = new BindingHandle();
        if (box == null || obs == null) {
            throw new UiException("Bindings.bindCheck: null arg");
        }
        int v = obs.get();
        if (v != 0) {
            box.setSelected(true);
        } else {
            box.setSelected(false);
        }
        h.obsSub = obs.subscribe((ev) -> {
            if (scope != null && scope.isDisposed() != 0) {
                return;
            }
            if (box.disposed != 0) {
                return;
            }
            int n = ev.newValue;
            int cur = 0;
            if (box.isSelected()) {
                cur = 1;
            }
            if (cur != n) {
                if (n != 0) {
                    box.setSelected(true);
                } else {
                    box.setSelected(false);
                }
            }
        });
        /* Prefer cancelable subscribeChange (fires from fireToggle) over permanent onChangeValue. */
        h.uiSub = box.subscribeChange((ev) -> {
            if (scope != null && scope.isDisposed() != 0) {
                return;
            }
            if (box.disposed != 0) {
                return;
            }
            if (box.isSelected()) {
                obs.set(1);
            } else {
                obs.set(0);
            }
        });
        if (scope != null) {
            scope.addObs(h.obsSub);
            scope.addUi(h.uiSub);
            scope.addHandle(h);
        }
        return h;
    }

    static BindingHandle bindEnabled(Component c, ObservableInt obs, BindingScope scope) {
        Application.checkUiThread("Bindings.bindEnabled");
        BindingHandle h = new BindingHandle();
        if (c == null || obs == null) {
            throw new UiException("Bindings.bindEnabled: null arg");
        }
        if (obs.get() != 0) {
            c.setEnabled(true);
        } else {
            c.setEnabled(false);
        }
        h.obsSub = obs.subscribe((ev) -> {
            if (scope != null && scope.isDisposed() != 0) {
                return;
            }
            if (c.disposed != 0) {
                return;
            }
            if (ev.newValue != 0) {
                c.setEnabled(true);
            } else {
                c.setEnabled(false);
            }
        });
        if (scope != null) {
            scope.addObs(h.obsSub);
            scope.addHandle(h);
        }
        return h;
    }

    static BindingHandle bindSegmented(SegmentedControl seg, ObservableInt obs, BindingScope scope) {
        Application.checkUiThread("Bindings.bindSegmented");
        BindingHandle h = new BindingHandle();
        if (seg == null || obs == null) {
            throw new UiException("Bindings.bindSegmented: null arg");
        }
        seg.setSelectedIndex(obs.get());
        h.obsSub = obs.subscribe((ev) -> {
            if (scope != null && scope.isDisposed() != 0) {
                return;
            }
            if (seg.disposed != 0) {
                return;
            }
            if (seg.getSelectedIndex() != ev.newValue) {
                seg.setSelectedIndex(ev.newValue);
            }
        });
        seg.onSelectionChanged((ev) -> {
            if (scope != null && scope.isDisposed() != 0) {
                return;
            }
            if (seg.disposed != 0) {
                return;
            }
            obs.set(ev.selectedIndex);
        });
        if (scope != null) {
            scope.addObs(h.obsSub);
            scope.addHandle(h);
        }
        return h;
    }

    /**
     * Combo selection by stable string id. items[i] maps to ids.get(i).
     * Does not use row index as business identity beyond the id table.
     */
    static BindingHandle bindComboId(ComboBox<String> combo, ObservableValue<String> selectedId,
            ArrayList<String> ids, BindingScope scope) {
        Application.checkUiThread("Bindings.bindComboId");
        BindingHandle h = new BindingHandle();
        if (combo == null || selectedId == null || ids == null) {
            throw new UiException("Bindings.bindComboId: null arg");
        }
        syncComboSelection(combo, selectedId.get(), ids);
        h.obsSub = selectedId.subscribe((ev) -> {
            if (scope != null && scope.isDisposed() != 0) {
                return;
            }
            if (combo.disposed != 0) {
                return;
            }
            syncComboSelection(combo, ev.newValue, ids);
        });
        combo.onSelectionChanged((ev) -> {
            if (scope != null && scope.isDisposed() != 0) {
                return;
            }
            if (combo.disposed != 0) {
                return;
            }
            int idx = ev.selectedIndex;
            if (idx >= 0 && idx < ids.size()) {
                selectedId.set(ids.get(idx));
            }
        });
        if (scope != null) {
            scope.addObs(h.obsSub);
            scope.addHandle(h);
        }
        return h;
    }

    static void syncComboSelection(ComboBox<String> combo, String id, ArrayList<String> ids) {
        int i = 0;
        int found = -1;
        while (i < ids.size()) {
            String x = ids.get(i);
            if (id == null && x == null) {
                found = i;
                break;
            }
            if (id != null && x != null && id.equals(x)) {
                found = i;
                break;
            }
            i = i + 1;
        }
        if (found >= 0) {
            combo.setSelectedIndex(found);
        }
    }

    /** Wire VoiceOver title UI element so the error label is announced with the field. */
    static void associateErrorLabel(TextField field, Label errorLabel) {
        if (field == null || errorLabel == null) {
            return;
        }
        if (field.nativeId > 0 && errorLabel.nativeId > 0) {
            UiNative.setAccessibleTitleUi(field.nativeId, errorLabel.nativeId);
        }
        if (errorLabel.accessibleName == null || errorLabel.accessibleName.length() == 0) {
            errorLabel.setAccessibleName("校验错误");
        }
    }

    /** Derive target from sources via compute(); recompute on any source change. */
    static BindingHandle bindComputed(ObservableValue<String> target, ArrayList<ObservableValue<String>> sources,
            Runnable compute, BindingScope scope) {
        Application.checkUiThread("Bindings.bindComputed");
        BindingHandle h = new BindingHandle();
        if (target == null || sources == null || compute == null) {
            throw new UiException("Bindings.bindComputed: null arg");
        }
        compute.run();
        int i = 0;
        while (i < sources.size()) {
            ObservableValue<String> src = sources.get(i);
            ObsSubscription s = src.subscribe((ev) -> {
                if (scope != null && scope.isDisposed() != 0) {
                    return;
                }
                compute.run();
            });
            if (scope != null) {
                scope.addObs(s);
            }
            i = i + 1;
        }
        if (scope != null) {
            scope.addHandle(h);
        }
        return h;
    }
}

/** Field visual/edit state for validation UX. */
class FieldState {
    static int UNTOUCHED = 0;
    static int EDITING = 1;
    static int INVALID = 2;
    static int VALID = 3;
}

interface FieldRule {
    /** Return null or "" if ok; otherwise error message. */
    String validate(String raw);
}

class RequiredRule implements FieldRule {
    String message;

    RequiredRule(String msg) {
        message = msg;
        if (message == null || message.length() == 0) {
            message = "必填";
        }
    }

    String validate(String raw) {
        if (raw == null || raw.trim().length() == 0) {
            return message;
        }
        return null;
    }
}

class LengthRule implements FieldRule {
    int minLen;
    int maxLen;
    String message;

    LengthRule(int min, int max, String msg) {
        minLen = min;
        maxLen = max;
        message = msg;
        if (message == null) {
            message = "长度不符合要求";
        }
    }

    String validate(String raw) {
        int n = 0;
        if (raw != null) {
            n = raw.length();
        }
        if (n < minLen || n > maxLen) {
            return message;
        }
        return null;
    }
}

/**
 * Recommended base for composed (composite) controls built from existing widgets.
 * Contract: create/mount via {@link #root()}; bind inputs through Observables;
 * subscribe with existing BindingLib/UiLib APIs; participate in layout as a VBox;
 * enable/hide/focus via helpers; dispose cancels scope and child peers (idempotent).
 * Do not invent a parallel event system.
 */
class CompositeControl extends VBox {
    BindingScope scope;
    int alive;
    int gen;
    String accessibleTitle;

    CompositeControl() {
        Application.checkUiThread("CompositeControl.<init>");
        scope = new BindingScope();
        alive = 1;
        gen = 1;
        accessibleTitle = "";
        setSpacing(4);
        setPadding(0);
    }

    /** Mount point — this container. */
    Component root() {
        return this;
    }

    BindingScope scope() {
        return scope;
    }

    int isAlive() {
        return alive;
    }

    int generation() {
        return gen;
    }

    /** Primary focus target (override in subclasses). */
    Component focusTarget() {
        return this;
    }

    void setAccessibleTitle(String title) {
        ensureAlive("CompositeControl.setAccessibleTitle");
        if (title == null) {
            title = "";
        }
        accessibleTitle = title;
        setAccessibleName(title);
    }

    void setControlEnabled(boolean on) {
        ensureAlive("CompositeControl.setControlEnabled");
        setEnabled(on);
    }

    void setControlVisible(boolean on) {
        ensureAlive("CompositeControl.setControlVisible");
        setVisible(on);
    }

    /**
     * Tear down subscriptions and child peers. Idempotent.
     * Late async callbacks must check {@link #isAlive()} / {@link #generation()}.
     * (JNative has no {@code super.dispose()}; mirrors Container.dispose.)
     */
    void dispose() {
        Application.checkUiThread("CompositeControl.dispose");
        if (alive == 0) {
            return;
        }
        alive = 0;
        gen = gen + 1;
        if (scope != null) {
            scope.dispose();
            scope = null;
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
        disposeSelf("CompositeControl.dispose");
    }
}

/**
 * Numeric text field: raw string stays editable; parsed Decimal is separate.
 * Parse failure does NOT coerce to zero.
 */
class DecimalFieldModel {
    ObservableValue<String> raw;
    ObservableValue<String> error;
    ObservableInt state;
    Decimal parsed;
    int hasParsed;
    Decimal min;
    Decimal max;
    String rangeMessage;
    /** -1 = unlimited fraction digits after the decimal point. */
    int maxFractionDigits;
    int allowNegative;
    int allowEmpty;
    ArrayList<FieldRule> rules;

    DecimalFieldModel() {
        raw = ObservableValue.ofString("");
        error = ObservableValue.ofString("");
        state = new ObservableInt(FieldState.UNTOUCHED);
        parsed = null;
        hasParsed = 0;
        min = null;
        max = null;
        rangeMessage = "数值超出范围";
        maxFractionDigits = -1;
        allowNegative = 1;
        allowEmpty = 0;
        rules = new ArrayList<FieldRule>();
    }

    void setRange(Decimal lo, Decimal hi, String msg) {
        min = lo;
        max = hi;
        if (msg != null) {
            rangeMessage = msg;
        }
    }

    void setMaxFractionDigits(int n) {
        maxFractionDigits = n;
    }

    void setAllowNegative(int on) {
        allowNegative = on;
    }

    void setAllowEmpty(int on) {
        allowEmpty = on;
    }

    void addRule(FieldRule r) {
        if (r != null) {
            rules.add(r);
        }
    }

    void bind(TextField field, Label errorLabel, BindingScope scope) {
        Bindings.bindTextTwoWay(field, raw, scope);
        if (errorLabel != null) {
            /* Adjacent Theme.STYLE_ERROR label + AX title UI element for VoiceOver. */
            Bindings.bindLabel(errorLabel, error, scope);
            Bindings.associateErrorLabel(field, errorLabel);
        }
        ObsSubscription sub = raw.subscribe((ev) -> {
            state.set(FieldState.EDITING);
            revalidate(1);
        });
        if (scope != null) {
            scope.addObs(sub);
        }
    }

    /** soft=1 keeps EDITING on failure (while typing); soft=0 marks INVALID. */
    int revalidate(int soft) {
        String r = raw.get();
        if (r == null) {
            r = "";
        }
        int i = 0;
        while (i < rules.size()) {
            String msg = rules.get(i).validate(r);
            if (msg != null && msg.length() > 0) {
                fail(msg, soft);
                return 0;
            }
            i = i + 1;
        }
        String trimmed = r.trim();
        if (trimmed.length() == 0) {
            parsed = null;
            hasParsed = 0;
            error.set("");
            if (allowEmpty != 0) {
                state.set(FieldState.VALID);
                return 1;
            }
            if (soft == 0) {
                state.set(FieldState.INVALID);
                error.set("必填");
                return 0;
            }
            state.set(FieldState.EDITING);
            return 0;
        }
        if (allowNegative == 0 && trimmed.charAt(0) == 45) {
            fail("不允许负数", soft);
            return 0;
        }
        if (maxFractionDigits >= 0) {
            int dot = trimmed.indexOf(".");
            if (dot >= 0) {
                int frac = trimmed.length() - dot - 1;
                if (frac > maxFractionDigits) {
                    fail("小数位不能超过 " + String.valueOf(maxFractionDigits) + " 位", soft);
                    return 0;
                }
            }
        }
        try {
            Decimal d = Decimal.fromString(trimmed);
            if (min != null && d.compareTo(min) < 0) {
                fail(rangeMessage, soft);
                return 0;
            }
            if (max != null && d.compareTo(max) > 0) {
                fail(rangeMessage, soft);
                return 0;
            }
            parsed = d;
            hasParsed = 1;
            error.set("");
            state.set(FieldState.VALID);
            return 1;
        } catch (DecimalException ex) {
            parsed = null;
            hasParsed = 0;
            fail("请输入有效数字", soft);
            return 0;
        }
    }

    void fail(String msg, int soft) {
        parsed = null;
        hasParsed = 0;
        error.set(msg);
        if (soft != 0) {
            state.set(FieldState.EDITING);
        } else {
            state.set(FieldState.INVALID);
        }
    }

    int hasValue() {
        return hasParsed;
    }

    Decimal getParsed() {
        return parsed;
    }
}

class StringFieldModel {
    ObservableValue<String> raw;
    ObservableValue<String> error;
    ObservableInt state;
    ArrayList<FieldRule> rules;

    StringFieldModel() {
        raw = ObservableValue.ofString("");
        error = ObservableValue.ofString("");
        state = new ObservableInt(FieldState.UNTOUCHED);
        rules = new ArrayList<FieldRule>();
    }

    void addRule(FieldRule r) {
        if (r != null) {
            rules.add(r);
        }
    }

    void bind(TextField field, Label errorLabel, BindingScope scope) {
        Bindings.bindTextTwoWay(field, raw, scope);
        if (errorLabel != null) {
            Bindings.bindLabel(errorLabel, error, scope);
            Bindings.associateErrorLabel(field, errorLabel);
        }
        ObsSubscription sub = raw.subscribe((ev) -> {
            state.set(FieldState.EDITING);
            revalidate(1);
        });
        if (scope != null) {
            scope.addObs(sub);
        }
    }

    int revalidate(int soft) {
        String r = raw.get();
        if (r == null) {
            r = "";
        }
        int i = 0;
        while (i < rules.size()) {
            String msg = rules.get(i).validate(r);
            if (msg != null && msg.length() > 0) {
                error.set(msg);
                if (soft != 0) {
                    state.set(FieldState.EDITING);
                } else {
                    state.set(FieldState.INVALID);
                }
                return 0;
            }
            i = i + 1;
        }
        error.set("");
        state.set(FieldState.VALID);
        return 1;
    }
}

interface CrossFieldRule {
    String validate();
}

class FormModel {
    ArrayList<StringFieldModel> stringFields;
    ArrayList<DecimalFieldModel> decimalFields;
    ArrayList<CrossFieldRule> crossRules;
    ObservableValue<String> formError;
    ObservableInt canSubmit;
    ObservableValue<String> serverError;

    FormModel() {
        stringFields = new ArrayList<StringFieldModel>();
        decimalFields = new ArrayList<DecimalFieldModel>();
        crossRules = new ArrayList<CrossFieldRule>();
        formError = ObservableValue.ofString("");
        canSubmit = new ObservableInt(0);
        serverError = ObservableValue.ofString("");
    }

    void addString(StringFieldModel f) {
        stringFields.add(f);
    }

    void addDecimal(DecimalFieldModel f) {
        decimalFields.add(f);
    }

    void addCross(CrossFieldRule r) {
        if (r != null) {
            crossRules.add(r);
        }
    }

    /** Full validate for submit. Returns 1 if ok. */
    int validateSubmit() {
        serverError.set("");
        int ok = 1;
        int i = 0;
        while (i < stringFields.size()) {
            if (stringFields.get(i).revalidate(0) == 0) {
                ok = 0;
            }
            i = i + 1;
        }
        i = 0;
        while (i < decimalFields.size()) {
            if (decimalFields.get(i).revalidate(0) == 0) {
                ok = 0;
            }
            i = i + 1;
        }
        i = 0;
        while (i < crossRules.size()) {
            String msg = crossRules.get(i).validate();
            if (msg != null && msg.length() > 0) {
                formError.set(msg);
                ok = 0;
            }
            i = i + 1;
        }
        if (ok != 0) {
            formError.set("");
        }
        canSubmit.set(ok);
        return ok;
    }

    void setServerError(String msg) {
        if (msg == null) {
            msg = "";
        }
        serverError.set(msg);
        if (msg.length() > 0) {
            canSubmit.set(0);
        }
    }

    void refreshCanSubmitSoft() {
        int ok = 1;
        int i = 0;
        while (i < stringFields.size()) {
            if (stringFields.get(i).state.get() == FieldState.INVALID) {
                ok = 0;
            }
            i = i + 1;
        }
        i = 0;
        while (i < decimalFields.size()) {
            if (decimalFields.get(i).state.get() == FieldState.INVALID) {
                ok = 0;
            }
            i = i + 1;
        }
        canSubmit.set(ok);
    }
}
