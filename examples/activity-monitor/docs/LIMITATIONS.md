# 限制与测试结果

## 首版范围

- 仅 macOS（arm64/x86_64 Darwin）。  
- 无网络、磁盘、能耗、启动项面板。  
- 无线程状态明细（仅线程数）。  
- 不宣称与苹果「活动监视器」数值一致。

## 已知限制

- 部分系统进程字段需更高权限；失败显示「不可用」。  
- Cocoa `TableView.setItems` 频繁全量重建可能崩溃（平台 peer）。GUI：warmup 双采样后建表；自动刷新只更新底栏；搜索/排序/「立即刷新」才重建表格。  
- 跨线程 `invokeLater`：warmup 必须在同一后台线程取两次快照再一次回写，否则第二次 TASK 可能丢失导致界面停在「正在采样」。  

- 系统 CPU 使用 host ticks，进程 CPU 使用 task CPU 时间，二者口径不同，勿横向硬比。  
- 自身保护仅覆盖当前进程 PID / PID≤1 / `kernel_task`；其他关键进程需用户自行谨慎。

## 验证结果（本机跑通）

命令：`./examples/activity-monitor/scripts/run_verify.sh`  
日志：`build/activity-monitor/verify-results.txt`

已覆盖：

- 真实进程出现/退出、名称/PID/RSS  
- 空闲与 `am_burn` CPU 变化、双采样  
- 搜索过滤（`--dump` 名称）、身份保持逻辑（GUI）  
- SIGTERM、`am_ignore_term` + SIGKILL  
- 过期身份拒绝（`-2`）、自身保护（`-1`）  
- 有界 bench：约 30–50ms/次全机采样（进程数 ~550）

未自动化：GUI 截图级点击（需本机目视）；窗口关闭后无残留采样依赖 `alive`/`sampleToken` 逻辑。
