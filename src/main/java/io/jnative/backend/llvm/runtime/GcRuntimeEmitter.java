package io.jnative.backend.llvm.runtime;

/**
 * Precise stop-the-world mark-sweep GC (0.5 / 0.9 multi-thread).
 *
 * <pre>
 * Allocation: [ GcHeader 24B | Object payload... ]
 * Java refs point at payload (ClassMetadata*), not GcHeader.
 *
 * Non-moving, precise roots via per-thread shadow stacks (TLS).
 * STW coordinates all mutators via jn_gc_stw_*; heap list locked.
 * </pre>
 */
public final class GcRuntimeEmitter {
    private final StringBuilder sb;

    public static final int GC_HEADER_SIZE = 24;

    public GcRuntimeEmitter(StringBuilder sb) {
        this.sb = sb;
    }

    public void emit() {
        sb.append("""
                ; === JNative GC 0.5 ===
                %jn.GcHeader = type { ptr, i64, i32, i32 }

                declare ptr @jn_tls_frame()
                declare void @jn_tls_set_frame(ptr)
                declare void @jn_safepoint_poll()
                declare void @jn_heap_lock()
                declare void @jn_heap_unlock()
                declare void @jn_gc_stw_begin()
                declare void @jn_gc_stw_end()
                declare void @jn_gc_collect_lock()
                declare void @jn_gc_collect_unlock()
                declare void @jn_foreach_root_slot(ptr, ptr)
                declare void @jn_foreach_ex_root(ptr, ptr)
                declare void @jn_foreach_thread_obj_root(ptr, ptr)
                declare void @jn_thread_attach_main()
                declare void @jn_enter_native()
                declare void @jn_leave_native()

                @jn.gc.heap_head = global ptr null, align 8
                @jn.gc.allocated_bytes = global i64 0, align 8
                @jn.gc.live_bytes = global i64 0, align 8
                @jn.gc.threshold = global i64 1048576, align 8
                @jn.gc.collections = global i64 0, align 8
                @jn.gc.allocated_objects = global i64 0, align 8
                @jn.gc.freed_objects = global i64 0, align 8
                @jn.gc.live_objects = global i64 0, align 8
                @jn.gc.peak_bytes = global i64 0, align 8
                @jn.gc.stress = global i32 0, align 4
                @jn.gc.stats = global i32 0, align 4
                @jn.gc.mark_stack = global ptr null, align 8
                @jn.gc.mark_size = global i64 0, align 8
                @jn.gc.mark_cap = global i64 0, align 8
                @jn.gc.inited = global i32 0, align 4

                @.jn.gc.env.thr = private unnamed_addr constant [21 x i8] c"JNATIVE_GC_THRESHOLD\\00"
                @.jn.gc.env.stress = private unnamed_addr constant [18 x i8] c"JNATIVE_GC_STRESS\\00"
                @.jn.gc.env.stats = private unnamed_addr constant [17 x i8] c"JNATIVE_GC_STATS\\00"
                @.jn.gc.stats.hdr = private unnamed_addr constant [13 x i8] c"JNative GC:\\0A\\00"
                @.jn.gc.stats.fmt = private unnamed_addr constant [69 x i8] c"collections: %lld\\0Aallocated: %lld\\0Afreed: %lld\\0Alive: %lld\\0Apeak: %lld\\0A\\00"

                define ptr @jnative_gc_object_to_header(ptr %obj) {
                entry:
                  %h = getelementptr i8, ptr %obj, i64 -24
                  ret ptr %h
                }

                define ptr @jnative_gc_header_to_object(ptr %hdr) {
                entry:
                  %o = getelementptr i8, ptr %hdr, i64 24
                  ret ptr %o
                }

                define void @jnative_gc_mark_push(ptr %obj) {
                entry:
                  %isnull = icmp eq ptr %obj, null
                  br i1 %isnull, label %done, label %check
                done:
                  ret void
                check:
                  %hdr = call ptr @jnative_gc_object_to_header(ptr %obj)
                  %mp = getelementptr %jn.GcHeader, ptr %hdr, i32 0, i32 2
                  %m = load i32, ptr %mp, align 4
                  %already = icmp ne i32 %m, 0
                  br i1 %already, label %done2, label %mark
                done2:
                  ret void
                mark:
                  store i32 1, ptr %mp, align 4
                  %sz = load i64, ptr @jn.gc.mark_size, align 8
                  %cap = load i64, ptr @jn.gc.mark_cap, align 8
                  %need = icmp uge i64 %sz, %cap
                  br i1 %need, label %grow, label %push
                grow:
                  %old = load ptr, ptr @jn.gc.mark_stack, align 8
                  %newcap = mul i64 %cap, 2
                  %iszero = icmp eq i64 %cap, 0
                  %nc = select i1 %iszero, i64 64, i64 %newcap
                  %nbytes = mul i64 %nc, 8
                  %neu = call ptr @realloc(ptr %old, i64 %nbytes)
                  store ptr %neu, ptr @jn.gc.mark_stack, align 8
                  store i64 %nc, ptr @jn.gc.mark_cap, align 8
                  br label %push
                push:
                  %stack = load ptr, ptr @jn.gc.mark_stack, align 8
                  %slot = getelementptr ptr, ptr %stack, i64 %sz
                  store ptr %obj, ptr %slot, align 8
                  %sz2 = add i64 %sz, 1
                  store i64 %sz2, ptr @jn.gc.mark_size, align 8
                  ret void
                }

                define void @jnative_gc_scan_object(ptr %obj) {
                entry:
                  %meta = load ptr, ptr %obj, align 8
                  %isnull = icmp eq ptr %meta, null
                  br i1 %isnull, label %done, label %flags
                done:
                  ret void
                flags:
                  %fp = getelementptr { ptr, ptr, i64, i32, ptr, i32, ptr }, ptr %meta, i32 0, i32 3
                  %fl = load i32, ptr %fp, align 4
                  %isarr = and i32 %fl, 1
                  %isarrb = icmp ne i32 %isarr, 0
                  br i1 %isarrb, label %array, label %fields
                array:
                  %re = and i32 %fl, 2
                  %reb = icmp ne i32 %re, 0
                  br i1 %reb, label %arrelems, label %done2
                done2:
                  ret void
                arrelems:
                  %lp = getelementptr i8, ptr %obj, i64 8
                  %len = load i32, ptr %lp, align 4
                  %base = getelementptr i8, ptr %obj, i64 16
                  br label %aloop
                aloop:
                  %i = phi i32 [ 0, %arrelems ], [ %inext, %abody ]
                  %cmp = icmp slt i32 %i, %len
                  br i1 %cmp, label %abody, label %adone
                abody:
                  %i64 = sext i32 %i to i64
                  %ep = getelementptr ptr, ptr %base, i64 %i64
                  %child = load ptr, ptr %ep, align 8
                  call void @jnative_gc_mark_push(ptr %child)
                  %inext = add i32 %i, 1
                  br label %aloop
                adone:
                  ret void
                fields:
                  %cp = getelementptr { ptr, ptr, i64, i32, ptr, i32, ptr }, ptr %meta, i32 0, i32 5
                  %cnt = load i32, ptr %cp, align 4
                  %op = getelementptr { ptr, ptr, i64, i32, ptr, i32, ptr }, ptr %meta, i32 0, i32 6
                  %offs = load ptr, ptr %op, align 8
                  %noffs = icmp eq ptr %offs, null
                  %nocnt = icmp eq i32 %cnt, 0
                  %skip = or i1 %noffs, %nocnt
                  br i1 %skip, label %done3, label %floop
                done3:
                  ret void
                floop:
                  %fi = phi i32 [ 0, %fields ], [ %finext, %fbody ]
                  %fcmp = icmp slt i32 %fi, %cnt
                  br i1 %fcmp, label %fbody, label %fdone
                fbody:
                  %fi64 = sext i32 %fi to i64
                  %offp = getelementptr i64, ptr %offs, i64 %fi64
                  %off = load i64, ptr %offp, align 8
                  %fp2 = getelementptr i8, ptr %obj, i64 %off
                  %child2 = load ptr, ptr %fp2, align 8
                  call void @jnative_gc_mark_push(ptr %child2)
                  %finext = add i32 %fi, 1
                  br label %floop
                fdone:
                  ret void
                }

                define void @jnative_gc_mark_slot_cb(ptr %slot, ptr %ctx) {
                entry:
                  %snull = icmp eq ptr %slot, null
                  br i1 %snull, label %done, label %load
                load:
                  %ref = load ptr, ptr %slot, align 8
                  call void @jnative_gc_mark_push(ptr %ref)
                  br label %done
                done:
                  ret void
                }

                define void @jnative_gc_mark_obj_cb(ptr %obj, ptr %ctx) {
                entry:
                  call void @jnative_gc_mark_push(ptr %obj)
                  ret void
                }

                define void @jnative_gc_mark_from_roots() {
                entry:
                  store i64 0, ptr @jn.gc.mark_size, align 8
                  call void @jn_foreach_root_slot(ptr @jnative_gc_mark_slot_cb, ptr null)
                  call void @jn_foreach_ex_root(ptr @jnative_gc_mark_obj_cb, ptr null)
                  call void @jn_foreach_thread_obj_root(ptr @jnative_gc_mark_obj_cb, ptr null)
                  call void @jnative_mark_static_roots()
                  br label %work
                work:
                  %sz = load i64, ptr @jn.gc.mark_size, align 8
                  %empty = icmp eq i64 %sz, 0
                  br i1 %empty, label %done, label %pop
                pop:
                  %sz1 = sub i64 %sz, 1
                  store i64 %sz1, ptr @jn.gc.mark_size, align 8
                  %stack = load ptr, ptr @jn.gc.mark_stack, align 8
                  %sp = getelementptr ptr, ptr %stack, i64 %sz1
                  %obj = load ptr, ptr %sp, align 8
                  call void @jnative_gc_scan_object(ptr %obj)
                  br label %work
                done:
                  ret void
                }

                define void @jnative_gc_sweep() {
                entry:
                  %head = load ptr, ptr @jn.gc.heap_head, align 8
                  store i64 0, ptr @jn.gc.live_bytes, align 8
                  store i64 0, ptr @jn.gc.live_objects, align 8
                  br label %loop
                loop:
                  %cur = phi ptr [ %head, %entry ], [ %next, %cont ]
                  %prev = phi ptr [ null, %entry ], [ %prevnext, %cont ]
                  %cnull = icmp eq ptr %cur, null
                  br i1 %cnull, label %done, label %check
                check:
                  %nextp = getelementptr %jn.GcHeader, ptr %cur, i32 0, i32 0
                  %next = load ptr, ptr %nextp, align 8
                  %mp = getelementptr %jn.GcHeader, ptr %cur, i32 0, i32 2
                  %m = load i32, ptr %mp, align 4
                  %alive = icmp ne i32 %m, 0
                  br i1 %alive, label %keep, label %freeit
                keep:
                  store i32 0, ptr %mp, align 4
                  %szp = getelementptr %jn.GcHeader, ptr %cur, i32 0, i32 1
                  %sz = load i64, ptr %szp, align 8
                  %lb = load i64, ptr @jn.gc.live_bytes, align 8
                  %lb2 = add i64 %lb, %sz
                  store i64 %lb2, ptr @jn.gc.live_bytes, align 8
                  %lo = load i64, ptr @jn.gc.live_objects, align 8
                  %lo2 = add i64 %lo, 1
                  store i64 %lo2, ptr @jn.gc.live_objects, align 8
                  br label %contkeep
                contkeep:
                  br label %cont
                freeit:
                  %stress = load i32, ptr @jn.gc.stress, align 4
                  %dostress = icmp ne i32 %stress, 0
                  br i1 %dostress, label %poison, label %dofree
                poison:
                  %szp2 = getelementptr %jn.GcHeader, ptr %cur, i32 0, i32 1
                  %sz2 = load i64, ptr %szp2, align 8
                  call ptr @memset(ptr %cur, i32 221, i64 %sz2)
                  br label %dofree
                dofree:
                  %pnull = icmp eq ptr %prev, null
                  br i1 %pnull, label %unlinkhead, label %unlinkmid
                unlinkhead:
                  store ptr %next, ptr @jn.gc.heap_head, align 8
                  br label %afterunlink
                unlinkmid:
                  %pnext = getelementptr %jn.GcHeader, ptr %prev, i32 0, i32 0
                  store ptr %next, ptr %pnext, align 8
                  br label %afterunlink
                afterunlink:
                  %fo = load i64, ptr @jn.gc.freed_objects, align 8
                  %fo2 = add i64 %fo, 1
                  store i64 %fo2, ptr @jn.gc.freed_objects, align 8
                  %szp3 = getelementptr %jn.GcHeader, ptr %cur, i32 0, i32 1
                  %sz3 = load i64, ptr %szp3, align 8
                  %ab = load i64, ptr @jn.gc.allocated_bytes, align 8
                  %ab2 = sub i64 %ab, %sz3
                  store i64 %ab2, ptr @jn.gc.allocated_bytes, align 8
                  call void @free(ptr %cur)
                  br label %contfree
                contfree:
                  br label %cont
                cont:
                  %prevnext = phi ptr [ %cur, %contkeep ], [ %prev, %contfree ]
                  br label %loop
                done:
                  ret void
                }

                define void @jnative_gc_collect() {
                entry:
                  call void @jnative_gc_ensure_init()
                  call void @jn_gc_collect_lock()
                  call void @jn_gc_stw_begin()
                  call void @jn_heap_lock()
                  %h0 = load ptr, ptr @jn.gc.heap_head, align 8
                  br label %clear
                clear:
                  %c = phi ptr [ %h0, %entry ], [ %cn, %cnext ]
                  %cnl = icmp eq ptr %c, null
                  br i1 %cnl, label %mark, label %cbody
                cbody:
                  %mp = getelementptr %jn.GcHeader, ptr %c, i32 0, i32 2
                  store i32 0, ptr %mp, align 4
                  %cnp = getelementptr %jn.GcHeader, ptr %c, i32 0, i32 0
                  %cn = load ptr, ptr %cnp, align 8
                  br label %cnext
                cnext:
                  br label %clear
                mark:
                  call void @jnative_gc_mark_from_roots()
                  call void @jnative_gc_sweep()
                  %col = load i64, ptr @jn.gc.collections, align 8
                  %col2 = add i64 %col, 1
                  store i64 %col2, ptr @jn.gc.collections, align 8
                  %stress = load i32, ptr @jn.gc.stress, align 4
                  %nostress = icmp eq i32 %stress, 0
                  br i1 %nostress, label %rethresh, label %unlock
                rethresh:
                  %live = load i64, ptr @jn.gc.live_bytes, align 8
                  %thr2 = mul i64 %live, 2
                  %min = icmp ult i64 %thr2, 1048576
                  %nth = select i1 %min, i64 1048576, i64 %thr2
                  store i64 %nth, ptr @jn.gc.threshold, align 8
                  br label %unlock
                unlock:
                  call void @jn_heap_unlock()
                  call void @jn_gc_stw_end()
                  call void @jn_gc_collect_unlock()
                  ret void
                }

                define void @jnative_gc_dump_stats() {
                entry:
                  %st = load i32, ptr @jn.gc.stats, align 4
                  %on = icmp ne i32 %st, 0
                  br i1 %on, label %print, label %done
                print:
                  call i32 @puts(ptr @.jn.gc.stats.hdr)
                  %c = load i64, ptr @jn.gc.collections, align 8
                  %a = load i64, ptr @jn.gc.allocated_objects, align 8
                  %f = load i64, ptr @jn.gc.freed_objects, align 8
                  %l = load i64, ptr @jn.gc.live_objects, align 8
                  %p = load i64, ptr @jn.gc.peak_bytes, align 8
                  call i32 (ptr, ...) @printf(ptr @.jn.gc.stats.fmt, i64 %c, i64 %a, i64 %f, i64 %l, i64 %p)
                  br label %done
                done:
                  ret void
                }

                define void @jnative_gc_ensure_init() {
                entry:
                  %in = load i32, ptr @jn.gc.inited, align 4
                  %done = icmp ne i32 %in, 0
                  br i1 %done, label %ret, label %init
                ret:
                  ret void
                init:
                  store i32 1, ptr @jn.gc.inited, align 4
                  %et = call ptr @getenv(ptr @.jn.gc.env.thr)
                  %etn = icmp eq ptr %et, null
                  br i1 %etn, label %stress, label %setthr
                setthr:
                  %tv = call i64 @atol(ptr %et)
                  %tok = icmp sgt i64 %tv, 0
                  br i1 %tok, label %storethr, label %stress
                storethr:
                  store i64 %tv, ptr @jn.gc.threshold, align 8
                  br label %stress
                stress:
                  %es = call ptr @getenv(ptr @.jn.gc.env.stress)
                  %esn = icmp eq ptr %es, null
                  br i1 %esn, label %stats, label %setstress
                setstress:
                  store i32 1, ptr @jn.gc.stress, align 4
                  store i64 1, ptr @jn.gc.threshold, align 8
                  br label %stats
                stats:
                  %esa = call ptr @getenv(ptr @.jn.gc.env.stats)
                  %esan = icmp eq ptr %esa, null
                  br i1 %esan, label %ate, label %setstats
                setstats:
                  store i32 1, ptr @jn.gc.stats, align 4
                  br label %ate
                ate:
                  %st = load i32, ptr @jn.gc.stats, align 4
                  %on = icmp ne i32 %st, 0
                  br i1 %on, label %reg, label %ret2
                reg:
                  call i32 @atexit(ptr @jnative_gc_dump_stats)
                  br label %ret2
                ret2:
                  ret void
                }

                define ptr @jnative_gc_alloc(i64 %payload) {
                entry:
                  call void @jnative_gc_ensure_init()
                  call void @jn_safepoint_poll()
                  %stress = load i32, ptr @jn.gc.stress, align 4
                  %dostress = icmp ne i32 %stress, 0
                  br i1 %dostress, label %collect, label %check
                check:
                  call void @jn_heap_lock()
                  %ab = load i64, ptr @jn.gc.allocated_bytes, align 8
                  %th = load i64, ptr @jn.gc.threshold, align 8
                  %over = icmp uge i64 %ab, %th
                  br i1 %over, label %needgc, label %alloc
                needgc:
                  call void @jn_heap_unlock()
                  br label %collect
                collect:
                  call void @jnative_gc_collect()
                  call void @jn_heap_lock()
                  br label %alloc
                alloc:
                  %total = add i64 %payload, 24
                  %raw = call ptr @malloc(i64 %total)
                  call ptr @memset(ptr %raw, i32 0, i64 %total)
                  %head = load ptr, ptr @jn.gc.heap_head, align 8
                  %np = getelementptr %jn.GcHeader, ptr %raw, i32 0, i32 0
                  store ptr %head, ptr %np, align 8
                  %sp = getelementptr %jn.GcHeader, ptr %raw, i32 0, i32 1
                  store i64 %total, ptr %sp, align 8
                  store ptr %raw, ptr @jn.gc.heap_head, align 8
                  %ab2 = load i64, ptr @jn.gc.allocated_bytes, align 8
                  %ab3 = add i64 %ab2, %total
                  store i64 %ab3, ptr @jn.gc.allocated_bytes, align 8
                  %peak = load i64, ptr @jn.gc.peak_bytes, align 8
                  %gt = icmp ugt i64 %ab3, %peak
                  br i1 %gt, label %setpeak, label %count
                setpeak:
                  store i64 %ab3, ptr @jn.gc.peak_bytes, align 8
                  br label %count
                count:
                  %ao = load i64, ptr @jn.gc.allocated_objects, align 8
                  %ao2 = add i64 %ao, 1
                  store i64 %ao2, ptr @jn.gc.allocated_objects, align 8
                  call void @jn_heap_unlock()
                  %obj = getelementptr i8, ptr %raw, i64 24
                  ret ptr %obj
                }

                define ptr @jnative_alloc_zeroed(i64 %size) {
                entry:
                  %p = call ptr @jnative_gc_alloc(i64 %size)
                  ret ptr %p
                }

                define ptr @jnative_gc_frame_push(i32 %n) {
                entry:
                  %n64 = sext i32 %n to i64
                  %bytes = mul i64 %n64, 8
                  %total = add i64 %bytes, 16
                  %frame = call ptr @malloc(i64 %total)
                  call ptr @memset(ptr %frame, i32 0, i64 %total)
                  %prev = call ptr @jn_tls_frame()
                  store ptr %prev, ptr %frame, align 8
                  %np = getelementptr i8, ptr %frame, i64 8
                  store i32 %n, ptr %np, align 4
                  call void @jn_tls_set_frame(ptr %frame)
                  ret ptr %frame
                }

                define void @jnative_gc_frame_set_root(ptr %frame, i32 %i, ptr %slot) {
                entry:
                  %roots = getelementptr i8, ptr %frame, i64 16
                  %i64 = sext i32 %i to i64
                  %p = getelementptr ptr, ptr %roots, i64 %i64
                  store ptr %slot, ptr %p, align 8
                  ret void
                }

                define void @jnative_gc_frame_pop() {
                entry:
                  %frame = call ptr @jn_tls_frame()
                  %isnull = icmp eq ptr %frame, null
                  br i1 %isnull, label %done, label %pop
                pop:
                  %prev = load ptr, ptr %frame, align 8
                  call void @jn_tls_set_frame(ptr %prev)
                  call void @free(ptr %frame)
                  br label %done
                done:
                  ret void
                }

                define void @jnative_safepoint_poll() {
                entry:
                  call void @jn_safepoint_poll()
                  ret void
                }

                """);
    }
}
