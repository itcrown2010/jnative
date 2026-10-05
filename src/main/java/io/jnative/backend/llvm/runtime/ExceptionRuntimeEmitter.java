package io.jnative.backend.llvm.runtime;

/**
 * Explicit exception propagation runtime (0.6). Uses thread-ready global state.
 */
public final class ExceptionRuntimeEmitter {
    private final StringBuilder sb;

    public ExceptionRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("@jn.ex.current = global ptr null\n");
        sb.append("@jn.ex.pending = global i1 false\n");
        sb.append("@jn.ex.returnPending = global i1 false\n");
        sb.append("@jn.ex.returnValue = global i64 0\n");
        sb.append('\n');

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
                  store ptr %ex, ptr @jn.ex.current, align 8
                  store i1 true, ptr @jn.ex.pending, align 1
                  store i1 false, ptr @jn.ex.returnPending, align 1
                  ret void
                }

                """);
    }

    private void emitPending() {
        sb.append("""
                define i1 @jnative_exception_pending() {
                entry:
                  %p = load i1, ptr @jn.ex.pending, align 1
                  ret i1 %p
                }

                """);
    }

    private void emitTake() {
        sb.append("""
                define ptr @jnative_exception_take() {
                entry:
                  %ex = load ptr, ptr @jn.ex.current, align 8
                  store i1 false, ptr @jn.ex.pending, align 1
                  ret ptr %ex
                }

                """);
    }

    private void emitClear() {
        sb.append("""
                define void @jnative_exception_clear() {
                entry:
                  store ptr null, ptr @jn.ex.current, align 8
                  store i1 false, ptr @jn.ex.pending, align 1
                  ret void
                }

                """);
    }

    private void emitPeek() {
        sb.append("""
                define ptr @jnative_exception_peek() {
                entry:
                  %ex = load ptr, ptr @jn.ex.current, align 8
                  ret ptr %ex
                }

                """);
    }

    private void emitUncaught() {
        sb.append("""
                define void @jnative_exception_uncaught() {
                entry:
                  %ex = load ptr, ptr @jn.ex.current, align 8
                  call void @jnative_print_uncaught(ptr %ex)
                  call void @exit(i32 1)
                  unreachable
                }

                define void @jnative_print_uncaught(ptr %ex) {
                entry:
                  call i32 @puts(ptr @.jn.uncaught.prefix)
                  %meta = load ptr, ptr %ex, align 8
                  %nameField = getelementptr %jn.ClassMeta, ptr %meta, i32 0, i32 4
                  %name = load ptr, ptr %nameField, align 8
                  call i32 @puts(ptr %name)
                  %msgField = getelementptr i8, ptr %ex, i64 8
                  %msg = load ptr, ptr %msgField, align 8
                  %hasMsg = icmp ne ptr %msg, null
                  br i1 %hasMsg, label %printMsg, label %done
                printMsg:
                  call void @jnative_println_string(ptr %msg)
                  br label %done
                done:
                  ret void
                }

                """);
    }

    private void emitCompletion() {
        sb.append("""
                define void @jnative_completion_set_return_i64(i64 %v) {
                entry:
                  store i64 %v, ptr @jn.ex.returnValue, align 8
                  store i1 true, ptr @jn.ex.returnPending, align 1
                  ret void
                }

                define void @jnative_completion_set_return_ptr(ptr %v) {
                entry:
                  %bits = ptrtoint ptr %v to i64
                  call void @jnative_completion_set_return_i64(i64 %bits)
                  ret void
                }

                define i1 @jnative_completion_has_return() {
                entry:
                  %r = load i1, ptr @jn.ex.returnPending, align 1
                  ret i1 %r
                }

                define i64 @jnative_completion_take_return_i64() {
                entry:
                  %v = load i64, ptr @jn.ex.returnValue, align 8
                  store i1 false, ptr @jn.ex.returnPending, align 1
                  ret i64 %v
                }

                define ptr @jnative_completion_take_return_ptr() {
                entry:
                  %v = call i64 @jnative_completion_take_return_i64()
                  %p = inttoptr i64 %v to ptr
                  ret ptr %p
                }

                define void @jnative_completion_clear() {
                entry:
                  store i1 false, ptr @jn.ex.returnPending, align 1
                  store i64 0, ptr @jn.ex.returnValue, align 8
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

                define void @jnative_throw_npe() {
                entry:
                  call void @jnative_throw_simple(ptr @meta.NullPointerException, i64 16)
                  ret void
                }

                define void @jnative_throw_aioobe() {
                entry:
                  call void @jnative_throw_simple(ptr @meta.ArrayIndexOutOfBoundsException, i64 16)
                  ret void
                }

                define void @jnative_throw_negative_array_size() {
                entry:
                  call void @jnative_throw_simple(ptr @meta.NegativeArraySizeException, i64 16)
                  ret void
                }

                define void @jnative_throw_class_cast() {
                entry:
                  call void @jnative_throw_simple(ptr @meta.ClassCastException, i64 16)
                  ret void
                }

                define void @jnative_throw_arithmetic() {
                entry:
                  call void @jnative_throw_simple(ptr @meta.ArithmeticException, i64 16)
                  ret void
                }
                ; Exception object size is 16 bytes: ClassMetadata* + String message.

                """);
    }
}
