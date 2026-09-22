# W2 E2a Migration Snapshot Review

审查对象：W2 `codex/r06-e2a-migration` 当前未跟踪 `E2aMigrationSnapshot.java`、`E2aMigrationExecutionIT.java` 及 `E2aMigrationEnvironment.java` 工作区修改；截至本次审查尚无稳定提交 SHA。

## 当前可确认的护栏

- `E2aMigrationEnvironment.requireExact` 校验固定工作树、19329 URL/schema/account、datadir/storage/signing 路径和禁止 reset/repair/reuse 环境变量。
- `connect`/`verifyConnected` 会在 SQL 前后核对 datadir、database、port 和 `CURRENT_USER()`。
- 默认测试不会执行 `E2aMigrationExecutionIT`，且 IT 还要求 `E2A_MIGRATION_RUN=true`。

这些是环境护栏结论，不是迁移或负向验收结果。

## 阻断性缺口

1. `E2aMigrationSnapshot.save` 只保存 `information_schema.TABLES` 的表名/估计行数、Flyway history 和 storage 下文件 bytes/SHA。没有保存旧业务表按主键的完整字段值、NULL 语义、BLOB/原始 response bytes、字段级 SHA、外部 private-file 引用及其关联哈希，因此无法证明 V34→V35 保留原值。
2. `seedNonEmptyV34` 只创建并插入 `e2a_v34_fixture_marker`。它没有 TEMP S/round、mapping version 0/null、mapped source、ABORT、command receipt、SOURCE_RESERVED 或引用文件，不能满足 S1 非空基线。
3. `runNegativeShapeChecks` 只检查 marker 行数为 1，没有执行任何 partial reference、scope/submission/round FK、duplicate natural key、immutable UPDATE/DELETE、route/status/digest/size 或文件缺失负向，也没有检查事务回滚后的行数和错误类别。
4. 执行测试在 target 35 后仅保存快照，没有逐表/逐文件 before/after diff；即使快照文件存在，也不能由当前代码得出“全部旧字段、NULL、bytes、files hash 未变”。
5. 当前 `E2aMigrationExecutionIT` 的 `seedTarget34MigrateTarget35AndCapturePreservation` 仍将动态阶段描述为 pending domain seed；不得把其编译、护栏或 marker 保留误记为迁移通过。

结论：W2 当前材料可作为环境/执行入口准备，尚不能作为 S1/S2/S4 迁移证据。待 W2 固定 SHA 后，W3 只读复审应重新核对上述五项是否由实际代码和输出覆盖；在此之前不运行 Gradle、启库或连接 19329。

## 固定 SHA 独立复审：3a4b9b842fdfd118e6a2d64dea5bdb95b571d31d

复审范围：仅使用该提交的 Git 内容，未启动 19329、未连接数据库、未运行 Gradle 或动态迁移。

1. `E2aMigrationRunner.java:10-12` 在通过护栏后固定抛出 “dynamic execution is not scheduled”，且仓库中没有调用 `E2aMysqlLauncher` 的入口。`E2aMigrationExecutionIT.java:18-31` 直接连接数据库并执行 Flyway，没有启动/初始化 MySQL、创建目标 schema/账号或正常停机链，因此当前空环境不可执行。
2. `E2aMysqlLauncher.java:15-25` 的 `start` 只执行 `--initialize-insecure`，该进程完成初始化后退出；`startServer` 又要求 `ibdata1` 不存在却未完成初始化串联。`shutdown` 通过共享密码调用固定端口，未绑定或核验目标进程 PID，不能证明关闭的是本次实例。
3. `E2aV34Seed.java:17-30` 先查询空 target-34 库中既有 `submission_scope`/`submission`，没有构造 TEMP S/round、mapping version 0/null、mapped source、ABORT、command receipt、`SOURCE_RESERVED` 等 S1 事实；空库会直接查询不到行而失败。
4. `E2aMigrationExecutionIT.java:20-21` 在连接和后续迁移前创建一次性 marker；任一步失败都会遗留 marker，且 marker 未记录提交 SHA、迁移版本、schema、端口、seed/snapshot 摘要或失败阶段。该保护必须保留，但当前缺少可审计的失败/续接证据。
5. `E2aV35NegativeChecks.java:14-26` 只有三类 UPDATE/DELETE/stage mismatch，未覆盖本轮 SQL 可验证的 operation reference shape、复合 FK、重复 natural key、route/status/digest/size 约束；未知 key、页/manifest 语义和文件耐久属于后续 writer/HTTP/持久化边界，不能由本轮 DDL 静态结果替代。SQLState 只从最深层直接 `SQLException` 读取，包装异常时可能为空。
6. `E2aMigrationSnapshot.java` 的逐值快照使用 JDBC `getBytes` 后 Base64，能够保留 NULL 和二进制字段，但 `flywayHistory` 依赖 JDBC 返回值的 Jackson 类型。`row.path("version").asText()` 对非文本数值通常可用，`row.path("success").asBoolean()` 对 JDBC 字符串 `"1"` 不保证按预期得到 true；应在 W2 修正后确认编码或显式兼容。Flyway 历史应按结构化字段比较，不能退回字符串包含匹配。
7. W2 当前快照 helper 在该固定 SHA 上还存在与其正在修订的 `E2aMigrationSnapshot` 调用签名适配风险；本树不复制该修复，避免与 W2 写权冲突。新 SHA 必须重新检查编译签名、逐值 legacy projection、Flyway success 判定和 marker 输出。

本固定 SHA 结论仅是独立静态复审；不得记作编译、数据库、迁移、负向、停机或组合通过。

## Snapshot helper follow-up

在本树 `d114785` 的 `E2aSnapshotFiles` 中补充了递归路径的真实路径校验：storage 根和每个 regular file 均经 `toRealPath()` 检查仍位于工作树/存储根内，防止子路径 junction/reparse point 通过逻辑 `normalize()` 逃逸。该 helper 修复不改变 W2 业务入口或迁移行为；Flyway history 的 JDBC/Jackson 类型兼容与 `E2aMigrationSnapshot` 调用签名仍由 W2 新 SHA 负责确认。
