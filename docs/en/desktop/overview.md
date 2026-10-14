# Desktop applications

**Status:** Experimental (macOS samples verified; Windows/Linux UI **not verified**)

JNative includes a desktop UI stdlib (`UiLib`, bindings) and samples (`examples/desktop-sample`, `activity-monitor`, `product-editor`, `guide-tasks` desktop).

## What works today

- Native macOS UI paths via Cocoa linkage when UI is included
- CLI-only apps everywhere clang works

## What is not claimed

- Pixel-perfect cross-platform UI “write once”
- Store packaging pipelines

See [native-ui.md](native-ui.md), [packaging.md](packaging.md), [../tutorials/desktop-app.md](../tutorials/desktop-app.md).
