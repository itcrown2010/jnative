# 限制与路线

下列内容 **当前不要当作可教语法**。需要时以编译器错误与议题为准。

## 未支持（文档承诺）

- 完整 Java SE / 任意第三方 JAR
- JPMS `module-info`
- JNI 兼容
- 原生移动控件 UI（仅 WebView 容器路线）
- VS Code 智能补全 / 跳转 / 重命名（当前明确未做）

## 实验性（可用但受限）

- 桌面 UI、WebUI、移动容器
- async/await、多线程
- 包注册表工作流
- 泛型与 lambda 的完整边角

## 未验证

- Windows / 多数 Linux 发行版原生工具链
- Android 完整安装验收（阶段 1）
- 商店发布流水线

## 路线（非承诺）

扩大 API 人工说明覆盖、第二阶段桌面/Web 教程验收、第三阶段移动与数独文档化。以 `docs/developer/STAGES.md` 为准，不在此写虚假完成日期。
