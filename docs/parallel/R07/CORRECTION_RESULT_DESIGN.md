# 已出库更正、库存对账及结果模型提案

适用于PDA和PC已成功出库的事实。依据V2.3 §5.3～5.7、7.5、12.12及Q06/Q08/Q10/Q15；[基线证据E08～E12](EVIDENCE_BASELINE.md)。不改变原未成功订单的召回/续作规则。

## 1. 三层结果和四类库存事实

原始确认事实、调整凭据、当前有效结果分别保存。`dispatch_record`、原扫码/PC录入、冻结正文、成功回执及结果v1不可改写；后续更正创建新结果版本并移动当前指针。原订单保持COMPLETED，净0也不能回到READY、复用旧审批或重新扫码。

库存同时保留：主数据/固定重量快照、上游全量在库声明、实物及全局占用、系统账务。可用性必须同时满足最新有效上游声明在库、实物验收合格、无冻结/隔离/未决对账、无活动占用/有效出库占用。已出库但未自动扣账仍不可再分配；新全量导入不能解除占用或增加周期。

## 2. 更正流程

统一过程：选择成功订单和具体原dispatch → 查询固定当前结果版本 → 填写原因、逐行目标值与实物凭据 → 固定文档摘要提交待审 → 有权限人员审批 → 锁后复验并原子生效 → 展示原结果、累计调整、当前净结果和库存处置。申请、证据上传、待审均不改账、不放库存。

| 类型 | 必须核验的证据 | 结果变化 | 库存/周期变化 |
| --- | --- | --- | --- |
| PHYSICAL_RETURN 真实退货 | 原dispatch/周期、实际收货时间/地点/人、条码实物、数量、检验结论及凭据；部分单可逐包退，不拆一个包为虚构小包 | 原行保持；该dispatch当前贡献从1/W归0/0 | 反向实际账务；受控周期+1一次。未合格或上游未在库则新周期仍隔离 |
| DISPATCH_CANCELLATION 错误出库 | 证明该包未实际发出、实物在库、误操作原因、核验者；已离场不能选择此类型 | 原贡献归0/0 | 同样办理恢复凭据与一次周期转换；真实已离场需退货路径 |
| WEIGHT_CORRECTION 重量更正 | 原dispatch、原/当前有效重量、新目标重量、计量/上游纠错凭据、原因及核验时点 | 包数不变，重量差额=目标-当前；保留原sku值/rawWeight | 仅对实际扣账的当前贡献记重量差额，数量0；不改周期、条码、产品或实物归属 |
| HEADER_CORRECTION 非实物更正 | 原车/柜等值、新值及原因，明确字段允许清单 | 包数/重量不变，新header快照及结果版本 | 不改库存或周期；不能借此更换发货产品/订单来源 |

每凭据建议单类型、多行，禁止同一文档既调重又退同一dispatch，便于审批明确前后贡献。整单部分包更正可支持；同一凭据多行必须全成或全不成，不静默成功部分。HEADER行的countDelta/weightDelta均为0，仍生成可追溯effect及新header版本。0包限制仅针对首次确认出库；全单退货后有效结果0包/0g合法。

只允许对当前仍有效的dispatch作减包/重量更正。已退货或错误出库恢复的原dispatch不能再次减包/增周期。对已归零历史值的说明补充通过追加审计/证据表达，不能改变净结果。已生效文档不编辑/撤回；未生效文档退回后用新ID/新摘要重新申请并关联原文档。已生效重量更正录错时，可再发一张基于最新结果版本的重量更正。误批退货且新周期已建立的反向业务不自动开放，按D04专项处置门槛处理。

## 3. 实物恢复与新周期再出库

1. 原单O1/cycle1成功后，主管取得退货/错误出库的完整实物证据并批准。事务保存correction effect、当前净结果新版本、实际账务反向、原dispatch调整状态、原占用处置、cycle1→2和状态outbox。
2. 唯一恢复记录绑定原dispatch及恢复凭据，最多推进一次。旧dispatch仍保有cycle1，原`UQ(bale,cycle)`不删除。可用状态独立于周期：未验收合格、上游缺码或其他差异未决时保持cycle2隔离。
3. 需要后续解除隔离时，单独审批核对隔离原因全集、当前位置、原恢复凭据、上游最新声明、包状态版本和当前周期，关闭已解决阻断并发布新状态版本；不再次增周期、不制造第二次退货，也不以重导sku代替审批。
4. PDA接收同恢复代次内更高版本的`CYCLE_REOPENED`，核验凭据及状态。若`available=false`仅保存新周期信息，仍不可扫码；之后经验证的AVAILABLE状态须携带对应reopeningId及更高stateVersion才允许使用。R05需保证基线也携带可验证的周期恢复依据，不能仅发一个更大cycle。
5. 仅已经确认出库的旧周期guard可按完整证明受控替代；未提交、未审、结果未知、旧周期未决输入仍阻断并进入对账。不能批量清空条码guard。
6. 再出库必须新订单O2、新资格、新输入、新周期2，新保存时刻及序号真实生成。旧输入永远保留expectedDispatchCycle=1，迟到上传返回周期冲突；不把历史记录升级到2。O1仍归原出库日，O2归新的出库日。

若产品/重量主数据也需变化，通过主数据流程形成新快照用于新授权；历史重量更正不会自动篡改库存快照。新周期前端不得基于原重量更正凭据假造一个全仓新sku版本。

## 4. 账务只调整真实贡献

账务来源由dispatchId串联原扣减、人工补扣、重量差额和完整反向。`autoDeductInventory`是原政策事实，当前实际记账情况另查真实ledger；开关后来变化不会补扣历史。

| 场景 | 应追加的账务 | 不能发生 |
| --- | --- | --- |
| 原来未扣账，之后也无人工补扣；退货 | 0条反向，结果及周期/实物凭据仍生效 | 虚构+1/+重量 |
| 原扣-1/-300000g；重量改为301000g | 0/-1000g差额，链接更正行 | 再扣-1、改原流水 |
| 上述包再退货 | +1/+300000g反向原扣；0/+1000g反向差额 | 将两条反向当成退了2包；只反向最早重量 |
| 原扣-1/-300000g；重量改299000g再退 | 重量差额0/+1000g；退时+1/+300000g及0/-1000g | 认为反向流水重量只能为正 |
| 原无自动扣账，后人工补扣-1/-有效重量 | 与自动扣账一样按实际剩余贡献反向 | 仅看原开关false而遗漏真实补扣 |
| 重复批准/重试/多行同dispatch | 返回原结果；若异文或过期版本则拒绝 | 再反向、再递增结果版本或周期 |

每条完整反向必须有 `reversesEntryId`，唯一且与原行同dispatch/bale、delta完全相反，不允许反向另一条反向流水。重量差额行不占完整反向引用。每个dispatch本轮有效数量账务合计应为0或-1；重量合计与“尚存实际账务贡献”一致。状态错误或账务缺链时转对账，不能依合计凑数生成流水。

人工补扣在对账审批中显式执行：仅对尚有效、未有数量扣减贡献的dispatch，锁定当前结果/账务版本，以当前有效重量一次补扣；唯一业务键为dispatch+补扣动作，审批和后续更正共同加锁。若已退回净0则不能再补扣历史出库。账务已反向后同码新周期以新dispatch建账。

结果差额与账务流水不是同一行数口径：每个更正明细恰好一条 `correction_effect`；该行可以关联0条、1条或多条ledger。汇总只求effects的countDelta/weightDelta，不JOIN账务后重复求和。

## 5. 库存差异处理

差异案件拟定 `OPEN → EVIDENCE_PENDING → REVIEW_READY → RESOLVED`，有冲突转`BLOCKED`。RESOLVED只说明该差异有处理结论，不等于该包可用；可用性仍检查所有独立阻断。每项固定旧/新快照、bale/cycle、实际dispatch/占用及账务版本。

| 差异 | 处理路径 | 关闭与放行条件 |
| --- | --- | --- |
| 全量缺码，包无在途/出库 | 核对上游声明及实物；缺码不是负向库存扣账 | 可记录接受上游缺码而关案件，但包仍不在库不可用 |
| 全量缺码，已有在途/待审 | 保留原快照和占用，提示冲突；必要时召回和实物核验 | 未解决前不自动提交、释放或改历史重量；持有作业须参加裁决 |
| 已出库包在新全量中重现（包括未自动扣账） | 核对是上游重复声明还是真实退货 | 仅重复声明可记录结论但保持DISPATCHED；真实退货须更正与恢复凭据 |
| 名称/重量改变 | 新授权按新快照；原作业展示差异，不能上传时替换 | 在途不兼容需召回/重批，已出库需要显式重量更正；单独关差异不改原结果 |
| 账实不符、少扣/多扣或缺账 | 列出每dispatch实际流水链，核验外部账证；已存在正确业务事实方可补记/反向 | 补扣按唯一业务键；对冲突事实先更正，不能凭总额“平账” |
| 隔离包申请释放 | 查全部隔离原因、实物检验、上游在库、占用/周期、恢复范围 | 每个阻断均有解决凭据；释放不改原事件，受控状态outbox通知 |
| 未知条码/跨仓库/单号无法对应 | 保持隔离，查主数据或恢复案件 | 不创建假dispatch、不通过导入转仓、不用“盘盈”伪造原扫码 |

未成功的PDA/PC作业仍走已有实物处置，不进入已成功更正。无出库事实的隔离解除不增dispatchCycle；已成功出库恢复才有周期转换。未知影响范围使用仓库级阻断，不能只删某条bale_commitment。

审批与新的库存发布、出库、退货、解除隔离竞争时，锁后校验 `expectedStockVersion/expectedSnapshotId/expectedDispatchCycle/expectedLedgerVersion`；任一变化返回冲突，保留原申请，重新核对新方案。关闭差异、账务、状态投影和审计在同一事务，避免“差异已关但库存未处理”。

## 6. 概念数据模型（非正式DDL）

| 实体 | 必备字段/关系 | 不变量 |
| --- | --- | --- |
| 原确认事实 | dispatchId、orderId、channel、原submission或pcSubmission、captureId或pcEntryId、baleId、cycle、原sequence、原重量/rawWeight、confirmedAt | PDA/PC来源二选一，不能给PC伪造PDA人员设备；原ID和摘要不可变 |
| correction_document | id、warehouse/order、type、expectedResultVersion、reason、documentSha256、状态、申请/审批身份和权限快照、submittedAt/approvedAt、sourceRecoveryEpoch | 发出后正文固定；批准绑定同摘要及版本；每单一份凭据原子生效 |
| correction_line | id、documentId、lineNo、dispatchId（仅header可空）、目标值、before值、证据引用/摘要、businessOccurredAt | 文档内dispatch不重复；目标由服务端相对当前版本重算；实物行必须有核验凭据 |
| correction_effect | id、lineId、orderId、resultVersion、dispatchId可空、countDelta、weightDeltaGrams、action、cycleBefore/After可空、businessOccurredAt、effectiveAt | lineId唯一，一行只产生一次结果差额；账务关联不增加effect数量 |
| order_result_version | orderId、resultVersion、originalResultVersion=1、originalSubmission引用、sourceCorrectionId可空、revision/header快照、effectiveCount/Weight、adjustmentStatus、createdAt | v1首次成功；v>1对应唯一生效凭据；发布只读，数量/重量可为0；current指向同单真实版本 |
| order_effective_scan | orderId/resultVersion/dispatchId、原capture或pcEntry引用、原sequence、isEffective、effectiveWeight、latestCorrectionLineId | 每版本覆盖全部原dispatch，包括归零行；不改原sequence，不新造扫描 |
| order_effective_line | orderId/resultVersion/原产品标识、count、weight | 按effective_scan聚合；原产品/计划顺序不变，保留净0产品 |
| inventory_ledger扩展 | id、dispatch/bale、entryKind、quantityDelta、weightDelta、correctionLineId/ reconciliationLineId、reversesEntryId可空、businessKey、createdAt | 每个业务键唯一；完整反向原entry唯一；跨表同单/同包/同仓须事务校验 |
| cycle_reopening | id、originalDispatchId、correctionLineId、cycleBefore/After、evidence、stateVersion、issuedEpoch | 原dispatch最多一次；after=before+1；是否可用独立表达 |
| inventory_reconciliation | id、warehouse、差异行全集、各期望版本、证据/结论、审批、commandReceipt | 处理已知差异不删除其他阻断；实际账务与结果更正分开链接 |

完整来源长正文可归档，但可查询的关系键、版本、摘要、状态不能全部塞进无约束JSON。不可变正文用于验签/比对；投影用于查询，两者可复算核对。

命名沿用V2.3的概念，实际基线字段是`actual_count/weight_g`且没有`order_line_result`表；迁移不能假设总体方案的表名已经存在。如何追加兼容列、拆首次来源与后续版本外键，见[接口与迁移提案](INTERFACE_PROPOSAL.md)。

## 7. 结果公式与版本

对订单O的指定版本v：`净包数 = 原确认包数 + Σ截至v的countDelta`，`净重量 = 原确认重量 + Σ截至v的weightDelta`。同时应等于该版本逐包有效快照的合计。不允许负包数/净重量或重复归零。所有重量用整数克精确计算，API传十进制整数字符串，rawWeight单独保留；溢出或超过支持精度要拒绝，不用浮点/舍入消除差额。

结果版本、作业轮次、订单修订、授权代次、出库周期、服务器恢复代次、回执版本、同步状态版本分别独立。新结果仍展示原autoDeduct政策，实际账务状态从真实贡献查询，不能复用原inventoryDeducted布尔值代表更正/补扣之后的当前账况：

| 标识 | 变化条件 | 不得混用 |
| --- | --- | --- |
| resultVersion | 成功v1，此后每次生效更正+1 | 审批重试、库存流重放、单纯账务补扣不增结果版本 |
| workRound | 受控续作/交接关闭旧轮次后+1 | 不重置orderScanSeq，不等于再出库cycle |
| orderRevision | 计划修订流程 | 已成功非实物更正保存结果header，不直接覆盖原revision |
| assignmentEpoch / authorizationId | 新资格/受控续期 | 原事件使用原凭据，不替换为最后一次续期ID |
| dispatchCycle | 同一包经有凭据恢复后+1 | 每周期一个有效出库事实；重量更正/解除普通隔离不增 |
| serverRecoveryEpoch | R09外部恢复动作 | 按不透明值判等；普通重启/登录不变化，不与其他版本比较大小 |
| receiptVersion | 原协议/回执自身版本 | 原成功回执不随resultVersion变化 |
| stockStateVersion / ledgerVersion | 同代次状态变化/实际账务变化 | 库存状态事件不是出库回执，也不是更正审批 |

`adjustmentStatus=NONE`表示尚无生效更正；`FULL`表示原出库有效贡献全归零；其余已生效更正为`PARTIAL`。包含只调重/只改车号时UI显示“已更正”，不误写“部分退货”。调整明细另给类型/数量标记，避免用一个状态概括多种业务。

## 8. 时间归属与R08接口口径

保留四种时间：原输入/保存时间（设备证据及可信度）、`shipmentConfirmedAt`（原服务端成功确认）、`businessOccurredAt`（经凭据核验的退货/纠错实际发生时点）、`effectiveAt/postedAt`（服务端批准生效/记账时点）。原出库日取shipmentConfirmedAt的Asia/Shanghai日期；不按上传重试、接收端手机时间或最后更正时间移动原单。

- 默认 `SHIPMENT_DATE_NET`：按原出库日选订单，展示固定快照中每单最新净版本；后来更正会影响重新查询的原出库日结果。明确显示“截至某快照、结果版本”。
- `MOVEMENT_DATE`：原出库贡献归原成功日，调整贡献归经批准的businessOccurredAt日期。建议退货取实际验收收货时点，错误出库取核验认定时点，重量/非实物纠错取有凭据的纠错发生时点；批准/入账时间另列。实际时点未经核实则不批准，不用客户端当前时间默填。跨期/补录的会计锁期政策已于2026-09-26确认为“允许3天”（原D02，权威记录：主树 `docs/BUSINESS_POLICY_R07.md`）；窗口校验与超窗口拒绝仍待实现，不得在实现前以“政策已确认”为由回填绕过锁期。
- `view=ORIGINAL/EFFECTIVE/ADJUSTMENTS`决定事实层；候选`dateBasis=SHIPMENT_DATE_NET/MOVEMENT_DATE`决定期间。R08当前模块的`basis=ORIGINAL_CONFIRMED/CURRENT_EFFECTIVE`只表示前者的原始/有效视图；日期固定SHIPMENT_DATE_NET，尚不支持MOVEMENT_DATE。适配时分别映射，不用同名basis混传，详见[跨任务联审](CROSS_TASK_ALIGNMENT.md)。
- 所有日范围按Asia/Shanghai日界转UTC，使用`[from,to)`。业务发生早于原出库（除单独说明的历史纠错案件）或晚于合理核验时点应阻断核对，不因机器时钟猜测。
- 查询多页先固定成员及每单resultVersion，页游标绑定snapshotId和排序。导出任务固定订单集合、revision/resultVersion、期间口径、模板版本及权限范围；生成时不重新读取“最新”。含账务列时额外固定ledgerVersion/账务截点；结果版本不会隐式冻结后来补扣的账务。
- 快照cutoff同时约束成功及更正批准生效的知识截点：仅businessOccurredAt早于截点但当时仍待审的调整不得计入。R08输入没有approvedAt/effectiveAt，需上游适配器验证并留存来源证明；不能用渲染器仅检查occurredAt来替代。20日收货21日批准，20日末快照仍是未含该退货的版本。
- 原始明细按原orderScanSeq升序；PC使用原在线录入sequence并标明渠道；调整独立页，当前有效页允许空号不重编号；不能用调整时间对原扫描重排。PDF原版固定v1，更正版明确版本及关联，更正不覆盖旧下载文件。

R08标准模板需要可信完整顺序。缺失案件可在业务查询中披露缺口，但不能生成声称精确原顺序的标准导出；须另有明确的损失/异常报告模式。当前模块PC字段和“原设备”标签尚未表达真实pcEntry/作业来源，接入前需补渠道语义，不能造PDA设备/capture序号。原姓名无历史快照时保留ID并明确名称缺失，不将现用名冒充原名。

## 9. 可复算示例（纯合成）

O1在9月18日成功100包/30000000g，均300000g，结果v1；P的原扣账为-1/-300000g。

| 动作 | O1结果 | P相关追加账务 | 日期归属 |
| --- | --- | --- | --- |
| 9月19日把P改为301000g并批准 | v2：100包/30001000g；effect=0/+1000g | 0/-1000g | 默认仍属18日；movement的+1000g属19日 |
| 9月20日收回P，21日审批 | v3：99包/29700000g；effect=-1/-301000g | +1/+300000g，0/+1000g各反向一次 | 默认18日净99；movement退货属20日，postedAt属21日 |
| P周期2合格后22日在O2重新扫/成功301000g | O1仍v3/99包；O2 v1：1包/301000g | O2新dispatch按其政策可扣-1/-301000g | O2属22日，不归回O1 |

若O1最初未扣账且没有补扣，前两次更正的结果完全相同，但P没有任何差额/反向账务。若O1全部100包后来归零，结果可为vN的0包/0g，原v1与成功回执仍是100包。已固定到v2的导出仍为100包/30001000g，不受v3影响。
