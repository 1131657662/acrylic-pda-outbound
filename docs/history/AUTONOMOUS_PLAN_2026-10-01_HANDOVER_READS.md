# R07 交接上下文与原回执查询方案（2026-10-01）

## 断点与依据

输入 `99861666ee796841c5ed72d07aa2c20e9e3b0c15`，主树 `D:/acrylic-pda-outbound`、`codex/development`，单助手沿有效自主目标执行。上一批新增811并发失败实证并推送，属于进展；不是等待在途测试。八项保护路径见本批before/protected.json，不编辑或暂存。

[剩余计划](../REMAINING_DEVELOPMENT_PLAN.md)顺序4要求“原来源/原ID/原出库事实不改写，防双端激活、重复调整”。[接口提案](../parallel/R07/INTERFACE_PROPOSAL.md)§1要求资源仓库鉴权及原动作身份查原结果，§2有GET案件四维状态和原命令回执；[交接设计](../parallel/R07/HANDOVER_DESIGN.md)§2要求结果未知只查询原请求/对象。

实际源码只有Web命令POST，无交接GET；内部scopeFacts/caseFacts仅供服务使用，测试直接取库内expected不能代替客户端入口。旧文档“全部未跑/outbox未实现”已由近期证据否定，但设备半场、故障路径和完整UI仍缺。811原键并发与同步消息缺status继续停报，不在新查询中重试命令、补消息或修旧算法。

## 本批实现与退出标准

1. 新增交接GET控制器/服务，沿既有commands.enabled开关默认关闭，仅匹配既有Web命令面。按身份和仓库读取scope的REQUEST上下文、案件的APPROVE/PREPARE/ACTIVATE上下文及四维事实，expected沿既有命令构造规则，能力全false。
2. 新增只读回执核验层：原命令/响应规范字节和摘要、签名、原键/动作/案件/actor/expected/event/时间/仓库逐项绑定。GET原回执限定原动作人，若原记录有设备则必须同设备。原回执不重签、不执行命令；坏件失败关闭，未知键404不表示可换键。
3. GET案件带验签且与案件绑定的原REQUEST命令，便于核对来源/原因；不伪装已具备完整恢复包下载或设备激活能力。
4. 定向离线验证有效原件与坏正文/摘要/签名/行绑定/权限/仓库等反例；主/test编译、脚本parse0及全服务端回归不得新增既有失败。
5. 新隔离MySQL一次真实Web HTTP GET上下文→REQUEST→GET案件→APPROVE→PREPARE→ACTIVATE，各步用GET提供的expected；四种原回执查询均与POST字节相等。原请求人以外/跨仓/类型不匹配/不存在对象拒绝；每组查询前后16表typed全行相等。
6. Boot上下文重建后重新登录，GET恢复四种原响应，业务表不变。默认关闭上下文没有新控制器。通过仅认当次XML/HTTP记录/数据库快照；缺陷即停该子项，不自动重跑产品断言。

## 范围和证据

新增代码、测试、文档，不改原命令算法、签名器、历史迁移、默认开关或旧测试。既有授权允许新增功能；采用有界方案直接实施，非重新等待审批。使用brainstorming、single-responsibility、security-practices、testing-best-practices；重复相似代码保持包内职责，不建立未经验证的跨业务抽象。

证据在 `.local/autonomous-20261001/handover-reads/` 和查重后新attempt；包括方案、输入/保护摘要、编译/单测/全回归XML、原HTTP字节、数据库快照、设备枚举、磁盘、dump/ZIP。合成FORMAL/停止证明/身份前置，真实Web传输；非PDA/独立JVM/断电/现场完整证据。原件/占用/账务不改写，GET通过不代表811或outbox缺陷修复，也不是R07整体完成。

一次仅一隔离库写者，真机只枚举且前后相同，AVD0；D保持高于20GiB。失败保全，认证停本批MySQL，dump和ZIP逐文件摘要回读后只清本批新datadir。更新看板前先归档，更新进度索引与自主报告，明确路径提交推送。实际SHA留提交回执，不追逐文档自身SHA。
