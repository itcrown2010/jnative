/* Author: 张晓达 */
#ifndef JNATIVE_FS_H
#define JNATIVE_FS_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int32_t jnative_fs_exists(const char *path);
int32_t jnative_fs_is_directory(const char *path);
int32_t jnative_fs_is_file(const char *path);
int32_t jnative_fs_is_hidden(const char *path);
int32_t jnative_fs_create_directories(const char *path);
int32_t jnative_fs_delete(const char *path);
int32_t jnative_fs_rename(const char *from, const char *to);
int32_t jnative_fs_copy_file(const char *from, const char *to);
int64_t jnative_fs_file_size(const char *path);
int64_t jnative_fs_mtime_ms(const char *path);

/** Newline-separated entry names (not full paths). Caller frees with jnative_fs_free. */
char *jnative_fs_list(const char *path);

/**
 * Tab-separated records, one per line:
 * name \\t isDir(0|1) \\t size \\t mtimeMs \\t hidden(0|1) \\n
 * Caller frees with jnative_fs_free.
 */
char *jnative_fs_list_info(const char *path);

/** Newline-separated filesystem roots (e.g. "/" or "C:\\"). Caller frees. */
char *jnative_fs_roots(void);

/**
 * Known folders: "home" | "desktop" | "documents" | "downloads" | "pictures".
 * Caller frees.
 */
char *jnative_fs_known_folder(const char *id);

/** Open path with OS default application (ShellExecute / open / xdg-open). */
int32_t jnative_fs_open(const char *path);

/** Move path to Recycle Bin / Trash. */
int32_t jnative_fs_trash(const char *path);

void jnative_fs_free(char *p);

/** Platform config directory for appName. Caller frees. */
char *jnative_fs_config_dir(const char *appName);

/**
 * Directory watch (platform backend).
 * Returns opaque handle id > 0, or 0 on failure.
 */
int64_t jnative_fs_watch_open(const char *path);
/** Non-blocking poll: 1 if directory changed since last poll, else 0. */
int32_t jnative_fs_watch_poll(int64_t handle);
/**
 * Extended poll: 0=none, 1=changed, 2=OVERFLOW/RESCAN_REQUIRED.
 */
int32_t jnative_fs_watch_poll_ex(int64_t handle);
void jnative_fs_watch_close(int64_t handle);

/** Stream FD: mode 0=read, 1=write trunc create, 2=append create. Returns fd>=0 or -1. */
int64_t jnative_fs_stream_open(const char *path, int32_t mode);
/** Read up to len bytes. Returns n>0, 0=EOF, -1=error. Retries EINTR. */
int32_t jnative_fs_stream_read(int64_t fd, void *buf, int32_t len);
/** Write up to len bytes (may be partial). Returns n>=0 or -1. Retries EINTR. */
int32_t jnative_fs_stream_write(int64_t fd, const void *buf, int32_t len);
int32_t jnative_fs_stream_close(int64_t fd);
int32_t jnative_fs_stream_fsync(int64_t fd);
/** Seek to absolute byte offset. Returns 0 on success, -1 on error. */
int32_t jnative_fs_stream_seek(int64_t fd, int64_t offset);

/** Symlink: 1 if path is a symlink. */
int32_t jnative_fs_is_symlink(const char *path);
/** readlink into malloc'd UTF-8; caller frees. NULL on error. */
char *jnative_fs_readlink(const char *path);

#ifdef __cplusplus
}
#endif

#endif
