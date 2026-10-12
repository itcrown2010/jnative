/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend.llvm.runtime;

/**
 * String runtime (UTF-16 code units). Core logic in {@code jnative_string.c}.
 * Layout: {@code { ClassMeta* @0, i32 utf16Len @8, uint16 data[] @16 }}.
 * @author 张晓达
 */
public final class StringRuntimeEmitter {
    private final StringBuilder sb;

    public StringRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("""
                declare ptr @jnative_string_from_utf8(ptr, ptr, i32)
                declare i32 @jnative_string_length(ptr)
                declare i32 @jnative_string_utf8_length(ptr)
                declare i16 @jnative_string_char_at(ptr, i32)
                declare ptr @jnative_string_concat(ptr, ptr)
                declare i32 @jnative_string_equals(ptr, ptr)
                declare i32 @jnative_string_hashcode(ptr)
                declare i32 @jnative_string_index_of(ptr, ptr)
                declare i32 @jnative_string_index_of_from(ptr, ptr, i32)
                declare i32 @jnative_string_last_index_of(ptr, ptr)
                declare i32 @jnative_string_last_index_of_from(ptr, ptr, i32)
                declare ptr @jnative_string_substring(ptr, i32, i32)
                declare ptr @jnative_string_substring_from(ptr, i32)
                declare i32 @jnative_string_starts_with(ptr, ptr)
                declare i32 @jnative_string_ends_with(ptr, ptr)
                declare i32 @jnative_string_equals_ignore_case(ptr, ptr)
                declare i32 @jnative_string_compare_to(ptr, ptr)
                declare i32 @jnative_string_is_empty(ptr)
                declare i32 @jnative_string_contains(ptr, ptr)
                declare ptr @jnative_string_trim(ptr)
                declare ptr @jnative_string_to_lower(ptr)
                declare ptr @jnative_string_to_upper(ptr)
                declare ptr @jnative_string_replace(ptr, ptr, ptr)
                declare ptr @jnative_string_get_bytes_with_meta(ptr, ptr)
                declare ptr @jnative_string_from_bytes_with_meta(ptr, ptr)
                declare ptr @jnative_string_to_cstr(ptr)
                declare i32 @jnative_string_encode_utf8(ptr, ptr, i32)

                define ptr @jnative_string_from_literal(ptr %bytes, i32 %len, ptr %meta) {
                entry:
                  %r = call ptr @jnative_string_from_utf8(ptr %meta, ptr %bytes, i32 %len)
                  ret ptr %r
                }

                define ptr @jnative_string_get_bytes(ptr %s) {
                entry:
                  %r = call ptr @jnative_string_get_bytes_with_meta(ptr %s, ptr @meta.array.i8)
                  ret ptr %r
                }

                define ptr @jnative_string_from_bytes(ptr %arr) {
                entry:
                  %r = call ptr @jnative_string_from_bytes_with_meta(ptr %arr, ptr @meta.String)
                  ret ptr %r
                }

                define i1 @jnative_string_equals_object(ptr %this, ptr %other) {
                entry:
                  %same = icmp eq ptr %this, %other
                  br i1 %same, label %yes, label %nullchk
                yes:
                  ret i1 true
                nullchk:
                  %on = icmp eq ptr %other, null
                  br i1 %on, label %no, label %meta
                no:
                  ret i1 false
                meta:
                  %om = load ptr, ptr %other, align 8
                  %is = icmp eq ptr %om, @meta.String
                  br i1 %is, label %cmp, label %no
                cmp:
                  %r = call i32 @jnative_string_equals(ptr %this, ptr %other)
                  %b = icmp ne i32 %r, 0
                  ret i1 %b
                }

                define ptr @jnative_string_value_of_i32(i32 %value) {
                entry:
                  %buf = alloca [16 x i8], align 1
                  %p = getelementptr [16 x i8], ptr %buf, i64 0, i64 0
                  %neg = icmp slt i32 %value, 0
                  br i1 %neg, label %negpath, label %pospath
                negpath:
                  store i8 45, ptr %p, align 1
                  %abs = sub i32 0, %value
                  %p2 = getelementptr i8, ptr %p, i64 1
                  %n1 = call i32 @jnative_string_u32_to_dec(i32 %abs, ptr %p2)
                  %len1 = add i32 %n1, 1
                  %r1 = call ptr @jnative_string_from_literal(ptr %p, i32 %len1, ptr @meta.String)
                  ret ptr %r1
                pospath:
                  %n2 = call i32 @jnative_string_u32_to_dec(i32 %value, ptr %p)
                  %r2 = call ptr @jnative_string_from_literal(ptr %p, i32 %n2, ptr @meta.String)
                  ret ptr %r2
                }

                define ptr @jnative_string_value_of_i64(i64 %value) {
                entry:
                  %buf = alloca [24 x i8], align 1
                  %p = getelementptr [24 x i8], ptr %buf, i64 0, i64 0
                  %neg = icmp slt i64 %value, 0
                  br i1 %neg, label %negpath, label %pospath
                negpath:
                  store i8 45, ptr %p, align 1
                  %abs = sub i64 0, %value
                  %p2 = getelementptr i8, ptr %p, i64 1
                  %n1 = call i32 @jnative_string_u64_to_dec(i64 %abs, ptr %p2)
                  %len1 = add i32 %n1, 1
                  %r1 = call ptr @jnative_string_from_literal(ptr %p, i32 %len1, ptr @meta.String)
                  ret ptr %r1
                pospath:
                  %n2 = call i32 @jnative_string_u64_to_dec(i64 %value, ptr %p)
                  %r2 = call ptr @jnative_string_from_literal(ptr %p, i32 %n2, ptr @meta.String)
                  ret ptr %r2
                }

                define i32 @jnative_string_u64_to_dec(i64 %value, ptr %out) {
                entry:
                  %zero = icmp eq i64 %value, 0
                  br i1 %zero, label %z, label %digits
                z:
                  store i8 48, ptr %out, align 1
                  ret i32 1
                digits:
                  %tmp = alloca [24 x i8], align 1
                  br label %loop
                loop:
                  %v = phi i64 [ %value, %digits ], [ %vnext, %body ]
                  %i = phi i32 [ 0, %digits ], [ %inext, %body ]
                  %done = icmp eq i64 %v, 0
                  br i1 %done, label %reverse, label %body
                body:
                  %d = urem i64 %v, 10
                  %di = trunc i64 %d to i32
                  %ch = add i32 %di, 48
                  %ch8 = trunc i32 %ch to i8
                  %tp = getelementptr [24 x i8], ptr %tmp, i64 0, i32 %i
                  store i8 %ch8, ptr %tp, align 1
                  %vnext = udiv i64 %v, 10
                  %inext = add i32 %i, 1
                  br label %loop
                reverse:
                  br label %rloop
                rloop:
                  %j = phi i32 [ 0, %reverse ], [ %jnext, %rcopy ]
                  %done2 = icmp eq i32 %j, %i
                  br i1 %done2, label %done3, label %rcopy
                rcopy:
                  %srcIdx = sub i32 %i, %j
                  %srcIdx1 = sub i32 %srcIdx, 1
                  %sp = getelementptr [24 x i8], ptr %tmp, i64 0, i32 %srcIdx1
                  %c = load i8, ptr %sp, align 1
                  %dp = getelementptr i8, ptr %out, i32 %j
                  store i8 %c, ptr %dp, align 1
                  %jnext = add i32 %j, 1
                  br label %rloop
                done3:
                  ret i32 %i
                }

                define i32 @jnative_string_u32_to_dec(i32 %value, ptr %out) {
                entry:
                  %z64 = zext i32 %value to i64
                  %r = call i32 @jnative_string_u64_to_dec(i64 %z64, ptr %out)
                  ret i32 %r
                }

                define ptr @jnative_string_split(ptr %s, ptr %delim) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  call void @jnative_null_check(ptr %delim)
                  %dlen = call i32 @jnative_string_length(ptr %delim)
                  %slen = call i32 @jnative_string_length(ptr %s)
                  %dempty = icmp eq i32 %dlen, 0
                  br i1 %dempty, label %single, label %count
                single:
                  %one = call ptr @jnative_array_alloc(i64 8, i32 1, ptr @meta.array.CString)
                  %ep0 = call ptr @jnative_array_elem_ptr(ptr %one, i32 0, i64 8)
                  store ptr %s, ptr %ep0, align 8
                  ret ptr %one
                count:
                  br label %scan
                scan:
                  %i = phi i32 [ 0, %count ], [ %i2, %scannext ]
                  %parts = phi i32 [ 1, %count ], [ %parts2, %scannext ]
                  %lim = sub i32 %slen, %dlen
                  %done = icmp sgt i32 %i, %lim
                  br i1 %done, label %alloc, label %try
                try:
                  %end = add i32 %i, %dlen
                  %cand = call ptr @jnative_string_substring(ptr %s, i32 %i, i32 %end)
                  %m = call i32 @jnative_string_equals(ptr %cand, ptr %delim)
                  %hit = icmp ne i32 %m, 0
                  br i1 %hit, label %hitL, label %miss
                hitL:
                  %parts1 = add i32 %parts, 1
                  %i1 = add i32 %i, %dlen
                  br label %scannext
                miss:
                  %i1m = add i32 %i, 1
                  br label %scannext
                scannext:
                  %i2 = phi i32 [ %i1, %hitL ], [ %i1m, %miss ]
                  %parts2 = phi i32 [ %parts1, %hitL ], [ %parts, %miss ]
                  br label %scan
                alloc:
                  %arr = call ptr @jnative_array_alloc(i64 8, i32 %parts, ptr @meta.array.CString)
                  br label %build
                build:
                  %si = phi i32 [ 0, %alloc ], [ %siN, %bcont ]
                  %pi = phi i32 [ 0, %alloc ], [ %piN, %bcont ]
                  %start = phi i32 [ 0, %alloc ], [ %startN, %bcont ]
                  %filled = icmp sge i32 %pi, %parts
                  br i1 %filled, label %doneB, label %bbody
                bbody:
                  %atEnd = icmp sge i32 %si, %slen
                  br i1 %atEnd, label %emitTail, label %bcheck
                bcheck:
                  %remain = sub i32 %slen, %si
                  %can = icmp sge i32 %remain, %dlen
                  br i1 %can, label %bmatch, label %badv
                bmatch:
                  %end2 = add i32 %si, %dlen
                  %cand2 = call ptr @jnative_string_substring(ptr %s, i32 %si, i32 %end2)
                  %bm = call i32 @jnative_string_equals(ptr %cand2, ptr %delim)
                  %bhit = icmp ne i32 %bm, 0
                  br i1 %bhit, label %bemit, label %badv
                bemit:
                  %piece2 = call ptr @jnative_string_substring(ptr %s, i32 %start, i32 %si)
                  %ep = call ptr @jnative_array_elem_ptr(ptr %arr, i32 %pi, i64 8)
                  store ptr %piece2, ptr %ep, align 8
                  %siR = add i32 %si, %dlen
                  %piR = add i32 %pi, 1
                  br label %bcont
                badv:
                  %siC = add i32 %si, 1
                  br label %bcont2
                emitTail:
                  %pieceT = call ptr @jnative_string_substring(ptr %s, i32 %start, i32 %slen)
                  %epT = call ptr @jnative_array_elem_ptr(ptr %arr, i32 %pi, i64 8)
                  store ptr %pieceT, ptr %epT, align 8
                  br label %doneB
                bcont2:
                  br label %bcont
                bcont:
                  %siN = phi i32 [ %siR, %bemit ], [ %siC, %bcont2 ]
                  %piN = phi i32 [ %piR, %bemit ], [ %pi, %bcont2 ]
                  %startN = phi i32 [ %siR, %bemit ], [ %start, %bcont2 ]
                  br label %build
                doneB:
                  ret ptr %arr
                }

                define ptr @jnative_string_join(ptr %delim, ptr %arr) {
                entry:
                  call void @jnative_null_check(ptr %delim)
                  call void @jnative_null_check(ptr %arr)
                  %n = call i32 @jnative_array_length(ptr %arr)
                  %zero = icmp eq i32 %n, 0
                  br i1 %zero, label %empty, label %loopInit
                empty:
                  %e = call ptr @jnative_string_from_literal(ptr @.jn.emptystr, i32 0, ptr @meta.String)
                  ret ptr %e
                loopInit:
                  %acc0 = call ptr @jnative_string_from_literal(ptr @.jn.emptystr, i32 0, ptr @meta.String)
                  br label %loop
                loop:
                  %i = phi i32 [ 0, %loopInit ], [ %inext, %next ]
                  %acc = phi ptr [ %acc0, %loopInit ], [ %accN, %next ]
                  %done = icmp sge i32 %i, %n
                  br i1 %done, label %ret, label %body
                body:
                  %needD = icmp sgt i32 %i, 0
                  br i1 %needD, label %addD, label %addE
                addD:
                  %accD = call ptr @jnative_string_concat(ptr %acc, ptr %delim)
                  br label %addE2
                addE:
                  br label %addE2
                addE2:
                  %acc1 = phi ptr [ %accD, %addD ], [ %acc, %addE ]
                  %ep = call ptr @jnative_array_elem_ptr(ptr %arr, i32 %i, i64 8)
                  %el = load ptr, ptr %ep, align 8
                  %isnull = icmp eq ptr %el, null
                  br i1 %isnull, label %usenull, label %useel
                usenull:
                  %ns = call ptr @jnative_string_from_literal(ptr @.jn.nullstr, i32 4, ptr @meta.String)
                  br label %cat
                useel:
                  br label %cat
                cat:
                  %piece = phi ptr [ %ns, %usenull ], [ %el, %useel ]
                  %accN = call ptr @jnative_string_concat(ptr %acc1, ptr %piece)
                  %inext = add i32 %i, 1
                  br label %next
                next:
                  br label %loop
                ret:
                  ret ptr %acc
                }

                """);
        emitCString(".jn.emptystr", "");
        emitCString(".jn.nullstr", "null");
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
