# 采购与库存阶段交付说明

## 本轮实现范围

- 统一规则文档：`docs/purchase-inventory-rules.md`
- 主数据复用 items / uom / partners / warehouses；金额整数分、数量整数
- PO：草稿保存/修改、提交、审核、关闭、取消；行已入库/未入库；列表筛选
- GR：从 PO 入库、禁止超收、CAS 防并发超收、幂等键、库存流水、暂估应付 `PROVISIONAL`
- 库存：余额（实存/可用；预留标注未实现）、流水、期初（已有）、调整 API、盘点 API、预警（`min_qty`）
- UI：PO/GR 经 `JnData` RPC 保存/提交/审核；库存页展示实存与可用

## 尚未支持

- 库存预留、多币种、批次/序列号、调拨
- 正式供应商发票确认、付款核销、完整总账结账
- 销售出库闭环（下一阶段）
- GR 草稿态（当前入库一次过账）

## 数据库迁移

- `migrations/007_purchase_ops(_sqlite).sql` + `ErpDb.ensurePurchaseOpsSchema()`
- 兼容：`ALTER` 增量列；已有库启动时自动 ensure
- 关键列：PO `warehouse_id/order_date/expected_date/notes/currency`；`items.min_qty`；`ap_invoices.is_provisional/notes`；`inventory_adjustments` / `inventory_counts(+lines)`

## 关键事务路径

- `ErpPurchasing`：create/update/submit/approve/close/cancel
- `GrPostWork`：CAS `received_qty` + Inv CAS + JE + `createProvisionalApConn`
- `ErpInventory.adjust` / `postCount` / `alertsJson`

## 验收

```bash
DATABASE_URL='sqlite:./build/erp/purchase-accept.db' \
  examples/erp/.jnative/build/erp-server --logic-test
```

期望：`OK stage_a_po10_gr4_6` 与 `PASS`（PO10→GR4+6、暂估应付 1000→2500、幂等无副作用）。
