# 多编译单元示例（独立解析）

验证项目级符号登记：**不拼接用户/依赖源码**；每文件独立词法/语法分析。

## 布局

```text
examples/multi-cu-resolve/
├── library/                 # 第三方源码包 demo/kit@1.0.0
│   ├── jnative.toml
│   └── src/Util.jn          # package com.demo.kit
├── app/
│   ├── jnative.toml / jnative.project
│   └── src/
│       ├── Main.jn          # com.demo.app — 调用第三方 + FQN 静态调用
│       ├── BaseService.jn   # 跨文件继承基类
│       ├── Service.jn       # 同名不同包 Node + 继承 BaseService
│       ├── Node.jn          # com.demo.app.Node
│       └── util/
│           ├── Node.jn      # com.demo.util.Node（与 app.Node 同简单名）
│           └── Link.jn      # 与 util.Node 交叉引用（无继承环）
└── registry/                # publish 目标（本地 registry）
```

覆盖点：跨文件继承、同简单名不同包、第三方源码包、循环文件引用（类型）、跨文件/FQN 方法调用。

## 构建

```bash
mvn -q -DskipTests package
java -jar target/jnative.jar publish --project examples/multi-cu-resolve/library \
  --registry examples/multi-cu-resolve/registry
java -jar target/jnative.jar build --project examples/multi-cu-resolve/app \
  --registry examples/multi-cu-resolve/registry
./examples/multi-cu-resolve/app/build/multi-cu-app
```

期望输出：

```text
42
7
42
```

负例（非法访问、重复 FQN、继承环、缺失类型）见 `MultiUnitCompileTest` / `PackageAndAccessTest`，不在本示例中编译。
