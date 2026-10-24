/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Author: 张晓达 */
#include "jnative_ui.h"
#include "jnative_graphics.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <pthread.h>
#include <unistd.h>
#include <sys/time.h>
#include <sys/wait.h>
#include <math.h>

#ifndef JN_UI_MAX
#define JN_UI_MAX 4096
#endif

#ifndef JN_UI_ITEM_MAX
#define JN_UI_ITEM_MAX 4096
#endif

typedef struct {
    int32_t kind;
    int32_t parent;
    int32_t x, y, w, h;
    int32_t visible;
    int32_t enabled;
    int32_t disposed;
    int32_t selected;
    int32_t selected_index;
    int32_t item_count;
    int32_t visible_rows;
    int32_t value_i32;
    int32_t tab_selected;
    int32_t tab_count;
    double progress;
    double divider;
    char *text;
    char *title;
    char *column_titles;
    char *items[JN_UI_ITEM_MAX];
    int32_t item_parents[JN_UI_ITEM_MAX];
    int32_t item_expanded[JN_UI_ITEM_MAX];
    int32_t menu_children[64];
    int32_t menu_child_count;
    int32_t shortcut_key;
    int32_t shortcut_mods;
} JnUiPeer;

static char *g_app_name = NULL;

static JnUiPeer g_peers[JN_UI_MAX];
static int32_t g_next_id = 1;
static int32_t g_live_windows = 0;
static int32_t g_live_controls = 0;
static int32_t g_live_handles = 0;
static int32_t g_events_dispatched = 0;
static int32_t g_running = 0;
static int32_t g_quit = 0;
static int32_t g_headless = 1;
static int32_t g_platform_active = 0;
static jn_ui_dispatch_fn g_dispatch = NULL;
static int32_t g_task_queue = 0;
static pthread_mutex_t g_lock = PTHREAD_MUTEX_INITIALIZER;

static int32_t g_event_a0 = 0;
static int32_t g_event_a1 = 0;
static int32_t g_event_a2 = 0;
static int32_t g_event_a3 = 0;
static int32_t g_dialog_result = 1;
static char *g_file_result = NULL;

#ifndef JN_UI_TIMER_MAX
#define JN_UI_TIMER_MAX 64
#endif
typedef struct {
    int32_t live;
    int32_t handleId;
    int32_t intervalMs;
    int64_t nextMs;
} JnUiTimer;
static JnUiTimer g_timers[JN_UI_TIMER_MAX];
static int32_t g_live_timers = 0;

static int64_t jn_now_ms(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (int64_t)tv.tv_sec * 1000 + tv.tv_usec / 1000;
}

__attribute__((weak)) void jnative_ui_platform_canvas_present(
        int32_t id, const uint8_t *rgba, int32_t w, int32_t h) {
    (void)id; (void)rgba; (void)w; (void)h;
}
__attribute__((weak)) void jnative_ui_platform_canvas_invalidate(int32_t id) {
    (void)id;
}
__attribute__((weak)) int32_t jnative_ui_platform_window_snapshot(int32_t id, const char *path) {
    (void)id; (void)path;
    return 0;
}
__attribute__((weak)) void jnative_ui_platform_panel_configure(int32_t id, int32_t flags) {
    (void)id; (void)flags;
}
__attribute__((weak)) void jnative_ui_platform_canvas_set_transparent(int32_t id, int32_t on) {
    (void)id; (void)on;
}
__attribute__((weak)) void jnative_ui_platform_timer_arm(
        int32_t timerId, int32_t intervalMs, int32_t handleId) {
    (void)timerId; (void)intervalMs; (void)handleId;
}
__attribute__((weak)) void jnative_ui_platform_timer_disarm(int32_t timerId) {
    (void)timerId;
}

__attribute__((weak)) void jnative_ui_platform_set_appearance_mode(int32_t mode) {
    (void)mode;
}
__attribute__((weak)) int32_t jnative_ui_platform_available(void) { return 0; }
__attribute__((weak)) void jnative_ui_platform_init(int32_t headless) { (void)headless; }
__attribute__((weak)) void jnative_ui_platform_run_loop(void) {}
__attribute__((weak)) void jnative_ui_platform_quit(void) {}
__attribute__((weak)) void jnative_ui_platform_wakeup(void) {}
__attribute__((weak)) int32_t jnative_ui_platform_create_window(
        int32_t id, const char *title, int32_t w, int32_t h) {
    (void)id; (void)title; (void)w; (void)h; return 0;
}
__attribute__((weak)) int32_t jnative_ui_platform_create_control(
        int32_t id, int32_t kind, int32_t parentId, const char *text) {
    (void)id; (void)kind; (void)parentId; (void)text; return 0;
}
__attribute__((weak)) void jnative_ui_platform_destroy(int32_t id) { (void)id; }
__attribute__((weak)) void jnative_ui_platform_show(int32_t id) { (void)id; }
__attribute__((weak)) void jnative_ui_platform_hide(int32_t id) { (void)id; }
__attribute__((weak)) void jnative_ui_platform_set_title(int32_t id, const char *title) {
    (void)id; (void)title;
}
__attribute__((weak)) void jnative_ui_platform_set_text(int32_t id, const char *text) {
    (void)id; (void)text;
}
__attribute__((weak)) void jnative_ui_platform_set_bounds(
        int32_t id, int32_t x, int32_t y, int32_t w, int32_t h) {
    (void)id; (void)x; (void)y; (void)w; (void)h;
}
__attribute__((weak)) void jnative_ui_platform_set_visible(int32_t id, int32_t visible) {
    (void)id; (void)visible;
}
__attribute__((weak)) void jnative_ui_platform_set_enabled(int32_t id, int32_t enabled) {
    (void)id; (void)enabled;
}
__attribute__((weak)) void jnative_ui_platform_set_content(int32_t windowId, int32_t childId) {
    (void)windowId; (void)childId;
}
__attribute__((weak)) void jnative_ui_platform_reparent(int32_t childId, int32_t parentId) {
    (void)childId; (void)parentId;
}
__attribute__((weak)) void jnative_ui_platform_set_menubar(int32_t windowId, int32_t menuBarId) {
    (void)windowId; (void)menuBarId;
}
__attribute__((weak)) void jnative_ui_platform_close_window(int32_t windowId) {
    (void)windowId;
}
__attribute__((weak)) void jnative_ui_platform_focus(int32_t id) {
    (void)id;
}
__attribute__((weak)) int32_t jnative_ui_platform_dialog_message(
        int32_t windowId, int32_t type, const char *title, const char *message) {
    (void)windowId; (void)type; (void)title; (void)message;
    return -1;
}
__attribute__((weak)) char *jnative_ui_platform_dialog_file(
        int32_t windowId, int32_t save, const char *filter) {
    (void)windowId; (void)save; (void)filter;
    return NULL;
}
__attribute__((weak)) void jnative_ui_platform_items_changed(int32_t id) {
    (void)id;
}
__attribute__((weak)) void jnative_ui_platform_set_tooltip(int32_t id, const char *tip) {
    (void)id; (void)tip;
}
__attribute__((weak)) void jnative_ui_platform_set_accessible(int32_t id, const char *name, const char *description) {
    (void)id; (void)name; (void)description;
}
__attribute__((weak)) void jnative_ui_platform_set_accessible_title_ui(int32_t fieldId, int32_t labelId) {
    (void)fieldId; (void)labelId;
}
__attribute__((weak)) void jnative_ui_platform_set_next_key_view(int32_t fromId, int32_t toId) {
    (void)fromId; (void)toId;
}
__attribute__((weak)) void jnative_ui_platform_window_set_default_button(int32_t windowId, int32_t buttonId) {
    (void)windowId; (void)buttonId;
}
__attribute__((weak)) void jnative_ui_platform_window_set_cancel_button(int32_t windowId, int32_t buttonId) {
    (void)windowId; (void)buttonId;
}
__attribute__((weak)) int32_t jnative_ui_platform_prefers_reduced_motion(void) { return -1; }
__attribute__((weak)) int32_t jnative_ui_platform_prefers_increased_contrast(void) { return -1; }
__attribute__((weak)) void jnative_ui_platform_set_button_symbol(int32_t id, const char *symbolName) {
    (void)id; (void)symbolName;
}
__attribute__((weak)) void jnative_ui_platform_set_selected_index(int32_t id, int32_t index) {
    (void)id; (void)index;
}
__attribute__((weak)) void jnative_ui_platform_set_selected(int32_t id, int32_t selected) {
    (void)id; (void)selected;
}
__attribute__((weak)) void jnative_ui_platform_set_control_style(int32_t id, int32_t style) {
    (void)id;
    (void)style;
}
__attribute__((weak)) void jnative_ui_platform_set_visual(
        int32_t id, int32_t bg_rgba, int32_t fg_rgba,
        int32_t corner_radius, int32_t font_size, int32_t text_align) {
    (void)id; (void)bg_rgba; (void)fg_rgba;
    (void)corner_radius; (void)font_size; (void)text_align;
}
__attribute__((weak)) void jnative_ui_platform_window_set_min_size(int32_t windowId, int32_t w, int32_t h) {
    (void)windowId; (void)w; (void)h;
}
__attribute__((weak)) void jnative_ui_platform_button_flash(int32_t id, int32_t pressed) {
    (void)id; (void)pressed;
}
__attribute__((weak)) int32_t jnative_ui_platform_features(void) {
    return 0;
}
__attribute__((weak)) int32_t jnative_ui_platform_table_get_scroll_y(int32_t id) {
    (void)id;
    return -1;
}
__attribute__((weak)) void jnative_ui_platform_table_set_scroll_y(int32_t id, int32_t y) {
    (void)id;
    (void)y;
}
__attribute__((weak)) void jnative_ui_platform_table_set_columns(
        int32_t id, const char *titles_tsv) {
    (void)id; (void)titles_tsv;
}

static char *jn_ui_strdup(const char *s) {
    if (s == NULL) {
        s = "";
    }
    size_t n = strlen(s);
    char *out = (char *)malloc(n + 1);
    if (out == NULL) {
        return NULL;
    }
    memcpy(out, s, n + 1);
    return out;
}

static JnUiPeer *peer(int32_t id) {
    if (id <= 0 || id >= JN_UI_MAX) {
        return NULL;
    }
    JnUiPeer *p = &g_peers[id];
    if (p->kind == 0 || p->disposed) {
        return NULL;
    }
    return p;
}

void jnative_ui_emit_event(int32_t handleId, int32_t eventCode) {
    jnative_ui_emit_event_ex(handleId, eventCode, 0, 0, 0, 0);
}

void jnative_ui_emit_event_ex(int32_t handleId, int32_t eventCode,
                              int32_t a0, int32_t a1, int32_t a2, int32_t a3) {
    g_event_a0 = a0;
    g_event_a1 = a1;
    g_event_a2 = a2;
    g_event_a3 = a3;
    jn_ui_dispatch_fn fn = g_dispatch;
    if (fn == NULL) {
        return;
    }
    g_events_dispatched++;
    (void)fn(handleId, eventCode);
}

int32_t jnative_ui_event_arg0(void) { return g_event_a0; }
int32_t jnative_ui_event_arg1(void) { return g_event_a1; }
int32_t jnative_ui_event_arg2(void) { return g_event_a2; }
int32_t jnative_ui_event_arg3(void) { return g_event_a3; }

void jnative_ui_init(int32_t headless) {
    const char *env = getenv("JNATIVE_HEADLESS");
    if (headless != 0 || (env != NULL && env[0] != '\0' && env[0] != '0')) {
        g_headless = 1;
    } else {
        g_headless = 0;
    }
    g_quit = 0;
    g_running = 0;
    g_task_queue = 0;
    g_events_dispatched = 0;
    jnative_ui_platform_init(g_headless);
    g_platform_active = (!g_headless) && (jnative_ui_platform_available() != 0);
}

void jnative_ui_set_dispatcher(void *fn) {
    g_dispatch = (jn_ui_dispatch_fn)fn;
}

static int32_t alloc_peer(int32_t kind, int32_t parent, const char *text) {
    pthread_mutex_lock(&g_lock);
    int32_t id = g_next_id++;
    if (id >= JN_UI_MAX) {
        pthread_mutex_unlock(&g_lock);
        return -1;
    }
    JnUiPeer *p = &g_peers[id];
    memset(p, 0, sizeof(*p));
    p->kind = kind;
    p->parent = parent;
    p->visible = 1;
    p->enabled = 1;
    p->w = 100;
    p->h = 32;
    p->selected_index = -1;
    p->visible_rows = 20;
    p->progress = 0.0;
    p->divider = 0.5;
    p->text = jn_ui_strdup(text);
    if (kind == JN_UI_KIND_WINDOW) {
        g_live_windows++;
        p->w = 800;
        p->h = 600;
    } else {
        g_live_controls++;
    }
    g_live_handles++;
    pthread_mutex_unlock(&g_lock);
    return id;
}

int32_t jnative_ui_create_window(const char *title, int32_t width, int32_t height) {
    int32_t id = alloc_peer(JN_UI_KIND_WINDOW, 0, "");
    if (id < 0) {
        return id;
    }
    JnUiPeer *p = &g_peers[id];
    p->title = jn_ui_strdup(title);
    p->w = width;
    p->h = height;
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_create_window(id, title, width, height);
    }
    return id;
}

int32_t jnative_ui_create_control(int32_t kind, int32_t parentId, const char *text) {
    int32_t id = alloc_peer(kind, parentId, text);
    if (id < 0) {
        return id;
    }
    if (kind == JN_UI_KIND_TEXTAREA) {
        g_peers[id].h = 120;
        g_peers[id].w = 300;
    } else if (kind == JN_UI_KIND_LISTVIEW || kind == JN_UI_KIND_TABLEVIEW
               || kind == JN_UI_KIND_TREEVIEW || kind == JN_UI_KIND_SCROLL) {
        g_peers[id].h = 200;
        g_peers[id].w = 240;
    } else if (kind == JN_UI_KIND_PROGRESS) {
        g_peers[id].h = 20;
        g_peers[id].w = 200;
    }
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_create_control(id, kind, parentId, text);
    }
    return id;
}

int32_t jnative_ui_create_button(int32_t parentId, const char *text) {
    return jnative_ui_create_control(JN_UI_KIND_BUTTON, parentId, text);
}
int32_t jnative_ui_create_label(int32_t parentId, const char *text) {
    return jnative_ui_create_control(JN_UI_KIND_LABEL, parentId, text);
}
int32_t jnative_ui_create_textfield(int32_t parentId, const char *text) {
    return jnative_ui_create_control(JN_UI_KIND_TEXTFIELD, parentId, text);
}
int32_t jnative_ui_create_panel(int32_t parentId) {
    return jnative_ui_create_control(JN_UI_KIND_PANEL, parentId, "");
}

void jnative_ui_destroy(int32_t id) {
    JnUiPeer *p = peer(id);
    if (p == NULL) {
        return;
    }
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_destroy(id);
    }
    pthread_mutex_lock(&g_lock);
    if (!p->disposed) {
        if (p->kind == JN_UI_KIND_WINDOW) {
            g_live_windows--;
        } else {
            g_live_controls--;
        }
        g_live_handles--;
        p->disposed = 1;
        free(p->text);
        free(p->title);
        free(p->column_titles);
        p->text = NULL;
        p->title = NULL;
        p->column_titles = NULL;
        for (int i = 0; i < JN_UI_ITEM_MAX; i++) {
            free(p->items[i]);
            p->items[i] = NULL;
        }
        p->kind = 0;
    }
    pthread_mutex_unlock(&g_lock);
}

void jnative_ui_show(int32_t id) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    p->visible = 1;
    if (g_platform_active && !g_headless) jnative_ui_platform_show(id);
}
void jnative_ui_hide(int32_t id) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    p->visible = 0;
    if (g_platform_active && !g_headless) jnative_ui_platform_hide(id);
}
void jnative_ui_set_title(int32_t id, const char *title) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    free(p->title);
    p->title = jn_ui_strdup(title);
    if (g_platform_active && !g_headless) jnative_ui_platform_set_title(id, title);
}
void jnative_ui_set_text(int32_t id, const char *text) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    free(p->text);
    p->text = jn_ui_strdup(text);
    if (g_platform_active && !g_headless) jnative_ui_platform_set_text(id, text);
}
void jnative_ui_store_text(int32_t id, const char *text) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    free(p->text);
    p->text = jn_ui_strdup(text);
}
char *jnative_ui_copy_text(int32_t id) {
    JnUiPeer *p = peer(id);
    if (p == NULL || p->text == NULL) {
        return jn_ui_strdup("");
    }
    return jn_ui_strdup(p->text);
}
void jnative_ui_set_bounds(int32_t id, int32_t x, int32_t y, int32_t w, int32_t h) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    p->x = x; p->y = y; p->w = w; p->h = h;
    if (g_platform_active && !g_headless) jnative_ui_platform_set_bounds(id, x, y, w, h);
}
void jnative_ui_set_visible(int32_t id, int32_t visible) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    p->visible = visible != 0;
    if (g_platform_active && !g_headless) jnative_ui_platform_set_visible(id, visible);
}
void jnative_ui_set_enabled(int32_t id, int32_t enabled) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    p->enabled = enabled != 0;
    if (g_platform_active && !g_headless) jnative_ui_platform_set_enabled(id, enabled);
}
int32_t jnative_ui_get_enabled(int32_t id) {
    JnUiPeer *p = peer(id);
    return p == NULL ? 0 : (p->enabled ? 1 : 0);
}
void jnative_ui_focus(int32_t id) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    if (g_platform_active && !g_headless) jnative_ui_platform_focus(id);
}
void jnative_ui_set_content(int32_t windowId, int32_t childId) {
    JnUiPeer *w = peer(windowId);
    JnUiPeer *c = peer(childId);
    if (w == NULL || c == NULL) return;
    c->parent = windowId;
    if (g_platform_active && !g_headless) jnative_ui_platform_set_content(windowId, childId);
}

void jnative_ui_reparent(int32_t childId, int32_t parentId) {
    JnUiPeer *c = peer(childId);
    JnUiPeer *p = peer(parentId);
    if (c == NULL || p == NULL) return;
    c->parent = parentId;
    if (g_platform_active && !g_headless) jnative_ui_platform_reparent(childId, parentId);
}

void jnative_ui_set_selected(int32_t id, int32_t selected) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    p->selected = selected != 0;
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_set_selected(id, p->selected ? 1 : 0);
    }
}
int32_t jnative_ui_get_selected(int32_t id) {
    JnUiPeer *p = peer(id);
    return p == NULL ? 0 : p->selected;
}
void jnative_ui_set_value_i32(int32_t id, int32_t value) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    p->value_i32 = value;
    /* Button/label/textfield chrome styles via value channel. */
    if (g_platform_active && !g_headless
            && (p->kind == JN_UI_KIND_BUTTON || p->kind == JN_UI_KIND_LABEL
                || p->kind == JN_UI_KIND_TEXTFIELD)) {
        jnative_ui_platform_set_control_style(id, value);
    }
}

void jnative_ui_set_visual(int32_t id, int32_t bg_rgba, int32_t fg_rgba,
                           int32_t corner_radius, int32_t font_size, int32_t text_align) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_set_visual(id, bg_rgba, fg_rgba, corner_radius, font_size, text_align);
    }
}

void jnative_ui_window_set_min_size(int32_t windowId, int32_t w, int32_t h) {
    JnUiPeer *p = peer(windowId);
    if (p == NULL) return;
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_window_set_min_size(windowId, w, h);
    }
}

__attribute__((weak)) void jnative_ui_platform_window_center_on_parent(
        int32_t childId, int32_t parentId) {
    (void)childId;
    (void)parentId;
}

void jnative_ui_window_center_on_parent(int32_t childId, int32_t parentId) {
    JnUiPeer *p = peer(childId);
    if (p == NULL) return;
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_window_center_on_parent(childId, parentId);
    }
}

void jnative_ui_button_flash(int32_t id, int32_t pressed) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_button_flash(id, pressed);
    }
}
int32_t jnative_ui_get_value_i32(int32_t id) {
    JnUiPeer *p = peer(id);
    return p == NULL ? 0 : p->value_i32;
}
void jnative_ui_set_progress(int32_t id, double progress) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    if (progress < 0.0) progress = 0.0;
    if (progress > 1.0) progress = 1.0;
    p->progress = progress;
}
double jnative_ui_get_progress(int32_t id) {
    JnUiPeer *p = peer(id);
    return p == NULL ? 0.0 : p->progress;
}
void jnative_ui_set_item_count(int32_t id, int32_t count) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    if (count < 0) count = 0;
    p->item_count = count;
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_items_changed(id);
    }
}
int32_t jnative_ui_get_item_count(int32_t id) {
    JnUiPeer *p = peer(id);
    return p == NULL ? 0 : p->item_count;
}
const char *jnative_ui_item_text(int32_t id, int32_t index) {
    JnUiPeer *p = peer(id);
    if (p == NULL || index < 0 || index >= p->item_count) return "";
    if (p->items[index] == NULL) return "";
    return p->items[index];
}
int32_t jnative_ui_item_parent(int32_t id, int32_t index) {
    JnUiPeer *p = peer(id);
    if (p == NULL || index < 0 || index >= p->item_count) return -1;
    return p->item_parents[index];
}
void jnative_ui_table_set_columns(int32_t id, const char *titles_tsv) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    free(p->column_titles);
    p->column_titles = jn_ui_strdup(titles_tsv);
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_table_set_columns(id, p->column_titles);
        jnative_ui_platform_items_changed(id);
    }
}
const char *jnative_ui_table_columns(int32_t id) {
    JnUiPeer *p = peer(id);
    if (p == NULL || p->column_titles == NULL) return "";
    return p->column_titles;
}
void jnative_ui_set_selected_index(int32_t id, int32_t index) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    p->selected_index = index;
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_set_selected_index(id, index);
    }
}
int32_t jnative_ui_get_selected_index(int32_t id) {
    JnUiPeer *p = peer(id);
    return p == NULL ? -1 : p->selected_index;
}
void jnative_ui_set_visible_rows(int32_t id, int32_t rows) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    p->visible_rows = rows > 0 ? rows : 1;
}
int32_t jnative_ui_get_visible_rows(int32_t id) {
    JnUiPeer *p = peer(id);
    return p == NULL ? 0 : p->visible_rows;
}

static void clear_items(JnUiPeer *p) {
    for (int i = 0; i < JN_UI_ITEM_MAX; i++) {
        free(p->items[i]);
        p->items[i] = NULL;
        p->item_parents[i] = -1;
        p->item_expanded[i] = 0;
    }
    p->item_count = 0;
}

void jnative_ui_combo_clear(int32_t id) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    clear_items(p);
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_items_changed(id);
    }
}
void jnative_ui_combo_add_item(int32_t id, const char *text) {
    JnUiPeer *p = peer(id);
    if (p == NULL || p->item_count >= JN_UI_ITEM_MAX) return;
    p->items[p->item_count] = jn_ui_strdup(text);
    p->item_count++;
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_items_changed(id);
    }
}
void jnative_ui_list_clear(int32_t id) {
    jnative_ui_combo_clear(id);
}
void jnative_ui_list_add_item(int32_t id, const char *text) {
    jnative_ui_combo_add_item(id, text);
}
void jnative_ui_tree_clear(int32_t id) {
    jnative_ui_combo_clear(id);
}
void jnative_ui_tree_add_item(int32_t id, int32_t parentIndex, const char *text) {
    JnUiPeer *p = peer(id);
    if (p == NULL || p->item_count >= JN_UI_ITEM_MAX) return;
    int idx = p->item_count;
    p->items[idx] = jn_ui_strdup(text);
    p->item_parents[idx] = parentIndex;
    p->item_expanded[idx] = 0;
    p->item_count++;
}
void jnative_ui_tree_set_expanded(int32_t id, int32_t index, int32_t expanded) {
    JnUiPeer *p = peer(id);
    if (p == NULL || index < 0 || index >= p->item_count) return;
    p->item_expanded[index] = expanded != 0;
}
int32_t jnative_ui_tree_is_expanded(int32_t id, int32_t index) {
    JnUiPeer *p = peer(id);
    if (p == NULL || index < 0 || index >= p->item_count) return 0;
    return p->item_expanded[index];
}
void jnative_ui_tab_add(int32_t id, const char *title, int32_t contentId) {
    JnUiPeer *p = peer(id);
    if (p == NULL || p->tab_count >= JN_UI_ITEM_MAX) return;
    p->items[p->tab_count] = jn_ui_strdup(title);
    p->item_parents[p->tab_count] = contentId;
    p->tab_count++;
    p->item_count = p->tab_count;
}
void jnative_ui_tab_set_selected(int32_t id, int32_t index) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    p->tab_selected = index;
    p->selected_index = index;
}
int32_t jnative_ui_tab_get_selected(int32_t id) {
    JnUiPeer *p = peer(id);
    return p == NULL ? 0 : p->tab_selected;
}
void jnative_ui_split_set_divider(int32_t id, double pos) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    if (pos < 0.05) pos = 0.05;
    if (pos > 0.95) pos = 0.95;
    p->divider = pos;
    p->value_i32 = (int32_t)(pos * 100.0 + 0.5);
}
double jnative_ui_split_get_divider(int32_t id) {
    JnUiPeer *p = peer(id);
    return p == NULL ? 0.5 : p->divider;
}

int32_t jnative_ui_menu_create_bar(void) {
    return jnative_ui_create_control(JN_UI_KIND_PANEL, 0, "menubar");
}
int32_t jnative_ui_menu_create(const char *title) {
    return jnative_ui_create_control(JN_UI_KIND_PANEL, 0, title);
}
int32_t jnative_ui_menu_create_item(const char *text) {
    return jnative_ui_create_control(JN_UI_KIND_MENUITEM, 0, text);
}
int32_t jnative_ui_menu_create_separator(void) {
    return jnative_ui_create_control(JN_UI_KIND_SEPARATOR, 0, "-");
}
void jnative_ui_menu_add(int32_t parentMenuId, int32_t childId) {
    JnUiPeer *p = peer(parentMenuId);
    JnUiPeer *c = peer(childId);
    if (p == NULL || c == NULL) return;
    if (p->menu_child_count >= 64) return;
    p->menu_children[p->menu_child_count++] = childId;
    c->parent = parentMenuId;
}
void jnative_ui_set_app_name(const char *name) {
    free(g_app_name);
    g_app_name = jn_ui_strdup(name != NULL ? name : "JNative");
}

const char *jnative_ui_get_app_name(void) {
    if (g_app_name == NULL || g_app_name[0] == '\0') {
        return "JNative";
    }
    return g_app_name;
}

void jnative_ui_menu_set_shortcut(int32_t itemId, int32_t keyCode, int32_t mods) {
    JnUiPeer *p = peer(itemId);
    if (p == NULL) {
        return;
    }
    p->shortcut_key = keyCode;
    p->shortcut_mods = mods;
}

int32_t jnative_ui_menu_get_shortcut_key(int32_t itemId) {
    JnUiPeer *p = peer(itemId);
    return p == NULL ? 0 : p->shortcut_key;
}

int32_t jnative_ui_menu_get_shortcut_mods(int32_t itemId) {
    JnUiPeer *p = peer(itemId);
    return p == NULL ? 0 : p->shortcut_mods;
}

void jnative_ui_request_quit(void) {
    /* Deliver on UI thread via task pump (handle 0 + QUIT_REQUEST). */
    if (g_dispatch != NULL) {
        g_dispatch(0, JN_UI_EVENT_QUIT_REQUEST);
    } else {
        jnative_ui_quit();
    }
}

void jnative_ui_window_set_menubar(int32_t windowId, int32_t menuBarId) {
    JnUiPeer *w = peer(windowId);
    JnUiPeer *m = peer(menuBarId);
    if (w == NULL || m == NULL) return;
    m->parent = windowId;
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_set_menubar(windowId, menuBarId);
    }
}

int32_t jnative_ui_peer_kind(int32_t id) {
    JnUiPeer *p = peer(id);
    return p == NULL ? 0 : p->kind;
}

const char *jnative_ui_peer_text(int32_t id) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return "";
    if (p->text != NULL) return p->text;
    if (p->title != NULL) return p->title;
    return "";
}

int32_t jnative_ui_menu_child_count(int32_t id) {
    JnUiPeer *p = peer(id);
    return p == NULL ? 0 : p->menu_child_count;
}

int32_t jnative_ui_menu_child_at(int32_t id, int32_t index) {
    JnUiPeer *p = peer(id);
    if (p == NULL || index < 0 || index >= p->menu_child_count) return 0;
    return p->menu_children[index];
}

void jnative_ui_close_window(int32_t windowId) {
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_close_window(windowId);
    }
    jnative_ui_destroy(windowId);
}

int32_t jnative_ui_dialog_message(int32_t windowId, int32_t type,
                                  const char *title, const char *message) {
    if (g_platform_active && !g_headless) {
        int32_t r = jnative_ui_platform_dialog_message(windowId, type, title, message);
        if (r >= 0) return r;
    }
    (void)windowId; (void)type; (void)title; (void)message;
    return g_dialog_result;
}

char *jnative_ui_dialog_file_open(int32_t windowId, const char *filter) {
    if (g_platform_active && !g_headless) {
        char *r = jnative_ui_platform_dialog_file(windowId, 0, filter);
        if (r != NULL) return r;
    }
    (void)windowId; (void)filter;
    return g_file_result != NULL ? jn_ui_strdup(g_file_result) : NULL;
}
char *jnative_ui_dialog_file_save(int32_t windowId, const char *filter) {
    if (g_platform_active && !g_headless) {
        char *r = jnative_ui_platform_dialog_file(windowId, 1, filter);
        if (r != NULL) return r;
    }
    (void)windowId; (void)filter;
    return g_file_result != NULL ? jn_ui_strdup(g_file_result) : NULL;
}
char *jnative_ui_dialog_dir_open(int32_t windowId) {
    if (g_platform_active && !g_headless) {
        char *r = jnative_ui_platform_dialog_file(windowId, 2, NULL);
        if (r != NULL) return r;
    }
    (void)windowId;
    return g_file_result != NULL ? jn_ui_strdup(g_file_result) : NULL;
}
void jnative_ui_dialog_free_path(char *path) {
    free(path);
}

/* ---- Notepad-class editor natives: weak defaults (Cocoa/Win32 override) ---- */
__attribute__((weak)) char *jnative_ui_dialog_file_ex(int32_t windowId, int32_t save,
        const char *filterSpec, const char *defaultName, const char *encLabels, int32_t encIn) {
    (void)defaultName; (void)encLabels; (void)encIn;
    return save ? jnative_ui_dialog_file_save(windowId, filterSpec)
                : jnative_ui_dialog_file_open(windowId, filterSpec);
}
__attribute__((weak)) int32_t jnative_ui_dialog_last_enc(void) { return 0; }
__attribute__((weak)) char *jnative_ui_dialog_font(int32_t windowId, const char *family,
        int32_t size10, int32_t weight, int32_t italic) {
    (void)windowId; (void)family; (void)size10; (void)weight; (void)italic;
    return NULL;
}
__attribute__((weak)) void jnative_ui_window_set_dialog(int32_t childId, int32_t ownerId) {
    (void)childId; (void)ownerId;
}
__attribute__((weak)) void jnative_ui_window_activate(int32_t id) { (void)id; }
__attribute__((weak)) int32_t jnative_ui_ta_get_sel_start(int32_t id) { (void)id; return 0; }
__attribute__((weak)) int32_t jnative_ui_ta_get_sel_end(int32_t id) { (void)id; return 0; }
__attribute__((weak)) void jnative_ui_ta_set_sel(int32_t id, int32_t s, int32_t e) {
    (void)id; (void)s; (void)e;
}
__attribute__((weak)) void jnative_ui_ta_replace_sel(int32_t id, const char *text) {
    (void)id; (void)text;
}
__attribute__((weak)) void jnative_ui_ta_command(int32_t id, int32_t cmd) { (void)id; (void)cmd; }
__attribute__((weak)) int32_t jnative_ui_ta_can_undo(int32_t id) { (void)id; return 0; }
__attribute__((weak)) int32_t jnative_ui_ta_line_count(int32_t id) { (void)id; return 1; }
__attribute__((weak)) int32_t jnative_ui_ta_line_from_char(int32_t id, int32_t ch) {
    (void)id; (void)ch; return 0;
}
__attribute__((weak)) int32_t jnative_ui_ta_line_index(int32_t id, int32_t line) {
    (void)id; (void)line; return 0;
}
__attribute__((weak)) int32_t jnative_ui_ta_length(int32_t id) { (void)id; return 0; }
__attribute__((weak)) void jnative_ui_ta_set_font(int32_t id, const char *family, int32_t size10,
        int32_t weight, int32_t italic) {
    (void)id; (void)family; (void)size10; (void)weight; (void)italic;
}
__attribute__((weak)) void jnative_ui_ta_set_wrap(int32_t id, int32_t wrap) { (void)id; (void)wrap; }
__attribute__((weak)) int32_t jnative_ui_clipboard_has_text(void) { return 0; }
__attribute__((weak)) int32_t jnative_ui_system_ansi_codepage(void) { return 1252; }
__attribute__((weak)) char *jnative_ui_datetime_string(void) {
    char buf[96];
    time_t now = time(NULL);
    struct tm lt;
    localtime_r(&now, &lt);
    strftime(buf, sizeof buf, "%I:%M %p %m/%d/%Y", &lt);
    return jn_ui_strdup(buf);
}
__attribute__((weak)) int32_t jnative_ui_page_setup(int32_t windowId) {
    (void)windowId;
    return 0;
}
__attribute__((weak)) int32_t jnative_ui_print_text(int32_t windowId, const char *docName,
        const char *utf8Text, const char *family, int32_t size10, int32_t weight,
        int32_t italic) {
    (void)windowId; (void)docName; (void)utf8Text; (void)family; (void)size10;
    (void)weight; (void)italic;
    return 0;
}
/* Fallback: byte strings are already Latin-1 text, so decode/encode are identity. */
__attribute__((weak)) char *jnative_ui_codepage_decode(const char *byteStr, int32_t codepage) {
    (void)codepage;
    return jn_ui_strdup(byteStr != NULL ? byteStr : "");
}
__attribute__((weak)) char *jnative_ui_codepage_encode(const char *utf8, int32_t codepage) {
    (void)codepage;
    return jn_ui_strdup(utf8 != NULL ? utf8 : "");
}

void jnative_ui_post_task(void) {
    pthread_mutex_lock(&g_lock);
    g_task_queue++;
    pthread_mutex_unlock(&g_lock);
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_wakeup();
    }
}

void jnative_ui_quit(void) {
    g_quit = 1;
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_quit();
    }
}

static void jn_ui_fire_due_timers(void) {
    int64_t now = jn_now_ms();
    for (int32_t i = 1; i < JN_UI_TIMER_MAX; i++) {
        JnUiTimer *t = &g_timers[i];
        if (!t->live) {
            continue;
        }
        if (now >= t->nextMs) {
            t->nextMs = now + t->intervalMs;
            jnative_ui_emit_event(t->handleId, JN_UI_EVENT_TIMER);
        }
    }
}

void jnative_ui_run_loop(void) {
    g_running = 1;
    g_quit = 0;
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_run_loop();
        g_running = 0;
        return;
    }
    while (!g_quit) {
        int32_t tasks = 0;
        pthread_mutex_lock(&g_lock);
        tasks = g_task_queue;
        g_task_queue = 0;
        pthread_mutex_unlock(&g_lock);
        int32_t i = 0;
        while (i < tasks) {
            jnative_ui_emit_event(0, JN_UI_EVENT_TASK);
            i++;
        }
        jn_ui_fire_due_timers();
        if (g_live_windows <= 0) {
            break;
        }
        if (tasks == 0) {
            usleep(2000);
        }
    }
    g_running = 0;
}

int32_t jnative_ui_live_windows(void) { return g_live_windows; }
int32_t jnative_ui_live_controls(void) { return g_live_controls; }
int32_t jnative_ui_live_handles(void) { return g_live_handles; }
int32_t jnative_ui_queued_tasks(void) { return g_task_queue; }
int32_t jnative_ui_events_dispatched(void) { return g_events_dispatched; }

__attribute__((weak)) int32_t jnative_ui_platform_family(void) { return 3; }
__attribute__((weak)) int32_t jnative_ui_platform_get_scale_percent(int32_t windowId) {
    (void)windowId;
    return 100;
}
__attribute__((weak)) int32_t jnative_ui_platform_system_dark(void) { return -1; }
__attribute__((weak)) int32_t jnative_ui_platform_context_menu_show(
        int32_t menuId, int32_t screenX, int32_t screenY) {
    (void)menuId; (void)screenX; (void)screenY;
    return 0;
}

int32_t jnative_ui_get_scale_percent(int32_t windowId) {
    if (g_platform_active && !g_headless) {
        return jnative_ui_platform_get_scale_percent(windowId);
    }
    return 100;
}
int32_t jnative_ui_system_dark(void) {
    if (g_platform_active && !g_headless) {
        return jnative_ui_platform_system_dark();
    }
    return -1;
}
void jnative_ui_set_appearance_mode(int32_t mode) {
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_set_appearance_mode(mode);
    }
}
void jnative_ui_set_tooltip(int32_t id, const char *tip) {
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_set_tooltip(id, tip);
    }
}
void jnative_ui_set_accessible(int32_t id, const char *name, const char *description) {
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_set_accessible(id, name, description);
    }
}
void jnative_ui_set_accessible_title_ui(int32_t fieldId, int32_t labelId) {
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_set_accessible_title_ui(fieldId, labelId);
    }
}
void jnative_ui_set_next_key_view(int32_t fromId, int32_t toId) {
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_set_next_key_view(fromId, toId);
    }
}
void jnative_ui_window_set_default_button(int32_t windowId, int32_t buttonId) {
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_window_set_default_button(windowId, buttonId);
    }
}
void jnative_ui_window_set_cancel_button(int32_t windowId, int32_t buttonId) {
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_window_set_cancel_button(windowId, buttonId);
    }
}
int32_t jnative_ui_prefers_reduced_motion(void) {
    if (g_platform_active && !g_headless) {
        return jnative_ui_platform_prefers_reduced_motion();
    }
    return -1;
}
int32_t jnative_ui_prefers_increased_contrast(void) {
    if (g_platform_active && !g_headless) {
        return jnative_ui_platform_prefers_increased_contrast();
    }
    return -1;
}
void jnative_ui_set_button_symbol(int32_t id, const char *symbolName) {
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_set_button_symbol(id, symbolName);
    }
}
int32_t jnative_ui_features(void) {
    if (g_platform_active && !g_headless) {
        return jnative_ui_platform_features();
    }
    return 0;
}
int32_t jnative_ui_table_get_scroll_y(int32_t id) {
    if (g_platform_active && !g_headless) {
        return jnative_ui_platform_table_get_scroll_y(id);
    }
    return -1;
}
void jnative_ui_table_set_scroll_y(int32_t id, int32_t y) {
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_table_set_scroll_y(id, y);
    }
}
int32_t jnative_ui_context_menu_show(int32_t menuId, int32_t screenX, int32_t screenY) {
    if (g_platform_active && !g_headless) {
        return jnative_ui_platform_context_menu_show(menuId, screenX, screenY);
    }
    return 0;
}

void jnative_ui_headless_click(int32_t id) {
    JnUiPeer *p = peer(id);
    if (p != NULL && p->enabled) {
        jnative_ui_emit_event(id, JN_UI_EVENT_CLICK);
    }
}
void jnative_ui_headless_close(int32_t id) {
    JnUiPeer *p = peer(id);
    if (p != NULL && p->kind == JN_UI_KIND_WINDOW) {
        jnative_ui_emit_event(id, JN_UI_EVENT_CLOSE);
    }
}
void jnative_ui_headless_toggle(int32_t id) {
    JnUiPeer *p = peer(id);
    if (p == NULL || !p->enabled) return;
    p->selected = !p->selected;
    jnative_ui_emit_event_ex(id, JN_UI_EVENT_TOGGLE, p->selected, 0, 0, 0);
    jnative_ui_emit_event(id, JN_UI_EVENT_CHANGE);
}
void jnative_ui_headless_select(int32_t id, int32_t index) {
    JnUiPeer *p = peer(id);
    if (p == NULL) return;
    p->selected_index = index;
    jnative_ui_emit_event_ex(id, JN_UI_EVENT_SELECT, index, 0, 0, 0);
}
void jnative_ui_headless_change(int32_t id) {
    jnative_ui_emit_event(id, JN_UI_EVENT_CHANGE);
}
void jnative_ui_headless_action(int32_t id) {
    jnative_ui_emit_event(id, JN_UI_EVENT_ACTION);
}
void jnative_ui_headless_key(int32_t id, int32_t keyCode, int32_t mods, int32_t down) {
    jnative_ui_emit_event_ex(id, JN_UI_EVENT_KEY, down ? JN_UI_KEY_DOWN : JN_UI_KEY_UP,
                             keyCode, mods, 0);
}
void jnative_ui_headless_mouse(int32_t id, int32_t type, int32_t x, int32_t y, int32_t button) {
    jnative_ui_emit_event_ex(id, JN_UI_EVENT_MOUSE, type, x, y, button);
}
void jnative_ui_headless_focus(int32_t id, int32_t gained) {
    jnative_ui_emit_event_ex(id, JN_UI_EVENT_FOCUS,
                             gained ? JN_UI_FOCUS_GAINED : JN_UI_FOCUS_LOST, 0, 0, 0);
}
void jnative_ui_headless_set_dialog_result(int32_t result) {
    g_dialog_result = result;
}
void jnative_ui_headless_set_file_result(const char *path) {
    free(g_file_result);
    g_file_result = path != NULL ? jn_ui_strdup(path) : NULL;
}
void jnative_ui_headless_paint(int32_t id) {
    jnative_ui_emit_event(id, JN_UI_EVENT_PAINT);
}
void jnative_ui_headless_timer_fire(int32_t id) {
    jnative_ui_emit_event(id, JN_UI_EVENT_TIMER);
}

void jnative_ui_canvas_invalidate(int32_t id) {
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_canvas_invalidate(id);
    } else {
        jnative_ui_emit_event(id, JN_UI_EVENT_PAINT);
    }
}

/* Saves the window content as a PNG (platform permitting); returns 1 on success. */
int32_t jnative_ui_window_snapshot(int32_t id, const char *path) {
    if (g_platform_active && !g_headless) {
        return jnative_ui_platform_window_snapshot(id, path);
    }
    return 0;
}

/* Panel options: bit0 clip children to the panel bounds, bit1 deliver wheel events to the panel. */
void jnative_ui_panel_configure(int32_t id, int32_t flags) {
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_panel_configure(id, flags);
    }
}

/* A transparent canvas lets the controls underneath show through (design-surface overlays). */
void jnative_ui_canvas_set_transparent(int32_t id, int32_t on) {
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_canvas_set_transparent(id, on);
    }
}

void jnative_ui_canvas_present(int32_t id, int32_t surfaceId) {
    int32_t w = 0, h = 0;
    const uint8_t *px = jnative_gfx_surface_pixels(surfaceId, &w, &h);
    if (px == NULL || w <= 0 || h <= 0) {
        return;
    }
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_canvas_present(id, px, w, h);
    }
}

int32_t jnative_ui_timer_start(int32_t intervalMs, int32_t handleId) {
    if (intervalMs < 1) {
        intervalMs = 1;
    }
    for (int32_t i = 1; i < JN_UI_TIMER_MAX; i++) {
        if (!g_timers[i].live) {
            g_timers[i].live = 1;
            g_timers[i].handleId = handleId;
            g_timers[i].intervalMs = intervalMs;
            g_timers[i].nextMs = jn_now_ms() + intervalMs;
            g_live_timers++;
            if (g_platform_active && !g_headless) {
                jnative_ui_platform_timer_arm(i, intervalMs, handleId);
            }
            return i;
        }
    }
    return -1;
}

void jnative_ui_timer_stop(int32_t timerId) {
    if (timerId <= 0 || timerId >= JN_UI_TIMER_MAX) {
        return;
    }
    if (!g_timers[timerId].live) {
        return;
    }
    g_timers[timerId].live = 0;
    g_live_timers--;
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_timer_disarm(timerId);
    }
}

/* ---- Clipboard ---- */
static char *g_clipboard = NULL;

__attribute__((weak)) int32_t jnative_ui_platform_clipboard_set(const char *text) {
    (void)text; return 0;
}
__attribute__((weak)) char *jnative_ui_platform_clipboard_get(void) {
    return NULL;
}

int32_t jnative_clipboard_set_text(const char *text) {
    free(g_clipboard);
    g_clipboard = text != NULL ? jn_ui_strdup(text) : NULL;
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_clipboard_set(text);
    }
    return 1;
}

char *jnative_clipboard_get_text(void) {
    if (g_platform_active && !g_headless) {
        char *p = jnative_ui_platform_clipboard_get();
        if (p != NULL) {
            return p;
        }
    }
    return g_clipboard != NULL ? jn_ui_strdup(g_clipboard) : NULL;
}

void jnative_clipboard_free(char *text) {
    free(text);
}

int32_t jnative_process_run(const char *command) {
    if (command == NULL || command[0] == '\0') {
        return -1;
    }
    int rc = system(command);
    if (rc == -1) {
        return -1;
    }
#if defined(WEXITSTATUS)
    if (WIFEXITED(rc)) {
        return WEXITSTATUS(rc);
    }
#endif
    return rc;
}

int32_t jnative_now_ms(void) {
    struct timeval tv;
    gettimeofday(&tv, NULL);
    long long ms = (long long)tv.tv_sec * 1000 + (long long)tv.tv_usec / 1000;
    return (int32_t)ms;
}

/* ---- Windows-only canvas / DPI extensions: inert stubs so UiLib links everywhere. ---- */
void jnative_ui_set_dpi_aware(int32_t v) { (void)v; }
int32_t jnative_ui_get_dpi(int32_t id) { (void)id; return 96; }
void jnative_ui_set_flat(int32_t id) { (void)id; }
void jnative_ui_set_placeholder(int32_t id, const char *text) { (void)id; (void)text; }
void jnative_ui_window_set_caption(int32_t w, int32_t cap, int32_t txt, int32_t dark) {
    (void)w; (void)cap; (void)txt; (void)dark;
}
int32_t jnative_ui_key_state(int32_t vk) { (void)vk; return 0; }
int32_t jnative_ui_cursor_screen_x(void) { return 0; }
int32_t jnative_ui_cursor_screen_y(void) { return 0; }
int32_t jnative_ui_cv_font(const char *face, int32_t size10, int32_t weight, int32_t italic) {
    (void)face; (void)size10; (void)weight; (void)italic; return 0;
}
int32_t jnative_ui_cv_text_width(int32_t f, const char *t) { (void)f; (void)t; return 0; }
int32_t jnative_ui_cv_text_height(int32_t f, const char *t, int32_t w) { (void)f; (void)t; (void)w; return 0; }
void jnative_ui_cv_fill_rect(int32_t x, int32_t y, int32_t w, int32_t h, int32_t a) {
    (void)x; (void)y; (void)w; (void)h; (void)a;
}
void jnative_ui_cv_round_rect(int32_t x, int32_t y, int32_t w, int32_t h, int32_t r,
                              int32_t f, int32_t s, int32_t sw) {
    (void)x; (void)y; (void)w; (void)h; (void)r; (void)f; (void)s; (void)sw;
}
void jnative_ui_cv_line(int32_t x1, int32_t y1, int32_t x2, int32_t y2, int32_t a, int32_t w) {
    (void)x1; (void)y1; (void)x2; (void)y2; (void)a; (void)w;
}
void jnative_ui_cv_poly_begin(void) {}
void jnative_ui_cv_poly_pt(int32_t x, int32_t y) { (void)x; (void)y; }
void jnative_ui_cv_poly_end(int32_t f, int32_t s, int32_t w) { (void)f; (void)s; (void)w; }
void jnative_ui_cv_text(int32_t x, int32_t y, int32_t w, int32_t h, const char *t,
                        int32_t a, int32_t f, int32_t fl) {
    (void)x; (void)y; (void)w; (void)h; (void)t; (void)a; (void)f; (void)fl;
}
int32_t jnative_ui_cv_icon(const char *p, int32_t x, int32_t y, int32_t s) {
    (void)p; (void)x; (void)y; (void)s; return 0;
}
void jnative_ui_cv_clip_push(int32_t x, int32_t y, int32_t w, int32_t h) {
    (void)x; (void)y; (void)w; (void)h;
}
void jnative_ui_cv_clip_pop(void) {}
__attribute__((weak)) void jnative_ui_platform_set_cursor(int32_t k) { (void)k; }
void jnative_ui_cv_set_cursor(int32_t k) {
    if (g_platform_active && !g_headless) {
        jnative_ui_platform_set_cursor(k);
    }
}
void jnative_ui_cv_glyph(int32_t x, int32_t y, int32_t w, int32_t h, int32_t cp, int32_t a, int32_t f, int32_t fl) {
    (void)x; (void)y; (void)w; (void)h; (void)cp; (void)a; (void)f; (void)fl;
}
