/* Author: 张晓达 — Phase A tech spike for minizip-ng via jn_zip ABI. */
#include "jn_zip.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

static int fail(const char *msg, int32_t code) {
    fprintf(stderr, "FAIL: %s (%d %s)\n", msg, (int)code, jn_zip_strerror(code));
    return 1;
}

static int write_file(const char *path, const char *data) {
    FILE *f = fopen(path, "wb");
    if (!f) return -1;
    fwrite(data, 1, strlen(data), f);
    fclose(f);
    return 0;
}

static int read_all(const char *path, char *buf, int cap) {
    FILE *f = fopen(path, "rb");
    size_t n;
    if (!f) return -1;
    n = fread(buf, 1, (size_t)(cap - 1), f);
    fclose(f);
    buf[n] = 0;
    return (int)n;
}

int main(void) {
    const char *tdir = "examples/jnative-archive/build/spike";
    char cmd[512];
    char buf[256];
    void *w;
    void *r;
    int32_t err;
    jn_zip_entry_info info;

    snprintf(cmd, sizeof(cmd), "rm -rf %s && mkdir -p %s/in %s/out %s/out_aes", tdir, tdir, tdir, tdir);
    if (system(cmd) != 0) return fail("mkdir", -1);

    write_file("examples/jnative-archive/build/spike/in/hello.txt", "hello jnative archive\n");
    write_file("examples/jnative-archive/build/spike/in/中文.txt", "unicode path ok\n");

    printf("engine=%s\n", jn_zip_engine_version());

    /* Create plain ZIP (deflate) */
    w = jn_zip_writer_create();
    err = jn_zip_writer_open(w, "examples/jnative-archive/build/spike/plain.zip", 0);
    if (err) return fail("writer_open", err);
    err = jn_zip_writer_add_file(w, "examples/jnative-archive/build/spike/in/hello.txt", "hello.txt",
            JN_ZIP_METHOD_DEFLATE, JN_ZIP_LEVEL_NORMAL);
    if (err) return fail("add hello", err);
    err = jn_zip_writer_add_file(w, "examples/jnative-archive/build/spike/in/中文.txt", "中文.txt",
            JN_ZIP_METHOD_DEFLATE, JN_ZIP_LEVEL_NORMAL);
    if (err) return fail("add unicode", err);
    /* streaming entry */
    err = jn_zip_writer_put_next_entry(w, "streamed.txt", JN_ZIP_METHOD_STORE, 0, 0);
    if (err) return fail("put_next", err);
    err = jn_zip_writer_write(w, "stream-data", 11);
    if (err < 0) return fail("write", err);
    err = jn_zip_writer_close_entry(w);
    if (err) return fail("close_entry", err);
    err = jn_zip_writer_close(w);
    if (err) return fail("writer_close", err);
    jn_zip_writer_destroy(w);
    printf("plain.zip created\n");

    /* List + test + extract */
    r = jn_zip_reader_create();
    err = jn_zip_reader_open(r, "examples/jnative-archive/build/spike/plain.zip");
    if (err) return fail("reader_open", err);
    err = jn_zip_reader_test_all(r);
    if (err) return fail("test_all", err);
    err = jn_zip_reader_goto_first(r);
    if (err) return fail("first", err);
    do {
        memset(&info, 0, sizeof(info));
        err = jn_zip_reader_entry_info(r, &info);
        if (err) return fail("info", err);
        printf("  entry name=%s method=%d size=%lld crc=%08x enc=%d\n",
                info.name, (int)info.method, (long long)info.uncompressed_size,
                (unsigned)info.crc32, (int)info.encrypted);
        err = jn_zip_reader_extract_entry(r, "examples/jnative-archive/build/spike/out");
        if (err) return fail("extract", err);
        jn_zip_entry_info_free(&info);
        err = jn_zip_reader_goto_next(r);
    } while (err == JN_ZIP_OK);
    if (err != JN_ZIP_ERR_EOF) return fail("next", err);
    jn_zip_reader_close(r);
    jn_zip_reader_destroy(r);

    if (read_all("examples/jnative-archive/build/spike/out/hello.txt", buf, sizeof(buf)) < 0)
        return fail("read extracted", -1);
    if (strcmp(buf, "hello jnative archive\n") != 0) return fail("content mismatch", -1);
    printf("extract content OK\n");

    /* Interop: system unzip -t */
    if (system("unzip -t examples/jnative-archive/build/spike/plain.zip >/tmp/jn_zip_unzip_t.out 2>&1") != 0)
        return fail("system unzip -t", -1);
    printf("system unzip -t OK\n");

    /* AES create/decrypt spike (Phase B feature smoke on Mac CommonCrypto) */
    w = jn_zip_writer_create();
    err = jn_zip_writer_open(w, "examples/jnative-archive/build/spike/aes.zip", 0);
    if (err) return fail("aes open", err);
    err = jn_zip_writer_set_password(w, "test-pass-not-logged");
    if (err) return fail("set_password", err);
    err = jn_zip_writer_add_file(w, "examples/jnative-archive/build/spike/in/hello.txt", "secret.txt",
            JN_ZIP_METHOD_DEFLATE, JN_ZIP_LEVEL_FAST);
    if (err) return fail("aes add", err);
    err = jn_zip_writer_close(w);
    if (err) return fail("aes close", err);
    jn_zip_writer_destroy(w);

    r = jn_zip_reader_create();
    err = jn_zip_reader_open(r, "examples/jnative-archive/build/spike/aes.zip");
    if (err) return fail("aes reader", err);
    err = jn_zip_reader_set_password(r, "wrong");
    if (err) return fail("aes wrong set", err);
    err = jn_zip_reader_test_all(r);
    if (err == JN_ZIP_OK) return fail("wrong password should fail", err);
    printf("wrong password rejected (%s)\n", jn_zip_strerror(err));
    jn_zip_reader_close(r);
    jn_zip_reader_destroy(r);

    r = jn_zip_reader_create();
    err = jn_zip_reader_open(r, "examples/jnative-archive/build/spike/aes.zip");
    if (err) return fail("aes reader2", err);
    err = jn_zip_reader_set_password(r, "test-pass-not-logged");
    if (err) return fail("aes good set", err);
    err = jn_zip_reader_test_all(r);
    if (err) return fail("aes test", err);
    err = jn_zip_reader_goto_first(r);
    if (err) return fail("aes first", err);
    err = jn_zip_reader_extract_entry(r, "examples/jnative-archive/build/spike/out_aes");
    if (err) return fail("aes extract", err);
    jn_zip_reader_close(r);
    jn_zip_reader_destroy(r);
    if (read_all("examples/jnative-archive/build/spike/out_aes/secret.txt", buf, sizeof(buf)) < 0)
        return fail("aes read", -1);
    if (strcmp(buf, "hello jnative archive\n") != 0) return fail("aes content", -1);
    printf("AES encrypt/decrypt OK\n");

    /* Cancel mid-write (not only before start) */
    w = jn_zip_writer_create();
    err = jn_zip_writer_open(w, "examples/jnative-archive/build/spike/cancel.zip", 0);
    if (err) return fail("cancel open", err);
    err = jn_zip_writer_put_next_entry(w, "c.txt", JN_ZIP_METHOD_STORE, 0, 0);
    if (err) return fail("cancel put", err);
    err = jn_zip_writer_write(w, "0123456789", 10);
    if (err < 0) return fail("cancel write1", err);
    jn_zip_writer_request_cancel(w);
    err = jn_zip_writer_write(w, "abcdefghij", 10);
    if (err != JN_ZIP_ERR_CANCELLED) return fail("mid-write cancel expected", err);
    jn_zip_writer_close(w);
    jn_zip_writer_destroy(w);
    printf("mid-write cancel OK\n");

    /* finish() must produce a readable ZIP before destroy/close */
    w = jn_zip_writer_create();
    err = jn_zip_writer_open(w, "examples/jnative-archive/build/spike/finish.zip", 0);
    if (err) return fail("finish open", err);
    err = jn_zip_writer_add_file(w, "examples/jnative-archive/build/spike/in/hello.txt", "hello.txt",
            JN_ZIP_METHOD_DEFLATE, JN_ZIP_LEVEL_NORMAL);
    if (err) return fail("finish add", err);
    err = jn_zip_writer_finish(w);
    if (err) return fail("finish", err);
    /* Independent tool must read before destroy */
    if (system("unzip -t examples/jnative-archive/build/spike/finish.zip >/tmp/jn_finish_t.out 2>&1") != 0)
        return fail("finish.zip not readable after finish()", -1);
    jn_zip_writer_destroy(w);
    printf("finish-before-close OK\n");

    printf("SPIKE_PASS\n");
    return 0;
}
