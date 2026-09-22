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
