# E07/O05 归档恢复隔离候选（2026-10-17）

本记录收口一个新的、一次性的隔离恢复候选。它不连接原库或现网，不使用正式签名，不改变生产开关或 `localDeletionAllowed=false`，也不恢复已取消的接收者确认/激活链。它不能代替 O05 的外置介质保管、现场取回、真实 PDA、Chrome、Android 或 D18 L3。

## 范围与改动

本次输入是工作树 `e3cebea70eb89576fc014c20128ea4940f3d273b` 加继承 dirty 改动；完整接管快照和改动保护见 `.local/goal-20261017-resume/e07-o05-l2-01/checkpoint-before-fix/`。本切片只修复恢复验收夹具的环境绑定：

| 路径 | 影响层 | 变化与直接验证 |
| --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/inventory/FormalArchiveRecoveryFixture.java` | 隔离 FORMAL HTTP fixture | 使用 `E2aTestPorts.http()`，不再连固定端口；run695 的 FORMAL 创建、恢复后读取和归档确认均实际通过。 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aTemporaryArchiveRecoveryIT.java` | 隔离恢复组合 | 历史 V34/V36 种子后继续完整迁移；写入本次尝试的迁移标记，供 Python 消费者核对实际候选输入。run695 source/restore 探针均通过。 |
| `docs/parallel/R09/ops/temporary_archive_probe.py` | 独立 Python 消费者 | 从本次环境读取根目录、run、端口和专用身份，并严格核对 `client.cnf`、datadir、schema 标记、数据库和 binlog；不再使用旧 worktree、固定端口或固定迁移假设。run695 source/restore 均通过。 |
| `server/src/test/java/com/acrylic/outbound/e2averify/E2aArchiveRecoveryPython.java` | 隔离 Python 调用器 | 复用本机专用 `R09_PYTHON` venv（既有改动，未改生产工具）；run695 实际调用 PyMySQL/cryptography 消费者和故障套件。 |

提交前记录：上述 Java fixture/IT 为服务端测试层，Python 为独立消费层；必跑清单为 `git diff --check`、Python `py_compile`、`testClasses`、真实隔离 HTTP/MySQL、源/恢复 Python 消费、恢复后 HTTP 和故障回滚。复用依据为现有 `E2aTestPorts`、`E2aCrossSourceProfile`、`E2aArchiveSnapshotRestore` 和 O05 同机复制验收的固定路径/身份围栏；没有复用 run693/694 的失败结果作为通过依据。

## run695 结果

命令如下，端口和数据库仅属于本次隔离尝试；Python 指向已存在的 `.local/r09-control-venv`，没有安装或修改全局依赖：

```text
$env:R09_PYTHON=(Resolve-Path '.local/r09-control-venv/Scripts/python.exe').Path
powershell -NoProfile -File tools/e2a-cross-source-verify.ps1 -Run 695 -DbPort 19703 -HttpPort 19704 -ArchiveRecovery -AttemptPath '.local/e2a-cross-20260925-695'
```

结果为退出 0，MySQL 经专用账号认证停机，19703–19706 无残留监听。JUnit XML 为 `server/build/test-results/integrationTest/TEST-com.acrylic.outbound.e2averify.E2aTemporaryArchiveRecoveryIT.xml`：1 test、0 skip、0 failure、0 error。

- `.local/e2a-cross-20260925-695/archive-inspect-source.log`：独立 Python 消费通过，TEMP 2 个归档、3 个命令、FORMAL 1 个归档、8 个文件引用。
- `.local/e2a-cross-20260925-695/archive-inspect-restore.log`：同一组引用在完整恢复库上通过。
- `.local/e2a-cross-20260925-695/archive-restore-faults.log`：11/11 故障测试通过，覆盖缺失/损坏正文、错误签名身份/能力、原审批损坏、审计/幂等引用、历史 key 和撤权不改历史。
- `.local/e2a-cross-20260925-695/archive-restore-summary.json`：恢复快照包含 179 张表和 15 个文件，源/恢复逻辑快照相等。
- `.local/e2a-cross-20260925-695/test-completed.marker`：明确记录 TEMP V2、FORMAL V1、原审批闭合、故障回滚和恢复后真实 HTTP 已执行，并注明没有灾难重建。
- `connected-identity.tsv`、`private/server.err.log`：候选数据库、专用账号、datadir、端口和 binlog 身份核对，随后由专用账号正常停机。

`testClasses` 在 run695 前退出 0；`git diff --check` 和 Python `py_compile` 通过。run695 不是 Android 客户端行为、Chrome、外置副本实际取回、原 PDA/原身份或现场回执验证，因此不计 E07/O05 完整 L2、D14/D15 组合、D17 整体或 D18 L3。

## 失败与保全

- run693（`.local/e2a-cross-20260925-693/`）在 TEMP 真实 HTTP 查询后续表时失败；原因是旧夹具只迁移到历史截点。`candidate693-failure.json`、运行日志、XML、datadir 和停机记录保留。本次通过在历史种子后继续完整迁移修复了夹具假设。
- run694（`.local/e2a-cross-20260925-694/`）TEMP 归档已生成，但 FORMAL fixture 固定连接旧端口而失败；运行日志、XML、datadir 和停机记录保留。本次改为运行时隔离端口后复验通过。

两次失败都曾对隔离产品/fixture 写入，不能按无产品写入快速修复通道计数；没有重启旧 run，也没有覆盖或删除失败输入。run695 的完整 datadir、原始日志、专用身份和唯一恢复证据暂不清理，因其仍是本次候选的可复核输入。

## 结论与下一步

本候选证明当前工作树可在新的隔离输入上完成 TEMP/FORMAL 归档的真实 HTTP 生成、独立 Python 消费、全库/文件恢复、故障回滚和恢复后真实 HTTP。它只推进 E07/O05 的隔离候选证据；外置介质/异地保管、真实 PDA 文件通道、现场身份、Android/Chrome 组合、统一最终候选和 D18 全链仍未完成。下一步继续选择独立缺口，保留本候选及 693/694 失败证据，不把局部通过写成总目标完成。
