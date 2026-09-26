# R07给主线、R08、R09的来源、结果与引用契约提案

日期：2026-09-20。**本文是语义契约，不是已发布OpenAPI、DDL或可调用服务。** 已有字段按87e1893核对；候选字段待主线在R06 M1/M2提交后映射。原签名正文保持原字段、原类型与原字节，不用本提案统一重序列化历史资料。

## 1. 来源与资格：已有字段到候选读模型

| 语义 | 87e1893实际字段/约束 | R07及消费方要求 |
| --- | --- | --- |
| 正式PDA作业来源 | 提交 `source={kind:FORMAL,sourceOrderId}`；`assignmentId`、`freezeDeviceId`、`workRound`、`continuationId`、`sourceRecoveryEpoch` | 保留sourceOrderId和每条原assignment/authorization。顶层freezeDevice不是全部历史行的origin；source种类和采集渠道分开 |
| TEMP稳定来源 | 审计 `source={kind:TEMP,creatorDeviceId,clientOrderId}`；V31主键`temporary_work_origin(creator_device_id,client_order_id)`，绑定首次`authorization_id` | 复用此唯一键；不能用接收机或新正式单替换creator/clientOrderId。读模型另给canonicalOrderId可空及映射版本，后两者仍待M1/M2定稿 |
| TEMP计划/资格 | `TemporaryDraft.authorizationId/revision/planSha256`；`TemporaryLine.deliveryCode/productId/plannedCount`；准备identity含authorizationEpoch/userAuthVersion/deviceVersion/serverRecoveryEpoch | 原计划及准备manifest字节/hash固定。产品mappingRevision不是正式单关联版本；新准备不改旧来源。不能将TEMP authorizationEpoch当FORMAL assignmentEpoch |
| TEMP原采集/保存 | `TemporaryCapture.id/originDeviceId/operatorId/orderScanSeq/deviceCaptureSeq/authorizationId/authorizationEpoch/snapshotId/sourceRecoveryEpoch/orderRevision/expectedDispatchCycle`；`TemporaryScan.captureId/localSeq/savedAt/weightGrams/rawWeight` | 继承真实ID及原资格/周期，新增行用接收机真实来源。当前服务端TEMP审计要求creator=origin=认证设备；跨机新行必须经新schema/票据验证，不能改creator或放宽普通入口来通过 |
| FORMAL原采集/保存 | `Capture.id/assignmentId/authorizationId/assignmentEpoch/orderId/originDeviceId/operatorId/orderScanSeq/deviceCaptureSeq`；Scan用`captureId`，没有独立scan UUID | capture/event UUID与来源复合序号一起验证；字段不改名进入历史正文。全量capture高水位含失败/撤销，不能取成功dispatch的max代替 |
| PC来源 | `dispatch_record.pc_submission_id/pc_entry_id/order_scan_seq`；PC冻结workId、entry sequence/operatorName | R07明确channel=PC、pcWorkId/pcEntryId；不制造设备签名或PDA资格。R08模块内部`pc-work:<id>`只是投影命名空间 |
| 审计连续覆盖 | 普通`POST /api/v1/pda/audit/events`；ack=`recoveryEpoch/originDeviceId/acknowledgedThrough/batchSha256` | acknowledgedThrough是连续前缀，非本批最大号；没有已提交通用GET checkpoint接口。来源设备各有独立范围；A/B的localSeq=1不冲突，也不合成一个序号 |
| 审计归档前缀 | `POST /api/v1/pda/audit/archives`及`/restore`；proof=`afterSeq/throughSeq/payloadSha256/byteCount/signedArchive/recoveryEpoch` | 区间语义为(afterSeq,throughSeq]；保留历史proof、文件与当前代次确认。文件收到/ack不等于停止、业务恢复或授权释放 |
| 可用状态 | BARCODE baseline用`rowVersion`，event用`entityVersion`，均含`dispatchCycle/status`；streamType不是BALE | 可投影为stockStateVersion，但不能更改历史字段。未来reopeningId/完整证明未实现；新周期＋AVAILABLE也不能直接清guard |
| 正式展示编号 | `outbound_order.order_no`、`OrderNumbers`及V32 `order_number_counter(business_date,last_sequence)` | UUID才是引用身份；新FH号按上海日期和全局日流水，旧OB号保留。不要解析编号替代来源或重新给恢复订单编号 |

候选`WorkSourceRef`包含source原键、warehouse、可空canonicalOrderId及映射版本；候选`ActiveWorkRef`包含当前资格、workRound和当前提交槽；候选`OriginRecordRef`包含每条原资格/设备/人员/序号/hash。三者明确分开。scopeId如最终采用，仅是服务端解析标识，不能由clientOrderId/authorizationId直接冒充；一份TEMP资格可支持多个来源。

未正式化、已正式化但待确认、已成功是三种不同事实。只有成功才能生成R08原确认结果及进入已成功更正。R07停止/批准/激活以及TEMP准备ack均不能显示“已出库/可离场”。

## 2. R07提供的只读业务对象

| 候选对象 | 最小内容 | 一致性要求 |
| --- | --- | --- |
| SourceView | 原source、当前canonical映射及版本、原资格/准备/计划引用、渠道 | 映射历史不可覆盖；别名解析到同一活动来源，权限仍按仓库/对象检查 |
| SequenceProof | source、captureHighWatermark、全部原capture/event摘要集合引用、分来源auditCoverage、各跳manifest/stop/activation引用、验证状态 | status完整才可给VERIFIED_HANDOVER；没有高水位的最近max只作下界。可分页，但成员/摘要固定，不以时间排序补顺序 |
| OriginalResult | orderId/resultVersion=1、原提交/成功回执及正文hash、header/产品、每dispatch来源/周期/原序号/重量、shipmentConfirmedAt | 永远与原成功相符；PC无PDA signedReceipt时保存其真实成功回执和正文证明，不假造签名 |
| CorrectionEffect | effectId、correctionId/lineId、from/toResultVersion、dispatchId可空、before/after及countDelta/weightDelta、businessOccurredAt/effectiveAt、证据引用 | 每行一次effect；HEADER为0/0；与ledger为0..N关系，不JOIN展开重复计包 |
| ResultVersion | orderId/version、前版及来源凭据、原出库时点、版本header、全部原dispatch当前贡献、产品/整单净值、createdAt/effectiveAt | 包括归零行，v1+effects=逐包净值=产品/整单；净0合法，原订单仍COMPLETED |
| LedgerContribution | dispatchId、ledgerVersion/截点、原扣/人工补扣/差额/完整反向各行及业务键、reversesEntryId | 只调整实际贡献；完整反向引用唯一；单纯补扣不增加resultVersion |
| CycleProof | reopeningId、originalDispatchId、correctionLineId、before/afterCycle、证据/签名用途/代次、包状态版本、剩余阻断 | 一次恢复只推进一次cycle；cycle与可用性分开；不删除旧dispatch或旧guard证据 |

版本一律明确所属实体：resultVersion、workRound、映射版本、计划revision、授权epoch、dispatchCycle、库存/账务版本和恢复代次互不替代。新契约BIGINT用十进制字符串；现有orderRevision/schemaVersion等整数类型保留兼容，不能因“统一字符串”改写旧正文。恢复代次目前是数字字符串，新模型按不透明值判等；R09 UUID升级须显式版本化。

所有读取固定对象版本/成员/顺序及游标，翻页时版本变化不能拼接结果。命令发出前固定原ID/正文/hash/expected版本；响应失踪沿原命令查找。权限撤销后重放也受当前对象范围控制，404本身不是可换ID重新做的证据。

## 3. 交给R08：快照、时间和展示语义

R07提供可信业务投影，R08负责固定成员快照、渲染、任务/下载及文件版本；R08不重新决定退货、重算账务或从ledger推断净结果。

| R07输出 | R08消费与拒绝条件 |
| --- | --- |
| 原结果/净结果/调整分层 | ORIGINAL→ORIGINAL_CONFIRMED；EFFECTIVE→CURRENT_EFFECTIVE；ADJUSTMENTS为独立明细，不冒充现有basis枚举。当前已接入闭环仅正式单原确认v1 |
| 固定snapshotId/dataVersion/cutoff、成员、每单resultVersion/header/revision | snapshot形成时确定截至cutoff已批准生效的版本；生成/重试不再读latest。含账务另固定ledgerVersion/账务截点；缺任一链拒绝发布 |
| shipmentConfirmedAt、businessOccurredAt、effectiveAt/postedAt | 默认SHIPMENT_DATE_NET按上海原出库日选单、展示固定快照内最新净值。20日收货21日批准，20日末快照不含该调整；事后MOVEMENT_DATE可归20日并另列21日生效 |
| 日期范围 | UTC上海自然日半开[from,to)可转换成模块包含首尾的fromDate/throughDate；非整日不能截断。观察模块尚不支持MOVEMENT_DATE，保留为待接视图，不改标题冒充支持 |
| 原序号、渠道/原人员、SequenceProof | 原明细用orderScanSeq排序，跨产品不分组，缺顺序阻断标准精确导出。单来源confirmed-v1入口要求原设备等于freezeDeviceId，多设备要独立适配路径，不能放宽校验就接入 |
| PC及TEMP来源 | PC保持pcEntry/work语义；既有R08 PC原确认适配已有证据，不再列为全缺。TEMP即使已有FH目标仍保留TEMP来源；关联目标不把临时明细改成FORMAL原采集 |
| displayRowNo与原序号 | 用户指定六列SCAN_XLSX从1连续显示，原orderScanSeq仍保留在私有快照/通用明细；不因显示编号连续而改原记录。该模板当前仅原确认，不将其自动变为净结果模板 |
| 姓名及时间质量 | PC保留冻结operatorName；PDA六列模板按既有需求固定导出申请时中文名并保存原ID/来源说明，不能声称扫码时姓名。通用文件可保留原ID；设备时刻质量仍DEVICE_UNVERIFIED/MISSING，不擅自校钟 |
| exact数值与零行 | API字符串经可信服务端精确解析模块所需long，范围溢出拒绝；重量按克精确换算，rawWeight另存。退货0贡献行保留；无净行不等于没有原事实 |

每个发布快照需有内部来源清单：producer提交/协议版本、仓库、source/结果版本集合、原正文/回执hash、所选effect链、顺序证明、批准截点校验证据；访问记录不暴露受限附件。模块目前没有effectiveAt字段时，由服务端适配器验证并在私有清单固定，不能静默丢弃该检查。

原文件不可被更正版覆盖。R08下载URL/本地文件路径是传输定位，不是R09业务证据ID。导出结果是否可重新生成由固定快照与对应模板/渲染版本证明；删除临时下载文件不得级联删除业务来源、快照或更正凭据。

## 4. 交给R09：证据接收和恢复状态

| R09负责输入/输出 | R07必须提供/遵守 |
| --- | --- |
| RecoveryContext：当前外部代次、mode、sessionId、受影响仓库/设备/来源、版本 | 普通业务写入共同检查；外部控制缺失时不放行。旧sourceRecoveryEpoch/正文原字节保留，不能换header普通重提；日常故障不自行创建新全局代次 |
| EvidenceIntake：固定evidenceId/原kind/原对象ID、源代次、schema、hash/长度、文件parts、transportActor/许可；当前代次接收回执 | 代传不改变原签名/人员/设备；相同ID同摘要复用，异文整项冲突。采用独立受控证据入口，不删除原机普通接口身份校验 |
| EvidenceStatus：当前代次RECEIVED/VERIFIED/INCOMPLETE/CONFLICT及缺失引用/区间 | 收到文件只表示耐久接收；R07输出业务校验报告和可重建原事实，不把接收状态当APPLIED或RELEASED |
| ReconstructionResult：案件版本、原ID集合、已应用/已存在/冲突项、核验摘要 | 专用幂等恢复事务分别恢复原成功/取消、交接资格和更正链；返回每项实际结果。恢复到激活前不得向B再签第二资格；成功回执仍对v1 |
| ScopeRelease：当前代次/会话/范围、已核对设备和案件摘要、未联系设备/未知范围、签名和版本 | 未解范围继续隔离；仅该证明及当前资格条件允许后续授权。本地RECONCILING不因登录、基线、普通audit restore或单案件RESOLVED清除 |

以上名称为候选语义，R09具体端点/签名aud由主线与R09定稿，不新增“只要主管确认即可绕过”入口。普通故障完整证据可在当前代次处理；恢复控制缺失只影响需要该能力的路径，不能用“完整R09尚缺”笼统否定R05已交付归档。

R09恢复检查至少覆盖：V31原资格绑定与混合连续审计；V32订单计数器及订单一致性；R06最终source映射/提交槽/成功和取消；每跳停止/准备/激活与来源资格；v1→各effect→每版净结果；实际ledger及完整反向唯一性；reopening→新周期→新dispatch；全文件/历史公钥/签名用途及投影/检查点。编号计数器须与同截点订单一致，跨截点重建也不得倒退到可重复签发已用编号；不得重编号修复签名。

恢复检查按真实schema版本选择，不在旧V21/V28检查器中删规则以接受新结构。备份回装与截点后事实重建分别验收；只恢复旧库但缺后来ABORT/更正/激活证据时，未知范围仍不能释放。

## 5. 引用与留存契约

候选`EvidenceRef`最少包含：kind、原objectId、不可变版本/schema、warehouse/source、sourceRecoveryEpoch、sha256、bytes、签名用途/keyId（适用时）、受控存储标识、retainUntil、验证状态。`ReferenceEdge`包含ownerKind/id/version、targetRef、用途及建立/解除依据。状态/当前代次验证另存，不改旧证明。

| 引用根/持有者 | 必须可追溯和取回的目标 | 解除条件与责任 |
| --- | --- | --- |
| 在途交接/新机暂存/未知激活（R07） | 原资格/准备/计划、全来源capture/event、审计前缀、停止/故障、manifest/parts、审批、原命令/回执及保留包集合 | 未知或未完成不得解除。完成后业务关系转为历史留存，不能把“没有活动案件”当作整链可删 |
| 成功原结果/更正/新周期（R07） | 原提交及成功回执、dispatch、全部凭据/effects/各版快照、真实ledger/反向关系、reopening、后续dispatch及隔离处置 | 原事实和调整链至少按云端5年保留，活动引用更长时继续保护；不得删v1或仅保留current |
| 固定导出快照及排队/重试任务（R08） | 指定版本来源清单、原/净结果、effects、顺序证明、模板/渲染版本、必要账务截点 | 临时文件到期仅解除文件用途；数据快照/业务引用按其生命周期单独处理。任务结束不自动删原业务事实 |
| 恢复会话/缺证据案件（R09） | 备份manifest、外部代次、设备清单、原终态/调整/交接链、取回失败项及历史验签公钥 | 只有明确范围核对及有效释放证明可结束活动恢复引用；未联系设备/未知影响范围继续保护 |
| PDA本地事实/有效guard（R05消费R07判定） | 原正文/回执/审计归档、跨设备继承及当前周期引用、可信时间和云端可取回证明 | 90天只是必要条件；同时无在途/未知/有效防重/未决恢复且完整恢复及取回证据已受测后，另开发整单清理。当前始终false |

新增持有关系必须与业务状态在同一事务固定，或先耐久上传再原子发布引用；不可先发布成功引用后异步补文件。清理判定与新增引用竞争必须锁定或使用版本栅栏复核，不按“先查无引用、稍后删除”的无保护窗口执行。相同hash的文件可被多个对象引用，删除要核对全部持有者及留存边界。

R07提供按对象分页的引用闭包和阻断清单，R08登记快照/任务所持引用，R09核对DB到文件、签名钥及反向悬空引用。技术细节可由实现选择，但必须证明固定版本分页不漏引用、文件可读取且长度/hash/签名匹配。只列一个S3/磁盘路径或证明备份压缩包存在均不够。

## 6. 主线接入必须补的契约证据

1. R06 M1/M2的提交ID、TEMP原控制头/终态查询/正式化或关联的真实字段与并发证据，替换本文候选scope/映射术语；确认拒绝/卸货与跨机停止怎样串行。
2. R07多来源证据schema、逐来源审计/资格/完整高水位验签夹具，以及旧单来源/原签名字节的兼容证明；普通审计不直接承担代传。
3. 指定结果版本/批准截点/逐行effects/真实ledger独立复算的消费夹具。R08既有PC与六列模板口径保留，多设备/TEMP/净结果另验。
4. R09对应新schema的恢复清单与四个截点演练（成功、取消、更正、交接激活之前），完整引用/可取回/范围释放证据。缺失项见[依赖与政策](DEPENDENCIES.md)，未满足者只阻断对应批次。
