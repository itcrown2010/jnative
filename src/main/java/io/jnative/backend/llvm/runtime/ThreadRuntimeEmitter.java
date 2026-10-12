/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend.llvm.runtime;

/**
 * Thread / Mutex / Condition / Atomic runtime helpers (JNative 0.9).
 * Platform primitives live in {@code jnative_mt.c}; this emits LLVM wrappers.
 * @author 张晓达
 */
public final class ThreadRuntimeEmitter {
    private final StringBuilder sb;

    public ThreadRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("; --- Thread / sync / atomics runtime ---\n");
        sb.append("""
                declare i64 @jn_thread_start(ptr, ptr)
                declare void @jn_thread_join_id(i64)
                declare void @jn_thread_sleep_ms(i64)
                declare void @jn_thread_mark_terminated()
                declare i64 @jn_thread_id()
                declare ptr @jn_thread_current_obj()
                declare void @jn_thread_set_current_obj(ptr)
                declare void @jn_thread_join_remaining()
                declare i64 @jn_os_thread_count()
                declare ptr @jn_mutex_create()
                declare void @jn_mutex_destroy(ptr)
                declare void @jn_mutex_lock(ptr)
                declare void @jn_mutex_unlock(ptr)
                declare i32 @jn_mutex_trylock(ptr)
                declare i32 @jn_mutex_owner_check(ptr)
                declare ptr @jn_cond_create()
                declare void @jn_cond_destroy(ptr)
                declare void @jn_cond_wait(ptr, ptr)
                declare void @jn_cond_signal(ptr)
                declare void @jn_cond_broadcast(ptr)

                ; Thread object: { meta*, i64 nativeId, ptr target }
                ; Mutex object: { meta*, ptr handle }
                ; Condition object: { meta*, ptr handle }
                ; AtomicInt: { meta*, i32 value }
                ; AtomicLong: { meta*, i64 value }
                ; AtomicBool: { meta*, i8 value }

                define void @jnative_thread_entry(ptr %thread_obj) {
                entry:
                  call void @jn_thread_set_current_obj(ptr %thread_obj)
                  call void @jn_safepoint_poll()
                  %meta = load ptr, ptr %thread_obj, align 8
                  %vtField = getelementptr %jn.ClassMeta, ptr %meta, i32 0, i32 0
                  %vtable = load ptr, ptr %vtField, align 8
                  ; Object.equals=0, Object.hashCode=1, Thread.run=2
                  %slotAddr = getelementptr ptr, ptr %vtable, i64 2
                  %fn = load ptr, ptr %slotAddr, align 8
                  call void %fn(ptr %thread_obj)
                  %pending = call i1 @jnative_exception_pending()
                  br i1 %pending, label %uncaught, label %done
                uncaught:
                  call void @jnative_exception_uncaught_worker()
                  br label %done
                done:
                  call void @jn_thread_mark_terminated()
                  ret void
                }

                define void @jnative_thread_start(ptr %self) {
                entry:
                  %idp = getelementptr i8, ptr %self, i64 8
                  %id0 = load i64, ptr %idp, align 8
                  %started = icmp ne i64 %id0, 0
                  br i1 %started, label %twice, label %go
                twice:
                  call void @jnative_throw_illegal_state()
                  ret void
                go:
                  %tid = call i64 @jn_thread_start(ptr %self, ptr @jnative_thread_entry)
                  %fail = icmp slt i64 %tid, 0
                  br i1 %fail, label %err, label %ok
                err:
                  call void @jnative_throw_illegal_state()
                  ret void
                ok:
                  store i64 %tid, ptr %idp, align 8
                  ret void
                }

                define void @jnative_thread_join(ptr %self) {
                entry:
                  %idp = getelementptr i8, ptr %self, i64 8
                  %id = load i64, ptr %idp, align 8
                  call void @jn_thread_join_id(i64 %id)
                  ret void
                }

                define void @jnative_thread_sleep(i64 %ms) {
                entry:
                  call void @jn_thread_sleep_ms(i64 %ms)
                  ret void
                }

                define ptr @jnative_thread_current() {
                entry:
                  %obj0 = call ptr @jn_thread_current_obj()
                  %isnull = icmp eq ptr %obj0, null
                  br i1 %isnull, label %create, label %ret
                create:
                  %obj = call ptr @jnative_gc_alloc(i64 24)
                  store ptr @meta.Thread, ptr %obj, align 8
                  %idp = getelementptr i8, ptr %obj, i64 8
                  %tid = call i64 @jn_thread_id()
                  store i64 %tid, ptr %idp, align 8
                  call void @jn_thread_set_current_obj(ptr %obj)
                  ret ptr %obj
                ret:
                  ret ptr %obj0
                }

                define i64 @jnative_thread_id_of(ptr %self) {
                entry:
                  %idp = getelementptr i8, ptr %self, i64 8
                  %id = load i64, ptr %idp, align 8
                  %zero = icmp eq i64 %id, 0
                  br i1 %zero, label %selfid, label %ret
                selfid:
                  %cur = call i64 @jn_thread_id()
                  ret i64 %cur
                ret:
                  ret i64 %id
                }

                define void @jnative_mutex_init(ptr %self) {
                entry:
                  %h = call ptr @jn_mutex_create()
                  %hp = getelementptr i8, ptr %self, i64 8
                  store ptr %h, ptr %hp, align 8
                  ret void
                }

                define void @jnative_mutex_lock(ptr %self) {
                entry:
                  %hp = getelementptr i8, ptr %self, i64 8
                  %h = load ptr, ptr %hp, align 8
                  call void @jn_mutex_lock(ptr %h)
                  ret void
                }

                define void @jnative_mutex_unlock(ptr %self) {
                entry:
                  %hp = getelementptr i8, ptr %self, i64 8
                  %h = load ptr, ptr %hp, align 8
                  %ok = call i32 @jn_mutex_owner_check(ptr %h)
                  %bad = icmp eq i32 %ok, 0
                  br i1 %bad, label %err, label %go
                err:
                  call void @jnative_throw_illegal_monitor()
                  ret void
                go:
                  call void @jn_mutex_unlock(ptr %h)
                  ret void
                }

                define i1 @jnative_mutex_trylock(ptr %self) {
                entry:
                  %hp = getelementptr i8, ptr %self, i64 8
                  %h = load ptr, ptr %hp, align 8
                  %ok = call i32 @jn_mutex_trylock(ptr %h)
                  %v = icmp ne i32 %ok, 0
                  ret i1 %v
                }

                define void @jnative_cond_init(ptr %self) {
                entry:
                  %h = call ptr @jn_cond_create()
                  %hp = getelementptr i8, ptr %self, i64 8
                  store ptr %h, ptr %hp, align 8
                  ret void
                }

                define void @jnative_cond_await(ptr %self, ptr %mutex) {
                entry:
                  %cp = getelementptr i8, ptr %self, i64 8
                  %c = load ptr, ptr %cp, align 8
                  %mp = getelementptr i8, ptr %mutex, i64 8
                  %m = load ptr, ptr %mp, align 8
                  %ok = call i32 @jn_mutex_owner_check(ptr %m)
                  %bad = icmp eq i32 %ok, 0
                  br i1 %bad, label %err, label %go
                err:
                  call void @jnative_throw_illegal_monitor()
                  ret void
                go:
                  call void @jn_cond_wait(ptr %c, ptr %m)
                  ret void
                }

                define void @jnative_cond_signal(ptr %self) {
                entry:
                  %cp = getelementptr i8, ptr %self, i64 8
                  %c = load ptr, ptr %cp, align 8
                  call void @jn_cond_signal(ptr %c)
                  ret void
                }

                define void @jnative_cond_signal_all(ptr %self) {
                entry:
                  %cp = getelementptr i8, ptr %self, i64 8
                  %c = load ptr, ptr %cp, align 8
                  call void @jn_cond_broadcast(ptr %c)
                  ret void
                }

                define void @jnative_atomic_int_init(ptr %self, i32 %v) {
                entry:
                  %vp = getelementptr i8, ptr %self, i64 8
                  store atomic i32 %v, ptr %vp seq_cst, align 4
                  ret void
                }

                define i32 @jnative_atomic_int_get(ptr %self) {
                entry:
                  %vp = getelementptr i8, ptr %self, i64 8
                  %v = load atomic i32, ptr %vp seq_cst, align 4
                  ret i32 %v
                }

                define void @jnative_atomic_int_set(ptr %self, i32 %v) {
                entry:
                  %vp = getelementptr i8, ptr %self, i64 8
                  store atomic i32 %v, ptr %vp seq_cst, align 4
                  ret void
                }

                define i1 @jnative_atomic_int_cas(ptr %self, i32 %expected, i32 %update) {
                entry:
                  %vp = getelementptr i8, ptr %self, i64 8
                  %pair = cmpxchg ptr %vp, i32 %expected, i32 %update seq_cst seq_cst
                  %ok = extractvalue { i32, i1 } %pair, 1
                  ret i1 %ok
                }

                define i32 @jnative_atomic_int_get_and_inc(ptr %self) {
                entry:
                  %vp = getelementptr i8, ptr %self, i64 8
                  %old = atomicrmw add ptr %vp, i32 1 seq_cst
                  ret i32 %old
                }

                define i32 @jnative_atomic_int_inc_and_get(ptr %self) {
                entry:
                  %old = call i32 @jnative_atomic_int_get_and_inc(ptr %self)
                  %n = add i32 %old, 1
                  ret i32 %n
                }

                define void @jnative_atomic_long_init(ptr %self, i64 %v) {
                entry:
                  %vp = getelementptr i8, ptr %self, i64 8
                  store atomic i64 %v, ptr %vp seq_cst, align 8
                  ret void
                }

                define i64 @jnative_atomic_long_get(ptr %self) {
                entry:
                  %vp = getelementptr i8, ptr %self, i64 8
                  %v = load atomic i64, ptr %vp seq_cst, align 8
                  ret i64 %v
                }

                define void @jnative_atomic_long_set(ptr %self, i64 %v) {
                entry:
                  %vp = getelementptr i8, ptr %self, i64 8
                  store atomic i64 %v, ptr %vp seq_cst, align 8
                  ret void
                }

                define i1 @jnative_atomic_long_cas(ptr %self, i64 %expected, i64 %update) {
                entry:
                  %vp = getelementptr i8, ptr %self, i64 8
                  %pair = cmpxchg ptr %vp, i64 %expected, i64 %update seq_cst seq_cst
                  %ok = extractvalue { i64, i1 } %pair, 1
                  ret i1 %ok
                }

                define i64 @jnative_atomic_long_get_and_inc(ptr %self) {
                entry:
                  %vp = getelementptr i8, ptr %self, i64 8
                  %old = atomicrmw add ptr %vp, i64 1 seq_cst
                  ret i64 %old
                }

                define i64 @jnative_atomic_long_inc_and_get(ptr %self) {
                entry:
                  %old = call i64 @jnative_atomic_long_get_and_inc(ptr %self)
                  %n = add i64 %old, 1
                  ret i64 %n
                }

                define void @jnative_atomic_bool_init(ptr %self, i1 %v) {
                entry:
                  %vp = getelementptr i8, ptr %self, i64 8
                  %b = zext i1 %v to i8
                  store atomic i8 %b, ptr %vp seq_cst, align 1
                  ret void
                }

                define i1 @jnative_atomic_bool_get(ptr %self) {
                entry:
                  %vp = getelementptr i8, ptr %self, i64 8
                  %b = load atomic i8, ptr %vp seq_cst, align 1
                  %v = icmp ne i8 %b, 0
                  ret i1 %v
                }

                define void @jnative_atomic_bool_set(ptr %self, i1 %v) {
                entry:
                  %vp = getelementptr i8, ptr %self, i64 8
                  %b = zext i1 %v to i8
                  store atomic i8 %b, ptr %vp seq_cst, align 1
                  ret void
                }

                define i1 @jnative_atomic_bool_cas(ptr %self, i1 %expected, i1 %update) {
                entry:
                  %vp = getelementptr i8, ptr %self, i64 8
                  %e = zext i1 %expected to i8
                  %u = zext i1 %update to i8
                  %pair = cmpxchg ptr %vp, i8 %e, i8 %u seq_cst seq_cst
                  %ok = extractvalue { i8, i1 } %pair, 1
                  ret i1 %ok
                }

                """);
    }
}
