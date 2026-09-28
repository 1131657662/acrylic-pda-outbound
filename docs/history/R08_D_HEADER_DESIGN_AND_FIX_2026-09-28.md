# R08 抬头导出：设计定性、修复与假设扫描（2026-09-28）

## 1. 第一轮只读定性：A

基线1b03f34，主树D:/acrylic-pda-outbound、codex/development，干净并已推送；验证树仍detached ea4d166，既有改动不重置。单助手，测试监听空闲，无已发现活动重型任务。源码版本事实见看板生成块。第一步仅核对设计与实现，没有改R08代码。

R07_3_IMPLEMENTATION_READY_DESIGN.md §26 B3（493–497行）明确：header_snapshot为冻结成功回执JSON，HEADER_CORRECTION使用允许已有键、排除服务端持有键的标量补丁；未知键/非标量/空操作拒绝，新版本写合并结果，旧版本不变，不产生effect或账务行；补丁只存在该单冻结CREATE命令字节，APPROVE从不可变回执读回，无需新列。§25收口项也明确未实现文本明细模型，correction_line是数值模型；早期§2的HEADER可空dispatch草案不能覆盖后续B3明确收口设计。

实现一致：CorrectionOperations.Create对headerPatch验证，拒绝非空lines，循环主动使用空数组；所以不调用insertLine。Approve读取CorrectionStore.createCommandBytes(documentId).headerPatch合并当前版本header_snapshot，新建版本；空lines使effect/账务循环无写入，但依然复制全部原dispatch有效快照。494真实CREATE/APPROVE、lineCount=0、v2抬头正确及scanCoverage=2是历史实证，不是缺明细的R07写入故障。

**判定A，不是B。**零明细是设计，不改R07，也不补伪造的correction_line/effect/ledger。

## 2. 凭据语义及已授权最小路线

抬头调整是“经批准、已写入该结果版本的字段变化”，不是实物出库差额。权威来源为correction_document身份/审批事实、该文档RECORDED CREATE命令里的headerPatch及真实回执ID、相邻不可变结果版本header_snapshot的前后值；检查补丁、前后版本及文档相互吻合。数量/重量均0差额，dispatchId与不存在的lineId留空，不把回执ID伪装成明细ID。evidenceReference明确引用真实CREATE回执/文档/字段。

现有Adjustment已带headerField/beforeText/afterText/reason/approver/evidenceReference，足以表达，不新增数据库结构或快照DTO字段。R08按文档单独读取零明细抬头，再与实物/撤销调整合并为版本链；渲染校验对抬头以文档+字段去重，只允许抬头无数值明细。保留原单号身份并据已验抬头调整显示该版本单号和前后变化；默认v1无调整时行为不变。若实现中发现必须改变结构，则依用户硬边界停止。

本轮按已明确授权的A分支接续，不重复要求批准；采用测试、安全SQL和单一职责约定，用户最小范围优先于无关重构。只修R08只读消费/渲染，不改R05/R06/R07、ConfirmedV1Adapter、默认开关或发布契约。

## 3. 验收及时间盒

第一轮定性；第二轮最小实现、真实MySQL/HTTP抬头XLSX/PDF逐值回读、默认v1真实HTTP及R08ExportIT14项回归；第三轮只读扫描凭据/明细假设并收口。新真bug立即停报告；其他同类记录，不扩修。attempt从495+、R08专用run31+先查重。494失败及全部历史红证据保留，不通过删除校验或修改成功期望冒充验收。

## 4. 第二轮修复交付：68a43f8

实现68a43f8已推送。HeaderCorrectionSource单独读取无明细抬头文档，使用真实CREATE回执字节及摘要、回执/文档/申请人一致性、相邻header_snapshot补丁合并结果、审批身份/时刻和数重不变约束；不创建数值明细，不借用回执ID冒充lineId。Adjustment现有文本字段表达变化，lineId/dispatchId留空；evidenceReference列真实文档、CREATE回执及headerPatch字段和前后版本。未改任何表/列、OrderSnapshot/Adjustment字段或契约结构。

CorrectionAdjustmentSource合并实物/撤销及抬头凭据，按版本排序，保留非空、版本连续与物理逆转检查。渲染校验仅允许HEADER_CORRECTION无lineId，核对原值/字段/零数重并去重。OrderSnapshot.number仍是原单号身份；文件正文按已校验的orderNo调整链显示本版本单号，并另列原单号和前后值。原确认无调整时显示逻辑不变。支持的显示键限现有orderNo/vehicle/container/remark；其他已有标量键显式拒绝，未用默认空值放行。

## 5. 本轮验收

- attempt495：E2aHeaderCorrectionExportIT 1/0/0/0，真MySQL、真服、PDA Bearer CREATE/APPROVE、真实Web会话/CSRF申请下载XLSX/PDF；没有用服务DTO调用替代导出HTTP。
- 样本仍复用两包原确认，仅orderNo从FH-LINKCONFIRM-0001改为CORR-HEADER-EXPORT-1；lineCount=0、effect=0、v2有效行仍2包/601500g。快照逐项核对前后字段、真实回执ID、原因、审批时刻、审批人原16字节、有效dispatch/数量/重量，以及空明细ID。
- XLSX/PDF只读独立回读PASS：有效明细2行、抬头凭据1行；新单号在标题/汇总/明细正确显示，原单号及前后变化保留；PDF两页2300字符，页图目视无裁切/重叠。
- 同一495还经真实HTTP验证basis=ORIGINAL_CONFIRMED且不传resultVersion：v1原单号、无调整、XLSX下载成功。R08专用run31的R08ExportIT为14/0/0/0（真MySQL+MockMvc），与外部HTTP证据分列。
- 渲染全套134/0/0/0，新增5项包括零明细、前值错误、同字段重复、物理lineId仍必填及默认显示不变；compileTestJava与installDist成功。最终仅移动一处方法注释，运行逻辑与实测代码一致，已同步验证树哈希。
- 作者自审，未独立审查。未改ConfirmedV1Adapter、R05/R06/R07、生产默认值/开关，不发布契约。494失败保留；本轮无红业务attempt，495/run31既有cleanup空值警告保留。
- 主树.local/d-header-fix-20260928/保存编译日志、134项XML、495/run31独立XML/控制台、页图、验证树前像和10文件同步哈希。495真实回执、库事实、快照及文件在验证树.local/e2a-cross-20260925-495/storage/header-correction/，default-v1子目录独立。未提交任何本机密钥/数据/产物。

## 6. 第三轮待做

仅只读扫描correction_line/effect/账行存在性及相关前提，检查撤销/抬头/部分/连续更正场景；分类记录已修、合理约束、风险候选及确认缺口。不强行扩修。四项基本样本已有通过证据，但混合/跨文档、抬头撤销、其他标量字段及独立审查仍未验，D整块是否交付由用户决策。

## 7. 第三轮：凭据/明细假设扫描及停报（只读）

扫描1813个受跟踪代码/配置文件（Java/Kotlin/TypeScript/TSX/JavaScript/Python/PowerShell/SQL）；14个文件引用correction_line、correction_effect或correction_ledger，其中生产Java/Kotlin/TS/JS 5个：CorrectionOperations、CorrectionStore、CorrectionAdjustmentSource、CorrectionWithdrawalSource、HeaderCorrectionSource。另对更正、导出、渲染目录60个生产Java文件检查非空/行数/版本链守卫。原始命中在.local/d-header-fix-20260928/assumption-scan-table-hits.json与assumption-scan-guards.json；这是静态扫描范围，不等同运行1813项测试。

| 位置及假设 | 判定/场景 | 处理及证据边界 |
| --- | --- | --- |
| CorrectionStore.lines（190行）、CorrectionOperations.Create/Approve（80–107、248–289行） | 抬头明确零明细；实物更正必须有数值明细，Approve对空集合自然不写effect/账务 | 设计一致，495实际无line/effect且数重不变 |
| CorrectionAdjustmentSource（40–59行）：line JOIN、有正向账才读 | 抬头消费假设已修：先排除HEADER_CORRECTION，改由HeaderCorrectionSource分流；物理路径约束保持 | 68a43f8；495抬头通过，489部分/490连续/493撤销属历史证据 |
| HeaderCorrectionSource（24–60行）：零明细、前后抬头与冻结CREATE补丁一致 | 正确文档证据，不依赖effect或账行 | 495回执引用及前后值实证；多个标量字段/类型仍未验 |
| CorrectionWithdrawalSource（45–65行）：逐笔正反账与line对应；AdjustmentValidator（62–69行）仅允许实物撤销 | 对493的物理撤销是合理完整性要求；不能外推到抬头撤销 | 未改；R08也没有已验的抬头反向字段凭据路径 |
| CorrectionStore.ledgerEntries（289–295行）及CorrectionOperations.WithdrawConfirm（428–430行） | **确认静态阻断**：所有撤销都要求账行；B3抬头按设计无line/账，确认撤销必被空账守卫拒绝 | 新发现R07面缺口，立即报告并停止新增开发/测试，只记录。没有实际执行抬头WITHDRAW/CONFIRM，不声称真实HTTP复现 |
| CorrectionExportSource（62–63行）、SnapshotValidator（38行）要求快照/原dispatch非空 | 合理：每版本保留全部原dispatch，归零行仍存在；“有效明细为空”不等于原事实为空 | 全归零历史486及部分489/撤销493；未移除约束 |
| AdjustmentValidator的连续版本、前值匹配、净值对账与重复凭据守卫 | 部分更正不强求所有包都有调整；连续更正需完整v2..目标；撤销原凭据可在显式逆转下复用 | 原有490/493及当次渲染134项；混合抬头/实物链未实际验证 |
| TemporaryExportEvidence（50行）mappingVersion接受1/2 | 历史==1缺陷已修；代码仍限1/2，注释v2+不代表任意大版本通过 | 不重复修改；更高映射版本合法性未决，未把静态候选升格为新实测bug |
| TemporaryDuplicateModels及Web useTemporaryCommand | A2命令候选摘要已分离commandContextSha256；页面contextSha256不再直接当命令摘要 | 核对已有代码，历史dcaf7ad/377；本轮未重跑A2 |
| CommandBusinessIntegrity（81行）固定v1；ExportRangeReader/ExportSummary原确认汇总 | 已知③命令记录时版本欠债和②最新有效汇总未实现；原确认汇总自身不应默默切更正口径 | 仍待单独授权，本轮不改 |

确认静态阻断的推导完整：495实际抬头文档零line/effect；B3无需账；ledgerEntries内连接correction_line必得空集合；WithdrawConfirm在读版本/身份之后无类型分支，直接抛CORRECTION_LEDGER_CONFLICT。发起WITHDRAW也没有排除HEADER_CORRECTION，因而存在进入WITHDRAW_PENDING后无法确认完成的风险。未在数据库执行该场景，不增加attempt、不修改R07，也不通过伪造账行或删守卫绕过。

这与已验物理撤销493、抬头生效导出495是不同组合。唯一新增确认的同类阻断在R07写入面；其余列为已修、合理约束、已有欠债或待验证候选。按用户“真bug立即停报告”已停止开发和后续测试，剩余仅保存清单及交接文档。不能据此声称全系统无其他缺陷。

## 8. 四项状态与交付边界

| 风险顺序 | 基本边界 | 证据/结果 |
| --- | --- | --- |
| 1 | 部分更正且净结果非空 | 489通过；493历史v2再次回读通过 |
| 2 | v2→v3连续实物更正 | 历史490通过；本轮未重跑HTTP |
| 3 | 实物更正撤销后快照导出 | 493通过，0e837ff修复；本轮未重跑HTTP |
| 4 | 无dispatch的抬头生效导出 | 495通过，68a43f8修复；XLSX/PDF逐值、默认v1与run31回归通过 |

四项基本样本通过不等于所有组合完成。D整块是否接受为有明确限制的交付，交用户决策；建议把抬头撤销作为单独R07设计/修复项，先确认撤销前后抬头、并发更正冲突及R08反向字段凭据的语义，不在本轮直接实施。R09 C2/A3不自动启动。

未验保留：抬头撤销真实HTTP（已有静态阻断）、多个字段/其他标量类型、抬头与实物混合链、多文档跨链撤销/撤销后再更正、独立审查和现场。本轮没有增加数据结构，历史494红及495/run31已知cleanup空值警告保留。

## 9. 最终断点

本轮提交顺序：ee9b2ee只读定性、68a43f8实现、3ef1715验收文档；最终扫描/交接文档SHA以Git日志为准。均逐步推送codex/development。代码清单为68a43f8的11文件（3个R08服务读取文件、5个内部渲染文件、2个测试、1个回读工具），无R05/R06/R07生产文件、ConfirmedV1Adapter、契约结构、默认生产开关或配置变动。

主树D:/acrylic-pda-outbound；验证树D:/acrylic-pda-worktrees/r06-e2a-migration仍detached ea4d166，全部既有dirty/untracked保留，本轮只同步10个源码/测试文件，前像/最终哈希/前后全量status保存在.local/d-header-fix-20260928/。验证树未提交、重置或合并。

无活动重型任务，测试端口19330/19434/23308已释放，原库3306未动；下一e2a attempt496+、R08专用run32+执行前查重。三轮到限，单助手、作者自审，未独立审查。所有.local密钥/数据库/真实回执和导出文件均未提交；本机留证不等于异地备份。
