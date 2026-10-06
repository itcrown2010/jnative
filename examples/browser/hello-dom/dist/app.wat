(module
  (import "jnative" "console_log" (func $js_console_log (param i32 i32)))
  (import "jnative" "console_warn" (func $js_console_warn (param i32 i32)))
  (import "jnative" "console_error" (func $js_console_error (param i32 i32)))
  (import "jnative" "document_get_element_by_id" (func $js_get_by_id (param i32 i32) (result i32)))
  (import "jnative" "element_set_text" (func $js_set_text (param i32 i32 i32)))
  (import "jnative" "element_get_text" (func $js_get_text (param i32) (result i32)))
  (import "jnative" "document_create_element" (func $js_create_element (param i32 i32) (result i32)))
  (import "jnative" "element_append_child" (func $js_append_child (param i32 i32)))
  (import "jnative" "element_add_event_listener" (func $js_add_event_listener (param i32 i32 i32 i32)))
  (import "jnative" "storage_set" (func $js_storage_set (param i32 i32 i32 i32 i32)))
  (import "jnative" "storage_get" (func $js_storage_get (param i32 i32 i32) (result i32)))
  (import "jnative" "fetch_get" (func $js_fetch_get (param i32 i32) (result i32)))
  (import "jnative" "timer_sleep" (func $js_timer_sleep (param i32)))
  (import "jnative" "invoke_callback" (func $js_register_callback (param i32) (result i32)))
  (memory (export "memory") 256 1024)
  (global $heap (mut i32) (i32.const 1397))
  (data (i32.const 1024) "\48\61\73\68\53\65\74\2e\69\74\65\72\61\74\6f\72\20\6e\6f\74\20\69\6d\70\6c\65\6d\65\6e\74\65\64\20\69\6e\20\30\2e\31\36")
  (data (i32.const 1064) "\48\65\6c\6c\6f\20\4a\4e\61\74\69\76\65\20\57\41\53\4d\21")
  (data (i32.const 1083) "\74\69\74\6c\65")
  (data (i32.const 1088) "\48\65\6c\6c\6f\20\66\72\6f\6d\20\4a\4e\61\74\69\76\65")
  (data (i32.const 1106) "\41\72\72\61\79\4c\69\73\74\20\69\6e\64\65\78")
  (data (i32.const 1121) "\74\69\6d\65\6f\75\74\20\61\66\74\65\72\20")
  (data (i32.const 1135) "\6d\73")
  (data (i32.const 1137) "\54\68\72\65\61\64\50\6f\6f\6c\45\78\65\63\75\74\6f\72\20\69\73\20\73\68\75\74\20\64\6f\77\6e")
  (data (i32.const 1168) "\48\61\73\68\4d\61\70\20\66\75\6c\6c")
  (data (i32.const 1180) "\4a\6e\46\75\74\75\72\65\2e\61\6e\79\3a\20\65\6d\70\74\79")
  (data (i32.const 1199) "")
  (data (i32.const 1200) "\41\72\72\61\79\4c\69\73\74\20\6d\6f\64\69\66\69\65\64\20\64\75\72\69\6e\67\20\69\74\65\72\61\74\69\6f\6e")
  (data (i32.const 1235) "\41\72\72\61\79\4c\69\73\74\49\74\65\72\61\74\6f\72")
  (data (i32.const 1252) "\66\75\74\75\72\65\20\63\61\6e\63\65\6c\6c\65\64")
  (data (i32.const 1268) "\41\72\72\61\79\44\65\71\75\65")
  (data (i32.const 1278) "\63\61\6e\63\65\6c\6c\65\64")
  (data (i32.const 1287) "\41\72\72\61\79\4c\69\73\74\20\69\6e\73\65\72\74\20\69\6e\64\65\78")
  (data (i32.const 1309) "\6e\65\67\61\74\69\76\65\20\69\6e\69\74\69\61\6c\43\61\70\61\63\69\74\79")
  ;; bump allocator — non-moving heap for browser GC foundation
  (func $jn_alloc (param $n i32) (result i32)
    (local $p i32)
    global.get $heap
    local.set $p
    global.get $heap
    local.get $n
    i32.const 7
    i32.add
    i32.const -8
    i32.and
    i32.add
    global.set $heap
    local.get $p
  )
  ;; string object: [i32 len][bytes...]  (UTF-8)
  (func $jn_str (param $off i32) (param $len i32) (result i32)
    (local $p i32)
    (local $i i32)
    local.get $len
    i32.const 4
    i32.add
    call $jn_alloc
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
  (func $jn_set_text (param $h i32) (param $s i32)
    local.get $h
    local.get $s
    call $jn_str_data
    local.get $s
    call $jn_str_len
    call $js_set_text
  )
  (func $jn_main
    (local $title i32)
    (local $s i32)
    i32.const 1064
    i32.const 19
    call $jn_str
    call $jn_console_log
    i32.const 1083
    i32.const 5
    call $jn_str
    call $jn_get_element_by_id
    local.set $title
    local.get $title
    i32.const 1088
    i32.const 18
    call $jn_str
    call $jn_set_text
  )
  (func (export "jnative_start")
    call $jn_main
  )
)
