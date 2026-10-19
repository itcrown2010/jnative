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
                declare ptr @jnative_string_repeat(ptr, i32)
                declare i32 @jnative_string_is_blank(ptr)
                declare void @jnative_string_copy_chars(ptr, ptr)
                declare ptr @jnative_string_from_char_array(ptr, ptr)
                declare i32 @jnative_string_compare_to_ignore_case(ptr, ptr)

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

                define ptr @jnative_string_from_chars(ptr %arr) {
                entry:
                  %r = call ptr @jnative_string_from_char_array(ptr %arr, ptr @meta.String)
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

                ; String.valueOf(char) / "" + 'c': one UTF-16 code unit -> UTF-8 (1..3 bytes) -> String.
                define ptr @jnative_string_value_of_char(i16 %c) {
                entry:
                  %buf = alloca [4 x i8], align 1
                  %p = getelementptr [4 x i8], ptr %buf, i64 0, i64 0
                  %v = zext i16 %c to i32
                  %lt80 = icmp ult i32 %v, 128
                  br i1 %lt80, label %one, label %t2
                one:
                  %o0 = trunc i32 %v to i8
                  store i8 %o0, ptr %p, align 1
                  %r1 = call ptr @jnative_string_from_literal(ptr %p, i32 1, ptr @meta.String)
                  ret ptr %r1
                t2:
                  %lt800 = icmp ult i32 %v, 2048
                  br i1 %lt800, label %two, label %three
                two:
                  %th = lshr i32 %v, 6
                  %th1 = or i32 %th, 192
                  %tb0 = trunc i32 %th1 to i8
                  store i8 %tb0, ptr %p, align 1
                  %tl = and i32 %v, 63
                  %tl1 = or i32 %tl, 128
                  %tb1 = trunc i32 %tl1 to i8
                  %tp1 = getelementptr i8, ptr %p, i64 1
                  store i8 %tb1, ptr %tp1, align 1
                  %r2 = call ptr @jnative_string_from_literal(ptr %p, i32 2, ptr @meta.String)
                  ret ptr %r2
                three:
                  %ha = lshr i32 %v, 12
                  %ha1 = or i32 %ha, 224
                  %hb0 = trunc i32 %ha1 to i8
                  store i8 %hb0, ptr %p, align 1
                  %hm = lshr i32 %v, 6
                  %hm1 = and i32 %hm, 63
                  %hm2 = or i32 %hm1, 128
                  %hb1 = trunc i32 %hm2 to i8
                  %hp1 = getelementptr i8, ptr %p, i64 1
                  store i8 %hb1, ptr %hp1, align 1
                  %hl = and i32 %v, 63
                  %hl1 = or i32 %hl, 128
                  %hb2 = trunc i32 %hl1 to i8
                  %hp2 = getelementptr i8, ptr %p, i64 2
                  store i8 %hb2, ptr %hp2, align 1
                  %r3 = call ptr @jnative_string_from_literal(ptr %p, i32 3, ptr @meta.String)
                  ret ptr %r3
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
                  %sSl = alloca ptr, align 8
                  %sDl = alloca ptr, align 8
                  %sAl = alloca ptr, align 8
                  store ptr %s, ptr %sSl, align 8
                  store ptr %delim, ptr %sDl, align 8
                  store ptr null, ptr %sAl, align 8
                  %sfr = call ptr @jnative_gc_frame_push(i32 3)
                  call void @jnative_gc_frame_set_root(ptr %sfr, i32 0, ptr %sSl)
                  call void @jnative_gc_frame_set_root(ptr %sfr, i32 1, ptr %sDl)
                  call void @jnative_gc_frame_set_root(ptr %sfr, i32 2, ptr %sAl)
                  br label %scan
                scan:
                  %i = phi i32 [ 0, %count ], [ %i2, %scannext ]
                  %parts = phi i32 [ 1, %count ], [ %parts2, %scannext ]
                  %lim = sub i32 %slen, %dlen
                  %done = icmp sgt i32 %i, %lim
                  br i1 %done, label %alloc, label %try
                try:
                  %end = add i32 %i, %dlen
                  %sc1 = load ptr, ptr %sSl, align 8
                  %cand = call ptr @jnative_string_substring(ptr %sc1, i32 %i, i32 %end)
                  %dc1 = load ptr, ptr %sDl, align 8
                  %m = call i32 @jnative_string_equals(ptr %cand, ptr %dc1)
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
                  %arr0 = call ptr @jnative_array_alloc(i64 8, i32 %parts, ptr @meta.array.CString)
                  store ptr %arr0, ptr %sAl, align 8
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
                  %sc2 = load ptr, ptr %sSl, align 8
                  %cand2 = call ptr @jnative_string_substring(ptr %sc2, i32 %si, i32 %end2)
                  %dc2 = load ptr, ptr %sDl, align 8
                  %bm = call i32 @jnative_string_equals(ptr %cand2, ptr %dc2)
                  %bhit = icmp ne i32 %bm, 0
                  br i1 %bhit, label %bemit, label %badv
                bemit:
                  %sc3 = load ptr, ptr %sSl, align 8
                  %piece2 = call ptr @jnative_string_substring(ptr %sc3, i32 %start, i32 %si)
                  %arr1 = load ptr, ptr %sAl, align 8
                  %ep = call ptr @jnative_array_elem_ptr(ptr %arr1, i32 %pi, i64 8)
                  store ptr %piece2, ptr %ep, align 8
                  %siR = add i32 %si, %dlen
                  %piR = add i32 %pi, 1
                  br label %bcont
                badv:
                  %siC = add i32 %si, 1
                  br label %bcont2
                emitTail:
                  %sc4 = load ptr, ptr %sSl, align 8
                  %pieceT = call ptr @jnative_string_substring(ptr %sc4, i32 %start, i32 %slen)
                  %arr2 = load ptr, ptr %sAl, align 8
                  %epT = call ptr @jnative_array_elem_ptr(ptr %arr2, i32 %pi, i64 8)
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
                  %resArr = load ptr, ptr %sAl, align 8
                  call void @jnative_gc_frame_pop()
                  ret ptr %resArr
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
                  %jD = alloca ptr, align 8
                  %jA = alloca ptr, align 8
                  %jC = alloca ptr, align 8
                  store ptr %delim, ptr %jD, align 8
                  store ptr %arr, ptr %jA, align 8
                  store ptr null, ptr %jC, align 8
                  %jfr = call ptr @jnative_gc_frame_push(i32 3)
                  call void @jnative_gc_frame_set_root(ptr %jfr, i32 0, ptr %jD)
                  call void @jnative_gc_frame_set_root(ptr %jfr, i32 1, ptr %jA)
                  call void @jnative_gc_frame_set_root(ptr %jfr, i32 2, ptr %jC)
                  %acc0 = call ptr @jnative_string_from_literal(ptr @.jn.emptystr, i32 0, ptr @meta.String)
                  store ptr %acc0, ptr %jC, align 8
                  br label %loop
                loop:
                  %i = phi i32 [ 0, %loopInit ], [ %inext, %next ]
                  %done = icmp sge i32 %i, %n
                  br i1 %done, label %ret, label %body
                body:
                  %needD = icmp sgt i32 %i, 0
                  br i1 %needD, label %addD, label %addE
                addD:
                  %accC0 = load ptr, ptr %jC, align 8
                  %dD0 = load ptr, ptr %jD, align 8
                  %accD = call ptr @jnative_string_concat(ptr %accC0, ptr %dD0)
                  store ptr %accD, ptr %jC, align 8
                  br label %addE2
                addE:
                  br label %addE2
                addE2:
                  %arrJ = load ptr, ptr %jA, align 8
                  %ep = call ptr @jnative_array_elem_ptr(ptr %arrJ, i32 %i, i64 8)
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
                  %acc1 = load ptr, ptr %jC, align 8
                  %accN = call ptr @jnative_string_concat(ptr %acc1, ptr %piece)
                  store ptr %accN, ptr %jC, align 8
                  %inext = add i32 %i, 1
                  br label %next
                next:
                  br label %loop
                ret:
                  %accR = load ptr, ptr %jC, align 8
                  call void @jnative_gc_frame_pop()
                  ret ptr %accR
                }

                ; Text conversion helpers for string concatenation / println (Java semantics).
                define ptr @jnative_string_or_null(ptr %s) {
                entry:
                  %isnull = icmp eq ptr %s, null
                  br i1 %isnull, label %nul, label %ok
                nul:
                  %ns = call ptr @jnative_string_from_literal(ptr @.jn.nullstr, i32 4, ptr @meta.String)
                  ret ptr %ns
                ok:
                  ret ptr %s
                }

                define ptr @jnative_string_value_of_bool(i1 %b) {
                entry:
                  br i1 %b, label %t, label %f
                t:
                  %ts = call ptr @jnative_string_from_literal(ptr @.fmt.bool.true, i32 4, ptr @meta.String)
                  ret ptr %ts
                f:
                  %fs = call ptr @jnative_string_from_literal(ptr @.fmt.bool.false, i32 5, ptr @meta.String)
                  ret ptr %fs
                }

                ; Default Object.toString(): "ClassName@hexIdentityHash". ClassMeta.name is the ptr field at
                ; byte offset 32 ({ ptr vtable, ptr super, i64 size, i32 flags, ptr name, ... }).
                define ptr @jnative_object_to_string(ptr %obj) {
                entry:
                  %isnull = icmp eq ptr %obj, null
                  br i1 %isnull, label %nul, label %go
                nul:
                  %ns = call ptr @jnative_string_from_literal(ptr @.jn.nullstr, i32 4, ptr @meta.String)
                  ret ptr %ns
                go:
                  %meta = load ptr, ptr %obj, align 8
                  %namePtr = getelementptr i8, ptr %meta, i64 32
                  %name = load ptr, ptr %namePtr, align 8
                  %h = call i32 @jnative_identity_hashcode(ptr %obj)
                  %buf = alloca [256 x i8], align 1
                  %p = getelementptr [256 x i8], ptr %buf, i64 0, i64 0
                  %n = call i32 (ptr, i64, ptr, ...) @snprintf(ptr %p, i64 256, ptr @.fmt.objstr, ptr %name, i32 %h)
                  %big = icmp sge i32 %n, 256
                  %len = select i1 %big, i32 255, i32 %n
                  %r = call ptr @jnative_string_from_literal(ptr %p, i32 %len, ptr @meta.String)
                  ret ptr %r
                }

                """);
        emitCString(".jn.emptystr", "");
        emitCString(".jn.nullstr", "null");
        emitCString(".fmt.objstr", "%s@%x");
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
