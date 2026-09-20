# R06 M2b 原命令、来源映射与恢复证据契约准备

日期：2026-09-20。源码基线 `90581375128ee426fcf979a76eb44f4bb7734fef`。**本文件中的新 DTO 名、命令族、状态维度和入口均为实施提案，没有新增API、表、权限授予、签名格式或客户端行为。** 既有正文、原签名、来源ID和数字恢复代次不得改写。写阶段仍受[锁图](LOCK_GRAPH.md)及[待执行验收](HANDOFF.md)约束。

本轮承接[M2b方案](../../R06_M2B_IMPLEMENTATION_PLAN.md)；[Q01～Q16](../../OPEN_QUESTIONS.md)均已确认，不重新决定正常临时确认、短装、48小时、精确重量或放行条件。Android/后端固定契约后可以分工，不能在协议尚未冻结时各自猜签名字段。

## 1. 当前实现与新协议的分界

| 当前代码事实 | 下一阶段必须补齐 |
| --- | --- |
| [SubmissionControl](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionControl.java) 的 TEMP 来源是 `{kind:TEMP,creatorDeviceId,clientOrderId}`，首轮 round=1，authorizationId 固定，assignment/continuation 为 null | 后续 round/映射/恢复契约须新版本显式接入；不让旧 parser 静默接受新续作结构 |
| [V33](../../../server/src/main/resources/db/migration/V33__temporary_submission_sources.sql) 允许 TEMP C 无 source_order、无 canonical；S 无 assignment、固定 temporary_authorization | 当前没有 canonical mapping 版本/历史/REDIRECTED 生命周期；不能从 current_order 非空推导已有完整关联协议 |
| [SubmissionService.accept](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionService.java) 在 ABORTED/REJECTED 时拒绝正文；read 可按原 submissionId 返回原机有权回执 | 日常提交 read 可作为已有控制证明的来源；它不是跨代次灾后重建、异机代传或完整实物恢复入口 |
| [TemporaryReviewService.read](../../../server/src/main/java/com/acrylic/outbound/temporary/review/TemporaryReviewService.java) 要求 TEMP、有 order、有 payload | M2a详情不是正文前ABORT证据读取入口，不能用 LEFT JOIN 后补空字段假装支持该场景 |
| [SubmissionReview](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionReview.java) 每S一项正式审批，TEMP拒绝；[SubmissionFinalizer](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionFinalizer.java) 也拒绝TEMP | 不能复用一个 approved 布尔值表示排除重复、短装批准、正常确认、恢复或处置 |
| [IdempotencyRequests](../../../server/src/main/java/com/acrylic/outbound/common/IdempotencyRequests.java) 现有索引按 actor/route/key 保存请求hash和resourceId | 新命令还需不可变原回执、实际权限/上下文及当前效力；不能每次用资源当前状态重新生成“原回执” |
| [TemporarySubmissionReceipt](../../../android/app/src/main/java/com/acrylic/outbound/pda/temporary/TemporarySubmissionReceipt.kt) 仅接受 REGISTERED/RECEIVING/REVIEW_REQUIRED/ABORTED，并拒绝已有 orderId 变化 | 关联签名安装、TEMP成功验签/耐久保存/归档都尚未实现；服务端不能先换目标后要求旧客户端接受 |

## 2. 稳定来源与上下文

定义 `SourceIdentity` 为判别结构。FORMAL 保留原 sourceOrderId；TEMP 保留原 creatorDeviceId + clientOrderId。warehouseId、originalOperatorId、authorizationId、freezeDeviceId 从服务器原记录/已验证证据交叉取得，不从当前调用者替换。canonical 是当前业务目标，**不是来源身份**。

| 字段 / 组 | 语义与约束 |
| --- | --- |
| sourceIdentity | 永久不变；TEMP 同来源不得改原资格，沿用 temporary_work_origin 绑定 |
| submissionId / workRound | 一个冻结结果固定一个S及轮次；续作的新S必须由受控票据产生，不能因超时/404换ID；旧轮次事实保留 |
| controlIdentity | source、S、round、原资格、freezeDevice、sourceRecoveryEpoch、既有continuation关系的固定组；正文未收时仍可存在 |
| receivedPayloadSha256 | `submission_payload` 已接收、验证和耐久引用的正文摘要；没有时null，不能用分块声明代替 |
| declaredUploadSha256 | `submission_upload` 的客户端冻结正文声明，可在未收齐甚至ABORTED时存在；单列声明大小/块数/已收块，不能称为完整正文 |
| canonicalOrderId / mappingVersion | 提案：未正式化为 null/0；首次正式化为目标/1；以后每次受控映射单调增加，保留from/to历史及签名证明。历史V33行回填策略待新迁移复审，不能在当前响应冒充这些字段已经持久化 |
| currentSubmissionId / scopeRound / slotVersion | 提案上下文的一部分；读取值只是提示，写事务重验 C/L 的真正归属、轮次和current；无canonical时slot明确为null |
| plan / targetRevision / policy | 原临时计划及其hash、目标revision/rowVersion、原库存/映射/政策与扣账标志分别绑定；关联不改原payload或替换原政策 |
| decisionVersion / candidateContext | 单调决定版本及服务器确定的完整候选集合/对象版本/检索政策；页面某一页、浏览器过滤条件或“0条”不能成为完整排除证明 |
| sourceRecoveryEpoch / transportRecoveryEpoch | 前者来自原签名/正文/控制，不改；后者为当前传输门禁。当前数字字符串与未来R09 UUID映射分开；恢复不能把旧正文epoch重写成新值 |

`controlSha256` 可作为**新证据封套中的派生摘要提案**，必须标明算法版本及所覆盖固定字段；V33没有独立持久原控制hash列，不得把后来派生值宣称为当时签署的控制正文。原签名取消证明及冻结正文原字节另行保存。

## 3. 原命令及不可变原回执

建议命令族覆盖 `DUPLICATE_EXCLUDE`、`LINK`、`REJECT`、`SHORT_APPROVE`、`NORMAL_CONFIRM`、`PHYSICAL_DISPOSITION`、`REOPEN_WORK`；名称只为设计定位，不是已注册route_scope或权限码。读取候选不创建命令，也不隐含确认。

1. 客户端首次发送前耐久保存：操作族/版本、原键、完整规范命令正文、来源、S/round、期望目标/映射/计划/决定/候选版本、传输代次、当前提交身份及理由。存储失败不发请求；丢响应、刷新、重登录继续查同键，不能重建正文或自动换操作者。
2. 复用现有 `(principal_id,route_scope,idempotency_key)` 语义；同一业务命令的接口别名不得产生两个route_scope绕过。正文hash覆盖 source、命令类型和全部期望字段/理由；actor来自认证，不接受客户端自报权限。新 `commandId` 如采用UUID，须同原键固定并纳入hash；不能每次服务端查询生成一个新ID。
3. 正文规范化沿用 [SubmissionJson](../../../server/src/main/java/com/acrylic/outbound/submissions/SubmissionJson.java) 的键排序/数组顺序、严格重复键检测、安全整数和十进制字符串规则，给命令独立版本。原payload hash、命令hash和回执hash三个域不混用；禁止忽略理由、target或expectedVersion计算hash。
4. 在[统一锁集合](LOCK_GRAPH.md)内取得当前授权及上下文，再处理原键。同键同hash返回原命令回执；同键异hash明确冲突；不同键同一动作竞争由决定版本/current条件和唯一约束裁决，不产生第二个成功/释放。只写 `api_idempotency_request` 而没有业务唯一性不足以实现这点。
5. 服务器持久命令原文/hash、实际 actor/userAuthVersion、device/session身份（适用时）、当时已核验权限集合或可重放的版本引用、仓库、原操作者引用、上下文、服务端决定时间及原响应字节。现有 auth_version 不能单独代表未来可独立修改的 role_permission 集合；权限快照从数据库生成，不让客户端提交快照授予自己权限。
6. 决定、命令回执、审计、映射/处置或记账结果同事务落库；签名失败或审计失败整笔回滚。只接收证据时返回的RECEIVED不与后续业务决定混为一个回执。文件先耐久、后引用；孤儿对象不等于业务已受理。

建议查询返回两个独立部分：

| 部分 | 必须保持 |
| --- | --- |
| `originalCommandReceipt` | commandId/原键引用、commandHash、originalContext、actor/权限版本、原决定、committedAt及原签名/原响应；同键重读逐字节稳定。旧决定后来失效也不修改原回执 |
| `currentEffect` | evaluatedAt、当前来源/目标/轮次/决定版本、是否仍适用、失效原因、supersededBy、当前业务状态和所缺条件；这是当前查询结果，不能伪装为原签名的一部分 |

命令回放也先验证当前身份和读取/操作范围，不能凭原键泄露他仓资料。写权限已撤销时不得重新执行动作；有明确证据只读能力的人可以通过独立读取路径看原回执，不能借“恢复重试”复活旧授权。认证失效、服务端未掌握记录、网络不明分别返回可恢复错误；404不能证明该操作从未提交，更不能作为现场释放依据。

## 4. 当前效力失效矩阵

下表是下一写阶段须实现的约束，当前M2a“无问题”不具备这些效力。

| 变化 | 原记录如何保留 | 对后续动作的约束 |
| --- | --- | --- |
| 新候选出现、候选计划/状态/已有作业变化 | 原排除命令和当时候选集合留存 | 原排除仅对原版本有效；最终化在共同W锁下重查完整集合。不能信任旧页面hash或“已点过排除” |
| canonical / mappingVersion 改变 | 原payload/source/orderScanSeq及旧映射证明不动，新增映射历史 | 旧目标短装/正常确认/排除的当前效力失效，新目标分别重验；关联不等于出库确认 |
| current S / round / continuation 改变 | 旧S终态与原回执留存，superseded关系追加 | 迟到批准不能作用于新S；旧ABORT/REJECT不能复活；新S覆盖受控保留集合及批准处置 |
| 原/目标计划、政策或摘要不符 | 不改原事实去匹配新目标 | 明确版本冲突/不兼容，按既有合法修订或续作处理后重新核对，不自动扩大计划消除短装 |
| actor/原人/设备权限或准入变化 | 当时权限快照保持，历史合法证据仍可核对 | 新决定实时鉴权；原资格和当前权限分开检查。合法晚传不自动作废，也不自动续期或忽略新作业超期 |
| 恢复代次或控制revision变化 | sourceEpoch、原命令/原签名不改 | 普通写停止，转R09专用证据/对账流程；重新登录或下载新基线不能解除保护 |
| S已SUCCEEDED | 唯一成功回执、原记账和结果不可变 | 后到abort/拒绝不释放、不重记；更正/返库走R07；成功TEMP永不再关联 |
| REJECTED/ABORTED | 原取消/拒绝、payload存在与否、已知占用保持 | 只有完整原机恢复/实物处置或受控续作才能改变已装包去向；不做“清空待审即释放” |

## 5. 权限与职责边界

| 动作 | 已确认边界 / 建议接法 | 不可推导出的权限 |
| --- | --- | --- |
| 正常临时确认 | 既有 `temporary_order:confirm`；可由有该权的普通上传者在同一提交服务直接确认正常单，或有权Web/PDA另行确认；服务器实时重验 | 不含短装批准、关联、重复排除、库存更正、实物释放 |
| 短装批准 | 沿用已存在 `submission:review` 的技术接法，记录短装项/原因和主管实际决定；异常自批规则按V2.3，未配置小团队例外不能自动启用 | 仅有该权仍不能完成TEMP正常确认；某产品0允许，整单0不出库 |
| 重复候选查询 | 当前M2a沿用独立 `temporary_order:confirm` 读权限；W1只读信息不授予决定效力 | 读权限不等于排除或关联权限 |
| 重复排除 | 原R06专项建议接 `temporary_order:link`，必须保存独立人工核对理由及完整候选版本；这是技术提案，下一契约批锁定后再实现 | 不从 `temporary_order:confirm` 自动推导，也不强制所有正常单由另一人确认；不自动清除库存/来源冲突 |
| 受控关联 | 已有 `temporary_order:link`，兼容且空白READY目标、无已领取/PC/PDA输入作业/在途或成功提交 | 无强制抢单、合并两套扫码、直接批准短装或放行权限 |
| 拒绝/原机恢复/续作/处置 | 分开记录拒绝、完整原机证明、`order:resume`及`order:dispose`；正式取消还用已有取消权限。TEMP适配尚待实现 | REJECT/ABORT不等于RELEASE；恢复read不等于扫描授权，全部卸下结束不伪造0包成功 |
| 灾后/停用身份的证据提交 | R09独立证据能力；原专项的 `temporary_order:recover` 只是候选权限名，V33没有实现/授予；代传需R07原来源链 | 不放宽现有login，不以新设备替换creator，不拿证据权限确认出库 |

正常确认和异常审批职责分离：可以允许同一显式操作在该人同时具备全部权限且职责规则满足时记录多项决定，但数据库及回执仍分别可追溯。旧设计中“首版关联完全相同计划”“48小时默认候选窗”等是技术建议，不把它们冒充新的业务硬限制。

## 6. nullable canonical / 正文前ABORT的独立证据读取模型

建议新增只读 `SourceEvidenceView`（名字未注册为API）：按仓库 + sourceIdentity + workRound + submissionId 查原来源和控制。服务端以原A/T/C/S交叉核验，不能靠orderId查起；不得为读取而创建scope、订单、assignment、FH号或空payload。

| 证据组 | 已有数据源 / 提议响应语义 |
| --- | --- |
| provenance | TEMP原二元source、原authorization、原设备/操作员/仓库/代次；当前 uploader/reviewer 另列。无签名来源证明则记录缺口，不自行推断归属 |
| control | S、round、固定control字段、registeredAt、状态、current关系；未知/备份缺失与“已验证不存在”分开。原控制读取不调用 lock 注册副作用 |
| mapping | canonical可null；V33原值读取即可。新mappingVersion/history须未来schema；无canonical时order、orderNo、slot都明确null，不从temporaryNo拆出FH号 |
| payload | `received=false, sha256=null, bytes=null` 表示没有已接收正文；known original local hash（证据提供方声明）、declaredUpload与received三类不混用 |
| upload | 可以有声明hash/bytes/chunkCount/已收到块；这仍不证明正文完整或已记账。每块摘要/原字节单独引用，不能把缺块补成空内容 |
| terminalProof | 原ABORTED签名及原外层回执字节、payloadPresent、原hash/null、来源/round/freezeDevice/epoch；验签后逐字段比对。无签名REJECT证据不伪造成已签取消 |
| audit | 原device共用localSeq的连续确认和缺口范围、原事件摘要/归档引用；不能只取MAX或把TEMP另起序列 |
| physical | 已知捕获/占用/恢复清单及来源；完整性状态 `NOT_PROVIDED / PARTIAL / VERIFIED_COMPLETE` 为提案。零条已知云端占用不等于车上无货；未知集合不能返回count=0代表完整 |
| currentAssessment | 服务端当前身份/代次/缺口/冲突及可执行下一步；本阶段始终不允许离场、续扫、释放或本地删除。历史来源时间不替代当前权限 |

读取器须覆盖三种可重现夹具，不混用：

- A：ABORT先到，无upload、无payload、无canonical/slot/assignment，原取消proof的hash=null / payloadPresent=false；查询后数据库和文件不变。
- B：start/部分chunk之后ABORT，声明upload摘要和已有块仍在，但received hash=null；不得把声明hash写入原取消回执，也不得调用finish/accept补正文。
- C：完整上传后ABORT或REJECT，已有原payload、唯一PDA_TEMP canonical/slot和待审占用；完整正文只供核对，不改变原取消/拒绝，更不释放已知占用。

已有 `SubmissionService.read` 可返回原机原批次的取消回执；证据详情可以组合读取这些既有记录，**不能调用 `submitTemporary/accept/chunks.finish` 恢复ABORT正文，也不能调用M2a detail并要求先正式化。** 如PDA事后提供其真正冻结的本地正文，保存为独立 `evidenceKind=LOCAL_FROZEN_PAYLOAD` 的证据对象：不写回 `submission_payload`，不改变原 `payloadPresent=false` 的承诺，不把ABORTED改为REVIEW_REQUIRED。证据与当前控制的匹配结论另存。

这一新证据通路首先可完成“读取已有证据/识别缺口”，不依赖M2b-2b整个关联功能。对无order的实物保留/处置，现有 `bale_commitment.order_id` 为NOT NULL且正式RecoveryStore需要assignment，因此下一写批要选择兼容的**来源归属模型**并迁移共同包互斥：不另建无共同唯一裁决的TEMP占用孤岛，不创建占位订单/assignment满足FK。R09案件证据索引也不能代替日常包占用。该模型及非空升级测试未完成前，只有证据，不开放释放/续作。

证据对象先写不可变私有文件并核摘要、耐久化，后在案件/来源事务建引用；失败不返回已接收。恢复包、块、历史签名、控制资料的大小/数量受明确资源上限；采用既有32MiB/256KiB分块只作为正文技术复用建议，不能将32MiB变成某车业务包数限制。每次读取文件仍重新核仓库/能力，不返回服务器内部路径。

## 7. 受控关联和成功后的反向提示

关联读取双方完整锁集合；目标必须满足V2.3空白READY及兼容条件；current/slot转换、mapping记录、原命令回执、签名映射和审计在同一事务。目标原生来源标为REDIRECTED或等价不可作业状态，并在正式领取、发布修订、PC start、普通submit所有入口核验。不能只在UI隐藏领取。

PDA先验签原source/from/to/mappingVersion/原S/round/hash/目标revision，再在一个本地事务安装映射；已有目标空缓存仅在证明无输入/扫码/批次/授权后合并占位。若保存失败、遇已有本机作业或映射丢响应，保留原记录和防重，按原命令恢复。旧Android现有 orderId恒定检查须显式升级，不能取消校验后盲信服务端目标。

**成功TEMP后补建/发布正式同趟单的反向候选提示最迟M2b-4b开放前完成。** R06提供成功与待审来源查询契约；总调度指定单一共享订单执行者接入OrderService.create/revise、OrderReleaseService.publish及PC嵌套发布。提示供人工核对，原成功事实不改、不重新关联；无作业重复正式单可依既有权限取消，有实际作业或出库则保留两边证据进入R07。未授权的新自动阻断、按车号合并规则不在本设计中。

## 8. 与R09控制/证据契约的接点

依据[R09控制提案](../R09/RECOVERY_CONTROL_CONTRACT.md)和[R09证据提案](../R09/RECOVERY_EVIDENCE_CONTRACT.md)。本轮双方核对：R09的V33工作只扩展隔离副本备份/恢复核验，不新增灾后字段或正式恢复API；最终工具覆盖以R09自身交付为准，不借用V32通过记录。

| 接点 | R06提供 / R09消费 | 仍未实现或待共享批完成 |
| --- | --- | --- |
| 普通/证据门禁 | 普通TEMP写走现有E；未来证据写用 `lockEvidenceWrite`，原source epoch不改 | 库外控制、UUID迁移桥、REQUIRES_NEW所有Job fence、认证恢复白名单和两阶段释放；当前RECEIVING API不是门禁例外 |
| 案件身份 | sourceIdentity+round+S+evidenceKind+原对象ID，原人/设备/资格，nullable canonical，原命令回执 | recoverySession/case索引及真实新API；不能以warehouse/order强制代替来源 |
| ABORT / 部分上传 | control、原签名、received vs declared hash分开、已知块/审计/未知实物范围 | 独立证据接收/完整清单证明/来源归属处置；不能重放业务accept |
| 不可变内容 | 原规范字节/hash/keyId，原签名逐字段对照；派生评估另存 | 历史验签密钥环、异地取回证明、缺文件/损坏/未知key处理 |
| 映射及决定 | 原映射链、currentEffect、失效原因、决定/审批/处置引用闭包 | 新schema和跨端验签；R09不能根据旧current或汇总重建新决定 |
| 审计/清单分页 | FORMAL/TEMP共用原device seq，固定快照cursor，缺口集合 | 灾后固定设备/批次清单、连续证明和跨代次cursor重建；未知设备不算空清单 |
| FH号/计数器 | 已正式化目标与计数器同一致截点；正文前ABORT无发号 | 截点外已发号可信高水位及日志闭包；不能按现存MAX猜安全发号 |
| 成功与清理 | 原结果、实际扣账标志和成功/取消原证明保留，归档引用至少5年 | TEMP成功/归档和完整灾后重建均尚未实现；`localDeletionAllowed=false`，不因read/备份监控PASS解除90天清理屏障 |

重建后的当前对账确认绑定新恢复会话，不修改原成功或取消签名。原autoDeduct=false不得补扣；丢失后续R07调整链仍是缺口。恢复证据VERIFIED或案件RECONCILED都不是车辆放行授权。

## 9. 本轮可冻结与后续必须再核验的材料

可交给下一执行者的是本文的身份/空值/不可变回执/当前效力分离规则、读模型、权限边界和字段核对清单。具体API路径、schema版本、签名audience、迁移编号、异常码和自批配置接口仍须在被派发的实现批核对后固定，不冒充现存实现。先完成有界证据读取及向量后，Android证据/映射消费者可独立准备；共享记账及协议签名解析保持单一写者。

无业务测试、HTTP、设备、数据库或跨存储崩溃验证结论；所有待验证项及实施责任见[HANDOFF](HANDOFF.md)。
