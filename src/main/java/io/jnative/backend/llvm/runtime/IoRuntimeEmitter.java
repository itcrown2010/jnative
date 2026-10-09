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
                  call i32 (ptr, ...) @printf(ptr @.fmt.double, double %v)
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
                  %c = zext i16 %v to i32
                  call i32 (ptr, ...) @printf(ptr @.fmt.char, i32 %c)
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
