# 迁移指南

## 从旧文档 / README 版本号迁移

若你仍看到 README 写「0.12」等历史版本：以 **`jnative --version`** 为准。旧优化器/HTTP 专题文档可能仍有用，但平台支持以本站矩阵为准。

## 从「当作 Java SE」迁移

1. 去掉对第三方 JAR 的依赖，改为源码包或自研模块。  
2. 用 `jnative doctor` 与最小程序验证 API，而不是 OpenJDK Javadoc。  
3. IDE：安装 JNative 扩展，勿假设 Java 扩展的补全能代表 JNative。

## 项目清单

旧仅单文件用户 → 增加 `jnative.project` + `src/` 以使用 `build` / VS Code。模板：`jnative new`。

## UI / 移动

- 桌面 ≠ 自动 Web ≠ 自动移动。  
- 移动是容器方案；从纯 Web 迁移时同步 www 资源与壳工程标识（applicationId / bundleId）。

## 破坏性变更

本页仅记录已核实变更。尚未核实的条目不写。有变更时在此追加「版本 → 行为 → 迁移动作」。
