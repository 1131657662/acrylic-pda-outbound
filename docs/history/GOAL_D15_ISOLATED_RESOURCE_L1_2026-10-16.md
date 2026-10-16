# D15 Linux 隔离资源约束 L1 开发备份

三个既有模板的资源参数已在 WSL Ubuntu 的随机 user unit 中实际核验。最终 resource-07 的 CPU、任务数、分段内存及停止排空共9项通过，无跳过；独立只读 Python 回读通过。此为合成负载的 L1 开发备份，不代表项目 Linux 服务部署/143启停、D15整体、E07 L2、D18 L3或总目标通过。

## 输入与唯一写权

- 固定树 `D:/acrylic-pda-outbound`，分支 `codex/development`，输入HEAD/实核远端 `b1493af99b0cbcda0d73e55f26ed623c76a895fc`。版本事实见看板生成块。
- 本聊天 `01a13a57-8082-7dd2-a08f-987483dcda4b` 单助手单写者。清理聊天于2026-10-16 17:15:51 +08:00只读复核活动清理进程为0，并释放源码、证据、看板及全部产物窗口；协调回复保留在聊天记录中。
- 全部tracked/untracked字节、SHA/长度、binary diff、未跟踪副本、worktrees、旧看板、写权、进程/监听/磁盘见 `.local/goal-20261016-resume/input11–13.json` 及同号附件。input13共5395项，SHA `dfe29cc9236e0ac5238ad709f5734fbde87ecb040aec3f6d1813a9a028750b0d`。与input10比较，继承输入仅看板变化，另新增本页下列三个工具；未还原或混入其他改动。

| 改动路径 | 影响层 | 必跑清单 | 复用依据（路径+SHA） |
| --- | --- | --- | --- |
| `docs/parallel/R09/tests/systemd_resource_load.py` | 有界合成负载 | CPU、线程拒绝、实际内存触页、OOM子进程退出 | 最终输入原文见resource-07/runner-input.json；无业务、网络或数据库消费者 |
| `docs/parallel/R09/tests/systemd_resource_probe.py` | user unit/cgroup观察与停止 | 随机unit身份、原始命令/结果、cgroup归属、PID消失、失败finally停止 | resource-07各case/identity、initial、stopped和command原件；模板SHA见下表 |
| `docs/parallel/R09/tests/run_systemd_resources.py` | 模板资源行为验收 | 三模板各CPU/Tasks/Memory、停止完成后才记录PASS、严格新证据根 | resource-07/runner-input.json与templates.json；未修改模板或业务路径 |

未修改Android、网页、API、迁移或数据库，故本切片不重跑无关业务链。历史项目业务恢复/worker/监控证据继续按各正式报告输入SHA复用，不能把本页9项与旧结果拼成最终候选验收。

## 范围与设计决定

只运行 `systemd-run --user` 随机命名 `acrylic-d15-resource-<uuid>.service`，不安装unit、启用timer、操作共享MySQL或项目部署入口。资源值从模板直接读取；API/worker显式KillMode/SendSIGKILL，ops未声明时沿systemd默认值，均从实际属性复核。

夹具额外设置 `Restart=no`、45秒运行上限、2秒停止上限、`OOMPolicy=continue` 和 `MemorySwapMax=0`，目的是隔离负载与保留父进程观察内核事件。这些是本轮安全参数，不能声称生产模板已经禁用swap或采用同一OOM策略。

内存采用两段：先保持模板原MemoryHigh/MemoryMax，触发并保存原软阈值的high事件；再只对本轮随机unit使用 `set-property --runtime MemoryHigh=<原MemoryMax>`，保持原硬上限，观察max/oom/oom_kill。原软限额会持续节流，有限时间内未触及硬上限不能推断硬限额失效。因此将两个行为分别测量，完整记录调整命令；不声称原软/硬组合下已观察OOM，也不改模板降低标准。分配子进程将自身oom_score_adj设为1000，父进程只负责保存子进程SIGKILL退出与后续排空。

## 最终实际结果

证据根 `.local/goal-20261016/d15-resource-07/`；启动参数和Python/Linux版本原文在runner-input.json，systemd版本命令在command-001.json。最终命令：

```text
wsl -d Ubuntu -- python3 /mnt/d/acrylic-pda-outbound/docs/parallel/R09/tests/run_systemd_resources.py --evidence-root /mnt/d/acrylic-pda-outbound/.local/goal-20261016/d15-resource-07
```

| 模板路径 | SHA256 | CPU实际核占比 | 线程数/包含主线程上限 | 原high事件 | 硬上限max/oom/oom_kill |
| --- | --- | --- | --- | --- | --- |
| `deploy/test/acrylic-outbound-test.service` | `cb99bada5266ca86c11ed2a25d647cdd5748b7e6e6193efba1d68ffab1a66fc2` | 1.0021 | 255/256 | 76 | 46/1/1 |
| `deploy/test/acrylic-outbound-test-worker.service` | `0fb47b7dce408d785cce8942340c809970ffc2a3eb5cd52eb32a394ff6ef7fb5` | 0.5005 | 511/512 | 109 | 43/1/1 |
| `docs/parallel/R09/config/acrylic-outbound-test-ops-check.service` | `bdc311c39e5f7dfb2f8090b1f160115ff835dc4c4c0eff0b1306a9ef0ee29dc3` | 0.2509 | 31/32 | 96 | 40/1/1 |

- CPU六秒实际usage、nr_throttled及throttled_usec均增加；限额值与内核cpu.max一致。
- 三个TasksMax均实际拒绝下一线程，错误为 `can't start new thread`，pids.current到达上限且pids.events.max增加。
- 原MemoryHigh/MemoryMax、swap=0、主PID及实际cgroup成员关系均核同；分段硬上限测试的三个分配子进程均退出-9。子进程退出后memory.current分别回落6217728、6127616、6111232 bytes，低于原硬上限。
- 九个随机unit逐一执行stop，MainPID为0、inactive/failed，主进程/已登记子进程和停止前cgroup全部PID消失；只有停止完成后才写PASS。最终Linux进程清单没有负载或项目Java/MySQL，unit列表为空；原始记录见 `d15-resource07-linux-final.txt`。
- `py_compile`及文件/函数长度自审通过；三个文件均不足200行、函数不足30行。安全自审确认命令为参数数组、证据根经resolve限制在项目.local、新根拒绝覆盖，停止前核固定前缀及完整cgroup/unit身份，journal采集失败仍finally执行stop。
- 独立只读消费者 `.local/goal-20261016-resume/audit-resources.py` 检查三份模板和执行脚本与记录输入相同、九case身份/结果/停止、两段内存与SIGKILL事实。2101原件共1389228 bytes双次SHA/长度不变，清单 `resource-evidence-hashes01.json` SHA `3ed4957b3c6e242590f60b53249fb6012b4ea34926a73419c566828c8e54415b`；审计结果 `resource-audit01.json` SHA `9e77add3be2cf95b9bfcc2df3ab766b4c402dfd2bccc7f68f7e8504258afbb8c`。

## 失败、修正与保全

- resource-01/02原内存夹具42秒未取得预期结果/硬限额事件，API的CPU/Tasks通过；原命令、分配与停止证据保留。不能把原初样本high=0解释为运行全过程未发生high。
- resource-03修正直接memory-child缺ready入口后，末样本high=5348、memory.current=855744512 bytes，max/oom_kill均为0，42秒等待失败；stop后PID353消失。此为软阈值实际限流的新依据，未计硬上限通过。
- 据该依据实现上述两段观察，resource-04单模板memory通过，resource-05九项通过。自审再修正PASS记录时序、停止时journal失败仍执行stop；resource-06在worker OOM后立即读取memory.current时捕获内核尚未回收的断言失败，原OOM和exit/stop原件保留，没有伪称通过。
- 最终将内存回落断言移到分配子进程实际退出之后，resource-07九项全过；未删除失败指纹、重置计数或复用旧证据根。全部原始文件含在上方双次清单内。
- 曾只读核对命令使用WSL内未安装的rg，返回command not found；之后使用完整ps清单补核，未安装依赖或影响业务。首次snapshot13时Ubuntu已经自动停机，`wsl --list --verbose`确认Ubuntu与Docker均Stopped；没有终止其他消费者。

## 断点与未完成

重型动作前复测C/D余量，resource-07前为82824400896/45212438528 bytes；input13结束采样82745679872/45209354240 bytes。证据只新增D盘，未设置全局环境、未清理任何产物，卷余量变化不推断为本任务净增长。

665–670、1668仍停机，未分配671；原AVD/ADB和共享mysqld只观察不连接/停止，全部继承字节保护。未执行产品Linux服务启停、权限/事务/HTTP/MySQL或真实负载验收，不替代历史审批阻断；无部署、main、Release、生产/原库、正式签名或能力开关变更，`localDeletionAllowed=false`及七类生产决定/TEMP生产边界保持。

下一步继续D15升级/程序回退与数据库恢复兼容边界的隔离实测，D16正常合法退出与复杂引用、E06/E07/E08剩余L2、E09实际候选及D18最终L3仍必做。本页仅收本工具切片L1，不以局部验收结束总目标。
