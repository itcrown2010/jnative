(module
  (type $jn_sig_0 (func (param i32) (param i32)))
  (import "jnative" "console_log" (func $js_console_log (param i32 i32)))
  (import "jnative" "document_get_element_by_id" (func $js_get_by_id (param i32 i32) (result i32)))
  (import "jnative" "element_set_text" (func $js_set_text (param i32 i32 i32)))
  (import "jnative" "document_create_element" (func $js_create_element (param i32 i32) (result i32)))
  (import "jnative" "element_append_child" (func $js_append_child (param i32 i32)))
  (import "jnative" "create_text" (func $js_create_text (param i32 i32) (result i32)))
  (import "jnative" "query_selector" (func $js_query_selector (param i32 i32) (result i32)))
  (import "jnative" "apply_dom_patches" (func $js_apply_dom_patches (param i32 i32)))
  (import "jnative" "schedule_microtask" (func $js_schedule_microtask))
  (import "jnative" "delegate_event" (func $js_delegate_event (param i32 i32 i32 i32)))
  (import "jnative" "init_delegation" (func $js_init_delegation (param i32)))
  (import "jnative" "register_callback" (func $js_register_callback (param i32) (result i32)))
  (import "jnative" "unregister_callback" (func $js_unregister_callback (param i32)))
  (import "jnative" "set_count_label" (func $js_set_count_label (param i32 i32)))
  (import "jnative" "document_body" (func $js_document_body (result i32)))
  (import "jnative" "input_value" (func $js_input_value (param i32) (result i32)))
  (import "jnative" "input_set_value" (func $js_input_set_value (param i32 i32 i32)))
  (import "jnative" "memory_grew" (func $js_memory_grew))
  (import "jnative" "report_uncaught" (func $js_report_uncaught (param i32 i32)))
  (memory (export "memory") 256 1024)
  (table $jn_funcref 1 funcref)
  (global $heap (mut i32) (i32.const 90112))
  (global $jn_heap_head (mut i32) (i32.const 0))
  (global $jn_free_head (mut i32) (i32.const 0))
  (global $jn_shadow_sp (mut i32) (i32.const 8192))
  (global $jn_frame (mut i32) (i32.const 0))
  (global $jn_mark_sp (mut i32) (i32.const 0))
  (global $jn_pending_exc (mut i32) (i32.const 0))
  (global $jn_cb_next (mut i32) (i32.const 1))
  (global $jn_host_next (mut i32) (i32.const 1))
  (global $jn_stat_alloc_bytes (mut i32) (i32.const 0))
  (global $jn_stat_live_bytes (mut i32) (i32.const 0))
  (global $jn_stat_free_bytes (mut i32) (i32.const 0))
  (global $jn_stat_gc_count (mut i32) (i32.const 0))
  (global $jn_stat_alloc_fail (mut i32) (i32.const 0))
  (global $jn_stat_host_roots (mut i32) (i32.const 0))
  (global $jn_since_gc (mut i32) (i32.const 0))
  (global $jn_gc_threshold (mut i32) (i32.const 262144))
  (global $jn_gc_collecting (mut i32) (i32.const 0))
  (global $jn_comp_kind (mut i32) (i32.const 0))
  (global $jn_comp_target (mut i32) (i32.const 0))
  (global $jn_comp_ret_i64 (mut i64) (i64.const 0))
  (global $jn_comp_ret_ptr (mut i32) (i32.const 0))
  (global $g_jn_static_FloatCalc_lastLeft (mut f64) (f64.const 0x0p0))
  (global $g_jn_static_FloatCalc_lastRight (mut f64) (f64.const 0x0p0))
  (global $g_jn_static_ResourceTracker_opens (mut i32) (i32.const 0))
  (global $g_jn_static_ResourceTracker_closes (mut i32) (i32.const 0))
  (global $g_jn_static_ResourceTracker_filesOpen (mut i32) (i32.const 0))
  (global $g_jn_static_ResourceTracker_socketsOpen (mut i32) (i32.const 0))
  (global $g_jn_static_ResourceTracker_tlsOpen (mut i32) (i32.const 0))
  (global $g_jn_static_ResourceTracker_serversOpen (mut i32) (i32.const 0))
  (global $g_jn_static_ResourceTracker_buffersOpen (mut i32) (i32.const 0))
  (global $g_jn_static_Integer_cache (mut i32) (i32.const 0))
  (global $g_jn_static_Integer_cacheReady (mut i32) (i32.const 0))
  (global $g_jn_static_Long_cache (mut i32) (i32.const 0))
  (global $g_jn_static_Long_cacheReady (mut i32) (i32.const 0))
  (global $g_jn_static_Boolean_TRUE (mut i32) (i32.const 0))
  (global $g_jn_static_Boolean_FALSE (mut i32) (i32.const 0))
  (global $g_jn_static_HashMap_EMPTY (mut i32) (i32.const 0))
  (global $g_jn_static_HashMap_OCCUPIED (mut i32) (i32.const 0))
  (global $g_jn_static_HashMap_DELETED (mut i32) (i32.const 0))
  (data (i32.const 2048) "")
  (data (i32.const 2049) "\66\69\6c\65")
  (data (i32.const 2053) "\73\6f\63\6b\65\74")
  (data (i32.const 2059) "\74\6c\73")
  (data (i32.const 2062) "\73\65\72\76\65\72")
  (data (i32.const 2068) "\62\75\66\66\65\72")
  (data (i32.const 2074) "\41\72\72\61\79\4c\69\73\74\20\69\6e\73\65\72\74\20\69\6e\64\65\78")
  (data (i32.const 2096) "\53\74\72\69\6e\67\42\75\69\6c\64\65\72\2e\73\65\74\4c\65\6e\67\74\68")
  (data (i32.const 2119) "\41\72\72\61\79\4c\69\73\74\20\6d\6f\64\69\66\69\65\64\20\64\75\72\69\6e\67\20\69\74\65\72\61\74\69\6f\6e")
  (data (i32.const 2154) "\41\72\72\61\79\4c\69\73\74\49\74\65\72\61\74\6f\72")
  (data (i32.const 2171) "\41\72\72\61\79\4c\69\73\74\20\63\61\70\61\63\69\74\79\20\6f\76\65\72\66\6c\6f\77")
  (data (i32.const 2198) "\53\74\72\69\6e\67\42\75\69\6c\64\65\72\2e\64\65\6c\65\74\65")
  (data (i32.const 2218) "\53\74\72\69\6e\67\42\75\69\6c\64\65\72\2e\69\6e\73\65\72\74")
  (data (i32.const 2238) "\6e\75\6c\6c")
  (data (i32.const 2242) "\54\68\72\65\61\64\50\6f\6f\6c\45\78\65\63\75\74\6f\72\20\69\73\20\73\68\75\74\20\64\6f\77\6e")
  (data (i32.const 2273) "\41\72\72\61\79\4c\69\73\74\20\69\6e\64\65\78")
  (data (i32.const 2288) "\6d\61\70\70\65\72")
  (data (i32.const 2294) "\41\72\72\61\79\4c\69\73\74\20\6d\6f\64\69\66\69\65\64\20\64\75\72\69\6e\67\20\6d\61\70\56\61\6c\75\65\73")
  (data (i32.const 2329) "\48\61\73\68\4d\61\70\20\66\75\6c\6c")
  (data (i32.const 2341) "\41\72\72\61\79\44\65\71\75\65")
  (data (i32.const 2351) "\48\61\73\68\4d\61\70\20\6d\6f\64\69\66\69\65\64\20\64\75\72\69\6e\67\20\65\6e\74\72\79\20\69\74\65\72\61\74\69\6f\6e")
  (data (i32.const 2390) "\48\61\73\68\4d\61\70\45\6e\74\72\79\49\74\65\72\61\74\6f\72")
  (data (i32.const 2410) "\70\72\65\64\69\63\61\74\65")
  (data (i32.const 2419) "\41\72\72\61\79\4c\69\73\74\20\6d\6f\64\69\66\69\65\64\20\64\75\72\69\6e\67\20\66\69\6c\74\65\72")
  (data (i32.const 2451) "\61\63\74\69\6f\6e")
  (data (i32.const 2457) "\41\72\72\61\79\4c\69\73\74\20\6d\6f\64\69\66\69\65\64\20\64\75\72\69\6e\67\20\66\6f\72\45\61\63\68")
  (data (i32.const 2490) "\41\72\72\61\79\49\74\65\72\61\74\6f\72")
  (data (i32.const 2503) "\6e\65\67\61\74\69\76\65\20\69\6e\69\74\69\61\6c\43\61\70\61\63\69\74\79")
  (func $jn_gc_trace_type_fields (param $obj i32) (param $tid i32)
    local.get $tid
    i32.const 3
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 4
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 5
    i32.eq
    if
      local.get $obj
      i32.const 16
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 7
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 8
    i32.eq
    if
      local.get $obj
      i32.const 16
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 9
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 10
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 12
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 13
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 17
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 18
    i32.eq
    if
      local.get $obj
      i32.const 16
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 20
    i32.eq
    if
      local.get $obj
      i32.const 16
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 23
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 24
    i32.eq
    if
      local.get $obj
      i32.const 16
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 20
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 25
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 26
    i32.eq
    if
      local.get $obj
      i32.const 16
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 27
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 28
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 29
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 31
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 32
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 35
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 37
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 38
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 39
    i32.eq
    if
      local.get $obj
      i32.const 16
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 41
    i32.eq
    if
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 44
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 45
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 47
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 28
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 48
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 50
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 51
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 16
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 52
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 53
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 55
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 56
    i32.eq
    if
      local.get $obj
      i32.const 16
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 57
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 58
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 59
    i32.eq
    if
      local.get $obj
      i32.const 16
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 64
    i32.eq
    if
      local.get $obj
      i32.const 4
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      local.get $obj
      i32.const 12
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
  )
  (func $jn_gc_trace_statics
    global.get $g_jn_static_Integer_cache
    call $jn_gc_mark_obj
    global.get $g_jn_static_Long_cache
    call $jn_gc_mark_obj
    global.get $g_jn_static_Boolean_TRUE
    call $jn_gc_mark_obj
    global.get $g_jn_static_Boolean_FALSE
    call $jn_gc_mark_obj
  )
  ;; ---- wasm32 mark-sweep GC (non-moving, precise roots) ----
  (func $jn_gc_frame_push (param $n i32) (result i32)
    (local $fp i32) (local $bytes i32) (local $end i32) (local $i i32)
    local.get $n
    i32.const 2
    i32.add
    i32.const 2
    i32.shl
    local.set $bytes
    global.get $jn_shadow_sp
    local.set $fp
    local.get $fp
    local.get $bytes
    i32.add
    local.set $end
    local.get $end
    i32.const 73728
    i32.gt_u
    if
      unreachable
    end
    local.get $fp
    global.get $jn_frame
    i32.store
    local.get $fp
    i32.const 4
    i32.add
    local.get $n
    i32.store
    i32.const 0
    local.set $i
    (block $zdone
      (loop $z
        local.get $i
        local.get $n
        i32.ge_u
        br_if $zdone
        local.get $fp
        i32.const 8
        i32.add
        local.get $i
        i32.const 2
        i32.shl
        i32.add
        i32.const 0
        i32.store
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $z
      )
    )
    local.get $end
    global.set $jn_shadow_sp
    local.get $fp
    global.set $jn_frame
    local.get $fp
  )
  (func $jn_gc_frame_set_root (param $frame i32) (param $i i32) (param $val i32)
    local.get $frame
    i32.const 8
    i32.add
    local.get $i
    i32.const 2
    i32.shl
    i32.add
    local.get $val
    i32.store
  )
  (func $jn_gc_frame_pop
    (local $fp i32)
    global.get $jn_frame
    local.set $fp
    local.get $fp
    i32.eqz
    if
      return
    end
    local.get $fp
    global.set $jn_shadow_sp
    local.get $fp
    i32.load
    global.set $jn_frame
  )
  (func $jn_host_root_retain (param $ptr i32) (result i32)
    (local $i i32) (local $b i32) (local $gen i32)
    local.get $ptr
    i32.eqz
    if (result i32)
      i32.const 0
    else
      i32.const 0
      local.set $i
      (block $found (result i32)
        (loop $scan
          local.get $i
          i32.const 128
          i32.ge_u
          if
            i32.const 0
            br $found
          end
          i32.const 768
          local.get $i
          i32.const 8
          i32.mul
          i32.add
          local.set $b
          local.get $b
          i32.const 4
          i32.add
          i32.load
          i32.eqz
          if
            local.get $b
            i32.load
            i32.const 1
            i32.add
            i32.const 65535
            i32.and
            local.set $gen
            local.get $gen
            i32.eqz
            if
              i32.const 1
              local.set $gen
            end
            local.get $b
            local.get $gen
            i32.store
            local.get $b
            i32.const 4
            i32.add
            local.get $ptr
            i32.store
            global.get $jn_stat_host_roots
            i32.const 1
            i32.add
            global.set $jn_stat_host_roots
            local.get $gen
            i32.const 16
            i32.shl
            local.get $i
            i32.const 1
            i32.add
            i32.or
            br $found
          end
          local.get $i
          i32.const 1
          i32.add
          local.set $i
          br $scan
        )
        i32.const 0
      )
    end
  )
  (func $jn_host_root_release (param $h i32)
    (local $slot i32) (local $b i32) (local $gen i32)
    local.get $h
    i32.eqz
    if
      return
    end
    local.get $h
    i32.const 65535
    i32.and
    i32.const 1
    i32.sub
    local.set $slot
    local.get $slot
    i32.const 128
    i32.ge_u
    if
      return
    end
    local.get $h
    i32.const 16
    i32.shr_u
    local.set $gen
    i32.const 768
    local.get $slot
    i32.const 8
    i32.mul
    i32.add
    local.set $b
    local.get $b
    i32.load
    local.get $gen
    i32.ne
    if
      return
    end
    local.get $b
    i32.const 4
    i32.add
    i32.load
    i32.eqz
    if
      return
    end
    local.get $b
    i32.const 4
    i32.add
    i32.const 0
    i32.store
    local.get $b
    local.get $gen
    i32.const 1
    i32.add
    i32.const 65535
    i32.and
    i32.store
    global.get $jn_stat_host_roots
    i32.const 1
    i32.sub
    global.set $jn_stat_host_roots
  )
  (func $jn_host_root_get (param $h i32) (result i32)
    (local $slot i32) (local $b i32) (local $gen i32)
    local.get $h
    i32.eqz
    if (result i32)
      i32.const 0
    else
      local.get $h
      i32.const 65535
      i32.and
      i32.const 1
      i32.sub
      local.set $slot
      local.get $slot
      i32.const 128
      i32.ge_u
      if (result i32)
        i32.const 0
      else
        local.get $h
        i32.const 16
        i32.shr_u
        local.set $gen
        i32.const 768
        local.get $slot
        i32.const 8
        i32.mul
        i32.add
        local.set $b
        local.get $b
        i32.load
        local.get $gen
        i32.ne
        if (result i32)
          i32.const 0
        else
          local.get $b
          i32.const 4
          i32.add
          i32.load
        end
      end
    end
  )
  (func $jn_gc_obj_to_hdr (param $obj i32) (result i32)
    local.get $obj
    i32.const 16
    i32.sub
  )
  (func $jn_gc_hdr_to_obj (param $h i32) (result i32)
    local.get $h
    i32.const 16
    i32.add
  )
  (func $jn_gc_mark_push (param $obj i32)
    (local $sp i32)
    local.get $obj
    i32.eqz
    if
      return
    end
    global.get $jn_mark_sp
    local.set $sp
    local.get $sp
    i32.const 4096
    i32.ge_u
    if
      unreachable
    end
    i32.const 73728
    local.get $sp
    i32.const 2
    i32.shl
    i32.add
    local.get $obj
    i32.store
    local.get $sp
    i32.const 1
    i32.add
    global.set $jn_mark_sp
  )
  (func $jn_gc_mark_obj (param $obj i32)
    (local $h i32) (local $f i32)
    local.get $obj
    i32.eqz
    if
      return
    end
    local.get $obj
    call $jn_gc_obj_to_hdr
    local.set $h
    local.get $h
    i32.const 90112
    i32.lt_u
    if
      return
    end
    local.get $h
    i32.const 8
    i32.add
    i32.load
    local.set $f
    local.get $f
    i32.const 2
    i32.and
    if
      return
    end
    local.get $f
    i32.const 1
    i32.and
    if
      return
    end
    local.get $h
    i32.const 8
    i32.add
    local.get $f
    i32.const 1
    i32.or
    i32.store
    local.get $obj
    call $jn_gc_mark_push
  )
  (func $jn_gc_trace_one (param $obj i32)
    (local $tid i32) (local $f i32) (local $len i32) (local $i i32)
    local.get $obj
    call $jn_gc_obj_to_hdr
    i32.const 8
    i32.add
    i32.load
    local.set $f
    local.get $f
    i32.const 16
    i32.and
    if
      return
    end
    local.get $obj
    i32.load
    local.set $tid
    local.get $obj
    local.get $tid
    call $jn_gc_trace_type_fields
    local.get $f
    i32.const 8
    i32.and
    i32.eqz
    if
      return
    end
    local.get $obj
    i32.const 8
    i32.add
    i32.load
    local.set $len
    i32.const 0
    local.set $i
    (block $adone
      (loop $a
        local.get $i
        local.get $len
        i32.ge_s
        br_if $adone
        local.get $obj
        i32.const 16
        i32.add
        local.get $i
        i32.const 2
        i32.shl
        i32.add
        i32.load
        call $jn_gc_mark_obj
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $a
      )
    )
  )
  (func $jn_gc_trace_roots
    (local $fp i32) (local $n i32) (local $i i32) (local $base i32)
    global.get $jn_frame
    local.set $fp
    (block $fdone
      (loop $f
        local.get $fp
        i32.eqz
        br_if $fdone
        local.get $fp
        i32.const 4
        i32.add
        i32.load
        local.set $n
        i32.const 0
        local.set $i
        (block $sdone
          (loop $s
            local.get $i
            local.get $n
            i32.ge_u
            br_if $sdone
            local.get $fp
            i32.const 8
            i32.add
            local.get $i
            i32.const 2
            i32.shl
            i32.add
            i32.load
            call $jn_gc_mark_obj
            local.get $i
            i32.const 1
            i32.add
            local.set $i
            br $s
          )
        )
        local.get $fp
        i32.load
        local.set $fp
        br $f
      )
    )
    global.get $jn_pending_exc
    call $jn_gc_mark_obj
    i32.const 0
    local.set $i
    (block $hdone
      (loop $h
        local.get $i
        i32.const 128
        i32.ge_u
        br_if $hdone
        i32.const 768
        local.get $i
        i32.const 8
        i32.mul
        i32.add
        i32.const 4
        i32.add
        i32.load
        call $jn_gc_mark_obj
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $h
      )
    )
    i32.const 0
    local.set $i
    (block $cdone
      (loop $c
        local.get $i
        i32.const 64
        i32.ge_u
        br_if $cdone
        i32.const 256
        local.get $i
        i32.const 8
        i32.mul
        i32.add
        i32.const 4
        i32.add
        i32.load
        call $jn_gc_mark_obj
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $c
      )
    )
    call $jn_gc_trace_statics
  )
  (func $jn_gc_collect
    (local $h i32) (local $next i32) (local $f i32) (local $live i32) (local $freeb i32)
    global.get $jn_gc_collecting
    if
      return
    end
    i32.const 1
    global.set $jn_gc_collecting
    i32.const 0
    global.set $jn_mark_sp
    global.get $jn_heap_head
    local.set $h
    (block $clrdone
      (loop $clr
        local.get $h
        i32.eqz
        br_if $clrdone
        local.get $h
        i32.const 8
        i32.add
        i32.load
        local.set $f
        local.get $h
        i32.const 8
        i32.add
        local.get $f
        i32.const -2
        i32.and
        i32.store
        local.get $h
        i32.load
        local.set $h
        br $clr
      )
    )
    call $jn_gc_trace_roots
    (block $mdone
      (loop $m
        global.get $jn_mark_sp
        i32.eqz
        br_if $mdone
        global.get $jn_mark_sp
        i32.const 1
        i32.sub
        global.set $jn_mark_sp
        i32.const 73728
        global.get $jn_mark_sp
        i32.const 2
        i32.shl
        i32.add
        i32.load
        call $jn_gc_trace_one
        br $m
      )
    )
    i32.const 0
    global.set $jn_free_head
    i32.const 0
    local.set $live
    i32.const 0
    local.set $freeb
    global.get $jn_heap_head
    local.set $h
    (block $swdone
      (loop $sw
        local.get $h
        i32.eqz
        br_if $swdone
        local.get $h
        i32.load
        local.set $next
        local.get $h
        i32.const 8
        i32.add
        i32.load
        local.set $f
        local.get $f
        i32.const 1
        i32.and
        if
          local.get $h
          i32.const 8
          i32.add
          local.get $f
          i32.const -2
          i32.and
          i32.store
          local.get $live
          local.get $h
          i32.const 4
          i32.add
          i32.load
          i32.add
          local.set $live
        else
          local.get $h
          i32.const 8
          i32.add
          local.get $f
          i32.const 2
          i32.or
          i32.store
          local.get $h
          i32.const 12
          i32.add
          global.get $jn_free_head
          i32.store
          local.get $h
          global.set $jn_free_head
          local.get $freeb
          local.get $h
          i32.const 4
          i32.add
          i32.load
          i32.add
          local.set $freeb
        end
        local.get $next
        local.set $h
        br $sw
      )
    )
    local.get $live
    global.set $jn_stat_live_bytes
    local.get $freeb
    global.set $jn_stat_free_bytes
    i32.const 0
    global.set $jn_since_gc
    global.get $jn_stat_gc_count
    i32.const 1
    i32.add
    global.set $jn_stat_gc_count
    i32.const 0
    global.set $jn_gc_collecting
  )
  (func $jn_gc_try_free_list (param $n i32) (param $flags i32) (result i32)
    (local $prev i32) (local $cur i32) (local $sz i32) (local $nx i32)
    i32.const 0
    local.set $prev
    global.get $jn_free_head
    local.set $cur
    (block $done (result i32)
      (loop $walk
        local.get $cur
        i32.eqz
        if
          i32.const 0
          br $done
        end
        local.get $cur
        i32.const 4
        i32.add
        i32.load
        local.set $sz
        local.get $sz
        local.get $n
        i32.eq
        if
          local.get $cur
          i32.const 12
          i32.add
          i32.load
          local.set $nx
          local.get $prev
          i32.eqz
          if
            local.get $nx
            global.set $jn_free_head
          else
            local.get $prev
            i32.const 12
            i32.add
            local.get $nx
            i32.store
          end
          local.get $cur
          i32.const 8
          i32.add
          local.get $flags
          i32.store
          local.get $cur
          call $jn_gc_hdr_to_obj
          br $done
        end
        local.get $cur
        local.set $prev
        local.get $cur
        i32.const 12
        i32.add
        i32.load
        local.set $cur
        br $walk
      )
      i32.const 0
    )
  )
  (func $jn_gc_bump (param $total i32) (result i32)
    (local $p i32) (local $end i32) (local $cur i32) (local $pages i32)
    global.get $heap
    local.set $p
    local.get $p
    local.get $total
    i32.const 7
    i32.add
    i32.const -8
    i32.and
    i32.add
    local.set $end
    local.get $end
    local.get $p
    i32.lt_u
    if
      i32.const 0
      return
    end
    memory.size
    i32.const 16
    i32.shl
    local.set $cur
    local.get $end
    local.get $cur
    i32.gt_u
    if
      local.get $end
      local.get $cur
      i32.sub
      i32.const 65535
      i32.add
      i32.const 16
      i32.shr_u
      local.set $pages
      local.get $pages
      memory.grow
      i32.const -1
      i32.eq
      if
        i32.const 0
        return
      end
      call $js_memory_grew
    end
    local.get $end
    global.set $heap
    local.get $p
  )
  (func $jn_gc_init_block (param $h i32) (param $n i32) (param $flags i32) (result i32)
    local.get $h
    global.get $jn_heap_head
    i32.store
    local.get $h
    global.set $jn_heap_head
    local.get $h
    i32.const 4
    i32.add
    local.get $n
    i32.store
    local.get $h
    i32.const 8
    i32.add
    local.get $flags
    i32.store
    local.get $h
    call $jn_gc_hdr_to_obj
  )
  (func $jn_gc_alloc (param $n i32) (param $flags i32) (result i32)
    (local $p i32) (local $h i32) (local $total i32) (local $i i32)
    local.get $n
    i32.const 1
    i32.lt_s
    if
      i32.const 1
      local.set $n
    end
    global.get $jn_since_gc
    global.get $jn_gc_threshold
    i32.ge_u
    if
      call $jn_gc_collect
    end
    local.get $n
    local.get $flags
    call $jn_gc_try_free_list
    local.set $p
    local.get $p
    i32.eqz
    if
      local.get $n
      i32.const 16
      i32.add
      local.set $total
      local.get $total
      call $jn_gc_bump
      local.set $h
      local.get $h
      i32.eqz
      if
        call $jn_gc_collect
        local.get $n
        local.get $flags
        call $jn_gc_try_free_list
        local.set $p
        local.get $p
        i32.eqz
        if
          local.get $total
          call $jn_gc_bump
          local.set $h
          local.get $h
          i32.eqz
          if
            global.get $jn_stat_alloc_fail
            i32.const 1
            i32.add
            global.set $jn_stat_alloc_fail
            i32.const 0
            return
          end
          local.get $h
          local.get $n
          local.get $flags
          call $jn_gc_init_block
          local.set $p
        end
      else
        local.get $h
        local.get $n
        local.get $flags
        call $jn_gc_init_block
        local.set $p
      end
    end
    i32.const 0
    local.set $i
    (block $zd
      (loop $z
        local.get $i
        local.get $n
        i32.ge_u
        br_if $zd
        local.get $p
        local.get $i
        i32.add
        i32.const 0
        i32.store8
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $z
      )
    )
    global.get $jn_stat_alloc_bytes
    local.get $n
    i32.add
    global.set $jn_stat_alloc_bytes
    global.get $jn_since_gc
    local.get $n
    i32.add
    global.set $jn_since_gc
    local.get $p
  )
  (func $jn_alloc (param $n i32) (result i32)
    local.get $n
    i32.const 0
    call $jn_gc_alloc
  )
  (func $jn_alloc_zeroed (param $n i32) (result i32)
    local.get $n
    i32.const 0
    call $jn_gc_alloc
  )
  (func $jn_alloc_opaque (param $n i32) (result i32)
    local.get $n
    i32.const 16
    call $jn_gc_alloc
  )
  (func $jn_gc_alloc_array (param $n i32) (param $refElems i32) (result i32)
    (local $f i32)
    i32.const 4
    local.set $f
    local.get $refElems
    if
      local.get $f
      i32.const 8
      i32.or
      local.set $f
    end
    local.get $n
    local.get $f
    call $jn_gc_alloc
  )
  (func $jn_gc_safepoint
    global.get $jn_since_gc
    global.get $jn_gc_threshold
    i32.ge_u
    if
      call $jn_gc_collect
    end
  )
  (func $jn_gc_stats_alloc_bytes (result i32) global.get $jn_stat_alloc_bytes)
  (func $jn_gc_stats_live_bytes (result i32) global.get $jn_stat_live_bytes)
  (func $jn_gc_stats_free_bytes (result i32) global.get $jn_stat_free_bytes)
  (func $jn_gc_stats_heap_cap (result i32) memory.size i32.const 16 i32.shl)
  (func $jn_gc_stats_gc_count (result i32) global.get $jn_stat_gc_count)
  (func $jn_gc_stats_host_roots (result i32) global.get $jn_stat_host_roots)
  (func $jn_gc_stats_alloc_fail (result i32) global.get $jn_stat_alloc_fail)
  (func $jn_str (param $off i32) (param $len i32) (result i32)
    (local $p i32) (local $i i32)
    local.get $len
    i32.const 4
    i32.add
    call $jn_alloc_opaque
    local.set $p
    local.get $p
    local.get $len
    i32.store
    i32.const 0
    local.set $i
    (block $done
      (loop $copy
        local.get $i
        local.get $len
        i32.ge_u
        br_if $done
        local.get $p
        i32.const 4
        i32.add
        local.get $i
        i32.add
        local.get $off
        local.get $i
        i32.add
        i32.load8_u
        i32.store8
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $copy
      )
    )
    local.get $p
  )
  (func $jn_str_len (param $s i32) (result i32)
    local.get $s
    i32.eqz
    if (result i32)
      i32.const 0
    else
      local.get $s
      i32.load
    end
  )
  (func $jn_str_data (param $s i32) (result i32)
    local.get $s
    i32.const 4
    i32.add
  )
  (func $jn_string_length (param $s i32) (result i32)
    local.get $s
    call $jn_str_len
  )
  (func $jn_string_char_at (param $s i32) (param $index i32) (result i32)
    (local $len i32)
    local.get $s
    i32.eqz
    if
      i32.const 0
      return
    end
    local.get $s
    i32.load
    local.set $len
    local.get $index
    i32.const 0
    i32.lt_s
    if
      i32.const 0
      return
    end
    local.get $index
    local.get $len
    i32.ge_s
    if
      i32.const 0
      return
    end
    local.get $s
    i32.const 4
    i32.add
    local.get $index
    i32.add
    i32.load8_u
    return
  )
  (func $jn_console_log (param $s i32)
    local.get $s
    call $jn_str_data
    local.get $s
    call $jn_str_len
    call $js_console_log
  )
  (func $jn_get_element_by_id (param $s i32) (result i32)
    local.get $s
    call $jn_str_data
    local.get $s
    call $jn_str_len
    call $js_get_by_id
  )
  (func $jn_create_element (param $s i32) (result i32)
    local.get $s
    call $jn_str_data
    local.get $s
    call $jn_str_len
    call $js_create_element
  )
  (func $jn_document_body (result i32)
    call $js_document_body
  )
  (func $jn_set_text (param $h i32) (param $s i32)
    local.get $h
    local.get $s
    call $jn_str_data
    local.get $s
    call $jn_str_len
    call $js_set_text
  )
  (func $jn_append_child (param $p i32) (param $c i32)
    local.get $p
    local.get $c
    call $js_append_child
  )
  (func $jn_delegate_event (param $node i32) (param $typeStr i32) (param $cbId i32)
    local.get $node
    local.get $typeStr
    call $jn_str_data
    local.get $typeStr
    call $jn_str_len
    local.get $cbId
    call $js_delegate_event
  )
  ;; callback table in reserved memory [256..768): 64 slots × 8 bytes (id,ptr) — simple map
  (func $jn_register_dom_callback (param $callable i32) (result i32)
    (local $id i32) (local $slot i32)
    global.get $jn_cb_next
    local.set $id
    global.get $jn_cb_next
    i32.const 1
    i32.add
    global.set $jn_cb_next
    local.get $id
    i32.const 63
    i32.and
    i32.const 8
    i32.mul
    i32.const 256
    i32.add
    local.set $slot
    local.get $slot
    local.get $id
    i32.store
    local.get $slot
    i32.const 4
    i32.add
    local.get $callable
    i32.store
    local.get $id
    call $js_register_callback
    drop
    local.get $id
  )
  (func $jn_lookup_dom_callback (param $id i32) (result i32)
    (local $slot i32)
    local.get $id
    i32.const 63
    i32.and
    i32.const 8
    i32.mul
    i32.const 256
    i32.add
    local.set $slot
    local.get $slot
    i32.load
    local.get $id
    i32.ne
    if (result i32)
      i32.const 0
    else
      local.get $slot
      i32.const 4
      i32.add
      i32.load
    end
  )
  (func $jn_unregister_dom_callback (param $id i32)
    (local $slot i32)
    local.get $id
    i32.const 63
    i32.and
    i32.const 8
    i32.mul
    i32.const 256
    i32.add
    local.set $slot
    local.get $slot
    i32.load
    local.get $id
    i32.eq
    if
      local.get $slot
      i32.const 0
      i32.store
      local.get $slot
      i32.const 4
      i32.add
      i32.const 0
      i32.store
    end
    local.get $id
    call $js_unregister_callback
  )
  (func $jn_input_value (param $h i32) (result i32)
    local.get $h
    call $js_input_value
  )
  (func $jn_input_set_value (param $h i32) (param $s i32)
    local.get $h
    local.get $s
    call $jn_str_data
    local.get $s
    call $jn_str_len
    call $js_input_set_value
  )
  (func $jn_string_concat (param $a i32) (param $b i32) (result i32)
    (local $la i32) (local $lb i32) (local $p i32) (local $i i32)
    local.get $a
    call $jn_str_len
    local.set $la
    local.get $b
    call $jn_str_len
    local.set $lb
    local.get $la
    local.get $lb
    i32.add
    i32.const 4
    i32.add
    call $jn_alloc_opaque
    local.set $p
    local.get $p
    local.get $la
    local.get $lb
    i32.add
    i32.store
    i32.const 0
    local.set $i
    (block $da
      (loop $ca
        local.get $i
        local.get $la
        i32.ge_u
        br_if $da
        local.get $p
        i32.const 4
        i32.add
        local.get $i
        i32.add
        local.get $a
        call $jn_str_data
        local.get $i
        i32.add
        i32.load8_u
        i32.store8
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $ca
      )
    )
    i32.const 0
    local.set $i
    (block $db
      (loop $cb
        local.get $i
        local.get $lb
        i32.ge_u
        br_if $db
        local.get $p
        i32.const 4
        i32.add
        local.get $la
        i32.add
        local.get $i
        i32.add
        local.get $b
        call $jn_str_data
        local.get $i
        i32.add
        i32.load8_u
        i32.store8
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $cb
      )
    )
    local.get $p
  )
  ;; decimal int → string (for money display helpers)
  (func $jn_string_value_of_i32 (param $v i32) (result i32)
    (local $neg i32) (local $n i32) (local $tmp i32) (local $len i32) (local $p i32) (local $i i32)
    local.get $v
    i32.const 0
    i32.lt_s
    local.set $neg
    local.get $v
    local.get $v
    i32.const 31
    i32.shr_s
    i32.xor
    local.get $v
    i32.const 31
    i32.shr_s
    i32.sub
    local.set $n
    local.get $n
    i32.eqz
    if (result i32)
      i32.const 1
      local.set $len
      i32.const 0
    else
      i32.const 0
      local.set $len
      local.get $n
      local.set $tmp
      (loop $cnt
        local.get $len
        i32.const 1
        i32.add
        local.set $len
        local.get $tmp
        i32.const 10
        i32.div_u
        local.set $tmp
        local.get $tmp
        br_if $cnt
      )
      i32.const 0
    end
    drop
    local.get $len
    local.get $neg
    i32.add
    i32.const 4
    i32.add
    call $jn_alloc_opaque
    local.set $p
    local.get $p
    local.get $len
    local.get $neg
    i32.add
    i32.store
    local.get $neg
    if
      local.get $p
      i32.const 4
      i32.add
      i32.const 45
      i32.store8
    end
    local.get $n
    i32.eqz
    if
      local.get $p
      i32.const 4
      i32.add
      local.get $neg
      i32.add
      i32.const 48
      i32.store8
      local.get $p
      return
    end
    local.get $len
    local.set $i
    (loop $dig
      local.get $i
      i32.const 1
      i32.sub
      local.set $i
      local.get $p
      i32.const 4
      i32.add
      local.get $neg
      i32.add
      local.get $i
      i32.add
      local.get $n
      i32.const 10
      i32.rem_u
      i32.const 48
      i32.add
      i32.store8
      local.get $n
      i32.const 10
      i32.div_u
      local.set $n
      local.get $i
      br_if $dig
    )
    local.get $p
  )
  ;; ---- wasm32 explicit exception / completion (no WASM EH) ----
  (func $jn_ex_alloc (param $tid i32) (param $msg i32) (result i32)
    (local $p i32)
    i32.const 16
    call $jn_alloc_zeroed
    local.set $p
    local.get $p
    i32.eqz
    if (result i32)
      i32.const 0
    else
      local.get $p
      local.get $tid
      i32.store
      local.get $p
      i32.const 4
      i32.add
      local.get $msg
      i32.store
      local.get $p
    end
  )
  (func $jnative_exception_throw (param $ex i32)
    local.get $ex
    i32.eqz
    if
      call $jnative_throw_npe
      return
    end
    local.get $ex
    global.set $jn_pending_exc
    i32.const 0
    global.set $jn_comp_kind
  )
  (func $jnative_exception_pending (result i32)
    global.get $jn_pending_exc
    i32.const 0
    i32.ne
  )
  (func $jnative_exception_peek (result i32)
    global.get $jn_pending_exc
  )
  (func $jnative_exception_take (result i32)
    (local $ex i32)
    global.get $jn_pending_exc
    local.set $ex
    i32.const 0
    global.set $jn_pending_exc
    local.get $ex
  )
  (func $jnative_exception_clear
    i32.const 0
    global.set $jn_pending_exc
  )
  (func $jnative_throw_npe
    i32.const 45
    i32.const 0
    call $jn_ex_alloc
    global.set $jn_pending_exc
  )
  (func $jnative_throw_illegal_argument
    i32.const 44
    i32.const 0
    call $jn_ex_alloc
    global.set $jn_pending_exc
  )
  (func $jnative_throw_aioobe
    call $jnative_throw_npe
  )
  (func $jnative_throw_negative_array_size
    call $jnative_throw_npe
  )
  (func $jnative_throw_class_cast
    call $jnative_throw_npe
  )
  (func $jnative_throw_arithmetic
    call $jnative_throw_npe
  )
  (func $jnative_throw_illegal_monitor
    call $jnative_throw_npe
  )
  (func $jnative_throw_illegal_state
    call $jnative_throw_npe
  )
  (func $jnative_throwable_add_suppressed (param $primary i32) (param $sup i32)
    (local $old i32) (local $len i32) (local $narr i32) (local $i i32)
    local.get $primary
    i32.eqz
    if
      return
    end
    local.get $sup
    i32.eqz
    if
      call $jnative_throw_npe
      return
    end
    local.get $primary
    local.get $sup
    i32.eq
    if
      call $jnative_throw_illegal_argument
      return
    end
    local.get $primary
    i32.const 12
    i32.add
    i32.load
    local.set $old
    local.get $old
    i32.eqz
    if
      i32.const 20
      i32.const 1
      call $jn_gc_alloc_array
      local.set $narr
      local.get $narr
      i32.eqz
      if
        return
      end
      local.get $narr
      i32.const 68
      i32.store
      local.get $narr
      i32.const 8
      i32.add
      i32.const 1
      i32.store
      local.get $narr
      i32.const 16
      i32.add
      local.get $sup
      i32.store
      local.get $primary
      i32.const 12
      i32.add
      local.get $narr
      i32.store
      return
    end
    local.get $old
    i32.const 8
    i32.add
    i32.load
    local.set $len
    local.get $len
    i32.const 1
    i32.add
    i32.const 2
    i32.shl
    i32.const 16
    i32.add
    i32.const 1
    call $jn_gc_alloc_array
    local.set $narr
    local.get $narr
    i32.eqz
    if
      return
    end
    local.get $narr
    i32.const 68
    i32.store
    local.get $narr
    i32.const 8
    i32.add
    local.get $len
    i32.const 1
    i32.add
    i32.store
    i32.const 0
    local.set $i
    (block $cdone
      (loop $c
        local.get $i
        local.get $len
        i32.ge_s
        br_if $cdone
        local.get $narr
        i32.const 16
        i32.add
        local.get $i
        i32.const 2
        i32.shl
        i32.add
        local.get $old
        i32.const 16
        i32.add
        local.get $i
        i32.const 2
        i32.shl
        i32.add
        i32.load
        i32.store
        local.get $i
        i32.const 1
        i32.add
        local.set $i
        br $c
      )
    )
    local.get $narr
    i32.const 16
    i32.add
    local.get $len
    i32.const 2
    i32.shl
    i32.add
    local.get $sup
    i32.store
    local.get $primary
    i32.const 12
    i32.add
    local.get $narr
    i32.store
  )
  (func $jnative_throwable_get_suppressed (param $ex i32) (result i32)
    (local $old i32) (local $len i32) (local $out i32) (local $i i32)
    local.get $ex
    i32.eqz
    if (result i32)
      call $jnative_throw_npe
      i32.const 0
    else
      local.get $ex
      i32.const 12
      i32.add
      i32.load
      local.set $old
      local.get $old
      i32.eqz
      if (result i32)
        i32.const 16
        i32.const 1
        call $jn_gc_alloc_array
        local.set $out
        local.get $out
        i32.eqz
        if (result i32)
          i32.const 0
        else
          local.get $out
          i32.const 68
          i32.store
          local.get $out
          i32.const 8
          i32.add
          i32.const 0
          i32.store
          local.get $out
        end
      else
        local.get $old
        i32.const 8
        i32.add
        i32.load
        local.set $len
        local.get $len
        i32.const 2
        i32.shl
        i32.const 16
        i32.add
        i32.const 1
        call $jn_gc_alloc_array
        local.set $out
        local.get $out
        i32.eqz
        if (result i32)
          i32.const 0
        else
          local.get $out
          i32.const 68
          i32.store
          local.get $out
          i32.const 8
          i32.add
          local.get $len
          i32.store
          i32.const 0
          local.set $i
          (block $gd
            (loop $gl
              local.get $i
              local.get $len
              i32.ge_s
              br_if $gd
              local.get $out
              i32.const 16
              i32.add
              local.get $i
              i32.const 2
              i32.shl
              i32.add
              local.get $old
              i32.const 16
              i32.add
              local.get $i
              i32.const 2
              i32.shl
              i32.add
              i32.load
              i32.store
              local.get $i
              i32.const 1
              i32.add
              local.set $i
              br $gl
            )
          )
          local.get $out
        end
      end
    end
  )
  (func $jnative_completion_clear
    i32.const 0
    global.set $jn_comp_kind
    i32.const 0
    global.set $jn_comp_target
    i64.const 0
    global.set $jn_comp_ret_i64
    i32.const 0
    global.set $jn_comp_ret_ptr
  )
  (func $jnative_completion_set_return_i64 (param $v i64)
    i32.const 1
    global.set $jn_comp_kind
    local.get $v
    global.set $jn_comp_ret_i64
    i32.const 0
    global.set $jn_comp_ret_ptr
  )
  (func $jnative_completion_set_return_ptr (param $v i32)
    i32.const 1
    global.set $jn_comp_kind
    local.get $v
    global.set $jn_comp_ret_ptr
    i64.const 0
    global.set $jn_comp_ret_i64
  )
  (func $jnative_completion_set_break (param $t i32)
    i32.const 2
    global.set $jn_comp_kind
    local.get $t
    global.set $jn_comp_target
  )
  (func $jnative_completion_set_continue (param $t i32)
    i32.const 3
    global.set $jn_comp_kind
    local.get $t
    global.set $jn_comp_target
  )
  (func $jnative_completion_has_return (result i32)
    global.get $jn_comp_kind
    i32.const 1
    i32.eq
  )
  (func $jnative_completion_has_break (result i32)
    global.get $jn_comp_kind
    i32.const 2
    i32.eq
  )
  (func $jnative_completion_has_continue (result i32)
    global.get $jn_comp_kind
    i32.const 3
    i32.eq
  )
  (func $jnative_completion_take_return_i64 (result i64)
    (local $v i64)
    global.get $jn_comp_ret_i64
    local.set $v
    call $jnative_completion_clear
    local.get $v
  )
  (func $jnative_completion_take_return_ptr (result i32)
    (local $v i32)
    global.get $jn_comp_ret_ptr
    local.set $v
    call $jnative_completion_clear
    local.get $v
  )
  (func $jnative_completion_take_target (result i32)
    (local $v i32)
    global.get $jn_comp_target
    local.set $v
    call $jnative_completion_clear
    local.get $v
  )
  (func $jnative_exception_uncaught
    (local $ex i32) (local $msg i32) (local $ptr i32) (local $len i32)
    global.get $jn_pending_exc
    local.set $ex
    local.get $ex
    i32.eqz
    if
      return
    end
    local.get $ex
    i32.const 4
    i32.add
    i32.load
    local.set $msg
    local.get $msg
    i32.eqz
    if
      i32.const 0
      i32.const 0
      call $js_report_uncaught
    else
      local.get $msg
      call $jn_str_data
      local.set $ptr
      local.get $msg
      call $jn_str_len
      local.set $len
      local.get $ptr
      local.get $len
      call $js_report_uncaught
    end
    i32.const 0
    global.set $jn_pending_exc
  )
  (func $jn_raise_host_error (param $msg i32)
    i32.const 31
    local.get $msg
    call $jn_ex_alloc
    call $jnative_exception_throw
  )
  (func (export "jn_raise_host_error") (param $msg i32)
    local.get $msg
    call $jn_raise_host_error
  )
  (func (export "jn_exception_pending") (result i32)
    call $jnative_exception_pending
  )
  (func (export "jn_exception_peek") (result i32)
    call $jnative_exception_peek
  )
  (func (export "jn_exception_take") (result i32)
    call $jnative_exception_take
  )
  (func (export "jn_exception_clear")
    call $jnative_exception_clear
  )
  (func (export "jn_exception_uncaught")
    call $jnative_exception_uncaught
  )
  (func $jnative_clinit
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $gctmp.1 i32)
    (local $gctmp.4 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    i32.const 6
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb4
        (block $bb3
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $exit
        )
          i32.const 8
          call $jn_alloc_zeroed
          local.set $t0
          local.get $t0
          i32.eqz
          if
            unreachable
          end
          local.get $t0
          i32.const 22
          i32.store
          local.get $__jn_frame
          i32.const 2
          local.get $t0
          call $jn_gc_frame_set_root
          local.get $t0
          local.set $gctmp.1
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.1
          call $jn_gc_frame_set_root
          local.get $gctmp.1
          local.set $t1
          local.get $__jn_frame
          i32.const 3
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t1
          i32.const 1
          call $_JN_Boolean_init_i1
          call $jnative_exception_pending
          local.set $t2
          local.get $t2
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t1
          global.set $g_jn_static_Boolean_TRUE
          i32.const 8
          call $jn_alloc_zeroed
          local.set $t3
          local.get $t3
          i32.eqz
          if
            unreachable
          end
          local.get $t3
          i32.const 22
          i32.store
          local.get $__jn_frame
          i32.const 4
          local.get $t3
          call $jn_gc_frame_set_root
          local.get $t3
          local.set $gctmp.4
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.4
          call $jn_gc_frame_set_root
          local.get $gctmp.4
          local.set $t4
          local.get $__jn_frame
          i32.const 5
          local.get $t4
          call $jn_gc_frame_set_root
          local.get $t4
          i32.const 0
          call $_JN_Boolean_init_i1
          call $jnative_exception_pending
          local.set $t5
          local.get $t5
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t4
          global.set $g_jn_static_Boolean_FALSE
          i32.const 0
          global.set $g_jn_static_HashMap_EMPTY
          i32.const 1
          global.set $g_jn_static_HashMap_OCCUPIED
          i32.const 2
          global.set $g_jn_static_HashMap_DELETED
          br $exit
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_Boolean_init_i1 (param $this i32) (param $value i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $this__slot i32)
    (local $value__slot i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    i32.const 4
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $this
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb4
        (block $bb3
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $exit
        )
          local.get $this
          local.set $this__slot
          local.get $__jn_frame
          i32.const 1
          local.get $this__slot
          call $jn_gc_frame_set_root
          local.get $value
          local.set $value__slot
          local.get $this__slot
          local.set $t0
          local.get $__jn_frame
          i32.const 2
          local.get $t0
          call $jn_gc_frame_set_root
          local.get $t0
          call $_JN_Object_init
          call $jnative_exception_pending
          local.set $t1
          local.get $t1
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $value__slot
          local.set $t3
          local.get $this__slot
          local.set $t4
          local.get $__jn_frame
          i32.const 3
          local.get $t4
          call $jn_gc_frame_set_root
          local.get $t4
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t5
          local.get $t5
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t4
          i32.const 4
          i32.add
          local.get $t3
          i32.store
          br $exit
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_Object_init (param $this i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    i32.const 1
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $this
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb0
          local.get $pc
          br_table $bb0 $exit
        )
          br $exit
      )
    )
    call $jn_gc_frame_pop
  )
  (func $jn_main (param $args i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $args__slot i32)
    (local $keep i32)
    (local $t0 i32)
    (local $t1 f64)
    (local $t2 i32)
    (local $t3 f32)
    (local $t4 f32)
    (local $t5 f32)
    (local $t6 i32)
    (local $t7 f64)
    (local $t8 f64)
    (local $t9 i32)
    (local $t10 f32)
    (local $t11 i32)
    (local $t12 f64)
    (local $t13 f64)
    (local $t14 i32)
    (local $t15 f64)
    (local $t16 i32)
    (local $t17 f64)
    (local $t18 i32)
    (local $t19 f32)
    (local $t20 f32)
    (local $t21 i32)
    (local $t22 f64)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 f64)
    (local $t26 i64)
    (local $t27 i32)
    (local $t28 f64)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 f64)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 f64)
    (local $t35 f64)
    (local $t36 i32)
    (local $t37 f64)
    (local $t38 i32)
    (local $t39 f64)
    (local $t40 i32)
    (local $t41 f32)
    (local $t42 f32)
    (local $t43 f32)
    (local $t44 i32)
    (local $t45 f64)
    (local $t46 f64)
    (local $t47 i32)
    i32.const 2
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $args
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb36
        (block $bb35
        (block $bb34
        (block $bb33
        (block $bb32
        (block $bb31
        (block $bb30
        (block $bb29
        (block $bb28
        (block $bb27
        (block $bb26
        (block $bb25
        (block $bb24
        (block $bb23
        (block $bb22
        (block $bb21
        (block $bb20
        (block $bb19
        (block $bb18
        (block $bb17
        (block $bb16
        (block $bb15
        (block $bb14
        (block $bb13
        (block $bb12
        (block $bb11
        (block $bb10
        (block $bb9
        (block $bb8
        (block $bb7
        (block $bb6
        (block $bb5
        (block $bb4
        (block $bb3
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $exit
        )
          local.get $args
          local.set $args__slot
          local.get $__jn_frame
          i32.const 1
          local.get $args__slot
          call $jn_gc_frame_set_root
          i32.const 0
          local.set $keep
          local.get $keep
          local.set $t0
          local.get $t0
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          f64.const 0x1.0p0
          f64.const 0x1.0p1
          i32.const 0
          call $_JN_FloatCalc_compute_f64_f64_i32
          local.set $t1
          call $jnative_exception_pending
          local.set $t2
          local.get $t2
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          br $exit
        )
          local.get $t1
          global.set $g_jn_static_FloatCalc_lastLeft
          f64.const 0x1.0p0
          f32.demote_f64
          local.set $t3
          f64.const 0x1.0p1
          f32.demote_f64
          local.set $t4
          local.get $t3
          local.get $t4
          call $_JN_FloatCalc_addf_f32_f32
          local.set $t5
          call $jnative_exception_pending
          local.set $t6
          local.get $t6
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t5
          f64.promote_f32
          local.set $t7
          local.get $t7
          global.set $g_jn_static_FloatCalc_lastRight
          f64.const 0x1.0p0
          f64.const 0x1.0p1
          call $_JN_FloatCalc_arrayRoundTrip_f64_f64
          local.set $t8
          call $jnative_exception_pending
          local.set $t9
          local.get $t9
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t8
          global.set $g_jn_static_FloatCalc_lastLeft
          call $_JN_FloatCalc_floatPrecision
          local.set $t10
          call $jnative_exception_pending
          local.set $t11
          local.get $t11
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t10
          f64.promote_f32
          local.set $t12
          local.get $t12
          global.set $g_jn_static_FloatCalc_lastLeft
          f64.const 0x1.0p0
          f64.const 0x1.0p1
          call $_JN_FloatCalc_selectPos_f64_f64
          local.set $t13
          call $jnative_exception_pending
          local.set $t14
          local.get $t14
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t13
          global.set $g_jn_static_FloatCalc_lastLeft
          f64.const 0x1.0p0
          call $_JN_FloatCalc_negate_f64
          local.set $t15
          call $jnative_exception_pending
          local.set $t16
          local.get $t16
          if
            i32.const 13
            local.set $pc
          else
            i32.const 14
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t15
          global.set $g_jn_static_FloatCalc_lastLeft
          i32.const 1
          call $_JN_FloatCalc_fromInt_i32
          local.set $t17
          call $jnative_exception_pending
          local.set $t18
          local.get $t18
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t17
          global.set $g_jn_static_FloatCalc_lastLeft
          f64.const 0x1.0p0
          f32.demote_f64
          local.set $t19
          local.get $t19
          call $_JN_FloatCalc_promoteDemote_f32
          local.set $t20
          call $jnative_exception_pending
          local.set $t21
          local.get $t21
          if
            i32.const 17
            local.set $pc
          else
            i32.const 18
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t20
          f64.promote_f32
          local.set $t22
          local.get $t22
          global.set $g_jn_static_FloatCalc_lastLeft
          f64.const 0x1.0p0
          call $_JN_FloatCalc_toInt_f64
          local.set $t23
          call $jnative_exception_pending
          local.set $t24
          local.get $t24
          if
            i32.const 19
            local.set $pc
          else
            i32.const 20
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t23
          f64.convert_i32_s
          local.set $t25
          local.get $t25
          global.set $g_jn_static_FloatCalc_lastLeft
          f64.const 0x1.0p0
          call $_JN_FloatCalc_toLong_f64
          local.set $t26
          call $jnative_exception_pending
          local.set $t27
          local.get $t27
          if
            i32.const 21
            local.set $pc
          else
            i32.const 22
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t26
          f64.convert_i64_s
          local.set $t28
          local.get $t28
          global.set $g_jn_static_FloatCalc_lastLeft
          f64.const 0x1.0p0
          f64.const 0x1.0p0
          call $_JN_FloatCalc_cmpEq_f64_f64
          local.set $t29
          call $jnative_exception_pending
          local.set $t30
          local.get $t30
          if
            i32.const 23
            local.set $pc
          else
            i32.const 24
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t29
          f64.convert_i32_s
          local.set $t31
          local.get $t31
          global.set $g_jn_static_FloatCalc_lastLeft
          f64.const 0x1.0p0
          f64.const 0x1.0p1
          call $_JN_FloatCalc_cmpLt_f64_f64
          local.set $t32
          call $jnative_exception_pending
          local.set $t33
          local.get $t33
          if
            i32.const 25
            local.set $pc
          else
            i32.const 26
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t32
          f64.convert_i32_s
          local.set $t34
          local.get $t34
          global.set $g_jn_static_FloatCalc_lastLeft
          f64.const 0x1.0p0
          f64.const 0x1.0p0
          call $_JN_FloatCalc_sub_f64_f64
          local.set $t35
          call $jnative_exception_pending
          local.set $t36
          local.get $t36
          if
            i32.const 27
            local.set $pc
          else
            i32.const 28
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t35
          global.set $g_jn_static_FloatCalc_lastLeft
          f64.const 0x1.0p0
          f64.const 0x1.0p0
          call $_JN_FloatCalc_mul_f64_f64
          local.set $t37
          call $jnative_exception_pending
          local.set $t38
          local.get $t38
          if
            i32.const 29
            local.set $pc
          else
            i32.const 30
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t37
          global.set $g_jn_static_FloatCalc_lastLeft
          f64.const 0x1.0p0
          f64.const 0x1.0p0
          call $_JN_FloatCalc_div_f64_f64
          local.set $t39
          call $jnative_exception_pending
          local.set $t40
          local.get $t40
          if
            i32.const 31
            local.set $pc
          else
            i32.const 32
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t39
          global.set $g_jn_static_FloatCalc_lastLeft
          f64.const 0x1.0p0
          f32.demote_f64
          local.set $t41
          f64.const 0x1.0p0
          f32.demote_f64
          local.set $t42
          local.get $t41
          local.get $t42
          call $_JN_FloatCalc_mulf_f32_f32
          local.set $t43
          call $jnative_exception_pending
          local.set $t44
          local.get $t44
          if
            i32.const 33
            local.set $pc
          else
            i32.const 34
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t43
          f64.promote_f32
          local.set $t45
          local.get $t45
          global.set $g_jn_static_FloatCalc_lastLeft
          f64.const 0x1.0p0
          f64.const 0x1.0p0
          call $_JN_FloatCalc_add_f64_f64
          local.set $t46
          call $jnative_exception_pending
          local.set $t47
          local.get $t47
          if
            i32.const 35
            local.set $pc
          else
            i32.const 36
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t46
          global.set $g_jn_static_FloatCalc_lastLeft
          i32.const 2
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_FloatCalc_compute_f64_f64_i32 (param $a f64) (param $b f64) (param $op i32) (result f64)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret f64)
    (local $a__slot f64)
    (local $b__slot f64)
    (local $op__slot i32)
    (local $t0 f64)
    (local $t1 f64)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 f64)
    (local $t5 f64)
    (local $t6 f64)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 f64)
    (local $t11 f64)
    (local $t12 f64)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 f64)
    (local $t17 f64)
    (local $t18 f64)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 f64)
    (local $t23 f64)
    (local $t24 f64)
    (local $t25 i32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb16
        (block $bb15
        (block $bb14
        (block $bb13
        (block $bb12
        (block $bb11
        (block $bb10
        (block $bb9
        (block $bb8
        (block $bb7
        (block $bb6
        (block $bb5
        (block $bb4
        (block $bb3
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $b
          local.set $b__slot
          local.get $op
          local.set $op__slot
          local.get $a__slot
          local.set $t0
          local.get $t0
          global.set $g_jn_static_FloatCalc_lastLeft
          local.get $b__slot
          local.set $t1
          local.get $t1
          global.set $g_jn_static_FloatCalc_lastRight
          local.get $op__slot
          local.set $t2
          local.get $t2
          i32.const 0
          i32.eq
          local.set $t3
          local.get $t3
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          local.get $a__slot
          local.set $t4
          local.get $b__slot
          local.set $t5
          local.get $t4
          local.get $t5
          call $_JN_FloatCalc_add_f64_f64
          local.set $t6
          call $jnative_exception_pending
          local.set $t7
          local.get $t7
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          local.get $op__slot
          local.set $t8
          local.get $t8
          i32.const 1
          i32.eq
          local.set $t9
          local.get $t9
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          f64.const 0x0p0
          local.set $ret
          br $exit
        )
          local.get $t6
          local.set $ret
          br $exit
        )
          local.get $a__slot
          local.set $t10
          local.get $b__slot
          local.set $t11
          local.get $t10
          local.get $t11
          call $_JN_FloatCalc_sub_f64_f64
          local.set $t12
          call $jnative_exception_pending
          local.set $t13
          local.get $t13
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          local.get $op__slot
          local.set $t14
          local.get $t14
          i32.const 2
          i32.eq
          local.set $t15
          local.get $t15
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          f64.const 0x0p0
          local.set $ret
          br $exit
        )
          local.get $t12
          local.set $ret
          br $exit
        )
          local.get $a__slot
          local.set $t16
          local.get $b__slot
          local.set $t17
          local.get $t16
          local.get $t17
          call $_JN_FloatCalc_mul_f64_f64
          local.set $t18
          call $jnative_exception_pending
          local.set $t19
          local.get $t19
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          local.get $op__slot
          local.set $t20
          local.get $t20
          i32.const 3
          i32.eq
          local.set $t21
          local.get $t21
          if
            i32.const 13
            local.set $pc
          else
            i32.const 14
            local.set $pc
          end
          br $dispatch
        )
          f64.const 0x0p0
          local.set $ret
          br $exit
        )
          local.get $t18
          local.set $ret
          br $exit
        )
          local.get $a__slot
          local.set $t22
          local.get $b__slot
          local.set $t23
          local.get $t22
          local.get $t23
          call $_JN_FloatCalc_div_f64_f64
          local.set $t24
          call $jnative_exception_pending
          local.set $t25
          local.get $t25
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          f64.const nan:0x8000000000000
          local.set $ret
          br $exit
        )
          f64.const 0x0p0
          local.set $ret
          br $exit
        )
          local.get $t24
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_addf_f32_f32 (param $a f32) (param $b f32) (result f32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret f32)
    (local $a__slot f32)
    (local $b__slot f32)
    (local $t0 f32)
    (local $t1 f32)
    (local $t2 f32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb0
          local.get $pc
          br_table $bb0 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $b
          local.set $b__slot
          local.get $a__slot
          local.set $t0
          local.get $b__slot
          local.set $t1
          local.get $t0
          local.get $t1
          f32.add
          local.set $t2
          local.get $t2
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_arrayRoundTrip_f64_f64 (param $a f64) (param $b f64) (result f64)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret f64)
    (local $a__slot f64)
    (local $b__slot f64)
    (local $xs i32)
    (local $gctmp.2 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 f64)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 f64)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 f64)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 f64)
    (local $t14 i32)
    (local $t15 f64)
    i32.const 8
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb10
        (block $bb9
        (block $bb8
        (block $bb7
        (block $bb6
        (block $bb5
        (block $bb4
        (block $bb3
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $b
          local.set $b__slot
          i32.const 2
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 2
          i32.const 8
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 8
          i32.div_u
          i32.const 2
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t0
          local.get $t0
          i32.eqz
          if
            unreachable
          end
          local.get $t0
          i32.const 76
          i32.store
          local.get $t0
          i32.const 8
          i32.add
          i32.const 2
          i32.store
          local.get $__jn_frame
          i32.const 2
          local.get $t0
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t1
          local.get $t1
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          f64.const 0x0p0
          local.set $ret
          br $exit
        )
          local.get $t0
          local.set $gctmp.2
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.2
          call $jn_gc_frame_set_root
          local.get $gctmp.2
          local.set $t2
          local.get $__jn_frame
          i32.const 3
          local.get $t2
          call $jn_gc_frame_set_root
          local.get $t2
          local.set $xs
          local.get $__jn_frame
          i32.const 0
          local.get $xs
          call $jn_gc_frame_set_root
          local.get $a__slot
          local.set $t3
          local.get $xs
          local.set $t4
          local.get $__jn_frame
          i32.const 4
          local.get $t4
          call $jn_gc_frame_set_root
          local.get $t4
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t4
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t4
          i32.const 16
          i32.add
          i32.const 0
          i32.const 8
          i32.mul
          i32.add
          local.get $t3
          f64.store
          call $jnative_exception_pending
          local.set $t5
          local.get $t5
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          f64.const 0x0p0
          local.set $ret
          br $exit
        )
          local.get $b__slot
          local.set $t6
          local.get $xs
          local.set $t7
          local.get $__jn_frame
          i32.const 5
          local.get $t7
          call $jn_gc_frame_set_root
          local.get $t7
          i32.eqz
          if
            unreachable
          end
          i32.const 1
          i32.const 0
          i32.lt_s
          i32.const 1
          local.get $t7
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t7
          i32.const 16
          i32.add
          i32.const 1
          i32.const 8
          i32.mul
          i32.add
          local.get $t6
          f64.store
          call $jnative_exception_pending
          local.set $t8
          local.get $t8
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          f64.const 0x0p0
          local.set $ret
          br $exit
        )
          local.get $xs
          local.set $t9
          local.get $__jn_frame
          i32.const 6
          local.get $t9
          call $jn_gc_frame_set_root
          local.get $t9
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t9
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t9
          i32.const 16
          i32.add
          i32.const 0
          i32.const 8
          i32.mul
          i32.add
          f64.load
          local.set $t10
          call $jnative_exception_pending
          local.set $t11
          local.get $t11
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          f64.const 0x0p0
          local.set $ret
          br $exit
        )
          local.get $xs
          local.set $t12
          local.get $__jn_frame
          i32.const 7
          local.get $t12
          call $jn_gc_frame_set_root
          local.get $t12
          i32.eqz
          if
            unreachable
          end
          i32.const 1
          i32.const 0
          i32.lt_s
          i32.const 1
          local.get $t12
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t12
          i32.const 16
          i32.add
          i32.const 1
          i32.const 8
          i32.mul
          i32.add
          f64.load
          local.set $t13
          call $jnative_exception_pending
          local.set $t14
          local.get $t14
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          f64.const 0x0p0
          local.set $ret
          br $exit
        )
          local.get $t10
          local.get $t13
          f64.add
          local.set $t15
          local.get $t15
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_FloatCalc_floatPrecision (result f32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret f32)
    (local $big f32)
    (local $one f32)
    (local $t0 f32)
    (local $t1 f32)
    (local $t2 f32)
    (local $t3 f32)
    (local $t4 f32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb0
          local.get $pc
          br_table $bb0 $exit
        )
          f64.const 0x1.0p24
          f32.demote_f64
          local.set $t0
          local.get $t0
          local.set $big
          f64.const 0x1.0p0
          f32.demote_f64
          local.set $t1
          local.get $t1
          local.set $one
          local.get $big
          local.set $t2
          local.get $one
          local.set $t3
          local.get $t2
          local.get $t3
          f32.add
          local.set $t4
          local.get $t4
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_selectPos_f64_f64 (param $a f64) (param $b f64) (result f64)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret f64)
    (local $a__slot f64)
    (local $b__slot f64)
    (local $t0 f64)
    (local $t1 i32)
    (local $t2 f64)
    (local $t3 f64)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $b
          local.set $b__slot
          local.get $a__slot
          local.set $t0
          local.get $t0
          f64.const 0x0p0
          f64.ge
          local.set $t1
          local.get $t1
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          local.get $a__slot
          local.set $t2
          local.get $t2
          local.set $ret
          br $exit
        )
          local.get $b__slot
          local.set $t3
          local.get $t3
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_negate_f64 (param $a f64) (result f64)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret f64)
    (local $a__slot f64)
    (local $t0 f64)
    (local $t1 f64)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb0
          local.get $pc
          br_table $bb0 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $a__slot
          local.set $t0
          f64.const -0x0p0
          local.get $t0
          f64.sub
          local.set $t1
          local.get $t1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_fromInt_i32 (param $a i32) (result f64)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret f64)
    (local $a__slot i32)
    (local $t0 i32)
    (local $t1 f64)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb0
          local.get $pc
          br_table $bb0 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $a__slot
          local.set $t0
          local.get $t0
          f64.convert_i32_s
          local.set $t1
          local.get $t1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_promoteDemote_f32 (param $a f32) (result f32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret f32)
    (local $a__slot f32)
    (local $d f64)
    (local $t0 f32)
    (local $t1 f64)
    (local $t2 f64)
    (local $t3 f32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb0
          local.get $pc
          br_table $bb0 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $a__slot
          local.set $t0
          local.get $t0
          f64.promote_f32
          local.set $t1
          local.get $t1
          local.set $d
          local.get $d
          local.set $t2
          local.get $t2
          f32.demote_f64
          local.set $t3
          local.get $t3
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_toInt_f64 (param $a f64) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $a__slot f64)
    (local $t0 f64)
    (local $t1 i32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb0
          local.get $pc
          br_table $bb0 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $a__slot
          local.set $t0
          local.get $t0
          i32.trunc_sat_f64_s
          local.set $t1
          local.get $t1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_toLong_f64 (param $a f64) (result i64)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i64)
    (local $a__slot f64)
    (local $t0 f64)
    (local $t1 i64)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb0
          local.get $pc
          br_table $bb0 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $a__slot
          local.set $t0
          local.get $t0
          i64.trunc_sat_f64_s
          local.set $t1
          local.get $t1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_cmpEq_f64_f64 (param $a f64) (param $b f64) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $a__slot f64)
    (local $b__slot f64)
    (local $t0 f64)
    (local $t1 f64)
    (local $t2 i32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $b
          local.set $b__slot
          local.get $a__slot
          local.set $t0
          local.get $b__slot
          local.set $t1
          local.get $t0
          local.get $t1
          f64.eq
          local.set $t2
          local.get $t2
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_cmpLt_f64_f64 (param $a f64) (param $b f64) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $a__slot f64)
    (local $b__slot f64)
    (local $t0 f64)
    (local $t1 f64)
    (local $t2 i32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $b
          local.set $b__slot
          local.get $a__slot
          local.set $t0
          local.get $b__slot
          local.set $t1
          local.get $t0
          local.get $t1
          f64.lt
          local.set $t2
          local.get $t2
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_sub_f64_f64 (param $a f64) (param $b f64) (result f64)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret f64)
    (local $a__slot f64)
    (local $b__slot f64)
    (local $t0 f64)
    (local $t1 f64)
    (local $t2 f64)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb0
          local.get $pc
          br_table $bb0 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $b
          local.set $b__slot
          local.get $a__slot
          local.set $t0
          local.get $b__slot
          local.set $t1
          local.get $t0
          local.get $t1
          f64.sub
          local.set $t2
          local.get $t2
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_mul_f64_f64 (param $a f64) (param $b f64) (result f64)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret f64)
    (local $a__slot f64)
    (local $b__slot f64)
    (local $t0 f64)
    (local $t1 f64)
    (local $t2 f64)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb0
          local.get $pc
          br_table $bb0 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $b
          local.set $b__slot
          local.get $a__slot
          local.set $t0
          local.get $b__slot
          local.set $t1
          local.get $t0
          local.get $t1
          f64.mul
          local.set $t2
          local.get $t2
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_div_f64_f64 (param $a f64) (param $b f64) (result f64)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret f64)
    (local $a__slot f64)
    (local $b__slot f64)
    (local $t0 f64)
    (local $t1 f64)
    (local $t2 f64)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb0
          local.get $pc
          br_table $bb0 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $b
          local.set $b__slot
          local.get $a__slot
          local.set $t0
          local.get $b__slot
          local.set $t1
          local.get $t0
          local.get $t1
          f64.div
          local.set $t2
          local.get $t2
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_mulf_f32_f32 (param $a f32) (param $b f32) (result f32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret f32)
    (local $a__slot f32)
    (local $b__slot f32)
    (local $t0 f32)
    (local $t1 f32)
    (local $t2 f32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb0
          local.get $pc
          br_table $bb0 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $b
          local.set $b__slot
          local.get $a__slot
          local.set $t0
          local.get $b__slot
          local.set $t1
          local.get $t0
          local.get $t1
          f32.mul
          local.set $t2
          local.get $t2
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_FloatCalc_add_f64_f64 (param $a f64) (param $b f64) (result f64)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret f64)
    (local $a__slot f64)
    (local $b__slot f64)
    (local $t0 f64)
    (local $t1 f64)
    (local $t2 f64)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb0
          local.get $pc
          br_table $bb0 $exit
        )
          local.get $a
          local.set $a__slot
          local.get $b
          local.set $b__slot
          local.get $a__slot
          local.set $t0
          local.get $b__slot
          local.set $t1
          local.get $t0
          local.get $t1
          f64.add
          local.set $t2
          local.get $t2
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_NoSuchElementException_init_CString (param $this i32) (param $message i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $this__slot i32)
    (local $message__slot i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    i32.const 6
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $this
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $message
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $exit
        )
          local.get $this
          local.set $this__slot
          local.get $__jn_frame
          i32.const 2
          local.get $this__slot
          call $jn_gc_frame_set_root
          local.get $this__slot
          local.set $t0
          local.get $__jn_frame
          i32.const 4
          local.get $t0
          call $jn_gc_frame_set_root
          local.get $message
          local.set $message__slot
          local.get $__jn_frame
          i32.const 3
          local.get $message__slot
          call $jn_gc_frame_set_root
          local.get $message__slot
          local.set $t1
          local.get $__jn_frame
          i32.const 5
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t0
          local.get $t1
          call $_JN_RuntimeException_init_CString
          call $jnative_exception_pending
          local.set $t2
          local.get $t2
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          br $exit
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_RuntimeException_init_CString (param $this i32) (param $message i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $this__slot i32)
    (local $message__slot i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    i32.const 6
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $this
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $message
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $exit
        )
          local.get $this
          local.set $this__slot
          local.get $__jn_frame
          i32.const 2
          local.get $this__slot
          call $jn_gc_frame_set_root
          local.get $this__slot
          local.set $t0
          local.get $__jn_frame
          i32.const 4
          local.get $t0
          call $jn_gc_frame_set_root
          local.get $message
          local.set $message__slot
          local.get $__jn_frame
          i32.const 3
          local.get $message__slot
          call $jn_gc_frame_set_root
          local.get $message__slot
          local.set $t1
          local.get $__jn_frame
          i32.const 5
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t0
          local.get $t1
          call $_JN_Exception_init_CString
          call $jnative_exception_pending
          local.set $t2
          local.get $t2
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          br $exit
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_Exception_init_CString (param $this i32) (param $message i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $this__slot i32)
    (local $message__slot i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    i32.const 6
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $this
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $message
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $exit
        )
          local.get $this
          local.set $this__slot
          local.get $__jn_frame
          i32.const 2
          local.get $this__slot
          call $jn_gc_frame_set_root
          local.get $this__slot
          local.set $t0
          local.get $__jn_frame
          i32.const 4
          local.get $t0
          call $jn_gc_frame_set_root
          local.get $message
          local.set $message__slot
          local.get $__jn_frame
          i32.const 3
          local.get $message__slot
          call $jn_gc_frame_set_root
          local.get $message__slot
          local.set $t1
          local.get $__jn_frame
          i32.const 5
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t0
          local.get $t1
          call $_JN_Throwable_init_CString
          call $jnative_exception_pending
          local.set $t2
          local.get $t2
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          br $exit
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_Throwable_init_CString (param $this i32) (param $message i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $this__slot i32)
    (local $message__slot i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    i32.const 6
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $this
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $message
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $exit
        )
          local.get $this
          local.set $this__slot
          local.get $__jn_frame
          i32.const 2
          local.get $this__slot
          call $jn_gc_frame_set_root
          local.get $message
          local.set $message__slot
          local.get $__jn_frame
          i32.const 3
          local.get $message__slot
          call $jn_gc_frame_set_root
          local.get $this__slot
          local.set $t0
          local.get $__jn_frame
          i32.const 4
          local.get $t0
          call $jn_gc_frame_set_root
          local.get $message__slot
          local.set $t1
          local.get $__jn_frame
          i32.const 5
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t0
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t2
          local.get $t2
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t0
          i32.const 4
          i32.add
          local.get $t1
          i32.store
          br $exit
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_ConcurrentModificationException_init_CString (param $this i32) (param $message i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $this__slot i32)
    (local $message__slot i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    i32.const 6
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $this
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $message
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $exit
        )
          local.get $this
          local.set $this__slot
          local.get $__jn_frame
          i32.const 2
          local.get $this__slot
          call $jn_gc_frame_set_root
          local.get $this__slot
          local.set $t0
          local.get $__jn_frame
          i32.const 4
          local.get $t0
          call $jn_gc_frame_set_root
          local.get $message
          local.set $message__slot
          local.get $__jn_frame
          i32.const 3
          local.get $message__slot
          call $jn_gc_frame_set_root
          local.get $message__slot
          local.set $t1
          local.get $__jn_frame
          i32.const 5
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t0
          local.get $t1
          call $_JN_RuntimeException_init_CString
          call $jnative_exception_pending
          local.set $t2
          local.get $t2
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          br $exit
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_IndexOutOfBoundsException_init_CString (param $this i32) (param $message i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $this__slot i32)
    (local $message__slot i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    i32.const 6
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $this
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $message
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $exit
        )
          local.get $this
          local.set $this__slot
          local.get $__jn_frame
          i32.const 2
          local.get $this__slot
          call $jn_gc_frame_set_root
          local.get $this__slot
          local.set $t0
          local.get $__jn_frame
          i32.const 4
          local.get $t0
          call $jn_gc_frame_set_root
          local.get $message
          local.set $message__slot
          local.get $__jn_frame
          i32.const 3
          local.get $message__slot
          call $jn_gc_frame_set_root
          local.get $message__slot
          local.set $t1
          local.get $__jn_frame
          i32.const 5
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t0
          local.get $t1
          call $_JN_RuntimeException_init_CString
          call $jnative_exception_pending
          local.set $t2
          local.get $t2
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          br $exit
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_NullPointerException_init_CString (param $this i32) (param $message i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $this__slot i32)
    (local $message__slot i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    i32.const 6
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $this
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $message
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $exit
        )
          local.get $this
          local.set $this__slot
          local.get $__jn_frame
          i32.const 2
          local.get $this__slot
          call $jn_gc_frame_set_root
          local.get $this__slot
          local.set $t0
          local.get $__jn_frame
          i32.const 4
          local.get $t0
          call $jn_gc_frame_set_root
          local.get $message
          local.set $message__slot
          local.get $__jn_frame
          i32.const 3
          local.get $message__slot
          call $jn_gc_frame_set_root
          local.get $message__slot
          local.set $t1
          local.get $__jn_frame
          i32.const 5
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t0
          local.get $t1
          call $_JN_RuntimeException_init_CString
          call $jnative_exception_pending
          local.set $t2
          local.get $t2
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          br $exit
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_IllegalStateException_init_CString (param $this i32) (param $message i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $this__slot i32)
    (local $message__slot i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    i32.const 6
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $this
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $message
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $exit
        )
          local.get $this
          local.set $this__slot
          local.get $__jn_frame
          i32.const 2
          local.get $this__slot
          call $jn_gc_frame_set_root
          local.get $this__slot
          local.set $t0
          local.get $__jn_frame
          i32.const 4
          local.get $t0
          call $jn_gc_frame_set_root
          local.get $message
          local.set $message__slot
          local.get $__jn_frame
          i32.const 3
          local.get $message__slot
          call $jn_gc_frame_set_root
          local.get $message__slot
          local.set $t1
          local.get $__jn_frame
          i32.const 5
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t0
          local.get $t1
          call $_JN_RuntimeException_init_CString
          call $jnative_exception_pending
          local.set $t2
          local.get $t2
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          br $exit
      )
    )
    call $jn_gc_frame_pop
  )
  (func $jn_super_of (param $tid i32) (result i32)
    local.get $tid
    i32.const 1
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 2
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 3
    i32.eq
    if
      i32.const 13
      return
    end
    local.get $tid
    i32.const 4
    i32.eq
    if
      i32.const 31
      return
    end
    local.get $tid
    i32.const 5
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 6
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 7
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 8
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 9
    i32.eq
    if
      i32.const 27
      return
    end
    local.get $tid
    i32.const 10
    i32.eq
    if
      i32.const 31
      return
    end
    local.get $tid
    i32.const 11
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 12
    i32.eq
    if
      i32.const 27
      return
    end
    local.get $tid
    i32.const 13
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 14
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 15
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 16
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 17
    i32.eq
    if
      i32.const 31
      return
    end
    local.get $tid
    i32.const 18
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 19
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 20
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 21
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 22
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 23
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 24
    i32.eq
    if
      i32.const 5
      return
    end
    local.get $tid
    i32.const 25
    i32.eq
    if
      i32.const 31
      return
    end
    local.get $tid
    i32.const 26
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 27
    i32.eq
    if
      i32.const 55
      return
    end
    local.get $tid
    i32.const 28
    i32.eq
    if
      i32.const 27
      return
    end
    local.get $tid
    i32.const 29
    i32.eq
    if
      i32.const 31
      return
    end
    local.get $tid
    i32.const 30
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 31
    i32.eq
    if
      i32.const 3
      return
    end
    local.get $tid
    i32.const 32
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 33
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 34
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 35
    i32.eq
    if
      i32.const 31
      return
    end
    local.get $tid
    i32.const 36
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 37
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 38
    i32.eq
    if
      i32.const 55
      return
    end
    local.get $tid
    i32.const 39
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 40
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 41
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 42
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 43
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 44
    i32.eq
    if
      i32.const 31
      return
    end
    local.get $tid
    i32.const 45
    i32.eq
    if
      i32.const 31
      return
    end
    local.get $tid
    i32.const 46
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 47
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 48
    i32.eq
    if
      i32.const 31
      return
    end
    local.get $tid
    i32.const 49
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 50
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 51
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 52
    i32.eq
    if
      i32.const 31
      return
    end
    local.get $tid
    i32.const 53
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 54
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 55
    i32.eq
    if
      i32.const 3
      return
    end
    local.get $tid
    i32.const 56
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 57
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 58
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 59
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 60
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 61
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 62
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 63
    i32.eq
    if
      i32.const 42
      return
    end
    local.get $tid
    i32.const 64
    i32.eq
    if
      i32.const 31
      return
    end
    local.get $tid
    i32.const 65
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 66
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 67
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 68
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 69
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 70
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 71
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 72
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 73
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 74
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 75
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 76
    i32.eq
    if
      i32.const 0
      return
    end
    i32.const 0
  )
  (func $jn_instanceof (param $obj i32) (param $target i32) (result i32)
    (local $tid i32)
    local.get $obj
    i32.eqz
    if (result i32)
      i32.const 0
    else
      local.get $obj
      i32.load
      local.set $tid
      (block $done (result i32)
        (loop $walk
          local.get $tid
          i32.eqz
          if
            i32.const 0
            br $done
          end
          local.get $tid
          local.get $target
          i32.eq
          if
            i32.const 1
            br $done
          end
          local.get $tid
          call $jn_super_of
          local.set $tid
          br $walk
        )
        i32.const 0
      )
    end
  )
  (func $jn_instanceof_iface (param $obj i32) (param $iid i32) (result i32)
    (local $tid i32)
    local.get $obj
    i32.eqz
    if (result i32)
      i32.const 0
    else
      local.get $obj
      i32.load
      local.set $tid
      local.get $tid
      i32.const 32
      i32.eq
      local.get $iid
      i32.const 1
      i32.eq
      i32.and
      if
        i32.const 1
        return
      end
      local.get $tid
      i32.const 50
      i32.eq
      local.get $iid
      i32.const 1
      i32.eq
      i32.and
      if
        i32.const 1
        return
      end
      local.get $tid
      i32.const 23
      i32.eq
      local.get $iid
      i32.const 2
      i32.eq
      i32.and
      if
        i32.const 1
        return
      end
      local.get $tid
      i32.const 51
      i32.eq
      local.get $iid
      i32.const 2
      i32.eq
      i32.and
      if
        i32.const 1
        return
      end
      local.get $tid
      i32.const 57
      i32.eq
      local.get $iid
      i32.const 2
      i32.eq
      i32.and
      if
        i32.const 1
        return
      end
      local.get $tid
      i32.const 32
      i32.eq
      local.get $iid
      i32.const 3
      i32.eq
      i32.and
      if
        i32.const 1
        return
      end
      local.get $tid
      i32.const 50
      i32.eq
      local.get $iid
      i32.const 3
      i32.eq
      i32.and
      if
        i32.const 1
        return
      end
      local.get $tid
      i32.const 47
      i32.eq
      local.get $iid
      i32.const 20
      i32.eq
      i32.and
      if
        i32.const 1
        return
      end
      local.get $tid
      i32.const 32
      i32.eq
      local.get $iid
      i32.const 4
      i32.eq
      i32.and
      if
        i32.const 1
        return
      end
      local.get $tid
      i32.const 50
      i32.eq
      local.get $iid
      i32.const 5
      i32.eq
      i32.and
      if
        i32.const 1
        return
      end
      local.get $tid
      i32.const 53
      i32.eq
      local.get $iid
      i32.const 6
      i32.eq
      i32.and
      if
        i32.const 1
        return
      end
      local.get $tid
      i32.const 58
      i32.eq
      local.get $iid
      i32.const 7
      i32.eq
      i32.and
      if
        i32.const 1
        return
      end
      i32.const 0
    end
  )
  (export "_JN_FloatCalc_add_f64_f64" (func $_JN_FloatCalc_add_f64_f64))
  (export "_JN_FloatCalc_sub_f64_f64" (func $_JN_FloatCalc_sub_f64_f64))
  (export "_JN_FloatCalc_mul_f64_f64" (func $_JN_FloatCalc_mul_f64_f64))
  (export "_JN_FloatCalc_div_f64_f64" (func $_JN_FloatCalc_div_f64_f64))
  (export "_JN_FloatCalc_addf_f32_f32" (func $_JN_FloatCalc_addf_f32_f32))
  (export "_JN_FloatCalc_mulf_f32_f32" (func $_JN_FloatCalc_mulf_f32_f32))
  (export "_JN_FloatCalc_compute_f64_f64_i32" (func $_JN_FloatCalc_compute_f64_f64_i32))
  (export "_JN_FloatCalc_negate_f64" (func $_JN_FloatCalc_negate_f64))
  (export "_JN_FloatCalc_cmpEq_f64_f64" (func $_JN_FloatCalc_cmpEq_f64_f64))
  (export "_JN_FloatCalc_cmpLt_f64_f64" (func $_JN_FloatCalc_cmpLt_f64_f64))
  (export "_JN_FloatCalc_toInt_f64" (func $_JN_FloatCalc_toInt_f64))
  (export "_JN_FloatCalc_toLong_f64" (func $_JN_FloatCalc_toLong_f64))
  (export "_JN_FloatCalc_fromInt_i32" (func $_JN_FloatCalc_fromInt_i32))
  (export "_JN_FloatCalc_promoteDemote_f32" (func $_JN_FloatCalc_promoteDemote_f32))
  (export "_JN_FloatCalc_arrayRoundTrip_f64_f64" (func $_JN_FloatCalc_arrayRoundTrip_f64_f64))
  (export "_JN_FloatCalc_floatPrecision" (func $_JN_FloatCalc_floatPrecision))
  (export "_JN_FloatCalc_selectPos_f64_f64" (func $_JN_FloatCalc_selectPos_f64_f64))
  (export "_JN_NoSuchElementException_init_CString" (func $_JN_NoSuchElementException_init_CString))
  (export "_JN_ConcurrentModificationException_init_CString" (func $_JN_ConcurrentModificationException_init_CString))
  (export "_JN_IndexOutOfBoundsException_init_CString" (func $_JN_IndexOutOfBoundsException_init_CString))
  (export "_JN_NullPointerException_init_CString" (func $_JN_NullPointerException_init_CString))
  (export "_JN_IllegalStateException_init_CString" (func $_JN_IllegalStateException_init_CString))
  (export "jn_main" (func $jn_main))
  (func (export "jnative_callback") (param $id i32) (param $ev i32)
    (local $callable i32) (local $env i32) (local $idx i32)
    (local $hobj i32) (local $eobj i32)
    local.get $id
    call $jn_lookup_dom_callback
    local.set $callable
    local.get $callable
    i32.eqz
    if
      return
    end
    local.get $callable
    i32.const 16
    i32.add
    i32.load
    local.set $env
    local.get $callable
    i32.const 8
    i32.add
    i64.load
    i32.wrap_i64
    local.set $idx
    i32.const 8
    call $jn_alloc_zeroed
    local.set $hobj
    local.get $hobj
    i32.const 0
    i32.store
    local.get $hobj
    i32.const 4
    i32.add
    local.get $ev
    i32.store
    i32.const 8
    call $jn_alloc_zeroed
    local.set $eobj
    local.get $eobj
    i32.const 0
    i32.store
    local.get $eobj
    i32.const 4
    i32.add
    local.get $hobj
    i32.store
    local.get $env
    local.get $eobj
    local.get $idx
    call_indirect $jn_funcref (type $jn_sig_0)
    call $jnative_exception_pending
    if
      call $jnative_exception_uncaught
    end
  )
  (func (export "jnative_start")
    call $jnative_clinit
    call $jnative_exception_pending
    if
      call $jnative_exception_uncaught
      return
    end
    i32.const 0
    call $jn_main
    call $jnative_exception_pending
    if
      call $jnative_exception_uncaught
    end
  )
  (func (export "jn_bump") (param $n i32) (result i32)
    local.get $n
    call $jn_alloc_opaque
  )
  (func (export "jn_release_callback") (param $id i32)
    local.get $id
    call $jn_unregister_dom_callback
  )
  (export "jn_host_root_retain" (func $jn_host_root_retain))
  (export "jn_host_root_release" (func $jn_host_root_release))
  (export "jn_host_root_get" (func $jn_host_root_get))
  (export "jn_gc_collect" (func $jn_gc_collect))
  (func (export "jn_gc_stats") (result i32 i32 i32 i32 i32 i32 i32)
    call $jn_gc_stats_alloc_bytes
    call $jn_gc_stats_live_bytes
    call $jn_gc_stats_free_bytes
    call $jn_gc_stats_heap_cap
    call $jn_gc_stats_gc_count
    call $jn_gc_stats_host_roots
    call $jn_gc_stats_alloc_fail
  )
)
