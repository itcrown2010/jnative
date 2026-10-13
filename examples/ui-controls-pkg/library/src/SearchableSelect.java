/**
 * Searchable select: filter + list + keyboard. Stable id selection.
 * Async filter results use generation tokens; dispose cancels late updates.
 * Author: 张晓达
 *
 * Limits: filter runs on UI thread by default; optional background filter via
 * {@link #setAsyncFilter}. Relies on current generics + Thread/invokeLater only.
 * ListView displays strings; option payloads stay in {@link #viewOptions}.
 */
class SearchableSelect<T> extends CompositeControl {
    TextField queryField;
    ListView<String> list;
    Label statusLabel;
    Label captionLabel;
    ObservableValue<String> selectedId;
    ObservableValue<String> queryText;
    ArrayList<SelectOption<T>> allOptions;
    ArrayList<SelectOption<T>> viewOptions;
    int highlight;
    int loading;
    int searchGen;
    SearchableSelectFilter<T> asyncFilter;

    SearchableSelect() {
        this("");
    }

    SearchableSelect(String caption) {
        Application.checkUiThread("SearchableSelect.<init>");
        selectedId = ObservableValue.ofString("");
        queryText = ObservableValue.ofString("");
        allOptions = new ArrayList<SelectOption<T>>();
        viewOptions = new ArrayList<SelectOption<T>>();
        highlight = -1;
        loading = 0;
        searchGen = 0;
        asyncFilter = null;
        captionLabel = new Label(caption == null ? "" : caption);
        Theme.applyStyle(captionLabel, Theme.STYLE_SECONDARY);
        queryField = new TextField("");
        queryField.setPrefSize(240, 28);
        queryField.setAccessibleName(caption == null || caption.length() == 0 ? "搜索选择" : caption);
        statusLabel = new Label("");
        Theme.applyStyle(statusLabel, Theme.STYLE_SECONDARY);
        statusLabel.setAccessibleName("候选项状态");
        list = new ListView<String>();
        list.setPrefSize(240, 140);
        list.setAccessibleName("候选项");
        list.onSelectionChanged((ev) -> {
            if (alive == 0 || ev == null) {
                return;
            }
            int idx = ev.selectedIndex;
            if (idx >= 0 && idx < viewOptions.size()) {
                applySelection(viewOptions.get(idx));
            }
        });
        Bindings.bindTextTwoWay(queryField, queryText, scope);
        scope.addObs(queryText.subscribe((ev) -> {
            if (alive == 0) {
                return;
            }
            scheduleFilter();
        }));
        queryField.onKeyDown((ev) -> {
            onQueryKey(ev);
        });
        if (caption != null && caption.length() > 0) {
            add(captionLabel);
            setAccessibleTitle(caption);
        } else {
            setAccessibleTitle("搜索选择");
        }
        add(queryField);
        add(statusLabel);
        add(list);
        setStatus("输入以筛选");
    }

    ObservableValue<String> selectedId() {
        return selectedId;
    }

    ObservableValue<String> query() {
        return queryText;
    }

    public Component focusTarget() {
        return queryField;
    }

    TextField queryField() {
        return queryField;
    }

    void setOptions(ArrayList<SelectOption<T>> options) {
        ensureAlive("SearchableSelect.setOptions");
        allOptions = new ArrayList<SelectOption<T>>();
        if (options != null) {
            int i = 0;
            while (i < options.size()) {
                SelectOption<T> o = options.get(i);
                if (o != null) {
                    allOptions.add(o);
                }
                i = i + 1;
            }
        }
        scheduleFilter();
    }

    void setAsyncFilter(SearchableSelectFilter<T> filter) {
        ensureAlive("SearchableSelect.setAsyncFilter");
        asyncFilter = filter;
    }

    void setSelectedId(String id) {
        ensureAlive("SearchableSelect.setSelectedId");
        if (id == null) {
            id = "";
        }
        selectedId.set(id);
        syncQueryFromSelection();
    }

    String getSelectedId() {
        return selectedId.get();
    }

    SelectOption<T> getSelectedOption() {
        String id = selectedId.get();
        if (id == null || id.length() == 0) {
            return null;
        }
        int i = 0;
        while (i < allOptions.size()) {
            SelectOption<T> o = allOptions.get(i);
            if (o != null && id.equals(o.id)) {
                return o;
            }
            i = i + 1;
        }
        return null;
    }

    void clearSelection() {
        ensureAlive("SearchableSelect.clearSelection");
        selectedId.set("");
        queryText.set("");
        highlight = -1;
        scheduleFilter();
    }

    void scheduleFilter() {
        searchGen = searchGen + 1;
        int token = searchGen;
        String q = queryText.get();
        if (q == null) {
            q = "";
        }
        if (asyncFilter != null) {
            loading = 1;
            setStatus("加载中…");
            String query = q;
            Thread t = new Thread(() -> {
                ArrayList<SelectOption<T>> result = null;
                String err = null;
                try {
                    result = asyncFilter.filter(query, allOptions);
                } catch (Exception ex) {
                    err = "加载失败";
                }
                ArrayList<SelectOption<T>> captured = result;
                String errMsg = err;
                Application.invokeLater(() -> {
                    if (alive == 0 || token != searchGen) {
                        return;
                    }
                    loading = 0;
                    if (errMsg != null) {
                        viewOptions = new ArrayList<SelectOption<T>>();
                        pushListLabels();
                        setStatus(errMsg);
                        return;
                    }
                    applyView(captured, query);
                });
            });
            t.start();
            return;
        }
        applyView(filterLocal(q), q);
    }

    ArrayList<SelectOption<T>> filterLocal(String q) {
        ArrayList<SelectOption<T>> out = new ArrayList<SelectOption<T>>();
        String needle = q.trim();
        int i = 0;
        while (i < allOptions.size()) {
            SelectOption<T> o = allOptions.get(i);
            if (o != null) {
                if (needle.length() == 0
                        || containsIgnoreCase(o.label, needle) != 0
                        || containsIgnoreCase(o.id, needle) != 0) {
                    out.add(o);
                }
            }
            i = i + 1;
        }
        return out;
    }

    void applyView(ArrayList<SelectOption<T>> rows, String q) {
        if (rows == null) {
            rows = new ArrayList<SelectOption<T>>();
        }
        viewOptions = rows;
        pushListLabels();
        highlight = -1;
        if (allOptions.size() == 0) {
            setStatus("无选项");
        } else if (viewOptions.size() == 0) {
            setStatus("无结果");
        } else {
            setStatus(String.valueOf(viewOptions.size()) + " 项");
        }
    }

    void pushListLabels() {
        ArrayList<String> labels = new ArrayList<String>();
        int i = 0;
        while (i < viewOptions.size()) {
            SelectOption<T> o = viewOptions.get(i);
            if (o == null) {
                labels.add("");
            } else {
                labels.add(o.label + "  (" + o.id + ")");
            }
            i = i + 1;
        }
        list.setItems(labels);
    }

    void applySelection(SelectOption<T> opt) {
        if (opt == null) {
            return;
        }
        selectedId.set(opt.id);
        queryText.set(opt.label);
        setStatus("已选：" + opt.label);
    }

    void syncQueryFromSelection() {
        SelectOption<T> o = getSelectedOption();
        if (o != null) {
            queryText.set(o.label);
        }
    }

    void onQueryKey(KeyEvent ev) {
        if (alive == 0 || ev == null) {
            return;
        }
        if (ev.keyCode == KeyCode.ESCAPE) {
            clearSelection();
            ev.consumed = 1;
            return;
        }
        if (ev.keyCode == KeyCode.DOWN) {
            moveHighlight(1);
            ev.consumed = 1;
            return;
        }
        if (ev.keyCode == KeyCode.UP) {
            moveHighlight(0 - 1);
            ev.consumed = 1;
            return;
        }
        if (ev.keyCode == KeyCode.ENTER) {
            if (highlight >= 0 && highlight < viewOptions.size()) {
                applySelection(viewOptions.get(highlight));
                list.select(highlight);
            }
            ev.consumed = 1;
        }
    }

    void moveHighlight(int delta) {
        if (viewOptions.size() == 0) {
            return;
        }
        if (highlight < 0) {
            highlight = 0;
        } else {
            highlight = highlight + delta;
            if (highlight < 0) {
                highlight = 0;
            }
            if (highlight >= viewOptions.size()) {
                highlight = viewOptions.size() - 1;
            }
        }
        list.select(highlight);
    }

    void setStatus(String s) {
        if (statusLabel == null || statusLabel.disposed != 0) {
            return;
        }
        if (s == null) {
            s = "";
        }
        statusLabel.setText(s);
    }

    static int containsIgnoreCase(String hay, String needle) {
        if (hay == null || needle == null) {
            return 0;
        }
        String a = hay;
        String b = needle;
        int n = a.length();
        int m = b.length();
        if (m == 0) {
            return 1;
        }
        int i = 0;
        while (i + m <= n) {
            int ok = 1;
            int j = 0;
            while (j < m) {
                int ca = a.charAt(i + j);
                int cb = b.charAt(j);
                if (ca >= 65 && ca <= 90) {
                    ca = ca + 32;
                }
                if (cb >= 65 && cb <= 90) {
                    cb = cb + 32;
                }
                if (ca != cb) {
                    ok = 0;
                    break;
                }
                j = j + 1;
            }
            if (ok != 0) {
                return 1;
            }
            i = i + 1;
        }
        return 0;
    }
}

interface SearchableSelectFilter<T> {
    ArrayList<SelectOption<T>> filter(String query, ArrayList<SelectOption<T>> all);
}
