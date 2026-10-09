# 订单处理（能力组合验收）

多包 + 第三方源码包 `acme/moneykit` 的综合示例：加载订单 → 校验 → 汇总 → 查询 → 输出。
金额使用**整数分**（`MoneyCents`），不使用浮点。

## 布局

```
examples/order-processing/
  library/                 # 第三方 moneykit 源码
  registry/acme/moneykit/  # 本地 file registry
  app/
    src/*.java             # 扁平多文件；package 声明形成多包
    jnative.project
    jnative.toml           # depends on acme/moneykit
  scripts/verify.sh
```

| 包 | 类型 |
|---|---|
| `com.acme.money` | `MoneyCents`（定点加/乘、重载 prefer、泛型 identity） |
| `com.demo.catalog` | `Product` |
| `com.demo.order` | `Order` / `OrderLine` / `OrderStatus` / `OrderStore` / `OrderPipeline` … |
| `com.demo.app` | `Main` |

## 构建与运行

```bash
# 仓库根目录
mvn -q -DskipTests package

JNATIVE_REGISTRY=file://$PWD/examples/order-processing/registry \
  java -jar target/jnative.jar build --project examples/order-processing/app \
  --registry examples/order-processing/registry

cd examples/order-processing/app
./build/order-app          # 正常路径
./build/order-app bad      # 非法状态转移
./build/order-app stress   # 有界压力（N=200）
./build/order-app stress50
```

### 正常路径输出（可核对）

```
1
3500
200
1
1
7
34
1
10
42
```

含义：成功标记、总额 3500 分、状态码 POSTED=200、store size、prefer(int)=1、
identity(7)=7、摘要 UTF-8 字节数 34、emoji 读回成功、note UTF-16 length=10、finally 标记 42。

### 错误路径

```
0
1
42
```

## 一键验证

```bash
bash examples/order-processing/scripts/verify.sh
```

比较 O0 / 默认优化、正常/错误路径、Unicode 文件字节、固定种子 stress 确定性；产物在 `app/build/verify-artifacts/`。

## 调试

```bash
java -jar target/jnative.jar build --project examples/order-processing/app \
  --registry examples/order-processing/registry --debug
# 产出带 DWARF 的二进制；可用 lldb 对 app/src/*.java 设断点
```

VS Code 调试：未在本轮强制验收，见交付文档如实记录。
