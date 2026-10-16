# 奇想校园（Wonder Campus）

作者：张晓达

独立教务示例：JNative 原生服务承载权限 / 排课 / 收费 / 课时；手机端为 WebView 触屏壳（**非** JNative 原生 UI）。

## 隔离

| 项 | 值 |
|----|-----|
| 端口 | `18880`（禁止 `18700`） |
| 数据库 | `build/wonder-campus/*.db` |
| 表前缀 | `wc_` |

## 构建与 E2E

```bash
./examples/wonder-campus/scripts/build-server.sh
./examples/wonder-campus/scripts/e2e-accept.sh
```

启动服务：

```bash
mkdir -p build/wonder-campus
DATABASE_URL=sqlite:./build/wonder-campus/wonder.db \
  examples/wonder-campus/.jnative/build/wonder-server
```

浏览器或 WebView 打开 `http://127.0.0.1:18880/`。

## 功能总览（商业化版）

| 角色 | 能力 |
|------|------|
| 教务 | 学员档案（含家长、邀请码）、招生线索 CRM（跟进→试听→转学员）、商品售卖/报名/分期收款、排课日历（按周重复、冲突检测、停课）、课程/班级/教室/员工/商品管理、机构规则设置、公告、请假审批、续费预警 |
| 负责人 | 经营报表（收款/课消/出勤/转化/欠费/漏斗/趋势）、课酬统计、家长评价、CSV 导出 |
| 老师 | 我的课表、签到与课堂反馈、布置作业与点评、我的学员、我的课酬、匿名评价 |
| 家长 | 多孩切换、课程表、请假与补课、作业提交、成长反馈、课后评价、续费咨询、公告通知 |

账号：账号+密码登录（连续 5 次失败锁定 10 分钟）、邀请码注册（8 位、72 小时、一次性）、改密。
测试环境设置 `WC_ALLOW_DEMO_LOGIN=1` 时，演示账号统一密码 `Wonder2026`；生产环境请勿开启。

```bash
# 商业化接口冒烟（需在全新库上运行；95 项断言）
WC_BASE=http://127.0.0.1:18880 python3 examples/wonder-campus/scripts/smoke_commercial.py
```

已知限制：服务端 JSON 解析暂不支持 `\uXXXX` 转义；WKWebView 内 CSV 下载不稳定，导出页同时提供「复制全部」；支付网关与系统推送不在范围内。

## 文档
- `docs/product-design/商业化完善路线图.md` — 产品缺口、业务规则、里程碑

- `CAPABILITY.md` — 能力边界
- `RULES.md` — 收费与考勤规则
- `docs/REGRESSION-REPORT.md` — 双链路与权限回归
- `mobile/BUILD-RECORD.md` — iOS/Android 壳构建声明
