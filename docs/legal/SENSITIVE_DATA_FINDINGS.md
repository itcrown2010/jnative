# 公开前敏感信息检查（路径与风险，不含密钥内容）

扫描方式：路径模式与关键词匹配；**不**在此打印任何密钥或口令正文。  
`.gitignore` **不能**清理 Git 历史中已存在的敏感对象。

---

## 高优先级

| 位置 | 风险 | 历史状态（当时检查） | 建议 |
|------|------|----------------------|------|
| `examples/https/certs/server.key` | TLS **私钥**文件在工作区 | **未**出现在 `git ls-files`；**未**发现该路径的历史提交 | 删除或移出工作树；加入 ignore；视为演示密钥则**轮换/作废**后仅保留生成脚本；**不要**提交 |
| `examples/https/certs/server.crt` | 公钥证书（已跟踪） | 引入于联网相关提交 | 演示证书可保留，但文档标明仅供本地 demo，并与私钥分离 |

## 中优先级（演示口令 / 配置）

| 位置 | 风险 | 建议 |
|------|------|------|
| `examples/erp/docker-compose.yml` | 明文演示数据库口令 | 改为环境变量占位符；README 说明本地设置 |
| `examples/music/docker-compose.yml` | 同上 | 同上 |
| 测试与文档中的 `password`/`SECRET`/`TOKEN` 字样 | 多为 API 名或假数据（如 JWT 示例、Pg 驱动常量） | 人工抽查高亮文件，确认无真实凭证 |

关键词命中但**更可能为代码标识符**的路径（抽查用，非已确认泄露）：

- `src/main/resources/stdlib/crypto/CryptoLib.java`
- `src/main/resources/stdlib/db/PgDriverLib.java`
- `examples/web/jwt-api/App.java`
- `src/test/java/io/jnative/security/**`
- `src/main/java/io/jnative/cli/CertCli.java`

## 个人数据 / 内部信息

| 项 | 说明 |
|----|------|
| ERP 演示 seed | 确认无真实客户/员工数据后再公开 |
| Git 作者邮箱 | 存在于提交元数据；公开仓将暴露；是否改写历史须**另行方案且用户批准**（本轮不执行） |
| `tmp/`、`build/`、本地 DB | 应保持不入库 |

## 明确未做

- 未 `git filter-repo` / force push。  
- 未撤销任何真实云密钥（若你曾把私钥用于别处，请在基础设施侧轮换）。  
- 未把「已从工作区删除」写成「历史已干净」。
