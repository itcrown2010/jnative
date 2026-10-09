# 添加第三方源码包

CLI 提供包相关子命令（`--help`）：

```text
jnative add|remove|update|deps|search|info|publish|audit|licenses [--registry DIR]
```

## 当前文档口径

| 项 | 状态 |
|----|------|
| 命令存在 | 是 |
| 端到端「搜索 → 添加 → 构建」在干净环境验收 | **未验证**（阶段 1） |
| 任意 Maven Central JAR | **未支持**（产品定位） |

JNative 包模型面向 **源码包**（`jnative.toml` 中 `type = "source"` 等），不是 Java 字节码生态。

## 建议工作流（待你本地验证后可升格）

```bash
jnative search <keyword> [--registry DIR]
jnative add <package> [--registry DIR]
jnative deps
jnative build
```

使用 `--registry` 指向你信任的本地或团队注册表目录。`doctor` 中的 **Package cache** 通过仅表示缓存路径可用，不代表某个第三方包已验证。

## 在文档示例中复现依赖

阶段 1 教程 **不** 依赖外部包。需要共享代码时，优先用多文件源码根或仓库内 `examples/` 路径，避免读者卡在未验证的注册表上。

## 下一步

[常见安装问题](/guide/troubleshooting-install)。
