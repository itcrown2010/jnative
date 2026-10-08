# ERP 试点基线报告（1.6.0-pilot）

**日期：** 2026-10-08  
**范围：** `examples/erp` 真实库基线阻断项验收与修复。

## 基线项结果

| 基线项 | 修复前 | 修复后 | 分级 |
|--------|--------|--------|------|
| 开放业务页 Rpc+DB；失败不回退 mock | UI DemoStateStore / Rpc reject | `JnData` 默认 `RpcDataProvider`；登录走 `/api/auth/login`；失败抛错不回落 mock | **已清阻断** |
| 演示库预装可对账样例；正式库空业务 | Seed 混用 | `book_kind` DEMO/FORMAL；`ERP_DEMO_MODE=1` 才种 JNAT；正式靠向导空库起步；`demo_seed_meta.seed_version` | **已清阻断** |
| DB 故障不回退 mock | 默认即 mock | Rpc 失败直接 Error；`SCHEMA_NOT_READY`→503 禁业务写 | **已清阻断** |
| 重复/并发过账；库存 CAS | 仅幂等 | 幂等保留 + `row_version` CAS / PG `FOR UPDATE` | **已清阻断** |
| 服务端权限有效 | 业务写仅登录 | 写路径 `authPerm`（PO/SO/GR/DL/冲销/期初/导入/向导） | **已清阻断** |
| 演示重置不影响正式 | 无 HTTP / 不擦库 | `POST /api/erp/admin/demo/reset`；正式账套或非 demoMode → **403**；CLI `--reset-demo` 同护栏 | **已清阻断** |
| 备份恢复对账 | 脚本有、证据弱 | `backup-restore.sh` + upgrade 前 `pg_dump`；pilot 验收检查脚本存在；本机 dump 证据见 `build/erp/backups/` | **核心（环境实测待证）** |

## 剩余缺陷

| ID | 分级 | 说明 |
|----|------|------|
| ERP-P0-01..06 | 平台 P0 | Linux/PG wire/TLS/安装包等仍见 `docs/erp-release-blockers.md`（不在本轮 examples/erp 范围） |
| ERP-PILOT-P1-01 | 核心 | 部分菜单页仍无完整 Rpc list 映射（会报 `RPC_UNSUPPORTED_COLLECTION`，不回落 mock） |
| ERP-PILOT-P1-02 | 核心 | 备份 Linux 全量 stop→wipe→restore→业务对账需目标环境签字 |
| ERP-PILOT-P2-01 | 一般 | WASM UI 仍 BLOCKED |

## 验证命令

```bash
chmod +x examples/erp/scripts/pilot-accept.sh
examples/erp/scripts/pilot-accept.sh
# 或
DATABASE_URL='sqlite:./build/erp/pilot.db' examples/erp/.jnative/build/erp-server --pilot-test
```
