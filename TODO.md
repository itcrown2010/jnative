# TODO / Roadmap

作者：张晓达

## Done

- 0.1 Language Core
- 0.2 Object Model
- 0.3 Inheritance & Virtual Dispatch
- 0.4 Runtime Foundation
- 0.5 Native Garbage Collector
- 0.6 Exceptions
- 0.7 Native Interop / FFI

- Canvas input: right/middle button, modifier bits, hover (`onMouseMove` with button 0),
  `onMouseWheel`, `onMouseEnter`/`onMouseExit` (macOS Cocoa + headless `UiTest.mouseButton/mouseWheel/mouseHover`)
- Static field initializers using constants in array dimensions (`static int[][] g = new int[N][N]`)
  no longer crash the semantic analyzer
- Showcase: `examples/starcraft` — deterministic RTS with AI, replays, fog of war, synthesized audio
- JNative Studio (`tools/jnative-studio`, see its README): IDE written in JNative — UI designer (toolbox, WYSIWYG canvas,
  property inspector, event binding, device presets, live preview, code navigation), project explorer, code editor,
  build/run with streamed output and a problems list. Platform additions: CoreText system fonts + anti-aliased
  shapes in `Graphics`, hiDPI `Canvas`, transparent canvases, clipped/wheel-aware `Panel`, cursors, non-blocking
  child-process reads (`Process.readStdoutNb/pollExit`), `Window.snapshot(path)`

## Next

- Records, default/static interface methods, `Type::new`
- `for (T x : list)` inside generic methods (IrGenerator loop variable type)
- `Comparable` bound for `String` (builtin String has no itable)
- Non-ASCII `char` println (prints `?`)
- UTF-16 String migration
- Threads + GC safepoint coordination
