/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Author: 张晓达 */
#ifndef JNATIVE_GRAPHICS_H
#define JNATIVE_GRAPHICS_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Opaque surface / image / graphics context handles (int ids). */
int32_t jnative_gfx_surface_create(int32_t width, int32_t height);
void jnative_gfx_surface_destroy(int32_t id);
int32_t jnative_gfx_surface_width(int32_t id);
int32_t jnative_gfx_surface_height(int32_t id);

int32_t jnative_gfx_begin(int32_t surfaceId);
void jnative_gfx_end(int32_t gfxId);
int32_t jnative_gfx_is_valid(int32_t gfxId);

void jnative_gfx_set_color(int32_t gfxId, int32_t rgba);
void jnative_gfx_set_color_components(int32_t gfxId, int32_t r, int32_t g, int32_t b, int32_t a);
int32_t jnative_gfx_pack_rgba(int32_t r, int32_t g, int32_t b, int32_t a);
int32_t jnative_gfx_unpack_r(int32_t rgba);
int32_t jnative_gfx_unpack_g(int32_t rgba);
int32_t jnative_gfx_unpack_b(int32_t rgba);
int32_t jnative_gfx_unpack_a(int32_t rgba);
void jnative_gfx_set_stroke_width(int32_t gfxId, double width);
void jnative_gfx_set_font(int32_t gfxId, const char *family, int32_t size, int32_t style);
void jnative_gfx_set_antialias(int32_t gfxId, int32_t on);

void jnative_gfx_clear(int32_t gfxId, int32_t rgba);
void jnative_gfx_draw_line(int32_t gfxId, double x1, double y1, double x2, double y2);
void jnative_gfx_draw_rect(int32_t gfxId, double x, double y, double w, double h);
void jnative_gfx_fill_rect(int32_t gfxId, double x, double y, double w, double h);
void jnative_gfx_draw_round_rect(int32_t gfxId, double x, double y, double w, double h, double r);
void jnative_gfx_fill_round_rect(int32_t gfxId, double x, double y, double w, double h, double r);
void jnative_gfx_draw_ellipse(int32_t gfxId, double x, double y, double w, double h);
void jnative_gfx_fill_ellipse(int32_t gfxId, double x, double y, double w, double h);
void jnative_gfx_draw_text(int32_t gfxId, const char *utf8, double x, double y);
/* measure: writes width/height into outW/outH (logical pixels). y baseline = top of glyph cell. */
void jnative_gfx_measure_text(int32_t gfxId, const char *utf8, double *outW, double *outH);
int32_t jnative_gfx_measure_text_w(int32_t gfxId, const char *utf8);
int32_t jnative_gfx_measure_text_h(int32_t gfxId, const char *utf8);
int32_t jnative_gfx_font_ascent(int32_t gfxId);
int32_t jnative_gfx_font_descent(int32_t gfxId);
int32_t jnative_gfx_font_line_height(int32_t gfxId);

void jnative_gfx_draw_image(int32_t gfxId, int32_t imageId, double x, double y);
void jnative_gfx_draw_image_sized(int32_t gfxId, int32_t imageId,
                                  double x, double y, double w, double h);

void jnative_gfx_set_clip(int32_t gfxId, double x, double y, double w, double h);
void jnative_gfx_clear_clip(int32_t gfxId);
void jnative_gfx_save(int32_t gfxId);
void jnative_gfx_restore(int32_t gfxId);
void jnative_gfx_translate(int32_t gfxId, double dx, double dy);
void jnative_gfx_scale(int32_t gfxId, double sx, double sy);
void jnative_gfx_rotate(int32_t gfxId, double radians);

/* Path: managed builds command list; native materializes for stroke/fill. */
int32_t jnative_gfx_path_create(void);
void jnative_gfx_path_destroy(int32_t pathId);
void jnative_gfx_path_clear(int32_t pathId);
void jnative_gfx_path_move_to(int32_t pathId, double x, double y);
void jnative_gfx_path_line_to(int32_t pathId, double x, double y);
void jnative_gfx_path_quad_to(int32_t pathId, double cx, double cy, double x, double y);
void jnative_gfx_path_cubic_to(int32_t pathId, double c1x, double c1y, double c2x, double c2y,
                               double x, double y);
void jnative_gfx_path_close(int32_t pathId);
void jnative_gfx_draw_path(int32_t gfxId, int32_t pathId);
void jnative_gfx_fill_path(int32_t gfxId, int32_t pathId);

/* Image decode/encode (PNG). Returns surface id or -1. */
int32_t jnative_gfx_image_load_file(const char *path);
int32_t jnative_gfx_image_load_bytes(const uint8_t *data, int32_t len);
int32_t jnative_gfx_image_save_png(int32_t surfaceId, const char *path);
/* Pixel probe for tests: returns RGBA packed. */
int32_t jnative_gfx_get_pixel(int32_t surfaceId, int32_t x, int32_t y);
void jnative_gfx_set_pixel(int32_t surfaceId, int32_t x, int32_t y, int32_t rgba);
/* Scaled (nearest) blit of the source rectangle (sx,sy,sw,sh) into (x,y,w,h); only visible pixels are touched. */
void jnative_gfx_draw_image_region(int32_t gfxId, int32_t imageId, int32_t sx, int32_t sy, int32_t sw, int32_t sh,
                                   double x, double y, double w, double h);
/* Flood fill; tol is the per-channel colour tolerance. Returns pixels changed. */
int32_t jnative_gfx_flood_fill(int32_t surfaceId, int32_t x, int32_t y, int32_t rgba, int32_t tol);

int32_t jnative_gfx_live_surfaces(void);
int32_t jnative_gfx_live_fonts(void);
int32_t jnative_gfx_live_paths(void);
int32_t jnative_gfx_live_contexts(void);

/* Raw RGBA8888 pixels for native present (pointer valid while surface alive). */
const uint8_t *jnative_gfx_surface_pixels(int32_t surfaceId, int32_t *outW, int32_t *outH);

#ifdef __cplusplus
}
#endif

#endif
