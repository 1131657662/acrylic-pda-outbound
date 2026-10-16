# D13 HTTP/Session 恢复入口 L1 开发备份

## 范围与输入

本单元只验证真实 Spring 容器、Spring Session JDBC 和恢复请求入口的有界组合，补 D13 所列 HTTP/Session 方向证据。它不证明 worker、文件、导出、跨进程生产恢复、D13 整体、E06/E07 L2 或 D18 L3。固定树为 `D:/acrylic-pda-outbound`、分支为 `codex/development`；执行前快照 `.local/goal-20261017-resume/input48.json`，SHA-256 `b369160f2d9349596b7dab8a9aa1b2433585106ad1173c3969fbda3b44d9e374`。

| 路径 | 影响层与必跑 | 输入绑定 |
| --- | --- | --- |
| `server/src/test/java/com/acrylic/outbound/recovery/RecoveryRequestRootHttpIT.java` | 真实 HTTP 请求、Session JDBC 保存、ERROR/ASYNC dispatch、multipart、并发、fenced/stale 拒绝 | SHA `4ef4bb434f5e804c9d27981cb06bda77e51122ec5e58fe53fd02a067c99549df`，本单元未改测试源码 |
| `server/src/test/java/com/acrylic/outbound/recovery/EffectRealEnvironment.java` | loopback、隔离端口、datadir、schema/account 和 evidence guard | SHA `1d12905f0c0e92050320fa78fe48e2a4cc8bdaf0acf97151af51397c7b3c4877`，本单元未改 |
| `tools/r09-recovery-it-batch.ps1` | 新隔离 MySQL、Gradle 单类执行、停机和证据收集；新增显式 `-InstanceRoot`，让测试工作目录与 datadir 根一致 | SHA `b60412c0eb7bd3d0c4e7edd02284eb5157408dded4de73e9d59f9af4c42ec9d3`；仅 harness 变化 |

## 失败指纹与修复

首次调用 `d13-s3session01` 在 runner 的“evidence root 必须不存在”守卫处停止，未启动数据库；该目录及控制台输出保留。`d13-s3session02` 使用历史 pinned 19442 实例，2 项均在 `EffectRealEnvironment` evidence guard 失败：Gradle 测试工作目录为 `server`，但证据位于主树 `.local`，guard 要求 `server/.local`。`d13-s3session03` 改用新 19443 实例和 `server/.local` 证据后，继续暴露 datadir 仍由 runner 建在主树 `.local` 的同一身份错误；两次均无业务断言写入。上述 XML、日志、实例停机记录和退出码均保留，不清零失败计数。

按失败事实加入 `-InstanceRoot` 参数；不改产品或测试源码，不复用 19442/19443 数据库。新的 19444 实例位于 `server/.local/mysql19444-r09batch`，证据位于 `server/.local/d13-s3session04`，执行前确认 19444/19458 无监听。

## 实际结果

19444 新实例使用 schema/account `r09_batch_20261017`，真实 root 连接后创建专用账号；应用/测试结束执行实例 shutdown，`db-shutdown.txt` 记录 exit 0、19444 监听为 0。`RecoveryRequestRootHttpIT` 共 2 tests、0 failures、0 errors、0 skipped，Gradle exit 0，耗时 57 秒。两项覆盖：

- `everyRealDispatchOfARequestIsCoveredByOneRootAndTheSessionSaveIsInsideIt`：真实 REQUEST/ERROR/ASYNC dispatch、Session 行和属性保存、4096B multipart、413 超限、malformed multipart、四个并发请求、fenced/stale refusal、暂存目录排空、live root 排空及 session cleanup 线程守卫。
- `anAdmittedInstanceRefusesToStartWhileTheSessionWriterIsEnabled`：未关闭 Session cleanup 时真实启动拒绝 `CONTROL_SESSION_CLEANUP_UNCOVERED`，且端口未被占用。

关键证据：

| 证据 | 字节 | SHA-256 |
| --- | ---: | --- |
| `server/.local/d13-s3session04/batch-summary.json` | 1083 | `94599e9c9598bda1ee5b8cf042651c6fa2a840251318bf149e83e7cfd9e68019` |
| `server/.local/d13-s3session04/run101-batch-recoveryrequestroothttpit/gradle.log` | 1270 | `8f9f2a293949c7aab0d8f18dfd8de0fbd39ccf7dc2946548fa7f45ebe1587033` |
| `.../real-records.tsv` | 5653 | `9580cdffe854b207b15827aa58bd80eea2b9c22ad18b6339ed9a9ed4d98674fd` |
| `.../s3-session-root.txt` | 4478 | `78d9940a5072a3fbea9d938130f43ed117e70ab904d53d0ab32a964479d64405` |
| `.../s5-cleanup-guard.txt` | 96 | `90278c4637ba664f497c275fffe7e2301b9ae1dba2f4aef098cee6fc8a95c32b` |
| `.../TEST-...RecoveryRequestRootHttpIT.xml` | 119918 | `80b5f921a4a318f14b88b8d0e4e59b33bc9cbc3345f7696919d24ea32a1637fd` |
| `server/.local/d13-s3session04/manifest.json` | 3056 | `115b8147433943618bef43bdd32bb2c387d345ac43f127a7c2cd7b0b0c409b9e` |
| `server/.local/d13-s3session04/postflight.json` | 5433 | `a8ee5c38f51782f403c6a3474ec519f508e97c1d1a2a14f263d032dd8b19b90b` |

`postflight.json` 记录 19444/19458 零监听、实例 datadir 仍在、进程和 C/D 余量；失败运行的原件和新实例的 datadir 均不清理。合成隔离身份不代表原 PDA 身份、现场回执或生产权限。

## 未覆盖

本单元没有执行 worker、文件消费者、renderer/export、Android、Chrome、Python、非空迁移、原模拟器或真实现场链；D13 要求的跨入口（HTTP/Session/worker/文件/导出）组合仍未满足。生产默认关闭、`localDeletionAllowed=false`、原库/现网、发布和部署边界不变。
