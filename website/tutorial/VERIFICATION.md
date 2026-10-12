# 语言教程验证报告

| 项 | 值 |
|----|-----|
| 日期 | 2026-10-09 |
| 工具链 | JNative 0.32 |
| 主机 | macOS arm64 |
| 脚本 | `./scripts/docs/verify-tutorial.sh` |

## 覆盖

- 单文件章节示例编译 + 输出比对（`_expected/*.txt`）
- `04-project` / `17-multifile` 项目构建
- `05-errors` 负例诊断关键字
- task-manager stage1–4 `demo` 输出比对
- 同步 `docs/tutorial` → `website/tutorial` 并尝试站点 build

## 结果（2026-10-09 本机）

示例与 task-manager `demo`：**全部通过**（见脚本 `OK …` 行）。  
站点构建：修复死链后应输出 `TUTORIAL_VERIFY_OK`；若仅剩高亮 `jn` 回退为 `txt` 的提示，可忽略。

## 未宣称通过

- 非 macOS 主机
- 商店/GUI/移动（另见开发者文档站）
- 交互式 stdin 会话
