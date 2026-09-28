# R08 撤销后导出：设计定性与修复（2026-09-28）

## 1. 只读定性：A

基线4a8e99c，codex/development。第一步只读设计、实现及历史证据，没有修改ExportFacts或任何生产代码。用户已明确授权判定A后修R08；判定B须停，本轮不属于B。源码版本事实见看板生成事实块。

- R07_3_IMPLEMENTATION_READY_DESIGN.md §4（行40–45）：结果按原确认+累计差额计算、指针仅指真实版本、发布后只读；§7（行72–76）：原批准与撤销凭据保留，逐笔回退。两节原则不能单独曲解为“回指v1”。
- 同文档§21（行367–381）明确“新结果版本v+1”“source_correction_id仍指本文档”、逐包快照重建/双指针同步/文档WITHDRAWN；§27（行499–507）将动作细化为WITHDRAW_PENDING只等待、WITHDRAW_CONFIRM才逐笔回退并出新结果版本。
- CorrectionOperations.WithdrawConfirm锁定当前版本，version=previous+1，insertResultVersion/insertEffectiveScan/insertEffectiveLine、moveCurrentPointer、confirmWithdrawal及CORRECTION_WITHDRAWN发件箱同事务执行；CorrectionStore.confirmWithdrawal仅从WITHDRAW_PENDING转WITHDRAWN。后者是终态，不是中间态，原effective_result_version保存原批准生效版本，不能为导出方便改写。
- 因而491库内v3是合法、不可变的撤销后状态，不是副作用；数量恢复不等于版本指针回到v1。原v1/生效v2/撤销v3均有各自历史意义。缺陷属R08只读消费面，不改R07撤销逻辑。

## 2. 486历史证据更正

486/correction-chain.txt及片2交付记录已核对：更正后导出发生在撤销前，导出结果版本2、原2包/601.5kg、调整-2包/-601.5kg、净0包/0kg。随后WITHDRAW/非申请人拒绝/WITHDRAW_CONFIRM及重复撤销拒绝跑过，但未对撤销后的版本生成文件。片2记录“撤销即归零已实现”属于未跑的映射声明，不能当作撤销后导出已验。

“撤销归零”在本次指更正累计净调整为零；原确认的2包/601.5kg恢复，不能把撤销后订单净结果再置零。491的库事实与此一致。

## 3. 已授权修复路线与验收标准

仅修R08读取和内部渲染凭据：按order_result_version.source_correction_id锚定合法结果版本；历史v2保持原调整，v3追加引用原行、具备撤销确认人/时刻与反向账引用的撤销凭据，显式验证正反对应。不更新correction_document.effective_result_version，不修改R07业务、ConfirmedV1Adapter、生产配置/开关或发布契约。

查询放行并不足以完整验收：原映射按文档的最终WITHDRAWN状态把前后值都变零，不能表达不可变的v2与恢复原包的v3。此为本次已授权撤销导出故障链的组成部分，将一并在R08内部修正，不用跳过校验器来放行。

要求：真MySQL/真HTTP申请撤销v3的XLSX/PDF并逐值回读2包/601.5kg和撤销凭据；撤销后重新导出历史v2仍保留原更正；默认v1回归；R08ExportIT当次14项。保留491红证据。下一attempt从492+、R08专用run从30查重。撤销通过且时间盒有余量才补抬头，发现新的真bug立即停报。

## 4. 第二步修复交付：0e837ff

代码与测试提交0e837ff已推送codex/development。ExportFacts从固定结果版本source_correction_id读取原生效与撤销终态版本；CorrectionAdjustmentSource按实际版本链重建凭据，原调整只计正向账，不再随文档终态把历史v2前后值改零。新增只读CorrectionWithdrawalSource核对真实RECORDED确认回执、确认人/版本/文档及每笔精确反向账，输出显式CORRECTION_WITHDRAWAL；审批人仍为原审批人，确认人及回执/账ID另列证据引用。内部AdjustmentValidator仅允许显式撤销复用原更正行，要求完整逆转、原凭据存在、无重复撤销，原版本链及净值守卫保持。

没有改correction_document.effective_result_version，也没有改R07撤销实现、R05/R06/R07生产逻辑、ConfirmedV1Adapter或开关/生产配置。外部契约未变、未发布；内部调整枚举新增，不改变快照记录字段。

## 5. 当次证据

- 渲染单测129/0/0/0，含5个撤销正反例；compileTestJava与installDist成功，真实HTTP测试使用重建后的主树安装目录。
- attempt493：E2aWithdrawnCorrectionExportIT为1/0/0/0。隔离真MySQL+真服+PDA Bearer的CREATE/APPROVE/WITHDRAW/WITHDRAW_CONFIRM，真实Web会话/CSRF申请并下载文件。
- v3：库内2包/601500g，正反账净0，2条有效行和2条调整。快照对照库事实逐项断言ID、版本、前后数重、理由、时间、原审批人原始16字节，以及真实确认人/回执/正反账ID。XLSX/PDF独立内容回读PASS；PDF3页2965字符，三页渲染目视无裁切或重叠。
- 撤销后历史v2：快照orders与撤销前保存快照完全相等；仍1包/301kg、1条原凭据；XLSX/PDF内容PASS，PDF2页2185字符。没有通过改历史事实凑v3。
- 默认v1：带原本必填的basis=ORIGINAL_CONFIRMED、不传resultVersion，真实HTTP成功导出XLSX，快照版本1/无调整/两条原始明细。
- R08专用run30：R08ExportIT 14/0/0/0，真实MySQL+MockMvc。与493外部真实HTTP证据分列，不相互冒充。
- 作者自审，未独立审查；未改导出文件。部分更正与连续更正的489/490是历史证据，493仅新增了撤销后历史v2兼容验证。

## 6. 失败及运行留证

- 首次渲染129项2红：主树默认浏览器路径不存在，ConfirmedV1FilesTest/PdfRendererTest缺Chromium；非业务缺陷，原XML与日志保留。仅在忽略目录建立Gradle init脚本，使用已有r08-export树的Chromium后全绿，不改项目配置。
- attempt492整体1/1/0/0：v3和历史v2四个文件已成功、内容回读正确，最后测试默认v1请求漏传既有必填basis，返回400 INVALID_REQUEST；只改测试输入，不改变API语义。493使用新编号复验绿；492不得改写成整次绿。
- 493与run30保留既有harness cleanup变量空值警告；均测试成功并先停库释放端口，未自行修工具/删失败库。
- 本机总留证：主树.local/d-withdraw-fix-20260928/包含前像/源码哈希、首次红及最终绿渲染XML、492/493/run30独立XML和日志、PDF页图。实际HTTP请求响应、库事实、快照、下载文件在验证树.local/e2a-cross-20260925-492及493/storage/withdrawn-correction/；historical-v2和default-v1各分目录，密钥不提交。

## 7. 第三轮入口

撤销修复验收完成后仅补抬头：沿既有headerBody的无dispatch样本，不新造业务场景。若真HTTP证实新缺陷即停报告并保留红证据，不扩本修复。四边界此时为3通过/1待测，D整体尚未交付；不转R09 C2/A3。抬头撤销、多文档跨链撤销与撤销后再更正组合也未新增验收。

## 8. 第三轮抬头补测：494发现新缺陷，立即停工

失败测试阶段备份48609c1已单独提交推送；撤销验收文档0797228已推送。新增fixture规模小：复用既有E2aCorrectionChainIT.headerBody，只把该测试方法改为包内可见；沿已验的无dispatch、无lines、标量orderNo更正，不构造新业务流程，不直接伪造业务事实。

真实HTTP的CREATE/APPROVE都成功：更正文档HEADER_CORRECTION/EFFECTIVE，lineCount=0；v1的orderNo=FH-LINKCONFIRM-0001，v2冻结header_snapshot已为CORR-HEADER-EXPORT-1。两版均2包/601500g，批准回执scanCoverage=2、effects/ledgerKeys为空，符合抬头不改实物的样本形状。

attempt494为1/1/0/0，异常为EXPORT_SOURCE_UNAVAILABLE的BusinessException，“该结果版本没有可追溯的更正凭据行”。调用栈：HeaderCorrectionExportHttpChecks.captureFacts → ExportSnapshotReader → CorrectionExportSource → CorrectionAdjustmentSource.read非空守卫。**证据边界：这是实际库事实上的服务读取失败；发生在测试预检，尚未发送导出HTTP POST，也未进入作业、渲染、下载与XLSX/PDF内容断言。** 不将其写成HTTP导出409，不写成容器或内容通过。

静态定位：R08凭据读取内连接correction_line，并仅保留有账行的调整；合法抬头文档没有correction_line，因此结果为空。对照本轮初始4a8e99c，原查询同样从correction_line出发并过滤无账行，原header(row)存在不代表真实无明细抬头可到达。此是既有R08消费缺口，不归因于本轮撤销写入（本轮未改R07）。后续若授权修复，应先核定抬头冻结事实和文档/回执证据的映射及报告字段，不得虚构dispatch或账行，也不能仅删除非空校验来放行。

本轮遵守新真bug即停：没有修任何抬头生产代码，没有改测试期待为失败，没有重新分配attempt去扩大验证。失败库和日志保留。证据在验证树.local/e2a-cross-20260925-494/，storage/header-correction/仅四件：command-create.json、create-correction.json、approve.json、database-facts.json；主树.local/d-withdraw-fix-20260928/保存attempt494-IT.xml、attempt494.log和header-source-manifest.json。无candidate-snapshot/导出请求/产物。

## 9. 四项边界与接续

| 顺序 | 边界 | 截止本轮结果 | 证据 |
| --- | --- | --- | --- |
| 1 | 部分更正且净结果非空 | 通过 | 历史489；493历史v2再次回读非空内容 |
| 2 | v2→v3连续更正 | 通过（历史） | 490及完整链/缺链反例；本轮未重跑该HTTP样本 |
| 3 | 撤销后版本导出 | 通过 | 0e837ff；493真HTTP及XLSX/PDF逐值内容，R08 run30 14项回归 |
| 4 | 抬头更正无dispatch | 失败，已停 | 48609c1阶段备份；494真命令成功，快照读取失败，导出HTTP/内容未验 |

D仍不能整块交付；用户待决定是否另行授权抬头消费修复，R09 C2/A3不自动启动。未验：抬头导出全链及内容、抬头撤销、多文档撤销与撤销后再更正组合、独立审查及现场；不外推单样本通过。

主树codex/development，停工代码HEAD48609c1（后续仅文档提交）；验证树仍detached ea4d166。初始/最终全量status与同步哈希/前像均保留.local/d-withdraw-fix-20260928/；没有提交/重置验证树。端口19330/19434/23308均已释放，系统3306未动，无活动测试进程。下一e2a attempt495+、R08专用run31+执行前查重。完成定性/撤销修复/抬头补测共三轮，至此结束。

## 10. 改动清单

- 实现0e837ff：exports目录ExportFacts、CorrectionAdjustmentSource、新CorrectionWithdrawalSource；report-rendering的Adjustment/AdjustmentValidator及新WithdrawalValidationTest；撤销IT和WithdrawnCorrectionExportHttpChecks；只读文件回读工具。合计9文件，详情以git show为准。
- 失败测试48609c1：E2aCorrectionChainIT仅headerBody测试可见性；新增E2aHeaderCorrectionExportIT、HeaderCorrectionExportHttpChecks。合计3文件，无生产代码变动。
- 文档：本报告、看板、欠债v31–v33、四边界报告续记、历史/验收索引；三次替换前看板原文归档。全部本机密钥、数据库、回执/导出文件及日志均未暂存。
