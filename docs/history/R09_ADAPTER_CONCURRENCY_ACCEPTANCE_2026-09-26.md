# R09 连接适配器并发执行与关闭验收

2026-09-26。主树 `codex/development@dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树 `codex/r06-e2a-migration@568683c1a1f8257ad0596a654c6b3558c767ed95`，加当批未提交差异。没有 commit/push/main/发布/部署。

## 范围与完成标准

完成标准来自[全应用恢复门禁实施断点](../parallel/R09/APPLICATION_ADMISSION.md)第 34 行“并发执行与 close”一项：同一 `Connection` 串行调用，执行与关闭不得竞争；关闭不得在执行未落账时放行，共享写者租约不得因某一条连接关闭而提前释放。

本批只补该标准下的既有缺口，**不新增生产接线**，未注册 Spring bean、未开放 NORMAL。

## 当批改动

服务端树单文件改动，未触碰其他未提交路径：

- `server/src/test/java/com/acrylic/outbound/recovery/RecoveryAdmittedDataSourceTest.java`：新增 2 项（12→14）。
  - `concurrentStatementExecuteIsAccountedForBeforeCloseReturns`：走真实 Statement 代理路径。执行线程在 `Statement.execute("UPDATE t SET v = 1")` 内被闩住，关闭线程随后在另一线程调用 `admitted.close()`；断言关闭返回时该执行已落账、`admitted.isClosed()` 为真，且第二个已接纳连接的共享租约仍让 fence 对第三次 `getConnection()` 报 `CONTROL_WRITERS_ACTIVE`；第二个连接关闭后才恢复接纳。
  - `concurrentAmbiguousExecutionPoisonsOnceAndCloseCannotReleaseTheFence`：执行线程在代理内等待后抛出 `08006`（结果不明），关闭线程同时调用 `close()`；断言异常按原样返回、重复 `close()` 幂等、fence 保持 `CONTROL_WRITERS_ACTIVE`。
- 主代码零改动：`RecoveryConnectionHandler`、`RecoveryAdmittedDataSource`、`RecoveryWriterLease` 当批未编辑（`RecoveryConnectionHandler.java` SHA256 `3d01281bbb865f192111bfecaa96f8d3bc79895ead51496aa415cd085e5a6d62`）。

## 当批验证

证据根 `outputs/r06-product-20260925-01a0d8fa/admission-concurrency-90/`，134 项 manifest SHA256 `00d2c1850caec294a0bb3c5c3817a5cc31cac5909c79602429a5788990df75c6`，已逐文件复算 `mismatches=0`；两树当批完整状态/差异见 `concurrency-final-main-status.txt`/`.diff`（338 条状态、5432 行差异）与 `concurrency-final-server-status.txt`/`.diff`（322 条状态、2692 行差异）。

- 定向：`run-focused.ps1` + `focused.log`/`.err.log`、`focused.exit.txt`。`--rerun-tasks` 实际执行 9 个任务，`RecoveryAdmittedDataSourceTest` tests=14/skipped=0/failures=0/errors=0，`RecoveryWriterLeaseTest` tests=6 全通过。定向前一次输出已被覆盖，仅本次 XML 计为当批结论。
- 全量：`run-full.ps1` + `full.log`/`.err.log`、`full.exit.txt`、`full-totals.txt`、`test-results/` 99 份 XML。`test bootJar --rerun-tasks` 11 个任务全部实际执行，**690 tests / 1 既有符号链接条件 skip / 0 failure / 0 error**，bootJar 成功。
  - 口径说明：attempt89 全量为 688。本批并非 +2：attempt89 那次 `test` 任务的 688 已包含 `RecoveryAdmittedDataSourceTest` 的 12 项，本批该 12 项内**替换**了 1 项（原 `concurrentCloseWaitsForRunningExecutionAndReleasesOnlyAfterwards` 改为走 Statement 代理的 `concurrentStatementExecuteIsAccountedForBeforeCloseReturns`）并新增 2 项，故净 +2 应记在 688 之前的基线上。当批唯一可复算事实是 **690/1skip/0fail**，不与历史 688 拼接。
- JAR：`acrylic-outbound-server-0.1.0.jar` SHA256 `428cfd68b149fa3621c14842c048f7479dd8b597bca8cbea16ce44674e74f05b`（`bootjar.sha256`），与 attempt89 的 `0aa56b3d…` 不同，属本批输入重新构建。
- 源码快照：`RecoveryAdmittedDataSourceTest.java.snapshot` SHA256 `ebd8903db9562aa18b7215cafb0ee8c82c708861cdc071e3cb4e2f4903798b93`（`source.sha256`）；两树 HEAD 见 `tree-heads.txt`。

## 自审发现与已保留的失败/限制

1. **去同步反证未成立，保留原始失败证据**。`run-counterproof.ps1` 在字节级备份后移除 `RecoveryConnectionHandler` 全部 `synchronized`（`RecoveryConnectionHandler.java.desynchronized`，SHA256 `e9f4cb371f65d10ab6926a2c01255d161cc19981fe748516df95ab21e3122972`），预期新增的“关闭早于执行返回”断言失败，实际 **`counterproof exit=0`、14 项全通过**（`counterproof-TEST-RecoveryAdmittedDataSourceTest.xml`）。原因是连接监视器无法从外部被抢占，主线程无法把关闭线程确定性地排到执行线程之前，该断言在去同步实现上**不构成可信反例**。备份已按字节还原并复核：`handler.original.sha256` 与 `handler.restored.sha256` 同为 `3d01281b…`，`restored-equals-original=True`。
2. 因此本批**只声明**“新增断言在当前实现下确定性通过”，**不声明**“已验证去同步实现会失败”。要得到可复现的反例，需要在接纳边界加可注入 seam（例如可替换的连接/语句代理工厂）或用真实 MySQL 连接复现，均未在本批实施。
3. 代码审查另发现一处未改的结构事实，记录备查：`RecoveryJdbcObjectHandler.invoke` 未加同步，`Statement`/`ResultSet`/`DatabaseMetaData` 的委托调用在其执行期间**不持有** `RecoveryConnectionHandler` 的监视器；跨进程安全由 `writers.lock` 文件锁保证，故本批未将其判为缺陷，但也未据此声称“同一连接调用已完全串行”。
4. 本批为纯 Java 单测与构建：未跑 MySQL/HTTP、未启动隔离实例、无 Android 变更、未跑 APK/lint。共享 3306 与 `19330/19434–19436` 未触碰。

## 适用范围与下一步

作者/自审均为当前单助手，无独立人员。`RecoveryAdmittedDataSource` 仍未注册 Spring/NORMAL，普通业务写与生产恢复能力保持关闭，`localDeletionAllowed=false`。

下一步仍按[全应用恢复门禁实施断点](../parallel/R09/APPLICATION_ADMISSION.md)：先做初次 NORMAL 登记的库外+DB 双侧证据，再在相同输入上接实际 `DataSource`、Spring Session 外层、Worker 与文件执行入口，并用独立非空 MySQL 与真实 HTTP 验证正常请求、CSRF 会话保存、关闭竞争、未知提交后排空。总目标未完成。
