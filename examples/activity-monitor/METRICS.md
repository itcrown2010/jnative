# 指标定义（macOS）

## 进程 CPU

| 项 | 定义 |
|----|------|
| 原始量 | `PROC_PIDTASKINFO`：`pti_total_user + pti_total_system`（纳秒） |
| 显示 | `(Δcpu_ns / Δmono_ns) × 100` |
| 100% | **一个逻辑核心**（非「整机 100%」） |
| 时钟 | `CLOCK_MONOTONIC_RAW`（失败则 `CLOCK_MONOTONIC`） |
| 首次 | 尚无上一采样 → 「等待采样」 |
| 失败 | 无 taskinfo → 「不可用」（`-2`），**不**显示 0% |

不把累计 CPU 时间直接当百分比。

## 进程内存

| 列 | API | 说明 |
|----|-----|------|
| RSS | `pti_resident_size` | 常驻集大小（字节） |
| 物理占用 | `ri_phys_footprint`（`proc_pid_rusage` / `RUSAGE_INFO_V2`） | 物理足迹 |

两列分开标注，不混称为笼统「内存」。

## 线程

`pti_threadnum`；无法读取时显示「不可用」。首版不做线程状态枚举（系统侧无稳定通用字段时不填假数据）。

## 系统概览

| 项 | 来源 |
|----|------|
| 逻辑核心数 | `hw.logicalcpu`（回退 `hw.ncpu`） |
| 物理内存总量 | `hw.memsize` |
| 页大小 | `host_page_size` |
| 空闲 / Active / Inactive / Wired | `HOST_VM_INFO64` 对应页计数 × 页大小 |
| 压缩相关页 | `compressor_page_count`（标为压缩器占用页，非「可压缩」） |
| 系统 CPU% | `HOST_CPU_LOAD_INFO` 两次采样的 ticks：`(user+system+nice)/total × 100` |

「已用(页合计)」≈ `(active + inactive + wired) × page_size`。  
**不保证**与 macOS「活动监视器」数字一致。

## 身份与终止

身份键：`pid + pbi_start_tvsec + pbi_start_tvusec`（`PROC_PIDTBSDINFO`）。  
终止：`kill(pid, SIGTERM|SIGKILL)`；先 `jn_am_check_identity`。
