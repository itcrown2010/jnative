package io.jnative.backend.llvm.runtime;

/**
 * String runtime (UTF-8, inline bytes for GC ownership).
 * Layout: { ClassMetadata* @0, i32 length @8, i8 data... @16 }
 */
public final class StringRuntimeEmitter {
    private final StringBuilder sb;

    public StringRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("""
                define ptr @jnative_string_from_literal(ptr %bytes, i32 %len, ptr %meta) {
                entry:
                  %len64 = sext i32 %len to i64
                  %payload = add i64 %len64, 17
                  %obj = call ptr @jnative_gc_alloc(i64 %payload)
                  store ptr %meta, ptr %obj, align 8
                  %lptr = getelementptr i8, ptr %obj, i64 8
                  store i32 %len, ptr %lptr, align 4
                  %dptr = getelementptr i8, ptr %obj, i64 16
                  call ptr @memcpy(ptr %dptr, ptr %bytes, i64 %len64)
                  %nul = getelementptr i8, ptr %dptr, i64 %len64
                  store i8 0, ptr %nul, align 1
                  ret ptr %obj
                }

                define i32 @jnative_string_length(ptr %s) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  %lptr = getelementptr i8, ptr %s, i64 8
                  %len = load i32, ptr %lptr, align 4
                  ret i32 %len
                }

                define i16 @jnative_string_char_at(ptr %s, i32 %index) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  %lptr = getelementptr i8, ptr %s, i64 8
                  %len = load i32, ptr %lptr, align 4
                  %lt0 = icmp slt i32 %index, 0
                  %ge = icmp sge i32 %index, %len
                  %bad = or i1 %lt0, %ge
                  br i1 %bad, label %trap, label %ok
                trap:
                  call void @jnative_throw_aioobe()
                  ret i16 0
                ok:
                  %idx64 = sext i32 %index to i64
                  %base = getelementptr i8, ptr %s, i64 16
                  %cp = getelementptr i8, ptr %base, i64 %idx64
                  %b = load i8, ptr %cp, align 1
                  %z = zext i8 %b to i16
                  ret i16 %z
                }

                define ptr @jnative_string_concat(ptr %a, ptr %b) {
                entry:
                  call void @jnative_null_check(ptr %a)
                  call void @jnative_null_check(ptr %b)
                  %la = call i32 @jnative_string_length(ptr %a)
                  %lb = call i32 @jnative_string_length(ptr %b)
                  %sum = add i32 %la, %lb
                  %sum64 = sext i32 %sum to i64
                  %payload = add i64 %sum64, 17
                  %obj = call ptr @jnative_gc_alloc(i64 %payload)
                  %meta = load ptr, ptr %a, align 8
                  store ptr %meta, ptr %obj, align 8
                  %lptr = getelementptr i8, ptr %obj, i64 8
                  store i32 %sum, ptr %lptr, align 4
                  %dest = getelementptr i8, ptr %obj, i64 16
                  %da = getelementptr i8, ptr %a, i64 16
                  %db = getelementptr i8, ptr %b, i64 16
                  %la64 = sext i32 %la to i64
                  call ptr @memcpy(ptr %dest, ptr %da, i64 %la64)
                  %dest2 = getelementptr i8, ptr %dest, i64 %la64
                  %lb64 = sext i32 %lb to i64
                  call ptr @memcpy(ptr %dest2, ptr %db, i64 %lb64)
                  %nul = getelementptr i8, ptr %dest, i64 %sum64
                  store i8 0, ptr %nul, align 1
                  ret ptr %obj
                }

                define i1 @jnative_string_equals(ptr %a, ptr %b) {
                entry:
                  %same = icmp eq ptr %a, %b
                  br i1 %same, label %yes, label %checknull
                yes:
                  ret i1 true
                checknull:
                  %an = icmp eq ptr %a, null
                  %bn = icmp eq ptr %b, null
                  %either = or i1 %an, %bn
                  br i1 %either, label %no, label %cmp
                no:
                  ret i1 false
                cmp:
                  %la = call i32 @jnative_string_length(ptr %a)
                  %lb = call i32 @jnative_string_length(ptr %b)
                  %leneq = icmp eq i32 %la, %lb
                  br i1 %leneq, label %bytes, label %no2
                no2:
                  ret i1 false
                bytes:
                  %da = getelementptr i8, ptr %a, i64 16
                  %db = getelementptr i8, ptr %b, i64 16
                  %n = sext i32 %la to i64
                  br label %loop
                loop:
                  %i = phi i64 [ 0, %bytes ], [ %inext, %body ]
                  %done = icmp eq i64 %i, %n
                  br i1 %done, label %yes2, label %body
                body:
                  %pa = getelementptr i8, ptr %da, i64 %i
                  %pb = getelementptr i8, ptr %db, i64 %i
                  %ca = load i8, ptr %pa, align 1
                  %cb = load i8, ptr %pb, align 1
                  %eq = icmp eq i8 %ca, %cb
                  %inext = add i64 %i, 1
                  br i1 %eq, label %loop, label %no3
                yes2:
                  ret i1 true
                no3:
                  ret i1 false
                }

                ; Object.equals override: only equal when other is a String with same bytes.
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
                  %r = call i1 @jnative_string_equals(ptr %this, ptr %other)
                  ret i1 %r
                }

                ; Java-compatible String.hashCode (31 * h + byte), ASCII/Latin-1 payload.
                define i32 @jnative_string_hashcode(ptr %s) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  %len = call i32 @jnative_string_length(ptr %s)
                  %data = getelementptr i8, ptr %s, i64 16
                  br label %loop
                loop:
                  %i = phi i32 [ 0, %entry ], [ %inext, %body ]
                  %h = phi i32 [ 0, %entry ], [ %h2, %body ]
                  %done = icmp eq i32 %i, %len
                  br i1 %done, label %doneL, label %body
                body:
                  %i64 = sext i32 %i to i64
                  %p = getelementptr i8, ptr %data, i64 %i64
                  %b = load i8, ptr %p, align 1
                  %z = zext i8 %b to i32
                  %m = mul i32 %h, 31
                  %h2 = add i32 %m, %z
                  %inext = add i32 %i, 1
                  br label %loop
                doneL:
                  ret i32 %h
                }

                define i32 @jnative_string_index_of(ptr %s, ptr %needle) {
                entry:
                  %r = call i32 @jnative_string_index_of_from(ptr %s, ptr %needle, i32 0)
                  ret i32 %r
                }

                define i32 @jnative_string_index_of_from(ptr %s, ptr %needle, i32 %from) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  call void @jnative_null_check(ptr %needle)
                  %slen = call i32 @jnative_string_length(ptr %s)
                  %nlen = call i32 @jnative_string_length(ptr %needle)
                  %from0 = icmp slt i32 %from, 0
                  %start = select i1 %from0, i32 0, i32 %from
                  %empty = icmp eq i32 %nlen, 0
                  br i1 %empty, label %retStart, label %scan
                retStart:
                  %okStart = icmp sle i32 %start, %slen
                  %rs = select i1 %okStart, i32 %start, i32 -1
                  ret i32 %rs
                scan:
                  %limit = sub i32 %slen, %nlen
                  %bad = icmp slt i32 %limit, %start
                  br i1 %bad, label %miss, label %loop
                loop:
                  %i = phi i32 [ %start, %scan ], [ %inext, %next ]
                  %match = call i1 @jnative_string_region_eq(ptr %s, i32 %i, ptr %needle)
                  br i1 %match, label %hit, label %next
                next:
                  %inext = add i32 %i, 1
                  %cont = icmp sle i32 %inext, %limit
                  br i1 %cont, label %loop, label %miss
                hit:
                  ret i32 %i
                miss:
                  ret i32 -1
                }

                define i1 @jnative_string_region_eq(ptr %s, i32 %off, ptr %needle) {
                entry:
                  %nlen = call i32 @jnative_string_length(ptr %needle)
                  %sd = getelementptr i8, ptr %s, i64 16
                  %nd = getelementptr i8, ptr %needle, i64 16
                  %off64 = sext i32 %off to i64
                  %base = getelementptr i8, ptr %sd, i64 %off64
                  br label %loop
                loop:
                  %i = phi i32 [ 0, %entry ], [ %inext, %body ]
                  %done = icmp eq i32 %i, %nlen
                  br i1 %done, label %yes, label %body
                body:
                  %i64 = sext i32 %i to i64
                  %pa = getelementptr i8, ptr %base, i64 %i64
                  %pb = getelementptr i8, ptr %nd, i64 %i64
                  %ca = load i8, ptr %pa, align 1
                  %cb = load i8, ptr %pb, align 1
                  %eq = icmp eq i8 %ca, %cb
                  %inext = add i32 %i, 1
                  br i1 %eq, label %loop, label %no
                yes:
                  ret i1 true
                no:
                  ret i1 false
                }

                define ptr @jnative_string_substring(ptr %s, i32 %begin, i32 %end) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  %len = call i32 @jnative_string_length(ptr %s)
                  %b0 = icmp slt i32 %begin, 0
                  %e0 = icmp slt i32 %end, %begin
                  %e1 = icmp sgt i32 %end, %len
                  %bad1 = or i1 %b0, %e0
                  %bad = or i1 %bad1, %e1
                  br i1 %bad, label %trap, label %ok
                trap:
                  call void @jnative_throw_aioobe()
                  ret ptr null
                ok:
                  %n = sub i32 %end, %begin
                  %src0 = getelementptr i8, ptr %s, i64 16
                  %b64 = sext i32 %begin to i64
                  %src = getelementptr i8, ptr %src0, i64 %b64
                  %r = call ptr @jnative_string_from_literal(ptr %src, i32 %n, ptr @meta.String)
                  ret ptr %r
                }

                define ptr @jnative_string_substring_from(ptr %s, i32 %begin) {
                entry:
                  %len = call i32 @jnative_string_length(ptr %s)
                  %r = call ptr @jnative_string_substring(ptr %s, i32 %begin, i32 %len)
                  ret ptr %r
                }

                define i1 @jnative_string_starts_with(ptr %s, ptr %prefix) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  call void @jnative_null_check(ptr %prefix)
                  %plen = call i32 @jnative_string_length(ptr %prefix)
                  %slen = call i32 @jnative_string_length(ptr %s)
                  %tooBig = icmp sgt i32 %plen, %slen
                  br i1 %tooBig, label %no, label %cmp
                cmp:
                  %r = call i1 @jnative_string_region_eq(ptr %s, i32 0, ptr %prefix)
                  ret i1 %r
                no:
                  ret i1 false
                }

                define i1 @jnative_string_ends_with(ptr %s, ptr %suffix) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  call void @jnative_null_check(ptr %suffix)
                  %slen = call i32 @jnative_string_length(ptr %s)
                  %suflen = call i32 @jnative_string_length(ptr %suffix)
                  %tooBig = icmp sgt i32 %suflen, %slen
                  br i1 %tooBig, label %no, label %cmp
                cmp:
                  %off = sub i32 %slen, %suflen
                  %r = call i1 @jnative_string_region_eq(ptr %s, i32 %off, ptr %suffix)
                  ret i1 %r
                no:
                  ret i1 false
                }

                define i8 @jnative_ascii_lower(i8 %c) {
                entry:
                  %geA = icmp uge i8 %c, 65
                  %leZ = icmp ule i8 %c, 90
                  %isUp = and i1 %geA, %leZ
                  %add = add i8 %c, 32
                  %r = select i1 %isUp, i8 %add, i8 %c
                  ret i8 %r
                }

                define i1 @jnative_string_equals_ignore_case(ptr %a, ptr %b) {
                entry:
                  call void @jnative_null_check(ptr %a)
                  call void @jnative_null_check(ptr %b)
                  %la = call i32 @jnative_string_length(ptr %a)
                  %lb = call i32 @jnative_string_length(ptr %b)
                  %ne = icmp ne i32 %la, %lb
                  br i1 %ne, label %no, label %loopinit
                loopinit:
                  %da = getelementptr i8, ptr %a, i64 16
                  %db = getelementptr i8, ptr %b, i64 16
                  br label %loop
                loop:
                  %i = phi i32 [ 0, %loopinit ], [ %inext, %body ]
                  %done = icmp eq i32 %i, %la
                  br i1 %done, label %yes, label %body
                body:
                  %i64 = sext i32 %i to i64
                  %pa = getelementptr i8, ptr %da, i64 %i64
                  %pb = getelementptr i8, ptr %db, i64 %i64
                  %ca0 = load i8, ptr %pa, align 1
                  %cb0 = load i8, ptr %pb, align 1
                  %ca = call i8 @jnative_ascii_lower(i8 %ca0)
                  %cb = call i8 @jnative_ascii_lower(i8 %cb0)
                  %eq = icmp eq i8 %ca, %cb
                  %inext = add i32 %i, 1
                  br i1 %eq, label %loop, label %no
                yes:
                  ret i1 true
                no:
                  ret i1 false
                }

                define ptr @jnative_string_trim(ptr %s) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  %len = call i32 @jnative_string_length(ptr %s)
                  %data = getelementptr i8, ptr %s, i64 16
                  br label %left
                left:
                  %i = phi i32 [ 0, %entry ], [ %inext, %leftbody ]
                  %doneL = icmp sge i32 %i, %len
                  br i1 %doneL, label %empty, label %leftbody
                leftbody:
                  %i64 = sext i32 %i to i64
                  %p = getelementptr i8, ptr %data, i64 %i64
                  %c = load i8, ptr %p, align 1
                  %sp = icmp eq i8 %c, 32
                  %ht = icmp eq i8 %c, 9
                  %cr = icmp eq i8 %c, 13
                  %lf = icmp eq i8 %c, 10
                  %ws1 = or i1 %sp, %ht
                  %ws2 = or i1 %cr, %lf
                  %ws = or i1 %ws1, %ws2
                  %inext = add i32 %i, 1
                  br i1 %ws, label %left, label %rightinit
                rightinit:
                  %j0 = sub i32 %len, 1
                  br label %right
                right:
                  %j = phi i32 [ %j0, %rightinit ], [ %jnext, %rightbody ]
                  %doneR = icmp slt i32 %j, %i
                  br i1 %doneR, label %empty, label %rightbody
                rightbody:
                  %j64 = sext i32 %j to i64
                  %pj = getelementptr i8, ptr %data, i64 %j64
                  %cj = load i8, ptr %pj, align 1
                  %sp2 = icmp eq i8 %cj, 32
                  %ht2 = icmp eq i8 %cj, 9
                  %cr2 = icmp eq i8 %cj, 13
                  %lf2 = icmp eq i8 %cj, 10
                  %ws3 = or i1 %sp2, %ht2
                  %ws4 = or i1 %cr2, %lf2
                  %wsr = or i1 %ws3, %ws4
                  %jnext = sub i32 %j, 1
                  br i1 %wsr, label %right, label %slice
                slice:
                  %end = add i32 %j, 1
                  %r = call ptr @jnative_string_substring(ptr %s, i32 %i, i32 %end)
                  ret ptr %r
                empty:
                  %e = call ptr @jnative_string_from_literal(ptr @.jn.emptystr, i32 0, ptr @meta.String)
                  ret ptr %e
                }

                define ptr @jnative_string_get_bytes(ptr %s) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  %len = call i32 @jnative_string_length(ptr %s)
                  %arr = call ptr @jnative_array_alloc(i64 1, i32 %len, ptr @meta.array.i8)
                  %dst = getelementptr i8, ptr %arr, i64 16
                  %src = getelementptr i8, ptr %s, i64 16
                  %n64 = sext i32 %len to i64
                  call ptr @memcpy(ptr %dst, ptr %src, i64 %n64)
                  ret ptr %arr
                }

                define ptr @jnative_string_from_bytes(ptr %arr) {
                entry:
                  call void @jnative_null_check(ptr %arr)
                  %len = call i32 @jnative_array_length(ptr %arr)
                  %src = getelementptr i8, ptr %arr, i64 16
                  %r = call ptr @jnative_string_from_literal(ptr %src, i32 %len, ptr @meta.String)
                  ret ptr %r
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
                  %zero = icmp eq i32 %value, 0
                  br i1 %zero, label %z, label %digits
                z:
                  store i8 48, ptr %out, align 1
                  ret i32 1
                digits:
                  %tmp = alloca [16 x i8], align 1
                  br label %loop
                loop:
                  %v = phi i32 [ %value, %digits ], [ %vnext, %body ]
                  %i = phi i32 [ 0, %digits ], [ %inext, %body ]
                  %done = icmp eq i32 %v, 0
                  br i1 %done, label %reverse, label %body
                body:
                  %d = urem i32 %v, 10
                  %ch = add i32 %d, 48
                  %ch8 = trunc i32 %ch to i8
                  %tp = getelementptr [16 x i8], ptr %tmp, i64 0, i32 %i
                  store i8 %ch8, ptr %tp, align 1
                  %vnext = udiv i32 %v, 10
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
                  %sp = getelementptr [16 x i8], ptr %tmp, i64 0, i32 %srcIdx1
                  %c = load i8, ptr %sp, align 1
                  %dp = getelementptr i8, ptr %out, i32 %j
                  store i8 %c, ptr %dp, align 1
                  %jnext = add i32 %j, 1
                  br label %rloop
                done3:
                  ret i32 %i
                }

                define ptr @jnative_string_to_lower(ptr %s) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  %len = call i32 @jnative_string_length(ptr %s)
                  %n64 = sext i32 %len to i64
                  %buf = call ptr @malloc(i64 %n64)
                  %src = getelementptr i8, ptr %s, i64 16
                  br label %loop
                loop:
                  %i = phi i32 [ 0, %entry ], [ %inext, %body ]
                  %done = icmp eq i32 %i, %len
                  br i1 %done, label %done2, label %body
                body:
                  %i64 = sext i32 %i to i64
                  %ps = getelementptr i8, ptr %src, i64 %i64
                  %c = load i8, ptr %ps, align 1
                  %cl = call i8 @jnative_ascii_lower(i8 %c)
                  %pd = getelementptr i8, ptr %buf, i64 %i64
                  store i8 %cl, ptr %pd, align 1
                  %inext = add i32 %i, 1
                  br label %loop
                done2:
                  %r = call ptr @jnative_string_from_literal(ptr %buf, i32 %len, ptr @meta.String)
                  call void @free(ptr %buf)
                  ret ptr %r
                }

                define i8 @jnative_ascii_upper(i8 %c) {
                entry:
                  %gea = icmp uge i8 %c, 97
                  %lez = icmp ule i8 %c, 122
                  %isl = and i1 %gea, %lez
                  %sub = sub i8 %c, 32
                  %r = select i1 %isl, i8 %sub, i8 %c
                  ret i8 %r
                }

                define ptr @jnative_string_to_upper(ptr %s) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  %len = call i32 @jnative_string_length(ptr %s)
                  %n64 = sext i32 %len to i64
                  %buf = call ptr @malloc(i64 %n64)
                  %src = getelementptr i8, ptr %s, i64 16
                  br label %loop
                loop:
                  %i = phi i32 [ 0, %entry ], [ %inext, %body ]
                  %done = icmp eq i32 %i, %len
                  br i1 %done, label %done2, label %body
                body:
                  %i64 = sext i32 %i to i64
                  %ps = getelementptr i8, ptr %src, i64 %i64
                  %c = load i8, ptr %ps, align 1
                  %cu = call i8 @jnative_ascii_upper(i8 %c)
                  %pd = getelementptr i8, ptr %buf, i64 %i64
                  store i8 %cu, ptr %pd, align 1
                  %inext = add i32 %i, 1
                  br label %loop
                done2:
                  %r = call ptr @jnative_string_from_literal(ptr %buf, i32 %len, ptr @meta.String)
                  call void @free(ptr %buf)
                  ret ptr %r
                }

                define i1 @jnative_string_is_empty(ptr %s) {
                entry:
                  %len = call i32 @jnative_string_length(ptr %s)
                  %z = icmp eq i32 %len, 0
                  ret i1 %z
                }

                define i1 @jnative_string_contains(ptr %s, ptr %needle) {
                entry:
                  %idx = call i32 @jnative_string_index_of(ptr %s, ptr %needle)
                  %ok = icmp sge i32 %idx, 0
                  ret i1 %ok
                }

                define i32 @jnative_string_last_index_of(ptr %s, ptr %needle) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  %slen = call i32 @jnative_string_length(ptr %s)
                  %from = sub i32 %slen, 1
                  %r = call i32 @jnative_string_last_index_of_from(ptr %s, ptr %needle, i32 %from)
                  ret i32 %r
                }

                define i32 @jnative_string_last_index_of_from(ptr %s, ptr %needle, i32 %from) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  call void @jnative_null_check(ptr %needle)
                  %slen = call i32 @jnative_string_length(ptr %s)
                  %nlen = call i32 @jnative_string_length(ptr %needle)
                  %empty = icmp eq i32 %nlen, 0
                  br i1 %empty, label %emptyN, label %bounds
                emptyN:
                  %okF = icmp sle i32 %from, %slen
                  %cap = select i1 %okF, i32 %from, i32 %slen
                  %neg = icmp slt i32 %cap, 0
                  %rs = select i1 %neg, i32 -1, i32 %cap
                  ret i32 %rs
                bounds:
                  %limit = sub i32 %slen, %nlen
                  %fromNeg = icmp slt i32 %from, 0
                  br i1 %fromNeg, label %miss, label %startPick
                startPick:
                  %tooBig = icmp sgt i32 %from, %limit
                  %start = select i1 %tooBig, i32 %limit, i32 %from
                  %badLim = icmp slt i32 %limit, 0
                  br i1 %badLim, label %miss, label %loop
                loop:
                  %i = phi i32 [ %start, %startPick ], [ %iprev, %next ]
                  %match = call i1 @jnative_string_region_eq(ptr %s, i32 %i, ptr %needle)
                  br i1 %match, label %hit, label %next
                next:
                  %iprev = sub i32 %i, 1
                  %cont = icmp sge i32 %iprev, 0
                  br i1 %cont, label %loop, label %miss
                hit:
                  ret i32 %i
                miss:
                  ret i32 -1
                }

                define i32 @jnative_string_compare_to(ptr %a, ptr %b) {
                entry:
                  call void @jnative_null_check(ptr %a)
                  call void @jnative_null_check(ptr %b)
                  %la = call i32 @jnative_string_length(ptr %a)
                  %lb = call i32 @jnative_string_length(ptr %b)
                  %da = getelementptr i8, ptr %a, i64 16
                  %db = getelementptr i8, ptr %b, i64 16
                  %minok = icmp sle i32 %la, %lb
                  %n = select i1 %minok, i32 %la, i32 %lb
                  br label %loop
                loop:
                  %i = phi i32 [ 0, %entry ], [ %inext, %body ]
                  %done = icmp eq i32 %i, %n
                  br i1 %done, label %byLen, label %body
                body:
                  %i64 = sext i32 %i to i64
                  %pa = getelementptr i8, ptr %da, i64 %i64
                  %pb = getelementptr i8, ptr %db, i64 %i64
                  %ca = load i8, ptr %pa, align 1
                  %cb = load i8, ptr %pb, align 1
                  %za = zext i8 %ca to i32
                  %zb = zext i8 %cb to i32
                  %eq = icmp eq i32 %za, %zb
                  %inext = add i32 %i, 1
                  br i1 %eq, label %loop, label %diff
                diff:
                  %d = sub i32 %za, %zb
                  ret i32 %d
                byLen:
                  %dl = sub i32 %la, %lb
                  ret i32 %dl
                }

                ; Literal replace-all (not regex). Empty target → return original.
                define ptr @jnative_string_replace(ptr %s, ptr %target, ptr %repl) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  call void @jnative_null_check(ptr %target)
                  call void @jnative_null_check(ptr %repl)
                  %tlen = call i32 @jnative_string_length(ptr %target)
                  %empty = icmp eq i32 %tlen, 0
                  br i1 %empty, label %orig, label %count
                orig:
                  ret ptr %s
                count:
                  %slen = call i32 @jnative_string_length(ptr %s)
                  %rlen = call i32 @jnative_string_length(ptr %repl)
                  br label %scan
                scan:
                  %i = phi i32 [ 0, %count ], [ %i2, %scannext ]
                  %occ = phi i32 [ 0, %count ], [ %occ2, %scannext ]
                  %lim = sub i32 %slen, %tlen
                  %done = icmp sgt i32 %i, %lim
                  br i1 %done, label %alloc, label %try
                try:
                  %m = call i1 @jnative_string_region_eq(ptr %s, i32 %i, ptr %target)
                  br i1 %m, label %hit, label %miss
                hit:
                  %occ1 = add i32 %occ, 1
                  %i1 = add i32 %i, %tlen
                  br label %scannext
                miss:
                  %i1m = add i32 %i, 1
                  br label %scannext
                scannext:
                  %i2 = phi i32 [ %i1, %hit ], [ %i1m, %miss ]
                  %occ2 = phi i32 [ %occ1, %hit ], [ %occ, %miss ]
                  br label %scan
                alloc:
                  %delta = sub i32 %rlen, %tlen
                  %extra = mul i32 %occ, %delta
                  %outLen = add i32 %slen, %extra
                  %out64 = sext i32 %outLen to i64
                  %buf = call ptr @malloc(i64 %out64)
                  %src = getelementptr i8, ptr %s, i64 16
                  %td = getelementptr i8, ptr %target, i64 16
                  %rd = getelementptr i8, ptr %repl, i64 16
                  br label %build
                build:
                  %si = phi i32 [ 0, %alloc ], [ %si2, %bnext ]
                  %di = phi i32 [ 0, %alloc ], [ %di2, %bnext ]
                  %doneB = icmp sge i32 %si, %slen
                  br i1 %doneB, label %finish, label %btry
                btry:
                  %remain = sub i32 %slen, %si
                  %can = icmp sge i32 %remain, %tlen
                  br i1 %can, label %bmatch, label %bcopy1
                bmatch:
                  %bm = call i1 @jnative_string_region_eq(ptr %s, i32 %si, ptr %target)
                  br i1 %bm, label %brepl, label %bcopy1
                brepl:
                  %r64 = sext i32 %rlen to i64
                  %dp = getelementptr i8, ptr %buf, i32 %di
                  call ptr @memcpy(ptr %dp, ptr %rd, i64 %r64)
                  %siR = add i32 %si, %tlen
                  %diR = add i32 %di, %rlen
                  br label %bnext
                bcopy1:
                  %s64 = sext i32 %si to i64
                  %sp = getelementptr i8, ptr %src, i64 %s64
                  %c = load i8, ptr %sp, align 1
                  %dp1 = getelementptr i8, ptr %buf, i32 %di
                  store i8 %c, ptr %dp1, align 1
                  %siC = add i32 %si, 1
                  %diC = add i32 %di, 1
                  br label %bnext
                bnext:
                  %si2 = phi i32 [ %siR, %brepl ], [ %siC, %bcopy1 ]
                  %di2 = phi i32 [ %diR, %brepl ], [ %diC, %bcopy1 ]
                  br label %build
                finish:
                  %r = call ptr @jnative_string_from_literal(ptr %buf, i32 %outLen, ptr @meta.String)
                  call void @free(ptr %buf)
                  ret ptr %r
                }

                ; Literal delimiter split (NOT Java regex).
                ; Contract:
                ;   - delimiter is literal UTF-8 bytes
                ;   - empty delimiter → single-element [s]
                ;   - empty input → [""]
                ;   - consecutive delimiters keep empty parts
                ;   - trailing empty parts kept
                ;   - null s/delimiter → NPE
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
                  %m = call i1 @jnative_string_region_eq(ptr %s, i32 %i, ptr %delim)
                  br i1 %m, label %hit, label %miss
                hit:
                  %parts1 = add i32 %parts, 1
                  %i1 = add i32 %i, %dlen
                  br label %scannext
                miss:
                  %i1m = add i32 %i, 1
                  br label %scannext
                scannext:
                  %i2 = phi i32 [ %i1, %hit ], [ %i1m, %miss ]
                  %parts2 = phi i32 [ %parts1, %hit ], [ %parts, %miss ]
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
                  %bm = call i1 @jnative_string_region_eq(ptr %s, i32 %si, ptr %delim)
                  br i1 %bm, label %bemit, label %badv
                bemit:
                  %piece = call ptr @jnative_string_substring(ptr %s, i32 %start, i32 %si)
                  %ep = call ptr @jnative_array_elem_ptr(ptr %arr, i32 %pi, i64 8)
                  store ptr %piece, ptr %ep, align 8
                  %siR = add i32 %si, %dlen
                  %piR = add i32 %pi, 1
                  br label %bcont
                badv:
                  %siC = add i32 %si, 1
                  br label %bcontKeep
                bcontKeep:
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

                ; Single-pass join (StringBuilder-equivalent; no chained concat).
                ; null delimiter or elements → NPE. null element → "null".
                define ptr @jnative_string_join(ptr %delim, ptr %arr) {
                entry:
                  call void @jnative_null_check(ptr %delim)
                  call void @jnative_null_check(ptr %arr)
                  %n = call i32 @jnative_array_length(ptr %arr)
                  %dlen = call i32 @jnative_string_length(ptr %delim)
                  %zero = icmp eq i32 %n, 0
                  br i1 %zero, label %empty, label %sum
                empty:
                  %e = call ptr @jnative_string_from_literal(ptr @.jn.emptystr, i32 0, ptr @meta.String)
                  ret ptr %e
                sum:
                  br label %slenloop
                slenloop:
                  %i = phi i32 [ 0, %sum ], [ %i2, %slennext ]
                  %acc = phi i32 [ 0, %sum ], [ %acc2, %slennext ]
                  %done = icmp sge i32 %i, %n
                  br i1 %done, label %allocJ, label %slenbody
                slenbody:
                  %ep = call ptr @jnative_array_elem_ptr(ptr %arr, i32 %i, i64 8)
                  %el = load ptr, ptr %ep, align 8
                  %isnull = icmp eq ptr %el, null
                  br i1 %isnull, label %usenull, label %uselen
                usenull:
                  br label %addlen
                uselen:
                  %elLen = call i32 @jnative_string_length(ptr %el)
                  br label %addlen
                addlen:
                  %elen = phi i32 [ 4, %usenull ], [ %elLen, %uselen ]
                  %acc1 = add i32 %acc, %elen
                  %notFirst = icmp sgt i32 %i, 0
                  %extra = select i1 %notFirst, i32 %dlen, i32 0
                  %acc2 = add i32 %acc1, %extra
                  %i2 = add i32 %i, 1
                  br label %slennext
                slennext:
                  br label %slenloop
                allocJ:
                  %acc64 = sext i32 %acc to i64
                  %buf = call ptr @malloc(i64 %acc64)
                  %dd = getelementptr i8, ptr %delim, i64 16
                  br label %buildJ
                buildJ:
                  %ji = phi i32 [ 0, %allocJ ], [ %ji2, %jnext ]
                  %di = phi i32 [ 0, %allocJ ], [ %di2, %jnext ]
                  %jdone = icmp sge i32 %ji, %n
                  br i1 %jdone, label %finishJ, label %jbody
                jbody:
                  %needDelim = icmp sgt i32 %ji, 0
                  br i1 %needDelim, label %jdelim, label %jelem
                jdelim:
                  %d64 = sext i32 %dlen to i64
                  %dp = getelementptr i8, ptr %buf, i32 %di
                  call ptr @memcpy(ptr %dp, ptr %dd, i64 %d64)
                  %diD = add i32 %di, %dlen
                  br label %jelem2
                jelem:
                  br label %jelem2
                jelem2:
                  %diE = phi i32 [ %diD, %jdelim ], [ %di, %jelem ]
                  %jep = call ptr @jnative_array_elem_ptr(ptr %arr, i32 %ji, i64 8)
                  %jel = load ptr, ptr %jep, align 8
                  %jnull = icmp eq ptr %jel, null
                  br i1 %jnull, label %jwritenull, label %jwritestr
                jwritenull:
                  %np = getelementptr i8, ptr %buf, i32 %diE
                  store i8 110, ptr %np, align 1
                  %np1 = getelementptr i8, ptr %np, i64 1
                  store i8 117, ptr %np1, align 1
                  %np2 = getelementptr i8, ptr %np, i64 2
                  store i8 108, ptr %np2, align 1
                  %np3 = getelementptr i8, ptr %np, i64 3
                  store i8 108, ptr %np3, align 1
                  %diN = add i32 %diE, 4
                  br label %jnext
                jwritestr:
                  %jl = call i32 @jnative_string_length(ptr %jel)
                  %js = getelementptr i8, ptr %jel, i64 16
                  %jl64 = sext i32 %jl to i64
                  %jp = getelementptr i8, ptr %buf, i32 %diE
                  call ptr @memcpy(ptr %jp, ptr %js, i64 %jl64)
                  %diS = add i32 %diE, %jl
                  br label %jnext
                jnext:
                  %di2 = phi i32 [ %diN, %jwritenull ], [ %diS, %jwritestr ]
                  %ji2 = add i32 %ji, 1
                  br label %buildJ
                finishJ:
                  %out = call ptr @jnative_string_from_literal(ptr %buf, i32 %acc, ptr @meta.String)
                  call void @free(ptr %buf)
                  ret ptr %out
                }

                """);
        emitCString(".jn.emptystr", "");
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
