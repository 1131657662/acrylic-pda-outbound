# Codex → DSH 工作树与本机状态清点（2026-09-28）

输入主树 ec4be3b；只读逐树 git status --porcelain=v1 --untracked-files=all。34棵树，起始主树干净，6棵非主树有改动，28棵干净。计数为逐文件展开，与旧目录折叠计数不可直接比较；共138条：39个已跟踪修改、99个未跟踪文件。

本轮未修改、stash、reset、clean、提交、删除或归档任何旧工作树；本文件只列路径/状态，不包含日志、密钥或业务内容。脏文件逐字节SHA-256快照留在主树.local/dsh-handoff-20260928-ec4be3b/，不能据清单判断可删除或已合入。收尾复核33棵非主树status及138个脏文件哈希，变化均为0，结果留存preservation-check.json。

| 工作树（D:/acrylic-pda-worktrees/ 下） | 分支 / HEAD | 修改 / 未跟踪 / 总数 | 内容及接手注意 |
| --- | --- | --- | --- |
| datetime-display | codex/datetime-display / daf4083 | 9 / 11 / 20 | Android时间展示/同步状态与界面；Web时间工具、页面、契约及Playwright测试/配置。9个已跟踪修改，11个未跟踪文件，未替用户整合或丢弃。 |
| r06-design | codex/r06-temporary-order-design / 2369392 | 0 / 6 / 6 | R06设计、交互、依赖、验收、进度与README六份文档。历史曾比较主树，但本次只盘点与保护原字节，不将旧比较当本次结论。 |
| r06-e2a-foundation | codex/r06-e2a-foundation / 319305f | 0 / 1 / 1 | 树根.local-e2a-vector.log一份未跟踪日志；不在.local目录内，不能据主树忽略规则当成可提交内容，需原样保留。 |
| r06-e2a-migration | (detached) / ea4d166 | 30 / 67 / 97 | 共享真服验证树：R06命令/归档/候选、R07更正与交接、R08导出/渲染、迁移和测试的历史同步副本；包含临时探针历史，不能把整树当待合业务分支。 |
| r06-e2a-protocol | codex/r06-e2a-protocol / 79cad44 | 0 / 1 / 1 | 树根.local-e2a-vector.log一份未跟踪日志；同样不属于.local目录，未读敏感正文、未提交。 |
| r07-design | codex/r07-handover-correction-design / 2369392 | 0 / 13 / 13 | R07设计、依赖、验收、UI、阶段实施、接口/来源结果契约等十三份文档，未替用户整合。 |

## 逐文件清单

### D:/acrylic-pda-worktrees/datetime-display

```text
 M android/app/src/main/java/com/acrylic/outbound/pda/sync/SyncScheduleState.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/ui/OrderCards.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/ui/PairingActivity.kt
 M android/app/src/main/java/com/acrylic/outbound/pda/ui/PdaUpdateActivity.kt
 M web/playwright.config.ts
 M web/src/inventory/contracts.ts
 M web/src/orders/OrderContinuation.vue
 M web/src/orders/contracts.ts
 M web/src/pda-download/DownloadPage.vue
?? android/app/src/main/java/com/acrylic/outbound/pda/display/DisplayTime.kt
?? android/app/src/test/java/com/acrylic/outbound/pda/display/DisplayTimeTest.kt
?? android/app/src/test/java/com/acrylic/outbound/pda/display/SyncDisplayTimeTest.kt
?? docs/parallel/DATETIME_DISPLAY.md
?? tools/datetime-tests.gradle
?? web/datetime-display.config.ts
?? web/src/shared/date-time.ts
?? web/tests/datetime/format.spec.ts
?? web/tests/datetime/page-fixture.ts
?? web/tests/datetime/pages.spec.ts
?? web/tests/datetime/serve.py
```

### D:/acrylic-pda-worktrees/r06-design

```text
?? docs/parallel/R06/ACCEPTANCE.md
?? docs/parallel/R06/DEPENDENCIES.md
?? docs/parallel/R06/DESIGN.md
?? docs/parallel/R06/INTERACTION.md
?? docs/parallel/R06/PROGRESS.md
?? docs/parallel/R06/README.md
```

### D:/acrylic-pda-worktrees/r06-e2a-foundation

```text
?? .local-e2a-vector.log
```

### D:/acrylic-pda-worktrees/r06-e2a-migration

```text
 M contracts/archive-schemas.yaml
 M report-rendering/src/main/java/com/acrylic/outbound/reports/Adjustment.java
 M report-rendering/src/main/java/com/acrylic/outbound/reports/AdjustmentValidator.java
 M report-rendering/src/main/java/com/acrylic/outbound/reports/ExcelDetails.java
 M report-rendering/src/main/java/com/acrylic/outbound/reports/ExcelRenderer.java
 M report-rendering/src/main/java/com/acrylic/outbound/reports/PdfHtml.java
 M report-rendering/src/main/java/com/acrylic/outbound/reports/ReportSnapshot.java
 M report-rendering/src/main/java/com/acrylic/outbound/reports/Values.java
 M server/src/main/java/com/acrylic/outbound/exports/ExportDownloads.java
 M server/src/main/java/com/acrylic/outbound/exports/ExportFacts.java
 M server/src/main/java/com/acrylic/outbound/exports/ExportJobQueries.java
 M server/src/main/java/com/acrylic/outbound/exports/ExportModels.java
 M server/src/main/java/com/acrylic/outbound/exports/ExportProducer.java
 M server/src/main/java/com/acrylic/outbound/exports/ExportRetry.java
 M server/src/main/java/com/acrylic/outbound/exports/ExportSnapshotReader.java
 M server/src/main/java/com/acrylic/outbound/exports/TemporaryExportEvidence.java
 M server/src/main/java/com/acrylic/outbound/submissions/SubmissionArchiveService.java
 M server/src/main/java/com/acrylic/outbound/temporary/TemporaryArchiveService.java
 M server/src/main/java/com/acrylic/outbound/temporary/command/CommandDecision.java
 M server/src/main/java/com/acrylic/outbound/temporary/command/CommandErrors.java
 M server/src/main/java/com/acrylic/outbound/temporary/command/CommandReceiptIntegrity.java
 M server/src/main/java/com/acrylic/outbound/temporary/command/CommandSignature.java
 M server/src/main/java/com/acrylic/outbound/temporary/command/TemporaryConfirmationSource.java
 M server/src/main/java/com/acrylic/outbound/temporary/command/TemporaryLinkMapping.java
 M server/src/main/java/com/acrylic/outbound/temporary/command/TemporaryLinkOperation.java
 M server/src/main/java/com/acrylic/outbound/temporary/review/TemporaryDuplicateComparison.java
 M server/src/main/java/com/acrylic/outbound/temporary/review/TemporaryDuplicateModels.java
 M server/src/main/java/com/acrylic/outbound/temporary/review/TemporaryDuplicateService.java
 M server/src/main/java/com/acrylic/outbound/temporary/review/TemporaryReviewService.java
 M server/src/test/java/com/acrylic/outbound/inventory/R08ExportFenceIT.java
?? report-rendering/src/test/java/com/acrylic/outbound/reports/HeaderEvidenceValidationTest.java
?? report-rendering/src/test/java/com/acrylic/outbound/reports/WithdrawalValidationTest.java
?? server/src/main/java/com/acrylic/outbound/correction/CorrectionCommand.java
?? server/src/main/java/com/acrylic/outbound/correction/CorrectionCommandConfiguration.java
?? server/src/main/java/com/acrylic/outbound/correction/CorrectionCommandController.java
?? server/src/main/java/com/acrylic/outbound/correction/CorrectionCommandService.java
?? server/src/main/java/com/acrylic/outbound/correction/CorrectionOperations.java
?? server/src/main/java/com/acrylic/outbound/correction/CorrectionReceiptCodec.java
?? server/src/main/java/com/acrylic/outbound/correction/CorrectionReconciliations.java
?? server/src/main/java/com/acrylic/outbound/correction/CorrectionStore.java
?? server/src/main/java/com/acrylic/outbound/exports/CorrectionAdjustmentSource.java
?? server/src/main/java/com/acrylic/outbound/exports/CorrectionExportSource.java
?? server/src/main/java/com/acrylic/outbound/exports/CorrectionWithdrawalSource.java
?? server/src/main/java/com/acrylic/outbound/exports/HeaderCorrectionSource.java
?? server/src/main/java/com/acrylic/outbound/handover/HandoverActivation.java
?? server/src/main/java/com/acrylic/outbound/handover/HandoverCommand.java
?? server/src/main/java/com/acrylic/outbound/handover/HandoverCommandConfiguration.java
?? server/src/main/java/com/acrylic/outbound/handover/HandoverCommandController.java
?? server/src/main/java/com/acrylic/outbound/handover/HandoverCommandService.java
?? server/src/main/java/com/acrylic/outbound/handover/HandoverOperations.java
?? server/src/main/java/com/acrylic/outbound/handover/HandoverReceiptCodec.java
?? server/src/main/java/com/acrylic/outbound/handover/HandoverStore.java
?? server/src/main/java/com/acrylic/outbound/temporary/TemporaryArchiveConfiguration.java
?? server/src/main/java/com/acrylic/outbound/temporary/command/TemporaryCommandFacts.java
?? server/src/main/java/com/acrylic/outbound/temporary/command/TemporaryCommandFactsController.java
?? server/src/main/java/com/acrylic/outbound/temporary/review/TemporarySourceBindingController.java
?? server/src/main/java/com/acrylic/outbound/temporary/review/TemporarySourceBindingQueries.java
?? server/src/main/resources/db/migration/V42__handover_case_model.sql
?? server/src/main/resources/db/migration/V43__correction_model.sql
?? server/src/main/resources/db/migration/V44__handover_approval_permission.sql
?? server/src/main/resources/db/migration/V45__correction_command_kernel.sql
?? server/src/main/resources/db/migration/V46__correction_effect_and_result_version.sql
?? server/src/main/resources/db/migration/V47__reconciliation_permission_and_receipt_nullability.sql
?? server/src/main/resources/db/migration/V48__correction_withdrawal_confirmation.sql
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aChainedCorrectionExportIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aCorrectionChainIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aCorrectionSchemaIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aCorrectionWebRouteIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aFormalRegisteredCounterexamplesIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aFormalRegisteredSourceIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aHandoverFormalIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aHandoverHttpLayerIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aHandoverModelIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aHeaderCorrectionExportIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aLatestSchemaRegressionIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aPartialCorrectionExportIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aServeBindingForAndroidIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aSourceBindingHttpIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aTemporaryArchiveSwitchIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aTemporaryCommandBrowserIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aTemporaryCommandWebHttpIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aTemporaryExportRenderIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aTemporaryLinkConfirmIT.java
?? server/src/test/java/com/acrylic/outbound/e2averify/E2aWithdrawnCorrectionExportIT.java
?? server/src/test/java/com/acrylic/outbound/exports/ExportVersionCompatibilityTest.java
?? server/src/test/java/com/acrylic/outbound/inventory/ChainedCorrectionExportHttpChecks.java
?? server/src/test/java/com/acrylic/outbound/inventory/CorrectionExportHttpChecks.java
?? server/src/test/java/com/acrylic/outbound/inventory/CorrectionExportHttpClient.java
?? server/src/test/java/com/acrylic/outbound/inventory/CorrectionExportSnapshotChecks.java
?? server/src/test/java/com/acrylic/outbound/inventory/HandoverFormalFixtureChecks.java
?? server/src/test/java/com/acrylic/outbound/inventory/HeaderCorrectionExportHttpChecks.java
?? server/src/test/java/com/acrylic/outbound/inventory/PartialCorrectionExportHttpChecks.java
?? server/src/test/java/com/acrylic/outbound/inventory/TemporaryArchiveFrontDoorHttpChecks.java
?? server/src/test/java/com/acrylic/outbound/inventory/TemporaryCommandBrowserChecks.java
?? server/src/test/java/com/acrylic/outbound/inventory/TemporaryCommandWebHttpChecks.java
?? server/src/test/java/com/acrylic/outbound/inventory/TemporaryLinkConfirmHttpChecks.java
?? server/src/test/java/com/acrylic/outbound/inventory/WithdrawnCorrectionExportHttpChecks.java
```

### D:/acrylic-pda-worktrees/r06-e2a-protocol

```text
?? docs/parallel/R06_E2A_PROTOCOL/bridge-classes/e2a/review/ProductionBridge.class
```

### D:/acrylic-pda-worktrees/r07-design

```text
?? docs/parallel/R07/ACCEPTANCE_PLAN.md
?? docs/parallel/R07/BASELINE_UPDATE.md
?? docs/parallel/R07/CORRECTION_RESULT_DESIGN.md
?? docs/parallel/R07/CROSS_TASK_ALIGNMENT.md
?? docs/parallel/R07/DEPENDENCIES.md
?? docs/parallel/R07/EVIDENCE_BASELINE.md
?? docs/parallel/R07/HANDOVER_DESIGN.md
?? docs/parallel/R07/INTERFACE_PROPOSAL.md
?? docs/parallel/R07/PROGRESS.md
?? docs/parallel/R07/README.md
?? docs/parallel/R07/SOURCE_RESULT_CONTRACT.md
?? docs/parallel/R07/STAGED_IMPLEMENTATION.md
?? docs/parallel/R07/UI_DESIGN.md
```

## 本机与写权

- 主树.local/it-keys和C1证据目录均命中.gitignore第5行.local/；git ls-files .local及嵌套.local为零，本次Codex接续提交范围内也没有.local路径提交。不等于全历史敏感数据审计或异地备份。
- 19300–19500范围内观察到127.0.0.1:19387，由PID23292的DeepSeek Harness.exe监听，非本次MySQL/Java测试实例；未终止。系统3306由PID5448监听，未操作。19330、19434、19437、23308未见监听。
- 仅见既有Gradle daemon PID20264，没有观察到本次C1应用/IT在途；DSH应用已运行不代表已确认其写权空闲。交接前后对六树状态与文件哈希复核，DSH开始写入前仍须确认本机任务及共享目标只有一个写者。
- r08-export虽Git干净，仍承载渲染浏览器/安装资产，不因干净而移除。其他干净树也未评估退役，不操作原库、历史失败库、marker、it-keys或本机文件。
- r06-e2a-migration保持detached ea4d166，30修改+67未跟踪=97。DSH主开发必须在D:/acrylic-pda-outbound的codex/development；若后续已授权验证守卫必须用旧树，先按文件比较并留前像/哈希、核对监听与独占写权，再定向同步。禁止整树覆盖、自动pull/merge/reset或套用旧树历史规则。

## 34棵工作树完整坐标（起始快照）

| 路径 | 分支 | HEAD | 未提交/未跟踪条目数 |
| --- | --- | --- | --- |
| D:/acrylic-pda-outbound | codex/development | ec4be3b | 0 |
| C:/Users/qq113/.codex/worktrees/password-min-eight/acrylic-pda-outbound | codex/password-min-eight | c1abef7 | 0 |
| C:/Users/qq113/.codex/worktrees/readable-order-number/acrylic-pda-outbound | codex/readable-order-number | b830f4c | 0 |
| D:/acrylic-pda-worktrees/datetime-display | codex/datetime-display | daf4083 | 20 |
| D:/acrylic-pda-worktrees/fifth-wave-integration | codex/fifth-wave-integration | f6e5af2 | 0 |
| D:/acrylic-pda-worktrees/first-wave-integration | codex/first-wave-integration | 9058137 | 0 |
| D:/acrylic-pda-worktrees/fourth-wave-integration | codex/fourth-wave-integration | c393d2d | 0 |
| D:/acrylic-pda-worktrees/r06-c1-ledger | codex/r06-c1-ledger | 26b224f | 0 |
| D:/acrylic-pda-worktrees/r06-command-vectors | codex/r06-command-vectors | 009faa9 | 0 |
| D:/acrylic-pda-worktrees/r06-confirmation | codex/r06-confirmation | 9fa0059 | 0 |
| D:/acrylic-pda-worktrees/r06-design | codex/r06-temporary-order-design | 2369392 | 6 |
| D:/acrylic-pda-worktrees/r06-duplicate-candidates | codex/r06-duplicate-candidates | df1a616 | 0 |
| D:/acrylic-pda-worktrees/r06-e2-source-model | codex/r06-e2-source-model | a073c23 | 0 |
| D:/acrylic-pda-worktrees/r06-e2a-contract | codex/r06-e2a-contract | 5de317b | 0 |
| D:/acrylic-pda-worktrees/r06-e2a-foundation | codex/r06-e2a-foundation | 319305f | 1 |
| D:/acrylic-pda-worktrees/r06-e2a-migration | (detached) | ea4d166 | 97 |
| D:/acrylic-pda-worktrees/r06-e2a-protocol | codex/r06-e2a-protocol | 79cad44 | 1 |
| D:/acrylic-pda-worktrees/r06-foundation-protocol | codex/r06-foundation-protocol | 1c5fca8 | 0 |
| D:/acrylic-pda-worktrees/r06-foundation-verification | codex/r06-foundation-verification | 8bdf1b8 | 0 |
| D:/acrylic-pda-worktrees/r06-identity-locks | codex/r06-identity-locks | 45a1c14 | 0 |
| D:/acrylic-pda-worktrees/r06-l2-locks | codex/r06-l2-locks | b530784 | 0 |
| D:/acrylic-pda-worktrees/r06-ledger-protocol | codex/r06-ledger-protocol | fec06b0 | 0 |
| D:/acrylic-pda-worktrees/r06-ledger-runtime | codex/r06-ledger-runtime | 0489036 | 0 |
| D:/acrylic-pda-worktrees/r06-ledger-verification | codex/r06-ledger-verification | b3f4265 | 0 |
| D:/acrylic-pda-worktrees/r06-source-evidence | codex/r06-source-evidence | 8cb9e2a | 0 |
| D:/acrylic-pda-worktrees/r06-source-foundation | codex/r06-source-foundation | 04a679b | 0 |
| D:/acrylic-pda-worktrees/r06-write-contract | codex/r06-write-contract | 71a78ba | 0 |
| D:/acrylic-pda-worktrees/r07-design | codex/r07-handover-correction-design | 2369392 | 13 |
| D:/acrylic-pda-worktrees/r08-export | codex/r08-export-rendering | f051535 | 0 |
| D:/acrylic-pda-worktrees/r09-ops | codex/r09-ops-foundation | 584449f | 0 |
| D:/acrylic-pda-worktrees/r09-v33-recovery | codex/r09-v33-recovery | 94305a7 | 0 |
| D:/acrylic-pda-worktrees/second-wave-integration | codex/second-wave-integration | 47dc8f2 | 0 |
| D:/acrylic-pda-worktrees/sixth-wave-integration | codex/sixth-wave-integration | 0b1094e | 0 |
| D:/acrylic-pda-worktrees/third-wave-integration | codex/third-wave-integration | 5c06875 | 0 |
