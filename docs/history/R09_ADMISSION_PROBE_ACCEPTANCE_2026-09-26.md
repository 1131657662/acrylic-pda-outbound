# R09 连接适配器并发 seam 与可信反证验收

2026-09-26。主树 `codex/development@dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树 `codex/r06-e2a-migration@568683c1a1f8257ad0596a654c6b3558c767ed95`，加当批未提交差异。没有 commit/push/main/发布/部署。

## 背景与本批目标

上一批（[并发补强验收](R09_ADAPTER_CONCURRENCY_ACCEPTANCE_2026-09-26.md)）新增的“关闭不得早于执行落账”断言在**去同步实现上仍然通过**，因为连接监视器无法从外部抢占，无法把两个线程确定性地排到同一竞争点上：该断言当时不构成可信反例。

本批目标：在接纳边界加最小可注入 seam，把该断言变成**在去同步实现上必定失败**的可信反例，再进入 NORMAL。属[全应用恢复门禁实施断点](../parallel/R09/APPLICATION_ADMISSION.md)第 34 行“并发执行与 close”的补强。

## 实现（3 个生产文件 + 1 个测试文件）

- 新增 `RecoveryAdmissionProbe`（包内可见接口）：`NOOP` 默认空实现 + `aroundDelegateCall()`。生产路径恒为 `NOOP`，不接 Spring、不注册 bean、不携带任何能力；唯一运行时影响是序列化区段内一次空调用。
- `RecoveryConnectionHandler`：新增三参构造（默认 `NOOP`），并在 `assertUsable()` 之后、委托调用之前调用 `probe.aroundDelegateCall()`。该点位于 `synchronized invoke` 持有的监视器**内部**。
- `RecoveryAdmittedDataSource`：新增包内 `admit(RecoveryAdmissionProbe)`，其余与 `getConnection()` 完全同路径；`getConnection()` 与 `getConnection(user,password)` 行为不变。
- `RecoveryAdmittedDataSourceTest`：新增 `probeForcedOverlapKeepsAConcurrentCloseOutOfTheInFlightDelegateCall`（14→15 项）。

## 判别设计（为什么这次是可信反例）

执行线程进入 `synchronized invoke` 持有监视器，在 probe 处闩住；关闭线程随后在同一连接代理上调用 `close()`。断言链：

1. 关闭线程发起 `close()` 后，`closeFinished` 在 **2 秒**内不得归零——即**正确的串行边界必须让这次 close 等在执行之后**；
2. 释放执行线程后，执行线程自己的委托调用（`setReadOnly(true)`）必须成功，`writerOutcome` 为 `null`；
3. `admitted.isClosed()` 为真、执行线程已结束。

第 1、2 条都在 probe（监视器内部）存在时才有意义；去同步实现下 close 会直接进入并把连接毒化，执行线程随后必然拿到 `CONTROL_CONNECTION_CLOSED`，第 2 条失败。该反例不依赖调度顺序。

## 当批验证

证据根 `outputs/r06-product-20260925-01a0d8fa/admission-probe-91/`，129 项 manifest SHA256 `4c331a283d1bf8c8a11a6d4c5b142e8efad11b5d50f1c6ab291601b0861816e7`，逐文件复算 `mismatches=0`。

- 定向：`focused.log`/`focused.exit.txt`、`focused-TEST-*.xml`。`RecoveryAdmittedDataSourceTest` **tests=15 / skipped=0 / failures=0 / errors=0**；`RecoveryWriterLeaseTest` 6/6。`gradle exit=0`。
- **可信反证（本批关键结论）**：`run-counterproof.ps1` 仅把 `RecoveryConnectionHandler.invoke` 的 `synchronized` 去掉（其余同步点保留），得到去同步变体 SHA256 `84c994e63423c0505b8ccdc436973f8c7d68c3934aa1a5747aa9abcdf0e51ea1`，随后
  - `counterproof.exit.txt`：**`gradle exit=1`（预期非零）**；
  - `counterproof-TEST-RecoveryAdmittedDataSourceTest.xml`：tests=15 / **failures=1**，失败用例正是 `probeForcedOverlapKeepsAConcurrentCloseOutOfTheInFlightDelegateCall`，`org.opentest4j.AssertionFailedError`（第 2 条断言）；
  - 备份按字节还原并复核：`handler.original.sha256` 与 `handler.restored.sha256` 同为 `e6a6ced32078c4abe6bc8d22bf376cf2e5d51ea09e7245f3d168c9f113896f57`，`restored-equals-original=True`。
  - 结论：该断言**已在去同步实现上实测失败**，构成可信反例；上一批“未成立”的缺口在本批关闭。
- 全量：`full.log`/`full.exit.txt`、`full-totals.txt`、`test-results/` 99 份 XML。`test bootJar --rerun-tasks` 11 任务全部实际执行，**691 tests / 1 既有符号链接条件 skip / 0 failure / 0 error**，bootJar 成功。
  - 口径：上批 690，本批 +1（新增 probe 用例）。不把 688/690/691 拼接为同一输入。
- JAR：`acrylic-outbound-server-0.1.0.jar` SHA256 `1a831ffe8eaef2273fd23aaba61009e76bcb66aa3812a4e23203cd75072c0e6c`（`bootjar.sha256`）；与上批 `428cfd68…` 不同，属本批重建。
- 源码快照 `source.sha256`：`RecoveryAdmissionProbe.java b30356b7…`、`RecoveryConnectionHandler.java e6a6ced3…`、`RecoveryAdmittedDataSource.java 3814f5a4…`、`RecoveryAdmittedDataSourceTest.java aae856af…`；两树 HEAD 见 `tree-heads.txt`，两树当批状态/差异见 `probe-final-main|server-status.txt`/`.diff`（338/322 条状态）。

## 自审与保留的失败证据

1. 前两版判别设计均未成立，失败经过保留：第一版把判别放在关闭线程侧（去掉同步后仍通过）；第二版改为让关闭线程在监视器内评估标志，仍通过；期间一次 30 秒 `TimeoutException` 暴露“关闭线程在监视器内等待执行结束”会死锁，这本身即串行语义的证据。编译期另有 2 个错误（缺 `ExecutorService` 导入、字段改名未同步），原始 `focused.err.log` 保留。最终版把判别移到执行线程侧并加 2 秒窗口，才得到可信反例。
2. seam 只加包内可见接口与一个空调用，未改动任何既有行为路径；`getConnection()` 两入口仍走 `NOOP`。全量 691 未出现新增失败，作为该结论的当前输入证据。
3. 本批为纯 Java 单测与构建：未跑 MySQL/HTTP、未启动隔离实例、无 Android 变更、未跑 APK/lint。共享 3306 与 `19330/19434–19436` 未触碰。

## 适用范围与下一步

作者/自审均为当前单助手，无独立人员。`RecoveryAdmittedDataSource` 仍未注册 Spring/NORMAL，普通业务写与生产恢复能力保持关闭，`localDeletionAllowed=false`。

连接层“串行执行与 close”一项至此完成实现、自审、可信反证与全量回归。下一步按[全应用恢复门禁实施断点](../parallel/R09/APPLICATION_ADMISSION.md)：先建立初次 NORMAL 登记的库外+DB 双侧证据，再在相同输入上接实际 `DataSource`、Spring Session 外层、Worker 与文件执行入口，并用独立非空 MySQL 与真实 HTTP 验证正常请求、CSRF 会话保存、关闭竞争与未知提交后排空。总目标未完成。
