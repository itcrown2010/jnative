#ifndef JNATIVE_FS_H
#define JNATIVE_FS_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

int32_t jnative_fs_exists(const char *path);
int32_t jnative_fs_is_directory(const char *path);
int32_t jnative_fs_is_file(const char *path);
int32_t jnative_fs_create_directories(const char *path);
int32_t jnative_fs_delete(const char *path);
int32_t jnative_fs_rename(const char *from, const char *to);

/** Newline-separated entry names (not full paths). Caller frees with jnative_fs_free. */
char *jnative_fs_list(const char *path);
void jnative_fs_free(char *p);

/** Platform config directory for appName (e.g. Application Support / .config). Caller frees. */
char *jnative_fs_config_dir(const char *appName);

#ifdef __cplusplus
}
#endif

#endif
