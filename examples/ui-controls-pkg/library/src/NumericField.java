/**
 * Composed numeric input: raw text + validated Decimal (never silent-coerce to 0).
 * Built on CompositeControl + DecimalFieldModel + TextField. Author: 张晓达
 *
 * Platform: macOS Cocoa (same as UiLib). Not claimed for Win/Linux until verified.
 */
class NumericField extends CompositeControl {
    TextField field;
    Label errorLabel;
    Label captionLabel;
    DecimalFieldModel model;
    ObservableInt valueChangedTick;

    NumericField() {
        this("");
    }

    NumericField(String caption) {
        Application.checkUiThread("NumericField.<init>");
        model = new DecimalFieldModel();
        valueChangedTick = new ObservableInt(0);
        captionLabel = new Label(caption == null ? "" : caption);
        Theme.applyStyle(captionLabel, Theme.STYLE_SECONDARY);
        field = new TextField("");
        field.setPrefSize(220, 28);
        errorLabel = new Label("");
        Theme.applyStyle(errorLabel, Theme.STYLE_ERROR);
        model.bind(field, errorLabel, scope);
        scope.addObs(model.raw.subscribe((ev) -> {
            if (alive == 0) {
                return;
            }
            valueChangedTick.set(valueChangedTick.get() + 1);
        }));
        if (caption != null && caption.length() > 0) {
            add(captionLabel);
            setAccessibleTitle(caption);
            field.setAccessibleName(caption);
        } else {
            setAccessibleTitle("数值");
            field.setAccessibleName("数值");
        }
        errorLabel.setAccessibleName("数值错误");
        add(field);
        add(errorLabel);
    }

    DecimalFieldModel model() {
        return model;
    }

    TextField textField() {
        return field;
    }

    Label errorLabel() {
        return errorLabel;
    }

    /** Fires (increments) when raw text changes — use with BindingScope. */
    ObservableInt valueChanged() {
        return valueChangedTick;
    }

    public Component focusTarget() {
        return field;
    }

    void setRange(Decimal lo, Decimal hi, String msg) {
        ensureAlive("NumericField.setRange");
        model.setRange(lo, hi, msg);
    }

    void setMaxFractionDigits(int n) {
        ensureAlive("NumericField.setMaxFractionDigits");
        model.setMaxFractionDigits(n);
    }

    void setAllowNegative(boolean on) {
        ensureAlive("NumericField.setAllowNegative");
        if (on) {
            model.setAllowNegative(1);
        } else {
            model.setAllowNegative(0);
        }
    }

    void setAllowEmpty(boolean on) {
        ensureAlive("NumericField.setAllowEmpty");
        if (on) {
            model.setAllowEmpty(1);
        } else {
            model.setAllowEmpty(0);
        }
    }

    /** Empty input invalid on submit ({@code soft=0}); while typing use soft revalidate. */
    void setRequired(boolean on) {
        ensureAlive("NumericField.setRequired");
        if (on) {
            model.setAllowEmpty(0);
        } else {
            model.setAllowEmpty(1);
        }
    }

    void setRaw(String text) {
        ensureAlive("NumericField.setRaw");
        if (text == null) {
            text = "";
        }
        model.raw.set(text);
    }

    String getRaw() {
        return model.raw.get();
    }

    int hasValue() {
        return model.hasValue();
    }

    Decimal getParsed() {
        return model.getParsed();
    }

    String getError() {
        return model.error.get();
    }

    int revalidate(int soft) {
        ensureAlive("NumericField.revalidate");
        return model.revalidate(soft);
    }

    public void setControlEnabled(boolean on) {
        ensureAlive("NumericField.setControlEnabled");
        field.setEnabled(on);
        setEnabled(on);
    }
}
