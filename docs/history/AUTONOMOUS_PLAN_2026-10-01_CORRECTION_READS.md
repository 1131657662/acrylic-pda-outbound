# 更正查询与原回执恢复自主方案（2026-10-01）

## 真实断点与选择

输入树 D:/acrylic-pda-outbound，codex/development，已推送输入 a9e926ad3b9e5d1c5993a39c55d9d5f3a534284a。附件字节闭环沿[原验收](R07_CORRECTION_ATTACHMENTS_2026-10-01.md)复用。版本事实只见[看板](../ORCHESTRATION_STATUS.md)生成块。八项工作区路径先快照保护；本批不编辑它们。

[剩余计划](../REMAINING_DEVELOPMENT_PLAN.md)要求 R07“原来源/原ID/原出库事实不改写”“逐笔调整和最新净结果可追溯”，R06/R07命令要求原请求恢复、不可变回执；[接口提案 §3](../parallel/R07/INTERFACE_PROPOSAL.md)明确 GET 更正原文与 GET 原命令回执、“权限内恢复原回执，不重复审批、不把404当失败终态”。源码 CorrectionCommandController 只有 POST；前轮测试靠SQL组装 expected，缺产品查询链。

先核库存差异：CorrectionReconciliations 只有不改变账务/可用性的案件状态机，审批/补扣未接。更严重的是 CorrectionOperations.Approve 第296–305行无条件将结果差额写入 correction_ledger，未根据原扣账贡献决定是否记账，完整反向传 null；CorrectionStore.insertCorrectionLedger 只是插入。与 CORRECTION_RESULT_DESIGN §4“原来未扣账…0条反向”“每条完整反向必须有 reversesEntryId”冲突。属于**静态确认的产品缺口，尚无本轮运行证据**；不改该算法，不以新补扣掩盖它，加入待决清单。已停报分页/P08/抬头撤销等仍保持。

## 本批方案与退出标准

采用既有 correction 命令协议上的新增只读入口。不开新业务命令、不改旧 POST/账务/迁移。只有显式 commands.enabled=true 时注册；默认关闭。另一方案是直接组装完整更正UI，但缺乏上下文/回执查询会迫使客户端猜测版本，所以先交付该必要产品接口。

| 子项 | 动作与退出标准 | 证据形态/依赖 |
| --- | --- | --- |
| Q1 查询与身份隔离 | 同仓建档人获取订单 CREATE expected；原申请人或同仓主管按命令身份获取文档上下文及冻结CREATE原文；无权限/跨仓/非原申请人/无记录拒绝。返回上下文不授业务能力 | 新代码、单测、真实Web HTTP；依赖既有身份/原文/事实存储 |
| Q2 原键回执 | GET按原键、原命令类型、原请求人恢复精确原字节；核原命令/响应SHA、规范字节、行身份、expected、仓库、能力位和RSA签名；PDA原件额外绑原设备。坏件失败关闭，缺键404不是失败终态 | 实际CREATE/APPROVE后GET及上下文重启恢复，JCA独立验签；损坏/错绑反例单测 |
| Q3 组合收口 | 客户端GET上下文→POST CREATE→GET待审原文→POST APPROVE→GET原回执；全部查询和拒绝前后业务表typed完整行不变。默认装配缺席，全服务端单测无新增失败；原事实保持 | 一次性新隔离MySQL attempt、HTTP原始响应、XML、快照、源码SHA、parse0、dump/ZIP |

查询事务使用只读一致快照；SQL参数化。文档详情仅提供既有冻结原文和命令上下文，不冒称完整账务预览、列表/游标或净结果API已实现。原回执保持签名，不重签、不重放POST。验签使用配置的当前/历史公钥；现有 OfflineSigner 要求当前签名配置就绪，此限制明示，不改其加载行为。

## 执行与边界

多步执行写ps1，新增文件/整文件重写；先核实际HEAD/远端、活动进程/监听、磁盘和真机枚举。只用新隔离库、一次attempt；前置错误记录后再决定修正，产品缺陷停该子项；既有测试失败不改旧断言或白名单。无AVD、无定向设备命令。D低于20GiB停止。

认证停新库并保存dump、完整ZIP逐文件SHA回读后，仅清本批新datadir；不删历史/.local证据。文档更新、看板先归档、事实脚本检查，明确路径提交推送开发分支。不开生产、不部署、不接原库/现网、不合main、不打标签；localDeletionAllowed=false。真实PDA/Keystore、独立JVM/断电、UI和现场留存仍未验。
