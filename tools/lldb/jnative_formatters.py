#!/usr/bin/env python3
# Author: 张晓达
"""
JNative LLDB formatters — memory-only inspection (no inferior method calls).

Layouts (see StringRuntimeEmitter / ArrayRuntimeEmitter / ClassMetadata):
  Object:  { ClassMeta* @0, fields... }
  String:  { ClassMeta* @0, i32 length @8, u16 data... @16 }  (UTF-16 code units, inline)
  Array:   { ClassMeta* @0, i32 length @8, elements @16 }
  ClassMeta name C-string pointer @ offset 32

Loads optional sidecar `<exe>.jn-debug.json` for field offsets.
"""

from __future__ import annotations

import json
import os
import struct
from typing import Any, Dict, List, Optional, Set, Tuple

import lldb

# ---- defaults matching compiler runtime ----
META_NAME_OFF = 32
STR_LEN_OFF = 8
STR_DATA_OFF = 16
ARR_LEN_OFF = 8
ARR_DATA_OFF = 16
MAX_STR = 256
MAX_ARR = 16
MAX_DEPTH = 3
MAX_READ = 4096  # hard cap on any single memory read

_CATALOG_CACHE: Dict[str, dict] = {}
_SEEN: Set[int] = set()


def _process(val: lldb.SBValue) -> Optional[lldb.SBProcess]:
    t = val.GetTarget()
    if not t or not t.IsValid():
        return None
    p = t.GetProcess()
    if not p or not p.IsValid():
        return None
    return p


def _read_bytes(process: lldb.SBProcess, addr: int, n: int) -> Optional[bytes]:
    if addr == 0 or n <= 0:
        return b"" if n == 0 else None
    if n > MAX_READ:
        n = MAX_READ
    err = lldb.SBError()
    data = process.ReadMemory(addr, n, err)
    if not err.Success() or data is None:
        return None
    return bytes(data)


def _u64(process: lldb.SBProcess, addr: int) -> Optional[int]:
    b = _read_bytes(process, addr, 8)
    if b is None or len(b) < 8:
        return None
    return struct.unpack("<Q", b)[0]


def _i32(process: lldb.SBProcess, addr: int) -> Optional[int]:
    b = _read_bytes(process, addr, 4)
    if b is None or len(b) < 4:
        return None
    return struct.unpack("<i", b)[0]


def _f32(process: lldb.SBProcess, addr: int) -> Optional[float]:
    b = _read_bytes(process, addr, 4)
    if b is None or len(b) < 4:
        return None
    return struct.unpack("<f", b)[0]


def _f64(process: lldb.SBProcess, addr: int) -> Optional[float]:
    b = _read_bytes(process, addr, 8)
    if b is None or len(b) < 8:
        return None
    return struct.unpack("<d", b)[0]


def _cstr(process: lldb.SBProcess, addr: int, maxlen: int = 128) -> Optional[str]:
    if addr == 0:
        return None
    b = _read_bytes(process, addr, maxlen)
    if b is None:
        return None
    nul = b.find(b"\x00")
    if nul >= 0:
        b = b[:nul]
    try:
        return b.decode("utf-8", errors="replace")
    except Exception:
        return None


def _exe_path(val: lldb.SBValue) -> Optional[str]:
    t = val.GetTarget()
    if not t or not t.IsValid():
        return None
    e = t.GetExecutable()
    if not e or not e.IsValid():
        return None
    d = e.GetDirectory()
    f = e.GetFilename()
    if not f:
        return None
    return os.path.join(d or "", f)


def _load_catalog(val: lldb.SBValue) -> dict:
    path = _exe_path(val)
    if not path:
        return {}
    if path in _CATALOG_CACHE:
        return _CATALOG_CACHE[path]
    cat_path = path + ".jn-debug.json"
    cat: dict = {}
    if os.path.isfile(cat_path):
        try:
            with open(cat_path, "r", encoding="utf-8") as fh:
                cat = json.load(fh)
        except Exception:
            cat = {}
    _CATALOG_CACHE[path] = cat
    return cat


def _cfg(cat: dict) -> Tuple[int, int, int, int, int, int, int]:
    cm = cat.get("classMeta") or {}
    st = cat.get("string") or {}
    ar = cat.get("array") or {}
    return (
        int(cm.get("namePtrOffset", META_NAME_OFF)),
        int(st.get("lengthOffset", STR_LEN_OFF)),
        int(st.get("dataOffset", STR_DATA_OFF)),
        int(ar.get("lengthOffset", ARR_LEN_OFF)),
        int(ar.get("dataOffset", ARR_DATA_OFF)),
        int(cat.get("maxStringBytes", MAX_STR)),
        int(cat.get("maxArrayElements", MAX_ARR)),
    )


def _ptr_value(val: lldb.SBValue) -> int:
    # Prefer unsigned; fall back to address of pointee / value
    err = lldb.SBError()
    u = val.GetValueAsUnsigned(err)
    if err.Success():
        return int(u)
    # Typed pointer: load as address
    a = val.GetValueAsAddress()
    if a:
        return int(a)
    try:
        return int(val.GetValue(), 0)
    except Exception:
        return 0


def _type_name_from_meta(process: lldb.SBProcess, obj: int, name_off: int) -> Optional[str]:
    meta = _u64(process, obj)
    if meta is None or meta == 0:
        return None
    name_ptr = _u64(process, meta + name_off)
    if name_ptr is None or name_ptr == 0:
        return None
    return _cstr(process, name_ptr)


def _elem_width(type_name: Optional[str], cat: dict) -> Tuple[int, str]:
    """Return (byte width, kind) for array element. kind: i8/i16/i32/i64/f32/f64/ref."""
    if not type_name:
        return 8, "ref"
    for am in cat.get("arrayMetas") or []:
        if am.get("name") == type_name:
            if am.get("refElements"):
                return 8, "ref"
            es = int(am.get("elemSize") or 0)
            if es == 1:
                return 1, "i8"
            if es == 2:
                return 2, "i16"
            if es == 4:
                # float vs int: prefer i32 (values still readable)
                return 4, "i32"
            if es == 8:
                return 8, "i64"
            if es > 0:
                return es, "i8"
    # Runtime RTTI names: array.i32, array.CDog, …
    runtime = {
        "array.i1": (1, "i8"),
        "array.i8": (1, "i8"),
        "array.i16": (2, "i16"),
        "array.i32": (4, "i32"),
        "array.i64": (8, "i64"),
        "array.float": (4, "f32"),
        "array.double": (8, "f64"),
    }
    if type_name in runtime:
        return runtime[type_name]
    if type_name.startswith("array.C") or (
        type_name.startswith("array.") and type_name[6:7].isupper()
    ):
        return 8, "ref"
    if type_name.endswith("[]"):
        elem = type_name[:-2]
    else:
        elem = type_name
    mapping = {
        "boolean": (1, "i8"),
        "byte": (1, "i8"),
        "short": (2, "i16"),
        "char": (2, "i16"),
        "int": (4, "i32"),
        "float": (4, "f32"),
        "long": (8, "i64"),
        "double": (8, "f64"),
    }
    if elem in mapping:
        return mapping[elem]
    return 8, "ref"


def _format_string(
    process: lldb.SBProcess, addr: int, cat: dict, depth: int
) -> str:
    name_off, slen_off, sdata_off, _, _, max_str, _ = _cfg(cat)
    length = _i32(process, addr + slen_off)
    if length is None:
        return "<unreadable String>"
    if length < 0:
        return f"<corrupt String length={length}>"
    # length counts UTF-16 code units (2 bytes each); max_str is a unit budget
    show = min(length, max_str, MAX_READ // 2)
    data = _read_bytes(process, addr + sdata_off, show * 2)
    if data is None:
        return f"<unreadable String length={length}>"
    try:
        text = data.decode("utf-16-le", errors="replace")
    except Exception:
        text = repr(data)
    # Escape for display
    text = text.replace("\\", "\\\\").replace('"', '\\"').replace("\n", "\\n").replace("\r", "\\r")
    if length > show:
        return f'String(len={length}) "{text}…" <truncated>'
    return f'String(len={length}) "{text}"'


def _format_prim_elem(process: lldb.SBProcess, addr: int, kind: str) -> str:
    if kind == "i8":
        b = _read_bytes(process, addr, 1)
        return "<unreadable>" if b is None else str(b[0])
    if kind == "i16":
        b = _read_bytes(process, addr, 2)
        return "<unreadable>" if b is None else str(struct.unpack("<h", b)[0])
    if kind == "i32":
        v = _i32(process, addr)
        return "<unreadable>" if v is None else str(v)
    if kind == "i64":
        v = _u64(process, addr)
        return "<unreadable>" if v is None else str(struct.unpack("<q", struct.pack("<Q", v))[0])
    if kind == "f32":
        v = _f32(process, addr)
        return "<unreadable>" if v is None else repr(v)
    if kind == "f64":
        v = _f64(process, addr)
        return "<unreadable>" if v is None else repr(v)
    # ref
    p = _u64(process, addr)
    if p is None:
        return "<unreadable>"
    if p == 0:
        return "null"
    return f"0x{p:x}"


def _format_array(
    process: lldb.SBProcess, addr: int, type_name: Optional[str], cat: dict, depth: int
) -> str:
    _, _, _, alen_off, adata_off, _, max_arr = _cfg(cat)
    length = _i32(process, addr + alen_off)
    if length is None:
        return "<unreadable array>"
    if length < 0:
        return f"<corrupt array length={length}>"
    width, kind = _elem_width(type_name, cat)
    show = min(length, max_arr)
    parts: List[str] = []
    for i in range(show):
        ea = addr + adata_off + i * width
        if kind == "ref":
            p = _u64(process, ea)
            if p is None:
                parts.append("<unreadable>")
            elif p == 0:
                parts.append("null")
            elif depth >= MAX_DEPTH:
                parts.append(f"0x{p:x}")
            else:
                parts.append(_format_ref(process, p, cat, depth + 1))
        else:
            parts.append(_format_prim_elem(process, ea, kind))
    more = "" if length <= show else f", … (+{length - show})"
    return f"{_array_label(type_name)}[{length}] {{{', '.join(parts)}{more}}}"


_RT_ELEM = {
    "i1": "boolean", "i8": "byte", "i16": "short", "i32": "int", "i64": "long",
    "float": "float", "double": "double", "CString": "String", "String": "String",
}


def _array_label(type_name: Optional[str]) -> str:
    """Runtime RTTI name (array.i32, array.CString, array.CDog) -> Java element type (int, String, Dog)."""
    if not type_name:
        return "Object"
    if type_name.endswith("[]"):
        return type_name[:-2]
    if type_name.startswith("array."):
        e = type_name[6:]
        if e in _RT_ELEM:
            return _RT_ELEM[e]
        if e.startswith("C") and len(e) > 1 and e[1:2].isupper():
            return e[1:]
        return e
    return type_name


def _format_object(
    process: lldb.SBProcess, addr: int, type_name: Optional[str], cat: dict, depth: int
) -> str:
    name_off, _, _, _, _, _, _ = _cfg(cat)
    tname = type_name or _type_name_from_meta(process, addr, name_off) or "Object"
    classes = cat.get("classes") or {}
    layout = classes.get(tname)
    if not layout:
        # Still show RTTI name + address
        return f"{tname}@0x{addr:x}"
    fields = layout.get("fields") or []
    parts: List[str] = []
    for f in fields:
        fname = f.get("name", "?")
        off = int(f.get("offset", 0))
        size = int(f.get("size", 8))
        is_ref = bool(f.get("ref"))
        llvm = f.get("llvmType") or ""
        fa = addr + off
        if is_ref:
            p = _u64(process, fa)
            if p is None:
                parts.append(f"{fname}=<unreadable>")
            elif p == 0:
                parts.append(f"{fname}=null")
            elif p in _SEEN or depth >= MAX_DEPTH:
                parts.append(f"{fname}=0x{p:x}")
            else:
                parts.append(f"{fname}={_format_ref(process, p, cat, depth + 1)}")
        elif llvm == "float" or (size == 4 and "float" in llvm):
            v = _f32(process, fa)
            parts.append(f"{fname}=<unreadable>" if v is None else f"{fname}={v!r}")
        elif llvm == "double" or size == 8 and llvm == "double":
            v = _f64(process, fa)
            parts.append(f"{fname}=<unreadable>" if v is None else f"{fname}={v!r}")
        elif size == 1 or llvm == "i1":
            b = _read_bytes(process, fa, 1)
            if b is None:
                parts.append(f"{fname}=<unreadable>")
            elif llvm == "i1":
                parts.append(f"{fname}={'true' if b[0] else 'false'}")
            else:
                parts.append(f"{fname}={b[0]}")
        elif size == 2:
            b = _read_bytes(process, fa, 2)
            parts.append(
                f"{fname}=<unreadable>"
                if b is None
                else f"{fname}={struct.unpack('<h', b)[0]}"
            )
        elif size == 4:
            v = _i32(process, fa)
            parts.append(f"{fname}=<unreadable>" if v is None else f"{fname}={v}")
        elif size == 8:
            v = _u64(process, fa)
            if v is None:
                parts.append(f"{fname}=<unreadable>")
            else:
                parts.append(f"{fname}={struct.unpack('<q', struct.pack('<Q', v))[0]}")
        else:
            parts.append(f"{fname}=<{size}B>")
    return f"{tname}{{{', '.join(parts)}}}"


def _format_ref(
    process: lldb.SBProcess, addr: int, cat: dict, depth: int = 0
) -> str:
    if addr == 0:
        return "null"
    if addr in _SEEN:
        return f"<cycle 0x{addr:x}>"
    name_off, _, _, _, _, _, _ = _cfg(cat)
    tname = _type_name_from_meta(process, addr, name_off)
    if tname is None:
        return f"<unreadable @0x{addr:x}>"
    _SEEN.add(addr)
    try:
        if tname == "String":
            return _format_string(process, addr, cat, depth)
        if tname.endswith("[]") or (cat.get("arrayMetas") and any(
            a.get("name") == tname for a in cat.get("arrayMetas") or []
        )):
            return _format_array(process, addr, tname, cat, depth)
        return _format_object(process, addr, tname, cat, depth)
    finally:
        _SEEN.discard(addr)


class JNativeRefSynthProvider:
    """Synthetic children for JNative managed references (ptr / typed pointers)."""

    def __init__(self, valobj: lldb.SBValue, internal_dict):
        self.valobj = valobj
        self.update()

    def update(self):
        self._summary = None
        process = _process(self.valobj)
        if process is None:
            self._summary = "<no process>"
            return
        addr = _ptr_value(self.valobj)
        cat = _load_catalog(self.valobj)
        global _SEEN
        _SEEN = set()
        self._summary = _format_ref(process, addr, cat, 0)

    def num_children(self):
        return 0

    def get_child_at_index(self, index):
        return None

    def has_children(self):
        return False


def jnative_ref_summary(valobj: lldb.SBValue, internal_dict) -> str:
    process = _process(valobj)
    if process is None:
        return "<no process>"
    addr = _ptr_value(valobj)
    if addr == 0:
        return "null"
    cat = _load_catalog(valobj)
    global _SEEN
    _SEEN = set()
    return _format_ref(process, addr, cat, 0)


def _type_name(valobj: lldb.SBValue) -> str:
    t = valobj.GetType()
    if not t or not t.IsValid():
        return ""
    return t.GetName() or ""


def jnative_pointer_recognizer(sbtype: lldb.SBType, internal_dict) -> bool:
    """Match bare ptr and JNative DI pointer-to-struct types."""
    name = sbtype.GetName() or ""
    if name in ("ptr", "void *", "void*"):
        return True
    # DIDerivedType pointer to our composites: "String *", "Person *", "int[] *", etc.
    if name.endswith(" *") or name.endswith("*"):
        base = name.replace(" *", "").replace("*", "").strip()
        if base in ("String", "__ClassMeta") or base.endswith("[]") or (
            base and base[0].isupper()
        ):
            return True
    return False


def __lldb_init_module(debugger: lldb.SBDebugger, internal_dict):
    category = "jnative"
    debugger.HandleCommand(f"type category define -e {category}")
    # Summary for recognized pointers
    debugger.HandleCommand(
        f'type summary add -w {category} '
        f'-F jnative_formatters.jnative_ref_summary '
        f'-x "^(ptr|void \\*|String \\*|.*\\[\\] \\*|[A-Z][A-Za-z0-9_]* \\*)$"'
    )
    # Also bind common DWARF names without regex escape issues
    for n in ("ptr", "void *", "String *"):
        debugger.HandleCommand(
            f'type summary add -w {category} '
            f'-F jnative_formatters.jnative_ref_summary "{n}"'
        )
    debugger.HandleCommand(f"type category enable {category}")
    print("JNative LLDB formatters loaded (memory-only, bounded reads)")
