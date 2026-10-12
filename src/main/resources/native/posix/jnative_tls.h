/* Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

/* Author: 张晓达 */
/* JNative TLS runtime (0.31) — OpenSSL backend behind opaque C ABI.
 * NOT a compiler intrinsic. Application code uses TlsLib / HttpClient / WebApp.
 */
#ifndef JNATIVE_TLS_H
#define JNATIVE_TLS_H

#include <stdint.h>
#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/* Handshake / IO want codes */
#define JN_TLS_OK            0
#define JN_TLS_WANT_READ     1
#define JN_TLS_WANT_WRITE    2
#define JN_TLS_FAIL         -1

/* Opaque handles stored as int64 (pointer bits). */
int64_t jnative_tls_client_ctx_new(
        int insecure_skip_verify,
        const char *ca_file,
        const char *alpn_csv,
        char *errbuf,
        int errbuf_len);

int64_t jnative_tls_server_ctx_new(
        const char *cert_file,
        const char *key_file,
        const char *alpn_csv,
        char *errbuf,
        int errbuf_len);

int jnative_tls_ctx_reload_server(
        int64_t ctx,
        const char *cert_file,
        const char *key_file,
        char *errbuf,
        int errbuf_len);

void jnative_tls_ctx_free(int64_t ctx);

/* Wrap an already-connected non-blocking-capable TCP fd. takes ownership of TLS only, not fd. */
int64_t jnative_tls_wrap_client(
        int64_t ctx,
        int64_t fd,
        const char *server_name,
        char *errbuf,
        int errbuf_len);

int64_t jnative_tls_wrap_server(
        int64_t ctx,
        int64_t fd,
        char *errbuf,
        int errbuf_len);

/* Drive handshake; returns JN_TLS_OK / WANT_READ / WANT_WRITE / FAIL */
int jnative_tls_handshake(int64_t tls, char *errbuf, int errbuf_len);

/* Returns n>=0 bytes, or -1 fail, -2 WANT_READ, -3 WANT_WRITE */
int jnative_tls_read(
        int64_t tls,
        uint8_t *buf,
        int len,
        char *errbuf,
        int errbuf_len);

int jnative_tls_write(
        int64_t tls,
        const uint8_t *buf,
        int len,
        char *errbuf,
        int errbuf_len);

void jnative_tls_close(int64_t tls);

/* Returns static/internal string; valid until next call on same connection or close. */
int jnative_tls_alpn(int64_t tls, char *out, int out_len);
int jnative_tls_version(int64_t tls, char *out, int out_len);

/* PEM helpers for CLI / inspect (no private key material returned). */
int jnative_tls_inspect_cert(
        const char *cert_file,
        char *out,
        int out_len,
        char *errbuf,
        int errbuf_len);

#ifdef __cplusplus
}
#endif

#endif
