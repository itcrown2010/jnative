# 库存计价与业务毛利（永续移动加权平均）

**政策：** 按「商品 × 仓库」永续移动加权平均（MAV）。本阶段不同时实现 FIFO。  
**金额：** 整数最小货币单位（分）。入库成本取业务确认的入库成本金额；未建税务规则前，不把价税合计自动当作库存成本。  
**禁止：** float/double 累计；页面自行算成本；用当前采购价倒算历史；给历史流水随意补零成本。

## 计算

```
入库后金额 = 原金额 + 本次入库成本
平均单位成本 = floor(库存金额 / 库存数量)   # 展示/出库用整数分
出库成本 = 出库前平均成本 × 出库数量
全部出清时：结转剩余库存金额（消除舍入尾差），数量与金额同时归零
零数量不得保留无法解释的金额
```

出入库、成本事件、单据状态在同一事务内更新；`inventory_balances.row_version` CAS 防并发错成本。

## 追溯

- `inventory_transactions`：数量/金额流水（biz_date、posted_by）
- `inventory_cost_events`：计价前后数量金额、平均成本、来源单据
- `delivery_lines.cogs_amount` / `cogs_returned_amount`：出库成本及已退回成本
- `goods_receipt_lines.line_value_amount`：入库成本

库存余额是可核对汇总；流水与成本事件是追溯依据（不是只改 `items.unit_cost_amount`）。

## 销售退货

关联原出库明细，按原 `cogs_amount` 比例恢复库存金额（末次部分退货吃掉舍入尾差）。  
**不得**用退货日移动平均/采购价代替原成本。无 COGS 记录时拒绝并标记 `PENDING_COST`。

## 采购退货 / 撤销 / 调价（边界）

- GR 冲销若已有后续同仓同品过账：本阶段不静默重算历史平均成本，应走明确调整（实现上优先阻止危险路径）。
- 供应商结算价变化的成本差异：本阶段显示待处理，不自动覆盖原入库金额（未实现自动差异过账）。

## 业务毛利

`业务毛利 = 销售净额（未税口径：单价×净数量）− 销售成本净额（COGS−已退回成本）`  

成本缺失显示 `PENDING_COST`，不按零成本展示虚高毛利。  
**业务毛利 ≠ 会计净利润**（无期间费用、税、总账结账）。

API：

- `GET /api/erp/inventory/costing`
- `GET /api/erp/inventory/cost-events`
- `GET /api/erp/inventory/margin`
- `GET /api/erp/inventory/cost-integrity`

## 历史查询

当前余额与流水按**过账顺序**（`posted_at` / 事件 id）核对。  
未支持历史重算前：不允许补录旧日期静默改变已确认成本；不得用今天的平均成本生成“历史库存金额”。

## 验收

```bash
DATABASE_URL='sqlite:./build/erp/costing-accept.db' \
  examples/erp/.jnative/build/erp-server --costing-test
```

场景：开仓 10/100 → 入 10/200 → 20/300 avg15 → 出 8 成本 120 → 入 3/60 → 15/240 avg16 → 退 2 按原 15 恢复 30 → 17/270 → 出清金额归零。
