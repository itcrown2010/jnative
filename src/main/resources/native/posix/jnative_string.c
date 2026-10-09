/* Author: 张晓达 */
#include "jnative_string.h"

#include <stdlib.h>
#include <string.h>

static uint16_t *jn_str_data(void *s) {
    return (uint16_t *)((uint8_t *)s + JN_STRING_DATA_OFF);
}

static int32_t jn_str_len(void *s) {
    return *(int32_t *)((uint8_t *)s + 8);
}

static void jn_str_set_len(void *s, int32_t len) {
    *(int32_t *)((uint8_t *)s + 8) = len;
}

static int jn_utf8_decode_len(const uint8_t *p, int32_t n, int32_t *outU16) {
    int32_t i = 0;
    int32_t u16 = 0;
    while (i < n) {
        uint8_t b = p[i];
        uint32_t cp;
        int32_t need;
        if (b <= 0x7F) {
            cp = b;
            need = 1;
        } else if ((b & 0xE0) == 0xC0) {
            need = 2;
            if (i + 1 >= n) return -1;
            uint8_t b1 = p[i + 1];
            if ((b1 & 0xC0) != 0x80) return -1;
            cp = ((uint32_t)(b & 0x1F) << 6) | (b1 & 0x3F);
            if (cp < 0x80) return -1; /* overlong */
        } else if ((b & 0xF0) == 0xE0) {
            need = 3;
            if (i + 2 >= n) return -1;
            uint8_t b1 = p[i + 1], b2 = p[i + 2];
            if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80) return -1;
            cp = ((uint32_t)(b & 0x0F) << 12) | ((uint32_t)(b1 & 0x3F) << 6) | (b2 & 0x3F);
            if (cp < 0x800) return -1;
            if (cp >= 0xD800 && cp <= 0xDFFF) return -1; /* UTF-16 surrogates illegal in UTF-8 */
        } else if ((b & 0xF8) == 0xF0) {
            need = 4;
            if (i + 3 >= n) return -1;
            uint8_t b1 = p[i + 1], b2 = p[i + 2], b3 = p[i + 3];
            if ((b1 & 0xC0) != 0x80 || (b2 & 0xC0) != 0x80 || (b3 & 0xC0) != 0x80) return -1;
            cp = ((uint32_t)(b & 0x07) << 18) | ((uint32_t)(b1 & 0x3F) << 12)
                    | ((uint32_t)(b2 & 0x3F) << 6) | (b3 & 0x3F);
            if (cp < 0x10000 || cp > 0x10FFFF) return -1;
        } else {
            return -1;
        }
        if (cp <= 0xFFFF) {
            u16 += 1;
        } else {
            u16 += 2;
        }
        i += need;
    }
    *outU16 = u16;
    return 0;
}

static int jn_utf8_decode(const uint8_t *p, int32_t n, uint16_t *out) {
    int32_t i = 0;
    int32_t o = 0;
    while (i < n) {
        uint8_t b = p[i];
        uint32_t cp;
        int32_t need;
        if (b <= 0x7F) {
            cp = b;
            need = 1;
        } else if ((b & 0xE0) == 0xC0) {
            need = 2;
            cp = ((uint32_t)(b & 0x1F) << 6) | (p[i + 1] & 0x3F);
        } else if ((b & 0xF0) == 0xE0) {
            need = 3;
            cp = ((uint32_t)(b & 0x0F) << 12) | ((uint32_t)(p[i + 1] & 0x3F) << 6) | (p[i + 2] & 0x3F);
        } else {
            need = 4;
            cp = ((uint32_t)(b & 0x07) << 18) | ((uint32_t)(p[i + 1] & 0x3F) << 12)
                    | ((uint32_t)(p[i + 2] & 0x3F) << 6) | (p[i + 3] & 0x3F);
        }
        if (cp <= 0xFFFF) {
            out[o++] = (uint16_t)cp;
        } else {
            uint32_t v = cp - 0x10000;
            out[o++] = (uint16_t)(0xD800 + (v >> 10));
            out[o++] = (uint16_t)(0xDC00 + (v & 0x3FF));
        }
        i += need;
    }
    return o;
}

static int32_t jn_utf8_encode_len_units(const uint16_t *u, int32_t n) {
    int32_t i = 0;
    int32_t bytes = 0;
    while (i < n) {
        uint16_t c = u[i];
        if (c >= 0xD800 && c <= 0xDBFF && i + 1 < n) {
            uint16_t d = u[i + 1];
            if (d >= 0xDC00 && d <= 0xDFFF) {
                bytes += 4;
                i += 2;
                continue;
            }
        }
        if (c >= 0xD800 && c <= 0xDFFF) {
            bytes += 3; /* U+FFFD */
            i += 1;
            continue;
        }
        if (c <= 0x7F) bytes += 1;
        else if (c <= 0x7FF) bytes += 2;
        else bytes += 3;
        i += 1;
    }
    return bytes;
}

static int32_t jn_utf8_encode_units(const uint16_t *u, int32_t n, uint8_t *out) {
    int32_t i = 0;
    int32_t o = 0;
    while (i < n) {
        uint16_t c = u[i];
        uint32_t cp;
        if (c >= 0xD800 && c <= 0xDBFF && i + 1 < n) {
            uint16_t d = u[i + 1];
            if (d >= 0xDC00 && d <= 0xDFFF) {
                cp = 0x10000 + (((uint32_t)(c - 0xD800) << 10) | (uint32_t)(d - 0xDC00));
                i += 2;
                out[o++] = (uint8_t)(0xF0 | (cp >> 18));
                out[o++] = (uint8_t)(0x80 | ((cp >> 12) & 0x3F));
                out[o++] = (uint8_t)(0x80 | ((cp >> 6) & 0x3F));
                out[o++] = (uint8_t)(0x80 | (cp & 0x3F));
                continue;
            }
        }
        if (c >= 0xD800 && c <= 0xDFFF) {
            cp = 0xFFFD; /* lone surrogate → replacement */
        } else {
            cp = c;
        }
        i += 1;
        if (cp <= 0x7F) {
            out[o++] = (uint8_t)cp;
        } else if (cp <= 0x7FF) {
            out[o++] = (uint8_t)(0xC0 | (cp >> 6));
            out[o++] = (uint8_t)(0x80 | (cp & 0x3F));
        } else {
            out[o++] = (uint8_t)(0xE0 | (cp >> 12));
            out[o++] = (uint8_t)(0x80 | ((cp >> 6) & 0x3F));
            out[o++] = (uint8_t)(0x80 | (cp & 0x3F));
        }
    }
    return o;
}

static void *jn_str_alloc(void *meta, int32_t unitLen) {
    if (unitLen < 0) {
        jnative_throw_illegal_argument();
        return NULL;
    }
    uint64_t bytes = (uint64_t)unitLen * 2u;
    if (unitLen > 0 && bytes / 2u != (uint64_t)unitLen) {
        jnative_throw_illegal_argument();
        return NULL;
    }
    uint64_t payload = 16u + bytes;
    void *obj = jnative_gc_alloc(payload);
    if (obj == NULL) return NULL;
    *(void **)obj = meta;
    jn_str_set_len(obj, unitLen);
    return obj;
}

void *jnative_string_from_utf16(void *meta, const uint16_t *units, int32_t unitLen) {
    void *obj = jn_str_alloc(meta, unitLen);
    if (obj == NULL) return NULL;
    if (unitLen > 0 && units != NULL) {
        memcpy(jn_str_data(obj), units, (size_t)unitLen * 2u);
    }
    return obj;
}

void *jnative_string_from_utf8(void *meta, const uint8_t *utf8, int32_t byteLen) {
    if (byteLen < 0) {
        jnative_throw_illegal_argument();
        return NULL;
    }
    if (byteLen == 0) {
        return jn_str_alloc(meta, 0);
    }
    if (utf8 == NULL) {
        jnative_throw_npe();
        return NULL;
    }
    int32_t u16 = 0;
    if (jn_utf8_decode_len(utf8, byteLen, &u16) != 0) {
        jnative_throw_illegal_argument();
        return NULL;
    }
    void *obj = jn_str_alloc(meta, u16);
    if (obj == NULL) return NULL;
    jn_utf8_decode(utf8, byteLen, jn_str_data(obj));
    return obj;
}

int32_t jnative_string_length(void *s) {
    jnative_null_check(s);
    return jn_str_len(s);
}

int32_t jnative_string_utf8_length(void *s) {
    jnative_null_check(s);
    return jn_utf8_encode_len_units(jn_str_data(s), jn_str_len(s));
}

int32_t jnative_string_encode_utf8(void *s, uint8_t *buf, int32_t bufCap) {
    jnative_null_check(s);
    int32_t n = jn_str_len(s);
    const uint16_t *u = jn_str_data(s);
    int32_t need = jn_utf8_encode_len_units(u, n);
    if (buf == NULL) return need;
    if (bufCap < need) return -1;
    return jn_utf8_encode_units(u, n, buf);
}

uint16_t jnative_string_char_at(void *s, int32_t index) {
    jnative_null_check(s);
    int32_t n = jn_str_len(s);
    if (index < 0 || index >= n) {
        jnative_throw_aioobe();
        return 0;
    }
    return jn_str_data(s)[index];
}

void *jnative_string_concat(void *a, void *b) {
    jnative_null_check(a);
    jnative_null_check(b);
    int32_t la = jn_str_len(a);
    int32_t lb = jn_str_len(b);
    int64_t sum64 = (int64_t)la + (int64_t)lb;
    if (sum64 > 0x7FFFFFFF) {
        jnative_throw_illegal_argument();
        return NULL;
    }
    int32_t sum = (int32_t)sum64;
    void *meta = *(void **)a;
    void *obj = jn_str_alloc(meta, sum);
    if (obj == NULL) return NULL;
    uint16_t *d = jn_str_data(obj);
    if (la > 0) memcpy(d, jn_str_data(a), (size_t)la * 2u);
    if (lb > 0) memcpy(d + la, jn_str_data(b), (size_t)lb * 2u);
    return obj;
}

int32_t jnative_string_equals(void *a, void *b) {
    if (a == b) return 1;
    if (a == NULL || b == NULL) return 0;
    int32_t la = jn_str_len(a);
    int32_t lb = jn_str_len(b);
    if (la != lb) return 0;
    if (la == 0) return 1;
    return memcmp(jn_str_data(a), jn_str_data(b), (size_t)la * 2u) == 0 ? 1 : 0;
}

int32_t jnative_string_hashcode(void *s) {
    jnative_null_check(s);
    int32_t n = jn_str_len(s);
    const uint16_t *u = jn_str_data(s);
    int32_t h = 0;
    for (int32_t i = 0; i < n; i++) {
        h = 31 * h + (int32_t)u[i];
    }
    return h;
}

static int jn_region_eq(void *s, int32_t off, void *needle) {
    int32_t nlen = jn_str_len(needle);
    const uint16_t *a = jn_str_data(s) + off;
    const uint16_t *b = jn_str_data(needle);
    return memcmp(a, b, (size_t)nlen * 2u) == 0;
}

int32_t jnative_string_index_of_from(void *s, void *needle, int32_t from) {
    jnative_null_check(s);
    jnative_null_check(needle);
    int32_t slen = jn_str_len(s);
    int32_t nlen = jn_str_len(needle);
    int32_t start = from < 0 ? 0 : from;
    if (nlen == 0) return start <= slen ? start : -1;
    if (start > slen - nlen) return -1;
    for (int32_t i = start; i <= slen - nlen; i++) {
        if (jn_region_eq(s, i, needle)) return i;
    }
    return -1;
}

int32_t jnative_string_index_of(void *s, void *needle) {
    return jnative_string_index_of_from(s, needle, 0);
}

int32_t jnative_string_last_index_of_from(void *s, void *needle, int32_t from) {
    jnative_null_check(s);
    jnative_null_check(needle);
    int32_t slen = jn_str_len(s);
    int32_t nlen = jn_str_len(needle);
    if (nlen == 0) {
        int32_t cap = from <= slen ? from : slen;
        return cap < 0 ? -1 : cap;
    }
    if (from < 0) return -1;
    int32_t limit = slen - nlen;
    if (limit < 0) return -1;
    int32_t start = from > limit ? limit : from;
    for (int32_t i = start; i >= 0; i--) {
        if (jn_region_eq(s, i, needle)) return i;
    }
    return -1;
}

int32_t jnative_string_last_index_of(void *s, void *needle) {
    jnative_null_check(s);
    return jnative_string_last_index_of_from(s, needle, jn_str_len(s) - 1);
}

void *jnative_string_substring(void *s, int32_t begin, int32_t end) {
    jnative_null_check(s);
    int32_t n = jn_str_len(s);
    if (begin < 0 || end > n || begin > end) {
        jnative_throw_aioobe();
        return NULL;
    }
    void *meta = *(void **)s;
    int32_t len = end - begin;
    void *obj = jn_str_alloc(meta, len);
    if (obj == NULL) return NULL;
    if (len > 0) {
        memcpy(jn_str_data(obj), jn_str_data(s) + begin, (size_t)len * 2u);
    }
    return obj;
}

void *jnative_string_substring_from(void *s, int32_t begin) {
    jnative_null_check(s);
    return jnative_string_substring(s, begin, jn_str_len(s));
}

int32_t jnative_string_starts_with(void *s, void *prefix) {
    jnative_null_check(s);
    jnative_null_check(prefix);
    int32_t plen = jn_str_len(prefix);
    if (plen > jn_str_len(s)) return 0;
    return jn_region_eq(s, 0, prefix) ? 1 : 0;
}

int32_t jnative_string_ends_with(void *s, void *suffix) {
    jnative_null_check(s);
    jnative_null_check(suffix);
    int32_t slen = jn_str_len(s);
    int32_t suflen = jn_str_len(suffix);
    if (suflen > slen) return 0;
    return jn_region_eq(s, slen - suflen, suffix) ? 1 : 0;
}

static uint16_t jn_ascii_lower_u16(uint16_t c) {
    if (c >= 'A' && c <= 'Z') return (uint16_t)(c + 32);
    return c;
}

static uint16_t jn_ascii_upper_u16(uint16_t c) {
    if (c >= 'a' && c <= 'z') return (uint16_t)(c - 32);
    return c;
}

int32_t jnative_string_equals_ignore_case(void *a, void *b) {
    jnative_null_check(a);
    jnative_null_check(b);
    int32_t la = jn_str_len(a);
    int32_t lb = jn_str_len(b);
    if (la != lb) return 0;
    const uint16_t *pa = jn_str_data(a);
    const uint16_t *pb = jn_str_data(b);
    for (int32_t i = 0; i < la; i++) {
        if (jn_ascii_lower_u16(pa[i]) != jn_ascii_lower_u16(pb[i])) return 0;
    }
    return 1;
}

int32_t jnative_string_compare_to(void *a, void *b) {
    jnative_null_check(a);
    jnative_null_check(b);
    int32_t la = jn_str_len(a);
    int32_t lb = jn_str_len(b);
    int32_t n = la < lb ? la : lb;
    const uint16_t *pa = jn_str_data(a);
    const uint16_t *pb = jn_str_data(b);
    for (int32_t i = 0; i < n; i++) {
        int32_t d = (int32_t)pa[i] - (int32_t)pb[i];
        if (d != 0) return d;
    }
    return la - lb;
}

int32_t jnative_string_is_empty(void *s) {
    return jnative_string_length(s) == 0 ? 1 : 0;
}

int32_t jnative_string_contains(void *s, void *needle) {
    return jnative_string_index_of(s, needle) >= 0 ? 1 : 0;
}

void *jnative_string_trim(void *s) {
    jnative_null_check(s);
    int32_t n = jn_str_len(s);
    const uint16_t *u = jn_str_data(s);
    int32_t b = 0;
    while (b < n && u[b] <= 0x20) b++;
    int32_t e = n;
    while (e > b && u[e - 1] <= 0x20) e--;
    return jnative_string_substring(s, b, e);
}

void *jnative_string_to_lower(void *s) {
    jnative_null_check(s);
    void *meta = *(void **)s;
    int32_t n = jn_str_len(s);
    void *obj = jn_str_alloc(meta, n);
    if (obj == NULL) return NULL;
    const uint16_t *src = jn_str_data(s);
    uint16_t *dst = jn_str_data(obj);
    for (int32_t i = 0; i < n; i++) dst[i] = jn_ascii_lower_u16(src[i]);
    return obj;
}

void *jnative_string_to_upper(void *s) {
    jnative_null_check(s);
    void *meta = *(void **)s;
    int32_t n = jn_str_len(s);
    void *obj = jn_str_alloc(meta, n);
    if (obj == NULL) return NULL;
    const uint16_t *src = jn_str_data(s);
    uint16_t *dst = jn_str_data(obj);
    for (int32_t i = 0; i < n; i++) dst[i] = jn_ascii_upper_u16(src[i]);
    return obj;
}

void *jnative_string_replace(void *s, void *target, void *repl) {
    jnative_null_check(s);
    jnative_null_check(target);
    jnative_null_check(repl);
    void *meta = *(void **)s;
    int32_t tlen = jn_str_len(target);
    if (tlen == 0) return s;
    int32_t slen = jn_str_len(s);
    int32_t rlen = jn_str_len(repl);
    int32_t occ = 0;
    for (int32_t i = 0; i <= slen - tlen;) {
        if (jn_region_eq(s, i, target)) {
            occ++;
            i += tlen;
        } else {
            i++;
        }
    }
    int64_t out64 = (int64_t)slen + (int64_t)occ * ((int64_t)rlen - (int64_t)tlen);
    if (out64 < 0 || out64 > 0x7FFFFFFF) {
        jnative_throw_illegal_argument();
        return NULL;
    }
    int32_t outLen = (int32_t)out64;
    void *obj = jn_str_alloc(meta, outLen);
    if (obj == NULL) return NULL;
    const uint16_t *src = jn_str_data(s);
    const uint16_t *rd = jn_str_data(repl);
    uint16_t *dst = jn_str_data(obj);
    int32_t si = 0, di = 0;
    while (si < slen) {
        if (si <= slen - tlen && jn_region_eq(s, si, target)) {
            if (rlen > 0) memcpy(dst + di, rd, (size_t)rlen * 2u);
            si += tlen;
            di += rlen;
        } else {
            dst[di++] = src[si++];
        }
    }
    return obj;
}

void *jnative_string_get_bytes_with_meta(void *s, void *arrayMeta) {
    jnative_null_check(s);
    int32_t n = jnative_string_utf8_length(s);
    void *arr = jnative_array_alloc(1, n, arrayMeta);
    if (arr == NULL) return NULL;
    uint8_t *dst = (uint8_t *)arr + 16;
    jn_utf8_encode_units(jn_str_data(s), jn_str_len(s), dst);
    return arr;
}

void *jnative_string_from_bytes_with_meta(void *arr, void *stringMeta) {
    jnative_null_check(arr);
    int32_t len = *(int32_t *)((uint8_t *)arr + 8);
    const uint8_t *src = (const uint8_t *)arr + 16;
    return jnative_string_from_utf8(stringMeta, src, len);
}

char *jnative_string_to_cstr(void *s) {
    if (s == NULL) return NULL;
    int32_t n = jn_str_len(s);
    const uint16_t *u = jn_str_data(s);
    for (int32_t i = 0; i < n; i++) {
        if (u[i] == 0) {
            jnative_throw_illegal_argument();
            return NULL;
        }
    }
    int32_t bytes = jn_utf8_encode_len_units(u, n);
    char *buf = (char *)malloc((size_t)bytes + 1u);
    if (buf == NULL) return NULL;
    jn_utf8_encode_units(u, n, (uint8_t *)buf);
    buf[bytes] = 0;
    return buf;
}
