/* Author: 张晓达
 * JNative Windows Explorer - native shell layer (C ABI).
 *
 * Final static link dependencies:   -l shell32  -l ole32  -l user32  -l advapi32
 * (every other API is resolved by the OS loader; no uuid.lib needed: INITGUID is defined.)
 *
 * Conventions
 *   - Text results are malloc'd UTF-8, released with jn_fm_free. Never NULL on success.
 *   - Records are '\n' separated, fields '\t' separated; tab / CR / LF inside a field -> ' '.
 *   - Int results: 0 = success, otherwise a Win32 error code (1223 = cancelled by the user).
 *   - Single caller thread (the UI thread). No function shows UI of its own except
 *     jn_fm_file_op (shell progress / conflict dialogs), jn_fm_shell_menu and jn_fm_shell_verb.
 *
 * jn_fm_list(path)            one record per child, '!' + code on failure:
 *       D|F \t name \t attrs \t size \t mtime_unix_ms \t ctime_unix_ms \t "yyyy/M/d HH:mm"
 *     attrs = Win32 FILE_ATTRIBUTE_* (1 ro, 2 hidden, 4 system, 16 dir, 1024 reparse).
 *     Error: a single line  "!\t<code>".
 * jn_fm_drives()              root \t display name \t DRIVE_* type \t total \t free
 * jn_fm_known_folder(id)      0 profile 1 desktop 2 downloads 3 documents 4 pictures 5 music
 *                             6 videos 7 appdata 8 temp 9 OneDrive ("" when absent)
 * jn_fm_type_name(name,isdir) localized shell type name ("TXT 文件", "文件夹")
 * jn_fm_exists(path)          0 missing, 1 file, 2 directory
 * jn_fm_full_path(path)       expands %VARS% and makes the path absolute
 * jn_fm_file_op(op,srcs,dst,flags)  op 1 copy 2 move 3 delete 4 recycle; srcs '\n' separated;
 *                             flags bit0 = rename on name collision. Shell UI shown.
 * jn_fm_clip_set/get/state/clear    CF_HDROP + "Preferred DropEffect" clipboard
 * jn_fm_clip_text(text)     puts plain text on the clipboard ("Copy as path")
 * jn_fm_rename(path,newName)  MoveFileEx inside the same folder
 * jn_fm_create(path,isdir)    0 or error (fails when it already exists)
 * jn_fm_shell_exec(path,verb,params,cwd)  ShellExecuteEx
 * jn_fm_shell_menu(paths,flags)  classic shell context menu at the cursor. flags bit0 extended verbs,
 *                             bit1 = single folder (verb "open" is returned, not invoked).
 *                             returns 0, 1 = user picked "rename", 2 = picked "open" on a folder.
 * jn_fm_shell_verb(paths,verb)   invokes a verb ("properties", ...) for the selection
 * jn_fm_search_begin/next/end    incremental recursive name search (see below)
 * jn_fm_watch_set/poll        directory change notification
 * jn_fm_spawn_self(path)      starts another instance of this program
 */
#ifndef JN_FM_H
#define JN_FM_H
#include <stdint.h>

void  jn_fm_free(void *p);
char *jn_fm_list(const char *path);
char *jn_fm_drives(void);
char *jn_fm_known_folder(int32_t id);
char *jn_fm_type_name(const char *name, int32_t isdir);
int32_t jn_fm_exists(const char *path);
char *jn_fm_full_path(const char *path);
int32_t jn_fm_file_op(int32_t op, const char *srcs, const char *dst, int32_t flags);
int32_t jn_fm_clip_set(const char *paths, int32_t cut);
char *jn_fm_clip_get(void);
int32_t jn_fm_clip_state(void);
void  jn_fm_clip_clear(void);
int32_t jn_fm_clip_text(const char *text);
int32_t jn_fm_rename(const char *path, const char *new_name);
int32_t jn_fm_create(const char *path, int32_t isdir);
int32_t jn_fm_shell_exec(const char *path, const char *verb, const char *params, const char *cwd);
int32_t jn_fm_shell_menu(const char *paths, int32_t flags);
int32_t jn_fm_shell_verb(const char *paths, const char *verb);
/* Search records: list record + '\t' + parent directory; the last call of a search ends with "#done". */
int32_t jn_fm_search_begin(const char *root, const char *query);
char *jn_fm_search_next(int32_t handle, int32_t budget_ms);
void  jn_fm_search_end(int32_t handle);
int32_t jn_fm_watch_set(const char *path);
int32_t jn_fm_watch_poll(void);
int32_t jn_fm_spawn_self(const char *path);

#endif
