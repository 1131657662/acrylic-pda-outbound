# R06 临时订单专项设计

日期：2026-09-18；代码基线：`2369392076083a5a4f59d8ea3d6edebe5067a03f`。**本文为建议设计，未实施；出现的新字段、表名、接口和状态都不是当前可调用能力。** 已实现能力仅以 [依赖清单](DEPENDENCIES.md) 的代码证据为准。本轮不修改正式契约或任何业务源码。

## 1. 已确认规则与方案选择

| 规则 | 本设计的落实 |
| --- | --- |
| Q01：每产品必填正整数计划 | 一个或多个不同发货代码，禁止无限量、重复行、0 计划及手改总数；允许混扫 |
| Q02：独立 `temporary_order:confirm` | 可确认正常单，包括本人正常临时单；不隐含短装、冲突、处置、关联或更正权 |
| Q03：确认出库后才离场 | 本机保存、冻结、HTTP 成功、接收、关联或批准动作均不是放行依据 |
| Q04：允许短装/某产品实际 0 | 每个短装产品有可追溯原因，主管批准；整单实际 0 永不确认出库 |
| Q05：48 小时 | 资格固定签发期限，创建新临时单不重新计时；到期不删事实、不放锁 |
| Q06～Q10 | 原周期、原重量/映射及库存版本固定；返库再用须核实实物并走 R07；导入同码不能解禁 |
| Q11～Q13 | 无 116 包限制；本地 90 天、云端至少 5 年，在途/有效防重例外保护；待审 30 分钟提醒不裁决 |
| Q16：B 模式 | 广播校验通过自动保存；手输校验后人工确认；错误暂停后续保存，PASS 只在事务成功后 |

三种实现路线的取舍：

| 路线 | 代价/边界 | 结论 |
| --- | --- | --- |
| 临时来源及资格适配现有作业机制 | 要显式处理正式授权耦合、映射和迁移，但共用一套事实、防重、冻结和最终入账约束 | **建议采用** |
| 离线伪造正式订单/assignment，上传时换 ID | 客户端没有服务端签名；改 ID 会破坏原来源、幂等和回执；已有外键并不证明合法授权 | 不采用 |
| 新建完全独立临时扫码/同步栈 | 两套防重、音效、持久队列和恢复路径易分叉，后续关联更复杂 | 不采用 |

默认仍为 `PHYSICAL_SEPARATION`：不同车不同货堆、本机防重、云端全局裁决；离线不能获知另一台 PDA 的即时行为。严格独占库存池仅保留扩展位，本轮设计不将其变成默认开工要求。

## 2. 身份与固定上下文

### 2.1 稳定身份

| 标识 | 建议定义与不变量 |
| --- | --- |
| `clientOrderId` | PDA 首次持久保存草稿时生成随机 UUID；同次双击/重试重用，同车下一次运输另建 UUID |
| `creatorDeviceId` | 创建时已绑定设备 ID，永久不变；以后代传/换机只变当前设备字段 |
| `source` | `TEMP + creatorDeviceId + clientOrderId`；服务端唯一来源，不以车牌、时间、可读号或当前上传设备判重 |
| `temporaryNo` | 例如 `TMP-设备短名-日期-本地序号`，只作显示；保留完整原号，重启/改时不参与唯一性 |
| `localWorkId` | 建议新增本机稳定作业容器键；临时单取独立 UUID，正式旧容器回填原 assignmentId；不冒充服务端授权 |
| `orderRevision` / `planSha256` | 临时计划版本和规范摘要；进入可扫码状态后固定。草稿更改形成新版本，首次输入后不直接改计划 |
| `workRound` / `submissionId` | 初始轮次 1；一次冻结仅一个 UUID/正文/摘要。仅受控续作增加轮次，扫码序号不归零 |
| `canonicalOrderId` / `canonicalMappingVersion` | 尚未正式化可空/0；正式化为 `PDA_TEMP` 记录后为 1；受控关联增加映射版本。映射独立存储且留历史 |

`assignmentId` 在 TEMP 的协议上下文为 null；`assignmentEpoch` 表示签名临时资格的代次，不能本机随意加一延长授权。`originAssignmentId` 对原临时输入为 null；原正式输入仍保留真实 ID。不能向现有 `PreparedOrder.assignmentJson` 塞一个伪造 JSON 就声称兼容。

本设计中正文叙述的 `mappingVersion` 均指正式目标映射版本，线上的建议全名统一为 `canonicalMappingVersion`；对应命令为 `expectedMappingVersion`，含义固定。产品映射沿用实际字段 `mappingRevision`，不能混用。建议服务端 `temporaryOrderId` 等于 TEMP scope ID，作为 `/temporary-orders/{id}` 的稳定资源标识；它不等于 clientOrderId 或 canonicalOrderId。abort/恢复证据可建立这一来源控制资源而尚无正式出库订单。

只有 `LOCAL_DRAFT` 可自由编辑；“保存并开始装车”事务固定计划和资格。若已开始但完全没有输入、扫码、占用或提交，可记录退出本次空作业并回草稿生成新计划版本；任一原始输入已存在即保留原版本，调整进入联网受控停止/续作。已有记录不通过复制新临时单规避原占用。

### 2.2 独立临时离线资格（建议新增）

已有正式订单 JWT 绑定 order/assignment/plan，不能用于尚不存在的临时订单。建议联网预备时签发设备、人员、仓库范围的 TEMP 资格；创建临时计划时引用它，并保存本机计划摘要及建单审计，不由 PDA 伪造服务器计划签名。

资格签名至少绑定：`authorizationId`、用途 `TEMP_WORK`、`userId`、`deviceId`、`warehouseId`、`authorizationEpoch`、`userAuthVersion`、`deviceVersion`、`serverRecoveryEpoch`、固定库存/映射/条码规则/政策版本及摘要、准备包 ID、`iat`、`exp`（固定 172800 秒）、允许创建/扫描的权限范围。建议沿用 RS256 和既有签名钥管理，独立 audience 防止与正式单资格互换。

服务端签发条件：当前 PDA 会话有效、设备属于该仓库且启用临时作业、人员有 `temporary_order:create` 与既有扫码/提交权限、仓库允许、NORMAL 且无恢复阻断。`temporary_order:confirm` 不作为离线放行声明；最终确认权只在云端实时重验。新建单不要求办公室预建订单，不要求联网逐单领取。

资格与准备包完成验签、摘要/行数校验、权限版本匹配及本机原子激活后才允许开始。下载确认丢响应可重发原确认；本机完整激活可按有效资格离线作业，云端须恢复原包激活证明后才能最终确认，不能通过新包覆盖旧事实。

续期只联网签发新资格并保留旧资格全文/替代关系。对已有临时作业须显式绑定原来源、计划和轮次，未决输入为 0、身份/库存等兼容后激活；不改旧输入的 authorizationId/时间。适配 R05 续期规则，不直接调用正式 assignment 续期接口。

### 2.3 离线资格判定

每次开始、接收和最终保存均复核：当前人员/设备/仓库匹配、资格签名有效且未到期、固定资料完整、计划合法、存储可写、当前轮次允许、无未决跨单输入、无已知停用/撤权/恢复阻断或本单在途未知结果。

- 在线会话短期失效与离线资格到期分别判定；有效离线资格可支持既有身份解锁后的作业。已获知人员/设备撤权、仓库变化则停止新增，不能离线降级绕过已知撤权。
- 同一开机期沿用服务器锚点 + elapsedRealtime。设备重启或墙钟漂移超既有容差后，只恢复查询/证据，提示联网校时；**本设计不承诺断网重启立即继续扫码**。
- 48 小时到期停止新建/接收/保存，查询、保留证据及有合法在线身份时原批次上传不受删除式处理。有完整已保存事实且无未决输入时可核对冻结；恢复代次阻断时仍禁止新冻结，保留已有冻结包。
- 上传时资格已到期，不抹掉在有效期内形成的事实；服务端按原资格、时间锚点及审计验证。可证明合法的不因晚传自动作废；超期/时间不可信转复核。账号停用不能靠原令牌上传，需有权限的证据恢复渠道，见 §7。

## 3. 库存缓存与可用状态

建议增加仓库级临时作业准备包，复用压缩下载、签名清单、双层哈希和原子激活机制，不能借某张正式单的准备缓存推断临时资格。

| 数据层 | 固定/更新方式 | 业务用途 |
| --- | --- | --- |
| 库存/产品/映射/规则快照 | 按版本不可变，资格引用精确摘要 | 条码、产品、原重量和计划校验 |
| 准备时 availability | 固定完整截点/业务版本及周期 | 说明准备时是否可用，并为状态流建立基线 |
| R05 条码状态流 | 仓库+恢复代次+流命名空间；完整基线启用、逐页原子游标；业务版本单调 | 已出库、隔离、其他占用、经批准的新周期等后续事实 |
| 本机 guard | 全正式/临时作业共享，每包唯一 | 未上传、待审、未知、续作及已成功防重；不能随远端刷新清空 |

保存资格 = 原固定快照允许 ∧ 最新已知状态未阻断 ∧ 本机无其他有效占用 ∧ 周期一致。服务端可用状态不能覆盖本机待上传/待核对 guard；更高周期也不能把旧输入自动改为新周期。R07 返库确认及新周期同步后，只有满足新资格的新采集可引用它，旧记录留存复核。

缺缓存、首次未授权、签名错误、部分下载、混版本、空间不足均不能开始。新下载失败保留旧完整资料；旧资格仍有效且无已知撤销时可继续固定旧任务，不把缓存发布日期另变成未经确认的业务 TTL。显示“最近核对时间”，不写“当前全仓实时库存”。

草稿开工时重新核对所选版本；已有任务不能“刷新库存”静默换快照。库存行未找到、映射无效、重量精度超过约定时明确阻断；重量大小本身不构成业务拒绝。整数克精确求和，原重量文本保留。

清理：引用中的库存/资格/正文/回执不删；在途、待审、拒绝未处置、结果未知及有效防重超 90 天仍保留。云端至少 5 年，取消墓碑和唯一来源索引不能按普通幂等缓存 TTL 删除。安全留存调度归 R05。

## 4. 状态与占用分离

下表是建议状态模型；可映射到既有枚举，但不可将三列合成一个“已完成”。

| 本机阶段 | 云端可见状态 | 实物/写入与主恢复动作 |
| --- | --- | --- |
| `LOCAL_DRAFT` | 无 | 仅保存建单资料；无装车事实，无出库占用 |
| `READY_TO_SCAN` / `SCANNING` | 未收到或最近上报进度 | 仅按资格新增；本机保存有 guard，不声称云端已预留 |
| `FROZEN` / 已排队 | 无或 `REGISTERED` | 同一正文只读；上传原批次，不再扫码 |
| `UPLOADING` / `RESULT_UNKNOWN` | 未知、接收中或已成功 | 先查原 ID；不撤销、不换批次、不放行 |
| `REVIEW_REQUIRED` | 已正式化但待确认/短装/冲突/重复核对 | 不入账；可占用包待审预留，冲突包不抢别单占用 |
| `ACTION_REQUIRED` | 权限/格式/签名等需处理 | 保留全部原证据；明确失败原因，禁止盲目新建单 |
| `REJECTED` / `ABORTED` | 原批次终态 | 占用保留；受控续作/卸货/隔离；不是自动解冻 |
| `CONTINUING` | 新轮次已批准 | 原机验签、核对恢复包及完整集合后激活；序号延续 |
| `SYNCED` | `SUCCEEDED` + `COMPLETED` | 本机核验并耐久保存成功回执，才显示确认出库 |
| 关联属性 `LINKED` | 临时壳只读，目标为唯一 canonical | 独立于批次状态；仍须新目标确认，不直接放行 |
| 结束属性 `CANCELLED` | 经核验无有效出库 | 每包有处置结论；隔离继续有效，不生成 0 包出库 |

退回后选择：保留原包补扫；有批准凭据后卸货释放/隔离并续作；全部卸下后受控结束。不得直接删除冻结行。已有正式单处置在接收替换正文时才落地释放/隔离；**全部卸下没有非零替换提交时，当前代码尚无完整通路**，R06 须补“实物核验后结束临时作业”事务（§7），不能伪造 0 包成功，也不能把整个异常尾部留给 R07 后声称 R06 完成。

放行条件须同时满足：当前选中 source/轮次/批次、冻结摘要、canonical 映射版本、恢复代次、有效结果版本及逐产品/整单汇总一致，`SUCCEEDED` 与 `COMPLETED`，签名可信，PDA 保存成功，且无已知后续撤销/更正/恢复阻断。成功后断网可查看“已核验回执及核验时间”；不能把历史回执当成另一次运输许可。R07 新净结果出现时区分原出库事实与当前有效结果。

## 5. 逻辑数据结构提案

以下是字段与约束提案，不是迁移脚本；版本号、DDL、索引和 Room 实体必须在编码任务重新审查，不能直接追加到当前库。

### 5.1 PDA

| 建议对象 | 关键字段/约束 |
| --- | --- |
| `local_temporary_order` | clientOrderId PK；creatorDeviceId、creatorUserId、warehouseId、temporaryNo、localWorkId 唯一、创建时间及时间质量、建单原因、草稿/计划版本、currentRound/currentSubmissionId；source 不可变 |
| `local_temporary_plan` / line | `(clientOrderId, revision)` 唯一；车号、可空柜号、区域备注、计划摘要、固定各版本；lineId、productId、deliveryCode、计划正整数、稳定展示顺序；同版本代码唯一 |
| `local_work_context` | localWorkId PK；sourceKind/source 身份、当前资格/轮次、固定准备包引用；正式记录回填旧容器并保留原 assignment 引用，临时记录不生成伪 assignment |
| `local_temporary_authorization` | 签名原文/公钥引用、有效期、权限/设备版本、代次、适用准备包、历史替代关系；状态与会话存储分离 |
| `local_inventory_bundle` / rows | 仓库/版本/摘要/行数/激活状态；原重量、产品映射、availability 基线；新包暂存与 active 指针分离 |
| `local_canonical_mapping` | source 唯一；临时服务器记录 ID、canonicalOrderId/orderNo、mappingVersion、签名证明、目标修订、更新时间；历史追加；不回写原 capture/提交 |
| `local_command_intent` | 原来源、命令 UUID/幂等键、不可变正文/摘要、操作人/设备/代次、状态、回执；建单激活/续期/确认/关联核对用原请求恢复 |
| 现有 capture/scan/event/guard/submission 的扩展 | 指向稳定 localWorkId 并保留历史来源；新旧正式单持久键、冻结字节、序号和 FK 迁移需单独验证；guard 全局防重不分临时/正式表 |

采集事实至少保留 `captureId`、原设备及 `deviceCaptureSeq`、`orderScanSeq`、raw/normalized barcode、BROADCAST/MANUAL、confirmationPolicy、接收 wall/elapsed/boot、原 user/warehouse、授权 ID/epoch、计划 revision、snapshot、expectedDispatchCycle、sourceRecoveryEpoch；保存另留 capture 引用、原重量文本/整数克、savedAt/localSeq，审计 event UUID+原设备序号。现有码以 captureId 作为 scan 主键；建议延续这一实际关系，不无依据换号或声称已有独立 scanId。

原事实只追加；撤销、卸货、隔离、续作、映射使用独立事件/处置记录。有效统计由原扫描和生效处置推导，历史扫码序号有空洞也不重排。重复产品校验、分产品数量、合计、防重和审计在同一保存事务内完成。

本机原子边界：草稿 ID+头/行；开工固定计划+资格+容器；接收+序号；确认意图；保存+guard+汇总+审计；冻结+不可变正文+current；映射/签名回执+状态。存储失败均不能先显示完成。沿用 WAL/FULL，不使用破坏性迁移。

### 5.2 服务端

| 建议对象/扩展 | 约束与目的 |
| --- | --- |
| TEMP 离线资格及准备包 | 与正式 assignment 资格区别建模；适用 user/device/warehouse 及版本，历史不可覆盖 |
| `submission_scope` | 建议增加 sourceKind、creatorDeviceId/clientOrderId、canonicalOrderId、mappingVersion、REDIRECTED 状态；FORMAL 的 sourceOrderId 与 TEMP 来源二选一，不能全空/混填；TEMP 来源二元唯一 |
| 临时记录/计划 | source→服务器临时记录一对一，首次完整合法接收才建立 `outbound_order.source=PDA_TEMP` 及正式号；只到 abort 控制头不造假订单 |
| `submission` | assignmentId 在 TEMP 可空；freezeDevice、submitter、来源资格、轮次、原恢复代次明确；`UNIQUE(scopeId,workRound)` 和不可变 payload 保持 |
| `order_submission_slot` | 每 canonical 目标仅一个活动来源/轮次/current；正式目标关联后其原 scope 为 REDIRECTED，无第二领取入口 |
| `temporary_order_mapping` 历史 | source、from/to canonical、版本、目标修订、原批次/摘要、关联人/权限快照/原因；关联证明签名 |
| 采集来源与结果投影 | 原来源/正文保留；现有 submitted_capture/结果等以 order_id 关联处需要“原来源 + 当前目标投影”区分，不能批量更新原事实冒充新来源 |
| 审批记录 | 建议追加型 decisionId；种类、source、submission/hash、round、mappingVersion、targetRevision、actor/authVersion、原因及失效关系；当前单行 `submission_review` 不能承担多类批准与关联后重审 |
| 重复核对/冲突及处置 | 候选快照及版本、NOT_DUPLICATE/关联决定、逐包冲突归属与实物证据；批准不等于释放 |

最终化继续共用一次整单事务：按固定锁序重验身份、来源、target/current、计划/政策、原资格/事实、周期和全局占用，重算汇总，写唯一出库事实、可选账务、成功回执和 outbox。无冲突包可在待审事务建立 `REVIEW_RESERVED`；有他单占用的包只记 discrepancy，不抢锁。单包冲突导致整单不入账；不允许“先出剩余包”。扣账关闭仍保留 DISPATCHED 防重。

锁序沿用 V2.3：收集锁集合 → scope ID 排序 → order ID 排序（含槽）→ submission ID 排序 → bale ID 排序；TEMP 首次无 canonical 时先按合法来源唯一键创建 scope，正式化/关联后若锁集合变化则回滚重取，禁止持锁逆序加锁。与库存发布、正式/PC领取及重复候选核对的并发隔离须集成验证；不能只靠前端按钮禁用。

## 6. 权限、审批和受控关联

### 6.1 决策条件不能合并成一个 approved

| 行为 | 所需权限/证据（建议接法） | 不能替代的条件 |
| --- | --- | --- |
| 创建/扫描 | `temporary_order:create` + pda:scan/pda:submit、设备临时资格与仓库范围 | 不能确认出库 |
| 正常临时确认 | `temporary_order:confirm`，当前在线授权及完整无异常批次 | 不含短装、冲突、关联 |
| 短装批准 | 沿用 `submission:review`，主管角色范围，逐产品原因、当前批次绑定 | 批准短装不自动获得临时确认权 |
| 重复排除/关联 | `temporary_order:link`，仓库范围、人工核对原因 | 关联不能批准短装或直接放行 |
| 释放/隔离 | `order:dispose` 与逐包实物凭据；受控生效事务 | 不可仅凭“拒绝审批”释放 |
| 续作 | `order:resume`、旧终态及完整恢复包、新签名票据 | 不等于换机/更正权 |
| 已成功后的调整 | R07 专项权限、实物/账务凭据 | R06 不重开成功单、不修改历史 |

R05 当前只提供角色/仓库授予，尚无三个 temporary 权限的实际授予路径。建议新增专职能力角色并复用角色管理，使指定人员可获得创建/确认/关联权，不将确认权批量加给所有 PDA_OPERATOR。设备临时作业开关/版本校验需单独扩展；管理员不能通过自改角色越级授权。具体人员、设备名单属于管理员上线配置，尚未提供，不阻塞设计。

正常上传者实时具备确认权且所有条件通过时，可由同一提交事务直接确认。否则 `reviewReasons` 为完整集合，例如 TEMPORARY_ORDER_CONFIRMATION、SHORT_SHIPMENT_APPROVAL_REQUIRED、DUPLICATE_ORDER_REVIEW_REQUIRED、库存/身份冲突。所有待决条件同时满足才能最终化；每次最终化重新校验。

有确认+短装审批两种权限的主管可在一次显式操作中记录两项决定；只有一项权限就只满足该项并继续等待。异常审批默认不能由原提交者或参与该批次采集者自批；小团队例外须另有已批准政策、原因及审计，本设计不默认启用。既有代码尚未检查这种临时专项职责分离，必须列为实现项。

冲突不能用 approve 布尔值越过。主管核对实物归属后按合法处置/更正流程消除冲突，或退回本批次进入续作；旧周期不能手工改成当前周期。拒绝使本批次终结，不修改事实及占用。只有有效新票据才能恢复采集；新摘要或映射变化后旧批准失效。

### 6.2 疑似重复正式单

已确认的信号为同车、相近时间、相同产品，不能视为唯一身份。建议默认候选窗为可信作业开始时间前后 48 小时（这是待试运行校准的技术参数，**不是另一个已确认业务规则**）；无可信时间时按接收时间检索并允许扩大时间范围。规范化车号保留原值；同仓库、产品集合相同或有重叠作为候选排序依据，不依据用户输入时间排除所有可能重复。

提交预检及最终化均检查已发布的正式候选；待审期间新候选出现必须重验。建议出现候选转“疑似重复待核对”，暂停自动确认，由有 link 权限者选择“不是同一车次（必填原因）”或关联。候选快照/版本/核对政策版本进入决定绑定，新候选或目标变化使旧排除结论失效。与正式发布并发须定义共同仓库/车号核对锁或等价序列化边界，不做先查后写的空窗。

出库成功后才补建正式单，不回写已成功批次或自动关联；发布端提示现有临时来源结果，由人工取消无作业的重复正式单。若两边已有作业/出库则交 R07 核实、更正；不能拼接两段独立扫码顺序。

### 6.3 关联事务

1. 仅关联同仓库 `READY`、未领取、无输入/扫码、无 PC 活动作业、无在途/成功提交及活动来源的正式单；本临时单尚未成功、当前冻结批次与摘要已确定。只有无正文 abort 的 scope 不可直接关联，先补完整恢复证据。
2. 建议首版严格要求车柜号、产品标识/代码与各产品计划相同，固定库存/映射/政策兼容，目标 revision/rowVersion/mappingVersion 匹配；不允许关联时顺手调大计划掩盖短装。差异先核实并按合法正式单修订或临时续作处理，之后重新核对；这是保守实施选择，可后续单独扩展兼容判定。
3. 锁定双方来源、订单、当前批次与包，重验目标仍空白。保留 TEMP source，原子把 canonical 和唯一活动槽/待审占用的目标投影转至正式单；原临时壳为 LINKED，目标原生 scope 为 REDIRECTED。源/目标不能各自领取、提交或重复入账。
4. mappingVersion 增加，所有旧目标批准失效；返回签名映射证明，要求对新目标重新确认并重验短装等条件。payload、capture/event 原身份、原订单顺序不变；业务关联不是修改已签正文。
5. PDA 查询原 submissionId 或 source 得到映射，在一个本地事务安装。目标若只有可证明无任何输入/授权/批次的空只读缓存，可合并空占位为别名；目标有本地作业则停止并对账，保留两边，不以唯一键冲突删除临时单。
6. 相同幂等键返回原映射，异目标/异摘要报冲突；重传旧 TEMP 正文仍路由到同一 source 的当前 canonical；不能新建第二正式记录。关联、批准、取消、PC/PDA领取竞争必须只有一个合法结果。

## 7. 接口提案（不是正式 OpenAPI）

### 7.1 通用约定与端点

下列路径省略 `/api/v1`。沿用实际 envelope、PDA 身份/设备证明、Web 会话+CSRF及仓库授权。所有新写命令带固定 `Idempotency-Key`、`X-Server-Recovery-Epoch`、对象版本；命令首次发送前耐久保存。批次自身还受 submissionId+source+round+hash 唯一约束，换 HTTP 键也不能重复建单。权限失败不创建他人 scope/取消墓碑。

当前恢复代次是数字字符串，V2.3 的 UUID 示例是 R09 目标；R06 将其当不透明字符串比较，不自行更换格式。BIGINT/克数/序号统一十进制字符串，UUID 文本保持，revision/schema 等受限整数按正式契约约定。新字段 schema/receipt 的版本值由编码整合时分配，不在本文擅改现行版本。

| 路径/能力 | 当前状态 | R06 建议契约 |
| --- | --- | --- |
| `POST pda/temporary-order-authorizations`；`GET .../current` | 不存在 | 签发/恢复当前临时资格；请求 warehouseId、设备/人员版本、所需准备包上下文；响应签名资格及期限；同键不续时 |
| `POST pda/temporary-order-authorizations/{id}/preparation`；`GET .../{id}/content/{name}?bundleId=`；`POST .../{id}/download-ack` | 不存在 | 固定准备清单、inventory/mapping/policy/availability 摘要及行数、状态流基线标识；全量原子激活后 ack 原包 |
| `POST pda/temporary-order-authorizations/{id}/renewal` | 不存在 | 原资格 ID、绑定 source/计划/轮次、原版本、未决数与原请求；新签名资格、旧资格替代关系；不得改旧事实 |
| `POST pda/temporary-orders/submissions` | 不存在 | 完整 TEMP 冻结正文；首次完整接收正式化，正常确认或待审；批次幂等 |
| `GET pda/temporary-orders`；`GET pda/temporary-orders/{clientOrderId}` | 不存在 | 当前原设备/人员来源范围分页恢复；单条返回 source/currentRound/currentSubmission/canonical+mapping/续作及可见动作，不泄露他人对象 |
| `GET temporary-orders`；`GET temporary-orders/{id}`；`GET .../{id}/commands/{commandId}` | 不存在 | Web按有权仓库/状态/来源游标分页及审批详情；详情含当前决定和分页明细入口，命令查询返回原请求摘要/版本与回执。关闭浏览器后按来源查询决定历史恢复；未知结果不得直接另发决定 |
| `GET pda/submissions/{id}`；POST `.../abort`、`.../upload`、`.../chunks/{index}`、`.../finalize` | 已实现 FORMAL | 扩展 TEMP 控制身份；未正式化也能 abort；分块只传证据，最后整单处理，查询含当前映射和终态证明 |
| `GET temporary-orders/{id}/duplicate-candidates`；`POST .../duplicate-decisions` | 不存在 | 服务端临时记录 ID、仓库/时间窗、游标；排除决定绑定候选集合版本及原批次/目标，必填原因 |
| `POST temporary-orders/{id}/link` | 不存在 | §7.3 受控关联，返回签名映射；只完成关联，不隐式批准 |
| `POST submissions/{id}/decisions`；PDA 对应 `POST pda/submissions/{id}/decisions` | 不存在；现有是 Web `/review` | 显式 CONFIRM_TEMP、APPROVE_SHORT、REJECT；分别鉴权并绑定当前上下文。PDA 正常确认限有权原机/来源；复杂冲突与关联在 Web 处理 |
| TEMP 恢复包、停止确认、续作申请/激活 | 正式单已有，TEMP 不支持 | 建议 `POST pda/temporary-orders/{clientOrderId}/recovery-packages`、`.../stop-ack`、`POST pda/temporary-orders/continuations`；主管在正式化目标 change-request 流核验；既有 `pda/continuations/{id}/activate` 扩展 TEMP |
| `POST temporary-orders/{id}/close-dispositions` | 不存在 | 针对旧批次 REJECTED/ABORTED、完整停机恢复包、全部包已逐包批准卸下/隔离的结束事务；expectedRound/mapping/恢复摘要、处置清单、原因；只返回 CANCELLED/占用变化证明，无 SUCCEEDED/出库账 |
| 受控证据接收 | 普通 pda:submit 受当前在线身份约束 | 建议 `POST pda/temporary-orders/{clientOrderId}/recovery-evidence`：有效设备证明+有恢复接收权的在线身份接收原证据，或主管组织原机恢复包；无可验证身份时等待人工恢复，不开放匿名上传/最终化 |

建议受控证据接收增加独立 `temporary_order:recover` 权限，仅接收/查询该仓库原证据，不赋予确认、关联、处置或代替原用户继续扫描的权限，默认不授予普通操作员。证据接收保留实际上传者与原采集者的区别；旧设备被停用时不能靠普通会话继续业务，管理员在受控流程核验设备证明和原资格。无法验证原机/来源时保持保护并等待 R07。

TEMP恢复包入口同样使用不可变正文、摘要及稳定请求ID；`stop-ack` 绑定scope/设备/资格/轮次/高水位/包数/恢复包摘要，要求未决输入已处置。完整恢复证据可用于建立恢复中的TEMP来源记录，不强行把已abort正文提交成功后才允许处置。Web详情、commands和明细分页接口不返回签名私钥或他仓业务数据。

`close-dispositions` 必须在服务端确认原机停止、未决输入 0、全集匹配及每条凭据合法后，原子 RELEASE/QUARANTINE、终结唯一活动槽并留审计。云端确认后 PDA 验签安装处置：RELEASE 才能按 R05/R07 状态与周期规则更新 guard，QUARANTINE 保留阻断；已成功的 source 拒绝调用。原机失联、不完整恢复包、跨机接管不走此快捷通路，等待 R07。

现有 `/submissions/{id}/review` 不应悄悄放宽为万能批准。建议保留旧正式单契约，新的 decisions 使用同一最终化内核；未来整合时明确旧接口遇到 TEMP/新映射的拒绝行为。V2.3 中 `/decisions` 是设计路径，不能声称当前已有。

### 7.2 TEMP 冻结正文与响应字段

这是结构提案，不是可直接调用的 JSON 样例；公共 captures/scans/events 的完整结构沿用实际冻结记录，经字段核对后再进入正式契约。

| 字段组 | 必填数据及约束 |
| --- | --- |
| 稳定控制头 | schemaVersion、submissionId、source.kind=TEMP、source.creatorDeviceId、source.clientOrderId、freezeDeviceId、workRound、sourceRecoveryEpoch；continuationId 首轮 null，后轮有效票据 |
| 资格与计划 | offlineAuthorizationId、assignmentId=null、assignmentEpoch、orderRevision、planSha256、inventorySnapshotId、mappingRevision（产品映射）、barcodeRuleVersion、policyVersion、autoDeductInventory；此产品映射修订与 canonicalMappingVersion 明确区分 |
| 临时头 | clientOrderId、temporaryNo、vehicleNo 必填、containerNo 可空、reason 必填、areaNote 可空、creatorUserId、创建时间/时间质量 |
| 产品计划 | items[]：lineId、productId、deliveryCode、plannedCount、planPosition；代码唯一、正整数、合计精确，产品/型号名仅作快照并由服务端校验 |
| 原事实 | captures、scans、events、累计 dispositions、按来源的审计覆盖信息、captureHighWatermark/captureSummary；不得只上传有效扫码列表而丢弃拒绝/撤销/序号证据 |
| 核对与汇总 | startedAt、closedAt、逐产品 shortShipmentReasons、expectedCount、expectedWeightGrams、逐产品期望汇总；服务端独立重算 |

建单字段按实际 `OrderCommands.Plan` 对齐：车号≤40字、柜号≤40字；建议临时原因/区域备注≤500字，不截断用户输入。TEMP 柜号允许 null，进入正式头投影时规范化为空字符串，原正文不变。产品技术范围沿用现有码（最多1000行、各行1～Int.MAX_VALUE、合计检查溢出），不是车辆业务上限。完整证据继承现行32MiB传输保护，小正文1MiB以上分块、每块≤256KiB、最多128块；超过则明确保留资料等待受控扩展，不拆成两张订单伪装完成。

建议待审返回 HTTP 202、成功 HTTP 200；**客户端只根据明确业务状态判定，不能依赖 HTTP 码放行**。现有正式提交统一 envelope 200，适配时写明差异。

待审响应至少含：source、submissionId、status=REVIEW_REQUIRED、payloadSha256、workRound、临时服务器记录 ID、temporaryNo、canonicalOrderId/orderNo、canonicalMappingVersion、全部 reviewReasons、receivedCount/weight、当前提交版本、可执行动作。`inventoryDeducted=false` 不等于未占用；不返回真假难辨的 `canLeave=true`。

成功回执建议沿用签名 audience，增加 TEMP 来源与签名 canonical 映射、目标 revision、canonicalMappingVersion、temporaryNo；保留现有 actualCount/actualWeightGrams/items、原高水位/首尾序号、扣账状态、resultVersion、completedAt、issuedRecoveryEpoch、freezeDeviceId。签名声明与外层响应完整一致。本机从已知映射版本接受合法单调新映射，禁止倒退或串来源；无法验证映射不得只核对新 orderId 后放行。

审计补传采用设备连续确认序号不能跨缺口；单单冻结自含审计不等于全设备审计完成，清理必须等 R05 的完整确认。

### 7.3 审批/关联请求绑定与错误恢复

审批必带 `decisionId`、`decisionType`、`submissionId`、`payloadSha256`、`expectedWorkRound`、`canonicalOrderId`、`expectedMappingVersion`、`expectedTargetRevision`、`expectedDecisionVersion`、原因及所批准短装项。服务端记录实际 actor 和实时权限版本，不接受客户端自报权限快照作为授权依据。

关联必带 `targetOrderId`、`expectedTargetRevision/rowVersion`、原 `submissionId/payloadSha256/expectedWorkRound`、`expectedMappingVersion`、候选核对版本和原因。新请求意图发送前保留；旧决定重试只返回该命令原回执，不能复活已失效批准。状态查询另返回当前决定，避免旧回执覆盖新状态。

| 情形/建议错误码 | 客户端动作 |
| --- | --- |
| 401 / 403 | 有限重认证或等待有权人员；原批次保留，无身份时不越权发送；不改原操作者 |
| TEMP_AUTH_REQUIRED / TEMP_AUTH_EXPIRED | 联网准备/续期；旧事实不删除，新单不重置48小时 |
| PREPARATION_INCOMPLETE / SNAPSHOT_MISMATCH | 重试同包或由主管处理，禁止将部分缓存视为可扫 |
| IDEMPOTENCY_CONTENT_MISMATCH / CURRENT_SUBMISSION_EXISTS | 恢复原 ID/正文/current，不换键重建 |
| SUBMISSION_NOT_FOUND | 查询未登记可能是正常分支，发送原正文；想改内容必须先得到原子 abort 终态 |
| WORK_ROUND_CONFLICT / SUBMISSION_SUPERSEDED | 查询当前轮次；旧请求只读，不自动续扫 |
| LINK_TARGET_BUSY / LINK_INCOMPATIBLE / MAPPING_VERSION_CONFLICT | 保留原来源，重新核对候选与状态；不得删除目标缓存/已有作业 |
| SUBMISSION_STILL_CONFLICTED / SELF_REVIEW_DENIED | 逐项核实、独立人员处理；普通确认权不能覆盖 |
| SERVER_RECOVERY_REQUIRED / ORDER_RECOVERY_REQUIRED | 独立持久阻断，保存旧代次和证据；重新登录/新基线不能解除 |
| 超时、断网、5xx、回执丢失 | RESULT_UNKNOWN，查原批次后原正文重传；退避且尊重 Retry-After |
| 签名/汇总/映射不符或本机保存失败 | 不显示放行，保留原回执与问题详情，恢复同一批次 |

所有尚不存在的错误码仅是提议；编码时与 R05 最新错误语义统一后再加入契约。用户界面显示可理解的原因和下一动作，技术详情不放主流程。

## 8. 实施边界与真正未决项

进入编码条件、责任分界及提交级检查见 [DEPENDENCIES](DEPENDENCIES.md)；逐项验证见 [ACCEPTANCE](ACCEPTANCE.md)。本设计没有尚未确认的基础业务规则，需要后续确定的是：R05 最终交接提交/字段及任务接口、共享作业容器迁移的最终方案与版本、审批/关联证明正式 schema、重复候选时间窗现场校准和实际授权名单。

建议选择已明确；以上技术事项应由编码准备在最新代码上收口，不要求用户重答 Q01～Q16。用户需另行明确启动具体编码范围，当前“继续”不构成完整实现授权。R07 之前可以独立交付原人原机正常/短装/受控关联及完整拒绝后处置，不能声称换机、成功后更正、返库再出或灾后重建已可用。
