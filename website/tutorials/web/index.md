# Web 教程：任务管理器（WASM + JS 壳）

## 方案说明（必读）

| 运行在哪 | 内容 |
|----------|------|
| **WASM（JNative）** | `TaskCore`：增删改、完成态、存档编解码与校验 |
| **JS（适配）** | DOM、事件、`localStorage`、把字符喂给 WASM 导出 |

**不能**把业务规则重写进 JS 再声称「JNative 实现」。  
当前 **WebUI `Html.*` 全量应用路径**（如 counter）在本工具链上仍会因 stdlib WAT 校验失败——状态：**实验性 / 对本教程不可用**。本教程采用与数独相同的已验证模式。

## 平台状态

| 项 | 状态 |
|----|------|
| `build_web.sh` / `verify_web.sh` | **实验性**（macOS arm64 已跑通） |
| Chrome 自动化：多页面/中文/非法日期/离开确认/网络 | **实验性 / 已验收**（`verify_browser.mjs`） |
| 存档 v3 + `#/list|task|new|edit|settings` | **实验性 / 已验收** |
| 开发预览可靠刷新（非 HMR） | **实验性 / 已验收**（`dev_web.mjs` / `verify_dev_web.mjs`） |
| `BrowserNative.fetch`（WASM 内） | **未支持**（网络走 JS `fetch`） |

## 课表

| 课 | 主题 |
|----|------|
| [01](./lessons/01) | 创建浏览器项目 |
| [02](./lessons/02) | 编译并本地预览 |
| [03](./lessons/03) | 页面、布局及状态 |
| [04](./lessons/04) | 事件和表单 |
| [05](./lessons/05) | 调用共享核心 |
| [06](./lessons/06) | 数据持久化 |
| [07](./lessons/07) | 异步与错误 |
| [08](./lessons/08) | JS 互操作 |
| [09](./lessons/09) | WASM 内存与句柄 |
| [10](./lessons/10) | 构建与部署说明 |

## 快速构建

```bash
./examples/guide-tasks/scripts/verify_web.sh
node examples/guide-tasks/scripts/verify_browser.mjs
node examples/guide-tasks/scripts/dev_web.mjs
# 打开 http://127.0.0.1:8767/
```

分步骤：`docs/tutorial/chapters/web-mobile-tasks.md`。
