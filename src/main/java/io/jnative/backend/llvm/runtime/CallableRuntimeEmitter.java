package io.jnative.backend.llvm.runtime;

/**
 * Managed callable invoke + native callback registration (JNative 0.10).
 * @author 张晓达
 */
public final class CallableRuntimeEmitter {
    private final StringBuilder sb;

    public CallableRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("; --- Callable / native callback runtime ---\n");
        sb.append("""
                declare i32 @jn_thread_attach_foreign()
                declare void @jn_thread_detach_foreign()

                ; SAM layout: { meta*, i64 code, ptr env, ... }
                ; code field at byte offset 8, env at offset 16

                define void @jnative_invoke_void(ptr %callable) {
                entry:
                  %codep = getelementptr i8, ptr %callable, i64 8
                  %codei = load i64, ptr %codep, align 8
                  %code = inttoptr i64 %codei to ptr
                  %envp = getelementptr i8, ptr %callable, i64 16
                  %env = load ptr, ptr %envp, align 8
                  call void %code(ptr %env)
                  ret void
                }

                define ptr @jnative_invoke_ptr(ptr %callable) {
                entry:
                  %codep = getelementptr i8, ptr %callable, i64 8
                  %codei = load i64, ptr %codep, align 8
                  %code = inttoptr i64 %codei to ptr
                  %envp = getelementptr i8, ptr %callable, i64 16
                  %env = load ptr, ptr %envp, align 8
                  %r = call ptr %code(ptr %env)
                  ret ptr %r
                }

                define i32 @jnative_invoke_i32_i32(ptr %callable, i32 %arg0) {
                entry:
                  %codep = getelementptr i8, ptr %callable, i64 8
                  %codei = load i64, ptr %codep, align 8
                  %code = inttoptr i64 %codei to ptr
                  %envp = getelementptr i8, ptr %callable, i64 16
                  %env = load ptr, ptr %envp, align 8
                  %r = call i32 %code(ptr %env, i32 %arg0)
                  ret i32 %r
                }

                define i32 @jnative_invoke_i32_i32_i32(ptr %callable, i32 %arg0, i32 %arg1) {
                entry:
                  %codep = getelementptr i8, ptr %callable, i64 8
                  %codei = load i64, ptr %codep, align 8
                  %code = inttoptr i64 %codei to ptr
                  %envp = getelementptr i8, ptr %callable, i64 16
                  %env = load ptr, ptr %envp, align 8
                  %r = call i32 %code(ptr %env, i32 %arg0, i32 %arg1)
                  ret i32 %r
                }

                define void @jnative_invoke_void_ptr(ptr %callable, ptr %arg0) {
                entry:
                  %codep = getelementptr i8, ptr %callable, i64 8
                  %codei = load i64, ptr %codep, align 8
                  %code = inttoptr i64 %codei to ptr
                  %envp = getelementptr i8, ptr %callable, i64 16
                  %env = load ptr, ptr %envp, align 8
                  call void %code(ptr %env, ptr %arg0)
                  ret void
                }

                define i32 @jnative_invoke_i32_ptr(ptr %callable, ptr %arg0) {
                entry:
                  %codep = getelementptr i8, ptr %callable, i64 8
                  %codei = load i64, ptr %codep, align 8
                  %code = inttoptr i64 %codei to ptr
                  %envp = getelementptr i8, ptr %callable, i64 16
                  %env = load ptr, ptr %envp, align 8
                  %r = call i32 %code(ptr %env, ptr %arg0)
                  ret i32 %r
                }

                define i32 @jnative_invoke_i32_ptr_ptr(ptr %callable, ptr %arg0, ptr %arg1) {
                entry:
                  %codep = getelementptr i8, ptr %callable, i64 8
                  %codei = load i64, ptr %codep, align 8
                  %code = inttoptr i64 %codei to ptr
                  %envp = getelementptr i8, ptr %callable, i64 16
                  %env = load ptr, ptr %envp, align 8
                  %r = call i32 %code(ptr %env, ptr %arg0, ptr %arg1)
                  ret i32 %r
                }

                define void @jnative_invoke_void_ptr_ptr(ptr %callable, ptr %arg0, ptr %arg1) {
                entry:
                  %codep = getelementptr i8, ptr %callable, i64 8
                  %codei = load i64, ptr %codep, align 8
                  %code = inttoptr i64 %codei to ptr
                  %envp = getelementptr i8, ptr %callable, i64 16
                  %env = load ptr, ptr %envp, align 8
                  call void %code(ptr %env, ptr %arg0, ptr %arg1)
                  ret void
                }

                define ptr @jnative_invoke_ptr_ptr(ptr %callable, ptr %arg0) {
                entry:
                  %codep = getelementptr i8, ptr %callable, i64 8
                  %codei = load i64, ptr %codep, align 8
                  %code = inttoptr i64 %codei to ptr
                  %envp = getelementptr i8, ptr %callable, i64 16
                  %env = load ptr, ptr %envp, align 8
                  %r = call ptr %code(ptr %env, ptr %arg0)
                  ret ptr %r
                }

                """);
    }
}
