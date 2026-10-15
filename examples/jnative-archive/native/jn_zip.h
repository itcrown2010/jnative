/* Author: 张晓达
 * Stable C ABI for JNative Archive — minizip-ng (Phase B: ZIP AES, ZIP64, tasks).
 */
#ifndef JN_ZIP_H
#define JN_ZIP_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

#define JN_ZIP_METHOD_STORE   0
#define JN_ZIP_METHOD_DEFLATE 8

#define JN_ZIP_LEVEL_DEFAULT  (-1)
#define JN_ZIP_LEVEL_FAST     2
#define JN_ZIP_LEVEL_NORMAL   6
#define JN_ZIP_LEVEL_BEST     9

/** WinZip AES strength (minizip-ng MZ_AES_STRENGTH_*). Default 256. */
#define JN_ZIP_AES_128  1
#define JN_ZIP_AES_192  2
#define JN_ZIP_AES_256  3

#define JN_ZIP_OK                 0
#define JN_ZIP_ERR                (-1)
#define JN_ZIP_ERR_PARAM          (-2)
#define JN_ZIP_ERR_IO             (-3)
#define JN_ZIP_ERR_FORMAT         (-4)
#define JN_ZIP_ERR_CRC            (-5)
#define JN_ZIP_ERR_PASSWORD       (-6)  /* wrong password and/or integrity/auth failure */
#define JN_ZIP_ERR_UNSUPPORTED    (-7)
#define JN_ZIP_ERR_PATH           (-8)
#define JN_ZIP_ERR_EOF            (-9)
#define JN_ZIP_ERR_STATE          (-10)
#define JN_ZIP_ERR_EXISTS         (-11)
#define JN_ZIP_ERR_NEED_PASSWORD  (-12)
#define JN_ZIP_ERR_CANCELLED      (-13)
#define JN_ZIP_ERR_LIMIT          (-14) /* entry count / size / expand budget */
#define JN_ZIP_ERR_LINK           (-15) /* symlink / reparse / junction policy */
#define JN_ZIP_ERR_SPACE          (-16)
#define JN_ZIP_ERR_SOURCE         (-17)
#define JN_ZIP_ERR_TARGET         (-18)

/** Overwrite policy for extract publish. */
#define JN_ZIP_OVERWRITE_FAIL      0
#define JN_ZIP_OVERWRITE_SKIP      1
#define JN_ZIP_OVERWRITE_REPLACE   2
#define JN_ZIP_OVERWRITE_RENAME    3

typedef struct jn_zip_entry_info {
    char *name;                 /* malloc UTF-8; free with jn_zip_entry_info_free */
    int64_t compressed_size;
    int64_t uncompressed_size;  /* -1 if unknown (data descriptor / streaming) */
    uint32_t crc32;
    int32_t method;
    int32_t is_dir;
    int32_t encrypted;
    int32_t aes_strength;       /* 0 if not AES; else 1/2/3 */
    int32_t is_symlink;         /* archive claims symlink — Phase B rejects extract */
    int64_t modified_unix;
} jn_zip_entry_info;

/** Extract / task limits. Zero or negative = no limit for that field. */
typedef struct jn_zip_limits {
    int64_t max_entry_uncompressed; /* bytes; unknown size counted as written */
    int64_t max_total_uncompressed;
    int64_t max_entries;
} jn_zip_limits;

/** Pollable counters (64-bit; ZIP64-safe). Updated during streaming ops. */
typedef struct jn_zip_stats {
    int64_t bytes_in;
    int64_t bytes_out;
    int64_t entries_done;
    int64_t entries_total;      /* -1 unknown */
    int64_t bytes_total_in;     /* -1 unknown */
    int32_t cancel_requested;
    int32_t cancel_acknowledged;
    int32_t last_error;
    char current_name[512];
} jn_zip_stats;

void jn_zip_free(void *p);
void jn_zip_entry_info_free(jn_zip_entry_info *info);

const char *jn_zip_engine_version(void);
/** Crypto/capability string: e.g. "WinZip AES-256 (AE-1/AE-2), UTF-8 password, ZipCrypto=OFF" */
const char *jn_zip_crypto_info(void);
int32_t jn_zip_last_error(void);
const char *jn_zip_strerror(int32_t code);

uint32_t jn_zip_crc32(uint32_t crc, const void *buf, int32_t len);
/** Monotonic-ish wall clock milliseconds for progress speed (not a secret). */
int64_t jn_zip_now_ms(void);

/**
 * Read password from FD into buf (NUL-terminated). Does not log.
 * Retries EINTR; rejects empty; rejects input that fills the buffer (too long).
 * Wipes trailing CR/LF. On error buf is wiped.
 */
int32_t jn_zip_password_read_fd(int32_t fd, char *buf, int32_t cap);
/** Interactive TTY prompt (no echo when possible). */
int32_t jn_zip_password_read_tty(const char *prompt, char *buf, int32_t cap);
void jn_zip_password_wipe(char *buf, int32_t cap);

/**
 * Allocate a unique temp path next to final_path for atomic publish (mkstemp pattern).
 * Caller must jn_zip_free the returned string and unlink the file when done.
 * Returns NULL on failure.
 */
char *jn_zip_make_unique_temp_path(const char *final_path);

/* ---- Writer ---- */
void *jn_zip_writer_create(void);
int32_t jn_zip_writer_open(void *w, const char *path, int32_t append);
/**
 * Set encryption password. NULL clears. Empty string → JN_ZIP_ERR_PARAM
 * (never silently create plaintext when encryption was requested).
 * Enables WinZip AES at configured strength; ZipCrypto is never used.
 */
int32_t jn_zip_writer_set_password(void *w, const char *password);
/** strength: JN_ZIP_AES_128/192/256. Default 256. */
int32_t jn_zip_writer_set_aes_strength(void *w, int32_t strength);
int32_t jn_zip_writer_put_next_entry(void *w, const char *name, int32_t method, int32_t level, int32_t is_dir);
int32_t jn_zip_writer_write(void *w, const void *buf, int32_t len);
int32_t jn_zip_writer_close_entry(void *w);
/** Stream file from disk with bounded buffer; respects cancel. */
int32_t jn_zip_writer_add_file(void *w, const char *src_path, const char *entry_name, int32_t method, int32_t level);
int32_t jn_zip_writer_finish(void *w);
int32_t jn_zip_writer_close(void *w);
void jn_zip_writer_destroy(void *w);
void jn_zip_writer_request_cancel(void *w);
int32_t jn_zip_writer_get_stats(void *w, jn_zip_stats *out);
int64_t jn_zip_writer_stat_bytes_in(void *w);
int64_t jn_zip_writer_stat_entries_done(void *w);
int32_t jn_zip_writer_stat_cancel_ack(void *w);
int32_t jn_zip_writer_stat_current_name(void *w, char *buf, int32_t cap);

/* ---- Reader ---- */
void *jn_zip_reader_create(void);
int32_t jn_zip_reader_open(void *r, const char *path);
int32_t jn_zip_reader_set_password(void *r, const char *password);
int32_t jn_zip_reader_goto_first(void *r);
int32_t jn_zip_reader_goto_next(void *r);
int32_t jn_zip_reader_locate(void *r, const char *name, int32_t ignore_case);
int32_t jn_zip_reader_entry_info(void *r, jn_zip_entry_info *out);
int32_t jn_zip_reader_entry_name(void *r, char *buf, int32_t cap);
int64_t jn_zip_reader_entry_uncompressed_size(void *r);
int64_t jn_zip_reader_entry_compressed_size(void *r);
uint32_t jn_zip_reader_entry_crc32(void *r);
int32_t jn_zip_reader_entry_method(void *r);
int32_t jn_zip_reader_entry_is_dir(void *r);
int32_t jn_zip_reader_entry_encrypted(void *r);
int32_t jn_zip_reader_entry_aes_strength(void *r);
int32_t jn_zip_reader_entry_is_symlink(void *r);
/** 1 if encrypted and no password set yet. */
int32_t jn_zip_reader_entry_needs_password(void *r);
int32_t jn_zip_reader_open_entry(void *r);
/**
 * Read decrypted bytes. Partial reads are NOT proof of authentication success.
 * Auth/CRC is finalized on close_entry (or extract_ex publish).
 */
int32_t jn_zip_reader_read(void *r, void *buf, int32_t len);
/**
 * Close entry and complete integrity/auth check.
 * Wrong password / auth / CRC → JN_ZIP_ERR_PASSWORD (or CRC).
 */
int32_t jn_zip_reader_close_entry(void *r);
/**
 * Extract current entry: stream to controlled temp under dest_dir, verify, then publish.
 * Rejects path escape, archive symlinks, and existing symlink components under dest.
 * overwrite: JN_ZIP_OVERWRITE_*.
 * limits: may be NULL.
 */
/**
 * Extract with overwrite policy and optional limits (0 = unlimited for that field).
 * Flat args for FFI (no struct pass-by-value).
 */
int32_t jn_zip_reader_extract_entry_ex(void *r, const char *dest_dir, int32_t overwrite,
        int64_t max_entry_uncompressed, int64_t max_total_uncompressed, int64_t max_entries);
/** Legacy: extract_ex with OVERWRITE_FAIL and no limits. */
int32_t jn_zip_reader_extract_entry(void *r, const char *dest_dir);
int32_t jn_zip_reader_test_all(void *r);
int32_t jn_zip_reader_close(void *r);
void jn_zip_reader_destroy(void *r);
void jn_zip_reader_request_cancel(void *r);
int32_t jn_zip_reader_get_stats(void *r, jn_zip_stats *out);
int64_t jn_zip_reader_stat_bytes_out(void *r);
int64_t jn_zip_reader_stat_entries_done(void *r);
int32_t jn_zip_reader_stat_cancel_ack(void *r);
int32_t jn_zip_reader_stat_current_name(void *r, char *buf, int32_t cap);

#ifdef __cplusplus
}
#endif

#endif
