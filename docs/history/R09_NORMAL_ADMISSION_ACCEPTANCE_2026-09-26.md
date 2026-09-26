# R09 初次 NORMAL 登记验收

2026-09-26。主树 `codex/development@dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；服务端树 `codex/r06-e2a-migration@568683c1a1f8257ad0596a654c6b3558c767ed95`，加当批未提交差异。没有 commit/push/main/发布/部署。

## 完成标准

来自[全应用恢复门禁实施断点](../parallel/R09/APPLICATION_ADMISSION.md)第 23 行第 2 项：建立明确的初次 NORMAL 登记协议及 DB 对应证据；初次初始化不得沿用灾后 `RELEASE_READY` 的释放路径；缺两侧一致证明不得放行；不重新生成或猜测原安装、原数字/UUID 映射、原请求与审批边界；生产释放端点仍不存在。

## 实现

- `V40__recovery_normal_admission.sql`（新增）：`recovery_normal_admission` 表（format_version/installation/revision/原 hash/前版 hash/意图 hash/numeric/operator/registered_at）+ UPDATE、DELETE 触发器；迁移不插行，不回填。
- `RecoveryNormalIntent.java`（新增）：可选耐久外部意图 `normal-admission.json`。严格字段集合、格式版本、安装一致、正值 revision/baseline、64 位 hash、`intentSha256` 自哈希；`requireBound(intent, control)` 要求声明的 revision/原 hash/前版 hash 与**关闭链头**完全一致。
- `RecoveryNormalRows.java`（新增）：登记行 SQL（命名参数绑定，显式 BINARY 类型）与 `enableNormal()`。
- `RecoveryNormalRegistration.java`（新增）：唯一可把 `server_state` 置回 NORMAL 的组件。要求：库外固定实例 + 数据库绑定（安装/UUID/schema/baseline）一致；`RecoveryProjectionHistory` 全链校验且末版为 `RELEASE_READY`；`numeric` 与 `server_state` 一致；有外部意图时逐项交叉核对（revision/原 hash/前版 hash/baseline/schema）；无外部意图时允许放行但登记行记 `UNRECORDED_EXTERNAL_INTENT`（不把操作者身份当证据）。有行时幂等返回 `ALREADY_REGISTERED`，意图 hash 不同则 `CONTROL_NORMAL_INTENT_CONFLICT`。全部在 `RecoveryLeasedTransaction` 内（外部写者租约 + 库锁），`ordinaryWritesAllowed=false`——**登记不等于开放业务写**。
- `RecoveryControlHistory.layout()`：白名单放行该意图文件，内容仍单独校验，不据存在性推断状态。
- 测试接入：`RecoveryProjectionChecks.verify(..., afterReleaseReady)` 回调（在投影到 `RELEASE_READY` 之后、故障子例之前运行，并在回调后恢复 `service_mode`）；`E2aClosedProjectionIT` 迁到 V40 并调用 `RecoveryNormalChecks`；`RecoveryNormalIntentTest` 9 项。

## 当批验证

- 定向：`RecoveryNormalIntentTest` 9/9；`com.acrylic.outbound.recovery.*` 75/75。
- 全量：`test bootJar --rerun-tasks` **700 tests / 1 既有符号链接条件 skip / 0 failure / 0 error**（V40 后连续多次一致）。
- **隔离 attempt90 通过（最终输入）**：独立非空 `V34→V40`、专用 datadir/schema/account、19330 端口；`test bootJar` 后 `integrationTest` XML `tests=1 skipped=0 failures=0 errors=0`；JVM 退出后该账户连接与 `INNODB_TRX` 均为 0；认证 shutdown 完成。证据 `outputs/r06-product-20260925-01a0d8fa/admission-normal-90m/`（19 项 manifest SHA256 `75f95cf2e8343100686210bf949d41652571e265151d1c6874e493133cda9eed`，逐文件复算 0 不符；JAR SHA256 `8dc1add85d5b0dfa5022ea75f9de8c2f004e8941bd5a4b1f2aebf8d71170be58`），其中 `normal-admission-checks.txt` 记录：
  - 无外部意图时可从 `RELEASE_READY` 放行，且登记行 `operator_id=UNRECORDED_EXTERNAL_INTENT`；
  - 有耐久意图时必须绑定同一链头，放行后 `outcome=REGISTERED`、`ordinaryWritesAllowed=false`、`recovery_normal_admission` 恰好 1 行、投影仍 4 行；
  - 重放返回 `ALREADY_REGISTERED` 且不新增行；
  - `expectedDatabaseSchema` 不符 → `CONTROL_NORMAL_INTENT_SCHEMA_MISMATCH`；伪造 baseline → `CONTROL_NORMAL_INTENT_BASELINE_MISMATCH`；篡改原 hash → `CONTROL_NORMAL_INTENT_CONTROL_MISMATCH`；换意图重放 → `CONTROL_NORMAL_INTENT_CONFLICT`；缺意图时保持 `RECOVERY_PENDING`；
  - 全部拒绝路径均断言 `service_mode` 不为 NORMAL 且 `recovery_normal_admission` 为 0 行（无半条记录）；
  - `recovery_normal_admission` 与 `recovery_control_projection` 的 UPDATE/DELETE 均被触发器拒绝。
- `normal-without-intent.json`、`normal-admission.json` 为当批原始回执副本。

## 追加：运行时只读门禁（本批新增，真实验证未完成）

在同一批继续实现"库外+DB 双侧一致"的**只读运行时检查**，作为接 API/Session/Worker/文件入口的前置：

- `RecoveryAdmissionEvidence`（接口）：只读视图（binding/admission/projection/state），实现不得初始化或授权。
- `RecoveryAdmissionQueries`：上述视图的只读 SQL 读取（无任何写语句）。
- `RecoveryAdmissionVerifier`：逐项比对库外固定快照、二次读取的耐久意图、库内冻结登记与投影、`server_state`；任一侧缺失或矛盾即返回 `admitted=false` 并列出 gap（含 `AUTHORITY_UNAVAILABLE`、`DURABLE_INTENT_MISSING/HASH/REVISION`、`DATABASE_BINDING_MISSING`、`NORMAL_ADMISSION_MISSING`、`PROJECTION_*`、`ADMISSION_FROZEN/AUTHORITY/INTENT`、`SERVER_MODE/NUMERIC`）。**不写库、不登记、不初始化 NORMAL**，`ordinaryWritesAllowed=false`。
- V40 增列 `authority_revision`：登记时冻结当时权威 revision，使"库外回退到更早链头"可被识破（`RecoveryNormalRows.admit`/`existing` 与 `RecoveryNormalRegistration` 已同步）。该迁移尚未提交，故直接补列而非新增 V41。
- 定向：`RecoveryAdmissionVerifierTest` 8 项（双侧一致放行但不开放写、库回退、库外回退、缺意图/篡改 hash、未登记/换实例、非终态/模式不符、意图或 numeric 不符、权威不可读）；全量 `test bootJar --rerun-tasks` **708 tests / 1 既有 skip / 0 failure / 0 error**，JAR `50a6859c8c0912c3294536adc1aeba0a1d031a98a14820e938586b88dc68dec0`。

**未完成**：V40 加列后的真实 MySQL 复验没有拿到绿色运行：

- attempt97 第一次：Java 侧全部通过（NORMAL 检查已执行完），随后 `E2aTemporaryExportBrowser.verify` 在登录页 45s 超时（该用例在全部历史运行中均为 ~4s，属环境抖动）。
- attempt97 第二次：更早中断于 `TemporaryExportFixture.generate` —— 导出作业停在 `RETRY_SCHEDULED` 而非 `SUCCEEDED`。
- 两次都未生成 `normal-admission-checks.txt`（在 `existingHttp` 之后才执行），也未写 `test-completed.marker`。这两处抖动属既有导出/浏览器组合的稳定性问题，与本批加列无因果关系推断；但**新 schema 的登记路径因此尚未完成真实库复验**。
- 既有绿色基线仍为 attempt90m（V40 无 `authority_revision` 列时）。

## 自审发现并修复的缺陷（均已复验）

1. `RecoveryNormalRows.existing()` 列名写错（`record_sha256` → `control_record_sha256`），真实库直接报 Unknown column。
2. `admit()` 首个 `byte[]` 实参被 varargs 当数组展开（"No value specified for parameter 7"）；改为命名参数 + 显式 `Types.BINARY`。
3. 幂等返回把 `operator` 放进了 `installationId` 字段；改为取控制快照的安装 ID。
4. 与本子项无关但阻塞验收的既有夹具竞态：`TemporaryExportChecks.formalAndPc` 用 `Instant.now()` 当快照截点，随后又校验"刚生成的回执不得晚于截点"，必然失败；改为 `plusSeconds(2)`。修前 attempt92/93/94 全部死在该处。
5. 既有故障子例 `RecoveryProjectionFaultChecks.overflow` 会把 `server_state.recovery_epoch` 留在 `Long.MAX_VALUE`；本批不改该子例，改由 `RecoveryNormalChecks.restoreEpoch` 在回调后恢复，使后续比对使用投影身份。
6. `ExportRendererProcessTest` 一次 `NumberFormatException: For input string: ""`，复跑通过，按不稳定记录保留。

## 失败证据（保留，未删除）

`admission-attempt92|93|94|95|96|97|98|99|90-failed*`、工作树 `.local/e2a-cross-20260925-<n>-failed*`；attempt100 因运行号三位被 fixture 正则 `[1-9][0-9]?` 拒绝（脚本层失败）。这些失败对应上面的缺陷定位过程，不作为通过结论。

## 适用范围与下一步

作者/自审均为当前单助手，无独立人员。`RecoveryNormalRegistration` 未注册 Spring bean、无路由、不随启动运行；`ordinaryWritesAllowed=false`，普通业务写与生产恢复能力仍未开放；`localDeletionAllowed=false`。

下一步按[全应用恢复门禁实施断点](../parallel/R09/APPLICATION_ADMISSION.md)：在相同输入上接实际 `DataSource`、Spring Session 外层、Worker 与文件执行入口，并用独立非空 MySQL 与真实 HTTP 验证正常请求、CSRF 会话保存、关闭竞争与未知提交后排空。总目标未完成。
