# R06 E2 来源归属与共同包占用模型

日期：2026-09-21。固定基线 `c3009b691d9d2c0afc32dad6e8f2293d36742a3f`，契约 0.29 / Flyway V33 / Android code20 / Room17。本文是可实施设计，未新增生产表、API、迁移、签名或写能力。字段与表名标为提案；正式编号和协议由后续唯一共享执行者冻结。

配套：[完整原机证据](ORIGINAL_DEVICE_EVIDENCE.md)、[非空升级与待验矩阵](MIGRATION_AND_ACCEPTANCE.md)、[交接与责任](HANDOFF.md)。继承 [M2b 来源契约](../R06_M2B/COMMAND_EVIDENCE_CONTRACT.md)、[E1 读取事实](../../R06_SOURCE_EVIDENCE_GUIDE.md)、[C0 向量边界](../R06_COMMAND_VECTORS/README.md)。

## 1. 已核对的事实与选择

| 当前事实 | E2 决定及原因 |
| --- | --- |
| [V33](../../../server/src/main/resources/db/migration/V33__temporary_submission_sources.sql) 的 TEMP `submission_scope` 可无 canonical；来源二元键唯一，S 可无 assignment | 直接复用 scope 和 `temporary_work_origin`，无订单仍有稳定来源。证据读取/接收不创建订单、FH、assignment 或 slot |
| [V9](../../../server/src/main/resources/db/migration/V9__formal_submissions.sql) 的 K=`bale_commitment` 以 `bale_id` 为主键；[V17](../../../server/src/main/resources/db/migration/V17__pc_exception_handover.sql) 延续正式、PC、恢复、续作、隔离互斥 owner | 保持 K 唯一裁决；在同表加入来源证据 owner 分支。证据明细允许不同来源声称同包，但它不授予占用，不能成为第二张可用库存表 |
| K 的 `order_id` 仍 NOT NULL，正式 [RecoveryPackages](../../../server/src/main/java/com/acrylic/outbound/recall/RecoveryPackages.java) 依赖原 assignment | 只为被完整来源引用约束的新分支允许 K.order_id 为 null；旧分支继续要求真实订单。新增来源证据对象，不绕入正式恢复入口造假父键 |
| [TemporarySubmissionIntake](../../../server/src/main/java/com/acrylic/outbound/temporary/TemporarySubmissionIntake.java) 接受完整正文后正式化，随后写 payload/事件/K | 日常原接受路径保持；终态后的本地冻结正文是独立 `LOCAL_FROZEN_PAYLOAD`，不写入 `submission_payload`，不再调用 accept/finalize |
| [E1 physical](../../../server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceFacts.java) 按 S 计已知 K，完整性永远 UNKNOWN_OR_PARTIAL | E2 新增独立完整性结果和来源级占用视图。旧 E1 计数不改称完整集合；来源级视图要包含后续 owner 变更、隔离和未解决冲突 |

比较过三种方案：推荐扩展共同 K 与独立证据引用，能保留当前唯一裁决和旧行；另建 TEMP 占用表会遗漏正式/PC 防重，拒绝；先把正式/PC 全部重构成新的通用作业根会扩大本批变更，不作为前置。这里没有新增统一授权框架或平行记账栈。

## 2. 永久来源与当前业务目标

来源永久为 `{kind:TEMP,creatorDeviceId,clientOrderId}`，原 authorization/operator/warehouse/sourceRecoveryEpoch 由 T、A、C、S 交叉核验。`scopeId` 是服务端内部归属引用；客户端不能以任意 scopeId 代替上述来源。`submissionId + workRound` 确定冻结轮次，freezeDevice、原资格和原控制不变。

canonical 是可空的当前目标，不能作来源主键。首次合法完整接受才可沿既有 `TemporaryFormalization` 生成唯一 PDA_TEMP 目标；后续受控关联只追加映射历史并更改当前目标，原来源、捕获 ID、顺序、原字节和原回执保持。原 canonical=null 的取消证明以后仍是 null。

映射版本采用 C0 提案语义：无目标 0，首次既有目标 1，后续单调递增。V33 历史行的 0/1 只标记“由既有事实导入的基线”，没有当时的 LINK 命令或新签名；不能伪造历史命令。映射持久化与 C1 在同一共享迁移批冻结，E2 不另建竞争版本计数器。

## 3. 关系模型提案

采用现有 MySQL 约定：ID 为 BINARY(16)，时间 DATETIME(3) UTC，计数/原周期 BIGINT，重量整数克 BIGINT 并保留原文本，摘要沿现有明确编码。本文不给出可执行 DDL。所有业务引用默认 RESTRICT，不级联删除；文件引用和历史决定至少保持既有留存期。

| 对象 | 最小持久事实和约束 |
| --- | --- |
| `source_evidence_object` | 原 objectId、scope/S/round、kind、原字节摘要/大小/私有 file key、原设备/上传者分别记录、接收时间。唯一 `(scopeId,submissionId,workRound,kind,originalObjectId)`；同键异字节冲突。同物理内容去重不合并授权引用 |
| `source_evidence_set` | 稳定 setId、scope/S/round、原停止对象、清单原文/hash、device/capture/audit 高水位、条目数；所有内容引用固定。接收状态与验证结果分开，接收成功不意味完整或可执行 |
| `source_evidence_item` | `(setId,itemKind,originalRecordId)` 唯一，原设备序号、原记录摘要和所属对象；扫描项另有 baleId、原 cycle/captureId/状态。容纳 INVALID/VOID/失败/放弃等事实，不能只存有效扫描 |
| `source_evidence_verification` | 追加验证结果，绑定 setHash、算法版本、服务端已知事实截点、缺口/冲突与实际验证时间。历史 VERIFIED 不随当前状态重写；当前适用性另算，不把客户端 complete 标志当证据 |
| `source_physical_resolution` 及逐包行 | scope/S/round、完整 setId/hash、期望映射/决定版本、原命令引用、停止证明、操作者及已核验权限；逐包 RETAIN/RELEASE/QUARANTINE、原 cycle/记录摘要/实物凭据，唯一命令和逐包应用记录 |
| `source_continuation` 及保留清单 | 新受控续作票据引用原终态 S、完整 set、resolution、from/to round、原人原机和保留集合 hash；同 scope/toRound 唯一，旧 S 不重开。其新授权与 Android 协议由后续共同协议批定义，不能复用假 assignment |

业务关联用显式 FK，拒绝仅有任意 `ownerType + ownerId` 字符串且无父键的设计。对象不得通过当前目标反推原人；历史 S/round 必须与 scope 相符，可用已有/新增唯一复合键及 FK 保证。集合引用跨仓、跨 S 或跨轮次在数据库约束可表达处加约束，其余在完整锁集合中重验并测试拒绝。

## 4. K 的扩展与约束

提案给 K 增加 `source_scope_id` 归属列，以及三种互斥 owner FK：`source_evidence_set_id`、`source_resolution_item_id`、`source_continuation_id`。source_scope 是稳定归属元数据，不与 submission owner 互斥。证据/处置/续作父表以 `(id,scope_id)` 建唯一复合引用，K 通过复合 FK 保证新 owner 和 source_scope 一致。

| K 分支 | order_id / source_scope / owner 约束 |
| --- | --- |
| 既有正式/PC 分支 | 保持原 owner CHECK，order_id 非空；新增 owner 均空。source_scope 可以为空，不为无提交的 PC/召回预造 scope |
| 既有 TEMP REVIEW_RESERVED | order_id 非空、submission_id 原值不改；从已核验 S.scope 回填 source_scope；新增 owner 均空 |
| 新 `SOURCE_RESERVED` | source_scope 和 source_evidence_set_id 非空，所有旧 owner 与另两个新 owner 空；order_id 可空，非空必须是同仓当前 canonical |
| 新 `SOURCE_CONTINUATION_RESERVED` | source_scope 和 source_continuation_id 非空，其余 owner 空；真实 canonical 可空；必须有已批准未失效的续作票据 |
| 新来源 `QUARANTINED` 分支 | source_scope 和 source_resolution_item_id 非空，其余 owner 空；沿用 QUARANTINED 状态，使共同可用性投影明确隔离 |

K 主键仍是单独 bale_id，不能改成 `(source_scope_id,bale_id)` 或 `(bale_id,cycle)`。同一实物不会因为另一来源、目标或新库存快照而出现第二活动占用。source_scope 一旦记录不得换来源；合法关联允许更新派生 order_id，但须同事务更新被授权的 K/映射并留原历史，不能因目标变更删除再插入占用。

只要 K 存在，就阻止非该合法 owner 的正式/TEMP/PC 使用。保留包因库存缺失、周期不符或已有别的 K 而冲突时，证据仍保存；已有 K 不覆盖、不释放，新集合标记冲突，不能宣称完整可处置或可续作。对尚无 K 且归属已验证的已知实物，可先建立保守 SOURCE_RESERVED，即使整个集合尚 PARTIAL；每条独立追溯到 set，不借 PARTIAL 作释放依据。

若本来源已有 REVIEW_RESERVED 的 K，只追加证据关联，保持 submission owner，直到一次合法处置/续作命令明确转移；不让证据接收自动夺取占用。转换必须匹配原 source/S/owner/cycle/state/版本；每条 affected row=1，任一不符回滚整条命令。

## 5. 共同裁决与锁内处理

继承 L1 前缀和 L2 冻结的精确集合，不发明第二套 access：入口提示定位原人/设备/会话及来源，E → G → 已排序 U/W/D/H → T/C → 已排序现存 O/L → S/授权及证据/命令对象 → 已排序 B/P/K。无 canonical 分支跳过 O/L，不锁假行；需要既有父对象的 FK 在首次子写前纳入。实际批使用 L2 最新锁合同，保留仓库互斥。

新集合或新 K 不存在时用共同 W/B 和唯一键裁决，不能把 RC 下“查询为空”当锁。提示后原人、scope、canonical 或所有者集合变化则事务退出并按原命令重取，不能持 K/O 后补未排序 U/C。文件持久化安排在事务外，引用和验证/保守占用的接受事务很短；不可在持 G/W 时传输大文件。

每次 K 新建、转移、释放、隔离都在同事务调用既有 `BarcodeChanges.bale`；失败连同命令/审计回滚。普通读取、接收原文文件、验证签名均不生成 dispatch/ledger/result，不递增 dispatchCycle。shared ledger 仍由原最终化责任者接入。

## 6. 必须配套核对的旧消费者

| 源码入口 | 已观察到的行为 / 后续实施要求 |
| --- | --- |
| [RecoveryStock](../../../server/src/main/java/com/acrylic/outbound/recall/RecoveryStock.java) `reserve` | 非隔离 owner 读取 BIN_TO_UUID(order_id)，随后对返回值 `.equals`。新 null order 可能触发空指针；必须按“存在 K 且不是本来源”返回受控冲突，不能在新数据可写后再补 |
| [SubmissionStock](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionStock.java) `reserve` | 按返回行是否存在区分空 K，常量 `.equals(nullable owner)` 目前会保守冲突；接 TEMP 正常接受时须显式设置/核验 source_scope。证据 owner 不可只因同 scope 自动当本 S 占用 |
| [PcStock](../../../server/src/main/java/com/acrylic/outbound/pc/PcStock.java)、[PcCandidates](../../../server/src/main/java/com/acrylic/outbound/pc/PcCandidates.java) | 存在任意 K 时未持有路径拒绝；保持 null owner 安全和隔离显示，不让无订单来源在候选 SQL 中漏掉 |
| [TemporaryReviewStock](../../../server/src/main/java/com/acrylic/outbound/temporary/review/TemporaryReviewStock.java) | null submission owner 最终返回需重验；不能改成“没有 owner 即无冲突”，来源视图单列明确的占用对象 |
| [ContinuationChecks](../../../server/src/main/java/com/acrylic/outbound/continuation/ContinuationChecks.java)、[RecallResolution](../../../server/src/main/java/com/acrylic/outbound/recall/RecallResolution.java) | 当前按 order_id 计集合；TEMP 无 order 不能复用该计数作全量证明。旧正式路径继续用旧 owner，E2 按 scope + 精确集合等价核验 |
| [DispositionStock](../../../server/src/main/java/com/acrylic/outbound/continuation/DispositionStock.java)、[PcExceptionStock](../../../server/src/main/java/com/acrylic/outbound/pc/PcExceptionStock.java) | 旧删除/更新条件限定自身 owner，保留；新来源处置须同等级精确条件，禁止按 baleId 或 orderId 批量释放 |
| [TemporaryAvailability](../../../server/src/main/java/com/acrylic/outbound/temporary/TemporaryAvailability.java)、[BarcodeStreamStore](../../../server/src/main/java/com/acrylic/outbound/sync/BarcodeStreamStore.java)、[PreparationPayloads](../../../server/src/main/java/com/acrylic/outbound/pda/PreparationPayloads.java) | 以 K 行存在投影 RESERVED，QUARANTINED 特判；回归 null order、新状态、库存重新发布和条码增量，不改为 INNER JOIN order 后过滤无订单 K |

这是一张新模型启用前的兼容清单，空指针是新 null 数据加入后的静态风险，不声称在当前 V33 生产数据上复现。所有相关消费者和迁移由后续一个共享执行者原子交付；当前 E2 只记录，不修改 L2 所属源码。

## 7. 业务与外部依赖

拒绝/取消保持 S 终态和占用，正常有权人员直接确认的既定目标保留，不加理由或强制主管确认。实物处置、短装审批及续作仍使用各自已有权限和既定责任规则；来源证据读取权限不推导证据写、释放、续扫或确认权。

R07 接管异机代传、换人交接、成功后更正/返库：传输者单列，原来源、签名和记录 ID 不改；已 SUCCEEDED 的 K/周期/原账不被本方案释放或回滚。R09 接管跨代次/失效身份恢复写门禁、库外 UUID、Job fence、历史公钥和引用闭包。E2 的日常原机证据不提供这些例外，完整性 VERIFIED 也不是恢复门禁解除或车辆放行。

当前全部写能力 false，`localDeletionAllowed=false`。仅有 E2 方案、E1 可读或 C0 离线通过均不能开放新按钮。后续正常 TEMP 成功仍须最终事务记账、签名回执、Android 验签耐久保存及归档闭环，正式单反向提示在开放前交付。
