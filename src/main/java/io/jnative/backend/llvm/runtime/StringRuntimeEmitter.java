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

                """);
    }
}
