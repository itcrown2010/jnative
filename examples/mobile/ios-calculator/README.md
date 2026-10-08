# JNative iOS Calculator

离线 iPhone 计算器：计算核心为 JNative 定点 `long`（SCALE = 10⁶），经真实 IR→WASM 打包进 App；Swift 仅提供 WKWebView 容器。

## 目录

```text
examples/mobile/ios-calculator/
  src/CalcEngine.java     # 四则运算与按键状态机（唯一计算实现）
  web/                    # 界面 HTML/CSS/JS（无算术重写）
  ios/IosCalculator/      # Swift + WKWebView
  scripts/build_all.sh    # 推荐入口
  dist/                   # 构建产出的 WASM/Web（勿手改）
  build/IosCalculator.app # 模拟器 App（环境允许时）
  screenshots/            # 模拟器截图
```

## 构建入口

```text
编译 CalcEngine.java
    → 生成并验证 WASM/Web（scripts/build_web.sh + verify_wasm.mjs）
    → 同步到 ios/IosCalculator/www
    → xcodebuild 模拟器 App
```

```bash
# 工具链（仓库根目录）
mvn -DskipTests package

# 本例（路径可含空格；不依赖绝对开发者路径）
cd examples/mobile/ios-calculator
./scripts/build_all.sh
```

分步：

```bash
./scripts/build_web.sh          # 失败则停止，不打包旧资源
node ./scripts/verify_wasm.mjs  # 驱动真实 WASM 导出
./scripts/sync_ios_assets.sh
./scripts/build_ios.sh          # → build/IosCalculator.app
./scripts/run_simulator.sh      # 安装/启动/截图（需模拟器 runtime）
```

也可在 Xcode 打开 `ios/IosCalculator.xcodeproj`（最低 **iOS 16.0**，bundle id `io.jnative.examples.mobile.ioscalculator`）。模拟器构建使用 `CODE_SIGNING_ALLOWED=NO`，无需开发者证书。

## 数值规则

| 项 | 规则 |
|----|------|
| 表示 | 内部 `long` 定点，SCALE = 1 000 000（六位小数） |
| 输入 | 小数最多六位；超出忽略 |
| 范围 | \|值\| ≤ 9 999 999 999.999999；超范围 → Error |
| 乘除舍入 | 四舍五入（远离零） |
| 溢出 | 运算前检查（含中间乘积）；禁止回绕 |
| 边界 | `long` 经 WASM 以 **BigInt (i64)** 交给 JS，仅用于显示格式化 |

**为何定点：** 当前 wasm32-browser 浮点路径不可靠；`long`/i64 降低已验证（含源码改动后结果变化的加法证明）。

静态字段初始化器在浏览器后端目前归零，因此 SCALE/上限以方法常量返回，可变状态仍用静态字段。

## 交互规则（即时运算，无优先级）

示例：`2 + 3 × 4 = 20`（先算 2+3，再 ×4）。

- 一个数字最多一个小数点；以 `.` 开始显示 `0.`
- 连续按运算符 → 替换尚未执行的运算符
- 已输入第二操作数后再按运算符 → 先执行当前运算
- 按 `=` 但无第二操作数 → 保持当前结果
- 重复按 `=` → 保持结果，不重做上次运算
- 结果显示后输入数字 → 开始新计算
- 除零/超范围 → 显示 Error；Clear 或输入数字可恢复
- ⌫ 仅编辑正在输入的数字
- 无科学计算、无百分比、无表达式优先级

## WASM 证明与测试

`scripts/verify_wasm.mjs` 直接调用 `_JN_CalcEngine_*` 导出（非镜像计算器），覆盖：

`12+30=42`、`7-10=-3`、`6×8=48`、`9÷4=2.25`、`0.1+0.2=0.3`、`1÷0→Error`、即时运算、运算符替换、删除/小数点/正负号、结果后续算、错误恢复、六位小数与舍入、溢出。

前置 i64 证明（工具链）：`add(12,30)→42`；将源码改为 `a+b+7` 后 → `49`。

## iOS 外壳

- Swift + WKWebView，本地 `www/` 加载 `index.html` / `app.js` / `app.wasm`
- **无**计算用原生桥接（不需要）
- 外部 http(s) 链接在系统浏览器打开，不进入带能力的 WebView 页
- 不申请无关权限；不提交签名证书

## 界面

深色 iPhone 布局、大号结果区、数字/功能/运算键区分、安全区、小屏按键可见、长结果缩小字号、无软键盘、按钮 `aria-label`、无 CDN。

## 限制 / 未验证项

见 [`TEST_RESULTS.md`](TEST_RESULTS.md)。

本机实测：Xcode 26.6 + iOS 17.5 runtime 时，`xcodebuild` 可能找不到 destination；`build_ios.sh` 会回退到 `swiftc` 打出模拟器 `.app`。安装 runtime 与 SDK 匹配后可用完整 Xcode 构建。

模拟器演示：`./scripts/run_simulator.sh` 会先截主界面，再用 `-autodemo` 对真实按键触发 `12 + 30 =`（结果应为 `42`）。
