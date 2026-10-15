# 销售订单与履约规则

**前置：** 采购→入库→库存流水/余额/幂等已在独立库 `--logic-test` 复验通过；预留此前未实现，本阶段补齐。

## 状态分离

| 维度 | 字段 | 取值 |
|------|------|------|
| 审批 | `approval_status` | DRAFT / SUBMITTED / APPROVED / CANCELLED |
| 履约 | `fulfillment_status` | NONE / PARTIAL / FULFILLED / CLOSED |
| 兼容列表 | `status` | 由上两者合成（如 PARTIALLY_DELIVERED） |

收款状态不写入销售订单。

## 预留

- 表：`inventory_reservations`（关联 SO 行、商品、仓库）
- 实存 `quantity_on_hand` 不变；可用 = 实存 − ACTIVE 预留剩余
- 审核时按可用**尽力预留**；缺货只预留可得数量，`shortfallQty` 标明缺口，不伪造成功
- 取消/关闭剩余释放未消耗预留；出库只能消耗本单本仓预留，不得占用他单

## 出库

同一事务：校验审批/履约/CAS 行数量 → 消耗预留 → 扣实存写流水 → 更新履约 → **暂估应收** `ar_invoices.status=PROVISIONAL`（非正式开票/收入确认）→ 幂等键。

默认禁止负库存；禁止超订单出库。

## 退货

关联原出库明细；累计退货 ≤ 出库；可销售则回流入库流水；调整对应暂估应收 open_amount。**不**自动重开订单可出库数量。

## 未实现（财务阶段）

正式开票、总账收入确认、毛利/成本报表、自动退款单。收款/核销/账龄见 [finance-settlement-rules.md](./finance-settlement-rules.md)。
