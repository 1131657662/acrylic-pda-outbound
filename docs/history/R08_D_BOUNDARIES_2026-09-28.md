# R08 D四项边界补测（2026-09-28）

## 1. 授权与范围

按用户风险顺序：部分更正非空、连续更正、撤销后、抬头更正；2–3轮。仅测试和内容断言，不改片1/片2/片3、R05/R06/R07、ConfirmedV1Adapter或生产配置；真bug停报，不自动修复。契约未改、未发布，未开放生产开关。源码版本事实见看板生成事实块。

## 2. 第一轮：部分更正非空

测试提交b523eff，已推送origin/codex/development。复用既有两包夹具，通过PDA Bearer真实CREATE/APPROVE仅归零第一包，不造新业务场景、不直接改业务事实。原2包/601.5kg，调整-1包/-300.5kg，净1包/301kg。

- 主树compileTestJava成功；attempt489的E2aPartialCorrectionExportIT为1/0/0/0，started/completed与HTTP完成marker齐全。
- HTTP申请XLSX/PDF、真实worker/渲染子进程、状态查询及下载通过；下载字节等于已发布artifact。使用隔离MySQL/19434真服，更正为PDA Bearer，导出为Web真实会话/CSRF。
- SQL权威事实与快照逐项对照：原明细保持、两条有效快照（首条归零、次条保留）、版本合计、单条调整、前后数量/重量、原因、ID及审批人原始16字节。
- openpyxl回读订单/产品汇总、有效明细、调整凭据：有效明细仅保留第二包，数量/重量/ID/序号/条码/产品/操作人/原设备与快照一致，调整凭据仅第一包一行。
- pypdf回读两页2185字符：完整审批人及引用、时间、原因、前后值、净明细与原/调整/净合计正确；本轮未另做视觉验收。文件不重写。
- 第一轮没有红业务attempt；harness已知cleanup空值警告保留，退出0。探索性Python读取先出现字符串换行转义错误、再出现默认GBK读取UTF-8失败，随后明确UTF-8成功；这两项是工具调用错误，不作业务失败/通过证据。文档写入首个脚本也因换行转义在解析期失败，未写入文件，随后修正。

## 3. 证据与断点

- 主树D:/acrylic-pda-outbound，codex/development，从eb3097d接续；仅测试文件/工具改动，作者自审，未独立审查。
- 验证树D:/acrylic-pda-worktrees/r06-e2a-migration保持detached ea4d166与既有改动，不合并、不提交、不清理。新增两文件与夹具可见性文件经守卫同步，前像、原状态与哈希清单在主树.local/d-boundaries-20260928/。
- attempt489目录保留独立XML、日志、marker、数据库与storage/partial-correction/下database-facts、原确认及导出快照、partial.xlsx/pdf、提取文本、content-readback.json。主树本机目录保留console/退出码/回读日志；未提交任何.local、密钥、库或产物。
- 第一轮结束，测试监听释放；下次attempt从490+查重，R08专用run从30查重。

## 4. 第一轮结束时待执行（历史）

连续更正v2→v3、撤销后快照、抬头更正仍未验，依次推进。D整块尚不能交付；不转R09 C2或A3剩余回归。

## 5. 第二轮：v2→v3连续更正

测试42b96fd已推送，第一轮文档提交092063d。无需新业务夹具，同一第一包通过两次真实CREATE/APPROVE从300.5改到300再改到299.5kg；第二包301kg不变。库内每条调整前后值逐项匹配，v3净2包/600.5kg，累计调整0包/-1kg。

- compileTestJava成功；attempt490的E2aChainedCorrectionExportIT为1/0/0/0，真实HTTP申请与下载XLSX/PDF、实际渲染子进程均通过。
- XLSX有效明细2行、调整凭据2行（分别v2/v3）；PDF三页2770字符；复用只读回读工具核对汇总/有效行/调整ID、版本、时间、原因、审批人、凭据和前后数量重量的具体值。
- 既有SnapshotValidator通过真实完整快照；仅在内存删除v2凭据，明确因“调整版本链不完整或乱序”抛出IllegalArgumentException。这是验证器反例，不是HTTP反例，也不改生产数据。
- 证据在验证树.local/e2a-cross-20260925-490/：独立XML、日志、marker、storage/chained-correction/中的database-facts、分格式快照、chained.xlsx/pdf、content-readback.json、pdf-extracted.txt、chain-validator-checks.txt。同步仅新增两个测试文件；哈希在主树.local/d-boundaries-20260928/chain-sync-manifest.json。
- 无红业务attempt；已知cleanup空值警告保留，测试退出0、监听释放。一次只读rg在Windows上使用路径通配符失败，改为目录加-g过滤；未作为业务证据。
- 第二轮结束仍未验：撤销后、抬头更正；下一步按风险顺序测试撤销。D未整体交付，不转其他任务。

## 6. 第三轮：撤销后导出失败，立即停工

失败测试阶段备份5e48d80已推送；第二轮文档提交3cf0709。复用同一两包业务夹具，仅归零第一包后执行原审批人WITHDRAW、申请人WITHDRAW_CONFIRM；角色/expected沿用已验证测试帮助方法，没有直接改业务事实来制造状态。

“撤销归零”按现有已交付业务定义指更正净调整归零，不是把原订单实绩归零。真实命令回执及数据库共同证明：v1为2包/601.5kg，v2为1包/301kg，撤销生成v3回到2包/601.5kg；两条correction_ledger的数量/重量合计均为零。两条有效快照恢复原300.5kg和301kg。

- attempt491 / E2aWithdrawnCorrectionExportIT：tests=1 failures=1 errors=0 skipped=0。编译成功；test-started存在，test-completed/withdrawn-http-completed不存在。保留红色200期待，未吞错或改为“409即通过”。
- 真实Web会话/CSRF发送XLSX、CORRECTED、resultVersion=3；HTTP409，code=EXPORT_SOURCE_UNAVAILABLE，message=该结果版本没有对应的更正文档。失败发生于申请阶段，未生成导出快照/文件，PDF请求尚未执行。
- v3.source_correction_id与原更正文档ID相同；文档status=WITHDRAWN、effective_result_version=2，withdrawal_confirmed_by/at已写；直接调用现有ExportFacts.correctionDocumentFor(order,3)返回null。库内原始事实保存于database-facts.json。
- 对应生产判定：ExportFacts.java:58–64只筛effective_result_version=目标版本且状态在EFFECTIVE/WITHDRAW_PENDING；CorrectionExportSource.java:51–52要求查到文档。这同时漏掉撤销新版本关联和WITHDRAWN状态，是本次已确认的导出阻断，不是fixture未造出撤销结果。
- 按用户硬边界立即停工，不修片1/片2/片3、不继续抬头、不发起更多业务测试。来源判定之后的撤销凭据表达、版本链和渲染约束尚未抵达；未宣称只改查询状态就能完整修复。
- 491失败后harness退出1，19330/19434释放，未出现489/490成功尾部的cleanup空值警告。失败库、日志与全部回执保留，不清理或覆盖。测试自审在执行前纠正了新断言查询的列归属（账表按correction_line_id取，不存在order_id）；此为测试草稿修正，未另占attempt，也不是生产修复。

## 7. 四项最终状态与提交

| 风险顺序 | 边界 | 结果 | 当次证据 / 测试提交 |
| --- | --- | --- | --- |
| 1 | 部分更正且净结果非空 | 通过 | 489：1/0/0/0；XLSX/PDF内容回读PASS；b523eff |
| 2 | v2→v3连续更正 | 通过 | 490：1/0/0/0；完整链/缺链反例/文件内容PASS；42b96fd |
| 3 | 撤销后快照导出 | 失败 | 491：1/1/0/0；HTTP409真bug；5e48d80阶段备份 |
| 4 | 抬头更正（无dispatch） | 跳过 | 第3项确认真bug后按授权停工；无attempt、无样本/内容结果 |

前两阶段文档分别092063d、3cf0709；本次停工文档提交SHA以Git记录为准。每步均单独提交并推送origin/codex/development，没有合main。业务生产路径从eb3097d到本轮无diff；原默认v1的R08ExportIT当次未重跑，run29 14/14仅历史引用。

## 8. 最终接续断点与未验范围

- 主树D:/acrylic-pda-outbound，codex/development；本轮代码变动：server/src/test/java/com/acrylic/outbound/e2averify/E2aCorrectionChainIT.java（仅9个方法/类型可见性），同目录E2aPartialCorrectionExportIT.java、E2aChainedCorrectionExportIT.java、E2aWithdrawnCorrectionExportIT.java，inventory目录PartialCorrectionExportHttpChecks.java、ChainedCorrectionExportHttpChecks.java、WithdrawnCorrectionExportHttpChecks.java，以及tools/verify-corrected-export-content.py。无生产文件变动。
- 文档变动：docs/ORCHESTRATION_STATUS.md、history/R06_DEBT_LIST_2026-09-27.md、R08_D_BOUNDARIES_2026-09-28.md、README.md，以及ORCHESTRATION_STATE_before_D_BOUNDARIES/CHAIN/WITHDRAW_STOP_2026-09-28.md三个原看板Git原文归档；旧原文不改写。
- 验证树保持detached ea4d166，累计仅同步7个测试文件；全部既有dirty/untracked保留。before/after完整git status、方法可见性文件两次前像、分步SHA256清单留在主树.local/d-boundaries-20260928/。未提交验证树。
- 491证据根D:/acrylic-pda-worktrees/r06-e2a-migration/.local/e2a-cross-20260925-491/，独立TEST-E2aWithdrawnCorrectionExportIT.xml和日志/marker保留。storage/withdrawn-correction/包含create-correction、approve、withdraw、withdraw-confirm回执，database-facts、request-XLSX、create-XLSX及http-status-XLSX；不含成功产物。主树保留console和退出码。489/490各自XML已在后续运行前单独归档，未被覆盖。
- 无活动重型任务，测试监听19330/23308/19434均释放，系统MySQL3306未动；单助手、作者自审，无独立审查。未提交.local密钥/数据库/导出产物；本机保存不等于异地备份。
- 三轮到限；下次attempt从492+查重，R08专用run从30查重。撤销后快照/文件/PDF内容、抬头更正、跨格式视觉及现场外推仍未验。前两项只覆盖本报告明确夹具，不外推全部组合。
- **交回用户决策**：是否授权撤销后导出缺陷的最小修复与复验。修复前D不能整体交付，R09 C2/A3选择暂不推进；不自动启动其他范围。

## 9. 后续授权接续：撤销修复通过，抬头新缺陷停工

§1–8保留原轮历史结论。后续用户授权先定性、判A才修：6d5d019定性A；0e837ff只修R08撤销消费，493真HTTP/文件内容通过，R08 run30 14项与渲染129项全绿，0797228记录全部红绿证据。492默认v1测试漏传必填basis导致400，整次红保留。

第三轮494的抬头样本规模小，真实CREATE/APPROVE成功、v2抬头正确且无dispatch明细；R08服务读取因无可追溯凭据行失败。导出HTTP尚未执行，XLSX/PDF未验；遵守真bug即停，没有修抬头。48609c1保存失败测试并推送。

四项更新为：部分更正通过（历史489/493历史v2）、连续更正通过（历史490）、撤销后通过（493）、抬头失败（494）。D仍不能整体交付；是否授权抬头消费修复交用户裁决，不自动转R09 C2/A3。新一轮完整证据、未验和文件清单见[R08撤销定性及修复记录§8–10](R08_D_WITHDRAW_DESIGN_AND_FIX_2026-09-28.md)。

## 10. 后续抬头修复及假设扫描

ee9b2ee定性A：B3抬头零line/effect/账是设计，补丁在冻结CREATE回执；68a43f8只修R08读取/渲染，无数据结构变更。495真实HTTP/MySQL与XLSX/PDF逐值通过，默认v1实际HTTP兼容，R08 run31 14项和渲染134项通过。494历史红保留。

四项基本边界均已有通过证据：部分489/493、连续490、实物撤销493、抬头生效495；前三项本轮未重跑。第三轮静态扫描发现R07抬头撤销空账阻断，未执行该HTTP组合、未修R07，按真bug即停。整块接受与新增R07项由用户决定，不把基本样本通过外推到所有组合。完整清单及未验见[抬头记录§7–9](R08_D_HEADER_DESIGN_AND_FIX_2026-09-28.md)。
