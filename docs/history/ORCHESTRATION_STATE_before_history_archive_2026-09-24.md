# 2026-09-24 历史签名归档/命令兼容前看板原文

不是当前状态。

<!-- CURRENT_STATE_START -->
## 当前有效状态（唯一接续入口）
### 2026-09-24 R06/E2a：历史公钥与可信逐包保守 K 写者已作者自审，第六步未完成

- 目标：完成 R01–R09 所有不依赖人工辅助/长时间测试的开发；逐子项自审修复。当前沿 R06，不提前把 R07–R09 判作完成。
- 写权：当前助手单写 `D:/acrylic-pda-worktrees/r06-e2a-migration` 的 E2a 业务及 `D:/acrylic-pda-outbound` 管理/历史；其它树未写。品牌任务已 idle；R07/R08/R09 旧任务未自动恢复。不触原库、现网、设备、正式部署/审批。
- Git：业务树 `codex/r06-e2a-migration` @ `568683c1a1f8257ad0596a654c6b3558c767ed95`，主树 `codex/development` @ `dccb73b10acbb63020beb3cf827b3b4580d3ed1c`；均有原有及本轮未提交改动，路径快照见 `docs/history/ORCHESTRATION_STATE_before_k_writer_2026-09-24.md` 末尾，后续以两树 `git status --short` 核实。未提交、推送、合并、打标签或部署；原 Android/server/管理脏改及 `docs/out.xlsx` 未覆盖/删除。
- 作者/代码：STOP 只保存独立对象与 `PARTIAL` 原回执，不因后续 set 事实失败回滚；VERIFY 事务内才在 PAGE/STOP 闭合、原字节、来源 B/K 事实核验后建立 set/item。set writer 三反例测试已补；原键 GET 保持本人原机 `pda:recover`、原请求/回执、来源与当前效力分离。服务器/原 TEMP 回执可用当前 RSA 或显式配置的历史 DER 公钥按 kid 验签；未知 kid 仍 `UNVERIFIABLE_KEY`，原字节直出409，能力全 false。没有历史公钥私钥或自动信任入口。
- 作者/K 写者：VERIFY 的同一事务在 set/item 原字节、提交 payload、stored capture、当前 B/K 仓与周期及所属来源核准后，逐活动包按 bale ID 顺序锁 K；无 K 才插入 `SOURCE_RESERVED` 并标记条码同步，精确同源 `REVIEW_RESERVED` 不改，同 set 的重复验证不重插，他源/重包拒绝。旧 set 重验改为逐 item 同值比较；实测发现原 `manifest_bytes` 被当作16字节 UUID 读取的真实缺陷，已修复且补反例。VERIFY 仍只给 `PARTIAL`，不开放释放或任何业务决定。
- 作者/自审：先前新测试 Mockito 顺序断言失败和旧 set manifest 解析失败已修正并保留反例。历史 key 正常、轮换、未知、篡改、缺失配置及逐包 K 的无 K/同源/异源/重复/锁序/旧 set 变造聚焦通过。最新 `./gradlew -p server test --no-daemon` 74 XML、463 项、0 失败/0 错误、1 Windows symlink 跳过；编译通过，`git diff --check` 无空白错误。此前 OpenAPI 3.1/126 路径解析通过（此后未改 schema）。以上均为作者单元/静态/契约，不是隔离 MySQL 逐包 INSERT/事务、独立消费者、真实 HTTP/Android/APK/模拟器/真机组合验收。
- 历史隔离实库：attempt6 非空 V34→V35→V36 与合成签名归档/JDBC 1/1 是更早代码版本的独立局部证据，不覆盖本轮新增 GET/逐包改动；专用实例已鉴权停止，旧 key attempt2 binlog/SUPER 1419 失败保留。19330、19434/19435 无监听；共享 3306/PID 5448 未操作。旧私有文件/日志不清理，证据 `.local/e2a-v36-audit-attempt6/private/`。
- 失败/限制：原机 STOP 与捕获高水位/审计全量耐久、真实全链 PAGE→STOP→set→VERIFY 及新 K 写者隔离 MySQL/HTTP、Android/现场与长时仍未完成；当前 VERIFY 仅 `PARTIAL`。历史 keyring 代码具备但真实历史公钥清单未配置/部署，不能把合成轮换测试充当真实旧回执可信。STOP 文件可能在 DB 回滚后留孤儿，不等于受理。R05 整体/code20/Room17、R09 恢复与现场限制保持。七类生产决定 handler/POST/放行关闭，`localDeletionAllowed=false`；不合 `main`、不部署。
- 下一断点：审查 Android 原机停止、全设备序列与 Room 耐久接点，再实现 PAGE/STOP 本机冻结证明和原键续传；补隔离非空实库 K 写者/GET 实际 HTTP 全链，随后核 R07–R09 可开发项。每子项先自审修复再前进，启隔离库前重核重型槽，不以合成签名取代原身份。
- 被替换当前区原文：`docs/history/ORCHESTRATION_STATE_before_k_writer_2026-09-24.md`；更早快照见 `docs/history/README.md`。
<!-- CURRENT_STATE_END -->

## 管理树未提交路径
```text
## codex/development...origin/codex/development
 M AGENTS.md
 M android/app/src/main/java/com/acrylic/outbound/pda/PdaApplication.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/data/ClaimIntent.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/data/PdaDatabase.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/data/PreparationDao.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/data/PreparedOrder.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/network/PdaHttp.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/network/PdaSession.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/preparation/PreparationRepository.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/retention/SubmissionArchiveRepository.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/submission/SubmissionDao.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/sync/BackgroundSync.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncWorker.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/ui/MainActivity.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/ui/OrderCards.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/ui/PreparationViewModel.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/update/UpdateManifestVerifier.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/work/WorkDao.kt
 M android/app/src/main/res/layout/activity_main.xml
 M android/app/src/test/java/com/acrylic/outbound/pda/preparation/LocalActivationTest.kt
 M android/app/src/test/java/com/acrylic/outbound/pda/retention/AuditArchiveMigrationTest.kt
 M android/app/src/test/java/com/acrylic/outbound/pda/retention/RetentionEvidenceTest.kt
 M android/app/src/test/java/com/acrylic/outbound/pda/retention/SubmissionArchiveMigrationTest.kt
 M android/app/src/test/java/com/acrylic/outbound/pda/retention/SubmissionArchiveTest.kt
 M android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryMigrationTest.kt
 M android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporarySubmissionMigrationTest.kt
 M android/app/src/test/java/com/acrylic/outbound/pda/temporary/TemporaryWorkMigrationTest.kt
 M android/app/src/test/java/com/acrylic/outbound/pda/ui/OrderCardsTest.kt
 M android/app/src/test/java/com/acrylic/outbound/pda/update/UpdateManifestVerifierTest.kt
 M android/app/src/test/java/com/acrylic/outbound/pda/work/WorkMigrationTest.kt
 M contracts/openapi.yaml
 M docs/DEVELOPMENT_PROGRESS.md
 M docs/DEVELOPMENT_ROADMAP.md
 M docs/GIT_WORKFLOW.md
 M docs/ORCHESTRATION_STATUS.md
 M docs/REMAINING_DEVELOPMENT_PLAN.md
 M docs/design-system.md
 M docs/history/README.md
 M docs/web-page-template.md
 M server/src/main/java/com/acrylic/outbound/pda/AssignmentStore.java
 M server/src/main/java/com/acrylic/outbound/pda/PdaOrdersController.java
 M server/src/test/java/com/acrylic/outbound/inventory/PdaAssignmentIT.java
?? android/app/hs_err_pid1492.log
?? android/app/schemas/com.acrylic.outbound.pda.data.PdaDatabase/18.json
?? android/app/src/main/java/com/acrylic/outbound/pda/data/ClaimIntentHistory.kt
?? android/app/src/main/java/com/acrylic/outbound/pda/ui/PreparationUi.kt
?? android/app/src/test/java/com/acrylic/outbound/pda/data/
?? android/app/src/test/java/com/acrylic/outbound/pda/network/
?? assets/brand/xianxu-login-preview.png
?? assets/brand/xianxu-logo-preview-32.png
?? assets/brand/xianxu-logo-preview.png
?? docs/PDA_ORDER_FLOW_ISSUES_2026-09-23.md
?? docs/PDA_TEST_RELEASE_CODE22_2026-09-23.md
?? docs/history/ORCHESTRATION_STATE_before_archive_coverage_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_audit_endpoint_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_audit_prefix_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_direct_key_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_e2a_attempt6_2026-09-23.md
?? docs/history/ORCHESTRATION_STATE_before_e2a_attempt8_2026-09-23.md
?? docs/history/ORCHESTRATION_STATE_before_e2a_independent_review_2026-09-23.md
?? docs/history/ORCHESTRATION_STATE_before_e2a_intake_read_2026-09-23.md
?? docs/history/ORCHESTRATION_STATE_before_e2a_intake_recovery_2026-09-23.md
?? docs/history/ORCHESTRATION_STATE_before_e2a_item_stage_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_e2a_open_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_e2a_page_read_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_e2a_page_write_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_e2a_trigger_decision_2026-09-23.md
?? docs/history/ORCHESTRATION_STATE_before_e2a_trigger_preflight_2026-09-23.md
?? docs/history/ORCHESTRATION_STATE_before_k_writer_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_keyring_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_page_closure_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_paired_key_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_raw_closure_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_source_read_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_stop_compat_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_stop_key_writer_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_stop_parser_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_stop_persistence_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_stop_preflight_2026-09-24.md
?? docs/history/ORCHESTRATION_STATE_before_v36_dynamic_2026-09-24.md
?? docs/history/R06_E2A_WORKTREE_STATUS_2026-09-24.md
?? docs/out.xlsx
?? docs/superpowers/
```

## E2a 树未提交路径
```text
## codex/r06-e2a-migration...origin/codex/r06-e2a-migration
 M AGENTS.md
 M contracts/IMPLEMENTED_API.md
 M contracts/openapi.yaml
 M docs/parallel/R06_E2A/CONTRACT.md
 M docs/parallel/R06_E2A/INTERFACE_CARD.md
 M docs/parallel/R06_E2A_MIGRATION/ACCEPTANCE_STAGES.md
 M docs/parallel/R06_E2A_MIGRATION/README.md
 M server/src/main/java/com/acrylic/outbound/pda/OfflineSigner.java
 M server/src/main/java/com/acrylic/outbound/submissions/SubmissionArchiveProofs.java
 M server/src/main/java/com/acrylic/outbound/temporary/command/CommandSignature.java
 M server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceProof.java
 M server/src/main/java/com/acrylic/outbound/temporary/protocol/E2aProtocol.java
 M server/src/main/resources/application.yml
 M server/src/test/java/com/acrylic/outbound/e2averify/E2aMigrationEnvironment.java
 M server/src/test/java/com/acrylic/outbound/e2averify/E2aMigrationEnvironmentTest.java
 M server/src/test/java/com/acrylic/outbound/e2averify/E2aMigrationExecutionIT.java
 M server/src/test/java/com/acrylic/outbound/e2averify/E2aMigrationRunner.java
 M server/src/test/java/com/acrylic/outbound/e2averify/E2aMigrationSnapshot.java
 M server/src/test/java/com/acrylic/outbound/e2averify/E2aMysqlLauncher.java
 M server/src/test/java/com/acrylic/outbound/e2averify/E2aV34BaseFixture.java
 M server/src/test/java/com/acrylic/outbound/e2averify/E2aV34Seed.java
?? .codex-patch
?? contracts/pda-temporary-source-evidence-paths.yaml
?? docs/parallel/R06_E2A_MIGRATION/PROCESS_LIFECYCLE_DESIGN.md
?? docs/parallel/R06_E2A_MIGRATION/TRIGGER_DEPLOYMENT_DECISION.md
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeAuditArchives.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeAuditCoverage.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeAuditPrefix.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeBusinessFacts.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeCaptureCoverage.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeController.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeDeviceKeys.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeEvidenceLocks.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeFacts.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeFiles.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeIdentity.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeObjectFiles.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeOpen.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeOpenService.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePage.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePageClosure.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePageItemWriter.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePageItems.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePageObjects.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePageReceipts.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePageService.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeRawObjectService.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeRawObjects.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReadController.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReadIntegrity.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReadService.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReadStore.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReceiptCodec.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReceipts.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeRecovery.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReplay.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReservations.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeSetWriter.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeSourceRecord.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeStop.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeStopLookup.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeStopObjects.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeStopReceipts.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeStopService.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeTransactions.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeVerificationStore.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeVerify.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeVerifyPreflight.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeVerifyReceipts.java
?? server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeVerifyService.java
?? server/src/main/java/com/acrylic/outbound/temporary/protocol/E2aPageItem.java
?? server/src/main/resources/db/migration/V36__source_evidence_page_item_stage.sql
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aMysqlBootstrap.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aMysqlBootstrapTest.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aMysqlCleanup.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aMysqlCleanupTest.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aMysqlLauncherTest.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aMysqlProcessIdentity.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aMysqlProcessIdentityTest.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aMysqlTriggerPreflight.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aMysqlTriggerPreflightTest.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aV34EvidenceBindingTest.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aV36AuditArchiveIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aV36AuditPrefixIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aV36DeviceKeyIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aV36MigrationIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aV36PageWriterChecks.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aV36PairedKeyIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aV36StageChecks.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aV36StopWriterChecks.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aV36StopWriterIT.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/OfflineSignerHistoryTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeArchivePageTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeAuditArchivesSignatureTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeAuditArchivesTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeAuditCoverageTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeAuditPageTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeAuditPrefixTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeBoundTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeBusinessFactsLockOrderTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeBusinessFactsTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeCaptureCoverageTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeControllerTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeDeviceKeysTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeEvidenceLocksTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeFactsTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeFilesTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeObjectFilesTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeOpenLookupTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeOpenServiceTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeOpenTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePageClosureTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePageItemWriterTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePageItemsTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePageObjectsTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePageReceiptCodecTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePageReceiptsTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePageServiceTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakePageTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeRawObjectServiceTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeRawObjectsTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReadControllerTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReadServiceTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReadStoreTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReceiptCodecTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReceiptsTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeRecoveryTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReplayTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeReservationsTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeSetWriterTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeSourceRecordTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeStopObjectsTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeStopReceiptCodecTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeStopReceiptsTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeStopServiceTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeStopTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeTransactionTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeVerifyPreflightTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceIntakeVerifyTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/protocol/E2aPageItemTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/protocol/E2aProtocolLimitsTest.java
?? server/src/test/java/com/acrylic/outbound/temporary/protocol/E2aV36MigrationTest.java
```
