# Web / 移动：多页面任务应用

作者：张晓达

## 目标

用共享 `TaskCore`（WASM）实现可导航、可编辑、可持久化的多页面任务应用；JS / Swift / Kotlin 只做平台适配。本轮不扩展桌面控件框架。

## 验收命令

```bash
./examples/guide-tasks/scripts/verify_core.sh
./examples/guide-tasks/scripts/verify_web.sh
node examples/guide-tasks/scripts/verify_browser.mjs
node examples/guide-tasks/scripts/verify_dev_web.mjs
./examples/guide-tasks/scripts/verify_mobile_assets.sh
```

Chrome 期望包含：`invalid-route,create-cjk,nav-back,dirty-leave,edit-save,bad-date,refresh,network-ok`。

## 1. 共享模型（JNative）

`examples/guide-tasks/core/TaskCore.java`（存档 **v3**）：

| 字段 | 规则 |
|------|------|
| 稳定 id | 删除后其余 id 不变 |
| 标题 | 必填，最长 48 |
| 备注 | 最长 120；状态=进行中时必填（跨字段） |
| 状态 | 0 待办 / 1 进行中 / 2 已完成 |
| 截止日期 | `YYYYMMDD` 或 0；非法日期 **error 6**，从不改写成默认日 |

## 2. 多页面导航

Hash 路由（地址与页面一致）：

| 路径 | 页面 |
|------|------|
| `#/list` | 列表 |
| `#/task/:id` | 详情 |
| `#/new` | 新建 |
| `#/edit/:id` | 编辑 |
| `#/settings` | 设置 |
| 其它 / 坏参数 | 无效页 |

- 浏览器前进后退：依赖 hash + `sessionStorage` 导航栈
- 移动「返回」：`router.back()` 先弹栈，根页才可能退出
- 未保存离开：表单 dirty 时 `confirm`
- 刷新：hash 恢复页面；任务数据从 localStorage v3 恢复

实现：`web/lib/router.js` + `web/app-shell.js`。

## 3. 表单体验

`web/lib/formController.js`：

- 静态 DOM，不在列表刷新时重建输入框
- `compositionstart/end`：组合输入期间不提交
- 非法日期保留原文并显示字段错误
- 提交中禁用按钮防重复；失败保留输入

## 4. 组件生命周期

两层说明：

1. **任务应用 JS 壳**（当前推荐路径）：`web/lib/lifecycle.js`  
   稳定 `instance id`、子控制器、订阅取消、`AbortController` 清理。
2. **WebUI `Html.*`**：`WebUI.unmount()` 已补齐（子组件 unmount、binding dispose、callback unregister）。  
   全量 WebUI 应用路径仍可能因 stdlib→WAT 失败——**实验性**；教程继续用导出核心 + JS 壳。

## 5. 开发预览（可靠刷新，非热更新）

```bash
node examples/guide-tasks/scripts/dev_web.mjs
# http://127.0.0.1:8767/
```

行为：监听 `core/` 与 `web/` → 防抖合并构建 → 成功写 `.build-ok` 并 SSE 通知刷新；失败保留上一成功产物并显示失败条；停止时关闭 watcher / SSE / 端口。

**不称为热更新**：整页 reload，不保留组件内存状态。

VS Code：扩展已有 Web 构建预览（失败不启旧 dist）。本示例额外提供 `dev_web.mjs` 监听重建。

## 6. 加载与故障

| 阶段 | UI |
|------|-----|
| 资源 / WASM | `#boot` 提示 + 重试 |
| 存档损坏 | 提示并清除，不重复写坏数据 |
| 网络 | 成功 / 失败 / 取消；页面销毁后 token 丢弃 |
| 未处理异常 | status 提示；控制台详情（无敏感载荷） |

## 7. 移动

`sync_mobile_assets.sh` 同步同一 Web 壳 + `tasks-touch.css`（底部导航、safe-area）。  
Android/iOS 容器仍是 WebView，不是原生控件。模拟器构建：`build_android.sh` / `build_ios.sh`。

## 边界

- `BrowserNative.fetch` 未接线 → 网络用 JS `fetch`
- 普通 KV ≠ 安全凭据存储
- 预览成功刷新 ≠ HMR
