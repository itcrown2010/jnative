# Native code generation

IR → `LlvmIrGenerator` → `.ll` → `clang` + runtime objects (`NativeLinker`). Wasm path: `backend.wasm`.
