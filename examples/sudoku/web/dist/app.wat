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
  (global $g_jn_static_SudokuCore_SAVE_VERSION (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_MAX_SAVE_CHARS (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_MAX_HISTORY (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_GEN_MAX_ATTEMPTS (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_GEN_MAX_STEPS (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_DIFF_EASY (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_DIFF_MEDIUM (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_DIFF_HARD (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_ST_PLAYING (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_ST_PAUSED (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_ST_WON (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_lastError (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_values (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_given (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_notes (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_conflict (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_solution (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_selected (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_noteMode (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_status (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_puzzleId (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_difficulty (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_elapsedMs (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_lastTickMs (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_tickValid (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_histCell (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_histPrevVal (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_histNewVal (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_histPrevNotes (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_histNewNotes (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_histLen (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_histPos (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_saveBuf (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_saveLen (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_work (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_work2 (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_solveSteps (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_genSteps (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_rngState (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_btIdx (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_btDigit (mut i32) (i32.const 0))
  (global $g_jn_static_SudokuCore_ready (mut i32) (i32.const 0))
  (data (i32.const 2048) "\4a\4e\53\44\4b")
  (data (i32.const 2053) "\35\33\30\30\37\30\30\30\30\36\30\30\31\39\35\30\30\30\30\39\38\30\30\30\30\36\30\38\30\30\30\36\30\30\30\33\34\30\30\38\30\33\30\30\31\37\30\30\30\32\30\30\30\36\30\36\30\30\30\30\32\38\30\30\30\30\34\31\39\30\30\35\30\30\30\30\38\30\30\37\39")
  (data (i32.const 2134) "\30\30\30\32\36\30\37\30\31\36\38\30\30\37\30\30\39\30\31\39\30\30\30\34\35\30\30\38\32\30\31\30\30\30\34\30\30\30\34\36\30\32\39\30\30\30\35\30\30\30\33\30\32\38\30\30\39\33\30\30\30\37\34\30\34\30\30\35\30\30\33\36\37\30\33\30\31\38\30\30\30")
  (data (i32.const 2215) "\36\30\39\32\30\30\37\34\38\34\35\30\36\33\30\30\39\30\30\31\30\30\30\30\30\30\35\33\39\34\30\30\35\30\30\37\31\37\35\33\30\39\30\30\30\30\30\36\30\30\34\30\30\30\39\38\30\34\37\30\35\30\36\30\36\37\35\39\33\30\30\30\30\34\33\30\30\30\32\30\39")
  (data (i32.const 2296) "\30\30\30\30\30\30\39\30\37\30\30\30\34\32\30\31\38\30\30\30\30\37\30\35\30\32\36\31\30\30\39\30\34\30\30\30\30\35\30\30\30\30\30\34\30\30\30\30\35\30\37\30\30\39\39\32\30\31\30\38\30\30\30\30\33\34\30\35\39\30\30\30\35\30\37\30\30\30\30\30\30")
  (data (i32.const 2377) "\35\39\30\31\32\30\37\30\30\30\38\30\33\30\35\30\30\30\30\32\31\30\30\30\36\30\30\30\30\35\30\30\30\34\36\30\30\33\38\30\36\31\30\30\30\32\31\30\30\37\34\35\38\30\30\30\30\30\30\30\38\30\30\30\34\30\30\30\30\32\30\30\30\36\30\30\39\30\30\30\34")
  (data (i32.const 2458) "\30\38\30\30\33\30\30\30\32\30\30\30\37\32\30\30\30\36\30\30\31\38\34\30\33\30\30\34\31\37\33\30\36\30\30\30\38\30\30\30\30\30\31\30\30\39\30\30\30\30\30\30\36\30\30\30\30\34\30\33\30\32\30\30\30\32\39\30\37\30\30\33\36\30\30\31\38\30\30\30\34")
  (data (i32.const 2539) "\38\30\30\30\30\30\30\30\30\30\30\33\36\30\30\30\30\30\30\37\30\30\39\30\32\30\30\30\35\30\30\30\37\30\30\30\30\30\30\30\34\35\37\30\30\30\30\30\31\30\30\30\33\30\30\30\31\30\30\30\30\36\38\30\30\38\35\30\30\30\31\30\30\39\30\30\30\30\34\30\30")
  (data (i32.const 2620) "\30\30\35\33\30\30\30\30\30\38\30\30\30\30\30\30\32\30\30\37\30\30\31\30\35\30\30\34\30\30\30\30\35\33\30\30\30\31\30\30\37\30\30\30\36\30\30\33\32\30\30\30\38\30\30\36\30\35\30\30\30\30\39\30\30\34\30\30\30\30\33\30\30\30\30\30\30\39\37\30\30")
  (data (i32.const 2701) "\30\30\30\30\30\33\30\39\30\30\30\30\39\30\31\38\30\30\30\36\31\30\35\37\30\30\34\30\30\30\30\30\30\31\38\30\30\35\30\30\30\36\30\30\30\36\30\30\37\33\38\35\30\30\30\37\30\30\30\30\30\35\30\30\30\30\30\32\30\39\30\30\30\30\32\30\30\30\30\30\30")
  (data (i32.const 2782) "\78")
  (data (i32.const 2783) "\30")
  (data (i32.const 2784) "")
  (func $jn_gc_trace_type_fields (param $obj i32) (param $tid i32)
    local.get $tid
    i32.const 2
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
    i32.const 3
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
    i32.const 6
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
    i32.const 7
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
      i32.const 16
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
    i32.const 14
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
    i32.const 16
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
    i32.const 21
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
    i32.const 22
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
    i32.const 23
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
    i32.const 30
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
    i32.const 33
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
    i32.const 36
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
    i32.const 37
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
      i32.const 8
      i32.add
      i32.load
      call $jn_gc_mark_obj
      return
    end
    local.get $tid
    i32.const 40
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
    i32.const 41
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
    i32.const 42
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
    global.get $g_jn_static_SudokuCore_values
    call $jn_gc_mark_obj
    global.get $g_jn_static_SudokuCore_given
    call $jn_gc_mark_obj
    global.get $g_jn_static_SudokuCore_notes
    call $jn_gc_mark_obj
    global.get $g_jn_static_SudokuCore_conflict
    call $jn_gc_mark_obj
    global.get $g_jn_static_SudokuCore_solution
    call $jn_gc_mark_obj
    global.get $g_jn_static_SudokuCore_histCell
    call $jn_gc_mark_obj
    global.get $g_jn_static_SudokuCore_histPrevVal
    call $jn_gc_mark_obj
    global.get $g_jn_static_SudokuCore_histNewVal
    call $jn_gc_mark_obj
    global.get $g_jn_static_SudokuCore_histPrevNotes
    call $jn_gc_mark_obj
    global.get $g_jn_static_SudokuCore_histNewNotes
    call $jn_gc_mark_obj
    global.get $g_jn_static_SudokuCore_saveBuf
    call $jn_gc_mark_obj
    global.get $g_jn_static_SudokuCore_work
    call $jn_gc_mark_obj
    global.get $g_jn_static_SudokuCore_work2
    call $jn_gc_mark_obj
    global.get $g_jn_static_SudokuCore_btIdx
    call $jn_gc_mark_obj
    global.get $g_jn_static_SudokuCore_btDigit
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
    i32.const 6
    i32.const 0
    call $jn_ex_alloc
    global.set $jn_pending_exc
  )
  (func $jnative_throw_illegal_argument
    i32.const 2
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
      i32.const 48
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
    i32.const 48
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
          i32.const 48
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
          i32.const 48
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
    i32.const 0
    global.set $jn_pending_exc
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
  )
  (func $jn_raise_host_error (param $msg i32)
    i32.const 29
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
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb0
          local.get $pc
          br_table $bb0 $exit
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_SAVE_VERSION
          i32.const 8192
          global.set $g_jn_static_SudokuCore_MAX_SAVE_CHARS
          i32.const 128
          global.set $g_jn_static_SudokuCore_MAX_HISTORY
          i32.const 40
          global.set $g_jn_static_SudokuCore_GEN_MAX_ATTEMPTS
          i32.const 200000
          global.set $g_jn_static_SudokuCore_GEN_MAX_STEPS
          i32.const 0
          global.set $g_jn_static_SudokuCore_DIFF_EASY
          i32.const 1
          global.set $g_jn_static_SudokuCore_DIFF_MEDIUM
          i32.const 2
          global.set $g_jn_static_SudokuCore_DIFF_HARD
          i32.const 0
          global.set $g_jn_static_SudokuCore_ST_PLAYING
          i32.const 1
          global.set $g_jn_static_SudokuCore_ST_PAUSED
          i32.const 2
          global.set $g_jn_static_SudokuCore_ST_WON
          i32.const 0
          global.set $g_jn_static_SudokuCore_selected
          i32.const 0
          global.set $g_jn_static_SudokuCore_noteMode
          i32.const 0
          global.set $g_jn_static_SudokuCore_status
          i32.const 0
          global.set $g_jn_static_SudokuCore_puzzleId
          i32.const 0
          global.set $g_jn_static_SudokuCore_difficulty
          i32.const 0
          global.set $g_jn_static_SudokuCore_elapsedMs
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastTickMs
          i32.const 0
          global.set $g_jn_static_SudokuCore_tickValid
          i32.const 0
          global.set $g_jn_static_SudokuCore_histLen
          i32.const 0
          global.set $g_jn_static_SudokuCore_histPos
          i32.const 0
          global.set $g_jn_static_SudokuCore_saveLen
          i32.const 0
          global.set $g_jn_static_SudokuCore_solveSteps
          i32.const 0
          global.set $g_jn_static_SudokuCore_genSteps
          i32.const 1
          global.set $g_jn_static_SudokuCore_rngState
          i32.const 0
          global.set $g_jn_static_SudokuCore_ready
          br $exit
      )
    )
  )
  (func $jn_main (param $args i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $args__slot i32)
    (local $keep i32)
    (local $gctmp.70 i32)
    (local $gctmp.74 i32)
    (local $gctmp.86 i32)
    (local $gctmp.90 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    (local $t72 i32)
    (local $t73 i32)
    (local $t74 i32)
    (local $t75 i32)
    (local $t76 i32)
    (local $t77 i32)
    (local $t78 i32)
    (local $t79 i32)
    (local $t80 i32)
    (local $t81 i32)
    (local $t82 i32)
    (local $t83 i32)
    (local $t84 i32)
    (local $t85 i32)
    (local $t86 i32)
    (local $t87 i32)
    (local $t88 i32)
    (local $t89 i32)
    (local $t90 i32)
    (local $t91 i32)
    (local $t92 i32)
    i32.const 14
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
        (block $bb86
        (block $bb85
        (block $bb84
        (block $bb83
        (block $bb82
        (block $bb81
        (block $bb80
        (block $bb79
        (block $bb78
        (block $bb77
        (block $bb76
        (block $bb75
        (block $bb74
        (block $bb73
        (block $bb72
        (block $bb71
        (block $bb70
        (block $bb69
        (block $bb68
        (block $bb67
        (block $bb66
        (block $bb65
        (block $bb64
        (block $bb63
        (block $bb62
        (block $bb61
        (block $bb60
        (block $bb59
        (block $bb58
        (block $bb57
        (block $bb56
        (block $bb55
        (block $bb54
        (block $bb53
        (block $bb52
        (block $bb51
        (block $bb50
        (block $bb49
        (block $bb48
        (block $bb47
        (block $bb46
        (block $bb45
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $bb45 $bb46 $bb47 $bb48 $bb49 $bb50 $bb51 $bb52 $bb53 $bb54 $bb55 $bb56 $bb57 $bb58 $bb59 $bb60 $bb61 $bb62 $bb63 $bb64 $bb65 $bb66 $bb67 $bb68 $bb69 $bb70 $bb71 $bb72 $bb73 $bb74 $bb75 $bb76 $bb77 $bb78 $bb79 $bb80 $bb81 $bb82 $bb83 $bb84 $bb85 $bb86 $exit
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
          i32.const 0
          i32.const 0
          call $_JN_SudokuCore_newGame_i32_i32
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
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          i32.const 0
          call $_JN_SudokuCore_selectCell_i32_i32
          local.set $t3
          call $jnative_exception_pending
          local.set $t4
          local.get $t4
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
          local.get $t3
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          i32.const 1
          call $_JN_SudokuCore_moveSelection_i32_i32
          local.set $t5
          call $jnative_exception_pending
          local.set $t6
          local.get $t6
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
          local.get $t5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          call $_JN_SudokuCore_inputDigit_i32
          local.set $t7
          call $jnative_exception_pending
          local.set $t8
          local.get $t8
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
          local.get $t7
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_clearCell
          local.set $t9
          call $jnative_exception_pending
          local.set $t10
          local.get $t10
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
          local.get $t9
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_toggleNoteMode
          local.set $t11
          call $jnative_exception_pending
          local.set $t12
          local.get $t12
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
          local.get $t11
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          call $_JN_SudokuCore_setNoteMode_i32
          local.set $t13
          call $jnative_exception_pending
          local.set $t14
          local.get $t14
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
          local.get $t13
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_undo
          local.set $t15
          call $jnative_exception_pending
          local.set $t16
          local.get $t16
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
          local.get $t15
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_redo
          local.set $t17
          call $jnative_exception_pending
          local.set $t18
          local.get $t18
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
          local.get $t17
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_hint
          local.set $t19
          call $jnative_exception_pending
          local.set $t20
          local.get $t20
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
          local.get $t19
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_pause
          local.set $t21
          call $jnative_exception_pending
          local.set $t22
          local.get $t22
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
          local.get $t21
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_resume
          local.set $t23
          call $jnative_exception_pending
          local.set $t24
          local.get $t24
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
          local.get $t23
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          call $_JN_SudokuCore_tick_i32
          local.set $t25
          call $jnative_exception_pending
          local.set $t26
          local.get $t26
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
          local.get $t25
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_getElapsedMs
          local.set $t27
          call $jnative_exception_pending
          local.set $t28
          local.get $t28
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
          local.get $t27
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_getStatus
          local.set $t29
          call $jnative_exception_pending
          local.set $t30
          local.get $t30
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
          local.get $t29
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_getSelected
          local.set $t31
          call $jnative_exception_pending
          local.set $t32
          local.get $t32
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
          local.get $t31
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_getNoteMode
          local.set $t33
          call $jnative_exception_pending
          local.set $t34
          local.get $t34
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
          local.get $t33
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_getPuzzleId
          local.set $t35
          call $jnative_exception_pending
          local.set $t36
          local.get $t36
          if
            i32.const 37
            local.set $pc
          else
            i32.const 38
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t35
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_getDifficulty
          local.set $t37
          call $jnative_exception_pending
          local.set $t38
          local.get $t38
          if
            i32.const 39
            local.set $pc
          else
            i32.const 40
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t37
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          call $_JN_SudokuCore_getValue_i32
          local.set $t39
          call $jnative_exception_pending
          local.set $t40
          local.get $t40
          if
            i32.const 41
            local.set $pc
          else
            i32.const 42
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t39
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          call $_JN_SudokuCore_getGiven_i32
          local.set $t41
          call $jnative_exception_pending
          local.set $t42
          local.get $t42
          if
            i32.const 43
            local.set $pc
          else
            i32.const 44
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t41
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          call $_JN_SudokuCore_getNotes_i32
          local.set $t43
          call $jnative_exception_pending
          local.set $t44
          local.get $t44
          if
            i32.const 45
            local.set $pc
          else
            i32.const 46
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t43
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          call $_JN_SudokuCore_getConflict_i32
          local.set $t45
          call $jnative_exception_pending
          local.set $t46
          local.get $t46
          if
            i32.const 47
            local.set $pc
          else
            i32.const 48
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t45
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          call $_JN_SudokuCore_getSolutionDigit_i32
          local.set $t47
          call $jnative_exception_pending
          local.set $t48
          local.get $t48
          if
            i32.const 49
            local.set $pc
          else
            i32.const 50
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t47
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_canUndo
          local.set $t49
          call $jnative_exception_pending
          local.set $t50
          local.get $t50
          if
            i32.const 51
            local.set $pc
          else
            i32.const 52
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t49
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_canRedo
          local.set $t51
          call $jnative_exception_pending
          local.set $t52
          local.get $t52
          if
            i32.const 53
            local.set $pc
          else
            i32.const 54
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t51
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_isComplete
          local.set $t53
          call $jnative_exception_pending
          local.set $t54
          local.get $t54
          if
            i32.const 55
            local.set $pc
          else
            i32.const 56
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t53
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_getHighlightDigit
          local.set $t55
          call $jnative_exception_pending
          local.set $t56
          local.get $t56
          if
            i32.const 57
            local.set $pc
          else
            i32.const 58
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t55
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          call $_JN_SudokuCore_sameRow_i32
          local.set $t57
          call $jnative_exception_pending
          local.set $t58
          local.get $t58
          if
            i32.const 59
            local.set $pc
          else
            i32.const 60
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t57
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          call $_JN_SudokuCore_sameCol_i32
          local.set $t59
          call $jnative_exception_pending
          local.set $t60
          local.get $t60
          if
            i32.const 61
            local.set $pc
          else
            i32.const 62
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t59
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          call $_JN_SudokuCore_sameBox_i32
          local.set $t61
          call $jnative_exception_pending
          local.set $t62
          local.get $t62
          if
            i32.const 63
            local.set $pc
          else
            i32.const 64
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t61
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_prepareSave
          local.set $t63
          call $jnative_exception_pending
          local.set $t64
          local.get $t64
          if
            i32.const 65
            local.set $pc
          else
            i32.const 66
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t63
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_getSaveLen
          local.set $t65
          call $jnative_exception_pending
          local.set $t66
          local.get $t66
          if
            i32.const 67
            local.set $pc
          else
            i32.const 68
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t65
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          call $_JN_SudokuCore_getSaveChar_i32
          local.set $t67
          call $jnative_exception_pending
          local.set $t68
          local.get $t68
          if
            i32.const 69
            local.set $pc
          else
            i32.const 70
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t67
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 2782
          i32.const 1
          call $jn_str
          local.set $t69
          local.get $__jn_frame
          i32.const 6
          local.get $t69
          call $jn_gc_frame_set_root
          local.get $t69
          local.set $gctmp.70
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.70
          call $jn_gc_frame_set_root
          local.get $gctmp.70
          local.set $t70
          local.get $__jn_frame
          i32.const 7
          local.get $t70
          call $jn_gc_frame_set_root
          local.get $t70
          call $_JN_SudokuCore_loadSave_CString
          local.set $t71
          call $jnative_exception_pending
          local.set $t72
          local.get $t72
          if
            i32.const 71
            local.set $pc
          else
            i32.const 72
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t71
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 2783
          i32.const 1
          call $jn_str
          local.set $t73
          local.get $__jn_frame
          i32.const 8
          local.get $t73
          call $jn_gc_frame_set_root
          local.get $t73
          local.set $gctmp.74
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.74
          call $jn_gc_frame_set_root
          local.get $gctmp.74
          local.set $t74
          local.get $__jn_frame
          i32.const 9
          local.get $t74
          call $jn_gc_frame_set_root
          local.get $t74
          call $_JN_SudokuCore_countSolutionsPuzzle_CString
          local.set $t75
          call $jnative_exception_pending
          local.set $t76
          local.get $t76
          if
            i32.const 73
            local.set $pc
          else
            i32.const 74
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t75
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          i32.const 0
          call $_JN_SudokuCore_generate_i32_i32
          local.set $t77
          call $jnative_exception_pending
          local.set $t78
          local.get $t78
          if
            i32.const 75
            local.set $pc
          else
            i32.const 76
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t77
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          call $_JN_SudokuCore_puzzleCount_i32
          local.set $t79
          call $jnative_exception_pending
          local.set $t80
          local.get $t80
          if
            i32.const 77
            local.set $pc
          else
            i32.const 78
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t79
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_exportBoardDigits
          local.set $t81
          call $jnative_exception_pending
          local.set $t82
          local.get $t82
          if
            i32.const 79
            local.set $pc
          else
            i32.const 80
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t81
          global.set $g_jn_static_SudokuCore_lastError
          call $_JN_SudokuCore_getLastError
          local.set $t83
          call $jnative_exception_pending
          local.set $t84
          local.get $t84
          if
            i32.const 81
            local.set $pc
          else
            i32.const 82
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t83
          global.set $g_jn_static_SudokuCore_lastError
          global.get $g_jn_static_SudokuCore_values
          local.set $t85
          local.get $__jn_frame
          i32.const 10
          local.get $t85
          call $jn_gc_frame_set_root
          local.get $t85
          local.set $gctmp.86
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.86
          call $jn_gc_frame_set_root
          local.get $gctmp.86
          local.set $t86
          local.get $__jn_frame
          i32.const 11
          local.get $t86
          call $jn_gc_frame_set_root
          local.get $t86
          call $_JN_SudokuCore_boardValidComplete_Ai32
          local.set $t87
          call $jnative_exception_pending
          local.set $t88
          local.get $t88
          if
            i32.const 83
            local.set $pc
          else
            i32.const 84
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t87
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 2783
          i32.const 1
          call $jn_str
          local.set $t89
          local.get $__jn_frame
          i32.const 12
          local.get $t89
          call $jn_gc_frame_set_root
          local.get $t89
          local.set $gctmp.90
          local.get $__jn_frame
          i32.const 5
          local.get $gctmp.90
          call $jn_gc_frame_set_root
          local.get $gctmp.90
          local.set $t90
          local.get $__jn_frame
          i32.const 13
          local.get $t90
          call $jn_gc_frame_set_root
          local.get $t90
          i32.const 0
          i32.const 0
          call $_JN_SudokuCore_loadPuzzleString_CString_i32_i32
          local.set $t91
          call $jnative_exception_pending
          local.set $t92
          local.get $t92
          if
            i32.const 85
            local.set $pc
          else
            i32.const 86
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t91
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 2
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_SudokuCore_newGame_i32_i32 (param $diff i32) (param $index i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $diff__slot i32)
    (local $index__slot i32)
    (local $sc.tmp249155636 i32)
    (local $idx i32)
    (local $base i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $exit
        )
          local.get $diff
          local.set $diff__slot
          local.get $index
          local.set $index__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $diff__slot
          local.set $t1
          local.get $t1
          i32.const 0
          i32.lt_s
          local.set $t2
          local.get $t2
          local.set $sc.tmp249155636
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $diff__slot
          local.set $t3
          local.get $t3
          i32.const 2
          i32.gt_s
          local.set $t4
          local.get $t4
          local.set $sc.tmp249155636
          i32.const 4
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp249155636
          local.set $t5
          local.get $t5
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $index__slot
          local.set $t6
          local.get $t6
          local.set $idx
          local.get $idx
          local.set $t7
          local.get $t7
          i32.const 0
          i32.lt_s
          local.set $t8
          local.get $t8
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $idx
          i32.const 8
          local.set $pc
          br $dispatch
        )
          local.get $idx
          local.set $t9
          local.get $t9
          i32.const 3
          i32.rem_s
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t10
          local.set $idx
          local.get $diff__slot
          local.set $t12
          local.get $t12
          i32.const 3
          i32.mul
          local.set $t13
          local.get $idx
          local.set $t14
          local.get $t13
          local.get $t14
          i32.add
          local.set $t15
          local.get $t15
          local.set $base
          local.get $base
          local.set $t16
          local.get $diff__slot
          local.set $t17
          local.get $idx
          local.set $t18
          local.get $t16
          local.get $t17
          local.get $t18
          call $_JN_SudokuCore_loadPuzzleFromCatalog_i32_i32_i32
          local.set $t19
          call $jnative_exception_pending
          local.set $t20
          local.get $t20
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t19
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_selectCell_i32_i32 (param $row i32) (param $col i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $row__slot i32)
    (local $col__slot i32)
    (local $sc.tmp1629604310 i32)
    (local $sc.tmp142555199 i32)
    (local $sc.tmp1320677379 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $exit
        )
          local.get $row
          local.set $row__slot
          local.get $col
          local.set $col__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t1
          global.get $g_jn_static_SudokuCore_ST_PAUSED
          local.set $t2
          local.get $t1
          local.get $t2
          i32.eq
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 3
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $row__slot
          local.set $t4
          local.get $t4
          i32.const 0
          i32.lt_s
          local.set $t5
          local.get $t5
          local.set $sc.tmp1320677379
          local.get $t5
          if
            i32.const 10
            local.set $pc
          else
            i32.const 9
            local.set $pc
          end
          br $dispatch
        )
          local.get $col__slot
          local.set $t12
          local.get $t12
          i32.const 8
          i32.gt_s
          local.set $t13
          local.get $t13
          local.set $sc.tmp1629604310
          i32.const 6
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1629604310
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
          local.get $col__slot
          local.set $t9
          local.get $t9
          i32.const 0
          i32.lt_s
          local.set $t10
          local.get $t10
          local.set $sc.tmp142555199
          i32.const 8
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp142555199
          local.set $t11
          local.get $t11
          local.set $sc.tmp1629604310
          local.get $t11
          if
            i32.const 6
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          local.get $row__slot
          local.set $t6
          local.get $t6
          i32.const 8
          i32.gt_s
          local.set $t7
          local.get $t7
          local.set $sc.tmp1320677379
          i32.const 10
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1320677379
          local.set $t8
          local.get $t8
          local.set $sc.tmp142555199
          local.get $t8
          if
            i32.const 8
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $row__slot
          local.set $t15
          local.get $t15
          i32.const 9
          i32.mul
          local.set $t16
          local.get $col__slot
          local.set $t17
          local.get $t16
          local.get $t17
          i32.add
          local.set $t18
          local.get $t18
          global.set $g_jn_static_SudokuCore_selected
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_moveSelection_i32_i32 (param $dRow i32) (param $dCol i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $dRow__slot i32)
    (local $dCol__slot i32)
    (local $r i32)
    (local $c i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
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
          local.get $dRow
          local.set $dRow__slot
          local.get $dCol
          local.set $dCol__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t1
          global.get $g_jn_static_SudokuCore_ST_PAUSED
          local.set $t2
          local.get $t1
          local.get $t2
          i32.eq
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 3
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_selected
          local.set $t4
          local.get $t4
          i32.const 9
          i32.div_s
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t5
          local.set $r
          global.get $g_jn_static_SudokuCore_selected
          local.set $t7
          local.get $t7
          i32.const 9
          i32.rem_s
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t8
          local.set $c
          local.get $r
          local.set $t10
          local.get $dRow__slot
          local.set $t11
          local.get $t10
          local.get $t11
          i32.add
          local.set $t12
          local.get $t12
          local.set $r
          local.get $c
          local.set $t13
          local.get $dCol__slot
          local.set $t14
          local.get $t13
          local.get $t14
          i32.add
          local.set $t15
          local.get $t15
          local.set $c
          local.get $r
          local.set $t16
          local.get $t16
          i32.const 0
          i32.lt_s
          local.set $t17
          local.get $t17
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $r
          i32.const 10
          local.set $pc
          br $dispatch
        )
          local.get $r
          local.set $t18
          local.get $t18
          i32.const 8
          i32.gt_s
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
          i32.const 8
          local.set $r
          i32.const 12
          local.set $pc
          br $dispatch
        )
          local.get $c
          local.set $t20
          local.get $t20
          i32.const 0
          i32.lt_s
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
          i32.const 0
          local.set $c
          i32.const 14
          local.set $pc
          br $dispatch
        )
          local.get $c
          local.set $t22
          local.get $t22
          i32.const 8
          i32.gt_s
          local.set $t23
          local.get $t23
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          i32.const 8
          local.set $c
          i32.const 16
          local.set $pc
          br $dispatch
        )
          local.get $r
          local.set $t24
          local.get $t24
          i32.const 9
          i32.mul
          local.set $t25
          local.get $c
          local.set $t26
          local.get $t25
          local.get $t26
          i32.add
          local.set $t27
          local.get $t27
          global.set $g_jn_static_SudokuCore_selected
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_inputDigit_i32 (param $d i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $d__slot i32)
    (local $sc.tmp84739718 i32)
    (local $sc.tmp2050835901 i32)
    (local $idx i32)
    (local $gctmp.15 i32)
    (local $prevV i32)
    (local $gctmp.27 i32)
    (local $prevN i32)
    (local $gctmp.32 i32)
    (local $newV i32)
    (local $newN i32)
    (local $sc.tmp511473681 i32)
    (local $gctmp.52 i32)
    (local $gctmp.57 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    i32.const 15
    call $jn_gc_frame_push
    local.set $__jn_frame
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
          local.get $d
          local.set $d__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t1
          global.get $g_jn_static_SudokuCore_ST_PAUSED
          local.set $t2
          local.get $t1
          local.get $t2
          i32.eq
          local.set $t3
          local.get $t3
          local.set $sc.tmp84739718
          local.get $t3
          if
            i32.const 4
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t4
          global.get $g_jn_static_SudokuCore_ST_WON
          local.set $t5
          local.get $t4
          local.get $t5
          i32.eq
          local.set $t6
          local.get $t6
          local.set $sc.tmp84739718
          i32.const 4
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp84739718
          local.set $t7
          local.get $t7
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 3
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $d__slot
          local.set $t8
          local.get $t8
          i32.const 1
          i32.lt_s
          local.set $t9
          local.get $t9
          local.set $sc.tmp2050835901
          local.get $t9
          if
            i32.const 8
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          local.get $d__slot
          local.set $t10
          local.get $t10
          i32.const 9
          i32.gt_s
          local.set $t11
          local.get $t11
          local.set $sc.tmp2050835901
          i32.const 8
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp2050835901
          local.set $t12
          local.get $t12
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_selected
          local.set $t13
          local.get $t13
          local.set $idx
          global.get $g_jn_static_SudokuCore_given
          local.set $t14
          local.get $__jn_frame
          i32.const 5
          local.get $t14
          call $jn_gc_frame_set_root
          local.get $t14
          local.set $gctmp.15
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.15
          call $jn_gc_frame_set_root
          local.get $gctmp.15
          local.set $t15
          local.get $__jn_frame
          i32.const 6
          local.get $t15
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t16
          local.get $t15
          i32.eqz
          if
            unreachable
          end
          local.get $t16
          i32.const 0
          i32.lt_s
          local.get $t16
          local.get $t15
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t15
          i32.const 16
          i32.add
          local.get $t16
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t17
          call $jnative_exception_pending
          local.set $t18
          local.get $t18
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t17
          i32.const 0
          i32.ne
          local.set $t19
          local.get $t19
          if
            i32.const 13
            local.set $pc
          else
            i32.const 14
            local.set $pc
          end
          br $dispatch
        )
          i32.const 2
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_noteMode
          local.set $t20
          local.get $t20
          i32.const 0
          i32.ne
          local.set $t21
          local.get $t21
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          local.get $idx
          local.set $t22
          local.get $d__slot
          local.set $t23
          local.get $t22
          local.get $t23
          call $_JN_SudokuCore_toggleNoteAt_i32_i32
          local.set $t24
          call $jnative_exception_pending
          local.set $t25
          local.get $t25
          if
            i32.const 17
            local.set $pc
          else
            i32.const 18
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t26
          local.get $__jn_frame
          i32.const 7
          local.get $t26
          call $jn_gc_frame_set_root
          local.get $t26
          local.set $gctmp.27
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.27
          call $jn_gc_frame_set_root
          local.get $gctmp.27
          local.set $t27
          local.get $__jn_frame
          i32.const 8
          local.get $t27
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t28
          local.get $t27
          i32.eqz
          if
            unreachable
          end
          local.get $t28
          i32.const 0
          i32.lt_s
          local.get $t28
          local.get $t27
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t27
          i32.const 16
          i32.add
          local.get $t28
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t29
          call $jnative_exception_pending
          local.set $t30
          local.get $t30
          if
            i32.const 19
            local.set $pc
          else
            i32.const 20
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t24
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t29
          local.set $prevV
          global.get $g_jn_static_SudokuCore_notes
          local.set $t31
          local.get $__jn_frame
          i32.const 9
          local.get $t31
          call $jn_gc_frame_set_root
          local.get $t31
          local.set $gctmp.32
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.32
          call $jn_gc_frame_set_root
          local.get $gctmp.32
          local.set $t32
          local.get $__jn_frame
          i32.const 10
          local.get $t32
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t33
          local.get $t32
          i32.eqz
          if
            unreachable
          end
          local.get $t33
          i32.const 0
          i32.lt_s
          local.get $t33
          local.get $t32
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t32
          i32.const 16
          i32.add
          local.get $t33
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t34
          call $jnative_exception_pending
          local.set $t35
          local.get $t35
          if
            i32.const 21
            local.set $pc
          else
            i32.const 22
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t34
          local.set $prevN
          local.get $d__slot
          local.set $t36
          local.get $t36
          local.set $newV
          i32.const 0
          local.set $newN
          local.get $prevV
          local.set $t37
          local.get $newV
          local.set $t38
          local.get $t37
          local.get $t38
          i32.eq
          local.set $t39
          local.get $t39
          local.set $sc.tmp511473681
          local.get $t39
          if
            i32.const 23
            local.set $pc
          else
            i32.const 24
            local.set $pc
          end
          br $dispatch
        )
          local.get $prevN
          local.set $t40
          local.get $newN
          local.set $t41
          local.get $t40
          local.get $t41
          i32.eq
          local.set $t42
          local.get $t42
          local.set $sc.tmp511473681
          i32.const 24
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp511473681
          local.set $t43
          local.get $t43
          if
            i32.const 25
            local.set $pc
          else
            i32.const 26
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
        )
          local.get $idx
          local.set $t44
          local.get $prevV
          local.set $t45
          local.get $newV
          local.set $t46
          local.get $prevN
          local.set $t47
          local.get $newN
          local.set $t48
          local.get $t44
          local.get $t45
          local.get $t46
          local.get $t47
          local.get $t48
          call $_JN_SudokuCore_pushHistory_i32_i32_i32_i32_i32
          call $jnative_exception_pending
          local.set $t49
          local.get $t49
          if
            i32.const 27
            local.set $pc
          else
            i32.const 28
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $newV
          local.set $t50
          global.get $g_jn_static_SudokuCore_values
          local.set $t51
          local.get $__jn_frame
          i32.const 11
          local.get $t51
          call $jn_gc_frame_set_root
          local.get $t51
          local.set $gctmp.52
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.52
          call $jn_gc_frame_set_root
          local.get $gctmp.52
          local.set $t52
          local.get $__jn_frame
          i32.const 12
          local.get $t52
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t53
          local.get $t52
          i32.eqz
          if
            unreachable
          end
          local.get $t53
          i32.const 0
          i32.lt_s
          local.get $t53
          local.get $t52
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t52
          i32.const 16
          i32.add
          local.get $t53
          i32.const 4
          i32.mul
          i32.add
          local.get $t50
          i32.store
          call $jnative_exception_pending
          local.set $t54
          local.get $t54
          if
            i32.const 29
            local.set $pc
          else
            i32.const 30
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $newN
          local.set $t55
          global.get $g_jn_static_SudokuCore_notes
          local.set $t56
          local.get $__jn_frame
          i32.const 13
          local.get $t56
          call $jn_gc_frame_set_root
          local.get $t56
          local.set $gctmp.57
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.57
          call $jn_gc_frame_set_root
          local.get $gctmp.57
          local.set $t57
          local.get $__jn_frame
          i32.const 14
          local.get $t57
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t58
          local.get $t57
          i32.eqz
          if
            unreachable
          end
          local.get $t58
          i32.const 0
          i32.lt_s
          local.get $t58
          local.get $t57
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t57
          i32.const 16
          i32.add
          local.get $t58
          i32.const 4
          i32.mul
          i32.add
          local.get $t55
          i32.store
          call $jnative_exception_pending
          local.set $t59
          local.get $t59
          if
            i32.const 31
            local.set $pc
          else
            i32.const 32
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          call $_JN_SudokuCore_recomputeConflicts
          call $jnative_exception_pending
          local.set $t60
          local.get $t60
          if
            i32.const 33
            local.set $pc
          else
            i32.const 34
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          call $_JN_SudokuCore_checkWon
          call $jnative_exception_pending
          local.set $t61
          local.get $t61
          if
            i32.const 35
            local.set $pc
          else
            i32.const 36
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_clearCell (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $sc.tmp439904756 i32)
    (local $idx i32)
    (local $gctmp.10 i32)
    (local $prevV i32)
    (local $gctmp.16 i32)
    (local $prevN i32)
    (local $gctmp.21 i32)
    (local $sc.tmp171497379 i32)
    (local $gctmp.35 i32)
    (local $gctmp.39 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    i32.const 15
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $exit
        )
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t1
          global.get $g_jn_static_SudokuCore_ST_PAUSED
          local.set $t2
          local.get $t1
          local.get $t2
          i32.eq
          local.set $t3
          local.get $t3
          local.set $sc.tmp439904756
          local.get $t3
          if
            i32.const 4
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t4
          global.get $g_jn_static_SudokuCore_ST_WON
          local.set $t5
          local.get $t4
          local.get $t5
          i32.eq
          local.set $t6
          local.get $t6
          local.set $sc.tmp439904756
          i32.const 4
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp439904756
          local.set $t7
          local.get $t7
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 3
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_selected
          local.set $t8
          local.get $t8
          local.set $idx
          global.get $g_jn_static_SudokuCore_given
          local.set $t9
          local.get $__jn_frame
          i32.const 5
          local.get $t9
          call $jn_gc_frame_set_root
          local.get $t9
          local.set $gctmp.10
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.10
          call $jn_gc_frame_set_root
          local.get $gctmp.10
          local.set $t10
          local.get $__jn_frame
          i32.const 6
          local.get $t10
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t11
          local.get $t10
          i32.eqz
          if
            unreachable
          end
          local.get $t11
          i32.const 0
          i32.lt_s
          local.get $t11
          local.get $t10
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t10
          i32.const 16
          i32.add
          local.get $t11
          i32.const 4
          i32.mul
          i32.add
          i32.load
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t12
          i32.const 0
          i32.ne
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
          i32.const 2
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t15
          local.get $__jn_frame
          i32.const 7
          local.get $t15
          call $jn_gc_frame_set_root
          local.get $t15
          local.set $gctmp.16
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.16
          call $jn_gc_frame_set_root
          local.get $gctmp.16
          local.set $t16
          local.get $__jn_frame
          i32.const 8
          local.get $t16
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t17
          local.get $t16
          i32.eqz
          if
            unreachable
          end
          local.get $t17
          i32.const 0
          i32.lt_s
          local.get $t17
          local.get $t16
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t16
          i32.const 16
          i32.add
          local.get $t17
          i32.const 4
          i32.mul
          i32.add
          i32.load
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t18
          local.set $prevV
          global.get $g_jn_static_SudokuCore_notes
          local.set $t20
          local.get $__jn_frame
          i32.const 9
          local.get $t20
          call $jn_gc_frame_set_root
          local.get $t20
          local.set $gctmp.21
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.21
          call $jn_gc_frame_set_root
          local.get $gctmp.21
          local.set $t21
          local.get $__jn_frame
          i32.const 10
          local.get $t21
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t22
          local.get $t21
          i32.eqz
          if
            unreachable
          end
          local.get $t22
          i32.const 0
          i32.lt_s
          local.get $t22
          local.get $t21
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t21
          i32.const 16
          i32.add
          local.get $t22
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t23
          call $jnative_exception_pending
          local.set $t24
          local.get $t24
          if
            i32.const 13
            local.set $pc
          else
            i32.const 14
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t23
          local.set $prevN
          local.get $prevV
          local.set $t25
          local.get $t25
          i32.const 0
          i32.eq
          local.set $t26
          local.get $t26
          local.set $sc.tmp171497379
          local.get $t26
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          local.get $prevN
          local.set $t27
          local.get $t27
          i32.const 0
          i32.eq
          local.set $t28
          local.get $t28
          local.set $sc.tmp171497379
          i32.const 16
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp171497379
          local.set $t29
          local.get $t29
          if
            i32.const 17
            local.set $pc
          else
            i32.const 18
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
        )
          local.get $idx
          local.set $t30
          local.get $prevV
          local.set $t31
          local.get $prevN
          local.set $t32
          local.get $t30
          local.get $t31
          i32.const 0
          local.get $t32
          i32.const 0
          call $_JN_SudokuCore_pushHistory_i32_i32_i32_i32_i32
          call $jnative_exception_pending
          local.set $t33
          local.get $t33
          if
            i32.const 19
            local.set $pc
          else
            i32.const 20
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t34
          local.get $__jn_frame
          i32.const 11
          local.get $t34
          call $jn_gc_frame_set_root
          local.get $t34
          local.set $gctmp.35
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.35
          call $jn_gc_frame_set_root
          local.get $gctmp.35
          local.set $t35
          local.get $__jn_frame
          i32.const 12
          local.get $t35
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t36
          local.get $t35
          i32.eqz
          if
            unreachable
          end
          local.get $t36
          i32.const 0
          i32.lt_s
          local.get $t36
          local.get $t35
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t35
          i32.const 16
          i32.add
          local.get $t36
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t37
          local.get $t37
          if
            i32.const 21
            local.set $pc
          else
            i32.const 22
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_notes
          local.set $t38
          local.get $__jn_frame
          i32.const 13
          local.get $t38
          call $jn_gc_frame_set_root
          local.get $t38
          local.set $gctmp.39
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.39
          call $jn_gc_frame_set_root
          local.get $gctmp.39
          local.set $t39
          local.get $__jn_frame
          i32.const 14
          local.get $t39
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t40
          local.get $t39
          i32.eqz
          if
            unreachable
          end
          local.get $t40
          i32.const 0
          i32.lt_s
          local.get $t40
          local.get $t39
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t39
          i32.const 16
          i32.add
          local.get $t40
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t41
          local.get $t41
          if
            i32.const 23
            local.set $pc
          else
            i32.const 24
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          call $_JN_SudokuCore_recomputeConflicts
          call $jnative_exception_pending
          local.set $t42
          local.get $t42
          if
            i32.const 25
            local.set $pc
          else
            i32.const 26
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_toggleNoteMode (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb7
        (block $bb6
        (block $bb5
        (block $bb4
        (block $bb3
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $exit
        )
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t1
          global.get $g_jn_static_SudokuCore_ST_PAUSED
          local.set $t2
          local.get $t1
          local.get $t2
          i32.eq
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 3
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_noteMode
          local.set $t4
          local.get $t4
          i32.const 0
          i32.eq
          local.set $t5
          local.get $t5
          if
            i32.const 5
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_noteMode
          i32.const 6
          local.set $pc
          br $dispatch
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          global.get $g_jn_static_SudokuCore_noteMode
          local.set $t6
          local.get $t6
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_noteMode
          i32.const 6
          local.set $pc
          br $dispatch
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_setNoteMode_i32 (param $on i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $on__slot i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb7
        (block $bb6
        (block $bb5
        (block $bb4
        (block $bb3
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $exit
        )
          local.get $on
          local.set $on__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t1
          global.get $g_jn_static_SudokuCore_ST_PAUSED
          local.set $t2
          local.get $t1
          local.get $t2
          i32.eq
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 3
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $on__slot
          local.set $t4
          local.get $t4
          i32.const 0
          i32.ne
          local.set $t5
          local.get $t5
          if
            i32.const 5
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_noteMode
          i32.const 6
          local.set $pc
          br $dispatch
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          global.get $g_jn_static_SudokuCore_noteMode
          local.set $t6
          local.get $t6
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_noteMode
          i32.const 6
          local.set $pc
          br $dispatch
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_undo (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $cell i32)
    (local $gctmp.9 i32)
    (local $gctmp.14 i32)
    (local $gctmp.19 i32)
    (local $gctmp.23 i32)
    (local $gctmp.28 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    i32.const 15
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $exit
        )
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t1
          global.get $g_jn_static_SudokuCore_ST_PAUSED
          local.set $t2
          local.get $t1
          local.get $t2
          i32.eq
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 3
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t4
          local.get $t4
          i32.const 0
          i32.le_s
          local.set $t5
          local.get $t5
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t6
          local.get $t6
          i32.const 1
          i32.sub
          local.set $t7
          local.get $t7
          global.set $g_jn_static_SudokuCore_histPos
          global.get $g_jn_static_SudokuCore_histCell
          local.set $t8
          local.get $__jn_frame
          i32.const 5
          local.get $t8
          call $jn_gc_frame_set_root
          local.get $t8
          local.set $gctmp.9
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.9
          call $jn_gc_frame_set_root
          local.get $gctmp.9
          local.set $t9
          local.get $__jn_frame
          i32.const 6
          local.get $t9
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t10
          local.get $t9
          i32.eqz
          if
            unreachable
          end
          local.get $t10
          i32.const 0
          i32.lt_s
          local.get $t10
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
          local.get $t10
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t11
          call $jnative_exception_pending
          local.set $t12
          local.get $t12
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t11
          local.set $cell
          global.get $g_jn_static_SudokuCore_histPrevVal
          local.set $t13
          local.get $__jn_frame
          i32.const 7
          local.get $t13
          call $jn_gc_frame_set_root
          local.get $t13
          local.set $gctmp.14
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.14
          call $jn_gc_frame_set_root
          local.get $gctmp.14
          local.set $t14
          local.get $__jn_frame
          i32.const 8
          local.get $t14
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t15
          local.get $t14
          i32.eqz
          if
            unreachable
          end
          local.get $t15
          i32.const 0
          i32.lt_s
          local.get $t15
          local.get $t14
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t14
          i32.const 16
          i32.add
          local.get $t15
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t16
          call $jnative_exception_pending
          local.set $t17
          local.get $t17
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t18
          local.get $__jn_frame
          i32.const 9
          local.get $t18
          call $jn_gc_frame_set_root
          local.get $t18
          local.set $gctmp.19
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.19
          call $jn_gc_frame_set_root
          local.get $gctmp.19
          local.set $t19
          local.get $__jn_frame
          i32.const 10
          local.get $t19
          call $jn_gc_frame_set_root
          local.get $cell
          local.set $t20
          local.get $t19
          i32.eqz
          if
            unreachable
          end
          local.get $t20
          i32.const 0
          i32.lt_s
          local.get $t20
          local.get $t19
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t19
          i32.const 16
          i32.add
          local.get $t20
          i32.const 4
          i32.mul
          i32.add
          local.get $t16
          i32.store
          call $jnative_exception_pending
          local.set $t21
          local.get $t21
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPrevNotes
          local.set $t22
          local.get $__jn_frame
          i32.const 11
          local.get $t22
          call $jn_gc_frame_set_root
          local.get $t22
          local.set $gctmp.23
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.23
          call $jn_gc_frame_set_root
          local.get $gctmp.23
          local.set $t23
          local.get $__jn_frame
          i32.const 12
          local.get $t23
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t24
          local.get $t23
          i32.eqz
          if
            unreachable
          end
          local.get $t24
          i32.const 0
          i32.lt_s
          local.get $t24
          local.get $t23
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t23
          i32.const 16
          i32.add
          local.get $t24
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t25
          call $jnative_exception_pending
          local.set $t26
          local.get $t26
          if
            i32.const 13
            local.set $pc
          else
            i32.const 14
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_notes
          local.set $t27
          local.get $__jn_frame
          i32.const 13
          local.get $t27
          call $jn_gc_frame_set_root
          local.get $t27
          local.set $gctmp.28
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.28
          call $jn_gc_frame_set_root
          local.get $gctmp.28
          local.set $t28
          local.get $__jn_frame
          i32.const 14
          local.get $t28
          call $jn_gc_frame_set_root
          local.get $cell
          local.set $t29
          local.get $t28
          i32.eqz
          if
            unreachable
          end
          local.get $t29
          i32.const 0
          i32.lt_s
          local.get $t29
          local.get $t28
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t28
          i32.const 16
          i32.add
          local.get $t29
          i32.const 4
          i32.mul
          i32.add
          local.get $t25
          i32.store
          call $jnative_exception_pending
          local.set $t30
          local.get $t30
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t31
          global.get $g_jn_static_SudokuCore_ST_WON
          local.set $t32
          local.get $t31
          local.get $t32
          i32.eq
          local.set $t33
          local.get $t33
          if
            i32.const 17
            local.set $pc
          else
            i32.const 18
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_ST_PLAYING
          local.set $t34
          local.get $t34
          global.set $g_jn_static_SudokuCore_status
          i32.const 18
          local.set $pc
          br $dispatch
        )
          call $_JN_SudokuCore_recomputeConflicts
          call $jnative_exception_pending
          local.set $t35
          local.get $t35
          if
            i32.const 19
            local.set $pc
          else
            i32.const 20
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_redo (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $cell i32)
    (local $gctmp.8 i32)
    (local $gctmp.13 i32)
    (local $gctmp.18 i32)
    (local $gctmp.22 i32)
    (local $gctmp.27 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    i32.const 15
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $exit
        )
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t1
          global.get $g_jn_static_SudokuCore_ST_PAUSED
          local.set $t2
          local.get $t1
          local.get $t2
          i32.eq
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 3
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t4
          global.get $g_jn_static_SudokuCore_histLen
          local.set $t5
          local.get $t4
          local.get $t5
          i32.ge_s
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
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histCell
          local.set $t7
          local.get $__jn_frame
          i32.const 5
          local.get $t7
          call $jn_gc_frame_set_root
          local.get $t7
          local.set $gctmp.8
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.8
          call $jn_gc_frame_set_root
          local.get $gctmp.8
          local.set $t8
          local.get $__jn_frame
          i32.const 6
          local.get $t8
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t9
          local.get $t8
          i32.eqz
          if
            unreachable
          end
          local.get $t9
          i32.const 0
          i32.lt_s
          local.get $t9
          local.get $t8
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t8
          i32.const 16
          i32.add
          local.get $t9
          i32.const 4
          i32.mul
          i32.add
          i32.load
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t10
          local.set $cell
          global.get $g_jn_static_SudokuCore_histNewVal
          local.set $t12
          local.get $__jn_frame
          i32.const 7
          local.get $t12
          call $jn_gc_frame_set_root
          local.get $t12
          local.set $gctmp.13
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.13
          call $jn_gc_frame_set_root
          local.get $gctmp.13
          local.set $t13
          local.get $__jn_frame
          i32.const 8
          local.get $t13
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t14
          local.get $t13
          i32.eqz
          if
            unreachable
          end
          local.get $t14
          i32.const 0
          i32.lt_s
          local.get $t14
          local.get $t13
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t13
          i32.const 16
          i32.add
          local.get $t14
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t15
          call $jnative_exception_pending
          local.set $t16
          local.get $t16
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t17
          local.get $__jn_frame
          i32.const 9
          local.get $t17
          call $jn_gc_frame_set_root
          local.get $t17
          local.set $gctmp.18
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.18
          call $jn_gc_frame_set_root
          local.get $gctmp.18
          local.set $t18
          local.get $__jn_frame
          i32.const 10
          local.get $t18
          call $jn_gc_frame_set_root
          local.get $cell
          local.set $t19
          local.get $t18
          i32.eqz
          if
            unreachable
          end
          local.get $t19
          i32.const 0
          i32.lt_s
          local.get $t19
          local.get $t18
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t18
          i32.const 16
          i32.add
          local.get $t19
          i32.const 4
          i32.mul
          i32.add
          local.get $t15
          i32.store
          call $jnative_exception_pending
          local.set $t20
          local.get $t20
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histNewNotes
          local.set $t21
          local.get $__jn_frame
          i32.const 11
          local.get $t21
          call $jn_gc_frame_set_root
          local.get $t21
          local.set $gctmp.22
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.22
          call $jn_gc_frame_set_root
          local.get $gctmp.22
          local.set $t22
          local.get $__jn_frame
          i32.const 12
          local.get $t22
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t23
          local.get $t22
          i32.eqz
          if
            unreachable
          end
          local.get $t23
          i32.const 0
          i32.lt_s
          local.get $t23
          local.get $t22
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t22
          i32.const 16
          i32.add
          local.get $t23
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t24
          call $jnative_exception_pending
          local.set $t25
          local.get $t25
          if
            i32.const 13
            local.set $pc
          else
            i32.const 14
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_notes
          local.set $t26
          local.get $__jn_frame
          i32.const 13
          local.get $t26
          call $jn_gc_frame_set_root
          local.get $t26
          local.set $gctmp.27
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.27
          call $jn_gc_frame_set_root
          local.get $gctmp.27
          local.set $t27
          local.get $__jn_frame
          i32.const 14
          local.get $t27
          call $jn_gc_frame_set_root
          local.get $cell
          local.set $t28
          local.get $t27
          i32.eqz
          if
            unreachable
          end
          local.get $t28
          i32.const 0
          i32.lt_s
          local.get $t28
          local.get $t27
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t27
          i32.const 16
          i32.add
          local.get $t28
          i32.const 4
          i32.mul
          i32.add
          local.get $t24
          i32.store
          call $jnative_exception_pending
          local.set $t29
          local.get $t29
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t30
          local.get $t30
          i32.const 1
          i32.add
          local.set $t31
          local.get $t31
          global.set $g_jn_static_SudokuCore_histPos
          call $_JN_SudokuCore_recomputeConflicts
          call $jnative_exception_pending
          local.set $t32
          local.get $t32
          if
            i32.const 17
            local.set $pc
          else
            i32.const 18
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          call $_JN_SudokuCore_checkWon
          call $jnative_exception_pending
          local.set $t33
          local.get $t33
          if
            i32.const 19
            local.set $pc
          else
            i32.const 20
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_hint (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $sc.tmp1250391581 i32)
    (local $i i32)
    (local $sc.tmp1725017993 i32)
    (local $gctmp.11 i32)
    (local $gctmp.17 i32)
    (local $gctmp.22 i32)
    (local $prevV i32)
    (local $gctmp.30 i32)
    (local $prevN i32)
    (local $gctmp.35 i32)
    (local $newV i32)
    (local $gctmp.40 i32)
    (local $gctmp.51 i32)
    (local $gctmp.55 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    i32.const 24
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $exit
        )
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t1
          global.get $g_jn_static_SudokuCore_ST_PAUSED
          local.set $t2
          local.get $t1
          local.get $t2
          i32.eq
          local.set $t3
          local.get $t3
          local.set $sc.tmp1250391581
          local.get $t3
          if
            i32.const 4
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t4
          global.get $g_jn_static_SudokuCore_ST_WON
          local.set $t5
          local.get $t4
          local.get $t5
          i32.eq
          local.set $t6
          local.get $t6
          local.set $sc.tmp1250391581
          i32.const 4
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1250391581
          local.set $t7
          local.get $t7
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 3
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $i
          i32.const 7
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t8
          local.get $t8
          i32.const 81
          i32.lt_s
          local.set $t9
          local.get $t9
          if
            i32.const 8
            local.set $pc
          else
            i32.const 9
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_given
          local.set $t10
          local.get $__jn_frame
          i32.const 8
          local.get $t10
          call $jn_gc_frame_set_root
          local.get $t10
          local.set $gctmp.11
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.11
          call $jn_gc_frame_set_root
          local.get $gctmp.11
          local.set $t11
          local.get $__jn_frame
          i32.const 9
          local.get $t11
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t12
          local.get $t11
          i32.eqz
          if
            unreachable
          end
          local.get $t12
          i32.const 0
          i32.lt_s
          local.get $t12
          local.get $t11
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t11
          i32.const 16
          i32.add
          local.get $t12
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t13
          call $jnative_exception_pending
          local.set $t14
          local.get $t14
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          i32.const 4
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t16
          local.get $__jn_frame
          i32.const 10
          local.get $t16
          call $jn_gc_frame_set_root
          local.get $t16
          local.set $gctmp.17
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.17
          call $jn_gc_frame_set_root
          local.get $gctmp.17
          local.set $t17
          local.get $__jn_frame
          i32.const 11
          local.get $t17
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t18
          local.get $t17
          i32.eqz
          if
            unreachable
          end
          local.get $t18
          i32.const 0
          i32.lt_s
          local.get $t18
          local.get $t17
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t17
          i32.const 16
          i32.add
          local.get $t18
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t19
          call $jnative_exception_pending
          local.set $t20
          local.get $t20
          if
            i32.const 14
            local.set $pc
          else
            i32.const 15
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp1725017993
          local.set $t27
          local.get $t27
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t13
          i32.const 0
          i32.eq
          local.set $t15
          local.get $t15
          local.set $sc.tmp1725017993
          local.get $t15
          if
            i32.const 10
            local.set $pc
          else
            i32.const 11
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_solution
          local.set $t21
          local.get $__jn_frame
          i32.const 12
          local.get $t21
          call $jn_gc_frame_set_root
          local.get $t21
          local.set $gctmp.22
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.22
          call $jn_gc_frame_set_root
          local.get $gctmp.22
          local.set $t22
          local.get $__jn_frame
          i32.const 13
          local.get $t22
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t23
          local.get $t22
          i32.eqz
          if
            unreachable
          end
          local.get $t23
          i32.const 0
          i32.lt_s
          local.get $t23
          local.get $t22
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t22
          i32.const 16
          i32.add
          local.get $t23
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t24
          call $jnative_exception_pending
          local.set $t25
          local.get $t25
          if
            i32.const 16
            local.set $pc
          else
            i32.const 17
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t19
          local.get $t24
          i32.ne
          local.set $t26
          local.get $t26
          local.set $sc.tmp1725017993
          i32.const 11
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t28
          local.get $t28
          global.set $g_jn_static_SudokuCore_selected
          global.get $g_jn_static_SudokuCore_values
          local.set $t29
          local.get $__jn_frame
          i32.const 14
          local.get $t29
          call $jn_gc_frame_set_root
          local.get $t29
          local.set $gctmp.30
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.30
          call $jn_gc_frame_set_root
          local.get $gctmp.30
          local.set $t30
          local.get $__jn_frame
          i32.const 15
          local.get $t30
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t31
          local.get $t30
          i32.eqz
          if
            unreachable
          end
          local.get $t31
          i32.const 0
          i32.lt_s
          local.get $t31
          local.get $t30
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t30
          i32.const 16
          i32.add
          local.get $t31
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t32
          call $jnative_exception_pending
          local.set $t33
          local.get $t33
          if
            i32.const 20
            local.set $pc
          else
            i32.const 21
            local.set $pc
          end
          br $dispatch
        )
          local.get $i
          local.set $t60
          local.get $t60
          i32.const 1
          i32.add
          local.set $t61
          local.get $t61
          local.set $i
          call $jn_gc_safepoint
          i32.const 7
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t32
          local.set $prevV
          global.get $g_jn_static_SudokuCore_notes
          local.set $t34
          local.get $__jn_frame
          i32.const 16
          local.get $t34
          call $jn_gc_frame_set_root
          local.get $t34
          local.set $gctmp.35
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.35
          call $jn_gc_frame_set_root
          local.get $gctmp.35
          local.set $t35
          local.get $__jn_frame
          i32.const 17
          local.get $t35
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t36
          local.get $t35
          i32.eqz
          if
            unreachable
          end
          local.get $t36
          i32.const 0
          i32.lt_s
          local.get $t36
          local.get $t35
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t35
          i32.const 16
          i32.add
          local.get $t36
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t37
          call $jnative_exception_pending
          local.set $t38
          local.get $t38
          if
            i32.const 22
            local.set $pc
          else
            i32.const 23
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t37
          local.set $prevN
          global.get $g_jn_static_SudokuCore_solution
          local.set $t39
          local.get $__jn_frame
          i32.const 18
          local.get $t39
          call $jn_gc_frame_set_root
          local.get $t39
          local.set $gctmp.40
          local.get $__jn_frame
          i32.const 5
          local.get $gctmp.40
          call $jn_gc_frame_set_root
          local.get $gctmp.40
          local.set $t40
          local.get $__jn_frame
          i32.const 19
          local.get $t40
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t41
          local.get $t40
          i32.eqz
          if
            unreachable
          end
          local.get $t41
          i32.const 0
          i32.lt_s
          local.get $t41
          local.get $t40
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t40
          i32.const 16
          i32.add
          local.get $t41
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t42
          call $jnative_exception_pending
          local.set $t43
          local.get $t43
          if
            i32.const 24
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t42
          local.set $newV
          local.get $i
          local.set $t44
          local.get $prevV
          local.set $t45
          local.get $newV
          local.set $t46
          local.get $prevN
          local.set $t47
          local.get $t44
          local.get $t45
          local.get $t46
          local.get $t47
          i32.const 0
          call $_JN_SudokuCore_pushHistory_i32_i32_i32_i32_i32
          call $jnative_exception_pending
          local.set $t48
          local.get $t48
          if
            i32.const 26
            local.set $pc
          else
            i32.const 27
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $newV
          local.set $t49
          global.get $g_jn_static_SudokuCore_values
          local.set $t50
          local.get $__jn_frame
          i32.const 20
          local.get $t50
          call $jn_gc_frame_set_root
          local.get $t50
          local.set $gctmp.51
          local.get $__jn_frame
          i32.const 6
          local.get $gctmp.51
          call $jn_gc_frame_set_root
          local.get $gctmp.51
          local.set $t51
          local.get $__jn_frame
          i32.const 21
          local.get $t51
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t52
          local.get $t51
          i32.eqz
          if
            unreachable
          end
          local.get $t52
          i32.const 0
          i32.lt_s
          local.get $t52
          local.get $t51
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t51
          i32.const 16
          i32.add
          local.get $t52
          i32.const 4
          i32.mul
          i32.add
          local.get $t49
          i32.store
          call $jnative_exception_pending
          local.set $t53
          local.get $t53
          if
            i32.const 28
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_notes
          local.set $t54
          local.get $__jn_frame
          i32.const 22
          local.get $t54
          call $jn_gc_frame_set_root
          local.get $t54
          local.set $gctmp.55
          local.get $__jn_frame
          i32.const 7
          local.get $gctmp.55
          call $jn_gc_frame_set_root
          local.get $gctmp.55
          local.set $t55
          local.get $__jn_frame
          i32.const 23
          local.get $t55
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t56
          local.get $t55
          i32.eqz
          if
            unreachable
          end
          local.get $t56
          i32.const 0
          i32.lt_s
          local.get $t56
          local.get $t55
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t55
          i32.const 16
          i32.add
          local.get $t56
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t57
          local.get $t57
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          call $_JN_SudokuCore_recomputeConflicts
          call $jnative_exception_pending
          local.set $t58
          local.get $t58
          if
            i32.const 32
            local.set $pc
          else
            i32.const 33
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          call $_JN_SudokuCore_checkWon
          call $jnative_exception_pending
          local.set $t59
          local.get $t59
          if
            i32.const 34
            local.set $pc
          else
            i32.const 35
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_pause (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
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
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t1
          global.get $g_jn_static_SudokuCore_ST_PLAYING
          local.set $t2
          local.get $t1
          local.get $t2
          i32.ne
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 3
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_ST_PAUSED
          local.set $t4
          local.get $t4
          global.set $g_jn_static_SudokuCore_status
          i32.const 0
          global.set $g_jn_static_SudokuCore_tickValid
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_resume (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
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
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t1
          global.get $g_jn_static_SudokuCore_ST_PAUSED
          local.set $t2
          local.get $t1
          local.get $t2
          i32.ne
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 3
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_ST_PLAYING
          local.set $t4
          local.get $t4
          global.set $g_jn_static_SudokuCore_status
          i32.const 0
          global.set $g_jn_static_SudokuCore_tickValid
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_tick_i32 (param $nowMs i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $nowMs__slot i32)
    (local $sc.tmp2619171 i32)
    (local $delta i32)
    (local $sc.tmp1728790703 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $exit
        )
          local.get $nowMs
          local.set $nowMs__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $nowMs__slot
          local.set $t1
          local.get $t1
          i32.const 0
          i32.lt_s
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
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          global.get $g_jn_static_SudokuCore_elapsedMs
          local.set $t3
          local.get $t3
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t4
          global.get $g_jn_static_SudokuCore_ST_PLAYING
          local.set $t5
          local.get $t4
          local.get $t5
          i32.eq
          local.set $t6
          local.get $t6
          local.set $sc.tmp2619171
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
          global.get $g_jn_static_SudokuCore_tickValid
          local.set $t7
          local.get $t7
          i32.const 0
          i32.ne
          local.set $t8
          local.get $t8
          local.set $sc.tmp2619171
          i32.const 6
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp2619171
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
          local.get $nowMs__slot
          local.set $t10
          global.get $g_jn_static_SudokuCore_lastTickMs
          local.set $t11
          local.get $t10
          local.get $t11
          i32.sub
          local.set $t12
          local.get $t12
          local.set $delta
          local.get $delta
          local.set $t13
          local.get $t13
          i32.const 0
          i32.gt_s
          local.set $t14
          local.get $t14
          local.set $sc.tmp1728790703
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
          local.get $nowMs__slot
          local.set $t21
          local.get $t21
          global.set $g_jn_static_SudokuCore_lastTickMs
          i32.const 1
          global.set $g_jn_static_SudokuCore_tickValid
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          global.get $g_jn_static_SudokuCore_elapsedMs
          local.set $t22
          local.get $t22
          local.set $ret
          br $exit
        )
          local.get $delta
          local.set $t15
          local.get $t15
          i32.const 60000
          i32.lt_s
          local.set $t16
          local.get $t16
          local.set $sc.tmp1728790703
          i32.const 10
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1728790703
          local.set $t17
          local.get $t17
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_elapsedMs
          local.set $t18
          local.get $delta
          local.set $t19
          local.get $t18
          local.get $t19
          i32.add
          local.set $t20
          local.get $t20
          global.set $g_jn_static_SudokuCore_elapsedMs
          i32.const 12
          local.set $pc
          br $dispatch
        )
          i32.const 8
          local.set $pc
          br $dispatch
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_getElapsedMs (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
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
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_elapsedMs
          local.set $t1
          local.get $t1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_getStatus (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
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
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t1
          local.get $t1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_getSelected (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
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
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_selected
          local.set $t1
          local.get $t1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_getNoteMode (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
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
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_noteMode
          local.set $t1
          local.get $t1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_getPuzzleId (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
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
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_puzzleId
          local.set $t1
          local.get $t1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_getDifficulty (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
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
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_difficulty
          local.set $t1
          local.get $t1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_getValue_i32 (param $idx i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $idx__slot i32)
    (local $sc.tmp1970881185 i32)
    (local $gctmp.7 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    i32.const 3
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $exit
        )
          local.get $idx
          local.set $idx__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $idx__slot
          local.set $t1
          local.get $t1
          i32.const 0
          i32.lt_s
          local.set $t2
          local.get $t2
          local.set $sc.tmp1970881185
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $idx__slot
          local.set $t3
          local.get $t3
          i32.const 80
          i32.gt_s
          local.set $t4
          local.get $t4
          local.set $sc.tmp1970881185
          i32.const 4
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1970881185
          local.set $t5
          local.get $t5
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          global.get $g_jn_static_SudokuCore_values
          local.set $t6
          local.get $__jn_frame
          i32.const 1
          local.get $t6
          call $jn_gc_frame_set_root
          local.get $t6
          local.set $gctmp.7
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.7
          call $jn_gc_frame_set_root
          local.get $gctmp.7
          local.set $t7
          local.get $__jn_frame
          i32.const 2
          local.get $t7
          call $jn_gc_frame_set_root
          local.get $idx__slot
          local.set $t8
          local.get $t7
          i32.eqz
          if
            unreachable
          end
          local.get $t8
          i32.const 0
          i32.lt_s
          local.get $t8
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
          local.get $t8
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t9
          call $jnative_exception_pending
          local.set $t10
          local.get $t10
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t9
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_getGiven_i32 (param $idx i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $idx__slot i32)
    (local $sc.tmp1665404403 i32)
    (local $gctmp.7 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    i32.const 3
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $exit
        )
          local.get $idx
          local.set $idx__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $idx__slot
          local.set $t1
          local.get $t1
          i32.const 0
          i32.lt_s
          local.set $t2
          local.get $t2
          local.set $sc.tmp1665404403
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $idx__slot
          local.set $t3
          local.get $t3
          i32.const 80
          i32.gt_s
          local.set $t4
          local.get $t4
          local.set $sc.tmp1665404403
          i32.const 4
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1665404403
          local.set $t5
          local.get $t5
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          global.get $g_jn_static_SudokuCore_given
          local.set $t6
          local.get $__jn_frame
          i32.const 1
          local.get $t6
          call $jn_gc_frame_set_root
          local.get $t6
          local.set $gctmp.7
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.7
          call $jn_gc_frame_set_root
          local.get $gctmp.7
          local.set $t7
          local.get $__jn_frame
          i32.const 2
          local.get $t7
          call $jn_gc_frame_set_root
          local.get $idx__slot
          local.set $t8
          local.get $t7
          i32.eqz
          if
            unreachable
          end
          local.get $t8
          i32.const 0
          i32.lt_s
          local.get $t8
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
          local.get $t8
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t9
          call $jnative_exception_pending
          local.set $t10
          local.get $t10
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t9
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_getNotes_i32 (param $idx i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $idx__slot i32)
    (local $sc.tmp1773206895 i32)
    (local $gctmp.7 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    i32.const 3
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $exit
        )
          local.get $idx
          local.set $idx__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $idx__slot
          local.set $t1
          local.get $t1
          i32.const 0
          i32.lt_s
          local.set $t2
          local.get $t2
          local.set $sc.tmp1773206895
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $idx__slot
          local.set $t3
          local.get $t3
          i32.const 80
          i32.gt_s
          local.set $t4
          local.get $t4
          local.set $sc.tmp1773206895
          i32.const 4
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1773206895
          local.set $t5
          local.get $t5
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          global.get $g_jn_static_SudokuCore_notes
          local.set $t6
          local.get $__jn_frame
          i32.const 1
          local.get $t6
          call $jn_gc_frame_set_root
          local.get $t6
          local.set $gctmp.7
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.7
          call $jn_gc_frame_set_root
          local.get $gctmp.7
          local.set $t7
          local.get $__jn_frame
          i32.const 2
          local.get $t7
          call $jn_gc_frame_set_root
          local.get $idx__slot
          local.set $t8
          local.get $t7
          i32.eqz
          if
            unreachable
          end
          local.get $t8
          i32.const 0
          i32.lt_s
          local.get $t8
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
          local.get $t8
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t9
          call $jnative_exception_pending
          local.set $t10
          local.get $t10
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t9
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_getConflict_i32 (param $idx i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $idx__slot i32)
    (local $sc.tmp2012846597 i32)
    (local $gctmp.7 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    i32.const 3
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $exit
        )
          local.get $idx
          local.set $idx__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $idx__slot
          local.set $t1
          local.get $t1
          i32.const 0
          i32.lt_s
          local.set $t2
          local.get $t2
          local.set $sc.tmp2012846597
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $idx__slot
          local.set $t3
          local.get $t3
          i32.const 80
          i32.gt_s
          local.set $t4
          local.get $t4
          local.set $sc.tmp2012846597
          i32.const 4
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp2012846597
          local.set $t5
          local.get $t5
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          global.get $g_jn_static_SudokuCore_conflict
          local.set $t6
          local.get $__jn_frame
          i32.const 1
          local.get $t6
          call $jn_gc_frame_set_root
          local.get $t6
          local.set $gctmp.7
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.7
          call $jn_gc_frame_set_root
          local.get $gctmp.7
          local.set $t7
          local.get $__jn_frame
          i32.const 2
          local.get $t7
          call $jn_gc_frame_set_root
          local.get $idx__slot
          local.set $t8
          local.get $t7
          i32.eqz
          if
            unreachable
          end
          local.get $t8
          i32.const 0
          i32.lt_s
          local.get $t8
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
          local.get $t8
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t9
          call $jnative_exception_pending
          local.set $t10
          local.get $t10
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t9
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_getSolutionDigit_i32 (param $idx i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $idx__slot i32)
    (local $sc.tmp495792375 i32)
    (local $gctmp.7 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    i32.const 3
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $exit
        )
          local.get $idx
          local.set $idx__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $idx__slot
          local.set $t1
          local.get $t1
          i32.const 0
          i32.lt_s
          local.set $t2
          local.get $t2
          local.set $sc.tmp495792375
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $idx__slot
          local.set $t3
          local.get $t3
          i32.const 80
          i32.gt_s
          local.set $t4
          local.get $t4
          local.set $sc.tmp495792375
          i32.const 4
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp495792375
          local.set $t5
          local.get $t5
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          global.get $g_jn_static_SudokuCore_solution
          local.set $t6
          local.get $__jn_frame
          i32.const 1
          local.get $t6
          call $jn_gc_frame_set_root
          local.get $t6
          local.set $gctmp.7
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.7
          call $jn_gc_frame_set_root
          local.get $gctmp.7
          local.set $t7
          local.get $__jn_frame
          i32.const 2
          local.get $t7
          call $jn_gc_frame_set_root
          local.get $idx__slot
          local.set $t8
          local.get $t7
          i32.eqz
          if
            unreachable
          end
          local.get $t8
          i32.const 0
          i32.lt_s
          local.get $t8
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
          local.get $t8
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t9
          call $jnative_exception_pending
          local.set $t10
          local.get $t10
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t9
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_canUndo (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
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
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t1
          local.get $t1
          i32.const 0
          i32.gt_s
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
  (func $_JN_SudokuCore_canRedo (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
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
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t1
          global.get $g_jn_static_SudokuCore_histLen
          local.set $t2
          local.get $t1
          local.get $t2
          i32.lt_s
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
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
  (func $_JN_SudokuCore_isComplete (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
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
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t1
          global.get $g_jn_static_SudokuCore_ST_WON
          local.set $t2
          local.get $t1
          local.get $t2
          i32.eq
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
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
  (func $_JN_SudokuCore_getHighlightDigit (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $v i32)
    (local $gctmp.2 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    i32.const 3
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
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t1
          local.get $__jn_frame
          i32.const 1
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t1
          local.set $gctmp.2
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.2
          call $jn_gc_frame_set_root
          local.get $gctmp.2
          local.set $t2
          local.get $__jn_frame
          i32.const 2
          local.get $t2
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_selected
          local.set $t3
          local.get $t2
          i32.eqz
          if
            unreachable
          end
          local.get $t3
          i32.const 0
          i32.lt_s
          local.get $t3
          local.get $t2
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t2
          i32.const 16
          i32.add
          local.get $t3
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t4
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t4
          local.set $v
          local.get $v
          local.set $t6
          local.get $t6
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_sameRow_i32 (param $idx i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $idx__slot i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $exit
        )
          local.get $idx
          local.set $idx__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_selected
          local.set $t1
          local.get $t1
          i32.const 9
          i32.div_s
          local.set $t2
          call $jnative_exception_pending
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $idx__slot
          local.set $t4
          local.get $t4
          i32.const 9
          i32.div_s
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t2
          local.get $t5
          i32.eq
          local.set $t7
          local.get $t7
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
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
  (func $_JN_SudokuCore_sameCol_i32 (param $idx i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $idx__slot i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $exit
        )
          local.get $idx
          local.set $idx__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_selected
          local.set $t1
          local.get $t1
          i32.const 9
          i32.rem_s
          local.set $t2
          call $jnative_exception_pending
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $idx__slot
          local.set $t4
          local.get $t4
          i32.const 9
          i32.rem_s
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t2
          local.get $t5
          i32.eq
          local.set $t7
          local.get $t7
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
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
  (func $_JN_SudokuCore_sameBox_i32 (param $idx i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $idx__slot i32)
    (local $sr i32)
    (local $sc i32)
    (local $r i32)
    (local $c i32)
    (local $sc.tmp1957502751 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $exit
        )
          local.get $idx
          local.set $idx__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_selected
          local.set $t1
          local.get $t1
          i32.const 9
          i32.div_s
          local.set $t2
          call $jnative_exception_pending
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t2
          local.set $sr
          global.get $g_jn_static_SudokuCore_selected
          local.set $t4
          local.get $t4
          i32.const 9
          i32.rem_s
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t5
          local.set $sc
          local.get $idx__slot
          local.set $t7
          local.get $t7
          i32.const 9
          i32.div_s
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t8
          local.set $r
          local.get $idx__slot
          local.set $t10
          local.get $t10
          i32.const 9
          i32.rem_s
          local.set $t11
          call $jnative_exception_pending
          local.set $t12
          local.get $t12
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t11
          local.set $c
          local.get $sr
          local.set $t13
          local.get $t13
          i32.const 3
          i32.div_s
          local.set $t14
          call $jnative_exception_pending
          local.set $t15
          local.get $t15
          if
            i32.const 13
            local.set $pc
          else
            i32.const 14
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc
          local.set $t20
          local.get $t20
          i32.const 3
          i32.div_s
          local.set $t21
          call $jnative_exception_pending
          local.set $t22
          local.get $t22
          if
            i32.const 17
            local.set $pc
          else
            i32.const 18
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp1957502751
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $r
          local.set $t16
          local.get $t16
          i32.const 3
          i32.div_s
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t14
          local.get $t17
          i32.eq
          local.set $t19
          local.get $t19
          local.set $sc.tmp1957502751
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $c
          local.set $t23
          local.get $t23
          i32.const 3
          i32.div_s
          local.set $t24
          call $jnative_exception_pending
          local.set $t25
          local.get $t25
          if
            i32.const 19
            local.set $pc
          else
            i32.const 20
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t21
          local.get $t24
          i32.eq
          local.set $t26
          local.get $t26
          local.set $sc.tmp1957502751
          i32.const 12
          local.set $pc
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
  (func $_JN_SudokuCore_prepareSave (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $gctmp.2 i32)
    (local $i i32)
    (local $gctmp.34 i32)
    (local $gctmp.46 i32)
    (local $gctmp.58 i32)
    (local $gctmp.71 i32)
    (local $gctmp.78 i32)
    (local $gctmp.85 i32)
    (local $gctmp.92 i32)
    (local $gctmp.99 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    (local $t72 i32)
    (local $t73 i32)
    (local $t74 i32)
    (local $t75 i32)
    (local $t76 i32)
    (local $t77 i32)
    (local $t78 i32)
    (local $t79 i32)
    (local $t80 i32)
    (local $t81 i32)
    (local $t82 i32)
    (local $t83 i32)
    (local $t84 i32)
    (local $t85 i32)
    (local $t86 i32)
    (local $t87 i32)
    (local $t88 i32)
    (local $t89 i32)
    (local $t90 i32)
    (local $t91 i32)
    (local $t92 i32)
    (local $t93 i32)
    (local $t94 i32)
    (local $t95 i32)
    (local $t96 i32)
    (local $t97 i32)
    (local $t98 i32)
    (local $t99 i32)
    (local $t100 i32)
    (local $t101 i32)
    (local $t102 i32)
    (local $t103 i32)
    (local $t104 i32)
    (local $t105 i32)
    (local $t106 i32)
    (local $t107 i32)
    (local $t108 i32)
    i32.const 27
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb104
        (block $bb103
        (block $bb102
        (block $bb101
        (block $bb100
        (block $bb99
        (block $bb98
        (block $bb97
        (block $bb96
        (block $bb95
        (block $bb94
        (block $bb93
        (block $bb92
        (block $bb91
        (block $bb90
        (block $bb89
        (block $bb88
        (block $bb87
        (block $bb86
        (block $bb85
        (block $bb84
        (block $bb83
        (block $bb82
        (block $bb81
        (block $bb80
        (block $bb79
        (block $bb78
        (block $bb77
        (block $bb76
        (block $bb75
        (block $bb74
        (block $bb73
        (block $bb72
        (block $bb71
        (block $bb70
        (block $bb69
        (block $bb68
        (block $bb67
        (block $bb66
        (block $bb65
        (block $bb64
        (block $bb63
        (block $bb62
        (block $bb61
        (block $bb60
        (block $bb59
        (block $bb58
        (block $bb57
        (block $bb56
        (block $bb55
        (block $bb54
        (block $bb53
        (block $bb52
        (block $bb51
        (block $bb50
        (block $bb49
        (block $bb48
        (block $bb47
        (block $bb46
        (block $bb45
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $bb45 $bb46 $bb47 $bb48 $bb49 $bb50 $bb51 $bb52 $bb53 $bb54 $bb55 $bb56 $bb57 $bb58 $bb59 $bb60 $bb61 $bb62 $bb63 $bb64 $bb65 $bb66 $bb67 $bb68 $bb69 $bb70 $bb71 $bb72 $bb73 $bb74 $bb75 $bb76 $bb77 $bb78 $bb79 $bb80 $bb81 $bb82 $bb83 $bb84 $bb85 $bb86 $bb87 $bb88 $bb89 $bb90 $bb91 $bb92 $bb93 $bb94 $bb95 $bb96 $bb97 $bb98 $bb99 $bb100 $bb101 $bb102 $bb103 $bb104 $exit
        )
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_saveLen
          i32.const 2048
          i32.const 5
          call $jn_str
          local.set $t1
          local.get $__jn_frame
          i32.const 9
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t1
          local.set $gctmp.2
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.2
          call $jn_gc_frame_set_root
          local.get $gctmp.2
          local.set $t2
          local.get $__jn_frame
          i32.const 10
          local.get $t2
          call $jn_gc_frame_set_root
          local.get $t2
          call $_JN_SudokuCore_emitStr_CString
          call $jnative_exception_pending
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_SAVE_VERSION
          local.set $t4
          local.get $t4
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t5
          local.get $t5
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 10
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t6
          local.get $t6
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_puzzleId
          local.set $t7
          local.get $t7
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t8
          local.get $t8
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 10
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t9
          local.get $t9
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_difficulty
          local.set $t10
          local.get $t10
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t11
          local.get $t11
          if
            i32.const 13
            local.set $pc
          else
            i32.const 14
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 10
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t12
          local.get $t12
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t13
          local.get $t13
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t14
          local.get $t14
          if
            i32.const 17
            local.set $pc
          else
            i32.const 18
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 10
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t15
          local.get $t15
          if
            i32.const 19
            local.set $pc
          else
            i32.const 20
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_elapsedMs
          local.set $t16
          local.get $t16
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t17
          local.get $t17
          if
            i32.const 21
            local.set $pc
          else
            i32.const 22
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 10
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t18
          local.get $t18
          if
            i32.const 23
            local.set $pc
          else
            i32.const 24
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_noteMode
          local.set $t19
          local.get $t19
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t20
          local.get $t20
          if
            i32.const 25
            local.set $pc
          else
            i32.const 26
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 10
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t21
          local.get $t21
          if
            i32.const 27
            local.set $pc
          else
            i32.const 28
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_selected
          local.set $t22
          local.get $t22
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t23
          local.get $t23
          if
            i32.const 29
            local.set $pc
          else
            i32.const 30
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 10
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t24
          local.get $t24
          if
            i32.const 31
            local.set $pc
          else
            i32.const 32
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t25
          local.get $t25
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t26
          local.get $t26
          if
            i32.const 33
            local.set $pc
          else
            i32.const 34
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 10
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t27
          local.get $t27
          if
            i32.const 35
            local.set $pc
          else
            i32.const 36
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histLen
          local.set $t28
          local.get $t28
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t29
          local.get $t29
          if
            i32.const 37
            local.set $pc
          else
            i32.const 38
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 10
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t30
          local.get $t30
          if
            i32.const 39
            local.set $pc
          else
            i32.const 40
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $i
          i32.const 41
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t31
          local.get $t31
          i32.const 81
          i32.lt_s
          local.set $t32
          local.get $t32
          if
            i32.const 42
            local.set $pc
          else
            i32.const 43
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_given
          local.set $t33
          local.get $__jn_frame
          i32.const 11
          local.get $t33
          call $jn_gc_frame_set_root
          local.get $t33
          local.set $gctmp.34
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.34
          call $jn_gc_frame_set_root
          local.get $gctmp.34
          local.set $t34
          local.get $__jn_frame
          i32.const 12
          local.get $t34
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t35
          local.get $t34
          i32.eqz
          if
            unreachable
          end
          local.get $t35
          i32.const 0
          i32.lt_s
          local.get $t35
          local.get $t34
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t34
          i32.const 16
          i32.add
          local.get $t35
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t36
          call $jnative_exception_pending
          local.set $t37
          local.get $t37
          if
            i32.const 44
            local.set $pc
          else
            i32.const 45
            local.set $pc
          end
          br $dispatch
        )
          i32.const 10
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t42
          local.get $t42
          if
            i32.const 48
            local.set $pc
          else
            i32.const 49
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 48
          local.get $t36
          i32.add
          local.set $t38
          local.get $t38
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t39
          local.get $t39
          if
            i32.const 46
            local.set $pc
          else
            i32.const 47
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t40
          local.get $t40
          i32.const 1
          i32.add
          local.set $t41
          local.get $t41
          local.set $i
          call $jn_gc_safepoint
          i32.const 41
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $i
          i32.const 50
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t43
          local.get $t43
          i32.const 81
          i32.lt_s
          local.set $t44
          local.get $t44
          if
            i32.const 51
            local.set $pc
          else
            i32.const 52
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t45
          local.get $__jn_frame
          i32.const 13
          local.get $t45
          call $jn_gc_frame_set_root
          local.get $t45
          local.set $gctmp.46
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.46
          call $jn_gc_frame_set_root
          local.get $gctmp.46
          local.set $t46
          local.get $__jn_frame
          i32.const 14
          local.get $t46
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t47
          local.get $t46
          i32.eqz
          if
            unreachable
          end
          local.get $t47
          i32.const 0
          i32.lt_s
          local.get $t47
          local.get $t46
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t46
          i32.const 16
          i32.add
          local.get $t47
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t48
          call $jnative_exception_pending
          local.set $t49
          local.get $t49
          if
            i32.const 53
            local.set $pc
          else
            i32.const 54
            local.set $pc
          end
          br $dispatch
        )
          i32.const 10
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t54
          local.get $t54
          if
            i32.const 57
            local.set $pc
          else
            i32.const 58
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 48
          local.get $t48
          i32.add
          local.set $t50
          local.get $t50
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t51
          local.get $t51
          if
            i32.const 55
            local.set $pc
          else
            i32.const 56
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t52
          local.get $t52
          i32.const 1
          i32.add
          local.set $t53
          local.get $t53
          local.set $i
          call $jn_gc_safepoint
          i32.const 50
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $i
          i32.const 59
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t55
          local.get $t55
          i32.const 81
          i32.lt_s
          local.set $t56
          local.get $t56
          if
            i32.const 60
            local.set $pc
          else
            i32.const 61
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_notes
          local.set $t57
          local.get $__jn_frame
          i32.const 15
          local.get $t57
          call $jn_gc_frame_set_root
          local.get $t57
          local.set $gctmp.58
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.58
          call $jn_gc_frame_set_root
          local.get $gctmp.58
          local.set $t58
          local.get $__jn_frame
          i32.const 16
          local.get $t58
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t59
          local.get $t58
          i32.eqz
          if
            unreachable
          end
          local.get $t59
          i32.const 0
          i32.lt_s
          local.get $t59
          local.get $t58
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t58
          i32.const 16
          i32.add
          local.get $t59
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t60
          call $jnative_exception_pending
          local.set $t61
          local.get $t61
          if
            i32.const 62
            local.set $pc
          else
            i32.const 63
            local.set $pc
          end
          br $dispatch
        )
          i32.const 10
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t66
          local.get $t66
          if
            i32.const 68
            local.set $pc
          else
            i32.const 69
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t60
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t62
          local.get $t62
          if
            i32.const 64
            local.set $pc
          else
            i32.const 65
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 44
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t63
          local.get $t63
          if
            i32.const 66
            local.set $pc
          else
            i32.const 67
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t64
          local.get $t64
          i32.const 1
          i32.add
          local.set $t65
          local.get $t65
          local.set $i
          call $jn_gc_safepoint
          i32.const 59
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $i
          i32.const 70
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t67
          global.get $g_jn_static_SudokuCore_histLen
          local.set $t68
          local.get $t67
          local.get $t68
          i32.lt_s
          local.set $t69
          local.get $t69
          if
            i32.const 71
            local.set $pc
          else
            i32.const 72
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_histCell
          local.set $t70
          local.get $__jn_frame
          i32.const 17
          local.get $t70
          call $jn_gc_frame_set_root
          local.get $t70
          local.set $gctmp.71
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.71
          call $jn_gc_frame_set_root
          local.get $gctmp.71
          local.set $t71
          local.get $__jn_frame
          i32.const 18
          local.get $t71
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t72
          local.get $t71
          i32.eqz
          if
            unreachable
          end
          local.get $t72
          i32.const 0
          i32.lt_s
          local.get $t72
          local.get $t71
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t71
          i32.const 16
          i32.add
          local.get $t72
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t73
          call $jnative_exception_pending
          local.set $t74
          local.get $t74
          if
            i32.const 73
            local.set $pc
          else
            i32.const 74
            local.set $pc
          end
          br $dispatch
        )
          i32.const 10
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t107
          local.get $t107
          if
            i32.const 103
            local.set $pc
          else
            i32.const 104
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t73
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t75
          local.get $t75
          if
            i32.const 75
            local.set $pc
          else
            i32.const 76
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 44
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t76
          local.get $t76
          if
            i32.const 77
            local.set $pc
          else
            i32.const 78
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPrevVal
          local.set $t77
          local.get $__jn_frame
          i32.const 19
          local.get $t77
          call $jn_gc_frame_set_root
          local.get $t77
          local.set $gctmp.78
          local.get $__jn_frame
          i32.const 5
          local.get $gctmp.78
          call $jn_gc_frame_set_root
          local.get $gctmp.78
          local.set $t78
          local.get $__jn_frame
          i32.const 20
          local.get $t78
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t79
          local.get $t78
          i32.eqz
          if
            unreachable
          end
          local.get $t79
          i32.const 0
          i32.lt_s
          local.get $t79
          local.get $t78
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t78
          i32.const 16
          i32.add
          local.get $t79
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t80
          call $jnative_exception_pending
          local.set $t81
          local.get $t81
          if
            i32.const 79
            local.set $pc
          else
            i32.const 80
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t80
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t82
          local.get $t82
          if
            i32.const 81
            local.set $pc
          else
            i32.const 82
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 44
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t83
          local.get $t83
          if
            i32.const 83
            local.set $pc
          else
            i32.const 84
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histNewVal
          local.set $t84
          local.get $__jn_frame
          i32.const 21
          local.get $t84
          call $jn_gc_frame_set_root
          local.get $t84
          local.set $gctmp.85
          local.get $__jn_frame
          i32.const 6
          local.get $gctmp.85
          call $jn_gc_frame_set_root
          local.get $gctmp.85
          local.set $t85
          local.get $__jn_frame
          i32.const 22
          local.get $t85
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t86
          local.get $t85
          i32.eqz
          if
            unreachable
          end
          local.get $t86
          i32.const 0
          i32.lt_s
          local.get $t86
          local.get $t85
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t85
          i32.const 16
          i32.add
          local.get $t86
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t87
          call $jnative_exception_pending
          local.set $t88
          local.get $t88
          if
            i32.const 85
            local.set $pc
          else
            i32.const 86
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t87
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t89
          local.get $t89
          if
            i32.const 87
            local.set $pc
          else
            i32.const 88
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 44
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t90
          local.get $t90
          if
            i32.const 89
            local.set $pc
          else
            i32.const 90
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPrevNotes
          local.set $t91
          local.get $__jn_frame
          i32.const 23
          local.get $t91
          call $jn_gc_frame_set_root
          local.get $t91
          local.set $gctmp.92
          local.get $__jn_frame
          i32.const 7
          local.get $gctmp.92
          call $jn_gc_frame_set_root
          local.get $gctmp.92
          local.set $t92
          local.get $__jn_frame
          i32.const 24
          local.get $t92
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t93
          local.get $t92
          i32.eqz
          if
            unreachable
          end
          local.get $t93
          i32.const 0
          i32.lt_s
          local.get $t93
          local.get $t92
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t92
          i32.const 16
          i32.add
          local.get $t93
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t94
          call $jnative_exception_pending
          local.set $t95
          local.get $t95
          if
            i32.const 91
            local.set $pc
          else
            i32.const 92
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t94
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t96
          local.get $t96
          if
            i32.const 93
            local.set $pc
          else
            i32.const 94
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 44
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t97
          local.get $t97
          if
            i32.const 95
            local.set $pc
          else
            i32.const 96
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histNewNotes
          local.set $t98
          local.get $__jn_frame
          i32.const 25
          local.get $t98
          call $jn_gc_frame_set_root
          local.get $t98
          local.set $gctmp.99
          local.get $__jn_frame
          i32.const 8
          local.get $gctmp.99
          call $jn_gc_frame_set_root
          local.get $gctmp.99
          local.set $t99
          local.get $__jn_frame
          i32.const 26
          local.get $t99
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t100
          local.get $t99
          i32.eqz
          if
            unreachable
          end
          local.get $t100
          i32.const 0
          i32.lt_s
          local.get $t100
          local.get $t99
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t99
          i32.const 16
          i32.add
          local.get $t100
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t101
          call $jnative_exception_pending
          local.set $t102
          local.get $t102
          if
            i32.const 97
            local.set $pc
          else
            i32.const 98
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t101
          call $_JN_SudokuCore_emitInt_i32
          call $jnative_exception_pending
          local.set $t103
          local.get $t103
          if
            i32.const 99
            local.set $pc
          else
            i32.const 100
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 59
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t104
          local.get $t104
          if
            i32.const 101
            local.set $pc
          else
            i32.const 102
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t105
          local.get $t105
          i32.const 1
          i32.add
          local.set $t106
          local.get $t106
          local.set $i
          call $jn_gc_safepoint
          i32.const 70
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          global.get $g_jn_static_SudokuCore_saveLen
          local.set $t108
          local.get $t108
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_getSaveLen (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
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
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_saveLen
          local.set $t1
          local.get $t1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_getSaveChar_i32 (param $i i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $i__slot i32)
    (local $sc.tmp1177377518 i32)
    (local $gctmp.8 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    i32.const 3
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $exit
        )
          local.get $i
          local.set $i__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i__slot
          local.set $t1
          local.get $t1
          i32.const 0
          i32.lt_s
          local.set $t2
          local.get $t2
          local.set $sc.tmp1177377518
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $i__slot
          local.set $t3
          global.get $g_jn_static_SudokuCore_saveLen
          local.set $t4
          local.get $t3
          local.get $t4
          i32.ge_s
          local.set $t5
          local.get $t5
          local.set $sc.tmp1177377518
          i32.const 4
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1177377518
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
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          global.get $g_jn_static_SudokuCore_saveBuf
          local.set $t7
          local.get $__jn_frame
          i32.const 1
          local.get $t7
          call $jn_gc_frame_set_root
          local.get $t7
          local.set $gctmp.8
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.8
          call $jn_gc_frame_set_root
          local.get $gctmp.8
          local.set $t8
          local.get $__jn_frame
          i32.const 2
          local.get $t8
          call $jn_gc_frame_set_root
          local.get $i__slot
          local.set $t9
          local.get $t8
          i32.eqz
          if
            unreachable
          end
          local.get $t9
          i32.const 0
          i32.lt_s
          local.get $t9
          local.get $t8
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t8
          i32.const 16
          i32.add
          local.get $t9
          i32.const 4
          i32.mul
          i32.add
          i32.load
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t10
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_loadSave_CString (param $data i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $data__slot i32)
    (local $n i32)
    (local $sc.tmp1800890735 i32)
    (local $pos i32)
    (local $gctmp.15 i32)
    (local $gctmp.21 i32)
    (local $ver i32)
    (local $pid i32)
    (local $diff i32)
    (local $st i32)
    (local $el i32)
    (local $nm i32)
    (local $sel i32)
    (local $hp i32)
    (local $hl i32)
    (local $sc.tmp352359770 i32)
    (local $sc.tmp243745864 i32)
    (local $sc.tmp699780352 i32)
    (local $sc.tmp1613255205 i32)
    (local $sc.tmp1897115967 i32)
    (local $sc.tmp1166151249 i32)
    (local $sc.tmp1121453612 i32)
    (local $sc.tmp1615056168 i32)
    (local $gTmp i32)
    (local $gctmp.105 i32)
    (local $vTmp i32)
    (local $gctmp.108 i32)
    (local $nTmp i32)
    (local $gctmp.111 i32)
    (local $i i32)
    (local $sc.tmp213193302 i32)
    (local $sc.tmp1502635287 i32)
    (local $sc.tmp1543237999 i32)
    (local $sc.tmp632249781 i32)
    (local $hc i32)
    (local $gctmp.181 i32)
    (local $hpv i32)
    (local $gctmp.185 i32)
    (local $hnv i32)
    (local $gctmp.189 i32)
    (local $hpn i32)
    (local $gctmp.193 i32)
    (local $hnn i32)
    (local $gctmp.197 i32)
    (local $gctmp.221 i32)
    (local $gctmp.225 i32)
    (local $sols i32)
    (local $gctmp.231 i32)
    (local $gctmp.237 i32)
    (local $gctmp.239 i32)
    (local $gctmp.242 i32)
    (local $gctmp.253 i32)
    (local $gctmp.261 i32)
    (local $gctmp.269 i32)
    (local $gctmp.273 i32)
    (local $gctmp.278 i32)
    (local $gctmp.291 i32)
    (local $gctmp.299 i32)
    (local $gctmp.307 i32)
    (local $gctmp.315 i32)
    (local $gctmp.323 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    (local $t72 i32)
    (local $t73 i32)
    (local $t74 i32)
    (local $t75 i32)
    (local $t76 i32)
    (local $t77 i32)
    (local $t78 i32)
    (local $t79 i32)
    (local $t80 i32)
    (local $t81 i32)
    (local $t82 i32)
    (local $t83 i32)
    (local $t84 i32)
    (local $t85 i32)
    (local $t86 i32)
    (local $t87 i32)
    (local $t88 i32)
    (local $t89 i32)
    (local $t90 i32)
    (local $t91 i32)
    (local $t92 i32)
    (local $t93 i32)
    (local $t94 i32)
    (local $t95 i32)
    (local $t96 i32)
    (local $t97 i32)
    (local $t98 i32)
    (local $t99 i32)
    (local $t100 i32)
    (local $t101 i32)
    (local $t102 i32)
    (local $t103 i32)
    (local $t104 i32)
    (local $t105 i32)
    (local $t106 i32)
    (local $t107 i32)
    (local $t108 i32)
    (local $t109 i32)
    (local $t110 i32)
    (local $t111 i32)
    (local $t112 i32)
    (local $t113 i32)
    (local $t114 i32)
    (local $t115 i32)
    (local $t116 i32)
    (local $t117 i32)
    (local $t118 i32)
    (local $t119 i32)
    (local $t120 i32)
    (local $t121 i32)
    (local $t122 i32)
    (local $t123 i32)
    (local $t124 i32)
    (local $t125 i32)
    (local $t126 i32)
    (local $t127 i32)
    (local $t128 i32)
    (local $t129 i32)
    (local $t130 i32)
    (local $t131 i32)
    (local $t132 i32)
    (local $t133 i32)
    (local $t134 i32)
    (local $t135 i32)
    (local $t136 i32)
    (local $t137 i32)
    (local $t138 i32)
    (local $t139 i32)
    (local $t140 i32)
    (local $t141 i32)
    (local $t142 i32)
    (local $t143 i32)
    (local $t144 i32)
    (local $t145 i32)
    (local $t146 i32)
    (local $t147 i32)
    (local $t148 i32)
    (local $t149 i32)
    (local $t150 i32)
    (local $t151 i32)
    (local $t152 i32)
    (local $t153 i32)
    (local $t154 i32)
    (local $t155 i32)
    (local $t156 i32)
    (local $t157 i32)
    (local $t158 i32)
    (local $t159 i32)
    (local $t160 i32)
    (local $t161 i32)
    (local $t162 i32)
    (local $t163 i32)
    (local $t164 i32)
    (local $t165 i32)
    (local $t166 i32)
    (local $t167 i32)
    (local $t168 i32)
    (local $t169 i32)
    (local $t170 i32)
    (local $t171 i32)
    (local $t172 i32)
    (local $t173 i32)
    (local $t174 i32)
    (local $t175 i32)
    (local $t176 i32)
    (local $t177 i32)
    (local $t178 i32)
    (local $t179 i32)
    (local $t180 i32)
    (local $t181 i32)
    (local $t182 i32)
    (local $t183 i32)
    (local $t184 i32)
    (local $t185 i32)
    (local $t186 i32)
    (local $t187 i32)
    (local $t188 i32)
    (local $t189 i32)
    (local $t190 i32)
    (local $t191 i32)
    (local $t192 i32)
    (local $t193 i32)
    (local $t194 i32)
    (local $t195 i32)
    (local $t196 i32)
    (local $t197 i32)
    (local $t198 i32)
    (local $t199 i32)
    (local $t200 i32)
    (local $t201 i32)
    (local $t202 i32)
    (local $t203 i32)
    (local $t204 i32)
    (local $t205 i32)
    (local $t206 i32)
    (local $t207 i32)
    (local $t208 i32)
    (local $t209 i32)
    (local $t210 i32)
    (local $t211 i32)
    (local $t212 i32)
    (local $t213 i32)
    (local $t214 i32)
    (local $t215 i32)
    (local $t216 i32)
    (local $t217 i32)
    (local $t218 i32)
    (local $t219 i32)
    (local $t220 i32)
    (local $t221 i32)
    (local $t222 i32)
    (local $t223 i32)
    (local $t224 i32)
    (local $t225 i32)
    (local $t226 i32)
    (local $t227 i32)
    (local $t228 i32)
    (local $t229 i32)
    (local $t230 i32)
    (local $t231 i32)
    (local $t232 i32)
    (local $t233 i32)
    (local $t234 i32)
    (local $t235 i32)
    (local $t236 i32)
    (local $t237 i32)
    (local $t238 i32)
    (local $t239 i32)
    (local $t240 i32)
    (local $t241 i32)
    (local $t242 i32)
    (local $t243 i32)
    (local $t244 i32)
    (local $t245 i32)
    (local $t246 i32)
    (local $t247 i32)
    (local $t248 i32)
    (local $t249 i32)
    (local $t250 i32)
    (local $t251 i32)
    (local $t252 i32)
    (local $t253 i32)
    (local $t254 i32)
    (local $t255 i32)
    (local $t256 i32)
    (local $t257 i32)
    (local $t258 i32)
    (local $t259 i32)
    (local $t260 i32)
    (local $t261 i32)
    (local $t262 i32)
    (local $t263 i32)
    (local $t264 i32)
    (local $t265 i32)
    (local $t266 i32)
    (local $t267 i32)
    (local $t268 i32)
    (local $t269 i32)
    (local $t270 i32)
    (local $t271 i32)
    (local $t272 i32)
    (local $t273 i32)
    (local $t274 i32)
    (local $t275 i32)
    (local $t276 i32)
    (local $t277 i32)
    (local $t278 i32)
    (local $t279 i32)
    (local $t280 i32)
    (local $t281 i32)
    (local $t282 i32)
    (local $t283 i32)
    (local $t284 i32)
    (local $t285 i32)
    (local $t286 i32)
    (local $t287 i32)
    (local $t288 i32)
    (local $t289 i32)
    (local $t290 i32)
    (local $t291 i32)
    (local $t292 i32)
    (local $t293 i32)
    (local $t294 i32)
    (local $t295 i32)
    (local $t296 i32)
    (local $t297 i32)
    (local $t298 i32)
    (local $t299 i32)
    (local $t300 i32)
    (local $t301 i32)
    (local $t302 i32)
    (local $t303 i32)
    (local $t304 i32)
    (local $t305 i32)
    (local $t306 i32)
    (local $t307 i32)
    (local $t308 i32)
    (local $t309 i32)
    (local $t310 i32)
    (local $t311 i32)
    (local $t312 i32)
    (local $t313 i32)
    (local $t314 i32)
    (local $t315 i32)
    (local $t316 i32)
    (local $t317 i32)
    (local $t318 i32)
    (local $t319 i32)
    (local $t320 i32)
    (local $t321 i32)
    (local $t322 i32)
    (local $t323 i32)
    (local $t324 i32)
    (local $t325 i32)
    (local $t326 i32)
    (local $t327 i32)
    (local $t328 i32)
    (local $t329 i32)
    (local $t330 i32)
    (local $t331 i32)
    (local $t332 i32)
    (local $t333 i32)
    (local $t334 i32)
    (local $t335 i32)
    (local $t336 i32)
    (local $t337 i32)
    (local $t338 i32)
    (local $t339 i32)
    (local $t340 i32)
    i32.const 146
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $data
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb205
        (block $bb204
        (block $bb203
        (block $bb202
        (block $bb201
        (block $bb200
        (block $bb199
        (block $bb198
        (block $bb197
        (block $bb196
        (block $bb195
        (block $bb194
        (block $bb193
        (block $bb192
        (block $bb191
        (block $bb190
        (block $bb189
        (block $bb188
        (block $bb187
        (block $bb186
        (block $bb185
        (block $bb184
        (block $bb183
        (block $bb182
        (block $bb181
        (block $bb180
        (block $bb179
        (block $bb178
        (block $bb177
        (block $bb176
        (block $bb175
        (block $bb174
        (block $bb173
        (block $bb172
        (block $bb171
        (block $bb170
        (block $bb169
        (block $bb168
        (block $bb167
        (block $bb166
        (block $bb165
        (block $bb164
        (block $bb163
        (block $bb162
        (block $bb161
        (block $bb160
        (block $bb159
        (block $bb158
        (block $bb157
        (block $bb156
        (block $bb155
        (block $bb154
        (block $bb153
        (block $bb152
        (block $bb151
        (block $bb150
        (block $bb149
        (block $bb148
        (block $bb147
        (block $bb146
        (block $bb145
        (block $bb144
        (block $bb143
        (block $bb142
        (block $bb141
        (block $bb140
        (block $bb139
        (block $bb138
        (block $bb137
        (block $bb136
        (block $bb135
        (block $bb134
        (block $bb133
        (block $bb132
        (block $bb131
        (block $bb130
        (block $bb129
        (block $bb128
        (block $bb127
        (block $bb126
        (block $bb125
        (block $bb124
        (block $bb123
        (block $bb122
        (block $bb121
        (block $bb120
        (block $bb119
        (block $bb118
        (block $bb117
        (block $bb116
        (block $bb115
        (block $bb114
        (block $bb113
        (block $bb112
        (block $bb111
        (block $bb110
        (block $bb109
        (block $bb108
        (block $bb107
        (block $bb106
        (block $bb105
        (block $bb104
        (block $bb103
        (block $bb102
        (block $bb101
        (block $bb100
        (block $bb99
        (block $bb98
        (block $bb97
        (block $bb96
        (block $bb95
        (block $bb94
        (block $bb93
        (block $bb92
        (block $bb91
        (block $bb90
        (block $bb89
        (block $bb88
        (block $bb87
        (block $bb86
        (block $bb85
        (block $bb84
        (block $bb83
        (block $bb82
        (block $bb81
        (block $bb80
        (block $bb79
        (block $bb78
        (block $bb77
        (block $bb76
        (block $bb75
        (block $bb74
        (block $bb73
        (block $bb72
        (block $bb71
        (block $bb70
        (block $bb69
        (block $bb68
        (block $bb67
        (block $bb66
        (block $bb65
        (block $bb64
        (block $bb63
        (block $bb62
        (block $bb61
        (block $bb60
        (block $bb59
        (block $bb58
        (block $bb57
        (block $bb56
        (block $bb55
        (block $bb54
        (block $bb53
        (block $bb52
        (block $bb51
        (block $bb50
        (block $bb49
        (block $bb48
        (block $bb47
        (block $bb46
        (block $bb45
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $bb45 $bb46 $bb47 $bb48 $bb49 $bb50 $bb51 $bb52 $bb53 $bb54 $bb55 $bb56 $bb57 $bb58 $bb59 $bb60 $bb61 $bb62 $bb63 $bb64 $bb65 $bb66 $bb67 $bb68 $bb69 $bb70 $bb71 $bb72 $bb73 $bb74 $bb75 $bb76 $bb77 $bb78 $bb79 $bb80 $bb81 $bb82 $bb83 $bb84 $bb85 $bb86 $bb87 $bb88 $bb89 $bb90 $bb91 $bb92 $bb93 $bb94 $bb95 $bb96 $bb97 $bb98 $bb99 $bb100 $bb101 $bb102 $bb103 $bb104 $bb105 $bb106 $bb107 $bb108 $bb109 $bb110 $bb111 $bb112 $bb113 $bb114 $bb115 $bb116 $bb117 $bb118 $bb119 $bb120 $bb121 $bb122 $bb123 $bb124 $bb125 $bb126 $bb127 $bb128 $bb129 $bb130 $bb131 $bb132 $bb133 $bb134 $bb135 $bb136 $bb137 $bb138 $bb139 $bb140 $bb141 $bb142 $bb143 $bb144 $bb145 $bb146 $bb147 $bb148 $bb149 $bb150 $bb151 $bb152 $bb153 $bb154 $bb155 $bb156 $bb157 $bb158 $bb159 $bb160 $bb161 $bb162 $bb163 $bb164 $bb165 $bb166 $bb167 $bb168 $bb169 $bb170 $bb171 $bb172 $bb173 $bb174 $bb175 $bb176 $bb177 $bb178 $bb179 $bb180 $bb181 $bb182 $bb183 $bb184 $bb185 $bb186 $bb187 $bb188 $bb189 $bb190 $bb191 $bb192 $bb193 $bb194 $bb195 $bb196 $bb197 $bb198 $bb199 $bb200 $bb201 $bb202 $bb203 $bb204 $bb205 $exit
        )
          local.get $data
          local.set $data__slot
          local.get $__jn_frame
          i32.const 1
          local.get $data__slot
          call $jn_gc_frame_set_root
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t1
          local.get $__jn_frame
          i32.const 37
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t1
          i32.const 0
          i32.eq
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
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t3
          local.get $__jn_frame
          i32.const 38
          local.get $t3
          call $jn_gc_frame_set_root
          local.get $t3
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t4
          local.get $t4
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t3
          call $jn_string_length
          local.set $t5
          call $jnative_exception_pending
          local.set $t6
          local.get $t6
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t5
          local.set $n
          local.get $n
          local.set $t7
          local.get $t7
          i32.const 20
          i32.lt_s
          local.set $t8
          local.get $t8
          local.set $sc.tmp1800890735
          local.get $t8
          if
            i32.const 10
            local.set $pc
          else
            i32.const 9
            local.set $pc
          end
          br $dispatch
        )
          local.get $n
          local.set $t9
          global.get $g_jn_static_SudokuCore_MAX_SAVE_CHARS
          local.set $t10
          local.get $t9
          local.get $t10
          i32.gt_s
          local.set $t11
          local.get $t11
          local.set $sc.tmp1800890735
          i32.const 10
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1800890735
          local.set $t12
          local.get $t12
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          i32.const 6
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 1
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 1
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 1
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t13
          local.get $t13
          i32.eqz
          if
            unreachable
          end
          local.get $t13
          i32.const 47
          i32.store
          local.get $t13
          i32.const 8
          i32.add
          i32.const 1
          i32.store
          local.get $__jn_frame
          i32.const 39
          local.get $t13
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t14
          local.get $t14
          if
            i32.const 13
            local.set $pc
          else
            i32.const 14
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t13
          local.set $gctmp.15
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.15
          call $jn_gc_frame_set_root
          local.get $gctmp.15
          local.set $t15
          local.get $__jn_frame
          i32.const 40
          local.get $t15
          call $jn_gc_frame_set_root
          local.get $t15
          local.set $pos
          local.get $__jn_frame
          i32.const 2
          local.get $pos
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t16
          local.get $__jn_frame
          i32.const 41
          local.get $t16
          call $jn_gc_frame_set_root
          local.get $t16
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t16
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t16
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t17
          local.get $t17
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t18
          local.get $__jn_frame
          i32.const 42
          local.get $t18
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t19
          local.get $__jn_frame
          i32.const 43
          local.get $t19
          call $jn_gc_frame_set_root
          i32.const 2048
          i32.const 5
          call $jn_str
          local.set $t20
          local.get $__jn_frame
          i32.const 44
          local.get $t20
          call $jn_gc_frame_set_root
          local.get $t20
          local.set $gctmp.21
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.21
          call $jn_gc_frame_set_root
          local.get $gctmp.21
          local.set $t21
          local.get $__jn_frame
          i32.const 45
          local.get $t21
          call $jn_gc_frame_set_root
          local.get $t18
          local.get $t19
          local.get $t21
          call $_JN_SudokuCore_matchLiteral_CString_Ai32_CString
          local.set $t22
          call $jnative_exception_pending
          local.set $t23
          local.get $t23
          if
            i32.const 17
            local.set $pc
          else
            i32.const 18
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t22
          i32.const 0
          i32.eq
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
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t25
          local.get $__jn_frame
          i32.const 46
          local.get $t25
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t26
          local.get $__jn_frame
          i32.const 47
          local.get $t26
          call $jn_gc_frame_set_root
          local.get $t25
          local.get $t26
          call $_JN_SudokuCore_parseIntLine_CString_Ai32
          local.set $t27
          call $jnative_exception_pending
          local.set $t28
          local.get $t28
          if
            i32.const 21
            local.set $pc
          else
            i32.const 22
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t27
          local.set $ver
          local.get $ver
          local.set $t29
          global.get $g_jn_static_SudokuCore_SAVE_VERSION
          local.set $t30
          local.get $t29
          local.get $t30
          i32.ne
          local.set $t31
          local.get $t31
          if
            i32.const 23
            local.set $pc
          else
            i32.const 24
            local.set $pc
          end
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t32
          local.get $__jn_frame
          i32.const 48
          local.get $t32
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t33
          local.get $__jn_frame
          i32.const 49
          local.get $t33
          call $jn_gc_frame_set_root
          local.get $t32
          local.get $t33
          call $_JN_SudokuCore_parseIntLine_CString_Ai32
          local.set $t34
          call $jnative_exception_pending
          local.set $t35
          local.get $t35
          if
            i32.const 25
            local.set $pc
          else
            i32.const 26
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t34
          local.set $pid
          local.get $data__slot
          local.set $t36
          local.get $__jn_frame
          i32.const 50
          local.get $t36
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t37
          local.get $__jn_frame
          i32.const 51
          local.get $t37
          call $jn_gc_frame_set_root
          local.get $t36
          local.get $t37
          call $_JN_SudokuCore_parseIntLine_CString_Ai32
          local.set $t38
          call $jnative_exception_pending
          local.set $t39
          local.get $t39
          if
            i32.const 27
            local.set $pc
          else
            i32.const 28
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t38
          local.set $diff
          local.get $data__slot
          local.set $t40
          local.get $__jn_frame
          i32.const 52
          local.get $t40
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t41
          local.get $__jn_frame
          i32.const 53
          local.get $t41
          call $jn_gc_frame_set_root
          local.get $t40
          local.get $t41
          call $_JN_SudokuCore_parseIntLine_CString_Ai32
          local.set $t42
          call $jnative_exception_pending
          local.set $t43
          local.get $t43
          if
            i32.const 29
            local.set $pc
          else
            i32.const 30
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t42
          local.set $st
          local.get $data__slot
          local.set $t44
          local.get $__jn_frame
          i32.const 54
          local.get $t44
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t45
          local.get $__jn_frame
          i32.const 55
          local.get $t45
          call $jn_gc_frame_set_root
          local.get $t44
          local.get $t45
          call $_JN_SudokuCore_parseIntLine_CString_Ai32
          local.set $t46
          call $jnative_exception_pending
          local.set $t47
          local.get $t47
          if
            i32.const 31
            local.set $pc
          else
            i32.const 32
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t46
          local.set $el
          local.get $data__slot
          local.set $t48
          local.get $__jn_frame
          i32.const 56
          local.get $t48
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t49
          local.get $__jn_frame
          i32.const 57
          local.get $t49
          call $jn_gc_frame_set_root
          local.get $t48
          local.get $t49
          call $_JN_SudokuCore_parseIntLine_CString_Ai32
          local.set $t50
          call $jnative_exception_pending
          local.set $t51
          local.get $t51
          if
            i32.const 33
            local.set $pc
          else
            i32.const 34
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t50
          local.set $nm
          local.get $data__slot
          local.set $t52
          local.get $__jn_frame
          i32.const 58
          local.get $t52
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t53
          local.get $__jn_frame
          i32.const 59
          local.get $t53
          call $jn_gc_frame_set_root
          local.get $t52
          local.get $t53
          call $_JN_SudokuCore_parseIntLine_CString_Ai32
          local.set $t54
          call $jnative_exception_pending
          local.set $t55
          local.get $t55
          if
            i32.const 35
            local.set $pc
          else
            i32.const 36
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t54
          local.set $sel
          local.get $data__slot
          local.set $t56
          local.get $__jn_frame
          i32.const 60
          local.get $t56
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t57
          local.get $__jn_frame
          i32.const 61
          local.get $t57
          call $jn_gc_frame_set_root
          local.get $t56
          local.get $t57
          call $_JN_SudokuCore_parseIntLine_CString_Ai32
          local.set $t58
          call $jnative_exception_pending
          local.set $t59
          local.get $t59
          if
            i32.const 37
            local.set $pc
          else
            i32.const 38
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t58
          local.set $hp
          local.get $data__slot
          local.set $t60
          local.get $__jn_frame
          i32.const 62
          local.get $t60
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t61
          local.get $__jn_frame
          i32.const 63
          local.get $t61
          call $jn_gc_frame_set_root
          local.get $t60
          local.get $t61
          call $_JN_SudokuCore_parseIntLine_CString_Ai32
          local.set $t62
          call $jnative_exception_pending
          local.set $t63
          local.get $t63
          if
            i32.const 39
            local.set $pc
          else
            i32.const 40
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t62
          local.set $hl
          global.get $g_jn_static_SudokuCore_lastError
          local.set $t64
          local.get $t64
          i32.const 0
          i32.ne
          local.set $t65
          local.get $t65
          if
            i32.const 41
            local.set $pc
          else
            i32.const 42
            local.set $pc
          end
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $hl
          local.set $t66
          local.get $t66
          i32.const 0
          i32.lt_s
          local.set $t67
          local.get $t67
          local.set $sc.tmp699780352
          local.get $t67
          if
            i32.const 48
            local.set $pc
          else
            i32.const 47
            local.set $pc
          end
          br $dispatch
        )
          local.get $hp
          local.set $t75
          local.get $hl
          local.set $t76
          local.get $t75
          local.get $t76
          i32.gt_s
          local.set $t77
          local.get $t77
          local.set $sc.tmp352359770
          i32.const 44
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp352359770
          local.set $t78
          local.get $t78
          if
            i32.const 49
            local.set $pc
          else
            i32.const 50
            local.set $pc
          end
          br $dispatch
        )
          local.get $hp
          local.set $t72
          local.get $t72
          i32.const 0
          i32.lt_s
          local.set $t73
          local.get $t73
          local.set $sc.tmp243745864
          i32.const 46
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp243745864
          local.set $t74
          local.get $t74
          local.set $sc.tmp352359770
          local.get $t74
          if
            i32.const 44
            local.set $pc
          else
            i32.const 43
            local.set $pc
          end
          br $dispatch
        )
          local.get $hl
          local.set $t68
          global.get $g_jn_static_SudokuCore_MAX_HISTORY
          local.set $t69
          local.get $t68
          local.get $t69
          i32.gt_s
          local.set $t70
          local.get $t70
          local.set $sc.tmp699780352
          i32.const 48
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp699780352
          local.set $t71
          local.get $t71
          local.set $sc.tmp243745864
          local.get $t71
          if
            i32.const 46
            local.set $pc
          else
            i32.const 45
            local.set $pc
          end
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $sel
          local.set $t79
          local.get $t79
          i32.const 0
          i32.lt_s
          local.set $t80
          local.get $t80
          local.set $sc.tmp1166151249
          local.get $t80
          if
            i32.const 56
            local.set $pc
          else
            i32.const 55
            local.set $pc
          end
          br $dispatch
        )
          local.get $diff
          local.set $t87
          local.get $t87
          i32.const 2
          i32.gt_s
          local.set $t88
          local.get $t88
          local.set $sc.tmp1613255205
          i32.const 52
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1613255205
          local.set $t89
          local.get $t89
          if
            i32.const 57
            local.set $pc
          else
            i32.const 58
            local.set $pc
          end
          br $dispatch
        )
          local.get $diff
          local.set $t84
          local.get $t84
          i32.const 0
          i32.lt_s
          local.set $t85
          local.get $t85
          local.set $sc.tmp1897115967
          i32.const 54
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1897115967
          local.set $t86
          local.get $t86
          local.set $sc.tmp1613255205
          local.get $t86
          if
            i32.const 52
            local.set $pc
          else
            i32.const 51
            local.set $pc
          end
          br $dispatch
        )
          local.get $sel
          local.set $t81
          local.get $t81
          i32.const 80
          i32.gt_s
          local.set $t82
          local.get $t82
          local.set $sc.tmp1166151249
          i32.const 56
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1166151249
          local.set $t83
          local.get $t83
          local.set $sc.tmp1897115967
          local.get $t83
          if
            i32.const 54
            local.set $pc
          else
            i32.const 53
            local.set $pc
          end
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $st
          local.set $t90
          global.get $g_jn_static_SudokuCore_ST_PLAYING
          local.set $t91
          local.get $t90
          local.get $t91
          i32.ne
          local.set $t92
          local.get $t92
          local.set $sc.tmp1615056168
          local.get $t92
          if
            i32.const 61
            local.set $pc
          else
            i32.const 62
            local.set $pc
          end
          br $dispatch
        )
          local.get $st
          local.set $t97
          global.get $g_jn_static_SudokuCore_ST_WON
          local.set $t98
          local.get $t97
          local.get $t98
          i32.ne
          local.set $t99
          local.get $t99
          local.set $sc.tmp1121453612
          i32.const 60
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1121453612
          local.set $t100
          local.get $t100
          if
            i32.const 63
            local.set $pc
          else
            i32.const 64
            local.set $pc
          end
          br $dispatch
        )
          local.get $st
          local.set $t93
          global.get $g_jn_static_SudokuCore_ST_PAUSED
          local.set $t94
          local.get $t93
          local.get $t94
          i32.ne
          local.set $t95
          local.get $t95
          local.set $sc.tmp1615056168
          i32.const 62
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1615056168
          local.set $t96
          local.get $t96
          local.set $sc.tmp1121453612
          local.get $t96
          if
            i32.const 59
            local.set $pc
          else
            i32.const 60
            local.set $pc
          end
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $el
          local.set $t101
          local.get $t101
          i32.const 0
          i32.lt_s
          local.set $t102
          local.get $t102
          if
            i32.const 65
            local.set $pc
          else
            i32.const 66
            local.set $pc
          end
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 81
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 81
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 81
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t103
          local.get $t103
          i32.eqz
          if
            unreachable
          end
          local.get $t103
          i32.const 47
          i32.store
          local.get $t103
          i32.const 8
          i32.add
          i32.const 81
          i32.store
          local.get $__jn_frame
          i32.const 64
          local.get $t103
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t104
          local.get $t104
          if
            i32.const 67
            local.set $pc
          else
            i32.const 68
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t103
          local.set $gctmp.105
          local.get $__jn_frame
          i32.const 6
          local.get $gctmp.105
          call $jn_gc_frame_set_root
          local.get $gctmp.105
          local.set $t105
          local.get $__jn_frame
          i32.const 65
          local.get $t105
          call $jn_gc_frame_set_root
          local.get $t105
          local.set $gTmp
          local.get $__jn_frame
          i32.const 5
          local.get $gTmp
          call $jn_gc_frame_set_root
          i32.const 81
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 81
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 81
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t106
          local.get $t106
          i32.eqz
          if
            unreachable
          end
          local.get $t106
          i32.const 47
          i32.store
          local.get $t106
          i32.const 8
          i32.add
          i32.const 81
          i32.store
          local.get $__jn_frame
          i32.const 66
          local.get $t106
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t107
          local.get $t107
          if
            i32.const 69
            local.set $pc
          else
            i32.const 70
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t106
          local.set $gctmp.108
          local.get $__jn_frame
          i32.const 8
          local.get $gctmp.108
          call $jn_gc_frame_set_root
          local.get $gctmp.108
          local.set $t108
          local.get $__jn_frame
          i32.const 67
          local.get $t108
          call $jn_gc_frame_set_root
          local.get $t108
          local.set $vTmp
          local.get $__jn_frame
          i32.const 7
          local.get $vTmp
          call $jn_gc_frame_set_root
          i32.const 81
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 81
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 81
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t109
          local.get $t109
          i32.eqz
          if
            unreachable
          end
          local.get $t109
          i32.const 47
          i32.store
          local.get $t109
          i32.const 8
          i32.add
          i32.const 81
          i32.store
          local.get $__jn_frame
          i32.const 68
          local.get $t109
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t110
          local.get $t110
          if
            i32.const 71
            local.set $pc
          else
            i32.const 72
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t109
          local.set $gctmp.111
          local.get $__jn_frame
          i32.const 10
          local.get $gctmp.111
          call $jn_gc_frame_set_root
          local.get $gctmp.111
          local.set $t111
          local.get $__jn_frame
          i32.const 69
          local.get $t111
          call $jn_gc_frame_set_root
          local.get $t111
          local.set $nTmp
          local.get $__jn_frame
          i32.const 9
          local.get $nTmp
          call $jn_gc_frame_set_root
          local.get $data__slot
          local.set $t112
          local.get $__jn_frame
          i32.const 70
          local.get $t112
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t113
          local.get $__jn_frame
          i32.const 71
          local.get $t113
          call $jn_gc_frame_set_root
          local.get $gTmp
          local.set $t114
          local.get $__jn_frame
          i32.const 72
          local.get $t114
          call $jn_gc_frame_set_root
          local.get $t112
          local.get $t113
          local.get $t114
          call $_JN_SudokuCore_parseDigitLine_CString_Ai32_Ai32
          local.set $t115
          call $jnative_exception_pending
          local.set $t116
          local.get $t116
          if
            i32.const 73
            local.set $pc
          else
            i32.const 74
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t115
          i32.const 0
          i32.eq
          local.set $t117
          local.get $t117
          if
            i32.const 75
            local.set $pc
          else
            i32.const 76
            local.set $pc
          end
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t118
          local.get $__jn_frame
          i32.const 73
          local.get $t118
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t119
          local.get $__jn_frame
          i32.const 74
          local.get $t119
          call $jn_gc_frame_set_root
          local.get $vTmp
          local.set $t120
          local.get $__jn_frame
          i32.const 75
          local.get $t120
          call $jn_gc_frame_set_root
          local.get $t118
          local.get $t119
          local.get $t120
          call $_JN_SudokuCore_parseDigitLine_CString_Ai32_Ai32
          local.set $t121
          call $jnative_exception_pending
          local.set $t122
          local.get $t122
          if
            i32.const 77
            local.set $pc
          else
            i32.const 78
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t121
          i32.const 0
          i32.eq
          local.set $t123
          local.get $t123
          if
            i32.const 79
            local.set $pc
          else
            i32.const 80
            local.set $pc
          end
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t124
          local.get $__jn_frame
          i32.const 76
          local.get $t124
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t125
          local.get $__jn_frame
          i32.const 77
          local.get $t125
          call $jn_gc_frame_set_root
          local.get $nTmp
          local.set $t126
          local.get $__jn_frame
          i32.const 78
          local.get $t126
          call $jn_gc_frame_set_root
          local.get $t124
          local.get $t125
          local.get $t126
          call $_JN_SudokuCore_parseNotesLine_CString_Ai32_Ai32
          local.set $t127
          call $jnative_exception_pending
          local.set $t128
          local.get $t128
          if
            i32.const 81
            local.set $pc
          else
            i32.const 82
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t127
          i32.const 0
          i32.eq
          local.set $t129
          local.get $t129
          if
            i32.const 83
            local.set $pc
          else
            i32.const 84
            local.set $pc
          end
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $i
          i32.const 85
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t130
          local.get $t130
          i32.const 81
          i32.lt_s
          local.set $t131
          local.get $t131
          if
            i32.const 86
            local.set $pc
          else
            i32.const 87
            local.set $pc
          end
          br $dispatch
        )
          local.get $gTmp
          local.set $t132
          local.get $__jn_frame
          i32.const 79
          local.get $t132
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t133
          local.get $t132
          i32.eqz
          if
            unreachable
          end
          local.get $t133
          i32.const 0
          i32.lt_s
          local.get $t133
          local.get $t132
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t132
          i32.const 16
          i32.add
          local.get $t133
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t134
          call $jnative_exception_pending
          local.set $t135
          local.get $t135
          if
            i32.const 90
            local.set $pc
          else
            i32.const 91
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_MAX_HISTORY
          local.set $t178
          local.get $t178
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          local.get $t178
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          local.get $t178
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t179
          local.get $t179
          i32.eqz
          if
            unreachable
          end
          local.get $t179
          i32.const 47
          i32.store
          local.get $t179
          i32.const 8
          i32.add
          local.get $t178
          i32.store
          local.get $__jn_frame
          i32.const 87
          local.get $t179
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t180
          local.get $t180
          if
            i32.const 120
            local.set $pc
          else
            i32.const 121
            local.set $pc
          end
          br $dispatch
        )
          local.get $gTmp
          local.set $t137
          local.get $__jn_frame
          i32.const 80
          local.get $t137
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t138
          local.get $t137
          i32.eqz
          if
            unreachable
          end
          local.get $t138
          i32.const 0
          i32.lt_s
          local.get $t138
          local.get $t137
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t137
          i32.const 16
          i32.add
          local.get $t138
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t139
          call $jnative_exception_pending
          local.set $t140
          local.get $t140
          if
            i32.const 92
            local.set $pc
          else
            i32.const 93
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp213193302
          local.set $t142
          local.get $t142
          if
            i32.const 94
            local.set $pc
          else
            i32.const 95
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t134
          i32.const 0
          i32.ne
          local.set $t136
          local.get $t136
          local.set $sc.tmp213193302
          local.get $t136
          if
            i32.const 88
            local.set $pc
          else
            i32.const 89
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t139
          i32.const 1
          i32.ne
          local.set $t141
          local.get $t141
          local.set $sc.tmp213193302
          i32.const 89
          local.set $pc
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $vTmp
          local.set $t143
          local.get $__jn_frame
          i32.const 81
          local.get $t143
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t144
          local.get $t143
          i32.eqz
          if
            unreachable
          end
          local.get $t144
          i32.const 0
          i32.lt_s
          local.get $t144
          local.get $t143
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t143
          i32.const 16
          i32.add
          local.get $t144
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t145
          call $jnative_exception_pending
          local.set $t146
          local.get $t146
          if
            i32.const 98
            local.set $pc
          else
            i32.const 99
            local.set $pc
          end
          br $dispatch
        )
          local.get $vTmp
          local.set $t148
          local.get $__jn_frame
          i32.const 82
          local.get $t148
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t149
          local.get $t148
          i32.eqz
          if
            unreachable
          end
          local.get $t149
          i32.const 0
          i32.lt_s
          local.get $t149
          local.get $t148
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t148
          i32.const 16
          i32.add
          local.get $t149
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t150
          call $jnative_exception_pending
          local.set $t151
          local.get $t151
          if
            i32.const 100
            local.set $pc
          else
            i32.const 101
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp1502635287
          local.set $t153
          local.get $t153
          if
            i32.const 102
            local.set $pc
          else
            i32.const 103
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t145
          i32.const 0
          i32.lt_s
          local.set $t147
          local.get $t147
          local.set $sc.tmp1502635287
          local.get $t147
          if
            i32.const 97
            local.set $pc
          else
            i32.const 96
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t150
          i32.const 9
          i32.gt_s
          local.set $t152
          local.get $t152
          local.set $sc.tmp1502635287
          i32.const 97
          local.set $pc
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $nTmp
          local.set $t154
          local.get $__jn_frame
          i32.const 83
          local.get $t154
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t155
          local.get $t154
          i32.eqz
          if
            unreachable
          end
          local.get $t155
          i32.const 0
          i32.lt_s
          local.get $t155
          local.get $t154
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t154
          i32.const 16
          i32.add
          local.get $t155
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t156
          call $jnative_exception_pending
          local.set $t157
          local.get $t157
          if
            i32.const 106
            local.set $pc
          else
            i32.const 107
            local.set $pc
          end
          br $dispatch
        )
          local.get $nTmp
          local.set $t159
          local.get $__jn_frame
          i32.const 84
          local.get $t159
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t160
          local.get $t159
          i32.eqz
          if
            unreachable
          end
          local.get $t160
          i32.const 0
          i32.lt_s
          local.get $t160
          local.get $t159
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t159
          i32.const 16
          i32.add
          local.get $t160
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t161
          call $jnative_exception_pending
          local.set $t162
          local.get $t162
          if
            i32.const 108
            local.set $pc
          else
            i32.const 109
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp1543237999
          local.set $t164
          local.get $t164
          if
            i32.const 110
            local.set $pc
          else
            i32.const 111
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t156
          i32.const 0
          i32.lt_s
          local.set $t158
          local.get $t158
          local.set $sc.tmp1543237999
          local.get $t158
          if
            i32.const 105
            local.set $pc
          else
            i32.const 104
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t161
          i32.const 511
          i32.gt_s
          local.set $t163
          local.get $t163
          local.set $sc.tmp1543237999
          i32.const 105
          local.set $pc
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $gTmp
          local.set $t165
          local.get $__jn_frame
          i32.const 85
          local.get $t165
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t166
          local.get $t165
          i32.eqz
          if
            unreachable
          end
          local.get $t166
          i32.const 0
          i32.lt_s
          local.get $t166
          local.get $t165
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t165
          i32.const 16
          i32.add
          local.get $t166
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t167
          call $jnative_exception_pending
          local.set $t168
          local.get $t168
          if
            i32.const 114
            local.set $pc
          else
            i32.const 115
            local.set $pc
          end
          br $dispatch
        )
          local.get $vTmp
          local.set $t170
          local.get $__jn_frame
          i32.const 86
          local.get $t170
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t171
          local.get $t170
          i32.eqz
          if
            unreachable
          end
          local.get $t171
          i32.const 0
          i32.lt_s
          local.get $t171
          local.get $t170
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t170
          i32.const 16
          i32.add
          local.get $t171
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t172
          call $jnative_exception_pending
          local.set $t173
          local.get $t173
          if
            i32.const 116
            local.set $pc
          else
            i32.const 117
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp632249781
          local.set $t175
          local.get $t175
          if
            i32.const 118
            local.set $pc
          else
            i32.const 119
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t167
          i32.const 0
          i32.ne
          local.set $t169
          local.get $t169
          local.set $sc.tmp632249781
          local.get $t169
          if
            i32.const 112
            local.set $pc
          else
            i32.const 113
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t172
          i32.const 0
          i32.eq
          local.set $t174
          local.get $t174
          local.set $sc.tmp632249781
          i32.const 113
          local.set $pc
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t176
          local.get $t176
          i32.const 1
          i32.add
          local.set $t177
          local.get $t177
          local.set $i
          call $jn_gc_safepoint
          i32.const 85
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t179
          local.set $gctmp.181
          local.get $__jn_frame
          i32.const 12
          local.get $gctmp.181
          call $jn_gc_frame_set_root
          local.get $gctmp.181
          local.set $t181
          local.get $__jn_frame
          i32.const 88
          local.get $t181
          call $jn_gc_frame_set_root
          local.get $t181
          local.set $hc
          local.get $__jn_frame
          i32.const 11
          local.get $hc
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_MAX_HISTORY
          local.set $t182
          local.get $t182
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          local.get $t182
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          local.get $t182
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t183
          local.get $t183
          i32.eqz
          if
            unreachable
          end
          local.get $t183
          i32.const 47
          i32.store
          local.get $t183
          i32.const 8
          i32.add
          local.get $t182
          i32.store
          local.get $__jn_frame
          i32.const 89
          local.get $t183
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t184
          local.get $t184
          if
            i32.const 122
            local.set $pc
          else
            i32.const 123
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t183
          local.set $gctmp.185
          local.get $__jn_frame
          i32.const 14
          local.get $gctmp.185
          call $jn_gc_frame_set_root
          local.get $gctmp.185
          local.set $t185
          local.get $__jn_frame
          i32.const 90
          local.get $t185
          call $jn_gc_frame_set_root
          local.get $t185
          local.set $hpv
          local.get $__jn_frame
          i32.const 13
          local.get $hpv
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_MAX_HISTORY
          local.set $t186
          local.get $t186
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          local.get $t186
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          local.get $t186
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t187
          local.get $t187
          i32.eqz
          if
            unreachable
          end
          local.get $t187
          i32.const 47
          i32.store
          local.get $t187
          i32.const 8
          i32.add
          local.get $t186
          i32.store
          local.get $__jn_frame
          i32.const 91
          local.get $t187
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t188
          local.get $t188
          if
            i32.const 124
            local.set $pc
          else
            i32.const 125
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t187
          local.set $gctmp.189
          local.get $__jn_frame
          i32.const 16
          local.get $gctmp.189
          call $jn_gc_frame_set_root
          local.get $gctmp.189
          local.set $t189
          local.get $__jn_frame
          i32.const 92
          local.get $t189
          call $jn_gc_frame_set_root
          local.get $t189
          local.set $hnv
          local.get $__jn_frame
          i32.const 15
          local.get $hnv
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_MAX_HISTORY
          local.set $t190
          local.get $t190
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          local.get $t190
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          local.get $t190
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t191
          local.get $t191
          i32.eqz
          if
            unreachable
          end
          local.get $t191
          i32.const 47
          i32.store
          local.get $t191
          i32.const 8
          i32.add
          local.get $t190
          i32.store
          local.get $__jn_frame
          i32.const 93
          local.get $t191
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t192
          local.get $t192
          if
            i32.const 126
            local.set $pc
          else
            i32.const 127
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t191
          local.set $gctmp.193
          local.get $__jn_frame
          i32.const 18
          local.get $gctmp.193
          call $jn_gc_frame_set_root
          local.get $gctmp.193
          local.set $t193
          local.get $__jn_frame
          i32.const 94
          local.get $t193
          call $jn_gc_frame_set_root
          local.get $t193
          local.set $hpn
          local.get $__jn_frame
          i32.const 17
          local.get $hpn
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_MAX_HISTORY
          local.set $t194
          local.get $t194
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          local.get $t194
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          local.get $t194
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t195
          local.get $t195
          i32.eqz
          if
            unreachable
          end
          local.get $t195
          i32.const 47
          i32.store
          local.get $t195
          i32.const 8
          i32.add
          local.get $t194
          i32.store
          local.get $__jn_frame
          i32.const 95
          local.get $t195
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t196
          local.get $t196
          if
            i32.const 128
            local.set $pc
          else
            i32.const 129
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t195
          local.set $gctmp.197
          local.get $__jn_frame
          i32.const 20
          local.get $gctmp.197
          call $jn_gc_frame_set_root
          local.get $gctmp.197
          local.set $t197
          local.get $__jn_frame
          i32.const 96
          local.get $t197
          call $jn_gc_frame_set_root
          local.get $t197
          local.set $hnn
          local.get $__jn_frame
          i32.const 19
          local.get $hnn
          call $jn_gc_frame_set_root
          local.get $data__slot
          local.set $t198
          local.get $__jn_frame
          i32.const 97
          local.get $t198
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $t199
          local.get $__jn_frame
          i32.const 98
          local.get $t199
          call $jn_gc_frame_set_root
          local.get $hl
          local.set $t200
          local.get $hc
          local.set $t201
          local.get $__jn_frame
          i32.const 99
          local.get $t201
          call $jn_gc_frame_set_root
          local.get $hpv
          local.set $t202
          local.get $__jn_frame
          i32.const 100
          local.get $t202
          call $jn_gc_frame_set_root
          local.get $hnv
          local.set $t203
          local.get $__jn_frame
          i32.const 101
          local.get $t203
          call $jn_gc_frame_set_root
          local.get $hpn
          local.set $t204
          local.get $__jn_frame
          i32.const 102
          local.get $t204
          call $jn_gc_frame_set_root
          local.get $hnn
          local.set $t205
          local.get $__jn_frame
          i32.const 103
          local.get $t205
          call $jn_gc_frame_set_root
          local.get $t198
          local.get $t199
          local.get $t200
          local.get $t201
          local.get $t202
          local.get $t203
          local.get $t204
          local.get $t205
          call $_JN_SudokuCore_parseHistoryLine_CString_Ai32_i32_Ai32_Ai32_Ai32_Ai32_Ai32
          local.set $t206
          call $jnative_exception_pending
          local.set $t207
          local.get $t207
          if
            i32.const 130
            local.set $pc
          else
            i32.const 131
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t206
          i32.const 0
          i32.eq
          local.set $t208
          local.get $t208
          if
            i32.const 132
            local.set $pc
          else
            i32.const 133
            local.set $pc
          end
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $i
          i32.const 134
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t209
          local.get $t209
          i32.const 81
          i32.lt_s
          local.set $t210
          local.get $t210
          if
            i32.const 135
            local.set $pc
          else
            i32.const 136
            local.set $pc
          end
          br $dispatch
        )
          local.get $gTmp
          local.set $t211
          local.get $__jn_frame
          i32.const 104
          local.get $t211
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t212
          local.get $t211
          i32.eqz
          if
            unreachable
          end
          local.get $t212
          i32.const 0
          i32.lt_s
          local.get $t212
          local.get $t211
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t211
          i32.const 16
          i32.add
          local.get $t212
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t213
          call $jnative_exception_pending
          local.set $t214
          local.get $t214
          if
            i32.const 137
            local.set $pc
          else
            i32.const 138
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t230
          local.get $__jn_frame
          i32.const 110
          local.get $t230
          call $jn_gc_frame_set_root
          local.get $t230
          local.set $gctmp.231
          local.get $__jn_frame
          i32.const 23
          local.get $gctmp.231
          call $jn_gc_frame_set_root
          local.get $gctmp.231
          local.set $t231
          local.get $__jn_frame
          i32.const 111
          local.get $t231
          call $jn_gc_frame_set_root
          local.get $t231
          i32.const 2
          call $_JN_SudokuCore_countSolutionsBoard_Ai32_i32
          local.set $t232
          call $jnative_exception_pending
          local.set $t233
          local.get $t233
          if
            i32.const 148
            local.set $pc
          else
            i32.const 149
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t213
          i32.const 0
          i32.ne
          local.set $t215
          local.get $t215
          if
            i32.const 139
            local.set $pc
          else
            i32.const 141
            local.set $pc
          end
          br $dispatch
        )
          local.get $vTmp
          local.set $t216
          local.get $__jn_frame
          i32.const 105
          local.get $t216
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t217
          local.get $t216
          i32.eqz
          if
            unreachable
          end
          local.get $t217
          i32.const 0
          i32.lt_s
          local.get $t217
          local.get $t216
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t216
          i32.const 16
          i32.add
          local.get $t217
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t218
          call $jnative_exception_pending
          local.set $t219
          local.get $t219
          if
            i32.const 142
            local.set $pc
          else
            i32.const 143
            local.set $pc
          end
          br $dispatch
        )
          local.get $i
          local.set $t228
          local.get $t228
          i32.const 1
          i32.add
          local.set $t229
          local.get $t229
          local.set $i
          call $jn_gc_safepoint
          i32.const 134
          local.set $pc
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t224
          local.get $__jn_frame
          i32.const 108
          local.get $t224
          call $jn_gc_frame_set_root
          local.get $t224
          local.set $gctmp.225
          local.get $__jn_frame
          i32.const 22
          local.get $gctmp.225
          call $jn_gc_frame_set_root
          local.get $gctmp.225
          local.set $t225
          local.get $__jn_frame
          i32.const 109
          local.get $t225
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t226
          local.get $t225
          i32.eqz
          if
            unreachable
          end
          local.get $t226
          i32.const 0
          i32.lt_s
          local.get $t226
          local.get $t225
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t225
          i32.const 16
          i32.add
          local.get $t226
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t227
          local.get $t227
          if
            i32.const 146
            local.set $pc
          else
            i32.const 147
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t220
          local.get $__jn_frame
          i32.const 106
          local.get $t220
          call $jn_gc_frame_set_root
          local.get $t220
          local.set $gctmp.221
          local.get $__jn_frame
          i32.const 21
          local.get $gctmp.221
          call $jn_gc_frame_set_root
          local.get $gctmp.221
          local.set $t221
          local.get $__jn_frame
          i32.const 107
          local.get $t221
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t222
          local.get $t221
          i32.eqz
          if
            unreachable
          end
          local.get $t222
          i32.const 0
          i32.lt_s
          local.get $t222
          local.get $t221
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t221
          i32.const 16
          i32.add
          local.get $t222
          i32.const 4
          i32.mul
          i32.add
          local.get $t218
          i32.store
          call $jnative_exception_pending
          local.set $t223
          local.get $t223
          if
            i32.const 144
            local.set $pc
          else
            i32.const 145
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 140
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 140
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t232
          local.set $sols
          local.get $sols
          local.set $t234
          local.get $t234
          i32.const 1
          i32.ne
          local.set $t235
          local.get $t235
          if
            i32.const 150
            local.set $pc
          else
            i32.const 151
            local.set $pc
          end
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t236
          local.get $__jn_frame
          i32.const 112
          local.get $t236
          call $jn_gc_frame_set_root
          local.get $t236
          local.set $gctmp.237
          local.get $__jn_frame
          i32.const 24
          local.get $gctmp.237
          call $jn_gc_frame_set_root
          local.get $gctmp.237
          local.set $t237
          local.get $__jn_frame
          i32.const 113
          local.get $t237
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_work2
          local.set $t238
          local.get $__jn_frame
          i32.const 114
          local.get $t238
          call $jn_gc_frame_set_root
          local.get $t238
          local.set $gctmp.239
          local.get $__jn_frame
          i32.const 25
          local.get $gctmp.239
          call $jn_gc_frame_set_root
          local.get $gctmp.239
          local.set $t239
          local.get $__jn_frame
          i32.const 115
          local.get $t239
          call $jn_gc_frame_set_root
          local.get $t237
          local.get $t239
          call $_JN_SudokuCore_copyBoard_Ai32_Ai32
          call $jnative_exception_pending
          local.set $t240
          local.get $t240
          if
            i32.const 152
            local.set $pc
          else
            i32.const 153
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work2
          local.set $t241
          local.get $__jn_frame
          i32.const 116
          local.get $t241
          call $jn_gc_frame_set_root
          local.get $t241
          local.set $gctmp.242
          local.get $__jn_frame
          i32.const 26
          local.get $gctmp.242
          call $jn_gc_frame_set_root
          local.get $gctmp.242
          local.set $t242
          local.get $__jn_frame
          i32.const 117
          local.get $t242
          call $jn_gc_frame_set_root
          local.get $t242
          call $_JN_SudokuCore_solveBoard_Ai32
          local.set $t243
          call $jnative_exception_pending
          local.set $t244
          local.get $t244
          if
            i32.const 154
            local.set $pc
          else
            i32.const 155
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t243
          i32.const 0
          i32.eq
          local.set $t245
          local.get $t245
          if
            i32.const 156
            local.set $pc
          else
            i32.const 157
            local.set $pc
          end
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $i
          i32.const 158
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t246
          local.get $t246
          i32.const 81
          i32.lt_s
          local.set $t247
          local.get $t247
          if
            i32.const 159
            local.set $pc
          else
            i32.const 160
            local.set $pc
          end
          br $dispatch
        )
          local.get $gTmp
          local.set $t248
          local.get $__jn_frame
          i32.const 118
          local.get $t248
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t249
          local.get $t248
          i32.eqz
          if
            unreachable
          end
          local.get $t249
          i32.const 0
          i32.lt_s
          local.get $t249
          local.get $t248
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t248
          i32.const 16
          i32.add
          local.get $t249
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t250
          call $jnative_exception_pending
          local.set $t251
          local.get $t251
          if
            i32.const 161
            local.set $pc
          else
            i32.const 162
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $i
          i32.const 177
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_given
          local.set $t252
          local.get $__jn_frame
          i32.const 119
          local.get $t252
          call $jn_gc_frame_set_root
          local.get $t252
          local.set $gctmp.253
          local.get $__jn_frame
          i32.const 27
          local.get $gctmp.253
          call $jn_gc_frame_set_root
          local.get $gctmp.253
          local.set $t253
          local.get $__jn_frame
          i32.const 120
          local.get $t253
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t254
          local.get $t253
          i32.eqz
          if
            unreachable
          end
          local.get $t254
          i32.const 0
          i32.lt_s
          local.get $t254
          local.get $t253
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t253
          i32.const 16
          i32.add
          local.get $t254
          i32.const 4
          i32.mul
          i32.add
          local.get $t250
          i32.store
          call $jnative_exception_pending
          local.set $t255
          local.get $t255
          if
            i32.const 163
            local.set $pc
          else
            i32.const 164
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $vTmp
          local.set $t256
          local.get $__jn_frame
          i32.const 121
          local.get $t256
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t257
          local.get $t256
          i32.eqz
          if
            unreachable
          end
          local.get $t257
          i32.const 0
          i32.lt_s
          local.get $t257
          local.get $t256
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t256
          i32.const 16
          i32.add
          local.get $t257
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t258
          call $jnative_exception_pending
          local.set $t259
          local.get $t259
          if
            i32.const 165
            local.set $pc
          else
            i32.const 166
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t260
          local.get $__jn_frame
          i32.const 122
          local.get $t260
          call $jn_gc_frame_set_root
          local.get $t260
          local.set $gctmp.261
          local.get $__jn_frame
          i32.const 28
          local.get $gctmp.261
          call $jn_gc_frame_set_root
          local.get $gctmp.261
          local.set $t261
          local.get $__jn_frame
          i32.const 123
          local.get $t261
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t262
          local.get $t261
          i32.eqz
          if
            unreachable
          end
          local.get $t262
          i32.const 0
          i32.lt_s
          local.get $t262
          local.get $t261
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t261
          i32.const 16
          i32.add
          local.get $t262
          i32.const 4
          i32.mul
          i32.add
          local.get $t258
          i32.store
          call $jnative_exception_pending
          local.set $t263
          local.get $t263
          if
            i32.const 167
            local.set $pc
          else
            i32.const 168
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $nTmp
          local.set $t264
          local.get $__jn_frame
          i32.const 124
          local.get $t264
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t265
          local.get $t264
          i32.eqz
          if
            unreachable
          end
          local.get $t265
          i32.const 0
          i32.lt_s
          local.get $t265
          local.get $t264
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t264
          i32.const 16
          i32.add
          local.get $t265
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t266
          call $jnative_exception_pending
          local.set $t267
          local.get $t267
          if
            i32.const 169
            local.set $pc
          else
            i32.const 170
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_notes
          local.set $t268
          local.get $__jn_frame
          i32.const 125
          local.get $t268
          call $jn_gc_frame_set_root
          local.get $t268
          local.set $gctmp.269
          local.get $__jn_frame
          i32.const 29
          local.get $gctmp.269
          call $jn_gc_frame_set_root
          local.get $gctmp.269
          local.set $t269
          local.get $__jn_frame
          i32.const 126
          local.get $t269
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t270
          local.get $t269
          i32.eqz
          if
            unreachable
          end
          local.get $t270
          i32.const 0
          i32.lt_s
          local.get $t270
          local.get $t269
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t269
          i32.const 16
          i32.add
          local.get $t270
          i32.const 4
          i32.mul
          i32.add
          local.get $t266
          i32.store
          call $jnative_exception_pending
          local.set $t271
          local.get $t271
          if
            i32.const 171
            local.set $pc
          else
            i32.const 172
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work2
          local.set $t272
          local.get $__jn_frame
          i32.const 127
          local.get $t272
          call $jn_gc_frame_set_root
          local.get $t272
          local.set $gctmp.273
          local.get $__jn_frame
          i32.const 30
          local.get $gctmp.273
          call $jn_gc_frame_set_root
          local.get $gctmp.273
          local.set $t273
          local.get $__jn_frame
          i32.const 128
          local.get $t273
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t274
          local.get $t273
          i32.eqz
          if
            unreachable
          end
          local.get $t274
          i32.const 0
          i32.lt_s
          local.get $t274
          local.get $t273
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t273
          i32.const 16
          i32.add
          local.get $t274
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t275
          call $jnative_exception_pending
          local.set $t276
          local.get $t276
          if
            i32.const 173
            local.set $pc
          else
            i32.const 174
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_solution
          local.set $t277
          local.get $__jn_frame
          i32.const 129
          local.get $t277
          call $jn_gc_frame_set_root
          local.get $t277
          local.set $gctmp.278
          local.get $__jn_frame
          i32.const 31
          local.get $gctmp.278
          call $jn_gc_frame_set_root
          local.get $gctmp.278
          local.set $t278
          local.get $__jn_frame
          i32.const 130
          local.get $t278
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t279
          local.get $t278
          i32.eqz
          if
            unreachable
          end
          local.get $t279
          i32.const 0
          i32.lt_s
          local.get $t279
          local.get $t278
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t278
          i32.const 16
          i32.add
          local.get $t279
          i32.const 4
          i32.mul
          i32.add
          local.get $t275
          i32.store
          call $jnative_exception_pending
          local.set $t280
          local.get $t280
          if
            i32.const 175
            local.set $pc
          else
            i32.const 176
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t281
          local.get $t281
          i32.const 1
          i32.add
          local.set $t282
          local.get $t282
          local.set $i
          call $jn_gc_safepoint
          i32.const 158
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t283
          local.get $hl
          local.set $t284
          local.get $t283
          local.get $t284
          i32.lt_s
          local.set $t285
          local.get $t285
          if
            i32.const 178
            local.set $pc
          else
            i32.const 179
            local.set $pc
          end
          br $dispatch
        )
          local.get $hc
          local.set $t286
          local.get $__jn_frame
          i32.const 131
          local.get $t286
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t287
          local.get $t286
          i32.eqz
          if
            unreachable
          end
          local.get $t287
          i32.const 0
          i32.lt_s
          local.get $t287
          local.get $t286
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t286
          i32.const 16
          i32.add
          local.get $t287
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t288
          call $jnative_exception_pending
          local.set $t289
          local.get $t289
          if
            i32.const 180
            local.set $pc
          else
            i32.const 181
            local.set $pc
          end
          br $dispatch
        )
          local.get $hl
          local.set $t328
          local.get $t328
          global.set $g_jn_static_SudokuCore_histLen
          local.get $hp
          local.set $t329
          local.get $t329
          global.set $g_jn_static_SudokuCore_histPos
          local.get $pid
          local.set $t330
          local.get $t330
          global.set $g_jn_static_SudokuCore_puzzleId
          local.get $diff
          local.set $t331
          local.get $t331
          global.set $g_jn_static_SudokuCore_difficulty
          local.get $st
          local.set $t332
          local.get $t332
          global.set $g_jn_static_SudokuCore_status
          local.get $el
          local.set $t333
          local.get $t333
          global.set $g_jn_static_SudokuCore_elapsedMs
          local.get $nm
          local.set $t334
          local.get $t334
          global.set $g_jn_static_SudokuCore_noteMode
          local.get $sel
          local.set $t335
          local.get $t335
          global.set $g_jn_static_SudokuCore_selected
          i32.const 0
          global.set $g_jn_static_SudokuCore_tickValid
          call $_JN_SudokuCore_recomputeConflicts
          call $jnative_exception_pending
          local.set $t336
          local.get $t336
          if
            i32.const 200
            local.set $pc
          else
            i32.const 201
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histCell
          local.set $t290
          local.get $__jn_frame
          i32.const 132
          local.get $t290
          call $jn_gc_frame_set_root
          local.get $t290
          local.set $gctmp.291
          local.get $__jn_frame
          i32.const 32
          local.get $gctmp.291
          call $jn_gc_frame_set_root
          local.get $gctmp.291
          local.set $t291
          local.get $__jn_frame
          i32.const 133
          local.get $t291
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t292
          local.get $t291
          i32.eqz
          if
            unreachable
          end
          local.get $t292
          i32.const 0
          i32.lt_s
          local.get $t292
          local.get $t291
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t291
          i32.const 16
          i32.add
          local.get $t292
          i32.const 4
          i32.mul
          i32.add
          local.get $t288
          i32.store
          call $jnative_exception_pending
          local.set $t293
          local.get $t293
          if
            i32.const 182
            local.set $pc
          else
            i32.const 183
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $hpv
          local.set $t294
          local.get $__jn_frame
          i32.const 134
          local.get $t294
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t295
          local.get $t294
          i32.eqz
          if
            unreachable
          end
          local.get $t295
          i32.const 0
          i32.lt_s
          local.get $t295
          local.get $t294
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t294
          i32.const 16
          i32.add
          local.get $t295
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t296
          call $jnative_exception_pending
          local.set $t297
          local.get $t297
          if
            i32.const 184
            local.set $pc
          else
            i32.const 185
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPrevVal
          local.set $t298
          local.get $__jn_frame
          i32.const 135
          local.get $t298
          call $jn_gc_frame_set_root
          local.get $t298
          local.set $gctmp.299
          local.get $__jn_frame
          i32.const 33
          local.get $gctmp.299
          call $jn_gc_frame_set_root
          local.get $gctmp.299
          local.set $t299
          local.get $__jn_frame
          i32.const 136
          local.get $t299
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t300
          local.get $t299
          i32.eqz
          if
            unreachable
          end
          local.get $t300
          i32.const 0
          i32.lt_s
          local.get $t300
          local.get $t299
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t299
          i32.const 16
          i32.add
          local.get $t300
          i32.const 4
          i32.mul
          i32.add
          local.get $t296
          i32.store
          call $jnative_exception_pending
          local.set $t301
          local.get $t301
          if
            i32.const 186
            local.set $pc
          else
            i32.const 187
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $hnv
          local.set $t302
          local.get $__jn_frame
          i32.const 137
          local.get $t302
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t303
          local.get $t302
          i32.eqz
          if
            unreachable
          end
          local.get $t303
          i32.const 0
          i32.lt_s
          local.get $t303
          local.get $t302
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t302
          i32.const 16
          i32.add
          local.get $t303
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t304
          call $jnative_exception_pending
          local.set $t305
          local.get $t305
          if
            i32.const 188
            local.set $pc
          else
            i32.const 189
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histNewVal
          local.set $t306
          local.get $__jn_frame
          i32.const 138
          local.get $t306
          call $jn_gc_frame_set_root
          local.get $t306
          local.set $gctmp.307
          local.get $__jn_frame
          i32.const 34
          local.get $gctmp.307
          call $jn_gc_frame_set_root
          local.get $gctmp.307
          local.set $t307
          local.get $__jn_frame
          i32.const 139
          local.get $t307
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t308
          local.get $t307
          i32.eqz
          if
            unreachable
          end
          local.get $t308
          i32.const 0
          i32.lt_s
          local.get $t308
          local.get $t307
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t307
          i32.const 16
          i32.add
          local.get $t308
          i32.const 4
          i32.mul
          i32.add
          local.get $t304
          i32.store
          call $jnative_exception_pending
          local.set $t309
          local.get $t309
          if
            i32.const 190
            local.set $pc
          else
            i32.const 191
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $hpn
          local.set $t310
          local.get $__jn_frame
          i32.const 140
          local.get $t310
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t311
          local.get $t310
          i32.eqz
          if
            unreachable
          end
          local.get $t311
          i32.const 0
          i32.lt_s
          local.get $t311
          local.get $t310
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t310
          i32.const 16
          i32.add
          local.get $t311
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t312
          call $jnative_exception_pending
          local.set $t313
          local.get $t313
          if
            i32.const 192
            local.set $pc
          else
            i32.const 193
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPrevNotes
          local.set $t314
          local.get $__jn_frame
          i32.const 141
          local.get $t314
          call $jn_gc_frame_set_root
          local.get $t314
          local.set $gctmp.315
          local.get $__jn_frame
          i32.const 35
          local.get $gctmp.315
          call $jn_gc_frame_set_root
          local.get $gctmp.315
          local.set $t315
          local.get $__jn_frame
          i32.const 142
          local.get $t315
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t316
          local.get $t315
          i32.eqz
          if
            unreachable
          end
          local.get $t316
          i32.const 0
          i32.lt_s
          local.get $t316
          local.get $t315
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t315
          i32.const 16
          i32.add
          local.get $t316
          i32.const 4
          i32.mul
          i32.add
          local.get $t312
          i32.store
          call $jnative_exception_pending
          local.set $t317
          local.get $t317
          if
            i32.const 194
            local.set $pc
          else
            i32.const 195
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $hnn
          local.set $t318
          local.get $__jn_frame
          i32.const 143
          local.get $t318
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t319
          local.get $t318
          i32.eqz
          if
            unreachable
          end
          local.get $t319
          i32.const 0
          i32.lt_s
          local.get $t319
          local.get $t318
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t318
          i32.const 16
          i32.add
          local.get $t319
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t320
          call $jnative_exception_pending
          local.set $t321
          local.get $t321
          if
            i32.const 196
            local.set $pc
          else
            i32.const 197
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histNewNotes
          local.set $t322
          local.get $__jn_frame
          i32.const 144
          local.get $t322
          call $jn_gc_frame_set_root
          local.get $t322
          local.set $gctmp.323
          local.get $__jn_frame
          i32.const 36
          local.get $gctmp.323
          call $jn_gc_frame_set_root
          local.get $gctmp.323
          local.set $t323
          local.get $__jn_frame
          i32.const 145
          local.get $t323
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t324
          local.get $t323
          i32.eqz
          if
            unreachable
          end
          local.get $t324
          i32.const 0
          i32.lt_s
          local.get $t324
          local.get $t323
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t323
          i32.const 16
          i32.add
          local.get $t324
          i32.const 4
          i32.mul
          i32.add
          local.get $t320
          i32.store
          call $jnative_exception_pending
          local.set $t325
          local.get $t325
          if
            i32.const 198
            local.set $pc
          else
            i32.const 199
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t326
          local.get $t326
          i32.const 1
          i32.add
          local.set $t327
          local.get $t327
          local.set $i
          call $jn_gc_safepoint
          i32.const 177
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_status
          local.set $t337
          global.get $g_jn_static_SudokuCore_ST_WON
          local.set $t338
          local.get $t337
          local.get $t338
          i32.ne
          local.set $t339
          local.get $t339
          if
            i32.const 202
            local.set $pc
          else
            i32.const 203
            local.set $pc
          end
          br $dispatch
        )
          call $_JN_SudokuCore_checkWon
          call $jnative_exception_pending
          local.set $t340
          local.get $t340
          if
            i32.const 204
            local.set $pc
          else
            i32.const 205
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 203
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_countSolutionsPuzzle_CString (param $p i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $p__slot i32)
    (local $sc.tmp19717364 i32)
    (local $i i32)
    (local $c i32)
    (local $sc.tmp1540270363 i32)
    (local $gctmp.25 i32)
    (local $sc.tmp1597655940 i32)
    (local $gctmp.34 i32)
    (local $gctmp.40 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    i32.const 14
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $p
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $exit
        )
          local.get $p
          local.set $p__slot
          local.get $__jn_frame
          i32.const 1
          local.get $p__slot
          call $jn_gc_frame_set_root
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $p__slot
          local.set $t1
          local.get $__jn_frame
          i32.const 5
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t1
          i32.const 0
          i32.eq
          local.set $t2
          local.get $t2
          local.set $sc.tmp19717364
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $p__slot
          local.set $t3
          local.get $__jn_frame
          i32.const 6
          local.get $t3
          call $jn_gc_frame_set_root
          local.get $t3
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t4
          local.get $t4
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp19717364
          local.set $t8
          local.get $t8
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t3
          call $jn_string_length
          local.set $t5
          call $jnative_exception_pending
          local.set $t6
          local.get $t6
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t5
          i32.const 81
          i32.ne
          local.set $t7
          local.get $t7
          local.set $sc.tmp19717364
          i32.const 4
          local.set $pc
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $i
          i32.const 11
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t9
          local.get $t9
          i32.const 81
          i32.lt_s
          local.set $t10
          local.get $t10
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          local.get $p__slot
          local.set $t11
          local.get $__jn_frame
          i32.const 7
          local.get $t11
          call $jn_gc_frame_set_root
          local.get $t11
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t12
          local.get $t12
          if
            i32.const 14
            local.set $pc
          else
            i32.const 15
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          global.get $g_jn_static_SudokuCore_work
          local.set $t39
          local.get $__jn_frame
          i32.const 12
          local.get $t39
          call $jn_gc_frame_set_root
          local.get $t39
          local.set $gctmp.40
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.40
          call $jn_gc_frame_set_root
          local.get $gctmp.40
          local.set $t40
          local.get $__jn_frame
          i32.const 13
          local.get $t40
          call $jn_gc_frame_set_root
          local.get $t40
          i32.const 2
          call $_JN_SudokuCore_countSolutionsBoard_Ai32_i32
          local.set $t41
          call $jnative_exception_pending
          local.set $t42
          local.get $t42
          if
            i32.const 32
            local.set $pc
          else
            i32.const 33
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t13
          local.get $t11
          local.get $t13
          call $jn_string_char_at
          local.set $t14
          call $jnative_exception_pending
          local.set $t15
          local.get $t15
          if
            i32.const 16
            local.set $pc
          else
            i32.const 17
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t14
          local.set $t16
          local.get $t16
          local.set $c
          local.get $c
          local.set $t17
          local.get $t17
          i32.const 49
          i32.ge_s
          local.set $t18
          local.get $t18
          local.set $sc.tmp1540270363
          local.get $t18
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t19
          local.get $t19
          i32.const 57
          i32.le_s
          local.set $t20
          local.get $t20
          local.set $sc.tmp1540270363
          i32.const 19
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1540270363
          local.set $t21
          local.get $t21
          if
            i32.const 20
            local.set $pc
          else
            i32.const 22
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t22
          local.get $t22
          i32.const 48
          i32.sub
          local.set $t23
          global.get $g_jn_static_SudokuCore_work
          local.set $t24
          local.get $__jn_frame
          i32.const 8
          local.get $t24
          call $jn_gc_frame_set_root
          local.get $t24
          local.set $gctmp.25
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.25
          call $jn_gc_frame_set_root
          local.get $gctmp.25
          local.set $t25
          local.get $__jn_frame
          i32.const 9
          local.get $t25
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t26
          local.get $t25
          i32.eqz
          if
            unreachable
          end
          local.get $t26
          i32.const 0
          i32.lt_s
          local.get $t26
          local.get $t25
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t25
          i32.const 16
          i32.add
          local.get $t26
          i32.const 4
          i32.mul
          i32.add
          local.get $t23
          i32.store
          call $jnative_exception_pending
          local.set $t27
          local.get $t27
          if
            i32.const 23
            local.set $pc
          else
            i32.const 24
            local.set $pc
          end
          br $dispatch
        )
          local.get $i
          local.set $t37
          local.get $t37
          i32.const 1
          i32.add
          local.set $t38
          local.get $t38
          local.set $i
          call $jn_gc_safepoint
          i32.const 11
          local.set $pc
          br $dispatch
        )
          local.get $c
          local.set $t28
          local.get $t28
          i32.const 48
          i32.eq
          local.set $t29
          local.get $t29
          local.set $sc.tmp1597655940
          local.get $t29
          if
            i32.const 26
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 21
          local.set $pc
          br $dispatch
        )
          local.get $c
          local.set $t30
          local.get $t30
          i32.const 46
          i32.eq
          local.set $t31
          local.get $t31
          local.set $sc.tmp1597655940
          i32.const 26
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1597655940
          local.set $t32
          local.get $t32
          if
            i32.const 27
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t33
          local.get $__jn_frame
          i32.const 10
          local.get $t33
          call $jn_gc_frame_set_root
          local.get $t33
          local.set $gctmp.34
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.34
          call $jn_gc_frame_set_root
          local.get $gctmp.34
          local.set $t34
          local.get $__jn_frame
          i32.const 11
          local.get $t34
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t35
          local.get $t34
          i32.eqz
          if
            unreachable
          end
          local.get $t35
          i32.const 0
          i32.lt_s
          local.get $t35
          local.get $t34
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t34
          i32.const 16
          i32.add
          local.get $t35
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t36
          local.get $t36
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          i32.const 21
          local.set $pc
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 28
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t41
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_generate_i32_i32 (param $seed i32) (param $diff i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $seed__slot i32)
    (local $diff__slot i32)
    (local $sc.tmp246399377 i32)
    (local $attempt i32)
    (local $targetGivens i32)
    (local $gctmp.22 i32)
    (local $gctmp.29 i32)
    (local $gctmp.31 i32)
    (local $gctmp.34 i32)
    (local $gctmp.38 i32)
    (local $gctmp.40 i32)
    (local $sols i32)
    (local $gctmp.43 i32)
    (local $i i32)
    (local $gctmp.51 i32)
    (local $gctmp.56 i32)
    (local $gctmp.60 i32)
    (local $gctmp.66 i32)
    (local $gctmp.70 i32)
    (local $gctmp.74 i32)
    (local $gctmp.80 i32)
    (local $gctmp.82 i32)
    (local $gctmp.85 i32)
    (local $gctmp.89 i32)
    (local $gctmp.91 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    (local $t72 i32)
    (local $t73 i32)
    (local $t74 i32)
    (local $t75 i32)
    (local $t76 i32)
    (local $t77 i32)
    (local $t78 i32)
    (local $t79 i32)
    (local $t80 i32)
    (local $t81 i32)
    (local $t82 i32)
    (local $t83 i32)
    (local $t84 i32)
    (local $t85 i32)
    (local $t86 i32)
    (local $t87 i32)
    (local $t88 i32)
    (local $t89 i32)
    (local $t90 i32)
    (local $t91 i32)
    (local $t92 i32)
    (local $t93 i32)
    (local $t94 i32)
    (local $t95 i32)
    (local $t96 i32)
    (local $t97 i32)
    (local $t98 i32)
    (local $t99 i32)
    (local $t100 i32)
    i32.const 54
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb57
        (block $bb56
        (block $bb55
        (block $bb54
        (block $bb53
        (block $bb52
        (block $bb51
        (block $bb50
        (block $bb49
        (block $bb48
        (block $bb47
        (block $bb46
        (block $bb45
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $bb45 $bb46 $bb47 $bb48 $bb49 $bb50 $bb51 $bb52 $bb53 $bb54 $bb55 $bb56 $bb57 $exit
        )
          local.get $seed
          local.set $seed__slot
          local.get $diff
          local.set $diff__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $diff__slot
          local.set $t1
          local.get $t1
          i32.const 0
          i32.lt_s
          local.set $t2
          local.get $t2
          local.set $sc.tmp246399377
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $diff__slot
          local.set $t3
          local.get $t3
          i32.const 2
          i32.gt_s
          local.set $t4
          local.get $t4
          local.set $sc.tmp246399377
          i32.const 4
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp246399377
          local.set $t5
          local.get $t5
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $seed__slot
          local.set $t6
          local.get $t6
          global.set $g_jn_static_SudokuCore_rngState
          global.get $g_jn_static_SudokuCore_rngState
          local.set $t7
          local.get $t7
          i32.const 0
          i32.eq
          local.set $t8
          local.get $t8
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_rngState
          i32.const 8
          local.set $pc
          br $dispatch
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_genSteps
          i32.const 0
          local.set $attempt
          i32.const 36
          local.set $targetGivens
          local.get $diff__slot
          local.set $t9
          global.get $g_jn_static_SudokuCore_DIFF_MEDIUM
          local.set $t10
          local.get $t9
          local.get $t10
          i32.eq
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
          i32.const 30
          local.set $targetGivens
          i32.const 10
          local.set $pc
          br $dispatch
        )
          local.get $diff__slot
          local.set $t12
          global.get $g_jn_static_SudokuCore_DIFF_HARD
          local.set $t13
          local.get $t12
          local.get $t13
          i32.eq
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
          i32.const 24
          local.set $targetGivens
          i32.const 12
          local.set $pc
          br $dispatch
        )
          i32.const 13
          local.set $pc
          br $dispatch
        )
          local.get $attempt
          local.set $t15
          global.get $g_jn_static_SudokuCore_GEN_MAX_ATTEMPTS
          local.set $t16
          local.get $t15
          local.get $t16
          i32.lt_s
          local.set $t17
          local.get $t17
          if
            i32.const 14
            local.set $pc
          else
            i32.const 15
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_genSteps
          local.set $t18
          global.get $g_jn_static_SudokuCore_GEN_MAX_STEPS
          local.set $t19
          local.get $t18
          local.get $t19
          i32.gt_s
          local.set $t20
          local.get $t20
          if
            i32.const 16
            local.set $pc
          else
            i32.const 17
            local.set $pc
          end
          br $dispatch
        )
          i32.const 7
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 7
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t21
          local.get $__jn_frame
          i32.const 18
          local.get $t21
          call $jn_gc_frame_set_root
          local.get $t21
          local.set $gctmp.22
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.22
          call $jn_gc_frame_set_root
          local.get $gctmp.22
          local.set $t22
          local.get $__jn_frame
          i32.const 19
          local.get $t22
          call $jn_gc_frame_set_root
          local.get $t22
          call $_JN_SudokuCore_fillRandomComplete_Ai32
          local.set $t23
          call $jnative_exception_pending
          local.set $t24
          local.get $t24
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t23
          i32.const 0
          i32.eq
          local.set $t25
          local.get $t25
          if
            i32.const 20
            local.set $pc
          else
            i32.const 21
            local.set $pc
          end
          br $dispatch
        )
          local.get $attempt
          local.set $t26
          local.get $t26
          i32.const 1
          i32.add
          local.set $t27
          local.get $t27
          local.set $attempt
          i32.const 13
          local.set $pc
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t28
          local.get $__jn_frame
          i32.const 20
          local.get $t28
          call $jn_gc_frame_set_root
          local.get $t28
          local.set $gctmp.29
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.29
          call $jn_gc_frame_set_root
          local.get $gctmp.29
          local.set $t29
          local.get $__jn_frame
          i32.const 21
          local.get $t29
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_work2
          local.set $t30
          local.get $__jn_frame
          i32.const 22
          local.get $t30
          call $jn_gc_frame_set_root
          local.get $t30
          local.set $gctmp.31
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.31
          call $jn_gc_frame_set_root
          local.get $gctmp.31
          local.set $t31
          local.get $__jn_frame
          i32.const 23
          local.get $t31
          call $jn_gc_frame_set_root
          local.get $t29
          local.get $t31
          call $_JN_SudokuCore_copyBoard_Ai32_Ai32
          call $jnative_exception_pending
          local.set $t32
          local.get $t32
          if
            i32.const 22
            local.set $pc
          else
            i32.const 23
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work2
          local.set $t33
          local.get $__jn_frame
          i32.const 24
          local.get $t33
          call $jn_gc_frame_set_root
          local.get $t33
          local.set $gctmp.34
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.34
          call $jn_gc_frame_set_root
          local.get $gctmp.34
          local.set $t34
          local.get $__jn_frame
          i32.const 25
          local.get $t34
          call $jn_gc_frame_set_root
          local.get $targetGivens
          local.set $t35
          local.get $t34
          local.get $t35
          call $_JN_SudokuCore_digHoles_Ai32_i32
          call $jnative_exception_pending
          local.set $t36
          local.get $t36
          if
            i32.const 24
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work2
          local.set $t37
          local.get $__jn_frame
          i32.const 26
          local.get $t37
          call $jn_gc_frame_set_root
          local.get $t37
          local.set $gctmp.38
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.38
          call $jn_gc_frame_set_root
          local.get $gctmp.38
          local.set $t38
          local.get $__jn_frame
          i32.const 27
          local.get $t38
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_work
          local.set $t39
          local.get $__jn_frame
          i32.const 28
          local.get $t39
          call $jn_gc_frame_set_root
          local.get $t39
          local.set $gctmp.40
          local.get $__jn_frame
          i32.const 5
          local.get $gctmp.40
          call $jn_gc_frame_set_root
          local.get $gctmp.40
          local.set $t40
          local.get $__jn_frame
          i32.const 29
          local.get $t40
          call $jn_gc_frame_set_root
          local.get $t38
          local.get $t40
          call $_JN_SudokuCore_copyBoard_Ai32_Ai32
          call $jnative_exception_pending
          local.set $t41
          local.get $t41
          if
            i32.const 26
            local.set $pc
          else
            i32.const 27
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t42
          local.get $__jn_frame
          i32.const 30
          local.get $t42
          call $jn_gc_frame_set_root
          local.get $t42
          local.set $gctmp.43
          local.get $__jn_frame
          i32.const 6
          local.get $gctmp.43
          call $jn_gc_frame_set_root
          local.get $gctmp.43
          local.set $t43
          local.get $__jn_frame
          i32.const 31
          local.get $t43
          call $jn_gc_frame_set_root
          local.get $t43
          i32.const 2
          call $_JN_SudokuCore_countSolutionsBoard_Ai32_i32
          local.set $t44
          call $jnative_exception_pending
          local.set $t45
          local.get $t45
          if
            i32.const 28
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t44
          local.set $sols
          local.get $sols
          local.set $t46
          local.get $t46
          i32.const 1
          i32.eq
          local.set $t47
          local.get $t47
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $i
          i32.const 32
          local.set $pc
          br $dispatch
        )
          local.get $attempt
          local.set $t99
          local.get $t99
          i32.const 1
          i32.add
          local.set $t100
          local.get $t100
          local.set $attempt
          call $jn_gc_safepoint
          i32.const 13
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t48
          local.get $t48
          i32.const 81
          i32.lt_s
          local.set $t49
          local.get $t49
          if
            i32.const 33
            local.set $pc
          else
            i32.const 34
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_work2
          local.set $t50
          local.get $__jn_frame
          i32.const 32
          local.get $t50
          call $jn_gc_frame_set_root
          local.get $t50
          local.set $gctmp.51
          local.get $__jn_frame
          i32.const 7
          local.get $gctmp.51
          call $jn_gc_frame_set_root
          local.get $gctmp.51
          local.set $t51
          local.get $__jn_frame
          i32.const 33
          local.get $t51
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t52
          local.get $t51
          i32.eqz
          if
            unreachable
          end
          local.get $t52
          i32.const 0
          i32.lt_s
          local.get $t52
          local.get $t51
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t51
          i32.const 16
          i32.add
          local.get $t52
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t53
          call $jnative_exception_pending
          local.set $t54
          local.get $t54
          if
            i32.const 35
            local.set $pc
          else
            i32.const 36
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_work2
          local.set $t79
          local.get $__jn_frame
          i32.const 44
          local.get $t79
          call $jn_gc_frame_set_root
          local.get $t79
          local.set $gctmp.80
          local.get $__jn_frame
          i32.const 13
          local.get $gctmp.80
          call $jn_gc_frame_set_root
          local.get $gctmp.80
          local.set $t80
          local.get $__jn_frame
          i32.const 45
          local.get $t80
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_work
          local.set $t81
          local.get $__jn_frame
          i32.const 46
          local.get $t81
          call $jn_gc_frame_set_root
          local.get $t81
          local.set $gctmp.82
          local.get $__jn_frame
          i32.const 14
          local.get $gctmp.82
          call $jn_gc_frame_set_root
          local.get $gctmp.82
          local.set $t82
          local.get $__jn_frame
          i32.const 47
          local.get $t82
          call $jn_gc_frame_set_root
          local.get $t80
          local.get $t82
          call $_JN_SudokuCore_copyBoard_Ai32_Ai32
          call $jnative_exception_pending
          local.set $t83
          local.get $t83
          if
            i32.const 50
            local.set $pc
          else
            i32.const 51
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t55
          local.get $__jn_frame
          i32.const 34
          local.get $t55
          call $jn_gc_frame_set_root
          local.get $t55
          local.set $gctmp.56
          local.get $__jn_frame
          i32.const 8
          local.get $gctmp.56
          call $jn_gc_frame_set_root
          local.get $gctmp.56
          local.set $t56
          local.get $__jn_frame
          i32.const 35
          local.get $t56
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t57
          local.get $t56
          i32.eqz
          if
            unreachable
          end
          local.get $t57
          i32.const 0
          i32.lt_s
          local.get $t57
          local.get $t56
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t56
          i32.const 16
          i32.add
          local.get $t57
          i32.const 4
          i32.mul
          i32.add
          local.get $t53
          i32.store
          call $jnative_exception_pending
          local.set $t58
          local.get $t58
          if
            i32.const 37
            local.set $pc
          else
            i32.const 38
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work2
          local.set $t59
          local.get $__jn_frame
          i32.const 36
          local.get $t59
          call $jn_gc_frame_set_root
          local.get $t59
          local.set $gctmp.60
          local.get $__jn_frame
          i32.const 9
          local.get $gctmp.60
          call $jn_gc_frame_set_root
          local.get $gctmp.60
          local.set $t60
          local.get $__jn_frame
          i32.const 37
          local.get $t60
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t61
          local.get $t60
          i32.eqz
          if
            unreachable
          end
          local.get $t61
          i32.const 0
          i32.lt_s
          local.get $t61
          local.get $t60
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t60
          i32.const 16
          i32.add
          local.get $t61
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t62
          call $jnative_exception_pending
          local.set $t63
          local.get $t63
          if
            i32.const 39
            local.set $pc
          else
            i32.const 40
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t62
          i32.const 0
          i32.ne
          local.set $t64
          local.get $t64
          if
            i32.const 41
            local.set $pc
          else
            i32.const 43
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_given
          local.set $t65
          local.get $__jn_frame
          i32.const 38
          local.get $t65
          call $jn_gc_frame_set_root
          local.get $t65
          local.set $gctmp.66
          local.get $__jn_frame
          i32.const 10
          local.get $gctmp.66
          call $jn_gc_frame_set_root
          local.get $gctmp.66
          local.set $t66
          local.get $__jn_frame
          i32.const 39
          local.get $t66
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t67
          local.get $t66
          i32.eqz
          if
            unreachable
          end
          local.get $t67
          i32.const 0
          i32.lt_s
          local.get $t67
          local.get $t66
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t66
          i32.const 16
          i32.add
          local.get $t67
          i32.const 4
          i32.mul
          i32.add
          i32.const 1
          i32.store
          call $jnative_exception_pending
          local.set $t68
          local.get $t68
          if
            i32.const 44
            local.set $pc
          else
            i32.const 45
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_notes
          local.set $t73
          local.get $__jn_frame
          i32.const 42
          local.get $t73
          call $jn_gc_frame_set_root
          local.get $t73
          local.set $gctmp.74
          local.get $__jn_frame
          i32.const 12
          local.get $gctmp.74
          call $jn_gc_frame_set_root
          local.get $gctmp.74
          local.set $t74
          local.get $__jn_frame
          i32.const 43
          local.get $t74
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t75
          local.get $t74
          i32.eqz
          if
            unreachable
          end
          local.get $t75
          i32.const 0
          i32.lt_s
          local.get $t75
          local.get $t74
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t74
          i32.const 16
          i32.add
          local.get $t75
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t76
          local.get $t76
          if
            i32.const 48
            local.set $pc
          else
            i32.const 49
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_given
          local.set $t69
          local.get $__jn_frame
          i32.const 40
          local.get $t69
          call $jn_gc_frame_set_root
          local.get $t69
          local.set $gctmp.70
          local.get $__jn_frame
          i32.const 11
          local.get $gctmp.70
          call $jn_gc_frame_set_root
          local.get $gctmp.70
          local.set $t70
          local.get $__jn_frame
          i32.const 41
          local.get $t70
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t71
          local.get $t70
          i32.eqz
          if
            unreachable
          end
          local.get $t71
          i32.const 0
          i32.lt_s
          local.get $t71
          local.get $t70
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t70
          i32.const 16
          i32.add
          local.get $t71
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t72
          local.get $t72
          if
            i32.const 46
            local.set $pc
          else
            i32.const 47
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 42
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 42
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t77
          local.get $t77
          i32.const 1
          i32.add
          local.set $t78
          local.get $t78
          local.set $i
          call $jn_gc_safepoint
          i32.const 32
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t84
          local.get $__jn_frame
          i32.const 48
          local.get $t84
          call $jn_gc_frame_set_root
          local.get $t84
          local.set $gctmp.85
          local.get $__jn_frame
          i32.const 15
          local.get $gctmp.85
          call $jn_gc_frame_set_root
          local.get $gctmp.85
          local.set $t85
          local.get $__jn_frame
          i32.const 49
          local.get $t85
          call $jn_gc_frame_set_root
          local.get $t85
          call $_JN_SudokuCore_solveBoard_Ai32
          local.set $t86
          call $jnative_exception_pending
          local.set $t87
          local.get $t87
          if
            i32.const 52
            local.set $pc
          else
            i32.const 53
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t88
          local.get $__jn_frame
          i32.const 50
          local.get $t88
          call $jn_gc_frame_set_root
          local.get $t88
          local.set $gctmp.89
          local.get $__jn_frame
          i32.const 16
          local.get $gctmp.89
          call $jn_gc_frame_set_root
          local.get $gctmp.89
          local.set $t89
          local.get $__jn_frame
          i32.const 51
          local.get $t89
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_solution
          local.set $t90
          local.get $__jn_frame
          i32.const 52
          local.get $t90
          call $jn_gc_frame_set_root
          local.get $t90
          local.set $gctmp.91
          local.get $__jn_frame
          i32.const 17
          local.get $gctmp.91
          call $jn_gc_frame_set_root
          local.get $gctmp.91
          local.set $t91
          local.get $__jn_frame
          i32.const 53
          local.get $t91
          call $jn_gc_frame_set_root
          local.get $t89
          local.get $t91
          call $_JN_SudokuCore_copyBoard_Ai32_Ai32
          call $jnative_exception_pending
          local.set $t92
          local.get $t92
          if
            i32.const 54
            local.set $pc
          else
            i32.const 55
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $diff__slot
          local.set $t93
          local.get $t93
          global.set $g_jn_static_SudokuCore_difficulty
          local.get $seed__slot
          local.set $t94
          local.get $t94
          i32.const 65535
          i32.and
          local.set $t95
          i32.const 1000
          local.get $t95
          i32.add
          local.set $t96
          local.get $t96
          global.set $g_jn_static_SudokuCore_puzzleId
          i32.const 0
          global.set $g_jn_static_SudokuCore_selected
          i32.const 0
          global.set $g_jn_static_SudokuCore_noteMode
          global.get $g_jn_static_SudokuCore_ST_PLAYING
          local.set $t97
          local.get $t97
          global.set $g_jn_static_SudokuCore_status
          i32.const 0
          global.set $g_jn_static_SudokuCore_elapsedMs
          i32.const 0
          global.set $g_jn_static_SudokuCore_tickValid
          i32.const 0
          global.set $g_jn_static_SudokuCore_histLen
          i32.const 0
          global.set $g_jn_static_SudokuCore_histPos
          call $_JN_SudokuCore_recomputeConflicts
          call $jnative_exception_pending
          local.set $t98
          local.get $t98
          if
            i32.const 56
            local.set $pc
          else
            i32.const 57
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_puzzleCount_i32 (param $diff i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $diff__slot i32)
    (local $sc.tmp2011986105 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb6
        (block $bb5
        (block $bb4
        (block $bb3
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $exit
        )
          local.get $diff
          local.set $diff__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $diff__slot
          local.set $t1
          local.get $t1
          i32.const 0
          i32.lt_s
          local.set $t2
          local.get $t2
          local.set $sc.tmp2011986105
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $diff__slot
          local.set $t3
          local.get $t3
          i32.const 2
          i32.gt_s
          local.set $t4
          local.get $t4
          local.set $sc.tmp2011986105
          i32.const 4
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp2011986105
          local.set $t5
          local.get $t5
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 3
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_exportBoardDigits (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $i i32)
    (local $gctmp.4 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    i32.const 3
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $exit
        )
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_saveLen
          i32.const 0
          local.set $i
          i32.const 3
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t1
          local.get $t1
          i32.const 81
          i32.lt_s
          local.set $t2
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t3
          local.get $__jn_frame
          i32.const 1
          local.get $t3
          call $jn_gc_frame_set_root
          local.get $t3
          local.set $gctmp.4
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.4
          call $jn_gc_frame_set_root
          local.get $gctmp.4
          local.set $t4
          local.get $__jn_frame
          i32.const 2
          local.get $t4
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t5
          local.get $t4
          i32.eqz
          if
            unreachable
          end
          local.get $t5
          i32.const 0
          i32.lt_s
          local.get $t5
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
          local.get $t5
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t6
          call $jnative_exception_pending
          local.set $t7
          local.get $t7
          if
            i32.const 6
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          global.get $g_jn_static_SudokuCore_saveLen
          local.set $t12
          local.get $t12
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 48
          local.get $t6
          i32.add
          local.set $t8
          local.get $t8
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t9
          local.get $t9
          if
            i32.const 8
            local.set $pc
          else
            i32.const 9
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t10
          local.get $t10
          i32.const 1
          i32.add
          local.set $t11
          local.get $t11
          local.set $i
          call $jn_gc_safepoint
          i32.const 3
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_getLastError (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
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
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_lastError
          local.set $t1
          local.get $t1
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $_JN_SudokuCore_boardValidComplete_Ai32 (param $b i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $b__slot i32)
    (local $i i32)
    (local $sc.tmp292917034 i32)
    (local $n i32)
    (local $rowMask i32)
    (local $colMask i32)
    (local $boxMask i32)
    (local $k i32)
    (local $rv i32)
    (local $cv i32)
    (local $br i32)
    (local $bc i32)
    (local $bv i32)
    (local $rb i32)
    (local $cb i32)
    (local $bb i32)
    (local $sc.tmp242355057 i32)
    (local $sc.tmp455538610 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    (local $t72 i32)
    (local $t73 i32)
    (local $t74 i32)
    (local $t75 i32)
    (local $t76 i32)
    (local $t77 i32)
    (local $t78 i32)
    (local $t79 i32)
    (local $t80 i32)
    (local $t81 i32)
    (local $t82 i32)
    (local $t83 i32)
    (local $t84 i32)
    (local $t85 i32)
    (local $t86 i32)
    (local $t87 i32)
    (local $t88 i32)
    (local $t89 i32)
    (local $t90 i32)
    (local $t91 i32)
    (local $t92 i32)
    i32.const 7
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $b
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $exit
        )
          local.get $b
          local.set $b__slot
          local.get $__jn_frame
          i32.const 1
          local.get $b__slot
          call $jn_gc_frame_set_root
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $i
          i32.const 3
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t1
          local.get $t1
          i32.const 81
          i32.lt_s
          local.set $t2
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          local.get $b__slot
          local.set $t3
          local.get $__jn_frame
          i32.const 2
          local.get $t3
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t4
          local.get $t3
          i32.eqz
          if
            unreachable
          end
          local.get $t4
          i32.const 0
          i32.lt_s
          local.get $t4
          local.get $t3
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t3
          i32.const 16
          i32.add
          local.get $t4
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t5
          call $jnative_exception_pending
          local.set $t6
          local.get $t6
          if
            i32.const 8
            local.set $pc
          else
            i32.const 9
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $n
          i32.const 14
          local.set $pc
          br $dispatch
        )
          local.get $b__slot
          local.set $t8
          local.get $__jn_frame
          i32.const 3
          local.get $t8
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t9
          local.get $t8
          i32.eqz
          if
            unreachable
          end
          local.get $t9
          i32.const 0
          i32.lt_s
          local.get $t9
          local.get $t8
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t8
          i32.const 16
          i32.add
          local.get $t9
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t10
          call $jnative_exception_pending
          local.set $t11
          local.get $t11
          if
            i32.const 10
            local.set $pc
          else
            i32.const 11
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp292917034
          local.set $t13
          local.get $t13
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t5
          i32.const 1
          i32.lt_s
          local.set $t7
          local.get $t7
          local.set $sc.tmp292917034
          local.get $t7
          if
            i32.const 7
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t10
          i32.const 9
          i32.gt_s
          local.set $t12
          local.get $t12
          local.set $sc.tmp292917034
          i32.const 7
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t14
          local.get $t14
          i32.const 1
          i32.add
          local.set $t15
          local.get $t15
          local.set $i
          call $jn_gc_safepoint
          i32.const 3
          local.set $pc
          br $dispatch
        )
          local.get $n
          local.set $t16
          local.get $t16
          i32.const 9
          i32.lt_s
          local.set $t17
          local.get $t17
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $rowMask
          i32.const 0
          local.set $colMask
          i32.const 0
          local.set $boxMask
          i32.const 0
          local.set $k
          i32.const 17
          local.set $pc
          br $dispatch
        )
          i32.const 1
          local.set $ret
          br $exit
        )
          local.get $k
          local.set $t18
          local.get $t18
          i32.const 9
          i32.lt_s
          local.set $t19
          local.get $t19
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          local.get $b__slot
          local.set $t20
          local.get $__jn_frame
          i32.const 4
          local.get $t20
          call $jn_gc_frame_set_root
          local.get $n
          local.set $t21
          local.get $t21
          i32.const 9
          i32.mul
          local.set $t22
          local.get $k
          local.set $t23
          local.get $t22
          local.get $t23
          i32.add
          local.set $t24
          local.get $t20
          i32.eqz
          if
            unreachable
          end
          local.get $t24
          i32.const 0
          i32.lt_s
          local.get $t24
          local.get $t20
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t20
          i32.const 16
          i32.add
          local.get $t24
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t25
          call $jnative_exception_pending
          local.set $t26
          local.get $t26
          if
            i32.const 20
            local.set $pc
          else
            i32.const 21
            local.set $pc
          end
          br $dispatch
        )
          local.get $n
          local.set $t91
          local.get $t91
          i32.const 1
          i32.add
          local.set $t92
          local.get $t92
          local.set $n
          call $jn_gc_safepoint
          i32.const 14
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t25
          local.set $rv
          local.get $b__slot
          local.set $t27
          local.get $__jn_frame
          i32.const 5
          local.get $t27
          call $jn_gc_frame_set_root
          local.get $k
          local.set $t28
          local.get $t28
          i32.const 9
          i32.mul
          local.set $t29
          local.get $n
          local.set $t30
          local.get $t29
          local.get $t30
          i32.add
          local.set $t31
          local.get $t27
          i32.eqz
          if
            unreachable
          end
          local.get $t31
          i32.const 0
          i32.lt_s
          local.get $t31
          local.get $t27
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t27
          i32.const 16
          i32.add
          local.get $t31
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t32
          call $jnative_exception_pending
          local.set $t33
          local.get $t33
          if
            i32.const 22
            local.set $pc
          else
            i32.const 23
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t32
          local.set $cv
          local.get $n
          local.set $t34
          local.get $t34
          i32.const 3
          i32.div_s
          local.set $t35
          call $jnative_exception_pending
          local.set $t36
          local.get $t36
          if
            i32.const 24
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t35
          i32.const 3
          i32.mul
          local.set $t37
          local.get $k
          local.set $t38
          local.get $t38
          i32.const 3
          i32.div_s
          local.set $t39
          call $jnative_exception_pending
          local.set $t40
          local.get $t40
          if
            i32.const 26
            local.set $pc
          else
            i32.const 27
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t37
          local.get $t39
          i32.add
          local.set $t41
          local.get $t41
          local.set $br
          local.get $n
          local.set $t42
          local.get $t42
          i32.const 3
          i32.rem_s
          local.set $t43
          call $jnative_exception_pending
          local.set $t44
          local.get $t44
          if
            i32.const 28
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t43
          i32.const 3
          i32.mul
          local.set $t45
          local.get $k
          local.set $t46
          local.get $t46
          i32.const 3
          i32.rem_s
          local.set $t47
          call $jnative_exception_pending
          local.set $t48
          local.get $t48
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t45
          local.get $t47
          i32.add
          local.set $t49
          local.get $t49
          local.set $bc
          local.get $b__slot
          local.set $t50
          local.get $__jn_frame
          i32.const 6
          local.get $t50
          call $jn_gc_frame_set_root
          local.get $br
          local.set $t51
          local.get $t51
          i32.const 9
          i32.mul
          local.set $t52
          local.get $bc
          local.set $t53
          local.get $t52
          local.get $t53
          i32.add
          local.set $t54
          local.get $t50
          i32.eqz
          if
            unreachable
          end
          local.get $t54
          i32.const 0
          i32.lt_s
          local.get $t54
          local.get $t50
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t50
          i32.const 16
          i32.add
          local.get $t54
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t55
          call $jnative_exception_pending
          local.set $t56
          local.get $t56
          if
            i32.const 32
            local.set $pc
          else
            i32.const 33
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t55
          local.set $bv
          local.get $rv
          local.set $t57
          local.get $t57
          i32.const 31
          i32.and
          local.set $t58
          i32.const 1
          local.get $t58
          i32.shl
          local.set $t59
          local.get $t59
          local.set $rb
          local.get $cv
          local.set $t60
          local.get $t60
          i32.const 31
          i32.and
          local.set $t61
          i32.const 1
          local.get $t61
          i32.shl
          local.set $t62
          local.get $t62
          local.set $cb
          local.get $bv
          local.set $t63
          local.get $t63
          i32.const 31
          i32.and
          local.set $t64
          i32.const 1
          local.get $t64
          i32.shl
          local.set $t65
          local.get $t65
          local.set $bb
          local.get $rowMask
          local.set $t66
          local.get $rb
          local.set $t67
          local.get $t66
          local.get $t67
          i32.and
          local.set $t68
          local.get $t68
          i32.const 0
          i32.ne
          local.set $t69
          local.get $t69
          local.set $sc.tmp455538610
          local.get $t69
          if
            i32.const 37
            local.set $pc
          else
            i32.const 36
            local.set $pc
          end
          br $dispatch
        )
          local.get $boxMask
          local.set $t75
          local.get $bb
          local.set $t76
          local.get $t75
          local.get $t76
          i32.and
          local.set $t77
          local.get $t77
          i32.const 0
          i32.ne
          local.set $t78
          local.get $t78
          local.set $sc.tmp242355057
          i32.const 35
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp242355057
          local.set $t79
          local.get $t79
          if
            i32.const 38
            local.set $pc
          else
            i32.const 39
            local.set $pc
          end
          br $dispatch
        )
          local.get $colMask
          local.set $t70
          local.get $cb
          local.set $t71
          local.get $t70
          local.get $t71
          i32.and
          local.set $t72
          local.get $t72
          i32.const 0
          i32.ne
          local.set $t73
          local.get $t73
          local.set $sc.tmp455538610
          i32.const 37
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp455538610
          local.set $t74
          local.get $t74
          local.set $sc.tmp242355057
          local.get $t74
          if
            i32.const 35
            local.set $pc
          else
            i32.const 34
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $rowMask
          local.set $t80
          local.get $rb
          local.set $t81
          local.get $t80
          local.get $t81
          i32.add
          local.set $t82
          local.get $t82
          local.set $rowMask
          local.get $colMask
          local.set $t83
          local.get $cb
          local.set $t84
          local.get $t83
          local.get $t84
          i32.add
          local.set $t85
          local.get $t85
          local.set $colMask
          local.get $boxMask
          local.set $t86
          local.get $bb
          local.set $t87
          local.get $t86
          local.get $t87
          i32.add
          local.set $t88
          local.get $t88
          local.set $boxMask
          local.get $k
          local.set $t89
          local.get $t89
          i32.const 1
          i32.add
          local.set $t90
          local.get $t90
          local.set $k
          call $jn_gc_safepoint
          i32.const 17
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_loadPuzzleString_CString_i32_i32 (param $p i32) (param $diff i32) (param $id i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $p__slot i32)
    (local $diff__slot i32)
    (local $id__slot i32)
    (local $sc.tmp1630521067 i32)
    (local $i i32)
    (local $c i32)
    (local $d i32)
    (local $sc.tmp274773041 i32)
    (local $sc.tmp1629911510 i32)
    (local $gctmp.32 i32)
    (local $gctmp.38 i32)
    (local $gctmp.42 i32)
    (local $gctmp.46 i32)
    (local $gctmp.50 i32)
    (local $gctmp.56 i32)
    (local $gctmp.58 i32)
    (local $sols i32)
    (local $gctmp.61 i32)
    (local $gctmp.67 i32)
    (local $gctmp.69 i32)
    (local $gctmp.72 i32)
    (local $gctmp.77 i32)
    (local $gctmp.79 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    (local $t72 i32)
    (local $t73 i32)
    (local $t74 i32)
    (local $t75 i32)
    (local $t76 i32)
    (local $t77 i32)
    (local $t78 i32)
    (local $t79 i32)
    (local $t80 i32)
    (local $t81 i32)
    (local $t82 i32)
    (local $t83 i32)
    (local $t84 i32)
    i32.const 44
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $p
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb58
        (block $bb57
        (block $bb56
        (block $bb55
        (block $bb54
        (block $bb53
        (block $bb52
        (block $bb51
        (block $bb50
        (block $bb49
        (block $bb48
        (block $bb47
        (block $bb46
        (block $bb45
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $bb45 $bb46 $bb47 $bb48 $bb49 $bb50 $bb51 $bb52 $bb53 $bb54 $bb55 $bb56 $bb57 $bb58 $exit
        )
          local.get $p
          local.set $p__slot
          local.get $__jn_frame
          i32.const 1
          local.get $p__slot
          call $jn_gc_frame_set_root
          local.get $diff
          local.set $diff__slot
          local.get $id
          local.set $id__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
          local.set $t1
          local.get $t1
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $p__slot
          local.set $t2
          local.get $__jn_frame
          i32.const 15
          local.get $t2
          call $jn_gc_frame_set_root
          local.get $t2
          i32.const 0
          i32.eq
          local.set $t3
          local.get $t3
          local.set $sc.tmp1630521067
          local.get $t3
          if
            i32.const 6
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          local.get $p__slot
          local.set $t4
          local.get $__jn_frame
          i32.const 16
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
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp1630521067
          local.set $t9
          local.get $t9
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t4
          call $jn_string_length
          local.set $t6
          call $jnative_exception_pending
          local.set $t7
          local.get $t7
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t6
          i32.const 81
          i32.ne
          local.set $t8
          local.get $t8
          local.set $sc.tmp1630521067
          i32.const 6
          local.set $pc
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $i
          i32.const 13
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t10
          local.get $t10
          i32.const 81
          i32.lt_s
          local.set $t11
          local.get $t11
          if
            i32.const 14
            local.set $pc
          else
            i32.const 15
            local.set $pc
          end
          br $dispatch
        )
          local.get $p__slot
          local.set $t12
          local.get $__jn_frame
          i32.const 17
          local.get $t12
          call $jn_gc_frame_set_root
          local.get $t12
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t13
          local.get $t13
          if
            i32.const 16
            local.set $pc
          else
            i32.const 17
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t55
          local.get $__jn_frame
          i32.const 28
          local.get $t55
          call $jn_gc_frame_set_root
          local.get $t55
          local.set $gctmp.56
          local.get $__jn_frame
          i32.const 7
          local.get $gctmp.56
          call $jn_gc_frame_set_root
          local.get $gctmp.56
          local.set $t56
          local.get $__jn_frame
          i32.const 29
          local.get $t56
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_work
          local.set $t57
          local.get $__jn_frame
          i32.const 30
          local.get $t57
          call $jn_gc_frame_set_root
          local.get $t57
          local.set $gctmp.58
          local.get $__jn_frame
          i32.const 8
          local.get $gctmp.58
          call $jn_gc_frame_set_root
          local.get $gctmp.58
          local.set $t58
          local.get $__jn_frame
          i32.const 31
          local.get $t58
          call $jn_gc_frame_set_root
          local.get $t56
          local.get $t58
          call $_JN_SudokuCore_copyBoard_Ai32_Ai32
          call $jnative_exception_pending
          local.set $t59
          local.get $t59
          if
            i32.const 43
            local.set $pc
          else
            i32.const 44
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t14
          local.get $t12
          local.get $t14
          call $jn_string_char_at
          local.set $t15
          call $jnative_exception_pending
          local.set $t16
          local.get $t16
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t15
          local.set $t17
          local.get $t17
          local.set $c
          i32.const 0
          local.set $d
          local.get $c
          local.set $t18
          local.get $t18
          i32.const 49
          i32.ge_s
          local.set $t19
          local.get $t19
          local.set $sc.tmp274773041
          local.get $t19
          if
            i32.const 20
            local.set $pc
          else
            i32.const 21
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t20
          local.get $t20
          i32.const 57
          i32.le_s
          local.set $t21
          local.get $t21
          local.set $sc.tmp274773041
          i32.const 21
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp274773041
          local.set $t22
          local.get $t22
          if
            i32.const 22
            local.set $pc
          else
            i32.const 24
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t23
          local.get $t23
          i32.const 48
          i32.sub
          local.set $t24
          local.get $t24
          local.set $d
          i32.const 23
          local.set $pc
          br $dispatch
        )
          local.get $d
          local.set $t30
          global.get $g_jn_static_SudokuCore_values
          local.set $t31
          local.get $__jn_frame
          i32.const 18
          local.get $t31
          call $jn_gc_frame_set_root
          local.get $t31
          local.set $gctmp.32
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.32
          call $jn_gc_frame_set_root
          local.get $gctmp.32
          local.set $t32
          local.get $__jn_frame
          i32.const 19
          local.get $t32
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t33
          local.get $t32
          i32.eqz
          if
            unreachable
          end
          local.get $t33
          i32.const 0
          i32.lt_s
          local.get $t33
          local.get $t32
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t32
          i32.const 16
          i32.add
          local.get $t33
          i32.const 4
          i32.mul
          i32.add
          local.get $t30
          i32.store
          call $jnative_exception_pending
          local.set $t34
          local.get $t34
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t25
          local.get $t25
          i32.const 48
          i32.eq
          local.set $t26
          local.get $t26
          local.set $sc.tmp1629911510
          local.get $t26
          if
            i32.const 26
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t27
          local.get $t27
          i32.const 46
          i32.eq
          local.set $t28
          local.get $t28
          local.set $sc.tmp1629911510
          i32.const 26
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1629911510
          local.set $t29
          local.get $t29
          if
            i32.const 27
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $d
          i32.const 28
          local.set $pc
          br $dispatch
        )
          i32.const 23
          local.set $pc
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $d
          local.set $t35
          local.get $t35
          i32.const 0
          i32.ne
          local.set $t36
          local.get $t36
          if
            i32.const 32
            local.set $pc
          else
            i32.const 34
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_given
          local.set $t37
          local.get $__jn_frame
          i32.const 20
          local.get $t37
          call $jn_gc_frame_set_root
          local.get $t37
          local.set $gctmp.38
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.38
          call $jn_gc_frame_set_root
          local.get $gctmp.38
          local.set $t38
          local.get $__jn_frame
          i32.const 21
          local.get $t38
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t39
          local.get $t38
          i32.eqz
          if
            unreachable
          end
          local.get $t39
          i32.const 0
          i32.lt_s
          local.get $t39
          local.get $t38
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t38
          i32.const 16
          i32.add
          local.get $t39
          i32.const 4
          i32.mul
          i32.add
          i32.const 1
          i32.store
          call $jnative_exception_pending
          local.set $t40
          local.get $t40
          if
            i32.const 35
            local.set $pc
          else
            i32.const 36
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_notes
          local.set $t45
          local.get $__jn_frame
          i32.const 24
          local.get $t45
          call $jn_gc_frame_set_root
          local.get $t45
          local.set $gctmp.46
          local.get $__jn_frame
          i32.const 5
          local.get $gctmp.46
          call $jn_gc_frame_set_root
          local.get $gctmp.46
          local.set $t46
          local.get $__jn_frame
          i32.const 25
          local.get $t46
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t47
          local.get $t46
          i32.eqz
          if
            unreachable
          end
          local.get $t47
          i32.const 0
          i32.lt_s
          local.get $t47
          local.get $t46
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t46
          i32.const 16
          i32.add
          local.get $t47
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t48
          local.get $t48
          if
            i32.const 39
            local.set $pc
          else
            i32.const 40
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_given
          local.set $t41
          local.get $__jn_frame
          i32.const 22
          local.get $t41
          call $jn_gc_frame_set_root
          local.get $t41
          local.set $gctmp.42
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.42
          call $jn_gc_frame_set_root
          local.get $gctmp.42
          local.set $t42
          local.get $__jn_frame
          i32.const 23
          local.get $t42
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t43
          local.get $t42
          i32.eqz
          if
            unreachable
          end
          local.get $t43
          i32.const 0
          i32.lt_s
          local.get $t43
          local.get $t42
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t42
          i32.const 16
          i32.add
          local.get $t43
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t44
          local.get $t44
          if
            i32.const 37
            local.set $pc
          else
            i32.const 38
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 33
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 33
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_conflict
          local.set $t49
          local.get $__jn_frame
          i32.const 26
          local.get $t49
          call $jn_gc_frame_set_root
          local.get $t49
          local.set $gctmp.50
          local.get $__jn_frame
          i32.const 6
          local.get $gctmp.50
          call $jn_gc_frame_set_root
          local.get $gctmp.50
          local.set $t50
          local.get $__jn_frame
          i32.const 27
          local.get $t50
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t51
          local.get $t50
          i32.eqz
          if
            unreachable
          end
          local.get $t51
          i32.const 0
          i32.lt_s
          local.get $t51
          local.get $t50
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t50
          i32.const 16
          i32.add
          local.get $t51
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t52
          local.get $t52
          if
            i32.const 41
            local.set $pc
          else
            i32.const 42
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t53
          local.get $t53
          i32.const 1
          i32.add
          local.set $t54
          local.get $t54
          local.set $i
          call $jn_gc_safepoint
          i32.const 13
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t60
          local.get $__jn_frame
          i32.const 32
          local.get $t60
          call $jn_gc_frame_set_root
          local.get $t60
          local.set $gctmp.61
          local.get $__jn_frame
          i32.const 9
          local.get $gctmp.61
          call $jn_gc_frame_set_root
          local.get $gctmp.61
          local.set $t61
          local.get $__jn_frame
          i32.const 33
          local.get $t61
          call $jn_gc_frame_set_root
          local.get $t61
          i32.const 2
          call $_JN_SudokuCore_countSolutionsBoard_Ai32_i32
          local.set $t62
          call $jnative_exception_pending
          local.set $t63
          local.get $t63
          if
            i32.const 45
            local.set $pc
          else
            i32.const 46
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t62
          local.set $sols
          local.get $sols
          local.set $t64
          local.get $t64
          i32.const 1
          i32.ne
          local.set $t65
          local.get $t65
          if
            i32.const 47
            local.set $pc
          else
            i32.const 48
            local.set $pc
          end
          br $dispatch
        )
          i32.const 8
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t66
          local.get $__jn_frame
          i32.const 34
          local.get $t66
          call $jn_gc_frame_set_root
          local.get $t66
          local.set $gctmp.67
          local.get $__jn_frame
          i32.const 10
          local.get $gctmp.67
          call $jn_gc_frame_set_root
          local.get $gctmp.67
          local.set $t67
          local.get $__jn_frame
          i32.const 35
          local.get $t67
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_work
          local.set $t68
          local.get $__jn_frame
          i32.const 36
          local.get $t68
          call $jn_gc_frame_set_root
          local.get $t68
          local.set $gctmp.69
          local.get $__jn_frame
          i32.const 11
          local.get $gctmp.69
          call $jn_gc_frame_set_root
          local.get $gctmp.69
          local.set $t69
          local.get $__jn_frame
          i32.const 37
          local.get $t69
          call $jn_gc_frame_set_root
          local.get $t67
          local.get $t69
          call $_JN_SudokuCore_copyBoard_Ai32_Ai32
          call $jnative_exception_pending
          local.set $t70
          local.get $t70
          if
            i32.const 49
            local.set $pc
          else
            i32.const 50
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t71
          local.get $__jn_frame
          i32.const 38
          local.get $t71
          call $jn_gc_frame_set_root
          local.get $t71
          local.set $gctmp.72
          local.get $__jn_frame
          i32.const 12
          local.get $gctmp.72
          call $jn_gc_frame_set_root
          local.get $gctmp.72
          local.set $t72
          local.get $__jn_frame
          i32.const 39
          local.get $t72
          call $jn_gc_frame_set_root
          local.get $t72
          call $_JN_SudokuCore_solveBoard_Ai32
          local.set $t73
          call $jnative_exception_pending
          local.set $t74
          local.get $t74
          if
            i32.const 51
            local.set $pc
          else
            i32.const 52
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t73
          i32.const 0
          i32.eq
          local.set $t75
          local.get $t75
          if
            i32.const 53
            local.set $pc
          else
            i32.const 54
            local.set $pc
          end
          br $dispatch
        )
          i32.const 8
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t76
          local.get $__jn_frame
          i32.const 40
          local.get $t76
          call $jn_gc_frame_set_root
          local.get $t76
          local.set $gctmp.77
          local.get $__jn_frame
          i32.const 13
          local.get $gctmp.77
          call $jn_gc_frame_set_root
          local.get $gctmp.77
          local.set $t77
          local.get $__jn_frame
          i32.const 41
          local.get $t77
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_solution
          local.set $t78
          local.get $__jn_frame
          i32.const 42
          local.get $t78
          call $jn_gc_frame_set_root
          local.get $t78
          local.set $gctmp.79
          local.get $__jn_frame
          i32.const 14
          local.get $gctmp.79
          call $jn_gc_frame_set_root
          local.get $gctmp.79
          local.set $t79
          local.get $__jn_frame
          i32.const 43
          local.get $t79
          call $jn_gc_frame_set_root
          local.get $t77
          local.get $t79
          call $_JN_SudokuCore_copyBoard_Ai32_Ai32
          call $jnative_exception_pending
          local.set $t80
          local.get $t80
          if
            i32.const 55
            local.set $pc
          else
            i32.const 56
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $diff__slot
          local.set $t81
          local.get $t81
          global.set $g_jn_static_SudokuCore_difficulty
          local.get $id__slot
          local.set $t82
          local.get $t82
          global.set $g_jn_static_SudokuCore_puzzleId
          i32.const 0
          global.set $g_jn_static_SudokuCore_selected
          i32.const 0
          global.set $g_jn_static_SudokuCore_noteMode
          global.get $g_jn_static_SudokuCore_ST_PLAYING
          local.set $t83
          local.get $t83
          global.set $g_jn_static_SudokuCore_status
          i32.const 0
          global.set $g_jn_static_SudokuCore_elapsedMs
          i32.const 0
          global.set $g_jn_static_SudokuCore_tickValid
          i32.const 0
          global.set $g_jn_static_SudokuCore_histLen
          i32.const 0
          global.set $g_jn_static_SudokuCore_histPos
          call $_JN_SudokuCore_recomputeConflicts
          call $jnative_exception_pending
          local.set $t84
          local.get $t84
          if
            i32.const 57
            local.set $pc
          else
            i32.const 58
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_ensureInit
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $gctmp.4 i32)
    (local $gctmp.7 i32)
    (local $gctmp.10 i32)
    (local $gctmp.13 i32)
    (local $gctmp.16 i32)
    (local $gctmp.20 i32)
    (local $gctmp.24 i32)
    (local $gctmp.28 i32)
    (local $gctmp.32 i32)
    (local $gctmp.36 i32)
    (local $gctmp.40 i32)
    (local $gctmp.43 i32)
    (local $gctmp.46 i32)
    (local $gctmp.49 i32)
    (local $gctmp.52 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    i32.const 45
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $exit
        )
          global.get $g_jn_static_SudokuCore_ready
          local.set $t0
          local.get $t0
          i32.const 0
          i32.ne
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
          i32.const 81
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 81
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 81
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t2
          local.get $t2
          i32.eqz
          if
            unreachable
          end
          local.get $t2
          i32.const 47
          i32.store
          local.get $t2
          i32.const 8
          i32.add
          i32.const 81
          i32.store
          local.get $__jn_frame
          i32.const 15
          local.get $t2
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t3
          local.get $t3
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
          local.get $t2
          local.set $gctmp.4
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.4
          call $jn_gc_frame_set_root
          local.get $gctmp.4
          local.set $t4
          local.get $__jn_frame
          i32.const 16
          local.get $t4
          call $jn_gc_frame_set_root
          local.get $t4
          global.set $g_jn_static_SudokuCore_values
          i32.const 81
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 81
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 81
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t5
          local.get $t5
          i32.eqz
          if
            unreachable
          end
          local.get $t5
          i32.const 47
          i32.store
          local.get $t5
          i32.const 8
          i32.add
          i32.const 81
          i32.store
          local.get $__jn_frame
          i32.const 17
          local.get $t5
          call $jn_gc_frame_set_root
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
          local.set $gctmp.7
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.7
          call $jn_gc_frame_set_root
          local.get $gctmp.7
          local.set $t7
          local.get $__jn_frame
          i32.const 18
          local.get $t7
          call $jn_gc_frame_set_root
          local.get $t7
          global.set $g_jn_static_SudokuCore_given
          i32.const 81
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 81
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 81
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t8
          local.get $t8
          i32.eqz
          if
            unreachable
          end
          local.get $t8
          i32.const 47
          i32.store
          local.get $t8
          i32.const 8
          i32.add
          i32.const 81
          i32.store
          local.get $__jn_frame
          i32.const 19
          local.get $t8
          call $jn_gc_frame_set_root
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
          local.set $gctmp.10
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.10
          call $jn_gc_frame_set_root
          local.get $gctmp.10
          local.set $t10
          local.get $__jn_frame
          i32.const 20
          local.get $t10
          call $jn_gc_frame_set_root
          local.get $t10
          global.set $g_jn_static_SudokuCore_notes
          i32.const 81
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 81
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 81
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t11
          local.get $t11
          i32.eqz
          if
            unreachable
          end
          local.get $t11
          i32.const 47
          i32.store
          local.get $t11
          i32.const 8
          i32.add
          i32.const 81
          i32.store
          local.get $__jn_frame
          i32.const 21
          local.get $t11
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t12
          local.get $t12
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
          local.get $t11
          local.set $gctmp.13
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.13
          call $jn_gc_frame_set_root
          local.get $gctmp.13
          local.set $t13
          local.get $__jn_frame
          i32.const 22
          local.get $t13
          call $jn_gc_frame_set_root
          local.get $t13
          global.set $g_jn_static_SudokuCore_conflict
          i32.const 81
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 81
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 81
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t14
          local.get $t14
          i32.eqz
          if
            unreachable
          end
          local.get $t14
          i32.const 47
          i32.store
          local.get $t14
          i32.const 8
          i32.add
          i32.const 81
          i32.store
          local.get $__jn_frame
          i32.const 23
          local.get $t14
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t15
          local.get $t15
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
          local.get $t14
          local.set $gctmp.16
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.16
          call $jn_gc_frame_set_root
          local.get $gctmp.16
          local.set $t16
          local.get $__jn_frame
          i32.const 24
          local.get $t16
          call $jn_gc_frame_set_root
          local.get $t16
          global.set $g_jn_static_SudokuCore_solution
          global.get $g_jn_static_SudokuCore_MAX_HISTORY
          local.set $t17
          local.get $t17
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          local.get $t17
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          local.get $t17
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t18
          local.get $t18
          i32.eqz
          if
            unreachable
          end
          local.get $t18
          i32.const 47
          i32.store
          local.get $t18
          i32.const 8
          i32.add
          local.get $t17
          i32.store
          local.get $__jn_frame
          i32.const 25
          local.get $t18
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t19
          local.get $t19
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
          local.get $t18
          local.set $gctmp.20
          local.get $__jn_frame
          i32.const 5
          local.get $gctmp.20
          call $jn_gc_frame_set_root
          local.get $gctmp.20
          local.set $t20
          local.get $__jn_frame
          i32.const 26
          local.get $t20
          call $jn_gc_frame_set_root
          local.get $t20
          global.set $g_jn_static_SudokuCore_histCell
          global.get $g_jn_static_SudokuCore_MAX_HISTORY
          local.set $t21
          local.get $t21
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          local.get $t21
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          local.get $t21
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t22
          local.get $t22
          i32.eqz
          if
            unreachable
          end
          local.get $t22
          i32.const 47
          i32.store
          local.get $t22
          i32.const 8
          i32.add
          local.get $t21
          i32.store
          local.get $__jn_frame
          i32.const 27
          local.get $t22
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t23
          local.get $t23
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
          local.get $t22
          local.set $gctmp.24
          local.get $__jn_frame
          i32.const 6
          local.get $gctmp.24
          call $jn_gc_frame_set_root
          local.get $gctmp.24
          local.set $t24
          local.get $__jn_frame
          i32.const 28
          local.get $t24
          call $jn_gc_frame_set_root
          local.get $t24
          global.set $g_jn_static_SudokuCore_histPrevVal
          global.get $g_jn_static_SudokuCore_MAX_HISTORY
          local.set $t25
          local.get $t25
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          local.get $t25
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          local.get $t25
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t26
          local.get $t26
          i32.eqz
          if
            unreachable
          end
          local.get $t26
          i32.const 47
          i32.store
          local.get $t26
          i32.const 8
          i32.add
          local.get $t25
          i32.store
          local.get $__jn_frame
          i32.const 29
          local.get $t26
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t27
          local.get $t27
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
          local.get $t26
          local.set $gctmp.28
          local.get $__jn_frame
          i32.const 7
          local.get $gctmp.28
          call $jn_gc_frame_set_root
          local.get $gctmp.28
          local.set $t28
          local.get $__jn_frame
          i32.const 30
          local.get $t28
          call $jn_gc_frame_set_root
          local.get $t28
          global.set $g_jn_static_SudokuCore_histNewVal
          global.get $g_jn_static_SudokuCore_MAX_HISTORY
          local.set $t29
          local.get $t29
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          local.get $t29
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          local.get $t29
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t30
          local.get $t30
          i32.eqz
          if
            unreachable
          end
          local.get $t30
          i32.const 47
          i32.store
          local.get $t30
          i32.const 8
          i32.add
          local.get $t29
          i32.store
          local.get $__jn_frame
          i32.const 31
          local.get $t30
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t31
          local.get $t31
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
          local.get $t30
          local.set $gctmp.32
          local.get $__jn_frame
          i32.const 8
          local.get $gctmp.32
          call $jn_gc_frame_set_root
          local.get $gctmp.32
          local.set $t32
          local.get $__jn_frame
          i32.const 32
          local.get $t32
          call $jn_gc_frame_set_root
          local.get $t32
          global.set $g_jn_static_SudokuCore_histPrevNotes
          global.get $g_jn_static_SudokuCore_MAX_HISTORY
          local.set $t33
          local.get $t33
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          local.get $t33
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          local.get $t33
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t34
          local.get $t34
          i32.eqz
          if
            unreachable
          end
          local.get $t34
          i32.const 47
          i32.store
          local.get $t34
          i32.const 8
          i32.add
          local.get $t33
          i32.store
          local.get $__jn_frame
          i32.const 33
          local.get $t34
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t35
          local.get $t35
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
          local.get $t34
          local.set $gctmp.36
          local.get $__jn_frame
          i32.const 9
          local.get $gctmp.36
          call $jn_gc_frame_set_root
          local.get $gctmp.36
          local.set $t36
          local.get $__jn_frame
          i32.const 34
          local.get $t36
          call $jn_gc_frame_set_root
          local.get $t36
          global.set $g_jn_static_SudokuCore_histNewNotes
          global.get $g_jn_static_SudokuCore_MAX_SAVE_CHARS
          local.set $t37
          local.get $t37
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          local.get $t37
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          local.get $t37
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t38
          local.get $t38
          i32.eqz
          if
            unreachable
          end
          local.get $t38
          i32.const 47
          i32.store
          local.get $t38
          i32.const 8
          i32.add
          local.get $t37
          i32.store
          local.get $__jn_frame
          i32.const 35
          local.get $t38
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t39
          local.get $t39
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
          local.get $t38
          local.set $gctmp.40
          local.get $__jn_frame
          i32.const 10
          local.get $gctmp.40
          call $jn_gc_frame_set_root
          local.get $gctmp.40
          local.set $t40
          local.get $__jn_frame
          i32.const 36
          local.get $t40
          call $jn_gc_frame_set_root
          local.get $t40
          global.set $g_jn_static_SudokuCore_saveBuf
          i32.const 81
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 81
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 81
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t41
          local.get $t41
          i32.eqz
          if
            unreachable
          end
          local.get $t41
          i32.const 47
          i32.store
          local.get $t41
          i32.const 8
          i32.add
          i32.const 81
          i32.store
          local.get $__jn_frame
          i32.const 37
          local.get $t41
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t42
          local.get $t42
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
          local.get $t41
          local.set $gctmp.43
          local.get $__jn_frame
          i32.const 11
          local.get $gctmp.43
          call $jn_gc_frame_set_root
          local.get $gctmp.43
          local.set $t43
          local.get $__jn_frame
          i32.const 38
          local.get $t43
          call $jn_gc_frame_set_root
          local.get $t43
          global.set $g_jn_static_SudokuCore_work
          i32.const 81
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 81
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 81
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t44
          local.get $t44
          i32.eqz
          if
            unreachable
          end
          local.get $t44
          i32.const 47
          i32.store
          local.get $t44
          i32.const 8
          i32.add
          i32.const 81
          i32.store
          local.get $__jn_frame
          i32.const 39
          local.get $t44
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t45
          local.get $t45
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
          local.get $t44
          local.set $gctmp.46
          local.get $__jn_frame
          i32.const 12
          local.get $gctmp.46
          call $jn_gc_frame_set_root
          local.get $gctmp.46
          local.set $t46
          local.get $__jn_frame
          i32.const 40
          local.get $t46
          call $jn_gc_frame_set_root
          local.get $t46
          global.set $g_jn_static_SudokuCore_work2
          i32.const 81
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 81
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 81
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t47
          local.get $t47
          i32.eqz
          if
            unreachable
          end
          local.get $t47
          i32.const 47
          i32.store
          local.get $t47
          i32.const 8
          i32.add
          i32.const 81
          i32.store
          local.get $__jn_frame
          i32.const 41
          local.get $t47
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t48
          local.get $t48
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
          local.get $t47
          local.set $gctmp.49
          local.get $__jn_frame
          i32.const 13
          local.get $gctmp.49
          call $jn_gc_frame_set_root
          local.get $gctmp.49
          local.set $t49
          local.get $__jn_frame
          i32.const 42
          local.get $t49
          call $jn_gc_frame_set_root
          local.get $t49
          global.set $g_jn_static_SudokuCore_btIdx
          i32.const 81
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 81
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 81
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t50
          local.get $t50
          i32.eqz
          if
            unreachable
          end
          local.get $t50
          i32.const 47
          i32.store
          local.get $t50
          i32.const 8
          i32.add
          i32.const 81
          i32.store
          local.get $__jn_frame
          i32.const 43
          local.get $t50
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t51
          local.get $t51
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
          local.get $t50
          local.set $gctmp.52
          local.get $__jn_frame
          i32.const 14
          local.get $gctmp.52
          call $jn_gc_frame_set_root
          local.get $gctmp.52
          local.set $t52
          local.get $__jn_frame
          i32.const 44
          local.get $t52
          call $jn_gc_frame_set_root
          local.get $t52
          global.set $g_jn_static_SudokuCore_btDigit
          i32.const 1
          global.set $g_jn_static_SudokuCore_ready
          br $exit
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_SudokuCore_loadPuzzleFromCatalog_i32_i32_i32 (param $puzzleIndex i32) (param $diff i32) (param $id i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $puzzleIndex__slot i32)
    (local $diff__slot i32)
    (local $id__slot i32)
    (local $cat i32)
    (local $gctmp.4 i32)
    (local $sc.tmp738433734 i32)
    (local $i i32)
    (local $c i32)
    (local $d i32)
    (local $sc.tmp1484594489 i32)
    (local $sc.tmp1489069835 i32)
    (local $gctmp.35 i32)
    (local $gctmp.41 i32)
    (local $gctmp.45 i32)
    (local $gctmp.49 i32)
    (local $gctmp.53 i32)
    (local $gctmp.59 i32)
    (local $gctmp.61 i32)
    (local $sols i32)
    (local $gctmp.64 i32)
    (local $gctmp.70 i32)
    (local $gctmp.72 i32)
    (local $gctmp.75 i32)
    (local $gctmp.80 i32)
    (local $gctmp.82 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    (local $t72 i32)
    (local $t73 i32)
    (local $t74 i32)
    (local $t75 i32)
    (local $t76 i32)
    (local $t77 i32)
    (local $t78 i32)
    (local $t79 i32)
    (local $t80 i32)
    (local $t81 i32)
    (local $t82 i32)
    (local $t83 i32)
    (local $t84 i32)
    (local $t85 i32)
    (local $t86 i32)
    (local $t87 i32)
    i32.const 46
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb58
        (block $bb57
        (block $bb56
        (block $bb55
        (block $bb54
        (block $bb53
        (block $bb52
        (block $bb51
        (block $bb50
        (block $bb49
        (block $bb48
        (block $bb47
        (block $bb46
        (block $bb45
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $bb45 $bb46 $bb47 $bb48 $bb49 $bb50 $bb51 $bb52 $bb53 $bb54 $bb55 $bb56 $bb57 $bb58 $exit
        )
          local.get $puzzleIndex
          local.set $puzzleIndex__slot
          local.get $diff
          local.set $diff__slot
          local.get $id
          local.set $id__slot
          call $_JN_SudokuCore_ensureInit
          call $jnative_exception_pending
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $puzzleIndex__slot
          local.set $t1
          local.get $t1
          call $_JN_SudokuCore_catalogPuzzle_i32
          local.set $t2
          local.get $__jn_frame
          i32.const 15
          local.get $t2
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t3
          local.get $t3
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t2
          local.set $gctmp.4
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.4
          call $jn_gc_frame_set_root
          local.get $gctmp.4
          local.set $t4
          local.get $__jn_frame
          i32.const 16
          local.get $t4
          call $jn_gc_frame_set_root
          local.get $t4
          local.set $cat
          local.get $__jn_frame
          i32.const 0
          local.get $cat
          call $jn_gc_frame_set_root
          local.get $cat
          local.set $t5
          local.get $__jn_frame
          i32.const 17
          local.get $t5
          call $jn_gc_frame_set_root
          local.get $t5
          i32.const 0
          i32.eq
          local.set $t6
          local.get $t6
          local.set $sc.tmp738433734
          local.get $t6
          if
            i32.const 6
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          local.get $cat
          local.set $t7
          local.get $__jn_frame
          i32.const 18
          local.get $t7
          call $jn_gc_frame_set_root
          local.get $t7
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t8
          local.get $t8
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp738433734
          local.set $t12
          local.get $t12
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t7
          call $jn_string_length
          local.set $t9
          call $jnative_exception_pending
          local.set $t10
          local.get $t10
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t9
          i32.const 81
          i32.ne
          local.set $t11
          local.get $t11
          local.set $sc.tmp738433734
          i32.const 6
          local.set $pc
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $i
          i32.const 13
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t13
          local.get $t13
          i32.const 81
          i32.lt_s
          local.set $t14
          local.get $t14
          if
            i32.const 14
            local.set $pc
          else
            i32.const 15
            local.set $pc
          end
          br $dispatch
        )
          local.get $cat
          local.set $t15
          local.get $__jn_frame
          i32.const 19
          local.get $t15
          call $jn_gc_frame_set_root
          local.get $t15
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t16
          local.get $t16
          if
            i32.const 16
            local.set $pc
          else
            i32.const 17
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t58
          local.get $__jn_frame
          i32.const 30
          local.get $t58
          call $jn_gc_frame_set_root
          local.get $t58
          local.set $gctmp.59
          local.get $__jn_frame
          i32.const 7
          local.get $gctmp.59
          call $jn_gc_frame_set_root
          local.get $gctmp.59
          local.set $t59
          local.get $__jn_frame
          i32.const 31
          local.get $t59
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_work
          local.set $t60
          local.get $__jn_frame
          i32.const 32
          local.get $t60
          call $jn_gc_frame_set_root
          local.get $t60
          local.set $gctmp.61
          local.get $__jn_frame
          i32.const 8
          local.get $gctmp.61
          call $jn_gc_frame_set_root
          local.get $gctmp.61
          local.set $t61
          local.get $__jn_frame
          i32.const 33
          local.get $t61
          call $jn_gc_frame_set_root
          local.get $t59
          local.get $t61
          call $_JN_SudokuCore_copyBoard_Ai32_Ai32
          call $jnative_exception_pending
          local.set $t62
          local.get $t62
          if
            i32.const 43
            local.set $pc
          else
            i32.const 44
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t17
          local.get $t15
          local.get $t17
          call $jn_string_char_at
          local.set $t18
          call $jnative_exception_pending
          local.set $t19
          local.get $t19
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t18
          local.set $t20
          local.get $t20
          local.set $c
          i32.const 0
          local.set $d
          local.get $c
          local.set $t21
          local.get $t21
          i32.const 49
          i32.ge_s
          local.set $t22
          local.get $t22
          local.set $sc.tmp1484594489
          local.get $t22
          if
            i32.const 20
            local.set $pc
          else
            i32.const 21
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t23
          local.get $t23
          i32.const 57
          i32.le_s
          local.set $t24
          local.get $t24
          local.set $sc.tmp1484594489
          i32.const 21
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1484594489
          local.set $t25
          local.get $t25
          if
            i32.const 22
            local.set $pc
          else
            i32.const 24
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t26
          local.get $t26
          i32.const 48
          i32.sub
          local.set $t27
          local.get $t27
          local.set $d
          i32.const 23
          local.set $pc
          br $dispatch
        )
          local.get $d
          local.set $t33
          global.get $g_jn_static_SudokuCore_values
          local.set $t34
          local.get $__jn_frame
          i32.const 20
          local.get $t34
          call $jn_gc_frame_set_root
          local.get $t34
          local.set $gctmp.35
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.35
          call $jn_gc_frame_set_root
          local.get $gctmp.35
          local.set $t35
          local.get $__jn_frame
          i32.const 21
          local.get $t35
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t36
          local.get $t35
          i32.eqz
          if
            unreachable
          end
          local.get $t36
          i32.const 0
          i32.lt_s
          local.get $t36
          local.get $t35
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t35
          i32.const 16
          i32.add
          local.get $t36
          i32.const 4
          i32.mul
          i32.add
          local.get $t33
          i32.store
          call $jnative_exception_pending
          local.set $t37
          local.get $t37
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t28
          local.get $t28
          i32.const 48
          i32.eq
          local.set $t29
          local.get $t29
          local.set $sc.tmp1489069835
          local.get $t29
          if
            i32.const 26
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t30
          local.get $t30
          i32.const 46
          i32.eq
          local.set $t31
          local.get $t31
          local.set $sc.tmp1489069835
          i32.const 26
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1489069835
          local.set $t32
          local.get $t32
          if
            i32.const 27
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $d
          i32.const 28
          local.set $pc
          br $dispatch
        )
          i32.const 23
          local.set $pc
          br $dispatch
        )
          i32.const 1
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $d
          local.set $t38
          local.get $t38
          i32.const 0
          i32.ne
          local.set $t39
          local.get $t39
          if
            i32.const 32
            local.set $pc
          else
            i32.const 34
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_given
          local.set $t40
          local.get $__jn_frame
          i32.const 22
          local.get $t40
          call $jn_gc_frame_set_root
          local.get $t40
          local.set $gctmp.41
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.41
          call $jn_gc_frame_set_root
          local.get $gctmp.41
          local.set $t41
          local.get $__jn_frame
          i32.const 23
          local.get $t41
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t42
          local.get $t41
          i32.eqz
          if
            unreachable
          end
          local.get $t42
          i32.const 0
          i32.lt_s
          local.get $t42
          local.get $t41
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t41
          i32.const 16
          i32.add
          local.get $t42
          i32.const 4
          i32.mul
          i32.add
          i32.const 1
          i32.store
          call $jnative_exception_pending
          local.set $t43
          local.get $t43
          if
            i32.const 35
            local.set $pc
          else
            i32.const 36
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_notes
          local.set $t48
          local.get $__jn_frame
          i32.const 26
          local.get $t48
          call $jn_gc_frame_set_root
          local.get $t48
          local.set $gctmp.49
          local.get $__jn_frame
          i32.const 5
          local.get $gctmp.49
          call $jn_gc_frame_set_root
          local.get $gctmp.49
          local.set $t49
          local.get $__jn_frame
          i32.const 27
          local.get $t49
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t50
          local.get $t49
          i32.eqz
          if
            unreachable
          end
          local.get $t50
          i32.const 0
          i32.lt_s
          local.get $t50
          local.get $t49
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t49
          i32.const 16
          i32.add
          local.get $t50
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t51
          local.get $t51
          if
            i32.const 39
            local.set $pc
          else
            i32.const 40
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_given
          local.set $t44
          local.get $__jn_frame
          i32.const 24
          local.get $t44
          call $jn_gc_frame_set_root
          local.get $t44
          local.set $gctmp.45
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.45
          call $jn_gc_frame_set_root
          local.get $gctmp.45
          local.set $t45
          local.get $__jn_frame
          i32.const 25
          local.get $t45
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t46
          local.get $t45
          i32.eqz
          if
            unreachable
          end
          local.get $t46
          i32.const 0
          i32.lt_s
          local.get $t46
          local.get $t45
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t45
          i32.const 16
          i32.add
          local.get $t46
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t47
          local.get $t47
          if
            i32.const 37
            local.set $pc
          else
            i32.const 38
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 33
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 33
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_conflict
          local.set $t52
          local.get $__jn_frame
          i32.const 28
          local.get $t52
          call $jn_gc_frame_set_root
          local.get $t52
          local.set $gctmp.53
          local.get $__jn_frame
          i32.const 6
          local.get $gctmp.53
          call $jn_gc_frame_set_root
          local.get $gctmp.53
          local.set $t53
          local.get $__jn_frame
          i32.const 29
          local.get $t53
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t54
          local.get $t53
          i32.eqz
          if
            unreachable
          end
          local.get $t54
          i32.const 0
          i32.lt_s
          local.get $t54
          local.get $t53
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t53
          i32.const 16
          i32.add
          local.get $t54
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t55
          local.get $t55
          if
            i32.const 41
            local.set $pc
          else
            i32.const 42
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t56
          local.get $t56
          i32.const 1
          i32.add
          local.set $t57
          local.get $t57
          local.set $i
          call $jn_gc_safepoint
          i32.const 13
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t63
          local.get $__jn_frame
          i32.const 34
          local.get $t63
          call $jn_gc_frame_set_root
          local.get $t63
          local.set $gctmp.64
          local.get $__jn_frame
          i32.const 9
          local.get $gctmp.64
          call $jn_gc_frame_set_root
          local.get $gctmp.64
          local.set $t64
          local.get $__jn_frame
          i32.const 35
          local.get $t64
          call $jn_gc_frame_set_root
          local.get $t64
          i32.const 2
          call $_JN_SudokuCore_countSolutionsBoard_Ai32_i32
          local.set $t65
          call $jnative_exception_pending
          local.set $t66
          local.get $t66
          if
            i32.const 45
            local.set $pc
          else
            i32.const 46
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t65
          local.set $sols
          local.get $sols
          local.set $t67
          local.get $t67
          i32.const 1
          i32.ne
          local.set $t68
          local.get $t68
          if
            i32.const 47
            local.set $pc
          else
            i32.const 48
            local.set $pc
          end
          br $dispatch
        )
          i32.const 8
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t69
          local.get $__jn_frame
          i32.const 36
          local.get $t69
          call $jn_gc_frame_set_root
          local.get $t69
          local.set $gctmp.70
          local.get $__jn_frame
          i32.const 10
          local.get $gctmp.70
          call $jn_gc_frame_set_root
          local.get $gctmp.70
          local.set $t70
          local.get $__jn_frame
          i32.const 37
          local.get $t70
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_work
          local.set $t71
          local.get $__jn_frame
          i32.const 38
          local.get $t71
          call $jn_gc_frame_set_root
          local.get $t71
          local.set $gctmp.72
          local.get $__jn_frame
          i32.const 11
          local.get $gctmp.72
          call $jn_gc_frame_set_root
          local.get $gctmp.72
          local.set $t72
          local.get $__jn_frame
          i32.const 39
          local.get $t72
          call $jn_gc_frame_set_root
          local.get $t70
          local.get $t72
          call $_JN_SudokuCore_copyBoard_Ai32_Ai32
          call $jnative_exception_pending
          local.set $t73
          local.get $t73
          if
            i32.const 49
            local.set $pc
          else
            i32.const 50
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t74
          local.get $__jn_frame
          i32.const 40
          local.get $t74
          call $jn_gc_frame_set_root
          local.get $t74
          local.set $gctmp.75
          local.get $__jn_frame
          i32.const 12
          local.get $gctmp.75
          call $jn_gc_frame_set_root
          local.get $gctmp.75
          local.set $t75
          local.get $__jn_frame
          i32.const 41
          local.get $t75
          call $jn_gc_frame_set_root
          local.get $t75
          call $_JN_SudokuCore_solveBoard_Ai32
          local.set $t76
          call $jnative_exception_pending
          local.set $t77
          local.get $t77
          if
            i32.const 51
            local.set $pc
          else
            i32.const 52
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t76
          i32.const 0
          i32.eq
          local.set $t78
          local.get $t78
          if
            i32.const 53
            local.set $pc
          else
            i32.const 54
            local.set $pc
          end
          br $dispatch
        )
          i32.const 8
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t79
          local.get $__jn_frame
          i32.const 42
          local.get $t79
          call $jn_gc_frame_set_root
          local.get $t79
          local.set $gctmp.80
          local.get $__jn_frame
          i32.const 13
          local.get $gctmp.80
          call $jn_gc_frame_set_root
          local.get $gctmp.80
          local.set $t80
          local.get $__jn_frame
          i32.const 43
          local.get $t80
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_solution
          local.set $t81
          local.get $__jn_frame
          i32.const 44
          local.get $t81
          call $jn_gc_frame_set_root
          local.get $t81
          local.set $gctmp.82
          local.get $__jn_frame
          i32.const 14
          local.get $gctmp.82
          call $jn_gc_frame_set_root
          local.get $gctmp.82
          local.set $t82
          local.get $__jn_frame
          i32.const 45
          local.get $t82
          call $jn_gc_frame_set_root
          local.get $t80
          local.get $t82
          call $_JN_SudokuCore_copyBoard_Ai32_Ai32
          call $jnative_exception_pending
          local.set $t83
          local.get $t83
          if
            i32.const 55
            local.set $pc
          else
            i32.const 56
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $diff__slot
          local.set $t84
          local.get $t84
          global.set $g_jn_static_SudokuCore_difficulty
          local.get $id__slot
          local.set $t85
          local.get $t85
          global.set $g_jn_static_SudokuCore_puzzleId
          i32.const 0
          global.set $g_jn_static_SudokuCore_selected
          i32.const 0
          global.set $g_jn_static_SudokuCore_noteMode
          global.get $g_jn_static_SudokuCore_ST_PLAYING
          local.set $t86
          local.get $t86
          global.set $g_jn_static_SudokuCore_status
          i32.const 0
          global.set $g_jn_static_SudokuCore_elapsedMs
          i32.const 0
          global.set $g_jn_static_SudokuCore_tickValid
          i32.const 0
          global.set $g_jn_static_SudokuCore_histLen
          i32.const 0
          global.set $g_jn_static_SudokuCore_histPos
          call $_JN_SudokuCore_recomputeConflicts
          call $jnative_exception_pending
          local.set $t87
          local.get $t87
          if
            i32.const 57
            local.set $pc
          else
            i32.const 58
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_toggleNoteAt_i32_i32 (param $idx i32) (param $d i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $idx__slot i32)
    (local $d__slot i32)
    (local $prevV i32)
    (local $gctmp.1 i32)
    (local $prevN i32)
    (local $gctmp.6 i32)
    (local $bit i32)
    (local $newN i32)
    (local $newV i32)
    (local $sc.tmp161960012 i32)
    (local $gctmp.40 i32)
    (local $gctmp.45 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    i32.const 12
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $exit
        )
          local.get $idx
          local.set $idx__slot
          local.get $d
          local.set $d__slot
          global.get $g_jn_static_SudokuCore_values
          local.set $t0
          local.get $__jn_frame
          i32.const 4
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
          i32.const 5
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $idx__slot
          local.set $t2
          local.get $t1
          i32.eqz
          if
            unreachable
          end
          local.get $t2
          i32.const 0
          i32.lt_s
          local.get $t2
          local.get $t1
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t1
          i32.const 16
          i32.add
          local.get $t2
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t3
          call $jnative_exception_pending
          local.set $t4
          local.get $t4
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t3
          local.set $prevV
          global.get $g_jn_static_SudokuCore_notes
          local.set $t5
          local.get $__jn_frame
          i32.const 6
          local.get $t5
          call $jn_gc_frame_set_root
          local.get $t5
          local.set $gctmp.6
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.6
          call $jn_gc_frame_set_root
          local.get $gctmp.6
          local.set $t6
          local.get $__jn_frame
          i32.const 7
          local.get $t6
          call $jn_gc_frame_set_root
          local.get $idx__slot
          local.set $t7
          local.get $t6
          i32.eqz
          if
            unreachable
          end
          local.get $t7
          i32.const 0
          i32.lt_s
          local.get $t7
          local.get $t6
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t6
          i32.const 16
          i32.add
          local.get $t7
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t8
          call $jnative_exception_pending
          local.set $t9
          local.get $t9
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t8
          local.set $prevN
          local.get $d__slot
          local.set $t10
          local.get $t10
          i32.const 1
          i32.sub
          local.set $t11
          local.get $t11
          i32.const 31
          i32.and
          local.set $t12
          i32.const 1
          local.get $t12
          i32.shl
          local.set $t13
          local.get $t13
          local.set $bit
          local.get $prevN
          local.set $t14
          local.get $t14
          local.set $newN
          local.get $newN
          local.set $t15
          local.get $bit
          local.set $t16
          local.get $t15
          local.get $t16
          i32.and
          local.set $t17
          local.get $t17
          i32.const 0
          i32.ne
          local.set $t18
          local.get $t18
          if
            i32.const 5
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          local.get $newN
          local.set $t19
          local.get $bit
          local.set $t20
          local.get $t19
          local.get $t20
          i32.sub
          local.set $t21
          local.get $t21
          local.set $newN
          i32.const 6
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $newV
          local.get $prevV
          local.set $t25
          local.get $newV
          local.set $t26
          local.get $t25
          local.get $t26
          i32.eq
          local.set $t27
          local.get $t27
          local.set $sc.tmp161960012
          local.get $t27
          if
            i32.const 8
            local.set $pc
          else
            i32.const 9
            local.set $pc
          end
          br $dispatch
        )
          local.get $newN
          local.set $t22
          local.get $bit
          local.set $t23
          local.get $t22
          local.get $t23
          i32.add
          local.set $t24
          local.get $t24
          local.set $newN
          i32.const 6
          local.set $pc
          br $dispatch
        )
          local.get $prevN
          local.set $t28
          local.get $newN
          local.set $t29
          local.get $t28
          local.get $t29
          i32.eq
          local.set $t30
          local.get $t30
          local.set $sc.tmp161960012
          i32.const 9
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp161960012
          local.set $t31
          local.get $t31
          if
            i32.const 10
            local.set $pc
          else
            i32.const 11
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
        )
          local.get $idx__slot
          local.set $t32
          local.get $prevV
          local.set $t33
          local.get $newV
          local.set $t34
          local.get $prevN
          local.set $t35
          local.get $newN
          local.set $t36
          local.get $t32
          local.get $t33
          local.get $t34
          local.get $t35
          local.get $t36
          call $_JN_SudokuCore_pushHistory_i32_i32_i32_i32_i32
          call $jnative_exception_pending
          local.set $t37
          local.get $t37
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $newV
          local.set $t38
          global.get $g_jn_static_SudokuCore_values
          local.set $t39
          local.get $__jn_frame
          i32.const 8
          local.get $t39
          call $jn_gc_frame_set_root
          local.get $t39
          local.set $gctmp.40
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.40
          call $jn_gc_frame_set_root
          local.get $gctmp.40
          local.set $t40
          local.get $__jn_frame
          i32.const 9
          local.get $t40
          call $jn_gc_frame_set_root
          local.get $idx__slot
          local.set $t41
          local.get $t40
          i32.eqz
          if
            unreachable
          end
          local.get $t41
          i32.const 0
          i32.lt_s
          local.get $t41
          local.get $t40
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t40
          i32.const 16
          i32.add
          local.get $t41
          i32.const 4
          i32.mul
          i32.add
          local.get $t38
          i32.store
          call $jnative_exception_pending
          local.set $t42
          local.get $t42
          if
            i32.const 14
            local.set $pc
          else
            i32.const 15
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $newN
          local.set $t43
          global.get $g_jn_static_SudokuCore_notes
          local.set $t44
          local.get $__jn_frame
          i32.const 10
          local.get $t44
          call $jn_gc_frame_set_root
          local.get $t44
          local.set $gctmp.45
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.45
          call $jn_gc_frame_set_root
          local.get $gctmp.45
          local.set $t45
          local.get $__jn_frame
          i32.const 11
          local.get $t45
          call $jn_gc_frame_set_root
          local.get $idx__slot
          local.set $t46
          local.get $t45
          i32.eqz
          if
            unreachable
          end
          local.get $t46
          i32.const 0
          i32.lt_s
          local.get $t46
          local.get $t45
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t45
          i32.const 16
          i32.add
          local.get $t46
          i32.const 4
          i32.mul
          i32.add
          local.get $t43
          i32.store
          call $jnative_exception_pending
          local.set $t47
          local.get $t47
          if
            i32.const 16
            local.set $pc
          else
            i32.const 17
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          call $_JN_SudokuCore_recomputeConflicts
          call $jnative_exception_pending
          local.set $t48
          local.get $t48
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_pushHistory_i32_i32_i32_i32_i32 (param $cell i32) (param $pv i32) (param $nv i32) (param $pn i32) (param $nn i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $cell__slot i32)
    (local $pv__slot i32)
    (local $nv__slot i32)
    (local $pn__slot i32)
    (local $nn__slot i32)
    (local $i i32)
    (local $gctmp.12 i32)
    (local $gctmp.18 i32)
    (local $gctmp.22 i32)
    (local $gctmp.28 i32)
    (local $gctmp.32 i32)
    (local $gctmp.38 i32)
    (local $gctmp.42 i32)
    (local $gctmp.48 i32)
    (local $gctmp.52 i32)
    (local $gctmp.58 i32)
    (local $gctmp.68 i32)
    (local $gctmp.73 i32)
    (local $gctmp.78 i32)
    (local $gctmp.83 i32)
    (local $gctmp.88 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    (local $t72 i32)
    (local $t73 i32)
    (local $t74 i32)
    (local $t75 i32)
    (local $t76 i32)
    (local $t77 i32)
    (local $t78 i32)
    (local $t79 i32)
    (local $t80 i32)
    (local $t81 i32)
    (local $t82 i32)
    (local $t83 i32)
    (local $t84 i32)
    (local $t85 i32)
    (local $t86 i32)
    (local $t87 i32)
    (local $t88 i32)
    (local $t89 i32)
    (local $t90 i32)
    (local $t91 i32)
    (local $t92 i32)
    (local $t93 i32)
    i32.const 45
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $exit
        )
          local.get $cell
          local.set $cell__slot
          local.get $pv
          local.set $pv__slot
          local.get $nv
          local.set $nv__slot
          local.get $pn
          local.set $pn__slot
          local.get $nn
          local.set $nn__slot
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t0
          global.get $g_jn_static_SudokuCore_histLen
          local.set $t1
          local.get $t0
          local.get $t1
          i32.lt_s
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
          global.get $g_jn_static_SudokuCore_histPos
          local.set $t3
          local.get $t3
          global.set $g_jn_static_SudokuCore_histLen
          i32.const 2
          local.set $pc
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_histLen
          local.set $t4
          global.get $g_jn_static_SudokuCore_MAX_HISTORY
          local.set $t5
          local.get $t4
          local.get $t5
          i32.ge_s
          local.set $t6
          local.get $t6
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $i
          i32.const 5
          local.set $pc
          br $dispatch
        )
          local.get $cell__slot
          local.set $t66
          global.get $g_jn_static_SudokuCore_histCell
          local.set $t67
          local.get $__jn_frame
          i32.const 35
          local.get $t67
          call $jn_gc_frame_set_root
          local.get $t67
          local.set $gctmp.68
          local.get $__jn_frame
          i32.const 10
          local.get $gctmp.68
          call $jn_gc_frame_set_root
          local.get $gctmp.68
          local.set $t68
          local.get $__jn_frame
          i32.const 36
          local.get $t68
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_histLen
          local.set $t69
          local.get $t68
          i32.eqz
          if
            unreachable
          end
          local.get $t69
          i32.const 0
          i32.lt_s
          local.get $t69
          local.get $t68
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t68
          i32.const 16
          i32.add
          local.get $t69
          i32.const 4
          i32.mul
          i32.add
          local.get $t66
          i32.store
          call $jnative_exception_pending
          local.set $t70
          local.get $t70
          if
            i32.const 28
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          local.get $i
          local.set $t7
          global.get $g_jn_static_SudokuCore_MAX_HISTORY
          local.set $t8
          local.get $t8
          i32.const 1
          i32.sub
          local.set $t9
          local.get $t7
          local.get $t9
          i32.lt_s
          local.set $t10
          local.get $t10
          if
            i32.const 6
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_histCell
          local.set $t11
          local.get $__jn_frame
          i32.const 15
          local.get $t11
          call $jn_gc_frame_set_root
          local.get $t11
          local.set $gctmp.12
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.12
          call $jn_gc_frame_set_root
          local.get $gctmp.12
          local.set $t12
          local.get $__jn_frame
          i32.const 16
          local.get $t12
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t13
          local.get $t13
          i32.const 1
          i32.add
          local.set $t14
          local.get $t12
          i32.eqz
          if
            unreachable
          end
          local.get $t14
          i32.const 0
          i32.lt_s
          local.get $t14
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
          local.get $t14
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t15
          call $jnative_exception_pending
          local.set $t16
          local.get $t16
          if
            i32.const 8
            local.set $pc
          else
            i32.const 9
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_MAX_HISTORY
          local.set $t63
          local.get $t63
          i32.const 1
          i32.sub
          local.set $t64
          local.get $t64
          global.set $g_jn_static_SudokuCore_histLen
          global.get $g_jn_static_SudokuCore_histLen
          local.set $t65
          local.get $t65
          global.set $g_jn_static_SudokuCore_histPos
          i32.const 4
          local.set $pc
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histCell
          local.set $t17
          local.get $__jn_frame
          i32.const 17
          local.get $t17
          call $jn_gc_frame_set_root
          local.get $t17
          local.set $gctmp.18
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.18
          call $jn_gc_frame_set_root
          local.get $gctmp.18
          local.set $t18
          local.get $__jn_frame
          i32.const 18
          local.get $t18
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t19
          local.get $t18
          i32.eqz
          if
            unreachable
          end
          local.get $t19
          i32.const 0
          i32.lt_s
          local.get $t19
          local.get $t18
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t18
          i32.const 16
          i32.add
          local.get $t19
          i32.const 4
          i32.mul
          i32.add
          local.get $t15
          i32.store
          call $jnative_exception_pending
          local.set $t20
          local.get $t20
          if
            i32.const 10
            local.set $pc
          else
            i32.const 11
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPrevVal
          local.set $t21
          local.get $__jn_frame
          i32.const 19
          local.get $t21
          call $jn_gc_frame_set_root
          local.get $t21
          local.set $gctmp.22
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.22
          call $jn_gc_frame_set_root
          local.get $gctmp.22
          local.set $t22
          local.get $__jn_frame
          i32.const 20
          local.get $t22
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t23
          local.get $t23
          i32.const 1
          i32.add
          local.set $t24
          local.get $t22
          i32.eqz
          if
            unreachable
          end
          local.get $t24
          i32.const 0
          i32.lt_s
          local.get $t24
          local.get $t22
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t22
          i32.const 16
          i32.add
          local.get $t24
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t25
          call $jnative_exception_pending
          local.set $t26
          local.get $t26
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPrevVal
          local.set $t27
          local.get $__jn_frame
          i32.const 21
          local.get $t27
          call $jn_gc_frame_set_root
          local.get $t27
          local.set $gctmp.28
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.28
          call $jn_gc_frame_set_root
          local.get $gctmp.28
          local.set $t28
          local.get $__jn_frame
          i32.const 22
          local.get $t28
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t29
          local.get $t28
          i32.eqz
          if
            unreachable
          end
          local.get $t29
          i32.const 0
          i32.lt_s
          local.get $t29
          local.get $t28
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t28
          i32.const 16
          i32.add
          local.get $t29
          i32.const 4
          i32.mul
          i32.add
          local.get $t25
          i32.store
          call $jnative_exception_pending
          local.set $t30
          local.get $t30
          if
            i32.const 14
            local.set $pc
          else
            i32.const 15
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histNewVal
          local.set $t31
          local.get $__jn_frame
          i32.const 23
          local.get $t31
          call $jn_gc_frame_set_root
          local.get $t31
          local.set $gctmp.32
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.32
          call $jn_gc_frame_set_root
          local.get $gctmp.32
          local.set $t32
          local.get $__jn_frame
          i32.const 24
          local.get $t32
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t33
          local.get $t33
          i32.const 1
          i32.add
          local.set $t34
          local.get $t32
          i32.eqz
          if
            unreachable
          end
          local.get $t34
          i32.const 0
          i32.lt_s
          local.get $t34
          local.get $t32
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t32
          i32.const 16
          i32.add
          local.get $t34
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t35
          call $jnative_exception_pending
          local.set $t36
          local.get $t36
          if
            i32.const 16
            local.set $pc
          else
            i32.const 17
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histNewVal
          local.set $t37
          local.get $__jn_frame
          i32.const 25
          local.get $t37
          call $jn_gc_frame_set_root
          local.get $t37
          local.set $gctmp.38
          local.get $__jn_frame
          i32.const 5
          local.get $gctmp.38
          call $jn_gc_frame_set_root
          local.get $gctmp.38
          local.set $t38
          local.get $__jn_frame
          i32.const 26
          local.get $t38
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t39
          local.get $t38
          i32.eqz
          if
            unreachable
          end
          local.get $t39
          i32.const 0
          i32.lt_s
          local.get $t39
          local.get $t38
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t38
          i32.const 16
          i32.add
          local.get $t39
          i32.const 4
          i32.mul
          i32.add
          local.get $t35
          i32.store
          call $jnative_exception_pending
          local.set $t40
          local.get $t40
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPrevNotes
          local.set $t41
          local.get $__jn_frame
          i32.const 27
          local.get $t41
          call $jn_gc_frame_set_root
          local.get $t41
          local.set $gctmp.42
          local.get $__jn_frame
          i32.const 6
          local.get $gctmp.42
          call $jn_gc_frame_set_root
          local.get $gctmp.42
          local.set $t42
          local.get $__jn_frame
          i32.const 28
          local.get $t42
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t43
          local.get $t43
          i32.const 1
          i32.add
          local.set $t44
          local.get $t42
          i32.eqz
          if
            unreachable
          end
          local.get $t44
          i32.const 0
          i32.lt_s
          local.get $t44
          local.get $t42
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t42
          i32.const 16
          i32.add
          local.get $t44
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t45
          call $jnative_exception_pending
          local.set $t46
          local.get $t46
          if
            i32.const 20
            local.set $pc
          else
            i32.const 21
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histPrevNotes
          local.set $t47
          local.get $__jn_frame
          i32.const 29
          local.get $t47
          call $jn_gc_frame_set_root
          local.get $t47
          local.set $gctmp.48
          local.get $__jn_frame
          i32.const 7
          local.get $gctmp.48
          call $jn_gc_frame_set_root
          local.get $gctmp.48
          local.set $t48
          local.get $__jn_frame
          i32.const 30
          local.get $t48
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t49
          local.get $t48
          i32.eqz
          if
            unreachable
          end
          local.get $t49
          i32.const 0
          i32.lt_s
          local.get $t49
          local.get $t48
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t48
          i32.const 16
          i32.add
          local.get $t49
          i32.const 4
          i32.mul
          i32.add
          local.get $t45
          i32.store
          call $jnative_exception_pending
          local.set $t50
          local.get $t50
          if
            i32.const 22
            local.set $pc
          else
            i32.const 23
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histNewNotes
          local.set $t51
          local.get $__jn_frame
          i32.const 31
          local.get $t51
          call $jn_gc_frame_set_root
          local.get $t51
          local.set $gctmp.52
          local.get $__jn_frame
          i32.const 8
          local.get $gctmp.52
          call $jn_gc_frame_set_root
          local.get $gctmp.52
          local.set $t52
          local.get $__jn_frame
          i32.const 32
          local.get $t52
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t53
          local.get $t53
          i32.const 1
          i32.add
          local.set $t54
          local.get $t52
          i32.eqz
          if
            unreachable
          end
          local.get $t54
          i32.const 0
          i32.lt_s
          local.get $t54
          local.get $t52
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t52
          i32.const 16
          i32.add
          local.get $t54
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t55
          call $jnative_exception_pending
          local.set $t56
          local.get $t56
          if
            i32.const 24
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histNewNotes
          local.set $t57
          local.get $__jn_frame
          i32.const 33
          local.get $t57
          call $jn_gc_frame_set_root
          local.get $t57
          local.set $gctmp.58
          local.get $__jn_frame
          i32.const 9
          local.get $gctmp.58
          call $jn_gc_frame_set_root
          local.get $gctmp.58
          local.set $t58
          local.get $__jn_frame
          i32.const 34
          local.get $t58
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t59
          local.get $t58
          i32.eqz
          if
            unreachable
          end
          local.get $t59
          i32.const 0
          i32.lt_s
          local.get $t59
          local.get $t58
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t58
          i32.const 16
          i32.add
          local.get $t59
          i32.const 4
          i32.mul
          i32.add
          local.get $t55
          i32.store
          call $jnative_exception_pending
          local.set $t60
          local.get $t60
          if
            i32.const 26
            local.set $pc
          else
            i32.const 27
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $i
          local.set $t61
          local.get $t61
          i32.const 1
          i32.add
          local.set $t62
          local.get $t62
          local.set $i
          call $jn_gc_safepoint
          i32.const 5
          local.set $pc
          br $dispatch
        )
          br $exit
        )
          local.get $pv__slot
          local.set $t71
          global.get $g_jn_static_SudokuCore_histPrevVal
          local.set $t72
          local.get $__jn_frame
          i32.const 37
          local.get $t72
          call $jn_gc_frame_set_root
          local.get $t72
          local.set $gctmp.73
          local.get $__jn_frame
          i32.const 11
          local.get $gctmp.73
          call $jn_gc_frame_set_root
          local.get $gctmp.73
          local.set $t73
          local.get $__jn_frame
          i32.const 38
          local.get $t73
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_histLen
          local.set $t74
          local.get $t73
          i32.eqz
          if
            unreachable
          end
          local.get $t74
          i32.const 0
          i32.lt_s
          local.get $t74
          local.get $t73
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t73
          i32.const 16
          i32.add
          local.get $t74
          i32.const 4
          i32.mul
          i32.add
          local.get $t71
          i32.store
          call $jnative_exception_pending
          local.set $t75
          local.get $t75
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $nv__slot
          local.set $t76
          global.get $g_jn_static_SudokuCore_histNewVal
          local.set $t77
          local.get $__jn_frame
          i32.const 39
          local.get $t77
          call $jn_gc_frame_set_root
          local.get $t77
          local.set $gctmp.78
          local.get $__jn_frame
          i32.const 12
          local.get $gctmp.78
          call $jn_gc_frame_set_root
          local.get $gctmp.78
          local.set $t78
          local.get $__jn_frame
          i32.const 40
          local.get $t78
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_histLen
          local.set $t79
          local.get $t78
          i32.eqz
          if
            unreachable
          end
          local.get $t79
          i32.const 0
          i32.lt_s
          local.get $t79
          local.get $t78
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t78
          i32.const 16
          i32.add
          local.get $t79
          i32.const 4
          i32.mul
          i32.add
          local.get $t76
          i32.store
          call $jnative_exception_pending
          local.set $t80
          local.get $t80
          if
            i32.const 32
            local.set $pc
          else
            i32.const 33
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $pn__slot
          local.set $t81
          global.get $g_jn_static_SudokuCore_histPrevNotes
          local.set $t82
          local.get $__jn_frame
          i32.const 41
          local.get $t82
          call $jn_gc_frame_set_root
          local.get $t82
          local.set $gctmp.83
          local.get $__jn_frame
          i32.const 13
          local.get $gctmp.83
          call $jn_gc_frame_set_root
          local.get $gctmp.83
          local.set $t83
          local.get $__jn_frame
          i32.const 42
          local.get $t83
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_histLen
          local.set $t84
          local.get $t83
          i32.eqz
          if
            unreachable
          end
          local.get $t84
          i32.const 0
          i32.lt_s
          local.get $t84
          local.get $t83
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t83
          i32.const 16
          i32.add
          local.get $t84
          i32.const 4
          i32.mul
          i32.add
          local.get $t81
          i32.store
          call $jnative_exception_pending
          local.set $t85
          local.get $t85
          if
            i32.const 34
            local.set $pc
          else
            i32.const 35
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $nn__slot
          local.set $t86
          global.get $g_jn_static_SudokuCore_histNewNotes
          local.set $t87
          local.get $__jn_frame
          i32.const 43
          local.get $t87
          call $jn_gc_frame_set_root
          local.get $t87
          local.set $gctmp.88
          local.get $__jn_frame
          i32.const 14
          local.get $gctmp.88
          call $jn_gc_frame_set_root
          local.get $gctmp.88
          local.set $t88
          local.get $__jn_frame
          i32.const 44
          local.get $t88
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_histLen
          local.set $t89
          local.get $t88
          i32.eqz
          if
            unreachable
          end
          local.get $t89
          i32.const 0
          i32.lt_s
          local.get $t89
          local.get $t88
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t88
          i32.const 16
          i32.add
          local.get $t89
          i32.const 4
          i32.mul
          i32.add
          local.get $t86
          i32.store
          call $jnative_exception_pending
          local.set $t90
          local.get $t90
          if
            i32.const 36
            local.set $pc
          else
            i32.const 37
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_histLen
          local.set $t91
          local.get $t91
          i32.const 1
          i32.add
          local.set $t92
          local.get $t92
          global.set $g_jn_static_SudokuCore_histLen
          global.get $g_jn_static_SudokuCore_histLen
          local.set $t93
          local.get $t93
          global.set $g_jn_static_SudokuCore_histPos
          br $exit
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_SudokuCore_recomputeConflicts
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $i i32)
    (local $gctmp.3 i32)
    (local $v i32)
    (local $gctmp.11 i32)
    (local $r i32)
    (local $c i32)
    (local $j i32)
    (local $ri i32)
    (local $sc.tmp140799417 i32)
    (local $gctmp.35 i32)
    (local $gctmp.43 i32)
    (local $gctmp.47 i32)
    (local $ci i32)
    (local $sc.tmp926370398 i32)
    (local $gctmp.58 i32)
    (local $gctmp.66 i32)
    (local $gctmp.70 i32)
    (local $br i32)
    (local $bc i32)
    (local $dr i32)
    (local $dc i32)
    (local $bi i32)
    (local $sc.tmp1181869371 i32)
    (local $gctmp.99 i32)
    (local $gctmp.107 i32)
    (local $gctmp.111 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    (local $t72 i32)
    (local $t73 i32)
    (local $t74 i32)
    (local $t75 i32)
    (local $t76 i32)
    (local $t77 i32)
    (local $t78 i32)
    (local $t79 i32)
    (local $t80 i32)
    (local $t81 i32)
    (local $t82 i32)
    (local $t83 i32)
    (local $t84 i32)
    (local $t85 i32)
    (local $t86 i32)
    (local $t87 i32)
    (local $t88 i32)
    (local $t89 i32)
    (local $t90 i32)
    (local $t91 i32)
    (local $t92 i32)
    (local $t93 i32)
    (local $t94 i32)
    (local $t95 i32)
    (local $t96 i32)
    (local $t97 i32)
    (local $t98 i32)
    (local $t99 i32)
    (local $t100 i32)
    (local $t101 i32)
    (local $t102 i32)
    (local $t103 i32)
    (local $t104 i32)
    (local $t105 i32)
    (local $t106 i32)
    (local $t107 i32)
    (local $t108 i32)
    (local $t109 i32)
    (local $t110 i32)
    (local $t111 i32)
    (local $t112 i32)
    (local $t113 i32)
    (local $t114 i32)
    (local $t115 i32)
    (local $t116 i32)
    (local $t117 i32)
    (local $t118 i32)
    (local $t119 i32)
    i32.const 33
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb59
        (block $bb58
        (block $bb57
        (block $bb56
        (block $bb55
        (block $bb54
        (block $bb53
        (block $bb52
        (block $bb51
        (block $bb50
        (block $bb49
        (block $bb48
        (block $bb47
        (block $bb46
        (block $bb45
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $bb45 $bb46 $bb47 $bb48 $bb49 $bb50 $bb51 $bb52 $bb53 $bb54 $bb55 $bb56 $bb57 $bb58 $bb59 $exit
        )
          i32.const 0
          local.set $i
          i32.const 1
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t0
          local.get $t0
          i32.const 81
          i32.lt_s
          local.set $t1
          local.get $t1
          if
            i32.const 2
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_conflict
          local.set $t2
          local.get $__jn_frame
          i32.const 11
          local.get $t2
          call $jn_gc_frame_set_root
          local.get $t2
          local.set $gctmp.3
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.3
          call $jn_gc_frame_set_root
          local.get $gctmp.3
          local.set $t3
          local.get $__jn_frame
          i32.const 12
          local.get $t3
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t4
          local.get $t3
          i32.eqz
          if
            unreachable
          end
          local.get $t4
          i32.const 0
          i32.lt_s
          local.get $t4
          local.get $t3
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t3
          i32.const 16
          i32.add
          local.get $t4
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t5
          local.get $t5
          if
            i32.const 4
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $i
          i32.const 6
          local.set $pc
          br $dispatch
        )
          br $exit
        )
          local.get $i
          local.set $t6
          local.get $t6
          i32.const 1
          i32.add
          local.set $t7
          local.get $t7
          local.set $i
          call $jn_gc_safepoint
          i32.const 1
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t8
          local.get $t8
          i32.const 81
          i32.lt_s
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
          global.get $g_jn_static_SudokuCore_values
          local.set $t10
          local.get $__jn_frame
          i32.const 13
          local.get $t10
          call $jn_gc_frame_set_root
          local.get $t10
          local.set $gctmp.11
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.11
          call $jn_gc_frame_set_root
          local.get $gctmp.11
          local.set $t11
          local.get $__jn_frame
          i32.const 14
          local.get $t11
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t12
          local.get $t11
          i32.eqz
          if
            unreachable
          end
          local.get $t12
          i32.const 0
          i32.lt_s
          local.get $t12
          local.get $t11
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t11
          i32.const 16
          i32.add
          local.get $t12
          i32.const 4
          i32.mul
          i32.add
          i32.load
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
          br $exit
        )
          br $exit
        )
          local.get $t13
          local.set $v
          local.get $v
          local.set $t15
          local.get $t15
          i32.const 0
          i32.eq
          local.set $t16
          local.get $t16
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          local.get $i
          local.set $t17
          local.get $t17
          i32.const 1
          i32.add
          local.set $t18
          local.get $t18
          local.set $i
          i32.const 6
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t19
          local.get $t19
          i32.const 9
          i32.div_s
          local.set $t20
          call $jnative_exception_pending
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
          br $exit
        )
          local.get $t20
          local.set $r
          local.get $i
          local.set $t22
          local.get $t22
          i32.const 9
          i32.rem_s
          local.set $t23
          call $jnative_exception_pending
          local.set $t24
          local.get $t24
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
          local.get $t23
          local.set $c
          i32.const 0
          local.set $j
          i32.const 17
          local.set $pc
          br $dispatch
        )
          local.get $j
          local.set $t25
          local.get $t25
          i32.const 9
          i32.lt_s
          local.set $t26
          local.get $t26
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          local.get $r
          local.set $t27
          local.get $t27
          i32.const 9
          i32.mul
          local.set $t28
          local.get $j
          local.set $t29
          local.get $t28
          local.get $t29
          i32.add
          local.set $t30
          local.get $t30
          local.set $ri
          local.get $ri
          local.set $t31
          local.get $i
          local.set $t32
          local.get $t31
          local.get $t32
          i32.ne
          local.set $t33
          local.get $t33
          local.set $sc.tmp140799417
          local.get $t33
          if
            i32.const 20
            local.set $pc
          else
            i32.const 21
            local.set $pc
          end
          br $dispatch
        )
          local.get $r
          local.set $t75
          local.get $t75
          i32.const 3
          i32.div_s
          local.set $t76
          call $jnative_exception_pending
          local.set $t77
          local.get $t77
          if
            i32.const 40
            local.set $pc
          else
            i32.const 41
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t34
          local.get $__jn_frame
          i32.const 15
          local.get $t34
          call $jn_gc_frame_set_root
          local.get $t34
          local.set $gctmp.35
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.35
          call $jn_gc_frame_set_root
          local.get $gctmp.35
          local.set $t35
          local.get $__jn_frame
          i32.const 16
          local.get $t35
          call $jn_gc_frame_set_root
          local.get $ri
          local.set $t36
          local.get $t35
          i32.eqz
          if
            unreachable
          end
          local.get $t36
          i32.const 0
          i32.lt_s
          local.get $t36
          local.get $t35
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t35
          i32.const 16
          i32.add
          local.get $t36
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t37
          call $jnative_exception_pending
          local.set $t38
          local.get $t38
          if
            i32.const 22
            local.set $pc
          else
            i32.const 23
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp140799417
          local.set $t41
          local.get $t41
          if
            i32.const 24
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $v
          local.set $t39
          local.get $t37
          local.get $t39
          i32.eq
          local.set $t40
          local.get $t40
          local.set $sc.tmp140799417
          i32.const 21
          local.set $pc
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_conflict
          local.set $t42
          local.get $__jn_frame
          i32.const 17
          local.get $t42
          call $jn_gc_frame_set_root
          local.get $t42
          local.set $gctmp.43
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.43
          call $jn_gc_frame_set_root
          local.get $gctmp.43
          local.set $t43
          local.get $__jn_frame
          i32.const 18
          local.get $t43
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t44
          local.get $t43
          i32.eqz
          if
            unreachable
          end
          local.get $t44
          i32.const 0
          i32.lt_s
          local.get $t44
          local.get $t43
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t43
          i32.const 16
          i32.add
          local.get $t44
          i32.const 4
          i32.mul
          i32.add
          i32.const 1
          i32.store
          call $jnative_exception_pending
          local.set $t45
          local.get $t45
          if
            i32.const 26
            local.set $pc
          else
            i32.const 27
            local.set $pc
          end
          br $dispatch
        )
          local.get $j
          local.set $t50
          local.get $t50
          i32.const 9
          i32.mul
          local.set $t51
          local.get $c
          local.set $t52
          local.get $t51
          local.get $t52
          i32.add
          local.set $t53
          local.get $t53
          local.set $ci
          local.get $ci
          local.set $t54
          local.get $i
          local.set $t55
          local.get $t54
          local.get $t55
          i32.ne
          local.set $t56
          local.get $t56
          local.set $sc.tmp926370398
          local.get $t56
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_conflict
          local.set $t46
          local.get $__jn_frame
          i32.const 19
          local.get $t46
          call $jn_gc_frame_set_root
          local.get $t46
          local.set $gctmp.47
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.47
          call $jn_gc_frame_set_root
          local.get $gctmp.47
          local.set $t47
          local.get $__jn_frame
          i32.const 20
          local.get $t47
          call $jn_gc_frame_set_root
          local.get $ri
          local.set $t48
          local.get $t47
          i32.eqz
          if
            unreachable
          end
          local.get $t48
          i32.const 0
          i32.lt_s
          local.get $t48
          local.get $t47
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t47
          i32.const 16
          i32.add
          local.get $t48
          i32.const 4
          i32.mul
          i32.add
          i32.const 1
          i32.store
          call $jnative_exception_pending
          local.set $t49
          local.get $t49
          if
            i32.const 28
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          i32.const 25
          local.set $pc
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t57
          local.get $__jn_frame
          i32.const 21
          local.get $t57
          call $jn_gc_frame_set_root
          local.get $t57
          local.set $gctmp.58
          local.get $__jn_frame
          i32.const 5
          local.get $gctmp.58
          call $jn_gc_frame_set_root
          local.get $gctmp.58
          local.set $t58
          local.get $__jn_frame
          i32.const 22
          local.get $t58
          call $jn_gc_frame_set_root
          local.get $ci
          local.set $t59
          local.get $t58
          i32.eqz
          if
            unreachable
          end
          local.get $t59
          i32.const 0
          i32.lt_s
          local.get $t59
          local.get $t58
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t58
          i32.const 16
          i32.add
          local.get $t59
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t60
          call $jnative_exception_pending
          local.set $t61
          local.get $t61
          if
            i32.const 32
            local.set $pc
          else
            i32.const 33
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp926370398
          local.set $t64
          local.get $t64
          if
            i32.const 34
            local.set $pc
          else
            i32.const 35
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $v
          local.set $t62
          local.get $t60
          local.get $t62
          i32.eq
          local.set $t63
          local.get $t63
          local.set $sc.tmp926370398
          i32.const 31
          local.set $pc
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_conflict
          local.set $t65
          local.get $__jn_frame
          i32.const 23
          local.get $t65
          call $jn_gc_frame_set_root
          local.get $t65
          local.set $gctmp.66
          local.get $__jn_frame
          i32.const 6
          local.get $gctmp.66
          call $jn_gc_frame_set_root
          local.get $gctmp.66
          local.set $t66
          local.get $__jn_frame
          i32.const 24
          local.get $t66
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t67
          local.get $t66
          i32.eqz
          if
            unreachable
          end
          local.get $t67
          i32.const 0
          i32.lt_s
          local.get $t67
          local.get $t66
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t66
          i32.const 16
          i32.add
          local.get $t67
          i32.const 4
          i32.mul
          i32.add
          i32.const 1
          i32.store
          call $jnative_exception_pending
          local.set $t68
          local.get $t68
          if
            i32.const 36
            local.set $pc
          else
            i32.const 37
            local.set $pc
          end
          br $dispatch
        )
          local.get $j
          local.set $t73
          local.get $t73
          i32.const 1
          i32.add
          local.set $t74
          local.get $t74
          local.set $j
          call $jn_gc_safepoint
          i32.const 17
          local.set $pc
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_conflict
          local.set $t69
          local.get $__jn_frame
          i32.const 25
          local.get $t69
          call $jn_gc_frame_set_root
          local.get $t69
          local.set $gctmp.70
          local.get $__jn_frame
          i32.const 7
          local.get $gctmp.70
          call $jn_gc_frame_set_root
          local.get $gctmp.70
          local.set $t70
          local.get $__jn_frame
          i32.const 26
          local.get $t70
          call $jn_gc_frame_set_root
          local.get $ci
          local.set $t71
          local.get $t70
          i32.eqz
          if
            unreachable
          end
          local.get $t71
          i32.const 0
          i32.lt_s
          local.get $t71
          local.get $t70
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t70
          i32.const 16
          i32.add
          local.get $t71
          i32.const 4
          i32.mul
          i32.add
          i32.const 1
          i32.store
          call $jnative_exception_pending
          local.set $t72
          local.get $t72
          if
            i32.const 38
            local.set $pc
          else
            i32.const 39
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          i32.const 35
          local.set $pc
          br $dispatch
        )
          br $exit
        )
          local.get $t76
          i32.const 3
          i32.mul
          local.set $t78
          local.get $t78
          local.set $br
          local.get $c
          local.set $t79
          local.get $t79
          i32.const 3
          i32.div_s
          local.set $t80
          call $jnative_exception_pending
          local.set $t81
          local.get $t81
          if
            i32.const 42
            local.set $pc
          else
            i32.const 43
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t80
          i32.const 3
          i32.mul
          local.set $t82
          local.get $t82
          local.set $bc
          i32.const 0
          local.set $dr
          i32.const 44
          local.set $pc
          br $dispatch
        )
          local.get $dr
          local.set $t83
          local.get $t83
          i32.const 3
          i32.lt_s
          local.set $t84
          local.get $t84
          if
            i32.const 45
            local.set $pc
          else
            i32.const 46
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $dc
          i32.const 47
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t118
          local.get $t118
          i32.const 1
          i32.add
          local.set $t119
          local.get $t119
          local.set $i
          call $jn_gc_safepoint
          i32.const 6
          local.set $pc
          br $dispatch
        )
          local.get $dc
          local.set $t85
          local.get $t85
          i32.const 3
          i32.lt_s
          local.set $t86
          local.get $t86
          if
            i32.const 48
            local.set $pc
          else
            i32.const 49
            local.set $pc
          end
          br $dispatch
        )
          local.get $br
          local.set $t87
          local.get $dr
          local.set $t88
          local.get $t87
          local.get $t88
          i32.add
          local.set $t89
          local.get $t89
          i32.const 9
          i32.mul
          local.set $t90
          local.get $bc
          local.set $t91
          local.get $dc
          local.set $t92
          local.get $t91
          local.get $t92
          i32.add
          local.set $t93
          local.get $t90
          local.get $t93
          i32.add
          local.set $t94
          local.get $t94
          local.set $bi
          local.get $bi
          local.set $t95
          local.get $i
          local.set $t96
          local.get $t95
          local.get $t96
          i32.ne
          local.set $t97
          local.get $t97
          local.set $sc.tmp1181869371
          local.get $t97
          if
            i32.const 50
            local.set $pc
          else
            i32.const 51
            local.set $pc
          end
          br $dispatch
        )
          local.get $dr
          local.set $t116
          local.get $t116
          i32.const 1
          i32.add
          local.set $t117
          local.get $t117
          local.set $dr
          call $jn_gc_safepoint
          i32.const 44
          local.set $pc
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t98
          local.get $__jn_frame
          i32.const 27
          local.get $t98
          call $jn_gc_frame_set_root
          local.get $t98
          local.set $gctmp.99
          local.get $__jn_frame
          i32.const 8
          local.get $gctmp.99
          call $jn_gc_frame_set_root
          local.get $gctmp.99
          local.set $t99
          local.get $__jn_frame
          i32.const 28
          local.get $t99
          call $jn_gc_frame_set_root
          local.get $bi
          local.set $t100
          local.get $t99
          i32.eqz
          if
            unreachable
          end
          local.get $t100
          i32.const 0
          i32.lt_s
          local.get $t100
          local.get $t99
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t99
          i32.const 16
          i32.add
          local.get $t100
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t101
          call $jnative_exception_pending
          local.set $t102
          local.get $t102
          if
            i32.const 52
            local.set $pc
          else
            i32.const 53
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp1181869371
          local.set $t105
          local.get $t105
          if
            i32.const 54
            local.set $pc
          else
            i32.const 55
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $v
          local.set $t103
          local.get $t101
          local.get $t103
          i32.eq
          local.set $t104
          local.get $t104
          local.set $sc.tmp1181869371
          i32.const 51
          local.set $pc
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_conflict
          local.set $t106
          local.get $__jn_frame
          i32.const 29
          local.get $t106
          call $jn_gc_frame_set_root
          local.get $t106
          local.set $gctmp.107
          local.get $__jn_frame
          i32.const 9
          local.get $gctmp.107
          call $jn_gc_frame_set_root
          local.get $gctmp.107
          local.set $t107
          local.get $__jn_frame
          i32.const 30
          local.get $t107
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t108
          local.get $t107
          i32.eqz
          if
            unreachable
          end
          local.get $t108
          i32.const 0
          i32.lt_s
          local.get $t108
          local.get $t107
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t107
          i32.const 16
          i32.add
          local.get $t108
          i32.const 4
          i32.mul
          i32.add
          i32.const 1
          i32.store
          call $jnative_exception_pending
          local.set $t109
          local.get $t109
          if
            i32.const 56
            local.set $pc
          else
            i32.const 57
            local.set $pc
          end
          br $dispatch
        )
          local.get $dc
          local.set $t114
          local.get $t114
          i32.const 1
          i32.add
          local.set $t115
          local.get $t115
          local.set $dc
          call $jn_gc_safepoint
          i32.const 47
          local.set $pc
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_conflict
          local.set $t110
          local.get $__jn_frame
          i32.const 31
          local.get $t110
          call $jn_gc_frame_set_root
          local.get $t110
          local.set $gctmp.111
          local.get $__jn_frame
          i32.const 10
          local.get $gctmp.111
          call $jn_gc_frame_set_root
          local.get $gctmp.111
          local.set $t111
          local.get $__jn_frame
          i32.const 32
          local.get $t111
          call $jn_gc_frame_set_root
          local.get $bi
          local.set $t112
          local.get $t111
          i32.eqz
          if
            unreachable
          end
          local.get $t112
          i32.const 0
          i32.lt_s
          local.get $t112
          local.get $t111
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t111
          i32.const 16
          i32.add
          local.get $t112
          i32.const 4
          i32.mul
          i32.add
          i32.const 1
          i32.store
          call $jnative_exception_pending
          local.set $t113
          local.get $t113
          if
            i32.const 58
            local.set $pc
          else
            i32.const 59
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          i32.const 55
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_SudokuCore_checkWon
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $i i32)
    (local $gctmp.3 i32)
    (local $gctmp.9 i32)
    (local $gctmp.17 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    i32.const 9
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $exit
        )
          i32.const 0
          local.set $i
          i32.const 1
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t0
          local.get $t0
          i32.const 81
          i32.lt_s
          local.set $t1
          local.get $t1
          if
            i32.const 2
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t2
          local.get $__jn_frame
          i32.const 3
          local.get $t2
          call $jn_gc_frame_set_root
          local.get $t2
          local.set $gctmp.3
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.3
          call $jn_gc_frame_set_root
          local.get $gctmp.3
          local.set $t3
          local.get $__jn_frame
          i32.const 4
          local.get $t3
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t4
          local.get $t3
          i32.eqz
          if
            unreachable
          end
          local.get $t4
          i32.const 0
          i32.lt_s
          local.get $t4
          local.get $t3
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t3
          i32.const 16
          i32.add
          local.get $t4
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t5
          call $jnative_exception_pending
          local.set $t6
          local.get $t6
          if
            i32.const 4
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_values
          local.set $t16
          local.get $__jn_frame
          i32.const 7
          local.get $t16
          call $jn_gc_frame_set_root
          local.get $t16
          local.set $gctmp.17
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.17
          call $jn_gc_frame_set_root
          local.get $gctmp.17
          local.set $t17
          local.get $__jn_frame
          i32.const 8
          local.get $t17
          call $jn_gc_frame_set_root
          local.get $t17
          call $_JN_SudokuCore_boardValidComplete_Ai32
          local.set $t18
          call $jnative_exception_pending
          local.set $t19
          local.get $t19
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t5
          i32.const 0
          i32.eq
          local.set $t7
          local.get $t7
          if
            i32.const 6
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_conflict
          local.set $t8
          local.get $__jn_frame
          i32.const 5
          local.get $t8
          call $jn_gc_frame_set_root
          local.get $t8
          local.set $gctmp.9
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.9
          call $jn_gc_frame_set_root
          local.get $gctmp.9
          local.set $t9
          local.get $__jn_frame
          i32.const 6
          local.get $t9
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t10
          local.get $t9
          i32.eqz
          if
            unreachable
          end
          local.get $t10
          i32.const 0
          i32.lt_s
          local.get $t10
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
          local.get $t10
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t11
          call $jnative_exception_pending
          local.set $t12
          local.get $t12
          if
            i32.const 8
            local.set $pc
          else
            i32.const 9
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t11
          i32.const 0
          i32.ne
          local.set $t13
          local.get $t13
          if
            i32.const 10
            local.set $pc
          else
            i32.const 11
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $i
          local.set $t14
          local.get $t14
          i32.const 1
          i32.add
          local.set $t15
          local.get $t15
          local.set $i
          call $jn_gc_safepoint
          i32.const 1
          local.set $pc
          br $dispatch
        )
          br $exit
        )
          local.get $t18
          i32.const 0
          i32.ne
          local.set $t20
          local.get $t20
          if
            i32.const 14
            local.set $pc
          else
            i32.const 15
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_ST_WON
          local.set $t21
          local.get $t21
          global.set $g_jn_static_SudokuCore_status
          i32.const 0
          global.set $g_jn_static_SudokuCore_tickValid
          i32.const 15
          local.set $pc
          br $dispatch
        )
          br $exit
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_SudokuCore_emitStr_CString (param $s i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $s__slot i32)
    (local $i i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    i32.const 4
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $s
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $exit
        )
          local.get $s
          local.set $s__slot
          local.get $__jn_frame
          i32.const 1
          local.get $s__slot
          call $jn_gc_frame_set_root
          i32.const 0
          local.set $i
          i32.const 1
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t0
          local.get $s__slot
          local.set $t1
          local.get $__jn_frame
          i32.const 2
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t1
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t2
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          local.get $s__slot
          local.set $t6
          local.get $__jn_frame
          i32.const 3
          local.get $t6
          call $jn_gc_frame_set_root
          local.get $t6
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t7
          local.get $t7
          if
            i32.const 8
            local.set $pc
          else
            i32.const 9
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          br $exit
        )
          local.get $t1
          call $jn_string_length
          local.set $t3
          call $jnative_exception_pending
          local.set $t4
          local.get $t4
          if
            i32.const 6
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t0
          local.get $t3
          i32.lt_s
          local.set $t5
          local.get $t5
          if
            i32.const 2
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $i
          local.set $t8
          local.get $t6
          local.get $t8
          call $jn_string_char_at
          local.set $t9
          call $jnative_exception_pending
          local.set $t10
          local.get $t10
          if
            i32.const 10
            local.set $pc
          else
            i32.const 11
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t9
          local.set $t11
          local.get $t11
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t12
          local.get $t12
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $i
          local.set $t13
          local.get $t13
          i32.const 1
          i32.add
          local.set $t14
          local.get $t14
          local.set $i
          call $jn_gc_safepoint
          i32.const 1
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_SudokuCore_emitInt_i32 (param $v i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $v__slot i32)
    (local $digs i32)
    (local $gctmp.10 i32)
    (local $n i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    i32.const 6
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $exit
        )
          local.get $v
          local.set $v__slot
          local.get $v__slot
          local.set $t0
          local.get $t0
          i32.const 0
          i32.lt_s
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
          i32.const 45
          call $_JN_SudokuCore_emitChar_i32
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
          local.get $v__slot
          local.set $t5
          local.get $t5
          i32.const 0
          i32.eq
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
          local.get $v__slot
          local.set $t3
          i32.const 0
          local.get $t3
          i32.sub
          local.set $t4
          local.get $t4
          local.set $v__slot
          i32.const 2
          local.set $pc
          br $dispatch
        )
          i32.const 48
          call $_JN_SudokuCore_emitChar_i32
          call $jnative_exception_pending
          local.set $t7
          local.get $t7
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 12
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 12
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 12
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t8
          local.get $t8
          i32.eqz
          if
            unreachable
          end
          local.get $t8
          i32.const 47
          i32.store
          local.get $t8
          i32.const 8
          i32.add
          i32.const 12
          i32.store
          local.get $__jn_frame
          i32.const 2
          local.get $t8
          call $jn_gc_frame_set_root
          call $jnative_exception_pending
          local.set $t9
          local.get $t9
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
          br $exit
        )
          br $exit
        )
          local.get $t8
          local.set $gctmp.10
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.10
          call $jn_gc_frame_set_root
          local.get $gctmp.10
          local.set $t10
          local.get $__jn_frame
          i32.const 3
          local.get $t10
          call $jn_gc_frame_set_root
          local.get $t10
          local.set $digs
          local.get $__jn_frame
          i32.const 0
          local.get $digs
          call $jn_gc_frame_set_root
          i32.const 0
          local.set $n
          i32.const 11
          local.set $pc
          br $dispatch
        )
          local.get $v__slot
          local.set $t11
          local.get $t11
          i32.const 0
          i32.gt_s
          local.set $t12
          local.get $t12
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          local.get $v__slot
          local.set $t13
          local.get $t13
          i32.const 10
          i32.rem_s
          local.set $t14
          call $jnative_exception_pending
          local.set $t15
          local.get $t15
          if
            i32.const 14
            local.set $pc
          else
            i32.const 15
            local.set $pc
          end
          br $dispatch
        )
          i32.const 20
          local.set $pc
          br $dispatch
        )
          br $exit
        )
          local.get $digs
          local.set $t16
          local.get $__jn_frame
          i32.const 4
          local.get $t16
          call $jn_gc_frame_set_root
          local.get $n
          local.set $t17
          local.get $t16
          i32.eqz
          if
            unreachable
          end
          local.get $t17
          i32.const 0
          i32.lt_s
          local.get $t17
          local.get $t16
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t16
          i32.const 16
          i32.add
          local.get $t17
          i32.const 4
          i32.mul
          i32.add
          local.get $t14
          i32.store
          call $jnative_exception_pending
          local.set $t18
          local.get $t18
          if
            i32.const 16
            local.set $pc
          else
            i32.const 17
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $v__slot
          local.set $t19
          local.get $t19
          i32.const 10
          i32.div_s
          local.set $t20
          call $jnative_exception_pending
          local.set $t21
          local.get $t21
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t20
          local.set $v__slot
          local.get $n
          local.set $t22
          local.get $t22
          i32.const 1
          i32.add
          local.set $t23
          local.get $t23
          local.set $n
          call $jn_gc_safepoint
          i32.const 11
          local.set $pc
          br $dispatch
        )
          local.get $n
          local.set $t24
          local.get $t24
          i32.const 0
          i32.gt_s
          local.set $t25
          local.get $t25
          if
            i32.const 21
            local.set $pc
          else
            i32.const 22
            local.set $pc
          end
          br $dispatch
        )
          local.get $n
          local.set $t26
          local.get $t26
          i32.const 1
          i32.sub
          local.set $t27
          local.get $t27
          local.set $n
          local.get $digs
          local.set $t28
          local.get $__jn_frame
          i32.const 5
          local.get $t28
          call $jn_gc_frame_set_root
          local.get $n
          local.set $t29
          local.get $t28
          i32.eqz
          if
            unreachable
          end
          local.get $t29
          i32.const 0
          i32.lt_s
          local.get $t29
          local.get $t28
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t28
          i32.const 16
          i32.add
          local.get $t29
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t30
          call $jnative_exception_pending
          local.set $t31
          local.get $t31
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
          br $exit
        )
          i32.const 48
          local.get $t30
          i32.add
          local.set $t32
          local.get $t32
          call $_JN_SudokuCore_emitChar_i32
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
          call $jn_gc_safepoint
          i32.const 20
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_SudokuCore_emitChar_i32 (param $c i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $c__slot i32)
    (local $gctmp.5 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    i32.const 3
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
          local.get $c
          local.set $c__slot
          global.get $g_jn_static_SudokuCore_saveLen
          local.set $t0
          global.get $g_jn_static_SudokuCore_MAX_SAVE_CHARS
          local.set $t1
          local.get $t0
          local.get $t1
          i32.lt_s
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
          local.get $c__slot
          local.set $t3
          global.get $g_jn_static_SudokuCore_saveBuf
          local.set $t4
          local.get $__jn_frame
          i32.const 1
          local.get $t4
          call $jn_gc_frame_set_root
          local.get $t4
          local.set $gctmp.5
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.5
          call $jn_gc_frame_set_root
          local.get $gctmp.5
          local.set $t5
          local.get $__jn_frame
          i32.const 2
          local.get $t5
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_saveLen
          local.set $t6
          local.get $t5
          i32.eqz
          if
            unreachable
          end
          local.get $t6
          i32.const 0
          i32.lt_s
          local.get $t6
          local.get $t5
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t5
          i32.const 16
          i32.add
          local.get $t6
          i32.const 4
          i32.mul
          i32.add
          local.get $t3
          i32.store
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
          br $exit
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_saveLen
          local.set $t8
          local.get $t8
          i32.const 1
          i32.add
          local.set $t9
          local.get $t9
          global.set $g_jn_static_SudokuCore_saveLen
          i32.const 2
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_SudokuCore_matchLiteral_CString_Ai32_CString (param $data i32) (param $pos i32) (param $lit i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $data__slot i32)
    (local $pos__slot i32)
    (local $lit__slot i32)
    (local $i i32)
    (local $sc.tmp1780132728 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    i32.const 14
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $data
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $pos
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 2
    local.get $lit
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $exit
        )
          local.get $data
          local.set $data__slot
          local.get $__jn_frame
          i32.const 3
          local.get $data__slot
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $pos__slot
          local.get $__jn_frame
          i32.const 4
          local.get $pos__slot
          call $jn_gc_frame_set_root
          local.get $lit
          local.set $lit__slot
          local.get $__jn_frame
          i32.const 5
          local.get $lit__slot
          call $jn_gc_frame_set_root
          i32.const 0
          local.set $i
          i32.const 1
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t0
          local.get $lit__slot
          local.set $t1
          local.get $__jn_frame
          i32.const 6
          local.get $t1
          call $jn_gc_frame_set_root
          local.get $t1
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t2
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          local.get $pos__slot
          local.set $t6
          local.get $__jn_frame
          i32.const 7
          local.get $t6
          call $jn_gc_frame_set_root
          local.get $t6
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t6
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t6
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t7
          call $jnative_exception_pending
          local.set $t8
          local.get $t8
          if
            i32.const 10
            local.set $pc
          else
            i32.const 11
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
          local.get $t1
          call $jn_string_length
          local.set $t3
          call $jnative_exception_pending
          local.set $t4
          local.get $t4
          if
            i32.const 6
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t0
          local.get $t3
          i32.lt_s
          local.set $t5
          local.get $t5
          if
            i32.const 2
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $data__slot
          local.set $t14
          local.get $__jn_frame
          i32.const 9
          local.get $t14
          call $jn_gc_frame_set_root
          local.get $t14
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t15
          local.get $t15
          if
            i32.const 16
            local.set $pc
          else
            i32.const 17
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp1780132728
          local.set $t29
          local.get $t29
          if
            i32.const 26
            local.set $pc
          else
            i32.const 27
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t9
          local.get $__jn_frame
          i32.const 8
          local.get $t9
          call $jn_gc_frame_set_root
          local.get $t9
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t10
          local.get $t10
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t9
          call $jn_string_length
          local.set $t11
          call $jnative_exception_pending
          local.set $t12
          local.get $t12
          if
            i32.const 14
            local.set $pc
          else
            i32.const 15
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t7
          local.get $t11
          i32.ge_s
          local.set $t13
          local.get $t13
          local.set $sc.tmp1780132728
          local.get $t13
          if
            i32.const 9
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t16
          local.get $__jn_frame
          i32.const 10
          local.get $t16
          call $jn_gc_frame_set_root
          local.get $t16
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t16
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t16
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t17
          call $jnative_exception_pending
          local.set $t18
          local.get $t18
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t14
          local.get $t17
          call $jn_string_char_at
          local.set $t19
          call $jnative_exception_pending
          local.set $t20
          local.get $t20
          if
            i32.const 20
            local.set $pc
          else
            i32.const 21
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $lit__slot
          local.set $t21
          local.get $__jn_frame
          i32.const 11
          local.get $t21
          call $jn_gc_frame_set_root
          local.get $t21
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t22
          local.get $t22
          if
            i32.const 22
            local.set $pc
          else
            i32.const 23
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t23
          local.get $t21
          local.get $t23
          call $jn_string_char_at
          local.set $t24
          call $jnative_exception_pending
          local.set $t25
          local.get $t25
          if
            i32.const 24
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t19
          i32.const 65535
          i32.and
          local.set $t26
          local.get $t24
          i32.const 65535
          i32.and
          local.set $t27
          local.get $t26
          local.get $t27
          i32.ne
          local.set $t28
          local.get $t28
          local.set $sc.tmp1780132728
          i32.const 9
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t30
          local.get $__jn_frame
          i32.const 12
          local.get $t30
          call $jn_gc_frame_set_root
          local.get $t30
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t30
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t30
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t31
          call $jnative_exception_pending
          local.set $t32
          local.get $t32
          if
            i32.const 28
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t31
          i32.const 1
          i32.add
          local.set $t33
          local.get $pos__slot
          local.set $t34
          local.get $__jn_frame
          i32.const 13
          local.get $t34
          call $jn_gc_frame_set_root
          local.get $t34
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t34
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t34
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          local.get $t33
          i32.store
          call $jnative_exception_pending
          local.set $t35
          local.get $t35
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t36
          local.get $t36
          i32.const 1
          i32.add
          local.set $t37
          local.get $t37
          local.set $i
          call $jn_gc_safepoint
          i32.const 1
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_parseIntLine_CString_Ai32 (param $data i32) (param $pos i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $data__slot i32)
    (local $pos__slot i32)
    (local $sign i32)
    (local $sc.tmp924477420 i32)
    (local $acc i32)
    (local $digits i32)
    (local $c i32)
    (local $sc.tmp99451533 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    i32.const 18
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $data
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $pos
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb51
        (block $bb50
        (block $bb49
        (block $bb48
        (block $bb47
        (block $bb46
        (block $bb45
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $bb45 $bb46 $bb47 $bb48 $bb49 $bb50 $bb51 $exit
        )
          local.get $data
          local.set $data__slot
          local.get $__jn_frame
          i32.const 2
          local.get $data__slot
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $pos__slot
          local.get $__jn_frame
          i32.const 3
          local.get $pos__slot
          call $jn_gc_frame_set_root
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 1
          local.set $sign
          local.get $pos__slot
          local.set $t0
          local.get $__jn_frame
          i32.const 4
          local.get $t0
          call $jn_gc_frame_set_root
          local.get $t0
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t0
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t0
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
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
          local.get $data__slot
          local.set $t8
          local.get $__jn_frame
          i32.const 6
          local.get $t8
          call $jn_gc_frame_set_root
          local.get $t8
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t9
          local.get $t9
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp924477420
          local.set $t17
          local.get $t17
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t3
          local.get $__jn_frame
          i32.const 5
          local.get $t3
          call $jn_gc_frame_set_root
          local.get $t3
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t4
          local.get $t4
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t3
          call $jn_string_length
          local.set $t5
          call $jnative_exception_pending
          local.set $t6
          local.get $t6
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t1
          local.get $t5
          i32.lt_s
          local.set $t7
          local.get $t7
          local.set $sc.tmp924477420
          local.get $t7
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t10
          local.get $__jn_frame
          i32.const 7
          local.get $t10
          call $jn_gc_frame_set_root
          local.get $t10
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t10
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t10
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t11
          call $jnative_exception_pending
          local.set $t12
          local.get $t12
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t8
          local.get $t11
          call $jn_string_char_at
          local.set $t13
          call $jnative_exception_pending
          local.set $t14
          local.get $t14
          if
            i32.const 13
            local.set $pc
          else
            i32.const 14
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t13
          i32.const 65535
          i32.and
          local.set $t15
          local.get $t15
          i32.const 45
          i32.eq
          local.set $t16
          local.get $t16
          local.set $sc.tmp924477420
          i32.const 2
          local.set $pc
          br $dispatch
        )
          i32.const -1
          local.set $sign
          local.get $pos__slot
          local.set $t19
          local.get $__jn_frame
          i32.const 8
          local.get $t19
          call $jn_gc_frame_set_root
          local.get $t19
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t19
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t19
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
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
          i32.const 0
          local.set $acc
          i32.const 0
          local.set $digits
          i32.const 21
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t20
          i32.const 1
          i32.add
          local.set $t22
          local.get $pos__slot
          local.set $t23
          local.get $__jn_frame
          i32.const 9
          local.get $t23
          call $jn_gc_frame_set_root
          local.get $t23
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t23
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t23
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          local.get $t22
          i32.store
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
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 16
          local.set $pc
          br $dispatch
        )
          local.get $pos__slot
          local.set $t25
          local.get $__jn_frame
          i32.const 10
          local.get $t25
          call $jn_gc_frame_set_root
          local.get $t25
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t25
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t25
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t26
          call $jnative_exception_pending
          local.set $t27
          local.get $t27
          if
            i32.const 24
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          local.get $data__slot
          local.set $t33
          local.get $__jn_frame
          i32.const 12
          local.get $t33
          call $jn_gc_frame_set_root
          local.get $t33
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t34
          local.get $t34
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          local.get $digits
          local.set $t67
          local.get $t67
          i32.const 0
          i32.eq
          local.set $t68
          local.get $t68
          if
            i32.const 50
            local.set $pc
          else
            i32.const 51
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t28
          local.get $__jn_frame
          i32.const 11
          local.get $t28
          call $jn_gc_frame_set_root
          local.get $t28
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t29
          local.get $t29
          if
            i32.const 26
            local.set $pc
          else
            i32.const 27
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t28
          call $jn_string_length
          local.set $t30
          call $jnative_exception_pending
          local.set $t31
          local.get $t31
          if
            i32.const 28
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t26
          local.get $t30
          i32.lt_s
          local.set $t32
          local.get $t32
          if
            i32.const 22
            local.set $pc
          else
            i32.const 23
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t35
          local.get $__jn_frame
          i32.const 13
          local.get $t35
          call $jn_gc_frame_set_root
          local.get $t35
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t35
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t35
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t36
          call $jnative_exception_pending
          local.set $t37
          local.get $t37
          if
            i32.const 32
            local.set $pc
          else
            i32.const 33
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t33
          local.get $t36
          call $jn_string_char_at
          local.set $t38
          call $jnative_exception_pending
          local.set $t39
          local.get $t39
          if
            i32.const 34
            local.set $pc
          else
            i32.const 35
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t38
          local.set $t40
          local.get $t40
          local.set $c
          local.get $c
          local.set $t41
          local.get $t41
          i32.const 10
          i32.eq
          local.set $t42
          local.get $t42
          if
            i32.const 36
            local.set $pc
          else
            i32.const 37
            local.set $pc
          end
          br $dispatch
        )
          local.get $pos__slot
          local.set $t43
          local.get $__jn_frame
          i32.const 14
          local.get $t43
          call $jn_gc_frame_set_root
          local.get $t43
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t43
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t43
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t44
          call $jnative_exception_pending
          local.set $t45
          local.get $t45
          if
            i32.const 38
            local.set $pc
          else
            i32.const 39
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t49
          local.get $t49
          i32.const 48
          i32.lt_s
          local.set $t50
          local.get $t50
          local.set $sc.tmp99451533
          local.get $t50
          if
            i32.const 43
            local.set $pc
          else
            i32.const 42
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t44
          i32.const 1
          i32.add
          local.set $t46
          local.get $pos__slot
          local.set $t47
          local.get $__jn_frame
          i32.const 15
          local.get $t47
          call $jn_gc_frame_set_root
          local.get $t47
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t47
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t47
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          local.get $t46
          i32.store
          call $jnative_exception_pending
          local.set $t48
          local.get $t48
          if
            i32.const 40
            local.set $pc
          else
            i32.const 41
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 23
          local.set $pc
          br $dispatch
        )
          local.get $c
          local.set $t51
          local.get $t51
          i32.const 57
          i32.gt_s
          local.set $t52
          local.get $t52
          local.set $sc.tmp99451533
          i32.const 43
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp99451533
          local.set $t53
          local.get $t53
          if
            i32.const 44
            local.set $pc
          else
            i32.const 45
            local.set $pc
          end
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $acc
          local.set $t54
          local.get $t54
          i32.const 10
          i32.mul
          local.set $t55
          local.get $c
          local.set $t56
          local.get $t56
          i32.const 48
          i32.sub
          local.set $t57
          local.get $t55
          local.get $t57
          i32.add
          local.set $t58
          local.get $t58
          local.set $acc
          local.get $digits
          local.set $t59
          local.get $t59
          i32.const 1
          i32.add
          local.set $t60
          local.get $t60
          local.set $digits
          local.get $pos__slot
          local.set $t61
          local.get $__jn_frame
          i32.const 16
          local.get $t61
          call $jn_gc_frame_set_root
          local.get $t61
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t61
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t61
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t62
          call $jnative_exception_pending
          local.set $t63
          local.get $t63
          if
            i32.const 46
            local.set $pc
          else
            i32.const 47
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t62
          i32.const 1
          i32.add
          local.set $t64
          local.get $pos__slot
          local.set $t65
          local.get $__jn_frame
          i32.const 17
          local.get $t65
          call $jn_gc_frame_set_root
          local.get $t65
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t65
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t65
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          local.get $t64
          i32.store
          call $jnative_exception_pending
          local.set $t66
          local.get $t66
          if
            i32.const 48
            local.set $pc
          else
            i32.const 49
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          call $jn_gc_safepoint
          i32.const 21
          local.set $pc
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $acc
          local.set $t69
          local.get $sign
          local.set $t70
          local.get $t69
          local.get $t70
          i32.mul
          local.set $t71
          local.get $t71
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_parseDigitLine_CString_Ai32_Ai32 (param $data i32) (param $pos i32) (param $out i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $data__slot i32)
    (local $pos__slot i32)
    (local $out__slot i32)
    (local $i i32)
    (local $c i32)
    (local $sc.tmp2116908859 i32)
    (local $sc.tmp917819120 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    i32.const 19
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $data
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $pos
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 2
    local.get $out
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb47
        (block $bb46
        (block $bb45
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $bb45 $bb46 $bb47 $exit
        )
          local.get $data
          local.set $data__slot
          local.get $__jn_frame
          i32.const 3
          local.get $data__slot
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $pos__slot
          local.get $__jn_frame
          i32.const 4
          local.get $pos__slot
          call $jn_gc_frame_set_root
          local.get $out
          local.set $out__slot
          local.get $__jn_frame
          i32.const 5
          local.get $out__slot
          call $jn_gc_frame_set_root
          i32.const 0
          local.set $i
          i32.const 1
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t0
          local.get $t0
          i32.const 81
          i32.lt_s
          local.set $t1
          local.get $t1
          if
            i32.const 2
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $pos__slot
          local.set $t2
          local.get $__jn_frame
          i32.const 6
          local.get $t2
          call $jn_gc_frame_set_root
          local.get $t2
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t2
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t2
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t3
          call $jnative_exception_pending
          local.set $t4
          local.get $t4
          if
            i32.const 4
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          local.get $pos__slot
          local.set $t36
          local.get $__jn_frame
          i32.const 13
          local.get $t36
          call $jn_gc_frame_set_root
          local.get $t36
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t36
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t36
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t37
          call $jnative_exception_pending
          local.set $t38
          local.get $t38
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t5
          local.get $__jn_frame
          i32.const 7
          local.get $t5
          call $jn_gc_frame_set_root
          local.get $t5
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t6
          local.get $t6
          if
            i32.const 6
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t5
          call $jn_string_length
          local.set $t7
          call $jnative_exception_pending
          local.set $t8
          local.get $t8
          if
            i32.const 8
            local.set $pc
          else
            i32.const 9
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t3
          local.get $t7
          i32.ge_s
          local.set $t9
          local.get $t9
          if
            i32.const 10
            local.set $pc
          else
            i32.const 11
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t10
          local.get $__jn_frame
          i32.const 8
          local.get $t10
          call $jn_gc_frame_set_root
          local.get $t10
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t11
          local.get $t11
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t12
          local.get $__jn_frame
          i32.const 9
          local.get $t12
          call $jn_gc_frame_set_root
          local.get $t12
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
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
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t13
          call $jnative_exception_pending
          local.set $t14
          local.get $t14
          if
            i32.const 14
            local.set $pc
          else
            i32.const 15
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t10
          local.get $t13
          call $jn_string_char_at
          local.set $t15
          call $jnative_exception_pending
          local.set $t16
          local.get $t16
          if
            i32.const 16
            local.set $pc
          else
            i32.const 17
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t15
          local.set $t17
          local.get $t17
          local.set $c
          local.get $c
          local.set $t18
          local.get $t18
          i32.const 48
          i32.lt_s
          local.set $t19
          local.get $t19
          local.set $sc.tmp2116908859
          local.get $t19
          if
            i32.const 19
            local.set $pc
          else
            i32.const 18
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t20
          local.get $t20
          i32.const 57
          i32.gt_s
          local.set $t21
          local.get $t21
          local.set $sc.tmp2116908859
          i32.const 19
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp2116908859
          local.set $t22
          local.get $t22
          if
            i32.const 20
            local.set $pc
          else
            i32.const 21
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $c
          local.set $t23
          local.get $t23
          i32.const 48
          i32.sub
          local.set $t24
          local.get $out__slot
          local.set $t25
          local.get $__jn_frame
          i32.const 10
          local.get $t25
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t26
          local.get $t25
          i32.eqz
          if
            unreachable
          end
          local.get $t26
          i32.const 0
          i32.lt_s
          local.get $t26
          local.get $t25
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t25
          i32.const 16
          i32.add
          local.get $t26
          i32.const 4
          i32.mul
          i32.add
          local.get $t24
          i32.store
          call $jnative_exception_pending
          local.set $t27
          local.get $t27
          if
            i32.const 22
            local.set $pc
          else
            i32.const 23
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t28
          local.get $__jn_frame
          i32.const 11
          local.get $t28
          call $jn_gc_frame_set_root
          local.get $t28
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t28
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t28
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t29
          call $jnative_exception_pending
          local.set $t30
          local.get $t30
          if
            i32.const 24
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t29
          i32.const 1
          i32.add
          local.set $t31
          local.get $pos__slot
          local.set $t32
          local.get $__jn_frame
          i32.const 12
          local.get $t32
          call $jn_gc_frame_set_root
          local.get $t32
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t32
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t32
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          local.get $t31
          i32.store
          call $jnative_exception_pending
          local.set $t33
          local.get $t33
          if
            i32.const 26
            local.set $pc
          else
            i32.const 27
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t34
          local.get $t34
          i32.const 1
          i32.add
          local.set $t35
          local.get $t35
          local.set $i
          call $jn_gc_safepoint
          i32.const 1
          local.set $pc
          br $dispatch
        )
          local.get $data__slot
          local.set $t44
          local.get $__jn_frame
          i32.const 15
          local.get $t44
          call $jn_gc_frame_set_root
          local.get $t44
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t45
          local.get $t45
          if
            i32.const 36
            local.set $pc
          else
            i32.const 37
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp917819120
          local.set $t53
          local.get $t53
          if
            i32.const 42
            local.set $pc
          else
            i32.const 43
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t39
          local.get $__jn_frame
          i32.const 14
          local.get $t39
          call $jn_gc_frame_set_root
          local.get $t39
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t40
          local.get $t40
          if
            i32.const 32
            local.set $pc
          else
            i32.const 33
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t39
          call $jn_string_length
          local.set $t41
          call $jnative_exception_pending
          local.set $t42
          local.get $t42
          if
            i32.const 34
            local.set $pc
          else
            i32.const 35
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t37
          local.get $t41
          i32.lt_s
          local.set $t43
          local.get $t43
          local.set $sc.tmp917819120
          local.get $t43
          if
            i32.const 28
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t46
          local.get $__jn_frame
          i32.const 16
          local.get $t46
          call $jn_gc_frame_set_root
          local.get $t46
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t46
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t46
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t47
          call $jnative_exception_pending
          local.set $t48
          local.get $t48
          if
            i32.const 38
            local.set $pc
          else
            i32.const 39
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t44
          local.get $t47
          call $jn_string_char_at
          local.set $t49
          call $jnative_exception_pending
          local.set $t50
          local.get $t50
          if
            i32.const 40
            local.set $pc
          else
            i32.const 41
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t49
          i32.const 65535
          i32.and
          local.set $t51
          local.get $t51
          i32.const 10
          i32.eq
          local.set $t52
          local.get $t52
          local.set $sc.tmp917819120
          i32.const 29
          local.set $pc
          br $dispatch
        )
          local.get $pos__slot
          local.set $t54
          local.get $__jn_frame
          i32.const 17
          local.get $t54
          call $jn_gc_frame_set_root
          local.get $t54
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t54
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t54
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t55
          call $jnative_exception_pending
          local.set $t56
          local.get $t56
          if
            i32.const 44
            local.set $pc
          else
            i32.const 45
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
          local.get $t55
          i32.const 1
          i32.add
          local.set $t57
          local.get $pos__slot
          local.set $t58
          local.get $__jn_frame
          i32.const 18
          local.get $t58
          call $jn_gc_frame_set_root
          local.get $t58
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t58
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t58
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          local.get $t57
          i32.store
          call $jnative_exception_pending
          local.set $t59
          local.get $t59
          if
            i32.const 46
            local.set $pc
          else
            i32.const 47
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 43
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_parseNotesLine_CString_Ai32_Ai32 (param $data i32) (param $pos i32) (param $out i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $data__slot i32)
    (local $pos__slot i32)
    (local $out__slot i32)
    (local $i i32)
    (local $acc i32)
    (local $digits i32)
    (local $c i32)
    (local $sc.tmp1188753216 i32)
    (local $sc.tmp317986356 i32)
    (local $sc.tmp331510866 i32)
    (local $sc.tmp640363654 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    (local $t72 i32)
    (local $t73 i32)
    (local $t74 i32)
    (local $t75 i32)
    (local $t76 i32)
    (local $t77 i32)
    (local $t78 i32)
    (local $t79 i32)
    (local $t80 i32)
    (local $t81 i32)
    (local $t82 i32)
    (local $t83 i32)
    (local $t84 i32)
    (local $t85 i32)
    (local $t86 i32)
    (local $t87 i32)
    (local $t88 i32)
    (local $t89 i32)
    (local $t90 i32)
    (local $t91 i32)
    (local $t92 i32)
    (local $t93 i32)
    (local $t94 i32)
    (local $t95 i32)
    (local $t96 i32)
    i32.const 25
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $data
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $pos
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 2
    local.get $out
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb74
        (block $bb73
        (block $bb72
        (block $bb71
        (block $bb70
        (block $bb69
        (block $bb68
        (block $bb67
        (block $bb66
        (block $bb65
        (block $bb64
        (block $bb63
        (block $bb62
        (block $bb61
        (block $bb60
        (block $bb59
        (block $bb58
        (block $bb57
        (block $bb56
        (block $bb55
        (block $bb54
        (block $bb53
        (block $bb52
        (block $bb51
        (block $bb50
        (block $bb49
        (block $bb48
        (block $bb47
        (block $bb46
        (block $bb45
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $bb45 $bb46 $bb47 $bb48 $bb49 $bb50 $bb51 $bb52 $bb53 $bb54 $bb55 $bb56 $bb57 $bb58 $bb59 $bb60 $bb61 $bb62 $bb63 $bb64 $bb65 $bb66 $bb67 $bb68 $bb69 $bb70 $bb71 $bb72 $bb73 $bb74 $exit
        )
          local.get $data
          local.set $data__slot
          local.get $__jn_frame
          i32.const 3
          local.get $data__slot
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $pos__slot
          local.get $__jn_frame
          i32.const 4
          local.get $pos__slot
          call $jn_gc_frame_set_root
          local.get $out
          local.set $out__slot
          local.get $__jn_frame
          i32.const 5
          local.get $out__slot
          call $jn_gc_frame_set_root
          i32.const 0
          local.set $i
          i32.const 1
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t0
          local.get $t0
          i32.const 81
          i32.lt_s
          local.set $t1
          local.get $t1
          if
            i32.const 2
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $acc
          i32.const 0
          local.set $digits
          i32.const 4
          local.set $pc
          br $dispatch
        )
          local.get $pos__slot
          local.set $t73
          local.get $__jn_frame
          i32.const 19
          local.get $t73
          call $jn_gc_frame_set_root
          local.get $t73
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t73
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t73
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t74
          call $jnative_exception_pending
          local.set $t75
          local.get $t75
          if
            i32.const 57
            local.set $pc
          else
            i32.const 58
            local.set $pc
          end
          br $dispatch
        )
          local.get $pos__slot
          local.set $t2
          local.get $__jn_frame
          i32.const 6
          local.get $t2
          call $jn_gc_frame_set_root
          local.get $t2
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t2
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t2
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t3
          call $jnative_exception_pending
          local.set $t4
          local.get $t4
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          local.get $data__slot
          local.set $t10
          local.get $__jn_frame
          i32.const 8
          local.get $t10
          call $jn_gc_frame_set_root
          local.get $t10
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t11
          local.get $t11
          if
            i32.const 13
            local.set $pc
          else
            i32.const 14
            local.set $pc
          end
          br $dispatch
        )
          local.get $digits
          local.set $t41
          local.get $t41
          i32.const 0
          i32.eq
          local.set $t42
          local.get $t42
          if
            i32.const 31
            local.set $pc
          else
            i32.const 32
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t5
          local.get $__jn_frame
          i32.const 7
          local.get $t5
          call $jn_gc_frame_set_root
          local.get $t5
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t6
          local.get $t6
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t5
          call $jn_string_length
          local.set $t7
          call $jnative_exception_pending
          local.set $t8
          local.get $t8
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t3
          local.get $t7
          i32.lt_s
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t12
          local.get $__jn_frame
          i32.const 9
          local.get $t12
          call $jn_gc_frame_set_root
          local.get $t12
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
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
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t13
          call $jnative_exception_pending
          local.set $t14
          local.get $t14
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t10
          local.get $t13
          call $jn_string_char_at
          local.set $t15
          call $jnative_exception_pending
          local.set $t16
          local.get $t16
          if
            i32.const 17
            local.set $pc
          else
            i32.const 18
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t15
          local.set $t17
          local.get $t17
          local.set $c
          local.get $c
          local.set $t18
          local.get $t18
          i32.const 44
          i32.eq
          local.set $t19
          local.get $t19
          local.set $sc.tmp1188753216
          local.get $t19
          if
            i32.const 20
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t20
          local.get $t20
          i32.const 10
          i32.eq
          local.set $t21
          local.get $t21
          local.set $sc.tmp1188753216
          i32.const 20
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1188753216
          local.set $t22
          local.get $t22
          if
            i32.const 21
            local.set $pc
          else
            i32.const 22
            local.set $pc
          end
          br $dispatch
        )
          i32.const 6
          local.set $pc
          br $dispatch
        )
          local.get $c
          local.set $t23
          local.get $t23
          i32.const 48
          i32.lt_s
          local.set $t24
          local.get $t24
          local.set $sc.tmp317986356
          local.get $t24
          if
            i32.const 24
            local.set $pc
          else
            i32.const 23
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t25
          local.get $t25
          i32.const 57
          i32.gt_s
          local.set $t26
          local.get $t26
          local.set $sc.tmp317986356
          i32.const 24
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp317986356
          local.set $t27
          local.get $t27
          if
            i32.const 25
            local.set $pc
          else
            i32.const 26
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $acc
          local.set $t28
          local.get $t28
          i32.const 10
          i32.mul
          local.set $t29
          local.get $c
          local.set $t30
          local.get $t30
          i32.const 48
          i32.sub
          local.set $t31
          local.get $t29
          local.get $t31
          i32.add
          local.set $t32
          local.get $t32
          local.set $acc
          local.get $digits
          local.set $t33
          local.get $t33
          i32.const 1
          i32.add
          local.set $t34
          local.get $t34
          local.set $digits
          local.get $pos__slot
          local.set $t35
          local.get $__jn_frame
          i32.const 10
          local.get $t35
          call $jn_gc_frame_set_root
          local.get $t35
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t35
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t35
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t36
          call $jnative_exception_pending
          local.set $t37
          local.get $t37
          if
            i32.const 27
            local.set $pc
          else
            i32.const 28
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t36
          i32.const 1
          i32.add
          local.set $t38
          local.get $pos__slot
          local.set $t39
          local.get $__jn_frame
          i32.const 11
          local.get $t39
          call $jn_gc_frame_set_root
          local.get $t39
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t39
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t39
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          local.get $t38
          i32.store
          call $jnative_exception_pending
          local.set $t40
          local.get $t40
          if
            i32.const 29
            local.set $pc
          else
            i32.const 30
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          call $jn_gc_safepoint
          i32.const 4
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $acc
          local.set $t43
          local.get $out__slot
          local.set $t44
          local.get $__jn_frame
          i32.const 12
          local.get $t44
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t45
          local.get $t44
          i32.eqz
          if
            unreachable
          end
          local.get $t45
          i32.const 0
          i32.lt_s
          local.get $t45
          local.get $t44
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t44
          i32.const 16
          i32.add
          local.get $t45
          i32.const 4
          i32.mul
          i32.add
          local.get $t43
          i32.store
          call $jnative_exception_pending
          local.set $t46
          local.get $t46
          if
            i32.const 33
            local.set $pc
          else
            i32.const 34
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t47
          local.get $__jn_frame
          i32.const 13
          local.get $t47
          call $jn_gc_frame_set_root
          local.get $t47
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t47
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t47
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t48
          call $jnative_exception_pending
          local.set $t49
          local.get $t49
          if
            i32.const 37
            local.set $pc
          else
            i32.const 38
            local.set $pc
          end
          br $dispatch
        )
          local.get $data__slot
          local.set $t55
          local.get $__jn_frame
          i32.const 15
          local.get $t55
          call $jn_gc_frame_set_root
          local.get $t55
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t56
          local.get $t56
          if
            i32.const 43
            local.set $pc
          else
            i32.const 44
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp331510866
          local.set $t64
          local.get $t64
          if
            i32.const 49
            local.set $pc
          else
            i32.const 50
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t50
          local.get $__jn_frame
          i32.const 14
          local.get $t50
          call $jn_gc_frame_set_root
          local.get $t50
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t51
          local.get $t51
          if
            i32.const 39
            local.set $pc
          else
            i32.const 40
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t50
          call $jn_string_length
          local.set $t52
          call $jnative_exception_pending
          local.set $t53
          local.get $t53
          if
            i32.const 41
            local.set $pc
          else
            i32.const 42
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t48
          local.get $t52
          i32.lt_s
          local.set $t54
          local.get $t54
          local.set $sc.tmp331510866
          local.get $t54
          if
            i32.const 35
            local.set $pc
          else
            i32.const 36
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t57
          local.get $__jn_frame
          i32.const 16
          local.get $t57
          call $jn_gc_frame_set_root
          local.get $t57
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t57
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t57
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t58
          call $jnative_exception_pending
          local.set $t59
          local.get $t59
          if
            i32.const 45
            local.set $pc
          else
            i32.const 46
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t55
          local.get $t58
          call $jn_string_char_at
          local.set $t60
          call $jnative_exception_pending
          local.set $t61
          local.get $t61
          if
            i32.const 47
            local.set $pc
          else
            i32.const 48
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t60
          i32.const 65535
          i32.and
          local.set $t62
          local.get $t62
          i32.const 44
          i32.eq
          local.set $t63
          local.get $t63
          local.set $sc.tmp331510866
          i32.const 36
          local.set $pc
          br $dispatch
        )
          local.get $pos__slot
          local.set $t65
          local.get $__jn_frame
          i32.const 17
          local.get $t65
          call $jn_gc_frame_set_root
          local.get $t65
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t65
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t65
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t66
          call $jnative_exception_pending
          local.set $t67
          local.get $t67
          if
            i32.const 51
            local.set $pc
          else
            i32.const 52
            local.set $pc
          end
          br $dispatch
        )
          local.get $i
          local.set $t71
          local.get $t71
          i32.const 1
          i32.add
          local.set $t72
          local.get $t72
          local.set $i
          call $jn_gc_safepoint
          i32.const 1
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t66
          i32.const 1
          i32.add
          local.set $t68
          local.get $pos__slot
          local.set $t69
          local.get $__jn_frame
          i32.const 18
          local.get $t69
          call $jn_gc_frame_set_root
          local.get $t69
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t69
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t69
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          local.get $t68
          i32.store
          call $jnative_exception_pending
          local.set $t70
          local.get $t70
          if
            i32.const 53
            local.set $pc
          else
            i32.const 54
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 50
          local.set $pc
          br $dispatch
        )
          local.get $data__slot
          local.set $t81
          local.get $__jn_frame
          i32.const 21
          local.get $t81
          call $jn_gc_frame_set_root
          local.get $t81
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t82
          local.get $t82
          if
            i32.const 63
            local.set $pc
          else
            i32.const 64
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp640363654
          local.set $t90
          local.get $t90
          if
            i32.const 69
            local.set $pc
          else
            i32.const 70
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t76
          local.get $__jn_frame
          i32.const 20
          local.get $t76
          call $jn_gc_frame_set_root
          local.get $t76
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t77
          local.get $t77
          if
            i32.const 59
            local.set $pc
          else
            i32.const 60
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t76
          call $jn_string_length
          local.set $t78
          call $jnative_exception_pending
          local.set $t79
          local.get $t79
          if
            i32.const 61
            local.set $pc
          else
            i32.const 62
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t74
          local.get $t78
          i32.lt_s
          local.set $t80
          local.get $t80
          local.set $sc.tmp640363654
          local.get $t80
          if
            i32.const 55
            local.set $pc
          else
            i32.const 56
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t83
          local.get $__jn_frame
          i32.const 22
          local.get $t83
          call $jn_gc_frame_set_root
          local.get $t83
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t83
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t83
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t84
          call $jnative_exception_pending
          local.set $t85
          local.get $t85
          if
            i32.const 65
            local.set $pc
          else
            i32.const 66
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t81
          local.get $t84
          call $jn_string_char_at
          local.set $t86
          call $jnative_exception_pending
          local.set $t87
          local.get $t87
          if
            i32.const 67
            local.set $pc
          else
            i32.const 68
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t86
          i32.const 65535
          i32.and
          local.set $t88
          local.get $t88
          i32.const 10
          i32.eq
          local.set $t89
          local.get $t89
          local.set $sc.tmp640363654
          i32.const 56
          local.set $pc
          br $dispatch
        )
          local.get $pos__slot
          local.set $t91
          local.get $__jn_frame
          i32.const 23
          local.get $t91
          call $jn_gc_frame_set_root
          local.get $t91
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t91
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t91
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t92
          call $jnative_exception_pending
          local.set $t93
          local.get $t93
          if
            i32.const 71
            local.set $pc
          else
            i32.const 72
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
          local.get $t92
          i32.const 1
          i32.add
          local.set $t94
          local.get $pos__slot
          local.set $t95
          local.get $__jn_frame
          i32.const 24
          local.get $t95
          call $jn_gc_frame_set_root
          local.get $t95
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t95
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t95
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          local.get $t94
          i32.store
          call $jnative_exception_pending
          local.set $t96
          local.get $t96
          if
            i32.const 73
            local.set $pc
          else
            i32.const 74
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 70
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_parseHistoryLine_CString_Ai32_i32_Ai32_Ai32_Ai32_Ai32_Ai32 (param $data i32) (param $pos i32) (param $hl i32) (param $hc i32) (param $hpv i32) (param $hnv i32) (param $hpn i32) (param $hnn i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $data__slot i32)
    (local $pos__slot i32)
    (local $hl__slot i32)
    (local $hc__slot i32)
    (local $hpv__slot i32)
    (local $hnv__slot i32)
    (local $hpn__slot i32)
    (local $hnn__slot i32)
    (local $sc.tmp988458918 i32)
    (local $i i32)
    (local $sc.tmp1990451863 i32)
    (local $sc.tmp1295083508 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    (local $t72 i32)
    (local $t73 i32)
    (local $t74 i32)
    (local $t75 i32)
    (local $t76 i32)
    (local $t77 i32)
    (local $t78 i32)
    (local $t79 i32)
    (local $t80 i32)
    (local $t81 i32)
    (local $t82 i32)
    (local $t83 i32)
    (local $t84 i32)
    (local $t85 i32)
    (local $t86 i32)
    (local $t87 i32)
    (local $t88 i32)
    (local $t89 i32)
    (local $t90 i32)
    (local $t91 i32)
    (local $t92 i32)
    (local $t93 i32)
    (local $t94 i32)
    (local $t95 i32)
    (local $t96 i32)
    (local $t97 i32)
    (local $t98 i32)
    (local $t99 i32)
    (local $t100 i32)
    (local $t101 i32)
    (local $t102 i32)
    (local $t103 i32)
    (local $t104 i32)
    (local $t105 i32)
    (local $t106 i32)
    (local $t107 i32)
    (local $t108 i32)
    (local $t109 i32)
    (local $t110 i32)
    i32.const 43
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $data
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $pos
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 2
    local.get $hc
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 3
    local.get $hpv
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 4
    local.get $hnv
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 5
    local.get $hpn
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 6
    local.get $hnn
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb83
        (block $bb82
        (block $bb81
        (block $bb80
        (block $bb79
        (block $bb78
        (block $bb77
        (block $bb76
        (block $bb75
        (block $bb74
        (block $bb73
        (block $bb72
        (block $bb71
        (block $bb70
        (block $bb69
        (block $bb68
        (block $bb67
        (block $bb66
        (block $bb65
        (block $bb64
        (block $bb63
        (block $bb62
        (block $bb61
        (block $bb60
        (block $bb59
        (block $bb58
        (block $bb57
        (block $bb56
        (block $bb55
        (block $bb54
        (block $bb53
        (block $bb52
        (block $bb51
        (block $bb50
        (block $bb49
        (block $bb48
        (block $bb47
        (block $bb46
        (block $bb45
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $bb45 $bb46 $bb47 $bb48 $bb49 $bb50 $bb51 $bb52 $bb53 $bb54 $bb55 $bb56 $bb57 $bb58 $bb59 $bb60 $bb61 $bb62 $bb63 $bb64 $bb65 $bb66 $bb67 $bb68 $bb69 $bb70 $bb71 $bb72 $bb73 $bb74 $bb75 $bb76 $bb77 $bb78 $bb79 $bb80 $bb81 $bb82 $bb83 $exit
        )
          local.get $data
          local.set $data__slot
          local.get $__jn_frame
          i32.const 7
          local.get $data__slot
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $pos__slot
          local.get $__jn_frame
          i32.const 8
          local.get $pos__slot
          call $jn_gc_frame_set_root
          local.get $hl
          local.set $hl__slot
          local.get $hc
          local.set $hc__slot
          local.get $__jn_frame
          i32.const 9
          local.get $hc__slot
          call $jn_gc_frame_set_root
          local.get $hpv
          local.set $hpv__slot
          local.get $__jn_frame
          i32.const 10
          local.get $hpv__slot
          call $jn_gc_frame_set_root
          local.get $hnv
          local.set $hnv__slot
          local.get $__jn_frame
          i32.const 11
          local.get $hnv__slot
          call $jn_gc_frame_set_root
          local.get $hpn
          local.set $hpn__slot
          local.get $__jn_frame
          i32.const 12
          local.get $hpn__slot
          call $jn_gc_frame_set_root
          local.get $hnn
          local.set $hnn__slot
          local.get $__jn_frame
          i32.const 13
          local.get $hnn__slot
          call $jn_gc_frame_set_root
          local.get $hl__slot
          local.set $t0
          local.get $t0
          i32.const 0
          i32.eq
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
          local.get $pos__slot
          local.set $t2
          local.get $__jn_frame
          i32.const 14
          local.get $t2
          call $jn_gc_frame_set_root
          local.get $t2
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t2
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t2
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t3
          call $jnative_exception_pending
          local.set $t4
          local.get $t4
          if
            i32.const 5
            local.set $pc
          else
            i32.const 6
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $i
          i32.const 23
          local.set $pc
          br $dispatch
        )
          local.get $data__slot
          local.set $t10
          local.get $__jn_frame
          i32.const 16
          local.get $t10
          call $jn_gc_frame_set_root
          local.get $t10
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t11
          local.get $t11
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp988458918
          local.set $t19
          local.get $t19
          if
            i32.const 17
            local.set $pc
          else
            i32.const 18
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t5
          local.get $__jn_frame
          i32.const 15
          local.get $t5
          call $jn_gc_frame_set_root
          local.get $t5
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t6
          local.get $t6
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t5
          call $jn_string_length
          local.set $t7
          call $jnative_exception_pending
          local.set $t8
          local.get $t8
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t3
          local.get $t7
          i32.lt_s
          local.set $t9
          local.get $t9
          local.set $sc.tmp988458918
          local.get $t9
          if
            i32.const 3
            local.set $pc
          else
            i32.const 4
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t12
          local.get $__jn_frame
          i32.const 17
          local.get $t12
          call $jn_gc_frame_set_root
          local.get $t12
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
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
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t13
          call $jnative_exception_pending
          local.set $t14
          local.get $t14
          if
            i32.const 13
            local.set $pc
          else
            i32.const 14
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t10
          local.get $t13
          call $jn_string_char_at
          local.set $t15
          call $jnative_exception_pending
          local.set $t16
          local.get $t16
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t15
          i32.const 65535
          i32.and
          local.set $t17
          local.get $t17
          i32.const 10
          i32.eq
          local.set $t18
          local.get $t18
          local.set $sc.tmp988458918
          i32.const 4
          local.set $pc
          br $dispatch
        )
          local.get $pos__slot
          local.set $t20
          local.get $__jn_frame
          i32.const 18
          local.get $t20
          call $jn_gc_frame_set_root
          local.get $t20
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t20
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t20
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t21
          call $jnative_exception_pending
          local.set $t22
          local.get $t22
          if
            i32.const 19
            local.set $pc
          else
            i32.const 20
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
          local.get $t21
          i32.const 1
          i32.add
          local.set $t23
          local.get $pos__slot
          local.set $t24
          local.get $__jn_frame
          i32.const 19
          local.get $t24
          call $jn_gc_frame_set_root
          local.get $t24
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t24
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t24
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          local.get $t23
          i32.store
          call $jnative_exception_pending
          local.set $t25
          local.get $t25
          if
            i32.const 21
            local.set $pc
          else
            i32.const 22
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 18
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t26
          local.get $hl__slot
          local.set $t27
          local.get $t26
          local.get $t27
          i32.lt_s
          local.set $t28
          local.get $t28
          if
            i32.const 24
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          local.get $data__slot
          local.set $t29
          local.get $__jn_frame
          i32.const 20
          local.get $t29
          call $jn_gc_frame_set_root
          local.get $pos__slot
          local.set $t30
          local.get $__jn_frame
          i32.const 21
          local.get $t30
          call $jn_gc_frame_set_root
          local.get $t29
          local.get $t30
          i32.const 44
          call $_JN_SudokuCore_parseNum_CString_Ai32_i32
          local.set $t31
          call $jnative_exception_pending
          local.set $t32
          local.get $t32
          if
            i32.const 26
            local.set $pc
          else
            i32.const 27
            local.set $pc
          end
          br $dispatch
        )
          local.get $pos__slot
          local.set $t87
          local.get $__jn_frame
          i32.const 37
          local.get $t87
          call $jn_gc_frame_set_root
          local.get $t87
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t87
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t87
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t88
          call $jnative_exception_pending
          local.set $t89
          local.get $t89
          if
            i32.const 66
            local.set $pc
          else
            i32.const 67
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $hc__slot
          local.set $t33
          local.get $__jn_frame
          i32.const 22
          local.get $t33
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t34
          local.get $t33
          i32.eqz
          if
            unreachable
          end
          local.get $t34
          i32.const 0
          i32.lt_s
          local.get $t34
          local.get $t33
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t33
          i32.const 16
          i32.add
          local.get $t34
          i32.const 4
          i32.mul
          i32.add
          local.get $t31
          i32.store
          call $jnative_exception_pending
          local.set $t35
          local.get $t35
          if
            i32.const 28
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_lastError
          local.set $t36
          local.get $t36
          i32.const 0
          i32.ne
          local.set $t37
          local.get $t37
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t38
          local.get $__jn_frame
          i32.const 23
          local.get $t38
          call $jn_gc_frame_set_root
          local.get $pos__slot
          local.set $t39
          local.get $__jn_frame
          i32.const 24
          local.get $t39
          call $jn_gc_frame_set_root
          local.get $t38
          local.get $t39
          i32.const 44
          call $_JN_SudokuCore_parseNum_CString_Ai32_i32
          local.set $t40
          call $jnative_exception_pending
          local.set $t41
          local.get $t41
          if
            i32.const 32
            local.set $pc
          else
            i32.const 33
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $hpv__slot
          local.set $t42
          local.get $__jn_frame
          i32.const 25
          local.get $t42
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t43
          local.get $t42
          i32.eqz
          if
            unreachable
          end
          local.get $t43
          i32.const 0
          i32.lt_s
          local.get $t43
          local.get $t42
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t42
          i32.const 16
          i32.add
          local.get $t43
          i32.const 4
          i32.mul
          i32.add
          local.get $t40
          i32.store
          call $jnative_exception_pending
          local.set $t44
          local.get $t44
          if
            i32.const 34
            local.set $pc
          else
            i32.const 35
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_lastError
          local.set $t45
          local.get $t45
          i32.const 0
          i32.ne
          local.set $t46
          local.get $t46
          if
            i32.const 36
            local.set $pc
          else
            i32.const 37
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t47
          local.get $__jn_frame
          i32.const 26
          local.get $t47
          call $jn_gc_frame_set_root
          local.get $pos__slot
          local.set $t48
          local.get $__jn_frame
          i32.const 27
          local.get $t48
          call $jn_gc_frame_set_root
          local.get $t47
          local.get $t48
          i32.const 44
          call $_JN_SudokuCore_parseNum_CString_Ai32_i32
          local.set $t49
          call $jnative_exception_pending
          local.set $t50
          local.get $t50
          if
            i32.const 38
            local.set $pc
          else
            i32.const 39
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $hnv__slot
          local.set $t51
          local.get $__jn_frame
          i32.const 28
          local.get $t51
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t52
          local.get $t51
          i32.eqz
          if
            unreachable
          end
          local.get $t52
          i32.const 0
          i32.lt_s
          local.get $t52
          local.get $t51
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t51
          i32.const 16
          i32.add
          local.get $t52
          i32.const 4
          i32.mul
          i32.add
          local.get $t49
          i32.store
          call $jnative_exception_pending
          local.set $t53
          local.get $t53
          if
            i32.const 40
            local.set $pc
          else
            i32.const 41
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_lastError
          local.set $t54
          local.get $t54
          i32.const 0
          i32.ne
          local.set $t55
          local.get $t55
          if
            i32.const 42
            local.set $pc
          else
            i32.const 43
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t56
          local.get $__jn_frame
          i32.const 29
          local.get $t56
          call $jn_gc_frame_set_root
          local.get $pos__slot
          local.set $t57
          local.get $__jn_frame
          i32.const 30
          local.get $t57
          call $jn_gc_frame_set_root
          local.get $t56
          local.get $t57
          i32.const 44
          call $_JN_SudokuCore_parseNum_CString_Ai32_i32
          local.set $t58
          call $jnative_exception_pending
          local.set $t59
          local.get $t59
          if
            i32.const 44
            local.set $pc
          else
            i32.const 45
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $hpn__slot
          local.set $t60
          local.get $__jn_frame
          i32.const 31
          local.get $t60
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t61
          local.get $t60
          i32.eqz
          if
            unreachable
          end
          local.get $t61
          i32.const 0
          i32.lt_s
          local.get $t61
          local.get $t60
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t60
          i32.const 16
          i32.add
          local.get $t61
          i32.const 4
          i32.mul
          i32.add
          local.get $t58
          i32.store
          call $jnative_exception_pending
          local.set $t62
          local.get $t62
          if
            i32.const 46
            local.set $pc
          else
            i32.const 47
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_lastError
          local.set $t63
          local.get $t63
          i32.const 0
          i32.ne
          local.set $t64
          local.get $t64
          if
            i32.const 48
            local.set $pc
          else
            i32.const 49
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t65
          local.get $__jn_frame
          i32.const 32
          local.get $t65
          call $jn_gc_frame_set_root
          local.get $pos__slot
          local.set $t66
          local.get $__jn_frame
          i32.const 33
          local.get $t66
          call $jn_gc_frame_set_root
          local.get $t65
          local.get $t66
          i32.const 59
          call $_JN_SudokuCore_parseNum_CString_Ai32_i32
          local.set $t67
          call $jnative_exception_pending
          local.set $t68
          local.get $t68
          if
            i32.const 50
            local.set $pc
          else
            i32.const 51
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $hnn__slot
          local.set $t69
          local.get $__jn_frame
          i32.const 34
          local.get $t69
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t70
          local.get $t69
          i32.eqz
          if
            unreachable
          end
          local.get $t70
          i32.const 0
          i32.lt_s
          local.get $t70
          local.get $t69
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t69
          i32.const 16
          i32.add
          local.get $t70
          i32.const 4
          i32.mul
          i32.add
          local.get $t67
          i32.store
          call $jnative_exception_pending
          local.set $t71
          local.get $t71
          if
            i32.const 52
            local.set $pc
          else
            i32.const 53
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_lastError
          local.set $t72
          local.get $t72
          i32.const 0
          i32.ne
          local.set $t73
          local.get $t73
          if
            i32.const 54
            local.set $pc
          else
            i32.const 55
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $hc__slot
          local.set $t74
          local.get $__jn_frame
          i32.const 35
          local.get $t74
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t75
          local.get $t74
          i32.eqz
          if
            unreachable
          end
          local.get $t75
          i32.const 0
          i32.lt_s
          local.get $t75
          local.get $t74
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t74
          i32.const 16
          i32.add
          local.get $t75
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t76
          call $jnative_exception_pending
          local.set $t77
          local.get $t77
          if
            i32.const 58
            local.set $pc
          else
            i32.const 59
            local.set $pc
          end
          br $dispatch
        )
          local.get $hc__slot
          local.set $t79
          local.get $__jn_frame
          i32.const 36
          local.get $t79
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t80
          local.get $t79
          i32.eqz
          if
            unreachable
          end
          local.get $t80
          i32.const 0
          i32.lt_s
          local.get $t80
          local.get $t79
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t79
          i32.const 16
          i32.add
          local.get $t80
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t81
          call $jnative_exception_pending
          local.set $t82
          local.get $t82
          if
            i32.const 60
            local.set $pc
          else
            i32.const 61
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp1990451863
          local.set $t84
          local.get $t84
          if
            i32.const 62
            local.set $pc
          else
            i32.const 63
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t76
          i32.const 0
          i32.lt_s
          local.set $t78
          local.get $t78
          local.set $sc.tmp1990451863
          local.get $t78
          if
            i32.const 57
            local.set $pc
          else
            i32.const 56
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t81
          i32.const 80
          i32.gt_s
          local.set $t83
          local.get $t83
          local.set $sc.tmp1990451863
          i32.const 57
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t85
          local.get $t85
          i32.const 1
          i32.add
          local.set $t86
          local.get $t86
          local.set $i
          call $jn_gc_safepoint
          i32.const 23
          local.set $pc
          br $dispatch
        )
          local.get $data__slot
          local.set $t95
          local.get $__jn_frame
          i32.const 39
          local.get $t95
          call $jn_gc_frame_set_root
          local.get $t95
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t96
          local.get $t96
          if
            i32.const 72
            local.set $pc
          else
            i32.const 73
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp1295083508
          local.set $t104
          local.get $t104
          if
            i32.const 78
            local.set $pc
          else
            i32.const 79
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t90
          local.get $__jn_frame
          i32.const 38
          local.get $t90
          call $jn_gc_frame_set_root
          local.get $t90
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t91
          local.get $t91
          if
            i32.const 68
            local.set $pc
          else
            i32.const 69
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t90
          call $jn_string_length
          local.set $t92
          call $jnative_exception_pending
          local.set $t93
          local.get $t93
          if
            i32.const 70
            local.set $pc
          else
            i32.const 71
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t88
          local.get $t92
          i32.lt_s
          local.set $t94
          local.get $t94
          local.set $sc.tmp1295083508
          local.get $t94
          if
            i32.const 64
            local.set $pc
          else
            i32.const 65
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t97
          local.get $__jn_frame
          i32.const 40
          local.get $t97
          call $jn_gc_frame_set_root
          local.get $t97
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t97
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t97
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t98
          call $jnative_exception_pending
          local.set $t99
          local.get $t99
          if
            i32.const 74
            local.set $pc
          else
            i32.const 75
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t95
          local.get $t98
          call $jn_string_char_at
          local.set $t100
          call $jnative_exception_pending
          local.set $t101
          local.get $t101
          if
            i32.const 76
            local.set $pc
          else
            i32.const 77
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t100
          i32.const 65535
          i32.and
          local.set $t102
          local.get $t102
          i32.const 10
          i32.eq
          local.set $t103
          local.get $t103
          local.set $sc.tmp1295083508
          i32.const 65
          local.set $pc
          br $dispatch
        )
          local.get $pos__slot
          local.set $t105
          local.get $__jn_frame
          i32.const 41
          local.get $t105
          call $jn_gc_frame_set_root
          local.get $t105
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t105
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t105
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t106
          call $jnative_exception_pending
          local.set $t107
          local.get $t107
          if
            i32.const 80
            local.set $pc
          else
            i32.const 81
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
          local.get $t106
          i32.const 1
          i32.add
          local.set $t108
          local.get $pos__slot
          local.set $t109
          local.get $__jn_frame
          i32.const 42
          local.get $t109
          call $jn_gc_frame_set_root
          local.get $t109
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t109
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t109
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          local.get $t108
          i32.store
          call $jnative_exception_pending
          local.set $t110
          local.get $t110
          if
            i32.const 82
            local.set $pc
          else
            i32.const 83
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 79
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_countSolutionsBoard_Ai32_i32 (param $board i32) (param $limit i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $board__slot i32)
    (local $limit__slot i32)
    (local $found i32)
    (local $sp i32)
    (local $gctmp.4 i32)
    (local $gctmp.7 i32)
    (local $gctmp.10 i32)
    (local $sc.tmp1045941616 i32)
    (local $idx i32)
    (local $gctmp.26 i32)
    (local $d i32)
    (local $gctmp.31 i32)
    (local $gctmp.51 i32)
    (local $next i32)
    (local $gctmp.71 i32)
    (local $gctmp.77 i32)
    (local $gctmp.84 i32)
    (local $gctmp.88 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    (local $t72 i32)
    (local $t73 i32)
    (local $t74 i32)
    (local $t75 i32)
    (local $t76 i32)
    (local $t77 i32)
    (local $t78 i32)
    (local $t79 i32)
    (local $t80 i32)
    (local $t81 i32)
    (local $t82 i32)
    (local $t83 i32)
    (local $t84 i32)
    (local $t85 i32)
    (local $t86 i32)
    (local $t87 i32)
    (local $t88 i32)
    (local $t89 i32)
    (local $t90 i32)
    (local $t91 i32)
    i32.const 38
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $board
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb47
        (block $bb46
        (block $bb45
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $bb45 $bb46 $bb47 $exit
        )
          local.get $board
          local.set $board__slot
          local.get $__jn_frame
          i32.const 1
          local.get $board__slot
          call $jn_gc_frame_set_root
          local.get $limit
          local.set $limit__slot
          i32.const 0
          global.set $g_jn_static_SudokuCore_solveSteps
          i32.const 0
          local.set $found
          i32.const 0
          local.set $sp
          local.get $board__slot
          local.set $t0
          local.get $__jn_frame
          i32.const 12
          local.get $t0
          call $jn_gc_frame_set_root
          local.get $t0
          call $_JN_SudokuCore_findEmpty_Ai32
          local.set $t1
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_btIdx
          local.set $t3
          local.get $__jn_frame
          i32.const 13
          local.get $t3
          call $jn_gc_frame_set_root
          local.get $t3
          local.set $gctmp.4
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.4
          call $jn_gc_frame_set_root
          local.get $gctmp.4
          local.set $t4
          local.get $__jn_frame
          i32.const 14
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
          i32.const 4
          i32.mul
          i32.add
          local.get $t1
          i32.store
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_btDigit
          local.set $t6
          local.get $__jn_frame
          i32.const 15
          local.get $t6
          call $jn_gc_frame_set_root
          local.get $t6
          local.set $gctmp.7
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.7
          call $jn_gc_frame_set_root
          local.get $gctmp.7
          local.set $t7
          local.get $__jn_frame
          i32.const 16
          local.get $t7
          call $jn_gc_frame_set_root
          local.get $t7
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
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
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.const 1
          i32.store
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_btIdx
          local.set $t9
          local.get $__jn_frame
          i32.const 17
          local.get $t9
          call $jn_gc_frame_set_root
          local.get $t9
          local.set $gctmp.10
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.10
          call $jn_gc_frame_set_root
          local.get $gctmp.10
          local.set $t10
          local.get $__jn_frame
          i32.const 18
          local.get $t10
          call $jn_gc_frame_set_root
          local.get $t10
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t10
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t10
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t11
          call $jnative_exception_pending
          local.set $t12
          local.get $t12
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t11
          i32.const 0
          i32.lt_s
          local.set $t13
          local.get $t13
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          local.set $ret
          br $exit
        )
          i32.const 11
          local.set $pc
          br $dispatch
        )
          local.get $sp
          local.set $t14
          local.get $t14
          i32.const 0
          i32.ge_s
          local.set $t15
          local.get $t15
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_solveSteps
          local.set $t16
          local.get $t16
          i32.const 1
          i32.add
          local.set $t17
          local.get $t17
          global.set $g_jn_static_SudokuCore_solveSteps
          global.get $g_jn_static_SudokuCore_solveSteps
          local.set $t18
          local.get $t18
          i32.const 2000000
          i32.gt_s
          local.set $t19
          local.get $t19
          local.set $sc.tmp1045941616
          local.get $t19
          if
            i32.const 15
            local.set $pc
          else
            i32.const 14
            local.set $pc
          end
          br $dispatch
        )
          local.get $found
          local.set $t91
          local.get $t91
          local.set $ret
          br $exit
        )
          local.get $found
          local.set $t20
          local.get $limit__slot
          local.set $t21
          local.get $t20
          local.get $t21
          i32.ge_s
          local.set $t22
          local.get $t22
          local.set $sc.tmp1045941616
          i32.const 15
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1045941616
          local.set $t23
          local.get $t23
          if
            i32.const 16
            local.set $pc
          else
            i32.const 17
            local.set $pc
          end
          br $dispatch
        )
          local.get $found
          local.set $t24
          local.get $t24
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_btIdx
          local.set $t25
          local.get $__jn_frame
          i32.const 19
          local.get $t25
          call $jn_gc_frame_set_root
          local.get $t25
          local.set $gctmp.26
          local.get $__jn_frame
          i32.const 5
          local.get $gctmp.26
          call $jn_gc_frame_set_root
          local.get $gctmp.26
          local.set $t26
          local.get $__jn_frame
          i32.const 20
          local.get $t26
          call $jn_gc_frame_set_root
          local.get $sp
          local.set $t27
          local.get $t26
          i32.eqz
          if
            unreachable
          end
          local.get $t27
          i32.const 0
          i32.lt_s
          local.get $t27
          local.get $t26
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t26
          i32.const 16
          i32.add
          local.get $t27
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t28
          call $jnative_exception_pending
          local.set $t29
          local.get $t29
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t28
          local.set $idx
          global.get $g_jn_static_SudokuCore_btDigit
          local.set $t30
          local.get $__jn_frame
          i32.const 21
          local.get $t30
          call $jn_gc_frame_set_root
          local.get $t30
          local.set $gctmp.31
          local.get $__jn_frame
          i32.const 6
          local.get $gctmp.31
          call $jn_gc_frame_set_root
          local.get $gctmp.31
          local.set $t31
          local.get $__jn_frame
          i32.const 22
          local.get $t31
          call $jn_gc_frame_set_root
          local.get $sp
          local.set $t32
          local.get $t31
          i32.eqz
          if
            unreachable
          end
          local.get $t32
          i32.const 0
          i32.lt_s
          local.get $t32
          local.get $t31
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t31
          i32.const 16
          i32.add
          local.get $t32
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t33
          call $jnative_exception_pending
          local.set $t34
          local.get $t34
          if
            i32.const 20
            local.set $pc
          else
            i32.const 21
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t33
          local.set $d
          local.get $board__slot
          local.set $t35
          local.get $__jn_frame
          i32.const 23
          local.get $t35
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t36
          local.get $t35
          i32.eqz
          if
            unreachable
          end
          local.get $t36
          i32.const 0
          i32.lt_s
          local.get $t36
          local.get $t35
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t35
          i32.const 16
          i32.add
          local.get $t36
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t37
          local.get $t37
          if
            i32.const 22
            local.set $pc
          else
            i32.const 23
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $d
          local.set $t38
          local.get $t38
          i32.const 9
          i32.gt_s
          local.set $t39
          local.get $t39
          if
            i32.const 24
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          local.get $sp
          local.set $t40
          local.get $t40
          i32.const 1
          i32.sub
          local.set $t41
          local.get $t41
          local.set $sp
          i32.const 11
          local.set $pc
          br $dispatch
        )
          local.get $board__slot
          local.set $t42
          local.get $__jn_frame
          i32.const 24
          local.get $t42
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t43
          local.get $d
          local.set $t44
          local.get $t42
          local.get $t43
          local.get $t44
          call $_JN_SudokuCore_canPlace_Ai32_i32_i32
          local.set $t45
          call $jnative_exception_pending
          local.set $t46
          local.get $t46
          if
            i32.const 26
            local.set $pc
          else
            i32.const 27
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t45
          i32.const 0
          i32.eq
          local.set $t47
          local.get $t47
          if
            i32.const 28
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          local.get $d
          local.set $t48
          local.get $t48
          i32.const 1
          i32.add
          local.set $t49
          global.get $g_jn_static_SudokuCore_btDigit
          local.set $t50
          local.get $__jn_frame
          i32.const 25
          local.get $t50
          call $jn_gc_frame_set_root
          local.get $t50
          local.set $gctmp.51
          local.get $__jn_frame
          i32.const 7
          local.get $gctmp.51
          call $jn_gc_frame_set_root
          local.get $gctmp.51
          local.set $t51
          local.get $__jn_frame
          i32.const 26
          local.get $t51
          call $jn_gc_frame_set_root
          local.get $sp
          local.set $t52
          local.get $t51
          i32.eqz
          if
            unreachable
          end
          local.get $t52
          i32.const 0
          i32.lt_s
          local.get $t52
          local.get $t51
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t51
          i32.const 16
          i32.add
          local.get $t52
          i32.const 4
          i32.mul
          i32.add
          local.get $t49
          i32.store
          call $jnative_exception_pending
          local.set $t53
          local.get $t53
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          local.get $d
          local.set $t54
          local.get $board__slot
          local.set $t55
          local.get $__jn_frame
          i32.const 27
          local.get $t55
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t56
          local.get $t55
          i32.eqz
          if
            unreachable
          end
          local.get $t56
          i32.const 0
          i32.lt_s
          local.get $t56
          local.get $t55
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t55
          i32.const 16
          i32.add
          local.get $t56
          i32.const 4
          i32.mul
          i32.add
          local.get $t54
          i32.store
          call $jnative_exception_pending
          local.set $t57
          local.get $t57
          if
            i32.const 32
            local.set $pc
          else
            i32.const 33
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 11
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $board__slot
          local.set $t58
          local.get $__jn_frame
          i32.const 28
          local.get $t58
          call $jn_gc_frame_set_root
          local.get $t58
          call $_JN_SudokuCore_findEmpty_Ai32
          local.set $t59
          call $jnative_exception_pending
          local.set $t60
          local.get $t60
          if
            i32.const 34
            local.set $pc
          else
            i32.const 35
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t59
          local.set $next
          local.get $next
          local.set $t61
          local.get $t61
          i32.const 0
          i32.lt_s
          local.set $t62
          local.get $t62
          if
            i32.const 36
            local.set $pc
          else
            i32.const 37
            local.set $pc
          end
          br $dispatch
        )
          local.get $found
          local.set $t63
          local.get $t63
          i32.const 1
          i32.add
          local.set $t64
          local.get $t64
          local.set $found
          local.get $board__slot
          local.set $t65
          local.get $__jn_frame
          i32.const 29
          local.get $t65
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t66
          local.get $t65
          i32.eqz
          if
            unreachable
          end
          local.get $t66
          i32.const 0
          i32.lt_s
          local.get $t66
          local.get $t65
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t65
          i32.const 16
          i32.add
          local.get $t66
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t67
          local.get $t67
          if
            i32.const 38
            local.set $pc
          else
            i32.const 39
            local.set $pc
          end
          br $dispatch
        )
          local.get $d
          local.set $t74
          local.get $t74
          i32.const 1
          i32.add
          local.set $t75
          global.get $g_jn_static_SudokuCore_btDigit
          local.set $t76
          local.get $__jn_frame
          i32.const 32
          local.get $t76
          call $jn_gc_frame_set_root
          local.get $t76
          local.set $gctmp.77
          local.get $__jn_frame
          i32.const 9
          local.get $gctmp.77
          call $jn_gc_frame_set_root
          local.get $gctmp.77
          local.set $t77
          local.get $__jn_frame
          i32.const 33
          local.get $t77
          call $jn_gc_frame_set_root
          local.get $sp
          local.set $t78
          local.get $t77
          i32.eqz
          if
            unreachable
          end
          local.get $t78
          i32.const 0
          i32.lt_s
          local.get $t78
          local.get $t77
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t77
          i32.const 16
          i32.add
          local.get $t78
          i32.const 4
          i32.mul
          i32.add
          local.get $t75
          i32.store
          call $jnative_exception_pending
          local.set $t79
          local.get $t79
          if
            i32.const 42
            local.set $pc
          else
            i32.const 43
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $d
          local.set $t68
          local.get $t68
          i32.const 1
          i32.add
          local.set $t69
          global.get $g_jn_static_SudokuCore_btDigit
          local.set $t70
          local.get $__jn_frame
          i32.const 30
          local.get $t70
          call $jn_gc_frame_set_root
          local.get $t70
          local.set $gctmp.71
          local.get $__jn_frame
          i32.const 8
          local.get $gctmp.71
          call $jn_gc_frame_set_root
          local.get $gctmp.71
          local.set $t71
          local.get $__jn_frame
          i32.const 31
          local.get $t71
          call $jn_gc_frame_set_root
          local.get $sp
          local.set $t72
          local.get $t71
          i32.eqz
          if
            unreachable
          end
          local.get $t72
          i32.const 0
          i32.lt_s
          local.get $t72
          local.get $t71
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t71
          i32.const 16
          i32.add
          local.get $t72
          i32.const 4
          i32.mul
          i32.add
          local.get $t69
          i32.store
          call $jnative_exception_pending
          local.set $t73
          local.get $t73
          if
            i32.const 40
            local.set $pc
          else
            i32.const 41
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 11
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $sp
          local.set $t80
          local.get $t80
          i32.const 1
          i32.add
          local.set $t81
          local.get $t81
          local.set $sp
          local.get $next
          local.set $t82
          global.get $g_jn_static_SudokuCore_btIdx
          local.set $t83
          local.get $__jn_frame
          i32.const 34
          local.get $t83
          call $jn_gc_frame_set_root
          local.get $t83
          local.set $gctmp.84
          local.get $__jn_frame
          i32.const 10
          local.get $gctmp.84
          call $jn_gc_frame_set_root
          local.get $gctmp.84
          local.set $t84
          local.get $__jn_frame
          i32.const 35
          local.get $t84
          call $jn_gc_frame_set_root
          local.get $sp
          local.set $t85
          local.get $t84
          i32.eqz
          if
            unreachable
          end
          local.get $t85
          i32.const 0
          i32.lt_s
          local.get $t85
          local.get $t84
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t84
          i32.const 16
          i32.add
          local.get $t85
          i32.const 4
          i32.mul
          i32.add
          local.get $t82
          i32.store
          call $jnative_exception_pending
          local.set $t86
          local.get $t86
          if
            i32.const 44
            local.set $pc
          else
            i32.const 45
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_btDigit
          local.set $t87
          local.get $__jn_frame
          i32.const 36
          local.get $t87
          call $jn_gc_frame_set_root
          local.get $t87
          local.set $gctmp.88
          local.get $__jn_frame
          i32.const 11
          local.get $gctmp.88
          call $jn_gc_frame_set_root
          local.get $gctmp.88
          local.set $t88
          local.get $__jn_frame
          i32.const 37
          local.get $t88
          call $jn_gc_frame_set_root
          local.get $sp
          local.set $t89
          local.get $t88
          i32.eqz
          if
            unreachable
          end
          local.get $t89
          i32.const 0
          i32.lt_s
          local.get $t89
          local.get $t88
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t88
          i32.const 16
          i32.add
          local.get $t89
          i32.const 4
          i32.mul
          i32.add
          i32.const 1
          i32.store
          call $jnative_exception_pending
          local.set $t90
          local.get $t90
          if
            i32.const 46
            local.set $pc
          else
            i32.const 47
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          call $jn_gc_safepoint
          i32.const 11
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_copyBoard_Ai32_Ai32 (param $from i32) (param $to i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $from__slot i32)
    (local $to__slot i32)
    (local $i i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    i32.const 6
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $from
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $to
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb7
        (block $bb6
        (block $bb5
        (block $bb4
        (block $bb3
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $exit
        )
          local.get $from
          local.set $from__slot
          local.get $__jn_frame
          i32.const 2
          local.get $from__slot
          call $jn_gc_frame_set_root
          local.get $to
          local.set $to__slot
          local.get $__jn_frame
          i32.const 3
          local.get $to__slot
          call $jn_gc_frame_set_root
          i32.const 0
          local.set $i
          i32.const 1
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t0
          local.get $t0
          i32.const 81
          i32.lt_s
          local.set $t1
          local.get $t1
          if
            i32.const 2
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $from__slot
          local.set $t2
          local.get $__jn_frame
          i32.const 4
          local.get $t2
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t3
          local.get $t2
          i32.eqz
          if
            unreachable
          end
          local.get $t3
          i32.const 0
          i32.lt_s
          local.get $t3
          local.get $t2
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t2
          i32.const 16
          i32.add
          local.get $t3
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t4
          call $jnative_exception_pending
          local.set $t5
          local.get $t5
          if
            i32.const 4
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          br $exit
        )
          local.get $to__slot
          local.set $t6
          local.get $__jn_frame
          i32.const 5
          local.get $t6
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t7
          local.get $t6
          i32.eqz
          if
            unreachable
          end
          local.get $t7
          i32.const 0
          i32.lt_s
          local.get $t7
          local.get $t6
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t6
          i32.const 16
          i32.add
          local.get $t7
          i32.const 4
          i32.mul
          i32.add
          local.get $t4
          i32.store
          call $jnative_exception_pending
          local.set $t8
          local.get $t8
          if
            i32.const 6
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $i
          local.set $t9
          local.get $t9
          i32.const 1
          i32.add
          local.set $t10
          local.get $t10
          local.set $i
          call $jn_gc_safepoint
          i32.const 1
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_SudokuCore_solveBoard_Ai32 (param $board i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $board__slot i32)
    (local $sp i32)
    (local $gctmp.4 i32)
    (local $gctmp.7 i32)
    (local $gctmp.10 i32)
    (local $idx i32)
    (local $gctmp.21 i32)
    (local $d i32)
    (local $gctmp.26 i32)
    (local $gctmp.46 i32)
    (local $next i32)
    (local $gctmp.61 i32)
    (local $gctmp.70 i32)
    (local $gctmp.74 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    (local $t72 i32)
    (local $t73 i32)
    (local $t74 i32)
    (local $t75 i32)
    (local $t76 i32)
    i32.const 34
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $board
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $exit
        )
          local.get $board
          local.set $board__slot
          local.get $__jn_frame
          i32.const 1
          local.get $board__slot
          call $jn_gc_frame_set_root
          i32.const 0
          global.set $g_jn_static_SudokuCore_solveSteps
          i32.const 0
          local.set $sp
          local.get $board__slot
          local.set $t0
          local.get $__jn_frame
          i32.const 11
          local.get $t0
          call $jn_gc_frame_set_root
          local.get $t0
          call $_JN_SudokuCore_findEmpty_Ai32
          local.set $t1
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_btIdx
          local.set $t3
          local.get $__jn_frame
          i32.const 12
          local.get $t3
          call $jn_gc_frame_set_root
          local.get $t3
          local.set $gctmp.4
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.4
          call $jn_gc_frame_set_root
          local.get $gctmp.4
          local.set $t4
          local.get $__jn_frame
          i32.const 13
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
          i32.const 4
          i32.mul
          i32.add
          local.get $t1
          i32.store
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_btDigit
          local.set $t6
          local.get $__jn_frame
          i32.const 14
          local.get $t6
          call $jn_gc_frame_set_root
          local.get $t6
          local.set $gctmp.7
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.7
          call $jn_gc_frame_set_root
          local.get $gctmp.7
          local.set $t7
          local.get $__jn_frame
          i32.const 15
          local.get $t7
          call $jn_gc_frame_set_root
          local.get $t7
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
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
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.const 1
          i32.store
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
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_btIdx
          local.set $t9
          local.get $__jn_frame
          i32.const 16
          local.get $t9
          call $jn_gc_frame_set_root
          local.get $t9
          local.set $gctmp.10
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.10
          call $jn_gc_frame_set_root
          local.get $gctmp.10
          local.set $t10
          local.get $__jn_frame
          i32.const 17
          local.get $t10
          call $jn_gc_frame_set_root
          local.get $t10
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t10
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t10
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t11
          call $jnative_exception_pending
          local.set $t12
          local.get $t12
          if
            i32.const 7
            local.set $pc
          else
            i32.const 8
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t11
          i32.const 0
          i32.lt_s
          local.set $t13
          local.get $t13
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          local.set $ret
          br $exit
        )
          i32.const 11
          local.set $pc
          br $dispatch
        )
          local.get $sp
          local.set $t14
          local.get $t14
          i32.const 0
          i32.ge_s
          local.set $t15
          local.get $t15
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_solveSteps
          local.set $t16
          local.get $t16
          i32.const 1
          i32.add
          local.set $t17
          local.get $t17
          global.set $g_jn_static_SudokuCore_solveSteps
          global.get $g_jn_static_SudokuCore_solveSteps
          local.set $t18
          local.get $t18
          i32.const 2000000
          i32.gt_s
          local.set $t19
          local.get $t19
          if
            i32.const 14
            local.set $pc
          else
            i32.const 15
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_btIdx
          local.set $t20
          local.get $__jn_frame
          i32.const 18
          local.get $t20
          call $jn_gc_frame_set_root
          local.get $t20
          local.set $gctmp.21
          local.get $__jn_frame
          i32.const 5
          local.get $gctmp.21
          call $jn_gc_frame_set_root
          local.get $gctmp.21
          local.set $t21
          local.get $__jn_frame
          i32.const 19
          local.get $t21
          call $jn_gc_frame_set_root
          local.get $sp
          local.set $t22
          local.get $t21
          i32.eqz
          if
            unreachable
          end
          local.get $t22
          i32.const 0
          i32.lt_s
          local.get $t22
          local.get $t21
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t21
          i32.const 16
          i32.add
          local.get $t22
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t23
          call $jnative_exception_pending
          local.set $t24
          local.get $t24
          if
            i32.const 16
            local.set $pc
          else
            i32.const 17
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t23
          local.set $idx
          global.get $g_jn_static_SudokuCore_btDigit
          local.set $t25
          local.get $__jn_frame
          i32.const 20
          local.get $t25
          call $jn_gc_frame_set_root
          local.get $t25
          local.set $gctmp.26
          local.get $__jn_frame
          i32.const 6
          local.get $gctmp.26
          call $jn_gc_frame_set_root
          local.get $gctmp.26
          local.set $t26
          local.get $__jn_frame
          i32.const 21
          local.get $t26
          call $jn_gc_frame_set_root
          local.get $sp
          local.set $t27
          local.get $t26
          i32.eqz
          if
            unreachable
          end
          local.get $t27
          i32.const 0
          i32.lt_s
          local.get $t27
          local.get $t26
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t26
          i32.const 16
          i32.add
          local.get $t27
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t28
          call $jnative_exception_pending
          local.set $t29
          local.get $t29
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t28
          local.set $d
          local.get $board__slot
          local.set $t30
          local.get $__jn_frame
          i32.const 22
          local.get $t30
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t31
          local.get $t30
          i32.eqz
          if
            unreachable
          end
          local.get $t31
          i32.const 0
          i32.lt_s
          local.get $t31
          local.get $t30
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t30
          i32.const 16
          i32.add
          local.get $t31
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t32
          local.get $t32
          if
            i32.const 20
            local.set $pc
          else
            i32.const 21
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $d
          local.set $t33
          local.get $t33
          i32.const 9
          i32.gt_s
          local.set $t34
          local.get $t34
          if
            i32.const 22
            local.set $pc
          else
            i32.const 23
            local.set $pc
          end
          br $dispatch
        )
          local.get $sp
          local.set $t35
          local.get $t35
          i32.const 1
          i32.sub
          local.set $t36
          local.get $t36
          local.set $sp
          i32.const 11
          local.set $pc
          br $dispatch
        )
          local.get $board__slot
          local.set $t37
          local.get $__jn_frame
          i32.const 23
          local.get $t37
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t38
          local.get $d
          local.set $t39
          local.get $t37
          local.get $t38
          local.get $t39
          call $_JN_SudokuCore_canPlace_Ai32_i32_i32
          local.set $t40
          call $jnative_exception_pending
          local.set $t41
          local.get $t41
          if
            i32.const 24
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t40
          i32.const 0
          i32.eq
          local.set $t42
          local.get $t42
          if
            i32.const 26
            local.set $pc
          else
            i32.const 27
            local.set $pc
          end
          br $dispatch
        )
          local.get $d
          local.set $t43
          local.get $t43
          i32.const 1
          i32.add
          local.set $t44
          global.get $g_jn_static_SudokuCore_btDigit
          local.set $t45
          local.get $__jn_frame
          i32.const 24
          local.get $t45
          call $jn_gc_frame_set_root
          local.get $t45
          local.set $gctmp.46
          local.get $__jn_frame
          i32.const 7
          local.get $gctmp.46
          call $jn_gc_frame_set_root
          local.get $gctmp.46
          local.set $t46
          local.get $__jn_frame
          i32.const 25
          local.get $t46
          call $jn_gc_frame_set_root
          local.get $sp
          local.set $t47
          local.get $t46
          i32.eqz
          if
            unreachable
          end
          local.get $t47
          i32.const 0
          i32.lt_s
          local.get $t47
          local.get $t46
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t46
          i32.const 16
          i32.add
          local.get $t47
          i32.const 4
          i32.mul
          i32.add
          local.get $t44
          i32.store
          call $jnative_exception_pending
          local.set $t48
          local.get $t48
          if
            i32.const 28
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          local.get $d
          local.set $t49
          local.get $board__slot
          local.set $t50
          local.get $__jn_frame
          i32.const 26
          local.get $t50
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t51
          local.get $t50
          i32.eqz
          if
            unreachable
          end
          local.get $t51
          i32.const 0
          i32.lt_s
          local.get $t51
          local.get $t50
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t50
          i32.const 16
          i32.add
          local.get $t51
          i32.const 4
          i32.mul
          i32.add
          local.get $t49
          i32.store
          call $jnative_exception_pending
          local.set $t52
          local.get $t52
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 11
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $board__slot
          local.set $t53
          local.get $__jn_frame
          i32.const 27
          local.get $t53
          call $jn_gc_frame_set_root
          local.get $t53
          call $_JN_SudokuCore_findEmpty_Ai32
          local.set $t54
          call $jnative_exception_pending
          local.set $t55
          local.get $t55
          if
            i32.const 32
            local.set $pc
          else
            i32.const 33
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t54
          local.set $next
          local.get $next
          local.set $t56
          local.get $t56
          i32.const 0
          i32.lt_s
          local.set $t57
          local.get $t57
          if
            i32.const 34
            local.set $pc
          else
            i32.const 35
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          local.set $ret
          br $exit
        )
          local.get $d
          local.set $t58
          local.get $t58
          i32.const 1
          i32.add
          local.set $t59
          global.get $g_jn_static_SudokuCore_btDigit
          local.set $t60
          local.get $__jn_frame
          i32.const 28
          local.get $t60
          call $jn_gc_frame_set_root
          local.get $t60
          local.set $gctmp.61
          local.get $__jn_frame
          i32.const 8
          local.get $gctmp.61
          call $jn_gc_frame_set_root
          local.get $gctmp.61
          local.set $t61
          local.get $__jn_frame
          i32.const 29
          local.get $t61
          call $jn_gc_frame_set_root
          local.get $sp
          local.set $t62
          local.get $t61
          i32.eqz
          if
            unreachable
          end
          local.get $t62
          i32.const 0
          i32.lt_s
          local.get $t62
          local.get $t61
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t61
          i32.const 16
          i32.add
          local.get $t62
          i32.const 4
          i32.mul
          i32.add
          local.get $t59
          i32.store
          call $jnative_exception_pending
          local.set $t63
          local.get $t63
          if
            i32.const 36
            local.set $pc
          else
            i32.const 37
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $sp
          local.set $t64
          local.get $t64
          i32.const 1
          i32.add
          local.set $t65
          local.get $t65
          local.set $sp
          local.get $sp
          local.set $t66
          local.get $t66
          i32.const 81
          i32.ge_s
          local.set $t67
          local.get $t67
          if
            i32.const 38
            local.set $pc
          else
            i32.const 39
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $next
          local.set $t68
          global.get $g_jn_static_SudokuCore_btIdx
          local.set $t69
          local.get $__jn_frame
          i32.const 30
          local.get $t69
          call $jn_gc_frame_set_root
          local.get $t69
          local.set $gctmp.70
          local.get $__jn_frame
          i32.const 9
          local.get $gctmp.70
          call $jn_gc_frame_set_root
          local.get $gctmp.70
          local.set $t70
          local.get $__jn_frame
          i32.const 31
          local.get $t70
          call $jn_gc_frame_set_root
          local.get $sp
          local.set $t71
          local.get $t70
          i32.eqz
          if
            unreachable
          end
          local.get $t71
          i32.const 0
          i32.lt_s
          local.get $t71
          local.get $t70
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t70
          i32.const 16
          i32.add
          local.get $t71
          i32.const 4
          i32.mul
          i32.add
          local.get $t68
          i32.store
          call $jnative_exception_pending
          local.set $t72
          local.get $t72
          if
            i32.const 40
            local.set $pc
          else
            i32.const 41
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          global.get $g_jn_static_SudokuCore_btDigit
          local.set $t73
          local.get $__jn_frame
          i32.const 32
          local.get $t73
          call $jn_gc_frame_set_root
          local.get $t73
          local.set $gctmp.74
          local.get $__jn_frame
          i32.const 10
          local.get $gctmp.74
          call $jn_gc_frame_set_root
          local.get $gctmp.74
          local.set $t74
          local.get $__jn_frame
          i32.const 33
          local.get $t74
          call $jn_gc_frame_set_root
          local.get $sp
          local.set $t75
          local.get $t74
          i32.eqz
          if
            unreachable
          end
          local.get $t75
          i32.const 0
          i32.lt_s
          local.get $t75
          local.get $t74
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t74
          i32.const 16
          i32.add
          local.get $t75
          i32.const 4
          i32.mul
          i32.add
          i32.const 1
          i32.store
          call $jnative_exception_pending
          local.set $t76
          local.get $t76
          if
            i32.const 42
            local.set $pc
          else
            i32.const 43
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          call $jn_gc_safepoint
          i32.const 11
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_fillRandomComplete_Ai32 (param $board i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $board__slot i32)
    (local $i i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    i32.const 4
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $board
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb7
        (block $bb6
        (block $bb5
        (block $bb4
        (block $bb3
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $exit
        )
          local.get $board
          local.set $board__slot
          local.get $__jn_frame
          i32.const 1
          local.get $board__slot
          call $jn_gc_frame_set_root
          i32.const 0
          local.set $i
          i32.const 1
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t0
          local.get $t0
          i32.const 81
          i32.lt_s
          local.set $t1
          local.get $t1
          if
            i32.const 2
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $board__slot
          local.set $t2
          local.get $__jn_frame
          i32.const 2
          local.get $t2
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t3
          local.get $t2
          i32.eqz
          if
            unreachable
          end
          local.get $t3
          i32.const 0
          i32.lt_s
          local.get $t3
          local.get $t2
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t2
          i32.const 16
          i32.add
          local.get $t3
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t4
          local.get $t4
          if
            i32.const 4
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          local.get $board__slot
          local.set $t7
          local.get $__jn_frame
          i32.const 3
          local.get $t7
          call $jn_gc_frame_set_root
          local.get $t7
          call $_JN_SudokuCore_fillRandomRec_Ai32
          local.set $t8
          call $jnative_exception_pending
          local.set $t9
          local.get $t9
          if
            i32.const 6
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t5
          local.get $t5
          i32.const 1
          i32.add
          local.set $t6
          local.get $t6
          local.set $i
          call $jn_gc_safepoint
          i32.const 1
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t8
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_digHoles_Ai32_i32 (param $board i32) (param $targetGivens i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $board__slot i32)
    (local $targetGivens__slot i32)
    (local $order i32)
    (local $gctmp.2 i32)
    (local $i i32)
    (local $j i32)
    (local $tmp i32)
    (local $filled i32)
    (local $sc.tmp1226622409 i32)
    (local $idx i32)
    (local $bak i32)
    (local $gctmp.57 i32)
    (local $sols i32)
    (local $gctmp.60 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    (local $t72 i32)
    i32.const 22
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $board
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $exit
        )
          local.get $board
          local.set $board__slot
          local.get $__jn_frame
          i32.const 1
          local.get $board__slot
          call $jn_gc_frame_set_root
          local.get $targetGivens
          local.set $targetGivens__slot
          i32.const 81
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 81
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 81
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
          i32.const 47
          i32.store
          local.get $t0
          i32.const 8
          i32.add
          i32.const 81
          i32.store
          local.get $__jn_frame
          i32.const 6
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
          br $exit
        )
          local.get $t0
          local.set $gctmp.2
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.2
          call $jn_gc_frame_set_root
          local.get $gctmp.2
          local.set $t2
          local.get $__jn_frame
          i32.const 7
          local.get $t2
          call $jn_gc_frame_set_root
          local.get $t2
          local.set $order
          local.get $__jn_frame
          i32.const 2
          local.get $order
          call $jn_gc_frame_set_root
          i32.const 0
          local.set $i
          i32.const 3
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t3
          local.get $t3
          i32.const 81
          i32.lt_s
          local.set $t4
          local.get $t4
          if
            i32.const 4
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          local.get $i
          local.set $t5
          local.get $order
          local.set $t6
          local.get $__jn_frame
          i32.const 8
          local.get $t6
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t7
          local.get $t6
          i32.eqz
          if
            unreachable
          end
          local.get $t7
          i32.const 0
          i32.lt_s
          local.get $t7
          local.get $t6
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t6
          i32.const 16
          i32.add
          local.get $t7
          i32.const 4
          i32.mul
          i32.add
          local.get $t5
          i32.store
          call $jnative_exception_pending
          local.set $t8
          local.get $t8
          if
            i32.const 6
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          i32.const 80
          local.set $i
          i32.const 8
          local.set $pc
          br $dispatch
        )
          br $exit
        )
          local.get $i
          local.set $t9
          local.get $t9
          i32.const 1
          i32.add
          local.set $t10
          local.get $t10
          local.set $i
          call $jn_gc_safepoint
          i32.const 3
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t11
          local.get $t11
          i32.const 0
          i32.gt_s
          local.set $t12
          local.get $t12
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          call $_JN_SudokuCore_rngNext
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
          i32.const 81
          local.set $filled
          i32.const 0
          local.set $i
          i32.const 23
          local.set $pc
          br $dispatch
        )
          br $exit
        )
          local.get $i
          local.set $t15
          local.get $t15
          i32.const 1
          i32.add
          local.set $t16
          local.get $t13
          local.get $t16
          i32.rem_s
          local.set $t17
          call $jnative_exception_pending
          local.set $t18
          local.get $t18
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
          local.get $t17
          local.set $j
          local.get $order
          local.set $t19
          local.get $__jn_frame
          i32.const 9
          local.get $t19
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t20
          local.get $t19
          i32.eqz
          if
            unreachable
          end
          local.get $t20
          i32.const 0
          i32.lt_s
          local.get $t20
          local.get $t19
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t19
          i32.const 16
          i32.add
          local.get $t20
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t21
          call $jnative_exception_pending
          local.set $t22
          local.get $t22
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
          local.get $t21
          local.set $tmp
          local.get $order
          local.set $t23
          local.get $__jn_frame
          i32.const 10
          local.get $t23
          call $jn_gc_frame_set_root
          local.get $j
          local.set $t24
          local.get $t23
          i32.eqz
          if
            unreachable
          end
          local.get $t24
          i32.const 0
          i32.lt_s
          local.get $t24
          local.get $t23
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t23
          i32.const 16
          i32.add
          local.get $t24
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t25
          call $jnative_exception_pending
          local.set $t26
          local.get $t26
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
          local.get $order
          local.set $t27
          local.get $__jn_frame
          i32.const 11
          local.get $t27
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t28
          local.get $t27
          i32.eqz
          if
            unreachable
          end
          local.get $t28
          i32.const 0
          i32.lt_s
          local.get $t28
          local.get $t27
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t27
          i32.const 16
          i32.add
          local.get $t28
          i32.const 4
          i32.mul
          i32.add
          local.get $t25
          i32.store
          call $jnative_exception_pending
          local.set $t29
          local.get $t29
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
          local.get $tmp
          local.set $t30
          local.get $order
          local.set $t31
          local.get $__jn_frame
          i32.const 12
          local.get $t31
          call $jn_gc_frame_set_root
          local.get $j
          local.set $t32
          local.get $t31
          i32.eqz
          if
            unreachable
          end
          local.get $t32
          i32.const 0
          i32.lt_s
          local.get $t32
          local.get $t31
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t31
          i32.const 16
          i32.add
          local.get $t32
          i32.const 4
          i32.mul
          i32.add
          local.get $t30
          i32.store
          call $jnative_exception_pending
          local.set $t33
          local.get $t33
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
          local.get $i
          local.set $t34
          local.get $t34
          i32.const 1
          i32.sub
          local.set $t35
          local.get $t35
          local.set $i
          call $jn_gc_safepoint
          i32.const 8
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t36
          local.get $t36
          i32.const 81
          i32.lt_s
          local.set $t37
          local.get $t37
          local.set $sc.tmp1226622409
          local.get $t37
          if
            i32.const 26
            local.set $pc
          else
            i32.const 27
            local.set $pc
          end
          br $dispatch
        )
          local.get $order
          local.set $t42
          local.get $__jn_frame
          i32.const 13
          local.get $t42
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t43
          local.get $t42
          i32.eqz
          if
            unreachable
          end
          local.get $t43
          i32.const 0
          i32.lt_s
          local.get $t43
          local.get $t42
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t42
          i32.const 16
          i32.add
          local.get $t43
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t44
          call $jnative_exception_pending
          local.set $t45
          local.get $t45
          if
            i32.const 28
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $filled
          local.set $t38
          local.get $targetGivens__slot
          local.set $t39
          local.get $t38
          local.get $t39
          i32.gt_s
          local.set $t40
          local.get $t40
          local.set $sc.tmp1226622409
          i32.const 27
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1226622409
          local.set $t41
          local.get $t41
          if
            i32.const 24
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t44
          local.set $idx
          local.get $board__slot
          local.set $t46
          local.get $__jn_frame
          i32.const 14
          local.get $t46
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t47
          local.get $t46
          i32.eqz
          if
            unreachable
          end
          local.get $t47
          i32.const 0
          i32.lt_s
          local.get $t47
          local.get $t46
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t46
          i32.const 16
          i32.add
          local.get $t47
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t48
          call $jnative_exception_pending
          local.set $t49
          local.get $t49
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t48
          local.set $bak
          local.get $bak
          local.set $t50
          local.get $t50
          i32.const 0
          i32.ne
          local.set $t51
          local.get $t51
          if
            i32.const 32
            local.set $pc
          else
            i32.const 33
            local.set $pc
          end
          br $dispatch
        )
          local.get $board__slot
          local.set $t52
          local.get $__jn_frame
          i32.const 15
          local.get $t52
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t53
          local.get $t52
          i32.eqz
          if
            unreachable
          end
          local.get $t53
          i32.const 0
          i32.lt_s
          local.get $t53
          local.get $t52
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t52
          i32.const 16
          i32.add
          local.get $t53
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t54
          local.get $t54
          if
            i32.const 34
            local.set $pc
          else
            i32.const 35
            local.set $pc
          end
          br $dispatch
        )
          local.get $i
          local.set $t71
          local.get $t71
          i32.const 1
          i32.add
          local.set $t72
          local.get $t72
          local.set $i
          call $jn_gc_safepoint
          i32.const 23
          local.set $pc
          br $dispatch
        )
          br $exit
        )
          local.get $board__slot
          local.set $t55
          local.get $__jn_frame
          i32.const 16
          local.get $t55
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_work
          local.set $t56
          local.get $__jn_frame
          i32.const 17
          local.get $t56
          call $jn_gc_frame_set_root
          local.get $t56
          local.set $gctmp.57
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.57
          call $jn_gc_frame_set_root
          local.get $gctmp.57
          local.set $t57
          local.get $__jn_frame
          i32.const 18
          local.get $t57
          call $jn_gc_frame_set_root
          local.get $t55
          local.get $t57
          call $_JN_SudokuCore_copyBoard_Ai32_Ai32
          call $jnative_exception_pending
          local.set $t58
          local.get $t58
          if
            i32.const 36
            local.set $pc
          else
            i32.const 37
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          global.get $g_jn_static_SudokuCore_work
          local.set $t59
          local.get $__jn_frame
          i32.const 19
          local.get $t59
          call $jn_gc_frame_set_root
          local.get $t59
          local.set $gctmp.60
          local.get $__jn_frame
          i32.const 5
          local.get $gctmp.60
          call $jn_gc_frame_set_root
          local.get $gctmp.60
          local.set $t60
          local.get $__jn_frame
          i32.const 20
          local.get $t60
          call $jn_gc_frame_set_root
          local.get $t60
          i32.const 2
          call $_JN_SudokuCore_countSolutionsBoard_Ai32_i32
          local.set $t61
          call $jnative_exception_pending
          local.set $t62
          local.get $t62
          if
            i32.const 38
            local.set $pc
          else
            i32.const 39
            local.set $pc
          end
          br $dispatch
        )
          br $exit
        )
          local.get $t61
          local.set $sols
          local.get $sols
          local.set $t63
          local.get $t63
          i32.const 1
          i32.ne
          local.set $t64
          local.get $t64
          if
            i32.const 40
            local.set $pc
          else
            i32.const 42
            local.set $pc
          end
          br $dispatch
        )
          local.get $bak
          local.set $t65
          local.get $board__slot
          local.set $t66
          local.get $__jn_frame
          i32.const 21
          local.get $t66
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t67
          local.get $t66
          i32.eqz
          if
            unreachable
          end
          local.get $t67
          i32.const 0
          i32.lt_s
          local.get $t67
          local.get $t66
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t66
          i32.const 16
          i32.add
          local.get $t67
          i32.const 4
          i32.mul
          i32.add
          local.get $t65
          i32.store
          call $jnative_exception_pending
          local.set $t68
          local.get $t68
          if
            i32.const 43
            local.set $pc
          else
            i32.const 44
            local.set $pc
          end
          br $dispatch
        )
          i32.const 33
          local.set $pc
          br $dispatch
        )
          local.get $filled
          local.set $t69
          local.get $t69
          i32.const 1
          i32.sub
          local.set $t70
          local.get $t70
          local.set $filled
          i32.const 41
          local.set $pc
          br $dispatch
        )
          br $exit
        )
          i32.const 41
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
  )
  (func $_JN_SudokuCore_catalogPuzzle_i32 (param $puzzleIndex i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $puzzleIndex__slot i32)
    (local $gctmp.3 i32)
    (local $gctmp.7 i32)
    (local $gctmp.11 i32)
    (local $gctmp.15 i32)
    (local $gctmp.19 i32)
    (local $gctmp.23 i32)
    (local $gctmp.27 i32)
    (local $gctmp.31 i32)
    (local $gctmp.35 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    i32.const 27
    call $jn_gc_frame_push
    local.set $__jn_frame
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $exit
        )
          local.get $puzzleIndex
          local.set $puzzleIndex__slot
          local.get $puzzleIndex__slot
          local.set $t0
          local.get $t0
          i32.const 0
          i32.eq
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
          i32.const 2053
          i32.const 81
          call $jn_str
          local.set $t2
          local.get $__jn_frame
          i32.const 9
          local.get $t2
          call $jn_gc_frame_set_root
          local.get $t2
          local.set $gctmp.3
          local.get $__jn_frame
          i32.const 0
          local.get $gctmp.3
          call $jn_gc_frame_set_root
          local.get $gctmp.3
          local.set $t3
          local.get $__jn_frame
          i32.const 10
          local.get $t3
          call $jn_gc_frame_set_root
          local.get $t3
          local.set $ret
          br $exit
        )
          local.get $puzzleIndex__slot
          local.set $t4
          local.get $t4
          i32.const 1
          i32.eq
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
          i32.const 2134
          i32.const 81
          call $jn_str
          local.set $t6
          local.get $__jn_frame
          i32.const 11
          local.get $t6
          call $jn_gc_frame_set_root
          local.get $t6
          local.set $gctmp.7
          local.get $__jn_frame
          i32.const 1
          local.get $gctmp.7
          call $jn_gc_frame_set_root
          local.get $gctmp.7
          local.set $t7
          local.get $__jn_frame
          i32.const 12
          local.get $t7
          call $jn_gc_frame_set_root
          local.get $t7
          local.set $ret
          br $exit
        )
          local.get $puzzleIndex__slot
          local.set $t8
          local.get $t8
          i32.const 2
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
          i32.const 2215
          i32.const 81
          call $jn_str
          local.set $t10
          local.get $__jn_frame
          i32.const 13
          local.get $t10
          call $jn_gc_frame_set_root
          local.get $t10
          local.set $gctmp.11
          local.get $__jn_frame
          i32.const 2
          local.get $gctmp.11
          call $jn_gc_frame_set_root
          local.get $gctmp.11
          local.set $t11
          local.get $__jn_frame
          i32.const 14
          local.get $t11
          call $jn_gc_frame_set_root
          local.get $t11
          local.set $ret
          br $exit
        )
          local.get $puzzleIndex__slot
          local.set $t12
          local.get $t12
          i32.const 3
          i32.eq
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
          i32.const 2296
          i32.const 81
          call $jn_str
          local.set $t14
          local.get $__jn_frame
          i32.const 15
          local.get $t14
          call $jn_gc_frame_set_root
          local.get $t14
          local.set $gctmp.15
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.15
          call $jn_gc_frame_set_root
          local.get $gctmp.15
          local.set $t15
          local.get $__jn_frame
          i32.const 16
          local.get $t15
          call $jn_gc_frame_set_root
          local.get $t15
          local.set $ret
          br $exit
        )
          local.get $puzzleIndex__slot
          local.set $t16
          local.get $t16
          i32.const 4
          i32.eq
          local.set $t17
          local.get $t17
          if
            i32.const 9
            local.set $pc
          else
            i32.const 10
            local.set $pc
          end
          br $dispatch
        )
          i32.const 2377
          i32.const 81
          call $jn_str
          local.set $t18
          local.get $__jn_frame
          i32.const 17
          local.get $t18
          call $jn_gc_frame_set_root
          local.get $t18
          local.set $gctmp.19
          local.get $__jn_frame
          i32.const 4
          local.get $gctmp.19
          call $jn_gc_frame_set_root
          local.get $gctmp.19
          local.set $t19
          local.get $__jn_frame
          i32.const 18
          local.get $t19
          call $jn_gc_frame_set_root
          local.get $t19
          local.set $ret
          br $exit
        )
          local.get $puzzleIndex__slot
          local.set $t20
          local.get $t20
          i32.const 5
          i32.eq
          local.set $t21
          local.get $t21
          if
            i32.const 11
            local.set $pc
          else
            i32.const 12
            local.set $pc
          end
          br $dispatch
        )
          i32.const 2458
          i32.const 81
          call $jn_str
          local.set $t22
          local.get $__jn_frame
          i32.const 19
          local.get $t22
          call $jn_gc_frame_set_root
          local.get $t22
          local.set $gctmp.23
          local.get $__jn_frame
          i32.const 5
          local.get $gctmp.23
          call $jn_gc_frame_set_root
          local.get $gctmp.23
          local.set $t23
          local.get $__jn_frame
          i32.const 20
          local.get $t23
          call $jn_gc_frame_set_root
          local.get $t23
          local.set $ret
          br $exit
        )
          local.get $puzzleIndex__slot
          local.set $t24
          local.get $t24
          i32.const 6
          i32.eq
          local.set $t25
          local.get $t25
          if
            i32.const 13
            local.set $pc
          else
            i32.const 14
            local.set $pc
          end
          br $dispatch
        )
          i32.const 2539
          i32.const 81
          call $jn_str
          local.set $t26
          local.get $__jn_frame
          i32.const 21
          local.get $t26
          call $jn_gc_frame_set_root
          local.get $t26
          local.set $gctmp.27
          local.get $__jn_frame
          i32.const 6
          local.get $gctmp.27
          call $jn_gc_frame_set_root
          local.get $gctmp.27
          local.set $t27
          local.get $__jn_frame
          i32.const 22
          local.get $t27
          call $jn_gc_frame_set_root
          local.get $t27
          local.set $ret
          br $exit
        )
          local.get $puzzleIndex__slot
          local.set $t28
          local.get $t28
          i32.const 7
          i32.eq
          local.set $t29
          local.get $t29
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          i32.const 2620
          i32.const 81
          call $jn_str
          local.set $t30
          local.get $__jn_frame
          i32.const 23
          local.get $t30
          call $jn_gc_frame_set_root
          local.get $t30
          local.set $gctmp.31
          local.get $__jn_frame
          i32.const 7
          local.get $gctmp.31
          call $jn_gc_frame_set_root
          local.get $gctmp.31
          local.set $t31
          local.get $__jn_frame
          i32.const 24
          local.get $t31
          call $jn_gc_frame_set_root
          local.get $t31
          local.set $ret
          br $exit
        )
          local.get $puzzleIndex__slot
          local.set $t32
          local.get $t32
          i32.const 8
          i32.eq
          local.set $t33
          local.get $t33
          if
            i32.const 17
            local.set $pc
          else
            i32.const 18
            local.set $pc
          end
          br $dispatch
        )
          i32.const 2701
          i32.const 81
          call $jn_str
          local.set $t34
          local.get $__jn_frame
          i32.const 25
          local.get $t34
          call $jn_gc_frame_set_root
          local.get $t34
          local.set $gctmp.35
          local.get $__jn_frame
          i32.const 8
          local.get $gctmp.35
          call $jn_gc_frame_set_root
          local.get $gctmp.35
          local.set $t35
          local.get $__jn_frame
          i32.const 26
          local.get $t35
          call $jn_gc_frame_set_root
          local.get $t35
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $ret
          br $exit
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_parseNum_CString_Ai32_i32 (param $data i32) (param $pos i32) (param $stop i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $data__slot i32)
    (local $pos__slot i32)
    (local $stop__slot i32)
    (local $acc i32)
    (local $digits i32)
    (local $c i32)
    (local $sc.tmp1227074340 i32)
    (local $sc.tmp1154002927 i32)
    (local $sc.tmp2070529722 i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    i32.const 16
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $data
    call $jn_gc_frame_set_root
    local.get $__jn_frame
    i32.const 1
    local.get $pos
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb49
        (block $bb48
        (block $bb47
        (block $bb46
        (block $bb45
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $bb45 $bb46 $bb47 $bb48 $bb49 $exit
        )
          local.get $data
          local.set $data__slot
          local.get $__jn_frame
          i32.const 2
          local.get $data__slot
          call $jn_gc_frame_set_root
          local.get $pos
          local.set $pos__slot
          local.get $__jn_frame
          i32.const 3
          local.get $pos__slot
          call $jn_gc_frame_set_root
          local.get $stop
          local.set $stop__slot
          i32.const 0
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $acc
          i32.const 0
          local.set $digits
          i32.const 1
          local.set $pc
          br $dispatch
        )
          local.get $pos__slot
          local.set $t0
          local.get $__jn_frame
          i32.const 4
          local.get $t0
          call $jn_gc_frame_set_root
          local.get $t0
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t0
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t0
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t1
          call $jnative_exception_pending
          local.set $t2
          local.get $t2
          if
            i32.const 4
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          local.get $data__slot
          local.set $t8
          local.get $__jn_frame
          i32.const 6
          local.get $t8
          call $jn_gc_frame_set_root
          local.get $t8
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t9
          local.get $t9
          if
            i32.const 10
            local.set $pc
          else
            i32.const 11
            local.set $pc
          end
          br $dispatch
        )
          local.get $digits
          local.set $t40
          local.get $t40
          i32.const 0
          i32.eq
          local.set $t41
          local.get $t41
          if
            i32.const 28
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t3
          local.get $__jn_frame
          i32.const 5
          local.get $t3
          call $jn_gc_frame_set_root
          local.get $t3
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t4
          local.get $t4
          if
            i32.const 6
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t3
          call $jn_string_length
          local.set $t5
          call $jnative_exception_pending
          local.set $t6
          local.get $t6
          if
            i32.const 8
            local.set $pc
          else
            i32.const 9
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t1
          local.get $t5
          i32.lt_s
          local.set $t7
          local.get $t7
          if
            i32.const 2
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t10
          local.get $__jn_frame
          i32.const 7
          local.get $t10
          call $jn_gc_frame_set_root
          local.get $t10
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t10
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t10
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t11
          call $jnative_exception_pending
          local.set $t12
          local.get $t12
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t8
          local.get $t11
          call $jn_string_char_at
          local.set $t13
          call $jnative_exception_pending
          local.set $t14
          local.get $t14
          if
            i32.const 14
            local.set $pc
          else
            i32.const 15
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t13
          local.set $t15
          local.get $t15
          local.set $c
          local.get $c
          local.set $t16
          local.get $stop__slot
          local.set $t17
          local.get $t16
          local.get $t17
          i32.eq
          local.set $t18
          local.get $t18
          local.set $sc.tmp1227074340
          local.get $t18
          if
            i32.const 17
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t19
          local.get $t19
          i32.const 10
          i32.eq
          local.set $t20
          local.get $t20
          local.set $sc.tmp1227074340
          i32.const 17
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1227074340
          local.set $t21
          local.get $t21
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          i32.const 3
          local.set $pc
          br $dispatch
        )
          local.get $c
          local.set $t22
          local.get $t22
          i32.const 48
          i32.lt_s
          local.set $t23
          local.get $t23
          local.set $sc.tmp1154002927
          local.get $t23
          if
            i32.const 21
            local.set $pc
          else
            i32.const 20
            local.set $pc
          end
          br $dispatch
        )
          local.get $c
          local.set $t24
          local.get $t24
          i32.const 57
          i32.gt_s
          local.set $t25
          local.get $t25
          local.set $sc.tmp1154002927
          i32.const 21
          local.set $pc
          br $dispatch
        )
          local.get $sc.tmp1154002927
          local.set $t26
          local.get $t26
          if
            i32.const 22
            local.set $pc
          else
            i32.const 23
            local.set $pc
          end
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $acc
          local.set $t27
          local.get $t27
          i32.const 10
          i32.mul
          local.set $t28
          local.get $c
          local.set $t29
          local.get $t29
          i32.const 48
          i32.sub
          local.set $t30
          local.get $t28
          local.get $t30
          i32.add
          local.set $t31
          local.get $t31
          local.set $acc
          local.get $digits
          local.set $t32
          local.get $t32
          i32.const 1
          i32.add
          local.set $t33
          local.get $t33
          local.set $digits
          local.get $pos__slot
          local.set $t34
          local.get $__jn_frame
          i32.const 8
          local.get $t34
          call $jn_gc_frame_set_root
          local.get $t34
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t34
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t34
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t35
          call $jnative_exception_pending
          local.set $t36
          local.get $t36
          if
            i32.const 24
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t35
          i32.const 1
          i32.add
          local.set $t37
          local.get $pos__slot
          local.set $t38
          local.get $__jn_frame
          i32.const 9
          local.get $t38
          call $jn_gc_frame_set_root
          local.get $t38
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t38
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t38
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          local.get $t37
          i32.store
          call $jnative_exception_pending
          local.set $t39
          local.get $t39
          if
            i32.const 26
            local.set $pc
          else
            i32.const 27
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          call $jn_gc_safepoint
          i32.const 1
          local.set $pc
          br $dispatch
        )
          i32.const 5
          global.set $g_jn_static_SudokuCore_lastError
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t42
          local.get $__jn_frame
          i32.const 10
          local.get $t42
          call $jn_gc_frame_set_root
          local.get $t42
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t42
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t42
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t43
          call $jnative_exception_pending
          local.set $t44
          local.get $t44
          if
            i32.const 32
            local.set $pc
          else
            i32.const 33
            local.set $pc
          end
          br $dispatch
        )
          local.get $data__slot
          local.set $t50
          local.get $__jn_frame
          i32.const 12
          local.get $t50
          call $jn_gc_frame_set_root
          local.get $t50
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t51
          local.get $t51
          if
            i32.const 38
            local.set $pc
          else
            i32.const 39
            local.set $pc
          end
          br $dispatch
        )
          local.get $sc.tmp2070529722
          local.set $t60
          local.get $t60
          if
            i32.const 44
            local.set $pc
          else
            i32.const 45
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $data__slot
          local.set $t45
          local.get $__jn_frame
          i32.const 11
          local.get $t45
          call $jn_gc_frame_set_root
          local.get $t45
          i32.eqz
          if
            call $jnative_throw_npe
          end
          call $jnative_exception_pending
          local.set $t46
          local.get $t46
          if
            i32.const 34
            local.set $pc
          else
            i32.const 35
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t45
          call $jn_string_length
          local.set $t47
          call $jnative_exception_pending
          local.set $t48
          local.get $t48
          if
            i32.const 36
            local.set $pc
          else
            i32.const 37
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t43
          local.get $t47
          i32.lt_s
          local.set $t49
          local.get $t49
          local.set $sc.tmp2070529722
          local.get $t49
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $pos__slot
          local.set $t52
          local.get $__jn_frame
          i32.const 13
          local.get $t52
          call $jn_gc_frame_set_root
          local.get $t52
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t52
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t52
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t53
          call $jnative_exception_pending
          local.set $t54
          local.get $t54
          if
            i32.const 40
            local.set $pc
          else
            i32.const 41
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t50
          local.get $t53
          call $jn_string_char_at
          local.set $t55
          call $jnative_exception_pending
          local.set $t56
          local.get $t56
          if
            i32.const 42
            local.set $pc
          else
            i32.const 43
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $stop__slot
          local.set $t57
          local.get $t55
          i32.const 65535
          i32.and
          local.set $t58
          local.get $t58
          local.get $t57
          i32.eq
          local.set $t59
          local.get $t59
          local.set $sc.tmp2070529722
          i32.const 31
          local.set $pc
          br $dispatch
        )
          local.get $pos__slot
          local.set $t61
          local.get $__jn_frame
          i32.const 14
          local.get $t61
          call $jn_gc_frame_set_root
          local.get $t61
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t61
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t61
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t62
          call $jnative_exception_pending
          local.set $t63
          local.get $t63
          if
            i32.const 46
            local.set $pc
          else
            i32.const 47
            local.set $pc
          end
          br $dispatch
        )
          local.get $acc
          local.set $t67
          local.get $t67
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t62
          i32.const 1
          i32.add
          local.set $t64
          local.get $pos__slot
          local.set $t65
          local.get $__jn_frame
          i32.const 15
          local.get $t65
          call $jn_gc_frame_set_root
          local.get $t65
          i32.eqz
          if
            unreachable
          end
          i32.const 0
          i32.const 0
          i32.lt_s
          i32.const 0
          local.get $t65
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t65
          i32.const 16
          i32.add
          i32.const 0
          i32.const 4
          i32.mul
          i32.add
          local.get $t64
          i32.store
          call $jnative_exception_pending
          local.set $t66
          local.get $t66
          if
            i32.const 48
            local.set $pc
          else
            i32.const 49
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 45
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_findEmpty_Ai32 (param $board i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $board__slot i32)
    (local $i i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    i32.const 3
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $board
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb7
        (block $bb6
        (block $bb5
        (block $bb4
        (block $bb3
        (block $bb2
        (block $bb1
        (block $bb0
          local.get $pc
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $exit
        )
          local.get $board
          local.set $board__slot
          local.get $__jn_frame
          i32.const 1
          local.get $board__slot
          call $jn_gc_frame_set_root
          i32.const 0
          local.set $i
          i32.const 1
          local.set $pc
          br $dispatch
        )
          local.get $i
          local.set $t0
          local.get $t0
          i32.const 81
          i32.lt_s
          local.set $t1
          local.get $t1
          if
            i32.const 2
            local.set $pc
          else
            i32.const 3
            local.set $pc
          end
          br $dispatch
        )
          local.get $board__slot
          local.set $t2
          local.get $__jn_frame
          i32.const 2
          local.get $t2
          call $jn_gc_frame_set_root
          local.get $i
          local.set $t3
          local.get $t2
          i32.eqz
          if
            unreachable
          end
          local.get $t3
          i32.const 0
          i32.lt_s
          local.get $t3
          local.get $t2
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t2
          i32.const 16
          i32.add
          local.get $t3
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t4
          call $jnative_exception_pending
          local.set $t5
          local.get $t5
          if
            i32.const 4
            local.set $pc
          else
            i32.const 5
            local.set $pc
          end
          br $dispatch
        )
          i32.const -1
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t4
          i32.const 0
          i32.eq
          local.set $t6
          local.get $t6
          if
            i32.const 6
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          local.get $i
          local.set $t7
          local.get $t7
          local.set $ret
          br $exit
        )
          local.get $i
          local.set $t8
          local.get $t8
          i32.const 1
          i32.add
          local.set $t9
          local.get $t9
          local.set $i
          call $jn_gc_safepoint
          i32.const 1
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_canPlace_Ai32_i32_i32 (param $board i32) (param $idx i32) (param $d i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $board__slot i32)
    (local $idx__slot i32)
    (local $d__slot i32)
    (local $r i32)
    (local $c i32)
    (local $j i32)
    (local $br i32)
    (local $bc i32)
    (local $dr i32)
    (local $dc i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    i32.const 5
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $board
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $exit
        )
          local.get $board
          local.set $board__slot
          local.get $__jn_frame
          i32.const 1
          local.get $board__slot
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $idx__slot
          local.get $d
          local.set $d__slot
          local.get $idx__slot
          local.set $t0
          local.get $t0
          i32.const 9
          i32.div_s
          local.set $t1
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t1
          local.set $r
          local.get $idx__slot
          local.set $t3
          local.get $t3
          i32.const 9
          i32.rem_s
          local.set $t4
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t4
          local.set $c
          i32.const 0
          local.set $j
          i32.const 5
          local.set $pc
          br $dispatch
        )
          local.get $j
          local.set $t6
          local.get $t6
          i32.const 9
          i32.lt_s
          local.set $t7
          local.get $t7
          if
            i32.const 6
            local.set $pc
          else
            i32.const 7
            local.set $pc
          end
          br $dispatch
        )
          local.get $board__slot
          local.set $t8
          local.get $__jn_frame
          i32.const 2
          local.get $t8
          call $jn_gc_frame_set_root
          local.get $r
          local.set $t9
          local.get $t9
          i32.const 9
          i32.mul
          local.set $t10
          local.get $j
          local.set $t11
          local.get $t10
          local.get $t11
          i32.add
          local.set $t12
          local.get $t8
          i32.eqz
          if
            unreachable
          end
          local.get $t12
          i32.const 0
          i32.lt_s
          local.get $t12
          local.get $t8
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t8
          i32.const 16
          i32.add
          local.get $t12
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t13
          call $jnative_exception_pending
          local.set $t14
          local.get $t14
          if
            i32.const 8
            local.set $pc
          else
            i32.const 9
            local.set $pc
          end
          br $dispatch
        )
          local.get $r
          local.set $t28
          local.get $t28
          i32.const 3
          i32.div_s
          local.set $t29
          call $jnative_exception_pending
          local.set $t30
          local.get $t30
          if
            i32.const 16
            local.set $pc
          else
            i32.const 17
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $d__slot
          local.set $t15
          local.get $t13
          local.get $t15
          i32.eq
          local.set $t16
          local.get $t16
          if
            i32.const 10
            local.set $pc
          else
            i32.const 11
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $board__slot
          local.set $t17
          local.get $__jn_frame
          i32.const 3
          local.get $t17
          call $jn_gc_frame_set_root
          local.get $j
          local.set $t18
          local.get $t18
          i32.const 9
          i32.mul
          local.set $t19
          local.get $c
          local.set $t20
          local.get $t19
          local.get $t20
          i32.add
          local.set $t21
          local.get $t17
          i32.eqz
          if
            unreachable
          end
          local.get $t21
          i32.const 0
          i32.lt_s
          local.get $t21
          local.get $t17
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t17
          i32.const 16
          i32.add
          local.get $t21
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t22
          call $jnative_exception_pending
          local.set $t23
          local.get $t23
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $d__slot
          local.set $t24
          local.get $t22
          local.get $t24
          i32.eq
          local.set $t25
          local.get $t25
          if
            i32.const 14
            local.set $pc
          else
            i32.const 15
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $j
          local.set $t26
          local.get $t26
          i32.const 1
          i32.add
          local.set $t27
          local.get $t27
          local.set $j
          call $jn_gc_safepoint
          i32.const 5
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t29
          i32.const 3
          i32.mul
          local.set $t31
          local.get $t31
          local.set $br
          local.get $c
          local.set $t32
          local.get $t32
          i32.const 3
          i32.div_s
          local.set $t33
          call $jnative_exception_pending
          local.set $t34
          local.get $t34
          if
            i32.const 18
            local.set $pc
          else
            i32.const 19
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t33
          i32.const 3
          i32.mul
          local.set $t35
          local.get $t35
          local.set $bc
          i32.const 0
          local.set $dr
          i32.const 20
          local.set $pc
          br $dispatch
        )
          local.get $dr
          local.set $t36
          local.get $t36
          i32.const 3
          i32.lt_s
          local.set $t37
          local.get $t37
          if
            i32.const 21
            local.set $pc
          else
            i32.const 22
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $dc
          i32.const 23
          local.set $pc
          br $dispatch
        )
          i32.const 1
          local.set $ret
          br $exit
        )
          local.get $dc
          local.set $t38
          local.get $t38
          i32.const 3
          i32.lt_s
          local.set $t39
          local.get $t39
          if
            i32.const 24
            local.set $pc
          else
            i32.const 25
            local.set $pc
          end
          br $dispatch
        )
          local.get $board__slot
          local.set $t40
          local.get $__jn_frame
          i32.const 4
          local.get $t40
          call $jn_gc_frame_set_root
          local.get $br
          local.set $t41
          local.get $dr
          local.set $t42
          local.get $t41
          local.get $t42
          i32.add
          local.set $t43
          local.get $t43
          i32.const 9
          i32.mul
          local.set $t44
          local.get $bc
          local.set $t45
          local.get $dc
          local.set $t46
          local.get $t45
          local.get $t46
          i32.add
          local.set $t47
          local.get $t44
          local.get $t47
          i32.add
          local.set $t48
          local.get $t40
          i32.eqz
          if
            unreachable
          end
          local.get $t48
          i32.const 0
          i32.lt_s
          local.get $t48
          local.get $t40
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t40
          i32.const 16
          i32.add
          local.get $t48
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t49
          call $jnative_exception_pending
          local.set $t50
          local.get $t50
          if
            i32.const 26
            local.set $pc
          else
            i32.const 27
            local.set $pc
          end
          br $dispatch
        )
          local.get $dr
          local.set $t55
          local.get $t55
          i32.const 1
          i32.add
          local.set $t56
          local.get $t56
          local.set $dr
          call $jn_gc_safepoint
          i32.const 20
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $d__slot
          local.set $t51
          local.get $t49
          local.get $t51
          i32.eq
          local.set $t52
          local.get $t52
          if
            i32.const 28
            local.set $pc
          else
            i32.const 29
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $dc
          local.set $t53
          local.get $t53
          i32.const 1
          i32.add
          local.set $t54
          local.get $t54
          local.set $dc
          call $jn_gc_safepoint
          i32.const 23
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_fillRandomRec_Ai32 (param $board i32) (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $board__slot i32)
    (local $idx i32)
    (local $order i32)
    (local $gctmp.12 i32)
    (local $k i32)
    (local $j i32)
    (local $tmp i32)
    (local $d i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
    (local $t8 i32)
    (local $t9 i32)
    (local $t10 i32)
    (local $t11 i32)
    (local $t12 i32)
    (local $t13 i32)
    (local $t14 i32)
    (local $t15 i32)
    (local $t16 i32)
    (local $t17 i32)
    (local $t18 i32)
    (local $t19 i32)
    (local $t20 i32)
    (local $t21 i32)
    (local $t22 i32)
    (local $t23 i32)
    (local $t24 i32)
    (local $t25 i32)
    (local $t26 i32)
    (local $t27 i32)
    (local $t28 i32)
    (local $t29 i32)
    (local $t30 i32)
    (local $t31 i32)
    (local $t32 i32)
    (local $t33 i32)
    (local $t34 i32)
    (local $t35 i32)
    (local $t36 i32)
    (local $t37 i32)
    (local $t38 i32)
    (local $t39 i32)
    (local $t40 i32)
    (local $t41 i32)
    (local $t42 i32)
    (local $t43 i32)
    (local $t44 i32)
    (local $t45 i32)
    (local $t46 i32)
    (local $t47 i32)
    (local $t48 i32)
    (local $t49 i32)
    (local $t50 i32)
    (local $t51 i32)
    (local $t52 i32)
    (local $t53 i32)
    (local $t54 i32)
    (local $t55 i32)
    (local $t56 i32)
    (local $t57 i32)
    (local $t58 i32)
    (local $t59 i32)
    (local $t60 i32)
    (local $t61 i32)
    (local $t62 i32)
    (local $t63 i32)
    (local $t64 i32)
    (local $t65 i32)
    (local $t66 i32)
    (local $t67 i32)
    (local $t68 i32)
    (local $t69 i32)
    (local $t70 i32)
    (local $t71 i32)
    i32.const 17
    call $jn_gc_frame_push
    local.set $__jn_frame
    local.get $__jn_frame
    i32.const 0
    local.get $board
    call $jn_gc_frame_set_root
    i32.const 0
    local.set $pc
    (block $exit
      (loop $dispatch
        (block $bb45
        (block $bb44
        (block $bb43
        (block $bb42
        (block $bb41
        (block $bb40
        (block $bb39
        (block $bb38
        (block $bb37
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
          br_table $bb0 $bb1 $bb2 $bb3 $bb4 $bb5 $bb6 $bb7 $bb8 $bb9 $bb10 $bb11 $bb12 $bb13 $bb14 $bb15 $bb16 $bb17 $bb18 $bb19 $bb20 $bb21 $bb22 $bb23 $bb24 $bb25 $bb26 $bb27 $bb28 $bb29 $bb30 $bb31 $bb32 $bb33 $bb34 $bb35 $bb36 $bb37 $bb38 $bb39 $bb40 $bb41 $bb42 $bb43 $bb44 $bb45 $exit
        )
          local.get $board
          local.set $board__slot
          local.get $__jn_frame
          i32.const 1
          local.get $board__slot
          call $jn_gc_frame_set_root
          global.get $g_jn_static_SudokuCore_genSteps
          local.set $t0
          local.get $t0
          i32.const 1
          i32.add
          local.set $t1
          local.get $t1
          global.set $g_jn_static_SudokuCore_genSteps
          global.get $g_jn_static_SudokuCore_genSteps
          local.set $t2
          global.get $g_jn_static_SudokuCore_GEN_MAX_STEPS
          local.set $t3
          local.get $t2
          local.get $t3
          i32.gt_s
          local.set $t4
          local.get $t4
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $board__slot
          local.set $t5
          local.get $__jn_frame
          i32.const 4
          local.get $t5
          call $jn_gc_frame_set_root
          local.get $t5
          call $_JN_SudokuCore_findEmpty_Ai32
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t6
          local.set $idx
          local.get $idx
          local.set $t8
          local.get $t8
          i32.const 0
          i32.lt_s
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
          i32.const 1
          local.set $ret
          br $exit
        )
          i32.const 9
          i32.const 0
          i32.lt_s
          if
            unreachable
          end
          i32.const 9
          i32.const 4
          i32.mul
          local.set $__jn_asz
          local.get $__jn_asz
          i32.const 4
          i32.div_u
          i32.const 9
          i32.ne
          if
            unreachable
          end
          local.get $__jn_asz
          i32.const 16
          i32.add
          i32.const 0
          call $jn_gc_alloc_array
          local.set $t10
          local.get $t10
          i32.eqz
          if
            unreachable
          end
          local.get $t10
          i32.const 47
          i32.store
          local.get $t10
          i32.const 8
          i32.add
          i32.const 9
          i32.store
          local.get $__jn_frame
          i32.const 5
          local.get $t10
          call $jn_gc_frame_set_root
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
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t10
          local.set $gctmp.12
          local.get $__jn_frame
          i32.const 3
          local.get $gctmp.12
          call $jn_gc_frame_set_root
          local.get $gctmp.12
          local.set $t12
          local.get $__jn_frame
          i32.const 6
          local.get $t12
          call $jn_gc_frame_set_root
          local.get $t12
          local.set $order
          local.get $__jn_frame
          i32.const 2
          local.get $order
          call $jn_gc_frame_set_root
          i32.const 0
          local.set $k
          i32.const 9
          local.set $pc
          br $dispatch
        )
          local.get $k
          local.set $t13
          local.get $t13
          i32.const 9
          i32.lt_s
          local.set $t14
          local.get $t14
          if
            i32.const 10
            local.set $pc
          else
            i32.const 11
            local.set $pc
          end
          br $dispatch
        )
          local.get $k
          local.set $t15
          local.get $t15
          i32.const 1
          i32.add
          local.set $t16
          local.get $order
          local.set $t17
          local.get $__jn_frame
          i32.const 7
          local.get $t17
          call $jn_gc_frame_set_root
          local.get $k
          local.set $t18
          local.get $t17
          i32.eqz
          if
            unreachable
          end
          local.get $t18
          i32.const 0
          i32.lt_s
          local.get $t18
          local.get $t17
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t17
          i32.const 16
          i32.add
          local.get $t18
          i32.const 4
          i32.mul
          i32.add
          local.get $t16
          i32.store
          call $jnative_exception_pending
          local.set $t19
          local.get $t19
          if
            i32.const 12
            local.set $pc
          else
            i32.const 13
            local.set $pc
          end
          br $dispatch
        )
          i32.const 8
          local.set $k
          i32.const 14
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $k
          local.set $t20
          local.get $t20
          i32.const 1
          i32.add
          local.set $t21
          local.get $t21
          local.set $k
          call $jn_gc_safepoint
          i32.const 9
          local.set $pc
          br $dispatch
        )
          local.get $k
          local.set $t22
          local.get $t22
          i32.const 0
          i32.gt_s
          local.set $t23
          local.get $t23
          if
            i32.const 15
            local.set $pc
          else
            i32.const 16
            local.set $pc
          end
          br $dispatch
        )
          call $_JN_SudokuCore_rngNext
          local.set $t24
          call $jnative_exception_pending
          local.set $t25
          local.get $t25
          if
            i32.const 17
            local.set $pc
          else
            i32.const 18
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $k
          i32.const 29
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $k
          local.set $t26
          local.get $t26
          i32.const 1
          i32.add
          local.set $t27
          local.get $t24
          local.get $t27
          i32.rem_s
          local.set $t28
          call $jnative_exception_pending
          local.set $t29
          local.get $t29
          if
            i32.const 19
            local.set $pc
          else
            i32.const 20
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t28
          local.set $j
          local.get $order
          local.set $t30
          local.get $__jn_frame
          i32.const 8
          local.get $t30
          call $jn_gc_frame_set_root
          local.get $k
          local.set $t31
          local.get $t30
          i32.eqz
          if
            unreachable
          end
          local.get $t31
          i32.const 0
          i32.lt_s
          local.get $t31
          local.get $t30
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t30
          i32.const 16
          i32.add
          local.get $t31
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t32
          call $jnative_exception_pending
          local.set $t33
          local.get $t33
          if
            i32.const 21
            local.set $pc
          else
            i32.const 22
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t32
          local.set $tmp
          local.get $order
          local.set $t34
          local.get $__jn_frame
          i32.const 9
          local.get $t34
          call $jn_gc_frame_set_root
          local.get $j
          local.set $t35
          local.get $t34
          i32.eqz
          if
            unreachable
          end
          local.get $t35
          i32.const 0
          i32.lt_s
          local.get $t35
          local.get $t34
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t34
          i32.const 16
          i32.add
          local.get $t35
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t36
          call $jnative_exception_pending
          local.set $t37
          local.get $t37
          if
            i32.const 23
            local.set $pc
          else
            i32.const 24
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $order
          local.set $t38
          local.get $__jn_frame
          i32.const 10
          local.get $t38
          call $jn_gc_frame_set_root
          local.get $k
          local.set $t39
          local.get $t38
          i32.eqz
          if
            unreachable
          end
          local.get $t39
          i32.const 0
          i32.lt_s
          local.get $t39
          local.get $t38
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t38
          i32.const 16
          i32.add
          local.get $t39
          i32.const 4
          i32.mul
          i32.add
          local.get $t36
          i32.store
          call $jnative_exception_pending
          local.set $t40
          local.get $t40
          if
            i32.const 25
            local.set $pc
          else
            i32.const 26
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $tmp
          local.set $t41
          local.get $order
          local.set $t42
          local.get $__jn_frame
          i32.const 11
          local.get $t42
          call $jn_gc_frame_set_root
          local.get $j
          local.set $t43
          local.get $t42
          i32.eqz
          if
            unreachable
          end
          local.get $t43
          i32.const 0
          i32.lt_s
          local.get $t43
          local.get $t42
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t42
          i32.const 16
          i32.add
          local.get $t43
          i32.const 4
          i32.mul
          i32.add
          local.get $t41
          i32.store
          call $jnative_exception_pending
          local.set $t44
          local.get $t44
          if
            i32.const 27
            local.set $pc
          else
            i32.const 28
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $k
          local.set $t45
          local.get $t45
          i32.const 1
          i32.sub
          local.set $t46
          local.get $t46
          local.set $k
          call $jn_gc_safepoint
          i32.const 14
          local.set $pc
          br $dispatch
        )
          local.get $k
          local.set $t47
          local.get $t47
          i32.const 9
          i32.lt_s
          local.set $t48
          local.get $t48
          if
            i32.const 30
            local.set $pc
          else
            i32.const 31
            local.set $pc
          end
          br $dispatch
        )
          local.get $order
          local.set $t49
          local.get $__jn_frame
          i32.const 12
          local.get $t49
          call $jn_gc_frame_set_root
          local.get $k
          local.set $t50
          local.get $t49
          i32.eqz
          if
            unreachable
          end
          local.get $t50
          i32.const 0
          i32.lt_s
          local.get $t50
          local.get $t49
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t49
          i32.const 16
          i32.add
          local.get $t50
          i32.const 4
          i32.mul
          i32.add
          i32.load
          local.set $t51
          call $jnative_exception_pending
          local.set $t52
          local.get $t52
          if
            i32.const 32
            local.set $pc
          else
            i32.const 33
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t51
          local.set $d
          local.get $board__slot
          local.set $t53
          local.get $__jn_frame
          i32.const 13
          local.get $t53
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t54
          local.get $d
          local.set $t55
          local.get $t53
          local.get $t54
          local.get $t55
          call $_JN_SudokuCore_canPlace_Ai32_i32_i32
          local.set $t56
          call $jnative_exception_pending
          local.set $t57
          local.get $t57
          if
            i32.const 34
            local.set $pc
          else
            i32.const 35
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t56
          i32.const 0
          i32.ne
          local.set $t58
          local.get $t58
          if
            i32.const 36
            local.set $pc
          else
            i32.const 37
            local.set $pc
          end
          br $dispatch
        )
          local.get $d
          local.set $t59
          local.get $board__slot
          local.set $t60
          local.get $__jn_frame
          i32.const 14
          local.get $t60
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t61
          local.get $t60
          i32.eqz
          if
            unreachable
          end
          local.get $t61
          i32.const 0
          i32.lt_s
          local.get $t61
          local.get $t60
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t60
          i32.const 16
          i32.add
          local.get $t61
          i32.const 4
          i32.mul
          i32.add
          local.get $t59
          i32.store
          call $jnative_exception_pending
          local.set $t62
          local.get $t62
          if
            i32.const 38
            local.set $pc
          else
            i32.const 39
            local.set $pc
          end
          br $dispatch
        )
          local.get $k
          local.set $t70
          local.get $t70
          i32.const 1
          i32.add
          local.set $t71
          local.get $t71
          local.set $k
          call $jn_gc_safepoint
          i32.const 29
          local.set $pc
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $board__slot
          local.set $t63
          local.get $__jn_frame
          i32.const 15
          local.get $t63
          call $jn_gc_frame_set_root
          local.get $t63
          call $_JN_SudokuCore_fillRandomRec_Ai32
          local.set $t64
          call $jnative_exception_pending
          local.set $t65
          local.get $t65
          if
            i32.const 40
            local.set $pc
          else
            i32.const 41
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          local.get $t64
          i32.const 0
          i32.ne
          local.set $t66
          local.get $t66
          if
            i32.const 42
            local.set $pc
          else
            i32.const 43
            local.set $pc
          end
          br $dispatch
        )
          i32.const 1
          local.set $ret
          br $exit
        )
          local.get $board__slot
          local.set $t67
          local.get $__jn_frame
          i32.const 16
          local.get $t67
          call $jn_gc_frame_set_root
          local.get $idx
          local.set $t68
          local.get $t67
          i32.eqz
          if
            unreachable
          end
          local.get $t68
          i32.const 0
          i32.lt_s
          local.get $t68
          local.get $t67
          i32.const 8
          i32.add
          i32.load
          i32.ge_s
          i32.or
          if
            unreachable
          end
          local.get $t67
          i32.const 16
          i32.add
          local.get $t68
          i32.const 4
          i32.mul
          i32.add
          i32.const 0
          i32.store
          call $jnative_exception_pending
          local.set $t69
          local.get $t69
          if
            i32.const 44
            local.set $pc
          else
            i32.const 45
            local.set $pc
          end
          br $dispatch
        )
          i32.const 0
          local.set $ret
          br $exit
        )
          i32.const 37
          local.set $pc
          br $dispatch
      )
    )
    call $jn_gc_frame_pop
    local.get $ret
  )
  (func $_JN_SudokuCore_rngNext (result i32)
    (local $pc i32)
    (local $__jn_tid i32)
    (local $__jn_asz i32)
    (local $__jn_frame i32)
    (local $ret i32)
    (local $t0 i32)
    (local $t1 i32)
    (local $t2 i32)
    (local $t3 i32)
    (local $t4 i32)
    (local $t5 i32)
    (local $t6 i32)
    (local $t7 i32)
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
          global.get $g_jn_static_SudokuCore_rngState
          local.set $t0
          local.get $t0
          i32.const 1664525
          i32.mul
          local.set $t1
          local.get $t1
          i32.const 1013904223
          i32.add
          local.set $t2
          local.get $t2
          global.set $g_jn_static_SudokuCore_rngState
          global.get $g_jn_static_SudokuCore_rngState
          local.set $t3
          local.get $t3
          i32.const 0
          i32.lt_s
          local.set $t4
          local.get $t4
          if
            i32.const 1
            local.set $pc
          else
            i32.const 2
            local.set $pc
          end
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_rngState
          local.set $t5
          i32.const 0
          local.get $t5
          i32.sub
          local.set $t6
          local.get $t6
          global.set $g_jn_static_SudokuCore_rngState
          i32.const 2
          local.set $pc
          br $dispatch
        )
          global.get $g_jn_static_SudokuCore_rngState
          local.set $t7
          local.get $t7
          local.set $ret
          br $exit
      )
    )
    local.get $ret
  )
  (func $jn_super_of (param $tid i32) (result i32)
    local.get $tid
    i32.const 1
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 2
    i32.eq
    if
      i32.const 29
      return
    end
    local.get $tid
    i32.const 3
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 4
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 5
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 6
    i32.eq
    if
      i32.const 29
      return
    end
    local.get $tid
    i32.const 7
    i32.eq
    if
      i32.const 32
      return
    end
    local.get $tid
    i32.const 8
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 9
    i32.eq
    if
      i32.const 29
      return
    end
    local.get $tid
    i32.const 10
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 11
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 12
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 13
    i32.eq
    if
      i32.const 29
      return
    end
    local.get $tid
    i32.const 14
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 15
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 16
    i32.eq
    if
      i32.const 31
      return
    end
    local.get $tid
    i32.const 17
    i32.eq
    if
      i32.const 29
      return
    end
    local.get $tid
    i32.const 18
    i32.eq
    if
      i32.const 29
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
      i32.const 38
      return
    end
    local.get $tid
    i32.const 21
    i32.eq
    if
      i32.const 31
      return
    end
    local.get $tid
    i32.const 22
    i32.eq
    if
      i32.const 29
      return
    end
    local.get $tid
    i32.const 23
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 24
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 25
    i32.eq
    if
      i32.const 7
      return
    end
    local.get $tid
    i32.const 26
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 27
    i32.eq
    if
      i32.const 25
      return
    end
    local.get $tid
    i32.const 28
    i32.eq
    if
      i32.const 29
      return
    end
    local.get $tid
    i32.const 29
    i32.eq
    if
      i32.const 7
      return
    end
    local.get $tid
    i32.const 30
    i32.eq
    if
      i32.const 29
      return
    end
    local.get $tid
    i32.const 31
    i32.eq
    if
      i32.const 25
      return
    end
    local.get $tid
    i32.const 32
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 33
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 34
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 35
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 36
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 37
    i32.eq
    if
      i32.const 29
      return
    end
    local.get $tid
    i32.const 38
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 39
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 40
    i32.eq
    if
      i32.const 29
      return
    end
    local.get $tid
    i32.const 41
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 42
    i32.eq
    if
      i32.const 31
      return
    end
    local.get $tid
    i32.const 43
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 44
    i32.eq
    if
      i32.const 38
      return
    end
    local.get $tid
    i32.const 45
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 46
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 47
    i32.eq
    if
      i32.const 0
      return
    end
    local.get $tid
    i32.const 48
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
      i32.const 0
    end
  )
  (export "_JN_SudokuCore_ensureInit" (func $_JN_SudokuCore_ensureInit))
  (export "_JN_SudokuCore_getLastError" (func $_JN_SudokuCore_getLastError))
  (export "_JN_SudokuCore_puzzleCount_i32" (func $_JN_SudokuCore_puzzleCount_i32))
  (export "_JN_SudokuCore_newGame_i32_i32" (func $_JN_SudokuCore_newGame_i32_i32))
  (export "_JN_SudokuCore_catalogPuzzle_i32" (func $_JN_SudokuCore_catalogPuzzle_i32))
  (export "_JN_SudokuCore_loadPuzzleFromCatalog_i32_i32_i32" (func $_JN_SudokuCore_loadPuzzleFromCatalog_i32_i32_i32))
  (export "_JN_SudokuCore_loadPuzzleString_CString_i32_i32" (func $_JN_SudokuCore_loadPuzzleString_CString_i32_i32))
  (export "_JN_SudokuCore_selectCell_i32_i32" (func $_JN_SudokuCore_selectCell_i32_i32))
  (export "_JN_SudokuCore_moveSelection_i32_i32" (func $_JN_SudokuCore_moveSelection_i32_i32))
  (export "_JN_SudokuCore_toggleNoteMode" (func $_JN_SudokuCore_toggleNoteMode))
  (export "_JN_SudokuCore_setNoteMode_i32" (func $_JN_SudokuCore_setNoteMode_i32))
  (export "_JN_SudokuCore_inputDigit_i32" (func $_JN_SudokuCore_inputDigit_i32))
  (export "_JN_SudokuCore_clearCell" (func $_JN_SudokuCore_clearCell))
  (export "_JN_SudokuCore_toggleNoteAt_i32_i32" (func $_JN_SudokuCore_toggleNoteAt_i32_i32))
  (export "_JN_SudokuCore_pushHistory_i32_i32_i32_i32_i32" (func $_JN_SudokuCore_pushHistory_i32_i32_i32_i32_i32))
  (export "_JN_SudokuCore_undo" (func $_JN_SudokuCore_undo))
  (export "_JN_SudokuCore_redo" (func $_JN_SudokuCore_redo))
  (export "_JN_SudokuCore_hint" (func $_JN_SudokuCore_hint))
  (export "_JN_SudokuCore_pause" (func $_JN_SudokuCore_pause))
  (export "_JN_SudokuCore_resume" (func $_JN_SudokuCore_resume))
  (export "_JN_SudokuCore_tick_i32" (func $_JN_SudokuCore_tick_i32))
  (export "_JN_SudokuCore_getElapsedMs" (func $_JN_SudokuCore_getElapsedMs))
  (export "_JN_SudokuCore_getStatus" (func $_JN_SudokuCore_getStatus))
  (export "_JN_SudokuCore_getSelected" (func $_JN_SudokuCore_getSelected))
  (export "_JN_SudokuCore_getNoteMode" (func $_JN_SudokuCore_getNoteMode))
  (export "_JN_SudokuCore_getPuzzleId" (func $_JN_SudokuCore_getPuzzleId))
  (export "_JN_SudokuCore_getDifficulty" (func $_JN_SudokuCore_getDifficulty))
  (export "_JN_SudokuCore_getValue_i32" (func $_JN_SudokuCore_getValue_i32))
  (export "_JN_SudokuCore_getGiven_i32" (func $_JN_SudokuCore_getGiven_i32))
  (export "_JN_SudokuCore_getNotes_i32" (func $_JN_SudokuCore_getNotes_i32))
  (export "_JN_SudokuCore_getConflict_i32" (func $_JN_SudokuCore_getConflict_i32))
  (export "_JN_SudokuCore_getSolutionDigit_i32" (func $_JN_SudokuCore_getSolutionDigit_i32))
  (export "_JN_SudokuCore_canUndo" (func $_JN_SudokuCore_canUndo))
  (export "_JN_SudokuCore_canRedo" (func $_JN_SudokuCore_canRedo))
  (export "_JN_SudokuCore_isComplete" (func $_JN_SudokuCore_isComplete))
  (export "_JN_SudokuCore_getHighlightDigit" (func $_JN_SudokuCore_getHighlightDigit))
  (export "_JN_SudokuCore_sameRow_i32" (func $_JN_SudokuCore_sameRow_i32))
  (export "_JN_SudokuCore_sameCol_i32" (func $_JN_SudokuCore_sameCol_i32))
  (export "_JN_SudokuCore_sameBox_i32" (func $_JN_SudokuCore_sameBox_i32))
  (export "_JN_SudokuCore_checkWon" (func $_JN_SudokuCore_checkWon))
  (export "_JN_SudokuCore_boardValidComplete_Ai32" (func $_JN_SudokuCore_boardValidComplete_Ai32))
  (export "_JN_SudokuCore_recomputeConflicts" (func $_JN_SudokuCore_recomputeConflicts))
  (export "_JN_SudokuCore_copyBoard_Ai32_Ai32" (func $_JN_SudokuCore_copyBoard_Ai32_Ai32))
  (export "_JN_SudokuCore_countSolutionsPuzzle_CString" (func $_JN_SudokuCore_countSolutionsPuzzle_CString))
  (export "_JN_SudokuCore_countSolutionsBoard_Ai32_i32" (func $_JN_SudokuCore_countSolutionsBoard_Ai32_i32))
  (export "_JN_SudokuCore_solveBoard_Ai32" (func $_JN_SudokuCore_solveBoard_Ai32))
  (export "_JN_SudokuCore_findEmpty_Ai32" (func $_JN_SudokuCore_findEmpty_Ai32))
  (export "_JN_SudokuCore_canPlace_Ai32_i32_i32" (func $_JN_SudokuCore_canPlace_Ai32_i32_i32))
  (export "_JN_SudokuCore_prepareSave" (func $_JN_SudokuCore_prepareSave))
  (export "_JN_SudokuCore_getSaveLen" (func $_JN_SudokuCore_getSaveLen))
  (export "_JN_SudokuCore_getSaveChar_i32" (func $_JN_SudokuCore_getSaveChar_i32))
  (export "_JN_SudokuCore_emitChar_i32" (func $_JN_SudokuCore_emitChar_i32))
  (export "_JN_SudokuCore_emitStr_CString" (func $_JN_SudokuCore_emitStr_CString))
  (export "_JN_SudokuCore_emitInt_i32" (func $_JN_SudokuCore_emitInt_i32))
  (export "_JN_SudokuCore_loadSave_CString" (func $_JN_SudokuCore_loadSave_CString))
  (export "_JN_SudokuCore_matchLiteral_CString_Ai32_CString" (func $_JN_SudokuCore_matchLiteral_CString_Ai32_CString))
  (export "_JN_SudokuCore_parseIntLine_CString_Ai32" (func $_JN_SudokuCore_parseIntLine_CString_Ai32))
  (export "_JN_SudokuCore_parseDigitLine_CString_Ai32_Ai32" (func $_JN_SudokuCore_parseDigitLine_CString_Ai32_Ai32))
  (export "_JN_SudokuCore_parseNotesLine_CString_Ai32_Ai32" (func $_JN_SudokuCore_parseNotesLine_CString_Ai32_Ai32))
  (export "_JN_SudokuCore_parseHistoryLine_CString_Ai32_i32_Ai32_Ai32_Ai32_Ai32_Ai32" (func $_JN_SudokuCore_parseHistoryLine_CString_Ai32_i32_Ai32_Ai32_Ai32_Ai32_Ai32))
  (export "_JN_SudokuCore_parseNum_CString_Ai32_i32" (func $_JN_SudokuCore_parseNum_CString_Ai32_i32))
  (export "_JN_SudokuCore_generate_i32_i32" (func $_JN_SudokuCore_generate_i32_i32))
  (export "_JN_SudokuCore_rngNext" (func $_JN_SudokuCore_rngNext))
  (export "_JN_SudokuCore_fillRandomComplete_Ai32" (func $_JN_SudokuCore_fillRandomComplete_Ai32))
  (export "_JN_SudokuCore_fillRandomRec_Ai32" (func $_JN_SudokuCore_fillRandomRec_Ai32))
  (export "_JN_SudokuCore_digHoles_Ai32_i32" (func $_JN_SudokuCore_digHoles_Ai32_i32))
  (export "_JN_SudokuCore_exportBoardDigits" (func $_JN_SudokuCore_exportBoardDigits))
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
