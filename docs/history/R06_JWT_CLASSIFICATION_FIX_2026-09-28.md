# R06 JWT状态误分类：先定性与最小修复（2026-09-28）

## 1. 只读定性：a，分类不精确

输入a257e11，主树D:/acrylic-pda-outbound、codex/development，与origin一致且干净；仅当前助手写入，测试端口空闲。第一步只读，未改生产代码。源码版本事实见[看板生成块](../ORCHESTRATION_STATUS.md)。历史500的实际失败见[A3记录](A3_NON_E2A_REGRESSION_2026-09-28.md)。

损坏样本invalid.token.signature在SourceEvidenceProof.verify第41行触发SubmissionJson.parse的JSON解析失败；后者包装成BusinessException(400, INVALID_SUBMISSION)。verify第53行把所有BusinessException透传，read第29–30行把它们统一映射成UNVERIFIABLE_KEY，误覆盖应有的INVALID_SIGNATURE。异常发生在publicKeyFor之前，与本机缺钥无关。

该字段是terminalProof.verification，只读证据状态，**不是认证HTTP错误码**。详情GET按设计允许读取不可信的原证据并返回200，不代表损坏JWT通过验签。证据链：

- SourceEvidenceService.read先调用SourceEvidenceAccess，按当前temporary_order:confirm、仓库和原来源身份鉴权，随后只读返回证据；不会凭verification授予读取权限。
- SourceEvidenceView.assessment对所有非VERIFIED状态加入VERIFIED_TERMINAL_PROOF_NOT_AVAILABLE；七项写入/确认/释放/续扫/删除等能力无条件false。
- SourceEvidenceIntakeLocalPayload.requireHead只接受VERIFIED；INVALID_SIGNATURE与UNVERIFIABLE_KEY都被同一E2A_ABORT_PROOF_UNVERIFIABLE拒绝，不能进入后续可信事实接纳。
- 检索服务端、Web、Android生产调用方：没有发现根据这两个具体状态区分授权或自动重试的逻辑。它们会影响人工排查含义（损坏签名与缺少可信钥），但没有证据表明本缺陷导致应拒绝的放行或有效JWT误拒。
- 契约temporary-source-evidence-schemas.yaml已分别列出这两个状态；路径契约和R06_SOURCE_EVIDENCE_GUIDE说明只读、不放行。无需更改或发布契约。

**判定a，不是b。**“拒绝”指拒绝把JWT当可信凭据，而非拒绝合法鉴权后的证据查看请求。该判断限于本次已知样本及已检索消费路径，不宣称全系统无其他安全问题。

## 2. 已授权的最小路线与验收

仅收窄SourceEvidenceProof.verify的BusinessException分类：真正SIGNING_KEY_UNAVAILABLE继续透传给现有read映射；解析/其他业务错误返回INVALID_SIGNATURE。不改RSA验签、算法/类型/keyId、issuer/audience/receipt匹配、可信钥来源、权限或能力决定逻辑。

补覆盖可解码但非JSON的JWT头、已签名但非JSON的claims、实际key provider不可用及有效签名；IT保留500原断言并补七项能力false、原证据不改，以及实际历史公钥不可用的只读状态。定向IT复验后跑认证相关IT，证据等级分列；每个新attempt执行前查重，500及所有红记录保留。

本项按用户已给的a分支授权执行，不另索授权；若出现新的安全边界问题或其他真bug，立即停报。R09 C2只另写范围/验收，不接生产入口。A3剩余7类在本项完成且有余量时继续。

## 3. 最小修复与当次验证

修复提交 **39d1b84**，已推送codex/development；定性先行提交4b25bca。生产差异仅SourceEvidenceProof.verify的一个catch：SIGNING_KEY_UNAVAILABLE保持透传，其余BusinessException返回INVALID_SIGNATURE。外层read仍将真实缺钥映射为UNVERIFIABLE_KEY；签名算法、可信钥来源、签名字节、claims约束、鉴权及七能力均未改。

测试共改两文件，复用原fixture，无新业务场景构造：

- TemporarySourceEvidenceProofTest加入可解码但非JSON的头、签名正确但claims非JSON、provider真实错误码的透传断言；原有效签名及未知keyId覆盖保留。
- TemporarySourceEvidenceIntegrityIT保留500的原失败断言，并核对原回执不变、缺口提示、七能力false及库/文件事实不变。测试配置独立指向不存在的历史DER；有效当前钥先VERIFIED，未知kid触发实际OfflineSigner读取缺失DER后为UNVERIFIABLE_KEY，恢复原回执又VERIFIED。测试配置不进入生产默认值。

| 验证 | tests / failures / errors / skipped | 结果及证据等级 |
| --- | --- | --- |
| 修复前新增定向单测 | 6/2/0/0 | 两红均为已知分类缺陷：非JSON头/claims抛BusinessException；故意先复现，完整保留 |
| 修复后定向单测 | 6/0/0/0 | TemporarySourceEvidenceProofTest通过 |
| attempt501 TemporarySourceEvidenceIntegrityIT | 6/0/0/0 | 损坏、真实缺钥、有效凭据及原件保持 |
| attempt502 TemporarySourceEvidenceIT | 5/0/0/0 | 来源证据正常读取回归 |
| attempt503 TemporarySourceEvidenceAccessIT | 16/0/0/0 | 身份、权限、仓库及恢复状态读取回归 |
| attempt504 PdaAuthenticationIT | 11/0/0/0 | PDA认证、设备签名、刷新和会话隔离回归 |
| attempt505 PdaSigningUnavailableIT | 1/0/0/0 | 缺签名密钥不发资格，原订单状态保持 |

IT合计5类39项全绿、无skip。每类一个独立MySQL库，Spring/MockMvc请求；**不是外部真服HTTP验收**。按XML、迁移日志、退出码及端口释放核对，非e2a没有one-shot marker不代表未跑。单测命令曾带不存在的SourceEvidenceIntakeLocalPayloadTest过滤项，未产出该类XML，**不计消费者单测覆盖**。

执行前34棵工作树.local查重501–525；本段仅使用501–505。本机证据根.local/jwt-classification-fix-20260928/保存修复前后单测日志/XML、allocation-check.json、verification-manifest.json、integration-console.log及verified-results.json（逐XML哈希）。IT报告位于.local/it-batches/batch-20260928-125249-start501、125327-start502、125404-start503、125451-start504、125528-start505；对应隔离库/日志/storage位于.local/e2a-cross-20260925-501至505。历史500红未删除、未改原断言，不被本次绿色覆盖。

## 4. JWT / 签名 / 密钥异常映射扫描

输入39d1b84。从git跟踪的1762个Java/Kotlin/TS/TSX/JS/Python/PowerShell文件中检索，再定向核对891个生产/工具文件中的JWT、signedReceipt、签名/公钥/密钥关键词及catch/状态映射；49文件命中不等于49缺陷。路径与行级原始清单保存在.local/jwt-classification-fix-20260928/signature-exception-scan.json。本表只报告已读到的分类行为，未把静态候选当HTTP复现，也未扩大修复。

以下Java路径均相对server/src/main/java/com/acrylic/outbound/。

| 位置 | 已读分类行为 | 判断与后续 |
| --- | --- | --- |
| temporary/evidence/SourceEvidenceProof.java:53 | 解析BusinessException曾被当缺钥透传 | **本次确认并修复**；501与单测验证 |
| temporary/command/CommandSignature.java:18–19；CommandReceiptIntegrity.java:21 | publicKeyFor也在总catch内，钥不可用与损坏都转COMMAND_RECEIPT_INTEGRITY_FAILURE | **诊断分类候选**：可能丢失缺钥含义；未复现、未判定违反既有“原命令不可核验”契约，单列评估，不改 |
| submissions/SubmissionArchiveProofs.java:36；SubmissionArchiveEvidence.java:28–31；audit/AuditArchiveReader.java:33；temporary/TemporaryArchiveEvidence.java | 多种解析/签名/缺钥问题统一ARCHIVE_EVIDENCE_UNAVAILABLE；内容摘要不匹配有独立分支 | **聚合码待评估**：有明确归档不可核验语义，不能等同本次已规定两种状态的缺陷；不改 |
| temporary/draft/DraftPreparationProof.java:32；DraftEvidenceReceipts.java:59–66 | 底层用已取出的钥验签，外层把解析/lookup/验签错误统一DRAFT_EVIDENCE_UNAVAILABLE | **聚合码待评估**；无本次同型BusinessException误透传；未实际缺钥组合验证 |
| temporary/protocol/E2aProtocol.java:69–86；temporary/evidence/SourceEvidenceProof.java的DER转换 | E2a未知kid为UNVERIFIABLE_KEY，格式/验签异常为INVALID_SIGNATURE；传入坏DER也落后者 | **参数/钥分类候选**：服务端正常provider先校验DER，尚未证明真实路径可到达坏DER；不改 |
| temporary/evidence/SourceEvidenceIntakeReadIntegrity.java:25–29、67–69；SourceEvidenceIntakeReadService.java:42 | 503原样保留，其余错误为完整性失败；只读可报告UNVERIFIABLE_KEY，可信取回仍拒绝 | 已有区分；按status而非错误码透传是宽泛条件，记录待评估，未证实错误分类 |
| temporary/evidence/SourceEvidenceIntakeAuditArchives.java:53–57；SourceEvidenceIntakeDeviceKeys.java:45；SourceEvidenceIntakeVerifyService.java:107 | 归档保留特定unavailable码；损坏已登记设备钥转E2A_STOP_KEY_UNVERIFIABLE；STOP不可信用专用拒绝码 | 来源/层级不同，未发现损坏凭据变可信的证据；未扩大为全组合通过 |
| pda/OfflineSigner.java；devices/DeviceCrypto.java:27–37；temporary/protocol/LocalDraftStopProtocol.java | 服务端配置钥异常明确SIGNING_KEY_UNAVAILABLE；设备输入坏钥INVALID_DEVICE_KEY、坏证明INVALID_DEVICE_PROOF；STOP verifier失败为false | 没有本次解析错误冒充服务端缺钥的同型透传；504/505只覆盖表内列明的认证范围 |
| android/.../evidence/E2aReceiptStore.kt:45；android/.../update/UpdateManifestVerifier.kt | 原件逐钥校验最终统一“签名或来源不可核验”；升级区分公钥读取，验签失败拒绝 | UI诊断信息聚合，未发现成功放行分支；本次未跑Android组合 |

其余命中包括分页游标、配对重试/存储JSON、文件原子保存、网络/UI错误展示、时间格式、下载配置及只读字符串提取；未发现它们把JWT解析异常转为UNVERIFIABLE_KEY。以上候选缺少契约违背或动态复现证据，**不标记为新确认bug，不强行全修**；后续需先核对各自错误码契约和调用方处理。

## 5. 交付限制与接续

作者自审，非独立审查。未做外部真实HTTP、Android组合、生产环境、整套认证/全量IT；39项和6项不外推全系统安全结论。未改R05/R07、ConfirmedV1Adapter、契约、数据库结构、生产默认值或开关，未发布/部署/合main。

JWT授权范围验收完成，A3原首批剩余7类另以506–512接续，失败即停；结果写入A3记录，不冒充本节JWT定向验证。R09 C2另写独立范围文档，实施继续待授权。
