# 本轮（版权/许可整理）修改与新增文件清单

**未**推送、**未**改写历史、**未**更换 MIT 条款。

## 新增

| 路径 | 说明 |
|------|------|
| `COPYRIGHT.md` | 个人版权主体「张晓达」、自有范围、第三方例外、贡献非转让 |
| `NOTICE` | 发行归属：Copyright (c) 2026 张晓达 |
| `THIRD_PARTY_NOTICES.md` | 第三方归属 |
| `CONTRIBUTING.md` | 贡献规则草案（无 CLA） |
| `third_party_licenses/*` | Apache-2.0、MIT（languageclient）、zlib、EPL-2.0 摘录、OpenSSL 说明 |
| `docs/legal/*` | 现状、来源、边界、敏感信息、阻断、核对表、隔离区、策略 |

## 修改

| 路径 | 说明 |
|------|------|
| `README.md` | 增加 Licensing 指向 |
| `docs/developer/README.md` | 指向 `docs/legal/` |
| `.gitignore` | 增加密钥类忽略（**不**清理历史） |
| `docs/legal/USER_CONFIRMATIONS.md` | §A 已确认署名「张晓达」 |
| `LICENSE` | **未改条款**；版权行 `Copyright (c) 2026 张晓达`（已确认） |
| 自有源文件约 548 个 | 增加 `Copyright (c) 2026 张晓达` + `SPDX-License-Identifier: MIT` |

## 故意未改头

| 路径 | 原因 |
|------|------|
| `src/test/java/io/jnative/http2/Hpack031.java` | quarantine Q-1，来源未证实前不加个人版权头 |
| Gradle Wrapper / `node_modules` / 第三方 | 保留上游声明 |

## 明确未做

- 未公开仓库 / 未 `git push`
- 未 `filter-repo` / 未改正文历史中的作者邮箱
- 未引入版权转让 CLA
- 未添加与 MIT 冲突的「禁止商用」等条款
- 未改 `pom.xml` / VSIX `license` 元数据（仍待 §C 勾选）
