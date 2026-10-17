# D15 旧程序写入与新 worker 兼容：保留逻辑输入 L1

本页记录 run698 的一个有界 D15 开发备份：旧 JAR 通过真实 HTTP 创建 `ORIGINAL_CONFIRMED/XLSX` 任务，由新 JAR 的定时 worker 执行，再由旧/新 JAR 下载并消费同一产物。它补上旧程序写入→新 worker 方向的实际隔离证据，但不代表 D15 整体、E07 L2、D18 L3 或总目标完成。

## 输入与范围

固定树 `D:/acrylic-pda-outbound`、分支 `codex/development`，HEAD/远端为 `e3cebea70eb89576fc014c20128ea4940f3d273b`。本批唯一写者为当前会话；共享 MySQL、原模拟器和 ADB 仅观察。证据根为 `.local/goal-20261017-resume/d15-old-producer698/`，隔离实验根为 `.local/e2a-cross-20260925-698/`。

原 667 的完整 SQL 仍是保留输入，SHA `fa024e4683d9471fd329107614993d4bec0368a6c919ae333f9bb363a8bcca30`，604 项原始文件清单 SHA `180c908a8966ea3bf0a64a4355ad49e521b6a78b2ab4e145d363d5417f8042c3`。其中 527 个 `data/` InnoDB 文件、256148997 bytes 已按此前 D 盘清理授权删除；本轮没有声称这些 datadir 字节被恢复。夹具现在严格校验保留的 SQL、storage、开发验签 DER 和 HTTP 输入，并在证据中记录缺失 datadir 数量和字节数。

| 改动路径 | 影响层 | 必跑清单 | 复用依据 |
| --- | --- | --- | --- |
| `docs/parallel/R09/tests/d15_program_rollback_fixture.py` | 保留输入校验、SQL 导入、存储文件守恒 | manifest SHA/604项校验、缺失项必须仅为 `data/`、SQL/存储/DER/HTTP 输入逐字节校验、非空恢复 | 原 667 清单 `manifest-after.json` 与 SQL SHA；本轮输入预检和 run698 |
| `docs/parallel/R09/tests/d15_old_worker_lab.py` | 新运行号、端口、旧/新 producer 选择 | 新目录/端口/工作树/磁盘/JAR/renderer 独占核对 | 旧/新 JAR SHA 与 run677 夹具结构 |
| `docs/parallel/R09/tests/d15_old_worker_http.py` | 旧程序实际 HTTP 写入 | 401、登录、跨仓404、同键重放、异文409、PENDING | 原 D15 HTTP 客户端路径；本轮 `old-queued-*` |
| `docs/parallel/R09/tests/d15_old_worker_process.py` | 直接 JDK 进程与 worker | 监听 PID、worker 完成、子 JVM 排空、认证停机 | run677 进程围栏；本轮 `new-worker-*` |
| `docs/parallel/R09/tests/run_d15_old_worker.py` | 旧 producer→新 worker→旧/新 consumer 编排 | 全链真实 HTTP/MySQL、文件、Python、数据库/DDL 守恒 | 本轮 run698/run700 |
| `docs/parallel/R09/tests/d15_current_effective_xlsx_consumer.py`、`d15_old_worker_checks.py` | `CURRENT_EFFECTIVE` XLSX 独立消费 | 口径、快照、扫码明细、有效明细、汇总和无公式/外链校验 | 699 失败原件；700 新隔离复验 |

## 实际验证

命令：

```powershell
$env:D15_WORKER_RUN='698'
$env:D15_WORKER_DB_PORT='19711'
$env:D15_WORKER_HTTP_PORT='19712'
$env:D15_WORKER_PRODUCER='old'
C:/Python314/python.exe docs/parallel/R09/tests/run_d15_old_worker.py
```

退出码为 0。旧 JAR 真实创建任务并返回 `PENDING`；跨仓访问被产品返回 `404 WAREHOUSE_NOT_FOUND`，同键同文返回原任务，异文返回 `409 IDEMPOTENCY_CONFLICT`。新 JAR worker 实际将同一任务执行为 `SUCCEEDED/attempts=1`，旧 JAR 和新 JAR 均通过真实 HTTP 状态、下载、原键重放和异文拒绝。

数据库保持 180 表，业务表逐值守恒；只增加本轮合成查询身份、登录/请求/生成/下载审计、session 和导出任务允许增量。原 10 个保留存储/签名/输入文件未变，只增加本轮 snapshot、XLSX 和 renderer 日志。旧/新下载均为 13700 bytes、SHA `1758530ff6670620d103fceedcf86adb3688d0c9d10535a0885918afb3ee8ccb`；Python 七表消费者读到 2 行、601500 克、前导零 `003`、0 公式。MySQL 通过专用账号认证停机，19711–19714 无监听。

关键证据：`run698-summary.json`、`e2a-cross-20260925-698/success.json`、`consumer01.json`、`source667-before.json`/`source667-after.json`、`mysql-start-identity.json`/`mysql-stop-identity.json`、`old-read.xlsx`、`new-read.xlsx`。

## CURRENT_EFFECTIVE 口径补验

run699 首先完成了旧 JAR 创建→新 JAR worker→旧/新 HTTP 下载，任务为 `SUCCEEDED/attempts=1`，两次下载各为 13764 bytes、SHA `bfb01649bb0647479cd2eea8053ec1e064d2cc601d014e1cdf9d33e531c06f4f`。原独立 Python 消费者随后因硬编码 `ORIGINAL_CONFIRMED` 口径断言退出 1；失败发生在产品业务完成之后，证据和认证停机均保留，不计 run699 通过。

新增 `d15_current_effective_xlsx_consumer.py`，只复用原 ZIP/XML 读取、行和数值校验，改为严格要求 `CURRENT_EFFECTIVE` 元数据；没有修改原封存消费者。run700 使用新的隔离库/端口执行同一旧 producer→new worker 链，退出码为 0。旧/新下载各为 13766 bytes、SHA `b48deb923a6d7a24c4aeec6bb26445098c9a32edf3522dcfd87a581b17680428`，独立消费者读到 7 sheets、2 行、601500 克、前导零 `003`、0 公式；19719–19722 无监听，MySQL 认证停机。

run699 与 run700 均使用保留 SQL/存储逻辑输入，不恢复已删除 datadir，不改变生产默认或 `localDeletionAllowed=false`。700 只补 `CURRENT_EFFECTIVE/XLSX` 的口径兼容，仍不覆盖 `SCAN_XLSX`、PDF、多任务组合、升级/回退完整组合或 D15/D18 整体。

## 失败和边界

- run697 在任何 SQL 导入、HTTP 或业务写入前因缺失 `data/#ib_16384_0.dblwr` 退出 1；原件保留在 `.local/goal-20261017-resume/d15-old-producer697/`，不计业务失败或通过。
- 本轮成功使用的是保留 SQL/存储输入的逻辑重建副本。由于原 527 个 InnoDB datadir 文件不可取回，不能把它写成“604 文件原字节恢复”，也不能替代原身份、原 PDA、现场或生产回退事实。
- 本轮只覆盖 `ORIGINAL_CONFIRMED/XLSX` 的旧写入→新 worker 方向；新写入→旧 worker、旧/新多任务格式、升级/回退完整组合、Android、Chrome、真实外置介质、Linux 项目启停和 D18 全链仍未完成。

生产默认开关、七类生产决定、TEMP 生产归档和 `localDeletionAllowed=false` 未改变；不接原库/现网、不合 `main`、不发布部署、不使用正式签名。
