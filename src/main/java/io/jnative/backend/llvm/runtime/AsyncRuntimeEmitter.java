package io.jnative.backend.llvm.runtime;

/**
 * Cooperative async/await runtime stubs (JNative 0.23).
 *
 * <p>These symbols are intentionally minimal: they register continuations without blocking
 * the event loop. Production scheduling will integrate with {@code jnative_ui} invokeLater /
 * a dedicated executor in a future release.
 */
public final class AsyncRuntimeEmitter {
    private final StringBuilder sb;

    public AsyncRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("; --- Async/await runtime (0.23) ---\n");
        sb.append("""
                ; AsyncFrame GC object: { meta*, i32 state, ptr awaitResult, ptr pendingFuture }
                ; state 0 = initial entry; N = resume after await site N-1

                ; Stub: allocate AsyncFrame with state=0
                define ptr @jnative_async_begin() {
                entry:
                  %f = call ptr @jnative_alloc_zeroed(i64 32)
                  ret ptr %f
                }

                define i32 @jnative_async_get_state(ptr %frame) {
                entry:
                  %p = getelementptr i8, ptr %frame, i64 8
                  %s = load i32, ptr %p, align 4
                  ret i32 %s
                }

                define void @jnative_async_suspend(ptr %frame, i32 %state, ptr %future) {
                entry:
                  %sp = getelementptr i8, ptr %frame, i64 8
                  store i32 %state, ptr %sp, align 4
                  %fp = getelementptr i8, ptr %frame, i64 24
                  store ptr %future, ptr %fp, align 8
                  ret void
                }

                ; Cooperative await: record state; caller returns pending future (no blocking wait)
                define void @jnative_async_awaitCooperative(ptr %future, ptr %frame, i32 %state) {
                entry:
                  call void @jnative_async_suspend(ptr %frame, i32 %state, ptr %future)
                  ret void
                }

                define i1 @jnative_future_isComplete(ptr %future) {
                entry:
                  %p = getelementptr i8, ptr %future, i64 8
                  %s = load i32, ptr %p, align 4
                  %c = icmp ne i32 %s, 0
                  ret i1 %c
                }

                define ptr @jnative_future_getResult(ptr %future) {
                entry:
                  %p = getelementptr i8, ptr %future, i64 16
                  %r = load ptr, ptr %p, align 8
                  ret ptr %r
                }

                define ptr @jnative_future_completed(ptr %value) {
                entry:
                  %f = call ptr @jnative_alloc_zeroed(i64 32)
                  %sp = getelementptr i8, ptr %f, i64 8
                  store i32 1, ptr %sp, align 4
                  %rp = getelementptr i8, ptr %f, i64 16
                  store ptr %value, ptr %rp, align 8
                  ret ptr %f
                }

                define ptr @jnative_async_load_await_result(ptr %frame, i32 %slot) {
                entry:
                  %rp = getelementptr i8, ptr %frame, i64 16
                  %r = load ptr, ptr %rp, align 8
                  ret ptr %r
                }

                define void @jnative_async_fail(ptr %ex) {
                entry:
                  call void @jnative_exception_throw(ptr %ex)
                  ret void
                }

                """);
    }
}
