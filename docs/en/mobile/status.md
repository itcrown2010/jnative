# Mobile status (honest)

> **JNative does not currently provide a production-ready mobile application toolchain** with native UIKit/SwiftUI or Android View/Compose.

## What works today (Experimental)

- Compile core logic to **wasm32-browser**
- Embed in **Android WebView** / **iOS WKWebView** shells (guide-tasks / sudoku)
- Asset sync scripts; bridge host patterns
- Simulator / `assembleDebug` builds on developer machines (device e2e incomplete)

## What is not mobile support

| Claim | Reality |
|-------|---------|
| FFI available on desktop | ≠ Android/iOS app SDK |
| Java-like syntax | ≠ shipping on Play/App Store automatically |
| NDK / JNI / SwiftUI product | **Not** the current path |

## Planned / required for a real native mobile product

Separate native mobile runtime, UI bindings, packaging, signing, store pipelines — **not** documented as available.

Full matrix: [capability-matrix.md](capability-matrix.md) · [overview.md](overview.md)
