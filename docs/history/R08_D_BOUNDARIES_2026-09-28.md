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
