# Mobile architecture

```text
┌─────────────────────────┐
│ Android WebView / WKWeb │
│   JS shell + bridge     │
└───────────┬─────────────┘
            │
            ▼
     wasm32-browser core
     (JNative business logic)
```

Platform file/share bridges exist in guide-tasks. This is **not** React Native / Flutter / pure native UI.
