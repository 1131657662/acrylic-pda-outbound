# D15 旧程序写入与新 worker 的 SCAN_XLSX 兼容：隔离 L1

本页记录 run701 的一个有界 D15 开发备份：旧 JAR 通过真实 HTTP 创建 `SCAN_XLSX/ORIGINAL_CONFIRMED` 任务，由新 JAR 定时 worker 执行，再由旧/新 JAR 下载同一扫码记录 Excel。它补上旧程序写入→新 worker 的专用扫码格式方向，不代表 D15 整体、E07 L2、D18 L3 或总目标完成。

## 输入与范围

固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`，运行前提交为 `d6082f26d191cdba19b51699ae775afb27f077e9`；本批唯一写者为当前会话。隔离实验根为 `.local/e2a-cross-20260925-701/`，接管运行日志为 `.local/goal-20261017-resume/d15-scan-xlsx-l1/run701.log`。

沿用已核对的保留 SQL、storage、DER 和 HTTP 输入；未连接原库/现网，未触碰共享 MySQL、原模拟器或 ADB。实验分配的 DB/HTTP 端口为 `19723`/`19724`（含 `19725`/`19726` 探测），运行结束均无监听；隔离 MySQL 已停机。

## 实现

- `d15_old_worker_lab.py` 允许 `XLSX` 与 `SCAN_XLSX`，并拒绝 `SCAN_XLSX` 的非 `ORIGINAL_CONFIRMED` 口径。
- `d15_old_worker_http.py` 在同键重放时按当前格式选择异格式，实际验证幂等冲突，而不是把同格式重放误判为冲突。
- `d15_old_worker_checks.py` 按格式选择消费者；新增 `d15_scan_xlsx_consumer.py` 只用 ZIP/XML 读取 `out` 单表六列，核对序号、型号、前导零条码、精确克数、扫码人和 UTC+08 时间，并拒绝公式/外链。

## 实际结果

`python docs/parallel/R09/tests/run_d15_old_worker.py` 使用 `D15_WORKER_RUN=701`、`D15_WORKER_PRODUCER=old`、`D15_WORKER_FORMAT=SCAN_XLSX`、`D15_WORKER_BASIS=ORIGINAL_CONFIRMED` 退出码为 0。恢复后的 180 张表/非空资料先经独立读者写入校验；旧 JAR 的未授权、未知仓库、PENDING、同键幂等和异格式冲突通过；新 JAR worker 生成成功；旧/新 JAR 下载均为 `4386` bytes，SHA-256 `5800fbf16ccf21ba8660155d13e180ea74aa8aabe3d2665aa8ac0516e83a1cda`，响应文件名均为 `scan-records-v1-<job>.xlsx`。

独立消费者两次读到 `out`/6列/2行、无公式和前导零条码 `003X37AC521`；任务事件为一次 `REPORT_EXPORT_REQUESTED`、一次 `REPORT_EXPORT_GENERATED` 及两次下载，数据库 180 张表的非业务表字节守恒，10 个原始文件未变，新增文件仅快照、生成文件和 renderer 日志。证据为 `.local/e2a-cross-20260925-701/consumer01.json`、`*-facts.json`、`*-files.json`、`private/old-read/05-http.json`、`private/new-read/05-http.json` 和 `storage/exports/attempts/**/renderer.log`。

本批消费者源码 SHA-256：`d15_scan_xlsx_consumer.py`=`58328fecbda66158e00a19648db6b649df5a4d9357be998c5f7088c281743161`；其余夹具改动分别见 `.local/e2a-cross-20260925-701/input.json` 的输入清单。样例 CLI 生成和独立消费者预检也通过；Python 运行器使用固定 UTC+08:00，避免本机缺少 `tzdata` 造成环境误报。

## 边界

这是单订单、单任务、旧 producer→新 worker 的隔离 L1；未覆盖多任务并发、跨订单汇总、PDF、升级/回退完整组合、Android/Chrome、项目 Linux 现场、真实 PDA/原身份或 D18 全链。`success.json` 的 `productionDefaultsChanged=false` 仅说明本批未改生产默认，不是生产验收。旧 run693/694/699 等失败原件继续保留，不重启旧 run。
