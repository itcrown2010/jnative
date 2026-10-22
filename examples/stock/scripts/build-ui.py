#!/usr/bin/env python3
"""Generate ASCII-only public/ files from ui-src/ (the runtime's static file server is not UTF-8 safe)."""
import os

root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
src = os.path.join(root, "ui-src")
dst = os.path.join(root, "public")
os.makedirs(dst, exist_ok=True)


def esc_js(text):
    out = []
    for ch in text:
        o = ord(ch)
        if o < 128:
            out.append(ch)
        elif o < 0x10000:
            out.append("\\u%04x" % o)
        else:
            o -= 0x10000
            out.append("\\u%04x\\u%04x" % (0xD800 + (o >> 10), 0xDC00 + (o & 0x3FF)))
    return "".join(out)


def esc_html(text):
    return "".join(ch if ord(ch) < 128 else "&#x%x;" % ord(ch) for ch in text)


for name in sorted(os.listdir(src)):
    if name.endswith(".png"):
        open(os.path.join(dst, name), "wb").write(open(os.path.join(src, name), "rb").read())
        print("copied", name)
        continue
    text = open(os.path.join(src, name), encoding="utf-8").read()
    if name.endswith(".js"):
        text = esc_js(text)
    elif name.endswith(".html"):
        text = esc_html(text)
    else:
        text = esc_js(text)
    open(os.path.join(dst, name), "w", encoding="ascii").write(text)
    print("built", name, len(text))
