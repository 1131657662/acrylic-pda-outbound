# D15 旧程序写入与新 worker 的双任务 `SCAN_XLSX`：隔离 L1

本页记录 run704 的有界 D15 开发备份：旧 JAR 在同一隔离服务上创建两个独立 `SCAN_XLSX/ORIGINAL_CONFIRMED` 任务，由一个新 JAR worker 依次处理，再由旧/新 JAR 各下载两份文件并由独立 ZIP/XML 消费者读取。它补上任务队列的双任务方向，不代表 D15 整体、E07 L2、D18 L3 或总目标完成。

## 输入与失败保全

运行目录为 `.local/e2a-cross-20260925-704/`，日志为 `.local/goal-20261017-resume/d15-multitask-l1/run704.log`；端口为 `19735`/`19736`（预留 `19737`/`19738`），运行后隔离 MySQL 已认证停机且端口无监听。沿用保留 SQL、storage、DER 和 HTTP 输入，不连接原库/现网，不触碰共享 MySQL、原模拟器或 ADB。

run702 和 run703 失败原件继续保留，未重启或复用：run702 在第二个任务已实际写入后，runner 固定写 `queued-http-result.json` 发生 `FileExistsError`；run703 在双任务已生成并 worker 成功后，首个下载已完成，runner 把二元返回值误解包为三元组。两者分别属于证据文件命名和夹具返回值误用，均不计业务通过。

## 实现

- `run_d15_old_worker_multitask.py` 编排两个旧 JAR HTTP 创建、一个新 JAR worker、旧/新各两次下载和隔离停机。
- `d15_multitask_checks.py` 验证两任务的 session/event 增量、任务状态/artifact、全表和文件集合守恒，并复用 `SCAN_XLSX` 独立消费者逐任务核对六列表。
- `d15_old_worker_http.py` 的 `enqueue` 增加可选证据文件名，保留既有默认名，避免多任务证据互相覆盖。

## 实际结果

run704 使用 `D15_WORKER_RUN=704`、`D15_WORKER_PRODUCER=old`、`D15_WORKER_FORMAT=SCAN_XLSX`、`D15_WORKER_BASIS=ORIGINAL_CONFIRMED` 退出码为 0。恢复后的 180 张表/非空资料先经基线核对；两个独立任务均通过未授权、未知仓库、PENDING、同键幂等和异格式冲突，单个新 JAR worker 处理两项，事件、任务状态和 attempts 守恒通过。旧/新 JAR 各下载两份，四次均为 `SCAN_XLSX`、HTTP 200、可下载且独立消费者通过。

四份下载按任务成对相等：任务 A 为 `4383` bytes、SHA-256 `79f88c5ddf34e0c59207db7b0beb9ba862b092c764cbee926e33255013456de8`；任务 B 为 `4384` bytes、SHA-256 `3d8c59f92e40acc0befbcbfeca2d2835fd96164ea4e7e7dbc25e12dbcd3876ba`。两任务快照 ID 不同，说明每个请求拥有独立固定快照；renderer 日志、快照、两个 attempt 目录和原始 10 个文件均按集合守恒核对。完整 facts、任务响应、四个下载和消费者结果见 `.local/e2a-cross-20260925-704/`。

本批源码 SHA-256：`d15_multitask_checks.py`=`964bb8ec4e9aebd284e938649f69a2a6e2d58714ce42651fc3a9fd5bd4e0987b`，`run_d15_old_worker_multitask.py`=`e2aca5cb290906f1b66a14c17f6c905d116a3501e62b5bcb8fd959dcb2438271`，`d15_old_worker_http.py`=`df8e745aad9ea6baa8db019e9dc8e05ded290555789a443d27ed6906ebdd7613`。

## 边界

这是同一订单、两个独立任务、单 worker 的隔离 L1；未覆盖多订单汇总、PDF、升级/回退完整组合、并发 worker、Android/Chrome、项目 Linux 现场、真实 PDA/原身份或 D18 全链。run702/run703 的失败计数和原件不因 run704 修复清零；D15/E07/E06/D17 完整 L2、统一候选和第8节闸门仍未完成。
