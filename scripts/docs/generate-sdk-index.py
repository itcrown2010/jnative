#!/usr/bin/env python3
"""Generate docs/en/_generated/sdk-index.json from stdlib + known builtins.
Author: Zhang Xiaoda
Does not invent APIs — lists classes found in stdlib .java files and builtin names.
"""
from __future__ import annotations

import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
STDLIB = ROOT / "src/main/resources/stdlib"
OUT = ROOT / "docs/en/_generated/sdk-index.json"

CLASS_RE = re.compile(r"^(?:public\s+)?(?:abstract\s+)?(?:final\s+)?class\s+(\w+)", re.M)
IFACE_RE = re.compile(r"^(?:public\s+)?interface\s+(\w+)", re.M)

# Builtins installed in SemanticAnalyzer (audit-maintained subset)
BUILTINS = [
    {"name": "File", "module": "builtin", "methods": [
        "readString", "writeString", "readBytes", "writeBytes", "exists", "size", "delete"
    ], "status": "Experimental", "tests": ["FileIoEndToEndTest"], "examples": ["examples/file-io"]},
    {"name": "TcpServer", "module": "builtin", "status": "Experimental",
     "tests": ["NetworkEndToEndTest"], "examples": ["examples/tcp-echo"]},
    {"name": "TcpSocket", "module": "builtin", "status": "Experimental",
     "tests": ["NetworkEndToEndTest"], "examples": ["examples/tcp-echo"]},
    {"name": "Thread", "module": "builtin", "status": "Experimental",
     "tests": ["ThreadEndToEndTest"], "examples": ["examples/multithreading"]},
    {"name": "Mutex", "module": "builtin", "status": "Experimental",
     "tests": ["ThreadEndToEndTest"], "examples": ["examples/multithreading"]},
]


def scan_stdlib() -> list[dict]:
    types = []
    if not STDLIB.is_dir():
        return types
    for path in sorted(STDLIB.rglob("*.java")):
        text = path.read_text(encoding="utf-8", errors="replace")
        mod = path.relative_to(STDLIB).parts[0] if path.relative_to(STDLIB).parts else "unknown"
        for m in CLASS_RE.finditer(text):
            types.append({
                "name": m.group(1),
                "kind": "class",
                "module": mod,
                "source": str(path.relative_to(ROOT)),
                "status": "Experimental",
            })
        for m in IFACE_RE.finditer(text):
            types.append({
                "name": m.group(1),
                "kind": "interface",
                "module": mod,
                "source": str(path.relative_to(ROOT)),
                "status": "Experimental",
            })
    return types


def main() -> None:
    types = scan_stdlib()
    modules = sorted({t["module"] for t in types} | {"builtin"})
    doc = {
        "generator": "scripts/docs/generate-sdk-index.py",
        "note": "Class inventory from stdlib sources + curated builtins. Methods incomplete except builtins.",
        "modules": modules,
        "builtins": BUILTINS,
        "types": types,
        "examples": [
            "examples/file-io",
            "examples/http-server",
            "examples/json-demo",
            "examples/multithreading",
            "examples/tcp-echo",
            "examples/tutorials/cli-todo",
            "examples/notes-cli",
        ],
    }
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(doc, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {OUT} types={len(types)} modules={len(modules)}")


if __name__ == "__main__":
    main()
