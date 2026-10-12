# 活动监视器：操作与开发教程

## 一、运行 GUI

```bash
./examples/activity-monitor/scripts/build_desktop.sh
./build/activity-monitor/ActivityMonitor
```

1. 顶栏概览约每 1.5s 自动更新；表格在第二次采样后出现（CPU% 已有差分）。  
2. 之后自动采样只更新概览；点「立即刷新」或改搜索/排序以重建表格行。  
3. 「暂停刷新」停止后台循环；选中 →「详情」/终止（确认 + PID 启动时间校验）。  

关闭窗口后 `alive=0`，采样 token 递增，后台回调不再更新 UI。

## 二、无界面排查

```bash
./examples/activity-monitor/scripts/build_core.sh
./build/activity-monitor/AmCore --dump am_   # 过滤名
./build/activity-monitor/AmCore --find am_idle
./build/activity-monitor/AmCore --bench 8
```

终止测试进程（勿对用户应用使用）：

```bash
# 先 --find 拿到 pid / startSec / startUsec
./build/activity-monitor/AmCore --signal <pid> <startSec> <startUsec> term
./build/activity-monitor/AmCore --signal <pid> <startSec> <startUsec> kill
```

## 三、架构

```
desktop/ActivityMonitor.java  → UI 线程 + invokeLater
core/AmCore.java              → 快照解析、CPU 差分、终止与保护
native/jn_am_macos.c          → libproc / Mach / sysctl / kill(2)
```

构建：`clang` 打成 `libjn_am.a`，`jnative … -L build/activity-monitor -l jn_am`。

## 四、扩展注意

- 采样在后台 `Thread`，结果 `Application.invokeLater`；`inFlight` 防止积压。  
- 选中用身份键，不用行号。  
- 新平台：新增 `jn_am_*.c` 实现同一 ABI，勿在 JNative 里解析 `ps` 文本。  
- 网络 / 磁盘 / 能耗 / 启动项：**非首版**。
