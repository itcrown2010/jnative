# 统一诊断说明

CLI 文本、`--format json` 与 VS Code Problems 使用同一 `Diagnostic` 事实来源。

## 字段

| 字段 | 说明 |
|------|------|
| severity | `error` / `warning` / `note`（JSON 中 note → `hint`） |
| location | `file` + 1-based `line`/`column`；工具错误可用 `<unknown>`，**不伪造**用户源码位置 |
| message | 简明说明 |
| code | 稳定诊断码（见下） |
| hint | 可执行修复建议 |
| related | 关联位置列表（可选） |

## 诊断码

| 码 | 类别 | 含义 |
|----|------|------|
| JN1001 | 源码 | 语法 |
| JN1002 | 源码 | 语义 |
| JN2001 | 依赖 | 包解析失败 |
| JN2002 | 依赖 | 包 requires 与目标不匹配 |
| JN2101 | 目标 | 源码 API 在目标上不可用 |
| JN3001 | 工具 | clang/LLVM 等缺失 |
| JN3002 | 工具 | 原生库缺失 |
| JN3101 | 后端 | 后端明确不支持 |
| JN4001 | 链接 | 链接/发射失败 |
| JN5001 | 构建 | 取消 |
| JN5002 | 构建 | 旧产物保护 |

## JSON 示例

```json
{
  "success": false,
  "artifact": null,
  "compilerVersion": "0.32",
  "target": "wasm32-browser",
  "diagnostics": [
    {
      "file": "src/App.jn",
      "line": 3,
      "column": 5,
      "severity": "error",
      "code": "JN2101",
      "message": "filesystem (Files) is not available on target wasm32-browser …",
      "hint": "Use a native target, or replace this API …"
    }
  ]
}
```

文本形式：`file:line:col: error: [JN2101] …` 另起一行 `help: …`。
