# Debugging

| Target | Status |
|--------|--------|
| Native (macOS arm64) | CodeLLDB / `--debug` DWARF path — Implemented |
| wasm32-browser | **Not supported** (explicitly rejected) |
| Android / iOS containers | **Not supported** for LLDB/WASM source debug |

Generate debug info with `jnative … --debug` (prefer `-O0` for stepping).
