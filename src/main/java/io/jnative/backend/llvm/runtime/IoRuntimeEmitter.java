package io.jnative.backend.llvm.runtime;

/** println helpers. String objects store UTF-8 bytes inline at +16. */
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
                  %data = getelementptr i8, ptr %s, i64 16
                  call i32 @puts(ptr %data)
                  ret void
                }

                """);
    }
}
