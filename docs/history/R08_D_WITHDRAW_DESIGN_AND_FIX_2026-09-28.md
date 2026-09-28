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
