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

## 文档

- `CAPABILITY.md` — 能力边界
- `RULES.md` — 收费与考勤规则
- `docs/REGRESSION-REPORT.md` — 双链路与权限回归
- `mobile/BUILD-RECORD.md` — iOS/Android 壳构建声明
