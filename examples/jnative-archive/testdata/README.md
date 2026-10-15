# Phase A fixtures

Small fixed inputs for CLI smoke and later benchmarks.

| Path | Role |
|------|------|
| `hello.txt` | ASCII text |
| `中文名.txt` | Unicode file name + content |
| `empty.dat` | Zero-byte file |

Larger corpora (many small files, >4GiB, media) are generated under `build/` by benchmark scripts in later phases — not committed.
