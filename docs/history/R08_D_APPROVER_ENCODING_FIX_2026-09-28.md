# R08 D 审批人ID编码修复与验收（2026-09-28）

## 1. 范围与结论

修复提交 089b335 已推送 origin/codex/development。本次是片2自身的审批人ID编码缺陷修复，不是为适配片3而改片2。生产代码只修改 CorrectionAdjustmentSource 查询中的一列表达式，其他片2逻辑不变。

attempt 488 真实HTTP导出通过，XLSX/PDF审批人内容与库内事实一致；R08 run29 的 R08ExportIT 当次14项通过。片3请求最小闭环的既有内容阻断和默认v1回归缺口由本次证据补齐。D四项边界继续待决策，不扩大为完整D导出验收或生产发布。

源码版本事实见[看板事实块](../ORCHESTRATION_STATUS.md)。本轮未改契约、生产默认值、R05/R06/R07业务、ConfirmedV1Adapter或渲染器；未开放开关、发布、部署或合main。

## 2. 项目惯例与最小修复

- common/Ids.java 的 bytes(UUID) 按UUID高低64位写入16字节，供数据库存储。
- CorrectionStore.java 的 approveDocument() 以 Ids.bytes(approvedBy) 写入 approved_by；同类查询 document 的 approvedBy 已使用 BIN_TO_UUID(approved_by)。
- CorrectionAdjustmentSource 同一查询的 line_id、document_id、dispatch_id 本就使用 BIN_TO_UUID。项目在这些ID上采用标准带连字符UUID文本，不采用Base64或自定义hex作为对外ID。
- 修复：将 d.approved_by 改为 BIN_TO_UUID(d.approved_by) approved_by。保留原别名、空值处理、查询范围、排序、调整分类及所有业务逻辑。第102–103行的原字符串读取因上游已返回UUID文本而正确，不另造Java编码器。
- 代码与测试工具共3文件：CorrectionAdjustmentSource.java、CorrectionExportHttpChecks.java、tools/verify-corrected-export-approvers.py；其中生产代码仅1行变化。

## 3. 同类排查清单

静态候选扫描覆盖1457个Git跟踪的Java/Kotlin源码、测试及构建脚本；365个文件包含识别到的二进制变量，2554行命中字符串转换候选。另检索直接Map取值后toString、String.valueOf、Objects.toString、new String、数组直接toString/append及Web二进制处理。对动态Map候选继续检查SQL投影、数据库类型与调用方。此为静态排查，非形式化全路径类型证明。

| 候选/类别 | 核对结论 | 处置 |
| --- | --- | --- |
| CorrectionAdjustmentSource.approver | BINARY(16)直接String.valueOf，已确认真缺陷 | 本次唯一生产修复 |
| CommandRecord.text | 当前调用读取文本/数字；二进制取bytes，ID取id并经ByteBuffer转UUID | 未发现同类缺陷，不改 |
| SourceEvidenceIntakeReceipts.text | 哈希文本走text，回执二进制走bytes | 未发现同类缺陷，不改 |
| CorrectionStore.createCommandBytes | byte[]分支先原样返回；String.valueOf只在非byte[]后备分支 | 未发现同类缺陷，不改 |
| CorrectionStore/CorrectionOperations及其他ID Map读取 | 对应SQL已BIN_TO_UUID；数值/JSON/状态字段为文本或数值 | 未发现同类缺陷，不改 |
| TemporaryShortApprovals、TemporaryLinkTarget、DraftEvidencePreparation | principal/snapshot等ID在查询中已BIN_TO_UUID | 未发现同类缺陷，不改 |
| PairingChallenges.nonce | 数据库为CHAR/VARCHAR文本，不是BINARY ID | 不改 |
| PdaHttp.body、PairingUpgradeTest.evidence | 扫描变量名误报；实际为JSONObject的toString | 不改 |
| new String / Kotlin带Charset的toString | JSON/协议/原始文件的显式字符集解码，不是UUID格式化 | 不改 |
| Web Uint8Array | 下载摘要按单字节转hex；签名保持原始字节或Base64解码 | 未发现同类ID缺陷，不改 |

未发现其他已确认同类缺陷，未扩大修复范围。原始检索及候选JSON保留于主树 .local/d-approver-fix-20260928/。初次检索有不存在的shared目录与正则错误，已改为实际仓库范围并完成复扫；不能用失败检索作为“无问题”证据。

## 4. 内容断言补强

- 在真实HTTP导出检查中，按更正单ID和订单ID直接读取库内 approved_by 原始16字节；要求导出值不是[B@…、可解析为UUID且为规范文本，再用项目 Ids.bytes 还原并逐字节相等比较。
- 对XLSX、PDF及XLSX重试分别执行上述检查，保存各自的快照和数据库审批事实。证据中的hex仅用于无损保存库内16字节，不是新API或导出ID格式。
- 内容回读工具独立使用Python UUID(bytes=...)生成期望文本，按更正号+行ID对应XLSX审批人列，核对PDF审批人值和出现次数、凭据引用，并核对两种格式的快照。
- 保留本夹具原2包/601.5kg、调整-2包/-601.5kg、净0包/0kg的内容断言；不把此全归零夹具的通过推广为部分更正样本通过。

## 5. 当次验收

| 项目 | 结果与证据等级 |
| --- | --- |
| 主树compileTestJava | 成功 |
| attempt 488 / E2aCorrectionChainIT | 1/0/0/0，started/completed与HTTP完成marker齐全；真MySQL+真服19434+真实HTTP申请/下载；更正前置为既有PDA Bearer链，导出为实际Web会话/CSRF |
| XLSX内容 | 7表；2条审批人UUID逐值匹配库内事实；更正号/行ID对应；数量重量通过 |
| PDF内容 | 2页、2492字符；2处审批人UUID逐值匹配库内事实；版本、凭据引用、数量重量通过 |
| PDF视觉 | 使用既有pypdfium2渲染两页后目视核对，审批人UUID完整可读，无本次字段溢出/裁切 |
| R08 run29 / R08ExportIT | 14/0/0/0；当次默认v1回归通过，专用真实MySQL+MockMvc，不能写成这14项全部真HTTP |
| 自审/独立审查 | 作者自审已做；未启子代理，未做独立审查 |

本轮没有红色业务IT attempt。488和run29收尾均重现已知cleanup空值警告，进程退出0且测试端口已释放；警告原样保存，未在本次编码修复中修改harness。新Python工具首轮暂存检查提示EOF空行，已去除并复验；未修改任何测试逻辑来掩盖失败。

## 6. 验收盲区与教训

**片2 attempt 486 没验“审批人ID内容正确性”**：当时只验字段存在、表/凭据数量以及数量/重量，没有将审批人值与库内事实对照，所以非空的Java对象标识漏过。487内容回读才发现，488补齐了值与来源的一致性验收。

教训：内容验收要验“值正确”，不能只验“字段非空”。后续断言应逐项覆盖所有关键字段的值及其权威来源：口径、版本、订单/更正/行ID、审批人、时间、原因、数量/重量、抬头前后值、凭据引用。本次新增覆盖集中在审批人身份及其关联ID，未声称其余所有字段/样本都已全面验收。

## 7. 证据、工作树与断点

- 主树：D:/acrylic-pda-outbound，codex/development；修复提交089b335。生成事实块是生成时点快照，随后文档提交的HEAD以Git为准。
- 验证树：D:/acrylic-pda-worktrees/r06-e2a-migration，保持detached ea4d166；原有脏文件未清理。同步前核对两个目标文件与主树改动前Git内容一致，仅同步上述两个Java文件；前像、哈希清单及原状态保存在主树 .local/d-approver-fix-20260928/。
- 488根目录：D:/acrylic-pda-worktrees/r06-e2a-migration/.local/e2a-cross-20260925-488/。保留integration-test.log、TEST-E2aCorrectionChainIT.xml、全部marker、storage/corrected-http下的XLSX/PDF、分格式snapshot/approval-facts、approval-content-readback.json、提取文本及两页PNG。
- R08根目录：D:/acrylic-pda-worktrees/r06-e2a-migration/.local/r08-it-29/。保留integration-test.log、TEST-R08ExportIT.xml和全部运行证据。
- 主树本机目录还保留两次console日志、原始检索、候选JSON与同步清单。.local、临时密钥、数据库、产物均未提交；本机证据保留不等于异地备份。
- 单助手写权，无其他已发现活动写者；两次测试已结束，19330/23308/19434无监听，系统MySQL3306未操作。下次attempt从489+查重；R08专用run从30查重。验证树继续保留。

## 8. 未验边界与下一步

D四项导出边界——v2→v3、撤销后快照、抬头更正、部分更正——仍未开工，交回用户决策。既有更正业务链的动作不替代这四项导出验收。不自动切入③、②、A3剩余回归、R09或任何生产动作。
