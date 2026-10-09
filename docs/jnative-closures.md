# 闭包 / Lambda / 方法引用（可靠逃逸里程碑）

**日期：** 2026-10-09  
**架构：** 复用 0.10 `CallableBinding` + `MakeCallable` / `InvokeCallable` + GC 托管 env，不另建平行机制。

## 语义（Java 风格）

| 规则 | 行为 |
|---|---|
| 目标类型 | 由赋值/参数/返回的函数式接口（SAM）确定 |
| SAM | 恰有一个有效抽象方法（继承折叠等价签名；排除 Object 方法） |
| 捕获局部/参数 | 须 **final 或有效 final**；赋值后捕获 → 编译错误 |
| 捕获基础值 | 创建闭包时**拷贝值**进 env 字段 |
| 捕获引用 | 可改对象字段；不可重新绑定被捕获局部变量 |
| `this` | 捕获外层实例；Lambda 内 `this` 仍是外层实例 |
| `return` | 只离开当前 Lambda，不退出外层方法 |
| 引用身份 | **不承诺** 闭包对象 `==` 稳定 |
| 线程 | 共享闭包不自动使捕获对象线程安全 |

```java
interface IntUnary { int apply(int value); }
class Functions {
    static IntUnary addBy(int base) {
        return value -> value + base; // base 逃逸后仍正确
    }
}
```

## 捕获环境与生命周期

- 无捕获：SAM `{code, env=null}`。
- 有捕获：合成 `__jn.Env.N`（普通托管对象），字段 `c0…`；创建时从局部 **load 值** 写入，**不**保留栈地址。
- 嵌套 Lambda：内层捕获向上传播，外层 env 也保留所需局部，以便内层在外层调用时再建闭包。
- GC：env 与捕获引用为普通堆引用；移动后经 SAM→env 边更新。
- 逃逸分析不得把已返回方法的捕获环境当栈对象释放（env 随 callable 逃逸）。

## 方法引用

| 形式 | 状态 |
|---|---|
| 静态 `Type::staticMethod` | 支持 |
| 已绑定 `expr::instanceMethod` | 支持；接收者**创建时求值一次**并 `nullCheck` |
| 未绑定 `Type::instanceMethod` | 支持；SAM 首参为接收者，调用时 `nullCheck` |
| 构造器 `Type::new` | **明确拒绝**（可解析，语义阶段诊断） |
| 数组构造器引用 | **不支持**（未实现） |

## 调用与运行时

支持的 invoke helper（缺签名 → **硬错误**，不静默落到错误 arity）：

`jnative_invoke_void`、`jnative_invoke_ptr`、`jnative_invoke_i32_i32`、`jnative_invoke_i32_i32_i32`、`jnative_invoke_void_ptr`、`jnative_invoke_i32_ptr`、`jnative_invoke_void_ptr_ptr`、`jnative_invoke_ptr_ptr`、`jnative_invoke_i32_ptr_ptr`。

用户定义 SAM 经 `__jn.IFaceAdapter.*` 适配到同一约定。

## Native.callback

见 `docs/native-callback.md`。要点：

- **禁止捕获** Lambda（编译期拒绝）。
- Trampoline + 槽位表；`pointer()` / `close()`；外线程 `jn_thread_attach_foreign`。
- 异常不穿越 C ABI（返回零类默认值）。
- 不是「裸函数指针 + 环境」假装成普通 C 函数指针。

## 后端限制

| 后端 | 闭包 |
|---|---|
| Native (LLVM) | 支持（本里程碑验收） |
| wasm32-browser | **明确拒绝**（`MakeCallable` / `InvokeCallable`），错误信息指向改写或改用 native |

## 调试

| 信息 | 状态 |
|---|---|
| Lambda 源位置 | DI 随 enclosing 编译单元（单文件路径已有；多包 DI 见能力组合文档缺口） |
| 可辨识栈名 | 代码符号 `_JN_lambda_N` / `_JN_thunk_*`（非 Java 源名） |
| 捕获值查看 | env 为普通对象字段 `c0…`；**无**伪造源码级捕获变量名映射 |

## 测试

- `ClosureEndToEndTest` — 逃逸+GC、双闭包、嵌套、中文、this、有效 final、集合+GC、异常/finally、绑定接收者一次、未绑定引用、ctor 拒绝、WASM 拒绝；O0/O1；Java 对照子集  
- `CallableEndToEndTest` / `CallableSemanticTest` — 既有回归  
- `NativeCallbackEndToEndTest` — 真实 C trampoline（不由普通 Lambda 代替）
- 函数式 API / 集合·UI·异步回调：见 `docs/jnative-functional-callbacks.md` 与 `FunctionalCallbacksEndToEndTest`

```bash
mvn -q -Dtest=ClosureEndToEndTest,CallableEndToEndTest,CallableSemanticTest,NativeCallbackEndToEndTest,FunctionalCallbacksEndToEndTest test
```

## 示例

`examples/LambdaClosure.java` — 返回捕获闭包 + GC + 跨线程调用。  
`examples/functional/` — 集合筛选映射、UI 订阅取消、异步 Future 回调。
