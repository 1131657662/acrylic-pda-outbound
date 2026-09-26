# R07接口、数据约束与迁移提案

**非正式契约。** 本文件不新增任何可调用接口，不改 `contracts/`、Java/Kotlin/TypeScript或数据库。候选名称须在R05/R06/R09接口核对后定稿，迁移编号、Room版本及APK/契约版本本轮不分配。

## 1. 通用请求/响应约束

候选路径沿用 `/api/v1` 前缀。Web使用现有会话/CSRF和实时仓库授权；PDA使用当前在线会话与已登记设备证明，离线JWT不是联网身份。每个资源ID都需核对仓库及对象范围，返回下载资源同样鉴权，不能因知道caseId而读取别仓证据。

所有命令发出前耐久保存 `commandId/Idempotency-Key`、目标/动作、原正文及SHA、操作者/设备/仓库、当前传输恢复代次、各expected版本。同键同正文返回原回执；同键异文409。资源约束也必须唯一，换一个键不能对同一交接/更正重复生效。服务端不能仅用一个全局幂等缓存绕过权限或仓库核验。

响应区分：`commandReceipt`（本动作结果）、`entityState/entityVersion`（读取时当前事实）、`blockers[]`（可定位的阻断）、`serverRecoveryEpoch/serviceMode`（当前环境）。旧命令回执可返回，但不能覆盖较新的实体投影。权限变化后可能只返回有权读取的最小结果，不为了重放泄露原证据。

所有BIGINT含序号、包数、整数克、版本均以十进制字符串传输，UUID为字符串，SHA-256为规范十六进制；`serverRecoveryEpoch`在新设计中作为不透明字符串判等。基线数字代次与R09 UUID协议必须显式能力协商，不能静默转换旧冻结正文。无值与已确认0严格分开。

## 2. PDA交接候选接口

| 方法/路径（提案） | 请求关键字段 | 响应/副作用与阻断 |
| --- | --- | --- |
| POST `/orders/{id}/handovers` | caseId、source/scopeId、expectedOrderVersion/revision/workRound、expectedMappingVersion、fromQualification、toDeviceId/toOperatorId、reason、mode=NORMAL/FAULT | FORMAL/canonical已存在时的入口；创建案件及停止申请，不发扫码授权。锁后解析唯一来源及当前PDA作业；成功订单转更正 |
| POST `/temporary-orders/{scopeId}/handovers` | caseId、原TEMP source、expectedScopeVersion/revision/workRound、canonicalOrderId可空、expectedMappingVersion、fromQualification、指定接收者、reason/mode | 来源先由R06恢复入口核验建立；允许canonical=null/映射0，不强制造正式单。与订单入口归一到同一scope案件，不能创建两份 |
| GET `/handovers/{id}` | 可选固定caseVersion、明细游标 | 四维状态、完整性、阻断、原提交结果、证据摘要、当前可执行动作；未知数不填0 |
| POST `/handovers/{id}/evidence-manifests` | uploadId、文件/页数量和长度、规范摘要、来源、代传许可引用、sourceRecoveryEpoch | 仅登记耐久证据上传；分块/分页按同ID摘要恢复，不生成有效出库 |
| PUT `/handovers/{id}/evidence/{uploadId}/parts/{part}` | 固定块SHA/字节 | 同块异文拒绝；下载/重试授权受案件范围约束 |
| POST `/handovers/{id}/evidence/{uploadId}/complete` | 整包摘要及清单摘要 | 核验全文件长度/摘要、原事实、来源审计；返回完整性和缺口，不等于停止确认 |
| POST `/pda/handovers/{id}/stop-ack` | scope/fromRound、原资格、packageId/hash、全量capture高水位、auditCoverage、stopEvent引用、caseVersion | 仅原机原身份正常停止；委托者不可代签。本来源资格封锁/停止回执/审计/outbox原子写，TEMP不默撤其他来源通用资格 |
| POST `/handovers/{id}/fault-resolutions` | expectedCaseVersion、故障/实物控制证据、归档覆盖、缺失区间、原提交终态依据、resolution | 专项裁决；FAULT_FENCED与DEVICE_ACKED区分。完整性不足只能隔离/补证据/记录损失，不能激活 |
| POST `/handovers/{id}/decisions` | expectedCaseVersion、expectedMappingVersion、documentSha256、原结果/集合版本、decision、physicalEvidenceIds | 批准固定清单或拒绝/撤回；批准仅生成准备票据。映射变化失效重批；撤回和激活同锁竞争 |
| GET `/pda/handovers/{id}/package` | 指定接收人/设备会话、固定manifestId、分页/块游标 | 原始事实、授权链、审计覆盖、计划/库存、处置及签名准备票据；大数据固定页，不返回原设备私钥 |
| POST `/pda/handovers/{id}/prepare-ack` | manifestId/hash、各文件核验摘要、localStagingId、expectedCaseVersion | 保存接收机“资料已耐久暂存”确认；不改变服务端可写资格 |
| POST `/pda/handovers/{id}/activate` | 原commandId、manifestHash、preparedReceiptId、实物核对凭据、expectedCaseVersion、expectedFromRound、expectedMappingVersion | 锁后复核；返回同一targetQualification、startOrderScanSeq、toRound、签名激活回执。FORMAL新资格首次成功起算48小时；TEMP票据不刷新通用授权期限 |
| GET `/handovers/{id}/commands/{commandId}` | 原动作身份 | 查停止/批准/激活原结果；未找到不授予换ID权限，不跨恢复代次自动重放 |

这些是角色和语义接口，可在定稿时复用现有召回文件传输基础，但**不能放宽旧召回端点的原人原机校验来兼容代传**。恢复模式下证据接收需R09专用受控入口；当前`RecallAccess`在非NORMAL中会拒绝写入，本提案不声称已能调用。

准备票据与激活回执使用不同固定签名用途（候选aud分别为`acrylic-pda-handover-prepare`与`acrylic-pda-handover-activation`），沿用固定算法/受信keyId策略。签名覆盖规范化完整正文，绑定案件、接收人/设备、双方资格、全部摘要、高水位、轮次、代次及有效期；不能用准备票据当扫码授权，也不能用本次签发时间重算原记录的合法窗口。

服务端将manifest和逐行清单作为有约束的权威集合，客户端不能提交“我下载了100包”代替逐字节摘要校验。签名有效不代表证据完整或实物相符，三个条件分别检查。

`fromQualification/targetQualification`为提案中的带种类结构：FORMAL引用真实assignment及离线授权；TEMP原assignment为null，引用真实临时授权，接收端另含scope/轮次/计划绑定的continuationGrant。新票据不代替通用TEMP资格，两者取较早到期点，需续期则走独立受控命令。`source`沿R06的TEMP原创建机/clientOrderId定义；`temporaryOrderId=scopeId`为R06草案建议。`canonicalMappingVersion/expectedMappingVersion`用于正式目标，`mappingRevision`仅用于产品映射。详见[联审对照](CROSS_TASK_ALIGNMENT.md)，这些名称尚未成为正式契约。

## 3. 更正、结果与对账候选接口

| 方法/路径（提案） | 请求关键字段 | 响应/规则 |
| --- | --- | --- |
| POST `/orders/{id}/corrections` | correctionId、type、expectedResultVersion、reason、lines及evidenceIds、逐行businessOccurredAt | 创建固定待审凭据；返回documentSha256及服务端重算预览，不改结果 |
| GET `/corrections/{id}` | 版本/固定明细游标 | 原值、当前版本前值、目标、预计净影响、实际账务贡献、证据和审批历史 |
| POST `/corrections/{id}/decisions` | decision、documentSha256、expectedResultVersion、expectedLedgerVersion及包状态版本集合、reason | APPROVE事务生效或REJECT留痕；响应含旧/新版本、原/累计/净数量重量、effects、实际ledger引用、cycleReopenings及available状态 |
| GET `/orders/{id}/results/{version}` | 精确版本；单独current入口可解析一次当前版本 | 不可变结果及header、原来源、调整状态、期间时间。净0不是订单不存在 |
| GET `/orders/{id}/scans` | view=ORIGINAL/EFFECTIVE、resultVersion、sort=SCAN_SEQUENCE_ASC、cursor | 固定版本/筛选游标，原sequence及来源。EFFECTIVE可显示全部含isEffective或显式仅有效筛选 |
| GET `/orders/{id}/adjustments` | throughResultVersion、游标 | 每effect一条，嵌套/另页查询账务，不按ledger行数重复计包 |
| POST `/inventory/reconciliations` | 案件ID、差异全集、expectedSnapshot/stock/cycle/ledger版本、证据、逐行拟处理结论 | 仅待审；不能直接设置available或bookStatus |
| POST `/inventory/reconciliations/{id}/decisions` | 文档摘要、expectedCaseVersion、批准/拒绝、核验凭据 | 重验原事实后显式补扣/反向/解除已解决隔离/关闭差异，返回每行仍存阻断 |
| GET `/inventory/reconciliations/{id}` | 固定版本/页游标 | 上游、实物占用、出库事实、账务四列及缺证据提示 |
| GET `/corrections/{id}/commands/{commandId}`、GET `/inventory/reconciliations/{id}/commands/{commandId}` | 原请求身份 | 权限内恢复原回执，不重复审批、不把404当失败终态 |

R08的summary/export snapshot接口由R08定稿；R07提供指定结果版本、effect、原事实和账务版本的只读模型。[时间及版本规则](CORRECTION_RESULT_DESIGN.md)是接口必须携带的口径，不能只返回一个会随更正变化的count。R07候选参数用view区分原始/有效/调整，dateBasis区分出库日/发生日；R08模块当前basis仅表示原始/有效，日期只支持出库日。适配器按cutoff筛选已生效版本、精确转换BIGINT与上海日界；PC渠道和缺顺序的标准导出尚有阻断，不能直接透传候选API正文。

## 4. 权限提案与审批条件

| 能力 | 已有/候选权限 | 条件 |
| --- | --- | --- |
| 申请停止 | 已有order:recall；换机另拟order:handover | 只作用本仓PDA活动作业，范围内双方可见 |
| 普通交接审批 | 拟order:handover + 已有order:resume | 实物、完整证据及明确原结果；不能代替短装/卸货权限 |
| 故障强制接管裁决 | 拟order:force_takeover | 独立授权，实物隔离及停止使用证据；审计缺口不可强行标完整 |
| 代传证据 | 已有pda:recover基础 + 拟evidence:relay授权凭据 | 限案件、来源、文件及期限；不能代表原人作出保存/停止/出库决定 |
| 接收/激活 | 当前PDA登录、相关仓库操作权限、清单指定人/设备 | 服务端从会话判身份，body身份仅用于绑定比对；换仓/停用后重验 |
| 出库更正 | V2.3拟outbound:correct，基线未落地 | 只改已成功结果；审批岗位已于2026-09-26确认为主管（原D01），权限命名与实现仍待落地 |
| 库存对账/解隔离 | 拟inventory:reconcile；涉及出库更正还需outbound:correct | 导入/发布权限不隐含更正，order:dispose不隐含已出库更正 |
| 灾后重建/解除恢复 | V2.3拟recovery:resolve及释放权限 | R09受控入口；普通交接主管不可解除全局RECONCILING |

不自动赋予这些新权限，不沿用临时单direct-confirm越过专项审批。审批和生效时均重验权限版本/人员设备状态，审批快照保留用于历史解释；变更后原批准不能无条件继续生效。

## 5. 数据约束与事务边界

设计约束最终需数据库唯一/FK/CHECK与服务事务共同保证，跨表同仓、同订单、同周期不能宣称只靠CHECK完成。

- 每scope/原轮次仅一个活动交接案件；canonical存在时亦受唯一活动槽约束。由当前案件控制行/指针及锁保证，历史拒绝/撤回案件不删除。一个案件最多一个激活资格，一个激活资格最多一份handover_manifest；FORMAL为assignment，TEMP为来源续作票据，不能强制后者非空assignment。
- 清单行主键`(handoverId,captureId)`及`(handoverId,orderScanSeq)`唯一；全局原来源+设备序号、原事件+localSeq、原UUID继续唯一；同证据经多跳出现是合法复用，不重复记原事实。
- 交接输入高水位覆盖所有输入终态；审计覆盖按设备独立，不把localSeq最大值当orderScanSeq。保存事件定位必须用`(originDeviceId,localSeq)`。
- 当前结果指针外键到同单版本。每个生效correctionId对应一次新版本，每个correctionLineId对应一次effect；每dispatch一次恢复cycle，ledger完整反向引用唯一。
- 首次成功保持count>0、weight>0；更正版本允许0且逐包非负。每个归零effect不能再次归零；与实物处置/重复恢复互斥。
- 占用按包全局唯一，不按渠道/周期分开容许两个当前占用；历史dispatch按`(baleId,cycle)`唯一。库存公开视图必须同时考虑未扣账但已出库事实。

统一锁序提案沿用现有前缀：恢复控制共享锁 → 仓库发布/身份锁（涉及多个设备/用户按稳定ID排序）→来源scope（多个按ID）→订单/提交槽 →原提交/案件/批准/结果控制头 →包ID排序的bale/position/commitment及账务贡献。最终实现须与现有PC、PDA提交、R05续期、R06关联和库存发布实际锁序一同核对，不各自新增相反路径。

TEMP未正式化时先核验来源并按唯一键取得scope，不凭空锁一张正式单；多scope/order/submission先收集集合并分别排序。锁内canonical变化导致锁集合扩展时回滚重取。关联与交接共用来源控制行和映射版本，不能各自只锁本案件而放过目标领取竞争。

| 事务 | 必须原子包含 | 事务外工作 |
| --- | --- | --- |
| 原机停止 | 本来源旧资格/轮次封锁、停止回执、案件状态、审计/outbox | 大文件先暂存及校验；未耐久文件不可被引用为完整；设备级停用另核对受影响来源全集 |
| 交接激活 | 再校验、旧轮次终结/新轮次唯一指针、新资格、保留包直接转占用、签名回执、审计/outbox | 接收机下载；批准不释放，文件未齐不激活 |
| 新机本地激活 | 校验暂存/签名、导入来源事实、guard、计数器、汇总、资格和完成标记 | 下载与验签可准备；提交失败重开仍不可扫 |
| 更正生效 | 凭据/effect、实际ledger、完整结果快照/指针、dispatch调整、周期/占用、审计/outbox、固定回执 | 附件先完成受控上传、权限核验及hash |
| 库存对账 | 锁后重验版本、账务/阻断变更、案件结论、状态outbox、回执 | 盘点/收货在现场完成并留证，不用事务模拟实际发生 |

大文件以固定清单/摘要和原子可见引用保护，不靠跨数据库和文件系统事务假设；文件尚缺、摘要不符或不能读取时阻断。异步发布仅发布已提交outbox，失败重试不重做业务命令。签名/审计/outbox插入任一步失败应整事务回滚。

## 6. 兼容与迁移准备（只设计，不执行）

| 现有结构/行为 | 需要准备的兼容改动 | 迁移/回退门槛 |
| --- | --- | --- |
| V9/V15结果表绑定唯一PDA/PC提交，count/weight>0 | 独立保留首次来源唯一关系，允许同原结果追加更正版本；新增sourceCorrection及逐包快照 | 回填v1只能从实际dispatch/原回执复算；差异阻断。不得删除原来源或为v2伪造提交 |
| ledger的dispatch唯一且delta仅-1/负重量 | 拆为每dispatch多笔，有entryKind、业务键、correction/reconciliation FK及reversesEntry唯一 | 旧扣减标成真实原扣减，字节/数量不变；新代码全部使用显式列，现有`INSERT ... VALUES`须兼容修订后才启用 |
| bale_commitment仅现有owner/CHECK分支 | 新交接/恢复隔离owner需明确FK及互斥条件；优先复用有证据的保留占用 | 无空档转移，PC/PDA同包争用回归；不先drop唯一约束再补数据 |
| 单来源SubmissionEvidence/RecoveryAudit | 新证据schema能力显式协商，逐来源验证及授权历史链，支持交接来源成员关系 | 保留schemaVersion=2旧正文原字节与原验证路径；旧客户端拒收新清单，不自动降级为原机续作 |
| Room单作业容器及原机续作 | 新增交接暂存、清单、来源关系、激活回执/命令记录；容器ID与originAssignment分离 | 原机/接收机都有存量、同包guard冲突、同来源同序号、重复导入、损坏大正文及回滚必须验证 |
| 数字恢复代次及旧JWT | 不透明读取抽象及明确新协议；旧源代次逐条保留 | R09协调迁移，不能直接放宽正则后宣称UUID灾后恢复已完成 |

沿用本项目MySQL约定：UUID/时间/重量格式与基线兼容（BINARY(16)、UTC DATETIME(3)、BIGINT克；既有摘要CHAR(64)不可无依据重写）。通用数据库建议不用于把项目改成PostgreSQL或更换ID系统。每条可空字段须有业务原因（如header行无dispatch、未批准无approvedAt），历史引用采用限制删除。

实施时再选择最新可用迁移编号，保留旧Flyway校验和；先追加兼容结构、分批回填/核验，再启用功能/约束。含跨设备资料和新结果的库不可用旧APK/服务端覆盖或破坏性降级；回退默认关闭新写入口、保留新事实，按受测恢复方案操作。未通过有存量升级及服务版本兼容验证，不开放功能开关。

## 7. 错误与原请求恢复

| 候选业务码 | 客户端下一步 | 自动重试边界 |
| --- | --- | --- |
| HANDOVER_STOP_REQUIRED / EVIDENCE_INCOMPLETE / ORDER_SEQUENCE_UNKNOWN | 展示缺停止、缺文件/审计或缺高水位，原案件补证据 | 不能自动批准或改高水位 |
| HANDOVER_TARGET_MISMATCH / PERMISSION_CHANGED | 指定人员/设备核对；需要改目标须新方案审批 | 重新登录不改变清单目标 |
| SOURCE_MAPPING_CHANGED / SOURCE_NOT_RECOVERED | 原案件补齐来源；映射变化重取固定方案并审批 | 不造正式单、不换creator、不直接改旧manifest版本 |
| ORIGINAL_RESULT_UNKNOWN | 查询原提交/命令及归档，保留占用 | 不生成新ID，不“强制忽略” |
| MANIFEST_HASH_MISMATCH / AUDIT_CONTENT_MISMATCH | 停止激活，保留两侧证据，重新下载同一固定文件或转恢复 | 不以重新计算hash接受损坏内容 |
| LOCAL_GUARD_CONFLICT / RETAINED_SET_MISMATCH | 原机/接收机/实物对账 | 不清本地guard/释放其他订单 |
| RESULT_VERSION_CONFLICT / STOCK_VERSION_CONFLICT / LEDGER_VERSION_CONFLICT | 展示当前版本差异，重新核对并发新申请 | 不自动替换expected版本重复批准 |
| DISPATCH_CYCLE_CONFLICT | 固定旧输入和原周期，受控对账 | 不把旧输入升级周期重传 |
| SERVER_RECOVERY_EPOCH_MISMATCH（基线）/ RECOVERY_EPOCH_MISMATCH（V2.3） | 同映射为持久RECONCILING，保留原请求/正文/guard | 禁止用新header重试旧业务命令 |
| SERVER_RECOVERY_REQUIRED（基线）/ SERVER_RECOVERING（V2.3） | 转专用恢复查询/证据流程 | 不在普通入口无限重试 |
| 网络超时/5xx/响应验签或落盘失败 | 客户端RESULT_UNKNOWN，原命令查询及同键恢复 | 退避、有界次数、人工入口；后台恢复不自动审批/激活 |

真实接口定稿需明确稳定错误码、可见证据和资源上限；本表是语义要求，不能仅修改UI字典就宣称后端具备阻断能力。
