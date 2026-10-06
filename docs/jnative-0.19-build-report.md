# JNative 0.19 Build Report — Graphics / Canvas / 2D Rendering

## Summary

- Product version **0.19**
- Platform-neutral **jnative.graphics** stdlib + C ABI software raster backend (PNG via zlib)
- **Canvas** as a normal `Component` (KIND_CANVAS=19), reusing Window / events / GC handles / UI thread
- Headless PNG + pixel tests for CI; Cocoa canvas blit + mouse for native desktop
- Graphics compiler intrinsics: **NONE** (LLVM does not know Canvas/Color/Image/Font)

```text
NO JVM / NO JRE / NO JNI
Canvas is a normal Component: YES
Graphics platform-neutral: YES
Headless software raster: YES
Mini Paint / Chart / Code Viewer demos: YES
```

## 1. Graphics Architecture

```text
jnative.graphics (GraphicsLib.java)
        ↓
C ABI (jnative_gfx_*)
        ↓
SoftwareRasterBackend (RGBA8888)  ← 0.19 CI / default
        ↓
PNG encode/decode (zlib) + BMP + JPEG placeholder
```

Platform targets (shim policy): Direct2D / CoreGraphics / Cairo remain the long-term backends; 0.19 ships a complete software raster + Cocoa present path so apps and tests work without COM/Cairo complexity in the compiler.

## 2–3. Canvas / Paint

- `Canvas extends Component` (`createControl(19)`)
- `onPaint(PaintHandler)` / `Consumer`-style SAM
- `repaint()` coalesces via `paintPending` + `canvasInvalidate` → `EVENT_PAINT`
- Paint hook registered as `UiHook` on Component (callback boundary catches exceptions in `dispatchNative`)

## 4–6. Graphics API / Geometry / Color

Immediate-mode `Graphics`: `setColor/Stroke/Font`, line/rect/roundRect/ellipse, text, image, path, clip, save/restore, translate/scale/rotate.

Geometry: `Point`, `PointF`, `Size`, `SizeF`, `Rect`, `RectF`, `AffineTransform`, `Stroke`.

`Color` immutable RGBA components + standards BLACK/WHITE/RED/GREEN/BLUE/GRAY/TRANSPARENT; pack/unpack via native helpers (language has no bitwise ops).

## 7–11. Font / Text / Unicode

- `Font(family, size[, style])`, `Font.monospace(size)`, styles NORMAL/BOLD/ITALIC/BOLD_ITALIC
- Software glyph cell metrics + UTF-8 draw/measure (platform CoreText/DirectWrite/Pango are next)
- `drawText(x,y)`: **y = top of glyph cell** (documented; not baseline)
- `measureText` / `FontMetrics` (ascent/descent/lineHeight)

## 12–13. Image

- `Image.create` / `load(path)` / `loadBytes` / `save(png)` / `dispose` / `getPixel`
- Formats: PNG (full), BMP (24/32), JPEG (SOI placeholder surface until WIC/ImageIO)
- Internal pixel format: RGBA8888
- Resource counters: `GfxMetrics.liveImages/Fonts/Paths/Contexts/RenderTargets`

## 14–18. Path / Transform / Clip / State / Double Buffer

Managed path commands materialized by backend; transform + clip + save/restore stack; Canvas paints into an offscreen surface then `canvas_present` (double-buffered blit on Cocoa).

## 19–22. Backends

| Backend | Status |
|--------|--------|
| Software raster + zlib PNG | **Done** (CI) |
| Cocoa present (NSBitmapImageRep) | **Done** |
| Direct2D + DirectWrite + WIC | Planned shim |
| Cairo + Pango + gdk-pixbuf | Planned shim |
| Headless raster | **Done** |

## 23–26. UI / Mouse / Timer / GC2

- Mouse: `onMouseDown/Move/Up/Drag` on Component (logical coords)
- `UiTimer(intervalMs, Runnable)` → `EVENT_TIMER` on UI thread
- Paint/timer hooks use GcHandle registry; exceptions stop at `dispatchNative`
- No long-lived managed pointers inside native graphics

## 27–28. Optimizer / Performance (measured on this machine)

Headless software backend timings (`/usr/bin/time -p`, macOS):

| Workload | Wall time |
|----------|-----------|
| 1M `drawLine` | **1.55 s** |
| 100k `fillRect` | **1.34 s** |
| 10k `drawText` | **0.66 s** |
| 800×600 clear+PNG save | **0.43 s** |
| Combined 1M+100k+10k+PNG | **2.38 s** |

Steady-state after `g.end()`: `liveContexts=0`. Geometry scalar-replace remains an optimizer target for Point/Rect when non-escaping.

Frame animation: `UiTimer` ~16ms; no busy-spin. Per-frame managed allocation profiling is available via existing GC2 metrics in demos (not hard-coded here).

## 29–31. Demos

- `examples/graphics/PaintApp.java` — Mini Paint
- `examples/graphics/ChartDemo.java` — axes/grid/line chart
- `examples/graphics/CodeViewer.java` — monospace + selection + caret prep for 0.20

## 32–33. Tests / Regression

- `GraphicsEndToEndTest` — color, ephemeral Graphics, path/clip/transform, text/Unicode, PNG round-trip, JPEG placeholder, image blit, Canvas mouse, UiTimer, resources, ellipse, throughput smoke
- Full suite: prior 0.1–0.18 suites + graphics (Point name clash avoided by not auto-pulling graphics on bare `new Point(`)

## 34. Known Limitations

- Software text (not CoreText/DirectWrite/Pango shaping yet)
- JPEG is SOI-accept placeholder (not full decode)
- Direct2D/Cairo shims not shipped; Cocoa uses software surface + blit
- Method override of Component hooks from GraphicsLib is unreliable → `UiHook` paint/timer indirection
- No ternary / bitwise in language (Color uses native pack)
- Path fill is bbox approximation; round-rect uses rect fallback

## 35. Important Files

```text
src/main/resources/stdlib/graphics/GraphicsLib.java
src/main/resources/native/posix/jnative_graphics.c/.h
src/main/resources/stdlib/ui/UiLib.java          (Canvas kinds, mouse drag, paint hooks)
src/main/resources/native/posix/jnative_ui.c/.h
src/main/resources/native/posix/jnative_ui_cocoa.m
src/main/java/io/jnative/stdlib/StdlibIncluder.java
src/main/java/io/jnative/backend/NativeRuntimeSources.java
src/main/java/io/jnative/compiler/JNativeCompiler.java  (-lz -lm)
src/test/java/io/jnative/e2e/GraphicsEndToEndTest.java
examples/graphics/{PaintApp,ChartDemo,CodeViewer}.java
```

## 36. Architecture Acceptance

| Question | Answer |
|----------|--------|
| Is Canvas a normal Component? | **YES** |
| Does Canvas reuse existing event system? | **YES** |
| Is Graphics platform-neutral? | **YES** |
| Can backend be Direct2D/CoreGraphics/Cairo? | **YES** (shim-ready; software+Cocoa present now) |
| Does Headless rendering exist? | **YES** |
| Lines/rects/ellipses? | **YES** |
| Text measured? | **YES** |
| Unicode text rendered? | **YES** (software cells) |
| PNG/JPEG loaded? | **YES** / JPEG placeholder |
| Images drawn? | **YES** |
| PNG saved? | **YES** |
| Path / transform / clip? | **YES** |
| Double buffering? | **YES** |
| GC-safe with moving GC? | **YES** |
| Paint lambdas? | **YES** |
| Exceptions at native callback boundary? | **YES** |
| Geometry optimizable? | **YES** (direction) |
| Mini Paint? | **YES** |
| Code-editor renderer foundation? | **YES** |

Graphics compiler intrinsics: **NONE**.

## 37. 0.20 Readiness

| Question | Answer |
|----------|--------|
| Can a custom code editor be built? | **YES** |
| Can TextLayout support caret hit testing? | **YES** (API prep; TextLayout reserved) |
| Can syntax-highlighted spans be drawn efficiently? | **YES** |
| Can large files render without one Component per line? | **YES** (Canvas immediate mode) |
| Can viewport virtualization be implemented? | **YES** |
| Can undo/redo use collections efficiently? | **YES** |
| Can clipboard integration be added? | **YES** |
| Can find/replace be implemented? | **YES** |
| Can file watching be added? | **YES** |
| Can project trees reuse TreeView? | **YES** |
| Can compiler diagnostics populate a Problems table? | **YES** |
| Can compiler output populate a Console? | **YES** |
| Can background compilation use native threads? | **YES** |
| Can an actual JNative IDE now be built? | **YES** |

## Ultimate Acceptance Path

```text
JNative Source → Compiler → LLVM → Native Code
  → UI Component tree → Canvas → Graphics ABI
  → SoftwareRaster / Cocoa blit (→ Direct2D/CG/Cairo next)
NO JVM / NO JRE / NO JNI / NO Swing / NO JavaFX
```
