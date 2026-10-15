# 会计总账（管理核算）规则

**定位：** 单账套、单本位币、基础权责发生制的**内部管理核算**。不宣称满足法定财务报表或税务申报。

**金额：** 整数货币单位（与采购/销售/结算/计价一致），借贷必须精确相等，禁止浮点容差。

## 账套与政策

| 字段 | 说明 |
|------|------|
| `companies.functional_currency` | 本位币（默认 CNY） |
| `companies.gl_policy` | 过账政策 |

### `PROVISIONAL_CLEARING`（默认，与历史 GR/DL 一致）

| 业务 | 分录 | 备注 |
|------|------|------|
| 采购入库 GR | 借库存 / 贷 GR_CLEARING(2100) | 暂估；正式应付确认 **PENDING_CONFIG** |
| 销售出库 DL | 借销售成本 / 贷库存 | 仅成本；收入确认见下方 |
| 销售确认 / 税务 / 暂估冲回 | — | **PENDING_CONFIG**，不自行推断 |

### `MGMT_DIRECT_ACCRUAL`（验收/管理测试政策：无税、无折扣、采购直接应付）

| 事件 `event_type` | 分录 |
|-------------------|------|
| `OPENING_CASH` | 借资金(1000) / 贷权益(3000) |
| `PURCHASE_STOCK` | 借库存(1200) / 贷应付(2000)+供应商 |
| `SALE_REVENUE` | 借应收(1100)+客户 / 贷收入(4000) |
| `SALE_COGS` | 借成本(5000) / 贷库存(1200) |
| `CUSTOMER_RECEIPT` | 借资金 / 贷应收+客户 |
| `VENDOR_PAYMENT` | 借应付+供应商 / 贷资金 |
| `REVERSAL` | 另记冲销凭证（借贷对换）；原单保持 `POSTED` 可查，并写入 `reversed_journal_id` 防重复冲销 |

应收应付**核销**本身不重复记已由收付款形成的账务影响。预收预付转销、损益结转、年结、法定报表：**未实现**。

## 科目

- 编码/名称/类别/`normal_balance`/`posting_allowed`/`active`/`is_control`/`require_partner`
- 被历史凭证引用的科目不得删除或改变历史含义（仅可停用）
- 映射键：`account_mappings`（BANK/AR/INVENTORY/AP/EQUITY/REVENUE/COGS/GR_CLEARING/…）

## 凭证生命周期

`DRAFT` → `APPROVED`（已审核，未入账）→ `POSTED`（已入账簿）→ 冲销后原单 `REVERSED`  
另有 `REJECTED`。

账簿/试算**仅**汇总 `POSTED`。

服务端校验：单行借贷互斥且非零、整单借贷精确相等、科目启用且可记账、必填辅助维度、期间开放、编号唯一、合法状态转换。

## 事件过账

表 `gl_posting_events`：`UNIQUE(company_id, event_type, source_type, source_id, source_line_id)`。

同一事务内：写入事件 → 生成凭证 → 更新事件为 `POSTED`；失败则事件 `FAILED`（含错误码/信息），**绝不**伪造已过账。

## 期间

结账 API 将期间置 `CLOSED`（期间锁定）。结账前检查：待处理/失败事件、未过账凭证、试算与往来控制差异。

**不是**完整财务结账：损益结转 / 年结未实现。重开需原因并写审计。

## 核对

`GET /api/erp/finance/gl-recon`：试算平衡、应收/应付控制与往来辅助、存货与计价库存、资金与 `bank_accounts.balance`、待过账事件。

## 验收命令

```bash
DATABASE_URL='sqlite:./build/erp/gl-accept.db' \
  examples/erp/.jnative/build/erp-server --gl-test
# or: examples/erp/scripts/gl-accept.sh
```

期望期末：资金 990、应收 30、库存 60、应付 40、权益 1000、收入 80、成本 40、业务利润 40。
