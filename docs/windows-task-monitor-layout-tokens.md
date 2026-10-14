# Windows Task Monitor — W11 布局 Token

**状态：** 临时 DIP（96 DPI 逻辑像素）。截图经缩放，**不得**把图片像素当 DIP。  
**实测填空：** 在 Parallel / 参考机上量完客户区后改写「实测」列，并更新本表日期。

## 参考机模板（请在 Windows 上填写）

| 字段 | 值 |
|------|-----|
| Windows 版本 | _未采集_ |
| 任务管理器版本 / 文件版本 | _未采集_ |
| 显示分辨率 | _未采集_ |
| 缩放 / DPI | _未采集_（目标验收：100% / 125% / 150% / 200%） |
| 主题 | 浅色（本轮） |
| 任务管理器窗口客户区 (W×H) | _未采集_ |
| UI 语言 | 中文（zh-CN） |
| 采集日期 | _未采集_ |

## 壳层尺寸（临时 → 实测）

| Token | 临时 DIP | 实测 DIP | 说明 |
|-------|----------|----------|------|
| `navWidthExpanded` | 260 | | 左侧导航展开宽 |
| `navWidthCollapsed` | 56 | | 折叠（可选） |
| `navItemHeight` | 36 | | 导航行高 |
| `navSettingsPin` | bottom | | 设置固定底部 |
| `titleBarSearchHeight` | 48 | | 顶区含居中搜索 |
| `searchFieldWidth` | 320 | | 默认搜索框宽；随窗口变宽 |
| `searchFieldHeight` | 32 | | |
| `contentCornerRadiusTL` | 8 | | 白色内容面板左上圆角 |
| `contentPadding` | 16 | | 内容区内边距 |
| `cmdBarHeight` | 40 | | 「进程」+ 命令按钮行 |
| `cmdButtonHeight` | 28 | | 命令按钮 |
| `dualHeaderHeight` | 28 | | 资源列上方整机摘要条 |
| `tableHeaderHeight` | 28 | | ListView 单行表头 |
| `rowHeight` | 30 | | 进程行 |
| `sectionRowHeight` | 28 | | 「应用 / 后台 / Windows」分区头 |
| `iconSize` | 16 | | 进程图标 |
| `expandIconSize` | 12 | | 展开箭头 |
| `nameColWidth` | 300 | | 名称列初始 |
| `nameColMax` | 420 | | 名称列上限；宽屏右侧留白 |
| `statusColWidth` | 100 | | 状态 |
| `cpuColWidth` | 88 | | CPU（不 flex） |
| `memColWidth` | 100 | | 内存 |
| `diskColWidth` | 88 | | 磁盘（本轮「—」） |
| `netColWidth` | 88 | | 网络（本轮「—」） |
| `resourceColFlex` | 0 | | 资源列不拉伸填满 |

## 颜色（浅色临时）

| Token | RGBA | 用途 |
|-------|------|------|
| `appBg` | 243,243,243,255 | 壳 / 导航底 |
| `contentBg` | 255,255,255,255 | 白内容面板 |
| `text` | 32,33,36,255 | 主文字 |
| `muted` | 96,101,108,255 | 次要 / N/A |
| `sep` | 232,232,232,255 | 分隔 |
| `accent` | 0,103,192,255 | 强调 |
| `navSelected` | 229,241,252,255 | 导航选中 |
| `navHover` | 242,246,250,255 | 导航悬停 |
| `rowSelected` | 229,241,252,255 | 行选中 |
| `rowHover` | 245,245,245,255 | 行悬停 |
| `sectionBg` | 248,248,248,255 | 分区头 |
| `heatCpu` | 向 #0078D4 混合 | CPU 热度 |
| `heatMem` | 向 #0078D4 混合 | 内存热度 |
| `dangerBg/Fg` | 252,232,230 / 197,34,31 | 结束任务 |

## 字体（临时）

| 用途 | 字号 pt | 字重 |
|------|---------|------|
| 页面标题「进程」 | 20 | regular |
| 导航项 | 13 | regular |
| 表体 / 命令 | 13 | regular |
| 分区头 | 12 | semibold（若后端支持，否则 regular） |
| 摘要 / 页脚 | 12 | regular |

## 对齐说明

- 宽屏：名称列受 `nameColMax` 限制，资源列保持固定宽，**右侧留白属于参考布局**。
- 窄窗：优先保证名称 + CPU + 内存可见；磁盘/网络可水平滚动（若 ListView 支持）。
- DPI：布局以逻辑 DIP 计；Win32 后端当前按 96 DPI 虚拟坐标；高 DPI 验收在 Parallel 记录偏差。
