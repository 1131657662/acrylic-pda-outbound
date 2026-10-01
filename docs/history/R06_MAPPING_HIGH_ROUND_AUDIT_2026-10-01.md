# R06 mapping2+、高轮次与跨来源续作审计（2026-10-01）

输入为主树 `D:/acrylic-pda-outbound`、`codex/development`、`9fe52fdc4c932b38e0f382805a7ecac5a500031b`。承接 A1.2/R2b；A1.1 批准设备范围沿[既有记录](R06_A1_1_CODEX_2026-09-30.md)复用。版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。

## 源码审计结论（不冒充运行验收）

1. **版本字段可表达 mapping3+，既有写链只证实一次关联。** `temporary/command/TemporaryLinkMapping.java:18` 虽使用 `Math.addExact`，但 `:26–28` 要求原 canonical 的 `source='PDA_TEMP' AND status='REVIEW_REQUIRED'`。首次 LINK 后 canonical 已切为 WEB；不能由版本算术推出事务可再次成功。`TemporaryConfirmationSource.java:47–56` 可识别受控的较高映射版本，也不等于存在可重复建立该状态的入口。mapping3+、重复关联、旧目标失效和 Android 多次安装均未验。
2. **TEMP 高轮次仍缺入口。** `SubmissionAccountingPolicy.java:37` 要求 `work_round=1`，`TemporaryConfirmationSource.java:26–27` 同样拒绝其他轮次；已验 mapping2 的记账分支不能替代高轮次确认、结果与归档链。
3. **正式交接/续作不能外推 TEMP。** `HandoverOperations.java:23` 拒绝 TEMP 并返回 `HANDOVER_TEMP_NOT_OPEN`，`handover_case` 有 FORMAL 约束；正式 `ContinuationRequests`/`ContinuationActivation` 基于 recall、assignment 和正式来源恢复。TEMP 交接及跨来源独立审计仍缺实现/组合验收，不因需要新表或组件认定业务政策待决。

## R06-BIND-PAGE：一次真实失败

attempt 806 在全新隔离 MySQL 迁移至历史 seed 基础点，写入 51 个来源并保留 seed 自带 4 个，再迁移至输入源码包含的全部迁移。合成 PDA bearer 经真实 Spring HTTP 请求 `GET /api/v1/pda/temporary-source-bindings?limit=50`，HTTP 200 返回 50 项，但 `hasMore=false`、`nextCursor=null`。JUnit 在 `unread sources require a continuation page` 失败：**1 test / 1 failure / 0 errors / 0 skipped，Gradle exit 1**。客户端已无服务端游标可继续获取剩余 5 项。

根因：`temporary/review/TemporarySourceBindingQueries.java:53` 查询 `limit+1`，`:54` 却先截成 `limit` 再交给 `common/Page.java`，分页器无法判断额外行。Android `SourceBindingRepository.kt:73` 在 null 游标结束遍历。证据证明列表截断；未实测第二页 CONTROLLED_LINK 安装或真机行为。该产品缺陷已登记停止，没有重跑或产品修改。

## 证据、离线门与收尾

- 证据根 `.local/e2a-cross-20260925-806/`：`integration-result.xml`、`integration-test.log`、55 行 `expected-source-ids.txt`、`page-0.json`、started marker、`result.json`、完整 SQL dump、摘要、设备快照。**page-0.json 仅保存 HTTP 的 data JSON，不是完整原始响应 envelope/headers；没有 completed marker。**
- 最终失败专项源码已保全到 `.local/autonomous-20261001/r06-bindings-page/failed-source/E2aSourceBindingPaginationIT.java`，SHA-256 `1BCE5723197E1C70C7DD5972654E50BE91BFAC0FE256369CD4D9E3E01863DA9D`；活动测试目录已移出，本提交不含测试/产品代码。
- 执行前 `testClasses` 编译成功、当时专项脚本 `parseErrors=0`。原方案中的冻结 Primary 白名单定向守卫**未执行**，不得声称执行前已通过；本轮没有全量回归。收尾仅检查文档、引用、事实块和暂存范围。
- 失败源码保全脚本在完成复制/移出后，因非法正则 `-replace '\'` 失败；后续独立分类脚本仅补分类记录，未重新执行测试，失败脚本保留。
- 806 MySQL 经身份核验后停机；SQL dump SHA-256 `E970D87A9E09DA4C9D7A529DF1D689DDB86653E573763F327E43ABEC1EF6D419`。完整 ZIP 的 290 个文件逐项长度/SHA-256 回读通过后，仅清理本轮新 datadir；ZIP 摘要 `C9D945C0BFB14C0F53D3573A5BF5B42CFA31971516729F56FEC1B92F62358F81`。dump、ZIP、binlog、XML、日志和失败轮均保留，历史 attempt 未动。
- 磁盘：本轮总前后 D 可用 **32110628864 → 32095174656 字节**；attempt 前后 **32110567424 → 31894368256**；归档清理前后 **31894269952 → 32095174656**，均高于 20 GiB。
- 总前后快照在 `.local/autonomous-20261001/r06-bindings-page/before/`、`after/`。七项保护文件摘要全部相同；SDK adb 前后均为真机 `22D2910042 device product:m71 model:m71 device:m71 transport_id:1`，目标设备命令/AVD 均 0。19330/19434 无监听；系统 MySQL 未动，仅留空闲 Gradle daemon。
- 旧看板在覆盖时漏先归档，本轮从输入提交恢复[精确原文](ORCHESTRATION_STATE_before_BINDING_PAGE_2026-10-01.md)，Git blob 校验相同；这是补保全，不声称执行顺序原本合规。原文旧 next-step 不重新生效。

## 下一步与未验边界

R06-BIND-PAGE 与[已停报 P08](R07_P08_REVIEW_STOP_2026-10-01.md) 缺陷子项保持停止。继续不依赖它们的 R06/R07 实现与验证，不能将自主授权降成全局只读。mapping3+、TEMP 高轮次、跨来源续作、写后回滚、故障换机、真实附件存储、库存差异审批/调整及现场身份/Keystore 条件仍未完成。候选排除和 TEMP REJECT 权限缺少可推导政策，继续单列；既定卸货释放、人工关联、主管审批和独立审计政策不重问。默认开关、`localDeletionAllowed=false` 及生产边界保持。
