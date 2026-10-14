# Mobile

**Status:** Experimental — **WebView + wasm32-browser cores**, not a full native mobile UI SDK

## Current status

| Piece | Reality |
|-------|---------|
| Android | Gradle WebView host; `assembleDebug` verified for guide-tasks; device install often unverified |
| iOS | WKWebView host; Simulator builds verified for samples; TestFlight/store **not** claimed |
| Native Kotlin/Swift UI | **Not supported** as the app UI model |
| JNI / NDK app model | Not the primary path—FFI exists for native code generally |

Architecture diagram: shell (Android/iOS) ↔ JS bridge ↔ wasm business core.

Details: [architecture.md](architecture.md), [android.md](android.md), [ios.md](ios.md).
