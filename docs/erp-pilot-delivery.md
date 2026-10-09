# JNative One ERP 试点交付清单（1.6.0-pilot）

作者：张晓达

## 可部署内容

- 服务端：`examples/erp/.jnative/build/erp-server`
- 静态 UI：`examples/erp/public/`
- 迁移：`001`–`005_pilot`（PG + SQLite）
- 脚本：`setup-wizard.sh`、`jnative-one-install.sh`、`jnative-one-upgrade.sh`、`backup-restore.sh`、`pilot-accept.sh`

## 功能与边界

**支持：** 正式账套向导、CSV 伙伴/商品导入（UTF-8 真解析）、期初库存/应收/应付单据、采购收货/销售出库、GR/DL 冲销、演示/正式账套隔离、服务端 RBAC 写门禁、库存 CAS、版本化 demo seed（不覆盖用户业务）。

**明确不声称：** 完整总账、完整库存估值、退货/退款与会计冲销一体、XLSX 导入、去 DEMO 标签即等于试点完成。

## CSV 模板

- `GET /api/erp/import/templates/partners` — 编码/名称/类型
- `GET /api/erp/import/templates/items` — SKU/名称/单价分/成本分  
默认不覆盖已有；预览接口返回按行错误；确认导入单事务 + `import_batches` / 审计。

## 迁移 / 备份 / 恢复

1. 升级前：`jnative-one-upgrade.sh` 执行 `pg_dump`  
2. 迁移失败：exit≠0，**不得**继续对外业务；`/ready` 在 `schemaReady=0` 时 503  
3. 演示 seed：`demo_seed_meta` 仅补练习料，不删用户单据  
4. 恢复：`backup-restore.sh` → 独立库 `pg_restore` → COUNT/version  
5. **禁止**删库重建当升级

## 中文教程

系统内：`/system/help` 或 `GET /api/erp/help/tutorials`（T01–T08）。  
练习料：`FG-PRACTICE-A` / `WH-PRACTICE`（仅 DEMO 账套）。

## 12 项集成验收

| # | 项 | 自动化 |
|---|----|--------|
| 1 | 初始化公司/仓/用户 | `--pilot-test` step1 |
| 2 | CSV 导入；错误行不写入 | step2 |
| 3 | 期初库存 20 | step3 |
| 4 | 采购入库 10 | step4 |
| 5 | 销售出库 3 → 库存 27 | step5 |
| 6 | 冲销出库 → 30 | step6 |
| 7 | 再次冲销拒绝 | step7 |
| 8 | 往来期初余额 | step8 |
| 9 | 重连核对 | step9 |
| 10 | 迁移 005 | step10 |
| 11 | 备份/升级脚本门禁 | step11 |
| 12 | 演示重置不影响正式 | step12 |

## 剩余 P0 / P1

见 `docs/erp-pilot-baseline-report.md` 与 `docs/erp-release-blockers.md`（平台级 Linux/PG/TLS 仍为发布大前提）。

## 路线

1. 补齐菜单页 Rpc 集合映射  
2. 目标 OS 备份恢复签字  
3. 平台 P0 清零后再做对外试点承诺  
