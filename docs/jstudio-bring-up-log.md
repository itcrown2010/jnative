# JStudio Bring-up Log

作者：张晓达

## Blocker 1
- Symptom: GUI build fails on `jnative_ui_cocoa.m` (`id obj = ...` parse errors)
- Category: F — platform backend (ObjC type `id` shadowed by C parameter named `id`)
- Status: FIXED — rename parameters to `peerId`

## Blocker 2
- Symptom: Native window opens but toolbar/explorer/editor not attached as subviews
- Category: D — UI runtime (`Container.add` never reparented native peers)
- Status: FIXED — `jnative_ui_reparent` + Cocoa `addSubview` + call from `Container.add`

## Blocker 3
- Symptom: Controls laid out with top-left coords appeared wrong under AppKit
- Category: F — Cocoa coordinate origin
- Status: FIXED — Y-flip in `jnative_ui_platform_set_bounds`

## Blocker 4
- Symptom: Agent environment cannot `screencapture` (TCC Screen Recording)
- Category: environment limitation (not JStudio)
- Status: OPEN for screenshots — UI proven via live NSView hierarchy dump + AX window title

## Result
- Build: PASS → `build/studio/jstudio` (Mach-O arm64)
- Launch: PASS → window title `JNative Studio`, process without JVM
