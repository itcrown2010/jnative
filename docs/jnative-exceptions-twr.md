# JNative — try/catch/finally & try-with-resources（本轮交付）

作者：张晓达

## 修改说明

1. **finally 突然完成**  
   - 进入 finally 时 stash/clear pending，避免 finally 内 `new`/调用被原异常 divert。  
   - 正常结束后 restore；finally 自身 throw/return 则替换原退出原因。  
   - `innermostFinallyFrame` 按 deque 头=最内层扫描；嵌套 return 顺序正确。

2. **try-with-resources**  
   - 嵌套 try 帧：内层关闭资源，外层 catch + 用户 finally（JLS：close → catch → finally）。  
   - 初始化在受保护区内；后一个 open 失败会关闭已成功资源。  
   - null 跳过 close；资源变量隐式 final。

3. **受抑制异常**  
   - `Throwable` 布局 +8：`suppressed`（32 字节）。  
   - 运行时 `jnative_throwable_add_suppressed` / `get_suppressed`。  
   - 语言 API：`addSuppressed` / `getSuppressed`。

4. **catch**  
   - 声明序 + 子类型匹配；不可达 catch 诊断；`throw null` → NPE。

## 测试结果

```text
mvn -Dtest=ExceptionEndToEndTest,ResourceEndToEndTest -DforkCount=1 test
Tests run: 35, Failures: 0, Errors: 0, Skipped: 0
```

含：嵌套 finally、finally 替换 throw、return 先求值、break 穿越、TWR 关闭序、初始化失败、抑制异常、O0/默认优化一致、不可达 catch。

## 正例 / 负例

正例见上述 e2e。负例：

- 不可达 catch → semantic error `unreachable catch clause`
- `throws` / multi-catch / TWR 已有变量形式 → 不支持（解析或未实现）
- wasm32-browser：与 native 同语义（见 `docs/wasm32-browser-exceptions.md`）

## 后端差异与剩余限制

| 项 | 状态 |
|----|------|
| Native LLVM | 完整 pending-exception 模型 |
| WASM browser | 同模型；`exc-stress` 验收 PASS |
| checked `throws` | 未做 |
| multi-catch / precise rethrow | 未做 |
| TWR 变量资源 | 未做 |

语义细节：`docs/exceptions.md`。
