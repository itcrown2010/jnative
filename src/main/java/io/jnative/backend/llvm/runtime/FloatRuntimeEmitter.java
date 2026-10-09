package io.jnative.backend.llvm.runtime;

/**
 * Java-compatible float/double → integer conversion (NaN→0, toward-zero, saturate).
 * Avoids raw {@code fptosi} on out-of-range / NaN inputs (LLVM UB/poison).
 * @author 张晓达
 */
public final class FloatRuntimeEmitter {
    private final StringBuilder sb;

    public FloatRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("""
                define i32 @jnative_f64_to_i32(double %v) {
                entry:
                  %nan = fcmp uno double %v, %v
                  br i1 %nan, label %z, label %hi
                z:
                  ret i32 0
                hi:
                  %ge = fcmp oge double %v, 0x41E0000000000000
                  br i1 %ge, label %max, label %lo
                max:
                  ret i32 2147483647
                lo:
                  %le = fcmp ole double %v, 0xC1E0000000000000
                  br i1 %le, label %min, label %ok
                min:
                  ret i32 -2147483648
                ok:
                  %r = fptosi double %v to i32
                  ret i32 %r
                }

                define i64 @jnative_f64_to_i64(double %v) {
                entry:
                  %nan = fcmp uno double %v, %v
                  br i1 %nan, label %z, label %hi
                z:
                  ret i64 0
                hi:
                  %ge = fcmp oge double %v, 0x43E0000000000000
                  br i1 %ge, label %max, label %lo
                max:
                  ret i64 9223372036854775807
                lo:
                  %le = fcmp ole double %v, 0xC3E0000000000000
                  br i1 %le, label %min, label %ok
                min:
                  ret i64 -9223372036854775808
                ok:
                  %r = fptosi double %v to i64
                  ret i64 %r
                }

                define i32 @jnative_f32_to_i32(float %v) {
                entry:
                  %d = fpext float %v to double
                  %r = call i32 @jnative_f64_to_i32(double %d)
                  ret i32 %r
                }

                define i64 @jnative_f32_to_i64(float %v) {
                entry:
                  %d = fpext float %v to double
                  %r = call i64 @jnative_f64_to_i64(double %d)
                  ret i64 %r
                }

                define ptr @jnative_string_value_of_f64(double %v) {
                entry:
                  %buf = alloca [64 x i8], align 1
                  %p = getelementptr [64 x i8], ptr %buf, i64 0, i64 0
                  %n = call i32 (ptr, i64, ptr, ...) @snprintf(ptr %p, i64 64, ptr @.fmt.double.raw, double %v)
                  %neg = icmp slt i32 %n, 0
                  br i1 %neg, label %empty, label %mk
                empty:
                  %e = call ptr @jnative_string_from_literal(ptr @.jn.emptystr, i32 0, ptr @meta.String)
                  ret ptr %e
                mk:
                  %r = call ptr @jnative_string_from_literal(ptr %p, i32 %n, ptr @meta.String)
                  ret ptr %r
                }

                define ptr @jnative_string_value_of_f32(float %v) {
                entry:
                  %d = fpext float %v to double
                  %r = call ptr @jnative_string_value_of_f64(double %d)
                  ret ptr %r
                }

                define double @jnative_parse_double(ptr %s) {
                entry:
                  call void @jnative_null_check(ptr %s)
                  %ulen = call i32 @jnative_string_utf8_length(ptr %s)
                  %len64 = sext i32 %ulen to i64
                  %n64 = add i64 %len64, 1
                  %buf = call ptr @malloc(i64 %n64)
                  %enc = call i32 @jnative_string_encode_utf8(ptr %s, ptr %buf, i32 %ulen)
                  %nul = getelementptr i8, ptr %buf, i32 %ulen
                  store i8 0, ptr %nul, align 1
                  %end = alloca ptr, align 8
                  store ptr null, ptr %end, align 8
                  %v = call double @strtod(ptr %buf, ptr %end)
                  %ep = load ptr, ptr %end, align 8
                  %consumed = ptrtoint ptr %ep to i64
                  %base = ptrtoint ptr %buf to i64
                  %diff = sub i64 %consumed, %base
                  call void @free(ptr %buf)
                  %ok = icmp eq i64 %diff, %len64
                  br i1 %ok, label %ret, label %bad
                bad:
                  call void @jnative_throw_illegal_argument()
                  ret double 0.0
                ret:
                  ret double %v
                }

                """);
        // Format without trailing newline for valueOf.
        emitCString(".fmt.double.raw", "%g");
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
