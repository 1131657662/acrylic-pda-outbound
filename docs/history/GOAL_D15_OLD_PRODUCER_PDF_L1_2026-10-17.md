# D15 旧程序写入与新 worker 的 PDF 兼容：隔离 L1

本页记录 run713 单任务与 run714 双任务的有界 D15 开发备份：旧 JAR 通过真实 HTTP 创建 `PDF/ORIGINAL_CONFIRMED` 任务，由新 JAR 定时 worker 执行，再由旧/新 JAR 下载产物，并由独立 Python PDF 消费器读取。它只补隔离 PDF 方向，不代表 D15 整体、E07 L2、D18 L3 或总目标完成。

## 输入与验证

固定树为 `D:/acrylic-pda-outbound`、分支 `codex/development`，本切片基线 HEAD/远端为 `65d7f2dba9c8543df4be3d8e64ec19b001b7a1b1`。运行命令使用 `D15_WORKER_RUN=713`、数据库端口 `19759`、HTTP 端口 `19760`、旧 producer、新 worker、`PDF` 和 `ORIGINAL_CONFIRMED`；隔离实验根为 `.local/e2a-cross-20260925-713/`，摘要为 `.local/goal-20261017-resume/d15-pdf-l1/run713-result.json`。`run713.log` 是终端已观测六行的转录，不是重定向原日志；完整原进程输出仍在 lab 下 `*-stdout/stderr`。原执行 handle 已结束，终端退出码未单独落盘；成功依据为 `success.json`、全套消费者产物和 `mysql-stopped.json`（认证停机 exitCode=0），摘要所列 exitCode=0 是依此推定，不作为独立退出码回执。

旧 producer 实际完成认证登录、未认证查询 401、未知仓库 404、PENDING、同键幂等及异格式冲突；旧/新下载进程分别完成登录、任务查询、下载、原键重放和异格式拒绝，不重复声称每个进程均执行创建前的权限反例。worker 处理后任务为 `SUCCEEDED/attempts=1`。

独立消费者通过 `%PDF`/`%%EOF`、`pdfinfo` 页数和文件大小、快照 ID 与“原确认V1”主题、无注释以及 `pdftoppm` 首页栅格化。数据库保持 180 张表，允许的登录/请求/生成/下载审计、session 和任务增量通过；原 10 个保留文件与 schema 守恒。下载 PDF 为 354512 bytes、SHA-256 `fda5df1e11eaf90630ae68eba811470d0c464cd39ff6005cfeb92ad349c65d64`，旧/新下载相同；renderer 日志无异常，两个固定 SogouInput sidecar 均非空并纳入文件集合守恒。隔离 MySQL 已认证停机，19759–19762 无监听，共享 MySQL、原模拟器和 ADB 未触碰。

## 本切片改动

- `run_d15_old_worker.py` 将消费者结果键从格式专用的 `xlsx` 改为 `artifact`，成功 scope 与终端输出从 `lab.FORMAT` 动态生成，避免 PDF 结果继续被标成 XLSX。
- PDF 结构与业务标记由 `d15_pdf_consumer.py` 独立校验；`d15_old_worker_checks.py` 继续验证任务、数据库、DDL、原文件和 renderer sidecar 守恒。
- 直接受影响验证为 py_compile、`git diff --check`、run713 单任务真实 HTTP/MySQL/PDF/独立消费和 run714 双任务真实 HTTP/MySQL/PDF/独立消费；run712 是同一 PDF 方向的前置通过记录。

| 改动路径（均位于 `docs/parallel/R09/tests/`） | 影响层 | 本切片必跑及实际证据 | 复用依据（路径+SHA） |
| --- | --- | --- | --- |
| `d15_old_worker_lab.py`、`d15_old_worker_process.py` | PDF 参数/浏览器路径、隔离 JAR/worker 启停 | run713 分配检查、旧创建→新 worker→旧/新下载、认证停机/零监听 | run713 `input.json` 绑定全部脚本与 29 个 renderer JAR；旧 JAR `.../e06-conflict-audit-1535/server.jar`=`adbd314f0fd619997dea0f16e0de36a963c3650173e991cbcf259ad1ae7184e1`；新 JAR `server/build/libs/acrylic-outbound-server-0.1.0.jar`=`e130aa2b7844daf6380045e3f3e809672b9c15f5ff31c4a24adf4cdf6bdc92ba` |
| `d15_old_worker_http.py` | HTTP 请求与下载后缀、异格式幂等冲突 | run713 `private/old-queued/`、`private/old-read/`、`private/new-read/` 原响应及摘要 | 复用 `d15_rollback_sql.py`=`0da65dd2ed3854cf205183a2d9be564e34e5e393d01082e1627b04c978ec6651`，不重跑历史 XLSX 业务 |
| `d15_old_worker_checks.py`、`d15_pdf_consumer.py`、`run_d15_old_worker.py` | Python 真实 PDF 消费、格式中性回执、全表/文件守恒 | py_compile、run713 `consumer01.json`、`success.json`；独立补充 `pdf713-content-result.json` 与两页 PNG | 快照 SHA `e8d7ca376de8ffdc54e63365d1c0a95d31ddae7c555dae27a1d8b800b081c0f0`；产品 renderer JAR SHA `5cdc5b25b8f0e2a4d797042b6fbafbf641fb5dd6c79282cdc86e6e630379a491` |
| `d15_multitask_checks.py` | 多任务 PDF 文件集合扩展 | run714 两任务排队、单 worker、旧/新四下载和独立 PDF 消费通过；结果摘要为 `d15-pdf-l1/run714-result.json`，runner 未写 `consumer01.json` | 原双任务 SCAN_XLSX 记录见 `GOAL_D15_OLD_PRODUCER_SCAN_XLSX_MULTITASK_L1_2026-10-17.md`；旧测试脚本 SHA `e2aca5cb290906f1b66a14c17f6c905d116a3501e62b5bcb8fd959dcb2438271`，仅作结构复用依据 |
| 本报告、`history/README.md`、`DEVELOPMENT_PROGRESS.md`、`ORCHESTRATION_STATUS.md` | 证据/断点索引 | 引用/差异、事实块 `-Write/-Check`、当前区上限 | 前置看板结论以 `65d7f2db:docs/ORCHESTRATION_STATUS.md` 归档于本切片 `board-before-pdf.md` |

独立补充只读消费者 `verify_pdf713.py` 解析 PDF 对象树并逐条比对固定快照：原始两行和有效两行、原始次序、前导零、601500 克、两页无 annotation 均通过；两页实际 PNG 人工可见核对无裁切/重叠。首次向 GBK 终端打印提取文本发生 `UnicodeEncodeError`，文本文件和 PNG 已先成功生成；后续 `-X utf8` 消费退出 0，未重启服务或数据库。run714 的四次下载由 `d15_pdf_consumer.py` 在 `d15_multitask_checks.downloads` 中实际消费，stdout 与每个下载/快照/sidecar 原件保留在 `.local/e2a-cross-20260925-714/`，没有额外生成独立 consumer JSON。作者、执行与自审均为同一助手，Python/Poppler 是独立工具消费者，非独立人员审查。

本批源码 SHA-256：`run_d15_old_worker.py`=`72083c854d88d40a5fdff795371c4a378d75f5d5a118a60fd4b64922f55af2a8`，`d15_old_worker_lab.py`=`0a8bcc2f3c3a2d55d528d12db2aee4a2f538f03aeda995ff35f11746c77ace08`，`d15_old_worker_process.py`=`3bae48c4f159eca2eef52b52b3b12b8a351c9404ed6496ee398c481caf90ff54`，`d15_old_worker_http.py`=`bf66e9dc29b769fcd17521bd8bb2af06ab75cbde1dcdb1dcbd71f7bd4c2eef4b`，`d15_old_worker_checks.py`=`616a6b97d71f3f06001f40780aa0e0feaa45204f8141079fa56378f1d3c95320`，`d15_multitask_checks.py`=`6b6cc6e55eabda659425adde5639e3cd120a823b9e3e566e45dd5845eaa81523`，`d15_pdf_consumer.py`=`db4347ff94577e97d49c15d23cfe604043541f34a05597234bb9fc4c21662f43`。

## 失败保全与边界

run705–711 的 PDF 失败原件继续保留，分别记录浏览器参数、GBK 解码、`File size` 解析、压缩字体、Subject 语义、页模式断言和固定 sidecar 文件守恒等运行器/消费者问题；这些失败不因 run712–714 清零。当前仍未覆盖并发 worker、升级/回退完整组合、Android、Chrome、项目 Linux 现场、真实 PDA/原身份或 D18 全链 L3；不连接原库/现网、不合 main、不发布部署、不使用正式签名，生产默认和 `localDeletionAllowed=false` 保持不变。
