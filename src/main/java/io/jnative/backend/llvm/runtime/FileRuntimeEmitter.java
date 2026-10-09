package io.jnative.backend.llvm.runtime;

/**
 * File I/O runtime (0.8.1). Public File API lowers here; libc details stay in this layer.
 *
 * <p>Uses POSIX/libc ({@code fopen}/{@code fread}/…) for the host platform. Future Windows
 * backends can replace this emitter without changing the File surface API.
 *
 * <p>Contract: temporary native buffers and {@code FILE*} are always released on success and error.
 * Results are GC-managed String / byte[].
 */
public final class FileRuntimeEmitter {
    private final StringBuilder sb;

    public FileRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        emitHelpers();
        emitExists();
        emitSize();
        emitDelete();
        emitReadBytes();
        emitReadString();
        emitWriteBytes();
        emitWriteString();
        emitArgv();
    }

    private void emitHelpers() {
        sb.append("""
                define void @jnative_throw_io_message(ptr %msg) {
                entry:
                  %obj = call ptr @jnative_alloc_zeroed(i64 24)
                  store ptr @meta.IOException, ptr %obj, align 8
                  %mp = getelementptr i8, ptr %obj, i64 8
                  store ptr %msg, ptr %mp, align 8
                  call void @jnative_exception_throw(ptr %obj)
                  ret void
                }

                define void @jnative_throw_file_not_found(ptr %msg) {
                entry:
                  %obj = call ptr @jnative_alloc_zeroed(i64 24)
                  store ptr @meta.FileNotFoundException, ptr %obj, align 8
                  %mp = getelementptr i8, ptr %obj, i64 8
                  store ptr %msg, ptr %mp, align 8
                  call void @jnative_exception_throw(ptr %obj)
                  ret void
                }

                define ptr @jnative_file_path_cstr(ptr %path) {
                entry:
                  %p = call ptr @jnative_ffi_string_to_cstr(ptr %path)
                  ret ptr %p
                }

                define ptr @jnative_file_msg2(ptr %prefix, ptr %path) {
                entry:
                  %m = call ptr @jnative_string_concat(ptr %prefix, ptr %path)
                  ret ptr %m
                }

                """);
        // Message prefixes as UTF-8 constants
        emitCString(".jn.io.notfound", "File not found: ");
        emitCString(".jn.io.readfail", "Failed to read: ");
        emitCString(".jn.io.writefail", "Failed to write: ");
        emitCString(".jn.io.huge", "File too large: ");
        emitCString(".jn.io.deletefail", "Failed to delete: ");
        emitCString(".jn.io.sizefail", "Failed to get size: ");
        sb.append('\n');
    }

    private void emitExists() {
        sb.append("""
                define i1 @jnative_file_exists(ptr %path) {
                entry:
                  %cstr = call ptr @jnative_file_path_cstr(ptr %path)
                  %isnull = icmp eq ptr %cstr, null
                  br i1 %isnull, label %retfalse, label %check
                check:
                  call void @jn_enter_native()
                  %f = call ptr @fopen(ptr %cstr, ptr @.jn.io.mode.rb)
                  call void @jn_leave_native()
                  call void @jnative_ffi_cstr_free(ptr %cstr)
                  %ok = icmp ne ptr %f, null
                  br i1 %ok, label %close, label %retfalse
                close:
                  %ignored = call i32 @fclose(ptr %f)
                  ret i1 true
                retfalse:
                  ret i1 false
                }

                """);
    }

    private void emitSize() {
        sb.append("""
                define i64 @jnative_file_size(ptr %path) {
                entry:
                  %cstr = call ptr @jnative_file_path_cstr(ptr %path)
                  call void @jn_enter_native()
                  %f = call ptr @fopen(ptr %cstr, ptr @.jn.io.mode.rb)
                  call void @jn_leave_native()
                  call void @jnative_ffi_cstr_free(ptr %cstr)
                  %nullf = icmp eq ptr %f, null
                  br i1 %nullf, label %missing, label %seek
                missing:
                  %pref = call ptr @jnative_string_from_literal(ptr @.jn.io.notfound, i32 16, ptr @meta.String)
                  %msg = call ptr @jnative_file_msg2(ptr %pref, ptr %path)
                  call void @jnative_throw_file_not_found(ptr %msg)
                  ret i64 -1
                seek:
                  %s1 = call i32 @fseek(ptr %f, i64 0, i32 2)
                  %sz = call i64 @ftell(ptr %f)
                  %s2 = call i32 @fseek(ptr %f, i64 0, i32 0)
                  %c = call i32 @fclose(ptr %f)
                  %bad = icmp slt i64 %sz, 0
                  br i1 %bad, label %fail, label %ok
                fail:
                  %pref2 = call ptr @jnative_string_from_literal(ptr @.jn.io.sizefail, i32 19, ptr @meta.String)
                  %msg2 = call ptr @jnative_file_msg2(ptr %pref2, ptr %path)
                  call void @jnative_throw_io_message(ptr %msg2)
                  ret i64 -1
                ok:
                  ret i64 %sz
                }

                """);
    }

    private void emitDelete() {
        sb.append("""
                define i1 @jnative_file_delete(ptr %path) {
                entry:
                  %cstr = call ptr @jnative_file_path_cstr(ptr %path)
                  call void @jn_enter_native()
                  %rc = call i32 @remove(ptr %cstr)
                  call void @jn_leave_native()
                  call void @jnative_ffi_cstr_free(ptr %cstr)
                  %ok = icmp eq i32 %rc, 0
                  ret i1 %ok
                }

                """);
    }

    private void emitReadBytes() {
        // Returns managed byte[] (meta.array.i8). Sets pending exception on failure.
        sb.append("""
                define ptr @jnative_file_read_bytes(ptr %path) {
                entry:
                  %cstr = call ptr @jnative_file_path_cstr(ptr %path)
                  call void @jn_enter_native()
                  %f = call ptr @fopen(ptr %cstr, ptr @.jn.io.mode.rb)
                  call void @jn_leave_native()
                  call void @jnative_ffi_cstr_free(ptr %cstr)
                  %nullf = icmp eq ptr %f, null
                  br i1 %nullf, label %missing, label %seek
                missing:
                  %pref = call ptr @jnative_string_from_literal(ptr @.jn.io.notfound, i32 16, ptr @meta.String)
                  %msg = call ptr @jnative_file_msg2(ptr %pref, ptr %path)
                  call void @jnative_throw_file_not_found(ptr %msg)
                  ret ptr null
                seek:
                  %s1 = call i32 @fseek(ptr %f, i64 0, i32 2)
                  %sz = call i64 @ftell(ptr %f)
                  %s2 = call i32 @fseek(ptr %f, i64 0, i32 0)
                  %badsize = icmp slt i64 %sz, 0
                  br i1 %badsize, label %iofail, label %checkhuge
                checkhuge:
                  %tooBig = icmp ugt i64 %sz, 2147483647
                  br i1 %tooBig, label %huge, label %alloc
                huge:
                  %c0 = call i32 @fclose(ptr %f)
                  %prefH = call ptr @jnative_string_from_literal(ptr @.jn.io.huge, i32 15, ptr @meta.String)
                  %msgH = call ptr @jnative_file_msg2(ptr %prefH, ptr %path)
                  call void @jnative_throw_io_message(ptr %msgH)
                  ret ptr null
                iofail:
                  %c1 = call i32 @fclose(ptr %f)
                  %prefR = call ptr @jnative_string_from_literal(ptr @.jn.io.readfail, i32 16, ptr @meta.String)
                  %msgR = call ptr @jnative_file_msg2(ptr %prefR, ptr %path)
                  call void @jnative_throw_io_message(ptr %msgR)
                  ret ptr null
                alloc:
                  %len32 = trunc i64 %sz to i32
                  %arr = call ptr @jnative_array_alloc(i64 1, i32 %len32, ptr @meta.array.i8)
                  %pending1 = call i1 @jnative_exception_pending()
                  br i1 %pending1, label %arrfail, label %read
                arrfail:
                  %c2 = call i32 @fclose(ptr %f)
                  ret ptr null
                read:
                  %empty = icmp eq i64 %sz, 0
                  br i1 %empty, label %doneempty, label %doread
                doneempty:
                  %c3 = call i32 @fclose(ptr %f)
                  ret ptr %arr
                doread:
                  %elem = getelementptr i8, ptr %arr, i64 16
                  call void @jn_enter_native()
                  %nread = call i64 @fread(ptr %elem, i64 1, i64 %sz, ptr %f)
                  call void @jn_leave_native()
                  %c4 = call i32 @fclose(ptr %f)
                  %ok = icmp eq i64 %nread, %sz
                  br i1 %ok, label %okret, label %readfail
                readfail:
                  %prefR2 = call ptr @jnative_string_from_literal(ptr @.jn.io.readfail, i32 16, ptr @meta.String)
                  %msgR2 = call ptr @jnative_file_msg2(ptr %prefR2, ptr %path)
                  call void @jnative_throw_io_message(ptr %msgR2)
                  ret ptr null
                okret:
                  ret ptr %arr
                }

                """);
    }

    private void emitReadString() {
        // Read into a temporary malloc buffer, then copy into a GC String.
        // Do NOT stage through a managed byte[] — GC may collect it during string alloc.
        sb.append("""
                define ptr @jnative_file_read_string(ptr %path) {
                entry:
                  %cstr = call ptr @jnative_file_path_cstr(ptr %path)
                  call void @jn_enter_native()
                  %f = call ptr @fopen(ptr %cstr, ptr @.jn.io.mode.rb)
                  call void @jn_leave_native()
                  call void @jnative_ffi_cstr_free(ptr %cstr)
                  %nullf = icmp eq ptr %f, null
                  br i1 %nullf, label %missing, label %seek
                missing:
                  %pref = call ptr @jnative_string_from_literal(ptr @.jn.io.notfound, i32 16, ptr @meta.String)
                  %msg = call ptr @jnative_file_msg2(ptr %pref, ptr %path)
                  call void @jnative_throw_file_not_found(ptr %msg)
                  ret ptr null
                seek:
                  %s1 = call i32 @fseek(ptr %f, i64 0, i32 2)
                  %sz = call i64 @ftell(ptr %f)
                  %s2 = call i32 @fseek(ptr %f, i64 0, i32 0)
                  %badsize = icmp slt i64 %sz, 0
                  br i1 %badsize, label %iofail, label %checkhuge
                checkhuge:
                  %tooBig = icmp ugt i64 %sz, 2147483647
                  br i1 %tooBig, label %huge, label %alloc
                huge:
                  %c0 = call i32 @fclose(ptr %f)
                  %prefH = call ptr @jnative_string_from_literal(ptr @.jn.io.huge, i32 15, ptr @meta.String)
                  %msgH = call ptr @jnative_file_msg2(ptr %prefH, ptr %path)
                  call void @jnative_throw_io_message(ptr %msgH)
                  ret ptr null
                iofail:
                  %c1 = call i32 @fclose(ptr %f)
                  %prefR = call ptr @jnative_string_from_literal(ptr @.jn.io.readfail, i32 16, ptr @meta.String)
                  %msgR = call ptr @jnative_file_msg2(ptr %prefR, ptr %path)
                  call void @jnative_throw_io_message(ptr %msgR)
                  ret ptr null
                alloc:
                  %n = add i64 %sz, 1
                  %buf = call ptr @malloc(i64 %n)
                  %nullb = icmp eq ptr %buf, null
                  br i1 %nullb, label %oom, label %read
                oom:
                  %c2 = call i32 @fclose(ptr %f)
                  %prefO = call ptr @jnative_string_from_literal(ptr @.jn.io.readfail, i32 16, ptr @meta.String)
                  %msgO = call ptr @jnative_file_msg2(ptr %prefO, ptr %path)
                  call void @jnative_throw_io_message(ptr %msgO)
                  ret ptr null
                read:
                  %empty = icmp eq i64 %sz, 0
                  br i1 %empty, label %mkempty, label %doread
                mkempty:
                  %c3 = call i32 @fclose(ptr %f)
                  store i8 0, ptr %buf, align 1
                  %se = call ptr @jnative_string_from_literal(ptr %buf, i32 0, ptr @meta.String)
                  call void @free(ptr %buf)
                  ret ptr %se
                doread:
                  call void @jn_enter_native()
                  %nread = call i64 @fread(ptr %buf, i64 1, i64 %sz, ptr %f)
                  call void @jn_leave_native()
                  %c4 = call i32 @fclose(ptr %f)
                  %ok = icmp eq i64 %nread, %sz
                  br i1 %ok, label %mk, label %readfail
                readfail:
                  call void @free(ptr %buf)
                  %prefR2 = call ptr @jnative_string_from_literal(ptr @.jn.io.readfail, i32 16, ptr @meta.String)
                  %msgR2 = call ptr @jnative_file_msg2(ptr %prefR2, ptr %path)
                  call void @jnative_throw_io_message(ptr %msgR2)
                  ret ptr null
                mk:
                  %nul = getelementptr i8, ptr %buf, i64 %sz
                  store i8 0, ptr %nul, align 1
                  %len32 = trunc i64 %sz to i32
                  %s = call ptr @jnative_string_from_literal(ptr %buf, i32 %len32, ptr @meta.String)
                  call void @free(ptr %buf)
                  ret ptr %s
                }

                """);
    }

    private void emitWriteBytes() {
        sb.append("""
                define void @jnative_file_write_bytes(ptr %path, ptr %arr) {
                entry:
                  call void @jnative_null_check(ptr %arr)
                  %cstr = call ptr @jnative_file_path_cstr(ptr %path)
                  call void @jn_enter_native()
                  %f = call ptr @fopen(ptr %cstr, ptr @.jn.io.mode.wb)
                  call void @jn_leave_native()
                  call void @jnative_ffi_cstr_free(ptr %cstr)
                  %nullf = icmp eq ptr %f, null
                  br i1 %nullf, label %fail, label %write
                fail:
                  %pref = call ptr @jnative_string_from_literal(ptr @.jn.io.writefail, i32 17, ptr @meta.String)
                  %msg = call ptr @jnative_file_msg2(ptr %pref, ptr %path)
                  call void @jnative_throw_io_message(ptr %msg)
                  ret void
                write:
                  %lenptr = getelementptr i8, ptr %arr, i64 8
                  %len = load i32, ptr %lenptr, align 4
                  %len64 = sext i32 %len to i64
                  %data = getelementptr i8, ptr %arr, i64 16
                  %empty = icmp eq i64 %len64, 0
                  br i1 %empty, label %closeok, label %dowrite
                dowrite:
                  call void @jn_enter_native()
                  %n = call i64 @fwrite(ptr %data, i64 1, i64 %len64, ptr %f)
                  call void @jn_leave_native()
                  %ok = icmp eq i64 %n, %len64
                  br i1 %ok, label %closeok, label %wfail
                wfail:
                  %c0 = call i32 @fclose(ptr %f)
                  %pref2 = call ptr @jnative_string_from_literal(ptr @.jn.io.writefail, i32 17, ptr @meta.String)
                  %msg2 = call ptr @jnative_file_msg2(ptr %pref2, ptr %path)
                  call void @jnative_throw_io_message(ptr %msg2)
                  ret void
                closeok:
                  %c1 = call i32 @fclose(ptr %f)
                  ret void
                }

                """);
    }

    private void emitWriteString() {
        sb.append("""
                define void @jnative_file_write_string(ptr %path, ptr %text) {
                entry:
                  call void @jnative_null_check(ptr %text)
                  %cstr = call ptr @jnative_file_path_cstr(ptr %path)
                  call void @jn_enter_native()
                  %f = call ptr @fopen(ptr %cstr, ptr @.jn.io.mode.wb)
                  call void @jn_leave_native()
                  call void @jnative_ffi_cstr_free(ptr %cstr)
                  %nullf = icmp eq ptr %f, null
                  br i1 %nullf, label %fail, label %write
                fail:
                  %pref = call ptr @jnative_string_from_literal(ptr @.jn.io.writefail, i32 17, ptr @meta.String)
                  %msg = call ptr @jnative_file_msg2(ptr %pref, ptr %path)
                  call void @jnative_throw_io_message(ptr %msg)
                  ret void
                write:
                  %ulen = call i32 @jnative_string_utf8_length(ptr %text)
                  %len64 = sext i32 %ulen to i64
                  %empty = icmp eq i64 %len64, 0
                  br i1 %empty, label %closeok, label %dowrite
                dowrite:
                  %buf = call ptr @malloc(i64 %len64)
                  %enc = call i32 @jnative_string_encode_utf8(ptr %text, ptr %buf, i32 %ulen)
                  call void @jn_enter_native()
                  %n = call i64 @fwrite(ptr %buf, i64 1, i64 %len64, ptr %f)
                  call void @jn_leave_native()
                  call void @free(ptr %buf)
                  %ok = icmp eq i64 %n, %len64
                  br i1 %ok, label %closeok, label %wfail
                wfail:
                  %c0 = call i32 @fclose(ptr %f)
                  %pref2 = call ptr @jnative_string_from_literal(ptr @.jn.io.writefail, i32 17, ptr @meta.String)
                  %msg2 = call ptr @jnative_file_msg2(ptr %pref2, ptr %path)
                  call void @jnative_throw_io_message(ptr %msg2)
                  ret void
                closeok:
                  %c1 = call i32 @fclose(ptr %f)
                  ret void
                }

                """);
    }

    private void emitArgv() {
        // Build managed String[] from C argc/argv. Skips argv[0] program name? No — Java includes all;
        // actually Java main args exclude program name. Spec: convert argc/argv to String[].
        // Standard Java: args does NOT include program name. So we use argc-1 elements from argv+1.
        sb.append("""
                define ptr @jnative_argv_to_string_array(i32 %argc, ptr %argv) {
                entry:
                  %lt1 = icmp slt i32 %argc, 1
                  br i1 %lt1, label %empty, label %mk
                empty:
                  %e = call ptr @jnative_array_alloc(i64 8, i32 0, ptr @meta.array.CString)
                  ret ptr %e
                mk:
                  %n = sub i32 %argc, 1
                  %arr = call ptr @jnative_array_alloc(i64 8, i32 %n, ptr @meta.array.CString)
                  %i = alloca i32, align 4
                  store i32 0, ptr %i, align 4
                  br label %loop
                loop:
                  %iv = load i32, ptr %i, align 4
                  %done = icmp sge i32 %iv, %n
                  br i1 %done, label %end, label %body
                body:
                  %idx1 = add i32 %iv, 1
                  %idx164 = sext i32 %idx1 to i64
                  %argp = getelementptr ptr, ptr %argv, i64 %idx164
                  %cstr = load ptr, ptr %argp, align 8
                  %s = call ptr @jnative_ffi_cstr_to_string(ptr %cstr)
                  %slot = call ptr @jnative_array_elem_ptr(ptr %arr, i32 %iv, i64 8)
                  store ptr %s, ptr %slot, align 8
                  %next = add i32 %iv, 1
                  store i32 %next, ptr %i, align 4
                  br label %loop
                end:
                  ret ptr %arr
                }

                """);
        emitCString(".jn.io.mode.rb", "rb");
        emitCString(".jn.io.mode.wb", "wb");
        sb.append('\n');
    }

    private void emitCString(String name, String content) {
        byte[] bytes = content.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int len = bytes.length + 1;
        sb.append('@').append(name).append(" = private unnamed_addr constant [")
                .append(len).append(" x i8] c\"");
        for (byte b : bytes) {
            int v = b & 0xff;
            if (v >= 32 && v < 127 && v != '"' && v != '\\') {
                sb.append((char) v);
            } else {
                sb.append(String.format("\\%02X", v));
            }
        }
        sb.append("\\00\", align 1\n");
    }
}
