#!/usr/bin/env python3
# Author: 张晓达
"""Add author attribution 张晓达 to project source files. Idempotent."""
from __future__ import annotations

import re
import subprocess
from pathlib import Path

AUTHOR = "张晓达"
ROOT = Path(__file__).resolve().parents[1]

SKIP_DIR = {
    ".git", "target", "build", "node_modules", "dist", ".jnative",
    "dSYM", ".idea", ".cursor",
}

SKIP_EXT = {
    "png", "jpg", "jpeg", "gif", "webp", "ico",
    "wasm", "wav", "mp3", "jar", "class", "o", "a", "so", "dylib",
    "gitkeep", "gitignore", "vscodeignore",
    "lock", "tsv", "pbxproj", "plist", "xcscheme",
    "zip", "gz", "pdf", "ttf", "woff", "woff2",
    "wat", "json",  # json has no comments; wat often generated
}

MARKERS = (
    f"@author {AUTHOR}",
    f"作者：{AUTHOR}",
    f"作者: {AUTHOR}",
    f"Author: {AUTHOR}",
    f"author: {AUTHOR}",
    f"Copyright (c) 2026 {AUTHOR}",
)


def iter_files() -> list[Path]:
    """Tracked files plus other on-disk sources, excluding build trees."""
    paths: set[Path] = set()
    try:
        out = subprocess.check_output(["git", "ls-files", "-z"], cwd=ROOT)
        for p in out.decode().split("\0"):
            if p:
                paths.add(ROOT / p)
    except Exception:
        pass
    for p in ROOT.rglob("*"):
        if not p.is_file():
            continue
        if any(part in SKIP_DIR for part in p.parts):
            continue
        paths.add(p)
    return sorted(paths)


def already(text: str) -> bool:
    return any(m in text for m in MARKERS)


def ext_of(path: Path) -> str:
    name = path.name
    if name == "SHA256":
        return "SHA256"
    if "." not in name:
        return ""
    return name.rsplit(".", 1)[-1].lower()


def add_java(text: str) -> str | None:
    if already(text):
        return None

    for m in re.finditer(r"/\*\*(.*?)\*/", text, re.DOTALL):
        body = m.group(1)
        after = text[m.end() : m.end() + 240]
        if not re.search(r"\b(class|interface|enum|record)\b", after):
            continue
        if "@author" in body:
            return None
        cleaned = body.rstrip()
        # Keep a trailing space so the following `*/` becomes ` */`
        new_body = cleaned + f"\n * @author {AUTHOR}\n "
        return text[: m.start(1)] + new_body + text[m.end(1) :]

    m = re.search(
        r"(?m)^(?:[ \t]*@[A-Za-z][^\n]*\n)*"
        r"[ \t]*(?:public |protected |private |abstract |final |sealed |static |non-sealed )*"
        r"(?:class|interface|enum|record)\b",
        text,
    )
    block = f"/**\n * @author {AUTHOR}\n */\n"
    if m:
        return text[: m.start()] + block + text[m.start() :]
    pkg = re.search(r"(?m)^package\s+[\w.]+;\s*\n", text)
    if pkg:
        return text[: pkg.end()] + "\n" + block + text[pkg.end() :]
    return block + text


def prepend_after_shebang_or_xml(text: str, line: str) -> str:
    if text.startswith("#!") or text.startswith("<?xml"):
        nl = text.find("\n")
        if nl < 0:
            return text + "\n" + line
        return text[: nl + 1] + line + text[nl + 1 :]
    return line + text


def add_hash_comment(text: str) -> str | None:
    if already(text):
        return None
    return prepend_after_shebang_or_xml(text, f"# Author: {AUTHOR}\n")


def add_slash_comment(text: str) -> str | None:
    if already(text):
        return None
    return prepend_after_shebang_or_xml(text, f"// Author: {AUTHOR}\n")


def add_c_block(text: str) -> str | None:
    if already(text):
        return None
    return f"/* Author: {AUTHOR} */\n" + text


def add_sql(text: str) -> str | None:
    if already(text):
        return None
    return f"-- Author: {AUTHOR}\n" + text


def add_md(text: str) -> str | None:
    if already(text):
        return None
    line = f"作者：{AUTHOR}\n\n"
    m = re.match(r"(#[^\n]*\n)([ \t]*\n)?", text)
    if m:
        return text[: m.end()] + line + text[m.end() :]
    return line + text


def add_html(text: str) -> str | None:
    if already(text):
        return None
    comment = f"<!-- Author: {AUTHOR} -->\n"
    m = re.search(r"(?is)<!doctype html[^>]*>\s*", text)
    if m:
        return text[: m.end()] + comment + text[m.end() :]
    m = re.search(r"(?is)<html[^>]*>\s*", text)
    if m:
        return text[: m.end()] + comment + text[m.end() :]
    return comment + text


def add_xml(text: str) -> str | None:
    if already(text):
        return None
    return prepend_after_shebang_or_xml(text, f"<!-- Author: {AUTHOR} -->\n")


def process(path: Path) -> bool:
    ext = ext_of(path)
    if ext in SKIP_EXT or ext == "SHA256":
        return False
    try:
        raw = path.read_bytes()
    except OSError:
        return False
    if b"\0" in raw[:8192]:
        return False
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError:
        return False

    if ext in {"java", "jn"}:
        new = add_java(text)
    elif ext == "md":
        new = add_md(text)
    elif ext in {"sh", "bash", "py", "toml", "yml", "yaml", "properties"}:
        new = add_hash_comment(text)
    elif ext in {"c", "h", "css"}:
        new = add_c_block(text)
    elif ext in {"js", "mjs", "ts", "tsx", "swift", "kt", "kts"}:
        new = add_slash_comment(text)
    elif ext == "sql":
        new = add_sql(text)
    elif ext in {"html", "htm"}:
        new = add_html(text)
    elif ext == "xml":
        new = add_xml(text)
    elif ext in {"bat", "cmd"}:
        new = None if already(text) else f"REM Author: {AUTHOR}\n" + text
    elif ext in {"tmpl", "example", "txt", "project", "jnpkg"}:
        if text.lstrip().startswith("{"):
            return False
        new = add_hash_comment(text)
    else:
        return False

    if new is None or new == text:
        return False
    if "\r\n" in text and "\r\n" not in new:
        new = new.replace("\n", "\r\n")
    path.write_text(new, encoding="utf-8")
    return True


def main() -> None:
    changed = 0
    for path in iter_files():
        if path.is_file() and process(path):
            changed += 1
    print(f"updated={changed}")


if __name__ == "__main__":
    main()
