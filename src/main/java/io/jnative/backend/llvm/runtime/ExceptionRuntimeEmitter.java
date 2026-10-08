package io.jnative.backend.llvm.runtime;

/**
 * Explicit exception propagation (0.6/0.9). State is thread-local via {@code jn_tls_ex_*}.
 * Throwable layout: {@code { ClassMeta* @0, message* @8, cause* @16 }} (24 bytes).
 */
public final class ExceptionRuntimeEmitter {
    private final StringBuilder sb;

    /** Header + message + cause. */
    private static final int THROWABLE_SIZE = 24;

    public ExceptionRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("""
                declare ptr @jn_tls_ex_current()
                declare void @jn_tls_set_ex_current(ptr)
                declare i32 @jn_tls_ex_pending()
                declare void @jn_tls_set_ex_pending(i32)
                declare i32 @jn_tls_ex_return_pending()
                declare void @jn_tls_set_ex_return_pending(i32)
                declare i64 @jn_tls_ex_return_value()
                declare void @jn_tls_set_ex_return_value(i64)
                declare void @jn_ex_capture_stack()
                declare void @jn_ex_print_stack()

                """);
        emitThrow();
        emitPending();
        emitTake();
        emitClear();
        emitPeek();
        emitUncaught();
        emitCompletion();
        emitSimpleThrows();
    }

    private void emitThrow() {
        sb.append("""
                define void @jnative_exception_throw(ptr %ex) {
                entry:
                  %isnull = icmp eq ptr %ex, null
                  br i1 %isnull, label %npe, label %set
                npe:
                  call void @jnative_throw_npe()
                  ret void
                set:
                  call void @jn_ex_capture_stack()
                  call void @jn_tls_set_ex_current(ptr %ex)
                  call void @jn_tls_set_ex_pending(i32 1)
                  call void @jn_tls_set_ex_return_pending(i32 0)
                  ret void
                }

                """);
    }

    private void emitPending() {
        sb.append("""
                define i1 @jnative_exception_pending() {
                entry:
                  %p = call i32 @jn_tls_ex_pending()
                  %r = icmp ne i32 %p, 0
                  ret i1 %r
                }

                """);
    }

    private void emitTake() {
        sb.append("""
                define ptr @jnative_exception_take() {
                entry:
                  %ex = call ptr @jn_tls_ex_current()
                  call void @jn_tls_set_ex_pending(i32 0)
                  ret ptr %ex
                }

                """);
    }

    private void emitClear() {
        sb.append("""
                define void @jnative_exception_clear() {
                entry:
                  call void @jn_tls_set_ex_current(ptr null)
                  call void @jn_tls_set_ex_pending(i32 0)
                  ret void
                }

                """);
    }

    private void emitPeek() {
        sb.append("""
                define ptr @jnative_exception_peek() {
                entry:
                  %ex = call ptr @jn_tls_ex_current()
                  ret ptr %ex
                }

                """);
    }

    private void emitUncaught() {
        sb.append("""
                ; Stable empty hook for debugger breakpoints (no-op when not debugging).
                ; LLDB/CodeLLDB: `breakpoint set --name jnative_debugger_on_uncaught`
                define void @jnative_debugger_on_uncaught(ptr %ex) {
                entry:
                  ret void
                }

                define void @jnative_exception_uncaught() {
                entry:
                  %ex = call ptr @jn_tls_ex_current()
                  call void @jnative_debugger_on_uncaught(ptr %ex)
                  call void @jnative_print_uncaught(ptr %ex)
                  call void @exit(i32 1)
                  unreachable
                }

                define void @jnative_exception_uncaught_worker() {
                entry:
                  %ex = call ptr @jn_tls_ex_current()
                  call void @jnative_print_uncaught_worker(ptr %ex)
                  call void @jnative_exception_clear()
                  ret void
                }

                define void @jnative_print_uncaught(ptr %ex) {
                entry:
                  call i32 @puts(ptr @.jn.uncaught.prefix)
                  br label %body
                body:
                  %meta = load ptr, ptr %ex, align 8
                  %nameField = getelementptr %jn.ClassMeta, ptr %meta, i32 0, i32 4
                  %name = load ptr, ptr %nameField, align 8
                  call i32 @puts(ptr %name)
                  %msgField = getelementptr i8, ptr %ex, i64 8
                  %msg = load ptr, ptr %msgField, align 8
                  %hasMsg = icmp ne ptr %msg, null
                  br i1 %hasMsg, label %printMsg, label %stack
                printMsg:
                  call void @jnative_println_string(ptr %msg)
                  br label %stack
                stack:
                  call void @jn_ex_print_stack()
                  ret void
                }

                define void @jnative_print_uncaught_worker(ptr %ex) {
                entry:
                  call i32 @puts(ptr @.jn.uncaught.worker)
                  %isnull = icmp eq ptr %ex, null
                  br i1 %isnull, label %done, label %body
                body:
                  call void @jnative_print_uncaught(ptr %ex)
                  br label %done
                done:
                  ret void
                }

                """);
        emitCString(".jn.uncaught.worker", "Uncaught exception in worker thread:");
    }

    private void emitCompletion() {
        // Kind: 0=none, 1=return, 2=break, 3=continue. Target id for break/continue in return_value.
        sb.append("""
                define void @jnative_completion_set_return_i64(i64 %v) {
                entry:
                  call void @jn_tls_set_ex_return_value(i64 %v)
                  call void @jn_tls_set_ex_return_pending(i32 1)
                  ret void
                }

                define void @jnative_completion_set_return_ptr(ptr %v) {
                entry:
                  %bits = ptrtoint ptr %v to i64
                  call void @jnative_completion_set_return_i64(i64 %bits)
                  ret void
                }

                define void @jnative_completion_set_break(i32 %targetId) {
                entry:
                  %v = sext i32 %targetId to i64
                  call void @jn_tls_set_ex_return_value(i64 %v)
                  call void @jn_tls_set_ex_return_pending(i32 2)
                  ret void
                }

                define void @jnative_completion_set_continue(i32 %targetId) {
                entry:
                  %v = sext i32 %targetId to i64
                  call void @jn_tls_set_ex_return_value(i64 %v)
                  call void @jn_tls_set_ex_return_pending(i32 3)
                  ret void
                }

                define i1 @jnative_completion_has_return() {
                entry:
                  %p = call i32 @jn_tls_ex_return_pending()
                  %r = icmp eq i32 %p, 1
                  ret i1 %r
                }

                define i1 @jnative_completion_has_break() {
                entry:
                  %p = call i32 @jn_tls_ex_return_pending()
                  %r = icmp eq i32 %p, 2
                  ret i1 %r
                }

                define i1 @jnative_completion_has_continue() {
                entry:
                  %p = call i32 @jn_tls_ex_return_pending()
                  %r = icmp eq i32 %p, 3
                  ret i1 %r
                }

                define i64 @jnative_completion_take_return_i64() {
                entry:
                  %v = call i64 @jn_tls_ex_return_value()
                  call void @jn_tls_set_ex_return_pending(i32 0)
                  ret i64 %v
                }

                define ptr @jnative_completion_take_return_ptr() {
                entry:
                  %v = call i64 @jnative_completion_take_return_i64()
                  %p = inttoptr i64 %v to ptr
                  ret ptr %p
                }

                define i32 @jnative_completion_take_target() {
                entry:
                  %v = call i64 @jn_tls_ex_return_value()
                  call void @jn_tls_set_ex_return_pending(i32 0)
                  %t = trunc i64 %v to i32
                  ret i32 %t
                }

                define void @jnative_completion_clear() {
                entry:
                  call void @jn_tls_set_ex_return_pending(i32 0)
                  call void @jn_tls_set_ex_return_value(i64 0)
                  ret void
                }

                """);
    }

    private void emitSimpleThrows() {
        sb.append("""
                define void @jnative_throw_simple(ptr %meta, i64 %size) {
                entry:
                  %obj = call ptr @jnative_alloc_zeroed(i64 %size)
                  store ptr %meta, ptr %obj, align 8
                  call void @jnative_exception_throw(ptr %obj)
                  ret void
                }

                """);
        emitThrowSimple("jnative_throw_npe", "NullPointerException");
        emitThrowSimple("jnative_throw_aioobe", "ArrayIndexOutOfBoundsException");
        emitThrowSimple("jnative_throw_negative_array_size", "NegativeArraySizeException");
        emitThrowSimple("jnative_throw_class_cast", "ClassCastException");
        emitThrowSimple("jnative_throw_arithmetic", "ArithmeticException");
        emitThrowSimple("jnative_throw_illegal_monitor", "IllegalMonitorStateException");
        emitThrowSimple("jnative_throw_illegal_state", "IllegalStateException");
    }

    private void emitThrowSimple(String name, String meta) {
        sb.append("define void @").append(name).append("() {\n");
        sb.append("entry:\n");
        sb.append("  call void @jnative_throw_simple(ptr @meta.").append(meta)
                .append(", i64 ").append(THROWABLE_SIZE).append(")\n");
        sb.append("  ret void\n");
        sb.append("}\n\n");
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
