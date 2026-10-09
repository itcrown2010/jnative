# iOS Calculator — test results

作者：张晓达

Date: 2026-10-08 (local)

## Verified

| Item | Result |
|------|--------|
| IR→WASM (not template) | Pass — WAT contains `_JN_CalcEngine_*` / `i64` ops |
| i64 source mutation proof | Pass — `add(12,30)=42`; `a+b+7` → `49` |
| Fixed-point suite (`verify_wasm.mjs`) | Pass — all cases including `0.1+0.2=0.3`, div0, overflow, rounding |
| Offline packaging | Pass — `www/` inside `.app` (html/js/wasm) |
| iPhone 15 simulator launch | Pass — display `0`, WASM loaded |
| Autodemo `12+30=` via real button `.click()` | Pass — screenshot `42` |
| iPhone SE layout | Pass — all keys visible (`03-se-launch.png`) |
| Swift typecheck / swiftc `.app` | Pass |

## Environment notes

- Host: Xcode 26.6 / iPhoneSimulator **26.5** SDK; installed Simulator runtime **iOS 17.5** only.
- `xcodebuild -destination …` often reports “iOS 26.5 is not installed”; build falls back to **swiftc** packaging (see `scripts/build_ios.sh`).
- When destination `OS=17.5,name=iPhone 15` is accepted, full xcodebuild also succeeds.

## Not verified / limits

- Physical device / Developer certificate signing — not attempted.
- Accessibility mouse taps via System Events — blocked (−25204); used button `.click()` autodemo instead.
- Scientific / percent / precedence — out of scope by design.
- wasm32-browser static field initializers remain zero — constants exposed via methods (`scale()`, `maxAbs()`).

## Screenshots

- `screenshots/01-launch.png` — main UI
- `screenshots/02-12-plus-30.png` — result `42`
- `screenshots/03-se-launch.png` — SE / small screen
