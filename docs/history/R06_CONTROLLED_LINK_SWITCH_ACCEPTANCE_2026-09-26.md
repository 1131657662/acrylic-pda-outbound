# R06 M2b-2b 受控关联指针切换验收（2026-09-26）

当前助手实现、自审并在隔离非空 MySQL 上验证；**无独立人员复审**。本批把上一批（[人工确认闸门验收](R06_CONTROLLED_LINK_ACCEPTANCE_2026-09-26.md)）只登记的“已签名 LINK 决定”变成**真正生效的受控关联**：人工确认与指针切换在同一事务内完成，或整体不生效。生产入口继续关闭。

## 1. 交付范围

| 项 | 内容 |
| --- | --- |
| 新增源码 | `server/.../temporary/command/TemporaryLinkMapping.java`（切换写入器） |
| 新增迁移 | `server/.../db/migration/V41__temporary_controlled_link_mapping.sql`：把 `temporary_source_mapping.origin` 的检查约束扩为含 `CONTROLLED_LINK`。约束名由 MySQL 自动生成，脚本从 `information_schema` 解析真实名称并在缺失时经临时表守卫**硬失败**，不做静默跳过；本迁移不改任何数据 |
| 修改源码 | `TemporaryLinkOperation`（闸门通过后调用切换，决定 `applyState=APPLIED` + `mappingId/mappingVersion`）、`CommandDecision`（APPLIED/NOT_APPLIED 两种 LINK 决定形状）、`CommandBusinessIntegrity`（按不可变映射行闭合 APPLIED 关联）、`CommandDecisionEffect`（`LINK_IN_FORCE` / 新映射取代 / 已复核范围变化） |
| 测试 | `inventory/TemporaryLinkChecks`（新增切换状态断言与“旧目标上下文失效”用例）、`e2averify/E2aTemporaryLinkIT`（迁移目标改到 V41） |
| 生产入口 | 仍无任何 `CommandOperation` bean；`capabilities` 五项全 false；`localDeletionAllowed=false` |

## 2. 切换语义（同一事务）

`TemporaryLinkMapping.install` 在人工确认闸门全部通过后执行 4 条**条件更新**，每条必须恰好命中 1 行，否则 `COMMAND_CONTEXT_CHANGED` 并整体回滚：

1. `INSERT temporary_source_mapping`：mapping_version = 原值+1、canonical_order_id = 目标正式单、`origin='CONTROLLED_LINK'`。
2. `UPDATE submission_scope SET canonical_order_id=?,mapping_version=? WHERE id=? AND mapping_version=<原值>`：当前目标改为正式单，旧目标决定随之失效。
3. `UPDATE order_submission_slot SET order_id=?,slot_version=slot_version+1 WHERE order_id=<旧目标> AND scope_id=? AND slot_version=<原值>`：作业槽改指正式单。
4. `UPDATE outbound_order SET status='LINKED',row_version=row_version+1 WHERE id=<旧目标> AND source='PDA_TEMP' AND status='REVIEW_REQUIRED'`：被取代的自动 PDA_TEMP 单置为不可作业。

目标正式单本身保持 `READY`（关联不改变其状态、不创建 assignment/结果）；已有作业、非 READY、计划/库存不符的目标在闸门阶段已被拒。

回执与审计在**同一事务**落库：`businessDecision.kind=LINK`、`submissionReceipt={applyState:"APPLIED", targetOrderId, targetOrderNo, targetRevision, targetRowVersion, sourceCanonicalOrderId, sourceMappingVersion, candidateContextSha256, mappingId, mappingVersion}`。

`CommandBusinessIntegrity` 只按**不可变事实**闭合：候选集合仍用已存 JSON 重算 `authentic`；新增映射行必须是 `CONTROLLED_LINK`、版本 = 原值+1、canonical = 目标、scope/仓库一致；上一版映射行的 canonical 必须等于回执记录的 `sourceCanonicalOrderId`。`CommandDecisionEffect` 对 APPLIED 关联给出 `LINK_IN_FORCE`；若后续新映射改变了当前 mapping/canonical 则报 `LINK_SUPERSEDED_BY_NEW_MAPPING`，若已复核候选**范围**（政策版本/起止日期/条数）变化则报 `CANDIDATE_CONTEXT_CHANGED:<字段>`。已生效关联自身造成的 canonical/mapping/slot 变化与“读取会话上下文差异”不判为失效，但失效原因仍逐条列出。

## 3. 隔离非空 MySQL 验证（attempt113）

`tools/e2a-cross-source-verify.ps1 -Run 113 -Link`：独立 127.0.0.1:19330 实例/独立 datadir+schema+账号、**非空 V34 种子 → V41** 迁移链、真实签名与真实 HTTP、认证 shutdown。

| 场景 | 期望 | 结果 |
| --- | --- | --- |
| 人工确认 + 切换 | 回执 `RECORDED`/`applyState=APPLIED`、`mappingId` 与库内 v2 行一致；`mapping_version=2`、canonical=目标、slot 指向目标且 `slot_version=2`、旧单 `LINKED`、目标仍 `READY`、目标无 assignment/PC/结果且仅本来源引用；映射行为 `0:SOURCE_REGISTERED:NULL`、`1:INITIAL_ACCEPTANCE:<旧单>`、`2:CONTROLLED_LINK:<目标>`；同键重放返回原字节；真实 HTTP 读回原回执且 `currentEffect.evaluation=LINK_IN_FORCE`；capabilities 全 false | 通过 |
| 旧目标上下文失效 | 切换后用切换前构造的旧上下文命令再提交 → `COMMAND_CONTEXT_CHANGED`；映射仍 2 版新、回执/幂等各 1 条、旧单保持 `LINKED`（不产生第二次关联） | 通过 |
| 跳过人工确认 / 伪造绑定 / 多候选 / 无候选 / 目标被改 / 计划被改 / 短装 | 与上一批一致：`TEMPORARY_MANUAL_CONFIRMATION_REQUIRED`、`COMMAND_CONTEXT_CHANGED`、`TEMPORARY_LINK_DUPLICATES_UNRESOLVED`、`TEMPORARY_LINK_TARGET_NOT_FOUND`、`COMMAND_CONTEXT_CHANGED`、`TEMPORARY_LINK_TARGET_PLAN_DIFFERS`、`TEMPORARY_LINK_SHORT_SHIPMENT_UNRESOLVED`，且均零痕迹（无回执/幂等行、decision_version=0、mapping/canonical/单状态不变） | 通过 |

IT 结果：`tests=1 skipped=0 failures=0 errors=0`（18.961s）。回归：同一批源码的服务端完整单元套件 `test --rerun-tasks` 为 `BUILD SUCCESSFUL`，102 个 XML/`tests=714 skipped=1 failures=0 errors=0`（唯一跳过为既有的 Windows 符号链接条件用例）；日志 `.local/link-full-unit-113.log`。证据：`.local/e2a-cross-20260925-113/`（`integration-result.xml` SHA256 `03C69AD465AB171AA69083009BDFCA9F5AF852740989104B5D7BBA2540CECDD8`、`integration.log` SHA256 `C2DA8E906F7F5F649EF004924A213E6A029787F01A001376390B4D69352CAF7F`、`connected-identity.tsv`/`shutdown-identity.tsv` SHA256 `4FF1027613A05F2A874E983F4340247889793363AC5D9E8806CC13C2D133BFA6`、起止 marker）。

当批源码 SHA256：

| 文件 | SHA256 |
| --- | --- |
| `db/migration/V41__temporary_controlled_link_mapping.sql` | `5D29AF12CA0E82D103F03A21AC7A7CE76C715FD36233995F7FFD4665C0A27A5F` |
| `temporary/command/TemporaryLinkMapping.java` | `6C517204BB45CFBF1338344E1BF8A71613810B222AAB2BB7E035B99AEC6E6F0B` |
| `temporary/command/TemporaryLinkOperation.java` | `2190D049839936976EC2E2E5AE904B9C5A1A125631298D1A28819800E5A58BD8` |
| `temporary/command/CommandDecision.java` | `7120B204EFE9B63C4D63EDDCEAF41DEB9E57EEBA216570409390B207878436A8` |
| `temporary/command/CommandBusinessIntegrity.java` | `CE5558C31FD89C50F66FE7C362F834EF66F489720B3BA4FB8AFC9B746F7CC3C5` |
| `temporary/command/CommandDecisionEffect.java` | `8E0BEE448BE97818830E30042EBA2ABDF14D9E3F1BF606278050756A0012A3B4` |
| `inventory/TemporaryLinkChecks.java` | `2C0D44D016FAA94F8DE921CF3DB3125E3CDE1474958B62D9370CB3F42F0DC30D` |
| `e2averify/E2aTemporaryLinkIT.java` | `8AFAB7A84F47B75BEF46E4B381E1DF5A82AC18F142D9F198CE5D46540CEBF9C4` |

（上一批 `TemporaryLinkOperation.java` 为 `0145408D…`，本批为切换版 `2190D049…`；上表 `2190D049…` 为 attempt113 之后、伪证 attempt114 还原后的复算值。）

## 4. 错误路径可伪证验证（attempt114）

沿用 `tools/e2a-link-counterproof.ps1`，**按字节**只删除 `TemporaryLinkOperation` 中“锁内重算候选摘要与命令绑定逐字比较”这一条判断后重跑同一 IT：

| 项 | 值 |
| --- | --- |
| 原始/还原后 SHA256（脚本备份与还原复算一致） | `2190D049839936976EC2E2E5AE904B9C5A1A125631298D1A28819800E5A58BD8` |
| 去守卫后 SHA256 | `063A9C02F3DF120B94914696E5F27B6D1CA5B87612F1DC46A8B02EB4C6567BA8` |
| 去守卫后 IT | **失败**（`tests=1 failures=1`，`forgedBinding` 期望 `COMMAND_CONTEXT_CHANGED`、实得 `COMMAND_RECEIPT_INTEGRITY_FAILURE`） |
| 证据 | `.local/link-counterproof-114/hashes.txt`、`.local/e2a-cross-20260925-114/integration-result.xml` |

如实记录：删除该守卫后拒绝用例**真实失败**（错误路径不是同义反复）；同时回执完整性用已存 `businessDecision.candidates.contextSha256` 重算 `expected`，伪造绑定被第二层拒绝，**该场景下没有任何映射被写入**。`CommandExpected` 中“候选绑定必须为非空文本”的守卫仍未做第二次按字节伪证（见 §5）。

## 5. 未实现 / 未验

- **关联后的正式作业链未接**：来源 canonical 已指向正式单，但 `TemporaryConfirmationSource.target()` 仍要求 `source='PDA_TEMP'` 且映射版本为 1，因此已关联来源**当前无法用普通确认对正式单出库**；这正是 M2b-4b（最终确认与成功恢复）的范围，未在本批改动，也**不能**据此声明 TEMP 闭环完成。
- 候选排除台账（M2b-2a）未实现：候选多于 1 条仍失败关闭；无法“逐单核对后关联到指定目标”。
- 正式单侧反向提示与本批的关联侧未接线（提示本身已有 attempt41 内部验证，未整合）。
- V41 为纯约束变更、不改数据；本批在非空 V34 种子库上跑通 V34→V41，但**未新增逐表数据快照比对**（旧表/旧行保留依赖既有 V34-V40 迁移验收，不重复）。
- 锁序：目标正式单仍在既有来源锁链之后加锁，完整锁图与真实并发（撤权/领取/发布竞争、并发关联）未验证，写阶段阻断仍有效。
- 未运行：`integrationTest` 其余 IT（含 R06_LEDGER_PROTOCOL 独立 Python 消费者 IT `E2aTemporaryDecisionProtocolIT`）；Android 未改、未跑 APK/lint；无浏览器/设备/现场/长时/断电验证；无独立人员复审。
- 无第二次按字节伪证：`CommandExpected` 的“候选绑定必须为非空文本”守卫；`TemporaryLinkMapping` 四条条件更新的一致性靠“任一不满足即回滚”的构造与断言覆盖，未构造并发触发。

## 6. 下一步

1. M2b-4b：接上“已关联来源 → 正式单”的确认与成功恢复（含 assignment/政策、签名成功回执、Android 映射安装与验签），并在取消/拒绝/重开路径上保持失败关闭。
2. M2b-2a 候选排除台账，解除“多于 1 条候选即失败关闭”。
3. 完整锁图与真实并发验证后关闭写阶段阻断；补做 `CommandExpected` 文本守卫的第二次按字节伪证。
