# 销售阶段交付说明

## 库存基础复验

```bash
rm -f build/erp/purchase-accept-reverify.db
DATABASE_URL='sqlite:./build/erp/purchase-accept-reverify.db' \
  examples/erp/.jnative/build/erp-server --logic-test
```

结果：`OK stage_a_po10_gr4_6` / `PASS`（分批入库、暂估应付、幂等、库存 80）。

## 本轮范围

- 审批/履约双状态；SO 草稿/提交/审核/取消/关闭
- `inventory_reservations` + 可用库存；审核尽力预留
- 分批出库事务：预留消耗 + 库存 CAS + 暂估应收 + 幂等
- 销售退货 → 入库流水 + 应收调整；不重开出库额度
- UI：SO/出库经 RPC；库存页展示 reserved/available

## 迁移

`migrations/008_sales_ops(_sqlite).sql` + `ErpDb.ensureSalesOpsSchema()`

## 关键源码

- `ErpReservation.java` / `ErpSales.java` / `DlPostWork` / `ErpSalesReturn.java`
- `ErpFinanceArAp.createProvisionalArConn` / `adjustArOpenConn`
- `ErpSalesLogicTest`（`--sales-test`）

## 验收命令

```bash
DATABASE_URL='sqlite:./build/erp/sales-accept.db' \
  examples/erp/.jnative/build/erp-server --sales-test
```

期望：`OK reserve_A12_B8` / `OK dl5` / `OK dl7_fulfilled` / `OK return2` / `PASS sales`。

## 明确未完成

收款、核销、退款、正式发票、总账；并发注入故障脚本（事务失败回滚由抛异常覆盖，未单独注入 harness）。
