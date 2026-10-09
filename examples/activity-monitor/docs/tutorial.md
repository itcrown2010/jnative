# 活动监视器：操作与开发教程

## 一、运行 GUI

```bash
./examples/activity-monitor/scripts/build_desktop.sh
./build/activity-monitor/ActivityMonitor
```

1. 等待约 1.5s 自动刷新；首次 CPU 列为「等待采样」，第二次起显示百分比。  
2. 搜索框输入名称 / 用户 / PID 过滤（刷新时保留过滤词与选中身份）。  
3. 「按 CPU / RSS / 名称 / PID」切换排序。  
4. 「暂停刷新」停止后台采样循环；「立即刷新」仍可手动采样。  
5. 选中进程 →「详情」查看口径说明；「结束进程」/「强制结束」需确认。  

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
