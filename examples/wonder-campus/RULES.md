# 奇想校园 — 收费与考勤规则

作者：张晓达

金额单位：**分**（INTEGER）。课时单位：**lesson_milli**（1000 = 1 课时）。

## 课时包（PACK）

- 字段：购买 milli、赠送 milli、有效期、适用课程/校区、每节扣除 milli（写入合同快照）。
- 扣除顺序默认：`PAID_FIRST`（先付费后赠送），可配置并留痕。
- 流水类型：PURCHASE / CONSUME / REVERSE_CONSUME / REFUND / EXPIRE。
- 排课**不**扣课时；签到成功才按快照扣除。

## 学期制（TERM）

- 绑定课程、班级、学期起止、承诺课次数。
- 统计：应上 / 已上 / 请假 / 缺勤 / 补课（学校停课不计学生缺勤）。
- **禁止**用极大课时包伪装学期制。

## 规则快照

报名时复制校规到 `wc_contracts.rule_snapshot_json`。后续改校规默认不追溯旧合同。

首版快照键：

- `leaveDeadlineHours`：开课前多少小时可请假
- `leaveConsumes`：0/1 请假是否扣权益
- `absentConsumes`：0/1 缺勤是否扣权益
- `allowMakeup`：0/1
- `makeupValidDays`：补课有效天数
- `allowCrossCampusMakeup`：0/1
- `deductOrder`：`PAID_FIRST` | `BONUS_FIRST`
- `sessionConsumeMilli`：单节扣除（课时包）

## 补课一致性

- 原课已扣 + 补课再扣 → **禁止**（关联 `source_session_id` + ledger）
- 原课未扣 + 补课也不扣却无限生成 → **禁止**
- 学校/老师取消课 → session 状态 `CANCELLED_BY_SCHOOL`，不记学生缺勤

## 资金

- 应收 / 实收 / 欠款 / 退款分列。
- 收款仅人工登记（微信/支付宝/银行卡/现金），不接支付网关。
- 退款关联原收款行，累计退款 ≤ 该笔可退金额。
- 课消收入在签到扣课时单独记账，与收款分离。
- 合同/收款可关联 `billing_entity_id`（收款主体）；与运营组织 `org_id` 分离。

## 跨校区权益

- 默认仅合同所属校区可用。
- 其他校区需写入 `wc_campus_entitlement_grants` 后才可排课/消课。
