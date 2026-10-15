# 采购与库存业务规则（首轮）

**范围：** 普通商品、单币种（默认 CNY）、基础收发货。  
**未实现（明确不做空壳）：** 多币种、批次、序列号、库存预留、完整总账结账。

## 主数据（统一复用）

| 实体 | 表 | 唯一性 |
|------|-----|--------|
| 商品 | `items` | `(company_id, sku)` |
| 单位 | `units_of_measure` | `(company_id, code)`；商品 `uom` 引用编码 |
| 供应商/客户 | `business_partners` | `(company_id, code)`；`kind` = SUPPLIER/CUSTOMER/BOTH |
| 仓库 | `warehouses` | `(company_id, code)` |
| 单据号 | 各业务表 `doc_no` | `(company_id, doc_no)`；由 `number_sequences` 分配 |

采购/销售/库存/财务子账均使用上述主数据，禁止各模块另建一套商品或往来单位。

## 金额与数量

| 字段 | 表示 | 精度 | 舍入 |
|------|------|------|------|
| 单价、金额、税额、折扣后行金额 | 整数最小货币单位（分 / cents） | 0 位小数 | 行金额：`(qty * unitPriceCents)`；折扣按基点 `discountBps`（万分比）先减再税；税 `taxBps`；合计 half-up 到分 |
| 数量 | 整数 | 0 位小数（件） | 禁止 float/double 累计 |

禁止用 `float`/`double` 做金额或数量累加。UI 展示可除以 100，持久化与服务端运算必须用整数。

## 采购订单状态

```
DRAFT → SUBMITTED → APPROVED → PARTIALLY_RECEIVED → RECEIVED → CLOSED
                ↘ CANCELLED（仅未收货）
DRAFT → APPROVED（允许跳过提交，兼容 API）
```

| 转换 | 条件 |
|------|------|
| 保存/修改 | 仅 `DRAFT`；已审核后禁止改供应商、仓库、行数量/单价 |
| 提交 | `DRAFT` → `SUBMITTED`；至少一行，qty>0，supplier/warehouse 有效 |
| 审核 | `DRAFT` 或 `SUBMITTED` → `APPROVED`；**不增加库存** |
| 部分入库 | 行 `received_qty` 累加后由过账刷新为 `PARTIALLY_RECEIVED` / `RECEIVED` |
| 关闭 | `APPROVED`/`PARTIALLY_RECEIVED`/`RECEIVED` → `CLOSED`；关闭后不可再入库 |
| 取消 | 仅无收货的 `DRAFT`/`SUBMITTED`/`APPROVED` → `CANCELLED` |

行字段：`quantity`（订购）、`received_qty`（已入库）、未入库 = `quantity - received_qty`。支持部分入库、多次入库。

## 采购入库

- 必须关联已审核/部分收货的 PO；默认**禁止超订单入库**（`received + qty > ordered` → 拒绝）。
- 入库审核（`POST /api/erp/goodsReceipts`）在**同一事务**内：
  1. 校验 PO 状态与行剩余数量（CAS：`UPDATE … WHERE received_qty=?`）
  2. 写 `goods_receipt_lines` + `inventory_transactions`
  3. CAS 更新 `inventory_balances`（`row_version`）
  4. 更新订单行 `received_qty` 与订单状态
  5. 生成**暂估应付**（见下）
  6. 写 `document_references`、幂等键、审计时间戳
- 幂等：`Idempotency-Key` / `idempotency_keys`；同一键重放返回缓存，不重复库存/应付。
- 已过账入库单不可改数量；撤销走冲销（`…/reverse`），并校验后续依赖。

### 暂估应付（本轮规则）

入库过账形成 **暂估应付**，状态 `PROVISIONAL`，写入 `ap_invoices`（`source_gr_id` 唯一）：

- 金额 = 本次入库数量 × PO 行单价（分）
- **不**视为供应商发票确认；**不**过账正式 AP 凭证（GR 已过账存货/GR-IR）
- 正式发票确认 / 付款核销属后续财务阶段

## 库存模型

| 概念 | 含义 | 本轮 |
|------|------|------|
| 实存 `quantity_on_hand` | 账面实物数量 | 已实现 |
| 预留 `quantity_reserved` | 销售预留 | **未实现**；API 固定返回 `0` 并标注 `reservedSupported:false` |
| 可用 | 实存 − 预留 | 本轮 = 实存（因预留为 0） |

- 余额只允许通过流水变更（入库、出库、期初、调整、盘点差异）；禁止直接改余额“修正”。
- 流水保留：数量变化、业务时间、`txn_type`、来源类型/单据 id、操作上下文。
- 期初：`opening_stock_*` 已有过账路径。
- 调整：`POST /api/erp/inventory/adjust`，需原因 + 权限 + 幂等键。
- 盘点：`POST /api/erp/inventory/count`，按实盘生成差异调整流水。
- 预警：商品 `min_qty > 0` 且实存 ≤ `min_qty`。

## 与后续模块的衔接

销售出库、客户应收、收付款核销复用同一套主数据、整数金额、单据状态机与库存 CAS/幂等机制。销售阶段再实现预留与出库扣减；财务阶段再将 `PROVISIONAL` 转为正式应付并核销。
