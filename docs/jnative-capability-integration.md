# 能力组合验收交付（订单处理）

**日期：** 2026-10-09  
**范围：** 核实前几轮语言能力 → 综合示例 → 交互缺陷 → Java/O0 回归 → GC/资源压力 → 调试验收。  
**不做：** 新 ERP 页面、移动容器、独立 IDE。

---

## 一、能力状态表（实现位置 + 真实测试）

| 能力 | 状态 | 实现位置 | 真实测试结果 |
|---|---|---|---|
| 多编译单元、package/import、访问控制 | **已验证** | `MultiUnitFrontend`、`SemanticAnalyzer` 可见性；stdlib 无 `public` 时对命名包导出（`defaultTypeVisibility` / `exportStdlibMember`） | `PackageAndAccessTest`、`MultipackageResolveIntegrationTest` PASS；订单示例多包构建成功 |
| cast、三元、位运算和移位 | **已验证** | `IrUtil` / `PrimitiveOps`；`>>>`/`&`/`|`/`~` | `PrimitiveOpsEndToEndTest`（既有套件） |
| switch、enum | **已验证** | enum 语义 + switch | `SwitchEnumEndToEndTest` PASS；订单 `OrderStatus`/`OrderTransitions` |
| final、确定赋值、增强 for | **已验证** | DAA + foreach lowering | `FinalDaForeachEndToEndTest` PASS |
| 泛型、Iterable、装箱和集合 | **已验证** | `BoxingLib`、`CollectionsLib`、`GenericLowering` | `GenericsIterableEndToEndTest`、`BoxingCollectionsEndToEndTest` PASS；订单 `ArrayList`/`HashMap`/`Integer` |
| 异常、finally、资源关闭 | **已验证** | 异常 IR、TWR、`ResourceTracker` | `ExceptionEndToEndTest`、`ResourceEndToEndTest` PASS；订单 ok/bad + TWR 读摘要 |
| 字符串、Unicode、浮点 | **已验证**（字符串 UTF-16；浮点见专项） | `jnative_string.c`；`FloatRuntimeEmitter` | `StringEndToEndTest`、`FloatEndToEndTest` PASS；订单 emoji 文件往返 |
| 原生源码调试 | **部分实现** | `--debug` → LLVM DI（单文件路径）；`SourceDebugMvpTest` | 单文件 DI：`SourceDebugMvpTest#debugCompileEmitsDiAndRuns` PASS。**订单多包项目：** `--debug` 产出 dSYM，但 DWARF 中未见用户 `.java` 行表；跨文件断点/变量 **未通过**。VS Code：**未验证** |

### 本轮发现并修复的阻断项

| ID | 级别 | 问题 | 最小复现 | 修复 |
|---|---|---|---|---|
| VIS-STDLIB | P0 | 命名包无法访问 stdlib `ArrayList`/`Integer`（parser 默认 `PACKAGE`，旧逻辑只抬升 `null`） | 订单 `com.demo.order` 引用 `ArrayList` | `SemanticAnalyzer.defaultTypeVisibility` / `exportStdlibMember`：stdlib 的 PACKAGE 类型与成员抬升为 PUBLIC |
| FIS-U8 | P0 | `FileInputStream.read` → `StreamNative.getU8` 用有符号 `/256` 拆字节，UTF-8 高字节损坏 | `/tmp/EmojiRead2`：`getBytes` vs `FIS.read` mismatch；`indexOf("🍎")==-1` | `StreamLib`/`NetLib` `getU8`/`putU8` 改为 `>>>` / `&` 位运算（对齐 `TlsLib`/`BitOps`） |

---

## 二、综合示例

路径：`examples/order-processing/`（说明见同目录 `README.md`）。

流程：加载样例订单 → 校验 → enum/switch 状态机 → `ArrayList`/`HashMap` 存取 → 定点金额 → Unicode 摘要文件（TWR 读回）→ 打印可核对标记；`bad` 错误路径；`stress` 有界压力。

```bash
mvn -q -DskipTests package
bash examples/order-processing/scripts/verify.sh
# 或：JNATIVE_REGISTRY=file://$PWD/examples/order-processing/registry \
#   java -jar target/jnative.jar build --project examples/order-processing/app \
#   --registry examples/order-processing/registry
```

正常路径期望输出：

```
1
3500
200
1
1
7
34
1
10
42
```

---

## 三、交互验证要点

| 检查项 | 结果 |
|---|---|
| 不同包同名类型（catalog vs order） | 通过（扁平多文件 + package） |
| 重载与装箱（`MoneyCents.prefer(3)`→1） | 通过 |
| enum + switch 状态机 | 通过 |
| 集合 put/get/remove/扩容路径（stress） | 通过；`OrderStore.remove` 同步 list+map |
| TWR 退出释放 + finally 分配后原结果仍有效 | 通过（ok/bad 末行 42） |
| Unicode 文件内容 | 通过（摘要含 `f0 9f 8d 8e`；读回 `hasEmoji=1`） |
| O0 vs 默认优化同结果 | 通过（`verify.sh` + `OrderProcessingEndToEndTest`） |
| 多文件诊断位置 | 未做专项；依赖既有 multi-CU 诊断 |

---

## 四、Java 差异与回归

| 套件 | 内容 | 结果 |
|---|---|---|
| `OrderProcessingEndToEndTest.javaCompatibleInteractionSubset` | enum/switch、prefer 重载、泛型 identity、Unicode length/indexOf、finally；**javac vs JNative O0/O1** | PASS |
| `OrderProcessingEndToEndTest.orderProjectO0MatchesOpt` | 完整项目 O0/opt + stress 确定性 | PASS |
| `OrderProcessingEndToEndTest.fileInputStreamUtf8RoundTrip` | FIS-U8 回归 | PASS |
| 既有 `BoxingCollections` / `String` / `Float` / `Exception` / `Resource` … | 本轮复跑 | PASS（见下） |

**明确排除（已文档化差异，不强制对齐）：**

- `println(Integer)` 需先 `.intValue()`  
- HashMap 无序、无 null 键  
- 嵌套 enum 类型不在本轮 Java 子集表面（测试使用顶层 enum）  
- 完整订单项目依赖 `File.*` / stdlib 集合，**不用宿主 JVM 镜像跑 JNative 路径**

复跑命令：

```bash
mvn -q -Dtest=PackageAndAccessTest,SwitchEnumEndToEndTest,FinalDaForeachEndToEndTest,GenericsIterableEndToEndTest,ExceptionEndToEndTest,ResourceEndToEndTest,StringEndToEndTest,FloatEndToEndTest,BoxingCollectionsEndToEndTest,IoStreamEndToEndTest,SourceDebugMvpTest,MultipackageResolveIntegrationTest,OrderProcessingEndToEndTest test
```

---

## 五、GC / 资源压力

`verify.sh` / `./build/order-app stress`（N=200，固定逻辑）：

- 反复 put/get/remove、间歇 `System.gc()`、循环写读文件  
- 两次 stress 输出一致（确定性）  
- `stress200_size=160`（N=200，每 5 次 remove 一次 → 保留 160）  
- `ok_time_sec≈1`，`stress200_time_sec≈0`（本机 wall clock 秒级）  
- `stress50`（`/usr/bin/time -l`）：约 0.01s real，max RSS ≈ 14.3 MB，peak footprint ≈ 10.1 MB（单次样本，非趋势）  
- 外部进程：`perl` alarm + 行式 drain，超时 60s，**不**先阻塞读完全部输出  

指标文件：`examples/order-processing/app/build/verify-artifacts/SUMMARY.txt`（每次 verify 刷新）。

不能仅凭一次成功退出认定无泄漏；本轮以**两次确定性对照 + size 上界 + 单次 RSS 样本**作有界证据。更长 soak / RSS 趋势曲线列入后续。

---

## 六、调试验收（如实）

| 项 | CLI | VS Code |
|---|---|---|
| 跨文件断点 | **未通过**（多包 `--debug` DWARF 无用户 `.java`） | **未验证** |
| 泛型参数/返回值查看 | **未验证** | **未验证** |
| 集合对象 / 中文字符串 | **未验证**（依赖上行） | **未验证** |
| GC 后变量 | **未验证** | **未验证** |
| 异常位置与调用栈 | **未验证**（示例路径） | **未验证** |
| 停止与重新启动 | 进程可正常 exit 0；非调试器语义验收 | **未验证** |
| 单文件源码 DI | **通过**（`SourceDebugMvpTest`） | **未验证** |

---

## 七、剩余 P0 / P1

### P0（本轮已清或未再发现）

- ~~stdlib 对命名包不可见~~ **已修**  
- ~~FIS UTF-8 字节损坏~~ **已修**  
- 未发现：优化改变订单结果、GC 引用失效、异常/TWR 退出错误、不支持功能被静默接受（本示例范围内）

### P1（后续）

1. **多包项目 `--debug` 未写入用户源 DI** — 阻断订单级源码调试验收。  
2. VS Code 调试适配未验收。  
3. 嵌套 enum / 部分 Java 语法糖仍硬错误或未支持（需明确诊断清单化）。  
4. 压力测试 RSS 长时间趋势与泄漏检测自动化。  
5. `docs/jnative-language-semantics.md` 中字符串 `length` 旧描述需与 UTF-16 契约对齐（见下节更新）。

---

## 八、语义契约更新摘要

1. **stdlib 可见性：** 位于 stdlib 源路径、声明为 package-private 的类型与成员，对用户命名包按 **exported（PUBLIC）** 处理，以匹配历史 stdlib 省略 `public` 的写法。用户源码仍保持 Java 包可见性。  
2. **Native 字节 RMW：** 经 `NativeMemory.getInt` 拆装字节必须使用逻辑移位；有符号 `/256` **不是** 合法实现。  
3. **String：** `length()` = UTF-16 码元（见 `docs/jnative-string-utf16.md`）；与旧「UTF-8 字节」描述冲突处以 UTF-16 文档为准。  
4. **金额：** 业务示例必须用整数定点；浮点仅用于显式浮点能力，不用于货币。

---

## 九、验收对照

| 标准 | 结果 |
|---|---|
| 综合示例在实际生成程序中正确运行 | **通过** |
| 支持范围内优化及 Java 差异验证 | **通过**（子集 + O0/opt） |
| 资源、GC、调试结果可靠 | 资源/GC 有界压力 **通过**；调试 **部分**（单文件通过，多包未通过） |
| 未支持能力明确诊断 | 本示例仅用已支持面；嵌套 enum 等仍报错而非静默 |
