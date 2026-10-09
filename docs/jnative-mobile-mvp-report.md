# JNative Mobile MVP — Delivery Report

作者：张晓达

Date: 2026-10-08  
Host: macOS (darwin), Xcode 26.6, Android SDK at `~/Library/Android/sdk`

> Tooling follow-up: see `docs/jnative-mobile-tooling.md` for templates, CLI, and handshake.

## Delivered

| Item | Location |
|------|----------|
| Real IR→WAT→WASM (no templates) | `BrowserWatEmitter`, `WebUiIrToWat`, `JsGlueGenerator` |
| Shared calculator source | `examples/mobile/calculator/src/CalculatorApp.java` |
| Web shell + bridge | `examples/mobile/calculator/web/` |
| Android Kotlin WebView | `examples/mobile/calculator/android/` (+ Gradle wrapper) |
| iOS Swift WKWebView | `examples/mobile/calculator/ios/` |
| Scripts | `examples/mobile/calculator/scripts/*` |
| Tests | `MobileCalculatorWasmTest`, rebuilt `WebUiReactiveTest` |

## Verification results (this machine)

| # | Check | Result |
|---|-------|--------|
| 1 | Rebuild web from source (`build_web.sh` / `verify_web.sh`) | **PASS** |
| 2 | `12` + `30` → `42` via WASM exports in Node | **PASS** |
| 3 | Mutate `add` to `a+b+1` → `43` (`MobileCalculatorWasmTest`) | **PASS** |
| 4 | Empty / invalid → error codes 1 / 2 | **PASS** |
| 5 | Persist + restore | Implemented (web `localStorage`, Android prefs, iOS UserDefaults); interactive restart not automated in CI |
| 6 | Bridge unknown method / bad params / timeout | Client whitelist + timeout in `bridge.js`; host rejects unknown methods |
| 7 | Destroy clears pending | `JNativeBridge.destroy()` |
| 8a | Android `assembleDebug` | **PASS** → `android/app/build/outputs/apk/debug/app-debug.apk` |
| 8b | Android emulator run (`Pixel_6_Pro_API_33`) | **BLOCKED** — emulator process exits immediately (vulkan/lib load); APK not installed on a running device |
| 9a | iOS `xcodebuild` (`generic/platform=iOS Simulator`) | **PASS** — produced `Calculator.app` under DerivedData |
| 9b | iOS Simulator *boot + UI run* | **NOT VERIFIED** — no iOS 26.5 Simulator runtime installed to launch; build ≠ run |

Do **not** treat Android APK success as iOS UI verification, or an iOS compile as a booted simulator session.

## Limits

- wasm32-browser lowers a practical subset (ints, string length/charAt, CFG dispatcher, statics, bump alloc). Unsupported IR **throws**.
- Full WebUI reactive counter IR is not guaranteed (fixed templates removed).
- Ordinary KV only — not secure storage for secrets.
- No mobile native GC, no Android JAR/AAR, no cross-platform native UI toolkit.
- Persistence/lifecycle exercised in shell code paths; full UI click-through on device/simulator pending environment fixes above.

## How to re-verify

```bash
mvn -DskipTests package
./examples/mobile/calculator/scripts/verify_web.sh
mvn -Dtest=MobileCalculatorWasmTest,WebUiReactiveTest test
export ANDROID_HOME=$HOME/Library/Android/sdk
./examples/mobile/calculator/scripts/build_android.sh
./examples/mobile/calculator/scripts/build_ios.sh
```
