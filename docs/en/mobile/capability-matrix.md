# Mobile Capability Matrix

> **JNative does not currently provide a production-ready mobile application toolchain** with native UIKit/SwiftUI or Android View/Compose controls.

What exists today is an **experimental** pattern: compile core logic to **wasm32-browser**, embed it in **Android WebView** / **iOS WKWebView**, and drive UI/network from a JS (or thin native) shell.

## Implemented (experimental)

| Item | Evidence |
|------|----------|
| wasm32-browser core export | `TargetProfiles`, guide-tasks / sudoku scripts |
| Android project shells | `examples/guide-tasks/mobile/android`, sudoku Android |
| iOS project shells | `examples/guide-tasks/mobile/ios`, sudoku iOS |
| Asset sync into containers | `sync_mobile_assets.sh` |
| Bridge host / capability discovery | BridgeHost + bridge.js (guide-tasks) |
| Local KV (SharedPreferences / UserDefaults) | ordinary storage — **not** a secure credential vault |

## Experimental / partial verification

| Item | Note |
|------|------|
| `assembleDebug` / Xcode Simulator build | Scripts exist; device install & touch e2e not fully automated |
| `jnative doctor --mobile` | Depends on local SDKs; not verified in every doc pass |
| Chrome browser e2e for web shell | guide-tasks `verify_browser.mjs` |

## Planned / Not Supported

| Item | Status |
|------|--------|
| Full native mobile UI SDK | **Not Supported** |
| JNI/NDK as primary app model | **Not** the shipping product path |
| App Store / Play release pipeline | **Not Supported** (no verified pipeline) |
| Equating FFI with “mobile apps” | **Incorrect** — do not document as such |

### Architecture (future vs today)

Today: **WebView + WASM**.  
Future native mobile runtimes, if any, must be documented separately once implemented **and** tested—not implied by FFI presence.

See [overview](overview.md), [architecture](architecture.md).
