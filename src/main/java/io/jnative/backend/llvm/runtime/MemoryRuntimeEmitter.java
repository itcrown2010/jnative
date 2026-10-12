/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend.llvm.runtime;

/** Memory + failure abstraction. Allocation is provided by GcRuntimeEmitter.
 * @author 张晓达
 */
public final class MemoryRuntimeEmitter {
    private final StringBuilder sb;

    public MemoryRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("""
                define void @jnative_runtime_error(ptr %msg) {
                entry:
                  call i32 @puts(ptr %msg)
                  call void @exit(i32 1)
                  unreachable
                }

                define void @jnative_null_check(ptr %p) {
                entry:
                  %isnull = icmp eq ptr %p, null
                  br i1 %isnull, label %trap, label %ok
                trap:
                  call void @jnative_throw_npe()
                  ret void
                ok:
                  ret void
                }

                """);
    }
}
