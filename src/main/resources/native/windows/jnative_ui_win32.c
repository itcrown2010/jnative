/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Author: 张晓达 */
/* Win32 UI backend (0.17/0.18). CreateWindowEx + common controls. */
#ifndef UNICODE
#define UNICODE
#endif
#ifndef _UNICODE
#define _UNICODE
#endif
#include <windows.h>
#include <commctrl.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include "jnative_ui.h"

#ifndef JN_UI_MAX
#define JN_UI_MAX 4096
#endif

static HINSTANCE g_inst;
static HWND g_hwnds[JN_UI_MAX];
static int g_kinds[JN_UI_MAX];
static int g_selected[JN_UI_MAX];
static int g_selected_index[JN_UI_MAX];
static int g_item_count[JN_UI_MAX];
static int g_visible_rows[JN_UI_MAX];
static double g_progress[JN_UI_MAX];
static double g_divider[JN_UI_MAX];
static int g_event_a0, g_event_a1, g_event_a2, g_event_a3;
static int g_dialog_result = 1;
static char *g_file_result = NULL;
static int g_live_handles = 0;
static int g_events_dispatched = 0;
static int g_win32_active = 0;
static jn_ui_dispatch_fn g_dispatch = NULL;
static int g_live_windows = 0;
static int g_live_controls = 0;
static int g_task_queue = 0;
static int g_quit = 0;
static const wchar_t *JN_WCLASS = L"JNativeWindow";

static wchar_t *utf8_to_wide(const char *utf8) {
    if (utf8 == NULL) {
        utf8 = "";
    }
    int n = MultiByteToWideChar(CP_UTF8, 0, utf8, -1, NULL, 0);
    if (n <= 0) {
        return _wcsdup(L"");
    }
    wchar_t *w = (wchar_t *)malloc(sizeof(wchar_t) * (size_t)n);
    MultiByteToWideChar(CP_UTF8, 0, utf8, -1, w, n);
    return w;
}

static UINT_PTR g_timers[JN_UI_MAX];

static LRESULT CALLBACK JnWndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    int32_t handleId = (int32_t)(intptr_t)GetWindowLongPtr(hwnd, GWLP_USERDATA);
    if (msg == WM_COMMAND) {
        HWND ctrl = (HWND)lParam;
        int32_t cid = (int32_t)(intptr_t)GetWindowLongPtr(ctrl, GWLP_USERDATA);
        if (cid > 0 && HIWORD(wParam) == BN_CLICKED) {
            jnative_ui_emit_event(cid, JN_UI_EVENT_CLICK);
        }
        if (cid > 0 && (HIWORD(wParam) == EN_CHANGE || HIWORD(wParam) == CBN_SELCHANGE)) {
            jnative_ui_emit_event(cid, JN_UI_EVENT_CHANGE);
        }
        return 0;
    }
    if (msg == WM_NOTIFY) {
        LPNMHDR hdr = (LPNMHDR)lParam;
        if (hdr != NULL) {
            int32_t cid = (int32_t)(intptr_t)GetWindowLongPtr(hdr->hwndFrom, GWLP_USERDATA);
            if (cid > 0) {
                if (hdr->code == LVN_ITEMCHANGED) {
                    NMLISTVIEW *lv = (NMLISTVIEW *)lParam;
                    if ((lv->uChanged & LVIF_STATE) != 0
                            && (lv->uNewState & LVIS_SELECTED) != 0) {
                        g_selected_index[cid] = lv->iItem;
                        jnative_ui_emit_event_ex(cid, JN_UI_EVENT_SELECT, lv->iItem, 0, 0, 0);
                    }
                } else if (hdr->code == NM_DBLCLK) {
                    jnative_ui_emit_event(cid, JN_UI_EVENT_ACTION);
                } else if (hdr->code == LVN_COLUMNCLICK) {
                    NMLISTVIEW *lv = (NMLISTVIEW *)lParam;
                    jnative_ui_emit_event_ex(cid, JN_UI_EVENT_HEADER, lv->iSubItem, 0, 0, 0);
                }
            }
        }
        return 0;
    }
    if (msg == WM_SIZE) {
        if (handleId > 0) {
            jnative_ui_emit_event_ex(handleId, JN_UI_EVENT_RESIZE,
                    (int32_t)LOWORD(lParam), (int32_t)HIWORD(lParam), 0, 0);
        }
        return 0;
    }
    if (msg == WM_TIMER) {
        int32_t tid = (int32_t)wParam;
        if (tid > 0 && tid < JN_UI_MAX) {
            jnative_ui_emit_event(tid, JN_UI_EVENT_TIMER);
        }
        return 0;
    }
    if (msg == WM_CLOSE) {
        if (handleId > 0) {
            jnative_ui_emit_event(handleId, JN_UI_EVENT_CLOSE);
        }
        DestroyWindow(hwnd);
        return 0;
    }
    if (msg == WM_DESTROY) {
        if (g_live_windows <= 0) {
            PostQuitMessage(0);
        }
        return 0;
    }
    if (msg == WM_APP + 1) {
        if (g_task_queue > 0) {
            g_task_queue--;
        }
        jnative_ui_emit_event(0, JN_UI_EVENT_TASK);
        return 0;
    }
    return DefWindowProcW(hwnd, msg, wParam, lParam);
}

/* Common entry points used when only Win32 file is linked (Windows path). */
void jnative_ui_emit_event_ex(int32_t handleId, int32_t eventCode,
                              int32_t a0, int32_t a1, int32_t a2, int32_t a3) {
    g_event_a0 = a0; g_event_a1 = a1; g_event_a2 = a2; g_event_a3 = a3;
    if (g_dispatch != NULL) {
        g_events_dispatched++;
        (void)g_dispatch(handleId, eventCode);
    }
}
void jnative_ui_emit_event(int32_t handleId, int32_t eventCode) {
    jnative_ui_emit_event_ex(handleId, eventCode, 0, 0, 0, 0);
}
int32_t jnative_ui_event_arg0(void) { return g_event_a0; }
int32_t jnative_ui_event_arg1(void) { return g_event_a1; }
int32_t jnative_ui_event_arg2(void) { return g_event_a2; }
int32_t jnative_ui_event_arg3(void) { return g_event_a3; }

int32_t jnative_ui_platform_available(void) {
    return g_win32_active ? 1 : 0;
}

void jnative_ui_init(int32_t headless) {
    const char *env = getenv("JNATIVE_HEADLESS");
    int hl = headless != 0 || (env != NULL && env[0] != '\0' && env[0] != '0');
    g_quit = 0;
    g_task_queue = 0;
    memset(g_timers, 0, sizeof(g_timers));
    if (hl) {
        g_win32_active = 0;
        return;
    }
#if defined(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2)
    SetProcessDpiAwarenessContext(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2);
#endif
    g_inst = GetModuleHandleW(NULL);
    INITCOMMONCONTROLSEX icc;
    memset(&icc, 0, sizeof(icc));
    icc.dwSize = sizeof(icc);
    icc.dwICC = ICC_WIN95_CLASSES | ICC_LISTVIEW_CLASSES | ICC_TREEVIEW_CLASSES | ICC_TAB_CLASSES | ICC_PROGRESS_CLASS;
    InitCommonControlsEx(&icc);
    WNDCLASSW wc;
    memset(&wc, 0, sizeof(wc));
    wc.lpfnWndProc = JnWndProc;
    wc.hInstance = g_inst;
    wc.lpszClassName = JN_WCLASS;
    wc.hCursor = LoadCursor(NULL, IDC_ARROW);
    wc.hbrBackground = (HBRUSH)(COLOR_WINDOW + 1);
    RegisterClassW(&wc);
    g_win32_active = 1;
}

void jnative_ui_set_dispatcher(void *fn) {
    g_dispatch = (jn_ui_dispatch_fn)fn;
}

void jnative_ui_post_task(void) {
    g_task_queue++;
    if (g_win32_active) {
        PostMessageW(HWND_BROADCAST, WM_APP + 1, 0, 0);
    }
}

void jnative_ui_quit(void) {
    g_quit = 1;
    PostQuitMessage(0);
}

void jnative_ui_run_loop(void) {
    if (!g_win32_active) {
        while (!g_quit && g_task_queue > 0) {
            g_task_queue--;
            jnative_ui_emit_event(0, JN_UI_EVENT_TASK);
        }
        return;
    }
    MSG msg;
    while (!g_quit && GetMessageW(&msg, NULL, 0, 0) > 0) {
        TranslateMessage(&msg);
        DispatchMessageW(&msg);
    }
}

int32_t jnative_ui_create_window(const char *title, int32_t width, int32_t height) {
    static int nextId = 1;
    int id = nextId++;
    if (id >= JN_UI_MAX) {
        return -1;
    }
    g_kinds[id] = JN_UI_KIND_WINDOW;
    if (!g_win32_active) {
        g_live_windows++;
        return id;
    }
    wchar_t *wtitle = utf8_to_wide(title);
    HWND hwnd = CreateWindowExW(
            0, JN_WCLASS, wtitle, WS_OVERLAPPEDWINDOW,
            CW_USEDEFAULT, CW_USEDEFAULT, width, height,
            NULL, NULL, g_inst, NULL);
    free(wtitle);
    if (hwnd == NULL) {
        return -1;
    }
    SetWindowLongPtr(hwnd, GWLP_USERDATA, (LONG_PTR)id);
    g_hwnds[id] = hwnd;
    g_live_windows++;
    return id;
}

static int32_t create_ctrl(int kind, int parentId, const char *text, const wchar_t *cls, DWORD style) {
    static int nextId = 1000;
    int id = nextId++;
    if (id >= JN_UI_MAX) {
        return -1;
    }
    g_kinds[id] = kind;
    g_live_controls++;
    if (!g_win32_active) {
        return id;
    }
    HWND parent = (parentId > 0 && parentId < JN_UI_MAX) ? g_hwnds[parentId] : NULL;
    wchar_t *wtext = utf8_to_wide(text);
    HWND hwnd = CreateWindowExW(
            0, cls, wtext, style | WS_CHILD | WS_VISIBLE,
            10, 10, 120, 28, parent, NULL, g_inst, NULL);
    free(wtext);
    if (hwnd != NULL) {
        SetWindowLongPtr(hwnd, GWLP_USERDATA, (LONG_PTR)id);
        g_hwnds[id] = hwnd;
    }
    return id;
}

int32_t jnative_ui_create_button(int32_t parentId, const char *text) {
    return create_ctrl(JN_UI_KIND_BUTTON, parentId, text, L"BUTTON", BS_PUSHBUTTON);
}
int32_t jnative_ui_create_label(int32_t parentId, const char *text) {
    return create_ctrl(JN_UI_KIND_LABEL, parentId, text, L"STATIC", SS_LEFT);
}
int32_t jnative_ui_create_textfield(int32_t parentId, const char *text) {
    return create_ctrl(JN_UI_KIND_TEXTFIELD, parentId, text, L"EDIT", ES_LEFT | ES_AUTOHSCROLL);
}
int32_t jnative_ui_create_panel(int32_t parentId) {
    return create_ctrl(JN_UI_KIND_PANEL, parentId, "", L"STATIC", SS_LEFT);
}

int32_t jnative_ui_create_control(int32_t kind, int32_t parentId, const char *text) {
    switch (kind) {
        case JN_UI_KIND_BUTTON: return jnative_ui_create_button(parentId, text);
        case JN_UI_KIND_LABEL: return jnative_ui_create_label(parentId, text);
        case JN_UI_KIND_TEXTFIELD: return jnative_ui_create_textfield(parentId, text);
        case JN_UI_KIND_PANEL: return jnative_ui_create_panel(parentId);
        case JN_UI_KIND_CHECKBOX:
            return create_ctrl(kind, parentId, text, L"BUTTON", BS_AUTOCHECKBOX);
        case JN_UI_KIND_RADIO:
            return create_ctrl(kind, parentId, text, L"BUTTON", BS_AUTORADIOBUTTON);
        case JN_UI_KIND_COMBOBOX:
            return create_ctrl(kind, parentId, text, L"COMBOBOX", CBS_DROPDOWNLIST | WS_VSCROLL);
        case JN_UI_KIND_TEXTAREA:
            return create_ctrl(kind, parentId, text, L"EDIT", ES_LEFT | ES_MULTILINE | ES_AUTOVSCROLL | WS_VSCROLL);
        case JN_UI_KIND_PROGRESS:
            return create_ctrl(kind, parentId, text, PROGRESS_CLASSW, PBS_SMOOTH);
        case JN_UI_KIND_LISTVIEW:
        case JN_UI_KIND_TABLEVIEW:
            return create_ctrl(kind, parentId, text, WC_LISTVIEWW, LVS_REPORT | LVS_SINGLESEL | WS_BORDER);
        case JN_UI_KIND_TREEVIEW:
            return create_ctrl(kind, parentId, text, WC_TREEVIEWW, TVS_HASLINES | TVS_LINESATROOT | TVS_HASBUTTONS);
        case JN_UI_KIND_TABPANE:
            return create_ctrl(kind, parentId, text, WC_TABCONTROLW, 0);
        case JN_UI_KIND_SCROLL:
        case JN_UI_KIND_SPLIT:
            return create_ctrl(kind, parentId, text, L"STATIC", SS_LEFT);
        case JN_UI_KIND_MENUITEM:
        case JN_UI_KIND_SEPARATOR:
            return create_ctrl(kind, parentId, text, L"STATIC", SS_LEFT);
        default:
            return create_ctrl(kind, parentId, text, L"STATIC", SS_LEFT);
    }
}

void jnative_ui_destroy(int32_t id) {
    if (id <= 0 || id >= JN_UI_MAX) {
        return;
    }
    HWND hwnd = g_hwnds[id];
    g_hwnds[id] = NULL;
    if (hwnd != NULL) {
        DestroyWindow(hwnd);
    }
    if (g_kinds[id] == JN_UI_KIND_WINDOW) {
        g_live_windows--;
    } else if (g_kinds[id] != 0) {
        g_live_controls--;
    }
    g_kinds[id] = 0;
}

void jnative_ui_show(int32_t id) {
    if (id > 0 && id < JN_UI_MAX && g_hwnds[id] != NULL) {
        ShowWindow(g_hwnds[id], SW_SHOW);
        UpdateWindow(g_hwnds[id]);
    }
}
void jnative_ui_hide(int32_t id) {
    if (id > 0 && id < JN_UI_MAX && g_hwnds[id] != NULL) {
        ShowWindow(g_hwnds[id], SW_HIDE);
    }
}
void jnative_ui_set_title(int32_t id, const char *title) {
    if (id > 0 && id < JN_UI_MAX && g_hwnds[id] != NULL) {
        wchar_t *w = utf8_to_wide(title);
        SetWindowTextW(g_hwnds[id], w);
        free(w);
    }
}
void jnative_ui_set_text(int32_t id, const char *text) {
    jnative_ui_set_title(id, text);
}
void jnative_ui_set_bounds(int32_t id, int32_t x, int32_t y, int32_t w, int32_t h) {
    if (id > 0 && id < JN_UI_MAX && g_hwnds[id] != NULL) {
        MoveWindow(g_hwnds[id], x, y, w, h, TRUE);
    }
}
void jnative_ui_set_visible(int32_t id, int32_t visible) {
    if (visible) {
        jnative_ui_show(id);
    } else {
        jnative_ui_hide(id);
    }
}
void jnative_ui_set_enabled(int32_t id, int32_t enabled) {
    if (id > 0 && id < JN_UI_MAX && g_hwnds[id] != NULL) {
        EnableWindow(g_hwnds[id], enabled != 0);
    }
}
void jnative_ui_set_content(int32_t windowId, int32_t childId) {
    if (!g_win32_active) {
        return;
    }
    if (windowId <= 0 || windowId >= JN_UI_MAX || childId <= 0 || childId >= JN_UI_MAX) {
        return;
    }
    HWND parent = g_hwnds[windowId];
    HWND child = g_hwnds[childId];
    if (parent == NULL || child == NULL) {
        return;
    }
    SetParent(child, parent);
    RECT rc;
    GetClientRect(parent, &rc);
    MoveWindow(child, 0, 0, rc.right - rc.left, rc.bottom - rc.top, TRUE);
    ShowWindow(child, SW_SHOW);
}

void jnative_ui_reparent(int32_t childId, int32_t parentId) {
    if (!g_win32_active) {
        return;
    }
    if (childId <= 0 || childId >= JN_UI_MAX || parentId <= 0 || parentId >= JN_UI_MAX) {
        return;
    }
    HWND parent = g_hwnds[parentId];
    HWND child = g_hwnds[childId];
    if (parent == NULL || child == NULL) {
        return;
    }
    SetParent(child, parent);
}
int32_t jnative_ui_live_windows(void) { return g_live_windows; }
int32_t jnative_ui_live_controls(void) { return g_live_controls; }
int32_t jnative_ui_live_handles(void) { return g_live_windows + g_live_controls; }
int32_t jnative_ui_queued_tasks(void) { return g_task_queue; }
int32_t jnative_ui_events_dispatched(void) { return g_events_dispatched; }

void jnative_ui_set_selected(int32_t id, int32_t selected) {
    if (id > 0 && id < JN_UI_MAX) g_selected[id] = selected != 0;
}
int32_t jnative_ui_get_selected(int32_t id) {
    return (id > 0 && id < JN_UI_MAX) ? g_selected[id] : 0;
}
void jnative_ui_set_value_i32(int32_t id, int32_t value) { (void)id; (void)value; }
int32_t jnative_ui_get_value_i32(int32_t id) { (void)id; return 0; }
void jnative_ui_set_progress(int32_t id, double progress) {
    if (id > 0 && id < JN_UI_MAX) g_progress[id] = progress;
}
double jnative_ui_get_progress(int32_t id) {
    return (id > 0 && id < JN_UI_MAX) ? g_progress[id] : 0.0;
}
void jnative_ui_set_item_count(int32_t id, int32_t count) {
    if (id > 0 && id < JN_UI_MAX) g_item_count[id] = count;
}
int32_t jnative_ui_get_item_count(int32_t id) {
    return (id > 0 && id < JN_UI_MAX) ? g_item_count[id] : 0;
}
const char *jnative_ui_item_text(int32_t id, int32_t index) {
    (void)id; (void)index; return "";
}
int32_t jnative_ui_item_parent(int32_t id, int32_t index) {
    (void)id; (void)index; return -1;
}
void jnative_ui_table_set_columns(int32_t id, const char *titles_tsv) {
    if (id <= 0 || id >= JN_UI_MAX || g_hwnds[id] == NULL || !g_win32_active) {
        return;
    }
    HWND lv = g_hwnds[id];
    while (ListView_DeleteColumn(lv, 0)) {
    }
    if (titles_tsv == NULL || titles_tsv[0] == '\0') {
        return;
    }
    char *copy = _strdup(titles_tsv);
    if (copy == NULL) {
        return;
    }
    int col = 0;
    char *p = copy;
    while (p != NULL) {
        char *tab = strchr(p, '\t');
        if (tab != NULL) {
            *tab = '\0';
        }
        wchar_t *w = utf8_to_wide(p);
        LVCOLUMNW lc;
        memset(&lc, 0, sizeof(lc));
        lc.mask = LVCF_TEXT | LVCF_WIDTH | LVCF_FMT;
        lc.pszText = w;
        lc.cx = (col == 0) ? 220 : 100;
        lc.fmt = (col == 0) ? LVCFMT_LEFT : LVCFMT_RIGHT;
        ListView_InsertColumn(lv, col, &lc);
        free(w);
        col++;
        p = (tab == NULL) ? NULL : (tab + 1);
    }
    free(copy);
    ListView_SetExtendedListViewStyle(lv,
            LVS_EX_FULLROWSELECT | LVS_EX_DOUBLEBUFFER | LVS_EX_LABELTIP);
}
const char *jnative_ui_table_columns(int32_t id) {
    (void)id; return "";
}
void jnative_ui_set_selected_index(int32_t id, int32_t index) {
    if (id > 0 && id < JN_UI_MAX) g_selected_index[id] = index;
}
int32_t jnative_ui_get_selected_index(int32_t id) {
    return (id > 0 && id < JN_UI_MAX) ? g_selected_index[id] : -1;
}
void jnative_ui_set_visible_rows(int32_t id, int32_t rows) {
    if (id > 0 && id < JN_UI_MAX) g_visible_rows[id] = rows;
}
int32_t jnative_ui_get_visible_rows(int32_t id) {
    return (id > 0 && id < JN_UI_MAX) ? g_visible_rows[id] : 0;
}
void jnative_ui_combo_clear(int32_t id) {
    if (id > 0 && id < JN_UI_MAX) {
        g_item_count[id] = 0;
        if (g_win32_active && g_hwnds[id] != NULL && g_kinds[id] == JN_UI_KIND_COMBOBOX) {
            SendMessageW(g_hwnds[id], CB_RESETCONTENT, 0, 0);
        }
    }
}
void jnative_ui_combo_add_item(int32_t id, const char *text) {
    if (id <= 0 || id >= JN_UI_MAX) {
        return;
    }
    g_item_count[id]++;
    if (g_win32_active && g_hwnds[id] != NULL && g_kinds[id] == JN_UI_KIND_COMBOBOX) {
        wchar_t *w = utf8_to_wide(text);
        SendMessageW(g_hwnds[id], CB_ADDSTRING, 0, (LPARAM)w);
        free(w);
    }
}
void jnative_ui_list_clear(int32_t id) {
    if (id <= 0 || id >= JN_UI_MAX) {
        return;
    }
    g_item_count[id] = 0;
    if (g_win32_active && g_hwnds[id] != NULL
            && (g_kinds[id] == JN_UI_KIND_LISTVIEW || g_kinds[id] == JN_UI_KIND_TABLEVIEW)) {
        ListView_DeleteAllItems(g_hwnds[id]);
    }
}
void jnative_ui_list_add_item(int32_t id, const char *text) {
    if (id <= 0 || id >= JN_UI_MAX) {
        return;
    }
    if (!g_win32_active || g_hwnds[id] == NULL
            || (g_kinds[id] != JN_UI_KIND_LISTVIEW && g_kinds[id] != JN_UI_KIND_TABLEVIEW)) {
        g_item_count[id]++;
        return;
    }
    HWND lv = g_hwnds[id];
    char *copy = _strdup(text == NULL ? "" : text);
    if (copy == NULL) {
        return;
    }
    char *p = copy;
    char *tab = strchr(p, '\t');
    if (tab != NULL) {
        *tab = '\0';
    }
    int row = ListView_GetItemCount(lv);
    wchar_t *w0 = utf8_to_wide(p);
    LVITEMW item;
    memset(&item, 0, sizeof(item));
    item.mask = LVIF_TEXT;
    item.iItem = row;
    item.iSubItem = 0;
    item.pszText = w0;
    ListView_InsertItem(lv, &item);
    free(w0);
    int sub = 1;
    p = (tab == NULL) ? NULL : (tab + 1);
    while (p != NULL) {
        tab = strchr(p, '\t');
        if (tab != NULL) {
            *tab = '\0';
        }
        wchar_t *w = utf8_to_wide(p);
        LVITEMW subitem;
        memset(&subitem, 0, sizeof(subitem));
        subitem.iSubItem = sub;
        subitem.pszText = w;
        SendMessageW(lv, LVM_SETITEMTEXT, (WPARAM)row, (LPARAM)&subitem);
        free(w);
        sub++;
        p = (tab == NULL) ? NULL : (tab + 1);
    }
    free(copy);
    g_item_count[id] = ListView_GetItemCount(lv);
}
void jnative_ui_tree_clear(int32_t id) { jnative_ui_combo_clear(id); }
void jnative_ui_tree_add_item(int32_t id, int32_t parentIndex, const char *text) {
    (void)parentIndex; jnative_ui_combo_add_item(id, text);
}
void jnative_ui_tree_set_expanded(int32_t id, int32_t index, int32_t expanded) {
    (void)id; (void)index; (void)expanded;
}
int32_t jnative_ui_tree_is_expanded(int32_t id, int32_t index) {
    (void)id; (void)index; return 0;
}
void jnative_ui_tab_add(int32_t id, const char *title, int32_t contentId) {
    (void)title; (void)contentId; jnative_ui_combo_add_item(id, title);
}
void jnative_ui_tab_set_selected(int32_t id, int32_t index) { jnative_ui_set_selected_index(id, index); }
int32_t jnative_ui_tab_get_selected(int32_t id) { return jnative_ui_get_selected_index(id); }
void jnative_ui_split_set_divider(int32_t id, double pos) {
    if (id > 0 && id < JN_UI_MAX) g_divider[id] = pos;
}
double jnative_ui_split_get_divider(int32_t id) {
    return (id > 0 && id < JN_UI_MAX) ? g_divider[id] : 0.5;
}
int32_t jnative_ui_menu_create_bar(void) { return jnative_ui_create_panel(0); }
int32_t jnative_ui_menu_create(const char *title) { return jnative_ui_create_label(0, title); }
int32_t jnative_ui_menu_create_item(const char *text) {
    return jnative_ui_create_control(JN_UI_KIND_MENUITEM, 0, text);
}
int32_t jnative_ui_menu_create_separator(void) {
    return jnative_ui_create_control(JN_UI_KIND_SEPARATOR, 0, "-");
}
void jnative_ui_menu_add(int32_t parentMenuId, int32_t childId) { (void)parentMenuId; (void)childId; }
void jnative_ui_window_set_menubar(int32_t windowId, int32_t menuBarId) {
    (void)windowId; (void)menuBarId;
}
int32_t jnative_ui_dialog_message(int32_t windowId, int32_t type, const char *title, const char *message) {
    if (g_win32_active) {
        wchar_t *wtitle = utf8_to_wide(title);
        wchar_t *wmsg = utf8_to_wide(message);
        UINT flags = MB_OK;
        if (type == 1) flags = MB_YESNO;
        if (type == 2) flags = MB_OK | MB_ICONERROR;
        HWND owner = (windowId > 0 && windowId < JN_UI_MAX) ? g_hwnds[windowId] : NULL;
        int r = MessageBoxW(owner, wmsg, wtitle, flags);
        free(wtitle); free(wmsg);
        return (r == IDOK || r == IDYES) ? 1 : 0;
    }
    (void)windowId; (void)type; (void)title; (void)message;
    return g_dialog_result;
}
char *jnative_ui_dialog_file_open(int32_t windowId, const char *filter) {
    (void)windowId; (void)filter;
    return g_file_result ? _strdup(g_file_result) : NULL;
}
char *jnative_ui_dialog_file_save(int32_t windowId, const char *filter) {
    (void)windowId; (void)filter;
    return g_file_result ? _strdup(g_file_result) : NULL;
}
void jnative_ui_dialog_free_path(char *path) { free(path); }

void jnative_ui_headless_click(int32_t id) {
    jnative_ui_emit_event(id, JN_UI_EVENT_CLICK);
}
void jnative_ui_headless_close(int32_t id) {
    jnative_ui_emit_event(id, JN_UI_EVENT_CLOSE);
}
void jnative_ui_headless_toggle(int32_t id) {
    if (id > 0 && id < JN_UI_MAX) g_selected[id] = !g_selected[id];
    jnative_ui_emit_event_ex(id, JN_UI_EVENT_TOGGLE, g_selected[id], 0, 0, 0);
    jnative_ui_emit_event(id, JN_UI_EVENT_CHANGE);
}
void jnative_ui_headless_select(int32_t id, int32_t index) {
    jnative_ui_set_selected_index(id, index);
    jnative_ui_emit_event_ex(id, JN_UI_EVENT_SELECT, index, 0, 0, 0);
}
void jnative_ui_headless_change(int32_t id) { jnative_ui_emit_event(id, JN_UI_EVENT_CHANGE); }
void jnative_ui_headless_action(int32_t id) { jnative_ui_emit_event(id, JN_UI_EVENT_ACTION); }
void jnative_ui_headless_key(int32_t id, int32_t keyCode, int32_t mods, int32_t down) {
    jnative_ui_emit_event_ex(id, JN_UI_EVENT_KEY, down ? 1 : 2, keyCode, mods, 0);
}
void jnative_ui_headless_mouse(int32_t id, int32_t type, int32_t x, int32_t y, int32_t button) {
    jnative_ui_emit_event_ex(id, JN_UI_EVENT_MOUSE, type, x, y, button);
}
void jnative_ui_headless_focus(int32_t id, int32_t gained) {
    jnative_ui_emit_event_ex(id, JN_UI_EVENT_FOCUS, gained ? 1 : 2, 0, 0, 0);
}
void jnative_ui_headless_set_dialog_result(int32_t result) { g_dialog_result = result; }
void jnative_ui_headless_set_file_result(const char *path) {
    free(g_file_result);
    g_file_result = path ? _strdup(path) : NULL;
}

/* Unused platform stubs for header compatibility when only win32 file is linked. */
void jnative_ui_platform_init(int32_t headless) { (void)headless; }
void jnative_ui_platform_run_loop(void) {}
void jnative_ui_platform_quit(void) {}
void jnative_ui_platform_wakeup(void) {}
int32_t jnative_ui_platform_create_window(int32_t id, const char *title, int32_t w, int32_t h) {
    (void)id; (void)title; (void)w; (void)h; return 0;
}
int32_t jnative_ui_platform_create_control(int32_t id, int32_t kind, int32_t parentId, const char *text) {
    (void)id; (void)kind; (void)parentId; (void)text; return 0;
}
void jnative_ui_platform_destroy(int32_t id) { (void)id; }
void jnative_ui_platform_show(int32_t id) { (void)id; }
void jnative_ui_platform_hide(int32_t id) { (void)id; }
void jnative_ui_platform_set_title(int32_t id, const char *title) { (void)id; (void)title; }
void jnative_ui_platform_set_text(int32_t id, const char *text) { (void)id; (void)text; }
void jnative_ui_platform_set_bounds(int32_t id, int32_t x, int32_t y, int32_t w, int32_t h) {
    (void)id; (void)x; (void)y; (void)w; (void)h;
}
void jnative_ui_platform_set_visible(int32_t id, int32_t visible) { (void)id; (void)visible; }
void jnative_ui_platform_set_enabled(int32_t id, int32_t enabled) { (void)id; (void)enabled; }
void jnative_ui_platform_set_content(int32_t windowId, int32_t childId) {
    (void)windowId; (void)childId;
}
int32_t jnative_ui_platform_dialog_message(int32_t windowId, int32_t type,
                                           const char *title, const char *message) {
    (void)windowId; (void)type; (void)title; (void)message; return -1;
}
char *jnative_ui_platform_dialog_file(int32_t windowId, int32_t save, const char *filter) {
    (void)windowId; (void)save; (void)filter; return NULL;
}

int32_t jnative_ui_platform_family(void) {
    return 2;
}

int32_t jnative_ui_platform_get_scale_percent(int32_t windowId) {
    (void)windowId;
    return 100;
}

int32_t jnative_ui_platform_system_dark(void) {
    return -1;
}

int32_t jnative_ui_platform_context_menu_show(int32_t menuId, int32_t screenX, int32_t screenY) {
    (void)menuId;
    (void)screenX;
    (void)screenY;
    return 0;
}

void jnative_ui_platform_set_control_style(int32_t id, int32_t style) {
    (void)id;
    (void)style;
}

void jnative_ui_platform_set_visual(int32_t id, int32_t bg_rgba, int32_t fg_rgba,
                                    int32_t corner_radius, int32_t font_size, int32_t text_align) {
    (void)id; (void)bg_rgba; (void)fg_rgba;
    (void)corner_radius; (void)font_size; (void)text_align;
}

void jnative_ui_platform_window_set_min_size(int32_t windowId, int32_t w, int32_t h) {
    (void)windowId; (void)w; (void)h;
}

void jnative_ui_platform_button_flash(int32_t id, int32_t pressed) {
    (void)id; (void)pressed;
}

/* ---- Symbols required by UiLib but previously missing on Win32 ---- */

void jnative_ui_close_window(int32_t id) {
    if (id > 0 && id < JN_UI_MAX && g_hwnds[id] != NULL) {
        DestroyWindow(g_hwnds[id]);
    }
}
char *jnative_ui_copy_text(int32_t id) {
    if (id <= 0 || id >= JN_UI_MAX || g_hwnds[id] == NULL) {
        return _strdup("");
    }
    int n = GetWindowTextLengthW(g_hwnds[id]);
    wchar_t *w = (wchar_t *)malloc(sizeof(wchar_t) * (size_t)(n + 1));
    if (w == NULL) {
        return _strdup("");
    }
    GetWindowTextW(g_hwnds[id], w, n + 1);
    int bytes = WideCharToMultiByte(CP_UTF8, 0, w, -1, NULL, 0, NULL, NULL);
    char *out = (char *)malloc((size_t)bytes);
    if (out == NULL) {
        free(w);
        return _strdup("");
    }
    WideCharToMultiByte(CP_UTF8, 0, w, -1, out, bytes, NULL, NULL);
    free(w);
    return out;
}
void jnative_ui_request_quit(void) {
    jnative_ui_emit_event(0, JN_UI_EVENT_QUIT_REQUEST);
    jnative_ui_quit();
}
void jnative_ui_set_app_name(const char *name) { (void)name; }
void jnative_ui_menu_set_shortcut(int32_t itemId, int32_t keyCode, int32_t mods) {
    (void)itemId; (void)keyCode; (void)mods;
}
void jnative_ui_focus(int32_t id) {
    if (id > 0 && id < JN_UI_MAX && g_hwnds[id] != NULL) {
        SetFocus(g_hwnds[id]);
    }
}
void jnative_ui_set_tooltip(int32_t id, const char *tip) { (void)id; (void)tip; }
void jnative_ui_set_accessible(int32_t id, const char *name, const char *description) {
    (void)id; (void)name; (void)description;
}
void jnative_ui_set_accessible_title_ui(int32_t fieldId, int32_t labelId) {
    (void)fieldId; (void)labelId;
}
void jnative_ui_set_next_key_view(int32_t id, int32_t nextId) { (void)id; (void)nextId; }
void jnative_ui_set_appearance_mode(int32_t mode) { (void)mode; }
void jnative_ui_set_button_symbol(int32_t id, const char *symbol) { (void)id; (void)symbol; }
void jnative_ui_window_set_default_button(int32_t windowId, int32_t buttonId) {
    (void)windowId; (void)buttonId;
}
void jnative_ui_window_set_cancel_button(int32_t windowId, int32_t buttonId) {
    (void)windowId; (void)buttonId;
}
int32_t jnative_ui_features(void) { return 0; }
int32_t jnative_ui_prefers_reduced_motion(void) { return 0; }
int32_t jnative_ui_prefers_increased_contrast(void) { return 0; }
char *jnative_ui_dialog_dir_open(int32_t windowId) {
    (void)windowId; return NULL;
}
void jnative_ui_canvas_invalidate(int32_t id) { (void)id; }
void jnative_ui_canvas_present(int32_t id, int32_t surfaceId) {
    (void)id; (void)surfaceId;
}
void jnative_ui_headless_paint(int32_t id) { (void)id; }
void jnative_ui_headless_timer_fire(int32_t id) {
    jnative_ui_emit_event(id, JN_UI_EVENT_TIMER);
}
int32_t jnative_ui_table_get_scroll_y(int32_t id) {
    if (id <= 0 || id >= JN_UI_MAX || g_hwnds[id] == NULL) {
        return 0;
    }
    return (int32_t)GetScrollPos(g_hwnds[id], SB_VERT);
}
void jnative_ui_table_set_scroll_y(int32_t id, int32_t y) {
    if (id <= 0 || id >= JN_UI_MAX || g_hwnds[id] == NULL) {
        return;
    }
    ListView_Scroll(g_hwnds[id], 0, y - GetScrollPos(g_hwnds[id], SB_VERT));
}
int32_t jnative_ui_timer_start(int32_t intervalMs, int32_t handleId) {
    if (handleId <= 0 || handleId >= JN_UI_MAX || intervalMs <= 0) {
        return 0;
    }
    HWND hwnd = NULL;
    int i = 1;
    while (i < JN_UI_MAX) {
        if (g_kinds[i] == JN_UI_KIND_WINDOW && g_hwnds[i] != NULL) {
            hwnd = g_hwnds[i];
            break;
        }
        i++;
    }
    if (hwnd == NULL) {
        return 0;
    }
    if (g_timers[handleId] != 0) {
        KillTimer(hwnd, (UINT_PTR)handleId);
    }
    g_timers[handleId] = SetTimer(hwnd, (UINT_PTR)handleId, (UINT)intervalMs, NULL);
    return g_timers[handleId] != 0 ? 1 : 0;
}
void jnative_ui_timer_stop(int32_t handleId) {
    if (handleId <= 0 || handleId >= JN_UI_MAX) {
        return;
    }
    int i = 1;
    while (i < JN_UI_MAX) {
        if (g_kinds[i] == JN_UI_KIND_WINDOW && g_hwnds[i] != NULL && g_timers[handleId] != 0) {
            KillTimer(g_hwnds[i], (UINT_PTR)handleId);
            break;
        }
        i++;
    }
    g_timers[handleId] = 0;
}

void jnative_ui_window_set_min_size(int32_t windowId, int32_t w, int32_t h) {
    (void)windowId; (void)w; (void)h;
}
void jnative_ui_button_flash(int32_t id, int32_t pressed) {
    (void)id; (void)pressed;
}
void jnative_ui_set_visual(int32_t id, int32_t bg_rgba, int32_t fg_rgba,
                           int32_t corner_radius, int32_t font_size, int32_t text_align) {
    (void)id; (void)bg_rgba; (void)fg_rgba;
    (void)corner_radius; (void)font_size; (void)text_align;
}
int32_t jnative_ui_get_scale_percent(int32_t windowId) {
    if (!g_win32_active || windowId <= 0 || windowId >= JN_UI_MAX || g_hwnds[windowId] == NULL) {
        return 100;
    }
    UINT dpi = 96;
#if defined(DPI_AWARENESS_CONTEXT_PER_MONITOR_AWARE_V2)
    dpi = GetDpiForWindow(g_hwnds[windowId]);
#endif
    if (dpi == 0) {
        dpi = 96;
    }
    return (int32_t)((dpi * 100) / 96);
}
int32_t jnative_ui_system_dark(void) {
    return jnative_ui_platform_system_dark();
}

static char *g_clipboard = NULL;

int32_t jnative_clipboard_set_text(const char *text) {
    free(g_clipboard);
    g_clipboard = text != NULL ? _strdup(text) : NULL;
    if (!g_win32_active || text == NULL) {
        return 1;
    }
    if (!OpenClipboard(NULL)) {
        return 0;
    }
    EmptyClipboard();
    int n = MultiByteToWideChar(CP_UTF8, 0, text, -1, NULL, 0);
    HGLOBAL h = GlobalAlloc(GMEM_MOVEABLE, (SIZE_T)n * sizeof(wchar_t));
    if (h == NULL) {
        CloseClipboard();
        return 0;
    }
    wchar_t *w = (wchar_t *)GlobalLock(h);
    MultiByteToWideChar(CP_UTF8, 0, text, -1, w, n);
    GlobalUnlock(h);
    SetClipboardData(CF_UNICODETEXT, h);
    CloseClipboard();
    return 1;
}

char *jnative_clipboard_get_text(void) {
    if (!g_win32_active) {
        return g_clipboard != NULL ? _strdup(g_clipboard) : NULL;
    }
    if (!OpenClipboard(NULL)) {
        return g_clipboard != NULL ? _strdup(g_clipboard) : NULL;
    }
    HANDLE h = GetClipboardData(CF_UNICODETEXT);
    if (h == NULL) {
        CloseClipboard();
        return g_clipboard != NULL ? _strdup(g_clipboard) : NULL;
    }
    wchar_t *w = (wchar_t *)GlobalLock(h);
    int bytes = WideCharToMultiByte(CP_UTF8, 0, w, -1, NULL, 0, NULL, NULL);
    char *out = (char *)malloc((size_t)bytes);
    if (out != NULL) {
        WideCharToMultiByte(CP_UTF8, 0, w, -1, out, bytes, NULL, NULL);
    }
    GlobalUnlock(h);
    CloseClipboard();
    return out;
}

void jnative_clipboard_free(char *text) {
    free(text);
}

void jnative_ui_platform_set_appearance_mode(int32_t mode) {
    (void)mode;
}
