# 版权与许可现状报告（公开前审阅稿 · 姓名已确认）

**约束**：不公开、不推送、不改写 Git 历史、不自动换证。  
**用户已明确**：有权拥有的 JNative **原创**代码，版权主体为**个人**（非公司）。  
**公开署名**：`张晓达`（`USER_CONFIRMATIONS.md` §A，2026-10-12）。  
本报告**不构成**「绝无侵权」法律意见。

---

## 1. 许可与权属（分开处理）

| 维度 | 现状 |
|------|------|
| 使用许可 | 根 `LICENSE` 为 **MIT**（条款未改） |
| 版权主体 | 个人权利人 **张晓达**；**非**公司 |
| 对外版权行 | `Copyright (c) 2026 张晓达` |
| `NOTICE` / `COPYRIGHT.md` | 已与确认姓名同步 |

### LICENSE 历史（许可条款始终为 MIT）

| 提交 | 版权行 |
|------|--------|
| `ba611a0` (2026-10-06) | `Copyright (c) 2026 JNative Contributors` |
| `92f8a54` (2026-10-09) 起 | `Copyright (c) 2026 张晓达` |

若曾按 MIT 对外提供过副本，新的版权署名**不能**撤回已按 MIT 授予的许可。

---

## 2. 自有 vs 非自有（检查摘要）

| 类别 | 处理 |
|------|------|
| 编译器 / 自写 runtime C / stdlib / 扩展 TS 源 | 个人版权 + MIT；已加文件头 |
| Gradle Wrapper、npm 依赖、系统 OpenSSL/zlib | **保留**原许可与声明；**不得**改成用户版权 |
| HPACK 测试表 | quarantine Q-1，**未**加个人版权头 |
| 外部 PR 贡献者 | 当前短日志未见第二贡献者；未来 PR ≠ 自动转让版权 |
| `.vscode-test` 内 VS Code 资源 | **不**纳入公开源码包 |

详细第三方表：`THIRD_PARTY_SOURCES.md` / 根 `THIRD_PARTY_NOTICES.md`。

---

## 3. 源文件声明现状

- 自有路径已批量写入 `Copyright (c) YYYY 张晓达` + `SPDX-License-Identifier: MIT`（约 548 个文件）。
- 既有 `@author 张晓达` 保留；文件头为规范化版权/许可声明。
- `Hpack031.java` 故意跳过。

策略：`HEADER_POLICY.md`。

---

## 4. 元数据缺口

- `pom.xml`、VSIX `package.json` 仍缺 `license` 字段（拟议片段见 `proposed-metadata-snippets.md`；待 §C 批准）。

---

## 5. 索引

| 文档 | 内容 |
|------|------|
| `../../COPYRIGHT.md` | 自有主体 + 第三方例外 |
| `USER_CONFIRMATIONS.md` | 确认清单（§A 已完成） |
| `GENERATED_ARTIFACT_LICENSE_BOUNDARY.md` | 生成程序边界 |
| `SENSITIVE_DATA_FINDINGS.md` | 敏感信息 |
| `RELEASE_BLOCKERS_LEGAL.md` | 阻断项 |
| `RELEASE_PREFLIGHT_CHECKLIST.md` | 发布前核对表 |
| `FILE_CHANGE_LIST.md` | 本轮修改文件清单 |
