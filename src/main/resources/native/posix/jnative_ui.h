/* Author: 张晓达 */
#ifndef JNATIVE_UI_H
#define JNATIVE_UI_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Component kinds (0.17 + 0.18). */
enum {
    JN_UI_KIND_WINDOW = 1,
    JN_UI_KIND_BUTTON = 2,
    JN_UI_KIND_LABEL = 3,
    JN_UI_KIND_TEXTFIELD = 4,
    JN_UI_KIND_PANEL = 5,
    JN_UI_KIND_CHECKBOX = 6,
    JN_UI_KIND_RADIO = 7,
    JN_UI_KIND_COMBOBOX = 8,
    JN_UI_KIND_TEXTAREA = 9,
    JN_UI_KIND_PROGRESS = 10,
    JN_UI_KIND_SCROLL = 11,
    JN_UI_KIND_TABPANE = 12,
    JN_UI_KIND_SPLIT = 13,
    JN_UI_KIND_LISTVIEW = 14,
    JN_UI_KIND_TABLEVIEW = 15,
    JN_UI_KIND_TREEVIEW = 16,
    JN_UI_KIND_MENUITEM = 17,
    JN_UI_KIND_SEPARATOR = 18,
    JN_UI_KIND_CANVAS = 19
};

/* Platform-neutral event codes. */
enum {
    JN_UI_EVENT_CLICK = 1,
    JN_UI_EVENT_CLOSE = 2,
    JN_UI_EVENT_RESIZE = 3,
    JN_UI_EVENT_CHANGE = 4,
    JN_UI_EVENT_TASK = 5,
    JN_UI_EVENT_SELECT = 6,
    JN_UI_EVENT_TOGGLE = 7,
    JN_UI_EVENT_ACTION = 8,
    JN_UI_EVENT_KEY = 9,
    JN_UI_EVENT_MOUSE = 10,
    JN_UI_EVENT_FOCUS = 11,
    JN_UI_EVENT_MENU = 12,
    JN_UI_EVENT_PAINT = 13,
    JN_UI_EVENT_TIMER = 14,
    /** Table/outline header click; arg0 = column index. */
    JN_UI_EVENT_HEADER = 15
};

/* Mouse / key subtypes packed into event arg0 for KEY/MOUSE/FOCUS. */
enum {
    JN_UI_MOUSE_DOWN = 1,
    JN_UI_MOUSE_UP = 2,
    JN_UI_MOUSE_CLICK = 3,
    JN_UI_MOUSE_DBLCLICK = 4,
    JN_UI_MOUSE_MOVE = 5,
    JN_UI_MOUSE_ENTER = 6,
    JN_UI_MOUSE_EXIT = 7,
    JN_UI_MOUSE_WHEEL = 8,
    JN_UI_KEY_DOWN = 1,
    JN_UI_KEY_UP = 2,
    JN_UI_FOCUS_GAINED = 1,
    JN_UI_FOCUS_LOST = 2
};

typedef int32_t (*jn_ui_dispatch_fn)(int32_t handleId, int32_t eventCode);

void jnative_ui_init(int32_t headless);
void jnative_ui_set_dispatcher(void *fn);
void jnative_ui_run_loop(void);
void jnative_ui_quit(void);
void jnative_ui_post_task(void);

int32_t jnative_ui_create_window(const char *title, int32_t width, int32_t height);
int32_t jnative_ui_create_button(int32_t parentId, const char *text);
int32_t jnative_ui_create_label(int32_t parentId, const char *text);
int32_t jnative_ui_create_textfield(int32_t parentId, const char *text);
int32_t jnative_ui_create_panel(int32_t parentId);
int32_t jnative_ui_create_control(int32_t kind, int32_t parentId, const char *text);

void jnative_ui_destroy(int32_t id);
void jnative_ui_close_window(int32_t windowId);
void jnative_ui_show(int32_t id);
void jnative_ui_hide(int32_t id);
void jnative_ui_set_title(int32_t id, const char *title);
void jnative_ui_set_text(int32_t id, const char *text);
void jnative_ui_set_bounds(int32_t id, int32_t x, int32_t y, int32_t w, int32_t h);
void jnative_ui_set_visible(int32_t id, int32_t visible);
void jnative_ui_set_enabled(int32_t id, int32_t enabled);
void jnative_ui_focus(int32_t id);
void jnative_ui_set_content(int32_t windowId, int32_t childId);
/** Attach child native peer as subview of parent peer (panel/split/etc). */
void jnative_ui_reparent(int32_t childId, int32_t parentId);

/* 0.18 control state */
void jnative_ui_set_selected(int32_t id, int32_t selected);
int32_t jnative_ui_get_selected(int32_t id);
void jnative_ui_set_value_i32(int32_t id, int32_t value);
int32_t jnative_ui_get_value_i32(int32_t id);
void jnative_ui_set_progress(int32_t id, double progress);
double jnative_ui_get_progress(int32_t id);
void jnative_ui_set_item_count(int32_t id, int32_t count);
int32_t jnative_ui_get_item_count(int32_t id);
const char *jnative_ui_item_text(int32_t id, int32_t index);
int32_t jnative_ui_item_parent(int32_t id, int32_t index);
void jnative_ui_table_set_columns(int32_t id, const char *titles_tsv);
const char *jnative_ui_table_columns(int32_t id);
void jnative_ui_set_selected_index(int32_t id, int32_t index);
int32_t jnative_ui_get_selected_index(int32_t id);
void jnative_ui_set_visible_rows(int32_t id, int32_t rows);
int32_t jnative_ui_get_visible_rows(int32_t id);
void jnative_ui_combo_clear(int32_t id);
void jnative_ui_combo_add_item(int32_t id, const char *text);
void jnative_ui_list_clear(int32_t id);
void jnative_ui_list_add_item(int32_t id, const char *text);
void jnative_ui_tree_clear(int32_t id);
void jnative_ui_tree_add_item(int32_t id, int32_t parentIndex, const char *text);
void jnative_ui_tree_set_expanded(int32_t id, int32_t index, int32_t expanded);
int32_t jnative_ui_tree_is_expanded(int32_t id, int32_t index);
void jnative_ui_tab_add(int32_t id, const char *title, int32_t contentId);
void jnative_ui_tab_set_selected(int32_t id, int32_t index);
int32_t jnative_ui_tab_get_selected(int32_t id);
void jnative_ui_split_set_divider(int32_t id, double pos);
double jnative_ui_split_get_divider(int32_t id);

/* Menus: menu items are peers; bar attaches to window. */
int32_t jnative_ui_menu_create_bar(void);
int32_t jnative_ui_menu_create(const char *title);
int32_t jnative_ui_menu_create_item(const char *text);
int32_t jnative_ui_menu_create_separator(void);
void jnative_ui_menu_add(int32_t parentMenuId, int32_t childId);
void jnative_ui_window_set_menubar(int32_t windowId, int32_t menuBarId);
/* Menu peer introspection for platform MenuBar install. */
int32_t jnative_ui_peer_kind(int32_t id);
const char *jnative_ui_peer_text(int32_t id);
int32_t jnative_ui_menu_child_count(int32_t id);
int32_t jnative_ui_menu_child_at(int32_t id, int32_t index);

/* Dialogs (modal OS dialogs; headless uses programmed result). */
int32_t jnative_ui_dialog_message(int32_t windowId, int32_t type, const char *title, const char *message);
/* type: 0=info 1=confirm 2=error 3=saveAsk; returns 1=OK/Yes/Save 0=Cancel/No 2=Don't Save */
char *jnative_ui_dialog_file_open(int32_t windowId, const char *filter);
char *jnative_ui_dialog_file_save(int32_t windowId, const char *filter);
char *jnative_ui_dialog_dir_open(int32_t windowId);
void jnative_ui_dialog_free_path(char *path);

int32_t jnative_ui_live_windows(void);
int32_t jnative_ui_live_controls(void);
int32_t jnative_ui_live_handles(void);
int32_t jnative_ui_queued_tasks(void);
int32_t jnative_ui_events_dispatched(void);
/** 1=macOS, 2=Windows, 3=other/linux/headless. */
int32_t jnative_ui_platform_family(void);
/** Window backing scale (logical→device). 100 = 1.0x, 200 = 2.0x Retina. */
int32_t jnative_ui_get_scale_percent(int32_t windowId);
/** System appearance: 0=light, 1=dark, -1=unknown. */
int32_t jnative_ui_system_dark(void);
/** Popup context menu at screen logical coords; returns 1 if shown. */
int32_t jnative_ui_context_menu_show(int32_t menuId, int32_t screenX, int32_t screenY);

/* Event payload side-channel (set before emit, read from managed dispatch). */
void jnative_ui_emit_event(int32_t handleId, int32_t eventCode);
void jnative_ui_emit_event_ex(int32_t handleId, int32_t eventCode,
                              int32_t a0, int32_t a1, int32_t a2, int32_t a3);
int32_t jnative_ui_event_arg0(void);
int32_t jnative_ui_event_arg1(void);
int32_t jnative_ui_event_arg2(void);
int32_t jnative_ui_event_arg3(void);

/* Platform hooks (optional strong symbols from cocoa/win32/gtk). */
int32_t jnative_ui_platform_available(void);
void jnative_ui_platform_init(int32_t headless);
void jnative_ui_platform_run_loop(void);
void jnative_ui_platform_quit(void);
void jnative_ui_platform_wakeup(void);
int32_t jnative_ui_platform_create_window(int32_t id, const char *title, int32_t w, int32_t h);
int32_t jnative_ui_platform_create_control(int32_t id, int32_t kind, int32_t parentId, const char *text);
void jnative_ui_platform_destroy(int32_t id);
void jnative_ui_platform_show(int32_t id);
void jnative_ui_platform_hide(int32_t id);
void jnative_ui_platform_set_title(int32_t id, const char *title);
void jnative_ui_platform_set_text(int32_t id, const char *text);
void jnative_ui_platform_set_bounds(int32_t id, int32_t x, int32_t y, int32_t w, int32_t h);
void jnative_ui_platform_set_visible(int32_t id, int32_t visible);
void jnative_ui_platform_set_enabled(int32_t id, int32_t enabled);
void jnative_ui_platform_set_content(int32_t windowId, int32_t childId);
void jnative_ui_platform_reparent(int32_t childId, int32_t parentId);
void jnative_ui_platform_set_menubar(int32_t windowId, int32_t menuBarId);
void jnative_ui_platform_close_window(int32_t windowId);
void jnative_ui_platform_focus(int32_t id);
int32_t jnative_ui_platform_dialog_message(int32_t windowId, int32_t type,
                                           const char *title, const char *message);
char *jnative_ui_platform_dialog_file(int32_t windowId, int32_t save, const char *filter);
void jnative_ui_platform_items_changed(int32_t id);
void jnative_ui_platform_table_set_columns(int32_t id, const char *titles_tsv);
/** Control chrome: 0=default, 1=toolbar, 2=breadcrumb, 3=sidebar item. */
void jnative_ui_platform_set_control_style(int32_t id, int32_t style);
/** Visual style: bg/fg as 0xAARRGGBB (-1 = leave), radius px (-1 leave), fontSize pt (-1 leave), align 0=L 1=C 2=R (-1 leave). */
void jnative_ui_set_visual(int32_t id, int32_t bg_rgba, int32_t fg_rgba,
                           int32_t corner_radius, int32_t font_size, int32_t text_align);
void jnative_ui_platform_set_visual(int32_t id, int32_t bg_rgba, int32_t fg_rgba,
                                    int32_t corner_radius, int32_t font_size, int32_t text_align);
void jnative_ui_window_set_min_size(int32_t windowId, int32_t w, int32_t h);
void jnative_ui_platform_window_set_min_size(int32_t windowId, int32_t w, int32_t h);
/** Flash pressed visual feedback (filled buttons). */
void jnative_ui_button_flash(int32_t id, int32_t pressed);
void jnative_ui_platform_button_flash(int32_t id, int32_t pressed);
int32_t jnative_ui_platform_get_scale_percent(int32_t windowId);
int32_t jnative_ui_platform_system_dark(void);
int32_t jnative_ui_platform_context_menu_show(int32_t menuId, int32_t screenX, int32_t screenY);

/* Headless / test helpers */
void jnative_ui_headless_click(int32_t id);
void jnative_ui_headless_close(int32_t id);
void jnative_ui_headless_toggle(int32_t id);
void jnative_ui_headless_select(int32_t id, int32_t index);
void jnative_ui_headless_change(int32_t id);
void jnative_ui_headless_action(int32_t id);
void jnative_ui_headless_key(int32_t id, int32_t keyCode, int32_t mods, int32_t down);
void jnative_ui_headless_mouse(int32_t id, int32_t type, int32_t x, int32_t y, int32_t button);
void jnative_ui_headless_focus(int32_t id, int32_t gained);
void jnative_ui_headless_set_dialog_result(int32_t result);
void jnative_ui_headless_set_file_result(const char *path);
void jnative_ui_headless_paint(int32_t id);
void jnative_ui_headless_timer_fire(int32_t id);

/* Canvas: present software surface pixels to native peer (double-buffered blit). */
void jnative_ui_canvas_invalidate(int32_t id);
void jnative_ui_canvas_present(int32_t id, int32_t surfaceId);

/* UI timer (fires JN_UI_EVENT_TIMER on handleId; UI thread). */
int32_t jnative_ui_timer_start(int32_t intervalMs, int32_t handleId);
void jnative_ui_timer_stop(int32_t timerId);

/* Appearance: 0=system, 1=light, 2=dark. */
void jnative_ui_set_appearance_mode(int32_t mode);
void jnative_ui_platform_set_appearance_mode(int32_t mode);

/* Clipboard (platform + headless buffer). */
int32_t jnative_clipboard_set_text(const char *text);
char *jnative_clipboard_get_text(void);
void jnative_clipboard_free(char *text);

/* Lightweight process spawn for IDE Build (shell command). */
int32_t jnative_process_run(const char *command);

/* Wall clock for editor benchmarks (milliseconds, truncated to int32). */
int32_t jnative_now_ms(void);

#ifdef __cplusplus
}
#endif

#endif
