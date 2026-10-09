package io.jnative.backend.llvm.runtime;

/**
 * FFI helpers: CString bridge and unsafe native memory accessors.
 */
public final class FfiRuntimeEmitter {
    private final StringBuilder sb;

    public FfiRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("""
                define ptr @jnative_ffi_string_to_cstr(ptr %s) {
                entry:
                  %isnull = icmp eq ptr %s, null
                  br i1 %isnull, label %retnull, label %copy
                retnull:
                  ret ptr null
                copy:
                  ; UTF-8 encode; rejects embedded U+0000 (IllegalArgumentException).
                  %buf = call ptr @jnative_string_to_cstr(ptr %s)
                  ret ptr %buf
                }

                define void @jnative_ffi_cstr_free(ptr %p) {
                entry:
                  %isnull = icmp eq ptr %p, null
                  br i1 %isnull, label %done, label %dofree
                dofree:
                  call void @free(ptr %p)
                  br label %done
                done:
                  ret void
                }

                define ptr @jnative_ffi_cstr_to_string(ptr %cstr) {
                entry:
                  %isnull = icmp eq ptr %cstr, null
                  br i1 %isnull, label %retnull, label %copy
                retnull:
                  ret ptr null
                copy:
                  %len64 = call i64 @strlen(ptr %cstr)
                  %len = trunc i64 %len64 to i32
                  %obj = call ptr @jnative_string_from_literal(ptr %cstr, i32 %len, ptr @meta.String)
                  ret ptr %obj
                }

                define ptr @jnative_native_alloc(i64 %size) {
                entry:
                  %p = call ptr @malloc(i64 %size)
                  ret ptr %p
                }

                define void @jnative_native_free(ptr %p) {
                entry:
                  call void @free(ptr %p)
                  ret void
                }

                define i32 @jnative_native_get_i32(ptr %p, i64 %offset) {
                entry:
                  %addr = getelementptr i8, ptr %p, i64 %offset
                  %v = load i32, ptr %addr, align 1
                  ret i32 %v
                }

                define void @jnative_native_put_i32(ptr %p, i64 %offset, i32 %v) {
                entry:
                  %addr = getelementptr i8, ptr %p, i64 %offset
                  store i32 %v, ptr %addr, align 1
                  ret void
                }

                define ptr @jnative_native_get_ptr(ptr %p, i64 %offset) {
                entry:
                  %addr = getelementptr i8, ptr %p, i64 %offset
                  %v = load ptr, ptr %addr, align 8
                  ret ptr %v
                }

                define void @jnative_native_put_ptr(ptr %p, i64 %offset, ptr %v) {
                entry:
                  %addr = getelementptr i8, ptr %p, i64 %offset
                  store ptr %v, ptr %addr, align 8
                  ret void
                }

                """);
    }
}
