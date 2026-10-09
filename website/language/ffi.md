# FFI、原生内存与回调

## 概念

JNative 通过原生链接与注解/API（如 `@Native`、`Native.callback()`）与 C 协作，**不是** JNI 兼容层。

## 最小示例

仓库示例：`examples/NativeCallback.java`（若存在）：

```bash
ls examples/NativeCallback.java
jnative examples/NativeCallback.java -o /tmp/NativeCallback
```

状态：**实验性**。WASM 使用单独 ABI（`Wasm32BrowserAbi`），不可与原生 FFI 文档混用。

## 明确语义

- 回调函数指针生命周期：在原生侧仍可能调用时，托管侧必须保持可达。
- 效果注解（`@NoGC`、`@NoThrow` 等）见 `docs/effects.md` / `docs/no-gc.md`（偏高级）。

## 常见错误

- 把 JAR/JNI 教程当 JNative FFI。
- 在 GC 移动对象后缓存裸指针（若 API 未提供句柄模型）。

## 支持限制

通用绑定生成器、稳定 ABI 承诺：无。跨平台 FFI **未验证**。

## 相关

- [API：FFI](/api/ffi) · `docs/native-callback.md`
