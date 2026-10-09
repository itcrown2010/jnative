# API：基础类型与字符串

**版本**：JNative 0.32  
**源码**：语言内建 + `stdlib/collections`（`StringBuilder` 等）

## String（语言/运行时）

| 项 | 内容 |
|----|------|
| 常见用法 | 字面量、`+`、`length()`、`compareTo`（模板测试使用） |
| 异常 | 待完善 |
| null | 对 null 调用实例方法 → 运行时失败（勿依赖 SE 信息字符串） |
| 线程 | 不可变使用一般可跨线程读；具体实现待完善 |
| 目标 | 原生 **实验性**；WASM **实验性** |
| 可运行 | `examples/guide-hello/tests/StringTest.jn` |

## StringBuilder

签名以提取文件为准：

```bash
./scripts/docs/extract-api.sh
grep -A2 "class StringBuilder" website/api/_generated/collections.txt
```

| 项 | 内容 |
|----|------|
| 资源 | 托管对象，GC 回收 |
| 状态 | 签名可提取；逐方法行为 **待完善** |

更多生成列表见 `_generated/`。
