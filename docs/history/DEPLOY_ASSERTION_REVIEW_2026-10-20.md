# 部署预检四项断言语义审查

日期：2026-10-20。原失败输入与日志见部署预检报告；本轮从文档备份提交64eaf491接续。以下仅审查与修复分类，正式结果绑定后续同一冻结候选，不拼接旧通过。

| 原失败用例 | 裁定 | 业务依据与修复 |
| --- | --- | --- |
| RecoveryLeasePublicationTest.anOrphanLeaseRecordIsRetiredByTheScanThatRefusesIt | 测试输入错误 | 正常退出有活跃操作时必须保留owner锁；原输入未制造owner死亡。改用真实文件/OS锁发布后释放的死亡窗口夹具；仍断言下一扫描拒绝、清孤儿及索引、保留process证据。 |
| RecoveryLeasePublicationTest.theTreeIsCleanForTheNextProcessAfterRetirement | 测试输入错误 | 同上；保留清理后本JVM共享围栏不可重置的拒绝断言，未把正常退出改成强制死亡。 |
| RecoveryLeaseVisibilityTest.aDeadOwnersRecordIsCountedAsOrphanWithoutBeingRetired | 测试输入错误 | 同上；保留只读计数、逐文件列表守恒和下一authority拒绝。另补退出等待期间owner仍活跃、禁止新操作、最后操作完成才退休的回归。 |
| SourceEvidenceIntakeAuditPrefixTest.conflictingFormalSubmissionDigestFailsClosed | 测试mock边界错误 | 产品流式RowCallbackHandler查询不消费旧queryForList桩。复用开发会话已提交dec98b2d的修复，逐项复核四查询冲突拒绝、相同摘要幂等、后行冲突及JDBC失败；未改产品SQL或错误码。 |

## 依据与边界

RecoveryFenceAuthority.retire/retireOperation、RecoveryLeaseRetirementTest.lifecycleRetirementDefersUntilTheLastOperationIsRetired及恢复门既有报告说明正常退出必须等待已准入操作，提前释放会把仍执行操作伪装为孤儿。RecoveryLeaseScan.check仍清理真正死亡owner的孤儿并拒绝本次启动；counts只观察。此次四项均归测试修正，产品语义不变，不混记实现修复。

RecoveryOrphanFixture仅在临时目录用产品原语持有再释放真实生命周期锁，明确为单位测试死亡窗口模型；不冒充跨JVM进程死亡、数据库、HTTP或部署验收。初轮失败日志/XML保留。结果待本轮冻结候选统一重跑。

## 探索性验证（非冻结候选）

本轮修改后的六类定向结果38项、0失败/错误/跳过，Gradle退出0；命令、日志及逐XML保存于.local/deploy-remediation-20261020/assertions-exploratory01*。此时工作树尚未冻结，不作为候选预检通过；后续同一SHA重新运行完整test及部署检查。原恢复实现未改。

## 固定候选结果

8cb51ded3ef2cd6cd45eaca09d8947ce068e89cd完整普通server4776项零失败/错误、19跳过；本审查涉及六类共38项均实际通过且无skip，见固定候选final-server-xml。十九条件跳过不计通过，所有生产恢复/审计实现未改。后续main开发会话子JVM方案不是本候选输入，明确区分不拼接。
