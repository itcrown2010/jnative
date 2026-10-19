/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend.llvm.runtime;

/** println helpers. String objects store UTF-8 bytes inline at +16.
 * @author 张晓达
 */
public final class IoRuntimeEmitter {
    private final StringBuilder sb;

    public IoRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("""
                define void @jnative_println_i32(i32 %v) {
                entry:
                  call i32 (ptr, ...) @printf(ptr @.fmt.i32, i32 %v)
                  ret void
                }

                define void @jnative_println_i64(i64 %v) {
                entry:
                  call i32 (ptr, ...) @printf(ptr @.fmt.i64, i64 %v)
                  ret void
                }

                define void @jnative_println_f64(double %v) {
                entry:
                  %buf = alloca [48 x i8], align 1
                  %p = getelementptr [48 x i8], ptr %buf, i64 0, i64 0
                  %n = call i32 @jnative_fp_to_chars(double %v, i32 0, ptr %p)
                  call i32 @puts(ptr %p)
                  ret void
                }

                define void @jnative_println_f32(float %v) {
                entry:
                  %buf = alloca [48 x i8], align 1
                  %p = getelementptr [48 x i8], ptr %buf, i64 0, i64 0
                  %d = fpext float %v to double
                  %n = call i32 @jnative_fp_to_chars(double %d, i32 1, ptr %p)
                  call i32 @puts(ptr %p)
                  ret void
                }

                define void @jnative_println_bool(i1 %v) {
                entry:
                  br i1 %v, label %t, label %f
                t:
                  call i32 @puts(ptr @.fmt.bool.true)
                  ret void
                f:
                  call i32 @puts(ptr @.fmt.bool.false)
                  ret void
                }

                define void @jnative_println_char(i16 %v) {
                entry:
                  ; Goes through String so non-ASCII UTF-16 units are written as UTF-8.
                  %s = call ptr @jnative_string_value_of_char(i16 %v)
                  call void @jnative_println_string(ptr %s)
                  ret void
                }

                define void @jnative_println_string(ptr %s) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  %ulen = call i32 @jnative_string_utf8_length(ptr %s)
                  %len64 = sext i32 %ulen to i64
                  %n64 = add i64 %len64, 1
                  %buf = call ptr @malloc(i64 %n64)
                  %enc = call i32 @jnative_string_encode_utf8(ptr %s, ptr %buf, i32 %ulen)
                  %nul = getelementptr i8, ptr %buf, i32 %ulen
                  store i8 0, ptr %nul, align 1
                  ; puts stops at embedded NUL; use File/TCP APIs for binary text.
                  call i32 @puts(ptr %buf)
                  call void @free(ptr %buf)
                  ret void
                }

                """);
    }
}
