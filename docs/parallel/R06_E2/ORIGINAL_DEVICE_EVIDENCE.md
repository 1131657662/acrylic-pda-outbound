# R06 E2 完整原机证据与实物处置边界

日期：2026-09-21；固定基线 `c3009b6`。这是 [来源模型](SOURCE_OWNERSHIP_MODEL.md) 的验证合同提案，没有证据写入口、Android 实现或新签名格式。新对象必须显式版本化；不能让旧 schemaVersion=2 或旧 `TemporarySubmissionReceipt` 静默接收续作字段。

## 1. 三类正文事实不合并

| 情形 | 必须保留的事实 | 后来原机补证据 |
| --- | --- | --- |
| A：无 upload/正文先 ABORT | 原 S 终态、payloadPresent=false、payloadSha256/order/slot/assignment 均 null；原取消签名/receipt_text 原字节 | 单独 LOCAL_FROZEN_PAYLOAD/完整停止集合；可建立来源保守占用，不生成 payload、FH 或 assignment |
| B：部分块后 ABORT | upload 声明摘要/大小、原收块各自存在；receivedPayload 仍 null，原回执不补声明摘要 | 新证据对象可引用并逐字节核对原块；全部块到齐也不调用旧 finalize；原 upload 不改成业务已接受 |
| C：完整接受后 ABORT；未来合法 REJECT | 原 payload 字节/hash、PDA_TEMP canonical/slot、原已知 K 与原取消/拒绝记录 | 新停止集合覆盖此前全部原事实；原 accepted payload 不被“较新恢复包”覆盖，原终态不改 |

E1 当前只已有合法 A/B/C-ABORT 测试，没有合法 REJECT 实现/夹具；本表 REJECT 为后续待验。`receivedPayloadSha256`、`declaredUploadSha256`、`localFrozenEvidenceSha256` 明确分列，null 不用空字符串/空数组替代。原回执保存原文本及原 SHA，查询时的适用性另算；新增证据不能改变过去“未接收正文”的承诺。

依据：[E1 文件核验](../../../server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceFiles.java)、[原证明核验](../../../server/src/main/java/com/acrylic/outbound/temporary/evidence/SourceEvidenceProof.java)、[C0 原回执/当前效力](../R06_COMMAND_VECTORS/README.md)。新签名 audience/hash 域不在本批发明，交共同协议执行者冻结；原 RS256 token 字节不重编码。

## 2. 完整性是有边界的证明

`VERIFIED_COMPLETE_AT_STOP` 的含义限定为“在被证明的原机停止截点，该来源所有电子事实已覆盖并与服务器已知事实一致”。它不证明未扫码实物不存在、车辆已经卸空或主管已完成实物核验。车辆为空需要逐包处置和独立现场核对结论；电子集合为空本身不具该结论。

每个 set 固定：scope/source、S/round、authorization、originalOperator、freezeDevice、sourceRecoveryEpoch、停止对象 ID、captureHighWatermark、全设备 auditThroughSeq、清单条目数/字节/hash、原机冻结集 hash。`transportRecoveryEpoch` 只用于当前门禁，不能替换原 source epoch。客户端上传的 `complete=true`、计数或最大序号只属于声明，服务器逐项验证。

原机须在一个 Room 事务内停止该来源新输入和扫码、处理全机未决输入、固定清单及停止事件，并耐久保存原对象键和正文；保存失败不发送。断电重启先恢复同 set/停止状态，不重新生成“更完整”同 ID 正文。停止不等于清除本机防重或允许下一新来源重扫保留包。

## 3. 完整性验证清单

| 维度 | 需要的原材料与服务器证明 | 拒绝升级为完整的情形 |
| --- | --- | --- |
| 来源与资格 | T/A/C/S 交叉绑定；原资格签名、五份准备资料和激活事实按实际引用核验；原人/机/仓/代次一致 | 新资格替换旧资格、当前上传者替换原操作人、缺绑定/未知 key/错仓/错轮次 |
| 捕获全集 | 原 source 的捕获序号连续覆盖至高水位；首次规则沿 [TemporarySubmissionEvidence](../../../server/src/main/java/com/acrylic/outbound/temporary/TemporarySubmissionEvidence.java)，后续按票据前驱承接；所有原 ID/hash 固定 | 缺中间捕获、重复 ID/序号、把 INVALID/BUSINESS_REJECTED/DISCARDED/VOID 删除、只上传 ACTIVE |
| 输入终态 | 每条 RECEIVED 对应合法终态及必要确认/失败确认事件；summary 由明细重算；unresolved=0 | 未决输入、失败未确认、手输未决被当作未发生、summary 与逐条不等 |
| 扫码和原快照 | COMMITTED 捕获与扫描一一对应；原 captureId/orderScanSeq/deviceCaptureSeq、barcode/cycle、精确重量原文、固定库存/映射/计划逐条一致 | 按当前库存重建原重量、改原周期、重复有效条码、遗漏 VOID 的原记录 |
| 已知不可变事实 | 对所有已接受正文、早先证据集、原分块、submitted_capture/event、pda_audit_event 逐原 ID/hash 包含性核对 | 同 ID 新内容、少一条历史事实、只有相同汇总；文件不可读/损坏也不是完整 |
| 全设备审计 | FORMAL/TEMP 共用原 device localSeq；已验签归档前缀 + 原字节连续后缀直到停止事件，核已知最大值和全部已知条目 | 只取 TEMP 子序列、MAX(seq) 冒充连续、跨来源缺段、末事件不是本 set 的停止、未知归档 key |
| 停止与重启 | 明确停止事件绑定原来源/round/set，原机已耐久阻止后续新保存；收到 stop 后迟到上传保持旧终态 | 只发一条声明而本机仍能扫描、停止后同 round 出现新原事实、重启丢停止状态 |
| 集合传输 | 固定 manifest 绑定每页/块顺序、条目数/原 bytes hash、前后链和结束页，重试按同键同摘要 | 页混用、重复/漏页、manifest 变更、只收最后页或 declared chunks 全齐即标 complete |
| 共同占用 | 每个已知有效/待处置包精确核 K owner/cycle/state；来源已知未处置 K 反向包含于集合；历史处置/续作链完整 | 来源集合少于 K、证据声称包属于他源、已有 dispatch、只比数量或只按 order_id 计数 |

可复用 [RecoveryEvidence](../../../server/src/main/java/com/acrylic/outbound/recall/RecoveryEvidence.java) 的“已提交事实不可遗漏”规则、[RecoveryAudit](../../../server/src/main/java/com/acrylic/outbound/recall/RecoveryAudit.java) 的连续审计与归档规则；这两个现有接收器仍要求正式 assignment/RECALL_STOPPED，不可直接调用并伪造字段。TEMP 停止事件/证据版本必须独立显式定义，原审计流继续共用，不另开 TEMP 序号。

状态分为接收 `RECEIVED`、完整性 `PARTIAL / VERIFIED_COMPLETE_AT_STOP / CONFLICT / UNVERIFIABLE` 与当前效力。完整集合后来有新已知事实或前驱变化时，保留历史验证，当前效力失效，处置必须重新核对。没有把“收到了文件”直接映射成 VERIFIED 或 RECONCILED 的路径。

## 4. 文件与接收事务

沿 E1 每次重新鉴权、私有路径不外露、大小/hash 核验。证据内容先落不可变私有对象并真实确认耐久，再在短事务中建授权引用、原键、集合关系和已知包保守占用。文件丢失/签名错误/索引写失败不返回“已接收完成”；孤儿文件不证明业务接受，不在本批自动清理。

正文继续以当前 32MiB/256KiB 的技术容量作为实现参考；若全设备审计超过单体容量，按已验签归档前缀和固定清单分页传输，不截断为“完整”，也不增加每车包数业务限制。具体资源配额、分页上限和新路径交协议实现批固定并压测，本批未注册 API。

新证据写仅在日常 NORMAL、当前有效身份及明确证据写能力下可用；E1 `temporary_order:confirm` 只读权限不能自动变成写权限。推荐后续复用现有 `pda:recover` 作为原机证据能力，但必须显式核 scope 的原人原机与当前同仓授权，登记契约并验收；不新增默认授权。停用身份、异机、跨代次由 R07/R09 独立授权，不绕过认证。

同 source/S/round/kind/originalObjectId 同 bytes 重读原接收结果；异 bytes 明确冲突。当前撤写权时不执行/续作，但有当前只读权可经独立读路径看原事实。404、断网、丢响应都保留原键和占用，不能作为重新提交或卸货释放的依据。

## 5. 拒绝、处置、结束与续作

| 动作 | 前置条件 | 原子持久结果与禁止推论 |
| --- | --- | --- |
| REJECT / ABORT | 对应决定权限与原上下文；ABORT 沿已存在原机接口 | 原 S 终态和原回执不可变，K 不释放。到期/提醒/撤权也不释放 |
| 追加原机证据 | 原来源身份、独立写能力、原文/原键与当前门禁 | 保存原文/索引和已知实物保护；PARTIAL 可保守占用，不能批准释放、续扫或记账 |
| RETAIN | 完整停止集合、精确 K/原周期核对、对应处置上下文 | 留在本来源保护中；它只是处置结果，不独自产生扫描授权 |
| RELEASE | 完整停止集合、实际卸下凭据、`order:dispose` 与同仓当前授权、精确原 owner/cycle/state | 同事务保存逐包应用和不可变命令回执，精确删除本来源 K，发条码变更；不逆转 ledger、不改 dispatchCycle |
| QUARANTINE | 同等级核对、隔离实物凭据和权限 | K 变为本来源 QUARANTINED owner，防重持续；未解除隔离，不作 AVAILABLE |
| 全部卸下结束 | 全部逐包去向闭合、现场核实车辆清空，保留集为空且无未知/冲突/未决；处置命令成功 | 新来源处置记录为 ENDED_UNLOADED，旧 S 保持 ABORTED/REJECTED，canonical 可 null；不造 0 包成功/新S/FH/账务，不隐含正常出库放行 |
| 受控续作 | 旧 S 终态、完整停止集合及处置闭合、`order:resume`、原人原机与合法新授权；固定保留集/下一轮/下一序号 | 同事务保存票据/前驱/current 变更/owner 转移；Android 验签耐久激活后才可新增。旧 S 不复活，新 round 单调，原 plan/资格历史不重写 |

全部卸下若有 QUARANTINE，车辆可被核实为空，但这些包继续在 K 隔离；不能把“全部从车上卸下”写成“全部库存已释放”。保留集、已释放、隔离三者以原 scan/cycle 不交叠分割原有效集合；所有非有效捕获也继续保留。零有效电子事实仍需完成停止/现场核对，不能凭数量 0 结束案件。

续作的原人/原机当前身份必须再验证，权限或人员集合改变沿 L2 C04 回滚/冲突处理。首批只准备原人原机，换人/换机不自行降级成普通登录重取资格；交 R07。原资格到期仅限制新增，历史合法事实/证据仍保留；新续作授权必须显式签发，不给旧 48 小时资格续命。

关联变更、current/round 变化、恢复代次变化和新的实物事实均使旧“待执行”决定失效；已提交处置是历史事实，不因 currentEffect 变化恢复已释放占用或再执行一次。C1 命令绑定 setHash、完整性结果版本、映射/决定/轮次和逐包清单摘要；原回执不随最新 view 重生成。

## 6. R07 / R09 交接闭包

R07 消费原 source、set/object ID、原设备序号/签名、旧 S 和处置/续作前驱。代传者仅新增 transport provenance；原人机字段与 raw bytes 不改变。成功后的退货/错误出库必须用 R07 更正凭据和周期规则，E2 不删除 DISPATCHED 的 K 或制造反向 ledger。

R09 备份/恢复必须闭合 T/A/C/S、原准备、payload/upload/chunks、证据对象/完整集合/验证、K 各 owner、处置/续作、C1 原命令/回执、映射、共同审计/归档、文件和验签公钥。数据库/文件同截点并证明实际可取回；缺对象、未知设备、缺审计或调整链继续隔离。新迁移版本不在已有 V33 工具支持范围，须另批扩展，不能重标 V33 已通过记录。

原 sourceRecoveryEpoch 不改，当前恢复会话/UUID 映射另列；没有共享 fence 和证据能力实现时不能跨代次写。VERIFIED_COMPLETE_AT_STOP、RECONCILED、备份 PASS 均不授予出库放行或清理能力，`localDeletionAllowed=false` 保持。
