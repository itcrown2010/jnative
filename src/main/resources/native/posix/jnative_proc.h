#ifndef JNATIVE_PROC_H
#define JNATIVE_PROC_H

#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Spawn without shell. argv_joined: argc args separated by '\\n'.
 * Returns malloc'd "pid\\nstdoutFd\\nstderrFd" or NULL.
 */
char *jnative_proc_spawn_s(
        const char *file,
        const char *argv_joined,
        int32_t argc,
        const char *cwd);

int32_t jnative_proc_wait(int32_t pid);
int32_t jnative_proc_kill(int32_t pid);
char *jnative_proc_read_string(int32_t fd, int32_t max_bytes);
void jnative_proc_close_fd(int32_t fd);
void jnative_proc_free(char *p);

#ifdef __cplusplus
}
#endif

#endif
