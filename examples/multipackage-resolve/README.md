# 多包名称解析示例（`demo/greetkit`）

验证：**语言 package / import / 访问控制** + **第三方源码包**（包管理坐标 `demo/greetkit` ≠ 语言包 `com.demo.greet`）。

## 布局

```text
examples/multipackage-resolve/
├── library/                 # 第三方源码库 demo/greetkit@1.0.0（.jn）
│   ├── jnative.toml
│   └── src/
│       ├── Greeter.jn       # public interface（com.demo.greet）
│       └── Texts.jn         # public / package-private 静态方法
├── app/
│   ├── jnative.toml         # 依赖 "demo/greetkit" = "1.0.0"
│   ├── jnative.project
│   ├── src/                 # com.demo.app：Main + 两个 Greeter 实现
│   └── negatives/           # 负例（单独编译，不进默认构建）
│       ├── BadAccess.jn     # 跨包调用 package-private
│       └── AmbiguousCall.jn # 重载歧义
└── registry/                # 本地文件注册表
```

## 构建与运行

```bash
mvn -q -DskipTests package

rm -rf examples/multipackage-resolve/registry/demo
rm -rf examples/multipackage-resolve/app/.jnative \
       examples/multipackage-resolve/app/jnative.lock \
       examples/multipackage-resolve/app/build

java -jar target/jnative.jar publish \
  --project examples/multipackage-resolve/library \
  --registry examples/multipackage-resolve/registry

java -jar target/jnative.jar build \
  --project examples/multipackage-resolve/app \
  --registry examples/multipackage-resolve/registry

./examples/multipackage-resolve/app/build/greet-app
```

期望输出：

```text
400
3
8
```

## 负例

将库源码与负例拼接后前端分析，或直接对 `negatives/*.jn`（含依赖类型）做 `jnative analyze` / 单文件编译，应看到：

- `BadAccess`：`not accessible` / package access，带行列。
- `AmbiguousCall`：`ambiguous method call`。

自动化：`mvn -q -Dtest=PackageAndAccessTest,MultipackageResolveIntegrationTest test`
