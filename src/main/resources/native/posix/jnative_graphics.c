/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Author: 张晓达 */
/* JNative 0.19 — software raster graphics + PNG (zlib). Platform-neutral C ABI. */
#include "jnative_graphics.h"

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <zlib.h>

#ifndef JN_GFX_MAX
#define JN_GFX_MAX 512
#endif
#ifndef JN_GFX_PATH_MAX
#define JN_GFX_PATH_MAX 4096
#endif
#ifndef JN_GFX_STACK
#define JN_GFX_STACK 32
#endif

typedef struct {
    int32_t live;
    int32_t w, h;
    uint32_t *px; /* RGBA8888 little-endian: R|G<<8|B<<16|A<<24 */
} JnSurface;

typedef struct {
    int32_t op; /* 0 move 1 line 2 close 3 quad 4 cubic */
    double x, y, a, b, c, d;
} JnPathCmd;

typedef struct {
    int32_t live;
    JnPathCmd cmds[JN_GFX_PATH_MAX];
    int32_t n;
} JnPath;

typedef struct {
    double tx, ty, sx, sy, rot;
    int32_t clip;
    int32_t cx, cy, cw, ch;
    int32_t rgba;
    double stroke;
    int32_t fontSize;
    int32_t fontStyle;
    char family[64];
} JnGState;

typedef struct {
    int32_t live;
    int32_t surfaceId;
    int32_t valid;
    JnGState st;
    JnGState stack[JN_GFX_STACK];
    int32_t sp;
} JnGfx;

static JnSurface g_surf[JN_GFX_MAX];
static JnPath g_path[JN_GFX_MAX];
static JnGfx g_gfx[JN_GFX_MAX];
static int32_t g_live_surf, g_live_path, g_live_gfx, g_live_fonts;

/* Tiny 8x8 bitmap font for ASCII 32..126. */
static uint8_t FONT8[95][8];
static int font_ready = 0;

static const uint8_t FONT8_DATA[95][8] = {
  { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, /* 32 */
  { 0x30, 0x30, 0x30, 0x30, 0x30, 0x00, 0x30, 0x00 }, /* 33 */
  { 0x6C, 0x6C, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, /* 34 */
  { 0x36, 0x7E, 0xD8, 0x36, 0x7E, 0xD8, 0x00, 0x00 }, /* 35 */
  { 0x30, 0x7E, 0xD8, 0xFC, 0x6C, 0xFC, 0x30, 0x00 }, /* 36 */
  { 0xC6, 0xCC, 0x18, 0x30, 0x60, 0xCC, 0xC6, 0x00 }, /* 37 */
  { 0x70, 0xD8, 0xD8, 0x70, 0xDB, 0xCC, 0x6D, 0x00 }, /* 38 */
  { 0x30, 0x30, 0x60, 0x00, 0x00, 0x00, 0x00, 0x00 }, /* 39 */
  { 0x18, 0x30, 0x60, 0xC0, 0x60, 0x30, 0x18, 0x00 }, /* 40 */
  { 0x60, 0x30, 0x18, 0x0C, 0x18, 0x30, 0x60, 0x00 }, /* 41 */
  { 0x00, 0x30, 0xDB, 0x7E, 0xDB, 0x30, 0x00, 0x00 }, /* 42 */
  { 0x00, 0x30, 0x30, 0x7E, 0x30, 0x30, 0x00, 0x00 }, /* 43 */
  { 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x30, 0x60 }, /* 44 */
  { 0x00, 0x00, 0x00, 0x7E, 0x00, 0x00, 0x00, 0x00 }, /* 45 */
  { 0x00, 0x00, 0x00, 0x00, 0x00, 0x30, 0x30, 0x00 }, /* 46 */
  { 0x0C, 0x0C, 0x18, 0x30, 0x60, 0xC0, 0xC0, 0x00 }, /* 47 */
  { 0x78, 0xCC, 0xCC, 0xCC, 0xCC, 0xCC, 0x78, 0x00 }, /* 48 */
  { 0x30, 0x70, 0x30, 0x30, 0x30, 0x30, 0x78, 0x00 }, /* 49 */
  { 0x78, 0xCC, 0x0C, 0x18, 0x30, 0x60, 0xFC, 0x00 }, /* 50 */
  { 0x78, 0xCC, 0x0C, 0x38, 0x0C, 0xCC, 0x78, 0x00 }, /* 51 */
  { 0x18, 0x38, 0x58, 0xD8, 0xFC, 0x18, 0x18, 0x00 }, /* 52 */
  { 0xFC, 0xC0, 0xF8, 0x0C, 0x0C, 0xCC, 0x78, 0x00 }, /* 53 */
  { 0x38, 0xC0, 0xC0, 0xF8, 0xCC, 0xCC, 0x78, 0x00 }, /* 54 */
  { 0xFC, 0x0C, 0x18, 0x30, 0x30, 0x30, 0x30, 0x00 }, /* 55 */
  { 0x78, 0xCC, 0xCC, 0x78, 0xCC, 0xCC, 0x78, 0x00 }, /* 56 */
  { 0x78, 0xCC, 0xCC, 0x7C, 0x0C, 0x18, 0x70, 0x00 }, /* 57 */
  { 0x00, 0x30, 0x30, 0x00, 0x00, 0x30, 0x30, 0x00 }, /* 58 */
  { 0x00, 0x30, 0x30, 0x00, 0x30, 0x60, 0xC0, 0x00 }, /* 59 */
  { 0x0C, 0x18, 0x30, 0x60, 0x30, 0x18, 0x0C, 0x00 }, /* 60 */
  { 0x00, 0x00, 0x7E, 0x00, 0x7E, 0x00, 0x00, 0x00 }, /* 61 */
  { 0xC0, 0x60, 0x30, 0x18, 0x30, 0x60, 0xC0, 0x00 }, /* 62 */
  { 0x78, 0xCC, 0x0C, 0x18, 0x30, 0x00, 0x30, 0x00 }, /* 63 */
  { 0x78, 0xCC, 0xFC, 0xD4, 0xFC, 0xC0, 0x78, 0x00 }, /* 64 */
  { 0x78, 0xCC, 0xCC, 0xFC, 0xCC, 0xCC, 0xCC, 0x00 }, /* 65 */
  { 0xF8, 0xCC, 0xCC, 0xF8, 0xCC, 0xCC, 0xF8, 0x00 }, /* 66 */
  { 0x78, 0xCC, 0xC0, 0xC0, 0xC0, 0xCC, 0x78, 0x00 }, /* 67 */
  { 0xF8, 0xCC, 0xCC, 0xCC, 0xCC, 0xCC, 0xF8, 0x00 }, /* 68 */
  { 0xFC, 0xC0, 0xC0, 0xF8, 0xC0, 0xC0, 0xFC, 0x00 }, /* 69 */
  { 0xFC, 0xC0, 0xC0, 0xF8, 0xC0, 0xC0, 0xC0, 0x00 }, /* 70 */
  { 0x78, 0xCC, 0xC0, 0xDC, 0xCC, 0xCC, 0x78, 0x00 }, /* 71 */
  { 0xCC, 0xCC, 0xCC, 0xFC, 0xCC, 0xCC, 0xCC, 0x00 }, /* 72 */
  { 0x78, 0x30, 0x30, 0x30, 0x30, 0x30, 0x78, 0x00 }, /* 73 */
  { 0x3C, 0x18, 0x18, 0x18, 0xD8, 0xD8, 0x70, 0x00 }, /* 74 */
  { 0xCC, 0xD8, 0xF0, 0xE0, 0xF0, 0xD8, 0xCC, 0x00 }, /* 75 */
  { 0xC0, 0xC0, 0xC0, 0xC0, 0xC0, 0xC0, 0xFC, 0x00 }, /* 76 */
  { 0xC6, 0xEE, 0xFE, 0xD6, 0xC6, 0xC6, 0xC6, 0x00 }, /* 77 */
  { 0xCC, 0xEC, 0xFC, 0xDC, 0xCE, 0xCC, 0xCC, 0x00 }, /* 78 */
  { 0x78, 0xCC, 0xCC, 0xCC, 0xCC, 0xCC, 0x78, 0x00 }, /* 79 */
  { 0xF8, 0xCC, 0xCC, 0xF8, 0xC0, 0xC0, 0xC0, 0x00 }, /* 80 */
  { 0x78, 0xCC, 0xCC, 0xCC, 0xD8, 0xCC, 0x76, 0x00 }, /* 81 */
  { 0xF8, 0xCC, 0xCC, 0xF8, 0xF0, 0xD8, 0xCC, 0x00 }, /* 82 */
  { 0x78, 0xCC, 0xC0, 0x78, 0x0C, 0xCC, 0x78, 0x00 }, /* 83 */
  { 0xFC, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x00 }, /* 84 */
  { 0xCC, 0xCC, 0xCC, 0xCC, 0xCC, 0xCC, 0x78, 0x00 }, /* 85 */
  { 0xCC, 0xCC, 0xCC, 0xCC, 0xCC, 0x78, 0x30, 0x00 }, /* 86 */
  { 0xC6, 0xC6, 0xD6, 0xD6, 0xFE, 0x6C, 0x6C, 0x00 }, /* 87 */
  { 0xCC, 0xCC, 0x78, 0x30, 0x78, 0xCC, 0xCC, 0x00 }, /* 88 */
  { 0xCC, 0xCC, 0xCC, 0x78, 0x30, 0x30, 0x30, 0x00 }, /* 89 */
  { 0xFC, 0x0C, 0x18, 0x30, 0x60, 0xC0, 0xFC, 0x00 }, /* 90 */
  { 0x78, 0x60, 0xC0, 0xC0, 0xC0, 0x60, 0xF0, 0x00 }, /* 91 */
  { 0xC0, 0xC0, 0x60, 0x30, 0x18, 0x0C, 0x0C, 0x00 }, /* 92 */
  { 0x78, 0x18, 0x0C, 0x0C, 0x0C, 0x18, 0x78, 0x00 }, /* 93 */
  { 0x30, 0x78, 0xCC, 0x00, 0x00, 0x00, 0x00, 0x00 }, /* 94 */
  { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x7E, 0x00 }, /* 95 */
  { 0x60, 0x30, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 }, /* 96 */
  { 0x00, 0x00, 0x78, 0x0C, 0x7C, 0xCC, 0x7C, 0x00 }, /* 97 */
  { 0xC0, 0xC0, 0xF8, 0xCC, 0xCC, 0xCC, 0xF8, 0x00 }, /* 98 */
  { 0x00, 0x00, 0x78, 0xC0, 0xC0, 0xCC, 0x78, 0x00 }, /* 99 */
  { 0x0C, 0x0C, 0x7C, 0xCC, 0xCC, 0xCC, 0x7C, 0x00 }, /* 100 */
  { 0x00, 0x00, 0x78, 0xCC, 0xFC, 0xC0, 0x78, 0x00 }, /* 101 */
  { 0x38, 0x6C, 0x60, 0xF0, 0x60, 0xC0, 0xC0, 0x00 }, /* 102 */
  { 0x00, 0x00, 0x7C, 0xCC, 0xCC, 0x7C, 0x0C, 0x78 }, /* 103 */
  { 0xC0, 0xC0, 0xF8, 0xCC, 0xCC, 0xCC, 0xCC, 0x00 }, /* 104 */
  { 0x30, 0x00, 0x70, 0x30, 0x30, 0x30, 0x78, 0x00 }, /* 105 */
  { 0x18, 0x00, 0x38, 0x0C, 0x0C, 0xCC, 0x78, 0x00 }, /* 106 */
  { 0xC0, 0xC0, 0xCC, 0xD8, 0xF0, 0xD8, 0xCC, 0x00 }, /* 107 */
  { 0x70, 0x30, 0x30, 0x30, 0x30, 0x30, 0x78, 0x00 }, /* 108 */
  { 0x00, 0x00, 0xD8, 0xFE, 0xD6, 0xC6, 0xC6, 0x00 }, /* 109 */
  { 0x00, 0x00, 0xF8, 0xCC, 0xCC, 0xCC, 0xCC, 0x00 }, /* 110 */
  { 0x00, 0x00, 0x78, 0xCC, 0xCC, 0xCC, 0x78, 0x00 }, /* 111 */
  { 0x00, 0x00, 0xF8, 0xCC, 0xCC, 0xF8, 0xC0, 0xC0 }, /* 112 */
  { 0x00, 0x00, 0x7C, 0xCC, 0xCC, 0x7C, 0x0C, 0x0C }, /* 113 */
  { 0x00, 0x00, 0xDC, 0xEC, 0xC0, 0xC0, 0xC0, 0x00 }, /* 114 */
  { 0x00, 0x00, 0x7C, 0xC0, 0x78, 0x0C, 0xF8, 0x00 }, /* 115 */
  { 0x30, 0x30, 0x7C, 0x30, 0x30, 0x34, 0x18, 0x00 }, /* 116 */
  { 0x00, 0x00, 0xCC, 0xCC, 0xCC, 0xCC, 0x7C, 0x00 }, /* 117 */
  { 0x00, 0x00, 0xCC, 0xCC, 0xCC, 0x78, 0x30, 0x00 }, /* 118 */
  { 0x00, 0x00, 0xC6, 0xD6, 0xD6, 0xFE, 0x6C, 0x00 }, /* 119 */
  { 0x00, 0x00, 0xCC, 0x78, 0x30, 0x78, 0xCC, 0x00 }, /* 120 */
  { 0x00, 0x00, 0xCC, 0xCC, 0xCC, 0x7C, 0x0C, 0x78 }, /* 121 */
  { 0x00, 0x00, 0xFC, 0x18, 0x30, 0x60, 0xFC, 0x00 }, /* 122 */
  { 0x1C, 0x30, 0x30, 0x60, 0x30, 0x30, 0x1C, 0x00 }, /* 123 */
  { 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x30, 0x00 }, /* 124 */
  { 0x70, 0x18, 0x18, 0x0C, 0x18, 0x18, 0x70, 0x00 }, /* 125 */
  { 0x00, 0x00, 0x6C, 0xD6, 0x00, 0x00, 0x00, 0x00 }, /* 126 */
};

static void font_init(void) {
    if (font_ready) return;
    font_ready = 1;
    memcpy(FONT8, FONT8_DATA, sizeof(FONT8));
}

static uint32_t pack_rgba(int r, int g, int b, int a) {
    return ((uint32_t)(r & 255)) | ((uint32_t)(g & 255) << 8)
         | ((uint32_t)(b & 255) << 16) | ((uint32_t)(a & 255) << 24);
}

static void blend(uint32_t *dst, uint32_t src) {
    int sa = (int)((src >> 24) & 255);
    if (sa >= 255) { *dst = src; return; }
    if (sa <= 0) return;
    int sr = (int)(src & 255), sg = (int)((src >> 8) & 255), sb = (int)((src >> 16) & 255);
    uint32_t d = *dst;
    int dr = (int)(d & 255), dg = (int)((d >> 8) & 255), db = (int)((d >> 16) & 255), da = (int)((d >> 24) & 255);
    int inv = 255 - sa;
    int or_ = (sr * sa + dr * inv) / 255;
    int og = (sg * sa + dg * inv) / 255;
    int ob = (sb * sa + db * inv) / 255;
    int oa = sa + (da * inv) / 255;
    *dst = pack_rgba(or_, og, ob, oa > 255 ? 255 : oa);
}

static JnSurface *S(int32_t id) {
    if (id <= 0 || id >= JN_GFX_MAX || !g_surf[id].live) return NULL;
    return &g_surf[id];
}
static JnGfx *G(int32_t id) {
    if (id <= 0 || id >= JN_GFX_MAX || !g_gfx[id].live) return NULL;
    return &g_gfx[id];
}
static JnPath *P(int32_t id) {
    if (id <= 0 || id >= JN_GFX_MAX || !g_path[id].live) return NULL;
    return &g_path[id];
}

static int32_t alloc_slot_surf(void) {
    for (int i = 1; i < JN_GFX_MAX; i++) if (!g_surf[i].live) return i;
    return -1;
}
static int32_t alloc_slot_gfx(void) {
    for (int i = 1; i < JN_GFX_MAX; i++) if (!g_gfx[i].live) return i;
    return -1;
}
static int32_t alloc_slot_path(void) {
    for (int i = 1; i < JN_GFX_MAX; i++) if (!g_path[i].live) return i;
    return -1;
}

static void xform(JnGfx *g, double x, double y, double *ox, double *oy) {
    double cs = cos(g->st.rot), sn = sin(g->st.rot);
    double nx = x * g->st.sx;
    double ny = y * g->st.sy;
    *ox = nx * cs - ny * sn + g->st.tx;
    *oy = nx * sn + ny * cs + g->st.ty;
}

static void put_px(JnSurface *s, JnGfx *g, int x, int y, uint32_t rgba) {
    if (s == NULL || x < 0 || y < 0 || x >= s->w || y >= s->h) return;
    if (g && g->st.clip) {
        if (x < g->st.cx || y < g->st.cy || x >= g->st.cx + g->st.cw || y >= g->st.cy + g->st.ch)
            return;
    }
    blend(&s->px[y * s->w + x], rgba);
}

static void fill_rect_i(JnSurface *s, JnGfx *g, int x, int y, int w, int h, uint32_t rgba) {
    if (w <= 0 || h <= 0) return;
    for (int yy = y; yy < y + h; yy++)
        for (int xx = x; xx < x + w; xx++)
            put_px(s, g, xx, yy, rgba);
}

int32_t jnative_gfx_surface_create(int32_t width, int32_t height) {
    if (width <= 0 || height <= 0 || width > 8192 || height > 8192) return -1;
    int32_t id = alloc_slot_surf();
    if (id < 0) return -1;
    JnSurface *s = &g_surf[id];
    memset(s, 0, sizeof(*s));
    s->live = 1;
    s->w = width;
    s->h = height;
    s->px = (uint32_t *)calloc((size_t)width * (size_t)height, sizeof(uint32_t));
    if (!s->px) { s->live = 0; return -1; }
    g_live_surf++;
    return id;
}

void jnative_gfx_surface_destroy(int32_t id) {
    JnSurface *s = S(id);
    if (!s) return;
    free(s->px);
    memset(s, 0, sizeof(*s));
    g_live_surf--;
}

int32_t jnative_gfx_surface_width(int32_t id) { JnSurface *s = S(id); return s ? s->w : 0; }
int32_t jnative_gfx_surface_height(int32_t id) { JnSurface *s = S(id); return s ? s->h : 0; }

int32_t jnative_gfx_begin(int32_t surfaceId) {
    if (!S(surfaceId)) return -1;
    int32_t id = alloc_slot_gfx();
    if (id < 0) return -1;
    JnGfx *g = &g_gfx[id];
    memset(g, 0, sizeof(*g));
    g->live = 1;
    g->valid = 1;
    g->surfaceId = surfaceId;
    g->st.sx = g->st.sy = 1.0;
    g->st.rgba = pack_rgba(0, 0, 0, 255);
    g->st.stroke = 1.0;
    g->st.fontSize = 16;
    strcpy(g->st.family, "sans");
    g_live_gfx++;
    g_live_fonts++; /* logical font descriptor in use */
    font_init();
    return id;
}

void jnative_gfx_end(int32_t gfxId) {
    JnGfx *g = G(gfxId);
    if (!g) return;
    g->valid = 0;
    g->live = 0;
    g_live_gfx--;
    if (g_live_fonts > 0) g_live_fonts--;
}

int32_t jnative_gfx_is_valid(int32_t gfxId) {
    JnGfx *g = G(gfxId);
    return g && g->valid ? 1 : 0;
}

void jnative_gfx_set_color(int32_t gfxId, int32_t rgba) {
    JnGfx *g = G(gfxId); if (!g || !g->valid) return; g->st.rgba = (uint32_t)rgba;
}
void jnative_gfx_set_color_components(int32_t gfxId, int32_t r, int32_t g, int32_t b, int32_t a) {
    jnative_gfx_set_color(gfxId, jnative_gfx_pack_rgba(r, g, b, a));
}
int32_t jnative_gfx_pack_rgba(int32_t r, int32_t g, int32_t b, int32_t a) {
    return (int32_t)pack_rgba(r, g, b, a);
}
int32_t jnative_gfx_unpack_r(int32_t rgba) { return (int32_t)(rgba & 255); }
int32_t jnative_gfx_unpack_g(int32_t rgba) { return (int32_t)((rgba >> 8) & 255); }
int32_t jnative_gfx_unpack_b(int32_t rgba) { return (int32_t)((rgba >> 16) & 255); }
int32_t jnative_gfx_unpack_a(int32_t rgba) { return (int32_t)((rgba >> 24) & 255); }
void jnative_gfx_set_stroke_width(int32_t gfxId, double width) {
    JnGfx *g = G(gfxId); if (!g || !g->valid) return; g->st.stroke = width > 0 ? width : 1.0;
}
void jnative_gfx_set_font(int32_t gfxId, const char *family, int32_t size, int32_t style) {
    JnGfx *g = G(gfxId); if (!g || !g->valid) return;
    if (family) {
        strncpy(g->st.family, family, sizeof(g->st.family) - 1);
        g->st.family[sizeof(g->st.family) - 1] = 0;
    }
    g->st.fontSize = size > 0 ? size : 12;
    g->st.fontStyle = style;
}

void jnative_gfx_clear(int32_t gfxId, int32_t rgba) {
    JnGfx *g = G(gfxId); JnSurface *s = g ? S(g->surfaceId) : NULL;
    if (!g || !g->valid || !s) return;
    uint32_t c = (uint32_t)rgba;
    size_t n = (size_t)s->w * (size_t)s->h;
    for (size_t i = 0; i < n; i++) s->px[i] = c;
}

static void draw_line_raw(JnSurface *s, JnGfx *g, int x0, int y0, int x1, int y1, uint32_t rgba, int thick) {
    int dx = abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
    int dy = -abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
    int err = dx + dy;
    for (;;) {
        for (int oy = -thick / 2; oy <= thick / 2; oy++)
            for (int ox = -thick / 2; ox <= thick / 2; ox++)
                put_px(s, g, x0 + ox, y0 + oy, rgba);
        if (x0 == x1 && y0 == y1) break;
        int e2 = 2 * err;
        if (e2 >= dy) { err += dy; x0 += sx; }
        if (e2 <= dx) { err += dx; y0 += sy; }
    }
}

void jnative_gfx_draw_line(int32_t gfxId, double x1, double y1, double x2, double y2) {
    JnGfx *g = G(gfxId); JnSurface *s = g ? S(g->surfaceId) : NULL;
    if (!g || !g->valid || !s) return;
    double ax, ay, bx, by;
    xform(g, x1, y1, &ax, &ay);
    xform(g, x2, y2, &bx, &by);
    int t = (int)(g->st.stroke + 0.5); if (t < 1) t = 1;
    draw_line_raw(s, g, (int)ax, (int)ay, (int)bx, (int)by, g->st.rgba, t);
}

void jnative_gfx_draw_rect(int32_t gfxId, double x, double y, double w, double h) {
    jnative_gfx_draw_line(gfxId, x, y, x + w, y);
    jnative_gfx_draw_line(gfxId, x + w, y, x + w, y + h);
    jnative_gfx_draw_line(gfxId, x + w, y + h, x, y + h);
    jnative_gfx_draw_line(gfxId, x, y + h, x, y);
}

void jnative_gfx_fill_rect(int32_t gfxId, double x, double y, double w, double h) {
    JnGfx *g = G(gfxId); JnSurface *s = g ? S(g->surfaceId) : NULL;
    if (!g || !g->valid || !s) return;
    double x0, y0, x1, y1;
    xform(g, x, y, &x0, &y0);
    xform(g, x + w, y + h, &x1, &y1);
    int ix0 = (int)floor(x0 < x1 ? x0 : x1);
    int iy0 = (int)floor(y0 < y1 ? y0 : y1);
    int ix1 = (int)ceil(x0 > x1 ? x0 : x1);
    int iy1 = (int)ceil(y0 > y1 ? y0 : y1);
    fill_rect_i(s, g, ix0, iy0, ix1 - ix0, iy1 - iy0, g->st.rgba);
}

void jnative_gfx_draw_round_rect(int32_t gfxId, double x, double y, double w, double h, double r) {
    (void)r; jnative_gfx_draw_rect(gfxId, x, y, w, h);
}
void jnative_gfx_fill_round_rect(int32_t gfxId, double x, double y, double w, double h, double r) {
    (void)r; jnative_gfx_fill_rect(gfxId, x, y, w, h);
}

static void fill_ellipse_i(JnSurface *s, JnGfx *g, int cx, int cy, int rx, int ry, uint32_t rgba, int fill) {
    if (rx <= 0 || ry <= 0) return;
    for (int y = -ry; y <= ry; y++) {
        for (int x = -rx; x <= rx; x++) {
            double nx = (double)x / rx, ny = (double)y / ry;
            if (nx * nx + ny * ny <= 1.0) {
                if (fill) put_px(s, g, cx + x, cy + y, rgba);
                else {
                    double d = nx * nx + ny * ny;
                    if (d > 0.85) put_px(s, g, cx + x, cy + y, rgba);
                }
            }
        }
    }
}

void jnative_gfx_draw_ellipse(int32_t gfxId, double x, double y, double w, double h) {
    JnGfx *g = G(gfxId); JnSurface *s = g ? S(g->surfaceId) : NULL;
    if (!g || !g->valid || !s) return;
    double cx, cy; xform(g, x + w * 0.5, y + h * 0.5, &cx, &cy);
    fill_ellipse_i(s, g, (int)cx, (int)cy, (int)(w * 0.5 * g->st.sx), (int)(h * 0.5 * g->st.sy), g->st.rgba, 0);
}
void jnative_gfx_fill_ellipse(int32_t gfxId, double x, double y, double w, double h) {
    JnGfx *g = G(gfxId); JnSurface *s = g ? S(g->surfaceId) : NULL;
    if (!g || !g->valid || !s) return;
    double cx, cy; xform(g, x + w * 0.5, y + h * 0.5, &cx, &cy);
    fill_ellipse_i(s, g, (int)cx, (int)cy, (int)(w * 0.5 * g->st.sx), (int)(h * 0.5 * g->st.sy), g->st.rgba, 1);
}

static int utf8_next(const char **p) {
    const unsigned char *s = (const unsigned char *)*p;
    if (*s == 0) return 0;
    if (*s < 0x80) { (*p)++; return *s; }
    if ((*s & 0xE0) == 0xC0) { int c = ((*s & 0x1F) << 6) | (s[1] & 0x3F); *p += 2; return c; }
    if ((*s & 0xF0) == 0xE0) { int c = ((*s & 0x0F) << 12) | ((s[1] & 0x3F) << 6) | (s[2] & 0x3F); *p += 3; return c; }
    if ((*s & 0xF8) == 0xF0) { *p += 4; return 0xFFFD; }
    (*p)++; return '?';
}

void jnative_gfx_draw_text(int32_t gfxId, const char *utf8, double x, double y) {
    JnGfx *g = G(gfxId); JnSurface *s = g ? S(g->surfaceId) : NULL;
    if (!g || !g->valid || !s || !utf8) return;
    font_init();
    double ox, oy; xform(g, x, y, &ox, &oy);
    int scale = g->st.fontSize / 8; if (scale < 1) scale = 1;
    int penx = (int)ox;
    int peny = (int)oy; /* top of cell (documented) */
    const char *p = utf8;
    int ch;
    while ((ch = utf8_next(&p)) != 0) {
        if (ch >= 32 && ch <= 126) {
            uint8_t *glyph = FONT8[ch - 32];
            for (int row = 0; row < 8; row++) {
                for (int col = 0; col < 8; col++) {
                    if (glyph[row] & (0x80 >> col)) {
                        fill_rect_i(s, g, penx + col * scale, peny + row * scale, scale, scale, g->st.rgba);
                    }
                }
            }
            penx += 8 * scale;
        } else {
            /* non-ASCII: filled block approximating full-width */
            fill_rect_i(s, g, penx, peny, 8 * scale, 8 * scale, g->st.rgba);
            penx += 8 * scale;
        }
    }
}

void jnative_gfx_measure_text(int32_t gfxId, const char *utf8, double *outW, double *outH) {
    JnGfx *g = G(gfxId);
    int size = g && g->valid ? g->st.fontSize : 16;
    int scale = size / 8; if (scale < 1) scale = 1;
    int n = 0;
    if (utf8) {
        const char *p = utf8; int ch;
        while ((ch = utf8_next(&p)) != 0) n++;
    }
    if (outW) *outW = (double)(n * 8 * scale);
    if (outH) *outH = (double)(8 * scale);
}
int32_t jnative_gfx_measure_text_w(int32_t gfxId, const char *utf8) {
    double w = 0, h = 0; jnative_gfx_measure_text(gfxId, utf8, &w, &h); return (int32_t)w;
}
int32_t jnative_gfx_measure_text_h(int32_t gfxId, const char *utf8) {
    double w = 0, h = 0; jnative_gfx_measure_text(gfxId, utf8, &w, &h); return (int32_t)h;
}

int32_t jnative_gfx_font_ascent(int32_t gfxId) {
    JnGfx *g = G(gfxId); int size = g && g->valid ? g->st.fontSize : 16; return size * 3 / 4;
}
int32_t jnative_gfx_font_descent(int32_t gfxId) {
    JnGfx *g = G(gfxId); int size = g && g->valid ? g->st.fontSize : 16; return size / 4;
}
int32_t jnative_gfx_font_line_height(int32_t gfxId) {
    JnGfx *g = G(gfxId); return g && g->valid ? g->st.fontSize : 16;
}

void jnative_gfx_draw_image(int32_t gfxId, int32_t imageId, double x, double y) {
    JnSurface *img = S(imageId);
    if (!img) return;
    jnative_gfx_draw_image_sized(gfxId, imageId, x, y, img->w, img->h);
}

void jnative_gfx_draw_image_sized(int32_t gfxId, int32_t imageId, double x, double y, double w, double h) {
    JnGfx *g = G(gfxId); JnSurface *s = g ? S(g->surfaceId) : NULL; JnSurface *img = S(imageId);
    if (!g || !g->valid || !s || !img || w <= 0 || h <= 0) return;
    double ox, oy; xform(g, x, y, &ox, &oy);
    int dw = (int)w, dh = (int)h;
    for (int yy = 0; yy < dh; yy++) {
        int sy = yy * img->h / dh;
        for (int xx = 0; xx < dw; xx++) {
            int sx = xx * img->w / dw;
            put_px(s, g, (int)ox + xx, (int)oy + yy, img->px[sy * img->w + sx]);
        }
    }
}

void jnative_gfx_set_clip(int32_t gfxId, double x, double y, double w, double h) {
    JnGfx *g = G(gfxId); if (!g || !g->valid) return;
    double x0, y0, x1, y1; xform(g, x, y, &x0, &y0); xform(g, x + w, y + h, &x1, &y1);
    g->st.clip = 1;
    g->st.cx = (int)floor(x0 < x1 ? x0 : x1);
    g->st.cy = (int)floor(y0 < y1 ? y0 : y1);
    g->st.cw = (int)ceil(fabs(x1 - x0));
    g->st.ch = (int)ceil(fabs(y1 - y0));
}
void jnative_gfx_clear_clip(int32_t gfxId) {
    JnGfx *g = G(gfxId); if (!g || !g->valid) return; g->st.clip = 0;
}
void jnative_gfx_save(int32_t gfxId) {
    JnGfx *g = G(gfxId); if (!g || !g->valid || g->sp >= JN_GFX_STACK) return;
    g->stack[g->sp++] = g->st;
}
void jnative_gfx_restore(int32_t gfxId) {
    JnGfx *g = G(gfxId); if (!g || !g->valid || g->sp <= 0) return;
    g->st = g->stack[--g->sp];
}
void jnative_gfx_translate(int32_t gfxId, double dx, double dy) {
    JnGfx *g = G(gfxId); if (!g || !g->valid) return; g->st.tx += dx; g->st.ty += dy;
}
void jnative_gfx_scale(int32_t gfxId, double sx, double sy) {
    JnGfx *g = G(gfxId); if (!g || !g->valid) return; g->st.sx *= sx; g->st.sy *= sy;
}
void jnative_gfx_rotate(int32_t gfxId, double radians) {
    JnGfx *g = G(gfxId); if (!g || !g->valid) return; g->st.rot += radians;
}

int32_t jnative_gfx_path_create(void) {
    int32_t id = alloc_slot_path(); if (id < 0) return -1;
    memset(&g_path[id], 0, sizeof(g_path[id])); g_path[id].live = 1; g_live_path++; return id;
}
void jnative_gfx_path_destroy(int32_t pathId) {
    JnPath *p = P(pathId); if (!p) return; memset(p, 0, sizeof(*p)); g_live_path--;
}
void jnative_gfx_path_clear(int32_t pathId) { JnPath *p = P(pathId); if (p) p->n = 0; }
static void path_add(JnPath *p, int op, double x, double y, double a, double b, double c, double d) {
    if (!p || p->n >= JN_GFX_PATH_MAX) return;
    p->cmds[p->n++] = (JnPathCmd){op, x, y, a, b, c, d};
}
void jnative_gfx_path_move_to(int32_t pathId, double x, double y) { path_add(P(pathId), 0, x, y, 0, 0, 0, 0); }
void jnative_gfx_path_line_to(int32_t pathId, double x, double y) { path_add(P(pathId), 1, x, y, 0, 0, 0, 0); }
void jnative_gfx_path_quad_to(int32_t pathId, double cx, double cy, double x, double y) {
    path_add(P(pathId), 3, x, y, cx, cy, 0, 0);
}
void jnative_gfx_path_cubic_to(int32_t pathId, double c1x, double c1y, double c2x, double c2y, double x, double y) {
    path_add(P(pathId), 4, x, y, c1x, c1y, c2x, c2y);
}
void jnative_gfx_path_close(int32_t pathId) { path_add(P(pathId), 2, 0, 0, 0, 0, 0, 0); }

void jnative_gfx_draw_path(int32_t gfxId, int32_t pathId) {
    JnPath *p = P(pathId); if (!p) return;
    double sx = 0, sy = 0, cx = 0, cy = 0; int has = 0;
    for (int i = 0; i < p->n; i++) {
        JnPathCmd *c = &p->cmds[i];
        if (c->op == 0) { sx = cx = c->x; sy = cy = c->y; has = 1; }
        else if (c->op == 1 && has) { jnative_gfx_draw_line(gfxId, cx, cy, c->x, c->y); cx = c->x; cy = c->y; }
        else if (c->op == 2 && has) { jnative_gfx_draw_line(gfxId, cx, cy, sx, sy); cx = sx; cy = sy; }
        else if (c->op == 3 && has) {
            /* approximate quad with segments */
            for (int t = 1; t <= 8; t++) {
                double u = t / 8.0, u1 = 1 - u;
                double x = u1*u1*cx + 2*u1*u*c->a + u*u*c->x;
                double y = u1*u1*cy + 2*u1*u*c->b + u*u*c->y;
                jnative_gfx_draw_line(gfxId, cx, cy, x, y); cx = x; cy = y;
            }
        } else if (c->op == 4 && has) {
            for (int t = 1; t <= 8; t++) {
                double u = t / 8.0, u1 = 1 - u;
                double x = u1*u1*u1*cx + 3*u1*u1*u*c->a + 3*u1*u*u*c->c + u*u*u*c->x;
                double y = u1*u1*u1*cy + 3*u1*u1*u*c->b + 3*u1*u*u*c->d + u*u*u*c->y;
                jnative_gfx_draw_line(gfxId, cx, cy, x, y); cx = x; cy = y;
            }
        }
    }
}
void jnative_gfx_fill_path(int32_t gfxId, int32_t pathId) {
    /* scan-convert bbox fill of polyline approx */
    JnPath *p = P(pathId); JnGfx *g = G(gfxId); JnSurface *s = g ? S(g->surfaceId) : NULL;
    if (!p || !g || !g->valid || !s || p->n == 0) return;
    double minx = 1e9, miny = 1e9, maxx = -1e9, maxy = -1e9;
    for (int i = 0; i < p->n; i++) {
        if (p->cmds[i].op == 0 || p->cmds[i].op == 1) {
            if (p->cmds[i].x < minx) minx = p->cmds[i].x;
            if (p->cmds[i].y < miny) miny = p->cmds[i].y;
            if (p->cmds[i].x > maxx) maxx = p->cmds[i].x;
            if (p->cmds[i].y > maxy) maxy = p->cmds[i].y;
        }
    }
    jnative_gfx_fill_rect(gfxId, minx, miny, maxx - minx, maxy - miny);
}

/* ---- PNG ---- */
static uint32_t crc_table[256]; static int crc_ready;
static void crc_init(void) {
    if (crc_ready) return; crc_ready = 1;
    for (uint32_t n = 0; n < 256; n++) {
        uint32_t c = n;
        for (int k = 0; k < 8; k++) c = (c & 1) ? (0xEDB88320u ^ (c >> 1)) : (c >> 1);
        crc_table[n] = c;
    }
}
static uint32_t crc32_jn(const uint8_t *buf, size_t len) {
    crc_init(); uint32_t c = 0xFFFFFFFFu;
    for (size_t n = 0; n < len; n++) c = crc_table[(c ^ buf[n]) & 0xFF] ^ (c >> 8);
    return c ^ 0xFFFFFFFFu;
}
static void put_be32(uint8_t *p, uint32_t v) {
    p[0] = (uint8_t)(v >> 24); p[1] = (uint8_t)(v >> 16); p[2] = (uint8_t)(v >> 8); p[3] = (uint8_t)v;
}
static uint32_t get_be32(const uint8_t *p) {
    return ((uint32_t)p[0] << 24) | ((uint32_t)p[1] << 16) | ((uint32_t)p[2] << 8) | p[3];
}

int32_t jnative_gfx_image_save_png(int32_t surfaceId, const char *path) {
    JnSurface *s = S(surfaceId);
    if (!s || !path) return 0;
    size_t raw_len = (size_t)(s->w * 4 + 1) * (size_t)s->h;
    uint8_t *raw = (uint8_t *)malloc(raw_len);
    if (!raw) return 0;
    size_t o = 0;
    for (int y = 0; y < s->h; y++) {
        raw[o++] = 0;
        for (int x = 0; x < s->w; x++) {
            uint32_t px = s->px[y * s->w + x];
            raw[o++] = (uint8_t)(px & 255);
            raw[o++] = (uint8_t)((px >> 8) & 255);
            raw[o++] = (uint8_t)((px >> 16) & 255);
            raw[o++] = (uint8_t)((px >> 24) & 255);
        }
    }
    uLongf comp_len = compressBound(raw_len);
    uint8_t *comp = (uint8_t *)malloc(comp_len);
    if (!comp) { free(raw); return 0; }
    if (compress2(comp, &comp_len, raw, raw_len, Z_BEST_SPEED) != Z_OK) {
        free(raw); free(comp); return 0;
    }
    free(raw);
    FILE *f = fopen(path, "wb");
    if (!f) { free(comp); return 0; }
    static const uint8_t sig[8] = {137,80,78,71,13,10,26,10};
    fwrite(sig, 1, 8, f);
    uint8_t ihdr[13 + 8 + 4];
    put_be32(ihdr, 13);
    memcpy(ihdr + 4, "IHDR", 4);
    put_be32(ihdr + 8, (uint32_t)s->w);
    put_be32(ihdr + 12, (uint32_t)s->h);
    ihdr[16] = 8; ihdr[17] = 6; ihdr[18] = 0; ihdr[19] = 0; ihdr[20] = 0;
    uint32_t c = crc32_jn(ihdr + 4, 17);
    put_be32(ihdr + 21, c);
    fwrite(ihdr, 1, 25, f);
    uint8_t lenb[4]; put_be32(lenb, (uint32_t)comp_len);
    fwrite(lenb, 1, 4, f);
    fwrite("IDAT", 1, 4, f);
    fwrite(comp, 1, comp_len, f);
    uint8_t *crcbuf = (uint8_t *)malloc(comp_len + 4);
    memcpy(crcbuf, "IDAT", 4); memcpy(crcbuf + 4, comp, comp_len);
    put_be32(lenb, crc32_jn(crcbuf, comp_len + 4));
    fwrite(lenb, 1, 4, f);
    free(crcbuf); free(comp);
    uint8_t iend[12]; put_be32(iend, 0); memcpy(iend + 4, "IEND", 4); put_be32(iend + 8, crc32_jn((uint8_t *)"IEND", 4));
    fwrite(iend, 1, 12, f);
    fclose(f);
    return 1;
}

static int32_t decode_png_rgba(const uint8_t *data, int32_t len) {
    if (len < 33) return -1;
    static const uint8_t sig[8] = {137,80,78,71,13,10,26,10};
    if (memcmp(data, sig, 8) != 0) return -1;
    int32_t w = 0, h = 0;
    uint8_t *idat = NULL; size_t idat_len = 0;
    size_t off = 8;
    while (off + 12 <= (size_t)len) {
        uint32_t clen = get_be32(data + off);
        const char *type = (const char *)(data + off + 4);
        const uint8_t *chunk = data + off + 8;
        if (off + 12 + clen > (size_t)len) break;
        if (memcmp(type, "IHDR", 4) == 0 && clen >= 13) {
            w = (int32_t)get_be32(chunk);
            h = (int32_t)get_be32(chunk + 4);
            if (chunk[8] != 8 || chunk[9] != 6) { free(idat); return -1; }
        } else if (memcmp(type, "IDAT", 4) == 0) {
            uint8_t *nbuf = (uint8_t *)realloc(idat, idat_len + clen);
            if (!nbuf) { free(idat); return -1; }
            idat = nbuf;
            memcpy(idat + idat_len, chunk, clen);
            idat_len += clen;
        } else if (memcmp(type, "IEND", 4) == 0) {
            break;
        }
        off += 12 + clen;
    }
    if (w <= 0 || h <= 0 || !idat) { free(idat); return -1; }
    size_t raw_len = (size_t)(w * 4 + 1) * (size_t)h;
    uint8_t *raw = (uint8_t *)malloc(raw_len);
    if (!raw) { free(idat); return -1; }
    uLongf dest = (uLongf)raw_len;
    if (uncompress(raw, &dest, idat, idat_len) != Z_OK) { free(idat); free(raw); return -1; }
    free(idat);
    int32_t sid = jnative_gfx_surface_create(w, h);
    JnSurface *s = S(sid);
    if (!s) { free(raw); return -1; }
    size_t i = 0;
    for (int y = 0; y < h; y++) {
        i++; /* filter */
        for (int x = 0; x < w; x++) {
            uint8_t r = raw[i++], g = raw[i++], b = raw[i++], a = raw[i++];
            s->px[y * w + x] = pack_rgba(r, g, b, a);
        }
    }
    free(raw);
    return sid;
}

static int32_t decode_bmp_rgba(const uint8_t *data, int32_t len) {
    if (len < 54 || data[0] != 'B' || data[1] != 'M') return -1;
    int32_t pixel_off = (int32_t)(data[10] | (data[11] << 8) | (data[12] << 16) | (data[13] << 24));
    int32_t w = (int32_t)(data[18] | (data[19] << 8) | (data[20] << 16) | (data[21] << 24));
    int32_t h = (int32_t)(data[22] | (data[23] << 8) | (data[24] << 16) | (data[25] << 24));
    int32_t bpp = (int32_t)(data[28] | (data[29] << 8));
    if (w <= 0 || h == 0 || (bpp != 24 && bpp != 32)) return -1;
    int abs_h = h < 0 ? -h : h;
    int32_t sid = jnative_gfx_surface_create(w, abs_h);
    JnSurface *s = S(sid);
    if (!s) return -1;
    int row_bytes = ((w * (bpp / 8) + 3) / 4) * 4;
    for (int y = 0; y < abs_h; y++) {
        int src_y = h > 0 ? (abs_h - 1 - y) : y;
        const uint8_t *row = data + pixel_off + src_y * row_bytes;
        for (int x = 0; x < w; x++) {
            uint8_t b = row[x * (bpp / 8) + 0];
            uint8_t g = row[x * (bpp / 8) + 1];
            uint8_t r = row[x * (bpp / 8) + 2];
            uint8_t a = bpp == 32 ? row[x * 4 + 3] : 255;
            s->px[y * w + x] = pack_rgba(r, g, b, a);
        }
    }
    return sid;
}

/* Minimal JPEG: accept SOI and create placeholder until WIC/ImageIO decode lands. */
static int32_t decode_jpeg_placeholder(const uint8_t *data, int32_t len) {
    if (len < 4 || data[0] != 0xFF || data[1] != 0xD8) return -1;
    int32_t sid = jnative_gfx_surface_create(8, 8);
    JnSurface *s = S(sid);
    if (!s) return -1;
    for (int i = 0; i < 64; i++) s->px[i] = pack_rgba(128, 128, 128, 255);
    return sid;
}

int32_t jnative_gfx_image_load_bytes(const uint8_t *data, int32_t len) {
    if (data == NULL || len <= 0) return -1;
    if (len >= 8 && data[0] == 137 && data[1] == 80) return decode_png_rgba(data, len);
    if (len >= 2 && data[0] == 'B' && data[1] == 'M') return decode_bmp_rgba(data, len);
    if (len >= 2 && data[0] == 0xFF && data[1] == 0xD8) return decode_jpeg_placeholder(data, len);
    return -1;
}

int32_t jnative_gfx_image_load_file(const char *path) {
    FILE *f = fopen(path, "rb");
    if (!f) return -1;
    fseek(f, 0, SEEK_END); long sz = ftell(f); fseek(f, 0, SEEK_SET);
    if (sz <= 0 || sz > 64 * 1024 * 1024) { fclose(f); return -1; }
    uint8_t *buf = (uint8_t *)malloc((size_t)sz);
    if (!buf) { fclose(f); return -1; }
    if (fread(buf, 1, (size_t)sz, f) != (size_t)sz) { free(buf); fclose(f); return -1; }
    fclose(f);
    int32_t id = jnative_gfx_image_load_bytes(buf, (int32_t)sz);
    free(buf);
    return id;
}

int32_t jnative_gfx_get_pixel(int32_t surfaceId, int32_t x, int32_t y) {
    JnSurface *s = S(surfaceId);
    if (!s || x < 0 || y < 0 || x >= s->w || y >= s->h) return 0;
    return (int32_t)s->px[y * s->w + x];
}
void jnative_gfx_set_pixel(int32_t surfaceId, int32_t x, int32_t y, int32_t rgba) {
    JnSurface *s = S(surfaceId);
    if (!s || x < 0 || y < 0 || x >= s->w || y >= s->h) return;
    s->px[y * s->w + x] = (uint32_t)rgba;
}

int32_t jnative_gfx_live_surfaces(void) { return g_live_surf; }
int32_t jnative_gfx_live_fonts(void) { return g_live_fonts; }
int32_t jnative_gfx_live_paths(void) { return g_live_path; }
int32_t jnative_gfx_live_contexts(void) { return g_live_gfx; }

const uint8_t *jnative_gfx_surface_pixels(int32_t surfaceId, int32_t *outW, int32_t *outH) {
    JnSurface *s = S(surfaceId);
    if (!s) {
        if (outW) *outW = 0;
        if (outH) *outH = 0;
        return NULL;
    }
    if (outW) *outW = s->w;
    if (outH) *outH = s->h;
    return (const uint8_t *)s->px;
}
