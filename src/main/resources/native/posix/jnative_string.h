/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Author: 张晓达 */
#pragma once

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/*
 * JNative String — UTF-16 code units (Java-compatible indexing).
 *
 * Layout:
 *   +0  void*   meta
 *   +8  int32_t length   (UTF-16 code unit count)
 *   +16 uint16_t data[length]
 *
 * Wire / FFI use UTF-8 via utf8Length / getBytes / from_utf8.
 * Embedded NUL (U+0000) is preserved in String and UTF-8 byte[].
 * CString conversion rejects embedded NUL (cannot represent).
 */

#define JN_STRING_DATA_OFF 16

void *jnative_gc_alloc(uint64_t payload);
void jnative_null_check(void *p);
void jnative_throw_aioobe(void);
void jnative_throw_npe(void);
void jnative_throw_illegal_argument(void);
void *jnative_array_alloc(int64_t elemSize, int32_t length, void *meta);

/** Decode strict UTF-8 into a new String. Invalid sequences → IllegalArgumentException. */
void *jnative_string_from_utf8(void *meta, const uint8_t *utf8, int32_t byteLen);

/** Build String from UTF-16 code units (copies). */
void *jnative_string_from_utf16(void *meta, const uint16_t *units, int32_t unitLen);

int32_t jnative_string_length(void *s);
/** UTF-8 byte length of the string content (no trailing NUL). */
int32_t jnative_string_utf8_length(void *s);

uint16_t jnative_string_char_at(void *s, int32_t index);

void *jnative_string_concat(void *a, void *b);
int32_t jnative_string_equals(void *a, void *b);
int32_t jnative_string_hashcode(void *s);

int32_t jnative_string_index_of(void *s, void *needle);
int32_t jnative_string_index_of_from(void *s, void *needle, int32_t from);
int32_t jnative_string_last_index_of(void *s, void *needle);
int32_t jnative_string_last_index_of_from(void *s, void *needle, int32_t from);

void *jnative_string_substring(void *s, int32_t begin, int32_t end);
void *jnative_string_substring_from(void *s, int32_t begin);

int32_t jnative_string_starts_with(void *s, void *prefix);
int32_t jnative_string_ends_with(void *s, void *suffix);
int32_t jnative_string_equals_ignore_case(void *a, void *b);
int32_t jnative_string_compare_to(void *a, void *b);
int32_t jnative_string_is_empty(void *s);
int32_t jnative_string_contains(void *s, void *needle);

void *jnative_string_trim(void *s);
void *jnative_string_to_lower(void *s);
void *jnative_string_to_upper(void *s);
void *jnative_string_replace(void *s, void *target, void *repl);

/** Encode to new byte[] (meta must be meta.array.i8). Lone surrogates → U+FFFD. */
void *jnative_string_get_bytes_with_meta(void *s, void *arrayMeta);

/** Strict UTF-8 decode from byte[] (throws on invalid). */
void *jnative_string_from_bytes_with_meta(void *arr, void *stringMeta);

/**
 * Encode to malloc'd UTF-8 C string (+NUL). Rejects embedded U+0000 (returns NULL
 * after throwing IllegalArgumentException). Caller frees with free()/ffi_cstr_free.
 */
char *jnative_string_to_cstr(void *s);

/** Encode into caller buffer; returns bytes written (no NUL). buf may be NULL to measure. */
int32_t jnative_string_encode_utf8(void *s, uint8_t *buf, int32_t bufCap);

/** Java Double/Float.toString into out (>= 48 bytes); returns length. */
int32_t jnative_fp_to_chars(double v, int32_t isFloat, char *out);

/* System shim: exit with flush; write UTF-8 C string to stdout/stderr (+ optional newline). */
int32_t jnative_is_windows(void);
void jnative_exit(int32_t status);
void jnative_stdout_write(const char *s, int32_t newline);
void jnative_stderr_write(const char *s, int32_t newline);

#ifdef __cplusplus
}
#endif
