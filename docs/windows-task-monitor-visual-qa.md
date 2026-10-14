# Windows Task Monitor — 进程页视觉验收

**前提：** 同一 Windows、主题、DPI、窗口客户区尺寸。  
**标记：** 未在参考机跑通前，结果一律 `WINDOWS_E2E_UNVERIFIED`。

## 1. 采集条件

1. 填写 [`windows-task-monitor-layout-tokens.md`](windows-task-monitor-layout-tokens.md) 参考机模板。  
2. 浅色主题；中文 UI。  
3. 固定客户区（建议先 1180×760 DIP 逻辑尺寸，再测最大化与窄窗 ~900×600）。  
4. DPI 矩阵：100% / 125% / 150% / 200%。

## 2. 截图步骤

1. 打开系统「任务管理器」→ 进程页；隐藏动态干扰（可暂停更新若参考支持）。  
2. 打开 `jnative-task-monitor.exe` → 进程页；同客户区。  
3. 截全窗口客户区，分别存：  
   - `docs/screenshots/w11-process/ref-<dpi>-<size>.png`  
   - `docs/screenshots/w11-process/jn-<dpi>-<size>.png`

## 3. 对齐与差异

使用 `examples/windows-task-monitor/scripts/visual-qa.ps1`（需 Windows + 可选 ImageMagick）：

1. 裁切到内容区（去掉系统标题栏若不一致）。  
2. 半透明叠加 → `overlay-<dpi>-<size>.png`。  
3. 差异图 → `diff-<dpi>-<size>.png`。

## 4. 动态区遮罩

不得用动态进程名/数值差异否决几何。遮罩：

- 名称列文字区、状态文字、CPU/内存/磁盘/网络数值单元格。  
- **保留可见：** 搜索框几何、导航宽、工具栏高、表头线、列边界、行高、分组头位置、图标槽、圆角。

固定测试数据（可选）：专用测试进程固定名称，便于叠图。

## 5. 对照检查表

- [ ] 搜索框位置与尺寸  
- [ ] 左导航宽与选中态  
- [ ] 命令栏与按钮禁用态  
- [ ] 双层摘要与列边界  
- [ ] 行高 / 分区间距 / 图标  
- [ ] 宽屏右侧留白（不强制拉满列）

## 6. 交互录屏（短）

连续刷新、搜索、排序、展开组、滚动、切页、改窗口尺寸；专用测试进程验证结束/效率模式。  
存 `docs/screenshots/w11-process/interact-<date>.mp4`（或自管路径并在测试报告引用）。

## 7. 性能自检

持续运行观察自身 CPU、内存、句柄、线程；不得为外观引入闪烁或泄漏。

## 本环境

macOS 开发机 **无法** 产出上述叠图；目录 `docs/screenshots/w11-process/` 仅占位说明，见该目录 `README.md`。
