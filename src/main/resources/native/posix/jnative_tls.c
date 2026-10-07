/* JNative TLS — OpenSSL 1.1+/3.x backend. Opaque ABI for TlsLib. */
#include "jnative_tls.h"

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#if defined(_WIN32)
#include <winsock2.h>
#else
#include <fcntl.h>
#include <unistd.h>
#endif

#include <openssl/ssl.h>
#include <openssl/err.h>
#include <openssl/x509v3.h>
#include <openssl/pem.h>
#include <openssl/bio.h>

typedef struct JnTlsCtx {
    SSL_CTX *ssl_ctx;
    int is_server;
    int insecure;
    char alpn_wire[64];
    int alpn_wire_len;
} JnTlsCtx;

typedef struct JnTlsConn {
    SSL *ssl;
    JnTlsCtx *parent;
    int fd;
    int owns_fd; /* 0 — TcpSocket still owns fd */
    char alpn[32];
    char version[32];
} JnTlsConn;

static void set_err(char *errbuf, int errbuf_len, const char *msg) {
    if (errbuf == NULL || errbuf_len <= 0) return;
    snprintf(errbuf, (size_t)errbuf_len, "%s", msg ? msg : "tls error");
}

static void set_ssl_err(char *errbuf, int errbuf_len, const char *prefix) {
    if (errbuf == NULL || errbuf_len <= 0) return;
    unsigned long e = ERR_get_error();
    char tmp[256];
    if (e == 0) {
        snprintf(errbuf, (size_t)errbuf_len, "%s", prefix ? prefix : "tls error");
        return;
    }
    ERR_error_string_n(e, tmp, sizeof(tmp));
    /* Translate common OpenSSL messages */
    if (strstr(tmp, "certificate verify failed") != NULL) {
        snprintf(errbuf, (size_t)errbuf_len, "certificate verification failed: %s", tmp);
    } else if (strstr(tmp, "hostname mismatch") != NULL || strstr(tmp, "IP address mismatch") != NULL) {
        snprintf(errbuf, (size_t)errbuf_len, "hostname mismatch: %s", tmp);
    } else if (strstr(tmp, "certificate has expired") != NULL) {
        snprintf(errbuf, (size_t)errbuf_len, "certificate expired: %s", tmp);
    } else if (strstr(tmp, "unable to get local issuer") != NULL) {
        snprintf(errbuf, (size_t)errbuf_len, "unknown CA: %s", tmp);
    } else {
        snprintf(errbuf, (size_t)errbuf_len, "%s: %s", prefix ? prefix : "tls", tmp);
    }
}

static int map_ssl_io(SSL *ssl, int rc) {
    int err = SSL_get_error(ssl, rc);
    if (err == SSL_ERROR_WANT_READ) return JN_TLS_WANT_READ;
    if (err == SSL_ERROR_WANT_WRITE) return JN_TLS_WANT_WRITE;
    if (err == SSL_ERROR_ZERO_RETURN) return JN_TLS_OK;
    return JN_TLS_FAIL;
}

static int build_alpn_wire(const char *csv, unsigned char *out, int out_cap, int *out_len) {
    if (csv == NULL || csv[0] == '\0') {
        /* default h2, http/1.1 */
        csv = "h2,http/1.1";
    }
    int n = 0;
    const char *p = csv;
    while (*p && n + 2 < out_cap) {
        while (*p == ' ' || *p == ',') p++;
        const char *start = p;
        while (*p && *p != ',') p++;
        int len = (int)(p - start);
        while (len > 0 && start[len - 1] == ' ') len--;
        if (len <= 0 || len > 255 || n + 1 + len >= out_cap) break;
        out[n++] = (unsigned char)len;
        memcpy(out + n, start, (size_t)len);
        n += len;
        if (*p == ',') p++;
    }
    *out_len = n;
    return n > 0 ? 0 : -1;
}

static int alpn_select_cb(
        SSL *ssl,
        const unsigned char **out,
        unsigned char *outlen,
        const unsigned char *in,
        unsigned int inlen,
        void *arg
) {
    (void)ssl;
    JnTlsCtx *ctx = (JnTlsCtx *)arg;
    if (ctx == NULL || ctx->alpn_wire_len <= 0) return SSL_TLSEXT_ERR_NOACK;
    if (SSL_select_next_proto(
            (unsigned char **)out, outlen,
            (const unsigned char *)ctx->alpn_wire, (unsigned int)ctx->alpn_wire_len,
            in, inlen) != OPENSSL_NPN_NEGOTIATED) {
        return SSL_TLSEXT_ERR_NOACK;
    }
    return SSL_TLSEXT_ERR_OK;
}

static void ensure_openssl(void) {
    static int once = 0;
    if (once) return;
    once = 1;
#if OPENSSL_VERSION_NUMBER < 0x10100000L
    SSL_library_init();
    SSL_load_error_strings();
    OpenSSL_add_all_algorithms();
#endif
}

static SSL_CTX *new_ctx(int server, char *errbuf, int errbuf_len) {
    ensure_openssl();
    const SSL_METHOD *method = server ? TLS_server_method() : TLS_client_method();
    SSL_CTX *ctx = SSL_CTX_new(method);
    if (ctx == NULL) {
        set_ssl_err(errbuf, errbuf_len, "SSL_CTX_new failed");
        return NULL;
    }
    SSL_CTX_set_min_proto_version(ctx, TLS1_2_VERSION);
    SSL_CTX_set_options(ctx, SSL_OP_NO_SSLv2 | SSL_OP_NO_SSLv3 | SSL_OP_NO_TLSv1 | SSL_OP_NO_TLSv1_1);
    SSL_CTX_set_mode(ctx, SSL_MODE_ENABLE_PARTIAL_WRITE | SSL_MODE_ACCEPT_MOVING_WRITE_BUFFER);
    return ctx;
}

int64_t jnative_tls_client_ctx_new(
        int insecure_skip_verify,
        const char *ca_file,
        const char *alpn_csv,
        char *errbuf,
        int errbuf_len
) {
    SSL_CTX *ssl_ctx = new_ctx(0, errbuf, errbuf_len);
    if (ssl_ctx == NULL) return 0;
    JnTlsCtx *ctx = (JnTlsCtx *)calloc(1, sizeof(JnTlsCtx));
    if (ctx == NULL) {
        SSL_CTX_free(ssl_ctx);
        set_err(errbuf, errbuf_len, "oom");
        return 0;
    }
    ctx->ssl_ctx = ssl_ctx;
    ctx->is_server = 0;
    ctx->insecure = insecure_skip_verify ? 1 : 0;
    build_alpn_wire(alpn_csv, (unsigned char *)ctx->alpn_wire, (int)sizeof(ctx->alpn_wire), &ctx->alpn_wire_len);
    SSL_CTX_set_alpn_protos(ssl_ctx, (const unsigned char *)ctx->alpn_wire, (unsigned int)ctx->alpn_wire_len);

    if (ctx->insecure) {
        SSL_CTX_set_verify(ssl_ctx, SSL_VERIFY_NONE, NULL);
    } else {
        SSL_CTX_set_verify(ssl_ctx, SSL_VERIFY_PEER, NULL);
        SSL_CTX_set_default_verify_paths(ssl_ctx);
        if (ca_file != NULL && ca_file[0] != '\0') {
            if (SSL_CTX_load_verify_locations(ssl_ctx, ca_file, NULL) != 1) {
                set_ssl_err(errbuf, errbuf_len, "load CA bundle failed");
                jnative_tls_ctx_free((int64_t)(intptr_t)ctx);
                return 0;
            }
        }
    }
    return (int64_t)(intptr_t)ctx;
}

int64_t jnative_tls_server_ctx_new(
        const char *cert_file,
        const char *key_file,
        const char *alpn_csv,
        char *errbuf,
        int errbuf_len
) {
    if (cert_file == NULL || key_file == NULL) {
        set_err(errbuf, errbuf_len, "certificate and private key required");
        return 0;
    }
    SSL_CTX *ssl_ctx = new_ctx(1, errbuf, errbuf_len);
    if (ssl_ctx == NULL) return 0;
    if (SSL_CTX_use_certificate_chain_file(ssl_ctx, cert_file) != 1) {
        set_ssl_err(errbuf, errbuf_len, "load certificate failed");
        SSL_CTX_free(ssl_ctx);
        return 0;
    }
    if (SSL_CTX_use_PrivateKey_file(ssl_ctx, key_file, SSL_FILETYPE_PEM) != 1) {
        set_ssl_err(errbuf, errbuf_len, "load private key failed");
        SSL_CTX_free(ssl_ctx);
        return 0;
    }
    if (SSL_CTX_check_private_key(ssl_ctx) != 1) {
        set_err(errbuf, errbuf_len, "private key does not match certificate");
        SSL_CTX_free(ssl_ctx);
        return 0;
    }
    JnTlsCtx *ctx = (JnTlsCtx *)calloc(1, sizeof(JnTlsCtx));
    if (ctx == NULL) {
        SSL_CTX_free(ssl_ctx);
        set_err(errbuf, errbuf_len, "oom");
        return 0;
    }
    ctx->ssl_ctx = ssl_ctx;
    ctx->is_server = 1;
    build_alpn_wire(alpn_csv, (unsigned char *)ctx->alpn_wire, (int)sizeof(ctx->alpn_wire), &ctx->alpn_wire_len);
    SSL_CTX_set_alpn_select_cb(ssl_ctx, alpn_select_cb, ctx);
    return (int64_t)(intptr_t)ctx;
}

int jnative_tls_ctx_reload_server(
        int64_t ctx_h,
        const char *cert_file,
        const char *key_file,
        char *errbuf,
        int errbuf_len
) {
    JnTlsCtx *ctx = (JnTlsCtx *)(intptr_t)ctx_h;
    if (ctx == NULL || ctx->ssl_ctx == NULL || !ctx->is_server) {
        set_err(errbuf, errbuf_len, "invalid server tls context");
        return JN_TLS_FAIL;
    }
    if (SSL_CTX_use_certificate_chain_file(ctx->ssl_ctx, cert_file) != 1) {
        set_ssl_err(errbuf, errbuf_len, "reload certificate failed");
        return JN_TLS_FAIL;
    }
    if (SSL_CTX_use_PrivateKey_file(ctx->ssl_ctx, key_file, SSL_FILETYPE_PEM) != 1) {
        set_ssl_err(errbuf, errbuf_len, "reload private key failed");
        return JN_TLS_FAIL;
    }
    if (SSL_CTX_check_private_key(ctx->ssl_ctx) != 1) {
        set_err(errbuf, errbuf_len, "reloaded key does not match certificate");
        return JN_TLS_FAIL;
    }
    return JN_TLS_OK;
}

void jnative_tls_ctx_free(int64_t ctx_h) {
    JnTlsCtx *ctx = (JnTlsCtx *)(intptr_t)ctx_h;
    if (ctx == NULL) return;
    if (ctx->ssl_ctx) SSL_CTX_free(ctx->ssl_ctx);
    free(ctx);
}

static int64_t wrap_common(JnTlsCtx *ctx, int64_t fd, const char *server_name, int server, char *errbuf, int errbuf_len) {
    if (ctx == NULL || ctx->ssl_ctx == NULL || fd < 0) {
        set_err(errbuf, errbuf_len, "invalid tls wrap arguments");
        return 0;
    }
    SSL *ssl = SSL_new(ctx->ssl_ctx);
    if (ssl == NULL) {
        set_ssl_err(errbuf, errbuf_len, "SSL_new failed");
        return 0;
    }
    if (SSL_set_fd(ssl, (int)fd) != 1) {
        set_ssl_err(errbuf, errbuf_len, "SSL_set_fd failed");
        SSL_free(ssl);
        return 0;
    }
    if (!server) {
        SSL_set_connect_state(ssl);
        if (server_name != NULL && server_name[0] != '\0') {
            SSL_set_tlsext_host_name(ssl, server_name);
#if OPENSSL_VERSION_NUMBER >= 0x10002000L
            /* hostname verification (OpenSSL 1.0.2+) */
            X509_VERIFY_PARAM *param = SSL_get0_param(ssl);
            if (param != NULL) {
                X509_VERIFY_PARAM_set_hostflags(param, X509_CHECK_FLAG_NO_PARTIAL_WILDCARDS);
                X509_VERIFY_PARAM_set1_host(param, server_name, 0);
            }
#endif
        }
        if (!ctx->insecure) {
            SSL_set_verify(ssl, SSL_VERIFY_PEER, NULL);
        }
    } else {
        SSL_set_accept_state(ssl);
    }
    JnTlsConn *conn = (JnTlsConn *)calloc(1, sizeof(JnTlsConn));
    if (conn == NULL) {
        SSL_free(ssl);
        set_err(errbuf, errbuf_len, "oom");
        return 0;
    }
    conn->ssl = ssl;
    conn->parent = ctx;
    conn->fd = (int)fd;
    conn->owns_fd = 0;
    return (int64_t)(intptr_t)conn;
}

int64_t jnative_tls_wrap_client(
        int64_t ctx_h,
        int64_t fd,
        const char *server_name,
        char *errbuf,
        int errbuf_len
) {
    return wrap_common((JnTlsCtx *)(intptr_t)ctx_h, fd, server_name, 0, errbuf, errbuf_len);
}

int64_t jnative_tls_wrap_server(
        int64_t ctx_h,
        int64_t fd,
        char *errbuf,
        int errbuf_len
) {
    return wrap_common((JnTlsCtx *)(intptr_t)ctx_h, fd, NULL, 1, errbuf, errbuf_len);
}

static void capture_meta(JnTlsConn *conn) {
    if (conn == NULL || conn->ssl == NULL) return;
    const unsigned char *alpn = NULL;
    unsigned int alpn_len = 0;
    SSL_get0_alpn_selected(conn->ssl, &alpn, &alpn_len);
    if (alpn != NULL && alpn_len > 0 && alpn_len < sizeof(conn->alpn)) {
        memcpy(conn->alpn, alpn, alpn_len);
        conn->alpn[alpn_len] = 0;
    } else {
        conn->alpn[0] = 0;
    }
    const char *v = SSL_get_version(conn->ssl);
    if (v != NULL) {
        snprintf(conn->version, sizeof(conn->version), "%s", v);
    }
}

int jnative_tls_handshake(int64_t tls_h, char *errbuf, int errbuf_len) {
    JnTlsConn *conn = (JnTlsConn *)(intptr_t)tls_h;
    if (conn == NULL || conn->ssl == NULL) {
        set_err(errbuf, errbuf_len, "invalid tls connection");
        return JN_TLS_FAIL;
    }
    int rc = SSL_do_handshake(conn->ssl);
    if (rc == 1) {
        if (!conn->parent->is_server && !conn->parent->insecure) {
            long vr = SSL_get_verify_result(conn->ssl);
            if (vr != X509_V_OK) {
                snprintf(errbuf, (size_t)errbuf_len, "certificate verification failed: %s",
                         X509_verify_cert_error_string(vr));
                return JN_TLS_FAIL;
            }
        }
        capture_meta(conn);
        return JN_TLS_OK;
    }
    int mapped = map_ssl_io(conn->ssl, rc);
    if (mapped == JN_TLS_FAIL) {
        set_ssl_err(errbuf, errbuf_len, "handshake failed");
    }
    return mapped;
}

int jnative_tls_read(
        int64_t tls_h,
        uint8_t *buf,
        int len,
        char *errbuf,
        int errbuf_len
) {
    JnTlsConn *conn = (JnTlsConn *)(intptr_t)tls_h;
    if (conn == NULL || conn->ssl == NULL || buf == NULL || len <= 0) {
        set_err(errbuf, errbuf_len, "invalid tls read");
        return -1;
    }
    int n = SSL_read(conn->ssl, buf, len);
    if (n >= 0) {
        return n;
    }
    int mapped = map_ssl_io(conn->ssl, n);
    if (mapped == JN_TLS_WANT_READ) return -2;
    if (mapped == JN_TLS_WANT_WRITE) return -3;
    set_ssl_err(errbuf, errbuf_len, "tls read failed");
    return -1;
}

int jnative_tls_write(
        int64_t tls_h,
        const uint8_t *buf,
        int len,
        char *errbuf,
        int errbuf_len
) {
    JnTlsConn *conn = (JnTlsConn *)(intptr_t)tls_h;
    if (conn == NULL || conn->ssl == NULL || buf == NULL || len < 0) {
        set_err(errbuf, errbuf_len, "invalid tls write");
        return -1;
    }
    int n = SSL_write(conn->ssl, buf, len);
    if (n >= 0) {
        return n;
    }
    int mapped = map_ssl_io(conn->ssl, n);
    if (mapped == JN_TLS_WANT_READ) return -2;
    if (mapped == JN_TLS_WANT_WRITE) return -3;
    set_ssl_err(errbuf, errbuf_len, "tls write failed");
    return -1;
}

void jnative_tls_close(int64_t tls_h) {
    JnTlsConn *conn = (JnTlsConn *)(intptr_t)tls_h;
    if (conn == NULL) return;
    if (conn->ssl != NULL) {
        SSL_shutdown(conn->ssl);
        SSL_free(conn->ssl);
        conn->ssl = NULL;
    }
    /* never log key material; fd owned by TcpSocket */
    free(conn);
}

int jnative_tls_alpn(int64_t tls_h, char *out, int out_len) {
    JnTlsConn *conn = (JnTlsConn *)(intptr_t)tls_h;
    if (conn == NULL || out == NULL || out_len <= 0) return -1;
    snprintf(out, (size_t)out_len, "%s", conn->alpn);
    return 0;
}

int jnative_tls_version(int64_t tls_h, char *out, int out_len) {
    JnTlsConn *conn = (JnTlsConn *)(intptr_t)tls_h;
    if (conn == NULL || out == NULL || out_len <= 0) return -1;
    snprintf(out, (size_t)out_len, "%s", conn->version);
    return 0;
}

int jnative_tls_inspect_cert(
        const char *cert_file,
        char *out,
        int out_len,
        char *errbuf,
        int errbuf_len
) {
    if (cert_file == NULL || out == NULL || out_len <= 0) {
        set_err(errbuf, errbuf_len, "invalid inspect arguments");
        return -1;
    }
    ensure_openssl();
    FILE *f = fopen(cert_file, "rb");
    if (f == NULL) {
        set_err(errbuf, errbuf_len, "cannot open certificate file");
        return -1;
    }
    X509 *cert = PEM_read_X509(f, NULL, NULL, NULL);
    fclose(f);
    if (cert == NULL) {
        set_ssl_err(errbuf, errbuf_len, "parse certificate failed");
        return -1;
    }
    BIO *bio = BIO_new(BIO_s_mem());
    if (bio == NULL) {
        X509_free(cert);
        set_err(errbuf, errbuf_len, "oom");
        return -1;
    }
    X509_NAME_print_ex(bio, X509_get_subject_name(cert), 0, XN_FLAG_ONELINE);
    BIO_printf(bio, "\n");
    X509_NAME_print_ex(bio, X509_get_issuer_name(cert), 0, XN_FLAG_ONELINE);
    BIO_printf(bio, "\n");
    ASN1_TIME *nb = X509_get_notBefore(cert);
    ASN1_TIME *na = X509_get_notAfter(cert);
    BIO_printf(bio, "notBefore=");
    ASN1_TIME_print(bio, nb);
    BIO_printf(bio, "\nnotAfter=");
    ASN1_TIME_print(bio, na);
    BIO_printf(bio, "\n");
    GENERAL_NAMES *sans = X509_get_ext_d2i(cert, NID_subject_alt_name, NULL, NULL);
    if (sans != NULL) {
        BIO_printf(bio, "SAN=");
        int count = sk_GENERAL_NAME_num(sans);
        for (int i = 0; i < count; i++) {
            GENERAL_NAME *gn = sk_GENERAL_NAME_value(sans, i);
            if (gn->type == GEN_DNS && gn->d.dNSName != NULL) {
                BIO_printf(bio, "DNS:%.*s ", gn->d.dNSName->length, gn->d.dNSName->data);
            }
        }
        BIO_printf(bio, "\n");
        GENERAL_NAMES_free(sans);
    }
    const EVP_MD *md = EVP_sha256();
    unsigned char dig[EVP_MAX_MD_SIZE];
    unsigned int dig_len = 0;
    if (X509_digest(cert, md, dig, &dig_len) == 1) {
        BIO_printf(bio, "sha256=");
        for (unsigned int i = 0; i < dig_len; i++) BIO_printf(bio, "%02x", dig[i]);
        BIO_printf(bio, "\n");
    }
    /* never print private key */
    char *data = NULL;
    long len = BIO_get_mem_data(bio, &data);
    if (data != NULL && len > 0) {
        if (len >= out_len) len = out_len - 1;
        memcpy(out, data, (size_t)len);
        out[len] = 0;
    } else {
        out[0] = 0;
    }
    BIO_free(bio);
    X509_free(cert);
    return 0;
}
