# 财务结算子账规则（应收 / 应付 / 收付款 / 核销）

**金额：** 一律整数最小货币单位（分），禁止 float/double 累计。  
**本阶段范围：** 往来结算子账 + 资金账户余额/流水。不含完整总账关账、税务申报、多币种汇兑、库存计价与销售成本。  
**原则：** 收款 ≠ 收入；付款 ≠ 费用；未建立成本/总账模型前不据此生成利润表。

## 一、业务来源与确认时点

| 业务 | 来源单据 | 往来 | 财务事件 | 确认时点 | 金额口径 |
|------|----------|------|----------|----------|----------|
| 采购入库 | GR + 行 | 供应商 | `PROVISIONAL_AP`（`ap_invoices`，`status=PROVISIONAL`） | GR 审核入账同一事务 | 行数量×单价（分）；暂估未结 |
| 销售出库 | DL + 行 | 客户 | `PROVISIONAL_AR` | DL 审核同一事务 | 同上；非正式开票/收入 |
| 销售退货 | SR | 客户 | `AR_ADJUST`（`ar_ap_adjustments`） | SR 审核 | 减少未结；已结部分记 CREDIT_BALANCE 备注，**不**自动伪造退款流水 |
| 正式 AR/AP | 手工/发票 | 客户/供应商 | `status=POSTED`（可走原 GL 路径） | 发票过账 | 与结算子账分离可选 |

**暂估 vs 正式：** `PROVISIONAL` = 业务确认待结算；正式发票/收入确认另列。结算状态 `settlement_status`（OPEN/PARTIAL/SETTLED）与确认状态分离。

**唯一约束：** `finance_source_events (company_id, source_type, source_id, event_type)`；GR/DL 源上另有 `source_gr_id` / `source_delivery_id` 唯一索引。重复审核不得重复生成应收应付。

**金额修正：** 仅通过 `ar_ap_adjustments` 或核销/撤销核销；不直接覆盖历史金额。记录操作人、业务时间、记录时间、原因、关联单据。

**余额公式（统一口径）：**

```
effective = total_amount + adjusted_amount   # adjusted 贷项为负
open_amount = effective - paid_amount
settlement_status = SETTLED|PARTIAL|OPEN 由 open/paid 推导，页面不可改
```

## 二、收款 / 付款

字段：往来单位、资金账户、业务日期、金额、支付方式、外部参考号、备注。

| 状态 | 资金 | 未分配 | 可核销 |
|------|------|--------|--------|
| DRAFT | 不影响 | — | 否 |
| POSTED | 同事务写 `bank_cash_movements` + CAS `bank_accounts.balance` | `unallocated = amount - allocated` | 是 |

- 审核幂等（`idempotency_keys` + `finance_source_events`）。
- 已审核不得直接删改金额/往来；撤销核销后才可再分配。
- 默认付款不得使资金账户余额为负（`allow_overdraft=0`）。

API（服务端鉴权 `FINANCE.PAYMENT.*`）：

- `POST /api/erp/finance/receipts` / `payments`
- `POST .../:id/post`
- `POST .../allocate` / `reverse-alloc`
- `GET /api/erp/finance/cash-movements`、`party-statement`、`settlement-integrity`
- 账龄：`/api/erp/finance/arAgeing`、`apAgeing`（按查询日 vs 到期日：未到期 / 1–30 / 31–60 / 61–90 / 90+）

## 三、核销

支持一收多应收、多收分次、付款对称、部分核销、未分配余额。  
同一事务：按发票 id 排序加锁 → 重算可核销 → 写 `ar_settlement_allocs`/`ap_settlement_allocs` → 更新收付款未分配与应收应付未结 → 审计。  
撤销：保留原行 `REVERSED` + `reversed_by_id` 与反向关联行，禁止重复撤销抹史。  
首轮不做同往来 AR/AP 自动抵销。跨往来、超额、并发 CAS 冲突返回业务错误。

## 四、退货 / 贷项 / 退款

- 原单未结：调整减少 `open_amount`（写调整记录）。
- 原单已结：超额贷项记 CREDIT_BALANCE 备注，形成可追溯贷项意图；**实际退款**须另走资金支出（本阶段未自动生成退款单）。
- 退货审核不伪造收退款事实；不得出现无法解释的负未结。

## 五、对账口径

- **逐笔未结：** `SUM(open_amount)`。
- **往来净余额：** 未结应收 − 未分配收款（或应付 − 未分配付款）；勿把收款与核销同时从同一汇总里减两次。
- **历史时点：** 当前 `party-statement` 仅报告**当前**未结合计；完整 as-of 事件回放未实现，不得把当前状态当作历史结果。

核对（`runIntegrityChecks` / API）：应收应付=有效核销；收付款金额=已分配+未分配；资金余额=末笔流水 `balance_after`；发现差异输出具体 id，不自动修平。

## 六、未支持（后续）

完整总账关账、税务、多币种汇兑、自动 AR/AP 互抵、退款单完整生命周期、库存成本与销售成本、利润表。

## 七、验收

```bash
DATABASE_URL='sqlite:./build/erp/settlement-accept.db' \
  examples/erp/.jnative/build/erp-server --settlement-test
```

场景：AR 1000+600 → 收款 1200 核销 1000+200 → 收款 500 核销 400 未分配 100 → 幂等 → 撤销 200 → 超额并发拒绝；供应商对称；完整性与账龄。
