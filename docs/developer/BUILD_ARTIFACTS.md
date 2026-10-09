# 构建产物与可重复构建

## 成功才替换有效产物

`CompilerSession` 将输出写到 `*.tmp.<generation>`，成功后再原子发布到目标路径。失败时：

- 删除 staging；
- **保留**磁盘上一次成功的二进制（last-known-good）；
- 写入 `.jnative/build/<target>/build-result.json` 且 `success: false`，使运行器不得把旧二进制当作**本次**构建结果。

## build-result.json

路径：`.jnative/build/<targetName>/build-result.json`

| 字段 | 说明 |
|------|------|
| success | 本次生成是否成功 |
| status | `success` / `failed` |
| compilerVersion | 如 `0.32` |
| target / triple | 目标名与编译 triple |
| optLevel / release | 优化设置 |
| inputFingerprint | 缓存键（含源指纹与选项） |
| lockFingerprint | `jnative.lock` 哈希（若有） |
| artifact | 成功时的绝对路径；失败为 null |

多目标使用独立 `output` / staging，避免互相覆盖。

## 可重复构建（当前承诺）

**保证（行为 / 输入稳定）：**

- 文件发现与符号/元数据顺序使用确定性集合；
- 依赖锁文件参与指纹；
- 缓存键含编译器版本、triple、opt、源内容哈希；
- 增量与干净构建不一致时应以完整重建为准（缓存未命中路径已走完整编译）。

**不承诺（除非固定工具链）：** 字节级可复现二进制。clang/LLVM 版本、SDK 路径、时间戳嵌入会导致差异。需要位级复现时须固定工具链版本并关闭非确定性标志；当前未经验证，不得对外承诺。

## 运行守卫

- CLI / VS Code：构建失败则不启动旧产物。
- 移动：`MobileRunner` 要求新鲜 build stamp。
- 编辑时 LSP 仅 analyze，不执行完整原生链接。
