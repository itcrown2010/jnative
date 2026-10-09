# 第三方源码包：`acme/stats`（装箱 + ArrayList/HashMap）

验证 JNative 包管理 + `ArrayList<Integer>` / `HashMap<String, Integer>` / 增强 for / 中文键。

最终产物为原生二进制；**不**使用宿主 JVM 集合作运行时。

## 目录

```text
examples/third-party-collections-lib/
├── library/     # acme/stats@1.0.0 — ItemStats.java
├── app/         # 依赖 acme/stats，不复制库源码
├── registry/    # 本地文件注册表
└── README.md
```

## 构建与运行

```bash
mvn -q -DskipTests package
alias jnative='java -jar target/jnative.jar'

rm -rf examples/third-party-collections-lib/registry/acme \
       examples/third-party-collections-lib/app/.jnative \
       examples/third-party-collections-lib/app/jnative.lock \
       examples/third-party-collections-lib/app/build

jnative publish \
  --project examples/third-party-collections-lib/library \
  --registry examples/third-party-collections-lib/registry

jnative build \
  --project examples/third-party-collections-lib/app \
  --registry examples/third-party-collections-lib/registry

./examples/third-party-collections-lib/app/build/stats-app
# 期望: 60 / 3 / 2 / 2
```

## 契约

- 库使用 `Integer` 装箱与 stdlib `ArrayList`/`HashMap`（编译期并入，非 JAR）。
- 金额/财务仍应使用整数最小单位或 `Decimal`，勿把本示例当货币模型。
