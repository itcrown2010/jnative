package io.jnative.backend.llvm.runtime;

/**
 * General-purpose byte[] helpers (0.8.3). Used by stdlib (e.g. HTTP) — not HTTP-specific.
 * @author 张晓达
 */
public final class BytesRuntimeEmitter {
    private final StringBuilder sb;

    public BytesRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("""
                define ptr @jnative_bytes_from_string(ptr %s) {
                entry:
                  %r = call ptr @jnative_string_get_bytes(ptr %s)
                  ret ptr %r
                }

                define ptr @jnative_bytes_to_string(ptr %arr) {
                entry:
                  %r = call ptr @jnative_string_from_bytes(ptr %arr)
                  ret ptr %r
                }

                define ptr @jnative_bytes_concat(ptr %a, ptr %b) {
                entry:
                  call void @jnative_null_check(ptr %a)
                  call void @jnative_null_check(ptr %b)
                  %la = call i32 @jnative_array_length(ptr %a)
                  %lb = call i32 @jnative_array_length(ptr %b)
                  %sum = add i32 %la, %lb
                  %out = call ptr @jnative_array_alloc(i64 1, i32 %sum, ptr @meta.array.i8)
                  %dst = getelementptr i8, ptr %out, i64 16
                  %sa = getelementptr i8, ptr %a, i64 16
                  %sb = getelementptr i8, ptr %b, i64 16
                  %la64 = sext i32 %la to i64
                  call ptr @memcpy(ptr %dst, ptr %sa, i64 %la64)
                  %dst2 = getelementptr i8, ptr %dst, i64 %la64
                  %lb64 = sext i32 %lb to i64
                  call ptr @memcpy(ptr %dst2, ptr %sb, i64 %lb64)
                  ret ptr %out
                }

                define void @jnative_bytes_copy(ptr %src, i32 %srcPos, ptr %dst, i32 %dstPos, i32 %len) {
                entry:
                  call void @jnative_null_check(ptr %src)
                  call void @jnative_null_check(ptr %dst)
                  %neg = icmp slt i32 %len, 0
                  br i1 %neg, label %trap, label %check
                trap:
                  call void @jnative_throw_aioobe()
                  ret void
                check:
                  %slen = call i32 @jnative_array_length(ptr %src)
                  %dlen = call i32 @jnative_array_length(ptr %dst)
                  %srcEnd = add i32 %srcPos, %len
                  %dstEnd = add i32 %dstPos, %len
                  %s0 = icmp slt i32 %srcPos, 0
                  %d0 = icmp slt i32 %dstPos, 0
                  %s1 = icmp sgt i32 %srcEnd, %slen
                  %d1 = icmp sgt i32 %dstEnd, %dlen
                  %b1 = or i1 %s0, %d0
                  %b2 = or i1 %s1, %d1
                  %bad = or i1 %b1, %b2
                  br i1 %bad, label %trap, label %ok
                ok:
                  %sbase = getelementptr i8, ptr %src, i64 16
                  %dbase = getelementptr i8, ptr %dst, i64 16
                  %sp64 = sext i32 %srcPos to i64
                  %dp64 = sext i32 %dstPos to i64
                  %n64 = sext i32 %len to i64
                  %sp = getelementptr i8, ptr %sbase, i64 %sp64
                  %dp = getelementptr i8, ptr %dbase, i64 %dp64
                  call ptr @memcpy(ptr %dp, ptr %sp, i64 %n64)
                  ret void
                }

                define i32 @jnative_bytes_index_of2(ptr %data, i32 %from, i32 %b0i, i32 %b1i) {
                entry:
                  call void @jnative_null_check(ptr %data)
                  %b0 = trunc i32 %b0i to i8
                  %b1 = trunc i32 %b1i to i8
                  %len = call i32 @jnative_array_length(ptr %data)
                  %from0 = icmp slt i32 %from, 0
                  %start = select i1 %from0, i32 0, i32 %from
                  %limit = sub i32 %len, 1
                  %bad = icmp sgt i32 %start, %limit
                  br i1 %bad, label %miss, label %loop
                loop:
                  %i = phi i32 [ %start, %entry ], [ %inext, %next ]
                  %base = getelementptr i8, ptr %data, i64 16
                  %i64 = sext i32 %i to i64
                  %p0 = getelementptr i8, ptr %base, i64 %i64
                  %c0 = load i8, ptr %p0, align 1
                  %eq0 = icmp eq i8 %c0, %b0
                  br i1 %eq0, label %check1, label %next
                check1:
                  %i1 = add i32 %i, 1
                  %i164 = sext i32 %i1 to i64
                  %p1 = getelementptr i8, ptr %base, i64 %i164
                  %c1 = load i8, ptr %p1, align 1
                  %eq1 = icmp eq i8 %c1, %b1
                  br i1 %eq1, label %hit, label %next
                next:
                  %inext = add i32 %i, 1
                  %cont = icmp sle i32 %inext, %limit
                  br i1 %cont, label %loop, label %miss
                hit:
                  ret i32 %i
                miss:
                  ret i32 -1
                }

                define ptr @jnative_bytes_slice(ptr %data, i32 %from, i32 %to) {
                entry:
                  call void @jnative_null_check(ptr %data)
                  %len = call i32 @jnative_array_length(ptr %data)
                  %b0 = icmp slt i32 %from, 0
                  %b1 = icmp slt i32 %to, %from
                  %b2 = icmp sgt i32 %to, %len
                  %t1 = or i1 %b0, %b1
                  %bad = or i1 %t1, %b2
                  br i1 %bad, label %trap, label %ok
                trap:
                  call void @jnative_throw_aioobe()
                  ret ptr null
                ok:
                  %n = sub i32 %to, %from
                  %out = call ptr @jnative_array_alloc(i64 1, i32 %n, ptr @meta.array.i8)
                  %dst = getelementptr i8, ptr %out, i64 16
                  %src0 = getelementptr i8, ptr %data, i64 16
                  %f64 = sext i32 %from to i64
                  %src = getelementptr i8, ptr %src0, i64 %f64
                  %n64 = sext i32 %n to i64
                  call ptr @memcpy(ptr %dst, ptr %src, i64 %n64)
                  ret ptr %out
                }

                """);
    }
}
