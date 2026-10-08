# JNative Mobile MVP — Calculator

Shared **JNative → WebUI/WASM** calculator with thin **Web / Android / iOS** containers.
Platform shells do **not** reimplement addition or input validation.

## Layout

```text
examples/mobile/calculator/
├── src/CalculatorApp.java   # business logic (parse + add)
├── web/                     # HTML shell + bridge client
├── dist/                    # fresh build output (generated)
├── android/                 # Kotlin WebView container
├── ios/                     # Swift WKWebView container
└── scripts/
```

## Prerequisites

- Built toolchain: `mvn -DskipTests package` → `target/jnative.jar`
- [WABT](https://github.com/WebAssembly/wabt) `wat2wasm` on `PATH`
- Node.js (for `verify_web.sh` / tests)
- Android: SDK (`ANDROID_HOME`, default `~/Library/Android/sdk`) + Gradle
- iOS: Xcode + Simulator (device signing is on you; no secrets in repo)

## Build Web (source of truth)

```bash
./scripts/build_web.sh
./scripts/verify_web.sh
```

Serve locally (any static server):

```bash
cd dist && python3 -m http.server 8765
# open http://127.0.0.1:8765/
```

Expected: inputs `12` and `30` → result `42`. Invalid / empty inputs show clear errors.
Last successful inputs restore after reload (web: `localStorage` — **not** secure storage).

## Sync + mobile

```bash
./scripts/sync_mobile_assets.sh   # copies dist → android assets + ios www
./scripts/build_android.sh        # rebuild web, sync, assembleDebug
./scripts/build_ios.sh            # rebuild web, sync, xcodebuild (simulator)
```

Override iOS simulator:

```bash
IOS_DESTINATION='platform=iOS Simulator,name=iPhone 16' ./scripts/build_ios.sh
```

## Bridge protocol (v1)

Request:

```json
{ "id": "<uuid>", "v": 1, "method": "platform.getName", "params": {} }
```

Response:

```json
{ "id": "<uuid>", "ok": true, "result": "web" }
```

or

```json
{ "id": "<uuid>", "ok": false, "error": { "code": "METHOD_NOT_ALLOWED", "message": "..." } }
```

Lifecycle events (not request/response):

```json
{ "type": "event", "name": "lifecycle.foreground", "params": {} }
```

**Whitelist:** `platform.getName`, `storage.get`, `storage.set`, `storage.remove`  
Params are type/length-checked. Client times out (default 5s). Page hide/destroy clears pending requests. Hosts do not `eval` page strings as code. Only the local app page origin/file URL may use the bridge.

KV storage is ordinary preferences / `localStorage` — **not** for passwords or tokens.

## Prove real IR → WASM

```bash
# 1) 12+30=42 from current source
./scripts/verify_web.sh

# 2) Change add() in src/CalculatorApp.java (e.g. return a + b + 1)
# 3) Rebuild and confirm 12+30 => 43
./scripts/build_web.sh
# then re-run the node check in verify_web.sh / MobileCalculatorWasmTest
```

JUnit: `MobileCalculatorWasmTest` rebuilds from source and asserts both the `42` path and a mutated `43` path.

## Limits (honest)

- wasm32-browser lowers a **subset** of IR (ints, string length/charAt, loops/branches via dispatcher, statics, alloc). Unsupported IR **errors** — no silent defaults.
- Full WebUI reactive counter IR is **not** guaranteed this round (templates removed).
- No mobile native GC, no Android JAR/AAR compatibility, no cross-platform native UI toolkit.
- Android/iOS “project present” ≠ verified on device unless the scripts succeed in your environment.

## Acceptance

Same `CalculatorApp.java` drives browser and mobile WebViews; shells only provide platform name, KV, and lifecycle over the bridge.

## Status snapshot (2026-10-08)

| Surface | Status |
|---------|--------|
| Web rebuild + WASM `12+30=42` + mutate-add proof | Verified (`verify_web.sh`, JUnit) |
| Bridge whitelist / bad params / timeout / destroy | Verified (Node harness) |
| Android `assembleDebug` | Verified (APK produced) |
| Android emulator UI run | Blocked here (emulator exits) |
| iOS `xcodebuild` simulator binary | Verified (`generic/platform=iOS Simulator`) |
| iOS Simulator boot + UI | Not verified (need matching Simulator runtime) |

Details: `docs/jnative-mobile-mvp-report.md`
