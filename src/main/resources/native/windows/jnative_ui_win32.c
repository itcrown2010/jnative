/* Author: 张晓达 */
/* Win32 UI backend (0.17/0.18). CreateWindowEx + common controls. */
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

static LRESULT CALLBACK JnWndProc(HWND hwnd, UINT msg, WPARAM wParam, LPARAM lParam) {
    int32_t handleId = (int32_t)(intptr_t)GetWindowLongPtr(hwnd, GWLP_USERDATA);
    if (msg == WM_COMMAND) {
        HWND ctrl = (HWND)lParam;
        int32_t cid = (int32_t)(intptr_t)GetWindowLongPtr(ctrl, GWLP_USERDATA);
        if (cid > 0 && HIWORD(wParam) == BN_CLICKED) {
            jnative_ui_emit_event(cid, JN_UI_EVENT_CLICK);
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
    if (hl) {
        g_win32_active = 0;
        return;
    }
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
    (void)windowId;
    (void)childId;
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
    (void)id; (void)titles_tsv;
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
void jnative_ui_combo_clear(int32_t id) { jnative_ui_set_item_count(id, 0); }
void jnative_ui_combo_add_item(int32_t id, const char *text) {
    (void)text;
    if (id > 0 && id < JN_UI_MAX) g_item_count[id]++;
}
void jnative_ui_list_clear(int32_t id) { jnative_ui_combo_clear(id); }
void jnative_ui_list_add_item(int32_t id, const char *text) { jnative_ui_combo_add_item(id, text); }
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
