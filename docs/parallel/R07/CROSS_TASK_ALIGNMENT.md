# R07跨任务联审与接入门槛

2026-09-18续接设计记录。本文只修订R07提案，不是共同定稿、正式API或运行验证。四个关联目录当时的HEAD均为 `2369392076083a5a4f59d8ea3d6edebe5067a03f`；所读新增资料/代码尚未提交，不能当作本分支已实现能力，也不能将其验证记录归入R07。

## 1. 观察范围与证据快照

| 别名 | 只读目录 | 本次相关资料 |
| --- | --- | --- |
| R05 | `D:\acrylic-pda-outbound` | 最新路线/进度、升级方案及 `android/app/src/main/java/com/acrylic/outbound/pda/update/`；原目录还在开发 |
| R06 | `D:\acrylic-pda-worktrees\r06-design` | `docs/parallel/R06/DESIGN.md`、DEPENDENCIES、PROGRESS |
| R08 | `D:\acrylic-pda-worktrees\r08-export` | `docs/parallel/R08/INTEGRATION.md`；`report-rendering/src/main/java/com/acrylic/outbound/reports/` 的输入记录、校验器及输出标签 |
| R09 | `D:\acrylic-pda-worktrees\r09-ops` | `docs/parallel/R09/BACKUP_AND_RESTORE.md`、DEPENDENCIES及 `ops/consistency.py`、`ops/receipts.py` |

以下SHA-256固定本次已观察内容，文件随后可能继续变化；编码前重取实际提交/契约，不追认后续内容为本次已核对。路径按上表目录展开。

| 输入 | SHA-256 |
| --- | --- |
| R06 `docs/parallel/R06/DESIGN.md` | `3740c6cc76c9ab95d590329feaf08ef74efb745edb4956a06d10c73a22af4fd8` |
| R08 `docs/parallel/R08/INTEGRATION.md` | `34dff27a0d837b3b90ca092297f7e5c365d6d19f21fc5c9489b2be3a60647bcb` |
| R08 `report-rendering/src/main/java/com/acrylic/outbound/reports/ReportSnapshot.java` | `fa92dd8333fa05dd28f1b326bd76eebbd632ac2e10c8421e6aeb818397021fdd` |
| R08 同目录 `AdjustmentValidator.java` | `3f64c7a0779251d5593eaf949811048a702a10f0eb11848308d27ed75624e2f8` |
| R09 `docs/parallel/R09/ops/consistency.py` | `0f0568a5191c8cf9ef68876796c0f4c0d4a264cfa2307d552f0540b5451c5dd0` |
| R09 `docs/parallel/R09/ops/receipts.py` | `4f3ce16b2ce55487cf2e9bfebfdc237698c32454b82fba298f1ea63fa9674685` |
| R05 `android/app/src/main/java/com/acrylic/outbound/pda/update/UpdateSafety.kt` | `0fe2de468251d1b47e088a0277371db2a0c6730cc0e90c980a36ce22777d3c61` |

## 2. R06：先有临时来源，未必已有正式订单

### 2.1 身份、资格和停止范围

| 概念 | R06草案事实 | R07接入提案 |
| --- | --- | --- |
| 稳定来源 | `source={kind:TEMP,creatorDeviceId,clientOrderId}` | 每一跳保持原创建机；transportDevice、originDevice和当前接收机分别保存 |
| 服务端资源 | `temporaryOrderId=scopeId`（提案） | `/temporary-orders/{scopeId}/handovers`；不拿clientOrderId充当scopeId |
| 正式映射 | 未正式化时canonicalOrderId=null、canonicalMappingVersion=0 | 可以恢复来源、停止、审批和交接；不制造正式单来满足接口非空限制 |
| 本地容器 | localWorkId独立；旧FORMAL容器保留原assignmentId | B建自身容器，导入来源关系；不能把B容器ID写成A的原授权ID |
| 原TEMP资格 | assignmentId/originAssignmentId=null；有真实TEMP授权ID/代次 | 清单按来源种类保存资格；不补造FORMAL assignment |
| 接收资格 | 原有TEMP授权绑定设备/人员/仓库，可覆盖多个临时来源 | 当前接收者的有效TEMP资格 + 绑定本scope/轮次/计划的交接续作票据；二者均须有效 |
| 版本 | canonicalMappingVersion；命令expectedMappingVersion；产品mappingRevision另有含义 | 清单、批准、准备和激活全部绑定正式映射版本，产品映射另列 |

仅本地存在的TEMP，先经R06受控恢复入口校验原计划、授权、来源和完整证据，建立唯一scope；此动作既不是成功出库，也不要求先伪造一份冻结/ABORT批次。无正文、无授权、无高水位的来源只能登记缺口/隔离，不能创建可激活清单。

停止的最小边界是 `scopeId + fromRound + 原作业资格`。原设备持久停止该来源，服务端封锁该来源旧轮次的新增/普通确认；保留受控证据接收。TEMP通用授权可能仍被同机其他临时作业引用，不能因交接一单就无记录地撤销所有来源。若故障要求设备级停用，需列出其全部受影响作业并逐项隔离/对账，不能只关闭当前案件便声称其他单安全。

FORMAL激活签发新的固定48小时资格。TEMP交接票据不得刷新通用TEMP授权的原到期时间；有效截止不晚于接收者TEMP授权及交接票据二者的较早到期点。到期先走独立受控续期，再核验原清单/计划/映射；续期保留旧凭据，不能用创建临时单或重试激活延长48小时。具体票据字段是P06接口门槛，不能直接复用FORMAL assignment端点。

### 2.2 轮次、顺序与关联竞争

换机定义为该来源的**受控续作**：停止/终结旧轮次后，批准只固定expectedFromRound与拟议toRound，成功激活时原子推进一次；下载、重试、映射或准备不加轮次。未冻结过批次也可以有受控关闭记录，不产生假ABORT。TEMP同人同机的拒绝后续作/卸货/全卸关闭由R06负责；跨设备承接才进入R07，成功后更正归R07。

`orderScanSeq`沿稳定来源累计。未正式化时按scope唯一；正式化/关联后canonical目标只投影这一来源的顺序，不重编号，也不能与目标原生scope并存两个有效作业。当前既有FORMAL订单序号约束保留。完整高水位包含失败、放弃、撤销输入，不能只按成功保存行求max。

先收集涉及的来源/目标scope、order、submission和包，分别稳定排序后按共同锁序取得；锁内发现canonical映射变化使锁集合新增对象时，回滚后重新收集，不持锁逆序补锁。映射改变使待审/已批准/已暂存的清单失效；必须重验空目标条件、双方权限/计划/占用并重批。接收端旧暂存作为证据保留，不改原字节后继续激活。目标本机已有真实输入、授权或批次时禁止空缓存替换。

## 3. R08：结果视图、日期口径和快照是三件事

R08观察版本是独立Java/JSON渲染模块，尚非正式API或数据库适配器。其 `basis` 为 `ORIGINAL_CONFIRMED/CURRENT_EFFECTIVE`，日期仅支持 `SHIPMENT_DATE_NET`。R07将日期参数候选名明确为 `dateBasis`，结果视图为 `view`，避免同名异义；不改当前正式契约。

| R07只读模型 | R08观察输入 | 必须完成的适配检查 |
| --- | --- | --- |
| view=ORIGINAL / EFFECTIVE | metadata.basis=ORIGINAL_CONFIRMED / CURRENT_EFFECTIVE | ORIGINAL必须取v1且adjustments为空；EFFECTIVE取已固定版本；ADJUSTMENTS是附页/独立数据，不是第三个basis枚举 |
| dateBasis=SHIPMENT_DATE_NET | 该模板固定规则 | MOVEMENT_DATE必须拒绝此模板或转未来明确支持的模板，不能只改标题 |
| UTC `[from,to)` 的上海自然日 | fromDate、throughDate，均为包含边界的LocalDate | from取上海起日；through取上海to日减1日。非整日边界不能截断后冒充全天 |
| snapshotId、固定成员/顺序、结果版本集合 | metadata.snapshotId/dataVersion/cutoff + orders | EFFECTIVE取cutoff内已生效且有完整链的最新版本，ORIGINAL取v1；从固定快照读取，不边渲染边取最新 |
| 原成功时间 | order.confirmedAt | 永远对应v1；更正/导出生成时间不移动原出库日 |
| 原/各版本header | originalHeader/effectiveHeader | 观察白名单vehicle/container/remark；其他字段尚无模块支持 |
| 全部原dispatch及指定版本贡献 | originalLines + effectiveLines | 一一覆盖；已退行保留0/0，缺行不能被解释为退货 |
| effect及其前值/后值 | adjustments | 从v2到选定版本完整连续链，每凭据行一次；不得从ledger行数生成重复调整 |
| businessOccurredAt | adjustment.occurredAt | 此字段仅实际发生时点；不能把批准时间塞入以通过校验 |
| approvedAt/effectiveAt | 当前模块无对应字段 | 适配器先独立核验不晚于cutoff；原凭据/快照证据中保留，不因模块没字段而删掉 |
| 完整来源顺序证明 | sequenceProof.kind/reference | 单来源持久顺序或已核验交接链；reference必须可解析到固定证据摘要 |
| API BIGINT十进制字符串 | 序号/revision/resultVersion等为Java long，JSON要求数值 | 在可信服务端做精确解析、范围校验再序列化；不得经JS浮点转换或直接透传API JSON |
| 整数克 | weightGrams规范非负十进制字符串 | 模块30位容量不扩大业务BIGINT上限；差额在before/after间表达，不传负目标重量 |

R08校验器要求调整实际发生时间处于confirmedAt至cutoff之间；R07普通路径一致。历史纠错特殊时间若越界，须另定协议/政策后启用，不能替换真实发生时间规避限制。快照cutoff为“知识已生效截至”：原成功、批准生效、所选结果版本及必要证据均应在截点内可证明存在。若灾后重建只能证明发生时间、不能证明当时已批准生效，应阻断历史截至导出或明确使用恢复后的新快照，不能伪造当时已知。

合成边界例：20日09:00收回P，21日10:00批准；20日23:59:59.999的快照仍取v2，无退货effect。21日10:00之后新快照才可取v3。以后按MOVEMENT_DATE查看20日可以显示该退货，并注明21日生效；**观察中的R08模板还不能输出这一movement口径**。18日整日查询转换为 `[2026-09-17T16:00:00Z,2026-09-18T16:00:00Z)`，R08两个LocalDate均为2026-09-18。

### 3.1 不能伪装已兼容的两类来源

1. 多设备原行：R08要求 `VERIFIED_HANDOVER`。渲染器结构检查不等于验签、审计连续和高水位核验；适配器必须从R07完整交接链产生可追溯证明。顺序证据不完整时阻断“精确原顺序”的标准模板；将来可设计独立缺失/损失报告，不能填一个非空reference骗过校验。
2. PC原行：R07事实是pcEntryId及原在线录入sequence。R08观察结构要求captureId、originDeviceId和正deviceCaptureSeq，Excel/PDF还标为“原设备ID/设备序号/输入ID”“原设备/操作人”。INTEGRATION提到 `pc-work:ID` 命名空间，但仅填合成设备名会误示PDA来源。须共同明确渠道字段、PC作业/录入ID语义及输出标签后接入，或采用经审阅的明确PC命名空间和对应标签；当前不能声称PC可直接无损导出。

原人员ID与原显示名快照分开。若历史只留ID，不能以人员当前改名后的名称冒充当时姓名；输出真实ID并说明历史名称未留存，模板字段需允许这一表达。导出附账务时另固定ledgerVersion/账务截点；R08目前的adjustments不能承担完整账务明细。

## 4. R09：V21检查需扩展后才覆盖R07

当前 `consistency.py` 明确拒绝非V21，按原dispatch合计核对每条结果、按inventoryDeducted判定唯一原扣减，并要求每个历史dispatch仍持有当前DISPATCHED占用。这些是观察基线规则；R07追加结果/流水/新周期后不能删掉校验就称恢复成功，也不能让正确退货数据套用旧不变量。

| 恢复校验对象 | 后续R07规则与负例 |
| --- | --- |
| 原成功/ABORT回执 | 固定原submission及原正文摘要；SUCCEEDED只关联不可变v1，不关联当前净结果；ABORT不得出现有效dispatch或复活 |
| 结果链 | v1与原dispatch一致；v2..vN与唯一已生效凭据、逐包effects及版本快照逐级一致；丢v2、重复effect、伪造净0必须失败 |
| 账务链 | 单独聚合原扣/实际补扣/重量差额/唯一完整反向，校验引用和前后有效贡献；无实际扣账不得生成正库存；不能与effects多对多求和 |
| 周期与占用 | 历史dispatch仍占原cycle；合法reopening可使其不再持有当前占用。当前cycle/活动owner唯一，周期增量必须有完整恢复凭据；cycle跳号、双owner、无据释放失败 |
| 交接来源 | 每跳停止/故障、清单、审批、准备、激活与资格绑定完整；所有origin/序号/摘要不改，缺高水位/审计不能标完整 |
| TEMP与canonical | source唯一、映射历史有据、current slot唯一；未正式化scope可存在，无证据的重映射/复制来源失败 |
| 文件与签名 | 恢复包分块长度/SHA和完整清单可取回；原/新签名按固定用途及相应历史keyId验签；错aud、缺钥、附件丢失失败 |
| 发布与放行 | 业务事实、outbox、固定基线与审计checkpoint在当前恢复代次核对；新快照存在不自动解隔离，未联系设备范围仍阻断 |

待扩展的归档manifest至少列出：每来源原资格/续期历史及审计检查点、原输入/冻结/成功/ABORT证据、source/canonical映射；每跳停止或故障证明、审批清单、准备/激活原命令和回执；correction文档/行/effect、每版结果、实际ledger及reversesEntry关系、cycleReopening和隔离处置；文件字节数/SHA/schema/用途/keyId与历史验签公钥。私钥按既有受控运维密钥方案备份，不写入业务交接包或本目录。

保留V21检查用于V21恢复；未来以明确schema能力选对应检查器，未知schema继续失败关闭。新旧检查、负例和恢复前后逐项复算均需实测。R09当前快照还原即便通过，也不等同于库外UUID控制、事实重建或范围释放已实现；本任务没有执行任何ops命令。

## 5. R05：升级安全点与能力协商

只读观察到R05已在开发升级代码。UpdateSafety检查扫码队列/候选、SCANNING、未决输入、未知提交、活动召回和待确认续作；这不是R07状态缺失的现有缺陷，R07尚未实施。本次对正式契约/服务端同步审计/Android同步范围的只读差异核对未见相对基线变化，状态流仍只有ORDER。升级开发进展不代表BALE流或多来源审计已经就绪。

未来R07向升级安全检查提供持久状态和安装互斥，不仅返回一条临时UI提示：

| R07阶段 | 安装/恢复提案 |
| --- | --- |
| 原机正在停扫、核对/冻结证据或写stop-ack | 阻断安装，先到可证明的耐久停止点；保留原输入和请求 |
| 下载/导入/准备写入正在进行 | 与安装意图争用同一业务写入互斥；已开始的事务完成后可暂停，不半迁移资料 |
| 已耐久只读等待审批，或需升级才能识别新清单 | 可以设计“暂停交接并更新”：保存命令/manifest摘要/暂存游标、停扫和guard，停止相关writer；仅当该状态存量迁移受测且无其他更新阻断时安装 |
| activate已发出但结果未知，或服务端成功本机未落盘 | 先查原请求并恢复确定耐久结果；不得自动选新目标或清暂存来安装。原查询不可用则保留阻断，由受控恢复处理 |
| 更新完成首次启动 | 恢复暂停态并重新验证能力、代次、映射和清单；不自动确认接收，不自动解除RECONCILING |

避免形成“清单需新版本才可接收，但必须交接完成才准升级”的闭环：受测的暂停点可以先升级，业务激活继续被阻断。相反，未知结果不能用暂停当成已失败。下载APK与调用系统安装器分开；安装器调用前必须在互斥内再次检查并耐久标记安装意图。

观察到的更新清单 `serverCompatibility` 是描述字符串，不能当成协商的 `handoverSchema/multiOriginEvidence/sourceKind/resultSchema/recoveryEpochFormat` 能力集合。上述候选能力需R05/R06/R09正式定义并验证；不支持时明确阻断，不删除新字段降级传旧schema。

## 6. 本次结论与编码闸门

已能独立确定的技术修订：来源级TEMP交接、映射版本失效、按来源封锁旧轮次、结果视图与日期参数分离、快照批准截点、严格来源证明、R07恢复不变量及可升级暂停点。相应主设计和[I类联审验收场景](ACCEPTANCE_PLAN.md)同步修订。

仍需真实接口/验证才能开启的项：P06资格与来源锁协议、R08 PC渠道输入/标签及历史截至适配、R09扩展schema检查和归档、R05升级迁移保护/BALE流/通用审计。它们归入D05，不将某份未提交草案视为对方承诺。**2026-09-26 更新：D01审批分工（主管）、D02时间/锁期（跨日补录允许3天）、D03故障证据标准（主管同意即可）、D04误批后处置（审批人发起撤销→申请人确认）及附件容量（单文件10MB/单次总量50MB/单条最多5个）已由用户确认为已确认规则**（权威记录：主树 `docs/BUSINESS_POLICY_R07.md`），不再列为未定；本次技术联审仍不代替业务决定，上述规则**均待实现**。
