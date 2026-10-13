# 全新环境验收记录（桌面 UI）

作者：张晓达  
日期：2026-10-13  
说明：在**独立目录**走完开发者路径。缺工具的步骤标为「未通过 / 环境缺失」，不得标通过。

## 环境

| 项 | 值 |
|----|-----|
| OS | macOS arm64 |
| 仓库 | `/Users/zhangxiaoda/jnative`（验收时用临时副本或独立工作目录） |
| JDK | 构建 `mvn package` 所需 |
| VS Code 扩展 | `tools/vscode-jnative`（需本机安装/链接） |
| 调试器 | CodeLLDB（可选；未安装则断点步骤失败） |

建议独立目录：

```bash
TMP=$(mktemp -d /tmp/jnative-fresh-XXXX)
# 将已构建的 target/jnative.jar 与 examples/desktop-sample 拷入，或 git clone 后 package
```

## 步骤结果

| # | 步骤 | 结果 | 备注 |
|---|------|------|------|
| 1 | 配置 SDK（`jnative.jar`）与 VS Code 插件 | 部分支持 | `mvn package` → `target/jnative.jar` 已验证；扩展需本机装 `tools/vscode-jnative`（本轮未在隔离机重装） |
| 2 | 创建或打开 sample | 通过 | `examples/desktop-sample`（`jnative.toml` + `src/Main.java`） |
| 3 | 编辑并补全 | 部分支持 / 未在新鲜机验证 | 依赖已装扩展；仓库内有 LSP verify 产物 |
| 4 | 制造错误并定位 | 部分支持 | CLI：`java -jar target/jnative.jar …` 可报错；IDE Problems 需扩展 |
| 5 | 构建运行 | 通过 | `examples/desktop-sample/scripts/build.sh`；`DS_SELF_TEST_OK` |
| 6 | 命中源码断点 | 未通过（环境） | 需 CodeLLDB；本轮未证明新鲜机断点 |
| 7 | 打包 `.app` 并双击运行 | 通过（产物） | `build/desktop-sample/DesktopSample.app`；`pack-app.sh`；未签名 |
| 8 | 关闭后无遗留任务 | 通过 | stress soak 后 `AM_NO_LEFTOVER_PROCESS=1`；`requestQuit` 清队列 |

## 命令验收（不依赖 VS Code UI）

```bash
cd /path/to/jnative
mvn -q -DskipTests package
examples/desktop-sample/scripts/build.sh
./build/desktop-sample/DesktopSample --self-test
# 期望：DS_SELF_TEST_OK
open build/desktop-sample/DesktopSample.app
# 手动退出后：
pgrep -lf DesktopSample || echo "no leftover DesktopSample"
```

## 结论

- **CLI 构建 / self-test / `.app` 产物**：可在本仓库脚本路径验收。  
- **VS Code 补全 / 断点**：以扩展与 CodeLLDB 是否安装为准；缺失时报告环境，不记「通过」。  
- **不得**因 CLI 成功而宣称完整 IDE 新鲜环境已通过。
