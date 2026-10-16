# D16 缓存投影清理与引用保护开发证据（2026-10-15，范围纠正于2026-10-16）

本切片复核缓存投影清理实现并完成定向 Android 单元测试。2026-10-16 对照源码与总目标D16纠正范围：它没有删除整单事实，不能满足D16受控整单消费者退出标准。纠正前原件保存在 `.local/goal-20261016/e08-submission-entry01/d16-report-before-scope-correction.md`；下列历史测试事实、输入哈希及日志保持原样。后续合成隔离整单首切片另见[L1报告](GOAL_D16_WHOLE_ORDER_L1_2026-10-16.md)，不追改本页历史结果，也不计D16整体通过。

## 输入与范围

- 分支 `codex/development`，输入 HEAD `1bf2abfb4366423241ab9f7a0b7ba6b743fa66ce`；接续快照见 `.local/goal-20261015/d16-resume-01/`。
- 代码范围：`android/app/src/main/java/com/acrylic/outbound/pda/retention/RetentionCleaner.kt`、`RetentionDao.kt`；测试范围为 `RetentionCleanerTest`、`RetentionEvidenceTest`、`SubmissionArchiveMigrationTest` 和 `SyncSchedulerTest`。
- 生产边界未改变：`localDeletionAllowed=false`，生产清理/决定入口默认关闭；未连接原库/现网、未部署、未生成 Release。

## 退出标准对照

- 清理只在 `NORMAL` 恢复模式、观察到相同服务恢复代次、完成同步游标、无下载中的状态、有效可信成功时间和已确认活动基线同时成立时运行；未知代次、恢复中、下载中、缺少游标或无效时间均延后。
- 只处理已退休且超过严格 90 天边界的本地云端投影，批量上限为 500；活动基线和未观察成功的旧投影保留，下一次运行继续处理剩余批次。
- 订单投影保护 `prepared_order`、`work_order`、`claim_intent` 引用；条码投影保护正式、TEMP 和工作中引用表。清理只删除无引用的云端投影，不删除扫描、提交、审核、召回、库存或审计事实。
- 删除、退休和引用判断在同一事务中执行；注入末条 SQL 失败时前序投影删除回滚。Room 迁移测试确认升级保留既有表、提交原件和未凭空生成归档确认。

## 实际验证

命令：

```text
.\gradlew.bat -p android testDebugUnitTest --tests com.acrylic.outbound.pda.retention.RetentionCleanerTest --tests com.acrylic.outbound.pda.retention.RetentionEvidenceTest --tests com.acrylic.outbound.pda.retention.SubmissionArchiveMigrationTest --tests com.acrylic.outbound.pda.sync.SyncSchedulerTest --console=plain
```

结果：`BUILD SUCCESSFUL`，4 个测试类共 19 项，失败 0、错误 0、跳过 0，退出码 0。原始日志和结果见 `.local/goal-20261015/d16-resume-01/retention-tests/gradle.log` 与 `result.json`；JUnit XML 已保存在同目录 `test-results/`。运行后监听、进程和磁盘快照分别见 `listeners-after.json`、`processes-after.json`、`disk-after.json`。

输入绑定哈希：

```text
RetentionCleaner.kt                 C1C0DD7B5416A1C6269868A275D3FD533930D15B217EA5C2921F3135FDDC4ED2
RetentionDao.kt                      7C27CDB82073D5A832EE2BE2F4B5805C6688E1B24D8B8D1143F25E4ADE09DBDE
RetentionCleanerTest.kt              DBA5D3C4149F220BF8BAD0458E713CE342E1C9BFD1BE1B7CD5565678ABCFC4FF
RetentionEvidenceTest.kt              4430725449D4BF365D8620CF2E82BBDE6B2E01DA475678706F05D659AF9295BB
SubmissionArchiveMigrationTest.kt    79FFF31103D0EA716E18AEA55988A2BAF23382C2EECF1AE10143DB55AC98EE75
gradle.log                           2464629200B8FBB568C67CE1C15389A94C8DE3062CDE1DA5415586ECA59365DA
result.json                          EEEF589F2430B6597C67A72C6AFF774F618B1493E96B3A2FA780E283D6AFB965
```

## 结论与未验

本切片只证明缓存投影的清理判定、引用保护、事务回滚和迁移保留，可按路径及SHA复用该范围。受控整单事实清理、完整引用/可取回证明与新合成隔离副本的允许/拒绝、原子性及恢复尚未实现验收；仅缓存清理不算D16完成。真实生产删除资格、90天/云端长期留存、现场PDA/断电/异地恢复及D13–D15完整依赖链仍未验。D16整体、D18 L3、E06 L2、E09和总目标保持未完成；生产删除与localDeletionAllowed=false边界不变。
