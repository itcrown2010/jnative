/*
 * Copyright (c) 2026 张晓达
 * SPDX-License-Identifier: MIT
 */

package io.jnative.backend.llvm.runtime;

/**
 * Cooperative async/await runtime (reliable suspend/resume).
 *
 * <p>Future layout (matches builtin {@code Future} fields + extension):
 * {@code { meta*@0, i32 state@8, pad, ptr result@16, ptr error@24, ptr cont@32 }}
 * state: 0=PENDING 1=SUCCESS 2=FAILED 3=CANCELLED.
 *
 * <p>AsyncFrame layout:
 * {@code { meta*@0, i32 state@8, i32 flags@12, ptr resultFuture@16, ptr awaitResult@24,
 * ptr awaitError@32, ptr resumeFn@40, ptr nextLive@48,
 * [16 x ptr] refSlots@56, [16 x i64] scalarSlots@184 }}
 *
 * <p>Continuations are registered on the awaited Future; completion schedules
 * {@code resumeFn(frame)} via a drain queue (avoids recursive stack blow-up).
 * @author 张晓达
 */
public final class AsyncRuntimeEmitter {
    private final StringBuilder sb;

    public AsyncRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("; --- Async/await runtime (suspend/resume) ---\n");
        sb.append("""
                @jn.async.live_frames = global ptr null
                @jn.async.resume_tls = thread_local global ptr null
                @jn.async.drain_head = global ptr null
                @jn.async.drain_tail = global ptr null
                @jn.async.draining = global i32 0

                ; Cont node: { ptr frame@0, ptr resumeFn@8, ptr next@16 }
                ; Drain node: { ptr frame@0, ptr resumeFn@8, ptr next@16 }

                define void @jnative_async_visit_future_conts(ptr %future, ptr %fn, ptr %ctx) {
                entry:
                  %nullf = icmp eq ptr %future, null
                  br i1 %nullf, label %ret, label %go
                go:
                  %cp = getelementptr i8, ptr %future, i64 32
                  call void %fn(ptr %cp, ptr %ctx)
                  %head = load ptr, ptr %cp, align 8
                  br label %loop
                loop:
                  %c = phi ptr [ %head, %go ], [ %n, %body ]
                  %isnull = icmp eq ptr %c, null
                  br i1 %isnull, label %ret, label %body
                body:
                  call void %fn(ptr %c, ptr %ctx)
                  %fp = getelementptr i8, ptr %c, i64 0
                  call void %fn(ptr %fp, ptr %ctx)
                  %np = getelementptr i8, ptr %c, i64 16
                  call void %fn(ptr %np, ptr %ctx)
                  %n = load ptr, ptr %np, align 8
                  br label %loop
                ret:
                  ret void
                }

                define void @jnative_async_visit_roots(ptr %fn, ptr %ctx) {
                entry:
                  call void %fn(ptr @jn.async.live_frames, ptr %ctx)
                  %head = load ptr, ptr @jn.async.live_frames, align 8
                  br label %walk
                walk:
                  %f = phi ptr [ %head, %entry ], [ %nf, %slots.done ]
                  %isnull = icmp eq ptr %f, null
                  br i1 %isnull, label %drain_roots, label %body
                body:
                  %rfp = getelementptr i8, ptr %f, i64 16
                  call void %fn(ptr %rfp, ptr %ctx)
                  %arp = getelementptr i8, ptr %f, i64 24
                  call void %fn(ptr %arp, ptr %ctx)
                  %aep = getelementptr i8, ptr %f, i64 32
                  call void %fn(ptr %aep, ptr %ctx)
                  %np = getelementptr i8, ptr %f, i64 48
                  call void %fn(ptr %np, ptr %ctx)
                  %rfv = load ptr, ptr %rfp, align 8
                  call void @jnative_async_visit_future_conts(ptr %rfv, ptr %fn, ptr %ctx)
                  %afv = load ptr, ptr %aep, align 8
                  call void @jnative_async_visit_future_conts(ptr %afv, ptr %fn, ptr %ctx)
                  %slots = getelementptr i8, ptr %f, i64 56
                  br label %slots.loop
                slots.loop:
                  %si = phi i64 [ 0, %body ], [ %si2, %slots.body ]
                  %sdone = icmp eq i64 %si, 16
                  br i1 %sdone, label %slots.done, label %slots.body
                slots.body:
                  %sp = getelementptr ptr, ptr %slots, i64 %si
                  call void %fn(ptr %sp, ptr %ctx)
                  %si2 = add i64 %si, 1
                  br label %slots.loop
                slots.done:
                  %nf = load ptr, ptr %np, align 8
                  br label %walk
                drain_roots:
                  call void %fn(ptr @jn.async.drain_head, ptr %ctx)
                  call void %fn(ptr @jn.async.drain_tail, ptr %ctx)
                  ret void
                }

                define ptr @jnative_future_pending() {
                entry:
                  %f = call ptr @jnative_alloc_zeroed(i64 40)
                  ; state already 0 (PENDING)
                  ret ptr %f
                }

                define ptr @jnative_future_completed(ptr %value) {
                entry:
                  %f = call ptr @jnative_alloc_zeroed(i64 40)
                  %sp = getelementptr i8, ptr %f, i64 8
                  store i32 1, ptr %sp, align 4
                  %rp = getelementptr i8, ptr %f, i64 16
                  store ptr %value, ptr %rp, align 8
                  ret ptr %f
                }

                define ptr @jnative_future_failed(ptr %error) {
                entry:
                  %f = call ptr @jnative_alloc_zeroed(i64 40)
                  %sp = getelementptr i8, ptr %f, i64 8
                  store i32 2, ptr %sp, align 4
                  %ep = getelementptr i8, ptr %f, i64 24
                  store ptr %error, ptr %ep, align 8
                  ret ptr %f
                }

                define i1 @jnative_future_isComplete(ptr %future) {
                entry:
                  %nullf = icmp eq ptr %future, null
                  br i1 %nullf, label %no, label %load
                no:
                  ret i1 false
                load:
                  %p = getelementptr i8, ptr %future, i64 8
                  %s = load i32, ptr %p, align 4
                  %c = icmp ne i32 %s, 0
                  ret i1 %c
                }

                define i32 @jnative_future_get_state(ptr %future) {
                entry:
                  %p = getelementptr i8, ptr %future, i64 8
                  %s = load i32, ptr %p, align 4
                  ret i32 %s
                }

                define ptr @jnative_future_getResult(ptr %future) {
                entry:
                  %p = getelementptr i8, ptr %future, i64 16
                  %r = load ptr, ptr %p, align 8
                  ret ptr %r
                }

                define ptr @jnative_future_getError(ptr %future) {
                entry:
                  %p = getelementptr i8, ptr %future, i64 24
                  %r = load ptr, ptr %p, align 8
                  ret ptr %r
                }

                define void @jnative_future_fire_conts(ptr %future) {
                entry:
                  %cp = getelementptr i8, ptr %future, i64 32
                  %head = load ptr, ptr %cp, align 8
                  store ptr null, ptr %cp, align 8
                  br label %loop
                loop:
                  %c = phi ptr [ %head, %entry ], [ %next, %body ]
                  %isnull = icmp eq ptr %c, null
                  br i1 %isnull, label %done, label %body
                body:
                  %fp = getelementptr i8, ptr %c, i64 0
                  %frame = load ptr, ptr %fp, align 8
                  %rfp = getelementptr i8, ptr %c, i64 8
                  %rfn = load ptr, ptr %rfp, align 8
                  %np = getelementptr i8, ptr %c, i64 16
                  %next = load ptr, ptr %np, align 8
                  call void @jnative_async_schedule(ptr %frame, ptr %rfn)
                  br label %loop
                done:
                  ret void
                }

                define i1 @jnative_future_try_complete(ptr %future, ptr %value) {
                entry:
                  %sp = getelementptr i8, ptr %future, i64 8
                  %s = load i32, ptr %sp, align 4
                  %pend = icmp eq i32 %s, 0
                  br i1 %pend, label %do, label %fail
                fail:
                  ret i1 false
                do:
                  store i32 1, ptr %sp, align 4
                  %rp = getelementptr i8, ptr %future, i64 16
                  store ptr %value, ptr %rp, align 8
                  call void @jnative_future_fire_conts(ptr %future)
                  ret i1 true
                }

                define i1 @jnative_future_try_fail(ptr %future, ptr %error) {
                entry:
                  %sp = getelementptr i8, ptr %future, i64 8
                  %s = load i32, ptr %sp, align 4
                  %pend = icmp eq i32 %s, 0
                  br i1 %pend, label %do, label %fail
                fail:
                  ret i1 false
                do:
                  store i32 2, ptr %sp, align 4
                  %ep = getelementptr i8, ptr %future, i64 24
                  store ptr %error, ptr %ep, align 8
                  call void @jnative_future_fire_conts(ptr %future)
                  ret i1 true
                }

                define i1 @jnative_future_try_cancel(ptr %future) {
                entry:
                  %sp = getelementptr i8, ptr %future, i64 8
                  %s = load i32, ptr %sp, align 4
                  %pend = icmp eq i32 %s, 0
                  br i1 %pend, label %do, label %fail
                fail:
                  ret i1 false
                do:
                  store i32 3, ptr %sp, align 4
                  call void @jnative_future_fire_conts(ptr %future)
                  ret i1 true
                }

                define void @jnative_future_register_cont(ptr %future, ptr %frame, ptr %resumeFn) {
                entry:
                  %node = call ptr @jnative_alloc_zeroed(i64 24)
                  store ptr %frame, ptr %node, align 8
                  %rfp = getelementptr i8, ptr %node, i64 8
                  store ptr %resumeFn, ptr %rfp, align 8
                  %sp = getelementptr i8, ptr %future, i64 8
                  %s = load i32, ptr %sp, align 4
                  %done = icmp ne i32 %s, 0
                  br i1 %done, label %immediate, label %pend
                immediate:
                  ; Already complete — schedule without storing (race-safe with try_complete).
                  call void @jnative_async_schedule(ptr %frame, ptr %resumeFn)
                  ret void
                pend:
                  %cp = getelementptr i8, ptr %future, i64 32
                  %prev = load ptr, ptr %cp, align 8
                  %np = getelementptr i8, ptr %node, i64 16
                  store ptr %prev, ptr %np, align 8
                  store ptr %node, ptr %cp, align 8
                  ; Re-check completion race: complete may have fired with empty list.
                  %s2 = load i32, ptr %sp, align 4
                  %done2 = icmp ne i32 %s2, 0
                  br i1 %done2, label %race, label %ok
                race:
                  ; Remove our node if still head (best-effort) and schedule.
                  %head = load ptr, ptr %cp, align 8
                  %ishead = icmp eq ptr %head, %node
                  br i1 %ishead, label %unlink, label %sched
                unlink:
                  %nnext = load ptr, ptr %np, align 8
                  store ptr %nnext, ptr %cp, align 8
                  br label %sched
                sched:
                  call void @jnative_async_schedule(ptr %frame, ptr %resumeFn)
                  ret void
                ok:
                  ret void
                }

                define void @jnative_async_schedule(ptr %frame, ptr %resumeFn) {
                entry:
                  %node = call ptr @malloc(i64 24)
                  call ptr @memset(ptr %node, i32 0, i64 24)
                  store ptr %frame, ptr %node, align 8
                  %rfp = getelementptr i8, ptr %node, i64 8
                  store ptr %resumeFn, ptr %rfp, align 8
                  %tail = load ptr, ptr @jn.async.drain_tail, align 8
                  %tailnull = icmp eq ptr %tail, null
                  br i1 %tailnull, label %empty, label %append
                empty:
                  store ptr %node, ptr @jn.async.drain_head, align 8
                  store ptr %node, ptr @jn.async.drain_tail, align 8
                  br label %kick
                append:
                  %tnp = getelementptr i8, ptr %tail, i64 16
                  store ptr %node, ptr %tnp, align 8
                  store ptr %node, ptr @jn.async.drain_tail, align 8
                  br label %kick
                kick:
                  %d = load i32, ptr @jn.async.draining, align 4
                  %busy = icmp ne i32 %d, 0
                  br i1 %busy, label %ret, label %drain
                drain:
                  call void @jnative_async_drain()
                  br label %ret
                ret:
                  ret void
                }

                define void @jnative_async_drain() {
                entry:
                  %d0 = load i32, ptr @jn.async.draining, align 4
                  %busy = icmp ne i32 %d0, 0
                  br i1 %busy, label %ret, label %start
                start:
                  store i32 1, ptr @jn.async.draining, align 4
                  br label %loop
                loop:
                  %head = load ptr, ptr @jn.async.drain_head, align 8
                  %isnull = icmp eq ptr %head, null
                  br i1 %isnull, label %done, label %run
                run:
                  %frame = load ptr, ptr %head, align 8
                  %rfp = getelementptr i8, ptr %head, i64 8
                  %rfn = load ptr, ptr %rfp, align 8
                  %np = getelementptr i8, ptr %head, i64 16
                  %next = load ptr, ptr %np, align 8
                  store ptr %next, ptr @jn.async.drain_head, align 8
                  %nextnull = icmp eq ptr %next, null
                  br i1 %nextnull, label %clear_tail, label %call
                clear_tail:
                  store ptr null, ptr @jn.async.drain_tail, align 8
                  br label %call
                call:
                  call void @free(ptr %head)
                  %fnnull = icmp eq ptr %rfn, null
                  br i1 %fnnull, label %loop, label %invoke
                invoke:
                  ; Claim resume at most once.
                  %flagp = getelementptr i8, ptr %frame, i64 12
                  %old = load i32, ptr %flagp, align 4
                  %claimed = and i32 %old, 1
                  %already = icmp ne i32 %claimed, 0
                  br i1 %already, label %loop, label %claim
                claim:
                  %newf = or i32 %old, 1
                  store i32 %newf, ptr %flagp, align 4
                  ; Copy await outcome from pending future into frame if needed is done at suspend site.
                  call void %rfn(ptr %frame)
                  br label %loop
                done:
                  store i32 0, ptr @jn.async.draining, align 4
                  br label %ret
                ret:
                  ret void
                }

                define ptr @jnative_async_begin() {
                entry:
                  ; 56 header + 16*8 ref slots + 16*8 scalar slots = 312
                  %frame = call ptr @jnative_alloc_zeroed(i64 312)
                  %rf = call ptr @jnative_future_pending()
                  %rfp = getelementptr i8, ptr %frame, i64 16
                  store ptr %rf, ptr %rfp, align 8
                  ; link into live list
                  %head = load ptr, ptr @jn.async.live_frames, align 8
                  %np = getelementptr i8, ptr %frame, i64 48
                  store ptr %head, ptr %np, align 8
                  store ptr %frame, ptr @jn.async.live_frames, align 8
                  ret ptr %frame
                }

                define ptr @jnative_async_result_future(ptr %frame) {
                entry:
                  %p = getelementptr i8, ptr %frame, i64 16
                  %f = load ptr, ptr %p, align 8
                  ret ptr %f
                }

                define i32 @jnative_async_get_state(ptr %frame) {
                entry:
                  %p = getelementptr i8, ptr %frame, i64 8
                  %s = load i32, ptr %p, align 4
                  ret i32 %s
                }

                define void @jnative_async_set_state(ptr %frame, i32 %state) {
                entry:
                  %p = getelementptr i8, ptr %frame, i64 8
                  store i32 %state, ptr %p, align 4
                  ret void
                }

                define void @jnative_async_set_resume_fn(ptr %frame, ptr %fn) {
                entry:
                  %p = getelementptr i8, ptr %frame, i64 40
                  store ptr %fn, ptr %p, align 8
                  ret void
                }

                define ptr @jnative_async_get_resume_fn(ptr %frame) {
                entry:
                  %p = getelementptr i8, ptr %frame, i64 40
                  %f = load ptr, ptr %p, align 8
                  ret ptr %f
                }

                define void @jnative_async_save_slot(ptr %frame, i32 %idx, i64 %val) {
                entry:
                  ; scalar slots at offset 184
                  %base = getelementptr i8, ptr %frame, i64 184
                  %i64 = sext i32 %idx to i64
                  %p = getelementptr i64, ptr %base, i64 %i64
                  store i64 %val, ptr %p, align 8
                  ret void
                }

                define i64 @jnative_async_load_slot(ptr %frame, i32 %idx) {
                entry:
                  %base = getelementptr i8, ptr %frame, i64 184
                  %i64 = sext i32 %idx to i64
                  %p = getelementptr i64, ptr %base, i64 %i64
                  %v = load i64, ptr %p, align 8
                  ret i64 %v
                }

                define void @jnative_async_save_ptr_slot(ptr %frame, i32 %idx, ptr %val) {
                entry:
                  %base = getelementptr i8, ptr %frame, i64 56
                  %i64 = sext i32 %idx to i64
                  %p = getelementptr ptr, ptr %base, i64 %i64
                  store ptr %val, ptr %p, align 8
                  ret void
                }

                define ptr @jnative_async_load_ptr_slot(ptr %frame, i32 %idx) {
                entry:
                  %base = getelementptr i8, ptr %frame, i64 56
                  %i64 = sext i32 %idx to i64
                  %p = getelementptr ptr, ptr %base, i64 %i64
                  %v = load ptr, ptr %p, align 8
                  ret ptr %v
                }

                define void @jnative_async_set_resume(ptr %frame) {
                entry:
                  store ptr %frame, ptr @jn.async.resume_tls, align 8
                  ret void
                }

                define ptr @jnative_async_take_resume() {
                entry:
                  %f = load ptr, ptr @jn.async.resume_tls, align 8
                  store ptr null, ptr @jn.async.resume_tls, align 8
                  ret ptr %f
                }

                define void @jnative_async_suspend(ptr %frame, i32 %state, ptr %future) {
                entry:
                  call void @jnative_async_set_state(ptr %frame, i32 %state)
                  ; stash awaited future in awaitResult temporarily? use slot -1 area: store in awaitError? 
                  ; Store pending future pointer at awaitError slot until resume copies outcome.
                  %fp = getelementptr i8, ptr %frame, i64 32
                  store ptr %future, ptr %fp, align 8
                  ret void
                }

                define void @jnative_async_awaitCooperative(ptr %future, ptr %frame, i32 %state, ptr %resumeFn) {
                entry:
                  call void @jnative_async_suspend(ptr %frame, i32 %state, ptr %future)
                  call void @jnative_async_set_resume_fn(ptr %frame, ptr %resumeFn)
                  ; Clear claim bit so this suspend can resume once.
                  %flagp = getelementptr i8, ptr %frame, i64 12
                  store i32 0, ptr %flagp, align 4
                  call void @jnative_future_register_cont(ptr %future, ptr %frame, ptr %resumeFn)
                  ret void
                }

                ; Prepare frame awaitResult/awaitError from the suspended future, then resume body runs.
                define void @jnative_async_prepare_resume(ptr %frame) {
                entry:
                  %fp = getelementptr i8, ptr %frame, i64 32
                  %fut = load ptr, ptr %fp, align 8
                  %nullf = icmp eq ptr %fut, null
                  br i1 %nullf, label %ret, label %copy
                copy:
                  %st = call i32 @jnative_future_get_state(ptr %fut)
                  %ok = icmp eq i32 %st, 1
                  br i1 %ok, label %success, label %failish
                success:
                  %v = call ptr @jnative_future_getResult(ptr %fut)
                  %rp = getelementptr i8, ptr %frame, i64 24
                  store ptr %v, ptr %rp, align 8
                  store ptr null, ptr %fp, align 8
                  br label %ret
                failish:
                  %e = call ptr @jnative_future_getError(ptr %fut)
                  store ptr %e, ptr %fp, align 8
                  %rp2 = getelementptr i8, ptr %frame, i64 24
                  store ptr null, ptr %rp2, align 8
                  br label %ret
                ret:
                  ret void
                }

                define ptr @jnative_async_load_await_result(ptr %frame) {
                entry:
                  %rp = getelementptr i8, ptr %frame, i64 16
                  ; wait — result is at +24
                  %rp2 = getelementptr i8, ptr %frame, i64 24
                  %r = load ptr, ptr %rp2, align 8
                  ret ptr %r
                }

                define ptr @jnative_async_load_await_error(ptr %frame) {
                entry:
                  %ep = getelementptr i8, ptr %frame, i64 32
                  %e = load ptr, ptr %ep, align 8
                  ret ptr %e
                }

                define void @jnative_async_unlink(ptr %frame) {
                entry:
                  %headp = load ptr, ptr @jn.async.live_frames, align 8
                  %ishead = icmp eq ptr %headp, %frame
                  br i1 %ishead, label %unlink_head, label %scan
                unlink_head:
                  %np0 = getelementptr i8, ptr %frame, i64 48
                  %n0 = load ptr, ptr %np0, align 8
                  store ptr %n0, ptr @jn.async.live_frames, align 8
                  ret void
                scan:
                  br label %loop
                loop:
                  %prev = phi ptr [ @jn.async.live_frames, %scan ], [ %cur, %next ]
                  %curp = phi ptr [ @jn.async.live_frames, %scan ], [ %curnp, %next ]
                  %cur = load ptr, ptr %curp, align 8
                  %isnull = icmp eq ptr %cur, null
                  br i1 %isnull, label %ret, label %check
                check:
                  %eq = icmp eq ptr %cur, %frame
                  br i1 %eq, label %found, label %next
                found:
                  %fnp = getelementptr i8, ptr %frame, i64 48
                  %fn = load ptr, ptr %fnp, align 8
                  store ptr %fn, ptr %curp, align 8
                  br label %ret
                next:
                  %curnp = getelementptr i8, ptr %cur, i64 48
                  br label %loop
                ret:
                  ret void
                }

                define void @jnative_async_complete(ptr %frame, ptr %value) {
                entry:
                  %rfp = getelementptr i8, ptr %frame, i64 16
                  %rf = load ptr, ptr %rfp, align 8
                  %ok = call i1 @jnative_future_try_complete(ptr %rf, ptr %value)
                  call void @jnative_async_unlink(ptr %frame)
                  ret void
                }

                define void @jnative_async_complete_exception(ptr %frame, ptr %ex) {
                entry:
                  %rfp = getelementptr i8, ptr %frame, i64 16
                  %rf = load ptr, ptr %rfp, align 8
                  %ok = call i1 @jnative_future_try_fail(ptr %rf, ptr %ex)
                  call void @jnative_async_unlink(ptr %frame)
                  ret void
                }

                define void @jnative_async_fail(ptr %ex) {
                entry:
                  call void @jnative_exception_throw(ptr %ex)
                  ret void
                }

                define ptr @jnative_async_select_nonzero(ptr %a, ptr %b) {
                entry:
                  %anull = icmp eq ptr %a, null
                  br i1 %anull, label %useb, label %usea
                usea:
                  ret ptr %a
                useb:
                  ret ptr %b
                }

                define i1 @jnative_async_ptr_is_null(ptr %p) {
                entry:
                  %z = icmp eq ptr %p, null
                  ret i1 %z
                }

                define void @jnative_async_fail_await(ptr %frame, ptr %err) {
                entry:
                  call void @jnative_async_complete_exception(ptr %frame, ptr %err)
                  ret void
                }

                ; If err != null, fail the method Future and throw (no fall-through into resume body).
                define void @jnative_async_rethrow_await_error(ptr %frame, ptr %err) {
                entry:
                  %isnull = icmp eq ptr %err, null
                  br i1 %isnull, label %ok, label %fail
                fail:
                  call void @jnative_async_complete_exception(ptr %frame, ptr %err)
                  call void @jnative_exception_throw(ptr %err)
                  unreachable
                ok:
                  ret void
                }

                define i64 @jnative_async_zext_i32_to_i64(i32 %v) {
                entry:
                  %w = zext i32 %v to i64
                  ret i64 %w
                }

                define i64 @jnative_async_zext_i64_to_i64(i64 %v) {
                entry:
                  ret i64 %v
                }

                define i64 @jnative_async_zext_i1_to_i64(i1 %v) {
                entry:
                  %w = zext i1 %v to i64
                  ret i64 %w
                }

                define i64 @jnative_async_zext_i16_to_i64(i16 %v) {
                entry:
                  %w = zext i16 %v to i64
                  ret i64 %w
                }

                define i64 @jnative_async_zext_i8_to_i64(i8 %v) {
                entry:
                  %w = zext i8 %v to i64
                  ret i64 %w
                }

                define i32 @jnative_async_trunc_i32(i64 %v) {
                entry:
                  %t = trunc i64 %v to i32
                  ret i32 %t
                }

                define i64 @jnative_async_trunc_i64(i64 %v) {
                entry:
                  ret i64 %v
                }

                define i1 @jnative_async_trunc_i1(i64 %v) {
                entry:
                  %t = trunc i64 %v to i1
                  ret i1 %t
                }

                define i16 @jnative_async_trunc_i16(i64 %v) {
                entry:
                  %t = trunc i64 %v to i16
                  ret i16 %t
                }

                define i8 @jnative_async_trunc_i8(i64 %v) {
                entry:
                  %t = trunc i64 %v to i8
                  ret i8 %t
                }

                define i32 @jnative_async_unbox_i32(ptr %boxed) {
                entry:
                  ; Integer box layout: { meta*@0, i32 value@8 }
                  %p = getelementptr i8, ptr %boxed, i64 8
                  %v = load i32, ptr %p, align 4
                  ret i32 %v
                }

                define i64 @jnative_async_unbox_i64(ptr %boxed) {
                entry:
                  %p = getelementptr i8, ptr %boxed, i64 8
                  %v = load i64, ptr %p, align 8
                  ret i64 %v
                }

                define i1 @jnative_async_unbox_i1(ptr %boxed) {
                entry:
                  %p = getelementptr i8, ptr %boxed, i64 8
                  %v = load i32, ptr %p, align 4
                  %c = icmp ne i32 %v, 0
                  ret i1 %c
                }

                """);
    }
}
