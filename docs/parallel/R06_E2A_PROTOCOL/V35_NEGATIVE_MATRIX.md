# V35 结构负向矩阵

`E2aV35NegativeChecks` 依赖 V34 父关系和 synthetic seed：从 `submission`、`submission_scope`、`source_evidence_object`、`source_evidence_set`、`source_evidence_verification` 读取一组完整 TEMP 上下文，并要求数据库另有第二个 TEMP scope 和第二个既有 `app_user`。它不创建用户、scope、签名、HTTP 回执或可信业务事实；缺少这些准备数据时明确失败。

`verify(evidenceRoot)` 共执行 4 个合法 control（OPEN、PAGE、STOP、VERIFY）和 24 个负向案例：

`status-check`、`version-check`、`request-digest-check`、`response-size-check`、`request-empty`、`request-too-large`、`response-too-large`、`response-digest-format`、`unknown-operation`、`principal-mismatch`、`open-reference-shape`、`page-reference-shape`、`verify-reference-shape`、`verify-set-only`、`verify-verification-only`、`stop-missing-object`、`stop-extra-references`、`scope-composite-fk`、`cross-scope-parent-fk`、`submission-round-fk`、`natural-key`、`natural-key-different-bytes`、`immutable-update`、`immutable-delete`。

每案使用独立 Spring JDBC 事务并标记 rollback；事务内或事务调用外的失败都会先写唯一 `CREATE_NEW` JSON。每个 control/negative 都用 `E2aSnapshotRows` 做全表 before/after 逐值相等检查，最终另写总报告并核对 intake 行数不变。自然键两案预期 `23000/1062`，scope/submission 外键预期 `23000/1452`，immutable trigger 预期 `45000/1644`，其余 DDL CHECK 预期 `HY000/3819`。

`cross-scope-parent-fk` 当前只把 intake 行的 `scope_id` 替换为第二个 TEMP scope，验证父关系外键失败；它不声称覆盖 object/set/verification 的跨 scope 业务组合。`response-digest-format` 只验证摘要字段格式；摘要内容与字节是否一致属于 writer 层，crypto 一致性未验。本文和 helper 截至当前提交均未运行编译、测试或真实数据库动态执行。
